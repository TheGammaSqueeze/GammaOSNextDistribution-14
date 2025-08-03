# Copyright 2019 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import common
import logging
from autotest_lib.client.common_lib import hosts
from autotest_lib.server.hosts import cros_constants
from autotest_lib.server.hosts import repair_utils
from autotest_lib.client.common_lib import utils

from autotest_lib.utils.frozen_chromite.lib import timeout_util

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock

# There are some labstations we don't want they receive auto-update,
# e.g. labstations that used for image qualification purpose
UPDATE_EXEMPTED_POOL = {
        'servo_verification', 'labstation_tryjob', 'labstation_canary'
}


class _LabstationUpdateVerifier(hosts.Verifier):
    """
    Verifier to trigger a labstation update, if necessary.

    The operation doesn't wait for the update to complete and is
    considered a success whether or not the servo is currently
    up-to-date.
    """

    @timeout_util.TimeoutDecorator(cros_constants.LONG_VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        """First, only run this verifier if the host is in the physical lab.
        Secondly, skip if the test is being run by test_that, because subnet
        restrictions can cause the update to fail.
        """
        if host.is_in_lab() and host.job and host.job.in_lab:
            host.update_cros_version_label()
            info = host.host_info_store.get()
            if bool(UPDATE_EXEMPTED_POOL & info.pools):
                logging.info("Skip update because the labstation is in"
                             " one of following exempted pool: %s", info.pools)
                return

            stable_version = info.stable_versions.get('cros')
            if stable_version:
                host.update_image(stable_version=stable_version)
            else:
                raise hosts.AutoservVerifyError('Failed to check/update'
                                                ' labstation due to no stable'
                                                '_version found in host_info'
                                                '_store.')

    @property
    def description(self):
        return 'Labstation image is updated to current stable-version'


class _LabstationRebootVerifier(hosts.Verifier):
    """Check if reboot is need for the labstation and perform a reboot if it's
    not currently using by any tests.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        if host.is_reboot_requested():
            host.try_reboot()

    @property
    def description(self):
        return 'Reboot labstation if requested and the labstation is not in use'


class _LabstationLangidVerifier(hosts.Verifier):
    """Check if labstation has issue with read serial from servo devices.

    TODO(b:162518926): remove when bug will be resolved.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            cmd = (
                    "python2 -c 'import usb;"
                    " print([[d.open().getString(d.iSerialNumber, 128)"
                    " for d in bus.devices if d.idVendor == 0x18d1"
                    " and (d.idProduct == 0x501b"  #servo_v4
                    " or d.idProduct == 0x501a"  #servo_micro
                    " or d.idProduct == 0x5014)"  #ccd_cr50
                    " and d.iSerialNumber == 3]"  # 3 - slot for serial
                    " for bus in usb.busses()])'")
            result = host.run(cmd, ignore_status=True, timeout=30)
            if result.exit_status == 0:
                return
            if 'The device has no langid' in result.stderr.strip():
                self._mark_host_for_reboot(host)
        except Exception as e:
            logging.debug('(Not critical) %s', e)
            if 'Timeout encountered' in str(e):
                # Time out mean we cannot get servo attributes in time because
                # one of the servos has langid.
                self._mark_host_for_reboot(host)

    def _mark_host_for_reboot(self, host):
        """Mark Labstation as has issue with langid."""
        logging.info('Detected langid issue.')
        data = {'host': host.hostname, 'board': host.get_board() or ''}
        metrics.Counter('chromeos/autotest/labstation/langid_issue').increment(
                fields=data)
        # labstation reboot will fix the issue but we does not want to
        # reboot the labstation to often. Just create request to reboot
        # it for the next time.
        logging.info('Created request for reboot.')
        cmd = ('touch %slangid%s' %
               (host.TEMP_FILE_DIR, host.REBOOT_FILE_POSTFIX))
        host.run(cmd, ignore_status=True, timeout=30)

    @property
    def description(self):
        return 'Check if labsattion has langid issue'


def create_labstation_repair_strategy():
    """
    Return a `RepairStrategy` for a `LabstationHost`.
    """
    verify_dag = [
            (repair_utils.SshVerifier, 'ssh', []),
            (_LabstationUpdateVerifier, 'update', ['ssh']),
            (_LabstationLangidVerifier, 'langid', ['ssh']),
            (_LabstationRebootVerifier, 'reboot', ['ssh']),
    ]

    repair_actions = [
        (repair_utils.RPMCycleRepair, 'rpm', [], ['ssh', 'reboot']),
    ]
    return hosts.RepairStrategy(verify_dag, repair_actions, 'labstation')
