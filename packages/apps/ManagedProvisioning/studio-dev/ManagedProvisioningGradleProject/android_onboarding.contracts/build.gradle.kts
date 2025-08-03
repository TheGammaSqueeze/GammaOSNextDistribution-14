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
        "$top/external/android_onboarding/src/com/android/onboarding/contracts"

android {
    namespace = "com.android.onboarding.contracts"
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "$moduleDir/proguard.pgcfg")
        }
    }

    sourceSets {
        sourceSets.getByName("main") {
            java.srcDirs(listOf("src", symlinkedSources(moduleDir)))
        }
    }
}

dependencies {
    api("androidx.activity:activity-ktx")
    api(project(":android_onboarding.contracts.annotations"))
    api(project(":android_onboarding.nodes"))
    api(libs.errorprone.annotations)
}