plugins {
    java
    `maven-publish`
    idea
}

group = "dev.plex"
version = "1.5-SNAPSHOT"
description = "Module-HTTPD"

repositories {
    mavenCentral()

    maven {
        url = uri("https://repo.papermc.io/repository/maven-public/")
    }

    maven {
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }

    maven {
        url = uri("https://jitpack.io")
        content {
            includeGroup("com.github.MilkBowl")
        }
    }

    maven { url = uri("https://maven.enginehub.org/repo/") }
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.34")
    annotationProcessor("org.projectlombok:lombok:1.18.34")
    implementation("io.papermc.paper:paper-api:1.21.1-R0.1-SNAPSHOT")
    implementation("dev.plex:server:1.5-SNAPSHOT")
    implementation("org.json:json:20240303")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.eclipse.jetty:jetty-server:11.0.24")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.24")
    implementation("org.eclipse.jetty:jetty-proxy:11.0.24")
    implementation("com.github.MilkBowl:VaultAPI:1.7.1") {
        exclude("org.bukkit", "bukkit")
    }
    implementation(platform("com.intellectualsites.bom:bom-newest:1.49")) // Ref: https://github.com/IntellectualSites/bom
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core:2.11.2")
    implementation("commons-io:commons-io:2.17.0")
}

tasks.getByName<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("Module-HTTPD")
    archiveVersion.set("")
    from("src/main/resources") {
        exclude("dev/**")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(21))
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name()
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name()
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name()
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}