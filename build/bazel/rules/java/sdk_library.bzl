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

JavaSdkLibraryInfo = provider(
    "Checked in current.txt for Public, System, Module_lib and System_server",
    fields = [
        "public",
        "system",
        "test",
        "module_lib",
        "system_server",
    ],
)

def _java_sdk_library_impl(ctx):
    return [
        JavaSdkLibraryInfo(
            public = ctx.file.public,
            system = ctx.file.system,
            test = ctx.file.test,
            module_lib = ctx.file.module_lib,
            system_server = ctx.file.system_server,
        ),
    ]

java_sdk_library = rule(
    implementation = _java_sdk_library_impl,
    attrs = {
        "public": attr.label(
            allow_single_file = True,
            doc = "public api surface file",
        ),
        "system": attr.label(
            allow_single_file = True,
            doc = "system api surface file",
        ),
        "test": attr.label(
            allow_single_file = True,
            doc = "test api surface file",
        ),
        "module_lib": attr.label(
            allow_single_file = True,
            doc = "module_lib api surface file",
        ),
        "system_server": attr.label(
            allow_single_file = True,
            doc = "system_server api surface file",
        ),
    },
)
