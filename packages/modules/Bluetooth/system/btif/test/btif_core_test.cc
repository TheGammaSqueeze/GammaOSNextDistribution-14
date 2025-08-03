/*
 * Copyright 2021 The Android Open Source Project
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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <future>
#include <map>

#include "bta/include/bta_ag_api.h"
#include "bta/include/bta_av_api.h"
#include "bta/include/bta_hd_api.h"
#include "bta/include/bta_hf_client_api.h"
#include "bta/include/bta_hh_api.h"
#include "btcore/include/module.h"
#include "btif/include/btif_api.h"
#include "btif/include/btif_common.h"
#include "btif/include/btif_util.h"
#include "hci/controller_interface_mock.h"
#include "include/hardware/bluetooth.h"
#include "include/hardware/bt_av.h"
#include "test/common/core_interface.h"
#include "test/mock/mock_main_shim_entry.h"
#include "test/mock/mock_stack_btm_sec.h"
#include "types/raw_address.h"

void set_hal_cbacks(bt_callbacks_t* callbacks);

const tBTA_AG_RES_DATA tBTA_AG_RES_DATA::kEmpty = {};

using testing::Return;
module_t bt_utils_module;
module_t gd_controller_module;
module_t gd_shim_module;
module_t osi_module;
module_t rust_module;

namespace {

const RawAddress kRawAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});

auto timeout_time = std::chrono::seconds(3);

std::map<std::string, std::function<void()>> callback_map_;
#define TESTCB                                             \
  if (callback_map_.find(__func__) != callback_map_.end()) \
    callback_map_[__func__]();

void adapter_state_changed_callback(bt_state_t /* state */) {}
void adapter_properties_callback(bt_status_t /* status */,
                                 int /* num_properties */,
                                 bt_property_t* /* properties */) {}
void remote_device_properties_callback(bt_status_t /* status */,
                                       RawAddress* /* bd_addr */,
                                       int /* num_properties */,
                                       bt_property_t* /* properties */) {}
void device_found_callback(int /* num_properties */,
                           bt_property_t* /* properties */) {}
void discovery_state_changed_callback(bt_discovery_state_t /* state */) {}
void pin_request_callback(RawAddress* /* remote_bd_addr */,
                          bt_bdname_t* /* bd_name */, uint32_t /* cod */,
                          bool /* min_16_digit */) {}
void ssp_request_callback(RawAddress* /* remote_bd_addr */,
                          bt_bdname_t* /* bd_name */, uint32_t /* cod */,
                          bt_ssp_variant_t /* pairing_variant */,
                          uint32_t /* pass_key */) {}
void bond_state_changed_callback(bt_status_t /* status */,
                                 RawAddress* /* remote_bd_addr */,
                                 bt_bond_state_t /* state */,
                                 int /* fail_reason */) {}
void address_consolidate_callback(RawAddress* /* main_bd_addr */,
                                  RawAddress* /* secondary_bd_addr */) {}
void le_address_associate_callback(RawAddress* /* main_bd_addr */,
                                   RawAddress* /* secondary_bd_addr */) {}
void acl_state_changed_callback(bt_status_t /* status */,
                                RawAddress* /* remote_bd_addr */,
                                bt_acl_state_t /* state */,
                                int /* transport_link_type */,
                                bt_hci_error_code_t /* hci_reason */,
                                bt_conn_direction_t /* direction */,
                                uint16_t /* acl_handle */) {}
void link_quality_report_callback(uint64_t /* timestamp */, int /* report_id */,
                                  int /* rssi */, int /* snr */,
                                  int /* retransmission_count */,
                                  int /* packets_not_receive_count */,
                                  int /* negative_acknowledgement_count */) {}
void callback_thread_event(bt_cb_thread_evt /* evt */) { TESTCB; }
void dut_mode_recv_callback(uint16_t /* opcode */, uint8_t* /* buf */,
                            uint8_t /* len */) {}
void le_test_mode_callback(bt_status_t /* status */,
                           uint16_t /* num_packets */) {}
void energy_info_callback(bt_activity_energy_info* /* energy_info */,
                          bt_uid_traffic_t* /* uid_data */) {}
void generate_local_oob_data_callback(tBT_TRANSPORT /* transport */,
                                      bt_oob_data_t /* oob_data */) {}
void switch_buffer_size_callback(bool /* is_low_latency_buffer_size */) {}
void switch_codec_callback(bool /* is_low_latency_buffer_size */) {}
#undef TESTCB

bt_callbacks_t callbacks = {
    .size = sizeof(bt_callbacks_t),
    .adapter_state_changed_cb = adapter_state_changed_callback,
    .adapter_properties_cb = adapter_properties_callback,
    .remote_device_properties_cb = remote_device_properties_callback,
    .device_found_cb = device_found_callback,
    .discovery_state_changed_cb = discovery_state_changed_callback,
    .pin_request_cb = pin_request_callback,
    .ssp_request_cb = ssp_request_callback,
    .bond_state_changed_cb = bond_state_changed_callback,
    .address_consolidate_cb = address_consolidate_callback,
    .le_address_associate_cb = le_address_associate_callback,
    .acl_state_changed_cb = acl_state_changed_callback,
    .thread_evt_cb = callback_thread_event,
    .dut_mode_recv_cb = dut_mode_recv_callback,
    .le_test_mode_cb = le_test_mode_callback,
    .energy_info_cb = energy_info_callback,
    .link_quality_report_cb = link_quality_report_callback,
    .generate_local_oob_data_cb = generate_local_oob_data_callback,
    .switch_buffer_size_cb = switch_buffer_size_callback,
    .switch_codec_cb = switch_codec_callback,
};

}  // namespace

