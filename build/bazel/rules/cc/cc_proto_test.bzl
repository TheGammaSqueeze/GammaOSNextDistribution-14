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
load("@bazel_skylib//lib:sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":cc_proto.bzl", "PROTO_GEN_NAME_SUFFIX", "cc_proto_library")

PROTO_GEN = "external/protobuf/bin/aprotoc/aprotoc"
VIRTUAL_IMPORT = "_virtual_imports"
RUNFILES = "_middlemen/external_Sprotobuf_Sbin_Saprotoc_Saprotoc-runfiles"

GEN_SUFFIX = [
    ".pb.h",
    ".pb.cc",
]

def _get_search_paths(action):
    cmd = action.argv
    search_paths = sets.make()
    cmd_len = len(cmd)
    for i in range(cmd_len):
        if cmd[i].startswith("-I"):
            sets.insert(search_paths, cmd[i].lstrip("- I"))

    return search_paths

def _proto_code_gen_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actions = analysistest.target_actions(env)
    package_root = ctx.label.package
    local_file_output_path = paths.join(
        package_root,
        target_under_test.label.name,
        package_root,
    )

    input_files = [
        ctx.attr.local_file_path,
        ctx.attr.external_file_path,
        ctx.attr.deps_file_path,
    ]

    output_files = [
        ctx.attr.local_file_path,
        ctx.attr.external_file_path,
    ]

    asserts.true(
        env,
        len(actions) == 1,
        "Proto gen action not found: %s" % actions,
    )

    action = actions[0]

    asserts.set_equals(
        env,
        expected = sets.make(
            [paths.join(package_root, file) for file in input_files] + [
                PROTO_GEN,
                RUNFILES,
            ],
        ),
        actual = sets.make([
            file.short_path
            for file in action.inputs.to_list()
        ]),
    )

    asserts.set_equals(
        env,
        expected = sets.make(
            [
                paths.join(
                    local_file_output_path,
                    paths.replace_extension(file, ext),
                )
                for ext in GEN_SUFFIX
                for file in output_files
            ],
        ),
        actual = sets.make([
            file.short_path
            for file in action.outputs.to_list()
        ]),
    )

    search_paths = _get_search_paths(action)

    asserts.equals(
        env,
        expected = sets.make(
            ["."] +
            [paths.join(package_root, f) + "=" + paths.join(package_root, f) for f in input_files],
        ),
        actual = search_paths,
    )

    return analysistest.end(env)

proto_code_gen_test = analysistest.make(
    _proto_code_gen_test_impl,
    attrs = {
        "local_file_path": attr.string(),
        "deps_file_path": attr.string(),
        "external_file_path": attr.string(),
    },
)

def _test_proto_code_gen():
    test_name = "proto_code_gen_test"
    local_file_path = "local/proto_local.proto"
    external_file_path = "external/proto_external.proto"
    deps_file_path = "deps/proto_deps.proto"
    external_proto_name = test_name + "_external_proto"
    deps_proto_name = test_name + "_deps_proto"
    local_proto_name = test_name + "_proto"
    cc_name = test_name + "_cc_proto"

    native.proto_library(
        name = external_proto_name,
        srcs = [external_file_path],
        tags = ["manual"],
    )

    native.proto_library(
        name = deps_proto_name,
        srcs = [deps_file_path],
        tags = ["manual"],
    )

    native.proto_library(
        name = local_proto_name,
        srcs = [local_file_path],
        deps = [":" + deps_proto_name],
        tags = ["manual"],
    )

    cc_proto_library(
        name = cc_name,
        deps = [
            ":" + local_proto_name,
            ":" + external_proto_name,
        ],
        tags = ["manual"],
    )

    proto_code_gen_test(
        name = test_name,
        target_under_test = cc_name + PROTO_GEN_NAME_SUFFIX,
        local_file_path = local_file_path,
        deps_file_path = deps_file_path,
        external_file_path = external_file_path,
    )

    return test_name

def _proto_strip_import_prefix_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    package_root = ctx.label.package

    # strip the proto file path, src/stripped/stripped.proto -> stripped/stripped.proto
    stripped_file_name = paths.relativize(ctx.attr.stripped_file_name, ctx.attr.strip_import_prefix)
    stripped_file_input_path = paths.join(
        package_root,
        VIRTUAL_IMPORT,
        ctx.attr.stripped_proto_name,
    )
    stripped_file_input_full_path = paths.join(
        stripped_file_input_path,
        stripped_file_name,
    )

    asserts.true(
        env,
        len(actions) == 1,
        "Proto gen action not found: %s" % actions,
    )

    action = actions[0]

    asserts.set_equals(
        env,
        expected = sets.make(
            [
                paths.join(package_root, ctx.attr.unstripped_file_name),
                stripped_file_input_full_path,
                PROTO_GEN,
                RUNFILES,
            ],
        ),
        actual = sets.make([
            file.short_path
            for file in action.inputs.to_list()
        ]),
    )

    asserts.set_equals(
        env,
        expected = sets.make(ctx.attr.expected_outputs),
        actual = sets.make([
            file.short_path
            for file in action.outputs.to_list()
        ]),
    )

    search_paths = _get_search_paths(action)

    asserts.equals(
        env,
        expected = sets.make([
            ".",
            paths.join(package_root, ctx.attr.unstripped_file_name) + "=" + paths.join(package_root, ctx.attr.unstripped_file_name),
            stripped_file_input_full_path + "=" +
            paths.join(
                ctx.genfiles_dir.path,
                stripped_file_input_full_path,
            ),
            paths.join(
                ctx.genfiles_dir.path,
                stripped_file_input_path,
            ),
        ]),
        actual = search_paths,
    )

    return analysistest.end(env)

proto_strip_import_prefix_test = analysistest.make(
    _proto_strip_import_prefix_test_impl,
    attrs = {
        "stripped_proto_name": attr.string(),
        "stripped_file_name": attr.string(),
        "unstripped_file_name": attr.string(),
        "strip_import_prefix": attr.string(),
        "expected_outputs": attr.string_list(),
    },
)

def _test_proto_strip_import_prefix():
    test_name = "proto_strip_import_prefix_test"
    unstripped_proto_name = test_name + "_unstripped_proto"
    stripped_proto_name = test_name + "_stripped_proto"
    unstripped_file_name = "unstripped/unstripped.proto"
    stripped_file_name = "src/stripped/stripped.proto"
    cc_name = test_name + "_cc_proto"
    strip_import_prefix = "src"

    native.proto_library(
        name = unstripped_proto_name,
        srcs = [unstripped_file_name],
        tags = ["manual"],
    )

    native.proto_library(
        name = stripped_proto_name,
        srcs = [stripped_file_name],
        strip_import_prefix = strip_import_prefix,
        tags = ["manual"],
    )

    cc_proto_library(
        name = cc_name,
        deps = [
            ":" + stripped_proto_name,
            ":" + unstripped_proto_name,
        ],
        tags = ["manual"],
    )

    expected_outputs = [
        # unstripped, the default behavior
        # bazel package is added to the path
        "build/bazel/rules/cc/proto_strip_import_prefix_test_cc_proto_proto_gen/build/bazel/rules/cc/unstripped/unstripped.pb.cc",
        "build/bazel/rules/cc/proto_strip_import_prefix_test_cc_proto_proto_gen/build/bazel/rules/cc/unstripped/unstripped.pb.h",
        # stripped - src/stripped/stripped.proto --> stripped/stripped.pb.cc
        # since strip_import_prefix is not nil, the bazel package is not added to the path
        "build/bazel/rules/cc/proto_strip_import_prefix_test_cc_proto_proto_gen/stripped/stripped.pb.cc",
        "build/bazel/rules/cc/proto_strip_import_prefix_test_cc_proto_proto_gen/stripped/stripped.pb.h",
    ]

    proto_strip_import_prefix_test(
        name = test_name,
        target_under_test = cc_name + PROTO_GEN_NAME_SUFFIX,
        stripped_proto_name = stripped_proto_name,
        stripped_file_name = stripped_file_name,
        unstripped_file_name = unstripped_file_name,
        strip_import_prefix = strip_import_prefix,
        expected_outputs = expected_outputs,
    )

    return test_name

def _proto_with_external_packages_test_impl(ctx):
    env = analysistest.begin(ctx)
    target_under_test = analysistest.target_under_test(env)
    actions = analysistest.target_actions(env)
    package_root = ctx.label.package
    deps_file_path = ctx.attr.deps_file_path
    external_file_path = ctx.attr.external_file_path
    local_file_path = ctx.attr.local_file_path

    asserts.true(
        env,
        len(actions) == 1,
        "Proto gen action not found: %s" % actions,
    )

    action = actions[0]

    asserts.set_equals(
        env,
        expected = sets.make(
            [
                paths.join(package_root, local_file_path),
                deps_file_path,
                external_file_path,
                PROTO_GEN,
                RUNFILES,
            ],
        ),
        actual = sets.make([
            file.short_path
            for file in action.inputs.to_list()
        ]),
    )

    asserts.set_equals(
        env,
        expected = sets.make(
            [
                paths.join(
                    package_root,
                    target_under_test.label.name,
                    package_root,
                    paths.replace_extension(local_file_path, ext),
                )
                for ext in GEN_SUFFIX
            ] +
            [
                paths.join(
                    package_root,
                    target_under_test.label.name,
                    paths.replace_extension(external_file_path, ext),
                )
                for ext in GEN_SUFFIX
            ],
        ),
        actual = sets.make([
            file.short_path
            for file in action.outputs.to_list()
        ]),
    )

    search_paths = _get_search_paths(action)

    asserts.equals(
        env,
        expected = sets.make([
            ".",
            paths.join(package_root, local_file_path) + "=" + paths.join(package_root, local_file_path),
            deps_file_path + "=" + deps_file_path,
            external_file_path + "=" + external_file_path,
        ]),
        actual = search_paths,
    )

    return analysistest.end(env)

proto_with_external_packages_test = analysistest.make(
    _proto_with_external_packages_test_impl,
    attrs = {
        "local_file_path": attr.string(),
        "deps_file_path": attr.string(),
        "external_file_path": attr.string(),
    },
)

def _test_proto_with_external_packages():
    test_name = "proto_with_external_packages_test"
    proto_name = test_name + "_proto"
    cc_name = test_name + "_cc_proto"
    local_file_path = "local/proto_local.proto"
    deps_file_path = "build/bazel/examples/cc/proto/deps/src/enums/proto_deps.proto"
    external_file_path = "build/bazel/examples/cc/proto/external/src/enums/proto_external.proto"

    native.proto_library(
        name = proto_name,
        srcs = [local_file_path],
        deps = ["//build/bazel/examples/cc/proto/deps:deps_proto"],
        tags = ["manual"],
    )

    cc_proto_library(
        name = cc_name,
        deps = [
            ":" + proto_name,
            "//build/bazel/examples/cc/proto/external:external_proto",
        ],
        tags = ["manual"],
    )

    proto_with_external_packages_test(
        name = test_name,
        target_under_test = cc_name + PROTO_GEN_NAME_SUFFIX,
        local_file_path = local_file_path,
        deps_file_path = deps_file_path,
        external_file_path = external_file_path,
    )

    return test_name

def cc_proto_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_proto_code_gen(),
            _test_proto_strip_import_prefix(),
            _test_proto_with_external_packages(),
        ],
    )
