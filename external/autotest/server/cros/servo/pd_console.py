# Lint as: python2, python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import re
import logging
import six
from six.moves import range
import time

from autotest_lib.client.common_lib import error


class PDConsoleUtils(object):
    """Base clase for all PD console utils

    This class provides a set of APIs for expected Type C PD required actions
    in TypeC FAFT tests. The base class is specific for Type C console access.

    """
    def __init__(self, console):
        """Console can be either usbpd, ec, or pdtester UART

        This object with then be used by the class which creates
        the PDConsoleUtils class to send/receive commands to UART
        """
        # save console for UART access functions
        self.console = console

    def send_pd_command(self, cmd):
        """Send command to PD console UART

        @param cmd: pd command string
        """
        self.console.send_command(cmd)

    def send_pd_command_get_output(self, cmd, regexp, debug_on=True):
        """Send command to PD console, wait for response

        @param cmd: pd command string
        @param regexp: regular expression for desired output
        """
        # Enable PD console debug mode to show control messages
        if debug_on:
            self.enable_pd_console_debug()
        output = self.console.send_command_get_output(cmd, regexp)
        if debug_on:
            self.disable_pd_console_debug()
        return output

    def send_pd_command_get_reply_msg(self, cmd):
        """Send PD protocol msg, get PD control msg reply

        The PD console debug mode is enabled prior to sending
        a pd protocol message. This allows the
        control message reply to be extracted. The debug mode
        is disabled prior to exiting.

        @param cmd: pd command to issue to the UART console

        @returns: PD control header message
        """
        m = self.send_pd_command_get_output(cmd, ['RECV\s([\w]+)\W'])
        ctrl_msg = int(m[0][1], 16) & self.PD_CONTROL_MSG_MASK
        return ctrl_msg

    def verify_pd_console(self):
        """Verify that PD commands exist on UART console

        Send 'help' command to UART console

        @returns: True if 'pd' is found, False if not
        """

        l = self.console.send_command_get_output('help', ['(pd)\s+([\w]+)'])
        if l[0][1] == 'pd':
            return True
        else:
            return False

    def get_pd_version(self):
        """Get the version of the PD stack

        @returns: version of PD stack, one of (1, 2)
        """
        # Match a number or an error ("Wrong number of params")
        matches = self.console.send_command_get_output('pd version',
                                                       [r'\s+(\d+|Wrong.*)'])
        if matches:
            result = matches[0][1]
            if result[0].isdigit():
                return int(result)
        return 1

    def execute_pd_state_cmd(self, port):
        """Get PD state for specified channel

        pd 0/1 state command gives produces 5 fields. The full response
        line is captured and then parsed to extract each field to fill
        the dict containing port, polarity, role, pd_state, and flags.

        @param port: Type C PD port 0 or 1

        @returns: A dict with the 5 fields listed above
        @raises: TestFail if any field not found
        """
        cmd = 'pd'
        subcmd = 'state'
        pd_cmd = cmd +" " + str(port) + " " + subcmd
        time.sleep(self.CURRENT_STATE_PROBE_DELAY)
        # Two FW versions for this command, get full line.
        m = self.send_pd_command_get_output(pd_cmd, ['(Port.*) - (Role:.*)\n'],
                                            debug_on=False)

        # Extract desired values from result string
        state_result = {}
        pd_state_dict = self.PD_STATE_DICT

        for key, regexp in six.iteritems(pd_state_dict):
            value = re.search(regexp, m[0][0])
            if value:
                state_result[key] = value.group(1)
            else:
                raise error.TestFail('pd %d state: %r value not found' %
                                     (port, key))

        return state_result

    def get_pd_state(self, port):
        """Get the current PD state

        """
        raise NotImplementedError(
            'should be implemented in derived class')

    def get_pd_port(self, port):
        """Get the current PD port

        @param port: Type C PD port 0/1
        @returns: current pd state
        """
        pd_dict = self.execute_pd_state_cmd(port)
        return pd_dict['port']

    def get_pd_role(self, port):
        """Get the current PD power role (source or sink)

        @param port: Type C PD port 0/1
        @returns: current pd state
        """
        pd_dict = self.execute_pd_state_cmd(port)
        return pd_dict['role']

    def get_pd_flags(self, port):
        """Get the current PD flags

        @param port: Type C PD port 0/1
        @returns: current pd state
        """
        pd_dict = self.execute_pd_state_cmd(port)
        return pd_dict['flags']

    def get_pd_dualrole(self, port):
        """Get the current PD dualrole setting

        @param port: Type C PD port 0/1
        @returns: current PD dualrole setting, one of (on, off, snk, src)
        """

        if self.per_port_dualrole_setting is True:
            cmd = 'pd %d dualrole' % port
        elif self.per_port_dualrole_setting is False:
            cmd = 'pd dualrole'
        else:
            try:
                self.per_port_dualrole_setting = True
                return self.get_pd_dualrole(port)
            except:
                self.per_port_dualrole_setting = False
                return self.get_pd_dualrole(port)

        dualrole_values = self.DUALROLE_VALUES

        m = self.send_pd_command_get_output(
                cmd, ['dual-role toggling:\s+([\w ]+)[\r\n]'], debug_on=False)
        # Find the index according to the output of "pd dualrole" command
        dual_index = self.DUALROLE_CMD_RESULTS.index(m[0][1])
        # Map to a string which is the output of this method
        return dualrole_values[dual_index]

    def set_pd_dualrole(self, port, value):
        """Set pd dualrole

        """
        raise NotImplementedError(
            'should be implemented in derived class')

    def query_pd_connection(self):
        """Determine if PD connection is present

        Try the 'pd 0/1 state' command and see if it's in either
        expected state of a connection. Record the port number
        that has an active connection

        @returns: dict with params port, connect, and state
        """
        status = {}
        port = 0;
        status['connect'] = False
        status['port'] = port
        state = self.get_pd_state(port)
        # Check port 0 first

        if self.is_pd_connected(port):
            status['connect'] = True
            status['role'] = state
        else:
            port = 1
            status['port'] = port
            state = self.get_pd_state(port)
            logging.info('CHECK PORT 1: %s', state)
            # Check port 1
            if self.is_pd_connected(port):
                status['connect'] = True
                status['role'] = state

        return status

    def swap_power_role(self, port):
        """Attempt a power role swap

        This method attempts to execute a power role swap. A check
        is made to ensure that dualrole mode is enabled and that
        a PD contract is currently established. If both checks pass,
        then the power role swap command is issued. After a delay,
        if a PD contract is established and the current state does
        not equal the starting state, then it was successful.

        @param port: pd port number

        @returns: True if power swap is successful, False otherwise.
        """
        # Get starting state
        if self.is_pd_dual_role_enabled(port) == False:
            logging.info('Dualrole Mode not enabled!')
            return False
        if self.is_pd_connected(port) == False:
            logging.info('PD contract not established!')
            return False
        current_pr = self.get_pd_state(port)
        swap_cmd = 'pd %d swap power' % port
        self.send_pd_command(swap_cmd)
        time.sleep(self.CONNECT_TIME)
        new_pr = self.get_pd_state(port)
        logging.info('Power swap: %s -> %s', current_pr, new_pr)
        if self.is_pd_connected(port) == False:
            return False
        return bool(current_pr != new_pr)

    def disable_pd_console_debug(self):
        """Turn off PD console debug

        """
        cmd = 'pd dump 0'
        self.send_pd_command(cmd)

    def enable_pd_console_debug(self):
        """Enable PD console debug level 1

        """
        cmd = 'pd dump 2'
        self.send_pd_command(cmd)

    def is_pd_flag_set(self, port, key):
        """Test a bit in PD protocol state flags

        The flag word contains various PD protocol state information.
        This method allows for a specific flag to be tested.

        @param port: Port which has the active PD connection
        @param key: dict key to retrieve the flag bit mapping

        @returns True if the bit to be tested is set
        """
        pd_flags = self.get_pd_flags(port)
        return bool(self.PD_STATE_FLAGS_DICT[key] & int(pd_flags, 16))

    def is_pd_connected(self, port):
        """Check if a PD connection is active

        @param port: port to be used for pd console commands

        @returns True if port is in connected state
        """
        return self.is_src_connected(port) or self.is_snk_connected(port)

    def is_pd_dual_role_enabled(self, port):
        """Check if a PD device is in dualrole mode

        @param port: Type C PD port 0/1

        @returns True is dualrole mode is active, false otherwise
        """
        drp = self.get_pd_dualrole(port)
        return drp == 'on'

    def is_src_connected(self, port, state=None):
        """Checks if the port is connected as a source

        @param port: Type C PD port 0/1
        @param state: the state to check (None to get current state)

        @returns True if connected as SRC, False otherwise
        """
        if state is None:
            state = self.get_pd_state(port)
        return state in self.get_src_connect_states()

    def is_snk_connected(self, port, state=None):
        """Checks if the port is connected as a sink

        @param port: Type C PD port 0/1
        @param state: the state to check (None to get current state)

        @returns True if connected as SNK, False otherwise
        """
        if state is None:
            state = self.get_pd_state(port)
        return state in self.get_snk_connect_states()

    def is_disconnected(self, port, state=None):
        """Checks if the port is disconnected

        @param port: Type C PD port 0/1
        @param state: the state to check (None to get current state)

        @return True if disconnected
        """
        if state is None:
            state = self.get_pd_state(port)
        return state in self.get_disconnected_states()

    def get_src_connect_states(self):
        """Returns the name of the SRC state

        """
        raise NotImplementedError(
            'should be implemented in derived class')

    def get_snk_connect_states(self):
        """Returns the name of the SNK state

        """
        raise NotImplementedError(
            'should be implemented in derived class')

    def get_disconnected_states(self):
        """Returns the names of the disconnected states

        """
        return self.DISCONNECTED_STATES

    def is_snk_discovery_state(self, port):
        """Returns true if in snk discovery state, else false

        @param port: Type C PD port 0/1

        @return: True if in SNK Discovery state
        """
        raise NotImplementedError(
            'should be implemented in derived class')

