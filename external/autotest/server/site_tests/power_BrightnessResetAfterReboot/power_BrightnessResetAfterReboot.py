# Lint as: python2, python3
# Copyright (c) 2014 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging

from autotest_lib.client.common_lib import error
from autotest_lib.server import autotest, test


# TODO(b/163372205): Some models before unibuild have ambient light sensors
# only on certain skus. However, has_ambient_light_sensor powerd pref has to
# be set for all skus. For these models, skip checking the existence of ambient
# light sensors against has_ambient_light_sensor powerd pref.
IGNORE_ALS_PREF_MODELS = ['caroline']

class power_BrightnessResetAfterReboot(test.test):
    """Verifies that the panel brightness level resets after device reboots.
    """
    version = 2

    def run_once(self, host, client_autotest):
        """This test verifies that the panel brightness level resets after
           device reboots.
        """
        if host.has_internal_display() is None:
            raise error.TestNAError('Device has no internal display.')
        autotest_client = autotest.Autotest(host)
        host.reboot()
        autotest_client.run_test(client_autotest,
                                 exit_without_logout=True)

        cmd = 'check_powerd_config --ambient_light_sensor'
        num_als_pref = int(host.run_output(cmd, ignore_status=True))

        cmd = 'backlight_tool --get_ambient_light_lux'
        result = host.run(cmd, ignore_status=True)
        als_exists = not result.exit_status # 0 is cmd success.

        if num_als_pref and not als_exists:
            model = host.get_platform()
            msg = ('Powerd pref indicates %d ambient light sensor(s) but device '
                   'is unable to find it (them).' % num_als_pref)
            if model in IGNORE_ALS_PREF_MODELS:
                logging.info('%s However, skip this check for model: %s.',
                             msg, model)
            else:
                raise error.TestFail(msg)

        initial_lux = -1
        if als_exists:
            initial_lux = int(result.stdout.rstrip())
            lux_domain = [initial_lux / 2, initial_lux * 2]
            brightness_range = \
                    [get_backlight(host, lux) for lux in lux_domain]
        else:
            brightness_range = [10.0, 90.0]

        initial_brightness = get_backlight(host)
        if (initial_brightness < brightness_range[0] or
                initial_brightness > brightness_range[1]):
            raise error.TestFail('Default brightness level is out of scope '
                                 '(%d%% - %d%%): %f' % (brightness_range[0],
                                                        brightness_range[1],
                                                        initial_brightness))

        brightness_min = 0.0
        if (not set_backlight(host, brightness_min) or
                get_backlight(host) != brightness_min):
            raise error.TestFail('Unable to change the brightness to minimum '
                                 '(%f%%) level.' % brightness_min)

        brightness_max = 100.0
        if (not set_backlight(host, brightness_max) or
                get_backlight(host) != brightness_max):
            raise error.TestFail('Unable to change the brightness to maximum '
                                 '(%f%%) level.' % brightness_max)

        host.reboot()
        autotest_client.run_test(client_autotest,
                                 exit_without_logout=True)
        brightness_after_reboot = get_backlight(host)
        if not als_exists and initial_brightness == brightness_after_reboot:
            return
        # If there is an ambient light sensor, allow a small change in internal
        # display brightness, in case that the ambient light changes slightly.
        if als_exists:
            cushion = 0.2
            lux_domain_after_reboot = [(1.0 - cushion) * initial_lux,
                                       (1.0 + cushion) * initial_lux]
            brightness_range_after_reboot = [get_backlight(host, lux) for lux
                                             in lux_domain_after_reboot]
            if (brightness_range_after_reboot[0] <=
                    brightness_after_reboot <=
                    brightness_range_after_reboot[1]):
                return

        raise error.TestFail('Unable to reset internal display brightness back '
                             'to default after reboot.\n'
                             'Previous boot default brightness: %f\n'
                             'Current boot default brightness: %f' %
                             (initial_brightness, brightness_after_reboot))


def set_backlight(host, percentage):
    """Executes backlight_tool to set internal display backlight.
       @param host: host object representing the DUT.
       @param percentage: linear percentage to set internal display
                          backlight to.
    """
    cmd = 'backlight_tool --set_brightness_percent=%f' % percentage
    try:
        exit_status = host.run(cmd).exit_status
    except error.CmdError:
        raise error.TestFail(cmd)
    return not exit_status # 0 is cmd success.

def get_backlight(host, lux=-1):
    """Executes backlight_tool to get internal display backlight.
       @param host: host object representing the DUT.
    """
    cmd = 'backlight_tool --get_brightness_percent'
    if lux >= 0:
        cmd = '%s --lux=%d' % (cmd, lux)
    try:
        result = host.run_output(cmd)
    except error.CmdError:
        raise error.TestFail(cmd)
    return float(result)
