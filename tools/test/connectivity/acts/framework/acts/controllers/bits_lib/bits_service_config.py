#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the 'License');
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an 'AS IS' BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import copy

DEFAULT_MONSOON_CONFIG_DICT = {
    'enabled': 1,
    'type': 'monsooncollector',
    'monsoon_reset': 0,
    # maximum monsoon sample rate that works best for both lvpm and hvpm
    'sampling_rate': 1000,
}


class _BitsMonsoonConfig(object):
    """Helper object to construct a bits_service config from a monsoon config as
    defined for the bits controller config and required additional resources,
    such as paths to executables.

    The format for the bits_service's monsoon configuration is explained at:
    http://go/pixel-bits/user-guide/service/collectors/monsoon

    Attributes:
        config_dic: A bits_service's monsoon configuration as a python
        dictionary.
    """

    def __init__(self, monsoon_config, lvpm_monsoon_bin=None,
                 hvpm_monsoon_bin=None):
        """Constructs _BitsServiceMonsoonConfig.

        Args:
            monsoon_config: The monsoon config as defined in the
                ACTS Bits controller config. Expected format is:
                  { 'serial_num': <serial number:int>,
                    'monsoon_voltage': <voltage:double> }
            lvpm_monsoon_bin: Binary file to interact with low voltage monsoons.
                Needed if the monsoon is a lvpm monsoon (serial number lower
                than 20000).
            hvpm_monsoon_bin: Binary file to interact with high voltage
                monsoons. Needed if the monsoon is a hvpm monsoon (serial number
                greater than 20000).
        """
        if 'serial_num' not in monsoon_config:
            raise ValueError(
                'Monsoon serial_num can not be undefined. Received '
                'config was: %s' % monsoon_config)
        if 'monsoon_voltage' not in monsoon_config:
            raise ValueError('Monsoon voltage can not be undefined. Received '
                             'config was: %s' % monsoon_config)

        self.serial_num = int(monsoon_config['serial_num'])
        self.monsoon_voltage = float(monsoon_config['monsoon_voltage'])

        self.config_dic = copy.deepcopy(DEFAULT_MONSOON_CONFIG_DICT)
        if float(self.serial_num) >= 20000:
            self.config_dic['hv_monsoon'] = 1
            if hvpm_monsoon_bin is None:
                raise ValueError('hvpm_monsoon binary is needed but was None. '
                                 'Received config was: %s' % monsoon_config)
            self.monsoon_binary = hvpm_monsoon_bin
        else:
            self.config_dic['hv_monsoon'] = 0
            if lvpm_monsoon_bin is None:
                raise ValueError('lvpm_monsoon binary is needed but was None. '
                                 'Received config was: %s' % monsoon_config)
            self.monsoon_binary = lvpm_monsoon_bin

        self.config_dic['monsoon_binary_path'] = self.monsoon_binary
        self.config_dic['monsoon_voltage'] = self.monsoon_voltage
        self.config_dic['serial_num'] = self.serial_num


DEFAULT_KIBBLES_BOARD_CONFIG = {
    'enabled': 1,
    'type': 'kibblecollector',
    'attached_kibbles': {}
}

DEFAULT_KIBBLE_CONFIG = {
    'ultra_channels_current_hz': 976.5625,
    'ultra_channels_voltage_hz': 976.5625,
    'high_channels_current_hz': 976.5625,
    'high_channels_voltage_hz': 976.5625
}


