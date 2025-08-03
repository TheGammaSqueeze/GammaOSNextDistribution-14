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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//lib:unittest.bzl", "analysistest", "asserts")
load(":cc_stub_library.bzl", "cc_stub_gen")

_SYMBOL_FILE = "foo.map.txt"

def _get_actual_api_surface_attrs(package, all_args):
    # These are injected after api map and before symbol_file
    index_api_map = all_args.index("../soong_injection/api_levels/api_levels.json")
    index_symbol_file = all_args.index(paths.join(package, _SYMBOL_FILE))
    return all_args[index_api_map + 1:index_symbol_file]

def _cc_stub_gen_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)
    asserts.equals(
        env,
        len(actions),
        1,
        "expected a single cc stubgen action",
    )

    asserts.equals(
        env,
        ctx.attr.expected_api_surface_attrs,
        _get_actual_api_surface_attrs(ctx.label.package, actions[0].argv),
    )

    return analysistest.end(env)

cc_stub_gen_test = analysistest.make(
    _cc_stub_gen_test_impl,
    attrs = {
        "expected_api_surface_attrs": attr.string_list(),
    },
)

def _test_cc_stub_gen_modulelibapi_with_no_ndk():
    test_name = "cc_stub_gen_modulelibapi_with_no_ndk"
    target_under_test_name = test_name + "_target"

    cc_stub_gen(
        name = target_under_test_name,
        symbol_file = _SYMBOL_FILE,
        version = "current",
        api_surface = "module-libapi",
        source_library_label = "//:empty",
        tags = ["manual"],
    )

    cc_stub_gen_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_api_surface_attrs = ["--systemapi", "--apex", "--no-ndk"],
    )

    return test_name

# This was created from a Soong cc_library with a non-empty stubs.symbol_file
# There also exists a sibling ndk_library
def _test_cc_stub_gen_modulelibapi_with_ndk():
    test_name = "cc_stub_gen_modulelibapi_with_ndk"
    target_under_test_name = test_name + "_target"

    cc_stub_gen(
        name = target_under_test_name,
        symbol_file = _SYMBOL_FILE,
        version = "current",
        api_surface = "module-libapi",
        source_library_label = "//:empty",
        included_in_ndk = True,
        tags = ["manual"],
    )

    cc_stub_gen_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_api_surface_attrs = ["--systemapi", "--apex"],
    )

    return test_name

def _test_cc_stub_gen_publicapi():
    test_name = "cc_stub_gen_publicapi"
    target_under_test_name = test_name + "_target"

    cc_stub_gen(
        name = target_under_test_name,
        symbol_file = _SYMBOL_FILE,
        version = "current",
        api_surface = "publicapi",
        source_library_label = "//:empty",
        included_in_ndk = True,
        tags = ["manual"],
    )

    cc_stub_gen_test(
        name = test_name,
        target_under_test = target_under_test_name,
        expected_api_surface_attrs = [],
    )

    return test_name

def cc_stub_library_test_suite(name):
    native.test_suite(
        name = name,
        tests = [
            _test_cc_stub_gen_modulelibapi_with_no_ndk(),
            _test_cc_stub_gen_modulelibapi_with_ndk(),
            _test_cc_stub_gen_publicapi(),
        ],
    )
