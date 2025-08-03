#!/usr/bin/env python3
#
#   Copyright 2019 - The Android Open Source Project
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

import os
import re
import time
import logging
import pandas as pd

from acts import asserts
from acts.libs.proc import job
from acts.base_test import BaseTestClass

from acts.metrics.loggers.blackbox import BlackboxMetricLogger
from acts_contrib.test_utils.bt.bt_power_test_utils import MediaControl
from acts_contrib.test_utils.bt.ble_performance_test_utils import run_ble_throughput_and_read_rssi
from acts_contrib.test_utils.abstract_devices.bluetooth_handsfree_abstract_device import BluetoothHandsfreeAbstractDeviceFactory as bt_factory

import acts_contrib.test_utils.bt.bt_test_utils as bt_utils
import acts_contrib.test_utils.wifi.wifi_performance_test_utils as wifi_utils

PHONE_MUSIC_FILE_DIRECTORY = '/sdcard/Music'

FORCE_SAR_ADB_COMMAND = ('am broadcast -n'
                         'com.google.android.apps.scone/.coex.TestReceiver -a '
                         'com.google.android.apps.scone.coex.SIMULATE_STATE ')

SLEEP_DURATION = 2

DEFAULT_DURATION = 5
DEFAULT_MAX_ERROR_THRESHOLD = 2
DEFAULT_AGG_MAX_ERROR_THRESHOLD = 2
FIXED_ATTENUATION = 36


