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
load(
    "//build/bazel/rules/test_common:args.bzl",
    "get_all_args_with_prefix",
    "get_single_arg_with_prefix",
)
load("//build/bazel/rules/test_common:rules.bzl", "expect_failure_test")
load(":cc_library_static.bzl", "cc_library_static")
load(":clang_tidy.bzl", "generate_clang_tidy_actions")

_PACKAGE_HEADER_FILTER = "^build/bazel/rules/cc/"
_DEFAULT_GLOBAL_CHECKS = [
    "android-*",
    "bugprone-*",
    "cert-*",
    "clang-diagnostic-unused-command-line-argument",
    "google-build-explicit-make-pair",
    "google-build-namespaces",
    "google-runtime-operator",
    "google-upgrade-*",
    "misc-*",
    "performance-*",
    "portability-*",
    "-bugprone-assignment-in-if-condition",
    "-bugprone-easily-swappable-parameters",
    "-bugprone-narrowing-conversions",
    "-misc-const-correctness",
    "-misc-no-recursion",
    "-misc-non-private-member-variables-in-classes",
    "-misc-unused-parameters",
    "-performance-no-int-to-ptr",
    "-clang-analyzer-security.insecureAPI.DeprecatedOrUnsafeBufferHandling",
]
_DEFAULT_CHECKS = [
    "-misc-no-recursion",
    "-readability-function-cognitive-complexity",
    "-bugprone-unchecked-optional-access",
    "-bugprone-reserved-identifier*",
    "-cert-dcl51-cpp",
    "-cert-dcl37-c",
    "-readability-qualified-auto",
    "-bugprone-implicit-widening-of-multiplication-result",
    "-bugprone-easily-swappable-parameters",
    "-cert-err33-c",
    "-bugprone-unchecked-optional-access",
    "-misc-use-anonymous-namespace",
    "-performance-avoid-endl",
]
_DEFAULT_CHECKS_AS_ERRORS = [
    "-bugprone-assignment-in-if-condition",
    "-bugprone-branch-clone",
    "-bugprone-signed-char-misuse",
    "-misc-const-correctness",
    "-bugprone-unsafe-functions",
    "-cert-msc24-c",
    "-cert-msc33-c",
    "-modernize-type-traits",
    "-readability-avoid-unconditional-preprocessor-if",
]
_EXTRA_ARGS_BEFORE = [
    "-D__clang_analyzer__",
    "-Xclang",
    "-analyzer-config",
    "-Xclang",
    "c++-temp-dtor-inlining=false",
]

def _clang_tidy_impl(ctx):
    tidy_outs = generate_clang_tidy_actions(
        ctx,
        ctx.attr.copts,
        ctx.attr.deps,
        ctx.files.srcs,
        ctx.files.hdrs,
        ctx.attr.language,
        ctx.attr.tidy_flags,
        ctx.attr.tidy_checks,
        ctx.attr.tidy_checks_as_errors,
        ctx.attr.tidy_timeout_srcs,
    )
    return [
        DefaultInfo(files = depset(tidy_outs)),
    ]

_clang_tidy = rule(
    implementation = _clang_tidy_impl,
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "deps": attr.label_list(),
        "copts": attr.string_list(),
        "hdrs": attr.label_list(allow_files = True),
        "language": attr.string(values = ["c++", "c"], default = "c++"),
        "tidy_checks": attr.string_list(),
        "tidy_checks_as_errors": attr.string_list(),
        "tidy_flags": attr.string_list(),
        "tidy_timeout_srcs": attr.label_list(allow_files = True),
        "_clang_tidy_sh": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy.sh"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            doc = "The clang tidy shell wrapper",
        ),
        "_clang_tidy": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            doc = "The clang tidy executable",
        ),
        "_clang_tidy_real": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy.real"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
        ),
        "_with_tidy": attr.label(
            default = "//build/bazel/flags/cc/tidy:with_tidy",
        ),
        "_allow_local_tidy_true": attr.label(
            default = "//build/bazel/flags/cc/tidy:allow_local_tidy_true",
        ),
        "_with_tidy_flags": attr.label(
            default = "//build/bazel/flags/cc/tidy:with_tidy_flags",
        ),
        "_default_tidy_header_dirs": attr.label(
            default = "//build/bazel/flags/cc/tidy:default_tidy_header_dirs",
        ),
        "_tidy_timeout": attr.label(
            default = "//build/bazel/flags/cc/tidy:tidy_timeout",
        ),
        "_tidy_checks": attr.label(
            default = "//build/bazel/product_config:tidy_checks",
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
)

