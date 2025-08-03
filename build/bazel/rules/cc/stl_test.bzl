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

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":stl.bzl", "stl_info_from_attr")

_ANDROID_STATIC_DEPS = ["//external/libcxxabi:libc++demangle"]
_STATIC_DEP = ["//external/libcxx:libc++_static"]
_ANDROID_BINARY_STATIC_DEP = ["//prebuilts/clang/host/linux-x86:libunwind"]
_SHARED_DEP = ["//external/libcxx:libc++"]
_SDK_SYSTEM_DYNAMIC_DEPS = ["//bionic/libc:libstdc++"]
_SDK_SYSTEM_DEPS = ["//prebuilts/ndk:ndk_system"]
_SDK_LIBUNWIND = "//prebuilts/ndk:ndk_libunwind"
_SDK_LIBCXX = "//prebuilts/ndk:ndk_libc++_shared"
_SDK_LIBCXX_STATIC = "//prebuilts/ndk:ndk_libc++_static"
_SDK_LIBCXX_ABI = "//prebuilts/ndk:ndk_libc++abi"

_ANDROID_CPPFLAGS = []
_ANDROID_LINKOPTS = []
_LINUX_CPPFLAGS = ["-nostdinc++"]
_LINUX_LINKOPTS = ["-nostdlib++"]
_LINUX_BIONIC_CPPFLAGS = []
_LINUX_BIONIC_LINKOPTS = []
_DARWIN_CPPFLAGS = [
    "-nostdinc++",
    "-D_LIBCPP_DISABLE_AVAILABILITY",
]
_DARWIN_CPPFLAGS_STL_NONE = ["-nostdinc++"]
_DARWIN_LINKOPTS = ["-nostdlib++"]
_WINDOWS_CPPFLAGS = [
    "-nostdinc++",
    "-D_LIBCPP_DISABLE_VISIBILITY_ANNOTATIONS",
    "-D_LIBCXXABI_DISABLE_VISIBILITY_ANNOTATIONS",
    "-D_LIBCPP_HAS_THREAD_API_WIN32",
]
_WINDOWS_CPPFLAGS_STL_NONE = ["-nostdinc++"]
_WINDOWS_LINKOPTS = ["-nostdlib++"]

_StlInfo = provider(fields = ["static", "shared", "deps"])

def _stl_impl(ctx):
    return [
        _StlInfo(
            static = ctx.attr.static,
            shared = ctx.attr.shared,
            deps = ctx.attr.deps,
        ),
    ]

_stl = rule(
    implementation = _stl_impl,
    attrs = {
        "shared": attr.string_list(),
        "static": attr.string_list(),
        "deps": attr.string_list(default = []),
    },
)

_StlFlagsInfo = provider(fields = ["cppflags", "linkopts"])

def _stl_flags_impl(ctx):
    return [
        _StlFlagsInfo(
            cppflags = ctx.attr.cppflags,
            linkopts = ctx.attr.linkopts,
        ),
    ]

_stl_flags = rule(
    implementation = _stl_flags_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
)

def _test_stl(
        stl,
        is_shared,
        is_binary,
        android_deps,
        non_android_deps,
        android_flags,
        linux_flags,
        linux_bionic_flags,
        darwin_flags,
        windows_flags,
        sdk_deps):
    target_name = _stl_deps_target(stl, is_shared, is_binary)
    flags_target_name = _stl_flags_target(stl, is_shared, is_binary)
    android_test_name = target_name + "_android_test"
    non_android_test_name = target_name + "_non_android_test"
    android_flags_test_name = target_name + "_android_flags_test"
    linux_flags_test_name = target_name + "_linux_flags_test"
    linux_bionic_flags_test_name = target_name + "_linux_bionic_flags_test"
    darwin_flags_test_name = target_name + "_darwin_flags_test"
    windows_flags_test_name = target_name + "_windows_flags_test"
    sdk_test_name = target_name + "_sdk_test"

    _stl_deps_android_test(
        name = android_test_name,
        static = android_deps.static,
        shared = android_deps.shared,
        deps = android_deps.deps,
        target_under_test = target_name,
    )

    _stl_deps_non_android_test(
        name = non_android_test_name,
        static = non_android_deps.static,
        shared = non_android_deps.shared,
        deps = non_android_deps.deps,
        target_under_test = target_name,
    )

    _stl_flags_android_test(
        name = android_flags_test_name,
        cppflags = android_flags.cppflags,
        linkopts = android_flags.linkopts,
        target_under_test = flags_target_name,
    )

    _stl_flags_linux_test(
        name = linux_flags_test_name,
        cppflags = linux_flags.cppflags,
        linkopts = linux_flags.linkopts,
        target_under_test = flags_target_name,
    )

    _stl_flags_linux_bionic_test(
        name = linux_bionic_flags_test_name,
        cppflags = linux_bionic_flags.cppflags,
        linkopts = linux_bionic_flags.linkopts,
        target_under_test = flags_target_name,
    )

    _stl_flags_darwin_test(
        name = darwin_flags_test_name,
        cppflags = darwin_flags.cppflags,
        linkopts = darwin_flags.linkopts,
        target_under_test = flags_target_name,
    )

    _stl_flags_windows_test(
        name = windows_flags_test_name,
        cppflags = windows_flags.cppflags,
        linkopts = windows_flags.linkopts,
        target_under_test = flags_target_name,
    )

    _stl_deps_sdk_test(
        name = sdk_test_name,
        static = sdk_deps.static,
        shared = sdk_deps.shared,
        deps = sdk_deps.deps,
        target_under_test = target_name,
    )

    return [
        android_test_name,
        non_android_test_name,
        android_flags_test_name,
        linux_flags_test_name,
        linux_bionic_flags_test_name,
        darwin_flags_test_name,
        windows_flags_test_name,
        sdk_test_name,
    ]

