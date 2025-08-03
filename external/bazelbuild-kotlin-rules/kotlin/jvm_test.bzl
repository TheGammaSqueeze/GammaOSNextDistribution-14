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

"""Kotlin macro for building and running tests on a JVM."""

load("//bazel:stubs.bzl", "register_extension_info")
load("//:visibility.bzl", "RULES_KOTLIN")
load(":jvm_library.bzl", "kt_jvm_library")

visibility(RULES_KOTLIN)

def _lib_name(name):
    return "%s_DO_NOT_DEPEND_LIB" % name

def kt_jvm_test(
        name,
        custom_kotlincopts = None,
        deps = None,
        disable_lint_checks = None,
        features = None,
        javacopts = None,
        plugins = None,
        runtime_deps = None,
        srcs = None,
        resources = None,
        tags = None,
        **kwargs):
    """Wrapper around kt_jvm_library and java_test to conveniently declare tests written in Kotlin.

    Use of this rule is discouraged for simple unit tests, which should instead use
     go/junit_test_suites or other, more efficient ways of compiling and running unit tests.

    Args:
      name: Name of the target.
      custom_kotlincopts: Additional flags to pass to Kotlin compiler defined by kt_compiler_opt.
      deps: A list of dependencies.
      disable_lint_checks: A list of AndroidLint checks to be skipped.
      features: A list of enabled features, see go/be#common.features.
      javacopts: Additional flags to pass to javac if used.
      plugins: Java annotation processors to run at compile-time.
      runtime_deps: A list of runtime dependencies.
      srcs: A list of sources to compile.
      tags: A list of string tags passed to generated targets.
      resources: A list of data files to include in the jar file.
      **kwargs: Additional parameters to pass on to generated java_test, see go/be-java#java_test.
    """
    if srcs:
        runtime_deps = [_lib_name(name)] + (runtime_deps or [])

        kt_jvm_library(
            name = _lib_name(name),
            srcs = srcs,
            resources = resources,
            deps = deps,
            plugins = plugins,
            javacopts = javacopts,
            custom_kotlincopts = custom_kotlincopts,
            disable_lint_checks = disable_lint_checks,
            tags = tags,
            features = features,
            testonly = 1,
            visibility = ["//visibility:private"],
        )
    elif deps:
        fail("deps specified without sources. Use runtime_deps instead to specify any dependencies needed to run this test.")

    native.java_test(
        name = name,
        runtime_deps = runtime_deps,
        tags = tags,
        features = features,
        **kwargs
    )

register_extension_info(
    extension = kt_jvm_test,
    label_regex_for_dep = "{extension_name}_DO_NOT_DEPEND_LIB",
)
