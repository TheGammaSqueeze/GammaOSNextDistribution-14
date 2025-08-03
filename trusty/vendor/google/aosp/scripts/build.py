#!/bin/sh
#
# Copyright (C) 2018 The Android Open Source Project
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
#
""":" # Shell script (in docstring to appease pylint)
# Find and invoke hermetic python3 interpreter
. "`dirname $0`/envsetup.sh"; exec "$PY3" "$0" "$@"
# Shell script end

Invoke trusty build system and run tests.
"""

import argparse
import getpass
import multiprocessing
import os
import pathlib
import re
import shutil
import subprocess
import sys
from zipfile import ZipFile, ZIP_DEFLATED

import run_tests
import trusty_build_config

script_dir = os.path.dirname(os.path.abspath(__file__))

SDK_README_PATH = "trusty/user/base/sdk/README.md"
TRUSTED_APP_MAKEFILE_PATH = "trusty/user/base/make/trusted_app.mk"
TRUSTED_LOADABLE_APP_MAKEFILE_PATH = "trusty/kernel/make/loadable_app.mk"
GEN_MANIFEST_MAKEFILE_PATH = "trusty/user/base/make/gen_manifest.mk"


def get_new_build_id(build_root):
    """Increment build-id file and return new build-id number."""
    path = os.path.join(build_root, "BUILDID")
    try:
        with open(path, "r", encoding="utf-8") as f:
            num = int(f.read()) + 1
    except IOError:
        num = 1
    with open(path, "w", encoding="utf-8") as f:
        f.write(str(num))
        f.truncate()
        # Return buildid string: <user>@<hostname>-<num>
        # Use getpass.getuser() to avoid non-portability/failure of
        # os.getlogin()
        return getpass.getuser() + "@" + os.uname()[1] + "-" + str(num)


def mkdir(path):
    """Create directory including parents if it does not already exist."""
    try:
        os.makedirs(path)
    except OSError:
        if not os.path.isdir(path):
            raise


def copy_file(src, dest, optional=False):
    """Copy a file.

    Copy a file or exit if the file cannot be copied.

    Args:
       src: Path of file to copy.
       dest: Path to copy file to.
       optional: Optional boolean argument. If True don't exit if source file
           does not exist.
    """
    if not os.path.exists(src) and optional:
        return
    print("Copy:", repr(src), "->", repr(dest))
    shutil.copy(src, dest)


def archive_build_file(args, project, src, dest=None, optional=False):
    """Copy a file to build archive directory.

    Construct src and dest path and call copy_file.

    Args:
       args: Program arguments.
       project: Project name.
       src: Source path relative to project build dir.
       dest: Optional dest path relative to archive dir. Can be omitted if src
           is a simple filename.
       optional: Optional boolean argument. If True don't exit if source file
           does not exist.
    """
    if not dest:
        dest = src
    src = os.path.join(args.build_root, "build-" + project, src)
    # dest must be a fixed path for repeated builds of the same artifact
    # for compatibility with prebuilt update scripts.
    # Project is fine because that specifies what artifact is being looked
    # for - LK for a specific target.
    # BUILD_ID or feature selections that may change are not, because the
    # prebuilt update script cannot predict the path at which the artifact
    # will live.
    dest = os.path.join(args.archive, project + "." + dest)
    copy_file(src, dest, optional=optional)


def is_child_of_any(path, possible_parents):
    for possible_parent in possible_parents:
        if path.startswith(possible_parent):
            return True
    return False


def archive_dir(zip_archive, src, dest, omit=()):
    """Recursively add a directory to a ZIP file.

    Recursively add the src directory to the ZIP with dest path inside the
    archive.

    Args:
       zip_archive: A ZipFile opened for append or write.
       src: Source directory to add to the archive.
       dest: Destination path inside the archive (must be a relative path).
       omit: List of directorys to omit from the archive. Specified as relative
           paths from `src`.
    """
    for root, _dirs, files in os.walk(src):
        rel_root = os.path.relpath(root, start=src)
        if is_child_of_any(rel_root, omit):
            continue

        for f in files:
            file_path = os.path.join(root, f)
            archive_dest = os.path.join(dest, os.path.relpath(file_path,
                                                              start=src))
            zip_archive.write(file_path, archive_dest)

def archive_file(zip_archive, src_file, dest_dir="", optional=False):
    """Add a file to a ZIP file.

    Adds src_file to archive in the directory dest_dir, relative to the root of
    the archive.

    Args:
       zip_archive: A ZipFile opened for append or write.
       src_file: Source file to add to the archive.
       dest_dir: Relative destination path in the archive for this file.
       optional: Optional boolean argument. If True don't exit if source file
           does not exist.
    """
    if not os.path.exists(src_file) and optional:
        return
    zip_archive.write(src_file,
                      os.path.join(dest_dir, os.path.basename(src_file)))


