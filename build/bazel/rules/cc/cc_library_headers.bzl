# Copyright (C) 2021 The Android Open Source Project
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

"""cc_library_headers is a headers only cc library."""

load(":cc_constants.bzl", "constants")
load(":cc_library_common.bzl", "check_absolute_include_dirs_disabled", "create_ccinfo_for_includes")

def _cc_headers_impl(ctx):
    check_absolute_include_dirs_disabled(
        ctx.label.package,
        ctx.attr.export_absolute_includes,
    )

    return [
        create_ccinfo_for_includes(
            ctx,
            hdrs = ctx.files.hdrs,
            includes = ctx.attr.export_includes,
            absolute_includes = ctx.attr.export_absolute_includes,
            system_includes = ctx.attr.export_system_includes,
            deps = ctx.attr.deps,
        ),
        cc_common.CcSharedLibraryHintInfo(
            attributes = [],
        ),
    ]

cc_library_headers = rule(
    implementation = _cc_headers_impl,
    attrs = {
        "export_absolute_includes": attr.string_list(doc = "List of exec-root relative or absolute search paths for headers, usually passed with -I"),
        "export_includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -I"),
        "export_system_includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -isystem"),
        "deps": attr.label_list(doc = "Re-propagates the includes obtained from these dependencies.", providers = [CcInfo]),
        "hdrs": attr.label_list(doc = "Header files.", allow_files = constants.hdr_dot_exts),
        "min_sdk_version": attr.string(),
        "sdk_version": attr.string(),
    },
    fragments = ["cpp"],
    provides = [CcInfo, cc_common.CcSharedLibraryHintInfo],
    doc = "A library that contains c/c++ headers which are imported by other targets.",
)
