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

load("@bazel_skylib//lib:paths.bzl", "paths")
load("@bazel_skylib//rules:common_settings.bzl", "BuildSettingInfo")
load("@bazel_tools//tools/build_defs/cc:action_names.bzl", "ACTION_NAMES")
load("@bazel_tools//tools/cpp:toolchain_utils.bzl", "find_cpp_toolchain")
load("@soong_injection//cc_toolchain:config_constants.bzl", config_constants = "constants")
load("//build/bazel/rules:common.bzl", "get_dep_targets")
load(
    ":cc_library_common.bzl",
    "CPP_EXTENSIONS",
    "C_EXTENSIONS",
    "CcAndroidMkInfo",
    "check_absolute_include_dirs_disabled",
    "create_cc_androidmk_provider",
    "create_ccinfo_for_includes",
    "get_non_header_srcs",
    "get_sanitizer_lib_info",
    "is_external_directory",
    "parse_sdk_version",
    "system_dynamic_deps_defaults",
)
load(":clang_tidy.bzl", "ClangTidyInfo", "clang_tidy_for_dir", "generate_clang_tidy_actions")

# TODO: b/294868620 - Change this back to lto_deps_transition when completing
#                     the bug
load(":composed_transitions.bzl", "lto_and_sanitizer_static_transition")

# TODO: b/294868620 - Remove when completing the bug
load(
    ":sanitizer_enablement_transition.bzl",
    "drop_sanitizer_enablement_transition",
)
load(":stl.bzl", "stl_info_from_attr")

_ALLOWED_MANUAL_INTERFACE_PATHS = [
    "vendor/",
    "hardware/",
    # for testing
    "build/bazel/rules/cc",
]

CcStaticLibraryInfo = provider(fields = ["root_static_archive", "objects"])

