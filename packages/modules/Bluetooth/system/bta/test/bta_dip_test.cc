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

#include "bta/sdp/bta_sdp_int.h"
#include "btif/include/btif_sock_sdp.h"
#include "main/shim/metrics_api.h"
#include "stack/include/sdpdefs.h"
#include "test/mock/mock_stack_sdp_api.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

namespace {
const RawAddress bdaddr({0x11, 0x22, 0x33, 0x44, 0x55, 0x66});
}  // namespace

tBTA_SDP_CB bta_sdp_cb;

static tSDP_DISC_ATTR g_attr_service_class_id_list;
static tSDP_DISC_ATTR g_sub_attr;
static tSDP_DISC_ATTR g_attr_spec_id;
static tSDP_DISC_ATTR g_attr_vendor_id;
static tSDP_DISC_ATTR g_attr_vendor_id_src;
static tSDP_DISC_ATTR g_attr_vendor_product_id;
static tSDP_DISC_ATTR g_attr_vendor_product_version;
static tSDP_DISC_ATTR g_attr_vendor_product_primary_record;
static tSDP_DISC_REC g_rec;

static void sdp_dm_cback(tBTA_SDP_EVT event, tBTA_SDP* p_data,
                         void* user_data) {
  return;
}

class BtaDipTest : public ::testing::Test {
 protected:
  void SetUp() override {
    g_attr_service_class_id_list.p_next_attr = &g_attr_spec_id;
    g_attr_service_class_id_list.attr_id = ATTR_ID_SERVICE_CLASS_ID_LIST;
    g_attr_service_class_id_list.attr_len_type = (DATA_ELE_SEQ_DESC_TYPE<<12)|2;
    g_attr_service_class_id_list.attr_value.v.p_sub_attr = &g_sub_attr;
    g_sub_attr.attr_len_type = (UUID_DESC_TYPE<<12)|2;
    g_sub_attr.attr_value.v.u16 = 0x1200;

    g_attr_spec_id.p_next_attr = &g_attr_vendor_id;
    g_attr_spec_id.attr_id = ATTR_ID_SPECIFICATION_ID;
    g_attr_spec_id.attr_len_type = (UINT_DESC_TYPE<<12)|2;
    g_attr_spec_id.attr_value.v.u16 = 0x0103;

    g_attr_vendor_id.p_next_attr = &g_attr_vendor_id_src;
    g_attr_vendor_id.attr_id = ATTR_ID_VENDOR_ID;
    g_attr_vendor_id.attr_len_type = (UINT_DESC_TYPE<<12)|2;
    g_attr_vendor_id.attr_value.v.u16 = 0x18d1;

    // Allocation should succeed
    g_attr_vendor_id_src.p_next_attr = &g_attr_vendor_product_id;
    g_attr_vendor_id_src.attr_id = ATTR_ID_VENDOR_ID_SOURCE;
    g_attr_vendor_id_src.attr_len_type = (UINT_DESC_TYPE<<12)|2;
    g_attr_vendor_id_src.attr_value.v.u16 = 1;

    g_attr_vendor_product_id.p_next_attr = &g_attr_vendor_product_version;
    g_attr_vendor_product_id.attr_id = ATTR_ID_PRODUCT_ID;
    g_attr_vendor_product_id.attr_len_type = (UINT_DESC_TYPE<<12)|2;
    g_attr_vendor_product_id.attr_value.v.u16 = 0x1234;

    g_attr_vendor_product_version.p_next_attr = &g_attr_vendor_product_primary_record;
    g_attr_vendor_product_version.attr_id = ATTR_ID_PRODUCT_VERSION;
    g_attr_vendor_product_version.attr_len_type = (UINT_DESC_TYPE<<12)|2;
    g_attr_vendor_product_version.attr_value.v.u16 = 0x0100;

    g_attr_vendor_product_primary_record.p_next_attr = &g_attr_vendor_product_primary_record;
    g_attr_vendor_product_primary_record.attr_id = ATTR_ID_PRIMARY_RECORD;
    g_attr_vendor_product_primary_record.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|1;
    g_attr_vendor_product_primary_record.attr_value.v.u8 = 1;

    g_rec.p_first_attr = &g_attr_service_class_id_list;
    g_rec.p_next_rec = nullptr;
    g_rec.remote_bd_addr = bdaddr;
    g_rec.time_read = 0;

    bta_sdp_cb.p_dm_cback = sdp_dm_cback;
    bta_sdp_cb.remote_addr = bdaddr;

    p_bta_sdp_cfg->p_sdp_db->p_first_rec = &g_rec;
  }

  void TearDown() override {}
};