def _get_all_arg(env, actions, argname):
    args = get_all_args_with_prefix(actions[0].argv, argname)
    asserts.false(env, args == [], "could not arguments that start with `{}`".format(argname))
    return args

def _get_single_arg(actions, argname):
    return get_single_arg_with_prefix(actions[0].argv, argname)

def _checks_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    checks = _get_single_arg(actions, "-checks=").split(",")
    asserts.set_equals(env, sets.make(ctx.attr.expected_checks), sets.make(checks))
    if len(ctx.attr.unexpected_checks) > 0:
        for c in ctx.attr.unexpected_checks:
            asserts.false(env, c in checks, "found unexpected check in -checks flag: %s" % c)

    checks_as_errors = _get_single_arg(actions, "-warnings-as-errors=").split(",")
    asserts.set_equals(env, sets.make(ctx.attr.expected_checks_as_errors), sets.make(checks_as_errors))

    return analysistest.end(env)

_checks_test = analysistest.make(
    _checks_test_impl,
    attrs = {
        "expected_checks": attr.string_list(mandatory = True),
        "expected_checks_as_errors": attr.string_list(mandatory = True),
        "unexpected_checks": attr.string_list(),
    },
)

def _copts_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    args = actions[0].argv
    clang_flags = []
    for i, a in enumerate(args):
        if a == "--" and len(args) > i + 1:
            clang_flags = args[i + 1:]
            break
    asserts.true(
        env,
        len(clang_flags) > 0,
        "no flags passed to clang; all arguments: %s" % args,
    )

    for expected_arg in ctx.attr.expected_copts:
        asserts.true(
            env,
            expected_arg in clang_flags,
            "expected `%s` not present in clang flags" % expected_arg,
        )

    return analysistest.end(env)

_copts_test = analysistest.make(
    _copts_test_impl,
    attrs = {
        "expected_copts": attr.string_list(mandatory = True),
    },
)

def _tidy_flags_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    args = actions[0].argv
    tidy_flags = []
    for i, a in enumerate(args):
        if a == "--" and len(args) > i + 1:
            tidy_flags = args[:i]
    asserts.true(
        env,
        len(tidy_flags) > 0,
        "no tidy flags passed to clang-tidy; all arguments: %s" % args,
    )

    for expected_arg in ctx.attr.expected_tidy_flags:
        asserts.true(
            env,
            expected_arg in tidy_flags,
            "expected `%s` not present in flags to clang-tidy" % expected_arg,
        )

    header_filter = _get_single_arg(actions, "-header-filter=")
    asserts.true(
        env,
        header_filter == ctx.attr.expected_header_filter,
        (
            "expected header-filter to have value `%s`; got `%s`" %
            (ctx.attr.expected_header_filter, header_filter)
        ),
    )

    extra_arg_before = _get_all_arg(env, actions, "-extra-arg-before=")
    for expected_arg in ctx.attr.expected_extra_arg_before:
        asserts.true(
            env,
            expected_arg in extra_arg_before,
            "did not find expected flag `%s` in args to clang-tidy" % expected_arg,
        )

    return analysistest.end(env)

_tidy_flags_test = analysistest.make(
    _tidy_flags_test_impl,
    attrs = {
        "expected_tidy_flags": attr.string_list(),
        "expected_header_filter": attr.string(mandatory = True),
        "expected_extra_arg_before": attr.string_list(),
    },
)

