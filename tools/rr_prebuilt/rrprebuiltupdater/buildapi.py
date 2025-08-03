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
"""Wrapper for the Android build API."""
import logging
from collections.abc import AsyncIterator
from typing import cast

from aiohttp import ClientSession

BUILD_API_URL = "https://androidbuildinternal.googleapis.com"


class BuildApi:
    """Wrapper for the Android build API."""

    def __init__(
        self, branch: str, target: str, build_api_url: str = BUILD_API_URL
    ) -> None:
        self.branch = branch
        self.target = target
        self.build_api_url = build_api_url

    async def get_latest_build_id(self) -> str:
        """Returns the latest successful build ID."""
        logging.debug(
            "Getting latest build ID for branch %s target %s", self.branch, self.target
        )
        async with ClientSession(self.build_api_url) as session:
            async for build_id in self._iter_build_ids(session):
                logging.debug("Checking if %s completed successfully", build_id)
                if await self.build_completed_successfully(session, build_id):
                    return build_id
        raise RuntimeError(
            f"Found no successful builds for {self.branch} {self.target}"
        )

    async def _iter_build_ids(self, session: ClientSession) -> AsyncIterator[str]:
        """Iterates over the latest build IDs for the branch.

        This will arbitrarily stop at whatever limit the build server decides. If we
        can't find the result we're looking for in the first page of results, there's
        probably something wrong enough that the user should probably have a look
        anyway.
        """
        async with session.get(
            f"/android/internal/build/v3/buildIds/{self.branch}",
            params={
                "buildIdSortingOrder": "descending",
                "buildType": "submitted",
            },
        ) as response:
            data = await response.json()
            for build_id_obj in data["buildIds"]:
                yield str(build_id_obj["buildId"])

    async def build_completed_successfully(
        self, session: ClientSession, build_id: str
    ) -> bool:
        """Returns True if the given build completed successfully."""
        async with session.get(
            f"/android/internal/build/v3/builds/{build_id}/{self.target}"
        ) as response:
            data = await response.json()
            return cast(bool, data["successful"])
