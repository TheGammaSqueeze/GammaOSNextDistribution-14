#!/usr/bin/env python3
# Copyright 2019 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Modifies a tryjob based off of arguments."""

import argparse
import enum
import json
import os
from pathlib import Path
import sys
from typing import Dict, Iterable, List, Union

import chroot
import failure_modes
import get_llvm_hash
import git
import update_chromeos_llvm_hash
import update_packages_and_run_tests
import update_tryjob_status


class ModifyTryjob(enum.Enum):
    """Options to modify a tryjob."""

    REMOVE = "remove"
    RELAUNCH = "relaunch"
    ADD = "add"


def GetCommandLineArgs() -> argparse.Namespace:
    """Parses the command line for the command line arguments."""

    # Default path to the chroot if a path is not specified.
    cros_root = os.path.expanduser("~")
    cros_root = os.path.join(cros_root, "chromiumos")

    # Create parser and add optional command-line arguments.
    parser = argparse.ArgumentParser(
        description="Removes, relaunches, or adds a tryjob."
    )

    # Add argument for the JSON file to use for the update of a tryjob.
    parser.add_argument(
        "--status_file",
        required=True,
        help="The absolute path to the JSON file that contains the tryjobs "
        "used for bisecting LLVM.",
    )

    # Add argument that determines what action to take on the revision
    # specified.
    parser.add_argument(
        "--modify_tryjob",
        required=True,
        choices=[modify_tryjob.value for modify_tryjob in ModifyTryjob],
        help="What action to perform on the tryjob.",
    )

    # Add argument that determines which revision to search for in the list of
    # tryjobs.
    parser.add_argument(
        "--revision",
        required=True,
        type=int,
        help="The revision to either remove or relaunch.",
    )

    # Add argument for other change lists that want to run alongside the
    # tryjob.
    parser.add_argument(
        "--extra_change_lists",
        type=int,
        nargs="+",
        help="change lists that would like to be run alongside the change list "
        "of updating the packages",
    )

    # Add argument for custom options for the tryjob.
    parser.add_argument(
        "--options",
        required=False,
        nargs="+",
        help="options to use for the tryjob testing",
    )

    # Add argument for the builder to use for the tryjob.
    parser.add_argument(
        "--builder", help="builder to use for the tryjob testing"
    )

    # Add argument for a specific chroot path.
    parser.add_argument(
        "--chroot_path",
        default=cros_root,
        help="the path to the chroot (default: %(default)s)",
    )

    args_output = parser.parse_args()

    if not os.path.isfile(
        args_output.status_file
    ) or not args_output.status_file.endswith(".json"):
        raise ValueError(
            'File does not exist or does not ending in ".json" '
            ": %s" % args_output.status_file
        )

    if (
        args_output.modify_tryjob == ModifyTryjob.ADD.value
        and not args_output.builder
    ):
        raise ValueError("A builder is required for adding a tryjob.")
    elif (
        args_output.modify_tryjob != ModifyTryjob.ADD.value
        and args_output.builder
    ):
        raise ValueError(
            "Specifying a builder is only available when adding a " "tryjob."
        )

    return args_output


def GetCLAfterUpdatingPackages(
    packages: Iterable[str],
    git_hash: str,
    svn_version: int,
    chroot_path: Union[Path, str],
    svn_option: Union[int, str],
) -> git.CommitContents:
    """Updates the packages' LLVM_NEXT."""

    change_list = update_chromeos_llvm_hash.UpdatePackages(
        packages=packages,
        manifest_packages=[],
        llvm_variant=update_chromeos_llvm_hash.LLVMVariant.next,
        git_hash=git_hash,
        svn_version=svn_version,
        chroot_path=Path(chroot_path),
        mode=failure_modes.FailureModes.DISABLE_PATCHES,
        git_hash_source=svn_option,
        extra_commit_msg_lines=None,
    )

    # We are calling UpdatePackages with upload_changes=True, in
    # which case it should always return a git.CommitContents value.
    assert change_list is not None
    print("\nSuccessfully updated packages to %d" % svn_version)
    print("Gerrit URL: %s" % change_list.url)
    print("Change list number: %d" % change_list.cl_number)

    return change_list


def CreateNewTryjobEntryForBisection(
    cl: int,
    extra_cls: List[int],
    options: List[str],
    builder: str,
    chroot_path: Union[Path, str],
    cl_url: str,
    revision,
) -> Dict:
    """Submits a tryjob and adds additional information."""

    # Get the tryjob results after submitting the tryjob.
    # Format of 'tryjob_results':
    # [
    #   {
    #     'link' : [TRYJOB_LINK],
    #     'buildbucket_id' : [BUILDBUCKET_ID],
    #     'extra_cls' : [EXTRA_CLS_LIST],
    #     'options' : [EXTRA_OPTIONS_LIST],
    #     'builder' : [BUILDER_AS_A_LIST]
    #   }
    # ]
    tryjob_results = update_packages_and_run_tests.RunTryJobs(
        cl, extra_cls, options, [builder], chroot_path
    )
    print("\nTryjob:")
    print(tryjob_results[0])

    # Add necessary information about the tryjob.
    tryjob_results[0]["url"] = cl_url
    tryjob_results[0]["rev"] = revision
    tryjob_results[0][
        "status"
    ] = update_tryjob_status.TryjobStatus.PENDING.value
    tryjob_results[0]["cl"] = cl

    return tryjob_results[0]


