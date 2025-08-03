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
"""Tests for BuildApi."""

import pytest
from pytest_httpserver import HTTPServer

from rrprebuiltupdater.buildapi import BuildApi

from .mockbuildapi import mock_build_api

# pylint: disable=missing-docstring

TEST_BRANCH = "aosp-rr-dev"
TEST_TARGET = "linux"


async def test_last_build_id(httpserver: HTTPServer) -> None:
    url = mock_build_api(
        httpserver,
        TEST_BRANCH,
        TEST_TARGET,
        {
            "3": b"",
            "2": b"",
            "1": b"",
        },
    )
    assert await BuildApi(TEST_BRANCH, TEST_TARGET, url).get_latest_build_id() == "3"


async def test_most_recent_build_id_not_successful(httpserver: HTTPServer) -> None:
    url = mock_build_api(
        httpserver,
        TEST_BRANCH,
        TEST_TARGET,
        {
            "3": None,
            "2": b"",
            "1": b"",
        },
    )
    assert await BuildApi(TEST_BRANCH, TEST_TARGET, url).get_latest_build_id() == "2"


async def test_no_successful_builds(httpserver: HTTPServer) -> None:
    url = mock_build_api(
        httpserver,
        TEST_BRANCH,
        TEST_TARGET,
        {
            "3": None,
            "2": None,
            "1": None,
        },
    )
    with pytest.raises(RuntimeError):
        await BuildApi(TEST_BRANCH, TEST_TARGET, url).get_latest_build_id()


async def test_no_builds(httpserver: HTTPServer) -> None:
    url = mock_build_api(httpserver, TEST_BRANCH, TEST_TARGET, {})
    with pytest.raises(RuntimeError):
        await BuildApi(TEST_BRANCH, TEST_TARGET, url).get_latest_build_id()
