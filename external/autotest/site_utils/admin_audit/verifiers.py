#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging


import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import utils as client_utils
from autotest_lib.server.cros.storage import storage_validate as storage
from autotest_lib.server.cros.servo.keyboard import servo_keyboard_flasher
from autotest_lib.server.cros.repair import mac_address_helper
from autotest_lib.site_utils.admin_audit import base
from autotest_lib.site_utils.admin_audit import constants
from autotest_lib.site_utils.admin_audit import rpm_validator
from autotest_lib.site_utils.admin_audit import servo_updater

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = client_utils.metrics_mock

# Common status used for statistics.
STATUS_FAIL = 'fail'
STATUS_SUCCESS = 'success'
STATUS_SKIPPED = 'skipped'


class VerifyDutStorage(base._BaseDUTVerifier):
    """Verify the state of the storage on the DUT

    The process to determine the type of storage and read metrics
    of usage and EOL(end-of-life) information to determine the
    state.
    Supported storage types: MMS, NVME, SSD.
    Possible states are:
      UNKNOWN - not access to the DUT, not determine type of storage,
                not information to determine metrics
      NORMAL - the storage is in good shape and will work stable
                device will work stable. (supported for all types)
      ACCEPTABLE - the storage almost used all resources, device will
                work stable but it is better be ready for replacement
                device will work stable. (supported by MMS, NVME)
      NEED_REPLACEMENT - the storage broken or worn off the life limit
                device can work by not stable and can cause the
                flakiness on the tests. (supported by all types)
    """
    def __init__(self, dut_host):
        super(VerifyDutStorage, self).__init__(dut_host)
        self._state = None

    def _verify(self, set_label=True, run_badblocks=None):
        if not self.host_is_up():
            logging.info('Host is down; Skipping the verification')
            return
        try:
            validator = storage.StorageStateValidator(self.get_host())
            storage_type = validator.get_type()
            logging.debug('Detected storage type: %s', storage_type)
            storage_state = validator.get_state(run_badblocks=run_badblocks)
            logging.debug('Detected storage state: %s', storage_state)
            state = self.convert_state(storage_state)
            if state and set_label:
                self._set_host_info_state(constants.DUT_STORAGE_STATE_PREFIX,
                                          state)
                if state == constants.HW_STATE_NEED_REPLACEMENT:
                    self.get_host().set_device_needs_replacement(
                        resultdir=self.get_result_dir())
            self._state = state
        except Exception as e:
            raise base.AuditError('Exception during getting state of'
                                  ' storage %s' % str(e))

    def convert_state(self, state):
        """Mapping state from validator to verifier"""
        if state == storage.STORAGE_STATE_NORMAL:
            return constants.HW_STATE_NORMAL
        if state == storage.STORAGE_STATE_WARNING:
            return constants.HW_STATE_ACCEPTABLE
        if state == storage.STORAGE_STATE_CRITICAL:
            return constants.HW_STATE_NEED_REPLACEMENT
        return None

    def get_state(self):
        return self._state


