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
import os

import acts_contrib.test_utils.power.PowerBaseTest as PBT
import acts_contrib.test_utils.cellular.cellular_base_test as CBT
from acts_contrib.test_utils.power import plot_utils
from acts import context


class PowerCellularLabBaseTest(CBT.CellularBaseTest, PBT.PowerBaseTest):
    """ Base class for Cellular power related tests.

    Inherits from both PowerBaseTest and CellularBaseTest so it has methods to
    collect power measurements and run a cellular simulation.
    """
    def __init__(self, controllers):
        """ Class initialization.

        Sets class attributes to None.
        """

        super().__init__(controllers)
        self.power_results = {}

    def setup_test(self):
        """ Turn screen on before starting a test. """

        super().setup_test()
        try:
            # Save a json file for device info
            path = os.path.join(self.log_path, 'device_info.json')

            self.log.info('save the device info to {}'.format(path))
            baseband = self.dut.adb.getprop('gsm.version.baseband')
            self.dut.add_device_info('baseband', baseband)
            with open(path, 'w') as f:
                json.dump(
                    self.dut.device_info,
                    f,
                    indent=2,
                    sort_keys=True)
        except Exception as e:
            self.log.error('error in saving device_info: {}'.format(e))

        # Make the device go to sleep
        self.dut.droid.goToSleepNow()

        return True

    def collect_power_data(self):
        """ Collect power data using base class method and plot result
        histogram. """

        samples = super().collect_power_data()
        plot_title = '{}_{}_{}_histogram'.format(
            self.test_name, self.dut.model, self.dut.build_info['build_id'])
        plot_utils.monsoon_histogram_plot(samples, self.mon_info.data_path,
                                          plot_title)
        return samples

    def teardown_test(self):
        """ Executed after every test case, even if it failed or an exception
        happened.

        Save results to dictionary so they can be displayed after completing
        the test batch.
        """
        super().teardown_test()

        self.power_results[self.test_name] = self.power_result.metric_value

    def teardown_class(self):
        """Clean up the test class after tests finish running.

        Stops the simulation and disconnects from the Anritsu Callbox. Then
        displays the test results.

        """
        super().teardown_class()

        # Log a summary of results
        results_table_log = 'Results for cellular power tests:'

        for test_name, value in self.power_results.items():
            results_table_log += '\n{}\t{}'.format(test_name, value)

        # Save this summary to a csv file in the logs directory
        self.save_summary_to_file()

        self.log.info(results_table_log)

    def save_summary_to_file(self):
        """ Creates CSV format files with a summary of results.

        This CSV files can be easily imported in a spreadsheet to analyze the
        results obtained from the tests.
        """

        # Save a csv file with the power measurements done in all the tests

        path = os.path.join(self.log_path, self.RESULTS_SUMMARY_FILENAME)

        # To avoid the test overwrite each other, open file with 'a' option
        csvfile_exist = os.path.exists(path)

        with open(path, 'a') as csvfile:
            if not csvfile_exist:
                csvfile.write('test,avg_power')
            for test_name, value in self.power_results.items():
                csvfile.write('\n{},{}'.format(test_name, value))

        # Save a csv file with the calibration table for each simulation type

        for sim_type in self.calibration_table:

            path = os.path.join(
                self.log_path, '{}_{}'.format(sim_type,
                                              self.CALIBRATION_TABLE_FILENAME))

            with open(path, 'w') as csvfile:
                csvfile.write('band,dl_pathloss, ul_pathloss')
                for band, pathloss in self.calibration_table[sim_type].items():
                    csvfile.write('\n{},{},{}'.format(
                        band, pathloss.get('dl', 'Error'),
                        pathloss.get('ul', 'Error')))