class TCPMv1ConsoleUtils(PDConsoleUtils):
    """ Provides a set of methods common to USB PD TCPMv1 FAFT tests

    Each instance of this class is associated with a particular
    servo UART console. USB PD tests will typically use the console
    command 'pd' and its subcommands to control/monitor Type C PD
    connections. The servo object used for UART operations is
    passed in and stored when this object is created.

    """
    SRC_CONNECT = ('SRC_READY',)
    SNK_CONNECT = ('SNK_READY',)
    SRC_DISC = 'SRC_DISCONNECTED'
    SNK_DISC = 'SNK_DISCONNECTED'
    SNK_DISCOVERY = 'SNK_DISCOVERY'
    DRP_AUTO_TOGGLE = 'DRP_AUTO_TOGGLE'
    DISCONNECTED_STATES = (SRC_DISC, SNK_DISC, DRP_AUTO_TOGGLE)

    PD_MAX_PORTS = 2
    CONNECT_TIME = 4

    CURRENT_STATE_PROBE_DELAY = 2
    DUALROLE_QUERY_DELAY = 1
    # Dualrole input/output values of methods in this class.
    DUALROLE_VALUES = ['on', 'off', 'snk', 'src']
    # Strings passing to the console command "pd dualrole"
    DUALROLE_CMD_ARGS = ['on', 'off', 'sink', 'source']
    # Strings returned from the console command "pd dualrole"
    DUALROLE_CMD_RESULTS = ['on', 'off', 'force sink', 'force source']

    # Some old firmware uses a single dualrole setting for all ports; while
    # some new firmware uses a per port dualrole setting. This flag will be
    # initialized to True or False.
    # TODO: Remove this flag when the old setting phases out
    per_port_dualrole_setting = None

    # Dictionary for 'pd 0/1 state' parsing
    PD_STATE_DICT = {
        'port': 'Port\s+([\w]+)',
        'role': 'Role:\s+([\w]+-[\w]+)',
        'pd_state': 'State:\s+([\w()]+)',
        'flags': 'Flags:\s+([\w]+)',
        'polarity': '(CC\d)'
    }

    # Regex to match PD state name; work for both old and new formats
    RE_PD_STATE = r"(\d+)?\(?([\w_]+)?\)?"
    # Copied from ec repo: common/usb_pd_protocol.c
    PD_STATE_NAMES = [
        "DISABLED",                   # index: 0
        "SUSPENDED",
        "SNK_DISCONNECTED",
        "SNK_DISCONNECTED_DEBOUNCE",
        "SNK_HARD_RESET_RECOVER",
        "SNK_DISCOVERY",              # index: 5
        "SNK_REQUESTED",
        "SNK_TRANSITION",
        "SNK_READY",
        "SNK_SWAP_INIT",
        "SNK_SWAP_SNK_DISABLE",       # index: 10
        "SNK_SWAP_SRC_DISABLE",
        "SNK_SWAP_STANDBY",
        "SNK_SWAP_COMPLETE",
        "SRC_DISCONNECTED",
        "SRC_DISCONNECTED_DEBOUNCE",  # index: 15
        "SRC_HARD_RESET_RECOVER",
        "SRC_STARTUP",
        "SRC_DISCOVERY",
        "SRC_NEGOCIATE",
        "SRC_ACCEPTED",               # index: 20
        "SRC_POWERED",
        "SRC_TRANSITION",
        "SRC_READY",
        "SRC_GET_SNK_CAP",
        "DR_SWAP",                    # index: 25
        "SRC_SWAP_INIT",
        "SRC_SWAP_SNK_DISABLE",
        "SRC_SWAP_SRC_DISABLE",
        "SRC_SWAP_STANDBY",
        "VCONN_SWAP_SEND",            # index: 30
        "VCONN_SWAP_INIT",
        "VCONN_SWAP_READY",
        "SOFT_RESET",
        "HARD_RESET_SEND",
        "HARD_RESET_EXECUTE",         # index: 35
        "BIST_RX",
        "BIST_TX",
        "DRP_AUTO_TOGGLE",
    ]

    # Dictionary for PD control message types
    PD_CONTROL_MSG_MASK = 0x1f
    PD_CONTROL_MSG_DICT = {
        'GoodCRC': 1,
        'GotoMin': 2,
        'Accept': 3,
        'Reject': 4,
        'Ping': 5,
        'PS_RDY': 6,
        'Get_Source_Cap': 7,
        'Get_Sink_Cap': 8,
        'DR_Swap': 9,
        'PR_Swap': 10,
        'VCONN_Swap': 11,
        'Wait': 12,
        'Soft_Reset': 13
    }

    # Dictionary for PD firmware state flags
    PD_STATE_FLAGS_DICT = {
        'power_swap': 1 << 1,
        'data_swap': 1 << 2,
        'data_swap_active': 1 << 3,
        'vconn_on': 1 << 12
    }

    def _normalize_pd_state(self, state):
        """Normalize the PD state name which handles both old and new formats.

        The old format is like: "SNK_READY"
        The new format is like: "8()" if debug_level == 0, or
                                "8(SNK_READY)" if debug_level > 0

        This method will convert the new format to the old one.

        @param state: The raw PD state text

        @returns: The normalized PD state name
        @raises: TestFail if unexpected PD state format
        """
        m = re.match(self.RE_PD_STATE, state)
        if m and any(m.groups()):
            state_index, state_name = m.groups()
            if state_index is None:
                # The old format: return the name
                return state_name
            # The new format: map the index to a name
            mapped_name = self.PD_STATE_NAMES[int(state_index)]
            if state_name is not None:
                assert mapped_name == state_name
            return mapped_name
        else:
            raise error.TestFail('Unexpected PD state format: %s' % state)

    def get_pd_state(self, port):
        """Get the current PD state

        @param port: Type C PD port 0/1
        @returns: current pd state
        """

        pd_dict = self.execute_pd_state_cmd(port)
        return self._normalize_pd_state(pd_dict['pd_state'])

    def set_pd_dualrole(self, port, value):
        """Set pd dualrole

        It can be set to either:
        1. on
        2. off
        3. snk (force sink mode)
        4. src (force source mode)
        After setting, the current value is read to confirm that it
        was set properly.

        @param port: Type C PD port 0/1
        @param value: One of the 4 options listed
        """
        dualrole_values = self.DUALROLE_VALUES
        # If the dualrole setting is not initialized, call the get method to
        # initialize it.
        if self.per_port_dualrole_setting is None:
            self.get_pd_dualrole(port)

        # Get string required for console command
        dual_index = dualrole_values.index(value)
        # Create console command
        if self.per_port_dualrole_setting is True:
            cmd = 'pd %d dualrole %s' % (port, self.DUALROLE_CMD_ARGS[dual_index])
        elif self.per_port_dualrole_setting is False:
            cmd = 'pd dualrole %s' % (self.DUALROLE_CMD_ARGS[dual_index])
        else:
            raise error.TestFail("dualrole error")

        self.console.send_command(cmd)
        time.sleep(self.DUALROLE_QUERY_DELAY)
        # Get current setting to verify that command was successful
        dual = self.get_pd_dualrole(port)
        # If it doesn't match, then raise error
        if dual != value:
            raise error.TestFail("dualrole error: " + value + " != " + dual)

    def get_src_connect_states(self):
        """Returns the name of the SRC state

        """
        return self.SRC_CONNECT

    def get_snk_connect_states(self):
        """Returns the name of the SRC state

        """
        return self.SNK_CONNECT

    def is_snk_discovery_state(self, port):
        """Returns true if in snk discovery state, else false

        @param port: Type C PD port 0/1

        @return: True if in SNK Discovery state
        """
        state = self.get_pd_state(port)
        return state == self.SNK_DISCOVERY

