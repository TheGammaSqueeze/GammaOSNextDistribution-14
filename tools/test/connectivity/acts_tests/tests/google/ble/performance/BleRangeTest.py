#!/usr/bin/env python3
#
# Copyright 2017 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
"""
Test script to execute BLE connection,run data traffic and calculating RSSI value of the remote BLE device.
"""

import os
import logging
import pandas as pd
import numpy as np
import time
import acts_contrib.test_utils.bt.bt_test_utils as btutils
import acts_contrib.test_utils.wifi.wifi_performance_test_utils.bokeh_figure as bokeh_figure
from acts_contrib.test_utils.bt.ble_performance_test_utils import ble_coc_connection
from acts_contrib.test_utils.bt.ble_performance_test_utils import ble_gatt_disconnection
from acts_contrib.test_utils.bt.ble_performance_test_utils import start_advertising_and_scanning
from acts_contrib.test_utils.bt.BluetoothBaseTest import BluetoothBaseTest
from acts_contrib.test_utils.bt.bt_test_utils import cleanup_scanners_and_advertisers
from acts_contrib.test_utils.bt.ble_performance_test_utils import establish_ble_connection
from acts_contrib.test_utils.bt.bt_constants import l2cap_max_inactivity_delay_after_disconnect
from acts_contrib.test_utils.bt.ble_performance_test_utils import run_ble_throughput
from acts_contrib.test_utils.bt.ble_performance_test_utils import read_ble_rssi
from acts_contrib.test_utils.bt.ble_performance_test_utils import read_ble_scan_rssi
from acts_contrib.test_utils.bt.bt_test_utils import reset_bluetooth
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test
from acts.signals import TestPass
from acts import utils

INIT_ATTEN = 0
MAX_RSSI = 92


