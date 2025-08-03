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
    namespace = "com.google.android.setupdesign"
    defaultConfig {
        vectorDrawables.useSupportLibrary = true
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android.txt"), "$top/external/setupdesign/proguard.flags")
        }
    }

    sourceSets {
        sourceSets.getByName("main") {
            manifest.srcFile("$top/external/setupdesign/main/AndroidManifest.xml")
            java.srcDirs(listOf("src", "$top/external/setupdesign/main/src"))
            res.srcDirs(listOf("$top/external/setupdesign/main/res"))
        }
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}

dependencies {
    api(libs.androidx.annotation)
    api(libs.androidx.appcompat)
    api(libs.androidx.core)
    api(libs.androidx.legacy.support.core.ui)
    api(libs.androidx.recyclerview)
    api(libs.androidx.window)
    api(libs.com.google.android.material)
    api(libs.errorprone.annotations)
    api(project(":setupcompat"))
    api(project(":setupdesign-strings"))
    implementation("androidx.core:core-ktx:+")
}