def _stl_deps_target(name, is_shared, is_binary):
    target_name = name if name else "empty"
    target_name += "_shared" if is_shared else "_static"
    target_name += "_bin" if is_binary else "_lib"
    info = stl_info_from_attr(name, is_shared, is_binary)

    _stl(
        name = target_name,
        shared = info.shared_deps,
        static = info.static_deps,
        deps = info.deps,
        tags = ["manual"],
    )

    return target_name

def _stl_deps_test_impl(ctx):
    env = analysistest.begin(ctx)

    stl_info = analysistest.target_under_test(env)[_StlInfo]

    expected_static = sets.make(ctx.attr.static)
    actual_static = sets.make(stl_info.static)
    asserts.set_equals(
        env,
        expected = expected_static,
        actual = actual_static,
    )

    expected_shared = sets.make(ctx.attr.shared)
    actual_shared = sets.make(stl_info.shared)
    asserts.set_equals(
        env,
        expected = expected_shared,
        actual = actual_shared,
    )

    expected_deps = sets.make(ctx.attr.deps)
    actual_deps = sets.make(stl_info.deps)
    asserts.set_equals(
        env,
        expected = expected_deps,
        actual = actual_deps,
    )

    return analysistest.end(env)

def _stl_flags_target(name, is_shared, is_binary):
    target_name = name if name else "empty"
    target_name += "_shared" if is_shared else "_static"
    target_name += "_bin" if is_binary else "_lib"
    target_name += "_flags"
    info = stl_info_from_attr(name, is_shared)

    _stl_flags(
        name = target_name,
        cppflags = info.cppflags,
        linkopts = info.linkopts,
        tags = ["manual"],
    )

    return target_name

def _stl_flags_test_impl(ctx):
    env = analysistest.begin(ctx)

    stl_info = analysistest.target_under_test(env)[_StlFlagsInfo]

    expected_cppflags = sets.make(ctx.attr.cppflags)
    actual_cppflags = sets.make(stl_info.cppflags)
    asserts.set_equals(
        env,
        expected = expected_cppflags,
        actual = actual_cppflags,
    )

    expected_linkopts = sets.make(ctx.attr.linkopts)
    actual_linkopts = sets.make(stl_info.linkopts)
    asserts.set_equals(
        env,
        expected = expected_linkopts,
        actual = actual_linkopts,
    )

    return analysistest.end(env)

__stl_flags_android_test = analysistest.make(
    impl = _stl_flags_test_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
)

def _stl_flags_android_test(**kwargs):
    __stl_flags_android_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        **kwargs
    )

__stl_flags_linux_test = analysistest.make(
    impl = _stl_flags_test_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
)

def _stl_flags_linux_test(**kwargs):
    __stl_flags_linux_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
        **kwargs
    )

__stl_flags_linux_bionic_test = analysistest.make(
    impl = _stl_flags_test_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
)

def _stl_flags_linux_bionic_test(**kwargs):
    __stl_flags_linux_bionic_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux_bionic"],
        **kwargs
    )

_stl_flags_windows_test = analysistest.make(
    impl = _stl_flags_test_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/rules/cc:windows_for_testing",
    },
)

_stl_flags_darwin_test = analysistest.make(
    impl = _stl_flags_test_impl,
    attrs = {
        "cppflags": attr.string_list(),
        "linkopts": attr.string_list(),
    },
    config_settings = {
        "//command_line_option:platforms": "@//build/bazel/rules/cc:darwin_for_testing",
    },
)

