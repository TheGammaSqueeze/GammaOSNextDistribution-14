# Lint as: python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from autotest_lib.client.cros.bluetooth.hcitool import HciToolParser


class HciToolParserTest(unittest.TestCase):
    """Unit test for class HciToolParser."""

    def test_parse_output(self):
        VALID_OUTPUT = ('< HCI Command: ogf 0x04, ocf 0x0003, plen 0\n'
                        '> HCI Event: 0x0e plen 12\n'
                        '  01 03 10 00 BF FE 0F FE DB FF 7B 87')

        VALID_EVENT_TYPE = '0x0e'
        VALID_PLEN_VALUE = 9
        VALID_PASS_STATUS_CODE = 0
        VALID_PAYLOAD = bytearray.fromhex('00 BF FE 0F FE DB FF 7B 87')

        parser_output = HciToolParser.parse_output(VALID_OUTPUT)
        event_type, plen_value, status, payload = parser_output
        self.assertEqual(event_type, VALID_EVENT_TYPE)
        self.assertEqual(plen_value, VALID_PLEN_VALUE)
        self.assertEqual(status, VALID_PASS_STATUS_CODE)
        self.assertEqual(payload, VALID_PAYLOAD)
