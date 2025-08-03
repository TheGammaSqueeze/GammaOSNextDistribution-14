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

# TODO(b/240466571): Introduce property owner
SyspropGenInfo = provider(fields = ["srcs"])

def _sysprop_library_impl(ctx):
    return [SyspropGenInfo(srcs = ctx.attr.srcs)]

# TODO(b/240466571): Add Java to the documentation once the rules/macros are created
# TODO(b/240463568): Implement API checks
sysprop_library = rule(
    implementation = _sysprop_library_impl,
    doc = """Defines a library of sysprop files which may be used across
    the platform from either c++ or Java code. a `sysprop_library` may be
    listed in the `dep` clause of `cc_sysprop_library_shared` or
    `cc_sysprop_library_static` targets. Java is not yet supported""",
    attrs = {
        "srcs": attr.label_list(
            allow_files = [".sysprop"],
            mandatory = True,
        ),
    },
    provides = [SyspropGenInfo],
)
