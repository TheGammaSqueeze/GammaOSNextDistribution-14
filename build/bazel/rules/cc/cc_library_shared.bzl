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
load("@bazel_tools//tools/cpp:toolchain_utils.bzl", "find_cpp_toolchain")
load("//build/bazel/rules/abi:abi_dump.bzl", "AbiDiffInfo", "abi_dump")
load(
    ":cc_library_common.bzl",
    "CcAndroidMkInfo",
    "add_lists_defaulting_to_none",
    "check_valid_ldlibs",
    "parse_sdk_version",
    "system_dynamic_deps_defaults",
)
load(":cc_library_static.bzl", "cc_library_static")
load(":clang_tidy.bzl", "collect_deps_clang_tidy_info")
load(
    ":composed_transitions.bzl",
    "lto_and_fdo_profile_incoming_transition",
    "lto_and_sanitizer_deps_transition",
)
load(
    ":fdo_profile_transitions.bzl",
    "FDO_PROFILE_ATTR_KEY",
)
load(":generate_toc.bzl", "CcTocInfo", "generate_toc")
load(":stl.bzl", "stl_info_from_attr")
load(":stripped_cc_common.bzl", "CcUnstrippedInfo", "stripped_shared_library")
load(":versioned_cc_common.bzl", "versioned_shared_library")

