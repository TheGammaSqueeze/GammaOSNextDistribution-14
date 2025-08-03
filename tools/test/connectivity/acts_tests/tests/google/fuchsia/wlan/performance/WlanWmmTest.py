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

import operator
import time

from bokeh.palettes import Category10
from bokeh.plotting import ColumnDataSource, figure, output_file, save
from bokeh.models import Span, Label

from acts import asserts
from acts import context
from acts import utils
from acts.controllers.access_point import setup_ap
from acts.controllers.ap_lib import hostapd_constants
from acts.controllers.ap_lib import hostapd_security
from acts_contrib.test_utils.abstract_devices import wmm_transceiver
from acts_contrib.test_utils.fuchsia import wmm_test_cases
from acts_contrib.test_utils.wifi.WifiBaseTest import WifiBaseTest
from acts_contrib.test_utils.abstract_devices.wlan_device import create_wlan_device

DEFAULT_N_CAPABILITIES_20_MHZ = [
    hostapd_constants.N_CAPABILITY_LDPC, hostapd_constants.N_CAPABILITY_SGI20,
    hostapd_constants.N_CAPABILITY_TX_STBC,
    hostapd_constants.N_CAPABILITY_RX_STBC1,
    hostapd_constants.N_CAPABILITY_HT20
]

DEFAULT_AP_PARAMS = {
    'profile_name': 'whirlwind',
    'channel': hostapd_constants.AP_DEFAULT_CHANNEL_2G,
    'n_capabilities': DEFAULT_N_CAPABILITIES_20_MHZ,
    'ac_capabilities': None
}

DEFAULT_BW_PERCENTAGE = 1
DEFAULT_STREAM_TIMEOUT = 60
DEFAULT_STREAM_TIME = 10

OPERATORS = {
    '>': operator.gt,
    '>=': operator.ge,
    '<': operator.lt,
    '<=': operator.le,
    '==': operator.eq
}

GRAPH_COLOR_LEN = 10
GRAPH_DEFAULT_LINE_WIDTH = 2
GRAPH_DEFAULT_CIRCLE_SIZE = 10


def eval_operator(operator_string,
                  actual_value,
                  expected_value,
                  max_bw,
                  rel_tolerance=0,
                  abs_tolerance=0,
                  max_bw_rel_tolerance=0):
    """
    Determines if an inequality evaluates to True, given relative and absolute
    tolerance.

    Args:
        operator_string: string, the operator to use for the comparison
        actual_value: the value to compare to some expected value
        expected_value: the value the actual value is compared to
        rel_tolerance: decimal representing the percent tolerance, relative to
            the expected value. E.g. (101 <= 100) w/ rel_tol=0.01 is True
        abs_tolerance: the lowest actual (not percent) tolerance for error.
            E.g. (101 == 100) w/ rel_tol=0.005 is False, but
            (101 == 100) w/ rel_tol=0.005 and abs_tol=1 is True
        max_bw_rel_tolerance: decimal representing the percent tolerance,
            relative to the maximimum allowed bandwidth.
            E.g. (101 <= max bw of 100) w/ max_bw_rel_tol=0.01 is True


    Returns:
        True, if inequality evaluates to True within tolerances
        False, otherwise
    """
    op = OPERATORS[operator_string]
    if op(actual_value, expected_value):
        return True

    error = abs(actual_value - expected_value)
    accepted_error = max(expected_value * rel_tolerance, abs_tolerance,
                         max_bw * max_bw_rel_tolerance)
    return error <= accepted_error


