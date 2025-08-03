# Copyright (C) 2023 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
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

load(":bazel_sandwich_imported_file.bzl", "bazel_sandwich_imported_file")

package(default_visibility = [
    "//visibility:public",
])

bazel_sandwich_imported_file(
    name = "system_staging_dir",
    # Don't add a dependency on the system folder itself, because there's no rule in make that
    # generates it. (it's created implicitly by the rules that install actual files) Depending on it
    # causes soong's test for dangling rules to fail (part of `m checkbuild`). The implicit dep on
    # the stamp file will handle all the dependencies we need.
    depend_on_target = False,
    implicit_deps = ["target/product/$(DeviceName)/obj/PACKAGING/systemimage_intermediates/staging_dir.stamp"],
    target = "target/product/$(DeviceName)/system",
)

bazel_sandwich_imported_file(
    name = "system_staging_dir_file_list",
    target = "target/product/$(DeviceName)/obj/PACKAGING/systemimage_intermediates/file_list.txt",
)

bazel_sandwich_imported_file(
    name = "root_staging_dir",
    depend_on_target = False,
    implicit_deps = ["target/product/$(DeviceName)/obj/PACKAGING/systemimage_intermediates/staging_dir.stamp"],
    target = "target/product/$(DeviceName)/root",
)

bazel_sandwich_imported_file(
    name = "selinux_file_contexts",
    target = "target/product/$(DeviceName)/obj/ETC/file_contexts.bin_intermediates/file_contexts.bin",
)

bazel_sandwich_imported_file(
    name = "make_system_image",
    target = "target/product/$(DeviceName)/system.img",
)
