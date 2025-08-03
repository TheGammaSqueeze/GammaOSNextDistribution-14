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

"""The tradefest test ruleset.

This file contains the definition and implementation of:

- tradefed_test_suite, which expands to
    - tradefed_deviceless_test
    - tradefed_host_driven_device_test
    - tradefed_device_driven_test

These rules provide Tradefed harness support around test executables and
runfiles. They are language independent, and thus work with cc_test, java_test,
and other test types.

The execution mode (host, device, deviceless) is automatically determined by the
target_compatible_with attribute of the test dependency. Whether a test runs is
handled by Bazel's incompatible target skipping, i.e. a test dep that's
compatible only with android would cause the tradefed_deviceless_test to be
SKIPPED automatically.
"""

load("@bazel_skylib//lib:dicts.bzl", "dicts")
load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@env//:env.bzl", "env")
load("//build/bazel/flags:common.bzl", "is_env_true")
load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load("//build/bazel_common_rules/rules/remote_device/device:device_environment.bzl", "DeviceEnvironment")
load(":cc_aspects.bzl", "CcTestSharedLibsInfo", "collect_cc_libs_aspect")

# Apply this suffix to the name of the test dep target (e.g. the cc_test target)
TEST_DEP_SUFFIX = "__tf_internal"

# Apply this suffix to the name of the test filter generator target.
FILTER_GENERATOR_SUFFIX = "__filter_generator"

LANGUAGE_CC = "cc"
LANGUAGE_JAVA = "java"
LANGUAGE_ANDROID = "android"
LANGUAGE_SHELL = "shell"

# A transition to force the target device platforms configuration. This is
# used in the tradefed -> cc_test edge (for example).
#
# TODO(b/290716628): Handle multilib. For example, cc_test sets `multilib:
# "both"` by default, so this may drop the secondary arch of the test, depending
# on the TARGET_PRODUCT.
def _tradefed_always_device_transition_impl(settings, _):
    device_platform = str(settings["//build/bazel/product_config:device_platform"])
    return {
        "//command_line_option:platforms": device_platform,
    }

_tradefed_always_device_transition = transition(
    implementation = _tradefed_always_device_transition_impl,
    inputs = ["//build/bazel/product_config:device_platform"],
    outputs = ["//command_line_option:platforms"],
)

_TRADEFED_TEST_ATTRIBUTES = {
    "_tradefed_test_sh_template": attr.label(
        default = ":tradefed.sh.tpl",
        allow_single_file = True,
        doc = "Template script to launch tradefed.",
    ),
    "_atest_tradefed_launcher": attr.label(
        default = "//tools/asuite/atest:atest_tradefed.sh",
        allow_single_file = True,
        cfg = "exec",
    ),
    "_atest_helper": attr.label(
        default = "//tools/asuite/atest:atest_script_help.sh",
        allow_single_file = True,
        cfg = "exec",
    ),
    # TODO(b/285949958): Use source-built adb for device tests.
    "_adb": attr.label(
        default = "//prebuilts/runtime:prebuilt-runtime-adb",
        executable = True,
        allow_single_file = True,
        cfg = "exec",
    ),
    "_aapt": attr.label(
        default = "//frameworks/base/tools/aapt:aapt",
        executable = True,
        cfg = "exec",
        doc = "aapt (v1). Used by Tradefed.",
    ),
    "_aapt2": attr.label(
        default = "//frameworks/base/tools/aapt2:aapt2",
        executable = True,
        cfg = "exec",
        doc = "aapt (v2). Used by Tradefed.",
    ),
    "_auto_gen_test_config": attr.label(
        default = "//build/make/tools:auto_gen_test_config",
        executable = True,
        cfg = "exec",
        doc = "Python script for automatically generating the Tradefed test config for android tests.",
    ),
    "_empty_test_config": attr.label(
        default = "//build/make/core:empty_test_config.xml",
        allow_single_file = True,
    ),
    "_tradefed_dependencies": attr.label_list(
        default = [
            "//tools/tradefederation/prebuilts/filegroups/tradefed:tradefed-prebuilt",
            "//tools/tradefederation/prebuilts/filegroups/suite:compatibility-host-util-prebuilt",
            "//tools/tradefederation/prebuilts/filegroups/suite:compatibility-tradefed-prebuilt",
            "//tools/asuite/atest:atest-tradefed",
            "//tools/asuite/atest/bazel/reporter:bazel-result-reporter",
        ],
        doc = "Files needed on the classpath to run tradefed",
        cfg = "exec",
    ),
    "data_bins": attr.label_list(
        doc = "Executables that need to be installed alongside the test entry point.",
        cfg = "exec",
    ),
    "_platform_utils": attr.label(
        default = Label("//build/bazel/platforms:platform_utils"),
    ),
    "test_config": attr.label(
        allow_single_file = True,
        doc = "Test/Tradefed config.",
    ),
    "dynamic_config": attr.label(
        allow_single_file = True,
        doc = "Dynamic test config.",
    ),
    "template_test_config": attr.label(
        allow_single_file = True,
        doc = "Template to generate test config.",
    ),
    "template_configs": attr.string_list(
        doc = "Extra tradefed config options to extend into generated test config.",
    ),
    "template_install_base": attr.string(
        default = "/data/local/tmp",
        doc = "Directory to install tests onto the device for generated config",
    ),
    "test_filter_generator": attr.label(
        allow_single_file = True,
        doc = "test filter to specify test class and method to run",
    ),
    "test_language": attr.string(
        default = "",
        values = ["", LANGUAGE_CC, LANGUAGE_JAVA, LANGUAGE_ANDROID, LANGUAGE_SHELL],
        doc = "the programming language the test uses",
    ),
    "suffix": attr.string(
        default = "",
        values = ["", "32", "64"],
        doc = "the suffix of the test binary",
    ),
    "_java_runtime": attr.label(
        default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
        cfg = "exec",
        providers = [java_common.JavaRuntimeInfo],
    ),
}