class WlanWmmTest(WifiBaseTest):
    """Tests WMM QoS Functionality (Station only)

    Testbed Requirements:
    * One ACTS compatible wlan_device (staut)
    * One Whirlwind Access Point
    * For some tests, One additional ACTS compatible device (secondary_sta)

    For accurate results, must be performed in an RF isolated environment.
    """

    def setup_class(self):
        super().setup_class()

        try:
            self.wmm_test_params = self.user_params['wmm_test_params']
            self._wmm_transceiver_configs = self.wmm_test_params[
                'wmm_transceivers']
        except KeyError:
            raise AttributeError('Must provide at least 2 WmmTransceivers in '
                                 '"wmm_test_params" field of ACTS config.')

        if len(self._wmm_transceiver_configs) < 2:
            raise AttributeError(
                'At least 2 WmmTransceivers must be provided.')

        self.android_devices = getattr(self, 'android_devices', [])
        self.fuchsia_devices = getattr(self, 'fuchsia_devices', [])

        self.wlan_devices = [
            create_wlan_device(device)
            for device in self.android_devices + self.fuchsia_devices
        ]

        # Create STAUT transceiver
        if 'staut' not in self._wmm_transceiver_configs:
            raise AttributeError(
                'Must provide a WmmTransceiver labeled "staut" with a '
                'wlan_device.')
        self.staut = wmm_transceiver.create(
            self._wmm_transceiver_configs['staut'],
            identifier='staut',
            wlan_devices=self.wlan_devices)

        # Required to for automated power cycling
        self.dut = self.staut.wlan_device

        # Create AP transceiver
        if 'access_point' not in self._wmm_transceiver_configs:
            raise AttributeError(
                'Must provide a WmmTransceiver labeled "access_point" with a '
                'access_point.')
        self.access_point_transceiver = wmm_transceiver.create(
            self._wmm_transceiver_configs['access_point'],
            identifier='access_point',
            access_points=self.access_points)

        self.wmm_transceivers = [self.staut, self.access_point_transceiver]

        # Create secondary station transceiver, if present
        if 'secondary_sta' in self._wmm_transceiver_configs:
            self.secondary_sta = wmm_transceiver.create(
                self._wmm_transceiver_configs['secondary_sta'],
                identifier='secondary_sta',
                wlan_devices=self.wlan_devices)
            self.wmm_transceivers.append(self.secondary_sta)
        else:
            self.secondary_sta = None

        self.wmm_transceiver_map = {
            tc.identifier: tc
            for tc in self.wmm_transceivers
        }

    def setup_test(self):
        for tc in self.wmm_transceivers:
            if tc.wlan_device:
                tc.wlan_device.wifi_toggle_state(True)
                tc.wlan_device.disconnect()
            if tc.access_point:
                tc.access_point.stop_all_aps()

    def teardown_test(self):
        for tc in self.wmm_transceivers:
            tc.cleanup_asynchronous_streams()
            if tc.wlan_device:
                tc.wlan_device.disconnect()
                tc.wlan_device.reset_wifi()
            if tc.access_point:
                self.download_ap_logs()
                tc.access_point.stop_all_aps()

    def teardown_class(self):
        for tc in self.wmm_transceivers:
            tc.destroy_resources()
        super().teardown_class()

    def on_fail(self, test_name, begin_time):
        for wlan_device in self.wlan_devices:
            super().on_device_fail(wlan_device.device, test_name, begin_time)

    def start_ap_with_wmm_params(self, ap_parameters, wmm_parameters):
        """Sets up WMM network on AP.

        Args:
            ap_parameters: a dictionary of kwargs to set up on ap
            wmm_parameters: a dictionary of wmm_params to set up on ap

        Returns:
            String, subnet of the network setup (e.g. '192.168.1.0/24')
        """
        # Defaults for required parameters
        ap_parameters['force_wmm'] = True
        if 'ssid' not in ap_parameters:
            ap_parameters['ssid'] = utils.rand_ascii_str(
                hostapd_constants.AP_SSID_LENGTH_2G)

        if 'profile_name' not in ap_parameters:
            ap_parameters['profile_name'] = 'whirlwind'

        if 'channel' not in ap_parameters:
            ap_parameters['channel'] = 6

        if 'n_capabilities' not in ap_parameters:
            ap_parameters['n_capabilities'] = DEFAULT_N_CAPABILITIES_20_MHZ

        if 'additional_ap_parameters' in ap_parameters:
            ap_parameters['additional_ap_parameters'].update(wmm_parameters)
        else:
            ap_parameters['additional_ap_parameters'] = wmm_parameters

        # Optional security
        security_config = ap_parameters.get('security_config', None)
        if security_config:
            ap_parameters['security'] = hostapd_security.Security(
                **security_config)
            ap_parameters.pop('security_config')

        # Start AP with kwargs
        self.log.info('Setting up WMM network: %s' % ap_parameters['ssid'])
        setup_ap(self.access_point_transceiver.access_point, **ap_parameters)
        self.log.info('Network (%s) is up.' % ap_parameters['ssid'])

        # Return subnet
        if ap_parameters['channel'] < hostapd_constants.LOWEST_5G_CHANNEL:
            return self.access_point_transceiver.access_point._AP_2G_SUBNET_STR
        else:
            return self.access_point_transceiver.access_point._AP_5G_SUBNET_STR

    def associate_transceiver(self, wmm_transceiver, ap_params):
        """Associates a WmmTransceiver that has a wlan_device.

        Args:
            wmm_transceiver: transceiver to associate
            ap_params: dict, contains ssid and password, if any, for network
        """
        if not wmm_transceiver.wlan_device:
            raise AttributeError(
                'Cannot associate a WmmTransceiver that does not have a '
                'WlanDevice.')
        ssid = ap_params['ssid']
        password = None
        target_security = None
        security = ap_params.get('security')
        if security:
            password = security.password
            target_security = hostapd_constants.SECURITY_STRING_TO_DEFAULT_TARGET_SECURITY.get(
                security.security_mode_string)
        associated = wmm_transceiver.wlan_device.associate(
            target_ssid=ssid,
            target_pwd=password,
            target_security=target_security)
        if not associated:
            raise ConnectionError('Failed to associate WmmTransceiver %s.' %
                                  wmm_transceiver.identifier)
        self.log.info('WmmTransceiver %s associated.' %
                      wmm_transceiver.identifier)

    def validate_streams_in_phase(self, phase_id, phases, max_bw):
        """Validates any stream in a phase that has validation criteria.

        Args:
            phase_id: identifier of the phase to check
            phases: dictionary containing phases for retrieving stream
                transmitters, expected bandwidths, etc.
            max_bw: the max link bandwidth, measured in the test

        Returns:
            True, if ALL validation criteria for ALL streams in phase pass
            False, otherwise
        """
        pass_val = True
        for stream_id, stream in phases[phase_id].items():
            if 'validation' in stream:
                transmitter = stream['transmitter']
                uuid = stream['uuid']
                actual_bw = transmitter.get_results(uuid).avg_rate
                if not actual_bw:
                    raise ConnectionError(
                        '(Phase: %s, Stream: %s) - Stream results show '
                        'bandwidth: None' % (phase_id, stream_id))
                for check in stream['validation']:
                    operator_str = check['operator']
                    rel_tolerance = check.get('rel_tolerance', 0)
                    abs_tolerance = check.get('abs_tolerance', 0)
                    max_bw_rel_tolerance = check.get('max_bw_rel_tolerance', 0)
                    expected_bw_percentage = check.get('bandwidth_percentage',
                                                       DEFAULT_BW_PERCENTAGE)
                    # Explicit Bandwidth Validation
                    if 'bandwidth' in check:
                        comp_bw = check['bandwidth']
                        log_msg = (
                            'Expected Bandwidth: %s (explicit validation '
                            'bandwidth [%s] x expected bandwidth '
                            'percentage [%s])' %
                            (expected_bw_percentage * comp_bw, comp_bw,
                             expected_bw_percentage))

                    # Stream Comparison Validation
                    elif 'phase' in check and 'stream' in check:
                        comp_phase_id = check['phase']
                        comp_stream_id = check['stream']
                        comp_stream = phases[comp_phase_id][comp_stream_id]
                        comp_transmitter = comp_stream['transmitter']
                        comp_uuid = comp_stream['uuid']
                        comp_bw = comp_transmitter.get_results(
                            comp_uuid).avg_rate
                        log_msg = (
                            'Expected Bandwidth: %s (bandwidth for phase: %s, '
                            'stream: %s [%s] x expected bandwidth percentage '
                            '[%s])' %
                            (expected_bw_percentage * comp_bw, comp_phase_id,
                             comp_stream_id, comp_bw, expected_bw_percentage))

                    # Expected Bandwidth Validation
                    else:
                        if 'bandwidth' in stream:
                            comp_bw = stream['bandwidth']
                            log_msg = (
                                'Expected Bandwidth: %s (expected stream '
                                'bandwidth [%s] x expected bandwidth '
                                'percentage [%s])' %
                                (expected_bw_percentage * comp_bw, comp_bw,
                                 expected_bw_percentage))
                        else:
                            max_bw_percentage = stream.get(
                                'max_bandwidth_percentage',
                                DEFAULT_BW_PERCENTAGE)
                            comp_bw = max_bw * max_bw_percentage
                            log_msg = (
                                'Expected Bandwidth: %s (max bandwidth [%s] x '
                                'stream bandwidth percentage [%s] x expected '
                                'bandwidth percentage [%s])' %
                                (expected_bw_percentage * comp_bw, max_bw,
                                 max_bw_percentage, expected_bw_percentage))

                    self.log.info(
                        'Validation criteria - Stream: %s, '
                        'Actual Bandwidth: %s, Operator: %s, %s, '
                        'Relative Tolerance: %s, Absolute Tolerance: %s, Max '
                        'Bandwidth Relative Tolerance: %s' %
                        (stream_id, actual_bw, operator_str, log_msg,
                         rel_tolerance, abs_tolerance, max_bw_rel_tolerance))

                    if eval_operator(
                            operator_str,
                            actual_bw,
                            comp_bw * expected_bw_percentage,
                            max_bw,
                            rel_tolerance=rel_tolerance,
                            abs_tolerance=abs_tolerance,
                            max_bw_rel_tolerance=max_bw_rel_tolerance):
                        self.log.info(
                            '(Phase: %s, Stream: %s) - PASSES validation check!'
                            % (phase_id, stream_id))
                    else:
                        self.log.info(
                            '(Phase: %s, Stream: %s) - Stream FAILS validation '
                            'check.' % (phase_id, stream_id))
                        pass_val = False
        if pass_val:
            self.log.info(
                '(Phase %s) - All streams\' validation criteria were met.' %
                phase_id)
            return True
        else:
            self.log.error(
                '(Phase %s) - At least one stream validation criterion was not '
                'met.' % phase_id)
            return False

    def graph_test(self, phases, max_bw):
        """ Outputs a bokeh html graph of the streams. Saves to ACTS log
        directory.

        Args:
            phases: dictionary containing phases for retrieving stream
                transmitters, expected bandwidths, etc.
            max_bw: the max link bandwidth, measured in the test

        """

        output_path = context.get_current_context().get_base_output_path()
        output_file_name = '%s/WlanWmmTest/%s.html' % (output_path,
                                                       self.test_name)
        output_file(output_file_name)

        start_time = 0
        graph_lines = []

        # Used for scaling
        highest_stream_bw = 0
        lowest_stream_bw = 100000

        for phase_id, phase in phases.items():
            longest_stream_time = 0
            for stream_id, stream in phase.items():
                transmitter = stream['transmitter']
                uuid = stream['uuid']

                if 'bandwidth' in stream:
                    stream_bw = "{:.3f}".format(stream['bandwidth'])
                    stream_bw_formula_str = '%sMb/s' % stream_bw
                elif 'max_bandwidth_percentage' in stream:
                    max_bw_percentage = stream['max_bandwidth_percentage']
                    stream_bw = "{:.3f}".format(max_bw * max_bw_percentage)
                    stream_bw_formula_str = '%sMb/s (%s%% of max bandwidth)' % (
                        stream_bw, str(max_bw_percentage * 100))
                else:
                    raise AttributeError(
                        'Stream %s must have either a bandwidth or '
                        'max_bandwidth_percentage parameter.' % stream_id)

                stream_time = stream.get('time', DEFAULT_STREAM_TIME)
                longest_stream_time = max(longest_stream_time, stream_time)

                avg_rate = transmitter.get_results(uuid).avg_rate

                instantaneous_rates = transmitter.get_results(
                    uuid).instantaneous_rates
                highest_stream_bw = max(highest_stream_bw,
                                        max(instantaneous_rates))
                lowest_stream_bw = min(lowest_stream_bw,
                                       min(instantaneous_rates))

                stream_data = ColumnDataSource(
                    dict(time=[
                        x for x in range(start_time, start_time + stream_time)
                    ],
                         instantaneous_bws=instantaneous_rates,
                         avg_bw=[avg_rate for _ in range(stream_time)],
                         stream_id=[stream_id for _ in range(stream_time)],
                         attempted_bw=[
                             stream_bw_formula_str for _ in range(stream_time)
                         ]))
                line = {
                    'x_axis': 'time',
                    'y_axis': 'instantaneous_bws',
                    'source': stream_data,
                    'line_width': GRAPH_DEFAULT_LINE_WIDTH,
                    'legend_label': '%s:%s' % (phase_id, stream_id)
                }
                graph_lines.append(line)

            start_time = start_time + longest_stream_time
        TOOLTIPS = [('Time', '@time'),
                    ('Attempted Bandwidth', '@attempted_bw'),
                    ('Instantaneous Bandwidth', '@instantaneous_bws'),
                    ('Stream Average Bandwidth', '@avg_bw'),
                    ('Stream', '@stream_id')]

        # Create and scale graph appropriately
        time_vs_bandwidth_graph = figure(
            title=('Bandwidth for %s' % self.test_name),
            x_axis_label='Time',
            y_axis_label='Bandwidth',
            tooltips=TOOLTIPS,
            y_range=(lowest_stream_bw -
                     (0.5 * (highest_stream_bw - lowest_stream_bw)),
                     1.05 * max_bw))
        time_vs_bandwidth_graph.sizing_mode = 'stretch_both'
        time_vs_bandwidth_graph.title.align = 'center'
        colors = Category10[GRAPH_COLOR_LEN]
        color_ind = 0

        # Draw max bandwidth line
        max_bw_span = Span(location=max_bw,
                           dimension='width',
                           line_color='black',
                           line_dash='dashed',
                           line_width=GRAPH_DEFAULT_LINE_WIDTH)
        max_bw_label = Label(x=(0.5 * start_time),
                             y=max_bw,
                             text=('Max Bandwidth: %sMb/s' % max_bw),
                             text_align='center')
        time_vs_bandwidth_graph.add_layout(max_bw_span)
        time_vs_bandwidth_graph.add_layout(max_bw_label)

        # Draw stream lines
        for line in graph_lines:
            time_vs_bandwidth_graph.line(line['x_axis'],
                                         line['y_axis'],
                                         source=line['source'],
                                         line_width=line['line_width'],
                                         legend_label=line['legend_label'],
                                         color=colors[color_ind])
            time_vs_bandwidth_graph.circle(line['x_axis'],
                                           line['y_axis'],
                                           source=line['source'],
                                           size=GRAPH_DEFAULT_CIRCLE_SIZE,
                                           legend_label=line['legend_label'],
                                           color=colors[color_ind])
            color_ind = (color_ind + 1) % GRAPH_COLOR_LEN
        time_vs_bandwidth_graph.legend.location = "top_left"
        time_vs_bandwidth_graph.legend.click_policy = "hide"
        graph_file = save([time_vs_bandwidth_graph])
        self.log.info('Saved graph to %s' % graph_file)

    def run_wmm_test(self,
                     phases,
                     ap_parameters=DEFAULT_AP_PARAMS,
                     wmm_parameters=hostapd_constants.
                     WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
                     stream_timeout=DEFAULT_STREAM_TIMEOUT):
        """Runs a WMM test case.

        Args:
            phases: dictionary of phases of streams to run in parallel,
                including any validation critera (see example below).
            ap_parameters: dictionary of custom kwargs to setup on AP (see
                start_ap_with_wmm_parameters)
            wmm_parameters: dictionary of WMM AC parameters
            stream_timeout: int, time in seconds to wait before force joining
                parallel streams

        Asserts:
            PASS, if all validation criteria for all phases are met
            FAIL, otherwise
        """
        # Setup AP
        subnet_str = self.start_ap_with_wmm_params(ap_parameters,
                                                   wmm_parameters)
        # Determine transmitters and receivers used in test case
        transmitters = set()
        receivers = set()
        for phase in phases.values():
            for stream in phase.values():
                transmitter = self.wmm_transceiver_map[
                    stream['transmitter_str']]
                transmitters.add(transmitter)
                stream['transmitter'] = transmitter
                receiver = self.wmm_transceiver_map[stream['receiver_str']]
                receivers.add(receiver)
                stream['receiver'] = receiver
        transceivers = transmitters.union(receivers)

        # Associate all transceivers with wlan_devices
        for tc in transceivers:
            if tc.wlan_device:
                self.associate_transceiver(tc, ap_parameters)

        # Determine link max bandwidth
        self.log.info('Determining link maximum bandwidth.')
        uuid = self.staut.run_synchronous_traffic_stream(
            {'receiver': self.access_point_transceiver}, subnet_str)
        max_bw = self.staut.get_results(uuid).avg_send_rate
        self.log.info('Link maximum bandwidth: %s Mb/s' % max_bw)

        # Run parallel phases
        pass_test = True
        for phase_id, phase in phases.items():
            self.log.info('Setting up phase: %s' % phase_id)

            for stream_id, stream in phase.items():

                transmitter = stream['transmitter']
                receiver = stream['receiver']
                access_category = stream.get('access_category', None)
                stream_time = stream.get('time', DEFAULT_STREAM_TIME)

                # Determine stream type
                if 'bandwidth' in stream:
                    bw = stream['bandwidth']
                elif 'max_bandwidth_percentage' in stream:
                    max_bw_percentage = stream['max_bandwidth_percentage']
                    bw = max_bw * max_bw_percentage
                else:
                    raise AttributeError(
                        'Stream %s must have either a bandwidth or '
                        'max_bandwidth_percentage parameter.' % stream_id)

                stream_params = {
                    'receiver': receiver,
                    'access_category': access_category,
                    'bandwidth': bw,
                    'time': stream_time
                }

                uuid = transmitter.prepare_asynchronous_stream(
                    stream_params, subnet_str)
                stream['uuid'] = uuid

            # Start all streams in phase
            start_time = time.time() + 5
            for transmitter in transmitters:
                transmitter.start_asynchronous_streams(start_time=start_time)

            # Wait for streams to join
            for transmitter in transmitters:
                end_time = time.time() + stream_timeout
                while transmitter.has_active_streams:
                    if time.time() > end_time:
                        raise ConnectionError(
                            'Transmitter\'s (%s) active streams are not finishing.'
                            % transmitter.identifier)
                    time.sleep(1)

            # Cleanup all streams
            for transmitter in transmitters:
                transmitter.cleanup_asynchronous_streams()

            # Validate streams
            pass_test = pass_test and self.validate_streams_in_phase(
                phase_id, phases, max_bw)

        self.graph_test(phases, max_bw)
        if pass_test:
            asserts.explicit_pass(
                'Validation criteria met for all streams in all phases.')
        else:
            asserts.fail(
                'At least one stream failed to meet validation criteria.')

