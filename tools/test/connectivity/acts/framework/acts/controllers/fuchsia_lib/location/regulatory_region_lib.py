#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

from acts.controllers.fuchsia_lib.base_lib import BaseLib


class FuchsiaRegulatoryRegionLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "location_regulatory_region")

    # TODO(fxb/46727): Provide an analagous call to check the region
    # configured into the driver.
    def setRegion(self, region_code):
        """Set regulatory region.

        Args:
            region_code: 2-byte ASCII string.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "location_regulatory_region_facade.set_region"
        test_args = {"region": region_code}

        return self.send_command(test_cmd, test_args)
