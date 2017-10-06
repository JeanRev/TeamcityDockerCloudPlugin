import org.gradle.api.tasks.testing.Test

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerCertificates
import com.spotify.docker.client.DockerClient

import java.nio.file.Paths
import com.spotify.docker.client.messages.RegistryAuth
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("com.spotify:docker-client:8.3.1")
    }
}

val testImage = "tc_dk_cld_plugin_test_img:1.0"
val dockerHubTestRepo = "docker.test.hub.repo"
val dockerHubTestUser = "docker.test.hub.user"
val dockerHubTestPwd = "docker.test.hub.pwd"
val unixSocketInstanceProp = "docker.test.unix.socket"
val tcpInstance_1_12Prop = "docker_1_12.test.tcp.address"
val tcpInstanceLatestProp = "docker.test.tcp.address"
val tcpTlsInstanceProp = "docker.test.tcp.ssl.address"
val npipeInstanceProp = "docker.test.npipe.address"
val registryInstanceProp = "docker.test.registry.address"
val dockerCertPath = "docker.test.tcp.ssl.certpath"
val yarnExecPathProp = "yarn.exec.path"

val dockerTestInstancesProps = mutableMapOf<String, String>()
listOf(dockerHubTestRepo, dockerHubTestUser, dockerHubTestPwd, unixSocketInstanceProp, tcpInstance_1_12Prop,
        tcpInstanceLatestProp, tcpTlsInstanceProp, npipeInstanceProp, registryInstanceProp, dockerCertPath)
        .filter { project.hasProperty(it) }
        .forEach { dockerTestInstancesProps.put(it, project.properties[it] as String) }

dependencies {
    compile("com.kohlschutter.junixsocket:junixsocket-common:2.0.4")
    compile("com.kohlschutter.junixsocket:junixsocket-native-common:2.0.4")
    compile("org.apache.httpcomponents:httpclient:4.5.2")
    compile("org.glassfish.jersey.core:jersey-client:2.23.1")
    compile("org.glassfish.jersey.media:jersey-media-json-jackson:2.23.1")
    compile("org.glassfish.jersey.connectors:jersey-apache-connector:2.23.1")
    add("provided", "org.jetbrains.teamcity:cloud-interface:${extra.get("serverApiVersion")}")
    add("provided", "org.jetbrains.teamcity:server-api:${extra.get("serverApiVersion")}")
    add("provided", "org.jetbrains.teamcity.internal:server:${extra.get("serverApiVersion")}")
    add("provided", "javax.websocket:javax.websocket-api:1.1")
    // Required for tests using the cloud API to process JSON structures.
    testCompile("com.google.code.gson:gson:2.8.0")
}

val jar = tasks.getByPath("jar") as Jar

jar.baseName = "docker-cloud-server"

jar.dependsOn("buildDockerCloudJS")

val jsSrcDir: File = project.file("src/main/js")

jar.apply {
    into("buildServerResources") {
        from(jsSrcDir.resolve("dist"))
        from(jsSrcDir.resolve("image-settings.html"))
        from(jsSrcDir.resolve("docker-cloud.css"))
    }
}

val clean = tasks.getByPath("clean")

clean.doLast {
    delete(jsSrcDir.resolve("dist"))
    delete(jsSrcDir.resolve("node_modules"))
    delete(jsSrcDir.resolve("test-results"))
}

tasks.withType<JavaCompile> {
    sourceCompatibility = "1.8"
    targetCompatibility = "1.8"
    if (project.hasProperty("rt8jar")) {
        options.compilerArgs.add("-Xbootclasspath:${project.properties.get("rt8jar")}")
    }
    options.encoding = "UTF-8"
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
    mustRunAfter(":server:setupTestImages")

    maxParallelForks = cpuCountForTest

    dockerTestInstancesProps.forEach { k, v -> systemProperty(k, v) }

    val certPath = dockerTestInstancesProps[dockerCertPath]
    if (certPath != null) {
        systemProperty("javax.net.ssl.keyStore", Paths.get(certPath, "client.jks").toString())
        systemProperty("javax.net.ssl.trustStore", Paths.get(certPath, "trustStore.jks").toString())
        systemProperty("javax.net.ssl.keyStorePassword", "fortestonly")
        systemProperty("javax.net.ssl.trustStorePassword", "fortestonly")
    }

    include("run/var/teamcity/cloud/docker/test/DockerTestSuite.class")
}

