#!/usr/bin/env python3
# Copyright 2024 The ChromiumOS Authors
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Helps reason about -Werror logs emitted by the compiler wrapper.

Specifically, this works with the -Werror reports produced by the compiler
wrapper in FORCE_DISABLE_WERROR mode. It's intended to be run on trees of these
reports, so devs can run roughly the following commands:

$ apply_force_disable_werror  # (There's no actual script to do this today.)
$ build_packages --board=foo --nousepkg
$ ./werror_logs.py aggregate --directory=/build/foo/var/lib/chromeos

And see a full aggregation of all warnings that were suppressed in that
`build_packages` invocation.

It can also be used to fetch warnings reports from CQ runs, for instance,
$ ./werror_logs.py fetch-cq --cq-orchestrator-id=123456

In this case, it downloads _all -Werror logs_ from children of the given
cq-orchestrator, and prints the parent directory of all of these reports. If
you run `aggregate` on this directory, it's highly recommended to use the
`--canonicalize-board-roots` flag.
"""

import argparse
import collections
import dataclasses
import json
import logging
import multiprocessing.pool
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import threading
from typing import Any, Counter, DefaultDict, Dict, IO, Iterable, List, Optional

import cros_cls


_DEFAULT_FETCH_DIRECTORY = Path("/tmp/werror_logs")


def canonicalize_file_path_board_root(file_path: str) -> str:
    # Get rid of double slashes, unnecessary directory traversal
    # (foo/../bar/..), etc. Easier to read this way.
    file_path = os.path.normpath(file_path)
    if file_path.startswith("/build/"):
        i = file_path.find("/", len("/build/"))
        if i != -1:
            return f"/build/{{board}}/{file_path[i+1:]}"
    return file_path


@dataclasses.dataclass(frozen=True, eq=True, order=True)
class ClangWarningLocation:
    """Represents a location at which a Clang warning was emitted."""

    file: str
    line: int
    column: int

    @classmethod
    def parse(
        cls, location: str, canonicalize_board_root: bool = False
    ) -> "ClangWarningLocation":
        split = location.rsplit(":", 2)
        if len(split) == 3:
            file = split[0]
            if canonicalize_board_root:
                file = canonicalize_file_path_board_root(file)
            return cls(file=file, line=int(split[1]), column=int(split[2]))
        raise ValueError(f"Invalid location: {location!r}")


@dataclasses.dataclass(frozen=True, eq=True)
class ClangWarning:
    """Represents a Clang warning at a specific location (if applicable)."""

    # The name of the warning, e.g., -Wunused-variable
    name: str
    # The message of the warning, e.g., "'allocate' is deprecated."
    message: str
    # The location of this warning. Not present for frontend diagnostics.
    location: Optional[ClangWarningLocation]

    # This parses two kinds of errors:
    # 1. `clang-17: error: foo [-W...]`
    # 2. `/file/path:123:45: error: foo [-W...]"
    _WARNING_RE = re.compile(
        # Capture the location on its own, since `clang-\d+` is unused below.
        r"^(?:([^:]*:\d+:\d+)|clang-\d+)"
        r": error: "
        # Capture the message
        r"(.*?)\s+"
        r"\[(-W[^\][]+)]\s*$"
    )

    @classmethod
    def try_parse_line(
        cls, line: str, canonicalize_board_root: bool = False
    ) -> Optional["ClangWarning"]:
        # Fast path: we can expect "error: " in interesting lines. Break early
        # if that's not present.
        if "error: " not in line:
            return None

        m = cls._WARNING_RE.fullmatch(line)
        if not m:
            return None

        location, message, warning_flags = m.groups()
        individual_warning_flags = [
            x for x in warning_flags.split(",") if x != "-Werror"
        ]

        # This isn't impossible to handle in theory, just unexpected. Complain
        # about it.
        if len(individual_warning_flags) != 1:
            raise ValueError(
                f"Weird: parsed warnings {individual_warning_flags} out "
                f"of {line}"
            )

        if location is None:
            parsed_location = None
        else:
            parsed_location = ClangWarningLocation.parse(
                location, canonicalize_board_root
            )
        return cls(
            name=individual_warning_flags[0],
            message=message,
            location=parsed_location,
        )


@dataclasses.dataclass(frozen=True, eq=True)
class WarningInfo:
    """Carries information about a ClangWarning."""

    packages: DefaultDict[str, int] = dataclasses.field(
        default_factory=lambda: collections.defaultdict(int)
    )


class UnknownPackageNameError(ValueError):
    """Raised when a package name can't be determined from a warning report."""


