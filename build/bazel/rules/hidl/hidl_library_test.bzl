"""Copyright (C) 2022 The Android Open Source Project

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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":hidl_library.bzl", "HidlInfo", "hidl_library")
load(":hidl_package_root.bzl", "hidl_package_root")

SRC_NAME = "src.hal"
DEP1_NAME = "dep1.hal"
DEP2_NAME = "dep2.hal"
DEP3_NAME = "dep3.hal"
ROOT = "android.hardware"
ROOT_INTERFACE_FILE_LABEL = "//hardware/interfaces:current.txt"
ROOT_INTERFACE_FILE = "hardware/interfaces/current.txt"
ROOT_INTERFACE_PATH = "hardware/interfaces"
ROOT_ARGUMENT = "android.hardware:hardware/interfaces"
ROOT1 = "android.system"
ROOT1_INTERFACE_FILE_LABEL = "//system/hardware/interfaces:current.txt"
ROOT1_INTERFACE_FILE = "system/hardware/interfaces/current.txt"
ROOT1_INTERFACE_PATH = "system/hardware/interfaces"
ROOT1_ARGUMENT = "android.system:system/hardware/interfaces"
ROOT2 = "android.hidl"
ROOT2_INTERFACE_FILE_LABEL = "//system/libhidl/transport:current.txt"
ROOT2_INTERFACE_FILE = "system/libhidl/transport/current.txt"
ROOT2_INTERFACE_PATH = "system/libhidl/transport"
ROOT2_ARGUMENT = "android.hidl:system/libhidl/transport"

def _setup_roots():
    hidl_package_root(
        name = ROOT,
        current = ROOT_INTERFACE_FILE_LABEL,
        path = ROOT_INTERFACE_PATH,
    )

    hidl_package_root(
        name = ROOT1,
        current = ROOT1_INTERFACE_FILE_LABEL,
        path = ROOT1_INTERFACE_PATH,
        tags = ["manual"],
    )

    hidl_package_root(
        name = ROOT2,
        current = ROOT2_INTERFACE_FILE_LABEL,
        path = ROOT2_INTERFACE_PATH,
        tags = ["manual"],
    )

def _hidl_info_simple_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    package_root = paths.dirname(ctx.build_file_path)

    asserts.equals(
        env,
        expected = [
            paths.join(package_root, "src.hal"),
        ],
        actual = [
            file.short_path
            for file in target_under_test[HidlInfo].srcs.to_list()
        ],
    )

    asserts.equals(
        env,
        expected = sorted([
            paths.join(package_root, DEP1_NAME),
            paths.join(package_root, DEP3_NAME),
            paths.join(package_root, DEP2_NAME),
            paths.join(package_root, SRC_NAME),
        ]),
        actual = sorted([
            file.short_path
            for file in target_under_test[HidlInfo].transitive_srcs.to_list()
        ]),
    )

    asserts.equals(
        env,
        expected = sorted([
            ROOT1_ARGUMENT,
            ROOT2_ARGUMENT,
            ROOT_ARGUMENT,
        ]),
        actual = sorted(target_under_test[HidlInfo].transitive_roots.to_list()),
        msg = "arguments",
    )

    asserts.equals(
        env,
        expected = sorted([
            ROOT1_INTERFACE_FILE,
            ROOT2_INTERFACE_FILE,
            ROOT_INTERFACE_FILE,
        ]),
        actual = sorted([
            file.short_path
            for file in target_under_test[HidlInfo].transitive_root_interface_files.to_list()
        ]),
        msg = "interface files",
    )

    return analysistest.end(env)

hidl_info_simple_test = analysistest.make(
    _hidl_info_simple_test_impl,
)

def _test_hidl_info_simple():
    test_base_name = "hidl_info_simple"
    test_name = test_base_name + "_test"
    dep1 = test_base_name + "_dep1"
    dep2 = test_base_name + "_dep2"
    dep3 = test_base_name + "_dep3"

    hidl_library(
        name = test_base_name,
        srcs = [SRC_NAME],
        deps = [
            ":" + dep1,
            ":" + dep2,
        ],
        root = ROOT,
        tags = ["manual"],
    )
    hidl_library(
        name = dep1,
        srcs = [DEP1_NAME],
        root = ROOT1,
        tags = ["manual"],
    )
    hidl_library(
        name = dep2,
        srcs = [DEP2_NAME],
        deps = [
            ":" + dep3,
        ],
        root = ROOT2,
        tags = ["manual"],
    )
    hidl_library(
        name = dep3,
        srcs = [DEP3_NAME],
        root = ROOT2,
        tags = ["manual"],
    )
    hidl_info_simple_test(
        name = test_name,
        target_under_test = test_base_name,
    )

    return test_name

def hidl_library_test_suite(name):
    _setup_roots()
    native.test_suite(
        name = name,
        tests = [
            _test_hidl_info_simple(),
        ],
    )
