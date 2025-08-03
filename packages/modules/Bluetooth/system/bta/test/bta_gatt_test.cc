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

#include <gtest/gtest.h>
#include <string.h>

#include <cstdint>
#include <map>
#include <memory>
#include <string>

#include "bta/gatt/bta_gattc_int.h"
#include "common/message_loop_thread.h"
#include "osi/include/allocator.h"
#include "stack/gatt/gatt_int.h"
#include "test/common/mock_functions.h"

namespace param {
struct {
  uint16_t conn_id;
  tGATT_STATUS status;
  uint16_t handle;
  uint16_t len;
  uint8_t* value;
  void* data;
} bta_gatt_read_complete_callback;
}  // namespace param
void bta_gatt_read_complete_callback(uint16_t conn_id, tGATT_STATUS status,
                                     uint16_t handle, uint16_t len,
                                     uint8_t* value, void* data) {
  param::bta_gatt_read_complete_callback.conn_id = conn_id;
  param::bta_gatt_read_complete_callback.status = status;
  param::bta_gatt_read_complete_callback.handle = handle;
  param::bta_gatt_read_complete_callback.len = len;
  param::bta_gatt_read_complete_callback.value = value;
  param::bta_gatt_read_complete_callback.data = data;
}

namespace param {
struct {
  uint16_t conn_id;
  tGATT_STATUS status;
  uint16_t handle;
  uint16_t len;
  const uint8_t* value;
  void* data;
} bta_gatt_write_complete_callback;
}  // namespace param

void bta_gatt_write_complete_callback(uint16_t conn_id, tGATT_STATUS status,
                                      uint16_t handle, uint16_t len,
                                      const uint8_t* value, void* data) {
  param::bta_gatt_write_complete_callback.conn_id = conn_id;
  param::bta_gatt_write_complete_callback.status = status;
  param::bta_gatt_write_complete_callback.handle = handle;
  param::bta_gatt_write_complete_callback.len = len;
  param::bta_gatt_write_complete_callback.value = value;
  param::bta_gatt_write_complete_callback.data = data;
}

namespace param {
struct {
  uint16_t conn_id;
  tGATT_STATUS status;
  void* data;
} bta_gatt_configure_mtu_complete_callback;
}  // namespace param

void bta_gatt_configure_mtu_complete_callback(uint16_t conn_id,
                                              tGATT_STATUS status, void* data) {
  param::bta_gatt_configure_mtu_complete_callback.conn_id = conn_id;
  param::bta_gatt_configure_mtu_complete_callback.status = status;
  param::bta_gatt_configure_mtu_complete_callback.data = data;
}

namespace param {
struct {
  tBTA_GATTC_EVT event;
  tBTA_GATTC* p_data;
} bta_gattc_event_complete_callback;
}  // namespace param

void bta_gattc_event_complete_callback(tBTA_GATTC_EVT event,
                                       tBTA_GATTC* p_data) {
  param::bta_gattc_event_complete_callback.event = event;
  param::bta_gattc_event_complete_callback.p_data = p_data;
}

class BtaGattTest : public ::testing::Test {
 protected:
  void SetUp() override {
    reset_mock_function_count_map();
    param::bta_gatt_read_complete_callback = {};
    param::bta_gatt_write_complete_callback = {};
    param::bta_gatt_configure_mtu_complete_callback = {};
    param::bta_gattc_event_complete_callback = {};
  }

  void TearDown() override {}

  tBTA_GATTC_RCB app_control_block = {
      .p_cback = bta_gattc_event_complete_callback,
  };

  tGATT_CL_COMPLETE gatt_cl_complete = {
      .att_value =
          {
              .conn_id = 1,
              .handle = 2,
              .offset = 3,
              .len = 4,  // length of value below
              .auth_req = GATT_AUTH_REQ_NONE,
              .value = {10, 11, 12, 13},
          },
  };

  tBTA_GATTC_SERV service_control_block = {
      .mtu = 456,
  };
  tBTA_GATTC_DATA command_queue;

  tBTA_GATTC_CLCB client_channel_control_block = {
      .bta_conn_id = 456,
      .p_rcb = &app_control_block,
      .p_srcb = &service_control_block,
      .p_q_cmd = &command_queue,
  };
};

TEST_F(BtaGattTest, bta_gattc_op_cmpl_read) {
  command_queue = {
      .api_read =  // tBTA_GATTC_API_READ
      {
          .hdr =
              {
                  .event = BTA_GATTC_API_READ_EVT,
              },
          .handle = 123,
          .read_cb = bta_gatt_read_complete_callback,
          .read_cb_data = static_cast<void*>(this),
      },
  };

  client_channel_control_block.p_q_cmd = &command_queue;

  tBTA_GATTC_DATA data = {
      .op_cmpl =
          {
              .op_code = GATTC_OPTYPE_READ,
              .status = GATT_OUT_OF_RANGE,
              .p_cmpl = &gatt_cl_complete,
          },
  };

  bta_gattc_op_cmpl(&client_channel_control_block, &data);
  ASSERT_EQ(1, get_func_call_count("osi_free_and_reset"));
  ASSERT_EQ(456, param::bta_gatt_read_complete_callback.conn_id);
  ASSERT_EQ(GATT_OUT_OF_RANGE, param::bta_gatt_read_complete_callback.status);
  ASSERT_EQ(123, param::bta_gatt_read_complete_callback.handle);
  ASSERT_EQ(4, param::bta_gatt_read_complete_callback.len);
  ASSERT_EQ(10, param::bta_gatt_read_complete_callback.value[0]);
  ASSERT_EQ(this, param::bta_gatt_read_complete_callback.data);
}