# The normalized name of test under tradefed harness. This is without any of the
#
# "__tf" suffixes, e.g. adbd_test or hello_world_test.
#
#The normalized module name is used as the stem for the test executable or
# config files, which are referenced in AndroidTest.xml, like in PushFilePreparer elements.
def _normalize_test_name(s):
    return s.replace(TEST_DEP_SUFFIX, "")

def _copy_file(ctx, input, output):
    ctx.actions.run_shell(
        inputs = depset(direct = [input]),
        outputs = [output],
        command = "cp -f %s %s" % (input.path, output.path),
        mnemonic = "CopyFile",
        use_default_shell_env = True,
    )

# Get test config if specified or generate test config from template.
def _get_or_generate_test_config(ctx, module_name, tf_test_dir, test_entry_point, test_language):
    # Validate input
    total = 0
    if ctx.file.test_config:
        total += 1
    if ctx.file.template_test_config:
        total += 1
    if total != 1:
        fail("Exactly one of test_config or test_config_template should be provided, but got: " +
             "%s %s" % (ctx.file.test_config, ctx.file.template_test_config))

    # If dynamic_config is specified copy it with a new name.
    dynamic_config = None
    if ctx.file.dynamic_config:
        # Dynamic config file is specified in test config file and doesn't have the 32/64 suffix.
        dynamic_config = ctx.actions.declare_file(paths.join(tf_test_dir, module_name + ".dynamic"))
        _copy_file(ctx, ctx.file.dynamic_config, dynamic_config)

    # If existing tradefed config is specified, copy to it and return early.
    #
    # The config needs to be a sibling file to the test executable, and both
    # files must be in tf_test_dir. Given that ctx.file.test_config could be
    # from another package, like //build/make/core, this copy handles that.
    #
    # $ tree bazel-bin/packages/modules/adb/adb_test__tf_deviceless_test/testcases/
    # bazel-bin/packages/modules/adb/adb_test__tf_deviceless_test/testcases/
    # ├── adb_test
    # └── adb_test.config
    test_config = ctx.actions.declare_file(paths.join(tf_test_dir, module_name + ".config"))
    if ctx.file.test_config:
        _copy_file(ctx, ctx.file.test_config, test_config)
        return test_config, dynamic_config

    # No test config specified, generate config from template.

    if test_language == LANGUAGE_ANDROID:
        # android tests require a tool to parse the final AndroidManifest.xml
        # for label, package and runner class.
        #
        # First, dump the xmltree with aapt2. android_binary doesn't have a
        # provider to access the AndroidManifest.xml directly, and we can't use
        # the compiled XML from the APK directly.
        xmltree = ctx.actions.declare_file(module_name + ".xmltree", sibling = test_config)
        extra_configs = ""
        if ctx.attr.template_configs:
            extra_configs = "--extra-configs %s" % ("\\n    ".join(ctx.attr.template_configs))
        ctx.actions.run_shell(
            inputs = [test_entry_point, ctx.executable._aapt2],
            outputs = [xmltree],
            command = "%s dump xmltree %s --file AndroidManifest.xml %s > %s" % (
                ctx.executable._aapt2.path,
                test_entry_point.path,
                extra_configs,
                xmltree.path,
            ),
            mnemonic = "DumpManifestXmlTree",
            progress_message = "Extracting test information from AndroidManifest.xml for %s" % module_name,
        )

        # Then, run auto_gen_test_config.py which has a small xmltree parser.
        args = ctx.actions.args()
        args.add_all([test_config, xmltree, ctx.file._empty_test_config, ctx.file.template_test_config])
        ctx.actions.run(
            executable = ctx.executable._auto_gen_test_config,
            arguments = [args],
            inputs = [
                xmltree,
                ctx.file._empty_test_config,
                ctx.file.template_test_config,
            ],
            outputs = [test_config],
            mnemonic = "AutoGenTestConfig",
            progress_message = "Generating Tradefed test config for %s" % module_name,
        )

        return test_config, dynamic_config

    # Non-android tests.
    expand_template_substitutions = {
        "{MODULE}": module_name,
        "{EXTRA_CONFIGS}": "\n    ".join(ctx.attr.template_configs),
        "{TEST_INSTALL_BASE}": ctx.attr.template_install_base,
    }
    if test_language == LANGUAGE_SHELL:
        expand_template_substitutions["{OUTPUT_FILENAME}"] = module_name + ".sh"
    ctx.actions.expand_template(
        template = ctx.file.template_test_config,
        output = test_config,
        substitutions = expand_template_substitutions,
    )
    return test_config, dynamic_config

