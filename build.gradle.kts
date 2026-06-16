import org.radarbase.gradle.plugin.radarKotlin

plugins {
    id("application")
    id("org.radarbase.radar-root-project") version Versions.radarCommons
    id("org.radarbase.radar-dependency-management") version Versions.radarCommons
    id("org.radarbase.radar-kotlin") version Versions.radarCommons
}

description = "RADAR-base data mapper — enriches and converts ODM output for downstream systems"

radarRootProject {
    projectVersion.set(Versions.project)
    gradleVersion.set(Versions.wrapper)
}

radarKotlin {
    javaVersion.set(Versions.java)
}

dependencies {
    implementation(platform("com.fasterxml.jackson:jackson-bom:${Versions.jackson}"))
    implementation("com.fasterxml.jackson.core:jackson-databind")
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-yaml") {
        runtimeOnly("org.yaml:snakeyaml:${Versions.snakeYaml}")
    }
    implementation("com.fasterxml.jackson.dataformat:jackson-dataformat-csv")
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")

    implementation("io.minio:minio:${Versions.minio}") {
        runtimeOnly("com.google.guava:guava:${Versions.guava}")
        runtimeOnly("com.squareup.okhttp3:okhttp:${Versions.okhttp}")
    }

    runtimeOnly("ch.qos.logback:logback-classic:${Versions.logback}")
}

application {
    mainClass.set("org.radarbase.mapper.RadarMapper")
}