def assemble_sdk(build_config, args):
    """Assemble Trusty SDK archive"""
    filename = os.path.join(args.archive, "trusty_sdk-" + args.buildid + ".zip")
    with ZipFile(filename, 'a', compression=ZIP_DEFLATED) as sdk_archive:
        print("Building SDK archive ZIP...")
        for project in args.project:
            project_buildroot = os.path.join(args.build_root,
                                             "build-" + project)

            project_sysroot_dir = os.path.join("sysroots", project, "usr")
            src = os.path.join(project_buildroot, "sdk", "sysroot", "usr")
            archive_dir(sdk_archive, src, project_sysroot_dir, omit=["lib/doc"])

            src = os.path.join(project_buildroot, "sdk", "LICENSE")
            archive_file(sdk_archive, src)

            project_makefile_dir = os.path.join("make", project)
            src = os.path.join(project_buildroot, "sdk", "make")
            archive_dir(sdk_archive, src, project_makefile_dir)

            project_tools_dir = os.path.join("sysroots", project, "tools")
            src = os.path.join(project_buildroot, "host_tools",
                               "apploader_package_tool")
            archive_file(sdk_archive, src, project_tools_dir, optional=True)

            src = os.path.join(project_buildroot, "sdk", "tools",
                               "manifest_compiler.py")
            archive_file(sdk_archive, src, project_tools_dir)

            project_keys = build_config.signing_keys(project)
            for filename in project_keys:
                archive_file(sdk_archive, filename, project_tools_dir)

        # Copy the app makefile
        archive_file(sdk_archive, TRUSTED_APP_MAKEFILE_PATH, "make")
        archive_file(sdk_archive, TRUSTED_LOADABLE_APP_MAKEFILE_PATH, "make")
        archive_file(sdk_archive, GEN_MANIFEST_MAKEFILE_PATH, "make")

        # Copy SDK README
        archive_file(sdk_archive, SDK_README_PATH)

        # Add clang version info
        envsetup = os.path.join(script_dir, 'envsetup.sh')
        cmd = f"source {envsetup} && echo $CLANG_BINDIR"
        clang_bindir = subprocess.check_output(
            cmd, shell=True, executable="/bin/bash").decode().strip()
        clang_dir = os.path.join(clang_bindir, "../")

        cmd = f"cd {clang_dir}; git rev-parse HEAD"
        clang_prebuilt_commit = subprocess.check_output(
            cmd, shell=True, executable="/bin/bash").decode().strip()

        archive_file(sdk_archive,
                     os.path.join(clang_dir, "AndroidVersion.txt"), "clang")
        archive_file(sdk_archive,
                     os.path.join(clang_dir, "clang_source_info.md"), "clang")
        sdk_archive.writestr(
            os.path.join("clang", "PrebuiltCommitId.txt"),
            clang_prebuilt_commit)

        # Add trusty version info
        sdk_archive.writestr("Version.txt", args.buildid)


def build(args):
    """Call build system and copy build files to archive dir."""
    mkdir(args.build_root)

    if args.buildid is None:
        args.buildid = get_new_build_id(args.build_root)
    print("BuildID", args.buildid)

    # build projects
    failed = []

    for project in args.project:
        cmd = (f'export BUILDROOT={args.build_root};'
               f'export BUILDID={args.buildid}; nice make {project} '
               f'-f external/lk/makefile -j {args.jobs}')
        # Call envsetup.  If it fails, abort.
        envsetup = os.path.join(script_dir, "envsetup.sh")
        cmd = f"source {envsetup:s} && ({cmd:s})"
        status = subprocess.call(cmd, shell=True, executable="/bin/bash")
        print("cmd: '" + cmd + "' returned", status)
        if status:
            failed.append(project)

    if failed:
        print()
        print("some projects have failed to build:")
        print(str(failed))
        sys.exit(1)


def zip_dir(zip_archive, src, dest, filterfunc=lambda _: True):
    """Recursively add a directory to a ZIP file.

    Recursively add the src directory to the ZIP with dest path inside the
    archive.

    Args:
       zip_archive: A ZipFile opened for append or write.
       src: Source directory to add to the archive.
       dest: Destination path inside the archive (must be a relative path).
    """
    for root, _dirs, files in os.walk(src):
        for f in files:
            if not filterfunc(f):
                continue
            file_path = os.path.join(root, f)
            archive_dest = os.path.join(dest,
                                        os.path.relpath(file_path, start=src))
            zip_archive.write(file_path, archive_dest)