def cc_library_shared(
        name,
        stem = "",
        suffix = "",
        # Common arguments between shared_root and the shared library
        features = [],
        dynamic_deps = [],
        implementation_dynamic_deps = [],
        linkopts = [],
        target_compatible_with = [],
        # Ultimately _static arguments for shared_root production
        srcs = [],
        srcs_c = [],
        srcs_as = [],
        copts = [],
        cppflags = [],
        conlyflags = [],
        asflags = [],
        hdrs = [],
        implementation_deps = [],
        deps = [],
        whole_archive_deps = [],
        implementation_whole_archive_deps = [],
        system_dynamic_deps = None,
        runtime_deps = [],
        export_includes = [],
        export_absolute_includes = [],
        export_system_includes = [],
        local_includes = [],
        absolute_includes = [],
        rtti = False,
        stl = "",
        cpp_std = "",
        c_std = "",
        additional_linker_inputs = None,
        additional_compiler_inputs = [],

        # Purely _shared arguments
        strip = {},

        # TODO(b/202299295): Handle data attribute.
        data = [],  # @unused
        use_version_lib = False,
        stubs_symbol_file = None,
        inject_bssl_hash = False,
        sdk_version = "",  # @unused
        min_sdk_version = "",
        abi_checker_enabled = None,
        abi_checker_symbol_file = None,
        abi_checker_exclude_symbol_versions = [],
        abi_checker_exclude_symbol_tags = [],
        abi_checker_check_all_apis = False,
        abi_checker_diff_flags = [],
        native_coverage = True,
        tags = [],
        fdo_profile = None,
        tidy = None,
        tidy_checks = None,
        tidy_checks_as_errors = None,
        tidy_flags = None,
        tidy_disabled_srcs = None,
        tidy_timeout_srcs = None,
        tidy_gen_header_filter = None,
        **kwargs):
    "Bazel macro to correspond with the cc_library_shared Soong module."

    # There exist modules named 'libtest_missing_symbol' and
    # 'libtest_missing_symbol_root'. Ensure that that the target suffixes are
    # sufficiently unique.
    shared_root_name = name + "__internal_root"
    unstripped_name = name + "_unstripped"
    stripped_name = name + "_stripped"

    if system_dynamic_deps == None:
        system_dynamic_deps = system_dynamic_deps_defaults

    if min_sdk_version:
        features = features + parse_sdk_version(min_sdk_version) + ["-sdk_version_default"]

    if fdo_profile != None:
        # FIXME(b/261609769): This is a temporary workaround to add link flags
        # that requires the path to fdo profile.
        # This workaround is error-prone because it assumes all the fdo_profile
        # targets are created in a specific way (e.g. fdo_profile target named foo
        # uses an afdo profile file named foo.afdo in the same folder).
        fdo_profile_file = fdo_profile + "_file"
        linkopts = linkopts + [
            "-funique-internal-linkage-names",
            "-fprofile-sample-accurate",
            # profile-sample-use is needed to ensure symbol ordering
            "-fprofile-sample-use=$(location {})".format(fdo_profile_file),
            "-Wl,-mllvm,-no-warn-sample-unused=true",
        ]
        if additional_linker_inputs != None:
            additional_linker_inputs = additional_linker_inputs + [fdo_profile_file]
        else:
            additional_linker_inputs = [fdo_profile_file]

    stl_info = stl_info_from_attr(stl, True)
    linkopts = linkopts + stl_info.linkopts
    copts = copts + stl_info.cppflags

    if not native_coverage:
        features = features + ["-coverage"]
    else:
        features = features + select({
            "//build/bazel/rules/cc:android_coverage_lib_flag": ["android_coverage_lib"],
            "//build/bazel/rules/cc:android_coverage_lib_flag_cfi": ["android_coverage_lib"],
            "//conditions:default": [],
        })

        # TODO(b/233660582): deal with the cases where the default lib shouldn't be used
        deps = deps + select({
            "//build/bazel/rules/cc:android_coverage_lib_flag": ["//system/extras/toolchain-extras:libprofile-clang-extras"],
            "//build/bazel/rules/cc:android_coverage_lib_flag_cfi": ["//system/extras/toolchain-extras:libprofile-clang-extras_cfi_support"],
            "//conditions:default": [],
        })

    # The static library at the root of the shared library.
    # This may be distinct from the static version of the library if e.g.
    # the static-variant srcs are different than the shared-variant srcs.
    cc_library_static(
        name = shared_root_name,
        shared_linking = True,
        hdrs = hdrs,
        srcs = srcs,
        srcs_c = srcs_c,
        srcs_as = srcs_as,
        copts = copts,
        cppflags = cppflags,
        conlyflags = conlyflags,
        asflags = asflags,
        export_includes = export_includes,
        export_absolute_includes = export_absolute_includes,
        export_system_includes = export_system_includes,
        local_includes = local_includes,
        absolute_includes = absolute_includes,
        rtti = rtti,
        stl = "none",
        cpp_std = cpp_std,
        c_std = c_std,
        dynamic_deps = dynamic_deps,
        implementation_deps = implementation_deps + stl_info.static_deps,
        implementation_dynamic_deps = implementation_dynamic_deps + stl_info.shared_deps,
        implementation_whole_archive_deps = implementation_whole_archive_deps,
        system_dynamic_deps = system_dynamic_deps,
        deps = deps,
        whole_archive_deps = whole_archive_deps,
        features = features,
        target_compatible_with = target_compatible_with,
        tags = ["manual"],
        native_coverage = native_coverage,
        tidy = tidy,
        tidy_checks = tidy_checks,
        tidy_checks_as_errors = tidy_checks_as_errors,
        tidy_flags = tidy_flags,
        tidy_disabled_srcs = tidy_disabled_srcs,
        tidy_timeout_srcs = tidy_timeout_srcs,
        tidy_gen_header_filter = tidy_gen_header_filter,
        additional_compiler_inputs = additional_compiler_inputs,
    )

    # dynamic deps are to be linked into the shared library via
    # --no-whole-archive. In order to do so, they need to be dependencies of a
    # "root" of the cc_shared_library, but may not be roots themselves.  Below
    # we define stub roots (which themselves have no srcs) in order to
    # facilitate this.
    imp_deps_stub = name + "_implementation_deps"
    native.cc_library(
        name = imp_deps_stub,
        deps = implementation_dynamic_deps +
               system_dynamic_deps +
               stl_info.shared_deps +
               dynamic_deps,
        target_compatible_with = target_compatible_with,
        tags = ["manual"],
    )

    shared_dynamic_deps = add_lists_defaulting_to_none(
        dynamic_deps,
        system_dynamic_deps,
        implementation_dynamic_deps,
        stl_info.shared_deps,
    )

    filename_stem = stem or name
    soname = filename_stem + suffix + ".so"
    soname_flag = "-Wl,-soname," + soname

    native.cc_shared_library(
        name = unstripped_name,
        user_link_flags = linkopts + [soname_flag],
        dynamic_deps = shared_dynamic_deps,
        additional_linker_inputs = additional_linker_inputs,
        deps = [shared_root_name] + whole_archive_deps + [imp_deps_stub],
        features = features,
        target_compatible_with = target_compatible_with,
        tags = ["manual"],
        **kwargs
    )

    hashed_name = name + "_hashed"
    _bssl_hash_injection(
        name = hashed_name,
        src = unstripped_name,
        inject_bssl_hash = inject_bssl_hash,
        tags = ["manual"],
    )

    versioned_name = name + "_versioned"
    versioned_shared_library(
        name = versioned_name,
        src = hashed_name,
        stamp_build_number = use_version_lib,
        tags = ["manual"],
    )

    stripped_shared_library(
        name = stripped_name,
        src = versioned_name,
        target_compatible_with = target_compatible_with,
        tags = ["manual"],
        **strip
    )

    # The logic here is based on the shouldCreateSourceAbiDumpForLibrary() in sabi.go
    # abi_root is used to control if abi_dump aspects should be run on the static
    # deps because there is no way to control the aspects directly from the rule.
    abi_root = shared_root_name

    # explicitly disabled
    if abi_checker_enabled == False:
        abi_root = None
    elif abi_checker_enabled == True or stubs_symbol_file:
        # The logic comes from here:
        # https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/library.go;l=2288;drc=73feba33308bf9432aea43e069ed24a2f0312f1b
        if not abi_checker_symbol_file and stubs_symbol_file:
            abi_checker_symbol_file = stubs_symbol_file
    else:
        abi_root = None

    abi_checker_explicitly_disabled = abi_checker_enabled == False

    abi_dump_name = name + "_abi_dump"
    abi_dump(
        name = abi_dump_name,
        shared = stripped_name,
        root = abi_root,
        soname = soname,
        has_stubs = stubs_symbol_file != None,
        enabled = abi_checker_enabled,
        explicitly_disabled = abi_checker_explicitly_disabled,
        symbol_file = abi_checker_symbol_file,
        exclude_symbol_versions = abi_checker_exclude_symbol_versions,
        exclude_symbol_tags = abi_checker_exclude_symbol_tags,
        check_all_apis = abi_checker_check_all_apis,
        diff_flags = abi_checker_diff_flags,
        tags = ["manual"],
    )

    _cc_library_shared_proxy(
        name = name,
        shared = stripped_name,
        shared_debuginfo = unstripped_name,
        deps = [shared_root_name],
        features = features,
        output_file = paths.join(name, soname),  # Prevent name collision by generating in a directory unique to the target
        target_compatible_with = target_compatible_with,
        has_stubs = stubs_symbol_file != None,
        runtime_deps = runtime_deps,
        abi_dump = abi_dump_name,
        fdo_profile = fdo_profile,
        linkopts = linkopts,
        package_name = native.package_name(),
        tags = tags,
    )

