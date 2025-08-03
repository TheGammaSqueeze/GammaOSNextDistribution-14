#!/usr/bin/env python3
#
# Copyright (C) 2019 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License"); you may not
# use this file except in compliance with the License. You may obtain a copy of
# the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
# WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
# License for the specific language governing permissions and limitations under
# the License.

import time
from acts_contrib.test_utils.bt.A2dpBaseTest import A2dpBaseTest
import acts_contrib.test_utils.bt.BleBaseTest as BleBT
from acts_contrib.test_utils.power.IperfHelper import IperfHelper
from acts_contrib.test_utils.bt.bt_test_utils import orchestrate_rfcomm_connection
from concurrent.futures import ThreadPoolExecutor
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.tel.tel_test_utils import WIFI_CONFIG_APBAND_2G
from acts_contrib.test_utils.wifi import wifi_power_test_utils as wputils


class BtMultiprofileBaseTest(A2dpBaseTest, BleBT.BleBaseTest):
    """Base class for BT mutiprofile related tests.

     Inherited from the A2DP Base class, Ble Base class
     """
    # Iperf waiting time (margin)
    IPERF_MARGIN = 10

    def mutiprofile_test(self,
                         codec_config=None,
                         mode=None,
                         victim=None,
                         aggressor=None,
                         metric=None):

        if victim == 'A2DP' and aggressor == 'Ble_Scan' and metric == 'range':
            scan_callback = self.start_ble_scan(self.dut, mode)
            self.run_a2dp_to_max_range(codec_config)
            self.dut.droid.bleStopBleScan(scan_callback)
            self.log.info("BLE Scan stopped successfully")
            return True

        if victim == 'Ble_Scan' and aggressor == 'A2DP' and metric == 'scan_accuracy':
            scan_callback = self.start_ble_scan(self.dut, mode)
            recorded_file = self.play_and_record_audio(
                self.audio_params['duration'])
            self.dut.droid.bleStopBleScan(scan_callback)
            self.media.stop()
            self.log.info("BLE Scan & A2DP streaming stopped successfully")
            return True

        if victim == 'RFCOMM' and aggressor == 'Ble_Scan' and metric == 'throughput':
            self.remote_device = self.android_devices[2]
            scan_callback = self.start_ble_scan(self.dut, mode)
            if not orchestrate_rfcomm_connection(self.dut, self.remote_device):
                return False
            self.log.info("RFCOMM Connection established")
            self.measure_rfcomm_throughput(100)
            self.dut.droid.bleStopBleScan(scan_callback)
            self.log.info("BLE Scan stopped successfully")

        if victim == 'A2DP' and aggressor == 'Ble_Adv' and metric == 'range':
            advertise_callback = self.start_ble_adv(self.dut, mode, 2)
            self.run_a2dp_to_max_range(codec_config)
            self.dut.droid.bleStopBleAdvertising(advertise_callback)
            self.log.info("Advertisement stopped Successfully")
            return True

        if victim == 'A2DP' and aggressor == 'Ble_conn' and metric == 'range':
            self.start_ble_connection(self.dut, self.android_devices[2], mode)
            self.run_a2dp_to_max_range(codec_config)
            return True

        if victim == 'A2DP' and aggressor == 'wifi' and metric == 'range':
            self.setup_hotspot_and_connect_client()
            self.setup_iperf_and_run_throughput()
            self.run_a2dp_to_max_range(codec_config)
            self.process_iperf_results()
            return True

        if victim == 'Ble_Scan' and aggressor == 'wifi' and metric == 'scan_accuracy':
            scan_callback = self.start_ble_scan(self.dut, mode)
            self.setup_hotspot_and_connect_client()
            self.setup_iperf_and_run_throughput()
            time.sleep(self.audio_params['duration'] + self.IPERF_MARGIN + 2)
            self.log.info("BLE Scan & iPerf started successfully")
            self.process_iperf_results()
            self.dut.droid.bleStopBleScan(scan_callback)
            self.log.info("BLE Scan stopped successfully")
            return True

        if victim == 'Ble_Adv' and aggressor == 'wifi' and metric == 'periodic_adv':
            advertise_callback = self.start_ble_adv(self.dut, mode, 2)
            self.setup_hotspot_and_connect_client()
            self.setup_iperf_and_run_throughput()
            time.sleep(self.audio_params['duration'] + self.IPERF_MARGIN + 2)
            self.log.info("BLE Advertisement & iPerf started successfully")
            self.process_iperf_results()
            self.dut.droid.bleStopBleAdvertising(advertise_callback)
            self.log.info("Advertisement stopped Successfully")
            return True

        if victim == 'RFCOMM' and aggressor == 'wifi' and metric == 'throughput':
            self.remote_device = self.android_devices[2]
            if not orchestrate_rfcomm_connection(self.dut, self.remote_device):
                return False
            self.log.info("RFCOMM Connection established")
            self.setup_hotspot_and_connect_client()
            executor = ThreadPoolExecutor(2)
            throughput = executor.submit(self.measure_rfcomm_throughput, 100)
            executor.submit(self.setup_iperf_and_run_throughput, )
            time.sleep(self.audio_params['duration'] + self.IPERF_MARGIN + 10)
            self.process_iperf_results()
            return True

    def measure_rfcomm_throughput(self, iteration):
        """Measures the throughput of a data transfer.

        Sends data over RFCOMM from the client device that is read by the server device.
        Calculates the throughput for the transfer.

        Args:
           iteration : An integer value that respesents number of RFCOMM data trasfer iteration

        Returns:
            The throughput of the transfer in bits per second.
        """
        #An integer value designating the number of buffers to be sent.
        num_of_buffers = 1
        #An integer value designating the size of each buffer, in bytes.
        buffer_size = 22000
        throughput_list = []
        for transfer in range(iteration):
            (self.dut.droid.bluetoothConnectionThroughputSend(
                num_of_buffers, buffer_size))

            throughput = (
                self.remote_device.droid.bluetoothConnectionThroughputRead(
                    num_of_buffers, buffer_size))
            throughput = throughput * 8
            throughput_list.append(throughput)
            self.log.info(
                ("RFCOMM Throughput is :{} bits/sec".format(throughput)))
        throughput = statistics.mean(throughput_list)
        return throughput

    def setup_hotspot_and_connect_client(self):
        """
        Setup hotspot on the remote device and client connects to hotspot

        """
        self.network = {
            wutils.WifiEnums.SSID_KEY: 'Pixel_2G',
            wutils.WifiEnums.PWD_KEY: '1234567890'
        }
        # Setup tethering on dut
        wutils.start_wifi_tethering(self.android_devices[1],
                                    self.network[wutils.WifiEnums.SSID_KEY],
                                    self.network[wutils.WifiEnums.PWD_KEY],
                                    WIFI_CONFIG_APBAND_2G)

        # Connect client device to Hotspot
        wutils.wifi_connect(self.dut, self.network, check_connectivity=False)

    def setup_iperf_and_run_throughput(self):
        self.iperf_server_address = self.android_devices[
            1].droid.connectivityGetIPv4Addresses('wlan2')[0]
        # Create the iperf config
        iperf_config = {
            'traffic_type': 'TCP',
            'duration': self.audio_params['duration'] + self.IPERF_MARGIN,
            'server_idx': 0,
            'traffic_direction': 'UL',
            'port': self.iperf_servers[0].port,
            'start_meas_time': 4,
        }
        # Start iperf traffic (dut is the client)
        self.client_iperf_helper = IperfHelper(iperf_config)
        self.iperf_servers[0].start()
        wputils.run_iperf_client_nonblocking(
            self.dut, self.iperf_server_address,
            self.client_iperf_helper.iperf_args)

    def process_iperf_results(self):
        time.sleep(self.IPERF_MARGIN + 2)
        self.client_iperf_helper.process_iperf_results(self.dut, self.log,
                                                       self.iperf_servers,
                                                       self.test_name)
        self.iperf_servers[0].stop()
        return True