def _test_clang_tidy():
    name = "checks"
    test_name = name + "_test"
    checks_test_name = test_name + "_checks"
    copts_test_name = test_name + "_copts"
    tidy_flags_test_name = test_name + "_tidy_flags"

    _clang_tidy(
        name = name,
        # clang-tidy operates differently on generated and non-generated files
        # use test_srcs so that the tidy rule doesn't think these are genearted
        # files
        srcs = ["//build/bazel/rules/cc/testing:test_srcs"],
        copts = ["-asdf1", "-asdf2"],
        tidy_flags = ["-tidy-flag1", "-tidy-flag2"],
        tags = ["manual"],
    )

    _checks_test(
        name = checks_test_name,
        target_under_test = name,
        expected_checks = _DEFAULT_CHECKS + _DEFAULT_GLOBAL_CHECKS,
        expected_checks_as_errors = _DEFAULT_CHECKS_AS_ERRORS,
    )

    _copts_test(
        name = copts_test_name,
        target_under_test = name,
        expected_copts = ["-asdf1", "-asdf2"],
    )

    _tidy_flags_test(
        name = tidy_flags_test_name,
        target_under_test = name,
        expected_tidy_flags = ["-tidy-flag1", "-tidy-flag2"],
        expected_header_filter = _PACKAGE_HEADER_FILTER,
        expected_extra_arg_before = _EXTRA_ARGS_BEFORE,
    )

    return [
        checks_test_name,
        copts_test_name,
        tidy_flags_test_name,
    ]

def _test_custom_header_dir():
    name = "custom_header_dir"
    test_name = name + "_test"

    _clang_tidy(
        name = name,
        srcs = ["a.cpp"],
        tidy_flags = ["-header-filter=dir1/"],
        tags = ["manual"],
    )

    _tidy_flags_test(
        name = test_name,
        target_under_test = name,
        expected_header_filter = "dir1/",
    )

    return [
        test_name,
    ]

def _test_disabled_checks_are_removed():
    name = "disabled_checks_are_removed"
    test_name = name + "_test"

    _clang_tidy(
        name = name,
        # clang-tidy operates differently on generated and non-generated files.
        # use test_srcs so that the tidy rule doesn't think these are genearted
        # files
        srcs = ["//build/bazel/rules/cc/testing:test_srcs"],
        tidy_checks = ["misc-no-recursion", "readability-function-cognitive-complexity"],
        tags = ["manual"],
    )

    _checks_test(
        name = test_name,
        target_under_test = name,
        expected_checks = _DEFAULT_CHECKS + _DEFAULT_GLOBAL_CHECKS,
        expected_checks_as_errors = _DEFAULT_CHECKS_AS_ERRORS,
        unexpected_checks = ["misc-no-recursion", "readability-function-cognitive-complexity"],
    )

    return [
        test_name,
    ]

def _create_bad_tidy_checks_test(name, tidy_checks, failure_message):
    name = "bad_tidy_checks_fail_" + name
    test_name = name + "_test"

    _clang_tidy(
        name = name,
        srcs = ["a.cpp"],
        tidy_checks = tidy_checks,
        tags = ["manual"],
    )

    expect_failure_test(
        name = test_name,
        target_under_test = name,
        failure_message = failure_message,
    )

    return [
        test_name,
    ]

def _test_bad_tidy_checks_fail():
    return (
        _create_bad_tidy_checks_test(
            name = "with_spaces",
            tidy_checks = ["check with spaces"],
            failure_message = "Check `check with spaces` invalid, cannot contain spaces",
        ) +
        _create_bad_tidy_checks_test(
            name = "with_commas",
            tidy_checks = ["check,with,commas"],
            failure_message = "Check `check,with,commas` invalid, cannot contain commas. Split each entry into its own string instead",
        )
    )

