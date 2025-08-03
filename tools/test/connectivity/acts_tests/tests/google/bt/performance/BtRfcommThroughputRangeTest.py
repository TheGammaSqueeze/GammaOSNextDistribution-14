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

import os
import logging
import pandas as pd
import time
import acts_contrib.test_utils.bt.bt_test_utils as btutils
from acts_contrib.test_utils.bt.BluetoothBaseTest import BluetoothBaseTest
from acts_contrib.test_utils.bt.ble_performance_test_utils import plot_graph
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation
from acts_contrib.test_utils.bt.bt_test_utils import setup_multiple_devices_for_bt_test
from acts_contrib.test_utils.bt.bt_test_utils import orchestrate_rfcomm_connection
from acts.signals import TestPass
from acts import utils
from acts_contrib.test_utils.bt.bt_test_utils import write_read_verify_data

INIT_ATTEN = 0
WRITE_ITERATIONS = 500


class BtRfcommThroughputRangeTest(BluetoothBaseTest):
    def __init__(self, configs):
        super().__init__(configs)
        req_params = ['attenuation_vector', 'system_path_loss']
        #'attenuation_vector' is a dict containing: start, stop and step of
        #attenuation changes
        self.unpack_userparams(req_params)

    def setup_class(self):
        super().setup_class()
        self.dut = self.android_devices[0]
        self.remote_device = self.android_devices[1]
        btutils.enable_bqr(self.android_devices)
        if hasattr(self, 'attenuators'):
            self.attenuator = self.attenuators[0]
            self.attenuator.set_atten(INIT_ATTEN)
        self.attenuation_range = range(self.attenuation_vector['start'],
                                       self.attenuation_vector['stop'] + 1,
                                       self.attenuation_vector['step'])
        self.log_path = os.path.join(logging.log_path, 'results')
        os.makedirs(self.log_path, exist_ok=True)
        return setup_multiple_devices_for_bt_test(self.android_devices)

    def teardown_test(self):
        self.dut.droid.bluetoothSocketConnStop()
        self.remote_device.droid.bluetoothSocketConnStop()
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(INIT_ATTEN)

    def test_rfcomm_throughput_range(self):
        data_points = []
        message = "x" * 990
        self.file_output = os.path.join(
            self.log_path, '{}.csv'.format(self.current_test_name))
        if not orchestrate_rfcomm_connection(self.dut, self.remote_device):
            return False
        self.log.info("RFCOMM Connection established")
        for atten in self.attenuation_range:
            ramp_attenuation(self.attenuator, atten)
            self.log.info('Set attenuation to %d dB', atten)
            process_data_dict = btutils.get_bt_metric(self.dut)
            rssi_primary = process_data_dict.get('rssi')
            pwlv_primary = process_data_dict.get('pwlv')
            rssi_primary = rssi_primary.get(self.dut.serial)
            pwlv_primary = pwlv_primary.get(self.dut.serial)
            self.log.info("DUT RSSI:{} and PwLv:{} with attenuation:{}".format(
                rssi_primary, pwlv_primary, atten))
            if type(rssi_primary) != str:
                data_rate = self.write_read_verify_rfcommdata(
                    self.dut, self.remote_device, message)
                data_point = {
                    'attenuation_db': atten,
                    'Dut_RSSI': rssi_primary,
                    'DUT_PwLv': pwlv_primary,
                    'Pathloss': atten + self.system_path_loss,
                    'RfcommThroughput': data_rate
                }
                data_points.append(data_point)
                df = pd.DataFrame(data_points)
                # bokeh data for generating BokehFigure
                bokeh_data = {
                    'x_label': 'Pathloss (dBm)',
                    'primary_y_label': 'RSSI (dBm)',
                    'log_path': self.log_path,
                    'current_test_name': self.current_test_name
                }
                # plot_data for adding line to existing BokehFigure
                plot_data = {
                    'line_one': {
                        'x_column': 'Pathloss',
                        'y_column': 'Dut_RSSI',
                        'legend': 'DUT RSSI (dBm)',
                        'marker': 'circle_x',
                        'y_axis': 'default'
                    },
                    'line_two': {
                        'x_column': 'Pathloss',
                        'y_column': 'RfcommThroughput',
                        'legend': 'RFCOMM Throughput (bits/sec)',
                        'marker': 'hex',
                        'y_axis': 'secondary'
                    }
                }
            else:
                df.to_csv(self.file_output, index=False)
                plot_graph(df,
                           plot_data,
                           bokeh_data,
                           secondary_y_label='RFCOMM Throughput (bits/sec)')
                raise TestPass("Reached RFCOMM Max Range,RFCOMM disconnected.")
        # Save Data points to csv
        df.to_csv(self.file_output, index=False)
        # Plot graph
        plot_graph(df,
                   plot_data,
                   bokeh_data,
                   secondary_y_label='RFCOMM Throughput (bits/sec)')
        self.dut.droid.bluetoothRfcommStop()
        self.remote_device.droid.bluetoothRfcommStop()
        return True

    def write_read_verify_rfcommdata(self, dut, remote_device, msg):
        """Verify that the client wrote data to the remote Android device correctly.

        Args:
            dut: the Android device to perform the write.
            remote_device: the Android device to read the data written.
            msg: the message to write.
        Returns:
            True if the data written matches the data read, false if not.
        """
        start_write_time = time.perf_counter()
        for n in range(WRITE_ITERATIONS):
            try:
                dut.droid.bluetoothSocketConnWrite(msg)
            except Exception as err:
                dut.log.error("Failed to write data: {}".format(err))
                return False
            try:
                read_msg = remote_device.droid.bluetoothSocketConnRead()
            except Exception as err:
                remote_device.log.error("Failed to read data: {}".format(err))
                return False
            if msg != read_msg:
                self.log.error("Mismatch! Read: {}, Expected: {}".format(
                    read_msg, msg))
                return False
        end_read_time = time.perf_counter()
        total_num_bytes = 990 * WRITE_ITERATIONS
        test_time = (end_read_time - start_write_time)
        if (test_time == 0):
            dut.log.error("Buffer transmits cannot take zero time")
            return 0
        data_rate = (1.000 * total_num_bytes) / test_time
        self.log.info(
            "Calculated using total write and read times: total_num_bytes={}, "
            "test_time={}, data rate={:08.0f} bytes/sec, {:08.0f} bits/sec".
            format(total_num_bytes, test_time, data_rate, (data_rate * 8)))
        data_rate = data_rate * 8
        return data_rate
