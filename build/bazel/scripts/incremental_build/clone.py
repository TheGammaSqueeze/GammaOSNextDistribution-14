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
import functools
import logging
import os
import re
import shutil
import uuid
from pathlib import Path
from string import Template
from typing import Callable, Generator, Iterable
from typing import NewType
from typing import Optional
from typing import TextIO

import cuj
import util
from cuj import src
from go_allowlists import GoAllowlistManipulator

_ALLOWLISTS = "build/soong/android/allowlists/allowlists.go"

ModuleType = NewType("ModuleType", str)
ModuleName = NewType("ModuleName", str)

Filter = Callable[[ModuleType, ModuleName], bool]


def module_defs(src_lines: TextIO) -> Generator[tuple[ModuleType, str], None, None]:
    """
    Split `scr_lines` (an Android.bp file) into module definitions and discard
    everything else (e.g. top level comments and assignments)
    Assumes that the Android.bp file is properly formatted, specifically,
    for any module definition:
     1. the first line matches `start_pattern`, e.g. `cc_library {`
     2. the last line matches a closing curly brace, i.e. '}'
    """
    start_pattern = re.compile(r"^(?P<module_type>\w+)\s*\{\s*$")
    module_type: Optional[ModuleType] = None
    buffer = ""

    def in_module_def() -> bool:
        return buffer != ""

    for line in src_lines:
        # NB: line includes ending newline
        line = line.replace("$", "$$")  # escape Templating meta-char '$'
        if not in_module_def():
            m = start_pattern.match(line)
            if m:
                module_type = ModuleType(m.group("module_type"))
                buffer = line
        else:
            buffer += line
            if line.rstrip() == "}":
                assert in_module_def()
                # end of module definition
                yield module_type, buffer
                module_type = None
                buffer = ""


def type_in(*module_types: str) -> Filter:
    def f(t: ModuleType, _: ModuleName) -> bool:
        return t in module_types

    return f


def name_in(*module_names: str) -> Filter:
    def f(_: ModuleType, n: ModuleName) -> bool:
        return n in module_names

    return f


def _modify_genrule_template(module_name: ModuleName, module_def: str) -> Optional[str]:
    # assume `out` only occurs as top-level property of a module
    # assume `out` is always a singleton array
    p = re.compile(r'[\n\r]\s+out\s*:\s*\[[\n\r]*\s*"[^"]+(?=")', re.MULTILINE)
    g = p.search(module_def)
    if g is None:
        logging.debug('Could not find "out" for "%s"', module_name)
        return None
    index = g.end()
    return f"{module_def[: index]}-${{suffix}}{module_def[index:]}"


def _extract_templates_helper(
    src_lines: TextIO, f: Filter
) -> dict[ModuleName, Template]:
    """
    For `src_lines` from an Android.bp file, find modules that satisfy the
    Filter `f` and for each such mach return a "template" text that facilitates
    changing the module's name.
    """
    # assume `name` only occurs as top-level property of a module
    name_pattern = re.compile(r'[\n\r]\s+name:\s*"(?P<name>[^"]+)(?=")', re.MULTILINE)
    result = dict[ModuleName, Template]()
    for module_type, module_def in module_defs(src_lines):
        m = name_pattern.search(module_def)
        if not m:
            continue
        module_name = ModuleName(m.group("name"))
        if module_name in result:
            logging.debug(
                f"{module_name} already exists thus " f"ignoring {module_type}"
            )
            continue
        if not f(module_type, module_name):
            continue
        i = m.end()
        module_def = f"{module_def[: i]}-${{suffix}}{module_def[i:]}"
        if module_type == ModuleType("genrule"):
            module_def = _modify_genrule_template(module_name, module_def)
            if module_def is None:
                continue
        result[module_name] = Template(module_def)
    return result


