#!/usr/bin/env python3
# -*- coding: utf-8 -*-
#
# Copyright 2020 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""
Unittest for device_setup_utils.

"""

from __future__ import print_function

import logging
import time
import unittest
from unittest import mock

import common
from autotest_lib.server import hosts
from autotest_lib.server.cros.crosperf import device_setup_utils

BIG_LITTLE_CPUINFO = """processor       : 0
model name      : ARMv8 Processor rev 4 (v8l)
BogoMIPS        : 48.00
Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 8
CPU variant     : 0x0
CPU part        : 0xd03
CPU revision    : 4

processor       : 1
model name      : ARMv8 Processor rev 4 (v8l)
BogoMIPS        : 48.00
Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 8
CPU variant     : 0x0
CPU part        : 0xd03
CPU revision    : 4

processor       : 2
model name      : ARMv8 Processor rev 2 (v8l)
BogoMIPS        : 48.00
Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 8
CPU variant     : 0x0
CPU part        : 0xd08
CPU revision    : 2
"""
LITTLE_ONLY_CPUINFO = """processor       : 0
model name      : ARMv8 Processor rev 4 (v8l)
BogoMIPS        : 48.00
Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 8
CPU variant     : 0x0
CPU part        : 0xd03
CPU revision    : 4

processor       : 1
model name      : ARMv8 Processor rev 4 (v8l)
BogoMIPS        : 48.00
Features        : half thumb fastmult vfp edsp neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 8
CPU variant     : 0x0
CPU part        : 0xd03
CPU revision    : 4
"""

NOT_BIG_LITTLE_CPUINFO = """processor       : 0
model name      : ARMv7 Processor rev 1 (v7l)
Features        : swp half thumb fastmult vfp edsp thumbee neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 7
CPU variant     : 0x0
CPU part        : 0xc0d
CPU revision    : 1

processor       : 1
model name      : ARMv7 Processor rev 1 (v7l)
Features        : swp half thumb fastmult vfp edsp thumbee neon vfpv3 tls vfpv4
CPU implementer : 0x41
CPU architecture: 7
CPU variant     : 0x0
CPU part        : 0xc0d
CPU revision    : 1

