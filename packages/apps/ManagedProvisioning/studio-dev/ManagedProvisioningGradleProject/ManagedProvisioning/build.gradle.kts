import com.android.build.api.artifact.SingleArtifact
import com.google.protobuf.gradle.proto
import java.util.Locale

plugins {
    id("aconfig")
    id(libs.plugins.android.application.get().pluginId)
    id(libs.plugins.protobuf.get().pluginId)
    id(libs.plugins.kotlin.android.get().pluginId)
    id(libs.plugins.kotlin.kapt.get().pluginId)
    id(libs.plugins.hilt.get().pluginId)
}
apply<ResourceFixerPlugin>()

val androidTop = extra["ANDROID_TOP"].toString()
val moduleDir = extra["MANAGED_PROVISIONING_DIR"].toString()
val googleTruthVersion = extra["google_truth_version"].toString()
val robolibBuildDir = project(":RobolectricLib").buildDir.toString()

aconfig {
    aconfigDeclaration {
        packageName.set("com.android.managedprovisioning.flags")
        containerName.set("system")
        srcFile.setFrom(fileTree("$moduleDir/aconfig").matching {
            include("*.aconfig")
        })
    }
}

hilt {
    enableAggregatingTask = false
}

android {
    namespace = "com.android.managedprovisioning"
    testNamespace = "com.android.managedprovisioning.tests"
    defaultConfig {
        // TODO: Remove this once b/78467428 is resolved
        vectorDrawables.useSupportLibrary = true
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        testApplicationId = "com.android.managedprovisioning.tests"

        versionCode = 1000000
        versionName = "BuildFromAndroidStudio"
        applicationId = "com.android.managedprovisioning"
    }
    signingConfigs {
        getByName("debug") {
            storeFile = file("$androidTop/vendor/google/certs/devkeys/platform.keystore")
        }
    }
    buildTypes {
        release {
            proguardFiles("$moduleDir/proguard.flags")
        }
    }

    sourceSets {
        named("main") {
            manifest.srcFile("$moduleDir/AndroidManifest.xml")
            res {
                srcDir("$moduleDir/res")
            }
            proto {
                srcDir("$moduleDir/proto")
            }
            aidl {
                srcDir("$moduleDir/src")
            }
            java {
                srcDir("$moduleDir/src")
            }
        }

        named("androidTest") {
            java {
                srcDir("$moduleDir/tests/instrumentation/src")
            }
            manifest {
                srcFile("$moduleDir/tests/instrumentation/AndroidManifest.xml")
            }
            res {
                srcDir("$moduleDir/tests/instrumentation/res")
            }
        }

        named("test") {
            java {
                srcDir("$moduleDir/tests/robotests/src")
                resources {
                    srcDir("$moduleDir/tests/robotests/config/")
                }
            }
            manifest {
                srcFile("$moduleDir/tests/instrumentation/AndroidManifest-base.xml")
            }
            res {
                srcDir("$moduleDir/tests/tests/instrumentation/res")
            }
        }
    }

    // Do not generate META-INF
    packagingOptions.jniLibs.excludes.add("META-INF/*")
    packagingOptions.resources.excludes.add("META-INF/*")
    packagingOptions.resources.excludes.add("protobuf.meta")

    buildFeatures {
        aidl = true
    }
    testOptions {
        unitTests {
            isIncludeAndroidResources = true
        }
    }
}

androidComponents {
    // Disable the "release" buildType for ManagedProvisioning
    beforeVariants(selector().all()) {
        it.enable = it.buildType != "release"
    }

    onVariants { variant ->
        // Capitalized variant name, e.g. debugGoogle -> DebugGoogle
        val variantName = variant.name.replaceFirstChar {
            if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString()
        }
        project.tasks.register<PushApkTask>("pushManagedProvisioning${variantName}Apk") {
            workingDir = rootDir
            apkFolder.set(variant.artifacts.get(SingleArtifact.APK))
            builtArtifactsLoader.set(variant.artifacts.getBuiltArtifactsLoader())
        }
    }
}

kapt {
    correctErrorTypes = true
}

dependencies {
    implementation(libs.protobuf.javalite)
    kapt("com.google.auto.value:auto-value:1.7.4")
    compileOnly("com.google.auto.value:auto-value-annotations:1.7.4")

    // Common dependencies
    api("androidx.annotation:annotation")
    api(libs.androidx.legacy.support.v4)
    api("androidx.webkit:webkit")
    api(libs.javax.inject)

    api(project(":android_onboarding.contracts.provisioning"))
    api(project(":android_onboarding.contracts.annotations"))
    api(project(":android_onboarding.contracts.setupwizard"))
    api(project(":android_onboarding.flags_hilt"))
    api(project(":setupdesign"))
    api(project(":setupdesign"))
    api(project(":setupdesign-lottie-loading-layout"))

    // Dagger
    api(libs.dagger)
    api(libs.dagger.android)
    kapt(libs.dagger.compiler)
    kapt(libs.dagger.android.processor)

    api(libs.hilt.android)
    kapt(libs.hilt.android.compiler)

    kaptTest(libs.dagger.compiler)
    kaptTest(libs.dagger.android.processor)
    kaptTest(libs.hilt.android.compiler)

    kaptAndroidTest(libs.dagger.compiler)
    kaptAndroidTest(libs.dagger.android.processor)
    kaptAndroidTest(libs.hilt.android.compiler)

    api(libs.guava)
    api(libs.com.airbnb.android.lottie)

    testImplementation(project(":RobolectricLib"))
    // this is compile only, to work around the incomplete MockSDK provided to SysUIStudio
    // from it's ./studiow script.  Robolectric will provide this jar at runtime via
    // It's SdkProvider and will also apply shadow logic at that time.
    testImplementation(fileTree("${robolibBuildDir}/android_all/") {
        include("*.jar")
    })
    testImplementation(libs.androidx.test.rules)
    testImplementation(libs.testng)
    testImplementation(libs.hilt.android.testing)
    testImplementation(project(":android_onboarding.contracts.testing"))
    testImplementation("androidx.test.ext:junit")
    // testImplementation("androidx.compose.ui:ui")
    testImplementation("androidx.test:core:1.5.0")
    testImplementation("androidx.test.ext:truth")
    // testImplementation("androidx.core:core-animation-testing")
    testImplementation(libs.truth)
    testImplementation(libs.google.truth)
    testImplementation("org.mockito:mockito-core:2.28.1")
    // //this needs to be modern to support JDK-17 + asm byte code.
    testImplementation("org.mockito:mockito-inline:4.11.0")
    testImplementation(libs.junit)
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
