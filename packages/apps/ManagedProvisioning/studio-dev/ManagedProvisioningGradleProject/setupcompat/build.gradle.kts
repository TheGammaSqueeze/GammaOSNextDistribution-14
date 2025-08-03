/**
 * This is a copy of external/setupcompat/build.gradle tailored for the library"s inclusion in
 * sysui studio builds.
 */

plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}

val top = extra["ANDROID_TOP"].toString()

android {
    namespace = "com.google.android.setupcompat"
    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "$top/external/setupcompat/proguard.flags")
        }
    }

    sourceSets {
        sourceSets.getByName("main") {
            manifest.srcFile("$top/external/setupcompat/AndroidManifest.xml")
            java.srcDirs(listOf(
                    "$top/external/setupcompat/main/java",
                "$top/external/setupcompat/partnerconfig/java",
            ))
            aidl.srcDirs(listOf("$top/external/setupcompat/main/aidl"))
            res.srcDirs(listOf("$top/external/setupcompat/main/res"))
        }
    }
    buildFeatures {
        aidl = true
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    implementation(libs.androidx.annotation)
    implementation(libs.errorprone.annotations)
    implementation(libs.androidx.window)
    implementation("androidx.core:core-ktx:+")
}
