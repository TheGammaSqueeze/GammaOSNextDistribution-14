#!/usr/bin/python3
#
# Copyright 2022 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

import unittest

import iproute
import netlink
import sock_diag
import tcp_metrics


class NetlinkTest(unittest.TestCase):

  def _CheckConstant(self, expected, module, value, prefix):
    self.assertEqual(
        expected,
        netlink.NetlinkSocket._GetConstantName(module.__name__, value, prefix))

  def testGetConstantName(self):
    self._CheckConstant("INET_DIAG_INFO", sock_diag, 2, "INET_DIAG_")
    self._CheckConstant("INET_DIAG_BC_S_GE", sock_diag, 2, "INET_DIAG_BC_")
    self._CheckConstant("IFA_ADDRESS", iproute, 1, "IFA_")
    self._CheckConstant("IFA_F_SECONDARY", iproute, 1, "IFA_F_")
    self._CheckConstant("TCP_METRICS_ATTR_AGE", tcp_metrics, 3,
                        "TCP_METRICS_ATTR_")


if __name__ == "__main__":
  unittest.main()
