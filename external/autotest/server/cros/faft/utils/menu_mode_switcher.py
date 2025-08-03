# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import abc
import logging
import six

from autotest_lib.client.common_lib import error


@six.add_metaclass(abc.ABCMeta)
class _BaseMenuModeSwitcher:
    """
    Base class for mode switch with menu navigator.
    """

    def __init__(self, faft_framework, menu_navigator):
        self.test = faft_framework
        self.faft_config = self.test.faft_config
        self.servo = self.test.servo
        self.menu = menu_navigator
        self.checkers = faft_framework.checkers

        self.minidiag_enabled = self.faft_config.minidiag_enabled
        self.minios_enabled = self.faft_config.minios_enabled

    @abc.abstractmethod
    def trigger_rec_to_dev(self):
        """
        Trigger to-dev transition.
        """
        raise NotImplementedError

    @abc.abstractmethod
    def dev_boot_from_internal(self):
        """
        Boot from internal disk in developer mode.
        """
        raise NotImplementedError

    @abc.abstractmethod
    def dev_boot_from_external(self):
        """Boot from external disk in developer mode."""
        raise NotImplementedError

    @abc.abstractmethod
    def trigger_dev_to_normal(self):
        """
        Trigger dev-to-norm transition.
        """
        raise NotImplementedError

    @abc.abstractmethod
    def power_off(self):
        """
        Power off the device.

        This method should work in both developer and recovery screens.
        """
        raise NotImplementedError


class _TabletDetachableMenuModeSwitcher(_BaseMenuModeSwitcher):
    """
    Mode switcher with menu navigator for legacy menu UI.

    The "legacy menu UI" is an old menu-based UI, which has been replaced
    by the new one, called "menu UI".
    """

    def trigger_rec_to_dev(self):
        """
        Trigger to-dev transition.
        """
        self.test.switcher.trigger_rec_to_dev()

    def dev_boot_from_internal(self):
        """
        Boot from internal disk in developer mode.

        Menu items in developer warning screen:
            0. Developer Options
            1. Show Debug Info
            2. Enable OS Verification
           *3. Power Off
            4. Language

        Menu items in developer boot options screen:
            0. Boot From Network
            1. Boot Legacy BIOS
            2. Boot From USB or SD Card
           *3. Boot From Internal Disk
            4. Cancel
            5. Power Off

        (*) is the default selection.
        """
        self.test.wait_for('firmware_screen')
        self.menu.move_to(3, 0)
        self.menu.select('Selecting "Developer Options"...')
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Boot From Internal Disk"...')

    def dev_boot_from_external(self):
        """Boot from external disk in developer mode.

        Menu items in developer warning screen:
            0. Developer Options
            1. Show Debug Info
            2. Enable OS Verification
           *3. Power Off
            4. Language

        Menu items in developer boot options screen:
            0. Boot From Network
            1. Boot Legacy BIOS
            2. Boot From USB or SD Card
           *3. Boot From Internal Disk
            4. Cancel
            5. Power Off
            6. Language
        """
        self.test.wait_for('firmware_screen')
        self.menu.move_to(3, 0)
        self.menu.select('Selecting "Developer Options"...')
        self.test.wait_for('keypress_delay')
        self.menu.move_to(3, 2)
        self.menu.select('Selecting "Boot From USB or SD Card"...')

    def trigger_dev_to_normal(self):
        """
        Trigger dev-to-norm transition.

        Menu items in developer warning screen:
            0. Developer Options
            1. Show Debug Info
            2. Enable OS Verification
           *3. Power Off
            4. Language

        Menu items in to-norm confirmation screen:
           *0. Confirm Enabling OS Verification
            1. Cancel
            2. Power Off
            3. Language

        (*) is the default selection.
        """
        self.test.wait_for('firmware_screen')
        self.menu.move_to(3, 2)
        self.menu.select('Selecting "Enable OS Verification"...')
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecing "Confirm Enabling OS Verification"...')

    def power_off(self):
        """
        Power off the device.

        This method should work in both developer and recovery screens.
        """
        self.test.wait_for('firmware_screen')
        # Either in developer or recovery screen, the "Power Off" option is the
        # default one.
        self.menu.select('Selecting "Power Off"...')


