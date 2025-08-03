/******************************************************************************
 *
 *  Copyright 2021 The Android Open Source Project
 *
 *  Licensed under the Apache License, Version 2.0 (the "License");
 *  you may not use this file except in compliance with the License.
 *  You may obtain a copy of the License at:
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 *
 ******************************************************************************/

#include <gtest/gtest.h>

#include "bta/dm/bta_dm_int.h"
#include "bta/test/bta_base_test.h"
#include "osi/include/allocator.h"
#include "stack/include/bt_hdr.h"
#include "stack/include/btm_status.h"
#include "test/mock/mock_stack_btm_interface.h"
#include "types/bluetooth/uuid.h"

using bluetooth::Uuid;

class BtaCustUuid : public BtaBaseTest {
 protected:
  void SetUp() override {
    BtaBaseTest::SetUp();
    bta_dm_cb = {};
    mock_btm_client_interface.eir.BTM_WriteEIR =
        [](BT_HDR* p_buf) -> tBTM_STATUS {
      osi_free(p_buf);
      return BTM_SUCCESS;
    };
    mock_btm_client_interface.eir.BTM_GetEirSupportedServices =
        [](uint32_t* p_eir_uuid, uint8_t** p, uint8_t max_num_uuid16,
           uint8_t* p_num_uuid16) -> uint8_t { return 0; };
    mock_btm_client_interface.eir.BTM_WriteEIR =
        [](BT_HDR* p_buf) -> tBTM_STATUS {
      osi_free(p_buf);
      return BTM_SUCCESS;
    };
    mock_btm_client_interface.local.BTM_ReadLocalDeviceNameFromController =
        [](tBTM_CMPL_CB* cb) -> tBTM_STATUS { return BTM_CMD_STARTED; };
    mock_btm_client_interface.security.BTM_SecRegister =
        [](const tBTM_APPL_INFO* p_cb_info) -> bool { return true; };
  }
  void TearDown() override { BtaBaseTest::TearDown(); }
};

namespace {
  uint32_t handle1 = 1;
  uint32_t handle2 = 2;
  static const Uuid uuid1 = Uuid::From128BitBE(
    Uuid::UUID128Bit{{0x00, 0x11, 0x22, 0x33, 0x44, 0x55, 0x66, 0x77, 0x88,
                      0x99, 0xaa, 0xbb, 0xcc, 0xdd, 0xee, 0xff}});
  static const Uuid uuid2 = Uuid::From128BitBE(
    Uuid::UUID128Bit{{0x00, 0x00, 0x00, 0x00, 0x22, 0x22, 0x22, 0x22, 0x33,
                      0x33, 0x55, 0x55, 0x55, 0x55, 0x55, 0x59}});
}

// Test we can remove/add 128 bit custom UUID from/to bta_dm_cb.bta_custom_uuid
TEST_F(BtaCustUuid, test_add_remove_cust_uuid) {
  tBTA_CUSTOM_UUID& curr0 = bta_dm_cb.bta_custom_uuid[0];
  tBTA_CUSTOM_UUID& curr1 = bta_dm_cb.bta_custom_uuid[1];
  tBTA_CUSTOM_UUID curr0_expect = {uuid1, handle1};
  tBTA_CUSTOM_UUID curr1_expect = {uuid2, handle2};
  // Add first 128 bit custom UUID
  bta_dm_eir_update_cust_uuid(curr0_expect, true);
  ASSERT_STREQ(uuid1.ToString().c_str(), curr0.custom_uuid.ToString().c_str());
  // Add second 128 bit custom UUID
  bta_dm_eir_update_cust_uuid(curr1_expect, true);
  ASSERT_STREQ(uuid2.ToString().c_str(), curr1.custom_uuid.ToString().c_str());

  curr0_expect.custom_uuid.UpdateUuid(Uuid::kEmpty);
  curr1_expect.custom_uuid.UpdateUuid(Uuid::kEmpty);
  // Remove first 128 bit custom UUID
  bta_dm_eir_update_cust_uuid(curr0_expect, false);
  ASSERT_STREQ(Uuid::kEmpty.ToString().c_str(), curr0.custom_uuid.ToString().c_str());
  // Remove second 128 bit custom UUID
  bta_dm_eir_update_cust_uuid(curr1_expect, false);
  ASSERT_STREQ(Uuid::kEmpty.ToString().c_str(), curr1.custom_uuid.ToString().c_str());
}
