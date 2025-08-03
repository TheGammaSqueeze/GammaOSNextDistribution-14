# Lint as: python2, python3
# Copyright 2018 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Helper class for managing charging the DUT with Servo v4."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
from six.moves import range
import time

from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib.cros import retry

# Base delay time in seconds for Servo role change and PD negotiation.
_DELAY_SEC = 0.1
# Total delay time in minutes for Servo role change and PD negotiation.
_TIMEOUT_MIN = 0.3
# Exponential backoff for Servo role change and PD negotiation.
_BACKOFF = 2
# Number of attempts to recover Servo v4.
_RETRYS = 3
# Seconds to wait after resetting the role on a recovery attempt
# before trying to set it to the intended role again.
_RECOVERY_WAIT_SEC = 1
# Delay to wait before polling whether the role as been changed successfully.
_ROLE_SETTLING_DELAY_SEC = 1
# Timeout in minutes to attempt checking AC information over ssh.
# Ethernet connection through the v4 flickers on role change. The usb
# adapter needs to reenumerate and the DUT reconnect before information can be
# queried. This delay has proven sufficient to overcome this in the current
# implementation.
_ETH_REENUMERATE_TIMEOUT_MIN = 1


def _invert_role(role):
    """Helper to invert the role.

    @param role: role to invert

    @returns:
      'src' if |role| is 'snk'
      'snk' if |role| is 'src'
    """
    return 'src' if role == 'snk' else 'snk'

class ServoV4ChargeManager(object):
    """A helper class for managing charging the DUT with Servo v4."""

    def __init__(self, host, servo):
        """Check for correct Servo setup.

        Make sure that Servo is v4 and can manage charging. Make sure that DUT
        responds to Servo charging commands. Restore Servo v4 power role after
        confidence check.

        @param host: CrosHost object representing the DUT or None.
                     If host is None, then the is_ac_connected check on the
                     host object is skipped.
        @param servo: a proxy for servod.
        """
        super(ServoV4ChargeManager, self).__init__()
        self._host = host
        self._servo = servo
        if not self._servo.supports_built_in_pd_control():
            raise error.TestNAError('Servo setup does not support PD control. '
                                    'Check logs for details.')

        self._original_role = self._servo.get('servo_pd_role')
        if self._original_role == 'snk':
            self.start_charging()
            self.stop_charging()
        elif self._original_role == 'src':
            self.stop_charging()
            self.start_charging()
        else:
            raise error.TestNAError('Unrecognized Servo v4 power role: %s.' %
                                    self._original_role)

    # TODO(b/129882930): once both sides are stable, remove the _retry_wrapper
    # wrappers as they aren't needed anymore. The current motivation for the
    # retry loop in the autotest framework is to have a 'stable' library i.e.
    # retries but also a mechanism and and easy to remove bridge once the bug
    # is fixed, and we don't require the bandaid anymore.

    def _retry_wrapper(self, role, verify):
        """Try up to |_RETRYS| times to set the v4 to |role|.

        @param role: string 'src' or 'snk'. If 'src' connect DUT to AC power; if
                     'snk' disconnect DUT from AC power.
        @param verify: bool to verify that charging started/stopped.

        @returns: number of retries needed for success
        """
        for retry in range(_RETRYS):
            try:
                self._change_role(role, verify)
                return retry
            except error.TestError as e:
                if retry < _RETRYS - 1:
                    # Ensure this retry loop and logging isn't run on the
                    # last iteration.
                    logging.warning('Failed to set to %s %d times. %s '
                                    'Trying to cycle through %s to '
                                    'recover.', role, retry + 1, str(e),
                                    _invert_role(role))
                    # Cycle through the other state before retrying. Do not
                    # verify as this is strictly a recovery mechanism - sleep
                    # instead.
                    self._change_role(_invert_role(role), verify=False)
                    time.sleep(_RECOVERY_WAIT_SEC)
        logging.error('Giving up on %s.', role)
        raise e

    def stop_charging(self, verify=True):
        """Cut off AC power supply to DUT with Servo.

        @param verify: whether to verify that charging stopped.

        @returns: number of retries needed for success
        """
        return self._retry_wrapper('snk', verify)

    def start_charging(self, verify=True):
        """Connect AC power supply to DUT with Servo.

        @param verify: whether to verify that charging started.

        @returns: number of retries needed for success
        """
        return self._retry_wrapper('src', verify)

    def restore_original_setting(self, verify=True):
        """Restore Servo to original charging setting.

        @param verify: whether to verify that original role was restored.
        """
        self._retry_wrapper(self._original_role, verify)

    def _change_role(self, role, verify=True):
        """Change Servo PD role and check if DUT responded accordingly.

        @param role: string 'src' or 'snk'. If 'src' connect DUT to AC power; if
                     'snk' disconnect DUT from AC power.
        @param verify: bool to verify that charging started/stopped.

        @raises error.TestError: if the role did not change successfully.
        """
        self._servo.set_nocheck('servo_pd_role', role)
        # Sometimes the role reverts quickly. Add a short delay to let the new
        # role stabilize.
        time.sleep(_ROLE_SETTLING_DELAY_SEC)

        if not verify:
            return

        @retry.retry(error.TestError, timeout_min=_TIMEOUT_MIN,
                     delay_sec=_DELAY_SEC, backoff=_BACKOFF)
        def check_servo_role(role):
            """Check if servo role is as expected, if not, retry."""
            if self._servo.get('servo_pd_role') != role:
                raise error.TestError('Servo v4 failed to set its PD role to '
                                      '%s.' % role)
        check_servo_role(role)

        connected = True if role == 'src' else False

        @retry.retry(error.TestError, timeout_min=_TIMEOUT_MIN,
                     delay_sec=_DELAY_SEC, backoff=_BACKOFF)
        def check_ac_connected(connected):
            """Check if the EC believes an AC charger is connected."""
            if not self._servo.has_control('charger_connected'):
                # TODO(coconutruben): remove this check once labs have the
                # latest hdctools with the required control.
                logging.warning('Could not verify %r control as the '
                              'control is not available on servod.',
                              'charger_connected')
                return
            ec_opinion = self._servo.get('charger_connected')
            if ec_opinion != connected:
                str_lookup = {True: 'connected', False: 'disconnected'}
                msg = ('EC thinks charger is %s but it should be %s.'
                       % (str_lookup[ec_opinion],
                          str_lookup[connected]))
                raise error.TestError(msg)

        check_ac_connected(connected)

        @retry.retry(error.TestError, timeout_min=_ETH_REENUMERATE_TIMEOUT_MIN,
                     delay_sec=_DELAY_SEC, backoff=_BACKOFF)
        def check_host_ac(connected):
            """Check if DUT AC power is as expected, if not, retry."""
            if self._host.is_ac_connected() != connected:
                intent = 'connect' if connected else 'disconnect'
                raise error.TestError('DUT failed to %s AC power.'% intent)

        if self._host and self._host.is_up_fast():
            # If the DUT has been charging in S3/S5/G3, cannot verify.
            check_host_ac(connected)
