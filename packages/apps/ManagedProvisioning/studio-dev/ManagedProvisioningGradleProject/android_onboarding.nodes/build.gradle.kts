import com.google.protobuf.gradle.proto

/**
 * This is an adaptation of the build.gradle found in external/android_onboarding.
 * There are certain classes that must be modified in order to work with the sysui studio build.
 */
plugins {
    id(libs.plugins.android.library.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.kapt.get().pluginId)
    id(libs.plugins.protobuf.get().pluginId)
}

val top = extra["ANDROID_TOP"].toString()
val moduleDir =
        "$top/external/android_onboarding/src/com/android/onboarding/nodes"

android {
    namespace = "com.android.onboarding.nodes"
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
            java.srcDirs(listOf("src", symlinkedSources(moduleDir)))
            proto {
                setSrcDirs(listOf(moduleDir))
            }
        }
    }
}

dependencies {
    implementation(libs.protobuf.javalite)
    api("androidx.activity:activity-ktx")
    api(libs.guava)
    api(project(":android_onboarding.contracts.annotations"))
    api(project(":android_onboarding.flags"))
}

protobuf {
    // Configure the protoc executable
    protoc {
        artifact = "${libs.protobuf.protoc.get()}"
    }
    generateProtoTasks {
        all().configureEach {
            builtins {
                register("java") {
                    option("lite")
                }
            }
        }
    }
}
