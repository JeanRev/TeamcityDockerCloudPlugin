import org.gradle.api.tasks.Delete
import org.gradle.api.tasks.bundling.Zip


/*
import org.gradle.api.JavaVersion.VERSION_1_7

        apply<ApplicationPlugin>()


configure<ApplicationPluginConvention> {
    mainClassName = "samples.HelloWorld"
}

configure<JavaPluginConvention> {
    sourceCompatibility = VERSION_1_7
    targetCompatibility = VERSION_1_7
}

repositories {
    jcenter()
}

dependencies {
    testCompile("junit:junit:4.12")
}
*/

subprojects {

    group = "var.run.docker.cloud"
    version = "0.1.0-SNAPSHOT"

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
    from("teamcity-plugin.xml")
}

task<Delete>("clean") {
    delete("build")
}