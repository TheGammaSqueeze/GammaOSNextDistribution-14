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

from acts.controllers.ap_lib.radio_measurement import BssidInformation, BssidInformationCapabilities, NeighborReportElement, PhyType

EXPECTED_BSSID = '01:23:45:ab:cd:ef'
EXPECTED_BSSID_INFO_CAP = BssidInformationCapabilities(
    spectrum_management=True, qos=True, apsd=True, radio_measurement=True)
EXPECTED_OP_CLASS = 81
EXPECTED_CHAN = 11
EXPECTED_PHY = PhyType.HT
EXPECTED_BSSID_INFO = BssidInformation(capabilities=EXPECTED_BSSID_INFO_CAP,
                                       high_throughput=True)


class RadioMeasurementTest(unittest.TestCase):
    def test_bssid_information_capabilities(self):
        self.assertTrue(EXPECTED_BSSID_INFO_CAP.spectrum_management)
        self.assertTrue(EXPECTED_BSSID_INFO_CAP.qos)
        self.assertTrue(EXPECTED_BSSID_INFO_CAP.apsd)
        self.assertTrue(EXPECTED_BSSID_INFO_CAP.radio_measurement)
        # Must also test the numeric representation.
        self.assertEqual(int(EXPECTED_BSSID_INFO_CAP), 0b111100)

    def test_bssid_information(self):
        self.assertEqual(EXPECTED_BSSID_INFO.capabilities,
                         EXPECTED_BSSID_INFO_CAP)
        self.assertEqual(EXPECTED_BSSID_INFO.high_throughput, True)
        # Must also test the numeric representation.
        self.assertEqual(int(EXPECTED_BSSID_INFO),
                         0b10001111000100000000000000000000)

    def test_neighbor_report_element(self):
        element = NeighborReportElement(bssid=EXPECTED_BSSID,
                                        bssid_information=EXPECTED_BSSID_INFO,
                                        operating_class=EXPECTED_OP_CLASS,
                                        channel_number=EXPECTED_CHAN,
                                        phy_type=EXPECTED_PHY)
        self.assertEqual(element.bssid, EXPECTED_BSSID)
        self.assertEqual(element.bssid_information, EXPECTED_BSSID_INFO)
        self.assertEqual(element.operating_class, EXPECTED_OP_CLASS)
        self.assertEqual(element.channel_number, EXPECTED_CHAN)
        self.assertEqual(element.phy_type, EXPECTED_PHY)


if __name__ == '__main__':
    unittest.main()
