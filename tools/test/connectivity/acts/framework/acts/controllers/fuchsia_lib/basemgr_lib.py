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

COMMAND_RESTART_SESSION = 'basemgr_facade.RestartSession'
COMMAND_START_BASEMGR = 'basemgr_facade.StartBasemgr'
COMMAND_KILL_BASEMGR = 'basemgr_facade.KillBasemgr'


class FuchsiaBasemgrLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "basemgr")

    def restartSession(self):
        """Restarts an ongoing basemgr session

        Returns:
            Dictionary:
                error: None, unless an error occurs
                result: 'Success', 'NoSessionToRestart', or None if error
        """
        test_cmd = COMMAND_RESTART_SESSION

        return self.send_command(test_cmd, {})

    def startBasemgr(self):
        """Starts basemgr service

        Returns:
            Dictionary:
                error: None, unless an error occurs
                result: 'Success' or None if error
        """
        test_cmd = COMMAND_START_BASEMGR

        return self.send_command(test_cmd, {})

    def killBasemgr(self):
        """Kill basemgr service, if one is running

        Returns:
            Dictionary:
                error: None, unless an error occurs
                result: 'Success', 'NoBasemgrToKill', or None if error
        """
        test_cmd = COMMAND_KILL_BASEMGR

        return self.send_command(test_cmd, {})
