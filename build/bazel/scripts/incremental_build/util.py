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
import csv
import dataclasses
import datetime
import enum
import functools
import json
import logging
import os
import re
import subprocess
import sys
from datetime import date
from pathlib import Path
import textwrap
from typing import Callable
from typing import Final
from typing import Generator
from typing import TypeVar

INDICATOR_FILE: Final[str] = "build/soong/soong_ui.bash"
# metrics.csv is written to but not read by this tool.
# It's supposed to be viewed as a spreadsheet that compiles data from multiple
# builds to be analyzed by other external tools.
METRICS_TABLE: Final[str] = "metrics.csv"
RUN_DIR_PREFIX: Final[str] = "run"
BUILD_INFO_JSON: Final[str] = "build_info.json"


@functools.cache
def _is_important(column) -> bool:
    patterns = {
        "actions",
        r"build_ninja_(?:hash|size)",
        "build_type",
        "cquery_out_size",
        "description",
        "log",
        r"mixed\.enabled",
        "targets",
        # the following are time-based values
        "bp2build",
        r"kati/kati (?:build|package)",
        "ninja/ninja",
        "soong/soong",
        r"soong_build/\*(?:\.bazel)?",
        "symlink_forest",
        "time",
    }
    for pattern in patterns:
        if re.fullmatch(pattern, column):
            return True
    return False


class BuildResult(enum.Enum):
    SUCCESS = enum.auto()
    FAILED = enum.auto()
    TEST_FAILURE = enum.auto()


class BuildType(enum.Enum):
    # see https://docs.python.org/3/library/enum.html#enum.Enum._ignore_
    _ignore_ = "_soong_cmd"
    # _soong_cmd_ will not be listed as an enum constant because of `_ignore_`
    _soong_cmd = ["build/soong/soong_ui.bash", "--make-mode", "--skip-soong-tests"]

    SOONG_ONLY = [*_soong_cmd, "BUILD_BROKEN_DISABLE_BAZEL=true"]
    MIXED_PROD = [*_soong_cmd, "--bazel-mode"]
    MIXED_STAGING = [*_soong_cmd, "--bazel-mode-staging"]
    B_BUILD = ["build/bazel/bin/b", "build"]
    B_ANDROID = [*B_BUILD, "--config=android"]

    @staticmethod
    def from_flag(s: str) -> list["BuildType"]:
        chosen: list[BuildType] = []
        for e in BuildType:
            if s.lower() in e.name.lower():
                chosen.append(e)
        if len(chosen) == 0:
            raise RuntimeError(f"no such build type: {s}")
        return chosen

    def to_flag(self):
        return self.name.lower()


CURRENT_BUILD_TYPE: BuildType
"""global state capturing what the current build type is"""


@dataclasses.dataclass
class BuildInfo:
    actions: int
    bp_size_total: int
    build_ninja_hash: str  # hash
    build_ninja_size: int
    build_result: BuildResult
    build_type: BuildType
    bz_size_total: int
    cquery_out_size: int
    description: str
    product: str
    targets: tuple[str, ...]
    time: datetime.timedelta
    tag: str = None
    rebuild: bool = False
    warmup: bool = False


class CustomEncoder(json.JSONEncoder):
    def default(self, obj):
        if isinstance(obj, BuildInfo):
            return self.default(dataclasses.asdict(obj))
        if isinstance(obj, dict):
            return {k: v for k, v in obj.items() if v is not None}
        if isinstance(obj, datetime.timedelta):
            return hhmmss(obj, decimal_precision=True)
        if isinstance(obj, enum.Enum):
            return obj.name
        return json.JSONEncoder.default(self, obj)


def get_csv_columns_cmd(d: Path) -> str:
    """
    :param d: the log directory
    :return: a quick shell command to view columns in metrics.csv
    """
    csv_file = d.joinpath(METRICS_TABLE).resolve()
    return f'head -n 1 "{csv_file}" | sed "s/,/\\n/g" | less -N'


def get_cmd_to_display_tabulated_metrics(d: Path, ci_mode: bool) -> str:
    """
    :param d: the log directory
    :param ci_mode: if true all top-level events are displayed
    :return: a quick shell command to view some collected metrics
    """
    csv_file = d.joinpath(METRICS_TABLE)
    headers: list[str] = []
    if csv_file.exists():
        with open(csv_file) as r:
            reader = csv.DictReader(r)
            headers = reader.fieldnames or []

    cols: list[int] = [i + 1 for i, h in enumerate(headers) if _is_important(h)]
    if ci_mode:
        # ci mode contains all information about the top level events
        for i, h in enumerate(headers):
            if re.match(r"^\w+/[^.]+$", h) and i not in cols:
                cols.append(i)

    if len(cols) == 0:
        # syntactically correct command even if the file doesn't exist
        cols.append(1)

    f = ",".join(str(i) for i in cols)
    # the sed invocations are to account for
    # https://man7.org/linux/man-pages/man1/column.1.html#BUGS
    # example: if a row were `,,,hi,,,,`
    # the successive sed conversions would be
    #    `,,,hi,,,,` =>
    #    `,--,,hi,--,,--,` =>
    #    `,--,--,hi,--,--,--,` =>
    #    `--,--,--,hi,--,--,--,` =>
    #    `--,--,--,hi,--,--,--,--`
    # Note sed doesn't support lookahead or lookbehinds
    return textwrap.dedent(
        f"""\
        grep -v "WARMUP\\|rebuild-" "{csv_file}" | \\
          sed "s/,,/,--,/g" | \\
          sed "s/,,/,--,/g" | \\
          sed "s/^,/--,/" | \\
          sed "s/,$/,--/" | \\
          cut -d, -f{f} | column -t -s,"""
    )