class BtifCoreTest : public ::testing::Test {
 protected:
  void SetUp() override {
    callback_map_.clear();
    bluetooth::hci::testing::mock_controller_ = &controller_;
    set_hal_cbacks(&callbacks);
    auto promise = std::promise<void>();
    auto future = promise.get_future();
    callback_map_["callback_thread_event"] = [&promise]() {
      promise.set_value();
    };
    InitializeCoreInterface();
    ASSERT_EQ(std::future_status::ready, future.wait_for(timeout_time));
    callback_map_.erase("callback_thread_event");
  }

  void TearDown() override {
    auto promise = std::promise<void>();
    auto future = promise.get_future();
    callback_map_["callback_thread_event"] = [&promise]() {
      promise.set_value();
    };
    CleanCoreInterface();
    ASSERT_EQ(std::future_status::ready, future.wait_for(timeout_time));
    bluetooth::hci::testing::mock_controller_ = nullptr;
    callback_map_.erase("callback_thread_event");
  }
  bluetooth::hci::testing::MockControllerInterface controller_;
};

class BtifCoreWithControllerTest : public BtifCoreTest {
  void SetUp() override {
    BtifCoreTest::SetUp();
    ON_CALL(controller_, SupportsSniffSubrating).WillByDefault(Return(true));
  }

  void TearDown() override { BtifCoreTest::TearDown(); }
};

std::promise<int> promise0;
void callback0(int val) { promise0.set_value(val); }

TEST_F(BtifCoreTest, test_nop) {}

TEST_F(BtifCoreTest, test_post_on_bt_simple0) {
  const int val = 123;
  promise0 = std::promise<int>();
  std::future<int> future0 = promise0.get_future();
  post_on_bt_jni([=]() { callback0(val); });
  ASSERT_EQ(std::future_status::ready, future0.wait_for(timeout_time));
  ASSERT_EQ(val, future0.get());
}

TEST_F(BtifCoreTest, test_post_on_bt_jni_simple1) {
  std::promise<void> promise;
  std::future<void> future = promise.get_future();
  post_on_bt_jni([=, &promise]() { promise.set_value(); });
  ASSERT_EQ(std::future_status::ready, future.wait_for(timeout_time));
}

TEST_F(BtifCoreTest, test_post_on_bt_jni_simple2) {
  std::promise<void> promise;
  std::future<void> future = promise.get_future();
  BtJniClosure closure = [&promise]() { promise.set_value(); };
  post_on_bt_jni(closure);
  ASSERT_EQ(std::future_status::ready, future.wait_for(timeout_time));
}

TEST_F(BtifCoreTest, test_post_on_bt_jni_simple3) {
  const int val = 456;
  std::promise<int> promise;
  auto future = promise.get_future();
  BtJniClosure closure = [&promise, val]() { promise.set_value(val); };
  post_on_bt_jni(closure);
  ASSERT_EQ(std::future_status::ready, future.wait_for(timeout_time));
  ASSERT_EQ(val, future.get());
}

