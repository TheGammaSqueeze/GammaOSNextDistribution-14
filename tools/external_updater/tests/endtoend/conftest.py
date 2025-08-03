#
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
#
"""Common tools for end-to-end tests."""
import subprocess
from pathlib import Path

import pytest

from .treebuilder import TreeBuilder

THIS_DIR = Path(__file__).parent.resolve()
EXTERNAL_UPDATER_DIR = THIS_DIR.parent.parent
ANDROID_DIR = EXTERNAL_UPDATER_DIR.parent.parent


def pytest_addoption(parser: pytest.Parser) -> None:
    """Add custom options to pytest."""
    parser.addoption(
        "--build-updater",
        action="store_true",
        default=True,
        help=(
            "Build external_updater before running tests. This is the default behavior."
        ),
    )
    parser.addoption(
        "--no-build-updater",
        action="store_false",
        dest="build_updater",
        help=(
            "Do not build external_updater before running tests. Only use this option "
            "if you've manually built external_updater. It will make test startup "
            "faster."
        ),
    )


@pytest.fixture(name="should_build_updater", scope="session")
def should_build_updater_fixture(request: pytest.FixtureRequest) -> bool:
    """True if external_updater should be built before running tests."""
    return request.config.getoption("--build-updater")


# Session scope means that this fixture will only run the first time it's used. We don't
# want to re-run soong for every test because it's horrendously slow to do so.
@pytest.fixture(scope="session")
def updater_cmd(should_build_updater: bool) -> list[str]:
    """The command to run for external_updater.

    The result is the prefix of the command that should be used with subprocess.run or
    similar.
    """
    # Running updater.sh should be a more accurate test, but doing so isn't really
    # feasible with a no-op `m external_updater` taking ~10 seconds. Doing that would
    # add 10 seconds to every test. Build the updater once for the first thing that
    # requires it (that's the "session" scope above) and run the PAR directly.
    if should_build_updater:
        subprocess.run(
            [
                ANDROID_DIR / "build/soong/soong_ui.bash",
                "--make-mode",
                "external_updater",
            ],
            check=True,
        )
    return [str("external_updater")]


@pytest.fixture(name="tree_builder")
def tree_builder_fixture(tmp_path: Path) -> TreeBuilder:
    """Creates a TreeBuilder for making test repo trees."""
    return TreeBuilder(tmp_path)
