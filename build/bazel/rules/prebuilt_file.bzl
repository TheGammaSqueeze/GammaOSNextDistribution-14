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

"""
Contains prebuilt_file rule that handles prebuilt artifacts installation.
"""

PrebuiltFileInfo = provider(
    "Info needed for prebuilt_file modules",
    fields = {
        "src": "Source file of this prebuilt",
        "dir": "Directory into which to install",
        "filename": "Optional name for the installed file",
        "installable": "Whether this is directly installable into one of the partitions",
    },
)
_handled_dirs = ["etc", "usr/share", "."]

def _prebuilt_file_rule_impl(ctx):
    src = ctx.file.src

    # Is this an acceptable directory, or a subdir under one?
    dir = ctx.attr.dir
    acceptable = False
    for d in _handled_dirs:
        if dir == d or dir.startswith(d + "/"):
            acceptable = True
            break
    if not acceptable:
        fail("dir for", ctx.label.name, "is `", dir, "`, but we only handle these:\n", _handled_dirs)

    if ctx.attr.filename_from_src and ctx.attr.filename != "":
        fail("filename is set. filename_from_src cannot be true")
    elif ctx.attr.filename != "":
        filename = ctx.attr.filename
    elif ctx.attr.filename_from_src:
        filename = src.basename
    else:
        filename = ctx.attr.name

    if "/" in filename:
        fail("filename cannot contain separator '/'")

    return [
        PrebuiltFileInfo(
            src = src,
            dir = dir,
            filename = filename,
            installable = ctx.attr.installable,
        ),
        DefaultInfo(
            files = depset([src]),
        ),
    ]

_prebuilt_file = rule(
    implementation = _prebuilt_file_rule_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            allow_single_file = True,
        ),
        "dir": attr.string(mandatory = True),
        "filename": attr.string(),
        "filename_from_src": attr.bool(default = True),
        "installable": attr.bool(default = True),
    },
)

def prebuilt_file(
        name,
        src,
        dir,
        filename = None,
        installable = True,
        filename_from_src = False,
        # TODO(b/207489266): Fully support;
        # data is currently dropped to prevent breakages from e.g. prebuilt_etc
        data = [],  # @unused
        **kwargs):
    "Bazel macro to correspond with the e.g. prebuilt_etc and prebuilt_usr_share Soong modules."

    _prebuilt_file(
        name = name,
        src = src,
        dir = dir,
        filename = filename,
        installable = installable,
        filename_from_src = filename_from_src,
        **kwargs
    )
