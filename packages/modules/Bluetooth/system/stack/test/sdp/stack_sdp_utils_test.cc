/*
 * Copyright 2022 The Android Open Source Project
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

#include <cstddef>

#include "btif/include/btif_storage.h"
#include "btif/include/stack_manager_t.h"
#include "common/init_flags.h"
#include "device/include/interop.h"
#include "mock_btif_config.h"
#include "profile/avrcp/avrcp_config.h"
#include "stack/include/avrc_api.h"
#include "stack/include/avrc_defs.h"
#include "stack/include/bt_types.h"
#include "stack/include/bt_uuid16.h"
#include "stack/sdp/sdpint.h"
#include "test/mock/mock_btif_config.h"
#include "test/mock/mock_osi_properties.h"

#define INVALID_LENGTH 5
#define INVALID_UUID 0X1F
#define UUID_HF_LSB 0X1E

#define PROFILE_VERSION_POSITION 7
#define SDP_PROFILE_DESC_LENGTH 8
#define HFP_PROFILE_MINOR_VERSION_6 0x06
#define HFP_PROFILE_MINOR_VERSION_7 0x07

using testing::_;
using testing::DoAll;
using testing::Return;
using testing::SetArrayArgument;

bool sdp_dynamic_change_hfp_version(const tSDP_ATTRIBUTE* p_attr,
                                    const RawAddress& remote_address);
void hfp_fallback(bool& is_hfp_fallback, const tSDP_ATTRIBUTE* p_attr);

const char* test_flags_feature_disabled[] = {
    "INIT_dynamic_avrcp_version_enhancement=false",
    nullptr,
};

const char* test_flags_feature_enabled[] = {
    "INIT_dynamic_avrcp_version_enhancement=true",
    nullptr,
};
const char* hfp_test_flags_feature_disabled[] = {
    "INIT_hfp_dynamic_version=false",
    nullptr,
};

const char* hfp_test_flags_feature_enabled[] = {
    "INIT_hfp_dynamic_version=true",
    nullptr,
};

namespace {
// convenience mock
class IopMock {
 public:
  MOCK_METHOD(bool, InteropMatchAddr,
              (const interop_feature_t, const RawAddress*));
  MOCK_METHOD(bool, InteropMatchName, (const interop_feature_t, const char*));
  MOCK_METHOD(void, InteropDatabaseAdd, (uint16_t, const RawAddress*, size_t));
  MOCK_METHOD(void, InteropDatabaseClear, ());
  MOCK_METHOD(bool, InteropMatchAddrOrName,
              (const interop_feature_t, const RawAddress*,
               bt_status_t (*)(const RawAddress*, bt_property_t*)));
  MOCK_METHOD(bool, InteropMatchManufacturer,
              (const interop_feature_t, uint16_t));
  MOCK_METHOD(bool, InteropMatchVendorProductIds,
              (const interop_feature_t, uint16_t, uint16_t));
  MOCK_METHOD(bool, InteropDatabaseMatchVersion,
              (const interop_feature_t, uint16_t));
  MOCK_METHOD(bool, InteropMatchAddrGetMaxLat,
              (const interop_feature_t, const RawAddress*, uint16_t*));
  MOCK_METHOD(bool, InteropGetAllowlistedMediaPlayersList, (list_t*));
  MOCK_METHOD(int, InteropFeatureNameToFeatureId, (const char*));
  MOCK_METHOD(void, InteropDatabaseAddAddr,
              (uint16_t, const RawAddress*, size_t));
};

class AvrcpVersionMock {
 public:
  MOCK_METHOD0(AvrcpProfileVersionMock, uint16_t(void));
};

std::unique_ptr<IopMock> localIopMock;
std::unique_ptr<AvrcpVersionMock> localAvrcpVersionMock;
}  // namespace

bool interop_match_addr(const interop_feature_t feature,
                        const RawAddress* addr) {
  return localIopMock->InteropMatchAddr(feature, addr);
}
bool interop_match_name(const interop_feature_t feature, const char* name) {
  return localIopMock->InteropMatchName(feature, name);
}
void interop_database_add(uint16_t feature, const RawAddress* addr,
                          size_t length) {
  return localIopMock->InteropDatabaseAdd(feature, addr, length);
}
void interop_database_clear() { localIopMock->InteropDatabaseClear(); }

bool interop_match_addr_or_name(const interop_feature_t feature,
                                const RawAddress* addr,
                                bt_status_t (*get_remote_device_property)(
                                    const RawAddress*, bt_property_t*)) {
  return localIopMock->InteropMatchAddrOrName(feature, addr,
                                              get_remote_device_property);
}

bool interop_match_manufacturer(const interop_feature_t feature,
                                uint16_t manufacturer) {
  return localIopMock->InteropMatchManufacturer(feature, manufacturer);
}

bool interop_match_vendor_product_ids(const interop_feature_t feature,
                                      uint16_t vendor_id, uint16_t product_id) {
  return localIopMock->InteropMatchVendorProductIds(feature, vendor_id,
                                                    product_id);
}

bool interop_database_match_version(const interop_feature_t feature,
                                    uint16_t version) {
  return localIopMock->InteropDatabaseMatchVersion(feature, version);
}
bool interop_match_addr_get_max_lat(const interop_feature_t feature,
                                    const RawAddress* addr, uint16_t* max_lat) {
  return localIopMock->InteropMatchAddrGetMaxLat(feature, addr, max_lat);
}

bool interop_get_allowlisted_media_players_list(list_t* p_bl_devices) {
  return localIopMock->InteropGetAllowlistedMediaPlayersList(p_bl_devices);
}

int interop_feature_name_to_feature_id(const char* feature_name) {
  return localIopMock->InteropFeatureNameToFeatureId(feature_name);
}

void interop_database_add_addr(uint16_t feature, const RawAddress* addr,
                               size_t length) {
  return localIopMock->InteropDatabaseAddAddr(feature, addr, length);
}

uint16_t AVRC_GetProfileVersion() {
  return localAvrcpVersionMock->AvrcpProfileVersionMock();
}

uint8_t avrc_value[8] = {
    ((DATA_ELE_SEQ_DESC_TYPE << 3) | SIZE_IN_NEXT_BYTE),  // data_element
    6,                                                    // data_len
    ((UUID_DESC_TYPE << 3) | SIZE_TWO_BYTES),             // uuid_element
    0,                                                    // uuid
    0,                                                    // uuid
    ((UINT_DESC_TYPE << 3) | SIZE_TWO_BYTES),             // version_element
    0,                                                    // version
    0                                                     // version
};
tSDP_ATTRIBUTE avrcp_attr = {
    .len = 0,
    .value_ptr = (uint8_t*)(&avrc_value),
    .id = 0,
    .type = 0,
};

uint8_t avrc_feat_value[2] = {
    0,  // feature
    0   // feature
};
tSDP_ATTRIBUTE avrcp_feat_attr = {
    .len = 0,
    .value_ptr = (uint8_t*)(&avrc_feat_value),
    .id = 0,
    .type = 0,
};

uint8_t hfp_value[8] = {0, 0, 0, 0x11, 0x1E, 0, 0, 0};

tSDP_ATTRIBUTE hfp_attr = {
    .len = 0,
    .value_ptr = (uint8_t*)(hfp_value),
    .id = 0,
    .type = 0,
};

void set_hfp_attr(uint32_t len, uint16_t id, uint16_t uuid) {
  hfp_attr.value_ptr[4] = uuid;
  hfp_attr.len = len;
  hfp_attr.id = id;
}

void set_avrcp_feat_attr(uint32_t len, uint16_t id, uint16_t feature) {
  UINT16_TO_BE_FIELD(avrc_feat_value, feature);
  avrcp_feat_attr.len = len;
  avrcp_feat_attr.id = id;
}

void set_avrcp_attr(uint32_t len, uint16_t id, uint16_t uuid,
                    uint16_t version) {
  UINT16_TO_BE_FIELD(avrc_value + 3, uuid);
  UINT16_TO_BE_FIELD(avrc_value + 6, version);
  avrcp_attr.len = len;
  avrcp_attr.id = id;
}

uint16_t get_avrc_target_version(tSDP_ATTRIBUTE* p_attr) {
  uint8_t* p_version = p_attr->value_ptr + 6;
  uint16_t version =
      (((uint16_t)(*(p_version))) << 8) + ((uint16_t)(*((p_version) + 1)));
  return version;
}

uint16_t get_avrc_target_feature(tSDP_ATTRIBUTE* p_attr) {
  uint8_t* p_feature = p_attr->value_ptr;
  uint16_t feature =
      (((uint16_t)(*(p_feature))) << 8) + ((uint16_t)(*((p_feature) + 1)));
  return feature;
}

class StackSdpUtilsTest : public ::testing::Test {
 protected:
  void SetUp() override {
    bluetooth::common::InitFlags::Load(hfp_test_flags_feature_disabled);
    bluetooth::common::InitFlags::Load(test_flags_feature_disabled);
    GetInterfaceToProfiles()->profileSpecific_HACK->AVRC_GetProfileVersion =
        AVRC_GetProfileVersion;
    test::mock::btif_config::btif_config_get_bin.body =
        [this](const std::string& section, const std::string& key,
               uint8_t* value, size_t* length) {
          return btif_config_interface_.GetBin(section, key, value, length);
        };
    test::mock::btif_config::btif_config_get_bin_length.body =
        [this](const std::string& section, const std::string& key) {
          return btif_config_interface_.GetBinLength(section, key);
        };
    test::mock::osi_properties::osi_property_get_bool.body =
        [](const char* key, bool default_value) { return true; };

    localIopMock = std::make_unique<IopMock>();
    localAvrcpVersionMock = std::make_unique<AvrcpVersionMock>();
    set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST,
                   UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
    set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
    set_hfp_attr(SDP_PROFILE_DESC_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST,
                 UUID_HF_LSB);
  }

  void TearDown() override {
    GetInterfaceToProfiles()->profileSpecific_HACK->AVRC_GetProfileVersion =
        nullptr;
    test::mock::btif_config::btif_config_get_bin_length = {};
    test::mock::btif_config::btif_config_get_bin = {};
    test::mock::osi_properties::osi_property_get_bool = {};

    localIopMock.reset();
    localAvrcpVersionMock.reset();
  }
  bluetooth::manager::MockBtifConfigInterface btif_config_interface_;
};

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_version_device_in_iop_table_versoin_1_4) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_4);
}

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_version_device_in_iop_table_versoin_1_3) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_3);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_len) {
  RawAddress bdaddr;
  set_avrcp_attr(5, ATTR_ID_BT_PROFILE_DESC_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_attribute_id) {
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_SERVICE_CLASS_ID_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_wrong_uuid) {
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST, UUID_SERVCLASS_AUDIO_SOURCE,
                 AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// device's controller version older than our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_older_version) {
  RawAddress bdaddr;
  uint8_t config_0104[2] = {0x04, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0104, config_0104 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_4);
}

// device's controller version same as our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_same_version) {
  RawAddress bdaddr;
  uint8_t config_0105[2] = {0x05, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0105, config_0105 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// device's controller version higher than our target version
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_device_newer_version) {
  RawAddress bdaddr;
  uint8_t config_0106[2] = {0x06, 0x01};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0106, config_0106 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// cannot read device's controller version from bt_config
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_no_config_value) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(0));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// read device's controller version from bt_config return only 1 byte
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_1_byte) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(1));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// read device's controller version from bt_config return 3 bytes
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_3_bytes) {
  RawAddress bdaddr;
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(3));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

// cached controller version is not valid
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_version_config_value_not_valid) {
  RawAddress bdaddr;
  uint8_t config_not_valid[2] = {0x12, 0x34};
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(
          DoAll(SetArrayArgument<2>(config_not_valid, config_not_valid + 2),
                Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_feature_wrong_len) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  set_avrcp_feat_attr(6, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_feature_wrong_attribute_id) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  set_avrcp_attr(8, ATTR_ID_BT_PROFILE_DESC_LIST,
                 UUID_SERVCLASS_AV_REMOTE_CONTROL, AVRC_REV_1_5);
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  set_avrcp_feat_attr(2, ATTR_ID_BT_PROFILE_DESC_LIST, AVRCP_SUPF_TG_1_5);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_5);
}

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_feature_device_in_iop_table_versoin_1_4) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  uint8_t feature_0105[2] = {0xC1, 0x00};
  EXPECT_CALL(*localAvrcpVersionMock, AvrcpProfileVersionMock())
      .WillOnce(Return(AVRC_REV_1_5));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_4);
  set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(feature_0105, feature_0105 + 2),
                      Return(true)));
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_4);
}

TEST_F(StackSdpUtilsTest,
       sdpu_set_avrc_target_feature_device_in_iop_table_versoin_1_3) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  uint8_t feature_0105[2] = {0xC1, 0x00};
  EXPECT_CALL(*localAvrcpVersionMock, AvrcpProfileVersionMock())
      .WillOnce(Return(AVRC_REV_1_5));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(true));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_3);
  set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(feature_0105, feature_0105 + 2),
                      Return(true)));
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_3);
}

// cannot read device's controller feature from bt_config
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_feature_no_config_value) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  EXPECT_CALL(*localAvrcpVersionMock, AvrcpProfileVersionMock())
      .WillOnce(Return(AVRC_REV_1_5));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(0));
  set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_5);
}

// read device's controller feature from bt_config return only 1 byte
TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_feature_config_value_1_byte) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  EXPECT_CALL(*localAvrcpVersionMock, AvrcpProfileVersionMock())
      .WillOnce(Return(AVRC_REV_1_5));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_5);
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(1));
  set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr), AVRCP_SUPF_TG_1_5);
}

TEST_F(StackSdpUtilsTest, sdpu_set_avrc_target_feature_device_versoin_1_6) {
  bluetooth::common::InitFlags::Load(test_flags_feature_enabled);
  RawAddress bdaddr;
  uint8_t config_0106[2] = {0x06, 0x01};
  uint8_t feature_0106[2] = {0xC1, 0x01};
  EXPECT_CALL(*localAvrcpVersionMock, AvrcpProfileVersionMock())
      .WillOnce(Return(AVRC_REV_1_6));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_4_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock, InteropMatchAddr(INTEROP_AVRCP_1_3_ONLY, &bdaddr))
      .WillOnce(Return(false));
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(config_0106, config_0106 + 2),
                      Return(true)));
  sdpu_set_avrc_target_version(&avrcp_attr, &bdaddr);
  ASSERT_EQ(get_avrc_target_version(&avrcp_attr), AVRC_REV_1_6);
  set_avrcp_feat_attr(2, ATTR_ID_SUPPORTED_FEATURES, AVRCP_SUPF_TG_1_5);
  EXPECT_CALL(btif_config_interface_, GetBinLength(bdaddr.ToString(), _))
      .WillOnce(Return(2));
  EXPECT_CALL(btif_config_interface_, GetBin(bdaddr.ToString(), _, _, _))
      .WillOnce(DoAll(SetArrayArgument<2>(feature_0106, feature_0106 + 2),
                      Return(true)));
  sdpu_set_avrc_target_features(&avrcp_feat_attr, &bdaddr,
                                get_avrc_target_version(&avrcp_attr));
  ASSERT_EQ(get_avrc_target_feature(&avrcp_feat_attr),
            AVRCP_SUPF_TG_1_6 | AVRC_SUPF_TG_PLAYER_COVER_ART);
}

TEST_F(StackSdpUtilsTest, dynamic_hfp_version_with_invalid_length) {
  bluetooth::common::InitFlags::Load(hfp_test_flags_feature_enabled);
  RawAddress bdaddr(RawAddress::kEmpty);
  set_hfp_attr(INVALID_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST, UUID_HF_LSB);
  ASSERT_EQ(sdp_dynamic_change_hfp_version(&hfp_attr, bdaddr), false);
}

TEST_F(StackSdpUtilsTest, dynamic_hfp_version_with_invalid_UUID) {
  bluetooth::common::InitFlags::Load(hfp_test_flags_feature_enabled);
  RawAddress bdaddr(RawAddress::kEmpty);
  set_hfp_attr(SDP_PROFILE_DESC_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST,
               INVALID_UUID);
  ASSERT_EQ(sdp_dynamic_change_hfp_version(&hfp_attr, bdaddr), false);
}

TEST_F(StackSdpUtilsTest, check_HFP_version_change_fail) {
  bluetooth::common::InitFlags::Load(hfp_test_flags_feature_enabled);
  RawAddress bdaddr(RawAddress::kEmpty);
  set_hfp_attr(SDP_PROFILE_DESC_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST,
               UUID_HF_LSB);
  test::mock::osi_properties::osi_property_get_bool.body =
      [](const char* key, bool default_value) { return false; };
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_7_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(false));
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_9_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(false));
  ASSERT_EQ(sdp_dynamic_change_hfp_version(&hfp_attr, bdaddr), false);
}

TEST_F(StackSdpUtilsTest, check_HFP_version_change_success) {
  bluetooth::common::InitFlags::Load(hfp_test_flags_feature_enabled);
  RawAddress bdaddr(RawAddress::kEmpty);
  set_hfp_attr(SDP_PROFILE_DESC_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST,
               UUID_HF_LSB);
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_7_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(true));
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_9_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(true));
  ASSERT_EQ(sdp_dynamic_change_hfp_version(&hfp_attr, bdaddr), true);
}

TEST_F(StackSdpUtilsTest, check_HFP_version_fallback_success) {
  bluetooth::common::InitFlags::Load(hfp_test_flags_feature_enabled);
  RawAddress bdaddr(RawAddress::kEmpty);
  set_hfp_attr(SDP_PROFILE_DESC_LENGTH, ATTR_ID_BT_PROFILE_DESC_LIST,
               UUID_HF_LSB);
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_7_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(true));
  EXPECT_CALL(*localIopMock,
              InteropMatchAddrOrName(INTEROP_HFP_1_9_ALLOWLIST, &bdaddr,
                                     &btif_storage_get_remote_device_property))
      .WillOnce(Return(true));
  bool is_hfp_fallback = sdp_dynamic_change_hfp_version(&hfp_attr, bdaddr);
  ASSERT_EQ(hfp_attr.value_ptr[PROFILE_VERSION_POSITION],
            HFP_PROFILE_MINOR_VERSION_7);
  hfp_fallback(is_hfp_fallback, &hfp_attr);
  ASSERT_EQ(hfp_attr.value_ptr[PROFILE_VERSION_POSITION],
            HFP_PROFILE_MINOR_VERSION_6);
}
