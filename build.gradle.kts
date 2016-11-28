import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip

version = "0.1.0-SNAPSHOT"
group = "var.run.docker.cloud"
allprojects {
    group = project.group
    version = project.version
}
subprojects {

    apply {
        plugin("java")
    }

    repositories {
        maven {
            setUrl("http://repository.jetbrains.com/all")
        }
        mavenCentral()
    }

    configurations.create("provided")

    val sourceSets = the<JavaPluginConvention>().sourceSets
    val mainSourceSet = sourceSets.getByName("main")!!
    val testSourceSet = sourceSets.getByName("test")!!

    mainSourceSet.compileClasspath += configurations.getByName("provided")
    testSourceSet.compileClasspath += configurations.getByName("provided")
    testSourceSet.runtimeClasspath += configurations.getByName("provided")
}

task<Zip>("tcdist") {
    into("server") {
        from(tasks.getByPath(":server:jar"))
        from(project(":server").configurations.runtime)
    }
    archiveName = "docker-cloud.zip"
    destinationDir = file("build")
    from("teamcity-plugin.xml") {
        filter<ReplaceTokens>("tokens" to mapOf("version" to project.version))
    }
}

task<Delete>("clean") {
    delete("build")
}