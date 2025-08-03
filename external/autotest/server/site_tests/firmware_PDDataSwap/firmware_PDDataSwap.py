# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest
from autotest_lib.server.cros.servo import pd_device


class firmware_PDDataSwap(FirmwareTest):
    """
    Servo based USB PD data role swap test

    Pass critera is all data role swaps complete, or
    a reject control message is received from the DUT in the
    cases where the swap does not complete.

    """

    version = 1
    PD_ROLE_DELAY = 0.5
    PD_CONNECT_DELAY = 4
    DATA_SWAP_ITERATIONS = 10
    # Upward facing port data role
    UFP = 'UFP'
    # Downward facing port data role
    DFP = 'DFP'
    # Swap Result Tables
    swap_attempt = {
        ('rx', DFP): 0,
        ('rx', UFP): 0,
        ('tx', DFP): 0,
        ('tx', UFP): 0
    }
    swap_failure = {
        ('rx', DFP): 0,
        ('rx', UFP): 0,
        ('tx', DFP): 0,
        ('tx', UFP): 0
    }

    def _get_data_role(self, port):
        """Get data role of PD connection

        @param console: pd console object for uart access
        @param port: 0/1 pd port of current connection

        @returns: 'DFP' or 'UFP'
        """
        role = port.get_pd_role()
        m = re.search('[\w]+-([\w]+)', role)
        return m.group(1)

    def _get_remote_role(self, local_role):
        """Invert data role

        @param local_role: data role to be flipped

        @returns: flipped data role value
        """
        if local_role == self.DFP:
            return self.UFP
        else:
            return self.DFP

    def _change_dut_power_role(self):
        """Force power role change via PDTester

        @returns True is power role change is successful
        """
        PDTESTER_SRC_VOLTAGE = 5
        PDTESTER_SNK_VOLTAGE = 0
        pd_state = self.dut_port.get_pd_state()
        if self.dut_port.is_src():
            # DUT is currently a SRC, so change to SNK
            # Use PDTester method to ensure power role change
            self.pdtester.charge(PDTESTER_SRC_VOLTAGE)
        else:
            # DUT is currently a SNK, so change it to a SRC.
            self.pdtester.charge(PDTESTER_SNK_VOLTAGE)
        # Wait for change to take place
        time.sleep(self.PD_CONNECT_DELAY)
        pdtester_state = self.pdtester_port.get_pd_state()
        # Current PDTester state should equal DUT state when called
        return bool(pd_state == pdtester_state)

    def _send_data_swap_get_reply(self, port):
        """Send data swap request, get PD control msg reply

        The PD console debug mode is enabled prior to sending
        a pd data role swap request message. This allows the
        control message reply to be extracted. The debug mode
        is disabled prior to exiting.

        @param port: pd device object

        @returns: PD control header message
        """
        # Enable PD console debug mode to show control messages
        port.utils.enable_pd_console_debug()
        cmd = 'pd %d swap data' % port.port
        m = port.utils.send_pd_command_get_output(cmd, ['RECV\s([\w]+)'])
        ctrl_msg = int(m[0][1], 16) & port.utils.PD_CONTROL_MSG_MASK
        port.utils.disable_pd_console_debug()
        return ctrl_msg

    def _attempt_data_swap(self, direction):
        """Perform a data role swap request

        Data swap requests can be either initiated by the DUT or received
        by the DUT. This direction determines which PD console is used
        to initiate the swap command. The data role before and after
        the swap command are compared to determine if it took place.

        Even if data swap capability is advertised, a PD device is allowed
        to reject the request. Therefore, not swapping isn't itself a
        failure. When PDTester is used to initate the request, the debug
        mode is enabled which allows the control message from the DUT to
        be analyzed. If the swap does not occur, but the request is rejected
        by the DUT then that is not counted as a failure.

        @param direction: rx or tx from the DUT perspective

        @returns PD control reply message for tx swaps, 0 otherwise
        """
        # Get starting DUT data role
        dut_dr = self._get_data_role(self.dut_port)
        self.swap_attempt[(direction, dut_dr)] += 1
        if direction == 'tx':
            # Initiate swap request from the DUT
            cmd = 'pd %d swap data' % self.dut_port.port
            # Send the 'swap data' command
            self.dut_port.utils.send_pd_command(cmd)
            # Not using debug mode, so there is no reply message
            ctrl = 0
        else:
            # Initiate swap request from PDTester
            ctrl = self._send_data_swap_get_reply(self.pdtester_port)

        time.sleep(self.PD_ROLE_DELAY)
        # Get DUT current data role
        swap_dr = self._get_data_role(self.dut_port)
        logging.info('%s swap attempt: prev = %s, new = %s, msg = %s',
                      direction, dut_dr, swap_dr, ctrl)
        if (dut_dr == swap_dr and
                ctrl != self.dut_port.utils.PD_CONTROL_MSG_DICT['Reject']):
            self.swap_failure[(direction, dut_dr)] += 1
        return ctrl

    def _execute_data_role_swap_test(self):
        """Execute a series of data role swaps

        Attempt both rx and tx data swaps, from perspective of DUT.
        Even if the DUT advertises support, it can
        reject swap requests when already in the desired data role. For
        example many devices will not swap if already in DFP mode.
        However, PDTester should always accept a request. Therefore,
        when a swap failed on a rx swap, then that is followed by
        a tx swap attempt.

        @param pd_port: port number of DUT PD connection
        """
        for attempt in range(self.DATA_SWAP_ITERATIONS):
            # Use the same direction for every 2 loop iterations
            if attempt & 2:
                direction = 'tx'
            else:
                direction = 'rx'
            ctrl_msg = self._attempt_data_swap(direction)
            if (direction == 'rx' and
                    ctrl_msg ==
                    self.dut_port.utils.PD_CONTROL_MSG_DICT['Reject']):
                # Use pdtester initiated swap to change roles
                self._attempt_data_swap('tx')

    def _test_data_swap_reject(self):
        """Verify that data swap request is rejected

        This tests the case where the DUT doesn't advertise support
        for data swaps. A data request is sent by PDTester, and then
        the control message checked to ensure the request was rejected.
        In addition, the data role and connection state are verified
        to remain unchanged.
        """
        # Get current DUT data role
        dut_data_role = self._get_data_role(self.dut_port)
        dut_connect_state = self.dut_port.get_pd_state()
        # Send swap command from PDTester and get reply
        ctrl_msg = self._send_data_swap_get_reply(self.pdtester_port)
        if ctrl_msg != self.dut_port.utils.PD_CONTROL_MSG_DICT['Reject']:
            raise error.TestFail('Data Swap Req not rejected, returned %r' %
                                 ctrl_msg)
        # Get DUT current state
        pd_state = self.dut_port.get_pd_state()
        if pd_state != dut_connect_state:
            raise error.TestFail('PD not connected! pd_state = %r' %
                                 pd_state)
        # Since reject message was received, verify data role didn't change
        curr_dr = self._get_data_role(self.dut_port)
        if curr_dr != dut_data_role:
            raise error.TestFail('Unexpected PD data role change')

    def initialize(self, host, cmdline_args, flip_cc=False, dts_mode=False,
                   init_power_mode=None):
        super(firmware_PDDataSwap, self).initialize(host, cmdline_args)
        self.setup_pdtester(flip_cc, dts_mode, min_batt_level=10)
        # Only run in normal mode
        self.switcher.setup_mode('normal')
        if init_power_mode:
            # Set the DUT to suspend or shutdown mode
            self.set_ap_off_power_mode(init_power_mode)
        self.usbpd.send_command('chan 0')

    def cleanup(self):
        self.usbpd.send_command('chan 0xffffffff')
        self.restore_ap_on_power_mode()
        super(firmware_PDDataSwap, self).cleanup()

    def run_once(self):
        """Exectue Data Role swap test.

        1. Verify that pd console is accessible
        2. Verify that DUT has a valid PD contract
        3. Determine if DUT advertises support for data swaps
        4. Test DUT initiated and received data swaps
        5. Swap power roles if supported
        6. Repeat DUT received data swap requests

        """

        # Create list of available UART consoles
        consoles = [self.usbpd, self.pdtester]
        port_partner = pd_device.PDPortPartner(consoles)

        # Identify a valid test port pair
        port_pair = port_partner.identify_pd_devices()
        if not port_pair:
            raise error.TestFail('No PD connection found!')

        for port in port_pair:
            if port.is_pdtester:
                self.pdtester_port = port
            else:
                self.dut_port = port

        dut_connect_state = self.dut_port.get_pd_state()
        logging.info('Initial DUT connect state = %s', dut_connect_state)

        # Determine if DUT supports data role swaps
        dr_swap_allowed = self.pdtester_port.is_pd_flag_set('data_swap')

        # Get current DUT data role
        dut_data_role = self._get_data_role(self.dut_port)
        logging.info('Starting DUT Data Role = %r', dut_data_role)

        # If data swaps are not allowed on the DUT, then still
        # attempt a data swap and verify that the request is
        # rejected by the DUT and that it remains connected and
        # in the same role.
        if dr_swap_allowed == False:
            logging.info('Data Swap support not advertised by DUT')
            self._test_data_swap_reject()
            logging.info('Data Swap request rejected by DUT as expected')
        else:
            # Data role swap support advertised, test this feature.
            self._execute_data_role_swap_test()

            # If DUT supports Power Role swap then attempt to change roles.
            # This way, data role swaps will be tested in both configurations.
            if self.pdtester_port.is_pd_flag_set('power_swap'):
                logging.info('\nDUT advertises Power Swap Support')
                # Attempt to swap power roles
                power_swap = self._change_dut_power_role()
                if power_swap:
                    try:
                        self._execute_data_role_swap_test()
                    finally:
                        # Swap power role, back to the original
                        self._change_dut_power_role()
                else:
                    logging.warning('Power swap not successful!')
                    logging.warning('Only tested with DUT in %s state',
                                 dut_connect_state)
            else:
                logging.info('DUT does not advertise power swap support')

            logging.info('***************** Swap Results ********************')
            total_attempts = 0
            total_failures = 0
            for direction, role in self.swap_attempt.keys():
                logging.info('%s %s swap attempts = %d, failures = %d',
                             direction, role,
                             self.swap_attempt[(direction, role)],
                             self.swap_failure[(direction, role)])
                total_attempts += self.swap_attempt[(direction, role)]
                total_failures += self.swap_failure[(direction, role)]

            # If any swap attempts were not successful, flag test as failure
            if total_failures:
                raise error.TestFail('Data Swap Fail: Attempt = %d, Failure = %d' %
                                 (total_attempts, total_failures))
