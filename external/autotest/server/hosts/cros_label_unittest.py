#!/usr/bin/python3
# Copyright 2017 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

import unittest
from unittest import mock

import common

from autotest_lib.server import utils
from autotest_lib.server.hosts.cros_label import BrandCodeLabel
from autotest_lib.server.hosts.cros_label import Cr50Label
from autotest_lib.server.hosts.cros_label import Cr50ROKeyidLabel
from autotest_lib.server.hosts.cros_label import Cr50RWKeyidLabel
from autotest_lib.server.hosts.cros_label import DeviceSkuLabel
from autotest_lib.server.hosts.cros_label import AudioConfigLabel
from autotest_lib.server.hosts.cros_label import AudioLoopbackDongleLabel
from autotest_lib.server.hosts.cros_label import ChameleonConnectionLabel
from autotest_lib.server.hosts.cros_label import ChameleonLabel
from autotest_lib.server.hosts.cros_label import ServoTypeLabel
from autotest_lib.server.hosts import host_info

# pylint: disable=missing-docstring

NON_UNI_LSB_RELEASE_OUTPUT = """
CHROMEOS_RELEASE_APPID={63A9F698-C1CA-4A75-95E7-6B90181B3718}
CHROMEOS_BOARD_APPID={63A9F698-C1CA-4A75-95E7-6B90181B3718}
CHROMEOS_CANARY_APPID={90F229CE-83E2-4FAF-8479-E368A34938B1}
DEVICETYPE=CHROMEBOOK
CHROMEOS_ARC_VERSION=4234098
CHROMEOS_ARC_ANDROID_SDK_VERSION=25
GOOGLE_RELEASE=9798.0.2017_08_02_1022
CHROMEOS_DEVSERVER=http://shapiroc3.bld.corp.google.com:8080
CHROMEOS_RELEASE_BOARD=pyro
CHROMEOS_RELEASE_BUILD_NUMBER=9798
CHROMEOS_RELEASE_BRANCH_NUMBER=0
CHROMEOS_RELEASE_CHROME_MILESTONE=62
CHROMEOS_RELEASE_PATCH_NUMBER=2017_08_02_1022
CHROMEOS_RELEASE_TRACK=testimage-channel
CHROMEOS_RELEASE_DESCRIPTION=9798.0.2017_08_02_1022 (Test Build)
CHROMEOS_RELEASE_BUILD_TYPE=Test Build
CHROMEOS_RELEASE_NAME=Chromium OS
CHROMEOS_RELEASE_VERSION=9798.0.2017_08_02_1022
CHROMEOS_AUSERVER=http://someserver.bld.corp.google.com:8080/update
"""

UNI_LSB_RELEASE_OUTPUT = """
CHROMEOS_RELEASE_APPID={5A3AB642-2A67-470A-8F37-37E737A53CFC}
CHROMEOS_BOARD_APPID={5A3AB642-2A67-470A-8F37-37E737A53CFC}
CHROMEOS_CANARY_APPID={90F229CE-83E2-4FAF-8479-E368A34938B1}
DEVICETYPE=CHROMEBOOK
CHROMEOS_ARC_VERSION=4340813
CHROMEOS_ARC_ANDROID_SDK_VERSION=25
GOOGLE_RELEASE=9953.0.2017_09_18_1334
CHROMEOS_DEVSERVER=http://server.bld.corp.google.com:8080
CHROMEOS_RELEASE_BOARD=coral
CHROMEOS_RELEASE_BUILD_NUMBER=9953
CHROMEOS_RELEASE_BRANCH_NUMBER=0
CHROMEOS_RELEASE_CHROME_MILESTONE=63
CHROMEOS_RELEASE_PATCH_NUMBER=2017_09_18_1334
CHROMEOS_RELEASE_TRACK=testimage-channel
CHROMEOS_RELEASE_DESCRIPTION=9953.0.2017_09_18_1334 (Test Build)
CHROMEOS_RELEASE_BUILD_TYPE=Test Build
CHROMEOS_RELEASE_NAME=Chromium OS
CHROMEOS_RELEASE_UNIBUILD=1
CHROMEOS_RELEASE_VERSION=9953.0.2017_09_18_1334
CHROMEOS_AUSERVER=http://server.bld.corp.google.com:8080/update
CHROMEOS_RELEASE_MODELS=coral astronaut blue bruce lava nasher
"""

