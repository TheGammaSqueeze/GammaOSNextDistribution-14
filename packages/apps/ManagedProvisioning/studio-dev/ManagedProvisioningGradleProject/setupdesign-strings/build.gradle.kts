/**
 * This is an adaptation of the build.gradle found in external/setupdesign. There are certain
 * classes that must be modified in order to work with the sysui studio build.
 */
plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val top = extra["ANDROID_TOP"].toString()

android {
    namespace = "com.google.android.setupdesign.strings"
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    sourceSets {
        sourceSets.getByName("main") {
            manifest.srcFile("$top/external/setupdesign/strings/AndroidManifest.xml")
            res.srcDirs(listOf("$top/external/setupdesign/strings/res"))
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation("androidx.core:core-ktx:+")
}
