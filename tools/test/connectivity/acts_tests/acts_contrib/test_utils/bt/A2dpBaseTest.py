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
"""Stream music through connected device from phone test implementation."""
import acts
import os
import pandas as pd
import shutil
import time

import acts_contrib.test_utils.coex.audio_test_utils as atu
import acts_contrib.test_utils.bt.bt_test_utils as btutils
from acts import asserts
from acts_contrib.test_utils.bt import bt_constants
from acts_contrib.test_utils.bt import BtEnum
from acts_contrib.test_utils.abstract_devices.bluetooth_handsfree_abstract_device import BluetoothHandsfreeAbstractDeviceFactory as bt_factory
from acts_contrib.test_utils.bt.BluetoothBaseTest import BluetoothBaseTest
from acts_contrib.test_utils.bt.ble_performance_test_utils import plot_graph
from acts_contrib.test_utils.power.PowerBTBaseTest import ramp_attenuation
from acts_contrib.test_utils.bt.loggers import bluetooth_metric_logger as log
from acts.signals import TestPass, TestError

PHONE_MUSIC_FILE_DIRECTORY = '/sdcard/Music'
INIT_ATTEN = 0
WAIT_TIME = 1


class A2dpBaseTest(BluetoothBaseTest):
    """Stream audio file over desired Bluetooth codec configurations.

    Audio file should be a sine wave. Other audio files will not work for the
    test analysis metrics.

    Device under test is Android phone, connected to headset with a controller
    that can generate a BluetoothHandsfreeAbstractDevice from test_utils.
    abstract_devices.bluetooth_handsfree_abstract_device.
    BuetoothHandsfreeAbstractDeviceFactory.
    """
    def setup_class(self):

        super().setup_class()
        self.bt_logger = log.BluetoothMetricLogger.for_test_case()
        self.dut = self.android_devices[0]
        req_params = ['audio_params', 'music_files', 'system_path_loss']
        opt_params = ['bugreport']
        #'audio_params' is a dict, contains the audio device type, audio streaming
        #settings such as volumn, duration, audio recording parameters such as
        #channel, sampling rate/width, and thdn parameters for audio processing
        self.unpack_userparams(req_params)
        self.unpack_userparams(opt_params, bugreport=None)
        # Find music file and push it to the dut
        music_src = self.music_files[0]
        music_dest = PHONE_MUSIC_FILE_DIRECTORY
        success = self.dut.push_system_file(music_src, music_dest)
        if success:
            self.music_file = os.path.join(PHONE_MUSIC_FILE_DIRECTORY,
                                           os.path.basename(music_src))
        # Initialize media_control class
        self.media = btutils.MediaControlOverSl4a(self.dut, self.music_file)
        # Set attenuator to minimum attenuation
        if hasattr(self, 'attenuators'):
            self.attenuator = self.attenuators[0]
            self.attenuator.set_atten(INIT_ATTEN)
        # Create the BTOE(Bluetooth-Other-End) device object
        bt_devices = self.user_params.get('bt_devices', [])
        if bt_devices:
            attr, idx = bt_devices.split(':')
            self.bt_device_controller = getattr(self, attr)[int(idx)]
            self.bt_device = bt_factory().generate(self.bt_device_controller)
        else:
            self.log.error('No BT devices config is provided!')

    def teardown_class(self):

        super().teardown_class()
        if hasattr(self, 'media'):
            self.media.stop()
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(INIT_ATTEN)
        self.dut.droid.bluetoothFactoryReset()
        self.bt_device.reset()
        self.bt_device.power_off()
        btutils.disable_bluetooth(self.dut.droid)

    def setup_test(self):

        super().setup_test()
        # Initialize audio capture devices
        self.audio_device = atu.get_audio_capture_device(
            self.bt_device_controller, self.audio_params)
        # Reset BT to factory defaults
        self.dut.droid.bluetoothFactoryReset()
        self.bt_device.reset()
        self.bt_device.power_on()
        btutils.enable_bluetooth(self.dut.droid, self.dut.ed)
        btutils.connect_phone_to_headset(self.dut, self.bt_device, 60)
        vol = self.dut.droid.getMaxMediaVolume() * self.audio_params['volume']
        self.dut.droid.setMediaVolume(0)
        time.sleep(1)
        self.dut.droid.setMediaVolume(int(vol))

    def teardown_test(self):

        super().teardown_test()
        self.dut.droid.bluetoothFactoryReset()
        self.media.stop()
        # Set Attenuator to the initial attenuation
        if hasattr(self, 'attenuator'):
            self.attenuator.set_atten(INIT_ATTEN)
        self.bt_device.reset()
        self.bt_device.power_off()
        btutils.disable_bluetooth(self.dut.droid)

    def on_pass(self, test_name, begin_time):

        if hasattr(self, 'bugreport') and self.bugreport == 1:
            self._take_bug_report(test_name, begin_time)

    def play_and_record_audio(self, duration):
        """Play and record audio for a set duration.

        Args:
            duration: duration in seconds for music playing
        Returns:
            audio_captured: captured audio file path
        """

        self.log.info('Play and record audio for {} second'.format(duration))
        self.media.play()
        proc = self.audio_device.start()
        time.sleep(duration + WAIT_TIME)
        proc.kill()
        time.sleep(WAIT_TIME)
        proc.kill()
        audio_captured = self.audio_device.stop()
        self.media.stop()
        self.log.info('Audio play and record stopped')
        asserts.assert_true(audio_captured, 'Audio not recorded')
        return audio_captured

    def _get_bt_link_metrics(self, tag=''):
        """Get bt link metrics such as rssi and tx pwls.

        Returns:
            master_metrics_list: list of metrics of central device
            slave_metrics_list: list of metric of peripheral device
        """

        self.raw_bt_metrics_path = os.path.join(self.log_path,
                                                'BT_Raw_Metrics')
        self.media.play()
        # Get master rssi and power level
        process_data_dict = btutils.get_bt_metric(
            self.dut, tag=tag, log_path=self.raw_bt_metrics_path)
        rssi_master = process_data_dict.get('rssi')
        pwl_master = process_data_dict.get('pwlv')
        rssi_c0_master = process_data_dict.get('rssi_c0')
        rssi_c1_master = process_data_dict.get('rssi_c1')
        txpw_c0_master = process_data_dict.get('txpw_c0')
        txpw_c1_master = process_data_dict.get('txpw_c1')
        bftx_master = process_data_dict.get('bftx')
        divtx_master = process_data_dict.get('divtx')

        if isinstance(self.bt_device_controller,
                      acts.controllers.android_device.AndroidDevice):
            rssi_slave = btutils.get_bt_rssi(self.bt_device_controller,
                                             tag=tag,
                                             log_path=self.raw_bt_metrics_path)
        else:
            rssi_slave = None
        self.media.stop()

        master_metrics_list = [
            rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
            txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
        ]
        slave_metrics_list = [rssi_slave]

        return master_metrics_list, slave_metrics_list

    def run_thdn_analysis(self, audio_captured, tag):
        """Calculate Total Harmonic Distortion plus Noise for latest recording.

        Store result in self.metrics.

        Args:
            audio_captured: the captured audio file
        Returns:
            thdn: thdn value in a list
        """
        # Calculate Total Harmonic Distortion + Noise
        audio_result = atu.AudioCaptureResult(audio_captured,
                                              self.audio_params)
        thdn = audio_result.THDN(**self.audio_params['thdn_params'])
        file_name = tag + os.path.basename(audio_result.path)
        file_new = os.path.join(os.path.dirname(audio_result.path), file_name)
        shutil.copyfile(audio_result.path, file_new)
        for ch_no, t in enumerate(thdn):
            self.log.info('THD+N for channel %s: %.4f%%' % (ch_no, t * 100))
        return thdn

    def run_anomaly_detection(self, audio_captured):
        """Detect anomalies in latest recording.

        Store result in self.metrics.

        Args:
            audio_captured: the captured audio file
        Returns:
            anom: anom detected in the captured file
        """
        # Detect Anomalies
        audio_result = atu.AudioCaptureResult(audio_captured)
        anom = audio_result.detect_anomalies(
            **self.audio_params['anomaly_params'])
        num_anom = 0
        for ch_no, anomalies in enumerate(anom):
            if anomalies:
                for anomaly in anomalies:
                    num_anom += 1
                    start, end = anomaly
                    self.log.warning(
                        'Anomaly on channel {} at {}:{}. Duration '
                        '{} sec'.format(ch_no, start // 60, start % 60,
                                        end - start))
        else:
            self.log.info('%i anomalies detected.' % num_anom)
        return anom

    def generate_proto(self, data_points, codec_type, sample_rate,
                       bits_per_sample, channel_mode):
        """Generate a results protobuf.

        Args:
            data_points: list of dicts representing info to go into
              AudioTestDataPoint protobuffer message.
            codec_type: The codec type config to store in the proto.
            sample_rate: The sample rate config to store in the proto.
            bits_per_sample: The bits per sample config to store in the proto.
            channel_mode: The channel mode config to store in the proto.
        Returns:
             dict: Dictionary with key 'proto' mapping to serialized protobuf,
               'proto_ascii' mapping to human readable protobuf info, and 'test'
               mapping to the test class name that generated the results.
        """

        # Populate protobuf
        test_case_proto = self.bt_logger.proto_module.BluetoothAudioTestResult(
        )

        for data_point in data_points:
            audio_data_proto = test_case_proto.data_points.add()
            log.recursive_assign(audio_data_proto, data_point)

        codec_proto = test_case_proto.a2dp_codec_config
        codec_proto.codec_type = bt_constants.codec_types[codec_type]
        codec_proto.sample_rate = int(sample_rate)
        codec_proto.bits_per_sample = int(bits_per_sample)
        codec_proto.channel_mode = bt_constants.channel_modes[channel_mode]

        self.bt_logger.add_config_data_to_proto(test_case_proto, self.dut,
                                                self.bt_device)

        self.bt_logger.add_proto_to_results(test_case_proto,
                                            self.__class__.__name__)

        proto_dict = self.bt_logger.get_proto_dict(self.__class__.__name__,
                                                   test_case_proto)
        del proto_dict["proto_ascii"]
        return proto_dict

    def set_test_atten(self, atten):
        """Set the attenuation(s) for current test condition.

        """
        if hasattr(self, 'dual_chain') and self.dual_chain == 1:
            ramp_attenuation(self.atten_c0,
                             atten,
                             attenuation_step_max=2,
                             time_wait_in_between=1)
            self.log.info('Set Chain 0 attenuation to %d dB', atten)
            ramp_attenuation(self.atten_c1,
                             atten + self.gain_mismatch,
                             attenuation_step_max=2,
                             time_wait_in_between=1)
            self.log.info('Set Chain 1 attenuation to %d dB',
                          atten + self.gain_mismatch)
        else:
            ramp_attenuation(self.attenuator, atten)
            self.log.info('Set attenuation to %d dB', atten)

    def run_a2dp_to_max_range(self, codec_config):
        attenuation_range = range(self.attenuation_vector['start'],
                                  self.attenuation_vector['stop'] + 1,
                                  self.attenuation_vector['step'])

        data_points = []
        self.file_output = os.path.join(
            self.log_path, '{}.csv'.format(self.current_test_name))

        # Set Codec if needed
        current_codec = self.dut.droid.bluetoothA2dpGetCurrentCodecConfig()
        current_codec_type = BtEnum.BluetoothA2dpCodecType(
            current_codec['codecType']).name
        if current_codec_type != codec_config['codec_type']:
            codec_set = btutils.set_bluetooth_codec(self.dut, **codec_config)
            asserts.assert_true(codec_set, 'Codec configuration failed.')
        else:
            self.log.info('Current codec is {}, no need to change'.format(
                current_codec_type))

        #loop RSSI with the same codec setting
        for atten in attenuation_range:
            self.media.play()
            self.set_test_atten(atten)

            tag = 'codec_{}_attenuation_{}dB_'.format(
                codec_config['codec_type'], atten)
            recorded_file = self.play_and_record_audio(
                self.audio_params['duration'])
            thdns = self.run_thdn_analysis(recorded_file, tag)

            # Collect Metrics for dashboard
            [
                rssi_master, pwl_master, rssi_c0_master, rssi_c1_master,
                txpw_c0_master, txpw_c1_master, bftx_master, divtx_master
            ], [rssi_slave] = self._get_bt_link_metrics(tag)

            data_point = {
                'attenuation_db':
                int(self.attenuator.get_atten()),
                'pathloss':
                atten + self.system_path_loss,
                'rssi_primary':
                rssi_master.get(self.dut.serial, -127),
                'tx_power_level_master':
                pwl_master.get(self.dut.serial, -127),
                'rssi_secondary':
                rssi_slave.get(self.bt_device_controller.serial, -127),
                'rssi_c0_dut':
                rssi_c0_master.get(self.dut.serial, -127),
                'rssi_c1_dut':
                rssi_c1_master.get(self.dut.serial, -127),
                'txpw_c0_dut':
                txpw_c0_master.get(self.dut.serial, -127),
                'txpw_c1_dut':
                txpw_c1_master.get(self.dut.serial, -127),
                'bftx_state':
                bftx_master.get(self.dut.serial, -127),
                'divtx_state':
                divtx_master.get(self.dut.serial, -127),
                'total_harmonic_distortion_plus_noise_percent':
                thdns[0] * 100
            }
            self.log.info(data_point)
            # bokeh data for generating BokehFigure
            bokeh_data = {
                'x_label': 'Pathloss (dBm)',
                'primary_y_label': 'RSSI (dBm)',
                'log_path': self.log_path,
                'current_test_name': self.current_test_name
            }
            #plot_data for adding line to existing BokehFigure
            plot_data = {
                'line_one': {
                    'x_label': 'Pathloss (dBm)',
                    'primary_y_label': 'RSSI (dBm)',
                    'x_column': 'pathloss',
                    'y_column': 'rssi_primary',
                    'legend': 'DUT RSSI (dBm)',
                    'marker': 'circle_x',
                    'y_axis': 'default'
                },
                'line_two': {
                    'x_column': 'pathloss',
                    'y_column': 'rssi_secondary',
                    'legend': 'Remote device RSSI (dBm)',
                    'marker': 'hex',
                    'y_axis': 'default'
                },
                'line_three': {
                    'x_column': 'pathloss',
                    'y_column': 'tx_power_level_master',
                    'legend': 'DUT TX Power (dBm)',
                    'marker': 'hex',
                    'y_axis': 'secondary'
                }
            }

            # Check thdn for glitches, stop if max range reached
            if thdns[0] == 0:
                proto_dict = self.generate_proto(data_points, **codec_config)
                A2dpRange_df = pd.DataFrame(data_points)
                A2dpRange_df.to_csv(self.file_output, index=False)
                plot_graph(A2dpRange_df,
                           plot_data,
                           bokeh_data,
                           secondary_y_label='DUT TX Power')
                raise TestError(
                    'Music play/recording is not working properly or Connection has lost'
                )

            data_points.append(data_point)
            A2dpRange_df = pd.DataFrame(data_points)

            for thdn in thdns:
                if thdn >= self.audio_params['thdn_threshold']:
                    self.log.info(
                        'Max range at attenuation {} dB'.format(atten))
                    self.log.info('DUT rssi {} dBm, DUT tx power level {}, '
                                  'Remote rssi {} dBm'.format(
                                      rssi_master, pwl_master, rssi_slave))
                    proto_dict = self.generate_proto(data_points,
                                                     **codec_config)
                    A2dpRange_df.to_csv(self.file_output, index=False)
                    plot_graph(A2dpRange_df,
                               plot_data,
                               bokeh_data,
                               secondary_y_label='DUT TX Power')
                    return True
                    raise TestPass('Max range reached and move to next codec',
                                   extras=proto_dict)
        # Save Data points to csv
        A2dpRange_df.to_csv(self.file_output, index=False)
        # Plot graph
        plot_graph(A2dpRange_df,
                   plot_data,
                   bokeh_data,
                   secondary_y_label='DUT TX Power')
        proto_dict = self.generate_proto(data_points, **codec_config)
        return True
        raise TestPass('Could not reach max range, need extra attenuation.',
                       extras=proto_dict)