TEST_F(BtaGattTest, bta_gattc_op_cmpl_write) {
  command_queue = {
      .api_write =  // tBTA_GATTC_API_WRITE
      {
          .hdr =
              {
                  .event = BTA_GATTC_API_WRITE_EVT,
              },
          .handle = 123,
          .write_cb = bta_gatt_write_complete_callback,
          .write_cb_data = static_cast<void*>(this),
      },
  };

  client_channel_control_block.p_q_cmd = &command_queue;

  tBTA_GATTC_DATA data = {
      .op_cmpl =
          {
              .op_code = GATTC_OPTYPE_WRITE,
              .status = GATT_OUT_OF_RANGE,
              .p_cmpl = &gatt_cl_complete,
          },
  };

  bta_gattc_op_cmpl(&client_channel_control_block, &data);
  ASSERT_EQ(1, get_func_call_count("osi_free_and_reset"));
  ASSERT_EQ(456, param::bta_gatt_write_complete_callback.conn_id);
  ASSERT_EQ(2, param::bta_gatt_write_complete_callback.handle);
  ASSERT_EQ(GATT_OUT_OF_RANGE, param::bta_gatt_write_complete_callback.status);
  ASSERT_EQ(this, param::bta_gatt_write_complete_callback.data);
}

TEST_F(BtaGattTest, bta_gattc_op_cmpl_config) {
  command_queue = {
      .api_mtu =  // tBTA_GATTC_API_CFG_MTU
      {
          .hdr =
              {
                  .event = BTA_GATTC_API_CFG_MTU_EVT,
              },
          .mtu_cb = bta_gatt_configure_mtu_complete_callback,
          .mtu_cb_data = static_cast<void*>(this),
      },
  };

  client_channel_control_block.p_q_cmd = &command_queue;

  tBTA_GATTC_DATA data = {
      .op_cmpl =
          {
              .op_code = GATTC_OPTYPE_CONFIG,
              .status = GATT_PRC_IN_PROGRESS,
          },
  };

  bta_gattc_op_cmpl(&client_channel_control_block, &data);
  ASSERT_EQ(1, get_func_call_count("osi_free_and_reset"));
  ASSERT_EQ(456, param::bta_gatt_configure_mtu_complete_callback.conn_id);

  ASSERT_EQ(GATT_PRC_IN_PROGRESS,
            param::bta_gatt_configure_mtu_complete_callback.status);
  ASSERT_EQ(this, param::bta_gatt_configure_mtu_complete_callback.data);
}

TEST_F(BtaGattTest, bta_gattc_op_cmpl_execute) {
  command_queue = {
      .api_exec =  // tBTA_GATTC_API_EXEC
      {
          .hdr =
              {
                  .event = BTA_GATTC_API_EXEC_EVT,
              },
      },
  };

  client_channel_control_block.p_q_cmd = &command_queue;

  tBTA_GATTC_DATA data = {
      .op_cmpl =
          {
              .op_code = GATTC_OPTYPE_EXE_WRITE,
          },
  };

  bta_gattc_op_cmpl(&client_channel_control_block, &data);
  ASSERT_EQ(BTA_GATTC_EXEC_EVT, param::bta_gattc_event_complete_callback.event);
  ASSERT_EQ(1, get_func_call_count("osi_free_and_reset"));
}

TEST_F(BtaGattTest, bta_gattc_op_cmpl_read_interrupted) {
  command_queue = {
      .api_read =  // tBTA_GATTC_API_READ
      {
          .hdr =
              {
                  .event = BTA_GATTC_API_READ_EVT,
              },
          .handle = 123,
          .read_cb = bta_gatt_read_complete_callback,
          .read_cb_data = static_cast<void*>(this),
      },
  };

  client_channel_control_block.p_q_cmd = &command_queue;

  // Create interrupt condition
  client_channel_control_block.auto_update = BTA_GATTC_DISC_WAITING;
  client_channel_control_block.p_srcb->srvc_hdl_chg = 1;

  tBTA_GATTC_DATA data = {
      .op_cmpl =
          {
              .op_code = GATTC_OPTYPE_READ,
              .status = GATT_OUT_OF_RANGE,
              .p_cmpl = &gatt_cl_complete,
          },
  };

  bta_gattc_op_cmpl(&client_channel_control_block, &data);
  ASSERT_EQ(GATT_ERROR, param::bta_gatt_read_complete_callback.status);
}
