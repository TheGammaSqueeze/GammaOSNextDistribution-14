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

import unittest

from acts.controllers.ap_lib.radio_measurement import BssidInformation, NeighborReportElement, PhyType
from acts.controllers.ap_lib.wireless_network_management import BssTransitionCandidateList, BssTransitionManagementRequest

EXPECTED_NEIGHBOR_1 = NeighborReportElement(
    bssid='01:23:45:ab:cd:ef',
    bssid_information=BssidInformation(),
    operating_class=81,
    channel_number=1,
    phy_type=PhyType.HT)
EXPECTED_NEIGHBOR_2 = NeighborReportElement(
    bssid='cd:ef:ab:45:67:89',
    bssid_information=BssidInformation(),
    operating_class=121,
    channel_number=149,
    phy_type=PhyType.VHT)
EXPECTED_NEIGHBORS = [EXPECTED_NEIGHBOR_1, EXPECTED_NEIGHBOR_2]
EXPECTED_CANDIDATE_LIST = BssTransitionCandidateList(EXPECTED_NEIGHBORS)


class WirelessNetworkManagementTest(unittest.TestCase):
    def test_bss_transition_management_request(self):
        request = BssTransitionManagementRequest(
            disassociation_imminent=True,
            abridged=True,
            candidate_list=EXPECTED_NEIGHBORS)
        self.assertTrue(request.disassociation_imminent)
        self.assertTrue(request.abridged)
        self.assertIn(EXPECTED_NEIGHBOR_1, request.candidate_list)
        self.assertIn(EXPECTED_NEIGHBOR_2, request.candidate_list)


if __name__ == '__main__':
    unittest.main()
