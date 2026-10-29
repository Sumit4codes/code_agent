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
        maven("https://jitpack.io")
    }
}

rootProject.name = "KodaDev"

// On-device ARM64 build support: If running on an aarch64/arm64 host and a local ARM64 aapt2
// binary is present, automatically configure android.aapt2FromMavenOverride so AGP doesn't download
// the incompatible x86_64 aapt2 from Maven.
val osArch = System.getProperty("os.arch")?.lowercase() ?: ""
if (osArch.contains("aarch64") || osArch.contains("arm64")) {
    val bundledAapt2 = file("tools/bin/arm64-v8a/aapt2")
    if (bundledAapt2.exists() && !gradle.startParameter.projectProperties.containsKey("android.aapt2FromMavenOverride")) {
        gradle.startParameter.projectProperties["android.aapt2FromMavenOverride"] = bundledAapt2.absolutePath
    }
}

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
include(":feature:terminal")
