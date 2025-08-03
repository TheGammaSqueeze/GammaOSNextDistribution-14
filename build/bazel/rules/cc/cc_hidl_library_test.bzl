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
load("//build/bazel/rules:hidl_file_utils.bzl", "INTERFACE_HEADER_PREFIXES", "TYPE_HEADER_PREFIXES")
load("//build/bazel/rules/hidl:hidl_interface.bzl", "INTERFACE_SUFFIX")
load("//build/bazel/rules/hidl:hidl_library.bzl", "hidl_library")
load("//build/bazel/rules/hidl:hidl_package_root.bzl", "hidl_package_root")
load(":cc_hidl_library.bzl", "CC_HEADER_SUFFIX", "cc_hidl_library")

HIDL_GEN = "prebuilts/build-tools/linux-x86/bin/hidl-gen"

SRC_TYPE_NAME_1 = "types_1.hal"
GEN_TYPE_NAME_1 = "types_1.h"
SRC_INTERFACE_NAME_1 = "IInterface_1.hal"
GEN_INTERFACE_NAME_1 = "Interface_1.h"
ROOT_1 = "android.hardware"
ROOT_INTERFACE_FILE_LABEL_1 = "//hardware/interfaces:current.txt"
ROOT_INTERFACE_FILE_1 = "hardware/interfaces/current.txt"
ROOT_INTERFACE_PATH_1 = "hardware/interfaces"
INTERFACE_PACKAGE_NAME_1 = "android.hardware.int1"
ROOT_ARGUMENT_1 = "android.hardware:hardware/interfaces"

SRC_TYPE_NAME_2 = "types_2.hal"
SRC_INTERFACE_NAME_2 = "IInterface_2.hal"
ROOT_2 = "android.hidl"
ROOT_INTERFACE_FILE_LABEL_2 = "//system/libhidl/transport:current.txt"
ROOT_INTERFACE_FILE_2 = "system/libhidl/transport/current.txt"
ROOT_INTERFACE_PATH_2 = "system/libhidl/transport"
ROOT_ARGUMENT_2 = "android.hidl:system/libhidl/transport"
INTERFACE_PACKAGE_NAME_2 = "android.hidl.int2"

INTERFACE_PACKAGE_NAME_CORE = "android.hidl.base"

INTERFACE_VERSION_1_0 = "1.0"
INTERFACE_VERSION_1_1 = "1.1"

def _setup_roots():
    hidl_package_root(
        name = ROOT_1,
        current = ROOT_INTERFACE_FILE_LABEL_1,
        path = ROOT_INTERFACE_PATH_1,
        tags = ["manual"],
    )

    hidl_package_root(
        name = ROOT_2,
        current = ROOT_INTERFACE_FILE_LABEL_2,
        path = ROOT_INTERFACE_PATH_2,
        tags = ["manual"],
    )

def _cc_code_gen_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    package_root = paths.dirname(ctx.build_file_path)
    header_gen_actions = [a for a in actions if a.mnemonic == "HidlGenCcHeader"]
    asserts.true(
        env,
        len(header_gen_actions) == 1,
        "Cc header gen action not found: %s" % actions,
    )

    header_gen_action = header_gen_actions[0]

    asserts.equals(
        env,
        expected = sorted([
            paths.join(package_root, SRC_TYPE_NAME_1),
            paths.join(package_root, SRC_INTERFACE_NAME_1),
            paths.join(package_root, SRC_TYPE_NAME_2),
            paths.join(package_root, SRC_INTERFACE_NAME_2),
            ROOT_INTERFACE_FILE_1,
            ROOT_INTERFACE_FILE_2,
            paths.join(HIDL_GEN),
        ]),
        actual = sorted([
            file.short_path
            for file in header_gen_action.inputs.to_list()
        ]),
    )

    path = paths.join(package_root, INTERFACE_PACKAGE_NAME_1.replace(".", "/"), INTERFACE_VERSION_1_0)
    asserts.equals(
        env,
        expected = sorted(
            [
                paths.join(path, prefix + GEN_TYPE_NAME_1)
                for prefix in TYPE_HEADER_PREFIXES
            ] +
            [
                paths.join(path, prefix + GEN_INTERFACE_NAME_1)
                for prefix in INTERFACE_HEADER_PREFIXES
            ],
        ),
        actual = sorted([
            file.short_path
            for file in header_gen_action.outputs.to_list()
        ]),
    )

    cmd = header_gen_action.argv
    asserts.true(
        env,
        HIDL_GEN == cmd[0],
        "hidl-gen is not called: %s" % cmd,
    )

    asserts.true(
        env,
        "-R" in cmd,
        "Calling hidl-gen without -R: %s" % cmd,
    )

    index = cmd.index("-p")
    asserts.true(
        env,
        index > 0,
        "Calling hidl-gen without -p: %s" % cmd,
    )

    asserts.true(
        env,
        cmd[index + 1] == ".",
        ". needs to follow -p: %s" % cmd,
    )

    index = cmd.index("-o")
    asserts.true(
        env,
        index > 0,
        "Calling hidl-gen without -o: %s" % cmd,
    )

    asserts.true(
        env,
        cmd[index + 1].endswith(package_root),
        "Incorrect output path: %s" % cmd,
    )

    index = cmd.index("-L")
    asserts.true(
        env,
        index > 0,
        "Calling hidl-gen without -L: %s" % cmd,
    )

    asserts.true(
        env,
        cmd[index + 1] == "c++-headers",
        "Incorrect language: %s" % cmd,
    )

    roots = []
    cmd_len = len(cmd)
    for i in range(cmd_len):
        if cmd[i] == "-r":
            roots.append(cmd[i + 1])

    asserts.equals(
        env,
        expected = sorted([
            ROOT_ARGUMENT_1,
            ROOT_ARGUMENT_2,
        ]),
        actual = sorted(roots),
    )

    asserts.true(
        env,
        cmd[cmd_len - 1] == INTERFACE_PACKAGE_NAME_1 + "@" + INTERFACE_VERSION_1_0,
        "The last arg should be the FQ name of the interface: %s" % cmd,
    )

    return analysistest.end(env)

