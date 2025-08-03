plugins {
    id("com.android.library")
    id("org.jetbrains.kotlin.android")
}
val androidTop = extra["ANDROID_TOP"].toString()
android {
    namespace = "com.android.internal.modules.utils"

    sourceSets.getByName("main") {
        java.setSrcDirs(listOf(
                "$androidTop/frameworks/libs/modules-utils/java",
        ))
        java.exclude(
                "android/annotations/**",
                "com/android/internal/**",
                "com/android/modules/**",
        )
        manifest.srcFile("empty-manifest.xml")
    }
    kotlinOptions {
        jvmTarget = "17"
    }
}
dependencies {
    implementation("androidx.core:core-ktx:+")
}
