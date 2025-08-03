# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import logging
import math
import six
import sys
import time

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import global_config
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib import utils
from autotest_lib.client.common_lib.cros import dev_server
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.client.common_lib.cros import tpm_utils
from autotest_lib.server import afe_utils
from autotest_lib.server import crashcollect
from autotest_lib.server.cros import provisioner
from autotest_lib.server.cros.dynamic_suite import tools
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.cros.servo.keyboard import servo_keyboard_flasher
from autotest_lib.server.cros.repair import mac_address_helper
from autotest_lib.server.hosts import cros_constants
from autotest_lib.server.hosts import cros_firmware
from autotest_lib.server.hosts import repair_utils
from autotest_lib.site_utils.admin_audit import verifiers as audit_verify
from autotest_lib.site_utils.admin_audit import constants as audit_const
from autotest_lib.site_utils.admin_audit import battery_validator
from six.moves import range

try:
    from autotest_lib.utils.frozen_chromite.lib import metrics
except ImportError:
    metrics = utils.metrics_mock

from autotest_lib.utils.frozen_chromite.lib import timeout_util

DEFAULT_SERVO_RESET_TRIGGER = (
        'ping',
        'ssh',
        'stop_start_ui',
        'power',
)


# _DEV_MODE_ALLOW_POOLS - The set of pools that are allowed to be
# in dev mode (usually, those should be unmanaged devices)
#
_DEV_MODE_ALLOWED_POOLS = set(
    global_config.global_config.get_config_value(
            'CROS',
            'pools_dev_mode_allowed',
            type=str,
            default='',
            allow_blank=True).split(','))

# Setting to suppress dev mode check; primarily used for moblab where all
# DUT's are in dev mode.
_DEV_MODE_ALWAYS_ALLOWED = global_config.global_config.get_config_value(
            'CROS',
            'dev_mode_allowed',
            type=bool,
            default=False)

# Triggers for the 'provision', 'powerwash', and 'usb' repair actions.
# These are also used as dependencies in the `CrosHost` repair
# sequence, as follows:
#
# provision:
#   - triggers: _CROS_PROVISION_TRIGGERS
#   - depends on: _CROS_USB_TRIGGERS + _CROS_POWERWASH_TRIGGERS
#
# powerwash:
#   - triggers: _CROS_POWERWASH_TRIGGERS + _CROS_PROVISION_TRIGGERS
#   - depends on: _CROS_USB_TRIGGERS
#
# usb:
#   - triggers: _CROS_USB_TRIGGERS + _CROS_POWERWASH_TRIGGERS +
#               _CROS_PROVISION_TRIGGERS
#   - depends on: _CROS_USB_DEPENDENCIES
#
# N.B. AC power detection depends on software on the DUT, and there
# have been bugs where detection failed even though the DUT really
# did have power.  So, we make the 'power' verifier a trigger for
# reinstall repair actions, too.
#
# TODO(jrbarnette):  provision repair can't fix all problems reported by
# the 'cros' verifier; it's listed as an provision trigger as a
# simplification.  The ultimate fix is to split the 'cros' verifier
# into smaller individual verifiers.
_CROS_PROVISION_TRIGGERS = (
        'power',
        'rwfw',
        'fwstatus',
        'python',
        'hwid',
        'cros',
        'dev_default_boot',
)
_CROS_POWERWASH_TRIGGERS = ('tpm', 'good_provision', 'ext4',)
_CROS_USB_TRIGGERS = (
        'ping',
        'ssh',
        'writable',
)
_JETSTREAM_USB_TRIGGERS = (
        'ping',
        'ssh',
        'writable',
)
_CROS_FIRMWARE_TRIGGERS = (
        'ping',
        'ssh',
)
_CROS_AC_TRIGGERS = (
        'ping',
        'power',
)
_CROS_USB_DEPENDENCIES = ('usb_drive', )


class ACPowerVerifier(hosts.Verifier):
    """Check for AC power and battery charging state."""

    # Battery discharging state in power_supply_info file.
    BATTERY_DISCHARGING = 'Discharging'
    # Power controller can discharge battery any time till 90% for any model.
    # Setting level to 90% in case we have wearout of it.
    BATTERY_DISCHARGE_MIN = 90

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        info = self._load_info(host)
        self._validate_ac_plugged(info)
        self._validate_battery(host, info)

    def _load_info(self, host):
        try:
            info = host.get_power_supply_info()
        except error.AutoservRunError:
            raise hosts.AutoservVerifyError(
                    'Failed to get power supply info')
        return info

    def _validate_ac_plugged(self, info):
        # Validate that DUT is plugged to the AC.
        try:
            if info['Line Power']['online'] != 'yes':
                raise hosts.AutoservVerifyError(
                        'AC power is not plugged in')
        except KeyError:
            raise hosts.AutoservVerifyError(
                    'Cannot determine AC power status')

    def _validate_battery(self, host, info):
        host_info = host.host_info_store.get()
        if host_info.get_label_value('power') == 'battery':
            if 'Battery' not in info:
                data = {'host': host.hostname, 'model': host_info.model}
                metrics.Counter('chromeos/autotest/battery_not_detected'
                                ).increment(fields=data)
                logging.info('Battery is not presented but expected!'
                             ' Probably hardware issue.')

        try:
            charging_state = info['Battery']['state']
            battery_level = float(info['Battery']['percentage'])

            # Collect info to determine which battery level is better to call
            # as MIN_BATTERY_LEVEL for DUTs in the lab.
            if battery_level < cros_constants.MIN_BATTERY_LEVEL:
                level_by_10 = int(math.floor(battery_level / 10.0)) * 10
                metrics_data = {
                        'host': host.hostname,
                        'level': level_by_10,
                        'mode': charging_state
                }
                metrics.Counter('chromeos/autotest/battery/state2').increment(
                        fields=metrics_data)

            if (charging_state == self.BATTERY_DISCHARGING
                        and battery_level < self.BATTERY_DISCHARGE_MIN):
                logging.debug('Try to fix discharging state of the battery. '
                              'Possible that a test left wrong state.')
                # Here is the chance that battery is discharging because
                # of some test did not clean up the state.
                # We are going to try to fix it by set charging to normal.
                host.run('ectool chargecontrol normal', ignore_status=True)
                # wait to change state.
                time.sleep(10)
                info = self._load_info(host)
                charging_state = info['Battery']['state']
                fixed = charging_state != self.BATTERY_DISCHARGING
                # TODO (@otabek) remove metrics after research
                logging.debug('Fixed battery discharge mode.')
                metrics_data = {
                        'model': host.host_info_store.get().model,
                        'fixed': fixed
                }
                metrics.Counter(
                    'chromeos/autotest/repair/chargecontrol_fixed'
                ).increment(fields=metrics_data)

            if (battery_level < cros_constants.MIN_BATTERY_LEVEL
                        and charging_state == self.BATTERY_DISCHARGING):
                # TODO(@xianuowang) remove metrics here once we have device
                # health profile to collect history of DUT's metrics.
                metrics_data = {'host': host.hostname,
                                'board': host.host_info_store.get().board}
                metrics.Counter(
                    'chromeos/autotest/repair/verifier/power').increment(
                        fields=metrics_data)
                raise hosts.AutoservVerifyError(
                        'Battery is in discharging state and current level'
                        ' is less than %s%%' %
                        cros_constants.MIN_BATTERY_LEVEL)
        except (KeyError, ValueError):
            logging.warning('Cannot determine battery state -'
                            ' skipping check.')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The DUT is plugged in to AC power and battery is charging'


