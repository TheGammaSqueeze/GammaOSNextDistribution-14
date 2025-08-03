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

#include <com_android_bluetooth_flags.h>
#include <flag_macros.h>
#include <gtest/gtest.h>
#include <string.h>

#include <cstddef>
#include <cstdint>
#include <memory>
#include <string>

#include "common/strings.h"
#include "osi/include/allocator.h"
#include "stack/gatt/gatt_int.h"
#include "stack/include/bt_types.h"
#include "stack/include/gatt_api.h"
#include "stack/include/l2c_api.h"
#include "stack/sdp/internal/sdp_api.h"
#include "test/mock/mock_stack_sdp_legacy_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

#define TEST_BT com::android::bluetooth::flags

namespace bluetooth {
namespace legacy {
namespace testing {
BT_HDR* attp_build_value_cmd(uint16_t payload_size, uint8_t op_code,
                             uint16_t handle, uint16_t offset, uint16_t len,
                             uint8_t* p_data);
}  // namespace testing
}  // namespace legacy
}  // namespace bluetooth

class StackGattTest : public ::testing::Test {
 protected:
  void SetUp() override {
    test::mock::stack_sdp_legacy::api_.handle.SDP_CreateRecord =
        ::SDP_CreateRecord;
    test::mock::stack_sdp_legacy::api_.handle.SDP_AddServiceClassIdList =
        ::SDP_AddServiceClassIdList;
    test::mock::stack_sdp_legacy::api_.handle.SDP_AddAttribute =
        ::SDP_AddAttribute;
    test::mock::stack_sdp_legacy::api_.handle.SDP_AddProtocolList =
        ::SDP_AddProtocolList;
    test::mock::stack_sdp_legacy::api_.handle.SDP_AddUuidSequence =
        ::SDP_AddUuidSequence;
  }
  void TearDown() override { test::mock::stack_sdp_legacy::api_.handle = {}; }
};

namespace {

// Actual size of structure without compiler padding
size_t actual_sizeof_tGATT_REG() {
  return sizeof(bluetooth::Uuid) + sizeof(tGATT_CBACK) + sizeof(tGATT_IF) +
         sizeof(bool) + sizeof(uint8_t) + sizeof(bool);
}

void tGATT_DISC_RES_CB(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                       tGATT_DISC_RES* p_data) {}
void tGATT_DISC_CMPL_CB(uint16_t conn_id, tGATT_DISC_TYPE disc_type,
                        tGATT_STATUS status) {}
void tGATT_CMPL_CBACK(uint16_t conn_id, tGATTC_OPTYPE op, tGATT_STATUS status,
                      tGATT_CL_COMPLETE* p_data) {}
void tGATT_CONN_CBACK(tGATT_IF gatt_if, const RawAddress& bda, uint16_t conn_id,
                      bool connected, tGATT_DISCONN_REASON reason,
                      tBT_TRANSPORT transport) {}
void tGATT_REQ_CBACK(uint16_t conn_id, uint32_t trans_id, tGATTS_REQ_TYPE type,
                     tGATTS_DATA* p_data) {}
void tGATT_CONGESTION_CBACK(uint16_t conn_id, bool congested) {}
void tGATT_ENC_CMPL_CB(tGATT_IF gatt_if, const RawAddress& bda) {}
void tGATT_PHY_UPDATE_CB(tGATT_IF gatt_if, uint16_t conn_id, uint8_t tx_phy,
                         uint8_t rx_phy, tGATT_STATUS status) {}
void tGATT_CONN_UPDATE_CB(tGATT_IF gatt_if, uint16_t conn_id, uint16_t interval,
                          uint16_t latency, uint16_t timeout,
                          tGATT_STATUS status) {}

tGATT_CBACK gatt_callbacks = {
    .p_conn_cb = tGATT_CONN_CBACK,
    .p_cmpl_cb = tGATT_CMPL_CBACK,
    .p_disc_res_cb = tGATT_DISC_RES_CB,
    .p_disc_cmpl_cb = tGATT_DISC_CMPL_CB,
    .p_req_cb = tGATT_REQ_CBACK,
    .p_enc_cmpl_cb = tGATT_ENC_CMPL_CB,
    .p_congestion_cb = tGATT_CONGESTION_CBACK,
    .p_phy_update_cb = tGATT_PHY_UPDATE_CB,
    .p_conn_update_cb = tGATT_CONN_UPDATE_CB,
};

}  // namespace