class _MenuModeSwitcher(_BaseMenuModeSwitcher):
    """
    Mode switcher with menu navigator for menu UI.

    The "menu UI" aims to replace both "legacy clamshell UI" and "legacy
    menu UI". See chromium:1033815 for the discussion about the naming.

    Menu items in recovery select screen:
        0. Language
        1. Recovery using phone (always hidden)
        2. Recovery using external disk
        3. Recovery using internet connection (shown if minios_enabled)
        4. Launch diagnostics (shown if minidiag_enabled)
        5. Advanced options
        6. Power off
    """
    RECOVERY_SELECT_ITEM_COUNT = 7

    def _confirm_to_dev(self):
        if self.faft_config.rec_button_dev_switch:
            logging.info('Confirm to-dev by RECOVERY button')
            self.servo.toggle_recovery_switch()
        elif self.faft_config.power_button_dev_switch:
            logging.info('Confirm to-dev by POWER button')
            self.servo.power_normal_press()
        else:
            self.menu.select('Confirm to-dev by menu selection')

    def trigger_rec_to_dev(self):
        """
        Trigger to-dev transition.

        Menu items in advanced options screen:
            0. Language
           *1. Enable developer mode
            2. Back
            3. Power off

        Menu items in to-dev screen:
            0. Language
           *1. Confirm
            2. Cancel
            3. Power off

        (*) is the default selection.
        """
        self.test.wait_for('firmware_screen')
        # The default selection is unknown, navigate to the last item first
        self.menu.move_to(0, self.RECOVERY_SELECT_ITEM_COUNT)
        # Navigate to "Advanced options"
        self.menu.up()
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Advanced options"...')
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Enable developer mode"...')
        self.test.wait_for('keypress_delay')
        # Confirm to-dev transition
        self._confirm_to_dev()

    def dev_boot_from_internal(self):
        """
        Boot from internal disk in developer mode.

        Menu items in developer mode screen:
            0. Language
            1. Return to secure mode
            2. Boot from internal disk
            3. Boot from external disk
            4. Advanced options
            5. Power off
        """
        self.test.wait_for('firmware_screen')
        # Since the default selection is unknown, navigate to item 0 first
        self.menu.move_to(5, 0)
        # Navigate to "Boot from internal disk"
        self.menu.move_to(0, 2)
        self.menu.select('Selecting "Boot from internal disk"...')

    def dev_boot_from_external(self):
        """Boot from external disk in developer mode.

        Menu items in developer mode screen:
            0. Language
            1. Return to secure mode
            2. Boot from internal disk
            3. Boot from external disk
            4. Advanced options
            5. Power off
        """
        self.test.wait_for('firmware_screen')
        # Since the default selection is unknown, navigate to item 0 first
        self.menu.move_to(5, 0)
        # Navigate to "Boot from external disk"
        self.menu.move_to(0, 3)
        self.menu.select('Selecting "Boot from external disk"...')

    def trigger_dev_to_normal(self):
        """
        Trigger dev-to-norm transition.

        Menu items in developer mode screen:
            0. Language
            1. Return to secure mode
            2. Boot from internal disk
            3. Boot from external disk
            4. Advanced options
            5. Power off

        Menu items in to-norm screen:
            0. Language
           *1. Confirm
            2. Cancel
            3. Power off

        (*) is the default selection.
        """
        self.test.wait_for('firmware_screen')
        # Since the default selection is unknown, navigate to item 0 first
        self.menu.move_to(5, 0)
        # Navigate to "Return to secure mode"
        self.menu.down()
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Return to secure mode"...')
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecing "Confirm"...')

    def power_off(self):
        """
        Power off the device.

        This method should work in both developer and recovery screens.
        """
        self.test.wait_for('firmware_screen')
        # Since there are at most 6 menu items in dev/rec screen, move the
        # cursor down 6 times to ensure we reach the last menu item.
        self.menu.move_to(0, 6)
        self.menu.select('Selecting "Power off"...')

    def trigger_rec_to_minidiag(self):
        """
        Trigger rec-to-MiniDiag.

        @raise TestError if MiniDiag is not enabled.
        """

        # Validity check; this only applicable for MiniDiag enabled devices.
        if not self.minidiag_enabled:
            raise error.TestError('MiniDiag is not enabled for this board')

        self.test.wait_for('firmware_screen')
        # The default selection is unknown, so navigate to the last item first
        self.menu.move_to(0, self.RECOVERY_SELECT_ITEM_COUNT)
        # Navigate to "Launch diagnostics"
        self.menu.up()
        self.test.wait_for('keypress_delay')
        self.menu.up()
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Launch diagnostics"...')
        self.test.wait_for('firmware_screen')

    def navigate_minidiag_storage(self):
        """
        Navigate to storage screen.

        Menu items in storage screen:
            0. Language
            1. Page up (disabled)
            2. Page down
            3. Back
            4. Power off

        @raise TestError if MiniDiag is not enabled.
        """

        # Validity check; this only applicable for MiniDiag enabled devices.
        if not self.minidiag_enabled:
            raise error.TestError('MiniDiag is not enabled for this board')

        # From root screen to storage screen
        self.menu.select('Selecting "Storage"...')
        self.test.wait_for('keypress_delay')
        # Since the default selection is unknown, navigate to item 4 first
        self.menu.move_to(0, 4)
        # Navigate to "Back"
        self.menu.up()
        self.test.wait_for('keypress_delay')
        self.menu.select('Back to MiniDiag root screen...')
        self.test.wait_for('keypress_delay')

    def navigate_minidiag_quick_memory_check(self):
        """
        Navigate to quick memory test screen.

        Menu items in quick memory test screen:
            0. Language
            1. Page up (disabled)
            2. Page down (disabled
            3. Back
            4. Power off

        @raise TestError if MiniDiag is not enabled.
        """

        # Validity check; this only applicable for MiniDiag enabled devices.
        if not self.minidiag_enabled:
            raise error.TestError('MiniDiag is not enabled for this board')

        # From root screen to quick memory test screen
        # There might be self test items, so navigate to the last item first
        self.menu.move_to(0, 5)
        self.menu.up()  # full memory test
        self.test.wait_for('keypress_delay')
        self.menu.up()  # quick memory test
        self.test.wait_for('keypress_delay')
        self.menu.select('Selecting "Quick memory test"...')
        self.test.wait_for('keypress_delay')
        # Wait for quick memory test
        self.menu.select('Back to MiniDiag root screen...')
        self.test.wait_for('keypress_delay')

    def reset_and_leave_minidiag(self):
        """
        Reset the DUT and normal boot to leave MiniDiag.

        @raise TestError if MiniDiag is not enabled or no apreset support.
        """

        # Validity check; this only applicable for MiniDiag enabled devices.
        if not self.minidiag_enabled:
            raise error.TestError('MiniDiag is not enabled for this board')

        # Since we want to keep the cbmem log, we need an AP reset and reboot to
        # normal mode
        if self.test.ec.has_command('apreset'):
            logging.info('Trigger apreset')
            self.test.ec.send_command('apreset')
        else:
            raise error.TestError('EC command apreset is not supported')

    def trigger_rec_to_minios(self, older_version=False):
        """
        Trigger recovery-to-MiniOS transition.

        Menu items in advanced options screen, developer mode:
            0. Language
           *1. Debug info
            2. Firmware log
            3. Internet recovery (older version)
            4. Back
            5. Power off

        (*) is the default selection.

        @param older_version: True for selecting the older version button in the
                              advanced options screen, and False for selecting
                              the newer one in the recovery selection screen.
        @raise TestError if MiniOS is not enabled.
        """
        # Validity check
        if not self.minios_enabled:
            raise NotImplementedError

        # Boot to MiniOS through UI menu
        if older_version:
            logging.info('Boot to MiniOS (older version)')
            # The default selection is unknown, so navigate to the last item
            # first
            self.menu.move_to(0, self.RECOVERY_SELECT_ITEM_COUNT)
            # Navigate to "Advanced options"
            self.menu.up()
            self.test.wait_for('keypress_delay')
            self.menu.select('Selecting "Advanced options"...')
            self.test.wait_for('keypress_delay')
            # Navigate to the last item in advanced options
            self.menu.move_to(0, 5)
            self.menu.move_to(5, 3)
            self.menu.select(
                    'Selecting "Internet recovery (older version)"...')
        else:
            logging.info('Boot to MiniOS')
            self.menu.down()
            self.menu.select(
                    'Selecting "Recovery using internet connection"...')

        self.test.wait_for('minios_screen')


_MENU_MODE_SWITCHER_CLASSES = {
        'menu_switcher': _MenuModeSwitcher,
        'tablet_detachable_switcher': _TabletDetachableMenuModeSwitcher,
}


def create_menu_mode_switcher(faft_framework, menu_navigator):
    """
    Create a proper navigator based on its mode switcher type.

    @param faft_framework: The main FAFT framework object.
    @param menu_navigator: The menu navigator for base logic of navigation.
    """
    switcher_type = faft_framework.faft_config.mode_switcher_type
    switcher_class = _MENU_MODE_SWITCHER_CLASSES.get(switcher_type, None)
    if switcher_class is None:
        # Not all devices support menu-based UI, so it is fine to return None.
        logging.info('Switcher type %s is menuless, return None',
                     switcher_type)
        return None
    return switcher_class(faft_framework, menu_navigator)
