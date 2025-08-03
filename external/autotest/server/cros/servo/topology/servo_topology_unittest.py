#!/usr/bin/python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest

import common
from autotest_lib.server.cros.servo.topology import servo_topology

# pylint: disable=missing-docstring


class ConvertDataToStringTests(unittest.TestCase):
    def test_expect_empty_when_data_is_none(self):
        data = servo_topology._convert_topology_to_string(None)
        self.assertEqual(data, '')

    def test_expect_empty_when_data_is_empty(self):
        data = servo_topology._convert_topology_to_string({})
        self.assertEqual(data, '')

    def test_expect_string_when_data_is_provided(self):
        expected_string = (
                'eyJtYWluIjp7InVzYl9odWJfcG9ydCI6IjYuNC4xIiwic2VyaWFsIjoiQzE'
                '5MDMxNDU1OTEhQCMkJV4qKClcIic6OycnXFw/L2BcIiwiLCJ0eXBlIjoic2'
                'Vydm9fdjQiLCJzeXNmc19wcm9kdWN0IjoiU2Vydm8gVjQifSwiY2hpbGRyZ'
                'W4iOlt7InVzYl9odWJfcG9ydCI6IjYuNC4yIiwic2VyaWFsIjoiMDY4MUQw'
                'M0EtOTJEQ0NENjQiLCJ0eXBlIjoiY2NkX2NyNTAiLCJzeXNmc19wcm9kdWN'
                '0IjoiQ3I1MCJ9XX0=')
        data = {
                "main": {
                        "usb_hub_port": "6.4.1",
                        "serial": "C1903145591!@#$%^*()\"':;''\\?/`\",",
                        "type": "servo_v4",
                        "sysfs_product": "Servo V4"
                },
                "children": [{
                        "usb_hub_port": "6.4.2",
                        "serial": "0681D03A-92DCCD64",
                        "type": "ccd_cr50",
                        "sysfs_product": "Cr50"
                }]
        }

        result = servo_topology._convert_topology_to_string(data)
        self.assertEqual(result, expected_string)

    def test_expect_string_when_data_is_provided2(self):
        expected_string = (
                'eyJtYWluIjp7InVzYl9odWJfcG9ydCI6IiIsInNlcmlhbCI6IiIsInR5cGU'
                'iOiIiLCJzeXNmc19wcm9kdWN0IjoiIn0sImNoaWxkcmVuIjpbXX0=')
        data = {
                "main": {
                        "usb_hub_port": "",
                        "serial": "",
                        "type": "",
                        "sysfs_product": ""
                },
                "children": []
        }

        result = servo_topology._convert_topology_to_string(data)
        self.assertEqual(result, expected_string)


class ParseStringToDataTests(unittest.TestCase):
    def test_expect_none_when_string_empty(self):
        data = servo_topology._parse_string_as_topology("")
        self.assertEqual(data, None)

    def test_expect_empty_data_when_string_empty_instance(self):
        data = servo_topology._parse_string_as_topology('{}')
        self.assertEqual(data, None)

    def test_expect_data_when_string_is_provided(self):
        b64_data = (
                'eyJtYWluIjp7InVzYl9odWJfcG9ydCI6IjYuNC4xIiwic2VyaWFsIjoiQzE5'
                'MDMxNDU1OTEhQCMkJV4mKigpXCInOjsnJ1xcPy9gflwiLCIsInR5cGUiOiJz'
                'ZXJ2b192NCIsInN5c2ZzX3Byb2R1Y3QiOiJTZXJ2byBWNCJ9LCJjaGlsZHJl'
                'biI6W3sidXNiX2h1Yl9wb3J0IjoiNi40LjIiLCJzZXJpYWwiOiIwNjgxRDAz'
                'QS05MkRDQ0Q2NCIsInR5cGUiOiJjY2RfY3I1MCIsInN5c2ZzX3Byb2R1Y3Qi'
                'OiJDcjUwIn1dfQ==')
        data = {
                "main": {
                        "usb_hub_port": "6.4.1",
                        "serial": "C1903145591!@#$%^&*()\"':;''\\?/`~\",",
                        "type": "servo_v4",
                        "sysfs_product": "Servo V4"
                },
                "children": [{
                        "usb_hub_port": "6.4.2",
                        "serial": "0681D03A-92DCCD64",
                        "type": "ccd_cr50",
                        "sysfs_product": "Cr50"
                }]
        }

        result = servo_topology._parse_string_as_topology(b64_data)
        self.assertEqual(result, data)

    def test_expect_data_when_string_is_provided2(self):
        b64_data = (
                'eyJtYWluIjp7InVzYl9odWJfcG9ydCI6IiIsInNlcmlhbCI6IiIsInR5cGU'
                'iOiIiLCJzeXNmc19wcm9kdWN0IjoiIn0sImNoaWxkcmVuIjpbXX0=')
        data = {
                "main": {
                        "usb_hub_port": "",
                        "serial": "",
                        "type": "",
                        "sysfs_product": ""
                },
                "children": []
        }

        result = servo_topology._parse_string_as_topology(b64_data)
        self.assertEqual(result, data)


if __name__ == '__main__':
    unittest.main()
