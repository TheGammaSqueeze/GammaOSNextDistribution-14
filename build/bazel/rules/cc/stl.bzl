# Copyright (C) 2021 The Android Open Source Project
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

# Helpers for stl property resolution.
# These mappings taken from build/soong/cc/stl.go

load("//build/bazel/platforms/arch/variants:constants.bzl", "arch_variant_to_constraints")

_libcpp_stl_names = {
    "libc++": True,
    "libc++_static": True,
    "": True,
    "system": True,
}

# https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/stl.go;l=162;drc=cb0ac95bde896fa2aa59193a37ceb580758c322c
# this should vary based on vndk version
# skip libm and libc because then we would have duplicates due to system_shared_library
_libunwind = "//prebuilts/clang/host/linux-x86:libunwind"

_ndk_system = "//prebuilts/ndk:ndk_system"

_static_binary_deps = select({
    arch_variant_to_constraints["android"]: [_libunwind],
    arch_variant_to_constraints["linux_bionic"]: [_libunwind],
    "//build/bazel/rules/apex:unbundled_app": [],
    "//conditions:default": [],
})

def _stl_name_resolver(stl_name, is_shared):
    if stl_name == "none":
        return stl_name

    if stl_name not in _libcpp_stl_names:
        fail("Unhandled stl %s" % stl_name)

    if stl_name in ("", "system"):
        if is_shared:
            stl_name = "libc++"
        else:
            stl_name = "libc++_static"
    return stl_name

def stl_info_from_attr(stl_name, is_shared, is_binary = False):
    deps = _stl_deps(stl_name, is_shared, is_binary)
    flags = _stl_flags(stl_name, is_shared)
    return struct(
        static_deps = deps.static,
        shared_deps = deps.shared,
        deps = deps.deps,
        cppflags = flags.cppflags,
        linkopts = flags.linkopts,
    )

def _stl_deps(stl_name, is_shared, is_binary = False):
    if stl_name == "none":
        return struct(static = [], shared = [], deps = [])

    shared, static, deps = [], [], []
    if stl_name in ("", "system"):
        if is_shared:
            shared = select({
                # non-sdk variant, effective STL is libc++
                arch_variant_to_constraints["android"]: [
                    "//external/libcxx:libc++",
                ],
                # sdk variant, effective STL is ndk_system
                "//build/bazel/rules/apex:unbundled_app": [
                    "//bionic/libc:libstdc++",
                ],
                "//conditions:default": ["//external/libcxx:libc++"],  # libc++ is used by host variants
            })
            static = select({
                arch_variant_to_constraints["android"]: [
                    "//external/libcxxabi:libc++demangle",
                ],
                # sdk variant does not have an entry since ndk_system's deps have been added in `shared`
                "//build/bazel/rules/apex:unbundled_app": [],
                "//conditions:default": [],
            })
            deps = select({
                # sdk variant, effective STL is ndk_system
                "//build/bazel/rules/apex:unbundled_app": [
                    _ndk_system,
                ],
                "//conditions:default": [],
            })

        else:
            shared = select({
                # sdk variant, effective STL is ndk_system
                "//build/bazel/rules/apex:unbundled_app": [
                    "//bionic/libc:libstdc++",
                ],
                "//conditions:default": [],
            })
            static = select({
                # non-sdk variant, effective STL is libc++_static
                arch_variant_to_constraints["android"]: [
                    "//external/libcxx:libc++_static",
                    "//external/libcxxabi:libc++demangle",
                ],
                # sdk variant does not have an entry since ndk_system's deps have been added in `shared`
                "//build/bazel/rules/apex:unbundled_app": [],
                "//conditions:default": ["//external/libcxx:libc++_static"],  # libc++_static is used by host variants
            })
            deps = select({
                # sdk variant, effective STL is ndk_system
                "//build/bazel/rules/apex:unbundled_app": [
                    _ndk_system,
                ],
                "//conditions:default": [],
            })
    elif stl_name == "libc++":
        shared = select({
            arch_variant_to_constraints["android"]: [
                "//external/libcxx:libc++",
            ],
            # sdk variant, effective STL is ndk_libc++_shared
            "//build/bazel/rules/apex:unbundled_app": [
                "//prebuilts/ndk:ndk_libc++_shared",
            ],
            "//conditions:default": ["//external/libcxx:libc++"],  # libc++ is used by host variants
        })
        static = select({
            arch_variant_to_constraints["android"]: [
                "//external/libcxxabi:libc++demangle",
            ],
            "//build/bazel/rules/apex:unbundled_app": [
                "//prebuilts/ndk:ndk_libunwind",
            ],
            "//conditions:default": [],
        })

    elif stl_name == "libc++_static":
        static = select({
            arch_variant_to_constraints["android"]: [
                "//external/libcxx:libc++_static",
                "//external/libcxxabi:libc++demangle",
            ],
            # sdk variant, effective STL is ndk_libc++_static
            "//build/bazel/rules/apex:unbundled_app": [
                "//prebuilts/ndk:ndk_libc++_static",
                "//prebuilts/ndk:ndk_libc++abi",
                "//prebuilts/ndk:ndk_libunwind",
            ],
            "//conditions:default": ["//external/libcxx:libc++_static"],  # libc++_static is used by host variants
        })
    if is_binary:
        static += _static_binary_deps
    return struct(
        static = static,
        shared = shared,
        deps = deps,
    )

