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

"""Common Kotlin definitions."""

load("//:visibility.bzl", "RULES_DEFS_THAT_COMPILE_KOTLIN")
load("//kotlin/jvm/util:file_factory.bzl", "FileFactory")
load("//kotlin/jvm/util:srcjars.bzl", "kt_srcjars")
load("//toolchains/kotlin_jvm:androidlint_toolchains.bzl", "androidlint_toolchains")
load("//toolchains/kotlin_jvm:kt_jvm_toolchains.bzl", "kt_jvm_toolchains")
load("@bazel_skylib//lib:sets.bzl", "sets")
load("//bazel:stubs.bzl", "lint_actions")
load("//bazel:stubs.bzl", "jspecify_flags")
load("//bazel:stubs.bzl", "is_android_lint_exempt")
load("//bazel:stubs.bzl", "BASE_JVMOPTS")

visibility(RULES_DEFS_THAT_COMPILE_KOTLIN)

# TODO: Remove the _ALLOWED_*_RULES lists to determine which rules
# are accepted dependencies to Kotlin rules as the approach does not scale
# because it will require a cl + release for every new rule.

_EXT = struct(
    KT = ".kt",
    JAVA = ".java",
    JAR = ".jar",
    SRCJAR = ".srcjar",
)

_KT_FILE_TYPES = [_EXT.KT]

_KT_JVM_FILE_TYPES = [
    _EXT.JAVA,
    _EXT.KT,
    _EXT.SRCJAR,
]

_JAR_FILE_TYPE = [_EXT.JAR]

_SRCJAR_FILE_TYPES = [_EXT.JAR, _EXT.SRCJAR]

_RULE_FAMILY = struct(
    UNKNOWN = 0,
    JVM_LIBRARY = 1,
    ANDROID_LIBRARY = 2,
)

def _is_dir(file, basename):
    return file.is_directory and file.basename == basename

def _is_file(file, extension):
    return (not file.is_directory) and file.path.endswith(extension)

def _is_kt_src(src):
    """Decides if `src` Kotlin code.

    Either:
      -  a Kotlin source file
      -  a tree-artifact expected to contain only Kotlin source files
    """

    return _is_file(src, _EXT.KT) or _is_dir(src, "kotlin")

# Compute module name based on target (b/139403883), similar to Swift
def _derive_module_name(ctx):
    label = _get_original_kt_target_label(ctx)
    package_part = label.package.replace("/", ".")  # .package has no leading '//'
    name_part = label.name
    if package_part:
        return package_part + "_" + name_part
    return name_part

def _get_common_and_user_kotlinc_args(ctx, toolchain, extra_kotlinc_args):
    return toolchain.kotlinc_cli_flags + [
        # Set module name so module-level metadata is preserved when merging Jars (b/139403883)
        "-module-name",
        _derive_module_name(ctx),
    ] + jspecify_flags(ctx) + extra_kotlinc_args

def _kt_plugins_map(
        android_lint_rulesets = [],
        java_plugin_datas = depset(),
        java_plugin_infos = [],
        kt_codegen_plugin_infos = depset(),
        kt_compiler_plugin_infos = []):
    """A struct containing all the plugin types understood by rules_kotlin.

    Args:
        android_lint_rulesets: (list[lint_actions.AndroidLintRulesInfo]) Android Lint checkers.
            Each JAR is self-contained and should be loaded in an isolated classloader.
        java_plugin_datas: (depset[JavaPluginData]) for KtCodegenProcessing.
        java_plugin_infos: (list[JavaPluginInfo])
        kt_codegen_plugin_infos: (depset[KtCodegenPluginInfo]) for KtCodegenProcessing.
        kt_compiler_plugin_infos: (list[KtCompilerPluginInfo])
    """
    return struct(
        android_lint_rulesets = android_lint_rulesets,
        java_plugin_datas = java_plugin_datas,
        java_plugin_infos = java_plugin_infos,
        kt_codegen_plugin_infos = kt_codegen_plugin_infos,
        kt_compiler_plugin_infos = kt_compiler_plugin_infos,
    )

