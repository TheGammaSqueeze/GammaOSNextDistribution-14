#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Runs a tryjob/tryjobs after updating the packages."""

import argparse
import datetime
import json
import os
from pathlib import Path
import subprocess
from typing import Any, Dict, Iterable, List, Optional, Union

import chroot
import failure_modes
import get_llvm_hash
import update_chromeos_llvm_hash


VALID_CQ_TRYBOTS = ("llvm", "llvm-next")


def GetCommandLineArgs() -> argparse.Namespace:
    """Parses the command line for the command line arguments.

    Returns:
        The log level to use when retrieving the LLVM hash or google3 LLVM
        version, the chroot path to use for executing chroot commands,
        a list of a package or packages to update their LLVM next hash,
        and the LLVM version to use when retrieving the LLVM hash.
    """

    # Default path to the chroot if a path is not specified.
    cros_root = os.path.expanduser("~")
    cros_root = os.path.join(cros_root, "chromiumos")

    # Create parser and add optional command-line arguments.
    parser = argparse.ArgumentParser(
        description="Update an LLVM hash of packages and run tests."
    )

    # Add argument for other change lists that want to run alongside the tryjob
    # which has a change list of updating a package's git hash.
    parser.add_argument(
        "--extra_change_lists",
        type=int,
        nargs="+",
        default=[],
        help="change lists that would like to be run alongside the change list "
        "of updating the packages",
    )

    # Add argument for a specific chroot path.
    parser.add_argument(
        "--chroot_path",
        default=cros_root,
        help="the path to the chroot (default: %(default)s)",
    )

    # Add argument to choose between llvm and llvm-next.
    parser.add_argument(
        "--is_llvm_next",
        action="store_true",
        help="which llvm hash to update. Update LLVM_NEXT_HASH if specified. "
        "Otherwise, update LLVM_HASH",
    )

    # Add argument for the absolute path to the file that contains information
    # on the previous tested svn version.
    parser.add_argument(
        "--last_tested",
        help="the absolute path to the file that contains the last tested "
        "arguments.",
    )

    # Add argument for the LLVM version to use.
    parser.add_argument(
        "--llvm_version",
        type=get_llvm_hash.IsSvnOption,
        required=True,
        help="which git hash of LLVM to find "
        "{google3, ToT, <svn_version>} "
        "(default: finds the git hash of the google3 LLVM "
        "version)",
    )

    # Add argument to add reviewers for the created CL.
    parser.add_argument(
        "--reviewers",
        nargs="+",
        default=[],
        help="The reviewers for the package update changelist",
    )

    subparsers = parser.add_subparsers(dest="subparser_name")
    subparser_names = []
    # Testing with the tryjobs.
    tryjob_subparser = subparsers.add_parser("tryjobs")
    subparser_names.append("tryjobs")
    tryjob_subparser.add_argument(
        "--builders",
        required=True,
        nargs="+",
        default=[],
        help="builders to use for the tryjob testing",
    )

    # Add argument for custom options for the tryjob.
    tryjob_subparser.add_argument(
        "--options",
        required=False,
        nargs="+",
        default=[],
        help="options to use for the tryjob testing",
    )

    # Testing with the recipe builders
    recipe_subparser = subparsers.add_parser("recipe")
    subparser_names.append("recipe")
    recipe_subparser.add_argument(
        "--options",
        required=False,
        nargs="+",
        default=[],
        help="options passed to the recipe builders",
    )

    recipe_subparser.add_argument(
        "--builders",
        required=True,
        nargs="+",
        default=[],
        help="recipe builders to launch",
    )

    # Testing with CQ.
    cq_subparser = subparsers.add_parser("cq")
    subparser_names.append("cq")

    # Add argument for specify a cq trybot to test along with other cq builders
    # e.g. llvm, llvm-next or llvm-tot
    cq_subparser.add_argument(
        "--cq_trybot",
        choices=VALID_CQ_TRYBOTS,
        help="include the trybot to test together with other cq builders "
        "available: %(choices)s",
    )

    args_output = parser.parse_args()

    if args_output.subparser_name not in subparser_names:
        parser.error("one of %s must be specified" % subparser_names)

    return args_output


