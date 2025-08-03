/*
 * Copyright 2020 The Android Open Source Project
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

#undef LOG_TAG  // Undefine the LOG_TAG by this compilation unit
#include "btif/src/btif_rc.cc"

#include <bluetooth/log.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <future>

#include "bta/include/bta_av_api.h"
#include "btif/avrcp/avrcp_service.h"
#include "btif/include/btif_common.h"
#include "common/message_loop_thread.h"
#include "device/include/interop.h"
#include "include/check.h"
#include "include/hardware/bt_rc.h"
#include "test/common/mock_functions.h"
#include "test/mock/mock_osi_alarm.h"
#include "test/mock/mock_osi_allocator.h"
#include "test/mock/mock_osi_list.h"
#include "types/raw_address.h"

namespace bluetooth {
namespace avrcp {
int VolChanged = 0;
AvrcpService* AvrcpService::instance_ = nullptr;

void AvrcpService::SendMediaUpdate(bool track_changed, bool play_state,
                                   bool queue){};
void AvrcpService::SendFolderUpdate(bool available_players,
                                    bool addressed_players, bool uids){};
void AvrcpService::SendActiveDeviceChanged(const RawAddress& address){};
void AvrcpService::SendPlayerSettingsChanged(
    std::vector<PlayerAttribute> attributes, std::vector<uint8_t> values){};
void AvrcpService::ServiceInterfaceImpl::Init(
    MediaInterface* media_interface, VolumeInterface* volume_interface,
    PlayerSettingsInterface* player_settings_interface){};
void AvrcpService::ServiceInterfaceImpl::RegisterBipServer(int psm){};
void AvrcpService::ServiceInterfaceImpl::UnregisterBipServer(){};
bool AvrcpService::ServiceInterfaceImpl::ConnectDevice(
    const RawAddress& bdaddr) {
  return true;
};
bool AvrcpService::ServiceInterfaceImpl::DisconnectDevice(
    const RawAddress& bdaddr) {
  return true;
};
void AvrcpService::ServiceInterfaceImpl::SetBipClientStatus(
    const RawAddress& bdaddr, bool connected){};
bool AvrcpService::ServiceInterfaceImpl::Cleanup() { return true; };

AvrcpService* AvrcpService::Get() {
  CHECK(instance_ == nullptr);
  instance_ = new AvrcpService();
  return instance_;
}

void AvrcpService::RegisterVolChanged(const RawAddress& bdaddr) {
  VolChanged++;
}
}  // namespace avrcp
}  // namespace bluetooth

namespace {
const RawAddress kDeviceAddress({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
}  // namespace

void btif_av_clear_remote_suspend_flag(void) {}
bool btif_av_is_connected(void) { return true; }
bool btif_av_is_sink_enabled(void) { return true; }
RawAddress btif_av_sink_active_peer(void) { return RawAddress(); }
RawAddress btif_av_source_active_peer(void) { return RawAddress(); }
bool btif_av_stream_started_ready(void) { return false; }
bt_status_t btif_transfer_context(tBTIF_CBACK* p_cback, uint16_t event,
                                  char* p_params, int param_len,
                                  tBTIF_COPY_CBACK* p_copy_cback) {
  return BT_STATUS_SUCCESS;
}
bool btif_av_src_sink_coexist_enabled() { return true; }
bool btif_av_is_connected_addr(const RawAddress& peer_address) { return true; }
bool btif_av_peer_is_connected_sink(const RawAddress& peer_address) {
  return false;
}
bool btif_av_peer_is_connected_source(const RawAddress& peer_address) {
  return true;
}
bool btif_av_peer_is_sink(const RawAddress& peer_address) { return false; }
bool btif_av_peer_is_source(const RawAddress& peer_address) { return true; }
bool btif_av_both_enable(void) { return true; }

static bluetooth::common::MessageLoopThread jni_thread("bt_jni_thread");
bt_status_t do_in_jni_thread(const base::Location& from_here,
                             base::OnceClosure task) {
  if (!jni_thread.DoInThread(from_here, std::move(task))) {
    log::error("Post task to task runner failed!");
    return BT_STATUS_FAIL;
  }
  return BT_STATUS_SUCCESS;
}
bluetooth::common::MessageLoopThread* get_main_thread() { return nullptr; }
bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr) {
  return false;
}

/**
 * Test class to test selected functionality in hci/src/hci_layer.cc
 */