def _run_kotlinc(
        ctx,
        file_factory,
        kt_srcs = [],
        common_srcs = [],
        java_srcs_and_dirs = [],
        kotlincopts = [],
        compile_jdeps = depset(),
        toolchain = None,
        classpath = [],
        directdep_jars = depset(),
        plugins = _kt_plugins_map(),
        friend_jars = depset(),
        enforce_strict_deps = False,
        enforce_complete_jdeps = False,
        mnemonic = None,
        message_prefix = ""):
    direct_inputs = []
    transitive_inputs = []
    outputs = []

    # Args to kotlinc.
    #
    # These go at the end of the commandline. They should be passed through all wrapper
    # layers without post-processing, except to unpack param files.
    kotlinc_args = ctx.actions.args()
    kotlinc_args.use_param_file("@%s", use_always = True)  # Use params file to handle long classpaths (b/76185759)
    kotlinc_args.set_param_file_format("multiline")  # kotlinc only supports double-quotes ("): https://youtrack.jetbrains.com/issue/KT-24472

    # Args to the kotlinc JVM
    #
    # These cannot use a param file because the file wouldn't be read until after the JVM launches.
    # Values will be prepended with --jvm_flag= for detection.
    jvm_args = []

    kotlinc_args.add_joined("-cp", classpath, join_with = ":")
    transitive_inputs.append(classpath)
    kotlinc_args.add_all(_get_common_and_user_kotlinc_args(ctx, toolchain, kotlincopts))

    kotlinc_args.add(toolchain.jvm_abi_gen_plugin, format = "-Xplugin=%s")
    direct_inputs.append(toolchain.jvm_abi_gen_plugin)
    kt_ijar = file_factory.declare_file("-ijar.jar")
    kotlinc_args.add("-P", kt_ijar, format = "plugin:org.jetbrains.kotlin.jvm.abi:outputDir=%s")
    outputs.append(kt_ijar)

    for p in plugins.kt_compiler_plugin_infos:
        kotlinc_args.add(p.jar, format = "-Xplugin=%s")
        direct_inputs.append(p.jar)
        kotlinc_args.add_all(p.args, before_each = "-P")

    # Common sources must also be specified as -Xcommon-sources= in addition to appearing in the
    # source list.
    if common_srcs:
        kotlinc_args.add("-Xmulti-platform=true")
        kotlinc_args.add_all(common_srcs, format_each = "-Xcommon-sources=%s")
        direct_inputs.extend(common_srcs)

    output = file_factory.declare_file(".jar")
    kotlinc_args.add("-d", output)
    outputs.insert(0, output)  # The param file name is derived from the 0th output
    kotlinc_args.add_all(kt_srcs)
    direct_inputs.extend(kt_srcs)
    kotlinc_args.add_all(common_srcs)
    direct_inputs.extend(common_srcs)

    if java_srcs_and_dirs:
        # This expands any directories into their contained files
        kotlinc_args.add_all(java_srcs_and_dirs)
        direct_inputs.extend(java_srcs_and_dirs)

    kotlinc_args.add_joined(friend_jars, format_joined = "-Xfriend-paths=%s", join_with = ",")
    transitive_inputs.append(friend_jars)

    # Do not change the "shape" or mnemonic of this action without consulting Kythe team
    # (kythe-eng@), to avoid breaking the Kotlin Kythe extractor which "shadows" this action.  In
    # particular, the extractor expects this to be a vanilla "spawn" (ctx.actions.run) so don't
    # change this to ctx.actions.run_shell or something else without considering Kythe implications
    # (b/112439843).
    ctx.actions.run(
        executable = toolchain.kotlin_compiler,
        arguments = ["--jvm_flag=" + x for x in jvm_args] + [kotlinc_args],
        inputs = depset(direct = direct_inputs, transitive = transitive_inputs),
        outputs = outputs,
        mnemonic = mnemonic,
        progress_message = message_prefix + str(_get_original_kt_target_label(ctx)),
        execution_requirements = {
            # Ensure comparable results across runs (cold builds, same machine)
            "no-cache": "1",
            "no-remote": "1",
        } if toolchain.is_profiling_enabled(ctx.label) else {
            "worker-key-mnemonic": "Kt2JavaCompile",
        },
        toolchain = toolchain.toolchain_type,
    )

    return struct(
        output_jar = output,
        compile_jar = kt_ijar,
    )

