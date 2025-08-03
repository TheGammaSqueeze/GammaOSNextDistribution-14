#!/usr/bin/python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import itertools
import unittest
from unittest import mock

import common
from autotest_lib.client.common_lib import error
from autotest_lib.client.common_lib import hosts
from autotest_lib.client.common_lib.cros import retry
from autotest_lib.server.hosts import cros_firmware
from autotest_lib.server.hosts import cros_repair
from autotest_lib.server.hosts import repair_utils


CROS_VERIFY_DAG = (
        (repair_utils.PingVerifier, 'ping', ()),
        (repair_utils.SshVerifier, 'ssh', ('ping', )),
        (cros_repair.ServoUSBDriveVerifier, 'usb_drive', ()),
        (cros_repair.DevDefaultBootVerifier, 'dev_default_boot', ('ssh', )),
        (cros_repair.DevModeVerifier, 'devmode', ('ssh', )),
        (cros_repair.EnrollmentStateVerifier, 'enrollment_state', ('ssh', )),
        (cros_repair.HWIDVerifier, 'hwid', ('ssh', )),
        (cros_repair.ACPowerVerifier, 'power', ('ssh', )),
        (cros_repair.EXT4fsErrorVerifier, 'ext4', ('ssh', )),
        (cros_repair.WritableVerifier, 'writable', ('ssh', )),
        (cros_repair.TPMStatusVerifier, 'tpm', ('ssh', )),
        (cros_repair.UpdateSuccessVerifier, 'good_provision', ('ssh', )),
        (cros_repair.FirmwareTpmVerifier, 'faft_tpm', ('ssh', )),
        (cros_firmware.FirmwareStatusVerifier, 'fwstatus', ('ssh', )),
        (cros_firmware.FirmwareVersionVerifier, 'rwfw', ('ssh', )),
        (cros_repair.PythonVerifier, 'python', ('ssh', )),
        (repair_utils.LegacyHostVerifier, 'cros', ('ssh', )),
        (cros_repair.ProvisioningLabelsVerifier, 'provisioning_labels',
         ('ssh', )),
        (cros_repair.StopStartUIVerifier, 'stop_start_ui', ('ssh', )),
        (cros_repair.DUTStorageVerifier, 'storage', ('ssh', )),
        (cros_repair.AuditBattery, 'audit_battery', ()),
        (cros_repair.GscToolPresentVerifier, 'dut_gsctool', ('ssh', )),
        (cros_repair.ServoKeyboardMapVerifier, 'dut_servo_keyboard',
         ('ssh', )),
        (cros_repair.ServoMacAddressVerifier, 'dut_servo_macaddr', ('ssh', )),
)

CROS_REPAIR_ACTIONS = (
        (repair_utils.RPMCycleRepair, 'rpm', (), (
                'ping',
                'ssh',
                'power',
        )),
        (cros_repair.ServoResetRepair, 'servoreset', (), (
                'ping',
                'ssh',
                'stop_start_ui',
                'power',
        )),
        (cros_repair.ServoCr50RebootRepair, 'cr50_reset', (),
         ('ping', 'ssh', 'stop_start_ui', 'power')),
        (cros_repair.ServoSysRqRepair, 'sysrq', (), (
                'ping',
                'ssh',
        )),
        (cros_repair.ProvisioningLabelsRepair, 'provisioning_labels_repair',
         ('ssh', ), ('provisioning_labels', )),
        (cros_firmware.FaftFirmwareRepair, 'faft_firmware_repair', (),
         ('ping', 'ssh', 'fwstatus', 'good_provision')),
        (cros_repair.DevDefaultBootRepair, 'set_default_boot', ('ssh', ),
         ('dev_default_boot', )),
        (cros_repair.CrosRebootRepair, 'reboot', ('ssh', ), (
                'devmode',
                'writable',
        )),
        (cros_repair.EnrollmentCleanupRepair, 'cleanup_enrollment', ('ssh', ),
         ('enrollment_state', )),
        (cros_firmware.GeneralFirmwareRepair, 'general_firmware',
         ('usb_drive', ), (
                 'ping',
                 'ssh',
         )),
        (cros_repair.RecoverACPowerRepair, 'ac_recover', (), ('ping',
                                                              'power')),
        (cros_repair.ProvisionRepair, 'provision',
         ('ping', 'ssh', 'writable', 'tpm', 'good_provision',
          'ext4'), ('power', 'rwfw', 'fwstatus', 'python', 'hwid', 'cros',
                    'dev_default_boot', 'stop_start_ui', 'dut_gsctool')),
        (cros_repair.PowerWashRepair, 'powerwash', ('ping', 'ssh', 'writable'),
         ('tpm', 'good_provision', 'ext4', 'power', 'rwfw', 'fwstatus',
          'python', 'hwid', 'cros', 'dev_default_boot', 'stop_start_ui',
          'dut_gsctool')),
        (cros_repair.ServoInstallRepair, 'usb', ('usb_drive', ),
         ('ping', 'ssh', 'writable', 'tpm', 'good_provision', 'ext4', 'power',
          'rwfw', 'fwstatus', 'python', 'hwid', 'cros', 'dev_default_boot',
          'stop_start_ui', 'dut_gsctool', 'faft_tpm')),
        (cros_repair.ServoResetAfterUSBRepair, 'servo_reset_after_usb',
         ('usb_drive', ), ('ping', 'ssh')),
        (cros_repair.RecoverFwAfterUSBRepair, 'recover_fw_after_usb',
         ('usb_drive', ), ('ping', 'ssh')),
)

