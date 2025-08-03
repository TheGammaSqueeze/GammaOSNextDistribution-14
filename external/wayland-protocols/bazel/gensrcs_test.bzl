"""
Copyright 2023 The Android Open Source Project

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

load("@bazel_skylib//lib:new_sets.bzl", "sets")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load("@bazel_skylib//lib:paths.bzl", "paths")
load(":gensrcs.bzl", "gensrcs")

SRCS = [
    "texts/src1.txt",
    "texts/src2.txt",
    "src3.txt",
]

# ==== Check the output paths created by gensrcs ====

def _test_output_path_expansion_impl(ctx):
    env = analysistest.begin(ctx)
    target = analysistest.target_under_test(env)
    actions = analysistest.target_actions(env)

    # Expect an action for each input/output file pair.
    asserts.equals(
        env,
        expected = len(ctx.attr.expected_outputs),
        actual = len(actions),
    )

    # Expect the correct set of output files.
    asserts.set_equals(
        env,
        expected = sets.make([
            paths.join(
                ctx.genfiles_dir.path,
                paths.dirname(ctx.build_file_path),
                out,
            )
            for out in ctx.attr.expected_outputs
        ]),
        actual = sets.make(
            [file.path for file in target.files.to_list()],
        ),
    )

    return analysistest.end(env)

output_path_expansion_test = analysistest.make(
    _test_output_path_expansion_impl,
    attrs = {
        "expected_outputs": attr.string_list(
            doc = "The expected list of output files",
        ),
    },
)

def _test_output_expansion_base():
    name = "gensrcs_output_expansion_base"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:BASE)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_src1_suffix",
            "prefix_src2_suffix",
            "prefix_src3_suffix",
        ],
    )
    return test_name

def _test_output_expansion_base_ext():
    name = "gensrcs_output_expansion_base_ext"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:BASE.EXT)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_src1.txt_suffix",
            "prefix_src2.txt_suffix",
            "prefix_src3.txt_suffix",
        ],
    )
    return test_name

def _test_output_expansion_path_base():
    name = "gensrcs_output_expansion_path_base"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:PATH/BASE)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_texts/src1_suffix",
            "prefix_texts/src2_suffix",
            "prefix_src3_suffix",
        ],
    )
    return test_name

def _test_output_expansion_path_base_ext():
    name = "gensrcs_output_expansion_path_base_ext"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:PATH/BASE.EXT)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_texts/src1.txt_suffix",
            "prefix_texts/src2.txt_suffix",
            "prefix_src3.txt_suffix",
        ],
    )
    return test_name

def _test_output_expansion_pkg_path_base():
    name = "gensrcs_output_expansion_pkg_path_base"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:PKG/PATH/BASE)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_external/wayland-protocols/bazel/texts/src1_suffix",
            "prefix_external/wayland-protocols/bazel/texts/src2_suffix",
            "prefix_external/wayland-protocols/bazel/src3_suffix",
        ],
    )
    return test_name

def _test_output_expansion_pkg_path_base_ext():
    name = "gensrcs_output_expansion_pkg_path_base_ext"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        output = "prefix_$(SRC:PKG/PATH/BASE.EXT)_suffix",
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "prefix_external/wayland-protocols/bazel/texts/src1.txt_suffix",
            "prefix_external/wayland-protocols/bazel/texts/src2.txt_suffix",
            "prefix_external/wayland-protocols/bazel/src3.txt_suffix",
        ],
    )
    return test_name

def _test_output_expansion_default():
    name = "gensrcs_output_expansion_default"
    test_name = name + "_test"

    gensrcs(
        name = name,
        cmd = "cat $(SRC) > $(OUT)",
        srcs = SRCS,
        tags = ["manual"],  # make sure it's not built using `:all`
    )

    output_path_expansion_test(
        name = test_name,
        target_under_test = name,
        expected_outputs = [
            "texts/src1.txt",
            "texts/src2.txt",
            "src3.txt",
        ],
    )
    return test_name

# ==== test suite ====

def gensrcs_test_suite(name):
    """Creates test targets for gensrcs.bzl"""
    native.test_suite(
        name = name,
        tests = [
            _test_output_expansion_base(),
            _test_output_expansion_base_ext(),
            _test_output_expansion_path_base(),
            _test_output_expansion_path_base_ext(),
            _test_output_expansion_pkg_path_base(),
            _test_output_expansion_pkg_path_base_ext(),
            _test_output_expansion_default(),
        ],
    )
