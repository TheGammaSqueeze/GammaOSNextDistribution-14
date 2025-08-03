#!/usr/bin/env python3
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

from blueberry.tests.gd.cert.cert_self_test import CertSelfTest
from blueberry.tests.gd.hal.simple_hal_test import SimpleHalTest
from blueberry.tests.gd.hci.acl_manager_test import AclManagerTest
from blueberry.tests.gd.hci.controller_test import ControllerTest
from blueberry.tests.gd.hci.direct_hci_test import DirectHciTest
from blueberry.tests.gd.hci.le_acl_manager_test import LeAclManagerTest
from blueberry.tests.gd.hci.le_advertising_manager_test import LeAdvertisingManagerTest
from blueberry.tests.gd.hci.le_scanning_manager_test import LeScanningManagerTest
from blueberry.tests.gd.hci.le_scanning_with_security_test import LeScanningWithSecurityTest
from blueberry.tests.gd.iso.le_iso_test import LeIsoTest
from blueberry.tests.gd.l2cap.classic.l2cap_performance_test import L2capPerformanceTest
from blueberry.tests.gd.l2cap.classic.l2cap_test import L2capTest
from blueberry.tests.gd.l2cap.le.dual_l2cap_test import DualL2capTest
from blueberry.tests.gd.l2cap.le.le_l2cap_test import LeL2capTest
from blueberry.tests.gd.neighbor.neighbor_test import NeighborTest
from blueberry.tests.gd.security.le_security_test import LeSecurityTest
from blueberry.tests.gd.security.security_test import SecurityTest
from blueberry.tests.gd.shim.shim_test import ShimTest
from blueberry.tests.gd.shim.stack_test import StackTest

from mobly import suite_runner

ALL_TESTS = {
    CertSelfTest, SimpleHalTest, AclManagerTest, ControllerTest, DirectHciTest, LeAclManagerTest,
    LeAdvertisingManagerTest, LeScanningManagerTest, LeScanningWithSecurityTest, LeIsoTest, L2capPerformanceTest,
    L2capTest, DualL2capTest, LeL2capTest, NeighborTest, LeSecurityTest, SecurityTest, ShimTest, StackTest
}

DISABLED_TESTS = set()

ENABLED_TESTS = list(ALL_TESTS - DISABLED_TESTS)

if __name__ == '__main__':
    suite_runner.run_suite(ENABLED_TESTS)