def zip_file(zip_archive, src_file, dest_dir=""):
    """Add a file to a ZIP file.

    Adds src_file to archive in the directory dest_dir, relative to the root of
    the archive.

    Args:
       zip_archive: A ZipFile opened for append or write.
       src_file: Source file to add to the archive.
       dest_dir: Relative destination path in the archive for this file.
    """
    zip_archive.write(src_file,
                      os.path.join(dest_dir, os.path.basename(src_file)))


def archive_symbols(args, project):
    """Archive symbol files for the kernel and each trusted app"""
    proj_buildroot = os.path.join(args.build_root, "build-" + project)
    filename = os.path.join(args.archive, f"{project}-{args.buildid}.syms.zip")

    with ZipFile(filename, 'a', compression=ZIP_DEFLATED) as zip_archive:
        print("Archiving symbols in " + os.path.relpath(filename, args.archive))

        # archive the kernel elf file
        zip_file(zip_archive, os.path.join(proj_buildroot, "lk.elf"))

        # archive the kernel symbols
        zip_file(zip_archive, os.path.join(proj_buildroot, "lk.elf.sym"))
        zip_file(zip_archive, os.path.join(proj_buildroot, "lk.elf.sym.sorted"))

        # archive path/to/app.syms.elf for each trusted app
        zip_dir(zip_archive, proj_buildroot, "",
                lambda f: f.endswith("syms.elf"))


def create_uuid_map(args, project):
    """Creating a mapping txt file for uuid and symbol files"""

    def time_from_bytes(f, n: int) -> str:
        """Read n bytes from f as an int, and convert that int to a string."""
        time = int.from_bytes(f.read(n), byteorder='little')
        width = 2 * n
        return f'{time:0{width}x}'

    proj_buildroot = os.path.join(args.build_root, "build-" + project)
    filename = os.path.join(args.archive, f"{project}-{args.buildid}-uuid-map.txt")
    zipfile = os.path.join(args.archive, f"{project}-{args.buildid}.syms.zip")
    sym_files = list(pathlib.Path(proj_buildroot).rglob("*.syms.elf"))

    for file in sym_files:
        folder = file.parents[0]
        manifest_files = list(pathlib.Path(folder).glob("*.manifest"))
        if len(manifest_files) == 1:
            manifest = manifest_files[0]
            with open(manifest, 'rb') as f:
                time_low = time_from_bytes(f, 4)
                time_mid = time_from_bytes(f, 2)
                time_hi_and_version = time_from_bytes(f, 2)
                clock_seq_and_node = [time_from_bytes(f, 1) for _ in range(8)]
                uuid_str = (f'{time_low}-{time_mid}-{time_hi_and_version}-'
                            f'{clock_seq_and_node[0]}{clock_seq_and_node[1]}-'
                            f'{clock_seq_and_node[2]}{clock_seq_and_node[3]}'
                            f'{clock_seq_and_node[4]}{clock_seq_and_node[5]}'
                            f'{clock_seq_and_node[6]}{clock_seq_and_node[7]}')
            with open(filename, 'a', encoding='utf-8') as f:
                f.write(f'{uuid_str}, {file.relative_to(proj_buildroot)}\n')

    if os.path.exists(filename):
        with ZipFile(zipfile, 'a', compression=ZIP_DEFLATED) as zip_archive:
            zip_file(zip_archive, filename)
        os.remove(filename)

def create_scripts_archive(args, project):
    """Create an archive for the scripts"""
    coverage_script = os.path.join(script_dir, "genReport.py")
    scripts_zip = os.path.join(args.archive, "scripts-" + args.buildid + ".zip")
    if not os.path.exists(coverage_script):
        print("Coverage script does not exist!")
        return

    with ZipFile(scripts_zip, 'a', compression=ZIP_DEFLATED) as zip_archive:
        zip_file(zip_archive, coverage_script)

def archive(build_config, args):
    if args.archive is None:
        return

    mkdir(args.archive)

    # Copy the files we care about to the archive directory
    for project in args.project:
        # config-driven archiving
        for item in build_config.dist:
            archive_build_file(args, project, item.src, item.dest,
                               optional=item.optional)

        # copy out tos.img if it exists
        archive_build_file(args, project, "tos.img", optional=True)

        # copy out monitor if it exists
        archive_build_file(args, project, "monitor/monitor.bin", "monitor.bin",
                           optional=True)

        # copy out trusty.padded if it exists
        archive_build_file(args, project, "trusty.padded", optional=True)

        # copy out trusty.signed if it exists
        archive_build_file(args, project, "trusty.signed", optional=True)

        # copy out trusty_usb.signed if it exists
        archive_build_file(args, project, "trusty_usb.signed", optional=True)

        # copy out lk image
        archive_build_file(args, project, "lk.bin")

        # copy out qemu package if it exists
        archive_build_file(args, project, "trusty_qemu_package.zip",
                           optional=True)

        # copy out test package if it exists
        archive_build_file(args, project, "trusty_test_package.zip",
                           optional=True)

        # export the app package tool for use in the SDK. This can go away once
        # all the SDK patches have landed, as the tool will be packaged in the
        # SDK zip.
        archive_build_file(args, project, "host_tools/apploader_package_tool",
                           "apploader_package_tool", optional=True)

        # copy out symbol files for kernel and apps
        archive_symbols(args, project)

        # create map between UUID and symbolic files
        create_uuid_map(args, project)

        # create zip file containing scripts
        create_scripts_archive(args, project)

    # create sdk zip
    assemble_sdk(build_config, args)