def _create_bad_tidy_flags_test(name, tidy_flags, failure_message):
    name = "bad_tidy_flags_fail_" + name
    test_name = name + "_test"

    _clang_tidy(
        name = name,
        srcs = ["a.cpp"],
        tidy_flags = tidy_flags,
        tags = ["manual"],
    )

    expect_failure_test(
        name = test_name,
        target_under_test = name,
        failure_message = failure_message,
    )

    return [
        test_name,
    ]

def _test_bad_tidy_flags_fail():
    return (
        _create_bad_tidy_flags_test(
            name = "without_leading_dash",
            tidy_flags = ["flag1"],
            failure_message = "Flag `flag1` must start with `-`",
        ) +
        _create_bad_tidy_flags_test(
            name = "fix_flags",
            tidy_flags = ["-fix"],
            failure_message = "Flag `%s` is not allowed, since it could cause multiple writes to the same source file",
        ) +
        _create_bad_tidy_flags_test(
            name = "checks_in_flags",
            tidy_flags = ["-checks=asdf"],
            failure_message = "Flag `-checks=asdf` is not allowed, use `tidy_checks` property instead",
        ) +
        _create_bad_tidy_flags_test(
            name = "warnings_as_errors_in_flags",
            tidy_flags = ["-warnings-as-errors=asdf"],
            failure_message = "Flag `-warnings-as-errors=asdf` is not allowed, use `tidy_checks_as_errors` property instead",
        ) +
        _create_bad_tidy_flags_test(
            name = "space_in_flags",
            tidy_flags = ["-flag with spaces"],
            failure_message = "Bad flag: `-flag with spaces` is not an allowed multi-word flag. Should it be split into multiple flags",
        )
    )

def _test_disable_global_checks():
    name = "disable_global_checks"
    test_name = name + "_test"

    _clang_tidy(
        name = name,
        srcs = ["a.cpp"],
        tidy_checks = ["-*"],
        tags = ["manual"],
    )

    _checks_test(
        name = test_name,
        target_under_test = name,
        expected_checks = ["-*"] + _DEFAULT_CHECKS,
        expected_checks_as_errors = _DEFAULT_CHECKS_AS_ERRORS,
    )

    return [
        test_name,
    ]

def _cc_library_static_generates_clang_tidy_actions_for_srcs_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    clang_tidy_actions = [a for a in actions if a.mnemonic == "ClangTidy"]
    asserts.equals(
        env,
        ctx.attr.expected_num_actions,
        len(clang_tidy_actions),
        "expected to have %s clang-tidy actions, but got %s; actions: %s" % (
            ctx.attr.expected_num_actions,
            len(clang_tidy_actions),
            clang_tidy_actions,
        ),
    )

    for a in clang_tidy_actions:
        for input in a.inputs.to_list():
            input_is_expected_header = input.short_path in [f.short_path for f in ctx.files.expected_headers]
            if input in ctx.files._clang_tidy_tools or input_is_expected_header:
                continue
            asserts.true(
                env,
                input in ctx.files.srcs,
                "clang-tidy operated on a file not in srcs: %s; all inputs: %s" % (input, a.inputs.to_list()),
            )
            asserts.true(
                env,
                input not in ctx.files.disabled_srcs,
                "clang-tidy operated on a file in disabled_srcs: %s; all inputs: %s" % (input, a.inputs.to_list()),
            )

    return analysistest.end(env)

_cc_library_static_generates_clang_tidy_actions_for_srcs_test = analysistest.make(
    impl = _cc_library_static_generates_clang_tidy_actions_for_srcs_test_impl,
    attrs = {
        "expected_num_actions": attr.int(mandatory = True),
        "srcs": attr.label_list(allow_files = True),
        "disabled_srcs": attr.label_list(allow_files = True),
        "expected_headers": attr.label_list(allow_files = True),
        "_clang_tidy_tools": attr.label_list(
            default = [
                "@//prebuilts/clang/host/linux-x86:clang-tidy",
                "@//prebuilts/clang/host/linux-x86:clang-tidy.real",
                "@//prebuilts/clang/host/linux-x86:clang-tidy.sh",
            ],
            allow_files = True,
        ),
    },
    config_settings = {
        "@//build/bazel/flags/cc/tidy:allow_local_tidy_true": True,
    },
)

