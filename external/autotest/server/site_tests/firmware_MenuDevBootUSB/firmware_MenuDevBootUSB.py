# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.faft.firmware_test import FirmwareTest


class firmware_MenuDevBootUSB(FirmwareTest):
    """
    Servo based test for USB boot in developer mode through the UI menu.
    """
    version = 1

    def initialize(self, host, cmdline_args):
        super(firmware_MenuDevBootUSB, self).initialize(host, cmdline_args)
        if not self.menu_switcher:
            raise error.TestNAError('Test skipped for menuless UI')
        if not self.faft_config.chrome_ec:
            raise error.TestNAError('Cannot check power state without EC')
        self.switcher.setup_mode('dev')
        self.setup_usbkey(usbkey=True, host=True, used_for_recovery=False)

    def cleanup(self):
        """Clean up the test."""
        try:
            self.faft_client.system.set_dev_boot_usb(0)
            self.servo.switch_usbkey('host')
        except Exception as e:
            logging.error("Caught exception: %s", str(e))
        super(firmware_MenuDevBootUSB, self).cleanup()

    def _dev_reboot_and_unplug_usb(self):
        """Reboot from internal disk and unplug USB disk."""
        # Device must be in dev mode
        logging.info('Reboot to dev mode and unplug USB')
        self.switcher.mode_aware_reboot()
        self.servo.switch_usbkey('host')

    def run_once(self):
        """Method which actually runs the test."""
        self.check_state((self.checkers.mode_checker, 'dev'))
        self.servo.switch_usbkey('dut')
        self.faft_client.system.set_dev_boot_usb(1)
        self.faft_client.system.set_dev_default_boot('disk')

        # Now the device should be in dev screen
        logging.info('Boot from USB in developer screen')
        self.switcher.simple_reboot()
        self.menu_switcher.dev_boot_from_external()
        self.switcher.wait_for_client()
        self.check_state((self.checkers.dev_boot_usb_checker, (True, True),
                          'Device not booted from USB'))

        # Reboot from internal disk in order to unplug USB
        self._dev_reboot_and_unplug_usb()

        # For menu UI, boot from USB in external boot screen, a polling screen
        # that repeatedly checks for USB disks
        if self.faft_config.mode_switcher_type != 'tablet_detachable_switcher':
            logging.info('Boot from USB in external boot screen')
            self.switcher.simple_reboot()
            self.menu_switcher.dev_boot_from_external()
            self.switcher.wait_for_client_offline()

            # Since there is no USB plugged-in, now the device should be in
            # external boot screen
            self.servo.switch_usbkey('dut')
            self.switcher.wait_for_client()
            self.check_state((self.checkers.dev_boot_usb_checker, (True, True),
                              'Device not booted from USB properly'))
            self._dev_reboot_and_unplug_usb()
        else:
            logging.info('Skipped polling screen test for switcher type %s',
                         self.faft_config.mode_switcher_type)

        # After selecting "Boot from external disk" while no USB is plugged-in,
        # the UI should still work
        logging.info('Try to boot from USB without USB plugged-in')
        self.switcher.simple_reboot()
        self.menu_switcher.dev_boot_from_external()
        self.wait_for('keypress_delay')
        if self.faft_config.mode_switcher_type == 'tablet_detachable_switcher':
            # In legacy menu UI, the device should be still in developer boot
            # options screen
            self.menu_switcher.menu.down()  # Boot From Internal Disk
        else:
            # In menu UI, the device should have changed to external boot screen
            self.menu_switcher.menu.select('Going back to dev screen...')
            self.wait_for('keypress_delay')
            self.menu_switcher.menu.up()  # Boot from internal disk
        self.wait_for('keypress_delay')
        self.menu_switcher.menu.select(
                'Selecting "Boot from internal disk"...')
        self.switcher.wait_for_client()
        self.check_state((self.checkers.dev_boot_usb_checker, False,
                          'Device not booted from internal disk properly'))