namespace bluetooth {
namespace testing {

void bta_create_dip_sdp_record(bluetooth_sdp_record* record,
                               tSDP_DISC_REC* p_rec);
void bta_sdp_search_cback(const RawAddress& bd_addr, tSDP_RESULT result,
                          const void* user_data);

}  // namespace testing
}  // namespace bluetooth

// Test that bta_create_dip_sdp_record can parse sdp record to bluetooth_sdp_record correctly
TEST_F(BtaDipTest, test_bta_create_dip_sdp_record) {
  bluetooth_sdp_record record;

  bluetooth::testing::bta_create_dip_sdp_record(&record, &g_rec);

  ASSERT_EQ(record.dip.spec_id, 0x0103);
  ASSERT_EQ(record.dip.vendor, 0x18d1);
  ASSERT_EQ(record.dip.vendor_id_source, 1);
  ASSERT_EQ(record.dip.product, 0x1234);
  ASSERT_EQ(record.dip.version, 0x0100);
  ASSERT_EQ(record.dip.primary_record, true);
}

// test for b/263958603
TEST_F(BtaDipTest, test_invalid_type_checks) {
  bluetooth_sdp_record record{};

  // here we provide the wrong types of records
  // and verify that the provided values are not accepted
  g_attr_spec_id.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|1;
  g_attr_spec_id.attr_value.v.u16 = 0x0103;

  g_attr_vendor_id.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|2;
  g_attr_vendor_id.attr_value.v.u16 = 0x18d1;

  g_attr_vendor_id_src.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|2;
  g_attr_vendor_id_src.attr_value.v.u16 = 1;

  g_attr_vendor_product_id.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|2;
  g_attr_vendor_product_id.attr_value.v.u16 = 0x1234;

  g_attr_vendor_product_version.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|2;
  g_attr_vendor_product_version.attr_value.v.u16 = 0x0100;

  g_attr_vendor_product_primary_record.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_vendor_product_primary_record.attr_value.v.u8 = 1;

  bluetooth::testing::bta_create_dip_sdp_record(&record, &g_rec);

  ASSERT_EQ(record.dip.spec_id, 0);
  ASSERT_EQ(record.dip.vendor, 0);
  ASSERT_EQ(record.dip.vendor_id_source, 0);
  ASSERT_EQ(record.dip.product, 0);
  ASSERT_EQ(record.dip.version, 0);
  ASSERT_EQ(record.dip.primary_record, false);
}

// test for b/263958603
TEST_F(BtaDipTest, test_invalid_size_checks) {
  bluetooth_sdp_record record{};

  // here we provide the wrong sizes of records
  // and verify that the provided values are not accepted
  g_attr_spec_id.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_spec_id.attr_value.v.u16 = 0x0103;

  g_attr_vendor_id.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_vendor_id.attr_value.v.u16 = 0x18d1;

  g_attr_vendor_id_src.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_vendor_id_src.attr_value.v.u16 = 1;

  g_attr_vendor_product_id.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_vendor_product_id.attr_value.v.u16 = 0x1234;

  g_attr_vendor_product_version.attr_len_type = (UINT_DESC_TYPE<<12)|1;
  g_attr_vendor_product_version.attr_value.v.u16 = 0x0100;

  // size greater than 1 is accepted
  g_attr_vendor_product_primary_record.attr_len_type = (BOOLEAN_DESC_TYPE<<12)|2;
  g_attr_vendor_product_primary_record.attr_value.v.u8 = 1;

  bluetooth::testing::bta_create_dip_sdp_record(&record, &g_rec);

  ASSERT_EQ(record.dip.spec_id, 0);
  ASSERT_EQ(record.dip.vendor, 0);
  ASSERT_EQ(record.dip.vendor_id_source, 0);
  ASSERT_EQ(record.dip.product, 0);
  ASSERT_EQ(record.dip.version, 0);
  ASSERT_EQ(record.dip.primary_record, true);

  // a size zero for boolean won't be accepted
  g_attr_vendor_product_primary_record.attr_len_type =
      (BOOLEAN_DESC_TYPE << 12) | 0;

  record = {};

  g_attr_vendor_product_primary_record.attr_value.v.u8 = 1;
  bluetooth::testing::bta_create_dip_sdp_record(&record, &g_rec);
  ASSERT_EQ(record.dip.primary_record, false);
}


TEST_F(BtaDipTest, test_bta_sdp_search_cback) {
  Uuid* userdata = (Uuid*)malloc(sizeof(Uuid));

  memcpy(userdata, &UUID_DIP, sizeof(UUID_DIP));
  bluetooth::testing::bta_sdp_search_cback(RawAddress::kEmpty, SDP_SUCCESS,
                                           userdata);
}
