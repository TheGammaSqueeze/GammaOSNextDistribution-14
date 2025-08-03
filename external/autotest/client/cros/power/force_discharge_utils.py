# Lint as: python2, python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Helper class for power autotests that force DUT to discharge with EC."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.cros import ec
from autotest_lib.client.cros.power import power_utils
from six.moves import range

_FORCE_DISCHARGE_SETTINGS = ['false', 'true', 'optional']


def _parse(force_discharge):
    """
    Parse and return force discharge setting.

    @param force_discharge: string of whether to tell ec to discharge battery
            even when the charger is plugged in. 'false' means no forcing
            discharge; 'true' means forcing discharge and raising an error when
            it fails; 'optional' means forcing discharge when possible but not
            raising an error when it fails, which is more friendly to devices
            without a battery.

    @return: string representing valid force discharge setting.

    @raise error.TestError: for invalid force discharge setting.

    """
    setting = str(force_discharge).lower()
    if setting not in _FORCE_DISCHARGE_SETTINGS:
        raise error.TestError(
                'Force discharge setting \'%s\' need to be one of %s.' %
                (str(force_discharge), _FORCE_DISCHARGE_SETTINGS))
    return setting


def _wait_for_battery_discharge(status):
    """
    Polling every 100ms for 2 seconds until battery is discharging. This
    normally would take about 350ms.

    @param status: DUT power status object.

    @return: boolean indicating force discharge success.
    """
    for _ in range(20):
        status.refresh()
        if status.battery_discharging():
            return True
        time.sleep(0.1)
    return False


def process(force_discharge, status):
    """
    Perform force discharge steps.

    @param force_discharge: string of whether to tell ec to discharge battery
            even when the charger is plugged in. 'false' means no forcing
            discharge; 'true' means forcing discharge and raising an error when
            it fails; 'optional' means forcing discharge when possible but not
            raising an error when it fails, which is more friendly to devices
            without a battery.
    @param status: DUT power status object.

    @return: bool to indicate whether force discharge steps are successful. Note
            that DUT cannot force discharge if DUT is not connected to AC.

    @raise error.TestError: for invalid force discharge setting.
    @raise error.TestNAError: when force_discharge is 'true' and the DUT is
            incapable of forcing discharge.
    @raise error.TestError: when force_discharge is 'true' and the DUT command
            to force discharge fails.
    """
    force_discharge = _parse(force_discharge)

    if force_discharge == 'true':
        if not status.battery:
            raise error.TestNAError('DUT does not have battery. '
                                    'Could not force discharge.')
        if not ec.has_cros_ec():
            raise error.TestNAError('DUT does not have CrOS EC. '
                                    'Could not force discharge.')
        if not power_utils.charge_control_by_ectool(False):
            raise error.TestError('Could not run battery force discharge.')
        if not _wait_for_battery_discharge(status):
            logging.warning('Battery does not report discharging state.')
        return True
    elif force_discharge == 'optional':
        if not status.battery:
            logging.warning('DUT does not have battery. '
                            'Do not force discharge.')
            return False
        if not ec.has_cros_ec():
            logging.warning('DUT does not have CrOS EC. '
                            'Do not force discharge.')
            return False
        if not power_utils.charge_control_by_ectool(False):
            logging.warning('Could not run battery force discharge. '
                            'Do not force discharge.')
            return False
        if not _wait_for_battery_discharge(status):
            logging.warning('Battery does not report discharging state.')
        return True
    elif force_discharge == 'false':
        return False


def restore(force_discharge_success):
    """
    Set DUT back to charging.

    @param force_discharge_success: if DUT previously forced discharge
            successfully, set DUT back to charging.
    """
    if force_discharge_success:
        if not power_utils.charge_control_by_ectool(True):
            logging.warning('Can not restore from force discharge.')
