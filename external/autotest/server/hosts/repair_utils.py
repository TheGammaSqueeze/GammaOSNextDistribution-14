# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import logging
import socket

import common
from autotest_lib.client.common_lib import hosts
from autotest_lib.server import utils
from autotest_lib.server.hosts import servo_constants
from autotest_lib.server.hosts import cros_constants

from autotest_lib.utils.frozen_chromite.lib import timeout_util


def require_servo(host, ignore_state=False):
    """Require a DUT to have a working servo for a repair action.

    @param host             Host object that require servo.
    @param ignore_state     Ignore servo state as long as the we still have
                            servo connection. Some non-critical verifier
                            failures may not cause servo connection been
                            disconnected.
    """
    servo_initialized = host.servo is not None
    servo_working = (host.get_servo_state() ==
                     servo_constants.SERVO_STATE_WORKING or ignore_state)

    if not (servo_initialized and servo_working):
        raise hosts.AutoservRepairError(
                '%s has no working servo.' % host.hostname, 'no_working_servo')
    logging.info('Servo dependence is available for the RepairAction/test.')


class SshVerifier(hosts.Verifier):
    """
    Verifier to test a host's accessibility via `ssh`.

    This verifier checks whether a given host is reachable over `ssh`.
    In the event of failure, it distinguishes one of three distinct
    conditions:
      * The host can't be found with a DNS lookup.
      * The host doesn't answer to ping.
      * The host answers to ping, but not to ssh.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if host.is_up():
            return
        msg = 'No answer to ssh from %s'
        try:
            socket.gethostbyname(host.hostname)
        except Exception as e:
            logging.exception('DNS lookup failure')
            msg = 'Unable to look up %%s in DNS: %s' % e
        else:
            if utils.ping(host.hostname, tries=1, deadline=1) != 0:
                msg = 'No answer to ping from %s'
        raise hosts.AutoservVerifyError(msg % host.hostname)

    @property
    def description(self):
        return 'host is available via ssh'


class PingVerifier(hosts.Verifier):
    """
    Verifier to test a host's accessibility via `ping`.

    This verifier checks whether a given host is reachable over `ping`.
    The device is pingable as soon as booted to level when network driver
    can respond.
    In the event of failure, it distinguishes one of distinct conditions:
      * The host can't be found with a DNS lookup.
      * The host doesn't booted with network drivers.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if host.is_up_fast(count=10):
            return
        msg = 'No answer to ping to %s'
        ip_address = None
        try:
            ip_address = socket.gethostbyname(host.hostname)
        except Exception as e:
            logging.exception('DNS lookup failure')
            msg = 'Unable to look up %s in DNS: %s' % (host.hostname, str(e))
            raise hosts.AutoservVerifyError(msg)
        if not ip_address:
            msg = 'Hostname: %s not present in DNS' % host.hostname
        else:
            msg = 'Hostname: %s not pingable' % host.hostname
        raise hosts.AutoservVerifyError(msg)

    @property
    def description(self):
        return 'host is available via ping'


class LegacyHostVerifier(hosts.Verifier):
    """
    Ask a Host instance to perform its own verification.

    This class exists as a temporary legacy during refactoring to
    provide access to code that hasn't yet been rewritten to use the new
    repair and verify framework.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        host.verify_software()
        host.verify_hardware()

    @property
    def description(self):
        return 'Legacy host verification checks'


class RebootRepair(hosts.RepairAction):
    """Repair a target device by rebooting it."""

    # Timeout of this action should defined in child class.
    def repair(self, host):
        host.reboot()

    @property
    def description(self):
        return 'Reboot the host'


class RPMCycleRepair(hosts.RepairAction):
    """
    Cycle AC power using the RPM infrastructure.

    This is meant to catch two distinct kinds of failure:
      * If the target has no battery (that is, a chromebox), power
        cycling it may force it back on.
      * If the target has a batter that is discharging or even fully
        drained, power cycling will leave power on, enabling other
        repair procedures.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        if not host.has_power():
            raise hosts.AutoservRepairError(
                    '%s has no RPM connection.' % host.hostname,
                    'no_working_rpm')
        host.power_cycle()


    @property
    def description(self):
        return 'Power cycle the host with RPM'