def _create_cc_library_static_generates_clang_tidy_actions_for_srcs(
        name,
        srcs,
        expected_num_actions,
        disabled_srcs = None,
        expected_headers = []):
    name = "cc_library_static_generates_clang_tidy_actions_for_srcs_" + name
    test_name = name + "_test"

    cc_library_static(
        name = name,
        srcs = srcs,
        tidy_disabled_srcs = disabled_srcs,
        tidy = "local",
        tags = ["manual"],
    )

    _cc_library_static_generates_clang_tidy_actions_for_srcs_test(
        name = test_name,
        target_under_test = name,
        expected_num_actions = expected_num_actions,
        srcs = srcs,
        disabled_srcs = disabled_srcs,
        expected_headers = expected_headers + select({
            "//build/bazel_common_rules/platforms/os:android": ["@//bionic/libc:generated_android_ids"],
            "//conditions:default": [],
        }),
    )

    return test_name

def _test_cc_library_static_generates_clang_tidy_actions_for_srcs():
    return [
        _create_cc_library_static_generates_clang_tidy_actions_for_srcs(
            name = "with_srcs",
            srcs = ["a.cpp", "b.cpp"],
            expected_num_actions = 2,
        ),
        _create_cc_library_static_generates_clang_tidy_actions_for_srcs(
            name = "with_disabled_srcs",
            srcs = ["a.cpp", "b.cpp"],
            disabled_srcs = ["b.cpp", "c.cpp"],
            expected_num_actions = 1,
        ),
    ]

def _no_clang_analyzer_on_generated_files_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    clang_tidy_actions = [a for a in actions if a.mnemonic == "ClangTidy"]
    for a in clang_tidy_actions:
        found_clang_analyzer = False
        for arg in a.argv:
            if "-clang-analyzer-*" in arg:
                found_clang_analyzer = True
        asserts.true(env, found_clang_analyzer)

    return analysistest.end(env)

_no_clang_analyzer_on_generated_files_test = analysistest.make(
    impl = _no_clang_analyzer_on_generated_files_test_impl,
    config_settings = {
        "@//build/bazel/flags/cc/tidy:allow_local_tidy_true": True,
    },
)

def _test_no_clang_analyzer_on_generated_files():
    name = "no_clang_analyzer_on_generated_files"
    gen_name = name + "_generated_files"
    test_name = name + "_test"

    native.genrule(
        name = gen_name,
        outs = ["aout.cpp", "bout.cpp"],
        cmd = "touch $(OUTS)",
        tags = ["manual"],
    )

    cc_library_static(
        name = name,
        srcs = [":" + gen_name],
        tidy = "local",
        tags = ["manual"],
    )

    _no_clang_analyzer_on_generated_files_test(
        name = test_name,
        target_under_test = name,
    )

    return [
        test_name,
    ]

def _clang_tidy_actions_count_no_tidy_env_test_impl(ctx):
    env = analysistest.begin(ctx)
    actions = analysistest.target_actions(env)

    clang_tidy_actions = [a for a in actions if a.mnemonic == "ClangTidy"]
    asserts.equals(
        env,
        ctx.attr.expected_num_tidy_actions,
        len(clang_tidy_actions),
        "expected to find %d tidy actions, but found %d" % (
            ctx.attr.expected_num_tidy_actions,
            len(clang_tidy_actions),
        ),
    )

    return analysistest.end(env)

_clang_tidy_actions_count_no_tidy_env_test = analysistest.make(
    impl = _clang_tidy_actions_count_no_tidy_env_test_impl,
    attrs = {
        "expected_num_tidy_actions": attr.int(),
    },
)