def _kt_compile(
        ctx,
        file_factory,
        kt_srcs = [],
        common_srcs = [],
        coverage_srcs = [],
        java_srcs_and_dirs = [],
        kt_hdrs = None,
        common_hdrs = None,
        kotlincopts = [],
        compile_jdeps = depset(),
        toolchain = None,
        classpath = [],
        directdep_jars = depset(),
        plugins = _kt_plugins_map(),
        friend_jars = depset(),
        enforce_strict_deps = False,
        enforce_complete_jdeps = False):
    # TODO: don't run jvm-abi-gen plugin here if we have headers
    kotlinc_full_result = _run_kotlinc(
        ctx,
        kt_srcs = kt_srcs,
        common_srcs = common_srcs,
        java_srcs_and_dirs = java_srcs_and_dirs,
        file_factory = file_factory,
        kotlincopts = kotlincopts,
        compile_jdeps = compile_jdeps,
        toolchain = toolchain,
        classpath = classpath,
        directdep_jars = directdep_jars,
        plugins = plugins,
        friend_jars = friend_jars,
        enforce_strict_deps = enforce_strict_deps,
        enforce_complete_jdeps = enforce_complete_jdeps,
        mnemonic = "Kt2JavaCompile",
        message_prefix = "Compiling Kotlin For Java Runtime: ",
    )

    srcjar = kt_srcjars.zip(
        ctx,
        toolchain,
        file_factory.declare_file("-kt-src.jar"),
        srcs = kt_srcs,
        common_srcs = common_srcs,
    )

    output_jar = kotlinc_full_result.output_jar
    if ctx.coverage_instrumented():
        output_jar = _offline_instrument_jar(
            ctx,
            toolchain,
            output_jar,
            kt_srcs + common_srcs + coverage_srcs,
        )

    # Use un-instrumented Jar at compile-time to avoid double-instrumenting inline functions
    # (see b/110763361 for the comparable Gradle issue)
    compile_jar = kotlinc_full_result.compile_jar
    if toolchain.header_gen_tool:
        kotlinc_header_result = _run_kotlinc(
            ctx,
            kt_srcs = kt_hdrs,
            common_srcs = common_hdrs,
            java_srcs_and_dirs = java_srcs_and_dirs,
            file_factory = file_factory.derive("-abi"),
            kotlincopts = kotlincopts,
            compile_jdeps = compile_jdeps,
            toolchain = toolchain,
            classpath = classpath,
            directdep_jars = directdep_jars,
            plugins = plugins,
            friend_jars = friend_jars,
            enforce_strict_deps = enforce_strict_deps,
            enforce_complete_jdeps = enforce_complete_jdeps,
            mnemonic = "Kt2JavaHeaderCompile",
            message_prefix = "Computing Kotlin ABI interface Jar: ",
        )
        compile_jar = kotlinc_header_result.compile_jar

    result = dict(
        output_jar = output_jar,
        compile_jar = compile_jar,
        source_jar = srcjar,
    )
    return struct(java_info = JavaInfo(**result), **result)

def _derive_headers(
        ctx,
        toolchain,
        file_factory,
        srcs):
    if not srcs or not toolchain.header_gen_tool:
        return srcs

    output_dir = file_factory.declare_directory("-headers")
    args = ctx.actions.args()
    args.add(output_dir.path, format = "-output_dir=%s")
    args.add_joined(srcs, format_joined = "-sources=%s", join_with = ",")
    ctx.actions.run(
        executable = toolchain.header_gen_tool,
        arguments = [args],
        inputs = srcs,
        outputs = [output_dir],
        mnemonic = "KtDeriveHeaders",
        progress_message = "Deriving %s: %s" % (output_dir.basename, _get_original_kt_target_label(ctx)),
        toolchain = toolchain.toolchain_type,
    )
    return [output_dir]

def _get_original_kt_target_label(ctx):
    label = ctx.label
    if label.name.find("_DO_NOT_DEPEND") > 0:
        # Remove rule suffix added by android_library(
        label = label.relative(":%s" % label.name[0:label.name.find("_DO_NOT_DEPEND")])
    elif hasattr(ctx.attr, "_kt_codegen_plugin_build_tool") and label.name.endswith("_processed_srcs"):
        # Remove rule suffix added by kt_codegen_filegroup. b/259984258
        label = label.relative(":{}".format(label.name.removesuffix("_processed_srcs")))
    return label

def _run_import_deps_checker(
        ctx,
        jars_to_check = [],
        merged_deps = None,
        enforce_strict_deps = True,
        jdeps_output = None,
        deps_checker = None,
        java_toolchain = None):
    full_classpath = _create_classpath(java_toolchain, [merged_deps])
    label = _get_original_kt_target_label(ctx)

    args = ctx.actions.args()
    args.add("--jdeps_output", jdeps_output)
    args.add_all(jars_to_check, before_each = "--input")
    args.add_all(java_toolchain.bootclasspath, before_each = "--bootclasspath_entry")
    args.add_all(full_classpath, before_each = "--classpath_entry")
    if enforce_strict_deps:
        args.add_all(merged_deps.compile_jars, before_each = "--directdep")
    args.add("error" if enforce_strict_deps else "silence", format = "--checking_mode=%s")
    args.add("--nocheck_missing_members")  # compiler was happy so no need
    args.add("--rule_label", label)

    ctx.actions.run(
        executable = deps_checker,
        arguments = [args],
        inputs = depset(jars_to_check, transitive = [full_classpath]),
        outputs = [jdeps_output],
        mnemonic = "KtCheckStrictDeps" if enforce_strict_deps else "KtJdeps",
        progress_message = "%s deps for %s" % (
            "Checking strict" if enforce_strict_deps else "Computing",
            label,
        ),
    )