@dataclasses.dataclass
class AggregatedWarnings:
    """Aggregates warning reports incrementally."""

    num_reports: int = 0
    # Mapping of warning -> list of packages that emitted it. Warnings in
    # headers may be referred to by multiple packages.
    warnings: DefaultDict[ClangWarning, WarningInfo] = dataclasses.field(
        default_factory=lambda: collections.defaultdict(WarningInfo)
    )

    _CWD_PACKAGE_RE = re.compile(
        r"^(?:/build/[^/]+)?/var/(?:cache|tmp)/portage/([^/]+/[^/]+)/"
    )

    @classmethod
    def _guess_package_name(cls, report: Dict[str, Any]) -> str:
        """Tries to guess what package `report` is from.

        Raises:
            UnknownPackageNameError if the package's name couldn't be
            determined.
        """
        m = cls._CWD_PACKAGE_RE.match(report.get("cwd", ""))
        if not m:
            raise UnknownPackageNameError()
        return m.group(1)

    def add_report_json(
        self, report_json: Dict[str, Any], canonicalize_board_root: bool = False
    ) -> int:
        """Adds the given report, returning the number of warnings parsed.

        Raises:
            UnknownPackageNameError if the package's name couldn't be
            determined.
        """
        self.num_reports += 1
        package_name = self._guess_package_name(report_json)

        num_warnings = 0
        for line in report_json.get("stdout", "").splitlines():
            if parsed := ClangWarning.try_parse_line(
                line, canonicalize_board_root
            ):
                self.warnings[parsed].packages[package_name] += 1
                num_warnings += 1

        return num_warnings

    def add_report(
        self, report_file: Path, canonicalize_board_root: bool = False
    ) -> None:
        with report_file.open(encoding="utf-8") as f:
            report = json.load(f)

        try:
            n = self.add_report_json(report, canonicalize_board_root)
        except UnknownPackageNameError:
            logging.warning(
                "Failed guessing package name for report at %r; ignoring file",
                report_file,
            )
            return

        if not n:
            logging.warning(
                "Report at %r had no parseable warnings", report_file
            )


def print_aligned_counts(
    name_count_map: Dict[str, int], file: Optional[IO[str]] = None
) -> None:
    assert name_count_map
    # Sort on value, highest first. Name breaks ties.
    summary = sorted(name_count_map.items(), key=lambda x: (-x[1], x[0]))
    num_col_width = len(f"{summary[0][1]:,}")
    name_col_width = max(len(x) for x in name_count_map)
    for name, count in summary:
        fmt_name = name.rjust(name_col_width)
        fmt_count = f"{count:,}".rjust(num_col_width)
        print(f"\t{fmt_name}: {fmt_count}", file=file)


def summarize_per_package_warnings(
    warning_infos: Iterable[WarningInfo],
    file: Optional[IO[str]] = None,
) -> None:
    warnings_per_package: DefaultDict[str, int] = collections.defaultdict(int)
    for info in warning_infos:
        for package_name, warning_count in info.packages.items():
            warnings_per_package[package_name] += warning_count

    if not warnings_per_package:
        return

    print("## Per-package warning counts:", file=file)
    print_aligned_counts(warnings_per_package, file=file)


def summarize_warnings_by_flag(
    warnings: Dict[ClangWarning, WarningInfo],
    file: Optional[IO[str]] = None,
) -> None:
    if not warnings:
        return

    warnings_per_flag: Counter[str] = collections.Counter()
    for warning, info in warnings.items():
        warnings_per_flag[warning.name] += sum(info.packages.values())

    print("## Instances of each fatal warning:", file=file)
    print_aligned_counts(warnings_per_flag, file=file)


def aggregate_reports(opts: argparse.Namespace) -> None:
    directory = opts.directory
    aggregated = AggregatedWarnings()
    for report in directory.glob("**/warnings_report*.json"):
        logging.debug("Discovered report %s", report)
        aggregated.add_report(report, opts.canonicalize_board_roots)

    if not aggregated.num_reports:
        raise ValueError(f"Found no warnings report under {directory}")

    logging.info("Discovered %d report files in total", aggregated.num_reports)
    summarize_per_package_warnings(aggregated.warnings.values())
    summarize_warnings_by_flag(aggregated.warnings)


def fetch_werror_tarball_links(
    child_builders: Dict[str, cros_cls.BuildID]
) -> List[str]:
    outputs = cros_cls.CQBoardBuilderOutput.fetch_many(child_builders.values())
    artifacts_links = []
    for builder_name, out in zip(child_builders, outputs):
        if out.artifacts_link:
            artifacts_links.append(out.artifacts_link)
        else:
            logging.info("%s had no output artifacts; ignoring", builder_name)

    gsutil_stdout = subprocess.run(
        ["gsutil", "-m", "ls"] + artifacts_links,
        check=True,
        encoding="utf-8",
        stdin=subprocess.DEVNULL,
        stdout=subprocess.PIPE,
    ).stdout

    return [
        x
        for x in gsutil_stdout.splitlines()
        if x.endswith(".fatal_clang_warnings.tar.xz")
    ]


