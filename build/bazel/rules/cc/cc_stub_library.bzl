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

load("//build/bazel/platforms:platform_utils.bzl", "platforms")
load("//build/bazel/rules/apis:api_surface.bzl", "MODULE_LIB_API", "PUBLIC_API")
load("//build/bazel/rules/common:api.bzl", "api")
load(":cc_library_headers.bzl", "cc_library_headers")
load(":cc_library_shared.bzl", "CcStubLibrariesInfo")
load(":cc_library_static.bzl", "cc_library_static")
load(":composed_transitions.bzl", "drop_lto_sanitizer_and_fdo_profile_incoming_transition")
load(":fdo_profile_transitions.bzl", "FDO_PROFILE_ATTR_KEY")
load(":generate_toc.bzl", "CcTocInfo", "generate_toc")

# This file contains the implementation for the cc_stub_library rule.
#
# TODO(b/207812332):
# - ndk_api_coverage_parser: https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/coverage.go;l=248-262;drc=master

CcStubInfo = provider(
    fields = {
        "stub_map": "The .map file containing library symbols for the specific API version.",
        "version": "The API version of this library.",
        "abi_symbol_list": "A plain-text list of all symbols of this library for the specific API version.",
    },
)

def _stub_gen_additional_args(ctx):
    if ctx.attr.api_surface == PUBLIC_API:
        return []

    # TODO: Support LLNDK
    # Module-lib api, i.e. apex use case
    apex_stub_args = ["--systemapi", "--apex"]

    # If this is not an ndk library, add --no-ndk
    if not ctx.attr.included_in_ndk:
        # https://cs.android.com/android/_/android/platform/build/soong/+/main:cc/library.go;l=1318-1323;drc=d9b7f17f372a196efc82112c29efb86abf91e266;bpv=1;bpt=0
        apex_stub_args.append("--no-ndk")
    return apex_stub_args

def _cc_stub_gen_impl(ctx):
    # The name of this target.
    name = ctx.attr.name

    # All declared outputs of ndkstubgen.
    out_stub_c = ctx.actions.declare_file("/".join([name, "stub.c"]))
    out_stub_map = ctx.actions.declare_file("/".join([name, "stub.map"]))
    out_abi_symbol_list = ctx.actions.declare_file("/".join([name, "abi_symbol_list.txt"]))

    outputs = [out_stub_c, out_stub_map, out_abi_symbol_list]

    ndkstubgen_args = ctx.actions.args()
    ndkstubgen_args.add_all(["--arch", platforms.get_target_arch(ctx.attr._platform_utils)])
    ndkstubgen_args.add_all(["--api", ctx.attr.version])
    ndkstubgen_args.add_all(["--api-map", ctx.file._api_levels_file])

    ndkstubgen_args.add_all(_stub_gen_additional_args(ctx))
    ndkstubgen_args.add(ctx.file.symbol_file)
    ndkstubgen_args.add_all(outputs)
    ctx.actions.run(
        executable = ctx.executable._ndkstubgen,
        inputs = [
            ctx.file.symbol_file,
            ctx.file._api_levels_file,
        ],
        outputs = outputs,
        arguments = [ndkstubgen_args],
    )

    return [
        # DefaultInfo.files contains the .stub.c file only so that this target
        # can be used directly in the srcs of a cc_library.
        DefaultInfo(files = depset([out_stub_c])),
        CcStubInfo(
            stub_map = out_stub_map,
            abi_symbol_list = out_abi_symbol_list,
            version = ctx.attr.version,
        ),
        OutputGroupInfo(
            stub_map = [out_stub_map],
        ),
    ]

