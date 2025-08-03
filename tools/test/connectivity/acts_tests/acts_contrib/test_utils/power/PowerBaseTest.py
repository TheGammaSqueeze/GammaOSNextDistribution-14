#!/usr/bin/env python3
#
#   Copyright 2018 - The Android Open Source Project
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
import json
import logging
import math
import os
import re
import time

import acts.controllers.power_monitor as power_monitor_lib
import acts.controllers.monsoon as monsoon_controller
import acts.controllers.iperf_server as ipf
from acts import asserts
from acts import base_test
from acts import utils
from acts.metrics.loggers.blackbox import BlackboxMetricLogger
from acts.controllers.adb_lib.error import AdbError
from acts_contrib.test_utils.power.loggers.power_metric_logger import PowerMetricLogger
from acts_contrib.test_utils.power import plot_utils

RESET_BATTERY_STATS = 'dumpsys batterystats --reset'
IPERF_TIMEOUT = 180
THRESHOLD_TOLERANCE_DEFAULT = 0.2
GET_FROM_PHONE = 'get_from_dut'
GET_FROM_AP = 'get_from_ap'
GET_PROPERTY_HARDWARE_PLATFORM = 'getprop ro.boot.hardware.platform'
POWER_STATS_DUMPSYS_CMD = 'dumpsys android.hardware.power.stats.IPowerStats/default delta'
PHONE_BATTERY_VOLTAGE_DEFAULT = 4.2
MONSOON_MAX_CURRENT = 8.0
DEFAULT_MONSOON_FREQUENCY = 500
ENABLED_MODULATED_DTIM = 'gEnableModulatedDTIM='
MAX_MODULATED_DTIM = 'gMaxLIModulatedDTIM='
TEMP_FILE = '/sdcard/Download/tmp.log'


class ObjNew(object):
    """Create a random obj with unknown attributes and value.

    """
    def __init__(self, **kwargs):
        self.__dict__.update(kwargs)

    def __contains__(self, item):
        """Function to check if one attribute is contained in the object.

        Args:
            item: the item to check
        Return:
            True/False
        """
        return hasattr(self, item)