extern const char* dump_av_sm_event_name(int event);
TEST_F(BtifCoreTest, dump_av_sm_event_name) {
  std::vector<std::pair<int, std::string>> events = {
      std::make_pair(BTA_AV_ENABLE_EVT, "BTA_AV_ENABLE_EVT"),
      std::make_pair(BTA_AV_REGISTER_EVT, "BTA_AV_REGISTER_EVT"),
      std::make_pair(BTA_AV_OPEN_EVT, "BTA_AV_OPEN_EVT"),
      std::make_pair(BTA_AV_CLOSE_EVT, "BTA_AV_CLOSE_EVT"),
      std::make_pair(BTA_AV_START_EVT, "BTA_AV_START_EVT"),
      std::make_pair(BTA_AV_STOP_EVT, "BTA_AV_STOP_EVT"),
      std::make_pair(BTA_AV_PROTECT_REQ_EVT, "BTA_AV_PROTECT_REQ_EVT"),
      std::make_pair(BTA_AV_PROTECT_RSP_EVT, "BTA_AV_PROTECT_RSP_EVT"),
      std::make_pair(BTA_AV_RC_OPEN_EVT, "BTA_AV_RC_OPEN_EVT"),
      std::make_pair(BTA_AV_RC_CLOSE_EVT, "BTA_AV_RC_CLOSE_EVT"),
      std::make_pair(BTA_AV_RC_BROWSE_OPEN_EVT, "BTA_AV_RC_BROWSE_OPEN_EVT"),
      std::make_pair(BTA_AV_RC_BROWSE_CLOSE_EVT, "BTA_AV_RC_BROWSE_CLOSE_EVT"),
      std::make_pair(BTA_AV_REMOTE_CMD_EVT, "BTA_AV_REMOTE_CMD_EVT"),
      std::make_pair(BTA_AV_REMOTE_RSP_EVT, "BTA_AV_REMOTE_RSP_EVT"),
      std::make_pair(BTA_AV_VENDOR_CMD_EVT, "BTA_AV_VENDOR_CMD_EVT"),
      std::make_pair(BTA_AV_VENDOR_RSP_EVT, "BTA_AV_VENDOR_RSP_EVT"),
      std::make_pair(BTA_AV_RECONFIG_EVT, "BTA_AV_RECONFIG_EVT"),
      std::make_pair(BTA_AV_SUSPEND_EVT, "BTA_AV_SUSPEND_EVT"),
      std::make_pair(BTA_AV_PENDING_EVT, "BTA_AV_PENDING_EVT"),
      std::make_pair(BTA_AV_META_MSG_EVT, "BTA_AV_META_MSG_EVT"),
      std::make_pair(BTA_AV_REJECT_EVT, "BTA_AV_REJECT_EVT"),
      std::make_pair(BTA_AV_RC_FEAT_EVT, "BTA_AV_RC_FEAT_EVT"),
      std::make_pair(BTA_AV_RC_PSM_EVT, "BTA_AV_RC_PSM_EVT"),
      std::make_pair(BTA_AV_OFFLOAD_START_RSP_EVT,
                     "BTA_AV_OFFLOAD_START_RSP_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_av_sm_event_name(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN_EVENT";
  ASSERT_STREQ(oss.str().c_str(),
               dump_av_sm_event_name(std::numeric_limits<int>::max()));
}

TEST_F(BtifCoreTest, dump_dm_search_event) {
  std::vector<std::pair<uint16_t, std::string>> events = {
      std::make_pair(BTA_DM_INQ_RES_EVT, "BTA_DM_INQ_RES_EVT"),
      std::make_pair(BTA_DM_INQ_CMPL_EVT, "BTA_DM_INQ_CMPL_EVT"),
      std::make_pair(BTA_DM_DISC_RES_EVT, "BTA_DM_DISC_RES_EVT"),
      std::make_pair(BTA_DM_GATT_OVER_LE_RES_EVT,
                     "BTA_DM_GATT_OVER_LE_RES_EVT"),
      std::make_pair(BTA_DM_DISC_CMPL_EVT, "BTA_DM_DISC_CMPL_EVT"),
      std::make_pair(BTA_DM_SEARCH_CANCEL_CMPL_EVT,
                     "BTA_DM_SEARCH_CANCEL_CMPL_EVT"),
      std::make_pair(BTA_DM_GATT_OVER_SDP_RES_EVT,
                     "BTA_DM_GATT_OVER_SDP_RES_EVT"),
      std::make_pair(BTA_DM_NAME_READ_EVT, "BTA_DM_NAME_READ_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_dm_search_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_dm_search_event(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_property_type) {
  std::vector<std::pair<bt_property_type_t, std::string>> types = {
      std::make_pair(BT_PROPERTY_BDNAME, "BT_PROPERTY_BDNAME"),
      std::make_pair(BT_PROPERTY_BDADDR, "BT_PROPERTY_BDADDR"),
      std::make_pair(BT_PROPERTY_UUIDS, "BT_PROPERTY_UUIDS"),
      std::make_pair(BT_PROPERTY_CLASS_OF_DEVICE,
                     "BT_PROPERTY_CLASS_OF_DEVICE"),
      std::make_pair(BT_PROPERTY_TYPE_OF_DEVICE, "BT_PROPERTY_TYPE_OF_DEVICE"),
      std::make_pair(BT_PROPERTY_REMOTE_RSSI, "BT_PROPERTY_REMOTE_RSSI"),
      std::make_pair(BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT,
                     "BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT"),
      std::make_pair(BT_PROPERTY_ADAPTER_BONDED_DEVICES,
                     "BT_PROPERTY_ADAPTER_BONDED_DEVICES"),
      std::make_pair(BT_PROPERTY_ADAPTER_SCAN_MODE,
                     "BT_PROPERTY_ADAPTER_SCAN_MODE"),
      std::make_pair(BT_PROPERTY_REMOTE_FRIENDLY_NAME,
                     "BT_PROPERTY_REMOTE_FRIENDLY_NAME"),
  };
  for (const auto& type : types) {
    ASSERT_STREQ(type.second.c_str(), dump_property_type(type.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN PROPERTY ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_property_type(static_cast<bt_property_type_t>(
                   std::numeric_limits<uint16_t>::max())));
}

TEST_F(BtifCoreTest, dump_dm_event) {
  std::vector<std::pair<uint8_t, std::string>> events = {
      std::make_pair(BTA_DM_PIN_REQ_EVT, "BTA_DM_PIN_REQ_EVT"),
      std::make_pair(BTA_DM_AUTH_CMPL_EVT, "BTA_DM_AUTH_CMPL_EVT"),
      std::make_pair(BTA_DM_LINK_UP_EVT, "BTA_DM_LINK_UP_EVT"),
      std::make_pair(BTA_DM_LINK_DOWN_EVT, "BTA_DM_LINK_DOWN_EVT"),
      std::make_pair(BTA_DM_BOND_CANCEL_CMPL_EVT,
                     "BTA_DM_BOND_CANCEL_CMPL_EVT"),
      std::make_pair(BTA_DM_SP_CFM_REQ_EVT, "BTA_DM_SP_CFM_REQ_EVT"),
      std::make_pair(BTA_DM_SP_KEY_NOTIF_EVT, "BTA_DM_SP_KEY_NOTIF_EVT"),
      std::make_pair(BTA_DM_BLE_KEY_EVT, "BTA_DM_BLE_KEY_EVT"),
      std::make_pair(BTA_DM_BLE_SEC_REQ_EVT, "BTA_DM_BLE_SEC_REQ_EVT"),
      std::make_pair(BTA_DM_BLE_PASSKEY_NOTIF_EVT,
                     "BTA_DM_BLE_PASSKEY_NOTIF_EVT"),
      std::make_pair(BTA_DM_BLE_PASSKEY_REQ_EVT, "BTA_DM_BLE_PASSKEY_REQ_EVT"),
      std::make_pair(BTA_DM_BLE_OOB_REQ_EVT, "BTA_DM_BLE_OOB_REQ_EVT"),
      std::make_pair(BTA_DM_BLE_SC_OOB_REQ_EVT, "BTA_DM_BLE_SC_OOB_REQ_EVT"),
      std::make_pair(BTA_DM_BLE_LOCAL_IR_EVT, "BTA_DM_BLE_LOCAL_IR_EVT"),
      std::make_pair(BTA_DM_BLE_LOCAL_ER_EVT, "BTA_DM_BLE_LOCAL_ER_EVT"),
      std::make_pair(BTA_DM_BLE_AUTH_CMPL_EVT, "BTA_DM_BLE_AUTH_CMPL_EVT"),
      std::make_pair(BTA_DM_DEV_UNPAIRED_EVT, "BTA_DM_DEV_UNPAIRED_EVT"),
      std::make_pair(BTA_DM_ENER_INFO_READ, "BTA_DM_ENER_INFO_READ"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_dm_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN DM EVENT";
  ASSERT_STREQ(oss.str().c_str(),
               dump_dm_event(std::numeric_limits<uint8_t>::max()));
}

TEST_F(BtifCoreTest, dump_hf_event) {
  std::vector<std::pair<uint8_t, std::string>> events = {
      std::make_pair(BTA_AG_ENABLE_EVT, "BTA_AG_ENABLE_EVT"),
      std::make_pair(BTA_AG_REGISTER_EVT, "BTA_AG_REGISTER_EVT"),
      std::make_pair(BTA_AG_OPEN_EVT, "BTA_AG_OPEN_EVT"),
      std::make_pair(BTA_AG_CLOSE_EVT, "BTA_AG_CLOSE_EVT"),
      std::make_pair(BTA_AG_CONN_EVT, "BTA_AG_CONN_EVT"),
      std::make_pair(BTA_AG_AUDIO_OPEN_EVT, "BTA_AG_AUDIO_OPEN_EVT"),
      std::make_pair(BTA_AG_AUDIO_CLOSE_EVT, "BTA_AG_AUDIO_CLOSE_EVT"),
      std::make_pair(BTA_AG_SPK_EVT, "BTA_AG_SPK_EVT"),
      std::make_pair(BTA_AG_MIC_EVT, "BTA_AG_MIC_EVT"),
      std::make_pair(BTA_AG_AT_CKPD_EVT, "BTA_AG_AT_CKPD_EVT"),
      std::make_pair(BTA_AG_DISABLE_EVT, "BTA_AG_DISABLE_EVT"),
      std::make_pair(BTA_AG_CODEC_EVT, "BTA_AG_CODEC_EVT"),
      std::make_pair(BTA_AG_AT_A_EVT, "BTA_AG_AT_A_EVT"),
      std::make_pair(BTA_AG_AT_D_EVT, "BTA_AG_AT_D_EVT"),
      std::make_pair(BTA_AG_AT_CHLD_EVT, "BTA_AG_AT_CHLD_EVT"),
      std::make_pair(BTA_AG_AT_CHUP_EVT, "BTA_AG_AT_CHUP_EVT"),
      std::make_pair(BTA_AG_AT_CIND_EVT, "BTA_AG_AT_CIND_EVT"),
      std::make_pair(BTA_AG_AT_VTS_EVT, "BTA_AG_AT_VTS_EVT"),
      std::make_pair(BTA_AG_AT_BINP_EVT, "BTA_AG_AT_BINP_EVT"),
      std::make_pair(BTA_AG_AT_BLDN_EVT, "BTA_AG_AT_BLDN_EVT"),
      std::make_pair(BTA_AG_AT_BVRA_EVT, "BTA_AG_AT_BVRA_EVT"),
      std::make_pair(BTA_AG_AT_NREC_EVT, "BTA_AG_AT_NREC_EVT"),
      std::make_pair(BTA_AG_AT_CNUM_EVT, "BTA_AG_AT_CNUM_EVT"),
      std::make_pair(BTA_AG_AT_BTRH_EVT, "BTA_AG_AT_BTRH_EVT"),
      std::make_pair(BTA_AG_AT_CLCC_EVT, "BTA_AG_AT_CLCC_EVT"),
      std::make_pair(BTA_AG_AT_COPS_EVT, "BTA_AG_AT_COPS_EVT"),
      std::make_pair(BTA_AG_AT_UNAT_EVT, "BTA_AG_AT_UNAT_EVT"),
      std::make_pair(BTA_AG_AT_CBC_EVT, "BTA_AG_AT_CBC_EVT"),
      std::make_pair(BTA_AG_AT_BAC_EVT, "BTA_AG_AT_BAC_EVT"),
      std::make_pair(BTA_AG_AT_BCS_EVT, "BTA_AG_AT_BCS_EVT"),
      std::make_pair(BTA_AG_AT_BIND_EVT, "BTA_AG_AT_BIND_EVT"),
      std::make_pair(BTA_AG_AT_BIEV_EVT, "BTA_AG_AT_BIEV_EVT"),
      std::make_pair(BTA_AG_AT_BIA_EVT, "BTA_AG_AT_BIA_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_hf_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_hf_event(std::numeric_limits<uint8_t>::max()));
}

TEST_F(BtifCoreTest, dump_hf_client_event) {
  std::vector<std::pair<int, std::string>> events = {
      std::make_pair(BTA_HF_CLIENT_ENABLE_EVT, "BTA_HF_CLIENT_ENABLE_EVT"),
      std::make_pair(BTA_HF_CLIENT_REGISTER_EVT, "BTA_HF_CLIENT_REGISTER_EVT"),
      std::make_pair(BTA_HF_CLIENT_OPEN_EVT, "BTA_HF_CLIENT_OPEN_EVT"),
      std::make_pair(BTA_HF_CLIENT_CLOSE_EVT, "BTA_HF_CLIENT_CLOSE_EVT"),
      std::make_pair(BTA_HF_CLIENT_CONN_EVT, "BTA_HF_CLIENT_CONN_EVT"),
      std::make_pair(BTA_HF_CLIENT_AUDIO_OPEN_EVT,
                     "BTA_HF_CLIENT_AUDIO_OPEN_EVT"),
      std::make_pair(BTA_HF_CLIENT_AUDIO_MSBC_OPEN_EVT,
                     "BTA_HF_CLIENT_AUDIO_MSBC_OPEN_EVT"),
      std::make_pair(BTA_HF_CLIENT_AUDIO_LC3_OPEN_EVT,
                     "BTA_HF_CLIENT_AUDIO_LC3_OPEN_EVT"),
      std::make_pair(BTA_HF_CLIENT_AUDIO_CLOSE_EVT,
                     "BTA_HF_CLIENT_AUDIO_CLOSE_EVT"),
      std::make_pair(BTA_HF_CLIENT_SPK_EVT, "BTA_HF_CLIENT_SPK_EVT"),
      std::make_pair(BTA_HF_CLIENT_MIC_EVT, "BTA_HF_CLIENT_MIC_EVT"),
      std::make_pair(BTA_HF_CLIENT_DISABLE_EVT, "BTA_HF_CLIENT_DISABLE_EVT"),
      std::make_pair(BTA_HF_CLIENT_IND_EVT, "BTA_HF_CLIENT_IND_EVT"),
      std::make_pair(BTA_HF_CLIENT_VOICE_REC_EVT,
                     "BTA_HF_CLIENT_VOICE_REC_EVT"),
      std::make_pair(BTA_HF_CLIENT_OPERATOR_NAME_EVT,
                     "BTA_HF_CLIENT_OPERATOR_NAME_EVT"),
      std::make_pair(BTA_HF_CLIENT_CLIP_EVT, "BTA_HF_CLIENT_CLIP_EVT"),
      std::make_pair(BTA_HF_CLIENT_CCWA_EVT, "BTA_HF_CLIENT_CCWA_EVT"),
      std::make_pair(BTA_HF_CLIENT_AT_RESULT_EVT,
                     "BTA_HF_CLIENT_AT_RESULT_EVT"),
      std::make_pair(BTA_HF_CLIENT_CLCC_EVT, "BTA_HF_CLIENT_CLCC_EVT"),
      std::make_pair(BTA_HF_CLIENT_CNUM_EVT, "BTA_HF_CLIENT_CNUM_EVT"),
      std::make_pair(BTA_HF_CLIENT_BTRH_EVT, "BTA_HF_CLIENT_BTRH_EVT"),
      std::make_pair(BTA_HF_CLIENT_BSIR_EVT, "BTA_HF_CLIENT_BSIR_EVT"),
      std::make_pair(BTA_HF_CLIENT_BINP_EVT, "BTA_HF_CLIENT_BINP_EVT"),
      std::make_pair(BTA_HF_CLIENT_RING_INDICATION,
                     "BTA_HF_CLIENT_RING_INDICATION"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_hf_client_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_hf_client_event(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_hh_event) {
  std::vector<std::pair<int, std::string>> events = {
      std::make_pair(BTA_HH_ENABLE_EVT, "BTA_HH_ENABLE_EVT"),
      std::make_pair(BTA_HH_DISABLE_EVT, "BTA_HH_DISABLE_EVT"),
      std::make_pair(BTA_HH_OPEN_EVT, "BTA_HH_OPEN_EVT"),
      std::make_pair(BTA_HH_CLOSE_EVT, "BTA_HH_CLOSE_EVT"),
      std::make_pair(BTA_HH_GET_DSCP_EVT, "BTA_HH_GET_DSCP_EVT"),
      std::make_pair(BTA_HH_GET_PROTO_EVT, "BTA_HH_GET_PROTO_EVT"),
      std::make_pair(BTA_HH_GET_RPT_EVT, "BTA_HH_GET_RPT_EVT"),
      std::make_pair(BTA_HH_GET_IDLE_EVT, "BTA_HH_GET_IDLE_EVT"),
      std::make_pair(BTA_HH_SET_PROTO_EVT, "BTA_HH_SET_PROTO_EVT"),
      std::make_pair(BTA_HH_SET_RPT_EVT, "BTA_HH_SET_RPT_EVT"),
      std::make_pair(BTA_HH_SET_IDLE_EVT, "BTA_HH_SET_IDLE_EVT"),
      std::make_pair(BTA_HH_VC_UNPLUG_EVT, "BTA_HH_VC_UNPLUG_EVT"),
      std::make_pair(BTA_HH_ADD_DEV_EVT, "BTA_HH_ADD_DEV_EVT"),
      std::make_pair(BTA_HH_RMV_DEV_EVT, "BTA_HH_RMV_DEV_EVT"),
      std::make_pair(BTA_HH_API_ERR_EVT, "BTA_HH_API_ERR_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_hh_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_hh_event(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_hd_event) {
  std::vector<std::pair<uint16_t, std::string>> events = {
      std::make_pair(BTA_HD_ENABLE_EVT, "BTA_HD_ENABLE_EVT"),
      std::make_pair(BTA_HD_DISABLE_EVT, "BTA_HD_DISABLE_EVT"),
      std::make_pair(BTA_HD_REGISTER_APP_EVT, "BTA_HD_REGISTER_APP_EVT"),
      std::make_pair(BTA_HD_UNREGISTER_APP_EVT, "BTA_HD_UNREGISTER_APP_EVT"),
      std::make_pair(BTA_HD_OPEN_EVT, "BTA_HD_OPEN_EVT"),
      std::make_pair(BTA_HD_CLOSE_EVT, "BTA_HD_CLOSE_EVT"),
      std::make_pair(BTA_HD_GET_REPORT_EVT, "BTA_HD_GET_REPORT_EVT"),
      std::make_pair(BTA_HD_SET_REPORT_EVT, "BTA_HD_SET_REPORT_EVT"),
      std::make_pair(BTA_HD_SET_PROTOCOL_EVT, "BTA_HD_SET_PROTOCOL_EVT"),
      std::make_pair(BTA_HD_INTR_DATA_EVT, "BTA_HD_INTR_DATA_EVT"),
      std::make_pair(BTA_HD_VC_UNPLUG_EVT, "BTA_HD_VC_UNPLUG_EVT"),
      std::make_pair(BTA_HD_CONN_STATE_EVT, "BTA_HD_CONN_STATE_EVT"),
      std::make_pair(BTA_HD_API_ERR_EVT, "BTA_HD_API_ERR_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_hd_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_hd_event(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_thread_evt) {
  std::vector<std::pair<bt_cb_thread_evt, std::string>> events = {
      std::make_pair(ASSOCIATE_JVM, "ASSOCIATE_JVM"),
      std::make_pair(DISASSOCIATE_JVM, "DISASSOCIATE_JVM"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_thread_evt(event.first));
  }
  std::ostringstream oss;
  oss << "unknown thread evt";
  ASSERT_STREQ(oss.str().c_str(), dump_thread_evt(static_cast<bt_cb_thread_evt>(
                                      std::numeric_limits<uint16_t>::max())));
}

TEST_F(BtifCoreTest, dump_av_conn_state) {
  std::vector<std::pair<uint16_t, std::string>> events = {
      std::make_pair(BTAV_CONNECTION_STATE_DISCONNECTED,
                     "BTAV_CONNECTION_STATE_DISCONNECTED"),
      std::make_pair(BTAV_CONNECTION_STATE_CONNECTING,
                     "BTAV_CONNECTION_STATE_CONNECTING"),
      std::make_pair(BTAV_CONNECTION_STATE_CONNECTED,
                     "BTAV_CONNECTION_STATE_CONNECTED"),
      std::make_pair(BTAV_CONNECTION_STATE_DISCONNECTING,
                     "BTAV_CONNECTION_STATE_DISCONNECTING"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_av_conn_state(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_av_conn_state(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_av_audio_state) {
  std::vector<std::pair<uint16_t, std::string>> events = {
      std::make_pair(BTAV_AUDIO_STATE_REMOTE_SUSPEND,
                     "BTAV_AUDIO_STATE_REMOTE_SUSPEND"),
      std::make_pair(BTAV_AUDIO_STATE_STOPPED, "BTAV_AUDIO_STATE_STOPPED"),
      std::make_pair(BTAV_AUDIO_STATE_STARTED, "BTAV_AUDIO_STATE_STARTED"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_av_audio_state(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN MSG ID";
  ASSERT_STREQ(oss.str().c_str(),
               dump_av_audio_state(std::numeric_limits<uint16_t>::max()));
}

TEST_F(BtifCoreTest, dump_adapter_scan_mode) {
  std::vector<std::pair<bt_scan_mode_t, std::string>> events = {
      std::make_pair(BT_SCAN_MODE_NONE, "BT_SCAN_MODE_NONE"),
      std::make_pair(BT_SCAN_MODE_CONNECTABLE, "BT_SCAN_MODE_CONNECTABLE"),
      std::make_pair(BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE,
                     "BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_adapter_scan_mode(event.first));
  }
  std::ostringstream oss;
  oss << "unknown scan mode";
  ASSERT_STREQ(oss.str().c_str(),
               dump_adapter_scan_mode(static_cast<bt_scan_mode_t>(
                   std::numeric_limits<int>::max())));
}

TEST_F(BtifCoreTest, dump_bt_status) {
  std::vector<std::pair<bt_status_t, std::string>> events = {
      std::make_pair(BT_STATUS_SUCCESS, "BT_STATUS_SUCCESS"),
      std::make_pair(BT_STATUS_FAIL, "BT_STATUS_FAIL"),
      std::make_pair(BT_STATUS_NOT_READY, "BT_STATUS_NOT_READY"),
      std::make_pair(BT_STATUS_NOMEM, "BT_STATUS_NOMEM"),
      std::make_pair(BT_STATUS_BUSY, "BT_STATUS_BUSY"),
      std::make_pair(BT_STATUS_UNSUPPORTED, "BT_STATUS_UNSUPPORTED"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_bt_status(event.first));
  }
  std::ostringstream oss;
  oss << "unknown scan mode";
  ASSERT_STREQ(oss.str().c_str(), dump_bt_status(static_cast<bt_status_t>(
                                      std::numeric_limits<int>::max())));
}

TEST_F(BtifCoreTest, dump_rc_event) {
  std::vector<std::pair<uint8_t, std::string>> events = {
      std::make_pair(BTA_AV_RC_OPEN_EVT, "BTA_AV_RC_OPEN_EVT"),
      std::make_pair(BTA_AV_RC_CLOSE_EVT, "BTA_AV_RC_CLOSE_EVT"),
      std::make_pair(BTA_AV_RC_BROWSE_OPEN_EVT, "BTA_AV_RC_BROWSE_OPEN_EVT"),
      std::make_pair(BTA_AV_RC_BROWSE_CLOSE_EVT, "BTA_AV_RC_BROWSE_CLOSE_EVT"),
      std::make_pair(BTA_AV_REMOTE_CMD_EVT, "BTA_AV_REMOTE_CMD_EVT"),
      std::make_pair(BTA_AV_REMOTE_RSP_EVT, "BTA_AV_REMOTE_RSP_EVT"),
      std::make_pair(BTA_AV_VENDOR_CMD_EVT, "BTA_AV_VENDOR_CMD_EVT"),
      std::make_pair(BTA_AV_VENDOR_RSP_EVT, "BTA_AV_VENDOR_RSP_EVT"),
      std::make_pair(BTA_AV_META_MSG_EVT, "BTA_AV_META_MSG_EVT"),
      std::make_pair(BTA_AV_RC_FEAT_EVT, "BTA_AV_RC_FEAT_EVT"),
      std::make_pair(BTA_AV_RC_PSM_EVT, "BTA_AV_RC_PSM_EVT"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(), dump_rc_event(event.first));
  }
  std::ostringstream oss;
  oss << "UNKNOWN_EVENT";
  ASSERT_STREQ(oss.str().c_str(),
               dump_rc_event(std::numeric_limits<uint8_t>::max()));
}

TEST_F(BtifCoreTest, dump_rc_notification_event_id) {
  std::vector<std::pair<uint8_t, std::string>> events = {
      std::make_pair(AVRC_EVT_PLAY_STATUS_CHANGE,
                     "AVRC_EVT_PLAY_STATUS_CHANGE"),
      std::make_pair(AVRC_EVT_TRACK_CHANGE, "AVRC_EVT_TRACK_CHANGE"),
      std::make_pair(AVRC_EVT_TRACK_REACHED_END, "AVRC_EVT_TRACK_REACHED_END"),
      std::make_pair(AVRC_EVT_TRACK_REACHED_START,
                     "AVRC_EVT_TRACK_REACHED_START"),
      std::make_pair(AVRC_EVT_PLAY_POS_CHANGED, "AVRC_EVT_PLAY_POS_CHANGED"),
      std::make_pair(AVRC_EVT_BATTERY_STATUS_CHANGE,
                     "AVRC_EVT_BATTERY_STATUS_CHANGE"),
      std::make_pair(AVRC_EVT_SYSTEM_STATUS_CHANGE,
                     "AVRC_EVT_SYSTEM_STATUS_CHANGE"),
      std::make_pair(AVRC_EVT_APP_SETTING_CHANGE,
                     "AVRC_EVT_APP_SETTING_CHANGE"),
      std::make_pair(AVRC_EVT_VOLUME_CHANGE, "AVRC_EVT_VOLUME_CHANGE"),
      std::make_pair(AVRC_EVT_ADDR_PLAYER_CHANGE,
                     "AVRC_EVT_ADDR_PLAYER_CHANGE"),
      std::make_pair(AVRC_EVT_AVAL_PLAYERS_CHANGE,
                     "AVRC_EVT_AVAL_PLAYERS_CHANGE"),
      std::make_pair(AVRC_EVT_NOW_PLAYING_CHANGE,
                     "AVRC_EVT_NOW_PLAYING_CHANGE"),
      std::make_pair(AVRC_EVT_UIDS_CHANGE, "AVRC_EVT_UIDS_CHANGE"),
  };
  for (const auto& event : events) {
    ASSERT_STREQ(event.second.c_str(),
                 dump_rc_notification_event_id(event.first));
  }
  std::ostringstream oss;
  oss << "Unhandled Event ID";
  ASSERT_STREQ(oss.str().c_str(), dump_rc_notification_event_id(
                                      std::numeric_limits<uint8_t>::max()));
}

TEST_F(BtifCoreTest, dump_rc_pdu) {
  std::vector<std::pair<uint8_t, std::string>> pdus = {
      std::make_pair(AVRC_PDU_LIST_PLAYER_APP_ATTR,
                     "AVRC_PDU_LIST_PLAYER_APP_ATTR"),
      std::make_pair(AVRC_PDU_LIST_PLAYER_APP_VALUES,
                     "AVRC_PDU_LIST_PLAYER_APP_VALUES"),
      std::make_pair(AVRC_PDU_GET_CUR_PLAYER_APP_VALUE,
                     "AVRC_PDU_GET_CUR_PLAYER_APP_VALUE"),
      std::make_pair(AVRC_PDU_SET_PLAYER_APP_VALUE,
                     "AVRC_PDU_SET_PLAYER_APP_VALUE"),
      std::make_pair(AVRC_PDU_GET_PLAYER_APP_ATTR_TEXT,
                     "AVRC_PDU_GET_PLAYER_APP_ATTR_TEXT"),
      std::make_pair(AVRC_PDU_GET_PLAYER_APP_VALUE_TEXT,
                     "AVRC_PDU_GET_PLAYER_APP_VALUE_TEXT"),
      std::make_pair(AVRC_PDU_INFORM_DISPLAY_CHARSET,
                     "AVRC_PDU_INFORM_DISPLAY_CHARSET"),
      std::make_pair(AVRC_PDU_INFORM_BATTERY_STAT_OF_CT,
                     "AVRC_PDU_INFORM_BATTERY_STAT_OF_CT"),
      std::make_pair(AVRC_PDU_GET_ELEMENT_ATTR, "AVRC_PDU_GET_ELEMENT_ATTR"),
      std::make_pair(AVRC_PDU_GET_PLAY_STATUS, "AVRC_PDU_GET_PLAY_STATUS"),
      std::make_pair(AVRC_PDU_REGISTER_NOTIFICATION,
                     "AVRC_PDU_REGISTER_NOTIFICATION"),
      std::make_pair(AVRC_PDU_REQUEST_CONTINUATION_RSP,
                     "AVRC_PDU_REQUEST_CONTINUATION_RSP"),
      std::make_pair(AVRC_PDU_ABORT_CONTINUATION_RSP,
                     "AVRC_PDU_ABORT_CONTINUATION_RSP"),
      std::make_pair(AVRC_PDU_SET_ABSOLUTE_VOLUME,
                     "AVRC_PDU_SET_ABSOLUTE_VOLUME"),
      std::make_pair(AVRC_PDU_SET_ADDRESSED_PLAYER,
                     "AVRC_PDU_SET_ADDRESSED_PLAYER"),
      std::make_pair(AVRC_PDU_CHANGE_PATH, "AVRC_PDU_CHANGE_PATH"),
      std::make_pair(AVRC_PDU_GET_CAPABILITIES, "AVRC_PDU_GET_CAPABILITIES"),
      std::make_pair(AVRC_PDU_SET_BROWSED_PLAYER,
                     "AVRC_PDU_SET_BROWSED_PLAYER"),
      std::make_pair(AVRC_PDU_GET_FOLDER_ITEMS, "AVRC_PDU_GET_FOLDER_ITEMS"),
      std::make_pair(AVRC_PDU_GET_ITEM_ATTRIBUTES,
                     "AVRC_PDU_GET_ITEM_ATTRIBUTES"),
      std::make_pair(AVRC_PDU_PLAY_ITEM, "AVRC_PDU_PLAY_ITEM"),
      std::make_pair(AVRC_PDU_SEARCH, "AVRC_PDU_SEARCH"),
      std::make_pair(AVRC_PDU_ADD_TO_NOW_PLAYING,
                     "AVRC_PDU_ADD_TO_NOW_PLAYING"),
      std::make_pair(AVRC_PDU_GET_TOTAL_NUM_OF_ITEMS,
                     "AVRC_PDU_GET_TOTAL_NUM_OF_ITEMS"),
      std::make_pair(AVRC_PDU_GENERAL_REJECT, "AVRC_PDU_GENERAL_REJECT"),
  };
  for (const auto& pdu : pdus) {
    ASSERT_STREQ(pdu.second.c_str(), dump_rc_pdu(pdu.first));
  }
  std::ostringstream oss;
  oss << "Unknown PDU";
  ASSERT_STREQ(oss.str().c_str(),
               dump_rc_pdu(std::numeric_limits<uint8_t>::max()));
}

void bta_dm_acl_up(const RawAddress& bd_addr, tBT_TRANSPORT transport,
                   uint16_t acl_handle);

TEST_F(BtifCoreWithControllerTest, btif_dm_get_connection_state__unconnected) {
  ASSERT_EQ(0, btif_dm_get_connection_state(kRawAddress));
}

TEST_F(BtifCoreWithControllerTest,
       btif_dm_get_connection_state__connected_no_encryption) {
  bta_dm_acl_up(kRawAddress, BT_TRANSPORT_AUTO, 0x123);

  test::mock::stack_btm_sec::BTM_IsEncrypted.body =
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT transport) {
        switch (transport) {
          case BT_TRANSPORT_BR_EDR:
            return false;
          case BT_TRANSPORT_LE:
            return false;
        }
        return false;
      };
  ASSERT_EQ(1, btif_dm_get_connection_state(kRawAddress));
  test::mock::stack_btm_sec::BTM_IsEncrypted = {};
}

TEST_F(BtifCoreWithControllerTest,
       btif_dm_get_connection_state__connected_classic_encryption) {
  bta_dm_acl_up(kRawAddress, BT_TRANSPORT_AUTO, 0x123);

  test::mock::stack_btm_sec::BTM_IsEncrypted.body =
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT transport) {
        switch (transport) {
          case BT_TRANSPORT_BR_EDR:
            return true;
          case BT_TRANSPORT_LE:
            return false;
        }
        return false;
      };
  ASSERT_EQ(3, btif_dm_get_connection_state(kRawAddress));
  test::mock::stack_btm_sec::BTM_IsEncrypted = {};
}

TEST_F(BtifCoreWithControllerTest,
       btif_dm_get_connection_state__connected_le_encryption) {
  bta_dm_acl_up(kRawAddress, BT_TRANSPORT_AUTO, 0x123);

  test::mock::stack_btm_sec::BTM_IsEncrypted.body =
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT transport) {
        switch (transport) {
          case BT_TRANSPORT_BR_EDR:
            return false;
          case BT_TRANSPORT_LE:
            return true;
        }
        return false;
      };
  ASSERT_EQ(5, btif_dm_get_connection_state(kRawAddress));
  test::mock::stack_btm_sec::BTM_IsEncrypted = {};
}

TEST_F(BtifCoreWithControllerTest,
       btif_dm_get_connection_state__connected_both_encryption) {
  bta_dm_acl_up(kRawAddress, BT_TRANSPORT_AUTO, 0x123);

  test::mock::stack_btm_sec::BTM_IsEncrypted.body =
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT transport) {
        switch (transport) {
          case BT_TRANSPORT_BR_EDR:
            return true;
          case BT_TRANSPORT_LE:
            return true;
        }
        return false;
      };
  ASSERT_EQ(7, btif_dm_get_connection_state(kRawAddress));
  test::mock::stack_btm_sec::BTM_IsEncrypted = {};
}

TEST_F(BtifCoreWithControllerTest, btif_dm_get_connection_state_sync) {
  bta_dm_acl_up(kRawAddress, BT_TRANSPORT_AUTO, 0x123);

  test::mock::stack_btm_sec::BTM_IsEncrypted.body =
      [](const RawAddress& /* bd_addr */, tBT_TRANSPORT transport) {
        switch (transport) {
          case BT_TRANSPORT_BR_EDR:
            return true;
          case BT_TRANSPORT_LE:
            return true;
        }
        return false;
      };
  ASSERT_EQ(7, btif_dm_get_connection_state_sync(kRawAddress));

  test::mock::stack_btm_sec::BTM_IsEncrypted = {};
}