class VerifyServoUsb(base._BaseServoVerifier):
    """Verify the state of the USB-drive on the Servo

    The process to determine by checking the USB-drive on having any
    bad sectors on it.
    Possible states are:
      UNKNOWN - not access to the device or servo, not available
                software on the servo.
      NORMAL - the device available for testing and not bad sectors.
                was found on it, device will work stable
      NEED_REPLACEMENT - the device available for testing and
                some bad sectors were found on it. The device can
                work but cause flakiness in the tests or repair process.

    badblocks errors:
    No such device or address while trying to determine device size
    """
    def _verify(self):
        if not self.servo_is_up():
            logging.info('Servo not initialized; Skipping the verification')
            return
        try:
            usb = self.get_host()._probe_and_validate_usb_dev()
            logging.debug('USB path: %s', usb)
        except Exception as e:
            usb = ''
            logging.debug('(Not critical) %s', e)
        if not usb:
            self._set_state(constants.HW_STATE_NOT_DETECTED)
            return
        # basic readonly check

        # path to USB if DUT is sshable
        logging.info('Starting verification of USB drive...')
        dut_usb = None
        if self.host_is_up():
            dut_usb = self._usb_path_on_dut()
        state = None
        try:
            if dut_usb:
                logging.info('Try run check on DUT side.')
                state = self._run_check_on_host(self._dut_host, dut_usb)
            else:
                logging.info('Try run check on ServoHost side.')
                servo = self.get_host().get_servo()
                servo_usb = servo.probe_host_usb_dev()
                state = self._run_check_on_host(self.get_host(), servo_usb)
        except Exception as e:
            if 'Timeout encountered:' in str(e):
                logging.info('Timeout during running action')
                metrics.Counter(
                    'chromeos/autotest/audit/servo/usb/timeout'
                    ).increment(fields={'host': self._dut_host.hostname})
            else:
                # badblocks generate errors when device not reachable or
                # cannot read system information to execute process
                state = constants.HW_STATE_NEED_REPLACEMENT
            logging.debug(str(e))

        self._set_state(state)
        logging.info('Finished verification of USB drive.')

        self._install_stable_image()

    def _usb_path_on_dut(self):
        """Return path to the USB detected on DUT side."""
        servo = self.get_host().get_servo()
        servo.switch_usbkey('dut')
        result = self._dut_host.run('ls /dev/sd[a-z]')
        for path in result.stdout.splitlines():
            cmd = ('. /usr/share/misc/chromeos-common.sh; get_device_type %s' %
                   path)
            check_run = self._dut_host.run(cmd, timeout=30, ignore_status=True)
            if check_run.stdout.strip() != 'USB':
                continue
            if self._quick_check_if_device_responsive(self._dut_host, path):
                logging.info('USB drive detected on DUT side as %s', path)
                return path
        return None

    def _quick_check_if_device_responsive(self, host, usb_path):
        """Verify that device """
        validate_cmd = 'fdisk -l %s' % usb_path
        try:
            resp = host.run(validate_cmd, ignore_status=True, timeout=30)
            if resp.exit_status == 0:
                return True
            logging.error('USB %s is not detected by fdisk!', usb_path)
        except error.AutoservRunError as e:
            if 'Timeout encountered' in str(e):
                logging.warning('Timeout encountered during fdisk run.')
            else:
                logging.error('(Not critical) fdisk check fail for %s; %s',
                              usb_path, str(e))
        return False

    def _run_check_on_host(self, host, usb):
        """Run badblocks on the provided host.

        @params host:   Host where USB drive mounted
        @params usb:    Path to USB drive. (e.g. /dev/sda)
        """
        command = 'badblocks -w -e 5 -b 4096 -t random %s' % usb
        logging.info('Running command: %s', command)
        # The response is the list of bad block on USB.
        # Extended time for 2 hour to run USB verification.
        # TODO (otabek@) (b:153661014#comment2) bring F3 to run
        # check faster if badblocks cannot finish in 2 hours.
        result = host.run(command, timeout=7200).stdout.strip()
        logging.info("Check result: '%s'", result)
        if result:
            # So has result is Bad and empty is Good.
            return constants.HW_STATE_NEED_REPLACEMENT
        return constants.HW_STATE_NORMAL

    def _install_stable_image(self):
        """Install stable image to the USB drive."""
        # install fresh image to the USB because badblocks formats it
        # https://crbug.com/1091406
        try:
            logging.debug('Started to install test image to USB-drive')
            _, image_path = self._dut_host.stage_image_for_servo()
            self.get_host().get_servo().image_to_servo_usb(image_path,
                                                           power_off_dut=False)
            logging.debug('Finished installing test image to USB-drive')
        except:
            # ignore any error which happined during install image
            # it not relative to the main goal
            logging.info('Fail to install test image to USB-drive')

    def _set_state(self, state):
        if state:
            self._set_host_info_state(constants.SERVO_USB_STATE_PREFIX, state)


class VerifyServoFw(base._BaseServoVerifier):
    """Force update Servo firmware if it not up-to-date.

    This is rarely case when servo firmware was not updated by labstation
    when servod started. This should ensure that the servo_v4 and
    servo_micro is up-to-date.
    """
    def _verify(self):
        if not self.servo_host_is_up():
            logging.info('Servo host is down; Skipping the verification')
            return
        servo_updater.update_servo_firmware(
            self.get_host(),
            force_update=True)


class VerifyRPMConfig(base._BaseDUTVerifier):
    """Check RPM config of the setup.

    This check run against RPM configs settings.
    """

    def _verify(self):
        if not self.host_is_up():
            logging.info('Host is down; Skipping the verification')
            return
        rpm_validator.verify_unsafe(self.get_host())


class FlashServoKeyboardMapVerifier(base._BaseDUTVerifier):
    """Flash the keyboard map on servo."""

    def _verify(self):
        if not self.host_is_up():
            raise base.AuditError('Host is down')
        if not self.servo_is_up():
            raise base.AuditError('Servo not initialized')

        host = self.get_host()
        flasher = servo_keyboard_flasher.ServoKeyboardMapFlasher()
        if flasher.is_image_supported(host):
            flasher.update(host)


class VerifyDUTMacAddress(base._BaseDUTVerifier):
    """Verify and update cached NIC mac address on servo.

    Servo_v4 plugged to the DUT and providing NIC for that. We caching mac
    address on servod side to better debugging.
    """

    def _verify(self):
        if not self.host_is_up():
            raise base.AuditError('Host is down.')
        if not self.servo_is_up():
            raise base.AuditError('Servo host is down.')

        helper = mac_address_helper.MacAddressHelper()
        helper.update_if_needed(self.get_host())
