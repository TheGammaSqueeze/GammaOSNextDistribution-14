# Lint as: python2, python3
# Copyright 2015 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros import vboot_constants as vboot

DEBOUNCE_STATE = 'debouncing'

class ConnectionError(Exception):
    """Raised on an error of connecting DUT."""
    pass


class _BaseFwBypasser(object):
    """Base class that controls bypass logic for firmware screens."""

    # Duration of holding Volume down button to quickly bypass the developer
    # warning screen in tablets/detachables.
    HOLD_VOL_DOWN_BUTTON_BYPASS = 3

    def __init__(self, faft_framework, menu_navigator):
        self.faft_framework = faft_framework
        self.servo = faft_framework.servo
        self.faft_config = faft_framework.faft_config
        self.client_host = faft_framework._client
        self.ec = getattr(faft_framework, 'ec', None)
        self.menu = menu_navigator


    def bypass_dev_mode(self):
        """Bypass the dev mode firmware logic to boot internal image."""
        raise NotImplementedError


    def bypass_dev_boot_usb(self):
        """Bypass the dev mode firmware logic to boot USB."""
        raise NotImplementedError


    def bypass_rec_mode(self):
        """Bypass the rec mode firmware logic to boot USB."""
        raise NotImplementedError


    def trigger_dev_to_rec(self):
        """Trigger to the rec mode from the dev screen."""
        raise NotImplementedError


    def trigger_rec_to_dev(self):
        """Trigger to the dev mode from the rec screen."""
        raise NotImplementedError


    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen."""
        raise NotImplementedError


    # This is used as a workaround of a bug in RO - DUT does not supply
    # Vbus when in SRC_ACCESSORY state (we set servo to snk before booting
    # to recovery due to the assumption of no PD in RO). It causes that DUT can
    # not see USB Stick in recovery mode(RO) despite being DFP(b/159938441).
    # The bug in RO has been fixed in 251212fb.
    # Some boards already have it in RO, so the issue does not appear
    def check_vbus_and_pd_state(self):
        """Perform PD power and data swap, if DUT is SRC and doesn't supply
        Vbus"""
        if (self.ec and self.faft_config.ec_ro_vbus_bug
                    and self.servo.is_servo_v4_type_c()):
            time.sleep(self.faft_framework.PD_RESYNC_DELAY)
            servo_pr_role = self.servo.get_servo_v4_role()
            if servo_pr_role == 'snk':
                mv = self.servo.get_vbus_voltage()
                # Despite the faft_config, make sure the issue occurs -
                # servo is snk and vbus is not supplied.
                if mv is not None and mv < self.servo.VBUS_THRESHOLD:
                    # Make servo SRC to supply Vbus correctly
                    self.servo.set_servo_v4_role('src')
                    time.sleep(self.faft_framework.PD_RESYNC_DELAY)

            # After reboot, EC can be UFP so check that
            MAX_PORTS = 2
            ec_is_dfp = False
            for port in range(0, MAX_PORTS):
                if self.ec.is_dfp(port):
                    ec_is_dfp = True
                    break

            if not ec_is_dfp:
                # EC is UFP, perform PD Data Swap
                for port in range(0, MAX_PORTS):
                    self.ec.send_command("pd %d swap data" % port)
                    time.sleep(self.faft_framework.PD_RESYNC_DELAY)
                    # Make sure EC is DFP now
                    if self.ec.is_dfp(port):
                        ec_is_dfp = True
                        break

            if not ec_is_dfp:
                # EC is still UFP
                raise error.TestError('DUT is not DFP in recovery mode.')


class _KeyboardBypasser(_BaseFwBypasser):
    """Controls bypass logic via keyboard shortcuts for menu UI."""

    def bypass_dev_mode(self):
        """Bypass the dev mode firmware logic to boot internal image.

        Press Ctrl-D repeatedly. To obtain a low firmware boot time, pressing
        Ctrl+D for every half second until firmware_screen delay has been
        reached.
        """
        logging.info("Pressing Ctrl-D.")
        # At maximum, device waits for twice of firmware_screen delay to
        # bypass the Dev screen.
        timeout = time.time() + (self.faft_config.firmware_screen * 2)
        while time.time() < timeout:
            self.servo.ctrl_d()
            time.sleep(0.5)
            if self.client_host.ping_wait_up(timeout=0.1):
                break


    def bypass_dev_boot_usb(self):
        """Bypass the dev mode firmware logic to boot USB."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing ctrl+u')
        self.servo.ctrl_u()


    def bypass_dev_default_boot(self):
        """Bypass the dev mode firmware logic to boot from default target."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing enter')
        self.menu.select()


    def bypass_rec_mode(self):
        """Bypass the rec mode firmware logic to boot USB."""
        self.servo.switch_usbkey('host')
        self.faft_framework.wait_for('usb_plug', 'Switching usb key to DUT')
        self.check_vbus_and_pd_state()
        self.servo.switch_usbkey('dut')
        logging.info('Enabled dut_sees_usb')
        tries = 3
        while tries > 0 and not self.client_host.wait_up(
                timeout=self.faft_config.delay_reboot_to_ping,
                host_is_down=True):
            tries = tries - 1
            logging.info('connect timed out, try REC_ON, retries left: %d',
                         tries)
            psc = self.servo.get_power_state_controller()
            psc.power_on(psc.REC_ON)
            # Check Vbus after reboot again
            self.check_vbus_and_pd_state()
        logging.info('bypass_rec_mode DONE')


    def trigger_dev_to_rec(self):
        """Trigger to the to-norm screen from the dev screen."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing ctrl+s')
        self.servo.ctrl_s()


    def trigger_rec_to_dev(self):
        """Trigger to the dev mode from the rec screen."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing ctrl+d')
        self.servo.ctrl_d()
        self.faft_framework.wait_for('keypress_delay',
                                     'Pressing button to switch to dev mode')
        if self.faft_config.rec_button_dev_switch:
            logging.info('RECOVERY button pressed to switch to dev mode')
            self.servo.toggle_recovery_switch()
        elif self.faft_config.power_button_dev_switch:
            logging.info('POWER button pressed to switch to dev mode')
            self.servo.power_normal_press()
        else:
            logging.info('ENTER pressed to switch to dev mode')
            self.menu.select()


    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen."""
        # Navigate to to-norm screen
        self.faft_framework.wait_for('firmware_screen', 'Pressing ctrl+s')
        self.servo.ctrl_s()
        # Select "Confirm"
        self.faft_framework.wait_for('keypress_delay', 'Pressing enter')
        self.menu.select()