MOBLAB_VERIFY_DAG = (
    (repair_utils.SshVerifier, 'ssh', ()),
    (cros_repair.ACPowerVerifier, 'power', ('ssh',)),
    (cros_repair.PythonVerifier, 'python', ('ssh',)),
    (repair_utils.LegacyHostVerifier, 'cros', ('ssh',)),
)

MOBLAB_REPAIR_ACTIONS = (
    (repair_utils.RPMCycleRepair, 'rpm', (), ('ssh', 'power',)),
    (cros_repair.ProvisionRepair,
     'provision', ('ssh',), ('power', 'python', 'cros',)),
)

JETSTREAM_VERIFY_DAG = (
        (repair_utils.PingVerifier, 'ping', ()),
        (repair_utils.SshVerifier, 'ssh', ('ping', )),
        (cros_repair.ServoUSBDriveVerifier, 'usb_drive', ()),
        (cros_repair.DevDefaultBootVerifier, 'dev_default_boot', ('ssh', )),
        (cros_repair.DevModeVerifier, 'devmode', ('ssh', )),
        (cros_repair.EnrollmentStateVerifier, 'enrollment_state', ('ssh', )),
        (cros_repair.HWIDVerifier, 'hwid', ('ssh', )),
        (cros_repair.ACPowerVerifier, 'power', ('ssh', )),
        (cros_repair.EXT4fsErrorVerifier, 'ext4', ('ssh', )),
        (cros_repair.WritableVerifier, 'writable', ('ssh', )),
        (cros_repair.TPMStatusVerifier, 'tpm', ('ssh', )),
        (cros_repair.UpdateSuccessVerifier, 'good_provision', ('ssh', )),
        (cros_repair.FirmwareTpmVerifier, 'faft_tpm', ('ssh', )),
        (cros_firmware.FirmwareStatusVerifier, 'fwstatus', ('ssh', )),
        (cros_firmware.FirmwareVersionVerifier, 'rwfw', ('ssh', )),
        (cros_repair.PythonVerifier, 'python', ('ssh', )),
        (repair_utils.LegacyHostVerifier, 'cros', ('ssh', )),
        (cros_repair.ProvisioningLabelsVerifier, 'provisioning_labels',
         ('ssh', )),
        (cros_repair.JetstreamTpmVerifier, 'jetstream_tpm', ('ssh', )),
        (cros_repair.JetstreamAttestationVerifier, 'jetstream_attestation',
         ('ssh', )),
        (cros_repair.JetstreamServicesVerifier, 'jetstream_services',
         ('ssh', )),
)