TEST_F(StackGattTest, lifecycle_tGATT_REG) {
  {
    std::unique_ptr<tGATT_REG> reg0 = std::make_unique<tGATT_REG>();
    std::unique_ptr<tGATT_REG> reg1 = std::make_unique<tGATT_REG>();
    memset(reg0.get(), 0xff, sizeof(tGATT_REG));
    memset(reg1.get(), 0xff, sizeof(tGATT_REG));
    ASSERT_EQ(0, memcmp(reg0.get(), reg1.get(), sizeof(tGATT_REG)));

    memset(reg0.get(), 0x0, sizeof(tGATT_REG));
    memset(reg1.get(), 0x0, sizeof(tGATT_REG));
    ASSERT_EQ(0, memcmp(reg0.get(), reg1.get(), sizeof(tGATT_REG)));
  }

  {
    std::unique_ptr<tGATT_REG> reg0 = std::make_unique<tGATT_REG>();
    memset(reg0.get(), 0xff, sizeof(tGATT_REG));

    tGATT_REG reg1;
    memset(&reg1, 0xff, sizeof(tGATT_REG));

    // Clear the structures
    memset(reg0.get(), 0, sizeof(tGATT_REG));
    // Restore the complex structure after memset
    memset(&reg1.name, 0, sizeof(std::string));
    reg1 = {};
    ASSERT_EQ(0, memcmp(reg0.get(), &reg1, actual_sizeof_tGATT_REG()));
  }

  {
    tGATT_REG* reg0 = new tGATT_REG();
    tGATT_REG* reg1 = new tGATT_REG();
    memset(reg0, 0, sizeof(tGATT_REG));
    *reg1 = {};
    reg0->in_use = true;
    ASSERT_NE(0, memcmp(reg0, reg1, sizeof(tGATT_REG)));
    delete reg1;
    delete reg0;
  }
}

TEST_F(StackGattTest, gatt_init_free) {
  gatt_init();
  gatt_free();
}

TEST_F(StackGattTest, GATT_Register_Deregister) {
  gatt_init();

  // Gatt db profile always takes the first slot
  tGATT_IF apps[GATT_MAX_APPS - 1];

  for (int i = 0; i < GATT_MAX_APPS - 1; i++) {
    std::string name = bluetooth::common::StringFormat("name%02d", i);
    apps[i] = GATT_Register(bluetooth::Uuid::GetRandom(), name, &gatt_callbacks,
                            false);
  }

  for (int i = 0; i < GATT_MAX_APPS - 1; i++) {
    GATT_Deregister(apps[i]);
  }

  gatt_free();
}