Hardware        : Rockchip (Device Tree)
Revision        : 0000
Serial          : 0000000000000000
"""


class device_setup_utilsTest(unittest.TestCase):
    """
    Class of device setup utils test.
    """

    def __init__(self, *args, **kwargs):
        super(device_setup_utilsTest, self).__init__(*args, **kwargs)

        self.dut = mock.Mock(spec=hosts.create_host)
        self.dut.hostname = 'lumpy.cros2'
        self.dut.run = mock.Mock()

        logging.error = mock.Mock()
        logging.warning = mock.Mock()


    def test_run_command_on_dut(self):
        self.dut.run.return_value.exit_status = 0
        self.dut.run.return_value.stdout = ''
        self.dut.run.return_value.stderr = ''

        self.dut.run.assert_not_called()
        device_setup_utils.run_command_on_dut(self.dut, 'run command;')
        self.dut.run.assert_called_once_with(
                'run command;', ignore_status=False)


    def test_run_command_on_dut_fatal_error(self):
        # Command returns error 1.
        self.dut.run.return_value.exit_status = 1
        self.dut.run.return_value.stdout = ''
        self.dut.run.return_value.stderr = 'Error!'

        self.dut.run.assert_not_called()
        device_setup_utils.run_command_on_dut(self.dut, 'run command;')
        self.dut.run.assert_called_once_with(
                'run command;', ignore_status=False)
        # Error status causes log fatal.
        logging.error.assert_called_once_with(
                'Command execution on DUT lumpy.cros2 failed.\n'
                'Failing command: run command;\nreturned 1\n'
                'Error message: Error!')


    def test_run_command_on_dut_ignore_error(self):
        # Command returns error 1.
        self.dut.run.return_value.exit_status = 1
        self.dut.run.return_value.stdout = ''
        self.dut.run.return_value.stderr = 'Error!'

        self.dut.run.assert_not_called()
        device_setup_utils.run_command_on_dut(
                self.dut, 'run command;', ignore_status=True)
        self.dut.run.assert_called_once_with(
                'run command;', ignore_status=True)
        # Error status causes log fatal.
        calls = [
                mock.call('Command execution on DUT lumpy.cros2 failed.\n'
                          'Failing command: run command;\nreturned 1\n'
                          'Error message: Error!'),
                mock.call('Failure is considered non-fatal. Continue.'),
        ]
        logging.warning.assert_has_calls(calls)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_disable_aslr(self, mock_run_command):
        mock_run_command.return_value = (0, '', '')
        device_setup_utils.disable_aslr(self.dut)
        # pyformat: disable
        set_cpu_cmd = ('set -e; '
                       'if [[ -e /proc/sys/kernel/randomize_va_space ]]; then '
                       '  echo 0 > /proc/sys/kernel/randomize_va_space; '
                       'fi')
        mock_run_command.assert_called_once_with(self.dut, set_cpu_cmd)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_set_cpu_governor(self, mock_run_command):
        mock_run_command.return_value = (0, '', '')
        device_setup_utils.set_cpu_governor(
                self.dut, 'new_governor', ignore_status=False)
        set_cpu_cmd = (
                'for f in `ls -d /sys/devices/system/cpu/cpu*/cpufreq 2>/dev/null`; do'
                # Skip writing scaling_governor if cpu is offline.
                '  [[ -e ${f/cpufreq/online} ]] && grep -q 0 ${f/cpufreq/online} '
                '   && continue; '
                ' cd $f; '
                ' if [[ -e scaling_governor ]]; then '
                '  echo %s > scaling_governor; fi; '
                'done; ')
        mock_run_command.assert_called_once_with(
                self.dut, set_cpu_cmd % 'new_governor', ignore_status=False)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_set_cpu_governor_propagate_error(self, mock_run_command):
        mock_run_command.return_value = (1, '', 'Error.')
        device_setup_utils.set_cpu_governor(self.dut, 'non-exist_governor')
        set_cpu_cmd = (
                'for f in `ls -d /sys/devices/system/cpu/cpu*/cpufreq 2>/dev/null`; do'
                # Skip writing scaling_governor if cpu is not online.
                '  [[ -e ${f/cpufreq/online} ]] && grep -q 0 ${f/cpufreq/online} '
                '   && continue; '
                ' cd $f; '
                ' if [[ -e scaling_governor ]]; then '
                '  echo %s > scaling_governor; fi; '
                'done; ')
        # By default error status is fatal.
        mock_run_command.assert_called_once_with(
                self.dut,
                set_cpu_cmd % 'non-exist_governor',
                ignore_status=False)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_set_cpu_governor_ignore_status(self, mock_run_command):
        mock_run_command.return_value = (1, '', 'Error.')
        ret_code = device_setup_utils.set_cpu_governor(
                self.dut, 'non-exist_governor', ignore_status=True)
        set_cpu_cmd = (
                'for f in `ls -d /sys/devices/system/cpu/cpu*/cpufreq 2>/dev/null`; do'
                # Skip writing scaling_governor if cpu is not online.
                '  [[ -e ${f/cpufreq/online} ]] && grep -q 0 ${f/cpufreq/online} '
                '   && continue; '
                ' cd $f; '
                ' if [[ -e scaling_governor ]]; then '
                '  echo %s > scaling_governor; fi; '
                'done; ')
        mock_run_command.assert_called_once_with(
                self.dut,
                set_cpu_cmd % 'non-exist_governor',
                ignore_status=True)
        self.assertEqual(ret_code, 1)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_disable_turbo(self, mock_run_command):
        mock_run_command.return_value = (0, '', '')
        device_setup_utils.disable_turbo(self.dut)
        set_cpu_cmd = (
                # Disable Turbo the in Intel pstate driver.
                'if [[ -e /sys/devices/system/cpu/intel_pstate/no_turbo ]]; then '
                '  if grep -q 0 /sys/devices/system/cpu/intel_pstate/no_turbo;  then '
                '    echo -n 1 > /sys/devices/system/cpu/intel_pstate/no_turbo; '
                '  fi; '
                'fi; '
                # Disable Boost on AMD.
                'if [[ -e /sys/devices/system/cpu/cpufreq/boost ]]; then '
                '  if grep -q 1 /sys/devices/system/cpu/cpufreq/boost;  then '
                '    echo -n 0 > /sys/devices/system/cpu/cpufreq/boost; '
                '  fi; '
                'fi; '
        )
        mock_run_command.assert_called_once_with(self.dut, set_cpu_cmd)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_get_cpu_online_two(self, mock_run_command):
        """Test one digit CPU #."""
        mock_run_command.return_value = (
                0, '/sys/devices/system/cpu/cpu0/online 0\n'
                '/sys/devices/system/cpu/cpu1/online 1\n', '')
        cpu_online = device_setup_utils.get_cpu_online(self.dut)
        self.assertEqual(cpu_online, {0: 0, 1: 1})


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_get_cpu_online_twelve(self, mock_run_command):
        """Test two digit CPU #."""
        mock_run_command.return_value = (
                0, '/sys/devices/system/cpu/cpu0/online 1\n'
                '/sys/devices/system/cpu/cpu1/online 0\n'
                '/sys/devices/system/cpu/cpu10/online 1\n'
                '/sys/devices/system/cpu/cpu11/online 1\n'
                '/sys/devices/system/cpu/cpu2/online 1\n'
                '/sys/devices/system/cpu/cpu3/online 0\n'
                '/sys/devices/system/cpu/cpu4/online 1\n'
                '/sys/devices/system/cpu/cpu5/online 0\n'
                '/sys/devices/system/cpu/cpu6/online 1\n'
                '/sys/devices/system/cpu/cpu7/online 0\n'
                '/sys/devices/system/cpu/cpu8/online 1\n'
                '/sys/devices/system/cpu/cpu9/online 0\n', '')
        cpu_online = device_setup_utils.get_cpu_online(self.dut)
        self.assertEqual(
                cpu_online, {
                        0: 1,
                        1: 0,
                        2: 1,
                        3: 0,
                        4: 1,
                        5: 0,
                        6: 1,
                        7: 0,
                        8: 1,
                        9: 0,
                        10: 1,
                        11: 1
                })


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_get_cpu_online_core0_not_exposed(self, mock_run_command):
        """Test that not exposed cpu0/online will still be in the list."""

        def run_command(dut, cmd):
            """Helper function."""
            if '/sys/devices/system/cpu/cpu' in cmd:
                # Cpu0 online is not exposed.
                return (0, '/sys/devices/system/cpu/cpu1/online 1\n', '')
            elif '/sys/devices/system/cpu/online' in cmd:
                # All online cores shows cpu0.
                return (0, '0-1', '')
            else:
                return (1, '', '')

        mock_run_command.side_effect = run_command
        cpu_online = device_setup_utils.get_cpu_online(self.dut)
        # Make sure core0 in the online list.
        self.assertEqual(cpu_online, {0: 1, 1: 1})


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_get_cpu_online_no_output(self, mock_run_command):
        """Test error case, no output."""
        mock_run_command.return_value = (0, '', '')
        with self.assertRaises(AssertionError):
            device_setup_utils.get_cpu_online(self.dut)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_get_cpu_online_command_error(self, mock_run_command):
        """Test error case, command error."""
        mock_run_command.side_effect = AssertionError
        with self.assertRaises(AssertionError):
            device_setup_utils.get_cpu_online(self.dut)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(device_setup_utils, 'setup_arm_cores')
    def test_setup_cpu_usage_little_on_arm(self, mock_setup_arm,
                                           mock_run_command):
        device_setup_utils.setup_arm_cores = mock_setup_arm
        mock_run_command.return_value = (0, 'armv7l', '')
        cpu_usage = 'little_only'
        device_setup_utils.setup_cpu_usage(self.dut, cpu_usage)
        mock_setup_arm.assert_called_once()


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(device_setup_utils, 'setup_arm_cores')
    def test_setup_cpu_usage_big_on_aarch64(self, mock_setup_arm,
                                            mock_run_command):
        device_setup_utils.setup_arm_cores = mock_setup_arm
        mock_run_command.return_value = (0, 'aarch64', '')
        cpu_usage = 'big_only'
        device_setup_utils.setup_cpu_usage(self.dut, cpu_usage)
        mock_setup_arm.assert_called_once()


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(device_setup_utils, 'setup_arm_cores')
    def test_setup_cpu_usage_big_on_intel(self, mock_setup_arm,
                                          mock_run_command):
        device_setup_utils.setup_arm_cores = mock_setup_arm
        mock_run_command.return_value = (0, 'x86_64', '')
        cpu_usage = 'big_only'
        device_setup_utils.setup_cpu_usage(self.dut, cpu_usage)
        # Check that setup_arm_cores not called with invalid setup.
        mock_setup_arm.assert_not_called()


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(device_setup_utils, 'setup_arm_cores')
    def test_setup_cpu_usage_all_on_intel(self, mock_setup_arm,
                                          mock_run_command):
        device_setup_utils.setup_arm_cores = mock_setup_arm
        mock_run_command.return_value = (0, 'x86_64', '')
        cpu_usage = 'all'
        device_setup_utils.setup_cpu_usage(self.dut, cpu_usage)
        # Check that setup_arm_cores not called in general case.
        mock_setup_arm.assert_not_called()


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_arm_cores_big_on_big_little(self, mock_run_command):
        mock_run_command.side_effect = [
                (0, BIG_LITTLE_CPUINFO, ''),
                (0, '', ''),
        ]
        cpu_usage = 'big_only'
        device_setup_utils.setup_arm_cores(self.dut, cpu_usage)
        mock_run_command.assert_called_with(
                self.dut,
                'echo 1 | tee /sys/devices/system/cpu/cpu{2}/online; '
                'echo 0 | tee /sys/devices/system/cpu/cpu{0,1}/online')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_arm_cores_little_on_big_little(self, mock_run_command):
        mock_run_command.side_effect = [
                (0, BIG_LITTLE_CPUINFO, ''),
                (0, '', ''),
        ]
        cpu_usage = 'little_only'
        device_setup_utils.setup_arm_cores(self.dut, cpu_usage)
        mock_run_command.assert_called_with(
                self.dut,
                'echo 1 | tee /sys/devices/system/cpu/cpu{0,1}/online; '
                'echo 0 | tee /sys/devices/system/cpu/cpu{2}/online')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_arm_cores_invalid_config(self, mock_run_command):
        mock_run_command.side_effect = [
                (0, LITTLE_ONLY_CPUINFO, ''),
                (0, '', ''),
        ]
        cpu_usage = 'big_only'
        device_setup_utils.setup_arm_cores(self.dut, cpu_usage)
        # Check that setup command is not sent when trying
        # to use 'big_only' on a platform with all little cores.
        mock_run_command.assert_called_once_with(self.dut, 'cat /proc/cpuinfo')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_arm_cores_not_big_little(self, mock_run_command):
        mock_run_command.side_effect = [
                (0, NOT_BIG_LITTLE_CPUINFO, ''),
                (0, '', ''),
        ]
        cpu_usage = 'big_only'
        device_setup_utils.setup_arm_cores(self.dut, cpu_usage)
        # Check that setup command is not sent when trying
        # to use 'big_only' on a platform w/o support of big/little.
        mock_run_command.assert_called_once_with(self.dut, 'cat /proc/cpuinfo')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_arm_cores_unsupported_cpu_usage(self, mock_run_command):
        mock_run_command.side_effect = [
                (0, BIG_LITTLE_CPUINFO, ''),
                (0, '', ''),
        ]
        cpu_usage = 'exclusive_cores'
        device_setup_utils.setup_arm_cores(self.dut, cpu_usage)
        # Check that setup command is not sent when trying to use
        # 'exclusive_cores' on ARM CPU setup.
        mock_run_command.assert_called_once_with(self.dut, 'cat /proc/cpuinfo')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_single_full(self, mock_run_command):
        online = [0]
        mock_run_command.side_effect = [
                (0,
                 '/sys/devices/system/cpu/cpu0/cpufreq/'
                 'scaling_available_frequencies\n',
                 ''),
                (0, '1 2 3 4 5 6 7 8 9 10', ''),
                (0, '', ''),
        ]
        cpu_frq_pct = 100
        device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)
        self.assertGreaterEqual(mock_run_command.call_count, 3)
        self.assertIn(
                mock.call(
                        self.dut, 'echo 10 | tee '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq'
                ),
                mock_run_command.call_args_list)
        # Check the fix of single core online.
        # We expect to see cpu0 instead of cpu{0}.
        self.assertIn(
                mock.call(self.dut,
                          'ls /sys/devices/system/cpu/cpu0/cpufreq/'
                          'scaling_available_frequencies',
                          ignore_status=True),
                mock_run_command.call_args_list)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_middle(self, mock_run_command):
        online = [0]
        mock_run_command.side_effect = [
                (0,
                 '/sys/devices/system/cpu/cpu0/cpufreq/'
                 'scaling_available_frequencies\n',
                 ''),
                (0, '1 2 3 4 5 6 7 8 9 10', ''),
                (0, '', ''),
        ]
        cpu_frq_pct = 60
        device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)
        self.assertGreaterEqual(mock_run_command.call_count, 2)
        self.assertEqual(
                mock_run_command.call_args,
                mock.call(
                        self.dut, 'echo 6 | tee '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq'
                ))


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_lowest(self, mock_run_command):
        online = [0]
        mock_run_command.side_effect = [
                (0,
                 '/sys/devices/system/cpu/cpu0/cpufreq/'
                 'scaling_available_frequencies\n',
                 ''),
                (0, '1 2 3 4 5 6 7 8 9 10', ''),
                (0, '', ''),
        ]
        cpu_frq_pct = 0
        device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)
        self.assertGreaterEqual(mock_run_command.call_count, 2)
        self.assertEqual(
                mock_run_command.call_args,
                mock.call(
                        self.dut, 'echo 1 | tee '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq'
                ))


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_multiple_middle(self, mock_run_command):
        online = [0, 1]
        mock_run_command.side_effect = [
                (0,
                 '/sys/devices/system/cpu/cpu0/cpufreq/'
                 'scaling_available_frequencies\n'
                 '/sys/devices/system/cpu/cpu1/cpufreq/'
                 'scaling_available_frequencies\n',
                 ''),
                (0, '1 2 3 4 5 6 7 8 9 10', ''),
                (0, '', ''),
                (0, '1 4 6 8 10 12 14 16 18 20', ''),
                (0, '', ''),
        ]
        cpu_frq_pct = 70
        device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)
        self.assertEqual(mock_run_command.call_count, 5)
        self.assertIn(
                mock.call(
                        self.dut, 'echo 7 | tee '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_max_freq '
                        '/sys/devices/system/cpu/cpu0/cpufreq/scaling_min_freq'
                ),
                mock_run_command.call_args_list)
        self.assertIn(
                mock.call(
                        self.dut, 'echo 14 | tee '
                        '/sys/devices/system/cpu/cpu1/cpufreq/scaling_max_freq '
                        '/sys/devices/system/cpu/cpu1/cpufreq/scaling_min_freq'
                ),
                mock_run_command.call_args_list)
        # Check that setup_cpu_freq queries all online cores.
        self.assertIn(
                mock.call(self.dut,
                          'ls /sys/devices/system/cpu/cpu{0,1}/cpufreq/'
                          'scaling_available_frequencies',
                          ignore_status=True),
                mock_run_command.call_args_list)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_no_scaling_available(self, mock_run_command):
        online = [0, 1]
        mock_run_command.return_value = (2, '', 'No such file or directory')
        cpu_frq_pct = 50
        device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)
        mock_run_command.assert_called_once()
        self.assertNotRegexpMatches(mock_run_command.call_args_list[0][0][1],
                                    '^echo.*scaling_max_freq$')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_setup_cpu_freq_multiple_no_access(self, mock_run_command):
        online = [0, 1]
        mock_run_command.side_effect = [
                (0,
                 '/sys/devices/system/cpu/cpu0/cpufreq/'
                 'scaling_available_frequencies\n'
                 '/sys/devices/system/cpu/cpu1/cpufreq/'
                 'scaling_available_frequencies\n',
                 ''),
                (0, '1 4 6 8 10 12 14 16 18 20', ''),
                AssertionError(),
        ]
        cpu_frq_pct = 30
        # Error status causes log fatal.
        with self.assertRaises(AssertionError):
            device_setup_utils.setup_cpu_freq(self.dut, cpu_frq_pct, online)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_nowait(self, mock_sleep, mock_run_command):
        """
        No cooldown wait.

        Don't wait when the temperature in uC does not exceed 40C.
        """
        mock_sleep.return_value = 0
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp', ''),
                (0, 'cpu', ''),
                (0, '39000', ''),
        ]
        cooldown_time = 10
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        # Expect no wait time.
        mock_sleep.assert_not_called()
        self.assertEqual(wait_time, 0)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_needwait_once(self, mock_sleep, mock_run_command):
        """
        Wait one iteration for cooldown.

        Set large enough timeout and changing temperature output.
        Make sure it exits when expected value received.
        """
        mock_sleep.return_value = 0
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp', ''),
                (0, 'cpu', ''),
                (0, '41000', ''),
                (0, '39000', ''),
        ]
        cooldown_time = 100
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        # Wait time is non-zero.
        mock_sleep.assert_called()
        self.assertGreater(wait_time, 0)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_space_in_thermal_name(self, mock_sleep,
                                                 mock_run_command):
        """
        Wait one iteration for cooldown.

        Make sure the cooldown is working properly when there is a space
        in the sensor type name.
        """
        mock_sleep.return_value = 0
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp\n'
                 '/sys/class/thermal/thermal_zone1/temp', ''),
                (0, 'cpu\ngpu thermal', ''),
                (0, '39000', ''),
                (0, '41000', ''),
                (0, '38000', ''),
        ]
        cooldown_time = 10
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        # Expect no wait time.
        mock_sleep.assert_called_once()
        self.assertGreater(wait_time, 0)

    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_wait_timeout(self, mock_sleep, mock_run_command):
        """
        Test exit by timeout.

        Send command to DUT checking the temperature and
        check repeatedly until timeout goes off.
        Output from temperature sensor never changes.

        """

        def constant_temp(temp):
            """Helper function returns gradually decreasing temperature."""
            yield (0, '/sys/class/thermal/thermal_zone0/temp', '')
            yield (0, 'cpu', '')
            while True:
                yield (0, str(temp), '')

        mock_sleep.return_value = 0
        # Set the temperature higher than a default threshold 40k.
        mock_run_command.side_effect = constant_temp(41000)
        # Cooldown time - 5 minutes.
        cooldown_time = 5
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        mock_sleep.assert_called()
        # Convert cooldown_time to seconds.
        self.assertEqual(wait_time, cooldown_time * 60)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_needwait_multtemp(self, mock_sleep,
                                             mock_run_command):
        """
        Wait until all temps go down.

        Set large enough timeout and changing temperature
        output. Make sure it exits when expected value
        for all temperatures received.
        """
        mock_sleep.return_value = 0
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp\n'
                 '/sys/class/thermal/thermal_zone1/temp\n'
                 '/sys/class/thermal/thermal_zone2/temp', ''),
                (0, 'cpu0\ncpu1\ngpu', ''),
                # Iteration 1 of monitoring.
                (0, '45000', ''),
                (0, '41000', ''),
                (0, '20000', ''),
                # Iteration 2 of monitoring.
                (0, '42000', ''),
                (0, '39000', ''),
                # Iteration 3 of monitoring.
                (0, '38000', ''),
                # Monitoring ends.
        ]
        cooldown_time = 100
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        # Wait time is non-zero.
        mock_sleep.assert_called()
        self.assertGreater(wait_time, 0)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_ignore_irrelevant_sensor(self, mock_sleep,
                                                    mock_run_command):
        """
        Ignore non cpu/gpu sensors.

        Set large temperature of a non-cpu sensor.
        Make sure we don't wait if cpu temperature is low
        regardless of other reports.
        """
        mock_sleep.return_value = 0
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp\n'
                 '/sys/class/thermal/thermal_zone1/temp', ''),
                (0, 'cpu0\ncharger-sensor', ''),
                # Iteration 1 of monitoring, check only cpu0.
                # cpu0
                (0, '39000', ''),
                # Monitoring should stop at this point since the other
                # sensor is irrelevant.
                # If it doesn't, the test will fail since the function
                # will continue monitoring until 50C drops but there is
                # no more input.
                (0, '50000', ''),
        ]
        cooldown_time = 100
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        mock_run_command.assert_called()
        # Wait time is zero.
        mock_sleep.assert_not_called()
        self.assertEqual(wait_time, 0)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    @mock.patch.object(time, 'sleep')
    def test_wait_cooldown_thermal_error(self, mock_sleep, mock_run_command):
        """
        Handle error status gracefully.

        Sensor with an error is excluded from temperature monitoring.
        But wait_cooldown still waits.
        """
        mock_sleep.return_value = 0
        # Error status and output with a high temperature.
        mock_run_command.side_effect = [
                (0, '/sys/class/thermal/thermal_zone0/temp\n'
                 '/sys/class/thermal/thermal_zone1/temp', ''),
                (0, 'cpu0\ncpu1', ''),
                # Iteration 1 of monitoring.
                # cpu0
                (1, '', 'Thernal error'),
                # Iteration 2 of monitoring.
                # cpu1
                (0, '45000', ''),
                (0, '39000', ''),
        ]
        cooldown_time = 10
        cooldown_temp = 40
        wait_time = device_setup_utils.wait_cooldown(self.dut, cooldown_time,
                                                     cooldown_temp)
        # Wait time is greater than 0.
        mock_sleep.assert_called()
        self.assertGreater(wait_time, 0)


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_stop_ui(self, mock_run_command):
        mock_run_command.return_value = (0, '', '')
        device_setup_utils.stop_ui(self.dut)
        mock_run_command.assert_called_once_with(self.dut, 'stop ui')


    @mock.patch.object(device_setup_utils, 'run_command_on_dut')
    def test_start_ui(self, mock_run_command):
        mock_run_command.return_value = (0, '', '')
        device_setup_utils.start_ui(self.dut)
        mock_run_command.assert_called_once_with(self.dut, 'start ui')


    @mock.patch.object(device_setup_utils, 'kern_cmd_update_needed')
    @mock.patch.object(device_setup_utils, 'update_kern_cmd_intel_pstate')
    @mock.patch.object(device_setup_utils, 'disable_aslr')
    @mock.patch.object(device_setup_utils, 'setup_cpu_usage')
    @mock.patch.object(device_setup_utils, 'setup_cpu_freq')
    @mock.patch.object(device_setup_utils, 'get_cpu_online')
    @mock.patch.object(device_setup_utils, 'set_cpu_governor')
    @mock.patch.object(device_setup_utils, 'disable_turbo')
    @mock.patch.object(device_setup_utils, 'stop_ui')
    @mock.patch.object(device_setup_utils, 'start_ui')
    @mock.patch.object(device_setup_utils, 'wait_cooldown')
    @mock.patch.object(device_setup_utils, 'decrease_wait_time')
    def test_setup_device(self, mock_decrease_wait_time, mock_wait_cooldown,
                          mock_start_ui, mock_stop_ui, mock_disable_turbo,
                          mock_set_cpu_governor, mock_get_cpu_online,
                          mock_setup_cpu_freq, mock_setup_cpu_usage,
                          mock_disable_aslr, mock_update_kern_cmd_intel_pstate,
                          mock_kern_cmd_update_needed):

        def setup_mock_functions():
            """
            Reset mock functions.
            """
            mock_kern_cmd_update_needed.reset_mock()
            mock_update_kern_cmd_intel_pstate.reset_mock()
            mock_disable_aslr.reset_mock()
            mock_setup_cpu_usage.reset_mock()
            mock_setup_cpu_freq.reset_mock()
            mock_get_cpu_online.reset_mock()
            mock_set_cpu_governor.reset_mock()
            mock_disable_turbo.reset_mock()
            mock_stop_ui.reset_mock()
            mock_start_ui.reset_mock()
            mock_wait_cooldown.reset_mock()
            mock_decrease_wait_time.reset_mock()

            mock_kern_cmd_update_needed.return_value = True
            mock_update_kern_cmd_intel_pstate.return_value = 0
            mock_disable_aslr.return_value = 0
            mock_setup_cpu_usage.return_value = 0
            mock_setup_cpu_freq.return_value = 0
            mock_get_cpu_online.return_value = {0: 1, 1: 1, 2: 0}
            mock_set_cpu_governor.return_value = 0
            mock_disable_turbo.return_value = 0
            mock_stop_ui.return_value = 0
            mock_start_ui.return_value = 0
            mock_wait_cooldown.return_value = 0
            mock_decrease_wait_time.return_value = 0

        dut_config = {
          'enable_aslr': False,
          'cooldown_time': 0,
          'cooldown_temp': 40,
          'governor': 'fake_governor',
          'cpu_freq_pct': 65,
          'intel_pstate': 'no_hwp',
        }

        setup_mock_functions()
        device_setup_utils.setup_device(self.dut, dut_config)

        mock_kern_cmd_update_needed.assert_called_once()
        mock_update_kern_cmd_intel_pstate.assert_called_once()
        mock_disable_aslr.assert_called_once()
        mock_setup_cpu_usage.assert_called_once()
        mock_setup_cpu_freq.assert_called_once_with(
                self.dut, dut_config['cpu_freq_pct'], [0, 1])
        mock_get_cpu_online.assert_called_once()
        mock_set_cpu_governor.assert_called_once_with(self.dut,
                                                      'fake_governor')
        mock_disable_turbo.assert_called_once()
        mock_decrease_wait_time.assert_called_once()
        mock_stop_ui.assert_called_once()
        mock_start_ui.assert_called_once()
        mock_wait_cooldown.assert_not_called()

        # Test SetupDevice with cooldown
        dut_config['cooldown_time'] = 10

        setup_mock_functions()
        mock_get_cpu_online.return_value = {0: 0, 1: 1}

        device_setup_utils.setup_device(self.dut, dut_config)

        mock_wait_cooldown.assert_called_once()
        mock_disable_aslr.assert_called_once()
        mock_disable_turbo.assert_called_once()
        mock_setup_cpu_usage.assert_called_once()
        mock_setup_cpu_freq.assert_called_once_with(
                self.dut, dut_config['cpu_freq_pct'], [1])
        mock_set_cpu_governor.assert_called()
        mock_get_cpu_online.assert_called_once()
        mock_stop_ui.assert_called_once()
        mock_start_ui.assert_called_once()
        self.assertGreater(mock_set_cpu_governor.call_count, 1)
        self.assertEqual(mock_set_cpu_governor.call_args,
                         mock.call(self.dut, 'fake_governor'))

        # Test SetupDevice with cooldown
        setup_mock_functions()
        mock_setup_cpu_usage.side_effect = RuntimeError()

        with self.assertRaises(RuntimeError):
            device_setup_utils.setup_device(self.dut, dut_config)

        # This call injected an exception.
        mock_setup_cpu_usage.assert_called_once()
        # Calls following the exception are skipped.
        mock_wait_cooldown.assert_not_called()
        mock_disable_turbo.assert_not_called()
        mock_setup_cpu_freq.assert_not_called()
        mock_set_cpu_governor.assert_not_called()
        mock_get_cpu_online.assert_not_called()
        # Check that Stop/Start UI are always called.
        mock_stop_ui.assert_called_once()
        mock_start_ui.assert_called_once()


if __name__ == '__main__':
    unittest.main()
