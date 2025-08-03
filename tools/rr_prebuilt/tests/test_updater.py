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
"""Tests for the rr prebuilt updater."""
import shutil
import textwrap
import unittest.mock
from pathlib import Path

import pytest
from pytest_httpserver import HTTPServer
from pytest_mock import MockFixture

from rrprebuiltupdater.updater import Updater

from .mockbuildapi import mock_build_api

# pylint: disable=missing-docstring

TEST_BRANCH = "aosp-rr-dev"
TEST_TARGET = "linux"


@pytest.fixture(name="archive_contents")
def archive_contents_fixture(tmp_path: Path) -> bytes:
    archive_dir = tmp_path / "archive"
    archive_dir.mkdir()
    (archive_dir / "a").write_text("a")
    (archive_dir / "b").write_text("b")
    return Path(
        shutil.make_archive(
            str(tmp_path / "archive"), "bztar", tmp_path, archive_dir.name
        )
    ).read_bytes()


def test_replace_current_prebuilt_with(tmp_path: Path, mocker: MockFixture) -> None:
    install_path = tmp_path / "install"
    install_path.mkdir()
    (install_path / "a").write_text("b")
    (install_path / "c").write_text("d")

    extracted_path = tmp_path / "extracted"
    extracted_path.mkdir()
    (extracted_path / "a").write_text("a")
    (extracted_path / "b").write_text("b")

    def mock_run(
        cmd: list[str | Path],
        check: bool,  # pylint: disable=unused-argument
    ) -> None:
        match cmd:
            case ["git", "rm", "-rf", path]:
                if Path(path).exists():
                    shutil.rmtree(path)

    run_mock = mocker.patch("subprocess.run")
    run_mock.side_effect = mock_run
    Updater("1234", install_path=install_path).replace_current_prebuilt_with(
        extracted_path
    )

    run_mock.assert_has_calls(
        [
            unittest.mock.call(["git", "rm", "-rf", install_path], check=True),
            unittest.mock.call(["git", "add", install_path], check=True),
        ]
    )
    assert run_mock.call_count == 2

    assert (install_path / "a").read_text() == "a"
    assert (install_path / "b").read_text() == "b"
    assert set(install_path.iterdir()) == {install_path / "a", install_path / "b"}


def test_replace_current_prebuilt_with_no_existing_prebuilt(
    tmp_path: Path, mocker: MockFixture
) -> None:
    install_path = tmp_path / "install"

    extracted_path = tmp_path / "extracted"
    extracted_path.mkdir()
    (extracted_path / "a").write_text("a")
    (extracted_path / "b").write_text("b")

    run_mock = mocker.patch("subprocess.run")
    Updater("1234", install_path=install_path).replace_current_prebuilt_with(
        extracted_path
    )

    run_mock.assert_called_once_with(["git", "add", install_path], check=True)

    assert (install_path / "a").read_text() == "a"
    assert (install_path / "b").read_text() == "b"
    assert set(install_path.iterdir()) == {install_path / "a", install_path / "b"}


async def test_extracted_artifact(
    archive_contents: bytes, httpserver: HTTPServer
) -> None:
    url = mock_build_api(
        httpserver, TEST_BRANCH, TEST_TARGET, {"1234": archive_contents}
    )
    updater = Updater("1234", build_api_url=url)
    async with updater.extracted_artifact() as extracted_path:
        assert (extracted_path / "a").read_text() == "a"
        assert (extracted_path / "b").read_text() == "b"


def test_commit(mocker: MockFixture) -> None:
    run_mock = mocker.patch("subprocess.run")
    Updater("1234").commit()
    run_mock.assert_called_once_with(
        [
            "git",
            "commit",
            "-m",
            textwrap.dedent(
                """\
                Update rr prebuilts to build 1234.

                Bug: None
                Test: treehugger
                """
            ),
        ],
        check=True,
    )


def test_upload(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".repo").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Updater("", should_upload=True, repo_root=tmp_path).upload()
    run_mock.assert_called_once_with(["repo", "upload", "--cbr", "."], check=True)


def test_no_upload(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".repo").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Updater("", should_upload=False).upload()
    run_mock.assert_not_called()


async def test_run(
    tmp_path: Path, httpserver: HTTPServer, archive_contents: bytes, mocker: MockFixture
) -> None:
    repo_root = tmp_path / "repo"
    (repo_root / ".repo").mkdir(parents=True)

    install_path = tmp_path / "install"
    install_path.mkdir()
    (install_path / "a").write_text("b")
    (install_path / "c").write_text("d")

    def mock_run(
        cmd: list[str | Path],
        check: bool,  # pylint: disable=unused-argument
    ) -> None:
        match cmd:
            case ["git", "rm", "-rf", path]:
                if Path(path).exists():
                    shutil.rmtree(path)

    run_mock = mocker.patch("subprocess.run")
    run_mock.side_effect = mock_run

    url = mock_build_api(
        httpserver, TEST_BRANCH, TEST_TARGET, {"1234": archive_contents}
    )
    await Updater(
        "1234", build_api_url=url, install_path=install_path, repo_root=repo_root
    ).run()

    run_mock.assert_has_calls(
        [
            unittest.mock.call(
                ["repo", "start", "update-rr-prebuilt-1234", "."], check=True
            ),
            unittest.mock.call(["git", "rm", "-rf", install_path], check=True),
            unittest.mock.call(["git", "add", install_path], check=True),
            unittest.mock.call(
                [
                    "git",
                    "commit",
                    "-m",
                    textwrap.dedent(
                        """\
                        Update rr prebuilts to build 1234.

                        Bug: None
                        Test: treehugger
                        """
                    ),
                ],
                check=True,
            ),
            unittest.mock.call(["repo", "upload", "--cbr", "."], check=True),
        ],
    )

    assert (install_path / "a").read_text() == "a"
    assert (install_path / "b").read_text() == "b"
    assert set(install_path.iterdir()) == {install_path / "a", install_path / "b"}
