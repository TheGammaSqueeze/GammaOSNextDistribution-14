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
"""Tests for Repo."""
from pathlib import Path

from pytest_mock import MockFixture

from rrprebuiltupdater.repo import Repo

# pylint: disable=missing-docstring


def test_start_pore(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".pore").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).start_branch("foo")
    run_mock.assert_called_once_with(["pore", "start", "foo"], check=True)


def test_start_repo(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".repo").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).start_branch("foo")
    run_mock.assert_called_once_with(["repo", "start", "foo", "."], check=True)


def test_start_neither(tmp_path: Path, mocker: MockFixture) -> None:
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).start_branch("foo")
    run_mock.assert_called_once_with(["repo", "start", "foo", "."], check=True)


def test_upload_pore(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".pore").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).upload()
    run_mock.assert_called_once_with(["pore", "upload", "--cbr", "."], check=True)


def test_upload_repo(tmp_path: Path, mocker: MockFixture) -> None:
    (tmp_path / ".repo").mkdir()
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).upload()
    run_mock.assert_called_once_with(["repo", "upload", "--cbr", "."], check=True)


def test_upload_neither(tmp_path: Path, mocker: MockFixture) -> None:
    run_mock = mocker.patch("subprocess.run")
    Repo(tmp_path).upload()
    run_mock.assert_called_once_with(["repo", "upload", "--cbr", "."], check=True)
