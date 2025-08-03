#!/usr/bin/env python3.4
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

from acts.test_decorators import test_tracker_info
from WifiNewSetupAutoJoinTest import WifiNewSetupAutoJoinTest

class WifiNewSetupWifiToWifiAutoJoinTest(WifiNewSetupAutoJoinTest):
    """Test Wifi to Wifi auto-switching.

    Note that tests are inherited from WifiNewSetupAutoJoinTest. The only
    modification is in setup_test, where we ensure Wifi is connected before
    switching networks
    """

    def __init__(self, configs):
        super().__init__(configs)
        # Since this test class is inherited, the test cases from the inherited
        # class are also run. Adding the self.tests that are specific to only
        # this class.
        self.tests = (
            "test_wifi_to_wifi_autojoin_Ap1_2g_AP1_20_AP2_95_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2g_AP1_15_AP2_95_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2g_AP1_10_AP2_95_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2g_AP1_5_AP2_95_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_55_AP2_10_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_50_AP2_10_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_45_AP2_10_AP3_95",
            "test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_5_AP2_80_AP3_95",
            "test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_10_AP2_75_AP3_95",
            "test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_15_AP2_70_AP3_95",
            "test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_65_AP2_75_AP3_2",
            "test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_70_AP2_70_AP3_2",
            "test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_75_AP2_65_AP3_2",
            "test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_70_AP2_85_AP3_75",
            "test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_75_AP2_80_AP3_75",
            "test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_75_AP2_75_AP3_75",
            "test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_70_AP3_10",
            "test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_75_AP3_10",
            "test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_80_AP3_10",
            "test_wifi_to_wifi_autojoin_out_of_range",
            "test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_85_AP3_10",
            "test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_80_AP3_10",
            "test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_75_AP3_10",
            "test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_70_AP3_10",
            "test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_70_AP3_10",
            "test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_75_AP3_10",
            "test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_80_AP3_10",
            "test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_15_AP2_65_AP3_75",
            "test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_10_AP2_70_AP3_75",
            "test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_5_AP2_75_AP3_75",
            "test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_10_AP2_80_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_15_AP2_80_AP3_95",
            "test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_20_AP2_80_AP3_95",
        )

    def setup_test(self):
        super().setup_test()
        # Attenuate all other networks except network 0's 2.4 GHz STA, and
        # validate we connect to it.
        # This ensures that we are connected to something at the beginning of
        # each test, in order to test Wifi-to-Wifi switching.
        self.set_attn_and_validate_connection(
            (0, 90, 90, 90),
            self.reference_networks[0]["2g"]['bssid'])

    """ Tests Begin """

    @test_tracker_info(uuid="205dbf01-cb2a-41b7-8945-4b1d0c4fb443")
    def test_wifi_to_wifi_autojoin_Ap1_2g_AP1_20_AP2_95_AP3_95(self):
        super().test_autojoin_Ap1_2g_AP1_20_AP2_95_AP3_95()

    @test_tracker_info(uuid="90d944a0-70fc-4ab0-a786-0ac8e967dbf6")
    def test_wifi_to_wifi_autojoin_Ap1_2g_AP1_15_AP2_95_AP3_95(self):
        super().test_autojoin_Ap1_2g_AP1_15_AP2_95_AP3_95()

    @test_tracker_info(uuid="cab7d874-83e0-444a-b538-b6f959a25091")
    def test_wifi_to_wifi_autojoin_Ap1_2g_AP1_10_AP2_95_AP3_95(self):
        super().test_autojoin_Ap1_2g_AP1_10_AP2_95_AP3_95()

    @test_tracker_info(uuid="b7914791-e801-49b7-a533-7b3a992253c6")
    def test_wifi_to_wifi_autojoin_Ap1_2g_AP1_5_AP2_95_AP3_95(self):
        super().test_autojoin_Ap1_2g_AP1_5_AP2_95_AP3_95()

    @test_tracker_info(uuid="82af1667-0859-4074-9006-70b0a7896f1d")
    def test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_55_AP2_10_AP3_95(self):
        super().test_autojoin_Ap1_2gto5g_AP1_55_AP2_10_AP3_95()

    @test_tracker_info(uuid="a8dae171-f98f-46ea-837e-86614719f1ea")
    def test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_50_AP2_10_AP3_95(self):
        super().test_autojoin_Ap1_2gto5g_AP1_50_AP2_10_AP3_95()

    @test_tracker_info(uuid="f70d6d59-430f-45cf-928f-bbc0609fe07e")
    def test_wifi_to_wifi_autojoin_Ap1_2gto5g_AP1_45_AP2_10_AP3_95(self):
        super().test_autojoin_Ap1_2gto5g_AP1_45_AP2_10_AP3_95()

    @test_tracker_info(uuid="16cedd3a-84e4-4b85-87c5-7658b8b8e5fb")
    def test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_5_AP2_80_AP3_95(self):
        super().test_autojoin_in_AP1_5gto2g_AP1_5_AP2_80_AP3_95()

    @test_tracker_info(uuid="5a2461c8-fa2b-418b-a6d2-9ed980a1932e")
    def test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_10_AP2_75_AP3_95(self):
        super().test_autojoin_in_AP1_5gto2g_AP1_10_AP2_75_AP3_95()

    @test_tracker_info(uuid="340d7d02-30e6-4ed9-a28c-0b2a1050c91c")
    def test_wifi_to_wifi_autojoin_in_AP1_5gto2g_AP1_15_AP2_70_AP3_95(self):
        super().test_autojoin_in_AP1_5gto2g_AP1_15_AP2_70_AP3_95()

    @test_tracker_info(uuid="dadf079f-6634-47e7-852a-4aaad3905f57")
    def test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_65_AP2_75_AP3_2(self):
        super().test_autojoin_swtich_AP1toAp2_AP1_65_AP2_75_AP3_2()

    @test_tracker_info(uuid="29a842a0-427a-41f7-8243-6cce34fac2f7")
    def test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_70_AP2_70_AP3_2(self):
        super().test_autojoin_swtich_AP1toAp2_AP1_70_AP2_70_AP3_2()

    @test_tracker_info(uuid="881c98cc-ddda-43b6-91af-a50152ae47bb")
    def test_wifi_to_wifi_autojoin_switch_AP1toAp2_AP1_75_AP2_65_AP3_2(self):
        super().test_autojoin_swtich_AP1toAp2_AP1_75_AP2_65_AP3_2()

    @test_tracker_info(uuid="a81c2a3b-3fe5-4b8f-9282-4d09718d0e40")
    def test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_70_AP2_85_AP3_75(self):
        super().test_autojoin_Ap2_2gto5g_AP1_70_AP2_85_AP3_75()

    @test_tracker_info(uuid="ac7605cd-1bcd-488f-ba53-70d9d6a6231a")
    def test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_75_AP2_80_AP3_75(self):
        super().test_autojoin_Ap2_2gto5g_AP1_75_AP2_80_AP3_75()

    @test_tracker_info(uuid="7757cae4-9cd4-48ba-a6d9-67e70093a76d")
    def test_wifi_to_wifi_autojoin_Ap2_2gto5g_AP1_75_AP2_75_AP3_75(self):
        super().test_autojoin_Ap2_2gto5g_AP1_75_AP2_75_AP3_75()

    @test_tracker_info(uuid="51ed8923-9409-403f-8001-4da130903d4e")
    def test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_70_AP3_10(self):
        super().test_autojoin_Ap2_5gto2g_AP1_75_AP2_70_AP3_10()

    @test_tracker_info(uuid="f818b6e0-a1af-4a8c-bffd-828752d5f5ba")
    def test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_75_AP3_10(self):
        super().test_autojoin_Ap2_5gto2g_AP1_75_AP2_75_AP3_10()

    @test_tracker_info(uuid="70958be1-bb6b-4cb5-9904-7acb8635ad93")
    def test_wifi_to_wifi_autojoin_Ap2_5gto2g_AP1_75_AP2_80_AP3_10(self):
        super().test_autojoin_Ap2_5gto2g_AP1_75_AP2_80_AP3_10()

    @test_tracker_info(uuid="f9e97a28-3dae-485b-af2a-acb8ac170607")
    def test_wifi_to_wifi_autojoin_out_of_range(self):
        super().test_autojoin_out_of_range()

    @test_tracker_info(uuid="c0f93393-9311-41ea-a5da-781af5101515")
    def test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_85_AP3_10(self):
        super().test_autojoin_Ap2_2g_AP1_75_AP2_85_AP3_10()

    @test_tracker_info(uuid="04f23c21-3f3e-4f84-89a1-cd88c616ee7d")
    def test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_80_AP3_10(self):
        super().test_autojoin_Ap2_2g_AP1_75_AP2_80_AP3_10()

    @test_tracker_info(uuid="9cb970cf-e8f7-4579-9cd4-d1de40aeb231")
    def test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_75_AP3_10(self):
        super().test_autojoin_Ap2_2g_AP1_75_AP2_75_AP3_10()

    @test_tracker_info(uuid="b1c73364-ff1e-450a-9818-d24958a9d9de")
    def test_wifi_to_wifi_autojoin_Ap2_2g_AP1_75_AP2_70_AP3_10(self):
        super().test_autojoin_Ap2_2g_AP1_75_AP2_70_AP3_10()

    @test_tracker_info(uuid="76994fa0-1239-4289-9bce-1ec6841abd0d")
    def test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_70_AP3_10(self):
        super().test_autojoin_in_Ap2_5gto2g_AP1_75_AP2_70_AP3_10()

    @test_tracker_info(uuid="5f459757-9b55-4e49-8549-ed25a8fbbe71")
    def test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_75_AP3_10(self):
        super().test_autojoin_in_Ap2_5gto2g_AP1_75_AP2_75_AP3_10()

    @test_tracker_info(uuid="82f2b16c-622d-44cd-b84c-7c6da63a9b0a")
    def test_wifi_to_wifi_autojoin_in_Ap2_5gto2g_AP1_75_AP2_80_AP3_10(self):
        super().test_autojoin_in_Ap2_5gto2g_AP1_75_AP2_80_AP3_10()

    @test_tracker_info(uuid="a6b31b17-5dba-4709-b603-62f324e4da0c")
    def test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_15_AP2_65_AP3_75(self):
        super().test_autojoin_swtich_AP2toAp1_AP1_15_AP2_65_AP3_75()

    @test_tracker_info(uuid="e69ad2fa-817b-4f57-854b-a2e0cf1ddf20")
    def test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_10_AP2_70_AP3_75(self):
        super().test_autojoin_swtich_AP2toAp1_AP1_10_AP2_70_AP3_75()

    @test_tracker_info(uuid="c3983109-a8ed-4161-aa8b-dfefe0068991")
    def test_wifi_to_wifi_autojoin_switch_AP2toAp1_AP1_5_AP2_75_AP3_75(self):
        super().test_autojoin_swtich_AP2toAp1_AP1_5_AP2_75_AP3_75()

    @test_tracker_info(uuid="6c5ac0b7-a027-44cd-8b03-3a05952bd6f7")
    def test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_10_AP2_80_AP3_95(self):
        super().test_autojoin_Ap1_5gto2g_AP1_10_AP2_80_AP3_95()

    @test_tracker_info(uuid="87e52e7c-ab86-41a9-b301-987c526141a4")
    def test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_15_AP2_80_AP3_95(self):
        super().test_autojoin_Ap1_5gto2g_AP1_15_AP2_80_AP3_95()

    @test_tracker_info(uuid="f48f45ad-3bde-4b19-8c0f-5abf5ece5acd")
    def test_wifi_to_wifi_autojoin_Ap1_5gto2g_AP1_20_AP2_80_AP3_95(self):
        super().test_autojoin_Ap1_5gto2g_AP1_20_AP2_80_AP3_95()

    """ Tests End """