class ProvisioningLabelsVerifier(hosts.Verifier):
    """Confirm that current ChromeOS image on the host is matches
    to provision labels.

    Some tests behavior may changed DUT image while they don't update
    provision-cros_version or provisioning-job_repo_url labels, which could
    cause the next test run on the same host gets an unexpected data and
    yields false positive test result.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        self._verify_cros_version(host)
        self._verify_job_repo_url(host)

    def _verify_cros_version(self, host):
        """Verify that cros-version match version on the host."""
        label_match = True
        try:
            label_match = host.verify_cros_version_label()
        except Exception as e:
            # We don't want fail this verifier for any errors that other
            # than a actual version mismatch, as that can make debugging
            # more challenge.
            logging.warning(
                    'Unexpected error during verify cros version on %s; %s',
                    host.hostname, e)

        if not label_match:
            raise hosts.AutoservVerifyError('ChromeOS image on the host'
                                            ' does not match to cros-version'
                                            ' label.')

    def _verify_job_repo_url(self, host):
        """Verify that job_repo_url match version on the host."""
        info = host.host_info_store.get()
        job_repo_url = info.attributes.get(ds_constants.JOB_REPO_URL, '')
        if not job_repo_url:
            logging.debug('job_repo_url is empty. Skip check.')
            return
        os_from_host = host.get_release_builder_path()
        if not os_from_host in job_repo_url:
            raise hosts.AutoservVerifyError('ChromeOS image on the host'
                                            ' does not match to job_repo_url'
                                            ' label.')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'ChromeOS image on host matches cros_version label'


class WritableVerifier(hosts.Verifier):
    """
    Confirm the stateful file systems are writable.

    The standard linux response to certain unexpected file system errors
    (including hardware errors in block devices) is to change the file
    system status to read-only.  This checks that that hasn't happened.

    The test covers the two file systems that need to be writable for
    critical operations like AU:
      * The (unencrypted) stateful system which includes
        /mnt/stateful_partition.
      * The encrypted stateful partition, which includes /var.

    The test doesn't check various bind mounts; those are expected to
    fail the same way as their underlying main mounts.  Whether the
    Linux kernel can guarantee that is untested...
    """

    # N.B. Order matters here:  Encrypted stateful is loop-mounted from
    # a file in unencrypted stateful, so we don't test for errors in
    # encrypted stateful if unencrypted fails.
    _TEST_DIRECTORIES = ['/mnt/stateful_partition', '/var/tmp']

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        # This deliberately stops looking after the first error.
        # See above for the details.
        for testdir in self._TEST_DIRECTORIES:
            if not host.is_file_system_writable([testdir]):
                msg = 'Can\'t create a file in %s' % testdir
                raise hosts.AutoservVerifyError(msg)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The stateful filesystems are writable'


class EXT4fsErrorVerifier(hosts.Verifier):
    """
    Confirm we have not seen critical file system kernel errors.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        # grep for stateful FS errors of the type "EXT4-fs error (device sda1):"
        command = ("dmesg | grep -E \"EXT4-fs error \(device "
                   "$(cut -d ' ' -f 5,9 /proc/$$/mountinfo | "
                   "grep -e '^/mnt/stateful_partition ' | "
                   "cut -d ' ' -f 2 | cut -d '/' -f 3)\):\"")
        output = host.run(command=command, ignore_status=True).stdout
        if output:
            sample = output.splitlines()[0]
            message = 'Saw file system error: %s' % sample
            raise hosts.AutoservVerifyError(message)
        # Check for other critical FS errors.
        command = 'dmesg | grep "This should not happen!!  Data will be lost"'
        output = host.run(command=command, ignore_status=True).stdout
        if output:
            message = 'Saw file system error: Data will be lost'
            raise hosts.AutoservVerifyError(message)
        else:
            logging.error('Could not determine stateful mount.')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Did not find critical file system errors'


class UpdateSuccessVerifier(hosts.Verifier):
    """
    Checks that the DUT successfully finished its last provision job.

    At the start of any update (e.g. for a Provision job), the code
    creates a marker file named `PROVISION_FAILED`.  The file is located
    in a part of the stateful partition that will be removed if an
    update finishes successfully.  Thus, the presence of the file
    indicates that a prior update failed.

    The verifier tests for the existence of the marker file and fails if
    it still exists.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        result = host.run('test -f %s' % provisioner.PROVISION_FAILED,
                          ignore_status=True)
        if result.exit_status == 0:
            raise hosts.AutoservVerifyError(
                    'Last provision on this DUT failed')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The most recent provision attempt on this DUT succeeded'


class TPMStatusVerifier(hosts.Verifier):
    """Verify that the host's TPM is in a good state."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        if _is_virtual_machine(host):
            # We do not forward host TPM / emulated TPM to qemu VMs, so skip
            # this verification step.
            logging.debug('Skipped verification %s on VM', self)
            return

        try:
            status = TpmStatus(host)
        except hosts.AutoservVerifyError:
            logging.info('Cannot determine the Cryptohome valid status - '
                         'skipping check.')
            return
        try:
            if not status['is_enabled']:
                raise hosts.AutoservVerifyError(
                        'TPM is not enabled -- Hardware is not working.')
            if status['is_owned'] and not status['is_srk_default_auth']:
                raise hosts.AutoservVerifyError('Cannot load the TPM SRK')
        except KeyError:
            logging.info('Cannot determine the TPM valid status - '
                         'skipping check.')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The host\'s TPM is available and working'


class PythonVerifier(hosts.Verifier):
    """Confirm the presence of a working Python interpreter."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        result = host.run('python -c "import json"',
                          ignore_status=True)
        if result.exit_status != 0:
            message = 'The python interpreter is broken'
            if result.exit_status == 127:
                search = host.run('which python', ignore_status=True)
                if search.exit_status != 0 or not search.stdout:
                    message = ('Python is missing; may be caused by '
                               'powerwash')
            raise hosts.AutoservVerifyError(message)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Python on the host is installed and working'


class DevModeVerifier(hosts.Verifier):
    """Verify that the host is not in dev mode."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        # Some pools are allowed to be in dev mode
        info = host.host_info_store.get()
        if (_DEV_MODE_ALWAYS_ALLOWED or
                bool(info.pools & _DEV_MODE_ALLOWED_POOLS)):
            return

        result = host.run('crossystem devsw_boot', ignore_status=True).stdout
        if result != '0':
            raise hosts.AutoservVerifyError('The host is in dev mode')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The host should not be in dev mode'


