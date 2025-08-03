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

"""A macro to handle build number stamping."""

def stamp_build_number(ctx, prefix = "", extension = ""):
    if len(ctx.files.src) != 1:
        fail("Expected only one input file for build number stamping")

    out_file = ctx.actions.declare_file(prefix + ctx.attr.name + extension)
    android_constraint = ctx.attr._android_constraint[platform_common.ConstraintValueInfo]

    # TODO(b/228461735): We need to dist the output for device target.
    if ctx.target_platform_has_constraint(android_constraint) or not ctx.attr.stamp_build_number:
        ctx.actions.symlink(
            output = out_file,
            target_file = ctx.files.src[0],
        )
        return out_file

    ctx.actions.run_shell(
        inputs = ctx.files.src + [ctx.version_file],
        outputs = [out_file],
        command = """
            build_number=$(cat {file} | grep "BUILD_NUMBER" | cut -f2 -d' ');
            {build_number_stamper} -i {input} -o {output} -s soong_build_number -v $build_number
        """.format(
            file = ctx.version_file.path,
            input = ctx.files.src[0].path,
            output = out_file.path,
            build_number_stamper = ctx.executable._build_number_stamper.path,
        ),
        tools = [ctx.executable._build_number_stamper],
        mnemonic = "StampBuildNumber",
    )

    return out_file

common_attrs = {
    "stamp_build_number": attr.bool(
        default = False,
        doc = "Whether to stamp the build number",
    ),
    "_build_number_stamper": attr.label(
        cfg = "exec",
        doc = "The build number stamp tool.",
        executable = True,
        default = "//build/soong/symbol_inject/cmd:symbol_inject",
        allow_single_file = True,
    ),
    "_android_constraint": attr.label(
        default = Label("//build/bazel_common_rules/platforms/os:android"),
    ),
}

def _versioned_binary_impl(ctx):
    common_providers = [
        ctx.attr.src[CcInfo],
        ctx.attr.src[InstrumentedFilesInfo],
        ctx.attr.src[DebugPackageInfo],
        ctx.attr.src[OutputGroupInfo],
    ]

    out_file = stamp_build_number(ctx)

    return [
        DefaultInfo(
            files = depset([out_file]),
            executable = out_file,
            runfiles = ctx.attr.src[DefaultInfo].default_runfiles,
        ),
    ] + common_providers

versioned_binary = rule(
    implementation = _versioned_binary_impl,
    attrs = dict(
        common_attrs,
        src = attr.label(mandatory = True, allow_single_file = True, providers = [CcInfo]),
    ),
)

def _versioned_shared_library_impl(ctx):
    out_file = stamp_build_number(ctx, "lib", ".so")

    return [
        DefaultInfo(files = depset([out_file])),
        ctx.attr.src[CcSharedLibraryInfo],
        ctx.attr.src[OutputGroupInfo],
    ]

versioned_shared_library = rule(
    implementation = _versioned_shared_library_impl,
    attrs = dict(
        common_attrs,
        src = attr.label(
            mandatory = True,
            providers = [CcSharedLibraryInfo],
        ),
    ),
)
