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

load("@bazel_skylib//:bzl_library.bzl", "bzl_library")
load(":unittests.bzl", "unittests")

package(
    default_testonly = True,
)

licenses(["notice"])

unittests.render(
    name = "unittests",
)

bzl_library(
    name = "unittests_bzl",
    srcs = ["unittests.bzl"],
    deps = ["//kotlin/common/testing:testing_bzl"],
)