# Generate tradefed result reporter config.
def _create_result_reporter_config(ctx):
    result_reporters_config_file = ctx.actions.declare_file("result-reporters.xml")
    config_lines = [
        "<?xml version=\"1.0\" encoding=\"utf-8\"?>",
        "<configuration>",
    ]

    result_reporters = [
        "com.android.tradefed.result.BazelExitCodeResultReporter",
        "com.android.tradefed.result.BazelXmlResultReporter",
        "com.android.tradefed.result.proto.FileProtoResultReporter",
    ]
    for result_reporter in result_reporters:
        config_lines.append("    <result_reporter class=\"%s\" />" % result_reporter)
    config_lines.append("</configuration>")

    ctx.actions.write(result_reporters_config_file, "\n".join(config_lines))
    return result_reporters_config_file

# Get the test Target object.
#
# ctx.attr.test could be a list, depending on the rule configuration. Host
# driven device test transitions ctx.attr.test to device config, which turns the
# test attr into a label list.
def _get_test_target(ctx):
    if type(ctx.attr.test) == "list":
        return ctx.attr.test[0]
    return ctx.attr.test

# Generate and run tradefed bash script entry point and associated runfiles.
def _tradefed_test_impl(ctx, tradefed_options = []):
    device_script = ""
    if _is_remote_device_test(ctx):
        device_script = _abspath(ctx.attr._run_with[DeviceEnvironment].runner.to_list()[0].short_path)

    tf_test_dir = paths.join(ctx.label.name, "testcases")
    test_target = _get_test_target(ctx)
    test_language = ctx.attr.test_language
    if test_language == LANGUAGE_ANDROID:
        test_entry_point = test_target[ApkInfo].signed_apk
    else:
        # cc, java, py, sh
        test_entry_point = test_target.files_to_run.executable

    # For Java, a library may make more sense here than the executable. When
    # expanding tradefed_test_impl to accept more rule types, this could be
    # turned into a provider, whether set by the rule or an aspect visiting the
    # rule.
    test_basename_with_ext = _normalize_test_name(test_entry_point.basename)
    module_name = paths.replace_extension(test_basename_with_ext, "")  # clean module name

    test_config_files = []

    # Get or generate test config.
    test_config, dynamic_config = _get_or_generate_test_config(
        ctx,
        module_name,
        tf_test_dir,
        test_entry_point,
        test_language,
    )
    test_config_files.append(test_config)
    if dynamic_config != None:
        test_config_files.append(dynamic_config)

    # Generate result reporter config file.
    report_config = _create_result_reporter_config(ctx)

    test_runfiles = []

    test_filter_output = None
    if ctx.attr.test_filter_generator:
        test_filter_output = ctx.file.test_filter_generator
        test_runfiles.append(test_filter_output)

    # This may contain a 32/64 suffix for multilib native test, or .jar/.apk
    # extension for others.
    out = ctx.actions.declare_file(test_basename_with_ext, sibling = test_config)

    # Copy the test executable to the test cases directory
    _copy_file(ctx, test_entry_point, out)
    root_relative_tests_dir = paths.dirname(out.short_path)
    test_runfiles.append(out)

    if ctx.attr.suffix and test_basename_with_ext.endswith(ctx.attr.suffix):
        # Create a compat entry point symlink without the 32/64 suffix so
        # Tradefed can find it with its local file target preparers, like
        # PushFilePreparer.
        #
        # This is also so that the test will pass regardless of
        # whether `<option name="append-bitness" value="true" />` is defined in
        # AndroidTest.xml.
        out_without_suffix = ctx.actions.declare_file(
            test_basename_with_ext.removesuffix(ctx.attr.suffix),
            sibling = out,
        )
        ctx.actions.symlink(
            output = out_without_suffix,
            target_file = out,
        )

        test_runfiles.append(out_without_suffix)

    if CcTestSharedLibsInfo in test_target:
        # We set the linker rpath in bazel and the binary will always look for shared libs in lib/lib64, we copy
        # all the shared libs to lib/lib64 so that the binary can load them correctly.
        # https://source.corp.google.com/h/android/platform/superproject/main/+/main:prebuilts/clang/host/linux-x86/cc_toolchain_features.bzl;l=771;drc=68f2dd9d06b946e99153304b3de04d1ac2c0d599
        # Soong does something similar: https://cs.android.com/android/platform/superproject/main/+/main:build/soong/cc/linker.go;l=584-621;drc=391a25d7fa3d4dd316b8263b0fd190aa5f33e4e8
        # TODO(b/294915120): add rapth support for 32 bit arch.
        shared_lib_dir = "lib"
        if platforms.get_target_bitness(ctx.attr._platform_utils) == 64:
            shared_lib_dir = "lib64"
        for lib in test_target[CcTestSharedLibsInfo].shared_libs.to_list():
            lib_out = ctx.actions.declare_file(paths.join(tf_test_dir, shared_lib_dir, lib.basename))
            _copy_file(ctx, lib, lib_out)
            test_runfiles.append(lib_out)

    # Prepare test-provided runfiles
    for f in test_target.files.to_list():
        if f == test_entry_point:
            continue
        test_runfiles.append(f)

    # Add harness dependencies into runfiles.
    test_runfiles.extend(ctx.files._tradefed_dependencies)
    test_runfiles.append(ctx.executable._adb)
    test_runfiles.append(ctx.executable._aapt)
    test_runfiles.append(ctx.executable._aapt2)

    # Make the test harness tooling available in the $PATH of the test action
    path_additions = [
        _abspath(paths.dirname(ctx.executable._adb.short_path)),
        _abspath(paths.dirname(ctx.executable._aapt.short_path)),
        _abspath(paths.dirname(ctx.executable._aapt2.short_path)),
    ]

    for runfile in test_target.default_runfiles.files.to_list():
        if runfile == test_entry_point:
            continue
        suffix = runfile.basename.removeprefix(test_entry_point.basename)
        if suffix in ["_versioned", "_unstripped"]:
            continue
        path_without_package = runfile.short_path.removeprefix(ctx.label.package + "/")
        out = ctx.actions.declare_file("%s/%s" % (tf_test_dir, path_without_package))
        _copy_file(ctx, runfile, out)
        test_runfiles.append(out)

    # Data_bins are special dependencies added to the working directory of the test entry point (as siblings), which are different from regular runfiles above.
    for data_bin_file in ctx.files.data_bins:
        data_bin_out = ctx.actions.declare_file("%s/%s" % (tf_test_dir, data_bin_file.basename))
        if data_bin_out in test_runfiles:
            continue
        _copy_file(ctx, data_bin_file, data_bin_out)
        test_runfiles.append(data_bin_out)

    # Gather runfiles.
    runfiles = ctx.runfiles(
        files = test_runfiles + test_config_files + [
            report_config,
            ctx.file._atest_tradefed_launcher,
            ctx.file._atest_helper,
        ],
    )
    runfiles = runfiles.merge(test_target.default_runfiles)

    # Append remote device runfiles if using remote execution.
    if _is_remote_device_test(ctx):
        runfiles = runfiles.merge(ctx.runfiles().merge(ctx.attr._run_with[DeviceEnvironment].data))
        java_home = "/jdk/jdk17/linux-x86"
    else:
        java_runtime = ctx.attr._java_runtime[java_common.JavaRuntimeInfo]
        runfiles = runfiles.merge(ctx.runfiles(java_runtime.files.to_list()))
        java_home = java_runtime.java_home_runfiles_path

    # Generate script to run tradefed.
    script = ctx.actions.declare_file("%s.sh" % ctx.label.name)
    ctx.actions.expand_template(
        template = ctx.file._tradefed_test_sh_template,
        output = script,
        is_executable = True,
        substitutions = {
            "{module_name}": module_name,
            "{atest_tradefed_launcher}": _abspath(ctx.file._atest_tradefed_launcher.short_path),
            "{atest_helper}": _abspath(ctx.file._atest_helper.short_path),
            "{tradefed_classpath}": _classpath(ctx.files._tradefed_dependencies),
            "{path_additions}": ":".join(path_additions),
            "{root_relative_tests_dir}": root_relative_tests_dir,
            "{additional_tradefed_options}": " ".join(tradefed_options),
            "{test_filter_output}": _abspath(test_filter_output.short_path) if test_filter_output else "",
            "{device_script}": device_script,
            "{java_home}": java_home,
        },
    )

    return [DefaultInfo(
        executable = script,
        runfiles = runfiles,
    )]

