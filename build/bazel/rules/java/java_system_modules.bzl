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
load("@bazel_skylib//lib:paths.bzl", "paths")

SystemInfo = provider(fields = ["system", "java_info"])

def _gen_module_info_java(ctx, jars_to_module_info, jars, module_info):
    ctx.actions.run_shell(
        inputs = jars,
        outputs = [module_info],
        command = "{} java.base {} > {}".format(
            jars_to_module_info.path,
            " ".join([jar.path for jar in jars]),
            module_info.path,
        ),
        tools = [jars_to_module_info],
    )

def _gen_module_info_class(ctx, java_runtime, module_info, java_base_patch_jars, module_info_class):
    ctx.actions.run_shell(
        inputs = depset([module_info], transitive = [java_base_patch_jars]),
        outputs = [module_info_class],
        tools = java_runtime.files,
        command = "{} -d {} --system=none --patch-module=java.base={} {}".format(
            paths.join(java_runtime.java_home, "bin", "javac"),
            module_info_class.dirname,
            ":".join([jar.path for jar in java_base_patch_jars.to_list()]),
            module_info.path,
        ),
    )

def _gen_module_info_jar(ctx, soong_zip, module_info_class, module_info_jar):
    args = ctx.actions.args()
    args.add("-jar")
    args.add("--symlinks=false")
    args.add("-o", module_info_jar)
    args.add("-C", module_info_class.dirname)
    args.add("-f", module_info_class)
    ctx.actions.run(
        inputs = [module_info_class],
        outputs = [module_info_jar],
        arguments = [args],
        executable = soong_zip,
    )

def _gen_merged_module_jar(ctx, merge_zips, module_info_jar, jars, merged_module_jar):
    args = ctx.actions.args()
    args.add("-j", merged_module_jar)
    args.add_all(depset([module_info_jar], transitive = [jars]))
    ctx.actions.run(
        inputs = depset([module_info_jar], transitive = [jars]),
        outputs = [merged_module_jar],
        arguments = [args],
        executable = merge_zips,
    )

def _gen_jmod(ctx, java_runtime, merged_module_jar, jmod):
    ctx.actions.run_shell(
        inputs = [merged_module_jar],
        outputs = [jmod],
        tools = java_runtime.files,
        command = (
            "{} create --module-version $({} --version) " +
            "--target-platform android --class-path {} {}"
        ).format(
            paths.join(java_runtime.java_home, "bin", "jmod"),
            paths.join(java_runtime.java_home, "bin", "jlink"),
            merged_module_jar.path,
            jmod.path,
        ),
    )

def _gen_system(ctx, java_runtime, jmod, system):
    ctx.actions.run_shell(
        inputs = depset([jmod], transitive = [java_runtime.files]),
        outputs = [system],
        tools = java_runtime.files,
        command = (
            "rm -rf {} && " +
            "{} --module-path {} --add-modules java.base --output {} " +
            "--disable-plugin system-modules && " +
            "cp {} {}/lib/"
        ).format(
            system.path,
            paths.join(java_runtime.java_home, "bin", "jlink"),
            jmod.dirname,
            system.path,
            paths.join(java_runtime.java_home, "lib", "jrt-fs.jar"),
            system.path,
        ),
    )

def _java_system_modules_impl(ctx):
    java_info = java_common.merge([d[JavaInfo] for d in ctx.attr.deps])
    module_info = ctx.actions.declare_file("%s/src/module-info.java" % ctx.label.name)
    _gen_module_info_java(ctx, ctx.executable._jars_to_module_info, java_info.compile_jars.to_list(), module_info)

    java_runtime = ctx.attr._runtime[java_common.JavaRuntimeInfo]
    module_info_class = ctx.actions.declare_file("%s/class/module-info.class" % ctx.label.name)
    _gen_module_info_class(ctx, java_runtime, module_info, java_info.compile_jars, module_info_class)

    module_info_jar = ctx.actions.declare_file("%s/jar/classes.jar" % ctx.label.name)
    _gen_module_info_jar(ctx, ctx.executable._soong_zip, module_info_class, module_info_jar)

    merged_module_jar = ctx.actions.declare_file("%s/merged/module.jar" % ctx.label.name)
    _gen_merged_module_jar(
        ctx,
        ctx.executable._merge_zips,
        module_info_jar,
        java_info.full_compile_jars,
        merged_module_jar,
    )

    jmod = ctx.actions.declare_file("%s/jmod/java.base.jmod" % ctx.label.name)
    _gen_jmod(ctx, java_runtime, merged_module_jar, jmod)

    system = ctx.actions.declare_directory("%s/system" % ctx.label.name)
    _gen_system(ctx, java_runtime, jmod, system)

    return [
        SystemInfo(
            system = system,
            java_info = java_info,
        ),
        DefaultInfo(files = depset([system])),
    ]

java_system_modules = rule(
    implementation = _java_system_modules_impl,
    attrs = {
        "_jars_to_module_info": attr.label(
            allow_files = True,
            executable = True,
            cfg = "exec",
            default = "//build/soong/scripts:jars-to-module-info-java",
        ),
        "_soong_zip": attr.label(
            cfg = "exec",
            allow_single_file = True,
            doc = "The tool soong_zip",
            default = "//build/soong/zip/cmd:soong_zip",
            executable = True,
        ),
        "_merge_zips": attr.label(
            cfg = "exec",
            allow_single_file = True,
            doc = "The tool merge_zips.",
            default = "//build/soong/cmd/merge_zips",
            executable = True,
        ),
        "_runtime": attr.label(
            default = Label("@bazel_tools//tools/jdk:current_java_runtime"),
            cfg = "exec",
            providers = [java_common.JavaRuntimeInfo],
        ),
        "deps": attr.label_list(
            providers = [JavaInfo],
            doc = "Libraries to be converted into a system module directory structure.",
        ),
    },
    doc = """Generates a system module directory from Java libraries.

Starting from version 1.9, Java requires a subset of java.* classes to be
provided via system modules. This rule encapsulates the set of steps necessary
to convert a jar file into the directory structure of system modules.
""",
)
