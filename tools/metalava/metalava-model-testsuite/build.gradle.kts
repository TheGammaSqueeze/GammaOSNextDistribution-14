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
    `java-test-fixtures`
    id("org.jetbrains.kotlin.jvm")
    id("metalava-build-plugin")
}

dependencies {
    implementation(project(":metalava-model"))
    implementation(testFixtures(project(":metalava-model")))
    implementation(project(":metalava-model-source"))
    implementation(project(":metalava-reporter"))
    implementation(project(":metalava-testing"))

    // Needed for the update baseline command.
    implementation(libs.clikt)

    implementation(libs.androidLintTests)
    implementation(libs.junit4)
    implementation(libs.truth)
    implementation(libs.kotlinTest)
}
