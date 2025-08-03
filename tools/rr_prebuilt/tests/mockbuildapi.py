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
"""Mocks for the build API."""
from pytest_httpserver import HTTPServer

from rrprebuiltupdater.updater import ARTIFACT_NAME


def mock_build_api(
    server: HTTPServer, branch: str, target: str, builds: dict[str, bytes | None]
) -> str:
    """Creates mock endpoints for the build API."""
    server.expect_request(
        f"/android/internal/build/v3/buildIds/{branch}"
    ).respond_with_json({"buildIds": [{"buildId": i} for i in builds.keys()]})
    for build_id, contents in builds.items():
        server.expect_request(
            f"/android/internal/build/v3/builds/{build_id}/{target}"
        ).respond_with_json({"successful": contents is not None})
        if contents is not None:
            server.expect_request(
                f"/android/internal/build/v3/builds/{build_id}/{target}/"
                f"attempts/latest/artifacts/{ARTIFACT_NAME}/url"
            ).respond_with_data(contents)
    return str(server.url_for(""))
