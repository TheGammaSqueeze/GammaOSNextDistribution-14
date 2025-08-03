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
import statistics
import unittest
from unittest import mock
from unittest.mock import patch

from acts.controllers import power_metrics
from acts.controllers.power_metrics import CURRENT
from acts.controllers.power_metrics import END_TIMESTAMP
from acts.controllers.power_metrics import HOUR
from acts.controllers.power_metrics import Metric
from acts.controllers.power_metrics import MILLIAMP
from acts.controllers.power_metrics import MINUTE
from acts.controllers.power_metrics import PowerMetrics
from acts.controllers.power_metrics import START_TIMESTAMP
from acts.controllers.power_metrics import TIME
from acts.controllers.power_metrics import WATT

FAKE_UNIT_TYPE = 'fake_unit'
FAKE_UNIT = 'F'


class MeasurementTest(unittest.TestCase):
    """Unit tests for the Measurement class."""

    def test_init_with_valid_unit_type(self):
        """Test that a Measurement is properly initialized given a valid unit
        type.
        """
        measurement = Metric(2, CURRENT, MILLIAMP)
        self.assertEqual(measurement.value, 2)
        self.assertEqual(measurement.unit, MILLIAMP)

    def test_init_with_invalid_unit_type(self):
        """Test that __init__ raises an error if given an invalid unit type."""
        with self.assertRaisesRegex(TypeError, 'valid unit type'):
            measurement = Metric(2, FAKE_UNIT_TYPE, FAKE_UNIT)

    def test_unit_conversion(self):
        """Test that to_unit correctly converts value and unit."""
        ratio = 1000
        current_amps = Metric.amps(15)
        current_milliamps = current_amps.to_unit(MILLIAMP)
        self.assertEqual(current_milliamps.value / current_amps.value, ratio)

    def test_unit_conversion_with_wrong_type(self):
        """Test that to_unit raises and error if incompatible unit type is
        specified.
        """
        current_amps = Metric.amps(3.4)
        with self.assertRaisesRegex(TypeError, 'Incompatible units'):
            power_watts = current_amps.to_unit(WATT)

    def test_comparison_operators(self):
        """Test that the comparison operators work as intended."""
        # time_a == time_b < time_c
        time_a = Metric.seconds(120)
        time_b = Metric(2, TIME, MINUTE)
        time_c = Metric(0.1, TIME, HOUR)

        self.assertEqual(time_a, time_b)
        self.assertEqual(time_b, time_a)
        self.assertLessEqual(time_a, time_b)
        self.assertGreaterEqual(time_a, time_b)

        self.assertNotEqual(time_a, time_c)
        self.assertNotEqual(time_c, time_a)
        self.assertLess(time_a, time_c)
        self.assertLessEqual(time_a, time_c)
        self.assertGreater(time_c, time_a)
        self.assertGreaterEqual(time_c, time_a)

    def test_arithmetic_operators(self):
        """Test that the addition and subtraction operators work as intended"""
        time_a = Metric(3, TIME, HOUR)
        time_b = Metric(90, TIME, MINUTE)

        sum_ = time_a + time_b
        self.assertEqual(sum_.value, 4.5)
        self.assertEqual(sum_.unit, HOUR)

        sum_reversed = time_b + time_a
        self.assertEqual(sum_reversed.value, 270)
        self.assertEqual(sum_reversed.unit, MINUTE)

        diff = time_a - time_b
        self.assertEqual(diff.value, 1.5)
        self.assertEqual(diff.unit, HOUR)

        diff_reversed = time_b - time_a
        self.assertEqual(diff_reversed.value, -90)
        self.assertEqual(diff_reversed.unit, MINUTE)


class PowerMetricsTest(unittest.TestCase):
    """Unit tests for the PowerMetrics class."""

    SAMPLES = [0.13, 0.95, 0.32, 4.84, 2.48, 4.11, 4.85, 4.88, 4.22, 2.2]
    RAW_DATA = list(zip(range(10), SAMPLES))
    VOLTAGE = 4.2

    def setUp(self):
        self.power_metrics = PowerMetrics(self.VOLTAGE)

    def test_import_raw_data(self):
        """Test that power metrics can be loaded from file. Simply ensure that
        the number of samples is correct."""

        imported_data = power_metrics.import_raw_data(
            os.path.join(os.path.dirname(__file__),
                         'data/sample_monsoon_data')
        )

        count = 0
        for _, __ in imported_data:
            count = count + 1
        self.assertEqual(count, 10)

    @patch('acts.controllers.power_metrics.PowerMetrics')
    def test_split_by_test_with_timestamps(self, mock_power_metric_type):
        """Test that given test timestamps, a power metric is generated from
        a subset of samples corresponding to the test."""
        timestamps = {'sample_test': {START_TIMESTAMP: 3500,
                                      END_TIMESTAMP:   8500}}

        mock_power_metric = mock.Mock()
        mock_power_metric_type.side_effect = lambda v: mock_power_metric
        power_metrics.generate_test_metrics(self.RAW_DATA,
                                            timestamps=timestamps,
                                            voltage=self.VOLTAGE)

        self.assertEqual(mock_power_metric.update_metrics.call_count, 5)

    def test_incomplete_timestamps_are_ignored(self):
        """Test that given incomplete timestamps, a power metric is generated from
        a subset of samples corresponding to the test."""
        sample_test = 'sample_test'
        test_end = 13500
        test_timestamps = {sample_test: {
            END_TIMESTAMP: test_end}}
        # no error expected
        metrics = (
            power_metrics.generate_test_metrics(self.RAW_DATA,
                                                timestamps=test_timestamps,
                                                voltage=self.VOLTAGE))


    def test_numeric_metrics(self):
        """Test that the numeric metrics have correct values."""
        timestamps = {'sample_test': {START_TIMESTAMP: 0,
                                      END_TIMESTAMP:   10000}}
        metrics = power_metrics.generate_test_metrics(self.RAW_DATA,
                                                      timestamps=timestamps,
                                                      voltage=self.VOLTAGE)
        metrics_as_dic = {m.name: m for m in metrics['sample_test']}
        self.assertAlmostEqual(metrics_as_dic['avg_current'].value,
                               statistics.mean(self.SAMPLES) * 1000)
        self.assertAlmostEqual(metrics_as_dic['max_current'].value,
                               max(self.SAMPLES) * 1000)
        self.assertAlmostEqual(metrics_as_dic['min_current'].value,
                               min(self.SAMPLES) * 1000)
        self.assertAlmostEqual(
            metrics_as_dic['stdev_current'].value,
            statistics.stdev(self.SAMPLES) * 1000)
        self.assertAlmostEqual(
            self.power_metrics.avg_power.value,
            self.power_metrics.avg_current.value * self.VOLTAGE)


if __name__ == '__main__':
    unittest.main()