def _tradefed_deviceless_test_impl(ctx):
    return _tradefed_test_impl(
        ctx,
        tradefed_options = [
            "--null-device",  # don't allocate a device
        ],
    )

tradefed_deviceless_test = rule(
    attrs = dicts.add(_TRADEFED_TEST_ATTRIBUTES, {
        "test": attr.label(
            # Deviceless test executables should always build for host.
            doc = "Test target to run in tradefed.",
            aspects = [collect_cc_libs_aspect],
        ),
    }),
    test = True,
    implementation = _tradefed_deviceless_test_impl,
    doc = """A rule used to run host deviceless tests using Tradefed.

Generally tests that use one of the following runners (not exhaustive):

- com.android.compatibility.common.tradefed.testtype.JarHostTest (java_test_host).
- com.android.tradefed.testtype.HostTest
- com.android.tradefed.testtype.HostGTest (cc_test_host)
- com.android.tradefed.testtype.python.PythonBinaryHostTest

These are tests built and executed on the host, and do NOT need a
connected device to run.
""",
)

tradefed_device_driven_test = rule(
    attrs = dicts.add(_TRADEFED_TEST_ATTRIBUTES, {
        "test": attr.label(
            # Device driven tests should always build for device.
            cfg = _tradefed_always_device_transition,
            doc = "Test target to run in tradefed.",
            aspects = [collect_cc_libs_aspect],
        ),
        "_exec_mode": attr.label(default = "//build/bazel_common_rules/rules/remote_device:exec_mode"),
        "_run_with": attr.label(default = "//build/bazel_common_rules/rules/remote_device:target_device"),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    }),
    test = True,
    implementation = _tradefed_test_impl,
    doc = """A rule used to run device-driven tests using Tradefed

Generally tests that use one of the following runners (not exhaustive):

- com.android.tradefed.testtype.AndroidJUnitTest (android instrumentation test).
- com.android.tradefed.testtype.GTest (native device tests)
- com.android.compatibility.testtype.LibcoreTest
- com.android.compatibility.testtype.DalvikTest
- com.android.compatibility.tradefed.CtsRootTradefedTest

This results in a single tradefed invocation for a device driven test that uses
plugins like SuiteApkInstaller. Tradefed is still executed on the host,
but the test is driven entirely on the device.""",
)