def AddTryjob(
    packages: Iterable[str],
    git_hash: str,
    revision: int,
    chroot_path: Union[Path, str],
    extra_cls: List[int],
    options: List[str],
    builder: str,
    svn_option: Union[int, str],
):
    """Submits a tryjob."""

    change_list = GetCLAfterUpdatingPackages(
        packages,
        git_hash,
        revision,
        chroot_path,
        svn_option,
    )

    tryjob_dict = CreateNewTryjobEntryForBisection(
        change_list.cl_number,
        extra_cls,
        options,
        builder,
        chroot_path,
        change_list.url,
        revision,
    )

    return tryjob_dict


def PerformTryjobModification(
    revision: int,
    modify_tryjob: ModifyTryjob,
    status_file: Union[Path, str],
    extra_cls: List[int],
    options: List[str],
    builder: str,
    chroot_path: Union[Path, str],
) -> None:
    """Removes, relaunches, or adds a tryjob.

    Args:
        revision: The revision associated with the tryjob.
        modify_tryjob: What action to take on the tryjob.
          Ex: ModifyTryjob.REMOVE, ModifyTryjob.RELAUNCH, ModifyTryjob.ADD
        status_file: The .JSON file that contains the tryjobs.
        extra_cls: Extra change lists to be run alongside tryjob
        options: Extra options to pass into 'cros tryjob'.
        builder: The builder to use for 'cros tryjob'.
        chroot_path: The absolute path to the chroot (used by 'cros tryjob'
          when relaunching a tryjob).
    """

    # Format of 'bisect_contents':
    # {
    #   'start': [START_REVISION_OF_BISECTION]
    #   'end': [END_REVISION_OF_BISECTION]
    #   'jobs' : [
    #       {[TRYJOB_INFORMATION]},
    #       {[TRYJOB_INFORMATION]},
    #       ...,
    #       {[TRYJOB_INFORMATION]}
    #   ]
    # }
    with open(status_file, encoding="utf-8") as tryjobs:
        bisect_contents = json.load(tryjobs)

    if not bisect_contents["jobs"] and modify_tryjob != ModifyTryjob.ADD:
        sys.exit("No tryjobs in %s" % status_file)

    tryjob_index = update_tryjob_status.FindTryjobIndex(
        revision, bisect_contents["jobs"]
    )

    # 'FindTryjobIndex()' returns None if the tryjob was not found.
    if tryjob_index is None and modify_tryjob != ModifyTryjob.ADD:
        raise ValueError(
            "Unable to find tryjob for %d in %s" % (revision, status_file)
        )

    # Determine the action to take based off of 'modify_tryjob'.
    if modify_tryjob == ModifyTryjob.REMOVE:
        del bisect_contents["jobs"][tryjob_index]

        print("Successfully deleted the tryjob of revision %d" % revision)
    elif modify_tryjob == ModifyTryjob.RELAUNCH:
        # Need to update the tryjob link and buildbucket ID.
        tryjob_results = update_packages_and_run_tests.RunTryJobs(
            bisect_contents["jobs"][tryjob_index]["cl"],
            bisect_contents["jobs"][tryjob_index]["extra_cls"],
            bisect_contents["jobs"][tryjob_index]["options"],
            bisect_contents["jobs"][tryjob_index]["builder"],
            chroot_path,
        )

        bisect_contents["jobs"][tryjob_index][
            "status"
        ] = update_tryjob_status.TryjobStatus.PENDING.value
        bisect_contents["jobs"][tryjob_index]["link"] = tryjob_results[0][
            "link"
        ]
        bisect_contents["jobs"][tryjob_index][
            "buildbucket_id"
        ] = tryjob_results[0]["buildbucket_id"]

        print(
            "Successfully relaunched the tryjob for revision %d and updated "
            "the tryjob link to %s" % (revision, tryjob_results[0]["link"])
        )
    elif modify_tryjob == ModifyTryjob.ADD:
        # Tryjob exists already.
        if tryjob_index is not None:
            raise ValueError(
                "Tryjob already exists (index is %d) in %s."
                % (tryjob_index, status_file)
            )

        # Make sure the revision is within the bounds of the start and end of
        # the bisection.
        elif bisect_contents["start"] < revision < bisect_contents["end"]:
            (
                git_hash,
                revision,
            ) = get_llvm_hash.GetLLVMHashAndVersionFromSVNOption(revision)

            tryjob_dict = AddTryjob(
                update_chromeos_llvm_hash.DEFAULT_PACKAGES,
                git_hash,
                revision,
                chroot_path,
                extra_cls,
                options,
                builder,
                revision,
            )

            bisect_contents["jobs"].append(tryjob_dict)

            print("Successfully added tryjob of revision %d" % revision)
        else:
            raise ValueError("Failed to add tryjob to %s" % status_file)
    else:
        raise ValueError(
            'Invalid "modify_tryjob" option provided: %s' % modify_tryjob
        )

    with open(status_file, "w", encoding="utf-8") as update_tryjobs:
        json.dump(
            bisect_contents, update_tryjobs, indent=4, separators=(",", ": ")
        )


def main() -> None:
    """Removes, relaunches, or adds a tryjob."""

    chroot.VerifyOutsideChroot()

    args_output = GetCommandLineArgs()

    chroot.VerifyChromeOSRoot(args_output.chroot_path)

    PerformTryjobModification(
        args_output.revision,
        ModifyTryjob(args_output.modify_tryjob),
        args_output.status_file,
        args_output.extra_change_lists,
        args_output.options,
        args_output.builder,
        args_output.chroot_path,
    )


if __name__ == "__main__":
    main()
