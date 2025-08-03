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

import functools
import io
import logging
import shutil
import tempfile
import uuid
from pathlib import Path
from typing import Final, Iterable, Optional

import clone
import cuj
import finder
import util
import cuj_regex_based
from cuj import CujGroup
from cuj import CujStep
from cuj import InWorkspace
from cuj import Verifier
from cuj import de_src
from cuj import src
from util import BuildType

"""
Provides some representative CUJs. If you wanted to manually run something but
would like the metrics to be collated in the metrics.csv file, use
`perf_metrics.py` as a stand-alone after your build.
"""


class Clean(CujGroup):
    def __init__(self):
        super().__init__("clean")

    def get_steps(self) -> Iterable[CujStep]:
        def clean():
            if util.get_out_dir().exists():
                shutil.rmtree(util.get_out_dir())

        return [CujStep("", clean)]


class NoChange(CujGroup):
    def __init__(self):
        super().__init__("no change")

    def get_steps(self) -> Iterable[CujStep]:
        return [CujStep("", lambda: None)]


Warmup: Final[CujGroup] = NoChange()
Warmup._desc = "WARMUP"


class Modify(CujGroup):
    """
    A pair of CujSteps, where the first modifies the file and the
    second reverts the modification
    Arguments:
        file: the file to be modified and reverted
        text: the text to be appended to the file to modify it
    """

    def __init__(self, file: Path, text: Optional[str] = None):
        super().__init__(f"modify {de_src(file)}")
        if not file.exists():
            raise RuntimeError(f"{file} does not exist")
        self.file = file
        self.text = text

    def get_steps(self) -> Iterable[CujStep]:
        if self.text is None:
            self.text = f"//BOGUS {uuid.uuid4()}\n"

        def add_line():
            with open(self.file, mode="a") as f:
                f.write(self.text)

        def revert():
            with open(self.file, mode="rb+") as f:
                # assume UTF-8
                f.seek(-len(self.text), io.SEEK_END)
                f.truncate()

        return [CujStep("", add_line), CujStep("revert", revert)]


class Create(CujGroup):
    """
    A pair of CujSteps, where the fist creates the file and the
    second deletes it
    Attributes:
        file: the file to be created and deleted
        ws: the expectation for the counterpart file in symlink
            forest (aka the synthetic bazel workspace) when its created
        text: the content of the file
    """

    def __init__(self, file: Path, ws: InWorkspace, text: Optional[str] = None):
        super().__init__(f"create {de_src(file)}")
        if file.exists():
            raise RuntimeError(
                f"File {file} already exists. Interrupted an earlier run?\n"
                "TIP: `repo status` and revert changes!!!"
            )
        self.file = file
        self.ws = ws
        self.text = text
        if self.text is None:
            self.text = f"//Test File: safe to delete {uuid.uuid4()}\n"

    def get_steps(self) -> Iterable[CujStep]:
        missing_dirs = [f for f in self.file.parents if not f.exists()]
        shallowest_missing_dir = missing_dirs[-1] if len(missing_dirs) else None

        def create():
            self.file.parent.mkdir(parents=True, exist_ok=True)
            self.file.touch(exist_ok=False)
            with open(self.file, mode="w") as f:
                f.write(self.text)

        def delete():
            if shallowest_missing_dir:
                shutil.rmtree(shallowest_missing_dir)
            else:
                self.file.unlink(missing_ok=False)

        return [
            CujStep("", create, self.ws.verifier(self.file)),
            CujStep("revert", delete, InWorkspace.OMISSION.verifier(self.file)),
        ]


class CreateBp(Create):
    """
    This is basically the same as "Create" but with canned content for
    an Android.bp file.
    """

    def __init__(self, bp_file: Path):
        super().__init__(
            bp_file,
            InWorkspace.SYMLINK,
            'filegroup { name: "test-bogus-filegroup", srcs: ["**/*.md"] }',
        )


class Delete(CujGroup):
    """
    A pair of CujSteps, where the first deletes a file and the second
    restores it
    Attributes:
        original: The file to be deleted then restored
        ws: When restored, expectation for the file's counterpart in the
            symlink forest (aka synthetic bazel workspace)
    """

    def __init__(self, original: Path, ws: InWorkspace):
        super().__init__(f"delete {de_src(original)}")
        self.original = original
        self.ws = ws

    def get_steps(self) -> Iterable[CujStep]:
        tempdir = Path(tempfile.gettempdir())
        if tempdir.is_relative_to(util.get_top_dir()):
            raise SystemExit(f"Temp dir {tempdir} is under source tree")
        if tempdir.is_relative_to(util.get_out_dir()):
            raise SystemExit(
                f"Temp dir {tempdir} is under " f"OUT dir {util.get_out_dir()}"
            )
        copied = tempdir.joinpath(f"{self.original.name}-{uuid.uuid4()}.bak")

        def move_to_tempdir_to_mimic_deletion():
            logging.warning("MOVING %s TO %s", de_src(self.original), copied)
            self.original.rename(copied)

        return [
            CujStep(
                "",
                move_to_tempdir_to_mimic_deletion,
                InWorkspace.OMISSION.verifier(self.original),
            ),
            CujStep(
                "revert",
                lambda: copied.rename(self.original),
                self.ws.verifier(self.original),
            ),
        ]


