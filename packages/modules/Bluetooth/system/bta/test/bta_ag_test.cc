/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <android-base/properties.h>
#include <android_bluetooth_flags.h>
#include <base/functional/bind.h>
#include <base/location.h>
#include <bluetooth/log.h>
#include <com_android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <chrono>
#include <iostream>
#include <memory>
#include <string>

#include "bta/ag/bta_ag_int.h"
#include "bta/include/bta_ag_swb_aptx.h"
#include "bta/include/bta_api.h"
#include "bta/include/bta_dm_api.h"
#include "bta/include/bta_hf_client_api.h"
#include "bta/include/bta_le_audio_api.h"
#include "common/message_loop_thread.h"
#include "os/system_properties.h"
#include "osi/include/compat.h"
#include "stack/btm/btm_int_types.h"
#include "stack/include/bt_device_type.h"
#include "stack/include/bt_name.h"
#include "stack/include/btm_status.h"
#include "test/common/main_handler.h"
#include "test/common/mock_functions.h"
#include "test/fake/fake_osi.h"
#include "test/mock/mock_bta_sys_main.h"
#include "test/mock/mock_device_esco_parameters.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_stack_acl.h"

#define TEST_BT com::android::bluetooth::flags

using namespace bluetooth;

namespace {

bool bta_ag_hdl_event(const BT_HDR_RIGID* p_msg) { return true; };
void BTA_AgDisable() { bta_sys_deregister(BTA_ID_AG); }

const tBTA_SYS_REG bta_ag_reg = {bta_ag_hdl_event, BTA_AgDisable};

}  // namespace

const std::string kBtCodecAptxVoiceEnabled =
    "bluetooth.hfp.codec_aptx_voice.enabled";

static bool enable_aptx_voice_property(bool enable) {
  const std::string value = enable ? "true" : "false";
  return android::base::SetProperty(kBtCodecAptxVoiceEnabled, value);
}

class BtaAgTest : public testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    fake_osi_ = std::make_unique<test::fake::FakeOsi>();

    main_thread_start_up();
    post_on_bt_main([]() { log::info("Main thread started up"); });

    bta_sys_register(BTA_ID_AG, &bta_ag_reg);

    bta_ag_cb.p_cback = [](tBTA_AG_EVT event, tBTA_AG* p_data) {};
    RawAddress::FromString("00:11:22:33:44:55", addr);
    test::mock::device_esco_parameters::esco_parameters_for_codec.body =
        [this](esco_codec_t codec) {
          this->codec = codec;
          return enh_esco_params_t{};
        };
  }
  void TearDown() override {
    test::mock::device_esco_parameters::esco_parameters_for_codec = {};
    bta_sys_deregister(BTA_ID_AG);
    post_on_bt_main([]() { log::info("Main thread shutting down"); });
    main_thread_shut_down();
  }

  std::unique_ptr<test::fake::FakeOsi> fake_osi_;
  const char test_strings[5][13] = {"0,4,6,7", "4,6,7", "test,0,4", "9,8,7",
                                    "4,6,7,test"};
  uint32_t tmp_num = 0xFFFF;
  RawAddress addr;
  esco_codec_t codec;
};

TEST_F_WITH_FLAGS(BtaAgTest, nop,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  bool status = true;
  ASSERT_EQ(true, status);
}

class BtaAgSwbTest : public BtaAgTest {
 protected:
  void SetUp() override { BtaAgTest::SetUp(); }
  void TearDown() override { BtaAgTest::TearDown(); }
};

TEST_F_WITH_FLAGS(BtaAgSwbTest, parse_qac_at_command,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_PEER_CODEC codec = bta_ag_parse_qac((char*)test_strings[0]);
  codec = bta_ag_parse_qac((char*)test_strings[0]);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q0_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q1_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q2_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q3_MASK);

  codec = bta_ag_parse_qac((char*)test_strings[1]);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q1_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q2_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q3_MASK);

  codec = bta_ag_parse_qac((char*)test_strings[2]);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q0_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q1_MASK);

  codec = bta_ag_parse_qac((char*)test_strings[3]);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q3_MASK);

  codec = bta_ag_parse_qac((char*)test_strings[4]);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q1_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q2_MASK);
  ASSERT_TRUE(codec & BTA_AG_SCO_APTX_SWB_SETTINGS_Q3_MASK);
}

