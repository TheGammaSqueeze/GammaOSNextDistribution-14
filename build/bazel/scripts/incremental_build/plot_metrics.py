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
import enum
import functools
import logging
import os
import statistics
import subprocess
import tempfile
from io import StringIO
from pathlib import Path
from string import Template
from typing import NewType, TypeVar, Iterable
from typing import Optional

Row = NewType("Row", dict[str, str])

N = TypeVar("N", int, float)


class Aggregation(enum.Enum):
    # naked function as value assignment doesn't seem to work,
    # hence wrapping in a singleton tuple
    AVG = (statistics.mean,)
    MAX = (max,)
    MEDIAN = (statistics.median,)
    MIN = (min,)
    STDEV = (statistics.stdev,)

    def fn(self, xs: Iterable[N]) -> N:
        return self.value[0](xs)


def _is_numeric(summary_row: Row) -> Optional[bool]:
    for k, v in summary_row.items():
        if k not in ("cuj", "targets"):
            if ":" in v:
                # presence of ':'  signifies a time field
                return False
            elif v.isnumeric():
                return True
    return None  # could not make a decision


def prepare_script(
    summary_csv_data: str, output: Path, filter: bool = True
) -> Optional[str]:
    reader: csv.DictReader = csv.DictReader(StringIO(summary_csv_data))
    lines: list[str] = [",".join(reader.fieldnames)]
    isnum = None

    for summary_row in reader:
        if isnum is None:
            isnum = _is_numeric(summary_row)
        cuj = summary_row.get("cuj")
        if filter and ("rebuild" in cuj or "WARMUP" in cuj):
            continue
        # fall back to 0 if a values is missing for plotting
        lines.append(",".join(v or "0" for v in summary_row.values()))

    if len(lines) <= 1:
        logging.warning("No data to plot")
        return None

    template_file = Path(os.path.dirname(__file__)).joinpath(
        "plot_metrics.template.txt"
    )
    with open(template_file, "r") as fp:
        script_template = Template(fp.read())

    os.makedirs(output.parent, exist_ok=True)
    column_count = len(reader.fieldnames)

    return script_template.substitute(
        column_count=column_count,
        data="\n".join(lines),
        output=output,
        term=output.suffix[1:],  # assume terminal = output suffix, e.g. png, svg
        width=max(160 * ((len(lines) + 4) // 4), 640),
        ydata="# default to num" if isnum else "time",
    )


def _with_line_num(script: str) -> str:
    return "".join(
        f"{i + 1:2d}:{line}" for i, line in enumerate(script.splitlines(keepends=True))
    )


@functools.cache
def _gnuplot_available() -> bool:
    has_gnuplot = (
        subprocess.run(
            "gnuplot --version",
            shell=True,
            check=False,
            stdout=subprocess.DEVNULL,
            stderr=subprocess.DEVNULL,
            text=True,
        ).returncode
        == 0
    )
    if not has_gnuplot:
        logging.warning("gnuplot unavailable")
    return has_gnuplot


def plot(summary_csv_data: str, output: Path, filter: bool):
    if not _gnuplot_available():
        return
    script = prepare_script(summary_csv_data, output, filter)
    if script is None:
        return  # no data to plot, probably due to the filter
    with tempfile.NamedTemporaryFile("w+t") as gnuplot:
        gnuplot.write(script)
        gnuplot.flush()
        p = subprocess.run(
            args=["gnuplot", gnuplot.name],
            shell=False,
            check=False,
            capture_output=True,
            text=True,
        )
        logging.debug("GnuPlot script:\n%s", script)
        if p.returncode:
            logging.error("GnuPlot errors:\n%s\n%s", p.stderr, _with_line_num(script))
        else:
            logging.info(f"See %s\n%s", output, p.stdout)
