# Copyright (C) 2021 The Android Open Source Project
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

"""Rules used to run device tests"""

_TEST_SRCDIR = "${TEST_SRCDIR}"
_BAZEL_WORK_DIR = "%s/${TEST_WORKSPACE}/" % _TEST_SRCDIR
_PY_TOOLCHAIN = "@bazel_tools//tools/python:toolchain_type"
_TOOLCHAINS = [_PY_TOOLCHAIN]

DeviceEnvironment = provider(
    "Represents the environment a test will run under. Concretely this is an " +
    "executable and any runfiles required to trigger execution in the " +
    "environment.",
    fields = {
        "runner": "depset of executable to to setup test environment and execute test.",
        "data": "runfiles of all needed artifacts in the executable.",
    },
)

def device_test_impl(ctx):
    runner_script = _BAZEL_WORK_DIR + ctx.attr.run_with[DeviceEnvironment].runner.to_list()[0].short_path
    test_script = _BAZEL_WORK_DIR + ctx.file.test.short_path
    script = ctx.actions.declare_file("device_test_%s.sh" % ctx.label.name)
    path_additions = []

    ctx.actions.expand_template(
        template = ctx.file._device_test_template,
        output = script,
        is_executable = True,
        substitutions = {
            "{runner}": runner_script,
            "{test_script}": test_script,
        },
    )

    test_runfiles = ctx.runfiles().merge(
        ctx.attr.test[DefaultInfo].default_runfiles,
    )
    device_runfiles = ctx.runfiles().merge(
        ctx.attr.run_with[DeviceEnvironment].data,
    )
    all_runfiles = test_runfiles.merge_all([device_runfiles])
    return [DefaultInfo(
        executable = script,
        runfiles = all_runfiles,
    )]

device_test = rule(
    attrs = {
        "run_with": attr.label(default = "//bazel/rules:target_device"),
        "test": attr.label(
            allow_single_file = True,
        ),
        "_device_test_template": attr.label(
            default = "//bazel/rules:device_test.sh.template",
            allow_single_file = True,
        ),
    },
    test = True,
    implementation = device_test_impl,
    doc = "Runs a test under a device environment",
)
