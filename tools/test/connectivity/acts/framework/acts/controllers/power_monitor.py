#!/usr/bin/env python3
#
#   Copyright 2020 - The Android Open Source Project
#
#   Licensed under the Apache License, Version 2.0 (the "License");
#   you may not use this file except in compliance with the License.
#   You may obtain a copy of the License at
#
#       http://www.apache.org/licenses/LICENSE-2.0
#
#   Unless required by applicable law or agreed to in writing, software
#   distributed under the License is distributed on an "AS IS" BASIS,
#   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
#   See the License for the specific language governing permissions and
#   limitations under the License.

import logging
import tempfile

from acts.controllers import power_metrics
from acts.controllers.monsoon_lib.api.common import MonsoonError


class ResourcesRegistryError(Exception):
    pass


_REGISTRY = {}


def update_registry(registry):
    """Updates the registry with the one passed.

    Overriding a previous value is not allowed.

    Args:
        registry: A dictionary.
    Raises:
        ResourceRegistryError if a property is updated with a different value.
    """
    for k, v in registry.items():
        if k in _REGISTRY:
            if v == _REGISTRY[k]:
                continue
            raise ResourcesRegistryError(
                'Overwriting resources_registry fields is not allowed. %s was '
                'already defined as %s and was attempted to be overwritten '
                'with %s.' % (k, _REGISTRY[k], v))
        _REGISTRY[k] = v


def get_registry():
    return _REGISTRY


def _write_raw_data_in_standard_format(raw_data, path, start_time):
    """Writes the raw data to a file in (seconds since epoch, amps).

    TODO(b/155294049): Deprecate this once Monsoon controller output
        format is updated.

    Args:
        start_time: Measurement start time in seconds since epoch
        raw_data: raw data as list or generator of (timestamp, sample)
        path: path to write output
    """
    with open(path, 'w') as f:
        for timestamp, amps in raw_data:
            f.write('%s %s\n' %
                    (timestamp + start_time, amps))


class BasePowerMonitor(object):

    def setup(self, **kwargs):
        raise NotImplementedError()

    def connect_usb(self, **kwargs):
        raise NotImplementedError()

    def measure(self, **kwargs):
        raise NotImplementedError()

    def release_resources(self, **kwargs):
        raise NotImplementedError()

    def disconnect_usb(self, **kwargs):
        raise NotImplementedError()

    def get_metrics(self, **kwargs):
        raise NotImplementedError()

    def get_waveform(self, **kwargs):
        raise NotImplementedError()

    def teardown(self, **kwargs):
        raise NotImplementedError()


class PowerMonitorMonsoonFacade(BasePowerMonitor):

    def __init__(self, monsoon):
        """Constructs a PowerMonitorFacade.

        Args:
            monsoon: delegate monsoon object, either
                acts.controllers.monsoon_lib.api.hvpm.monsoon.Monsoon or
                acts.controllers.monsoon_lib.api.lvpm_stock.monsoon.Monsoon.
        """
        self.monsoon = monsoon
        self._log = logging.getLogger()

    def setup(self, monsoon_config=None, **__):
        """Set up the Monsoon controller for this testclass/testcase."""

        if monsoon_config is None:
            raise MonsoonError('monsoon_config can not be None')

        self._log.info('Setting up Monsoon %s' % self.monsoon.serial)
        voltage = monsoon_config.get_numeric('voltage', 4.2)
        self.monsoon.set_voltage_safe(voltage)
        if 'max_current' in monsoon_config:
            self.monsoon.set_max_current(
                monsoon_config.get_numeric('max_current'))

    def power_cycle(self, monsoon_config=None, **__):
        """Power cycles the delegated monsoon controller."""

        if monsoon_config is None:
            raise MonsoonError('monsoon_config can not be None')

        self._log.info('Setting up Monsoon %s' % self.monsoon.serial)
        voltage = monsoon_config.get_numeric('voltage', 4.2)
        self._log.info('Setting up Monsoon voltage %s' % voltage)
        self.monsoon.set_voltage_safe(0)
        if 'max_current' in monsoon_config:
            self.monsoon.set_max_current(
                monsoon_config.get_numeric('max_current'))
            self.monsoon.set_max_initial_current(
                monsoon_config.get_numeric('max_current'))
        self.connect_usb()
        self.monsoon.set_voltage_safe(voltage)

    def connect_usb(self, **__):
        self.monsoon.usb('on')

    def measure(self, measurement_args=None, start_time=None,
                monsoon_output_path=None, **__):
        if measurement_args is None:
            raise MonsoonError('measurement_args can not be None')

        with tempfile.NamedTemporaryFile(prefix='monsoon_') as tmon:
            self.monsoon.measure_power(**measurement_args,
                                       output_path=tmon.name)

            if monsoon_output_path and start_time is not None:
                _write_raw_data_in_standard_format(
                    power_metrics.import_raw_data(tmon.name),
                    monsoon_output_path, start_time)

    def release_resources(self, **__):
        # nothing to do
        pass

    def disconnect_usb(self, **__):
        self.monsoon.usb('off')

    def get_waveform(self, file_path=None):
        """Parses a file to obtain all current (in amps) samples.

        Args:
            file_path: Path to a monsoon file.

        Returns:
            A list of tuples in which the first element is a timestamp and the
            second element is the sampled current at that time.
        """
        if file_path is None:
            raise MonsoonError('file_path can not be None')

        return list(power_metrics.import_raw_data(file_path))

    def get_metrics(self, start_time=None, voltage=None, monsoon_file_path=None,
                    timestamps=None, **__):
        """Parses a monsoon_file_path to compute the consumed power and other
        power related metrics.

        Args:
            start_time: Time when the measurement started, this is used to
                correlate timestamps from the device and from the power samples.
            voltage: Voltage used when the measurement started. Used to compute
                power from current.
            monsoon_file_path: Path to a monsoon file.
            timestamps: Named timestamps delimiting the segments of interest.
            **__:

        Returns:
            A list of power_metrics.Metric.
        """
        if start_time is None:
            raise MonsoonError('start_time can not be None')
        if voltage is None:
            raise MonsoonError('voltage can not be None')
        if monsoon_file_path is None:
            raise MonsoonError('monsoon_file_path can not be None')
        if timestamps is None:
            raise MonsoonError('timestamps can not be None')

        return power_metrics.generate_test_metrics(
            power_metrics.import_raw_data(monsoon_file_path),
            timestamps=timestamps, voltage=voltage)

    def teardown(self, **__):
        # nothing to do
        pass
