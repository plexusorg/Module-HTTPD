plugins {
    java
    `maven-publish`
    idea
    id("dev.plex.module") version "1.2"
}

group = "dev.plex"
version = "2.0-SNAPSHOT"
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
        url = uri("https://maven.enginehub.org/repo/")
    }

    maven {
        name = "codemc"
        url = uri("https://repo.codemc.io/repository/maven-public/")
    }
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.46")
    annotationProcessor("org.projectlombok:lombok:1.18.46")
    compileOnly("io.papermc.paper:paper-api:26.1.2.build.+")
    implementation("dev.plex:api:2.0-SNAPSHOT")
    implementation("org.json:json:20251224")
    implementation("org.reflections:reflections:0.10.2")
    plexLibrary("org.eclipse.jetty:jetty-server:12.1.9")
    plexLibrary("org.eclipse.jetty.ee10:jetty-ee10-servlet:12.1.9")
    plexLibrary("org.eclipse.jetty:jetty-proxy:12.1.9")
    compileOnly("de.tr7zw:item-nbt-api:2.15.7")
    implementation(platform("com.intellectualsites.bom:bom-newest:1.56")) // Ref: https://github.com/IntellectualSites/bom
    compileOnly("com.fastasyncworldedit:FastAsyncWorldEdit-Core")
    implementation("commons-io:commons-io:2.22.0")
}

tasks.getByName<Jar>("jar") {
    duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    archiveBaseName.set("Module-HTTPD")
    archiveVersion.set("")
    from("src/main/resources") {
        exclude("dev/**")
        exclude("httpd/assets/textures/**")
        exclude("httpd/assets/models/**")
        exclude("httpd/assets/items/**")
        exclude("httpd/assets/.minecraft-version")
    }
}

java {
    toolchain.languageVersion.set(JavaLanguageVersion.of(25))
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
        exclude("httpd/assets/textures/**")
        exclude("httpd/assets/models/**")
        exclude("httpd/assets/items/**")
        exclude("httpd/assets/.minecraft-version")
    }
}

publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}