def _offline_instrument_jar(ctx, toolchain, jar, srcs = []):
    if not _is_file(jar, _EXT.JAR):
        fail("Expect JAR input but got %s" % jar)
    file_factory = FileFactory(ctx, jar)

    paths_for_coverage_file = file_factory.declare_file("-kt-paths-for-coverage.txt")
    paths = ctx.actions.args()
    paths.set_param_file_format("multiline")  # don't shell-quote, just list file names
    paths.add_all([src for src in srcs if src.is_source])
    ctx.actions.write(paths_for_coverage_file, paths)

    output = file_factory.declare_file("-instrumented.jar")
    args = ctx.actions.args()
    args.add(jar)
    args.add(output)
    args.add(paths_for_coverage_file)
    ctx.actions.run(
        executable = toolchain.coverage_instrumenter,
        arguments = [args],
        inputs = [jar, paths_for_coverage_file],
        outputs = [output],
        mnemonic = "KtJaCoCoInstrument",
        progress_message = "Instrumenting Kotlin for coverage collection: %s" % _get_original_kt_target_label(ctx),
        toolchain = toolchain.toolchain_type,
    )

    return output

def _singlejar(
        ctx,
        inputs,
        output,
        singlejar,
        mnemonic = "KtMergeJar",
        content = "final Jar",
        preserve_compression = False,
        pseudo_inputs = []):
    label = _get_original_kt_target_label(ctx)
    args = ctx.actions.args()
    args.add("--normalize")
    args.add("--add_missing_directories")  # make output more similar to jar tool (b/114414678)
    args.add("--exclude_build_data")
    args.add("--no_duplicates")  # No Kt/Java classname collisions (b/216841985)
    args.add("--output")
    args.add(output)
    args.add("--sources")
    args.add_all(inputs)
    args.add("--deploy_manifest_lines")
    args.add("Target-Label: %s" % label)
    if preserve_compression:
        args.add("--dont_change_compression")

    ctx.actions.run(
        executable = singlejar,
        arguments = [args],
        inputs = inputs + pseudo_inputs,
        outputs = [output],
        mnemonic = mnemonic,
        progress_message = "Merging %s: %s" % (content, label),
        toolchain = "@bazel_tools//tools/jdk:toolchain_type",
    )

def _merge_jdeps(ctx, kt_jvm_toolchain, jdeps_files, file_factory):
    merged_jdeps_file = file_factory.declare_file("-merged.jdeps")

    args = ctx.actions.args()
    args.add("--kind=jdeps")
    args.add(merged_jdeps_file, format = "--output=%s")
    args.add(_get_original_kt_target_label(ctx), format = "--rule_label=%s")
    args.add_all(jdeps_files)

    ctx.actions.run(
        executable = kt_jvm_toolchain.jdeps_merger,
        inputs = jdeps_files,
        outputs = [merged_jdeps_file],
        arguments = [args],
        mnemonic = "KtMergeJdeps",
        progress_message = "Merging jdeps files %{output}",
        toolchain = kt_jvm_toolchain.toolchain_type,
    )

    return merged_jdeps_file

def _check_srcs_package(target_package, srcs, attr_name):
    """Makes sure the given srcs live in the given package."""

    # Analogous to RuleContext.checkSrcsSamePackage
    for src in srcs:
        if target_package != src.owner.package:
            fail(("Please do not depend on %s directly in %s.  Either move it to this package or " +
                  "depend on an appropriate rule in its package.") % (src.owner, attr_name))

def _split_srcs_by_language(srcs, common_srcs, java_syncer):
    srcs_set = sets.make(srcs)
    common_srcs_set = sets.make(common_srcs)

    overlapping_srcs_set = sets.intersection(srcs_set, common_srcs_set)
    if sets.length(overlapping_srcs_set) != 0:
        fail("Overlap between srcs and common_srcs: %s" % sets.to_list(overlapping_srcs_set))

    # Split sources, as java requires a separate compile step.
    kt_srcs = [s for s in srcs if _is_kt_src(s)]
    java_srcs = [s for s in srcs if _is_file(s, _EXT.JAVA)]
    java_syncer.add_dirs([s for s in srcs if _is_dir(s, "java")])
    java_syncer.add_srcjars([s for s in srcs if _is_file(s, _EXT.SRCJAR)])

    expected_srcs_set = sets.make(kt_srcs + java_srcs + java_syncer.dirs + java_syncer.srcjars)
    unexpected_srcs_set = sets.difference(srcs_set, expected_srcs_set)
    if sets.length(unexpected_srcs_set) != 0:
        fail("Unexpected srcs: %s" % sets.to_list(unexpected_srcs_set))

    return (kt_srcs, java_srcs)

