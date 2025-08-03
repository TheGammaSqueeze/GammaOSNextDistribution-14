# Copyright 2022 Google LLC. All rights reserved.
#
# Licensed under the Apache License, Version 2.0 (the License);
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

"""kt_srcjars"""

# go/keep-sorted start
load("//:visibility.bzl", "RULES_KOTLIN")
load(":run_deploy_jar.bzl", "kt_run_deploy_jar")
# go/keep-sorted end

visibility(RULES_KOTLIN)

def _zip(
        ctx,
        kt_jvm_toolchain,
        out_jar,
        srcs = [],
        common_srcs = [],
        ignore_not_allowed_files = False):
    """Creates a srcjar from a set of Kotlin and Java srcs

    Paths inside the srcjar are derived from the package name in the source file.
    """

    args = ctx.actions.args()
    args.add("zip")
    args.add(out_jar)
    args.add_joined("--kotlin_srcs", srcs, join_with = ",")
    args.add_joined("--common_srcs", common_srcs, join_with = ",")
    if ignore_not_allowed_files:
        args.add("-i")

    kt_run_deploy_jar(
        ctx = ctx,
        java_runtime = kt_jvm_toolchain.java_runtime,
        deploy_jar = kt_jvm_toolchain.source_jar_zipper,
        inputs = srcs + common_srcs,
        outputs = [out_jar],
        args = [args],
        mnemonic = "KtJar",
        progress_message = "Create Jar (kotlin/common.bzl): %{output}",
    )

    return out_jar

def _unzip(
        ctx,
        kt_jvm_toolchain,
        dir,
        input):
    args = ctx.actions.args()
    args.add("unzip", input)
    args.add(dir.path)

    kt_run_deploy_jar(
        ctx = ctx,
        java_runtime = kt_jvm_toolchain.java_runtime,
        deploy_jar = kt_jvm_toolchain.source_jar_zipper,
        inputs = [input],
        outputs = [dir],
        args = [args],
        mnemonic = "SrcJarUnzip",
    )

    return dir

def _zip_resources(ctx, kt_jvm_toolchain, output_jar, input_dirs):
    """Packs a sequence of tree artifacts into a single jar.

    Given the following file directory structure,
        /usr/home/a/x/1.txt
        /usr/home/b/y/1.txt
    with an input_dirs as [
        "/usr/home/a",
        "/usr/home/b",
    ],
    The tool produces a jar with in-archive structure of,
        x/1.txt
        y/1.txt

    The function fails on the duplicate jar entry case. e.g. if we pass an
    input_dirs as [
        "/usr/home/a/x",
        "/usr/home/b/y",
    ],
    then the blaze action would fail with an error message.
        "java.lang.IllegalStateException: 1.txt has the same path as 1.txt!
        If it is intended behavior rename one or both of them."

    Args:
        ctx: The build rule context.
        kt_jvm_toolchain: Toolchain containing the jar tool.
        output_jar: The jar to be produced by this action.
        input_dirs: A sequence of tree artifacts to be zipped.

    Returns:
        The generated output jar, i.e. output_jar
    """

    args = ctx.actions.args()
    args.add("zip_resources")
    args.add(output_jar)
    args.add_joined(
        "--input_dirs",
        input_dirs,
        join_with = ",",
        omit_if_empty = False,
        expand_directories = False,
    )

    kt_run_deploy_jar(
        ctx = ctx,
        java_runtime = kt_jvm_toolchain.java_runtime,
        deploy_jar = kt_jvm_toolchain.source_jar_zipper,
        inputs = input_dirs,
        outputs = [output_jar],
        args = [args],
        mnemonic = "KtJarActionFromTreeArtifacts",
        progress_message = "Create Jar %{output}",
    )

    return output_jar

def _DirSrcjarSyncer(
        ctx,
        kt_jvm_toolchain,
        file_factory):
    """Synchronizes the contents of a set of srcjar files and tree-artifacts"""

    _dirs = []
    _srcjars = []

    def add_dirs(dirs):
        if not dirs:
            return

        _dirs.extend(dirs)
        _srcjars.append(
            _zip_resources(
                ctx,
                kt_jvm_toolchain,
                file_factory.declare_file("%s-codegen.srcjar" % len(_srcjars)),
                dirs,
            ),
        )

    def add_srcjars(srcjars):
        if not srcjars:
            return

        for srcjar in srcjars:
            _dirs.append(
                _unzip(
                    ctx,
                    kt_jvm_toolchain,
                    file_factory.declare_directory("%s.expand" % len(_dirs)),
                    srcjar,
                ),
            )
        _srcjars.extend(srcjars)

    return struct(
        add_dirs = add_dirs,
        add_srcjars = add_srcjars,
        dirs = _dirs,
        srcjars = _srcjars,
    )

kt_srcjars = struct(
    zip = _zip,
    unzip = _unzip,
    zip_resources = _zip_resources,
    DirSrcjarSyncer = _DirSrcjarSyncer,
)
