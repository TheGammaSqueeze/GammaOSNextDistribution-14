# Lint as: python2, python3
# Copyright 2022 The Chromium OS Authors. All rights reserved.
# Use of this source code is governed by a BSD-style license that can be
# found in the LICENSE file.
"""Server side Bluetooth Quality Report tests."""

from __future__ import absolute_import
from __future__ import division
from __future__ import print_function

import collections
import logging
import os
from threading import Thread
import time

from autotest_lib.client.common_lib import error
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_audio_tests import (
        BluetoothAdapterAudioTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_hidreports_tests import (
        BluetoothAdapterHIDReportTests)
from autotest_lib.server.cros.bluetooth.bluetooth_adapter_tests import (
        test_retry_and_log)

# List of the controllers that does not support the Bluetooth Quality Report.
QR_UNSUPPORTED_CHIPSETS = [
        'MVL-8897', 'MVL-8997',
        'Intel-AC7260', 'Intel-AC7265',
        'QCA-6174A-3-UART', 'QCA-6174A-5-USB'
]

# An example AOSP BQR event in btsnoop.log looks like:
# = bluetoothd: quality: BQR Quality Report                       75.018599
# = bluetoothd: quality:   quality_report_id 1                    75.018658
# = bluetoothd: quality:   packet_type 2                          75.019402
# = bluetoothd: quality:   conn_handle 1                          75.019477
# = bluetoothd: quality:   conn_role 0                            75.019539
# = bluetoothd: quality:   tx_power_level 0                       75.019601
# = bluetoothd: quality:   rssi -29                               75.019665
# = bluetoothd: quality:   snr 0                                  75.019727
# = bluetoothd: quality:   unused_afh_channel_count 3             75.019787
# = bluetoothd: quality:   afh_select_unideal_channel_count 0     75.019847
# = bluetoothd: quality:   lsto 20000.00                          75.019906
# = bluetoothd: quality:   conn_piconet_clock 9143780.00          75.019965
# = bluetoothd: quality:   retransmission_count 0                 75.020050
# = bluetoothd: quality:   no_rx_count 0                          75.020120
# = bluetoothd: quality:   nak_count 0                            75.020420
# = bluetoothd: quality:   last_tx_ack_timestamp 9143754.06       75.020485
# = bluetoothd: quality:   flow_off_count 0                       75.020551
# = bluetoothd: quality:   last_flow_on_timestamp 9143779.06      75.020610
# = bluetoothd: quality:   buffer_overflow_bytes 0                75.020670
# = bluetoothd: quality:   buffer_underflow_bytes 150492          75.020732

# An example Telemetry event for A2DP (ACL) in btsnoop.log looks like:
# = bluetoothd: quality: Intel Extended Telemetry Event           5.251502
# = bluetoothd: quality:   ACL connection handle: 0x0100          5.251520
# = bluetoothd: quality:   Rx HEC errors: 0                       5.251546
# = bluetoothd: quality:   Rx CRC errors: 0                       5.251558
# = bluetoothd: quality:   Packets from host: 222                 5.251581
# = bluetoothd: quality:   Tx packets: 221                        5.251594
# = bluetoothd: quality:   Tx packets 0 retries: 217              5.251617
# = bluetoothd: quality:   Tx packets 1 retries: 4                5.251630
# = bluetoothd: quality:   Tx packets 2 retries: 0                5.251651
# = bluetoothd: quality:   Tx packets 3 retries: 0                5.251662
# = bluetoothd: quality:   Tx packets 4 retries: 0                5.251686
# = bluetoothd: quality:   Tx DH1 packets: 0                      5.251703
# = bluetoothd: quality:   Tx DH3 packets: 0                      5.251725
# = bluetoothd: quality:   Tx DH5 packets: 0                      5.251762
# = bluetoothd: quality:   Tx 2DH1 packets: 0                     5.251790
# = bluetoothd: quality:   Tx 2DH3 packets: 0                     5.251818
# = bluetoothd: quality:   Tx 2DH5 packets: 0                     5.251847
# = bluetoothd: quality:   Tx 3DH1 packets: 55                    5.251872
# = bluetoothd: quality:   Tx 3DH3 packets: 2                     5.251898
# = bluetoothd: quality:   Tx 3DH5 packets: 164                   5.251926
# = bluetoothd: quality:   Rx packets: 1304                       5.251953
# = bluetoothd: quality:   ACL link throughput: 97143             5.251978
# = bluetoothd: quality:   ACL max packet latency: 25625          5.252023
# = bluetoothd: quality:   ACL avg packet latency: 9143           5.252052

# An example Telemetry events for HFP (SCO) in btsnoop.log looks like:
# = bluetoothd: quality: Intel Extended Telemetry Event                5.894338
# = bluetoothd: quality:   SCO connection handle: 0x010a               5.894359
# = bluetoothd: quality:   Packets from host: 1584                     5.894378
# = bluetoothd: quality:   Tx packets: 637                             5.894397
# = bluetoothd: quality:   Rx payload lost: 0                          5.894417
# = bluetoothd: quality:   Tx payload lost: 24                         5.894436
# = bluetoothd: quality:   Rx No SYNC errors (slot 0): 0               5.894454
# = bluetoothd: quality:   Rx No SYNC errors (slot 1): 20              5.894474
# = bluetoothd: quality:   Rx No SYNC errors (slot 2): 0               5.894492
# = bluetoothd: quality:   Rx No SYNC errors (slot 3): 0               5.894511
# = bluetoothd: quality:   Rx No SYNC errors (slot 4): 0               5.894531
# = bluetoothd: quality:   Rx HEC errors (slot 0): 65536               5.894550
# = bluetoothd: quality:   Rx HEC errors (slot 1): 1                   5.894569
# = bluetoothd: quality:   Rx HEC errors (slot 2): 0                   5.894590
# = bluetoothd: quality:   Rx HEC errors (slot 3): 0                   5.894608
# = bluetoothd: quality:   Rx HEC errors (slot 4): 0                   5.894627
# = bluetoothd: quality:   Rx CRC errors (slot 0): 0                   5.894645
# = bluetoothd: quality:   Rx CRC errors (slot 1): 0                   5.894664
# = bluetoothd: quality:   Rx CRC errors (slot 2): 0                   5.894682
# = bluetoothd: quality:   Rx CRC errors (slot 3): 0                   5.894701
# = bluetoothd: quality:   Rx CRC errors (slot 4): 0                   5.894720
# = bluetoothd: quality:   Rx NAK errors (slot 0): 41549824            5.894738
# = bluetoothd: quality:   Rx NAK errors (slot 1): 4                   5.894757
# = bluetoothd: quality:   Rx NAK errors (slot 2): 0                   5.894775
# = bluetoothd: quality:   Rx NAK errors (slot 3): 0                   5.894806
# = bluetoothd: quality:   Rx NAK errors (slot 4): 0                   5.894824
# = bluetoothd: quality:   Failed Tx due to Wifi coex (slot 0): 0      5.894843
# = bluetoothd: quality:   Failed Tx due to Wifi coex (slot 1): 0      5.894861
# = bluetoothd: quality:   Failed Tx due to Wifi coex (slot 2): 0      5.894876
# = bluetoothd: quality:   Failed Tx due to Wifi coex (slot 3): 0      5.894890
# = bluetoothd: quality:   Failed Tx due to Wifi coex (slot 4): 0      5.894903
# = bluetoothd: quality:   Failed Rx due to Wifi coex (slot 0): 0      5.894917
# = bluetoothd: quality:   Failed Rx due to Wifi coex (slot 1): 0      5.894930
# = bluetoothd: quality:   Failed Rx due to Wifi coex (slot 2): 0      5.894944
# = bluetoothd: quality:   Failed Rx due to Wifi coex (slot 3): 0      5.894957
# = bluetoothd: quality:   Failed Rx due to Wifi coex (slot 4): 0      5.894971
# = bluetoothd: quality:   Late samples inserted based on CDC: 0       5.894984
# = bluetoothd: quality:   Samples dropped: 0                          5.894997
# = bluetoothd: quality:   Mute samples sent at initial connection: 18 5.895032
# = bluetoothd: quality:   PLC injection data: 0                       5.895050

# Define constants
QR_EVENT_PERIOD = 5
TELEMETRY_NUM_SLOTS = 5
TELEMETRY_NUM_RETRIES = 5
TELEMETRY_NUM_PACKET_TYPES = 9

# Define event types
AOSP_BQR = 0
TELEMETRY_ACL = 1
TELEMETRY_SCO = 2

# Define event subevts
AOSP_SUBEVTS = [
        'quality_report_id', 'packet_type', 'conn_handle', 'conn_role',
        'tx_power_level', 'rssi', 'snr', 'unused_afh_channel_count',
        'afh_select_unideal_channel_count', 'lsto', 'conn_piconet_clock',
        'retransmission_count', 'no_rx_count', 'nak_count',
        'last_tx_ack_timestamp', 'flow_off_count',
        'last_flow_on_timestamp', 'buffer_overflow_bytes',
        'buffer_underflow_bytes'
]

BREDR_PACKET_TYPE = [
        'DH1', 'DH3', 'DH5', '2DH1', '2DH3', '2DH5', '3DH1', '3DH3', '3DH5'
]

TELEMETRY_ACL_SUBEVTS = [
        'ACL_connection_handle', 'Rx_HEC_errors', 'Rx_CRC_errors',
        'Packets_from_host', 'Tx_packets', 'Rx_packets',
        'ACL_link_throughput', 'ACL_max_packet_latency',
        'ACL_avg_packet_latency'
]

for t in BREDR_PACKET_TYPE:
    TELEMETRY_ACL_SUBEVTS.append(f'Tx_{t}_packets')

for i in range(TELEMETRY_NUM_RETRIES):
    TELEMETRY_ACL_SUBEVTS.append(f'Tx_packets_{i}_retries')

TELEMETRY_SCO_SUBEVTS = [
        'Tx_packets', 'Rx_payload_lost',
        'Late_samples_inserted_based_on_CDC', 'Samples_dropped',
        'Mute_samples_sent_at_initial_connection', 'PLC_injection_data'
]

for i in range(TELEMETRY_NUM_SLOTS):
    TELEMETRY_SCO_SUBEVTS.append(f'Rx_No_SYNC_errors_(slot_{i})')
    TELEMETRY_SCO_SUBEVTS.append(f'Rx_HEC_errors_(slot_{i})')
    TELEMETRY_SCO_SUBEVTS.append(f'Rx_CRC_errors_(slot_{i})')
    TELEMETRY_SCO_SUBEVTS.append(f'Rx_NAK_errors_(slot_{i})')
    TELEMETRY_SCO_SUBEVTS.append(f'Failed_Tx_due_to_Wifi_coex_(slot_{i})')
    TELEMETRY_SCO_SUBEVTS.append(f'Failed_Rx_due_to_Wifi_coex_(slot_{i})')

START_TIME_SUBEVT = 'start_time'
END_TIME_SUBEVT = 'end_time'
QUALITY_PREFIX_STRING = '= bluetoothd: quality:'

# Define event handler ids and last ids
AOSP_HANDLER_SUBEVT = 'conn_handle'
AOSP_LAST_SUBEVT = 'buffer_underflow_bytes'

TELEMETRY_ACL_HANDLER_SUBEVT = 'ACL_connection_handle'
TELEMETRY_ACL_LAST_SUBEVT = 'ACL_avg_packet_latency'

TELEMETRY_SCO_HANDLER_SUBEVT = 'SCO_connection_handle'
TELEMETRY_SCO_LAST_SUBEVT = 'PLC_injection_data'

HANDLER_SUBEVT = (AOSP_HANDLER_SUBEVT, TELEMETRY_ACL_HANDLER_SUBEVT,
                    TELEMETRY_SCO_HANDLER_SUBEVT)
END_SUBEVT = (AOSP_LAST_SUBEVT, TELEMETRY_ACL_LAST_SUBEVT,
                TELEMETRY_SCO_LAST_SUBEVT)
CHECK_SUBEVTS = (AOSP_SUBEVTS, TELEMETRY_ACL_SUBEVTS,
                    TELEMETRY_SCO_SUBEVTS)
NOT_EVENT_SUBEVTS = (START_TIME_SUBEVT, END_TIME_SUBEVT)

def _read_line(line):
    """Reading a line of log produced by the quality event packet.

    A line of log looks like:

        = bluetoothd: quality:   buffer_underflow_bytes 150492 75.020732

    line[0:2] is the prefix,
    line[3:-2] is the data subevt, may separate by some spaces,
    line[-2] is the value of the subevt,
    line[-1] is the sending time of the data.

    @returns: subevt, name of the variable in the packet.
                value, value of the variable in the packet.
                time, sending time of the variable in the packet.

    @raises: error.TestError if failed.
    """
    try:
        line = line.split()
        subevt = '_'.join(line[3:-2]).strip(':')
        value = line[-2]
        time_ = line[-1]
    except Exception as e:
        raise error.TestError(
                'Exception in reading Bluetooth Quality Report: %s' % e)
    return subevt, value, time_

def _handler_to_base_10(handler):
    """Convert handler from string to base 10 integer.

    @param handler: a string of quality report handler.

    @returns: integer represents the handler.
    """
    # Either base 10 or base 16.
    if handler.startswith('0x'):
        handler = int(handler, 16)
    else:
        handler = int(handler)

    return handler

def collect_qr_event_from_log(file_path):
    """Collecting all the quality event reports from the btsnoop log.

    This function will grep all the quality event from the log
    and store into a dict.

    @param file_path: where the btsnoop log place at.

    @returns: all_reports, a dict with the format:
                {'handler1':packet_list1, 'handler2':packet_list2, ...}.

    @raises: error.TestError if failed.
    """
    all_reports = collections.defaultdict(list)

    lines = None
    with open(file_path, 'r') as f:
        lines = f.readlines()

    report, handler = {}, None
    for line in lines:
        if not line.startswith(QUALITY_PREFIX_STRING):
            continue

        subevt, value, time_ = _read_line(line)
        if not report:
            report[START_TIME_SUBEVT] = time_
        else:
            report[subevt] = value

            if subevt in HANDLER_SUBEVT:
                handler = _handler_to_base_10(value)

            if subevt in END_SUBEVT:
                if handler is None:
                    raise error.TestError(
                            'Report handler is None type')

                report[END_TIME_SUBEVT] = time_
                all_reports[handler].append(report)
                report, handler = {}, None

    logging.debug("========== Got reports: ========== ")
    for handler, reports in all_reports.items():
        logging.debug('handler: %s \n', handler)
        for report in reports:
            logging.debug('report: %s \n', report)
        logging.debug('\n')

    return all_reports

class BluetoothAdapterQRTests(BluetoothAdapterHIDReportTests,
                              BluetoothAdapterAudioTests):
    """Server side Bluetooth adapter QR test class."""
    BTSNOOP_LOG_DIR = '/tmp'
    BTSNOOP_LOG_FILENAME = 'btsnoop.log'
    BTSNOOP_LOG_FILE = os.path.join(BTSNOOP_LOG_DIR, BTSNOOP_LOG_FILENAME)

    def collect_qr_event_from_log(self):
        """Collect the quality event from btsnoop log"""
        return collect_qr_event_from_log(self.BTSNOOP_LOG_FILE)

    @test_retry_and_log(False)
    def test_check_connection_handle_unique(self, reports, handler_subevt):
        """Checking if the handler subevt in the quality packet list is unique.

        @param reports: a list of quality event reports.
        @param handler_subevt: specify a handler subevt in HANDLER_SUBEVT to
                               check.

        @returns: True if the handler subevt is unique in the packet list,
                  False otherwise.
        """
        reports_len = len(reports)
        if reports_len <= 1:
            return True

        handlers = [reports[i][handler_subevt] for i in range(reports_len)]
        return len(set(handlers)) == 1

    @test_retry_and_log(False)
    def test_check_reports_completeness(self, reports, check_subevt_list):
        """Check if all sub-events in check_subevt_list can be found in reports.

        @param reports: a list of quality event reports.
        @param check_subevt_list: a set of subevts that define the content of
                              the quality event packet.

        @returns: True if all sub-events in check_subevt_list can be found in
                  reports, False otherwise.
        """
        missing_subevt = []
        for report in reports:
            # Check the completeness of the packet.
            for check_subevt in check_subevt_list:
                if check_subevt not in report:
                    missing_subevt.append(check_subevt)

            # Check the length of the packet.
            if (len(check_subevt_list) + len(NOT_EVENT_SUBEVTS)) > len(report):
                logging.error('Error in test_check_reports_completeness(): '
                              'wrong packet size')
                return False

        if missing_subevt:
            logging.info(
                    'Error in test_check_reports_completeness(): '
                    'missing subevt: %s in all reports', missing_subevt)
            return False
        return True

    @test_retry_and_log(False)
    def test_check_period(self, reports, report_type,
                          tolerable_deviation=0.05):
        """Checking if the sending time between adjecent packet is tolerable.

        @param reports: a list of quality event reports.
        @param tolerable_deviation : the percentage of the tolerable deviation
                                     to the QR_EVENT_PERIOD.

        @returns: True if all the time differences between reports are
                  less than the tolerance.
        """
        if len(reports) <= 1:
            return True

        tolerance = tolerable_deviation * QR_EVENT_PERIOD

        # According to the spec of AOSP, there are 4 kind of sub-events and we
        # only care about the sub-event whose quality_report_id is 1.
        if report_type == AOSP_BQR:
            reports = [
                    report for report in reports
                    if report['quality_report_id'] == '1'
            ]

        for i in range(1, len(reports)):
            time_diff = (float(reports[i][START_TIME_SUBEVT]) -
                         float(reports[i - 1][END_TIME_SUBEVT]))

            if time_diff < 0:
                logging.error('Error in test_check_period(): time_diff < 0')
                return False
            if abs(time_diff - QR_EVENT_PERIOD) >= tolerance:
                logging.error('Error in test_check_period: tolerance exceed')
                return False
        return True

    @test_retry_and_log(False)
    def test_send_log(self):
        """Sending the btsnoop log from the DUT back to the autoserv.

        This test can be used only when the self.dut_btmon_log_path
        was set and this variable is set in the quick_test_init() by default.

        @returns: True if success, False otherwise.
        """
        btsnoop_path = self.BTSNOOP_LOG_FILE
        try:
            cmd = f'btmon -C 100 -r {self.dut_btmon_log_path} > {btsnoop_path}'
            res = self.host.run(cmd).stdout
            logging.debug('run command: %s, result: %s', cmd, res)

            self.host.get_file(btsnoop_path, btsnoop_path, delete_dest=True)
        except Exception as e:
            logging.error('Exception in test_send_log: %s', e)
            return False
        return True

    @test_retry_and_log(False)
    def test_not_receive_qr_event_log(self):
        """Checking if not reveice the qr event log"""
        all_reports = self.collect_qr_event_from_log()
        logging.debug("all_reports: %s", all_reports)
        return len(all_reports) == 0

    # ---------------------------------------------------------------
    # Definitions of all bluetooth audio test sequences
    # ---------------------------------------------------------------

    def check_qr_event_log(self, num_devices):
        """Checking if the all the packet list pass the criteria.

        This function check four things:
                - the number of event handlers is greater than the num_devices
                - test_check_connection_handle_unique
                - test_check_reports_completeness
                - test_check_period

        @param num_devices: number of Bluetooth devices expected.
        """
        all_reports = self.collect_qr_event_from_log()

        if len(all_reports) < num_devices:
            raise error.TestFail(
                    'Error in test_check_qr_event_log: wrong '
                    'handler number: %s, expected: %s' % (len(all_reports),
                    num_devices))

        for reports in all_reports.values():
            report_type = None
            for type_, handler_subevt in enumerate(HANDLER_SUBEVT):
                if handler_subevt in reports[0]:
                    report_type = type_
                    break
            if report_type is None:
                raise error.TestError('report_type is None')

            self.test_check_connection_handle_unique(
                    reports, HANDLER_SUBEVT[report_type])
            self.test_check_reports_completeness(
                    reports, CHECK_SUBEVTS[report_type])
            self.test_check_period(reports, report_type)

    def qr_a2dp(self, device, test_profile):
        """Checking if quality event works fine with A2DP streaming.

        @param device: the bluetooth peer device.
        @param test_profile: the test profile to used.
        """
        self.test_a2dp_sinewaves(device, test_profile, duration=None)

    def qr_hfp_dut_as_src(self, device, test_profile):
        """Checking if quality event works fine with HFP streaming.

        @param device: the bluetooth peer device.
        @param test_profile: the test profile to used.
        """
        self.hfp_dut_as_source(device, test_profile)

    def qr_disabled_a2dp(self, device, test_profile):
        """Checking if disable logging quality event success.

        @param device: the bluetooth peer device.
        @param test_profile: the test profile to used.
        """
        self.enable_disable_debug_log(enable=True)
        self.enable_disable_quality_debug_log(enable=True)
        time.sleep(3)
        self.enable_disable_quality_debug_log(enable=False)
        self.enable_disable_debug_log(enable=False)
        time.sleep(3)

        self.dut_btmon_log_path = self.start_new_btmon()
        self.test_a2dp_sinewaves(device, test_profile, duration=None)
        self.test_send_log()
        self.test_not_receive_qr_event_log()

    def qr_a2dp_cl_keyboard(self, audio_device, keyboard_device, test_profile):
        """Checking if quality event works fine with multiple devices.

        @param audio_device: the bluetooth audio device.
        @param keyboard_device: the bluetooth keyboard device.
        @param test_profile: the audio test profile to used.
        """
        p1 = Thread(target=self.test_keyboard_input_from_trace,
                    args=(keyboard_device, "simple_text"))
        p2 = Thread(target=self.test_a2dp_sinewaves,
                    args=(audio_device, test_profile, None))
        p1.start()
        p2.start()
        p1.join()
        p2.join()

    def qr_hfp_dut_as_sink_cl_keyboard(self, audio_device, keyboard_device,
                                       test_profile):
        """Checking if quality event works fine with multiple devices.

        @param audio_device: the bluetooth audio device.
        @param keyboard_device: the bluetooth keyboard device.
        @param test_profile: the audio test profile to used.
        """
        p1 = Thread(target=self.test_keyboard_input_from_trace,
                    args=(keyboard_device, "simple_text"))
        p2 = Thread(target=self.hfp_dut_as_sink,
                    args=(audio_device, test_profile))
        p1.start()
        p2.start()
        p1.join()
        p2.join()

    def qr_power_cycle_a2dp(self, device, test_profile):
        """Checking if the enable debug state persists after power reset.

        @param device: the bluetooth audio device.
        @param test_profile: the audio test profile to used.
        """
        self.test_reset_off_adapter()
        time.sleep(3)
        self.test_reset_on_adapter()

        # Need to connect to the device again.
        self.test_bluetoothd_running()
        self.test_discover_device(device.address)
        self.test_pairing(device.address, device.pin, trusted=True)
        self.test_connection_by_adapter(device.address)

        self.dut_btmon_log_path = self.start_new_btmon()

        self.test_a2dp_sinewaves(device, test_profile, duration=None)