def _stl_flags(stl_name, is_shared):
    """returns flags that control STL inclusion

    Should be kept up to date with
    https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/stl.go;l=197;drc=8722ca5486fa62c07520e09db54b1b330b48da17

    Args:
        stl_name: string, name of STL library to use
        is_shared: bool, if true, the STL should be linked dynamically
    Returns:
        struct containing flags for CC compilation
    """
    stl_name = _stl_name_resolver(stl_name, is_shared)

    cppflags_darwin = []
    cppflags_windows_not_bionic = []
    cppflags_not_bionic = []
    linkopts_not_bionic = []
    if stl_name in ("libc++", "libc++_static"):
        cppflags_not_bionic.append("-nostdinc++")
        linkopts_not_bionic.append("-nostdlib++")

        # libc++'s headers are annotated with availability macros that
        # indicate which version of Mac OS was the first to ship with a
        # libc++ feature available in its *system's* libc++.dylib. We do
        # not use the system's library, but rather ship our own. As such,
        # these availability attributes are meaningless for us but cause
        # build breaks when we try to use code that would not be available
        # in the system's dylib.
        cppflags_darwin.append("-D_LIBCPP_DISABLE_AVAILABILITY")

        # Disable visiblity annotations since we're using static libc++.
        cppflags_windows_not_bionic.append("-D_LIBCPP_DISABLE_VISIBILITY_ANNOTATIONS")
        cppflags_windows_not_bionic.append("-D_LIBCXXABI_DISABLE_VISIBILITY_ANNOTATIONS")

        # Use Win32 threads in libc++.
        cppflags_windows_not_bionic.append("-D_LIBCPP_HAS_THREAD_API_WIN32")
    elif stl_name in ("none"):
        cppflags_not_bionic.append("-nostdinc++")
        linkopts_not_bionic.append("-nostdlib++")
    else:
        #TODO(b/201079053) handle ndk STL flags
        pass

    return struct(
        cppflags = select({
            "//build/bazel_common_rules/platforms/os:bionic": [],
            "//build/bazel/rules/apex:unbundled_app": [],
            "//conditions:default": cppflags_not_bionic,
        }) + select({
            "//build/bazel_common_rules/platforms/os:darwin": cppflags_darwin,
            "//build/bazel_common_rules/platforms/os:windows": (
                cppflags_windows_not_bionic
            ),
            "//conditions:default": [],
        }),
        linkopts = select({
            "//build/bazel_common_rules/platforms/os:bionic": [],
            "//build/bazel/rules/apex:unbundled_app": [],
            "//build/bazel/rules/apex:unbundled_app.arm": ["-Wl,--exclude-libs,libunwind.a"],
            "//conditions:default": linkopts_not_bionic,
        }),
    )
