#!/usr/bin/env python3.4
#
#   Copyright 2022 - Google
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


import time
from acts import asserts
from acts import signals
from acts.test_decorators import test_tracker_info
from acts.libs.utils.multithread import multithread_func
from acts.utils import get_current_epoch_time
from acts_contrib.test_utils.net import ui_utils as uutils
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts.controllers.android_lib.errors import AndroidDeviceError
from acts_contrib.test_utils.tel.tel_logging_utils import log_screen_shot
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_log
from acts_contrib.test_utils.tel.tel_logging_utils import get_screen_shot_logs
from acts_contrib.test_utils.tel.tel_rcs_utils import go_to_message_app
from acts_contrib.test_utils.tel.tel_rcs_utils import go_to_rcs_settings
from acts_contrib.test_utils.tel.tel_rcs_utils import is_rcs_enabled
from acts_contrib.test_utils.tel.tel_rcs_utils import enable_chat_feature
from acts_contrib.test_utils.tel.tel_rcs_utils import disable_chat_feature
from acts_contrib.test_utils.tel.tel_rcs_utils import is_rcs_connected



class TelLiveGFTRcsTest(TelephonyBaseTest):
    def setup_class(self):
        super().setup_class()
        self.my_error_msg = ""

    def setup_test(self):
        TelephonyBaseTest.setup_test(self)
        for ad in self.android_devices:
            ad.send_keycode("HOME")

    def teardown_test(self):
        TelephonyBaseTest.teardown_test(self)
        get_screen_shot_logs(self.android_devices)

    def teardown_class(self):
        TelephonyBaseTest.teardown_class(self)


    def test_is_single_reg_capable(self):
        """ Tests single registration provisioning.

        """
        for ad in self.android_devices:
            isRcsVolteSingleRegistrationCapable = ad.droid.isRcsVolteSingleRegistrationCapable()
            ad.log.info("isRcsVolteSingleRegistrationCapable: %r",
                isRcsVolteSingleRegistrationCapable)

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="eb68fdc6-b070-4ba2-92f4-3dd8aca78a2b")
    def test_rcs_enable(self):
        """1.1.1 - First time Registration over Cellular - Successful
        """
        tasks = [(enable_chat_feature, (ad,)) for ad in self.android_devices]
        if not multithread_func(self.log, tasks):
            raise signals.TestFailure("enable_chat_feature failure: %s"
                %(self.my_error_msg))

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="176a0230-c35d-454d-a1f7-c706f71c5dbd")
    def test_rcs_message(self):
        """Sends rcs message

        Returns:
            True if pass; False if fail
        """
        try:
            for ad in self.android_devices:
                enable_chat_feature(ad)
                # Go to message
                go_to_message_app(ad)
                time.sleep(2)
                if uutils.has_element(ad, text="Start chat"):
                    uutils.wait_and_click(ad, text="Start chat")
                    time.sleep(2)
                    log_screen_shot(ad, "click_start_chat")
                # input MT phone number
                uutils.wait_and_input_text(ad, input_text=ad.mt_phone_number)
                time.sleep(2)
                self.log.info("input mt phone number")
                log_screen_shot(ad, "input_message_phone_num")
                self.log.info("select receiver")
                # com.google.android.apps.messaging:id/contact_picker_create_group
                uutils.wait_and_click(ad, resource_id="com.google.android.apps."
                    "messaging:id/contact_picker_create_group")
                time.sleep(2)
                log_screen_shot(ad, "message_select_receiver")
                # input chat message
                uutils.wait_and_input_text(ad, input_text="RCS test")
                self.log.info("input rcs message")
                time.sleep(2)
                log_screen_shot(ad, "message_input_rcs")
                self.log.info("click send message button")
                uutils.wait_and_click(ad, resource_id="com.google.android.apps."
                    "messaging:id/send_message_button_icon")
                time.sleep(2)
                log_screen_shot(ad, "message_click_send")
                is_rcs_connected(ad)
            return True
        except AndroidDeviceError:
            return False