def _merge_exported_plugins(exported_plugins_map):
    for field in ["java_plugin_datas", "kt_codegen_plugin_infos", "kt_compiler_plugin_infos"]:
        if getattr(exported_plugins_map, field):
            fail("exported_plugins doesn't support %s. These are propagated with aspects" % field)

    android_lint_ruleset_jars = []

    return exported_plugins_map.java_plugin_infos + [
        JavaPluginInfo(
            processor_class = None,
            runtime_deps = [
                # Assume this list is short
                JavaInfo(output_jar = jar, compile_jar = jar)
                for jar in android_lint_ruleset_jars
            ],
        ),
    ]

# TODO: Streamline API to generate less actions.
def _kt_jvm_library(
        ctx,
        kt_toolchain,
        srcs = [],
        common_srcs = [],
        coverage_srcs = [],
        java_android_lint_config = None,
        manifest = None,  # set for Android libs, otherwise None.
        merged_manifest = None,  # set for Android libs, otherwise None.
        resource_files = [],  # set for Android libs, otherwise empty.
        classpath_resources = [],  # set for kt_jvm_library, otherwise empty.
        output = None,
        output_srcjar = None,  # Will derive default filename if not set.
        deps = [],
        exports = [],  # passthrough for JavaInfo constructor
        runtime_deps = [],  # passthrough for JavaInfo constructor
        native_libraries = [],  # passthrough of CcInfo for JavaInfo constructor
        plugins = _kt_plugins_map(),
        exported_plugins = _kt_plugins_map(),
        javacopts = [],
        kotlincopts = [],
        compile_jdeps = depset(),
        disable_lint_checks = [],
        neverlink = False,
        testonly = False,  # used by Android Lint
        enforce_strict_deps = True,
        rule_family = _RULE_FAMILY.UNKNOWN,
        enforce_complete_jdeps = False,
        java_toolchain = None,
        friend_jars = depset(),
                annotation_processor_additional_outputs = [],
        annotation_processor_additional_inputs = []):
    if not java_toolchain:
        fail("Missing or invalid java_toolchain")
    if not kt_toolchain:
        fail("Missing or invalid kt_toolchain")

    file_factory = FileFactory(ctx, output)
    static_deps = list(deps)  # Defensive copy

    kt_codegen_processing_env = dict()
    codegen_plugin_output = None

    kt_codegen_processors = kt_codegen_processing_env.get("processors_for_kt_codegen_processing", depset()).to_list()
    codegen_tags = kt_codegen_processing_env.get("codegen_tags", [])
    generative_deps = kt_codegen_processing_env.get("codegen_output_java_infos", depset()).to_list()

    java_syncer = kt_srcjars.DirSrcjarSyncer(ctx, kt_toolchain, file_factory)
    kt_srcs, java_srcs = _split_srcs_by_language(srcs, common_srcs, java_syncer)

    is_android_library_without_kt_srcs = rule_family == _RULE_FAMILY.ANDROID_LIBRARY and not kt_srcs and not common_srcs
    is_android_library_without_kt_srcs_without_generative_deps = is_android_library_without_kt_srcs and not generative_deps

    # TODO: Remove this special case
    if kt_srcs and ("flogger" in [p.plugin_id for p in plugins.kt_compiler_plugin_infos]):
        static_deps.append(kt_toolchain.flogger_runtime)

    if not is_android_library_without_kt_srcs_without_generative_deps:
        static_deps.extend(kt_toolchain.kotlin_libs)

    # Skip srcs package check for android_library targets with no kotlin sources: b/239725424
    if not is_android_library_without_kt_srcs:
        if "check_srcs_package_against_kt_srcs_only" in codegen_tags:
            _check_srcs_package(ctx.label.package, kt_srcs, "srcs")
        else:
            _check_srcs_package(ctx.label.package, srcs, "srcs")

        _check_srcs_package(ctx.label.package, common_srcs, "common_srcs")
        _check_srcs_package(ctx.label.package, coverage_srcs, "coverage_srcs")

    # Includes generative deps from codegen.
    extended_deps = static_deps + generative_deps
    full_classpath = _create_classpath(java_toolchain, extended_deps)
    exported_plugins = _merge_exported_plugins(exported_plugins)

    # Collect all plugin data, including processors to run and all plugin classpaths,
    # whether they have processors or not (b/120995492).
    # This may include go/errorprone plugin classpaths that kapt will ignore.
    java_plugin_datas = kt_codegen_processing_env.get("java_plugin_data_set", plugins.java_plugin_datas).to_list()
    processors_for_java_srcs = kt_codegen_processing_env.get("processors_for_java_srcs", depset(transitive = [p.processor_classes for p in java_plugin_datas])).to_list()
    java_plugin_classpaths_for_java_srcs = depset(transitive = [p.processor_jars for p in java_plugin_datas])
    out_jars = kt_codegen_processing_env.get("codegen_runtime_output_jars", [])
    out_srcjars = kt_codegen_processing_env.get("codegen_source_jars", [])
    out_compilejars = kt_codegen_processing_env.get("codegen_compile_jars", [])

    kt_hdrs = _derive_headers(
        ctx,
        toolchain = kt_toolchain,
        file_factory = file_factory.derive("-kt"),
        # TODO: prohibit overlap of srcs and common_srcs
        srcs = kt_srcs,
    )
    common_hdrs = _derive_headers(
        ctx,
        toolchain = kt_toolchain,
        file_factory = file_factory.derive("-common"),
        srcs = common_srcs,
    )

    kotlinc_result = None
    if kt_srcs or common_srcs:
        kotlinc_result = _kt_compile(
            ctx,
            kt_srcs = kt_srcs,
            common_srcs = common_srcs,
            coverage_srcs = coverage_srcs,
            java_srcs_and_dirs = java_srcs + java_syncer.dirs,
            kt_hdrs = kt_hdrs,
            common_hdrs = common_hdrs,
            file_factory = file_factory.derive("-kt"),
            kotlincopts = kotlincopts,
            compile_jdeps = compile_jdeps,
            toolchain = kt_toolchain,
            classpath = full_classpath,
            plugins = plugins,
            friend_jars = friend_jars,
            enforce_strict_deps = enforce_strict_deps,
            enforce_complete_jdeps = enforce_complete_jdeps,
        )
        out_compilejars.append(kotlinc_result.compile_jar)
        out_srcjars.append(kotlinc_result.source_jar)
        out_jars.append(kotlinc_result.output_jar)

    classpath_resources_dirs, classpath_resources_non_dirs = _partition(
        classpath_resources,
        filter = lambda res: res.is_directory,
    )
    if classpath_resources_dirs:
        out_jars.append(
            kt_srcjars.zip_resources(
                ctx,
                kt_toolchain,
                file_factory.declare_file("-dir-res.jar"),
                classpath_resources_dirs,
            ),
        )

    javac_java_info = None
    java_native_headers_jar = None

    if java_srcs or java_syncer.srcjars or classpath_resources:
        javac_deps = list(extended_deps)  # Defensive copy
        if kotlinc_result:
            javac_deps.append(kotlinc_result.java_info)
            if ctx.coverage_instrumented():
                # Including the coverage runtime improves jdeps computation (b/130747644), but it
                # could be runtime-only if we computed compile-time jdeps using the compile JAR
                # (which doesn't contain instrumentation). See b/117897097.
                javac_deps.append(kt_toolchain.coverage_runtime)

        javac_out = output if is_android_library_without_kt_srcs_without_generative_deps else file_factory.declare_file("-libjvm-java.jar")

        annotation_plugins = kt_codegen_processing_env.get("java_common_annotation_plugins", list(plugins.java_plugin_infos))
        enable_annotation_processing = kt_codegen_processing_env.get("enable_java_common_annotation_processing", True)

        javac_java_info = java_common.compile(
            ctx,
            source_files = java_srcs,
            source_jars = java_syncer.srcjars,
            resources = classpath_resources_non_dirs,
            # For targets that are not android_library with java-only srcs, exports will be passed
            # to the final constructed JavaInfo.
            exports = exports if is_android_library_without_kt_srcs_without_generative_deps else [],
            output = javac_out,
            exported_plugins = exported_plugins,
            deps = javac_deps,
            # Include default_javac_flags, which reflect Blaze's --javacopt flag, so they win over
            # all sources of default flags (for Ellipsis builds, see b/125452475).
            # TODO: remove default_javac_flags here once java_common.compile is fixed.
            javac_opts = ctx.fragments.java.default_javac_flags + javacopts,
            plugins = annotation_plugins,
            strict_deps = "DEFAULT",
            java_toolchain = java_toolchain,
            neverlink = neverlink,
            enable_annotation_processing = enable_annotation_processing,
            annotation_processor_additional_outputs = annotation_processor_additional_outputs,
            annotation_processor_additional_inputs = annotation_processor_additional_inputs,
        )

        out_jars.append(javac_out)
        out_srcjars.extend(javac_java_info.source_jars)
        out_compilejars.extend(javac_java_info.compile_jars.to_list())  # unpack singleton depset
        java_native_headers_jar = javac_java_info.outputs.native_headers

    java_gensrcjar = None
    java_genjar = None

    if codegen_plugin_output:
        pass

    elif javac_java_info:
        java_gensrcjar = javac_java_info.annotation_processing.source_jar
        java_genjar = javac_java_info.annotation_processing.class_jar
        if java_gensrcjar:
            java_syncer.add_srcjars([java_gensrcjar])

    jdeps_output = None
    compile_jdeps_output = None
    manifest_proto = None

    # TODO: Move severity overrides to config file when possible again
    blocking_action_outs = []

    # TODO: Remove the is_android_library_without_kt_srcs condition once KtAndroidLint
    # uses the same lint checks with AndroidLint

    if (srcs or common_srcs or resource_files) and not is_android_lint_exempt(ctx):
        lint_flags = [
            "--java-language-level",  # b/159950410
            kt_toolchain.java_language_version,
            "--kotlin-language-level",
            kt_toolchain.kotlin_language_version,
            "--nowarn",  # Check for "errors", which includes custom checks that report errors.
            "--XallowBaselineSuppress",  # allow baseline exemptions of otherwise unsuppressable errors
            "--exitcode",  # fail on error
            "--fullpath",  # reduce file path clutter in reported issues
            "--text",
            "stdout",  # also log to stdout
        ]
        if disable_lint_checks and disable_lint_checks != [""]:
            lint_flags.append("--disable")
            lint_flags.append(",".join(disable_lint_checks))

        android_lint_out = lint_actions.run_lint_on_library(
            ctx,
            runner = kt_toolchain.android_lint_runner,
            output = file_factory.declare_file("_android_lint_output.xml"),
            srcs = kt_srcs + java_srcs + common_srcs,
            source_jars = java_syncer.srcjars,
            classpath = full_classpath,
            manifest = manifest,
            merged_manifest = merged_manifest,
            resource_files = resource_files,
            baseline_file = androidlint_toolchains.get_baseline(ctx),
            config = (None if (kt_srcs or common_srcs) else java_android_lint_config) or kt_toolchain.android_lint_config,
            android_lint_rules = plugins.android_lint_rulesets + [
                lint_actions.AndroidLintRulesetInfo(singlejars = java_plugin_classpaths_for_java_srcs),
            ],
            lint_flags = lint_flags,
            extra_input_depsets = [p.processor_data for p in java_plugin_datas],
            testonly = testonly,
            android_java8_libs = kt_toolchain.android_java8_apis_desugared,
            mnemonic = "AndroidLint" if is_android_library_without_kt_srcs else "KtAndroidLint",  # so LSA extractor can distinguish Kotlin (b/189442586)
        )
        blocking_action_outs.append(android_lint_out)

    # Directly return the JavaInfo from java.compile() for java-only android_library targets
    # to avoid creating a new JavaInfo. See b/239847857 for additional context.
    if javac_java_info and is_android_library_without_kt_srcs_without_generative_deps:
        return struct(
            java_info = javac_java_info,
            validations = blocking_action_outs,
        )

    if output_srcjar == None:
        output_srcjar = file_factory.declare_file("-src.jar")
    compile_jar = file_factory.declare_file("-compile.jar")
    single_jar = java_toolchain.single_jar
    _singlejar(ctx, out_srcjars, output_srcjar, single_jar, mnemonic = "KtMergeSrcjar", content = "srcjar", preserve_compression = True)

    # Don't block compile-time Jar on Android Lint and other validations (b/117991324).
    _singlejar(ctx, out_compilejars, compile_jar, single_jar, mnemonic = "KtMergeCompileJar", content = "compile-time Jar")

    # Disable validation for Guitar BUILD targets (b/144326858).
    # TODO Remove use of RUN_ANALYSIS_TIME_VALIDATION once Guitar disables validations
    use_validation = ctx.var.get("RUN_ANALYSIS_TIME_VALIDATION", "true")  # will be "0" if set by Guitar
    use_validation = ctx.var.get("kt_use_validations", use_validation)

    # Include marker file in runtime Jar so we can reliably identify 1P Kotlin code
    # TODO: consider only doing this for android_library(
    _singlejar(
        ctx,
        out_jars + ([kt_toolchain.build_marker] if kt_srcs and ctx.label.package.startswith("java/") else []),
        output,
        single_jar,
        preserve_compression = True,
        pseudo_inputs = ([] if use_validation == "true" else blocking_action_outs),
    )
    result_java_info = JavaInfo(
        output_jar = output,
        compile_jar = compile_jar,
        source_jar = output_srcjar,
        deps = static_deps,
        exports = exports,
        exported_plugins = exported_plugins,
        runtime_deps = runtime_deps,
        manifest_proto = manifest_proto,
        neverlink = neverlink,
        jdeps = jdeps_output,
        compile_jdeps = compile_jdeps_output,
        native_libraries = native_libraries,
        native_headers_jar = java_native_headers_jar,
        generated_source_jar = java_gensrcjar,
        generated_class_jar = java_genjar,
    )

    return struct(
        java_info = result_java_info,
        validations = (blocking_action_outs if use_validation == "true" else []),
    )

