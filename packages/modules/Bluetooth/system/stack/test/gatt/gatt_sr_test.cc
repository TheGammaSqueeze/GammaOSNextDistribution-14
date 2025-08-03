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

#include <base/logging.h>
#include <gtest/gtest.h>
#include <stdio.h>

#include <cstdint>

#include "include/check.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/main_thread.h"
#undef LOG_TAG
#include "stack/gatt/gatt_sr.cc"
#include "types/raw_address.h"

#define MAX_UINT16 ((uint16_t)0xffff)

tGATT_CB gatt_cb;

namespace {

struct TestMutables {
  struct {
    uint8_t op_code_;
  } attp_build_sr_msg;
  struct {
    uint16_t conn_id_{0};
    uint32_t trans_id_{0};
    tGATTS_REQ_TYPE type_{0xff};
    tGATTS_DATA data_;
  } application_request_callback;
  struct {
    int access_count_{0};
    tGATT_STATUS return_status_{GATT_SUCCESS};
  } gatts_write_attr_perm_check;
};

TestMutables test_state_;
}  // namespace

namespace connection_manager {
bool background_connect_remove(uint8_t app_id, const RawAddress& address) {
  return false;
}
bool direct_connect_remove(uint8_t app_id, const RawAddress& address,
                           bool connection_timeout) {
  return false;
}
bool is_background_connection(const RawAddress& address) { return false; }

}  // namespace connection_manager

BT_HDR* attp_build_sr_msg(tGATT_TCB& tcb, uint8_t op_code, tGATT_SR_MSG* p_msg,
                          uint16_t payload_size) {
  test_state_.attp_build_sr_msg.op_code_ = op_code;
  return nullptr;
}
tGATT_STATUS attp_send_cl_confirmation_msg(tGATT_TCB& tcb, uint16_t cid) {
  return GATT_SUCCESS;
}
tGATT_STATUS attp_send_cl_msg(tGATT_TCB& tcb, tGATT_CLCB* p_clcb,
                              uint8_t op_code, tGATT_CL_MSG* p_msg) {
  return GATT_SUCCESS;
}
tGATT_STATUS attp_send_sr_msg(tGATT_TCB& tcb, uint16_t cid, BT_HDR* p_msg) {
  return GATT_SUCCESS;
}

void gatt_act_discovery(tGATT_CLCB* p_clcb) {}
bool gatt_disconnect(tGATT_TCB* p_tcb) { return false; }
tGATT_CH_STATE gatt_get_ch_state(tGATT_TCB* p_tcb) { return GATT_CH_CLOSE; }
tGATT_STATUS gatts_db_read_attr_value_by_type(
    tGATT_TCB& tcb, uint16_t cid, tGATT_SVC_DB* p_db, uint8_t op_code,
    BT_HDR* p_rsp, uint16_t s_handle, uint16_t e_handle, const Uuid& type,
    uint16_t* p_len, tGATT_SEC_FLAG sec_flag, uint8_t key_size,
    uint32_t trans_id, uint16_t* p_cur_handle) {
  return GATT_SUCCESS;
}
void gatt_set_ch_state(tGATT_TCB* p_tcb, tGATT_CH_STATE ch_state) {}
Uuid* gatts_get_service_uuid(tGATT_SVC_DB* p_db) { return nullptr; }
tGATT_STATUS GATTS_HandleValueIndication(uint16_t conn_id, uint16_t attr_handle,
                                         uint16_t val_len, uint8_t* p_val) {
  return GATT_SUCCESS;
}
tGATT_STATUS gatts_read_attr_perm_check(tGATT_SVC_DB* p_db, bool is_long,
                                        uint16_t handle,
                                        tGATT_SEC_FLAG sec_flag,
                                        uint8_t key_size) {
  return GATT_SUCCESS;
}
tGATT_STATUS gatts_read_attr_value_by_handle(
    tGATT_TCB& tcb, uint16_t cid, tGATT_SVC_DB* p_db, uint8_t op_code,
    uint16_t handle, uint16_t offset, uint8_t* p_value, uint16_t* p_len,
    uint16_t mtu, tGATT_SEC_FLAG sec_flag, uint8_t key_size,
    uint32_t trans_id) {
  return GATT_SUCCESS;
}
tGATT_STATUS gatts_write_attr_perm_check(tGATT_SVC_DB* p_db, uint8_t op_code,
                                         uint16_t handle, uint16_t offset,
                                         uint8_t* p_data, uint16_t len,
                                         tGATT_SEC_FLAG sec_flag,
                                         uint8_t key_size) {
  test_state_.gatts_write_attr_perm_check.access_count_++;
  return test_state_.gatts_write_attr_perm_check.return_status_;
}
void gatt_update_app_use_link_flag(tGATT_IF gatt_if, tGATT_TCB* p_tcb,
                                   bool is_add, bool check_acl_link) {}
