# Lint as: python2, python3
# Copyright 2016 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.

"""This class defines the CrosHost Label class."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import collections
import configparser
import logging
import re

import common

from autotest_lib.client.bin import utils
from autotest_lib.client.cros.audio import cras_utils
from autotest_lib.server.cros.dynamic_suite import constants as ds_constants
from autotest_lib.server.hosts import base_label
from autotest_lib.server.hosts import common_label
from autotest_lib.server.hosts import servo_constants
from six.moves import zip

# pylint: disable=missing-docstring
LsbOutput = collections.namedtuple('LsbOutput', ['unibuild', 'board'])

# Repair and Deploy taskName
REPAIR_TASK_NAME = 'repair'
DEPLOY_TASK_NAME = 'deploy'


def _parse_lsb_output(host):
    """Parses the LSB output and returns key data points for labeling.

    @param host: Host that the command will be executed against
    @returns: LsbOutput with the result of parsing the /etc/lsb-release output
    """
    release_info = utils.parse_cmd_output('cat /etc/lsb-release',
                                          run_method=host.run)

    unibuild = release_info.get('CHROMEOS_RELEASE_UNIBUILD') == '1'
    return LsbOutput(unibuild, release_info['CHROMEOS_RELEASE_BOARD'])


class DeviceSkuLabel(base_label.StringPrefixLabel):
    """Determine the correct device_sku label for the device."""

    _NAME =  ds_constants.DEVICE_SKU_LABEL

    def generate_labels(self, host):
        device_sku = host.host_info_store.get().device_sku
        if device_sku:
            return [device_sku]

        mosys_cmd = 'mosys platform sku'
        result = host.run(command=mosys_cmd, ignore_status=True)
        if result.exit_status == 0:
            return [result.stdout.strip()]

        return []

    def update_for_task(self, task_name):
        # This label is stored in the lab config.
        return task_name in (DEPLOY_TASK_NAME, REPAIR_TASK_NAME, '')


class BrandCodeLabel(base_label.StringPrefixLabel):
    """Determine the correct brand_code (aka RLZ-code) for the device."""

    _NAME =  ds_constants.BRAND_CODE_LABEL

    def generate_labels(self, host):
        brand_code = host.host_info_store.get().brand_code
        if brand_code:
            return [brand_code]

        cros_config_cmd = 'cros_config / brand-code'
        result = host.run(command=cros_config_cmd, ignore_status=True)
        if result.exit_status == 0:
            return [result.stdout.strip()]

        return []


class BluetoothPeerLabel(base_label.StringPrefixLabel):
    """Return the Bluetooth peer labels.

    working_bluetooth_btpeer label is applied if a Raspberry Pi Bluetooth peer
    is detected.There can be up to 4 Bluetooth peers. Labels
    working_bluetooth_btpeer:[1-4] will be assigned depending on the number of
    peers present.

    """

    _NAME = 'working_bluetooth_btpeer'

    def exists(self, host):
        return  len(host._btpeer_host_list) > 0

    def generate_labels(self, host):
        labels_list = []
        count = 1

        for (btpeer, btpeer_host) in \
                        zip(host.btpeer_list, host._btpeer_host_list):
            try:
                # Initialize one device type to make sure the peer is working
                bt_hid_device = btpeer.get_bluetooth_hid_mouse()
                if bt_hid_device.CheckSerialConnection():
                    labels_list.append(str(count))
                    count += 1
            except Exception as e:
                logging.error('Error with initializing bt_hid_mouse on '
                              'btpeer %s %s', btpeer_host.hostname, e)

        logging.info('Bluetooth Peer labels are %s', labels_list)
        return labels_list

    def update_for_task(self, task_name):
        # This label is stored in the state config, so only repair tasks update
        # it or when no task name is mentioned.
        return task_name in (REPAIR_TASK_NAME, '')


class Cr50Label(base_label.StringPrefixLabel):
    """Label indicating the cr50 image type."""

    _NAME = 'cr50'

    def __init__(self):
        self.ver = None

    def exists(self, host):
        # Make sure the gsctool version command runs ok
        self.ver = host.run('gsctool -a -f', ignore_status=True)
        return self.ver.exit_status == 0

    def _get_version(self, region):
        """Get the version number of the given region"""
        return re.search(region + ' (\d+\.\d+\.\d+)', self.ver.stdout).group(1)

    def generate_labels(self, host):
        # Check the major version to determine prePVT vs PVT
        version = self._get_version('RW')
        major_version = int(version.split('.')[1])
        # PVT images have a odd major version prePVT have even
        return ['pvt' if (major_version % 2) else 'prepvt']

    def update_for_task(self, task_name):
        # This label is stored in the state config, so only repair tasks update
        # it or when no task name is mentioned.
        return task_name in (REPAIR_TASK_NAME, '')


class Cr50RWKeyidLabel(Cr50Label):
    """Label indicating the cr50 RW version."""
    _REGION = 'RW'
    _NAME = 'cr50-rw-keyid'

    def _get_keyid_info(self, region):
        """Get the keyid of the given region."""
        match = re.search('keyids:.*%s (\S+)' % region, self.ver.stdout)
        keyid = match.group(1).rstrip(',')
        is_prod = int(keyid, 16) & (1 << 2)
        return [keyid, 'prod' if is_prod else 'dev']

    def generate_labels(self, host):
        """Get the key type."""
        return self._get_keyid_info(self._REGION)


class Cr50ROKeyidLabel(Cr50RWKeyidLabel):
    """Label indicating the RO key type."""
    _REGION = 'RO'
    _NAME = 'cr50-ro-keyid'


class ChameleonLabel(base_label.BaseLabel):
    """Determine if a Chameleon is connected to this host."""

    _NAME = 'chameleon'

    def exists(self, host):
        # See crbug.com/1004500#2 for details.
        has_chameleon = host._chameleon_host is not None
        # TODO(crbug.com/995900) -- debug why chameleon label is flipping
        try:
            logging.info("has_chameleon %s", has_chameleon)
            logging.info("_chameleon_host %s",
                         getattr(host, "_chameleon_host", "NO_ATTRIBUTE"))
            logging.info("chameleon %s",
                         getattr(host, "chameleon", "NO_ATTRIBUTE"))
        except:
            pass
        return has_chameleon

    def update_for_task(self, task_name):
        # This label is stored in the state config, so only repair tasks update
        # it or when no task name is mentioned.
        return task_name in (REPAIR_TASK_NAME, '')


class ChameleonConnectionLabel(base_label.StringPrefixLabel):
    """Return the Chameleon connection label."""

    _NAME = 'chameleon'

    def exists(self, host):
        return host._chameleon_host is not None

    def generate_labels(self, host):
        return [host.chameleon.get_label()]

    def update_for_task(self, task_name):
        # This label is stored in the lab config, so only deploy tasks update it
        # or when no task name is mentioned.
        return task_name in (DEPLOY_TASK_NAME, '')


class AudioConfigLabel(base_label.StringPrefixLabel):
    """Determine the label of CRAS configuration for the device.

    It parses config keys from the board.ini file content as the example below:

    [hotword]
    pause_at_suspend=1
    [processing]
    nc_supported=1

    """

    _NAME = 'audio'

    def generate_labels(self, host):
        # Get model name for determining the board.ini file path.
        cros_config_cmd = 'cros_config / name'
        result = host.run(command=cros_config_cmd, ignore_status=True)
        if result.exit_status != 0:
            logging.error('Failed to run command: %s', cros_config_cmd)
            return []

        model = result.stdout.strip()
        cras_config_cmd = 'cat /etc/cras/{}/board.ini'.format(model)
        result = host.run(command=cras_config_cmd, ignore_status=True)
        if result.exit_status != 0:
            logging.error('Failed to run command: %s', cras_config_cmd)
            return []

        config = configparser.ConfigParser()
        config.read_string(result.stdout)
        labels = []
        # Generate "has_noise_cancellation" from "processing:nc_supported".
        if config.getboolean('processing', 'nc_supported', fallback=False):
            labels.append('has_noise_cancellation')

        return labels


class AudioLoopbackDongleLabel(base_label.BaseLabel):
    """Return the label if an audio loopback dongle is plugged in."""

    _NAME = 'audio_loopback_dongle'

    def exists(self, host):
        # Based on crbug.com/991285, AudioLoopbackDongle sometimes flips.
        # Ensure that AudioLoopbackDongle.exists returns True
        # forever, after it returns True *once*.
        if self._cached_exists(host):
            # If the current state is True, return it, don't run the command on
            # the DUT and potentially flip the state.
            return True
        # If the current state is not True, run the command on
        # the DUT. The new state will be set to whatever the command
        # produces.
        return self._host_run_exists(host)

    def _cached_exists(self, host):
        """Get the state of AudioLoopbackDongle in the data store"""
        info = host.host_info_store.get()
        for label in info.labels:
            if label.startswith(self._NAME):
                return True
        return False

    def _host_run_exists(self, host):
        """Detect presence of audio_loopback_dongle by physically
        running a command on the DUT."""
        nodes_info = host.run(command=cras_utils.get_cras_nodes_cmd(),
                              ignore_status=True).stdout
        if (cras_utils.node_type_is_plugged('HEADPHONE', nodes_info) and
            cras_utils.node_type_is_plugged('MIC', nodes_info)):
            return True
        return False

    def update_for_task(self, task_name):
        # This label is stored in the state config, so only repair tasks update
        # it or when no task name is mentioned.
        return task_name in (REPAIR_TASK_NAME, '')


class ServoTypeLabel(base_label.StringPrefixLabel):
    _NAME = servo_constants.SERVO_TYPE_LABEL_PREFIX

    def generate_labels(self, host):
        info = host.host_info_store.get()

        servo_type = self._get_from_labels(info)
        if servo_type != '':
            logging.info("Using servo_type: %s from cache!", servo_type)
            return [servo_type]

        if host.servo is not None:
            try:
                servo_type = host.servo.get_servo_version()
                if servo_type != '':
                    return [servo_type]
                logging.warning('Cannot collect servo_type from servo'
                ' by `dut-control servo_type`! Please file a bug'
                ' and inform infra team as we are not expected '
                ' to reach this point.')
            except Exception as e:
                # We don't want fail the label and break DUTs here just
                # because of servo issue.
                logging.error("Failed to update servo_type, %s", str(e))
        return []

    def _get_from_labels(self, info):
        prefix = self._NAME + ':'
        for label in info.labels:
            if  label.startswith(prefix):
                suffix_length = len(prefix)
                return label[suffix_length:]
        return ''

    def update_for_task(self, task_name):
        # This label is stored in the lab config,
        # only deploy and repair tasks update it
        # or when no task name is mentioned.
        return task_name in (DEPLOY_TASK_NAME, '')


def _parse_hwid_labels(hwid_info_list):
    if len(hwid_info_list) == 0:
        return hwid_info_list

    res = []
    # See crbug.com/997816#c7 for details of two potential formats of returns
    # from HWID server.
    if isinstance(hwid_info_list[0], dict):
        # Format of hwid_info:
        # [{u'name': u'sku', u'value': u'xxx'}, ..., ]
        for hwid_info in hwid_info_list:
            value = hwid_info.get('value', '')
            name = hwid_info.get('name', '')
            # There should always be a name but just in case there is not.
            if name:
                new_label = name if not value else '%s:%s' % (name, value)
                res.append(new_label)
    else:
        # Format of hwid_info:
        # [<DUTLabel name: 'sku' value: u'xxx'>, ..., ]
        for hwid_info in hwid_info_list:
            new_label = str(hwid_info)
            logging.info('processing hwid label: %s', new_label)
            res.append(new_label)

    return res


CROS_LABELS = [
    AudioConfigLabel(),
    AudioLoopbackDongleLabel(), #STATECONFIG
    BluetoothPeerLabel(), #STATECONFIG
    ChameleonConnectionLabel(), #LABCONFIG
    ChameleonLabel(), #STATECONFIG
    common_label.OSLabel(),
    DeviceSkuLabel(), #LABCONFIG
    ServoTypeLabel(), #LABCONFIG
    # Temporarily add back as there's no way to reference cr50 configs.
    # See crbug.com/1057145 for the root cause.
    # See crbug.com/1057719 for future tracking.
    Cr50Label(),
    Cr50ROKeyidLabel(),
]

LABSTATION_LABELS = [
    common_label.OSLabel(),
]
