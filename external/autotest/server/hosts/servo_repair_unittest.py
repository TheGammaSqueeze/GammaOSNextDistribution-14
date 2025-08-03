#!/usr/bin/python3
# Copyright 2021 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

# pylint: disable=missing-docstring

import unittest
from unittest import mock

import common
from autotest_lib.server.hosts import servo_repair
from autotest_lib.server.hosts import repair_utils

SERVO_VERIFY_DAG = (
        (servo_repair._ConnectionVerifier, 'connection', []),
        (servo_repair._RootServoPresentVerifier, 'servo_root_present',
         ['connection']),
        (servo_repair._RootServoV3PresentVerifier, 'servo_v3_root_present',
         ['connection']),
        (servo_repair._ServoFwVerifier, 'servo_fw', ['servo_root_present']),
        (servo_repair._StartServodVerifier, 'start_servod',
         ['servo_fw', 'servo_v3_root_present']),
        (servo_repair._DiskSpaceVerifier, 'servo_disk_space', ['connection']),
        (servo_repair._UpdateVerifier, 'servo_update',
         ['servo_v3_root_present']),
        (servo_repair._BoardConfigVerifier, 'servo_config_board',
         ['connection']),
        (servo_repair._SerialConfigVerifier, 'servo_config_serial',
         ['connection']),
        (servo_repair._ServodJobVerifier, 'servod_started', [
                'start_servod', 'servo_config_board', 'servo_config_serial',
                'servo_disk_space'
        ]),
        (servo_repair._ServodEchoVerifier, 'servod_echo', ['servod_started']),
        (servo_repair._TopologyVerifier, 'servo_topology', ['servod_echo']),
        (servo_repair._ServodConnectionVerifier, 'servod_connection',
         ['servod_echo']),
        (servo_repair._Cr50LowSBUVerifier, 'servo_cr50_low_sbu',
         ['servod_connection']),
        (servo_repair.ServodDutControllerMissingVerifier,
         'servod_dut_controller_missing', ['servod_connection']),
        (servo_repair._Cr50OffVerifier, 'servo_cr50_off',
         ['servod_connection']),
        (servo_repair._ServodControlVerifier, 'servod_control',
         ['servod_connection']),
        (servo_repair._DUTConnectionVerifier, 'servo_dut_connected',
         ['servod_connection']),
        (servo_repair._ServoHubConnectionVerifier, 'servo_hub_connected',
         ['servo_dut_connected']),
        (servo_repair._PowerButtonVerifier, 'servo_pwr_button',
         ['servo_hub_connected']),
        (servo_repair._BatteryVerifier, 'servo_battery',
         ['servo_hub_connected']),
        (servo_repair._LidVerifier, 'servo_lid_open', ['servo_hub_connected']),
        (servo_repair.ECConsoleVerifier, 'servo_ec_console',
         ['servo_dut_connected']),
        (servo_repair._Cr50ConsoleVerifier, 'servo_cr50_console',
         ['servo_dut_connected']),
        (servo_repair._CCDTestlabVerifier, 'servo_ccd_testlab',
         ['servo_cr50_console']),
        (servo_repair._CCDPowerDeliveryVerifier, 'servo_power_delivery',
         ['servod_connection']),
)

SERVO_REPAIR_ACTIONS = (
        (servo_repair._ServoFwUpdateRepair, 'servo_fw_update', ['connection'],
         ['servo_fw']),
        (servo_repair._DiskCleanupRepair, 'servo_disk_cleanup', ['connection'],
         ['servo_disk_space']),
        (servo_repair._ServoMicroFlashRepair, 'servo_micro_flash',
         ['connection', 'servo_topology'], ['servo_dut_connected']),
        (servo_repair._RestartServod, 'servod_restart',
         ['connection', 'servo_fw'], [
                 'servo_config_board', 'servo_config_serial', 'start_servod',
                 'servod_started', 'servo_topology', 'servod_connection',
                 'servod_echo', 'servod_control', 'servo_dut_connected',
                 'servo_hub_connected', 'servo_pwr_button',
                 'servo_cr50_console', 'servo_cr50_low_sbu', 'servo_cr50_off',
                 'servo_power_delivery', 'servod_dut_controller_missing'
         ]),
        (servo_repair._ServoRebootRepair, 'servo_reboot', ['connection'], [
                'servo_topology', 'servo_root_present', 'servo_disk_space',
                'servo_power_delivery'
        ]),
        (servo_repair._PowerDeliveryRepair, 'servo_pd_recover',
         ['servod_connection'], [
                 'servod_started', 'servo_topology', 'servod_connection',
                 'servod_echo', 'servod_control', 'servo_dut_connected',
                 'servo_hub_connected', 'servo_pwr_button',
                 'servo_cr50_console', 'servo_cr50_low_sbu', 'servo_cr50_off',
                 'servo_power_delivery', 'servod_dut_controller_missing'
         ]),
        (servo_repair._FakedisconnectRepair, 'servo_fakedisconnect',
         ['servod_connection'], [
                 'servod_started', 'servo_topology', 'servod_connection',
                 'servod_echo', 'servod_control', 'servo_dut_connected',
                 'servo_hub_connected', 'servo_pwr_button',
                 'servo_cr50_console', 'servo_cr50_low_sbu', 'servo_cr50_off',
                 'servo_power_delivery', 'servod_dut_controller_missing'
         ]),
        (servo_repair._ToggleCCLineRepair, 'servo_cc', ['servod_connection'], [
                'servod_started', 'servo_topology', 'servod_connection',
                'servod_echo', 'servod_control', 'servo_dut_connected',
                'servo_hub_connected', 'servo_pwr_button',
                'servo_cr50_console', 'servo_cr50_low_sbu', 'servo_cr50_off',
                'servo_power_delivery', 'servod_dut_controller_missing'
        ]),
        (servo_repair._DutRebootRepair, 'servo_dut_reboot',
         ['servod_connection'], [
                 'servod_control', 'servo_lid_open', 'servo_ec_console',
                 'servo_topology', 'servo_dut_connected',
                 'servo_hub_connected', 'servo_cr50_low_sbu', 'servo_cr50_off',
                 'servo_cr50_console', 'servo_power_delivery',
                 'servod_dut_controller_missing'
         ]),
        (servo_repair._ECRebootRepair, 'servo_ec_reboot',
         ['servod_connection'], [
                 'servod_control', 'servo_lid_open', 'servo_ec_console',
                 'servo_topology', 'servo_dut_connected',
                 'servo_hub_connected', 'servo_cr50_low_sbu', 'servo_cr50_off',
                 'servo_cr50_console', 'servo_power_delivery',
                 'servod_dut_controller_missing'
         ]),
)


class ServoRepairUnittests(unittest.TestCase):

    # Allow to show all diff when compare tuple.
    maxDiff = None

    def test_servo_repair_components(self):
        verify_dag = servo_repair._servo_verifier_actions()
        self.assertTupleEqual(verify_dag, SERVO_VERIFY_DAG)
        self.check_verify_dag(verify_dag)
        repair_actions = servo_repair._servo_repair_actions()
        self.assertTupleEqual(repair_actions, SERVO_REPAIR_ACTIONS)
        self.check_repair_actions(verify_dag, repair_actions)

    def test_servo_repair_strategy(self):
        servo_repair.create_servo_repair_strategy()

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


if __name__ == '__main__':
    unittest.main()
