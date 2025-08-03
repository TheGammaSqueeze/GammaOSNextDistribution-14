#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

from blueberry.tests.gd.cert import gd_base_test
from blueberry.tests.gd.hci.le_scanning_manager_test_blueberry import LeScanningManagerTestBase
from mobly import test_runner


class LeExtendedScanningManagerTest(gd_base_test.GdBaseTestClass, LeScanningManagerTestBase):

    def setup_class(self):
        gd_base_test.GdBaseTestClass.setup_class(self, dut_module='HCI_INTERFACES', cert_module='HCI_INTERFACES')

    def setup_test(self):
        gd_base_test.GdBaseTestClass.set_controller_properties_path(self,
                                                                    'blueberry/tests/gd/hci/le_extended_config.json')
        gd_base_test.GdBaseTestClass.setup_test(self)
        LeScanningManagerTestBase.setup_test(self, self.cert, self.dut)

    def teardown_test(self):
        LeScanningManagerTestBase.teardown_test(self)
        gd_base_test.GdBaseTestClass.teardown_test(self)


if __name__ == '__main__':
    test_runner.main()