TEST_F_WITH_FLAGS(StackGattTest, gatt_status_text,
                  REQUIRES_FLAGS_ENABLED(ACONFIG_FLAG(TEST_BT,
                                                      enumerate_gatt_errors))) {
  std::vector<std::pair<tGATT_STATUS, std::string>> statuses = {
      std::make_pair(GATT_SUCCESS, "GATT_SUCCESS"),  // Also GATT_ENCRYPED_MITM
      std::make_pair(GATT_INVALID_HANDLE, "GATT_INVALID_HANDLE"),
      std::make_pair(GATT_READ_NOT_PERMIT, "GATT_READ_NOT_PERMIT"),
      std::make_pair(GATT_WRITE_NOT_PERMIT, "GATT_WRITE_NOT_PERMIT"),
      std::make_pair(GATT_INVALID_PDU, "GATT_INVALID_PDU"),
      std::make_pair(GATT_INSUF_AUTHENTICATION, "GATT_INSUF_AUTHENTICATION"),
      std::make_pair(GATT_REQ_NOT_SUPPORTED, "GATT_REQ_NOT_SUPPORTED"),
      std::make_pair(GATT_INVALID_OFFSET, "GATT_INVALID_OFFSET"),
      std::make_pair(GATT_INSUF_AUTHORIZATION, "GATT_INSUF_AUTHORIZATION"),
      std::make_pair(GATT_PREPARE_Q_FULL, "GATT_PREPARE_Q_FULL"),
      std::make_pair(GATT_NOT_FOUND, "GATT_NOT_FOUND"),
      std::make_pair(GATT_NOT_LONG, "GATT_NOT_LONG"),
      std::make_pair(GATT_INSUF_KEY_SIZE, "GATT_INSUF_KEY_SIZE"),
      std::make_pair(GATT_INVALID_ATTR_LEN, "GATT_INVALID_ATTR_LEN"),
      std::make_pair(GATT_ERR_UNLIKELY, "GATT_ERR_UNLIKELY"),
      std::make_pair(GATT_INSUF_ENCRYPTION, "GATT_INSUF_ENCRYPTION"),
      std::make_pair(GATT_UNSUPPORT_GRP_TYPE, "GATT_UNSUPPORT_GRP_TYPE"),
      std::make_pair(GATT_INSUF_RESOURCE, "GATT_INSUF_RESOURCE"),
      std::make_pair(GATT_DATABASE_OUT_OF_SYNC, "GATT_DATABASE_OUT_OF_SYNC"),
      std::make_pair(GATT_VALUE_NOT_ALLOWED, "GATT_VALUE_NOT_ALLOWED"),
      std::make_pair(GATT_ILLEGAL_PARAMETER, "GATT_ILLEGAL_PARAMETER"),
      std::make_pair(GATT_NO_RESOURCES, "GATT_NO_RESOURCES"),
      std::make_pair(GATT_INTERNAL_ERROR, "GATT_INTERNAL_ERROR"),
      std::make_pair(GATT_WRONG_STATE, "GATT_WRONG_STATE"),
      std::make_pair(GATT_DB_FULL, "GATT_DB_FULL"),
      std::make_pair(GATT_BUSY, "GATT_BUSY"),
      std::make_pair(GATT_ERROR, "GATT_ERROR"),
      std::make_pair(GATT_CMD_STARTED, "GATT_CMD_STARTED"),
      std::make_pair(GATT_PENDING, "GATT_PENDING"),
      std::make_pair(GATT_AUTH_FAIL, "GATT_AUTH_FAIL"),
      std::make_pair(GATT_INVALID_CFG, "GATT_INVALID_CFG"),
      std::make_pair(GATT_SERVICE_STARTED, "GATT_SERVICE_STARTED"),
      std::make_pair(GATT_ENCRYPED_NO_MITM, "GATT_ENCRYPED_NO_MITM"),
      std::make_pair(GATT_NOT_ENCRYPTED, "GATT_NOT_ENCRYPTED"),
      std::make_pair(GATT_CONGESTED, "GATT_CONGESTED"),
      std::make_pair(GATT_DUP_REG, "GATT_DUP_REG"),
      std::make_pair(GATT_ALREADY_OPEN, "GATT_ALREADY_OPEN"),
      std::make_pair(GATT_CANCEL, "GATT_CANCEL"),
      std::make_pair(GATT_CONNECTION_TIMEOUT, "GATT_CONNECTION_TIMEOUT"),
      std::make_pair(GATT_CCC_CFG_ERR, "GATT_CCC_CFG_ERR"),
      std::make_pair(GATT_PRC_IN_PROGRESS, "GATT_PRC_IN_PROGRESS"),
      std::make_pair(GATT_OUT_OF_RANGE, "GATT_OUT_OF_RANGE"),
  };
  for (const auto& status : statuses) {
    ASSERT_STREQ(status.second.c_str(), gatt_status_text(status.first).c_str());
  }
  // Typical max value is already classified so use arbitrary unused one.
  auto unknown = base::StringPrintf("UNKNOWN[%hhu]", 0xfc);
  ASSERT_STREQ(unknown.c_str(),
               gatt_status_text(static_cast<tGATT_STATUS>(0xfc)).c_str());
}

const static std::map<uint8_t, uint16_t> gatt_min_value_cmd_size{
    {GATT_RSP_READ_BY_TYPE, 4},  // op_code (1) + pair_len (1) + handle (2)
    {GATT_RSP_READ_BLOB, 1},     // op_code (1)
    {GATT_RSP_READ, 1},
    {GATT_REQ_PREPARE_WRITE, 5},     // op_code (1) + handle (2) + offset (2)
    {GATT_REQ_READ_BY_GRP_TYPE, 3},  // op_code + handle
};