JETSTREAM_REPAIR_ACTIONS = (
        (repair_utils.RPMCycleRepair, 'rpm', (), (
                'ping',
                'ssh',
                'power',
        )),
        (cros_repair.ServoResetRepair, 'servoreset', (), (
                'ping',
                'ssh',
        )),
        (cros_repair.ServoCr50RebootRepair, 'cr50_reset', (), (
                'ping',
                'ssh',
        )),
        (cros_repair.ServoSysRqRepair, 'sysrq', (), (
                'ping',
                'ssh',
        )),
        (cros_repair.ProvisioningLabelsRepair, 'provisioning_labels_repair',
         ('ssh', ), ('provisioning_labels', )),
        (cros_firmware.FaftFirmwareRepair, 'faft_firmware_repair', (),
         ('ping', 'ssh', 'fwstatus', 'good_provision')),
        (cros_repair.DevDefaultBootRepair, 'set_default_boot', ('ssh', ),
         ('dev_default_boot', )),
        (cros_repair.CrosRebootRepair, 'reboot', ('ssh', ), (
                'devmode',
                'writable',
        )),
        (cros_repair.EnrollmentCleanupRepair, 'cleanup_enrollment', ('ssh', ),
         ('enrollment_state', )),
        (cros_repair.JetstreamTpmRepair, 'jetstream_tpm_repair',
         ('ping', 'ssh', 'writable', 'tpm', 'good_provision', 'ext4'),
         ('power', 'rwfw', 'fwstatus', 'python', 'hwid', 'cros',
          'dev_default_boot', 'jetstream_tpm', 'jetstream_attestation')),
        (cros_repair.JetstreamServiceRepair, 'jetstream_service_repair',
         ('ping', 'ssh', 'writable', 'tpm', 'good_provision', 'ext4',
          'jetstream_tpm', 'jetstream_attestation'),
         ('power', 'rwfw', 'fwstatus', 'python', 'hwid', 'cros',
          'dev_default_boot', 'jetstream_tpm', 'jetstream_attestation',
          'jetstream_services')),
        (cros_repair.ProvisionRepair, 'provision',
         ('ping', 'ssh', 'writable', 'tpm', 'good_provision',
          'ext4'), ('power', 'rwfw', 'fwstatus', 'python', 'hwid', 'cros',
                    'dev_default_boot', 'jetstream_tpm',
                    'jetstream_attestation', 'jetstream_services')),
        (cros_repair.PowerWashRepair, 'powerwash', ('ping', 'ssh', 'writable'),
         ('tpm', 'good_provision', 'ext4', 'power', 'rwfw', 'fwstatus',
          'python', 'hwid', 'cros', 'dev_default_boot', 'jetstream_tpm',
          'jetstream_attestation', 'jetstream_services')),
        (cros_repair.ServoInstallRepair, 'usb', ('usb_drive', ), (
                'ping',
                'ssh',
                'writable',
                'tpm',
                'good_provision',
                'ext4',
                'power',
                'rwfw',
                'fwstatus',
                'python',
                'hwid',
                'cros',
                'dev_default_boot',
                'jetstream_tpm',
                'jetstream_attestation',
                'jetstream_services',
                'faft_tpm',
        )),
)

TPM_STATUS_OWNED = """
Message Reply: [tpm_manager.GetTpmNonsensitiveStatusReply] {
  status: STATUS_SUCCESS
  is_enabled: true
  is_owned: true
  is_owner_password_present: true
  has_reset_lock_permissions: true
  is_srk_default_auth: true
}
"""

TPM_STATUS_NOT_OWNED = """
Message Reply: [tpm_manager.GetTpmNonsensitiveStatusReply] {
  status: STATUS_SUCCESS
  is_enabled: true
  is_owned: false
  is_owner_password_present: false
  has_reset_lock_permissions: false
  is_srk_default_auth: true
}
"""

TPM_STATUS_CANNOT_LOAD_SRK = """
Message Reply: [tpm_manager.GetTpmNonsensitiveStatusReply] {
  status: STATUS_SUCCESS
  is_enabled: true
  is_owned: true
  is_owner_password_present: false
  has_reset_lock_permissions: false
  is_srk_default_auth: false
}
"""

TPM_STATUS_READY = """
TPM Enabled: true
TPM Owned: true
TPM Being Owned: false
TPM Ready: true
TPM Password: 9eaee4da8b4c
"""

TPM_STATUS_NOT_READY = """
TPM Enabled: true
TPM Owned: false
TPM Being Owned: true
TPM Ready: false
TPM Password:
"""