def get_build_deps(project_name, project, project_names, already_built):
    if project_name not in already_built:
        already_built.add(project_name)
        for dep_project_name, dep_project in project.also_build.items():
            get_build_deps(dep_project_name, dep_project, project_names,
                           already_built)
        project_names.append(project_name)


def main(default_config=None):
    top = os.path.abspath(os.path.join(script_dir, "../../../../.."))
    os.chdir(top)

    parser = argparse.ArgumentParser()

    parser.add_argument("project", type=str, nargs="*", default=[".test.all"],
                        help="Project to build and/or test.")
    parser.add_argument("--build-root", type=str,
                        default=os.path.join(top, "build-root"),
                        help="Root of intermediate build directory.")
    parser.add_argument("--archive", type=str, default=None,
                        help="Location of build artifacts directory. If "
                        "omitted, no artifacts will be produced.")
    parser.add_argument("--buildid", type=str, help="Server build id")
    parser.add_argument("--jobs", type=str, default=multiprocessing.cpu_count(),
                        help="Max number of build jobs.")
    parser.add_argument("--test", type=str, action="append",
                        help="Manually specify test(s) to run. "
                        "Only build projects that have test(s) enabled that "
                        "matches a listed regex.")
    parser.add_argument("--verbose", action="store_true",
                        help="Verbose debug output from test(s).")
    parser.add_argument("--debug-on-error", action="store_true",
                        help="Wait for debugger connection if test fails.")
    parser.add_argument("--clang", action="store_true", default=None,
                        help="Build with clang.")
    parser.add_argument("--skip-build", action="store_true", help="Skip build.")
    parser.add_argument("--skip-tests", action="store_true",
                        help="Skip running tests.")
    parser.add_argument("--run-disabled-tests", action="store_true",
                        help="Also run disabled tests.")
    parser.add_argument("--skip-project", action="append", default=[],
                        help="Remove project from projects being built.")
    parser.add_argument("--config", type=str, help="Path to an alternate "
                        "build-config file.", default=default_config)
    parser.add_argument("--android", type=str,
                        help="Path to an Android build to run tests against.")
    args = parser.parse_args()

    build_config = trusty_build_config.TrustyBuildConfig(
        config_file=args.config, android=args.android)

    projects = []
    for project in args.project:
        if project == ".test.all":
            projects += build_config.get_projects(build=True)
        elif project == ".test":
            projects += build_config.get_projects(build=True, have_tests=True)
        else:
            projects.append(project)

    # skip specific projects
    ok = True
    for skip in args.skip_project:
        if skip in projects:
            projects.remove(skip)
        else:
            sys.stderr.write(f"ERROR unknown project --skip-project={skip}\n")
            ok = False
    if not ok:
        sys.exit(1)

    # If there's any test filters, ignore projects that don't have
    # any tests that match those filters.
    test_filters = ([re.compile(test) for test in args.test]
                    if args.test else None)
    if test_filters:
        projects = run_tests.projects_to_test(
            build_config, projects, test_filters,
            run_disabled_tests=args.run_disabled_tests)

    # find build dependencies
    projects_old = projects
    projects = []
    built_projects = set()
    for project_name in projects_old:
        get_build_deps(project_name,
                       build_config.get_project(project_name),
                       projects,
                       built_projects)
    args.project = projects

    print("Projects", str(projects))

    if args.skip_build:
        print("Skip build for", args.project)
    else:
        build(args)
        archive(build_config, args)

    # Run tests
    if not args.skip_tests:
        test_result = run_tests.test_projects(
            build_config, args.build_root, projects,
            run_disabled_tests=args.run_disabled_tests,
            test_filters=test_filters,
            verbose=args.verbose,
            debug_on_error=args.debug_on_error)

        test_result.print_results()
        if test_result.failed_projects:
            sys.exit(1)


if __name__ == "__main__":
    main()