class DevDefaultBootVerifier(hosts.Verifier):
    """Verify that the host is set to boot the internal disk by default."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        result = host.run('crossystem dev_default_boot', ignore_status=True)
        default_boot = result.stdout.strip()
        if default_boot != 'disk':
            raise hosts.AutoservVerifyError(
                    'The host has incorrect dev_default_boot value: %r'
                    % default_boot)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The host should have dev_default_boot=disk'


class HWIDVerifier(hosts.Verifier):
    """Verify that the host has HWID & serial number."""

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        info = host.host_info_store.get()
        if not info.board or not info.model:
            # if board or model missed in host_info file then it is empty
            # skip verifier
            return
        info_hwid = info.attributes.get('HWID')
        info_serial_number = info.attributes.get('serial_number')

        if not info_hwid or not info_serial_number:
            logging.info('Missing HWID or/and SerialNumber.'
                         ' Probably device was not deployed properly.'
                         ' Marking DUT for need re-deployment.')
            host.set_device_repair_state(
                    cros_constants.DEVICE_STATE_NEEDS_DEPLOY)
            return

        host_hwid = host.run('crossystem hwid', ignore_status=True).stdout
        host_serial_number = self._get_serial_number(host, info_serial_number)
        if not host_hwid or not host_serial_number:
            raise hosts.AutoservVerifyError(
                    'Failed to get HWID & Serial Number for host %s' %
                    host.hostname)

        if host_hwid != info_hwid:
            # We not fail verifier as it not critical for majority tests.
            metrics.Counter('chromeos/autotest/repair/hwid_change').increment(
                    fields={
                            'host': host.hostname,
                            'board': info.board or ''
                    })
            logging.info(
                    'HWID changed to: %s required manual work'
                    ' to fix it.', host_hwid)

        if host_serial_number and host_serial_number != info_serial_number:
            logging.info(
                    'The SerialNumber mismatch detected %s != %s.'
                    ' Probably attempt to replace DUT without deployment.'
                    ' Marking DUT for need re-deployment.', info_serial_number,
                    host_serial_number)
            host.set_device_repair_state(
                    cros_constants.DEVICE_STATE_NEEDS_DEPLOY)

    def _get_serial_number(self, host, serial_number):
        """Read serial_number from VPD.

        If VPD does not have any value for serial_number then it will
        try to restore from host_info.

        @param host             CrosHost
        @param serial_number    Serial-number from host-info
        """
        req = host.run('vpd -g serial_number', ignore_status=True)
        # serial_number not found in the VPD info
        if not req.stdout and req.exit_status == 3 and serial_number:
            logging.debug('Cannot find serial_number from VPD.')
            # check if vpd working fine without error
            l1 = host.run('vpd -l', ignore_status=True)
            l2 = host.run('vpd -l |grep "\"serial_number\"="',
                          ignore_status=True)
            if l1.exit_status == 0 and l2.exit_status == 1:
                logging.info('Start restoring serial_number:%s for VPD.',
                             serial_number)
                # update serial_number for VPD
                cmd = 'vpd -s serial_number=%s'
                host.run(cmd % serial_number, ignore_status=True)
                host.run('dump_vpd_log --force', ignore_status=True)
                # reading from VPD to see what we updated
                req = host.run('vpd -g serial_number', ignore_status=True)
        return req.stdout

    def _is_applicable(self, host):
        if host.is_satlab():
            logging.info('Not critical for Satlab. Skipping')
            return False
        return True

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The host should have valid HWID and Serial Number'


class EnrollmentStateVerifier(hosts.Verifier):
    """Verify that the device's enrollment state is clean.

    There are two "flags" that generate 3 possible enrollment states here.
    Flag 1 - The presence of install attributes file in
             /home/.shadow/install_attributes.pb

    Flag 2 - The value of "check_enrollment" from VPD. Can be obtained by
             reading the cache file in
             /mnt/stateful_partition/unencrypted/cache/vpd/full-v2.txt

    The states:
    State 1 - Device is enrolled, means flag 1 is true and in
              flag 2 check_enrollment=1
    State 2 - Device is consumer owned, means flag 1 is true and in
              flag 2 check_enrollment=0
    State 3 - Device is enrolled and has been powerwashed, means flag 1 is
              false. If the value in flag 2 is check_enrollment=1 then the
              device will perform forced re-enrollment check and depending
              on the response from the server might force the device to enroll
              again. If the value is check_enrollment=0, then device can be
              used like a new device.

    We consider state 1, and first scenario(check_enrollment=1) of state 3
    as unacceptable state here as they may interfere with normal tests.
    """

    VPD_CACHE = '/mnt/stateful_partition/unencrypted/cache/vpd/full-v2.txt'

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        if self._get_enrollment_state(host):
            raise hosts.AutoservNonCriticalVerifyError('The device is enrolled,'
                                                       ' it may interfere with'
                                                       ' some tests.')

    def _get_enrollment_state(self, host):
        logging.debug('checking enrollment state from VPD cache...')
        response = host.run('grep "check_enrollment" %s' % self.VPD_CACHE,
                            ignore_status=True)
        if response.exit_status == 0:
            result = response.stdout.strip()
            logging.info('Enrollment state in VPD cache: %s', result)
            return result == '"check_enrollment"="1"'

        logging.error('Unexpected error occured during verify enrollment state'
                      ' in VPD cache, skipping verify process.')
        return False

    def _is_applicable(self, host):
        info = host.host_info_store.get()
        # if os type is missing from host_info, then we assume it's cros.
        return getattr(info, 'os', 'cros') in ('', 'cros')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'The enrollment state is clean on the host'


class FirmwareTpmVerifier(hosts.Verifier):
    """Verifier that firmware tpm info is correct.

    For dev-signed firmware, tpm_fwver and tpm_kernver reported from
    crossystem should always be 0x10001. Firmware update on DUTs with
    incorrect tmp_fwver or tpm_kernver may fail due to firmware
    rollback protection.
    """
    # A list of field we want check from crossystem and expected value.
    CHECK_LIST = [
            ('tpm_fwver', '0x00010001'),
            ('tpm_kernver', '0x00010001'),
    ]

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        for field, expected_value in self.CHECK_LIST:
            result = host.run('crossystem %s' % field, ignore_status=True)
            if result.exit_status != 0:
                raise hosts.AutoservNonCriticalVerifyError(
                        'Unable to get %s from crossystem.' % field)
            if result.stdout != expected_value:
                raise hosts.AutoservNonCriticalVerifyError(
                        'Unexpected %s value: %s, expected: %s. This error'
                        ' may cause firmware provision fail due to the'
                        ' rollback protection.' %
                        (field, result.stdout, expected_value))

    def _is_applicable(self, host):
        return cros_firmware._is_firmware_testing_device(host)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Firmware tpm info is correct in crossystem.'


class JetstreamTpmVerifier(hosts.Verifier):
    """Verify that Jetstream TPM is in a good state."""

    @retry.retry(error.AutoservError, timeout_min=2, delay_sec=10)
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        try:
            status = TpmStatus(host)
            if not status.tpm_enabled:
                raise hosts.AutoservVerifyError('TPM is not enabled')
            if not status.tpm_owned:
                raise hosts.AutoservVerifyError('TPM is not owned')
            if not status.tpm_can_load_srk:
                raise hosts.AutoservVerifyError('TPM cannot load SRK')
            if not status.tpm_can_load_srk_pubkey:
                raise hosts.AutoservVerifyError('TPM cannot load SRK pubkey')

            # Check that the TPM is fully initialized. The output of this
            # command is line-oriented property/value pairs.
            result = host.run('cryptohome --action=tpm_status')
            if 'TPM Ready: true' not in result.stdout:
                raise hosts.AutoservVerifyError('TPM is not ready')
        except error.AutoservRunError:
            raise hosts.AutoservVerifyError(
                    'Could not determine TPM status')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Jetstream TPM state check'


class JetstreamAttestationVerifier(hosts.Verifier):
    """Verify that Jetstream attestation client has a certificate."""

    @retry.retry(error.AutoservError, timeout_min=2, delay_sec=10)
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        try:
            # This output is in text protobuf format.
            result = host.run('cryptohome --action=tpm_more_status')
            if 'attestation_prepared: true' not in result.stdout:
                raise hosts.AutoservVerifyError(
                        'Attestation has not been prepared')

            result = host.run('cryptohome --action=tpm_attestation_get_ek')
            if 'EK Certificate' not in result.stdout:
                raise hosts.AutoservVerifyError(
                        'Endorsement certificate not found')
        except error.AutoservRunError:
            raise hosts.AutoservVerifyError(
                    'Unable to fetch endorsement certificate')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Jetstream attestation endorsement check'


class JetstreamServicesVerifier(hosts.Verifier):
    """Verify that Jetstream services are running."""

    # Retry for b/62576902
    @retry.retry(error.AutoservError, timeout_min=1, delay_sec=10)
    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        try:
            host.run('pgrep ap-controller')
        except error.AutoservRunError:
            raise hosts.AutoservVerifyError(
                'ap-controller process is not running')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Jetstream services must be running'


class StopStartUIVerifier(hosts.Verifier):
    """Verify that command 'stop ui' won't crash the DUT.

    We run 'stop ui' in AU and provision. We found some bad images broke
    this command and then broke all the provision of all following test. We add
    this verifier to ensure it works and will trigger reimaging to a good
    version if it fails.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            host.run('stop ui && start ui', ignore_status=True, timeout=45)
        except error.AutoservSSHTimeout:
            raise hosts.AutoservVerifyError(
                "Got timeout when stop ui/start ui. DUT might crash.")

    @property
    def description(self):
        return 'The DUT image works fine when stop ui/start ui.'