cc_stub_gen = rule(
    implementation = _cc_stub_gen_impl,
    attrs = {
        FDO_PROFILE_ATTR_KEY: attr.label(),
        # Public attributes
        "symbol_file": attr.label(mandatory = True, allow_single_file = [".map.txt"]),
        "version": attr.string(mandatory = True, default = "current"),
        "source_library_label": attr.label(mandatory = True),
        "api_surface": attr.string(mandatory = True, values = [PUBLIC_API, MODULE_LIB_API]),
        "included_in_ndk": attr.bool(
            mandatory = False,
            default = False,
            doc = """
Set to true if the source library is part of the NDK (e.g. libc, liblog). This property is a no-op unless api_surface = module-libapi.
When generating the stubs for this API surface, this property will be used to gate apis
1. If True, every un-annotated api, i.e. public api will be present in stubs
2. If False, un-annonated apis will be missing in stubs. Only #systemapi and #apex annotated apis will be present

Another way to interpret this
- For (1: True) module-libapi is a superset of publicapi and (#systemapi/#apex symbols)
- For (2: False), module-libapi is just (#systemapi/#apex symbols)
""",
        ),
        # Private attributes
        "_api_levels_file": attr.label(default = "@soong_injection//api_levels:api_levels.json", allow_single_file = True),
        "_ndkstubgen": attr.label(default = "//build/soong/cc/ndkstubgen", executable = True, cfg = "exec"),
        "_platform_utils": attr.label(default = Label("//build/bazel/platforms:platform_utils")),
    },
)

CcStubLibrarySharedInfo = provider(
    fields = {
        "source_library_label": "The source library label of the cc_stub_library_shared",
    },
)

# cc_stub_library_shared creates a cc_library_shared target, but using stub C source files generated
# from a library's .map.txt files and ndkstubgen. The top level target returns the same
# providers as a cc_library_shared, with the addition of a CcStubInfo
# containing metadata files and versions of the stub library.
def cc_stub_library_shared(name, stubs_symbol_file, version, export_includes, soname, source_library_label, deps, target_compatible_with, features, tags, api_surface, included_in_ndk = False):
    # Call ndkstubgen to generate the stub.c source file from a .map.txt file. These
    # are accessible in the CcStubInfo provider of this target.
    cc_stub_gen(
        name = name + "_files",
        symbol_file = stubs_symbol_file,
        version = version,
        source_library_label = source_library_label,
        target_compatible_with = target_compatible_with,
        api_surface = api_surface,
        included_in_ndk = included_in_ndk,
        tags = ["manual"],
    )

    # Disable coverage for stub libraries.
    features = features + ["-coverage", "-link_crt"]

    # The static library at the root of the stub shared library.
    cc_library_static(
        name = name + "_root",
        srcs_c = [name + "_files"],  # compile the stub.c file
        copts = ["-fno-builtin"],  # ignore conflicts with builtin function signatures
        features = [
            # Don't link the C runtime
            "-link_crt",
            # Enable the stub library compile flags
            "stub_library",
            # Disable all include-related features to avoid including any headers
            # that may cause conflicting type errors with the symbols in the
            # generated stubs source code.
            #  e.g.
            #  double acos(double); // in header
            #  void acos() {} // in the generated source code
            # See https://cs.android.com/android/platform/superproject/+/master:build/soong/cc/library.go;l=942-946;drc=d8a72d7dc91b2122b7b10b47b80cf2f7c65f9049
            "-toolchain_include_directories",
            "-includes",
            "-include_paths",
        ],
        target_compatible_with = target_compatible_with,
        stl = "none",
        system_dynamic_deps = [],
        tags = ["manual"],
        export_includes = export_includes,
        # deps is used to export includes that specified using "header_libs" in Android.bp, e.g. "libc_headers".
        deps = deps,
    )

    # Create a .so for the stub library. This library is self contained, has
    # no deps, and doesn't link against crt.
    if len(soname) == 0:
        fail("For stub libraries 'soname' is mandatory and must be same as the soname of its source library.")
    soname_flag = "-Wl,-soname," + soname
    stub_map = name + "_stub_map"
    native.filegroup(
        name = stub_map,
        srcs = [name + "_files"],
        output_group = "stub_map",
        tags = ["manual"],
    )
    version_script_flag = "-Wl,--version-script,$(location %s)" % stub_map
    native.cc_shared_library(
        name = name + "_so",
        additional_linker_inputs = [stub_map],
        user_link_flags = [soname_flag, version_script_flag],
        roots = [name + "_root"],
        features = features + ["-link_crt"],
        target_compatible_with = target_compatible_with,
        tags = ["manual"],
    )

    # Create a target with CcSharedLibraryInfo and CcStubInfo providers.
    _cc_stub_library_shared(
        name = name,
        stub_target = name + "_files",
        library_target = name + "_so",
        root = name + "_root",
        source_library_label = source_library_label,
        version = version,
        tags = tags,
    )