def _kt_jvm_import(
        ctx,
        kt_toolchain,
        jars = [],
        srcjar = None,
        deps = [],
        runtime_deps = [],
        neverlink = False,
                java_toolchain = None,
        deps_checker = None):
    if not java_toolchain:
        fail("Missing or invalid java_toolchain")
    if not jars:
        fail("Must import at least one JAR")

    _check_srcs_package(ctx.label.package, jars, "jars")
    if srcjar:
        _check_srcs_package(ctx.label.package, [srcjar], "srcjar")

    file_factory = FileFactory(ctx, jars[0])
    deps = java_common.merge(deps + kt_toolchain.kotlin_libs)

    # Check that any needed deps are declared unless neverlink, in which case Jars won't be used
    # at runtime so we skip the check, though we'll populate jdeps either way.
    jdeps_output = file_factory.declare_file(".jdeps")
    _run_import_deps_checker(
        ctx,
        jars_to_check = jars,
        merged_deps = deps,
        enforce_strict_deps = not neverlink,
        jdeps_output = jdeps_output,
        deps_checker = deps_checker,
        java_toolchain = java_toolchain,
    )

    java_info = java_common.merge([
        JavaInfo(
            output_jar = jar,
            compile_jar = java_common.run_ijar(
                actions = ctx.actions,
                jar = jar,
                target_label = _get_original_kt_target_label(ctx),
                java_toolchain = java_toolchain,
            ),
            source_jar = srcjar,
            deps = [deps],
            runtime_deps = runtime_deps,
            neverlink = neverlink,
            # TODO: Set compile-time jdeps to help reduce Javac classpaths downstream
            jdeps = jdeps_output,  # not clear this is useful but let's populate since we have it
        )
        for jar in jars
    ])

    # TODO Remove use of RUN_ANALYSIS_TIME_VALIDATION once Guitar disables validations
    use_validation = ctx.var.get("RUN_ANALYSIS_TIME_VALIDATION", "true")  # will be "0" if set by Guitar

    return struct(
        java_info = java_info,
        validations = [jdeps_output] if use_validation == "true" and not neverlink else [],
    )

