# Copyright 2022 Google LLC
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#     https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

"""Map Bluetooth PTS Man Machine Interface to Pandora gRPC calls."""

__version__ = "0.0.1"

from typing import List
import time
import sys

import grpc

from mmi2grpc.a2dp import A2DPProxy
from mmi2grpc._helpers import format_proxy
from pandora.host_grpc import Host

GRPC_PORT = 8999
MAX_RETRIES = 10


class IUT:
    """IUT class.

    Handles MMI calls from the PTS and routes them to corresponding profile
    proxy which translates MMI calls to gRPC calls to the IUT.
    """
    def __init__(
            self, test: str, args: List[str], port: int = GRPC_PORT, **kwargs):
        """Init IUT class for a given test.

        Args:
            test: PTS test id.
            args: test arguments.
            port: gRPC port exposed by the IUT test server.
        """
        self.port = port
        self.test = test

        # Profile proxies.
        self._a2dp = None

    def __enter__(self):
        """Resets the IUT when starting a PTS test."""
        # Note: we don't keep a single gRPC channel instance in the IUT class
        # because reset is allowed to close the gRPC server.
        with grpc.insecure_channel(f'localhost:{self.port}') as channel:
            Host(channel).Reset(wait_for_ready=True)

    def __exit__(self, exc_type, exc_value, exc_traceback):
        self._a2dp = None

    @property
    def address(self) -> bytes:
        """Bluetooth MAC address of the IUT."""
        with grpc.insecure_channel(f'localhost:{self.port}') as channel:
            tries = 0
            while True:
                try:
                    return Host(channel).ReadLocalAddress(
                        wait_for_ready=True).address
                except grpc.RpcError or grpc._channel._InactiveRpcError:
                    tries += 1
                    if tries >= MAX_RETRIES:
                        raise
                    else:
                        print('Retry', tries, 'of', MAX_RETRIES)
                        time.sleep(1)

    def interact(self,
                 pts_address: bytes,
                 profile: str,
                 test: str,
                 interaction: str,
                 description: str,
                 style: str,
                 **kwargs) -> str:
        """Routes MMI calls to corresponding profile proxy.

        Args:
            pts_address: Bluetooth MAC addres of the PTS in bytes.
            profile: Bluetooth profile.
            test: PTS test id.
            interaction: MMI name.
            description: MMI description.
            style: MMI popup style, unused for now.
        """
        print(f'{profile} mmi: {interaction}', file=sys.stderr)

        # Handles A2DP and AVDTP MMIs.
        if profile in ('A2DP', 'AVDTP'):
            if not self._a2dp:
                self._a2dp = A2DPProxy(
                    grpc.insecure_channel(f'localhost:{self.port}'))
            return self._a2dp.interact(
                test, interaction, description, pts_address)

        # Handles unsupported profiles.
        code = format_proxy(profile, interaction, description)
        error_msg = (
            f'Missing {profile} proxy and mmi: {interaction}\n'
            f'Create a {profile.lower()}.py in mmi2grpc/:\n\n{code}\n'
            f'Then, instantiate the corresponding proxy in __init__.py\n'
            f'Finally, create a {profile.lower()}.proto in proto/pandora/'
            f'and generate the corresponding interface.')

        assert False, error_msg
