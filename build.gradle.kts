plugins {
    id("java")
    id("com.github.johnrengelman.shadow") version "8.1.1"
}

group = "com.gearworks"
version = "1.0.0"

description = "DiscordWhitelist Plugin"

repositories {
    mavenCentral()
    maven("https://repo.velocitypowered.com/snapshots/")
    maven("https://m2.dv8tion.net/releases")
}

dependencies {
    // Velocity API
    compileOnly("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")
    annotationProcessor("com.velocitypowered:velocity-api:3.1.2-SNAPSHOT")

    // JDA - Compatible with Java 8
    implementation("net.dv8tion:JDA:4.4.0_352") {
        exclude(module = "opus-java")
        exclude(group = "org.slf4j", module = "slf4j-api")
    }

    // MySQL JDBC Driver
    implementation("mysql:mysql-connector-java:8.0.28")

    // HikariCP for connection pooling
    implementation("com.zaxxer:HikariCP:3.4.5")

    // Configurate 4.x for configuration management
    implementation("org.spongepowered:configurate-yaml:4.1.2")

    // Guava
    implementation("com.google.guava:guava:21.0")
}

tasks.withType<com.github.jengelman.gradle.plugins.shadow.tasks.ShadowJar> {
    archiveBaseName.set("velocity-discord-whitelist")

    // Relocate dependencies to avoid classpath conflicts
    relocate("net.dv8tion", "com.gearworks.whitelist.shaded.net.dv8tion")
    // Remove relocation for MySQL JDBC driver
    // Do not relocate MySQL packages

    // Remove relocation for HikariCP
    // Do not relocate HikariCP packages

    // Remove relocation for SLF4J
    // Do not relocate SLF4J packages

    relocate("org.spongepowered.configurate", "com.gearworks.whitelist.shaded.org.spongepowered.configurate")
    relocate("com.google.common", "com.gearworks.whitelist.shaded.com.google.common")

    // Merge service files (important for JDBC drivers)
    mergeServiceFiles()

    // Exclude SLF4J from the shaded JAR
    dependencies {
        exclude(dependency("org.slf4j:slf4j-api"))
        exclude(dependency("org.slf4j:slf4j-simple"))
    }

    // Minimize the final jar by removing unused classes
    minimize {
        exclude(dependency("mysql:mysql-connector-java"))
        exclude(dependency("com.zaxxer:HikariCP"))
    }
}

tasks.build {
    dependsOn("shadowJar")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(8))
    }
}