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
This file contains rules and logic to initialize GBL workspace.
"""

def _abs_path(repo_ctx, path):
    return repo_ctx.execute(["readlink", "-f", path]).stdout.strip("\n")

def _dir_of(repo_ctx, path):
    return repo_ctx.execute(["dirname", _abs_path(repo_ctx, path)]).stdout.strip("\n")

def _gbl_llvm_prebuilts_impl(repo_ctx):
    """Implementation for gbl_llvm_prebuilts

    The repository rule sets up a repo for hosting LLVM distribution and Linux sysroot. They can
    be provided manually via the `GBL_LLVM_PREBUILTS` and `GBL_LINUX_SYSROOT` environment
    variables. If they don't exist, the top-level workspace needs to define them in repo
    `@llvm_linux_x86_64_prebuilts` and `@linux_x86_64_sysroot` respectively.

    Only Linux x86_64 platform is supported.
    """
    prebuilts = repo_ctx.os.environ.get("GBL_LLVM_PREBUILTS")
    if prebuilts:
        repo_ctx.symlink(prebuilts, "llvm-linux-x86")
    else:
        path = _dir_of(repo_ctx, repo_ctx.path(Label("@llvm_linux_x86_64_prebuilts//:BUILD.bazel")))
        repo_ctx.symlink(path, "llvm-linux-x86")

    # Linux host toolchain additionally needs a sysroot
    linux_glibc = repo_ctx.os.environ.get("GBL_LINUX_SYSROOT")
    if linux_glibc:
        repo_ctx.symlink(linux_glibc, "linux_glibc")
    else:
        path = _dir_of(repo_ctx, repo_ctx.path(Label("@linux_x86_64_sysroot//:BUILD.bazel")))
        repo_ctx.symlink(path, "linux_glibc")

    # Get the bin directory so that we can access other LLVM tools by path.
    gbl_llvm_bin_dir = _abs_path(repo_ctx, "llvm-linux-x86/bin")

    # Create a info.bzl file in the assembled repo to export header/library/tool paths.
    info_bzl_content = """
def gbl_llvm_tool_path(tool_name):
    return "{}/" + tool_name
""".format(gbl_llvm_bin_dir)

    # Get the builtin include directories.
    clang = _abs_path(repo_ctx, "llvm-linux-x86/bin/clang")
    llvm_resource_dir = repo_ctx.execute([clang, "--print-resource-dir"]).stdout.strip("\n")
    info_bzl_content += """
LLVM_PREBUILTS_C_INCLUDE = "{}"
LLVM_PREBUILTS_CPP_INCLUDE = "{}"
""".format(
        "{}/include".format(llvm_resource_dir),
        _abs_path(repo_ctx, "llvm-linux-x86/include/c++/v1"),
    )

    # Linux sysroot headers
    sysroot_includes = [
        _abs_path(repo_ctx, "linux_glibc/sysroot/usr/include"),
        _abs_path(repo_ctx, "linux_glibc/sysroot/usr/include/x86_64-linux-gnu"),
    ]
    info_bzl_content += """
LINUX_SYSROOT_INCLUDES = [{}]
""".format(",".join(["\"{}\"".format(inc) for inc in sysroot_includes]))

    repo_ctx.file("info.bzl", info_bzl_content)

    # The following files are needed for defining bindgen toolchain, we symlink them out to the
    # top level directory in case the the distribution repo has its own BUILD file which blocks
    # direct access.
    repo_ctx.symlink("llvm-linux-x86/bin/clang", "clang")

    # In some prebuilt versions, "libc++.so" is a symlink to "libc++.so.1" etc. We need to use the
    # same name as the actual library file name in cc_import(). Otherwise it complains it can't
    # find the shared object.
    libcpp_sharelib_path = _abs_path(repo_ctx, "llvm-linux-x86/lib/libc++.so")
    libcpp_base_name = repo_ctx.execute(["basename", libcpp_sharelib_path]).stdout.strip("\n")
    repo_ctx.symlink(libcpp_sharelib_path, libcpp_base_name)

    # Do the same for libclang.so in case it's a symlink.
    libclang_sharelib_path = _abs_path(repo_ctx, "llvm-linux-x86/lib/libclang.so")
    libclang_basename = repo_ctx.execute(["basename", libclang_sharelib_path]).stdout.strip("\n")
    repo_ctx.symlink(libclang_sharelib_path, libclang_basename)

    # Add a BUILD file to make it a package
    repo_ctx.file("BUILD", """
package(
    default_visibility = ["//visibility:public"],
)

exports_files(glob(["**/*"]))

sh_binary(
    name = "clang-bin",
    srcs = [":clang"],
)

cc_import(
    name = "libc++",
    shared_library = ":{}",
)

cc_import(
    name = "libclang",
    shared_library = ":{}",
)
""".format(libcpp_base_name, libclang_basename))

gbl_llvm_prebuilts = repository_rule(
    implementation = _gbl_llvm_prebuilts_impl,
    local = True,
    environ = ["GBL_LLVM_PREBUILTS", "GBL_LINUX_SYSROOT"],
)

def _android_rust_prebuilts_impl(repo_ctx):
    """Assemble a rust toolchain repo from the Android rust prebuilts repo.

    The Android rust prebuilts repo is expected to be from
    https://android.googlesource.com/platform/prebuilts/rust/. The repository rule auto-detects
    the current version of toolchain from the repo.

    Attributes:
        path (String): Relative path to the Android rust prebuilts repo.
        build_file (Label): Label of the build file to use.
    """

    # We only support linux x86 platform.
    path = repo_ctx.workspace_root.get_child(repo_ctx.attr.path).get_child("linux-x86")

    # Scan sub directoires
    curr_ver = None
    for entry in [ele.basename for ele in path.readdir()]:
        # Different versions of toolchains are organized into their own subdirs named afer the
        # version i.e. "1.71.0/", "1.72.0/" etc.
        arr = entry.split(".")
        if len(arr) == 3 and all([ele.isdigit() for ele in arr]):
            curr_ver = max(curr_ver or entry, entry)

    if not curr_ver:
        fail("Failed to find rust toolchain prebuilt from {}".format(path))

    # Symlink everything into the assembled repo.
    path = path.get_child(curr_ver)
    for entry in path.readdir():
        # Ignore native BUILD file as we'll use override from `ctx.attr.build_file` instead.
        if entry.basename == "BUILD" or entry.basename == "BUILD.bazel":
            continue
        repo_ctx.symlink(entry.realpath, entry.basename)

    # Symlink the provided build file
    repo_ctx.symlink(repo_ctx.attr.build_file, "BUILD")

android_rust_prebuilts = repository_rule(
    implementation = _android_rust_prebuilts_impl,
    attrs = {
        "path": attr.string(mandatory = True),
        "build_file": attr.label(mandatory = True),
    },
)
