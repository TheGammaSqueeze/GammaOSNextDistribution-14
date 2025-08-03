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

import unittest

from acts.controllers.bits_lib import bits_service_config


class BitsServiceConfigTest(unittest.TestCase):

    def test_basic_config(self):
        config_dic = bits_service_config.BitsServiceConfig({}).config_dic
        self.assertIn('devices', config_dic)
        self.assertIn('default_device', config_dic['devices'])
        self.assertIn('collectors', config_dic['devices']['default_device'])

    def test_bits_service_config_has_an_enabled_default_device(self):
        config_dic = bits_service_config.BitsServiceConfig({}).config_dic
        self.assertEqual(1, config_dic['devices']['default_device']['enabled'])


class BitsServiceConfigWithMonsoonTest(unittest.TestCase):

    def test_monsoon_with_serial_less_than_20000_is_configured_as_non_hv(self):
        config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 19999, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='lvpm_bin', hvpm_monsoon_bin='hvpm_bin')
        self.assertEqual(0, config.config_dic['hv_monsoon'])
        self.assertEqual('lvpm_bin', config.config_dic['monsoon_binary_path'])

    def test_lvpm_monsoon_requires_lvpm_binary(self):
        self.assertRaisesRegex(ValueError,
                               r'lvpm_monsoon binary is needed but was None.',
                               bits_service_config._BitsMonsoonConfig,
                               {'serial_num': 19999, 'monsoon_voltage': 1},
                               hvpm_monsoon_bin='hvpm_bin')

    def test_monsoon_with_serial_greater_than_20000_is_configured_as_hv(self):
        config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 20001, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='lvpm_bin', hvpm_monsoon_bin='hvpm_bin')
        self.assertEqual(1, config.config_dic['hv_monsoon'])
        self.assertEqual('hvpm_bin', config.config_dic['monsoon_binary_path'])

    def test_hvpm_monsoon_requires_hvpm_binary(self):
        self.assertRaisesRegex(ValueError,
                               r'hvpm_monsoon binary is needed but was None.',
                               bits_service_config._BitsMonsoonConfig,
                               {'serial_num': 20001, 'monsoon_voltage': 1},
                               lvpm_monsoon_bin='hvpm_bin')

    def test_monsoon_config_fails_without_voltage(self):
        self.assertRaisesRegex(ValueError,
                               r'Monsoon voltage can not be undefined.',
                               bits_service_config._BitsMonsoonConfig,
                               {'serial_num': 1},
                               lvpm_monsoon_bin='lvpm_bin')

    def test_monsoon_config_fails_without_serial(self):
        self.assertRaisesRegex(ValueError,
                               r'Monsoon serial_num can not be undefined.',
                               bits_service_config._BitsMonsoonConfig,
                               {'monsoon_voltage': 1},
                               lvpm_monsoon_bin='lvpm_bin')

    def test_monsoon_config_is_always_enabled(self):
        config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 1, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='bin')
        self.assertEqual(1, config.config_dic['enabled'])

    def test_monsoon_config_disables_monsoon_reseting(self):
        config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 1, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='bin')
        self.assertEqual(0, config.config_dic['monsoon_reset'])

    def test_monsoon_config_type_is_monsooncollector(self):
        config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 1, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='bin')
        self.assertEqual('monsooncollector', config.config_dic['type'])

    def test_bits_service_config_without_monsoon(self):
        service_config = bits_service_config.BitsServiceConfig({})
        self.assertFalse(service_config.has_monsoon)

    def test_bits_service_config_with_a_monsoon(self):
        service_config = bits_service_config.BitsServiceConfig(
            {'Monsoon': {'serial_num': 1, 'monsoon_voltage': 1}},
            lvpm_monsoon_bin='bin')
        config_dic = service_config.config_dic

        self.assertTrue(service_config.has_monsoon)
        self.assertIn('Monsoon',
                      config_dic['devices']['default_device'][
                          'collectors'])

        monsoon_config = bits_service_config._BitsMonsoonConfig(
            {'serial_num': 1, 'monsoon_voltage': 1},
            lvpm_monsoon_bin='bin').config_dic
        self.assertEqual(monsoon_config,
                         config_dic['devices']['default_device'][
                             'collectors']['Monsoon'])


