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

import acts_contrib.test_utils.power.cellular.cellular_power_base_test as PWCEL


class PowerTelIdleTest(PWCEL.PowerCellularLabBaseTest):
    """Cellular idle power test.

    Inherits from PowerCellularLabBaseTest. Tests power consumption during
    cellular idle scenarios to verify the ability to set power consumption
    to a minimum during connectivity power tests.
    """

    TIME_SLOT_WINDOW_SECONDS = 1.280
    FILTER_CURRENT_THRESHOLD = 20

    def power_tel_idle_test(self, filter_results=False):
        """ Measures power when the device is on LTE RRC idle state.

        Args:
            filter_results: when True the reported result is filtered to only
                samples where average power was below a certain threshold.
        """
        # disable data for idle test
        self.dut.adb.shell('service call phone 39')
        self.dut.adb.shell('svc data disable')

        idle_wait_time = self.simulation.rrc_sc_timer + 30

        # Wait for RRC status change to trigger
        self.cellular_simulator.wait_until_idle_state(idle_wait_time)

        # Measure power
        samples = self.collect_power_data()

        # If necessary, replace the test result with the filtered metric
        if filter_results:
            self.avg_current = self.filter_for_idle_state(samples)
            self.power_result.metric_value = self.avg_current * self.mon_voltage

        # Check if power measurement is below the required value
        self.pass_fail_check(self.avg_current)

    def filter_for_idle_state(self, samples):
        """ Process results and only take an average of time slots that are
        below a certain threshold.

        Args:
            samples: a list of tuples in which the first element is a timestamp
            and the second element is the sampled current in micro amps at that
            time.
        """
        # Calculate the time slot duration in number of samples
        slot_length = round(self.mon_freq * self.TIME_SLOT_WINDOW_SECONDS)

        # Transform the currents from samples into milli_amps.
        milli_amps = [sample[1] * 1000 for sample in samples]

        filtered_slot_averages = []
        for slot in range(int(len(milli_amps) / slot_length)):
            # Calculate the average in this time slot
            slot_start = slot_length * slot
            slot_end = slot_start + slot_length
            slot_average = sum(milli_amps[slot_start:slot_end]) / slot_length
            # Only use time slots in which the average was below the threshold
            if slot_average <= self.FILTER_CURRENT_THRESHOLD:
                filtered_slot_averages.append(slot_average)

        if filtered_slot_averages:
            # Calculate the average from all the filtered slots
            result = sum(filtered_slot_averages) / len(filtered_slot_averages)
            self.log.info(
                "The {} s window average current was below {} mA "
                "for {} s. During that time the average current was {} mA.".
                format(
                    self.TIME_SLOT_WINDOW_SECONDS,
                    self.FILTER_CURRENT_THRESHOLD,
                    self.TIME_SLOT_WINDOW_SECONDS *
                    len(filtered_slot_averages), result))
            return result
        else:
            self.log.error("The device was not in idle state for the whole "
                           "duration of the test.")
            return 0
