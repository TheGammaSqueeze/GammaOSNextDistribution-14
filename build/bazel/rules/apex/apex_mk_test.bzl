# Copyright (C) 2022 The Android Open Source Project
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

load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("//build/bazel/rules:prebuilt_file.bzl", "prebuilt_file")
load("//build/bazel/rules:sh_binary.bzl", "sh_binary")
load("//build/bazel/rules/cc:cc_binary.bzl", "cc_binary")
load("//build/bazel/rules/cc:cc_library_shared.bzl", "cc_library_shared")
load(":apex_info.bzl", "ApexMkInfo")
load(":apex_test_helpers.bzl", "test_apex")

def _apex_files_info_test(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)

    # no attr.string_keyed_string_dict_list.. so we'll have to make do :-)
    expected_files_info = [json.decode(i) for i in ctx.attr.expected_files_info]
    actual_files_info = target[ApexMkInfo].files_info

    asserts.equals(env, len(expected_files_info), len(actual_files_info))

    for idx, expected in enumerate(expected_files_info):
        actual = actual_files_info[idx]

        asserts.equals(env, len(expected), len(actual))
        for k, v in expected.items():
            if k in ["built_file", "unstripped_built_file"]:
                # don't test the part that contains the configuration hash, which is sensitive to changes.
                expected_path_without_config = v.split("bazel-out/")[-1]
                asserts.true(env, actual[k].endswith(expected_path_without_config))
            else:
                asserts.equals(env, v, actual[k])
    return analysistest.end(env)

apex_files_info_test = analysistest.make(
    _apex_files_info_test,
    attrs = {
        "expected_files_info": attr.string_list(
            doc = "expected files info",
        ),
    },
)

def _test_apex_files_info_basic():
    name = "apex_files_info_basic"
    test_name = name + "_test"

    test_apex(name = name)

    apex_files_info_test(
        name = test_name,
        target_under_test = name,
        expected_files_info = [
            # deliberately empty.
        ],
    )

    return test_name

def _test_apex_files_info_complex():
    name = "apex_files_info_complex"
    test_name = name + "_test"

    prebuilt_file(
        name = name + "_file",
        src = name + "_file.txt",
        dir = "etc",
        tags = ["manual"],
    )

    sh_binary(
        name = name + "_bin_sh",
        srcs = [name + "_bin.sh"],
        tags = ["manual"],
    )

    cc_binary(
        name = name + "_bin_cc",
        srcs = [name + "_bin.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib_cc",
        srcs = [name + "_lib.cc"],
        tags = ["manual"],
    )

    cc_library_shared(
        name = name + "_lib2_cc",
        srcs = [name + "_lib2.cc"],
        tags = ["manual"],
    )

    test_apex(
        name = name,
        binaries = [name + "_bin_sh", name + "_bin_cc"],
        prebuilts = [name + "_file"],
        native_shared_libs_32 = [name + "_lib_cc"],
        native_shared_libs_64 = [name + "_lib2_cc"],
    )

    apex_files_info_test(
        name = test_name,
        target_under_test = name,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:android", "//build/bazel_common_rules/platforms/arch:arm64"],
        expected_files_info = [json.encode(i) for i in [
            {
                "built_file": "bazel-out/bin/build/bazel/rules/apex/bin/apex_files_info_complex_bin_cc/apex_files_info_complex_bin_cc",
                "class": "nativeExecutable",
                "install_dir": "bin",
                "basename": "apex_files_info_complex_bin_cc",
                "package": "build/bazel/rules/apex",
                "make_module_name": "apex_files_info_complex_bin_cc",
                "arch": "arm64",
                "unstripped_built_file": "bazel-out/build/bazel/rules/apex/apex_files_info_complex_bin_cc_unstripped",
            },
            {
                "built_file": "bazel-out/bin/build/bazel/rules/apex/apex_files_info_complex_bin_sh",
                "class": "shBinary",
                "install_dir": "bin",
                "basename": "apex_files_info_complex_bin_sh",
                "package": "build/bazel/rules/apex",
                "make_module_name": "apex_files_info_complex_bin_sh",
                "arch": "arm64",
            },
            {
                "built_file": "build/bazel/rules/apex/apex_files_info_complex_file.txt",
                "class": "etc",
                "install_dir": "etc",
                "basename": "apex_files_info_complex_file",
                "package": "build/bazel/rules/apex",
                "make_module_name": "apex_files_info_complex_file",
                "arch": "arm64",
            },
            {
                "built_file": "bazel-out/bin/build/bazel/rules/apex/apex_files_info_complex_lib2_cc/apex_files_info_complex_lib2_cc.so",
                "class": "nativeSharedLib",
                "install_dir": "lib64",
                "basename": "apex_files_info_complex_lib2_cc.so",
                "package": "build/bazel/rules/apex",
                "make_module_name": "apex_files_info_complex_lib2_cc",
                "arch": "arm64",
                "unstripped_built_file": "bazel-out/bin/build/bazel/rules/apex/libapex_files_info_complex_lib2_cc_unstripped.so",
            },
            {
                "built_file": "bazel-out/bin/build/bazel/rules/apex/apex_files_info_complex_lib_cc/apex_files_info_complex_lib_cc.so",
                "class": "nativeSharedLib",
                "install_dir": "lib",
                "basename": "apex_files_info_complex_lib_cc.so",
                "package": "build/bazel/rules/apex",
                "make_module_name": "apex_files_info_complex_lib_cc",
                "arch": "arm",
                "unstripped_built_file": "bazel-out/bin/build/bazel/rules/apex/libapex_files_info_complex_lib_cc_unstripped.so",
            },
            {
                "built_file": "bazel-out/bin/external/libcxx/libc++/libc++.so",
                "class": "nativeSharedLib",
                "install_dir": "lib",
                "basename": "libc++.so",
                "package": "external/libcxx",
                "make_module_name": "libc++",
                "arch": "arm",
                "unstripped_built_file": "bazel-out/bin/external/libcxx/liblibc++_unstripped.so",
            },
            {
                "built_file": "bazel-out/bin/external/libcxx/libc++/libc++.so",
                "class": "nativeSharedLib",
                "install_dir": "lib64",
                "basename": "libc++.so",
                "package": "external/libcxx",
                "make_module_name": "libc++",
                "arch": "arm64",
                "unstripped_built_file": "bazel-out/bin/external/libcxx/liblibc++_unstripped.so",
            },
        ]],
    )

    return test_name

def apex_mk_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_apex_files_info_basic(),
            _test_apex_files_info_complex(),
        ],
    )
