import org.jsonschema2pojo.gradle.GenerateJsonSchemaJavaTask
import org.jsonschema2pojo.gradle.JsonSchemaExtension

plugins {
    id("java")
    id("org.jsonschema2pojo") version "1.2.2"
}

group = "org.example"
version = "1.0-SNAPSHOT"

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



tasks.create("jsonSchema-objects", GenerateJsonSchemaJavaTask::class) {
    // configured here so that gradle knows when the task has to be run again
    val inputFiles = files(
        "$projectDir/src/main/resources/schemas",
        "$projectDir/src/main/resources/schemas/inwonerplan.schema.json",

        )
    inputs.files(inputFiles)

    // need afterEvaluate due to how the plugin works currently; maybe there is a better way?
    project.afterEvaluate {
        configuration = JsonSchemaExtension().apply {
            targetDirectory = project.layout.buildDirectory.dir("generated-sources/js2p").get().asFile
            targetPackage = "com.ritense.valtimo.implementation.objects"
            generateBuilders = true
            includeToString = true
            includeHashcodeAndEquals = true
            includeCopyConstructor = true
            includeAdditionalProperties = false
            dateType = "java.time.LocalDate"
            dateTimeType = "java.time.LocalDateTime"
            customDatePattern = "yyyy-MM-dd"
            useTitleAsClassname = true


            setSource(inputFiles)
            setAnnotationStyle("JACKSON2")
        }
    }
}

tasks.compileJava {
    dependsOn("jsonSchema")
}

tasks.register("jsonSchema") {
    dependsOn(tasks.matching { it.name.startsWith("jsonSchema-") })
}

tasks.named("generateJsonSchema2Pojo") {
    // ensure that jsonSchema task is run whenever jsonschema2pojo is run
    // this is done to prevent confusion with developers why that task is not generating the schema's
    dependsOn("jsonSchema")
}


tasks.test {
    useJUnitPlatform()
}