_clang_tidy_actions_count_with_tidy_true_test = analysistest.make(
    impl = _clang_tidy_actions_count_no_tidy_env_test_impl,
    attrs = {
        "expected_num_tidy_actions": attr.int(),
    },
    config_settings = {
        "@//build/bazel/flags/cc/tidy:with_tidy": True,
    },
)

_clang_tidy_actions_count_with_allow_local_tidy_true_test = analysistest.make(
    impl = _clang_tidy_actions_count_no_tidy_env_test_impl,
    attrs = {
        "expected_num_tidy_actions": attr.int(),
    },
    config_settings = {
        "@//build/bazel/flags/cc/tidy:allow_local_tidy_true": True,
    },
)

def _test_clang_tidy_runs_if_tidy_true():
    name = "clang_tidy_runs_if_tidy_true"
    test_name = name + "_test"
    with_tidy_test_name = test_name + "_with_tidy_true"
    allow_local_tidy_true_test_name = test_name + "_allow_local_tidy_true"

    cc_library_static(
        name = name,
        srcs = ["a.cpp"],
        tidy = "local",
        tags = ["manual"],
    )
    _clang_tidy_actions_count_no_tidy_env_test(
        name = test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    _clang_tidy_actions_count_with_tidy_true_test(
        name = with_tidy_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 1,
    )
    _clang_tidy_actions_count_with_allow_local_tidy_true_test(
        name = allow_local_tidy_true_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 1,
    )
    return [
        test_name,
        with_tidy_test_name,
        allow_local_tidy_true_test_name,
    ]

def _test_clang_tidy_runs_if_attribute_unset():
    name = "clang_tidy_runs_if_attribute_unset"
    test_name = name + "_test"
    with_tidy_test_name = test_name + "_with_tidy_true"
    allow_local_tidy_true_test_name = test_name + "_allow_local_tidy_true"

    cc_library_static(
        name = name,
        srcs = ["a.cpp"],
        tags = ["manual"],
    )
    _clang_tidy_actions_count_no_tidy_env_test(
        name = test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    _clang_tidy_actions_count_with_tidy_true_test(
        name = with_tidy_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 1,
    )
    _clang_tidy_actions_count_with_allow_local_tidy_true_test(
        name = allow_local_tidy_true_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    return [
        test_name,
        with_tidy_test_name,
        allow_local_tidy_true_test_name,
    ]

def _test_no_clang_tidy_if_tidy_false():
    name = "no_clang_tidy_if_tidy_false"
    test_name = name + "_test"
    with_tidy_test_name = test_name + "_with_tidy_true"
    allow_local_tidy_true_test_name = test_name + "_allow_local_tidy_true"

    cc_library_static(
        name = name,
        srcs = ["a.cpp"],
        tidy = "never",
        tags = ["manual"],
    )
    _clang_tidy_actions_count_no_tidy_env_test(
        name = test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    _clang_tidy_actions_count_with_tidy_true_test(
        name = with_tidy_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    _clang_tidy_actions_count_with_allow_local_tidy_true_test(
        name = allow_local_tidy_true_test_name,
        target_under_test = name,
        expected_num_tidy_actions = 0,
    )
    return [
        test_name,
        with_tidy_test_name,
        allow_local_tidy_true_test_name,
    ]

def clang_tidy_test_suite(name):
    native.test_suite(
        name = name,
        tests =
            _test_clang_tidy() +
            _test_custom_header_dir() +
            _test_disabled_checks_are_removed() +
            _test_bad_tidy_checks_fail() +
            _test_bad_tidy_flags_fail() +
            _test_disable_global_checks() +
            _test_cc_library_static_generates_clang_tidy_actions_for_srcs() +
            _test_no_clang_analyzer_on_generated_files() +
            _test_no_clang_tidy_if_tidy_false() +
            _test_clang_tidy_runs_if_tidy_true() +
            _test_clang_tidy_runs_if_attribute_unset(),
    )