class TCPMv2ConsoleUtils(PDConsoleUtils):
    """ Provides a set of methods common to USB PD TCPMv1 FAFT tests

    Each instance of this class is associated with a particular
    servo UART console. USB PD tests will typically use the console
    command 'pd' and its subcommands to control/monitor Type C PD
    connections. The servo object used for UART operations is
    passed in and stored when this object is created.

    """
    SRC_CONNECT = ('Attached.SRC', 'UnorientedDebugAccessory.SRC')
    SNK_CONNECT = ('Attached.SNK', 'DebugAccessory.SNK')
    SRC_DISC = 'Unattached.SRC'
    SNK_DISC = 'Unattached.SNK'
    DRP_AUTO_TOGGLE = 'DRPAutoToggle'
    LOW_POWER_MODE = 'LowPowerMode'
    SNK_DISCOVERY = 'PE_SNK_DISCOVERY'
    DISCONNECTED_STATES = (SRC_DISC, SNK_DISC, DRP_AUTO_TOGGLE, LOW_POWER_MODE)

    PD_MAX_PORTS = 2
    CONNECT_TIME = 4

    CURRENT_STATE_PROBE_DELAY = 2
    DUALROLE_QUERY_DELAY = 1
    # Dualrole input/output values of methods in this class.
    DUALROLE_VALUES = ['on', 'off', 'sink', 'source']
    # Strings passing to the console command "pd dualrole"
    DUALROLE_CMD_ARGS = ['on', 'off', 'sink', 'source']
    # Strings returned from the console command "pd dualrole"
    DUALROLE_CMD_RESULTS = ['on', 'off', 'force sink', 'force source']

    # Some old firmware uses a single dualrole setting for all ports; while
    # some new firmware uses a per port dualrole setting. This flag will be
    # initialized to True or False.
    # TODO: Remove this flag when the old setting phases out
    per_port_dualrole_setting = None

    # Dictionary for 'pd 0/1 state' parsing
    PD_STATE_DICT = {
        'port': 'Port\s+([\w]+)',
        'role': 'Role:\s+([\w]+-[\w]+)',
        'pd_state': 'TC State:\s+([\w().]+)',
        'flags': 'Flags:\s+([\w]+)',
        'pe_state': 'PE State:\s+(\w*)',
        'polarity': '(CC\d)'
    }

    # Regex to match PD state name; work for both old and new formats
    RE_PD_STATE = r"(\d+)?\(?([\w_]+)?\)?"

    # Dictionary for PD control message types
    PD_CONTROL_MSG_MASK = 0x1f
    PD_CONTROL_MSG_DICT = {
        'GoodCRC': 1,
        'GotoMin': 2,
        'Accept': 3,
        'Reject': 4,
        'Ping': 5,
        'PS_RDY': 6,
        'Get_Source_Cap': 7,
        'Get_Sink_Cap': 8,
        'DR_Swap': 9,
        'PR_Swap': 10,
        'VCONN_Swap': 11,
        'Wait': 12,
        'Soft_Reset': 13
    }

    # Dictionary for PD firmware state flags
    PD_STATE_FLAGS_DICT = {
        'power_swap': 1 << 1,
        'data_swap': 1 << 2,
        'data_swap_active': 1 << 3,
        'vconn_on': 1 << 12
    }

    def get_pe_state(self, port):
        """Get the current Policy Engine state

        @param port: Type C PD port 0/1
        @returns: current pe state
        """

        pd_dict = self.execute_pd_state_cmd(port)
        return pd_dict['pe_state']

    def get_pd_state(self, port):
        """Get the current PD state

        @param port: Type C PD port 0/1
        @returns: current pd state
        """

        pd_dict = self.execute_pd_state_cmd(port)
        return pd_dict['pd_state']

    def set_pd_dualrole(self, port, value):
        """Set pd dualrole

        It can be set to either:
        1. on
        2. off
        3. snk (force sink mode)
        4. src (force source mode)
        After setting, the current value is read to confirm that it
        was set properly.

        @param port: Type C PD port 0/1
        @param value: One of the 4 options listed
        """
        dualrole_values = self.DUALROLE_VALUES

        if value == 'src':
            value = 'source'
        elif value == 'snk':
            value = 'sink'

        # Get string required for console command
        dual_index = dualrole_values.index(value)
        # Create console command
        cmd = 'pd %d dualrole %s' % (port, self.DUALROLE_CMD_ARGS[dual_index])
        self.console.send_command(cmd)
        time.sleep(self.DUALROLE_QUERY_DELAY)
        # Get current setting to verify that command was successful
        dual = self.get_pd_dualrole(port)
        # If it doesn't match, then raise error
        if dual != value:
            raise error.TestFail("dualrole error: " + value + " != " + dual)

    def get_src_connect_states(self):
        """Returns the name of the SRC states

        @returns: List of connected source state names
        """
        return self.SRC_CONNECT

    def get_snk_connect_states(self):
        """Returns the name of the SRC states

        @returns: List of connected sink state names
        """
        return self.SNK_CONNECT

    def is_snk_discovery_state(self, port):
        """Returns true if in snk discovery state, else false

        @param port: Type C PD port 0/1

        @return: True if in SNK Discovery state
        """
        state = self.get_pe_state(port)
        return state == self.SNK_DISCOVERY