def _cc_stub_library_shared_impl(ctx):
    source_library_label = Label(ctx.attr.source_library_label)
    api_level = str(api.parse_api_level_from_version(ctx.attr.version))
    version_macro_name = "__" + source_library_label.name.upper() + "_API__=" + api_level
    compilation_context = cc_common.create_compilation_context(
        defines = depset([version_macro_name]),
    )

    cc_info = cc_common.merge_cc_infos(cc_infos = [
        ctx.attr.root[CcInfo],
        CcInfo(compilation_context = compilation_context),
    ])

    library_target_so_files = ctx.attr.library_target.files.to_list()
    if len(library_target_so_files) != 1:
        fail("expected single .so output file from library_target (%s); got %s" % (
            ctx.attr.library_target.label,
            library_target_so_files,
        ))
    toc_info = generate_toc(ctx, ctx.attr.name, library_target_so_files[0])

    return [
        ctx.attr.library_target[DefaultInfo],
        ctx.attr.library_target[CcSharedLibraryInfo],
        ctx.attr.stub_target[CcStubInfo],
        toc_info,
        cc_info,
        CcStubLibrariesInfo(has_stubs = True),
        OutputGroupInfo(rule_impl_debug_files = depset()),
        CcStubLibrarySharedInfo(source_library_label = source_library_label),
    ]

_cc_stub_library_shared = rule(
    implementation = _cc_stub_library_shared_impl,
    doc = "Top level rule to merge CcStubInfo and CcSharedLibraryInfo into a single target",
    # Incoming transition to reset //command_line_option:fdo_profile to None
    # to converge the configurations of the stub targets
    # This also resets any lto transitions.
    cfg = drop_lto_sanitizer_and_fdo_profile_incoming_transition,
    attrs = {
        FDO_PROFILE_ATTR_KEY: attr.label(),
        "stub_target": attr.label(
            providers = [CcStubInfo],
            mandatory = True,
        ),
        "library_target": attr.label(
            providers = [CcSharedLibraryInfo],
            mandatory = True,
        ),
        "root": attr.label(
            providers = [CcInfo],
            mandatory = True,
        ),
        "source_library_label": attr.string(mandatory = True),
        "version": attr.string(mandatory = True),
        "_allowlist_function_transition": attr.label(
            default = "@bazel_tools//tools/allowlists/function_transition_allowlist",
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
    },
    provides = [
        CcSharedLibraryInfo,
        CcTocInfo,
        CcInfo,
        CcStubInfo,
        CcStubLibrariesInfo,
        CcStubLibrarySharedInfo,
    ],
)

def cc_stub_suite(
        name,
        source_library_label,
        versions,
        symbol_file,
        export_includes = [],
        soname = "",
        deps = [],
        data = [],  # @unused
        target_compatible_with = [],
        features = [],
        tags = ["manual"],
        api_surface = PUBLIC_API,
        included_in_ndk = False):
    # Implicitly add "current" to versions. This copies the behavior from Soong (aosp/1641782)
    if "current" not in versions:
        versions.append("current")

    for version in versions:
        cc_stub_library_shared(
            # Use - as the seperator of name and version. "current" might be the version of some libraries.
            name = name + "-" + version,
            version = version,
            stubs_symbol_file = symbol_file,
            export_includes = export_includes,
            soname = soname,
            source_library_label = str(native.package_relative_label(source_library_label)),
            deps = deps,
            target_compatible_with = target_compatible_with,
            features = features,
            tags = tags,
            api_surface = api_surface,
            included_in_ndk = included_in_ndk,
        )

    # Create a header library target for this API surface (ModuleLibApi)
    # The external @api_surfaces repository will contain an alias to this header library.
    cc_library_headers(
        name = "%s_%s_headers" % (name, MODULE_LIB_API),
        export_includes = export_includes,
        deps = deps,  # Necessary for exporting headers that might exist in a different directory (e.g. libEGL)
    )

    native.alias(
        # Use _ as the seperator of name and version in alias. So there is no
        # duplicated name if "current" is one of the versions of a library.
        name = name + "_current",
        actual = name + "-" + "current",
        tags = tags,
    )
