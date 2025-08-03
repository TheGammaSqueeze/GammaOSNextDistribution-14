#!/usr/bin/env python3
#
#   Copyright 2023 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#           http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.
"""Provides unittests for cmw500_scenario_generator"""

from __future__ import print_function

import unittest

from acts.controllers.rohdeschwarz_lib import cmw500_scenario_generator as cg


class CMW500ScenarioTest(unittest.TestCase):
    """Unit test for CMW500Scenario class."""

    def test_1CC_1x1(self):
        """Verify name and route text for: 1CC, MIMO: 1x1, BANDS: 3"""
        self._gen_equals((3, ), (1, ), "SCEL", "SUA1,RF1C,RX1,RF1C,TX1")

    def test_1CC_2x2(self):
        """Verify name and route text for: 1CC, MIMO: 2x2, BANDS: 3"""
        self._gen_equals((3, ), (2, ), "TRO",
                         "SUA1,RF1C,RX1,RF1C,TX1,RF3C,TX2")

    def test_2CC_4x4(self):
        """Verify name and route text for: 1CC, MIMO: 4x4, BANDS: 3"""
        self._gen_equals(
            (3, ),
            (4, ),
            "AD",
            "SUA1,RF1C,RX1,RF1C,TX1,RF3C,TX2,RF2C,TX3,RF4C,TX4",
        )

    def _gen_equals(self, bands, antennas, scenario_name, scenario_route):
        scenario = cg.get_scenario(bands, antennas)
        print("want scenario name: {}".format(scenario_name))
        print("got scenario name: {}".format(scenario.name))
        self.assertTrue(scenario.name == scenario_name)
        print("want scenario route: {}".format(scenario_route))
        print("got scenario route: {}".format(scenario.routing))
        self.assertTrue(scenario.routing == scenario_route)


class CMW500ScenarioGeneratorTest(unittest.TestCase):
    """Unit test for CMW500ScenarioGenerator class."""

    def test_2CC_2x2_2x2_B3B3(self):
        """Verify generated output for: 2CC, MIMO: 2x2 2x2, BANDS: 3 3"""
        self._gen_equals((3, 3), (2, 2), [[(1, 1), (3, 2)], [(1, 1), (3, 2)]])

    def test_2CC_2x2_2x2_B2B3(self):
        """Verify generated output for: 2CC, MIMO: 2x2 2x2, BANDS: 2 3"""
        self._gen_equals((2, 3), (2, 2), [[(1, 1), (3, 2)], [(1, 3), (3, 4)]])

    def test_2CC_4x4_4x4_B3B3(self):
        """Verify generated output for: 3CC, MIMO: 4x4 4x4, BANDS: 3 3"""
        self._gen_equals(
            (3, 3),
            (4, 4),
            [
                [(1, 1), (3, 2), (2, 3), (4, 4)],
                [(1, 1), (3, 2), (2, 3), (4, 4)],
            ],
        )

    def test_3CC_2x2_1x1_1x1_B3B5B7(self):
        """Verify generated output for: 3CC, MIMO: 2x2 1x1 1x1, BANDS: 3 5 7"""
        self._gen_equals((3, 5, 7), (2, 1, 1), [[(1, 1),
                                                 (3, 2)], [(1, 3)], [(3, 4)]])

    def test_3CC_2x2_2x2_2x2_B3B5B3(self):
        """Verify generated output for: 3CC, MIMO: 2x2 2x2 2x2, BANDS: 3 5 3"""
        self._gen_equals(
            (3, 5, 3),
            (2, 2, 2),
            [[(1, 1), (3, 2)], [(1, 3), (3, 4)], [(1, 1), (3, 2)]],
        )

    def test_3CC_2x2_2x2_2x2_B3B5B7(self):
        """Verify generated output for: 3CC, MIMO: 2x2 2x2 2x2, BANDS: 3 5 7"""
        self._raises((3, 5, 7), (2, 2, 2))

    def test_4CC_1x1_1x1_1x1_1x1_B2B3B5B7(self):
        """Verify generated output for: 4CC, MIMO: 2x2 2x2 2x2, BANDS: 2 3 5 7"""
        self._gen_equals((2, 3, 5, 7), (1, 1, 1, 1),
                         [[(1, 1)], [(1, 3)], [(3, 2)], [(3, 4)]])

    def test_4CC_2x2_2x2_2x2_2x2_B2B3B2B3(self):
        """Verify generated output for: 4CC, MIMO: 2x2 2x2 2x2 2x2, BANDS: 2 3 2 3"""
        self._gen_equals(
            (2, 3, 2, 3),
            (2, 2, 2, 2),
            [
                [(1, 1), (3, 2)],
                [(1, 3), (3, 4)],
                [(1, 1), (3, 2)],
                [(1, 3), (3, 4)],
            ],
        )

    def _gen_equals(self, bands, antennas, want):
        # ensure expected port configurations match
        got = self._gen(bands, antennas)
        print(f"want config: {want}")
        print(f"got config: {got}")
        self.assertTrue(got == want)
        # ensure antenna matches with original
        scenario = cg.get_scenario(bands, antennas)
        antennas_got = cg.get_antennas(scenario.name)
        print(f"want antennas: {antennas}")
        print(f"got antennas: {antennas_got}")
        self.assertTrue(tuple(antennas_got) == tuple(antennas))

    def _raises(self, bands, antennas):
        with self.assertRaises(ValueError):
            self._gen(bands, antennas)

    def _gen(self, bands, antennas):
        gen = cg.CMW500ScenarioGenerator()
        return [gen.get_next(b, a) for b, a in zip(bands, antennas)]


if __name__ == "__main__":
    unittest.main()