def _create_dynamic_library_linker_input_for_file(ctx, shared_info, output):
    cc_toolchain = find_cpp_toolchain(ctx)
    feature_configuration = cc_common.configure_features(
        ctx = ctx,
        cc_toolchain = cc_toolchain,
    )

    new_library_to_link = cc_common.create_library_to_link(
        actions = ctx.actions,
        dynamic_library = output,
        feature_configuration = feature_configuration,
        cc_toolchain = cc_toolchain,
    )

    new_linker_input = cc_common.create_linker_input(
        owner = shared_info.linker_input.owner,
        libraries = depset([new_library_to_link]),
    )
    return new_linker_input

def _correct_cc_shared_library_linking(ctx, shared_info, new_output):
    # we may have done some post-processing of the shared library
    # replace the linker_input that has not been post-processed with the
    # library that has been post-processed
    new_linker_input = _create_dynamic_library_linker_input_for_file(ctx, shared_info, new_output)

    return CcSharedLibraryInfo(
        dynamic_deps = shared_info.dynamic_deps,
        exports = shared_info.exports,
        link_once_static_libs = shared_info.link_once_static_libs,
        linker_input = new_linker_input,
    )

CcStubLibrariesInfo = provider(
    fields = {
        "has_stubs": "If the shared library has stubs",
    },
)