task<Test>("windowsDaemonTest") {
    group = "Verification"
    description = "Docker integration tests for the Windows daemon."

    useJUnit()
    mustRunAfter(":server:setupTestImages")

    maxParallelForks = cpuCountForTest

    dockerTestInstancesProps.forEach { k, v -> systemProperty(k, v) }

    include("run/var/teamcity/cloud/docker/test/WindowsDaemonTestSuite.class")
}

val yarnExecPath: String = if (project.hasProperty(yarnExecPathProp)) project.properties[yarnExecPathProp]
        .toString() else "yarn"


task("installDockerCloudJS").doFirst({
    run(jsSrcDir, yarnExecPath, "install", "--frozen-lockfile")
})

task("testDockerCloudJS").doFirst({
    run(jsSrcDir, yarnExecPath, "test")
}).dependsOn("installDockerCloudJS")

task("buildDockerCloudJS").doFirst({
    run(jsSrcDir, yarnExecPath, "build")
}).dependsOn("installDockerCloudJS")

val setupTestImages = task("setupTestImages") {

    description = "Setup the Docker-In-Docker testbed."

    doLast {
        val unixInstance = dockerTestInstancesProps[unixSocketInstanceProp]
        if (unixInstance != null) {
            buildTestImage("unix://$unixInstance")
        }

        val tcpInstance1_12 = dockerTestInstancesProps[tcpInstance_1_12Prop]
        if (tcpInstance1_12 != null) {
            buildTestImage("http://$tcpInstance1_12")
        }

        val tcpInstanceLatest = dockerTestInstancesProps[tcpInstanceLatestProp]
        if (tcpInstanceLatest != null) {
            val dockerURI = "http://$tcpInstanceLatest"
            buildTestImage(dockerURI)

            val registry = dockerTestInstancesProps[registryInstanceProp]
            if (registry != null) {
                pushTestImageToRegistry(dockerURI, registry)
            }
        }

        val tcpTlsInstance = dockerTestInstancesProps[tcpTlsInstanceProp]
        if (tcpTlsInstance != null) {
            buildTestImage("https://$tcpTlsInstance", true)
        }
    }
}

fun run(wd: File = projectDir, vararg cmd: String) {
    val proc = ProcessBuilder(cmd.asList()).redirectErrorStream(true).directory(wd).start()
    proc.inputStream.reader().readLines().forEach { println(it) }
    val exitCode: Int = proc.waitFor()
    if (exitCode != 0) {
        throw RuntimeException("Command \"${cmd.joinToString(" ")}\" returned with non-zero exit code: $exitCode")
    }
}

fun buildTestImage(dockerURI: String, tls: Boolean = false) {
    val clientBuilder = DefaultDockerClient.builder().uri(dockerURI)

    if (tls) {
        clientBuilder.dockerCertificates(DockerCertificates(Paths.get(dockerTestInstancesProps[dockerCertPath])))
    }

    val client = clientBuilder.build()

    client.use {
        it.build(project.file("client_test_image").toPath(), DockerClient.BuildParam.name(testImage))
    }
}

fun pushTestImageToRegistry(dockerURI: String, registry: String) {
    val client = DefaultDockerClient.builder().uri(dockerURI).build()

    val auth = RegistryAuth.builder().username("gradle").password("123").build()
    val target = registry + "/" + testImage
    client.use {
        it.tag(testImage, target)
        it.push(target, auth)
    }
}