def cc_library_static(
        name,
        shared_linking = False,
        deps = [],
        implementation_deps = [],
        dynamic_deps = [],
        implementation_dynamic_deps = [],
        whole_archive_deps = [],
        implementation_whole_archive_deps = [],
        system_dynamic_deps = None,
        runtime_deps = [],
        export_absolute_includes = [],
        export_includes = [],
        export_system_includes = [],
        local_includes = [],
        absolute_includes = [],
        hdrs = [],
        native_bridge_supported = False,  # TODO: not supported yet. @unused
        rtti = False,
        stl = "",
        cpp_std = "",
        c_std = "",
        # Flags for C and C++
        copts = [],
        # C++ attributes
        srcs = [],
        cppflags = [],
        # C attributes
        srcs_c = [],
        conlyflags = [],
        # asm attributes
        srcs_as = [],
        asflags = [],
        features = [],
        linkopts = [],
        alwayslink = None,
        target_compatible_with = [],
        # TODO(b/202299295): Handle data attribute.
        data = [],  # @unused
        sdk_version = "",  # @unused
        min_sdk_version = "",
        tags = [],
        tidy = None,
        tidy_checks = None,
        tidy_checks_as_errors = None,
        tidy_flags = None,
        tidy_disabled_srcs = None,
        tidy_timeout_srcs = None,
        tidy_gen_header_filter = None,
        native_coverage = True,
        additional_compiler_inputs = [],
        applicable_licenses = []):
    "Bazel macro to correspond with the cc_library_static Soong module."

    exports_name = "%s_exports" % name
    locals_name = "%s_locals" % name
    cpp_name = "%s_cpp" % name
    c_name = "%s_c" % name
    asm_name = "%s_asm" % name

    toolchain_features = []

    toolchain_features.append("pic")

    if is_external_directory(native.package_name()):
        toolchain_features += [
            "-non_external_compiler_flags",
            "external_compiler_flags",
        ]
    else:
        toolchain_features += [
            "non_external_compiler_flags",
            "-external_compiler_flags",
        ]

    for allowed_project in config_constants.WarningAllowedProjects:
        if native.package_name().startswith(allowed_project):
            toolchain_features.append("-warnings_as_errors")
            break

    if rtti:
        toolchain_features.append("rtti")
    if cpp_std:
        toolchain_features += [cpp_std, "-cpp_std_default"]
    if c_std:
        toolchain_features += [c_std, "-c_std_default"]

    for path in _ALLOWED_MANUAL_INTERFACE_PATHS:
        if native.package_name().startswith(path):
            toolchain_features.append("do_not_check_manual_binder_interfaces")
            break

    if min_sdk_version:
        toolchain_features += parse_sdk_version(min_sdk_version) + ["-sdk_version_default"]
    toolchain_features += features

    if not native_coverage:
        toolchain_features += ["-coverage"]  # buildifier: disable=list-append This could be a select, not a list

    if system_dynamic_deps == None:
        system_dynamic_deps = system_dynamic_deps_defaults

    _cc_includes(
        name = exports_name,
        includes = export_includes,
        absolute_includes = export_absolute_includes,
        system_includes = export_system_includes,
        # whole archive deps always re-export their includes, etc
        deps = deps + whole_archive_deps + dynamic_deps,
        target_compatible_with = target_compatible_with,
        applicable_licenses = applicable_licenses,
        tags = ["manual"],
    )

    stl_info = stl_info_from_attr(stl, False)
    linkopts = linkopts + stl_info.linkopts
    copts = copts + stl_info.cppflags

    _cc_includes(
        name = locals_name,
        includes = local_includes,
        absolute_includes = absolute_includes,
        deps = (
            implementation_deps +
            implementation_dynamic_deps +
            system_dynamic_deps +
            stl_info.deps +
            stl_info.static_deps +
            stl_info.shared_deps +
            implementation_whole_archive_deps
        ),
        target_compatible_with = target_compatible_with,
        applicable_licenses = applicable_licenses,
        tags = ["manual"],
    )

    # Silently drop these attributes for now:
    # - native_bridge_supported
    common_attrs = dict(
        [
            # TODO(b/199917423): This may be superfluous. Investigate and possibly remove.
            ("linkstatic", True),
            ("hdrs", hdrs),
            # Add dynamic_deps to implementation_deps, as the include paths from the
            # dynamic_deps are also needed.
            ("implementation_deps", [locals_name]),
            ("deps", [exports_name]),
            ("features", toolchain_features),
            ("toolchains", ["//build/bazel/product_config:product_variables_for_attributes"]),
            ("target_compatible_with", target_compatible_with),
            ("linkopts", linkopts),
            ("applicable_licenses", applicable_licenses),
        ],
    )

    # TODO(b/231574899): restructure this to handle other images
    copts += select({
        "//build/bazel/rules/apex:non_apex": [],
        "//conditions:default": [
            "-D__ANDROID_APEX__",
        ],
    })

    native.cc_library(
        name = cpp_name,
        srcs = srcs,
        copts = copts + cppflags,
        additional_compiler_inputs = additional_compiler_inputs,
        tags = ["manual"],
        alwayslink = True,
        **common_attrs
    )
    native.cc_library(
        name = c_name,
        srcs = srcs_c,
        copts = copts + conlyflags,
        additional_compiler_inputs = additional_compiler_inputs,
        tags = ["manual"],
        alwayslink = True,
        **common_attrs
    )
    native.cc_library(
        name = asm_name,
        srcs = srcs_as,
        copts = asflags,
        tags = ["manual"],
        alwayslink = True,
        **common_attrs
    )

    tidy_providing_deps = []
    if shared_linking:
        tidy_providing_deps = (
            deps +
            implementation_deps +
            whole_archive_deps +
            implementation_whole_archive_deps +
            runtime_deps +
            dynamic_deps +
            implementation_dynamic_deps
        )
    else:
        # We should only be running tidy actions for object files on which
        # we depend. For static libraries, only whole_archive_deps actually
        # create a linking dependency; other dependencies are header-only,
        # so we shouldn't try to run their tidy actions.
        tidy_providing_deps = whole_archive_deps + implementation_whole_archive_deps

    # Root target to handle combining of the providers of the language-specific targets.
    _cc_library_combiner(
        name = name,
        shared_linking = shared_linking,
        roots = [cpp_name, c_name, asm_name],
        deps = whole_archive_deps + implementation_whole_archive_deps,
        additional_sanitizer_deps = (
            deps +
            stl_info.deps +
            stl_info.static_deps +
            implementation_deps
        ),
        runtime_deps = runtime_deps,
        target_compatible_with = target_compatible_with,
        alwayslink = alwayslink,
        static_deps = deps + implementation_deps + whole_archive_deps + implementation_whole_archive_deps,
        androidmk_static_deps = deps + implementation_deps + stl_info.static_deps,
        androidmk_whole_archive_deps = whole_archive_deps + implementation_whole_archive_deps,
        androidmk_dynamic_deps = dynamic_deps + implementation_dynamic_deps + system_dynamic_deps + stl_info.shared_deps,
        exports = exports_name,
        applicable_licenses = applicable_licenses,
        tags = tags,
        features = toolchain_features,

        # clang-tidy attributes
        tidy = tidy,
        srcs_cpp = srcs,
        srcs_c = srcs_c,
        copts_cpp = copts + cppflags,
        copts_c = copts + conlyflags,
        hdrs = hdrs,
        includes = [locals_name, exports_name],
        tidy_flags = tidy_flags,
        tidy_checks = tidy_checks,
        tidy_checks_as_errors = tidy_checks_as_errors,
        tidy_disabled_srcs = tidy_disabled_srcs,
        tidy_timeout_srcs = tidy_timeout_srcs,
        tidy_gen_header_filter = tidy_gen_header_filter,
        tidy_providing_deps = tidy_providing_deps,
    )

