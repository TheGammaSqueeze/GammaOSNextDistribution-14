# Copyright (C) 2023 The Android Open Source Project
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

"""
This rule runs a test during the build. It's intended for use in the bazel sandwich.
It should mimic bazel's TestRunner action (like run the tests with
@bazel_tools//tools/test:test_setup), but currently it just does the bare minimum to get skylib's
diff_test to work.
"""

def _run_test_in_build_impl(ctx):
    out = ctx.actions.declare_file(ctx.attr.name + "_out.xml")
    ctx.actions.run_shell(
        outputs = [out],
        command = "{test} && touch {stampfile}".format(
            test = ctx.attr.test[DefaultInfo].files_to_run.executable.path,
            stampfile = out.path,
        ),
        inputs = ctx.attr.test[DefaultInfo].default_runfiles.files,
        env = {
            "TEST_WORKSPACE": ".",
            "TEST_SRCDIR": ctx.attr.test[DefaultInfo].default_runfiles.files.to_list()[0].root.path,
        },
    )

    return DefaultInfo(files = depset([out]))

_run_test_in_build = rule(
    implementation = _run_test_in_build_impl,
    attrs = {
        # TODO: Currently can't use executable = true / config = exec because we may want to build
        # things for device, and then diff them. We need to add a transition from the diff_test back
        # to device
        "test": attr.label(),
    },
)

def run_test_in_build(**kwargs):
    _run_test_in_build(testonly = True, **kwargs)
