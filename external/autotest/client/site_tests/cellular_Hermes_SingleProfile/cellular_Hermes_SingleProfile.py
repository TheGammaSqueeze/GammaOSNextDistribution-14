# Lint as: python2, python3
# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.bin import test
from autotest_lib.client.cros.cellular import cellular_logging
from autotest_lib.client.cros.cellular import hermes_utils

log = cellular_logging.SetupCellularLogging('HermesSingleProfileTest')

class cellular_Hermes_SingleProfile(test.test):
    """
    Tests Enable and Disable functions on active/inactive Euicc present

    This test fails when not able to Enable/Disable a given Euicc profile

    Prerequisites

    1) For test CI:
       Before running this test on test CI, a profile needs to be created on
    go/stork-profile. The profile needs to be linked to the EID of the dut.
    Profiles with class=operational and type=Android GTS test are known to work
    well with this test.

       We rely on the SMDS event to find the activation code for the test.
    There is a limit of 99 downloads before the profile needs to be deleted and
    recreated.(b/181723689)

    2) For prod CI:
       Install a production profile before running the test.

    """
    version = 1

    def run_once(self, is_prod_ci=False):
        """ Enable Disable Euicc by enabling or disabling a profile """
        self.is_prod_ci = is_prod_ci

        self.mm_proxy, self.hermes_manager, euicc_path = \
            hermes_utils.initialize_test(is_prod_ci)

        self.installed_iccid = None

        self.installed_iccid = hermes_utils.install_profile(
        euicc_path, self.hermes_manager, self.is_prod_ci)

        hermes_utils.enable_or_disable_profile_test(
        euicc_path, self.hermes_manager, self.installed_iccid, True)

        hermes_utils.enable_or_disable_profile_test(
        euicc_path, self.hermes_manager, self.installed_iccid, False)

        if not self.is_prod_ci:
            hermes_utils.uninstall_profile_test(
            euicc_path, self.hermes_manager, self.installed_iccid)

        logging.info('HermesSingleProfileTest Completed')
