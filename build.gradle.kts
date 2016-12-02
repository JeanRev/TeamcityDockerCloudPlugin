import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip

version = "0.3.0-SNAPSHOT"
group = "var.run.docker.cloud"
val commitId = gitCommitId()
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

    var versionSuffix = project.version as String
    if (project.version.toString().endsWith("-SNAPSHOT")) {
        versionSuffix += "_${commitId.substring(0,7)}"
    }

    archiveName = "docker-cloud_$versionSuffix.zip"
    destinationDir = file("build")
    from("teamcity-plugin.xml") {
        filter<ReplaceTokens>("tokens" to mapOf("version" to "${project.version} (build $commitId)"))
    }
}

task<Delete>("clean") {
    delete("build")
}

fun gitCommitId(): String {
    val ref = "ref: (.*)".toRegex().find(project.file(".git/HEAD").readText())?.groups?.get(1)?.value
    val refFile = project.file(".git/$ref")
    if (refFile.exists()) {
        return refFile.readText().trim()
    }
    return "(.*) $ref".toRegex().find(project.file(".git/packed-refs").readText())?.groups?.get(1)?.value!!
}
