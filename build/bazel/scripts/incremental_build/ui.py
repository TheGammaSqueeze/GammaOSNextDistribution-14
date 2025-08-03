# Copyright (C) 2022 The Android Open Source Project
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

import argparse
import dataclasses
import functools
import logging
import os
import re
import sys
import textwrap
from pathlib import Path
from typing import Optional

import cuj_catalog
import util
from util import BuildType


@dataclasses.dataclass(frozen=True)
class UserInput:
    build_types: tuple[BuildType, ...]
    chosen_cujgroups: tuple[int, ...]
    tag: Optional[str]
    log_dir: Path
    no_warmup: bool
    targets: tuple[str, ...]
    ci_mode: bool


@functools.cache
def get_user_input() -> UserInput:
    cujgroups = cuj_catalog.get_cujgroups()

    def validate_cujgroups(input_str: str) -> list[int]:
        if input_str.isnumeric():
            i = int(input_str)
            if 0 <= i < len(cujgroups):
                return [i]
            logging.critical(
                f"Invalid input: {input_str}. "
                f"Expected an index between 1 and {len(cujgroups)}. "
                "Try --help to view the list of available CUJs"
            )
            raise argparse.ArgumentTypeError()
        else:
            pattern = re.compile(input_str)
            matching_cuj_groups = [
                i
                for i, cujgroup in enumerate(cujgroups)
                if pattern.search(cujgroup.description)
            ]
            if len(matching_cuj_groups):
                return matching_cuj_groups
            logging.critical(
                f'Invalid input: "{input_str}" does not match any CUJ. '
                "Try --help to view the list of available CUJs"
            )
            raise argparse.ArgumentTypeError()

    # importing locally here to avoid chances of cyclic import
    import incremental_build

    p = argparse.ArgumentParser(
        formatter_class=argparse.RawTextHelpFormatter,
        description=""
        + textwrap.dedent(incremental_build.__doc__)
        + textwrap.dedent(incremental_build.main.__doc__),
    )

    cuj_list = "\n".join(
        [f"{i:2}: {cujgroup.description}" for i, cujgroup in enumerate(cujgroups)]
    )
    p.add_argument(
        "-c",
        "--cujs",
        nargs="*",
        type=validate_cujgroups,
        help="Index number(s) for the CUJ(s) from the following list. "
        "Or substring matches for the CUJ description."
        f"Note the ordering will be respected:\n{cuj_list}",
    )
    p.add_argument(
        "--no-warmup",
        default=False,
        action="store_true",
        help="skip warmup builds; this can skew your results for the first CUJ you run.",
    )
    p.add_argument(
        "-t",
        "--tag",
        type=str,
        default="",
        help="Any additional tag for this set of builds, this helps "
        "distinguish the new data from previously collected data, "
        "useful for comparative analysis",
    )

    log_levels = dict(getattr(logging, "_levelToName")).values()
    p.add_argument(
        "-v",
        "--verbosity",
        choices=log_levels,
        default="INFO",
        help="Log level. Defaults to %(default)s",
    )
    default_log_dir = util.get_default_log_dir()
    p.add_argument(
        "-l",
        "--log-dir",
        type=Path,
        default=default_log_dir,
        help=textwrap.dedent(
            """\
                Directory for timing logs. Defaults to %(default)s
                TIPS:
                  1 Specify a directory outside of the source tree
                  2 To view key metrics in metrics.csv:
                {}
                  3 To view column headers:
                {}
            """
        ).format(
            textwrap.indent(
                util.get_cmd_to_display_tabulated_metrics(default_log_dir, False),
                " " * 4,
            ),
            textwrap.indent(util.get_csv_columns_cmd(default_log_dir), " " * 4),
        ),
    )
    def_build_types = [
        BuildType.SOONG_ONLY,
    ]
    p.add_argument(
        "-b",
        "--build-types",
        nargs="+",
        type=BuildType.from_flag,
        default=[def_build_types],
        help=f"Defaults to {[b.to_flag() for b in def_build_types]}. "
        f"Choose from {[e.name.lower() for e in BuildType]}",
    )
    p.add_argument(
        "--ignore-repo-diff",
        default=False,
        action="store_true",
        help='Skip "repo status" check',
    )
    p.add_argument(
        "--append-csv",
        default=False,
        action="store_true",
        help="Add results to existing spreadsheet",
    )
    p.add_argument(
        "targets",
        nargs="*",
        default=["nothing"],
        help='Targets to run, e.g. "libc adbd". ' "Defaults to %(default)s",
    )
    p.add_argument(
        "--ci-mode",
        default=False,
        action="store_true",
        help="Only use it for CI runs.It will copy the "
        "first metrics after warmup to the logs directory in CI",
    )

    options = p.parse_args()

    if options.verbosity:
        logging.root.setLevel(options.verbosity)

    chosen_cujgroups: tuple[int, ...] = (
        tuple(int(i) for sublist in options.cujs for i in sublist)
        if options.cujs
        else tuple()
    )

    bazel_labels: list[str] = [
        target for target in options.targets if target.startswith("//")
    ]
    if 0 < len(bazel_labels) < len(options.targets):
        logging.critical(
            f"Don't mix bazel labels {bazel_labels} with soong targets "
            f"{[t for t in options.targets if t not in bazel_labels]}"
        )
        sys.exit(1)
    if os.getenv("BUILD_BROKEN_DISABLE_BAZEL") is not None:
        raise RuntimeError(
            f"use -b {BuildType.SOONG_ONLY.to_flag()} "
            f"instead of BUILD_BROKEN_DISABLE_BAZEL"
        )
    build_types: tuple[BuildType, ...] = tuple(
        BuildType(i) for sublist in options.build_types for i in sublist
    )
    if len(bazel_labels) > 0:
        non_b = [
            b.name for b in build_types if b != BuildType.B_BUILD and b != BuildType.B_ANDROID
        ]
        if len(non_b):
            raise RuntimeError(f"bazel labels can not be used with {non_b}")

    pretty_str = "\n".join(
        [f"{i:2}: {cujgroups[i].description}" for i in chosen_cujgroups]
    )
    logging.info(f"%d CUJs chosen:\n%s", len(chosen_cujgroups), pretty_str)

    if not options.ignore_repo_diff and util.has_uncommitted_changes():
        error_message = (
            "THERE ARE UNCOMMITTED CHANGES (TIP: repo status). "
            "Use --ignore-repo-diff to skip this check."
        )
        if not util.is_interactive_shell():
            logging.critical(error_message)
            sys.exit(1)
        logging.error(error_message)
        response = input("Continue?[Y/n]")
        if response.upper() != "Y":
            sys.exit(1)

    log_dir = Path(options.log_dir).resolve()
    if not options.append_csv and log_dir.exists():
        error_message = (
            f"{log_dir} already exists. "
            "Use --append-csv to skip this check."
            "Consider --tag to your new runs"
        )
        if not util.is_interactive_shell():
            logging.critical(error_message)
            sys.exit(1)
        logging.error(error_message)
        response = input("Continue?[Y/n]")
        if response.upper() != "Y":
            sys.exit(1)

    if log_dir.is_relative_to(util.get_top_dir()):
        logging.critical(
            f"choose a log_dir outside the source tree; "
            f"'{options.log_dir}' resolves to {log_dir}"
        )
        sys.exit(1)

    if options.ci_mode:
        if len(chosen_cujgroups) > 1:
            logging.critical(
                "CI mode can only allow one cuj group. "
                "Remove --ci-mode flag to skip this check."
            )
            sys.exit(1)
        if len(build_types) > 1:
            logging.critical(
                "CI mode can only allow one build type. "
                "Remove --ci-mode flag to skip this check."
            )
            sys.exit(1)

    if options.no_warmup:
        logging.warning(
            "WARMUP runs will be skipped. Note this is not advised "
            "as it gives unreliable results."
        )
    return UserInput(
        build_types=build_types,
        chosen_cujgroups=chosen_cujgroups,
        tag=options.tag,
        log_dir=Path(options.log_dir).resolve(),
        no_warmup=options.no_warmup,
        targets=options.targets,
        ci_mode=options.ci_mode,
    )