class BtSarBaseTest(BaseTestClass):
    """ Base class for all BT SAR Test classes.

        This class implements functions common to BT SAR test Classes.
    """
    BACKUP_BT_SAR_TABLE_NAME = 'backup_bt_sar_table.csv'

    def __init__(self, controllers):
        BaseTestClass.__init__(self, controllers)
        self.power_file_paths = [
            '/vendor/etc/bluetooth_power_limits.csv',
            '/data/vendor/radio/bluetooth_power_limits.csv'
        ]
        self.sar_test_result = BlackboxMetricLogger.for_test_case(
            metric_name='pass')
        self.sar_file_name = os.path.basename(self.power_file_paths[0])
        self.power_column = 'BluetoothPower'
        self.REG_DOMAIN_DICT = {
            ('us', 'ca', 'in'): 'US',
            ('uk', 'fr', 'es', 'de', 'it', 'ie', 'sg', 'au', 'tw'): 'EU',
            ('jp', ): 'JP'
        }

    def setup_class(self):
        """Initializes common test hardware and parameters.

        This function initializes hardware and compiles parameters that are
        common to all tests in this class and derived classes.
        """
        super().setup_class()

        self.test_params = self.user_params.get('bt_sar_test_params', {})
        if not self.test_params:
            self.log.warning(
                'bt_sar_test_params was not found in the config file.')

        self.user_params.update(self.test_params)
        req_params = ['bt_devices', 'calibration_params', 'custom_files']

        self.unpack_userparams(
            req_params,
            country_code='us',
            duration=DEFAULT_DURATION,
            sort_order=None,
            max_error_threshold=DEFAULT_MAX_ERROR_THRESHOLD,
            agg_error_threshold=DEFAULT_AGG_MAX_ERROR_THRESHOLD,
            tpc_threshold=[2, 8],
            sar_margin={
                'BDR': 0,
                'EDR': 0,
                'BLE': 0
            })

        self.attenuator = self.attenuators[0]
        self.dut = self.android_devices[0]

        for key in self.REG_DOMAIN_DICT.keys():
            if self.country_code.lower() in key:
                self.reg_domain = self.REG_DOMAIN_DICT[key]

        self.sar_version_2 = False

        if 'Error' not in self.dut.adb.shell('bluetooth_sar_test -r'):
            #Flag for SAR version 2
            self.sar_version_2 = True
            self.power_column = 'BluetoothEDRPower'
            self.power_file_paths[0] = os.path.join(
                os.path.dirname(self.power_file_paths[0]),
                'bluetooth_power_limits_{}.csv'.format(self.reg_domain))
            self.sar_file_name = os.path.basename(self.power_file_paths[0])

        if self.sar_version_2:
            custom_file_suffix = 'version2'
        else:
            custom_file_suffix = 'version1'

        for file in self.custom_files:
            if 'custom_sar_table_{}.csv'.format(custom_file_suffix) in file:
                self.custom_sar_path = file
                break
        else:
            raise RuntimeError('Custom Sar File is missing')

        self.sar_file_path = self.power_file_paths[0]
        self.atten_min = 0
        self.atten_max = int(self.attenuator.get_max_atten())

        # Get music file and push it to the phone and initialize Media controller
        music_files = self.user_params.get('music_files', [])
        if music_files:
            music_src = music_files[0]
            music_dest = PHONE_MUSIC_FILE_DIRECTORY
            success = self.dut.push_system_file(music_src, music_dest)
            if success:
                self.music_file = os.path.join(PHONE_MUSIC_FILE_DIRECTORY,
                                               os.path.basename(music_src))
            # Initialize media_control class
            self.media = MediaControl(self.dut, self.music_file)

        #Initializing BT device controller
        if self.bt_devices:
            attr, idx = self.bt_devices.split(':')
            self.bt_device_controller = getattr(self, attr)[int(idx)]
            self.bt_device = bt_factory().generate(self.bt_device_controller)
        else:
            self.log.error('No BT devices config is provided!')

        bt_utils.enable_bqr(self.android_devices)

        self.log_path = os.path.join(logging.log_path, 'results')
        os.makedirs(self.log_path, exist_ok=True)

        # Reading BT SAR table from the phone
        self.bt_sar_df = self.read_sar_table(self.dut)

    def setup_test(self):
        super().setup_test()

        #Reset SAR test result to 0 before every test
        self.sar_test_result.metric_value = 0

        # Starting BT on the master
        self.dut.droid.bluetoothFactoryReset()
        bt_utils.enable_bluetooth(self.dut.droid, self.dut.ed)

        # Starting BT on the slave
        self.bt_device.reset()
        self.bt_device.power_on()

        # Connect master and slave
        bt_utils.connect_phone_to_headset(self.dut, self.bt_device, 60)

        # Playing music
        self.media.play()

        # Find and set PL10 level for the DUT
        self.pl10_atten = self.set_PL10_atten_level(self.dut)
        self.attenuator.set_atten(self.pl10_atten)

    def teardown_test(self):
        #Stopping Music
        if hasattr(self, 'media'):
            self.media.stop()

        # Stopping BT on slave
        self.bt_device.reset()
        self.bt_device.power_off()

        #Stopping BT on master
        bt_utils.disable_bluetooth(self.dut.droid)

        #Resetting the atten to initial levels
        self.attenuator.set_atten(self.atten_min)
        self.log.info('Attenuation set to {} dB'.format(self.atten_min))

    def teardown_class(self):

        super().teardown_class()
        self.dut.droid.bluetoothFactoryReset()

        # Stopping BT on slave
        self.bt_device.reset()
        self.bt_device.power_off()

        #Stopping BT on master
        bt_utils.disable_bluetooth(self.dut.droid)

    def save_sar_plot(self, df):
        """ Saves SAR plot to the path given.

        Args:
            df: Processed SAR table sweep results
        """
        self.plot.add_line(
            df.index,
            df['expected_tx_power'],
            legend='expected',
            marker='circle')
        self.plot.add_line(
            df.index,
            df['measured_tx_power'],
            legend='measured',
            marker='circle')
        self.plot.add_line(
            df.index, df['delta'], legend='delta', marker='circle')

        results_file_path = os.path.join(self.log_path, '{}.html'.format(
            self.current_test_name))
        self.plot.generate_figure()
        wifi_utils.BokehFigure.save_figures([self.plot], results_file_path)

    def sweep_power_cap(self):
        sar_df = self.bt_sar_df
        sar_df['BDR_power_cap'] = -128
        sar_df['EDR_power_cap'] = -128
        sar_df['BLE_power_cap'] = -128

        if self.sar_version_2:
            power_column_dict = {
                'BDR': 'BluetoothBDRPower',
                'EDR': 'BluetoothEDRPower',
                'BLE': 'BluetoothLEPower'
            }
        else:
            power_column_dict = {'EDR': self.power_column}

        power_cap_error = False

        for type, column_name in power_column_dict.items():

            self.log.info("Performing sanity test on {}".format(type))
            # Iterating through the BT SAR scenarios
            for scenario in range(0, self.bt_sar_df.shape[0]):
                # Reading BT SAR table row into dict
                read_scenario = sar_df.loc[scenario].to_dict()
                start_time = self.dut.adb.shell('date +%s.%m')
                time.sleep(SLEEP_DURATION)

                # Setting SAR state to the read BT SAR row
                self.set_sar_state(self.dut, read_scenario, self.country_code)

                # Reading device power cap from logcat after forcing SAR State
                scenario_power_cap = self.get_current_power_cap(
                    self.dut, start_time, type=type)
                sar_df.loc[scenario, '{}_power_cap'.format(
                    type)] = scenario_power_cap
                self.log.info(
                    'scenario: {}, '
                    'sar_power: {}, power_cap:{}'.format(
                        scenario, sar_df.loc[scenario, column_name],
                        sar_df.loc[scenario, '{}_power_cap'.format(type)]))

        if not sar_df['{}_power_cap'.format(type)].equals(sar_df[column_name]):
            power_cap_error = True

        results_file_path = os.path.join(self.log_path, '{}.csv'.format(
            self.current_test_name))
        sar_df.to_csv(results_file_path)

        return power_cap_error

    def sweep_table(self,
                    client_ad=None,
                    server_ad=None,
                    client_conn_id=None,
                    gatt_server=None,
                    gatt_callback=None,
                    isBLE=False):
        """Iterates over the BT SAR table and forces signal states.

        Iterates over BT SAR table and forces signal states,
        measuring RSSI and power level for each state.

        Args:
            client_ad: the Android device performing the connection.
            server_ad: the Android device accepting the connection.
            client_conn_id: the client connection ID.
            gatt_server: the gatt server
            gatt_callback: Gatt callback objec
            isBLE : boolean variable for BLE connection
        Returns:
            sar_df : SAR table sweep results in pandas dataframe
        """

        sar_df = self.bt_sar_df.copy()
        sar_df['power_cap'] = -128
        sar_df['slave_rssi'] = -128
        sar_df['master_rssi'] = -128
        sar_df['ble_rssi'] = -128
        sar_df['pwlv'] = -1

        # Sorts the table
        if self.sort_order:
            if self.sort_order.lower() == 'ascending':
                sar_df = sar_df.sort_values(
                    by=[self.power_column], ascending=True)
            else:
                sar_df = sar_df.sort_values(
                    by=[self.power_column], ascending=False)
            sar_df = sar_df.reset_index(drop=True)

        # Sweeping BT SAR table
        for scenario in range(sar_df.shape[0]):
            # Reading BT SAR Scenario from the table
            read_scenario = sar_df.loc[scenario].to_dict()

            start_time = self.dut.adb.shell('date +%s.%m')
            time.sleep(SLEEP_DURATION)

            #Setting SAR State
            self.set_sar_state(self.dut, read_scenario, self.country_code)

            if isBLE:
                sar_df.loc[scenario, 'power_cap'] = self.get_current_power_cap(
                    self.dut, start_time, type='BLE')

                sar_df.loc[
                    scenario, 'ble_rssi'] = run_ble_throughput_and_read_rssi(
                        client_ad, server_ad, client_conn_id, gatt_server,
                        gatt_callback)

                self.log.info('scenario:{}, power_cap:{},  ble_rssi:{}'.format(
                    scenario, sar_df.loc[scenario, 'power_cap'],
                    sar_df.loc[scenario, 'ble_rssi']))
            else:
                sar_df.loc[scenario, 'power_cap'] = self.get_current_power_cap(
                    self.dut, start_time)

                processed_bqr_results = bt_utils.get_bt_metric(
                    self.android_devices, self.duration)
                sar_df.loc[scenario, 'slave_rssi'] = processed_bqr_results[
                    'rssi'][self.bt_device_controller.serial]
                sar_df.loc[scenario, 'master_rssi'] = processed_bqr_results[
                    'rssi'][self.dut.serial]
                sar_df.loc[scenario, 'pwlv'] = processed_bqr_results['pwlv'][
                    self.dut.serial]
                self.log.info(
                    'scenario:{}, power_cap:{},  s_rssi:{}, m_rssi:{}, m_pwlv:{}'
                    .format(scenario, sar_df.loc[scenario, 'power_cap'],
                            sar_df.loc[scenario, 'slave_rssi'],
                            sar_df.loc[scenario, 'master_rssi'],
                            sar_df.loc[scenario, 'pwlv']))

        self.log.info('BT SAR Table swept')

        return sar_df

    def process_table(self, sar_df):
        """Processes the results of sweep_table and computes BT TX power.

        Processes the results of sweep_table and computes BT TX power
        after factoring in the path loss and FTM offsets.

        Args:
             sar_df: BT SAR table after the sweep

        Returns:
            sar_df: processed BT SAR table
        """

        sar_df['pathloss'] = self.calibration_params['pathloss']

        if hasattr(self, 'pl10_atten'):
            sar_df['atten'] = self.pl10_atten
        else:
            sar_df['atten'] = FIXED_ATTENUATION

        # BT SAR Backoff for each scenario
        if self.sar_version_2:
            #Reads OTP values from the phone
            self.otp = bt_utils.read_otp(self.dut)

            #OTP backoff
            edr_otp = min(0, float(self.otp['EDR']['10']))
            bdr_otp = min(0, float(self.otp['BR']['10']))
            ble_otp = min(0, float(self.otp['BLE']['10']))

            # EDR TX Power for PL10
            edr_tx_power_pl10 = self.calibration_params['target_power']['EDR']['10'] - edr_otp

            # BDR TX Power for PL10
            bdr_tx_power_pl10 = self.calibration_params['target_power']['BDR']['10'] - bdr_otp

            # RSSI being measured is BDR
            offset = bdr_tx_power_pl10 - edr_tx_power_pl10

            # BDR-EDR offset
            sar_df['offset'] = offset

            # Max TX power permissible
            sar_df['max_power'] = self.calibration_params['max_power']

            # Adding a target power column
            if 'ble_rssi' in sar_df.columns:
                sar_df[
                    'target_power'] = self.calibration_params['target_power']['BLE']['10'] - ble_otp
            else:
                sar_df['target_power'] = sar_df['pwlv'].astype(str).map(
                    self.calibration_params['target_power']['EDR']) - edr_otp

            #Translates power_cap values to expected TX power level
            sar_df['cap_tx_power'] = sar_df['power_cap'] / 4.0

            sar_df['expected_tx_power'] = sar_df[[
                'cap_tx_power', 'target_power', 'max_power'
            ]].min(axis=1)

            if hasattr(self, 'pl10_atten'):
                sar_df[
                    'measured_tx_power'] = sar_df['slave_rssi'] + sar_df['pathloss'] + self.pl10_atten - offset
            else:
                sar_df[
                    'measured_tx_power'] = sar_df['ble_rssi'] + sar_df['pathloss'] + FIXED_ATTENUATION

        else:

            # Adding a target power column
            sar_df['target_power'] = sar_df['pwlv'].astype(str).map(
                self.calibration_params['target_power']['EDR']['10'])

            # Adding a ftm  power column
            sar_df['ftm_power'] = sar_df['pwlv'].astype(str).map(
                self.calibration_params['ftm_power']['EDR'])
            sar_df[
                'backoff'] = sar_df['target_power'] - sar_df['power_cap'] / 4.0

            sar_df[
                'expected_tx_power'] = sar_df['ftm_power'] - sar_df['backoff']
            sar_df[
                'measured_tx_power'] = sar_df['slave_rssi'] + sar_df['pathloss'] + self.pl10_atten

        sar_df[
            'delta'] = sar_df['expected_tx_power'] - sar_df['measured_tx_power']

        self.log.info('Sweep results processed')

        results_file_path = os.path.join(self.log_path, self.current_test_name)
        sar_df.to_csv('{}.csv'.format(results_file_path))
        self.save_sar_plot(sar_df)

        return sar_df

    def process_results(self, sar_df, type='EDR'):
        """Determines the test results of the sweep.

         Parses the processed table with computed BT TX power values
         to return pass or fail.

        Args:
             sar_df: processed BT SAR table
        """
        if self.sar_version_2:
            breach_error_result = (
                sar_df['expected_tx_power'] + self.sar_margin[type] >
                sar_df['measured_tx_power']).all()
            if not breach_error_result:
                asserts.fail('Measured TX power exceeds expected')

        else:
            # checks for errors at particular points in the sweep
            max_error_result = abs(
                sar_df['delta']) > self.max_error_threshold[type]
            if max_error_result:
                asserts.fail('Maximum Error Threshold Exceeded')

            # checks for error accumulation across the sweep
            if sar_df['delta'].sum() > self.agg_error_threshold[type]:
                asserts.fail(
                    'Aggregate Error Threshold Exceeded. Error: {} Threshold: {}'.
                    format(sar_df['delta'].sum(), self.agg_error_threshold))

        self.sar_test_result.metric_value = 1
        asserts.explicit_pass('Measured and Expected Power Values in line')

    def set_sar_state(self, ad, signal_dict, country_code='us'):
        """Sets the SAR state corresponding to the BT SAR signal.

        The SAR state is forced using an adb command that takes
        device signals as input.

        Args:
            ad: android_device object.
            signal_dict: dict of BT SAR signals read from the SAR file.
        Returns:
            enforced_state: dict of device signals.
        """
        signal_dict = {k: max(int(v), 0) for (k, v) in signal_dict.items()}
        signal_dict["Wifi"] = signal_dict['WIFI5Ghz']
        signal_dict['WIFI2Ghz'] = 0 if signal_dict['WIFI5Ghz'] else 1

        device_state_dict = {
            ('Earpiece', 'earpiece'): signal_dict['Head'],
            ('Wifi', 'wifi'): signal_dict['WIFI5Ghz'],
            ('Wifi 2.4G', 'wifi_24g'): signal_dict['WIFI2Ghz'],
            ('Voice', 'voice'): 0,
            ('Wifi AP', 'wifi_ap'): signal_dict['HotspotVoice'],
            ('Bluetooth', 'bluetooth'): 1,
            ('Bluetooth media', 'bt_media'): signal_dict['BTMedia'],
            ('Radio', 'radio_power'): signal_dict['Cell'],
            ('Motion', 'motion'): signal_dict['IMU'],
            ('Bluetooth connected', 'bt_connected'): 1
        }

        if 'BTHotspot' in signal_dict.keys():
            device_state_dict[('Bluetooth tethering',
                               'bt_tethering')] = signal_dict['BTHotspot']

        enforced_state = {}
        sar_state_command = FORCE_SAR_ADB_COMMAND
        for key in device_state_dict:
            enforced_state[key[0]] = device_state_dict[key]
            sar_state_command = '{} --ei {} {}'.format(
                sar_state_command, key[1], device_state_dict[key])
        if self.sar_version_2:
            sar_state_command = '{} --es country_iso "{}"'.format(
                sar_state_command, country_code.lower())

        #Forcing the SAR state
        adb_output = ad.adb.shell(sar_state_command)

        # Checking if command was successfully enforced
        if 'result=0' in adb_output:
            self.log.info('Requested BT SAR state successfully enforced.')
            return enforced_state
        else:
            self.log.error("Couldn't force BT SAR state.")

    def parse_bt_logs(self, ad, begin_time, regex=''):
        """Returns bt software stats by parsing logcat since begin_time.

        The quantity to be fetched is dictated by the regex provided.

        Args:
             ad: android_device object.
             begin_time: time stamp to start the logcat parsing.
             regex: regex for fetching the required BT software stats.

        Returns:
             stat: the desired BT stat.
        """
        # Waiting for logcat to update
        time.sleep(SLEEP_DURATION)
        bt_adb_log = ad.adb.logcat('-b all -t %s' % begin_time)
        for line in bt_adb_log.splitlines():
            if re.findall(regex, line):
                stat = re.findall(regex, line)[0]
                return stat

    def set_country_code(self, ad, cc):
        """Sets the SAR regulatory domain as per given country code

        The SAR regulatory domain is forced using an adb command that takes
        country code as input.

        Args:
            ad: android_device object.
            cc: country code
        """

        ad.adb.shell("{} --es country_iso {}".format(FORCE_SAR_ADB_COMMAND,
                                                     cc))
        self.log.info("Country Code set to {}".format(cc))

    def get_country_code(self, ad, begin_time):
        """Returns the enforced regulatory domain since begin_time

        Returns enforced regulatory domain since begin_time by parsing logcat.
        Function should follow a function call to set a country code

        Args:
            ad : android_device obj
            begin_time: time stamp to start

        Returns:
            read enforced regulatory domain
        """

        reg_domain_regex = "updateRegulatoryDomain:\s+(\S+)"
        reg_domain = self.parse_bt_logs(ad, begin_time, reg_domain_regex)
        return reg_domain

    def get_current_power_cap(self, ad, begin_time, type='EDR'):
        """ Returns the enforced software EDR power cap since begin_time.

        Returns the enforced EDR power cap since begin_time by parsing logcat.
        Function should follow a function call that forces a SAR state

        Args:
            ad: android_device obj.
            begin_time: time stamp to start.

        Returns:
            read enforced power cap
        """
        power_cap_regex_dict = {
            'BDR': [
                'Bluetooth powers: BR:\s+(\d+), EDR:\s+\d+',
                'Bluetooth Tx Power Cap\s+(\d+)'
            ],
            'EDR': [
                'Bluetooth powers: BR:\s+\d+, EDR:\s+(\d+)',
                'Bluetooth Tx Power Cap\s+(\d+)'
            ],
            'BLE': [
                'Bluetooth powers: BR:\s+\d+, EDR:\s+\d+, BLE:\s+(\d+)',
                'Bluetooth Tx Power Cap\s+(\d+)'
            ]
        }

        power_cap_regex_list = power_cap_regex_dict[type]

        for power_cap_regex in power_cap_regex_list:
            power_cap = self.parse_bt_logs(ad, begin_time, power_cap_regex)
            if power_cap:
                return int(power_cap)

        raise ValueError('Failed to get TX power cap')

    def get_current_device_state(self, ad, begin_time):
        """ Returns the device state of the android dut since begin_time.

        Returns the device state of the android dut by parsing logcat since
        begin_time. Function should follow a function call that forces
        a SAR state.

        Args:
            ad: android_device obj.
            begin_time: time stamp to start.

        Returns:
            device_state: device state of the android device.
        """

        device_state_regex = 'updateDeviceState: DeviceState: ([\s*\S+\s]+)'
        time.sleep(SLEEP_DURATION)
        device_state = self.parse_bt_logs(ad, begin_time, device_state_regex)
        if device_state:
            return device_state

        raise ValueError("Couldn't fetch device state")

    def read_sar_table(self, ad, output_path=''):
        """Extracts the BT SAR table from the phone.

        Extracts the BT SAR table from the phone into the android device
        log path directory.

        Args:
            ad: android_device object.
            output_path: path to custom sar table
        Returns:
            df : BT SAR table (as pandas DataFrame).
        """
        if not output_path:
            output_path = os.path.join(ad.device_log_path, self.sar_file_name)
            ad.adb.pull('{} {}'.format(self.sar_file_path, output_path))

        df = pd.read_csv(output_path)
        self.log.info('BT SAR table read from the phone')
        return df

    def push_table(self, ad, src_path, dest_path=''):
        """Pushes a BT SAR table to the phone.

        Pushes a BT SAR table to the android device and reboots the device.
        Also creates a backup file if backup flag is True.

        Args:
            ad: android_device object.
            src_path: path to the  BT SAR table.
        """
        #Copying the to-be-pushed file for logging
        if os.path.dirname(src_path) != ad.device_log_path:
            job.run('cp {} {}'.format(src_path, ad.device_log_path))

        #Pushing the file provided in the config
        if dest_path:
            ad.push_system_file(src_path, dest_path)
        else:
            ad.push_system_file(src_path, self.sar_file_path)
        self.log.info('BT SAR table pushed')
        ad.reboot()

        self.bt_sar_df = self.read_sar_table(self.dut, src_path)

    def set_PL10_atten_level(self, ad):
        """Finds the attenuation level at which the phone is at PL10

        Finds PL10 attenuation level by sweeping the attenuation range.
        If the power level is not achieved during sweep,
        returns the max atten level

        Args:
            ad: android object class
        Returns:
            atten : attenuation level when the phone is at PL10
        """
        BT_SAR_ATTEN_STEP = 3

        for atten in range(self.atten_min, self.atten_max, BT_SAR_ATTEN_STEP):
            self.attenuator.set_atten(atten)
            # Sleep required for BQR to reflect the change in parameters
            time.sleep(SLEEP_DURATION)
            metrics = bt_utils.get_bt_metric(ad)
            if metrics['pwlv'][ad.serial] == 10:
                self.log.info(
                    'PL10 located at {}'.format(atten + BT_SAR_ATTEN_STEP))
                return atten + BT_SAR_ATTEN_STEP

        self.log.warn(
            "PL10 couldn't be located in the given attenuation range")
