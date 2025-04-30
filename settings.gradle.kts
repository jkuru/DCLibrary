pluginManagement {
    repositories {
        google() // <-- Essential for Android/Compose plugins
        mavenCentral() // <-- Essential for many libraries and Kotlin plugins
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
    }
}

rootProject.name = "FeatureFlow"
include(":app")
 