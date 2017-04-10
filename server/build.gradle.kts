import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

import com.spotify.docker.client.DefaultDockerClient
import com.spotify.docker.client.DockerCertificates
import com.spotify.docker.client.DockerClient

import java.nio.file.Paths
import com.spotify.docker.client.messages.AuthConfig
import org.apache.http.ssl.SSLContextBuilder

buildscript {
    repositories {
        jcenter()
    }

    dependencies {
        classpath("com.spotify:docker-client:6.1.1")
    }
}

val unixSocketInstanceProp = "docker.test.unix.socket"
val tcpInstance_1_12Prop = "docker_1_12.test.tcp.address"
val tcpInstanceLatestProp = "docker.test.tcp.address"
val tcpTlsInstanceProp = "docker.test.tcp.ssl.address"
val dockerCertPath = "docker.test.tcp.ssl.certpath"

val dockerTestInstancesProps = mutableMapOf<String, String>()
listOf(unixSocketInstanceProp, tcpInstance_1_12Prop, tcpInstanceLatestProp, tcpTlsInstanceProp, dockerCertPath)
        .filter { project.hasProperty(it) }
        .forEach { dockerTestInstancesProps.put(it, project.properties[it] as String) }

apply {
    plugin("java")
}

tasks.withType<JavaCompile> {
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
    // Required for tests using the cloud API to process JSON structures.
    testCompile("com.google.code.gson:gson:2.8.0")
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

    val certPath = dockerTestInstancesProps[dockerCertPath];
    if (certPath != null) {
        systemProperty("javax.net.ssl.keyStore", Paths.get(certPath, "client.jks").toString())
        systemProperty("javax.net.ssl.trustStore", Paths.get(certPath, "trustStore.jks").toString())
        systemProperty("javax.net.ssl.keyStorePassword", "fortestonly")
        systemProperty("javax.net.ssl.trustStorePassword", "fortestonly")
    }


    include("run/var/teamcity/cloud/docker/test/DockerTestSuite.class")
}

val setupTestImages = task("setupTestImages") {

    description = "Setup the Docker-In-Docker testbed."

    doLast {
        val unixInstance = dockerTestInstancesProps[unixSocketInstanceProp]
        if (unixInstance != null) {
            buildTestImage("unix://$unixInstance")
        }

        val tcpInstance1_12 = dockerTestInstancesProps[tcpInstance_1_12Prop];
        if (tcpInstance1_12 != null) {
            buildTestImage("http://$tcpInstance1_12")
        }

        val tcpInstanceLatest = dockerTestInstancesProps[tcpInstanceLatestProp];
        if (tcpInstanceLatest != null) {
            buildTestImage("http://$tcpInstanceLatest")
        }

        val tcpTlsInstance = dockerTestInstancesProps[tcpTlsInstanceProp]
        if (tcpTlsInstance != null) {
            buildTestImage("https://$tcpTlsInstance", true)
        }
    }
}

fun buildTestImage(dockerURI: String, tls: Boolean = false) {
    val clientBuilder = DefaultDockerClient.builder().uri(dockerURI)

    if (tls) {
        clientBuilder.dockerCertificates(DockerCertificates(Paths.get(dockerTestInstancesProps[dockerCertPath])))
    }

    val client = clientBuilder.build()

    client.use {
        it.build(project.file("client_test_image").toPath(), DockerClient.BuildParam.name("tc_dk_cld_plugin_test_img:1.0"))
    }
}