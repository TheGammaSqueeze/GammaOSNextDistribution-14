/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

plugins {
    id("com.google.doclava.java-application-conventions")
}

java {
    toolchain {
        languageVersion.set(JavaLanguageVersion.of(17))
    }
}

dependencies {
    implementation("com.google.code.findbugs:jsr305:3.0.2")
}

sourceSets {
    main {
        java {
            srcDir("${project.rootDir}/src")
        }
        resources {
            srcDirs("${project.rootDir}/res")
        }
    }
}

val addedExports = listOf("--add-exports", "jdk.javadoc/jdk.javadoc.internal.tool=ALL-UNNAMED")

tasks.withType<JavaCompile> {
    options.compilerArgs.addAll(addedExports)

    // Exporting a package from system module jdk.javadoc is not allowed with --release so
    // trick gradle to use -source/-target flags instead.
    sourceCompatibility = JavaVersion.VERSION_17.majorVersion
}

tasks.withType<Test> {
    jvmArgs(addedExports)
}

val docletJars by configurations.creating {
    extendsFrom(configurations.runtimeClasspath.get())
}

tasks.javadoc {
    dependsOn("e2eTestAOSP")
}

tasks.create<Exec>("e2eTestAOSP") {
    dependsOn("jar")

    val outputDirectory = project.buildDir.resolve("docs/aosp")
    val docletpath = listOf(
        buildDir.resolve("libs/${project.name}.jar").absolutePath,
        *docletJars.files.map { it.path }.toTypedArray(),
    ).joinToString(separator = ":")

    group = "run"
    workingDir = rootProject.rootDir.resolve("run")
    outputs.dir(outputDirectory)
    outputs.upToDateWhen {
        !tasks.getByPath("jar").didWork
    }

    val javadocTool = javaToolchains.javadocToolFor {
        languageVersion.set(JavaLanguageVersion.of(17))
    }.get().executablePath.toString()

    val args = mutableListOf(
        javadocTool,
        "-d",
        outputDirectory.absolutePath,
        "-doclet",
        application.mainClass.get(),
        "-docletpath",
        docletpath,
        "-encoding", "UTF-8",
        "-sourcepath", "out/soong/.intermediates/frameworks/base/offline-sdk-docs/android_common/srcjars", "@out/soong/.intermediates/frameworks/base/offline-sdk-docs/android_common/javadoc.rsp", "@out/soong/.intermediates/frameworks/base/offline-sdk-docs/android_common/srcjars/list",
        "-bootclasspath", "out/soong/.intermediates/build/soong/java/core-libraries/stable.core.platform.api.stubs/android_common/combined/stable.core.platform.api.stubs.jar:out/soong/.intermediates/libcore/core-lambda-stubs/android_common/javac/core-lambda-stubs.jar",
        "-classpath", "out/soong/.intermediates/frameworks/base/ext/android_common/turbine-combined/ext.jar:out/soong/.intermediates/frameworks/base/framework/android_common/turbine-combined/framework.jar:out/soong/.intermediates/frameworks/opt/net/voip/voip-common/android_common/turbine-combined/voip-common.jar:out/soong/.intermediates/frameworks/base/test-mock/android.test.mock.stubs.system/android_common/turbine-combined/android.test.mock.stubs.system.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-annotations/android_common/turbine-combined/android-support-annotations.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-compat/android_common/turbine-combined/android-support-compat.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-core-ui/android_common/turbine-combined/android-support-core-ui.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-core-utils/android_common/turbine-combined/android-support-core-utils.jar:out/soong/.intermediates/prebuilts/sdk/current/extras/material-design/android-support-design/android_common/turbine-combined/android-support-design.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-dynamic-animation/android_common/turbine-combined/android-support-dynamic-animation.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-exifinterface/android_common/turbine-combined/android-support-exifinterface.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-fragment/android_common/turbine-combined/android-support-fragment.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-media-compat/android_common/turbine-combined/android-support-media-compat.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-percent/android_common/turbine-combined/android-support-percent.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-transition/android_common/turbine-combined/android-support-transition.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-cardview/android_common/turbine-combined/android-support-v7-cardview.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-gridlayout/android_common/turbine-combined/android-support-v7-gridlayout.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-mediarouter/android_common/turbine-combined/android-support-v7-mediarouter.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-palette/android_common/turbine-combined/android-support-v7-palette.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-preference/android_common/turbine-combined/android-support-v7-preference.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v13/android_common/turbine-combined/android-support-v13.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v14-preference/android_common/turbine-combined/android-support-v14-preference.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v17-leanback/android_common/turbine-combined/android-support-v17-leanback.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-vectordrawable/android_common/turbine-combined/android-support-vectordrawable.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-animatedvectordrawable/android_common/turbine-combined/android-support-animatedvectordrawable.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-appcompat/android_common/turbine-combined/android-support-v7-appcompat.jar:out/soong/.intermediates/prebuilts/sdk/current/support/android-support-v7-recyclerview/android_common/turbine-combined/android-support-v7-recyclerview.jar:out/soong/.intermediates/frameworks/rs/support/android-support-v8-renderscript/android_common/turbine-combined/android-support-v8-renderscript.jar:out/soong/.intermediates/frameworks/multidex/library/android-support-multidex/android_common/turbine-combined/android-support-multidex.jar:out/soong/.intermediates/frameworks/multidex/instrumentation/android-support-multidex-instrumentation/android_common/turbine-combined/android-support-multidex-instrumentation.jar:out/soong/.intermediates/tools/metalava/stub-annotations/android_common/turbine-combined/stub-annotations.jar:out/soong/.intermediates/tools/platform-compat/java/android/compat/annotation/unsupportedappusage/android_common/turbine-combined/unsupportedappusage.jar:out/soong/.intermediates/prebuilts/runtime/mainline/i18n/sdk/core-icu4j/android_common/combined/core-icu4j.jar",
        "-android",
        "-manifest", "frameworks_base_core_res_AndroidManifest.xml",
        "-hide", "111",
        "-hide", "113",
        "-hide", "125",
        "-hide", "126",
        "-hide", "127",
        "-hide", "128",
        "-overview", "frameworks_base_core_java_overview.html",
        "-federate", "SupportLib", "https://developer.android.com",
        "-federationapi", "SupportLib", "prebuilts_sdk_current_support-api.txt",
        "-federate", "AndroidX", "https://developer.android.com",
        "-federationapi", "AndroidX", "prebuilts_sdk_current_androidx-api.txt",
        "-offlinemode",
        "-title", "Android SDK",
        "-compatconfig", "out/soong/.intermediates/tools/platform-compat/build/global-compat-config/android_common/all_compat_config.xml",
        "-source", "1.8",
        "-J-Xmx1600m",
        "-XDignore.symbol.file",
        "-hdf", "page.build", "AOSP.MASTER-nikitai-doclava17",
        "-hdf", "page.now", "Wed Feb  1 20:27:46 GMT 2023",
        "-templatedir", "../res/assets/templates-sdk",
        "-htmldir", "frameworks_base_docs/html",
        "-knowntags", "frameworks_base_docs/knowntags.txt",
        "-knowntags", "libcore_known_oj_tags.txt",
        "-hdf", "dac", "true",
        "-hdf", "sdk.codename", "O",
        "-hdf", "sdk.preview.version", "1",
        "-hdf", "sdk.version", "7.0",
        "-hdf", "sdk.rel.id", "1",
        "-hdf", "sdk.preview", "0",
        "-hdf", "android.whichdoc", "offline",
        "-proofread", "out/soong/.intermediates/frameworks/base/offline-sdk-docs/android_common/offline-sdk-docs-proofread.txt",
        "-resourcesdir", "frameworks_base_docs/html/reference/images",
        "-resourcesoutdir", "reference/android/images/",
        // parameters to tweak
        //"-werror",
        //"-lerror",
        if (false) "-verbose" else "-quiet"
    )
    commandLine = args
}
