# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import dbus
import logging

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros.cellular import cellular_logging
from autotest_lib.client.cros.cellular import hermes_utils
from autotest_lib.client.cros.cellular import mm1_constants

log = cellular_logging.SetupCellularLogging('HermesMMInstallEnable')

class cellular_HermesMM_InstallEnable(test.test):
    """
    Tests Install & Enable functions on active/inactive Euicc and
    validates the same on Modem Manager

    This test fails when fails to Install/Enable a given Euicc profile
    or not reflecting same on Modem Manager

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

    def _validate_sim_data(self, euicc_path):
        """
        Validate SIM details that Modem Manager getting from modem

        Check Installed profile presence on an euicc
        Check Profile enabled and same  SIM details reflected on MM

        @param euicc_path: available euicc dbus path as string
        @raise error.TestFail if dbus exception happens or validation fail

        """
        try:
            logging.info('validate_sim_data start')
            euicc = self.hermes_manager.get_euicc(euicc_path)
            if not euicc:
                logging.error('No Euicc enumerated')
                raise error.TestFail('Validation of profile installation on MM'
                                    ' failed as no euicc enumerated')

            modem_proxy = self.mm_proxy.wait_for_modem(
                    mm1_constants.MM_MODEM_POLL_TIME)
            if not modem_proxy:
                logging.info('No modem object yet can not validate')
                raise error.TestFail('Validation of profile installation on MM'
                                    ' failed as no modem')

            # Read MM SIM properties and validate with installed profile data
            sim_proxy = modem_proxy.get_sim()
            sim_props = sim_proxy.properties()

            logging.info('MM-SIM properties are SimIdentifier:%s Active:%s'
                          ' Imsi:%s', sim_props['SimIdentifier'],
                          sim_props['Active'], sim_props['Imsi'])

            if (sim_props['SimIdentifier'] == self.installed_iccid):
                logging.info('===validate_sim_data succeeded===\n')
            else:
                raise error.TestFail('Validation of profile Installation on MM'
                                    ' failed:' + self.installed_iccid)

            return True
        except dbus.DBusException as e:
            logging.error('Resulted Modem Manager Validation error:%s', e)
            raise error.TestFail('MM validation failed')

    def run_once(self, test_env, is_prod_ci=False):
        """ Install & Enable Euicc by enabling a profile """
        self.is_prod_ci = is_prod_ci

        self.mm_proxy, self.hermes_manager, euicc_path = \
                    hermes_utils.initialize_test(is_prod_ci)

        logging.info('Getting profile to enable')
        self.installed_iccid = hermes_utils.get_iccid_of_disabled_profile(
            euicc_path, self.hermes_manager, self.is_prod_ci)

        hermes_utils.enable_or_disable_profile_test(
        euicc_path, self.hermes_manager, self.installed_iccid, True)

        # Validate esim profile enabled is same as MM sim profile
        self._validate_sim_data(euicc_path)

        logging.info('HermesMMInstallEnableTest Completed')