class BtifRcTest : public ::testing::Test {
 protected:
  void SetUp() override { reset_mock_function_count_map(); }
  void TearDown() override {}
};

TEST_F(BtifRcTest, get_element_attr_rsp) {
  btif_rc_cb.rc_multi_cb[0].rc_addr = kDeviceAddress;
  btif_rc_cb.rc_multi_cb[0].rc_connected = true;
  btif_rc_cb.rc_multi_cb[0]
      .rc_pdu_info[IDX_GET_ELEMENT_ATTR_RSP]
      .is_rsp_pending = true;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;

  btrc_element_attr_val_t p_attrs[BTRC_MAX_ELEM_ATTR_SIZE];
  uint8_t num_attr = BTRC_MAX_ELEM_ATTR_SIZE + 1;

  CHECK(get_element_attr_rsp(kDeviceAddress, num_attr, p_attrs) ==
        BT_STATUS_SUCCESS);
  ASSERT_EQ(1, get_func_call_count("AVRC_BldResponse"));
}

TEST_F(BtifRcTest, btif_rc_get_addr_by_handle) {
  RawAddress bd_addr;

  btif_rc_cb.rc_multi_cb[0].rc_addr = kDeviceAddress;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_handle = 0;

  btif_rc_get_addr_by_handle(0, bd_addr);
  CHECK(kDeviceAddress == bd_addr);
}

static btrc_ctrl_callbacks_t default_btrc_ctrl_callbacks = {
    .size = sizeof(btrc_ctrl_callbacks_t),
    .passthrough_rsp_cb = [](const RawAddress& /* bd_addr */, int /* id */,
                             int /* key_state */) { FAIL(); },
    .groupnavigation_rsp_cb = [](int /* id */, int /* key_state */) { FAIL(); },
    .connection_state_cb = [](bool /* rc_connect */, bool /* bt_connect */,
                              const RawAddress& /* bd_addr */) { FAIL(); },
    .getrcfeatures_cb = [](const RawAddress& /* bd_addr */,
                           int /* features */) { FAIL(); },
    .setplayerappsetting_rsp_cb = [](const RawAddress& /* bd_addr */,
                                     uint8_t /* accepted */) { FAIL(); },
    .playerapplicationsetting_cb =
        [](const RawAddress& /* bd_addr */, uint8_t /* num_attr */,
           btrc_player_app_attr_t* /* app_attrs */, uint8_t /* num_ext_attr */,
           btrc_player_app_ext_attr_t* /* ext_attrs */) { FAIL(); },
    .playerapplicationsetting_changed_cb =
        [](const RawAddress& /* bd_addr */,
           const btrc_player_settings_t& /* vals */) { FAIL(); },
    .setabsvol_cmd_cb = [](const RawAddress& /* bd_addr */,
                           uint8_t /* abs_vol */,
                           uint8_t /* label */) { FAIL(); },
    .registernotification_absvol_cb = [](const RawAddress& /* bd_addr */,
                                         uint8_t /* label */) { FAIL(); },
    .track_changed_cb = [](const RawAddress& /* bd_addr */,
                           uint8_t /* num_attr */,
                           btrc_element_attr_val_t* /* p_attrs */) { FAIL(); },
    .play_position_changed_cb = [](const RawAddress& /* bd_addr */,
                                   uint32_t /* song_len */,
                                   uint32_t /* song_pos */) { FAIL(); },
    .play_status_changed_cb =
        [](const RawAddress& /* bd_addr */,
           btrc_play_status_t /* play_status */) { FAIL(); },
    .get_folder_items_cb = [](const RawAddress& /* bd_addr */,
                              btrc_status_t /* status */,
                              const btrc_folder_items_t* /* folder_items */,
                              uint8_t /* count */) { FAIL(); },
    .change_folder_path_cb = [](const RawAddress& /* bd_addr */,
                                uint32_t /* count */) { FAIL(); },
    .set_browsed_player_cb = [](const RawAddress& /* bd_addr */,
                                uint8_t /* num_items */,
                                uint8_t /* depth */) { FAIL(); },
    .set_addressed_player_cb = [](const RawAddress& /* bd_addr */,
                                  uint8_t /* status */) { FAIL(); },
    .addressed_player_changed_cb = [](const RawAddress& /* bd_addr */,
                                      uint16_t /* id */) { FAIL(); },
    .now_playing_contents_changed_cb =
        [](const RawAddress& /* bd_addr */) { FAIL(); },
    .available_player_changed_cb =
        [](const RawAddress& /* bd_addr */) { FAIL(); },
    .get_cover_art_psm_cb = [](const RawAddress& /* bd_addr */,
                               const uint16_t /* psm */) { FAIL(); },
};
static btrc_ctrl_callbacks_t btrc_ctrl_callbacks = default_btrc_ctrl_callbacks;

