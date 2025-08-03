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

import csv
import os
import posixpath
import time
import zipfile
import acts_contrib.test_utils.wifi.wifi_test_utils as wutils

from acts import context
from acts import logger
from acts import utils
from acts.controllers.utils_lib import ssh

WifiEnums = wutils.WifiEnums
SNIFFER_TIMEOUT = 6


def create(configs):
    """Factory method for sniffer.
    Args:
        configs: list of dicts with sniffer settings.
        Settings must contain the following : ssh_settings, type, OS, interface.

    Returns:
        objs: list of sniffer class objects.
    """
    objs = []
    for config in configs:
        try:
            if config['type'] == 'tshark':
                if config['os'] == 'unix':
                    objs.append(TsharkSnifferOnUnix(config))
                elif config['os'] == 'linux':
                    objs.append(TsharkSnifferOnLinux(config))
                else:
                    raise RuntimeError('Wrong sniffer config')

            elif config['type'] == 'mock':
                objs.append(MockSniffer(config))
        except KeyError:
            raise KeyError('Invalid sniffer configurations')
        return objs


def destroy(objs):
    return


class OtaSnifferBase(object):
    """Base class defining common sniffers functions."""

    _log_file_counter = 0

    @property
    def started(self):
        raise NotImplementedError('started must be specified.')

    def start_capture(self, network, duration=30):
        """Starts the sniffer Capture.

        Args:
            network: dict containing network information such as SSID, etc.
            duration: duration of sniffer capture in seconds.
        """
        raise NotImplementedError('start_capture must be specified.')

    def stop_capture(self, tag=''):
        """Stops the sniffer Capture.

        Args:
            tag: string to tag sniffer capture file name with.
        """
        raise NotImplementedError('stop_capture must be specified.')

    def _get_remote_dump_path(self):
        """Returns name of the sniffer dump file."""
        remote_file_name = 'sniffer_dump.{}'.format(
            self.sniffer_output_file_type)
        remote_dump_path = posixpath.join(posixpath.sep, 'tmp',
                                          remote_file_name)
        return remote_dump_path

    def _get_full_file_path(self, tag=None):
        """Returns the full file path for the sniffer capture dump file.

        Returns the full file path (on test machine) for the sniffer capture
        dump file.

        Args:
            tag: The tag appended to the sniffer capture dump file .
        """
        tags = [tag, 'count', OtaSnifferBase._log_file_counter]
        out_file_name = 'Sniffer_Capture_%s.%s' % ('_'.join([
            str(x) for x in tags if x != '' and x is not None
        ]), self.sniffer_output_file_type)
        OtaSnifferBase._log_file_counter += 1

        file_path = os.path.join(self.log_path, out_file_name)
        return file_path

    @property
    def log_path(self):
        current_context = context.get_current_context()
        full_out_dir = os.path.join(current_context.get_full_output_path(),
                                    'sniffer_captures')

        # Ensure the directory exists.
        os.makedirs(full_out_dir, exist_ok=True)

        return full_out_dir


class MockSniffer(OtaSnifferBase):
    """Class that implements mock sniffer for test development and debug."""

    def __init__(self, config):
        self.log = logger.create_tagged_trace_logger('Mock Sniffer')

    def start_capture(self, network, duration=30):
        """Starts sniffer capture on the specified machine.

        Args:
            network: dict of network credentials.
            duration: duration of the sniff.
        """
        self.log.debug('Starting sniffer.')

    def stop_capture(self):
        """Stops the sniffer.

        Returns:
            log_file: name of processed sniffer.
        """

        self.log.debug('Stopping sniffer.')
        log_file = self._get_full_file_path()
        with open(log_file, 'w') as file:
            file.write('this is a sniffer dump.')
        return log_file


