import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.jvm.tasks.Jar

val javaCompile = tasks.getByName("compileJava") as JavaCompile
javaCompile.apply {
    sourceCompatibility = "1.6"
    targetCompatibility = "1.6"
    if (project.hasProperty("rt6jar")) {
        options.compilerArgs.add("-Xbootclasspath:${project.properties.get("rt6jar")}")
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
    add("provided", "org.jetbrains.teamcity:agent-api:${extra.get("serverApiVersion")}")
    add("provided", "org.jetbrains.teamcity:cloud-shared:${extra.get("serverApiVersion")}")
}

val jar = tasks.getByPath("jar") as Jar

jar.baseName = "docker-cloud-agent"

task<Zip>("tcdist") {
    baseName = "docker-cloud-agent"

    dependsOn("jar")
    into("lib") {
        from(tasks.getByName("jar"))
        from(project(":agent").configurations.runtime)
    }
    from("teamcity-plugin.xml")
}