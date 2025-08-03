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

load("//build/bazel/rules:hidl_file_utils.bzl", "LANGUAGE_CC_HEADERS", "LANGUAGE_CC_SOURCES", "hidl_file_utils")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load("//build/bazel/rules/cc:cc_library_static.bzl", "cc_library_static")
load("//build/bazel/rules/hidl:hidl_library.bzl", "HidlInfo")
load(":cc_library_common.bzl", "create_ccinfo_for_includes")

CC_SOURCE_SUFFIX = "_genc++"
CC_HEADER_SUFFIX = "_genc++_headers"
CORE_PACKAGES = ["android.hidl.base@", "android.hidl.manager@"]

def _cc_hidl_code_gen_rule_impl(ctx):
    hidl_info = ctx.attr.dep[HidlInfo]
    outs = hidl_file_utils.generate_hidl_action(
        hidl_info,
        ctx.attr.language,
        ctx,
    )

    return [
        DefaultInfo(files = depset(direct = outs)),
        create_ccinfo_for_includes(ctx, includes = [ctx.label.name]),
    ]

_cc_hidl_code_gen = rule(
    implementation = _cc_hidl_code_gen_rule_impl,
    attrs = {
        "dep": attr.label(
            providers = [HidlInfo],
            doc = "hidl_library that exposes HidlInfo provider with *.hal files",
            mandatory = True,
        ),
        "language": attr.string(
            mandatory = True,
            values = ["c++-headers", "c++-sources"],
        ),
        "_hidl_gen": attr.label(
            allow_single_file = True,
            default = Label("//prebuilts/build-tools:linux-x86/bin/hidl-gen"),
            executable = True,
            cfg = "exec",
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)

def cc_hidl_library(
        name,
        interface,
        dynamic_deps = [],
        min_sdk_version = "",
        tags = []):
    srcs_name = name + CC_SOURCE_SUFFIX
    hdrs_name = name + CC_HEADER_SUFFIX

    _cc_hidl_code_gen(
        name = srcs_name,
        dep = interface,
        language = LANGUAGE_CC_SOURCES,
        tags = ["manual"],
    )

    _cc_hidl_code_gen(
        name = hdrs_name,
        dep = interface,
        language = LANGUAGE_CC_HEADERS,
        tags = ["manual"],
    )

    # Don't generate the cc library target for the core interfaces, they are parts
    # of the libhidlbase
    if _is_core_package(name):
        return

    combined_dynamic_deps = [
        "//system/libhidl:libhidlbase",
        "//system/core/libutils:libutils",
    ]
    implementation_dynamic_deps = [
        "//system/core/libcutils:libcutils",
    ] + select({
        "//build/bazel/rules/apex:android-in_apex": ["//system/logging/liblog:liblog_stub_libs_current"],
        "//conditions:default": ["//system/logging/liblog:liblog"],
    })

    for dep in dynamic_deps:
        # Break up something like: //system/libhidl/transport/base/1.0:android.hidl.base@1.0
        # and get the interface name such as android.hidl.base@1.0.
        parts = dep.split(":")
        dep_name = parts[1] if len(parts) == 2 else dep

        # core packages will be provided by libhidlbase
        if not _is_core_package(dep_name):
            combined_dynamic_deps.append(dep)

    common_attrs = dict(
        [
            ("srcs", [":" + srcs_name]),
            ("hdrs", [":" + hdrs_name]),
            ("dynamic_deps", combined_dynamic_deps),
            ("implementation_dynamic_deps", implementation_dynamic_deps),
            ("export_includes", ["."]),
            ("local_includes", ["."]),
            ("copts", [
                "-Wall",
                "-Werror",
                "-Wextra-semi",
            ] + select({
                "//build/bazel/product_config/config_settings:debuggable": ["-D__ANDROID_DEBUGGABLE__"],
                "//conditions:default": [],
            })),
            ("min_sdk_version", min_sdk_version),
            ("tags", tags),
        ],
    )

    cc_library_shared(
        name = name,
        **common_attrs
    )

    cc_library_static(
        name = name + "_bp2build_cc_library_static",
        **common_attrs
    )

def _is_core_package(name):
    for pkg in CORE_PACKAGES:
        if name.startswith(pkg):
            return True

    return False
