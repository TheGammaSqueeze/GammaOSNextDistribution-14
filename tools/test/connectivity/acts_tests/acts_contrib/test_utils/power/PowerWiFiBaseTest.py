#!/usr/bin/env python3.4
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

from retry import retry

import acts_contrib.test_utils.power.PowerBaseTest as PBT
from acts_contrib.test_utils.wifi import wifi_test_utils as wutils
from acts_contrib.test_utils.wifi import wifi_power_test_utils as wputils
from acts_contrib.test_utils.power import plot_utils

IPERF_DURATION = 'iperf_duration'
INITIAL_ATTEN = [0, 0, 90, 90]
IPERF_TAIL = 5


class PowerWiFiBaseTest(PBT.PowerBaseTest):
    """Base class for WiFi power related tests.

    Inherited from the PowerBaseTest class
    """
    def setup_class(self):

        super().setup_class()
        if hasattr(self, 'access_points'):
            self.access_point = self.access_points[0]
            self.access_point_main = self.access_points[0]
            if len(self.access_points) > 1:
                self.access_point_aux = self.access_points[1]
        if hasattr(self, 'attenuators'):
            self.set_attenuation(INITIAL_ATTEN)
        if hasattr(self, 'network_file'):
            self.networks = self.unpack_custom_file(self.network_file, False)
            self.main_network = self.networks['main_network']
            self.aux_network = self.networks['aux_network']
        if hasattr(self, 'packet_senders'):
            self.pkt_sender = self.packet_senders[0]
        if hasattr(self, 'iperf_servers'):
            self.iperf_server = self.iperf_servers[0]
        if self.iperf_duration:
            self.mon_duration = self.iperf_duration - self.mon_offset - IPERF_TAIL
            self.mon_info = self.create_monsoon_info()
        try:
          self._set_country_code()
        except Exception as e:
          self.log.warning('error in set country code with %s', e)
          country_code = self.dut.droid.wifiGetCountryCode()
          self.log.info('the country code is %s', country_code)

    @retry(tries=5, delay=10)
    def _set_country_code(self):
      wutils.wifi_toggle_state(self.dut, True)
      wutils.set_wifi_country_code(self.dut, 'US')

    def teardown_test(self):
        """Tear down necessary objects after test case is finished.

        Bring down the AP interface, delete the bridge interface, stop the
        packet sender, and reset the ethernet interface for the packet sender
        """
        super().teardown_test()
        if hasattr(self, 'pkt_sender'):
            self._safe_teardown('pkt_sender stop sending',
                                self.pkt_sender.stop_sending,
                                ignore_status=True)
        if hasattr(self, 'brconfigs'):
            self._safe_teardown('brconfigs', self.access_point.bridge.teardown,
                                self.brconfigs)
            delattr(self, 'brconfigs')
        if hasattr(self, 'brconfigs_main'):
            self._safe_teardown('brconfigs_main',
                                self.access_point_main.bridge.teardown,
                                self.brconfigs_main)
            delattr(self, 'brconfigs_main')
        if hasattr(self, 'brconfigs_aux'):
            self._safe_teardown('brconfigs_aux',
                                self.access_point_aux.bridge.teardown,
                                self.brconfigs_aux)
            delattr(self, 'brconfigs_aux')
        if hasattr(self, 'access_points'):
            for ap in self.access_points:
                self._safe_teardown('access point {}'.format(ap.identifier),
                                    ap.close)
        if hasattr(self, 'pkt_sender'):
            self._safe_teardown('pkt_sender reset host interface',
                                wputils.reset_host_interface,
                                self.pkt_sender.interface)
        if hasattr(self, 'iperf_server'):
            self._safe_teardown('iperf_server', self.iperf_server.stop);

    def _safe_teardown(self, attr, teardown_method, *arg, **kwargs):
        """Teardown the object with try block.

        Adds a try block for each teardown step to make sure that each
        teardown step is executed.

        Args:
            attr: the teardown attribute description for logging
            teardown_method: the method for teardown
            *arg: positional arguments for teardown_method
            **kwargs: keyword arguments for teardown_method
        """
        try:
            self.log.info('teardown %s with %s', attr, teardown_method.__name__)
            teardown_method(*arg, **kwargs)
        except Exception as e:
            self.log.warning('teardown of %s fails with %s', attr, e)

    def teardown_class(self):
        """Clean up the test class after tests finish running

        """
        super().teardown_class()
        if hasattr(self, 'access_points'):
            for ap in self.access_points:
                ap.close()

    def setup_ap_connection(self,
                            network,
                            bandwidth=80,
                            connect=True,
                            ap=None,
                            dtim_period=None):
        """Setup AP and connect DUT to it.

        Args:
            network: the network config for the AP to be setup
            bandwidth: bandwidth of the WiFi network to be setup
            connect: indicator of if connect dut to the network after setup
            ap: access point object, default is None to find the main AP
            dtim_period: the dtim period of access point
        Returns:
            self.brconfigs: dict for bridge interface configs
        """
        wutils.wifi_toggle_state(self.dut, True)
        if not dtim_period:
            dtim_period = self.ap_dtim_period
        if not ap:
            if hasattr(self, 'access_points'):
                self.brconfigs = wputils.ap_setup(
                    self.access_point,
                    network,
                    bandwidth=bandwidth,
                    dtim_period=dtim_period)
        else:
            self.brconfigs = wputils.ap_setup(
                ap, network, bandwidth=bandwidth, dtim_period=dtim_period)
        if connect:
            wutils.wifi_connect(self.dut, network, num_of_tries=3)

        if ap or (not ap and hasattr(self, 'access_points')):
            return self.brconfigs

    def collect_power_data(self):
        """Measure power, plot and check pass/fail.

        If IPERF is run, need to pull iperf results and attach it to the plot.
        """
        samples = super().collect_power_data()
        tag = ''
        if self.iperf_duration:
            throughput = self.process_iperf_results()
            plot_title = ('{0}_{1}_{2}_RSSI_{3:d}dBm_Throughput_{4:.2f}'
                          'Mbps'.format(self.test_name,
                                        self.dut.model,
                                        self.dut.build_info['build_id'],
                                        self.RSSI,
                                        throughput))
            plot_utils.current_waveform_plot(samples, self.mon_voltage,
                                             self.mon_info.data_path,
                                             plot_title)
        return samples

    def setup_test(self):
        """Set up test specific parameters or configs.

        """
        super().setup_test()

        wutils.reset_wifi(self.dut)
        wutils.wifi_toggle_state(self.dut, False)
