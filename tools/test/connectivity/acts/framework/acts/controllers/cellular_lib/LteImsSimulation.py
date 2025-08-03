#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
import time

import acts.controllers.anritsu_lib.md8475a as md8475a
from acts.controllers.cellular_lib.LteSimulation import LteSimulation

# Time to wait for Anritsu's IMS CSCF state change
MAX_WAIT_TIME_IMS_CSCF_STATE = 30
# default ims virtual network id for Anritsu ims call test.
DEFAULT_IMS_VIRTUAL_NETWORK_ID = 1


class LteImsSimulation(LteSimulation):

    LTE_BASIC_SIM_FILE = 'VoLTE_ATT_Sim.wnssp'
    LTE_BASIC_CELL_FILE = 'VoLTE_ATT_Cell.wnscp'

    def attach(self):
        """ After attaching verify the UE has registered with the IMS server.

        Returns:
            True if the phone was able to attach, False if not.
        """

        if not super().attach():
            return False

        # The phone should have registered with the IMS server before attaching.
        # Make sure the IMS registration was successful by verifying the CSCF
        # status is SIP IDLE.
        if not _wait_for_ims_cscf_status(
                self.log,
                self.simulator.anritsu,
                DEFAULT_IMS_VIRTUAL_NETWORK_ID,
                md8475a.ImsCscfStatus.SIPIDLE.value):
            self.log.error('UE failed to register with the IMS server.')
            return False

        return True


def _wait_for_ims_cscf_status(log,
                              anritsu_handle,
                              virtual_network_id,
                              status,
                              timeout=MAX_WAIT_TIME_IMS_CSCF_STATE):
    """ Wait for IMS CSCF to be in expected state.

    Args:
        log: log object
        anritsu_handle: anritsu object
        virtual_network_id: virtual network id to be monitored
        status: expected status
        timeout: wait time
    """
    sleep_interval = 1
    wait_time = timeout
    while wait_time > 0:
        if status == anritsu_handle.get_ims_cscf_status(virtual_network_id):
            return True
        time.sleep(sleep_interval)
        wait_time = wait_time - sleep_interval
    return False
