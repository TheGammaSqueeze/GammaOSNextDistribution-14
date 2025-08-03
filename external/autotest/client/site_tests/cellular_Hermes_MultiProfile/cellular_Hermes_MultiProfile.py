# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import random

from six.moves import range

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.cellular import cellular_logging
from autotest_lib.client.cros.cellular import hermes_utils

log = cellular_logging.SetupCellularLogging('HermesMultiProfile')

class cellular_Hermes_MultiProfile(test.test):
    """
    Test that Hermes can perform enable/disable operations on multiple profiles

    Prerequisites

    1) For test CI:
       Before running this test on test CI, two profiles need to be created on
    go/stork-profile. The profiles required to be linked to the EID of the dut.
    Profiles with class=operational and type=Android GTS test are known to work
    well with this test.

       We rely on the SMDS event to find the activation codes for the test.
    There is a limit of 99 downloads before profiles to be deleted and
    recreated.(b/181723689)

    2) For prod CI:
       Install two production profiles before running the test.

    """
    version = 1

    def run_once(self, test_env, is_prod_ci=False):
        """ Enable/Disable a profile """
        self.is_prod_ci = is_prod_ci

        self.mm_proxy, self.hermes_manager, euicc_path = \
                    hermes_utils.initialize_test(is_prod_ci)
        # separate testci and prodici procedure to get 2 iccids
        if not is_prod_ci:
            first_iccid = hermes_utils.install_profile_test(euicc_path, self.hermes_manager)
            second_iccid = hermes_utils.install_profile_test(euicc_path, self.hermes_manager)
        else:
            _, installed_profiles = \
            hermes_utils.request_installed_profiles(euicc_path, self.hermes_manager)

            profiles_count = len(installed_profiles)
            if profiles_count < 2:
                raise error.TestError('Two distinct profiles need to be '
                'installed before test begins but count is '+ profiles_count)

            first_iccid = list(installed_profiles.values())[0].iccid
            second_iccid = list(installed_profiles.values())[1].iccid

        if not first_iccid or not second_iccid :
            fail_iccid = 'first' if not first_iccid else 'second'
            raise error.TestError('Could not get' + fail_iccid  + ' iccid')

        if first_iccid == second_iccid:
            raise error.TestError('Two distinct profiles need to be installed '
                'before test begins. Got only ' + first_iccid)

        # first get two profiles, check first_iccid and disable if not disabled,
        # also check second profile is enabled or not. if not enable 2nd one
        logging.info('Disabling first profile to prevent enabling already '
                    'enabled profile in next stress loop. first_iccid:%s, '
                    'second_iccid:%s', first_iccid, second_iccid)
        # get profile state to make sure to keep in expected state
        first_state = hermes_utils.get_profile_state(
        euicc_path, self.hermes_manager, first_iccid)

        if first_state:
            hermes_utils.set_profile_state(
            False, euicc_path, self.hermes_manager, first_iccid, None)

        second_state = hermes_utils.get_profile_state(
        euicc_path, self.hermes_manager, second_iccid)

        if not second_state:
            hermes_utils.set_profile_state(
            True, euicc_path, self.hermes_manager, second_iccid, None)

        logging.info('Stress enable/disable profiles')
        for i in range(1,5):
            logging.info('Iteration :: %d', i)
            for iccid in [first_iccid, second_iccid]:
                if not hermes_utils.get_profile_state(
                    euicc_path, self.hermes_manager, iccid):
                    logging.info('Enabling profile:%s', iccid)
                    hermes_utils.enable_or_disable_profile_test(
                        euicc_path, self.hermes_manager, iccid, True)
                explicitly_disable_profile = random.choice([True,False])
                if (explicitly_disable_profile):
                    if hermes_utils.get_profile_state(
                        euicc_path, self.hermes_manager, iccid):
                        logging.info('Disabling profile:%s', iccid)
                        hermes_utils.enable_or_disable_profile_test(
                            euicc_path, self.hermes_manager, iccid, False)

        logging.info('HermesMultiProfileTest Completed')
