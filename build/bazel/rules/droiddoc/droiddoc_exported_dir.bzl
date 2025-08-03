# Copyright (C) 2023 The Android Open Source Project
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

"""Soong's droiddoc_exported_dir equivalent"""

load("@bazel_skylib//lib:paths.bzl", "paths")

DroiddocExportedDirInfo = provider(
    "Info needed to identify the root dir and files for droid doc",
    fields = {
        "dir": "Common root directory for files",
        "srcs": "Files",
    },
)

def _droiddoc_exported_dir_rule_impl(ctx):
    dir = paths.join(ctx.label.package, paths.normalize(ctx.attr.dir))
    dir = paths.normalize(dir)

    def validate(s):
        if s.owner.workspace_name != ctx.label.workspace_name:
            fail("File [{}] is under a different workspace [{}]".format(s.short_path, s.owner.workspace_name))
        if not s.short_path.startswith(dir + "/"):
            fail("File [{}] is not under [{}]".format(s.short_path, dir))

    for s in ctx.files.srcs:
        validate(s)

    srcs = ctx.files.srcs
    return [
        DroiddocExportedDirInfo(
            dir = dir,
            srcs = srcs,
        ),
    ]

_droiddoc_exported_dir = rule(
    implementation = _droiddoc_exported_dir_rule_impl,
    attrs = {
        "dir": attr.string(),
        "srcs": attr.label_list(allow_empty = False, allow_files = True, mandatory = True),
    },
)

def droiddoc_exported_dir(name, srcs, **kwargs):
    _droiddoc_exported_dir(
        name = name,
        srcs = srcs,
        **kwargs
    )