def UnchangedSinceLastRun(
    last_tested_file: Optional[Union[Path, str]],
    arg_dict: Dict,
) -> bool:
    """Gets the arguments used for last run

    Args:
        last_tested_file: The absolute path to the file that contains the
          arguments for the last run.
        arg_dict: The arguments used for this run.

    Returns:
        Return true if the arguments used for last run exist and are the
        same as the arguments used for this run. Otherwise return false.
    """

    if not last_tested_file:
        return False

    # Get the last tested svn version if the file exists.
    last_arg_dict = None
    try:
        with open(last_tested_file, encoding="utf-8") as f:
            last_arg_dict = json.load(f)

    except (IOError, ValueError):
        return False

    return arg_dict == last_arg_dict


def AddReviewers(
    cl: int,
    reviewers: Iterable[str],
    chroot_path: Union[Path, str],
) -> None:
    """Add reviewers for the created CL.

    Args:
        cl: The CL number to add reviewers to.
        reviewers: Email addresses of reviewers to add.
        chroot_path: The absolute path to the chroot.
    """

    gerrit_abs_path = os.path.join(chroot_path, "chromite/bin/gerrit")
    for reviewer in reviewers:
        cmd = [gerrit_abs_path, "reviewers", str(cl), reviewer]

        subprocess.check_output(cmd)


def AddLinksToCL(
    tests: Iterable[Dict[str, Any]],
    cl: int,
    chroot_path: Union[Path, str],
) -> None:
    """Adds the test link(s) to the CL as a comment.

    Args:
        tests: Links to the tests.
        cl: The number of the CL to add the test links to.
        chroot_path: Absolute path to the chroot.
    """

    # NOTE: Invoking `cros_sdk` does not make each tryjob link appear on its
    # own line, so invoking the `gerrit` command directly instead of using
    # `cros_sdk` to do it for us.
    #
    # FIXME: Need to figure out why `cros_sdk` does not add each tryjob link as
    # a newline.
    gerrit_abs_path = os.path.join(chroot_path, "chromite/bin/gerrit")

    links = ["Started the following tests:"]
    links.extend(test["link"] for test in tests)

    add_message_cmd = [gerrit_abs_path, "message", str(cl), "\n".join(links)]

    subprocess.check_output(add_message_cmd)


# Testing with tryjobs
def GetCurrentTimeInUTC() -> datetime.datetime:
    """Returns the current time via `datetime.datetime.utcnow()`."""
    return datetime.datetime.utcnow()


def GetTryJobCommand(
    change_list: int,
    extra_change_lists: Iterable[int],
    options: Iterable[str],
    builder: str,
) -> List[str]:
    """Constructs the 'tryjob' command.

    Args:
        change_list: The CL obtained from updating the packages.
        extra_change_lists: Extra change lists that would like to be run
          alongside the change list of updating the packages.
        options: Options to be passed into the tryjob command.
        builder: The builder to be passed into the tryjob command.

    Returns:
        The 'tryjob' command with the change list of updating the packages and
        any extra information that was passed into the command line.
    """

    tryjob_cmd = ["cros", "tryjob", "--yes", "--json", "-g", "%d" % change_list]

    if extra_change_lists:
        for extra_cl in extra_change_lists:
            tryjob_cmd.extend(["-g", "%d" % extra_cl])

    if options:
        tryjob_cmd.extend("--%s" % option for option in options)

    tryjob_cmd.append(builder)

    return tryjob_cmd


