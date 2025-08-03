# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time
import os

import common
from autotest_lib.client.common_lib import error
from autotest_lib.server.cros import servo_keyboard_utils


class ServoKeyboardMapFlasher():
    """Flash the servo keyboard map on servo."""

    _ATMEGA_RESET_DELAY = 0.2
    _ATMEGA_FLASH_TIMEOUT = 120
    _USB_PRESENT_DELAY = 1

    # Command to detect LUFA Keyboard Demo by VID.
    LSUSB_CMD = 'lsusb -d %s:' % servo_keyboard_utils.ATMEL_USB_VENDOR_ID
    LSUSB_TIMEOUT = 30

    def is_image_supported(self, host):
        """Check if servo keyboard map supported on host

        @param host: CrosHost instance
        """
        if host.run('hash dfu-programmer', ignore_status=True).exit_status:
            return False
        return True

    def update(self, host):
        """Update servo keyboard map firmware on the host if required.

        The process will verify present of the keyboard firmware on the host
        and flash it if device was not detected.

        @param host: CrosHost instance
        """
        if not self.is_image_supported(host):
            raise Exception(
                    'The image is too old that does not have dfu-programmer.')

        try:
            logging.debug('Starting flashing the keyboard map.')
            host.servo.set_nocheck('init_usb_keyboard', 'on')

            if self._is_keyboard_present(host):
                logging.info('Already using the new keyboard map.')
                return

            self._flash_keyboard_map(host)
        finally:
            # Restore the default settings.
            # Select the chip on the USB mux unless using Servo V4
            if 'servo_v4' not in host.servo.get_servo_type():
                host.servo.set('usb_mux_sel4', 'on')

    def _flash_keyboard_map(self, host):
        """FLash servo keyboard firmware on the host."""
        servo = host.servo
        # Boot AVR into DFU mode by enabling the HardWareBoot mode
        # strapping and reset.
        servo.set_get_all([
                'at_hwb:on', 'atmega_rst:on',
                'sleep:%f' % self._ATMEGA_RESET_DELAY, 'atmega_rst:off',
                'sleep:%f' % self._ATMEGA_RESET_DELAY, 'at_hwb:off'
        ])

        time.sleep(self._USB_PRESENT_DELAY)
        result = host.run(self.LSUSB_CMD,
                          timeout=self.LSUSB_TIMEOUT).stdout.strip()
        if not 'Atmel Corp. atmega32u4 DFU bootloader' in result:
            raise Exception('Not an expected chip: %s', result)

        # Update the keyboard map.
        bindir = os.path.dirname(os.path.realpath(__file__))
        local_path = os.path.join(bindir, 'data', 'keyboard.hex')
        host.send_file(local_path, '/tmp')
        logging.info('Updating the keyboard map...')
        host.run('dfu-programmer atmega32u4 erase --force',
                 timeout=self._ATMEGA_FLASH_TIMEOUT)
        host.run('dfu-programmer atmega32u4 flash /tmp/keyboard.hex',
                 timeout=self._ATMEGA_FLASH_TIMEOUT)

        # Reset the chip.
        servo.set_get_all([
                'atmega_rst:on',
                'sleep:%f' % self._ATMEGA_RESET_DELAY, 'atmega_rst:off'
        ])
        if self._is_keyboard_present(host):
            logging.info('Update successfully!')
        else:
            raise Exception('Update failed!')

    def _is_keyboard_present(self, host):
        """Verify if servo keyboard is present on the host.

        The keyboard will be detected as USB device on the host with name:
                'Atmel Corp. LUFA Keyboard Demo Application'
        """
        time.sleep(self._USB_PRESENT_DELAY)
        result = host.run(self.LSUSB_CMD,
                          timeout=self.LSUSB_TIMEOUT).stdout.strip()
        logging.debug('got the result: %s', result)
        if ('LUFA Keyboard Demo' in result
                    and servo_keyboard_utils.is_servo_usb_wake_capable(host)):
            return True
        return False