class BleRangeTest(BluetoothBaseTest):
    active_adv_callback_list = []
    active_scan_callback_list = []

    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['attenuation_vector', 'system_path_loss']
        #'attenuation_vector' is a dict containing: start, stop and step of
        #attenuation changes
        self.unpack_userparams(req_params)

    def setup_class(self):
        super().setup_class()
        self.client_ad = self.android_devices[0]
        # The client which is scanning will need location to be enabled in order to
        # start scan and get scan results.
        utils.set_location_service(self.client_ad, True)
        self.server_ad = self.android_devices[1]
        # Note that some tests required a third device.
        if hasattr(self, 'attenuators'):
            self.attenuator = self.attenuators[0]
            self.attenuator.set_atten(INIT_ATTEN)
        self.attenuation_range = range(self.attenuation_vector['start'],
                                       self.attenuation_vector['stop'] + 1,
                                       self.attenuation_vector['step'])
        self.log_path = os.path.join(logging.log_path, 'results')
        os.makedirs(self.log_path, exist_ok=True)
        # BokehFigure object
        self.plot = bokeh_figure.BokehFigure(
            title='{}'.format(self.current_test_name),
            x_label='Pathloss (dB)',
            primary_y_label='BLE RSSI (dBm)',
            secondary_y_label='DUT Tx Power (dBm)',
            axis_label_size='16pt')
        if len(self.android_devices) > 2:
            self.server2_ad = self.android_devices[2]

        btutils.enable_bqr(self.android_devices)
        return setup_multiple_devices_for_bt_test(self.android_devices)

    def teardown_test(self):
        self.client_ad.droid.bluetoothSocketConnStop()
        self.server_ad.droid.bluetoothSocketConnStop()
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(INIT_ATTEN)
        # Give sufficient time for the physical LE link to be disconnected.
        time.sleep(l2cap_max_inactivity_delay_after_disconnect)
        cleanup_scanners_and_advertisers(self.client_ad,
                                         self.active_scan_callback_list,
                                         self.server_ad,
                                         self.active_adv_callback_list)

    def test_ble_gatt_connection_range(self):
        """Test GATT connection over LE and read RSSI.

        Test will establish a gatt connection between a GATT server and GATT
        client then read the RSSI for each attenuation until the BLE link get disconnect

        Expected Result:
        Verify that a connection was established and then disconnected
        successfully. Verify that the RSSI was read correctly.

        """
        attenuation = []
        ble_rssi = []
        dut_pwlv = []
        path_loss = []
        bluetooth_gatt, gatt_callback, adv_callback, gatt_server = establish_ble_connection(
            self.client_ad, self.server_ad)
        for atten in self.attenuation_range:
            ramp_attenuation(self.attenuator, atten)
            self.log.info('Set attenuation to %d dB', atten)
            rssi_primary, pwlv_primary = self.get_ble_rssi_and_pwlv()
            self.log.info(
                "Dut BLE RSSI:{} and Pwlv:{} with attenuation:{}".format(
                    rssi_primary, pwlv_primary, atten))
            rssi = self.client_ad.droid.gattClientReadRSSI(gatt_server)
            if type(rssi_primary) != str:
                attenuation.append(atten)
                ble_rssi.append(rssi_primary)
                dut_pwlv.append(pwlv_primary)
                path_loss.append(atten + self.system_path_loss)
                df = pd.DataFrame({
                    'Attenuation': attenuation,
                    'BLE_RSSI': ble_rssi,
                    'Dut_PwLv': dut_pwlv,
                    'Pathloss': path_loss
                })
                filepath = os.path.join(
                    self.log_path, '{}.csv'.format(self.current_test_name))
            else:
                self.plot_ble_graph(df)
                df.to_csv(filepath, encoding='utf-8')
                raise TestPass('Reached BLE Max Range, BLE Gatt disconnected')
        ble_gatt_disconnection(self.client_ad, bluetooth_gatt, gatt_callback)
        self.plot_ble_graph(df)
        df.to_csv(filepath, encoding='utf-8')
        self.server_ad.droid.bleStopBleAdvertising(adv_callback)
        return True

    def test_ble_coc_throughput_range(self):
        """Test LE CoC data transfer and read RSSI with each attenuation

        Test will establish a L2CAP CoC connection between client and server
        then start BLE date transfer and read the RSSI for each attenuation
        until the BLE link get disconnect

        Expected Result:
        BLE data transfer successful and Read RSSi Value of the server

        """
        attenuation = []
        ble_rssi = []
        throughput = []
        dut_pwlv = []
        path_loss = []
        self.plot_throughput = bokeh_figure.BokehFigure(
            title='{}'.format(self.current_test_name),
            x_label='Pathloss (dB)',
            primary_y_label='BLE Throughput (bits per sec)',
            axis_label_size='16pt')
        status, gatt_callback, gatt_server, bluetooth_gatt, client_conn_id = ble_coc_connection(
            self.server_ad, self.client_ad)
        for atten in self.attenuation_range:
            ramp_attenuation(self.attenuator, atten)
            self.log.info('Set attenuation to %d dB', atten)
            datarate = run_ble_throughput(self.client_ad, client_conn_id,
                                          self.server_ad)
            rssi_primary, pwlv_primary = self.get_ble_rssi_and_pwlv()
            self.log.info(
                "BLE RSSI is:{} dBm and Tx Power:{} with attenuation {} dB with throughput:{}bits per sec"
                .format(rssi_primary, pwlv_primary, atten, datarate))
            if type(rssi_primary) != str:
                attenuation.append(atten)
                ble_rssi.append(rssi_primary)
                dut_pwlv.append(pwlv_primary)
                throughput.append(datarate)
                path_loss.append(atten + self.system_path_loss)
                df = pd.DataFrame({
                    'Attenuation': attenuation,
                    'BLE_RSSI': ble_rssi,
                    'Dut_PwLv': dut_pwlv,
                    'Throughput': throughput,
                    'Pathloss': path_loss
                })
                filepath = os.path.join(
                    self.log_path, '{}.csv'.format(self.current_test_name))
                results_file_path = os.path.join(
                    self.log_path,
                    '{}_throughput.html'.format(self.current_test_name))
                self.plot_throughput.add_line(df['Pathloss'],
                                              df['Throughput'],
                                              legend='BLE Throughput',
                                              marker='square_x')
            else:
                self.plot_ble_graph(df)
                self.plot_throughput.generate_figure()
                bokeh_figure.BokehFigure.save_figures([self.plot_throughput],
                                                      results_file_path)
                df.to_csv(filepath, encoding='utf-8')
                raise TestPass('Reached BLE Max Range, BLE Gatt disconnected')
        self.plot_ble_graph(df)
        self.plot_throughput.generate_figure()
        bokeh_figure.BokehFigure.save_figures([self.plot_throughput],
                                              results_file_path)
        df.to_csv(filepath, encoding='utf-8')
        ble_gatt_disconnection(self.server_ad, bluetooth_gatt, gatt_callback)
        return True

    def test_ble_scan_remote_rssi(self):
        data_points = []
        for atten in self.attenuation_range:
            csv_path = os.path.join(
                self.log_path,
                '{}_attenuation_{}.csv'.format(self.current_test_name, atten))
            ramp_attenuation(self.attenuator, atten)
            self.log.info('Set attenuation to %d dB', atten)
            adv_callback, scan_callback = start_advertising_and_scanning(
                self.client_ad, self.server_ad, Legacymode=False)
            self.active_adv_callback_list.append(adv_callback)
            self.active_scan_callback_list.append(scan_callback)
            average_rssi, raw_rssi, timestamp = read_ble_scan_rssi(
                self.client_ad, scan_callback)
            self.log.info(
                "Scanned rssi list of the remote device is :{}".format(
                    raw_rssi))
            self.log.info(
                "BLE RSSI of the remote device is:{} dBm".format(average_rssi))
            min_rssi = min(raw_rssi)
            max_rssi = max(raw_rssi)
            path_loss = atten + self.system_path_loss
            std_deviation = np.std(raw_rssi)
            data_point = {
                'Attenuation': atten,
                'BLE_RSSI': average_rssi,
                'Pathloss': path_loss,
                'Min_RSSI': min_rssi,
                'Max_RSSI': max_rssi,
                'Standard_deviation': std_deviation
            }
            data_points.append(data_point)
            df = pd.DataFrame({'timestamp': timestamp, 'raw rssi': raw_rssi})
            df.to_csv(csv_path, encoding='utf-8', index=False)
            try:
                self.server_ad.droid.bleAdvSetStopAdvertisingSet(adv_callback)
            except Exception as err:
                self.log.warning(
                    "Failed to stop advertisement: {}".format(err))
                reset_bluetooth([self.server_ad])
            self.client_ad.droid.bleStopBleScan(scan_callback)
        filepath = os.path.join(
            self.log_path, '{}_summary.csv'.format(self.current_test_name))
        ble_df = pd.DataFrame(data_points)
        ble_df.to_csv(filepath, encoding='utf-8')
        return True

    def plot_ble_graph(self, df):
        """ Plotting BLE RSSI and Throughput with Attenuation.

        Args:
            df: Summary of results contains attenuation, BLE_RSSI and Throughput
        """
        self.plot.add_line(df['Pathloss'],
                           df['BLE_RSSI'],
                           legend='DUT BLE RSSI (dBm)',
                           marker='circle_x')
        self.plot.add_line(df['Pathloss'],
                           df['Dut_PwLv'],
                           legend='DUT TX Power (dBm)',
                           marker='hex',
                           y_axis='secondary')
        results_file_path = os.path.join(
            self.log_path, '{}.html'.format(self.current_test_name))
        self.plot.generate_figure()
        bokeh_figure.BokehFigure.save_figures([self.plot], results_file_path)

    def get_ble_rssi_and_pwlv(self):
        process_data_dict = btutils.get_bt_metric(self.client_ad)
        rssi_primary = process_data_dict.get('rssi')
        pwlv_primary = process_data_dict.get('pwlv')
        rssi_primary = rssi_primary.get(self.client_ad.serial)
        pwlv_primary = pwlv_primary.get(self.client_ad.serial)
        return rssi_primary, pwlv_primary
