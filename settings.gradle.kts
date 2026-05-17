pluginManagement {
    repositories {
        maven("https://nexus.telesphoreo.me/repository/gradle-plugins-releases/")
        maven("https://nexus.telesphoreo.me/repository/gradle-plugins-snapshots/")
        gradlePluginPortal()
        mavenCentral()
    }
    resolutionStrategy {
        eachPlugin {
            if (requested.id.id == "dev.plex.module") {
                useModule("dev.plex:plex-modules-gradle-plugin:${requested.version}")
            }
        }
    }
}

rootProject.name = "Module-HTTPD"