class CrosRepairUnittests(unittest.TestCase):
    # pylint: disable=missing-docstring

    maxDiff = None

    def test_cros_repair(self):
        verify_dag = cros_repair._cros_verify_dag()
        self.assertTupleEqual(verify_dag, CROS_VERIFY_DAG)
        self.check_verify_dag(verify_dag)
        repair_actions = cros_repair._cros_repair_actions()
        self.assertTupleEqual(repair_actions, CROS_REPAIR_ACTIONS)
        self.check_repair_actions(verify_dag, repair_actions)

    def test_moblab_repair(self):
        verify_dag = cros_repair._moblab_verify_dag()
        self.assertTupleEqual(verify_dag, MOBLAB_VERIFY_DAG)
        self.check_verify_dag(verify_dag)
        repair_actions = cros_repair._moblab_repair_actions()
        self.assertTupleEqual(repair_actions, MOBLAB_REPAIR_ACTIONS)
        self.check_repair_actions(verify_dag, repair_actions)

    def test_jetstream_repair(self):
        verify_dag = cros_repair._jetstream_verify_dag()
        self.assertTupleEqual(verify_dag, JETSTREAM_VERIFY_DAG)
        self.check_verify_dag(verify_dag)
        repair_actions = cros_repair._jetstream_repair_actions()
        self.assertTupleEqual(repair_actions, JETSTREAM_REPAIR_ACTIONS)
        self.check_repair_actions(verify_dag, repair_actions)

    def check_verify_dag(self, verify_dag):
        """Checks that dependency labels are defined."""
        labels = [n[1] for n in verify_dag]
        for node in verify_dag:
            for dep in node[2]:
                self.assertIn(dep, labels)

    def check_repair_actions(self, verify_dag, repair_actions):
        """Checks that dependency and trigger labels are defined."""
        verify_labels = [n[1] for n in verify_dag]
        for action in repair_actions:
            deps = action[2]
            triggers = action[3]
            for label in deps + triggers:
                self.assertIn(label, verify_labels)

    def test_get_tpm_status_owned(self):
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_OWNED
        status = cros_repair.TpmStatus(mock_host)
        self.assertTrue(status.tpm_enabled)
        self.assertTrue(status.tpm_owned)
        self.assertTrue(status.tpm_can_load_srk)
        self.assertTrue(status.tpm_can_load_srk_pubkey)

    def test_get_tpm_status_not_owned(self):
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_NOT_OWNED
        status = cros_repair.TpmStatus(mock_host)
        self.assertTrue(status.tpm_enabled)
        self.assertFalse(status.tpm_owned)
        self.assertFalse(status.tpm_can_load_srk)
        self.assertFalse(status.tpm_can_load_srk_pubkey)

    @mock.patch.object(cros_repair, '_is_virtual_machine')
    def test_tpm_status_verifier_owned(self, mock_is_virt):
        mock_is_virt.return_value = False
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_OWNED
        tpm_verifier = cros_repair.TPMStatusVerifier('test', [])
        tpm_verifier.verify(mock_host)

    @mock.patch.object(cros_repair, '_is_virtual_machine')
    def test_tpm_status_verifier_not_owned(self, mock_is_virt):
        mock_is_virt.return_value = False
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_NOT_OWNED
        tpm_verifier = cros_repair.TPMStatusVerifier('test', [])
        tpm_verifier.verify(mock_host)

    @mock.patch.object(cros_repair, '_is_virtual_machine')
    def test_tpm_status_verifier_cannot_load_srk_pubkey(self, mock_is_virt):
        mock_is_virt.return_value = False
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_CANNOT_LOAD_SRK
        tpm_verifier = cros_repair.TPMStatusVerifier('test', [])
        with self.assertRaises(hosts.AutoservVerifyError) as ctx:
            tpm_verifier.verify(mock_host)
        self.assertEqual('Cannot load the TPM SRK', str(ctx.exception))

    def test_jetstream_tpm_owned(self):
        mock_host = mock.Mock()
        mock_host.run.side_effect = [
                mock.Mock(stdout=TPM_STATUS_OWNED),
                mock.Mock(stdout=TPM_STATUS_READY),
        ]
        tpm_verifier = cros_repair.JetstreamTpmVerifier('test', [])
        tpm_verifier.verify(mock_host)

    @mock.patch.object(retry.logging, 'warning')
    @mock.patch.object(retry.time, 'time')
    @mock.patch.object(retry.time, 'sleep')
    def test_jetstream_tpm_not_owned(self, mock_sleep, mock_time, mock_logging):
        mock_time.side_effect = itertools.count(0, 20)
        mock_host = mock.Mock()
        mock_host.run.return_value.stdout = TPM_STATUS_NOT_OWNED
        tpm_verifier = cros_repair.JetstreamTpmVerifier('test', [])
        with self.assertRaises(hosts.AutoservVerifyError) as ctx:
            tpm_verifier.verify(mock_host)
        self.assertEqual('TPM is not owned', str(ctx.exception))

    @mock.patch.object(retry.logging, 'warning')
    @mock.patch.object(retry.time, 'time')
    @mock.patch.object(retry.time, 'sleep')
    def test_jetstream_tpm_not_ready(self, mock_sleep, mock_time, mock_logging):
        mock_time.side_effect = itertools.count(0, 20)
        mock_host = mock.Mock()
        mock_host.run.side_effect = itertools.cycle([
                mock.Mock(stdout=TPM_STATUS_OWNED),
                mock.Mock(stdout=TPM_STATUS_NOT_READY),
        ])
        tpm_verifier = cros_repair.JetstreamTpmVerifier('test', [])
        with self.assertRaises(hosts.AutoservVerifyError) as ctx:
            tpm_verifier.verify(mock_host)
        self.assertEqual('TPM is not ready', str(ctx.exception))

    @mock.patch.object(retry.logging, 'warning')
    @mock.patch.object(retry.time, 'time')
    @mock.patch.object(retry.time, 'sleep')
    def test_jetstream_tpm_missing(self, mock_sleep, mock_time, mock_logging):
        mock_time.side_effect = itertools.count(0, 20)
        mock_host = mock.Mock()
        mock_host.run.side_effect = error.AutoservRunError('test', None)
        tpm_verifier = cros_repair.JetstreamTpmVerifier('test', [])
        with self.assertRaises(hosts.AutoservVerifyError) as ctx:
            tpm_verifier.verify(mock_host)
        self.assertEqual('Could not determine TPM status', str(ctx.exception))


if __name__ == '__main__':
    unittest.main()
