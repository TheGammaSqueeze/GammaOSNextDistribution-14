#!/usr/bin/env python3
#
#   Copyright 2021 - The Android Open Source Project
#
##   Licensed under the Apache License, Version 2.0 (the 'License');
##   you may not use this file except in compliance with the License.
##   You may obtain a copy of the License at
##
##       http://www.apache.org/licenses/LICENSE-2.0
##
##   Unless required by applicable law or agreed to in writing, software
##   distributed under the License is distributed on an 'AS IS' BASIS,
##   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
##   See the License for the specific language governing permissions and
##   limitations under the License.

from acts_contrib.test_utils.gnss import LabTtffTestBase as lttb
from acts_contrib.test_utils.gnss.gnss_test_utils import launch_eecoexer
from acts_contrib.test_utils.gnss.gnss_test_utils import execute_eecoexer_function



class LabTtffGeneralCoexTest(lttb.LabTtffTestBase):
    """Lab stand alone GNSS general coex TTFF/FFPE test"""

    def setup_class(self):
        super().setup_class()
        req_params = ['coex_testcase_ls']
        self.unpack_userparams(req_param_names=req_params)
        self.test_cmd = ''
        self.stop_cmd = ''

    def setup_test(self):
        super().setup_test()
        launch_eecoexer(self.dut)
        # Set DUT temperature the limit to 60 degree
        self.dut.adb.shell(
            'setprop persist.com.google.eecoexer.cellular.temperature_limit 60')

    def teardown_test(self):
        super().teardown_test()
        self.exe_eecoexer_loop_cmd(self.stop_cmd)

    def gnss_ttff_ffpe_coex_base(self, mode):
        """
        TTFF and FFPE general coex base test function

            Args:
                mode: Set the TTFF mode for testing. Definitions are as below.
                cs(cold start), ws(warm start), hs(hot start)
        """
        # Loop all test case in coex_testcase_ls
        for i, test_item in enumerate(self.coex_testcase_ls):

            if i > 0:
                self.setup_test()

            # get test_log_path from coex_testcase_ls['test_name']
            test_log_path = test_item['test_name']

            # get test_cmd from coex_testcase_ls['test_cmd']
            self.test_cmd = test_item['test_cmd']

            # get stop_cmd from coex_testcase_ls['stop_cmd']
            self.stop_cmd = test_item['stop_cmd']

            # Start aggressor Tx by EEcoexer
            # self.exe_eecoexer_loop_cmd(test_cmd)

            # Start GNSS TTFF FFPE testing
            self.gnss_ttff_ffpe(mode, test_log_path, self.test_cmd, self.stop_cmd)

            # Stop aggressor Tx by EEcoexer
            # self.exe_eecoexer_loop_cmd(stop_cmd)

            # Clear GTW GPSTool log. Need to clean the log every round of the test.
            self.clear_gps_log()

            if i < len(self.coex_testcase_ls) - 1:
                self.teardown_test()

    def test_gnss_cold_ttff_ffpe_coex(self):
        """
        Cold start TTFF and FFPE GNSS general coex testing
        """
        self.gnss_ttff_ffpe_coex_base('cs')

    def test_gnss_warm_ttff_ffpe_coex(self):
        """
        Warm start TTFF and FFPE GNSS general coex testing
        """
        self.gnss_ttff_ffpe_coex_base('ws')

    def test_gnss_hot_ttff_ffpe_coex(self):
        """
        Hot start TTFF and FFPE GNSS general coex testing
        """
        self.gnss_ttff_ffpe_coex_base('hs')
