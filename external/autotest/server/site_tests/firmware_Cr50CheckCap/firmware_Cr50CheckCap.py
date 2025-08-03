# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import pprint

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.cr50_test import Cr50Test


class firmware_Cr50CheckCap(Cr50Test):
    """Verify cr50 capabilities"""
    version = 1

    # The default requirements for these capabilities change between prod and
    # prepvt images. Make sure they match the expected values.
    SPECIAL_CAPS = ['OpenNoDevMode', 'OpenFromUSB']
    EXPECTED_REQ_PREPVT = 'Always'
    EXPECTED_REQ_PROD = 'IfOpened'

    def check_cap_command(self, enable_factory, reset_caps):
        """Verify the cr50 cap response after running the given command"""
        if enable_factory:
            self.cr50.ccd_reset_factory()
        else:
            # Testlab mode is enabled, so it's ok to reset ccd without enabling
            # capabilities necessary for ccd.
            self.cr50.ccd_reset(servo_en=False)
        caps = self.cr50.get_cap_dict()
        logging.info(caps)
        in_factory_mode, is_reset = self.cr50.get_cap_overview(caps)
        if reset_caps and not is_reset:
            raise error.TestFail('did not reset capabilities')
        if enable_factory and not in_factory_mode:
            raise error.TestFail('did not enable factory mode')


    def check_cap_req(self, cap_dict, cap, expected_req):
        """Check the current cap requirement against the expected requirement"""
        req = cap_dict[cap]
        if req != expected_req:
            raise error.TestFail('%r should be %r not %r' % (cap, expected_req,
                                                             req))


    def ccd_ec_uart_works(self):
        """Returns True if the CCD ec uart works."""
        logging.info('checking ec console')
        try:
            self.servo.get('ec_board', self._ec_prefix)
            logging.info('ccd ec console is responsive')
            return True
        except:
            logging.info('ccd ec console is unresponsive')
            return False


    def check_cap_accessiblity(self, ccd_level, cap_setting, expect_accessible):
        """Check setting cap requirements restricts the capabilities correctly.

        Set each ccd capability to cap_setting. Set the ccd state to ccd_level.
        Then verify the capability accessiblity matches expect_accessible.

        Args:
            ccd_level: a ccd state level: 'lock', 'unlock', or 'open'.
            cap_setting: A ccd cap setting: 'IfOpened', 'Always', or
                         'UnlessLocked'.
            expect_accessible: True if capabilities should be accessible

        Raises:
            TestFail if expect_accessible doesn't match the accessibility state.
        """

        if (ccd_level == 'unlock' or cap_setting == 'UnlessLocked') \
            and not self.cr50.unlock_is_supported():
            return

        # Run testlab open, so we won't have to do physical presence stuff.
        self.cr50.send_command('ccd testlab open')

        # Set all capabilities to cap_setting
        caps = self.cr50.get_cap_dict().keys()
        cap_settings = {}
        for cap in caps:
            cap_settings[cap] = cap_setting
        self.cr50.set_caps(cap_settings)

        # Set the ccd state to ccd_level
        self.cr50.set_ccd_level(ccd_level, self.CCD_PASSWORD)
        cap_dict = self.cr50.get_cap_dict()
        logging.info('Cap state with console %r req %r:\n%s', ccd_level,
                     cap_setting, pprint.pformat(cap_dict))

        # Check the accessiblity
        for cap, cap_info in cap_dict.items():
            if cap_info[self.cr50.CAP_IS_ACCESSIBLE] != expect_accessible:
                raise error.TestFail('%r is %raccessible' % (cap,
                                     'not ' if expect_accessible else ''))

        if (self.check_ec_uart and
            expect_accessible != self.ccd_ec_uart_works()):
            raise error.TestFail('EC UART is %saccessible when it should%s be' %
                                 ('not ' if expect_accessible else '',
                                  '' if expect_accessible else "n't"))


    def run_once(self, ccd_open_restricted=False):
        """Check cr50 capabilities work correctly."""
        self.fast_ccd_open(enable_testlab=True)


        # Check servo monitoring before changing the active device. There's no
        # need for servo detection if ccd is the only device.
        servo_detect_ok = (self.servo.main_device_is_ccd()
                           or self.cr50.check_servo_monitor())

        set_ccd = self.servo.enable_ccd_servo_device()
        self._ec_prefix = self.servo.get_active_device_prefix()
        # Only check EC uart if the board has a working EC and cr50 can detect
        # servo connect/disconnect.
        self.check_ec_uart = (
                set_ccd and servo_detect_ok
                and self.check_ec_capability(suppress_warning=True)
                and self.servo.active_device_is_ccd()
                and self.servo.has_control('ec_board', self._ec_prefix))

        # Make sure factory reset sets all capabilities to Always
        self.check_cap_command(True, False)

        # Make sure ccd reset sets all capabilities to Default
        self.check_cap_command(False, True)

        expected_req = (self.EXPECTED_REQ_PROD if ccd_open_restricted else
                        self.EXPECTED_REQ_PREPVT)
        cap_dict = self.cr50.get_cap_dict(info=self.cr50.CAP_REQ)
        # Make sure the special ccd capabilities match ccd_open_restricted
        for cap in self.SPECIAL_CAPS:
            self.check_cap_req(cap_dict, cap, expected_req)

        # Set the password so we can change the ccd level from the console
        self.cr50.send_command('ccd testlab open')
        # Testlab mode is enabled, so it's ok to reset ccd without enabling
        # capabilities necessary for ccd.
        self.cr50.ccd_reset(servo_en=False)
        self.set_ccd_password(self.CCD_PASSWORD)

        # Make sure ccd accessiblity behaves as expected based on the cap
        # settings and the ccd state.
        self.check_cap_accessiblity('open', 'IfOpened', True)
        self.check_cap_accessiblity('open', 'UnlessLocked', True)
        self.check_cap_accessiblity('open', 'Always', True)

        self.check_cap_accessiblity('unlock', 'IfOpened', False)
        self.check_cap_accessiblity('unlock', 'UnlessLocked', True)
        self.check_cap_accessiblity('unlock', 'Always', True)

        self.check_cap_accessiblity('lock', 'IfOpened', False)
        self.check_cap_accessiblity('lock', 'UnlessLocked', False)
        self.check_cap_accessiblity('lock', 'Always', True)