# Test Cases

# Internal Traffic Differentiation

    def test_internal_traffic_diff_VO_VI(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_VO_VI)

    def test_internal_traffic_diff_VO_BE(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_VO_BE)

    def test_internal_traffic_diff_VO_BK(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_VO_BK)

    def test_internal_traffic_diff_VI_BE(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_VI_BE)

    def test_internal_traffic_diff_VI_BK(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_VI_BK)

    def test_internal_traffic_diff_BE_BK(self):
        self.run_wmm_test(wmm_test_cases.test_internal_traffic_diff_BE_BK)

# External Traffic Differentiation

    """Single station, STAUT transmits high priority"""

    def test_external_traffic_diff_staut_VO_ap_VI(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VO_ap_VI)

    def test_external_traffic_diff_staut_VO_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VO_ap_BE)

    def test_external_traffic_diff_staut_VO_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VO_ap_BK)

    def test_external_traffic_diff_staut_VI_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VI_ap_BE)

    def test_external_traffic_diff_staut_VI_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VI_ap_BK)

    def test_external_traffic_diff_staut_BE_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BE_ap_BK)

    """Single station, STAUT transmits low priority"""

    def test_external_traffic_diff_staut_VI_ap_VO(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_VI_ap_VO)

    def test_external_traffic_diff_staut_BE_ap_VO(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BE_ap_VO)

    def test_external_traffic_diff_staut_BK_ap_VO(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BK_ap_VO)

    def test_external_traffic_diff_staut_BE_ap_VI(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BE_ap_VI)

    def test_external_traffic_diff_staut_BK_ap_VI(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BK_ap_VI)

    def test_external_traffic_diff_staut_BK_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_external_traffic_diff_staut_BK_ap_BE)