static void attp_build_value_cmd_test_with_p_data(uint16_t payload_size,
                                                  uint8_t op_code,
                                                  uint16_t handle,
                                                  uint16_t offset, uint16_t len,
                                                  uint8_t* p_data) {
  uint16_t min_payload_size;
  uint8_t pair_len_read;
  uint16_t offset_read = 0;
  uint16_t handle_read = 0;

  ASSERT_TRUE(gatt_min_value_cmd_size.find(op_code) !=
              gatt_min_value_cmd_size.end());
  min_payload_size = gatt_min_value_cmd_size.at(op_code);

  ASSERT_GE(payload_size, min_payload_size);

  BT_HDR* ret = bluetooth::legacy::testing::attp_build_value_cmd(
      payload_size, op_code, handle, offset, len, p_data);

  ASSERT_NE(ret, nullptr);
  uint8_t* p = (uint8_t*)(ret + 1) + L2CAP_MIN_OFFSET;

  uint8_t op_code_read;
  STREAM_TO_UINT8(op_code_read, p);
  ASSERT_EQ(op_code_read, op_code);

  if (op_code == GATT_RSP_READ_BY_TYPE) {
    STREAM_TO_UINT8(pair_len_read, p);

    STREAM_TO_UINT16(handle_read, p);
    ASSERT_EQ(handle_read, handle);
  } else if (op_code == GATT_RSP_READ_BLOB || op_code == GATT_RSP_READ) {
    ;
  } else if (op_code == GATT_REQ_PREPARE_WRITE ||
             op_code == GATT_RSP_PREPARE_WRITE) {
    STREAM_TO_UINT16(handle_read, p);
    ASSERT_EQ(handle_read, handle);
    STREAM_TO_UINT16(offset_read, p);
    ASSERT_EQ(offset_read, offset);
  } else {
    STREAM_TO_UINT16(handle_read, p);
    ASSERT_EQ(handle_read, handle);
  }

  uint16_t actual_payload_size;
  uint8_t pair_len;

  if (p_data != nullptr) {
    if (min_payload_size + len <= payload_size) {
      actual_payload_size = min_payload_size + len;
      pair_len = len + 2;
    } else {
      actual_payload_size = payload_size;
      pair_len = payload_size - min_payload_size + 2;
    }

    size_t cmp_size = actual_payload_size - min_payload_size;
    int ret = memcmp(p_data, p, cmp_size);
    ASSERT_EQ(ret, 0);

  } else {
    pair_len = len + 2;
    actual_payload_size = min_payload_size;
  }

  ASSERT_EQ(ret->len, actual_payload_size);

  if (op_code == GATT_RSP_READ_BY_TYPE) {
    ASSERT_EQ(pair_len_read, pair_len);
  }

  osi_free_and_reset((void**)&ret);
}

TEST_F(StackGattTest, attp_build_value_cmd_p_data_null) {
  for (auto it = gatt_min_value_cmd_size.begin();
       it != gatt_min_value_cmd_size.end(); it++) {
    attp_build_value_cmd_test_with_p_data(it->second, it->first, 0x1, 0x1234, 0,
                                          nullptr);
  }
}

TEST_F(StackGattTest, attp_build_value_cmd_no_p_data) {
  for (auto it = gatt_min_value_cmd_size.begin();
       it != gatt_min_value_cmd_size.end(); it++) {
    attp_build_value_cmd_test_with_p_data(it->second, it->first, 0x1, 0x1234, 3,
                                          (uint8_t*)"abc");
  }
}

TEST_F(StackGattTest, attp_build_value_cmd_partial_p_data) {
  for (auto it = gatt_min_value_cmd_size.begin();
       it != gatt_min_value_cmd_size.end(); it++) {
    attp_build_value_cmd_test_with_p_data(it->second + 1, it->first, 0x1,
                                          0x1234, 3, (uint8_t*)"abc");
  }
}

TEST_F(StackGattTest, attp_build_value_cmd_full_p_data) {
  for (auto it = gatt_min_value_cmd_size.begin();
       it != gatt_min_value_cmd_size.end(); it++) {
    attp_build_value_cmd_test_with_p_data(it->second + 5, it->first, 0x1,
                                          0x1234, 3, (uint8_t*)"abc");
  }
}

static void attp_build_value_cmd_small_payload_size(uint8_t op_code) {
  // payload size too small
  uint16_t offset_0 = 0;
  uint16_t handle = 0x1;
  uint16_t len = 0;
  uint8_t* p_data = nullptr;
  uint16_t test_payload_size = gatt_min_value_cmd_size.at(op_code) - 1;

  ASSERT_TRUE(gatt_min_value_cmd_size.find(op_code) !=
              gatt_min_value_cmd_size.end());
  test_payload_size = gatt_min_value_cmd_size.at(op_code) - 1;

  BT_HDR* ret = bluetooth::legacy::testing::attp_build_value_cmd(
      test_payload_size, op_code, handle, offset_0, len, p_data);

  ASSERT_EQ(ret, nullptr);
}

TEST_F(StackGattTest,
       attp_build_value_cmd_test_payload_size_less_than_mimimal) {
  for (auto it = gatt_min_value_cmd_size.begin();
       it != gatt_min_value_cmd_size.end(); it++) {
    attp_build_value_cmd_small_payload_size(it->first);
  }
}

TEST_F(StackGattTest, attp_build_value_cmd_read_by_type_test_long_data) {
  // p_data too large and does not fit in pair_len
  // only for GATT_RSP_READ_BY_TYPE
  uint16_t offset_0 = 0;
  uint16_t handle = 0x1;
  const uint8_t op_code = GATT_RSP_READ_BY_TYPE;

  const int data_size = 255;
  uint16_t payload_size = data_size + 4;

  uint8_t data[data_size];

  for (int i = 0; i < data_size; i++) {
    data[i] = 'A';
  }

  BT_HDR* ret = bluetooth::legacy::testing::attp_build_value_cmd(
      payload_size, op_code, handle, offset_0, data_size, data);
  ASSERT_EQ(ret, nullptr);
}
