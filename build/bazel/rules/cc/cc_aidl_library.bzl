# Copyright (C) 2022 The Android Open Source Project
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

load("//build/bazel/rules/cc:cc_aidl_code_gen.bzl", "cc_aidl_code_gen")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")

def cc_aidl_library(
        name,
        deps = [],
        lang = "cpp",
        make_shared = False,
        **kwargs):
    """
    Generate AIDL stub code for C++ and wrap it in a cc_library_static target

    Args:
        name (str):               name of the cc_library_static target
        deps (list[AidlGenInfo]): list of all aidl_libraries that this cc_aidl_library depends on
        make_shared (bool):       if true, `name` will refer to a cc_library_shared,
                                  and an additional cc_library_static will be created
                                  if false, `name` will refer to a cc_library_static
        **kwargs:                 extra arguments that will be passesd to cc_library_{static,shared}.
    """

    if lang not in ["cpp", "ndk"]:
        fail("lang {} is unsupported. Allowed lang: ndk, cpp.".format(lang))

    aidl_code_gen = name + "_aidl_code_gen"
    cc_aidl_code_gen(
        name = aidl_code_gen,
        deps = deps,
        lang = lang,
        min_sdk_version = kwargs.get("min_sdk_version", None),
        tags = kwargs.get("tags", []) + ["manual"],
    )

    arguments_with_kwargs = dict(
        kwargs,
        srcs = [":" + aidl_code_gen],
        deps = [aidl_code_gen],
    )

    static_name = name
    if make_shared:
        cc_library_shared(
            name = name,
            **arguments_with_kwargs
        )
        static_name = name + "_bp2build_cc_library_static"

    cc_library_static(
        name = static_name,
        **arguments_with_kwargs
    )
