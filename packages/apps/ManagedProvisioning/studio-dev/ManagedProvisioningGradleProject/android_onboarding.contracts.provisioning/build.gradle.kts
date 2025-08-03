/**
 * This is an adaptation of the build.gradle found in external/android_onboarding.
 * There are certain classes that must be modified in order to work with the sysui studio build.
 */
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.kapt.get().pluginId)
}

val top = extra["ANDROID_TOP"].toString()
val moduleDir =
        "$top/external/android_onboarding/src/com/android/onboarding/contracts/provisioning"

android {
    namespace = "com.android.onboarding.contracts.provisioning"
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
        }
    }

    sourceSets {
        sourceSets.getByName("main") {
            java.srcDirs(listOf("src", symlinkedSources(moduleDir)))
        }
    }
}

dependencies {
    api(project(":android_onboarding.common"))
    api(project(":android_onboarding.contracts"))
    api(project(":android_onboarding.contracts.setupwizard"))
    api(project(":android_onboarding.contracts.annotations"))
    api(project(":setupcompat"))
    api(libs.androidx.annotation)
    api(libs.javax.inject)
}