bluetooth::common::MessageLoopThread* get_main_thread() { return nullptr; }
void l2cble_set_fixed_channel_tx_data_length(const RawAddress& remote_bda,
                                             uint16_t fix_cid,
                                             uint16_t tx_mtu) {}
void L2CA_SetLeFixedChannelTxDataLength(const RawAddress& remote_bda,
                                        uint16_t fix_cid, uint16_t tx_mtu) {}
void ApplicationRequestCallback(uint16_t conn_id, uint32_t trans_id,
                                tGATTS_REQ_TYPE type, tGATTS_DATA* p_data) {
  test_state_.application_request_callback.conn_id_ = conn_id;
  test_state_.application_request_callback.trans_id_ = trans_id;
  test_state_.application_request_callback.type_ = type;
  test_state_.application_request_callback.data_ = *p_data;
}

bool gatt_sr_is_cl_change_aware(tGATT_TCB& tcb) { return false; }
void gatt_sr_init_cl_status(tGATT_TCB& p_tcb) {}
void gatt_sr_update_cl_status(tGATT_TCB& p_tcb, bool chg_aware) {
  p_tcb.is_robust_cache_change_aware = chg_aware;
}

/**
 * Test class to test selected functionality in stack/gatt/gatt_sr.cc
 */
namespace {
uint16_t kHandle = 1;
bt_gatt_db_attribute_type_t kGattCharacteristicType = BTGATT_DB_CHARACTERISTIC;
}  // namespace
class GattSrTest : public ::testing::Test {
 protected:
  void SetUp() override {
    memset(&tcb_, 0, sizeof(tcb_));
    memset(&el_, 0, sizeof(el_));

    tcb_.trans_id = 0x12345677;
    tcb_.att_lcid = L2CAP_ATT_CID;
    el_.gatt_if = 1;
    gatt_cb.cl_rcb[el_.gatt_if - 1].in_use = true;
    gatt_cb.cl_rcb[el_.gatt_if - 1].app_cb.p_req_cb =
        ApplicationRequestCallback;

    test_state_ = TestMutables();
  }

  tGATT_TCB tcb_;
  tGATT_SRV_LIST_ELEM el_;
};

/* Server Robust Caching Test */
class GattSrRobustCachingTest : public ::testing::Test {
 protected:
  void SetUp() override {
    memset(&tcb_, 0, sizeof(tcb_));

    default_length_ = 2;
    memset(default_data_, 0, sizeof(default_data_));

    gatt_cb.handle_of_database_hash = 0x0010;
  }

  tGATT_TCB tcb_;
  uint16_t default_length_;
  uint8_t default_data_[2];
};

TEST_F(GattSrTest, gatts_process_write_req_request_prepare_write_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_REQ_PREPARE_WRITE, 0, nullptr,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest,
       gatts_process_write_req_request_prepare_write_max_len_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_REQ_PREPARE_WRITE, MAX_UINT16, nullptr,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest,
       gatts_process_write_req_request_prepare_write_zero_len_max_data) {
  uint8_t max_mem[MAX_UINT16];
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_REQ_PREPARE_WRITE, 0, max_mem,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_request_prepare_write_typical) {
  uint8_t p_data[2] = {0x34, 0x12};
  uint16_t length = static_cast<uint16_t>(sizeof(p_data) / sizeof(p_data[0]));
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_REQ_PREPARE_WRITE, length, p_data,
                          kGattCharacteristicType);

  CHECK(test_state_.gatts_write_attr_perm_check.access_count_ == 1);
  CHECK(test_state_.application_request_callback.conn_id_ == el_.gatt_if);
  CHECK(test_state_.application_request_callback.trans_id_ == 0x12345678);
  CHECK(test_state_.application_request_callback.type_ ==
        GATTS_REQ_TYPE_WRITE_CHARACTERISTIC);
  CHECK(test_state_.application_request_callback.data_.write_req.offset ==
        0x1234);
  CHECK(test_state_.application_request_callback.data_.write_req.is_prep ==
        true);
  CHECK(test_state_.application_request_callback.data_.write_req.len == 0);
}

