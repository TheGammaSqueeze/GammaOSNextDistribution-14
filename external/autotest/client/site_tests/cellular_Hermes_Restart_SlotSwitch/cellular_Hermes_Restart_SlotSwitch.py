# Lint as: python2, python3
# Copyright (c) 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import dbus
import logging
import re
import time
import subprocess

from six.moves import range

from autotest_lib.client.bin import test
from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import upstart
from autotest_lib.client.cros.cellular import cellular_logging
from autotest_lib.client.cros.cellular import hermes_constants
from autotest_lib.client.cros.cellular import hermes_utils
from autotest_lib.client.cros.cellular import mm1_constants
from autotest_lib.client.cros.networking import mm1_proxy

log = cellular_logging.SetupCellularLogging('HermesRestartSlotSwitchTest')
class cellular_Hermes_Restart_SlotSwitch(test.test):
    """
    This test restarts hermes or switches slots in between hermes
    operations.

    The test fails if any of the hermes operations fail.

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

    def restart_hermes(self):
        """ Restarts Hermes daemon """
        logging.info('->restart_hermes start')
        upstart.restart_job('hermes')

        # hermes takes 15+ sec to load all apis, wait on hermes dbus not enough
        time.sleep(hermes_constants.HERMES_RESTART_WAIT_SECONDS)
        self.hermes_manager = hermes_utils.connect_to_hermes()

        euicc = self.hermes_manager.get_euicc(self.euicc_path)
        if not euicc:
            raise error.TestFail('restart_hermes operation failed - no euicc')
        euicc.use_test_certs(not self.is_prod_ci)
        logging.info('restart_hermes done')

        if not self.hermes_manager:
            logging.error('restart_hermes failed, no hermes daemon')
            raise error.TestFail('restart_hermes operation failed')

    def qmi_get_active_slot(self):
        """
        Gets current active slot

        @return parse qmicli slot status result and return active slot number

        sample slot status:
        [qrtr://0] Successfully got slots status
        [qrtr://0] 2 physical slots found:
        Physical slot 1:
            Card status: present
            Slot status: inactive
            Logical slot: 1
            ICCID: unknown
            Protocol: uicc
            Num apps: 3
            Is eUICC: yes
            EID: 89033023425120000000001236712288
        Physical slot 2:
            Card status: present
            Slot status: active
            ICCID: unknown
            Protocol: uicc
            Num apps: 0
            Is eUICC: yes
            EID: 89033023425120000000000024200260
        """
        # Read qmi slot status and parse to return current active slot no
        status_cmd = 'qmicli -p -d qrtr://0 --uim-get-slot-status'
        slot_status = subprocess.check_output(status_cmd, shell=True)
        slot_status_list = re.findall('.*active.*', slot_status.decode('utf-8'), re.I)
        for slot_num, status in enumerate(slot_status_list):
            if "inactive" not in status:
                logging.info('active slot is %d', slot_num+1)
                return slot_num+1

    def qmi_switch_slot(self, slot):
        """
        Perform switch slot using qmicli commands

        Command usage:

        localhost ~ # qmicli -d qrtr://0 --uim-switch-slot 1
        error: couldn't switch slots: QMI protocol error (26): 'NoEffect'
        localhost ~ # echo $?
        1
        localhost ~ # qmicli -d qrtr://0 --uim-switch-slot 2
        [qrtr://0] Successfully switched slots
        localhost ~ # echo $?
        0
        """
        # switch to given slot using qmicli command
        switch_cmd = 'qmicli -d qrtr://0 --uim-switch-slot ' + str(slot)
        if (self.qmi_get_active_slot() == slot):
            logging.info('slot switch not needed, same slot %d is active', slot)
            return
        logging.info('call qmicli cmd to switch to:%s cmd:%s', slot, switch_cmd)
        ret = subprocess.call(switch_cmd, shell=True)
        # As we are not testing slot switching here, timeout is to make sure all
        # euiic's are powered up. allowing modem FW to switch slots and load euiccs.
        time.sleep(8)
        logging.info(switch_cmd + ':return value is %d', ret)
        if ret is not 0:
            raise error.TestFail('qmi switch slot failed:', slot)

    def hermes_operations_test(self):
        """
        Perform hermes operations with restart, slotswitch in between

        Do Install, Enable, Disable, Uninstall operations combined with
        operations hermes restart, sim slot switch

        @return Fails the test if any operation fails

        """
        try:
            logging.info('hermes_operations_test start')

            for slot in range(1,3):
                # Do restart, slotswitch and install, enable, disable, uninstall
                self.qmi_switch_slot(slot)
                self.restart_hermes()
                logging.info('Iteration on slot %d', slot)
                if not self.is_prod_ci:
                    installed_iccid = None
                    logging.info('INSTALL:\n')
                    installed_iccid = hermes_utils.install_profile(
                    self.euicc_path, self.hermes_manager, self.is_prod_ci)
                else:
                    installed_iccid = hermes_utils.get_iccid_of_disabled_profile(
                    self.euicc_path, self.hermes_manager, self.is_prod_ci)

                self.qmi_switch_slot(slot)
                self.restart_hermes()
                logging.info('ENABLE:\n')
                hermes_utils.enable_or_disable_profile_test(
                self.euicc_path, self.hermes_manager, installed_iccid, True)

                self.qmi_switch_slot(slot)
                self.restart_hermes()
                logging.info('DISABLE:\n')
                hermes_utils.enable_or_disable_profile_test(
                self.euicc_path, self.hermes_manager, installed_iccid, False)

                if not self.is_prod_ci:
                    self.qmi_switch_slot(slot)
                    self.restart_hermes()
                    logging.info('UNINSTALL:\n')
                    hermes_utils.uninstall_profile_test(
                    self.euicc_path, self.hermes_manager, installed_iccid)

            logging.info('===hermes_operations_test succeeded===\n')
        except dbus.DBusException as e:
            logging.error('hermes_operations failed')
            raise error.TestFail('hermes_operations_test failed', e)

    def run_once(self, test_env, is_prod_ci=False):
        """
        Perform hermes ops with hermes restart and sim slot switch

        """
        self.test_env = test_env
        self.is_prod_ci = is_prod_ci

        self.mm_proxy, self.hermes_manager, self.euicc_path = \
                    hermes_utils.initialize_test(is_prod_ci)

        self.hermes_operations_test()
        logging.info('HermesRestartSlotSwitchTest Completed')