class GscToolPresentVerifier(hosts.Verifier):
    """Verify that GSC tool is functional.

    If board/model expected to have GSC tool but it does not have it then need
    to re-image the host to recover it.
    If host-info has label 'cr50' then we expect to have GSC tool on the host.
    """

    VERIFY_GSC_CMD = 'gsctool -a -f'

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        r = host.run(self.VERIFY_GSC_CMD, ignore_status=True, timeout=10)
        if r.exit_status != 0:
            raise hosts.AutoservNonCriticalVerifyError(
                    "GSC tool issue detected.")
        logging.debug('GSC tool is functional.')

    def _is_applicable(self, host):
        host_info = host.host_info_store.get()
        if host_info.get_label_value('cr50'):
            return True
        logging.info('GSC is not on the host.')
        return False

    @property
    def description(self):
        return 'Verify GSC tool is functional.'


class ServoUSBDriveVerifier(hosts.Verifier):
    """Verify that USB drive on Servo is good to use.

    Check if USB drive is detected on servo and verified on servohost and
    USB is not marked for replacement.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        usb_dev = ''
        try:
            usb_dev = host._servo_host._probe_and_validate_usb_dev()
        except hosts.AutoservRepairError as e:
            # We USB drive not detected by servod
            logging.debug('(Not critical) %s', e)
        host_info = host.host_info_store.get()
        if not usb_dev:
            host_info.set_version_label(audit_const.SERVO_USB_STATE_PREFIX,
                                        audit_const.HW_STATE_NOT_DETECTED)
            host.host_info_store.commit(host_info)
            raise hosts.AutoservNonCriticalVerifyError(
                    'USB-drive is not detected or bad')

        # Check if USB-drive marked for replacement.
        usb_state = host_info.get_label_value(
                audit_const.SERVO_USB_STATE_PREFIX)
        if usb_state and usb_state == audit_const.HW_STATE_NEED_REPLACEMENT:
            # Allow to use USB-key marked for replacement.
            # Goal to collect metrics to see if DUT still can recovered
            return
            # TODO(otabek): restory when fix crbug.com/1164408
            # raise hosts.AutoservNonCriticalVerifyError(
            #         'USB-drive marked for replacement')

        # The USB-drive detected and was not mark for replacement.
        # Set as normal for future audit.
        host_info.set_version_label(audit_const.SERVO_USB_STATE_PREFIX,
                                    audit_const.HW_STATE_NORMAL)
        host.host_info_store.commit(host_info)

    def _is_applicable(self, host):
        if host.servo:
            return True
        return False

    @property
    def description(self):
        return 'Ensure USB drive on Servo is in good state.'


class DUTStorageVerifier(hosts.Verifier):
    """Verify that main storage on DUT is good to use.

    Check if DUT drive is providing good SMART stats which not showing any
    issues on it. The verifier can mark DUT for replacement if SMART stats
    show outworn data.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        verifier = audit_verify.VerifyDutStorage(host)
        verifier.verify(set_label=True, run_badblocks='NOT')
        state = verifier.get_state() or audit_const.HW_STATE_UNKNOWN
        if not state:
            raise hosts.AutoservNonCriticalVerifyError(
                    'DUT storage did not detected or state cannot extracted.')
        if state == audit_const.HW_STATE_NEED_REPLACEMENT:
            logging.info('Detected issue with storage on the DUT.')
            host.set_device_needs_replacement()

    @property
    def description(self):
        return 'Ensure DUT storage SMART information is in good state.'


class AuditBattery(hosts.Verifier):
    """Verify that battery on DUT is good to use.

    Check if DUT drive is providing good SMART stats which not showing any
    issues on it. The verifier can mark DUT for replacement if SMART stats
    show outworn data.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        # pylint: disable=missing-docstring
        state = None
        try:
            state = self._get_validator(host).validate()
        except Exception as e:
            # We do not want stop main process if it fail.
            logging.debug('(Not critical) %s', e)
        if not state:
            raise hosts.AutoservNonCriticalVerifyError(
                    'DUT battery did not detected or state cannot extracted.')
        if state == audit_const.HW_STATE_NEED_REPLACEMENT:
            logging.info('Detected issue with storage on the DUT.')
            host.set_device_needs_replacement()

    def _is_applicable(self, host):
        return self._get_validator(host).is_battery_expected()

    def _get_validator(self, host):
        if not getattr(self, '_validator', None):
            self._validator = battery_validator.BatteryValidator(host)
        return self._validator

    @property
    def description(self):
        return 'Ensure DUT battery is in good state.'


class ServoKeyboardMapVerifier(hosts.Verifier):
    """Not critical verify to flash servo keyboard for the host.

    Check if host support servo keyboard and update if firmware is not present.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            flasher = servo_keyboard_flasher.ServoKeyboardMapFlasher()
            if flasher.is_image_supported(host):
                flasher.update(host)
        except Exception as e:
            logging.debug('(Not critical) %s', e)
            raise hosts.AutoservNonCriticalVerifyError(
                    'Fail to verify/update servo keyboard map on the host.')

    def _is_applicable(self, host):
        if host.servo:
            return True
        return False

    @property
    def description(self):
        return 'Verify and update servo keyboard map.'


class ServoMacAddressVerifier(hosts.Verifier):
    """Not critical verify to cache NIC mac address for the host on servo.

    Servo_v4 plugged to the DUT and providing NIC for that. We caching mac
    address on servod side for better debugging.
    """

    @timeout_util.TimeoutDecorator(cros_constants.VERIFY_TIMEOUT_SEC)
    def verify(self, host):
        try:
            helper = mac_address_helper.MacAddressHelper()
            helper.update_if_needed(host)
        except Exception as e:
            logging.debug('(Not critical) %s', e)
            raise hosts.AutoservNonCriticalVerifyError(
                    'Fail to verify/update servo NIC mac address for host.')

    def _is_applicable(self, host):
        if host.servo:
            return True
        return False

    @property
    def description(self):
        return 'Verify and update cached NIC mac address.'


class _ResetRepairAction(hosts.RepairAction):
    """Common handling for repair actions that reset a DUT."""

    def _collect_logs(self, host):
        """Collect logs from a successfully repaired DUT."""
        dirname = 'after_%s' % self.tag
        local_log_dir = crashcollect.get_crashinfo_dir(host, dirname)
        # Collect crash info.
        crashcollect.get_crashinfo(host, None)

    def _check_reset_success(self, host):
        """Check whether reset succeeded, and gather logs if possible."""
        # Waiting to boot device after repair action.
        if host.wait_up(host.BOOT_TIMEOUT):
            if host.get_verifier_state('ssh') == hosts.VERIFY_SUCCESS:
                logging.debug(
                        'Skip collection logs due DUT was sshable before')
                return
            try:
                # Collect logs once we regain ssh access before
                # clobbering them.
                self._collect_logs(host)
            except Exception:
                # If the DUT is up, we want to declare success, even if
                # log gathering fails for some reason.  So, if there's
                # a failure, just log it and move on.
                logging.exception('Non-critical failure in log '
                                  'collection during %s.',
                                  self.tag)
            return
        raise hosts.AutoservRepairError(
                'Host %s is offline after %s.' % (host.hostname, self.tag),
                'failed_to_boot_after_' + self.tag)


