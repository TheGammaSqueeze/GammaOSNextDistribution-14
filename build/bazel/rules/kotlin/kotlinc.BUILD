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

load("@soong_injection//java_toolchain:constants.bzl", "constants")

package(default_visibility = ["//visibility:public"])

java_import(
    name = "annotations",
    jars = ["lib/annotations-13.0.jar"],
)

java_import(
    name = "jvm_abi_gen_plugin",
    jars = ["lib/jvm-abi-gen.jar"],
)

java_import(
    name = "kotlin_annotation_processing",
    jars = ["lib/kotlin-annotation-processing.jar"],
)

# sh_binary(
#     name = "kotlin_compiler",
#     srcs = ["bin/kotlinc"],
#     data = glob(["lib/**"]),
# )

java_binary(
    name = "kotlin_compiler",
    jvm_flags = ["-Xmx" + constants.JavacHeapSize],
    main_class = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
    runtime_deps = [
        "lib/kotlin-compiler.jar",
        "lib/kotlin-stdlib.jar",
        "lib/trove4j.jar",
    ],
)

# java_binary(
#     name = "kotlin_compiler",
#     main_class = "org.jetbrains.kotlin.cli.jvm.K2JVMCompiler",
#     runtime_deps = [":kotlin_compiler_lib"],
# )

# java_import(
#     name = "kotlin_compiler_lib",
#     jars = ["lib/kotlin-compiler.jar"]
#     srcjar = "lib/kotlin-compiler-sources.jar",
# )

java_import(
    name = "kotlin_reflect",
    jars = ["lib/kotlin-reflect.jar"],
    srcjar = "lib/kotlin-reflect-sources.jar",
)

java_import(
    name = "kotlin_stdlib",
    jars = ["lib/kotlin-stdlib.jar"],
    srcjar = "lib/kotlin-stdlib-sources.jar",
)

java_import(
    name = "kotlin_stdlib_jdk7",
    jars = ["lib/kotlin-stdlib-jdk7.jar"],
    srcjar = "lib/kotlin-stdlib-jdk7-sources.jar",
)

java_import(
    name = "kotlin_stdlib_jdk8",
    jars = ["lib/kotlin-stdlib-jdk8.jar"],
    srcjar = "lib/kotlin-stdlib-jdk8-sources.jar",
)

java_import(
    name = "kotlin_test",
    jars = ["lib/kotlin-test.jar"],
    srcjar = "lib/kotlin-test-sources.jar",
)

alias(
    name = "kotlin_test_not_testonly",
    actual = ":kotlin_test",
)
