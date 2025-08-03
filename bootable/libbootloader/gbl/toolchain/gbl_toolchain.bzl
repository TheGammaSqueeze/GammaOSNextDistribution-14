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

"""
This file contains rules for defininig GBL toolchains.
"""

load(
    "@bazel_tools//tools/cpp:cc_toolchain_config_lib.bzl",
    "action_config",
    "flag_group",
    "flag_set",
    "tool",
    "tool_path",
)
load("@gbl_llvm_prebuilts//:info.bzl", "LLVM_PREBUILTS_C_INCLUDE", "gbl_llvm_tool_path")
load("@rules_cc//cc:action_names.bzl", "ACTION_NAMES", "ALL_CPP_COMPILE_ACTION_NAMES")

def _flag_set(flags):
    """Convert a list of compile/link flags to a flag_set type."""
    return flag_set(
        flag_groups = [
            flag_group(
                flags = flags,
            ),
        ],
    )

def _gbl_clang_cc_toolchain_config_impl(ctx):
    """Implementation for rule _gbl_clang_cc_toolchain_config()"""
    builtin_includes = ctx.attr.builtin_includes or [LLVM_PREBUILTS_C_INCLUDE]
    common_compile_flagset = [
        _flag_set([
            "--target={}".format(ctx.attr.target_system_triple),
            "-nostdinc",
            "-no-canonical-prefixes",
            "-Werror",
            "-Wall",
        ] + ["-I{}".format(inc) for inc in builtin_includes] + ctx.attr.cc_flags),
    ]
    return cc_common.create_cc_toolchain_config_info(
        ctx = ctx,
        cxx_builtin_include_directories = builtin_includes,
        toolchain_identifier = "{}_id".format(ctx.attr.cc_toolchain_name),
        host_system_name = "local",
        target_system_name = ctx.attr.target_system_triple,
        target_cpu = ctx.attr.target_cpu,
        target_libc = "unknown",
        compiler = "clang",
        abi_version = "unknown",
        abi_libc_version = "unknown",
        action_configs = [
            action_config(
                action_name = action_name,
                enabled = True,
                tools = [tool(path = gbl_llvm_tool_path("clang++"))],
                flag_sets = common_compile_flagset,
            )
            for action_name in ALL_CPP_COMPILE_ACTION_NAMES +
                               [ACTION_NAMES.assemble, ACTION_NAMES.preprocess_assemble]
        ] + [
            action_config(
                action_name = ACTION_NAMES.c_compile,
                enabled = True,
                tools = [tool(path = gbl_llvm_tool_path("clang"))],
                flag_sets = common_compile_flagset,
            ),
        ] + [
            action_config(
                action_name = ACTION_NAMES.cpp_link_executable,
                enabled = True,
                tools = [tool(path = gbl_llvm_tool_path("clang++"))],
                flag_sets = [_flag_set(ctx.attr.ld_flags)] if ctx.attr.ld_flags else [],
            ),
        ],
        tool_paths = [
            tool_path(
                name = "ld",
                path = gbl_llvm_tool_path("clang++"),
            ),
            tool_path(
                name = "ar",
                path = gbl_llvm_tool_path("llvm-ar"),
            ),
            tool_path(
                name = "cpp",
                path = gbl_llvm_tool_path("clang++"),
            ),
            tool_path(
                name = "gcc",
                path = gbl_llvm_tool_path("clang"),
            ),
            tool_path(
                name = "gcov",
                path = gbl_llvm_tool_path("llvm-cov"),
            ),
            tool_path(
                name = "nm",
                path = gbl_llvm_tool_path("llvm-nm"),
            ),
            tool_path(
                name = "objdump",
                path = gbl_llvm_tool_path("llvm-objdump"),
            ),
            tool_path(
                name = "strip",
                path = gbl_llvm_tool_path("llvm-strip"),
            ),
        ],
    )

_gbl_clang_cc_toolchain_config = rule(
    implementation = _gbl_clang_cc_toolchain_config_impl,
    attrs = {
        "cc_toolchain_name": attr.string(),
        "target_cpu": attr.string(),
        "target_system_triple": attr.string(),
        "cc_flags": attr.string_list(),
        "ld_flags": attr.string_list(),
        "builtin_includes": attr.string_list(),
    },
    provides = [CcToolchainConfigInfo],
)

def gbl_clang_cc_toolchain(
        name,
        target_cpu,
        target_system_triple,
        cc_flags = None,
        ld_flags = None,
        builtin_includes = None):
    """Configure a clang based cc_toolchain().

    Args:
        name (String): Name of the target.
        target_cpu (string): Target CPU.
        target_system_triple (string): LLVM-style target system triple.
        cc_flags (List): clang compile flags.
        ld_flags (List): clang link flags
        builtin_includes (List): Override the default list of builtin include dirs.
    """
    config_name = "_{}_config".format(name)
    _gbl_clang_cc_toolchain_config(
        name = config_name,
        cc_toolchain_name = name,
        target_cpu = target_cpu,
        target_system_triple = target_system_triple,
        cc_flags = cc_flags,
        ld_flags = ld_flags,
        builtin_includes = builtin_includes,
    )

    empty_filegroup = "_empty_{}".format(name)
    native.filegroup(name = empty_filegroup)
    empty_filegroup_target = ":{}".format(empty_filegroup)

    native.cc_toolchain(
        name = name,
        toolchain_identifier = name,
        toolchain_config = ":{}".format(config_name),
        all_files = empty_filegroup_target,
        compiler_files = empty_filegroup_target,
        dwp_files = empty_filegroup_target,
        linker_files = empty_filegroup_target,
        objcopy_files = empty_filegroup_target,
        strip_files = empty_filegroup_target,
        supports_param_files = 0,
    )