class ServoSysRqRepair(_ResetRepairAction):
    """
    Repair a Chrome device by sending a system request to the kernel.

    Sending 3 times the Alt+VolUp+x key combination (aka sysrq-x)
    will ask the kernel to panic itself and reboot while conserving
    the kernel logs in console ramoops.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        repair_utils.require_servo(host, ignore_state=True)
        # Press 3 times Alt+VolUp+X
        # no checking DUT health between each press as
        # killing Chrome is not really likely to fix the DUT SSH.
        for _ in range(3):
            try:
                host.servo.sysrq_x()
            except error.TestFail as ex:
                raise hosts.AutoservRepairError(
                      'cannot press sysrq-x: %s.' % str(ex),
                      'cannot_press_sysrq_x')
            # less than 5 seconds between presses.
            time.sleep(2.0)
        self._check_reset_success(host)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset the DUT via keyboard sysrq-x'


class ServoResetRepair(_ResetRepairAction):
    """Repair a Chrome device by resetting it with servo."""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        repair_utils.require_servo(host, ignore_state=True)
        host.servo.get_power_state_controller().reset()
        self._check_reset_success(host)

    def _is_applicable(self, host):
        if host.servo:
            return True
        return False

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset the DUT via servo'


class ServoCr50RebootRepair(_ResetRepairAction):
    """
    Repair a Chrome device by resetting cr50 by servo.

    Reset cr50 which is ec+ccd reset.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        try:
            host.servo.get_power_state_controller().cr50_reset()
            self._check_reset_success(host)
        finally:
            # cr50 reset will clear some some init like `ccd testlab open`
            # so we want to re-initialize servo after cr50 reset if the main
            # device uses cr50 console commands.
            if host.servo.main_device_uses_gsc_drv():
                host.servo.initialize_dut()

    def _is_applicable(self, host):
        if host.servo:
            if host.servo.has_control('cr50_reboot'):
                return True
        return False

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset(cr50) the DUT via servo'


class DevDefaultBootRepair(hosts.RepairAction):
    """Repair a CrOS target by setting dev_default_boot to 'disk'"""

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        host.run('crossystem dev_default_boot=disk', ignore_status=True)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return "Set dev_default_boot to 'disk'"


class CrosRebootRepair(repair_utils.RebootRepair):
    """Repair a CrOS target by clearing dev mode and rebooting it."""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        # N.B. We need to reboot regardless of whether clearing
        # dev_mode succeeds or fails.
        host.run('/usr/share/vboot/bin/set_gbb_flags.sh 0',
                 ignore_status=True)
        host.run('crossystem disable_dev_request=1',
                 ignore_status=True)
        super(CrosRebootRepair, self).repair(host)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset GBB flags and Reboot the host'


