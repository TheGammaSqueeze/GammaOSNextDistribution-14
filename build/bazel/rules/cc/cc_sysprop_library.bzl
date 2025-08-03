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

load(
    "//build/bazel/rules/sysprop:sysprop_library.bzl",
    "SyspropGenInfo",
)
load(
    ":cc_library_common.bzl",
    "create_ccinfo_for_includes",
)
load(":cc_library_shared.bzl", "cc_library_shared")
load(":cc_library_static.bzl", "cc_library_static")

# TODO(b/240466571): Implement determination of exported includes
def _cc_gen_sysprop_impl(ctx):
    outputs = []
    output_headers = []
    all_srcs = []
    [
        all_srcs.extend(src.files.to_list())
        for src in ctx.attr.dep[SyspropGenInfo].srcs
    ]
    for src_file in all_srcs:
        output_subpath = src_file.short_path.replace(
            ctx.label.package + "/",
            "",
            1,
        )
        action_outputs = []
        args = ctx.actions.args()
        output_src_file = ctx.actions.declare_file(
            "sysprop/%s.cpp" % output_subpath,
        )
        action_outputs.append(output_src_file)

        output_header_file = ctx.actions.declare_file(
            "sysprop/include/%s.h" % output_subpath,
        )
        action_outputs.append(output_header_file)
        output_headers.append(output_header_file)

        # TODO(b/240466571): This will in some cases be exported with the
        #                    linked bug
        output_public_header_file = ctx.actions.declare_file(
            "sysprop/public/include/%s.h" % output_subpath,
        )
        action_outputs.append(output_public_header_file)

        args.add("--header-dir", output_header_file.dirname)
        args.add("--public-header-dir", output_public_header_file.dirname)
        args.add("--source-dir", output_src_file.dirname)
        args.add("--include-name", "%s.h" % output_subpath)
        args.add(src_file.path)
        ctx.actions.run(
            executable = ctx.executable._sysprop_cpp,
            arguments = [args],
            inputs = [src_file],
            outputs = action_outputs,
            mnemonic = "syspropcc",
            progress_message = "Generating sources from %s" % (
                src_file.short_path,
            ),
        )
        outputs.extend(action_outputs)
    return [
        DefaultInfo(files = depset(outputs)),
        create_ccinfo_for_includes(
            ctx = ctx,
            hdrs = output_headers,
            # TODO(b/240466571): This will be determined dynamically with the
            #                    linked bug
            includes = ["sysprop/include"],
        ),
    ]

# Visible For Testing
cc_gen_sysprop = rule(
    implementation = _cc_gen_sysprop_impl,
    doc = """compilation of sysprop sources into cpp sources and headers""",
    attrs = {
        "dep": attr.label(
            providers = [SyspropGenInfo],
            mandatory = True,
        ),
        "_sysprop_cpp": attr.label(
            default = "//system/tools/sysprop:sysprop_cpp",
            executable = True,
            cfg = "exec",
        ),
    },
    provides = [CcInfo],
)

def _cc_gen_sysprop_common(
        name,
        dep):
    sysprop_gen_name = name + "_sysprop_gen"
    cc_gen_sysprop(
        name = sysprop_gen_name,
        dep = dep,
        tags = ["manual"],
    )

    return sysprop_gen_name

sysprop_deps = select({
    "//build/bazel_common_rules/platforms/os:android": ["//system/libbase:libbase_headers"],
    "//conditions:default": [
        "//system/libbase:libbase_bp2build_cc_library_static",
        "//system/logging/liblog:liblog_bp2build_cc_library_static",
    ],
})

sysprop_dynamic_deps = select({
    "//build/bazel_common_rules/platforms/os:android": [
        "//system/logging/liblog",
    ],
    "//conditions:default": [],
})

def cc_sysprop_library_shared(
        name,
        dep,
        min_sdk_version = "",
        **kwargs):
    sysprop_gen_name = _cc_gen_sysprop_common(name, dep)

    cc_library_shared(
        name = name,
        srcs = [":" + sysprop_gen_name],
        min_sdk_version = min_sdk_version,
        deps = sysprop_deps + [sysprop_gen_name],
        dynamic_deps = sysprop_dynamic_deps,
        **kwargs
    )

def cc_sysprop_library_static(
        name,
        dep,
        min_sdk_version = "",
        **kwargs):
    sysprop_gen_name = _cc_gen_sysprop_common(name, dep)
    cc_library_static(
        name = name,
        srcs = [":" + sysprop_gen_name],
        min_sdk_version = min_sdk_version,
        deps = sysprop_deps + [sysprop_gen_name],
        dynamic_deps = sysprop_dynamic_deps,
        **kwargs
    )