def _generate_tidy_files(ctx):
    disabled_srcs = [] + ctx.files.tidy_disabled_srcs
    tidy_timeout = ctx.attr._tidy_timeout[BuildSettingInfo].value
    if tidy_timeout != "":
        disabled_srcs.extend(ctx.attr.tidy_timeout_srcs)

    if ctx.attr.tidy_gen_header_filter:
        if ctx.attr.tidy_flags:
            fail("tidy_flags cannot be set when also using tidy_gen_header_filter")
        tidy_flags = ["-header-filter=" + paths.join(ctx.genfiles_dir.path, ctx.label.package) + ".*"]
    else:
        tidy_flags = ctx.attr.tidy_flags

    cpp_srcs, cpp_hdrs = get_non_header_srcs(
        ctx.files.srcs_cpp,
        ctx.files.tidy_disabled_srcs,
        source_extensions = CPP_EXTENSIONS,
    )
    c_srcs, c_hdrs = get_non_header_srcs(
        ctx.files.srcs_cpp + ctx.files.srcs_c,
        ctx.files.tidy_disabled_srcs,
        source_extensions = C_EXTENSIONS,
    )
    hdrs = ctx.files.hdrs + cpp_hdrs + c_hdrs
    cpp_tidy_outs = generate_clang_tidy_actions(
        ctx,
        ctx.attr.copts_cpp,
        ctx.attr.deps + ctx.attr.includes,
        cpp_srcs,
        hdrs,
        "c++",
        tidy_flags,
        ctx.attr.tidy_checks,
        ctx.attr.tidy_checks_as_errors,
        tidy_timeout,
    )
    c_tidy_outs = generate_clang_tidy_actions(
        ctx,
        ctx.attr.copts_c,
        ctx.attr.deps + ctx.attr.includes,
        c_srcs,
        hdrs,
        "c",
        tidy_flags,
        ctx.attr.tidy_checks,
        ctx.attr.tidy_checks_as_errors,
        tidy_timeout,
    )
    return cpp_tidy_outs + c_tidy_outs