struct rc_connection_state_cb_t {
  bool rc_state;
  bool bt_state;
  RawAddress raw_address;
};

struct rc_feature_cb_t {
  int feature;
  RawAddress raw_address;
};

static std::promise<rc_connection_state_cb_t> g_btrc_connection_state_promise;
static std::promise<rc_feature_cb_t> g_btrc_feature;

class BtifRcWithCallbacksTest : public BtifRcTest {
 protected:
  void SetUp() override {
    BtifRcTest::SetUp();
    btrc_ctrl_callbacks = default_btrc_ctrl_callbacks;
    init_ctrl(&btrc_ctrl_callbacks);
    jni_thread.StartUp();
    btrc_ctrl_callbacks.getrcfeatures_cb = [](const RawAddress& bd_addr,
                                              int features) {
      rc_feature_cb_t rc_feature = {
          .feature = features,
          .raw_address = bd_addr,
      };
      g_btrc_feature.set_value(rc_feature);
    };
  }

  void TearDown() override {
    jni_thread.ShutDown();
    bt_rc_ctrl_callbacks->getrcfeatures_cb = [](const RawAddress& bd_addr,
                                                int features) {};
    btrc_ctrl_callbacks = default_btrc_ctrl_callbacks;
    BtifRcTest::TearDown();
  }
};

TEST_F(BtifRcWithCallbacksTest, handle_rc_ctrl_features) {
  g_btrc_feature = std::promise<rc_feature_cb_t>();
  std::future<rc_feature_cb_t> future = g_btrc_feature.get_future();
  btif_rc_device_cb_t p_dev;

  p_dev.peer_tg_features =
      (BTA_AV_FEAT_RCTG | BTA_AV_FEAT_ADV_CTRL | BTA_AV_FEAT_RCCT |
       BTA_AV_FEAT_METADATA | BTA_AV_FEAT_VENDOR | BTA_AV_FEAT_BROWSE |
       BTA_AV_FEAT_COVER_ARTWORK);
  p_dev.rc_connected = true;

  handle_rc_ctrl_features(&p_dev);
  ASSERT_EQ(1, get_func_call_count("AVRC_BldCommand"));

  CHECK(std::future_status::ready == future.wait_for(std::chrono::seconds(2)));
  auto res = future.get();
  log::info("FEATURES:{}", res.feature);
  CHECK(res.feature == (BTRC_FEAT_ABSOLUTE_VOLUME | BTRC_FEAT_METADATA |
                        BTRC_FEAT_BROWSE | BTRC_FEAT_COVER_ARTWORK));
}

class BtifRcBrowseConnectionTest : public BtifRcTest {
 protected:
  void SetUp() override {
    BtifRcTest::SetUp();
    init_ctrl(&btrc_ctrl_callbacks);
    jni_thread.StartUp();
    btrc_ctrl_callbacks.connection_state_cb = [](bool rc_state, bool bt_state,
                                                 const RawAddress& bd_addr) {
      rc_connection_state_cb_t rc_connection_state = {
          .rc_state = rc_state,
          .bt_state = bt_state,
          .raw_address = bd_addr,
      };
      g_btrc_connection_state_promise.set_value(rc_connection_state);
    };
  }