def RunTryJobs(
    cl_number: int,
    extra_change_lists: List[int],
    options: List[str],
    builders: Iterable[str],
    chroot_path: Union[Path, str],
) -> List[Dict]:
    """Runs a tryjob/tryjobs.

    Args:
        cl_number: The CL created by updating the packages.
        extra_change_lists: Any extra change lists that would run alongside the
          CL that was created by updating the packages ('cl_number').
        options: Any options to be passed into the 'tryjob' command.
        builders: All the builders to run the 'tryjob' with.
        chroot_path: The absolute path to the chroot.

    Returns:
        A list that contains stdout contents of each tryjob, where stdout is
        information (a hashmap) about the tryjob. The hashmap also contains
        stderr if there was an error when running a tryjob.

    Raises:
        ValueError: Failed to submit a tryjob.
    """

    # Contains the results of each builder.
    tests = []

    # Run tryjobs with the change list number obtained from updating the
    # packages and append additional changes lists and options obtained from the
    # command line.
    for builder in builders:
        cmd = GetTryJobCommand(cl_number, extra_change_lists, options, builder)

        out = subprocess.check_output(cmd, cwd=chroot_path, encoding="utf-8")

        test_output = json.loads(out)

        buildbucket_id = int(test_output[0]["id"])

        tests.append(
            {
                "launch_time": str(GetCurrentTimeInUTC()),
                "link": "http://ci.chromium.org/b/%s" % buildbucket_id,
                "buildbucket_id": buildbucket_id,
                "extra_cls": extra_change_lists,
                "options": options,
                "builder": [builder],
            }
        )

    AddLinksToCL(tests, cl_number, chroot_path)

    return tests


def StartRecipeBuilders(
    cl_number: int,
    extra_change_lists: List[int],
    options: List[str],
    builders: List[str],
    chroot_path: Union[Path, str],
) -> List[Dict]:
    """Launch recipe builders.

    Args:
        cl_number: The CL created by updating the packages.
        extra_change_lists: Any extra change lists that would run alongside the
          CL that was created by updating the packages ('cl_number').
        options: Any options to be passed into the 'tryjob' command.
        builders: All the builders to run the 'tryjob' with.
        chroot_path: The absolute path to the chroot.

    Returns:
        A list that contains stdout contents of each builder, where stdout is
        information (a hashmap) about the tryjob. The hashmap also contains
        stderr if there was an error when running a tryjob.

    Raises:
        ValueError: Failed to start a builder.
    """

    # Contains the results of each builder.
    tests = []

    # Launch a builders with the change list number obtained from updating the
    # packages and append additional changes lists and options obtained from the
    # command line.
    for builder in builders:
        cmd = ["bb", "add", "-json"]

        if cl_number:
            cmd.extend(["-cl", "crrev.com/c/%d" % cl_number])

        if extra_change_lists:
            for cl in extra_change_lists:
                cmd.extend(["-cl", "crrev.com/c/%d" % cl])

        if options:
            cmd.extend(options)

        cmd.append(builder)

        out = subprocess.check_output(cmd, cwd=chroot_path, encoding="utf-8")

        test_output = json.loads(out)

        tests.append(
            {
                "launch_time": test_output["createTime"],
                "link": "http://ci.chromium.org/b/%s" % test_output["id"],
                "buildbucket_id": test_output["id"],
                "extra_cls": extra_change_lists,
                "options": options,
                "builder": [builder],
            }
        )

    AddLinksToCL(tests, cl_number, chroot_path)

    return tests


# Testing with CQ
def GetCQDependString(dependent_cls: List[int]) -> Optional[str]:
    """Get CQ dependency string e.g. `Cq-Depend: chromium:MM, chromium:NN`."""

    if not dependent_cls:
        return None

    # Cq-Depend must start a new paragraph prefixed with "Cq-Depend".
    return "Cq-Depend: " + ", ".join(f"chromium:{x}" for x in dependent_cls)


def GetCQIncludeTrybotsString(trybot: Optional[str]) -> Optional[str]:
    """Get Cq-Include-Trybots string, for more llvm testings"""

    if not trybot:
        return None

    if trybot not in VALID_CQ_TRYBOTS:
        raise ValueError("%s is not a valid llvm trybot" % trybot)

    # Cq-Include-Trybots must start a new paragraph prefixed
    # with "Cq-Include-Trybots".
    return "Cq-Include-Trybots:chromeos/cq:cq-%s-orchestrator" % trybot


def StartCQDryRun(
    cl: int,
    dependent_cls: List[int],
    chroot_path: Union[Path, str],
) -> None:
    """Start CQ dry run for the changelist and dependencies."""

    gerrit_abs_path = os.path.join(chroot_path, "chromite/bin/gerrit")

    cl_list = [cl]
    cl_list.extend(dependent_cls)

    for changes in cl_list:
        cq_dry_run_cmd = [gerrit_abs_path, "label-cq", str(changes), "1"]

        subprocess.check_output(cq_dry_run_cmd)


