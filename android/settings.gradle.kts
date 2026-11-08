pluginManagement {
    repositories {
        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // 공식 Pleos Playground Maven (docs/pleos-only/connect-sdk-pleos/Vehicle/project-setup)
        maven {
            url = uri("https://nexus-playground.pleos.ai/repository/maven-releases/")
            // SpeechToText 가 ai.pleos.playground.security:universal-access 를 의존
            content { includeGroupByRegex("ai\\.pleos\\.playground(\\..*)?") }
        }
    }
}

rootProject.name = "loadmate"
include(":app")