def _generate_tidy_actions(ctx):
    transitive_tidy_files = []
    for attr, attr_targets in get_dep_targets(ctx.attr, predicate = lambda t: ClangTidyInfo in t).items():
        if attr == "tidy_providing_deps":
            for t in attr_targets:
                transitive_tidy_files.append(t[ClangTidyInfo].transitive_tidy_files)

    with_tidy = ctx.attr._with_tidy[BuildSettingInfo].value
    allow_local_tidy_true = ctx.attr._allow_local_tidy_true[BuildSettingInfo].value
    tidy_external_vendor = ctx.attr._tidy_external_vendor[BuildSettingInfo].value
    tidy_enabled = (with_tidy and ctx.attr.tidy != "never") or (allow_local_tidy_true and ctx.attr.tidy == "local")
    should_run_for_current_package = clang_tidy_for_dir(tidy_external_vendor, ctx.label.package)
    if tidy_enabled and should_run_for_current_package:
        direct_tidy_files = _generate_tidy_files(ctx)
    else:
        direct_tidy_files = None

    tidy_files = depset(
        direct = direct_tidy_files,
    )
    transitive_tidy_files = depset(
        direct = direct_tidy_files,
        transitive = transitive_tidy_files,
    )
    return [
        OutputGroupInfo(
            _validation = tidy_files,
        ),
        ClangTidyInfo(
            tidy_files = tidy_files,
            transitive_tidy_files = transitive_tidy_files,
        ),
    ]

def _archive_with_prebuilt_libs(ctx, prebuilt_deps, linking_outputs, cc_toolchain):
    linking_output = linking_outputs.library_to_link.static_library
    if not prebuilt_deps:
        return linking_output

    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
        requested_features = ctx.features + ["archive_with_prebuilt_flags"],
        unsupported_features = ctx.disabled_features + ["linker_flags", "archiver_flags"],
    )

    output_file = ctx.actions.declare_file("lib" + ctx.label.name + ".a")

    archiver_path = cc_common.get_tool_for_action(
        feature_configuration = feature_configuration,
        action_name = ACTION_NAMES.cpp_link_static_library,
    )
    archiver_variables = cc_common.create_link_variables(
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
        output_file = output_file.path,
        is_using_linker = False,
    )
    command_line = cc_common.get_memory_inefficient_command_line(
        feature_configuration = feature_configuration,
        action_name = ACTION_NAMES.cpp_link_static_library,
        variables = archiver_variables,
    )
    args = ctx.actions.args()
    args.add_all(command_line)
    args.add(linking_output)
    args.add_all(prebuilt_deps)

    ctx.actions.run(
        executable = archiver_path,
        arguments = [args],
        inputs = depset(
            direct = [linking_output] + prebuilt_deps,
            transitive = [
                cc_toolchain.all_files,
            ],
        ),
        outputs = [output_file],
        mnemonic = "CppArchive",
    )

    return output_file

