#!/usr/bin/env python3
#
#   Copyright 2020 - Google
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

from acts import signals
from acts.test_decorators import test_tracker_info
from acts_contrib.test_utils.tel.loggers.telephony_metric_logger import TelephonyMetricLogger
from acts_contrib.test_utils.tel.TelephonyBaseTest import TelephonyBaseTest
from acts_contrib.test_utils.tel.tel_defines import CAPABILITY_CONFERENCE
from acts_contrib.test_utils.tel.tel_dsds_utils import erase_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_call_forwarding
from acts_contrib.test_utils.tel.tel_dsds_utils import msim_call_voice_conf
from acts_contrib.test_utils.tel.tel_phone_setup_utils import ensure_phones_idle
from acts_contrib.test_utils.tel.tel_ss_utils import set_call_waiting
from acts_contrib.test_utils.tel.tel_subscription_utils import get_outgoing_voice_sub_id
from acts_contrib.test_utils.tel.tel_test_utils import get_capability_for_subscription


class TelLiveGFTDSDSSupplementaryServiceTest(TelephonyBaseTest):
    def setup_class(self):
        TelephonyBaseTest.setup_class(self)
        self.message_lengths = (50, 160, 180)
        self.tel_logger = TelephonyMetricLogger.for_test_case()
        erase_call_forwarding(self.log, self.android_devices[0])
        if not get_capability_for_subscription(
            self.android_devices[0],
            CAPABILITY_CONFERENCE,
            get_outgoing_voice_sub_id(self.android_devices[0])):
            self.android_devices[0].log.error(
                "Conference call is not supported, abort test.")
            raise signals.TestAbortClass(
                "Conference call is not supported, abort test.")

    def teardown_test(self):
        ensure_phones_idle(self.log, self.android_devices)
        erase_call_forwarding(self.log, self.android_devices[0])
        set_call_waiting(self.log, self.android_devices[0], enable=1)

    @test_tracker_info(uuid="ccaeff83-4b8c-488a-8c7f-6bb019528bf8")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_psim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            callee_rat=["volte", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="a132bfa6-d545-4970-9a39-55aea7477f8c")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_psim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            callee_rat=["volte", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="71a4db8a-d20f-4fcb-ac5f-5fe6b9fa36f5")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_esim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            callee_rat=["volte", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="50b064e7-4bf6-4bb3-aed1-e4d78b0b6195")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_esim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            callee_rat=["volte", "volte"],
            call_forwarding_type="unconditional")



    @test_tracker_info(uuid="b1cfe07f-f4bf-49c4-95f1-f0973f32940e")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_csfb_psim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            callee_rat=["volte", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="668bd2c6-beee-4c38-a9e5-8b0cc5937c28")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_csfb_psim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            callee_rat=["volte", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="d69e86f3-f279-4cc8-8c1f-8a9dce0acfdf")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_csfb_esim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            callee_rat=["volte", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="6156c374-7b07-473b-84f7-45de633f9681")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_volte_csfb_esim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            callee_rat=["volte", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="29e36a21-9c94-418b-8628-e601e56fb168")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_volte_psim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            callee_rat=["csfb", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="36ebf549-e64e-4093-bebf-c9ca56289477")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_volte_psim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            callee_rat=["csfb", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="cfb973d7-aa3b-4e59-9f00-501e42c99947")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_volte_esim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            callee_rat=["csfb", "volte"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="a347c3db-e128-4deb-9009-c8b8e8145f67")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_volte_esim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            callee_rat=["csfb", "volte"],
            call_forwarding_type="unconditional")



    @test_tracker_info(uuid="7040e929-eb1d-4dc6-a404-2c185dc8a0a0")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_psim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            0,
            callee_rat=["csfb", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="b88a2ce3-74c7-41df-8114-71b6c3d0b050")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_psim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            0,
            None,
            1,
            callee_rat=["csfb", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="0ffd2391-ec5a-4a48-b0a8-fceba0c922d3")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_esim_dds_slot_0(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            0,
            callee_rat=["csfb", "csfb"],
            call_forwarding_type="unconditional")

    @test_tracker_info(uuid="44937439-2d0a-4aea-bb4d-263e5ed634b4")
    @TelephonyBaseTest.tel_test_wrap
    def test_msim_call_forwarding_unconditional_csfb_esim_dds_slot_1(self):
        return msim_call_forwarding(
            self.log,
            self.tel_logger,
            self.android_devices,
            None,
            1,
            None,
            1,
            callee_rat=["csfb", "csfb"],
            call_forwarding_type="unconditional")

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="73ac948b-5260-44f1-a0a6-e4a410cb3283")
    def test_msim_voice_conf_call_host_volte_psim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="75d7fb2c-aa62-4b4f-9e70-8f6b1647f816")
    def test_msim_voice_conf_call_host_volte_psim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="2343369e-0240-4adc-bc01-7c08f9327737")
    def test_msim_voice_conf_call_host_volte_esim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3a28e621-1d47-432c-a7e8-20d2d9f82588")
    def test_msim_voice_conf_call_host_volte_esim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["volte", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="378f24cf-bb96-45e1-8150-02f08d7417b6")
    def test_msim_voice_conf_call_host_volte_csfb_psim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["volte", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="e3fdf5ec-eafe-4825-acd3-5d4ff03df1d2")
    def test_msim_voice_conf_call_host_volte_csfb_psim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["volte", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="221da988-e8c7-43e5-ae3a-414e8f01e872")
    def test_msim_voice_conf_call_host_volte_csfb_esim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["volte", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="ea5f0254-59b8-4f63-8a4a-6f0ecb55ddbf")
    def test_msim_voice_conf_call_host_volte_csfb_esim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["volte", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="90abbc8a-d492-45f9-9919-fae7e44c877a")
    def test_msim_voice_conf_call_host_csfb_volte_psim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["csfb", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="da98268a-a94a-4fc7-8fb9-8e8573baed50")
    def test_msim_voice_conf_call_host_csfb_volte_psim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["csfb", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="df46bcf5-48a3-466f-ba37-9519f5a671cf")
    def test_msim_voice_conf_call_host_csfb_volte_esim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["csfb", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="f0c82ae0-c659-45e3-9a00-419e2da55739")
    def test_msim_voice_conf_call_host_csfb_volte_esim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["csfb", "volte"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4831c07a-9a38-4ccd-8fa0-beaf52a2751e")
    def test_msim_voice_conf_call_host_csfb_psim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 0, host_rat=["csfb", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="79cbf768-88ea-4d03-b798-2097789ee456")
    def test_msim_voice_conf_call_host_csfb_psim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0, None, None, 1, host_rat=["csfb", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="68b0a15f-62e4-419d-948a-d74d763a736c")
    def test_msim_voice_conf_call_host_csfb_esim_dds_slot_0(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 0, host_rat=["csfb", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="a93af289-98a8-4d4b-bdbd-54478f273fea")
    def test_msim_voice_conf_call_host_csfb_esim_dds_slot_1(self):
        return msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1, None, None, 1, host_rat=["csfb", "csfb"])

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="43e450c8-8a0b-4dfc-8c59-d0865c4c6399")
    def test_msim_call_waiting_volte_psim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "volte"],
            merge=False, disable_cw=False):
        	result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=True):
        	result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="7d05525e-8fcf-4630-9248-22803a14209d")
    def test_msim_call_waiting_volte_psim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="caec880c-948a-4fcd-b57e-e64fd3048b08")
    def test_msim_call_waiting_volte_esim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="72ec685d-6c36-40cd-81fd-dd97e32b1e48")
    def test_msim_call_waiting_volte_esim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="3cef5c80-b15f-45fa-8376-5252e61d7849")
    def test_msim_call_waiting_volte_csfb_psim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="5da5c799-5349-4cf3-b683-c7372aadfdfa")
    def test_msim_call_waiting_volte_csfb_psim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="30c06bb3-a62f-4dba-90c2-1b00c515034a")
    def test_msim_call_waiting_volte_csfb_esim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d2b0fdb1-5ea6-4958-a34f-6f701801e3c9")
    def test_msim_call_waiting_volte_csfb_esim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["volte", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="b239d4be-9a36-4791-84df-ecebae645c84")
    def test_msim_call_waiting_csfb_volte_psim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="51a368e6-83d8-46af-8a85-56aaed787f9f")
    def test_msim_call_waiting_csfb_volte_psim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="73646014-1ead-4bd9-bd8f-2c21da3d596a")
    def test_msim_call_waiting_csfb_volte_esim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="0d520b78-20b8-4be7-833a-40179114cbce")
    def test_msim_call_waiting_csfb_volte_esim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["csfb", "volte"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="0544abec-7a59-4de0-be45-0b9b9d706b17")
    def test_msim_call_waiting_csfb_psim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            0,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="4329319b-0503-4c51-8792-2f36090b8071")
    def test_msim_call_waiting_csfb_psim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            0,
            None,
            None,
            1,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="d612ce5c-b4cd-490c-bc6c-7f67c25264aa")
    def test_msim_call_waiting_csfb_esim_dds_slot_0(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            0,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result

    @TelephonyBaseTest.tel_test_wrap
    @test_tracker_info(uuid="fb4869da-a346-4275-a742-d2c653bfc39a")
    def test_msim_call_waiting_csfb_esim_dds_slot_1(self):
        result = True
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=False):
            result = False
        if not msim_call_voice_conf(
            self.log,
            self.tel_logger,
            self.android_devices,
            1,
            None,
            None,
            1,
            host_rat=["csfb", "csfb"],
            merge=False,
            disable_cw=True):
            result = False
        return result