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

# Rules and macros to define a cc toolchain with a static libc.
# Used to bootstrap cc development using the bionic lib build by Soong.
# Rule: _libc_config
#   Provides information needed by CcToolchainConfigInfo to configure the cc_toolchain properly.
# Macro: static_libc
#   Creates the libc_config target and filegroups needed by cc_toolchain.
LibcConfigInfo = provider(fields = ["include_dirs", "system_libraries"])

def _libc_config_impl(ctx):
    include_dirs = ctx.attr.include_dirs
    system_libraries = [file.path for file in ctx.files.system_libraries]
    provider = LibcConfigInfo(
        include_dirs = include_dirs,
        system_libraries = system_libraries,
    )
    return [provider]

_libc_config = rule(
    implementation = _libc_config_impl,
    attrs = {
        "include_dirs": attr.string_list(default = []),
        "system_libraries": attr.label_list(default = [], allow_files = True),
    },
)

def static_libc(
        name,
        include_dirs = {},
        system_libraries = []):
    # Create the filegroups
    include_srcs = []
    include_globs = []
    for value in include_dirs.values():
        if "*" in value:
            # It must be a glob.
            include_globs.append(value)
        else:
            # Assume it's a label.
            include_srcs.append(value)
    native.filegroup(
        name = "%s_includes" % name,
        srcs = include_srcs + native.glob(include_globs),
    )
    native.filegroup(
        name = "%s_system_libraries" % name,
        srcs = system_libraries,
    )

    # Create the libc config.
    include_paths = [path for path in include_dirs.keys()]
    _libc_config(
        name = name,
        include_dirs = include_paths,
        system_libraries = system_libraries,
    )

    # Also create cc_library target for direct dependencies.
    native.cc_library(
        name = "%s_library" % name,
        hdrs = [
            ":%s_includes" % name,
        ],
        includes = include_paths,
        srcs = [
            ":%s_system_libraries" % name,
        ],
    )