class PowerBaseTest(base_test.BaseTestClass):
    """Base class for all wireless power related tests.

    """
    def __init__(self, controllers):

        super().__init__(controllers)
        self.power_result = BlackboxMetricLogger.for_test_case(
            metric_name='avg_power')
        self.start_meas_time = 0
        self.rockbottom_script = None
        self.img_name = ''
        self.dut = None
        self.power_logger = PowerMetricLogger.for_test_case()
        self.power_monitor = None
        self.odpm_folder = None

    @property
    def final_test(self):
        return len(
            self.results.requested
        ) > 0 and self.current_test_name == self.results.requested[-1]

    @property
    def display_name_test_suite(self):
        return getattr(self, '_display_name_test_suite',
                       self.__class__.__name__)

    @display_name_test_suite.setter
    def display_name_test_suite(self, name):
        self._display_name_test_suite = name

    @property
    def display_name_test_case(self):
        default_test_name = getattr(self, 'test_name', None)
        return getattr(self, '_display_name_test_case', default_test_name)

    @display_name_test_case.setter
    def display_name_test_case(self, name):
        self._display_name_test_case = name

    def initialize_power_monitor(self):
        """ Initializes the power monitor object.

        Raises an exception if there are no controllers available.
        """
        if hasattr(self, 'bitses'):
            if hasattr(self, 'monsoons'):
                self.log.info('Destroying monsoon controller.')
                monsoon_controller.destroy(self.monsoons)
                time.sleep(2)
            self.power_monitor = self.bitses[0]
            self.power_monitor.setup(registry=self.user_params)
        elif hasattr(self, 'monsoons'):
            self.power_monitor = power_monitor_lib.PowerMonitorMonsoonFacade(
                self.monsoons[0])
            self.monsoons[0].set_max_current(8.0)
            self.monsoons[0].set_voltage(self.mon_voltage)
        else:
            raise RuntimeError('No power monitors available.')

    def setup_class(self):

        super().setup_class()

        self.log = logging.getLogger()
        self.tests = self.get_existing_test_names()

        # Obtain test parameters from user_params
        TEST_PARAMS = self.TAG + '_params'
        self.test_params = self.user_params.get(TEST_PARAMS, {})
        if not self.test_params:
            self.log.warning(TEST_PARAMS + ' was not found in the user '
                             'parameters defined in the config file.')

        # Override user_param values with test parameters
        self.user_params.update(self.test_params)

        # Unpack user_params with default values. All the usages of user_params
        # as self attributes need to be included either as a required parameter
        # or as a parameter with a default value.
        req_params = ['custom_files', 'mon_duration']
        self.unpack_userparams(req_params,
                               mon_freq=DEFAULT_MONSOON_FREQUENCY,
                               mon_offset=0,
                               bug_report=False,
                               extra_wait=None,
                               iperf_duration=None,
                               pass_fail_tolerance=THRESHOLD_TOLERANCE_DEFAULT,
                               mon_voltage=PHONE_BATTERY_VOLTAGE_DEFAULT,
                               ap_dtim_period=None,
                               bits_root_rail_csv_export=False,
                               is_odpm_supported=False)

        # Setup the must have controllers, phone and monsoon
        self.dut = self.android_devices[0]
        self.mon_data_path = os.path.join(self.log_path, 'Monsoon')
        os.makedirs(self.mon_data_path, exist_ok=True)

        # Make odpm path for P21 or later
        platform = self.dut.adb.shell(GET_PROPERTY_HARDWARE_PLATFORM)
        self.log.info('The hardware platform is {}'.format(platform))
        if (
            platform.startswith('gs')
            or platform.startswith('z')
            or self.is_odpm_supported
        ):
            self.odpm_folder = os.path.join(self.log_path, 'odpm')
            os.makedirs(self.odpm_folder, exist_ok=True)
            self.log.info('For P21 or later, create odpm folder {}'.format(
                self.odpm_folder))

        # Initialize the power monitor object that will be used to measure
        self.initialize_power_monitor()

        # Unpack the thresholds file or fail class setup if it can't be found
        for file in self.custom_files:
            if 'pass_fail_threshold_' + self.dut.model in file:
                self.threshold_file = file
                break
        else:
            raise RuntimeError('Required test pass/fail threshold file is '
                               'missing')

        # Unpack the rockbottom script or fail class setup if it can't be found
        for file in self.custom_files:
            if 'rockbottom_' + self.dut.model in file:
                self.rockbottom_script = file
                break
        else:
            raise RuntimeError('Required rockbottom script is missing.')

        # Unpack optional custom files
        for file in self.custom_files:
            if 'attenuator_setting' in file:
                self.attenuation_file = file
            elif 'network_config' in file:
                self.network_file = file

        if hasattr(self, 'attenuators'):
            self.num_atten = self.attenuators[0].instrument.num_atten
            self.atten_level = self.unpack_custom_file(self.attenuation_file)
        self.threshold = self.unpack_custom_file(self.threshold_file)
        self.mon_info = self.create_monsoon_info()

        # Sync device time, timezone and country code
        utils.require_sl4a((self.dut, ))
        utils.sync_device_time(self.dut)

        screen_on_img = self.user_params.get('screen_on_img', [])
        if screen_on_img:
            img_src = screen_on_img[0]
            img_dest = '/sdcard/Pictures/'
            success = self.dut.push_system_file(img_src, img_dest)
            if success:
                self.img_name = os.path.basename(img_src)

    def setup_test(self):
        """Set up test specific parameters or configs.

        """
        super().setup_test()

        # Reset result variables
        self.avg_current = 0
        self.samples = []
        self.power_result.metric_value = 0

        # Set the device into rockbottom state
        self.dut_rockbottom()

        # Wait for extra time if needed for the first test
        if self.extra_wait:
            self.more_wait_first_test()

    def teardown_test(self):
        """Tear down necessary objects after test case is finished.

        """
        self.log.info('Tearing down the test case')
        self.power_monitor.connect_usb()
        self.power_logger.set_avg_power(self.power_result.metric_value)
        self.power_logger.set_avg_current(self.avg_current)
        self.power_logger.set_voltage(self.mon_voltage)
        self.power_logger.set_testbed(self.testbed_name)

        # If a threshold was provided, log it in the power proto
        if self.threshold and self.test_name in self.threshold:
            avg_current_threshold = self.threshold[self.test_name]
            self.power_logger.set_avg_current_threshold(avg_current_threshold)

        build_id = self.dut.build_info.get('build_id', '')
        incr_build_id = self.dut.build_info.get('incremental_build_id', '')
        branch = self.user_params.get('branch', '')
        target = self.dut.device_info.get('flavor', '')

        self.power_logger.set_branch(branch)
        self.power_logger.set_build_id(build_id)
        self.power_logger.set_incremental_build_id(incr_build_id)
        self.power_logger.set_target(target)

        # Log the display name of the test suite and test case
        if self.display_name_test_suite:
            name = self.display_name_test_suite
            self.power_logger.set_test_suite_display_name(name)

        if self.display_name_test_case:
            name = self.display_name_test_case
            self.power_logger.set_test_case_display_name(name)

        # Take Bugreport
        if self.bug_report:
            begin_time = utils.get_current_epoch_time()
            self.dut.take_bug_report(self.test_name, begin_time)

        # Allow the device to cooldown before executing the next test
        cooldown = self.test_params.get('cooldown', None)
        if cooldown and not self.final_test:
            time.sleep(cooldown)

    def teardown_class(self):
        """Clean up the test class after tests finish running

        """
        self.log.info('Tearing down the test class')
        if self.power_monitor:
            self.power_monitor.connect_usb()

    def on_fail(self, test_name, begin_time):
        self.power_logger.set_pass_fail_status('FAIL')

    def on_pass(self, test_name, begin_time):
        self.power_logger.set_pass_fail_status('PASS')

    def dut_save_odpm(self, tag):
        """Dumpsys ODPM data and save it to self.odpm_folder.

        Args:
            tag: the moment of save ODPM data
        """
        odpm_file_name = '{}.{}.dumpsys_odpm_{}.txt'.format(
            self.__class__.__name__,
            self.current_test_name,
            tag)
        odpm_file_path = os.path.join(self.odpm_folder, odpm_file_name)

        try:
            stats = self.dut.adb.shell(POWER_STATS_DUMPSYS_CMD)
            with open(odpm_file_path, 'w') as f:
                f.write(stats)
        except AdbError as e:
            self.log.warning('Odpm data with tag {} did not save due to adb '
                             'error {}'.format(e))

    def dut_rockbottom(self):
        """Set the dut to rockbottom state

        """
        # The rockbottom script might include a device reboot, so it is
        # necessary to stop SL4A during its execution.
        self.dut.stop_services()
        self.log.info('Executing rockbottom script for ' + self.dut.model)
        os.chmod(self.rockbottom_script, 0o777)
        os.system('{} {} {}'.format(self.rockbottom_script, self.dut.serial,
                                    self.img_name))
        # Make sure the DUT is in root mode after coming back
        self.dut.root_adb()
        # Restart SL4A
        self.dut.start_services()

    def unpack_custom_file(self, file, test_specific=True):
        """Unpack the pass_fail_thresholds from a common file.

        Args:
            file: the common file containing pass fail threshold.
            test_specific: if True, returns the JSON element within the file
                that starts with the test class name.
        """
        with open(file, 'r') as f:
            params = json.load(f)
        if test_specific:
            try:
                return params[self.TAG]
            except KeyError:
                pass
        else:
            return params

    def decode_test_configs(self, attrs, indices):
        """Decode the test config/params from test name.

        Remove redundant function calls when tests are similar.
        Args:
            attrs: a list of the attrs of the test config obj
            indices: a list of the location indices of keyword in the test name.
        """
        # Decode test parameters for the current test
        test_params = self.current_test_name.split('_')
        values = [test_params[x] for x in indices]
        config_dict = dict(zip(attrs, values))
        self.test_configs = ObjNew(**config_dict)

    def more_wait_first_test(self):
        # For the first test, increase the offset for longer wait time
        if self.current_test_name == self.tests[0]:
            self.mon_info.offset = self.mon_offset + self.extra_wait
        else:
            self.mon_info.offset = self.mon_offset

    def set_attenuation(self, atten_list):
        """Function to set the attenuator to desired attenuations.

        Args:
            atten_list: list containing the attenuation for each attenuator.
        """
        if len(atten_list) != self.num_atten:
            raise Exception('List given does not have the correct length')
        for i in range(self.num_atten):
            self.attenuators[i].set_atten(atten_list[i])

    def measure_power_and_validate(self):
        """The actual test flow and result processing and validate.

        """
        self.collect_power_data()
        self.pass_fail_check(self.avg_current)

    def collect_power_data(self):
        """Measure power, plot and take log if needed.

        Returns:
            A MonsoonResult object.
        """
        # Collecting current measurement data and plot
        samples = self.power_monitor_data_collect_save()

        current = [sample[1] for sample in samples]
        average_current = sum(current) * 1000 / len(current)

        self.power_result.metric_value = (average_current * self.mon_voltage)
        self.avg_current = average_current

        plot_title = '{}_{}_{}'.format(self.test_name, self.dut.model,
                                       self.dut.build_info['build_id'])
        plot_utils.current_waveform_plot(samples, self.mon_voltage,
                                         self.mon_info.data_path, plot_title)

        return samples

    def pass_fail_check(self, average_current=None):
        """Check the test result and decide if it passed or failed.

        The threshold is provided in the config file. In this class, result is
        current in mA.
        """

        if not self.threshold or self.test_name not in self.threshold:
            self.log.error("No threshold is provided for the test '{}' in "
                           "the configuration file.".format(self.test_name))
            return

        current_threshold = self.threshold[self.test_name]
        if average_current:
            asserts.assert_true(
                abs(average_current - current_threshold) / current_threshold <
                self.pass_fail_tolerance,
                'Measured average current in [{}]: {:.2f}mA, which is '
                'out of the acceptable range {:.2f}±{:.2f}mA'.format(
                    self.test_name, average_current, current_threshold,
                    self.pass_fail_tolerance * current_threshold))
            asserts.explicit_pass(
                'Measurement finished for [{}]: {:.2f}mA, which is '
                'within the acceptable range {:.2f}±{:.2f}'.format(
                    self.test_name, average_current, current_threshold,
                    self.pass_fail_tolerance * current_threshold))
        else:
            asserts.fail(
                'Something happened, measurement is not complete, test failed')

    def create_monsoon_info(self):
        """Creates the config dictionary for monsoon

        Returns:
            mon_info: Dictionary with the monsoon packet config
        """
        mon_info = ObjNew(freq=self.mon_freq,
                          duration=self.mon_duration,
                          offset=self.mon_offset,
                          data_path=self.mon_data_path)
        return mon_info

    def power_monitor_data_collect_save(self):
        """Current measurement and save the log file.

        Collect current data using Monsoon box and return the path of the
        log file. Take bug report if requested.

        Returns:
            A list of tuples in which the first element is a timestamp and the
            second element is the sampled current in Amperes at that time.
        """

        tag = '{}_{}_{}'.format(self.test_name, self.dut.model,
                                self.dut.build_info['build_id'])

        data_path = os.path.join(self.mon_info.data_path, '{}.txt'.format(tag))

        # If the specified Monsoon data file already exists (e.g., multiple
        # measurements in a single test), write the results to a new file with
        # the postfix "_#".
        if os.path.exists(data_path):
            highest_value = 1
            for filename in os.listdir(os.path.dirname(data_path)):
                match = re.match(r'{}_(\d+).txt'.format(tag), filename)
                if match:
                    highest_value = max(highest_value, int(match.group(1)))

            data_path = os.path.join(self.mon_info.data_path,
                                     '%s_%s.txt' % (tag, highest_value + 1))

        # Resets the battery status right before the test starts.
        self.dut.adb.shell(RESET_BATTERY_STATS)
        self.log.info('Starting power measurement. Duration: {}s. Offset: '
                      '{}s. Voltage: {} V.'.format(self.mon_info.duration,
                                                   self.mon_info.offset,
                                                   self.mon_voltage))

        # TODO(b/155426729): Create an accurate host-to-device time difference
        # measurement.
        device_time_cmd = 'echo $EPOCHREALTIME'
        device_time = self.dut.adb.shell(device_time_cmd)
        host_time = time.time()
        self.log.debug('device start time %s, host start time %s', device_time,
                       host_time)
        device_to_host_offset = float(device_time) - host_time

        # Start the power measurement using monsoon.
        self.dut.stop_services()
        time.sleep(1)

        # P21 or later device, save the odpm data before power measurement
        if self.odpm_folder:
            self.dut_save_odpm('before')

        self.power_monitor.disconnect_usb()
        measurement_args = dict(duration=self.mon_info.duration,
                                measure_after_seconds=self.mon_info.offset,
                                hz=self.mon_info.freq)
        self.power_monitor.measure(measurement_args=measurement_args,
                                   measurement_name=self.test_name,
                                   start_time=device_to_host_offset,
                                   monsoon_output_path=data_path)
        self.power_monitor.release_resources()
        self.collect_raw_data_samples()
        self.power_monitor.connect_usb()
        self.dut.wait_for_boot_completion()
        time.sleep(10)

        # For P21 or later device, save the odpm data after power measurement
        if self.odpm_folder:
            self.dut_save_odpm('after')

        self.dut.start_services()

        return self.power_monitor.get_waveform(file_path=data_path)

    def process_iperf_results(self):
        """Get the iperf results and process.

        Returns:
             throughput: the average throughput during tests.
        """
        # Get IPERF results and add this to the plot title
        RESULTS_DESTINATION = os.path.join(
            self.iperf_server.log_path,
            'iperf_client_output_{}.log'.format(self.current_test_name))
        self.dut.pull_files(TEMP_FILE, RESULTS_DESTINATION)
        # Calculate the average throughput
        if self.use_client_output:
            iperf_file = RESULTS_DESTINATION
        else:
            iperf_file = self.iperf_server.log_files[-1]
        try:
            iperf_result = ipf.IPerfResult(iperf_file)

            # Compute the throughput in Mbit/s
            throughput = (math.fsum(
                iperf_result.instantaneous_rates[self.start_meas_time:-1]
            ) / len(iperf_result.instantaneous_rates[self.start_meas_time:-1])
                          ) * 8 * (1.024**2)

            self.log.info('The average throughput is {}'.format(throughput))
        except ValueError:
            self.log.warning('Cannot get iperf result. Setting to 0')
            throughput = 0
        return throughput

    def collect_raw_data_samples(self):
        if hasattr(self, 'bitses') and self.bits_root_rail_csv_export:
            path = os.path.join(os.path.dirname(self.mon_info.data_path),
                                'Kibble')
            self.power_monitor.get_bits_root_rail_csv_export(path, self.test_name)
