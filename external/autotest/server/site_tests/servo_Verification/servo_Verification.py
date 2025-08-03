# Lint as: python2, python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Wrapper test to run verification on a servo_host/dut pair."""

import ast
import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import test
from autotest_lib.server.cros.dynamic_suite import suite

class servo_Verification(test.test):
    """A wrapper test to run the suite |servo_lab| against a dut/servo pair."""
    version = 1

    DEFAULT_SUITE = "servo_lab"

    def get_test_args_from_control(self, control_data):
        """Helper to extract the control file information.

        We leverage control files and suite matching to not have to duplicate
        the work of writing the test arguments out. However, we cannot just
        execute the control-file itself, but rather need to extract the args,
        and then runsubtest ourselves.

        Please make sure that the control files being run in this suite are
        compatible with the limitations indicated below, otherwise, modify
        the test, or add a new control file.

        A few things to note:
        - tests will always be run with disable_sysinfo
        - args that are not literals e.g. local=local and local is defined
          somewhere else in the control file will be set to None
        - 'args' and 'args_dict' will be passed along as '' and {} and available
          as such e.g. if an arg says 'cmdline_args=args'

        @param control_data: ControlData of a parsed control file

        @returns: tuple(test, args): where test is the main test name
                                     args is a kwargs dict to pass to runsubtest
        """
        # Skipped args that we do not evaluate
        skipped_args = ['args', 'args_dict', 'disable_sysinfo', 'host']
        args = ''
        args_dict = {}
        # The result that we will populate.
        test_args = {'args': args,
                     'args_dict': args_dict,
                     'disable_sysinfo': True}
        cname = control_data.name
        control_file = control_data.text
        anchor = 'job.run_test'
        if anchor not in control_file:
            raise error.TestNAError('Control file for test %s does not define '
                                    '%s.' % (cname, anchor))
        # Find the substring only
        run_test_str = control_file[control_file.index(anchor) + len(anchor):]
        # Find the balanced parentheses
        paran = 1
        # This assumes that the string is job.run_test(...) so the first ( is
        # at index 0.
        for index in range(1, len(run_test_str)):
            if run_test_str[index] == '(': paran += 1
            if run_test_str[index] == ')': paran -= 1
            if paran == 0: break
        else:
            # Failed to find balanced parentheses.
            raise error.TestNAError('Unable to parse %s for %s.' %
                                    (anchor, cname))
        # Extract only the args
        run_test_str = run_test_str[1:index]
        raw_args = run_test_str.split(',')
        try:
            base_test_name = ast.literal_eval(raw_args[0])
        except (ValueError, SyntaxError) as e:
            logging.debug('invalid run_test_str: %s. %s', run_test_str, str(e))
            raise error.TestNAError('Unable to parse test name from %s for %s.'
                                    % (anchor, cname))
        # Parse an evaluate the remaining args
        for arg in raw_args[1:]:
            # Issues here are also caught by ValueError below.
            aname, aval = arg.split('=')
            aname = aname.strip()
            aval = aval.strip()
            if aname not in skipped_args:
                # eval() is used here as some test might make references
                # to 'args' and 'args_dict'. Hence the BaseException below
                # as any error might occur here.
                try:
                    test_args[aname] = eval(aval)
                except BaseException as e:
                    logging.debug(str(e))
                    logging.info('Unable to parse value %r for arg %r. Setting '
                                 'to None.', aval, aname)
                    test_args[aname] = None

        logging.info('Will run the test %s as %s with args: %s', cname,
                     base_test_name, test_args)
        return base_test_name, test_args

    def initialize(self, host, local=False):
        """Prepare all test-names and args to be run.

        @param host: cros host to run the test against. Needs to have a servo
        @param: on False, the latest repair image is downloaded onto the usb
                 stick. Set to true to skip (reuse image on stick)
        """
        fs_getter = suite.create_fs_getter(self.autodir)
        # Find the test suite in autotest file system.
        predicate = suite.name_in_tag_predicate(self.DEFAULT_SUITE)
        tests = suite.find_and_parse_tests(fs_getter, predicate)
        if not tests:
            raise error.TestNAError('%r suite has no tests under it.' %
                                    self.DEFAULT_SUITE)
        self._tests = []
        for data in tests:
            try:
                self._tests.append(self.get_test_args_from_control(data))
            except error.TestNAError as e:
                logging.info('Unable to parse %s. Skipping. %s', data.name,
                             str(e))
        if not self._tests:
            raise error.TestFail('No test parsed successfully.')
        self._tests.sort(key=lambda t: t[0])
        if not local:
            # Pre-download the usb image onto the stick so that tests that
            # need it can use it.
            _, image_url = host.stage_image_for_servo()
            host.servo.image_to_servo_usb(image_url)
            # `image_to_servo_usb` turned DUT off while download image to usb
            # drive, so we need to turn DUT back on as some tests assume DUT
            # is sshable at begin.
            host.servo.get_power_state_controller().power_on()
            if not host.wait_up(timeout=host.BOOT_TIMEOUT):
                logging.warning(
                        '%s failed to boot in %s seconds, some tests'
                        ' may fail due to not able to ssh to the DUT',
                        host.hostname, host.BOOT_TIMEOUT)

    def run_once(self, host):
        """Run through the test sequence.

        @param host: cros host to run the test against. Needs to have a servo

        @raises: error.TestFail if any test in the sequence fails
        """
        success = True
        for idx, test in enumerate(self._tests):
            tname, targs = test
            # Some tests might run multiple times e.g.
            # platform_ServoPowerStateController with usb and without usb.
            # The subdir task ensures that there won't ever be a naming
            # collision.
            subdir_tag = '%02d' % idx
            success &= self.runsubtest(tname, subdir_tag=subdir_tag,
                                       host=host, **targs)
        if not success:
            raise error.TestFail('At least one verification test failed. '
                                 'Check the logs.')
