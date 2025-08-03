# Copyright (C) 2022 The Android Open Source Project
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
load(":java_system_modules.bzl", "SystemInfo")

def _bootclasspath_impl(ctx):
    infos = [b[JavaInfo] for b in ctx.attr.bootclasspath]
    java_info = java_common.merge(infos)

    # TODO: b/304657641 - system modules propagate java_info, including compile jars, we could consider consolidating bootclasspaths from system modules for the toolchains
    bootclasspath_jars = java_info.compile_jars.to_list()

    return java_common.BootClassPathInfo(
        bootclasspath = bootclasspath_jars,
        system = ctx.attr.system[SystemInfo].system if ctx.attr.system else None,
        auxiliary = [jar for b in ctx.attr.auxiliary for jar in b[JavaInfo].compile_jars.to_list()],
    )

bootclasspath = rule(
    implementation = _bootclasspath_impl,
    attrs = {
        "bootclasspath": attr.label_list(
            providers = [JavaInfo],
            doc = "The list of libraries to use as javac's --bootclasspath argument.",
        ),
        "system": attr.label(
            providers = [SystemInfo],
            doc = "The java_system_modules target to use as javac's --system argument.",
        ),
        "auxiliary": attr.label_list(
            providers = [JavaInfo],
            doc = "The list of libraries to include first in javac's --classpath.",
        ),
    },
    provides = [java_common.BootClassPathInfo],
    doc = """Provides BootClassPathInfo to a Java toolchain.

the java_common.BootClassPathInfo provider is used by a Java toolchain to
set javac's --bootclasspath and --system arguments. It can also optionally add
to the classpath before anything else gets added to it. This rule generates this
provider from a list of JavaInfo-providing targets for --bootclasspath and
--classpath, and from a single SystemInfo-providing target for --system.
""",
)
