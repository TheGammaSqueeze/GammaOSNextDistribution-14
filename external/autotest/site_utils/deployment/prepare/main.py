#!/usr/bin/python3 -u
# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""Tool to (re)prepare a DUT for lab deployment."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import argparse
import errno
import logging
import logging.config
import os
import sys

import common
from autotest_lib.client.common_lib import autotest_enum
from autotest_lib.client.common_lib import logging_manager
from autotest_lib.server import afe_utils
from autotest_lib.server import server_logging_config
from autotest_lib.server.hosts import file_store
from autotest_lib.site_utils.deployment.prepare import dut as preparedut
from autotest_lib.server.hosts import factory
from autotest_lib.site_utils.admin_audit import rpm_validator


RETURN_CODES = autotest_enum.AutotestEnum(
        'OK',
        'SERVO_VERIFICATION_FAILURE',
        'STAGE_USB_FAILURE',
        'INSTALL_FIRMWARE_FAILURE',
        'INSTALL_TEST_IMAGE_FAILURE',
        'PRE_DEPLOY_VERIFICATION_FAILURE',
        'BOOT_FROM_RECOVERY_MODE_FAILURE',
        'SETUP_LABSTATION_FAILURE',
        'UPDATE_LABEL_FAILURE',
        'OTHER_FAILURES',
)

_SERVO_UART_LOGS = 'servo_uart'


class DutPreparationError(Exception):
    """Generic error raised during DUT preparation."""


def main():
    """Tool to (re)prepare a DUT for lab deployment."""
    opts = _parse_args()

    # Create logging setting
    logging_manager.configure_logging(
            server_logging_config.ServerLoggingConfig(),
            results_dir=opts.results_dir)

    try:
        host_info = _read_store(opts.host_info_file)
    except Exception as err:
        logging.error("fail to prepare: %s", err)
        return RETURN_CODES.OTHER_FAILURES

    with create_host(opts.hostname, host_info, opts.results_dir) as host:
        if opts.dry_run:
            logging.info('DRY RUN: Would have run actions %s', opts.actions)
            return

        is_labstation = (host_info.get().os == "labstation")

        if 'servo-verification' in opts.actions:
            try:
                if not is_labstation:
                    preparedut.verify_servo(host)
            except Exception as err:
                logging.error("fail to check servo: %s", err)
                return RETURN_CODES.SERVO_VERIFICATION_FAILURE

        if 'stage-usb' in opts.actions:
            try:
                repair_image = afe_utils.get_stable_cros_image_name_v2(
                        host_info.get())
                logging.info('Using repair image %s, obtained from AFE',
                             repair_image)
                preparedut.download_image_to_servo_usb(host, repair_image)
            except Exception as err:
                logging.error("fail to stage image to usb: %s", err)
                return RETURN_CODES.STAGE_USB_FAILURE

        if 'install-test-image' in opts.actions:
            try:
                preparedut.install_test_image(host)
            except Exception as err:
                logging.error("fail to install test image: %s", err)
                return RETURN_CODES.INSTALL_TEST_IMAGE_FAILURE

        if 'install-firmware' in opts.actions:
            try:
                preparedut.install_firmware(host)
            except Exception as err:
                logging.error("fail to install firmware: %s", err)
                return RETURN_CODES.INSTALL_FIRMWARE_FAILURE

        if 'verify-recovery-mode' in opts.actions:
            try:
                preparedut.verify_boot_into_rec_mode(host)
            except Exception as err:
                logging.error("fail to boot from recovery mode: %s", err)
                return RETURN_CODES.BOOT_FROM_RECOVERY_MODE_FAILURE

        # TODO (otabek): mix this step with update-label later.
        if 'setup-labstation' in opts.actions:
            try:
                preparedut.setup_hwid_and_serialnumber(host)
            except Exception as err:
                logging.error("fail to setup labstation: %s", err)
                return RETURN_CODES.SETUP_LABSTATION_FAILURE

        if 'update-label' in opts.actions:
            try:
                preparedut.setup_hwid_and_serialnumber(host)
                if not is_labstation:
                    host.labels.update_labels(host, task_name='deploy')
            except Exception as err:
                logging.error("fail to update label: %s", err)
                return RETURN_CODES.UPDATE_LABEL_FAILURE

        if 'run-pre-deploy-verification' in opts.actions:
            try:
                if is_labstation:
                    logging.info("testing RPM information on labstation")
                    preparedut.verify_labstation_RPM_config_unsafe(host)
                else:
                    preparedut.verify_servo(host)
                    preparedut.verify_battery_status(host)
                    preparedut.verify_ccd_testlab_enable(host)
                    rpm_validator.verify_unsafe(host)
            except Exception as err:
                logging.error("fail on pre-deploy verification: %s", err)
                return RETURN_CODES.PRE_DEPLOY_VERIFICATION_FAILURE

    return RETURN_CODES.OK


def _parse_args():
    parser = argparse.ArgumentParser(
            description='Prepare / validate DUT for lab deployment.')

    parser.add_argument(
            'actions',
            nargs='+',
            choices=[
                    'servo-verification', 'stage-usb', 'install-test-image',
                    'install-firmware', 'verify-recovery-mode',
                    'run-pre-deploy-verification', 'update-label',
                    'setup-labstation'
            ],
            help='DUT preparation actions to execute.',
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
            help='Hostname of the DUT to prepare.',
    )
    parser.add_argument(
            '--host-info-file',
            required=True,
            help=('Full path to HostInfo file.'
                  ' DUT inventory information is read from the HostInfo file.'
                  ),
    )

    return parser.parse_args()


def _read_store(path):
    """Read a HostInfo from a file at path."""
    store = file_store.FileStore(path)
    return store


def create_host(hostname, host_info, results_dir):
    """Yield a hosts.CrosHost object with the given inventory information.

    @param hostname: Hostname of the DUT.
    @param info: A HostInfo with the inventory information to use.
    @param results_dir: Path to directory for logs / output artifacts.

    @yield server.hosts.CrosHost object.
    """
    info = host_info.get()
    if not info.board:
        raise DutPreparationError('No board in DUT labels')
    if not info.model:
        raise DutPreparationError('No model in DUT labels')

    need_servo = info.os != 'labstation'
    dut_logs_dir = None

    if need_servo:
        # We assume target host is a cros DUT by default
        if 'servo_host' not in info.attributes:
            raise DutPreparationError('No servo_host in DUT attributes')
        if 'servo_port' not in info.attributes:
            raise DutPreparationError('No servo_port in DUT attributes')

        dut_logs_dir = os.path.join(results_dir, _SERVO_UART_LOGS)
        try:
            os.makedirs(dut_logs_dir)
        except OSError as e:
            if e.errno != errno.EEXIST:
                raise

    return factory.create_target_host(hostname,
                                      host_info_store=host_info,
                                      try_lab_servo=need_servo,
                                      try_servo_repair=need_servo,
                                      try_servo_recovery=need_servo,
                                      servo_uart_logs_dir=dut_logs_dir)


if __name__ == '__main__':
    sys.exit(main())
