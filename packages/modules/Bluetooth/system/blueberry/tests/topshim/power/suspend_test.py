#!/usr/bin/env python3
#
#   Copyright 2022 - The Android Open Source Project
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

from blueberry.tests.gd.cert.truth import assertThat
from blueberry.tests.topshim.lib.topshim_base_test import TopshimBaseTest
from blueberry.tests.topshim.lib.adapter_client import AdapterClient

from mobly import test_runner


class SuspendTest(TopshimBaseTest):

    def __verify_no_wake_suspend(self):
        # Start suspend work
        self.dut().clear_event_mask()
        self.dut().clear_event_filter()
        self.dut().clear_filter_accept_list()
        self.dut().stop_advertising()
        self.dut().stop_scanning()
        self.dut().disconnect_all_acls()
        self.dut().le_rand()

    def __verify_no_wake_resume(self):
        # Start resume work
        self.dut().set_default_event_mask_except(0, 0)
        self.dut().set_event_filter_inquiry_result_all_devices()
        self.dut().set_event_filter_connection_setup_all_devices()
        self.dut().le_rand()

    def __verify_wakeful_suspend(self, is_a2dp_connected):
        self.dut().clear_event_mask()
        self.dut().clear_event_filter()
        self.dut().clear_filter_accept_list()
        self.dut().stop_advertising()
        self.dut().stop_scanning()
        if is_a2dp_connected:
            # self.media_server.disconnect_a2dp()
            pass
        self.dut().disconnect_all_acls()
        self.dut().allow_wake_by_hid()
        self.dut().le_rand()

    def __verify_wakeful_resume(self, was_a2dp_connected):
        # Start resume work
        self.dut().set_default_event_mask_except(0, 0)
        self.dut().set_event_filter_inquiry_result_all_devices()
        self.dut().set_event_filter_connection_setup_all_devices()
        if was_a2dp_connected:
            # restore filter accept list?
            self.dut().restore_filter_accept_list()
            # reconnect a2dp
            # self.media_server.reconnect_last_a2dp()
            # self.gatt.restart_all_previous_advertising()
        self.dut().start_advertising()
        self.dut().le_rand()

    def test_no_wake_suspend(self):
        self.__verify_no_wake_suspend()

    def test_no_wake_resume(self):
        self.__verify_no_wake_resume()

    def test_no_wake_suspend_then_resume(self):
        self.__verify_no_wake_suspend()
        self.__verify_no_wake_resume()

    def test_no_wake_suspend_then_resume_then_suspend(self):
        self.__verify_no_wake_suspend()
        self.__verify_no_wake_resume()
        self.__verify_no_wake_suspend()

    def test_wakeful_suspend_no_a2dp(self):
        self.__verify_wakeful_suspend(False)

    def test_wakeful_resume_no_a2dp(self):
        self.__verify_wakeful_resume(False)

    def test_wakeful_suspend_then_resume_no_a2dp(self):
        self.__verify_wakeful_suspend(False)
        self.__verify_wakeful_resume(False)

    def test_wakeful_suspend_then_resume_then_suspend_no_a2dp(self):
        self.__verify_wakeful_suspend(False)
        self.__verify_wakeful_resume(False)
        self.__verify_wakeful_suspend(False)


if __name__ == "__main__":
    test_runner.main()
