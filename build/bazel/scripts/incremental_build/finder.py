#!/usr/bin/env python3
# Copyright (C) 2023 The Android Open Source Project
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
import glob
import os
import subprocess
from pathlib import Path
import textwrap
from typing import Generator, Iterator

from util import get_out_dir
from util import get_top_dir


def is_git_repo(p: Path) -> bool:
    """checks if p is in a directory that's under git version control"""
    git = subprocess.run(
        args=f"git remote".split(),
        cwd=p,
        stdout=subprocess.DEVNULL,
        stderr=subprocess.DEVNULL,
    )
    return git.returncode == 0


def confirm(root_dir: Path, *globs: str):
    for g in globs:
        disallowed = g.startswith("!")
        if disallowed:
            g = g[1:]
        paths = glob.iglob(g, root_dir=root_dir, recursive=True)
        path = next(paths, None)
        if disallowed:
            if path is not None:
                raise RuntimeError(f"{root_dir}/{path} unexpected")
        else:
            if path is None:
                raise RuntimeError(f"{root_dir}/{g} doesn't match")


def _should_visit(c: os.DirEntry) -> bool:
    return c.is_dir() and not (
        c.is_symlink()
        or "." in c.name
        or "test" in c.name
        or Path(c.path) == get_out_dir()
    )


def find_matches(root_dir: Path, *globs: str) -> Generator[Path, None, None]:
    """
    Finds sub-paths satisfying the patterns
    :param root_dir the first directory to start searching from
    :param globs glob patterns to require or disallow (if starting with "!")
    :returns dirs satisfying the glbos
    """
    bfs: list[Path] = [root_dir]
    while len(bfs) > 0:
        first = bfs.pop(0)
        if is_git_repo(first):
            try:
                confirm(first, *globs)
                yield first
            except RuntimeError:
                pass
        children = [Path(c.path) for c in os.scandir(first) if _should_visit(c)]
        children.sort()
        bfs.extend(children)


def main():
    p = argparse.ArgumentParser(
        formatter_class=argparse.RawTextHelpFormatter,
        description=textwrap.dedent(
            f"""\
            A utility to find a directory that have descendants that satisfy
            specified required glob patterns and have no descendent that
            contradict any specified disallowed glob pattern.

            Example:
                {Path(__file__).name} '**/Android.bp'
                {Path(__file__).name} '!**/BUILD' '**/Android.bp'

            Don't forget to SINGLE QUOTE patterns to avoid shell glob expansion!
            """
        ),
    )
    p.add_argument(
        "globs",
        nargs="+",
        help="""glob patterns to require or disallow(if preceded with "!")""",
    )
    p.add_argument(
        "--root_dir",
        "-r",
        type=lambda s: Path(s).resolve(),
        default=get_top_dir(),
        help=textwrap.dedent(
            """\
                top dir to interpret the glob patterns relative to
                defaults to %(default)s
            """
        ),
    )
    p.add_argument(
        "--max",
        "-m",
        type=int,
        default=1,
        help=textwrap.dedent(
            """\
                maximum number of matching directories to show
                defaults to %(default)s
            """
        ),
    )
    options = p.parse_args()
    results = find_matches(options.root_dir, *options.globs)
    max: int = options.max
    while max > 0:
        max -= 1
        path = next(results, None)
        if path is None:
            break
        print(f"{path.relative_to(get_top_dir())}")


if __name__ == "__main__":
    main()
