#!/usr/bin/env python3
# Copyright 2022 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""check_clang_diags monitors for new diagnostics in LLVM

This looks at projects we care about (currently only clang-tidy, though
hopefully clang in the future, too?) and files bugs whenever a new check or
warning appears. These bugs are intended to keep us up-to-date with new
diagnostics, so we can enable them as they land.
"""

import argparse
import json
import logging
import os
import shutil
import subprocess
import sys
import textwrap
from typing import Dict, List, Tuple

from cros_utils import bugs


_DEFAULT_ASSIGNEE = "mage"
_DEFAULT_CCS = ["cjdb@google.com"]


# FIXME: clang would be cool to check, too? Doesn't seem to have a super stable
# way of listing all warnings, unfortunately.
def _build_llvm(llvm_dir: str, build_dir: str) -> None:
    """Builds everything that _collect_available_diagnostics depends on."""
    targets = ["clang-tidy"]
    # use `-C $llvm_dir` so the failure is easier to handle if llvm_dir DNE.
    ninja_result = subprocess.run(
        ["ninja", "-C", build_dir] + targets,
        check=False,
    )
    if not ninja_result.returncode:
        return

    # Sometimes the directory doesn't exist, sometimes incremental cmake
    # breaks, sometimes something random happens. Start fresh since that fixes
    # the issue most of the time.
    logging.warning("Initial build failed; trying to build from scratch.")
    shutil.rmtree(build_dir, ignore_errors=True)
    os.makedirs(build_dir)
    subprocess.run(
        [
            "cmake",
            "-G",
            "Ninja",
            "-DCMAKE_BUILD_TYPE=MinSizeRel",
            "-DLLVM_USE_LINKER=lld",
            "-DLLVM_ENABLE_PROJECTS=clang;clang-tools-extra",
            "-DLLVM_TARGETS_TO_BUILD=X86",
            f"{os.path.abspath(llvm_dir)}/llvm",
        ],
        cwd=build_dir,
        check=True,
    )
    subprocess.run(["ninja"] + targets, check=True, cwd=build_dir)


def _collect_available_diagnostics(
    llvm_dir: str, build_dir: str
) -> Dict[str, List[str]]:
    _build_llvm(llvm_dir, build_dir)

    clang_tidy = os.path.join(os.path.abspath(build_dir), "bin", "clang-tidy")
    clang_tidy_checks = subprocess.run(
        [clang_tidy, "-checks=*", "-list-checks"],
        # Use cwd='/' to ensure no .clang-tidy files are picked up. It
        # _shouldn't_ matter, but it's also ~free, so...
        check=True,
        cwd="/",
        stdout=subprocess.PIPE,
        encoding="utf-8",
    )
    clang_tidy_checks_stdout = [
        x.strip() for x in clang_tidy_checks.stdout.strip().splitlines()
    ]

    # The first line should always be this, then each line thereafter is a check
    # name.
    assert (
        clang_tidy_checks_stdout[0] == "Enabled checks:"
    ), clang_tidy_checks_stdout
    available_checks = clang_tidy_checks_stdout[1:]
    assert not any(
        check.isspace() for check in available_checks
    ), clang_tidy_checks
    return {"clang-tidy": available_checks}


def _process_new_diagnostics(
    old: Dict[str, List[str]], new: Dict[str, List[str]]
) -> Tuple[Dict[str, List[str]], Dict[str, List[str]]]:
    """Determines the set of new diagnostics that we should file bugs for.

    old: The previous state that this function returned as `new_state_file`, or
      `{}`
    new: The diagnostics that we've most recently found. This is a dict in the
      form {tool: [diag]}

    Returns a `new_state_file` to pass into this function as `old` in the
    future, and a dict of diags to file bugs about.
    """
    new_diagnostics = {}
    new_state_file = {}
    for tool, diags in new.items():
        if tool not in old:
            logging.info(
                "New tool with diagnostics: %s; pretending none are new", tool
            )
            new_state_file[tool] = diags
        else:
            old_diags = set(old[tool])
            newly_added_diags = [x for x in diags if x not in old_diags]
            if newly_added_diags:
                new_diagnostics[tool] = newly_added_diags
            # This specifically tries to make diags sticky: if one is landed,
            # then reverted, then relanded, we ignore the reland. This might
            # not be desirable? I don't know.
            new_state_file[tool] = old[tool] + newly_added_diags

    # Sort things so we have more predictable output.
    for v in new_diagnostics.values():
        v.sort()

    return new_state_file, new_diagnostics


def _file_bugs_for_new_diags(new_diags: Dict[str, List[str]]):
    for tool, diags in sorted(new_diags.items()):
        for diag in diags:
            bugs.CreateNewBug(
                component_id=bugs.WellKnownComponents.CrOSToolchainPublic,
                title=f"Investigate {tool} check `{diag}`",
                body=textwrap.dedent(
                    f"""\
                    It seems that the `{diag}` check was recently added
                    to {tool}. It's probably good to TAL at whether this
                    check would be good for us to enable in e.g., platform2, or
                    across ChromeOS.
                    """
                ),
                assignee=_DEFAULT_ASSIGNEE,
                cc=_DEFAULT_CCS,
            )


def main(argv: List[str]) -> None:
    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.INFO,
    )

    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--llvm_dir", required=True, help="LLVM directory to check. Required."
    )
    parser.add_argument(
        "--llvm_build_dir",
        required=True,
        help="Build directory for LLVM. Required & autocreated.",
    )
    parser.add_argument(
        "--state_file",
        required=True,
        help="State file to use to suppress duplicate complaints. Required.",
    )
    parser.add_argument(
        "--dry_run",
        action="store_true",
        help="Skip filing bugs & writing to the state file; just log "
        "differences.",
    )
    opts = parser.parse_args(argv)

    build_dir = opts.llvm_build_dir
    dry_run = opts.dry_run
    llvm_dir = opts.llvm_dir
    state_file = opts.state_file

    try:
        with open(state_file, encoding="utf-8") as f:
            prior_diagnostics = json.load(f)
    except FileNotFoundError:
        # If the state file didn't exist, just create it without complaining
        # this time.
        prior_diagnostics = {}

    available_diagnostics = _collect_available_diagnostics(llvm_dir, build_dir)
    logging.info("Available diagnostics are %s", available_diagnostics)
    if available_diagnostics == prior_diagnostics:
        logging.info("Current diagnostics are identical to previous ones; quit")
        return

    new_state_file, new_diagnostics = _process_new_diagnostics(
        prior_diagnostics, available_diagnostics
    )
    logging.info("New diagnostics in existing tool(s): %s", new_diagnostics)

    if dry_run:
        logging.info(
            "Skipping new state file writing and bug filing; dry-run "
            "mode wins"
        )
    else:
        _file_bugs_for_new_diags(new_diagnostics)
        new_state_file_path = state_file + ".new"
        with open(new_state_file_path, "w", encoding="utf-8") as f:
            json.dump(new_state_file, f)
        os.rename(new_state_file_path, state_file)


if __name__ == "__main__":
    main(sys.argv[1:])
