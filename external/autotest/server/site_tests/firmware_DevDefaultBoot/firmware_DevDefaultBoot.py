# Copyright (c) 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_DevDefaultBoot(FirmwareTest):
    """
    This test requires a USB disk plugged in, containing a ChromeOS test image.
    On runtime, this test first switches DUT to developer mode, and modifies
    the dev_default_boot crossystem value.
    After waiting at the dev warning, or pressing enter in the detachable UI
    dev options, the device should boot whatever's selected by default.
    """
    version = 1
    NEEDS_SERVO_USB = True

    def initialize(self, host, cmdline_args, ec_wp=None):
        """Initialize the test"""
        super(firmware_DevDefaultBoot, self).initialize(host, cmdline_args,
                                                        ec_wp=ec_wp)
        self.switcher.setup_mode('dev')
        # Use the USB key for Ctrl-U dev boot, not recovery.
        self.setup_usbkey(usbkey=True, host=False, used_for_recovery=False)

        self.orig_boot_usb = self.faft_client.system.get_dev_boot_usb()
        logging.info('Original dev_boot_usb: %s', self.orig_boot_usb)

    def cleanup(self):
        """Cleanup the test"""
        if hasattr(self, 'orig_boot_usb'):
            try:
                self.ensure_dev_internal_boot(self.orig_boot_usb)
            except Exception as e:
                logging.error("Error while restoring dev_boot_usb: %s", str(e))
        super(firmware_DevDefaultBoot, self).cleanup()

    def try_timeout_default_internal(self):
        """With dev_default_boot=disk, wait for default: should boot internal"""
        logging.info('%s', self.try_timeout_default_internal.__doc__.strip())

        self.faft_client.system.set_dev_default_boot('disk')
        self.switcher.simple_reboot()
        # No ctrl-d or tablet bypass, so it uses the default
        self.switcher.wait_for_client()
        self.check_state((
                self.checkers.dev_boot_usb_checker, False,
                "dev_default_boot=disk, but didn't boot internal after timer"))

    def try_menu_default_internal(self):
        """With dev_default_boot=disk, use menu default: should boot internal"""
        logging.info('%s', self.try_menu_default_internal.__doc__.strip())

        self.faft_client.system.set_dev_default_boot('disk')
        self.switcher.simple_reboot()
        self.switcher.bypass_dev_default_boot()
        self.check_state((
                self.checkers.dev_boot_usb_checker, False,
                "dev_default_boot=disk, but didn't boot"
                " internal disk as default menu option"
        ))

    def try_timeout_default_usb(self):
        """With dev_default_boot=usb, wait for default: should boot USB"""
        logging.info('%s', self.try_timeout_default_usb.__doc__.strip())

        self.faft_client.system.set_dev_default_boot('usb')
        self.switcher.simple_reboot()
        # No ctrl-d or tablet bypass, so it uses the default
        self.switcher.wait_for_client()
        self.check_state((
                self.checkers.dev_boot_usb_checker, (True, True),
                "dev_default_boot=usb, but didn't boot USB after timer"))

    def try_menu_default_usb(self):
        """With dev_default_boot=usb, use menu default: should boot USB"""
        logging.info('%s', self.try_menu_default_usb.__doc__.strip())

        self.faft_client.system.set_dev_default_boot('usb')
        self.switcher.simple_reboot()
        self.switcher.bypass_dev_default_boot()
        self.check_state((
                self.checkers.dev_boot_usb_checker, (True, True),
                "dev_default_boot=usb, but didn't boot"
                " USB as default menu option"))

    def run_once(self):
        """Main test logic"""
        if (self.faft_config.has_keyboard and
                not self.check_ec_capability(['keyboard'])):
            raise error.TestNAError("TEST IT MANUALLY! This test can't be "
                                    "automated on non-Chrome-EC devices.")

        self.faft_client.system.set_dev_boot_usb(1)
        has_menu = self.faft_config.mode_switcher_type in (
                'menu_switcher',
                'tablet_detachable_switcher',
        )

        self.try_timeout_default_internal()
        if has_menu:
            self.try_menu_default_internal()

        self.try_timeout_default_usb()
        if has_menu:
            self.try_menu_default_usb()

        # try again to make sure it can be changed back
        self.try_timeout_default_internal()