def _prebuilt_binary_impl(ctx):
    """Generate a wrapper executable type target that simply symlinks to a given executable binary.

    This is for rules that only accept executable type target but not binary file directly.
    i.e. `rust_bindgen_toolchain`
    """
    out = ctx.actions.declare_file(ctx.label.name)
    ctx.actions.symlink(
        output = out,
        target_file = ctx.executable.bin,
    )
    return [DefaultInfo(files = depset([out]), executable = out)]

prebuilt_binary = rule(
    implementation = _prebuilt_binary_impl,
    executable = True,
    attrs = {
        "bin": attr.label(allow_single_file = True, executable = True, cfg = "exec"),
    },
)

# A transition rule that emits the `@gbl//toolchain:rust_no_sysroot_true` setting.
def _no_sysroot_transition_impl(_, __):
    return {"@gbl//toolchain:rust_no_sysroot": True}

_no_sysroot_transition = transition(
    implementation = _no_sysroot_transition_impl,
    inputs = [],
    outputs = ["@gbl//toolchain:rust_no_sysroot"],
)

# A rule implementation that simply forwards dependencies from attribute `deps` and generates
# symlinks to their output files.
def _forward_and_symlink(ctx):
    outs = []
    for file in ctx.files.deps:
        # Append the label name to the file name but keep the same extension. i.e.
        # "<file>.<extension>" -> "<file>_<label>.<extension>"
        stem = file.basename.removesuffix(".{}".format(file.extension))
        out = ctx.actions.declare_file("{}_{}.{}".format(stem, ctx.label.name, file.extension))
        ctx.actions.symlink(output = out, target_file = file)
        outs.append(out)
    return [DefaultInfo(files = depset(outs))]

# A rule for building rust targets with the `@gbl//toolchain:rust_no_sysroot_true` setting.
build_with_no_rust_sysroot = rule(
    implementation = _forward_and_symlink,
    cfg = _no_sysroot_transition,
    attrs = {
        # Mandatory attribute for rules with transition.
        "_allowlist_function_transition": attr.label(
            default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist"),
        ),
        "deps": attr.label_list(allow_files = True, mandatory = True),
    },
)

# A transition rule that emits the "--platforms=<attr.platform>" option.
def _platform_transition_impl(_, attr):
    return {"//command_line_option:platforms": attr.platform}

_platform_transition = transition(
    implementation = _platform_transition_impl,
    inputs = [],
    outputs = ["//command_line_option:platforms"],
)

build_with_platform = rule(
    implementation = _forward_and_symlink,
    cfg = _platform_transition,
    attrs = {
        # Mandatory attribute for rules with transition.
        "_allowlist_function_transition": attr.label(
            default = Label("@bazel_tools//tools/allowlists/function_transition_allowlist"),
        ),
        "platform": attr.string(mandatory = True),
        "deps": attr.label_list(allow_files = True, mandatory = True),
    },
)

# This rule creates symlink for a static library in both Linux/GNU and MSVC naming styles so that
# rust linker is able to find it when building for them.
#
# When flag "-Clink-arg=-l<libname>" is passed to rustc, for Linux/GNU target platforms, the linker
# looks for library named "lib<libname>.a", for MSVC target plaforms (i.e. UEFI), the linker looks
# for library named "<libname>.lib". When bazel builds a cc_library target, it always outputs the
# Linux/GNU naming style and therefore fails linking when building for UEFI targets.
def _link_static_cc_library_impl(ctx):
    # Put an underscore so that we don't need to deal with potential "lib" prefix from user
    # provided name.
    libname = "_{}".format(ctx.label.name)

    # Create symlink for both naming styles.
    out_msvc_style = ctx.actions.declare_file("{}.lib".format(libname))
    ctx.actions.symlink(output = out_msvc_style, target_file = ctx.files.cc_library[0])
    out_linux_style = ctx.actions.declare_file("lib{}.a".format(libname))
    ctx.actions.symlink(output = out_linux_style, target_file = ctx.files.cc_library[0])

    # Construct and return a `CcInfo` for this rule that includes the library to link, so that
    # other rust_library/cc_library can depend directly on this target.
    library_to_link = cc_common.create_library_to_link(
        actions = ctx.actions,
        # Either is fine, since both yield the same linker option by Bazel.
        static_library = out_linux_style,
    )
    linking_input = cc_common.create_linker_input(
        owner = ctx.label,
        libraries = depset([library_to_link]),
        # Make sure both symlinks are generated.
        additional_inputs = depset([out_msvc_style, out_linux_style]),
    )
    linking_context = cc_common.create_linking_context(linker_inputs = depset([linking_input]))
    return [CcInfo(linking_context = linking_context)]

link_static_cc_library = rule(
    implementation = _link_static_cc_library_impl,
    attrs = {
        "cc_library": attr.label(),  # The cc_library() target for the static library.
    },
)
