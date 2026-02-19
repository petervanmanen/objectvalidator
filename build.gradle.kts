import java.net.URI

plugins {
    id("java")
    id("application")
    id("maven-publish")
    id("org.jsonschema2pojo") version "1.2.2"
}

group = "com.ritense"
version = "1.0.2-SNAPSHOT"

repositories {
    mavenCentral()
}

application {
    mainClass = "com.ritense.InwonerplanValidator"
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("com.fasterxml.jackson.datatype:jackson-datatype-jsr310:2.18.0")
    implementation("org.apache.commons:commons-lang3:3.12.0")
    implementation("com.github.erosb:json-sKema:0.18.0")
    implementation("com.formdev:flatlaf:3.5.4")
    implementation("com.fifesoft:rsyntaxtextarea:3.5.3")
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
}

tasks.withType<Jar> {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    from(sourceSets.main.get().output)

    // Include runtime dependencies
    from({
        configurations.runtimeClasspath.get().filter { it.name.endsWith("jar") }.map { zipTree(it) }
    })

    manifest {
        attributes["Main-Class"] = "com.ritense.InwonerplanValidator"
    }
}

publishing {
    publications {
        create<MavenPublication>("mavenJava") {
            from(components["java"])
            pom {
                name = "ObjectValidator"
                description = "A concise description of my library"
                url = "https://github.com/petervanmanen/objectvalidator/"

                licenses {
                    license {
                        name = "The Apache License, Version 2.0"
                        url = "http://www.apache.org/licenses/LICENSE-2.0.txt"
                    }
                }
                developers {
                    developer {
                        id = "Peter van Manen"
                        name = "Peter van Manen"
                        email = "peter.van.manen@ritense.com"
                    }
                }
                scm {
                    connection = "scm:git:git://ithub.com:petervanmanen/objectvalidator.git"
                    developerConnection = "scm:git:ssh://github.com:petervanmanen/objectvalidator.git"
                    url = "https://github.com/petervanmanen/objectvalidator/"
                }
            }
            repositories {
                maven {
                    name = "GitHubPackages"
                    url = URI("https://maven.pkg.github.com/petervanmanen/objectvalidator")
                    credentials {
                        username = System.getenv("USERNAME")
                        password = System.getenv("TOKEN")
                    }
                }
            }
        }
    }
}

jsonSchema2Pojo {

    val inputFiles = files(
        project.layout.projectDirectory.dir("src/main/resources/schemas/inwonerplan.schema.json"),
    )
    setSource(inputFiles)

    // Target directory for generated Java source files. The plugin will add this directory to the
    // java source set so the compiler will find and compile the newly generated source files.
    targetDirectory = project.layout.buildDirectory.dir("generated-sources/js2p").get().asFile
    targetPackage = "com.ritense"

    setSource(inputFiles)
    setAnnotationStyle("JACKSON2")
    dateType = "java.time.LocalDate"
    dateTimeType = "java.time.ZonedDateTime"

}

tasks.test {
    useJUnitPlatform()
}