class ReplaceFileWithDir(CujGroup):
    """Replace a file with a non-empty directory"""

    def __init__(self, p: Path):
        super().__init__(f"replace {de_src(p)} with dir")
        self.p = p

    def get_steps(self) -> Iterable[CujStep]:
        # an Android.bp is always a symlink in the workspace and thus its parent
        # will be a directory in the workspace
        create_dir: CujStep
        delete_dir: CujStep
        create_dir, delete_dir, *tail = CreateBp(
            self.p.joinpath("Android.bp")
        ).get_steps()
        assert len(tail) == 0

        original_text: str

        def replace_it():
            nonlocal original_text
            original_text = self.p.read_text()
            self.p.unlink()
            create_dir.apply_change()

        def revert():
            delete_dir.apply_change()
            self.p.write_text(original_text)

        return [
            CujStep(f"", replace_it, create_dir.verify),
            CujStep(f"revert", revert, InWorkspace.SYMLINK.verifier(self.p)),
        ]


def content_verfiers(ws_build_file: Path, content: str) -> tuple[Verifier, Verifier]:
    def search() -> bool:
        with open(ws_build_file, "r") as f:
            for line in f:
                if line == content:
                    return True
        return False

    @cuj.skip_for(BuildType.SOONG_ONLY)
    def contains():
        if not search():
            raise AssertionError(
                f"{de_src(ws_build_file)} expected to contain {content}"
            )
        logging.info(f"VERIFIED {de_src(ws_build_file)} contains {content}")

    @cuj.skip_for(BuildType.SOONG_ONLY)
    def does_not_contain():
        if search():
            raise AssertionError(
                f"{de_src(ws_build_file)} not expected to contain {content}"
            )
        logging.info(f"VERIFIED {de_src(ws_build_file)} does not contain {content}")

    return contains, does_not_contain


class ModifyKeptBuildFile(CujGroup):
    def __init__(self, build_file: Path):
        super().__init__(f"modify kept {de_src(build_file)}")
        self.build_file = build_file

    def get_steps(self) -> Iterable[CujStep]:
        content = f"//BOGUS {uuid.uuid4()}\n"
        step1, step2, *tail = Modify(self.build_file, content).get_steps()
        assert len(tail) == 0
        ws_build_file = InWorkspace.ws_counterpart(self.build_file).with_name(
            "BUILD.bazel"
        )
        merge_prover, merge_disprover = content_verfiers(ws_build_file, content)
        return [
            CujStep(
                step1.verb,
                step1.apply_change,
                cuj.sequence(step1.verify, merge_prover),
            ),
            CujStep(
                step2.verb,
                step2.apply_change,
                cuj.sequence(step2.verify, merge_disprover),
            ),
        ]


class CreateKeptBuildFile(CujGroup):
    def __init__(self, build_file: Path):
        super().__init__(f"create kept {de_src(build_file)}")
        self.build_file = build_file
        if self.build_file.name == "BUILD.bazel":
            self.ws = InWorkspace.NOT_UNDER_SYMLINK
        elif self.build_file.name == "BUILD":
            self.ws = InWorkspace.SYMLINK
        else:
            raise RuntimeError(f"Illegal name for a build file {self.build_file}")

    def get_steps(self):
        content = f"//BOGUS {uuid.uuid4()}\n"
        ws_build_file = InWorkspace.ws_counterpart(self.build_file).with_name(
            "BUILD.bazel"
        )
        merge_prover, merge_disprover = content_verfiers(ws_build_file, content)

        step1: CujStep
        step2: CujStep
        step1, step2, *tail = Create(self.build_file, self.ws, content).get_steps()
        assert len(tail) == 0
        return [
            CujStep(
                step1.verb,
                step1.apply_change,
                cuj.sequence(step1.verify, merge_prover),
            ),
            CujStep(
                step2.verb,
                step2.apply_change,
                cuj.sequence(step2.verify, merge_disprover),
            ),
        ]