class _LegacyKeyboardBypasser(_KeyboardBypasser):
    """Controls bypass logic via keyboard shortcuts for legacy clamshell UI."""

    def trigger_dev_to_rec(self):
        """Trigger to the to-norm screen from the dev screen."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing enter')
        self.menu.select()

    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen."""
        self.faft_framework.wait_for('firmware_screen', 'Pressing enter')
        self.menu.select()
        self.faft_framework.wait_for('keypress_delay', 'Pressing enter')
        self.menu.select()


class _JetstreamBypasser(_BaseFwBypasser):
    """Controls bypass logic of Jetstream devices."""

    def bypass_dev_mode(self):
        """Bypass the dev mode firmware logic to boot internal image."""
        # Jetstream does nothing to bypass.
        pass


    def bypass_dev_boot_usb(self):
        """Bypass the dev mode firmware logic to boot USB."""
        self.servo.switch_usbkey('dut')
        self.faft_framework.wait_for('firmware_screen', 'Toggling development switch')
        self.servo.toggle_development_switch()


    def bypass_rec_mode(self):
        """Bypass the rec mode firmware logic to boot USB."""
        self.servo.switch_usbkey('host')
        self.faft_framework.wait_for('usb_plug', 'Switching usb key to DUT')
        self.check_vbus_and_pd_state()
        self.servo.switch_usbkey('dut')
        if not self.client_host.ping_wait_up(
                timeout=self.faft_config.delay_reboot_to_ping):
            psc = self.servo.get_power_state_controller()
            psc.power_on(psc.REC_ON)
            # Check Vbus after reboot again
            self.check_vbus_and_pd_state()


    def trigger_dev_to_rec(self):
        """Trigger to the rec mode from the dev screen."""
        # Jetstream does not have this triggering logic.
        raise NotImplementedError


    def trigger_rec_to_dev(self):
        """Trigger to the dev mode from the rec screen."""
        self.servo.disable_development_mode()
        self.faft_framework.wait_for('firmware_screen', 'Toggling development switch')
        self.servo.toggle_development_switch()


    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen."""
        # Jetstream does not have this triggering logic.
        raise NotImplementedError


class _TabletDetachableBypasser(_BaseFwBypasser):
    """Controls bypass logic of tablet/ detachable chromebook devices."""

    def set_button(self, button, duration, info):
        """Helper method that sets the button hold time for UI selections"""
        self.servo.set_nocheck(button, duration)
        self.faft_framework.wait_for('keypress_delay')
        logging.info(info)


    def bypass_dev_boot_usb(self):
        """Bypass the dev mode firmware logic to boot USB.

        On tablets/ detachables, recovery entered by pressing pwr, vol up
        & vol down buttons for 10s.
           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in DEV screen:
                 Boot legacy BIOS
                 Boot USB image
                 Boot developer image*
                 Cancel
                 Power off
                 Language

        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.

        Note: if dev_default_boot=usb, the default selection will start on USB,
        and this will move up one to legacy boot instead.
        """
        self.trigger_dev_screen()
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume up')
        self.set_button('volume_up_hold', 100, ('Selecting power as'
                        ' enter key to select Boot USB Image'))
        self.menu.select()

    def bypass_dev_default_boot(self):
        """Open the Developer Options menu, and accept the default boot device

           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in DEV screen:
                 Boot legacy BIOS*      (default if dev_default_boot=legacy)
                 Boot USB image*        (default if dev_default_boot=usb)
                 Boot developer image*  (default if dev_default_boot=disk)
                 Cancel
                 Power off
                 Language

        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.trigger_dev_screen()
        self.faft_framework.wait_for('firmware_screen', 'Pressing power button')
        logging.info('Selecting power as enter key to accept the default'
                     ' boot option.')
        self.menu.select()

    def bypass_rec_mode(self):
        """Bypass the rec mode firmware logic to boot USB."""
        self.servo.switch_usbkey('host')
        self.faft_framework.wait_for('usb_plug', 'Switching usb key to DUT')
        self.check_vbus_and_pd_state()
        self.servo.switch_usbkey('dut')
        logging.info('Enabled dut_sees_usb')
        if not self.client_host.ping_wait_up(
                timeout=self.faft_config.delay_reboot_to_ping):
            logging.info('ping timed out, try REC_ON')
            psc = self.servo.get_power_state_controller()
            psc.power_on(psc.REC_ON)
            # Check Vbus after reboot again
            self.check_vbus_and_pd_state()


    def bypass_dev_mode(self):
        """Bypass the developer warning screen immediately to boot into
        internal disk.

        On tablets/detachables, press & holding the Volume down button for
        3-seconds will quickly bypass the developer warning screen.
        """
        # Unit for the "volume_down_hold" console command is msec.
        duration = (self.HOLD_VOL_DOWN_BUTTON_BYPASS + 0.1) * 1000
        logging.info("Press and hold volume down button for %.1f seconds to "
                     "immediately bypass the Developer warning screen.",
                     self.HOLD_VOL_DOWN_BUTTON_BYPASS + 0.1)
        # At maximum, device waits for twice of firmware_screen delay to
        # bypass the Dev screen.
        timeout = time.time() + (self.faft_config.firmware_screen * 2)
        # To obtain a low firmware boot time, volume_down button pressed for
        # every 3.1 seconds until firmware_screen delay has been reached.
        while time.time() < timeout:
            self.servo.set_nocheck('volume_down_hold', duration)
            # After pressing 'volume_down_hold' button, wait for 0.1 seconds
            # before start pressing the button for next iteration.
            time.sleep(0.1)
            if self.client_host.ping_wait_up(timeout=0.1):
                break


    def trigger_dev_screen(self):
        """Helper method that transitions from DEVELOPER WARNING to DEV screen

           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in DEV screen:
                 Boot legacy BIOS
                 Boot USB image
                 Boot developer image*
                 Cancel
                 Power off
                 Language
        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume up')
        self.servo.set_nocheck('volume_up_hold', 100)
        self.faft_framework.wait_for('keypress_delay', 'Pressing volume up')
        self.servo.set_nocheck('volume_up_hold', 100)
        self.faft_framework.wait_for('keypress_delay', 'Pressing volume up')
        self.set_button('volume_up_hold', 100, ('Selecting power '
                        'as enter key to select Developer Options'))
        self.menu.select()


    def trigger_rec_to_dev(self):
        """Trigger to the dev mode from the rec screen using vol up button.

        On tablets/ detachables, recovery entered by pressing pwr, vol up
        & vol down buttons for 10s. TO_DEV screen is entered by pressing
        vol up & vol down buttons together on the INSERT screen.
           Menu options seen in TO_DEV screen:
                 Confirm enabling developer mode
                 Cancel*
                 Power off
                 Language
        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume up + volume down')
        self.set_button('volume_up_down_hold', 100, ('Enter Recovery Menu.'))
        self.faft_framework.wait_for('keypress_delay', 'Pressing volume up')
        self.set_button('volume_up_hold', 100, ('Selecting power as '
                        'enter key to select Confirm Enabling Developer Mode'))
        self.menu.select()
        self.faft_framework.wait_for('firmware_screen')


    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen.

           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in TO_NORM screen:
                 Confirm Enabling Verified Boot*
                 Cancel
                 Power off
                 Language
        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume up')
        self.set_button('volume_up_hold', 100, ('Selecting '
                        'Enable Root Verification using pwr '
                        'button to enter TO_NORM screen'))
        self.menu.select()
        logging.info('Transitioning from DEV to TO_NORM screen.')
        self.faft_framework.wait_for('firmware_screen', 'Pressing power button')
        logging.info('Selecting Confirm Enabling Verified '
                        'Boot using pwr button in '
                        'TO_NORM screen')
        self.menu.select()

    def trigger_dev_to_rec(self):
        """Trigger to the TO_NORM screen from the dev screen.
           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in TO_NORM screen:
                 Confirm Enabling Verified Boot*
                 Cancel
                 Power off
                 Language
        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume up')
        self.set_button('volume_up_hold', 100, ('Selecting '
                        'Enable Root Verification using pwr '
                        'button to enter TO_NORM screen'))
        self.menu.select()
        logging.info('Transitioning from DEV to TO_NORM screen.')
        self.faft_framework.wait_for('firmware_screen', 'Pressing volume down')

        # In firmware_FwScreenPressPower, test will power off the DUT using
        # Power button in second screen (TO_NORM screen) so scrolling to
        # Power-off is necessary in this case. Hence scroll to Power-off as
        # a generic action and wait for next action of either Lid close or
        # power button press.
        self.servo.set_nocheck('volume_down_hold', 100)
        self.faft_framework.wait_for('keypress_delay', 'Pressing volume down')
        self.servo.set_nocheck('volume_down_hold', 100)
        self.faft_framework.wait_for('keypress_delay')


class _BaseModeSwitcher(object):
    """Base class that controls firmware mode switching."""

    HOLD_VOL_DOWN_BUTTON_BYPASS = _BaseFwBypasser.HOLD_VOL_DOWN_BUTTON_BYPASS

    FW_BYPASSER_CLASS = _BaseFwBypasser

    def __init__(self, faft_framework, menu_navigator):
        self.faft_framework = faft_framework
        self.client_host = faft_framework._client
        self.faft_client = faft_framework.faft_client
        self.servo = faft_framework.servo
        self.faft_config = faft_framework.faft_config
        self.checkers = faft_framework.checkers
        self.menu = menu_navigator
        self.bypasser = self._create_fw_bypasser()
        original_boot_mode = self.faft_client.system.get_boot_mode()
        # Only resume to normal/dev mode after test, not recovery.
        self._backup_mode = 'dev' if original_boot_mode == 'dev' else 'normal'

    def _create_fw_bypasser(self):
        """Creates a proper firmware bypasser.

        @rtype: _BaseFwBypasser
        """
        return self.FW_BYPASSER_CLASS(self.faft_framework, self.menu)

    def setup_mode(self, to_mode, allow_gbb_force=False):
        """Setup for the requested boot mode.

        It makes sure the system in the requested mode. If not, it tries to
        do so.

        @param to_mode: A string of boot mode, one of 'normal', 'dev', or 'rec'.
        @param allow_gbb_force: Bool. If True, allow forcing dev mode via GBB
                                flags. This is more reliable, but it can prevent
                                testing other mode-switch workflows.
        @raise TestFail: If the system not switched to expected mode after
                         reboot_to_mode.

        """
        current_mode = self.faft_client.system.get_boot_mode()
        if current_mode == to_mode:
            logging.debug('System already in expected %s mode.', to_mode)
            return
        logging.info('System not in expected %s mode. Reboot into it.',
                     to_mode)

        self.reboot_to_mode(to_mode, allow_gbb_force=allow_gbb_force)
        current_mode = self.faft_client.system.get_boot_mode()
        if current_mode != to_mode:
            raise error.TestFail(
                    'After setup_mode, wanted mode=%s but got %s' %
                    (to_mode, current_mode))

    def restore_mode(self):
        """Restores original dev mode status if it has changed.

        @raise TestFail: If the system not restored to expected mode.
        """
        if self._backup_mode is None:
            logging.debug('No backup mode to restore.')
            return
        current_mode = self.faft_client.system.get_boot_mode()
        if current_mode == self._backup_mode:
            logging.debug('System already in backup %s mode.', current_mode)
            return

        self.reboot_to_mode(self._backup_mode, allow_gbb_force=True)
        current_mode = self.faft_client.system.get_boot_mode()
        if current_mode != self._backup_mode:
            raise error.TestFail(
                    'After restore_mode, wanted mode=%s but got %s' %
                    (self._backup_mode, current_mode))
        self._backup_mode = None

    def reboot_to_mode(self,
                       to_mode,
                       allow_gbb_force=False,
                       sync_before_boot=True,
                       wait_for_dut_up=True,
                       rec_usb_state='dut'):
        """Reboot and execute the mode switching sequence.

        Normally this method simulates what a user would do to switch between
        different modes of ChromeOS. However, if allow_gbb_force is True, then
        booting to dev mode will instead be forced by GBB flags.

        Note that the modes are end-states where the OS is booted up
        to the Welcome screen, so it takes care of navigating through
        intermediate steps such as various boot confirmation screens.

        From the user perspective, these are the states (note that there's also
        a rec_force_mrc mode which is like rec mode but forces MRC retraining):

        normal <-----> dev <------ rec
          ^                         ^
          |                         |
          +-------------------------+

        Normal <-----> Dev:
          _enable_dev_mode_and_reboot()

        Rec,normal -----> Dev:
          disable_rec_mode_and_reboot()

        Any -----> normal:
          _enable_normal_mode_and_reboot()

        Normal <-----> rec:
          enable_rec_mode_and_reboot(usb_state=rec_usb_state)

        Normal <-----> rec_force_mrc:
          _enable_rec_mode_force_mrc_and_reboot(usb_state=rec_usb_state)

        Note that one shouldn't transition to dev again without going through the
        normal mode.  This is because trying to disable os_verification when it's
        already off is not supported by reboot_to_mode.

        @param to_mode: The target mode, one of 'normal', 'dev', or 'rec'.
        @param allow_gbb_force: Bool. If True, allow forcing dev mode via GBB
                                flags. This is more reliable, but it can prevent
                                testing other mode-switch workflows.
        @param sync_before_boot: True to sync to disk before booting.
        @param wait_for_dut_up: True to wait DUT online again. False to do the
                                reboot and mode switching sequence only and may
                                need more operations to pass the firmware
                                screen.
        @param rec_usb_state: None or a string, one of 'dut', 'host', or 'off'.
                              This parameter is only valid when to_mode is 'rec'
                              or 'rec_force_mrc'. Set this to None to prevent
                              changing the USB state before rebooting.
        """
        logging.info(
                '-[ModeSwitcher]-[ start reboot_to_mode(%r, %r, %r, %r)]-',
                to_mode, sync_before_boot, allow_gbb_force, wait_for_dut_up)

        from_mode = self.faft_client.system.get_boot_mode()
        note = 'reboot_to_mode: from=%s, to=%s' % (from_mode, to_mode)

        if sync_before_boot:
            lines = self.faft_client.system.run_shell_command_get_output(
                'crossystem')
            logging.debug('-[ModeSwitcher]- crossystem output:\n%s',
                          '\n'.join(lines))
            devsw_cur = self.faft_client.system.get_crossystem_value(
                'devsw_cur')
            note += ', devsw_cur=%s' % devsw_cur
            self.faft_framework.blocking_sync(freeze_for_reset=True)
        note += '.'

        # If booting to anything but dev mode, make sure we're not forcing dev.
        # This is irrespective of allow_gbb_force: disabling the flag doesn't
        # force a mode, it just stops forcing dev.
        if to_mode != 'dev':
            self.faft_framework.clear_set_gbb_flags(
                    vboot.GBB_FLAG_FORCE_DEV_SWITCH_ON, 0, reboot=False)

        if to_mode == 'rec':
            self.enable_rec_mode_and_reboot(usb_state=rec_usb_state)
        elif to_mode == 'rec_force_mrc':
            self._enable_rec_mode_force_mrc_and_reboot(usb_state=rec_usb_state)
        elif to_mode == 'dev':
            if allow_gbb_force:
                self.faft_framework.clear_set_gbb_flags(
                        0, vboot.GBB_FLAG_FORCE_DEV_SWITCH_ON, reboot=True)
            else:
                self._enable_dev_mode_and_reboot()
                if wait_for_dut_up:
                    self.bypass_dev_mode()
        elif to_mode == 'normal':
            self._enable_normal_mode_and_reboot()
        else:
            raise NotImplementedError('Unexpected boot mode param: %s',
                                      to_mode)
        if wait_for_dut_up:
            self.wait_for_client(retry_power_on=True, note=note)

        logging.info('-[ModeSwitcher]-[ end reboot_to_mode(%r, %r, %r, %r)]-',
                     to_mode, sync_before_boot, allow_gbb_force,
                     wait_for_dut_up)

    def simple_reboot(self, reboot_type='warm', sync_before_boot=True):
        """Simple reboot method

        Just reboot the DUT using either cold or warm reset.  Does not wait for
        DUT to come back online.  Will wait for test to handle this.

        @param reboot_type: A string of reboot type, 'warm' or 'cold'.
                            If reboot_type != warm/cold, raise exception.
        @param sync_before_boot: True to sync to disk before booting.
                                 If sync_before_boot=False, DUT offline before
                                 calling mode_aware_reboot.
        """
        if reboot_type == 'warm':
            reboot_method = self.servo.get_power_state_controller().warm_reset
        elif reboot_type == 'cold':
            reboot_method = self.servo.get_power_state_controller().reset
        else:
            raise NotImplementedError('Not supported reboot_type: %s',
                                      reboot_type)
        if sync_before_boot:
            boot_id = self.faft_framework.get_bootid()
            self.faft_framework.blocking_sync(freeze_for_reset=True)
        logging.info("-[ModeSwitcher]-[ start simple_reboot(%r) ]-",
                     reboot_type)
        reboot_method()
        if sync_before_boot:
            self.wait_for_client_offline(orig_boot_id=boot_id)
        logging.info("-[ModeSwitcher]-[ end simple_reboot(%r) ]-",
                     reboot_type)

    def mode_aware_reboot(self, reboot_type=None, reboot_method=None,
                          sync_before_boot=True, wait_for_dut_up=True):
        """Uses a mode-aware way to reboot DUT.

        For example, if DUT is in dev mode, it requires pressing Ctrl-D to
        bypass the developer screen.

        @param reboot_type: A string of reboot type, one of 'warm', 'cold', or
                            'custom'. Default is a warm reboot.
        @param reboot_method: A custom method to do the reboot. Only use it if
                              reboot_type='custom'.
        @param sync_before_boot: True to sync to disk before booting.
                                 If sync_before_boot=False, DUT offline before
                                 calling mode_aware_reboot.
        @param wait_for_dut_up: True to wait DUT online again. False to do the
                                reboot only.
        """
        if reboot_type is None or reboot_type == 'warm':
            reboot_method = self.servo.get_power_state_controller().warm_reset
        elif reboot_type == 'cold':
            reboot_method = self.servo.get_power_state_controller().reset
        elif reboot_type != 'custom':
            raise NotImplementedError('Not supported reboot_type: %s',
                                      reboot_type)

        logging.info("-[ModeSwitcher]-[ start mode_aware_reboot(%r, %s, ..) ]-",
                     reboot_type, reboot_method.__name__)
        is_dev = is_rec = is_devsw_boot = False
        if sync_before_boot:
            is_dev = self.checkers.mode_checker('dev')
            is_rec = self.checkers.mode_checker('rec')
            is_devsw_boot = self.checkers.crossystem_checker(
                                               {'devsw_boot': '1'}, True)
            boot_id = self.faft_framework.get_bootid()

            self.faft_framework.blocking_sync(reboot_type != 'custom')
        if is_rec:
            logging.info("-[mode_aware_reboot]-[ is_rec=%s is_dev_switch=%s ]-",
                         is_rec, is_devsw_boot)
        else:
            logging.info("-[mode_aware_reboot]-[ is_dev=%s ]-", is_dev)
        reboot_method()
        if sync_before_boot:
            self.wait_for_client_offline(orig_boot_id=boot_id)
        # Encapsulating the behavior of skipping dev firmware screen,
        # hitting ctrl-D
        # Note that if booting from recovery mode, we can predict the next
        # boot based on the developer switch position at boot (devsw_boot).
        # If devsw_boot is True, we will call bypass_dev_mode after reboot.
        if is_dev or is_devsw_boot:
            self.bypass_dev_mode()
        if wait_for_dut_up:
            self.wait_for_client()
        logging.info("-[ModeSwitcher]-[ end mode_aware_reboot(%r, %s, ..) ]-",
                     reboot_type, reboot_method.__name__)


    def enable_rec_mode_and_reboot(self, usb_state=None):
        """Switch to rec mode and reboot.

        This method emulates the behavior of the old physical recovery switch,
        i.e. switch ON + reboot + switch OFF, and the new keyboard controlled
        recovery mode, i.e. just press Power + Esc + Refresh.

        @param usb_state: A string, one of 'dut', 'host', or 'off'.
        """
        psc = self.servo.get_power_state_controller()
        # Switch the USB key when AP is on, because there is a
        # bug (b/172909077) - using "image_usbkey_direction:usb_state", when
        # AP if off may cause not recognizing the file system,
        # so system won't boot in recovery from USB.
        # When the issue is fixed, it can be done when AP is off.
        if usb_state:
            self.servo.switch_usbkey(usb_state)
        psc.power_off()
        psc.power_on(psc.REC_ON)
        # Check VBUS and pd state only if we are going to boot
        # to ChromeOS in the recovery mode
        if usb_state == 'dut':
            self.bypasser.check_vbus_and_pd_state()


    def _enable_rec_mode_force_mrc_and_reboot(self, usb_state=None):
        """Switch to rec mode, enable force mrc cache retraining, and reboot.

        This method emulates the behavior of the old physical recovery switch,
        i.e. switch ON + reboot + switch OFF, and the new keyboard controlled
        recovery mode, i.e. just press Power + Esc + Refresh.

        @param usb_state: A string, one of 'dut', 'host', or 'off'.
        """
        psc = self.servo.get_power_state_controller()
        # Switch the USB key when AP is on, because there is a
        # bug (b/172909077) - using "image_usbkey_direction:usb_state", when
        # AP if off may cause not recognizing the file system,
        # so system won't boot in recovery from USB.
        # When the issue is fixed, it can be done when AP is off.
        if usb_state:
            self.servo.switch_usbkey(usb_state)
        psc.power_off()
        psc.power_on(psc.REC_ON_FORCE_MRC)

    def disable_rec_mode_and_reboot(self, usb_state=None):
        """Disable the rec mode and reboot.

        It is achieved by calling power state controller to do a normal
        power on.
        """
        psc = self.servo.get_power_state_controller()
        psc.power_off()
        self.faft_framework.wait_for('ec_boot_to_pwr_button', 'Powering on')
        psc.power_on(psc.REC_OFF)


    def _enable_dev_mode_and_reboot(self):
        """Switch to developer mode and reboot."""
        raise NotImplementedError


    def _enable_normal_mode_and_reboot(self):
        """Switch to normal mode and reboot."""
        raise NotImplementedError


    # Redirects the following methods to FwBypasser
    def bypass_dev_mode(self):
        """Bypass the dev mode firmware logic to boot internal image."""
        logging.info("-[bypass_dev_mode]-")
        self.bypasser.bypass_dev_mode()


    def bypass_dev_boot_usb(self):
        """Bypass the dev mode firmware logic to boot USB."""
        logging.info("-[bypass_dev_boot_usb]-")
        self.bypasser.bypass_dev_boot_usb()


    def bypass_dev_default_boot(self):
        """Bypass the dev mode firmware logic to boot from default target."""
        logging.info("-[bypass_dev_default_boot]-")
        self.bypasser.bypass_dev_default_boot()


    def bypass_rec_mode(self):
        """Bypass the rec mode firmware logic to boot USB."""
        logging.info("-[bypass_rec_mode]-")
        self.bypasser.bypass_rec_mode()


    def trigger_dev_to_rec(self):
        """Trigger to the rec mode from the dev screen."""
        self.bypasser.trigger_dev_to_rec()


    def trigger_rec_to_dev(self):
        """Trigger to the dev mode from the rec screen."""
        self.bypasser.trigger_rec_to_dev()


    def trigger_dev_to_normal(self):
        """Trigger to the normal mode from the dev screen."""
        self.bypasser.trigger_dev_to_normal()


    def wait_for_client(self, timeout=180, retry_power_on=False,
                        debounce_power_state=True, note=''):
        """Wait for the client to come back online.

        New remote processes will be launched if their used flags are enabled.

        @param timeout: Time in seconds to wait for the client SSH daemon to
                        come up.
        @param retry_power_on: Try to power on the DUT if it isn't in S0.
        @param debounce_power_state: Wait until power_state is the same two
                                     times in a row to determine the actual
                                     power_state.
        @param note: Extra note to add to the end of the error text
        @raise ConnectionError: Failed to connect DUT.
        """
        logging.info("-[FAFT]-[ start wait_for_client(%ds) ]---",
                     timeout if retry_power_on else 0)
        # Wait for the system to be powered on before trying the network
        # Skip "None" result because that indicates lack of EC or problem
        # querying the power state.
        current_timer = 0
        self.faft_framework.wait_for('delay_powerinfo_stable',
                                     'checking power state')
        power_state = self.faft_framework.get_power_state()

        # The device may transition between states. Wait until the power state
        # is stable for two seconds before determining the state.
        if debounce_power_state:
            last_state = power_state
            power_state = DEBOUNCE_STATE

        while (timeout > current_timer and
               power_state not in (self.faft_framework.POWER_STATE_S0, None)):
            time.sleep(2)
            current_timer += 2
            power_state = self.faft_framework.get_power_state()

            # If the state changed, debounce it.
            if debounce_power_state and power_state != last_state:
                last_state = power_state
                power_state = DEBOUNCE_STATE

            logging.info('power state: %s', power_state)

            # Only power-on the device if it has been consistently out of
            # S0.
            if (retry_power_on and
                power_state not in (self.faft_framework.POWER_STATE_S0,
                                    None, DEBOUNCE_STATE)):
                logging.info("-[FAFT]-[ retry powering on the DUT ]---")
                psc = self.servo.get_power_state_controller()
                psc.retry_power_on()

        # Use the last state if the device didn't reach a stable state in
        # timeout seconds.
        if power_state == DEBOUNCE_STATE:
            power_state = last_state
        if power_state not in (self.faft_framework.POWER_STATE_S0, None):
            msg = 'DUT unexpectedly down, power state is %s.' % power_state
            if note:
                msg += ' %s' % note
            raise ConnectionError(msg)

        # Wait for the system to respond to ping before attempting ssh
        if self.client_host.use_icmp and not self.client_host.ping_wait_up(
                timeout):
            logging.warning("-[FAFT]-[ system did not respond to ping ]")
        if self.client_host.wait_up(timeout, host_is_down=True):
            # Check the FAFT client is avaiable.
            self.faft_client.system.is_available()
            # Stop update-engine as it may change firmware/kernel.
            self.faft_framework.faft_client.updater.stop_daemon()
        else:
            logging.error('wait_for_client() timed out.')
            power_state = self.faft_framework.get_power_state()
            msg = 'DUT is still down unexpectedly.'
            if power_state:
                msg += ' Power state: %s.' % power_state
            if note:
                msg += ' %s' % note
            raise ConnectionError(msg)
        logging.info("-[FAFT]-[ end wait_for_client ]-----")


    def wait_for_client_offline(self, timeout=60, orig_boot_id=None):
        """Wait for the client to come offline.

        @param timeout: Time in seconds to wait the client to come offline.
        @param orig_boot_id: A string containing the original boot id.
        @raise ConnectionError: Failed to wait DUT offline.
        """
        if not self.client_host.ping_wait_down(timeout):
            if orig_boot_id and self.client_host.get_boot_id() != orig_boot_id:
                logging.warning('Reboot done very quickly.')
                return
            raise ConnectionError('DUT is still up unexpectedly')


    def launch_minios(self, minios_priority=None):
        """Reboot to recovery mode and launch MiniOS with specified priority.
        The DUT must have the config 'minios_enabled'.

        @param minios_priority: Set to 'a' or 'b' for specified priority; Set to
                                None to skip assigning the priority.
        @raise ConnectionError: Failed to wait DUT offline.
        @raise NotImplementedError: DUT does not support MiniOS.
        """
        raise NotImplementedError

    def leave_minios(self, is_devsw_boot=False):
        """Leave MiniOS and use a mode-aware way to reboot DUT.

        The DUT must have the config 'minios_enabled'.
        This method will reboot DUT to leave MiniOS.

        @param is_devsw_boot: True to bypass the developer screen.
        @raise ConnectionError: Failed to wait DUT offline.
        @raise NotImplementedError: DUT does not support MiniOS.
        """
        raise NotImplementedError


class _MenuSwitcher(_BaseModeSwitcher):
    """Mode switcher via keyboard shortcuts for menu UI."""

    FW_BYPASSER_CLASS = _KeyboardBypasser

    def _enable_dev_mode_and_reboot(self):
        """Switch to developer mode and reboot."""
        logging.info("Enabling keyboard controlled developer mode")
        # Rebooting EC with rec mode on. Should power on AP.
        # Plug out USB disk for preventing recovery boot without warning
        self.enable_rec_mode_and_reboot(usb_state='host')
        self.wait_for_client_offline()
        self.bypasser.trigger_rec_to_dev()

    def _enable_normal_mode_and_reboot(self):
        """Switch to normal mode and reboot."""
        logging.info("Disabling keyboard controlled developer mode")
        self.disable_rec_mode_and_reboot()
        self.wait_for_client_offline()
        self.bypasser.trigger_dev_to_normal()

    def launch_minios(self, minios_priority=None):
        """Reboot to recovery mode and launch MiniOS with specified priority.
        The DUT must have the config 'minios_enabled'.

        @param minios_priority: Set to 'a' or 'b' for specified priority; Set to
                                None to skip assigning the priority.
        @raise ConnectionError: Failed to wait DUT offline.
        @raise NotImplementedError: DUT does not support MiniOS.
        """
        # Validity check
        if not self.faft_config.minios_enabled:
            raise NotImplementedError

        # Set MiniOS priority
        if minios_priority:
            logging.info('Set the MiniOS priority to %s', minios_priority)
            self.faft_client.system.set_minios_priority(minios_priority)
        else:
            logging.info('Use the original MiniOS priority setting')

        # Boot to recovery mode to launch MiniOS. We do not want to change the
        # usb state since it will disturb the MiniOS booting flow.
        logging.info('Boot into recovery mode')
        self.reboot_to_mode(to_mode="rec",
                            wait_for_dut_up=False,
                            rec_usb_state=None)
        self.faft_framework.wait_for('firmware_screen')

        # Use Ctrl+R shortcut to boot MiniOS
        logging.info('Try to boot MiniOS')
        self.servo.ctrl_r()
        self.faft_framework.wait_for('minios_screen')

    def leave_minios(self, is_devsw_boot=False):
        """Leave MiniOS and use a mode-aware way to reboot DUT.

        The DUT must have the config 'minios_enabled'.
        This method will reboot DUT to leave MiniOS.

        @param is_devsw_boot: True to bypass the developer screen.
        @raise ConnectionError: Failed to wait DUT offline.
        @raise NotImplementedError: DUT does not support MiniOS.
        """
        # mode_aware_reboot() cannot be used here since it leverages autotest
        # libraries which don't exist within MiniOS.
        self.simple_reboot(sync_before_boot=False)
        if is_devsw_boot:
            self.faft_framework.wait_for('firmware_screen')
            self.bypass_dev_mode()


class _KeyboardDevSwitcher(_MenuSwitcher):
    """Mode switcher via keyboard shortcuts for legacy clamshell UI."""

    FW_BYPASSER_CLASS = _LegacyKeyboardBypasser


class _JetstreamSwitcher(_BaseModeSwitcher):
    """Mode switcher for Jetstream devices."""

    FW_BYPASSER_CLASS = _JetstreamBypasser

    def _enable_dev_mode_and_reboot(self):
        """Switch to developer mode and reboot."""
        logging.info("Enabling Jetstream developer mode")
        self.enable_rec_mode_and_reboot(usb_state='host')
        self.wait_for_client_offline()
        self.bypasser.trigger_rec_to_dev()

    def _enable_normal_mode_and_reboot(self):
        """Switch to normal mode and reboot."""
        logging.info("Disabling Jetstream developer mode")
        self.servo.disable_development_mode()
        self.enable_rec_mode_and_reboot(usb_state='host')
        self.faft_framework.wait_for('firmware_screen', 'Disabling rec and rebooting')
        self.disable_rec_mode_and_reboot(usb_state='host')


class _TabletDetachableSwitcher(_BaseModeSwitcher):
    """Mode switcher for legacy menu UI."""

    FW_BYPASSER_CLASS = _TabletDetachableBypasser

    def _enable_dev_mode_and_reboot(self):
        """Switch to developer mode and reboot.

        On tablets/ detachables, recovery entered by pressing pwr, vol up
        & vol down buttons for 10s.
           Menu options seen in RECOVERY screen:
                 Enable Developer Mode
                 Show Debug Info
                 Power off*
                 Language
        """
        logging.info('Enabling tablets/detachable recovery mode')
        self.enable_rec_mode_and_reboot(usb_state='host')
        self.wait_for_client_offline()
        self.bypasser.trigger_rec_to_dev()

    def _enable_normal_mode_and_reboot(self):
        """Switch to normal mode and reboot.

           Menu options seen in DEVELOPER WARNING screen:
                 Developer Options
                 Show Debug Info
                 Enable Root Verification
                 Power Off*
                 Language
           Menu options seen in TO_NORM screen:
                 Confirm Enabling Verified Boot
                 Cancel
                 Power off*
                 Language
        Vol up button selects previous item, vol down button selects
        next item and pwr button selects current activated item.
        """
        self.disable_rec_mode_and_reboot()
        self.wait_for_client_offline()
        self.bypasser.trigger_dev_to_normal()


_SWITCHER_CLASSES = {
    'menu_switcher': _MenuSwitcher,
    'keyboard_dev_switcher': _KeyboardDevSwitcher,
    'jetstream_switcher': _JetstreamSwitcher,
    'tablet_detachable_switcher': _TabletDetachableSwitcher,
}


def create_mode_switcher(faft_framework, menu_navigator):
    """Creates a proper mode switcher.

    @param faft_framework: The main FAFT framework object.
    @param menu_navigator: The menu navigator for base logic of navigation.
    """
    switcher_type = faft_framework.faft_config.mode_switcher_type
    switcher_class = _SWITCHER_CLASSES.get(switcher_type, None)
    if switcher_class is None:
        raise NotImplementedError('Not supported mode_switcher_type: %s',
                                  switcher_type)
    else:
        return switcher_class(faft_framework, menu_navigator)
