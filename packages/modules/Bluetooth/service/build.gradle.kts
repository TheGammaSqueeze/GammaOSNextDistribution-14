plugins {
    // Apply the org.jetbrains.kotlin.jvm Plugin to add support for Kotlin.
    id("org.jetbrains.kotlin.jvm") version "1.8.20"

    // Apply the java-library plugin for API and implementation separation.
    `java-library`
}

repositories {
    // Use Maven Central for resolving dependencies.
    mavenCentral()
}

sourceSets.main {
    java {
        exclude("**/*.bp")
        srcDirs("src", "aidl", "change-ids")
    }
}

dependencies {
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.7.2")
    implementation(files("../../../../external/kotlinc/lib/kotlin-stdlib.jar"))
    implementation(files("../../../../out/soong/.intermediates/frameworks/libs/modules-utils/java/com/android/modules/utils/build/modules-utils-build_system/android_common_apex33/turbine-combined/modules-utils-build_system.jar"))
    implementation(files("../../../../out/soong/.intermediates/frameworks/libs/modules-utils/java/com/android/modules/utils/modules-utils-shell-command-handler/android_common_apex33/turbine-combined/modules-utils-shell-command-handler.jar"))
    implementation(files("../../../../out/soong/.intermediates/frameworks/libs/modules-utils/java/framework-annotations-lib/android_common/turbine-combined/framework-annotations-lib.jar"))
    implementation(files("../../../../out/soong/.intermediates/packages/modules/Bluetooth/framework/framework-bluetooth-pre-jarjar/android_common/turbine-combined/framework-bluetooth-pre-jarjar.jar"))
    implementation(files("../../../../out/soong/.intermediates/packages/modules/Bluetooth/service/change-ids/service-bluetooth.change-ids/android_common/turbine-combined/service-bluetooth.change-ids.jar"))
    implementation(files("../../../../prebuilts/sdk/33/system-server/android.jar"))
}

