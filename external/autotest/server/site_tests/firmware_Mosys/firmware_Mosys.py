# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import re

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_Mosys(FirmwareTest):
    """
    Mosys commands test for Firmware values.

    Execute
    * mosys -k ec info
    * mosys platform name
    * mosys -k pd info

    """
    version = 1


    def initialize(self, host, cmdline_args, dev_mode=False):
        # Parse arguments from command line
        dict_args = utils.args_to_dict(cmdline_args)
        super(firmware_Mosys, self).initialize(host, cmdline_args)
        self.switcher.setup_mode('dev' if dev_mode else 'normal',
                                 allow_gbb_force=True)
        # a list contain failed execution.
        self.failed_command = []
        # Get a list of available mosys commands.
        lines = self.run_cmd('mosys help')
        self.command_list = []
        cmdlist_start = False
        for line in lines:
            if cmdlist_start:
                cmdlst = re.split('\s+', line)
                if len(cmdlst) > 2:
                    self.command_list.append(cmdlst[1])
            elif 'Commands:' in line:
                cmdlist_start = True
        logging.info('Available commands: %s', ' '.join(self.command_list))

    def check_for_errors(self, output, command):
        """
        Check for known errors.
        1.  Bad system call (core dumped)
            We see this a lot now that mosys is in a minijail.  Even if we see
            this error, mosys will return success, so we need to check stderr.
        """
        for line in output:
            if "Bad system call" in line:
                logging.info("ERROR: Bad system call detected when calling mosys!")
                self._tag_failure(command)
                return 1
        return 0

    def run_cmd(self, command):
        """
        Log and execute command and return the output.

        @param command: Command to execution device.
        @returns the output of command.

        """
        logging.info('Execute %s', command)
        output = self.faft_client.system.run_shell_command_get_output(
                command, True)
        logging.info('Output %s', output)
        return output

    def check_ec_version(self, command, exp_ec_version):
        """
        Compare output of 'ectool version' for the current firmware
        copy to exp_ec_version.

        @param command: command string
        @param exp_ec_version: The expected EC version string.

        """
        lines = self.run_cmd('ectool version')
        fwcopy_pattern = re.compile('Firmware copy: (.*)$')
        ver_pattern = re.compile('(R[OW]) version:    (.*)$')
        version = {}
        for line in lines:
            ver_matched = ver_pattern.match(line)
            if ver_matched:
                version[ver_matched.group(1)] = ver_matched.group(2)
            fwcopy_matched = fwcopy_pattern.match(line)
            if fwcopy_matched:
                fwcopy = fwcopy_matched.group(1)
        if fwcopy in version:
            actual_version = version[fwcopy]
            logging.info('Expected ec version %s actual_version %s',
                         exp_ec_version, actual_version)
            if exp_ec_version != actual_version:
                self._tag_failure(command)
        else:
            self._tag_failure(command)
            logging.error('Failed to locate version from ectool')

    def check_pd_version(self, command, exp_pd_version):
        """
        Compare output of 'ectool --dev 1 version' for the current PD firmware
        copy to exp_pd_version.

        @param command: command string
        @param exp_pd_version: The expected PD version string.

        """
        lines = self.run_cmd('ectool --dev 1 version')
        fwcopy_pattern = re.compile('Firmware copy: (.*)$')
        ver_pattern = re.compile('(R[OW]) version:    (.*)$')
        version = {}
        for line in lines:
            ver_matched = ver_pattern.match(line)
            if ver_matched:
                version[ver_matched.group(1)] = ver_matched.group(2)
            fwcopy_matched = fwcopy_pattern.match(line)
            if fwcopy_matched:
                fwcopy = fwcopy_matched.group(1)
        if fwcopy in version:
            actual_version = version[fwcopy]
            logging.info('Expected pd version %s actual_version %s',
                         exp_pd_version, actual_version)
            if exp_pd_version != actual_version:
                self._tag_failure(command)
        else:
            self._tag_failure(command)
            logging.error('Failed to locate version from ectool')

    def _tag_failure(self, cmd):
        self.failed_command.append(cmd)
        logging.error('Execute %s failed', cmd)

    def run_once(self, dev_mode=False):
        """Runs a single iteration of the test."""
        # mosys -k ec info
        command = 'mosys -k ec info'
        if self.faft_config.chrome_ec:
            output = self.run_cmd(command)
            self.check_for_errors(output, command)
            p = re.compile(
                    'vendor="[A-Z]?[a-z]+" name="[ -~]+" fw_version="(.*)"')
            v = p.match(output[0])
            if v:
                version = v.group(1)
                self.check_ec_version(command, version)
            else:
                self._tag_failure(command)
        else:
            logging.info('Skip "%s", command not available.', command)

        # mosys platform name
        command = 'mosys platform name'
        output = self.run_cmd(command)
        self.check_for_errors(output, command)

        # mosys -k pd info
        command = 'mosys -k pd info'
        if self.faft_config.chrome_usbpd and 'pd' in self.command_list:
            output = self.run_cmd(command)
            self.check_for_errors(output, command)
            p = re.compile('vendor="[a-z]+" name="[ -~]+" fw_version="(.*)"')
            v = p.match(output[0])
            if v:
                version = v.group(1)
                self.check_pd_version(command, version)
            else:
                self._tag_failure(command)
        else:
            logging.info('Skip "%s", command not available.', command)

        # mosys -k memory spd print all (check no error output)
        command = 'mosys -k memory spd print all'
        output = self.run_cmd(command)
        self.check_for_errors(output, command)
        p = re.compile('^dimm=".*$')
        # Each line should start with "dimm=".
        for i in output:
            if not p.match(i):
                logging.error('output does not start with dimm=%s', i)
                self._tag_failure(command)
                break

        # Add any other mosys commands or tests before this section.
        # empty failed_command indicate all passed.
        if self.failed_command:
            raise error.TestFail(
                    '%d commands failed, detail above.  '
                    'Failed commands are "%s"' %
                    (len(self.failed_command), ','.join(self.failed_command)))
