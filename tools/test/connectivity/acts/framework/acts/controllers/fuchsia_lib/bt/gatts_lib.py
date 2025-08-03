#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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


class FuchsiaGattsLib(BaseLib):

    def __init__(self, addr: str) -> None:
        super().__init__(addr, "gatt_server")

    def publishServer(self, database):
        """Publishes services specified by input args

        Args:
            database: A database that follows the conventions of
                acts_contrib.test_utils.bt.gatt_test_database.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "gatt_server_facade.GattServerPublishServer"
        test_args = {
            "database": database,
        }

        return self.send_command(test_cmd, test_args)

    def closeServer(self):
        """Closes an active GATT server.

        Returns:
            Dictionary, None if success, error if error.
        """
        test_cmd = "gatt_server_facade.GattServerCloseServer"
        test_args = {}

        return self.send_command(test_cmd, test_args)
