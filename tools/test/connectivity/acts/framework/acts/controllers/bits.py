"""Module managing the required definitions for using the bits power monitor"""

import csv
import json
import logging
import os
import time
import uuid

from acts import context
from acts.controllers import power_metrics
from acts.controllers import power_monitor
from acts.controllers.bits_lib import bits_client
from acts.controllers.bits_lib import bits_service
from acts.controllers.bits_lib import bits_service_config as bsc

MOBLY_CONTROLLER_CONFIG_NAME = 'Bits'
ACTS_CONTROLLER_REFERENCE_NAME = 'bitses'


def create(configs):
    return [Bits(index, config) for (index, config) in enumerate(configs)]


def destroy(bitses):
    for bits in bitses:
        bits.teardown()


def get_info(bitses):
    return [bits.config for bits in bitses]


class BitsError(Exception):
    pass


class _BitsCollection(object):
    """Object that represents a bits collection

    Attributes:
        name: The name given to the collection.
        markers_buffer: An array of un-flushed markers, each marker is
        represented by a bi-dimensional tuple with the format
        (<nanoseconds_since_epoch or datetime>, <text>).
        monsoon_output_path: A path to store monsoon-like data if possible, Bits
        uses this path to attempt data extraction in monsoon format, if this
        parameter is left as None such extraction is not attempted.
    """

    def __init__(self, name, monsoon_output_path=None):
        self.monsoon_output_path = monsoon_output_path
        self.name = name
        self.markers_buffer = []

    def add_marker(self, timestamp, marker_text):
        self.markers_buffer.append((timestamp, marker_text))


def _transform_name(bits_metric_name):
    """Transform bits metrics names to a more succinct version.

    Examples of bits_metrics_name as provided by the client:
    - default_device.slider.C1_30__PP0750_L1S_VDD_G3D_M_P:mA,
    - default_device.slider.C1_30__PP0750_L1S_VDD_G3D_M_P:mW,
    - default_device.Monsoon.Monsoon:mA,
    - default_device.Monsoon.Monsoon:mW,
    - <device>.<collector>.<rail>:<unit>

    Args:
        bits_metric_name: A bits metric name.

    Returns:
        For monsoon metrics, and for backwards compatibility:
          Monsoon:mA -> avg_current,
          Monsoon:mW -> avg_power,

        For everything else:
          <rail>:mW -> <rail/rail>_avg_current
          <rail>:mW -> <rail/rail>_avg_power
          ...
    """
    prefix, unit = bits_metric_name.split(':')
    rail = prefix.split('.')[-1]

    if 'mW' == unit:
        suffix = 'avg_power'
    elif 'mA' == unit:
        suffix = 'avg_current'
    elif 'mV' == unit:
        suffix = 'avg_voltage'
    else:
        logging.warning('unknown unit type for unit %s' % unit)
        suffix = ''

    if 'Monsoon' == rail:
        return suffix
    elif suffix == '':
        return rail
    else:
        return '%s_%s' % (rail, suffix)


def _raw_data_to_metrics(raw_data_obj):
    data = raw_data_obj['data']
    metrics = []
    for sample in data:
        unit = sample['unit']
        if 'Msg' == unit:
            continue
        elif 'mW' == unit:
            unit_type = 'power'
        elif 'mA' == unit:
            unit_type = 'current'
        elif 'mV' == unit:
            unit_type = 'voltage'
        else:
            logging.warning('unknown unit type for unit %s' % unit)
            continue

        name = _transform_name(sample['name'])
        avg = sample['avg']
        metrics.append(power_metrics.Metric(avg, unit_type, unit, name=name))

    return metrics


def _get_single_file(registry, key):
    if key not in registry:
        return None
    entry = registry[key]
    if isinstance(entry, str):
        return entry
    if isinstance(entry, list):
        return None if len(entry) == 0 else entry[0]
    raise ValueError('registry["%s"] is of unsupported type %s for this '
                     'operation. Supported types are str and list.' % (
                         key, type(entry)))