class PDConnectionUtils(PDConsoleUtils):
    """Provides a set of methods common to USB PD FAFT tests

    This class is used for PD utility methods that require access
    to both PDTester and DUT PD consoles.

    """

    def __init__(self, dut_console, pdtester_console):
        """
        @param dut_console: PD console object for DUT
        @param pdtester_console: PD console object for PDTester
        """
        # save console for DUT PD UART access functions
        self.dut_console = dut_console
        # save console for PDTester UART access functions
        self.pdtester_console = pdtester_console

    def _verify_pdtester_connection(self, port):
        """Verify DUT to PDTester PD connection

        This method checks for a PDTester PD connection for the
        given port by first verifying if a PD connection is present.
        If found, then it uses a PDTester feature to force a PD disconnect.
        If the port is no longer in the connected state, and following
        a delay, is found to be back in the connected state, then
        a DUT pd to PDTester connection is verified.

        @param port: DUT pd port to test

        @returns True if DUT to PDTester pd connection is verified
        """
        DISCONNECT_CHECK_TIME = 2
        DISCONNECT_TIME_SEC = 10
        # pdtester console command to force PD disconnect
        disc_cmd = 'fakedisconnect 100 %d' % (DISCONNECT_TIME_SEC * 1000)
        # Only check for PDTester if DUT has active PD connection
        if self.dut_console.is_pd_connected(port):
            # Attempt to force PD disconnection
            self.pdtester_console.send_pd_command(disc_cmd)
            time.sleep(DISCONNECT_CHECK_TIME)
            # Verify that DUT PD port is no longer connected
            if self.dut_console.is_pd_connected(port) == False:
                # Wait for disconnect timer and give time to reconnect
                time.sleep(self.dut_console.CONNECT_TIME + DISCONNECT_TIME_SEC)
                if self.dut_console.is_pd_connected(port):
                    logging.info('PDTester connection verified on port %d',
                                 port)
                    return True
            else:
                # Could have disconnected other port, allow it to reconnect
                # before exiting.
                time.sleep(self.dut_console.CONNECT_TIME + DISCONNECT_TIME_SEC)
        return False

    def find_dut_to_pdtester_connection(self):
        """Find the PD port which is connected to PDTester

        @returns DUT pd port number if found, None otherwise
        """
        for port in range(self.dut_console.PD_MAX_PORTS):
            # Check for DUT to PDTester connection on port
            if self._verify_pdtester_connection(port):
                # PDTester PD connection found so exit
                return port
        return None

def create_pd_console_utils(console):
    """Factory that detects the proper PDConsole Utils to use for DUT

    @param console: DUT PD console

    @returns: An instance of TCPMv1ConsoleUtils or TCPMv2ConsoleUtils
    """
    pd_console_utils = {
        1: TCPMv1ConsoleUtils,
        2: TCPMv2ConsoleUtils,
    }

    version = PDConsoleUtils(console).get_pd_version()
    logging.debug('%s is TCPM v%s', console, version)
    cls = pd_console_utils[version]
    return cls(console)