# A provider to propagate shared library output artifacts, primarily useful
# for root level querying in Soong-Bazel mixed builds.
# Ideally, it would be preferable to reuse the existing native
# CcSharedLibraryInfo provider, but that provider requires that shared library
# artifacts are wrapped in a linker input. Artifacts retrievable from this linker
# input are symlinks to the original artifacts, which is problematic when
# other dependencies expect a real file.
CcSharedLibraryOutputInfo = provider(
    fields = {
        "output_file": "A single .so file, produced by this target.",
    },
)

def _cc_library_shared_proxy_impl(ctx):
    check_valid_ldlibs(ctx, ctx.attr.linkopts)

    # Using a "deps" label_list instead of a single mandatory label attribute
    # is a hack to support aspect propagation of graph_aspect of the native
    # cc_shared_library. The aspect will only be applied and propagated along
    # a label_list attribute named "deps".
    if len(ctx.attr.deps) != 1:
        fail("Exactly one 'deps' must be specified for cc_library_shared_proxy")
    root_files = ctx.attr.deps[0][DefaultInfo].files.to_list()
    shared_files = ctx.attr.shared[0][DefaultInfo].files.to_list()
    shared_debuginfo = ctx.attr.shared_debuginfo[0][DefaultInfo].files.to_list()
    if len(shared_files) != 1 or len(shared_debuginfo) != 1:
        fail("Expected only one shared library file and one debuginfo file for it")

    shared_lib = shared_files[0]
    abi_diff_files = ctx.attr.abi_dump[0][AbiDiffInfo].diff_files.to_list()

    # Copy the output instead of symlinking. This is because this output
    # can be directly installed into a system image; this installation treats
    # symlinks differently from real files (symlinks will be preserved relative
    # to the image root).
    ctx.actions.run_shell(
        # We need to add the abi dump files to the inputs of this copy action even
        # though they are not used, otherwise not all the abi dump files will be
        # created. For example, for b build
        # packages/modules/adb/pairing_connection:libadb_pairing_server, only
        # libadb_pairing_server.so.lsdump will be created, libadb_pairing_auth.so.lsdump
        # and libadb_pairing_connection.so.lsdump will not be. The reason is that
        # even though libadb_pairing server depends on libadb_pairing_auth and
        # libadb_pairing_connection, the abi dump files are not explicitly used
        # by libadb_pairing_server, so bazel won't bother generating them.
        inputs = depset(direct = [shared_lib] + abi_diff_files),
        outputs = [ctx.outputs.output_file],
        command = "cp -f %s %s" % (shared_lib.path, ctx.outputs.output_file.path),
        mnemonic = "CopyFile",
        progress_message = "Copying files",
        use_default_shell_env = True,
    )

    toc_info = generate_toc(ctx, ctx.attr.name, ctx.outputs.output_file)

    files = root_files + [ctx.outputs.output_file, toc_info.toc] + abi_diff_files

    return [
        DefaultInfo(
            files = depset(direct = files),
            runfiles = ctx.runfiles(files = [ctx.outputs.output_file]),
        ),
        _correct_cc_shared_library_linking(ctx, ctx.attr.shared[0][CcSharedLibraryInfo], ctx.outputs.output_file),
        toc_info,
        # The _only_ linker_input is the statically linked root itself. We need to propagate this
        # as cc_shared_library identifies which libraries can be linked dynamically based on the
        # linker_inputs of the roots
        ctx.attr.deps[0][CcInfo],
        ctx.attr.deps[0][CcAndroidMkInfo],
        CcStubLibrariesInfo(has_stubs = ctx.attr.has_stubs),
        ctx.attr.shared[0][OutputGroupInfo],
        CcSharedLibraryOutputInfo(output_file = ctx.outputs.output_file),
        CcUnstrippedInfo(unstripped = shared_debuginfo[0]),
        ctx.attr.abi_dump[0][AbiDiffInfo],
        collect_deps_clang_tidy_info(ctx),
        cc_common.CcSharedLibraryHintInfo(
            # cc_shared_library doesn't need to traverse any attrs of a cc_shared_library dep
            attributes = [],
        ),
    ]