GSCTOOL_OUTPUT_PVT = """
start
target running protocol version 6
keyids: RO 0xaa66150f, RW 0xde88588d
offsets: backup RO at 0x40000, backup RW at 0x44000
Current versions:
RO 0.0.10
RW 0.3.14
"""

GSCTOOL_OUTPUT_PREPVT = """
start
target running protocol version 6
keyids: RO 0xaa66150f, RW 0xde88588d
offsets: backup RO at 0x40000, backup RW at 0x44000
Current versions:
RO 0.0.10
RW 0.4.15
"""

GSCTOOL_OUTPUT_DEV_RO = """
start
target running protocol version 6
keyids: RO 0x3716ee6b, RW 0xde88588d
offsets: backup RO at 0x40000, backup RW at 0x44000
Current versions:
RO 0.0.10
RW 0.4.15
"""

GSCTOOL_OUTPUT_DEV_RW = """
start
target running protocol version 6
keyids: RO 0xaa66150f, RW 0xb93d6539
offsets: backup RO at 0x40000, backup RW at 0x44000
Current versions:
RO 0.0.10
RW 0.4.15
"""


class MockCmd(object):
    """Simple mock command with base command and results"""

    def __init__(self, cmd, exit_status, stdout):
        self.cmd = cmd
        self.stdout = stdout
        self.exit_status = exit_status


class MockAFEHost(utils.EmptyAFEHost):

    def __init__(self, labels=[], attributes={}):
        self.labels = labels
        self.attributes = attributes


class MockHost(object):
    """Simple host for running mock'd host commands"""

    def __init__(self, labels, *args):
        self._afe_host = MockAFEHost(labels)
        self.mock_cmds = {c.cmd: c for c in args}
        info = host_info.HostInfo(labels=labels)
        self.host_info_store = host_info.InMemoryHostInfoStore(info)

    def run(self, command, **kwargs):
        """Finds the matching result by command value"""
        return self.mock_cmds[command]

    def is_up(self, **args):
        return True


class MockHostWithoutAFE(MockHost):

    def __init__(self, labels, *args):
        super(MockHostWithoutAFE, self).__init__(labels, *args)
        self._afe_host = utils.EmptyAFEHost()


class DeviceSkuLabelTests(unittest.TestCase):
    """Unit tests for DeviceSkuLabel"""

    def test_new_label(self):
        mosys_cmd = 'mosys platform sku'
        host = MockHost([], MockCmd(mosys_cmd, 0, '27\n'))
        self.assertEqual(DeviceSkuLabel().generate_labels(host), ['27'])

    def test_new_label_mosys_fails(self):
        mosys_cmd = 'mosys platform sku'
        host = MockHost([], MockCmd(mosys_cmd, 1, '27\n'))
        self.assertEqual(DeviceSkuLabel().generate_labels(host), [])

    def test_existing_label(self):
        host = MockHost(['device-sku:48'])
        self.assertEqual(DeviceSkuLabel().generate_labels(host), ['48'])

    def test_update_for_task(self):
        self.assertTrue(DeviceSkuLabel().update_for_task(''))
        self.assertTrue(DeviceSkuLabel().update_for_task('repair'))
        self.assertTrue(DeviceSkuLabel().update_for_task('deploy'))


class BrandCodeLabelTests(unittest.TestCase):
    """Unit tests for DeviceSkuLabel"""

    def test_new_label(self):
        cros_config_cmd = 'cros_config / brand-code'
        host = MockHost([], MockCmd(cros_config_cmd, 0, 'XXYZ\n'))
        self.assertEqual(BrandCodeLabel().generate_labels(host), ['XXYZ'])

    def test_new_label_cros_config_fails(self):
        cros_config_cmd = 'cros_config / brand-code'
        host = MockHost([], MockCmd(cros_config_cmd, 1, 'XXYZ\n'))
        self.assertEqual(BrandCodeLabel().generate_labels(host), [])

    def test_existing_label(self):
        host = MockHost(['brand-code:ABCD'])
        self.assertEqual(BrandCodeLabel().generate_labels(host), ['ABCD'])