# Returns a CcInfo object which combines one or more CcInfo objects, except that all
# linker inputs owned by  owners in `old_owner_labels` are relinked and owned by the current target.
#
# This is useful in the "macro with proxy rule" pattern, as some rules upstream
# may expect they are depending directly on a target which generates linker inputs,
# as opposed to a proxy target which is a level of indirection to such a target.
def _cc_library_combiner_impl(ctx):
    sanitizer_lib_info = get_sanitizer_lib_info(ctx.attr.features, ctx.attr.deps + ctx.attr.additional_sanitizer_deps)

    old_owner_labels = []
    cc_infos = []

    # Soong links whole archive deps of a static lib differently, all the .o files
    # from the whole archive deps will be loaded into the static lib. This is
    # different from when linking from a shared lib, in which case the whole
    # archive deps will be linked separately.
    if not ctx.attr.shared_linking:
        for dep in ctx.attr.deps:
            old_owner_labels.append(dep.label)
            cc_info = dep[CcInfo]

            # do not propagate includes, hdrs, etc, already handled by roots
            cc_infos.append(CcInfo(linking_context = cc_info.linking_context))

    # we handle roots after deps to mimic Soong handling objects from whole archive deps prior to objects from the target itself
    for dep in ctx.attr.roots:
        old_owner_labels.append(dep.label)
        cc_infos.append(dep[CcInfo])

    direct_owner_labels = []
    if ctx.attr.shared_linking:
        for dep in ctx.attr.static_deps:
            if dep.label in old_owner_labels:
                continue
            direct_owner_labels.append(dep.label)
            cc_info = dep[CcInfo]

            # do not propagate includes, hdrs, etc, already handled by roots
            cc_infos.append(CcInfo(linking_context = cc_info.linking_context))
        if sanitizer_lib_info.propagate_ubsan_deps:
            direct_owner_labels.append(ctx.attr._ubsan_library.label)
            cc_infos.append(CcInfo(linking_context = ctx.attr._ubsan_library[CcInfo].linking_context))

    combined_info = cc_common.merge_cc_infos(cc_infos = cc_infos)

    objects_to_link = []
    extra_linker_inputs = []

    prebuilt_deps = []

    # This is not ideal, as it flattens a depset.
    for old_linker_input in combined_info.linking_context.linker_inputs.to_list():
        if old_linker_input.owner in old_owner_labels:
            for lib in old_linker_input.libraries:
                # These objects will be recombined into the root archive.
                objects_to_link.extend(lib.objects)

                # This is a prebuilt library, we have to handle it separately
                if not lib.objects and lib.static_library:
                    prebuilt_deps.append(lib.static_library)
        elif ctx.attr.shared_linking:
            if old_linker_input.owner in direct_owner_labels:
                extra_linker_inputs.append(old_linker_input)
        else:
            # Android macros don't handle transitive linker dependencies because
            # it's unsupported in legacy. We may want to change this going forward,
            # but for now it's good to validate that this invariant remains.
            fail("cc_static_library %s given transitive linker dependency from %s %s" % (ctx.label, old_linker_input.owner, old_owner_labels))

    cc_toolchain = find_cpp_toolchain(ctx)

    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
        requested_features = ctx.features + ["archiver_flags"],
        unsupported_features = ctx.disabled_features + ["linker_flags"],
    )

    out_name = ctx.label.name
    if prebuilt_deps:
        out_name += "_objs_only"
    linking_context, linking_outputs = cc_common.create_linking_context_from_compilation_outputs(
        actions = ctx.actions,
        name = out_name,
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
        alwayslink = ctx.attr.alwayslink,
        disallow_dynamic_library = True,
        compilation_outputs = cc_common.create_compilation_outputs(objects = depset(direct = objects_to_link)),
    )

    output_file = _archive_with_prebuilt_libs(ctx, prebuilt_deps, linking_outputs, cc_toolchain)
    linker_input = cc_common.create_linker_input(
        owner = ctx.label,
        libraries = depset(
            direct = [
                cc_common.create_library_to_link(
                    actions = ctx.actions,
                    feature_configuration = feature_configuration,
                    cc_toolchain = cc_toolchain,
                    static_library = output_file,
                    objects = objects_to_link,
                    alwayslink = ctx.attr.alwayslink,
                ),
            ],
        ),
    )
    linking_context = cc_common.create_linking_context(
        linker_inputs = depset(
            direct = [linker_input],
            transitive = [depset(direct = extra_linker_inputs)],
        ),
    )

    providers = [
        DefaultInfo(files = depset(direct = [output_file]), data_runfiles = ctx.runfiles(files = [output_file])),
        CcInfo(compilation_context = combined_info.compilation_context, linking_context = linking_context),
        CcStaticLibraryInfo(root_static_archive = output_file, objects = objects_to_link),
        sanitizer_lib_info,
        create_cc_androidmk_provider(
            static_deps = ctx.attr.androidmk_static_deps,
            whole_archive_deps = ctx.attr.androidmk_whole_archive_deps,
            dynamic_deps = ctx.attr.androidmk_dynamic_deps,
        ),
    ]
    providers.extend(_generate_tidy_actions(ctx))
    if ctx.attr.shared_linking:
        providers.append(
            # cc_shared_library only needs to traverse some attrs of the root library
            cc_common.CcSharedLibraryHintInfo(
                attributes = [
                    "roots",
                    "deps",
                    "static_deps",
                    "_ubsan_library",
                ],
            ),
        )
    else:
        providers.append(cc_common.CcSharedLibraryHintInfo(
            # cc_shared_library only needs to traverse some attrs of a static library
            attributes = [],
        ))

    return providers

