import java.util.Properties

plugins {
    id("org.jetbrains.kotlin.jvm") version "1.9.24"
    id("com.github.johnrengelman.shadow") version "8.1.1"
    application
}

// Run the Kotlin compiler on JDK 15 to avoid Java 25 version-string parse failures in Kotlin 1.9.x.
// jvmTarget is explicitly set to 1.8 so the compiled bytecode stays compatible with the Furhat OS JVM.
kotlin {
    jvmToolchain(15)
}

application {
    mainClass.set("furhatos.app.openaichat.MainKt")
}

java {
    sourceCompatibility = JavaVersion.VERSION_1_8
    targetCompatibility = JavaVersion.VERSION_1_8
}

tasks.withType<org.jetbrains.kotlin.gradle.tasks.KotlinCompile>().configureEach {
    kotlinOptions {
        jvmTarget = "1.8"
    }
}


repositories {
    mavenLocal()
    maven { url = uri("https://s3-eu-west-1.amazonaws.com/furhat-maven/releases") }
    maven { url = uri("https://repo.gradle.org/gradle/libs-releases") }
    mavenCentral()
}

dependencies {
    implementation("com.furhatrobotics.furhatos:furhat-commons:2.9.1")
}

tasks.jar {
    val lowerCasedName = archiveBaseName.get().lowercase()
    val normalizedName = lowerCasedName.substring(0, 1).uppercase() + lowerCasedName.substring(1)
    manifest {
        attributes(
            "Class-Path" to configurations.compileClasspath.get().map { it.name }.joinToString(" "),
            "Main-Class" to "furhatos.app.${lowerCasedName}.${normalizedName}Skill"
        )
    }
}

// ShadowJar depends on jar being finished properly.
tasks.shadowJar {
    exclude("**/Log4j2Plugins.dat")
    exclude("**/node_modules")

    val props = Properties()
    props.load(project.file("skill.properties").inputStream())
    val skillVersion = props.getProperty("version")
    val skillName = props.getProperty("name")

    archiveFileName.set("${skillName}_${skillVersion}.skill")
    archiveExtension.set("skill")

    from("skill.properties")
    from("assets")
    from("local.properties")

    manifest {
        attributes(
            "Main-Class" to "furhatos.app.openaichat.OpenaichatSkill"
        )
    }
}
