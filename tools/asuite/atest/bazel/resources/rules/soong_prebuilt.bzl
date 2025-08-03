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

"""Rule used to import artifacts prebuilt by Soong into the Bazel workspace.

The rule returns a DefaultInfo provider with all artifacts and runtime dependencies,
and a SoongPrebuiltInfo provider with the original Soong module name, artifacts,
runtime dependencies and data dependencies.
"""

load("//bazel/rules:platform_transitions.bzl", "device_transition")
load("//bazel/rules:common_settings.bzl", "BuildSettingInfo")

SoongPrebuiltInfo = provider(
    doc = "Info about a prebuilt Soong build module",
    fields = {
        "module_name": "Name of the original Soong build module",
        # This field contains this target's outputs and all runtime dependency
        # outputs.
        "transitive_runtime_outputs": "Files required in the runtime environment",
        "transitive_test_files": "Files of test modules",
        "platform_flavor": "The platform flavor that this target will be built on",
    },
)

def _soong_prebuilt_impl(ctx):
    files = ctx.files.files

    # Ensure that soong_prebuilt targets always have at least one file to avoid
    # evaluation errors when running Bazel cquery on a clean tree to find
    # dependencies.
    #
    # This happens because soong_prebuilt dependency target globs don't match
    # any files when the workspace symlinks are broken and point to build
    # artifacts that still don't exist. This in turn causes errors in rules
    # that reference these targets via attributes with allow_single_file=True
    # and which expect a file to be present.
    #
    # Note that the below action is never really executed during cquery
    # evaluation but fails when run as part of a test execution to signal that
    # prebuilts were not correctly imported.
    if not files:
        placeholder_file = ctx.actions.declare_file(ctx.label.name + ".missing")

        progress_message = (
            "Attempting to import missing artifacts for Soong module '%s'; " +
            "please make sure that the module is built with Soong before " +
            "running Bazel"
        ) % ctx.attr.module_name

        # Note that we don't write the file for the action to always be
        # executed and display the warning message.
        ctx.actions.run_shell(
            outputs = [placeholder_file],
            command = "/bin/false",
            progress_message = progress_message,
        )
        files = [placeholder_file]

    runfiles = ctx.runfiles(files = files).merge_all([
        dep[DefaultInfo].default_runfiles
        for dep in ctx.attr.runtime_deps + ctx.attr.data + ctx.attr.device_data
    ])

    # We exclude the outputs of static dependencies from the runfiles since
    # they're already embedded in this target's output. Note that this is done
    # recursively such that only transitive runtime dependency outputs are
    # included. For example, in a chain A -> B -> C -> D where B and C are
    # statically linked, only A's and D's outputs would remain in the runfiles.
    runfiles = runfiles.merge_all([
        ctx.runfiles(
            files = _exclude_files(
                dep[DefaultInfo].default_runfiles.files,
                dep[DefaultInfo].files,
            ).to_list(),
        )
        for dep in ctx.attr.static_deps
    ])

    return [
        _make_soong_prebuilt_info(
            ctx.attr.module_name,
            ctx.attr._platform_flavor[BuildSettingInfo].value,
            files = files,
            runtime_deps = ctx.attr.runtime_deps,
            static_deps = ctx.attr.static_deps,
            data = ctx.attr.data,
            device_data = ctx.attr.device_data,
            suites = ctx.attr.suites,
        ),
        DefaultInfo(
            files = depset(files),
            runfiles = runfiles,
        ),
    ]

soong_prebuilt = rule(
    attrs = {
        "module_name": attr.string(),
        # Artifacts prebuilt by Soong.
        "files": attr.label_list(allow_files = True),
        # Targets that are needed by this target during runtime.
        "runtime_deps": attr.label_list(),
        # Note that while the outputs of static deps are not required for test
        # execution we include them since they have their own runtime
        # dependencies.
        "static_deps": attr.label_list(),
        "data": attr.label_list(),
        "device_data": attr.label_list(
            cfg = device_transition,
        ),
        "suites": attr.string_list(),
        "_platform_flavor": attr.label(default = "//bazel/rules:platform_flavor"),
        # This attribute is required to use Starlark transitions. It allows
        # allowlisting usage of this rule. For more information, see
        # https://docs.bazel.build/versions/master/skylark/config.html#user-defined-transitions
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
    },
    implementation = _soong_prebuilt_impl,
    doc = "A rule that imports artifacts prebuilt by Soong into the Bazel workspace",
)

def _soong_uninstalled_prebuilt_impl(ctx):
    runfiles = ctx.runfiles().merge_all([
        dep[DefaultInfo].default_runfiles
        for dep in ctx.attr.runtime_deps
    ])

    return [
        _make_soong_prebuilt_info(
            ctx.attr.module_name,
            ctx.attr._platform_flavor[BuildSettingInfo].value,
            runtime_deps = ctx.attr.runtime_deps,
        ),
        DefaultInfo(
            runfiles = runfiles,
        ),
    ]

soong_uninstalled_prebuilt = rule(
    attrs = {
        "module_name": attr.string(),
        "runtime_deps": attr.label_list(),
        "_platform_flavor": attr.label(default = "//bazel/rules:platform_flavor"),
    },
    implementation = _soong_uninstalled_prebuilt_impl,
    doc = "A rule for targets with no runtime outputs",
)

def _make_soong_prebuilt_info(
        module_name,
        platform_flavor,
        files = [],
        runtime_deps = [],
        static_deps = [],
        data = [],
        device_data = [],
        suites = []):
    """Build a SoongPrebuiltInfo based on the given information.

    Args:
        runtime_deps: List of runtime dependencies required by this target.
        static_deps: List of static dependencies required by this target.
        data: List of data required by this target.
        device_data: List of data on device variant required by this target.
        suites: List of test suites this target belongs to.

    Returns:
        An instance of SoongPrebuiltInfo.
    """
    transitive_runtime_outputs = [
        dep[SoongPrebuiltInfo].transitive_runtime_outputs
        for dep in runtime_deps
    ]

    # We exclude the outputs of static dependencies and data dependencies from
    # the transitive runtime outputs since static dependencies are already
    # embedded in this target's output and the data dependencies shouldn't be
    # present in the runtime paths. Note that this is done recursively such that
    # only transitive runtime dependency outputs are included. For example, in a
    # chain A -> B -> C -> D where B and C are statically linked or data
    # dependencies, only A's and D's outputs would remain in the transitive
    # runtime outputs.
    transitive_runtime_outputs.extend([
        _exclude_files(
            dep[SoongPrebuiltInfo].transitive_runtime_outputs,
            dep[DefaultInfo].files,
        )
        for dep in static_deps + data
    ])
    return SoongPrebuiltInfo(
        module_name = module_name,
        platform_flavor = platform_flavor,
        transitive_runtime_outputs = depset(files, transitive = transitive_runtime_outputs),
        transitive_test_files = depset(
            # Note that `suites` is never empty for test files. This because
            # test build modules that do not explicitly specify a `test_suites`
            # Soong attribute belong to `null-suite`.
            files if suites else [],
            transitive = [
                dep[SoongPrebuiltInfo].transitive_test_files
                for dep in data + device_data + runtime_deps
            ],
        ),
    )

def _exclude_files(all_files, files_to_exclude):
    files = []
    files_to_exclude = {f: None for f in files_to_exclude.to_list()}
    for f in all_files.to_list():
        if f not in files_to_exclude:
            files.append(f)
    return depset(files)