class _BitsKibblesConfig(object):
    def __init__(self, kibbles_config, kibble_bin, kibble_board_file):
        """Constructs _BitsKibblesConfig.

        Args:
            kibbles_config: A list of compacted kibble boards descriptions.
                Expected format is:
                    [{
                        'board': 'BoardName1',
                        'connector': 'A',
                        'serial': 'serial_1'
                     },
                    {
                        'board': 'BoardName2',
                        'connector': 'D',
                        'serial': 'serial_2'
                    }]
                More details can be found at go/acts-bits.
            kibble_bin: Binary file to interact with kibbles.
            kibble_board_file: File describing the distribution of rails on a
                kibble. go/kibble#setting-up-bits-board-files
        """

        if not isinstance(kibbles_config, list):
            raise ValueError(
                'kibbles_config must be a list. Got %s.' % kibbles_config)

        if kibble_bin is None:
            raise ValueError('Kibbles were present in the config but no '
                             'kibble_bin was provided')
        if kibble_board_file is None:
            raise ValueError('Kibbles were present in the config but no '
                             'kibble_board_file was provided')

        self.boards_configs = {}

        for kibble in kibbles_config:
            if 'board' not in kibble:
                raise ValueError('An individual kibble config must have a '
                                 'board')
            if 'connector' not in kibble:
                raise ValueError('An individual kibble config must have a '
                                 'connector')
            if 'serial' not in kibble:
                raise ValueError('An individual kibble config must have a '
                                 'serial')
            if 'subkibble_params' in kibble:
                user_defined_kibble_config = kibble['subkibble_params']
                kibble_config = copy.deepcopy(user_defined_kibble_config)
            else:
                kibble_config = copy.deepcopy(DEFAULT_KIBBLE_CONFIG)
            board = kibble['board']
            connector = kibble['connector']
            serial = kibble['serial']
            if board not in self.boards_configs:
                self.boards_configs[board] = copy.deepcopy(
                    DEFAULT_KIBBLES_BOARD_CONFIG)
                self.boards_configs[board][
                    'board_file'] = kibble_board_file
                self.boards_configs[board]['kibble_py'] = kibble_bin
            kibble_config['connector'] = connector
            self.boards_configs[board]['attached_kibbles'][
                serial] = kibble_config


DEFAULT_SERVICE_CONFIG_DICT = {
    'devices': {
        'default_device': {
            'enabled': 1,
            'collectors': {}
        }
    }
}


class BitsServiceConfig(object):
    """Helper object to construct a bits_service config from a bits controller
    config and required additional resources, such as paths to executables.

    The format for bits_service's configuration is explained in:
    go/pixel-bits/user-guide/service/configuration.md

    Attributes:
        config_dic: A bits_service configuration as a python dictionary.
    """

    def __init__(self, controller_config, lvpm_monsoon_bin=None,
                 hvpm_monsoon_bin=None, kibble_bin=None,
                 kibble_board_file=None, virtual_metrics_file=None):
        """Creates a BitsServiceConfig.

        Args:
            controller_config: The config as defined in the ACTS  BiTS
                controller config. Expected format is:
                {
                    // optional
                    'Monsoon':   {
                        'serial_num': <serial number:int>,
                        'monsoon_voltage': <voltage:double>
                    }
                    // optional
                    'Kibble': [
                        {
                            'board': 'BoardName1',
                            'connector': 'A',
                            'serial': 'serial_1'
                        },
                        {
                            'board': 'BoardName2',
                            'connector': 'D',
                            'serial': 'serial_2'
                        }
                    ]
                }
            lvpm_monsoon_bin: Binary file to interact with low voltage monsoons.
                Needed if the monsoon is a lvpm monsoon (serial number lower
                than 20000).
            hvpm_monsoon_bin: Binary file to interact with high voltage
                monsoons. Needed if the monsoon is a hvpm monsoon (serial number
                greater than 20000).
            kibble_bin: Binary file to interact with kibbles.
            kibble_board_file: File describing the distribution of rails on a
                kibble. go/kibble#setting-up-bits-board-files
            virtual_metrics_file: A list of virtual metrics files to add
                data aggregates on top of regular channel aggregates.
                go/pixel-bits/user-guide/virtual-metrics
        """
        self.config_dic = copy.deepcopy(DEFAULT_SERVICE_CONFIG_DICT)
        self.has_monsoon = False
        self.has_kibbles = False
        self.has_virtual_metrics_file = False
        self.monsoon_config = None
        self.kibbles_config = None
        if 'Monsoon' in controller_config:
            self.has_monsoon = True
            self.monsoon_config = _BitsMonsoonConfig(
                controller_config['Monsoon'],
                lvpm_monsoon_bin,
                hvpm_monsoon_bin)
            self.config_dic['devices']['default_device']['collectors'][
                'Monsoon'] = self.monsoon_config.config_dic
        if 'Kibbles' in controller_config:
            self.has_kibbles = True
            self.kibbles_config = _BitsKibblesConfig(
                controller_config['Kibbles'],
                kibble_bin, kibble_board_file)
            self.config_dic['devices']['default_device']['collectors'].update(
                self.kibbles_config.boards_configs)
            if virtual_metrics_file is not None:
                self.config_dic['devices']['default_device'][
                    'vm_files'] = [virtual_metrics_file]
                self.has_virtual_metrics_file = True