__stl_deps_android_test = analysistest.make(
    impl = _stl_deps_test_impl,
    attrs = {
        "static": attr.string_list(),
        "shared": attr.string_list(),
        "deps": attr.string_list(default = []),
    },
)

def _stl_deps_android_test(**kwargs):
    __stl_deps_android_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        **kwargs
    )

__stl_deps_non_android_test = analysistest.make(
    impl = _stl_deps_test_impl,
    attrs = {
        "static": attr.string_list(),
        "shared": attr.string_list(),
        "deps": attr.string_list(default = []),
    },
)

def _stl_deps_non_android_test(**kwargs):
    __stl_deps_non_android_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
        **kwargs
    )

__stl_deps_sdk_test = analysistest.make(
    impl = _stl_deps_test_impl,
    attrs = {
        "static": attr.string_list(),
        "shared": attr.string_list(),
        "deps": attr.string_list(default = []),
    },
    config_settings = {
        "@//build/bazel/rules/apex:api_domain": "unbundled_app",
    },
)

def _stl_deps_sdk_test(**kwargs):
    __stl_deps_sdk_test(
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android"],
        **kwargs
    )

def stl_test_suite(name):
    native.test_suite(
        name = name,
        tests =
            _test_stl(
                stl = "",
                is_shared = True,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "system",
                is_shared = True,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "libc++",
                is_shared = True,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND],
                    shared = [_SDK_LIBCXX],
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "libc++_static",
                is_shared = True,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND, _SDK_LIBCXX_STATIC, _SDK_LIBCXX_ABI],
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "none",
                is_shared = True,
                is_binary = False,
                android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS_STL_NONE,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS_STL_NONE,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "",
                is_shared = False,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "system",
                is_shared = False,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "libc++",
                is_shared = False,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND],
                    shared = [_SDK_LIBCXX],
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "libc++_static",
                is_shared = False,
                is_binary = False,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND, _SDK_LIBCXX_STATIC, _SDK_LIBCXX_ABI],
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "none",
                is_shared = False,
                is_binary = False,
                android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS_STL_NONE,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS_STL_NONE,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "",
                is_shared = True,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _ANDROID_BINARY_STATIC_DEP,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "system",
                is_shared = True,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _ANDROID_BINARY_STATIC_DEP,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "libc++",
                is_shared = True,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _ANDROID_BINARY_STATIC_DEP,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND],
                    shared = [_SDK_LIBCXX],
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "libc++_static",
                is_shared = True,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP + _ANDROID_BINARY_STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND, _SDK_LIBCXX_STATIC, _SDK_LIBCXX_ABI],
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "none",
                is_shared = True,
                is_binary = True,
                android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS_STL_NONE,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS_STL_NONE,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "",
                is_shared = False,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP + _ANDROID_BINARY_STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "system",
                is_shared = False,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP + _ANDROID_BINARY_STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = _SDK_SYSTEM_DYNAMIC_DEPS,
                    deps = _SDK_SYSTEM_DEPS,
                ),
            ) +
            _test_stl(
                stl = "libc++",
                is_shared = False,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _ANDROID_BINARY_STATIC_DEP,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = _SHARED_DEP,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND],
                    shared = [_SDK_LIBCXX],
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "libc++_static",
                is_shared = False,
                is_binary = True,
                android_deps = struct(
                    static = _ANDROID_STATIC_DEPS + _STATIC_DEP + _ANDROID_BINARY_STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = _STATIC_DEP,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = [_SDK_LIBUNWIND, _SDK_LIBCXX_STATIC, _SDK_LIBCXX_ABI],
                    shared = None,
                    deps = None,
                ),
            ) +
            _test_stl(
                stl = "none",
                is_shared = False,
                is_binary = True,
                android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                non_android_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
                android_flags = struct(
                    cppflags = _ANDROID_CPPFLAGS,
                    linkopts = _ANDROID_LINKOPTS,
                ),
                linux_flags = struct(
                    cppflags = _LINUX_CPPFLAGS,
                    linkopts = _LINUX_LINKOPTS,
                ),
                linux_bionic_flags = struct(
                    cppflags = _LINUX_BIONIC_CPPFLAGS,
                    linkopts = _LINUX_BIONIC_LINKOPTS,
                ),
                darwin_flags = struct(
                    cppflags = _DARWIN_CPPFLAGS_STL_NONE,
                    linkopts = _DARWIN_LINKOPTS,
                ),
                windows_flags = struct(
                    cppflags = _WINDOWS_CPPFLAGS_STL_NONE,
                    linkopts = _WINDOWS_LINKOPTS,
                ),
                sdk_deps = struct(
                    static = None,
                    shared = None,
                    deps = None,
                ),
            ),
    )