class BtaAgActTest : public BtaAgTest {
 protected:
  void SetUp() override { BtaAgTest::SetUp(); }
  void TearDown() override { BtaAgTest::TearDown(); }
};

TEST_F_WITH_FLAGS(BtaAgActTest, set_codec_q0_success,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  const tBTA_AG_DATA data = {.api_setcodec.codec =
                                 BTA_AG_SCO_APTX_SWB_SETTINGS_Q0};

  bta_ag_cb.p_cback = [](tBTA_AG_EVT event, tBTA_AG* p_data) {
    tBTA_AG_VAL* val = (tBTA_AG_VAL*)p_data;
    ASSERT_EQ(val->num, BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
    ASSERT_EQ(val->hdr.status, BTA_AG_SUCCESS);
  };

  p_scb->peer_codecs = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0;
  p_scb->sco_codec = BTM_SCO_CODEC_NONE;
  p_scb->codec_updated = false;

  bta_ag_setcodec(p_scb, data);
  ASSERT_EQ(p_scb->sco_codec, BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
}

TEST_F_WITH_FLAGS(BtaAgActTest, set_codec_q1_fail_unsupported,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB* p_scb = &bta_ag_cb.scb[0];
  const tBTA_AG_DATA data = {.api_setcodec.codec =
                                 BTA_AG_SCO_APTX_SWB_SETTINGS_Q1};

  ASSERT_TRUE(enable_aptx_voice_property(true));

  bta_ag_cb.p_cback = [](tBTA_AG_EVT event, tBTA_AG* p_data) {
    tBTA_AG_VAL* val = (tBTA_AG_VAL*)p_data;
    ASSERT_EQ(val->num, BTA_AG_SCO_APTX_SWB_SETTINGS_Q1);
    ASSERT_EQ(val->hdr.status, BTA_AG_FAIL_RESOURCES);
  };

  p_scb->peer_codecs = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0;
  p_scb->sco_codec = BTM_SCO_CODEC_NONE;
  p_scb->codec_updated = false;

  bta_ag_setcodec(p_scb, data);
  ASSERT_TRUE(enable_aptx_voice_property(false));
}

class BtaAgCmdTest : public BtaAgTest {
 protected:
  void SetUp() override { BtaAgTest::SetUp(); }
  void TearDown() override { BtaAgTest::TearDown(); }
};

TEST_F_WITH_FLAGS(BtaAgCmdTest, check_flag_disabling_guarding_with_prop,
                  REQUIRES_FLAGS_DISABLED(ACONFIG_FLAG(TEST_BT,
                                                       hfp_codec_aptx_voice))) {
  ASSERT_FALSE(IS_FLAG_ENABLED(hfp_codec_aptx_voice));
  ASSERT_TRUE(enable_aptx_voice_property(false));
  ASSERT_FALSE(is_hfp_aptx_voice_enabled());

  ASSERT_TRUE(enable_aptx_voice_property(true));
  ASSERT_FALSE(is_hfp_aptx_voice_enabled());
}

TEST_F_WITH_FLAGS(BtaAgCmdTest, check_flag_guarding_with_prop,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  ASSERT_TRUE(IS_FLAG_ENABLED(hfp_codec_aptx_voice));
  ASSERT_TRUE(enable_aptx_voice_property(false));
  ASSERT_FALSE(is_hfp_aptx_voice_enabled());

  ASSERT_TRUE(enable_aptx_voice_property(true));
  ASSERT_TRUE(is_hfp_aptx_voice_enabled());
}

TEST_F_WITH_FLAGS(BtaAgCmdTest, at_hfp_cback__qac_ev_codec_disabled,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB p_scb = {
      .peer_addr = addr,
      .app_id = 0,
  };

  ASSERT_TRUE(enable_aptx_voice_property(false));

  bta_ag_at_hfp_cback(&p_scb, BTA_AG_AT_QAC_EVT, 0, (char*)&test_strings[0][0],
                      (char*)&test_strings[0][12],
                      BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
  ASSERT_FALSE(p_scb.codec_updated);
  ASSERT_FALSE(p_scb.is_aptx_swb_codec);
  ASSERT_EQ(1, get_func_call_count("PORT_WriteData"));
}

TEST_F_WITH_FLAGS(BtaAgCmdTest, at_hfp_cback__qac_ev_codec_enabled,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB p_scb = {.peer_addr = addr,
                       .app_id = 0,
                       .peer_codecs = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0_MASK};

  ASSERT_TRUE(enable_aptx_voice_property(true));

  bta_ag_at_hfp_cback(&p_scb, BTA_AG_AT_QAC_EVT, 0, (char*)&test_strings[0][0],
                      (char*)&test_strings[0][12],
                      BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
  ASSERT_TRUE(p_scb.codec_updated);
  ASSERT_TRUE(p_scb.is_aptx_swb_codec);
  ASSERT_EQ(2, get_func_call_count("PORT_WriteData"));
  ASSERT_EQ(p_scb.sco_codec, BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
  ASSERT_TRUE(enable_aptx_voice_property(false));
}

TEST_F_WITH_FLAGS(BtaAgCmdTest, at_hfp_cback__qcs_ev_codec_disabled,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB p_scb = {
      .peer_addr = addr,
      .app_id = 0,
  };

  ASSERT_TRUE(enable_aptx_voice_property(false));

  bta_ag_at_hfp_cback(&p_scb, BTA_AG_AT_QCS_EVT, 0, (char*)&test_strings[0][0],
                      (char*)&test_strings[0][12],
                      BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);
  ASSERT_FALSE(p_scb.codec_updated);
  ASSERT_FALSE(p_scb.is_aptx_swb_codec);
  ASSERT_EQ(1, get_func_call_count("PORT_WriteData"));
}

TEST_F_WITH_FLAGS(BtaAgCmdTest, at_hfp_cback__qcs_ev_codec_q0_enabled,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB p_scb = {.peer_addr = addr,
                       .sco_idx = BTM_INVALID_SCO_INDEX,
                       .app_id = 0,
                       .sco_codec = BTA_AG_SCO_APTX_SWB_SETTINGS_Q0,
                       .is_aptx_swb_codec = true};

  ASSERT_TRUE(enable_aptx_voice_property(true));

  bta_ag_cb.sco.state = BTA_AG_SCO_CODEC_ST;
  bta_ag_api_set_active_device(addr);
  ASSERT_EQ(addr, bta_ag_get_active_device());

  bta_ag_at_hfp_cback(&p_scb, BTA_AG_AT_QCS_EVT, 0, (char*)&test_strings[0][0],
                      (char*)&test_strings[0][12],
                      BTA_AG_SCO_APTX_SWB_SETTINGS_Q0);

  ASSERT_EQ(1, get_func_call_count("alarm_cancel"));
  ASSERT_EQ(2, get_func_call_count("esco_parameters_for_codec"));
  ASSERT_EQ(1, get_func_call_count("BTM_SetEScoMode"));
  ASSERT_EQ(1, get_func_call_count("BTM_CreateSco"));
  ASSERT_EQ(this->codec, ESCO_CODEC_SWB_Q0);
}

TEST_F_WITH_FLAGS(BtaAgCmdTest,
                  handle_swb_at_event__qcs_ev_codec_q1_fallback_to_q0,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      hfp_codec_aptx_voice))) {
  tBTA_AG_SCB p_scb = {.peer_addr = addr,
                       .sco_idx = BTM_INVALID_SCO_INDEX,
                       .app_id = 0,
                       .sco_codec = BTA_AG_SCO_APTX_SWB_SETTINGS_Q1,
                       .codec_fallback = false,
                       .is_aptx_swb_codec = true};

  ASSERT_TRUE(enable_aptx_voice_property(true));

  bta_ag_cb.sco.state = BTA_AG_SCO_CODEC_ST;
  bta_ag_api_set_active_device(addr);
  ASSERT_EQ(addr, bta_ag_get_active_device());

  bta_ag_at_hfp_cback(&p_scb, BTA_AG_AT_QCS_EVT, 0, (char*)&test_strings[0][0],
                      (char*)&test_strings[0][12],
                      BTA_AG_SCO_APTX_SWB_SETTINGS_Q1);

  ASSERT_EQ(1, get_func_call_count("alarm_cancel"));
  ASSERT_EQ(2, get_func_call_count("esco_parameters_for_codec"));
  ASSERT_EQ(1, get_func_call_count("BTM_SetEScoMode"));
  ASSERT_EQ(1, get_func_call_count("BTM_CreateSco"));
  ASSERT_EQ(this->codec, ESCO_CODEC_SWB_Q0);
  ASSERT_TRUE(enable_aptx_voice_property(false));
}