tradefed_host_driven_device_test = rule(
    attrs = dicts.add(_TRADEFED_TEST_ATTRIBUTES, {
        "test": attr.label(
            # Host driven device tests should always build for the host by
            # default (since they're host driven!). There may be dependencies
            # from the test to other runtime deps which the host executable will
            # push onto the device during test runtime, but we'll let the test
            # itself handle the configuration transition to those dependencies.
            doc = "Test target to run in tradefed.",
            aspects = [collect_cc_libs_aspect],
        ),
    }),
    test = True,
    implementation = _tradefed_test_impl,
    doc = """A rule used to run host-driven device tests using Tradefed

Generally tests that use one of the following runners (not exhaustive):

- com.android.compatibility.common.tradefed.testtype.JarHostTest (java_test_host).
- com.android.tradefed.testtype.HostTest
- com.android.tradefed.testtype.HostGTest (cc_test_host)
- com.android.tradefed.testtype.mobly.MoblyBinaryHostTest
- com.android.tradefed.testtype.python.PythonBinaryHostTest

These are tests built and executed on the host, but may rely on Tradefed
plugins to install data onto the device during the test, like
PushFilePreparer or SuiteApkInstaller.
""",
)

def tradefed_test_suite(
        name,
        test_dep,
        test_config,
        template_test_config,
        template_configs,
        template_install_base,
        tags,
        visibility,
        dynamic_config = None,
        test_language = "",
        suffix = "",
        deviceless_test_config = None,
        device_driven_test_config = None,
        host_driven_device_test_config = None,
        test_filter_generator = None,
        runs_on = [],
        data_bins = []):
    """The tradefed_test_suite macro groups all three test types under a single test_suite.o

    This enables users or tools to simply run 'b test //path/to:foo_test_suite' and bazel
    can automatically determine which of the device or deviceless variants to run, using
    target_compatible_with information from the test_dep target.


    Args:
      name: name of the test suite. This is the canonical name of the test, e.g. "hello_world_test".
      test_dep: label of the language-specific test dependency.
      test_config: label of a custom Tradefed XML config. if specified, skip auto generation with default configs.
      template_test_config: label of a custom template of Tradefed XML config. If specified, skip using default template.
      dynamic_config: label of a custom dynamic test config, by default it is the DynamicConfig.xml file.
      template_configs: additional lines to be added to the test config.
      template_install_base: the default install location on device for files.
      tags: additional tags for the top level test_suite target. This can be used for filtering tests.
      visibility: Bazel visibility declarations for this target.
      test_language: language used for the test dependency. One of [LANGUAGE_CC, LANGUAGE_JAVA, LANGUAGE_ANDROID].
      suffix: the suffix such as 32 or 64 of the test binary.
      deviceless_test_config: default Tradefed test config for the deviceless execution mode.
      device_driven_test_config: default Tradefed test config for the device driven execution mode.
      host_driven_device_test_config: default Tradefed test config for the host driven execution mode.
      test_filter_generator: label of a file containing a test filter that will be passed through to TradeFed.
      runs_on: platform variants that this test runs on. The allowed values are 'device', 'host_with_device' and 'host_without_device'.
      data_bins: executables that need to be installed alongside the test entry point to be used for the test itself.
    """

    # Validate names.
    if not test_dep.endswith(TEST_DEP_SUFFIX) or test_dep.removesuffix(TEST_DEP_SUFFIX) != name:
        fail("tradefed_test_suite.test_dep must be named %s%s, " % (name, TEST_DEP_SUFFIX) +
             "but got %s" % test_dep)

    if test_config and template_test_config:
        fail("'test_config' and 'template_test_config' should not be specified at same time.")

    # TODO(b/296312548): Make `runs_on` a required attribute.
    if runs_on:
        if [p for p in runs_on if p not in ["device", "host_with_device", "host_without_device"]]:
            fail("Invalid value in 'runs_on' attribute: has to be within 'device', 'host_with_device' and 'host_without_device'.")
        if "host_with_device" in runs_on and "device" in runs_on:
            fail("'host_with_device' and 'device' should not exist in the 'runs_on' attribute at same time.")
        if "host_with_device" in runs_on and "host_without_device" in runs_on:
            fail("'host_with_device' and 'host_without_device' should not exist in the 'runs_on' attribute at same time.")

        if "host_with_device" not in runs_on:
            host_driven_device_test_config = None
        if "host_without_device" not in runs_on:
            deviceless_test_config = None
        if "device" not in runs_on:
            device_driven_test_config = None

    # Shared attributes between all three test types. The only difference between them
    # are the default template_test_config at this level.
    common_tradefed_attrs = dict(
        [
            ("test", test_dep),
            # User-specified test config should take precedence over auto-generated ones.
            ("test_config", test_config),
            ("dynamic_config", dynamic_config),
            # Extra lines to go into the test config.
            ("template_configs", template_configs),
            # Path to install the test executable on device.
            ("template_install_base", template_install_base),
            # Test language helps to determine test_entry_point and fit test into config templates.
            ("test_language", test_language),
            ("test_filter_generator", test_filter_generator),
            ("suffix", suffix),
            # There shouldn't be package-external dependencies on the internal tests.
            ("visibility", ["//visibility:private"]),
            # Tradefed harness always builds for host.
            ("target_compatible_with", ["//build/bazel_common_rules/platforms/os:linux"]),
            # List of binary modules that should be installed alongside the test
            ("data_bins", data_bins),
        ],
    )

    tests = []

    # Tradefed deviceless test. Device NOT necessary. Tradefed will be invoked with --null-device.
    if deviceless_test_config:
        tradefed_deviceless_test_name = name + "__tf_deviceless_test"
        tests.append(tradefed_deviceless_test_name)
        tradefed_deviceless_test(
            name = tradefed_deviceless_test_name,
            template_test_config = None if test_config else template_test_config or deviceless_test_config,
            # The internal tests shouldn't run with ... or :all target patterns
            tags = tags + ["manual"],
            **common_tradefed_attrs
        )

    # | type             | deviceless / unit     | device-driven | host-driven device |
    # |------------------+-----------------------+---------------+--------------------|
    # | java_test_host   | YES                   |               | YES                |
    # | java_test        | YES if host_supported | YES           |                    |
    # | cc_test_host     | YES                   |               | YES                |
    # | cc_test          | YES if host_supported | YES           |                    |
    # | python_test_host | YES                   |               | YES                |
    # | python_test      | YES if host_supported | YES           |                    |
    # | android_test     |                       | YES           |                    |
    if device_driven_test_config and host_driven_device_test_config:
        fail("%s: device tests cannot be both device driven and host driven at the same time." % name)

    # Tradefed host or device driven device test. Device necessary.
    if device_driven_test_config or host_driven_device_test_config:
        tradefed_device_test_name = name + "__tf_device_test"
        tests.append(tradefed_device_test_name)

        # manual: The internal tests shouldn't run with ... or :all target patterns.
        #
        # exclusive: Device tests should run exclusively (one at a time), since they tend
        # to acquire resources and can often result in oddities when running in parellel.
        # Think Activity-based or port-based tests for example.
        #
        # TODO(b/302290752), https://github.com/bazelbuild/bazel/issues/17834:
        # exclusive-if-local does not work with RBE, and behaves exactly
        # like exclusive. Add exclusive-if-local once it's working.
        device_test_tags = tags + ["manual"]
        if not is_env_true(env.get("REMOTE_AVD")):
            device_test_tags.append("exclusive")
        if device_driven_test_config:
            tradefed_device_driven_test(
                name = tradefed_device_test_name,
                template_test_config = None if test_config else template_test_config or device_driven_test_config,
                tags = device_test_tags,
                **common_tradefed_attrs
            )
        else:
            tradefed_host_driven_device_test(
                name = tradefed_device_test_name,
                template_test_config = None if test_config else template_test_config or host_driven_device_test_config,
                tags = device_test_tags,
                **common_tradefed_attrs
            )

    native.test_suite(
        name = name,
        tests = tests,
        visibility = visibility,
        # Warning: be careful when specifying tags here, as tags have special
        # meaning in test suites for filtering tests.
        tags = tags,
        target_compatible_with = ["//build/bazel_common_rules/platforms/os:linux"],
    )