# A rule which combines objects of oen or more cc_library targets into a single
# static linker input. This outputs a single archive file combining the objects
# of its direct deps, and propagates Cc providers describing that these objects
# should be linked for linking rules upstream.
# This rule is useful for maintaining the illusion that the target's deps are
# comprised by a single consistent rule:
#   - A single archive file is always output by this rule.
#   - A single linker input struct is always output by this rule, and it is 'owned'
#       by this rule.
_cc_library_combiner = rule(
    implementation = _cc_library_combiner_impl,
    cfg = drop_sanitizer_enablement_transition,
    attrs = {
        "roots": attr.label_list(
            providers = [CcInfo],
            cfg = lto_and_sanitizer_static_transition,
        ),
        "deps": attr.label_list(
            providers = [CcInfo],
            cfg = lto_and_sanitizer_static_transition,
        ),
        "shared_linking": attr.bool(
            doc = "Whether to link as needed for shared libraries, rather than as needed for a static libraries.",
            default = False,
        ),
        "additional_sanitizer_deps": attr.label_list(
            providers = [CcInfo],
            cfg = lto_and_sanitizer_static_transition,
            doc = "Deps used only to check for sanitizer enablement",
        ),
        "runtime_deps": attr.label_list(
            providers = [CcInfo],
            doc = "Deps that should be installed along with this target. Read by the apex cc aspect.",
        ),
        "static_deps": attr.label_list(
            providers = [CcInfo],
            doc = "All the static deps of the lib. This is used by" +
                  " abi_dump_aspect to travel along the static_deps edges" +
                  " to create abi dump files.",
        ),
        "_ubsan_library": attr.label(
            default = "//prebuilts/clang/host/linux-x86:libclang_rt.ubsan_minimal",
            doc = "The library target corresponding to the undefined " +
                  "behavior sanitizer library to be used",
        ),
        "androidmk_static_deps": attr.label_list(
            providers = [CcInfo],
            doc = "All the whole archive deps of the lib. This is used to propagate" +
                  " information to AndroidMk about LOCAL_STATIC_LIBRARIES.",
        ),
        "androidmk_whole_archive_deps": attr.label_list(
            providers = [CcInfo],
            doc = "All the whole archive deps of the lib. This is used to propagate" +
                  " information to AndroidMk about LOCAL_WHOLE_STATIC_LIBRARIES.",
        ),
        "androidmk_dynamic_deps": attr.label_list(
            providers = [CcInfo],
            doc = "All the dynamic deps of the lib. This is used to propagate" +
                  " information to AndroidMk about LOCAL_SHARED_LIBRARIES." +
                  " The attribute name is prefixed with androidmk to avoid" +
                  " collision with the dynamic_deps attribute used in APEX" +
                  " aspects' propagation.",
        ),
        "exports": attr.label(
            providers = [CcInfo],
            cfg = lto_and_sanitizer_static_transition,
        ),
        "_cc_toolchain": attr.label(
            default = Label("@local_config_cc//:toolchain"),
            providers = [cc_common.CcToolchainInfo],
            doc = "The exported includes used by abi_dump_aspect to retrieve" +
                  " and use as the inputs of abi dumper binary.",
        ),
        "alwayslink": attr.bool(
            doc = """At link time, whether these libraries should be wrapped in
            the --whole_archive block. This causes all libraries in the static
            archive to be unconditionally linked, regardless of whether the
            symbols in these object files are being searched by the linker.""",
            default = False,
        ),

        # Clang-tidy attributes
        "tidy": attr.string(values = ["", "local", "never"]),
        "srcs_cpp": attr.label_list(allow_files = True),
        "srcs_c": attr.label_list(allow_files = True),
        "copts_cpp": attr.string_list(),
        "copts_c": attr.string_list(),
        "hdrs": attr.label_list(allow_files = True),
        "includes": attr.label_list(cfg = lto_and_sanitizer_static_transition),
        "tidy_checks": attr.string_list(),
        "tidy_checks_as_errors": attr.string_list(),
        "tidy_flags": attr.string_list(),
        "tidy_disabled_srcs": attr.label_list(allow_files = True),
        "tidy_timeout_srcs": attr.label_list(allow_files = True),
        "tidy_gen_header_filter": attr.bool(),
        "tidy_providing_deps": attr.label_list(),
        "_clang_tidy_sh": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy.sh"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            doc = "The clang tidy shell wrapper",
        ),
        "_clang_tidy": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
            doc = "The clang tidy executable",
        ),
        "_clang_tidy_real": attr.label(
            default = Label("@//prebuilts/clang/host/linux-x86:clang-tidy.real"),
            allow_single_file = True,
            executable = True,
            cfg = "exec",
        ),
        "_with_tidy": attr.label(
            default = "//build/bazel/flags/cc/tidy:with_tidy",
        ),
        "_allow_local_tidy_true": attr.label(
            default = "//build/bazel/flags/cc/tidy:allow_local_tidy_true",
        ),
        "_with_tidy_flags": attr.label(
            default = "//build/bazel/flags/cc/tidy:with_tidy_flags",
        ),
        "_default_tidy_header_dirs": attr.label(
            default = "//build/bazel/flags/cc/tidy:default_tidy_header_dirs",
        ),
        "_tidy_timeout": attr.label(
            default = "//build/bazel/flags/cc/tidy:tidy_timeout",
        ),
        "_tidy_external_vendor": attr.label(
            default = "//build/bazel/flags/cc/tidy:tidy_external_vendor",
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
        ),
        "_tidy_checks": attr.label(
            default = "//build/bazel/product_config:tidy_checks",
        ),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    provides = [
        CcInfo,
        CcAndroidMkInfo,
        cc_common.CcSharedLibraryHintInfo,
    ],
    fragments = ["cpp"],
)