def main():
    """Updates the packages' LLVM hash and run tests.

    Raises:
        AssertionError: The script was run inside the chroot.
    """

    chroot.VerifyOutsideChroot()

    args_output = GetCommandLineArgs()

    chroot.VerifyChromeOSRoot(args_output.chroot_path)

    svn_option = args_output.llvm_version

    git_hash, svn_version = get_llvm_hash.GetLLVMHashAndVersionFromSVNOption(
        svn_option
    )

    # There is no need to run tryjobs when all the key parameters remain
    # unchanged from last time.

    # If --last_tested is specified, check if the current run has the same
    # arguments last time --last_tested is used.
    if args_output.last_tested:
        chroot_file_paths = chroot.GetChrootEbuildPaths(
            args_output.chroot_path, update_chromeos_llvm_hash.DEFAULT_PACKAGES
        )
        arg_dict = {
            "svn_version": svn_version,
            "ebuilds": chroot_file_paths,
            "extra_cls": args_output.extra_change_lists,
        }
        if args_output.subparser_name in ("tryjobs", "recipe"):
            arg_dict["builders"] = args_output.builders
            arg_dict["tryjob_options"] = args_output.options
        if UnchangedSinceLastRun(args_output.last_tested, arg_dict):
            print(
                "svn version (%d) matches the last tested svn version in %s"
                % (svn_version, args_output.last_tested)
            )
            return

    llvm_variant = update_chromeos_llvm_hash.LLVMVariant.current
    if args_output.is_llvm_next:
        llvm_variant = update_chromeos_llvm_hash.LLVMVariant.next

    extra_commit_msg_lines = []
    if args_output.subparser_name == "cq":
        footers = []
        cq_depend_msg = GetCQDependString(args_output.extra_change_lists)
        if cq_depend_msg:
            footers.append(cq_depend_msg)
        cq_trybot_msg = GetCQIncludeTrybotsString(args_output.cq_trybot)
        if cq_trybot_msg:
            footers.append(cq_trybot_msg)

        # We want a single blank line before any of these, so Git properly
        # interprets them as a footer.
        if footers:
            extra_commit_msg_lines.append("")
            extra_commit_msg_lines += footers

    change_list = update_chromeos_llvm_hash.UpdatePackages(
        packages=update_chromeos_llvm_hash.DEFAULT_PACKAGES,
        manifest_packages=[],
        llvm_variant=llvm_variant,
        git_hash=git_hash,
        svn_version=svn_version,
        chroot_path=Path(args_output.chroot_path),
        mode=failure_modes.FailureModes.DISABLE_PATCHES,
        git_hash_source=svn_option,
        extra_commit_msg_lines=extra_commit_msg_lines,
    )

    AddReviewers(
        change_list.cl_number, args_output.reviewers, args_output.chroot_path
    )

    print("Successfully updated packages to %d" % svn_version)
    print("Gerrit URL: %s" % change_list.url)
    print("Change list number: %d" % change_list.cl_number)

    if args_output.subparser_name == "tryjobs":
        tests = RunTryJobs(
            change_list.cl_number,
            args_output.extra_change_lists,
            args_output.options,
            args_output.builders,
            args_output.chroot_path,
        )
        print("Tests:")
        for test in tests:
            print(test)
    elif args_output.subparser_name == "recipe":
        tests = StartRecipeBuilders(
            change_list.cl_number,
            args_output.extra_change_lists,
            args_output.options,
            args_output.builders,
            args_output.chroot_path,
        )
        print("Tests:")
        for test in tests:
            print(test)

    else:
        StartCQDryRun(
            change_list.cl_number,
            args_output.extra_change_lists,
            args_output.chroot_path,
        )

    # If --last_tested is specified, record the arguments used
    if args_output.last_tested:
        with open(args_output.last_tested, "w", encoding="utf-8") as f:
            json.dump(arg_dict, f, indent=2)


if __name__ == "__main__":
    main()
