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
import dataclasses
import re
from pathlib import Path

import util


@dataclasses.dataclass(frozen=True)
class Defaults:
    allowed: bool
    recurse: bool


@dataclasses.dataclass(frozen=True)
class GoAllowlistManipulator:
    """
    This is a bare-bones regex-based utility for manipulating `allowlists.go`
    It expects that file to be propertly formatted.
    """

    lines: list[str]
    """the source code lines of `allowlists.go`"""
    _lists: dict[str, "GoList"] = dataclasses.field(default_factory=lambda: {})
    """
    All GoList instances retrieved via `locate()` indexed by their list names.
    This dict is kept around such that any list when modified can adjust the
    line numbers of all other lists appropriately
    """
    dir_defaults: dict[Path, Defaults] = dataclasses.field(default_factory=lambda: {})
    """
    the mappings from directories to whether they are bp2build allowed or not
    """

    def __post_init__(self):
        #  reads the Bp2BuildConfig to materialize `dir_defaults`
        start = re.compile(r"\w+\s*=\s*Bp2BuildConfig\{")
        entry = re.compile(
            r'"(?P<path>[^"]+)"\s*:\s*Bp2BuildDefault(?P<allowed>True|False)(?P<recurse>Recursively)?'
        )
        begun = False
        left_pad: str = ""
        for line in self.lines:
            line = line.strip()
            if not begun:
                begun = bool(start.match(line))
            elif line == "}":
                break
            else:
                real_item = line.strip()
                m = entry.search(real_item)
                if m:
                    key = Path(m.group("path"))
                    value = Defaults(
                        m.group("allowed") == "True", bool(m.group("recurse"))
                    )
                    self.dir_defaults[key] = value
                    if left_pad == "":
                        left_pad = line[: line.index(real_item)]

        else:
            raise RuntimeError("Bp2BuildConfig missing")

    def locate(self, listname: str) -> "GoList":
        if listname in self._lists:
            return self._lists[listname]
        start = re.compile(r"^\s*{l}\s=\s*\[]string\{{\s*$".format(l=listname))
        begin: int = -1
        left_pad: str = ""
        for i, line in enumerate(self.lines):
            if begin == -1:
                if start.match(line):
                    begin = i + 1
            else:
                if line.strip() == "}":
                    go_list = GoList(self, begin, end=i, left_pad=left_pad)
                    self._lists[listname] = go_list
                    return go_list
                elif left_pad == "":
                    real_item = line.lstrip()
                    left_pad = line[: line.index(real_item)]
        raise RuntimeError(f"{listname} not found")

    def is_dir_allowed(self, d: Path) -> bool:
        if d.is_absolute():
            d = d.relative_to(util.get_top_dir())
        if d in self.dir_defaults:
            return self.dir_defaults[d].allowed
        while d.parent != d:
            if d.parent in self.dir_defaults:
                v = self.dir_defaults[d.parent]
                return v.allowed and v.recurse
            d = d.parent
        return False

    @property
    def lists(self):
        return self._lists


@dataclasses.dataclass
class GoList:
    parent: GoAllowlistManipulator
    begin: int
    end: int
    left_pad: str = ""

    def __contains__(self, item: str) -> bool:
        quoted = f'"{item}"'
        for i in range(self.begin, self.end):
            if quoted in self.parent.lines[i]:
                return True
        return False

    def prepend(self, items: list[str]):
        clones = [f'{self.left_pad}"{i}",\n' for i in items]
        self.parent.lines[self.begin : self.begin] = clones
        growth = len(items)
        self.end += growth
        for go_list in self.parent.lists.values():
            #  adjust line numbers for all subsequent lists
            #  in the source code
            if go_list.begin > self.begin:
                go_list.begin += growth
                go_list.end += growth