def _extract_templates(
    bps: dict[Path, Filter]
) -> dict[Path, dict[ModuleName, Template]]:
    """
    If any key is a directory instead of an Android.bp file, expand it is as if it
    were the glob pattern $key/**/Android.bp, i.e. replace it with all Android.bp
    files under its tree.
    """
    bp2templates = dict[Path, dict[ModuleName, Template]]()
    with open(src(_ALLOWLISTS), "rt") as af:
        go_allowlists = GoAllowlistManipulator(af.readlines())
        alwaysconvert = go_allowlists.locate("Bp2buildModuleAlwaysConvertList")

    def maybe_register(bp: Path):
        with open(bp, "rt") as src_lines:
            templates = _extract_templates_helper(src_lines, fltr)
            if not go_allowlists.is_dir_allowed(bp.parent):
                templates = {n: v for n, v in templates.items() if n in alwaysconvert}
            if len(templates) == 0:
                logging.debug("No matches in %s", k)
            else:
                bp2templates[bp] = bp2templates.get(bp, {}) | templates

    for k, fltr in bps.items():
        if k.name == "Android.bp":
            maybe_register(k)
        for root, _, _ in os.walk(k):
            if Path(root).is_relative_to(util.get_out_dir()):
                continue
            file = Path(root).joinpath("Android.bp")
            if file.exists():
                maybe_register(file)

    return bp2templates


@functools.cache
def _back_up_path() -> Path:
    #  a directory to back up files that these CUJs change
    return util.get_out_dir().joinpath("clone-cuj-backup")


def _backup(bps: Iterable[Path]):
    # if first cuj_step then back up files to restore later
    if _back_up_path().exists():
        raise RuntimeError(
            f"{_back_up_path()} already exists. "
            f"Did you kill a previous cuj run? "
            f"Delete {_back_up_path()} and revert changes to "
            f"allowlists.go and Android.bp files"
        )
    for bp in bps:
        src_path = bp.relative_to(util.get_top_dir())
        bak_file = _back_up_path().joinpath(src_path)
        os.makedirs(os.path.dirname(bak_file))
        shutil.copy(bp, bak_file)
    src_allowlists = src(_ALLOWLISTS)
    bak_allowlists = _back_up_path().joinpath(_ALLOWLISTS)
    os.makedirs(os.path.dirname(bak_allowlists))
    shutil.copy(src_allowlists, bak_allowlists)


def _restore():
    src(_ALLOWLISTS).touch(exist_ok=True)
    for root, _, files in os.walk(_back_up_path()):
        for file in files:
            bak_file = Path(root).joinpath(file)
            bak_path = bak_file.relative_to(_back_up_path())
            src_file = util.get_top_dir().joinpath(bak_path)
            shutil.copy(bak_file, src_file)
            # touch to update mtime; ctime is ignored by ninja
            src_file.touch(exist_ok=True)


def _bz_counterpart(bp: Path) -> Path:
    return (
        util.get_out_dir()
        .joinpath("soong", "bp2build", bp.relative_to(util.get_top_dir()))
        .with_name("BUILD.bazel")
    )


def _make_clones(bp2templates: dict[Path, dict[ModuleName, Template]], n: int):
    r = f"{str(uuid.uuid4()):.6}"  # cache-busting
    source_count = 0
    output = ["\n"]

    with open(src(_ALLOWLISTS), "rt") as f:
        go_allowlists = GoAllowlistManipulator(f.readlines())
        mixed_build_enabled_list = go_allowlists.locate("ProdMixedBuildsEnabledList")
        alwaysconvert = go_allowlists.locate("Bp2buildModuleAlwaysConvertList")

    def _allow():
        if name not in mixed_build_enabled_list:
            mixed_build_enabled_list.prepend([name])
        mixed_build_enabled_list.prepend(clones)

        if name in alwaysconvert:
            alwaysconvert.prepend(clones)

    for bp, n2t in bp2templates.items():
        source_count += len(n2t)
        output.append(
            f"{n:5,}X{len(n2t):2,} modules = {n * len(n2t):+5,} "
            f"in {bp.relative_to(util.get_top_dir())}"
        )
        with open(bp, "a") as f:
            for name, t in n2t.items():
                clones = []
                for n in range(1, n + 1):
                    suffix = f"{r}-{n:05d}"
                    f.write(t.substitute(suffix=suffix))
                    clones.append(ModuleName(f"{name}-{suffix}"))
                _allow()

    with open(src(_ALLOWLISTS), "wt") as f:
        f.writelines(go_allowlists.lines)

    logging.info(
        f"Cloned {n:,}X{source_count:,} modules = {n * source_count:+,} "
        f"in {len(bp2templates)} Android.bp files"
    )
    logging.debug("\n".join(output))