TEST_F(GattSrTest, gatts_process_write_req_signed_command_write_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_SIGN_CMD_WRITE, 0, nullptr,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest,
       gatts_process_write_req_signed_command_write_max_len_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_SIGN_CMD_WRITE, MAX_UINT16, nullptr,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest,
       gatts_process_write_req_signed_command_write_zero_len_max_data) {
  uint8_t max_mem[MAX_UINT16];
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_SIGN_CMD_WRITE, 0, max_mem,
                          kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_signed_command_write_typical) {
  static constexpr size_t kDataLength = 4;
  uint8_t p_data[GATT_AUTH_SIGN_LEN + kDataLength] = {
      0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
      0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x01};
  uint16_t length = static_cast<uint16_t>(sizeof(p_data) / sizeof(p_data[0]));
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle,
                          GATT_SIGN_CMD_WRITE, length, p_data,
                          kGattCharacteristicType);

  CHECK(test_state_.gatts_write_attr_perm_check.access_count_ == 1);
  CHECK(test_state_.application_request_callback.conn_id_ == el_.gatt_if);
  CHECK(test_state_.application_request_callback.trans_id_ == 0x12345678);
  CHECK(test_state_.application_request_callback.type_ ==
        GATTS_REQ_TYPE_WRITE_CHARACTERISTIC);
  CHECK(test_state_.application_request_callback.data_.write_req.offset == 0x0);
  CHECK(test_state_.application_request_callback.data_.write_req.is_prep ==
        false);
  CHECK(test_state_.application_request_callback.data_.write_req.len ==
        kDataLength);
}

TEST_F(GattSrTest, gatts_process_write_req_command_write_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_CMD_WRITE, 0,
                          nullptr, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_command_write_max_len_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_CMD_WRITE,
                          MAX_UINT16, nullptr, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_command_write_zero_len_max_data) {
  uint8_t max_mem[MAX_UINT16];
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_CMD_WRITE, 0,
                          max_mem, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_command_write_typical) {
  uint8_t p_data[16] = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                        0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x01};
  uint16_t length = static_cast<uint16_t>(sizeof(p_data) / sizeof(p_data[0]));
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_CMD_WRITE,
                          length, p_data, kGattCharacteristicType);

  CHECK(test_state_.gatts_write_attr_perm_check.access_count_ == 1);
  CHECK(test_state_.application_request_callback.conn_id_ == el_.gatt_if);
  CHECK(test_state_.application_request_callback.trans_id_ == 0x12345678);
  CHECK(test_state_.application_request_callback.type_ ==
        GATTS_REQ_TYPE_WRITE_CHARACTERISTIC);
  CHECK(test_state_.application_request_callback.data_.write_req.offset == 0x0);
  CHECK(test_state_.application_request_callback.data_.write_req.is_prep ==
        false);
  CHECK(test_state_.application_request_callback.data_.write_req.len == length);
}

TEST_F(GattSrTest, gatts_process_write_req_request_write_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_REQ_WRITE, 0,
                          nullptr, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_request_write_max_len_no_data) {
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_REQ_WRITE,
                          MAX_UINT16, nullptr, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_request_write_zero_len_max_data) {
  uint8_t max_mem[MAX_UINT16];
  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_REQ_WRITE, 0,
                          max_mem, kGattCharacteristicType);
}

