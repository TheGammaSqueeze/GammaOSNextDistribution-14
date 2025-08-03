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

from acts import logger
from acts.controllers.fuchsia_lib.base_lib import BaseLib


class FuchsiaWlanDeprecatedConfigurationLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "wlan_deprecated")

    def wlanSuggestAccessPointMacAddress(self, addr):
        """ Suggests a mac address to soft AP interface, to support
        cast legacy behavior.

        Args:
            addr: string of mac address to suggest (e.g. '12:34:56:78:9a:bc')
        """
        test_cmd = 'wlan_deprecated.suggest_ap_mac'
        test_args = {'mac': addr}

        return self.send_command(test_cmd, test_args)