  void TearDown() override {
    jni_thread.ShutDown();
    bt_rc_ctrl_callbacks->connection_state_cb =
        [](bool rc_state, bool bt_state, const RawAddress& bd_addr) {};
    BtifRcTest::TearDown();
  }
};

TEST_F(BtifRcBrowseConnectionTest, handle_rc_browse_connect) {
  g_btrc_connection_state_promise = std::promise<rc_connection_state_cb_t>();
  std::future<rc_connection_state_cb_t> future =
      g_btrc_connection_state_promise.get_future();

  tBTA_AV_RC_BROWSE_OPEN browse_data = {
      .rc_handle = 0,
      .peer_addr = {},
      .status = BTA_AV_SUCCESS,
  };

  btif_rc_cb.rc_multi_cb[0].rc_handle = 0;
  btif_rc_cb.rc_multi_cb[0].rc_addr = RawAddress::kEmpty;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_connected = false;

  /* process unit test  handle_rc_browse_connect */
  handle_rc_browse_connect(&browse_data);
  CHECK(std::future_status::ready == future.wait_for(std::chrono::seconds(2)));
  auto res = future.get();
  CHECK(res.bt_state == true);
}

class BtifRcConnectionTest : public BtifRcTest {
 protected:
  void SetUp() override {
    BtifRcTest::SetUp();
    init_ctrl(&btrc_ctrl_callbacks);
    jni_thread.StartUp();
    g_btrc_connection_state_promise = std::promise<rc_connection_state_cb_t>();
    g_btrc_connection_state_future =
        g_btrc_connection_state_promise.get_future();
    btrc_ctrl_callbacks.connection_state_cb = [](bool rc_state, bool bt_state,
                                                 const RawAddress& bd_addr) {
      rc_connection_state_cb_t rc_connection_state = {
          .rc_state = rc_state,
          .bt_state = bt_state,
          .raw_address = bd_addr,
      };
      g_btrc_connection_state_promise.set_value(rc_connection_state);
    };
  }

  void TearDown() override {
    jni_thread.ShutDown();
    bt_rc_ctrl_callbacks->connection_state_cb =
        [](bool rc_state, bool bt_state, const RawAddress& bd_addr) {};
    BtifRcTest::TearDown();
  }
  std::future<rc_connection_state_cb_t> g_btrc_connection_state_future;
};

TEST_F(BtifRcConnectionTest, btif_rc_connection_test) {}

TEST_F(BtifRcConnectionTest, handle_rc_browse_connect) {
  tBTA_AV_RC_BROWSE_OPEN browse_data = {
      .rc_handle = 0,
      .peer_addr = {},
      .status = BTA_AV_SUCCESS,
  };

  btif_rc_cb.rc_multi_cb[0].rc_handle = 0;
  btif_rc_cb.rc_multi_cb[0].rc_addr = RawAddress::kEmpty;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_connected = false;

  /* process unit test  handle_rc_browse_connect */
  handle_rc_browse_connect(&browse_data);
  CHECK(std::future_status::ready ==
        g_btrc_connection_state_future.wait_for(std::chrono::seconds(2)));
  auto res = g_btrc_connection_state_future.get();
  CHECK(res.bt_state == true);
}

TEST_F(BtifRcConnectionTest, btif_rc_check_pending_cmd) {
  btif_rc_cb.rc_multi_cb[0].rc_handle = 0xff;
  btif_rc_cb.rc_multi_cb[0].rc_addr = kDeviceAddress;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_connected = true;
  btif_rc_cb.rc_multi_cb[0].launch_cmd_pending |=
      (RC_PENDING_ACT_REG_VOL | RC_PENDING_ACT_GET_CAP |
       RC_PENDING_ACT_REPORT_CONN);

  btif_rc_check_pending_cmd(kDeviceAddress);
  ASSERT_EQ(1, get_func_call_count("AVRC_BldCommand"));

  CHECK(std::future_status::ready ==
        g_btrc_connection_state_future.wait_for(std::chrono::seconds(3)));
  auto res = g_btrc_connection_state_future.get();
  CHECK(res.rc_state == true);
}