TEST_F(GattSrTest, gatts_process_write_req_request_write_typical) {
  uint8_t p_data[16] = {0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                        0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff, 0x01};
  uint16_t length = static_cast<uint16_t>(sizeof(p_data) / sizeof(p_data[0]));

  gatts_process_write_req(tcb_, L2CAP_ATT_CID, el_, kHandle, GATT_REQ_WRITE,
                          length, p_data, kGattCharacteristicType);

  CHECK(test_state_.gatts_write_attr_perm_check.access_count_ == 1);
  CHECK(test_state_.application_request_callback.conn_id_ == el_.gatt_if);
  CHECK(test_state_.application_request_callback.trans_id_ == 0x12345678);
  CHECK(test_state_.application_request_callback.type_ ==
        GATTS_REQ_TYPE_WRITE_CHARACTERISTIC);
  CHECK(test_state_.application_request_callback.data_.write_req.offset == 0x0);
  CHECK(test_state_.application_request_callback.data_.write_req.is_prep ==
        false);
  CHECK(test_state_.application_request_callback.data_.write_req.len == length);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_by_grp_type) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BY_GRP_TYPE, default_length_,
      default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_find_type_value) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_FIND_TYPE_VALUE, default_length_,
      default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_find_info) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_FIND_INFO, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_by_type_parse_failed) {
  // INVALID_PDU
  uint16_t len = 4;
  uint8_t p_data[4] = {0x00, 0x02, 0x14, 0x02};
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BY_TYPE, len, p_data);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_by_type_db_hash_uuid) {
  // ATT_READ_BY_TYPE_REQ(0x0001, 0x0010, 0x2B2A)
  uint16_t len = 6;
  uint8_t p_data[6] = {0x01, 0x00, 0x10, 0x00, 0x2A, 0x2B};
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BY_TYPE, len, p_data);

  ASSERT_FALSE(should_ignore);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_by_type_wrong_range) {
  // ATT_READ_BY_TYPE_REQ(0x0200, 0x0214, 0x2803)
  uint16_t len = 6;
  uint8_t p_data[6] = {0x00, 0x02, 0x14, 0x02, 0x2A, 0x28};
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BY_TYPE, len, p_data);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_by_type_other_uuid) {
  // ATT_READ_BY_TYPE_REQ(0x0200, 0x0214, 0x2803)
  uint16_t len = 6;
  uint8_t p_data[6] = {0x00, 0x02, 0x14, 0x02, 0x03, 0x28};
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BY_TYPE, len, p_data);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_parse_failed) {
  // INVALID_PDU
  uint8_t p_data[1] = {0x02};
  uint16_t len = 1;
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(tcb_, L2CAP_ATT_CID,
                                                    GATT_REQ_READ, len, p_data);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_db_hash_handle) {
  // ATT_READ_REQ(0x0010)
  uint8_t p_data[2] = {0x10, 0x00};
  uint16_t len = 2;
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(tcb_, L2CAP_ATT_CID,
                                                    GATT_REQ_READ, len, p_data);

  ASSERT_FALSE(should_ignore);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_other_handle) {
  // ATT_READ_REQ(0x0002)
  uint8_t p_data[2] = {0x02, 0x00};
  uint16_t len = 2;
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(tcb_, L2CAP_ATT_CID,
                                                    GATT_REQ_READ, len, p_data);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_blob) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_BLOB, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_read_multi) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_READ_MULTI, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_write) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_WRITE, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_cmd_write) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_CMD_WRITE, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_FALSE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_sign_cmd_write) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_SIGN_CMD_WRITE, default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_FALSE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_prepare_write) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore =
      gatts_process_db_out_of_sync(tcb_, L2CAP_ATT_CID, GATT_REQ_PREPARE_WRITE,
                                   default_length_, default_data_);

  ASSERT_TRUE(should_ignore);
  ASSERT_TRUE(tcb_.is_robust_cache_change_aware);
}

TEST_F(GattSrRobustCachingTest, gatts_process_db_out_of_sync_for_gatt_req_mtu) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_MTU, default_length_, default_data_);

  ASSERT_FALSE(should_ignore);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_req_exec_write) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore = gatts_process_db_out_of_sync(
      tcb_, L2CAP_ATT_CID, GATT_REQ_EXEC_WRITE, default_length_, default_data_);

  ASSERT_FALSE(should_ignore);
}

TEST_F(GattSrRobustCachingTest,
       gatts_process_db_out_of_sync_for_gatt_handle_value_conf) {
  tcb_.is_robust_cache_change_aware = false;

  bool should_ignore =
      gatts_process_db_out_of_sync(tcb_, L2CAP_ATT_CID, GATT_HANDLE_VALUE_CONF,
                                   default_length_, default_data_);

  ASSERT_FALSE(should_ignore);
}
