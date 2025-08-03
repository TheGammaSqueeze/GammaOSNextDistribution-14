#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Functional to validate RPM configs in the lab."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import os
import logging

import common
from autotest_lib.site_utils.admin_audit import constants


class BatteryValidator(object):
    """Battery validator provides capacity verification of battery on the host.

    The state detection and set state as:
    - NORMAL - battery capacity >= 70%
    - ACCEPTABLE - battery capacity >= 40%
    - NEED_REPLACEMENT - battery capacity < 40%
    - UNKNOWN - logic cannot read data to specify the state
    - NOT_DETECTED - battery is not present on the host
    """
    # Battery capacity levels
    BATTER_NORMAL_LEVEL = 70
    BATTER_ACCEPTABLE_LEVEL = 40

    # Attempts to try read battery data
    READ_DATA_RETRY_COUNT = 3

    def __init__(self, host):
        """Initialize the battery validator.

        @params host CrosHost instance.
        """
        self._host = host
        self._battery_path = None
        self.charge_full = 0
        self.charge_full_design = 0

    def _read_battery_path(self):
        """Detect path to battery properties on the host."""
        self._battery_path = None
        info = self._host.get_power_supply_info()
        if 'Battery' not in info:
            logging.debug('Battery is not presented but expected!'
                          ' In some cases it possible.')
            return None
        self._battery_path = info['Battery']['path']
        logging.info('Battery path: %s', self._battery_path)
        return self._battery_path

    def is_battery_expected(self):
        """Verify if battery expected on the host based on host info."""
        host_info = self._host.host_info_store.get()
        return host_info.get_label_value('power') == 'battery'

    def _read_data_from_host(self):
        """Read data from the host."""

        def read_val(file_name, field_type):
            """Read a value from file."""
            try:
                path = os.path.join(self._battery_path, file_name)
                out = self._host.run('cat %s' % path,
                                     ignore_status=True).stdout.strip()
                return field_type(out)
            except:
                return field_type(0)

        self.charge_full = read_val('charge_full', float)
        self.charge_full_design = read_val('charge_full_design', float)
        cycle_count = read_val('cycle_count', int)
        logging.debug('Battery cycle_count: %d', cycle_count)

    def _validate_by_host(self):
        """Validate battery by reading data from the host."""
        logging.debug('Try to validate from host side.')
        if self._host.is_up():
            for _ in range(self.READ_DATA_RETRY_COUNT):
                try:
                    self._read_battery_path()
                    if not self._battery_path:
                        logging.info('Battery is not present/found on host')
                        return self._update_host_info(
                                constants.HW_STATE_NOT_DETECTED)
                    self._read_data_from_host()
                    return self._update_battery_state()
                except Exception as e:
                    logging.debug('(Not critical) %s', e)
        return None

    def _validate_by_servo(self):
        """Validate battery by servo access."""
        servo = self._host.servo
        logging.debug('Try to validate from servo side.')
        if servo:
            for _ in range(self.READ_DATA_RETRY_COUNT):
                try:
                    if not servo.has_control('battery_full_charge_mah'):
                        break
                    self.charge_full = servo.get('battery_full_charge_mah')
                    self.charge_full_design = servo.get(
                            'battery_full_design_mah')
                    return self._update_battery_state()
                except Exception as e:
                    logging.debug('(Not critical) %s', e)
        return None

    def validate(self):
        """Validate battery and update state.

        Try to validate from host if device is sshable if not then try
        read battery info by servo.
        """
        logging.info('Starting battery validation.')
        state = None
        if not self.is_battery_expected():
            state = self._update_host_info(constants.HW_STATE_NOT_DETECTED)
        if not state:
            state = self._validate_by_host()
        if not state:
            state = self._validate_by_servo()
        if not state:
            state = self._update_host_info(constants.HW_STATE_UNKNOWN)
        return state

    def _update_battery_state(self):
        """Update battery state based on batter charging capacity

        The logic will update state based on:
            if capacity >= 70% then NORMAL
            if capacity >= 40% then ACCEPTABLE
            if capacity  < 40% then NEED_REPLACEMENT
        """
        if self.charge_full == 0:
            logging.debug('charge_full is 0. Skip update battery_state!')
            return
        if self.charge_full_design == 0:
            logging.debug('charge_full_design is 0.'
                          ' Skip update battery_state!')
            return
        capacity = (100.0 * self.charge_full / self.charge_full_design)
        logging.debug('Battery capacity: %d', capacity)

        if capacity >= self.BATTER_NORMAL_LEVEL:
            return self._update_host_info(constants.HW_STATE_NORMAL)
        if capacity >= self.BATTER_ACCEPTABLE_LEVEL:
            return self._update_host_info(constants.HW_STATE_ACCEPTABLE)
        return self._update_host_info(constants.HW_STATE_NEED_REPLACEMENT)

    def _update_host_info(self, state):
        """Update state value to the battery_state in the host_info

        @param state: new state value for the label
        """
        if self._host:
            state_prefix = constants.BATTERY_STATE_PREFIX
            host_info = self._host.host_info_store.get()
            old_state = host_info.get_label_value(state_prefix)
            host_info.set_version_label(state_prefix, state)
            logging.info('Set %s as `%s` (previous: `%s`)', state_prefix,
                         state, old_state)
            self._host.host_info_store.commit(host_info)
        return state
