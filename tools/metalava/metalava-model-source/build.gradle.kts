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

// Subproject containing code that is common to all models that are produced from source code.

plugins {
    `java-library`
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("metalava-build-plugin")
    id("maven-publish")
}

dependencies {
    implementation(project(":metalava-model"))
    implementation(project(":metalava-reporter"))

    testFixturesImplementation(project(":metalava-model"))
    testFixturesImplementation(project(":metalava-model-testsuite"))
    testFixturesImplementation(project(":metalava-reporter"))
    testFixturesImplementation(libs.androidLintTests)
    testFixturesImplementation(project(":metalava-testing"))

    testImplementation(libs.androidLintTests)
    testImplementation(libs.junit4)
    testImplementation(libs.truth)
    testImplementation(libs.kotlinTest)
}