class Cr50Tests(unittest.TestCase):
    """Unit tests for Cr50Label"""

    def test_cr50_pvt(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_PVT))
        self.assertEqual(Cr50Label().get(host), ['cr50:pvt'])

    def test_cr50_prepvt(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_PREPVT))
        self.assertEqual(Cr50Label().get(host), ['cr50:prepvt'])

    def test_gsctool_fails(self):
        host = MockHost([], MockCmd('gsctool -a -f', 1, ''))
        self.assertEqual(Cr50Label().get(host), [])


class Cr50RWKeyidTests(unittest.TestCase):
    """Unit tests for Cr50RWKeyidLabel"""

    def test_cr50_prod_rw(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_PVT))
        self.assertEqual(Cr50RWKeyidLabel().get(host),
                         ['cr50-rw-keyid:0xde88588d', 'cr50-rw-keyid:prod'])

    def test_cr50_dev_rw(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_DEV_RW))
        self.assertEqual(Cr50RWKeyidLabel().get(host),
                         ['cr50-rw-keyid:0xb93d6539', 'cr50-rw-keyid:dev'])

    def test_gsctool_fails(self):
        host = MockHost([], MockCmd('gsctool -a -f', 1, ''))
        self.assertEqual(Cr50RWKeyidLabel().get(host), [])


class Cr50ROKeyidTests(unittest.TestCase):
    """Unit tests for Cr50ROKeyidLabel"""

    def test_cr50_prod_ro(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_PREPVT))
        self.assertEqual(Cr50ROKeyidLabel().get(host),
                         ['cr50-ro-keyid:0xaa66150f', 'cr50-ro-keyid:prod'])

    def test_cr50_dev_ro(self):
        host = MockHost([], MockCmd('gsctool -a -f', 0, GSCTOOL_OUTPUT_DEV_RO))
        self.assertEqual(Cr50ROKeyidLabel().get(host),
                         ['cr50-ro-keyid:0x3716ee6b', 'cr50-ro-keyid:dev'])

    def test_gsctool_fails(self):
        host = MockHost([], MockCmd('gsctool -a -f', 1, ''))
        self.assertEqual(Cr50ROKeyidLabel().get(host), [])


