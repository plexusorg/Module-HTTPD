pluginManagement {
    repositories {
        maven("https://telesphorepo.com/maven/gradle-plugins-releases/")
        maven("https://telesphorepo.com/maven/gradle-plugins-snapshots/")
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