def cq_builder_name_from_werror_logs_path(werror_logs: str) -> str:
    """Returns the CQ builder given a -Werror logs path.

    >>> cq_builder_name_from_werror_logs_path(
            "gs://chromeos-image-archive/staryu-cq/"
            "R123-15771.0.0-94466-8756713501925941617/"
            "staryu.20240207.fatal_clang_warnings.tar.xz"
        )
    "staryu-cq"
    """
    return os.path.basename(os.path.dirname(os.path.dirname(werror_logs)))


def download_and_unpack_werror_tarballs(
    unpack_dir: Path, download_dir: Path, gs_urls: List[str]
):
    # This is necessary below when we're untarring files. It should trivially
    # always be the case, and assuming it makes testing easier.
    assert download_dir.is_absolute(), download_dir

    unpack_dir.mkdir()
    download_dir.mkdir()

    logging.info(
        "Fetching and unpacking %d -Werror reports; this may take a bit",
        len(gs_urls),
    )
    # Run the download in a threadpool since we can have >100 logs, and all of
    # this is heavily I/O-bound.
    # Max 8 downloads at a time is arbitrary, but should minimize the chance of
    # rate-limiting. Don't limit `tar xaf`, since those should be short-lived.
    download_limiter = threading.BoundedSemaphore(8)

    def download_one_url(
        unpack_dir: Path, download_dir: Path, gs_url: str
    ) -> Optional[subprocess.CalledProcessError]:
        """Downloads and unpacks -Werror logs from the given gs_url.

        Leaves the tarball in `download_dir`, and the unpacked version in
        `unpack_dir`.

        Returns:
            None if all went well; otherwise, returns the command that failed.
            All commands have stderr data piped in.
        """
        file_targ = download_dir / os.path.basename(gs_url)
        try:
            with download_limiter:
                subprocess.run(
                    ["gsutil", "cp", gs_url, file_targ],
                    check=True,
                    stdin=subprocess.DEVNULL,
                    stdout=subprocess.DEVNULL,
                    stderr=subprocess.PIPE,
                    encoding="utf-8",
                    errors="replace",
                )

            # N.B., file_targ is absolute, so running with `file_targ` while
            # changing `cwd` is safe.
            subprocess.run(
                ["tar", "xaf", file_targ],
                check=True,
                cwd=unpack_dir,
                stdin=subprocess.DEVNULL,
                stdout=subprocess.DEVNULL,
                stderr=subprocess.PIPE,
                encoding="utf-8",
                errors="replace",
            )
        except subprocess.CalledProcessError as e:
            return e
        return None

    with multiprocessing.pool.ThreadPool() as thread_pool:
        download_futures = []
        for gs_url in gs_urls:
            name = cq_builder_name_from_werror_logs_path(gs_url)
            unpack_to = unpack_dir / name
            unpack_to.mkdir()
            download_to = download_dir / name
            download_to.mkdir()
            download_futures.append(
                (
                    name,
                    thread_pool.apply_async(
                        download_one_url, (unpack_to, download_to, gs_url)
                    ),
                )
            )

        num_failures = 0
        for name, future in download_futures:
            result = future.get()
            if not result:
                continue

            num_failures += 1
            logging.error(
                "Downloading %s failed: running %r. Stderr: %r",
                name,
                result.cmd,
                result.stderr,
            )
    if num_failures:
        raise ValueError(f"{num_failures} download(s) failed.")