_cc_library_shared_proxy = rule(
    implementation = _cc_library_shared_proxy_impl,
    # Incoming transition to override outgoing transition from rdep
    cfg = lto_and_fdo_profile_incoming_transition,
    attrs = {
        FDO_PROFILE_ATTR_KEY: attr.label(),
        "shared": attr.label(
            mandatory = True,
            providers = [CcSharedLibraryInfo],
            cfg = lto_and_sanitizer_deps_transition,
        ),
        "shared_debuginfo": attr.label(
            mandatory = True,
            cfg = lto_and_sanitizer_deps_transition,
        ),
        # "deps" should be a single element: the root target of the shared library.
        # See _cc_library_shared_proxy_impl comment for explanation.
        "deps": attr.label_list(
            mandatory = True,
            providers = [CcInfo],
            cfg = lto_and_sanitizer_deps_transition,
        ),
        "output_file": attr.output(mandatory = True),
        "has_stubs": attr.bool(default = False),
        "runtime_deps": attr.label_list(
            providers = [CcInfo],
            doc = "Deps that should be installed along with this target. Read by the apex cc aspect.",
        ),
        "abi_dump": attr.label(
            providers = [AbiDiffInfo],
            cfg = lto_and_sanitizer_deps_transition,
        ),
        "package_name": attr.string(
            mandatory = True,
            doc = "Just the path to the target package. Used by transitions.",
        ),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
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
                  " information to AndroidMk about LOCAL_SHARED_LIBRARIES.",
        ),
        "_toc_script": attr.label(
            cfg = "exec",
            executable = True,
            allow_single_file = True,
            default = "//build/soong/scripts:toc.sh",
        ),
        "_readelf": attr.label(
            cfg = "exec",
            executable = True,
            allow_single_file = True,
            default = "//prebuilts/clang/host/linux-x86:llvm-readelf",
        ),
        "linkopts": attr.string_list(default = []),  # Used for validation
        "_android_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/os:android"),
        ),
        "_darwin_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/os:darwin"),
        ),
        "_linux_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/os:linux"),
        ),
        "_windows_constraint": attr.label(
            default = Label("//build/bazel_common_rules/platforms/os:windows"),
        ),
    },
    provides = [CcAndroidMkInfo, CcInfo, CcTocInfo, cc_common.CcSharedLibraryHintInfo],
    fragments = ["cpp"],
    toolchains = ["@bazel_tools//tools/cpp:toolchain_type"],
)

def _bssl_hash_injection_impl(ctx):
    hashed_file = ctx.file.src
    if ctx.attr.inject_bssl_hash:
        hashed_file = ctx.actions.declare_file("lib" + ctx.attr.name + ".so")
        args = ctx.actions.args()
        args.add_all(["-in-object", ctx.files.src[0]])
        args.add_all(["-o", hashed_file])

        ctx.actions.run(
            inputs = ctx.files.src,
            outputs = [hashed_file],
            executable = ctx.executable._bssl_inject_hash,
            arguments = [args],
            tools = [ctx.executable._bssl_inject_hash],
            mnemonic = "BsslInjectHash",
        )

    return [
        DefaultInfo(files = depset([hashed_file])),
        ctx.attr.src[CcSharedLibraryInfo],
        ctx.attr.src[OutputGroupInfo],
    ]

_bssl_hash_injection = rule(
    implementation = _bssl_hash_injection_impl,
    attrs = {
        "src": attr.label(
            mandatory = True,
            allow_single_file = True,
            providers = [CcSharedLibraryInfo],
        ),
        "inject_bssl_hash": attr.bool(
            default = False,
            doc = "Whether inject BSSL hash",
        ),
        "_bssl_inject_hash": attr.label(
            cfg = "exec",
            doc = "The BSSL hash injection tool.",
            executable = True,
            default = "//external/boringssl:bssl_inject_hash",
            allow_single_file = True,
        ),
    },
)