def _validate_proguard_specs(
        ctx,
        proguard_specs,
        proguard_allowlister):
    validated_proguard_specs = []
    for proguard_spec in proguard_specs:
        validated_proguard_spec = ctx.actions.declare_file(
            "validated_proguard/%s/%s_valid" % (ctx.label.name, proguard_spec.path),
        )
        validated_proguard_specs.append(validated_proguard_spec)

        args = ctx.actions.args()
        args.add("--path", proguard_spec)
        args.add("--output", validated_proguard_spec)

        ctx.actions.run(
            executable = proguard_allowlister,
            toolchain = kt_jvm_toolchains.type,
            arguments = [args],
            inputs = [proguard_spec],
            outputs = [validated_proguard_spec],
            mnemonic = "ValidateProguard",
            progress_message = (
                "Validating proguard configuration %s" % proguard_spec
            ),
        )
    return validated_proguard_specs

def _collect_proguard_specs(
        ctx,
        proguard_specs,
        propagated_deps,
        proguard_allowlister):
    validated_proguard_specs = _validate_proguard_specs(
        ctx,
        proguard_specs,
        proguard_allowlister,
    )

    return depset(
        validated_proguard_specs,
        transitive = [p.specs for p in _collect_providers(ProguardSpecProvider, propagated_deps)],
        order = "preorder",
    )