def fetch_cq_reports(opts: argparse.Namespace) -> None:
    if opts.cl:
        logging.info(
            "Fetching most recent completed CQ orchestrator from %s", opts.cl
        )
        all_ids = cros_cls.fetch_cq_orchestrator_ids(opts.cl)
        if not all_ids:
            raise ValueError(
                f"No CQ orchestrators found under {opts.cl}. See --help for "
                "how to pass a build ID directly."
            )
        # Note that these cq-orchestrator runs are returned in oldest-to-newest
        # order. The user probably wants the newest run.
        cq_orchestrator_id = all_ids[-1]
        cq_orchestrator_url = cros_cls.builder_url(cq_orchestrator_id)
        logging.info("Checking CQ run %s", cq_orchestrator_url)
    else:
        cq_orchestrator_id = opts.cq_orchestrator_id
        cq_orchestrator_url = cros_cls.builder_url(cq_orchestrator_id)

    # This is the earliest point at which we can compute this directory with
    # certainty. Figure it out now and fail early if it exists.
    output_directory = opts.directory
    if not output_directory:
        output_directory = _DEFAULT_FETCH_DIRECTORY / str(cq_orchestrator_id)

    if output_directory.exists():
        if not opts.force:
            sys.exit(
                f"Directory at {output_directory} exists; not overwriting. "
                "Pass --force to overwrite."
            )
        # Actually _remove_ it when we have all logs unpacked and are able to
        # create the output directory with confidence.

    logging.info("Fetching info on child builders of %s", cq_orchestrator_url)
    child_builders = cros_cls.CQOrchestratorOutput.fetch(
        cq_orchestrator_id
    ).child_builders
    if not child_builders:
        raise ValueError(f"No child builders found for {cq_orchestrator_url}")

    logging.info(
        "%d child builders found; finding associated tarball links",
        len(child_builders),
    )
    werror_links = fetch_werror_tarball_links(child_builders)
    if not werror_links:
        raise ValueError(
            f"No -Werror logs found in children of {cq_orchestrator_url}"
        )

    logging.info("%d -Werror logs found", len(werror_links))
    with tempfile.TemporaryDirectory("werror_logs_fetch_cq") as t:
        tempdir = Path(t)
        unpack_dir = tempdir / "unpacked"
        download_and_unpack_werror_tarballs(
            unpack_dir=unpack_dir,
            download_dir=tempdir / "tarballs",
            gs_urls=werror_links,
        )

        if output_directory.exists():
            logging.info("Removing output directory at %s", output_directory)
            shutil.rmtree(output_directory)
        output_directory.parent.mkdir(parents=True, exist_ok=True)
        # (Convert these to strs to keep mypy happy.)
        shutil.move(str(unpack_dir), str(output_directory))
        logging.info(
            "CQ logs from %s stored in %s",
            cq_orchestrator_url,
            output_directory,
        )


def main(argv: List[str]) -> None:
    parser = argparse.ArgumentParser(
        description=__doc__,
        formatter_class=argparse.RawDescriptionHelpFormatter,
    )
    parser.add_argument(
        "--debug", action="store_true", help="Enable debug logging"
    )
    subparsers = parser.add_subparsers(required=True)
    # b/318833638: While there's only one subparser here for the moment, more
    # are expected to come (specifically, one to download logs from a CQ run).
    aggregate = subparsers.add_parser(
        "aggregate",
        help="""
        Aggregate all -Werror reports beneath a directory. Note that this will
        traverse all children of the directory, so can be used either on
        unpacked -Werror reports from CQ builders, or can be used on e.g.,
        /build/cherry/var/lib/chromeos.
        """,
    )
    aggregate.set_defaults(func=aggregate_reports)
    aggregate.add_argument(
        "--canonicalize-board-roots",
        action="store_true",
        help="""
        Converts warnings paths starting with a board root (e.g., /build/atlas)
        to a form consistent across many boards.
        """,
    )
    aggregate.add_argument(
        "--directory", type=Path, required=True, help="Directory to inspect."
    )

    fetch_cq = subparsers.add_parser(
        "fetch-cq",
        help="Fetch all -Werror reports for a CQ run.",
    )
    fetch_cq.set_defaults(func=fetch_cq_reports)
    cl_or_cq_orchestrator = fetch_cq.add_mutually_exclusive_group(required=True)
    cl_or_cq_orchestrator.add_argument(
        "--cl",
        type=cros_cls.ChangeListURL.parse_with_patch_set,
        help="Link to a CL to get the most recent cq-orchestrator from",
    )
    cl_or_cq_orchestrator.add_argument(
        "--cq-orchestrator-id",
        type=cros_cls.BuildID,
        help="""
        Build number for a cq-orchestrator run. Builders invoked by this are
        examined for -Werror logs.
        """,
    )
    fetch_cq.add_argument(
        "--directory",
        type=Path,
        help=f"""
        Directory to put downloaded -Werror logs in. Default is a subdirectory
        of {_DEFAULT_FETCH_DIRECTORY}.
        """,
    )
    fetch_cq.add_argument(
        "-f",
        "--force",
        action="store_true",
        help="Remove the directory at `--directory` if it exists",
    )

    opts = parser.parse_args(argv)

    logging.basicConfig(
        format=">> %(asctime)s: %(levelname)s: %(filename)s:%(lineno)d: "
        "%(message)s",
        level=logging.DEBUG if opts.debug else logging.INFO,
    )

    assert getattr(opts, "func", None), "Unknown subcommand?"
    opts.func(opts)


if __name__ == "__main__":
    main(sys.argv[1:])