class TsharkSnifferBase(OtaSnifferBase):
    """Class that implements Tshark based sniffer controller. """

    TYPE_SUBTYPE_DICT = {
        '0': 'Association Requests',
        '1': 'Association Responses',
        '2': 'Reassociation Requests',
        '3': 'Resssociation Responses',
        '4': 'Probe Requests',
        '5': 'Probe Responses',
        '8': 'Beacon',
        '9': 'ATIM',
        '10': 'Disassociations',
        '11': 'Authentications',
        '12': 'Deauthentications',
        '13': 'Actions',
        '24': 'Block ACK Requests',
        '25': 'Block ACKs',
        '26': 'PS-Polls',
        '27': 'RTS',
        '28': 'CTS',
        '29': 'ACK',
        '30': 'CF-Ends',
        '31': 'CF-Ends/CF-Acks',
        '32': 'Data',
        '33': 'Data+CF-Ack',
        '34': 'Data+CF-Poll',
        '35': 'Data+CF-Ack+CF-Poll',
        '36': 'Null',
        '37': 'CF-Ack',
        '38': 'CF-Poll',
        '39': 'CF-Ack+CF-Poll',
        '40': 'QoS Data',
        '41': 'QoS Data+CF-Ack',
        '42': 'QoS Data+CF-Poll',
        '43': 'QoS Data+CF-Ack+CF-Poll',
        '44': 'QoS Null',
        '46': 'QoS CF-Poll (Null)',
        '47': 'QoS CF-Ack+CF-Poll (Null)'
    }

    TSHARK_COLUMNS = [
        'frame_number', 'frame_time_relative', 'mactime', 'frame_len', 'rssi',
        'channel', 'ta', 'ra', 'bssid', 'type', 'subtype', 'duration', 'seq',
        'retry', 'pwrmgmt', 'moredata', 'ds', 'phy', 'radio_datarate',
        'vht_datarate', 'radiotap_mcs_index', 'vht_mcs', 'wlan_data_rate',
        '11n_mcs_index', '11ac_mcs', '11n_bw', '11ac_bw', 'vht_nss', 'mcs_gi',
        'vht_gi', 'vht_coding', 'ba_bm', 'fc_status', 'bf_report'
    ]

    TSHARK_OUTPUT_COLUMNS = [
        'frame_number', 'frame_time_relative', 'mactime', 'ta', 'ra', 'bssid',
        'rssi', 'channel', 'frame_len', 'Info', 'radio_datarate',
        'radiotap_mcs_index', 'pwrmgmt', 'phy', 'vht_nss', 'vht_mcs',
        'vht_datarate', '11ac_mcs', '11ac_bw', 'vht_gi', 'vht_coding',
        'wlan_data_rate', '11n_mcs_index', '11n_bw', 'mcs_gi', 'type',
        'subtype', 'duration', 'seq', 'retry', 'moredata', 'ds', 'ba_bm',
        'fc_status', 'bf_report'
    ]

    TSHARK_FIELDS_LIST = [
        'frame.number', 'frame.time_relative', 'radiotap.mactime', 'frame.len',
        'radiotap.dbm_antsignal', 'wlan_radio.channel', 'wlan.ta', 'wlan.ra',
        'wlan.bssid', 'wlan.fc.type', 'wlan.fc.type_subtype', 'wlan.duration',
        'wlan.seq', 'wlan.fc.retry', 'wlan.fc.pwrmgt', 'wlan.fc.moredata',
        'wlan.fc.ds', 'wlan_radio.phy', 'radiotap.datarate',
        'radiotap.vht.datarate.0', 'radiotap.mcs.index', 'radiotap.vht.mcs.0',
        'wlan_radio.data_rate', 'wlan_radio.11n.mcs_index',
        'wlan_radio.11ac.mcs', 'wlan_radio.11n.bandwidth',
        'wlan_radio.11ac.bandwidth', 'radiotap.vht.nss.0', 'radiotap.mcs.gi',
        'radiotap.vht.gi', 'radiotap.vht.coding.0', 'wlan.ba.bm',
        'wlan.fcs.status', 'wlan.vht.compressed_beamforming_report.snr'
    ]

    def __init__(self, config):
        self.sniffer_proc_pid = None
        self.log = logger.create_tagged_trace_logger('Tshark Sniffer')
        self.ssh_config = config['ssh_config']
        self.sniffer_os = config['os']
        self.run_as_sudo = config.get('run_as_sudo', False)
        self.sniffer_output_file_type = config['output_file_type']
        self.sniffer_snap_length = config['snap_length']
        self.sniffer_interface = config['interface']
        self.sniffer_disabled = False

        #Logging into sniffer
        self.log.info('Logging into sniffer.')
        self._sniffer_server = ssh.connection.SshConnection(
            ssh.settings.from_config(self.ssh_config))
        # Get tshark params
        self.tshark_fields = self._generate_tshark_fields(
            self.TSHARK_FIELDS_LIST)
        self.tshark_path = self._sniffer_server.run('which tshark').stdout

    @property
    def _started(self):
        return self.sniffer_proc_pid is not None

    def _scan_for_networks(self):
        """Scans for wireless networks on the sniffer."""
        raise NotImplementedError

    def _get_tshark_command(self, duration):
        """Frames the appropriate tshark command.

        Args:
            duration: duration to sniff for.

        Returns:
            tshark_command : appropriate tshark command.
        """
        tshark_command = '{} -l -i {} -I -t u -a duration:{}'.format(
            self.tshark_path, self.sniffer_interface, int(duration))
        if self.run_as_sudo:
            tshark_command = 'sudo {}'.format(tshark_command)

        return tshark_command

    def _get_sniffer_command(self, tshark_command):
        """
        Frames the appropriate sniffer command.

        Args:
            tshark_command: framed tshark command

        Returns:
            sniffer_command: appropriate sniffer command
        """
        if self.sniffer_output_file_type in ['pcap', 'pcapng']:
            sniffer_command = ' {tshark} -s {snaplength} -w {log_file} '.format(
                tshark=tshark_command,
                snaplength=self.sniffer_snap_length,
                log_file=self._get_remote_dump_path())

        elif self.sniffer_output_file_type == 'csv':
            sniffer_command = '{tshark} {fields} > {log_file}'.format(
                tshark=tshark_command,
                fields=self.tshark_fields,
                log_file=self._get_remote_dump_path())

        else:
            raise KeyError('Sniffer output file type not configured correctly')

        return sniffer_command

    def _generate_tshark_fields(self, fields):
        """Generates tshark fields to be appended to the tshark command.

        Args:
            fields: list of tshark fields to be appended to the tshark command.

        Returns:
            tshark_fields: string of tshark fields to be appended
            to the tshark command.
        """
        tshark_fields = "-T fields -y IEEE802_11_RADIO -E separator='^'"
        for field in fields:
            tshark_fields = tshark_fields + ' -e {}'.format(field)
        return tshark_fields

    def _configure_sniffer(self, network, chan, bw):
        """ Connects to a wireless network using networksetup utility.

        Args:
            network: dictionary of network credentials; SSID and password.
        """
        raise NotImplementedError

    def _run_tshark(self, sniffer_command):
        """Starts the sniffer.

        Args:
            sniffer_command: sniffer command to execute.
        """
        self.log.debug('Starting sniffer.')
        sniffer_job = self._sniffer_server.run_async(sniffer_command)
        self.sniffer_proc_pid = sniffer_job.stdout

    def _stop_tshark(self):
        """ Stops the sniffer."""
        self.log.debug('Stopping sniffer')

        # while loop to kill the sniffer process
        stop_time = time.time() + SNIFFER_TIMEOUT
        while time.time() < stop_time:
            # Wait before sending more kill signals
            time.sleep(0.1)
            try:
                # Returns 1 if process was killed
                self._sniffer_server.run(
                    'ps aux| grep {} | grep -v grep'.format(
                        self.sniffer_proc_pid))
            except:
                return
            try:
                # Returns error if process was killed already
                self._sniffer_server.run('sudo kill -15 {}'.format(
                    str(self.sniffer_proc_pid)))
            except:
                # Except is hit when tshark is already dead but we will break
                # out of the loop when confirming process is dead using ps aux
                pass
        self.log.warning('Could not stop sniffer. Trying with SIGKILL.')
        try:
            self.log.debug('Killing sniffer with SIGKILL.')
            self._sniffer_server.run('sudo kill -9 {}'.format(
                str(self.sniffer_proc_pid)))
        except:
            self.log.debug('Sniffer process may have stopped succesfully.')

    def _process_tshark_dump(self, log_file):
        """ Process tshark dump for better readability.

        Processes tshark dump for better readability and saves it to a file.
        Adds an info column at the end of each row. Format of the info columns:
        subtype of the frame, sequence no and retry status.

        Args:
            log_file : unprocessed sniffer output
        Returns:
            log_file : processed sniffer output
        """
        temp_dump_file = os.path.join(self.log_path, 'sniffer_temp_dump.csv')
        utils.exe_cmd('cp {} {}'.format(log_file, temp_dump_file))

        with open(temp_dump_file, 'r') as input_csv, open(log_file,
                                                          'w') as output_csv:
            reader = csv.DictReader(input_csv,
                                    fieldnames=self.TSHARK_COLUMNS,
                                    delimiter='^')
            writer = csv.DictWriter(output_csv,
                                    fieldnames=self.TSHARK_OUTPUT_COLUMNS,
                                    delimiter='\t')
            writer.writeheader()
            for row in reader:
                if row['subtype'] in self.TYPE_SUBTYPE_DICT:
                    row['Info'] = '{sub} S={seq} retry={retry_status}'.format(
                        sub=self.TYPE_SUBTYPE_DICT[row['subtype']],
                        seq=row['seq'],
                        retry_status=row['retry'])
                else:
                    row['Info'] = '{} S={} retry={}\n'.format(
                        row['subtype'], row['seq'], row['retry'])
                writer.writerow(row)

        utils.exe_cmd('rm -f {}'.format(temp_dump_file))
        return log_file

    def start_capture(self, network, chan, bw, duration=60):
        """Starts sniffer capture on the specified machine.

        Args:
            network: dict describing network to sniff on.
            duration: duration of sniff.
        """
        # Checking for existing sniffer processes
        if self._started:
            self.log.debug('Sniffer already running')
            return

        # Configure sniffer
        self._configure_sniffer(network, chan, bw)
        tshark_command = self._get_tshark_command(duration)
        sniffer_command = self._get_sniffer_command(tshark_command)

        # Starting sniffer capture by executing tshark command
        self._run_tshark(sniffer_command)

    def stop_capture(self, tag=''):
        """Stops the sniffer.

        Args:
            tag: tag to be appended to the sniffer output file.
        Returns:
            log_file: path to sniffer dump.
        """
        # Checking if there is an ongoing sniffer capture
        if not self._started:
            self.log.debug('No sniffer process running')
            return
        # Killing sniffer process
        self._stop_tshark()

        # Processing writing capture output to file
        log_file = self._get_full_file_path(tag)
        self._sniffer_server.run('sudo chmod 777 {}'.format(
            self._get_remote_dump_path()))
        self._sniffer_server.pull_file(log_file, self._get_remote_dump_path())

        if self.sniffer_output_file_type == 'csv':
            log_file = self._process_tshark_dump(log_file)
        if self.sniffer_output_file_type == 'pcap':
            zip_file_path = log_file[:-4] + "zip"
            zipfile.ZipFile(zip_file_path, 'w', zipfile.ZIP_DEFLATED).write(
                log_file, arcname=log_file.split('/')[-1])
            os.remove(log_file)

        self.sniffer_proc_pid = None
        return log_file


