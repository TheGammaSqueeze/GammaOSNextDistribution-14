#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
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
from acts import signals


class NetstackControllerError(signals.ControllerError):
    pass


class NetstackController:
    """Contains methods related to netstack, to be used in FuchsiaDevice object"""

    def __init__(self, fuchsia_device):
        self.device = fuchsia_device
        self.log = logger.create_tagged_trace_logger(
            'NetstackController for FuchsiaDevice | %s' % self.device.ip)

    def list_interfaces(self):
        """Retrieve netstack interfaces from netstack facade

        Returns:
            List of dicts, one for each interface, containing interface
            information
        """
        response = self.device.sl4f.netstack_lib.netstackListInterfaces()
        if response.get('error'):
            raise NetstackControllerError(
                'Failed to get network interfaces list: %s' %
                response['error'])
        return response['result']
