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

import base64


class FuchsiaAudioLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "audio")

    def startOutputSave(self):
        """Starts saving audio output on the device

        Returns:
            Dictionary is success, error if error.
        """
        test_cmd = "audio_facade.StartOutputSave"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def stopOutputSave(self):
        """Stops saving audio output on the device

        Returns:
            Dictionary is success, error if error.
        """
        test_cmd = "audio_facade.StopOutputSave"
        test_args = {}

        return self.send_command(test_cmd, test_args)

    def getOutputAudio(self, save_path):
        """Gets the saved audio in base64 encoding. Use base64.b64decode.

        Args:
            save_path: The path to save the raw audio

        Returns:
            True if success, False if error.
        """
        test_cmd = "audio_facade.GetOutputAudio"
        test_args = {}

        result = self.send_command(test_cmd, test_args)
        if result.get("error") is not None:
            self.log.error("Failed to get recorded audio.")
            return False

        f = open(save_path, "wb")
        f.write(base64.b64decode(result.get('result')))
        f.close()
        self.log.info("Raw audio file captured at {}".format(save_path))
        return True
