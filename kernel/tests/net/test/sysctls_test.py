#!/usr/bin/python3
#
# Copyright 2021 The Android Open Source Project
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

import net_test


class SysctlsTest(net_test.NetworkTest):

  def check(self, f):
    with open(f) as algs_file:
      algs = algs_file.readline().strip().split(' ')
    bad_algs = [a for a in algs if a not in ['cubic', 'reno']]
    msg = ("Obsolete TCP congestion control algorithm found. These "
           "algorithms will decrease real-world networking performance for "
           "users and must be disabled. Found: %s" % bad_algs)
    self.assertEqual(bad_algs, [], msg)

  @unittest.skipUnless(net_test.LINUX_VERSION >= (5, 7, 0), "not yet namespaced")
  def testAllowedCongestionControl(self):
    self.check('/proc/sys/net/ipv4/tcp_allowed_congestion_control')

  @unittest.skipUnless(net_test.LINUX_VERSION >= (5, 7, 0), "not yet namespaced")
  def testAvailableCongestionControl(self):
    self.check('/proc/sys/net/ipv4/tcp_available_congestion_control')

  def testCongestionControl(self):
    self.check('/proc/sys/net/ipv4/tcp_congestion_control')


if __name__ == "__main__":
  unittest.main()
