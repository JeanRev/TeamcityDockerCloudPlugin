import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerClient
import com.spotify.docker.client.exceptions.DockerException
import com.spotify.docker.client.messages.Container
import com.spotify.docker.client.messages.ContainerConfig
import com.spotify.docker.client.messages.ContainerCreation
import com.spotify.docker.client.messages.ContainerInfo
import com.spotify.docker.client.messages.HostConfig
import org.gradle.BuildAdapter
import org.gradle.BuildResult

import org.gradle.api.GradleException
import java.io.File

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("com.spotify:docker-client:6.1.1")
    }
}

val unixSocketInstanceProp = "docker.test.unix.socket"
val tcpInstanceProp = "docker.test.tcp.address"
val tcpTlsInstanceProp = "docker.test.tcp.ssl.address"

val dockerTestImage = "docker:1.12.3-dind"
val testContainerMgr = TestContainerManager()

val dockerTestInstances = mutableMapOf<String, String>()
listOf(unixSocketInstanceProp, tcpInstanceProp, tcpTlsInstanceProp)
        .filter { project.hasProperty(it) }
        .forEach { dockerTestInstances.put(it, project.properties[it] as String) }


gradle.addListener(object : BuildAdapter() {
    override fun buildFinished(result: BuildResult) {
        testContainerMgr.dispose()
    }
})

apply {
    plugin("java")
}

val javaCompile = tasks.getByName("compileJava") as JavaCompile
javaCompile.apply {
    sourceCompatibility = "1.7"
    targetCompatibility = "1.7"
    if (project.hasProperty("rt7jar")) {
        options.compilerArgs.add("-Xbootclasspath:${project.properties.get("rt7jar")}")
    }
    options.encoding = "UTF-8"
}

val javaCompileTest = tasks.getByName("compileTestJava") as JavaCompile
javaCompileTest.apply {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    if (project.hasProperty("rt8jar")) {
        options.compilerArgs.add("-Xbootclasspath:${project.properties.get("rt8jar")}")
    }
    options.encoding = "UTF-8"
}

dependencies {
    compile("com.kohlschutter.junixsocket:junixsocket-common:2.0.4")
    compile("com.kohlschutter.junixsocket:junixsocket-native-common:2.0.4")
    compile("org.apache.httpcomponents:httpclient:4.5.2")
    compile("org.glassfish.jersey.core:jersey-client:2.23.1")
    compile("org.glassfish.jersey.media:jersey-media-json-jackson:2.23.1")
    compile("org.glassfish.jersey.connectors:jersey-apache-connector:2.23.1")
    add("provided", "org.apache.logging.log4j:log4j-api:2.5")
    add("provided", "org.apache.logging.log4j:log4j-api:2.5")
    add("provided", "org.atmosphere:atmosphere-runtime:2.2.4")
    add("provided", "org.jetbrains.teamcity:cloud-interface:10.0")
    add("provided", "org.jetbrains.teamcity:server-api:10.0")
    testCompile("junit:junit:4.12")
    testCompile("org.assertj:assertj-core:3.5.2")
    testCompile("io.takari.junit:takari-cpsuite:1.2.7")
}

val cpuCountForTest = Math.max(1, Runtime.getRuntime().availableProcessors() / 2)

val test = tasks.getByName("test") as Test
test.apply {
    group = "Verification"
    description = "Standard test suite."

    useJUnit()

    maxParallelForks = cpuCountForTest

    include("run/var/teamcity/cloud/docker/test/StandardTestSuite.class")
}

task<Test>("quickTest") {
    group = "Verification"
    description = "Standard test suite, excluding long running tests."

    useJUnit()

    maxParallelForks = cpuCountForTest

    include("run/var/teamcity/cloud/docker/test/QuickTestSuite.class")
}

task<Test>("dockerIT") {
    group = "Verification"
    description = "Docker integration tests."

    useJUnit()
    mustRunAfter(":server:setupDindTestBed")

    maxParallelForks = cpuCountForTest

    dockerTestInstances.forEach { k, v -> systemProperty(k, v) }

    include("run/var/teamcity/cloud/docker/test/DockerTestSuite.class")
}

