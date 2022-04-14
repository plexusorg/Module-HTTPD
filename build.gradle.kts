plugins {
    java
    `maven-publish`
    idea
}

group = "dev.plex"
version = "1.0.1"
description = "Module-HTTPD"

repositories {
    mavenLocal()
    mavenCentral()

    maven {
        url = uri("https://papermc.io/repo/repository/maven-public/")
    }

    maven {
        url = uri("https://nexus.telesphoreo.me/repository/plex/")
    }

    maven {
        url = uri("https://nexus.telesphoreo.me/repository/totalfreedom/")
    }
    maven {
        url = uri("https://jitpack.io")
    }
}

dependencies {
    implementation("org.projectlombok:lombok:1.18.22")
    annotationProcessor("org.projectlombok:lombok:1.18.22")
    implementation("io.papermc.paper:paper-api:1.18.2-R0.1-SNAPSHOT")
    implementation("dev.plex:Plex:1.0.2-SNAPSHOT")
    implementation("org.json:json:20220320")
    implementation("org.reflections:reflections:0.10.2")
    implementation("org.eclipse.jetty:jetty-server:11.0.9")
    implementation("org.eclipse.jetty:jetty-servlet:11.0.9")
    implementation("org.eclipse.jetty:jetty-proxy:11.0.9")
    implementation("com.github.MilkBowl:VaultAPI:1.7")
}

tasks {
    compileJava {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything

        // Set the release flag. This configures what version bytecode the compiler will emit, as well as what JDK APIs are usable.
        // See https://openjdk.java.net/jeps/247 for more information.
        options.release.set(17)
    }
    javadoc {
        options.encoding = Charsets.UTF_8.name() // We want UTF-8 for everything
    }
    processResources {
        filteringCharset = Charsets.UTF_8.name() // We want UTF-8 for everything
    }

}


publishing {
    publications {
        create<MavenPublication>("maven") {
            from(components["java"])
        }
    }
}

tasks.getByName<Jar>("jar") {
    archiveBaseName.set("Plex-HTTPD")
    archiveVersion.set("")
}