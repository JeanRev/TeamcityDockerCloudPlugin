import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.api.tasks.testing.Test

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
    testCompile("org.testng:testng:6.9.13.6")
    testCompile("org.assertj:assertj-core:3.5.2")
}

val test = tasks.getByName("test") as Test
test.apply {
    useTestNG()
    if (project.hasProperty("docker.test.tcp.address")) {
        jvmArgs("-Ddocker.test.tcp.address=${project.properties.get("docker.test.tcp.address")}")
    }
    if (project.hasProperty("docker.test.unix.socket")) {
        jvmArgs("-Ddocker.test.unix.socket=${project.properties.get("docker.test.unix.socket")}")
    }
}