def _cc_includes_impl(ctx):
    check_absolute_include_dirs_disabled(
        ctx.label.package,
        ctx.attr.absolute_includes,
    )

    return [
        create_ccinfo_for_includes(
            ctx,
            includes = ctx.attr.includes,
            absolute_includes = ctx.attr.absolute_includes,
            system_includes = ctx.attr.system_includes,
            deps = ctx.attr.deps,
        ),
        cc_common.CcSharedLibraryHintInfo(
            # cc_shared_library shouldn't ever traverse into deps of includes
            attributes = [],
        ),
    ]

# Bazel's native cc_library rule supports specifying include paths two ways:
# 1. non-exported includes can be specified via copts attribute
# 2. exported -isystem includes can be specified via includes attribute
#
# In order to guarantee a correct inclusion search order, we need to export
# includes paths for both -I and -isystem; however, there is no native Bazel
# support to export both of these, this rule provides a CcInfo to propagate the
# given package-relative include/system include paths as exec root relative
# include/system include paths.
_cc_includes = rule(
    implementation = _cc_includes_impl,
    attrs = {
        "absolute_includes": attr.string_list(doc = "List of exec-root relative or absolute search paths for headers, usually passed with -I"),
        "includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -I"),
        "system_includes": attr.string_list(doc = "Package-relative list of search paths for headers, usually passed with -isystem"),
        "deps": attr.label_list(doc = "Re-propagates the includes obtained from these dependencies.", providers = [CcInfo]),
    },
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
    fragments = ["cpp"],
    provides = [CcInfo, cc_common.CcSharedLibraryHintInfo],
)
