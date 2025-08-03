# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

workspace(name = "rules_kotlin")

load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-5.2",
    sha256 = "3824ac95d9edf8465c7a42b7fcb88a5c6b85d2bac0e98b941ba13f235216f313",
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/5.2.zip",
)
load("@rules_jvm_external//:repositories.bzl", "rules_jvm_external_deps")
rules_jvm_external_deps()
load("@rules_jvm_external//:setup.bzl", "rules_jvm_external_setup")
rules_jvm_external_setup()
load("@rules_jvm_external//:defs.bzl", "maven_install")

http_archive(
    name = "bazel_skylib",
    sha256 = "f7be3474d42aae265405a592bb7da8e171919d74c16f082a5457840f06054728",
    urls = [
        "https://github.com/bazelbuild/bazel-skylib/releases/download/1.2.1/bazel-skylib-1.2.1.tar.gz",
    ],
)
load("@bazel_skylib//:workspace.bzl", "bazel_skylib_workspace")
bazel_skylib_workspace()

http_archive(
    name = "bazel_platforms",
    sha256 = "379113459b0feaf6bfbb584a91874c065078aa673222846ac765f86661c27407",
    urls = [
        "https://github.com/bazelbuild/platforms/releases/download/0.0.5/platforms-0.0.5.tar.gz",
    ],
)

http_archive(
    name = "rules_license",
    sha256 = "4531deccb913639c30e5c7512a054d5d875698daeb75d8cf90f284375fe7c360",
    urls = [
        "https://github.com/bazelbuild/rules_license/releases/download/0.0.7/rules_license-0.0.7.tar.gz",
    ],
)
load("@rules_license//:deps.bzl", "rules_license_dependencies")
rules_license_dependencies()

http_archive(
    name = "rules_java",
    urls = [
      "https://mirror.bazel.build/github.com/bazelbuild/rules_java/releases/download/5.5.0/rules_java-5.5.0.tar.gz",
      "https://github.com/bazelbuild/rules_java/releases/download/5.5.0/rules_java-5.5.0.tar.gz",
    ],
    sha256 = "bcfabfb407cb0c8820141310faa102f7fb92cc806b0f0e26a625196101b0b57e",
)
load("@rules_java//java:repositories.bzl", "java_tools_repos")
java_tools_repos()

http_archive(
    name = "dagger",
    strip_prefix = "dagger-dagger-2.44.2",
    build_file = "@//bazel:dagger.BUILD",
    sha256 = "cbff42063bfce78a08871d5a329476eb38c96af9cf20d21f8b412fee76296181",
    urls = ["https://github.com/google/dagger/archive/dagger-2.44.2.zip"],
)
load("@dagger//:workspace_defs.bzl", "DAGGER_ARTIFACTS", "DAGGER_REPOSITORIES")

load("@//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", "KT_VERSION")
http_archive(
    name = "kotlinc",
    build_file = "@//bazel:kotlinc.BUILD",
    sha256 = "7d74863deecf8e0f28ea54c3735feab003d0eac67e8d3a791254b16889c20342",
    strip_prefix = "kotlinc",
    urls = [
        "https://github.com/JetBrains/kotlin/releases/download/v{0}/kotlin-compiler-{0}.zip".format(KT_VERSION[1:].replace("_", ".")),
    ],
)

register_toolchains("@//toolchains/kotlin_jvm:all")

maven_install(
    artifacts = DAGGER_ARTIFACTS + [
        "com.google.auto.service:auto-service-annotations:1.0.1",
        "com.google.auto.service:auto-service:1.0.1",
        "com.google.auto.value:auto-value:1.9",
        "com.google.testing.compile:compile-testing:0.19",
        "com.google.truth:truth:1.1.3",
        "info.picocli:picocli:4.6.3",
        "javax.inject:jsr330-api:0.9",
        "junit:junit:4.13.2",
        "org.jacoco:org.jacoco.agent:0.8.8",
        "org.jacoco:org.jacoco.cli:0.8.8",
        "org.jetbrains.kotlinx:kotlinx-metadata-jvm:0.4.2",
        "org.mockito:mockito-core:4.5.1",
    ],
    repositories = DAGGER_REPOSITORIES + [
        "https://repository.mulesoft.org/nexus/content/repositories/public",
    ],
    override_targets = {
        "org.jetbrains.kotlin:annotations": "@kotlinc//:annotations",
        "org.jetbrains.kotlin:kotlin-reflect": "@kotlinc//:kotlin_reflect",
        "org.jetbrains.kotlin:kotlin-stdlib": "@kotlinc//:kotlin_stdlib",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk7": "@kotlinc//:kotlin_stdlib_jdk7",
        "org.jetbrains.kotlin:kotlin-stdlib-jdk8": "@kotlinc//:kotlin_stdlib_jdk8",
        "org.jetbrains.kotlin:kotlin-test": "@kotlinc//:kotlin_test",
    },
)