def _collect_providers(provider, deps):
    """Collects the requested provider from the given list of deps."""
    return [dep[provider] for dep in deps if provider in dep]

def _create_classpath(java_toolchain, deps):
    # To not confuse strictdeps, order as boot > direct > transitive JARs (b/149107867).
    return depset(
        order = "preorder",
        transitive = (
            [java_toolchain.bootclasspath] +
            [dep.compile_jars for dep in deps] +
            [dep.transitive_compile_time_jars for dep in deps]
        ),
    )

def _partition(sequence, filter):
    pos, neg = [], []
    for element in sequence:
        if filter(element):
            pos.append(element)
        else:
            neg.append(element)
    return pos, neg

common = struct(
    JAR_FILE_TYPE = _JAR_FILE_TYPE,
    JVM_FLAGS = BASE_JVMOPTS,
    KT_FILE_TYPES = _KT_FILE_TYPES,
    KT_JVM_FILE_TYPES = _KT_JVM_FILE_TYPES,
    RULE_FAMILY = _RULE_FAMILY,
    SRCJAR_FILE_TYPES = _SRCJAR_FILE_TYPES,
    collect_proguard_specs = _collect_proguard_specs,
    collect_providers = _collect_providers,
    create_jar_from_tree_artifacts = kt_srcjars.zip_resources,
    get_common_and_user_kotlinc_args = _get_common_and_user_kotlinc_args,
    is_kt_src = _is_kt_src,
    kt_jvm_import = _kt_jvm_import,
    kt_jvm_library = _kt_jvm_library,
    kt_plugins_map = _kt_plugins_map,
    partition = _partition,
)
