/**
 * This is an adaptation of the build.gradle found in external/android_onboarding.
 * There are certain classes that must be modified in order to work with the sysui studio build.
 */
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.kapt.get().pluginId)
    id(libs.plugins.hilt.get().pluginId)
}

val top = extra["ANDROID_TOP"].toString()
val moduleDir = "$top/external/android_onboarding/src/com/android/onboarding/flags"

android {
    namespace = "com.android.onboarding.flags"
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        named("main") {
            java.srcDirs("src", symlinkedSources(moduleDir) {
                include("OnboardingFlagsHiltModule.kt")
            })
        }
    }
}

dependencies {
    api(project(":android_onboarding.flags"))
    api(libs.dagger)
    api(libs.dagger.android)
    kapt(libs.dagger.compiler)
    kapt(libs.dagger.android.processor)

    api(libs.hilt.android)
    kapt(libs.hilt.android.compiler)
}