class ProvisioningLabelsRepair(hosts.RepairAction):
    """Repair issue with provisioning labels for the host.

    The repair is doing simple clean up of labels as next provisioning will
    re-generate required fields.
    """

    @timeout_util.TimeoutDecorator(cros_constants.SHORT_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        afe_utils.clean_provision_labels(host)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Cleanup provisioning labels for the host'


class EnrollmentCleanupRepair(hosts.RepairAction):
    """Cleanup enrollment state on ChromeOS device"""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # Reset VPD enrollment state.
        host.run('/usr/sbin/update_rw_vpd check_enrollment 0')

        # Clear TPM Owner state.
        tpm_utils.ClearTPMOwnerRequest(host, wait_for_ready=True,
                                       timeout=host.BOOT_TIMEOUT)

    def _is_applicable(self, host):
        info = host.host_info_store.get()
        # if os type is missing from host_info, then we assume it's cros.
        return getattr(info, 'os', 'cros') in ('', 'cros')

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Cleanup enrollment state and reboot the host'


class ProvisionRepair(hosts.RepairAction):
    """
    Repair by re-installing a test image using quick provision.

    Try to install the DUT's designated "stable test image" using the
    standard procedure for installing a new test image via quick provision.
    """

    @timeout_util.TimeoutDecorator(cros_constants.LONG_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        image_name = host.get_cros_repair_image_name()
        logging.info('Staging build for provision: %s', image_name)
        devserver = dev_server.ImageServer.resolve(image_name, host.hostname)
        devserver.trigger_download(image_name, synchronous=False)
        update_url = tools.image_url_pattern() % (
                devserver.url(), image_name)
        afe_utils.machine_install_and_update_labels(host, update_url)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Re-install the stable build on the host'


class PowerWashRepair(ProvisionRepair):
    """
    Powerwash the DUT, then re-install using quick provision.

    Powerwash the DUT, then attempt to re-install a stable test image as
    for `ProvisionRepair`.
    """

    @timeout_util.TimeoutDecorator(cros_constants.LONG_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        host.run('echo "fast safe" > '
                 '/mnt/stateful_partition/factory_install_reset')
        host.reboot(timeout=host.POWERWASH_BOOT_TIMEOUT, wait=True)
        super(PowerWashRepair, self).repair(host)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Powerwash and then re-install the stable build on the host'


class ServoInstallRepair(hosts.RepairAction):
    """
    Reinstall a test image from USB using servo.

    Use servo to re-install the DUT's designated "stable test image"
    from servo-attached USB storage.
    """

    # Timeout value for this repair action is specially configured as we need
    # stage image to usb drive, install chromeos image.
    @timeout_util.TimeoutDecorator(60 * 60)
    def repair(self, host):
        self.boot_in_recovery = False
        # pylint: disable=missing-docstring
        repair_utils.require_servo(host, ignore_state=True)
        image_name = host.get_cros_repair_image_name()
        image_name_on_usb = host._servo_host.validate_image_usbkey()
        if image_name_on_usb == image_name:
            logging.info(
                    'Required image %s is already on usbkey,'
                    ' skipping download.', image_name)
            need_update_image = False
        else:
            logging.info('Required image is not on usbkey.')
            need_update_image = True

        # Verify if we want to force re-image the USB.
        if not need_update_image and host.health_profile:
            repair_failed_count = host.health_profile.get_repair_fail_count()
            # try to re-image USB when previous attempt failed
            if (repair_failed_count > 0 and
                (repair_failed_count == 1 or repair_failed_count % 10 == 0)):
                logging.info(
                        'Required re-download image to usbkey as'
                        ' a previous repair failed. Fail count: %s',
                        repair_failed_count)
                need_update_image = True

        update_url = None
        if need_update_image:
            logging.info('Staging image: %s on caching server.', image_name)
            _, update_url = host.stage_image_for_servo()
        afe_utils.clean_provision_labels(host)
        # Start process to install new image from USB
        need_snk = host.require_snk_mode_in_recovery()

        host.servo.get_power_state_controller().power_off()
        if update_url:
            try:
                host.install_image_to_servo_usb(image_url=update_url)
            except Exception as e:
                # Format USB-storage as incorrect download image can cause
                # false believe that image downloaded.
                self._format_usb_storage(host)
                # Powering DUT on as if leave it in off mode can cause issue
                # with detecting ccd_cr50 on the board.
                host.servo.get_power_state_controller().power_on()
                six.reraise(error.AutotestError, str(e), sys.exc_info()[2])
        else:
            # Give the DUT some time to power_off if we skip
            # download image to usb. (crbug.com/982993)
            time.sleep(10)

        host.boot_in_recovery_mode(need_snk=need_snk)
        # Note that device successful booted from USB
        # That mean fw RO is good.
        self.boot_in_recovery = True
        host.run_install_image(install_timeout=host.ADMIN_INSTALL_TIMEOUT * 2,
                               need_snk=need_snk,
                               is_repair=True)
        afe_utils.add_provision_labels(host, host.VERSION_PREFIX, image_name)
        # Collect info which USB-key used for successful re-image.
        host_info = host.host_info_store.get()
        if host_info:
            usb_state = host_info.get_label_value(
                    audit_const.SERVO_USB_STATE_PREFIX)
            metrics_data = {'host': host.hostname, 'usb_state': usb_state}
            metrics.Counter('chromeos/autotest/usbkey_install_success'
                            ).increment(fields=metrics_data)

    def _format_usb_storage(self, host):
        """Format USB-storage connected to servo."""
        try:
            # Format USB-storage to prevent corrupted image to be
            # counted as good image.
            usb_path = host.servo.probe_host_usb_dev()
            logging.info('Formating %s', usb_path)
            cmd = 'mkfs.ext4 -F %s' % usb_path
            host._servo_host.run(cmd, ignore_status=True)
        except Exception as e:
            logging.info('(Not critical) fail to format USB-storage: %s', e)

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reinstall from USB using servo'


class ServoResetAfterUSBRepair(_ResetRepairAction):
    """Repair a host by resetting it with servo.

    This is follow up action for cases when device fail to boot as part of
    USB-install. The repair will be applicable only if device was successful
    booted from USB-key.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        host.servo.get_power_state_controller().reset()
        self._check_reset_success(host)

    def _is_applicable(self, host):
        if not host.servo:
            return False
        if host.is_marked_for_replacement():
            logging.debug('The device marked for replacement.'
                          ' Skip the action.')
            return False
        usb_install = host.get_repair_strategy_node('usb')
        if not usb_install:
            logging.debug('Strategy node not found! Skip repair action.')
            return False
        if not getattr(usb_install, 'boot_in_recovery', False):
            logging.debug('Device did not boot in recovery mode.'
                          ' Skip repair action.')
            return False
        return True

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset the DUT via servo after USB-install'


class RecoverFwAfterUSBRepair(_ResetRepairAction):
    """Recover FW on the host when host can boot in recovery mode.

    This is follow up action for cases when device fail to boot as part of
    USB-install but successful booted in recovery mode.

    If host can boot in recovery mode but fail boot in default mode then
    probably we have corrupted firmware. The repair try to recover firmware
    on the host by booting from USB-key.
    """

    # Command to update firmware located on host
    _FW_UPDATE_CMD = 'chromeos-firmwareupdate --mode=recovery'

    @timeout_util.TimeoutDecorator(cros_constants.LONG_REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        # Switch USB_key to servo to wake up it as sometimes it can show
        # USB-key direction to DUT but it is not yet seeing by DUT.
        host.servo.switch_usbkey('host')
        time.sleep(host.servo.USB_DETECTION_DELAY)
        # Power off the DUT as in this case the host will boot
        # in recovery mode with higher chance.
        host.servo.get_power_state_controller().power_off()
        # Give the DUT some time to power_off if we skip
        # download image to usb. (crbug.com/982993)
        time.sleep(10)

        # Boot host in recovery mode as it is working and verified
        # by another repair action.
        need_snk = host.require_snk_mode_in_recovery()
        try:
            host.boot_in_recovery_mode(need_snk=need_snk)
            logging.debug('Host booted in recovery mode')

            result = host.run(self._FW_UPDATE_CMD, ignore_status=True)
            if result.exit_status != 0:
                logging.error('chromeos-firmwareupdate failed: %s',
                              result.stdout.strip())
            host.halt()
        finally:
            # We need reset the DUT no matter success or not,
            # as we don't want leave the DUT in boot from usb state.
            # N.B. The Servo API requires that we use power_on() here
            # for two reasons:
            #  1) After turning on a DUT in recovery mode, you must turn
            #     it off and then on with power_on() once more to
            #     disable recovery mode (this is a Parrot specific
            #     requirement).
            #  2) After power_off(), the only way to turn on is with
            #     power_on() (this is a Storm specific requirement).
            logging.debug('Power cycling DUT through servo.')
            host.servo.get_power_state_controller().power_off()
            host.servo.switch_usbkey('off')
            if need_snk:
                # Attempt to restore servo_v4 role to 'src' mode.
                host.servo.set_servo_v4_role('src')
            # Use cold-reset instead 'on' to increase the chance to boot DUT
            host.servo.get_power_state_controller().reset()
        self._check_reset_success(host)

    def _is_applicable(self, host):
        if not host.servo:
            return False
        if host.is_marked_for_replacement():
            logging.debug('The device marked for replacement.'
                          ' Skip the action.')
            return False
        usb_install = host.get_repair_strategy_node('usb')
        if not usb_install:
            logging.debug('Strategy node not found! Skip repair action.')
            return False
        if not getattr(usb_install, 'boot_in_recovery', False):
            logging.debug('Device did not boot in recovery mode.'
                          ' Skip repair action.')
            return False
        dhp = host.health_profile
        if not dhp:
            logging.info('Device health profile is not available, cannot'
                         ' determine if firmware repair is needed.')
            return False
        if dhp.get_failed_repair_action(self.tag) > 2:
            logging.info('Firmware recovery has been attempted and failed 3'
                         ' times, no need to retry.')
            return False
        return True

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Recover FW on the host after USB-install'


class RecoverACPowerRepair(_ResetRepairAction):
    """Recover AC detection if AC is not detected.

    The fix based on toggle PD negotiating on EC level of DUT.
    Repair works only for the DUT which has EC and battery.
    """

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        repair_utils.require_servo(host, ignore_state=True)
        # Verify that EC is available and we can interact with that.
        # Do not put it in '_is_applicable' to avoid extra DUT reset.
        try:
            host.servo.get_ec_board()
        except Exception as e:
            logging.debug('(Not critical) %s', e)
            # if EC is off it will fail to execute any EC command
            # to wake it up we do cold-reboot then we will have active ec
            # connection for ~30 seconds
            host.servo.get_power_state_controller().reset()
        try:
            if host.servo.get('battery_is_charging'):
                # device is changing.
                return
        except Exception as e:
            logging.debug('(Not critical) %s', e)
            raise hosts.AutoservRepairError(
                    'Fail to read battery metrics from EC')
        # Simple off-on not always working stable in all cases as source-sink
        # not working too in another cases. To cover more cases here we do
        # both toggle to recover PD negotiation.
        # Source/sink switching CC lines to make DUT work as supplying or
        # consuming power (between Rp and Rd).
        self._set_pd_dualrole(host, 'off')
        self._set_pd_dualrole(host, 'on')
        self._set_pd_dualrole(host, 'source')
        self._set_pd_dualrole(host, 'sink')
        # wait to reinitialize PD negotiation and charge a little bit
        time.sleep(120)
        # Recommended to reset EC after manipulation with PD
        host.servo.get_power_state_controller().reset()
        # Verify if repair well done.
        if not host.servo.get('battery_is_charging'):
            raise hosts.AutoservRepairError(
                    'Fail recovery AC detection fo the DUT.',
                    'failed_recover_usb_pd_ac')
        self._check_reset_success(host)

    def _set_pd_dualrole(self, host, role):
        host.servo.set_nocheck('ec_uart_flush', 'off')
        host.servo.set_nocheck('ec_uart_cmd', 'pd dualrole %s' % role)
        host.servo.set_nocheck('ec_uart_flush', 'on')
        time.sleep(1)

    def _is_applicable(self, host):
        if not host._servo_host.is_ec_supported():
            logging.info('The board not support EC')
            return False
        host_info = host.host_info_store.get()
        if host_info.get_label_value('power') != 'battery':
            logging.info('The board does not have battery')
            return False
        return True

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Recovery AC of DUT'


class JetstreamTpmRepair(hosts.RepairAction):
    """Repair by resetting TPM and rebooting."""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        host.run('rm -f /var/cache/ap/setup-network', ignore_status=True)
        host.run('rm -f /home/chronos/.oobe_completed', ignore_status=True)
        host.run('rm -f /home/.shadow/.can_attempt_ownership',
                 ignore_status=True)
        host.run('crossystem clear_tpm_owner_request=1', ignore_status=True)
        host.reboot()

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Reset TPM and reboot'


class JetstreamServiceRepair(hosts.RepairAction):
    """Repair by restarting Jetstream services."""

    @timeout_util.TimeoutDecorator(cros_constants.REPAIR_TIMEOUT_SEC)
    def repair(self, host):
        # pylint: disable=missing-docstring
        host.cleanup_services()

    @property
    def description(self):
        # pylint: disable=missing-docstring
        return 'Restart Jetstream services'


def _cros_verify_dag():
    """Return the verification DAG for a `CrosHost`."""
    return _cros_verify_base_dag() + _cros_verify_extended_dag()


def _cros_verify_base_dag():
    """Return the base verification DAG for a `CrosHost`."""
    FirmwareStatusVerifier = cros_firmware.FirmwareStatusVerifier
    FirmwareVersionVerifier = cros_firmware.FirmwareVersionVerifier
    verify_dag = (
            (repair_utils.PingVerifier, 'ping', ()),
            (repair_utils.SshVerifier, 'ssh', ('ping', )),
            (ServoUSBDriveVerifier, 'usb_drive', ()),
            (DevDefaultBootVerifier, 'dev_default_boot', ('ssh', )),
            (DevModeVerifier, 'devmode', ('ssh', )),
            (EnrollmentStateVerifier, 'enrollment_state', ('ssh', )),
            (HWIDVerifier, 'hwid', ('ssh', )),
            (ACPowerVerifier, 'power', ('ssh', )),
            (EXT4fsErrorVerifier, 'ext4', ('ssh', )),
            (WritableVerifier, 'writable', ('ssh', )),
            (TPMStatusVerifier, 'tpm', ('ssh', )),
            (UpdateSuccessVerifier, 'good_provision', ('ssh', )),
            (FirmwareTpmVerifier, 'faft_tpm', ('ssh', )),
            (FirmwareStatusVerifier, 'fwstatus', ('ssh', )),
            (FirmwareVersionVerifier, 'rwfw', ('ssh', )),
            (PythonVerifier, 'python', ('ssh', )),
            (repair_utils.LegacyHostVerifier, 'cros', ('ssh', )),
            (ProvisioningLabelsVerifier, 'provisioning_labels', ('ssh', )),
    )
    return verify_dag


def _cros_verify_extended_dag():
    """Return the extended verification DAG for a `CrosHost`."""
    return (
            (StopStartUIVerifier, 'stop_start_ui', ('ssh', )),
            (DUTStorageVerifier, 'storage', ('ssh', )),
            (AuditBattery, 'audit_battery', ()),
            (GscToolPresentVerifier, 'dut_gsctool', ('ssh', )),
            (ServoKeyboardMapVerifier, 'dut_servo_keyboard', ('ssh', )),
            (ServoMacAddressVerifier, 'dut_servo_macaddr', ('ssh', )),
    )


def _cros_basic_repair_actions(
    servo_reset_trigger=DEFAULT_SERVO_RESET_TRIGGER
):
    """Return the basic repair actions for a `CrosHost`

    @param servo_reset_trigger: sequence of verifiers that trigger servo reset
    and servo cr50 reboot repair.
    """
    repair_actions = (
            # RPM cycling must precede Servo reset:  if the DUT has a dead
            # battery, we need to reattach AC power before we reset via servo.
            (repair_utils.RPMCycleRepair, 'rpm', (), (
                    'ping',
                    'ssh',
                    'power',
            )),
            (ServoResetRepair, 'servoreset', (), servo_reset_trigger),
            (ServoCr50RebootRepair, 'cr50_reset', (), servo_reset_trigger),
            (ServoSysRqRepair, 'sysrq', (), (
                    'ping',
                    'ssh',
            )),
            (ProvisioningLabelsRepair, 'provisioning_labels_repair', ('ssh', ),
             ('provisioning_labels', )),

            # N.B. FaftFirmwareRepair can't fix a 'good_provision' failure
            # directly, because it doesn't remove the flag file that triggers
            # the failure.  We include it as a repair trigger because it's
            # possible the the last update failed because of the firmware,
            # and we want the repair steps below to be able to trust the
            # firmware.
            (cros_firmware.FaftFirmwareRepair, 'faft_firmware_repair', (), (
                    'ping',
                    'ssh',
                    'fwstatus',
                    'good_provision',
            )),
            (DevDefaultBootRepair, 'set_default_boot', ('ssh', ),
             ('dev_default_boot', )),
            (CrosRebootRepair, 'reboot', ('ssh', ), (
                    'devmode',
                    'writable',
            )),
            (EnrollmentCleanupRepair, 'cleanup_enrollment', ('ssh', ),
             ('enrollment_state', )),
    )
    return repair_actions


def _cros_extended_repair_actions(provision_triggers=_CROS_PROVISION_TRIGGERS,
                                  powerwash_triggers=_CROS_POWERWASH_TRIGGERS,
                                  usb_triggers=_CROS_USB_TRIGGERS,
                                  usb_dependencies=_CROS_USB_DEPENDENCIES):
    """Return the extended repair actions for a `CrosHost`"""

    # The dependencies and triggers for the 'provision', 'powerwash', and 'usb'
    # repair actions stack up:  Each one is able to repair progressively
    # more verifiers than the one before.  The 'triggers' lists specify
    # the progression.

    repair_actions = (
            (ProvisionRepair, 'provision', usb_triggers + powerwash_triggers,
             provision_triggers),
            (PowerWashRepair, 'powerwash', usb_triggers,
             powerwash_triggers + provision_triggers),
            (
                    ServoInstallRepair,
                    'usb',
                    usb_dependencies,
                    # faft_tpm is a trigger of usb repair action but should not be
                    # dependence of provision and powerwash repair action, due to
                    # restriction of current structure, we hardcode it here instead
                    # of put it into _CROS_USB_TRIGGERS. TODO(xianuowang@) refactor
                    # the logic to create action/verifier DAG for different host
                    # type after we decouple infra from test autotest repo.
                    usb_triggers + powerwash_triggers + provision_triggers +
                    ('faft_tpm', )),
    )
    return repair_actions


def _cros_repair_actions():
    """Return the repair actions for a `CrosHost`."""

    servo_reset_trigger = DEFAULT_SERVO_RESET_TRIGGER
    firmware_triggers = _CROS_FIRMWARE_TRIGGERS
    ac_triggers = _CROS_AC_TRIGGERS
    usb_dependencies = _CROS_USB_DEPENDENCIES
    provision_triggers = _CROS_PROVISION_TRIGGERS + (
            'stop_start_ui',
            'dut_gsctool',
    )
    powerwash_triggers = _CROS_POWERWASH_TRIGGERS
    usb_triggers = _CROS_USB_TRIGGERS

    repair_actions = (
            # RPM cycling must precede Servo reset:  if the DUT has a dead
            # battery, we need to reattach AC power before we reset via servo.
            (repair_utils.RPMCycleRepair, 'rpm', (), (
                    'ping',
                    'ssh',
                    'power',
            )),
            (ServoResetRepair, 'servoreset', (), servo_reset_trigger),
            (ServoCr50RebootRepair, 'cr50_reset', (), servo_reset_trigger),
            (ServoSysRqRepair, 'sysrq', (), (
                    'ping',
                    'ssh',
            )),
            (ProvisioningLabelsRepair, 'provisioning_labels_repair', ('ssh', ),
             ('provisioning_labels', )),

            # N.B. FaftFirmwareRepair can't fix a 'good_provision' failure
            # directly, because it doesn't remove the flag file that triggers
            # the failure.  We include it as a repair trigger because it's
            # possible the the last update failed because of the firmware,
            # and we want the repair steps below to be able to trust the
            # firmware.
            (cros_firmware.FaftFirmwareRepair, 'faft_firmware_repair', (), (
                    'ping',
                    'ssh',
                    'fwstatus',
                    'good_provision',
            )),
            (DevDefaultBootRepair, 'set_default_boot', ('ssh', ),
             ('dev_default_boot', )),
            (CrosRebootRepair, 'reboot', ('ssh', ), (
                    'devmode',
                    'writable',
            )),
            (EnrollmentCleanupRepair, 'cleanup_enrollment', ('ssh', ),
             ('enrollment_state', )),
            (cros_firmware.GeneralFirmwareRepair, 'general_firmware',
             usb_dependencies, firmware_triggers),
            (RecoverACPowerRepair, 'ac_recover', (), ac_triggers),
            (ProvisionRepair, 'provision', usb_triggers + powerwash_triggers,
             provision_triggers),
            (PowerWashRepair, 'powerwash', usb_triggers,
             powerwash_triggers + provision_triggers),
            (
                    ServoInstallRepair,
                    'usb',
                    usb_dependencies,
                    # faft_tpm is a trigger of usb repair action but should
                    # not be dependence of provision and powerwash repair
                    # action, due to restriction of current structure, we
                    # hardcode it here instead of put it into
                    # _CROS_USB_TRIGGERS. TODO(xianuowang@) refactor the logic
                    # to create action/verifier DAG for different host type
                    # after we decouple infra from test autotest repo.
                    usb_triggers + powerwash_triggers + provision_triggers +
                    ('faft_tpm', )),
            (ServoResetAfterUSBRepair, 'servo_reset_after_usb',
             (usb_dependencies), (
                     'ping',
                     'ssh',
             )),
            (RecoverFwAfterUSBRepair, 'recover_fw_after_usb',
             (usb_dependencies), (
                     'ping',
                     'ssh',
             )),
    )
    return repair_actions


def create_cros_repair_strategy():
    """Return a `RepairStrategy` for a `CrosHost`."""
    verify_dag = _cros_verify_dag()
    repair_actions = _cros_repair_actions()
    return hosts.RepairStrategy(verify_dag, repair_actions, 'cros')


def _moblab_verify_dag():
    """Return the verification DAG for a `MoblabHost`."""
    verify_dag = (
        (repair_utils.SshVerifier,        'ssh',     ()),
        (ACPowerVerifier,                 'power',   ('ssh',)),
        (PythonVerifier,                  'python',  ('ssh',)),
        (repair_utils.LegacyHostVerifier, 'cros',    ('ssh',)),
    )
    return verify_dag


def _moblab_repair_actions():
    """Return the repair actions for a `MoblabHost`."""
    repair_actions = (
        (repair_utils.RPMCycleRepair, 'rpm', (), ('ssh', 'power',)),
        (ProvisionRepair, 'provision', ('ssh',), ('power', 'python', 'cros')),
    )
    return repair_actions


def create_moblab_repair_strategy():
    """
    Return a `RepairStrategy` for a `MoblabHost`.

    Moblab is a subset of the CrOS verify and repair.  Several pieces
    are removed because they're not expected to be meaningful.  Some
    others are removed for more specific reasons:

    'tpm':  Moblab DUTs don't run the tests that matter to this
        verifier.  TODO(jrbarnette)  This assertion is unproven.

    'good_provision':  This verifier can't pass, because the Moblab provision
        procedure doesn't properly delete the PROVISION_FAILED file.
        TODO(jrbarnette) We should refactor ChromiumOSProvisioner so
        that it can be different for Moblab.

    'firmware':  Moblab DUTs shouldn't be in FAFT pools, so we don't try
        this.

    'powerwash':  Powerwash on Moblab causes trouble with deleting the
        DHCP leases file, so we skip it.
    """
    verify_dag = _moblab_verify_dag()
    repair_actions = _moblab_repair_actions()
    return hosts.RepairStrategy(verify_dag, repair_actions, 'moblab')


def _jetstream_repair_actions():
    """Return the repair actions for a `JetstreamHost`."""
    provision_triggers = _CROS_PROVISION_TRIGGERS
    jetstream_tpm_triggers = ('jetstream_tpm', 'jetstream_attestation')
    jetstream_service_triggers = (jetstream_tpm_triggers +
                                  ('jetstream_services',))
    base_actions = _cros_basic_repair_actions(servo_reset_trigger=(
            'ping',
            'ssh',
    ))
    custom_actions = (
            (JetstreamTpmRepair, 'jetstream_tpm_repair',
             _JETSTREAM_USB_TRIGGERS + _CROS_POWERWASH_TRIGGERS,
             provision_triggers + jetstream_tpm_triggers),
            (JetstreamServiceRepair, 'jetstream_service_repair',
             _JETSTREAM_USB_TRIGGERS + _CROS_POWERWASH_TRIGGERS +
             ('jetstream_tpm', 'jetstream_attestation'),
             provision_triggers + jetstream_service_triggers),
    )
    extend_actions = _cros_extended_repair_actions(
            provision_triggers=provision_triggers + jetstream_service_triggers,
            usb_triggers=_JETSTREAM_USB_TRIGGERS)
    return base_actions + custom_actions + extend_actions


def _jetstream_verify_dag():
    """Return the verification DAG for a `JetstreamHost`."""
    verify_dag = _cros_verify_base_dag() + (
        (JetstreamTpmVerifier, 'jetstream_tpm', ('ssh',)),
        (JetstreamAttestationVerifier, 'jetstream_attestation', ('ssh',)),
        (JetstreamServicesVerifier, 'jetstream_services', ('ssh',)),
    )
    return verify_dag


def create_jetstream_repair_strategy():
    """
    Return a `RepairStrategy` for a `JetstreamHost`.

    The Jetstream repair strategy is based on the CrOS verify and repair,
    but adds the JetstreamServicesVerifier.
    """
    verify_dag = _jetstream_verify_dag()
    repair_actions = _jetstream_repair_actions()
    return hosts.RepairStrategy(verify_dag, repair_actions, 'jetstream')


# TODO(pprabhu) Move this to a better place. I have no idea what that place
# would be.
def _is_virtual_machine(host):
    """Determine whether the given |host| is a virtual machine.

    @param host: a hosts.Host object.
    @returns True if the host is a virtual machine, False otherwise.
    """
    output = host.run('cat /proc/cpuinfo | grep "model name"',
                      ignore_status=True)
    return (output.exit_status == 0 and output.stdout and
            'qemu' in output.stdout.lower())


class TpmStatus(dict):
    """Wrapper for getting cryptohome status from a host."""

    def __init__(self, host):
        super(TpmStatus, self).__init__()
        self.update(_get_tpm_status(host))

    @property
    def tpm_enabled(self):
        # pylint: disable=missing-docstring
        return self.get('is_enabled') == True

    @property
    def tpm_owned(self):
        # pylint: disable=missing-docstring
        return self.get('is_owned') == True

    @property
    def tpm_can_load_srk(self):
        # pylint: disable=missing-docstring
        return self.tpm_owned and self.get('is_srk_default_auth') == True

    @property
    def tpm_can_load_srk_pubkey(self):
        # pylint: disable=missing-docstring
        return self.tpm_owned and self.get('is_srk_default_auth') == True


def _get_tpm_status(host):
    """Returns a dictionary containing the TPM status.

    @param host: a hosts.Host object.
    @returns A dictionary containing the TPM status.
    @raises AutoservVerifyError: if the output could not be parsed or the TPM
       status is missing.
    @raises hosts.AutoservRunError: if the cryptohome command failed.
    """
    try:
        output = host.run(
                'tpm_manager_client status --nonsensitive').stdout.strip()
        lines = output.split('\n')[1:-1]
        status = {}
        for item in lines:
            item = item.split(':')
            if not item[0]:
                continue
            if len(item) == 1:
                item.append('')
            item = [x.strip() for x in item]
            item[1] = True if item[1] == 'true' else item[1]
            item[1] = False if item[1] == 'false' else item[1]
            status[item[0]] = item[1]
        if status['status'] != 'STATUS_SUCCESS':
            raise hosts.AutoservVerifyError('TPM status is missing')
        return status
    except ValueError:
        raise hosts.AutoservVerifyError('Unable to parse cryptohome status')
