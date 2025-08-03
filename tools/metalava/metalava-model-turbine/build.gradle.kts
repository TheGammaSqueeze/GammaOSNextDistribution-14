/*
 * Copyright (C) 2023 The Android Open Source Project
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
    `java-library`
    id("org.jetbrains.kotlin.jvm")
    id("metalava-build-plugin")
    id("maven-publish")

    // This project provides an implementation of the metalava-model.
    id("metalava-model-provider-plugin")
}

dependencies {
    implementation(project(":metalava-model-source"))
    implementation(libs.turbine) { exclude(group = "com.google.protobuf") }

    // Pick up the SourceModelSuiteRunner service to run the `metalava-model-testsuite`.
    testImplementation(testFixtures(project(":metalava-model-source")))
    testImplementation(project(":metalava-model-testsuite"))
    testImplementation(project(":metalava-testing"))
    testImplementation(libs.androidLintTests)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinTest)
}
