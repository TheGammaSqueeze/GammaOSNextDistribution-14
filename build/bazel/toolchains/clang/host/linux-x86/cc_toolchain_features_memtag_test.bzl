"""Copyright (C) 2023 The Android Open Source Project

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

     http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
"""

load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load(
    "//build/bazel/rules/test_common:flags.bzl",
    "action_flags_absent_for_mnemonic_aosp_arm64_test",
    "action_flags_present_only_for_mnemonic_aosp_arm64_test",
)

# Include these different file types to make sure that all actions types are
# triggered
test_srcs = [
    "foo.cpp",
    "bar.c",
    "baz.s",
    "blah.S",
]

compile_action_mnemonic = "CppCompile"
link_action_mnemonic = "CppLink"

def test_cc_binary_without_memtag():
    name = "cc_binary_without_memtag"
    test_name = name + "_test"

    cc_binary(
        name = name,
        srcs = test_srcs,
        tags = ["manual"],
    )

    action_flags_absent_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [
            compile_action_mnemonic,
            link_action_mnemonic,
        ],
        expected_absent_flags = ["-fsanitize=memtag-heap,-fsanitize-memtag-mode=sync,-fsanitize-memtag-mode=async"],
    )

    return test_name

def test_cc_binary_with_memtag_sync():
    name = "cc_binary_with_memtag_sync"

    cc_binary(
        name = name,
        srcs = test_srcs,
        features = select({
            "//build/bazel_common_rules/platforms/os_arch:android_arm64": [
                "memtag_heap",
                "diag_memtag_heap",
            ],
            "//conditions:default": [],
        }),
        tags = ["manual"],
    )

    test_name = name + "_compile_and_link_flags_test"
    test_names = [test_name]
    action_flags_present_only_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [compile_action_mnemonic, link_action_mnemonic],
        expected_flags = ["-fsanitize=memtag-heap"],
    )

    test_name = name + "_link_flags_test"
    test_names.append(test_name)
    action_flags_present_only_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [link_action_mnemonic],
        expected_flags = ["-fsanitize-memtag-mode=sync"],
    )
    return test_names

def test_cc_binary_with_memtag_async():
    name = "cc_binary_with_memtag_async"

    cc_binary(
        name = name,
        srcs = test_srcs,
        features = select({
            "//build/bazel_common_rules/platforms/os_arch:android_arm64": [
                "memtag_heap",
            ],
            "//conditions:default": [],
        }),
        tags = ["manual"],
    )

    test_name = name + "_compile_and_link_flags_test"
    test_names = [test_name]
    action_flags_present_only_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [compile_action_mnemonic, link_action_mnemonic],
        expected_flags = ["-fsanitize=memtag-heap"],
    )

    test_name = name + "_link_flags_test"
    test_names.append(test_name)
    action_flags_present_only_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [link_action_mnemonic],
        expected_flags = ["-fsanitize-memtag-mode=async"],
    )
    return test_names

def test_cc_library_memtag_not_supported():
    name = "cc_library_memtag_not_supported"
    test_name = name + "_test"

    cc_library_shared(
        name = name,
        srcs = test_srcs,
        features = select({
            "//build/bazel_common_rules/platforms/os_arch:android_arm64": [
                "memtag_heap",
                "diag_memtag_heap",
            ],
            "//conditions:default": [],
        }),
        tags = ["manual"],
    )

    action_flags_absent_for_mnemonic_aosp_arm64_test(
        name = test_name,
        target_under_test = name + "_unstripped",
        mnemonics = [
            compile_action_mnemonic,
            link_action_mnemonic,
        ],
        expected_absent_flags = ["-fsanitize=memtag-heap,-fsanitize-memtag-mode=sync,-fsanitize-memtag-mode=async"],
    )

    return test_name

def cc_toolchain_features_memtag_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
                    test_cc_binary_without_memtag(),
                    test_cc_library_memtag_not_supported(),
                ] + test_cc_binary_with_memtag_sync() +
                test_cc_binary_with_memtag_async(),
    )