TEST_F(BtifRcConnectionTest, bt_av_rc_open_evt) {
  btrc_ctrl_callbacks.get_cover_art_psm_cb = [](const RawAddress& /* bd_addr */,
                                                const uint16_t /* psm */) {};
  btrc_ctrl_callbacks.getrcfeatures_cb = [](const RawAddress& /* bd_addr */,
                                            int /* features */) {};

  /* handle_rc_connect  */
  tBTA_AV data = {
      .rc_open =
          {
              .rc_handle = 0,
              .cover_art_psm = 0,
              .peer_features = 0,
              .peer_ct_features = 0,
              .peer_tg_features = (BTA_AV_FEAT_METADATA | BTA_AV_FEAT_VENDOR |
                                   BTA_AV_FEAT_RCTG | BTA_AV_FEAT_RCCT),
              .peer_addr = kDeviceAddress,
              .status = BTA_AV_SUCCESS,
          },
  };
  btif_rc_cb.rc_multi_cb[0].rc_handle = 0;
  btif_rc_cb.rc_multi_cb[0].rc_addr = RawAddress::kEmpty;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_DISCONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_connected = false;

  btif_rc_handler(BTA_AV_RC_OPEN_EVT, &data);

  CHECK(btif_rc_cb.rc_multi_cb[data.rc_open.rc_handle].rc_connected == true);
  CHECK(btif_rc_cb.rc_multi_cb[data.rc_open.rc_handle].rc_state ==
        BTRC_CONNECTION_STATE_CONNECTED);

  CHECK(std::future_status::ready ==
        g_btrc_connection_state_future.wait_for(std::chrono::seconds(2)));
  auto res = g_btrc_connection_state_future.get();
  CHECK(res.rc_state == true);
}

class BtifTrackChangeCBTest : public BtifRcTest {
 protected:
  void SetUp() override {
    BtifRcTest::SetUp();
    init_ctrl(&btrc_ctrl_callbacks);
    jni_thread.StartUp();
    btrc_ctrl_callbacks.track_changed_cb = [](const RawAddress& bd_addr,
                       uint8_t num_attr, btrc_element_attr_val_t* p_attrs) {
      btif_rc_cb.rc_multi_cb[0].rc_addr = bd_addr;
    };
  }

  void TearDown() override {
    jni_thread.ShutDown();
    btrc_ctrl_callbacks.track_changed_cb = [](const RawAddress& bd_addr,
                       uint8_t num_attr, btrc_element_attr_val_t* p_attrs) {};
    BtifRcTest::TearDown();
  }
};

TEST_F(BtifTrackChangeCBTest, handle_get_metadata_attr_response) {
  tBTA_AV_META_MSG meta_msg = {
      .rc_handle = 0,
      .len = 0,
      .label = 0,
      .code{},
      .company_id = 0,
      .p_data = {},
      .p_msg = nullptr,
  };

  tAVRC_GET_ATTRS_RSP rsp = {
      .pdu = 0,
      .status = AVRC_STS_NO_ERROR,
      .opcode = 0,
      .num_attrs = 0,
      .p_attrs = nullptr,
  };

  btif_rc_cb.rc_multi_cb[0].rc_handle = 0;
  btif_rc_cb.rc_multi_cb[0].rc_addr = RawAddress::kEmpty;
  btif_rc_cb.rc_multi_cb[0].rc_state = BTRC_CONNECTION_STATE_CONNECTED;
  btif_rc_cb.rc_multi_cb[0].rc_connected = true;

  handle_get_metadata_attr_response(&meta_msg, &rsp);

  ASSERT_EQ(1, get_func_call_count("osi_free_and_reset"));
}