class Bits(object):

    ROOT_RAIL_KEY = 'RootRail'
    ROOT_RAIL_DEFAULT_VALUE = 'Monsoon:mA'

    def __init__(self, index, config):
        """Creates an instance of a bits controller.

        Args:
            index: An integer identifier for this instance, this allows to
                tell apart different instances in the case where multiple
                bits controllers are being used concurrently.
            config: The config as defined in the ACTS  BiTS controller config.
                Expected format is:
                {
                    // optional
                    'Monsoon':   {
                        'serial_num': <serial number:int>,
                        'monsoon_voltage': <voltage:double>
                    }
                    // optional
                    'Kibble': [
                        {
                            'board': 'BoardName1',
                            'connector': 'A',
                            'serial': 'serial_1'
                        },
                        {
                            'board': 'BoardName2',
                            'connector': 'D',
                            'serial': 'serial_2'
                        }
                    ]
                    // optional
                    'RootRail': 'Monsoon:mA'
                }
        """
        self.index = index
        self.config = config
        self._service = None
        self._client = None
        self._active_collection = None
        self._collections_counter = 0
        self._root_rail = config.get(self.ROOT_RAIL_KEY,
                                     self.ROOT_RAIL_DEFAULT_VALUE)

    def setup(self, *_, registry=None, **__):
        """Starts a bits_service in the background.

        This function needs to be called with either a registry or after calling
        power_monitor.update_registry, and it needs to be called before any other
        method in this class.

        Args:
            registry: A dictionary with files used by bits. Format:
                {
                    // required, string or list of strings
                    bits_service: ['/path/to/bits_service']

                    // required, string or list of strings
                    bits_client: ['/path/to/bits.par']

                    // needed for monsoon, string or list of strings
                    lvpm_monsoon: ['/path/to/lvpm_monsoon.par']

                    // needed for monsoon, string or list of strings
                    hvpm_monsoon: ['/path/to/hvpm_monsoon.par']

                    // needed for kibble, string or list of strings
                    kibble_bin: ['/path/to/kibble.par']

                    // needed for kibble, string or list of strings
                    kibble_board_file: ['/path/to/phone_s.board']

                    // optional, string or list of strings
                    vm_file: ['/path/to/file.vm']
                }

                All fields in this dictionary can be either a string or a list
                of strings. If lists are passed, only their first element is
                taken into account. The reason for supporting lists but only
                acting on their first element is for easier integration with
                harnesses that handle resources as lists.
        """
        if registry is None:
            registry = power_monitor.get_registry()
        if 'bits_service' not in registry:
            raise ValueError('No bits_service binary has been defined in the '
                             'global registry.')
        if 'bits_client' not in registry:
            raise ValueError('No bits_client binary has been defined in the '
                             'global registry.')

        bits_service_binary = _get_single_file(registry, 'bits_service')
        bits_client_binary = _get_single_file(registry, 'bits_client')
        lvpm_monsoon_bin = _get_single_file(registry, 'lvpm_monsoon')
        hvpm_monsoon_bin = _get_single_file(registry, 'hvpm_monsoon')
        kibble_bin = _get_single_file(registry, 'kibble_bin')
        kibble_board_file = _get_single_file(registry, 'kibble_board_file')
        vm_file = _get_single_file(registry, 'vm_file')
        config = bsc.BitsServiceConfig(self.config,
                                       lvpm_monsoon_bin=lvpm_monsoon_bin,
                                       hvpm_monsoon_bin=hvpm_monsoon_bin,
                                       kibble_bin=kibble_bin,
                                       kibble_board_file=kibble_board_file,
                                       virtual_metrics_file=vm_file)
        output_log = os.path.join(
            context.get_current_context().get_full_output_path(),
            'bits_service_out_%s.txt' % self.index)
        service_name = 'bits_config_%s' % self.index

        self._active_collection = None
        self._collections_counter = 0
        self._service = bits_service.BitsService(config,
                                                 bits_service_binary,
                                                 output_log,
                                                 name=service_name,
                                                 timeout=3600 * 24)
        self._service.start()
        self._client = bits_client.BitsClient(bits_client_binary,
                                              self._service,
                                              config)
        # this call makes sure that the client can interact with the server.
        devices = self._client.list_devices()
        logging.debug(devices)

    def disconnect_usb(self, *_, **__):
        self._client.disconnect_usb()

    def connect_usb(self, *_, **__):
        self._client.connect_usb()

    def measure(self, *_, measurement_args=None,
                measurement_name=None, monsoon_output_path=None,
                **__):
        """Blocking function that measures power through bits for the specified
        duration. Results need to be consulted through other methods such as
        get_metrics or post processing files like the ones
        generated at monsoon_output_path after calling `release_resources`.

        Args:
            measurement_args: A dictionary with the following structure:
                {
                   'duration': <seconds to measure for>
                   'hz': <samples per second>
                   'measure_after_seconds': <sleep time before measurement>
                }
                The actual number of samples per second is limited by the
                bits configuration. The value of hz is defaulted to 1000.
            measurement_name: A name to give to the measurement (which is also
                used as the Bits collection name. Bits collection names (and
                therefore measurement names) need to be unique within the
                context of a Bits object.
            monsoon_output_path: If provided this path will be used to generate
                a monsoon like formatted file at the release_resources step.
        """
        if measurement_args is None:
            raise ValueError('measurement_args can not be left undefined')

        duration = measurement_args.get('duration')
        if duration is None:
            raise ValueError(
                'duration can not be left undefined within measurement_args')

        hz = measurement_args.get('hz', 1000)

        # Delay the start of the measurement if an offset is required
        measure_after_seconds = measurement_args.get('measure_after_seconds')
        if measure_after_seconds:
            time.sleep(measure_after_seconds)

        if self._active_collection:
            raise BitsError(
                'Attempted to start a collection while there is still an '
                'active one. Active collection: %s',
                self._active_collection.name)

        self._collections_counter = self._collections_counter + 1
        # The name gets a random 8 characters salt suffix because the Bits
        # client has a bug where files with the same name are considered to be
        # the same collection and it won't load two files with the same name.
        # b/153170987 b/153944171
        if not measurement_name:
            measurement_name = 'bits_collection_%s_%s' % (
                str(self._collections_counter), str(uuid.uuid4())[0:8])

        self._active_collection = _BitsCollection(measurement_name,
                                                  monsoon_output_path)
        self._client.start_collection(self._active_collection.name,
                                      default_sampling_rate=hz)
        time.sleep(duration)

    def get_metrics(self, *_, timestamps=None, **__):
        """Gets metrics for the segments delimited by the timestamps dictionary.

        Must be called before releasing resources, otherwise it will fail adding
        markers to the collection.

        Args:
            timestamps: A dictionary of the shape:
                {
                    'segment_name': {
                        'start' : <milliseconds_since_epoch> or <datetime>
                        'end': <milliseconds_since_epoch> or <datetime>
                    }
                    'another_segment': {
                        'start' : <milliseconds_since_epoch> or <datetime>
                        'end': <milliseconds_since_epoch> or <datetime>
                    }
                }
        Returns:
            A dictionary of the shape:
                {
                    'segment_name': <list of power_metrics.Metric>
                    'another_segment': <list of power_metrics.Metric>
                }
        """
        if timestamps is None:
            raise ValueError('timestamps dictionary can not be left undefined')

        metrics = {}

        for segment_name, times in timestamps.items():
            if 'start' not in times or 'end' not in times:
                continue

            start = times['start']
            end = times['end']

            # bits accepts nanoseconds only, but since this interface needs to
            # backwards compatible with monsoon which works with milliseconds we
            # require to do a conversion from milliseconds to nanoseconds.
            # The preferred way for new calls to this function should be using
            # datetime instead which is unambiguous
            if isinstance(start, (int, float)):
                start = start * 1e6
            if isinstance(end, (int, float)):
                end = end * 1e6

            raw_metrics = self._client.get_metrics(self._active_collection.name,
                                                   start=start, end=end)
            self._add_marker(start, 'start - %s' % segment_name)
            self._add_marker(end, 'end - %s' % segment_name)
            metrics[segment_name] = _raw_data_to_metrics(raw_metrics)
        return metrics

    def _add_marker(self, timestamp, marker_text):
        if not self._active_collection:
            raise BitsError(
                'markers can not be added without an active collection')
        self._active_collection.add_marker(timestamp, marker_text)

    def release_resources(self):
        """Performs all the cleanup and export tasks.

        In the way that Bits' is interfaced several tasks can not be performed
        while a collection is still active (like exporting the data) and others
        can only take place while the collection is still active (like adding
        markers to a collection).

        To workaround this unique workflow, the collections that are started
        with the 'measure' method are not really stopped after the method
        is unblocked, it is only stopped after this method is called.

        All the export files (.7z.bits and monsoon-formatted file) are also
        generated in this method.
        """
        if not self._active_collection:
            raise BitsError(
                'Attempted to stop a collection without starting one')
        self._client.add_markers(self._active_collection.name,
                                 self._active_collection.markers_buffer)
        self._client.stop_collection(self._active_collection.name)

        export_file = os.path.join(
            context.get_current_context().get_full_output_path(),
            '%s.7z.bits' % self._active_collection.name)
        self._client.export(self._active_collection.name, export_file)
        if self._active_collection.monsoon_output_path:
            self._attempt_monsoon_format()
        self._active_collection = None

    def _attempt_monsoon_format(self):
        """Attempts to create a monsoon-formatted file.

        In the case where there is not enough information to retrieve a
        monsoon-like file, this function will do nothing.
        """
        metrics = self._client.get_metrics(self._active_collection.name)

        try:
            self._save_rails_csv(metrics)
        except Exception as e:
            logging.warning(
                'Could not save rails data to csv format with error {}'.format(e))
        available_channels = [channel['name'] for channel in metrics['data']]
        milli_amps_channel = None

        for channel in available_channels:
            if channel.endswith(self._root_rail):
                milli_amps_channel = self._root_rail
                break

        if milli_amps_channel is None:
            logging.debug('No monsoon equivalent channels were found when '
                          'attempting to recreate monsoon file format. '
                          'Available channels were: %s',
                          str(available_channels))
            return

        logging.debug('Recreating monsoon file format from channel: %s',
                      milli_amps_channel)
        self._client.export_as_monsoon_format(
            self._active_collection.monsoon_output_path,
            self._active_collection.name,
            milli_amps_channel)

    def _save_rails_csv(self, metrics):
        # Creates csv path for rails data
        monsoon_path = self._active_collection.monsoon_output_path
        dir_path = os.path.dirname(monsoon_path)
        if dir_path.endswith('Monsoon'):
            dir_path = os.path.join(os.path.dirname(dir_path), 'Kibble')
            os.makedirs(dir_path, exist_ok=True)
        rails_basename = os.path.basename(monsoon_path)
        if rails_basename.endswith('.txt'):
            rails_basename = os.path.splitext(rails_basename)[0]
        json_basename = 'kibble_rails_' + rails_basename + '.json'
        rails_basename = 'kibble_rails_' + rails_basename + '.csv'
        root_rail_results_basename = '{}_results.csv'.format(
            self._root_rail.split(':')[0])
        rails_csv_path = os.path.join(dir_path, rails_basename)
        rails_json_path = os.path.join(dir_path, json_basename)
        root_rail_results_path = os.path.join(dir_path, root_rail_results_basename)

        logging.info('dump metric to json format: {}'.format(rails_json_path))
        with open(rails_json_path, 'w') as f:
            json.dump(metrics['data'], f, sort_keys=True, indent=2)

        # Gets all channels
        channels = {
            channel['name'].split('.')[-1].split(':')[0]
            for channel in metrics['data']
        }
        channels = list(channels)
        list.sort(channels)

        rail_dict = {
            channel['name'].split('.')[-1] : channel['avg']
            for channel in metrics['data']
        }

        root_rail_key = self._root_rail.split(':')[0] + ':mW'
        root_rail_power = 0
        if root_rail_key in rail_dict:
            root_rail_power = rail_dict[root_rail_key]
        logging.info('root rail {} power is: {}'.format(root_rail_key, root_rail_power))

        path_existed = os.path.exists(root_rail_results_path)
        with open(root_rail_results_path, 'a') as f:
            if not path_existed:
                f.write('{},{}'.format(root_rail_key, 'power(mW)'))
            f.write('\n{},{}'.format(self._active_collection.name, root_rail_power))

        header = ['CHANNEL', 'VALUE', 'UNIT', 'VALUE', 'UNIT', 'VALUE', 'UNIT']
        with open(rails_csv_path, 'w') as f:
            csvwriter = csv.writer(f)
            csvwriter.writerow(header)
            for key in  channels:
                if not key.startswith('C') and not key.startswith('M'):
                    continue
                try:
                    row = [key, '0', 'mA', '0', 'mV', '0', 'mW']
                    row[1] = str(rail_dict[key + ':mA'])
                    row[3] = str(rail_dict[key + ':mV'])
                    row[5] = str(rail_dict[key + ':mW'])
                    csvwriter.writerow(row)
                    logging.debug('channel {}: {}'.format(key, row))
                except Exception as e:
                    logging.info('channel {} fail'.format(key))

    def get_waveform(self, file_path=None):
        """Parses a file generated in release_resources.

        Args:
            file_path: Path to a waveform file.

        Returns:
            A list of tuples in which the first element is a timestamp and the
            second element is the sampled current at that time.
        """
        if file_path is None:
            raise ValueError('file_path can not be None')

        return list(power_metrics.import_raw_data(file_path))

    def get_bits_root_rail_csv_export(self, file_path=None, collection_name=None):
        """Export raw data samples for root rail in csv format.

        Args:
            file_path: Path to save the export file.
            collection_name: Name of collection to be exported on client.
        """
        if file_path is None:
            raise ValueError('file_path cannot be None')
        if collection_name is None:
            raise ValueError('collection_name cannot be None')
        try:
            key = self._root_rail.split(':')[0] + ':mW'
            file_name = 'raw_data_' + collection_name + '.csv'
            raw_bits_data_path = os.path.join(file_path, file_name)
            self._client.export_as_csv([key], collection_name,
                                       raw_bits_data_path)
        except Exception as e:
            logging.warning('Failed to save raw data due to :  {}'.format(e))

    def teardown(self):
        if self._service is None:
            return

        if self._service.service_state == bits_service.BitsServiceStates.STARTED:
            self._service.stop()