class TsharkSnifferOnUnix(TsharkSnifferBase):
    """Class that implements Tshark based sniffer controller on Unix systems."""

    def _scan_for_networks(self):
        """Scans the wireless networks on the sniffer.

        Returns:
            scan_results : output of the scan command.
        """
        scan_command = '/usr/local/bin/airport -s'
        scan_result = self._sniffer_server.run(scan_command).stdout

        return scan_result

    def _configure_sniffer(self, network, chan, bw):
        """Connects to a wireless network using networksetup utility.

        Args:
            network: dictionary of network credentials; SSID and password.
        """

        self.log.debug('Connecting to network {}'.format(network['SSID']))

        if 'password' not in network:
            network['password'] = ''

        connect_command = 'networksetup -setairportnetwork en0 {} {}'.format(
            network['SSID'], network['password'])
        self._sniffer_server.run(connect_command)


class TsharkSnifferOnLinux(TsharkSnifferBase):
    """Class that implements Tshark based sniffer controller on Linux."""

    def __init__(self, config):
        super().__init__(config)
        self._init_sniffer()
        self.channel = None
        self.bandwidth = None

    def _init_sniffer(self):
        """Function to configure interface for the first time"""
        self._sniffer_server.run('sudo modprobe -r iwlwifi')
        self._sniffer_server.run('sudo dmesg -C')
        self._sniffer_server.run('cat /dev/null | sudo tee /var/log/syslog')
        self._sniffer_server.run('sudo modprobe iwlwifi debug=0x1')
        # Wait for wifi config changes before trying to further configuration
        # e.g. setting monitor mode (which will fail if above is not complete)
        time.sleep(1)

    def start_capture(self, network, chan, bw, duration=60):
        """Starts sniffer capture on the specified machine.

        Args:
            network: dict describing network to sniff on.
            duration: duration of sniff.
        """
        # If sniffer doesnt support the channel, return
        if '6g' in str(chan):
            self.log.debug('Channel not supported on sniffer')
            return
        # Checking for existing sniffer processes
        if self._started:
            self.log.debug('Sniffer already running')
            return

        # Configure sniffer
        self._configure_sniffer(network, chan, bw)
        tshark_command = self._get_tshark_command(duration)
        sniffer_command = self._get_sniffer_command(tshark_command)

        # Starting sniffer capture by executing tshark command
        self._run_tshark(sniffer_command)

    def set_monitor_mode(self, chan, bw):
        """Function to configure interface to monitor mode

        Brings up the sniffer wireless interface in monitor mode and
        tunes it to the appropriate channel and bandwidth

        Args:
            chan: primary channel (int) to tune the sniffer to
            bw: bandwidth (int) to tune the sniffer to
        """
        if chan == self.channel and bw == self.bandwidth:
            return

        self.channel = chan
        self.bandwidth = bw

        channel_map = {
            80: {
                tuple(range(36, 50, 2)): 42,
                tuple(range(52, 66, 2)): 58,
                tuple(range(100, 114, 2)): 106,
                tuple(range(116, 130, 2)): 122,
                tuple(range(132, 146, 2)): 138,
                tuple(range(149, 163, 2)): 155
            },
            40: {
                (36, 38, 40): 38,
                (44, 46, 48): 46,
                (52, 54, 56): 54,
                (60, 62, 64): 62,
                (100, 102, 104): 102,
                (108, 110, 112): 108,
                (116, 118, 120): 118,
                (124, 126, 128): 126,
                (132, 134, 136): 134,
                (140, 142, 144): 142,
                (149, 151, 153): 151,
                (157, 159, 161): 159
            },
            160: {
                (36, 38, 40): 50
            }
        }

        if chan <= 13:
            primary_freq = WifiEnums.channel_2G_to_freq[chan]
        else:
            primary_freq = WifiEnums.channel_5G_to_freq[chan]

        self._sniffer_server.run('sudo ifconfig {} down'.format(
            self.sniffer_interface))
        self._sniffer_server.run('sudo iwconfig {} mode monitor'.format(
            self.sniffer_interface))
        self._sniffer_server.run('sudo ifconfig {} up'.format(
            self.sniffer_interface))

        if bw in channel_map:
            for tuple_chan in channel_map[bw]:
                if chan in tuple_chan:
                    center_freq = WifiEnums.channel_5G_to_freq[channel_map[bw]
                                                               [tuple_chan]]
                    self._sniffer_server.run(
                        'sudo iw dev {} set freq {} {} {}'.format(
                            self.sniffer_interface, primary_freq, bw,
                            center_freq))

        else:
            self._sniffer_server.run('sudo iw dev {} set freq {}'.format(
                self.sniffer_interface, primary_freq))

    def _configure_sniffer(self, network, chan, bw):
        """ Connects to a wireless network using networksetup utility.

        Args:
            network: dictionary of network credentials; SSID and password.
        """

        self.log.debug('Setting monitor mode on Ch {}, bw {}'.format(chan, bw))
        self.set_monitor_mode(chan, bw)