class BitsServiceConfigWithKibblesTest(unittest.TestCase):
    def test_bits_service_config_without_kibbles(self):
        service_config = bits_service_config.BitsServiceConfig({})
        self.assertFalse(service_config.has_kibbles)

    def test_bits_service_config_with_kibbles_but_no_vm_files(self):
        service_config = bits_service_config.BitsServiceConfig({'Kibbles': [
            {'board': 'BOARD', 'connector': 'CONNECTOR', 'serial': 'SERIAL'}]},
            kibble_bin='bin',
            kibble_board_file='file.board')

        self.assertFalse(service_config.has_virtual_metrics_file)

    def test_bits_service_config_with_kibbles_and_vm_files(self):
        service_config = bits_service_config.BitsServiceConfig({'Kibbles': [
            {'board': 'BOARD', 'connector': 'CONNECTOR', 'serial': 'SERIAL'}]},
            kibble_bin='bin',
            kibble_board_file='file.board',
            virtual_metrics_file='some_file.vm')
        config_dic = service_config.config_dic

        self.assertTrue(service_config.has_virtual_metrics_file)
        self.assertIn('some_file.vm',
                      config_dic['devices']['default_device']['vm_files'])

    def test_bits_service_config_with_kibbles(self):
        service_config = bits_service_config.BitsServiceConfig({'Kibbles': [
            {'board': 'BOARD', 'connector': 'CONNECTOR', 'serial': 'SERIAL'}]},
            kibble_bin='bin',
            kibble_board_file='file.board')
        config_dic = service_config.config_dic

        self.assertTrue(service_config.has_kibbles)
        self.assertIn('BOARD',
                      config_dic['devices']['default_device']['collectors'])

        boards_config = bits_service_config._BitsKibblesConfig([
            {'board': 'BOARD', 'connector': 'CONNECTOR', 'serial': 'SERIAL'}],
            kibble_bin='bin', kibble_board_file='file.board').boards_configs
        self.assertEqual(boards_config['BOARD'],
                         config_dic['devices']['default_device'][
                             'collectors']['BOARD'])

    def test_kibbles_get_grouped_by_board(self):
        boards_config = bits_service_config._BitsKibblesConfig([
            {'board': 'BOARD1', 'connector': 'A', 'serial': 'SERIAL1'},
            {'board': 'BOARD2', 'connector': 'B', 'serial': 'SERIAL2'},
            {'board': 'BOARD2', 'connector': 'C', 'serial': 'SERIAL3'}],
            kibble_bin='bin',
            kibble_board_file='file.board').boards_configs

        self.assertIn('BOARD1', boards_config)
        board1 = boards_config['BOARD1']
        self.assertEqual(1, len(board1['attached_kibbles']))
        self.assertIn('SERIAL1', board1['attached_kibbles'])

        self.assertIn('BOARD2', boards_config)
        board2 = boards_config['BOARD2']
        self.assertEqual(2, len(board2['attached_kibbles']))
        self.assertIn('SERIAL2', board2['attached_kibbles'])
        self.assertIn('SERIAL3', board2['attached_kibbles'])

    def test_kibble_config_type_is_kibblecollector(self):
        board_config = bits_service_config._BitsKibblesConfig([
            {'board': 'BOARD', 'connector': 'CONNECTOR', 'serial': 'SERIAL'}],
            kibble_bin='bin',
            kibble_board_file='file.board').boards_configs['BOARD']

        self.assertEqual('kibblecollector', board_config['type'])


if __name__ == '__main__':
    unittest.main()
