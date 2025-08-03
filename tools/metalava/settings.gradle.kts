/*
 * Copyright (C) 2018 The Android Open Source Project
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

pluginManagement {
    repositories {
        // Prefer mavenCentral as that has signed artifacts
        mavenCentral()
        gradlePluginPortal()
        google()
    }
}

dependencyResolutionManagement {
    repositories {
        google()
        mavenCentral()
        val customLintRepo = System.getenv("LINT_REPO")
        if (customLintRepo != null) {
            logger.warn("Building using custom $customLintRepo maven repository")
            maven { url = uri(customLintRepo) }
        }
    }
    versionCatalogs {
        create("libs") {
            val lintOverride = System.getenv("LINT_VERSION")
            if (lintOverride != null) {
                logger.warn("Building using custom $lintOverride version of Android Lint.")
                version("androidLint", lintOverride)
            }
        }
    }
}

settings.gradle.beforeProject {
    val outDir = if (System.getenv("OUT_DIR") != null) {
        File(System.getenv("OUT_DIR"))
    } else {
        File(rootDir, "../../out")
    }
    val suffix = "${path.replace(":", "/")}/build"
    layout.buildDirectory.set(File(outDir, "metalava$suffix"))
}

if (!System.getenv("INTEGRATION").isNullOrBlank()) {
    include(":integration")
}

include(":metalava")
include(":metalava-model")
include(":metalava-model-psi")
include(":metalava-model-source")
include(":metalava-model-testsuite")
include(":metalava-model-testsuite-cli")
include(":metalava-model-text")
include(":metalava-model-turbine")
include(":metalava-reporter")
include(":metalava-testing")
include(":stub-annotations")