# # Dual Internal/External Traffic Differentiation (Single station)

    def test_dual_traffic_diff_staut_VO_VI_ap_VI(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_VO_VI_ap_VI)

    def test_dual_traffic_diff_staut_VO_BE_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_VO_BE_ap_BE)

    def test_dual_traffic_diff_staut_VO_BK_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_VO_BK_ap_BK)

    def test_dual_traffic_diff_staut_VI_BE_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_VI_BE_ap_BE)

    def test_dual_traffic_diff_staut_VI_BK_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_VI_BK_ap_BK)

    def test_dual_traffic_diff_staut_BE_BK_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_dual_traffic_diff_staut_BE_BK_ap_BK)

# ACM Bit Conformance Tests (Single station, as WFA test below uses two)

    def test_acm_bit_on_VI(self):
        wmm_params_VI_ACM = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI)
        self.run_wmm_test(wmm_test_cases.test_acm_bit_on_VI,
                          wmm_parameters=wmm_params_VI_ACM)

# AC Parameter Modificiation Tests (Single station, as WFA test below uses two)

    def test_ac_param_degrade_VO(self):
        self.run_wmm_test(
            wmm_test_cases.test_ac_param_degrade_VO,
            wmm_parameters=hostapd_constants.WMM_DEGRADED_VO_PARAMS)

    def test_ac_param_degrade_VI(self):
        self.run_wmm_test(
            wmm_test_cases.test_ac_param_degrade_VI,
            wmm_parameters=hostapd_constants.WMM_DEGRADED_VI_PARAMS)

    def test_ac_param_improve_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_ac_param_improve_BE,
            wmm_parameters=hostapd_constants.WMM_IMPROVE_BE_PARAMS)

    def test_ac_param_improve_BK(self):
        self.run_wmm_test(
            wmm_test_cases.test_ac_param_improve_BK,
            wmm_parameters=hostapd_constants.WMM_IMPROVE_BK_PARAMS)


