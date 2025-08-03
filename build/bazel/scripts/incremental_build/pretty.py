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
import csv
import datetime
import enum
import logging
import re
import statistics
import subprocess
import textwrap
from pathlib import Path

from typing import Iterable, NewType, TextIO, TypeVar

import plot_metrics
import util

Row = NewType("Row", dict[str, str])


# modify the row in-place
def _normalize_rebuild(row: Row):
    row["description"] = re.sub(
        r"^(rebuild)-[\d+](.*)$", "\\1\\2", row.get("description")
    )

def _get_tagged_build_type(row: Row) -> str:
    build_type = row.get("build_type")
    tag = row.get("tag")
    return build_type if not tag else f"{build_type}:{tag}"

def _build_types(rows: list[Row]) -> list[str]:
    return list(dict.fromkeys(_get_tagged_build_type(row) for row in rows).keys())


def _write_table(lines: list[list[str]]) -> str:
    def join_cells(line: list[str]) -> str:
        return ",".join(str(cell) for cell in line)

    return "\n".join(join_cells(line) for line in lines) + "\n"


class Aggregation(enum.Enum):
    # naked function as value assignment doesn't seem to work,
    # hence wrapping in a singleton tuple
    AVG = (statistics.mean,)
    MAX = (max,)
    MEDIAN = (statistics.median,)
    MIN = (min,)
    STDEV = (statistics.stdev,)

    N = TypeVar("N", int, float)

    def fn(self, xs: Iterable[N]) -> N:
        return self.value[0](xs)


def _aggregate(prop: str, rows: list[Row], agg: Aggregation) -> str:
    """
    compute the requested aggregation
    :return formatted values
    """
    if not rows:
        return ""
    vals = [x.get(prop) for x in rows]
    vals = [x for x in vals if bool(x)]
    if len(vals) == 0:
        return ""

    isnum = any(x.isnumeric() for x in vals)
    if isnum:
        vals = [int(x) for x in vals]
        cell = f"{(agg.fn(vals)):.0f}"
    else:
        vals = [util.period_to_seconds(x) for x in vals]
        cell = util.hhmmss(datetime.timedelta(seconds=agg.fn(vals)))

    if len(vals) > 1:
        cell = f"{cell}[N={len(vals)}]"
    return cell


def acceptable(row: Row) -> bool:
    failure = row.get("build_result") == "FAILED"
    if failure:
        logging.error(f"Skipping {row.get('description')}/{row.get('build_type')}")
    return not failure


def summarize_helper(metrics: TextIO, regex: str, agg: Aggregation) -> dict[str, str]:
    """
    Args:
      metrics: csv detailed input, each row corresponding to a build
      regex: regex matching properties to be summarized
      agg: aggregation to use
    """
    reader: csv.DictReader = csv.DictReader(metrics)

    # get all matching properties
    p = re.compile(regex)
    properties = [f for f in reader.fieldnames if p.search(f)]
    if len(properties) == 0:
        logging.error("no matching properties found")
        return {}

    all_rows: list[Row] = [row for row in reader if acceptable(row)]
    for row in all_rows:
        _normalize_rebuild(row)
    build_types: list[str] = _build_types(all_rows)
    by_cuj: dict[str, list[Row]] = util.groupby(
        all_rows, lambda l: l.get("description")
    )

    def extract_lines_for_cuj(prop, cuj, cuj_rows) -> list[list[str]]:
        by_targets = util.groupby(cuj_rows, lambda l: l.get("targets"))
        lines = []
        for targets, target_rows in by_targets.items():
            by_build_type = util.groupby(target_rows, _get_tagged_build_type)
            vals = [
                _aggregate(prop, by_build_type.get(build_type), agg)
                for build_type in build_types
            ]
            lines.append([cuj, targets, *vals])
        return lines

    def tabulate(prop) -> str:
        headers = ["cuj", "targets"] + build_types
        lines: list[list[str]] = [headers]
        for cuj, cuj_rows in by_cuj.items():
            lines.extend(extract_lines_for_cuj(prop, cuj, cuj_rows))
        return _write_table(lines)

    return {prop: tabulate(prop) for prop in properties}


def _display_summarized_metrics(summary_csv: Path, filter_cujs: bool):
    cmd = (
        (
            f'grep -v "WARMUP\\|rebuild\\|revert\\|delete" {summary_csv}'
            f" | column -t -s,"
        )
        if filter_cujs
        else f"column -t -s, {summary_csv}"
    )
    output = subprocess.check_output(cmd, shell=True, text=True)
    logging.info(
        textwrap.dedent(
            f"""\
            %s
            %s
            """
        ),
        cmd,
        output,
    )


def summarize(
    metrics_csv: Path,
    regex: str,
    output_dir: Path,
    agg: Aggregation = Aggregation.MEDIAN,
    filter_cujs: bool = True,
    plot_format: str = "svg",
):
    """
    writes `summary_data` value as a csv files under `output_dir`
    if `filter_cujs` is False, then does not filter out WARMUP and rebuild cuj steps
    """
    with open(metrics_csv, "rt") as input_file:
        summary_data = summarize_helper(input_file, regex, agg)
    for k, v in summary_data.items():
        summary_csv = output_dir.joinpath(f"{k}.{agg.name}.csv")
        summary_csv.parent.mkdir(parents=True, exist_ok=True)
        with open(summary_csv, mode="wt") as f:
            f.write(v)
        _display_summarized_metrics(summary_csv, filter_cujs)
        plot_file = output_dir.joinpath(f"{k}.{agg.name}.{plot_format}")
        plot_metrics.plot(v, plot_file, filter_cujs)


def main():
    p = argparse.ArgumentParser()
    p.add_argument(
        "-p",
        "--properties",
        default="^time$",
        nargs="?",
        help="regex to select properties",
    )
    p.add_argument(
        "metrics",
        nargs="?",
        default=util.get_default_log_dir().joinpath(util.METRICS_TABLE),
        help="metrics.csv file to parse",
    )
    p.add_argument(
        "--statistic",
        nargs="?",
        type=lambda arg: Aggregation[arg],
        default=Aggregation.MEDIAN,
        help=f"Defaults to {Aggregation.MEDIAN.name}. "
        f"Choose from {[a.name for a in Aggregation]}",
    )
    p.add_argument(
        "--filter",
        default=True,
        action=argparse.BooleanOptionalAction,
        help="Filter out 'rebuild-' and 'WARMUP' builds?",
    )
    p.add_argument(
        "--format",
        nargs="?",
        default="svg",
        help="graph output format, e.g. png, svg etc"
    )
    options = p.parse_args()
    metrics_csv = Path(options.metrics)
    aggregation: Aggregation = options.statistic
    if metrics_csv.exists() and metrics_csv.is_dir():
        metrics_csv = metrics_csv.joinpath(util.METRICS_TABLE)
    if not metrics_csv.exists():
        raise RuntimeError(f"{metrics_csv} does not exit")
    summarize(
        metrics_csv=metrics_csv,
        regex=options.properties,
        agg=aggregation,
        filter_cujs=options.filter,
        output_dir=metrics_csv.parent.joinpath("perf"),
        plot_format=options.format,
    )


if __name__ == "__main__":
    logging.root.setLevel(logging.INFO)
    main()
