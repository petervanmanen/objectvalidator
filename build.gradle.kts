import java.net.URI

plugins {
    id("java")
    id("maven-publish")
}

group = "com.ritense"
version = "1.0.1-SNAPSHOT"

repositories {
    mavenCentral()
}

dependencies {
    implementation("com.fasterxml.jackson.core:jackson-core:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-annotations:2.18.0")
    implementation("com.fasterxml.jackson.core:jackson-databind:2.18.0")
    implementation("org.json:json:20240303")
    implementation("com.github.erosb:json-sKema:0.18.0")
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
          /*      properties = mapOf(
                    "myProp" to "value",
                    "prop.with.dots" to "anotherValue"
                )*/
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


tasks.test {
    useJUnitPlatform()
}