# WFA Test Plan Tests

    """Traffic Differentiation in Single BSS (Single Station)"""

    def test_wfa_traffic_diff_single_station_staut_BE_ap_VI_BE(self):
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_single_station_staut_BE_ap_VI_BE)

    def test_wfa_traffic_diff_single_station_staut_VI_BE(self):
        self.run_wmm_test(
            wmm_test_cases.test_wfa_traffic_diff_single_station_staut_VI_BE)

    def test_wfa_traffic_diff_single_station_staut_VI_BE_ap_BE(self):
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_single_station_staut_VI_BE_ap_BE)

    def test_wfa_traffic_diff_single_station_staut_BE_BK_ap_BK(self):
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_single_station_staut_BE_BK_ap_BK)

    def test_wfa_traffic_diff_single_station_staut_VO_VI_ap_VI(self):
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_single_station_staut_VO_VI_ap_VI)

    """Traffic Differentiation in Single BSS (Two Stations)"""

    def test_wfa_traffic_diff_two_stations_staut_BE_secondary_VI_BE(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_two_stations_staut_BE_secondary_VI_BE)

    def test_wfa_traffic_diff_two_stations_staut_VI_secondary_BE(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_two_stations_staut_VI_secondary_BE)

    def test_wfa_traffic_diff_two_stations_staut_BK_secondary_BE_BK(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_two_stations_staut_BK_secondary_BE_BK)

    def test_wfa_traffic_diff_two_stations_staut_VI_secondary_VO_VI(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        self.run_wmm_test(
            wmm_test_cases.
            test_wfa_traffic_diff_two_stations_staut_VI_secondary_VO_VI)

    """Test ACM Bit Conformance (Two Stations)"""

    def test_wfa_acm_bit_on_VI(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        wmm_params_VI_ACM = utils.merge_dicts(
            hostapd_constants.WMM_PHYS_11A_11G_11N_11AC_DEFAULT_PARAMS,
            hostapd_constants.WMM_ACM_VI)
        self.run_wmm_test(wmm_test_cases.test_wfa_acm_bit_on_VI,
                          wmm_parameters=wmm_params_VI_ACM)

    """Test the AC Parameter Modification"""

    def test_wfa_ac_param_degrade_VI(self):
        asserts.skip_if(not self.secondary_sta, 'No secondary station.')
        self.run_wmm_test(
            wmm_test_cases.test_wfa_ac_param_degrade_VI,
            wmm_parameters=hostapd_constants.WMM_DEGRADED_VI_PARAMS)
