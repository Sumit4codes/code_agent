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
    }
}

rootProject.name = "CodeAgent"

include(":app")
include(":core:common")
include(":core:model")
include(":core:ui")
include(":core:ai")
include(":core:agent")
include(":core:files")
include(":core:data")
include(":core:git")
include(":core:terminal")
include(":core:testing")
include(":feature:projects")
include(":feature:chat")
include(":feature:editor")
include(":feature:settings")