class CreateUnkeptBuildFile(CujGroup):
    def __init__(self, build_file: Path):
        super().__init__(f"create unkept {de_src(build_file)}")
        self.build_file = build_file

    def get_steps(self):
        content = f"//BOGUS {uuid.uuid4()}\n"
        ws_build_file = InWorkspace.ws_counterpart(self.build_file).with_name(
            "BUILD.bazel"
        )
        step1: CujStep
        step2: CujStep
        step1, step2, *tail = Create(
            self.build_file, InWorkspace.OMISSION, content
        ).get_steps()
        assert len(tail) == 0
        _, merge_disprover = content_verfiers(ws_build_file, content)
        return [
            CujStep(step1.verb, step1.apply_change, merge_disprover),
            CujStep(step2.verb, step2.apply_change, merge_disprover),
        ]


def _mixed_build_launch_cujs() -> tuple[CujGroup, ...]:
    core_settings = src("frameworks/base/core/java/android/provider/Settings.java")
    ams = src(
        "frameworks/base/services/core/java/com/android/server/am/ActivityManagerService.java"
    )
    resource = src("frameworks/base/core/res/res/values/config.xml")
    return (
        Modify(src("bionic/libc/tzcode/asctime.c")),
        Modify(src("bionic/libc/stdio/stdio.cpp")),
        Modify(src("packages/modules/adb/daemon/main.cpp")),
        Modify(src("frameworks/base/core/java/android/view/View.java")),
        Modify(core_settings),
        cuj_regex_based.modify_private_method(core_settings),
        cuj_regex_based.add_private_field(core_settings),
        cuj_regex_based.add_public_api(core_settings),
        cuj_regex_based.modify_private_method(ams),
        cuj_regex_based.add_private_field(ams),
        cuj_regex_based.add_public_api(ams),
        cuj_regex_based.modify_resource(resource),
        cuj_regex_based.add_resource(resource),
    )


def _cloning_cujs() -> tuple[CujGroup, ...]:
    cc_ = (
        lambda t, name: t.startswith("cc_")
        and "test" not in t
        and not name.startswith("libcrypto")  # has some unique hash
    )
    libNN = lambda t, name: t == "cc_library_shared" and name == "libneuralnetworks"
    return (
        clone.Clone("clone genrules", {src("."): clone.type_in("genrule")}),
        clone.Clone("clone cc_", {src("."): cc_}),
        clone.Clone(
            "clone adbd",
            {src("packages/modules/adb/Android.bp"): clone.name_in("adbd")},
        ),
        clone.Clone(
            "clone libNN",
            {src("packages/modules/NeuralNetworks/runtime/Android.bp"): libNN},
        ),
        clone.Clone(
            "clone adbd&libNN",
            {
                src("packages/modules/adb/Android.bp"): clone.name_in("adbd"),
                src("packages/modules/NeuralNetworks/runtime/Android.bp"): libNN,
            },
        ),
    )


@functools.cache
def get_cujgroups() -> tuple[CujGroup, ...]:
    # we are choosing "package" directories that have Android.bp but
    # not BUILD nor BUILD.bazel because
    # we can't tell if ShouldKeepExistingBuildFile would be True or not
    non_empty_dir = "*/*"
    pkg = src("art")
    finder.confirm(pkg, non_empty_dir, "Android.bp", "!BUILD*")
    pkg_free = src("bionic/docs")
    finder.confirm(pkg_free, non_empty_dir, "!**/Android.bp", "!**/BUILD*")
    ancestor = src("bionic")
    finder.confirm(ancestor, "**/Android.bp", "!Android.bp", "!BUILD*")
    leaf_pkg_free = src("bionic/build")
    finder.confirm(leaf_pkg_free, f"!{non_empty_dir}", "!**/Android.bp", "!**/BUILD*")

    android_bp_cujs = (
        Modify(src("Android.bp")),
        *(
            CreateBp(d.joinpath("Android.bp"))
            for d in [ancestor, pkg_free, leaf_pkg_free]
        ),
    )
    unreferenced_file_cujs = (
        *(
            Create(d.joinpath("unreferenced.txt"), InWorkspace.SYMLINK)
            for d in [ancestor, pkg]
        ),
        *(
            Create(d.joinpath("unreferenced.txt"), InWorkspace.UNDER_SYMLINK)
            for d in [pkg_free, leaf_pkg_free]
        ),
    )

    return (
        Clean(),
        NoChange(),
        *_cloning_cujs(),
        Create(src("bionic/libc/tzcode/globbed.c"), InWorkspace.UNDER_SYMLINK),
        # TODO (usta): find targets that should be affected
        *(
            Delete(f, InWorkspace.SYMLINK)
            for f in [
                src("bionic/libc/version_script.txt"),
                src("external/cbor-java/AndroidManifest.xml"),
            ]
        ),
        *unreferenced_file_cujs,
        *_mixed_build_launch_cujs(),
        *android_bp_cujs,
        ReplaceFileWithDir(src("bionic/README.txt")),
        # TODO(usta): add a dangling symlink
    )