def _test_filter_generator_impl(ctx, name_suffix = "", progress_message = ""):
    output = ctx.actions.declare_file(ctx.attr.name + name_suffix)
    args = ["--out", output.path]

    for f in ctx.files.srcs:
        args.extend(["--class-file", f.path])

    for f in ctx.attr._test_reference[BuildSettingInfo].value:
        if not f:
            continue
        if ":" not in f:
            fail("Module name is required in the test reference %s. The format should follow: <module name>:<class name>#<method name>" % f)

        module_name, class_method_reference = f.split(":", 1)
        if module_name != ctx.attr.module_name:
            continue

        args.extend(["--class-method-reference", class_method_reference])

    ctx.actions.run(
        inputs = ctx.files.srcs,
        outputs = [output],
        arguments = args,
        executable = ctx.attr._executable.files_to_run.executable,
        tools = [ctx.attr._executable[DefaultInfo].files_to_run],
        progress_message = progress_message,
    )

    return [DefaultInfo(
        files = depset([output]),
    )]

def _cc_test_filter_generator_impl(ctx):
    return _test_filter_generator_impl(
        ctx,
        name_suffix = "_cc_test_filter",
        progress_message = "Generating the test filters for cc tests",
    )

cc_test_filter_generator = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_files = True,
            doc = "CC files containing the class and method that the test filter will match.",
        ),
        "module_name": attr.string(
            mandatory = True,
            doc = "Module name that the test filters are generated on by this target.",
        ),
        "_executable": attr.label(
            default = "//tools/asuite/atest:cc-test-filter-generator",
            doc = "Executable used to generate the cc test filter.",
        ),
        "_test_reference": attr.label(
            default = ":test_reference",
            doc = "Repeatable string flag used to accept the test reference.",
        ),
    },
    implementation = _cc_test_filter_generator_impl,
    doc = """A rule used to generate the cc test filter

An executable computes the cc test filter for a test module based on the given
cc files and the test reference, and writes the result into a output file that
is stored in the DefaultInfo provider.

Each test reference is a string in the test reference format of ATest:
    <module name>:<class name>#<method name>,<method name>
""",
)

