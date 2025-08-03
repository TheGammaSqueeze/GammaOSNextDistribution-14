#!/usr/bin/python3
# Copyright (c) 2012 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import division
from __future__ import print_function

import argparse
import logging
import sys
import six.moves.xmlrpc_client

import common

from autotest_lib.client.common_lib.cros import retry
from autotest_lib.site_utils.rpm_control_system import rpm_constants

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    from autotest_lib.client.bin.utils import metrics_mock as metrics


class RemotePowerException(Exception):
    """This is raised when we fail to set the state of the device's outlet."""
    pass


def set_power(host,
              new_state,
              timeout_mins=rpm_constants.RPM_CALL_TIMEOUT_MINS):
    """Sends the power state change request to the RPM Infrastructure.

    @param host: A CrosHost or ServoHost instance.
    @param new_state: State we want to set the power outlet to.
    """
    # servo V3 is handled differently from the rest.
    # The best heuristic we have to determine servo V3 is the hostname.
    if host.hostname.endswith('servo'):
        args_tuple = (host.hostname, new_state)
    else:
        info = host.host_info_store.get()
        try:
            args_tuple = (
                    host.hostname,
                    info.attributes[rpm_constants.POWERUNIT_HOSTNAME_KEY],
                    info.attributes[rpm_constants.POWERUNIT_OUTLET_KEY],
                    info.attributes.get(rpm_constants.HYDRA_HOSTNAME_KEY),
                    new_state)
        except KeyError as e:
            logging.warning('Powerunit information not found. Missing:'
                            ' %s in host_info_store.', e)
            raise RemotePowerException('Remote power control is not applicable'
                                       ' for %s, it could be either RPM is not'
                                       ' supported on the rack or powerunit'
                                       ' attributes is not configured in'
                                       ' inventory.' % host.hostname)
    _set_power(args_tuple, timeout_mins)


def _set_power(args_tuple, timeout_mins=rpm_constants.RPM_CALL_TIMEOUT_MINS):
    """Sends the power state change request to the RPM Infrastructure.

    @param args_tuple: A args tuple for rpc call. See example below:
        (hostname, powerunit_hostname, outlet, hydra_hostname, new_state)
    """
    client = six.moves.xmlrpc_client.ServerProxy(
            rpm_constants.RPM_FRONTEND_URI, verbose=False, allow_none=True)
    timeout = None
    result = None
    endpoint = (client.set_power_via_poe if len(args_tuple) == 2
                else client.set_power_via_rpm)
    try:
        timeout, result = retry.timeout(endpoint,
                                        args=args_tuple,
                                        timeout_sec=timeout_mins * 60,
                                        default_result=False)
    except Exception as e:
        logging.exception(e)
        raise RemotePowerException('Client call exception (%s): %s' %
                                   (rpm_constants.RPM_FRONTEND_URI, e))
    if timeout:
        raise RemotePowerException(
                'Call to RPM Infrastructure timed out (%s).' %
                rpm_constants.RPM_FRONTEND_URI)
    if not result:
        error_msg = ('Failed to change outlet status for host: %s to '
                     'state: %s.' % (args_tuple[0], args_tuple[-1]))
        logging.error(error_msg)
        if len(args_tuple) > 2:
            # Collect failure metrics if we set power via rpm.
            _send_rpm_failure_metrics(args_tuple[0], args_tuple[1],
                                      args_tuple[2])
        raise RemotePowerException(error_msg)


def _send_rpm_failure_metrics(hostname, rpm_host, outlet):
    metrics_fields = {
            'hostname': hostname,
            'rpm_host': rpm_host,
            'outlet': outlet
    }
    metrics.Counter('chromeos/autotest/rpm/rpm_failure2').increment(
            fields=metrics_fields)


def parse_options():
    """Parse the user supplied options."""
    parser = argparse.ArgumentParser()
    parser.add_argument('-m', '--machine', dest='machine', required=True,
                        help='Machine hostname to change outlet state.')
    parser.add_argument('-s', '--state', dest='state', required=True,
                        choices=['ON', 'OFF', 'CYCLE'],
                        help='Power state to set outlet: ON, OFF, CYCLE')
    parser.add_argument('-p', '--powerunit_hostname', dest='p_hostname',
                        help='Powerunit hostname of the host.')
    parser.add_argument('-o', '--outlet', dest='outlet',
                        help='Outlet of the host.')
    parser.add_argument('-y', '--hydra_hostname', dest='hydra', default='',
                        help='Hydra hostname of the host.')
    parser.add_argument('-d', '--disable_emails', dest='disable_emails',
                        help='Hours to suspend RPM email notifications.')
    parser.add_argument('-e', '--enable_emails', dest='enable_emails',
                        action='store_true',
                        help='Resume RPM email notifications.')
    return parser.parse_args()


def main():
    """Entry point for rpm_client script."""
    options = parse_options()
    if options.machine.endswith('servo'):
        args_tuple = (options.machine, options.state)
    elif not options.p_hostname or not options.outlet:
        print("Powerunit hostname and outlet info are required for DUT.")
        return
    else:
        args_tuple = (options.machine, options.p_hostname, options.outlet,
                      options.hydra, options.state)
    _set_power(args_tuple)

    if options.disable_emails is not None:
        client = six.moves.xmlrpc_client.ServerProxy(
                rpm_constants.RPM_FRONTEND_URI, verbose=False)
        client.suspend_emails(options.disable_emails)
    if options.enable_emails:
        client = six.moves.xmlrpc_client.ServerProxy(
                rpm_constants.RPM_FRONTEND_URI, verbose=False)
        client.resume_emails()


if __name__ == "__main__":
    sys.exit(main())