cc_code_gen_test = analysistest.make(
    _cc_code_gen_test_impl,
)

def _test_cc_code_gen():
    test_name = "cc_code_gen_test"
    cc_name = INTERFACE_PACKAGE_NAME_1 + "@" + INTERFACE_VERSION_1_0
    interface_name = cc_name + INTERFACE_SUFFIX
    cc_name_dep = INTERFACE_PACKAGE_NAME_2 + "@" + INTERFACE_VERSION_1_0
    interface_name_dep = cc_name_dep + INTERFACE_SUFFIX

    hidl_library(
        name = interface_name_dep,
        root = ROOT_2,
        fq_name = cc_name_dep,
        srcs = [
            SRC_TYPE_NAME_2,
            SRC_INTERFACE_NAME_2,
        ],
        tags = ["manual"],
    )

    cc_hidl_library(
        name = cc_name_dep,
        interface = interface_name_dep,
        tags = ["manual"],
    )

    hidl_library(
        name = interface_name,
        deps = [interface_name_dep],
        root = ROOT_1,
        fq_name = cc_name,
        srcs = [
            SRC_TYPE_NAME_1,
            SRC_INTERFACE_NAME_1,
        ],
        tags = ["manual"],
    )

    cc_hidl_library(
        name = cc_name,
        interface = interface_name,
        dynamic_deps = [cc_name_dep],
        tags = ["manual"],
    )

    cc_code_gen_test(
        name = test_name,
        target_under_test = cc_name + CC_HEADER_SUFFIX,
    )

    return test_name

def _cc_interface_dep_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    dynamic_deps = target_under_test[CcSharedLibraryInfo].dynamic_deps

    dep_name = INTERFACE_PACKAGE_NAME_CORE + "@" + INTERFACE_VERSION_1_1
    package_root = paths.dirname(ctx.build_file_path)

    asserts.false(
        env,
        _find_dep(package_root, dep_name, dynamic_deps),
        "Core package in the dependencies: %s %s" % (dep_name, dynamic_deps),
    )

    dep_name = INTERFACE_PACKAGE_NAME_2 + "@" + INTERFACE_VERSION_1_1
    asserts.true(
        env,
        _find_dep(package_root, dep_name, dynamic_deps),
        "Missing valid dependency: %s %s" % (dep_name, dynamic_deps),
    )

    return analysistest.end(env)

def _find_dep(package_root, name, deps):
    full_name = "@//" + package_root + ":" + name
    for lists in deps.to_list():
        for dep in lists.exports:
            if dep.startswith(full_name):
                return True

    return False

cc_interface_dep_test = analysistest.make(
    _cc_interface_dep_test_impl,
)

def _test_cc_interface_dep():
    test_name = "cc_interface_dep_test"
    cc_name = INTERFACE_PACKAGE_NAME_1 + "@" + INTERFACE_VERSION_1_1
    interface_name = cc_name + INTERFACE_SUFFIX
    cc_name_dep = INTERFACE_PACKAGE_NAME_2 + "@" + INTERFACE_VERSION_1_1
    interface_name_dep = cc_name_dep + INTERFACE_SUFFIX
    cc_name_core = INTERFACE_PACKAGE_NAME_CORE + "@" + INTERFACE_VERSION_1_1
    interface_name_core = cc_name_core + INTERFACE_SUFFIX

    hidl_library(
        name = interface_name_dep,
        root = ROOT_2,
        fq_name = cc_name_dep,
        srcs = [
            SRC_TYPE_NAME_2,
            SRC_INTERFACE_NAME_2,
        ],
        tags = ["manual"],
    )

    cc_hidl_library(
        name = cc_name_dep,
        interface = interface_name_dep,
        tags = ["manual"],
    )

    hidl_library(
        name = interface_name_core,
        root = ROOT_2,
        fq_name = cc_name_core,
        srcs = [
            SRC_TYPE_NAME_2,
            SRC_INTERFACE_NAME_2,
        ],
        tags = ["manual"],
    )

    cc_hidl_library(
        name = cc_name_core,
        interface = interface_name_core,
        tags = ["manual"],
    )

    hidl_library(
        name = interface_name,
        deps = [interface_name_dep, interface_name_core],
        root = ROOT_1,
        fq_name = cc_name,
        srcs = [
            SRC_TYPE_NAME_1,
            SRC_INTERFACE_NAME_1,
        ],
        tags = ["manual"],
    )

    cc_hidl_library(
        name = cc_name,
        interface = interface_name,
        dynamic_deps = [cc_name_dep, cc_name_core],
        tags = ["manual"],
    )

    cc_interface_dep_test(
        name = test_name,
        target_under_test = cc_name,
    )

    return test_name

def cc_hidl_library_test_suite(name):
    _setup_roots()
    native.test_suite(
        name = name,
        tests = [
            _test_cc_code_gen(),
            _test_cc_interface_dep(),
        ],
    )