@functools.cache
def get_top_dir(d: Path = Path(".").resolve()) -> Path:
    """Get the path to the root of the Android source tree"""
    top_dir = os.environ.get("ANDROID_BUILD_TOP")
    if top_dir:
        logging.info("ANDROID BUILD TOP = %s", d)
        return Path(top_dir).resolve()
    logging.debug("Checking if Android source tree root is %s", d)
    if d.parent == d:
        sys.exit(
            "Unable to find ROOT source directory, specifically,"
            f"{INDICATOR_FILE} not found anywhere. "
            "Try `m nothing` and `repo sync`"
        )
    if d.joinpath(INDICATOR_FILE).is_file():
        logging.info("ANDROID BUILD TOP assumed to be %s", d)
        return d
    return get_top_dir(d.parent)


@functools.cache
def get_out_dir() -> Path:
    out_dir = os.environ.get("OUT_DIR")
    return Path(out_dir).resolve() if out_dir else get_top_dir().joinpath("out")


@functools.cache
def get_default_log_dir() -> Path:
    return get_top_dir().parent.joinpath(f'timing-{date.today().strftime("%b%d")}')


def is_interactive_shell() -> bool:
    return (
        sys.__stdin__.isatty() and sys.__stdout__.isatty() and sys.__stderr__.isatty()
    )


# see test_next_path_helper() for examples
def _next_path_helper(basename: str) -> str:
    def padded_suffix(i: int) -> str:
        return f"{i:03d}"

    # find the sequence digits following "-" and preceding the file extension
    name = re.sub(
        r"(?<=-)(?P<suffix>\d+)$",
        lambda d: padded_suffix(int(d.group("suffix")) + 1),
        basename,
    )

    if name == basename:
        # basename didn't have any numeric suffix
        name = f"{name}-{padded_suffix(1)}"
    return name


def next_path(path: Path) -> Generator[Path, None, None]:
    """
    Generator for indexed paths
    :returns a new Path with an increasing number suffix to the name
    e.g. _to_file('a.txt') = a-5.txt (if a-4.txt already exists)
    """
    while True:
        name = _next_path_helper(path.name)
        path = path.parent.joinpath(name)
        if not path.exists():
            yield path


def has_uncommitted_changes() -> bool:
    """
    effectively a quick 'repo status' that fails fast
    if any project has uncommitted changes
    """
    for cmd in ["diff", "diff --staged"]:
        diff = subprocess.run(
            f"repo forall -c git {cmd} --quiet --exit-code",
            cwd=get_top_dir(),
            shell=True,
            text=True,
            capture_output=True,
        )
        if diff.returncode != 0:
            logging.error(diff.stderr)
            return True
    return False


def hhmmss(t: datetime.timedelta, decimal_precision: bool = False) -> str:
    """pretty prints time periods, prefers mm:ss.sss and resorts to hh:mm:ss.sss
    only if t >= 1 hour.
    Examples(non_decimal_precision): 02:12, 1:12:13
    Examples(decimal_precision): 02:12.231, 00:00.512, 00:01:11.321, 1:12:13.121
    See unit test for more examples."""
    h, f = divmod(t.seconds, 60 * 60)
    m, f = divmod(f, 60)
    s = f + t.microseconds / 1000_000
    if decimal_precision:
        return f"{h}:{m:02d}:{s:06.3f}" if h else f"{m:02d}:{s:06.3f}"
    else:
        return f"{h}:{m:02}:{s:02.0f}" if h else f"{m:02}:{s:02.0f}"


def period_to_seconds(s: str) -> float:
    """converts a time period into seconds. The input is expected to be in the
    format used by hhmmss().
    Example: 02:04 -> 125
    See unit test for more examples."""
    if s == "":
        return 0.0
    acc = 0.0
    while True:
        [left, *right] = s.split(":", 1)
        acc = acc * 60 + float(left)
        if right:
            s = right[0]
        else:
            return acc


R = TypeVar("R")


def groupby(xs: list[R], key: Callable[[R], str]) -> dict[str, list[R]]:
    grouped: dict[str, list[R]] = {}
    for x in xs:
        grouped.setdefault(key(x), []).append(x)
    return grouped
