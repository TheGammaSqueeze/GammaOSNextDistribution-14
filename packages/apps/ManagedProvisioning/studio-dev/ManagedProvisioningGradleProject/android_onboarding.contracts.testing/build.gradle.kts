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
        "$top/external/android_onboarding/src/com/android/onboarding/contracts/testing"
val robolibBuildDir = project(":RobolectricLib").buildDir.toString()

android {
    namespace = "com.android.onboarding.contracts.testing"
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
            manifest.srcFile("$moduleDir/AndroidManifest.xml")
            java.srcDirs(listOf("src", symlinkedSources(moduleDir) {
                // Excluded until g3 finishes appcompat migration
                exclude("TestAppCompatActivity.kt")
            }))
        }
    }
}

dependencies {
    api(libs.androidx.annotation)
    api(project(":android_onboarding.contracts"))
    api(libs.androidx.test.core)
    api(libs.androidx.activity.ktx)
    api(libs.androidx.fragment.ktx)
    api(libs.androidx.appcompat)

    api(project(":RobolectricLib"))
    // this is compile only, to work around the incomplete MockSDK provided to SysUIStudio
    // from it's ./studiow script.  Robolectric will provide this jar at runtime via
    // It's SdkProvider and will also apply shadow logic at that time.
    api(fileTree("${robolibBuildDir}/android_all/") {
        include("*.jar")
    })
    api(libs.truth)
}