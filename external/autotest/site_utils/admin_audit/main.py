#!/usr/bin/env python3
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.


"""Tool to audit a DUT in the lab."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse
import logging
import logging.config
import os
import sys
import socket
import errno

import common
from autotest_lib.client.common_lib import autotest_enum
from autotest_lib.client.common_lib import logging_manager
from autotest_lib.server import server_logging_config
from autotest_lib.server.hosts import factory
from autotest_lib.server.hosts import servo_host

import verifiers

RETURN_CODES = autotest_enum.AutotestEnum(
        'OK',
        'VERIFY_FAILURE',
        'OTHER_FAILURES'
)

ACTION_VERIFY_DUT_STORAGE = 'verify-dut-storage'
ACTION_VERIFY_SERVO_USB = 'verify-servo-usb-drive'
ACTION_VERIFY_SERVO_FW = 'verify-servo-fw'
ACTION_FLASH_SERVO_KEYBOARD_MAP = 'flash-servo-keyboard-map'
ACTION_VERIFY_DUT_MACADDR = 'verify-dut-macaddr'
ACTION_VERIFY_RPM_CONFIG = 'verify-rpm-config'

_LOG_FILE = 'audit.log'
_SERVO_UART_LOGS = 'servo_uart'

VERIFIER_MAP = {
        ACTION_VERIFY_DUT_STORAGE: verifiers.VerifyDutStorage,
        ACTION_VERIFY_SERVO_USB: verifiers.VerifyServoUsb,
        ACTION_VERIFY_SERVO_FW: verifiers.VerifyServoFw,
        ACTION_FLASH_SERVO_KEYBOARD_MAP:
        verifiers.FlashServoKeyboardMapVerifier,
        ACTION_VERIFY_DUT_MACADDR: verifiers.VerifyDUTMacAddress,
        ACTION_VERIFY_RPM_CONFIG: verifiers.VerifyRPMConfig,
}

# Actions required Servod service
ACTIONS_REQUIRED_SERVOD = set([
        ACTION_VERIFY_DUT_STORAGE,
        ACTION_VERIFY_SERVO_USB,
        ACTION_FLASH_SERVO_KEYBOARD_MAP,
        ACTION_VERIFY_DUT_MACADDR,
])

# Actions required ServoHost without Servod process
ACTIONS_REQUIRED_SERVO_HOST = set([
    ACTION_VERIFY_SERVO_FW,
])

class DutAuditError(Exception):
    """Generic error raised during DUT audit."""


def main():
    """Tool to audit a DUT."""
    opts = _parse_args()

    # Create logging setting
    logging_manager.configure_logging(
        server_logging_config.ServerLoggingConfig(),
        results_dir=opts.results_dir)

    logging.debug('autoserv is running in drone %s.', socket.gethostname())
    logging.debug('audit environment: %r', os.environ)
    logging.debug('audit command was: %s', ' '.join(sys.argv))
    logging.debug('audit parsed options: %s', opts)

    # Initialize ServoHost without running Servod process.
    need_servo_host = bool(set(opts.actions) & ACTIONS_REQUIRED_SERVO_HOST)
    # Initialize ServoHost with running Servod process.
    need_servod = bool(set(opts.actions) & ACTIONS_REQUIRED_SERVOD)

    # Create folder for servo uart logs.
    servo_uart_logs_dir = None
    if need_servod:
        servo_uart_logs_dir = _create_servo_uart_path(opts.results_dir)

    try:
        host_object = factory.create_target_host(
                opts.hostname,
                host_info_path=opts.host_info_file,
                try_lab_servo=need_servod,
                try_servo_repair=need_servod,
                try_servo_recovery=need_servod,
                servo_uart_logs_dir=servo_uart_logs_dir)
    except Exception as err:
        logging.error("fail to create host: %s", err)
        return RETURN_CODES.OTHER_FAILURES

    with host_object as host:
        if need_servo_host and not need_servod:
            try:
                host.set_servo_host(servo_host.ServoHost(
                    **servo_host.get_servo_args_for_host(host)
                ))
            except Exception as err:
                logging.error("fail to init servo host: %s", err)
                return RETURN_CODES.OTHER_FAILURES
        for action in opts.actions:
            if opts.dry_run:
                logging.info('DRY RUN: Would have run actions %s', action)
                return

            response = _verify(action, host, opts.results_dir)
            if response:
                return response

    return RETURN_CODES.OK


def _verify(action, host, resultdir):
    """Run verifier for the action with targeted host.

    @param action: The action requested to run the verifier.
    @param host: The host presentation of the DUT.
    """
    try:
        _log("START", action)
        verifier = VERIFIER_MAP[action]
        if verifier:
            v = verifier(host)
            v.set_result_dir(resultdir)
            v.verify()
        else:
            logging.info('Verifier is not specified')
        _log("END_GOOD", action)
    except Exception as err:
        _log("END_FAIL", action, err)
        return RETURN_CODES.VERIFY_FAILURE


def _log(status, action, err=None):
    if err:
        message = '%s:%s; %s' % (action, status, str(err))
    else:
        message = '%s:%s' % (action, status)
    logging.info(message)


def _create_servo_uart_path(results_dir):
    servo_uart_logs = os.path.join(results_dir, _SERVO_UART_LOGS)
    try:
        if not os.path.exists(servo_uart_logs):
            os.makedirs(servo_uart_logs)
    except OSError as e:
        logging.debug(
                '(not critical) Fail to create dir for servo logs;'
                ' %s', e)
        if not (e.errno == errno.EEXIST):
            servo_uart_logs = None
    return servo_uart_logs


def _parse_args():
    parser = argparse.ArgumentParser(description='Audit DUT in a lab.')

    parser.add_argument(
            'actions',
            nargs='+',
            choices=list(VERIFIER_MAP),
            help='DUT audit actions to execute.',
    )
    parser.add_argument(
            '--dry-run',
            action='store_true',
            default=False,
            help='Run in dry-run mode. No changes will be made to the DUT.',
    )
    parser.add_argument(
            '--results-dir',
            required=True,
            help='Directory to drop logs and output artifacts in.',
    )

    parser.add_argument(
            '--hostname',
            required=True,
            help='Hostname of the DUT to audit.',
    )
    parser.add_argument(
            '--host-info-file',
            required=True,
            help=('Full path to HostInfo file.'
                  ' DUT inventory information is read from the HostInfo file.'
                  ),
    )

    return parser.parse_args()


if __name__ == '__main__':
    sys.exit(main())