def _java_test_filter_generator_impl(ctx):
    return _test_filter_generator_impl(
        ctx,
        name_suffix = "_java_test_filter",
        progress_message = "Generating the java test filters",
    )

java_test_filter_generator = rule(
    attrs = {
        "srcs": attr.label_list(
            allow_files = True,
            doc = "Source files containing the class and method that the test filter will match.",
        ),
        "module_name": attr.string(
            mandatory = True,
            doc = "Module name that the test filters are generated on by this target.",
        ),
        "_executable": attr.label(
            default = "//tools/asuite/atest:java-test-filter-generator",
            doc = "Executable used to generate the java test filter.",
        ),
        "_test_reference": attr.label(
            default = ":test_reference",
            doc = "Repeatable string flag used to accept the test reference.",
        ),
    },
    implementation = _java_test_filter_generator_impl,
    doc = """A rule used to generate the java test filter

An executable computes the java test filter for a test module based on the given
source files and the test reference, and writes the result into an output file
that is stored in the DefaultInfo provider.

Each test reference is a string in the test reference format of ATest:
    <module name>:<class name>#<method name>,<method name>
""",
)

def _abspath(relative):
    return "${TEST_SRCDIR}/${TEST_WORKSPACE}/" + relative

def _classpath(jars):
    return ":".join([_abspath(f.short_path) for f in depset(jars).to_list()])

def _is_remote_device_test(ctx):
    return hasattr(ctx.attr, "_exec_mode") and \
           ctx.attr._exec_mode[BuildSettingInfo].value == "remote" and \
           "no-remote" not in ctx.attr.tags
