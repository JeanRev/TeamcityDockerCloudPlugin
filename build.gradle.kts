import org.apache.tools.ant.filters.ReplaceTokens
import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip
import org.gradle.api.tasks.compile.JavaCompile

version = "0.5.2-SNAPSHOT"
group = "var.run.docker.cloud"
val commitId = gitCommitId()
allprojects {
    group = project.group
    version = project.version
}

subprojects {

    extra.set("serverApiVersion", "2017.1")

    apply {
        plugin("java")
    }

    repositories {
        maven {
            setUrl("https://download.jetbrains.com/teamcity-repository")
        }
        mavenCentral()
    }

    configurations.create("provided")

    dependencies {
        add("provided", "org.apache.logging.log4j:log4j-api:2.5")
        // Provides null-check annotations in the javax.annotation namespace.
        add("provided", "com.google.code.findbugs:jsr305:3.0.2")
        testCompile("junit:junit:4.12")
        testCompile("org.assertj:assertj-core:3.5.2")
        testCompile("io.takari.junit:takari-cpsuite:1.2.7")
    }

    val sourceSets = the<JavaPluginConvention>().sourceSets
    val mainSourceSet = sourceSets.getByName("main")!!
    val testSourceSet = sourceSets.getByName("test")!!

    mainSourceSet.compileClasspath += configurations.getByName("provided")
    testSourceSet.compileClasspath += configurations.getByName("provided")
    testSourceSet.runtimeClasspath += configurations.getByName("provided")

    afterEvaluate({
        tasks.withType<JavaCompile> {
            options.compilerArgs.add("-Xlint:unchecked")
            options.compilerArgs.add("-Xlint:deprecation")
            options.encoding = "UTF-8"
        }
    })
}

task<Zip>("tcdist") {
    dependsOn(":agent:tcdist")
    dependsOn(":server:jar")
    into("agent") {
        from(tasks.getByPath(":agent:tcdist"))
    }
    into("server") {
        from(tasks.getByPath(":server:jar"))
        from(project(":server").configurations.runtime)
    }

    val parsedVersion = "((?:[0-9]\\.)*[0-9])(-SNAPSHOT)?".toRegex().matchEntire(project.version.toString())!!
    val snapshotVersion = parsedVersion.groups[2] != null

    var versionSuffix = project.version as String
    if (snapshotVersion) {
        versionSuffix += "_${commitId.substring(0,7)}"
    }

    archiveName = "docker-cloud_$versionSuffix.zip"
    destinationDir = file("build")

    // Apply filtering on the plugin descriptor.
    // The version field is especially important since it will be evaluated by the server to detect outdated plugins.
    // The exact format of the version number is however unspecified, especially when it comes to non-digit characters.
    // To be safe, we stick to digits separated by dots, and we add a timestamp as the last version token to force
    // older build for the same plugin version to be considered outdated.
    from("teamcity-plugin.xml") {
        filter<ReplaceTokens>("tokens" to mapOf(
                "version" to "${parsedVersion.groups[1]!!.value}.${System.currentTimeMillis()}",
                "buildInfo" to "build: $commitId, ${if(snapshotVersion) "SNAPSHOT" else "RELEASE"}"))
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
