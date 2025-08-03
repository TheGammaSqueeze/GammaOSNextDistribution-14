#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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

import ipaddress

from acts import logger
from acts.controllers.fuchsia_lib import utils_lib
from acts.controllers.fuchsia_lib.audio_lib import FuchsiaAudioLib
from acts.controllers.fuchsia_lib.basemgr_lib import FuchsiaBasemgrLib
from acts.controllers.fuchsia_lib.bt.avdtp_lib import FuchsiaAvdtpLib
from acts.controllers.fuchsia_lib.bt.ble_lib import FuchsiaBleLib
from acts.controllers.fuchsia_lib.bt.bts_lib import FuchsiaBtsLib
from acts.controllers.fuchsia_lib.bt.gattc_lib import FuchsiaGattcLib
from acts.controllers.fuchsia_lib.bt.gatts_lib import FuchsiaGattsLib
from acts.controllers.fuchsia_lib.bt.hfp_lib import FuchsiaHfpLib
from acts.controllers.fuchsia_lib.bt.rfcomm_lib import FuchsiaRfcommLib
from acts.controllers.fuchsia_lib.bt.sdp_lib import FuchsiaProfileServerLib
from acts.controllers.fuchsia_lib.hardware_power_statecontrol_lib import FuchsiaHardwarePowerStatecontrolLib
from acts.controllers.fuchsia_lib.location.regulatory_region_lib import FuchsiaRegulatoryRegionLib
from acts.controllers.fuchsia_lib.logging_lib import FuchsiaLoggingLib
from acts.controllers.fuchsia_lib.netstack.netstack_lib import FuchsiaNetstackLib
from acts.controllers.fuchsia_lib.ssh import SSHProvider, FuchsiaSSHError
from acts.controllers.fuchsia_lib.wlan_ap_policy_lib import FuchsiaWlanApPolicyLib
from acts.controllers.fuchsia_lib.wlan_deprecated_configuration_lib import FuchsiaWlanDeprecatedConfigurationLib
from acts.controllers.fuchsia_lib.wlan_lib import FuchsiaWlanLib
from acts.controllers.fuchsia_lib.wlan_policy_lib import FuchsiaWlanPolicyLib

DEFAULT_SL4F_PORT = 80
START_SL4F_V2_CMD = 'start_sl4f'


class SL4F:
    """Module for Fuchsia devices to interact with the SL4F tool.

    Attributes:
        ssh: SSHProvider transport to start and stop SL4F.
        address: http address for SL4F server including SL4F port.
        log: Logger for the device-specific instance of SL4F.
    """

    def __init__(self, ssh: SSHProvider,
                 port: int = DEFAULT_SL4F_PORT) -> None:
        """
        Args:
            ssh: SSHProvider transport to start and stop SL4F.
            port: Port for the SL4F server to listen on.
        """
        host = ipaddress.ip_address(ssh.config.host_name)
        if host.version == 4:
            self.address = f'http://{host}:{port}'
        elif host.version == 6:
            self.address = f'http://[{host}]:{port}'

        self.log = logger.create_tagged_trace_logger(f"SL4F | {self.address}")

        try:
            ssh.run(START_SL4F_V2_CMD).stdout
        except FuchsiaSSHError:
            # TODO(fxbug.dev/99331) Remove support to run SL4F in CFv1 mode
            # once ACTS no longer use images that comes with only CFv1 SL4F.
            self.log.warn(
                "Running SL4F in CFv1 mode, "
                "this is deprecated for images built after 5/9/2022, "
                "see https://fxbug.dev/77056 for more info.")
            ssh.stop_v1_component("sl4f")
            ssh.start_v1_component("sl4f")

        utils_lib.wait_for_port(str(host), port)
        self._init_libraries()
        self._verify_sl4f_connection()

    def _init_libraries(self) -> None:
        # Grab commands from FuchsiaAudioLib
        self.audio_lib = FuchsiaAudioLib(self.address)

        # Grab commands from FuchsiaAvdtpLib
        self.avdtp_lib = FuchsiaAvdtpLib(self.address)

        # Grab commands from FuchsiaHfpLib
        self.hfp_lib = FuchsiaHfpLib(self.address)

        # Grab commands from FuchsiaRfcommLib
        self.rfcomm_lib = FuchsiaRfcommLib(self.address)

        # Grab commands from FuchsiaBasemgrLib
        self.basemgr_lib = FuchsiaBasemgrLib(self.address)

        # Grab commands from FuchsiaBleLib
        self.ble_lib = FuchsiaBleLib(self.address)

        # Grab commands from FuchsiaBtsLib
        self.bts_lib = FuchsiaBtsLib(self.address)

        # Grab commands from FuchsiaGattcLib
        self.gattc_lib = FuchsiaGattcLib(self.address)

        # Grab commands from FuchsiaGattsLib
        self.gatts_lib = FuchsiaGattsLib(self.address)

        # Grab commands from FuchsiaHardwarePowerStatecontrolLib
        self.hardware_power_statecontrol_lib = (
            FuchsiaHardwarePowerStatecontrolLib(self.address))

        # Grab commands from FuchsiaLoggingLib
        self.logging_lib = FuchsiaLoggingLib(self.address)

        # Grab commands from FuchsiaNetstackLib
        self.netstack_lib = FuchsiaNetstackLib(self.address)

        # Grab commands from FuchsiaProfileServerLib
        self.sdp_lib = FuchsiaProfileServerLib(self.address)

        # Grab commands from FuchsiaRegulatoryRegionLib
        self.regulatory_region_lib = FuchsiaRegulatoryRegionLib(self.address)

        # Grabs command from FuchsiaWlanDeprecatedConfigurationLib
        self.wlan_deprecated_configuration_lib = (
            FuchsiaWlanDeprecatedConfigurationLib(self.address))

        # Grab commands from FuchsiaWlanLib
        self.wlan_lib = FuchsiaWlanLib(self.address)

        # Grab commands from FuchsiaWlanApPolicyLib
        self.wlan_ap_policy_lib = FuchsiaWlanApPolicyLib(self.address)

        # Grab commands from FuchsiaWlanPolicyLib
        self.wlan_policy_lib = FuchsiaWlanPolicyLib(self.address)

    def _verify_sl4f_connection(self) -> None:
        """Verify SL4F commands can run on server."""

        self.log.info('Verifying SL4F commands can run.')
        try:
            self.wlan_lib.wlanGetIfaceIdList()
        except Exception as err:
            raise ConnectionError(
                f'Failed to connect and run command via SL4F. Err: {err}')