class AudioConfigLabelTests(unittest.TestCase):
    """Unit tests for AudioConfigLabel"""

    HAS_NC_BOARD_INI_OUTPUT = """
    [hotword]
    pause_at_suspend=1
    [processing]
    nc_supported=1
    hw_echo_ref_disabled=1
    """

    NO_NC_BOARD_INI_OUTPUT = """
    [hotword]
    pause_at_suspend=1
    [processing]
    hw_echo_ref_disabled=1
    """

    DISABLED_NC_BOARD_INI_OUTPUT = """
    [hotword]
    pause_at_suspend=1
    [processing]
    hw_echo_ref_disabled=1
    nc_supported=0
    """

    def test_has_noise_cancellation_label_enabled(self):
        cros_config_cmd = 'cros_config / name'
        cras_config_cmd = 'cat /etc/cras/HAS_NC/board.ini'
        host = MockHost([], MockCmd(cros_config_cmd, 0, 'HAS_NC\n'),
                        MockCmd(cras_config_cmd, 0,
                                self.HAS_NC_BOARD_INI_OUTPUT))
        self.assertEqual(AudioConfigLabel().get(host),
                         ['audio:has_noise_cancellation'])

    def test_has_noise_cancellation_label_not_exists(self):
        cros_config_cmd = 'cros_config / name'
        cras_config_cmd = 'cat /etc/cras/NO_NC/board.ini'
        host = MockHost([], MockCmd(cros_config_cmd, 0, 'NO_NC\n'),
                        MockCmd(cras_config_cmd, 0,
                                self.NO_NC_BOARD_INI_OUTPUT))
        self.assertEqual(AudioConfigLabel().get(host), [])

    def test_has_noise_cancellation_label_disabled(self):
        cros_config_cmd = 'cros_config / name'
        cras_config_cmd = 'cat /etc/cras/DISABLED_NC/board.ini'
        host = MockHost([], MockCmd(cros_config_cmd, 0, 'DISABLED_NC\n'),
                        MockCmd(cras_config_cmd, 0,
                                self.DISABLED_NC_BOARD_INI_OUTPUT))
        self.assertEqual(AudioConfigLabel().get(host), [])

    def test_has_noise_cancellation_label_fails_cros_config(self):
        cros_config_cmd = 'cros_config / name'
        cras_config_cmd = 'cat /etc/cras/HAS_NC/board.ini'
        host = MockHost([], MockCmd(cros_config_cmd, 1, 'HAS_NC\n'),
                        MockCmd(cras_config_cmd, 0,
                                self.HAS_NC_BOARD_INI_OUTPUT))
        self.assertEqual(AudioConfigLabel().get(host), [])

    def test_has_noise_cancellation_label_fails_cras_config(self):
        cros_config_cmd = 'cros_config / name'
        cras_config_cmd = 'cat /etc/cras/HAS_NC/board.ini'
        host = MockHost([], MockCmd(cros_config_cmd, 0, 'HAS_NC\n'),
                        MockCmd(cras_config_cmd, 1,
                                self.HAS_NC_BOARD_INI_OUTPUT))
        self.assertEqual(AudioConfigLabel().get(host), [])


class AudioLoopbackDongleLabelTests(unittest.TestCase):
    def test_update_for_task(self):
        self.assertTrue(AudioLoopbackDongleLabel().update_for_task(''))
        self.assertTrue(AudioLoopbackDongleLabel().update_for_task('repair'))
        self.assertFalse(AudioLoopbackDongleLabel().update_for_task('deploy'))


class ChameleonConnectionLabelTests(unittest.TestCase):
    def test_update_for_task(self):
        self.assertTrue(ChameleonConnectionLabel().update_for_task(''))
        self.assertFalse(ChameleonConnectionLabel().update_for_task('repair'))
        self.assertTrue(ChameleonConnectionLabel().update_for_task('deploy'))


class ChameleonLabelTests(unittest.TestCase):
    def test_update_for_task(self):
        self.assertTrue(ChameleonLabel().update_for_task(''))
        self.assertTrue(ChameleonLabel().update_for_task('repair'))
        self.assertFalse(ChameleonLabel().update_for_task('deploy'))


class ServoTypeLabelTests(unittest.TestCase):
    """Unit tests for ServoTypeLabel"""
    def test_update_for_task(self):
        self.assertTrue(ServoTypeLabel().update_for_task(''))
        self.assertFalse(ServoTypeLabel().update_for_task('repair'))
        self.assertTrue(ServoTypeLabel().update_for_task('deploy'))

    def test_generate_labels_return_value_from_labels(self):
        host = MockHost(['servo_type:Some_interesting'])
        servo = ServoTypeLabel()
        self.assertEqual(servo.get(host), ['servo_type:Some_interesting'])
        self.assertEqual(servo.generate_labels(host), ['Some_interesting'])

    def test_generate_labels_from_cache_when_servo_is_none(self):
        host = MockHost(['servo_state:Some_interesting'])
        host.servo = None
        servo = ServoTypeLabel()
        self.assertEqual(servo.get(host), [])
        self.assertEqual(servo.generate_labels(host), [])

    def test_generate_labels_not_from_cache_when_servo_exist(self):
        host = MockHost(['servo_type'])
        host.servo = mock.Mock()
        host.servo.get_servo_version.return_value = 'servo_v3'
        servo = ServoTypeLabel()
        self.assertEqual(servo.get(host), ['servo_type:servo_v3'])
        self.assertEqual(servo.generate_labels(host), ['servo_v3'])
        host.servo.get_servo_version.assert_called()


if __name__ == '__main__':
    unittest.main()