inner class TestContainerManager {

    private val UUID_LABEL: String = "run.var.teamcity.cloud.docker.build_container_uuid"
    private val CREATE_TS_LABEL: String = "run.var.teamcity.cloud.docker.build_container_ts"
    private val UUID: java.util.UUID = java.util.UUID.randomUUID()

    val client: Lazy<DefaultDockerClient> = lazy({
        val dockerHost = System.getenv()["DOCKER_HOST"] ?: throw GradleException("DOCKER_HOST env. variable not set.")
        DefaultDockerClient.builder().uri(dockerHost).build()
    })

    fun startContainer(config: ContainerConfig.Builder) : ContainerInfo {
        config.labels(mapOf(UUID_LABEL to UUID.toString(), CREATE_TS_LABEL to System.currentTimeMillis().toString()))
        val container = client.value.createContainer(config.build())
        val containerId = container.id()
        client.value.startContainer(containerId)
        return client.value.inspectContainer(containerId)
    }

    fun pullImage(image: String) {
        client.value.pull(image)
    }

    fun dispose() {
        if (!client.isInitialized()) {
            return
        }

        try {
            val containers = client.value.listContainers(DockerClient.ListContainersParam.withLabel(UUID_LABEL))
            for (container in containers) {
                val labels = container.labels()
                if (UUID.toString() == labels.get(UUID_LABEL) || (System.currentTimeMillis() -
                        labels.get(CREATE_TS_LABEL)!!.toLong()) > java.util.concurrent.TimeUnit.MINUTES.toMillis(30)) {
                    client.value.removeContainer(container.id(), DockerClient.RemoveContainerParam.removeVolumes(),
                            DockerClient.RemoveContainerParam.forceKill())
                }
            }
        } finally {
            client.value.close()
        }
    }
}

val setupDindTestBed = task("setupDindTestBed") {

    description = "Setup the Docker-In-Docker testbed."

    doLast {
        // The directory where the Docker sockets will be bound.
        val runFolder = project.buildDir.resolve("dockerIT")
        // Create any missing parent. Note that Docker will create missing host directories when binding folders, but
        // with root as owner instead of the current user.
        runFolder.mkdirs()

        // The folder containing the certificates and keys to test Docker over TLS.
        val pkiDir = project.file("test_pki")

        // Retrieve the current user GID to pass it to the containerized Docker daemon in order to create the socket
        // with the right group ownership.
        val p = ProcessBuilder("id", "-g").redirectErrorStream(true).start()
        val gid = p.inputStream.reader(java.nio.charset.StandardCharsets.UTF_8).readText().trim()

        testContainerMgr.pullImage(dockerTestImage)

        // Configure and start the "plain" Docker container (without TLS).
        val plainDockerConfig = prepareDinDConfig(runFolder, pkiDir)
        plainDockerConfig.cmd("-G", gid, "-H", "unix:///var/tmp/docker.sock")
        val container = testContainerMgr.startContainer(plainDockerConfig)
        buildTestImage("unix://$runFolder/docker.sock")
        // The plain Docker container is used both for TCP tests and tests through the Unix domain socket.
        dockerTestInstances[unixSocketInstanceProp] = "$runFolder/docker.sock"
        dockerTestInstances[tcpInstanceProp] = "${container.networkSettings().ipAddress()}:2375"

        // Configure and start the TLS Docker container.
        val tlsDockerConfig = prepareDinDConfig(runFolder, pkiDir)
        tlsDockerConfig.cmd("-G", gid, "-H", "unix:///var/tmp/tlsDocker.sock", "-H", "tcp://0.0.0.0:2376", "--tlsverify",
                "--tlscacert=/root/pki/ca.pem", "--tlscert=/root/pki/server-cert.pem", "--tlskey=/root/pki/server-key.pem")
        val tlsContainer = testContainerMgr.startContainer(tlsDockerConfig)
        buildTestImage("unix://$runFolder/tlsDocker.sock")
        dockerTestInstances[tcpTlsInstanceProp] = "${tlsContainer.networkSettings().ipAddress()}:2376"

        // Register the TLS settings as java system properties.
        val trustStoreFile = pkiDir.resolve("trustStore.jks")
        val clientStoreFile = pkiDir.resolve("client.jks")
        val pwd = "fortestonly"

        val dockerITTask = tasks.findByName("dockerIT") as Test
        dockerITTask.systemProperties.putAll(mapOf("javax.net.ssl.trustStore" to trustStoreFile.toString(),
                "javax.net.ssl.keyStore" to clientStoreFile.toString(),
                "javax.net.ssl.trustStorePassword" to pwd,
                "javax.net.ssl.keyStorePassword" to pwd))

        dockerITTask.systemProperties.putAll(dockerTestInstances)
    }
}


fun prepareDinDConfig(runFolder: File, pkiDir: File) : ContainerConfig.Builder {
    return ContainerConfig.builder()
            .image(dockerTestImage)
            .hostConfig(HostConfig.builder().privileged(true).binds("$runFolder:/var/tmp", "$pkiDir:/root/pki").build())
            .openStdin(true)
            .tty(true)
}

fun buildTestImage(dockerURI: String) {
    val client: DefaultDockerClient = DefaultDockerClient.builder().uri(dockerURI).build()
    println()
    client.use {
        it.build(project.file("client_test_image").toPath(), DockerClient.BuildParam.name("tc_dk_cld_plugin_test_img:1.0"))
    }
}
