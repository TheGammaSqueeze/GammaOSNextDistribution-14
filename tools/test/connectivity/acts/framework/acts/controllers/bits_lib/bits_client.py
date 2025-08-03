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

import csv
from datetime import datetime
import logging
import tempfile

from acts.libs.proc import job
import yaml


class BitsClientError(Exception):
    pass


# An arbitrary large number of seconds.
ONE_YEAR = str(3600 * 24 * 365)
EPOCH = datetime.utcfromtimestamp(0)


def _to_ns(timestamp):
    """Returns the numerical value of a timestamp in nanoseconds since epoch.

    Args:
        timestamp: Either a number or a datetime.

    Returns:
        Rounded timestamp if timestamp is numeric, number of nanoseconds since
        epoch if timestamp is instance of datetime.datetime.
    """
    if isinstance(timestamp, datetime):
        return int((timestamp - EPOCH).total_seconds() * 1e9)
    elif isinstance(timestamp, (float, int)):
        return int(timestamp)
    raise ValueError('%s can not be converted to a numerical representation of '
                     'nanoseconds.' % type(timestamp))


class BitsClient(object):
    """Helper class to issue bits' commands"""

    def __init__(self, binary, service, service_config):
        """Constructs a BitsClient.

        Args:
            binary: The location of the bits.par client binary.
            service: A bits_service.BitsService object. The service is expected
              to be previously setup.
            service_config: The bits_service_config.BitsService object used to
              start the service on service_port.
        """
        self._log = logging.getLogger()
        self._binary = binary
        self._service = service
        self._server_config = service_config

    def _acquire_monsoon(self):
        """Gets hold of a Monsoon so no other processes can use it.
        Only works if there is a monsoon."""
        self._log.debug('acquiring monsoon')
        self.run_cmd('--collector',
                     'Monsoon',
                     '--collector_cmd',
                     'acquire_monsoon', timeout=10)

    def _release_monsoon(self):
        self._log.debug('releasing monsoon')
        self.run_cmd('--collector',
                     'Monsoon',
                     '--collector_cmd',
                     'release_monsoon', timeout=10)

    def run_cmd(self, *args, timeout=60):
        """Executes a generic bits.par command.

        Args:
            args: A bits.par command as a tokenized array. The path to the
              binary and the service port are provided by default, cmd should
              only contain the remaining tokens of the desired command.
            timeout: Number of seconds to wait for the command to finish before
              forcibly killing it.
        """
        result = job.run([self._binary, '--port',
                          self._service.port] + [str(arg) for arg in args],
                         timeout=timeout)
        return result.stdout

    def export(self, collection_name, path):
        """Exports a collection to its bits persistent format.

        Exported files can be shared and opened through the Bits UI.

        Args:
            collection_name: Collection to be exported.
            path: Where the resulting file should be created. Bits requires that
            the resulting file ends in .7z.bits.
        """
        if not path.endswith('.7z.bits'):
            raise BitsClientError('Bits\' collections can only be exported to '
                                  'files ending in .7z.bits, got %s' % path)
        self._log.debug('exporting collection %s to %s',
                        collection_name,
                        path)
        self.run_cmd('--name',
                     collection_name,
                     '--ignore_gaps',
                     '--export',
                     '--export_path',
                     path,
                     timeout=600)

    def export_as_csv(self, channels, collection_name, output_file):
        """Export bits data as CSV.

        Writes the selected channel data to the given output_file. Note that
        the first line of the file contains headers.

        Args:
          channels: A list of string pattern matches for the channel to be
            retrieved. For example, ":mW" will export all power channels,
            ":mV" will export all voltage channels, "C1_01__" will export
            power/voltage/current for the first fail of connector 1.
          collection_name: A string for a collection that is sampling.
          output_file: A string file path where the CSV will be written.
        """
        channels_arg = ','.join(channels)
        cmd = ['--csvfile',
               output_file,
               '--name',
               collection_name,
               '--ignore_gaps',
               '--csv_rawtimestamps',
               '--channels',
               channels_arg]
        if self._server_config.has_virtual_metrics_file:
            cmd = cmd + ['--vm_file', 'default']
        self._log.debug(
            'exporting csv for collection %s to %s, with channels %s',
            collection_name, output_file, channels_arg)
        self.run_cmd(*cmd, timeout=600)

    def add_markers(self, collection_name, markers):
        """Appends markers to a collection.

        These markers are displayed in the Bits UI and are useful to label
        important test events.

        Markers can only be added to collections that have not been
        closed / stopped. Markers need to be added in chronological order,
        this function ensures that at least the markers added in each
        call are sorted in chronological order, but if this function
        is called multiple times, then is up to the user to ensure that
        the subsequent batches of markers are for timestamps higher (newer)
        than all the markers passed in previous calls to this function.

        Args:
            collection_name: The name of the collection to add markers to.
            markers: A list of tuples of the shape:

             [(<nano_seconds_since_epoch or datetime>, <marker text>),
              (<nano_seconds_since_epoch or datetime>, <marker text>),
              (<nano_seconds_since_epoch or datetime>, <marker text>),
              ...
            ]
        """
        # sorts markers in chronological order before adding them. This is
        # required by go/pixel-bits
        for ts, marker in sorted(markers, key=lambda x: _to_ns(x[0])):
            self._log.debug('Adding marker at %s: %s', str(ts), marker)
            self.run_cmd('--name',
                         collection_name,
                         '--log_ts',
                         str(_to_ns(ts)),
                         '--log',
                         marker,
                         timeout=10)

    def get_metrics(self, collection_name, start=None, end=None):
        """Extracts metrics for a period of time.

        Args:
            collection_name: The name of the collection to get metrics from
            start: Numerical nanoseconds since epoch until the start of the
            period of interest or datetime. If not provided, start will be the
            beginning of the collection.
            end: Numerical nanoseconds since epoch until the end of the
            period of interest or datetime. If not provided, end will be the
            end of the collection.
        """
        with tempfile.NamedTemporaryFile(prefix='bits_metrics') as tf:
            cmd = ['--name',
                   collection_name,
                   '--ignore_gaps',
                   '--aggregates_yaml_path',
                   tf.name]

            if start is not None:
                cmd = cmd + ['--abs_start_time', str(_to_ns(start))]
            if end is not None:
                cmd = cmd + ['--abs_stop_time', str(_to_ns(end))]
            if self._server_config.has_virtual_metrics_file:
                cmd = cmd + ['--vm_file', 'default']

            self.run_cmd(*cmd)
            with open(tf.name) as mf:
                self._log.debug(
                    'bits aggregates for collection %s [%s-%s]: %s' % (
                        collection_name, start, end,
                        mf.read()))

            with open(tf.name) as mf:
                return yaml.safe_load(mf)

    def disconnect_usb(self):
        """Disconnects the monsoon's usb. Only works if there is a monsoon"""
        self._log.debug('disconnecting monsoon\'s usb')
        self.run_cmd('--collector',
                     'Monsoon',
                     '--collector_cmd',
                     'usb_disconnect', timeout=10)

    def start_collection(self, collection_name, default_sampling_rate=1000):
        """Indicates Bits to start a collection.

        Args:
            collection_name: Name to give to the collection to be started.
            Collection names must be unique at Bits' service level. If multiple
            collections must be taken within the context of the same Bits'
            service, ensure that each collection is given a different one.
            default_sampling_rate: Samples per second to be collected
        """

        cmd = ['--name',
               collection_name,
               '--non_blocking',
               '--time',
               ONE_YEAR,
               '--default_sampling_rate',
               str(default_sampling_rate)]

        if self._server_config.has_kibbles:
            cmd = cmd + ['--disk_space_saver']

        self._log.debug('starting collection %s', collection_name)
        self.run_cmd(*cmd, timeout=10)

    def connect_usb(self):
        """Connects the monsoon's usb. Only works if there is a monsoon."""
        cmd = ['--collector',
               'Monsoon',
               '--collector_cmd',
               'usb_connect']
        self._log.debug('connecting monsoon\'s usb')
        self.run_cmd(*cmd, timeout=10)

    def stop_collection(self, collection_name):
        """Stops the active collection."""
        self._log.debug('stopping collection %s', collection_name)
        self.run_cmd('--name',
                     collection_name,
                     '--stop')
        self._log.debug('stopped collection %s', collection_name)

    def list_devices(self):
        """Lists devices managed by the bits_server this client is connected
        to.

        Returns:
            bits' output when called with --list devices.
        """
        self._log.debug('listing devices')
        result = self.run_cmd('--list', 'devices', timeout=20)
        return result

    def list_channels(self, collection_name):
        """Finds all the available channels in a given collection.

        Args:
            collection_name: The name of the collection to get channels from.
        """
        metrics = self.get_metrics(collection_name)
        return [channel['name'] for channel in metrics['data']]

    def export_as_monsoon_format(self, dest_path, collection_name,
                                 channel_pattern):
        """Exports data from a collection in monsoon style.

        This function exists because there are tools that have been built on
        top of the monsoon format. To be able to leverage such tools we need
        to make the data compliant with the format.

        The monsoon format is:

        <time_since_epoch_in_secs> <amps>

        Args:
            dest_path: Path where the resulting file will be generated.
            collection_name: The name of the Bits' collection to export data
            from.
            channel_pattern: A regex that matches the Bits' channel to be used
            as source of data. If there are multiple matching channels, only the
            first one will be used. The channel is always assumed to be
            expressed en milli-amps, the resulting format requires amps, so the
            values coming from the first matching channel will always be
            multiplied by 1000.
        """
        with tempfile.NamedTemporaryFile(prefix='bits_csv_') as tmon:
            self.export_as_csv([channel_pattern], collection_name, tmon.name)

            self._log.debug(
                'massaging bits csv to monsoon format for collection'
                ' %s', collection_name)
            with open(tmon.name) as csv_file:
                reader = csv.reader(csv_file)
                headers = next(reader)
                self._log.debug('csv headers %s', headers)
                with open(dest_path, 'w') as dest:
                    for row in reader:
                        ts = float(row[0]) / 1e9
                        amps = float(row[1]) / 1e3
                        dest.write('%.7f %.12f\n' % (ts, amps))
