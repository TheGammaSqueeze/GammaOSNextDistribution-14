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
import six
import time

import common
from autotest_lib.client.common_lib import error
from autotest_lib.site_utils.rpm_control_system import rpm_client
from autotest_lib.site_utils.admin_audit import constants


def _is_rpm_config_present(host):
    """Check if RPM config data present.

    @param host: any host with has host_info_store field

    @raises: error.AutoservError if config present partially.
    """
    host_info = host.host_info_store.get()
    powerunit_hostname = host_info.attributes.get('powerunit_hostname')
    powerunit_outlet = host_info.attributes.get('powerunit_outlet')

    powerunit_hasinfo = (bool(powerunit_hostname), bool(powerunit_outlet))

    if powerunit_hasinfo == (True, True):
        return True
    elif powerunit_hasinfo == (False, False):
        set_rpm_state(host, constants.RPM_STATE_MISSING_CONFIG)
        return False
    else:
        set_rpm_state(host, constants.RPM_STATE_WRONG_CONFIG)
        msg = "inconsistent power info: %s %s" % (powerunit_hostname,
                                                  powerunit_outlet)
        logging.error(msg)
        raise error.AutoservError(msg)


def _set_power_off(host, quite=False):
    try:
        rpm_client.set_power(host, "OFF")
    except Exception as e:
        # We do not want to leave RPM outlets in off state
        _set_power_on(host, quite=True)
        if not quite:
            logging.debug('Fail to set state OFF for RPM; %s', str(e))
            six.reraise(e.__class__, e)


def _set_power_on(host, quite=False):
    try:
        rpm_client.set_power(host, "ON")
    except Exception as e:
        # We do not want to leave RPM outlets in off state
        if not quite:
            logging.debug('Fail to set state ON for RPM; %s', str(e))
            six.reraise(e.__class__, e)


def _check_rpm_power_delivery_with_battery(host):
    """Verify RPM for device which has battery.

    Verification based on check if device can charging.
    @param host: any host with has host_info_store field
    """

    def validate_power_state(is_on, wait_time):
        deadline = time.time() + wait_time
        while time.time() < deadline:
            if not host.is_up():
                # DUT is not available by ssh will try again.
                continue
            power_info = host.get_power_supply_info()
            try:
                is_online = power_info['Line Power']['online'] == 'yes'
                if is_on == is_online:
                    break
            except KeyError:
                logging.debug('(Not critical) Fail check online power')
            time.sleep(5)
        else:
            expected_state = 'ON' if is_on else 'OFF'
            msg = "%s didn't enter %s state in %s seconds" % (
                    host.hostname,
                    expected_state,
                    wait_time,
            )
            raise Exception(msg)

    logging.info("Cutting down wall power for %s...", host.hostname)
    _set_power_off(host)
    validate_power_state(False, host.WAIT_DOWN_REBOOT_TIMEOUT)

    logging.info("Re-enable wall power for %s...", host.hostname)
    _set_power_on(host)
    validate_power_state(True, host.BOOT_TIMEOUT)
    logging.info("RPM Check Successful")


def _check_rpm_power_delivery_without_battery(host):
    """Verify RPM for device which has battery.

    Verification based on check if device online or offline.
    @param host: any host with has host_info_store field
    """
    logging.info("Cutting down wall power for %s...", host.hostname)
    _set_power_off(host)
    if not host.wait_down(timeout=host.WAIT_DOWN_REBOOT_TIMEOUT):
        msg = "%s didn't enter OFF state in %s seconds" % (
                host.hostname,
                host.WAIT_DOWN_REBOOT_TIMEOUT,
        )
        raise Exception(msg)

    logging.info("Re-enable wall power for %s...", host.hostname)
    _set_power_on(host)
    if not host.wait_up(timeout=host.BOOT_TIMEOUT):
        msg = "%s didn't enter ON state in %s seconds" % (
                host.hostname,
                host.BOOT_TIMEOUT,
        )
        raise Exception(msg)


def verify_unsafe(host):
    """Verify that we can power cycle a host with its RPM information.
    Any host without RPM information will be safely skipped.

    This procedure is intended to catch inaccurate RPM info when the
    host is deployed.

    @param host: any host with has host_info_store field

    @raises: error.AutoservError if config present partially or wrong.
             error.AutoservError if device does not specify power label.
             error.AutoservError if device has mismatch between host-info
                                and device.
    """
    logging.info("Start RPM check for: %s", host.hostname)
    if not hasattr(host, 'host_info_store'):
        logging.info('Host:%s does not have host_info_store attribute',
                     host.hostname)
        return
    if not _is_rpm_config_present(host):
        logging.info("RPM config is not present. Skipping check.")
        return

    # In the lab we need trust that host-info will be correct.
    power_info = host.host_info_store.get().get_label_value('power')
    if not power_info:
        raise error.AutoservError(
                'Could not detect power-info in host-info. The information'
                ' has to be provided by manufacture configs. Please file'
                ' the bug agains Fleet Inventory')
    has_battery = power_info == 'battery'

    # Verify host-info against manufactory configs
    try:
        info = host.get_power_supply_info()
    except Exception as e:
        logging.debug('(Not critical) %s', e)
        raise error.AutoservError('Could not detect power supply info')
    if 'Battery' in info:
        if not has_battery:
            raise error.AutoservError(
                    'Unexpected detected battery on the device')
    elif has_battery:
        raise error.AutoservError(
                'Battery is not detected on the device. But expected')
    # Try to use RPM config to confirm tha information is correct.
    try:
        if has_battery:
            _check_rpm_power_delivery_with_battery(host)
        else:
            _check_rpm_power_delivery_without_battery(host)
    except Exception as e:
        set_rpm_state(host, constants.RPM_STATE_WRONG_CONFIG)
        logging.debug('(Not critical) %s', e)
        msg = getattr(e, 'message') if hasattr(e, 'message') else str(e)
        logging.info('RPM check fails! %s', msg)
        six.reraise(error.AutoservError, e)
    else:
        set_rpm_state(host, constants.RPM_STATE_WORKING)
        logging.info("The host passed RPM config check!")


def set_rpm_state(host, new_state):
    """Set RPM state info labels to dut host_info.

    @param host:        Any host with has host_info_store field
    @param new_state:   New RPM state. Possible values are
                        listed in RPM_STATES_SUPPORTED.
    """
    if not new_state:
        logging.debug('RPM state is not specified.')
        return
    if new_state not in constants.RPM_STATES_SUPPORTED:
        logging.debug('Not supported Incorrect RPM state.')
        return
    host_info = host.host_info_store.get()
    prefix = constants.RPM_STATE_LABEL_PREFIX
    old_state = host_info.get_label_value(prefix)
    if old_state == new_state:
        # do not need update
        logging.debug('RPM state not changed. Skiping update')
        return
    host_info.set_version_label(prefix, new_state)
    host.host_info_store.commit(host_info)
    logging.info('Rpm state updated to %s (previous: %s)', new_state,
                 old_state)