def _display_sizes():
    file_count = 0
    orig_tot = 0
    curr_tot = 0
    output = ["\n"]
    for root, _, files in os.walk(_back_up_path()):
        file_count += len(files)
        for file in files:
            backup_file = Path(root).joinpath(file)
            common_path = backup_file.relative_to(_back_up_path())
            source_file = util.get_top_dir().joinpath(common_path)
            curr_size = os.stat(source_file).st_size
            curr_tot += curr_size
            orig_size = os.stat(backup_file).st_size
            orig_tot += orig_size
            output.append(
                f"{orig_size:7,} {curr_size - orig_size :+5,} => {curr_size:9,} "
                f"bytes {source_file.relative_to(util.get_top_dir())}"
            )
            if file == "Android.bp":
                bz = _bz_counterpart(source_file)
                output.append(
                    f"{os.stat(bz).st_size:8,} bytes "
                    f"$OUTDIR/{bz.relative_to(util.get_out_dir())}"
                )
    logging.info(
        f"Affected {file_count} files {orig_tot:,} "
        f"{curr_tot - orig_tot:+,} => {curr_tot:,} bytes"
    )
    logging.debug("\n".join(output))


def _name_cuj(count: int, module_count: int, bp_count: int) -> str:
    match module_count:
        case 1:
            name = f"{count}"
        case _:
            name = f"{count}x{module_count}"
    if bp_count > 1:
        name = f"{name}({bp_count} files)"
    return name


class Clone(cuj.CujGroup):
    def __init__(self, group_name: str, bps: dict[Path, Filter]):
        super().__init__(group_name)
        self.bps = bps

    def get_steps(self) -> Iterable[cuj.CujStep]:
        bp2templates = _extract_templates(self.bps)
        bp_count = len(bp2templates)
        if bp_count == 0:
            raise RuntimeError(f"No eligible module to clone in {self.bps.keys()}")
        module_count = sum(len(templates) for templates in bp2templates.values())

        if "CLONE" in os.environ:
            counts = [int(s) for s in os.environ["CLONE"].split(",")]
        else:
            counts = [1, 100, 200, 300, 400]
            logging.info(
                f'Will clone {",".join(str(i) for i in counts)} in cujs. '
                f"You may specify alternative counts with CLONE env var, "
                f"e.g. CLONE = 1,10,100,1000"
            )
        first_bp = next(iter(bp2templates.keys()))

        def modify_bp():
            with open(first_bp, mode="a") as f:
                f.write(f"//post clone modification {uuid.uuid4()}\n")

        steps: list[cuj.CujStep] = []
        for i, count in enumerate(counts):
            base_name = _name_cuj(count, module_count, bp_count)
            steps.append(
                cuj.CujStep(
                    verb=base_name,
                    apply_change=cuj.sequence(
                        functools.partial(_backup, bp2templates.keys())
                        if i == 0
                        else _restore,
                        functools.partial(_make_clones, bp2templates, count),
                    ),
                    verify=_display_sizes,
                )
            )
            steps.append(cuj.CujStep(verb=f"bp aft {base_name}", apply_change=modify_bp))
            if i == len(counts) - 1:
                steps.append(
                    cuj.CujStep(
                        verb="revert",
                        apply_change=cuj.sequence(
                            _restore, lambda: shutil.rmtree(_back_up_path())
                        ),
                        verify=_display_sizes,
                    )
                )
        return steps


def main():
    """
    provided only for manual run;
    use incremental_build.sh to invoke the cuj instead
    """
    p = argparse.ArgumentParser()
    p.add_argument(
        "--module",
        "-m",
        default="adbd",
        help="name of the module to clone; default=%(default)s",
    )
    p.add_argument(
        "--count",
        "-n",
        default=1,
        type=int,
        help="number of times to clone; default: %(default)s",
    )
    adb_bp = util.get_top_dir().joinpath("packages/modules/adb/Android.bp")
    p.add_argument(
        "androidbp",
        nargs="?",
        default=adb_bp,
        type=Path,
        help="absolute path to Android.bp file; default=%(default)s",
    )
    options = p.parse_args()
    _make_clones(
        _extract_templates({options.androidbp: name_in(options.module)}), options.count
    )
    logging.warning("Changes made to your source tree; TIP: `repo status`")


if __name__ == "__main__":
    logging.root.setLevel(logging.INFO)
    main()
