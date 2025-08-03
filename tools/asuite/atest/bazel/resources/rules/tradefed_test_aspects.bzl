# Copyright (C) 2021 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Aspects used to transform certain providers into a TradefedTestDependencyInfo.

Tradefed tests require a TradefedTestDependencyInfo provider that is not
usually returned by most rules. Instead of creating custom rules to adapt
build rule providers, we use Bazel aspects to convert the input rule's
provider into a suitable type.

See https://docs.bazel.build/versions/main/skylark/aspects.html#aspects
for more information on how aspects work.
"""

load("//bazel/rules:soong_prebuilt.bzl", "SoongPrebuiltInfo")
load("//bazel/rules:tradefed_test_dependency_info.bzl", "TradefedTestDependencyInfo")

def _soong_prebuilt_tradefed_aspect_impl(target, ctx):
    runtime_jars = []
    runtime_shared_libraries = []
    for f in target[SoongPrebuiltInfo].transitive_runtime_outputs.to_list():
        if f.extension == "so":
            runtime_shared_libraries.append(f)
        elif f.extension == "jar":
            runtime_jars.append(f)

    return [
        TradefedTestDependencyInfo(
            runtime_jars = depset(runtime_jars),
            runtime_shared_libraries = depset(runtime_shared_libraries),
            transitive_test_files = target[SoongPrebuiltInfo].transitive_test_files,
        ),
    ]

soong_prebuilt_tradefed_test_aspect = aspect(
    implementation = _soong_prebuilt_tradefed_aspect_impl,
)
