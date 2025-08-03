/*
 * Copyright 2024 The Android Open Source Project
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

#include "discovery/device/bt_property.h"

#include <cstdint>
#include <future>

#include "gtest/gtest.h"
#include "hardware/bluetooth.h"
#include "os/log.h"
#include "stack/include/bt_name.h"

using namespace bluetooth::property;

namespace {

constexpr size_t kNumberTestedProperties = 22;

constexpr size_t kBdPropNameLength = kBdNameLength + sizeof(kBdNameDelim);

constexpr uint8_t kReallyLongName[kBdPropNameLength] =
    "aaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaa"
    "aaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaa"
    "aAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaaaAaaaaaaaa";

// BT_PROPERTY_BDNAME
constexpr BD_NAME kBdName{'k', 'B', 'd', 'N', 'a', 'm', 'e', '\0'};

// BT_PROPERTY_BDADDR
const RawAddress kRawAddress{{0x11, 0x22, 0x33, 0x44, 0x55, 0x66}};

// BT_PROPERTY_UUIDS
const bluetooth::Uuid uuids[] = {
    {bluetooth::Uuid::FromString("00000001-1001-1000-8000-00805f9b34fb")},
    {bluetooth::Uuid::FromString("00000001-1002-1000-8000-00805f9b34fb")},
    {bluetooth::Uuid::FromString("00000001-1003-1000-8000-00805f9b34fb")},
};
const std::vector<bluetooth::Uuid> kUuids(uuids, uuids + sizeof(uuids) / sizeof(uuids[0]));

// BT_PROPERTY_CLASS_OF_DEVICE
constexpr uint32_t kClassOfDevice{0x99663300};

// BT_PROPERTY_TYPE_OF_DEVICE
constexpr bt_device_type_t kTypeOfDevice{BT_DEVICE_DEVTYPE_BREDR};

// BT_PROPERTY_SERVICE_RECORD
const bt_service_record_t kServiceRecord{
    .uuid = uuids[0],
    .channel = 0x1234,
    .name = {'k', 'S', 'e', 'r', 'v', 'i', 'c', 'e', 'R', 'e',
             'c', 'o', 'r', 'd', '.', 'n', 'a', 'm', 'e', '\0'},
};

// BT_PROPERTY_ADAPTER_SCAN_MODE
constexpr bt_scan_mode_t kAdapterScanMode{BT_SCAN_MODE_CONNECTABLE_DISCOVERABLE};
//
// BT_PROPERTY_ADAPTER_BONDED_DEVICES
const RawAddress kAdapterBondedDevices[] = {
    {{0x11, 0x22, 0x33, 0x44, 0x55}},
    {{0x12, 0x22, 0x33, 0x44, 0x55}},
    {{0x13, 0x22, 0x33, 0x44, 0x55}},
    {{0x14, 0x22, 0x33, 0x44, 0x55}},
    {{0x15, 0x22, 0x33, 0x44, 0x55}},
};
constexpr size_t kNumBondedDevices =
    sizeof(kAdapterBondedDevices) / sizeof(kAdapterBondedDevices[0]);

// BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT
constexpr uint32_t kAdapterDiscoverableTimeout{0x4488cc00};

// BT_PROPERTY_REMOTE_FRIENDLY_NAME
const uint8_t kRemoteFriendlyName[] = {'k', 'R', 'e', 'm', 'o', 't', 'e', 'F', 'r', 'i',
                                       'e', 'n', 'd', 'l', 'y', 'N', 'a', 'm', 'e', '\0'};

// BT_PROPERTY_REMOTE_RSSI
constexpr int8_t kRemoteRssi{0x10};

// BT_PROPERTY_REMOTE_VERSION_INFO
bt_remote_version_t kRemoteVersionInfo{
    .version = 1,
    .sub_ver = 2,
    .manufacturer = 3,
};

// BT_PROPERTY_LOCAL_LE_FEATURES
constexpr bt_local_le_features_t kLocalLeFeatures{
    .version_supported = 0x1234,
    .local_privacy_enabled = 0x11,
    .max_adv_instance = 0x22,
    .rpa_offload_supported = 0x33,
    .max_irk_list_size = 0x44,
    .max_adv_filter_supported = 0x55,
    .activity_energy_info_supported = 0x66,
    .scan_result_storage_size = 0x5678,
    .total_trackable_advertisers = 0x9abc,
    .extended_scan_support = true,
    .debug_logging_supported = true,
    .le_2m_phy_supported = true,
    .le_coded_phy_supported = true,
    .le_extended_advertising_supported = true,
    .le_periodic_advertising_supported = true,
    .le_maximum_advertising_data_length = 0x1357,
    .dynamic_audio_buffer_supported = 0x22446688,
    .le_periodic_advertising_sync_transfer_sender_supported = true,
    .le_connected_isochronous_stream_central_supported = true,
    .le_isochronous_broadcast_supported = true,
    .le_periodic_advertising_sync_transfer_recipient_supported = true,
    .adv_filter_extended_features_mask = 0x3366,
};

// BT_PROPERTY_LOCAL_IO_CAPS
constexpr bt_io_cap_t kLocalIoCaps{BT_IO_CAP_UNKNOWN};

// BT_PROPERTY_RESERVED_0F
// BT_PROPERTY_DYNAMIC_AUDIO_BUFFER

// BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER
constexpr bool kRemoteIsCoordinatedSetMember{true};

// BT_PROPERTY_APPEARANCE
constexpr uint16_t kAppearance{0x44};

// BT_PROPERTY_VENDOR_PRODUCT_INFO
constexpr bt_vendor_product_info_t kVendorProductInfo{
    .vendor_id_src = 0x02,
    .vendor_id = 0x1235,
    .product_id = 0x5679,
    .version = 0x9abd,
};

// BT_PROPERTY_WL_MEDIA_PLAYERS_LIST

// BT_PROPERTY_REMOTE_ASHA_CAPABILITY
constexpr int16_t kRemoteAshaCapability{0x89};

// BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID
constexpr uint32_t kRemoteAshaTruncatedHisyncId{0x22446688};

// BT_PROPERTY_REMOTE_MODEL_NUM
constexpr bt_bdname_t kRemoteModelNum{
    .name = {'k', 'R', 'e', 'm', 'o', 't', 'e', 'M', 'o', 'd', 'e', 'l', 'N', 'u', 'm', '\0'},
};

// BT_PROPERTY_REMOTE_ADDR_TYPE
constexpr uint8_t kRemoteAddrType{0x55};

// BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP
constexpr int kRemoteDeviceTimestamp{0x12345678};

// Fill the given property type with the well known property data set
void fill_property(
    const bt_property_type_t& type, std::vector<std::shared_ptr<BtProperty>>& properties) {
  switch (type) {
    case BT_PROPERTY_BDNAME: {
      properties.push_back(BdName::Create(kBdName));
      ASSERT_EQ(kBdPropNameLength, properties.back()->Size());
    } break;

    case BT_PROPERTY_BDADDR:
      properties.push_back(BdAddr::Create(kRawAddress));
      ASSERT_EQ(sizeof(RawAddress), properties.back()->Size());
      break;

    case BT_PROPERTY_UUIDS: {
      properties.push_back(Uuids::Create(kUuids));
      ASSERT_EQ(sizeof(bluetooth::Uuid) * kUuids.size(), properties.back()->Size());
    } break;

    case BT_PROPERTY_CLASS_OF_DEVICE:
      properties.push_back(ClassOfDevice::Create(kClassOfDevice));
      ASSERT_EQ(sizeof(uint32_t), properties.back()->Size());
      break;

    case BT_PROPERTY_TYPE_OF_DEVICE:
      properties.push_back(TypeOfDevice::Create(kTypeOfDevice));
      ASSERT_EQ(sizeof(bt_device_type_t), properties.back()->Size());
      break;

    case BT_PROPERTY_SERVICE_RECORD:
      properties.push_back(ServiceRecord::Create(kServiceRecord));
      ASSERT_EQ(sizeof(bt_service_record_t), properties.back()->Size());
      break;

    case BT_PROPERTY_ADAPTER_SCAN_MODE:
      properties.push_back(AdapterScanMode::Create(kAdapterScanMode));
      ASSERT_EQ(sizeof(bt_scan_mode_t), properties.back()->Size());
      break;

    case BT_PROPERTY_ADAPTER_BONDED_DEVICES: {
      properties.push_back(AdapterBondedDevices::Create(kAdapterBondedDevices, kNumBondedDevices));
      ASSERT_EQ(sizeof(RawAddress) * kNumBondedDevices, properties.back()->Size());
    } break;

    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT:
      properties.push_back(AdapterDiscoverableTimeout::Create(kAdapterDiscoverableTimeout));
      ASSERT_EQ(sizeof(uint32_t), properties.back()->Size());
      break;

    case BT_PROPERTY_REMOTE_FRIENDLY_NAME: {
      properties.push_back(
          RemoteFriendlyName::Create(kRemoteFriendlyName, sizeof(kRemoteFriendlyName)));
      ASSERT_EQ(sizeof(kRemoteFriendlyName) + sizeof(kBdNameDelim), properties.back()->Size());
    } break;

    case BT_PROPERTY_REMOTE_RSSI:
      properties.push_back(RemoteRSSI::Create(kRemoteRssi));
      ASSERT_EQ(sizeof(int8_t), properties.back()->Size());
      break;

    case BT_PROPERTY_REMOTE_VERSION_INFO:
      properties.push_back(RemoteVersionInfo::Create(kRemoteVersionInfo));
      ASSERT_EQ(sizeof(bt_remote_version_t), properties.back()->Size());
      break;

    case BT_PROPERTY_LOCAL_LE_FEATURES:
      properties.push_back(LocalLeFeatures::Create(kLocalLeFeatures));
      ASSERT_EQ(sizeof(kLocalLeFeatures), properties.back()->Size());
      break;

    case BT_PROPERTY_LOCAL_IO_CAPS:
      properties.push_back(LocalIOCaps::Create(kLocalIoCaps));
      ASSERT_EQ(sizeof(kLocalIoCaps), properties.back()->Size());
      break;

    case BT_PROPERTY_RESERVED_0F:
    case BT_PROPERTY_DYNAMIC_AUDIO_BUFFER:
      break;

    case BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER:
      properties.push_back(RemoteIsCoordinatedSetMember::Create(kRemoteIsCoordinatedSetMember));
      ASSERT_EQ(sizeof(kRemoteIsCoordinatedSetMember), properties.back()->Size());
      break;

    case BT_PROPERTY_APPEARANCE:
      properties.push_back(Appearance::Create(kAppearance));
      ASSERT_EQ(sizeof(kAppearance), properties.back()->Size());
      break;

    case BT_PROPERTY_VENDOR_PRODUCT_INFO:
      properties.push_back(VendorProductInfo::Create(kVendorProductInfo));
      ASSERT_EQ(sizeof(kVendorProductInfo), properties.back()->Size());
      break;

    case BT_PROPERTY_WL_MEDIA_PLAYERS_LIST:
      break;

    case BT_PROPERTY_REMOTE_ASHA_CAPABILITY:
      properties.push_back(RemoteASHACapability::Create(kRemoteAshaCapability));
      ASSERT_EQ(sizeof(kRemoteAshaCapability), properties.back()->Size());
      break;

    case BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID:
      properties.push_back(RemoteASHATruncatedHiSyncId::Create(kRemoteAshaTruncatedHisyncId));
      ASSERT_EQ(sizeof(kRemoteAshaTruncatedHisyncId), properties.back()->Size());
      break;

    case BT_PROPERTY_REMOTE_MODEL_NUM: {
      properties.push_back(RemoteModelNum::Create(kRemoteModelNum));
      ASSERT_EQ(sizeof(kRemoteModelNum), properties.back()->Size());
    } break;

    case BT_PROPERTY_REMOTE_ADDR_TYPE:
      properties.push_back(RemoteAddrType::Create(kRemoteAddrType));
      ASSERT_EQ(sizeof(kRemoteAddrType), properties.back()->Size());
      break;

    case BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP:
      properties.push_back(RemoteDeviceTimestamp::Create(kRemoteDeviceTimestamp));
      ASSERT_EQ(sizeof(kRemoteDeviceTimestamp), properties.back()->Size());
      break;

    default:
      FAIL() << "Illegal property type:" << type;
      break;
  }
}

// Verify the given property type with the well known property data set
void verify_property(const bt_property_type_t& type, const bt_property_t& property) {
  ASSERT_EQ(type, property.type);
  switch (property.type) {
    case BT_PROPERTY_BDNAME:
      ASSERT_EQ((int)kBdPropNameLength, property.len);
      ASSERT_STREQ((const char*)kBdName, (const char*)property.val);
      break;

    case BT_PROPERTY_BDADDR:
      ASSERT_EQ((int)sizeof(RawAddress), property.len);
      ASSERT_EQ(kRawAddress, *((RawAddress*)property.val));
      break;

    case BT_PROPERTY_UUIDS: {
      ASSERT_EQ((int)(sizeof(bluetooth::Uuid) * kUuids.size()), property.len);
      const bluetooth::Uuid* uuid = (const bluetooth::Uuid*)property.val;
      ASSERT_EQ(uuids[0], *uuid++);
      ASSERT_EQ(uuids[1], *uuid++);
      ASSERT_EQ(uuids[2], *uuid++);
    } break;

    case BT_PROPERTY_CLASS_OF_DEVICE:
      ASSERT_EQ((int)sizeof(uint32_t), property.len);
      ASSERT_EQ(kClassOfDevice, *((uint32_t*)property.val));
      break;

    case BT_PROPERTY_TYPE_OF_DEVICE:
      ASSERT_EQ((int)sizeof(uint32_t), property.len);
      ASSERT_EQ(kTypeOfDevice, *((uint32_t*)property.val));
      break;

    case BT_PROPERTY_SERVICE_RECORD:
      ASSERT_EQ((int)sizeof(bt_service_record_t), property.len);
      ASSERT_EQ(kServiceRecord.uuid, ((bt_service_record_t*)property.val)->uuid);
      ASSERT_EQ(kServiceRecord.channel, ((bt_service_record_t*)property.val)->channel);
      ASSERT_STREQ(kServiceRecord.name, ((bt_service_record_t*)property.val)->name);
      break;

    case BT_PROPERTY_ADAPTER_SCAN_MODE:
      ASSERT_EQ((int)sizeof(bt_scan_mode_t), property.len);
      ASSERT_EQ(kAdapterScanMode, *((bt_scan_mode_t*)property.val));
      break;

    case BT_PROPERTY_ADAPTER_BONDED_DEVICES: {
      ASSERT_EQ((int)sizeof(kAdapterBondedDevices), property.len);
      const RawAddress* raw_address = static_cast<RawAddress*>(property.val);
      for (size_t i = 0; i < kNumBondedDevices; i++, raw_address++) {
        ASSERT_EQ(kAdapterBondedDevices[i], *raw_address);
      }
    } break;

    case BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT:
      ASSERT_EQ((int)sizeof(uint32_t), property.len);
      ASSERT_EQ(kAdapterDiscoverableTimeout, *((uint32_t*)property.val));
      break;

    case BT_PROPERTY_REMOTE_FRIENDLY_NAME:
      ASSERT_EQ((int)(sizeof(kRemoteFriendlyName) + sizeof(kBdNameDelim)), property.len);
      ASSERT_STREQ((const char*)kRemoteFriendlyName, (const char*)property.val);
      break;

    case BT_PROPERTY_REMOTE_RSSI:
      ASSERT_EQ((int)sizeof(int8_t), property.len);
      ASSERT_EQ(kRemoteRssi, *((int8_t*)property.val));
      break;

    case BT_PROPERTY_REMOTE_VERSION_INFO:
      ASSERT_EQ((int)sizeof(bt_remote_version_t), property.len);
      ASSERT_EQ(kRemoteVersionInfo.version, ((bt_remote_version_t*)property.val)->version);
      ASSERT_EQ(kRemoteVersionInfo.sub_ver, ((bt_remote_version_t*)property.val)->sub_ver);
      ASSERT_EQ(
          kRemoteVersionInfo.manufacturer, ((bt_remote_version_t*)property.val)->manufacturer);
      break;

    case BT_PROPERTY_LOCAL_LE_FEATURES:
      ASSERT_EQ((int)sizeof(bt_local_le_features_t), property.len);
      ASSERT_EQ(
          kLocalLeFeatures.version_supported,
          ((bt_local_le_features_t*)property.val)->version_supported);
      ASSERT_EQ(
          kLocalLeFeatures.local_privacy_enabled,
          ((bt_local_le_features_t*)property.val)->local_privacy_enabled);
      ASSERT_EQ(
          kLocalLeFeatures.local_privacy_enabled,
          ((bt_local_le_features_t*)property.val)->local_privacy_enabled);
      ASSERT_EQ(
          kLocalLeFeatures.max_adv_instance,
          ((bt_local_le_features_t*)property.val)->max_adv_instance);
      ASSERT_EQ(
          kLocalLeFeatures.rpa_offload_supported,
          ((bt_local_le_features_t*)property.val)->rpa_offload_supported);
      ASSERT_EQ(
          kLocalLeFeatures.max_irk_list_size,
          ((bt_local_le_features_t*)property.val)->max_irk_list_size);
      ASSERT_EQ(
          kLocalLeFeatures.max_adv_filter_supported,
          ((bt_local_le_features_t*)property.val)->max_adv_filter_supported);
      ASSERT_EQ(
          kLocalLeFeatures.activity_energy_info_supported,
          ((bt_local_le_features_t*)property.val)->activity_energy_info_supported);
      ASSERT_EQ(
          kLocalLeFeatures.scan_result_storage_size,
          ((bt_local_le_features_t*)property.val)->scan_result_storage_size);
      ASSERT_EQ(
          kLocalLeFeatures.total_trackable_advertisers,
          ((bt_local_le_features_t*)property.val)->total_trackable_advertisers);
      ASSERT_EQ(
          kLocalLeFeatures.extended_scan_support,
          ((bt_local_le_features_t*)property.val)->extended_scan_support);
      ASSERT_EQ(
          kLocalLeFeatures.debug_logging_supported,
          ((bt_local_le_features_t*)property.val)->debug_logging_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_2m_phy_supported,
          ((bt_local_le_features_t*)property.val)->le_2m_phy_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_coded_phy_supported,
          ((bt_local_le_features_t*)property.val)->le_coded_phy_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_extended_advertising_supported,
          ((bt_local_le_features_t*)property.val)->le_extended_advertising_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_periodic_advertising_supported,
          ((bt_local_le_features_t*)property.val)->le_periodic_advertising_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_maximum_advertising_data_length,
          ((bt_local_le_features_t*)property.val)->le_maximum_advertising_data_length);
      ASSERT_EQ(
          kLocalLeFeatures.dynamic_audio_buffer_supported,
          ((bt_local_le_features_t*)property.val)->dynamic_audio_buffer_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_periodic_advertising_sync_transfer_sender_supported,
          ((bt_local_le_features_t*)property.val)
              ->le_periodic_advertising_sync_transfer_sender_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_connected_isochronous_stream_central_supported,
          ((bt_local_le_features_t*)property.val)
              ->le_connected_isochronous_stream_central_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_isochronous_broadcast_supported,
          ((bt_local_le_features_t*)property.val)->le_isochronous_broadcast_supported);
      ASSERT_EQ(
          kLocalLeFeatures.le_periodic_advertising_sync_transfer_recipient_supported,
          ((bt_local_le_features_t*)property.val)
              ->le_periodic_advertising_sync_transfer_recipient_supported);
      ASSERT_EQ(
          kLocalLeFeatures.adv_filter_extended_features_mask,
          ((bt_local_le_features_t*)property.val)->adv_filter_extended_features_mask);
      break;

    case BT_PROPERTY_LOCAL_IO_CAPS:
      ASSERT_EQ((int)sizeof(bt_io_cap_t), property.len);
      ASSERT_EQ(kLocalIoCaps, *((bt_io_cap_t*)property.val));
      break;

    case BT_PROPERTY_RESERVED_0F:
    case BT_PROPERTY_DYNAMIC_AUDIO_BUFFER:
      break;

    case BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER:
      ASSERT_EQ((int)sizeof(bool), property.len);
      ASSERT_EQ(kRemoteIsCoordinatedSetMember, *((bool*)property.val));
      break;

    case BT_PROPERTY_APPEARANCE:
      ASSERT_EQ((int)sizeof(uint16_t), property.len);
      ASSERT_EQ(kAppearance, *((uint16_t*)property.val));
      break;

    case BT_PROPERTY_VENDOR_PRODUCT_INFO:
      ASSERT_EQ((int)sizeof(bt_vendor_product_info_t), property.len);
      ASSERT_EQ(
          kVendorProductInfo.vendor_id_src,
          ((bt_vendor_product_info_t*)property.val)->vendor_id_src);
      ASSERT_EQ(kVendorProductInfo.vendor_id, ((bt_vendor_product_info_t*)property.val)->vendor_id);
      ASSERT_EQ(
          kVendorProductInfo.product_id, ((bt_vendor_product_info_t*)property.val)->product_id);
      ASSERT_EQ(kVendorProductInfo.version, ((bt_vendor_product_info_t*)property.val)->version);
      break;

    case BT_PROPERTY_WL_MEDIA_PLAYERS_LIST:
      break;

    case BT_PROPERTY_REMOTE_ASHA_CAPABILITY:
      ASSERT_EQ((int)sizeof(int16_t), property.len);
      ASSERT_EQ(kRemoteAshaCapability, *((int16_t*)property.val));
      break;

    case BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID:
      ASSERT_EQ((int)sizeof(uint32_t), property.len);
      ASSERT_EQ(kRemoteAshaTruncatedHisyncId, *((uint32_t*)property.val));
      break;

    case BT_PROPERTY_REMOTE_MODEL_NUM:
      ASSERT_EQ((int)sizeof(kRemoteModelNum.name), property.len);
      ASSERT_STREQ((const char*)kRemoteModelNum.name, ((const char*)property.val));
      break;

    case BT_PROPERTY_REMOTE_ADDR_TYPE:
      ASSERT_EQ((int)sizeof(uint8_t), property.len);
      ASSERT_EQ(kRemoteAddrType, *((uint8_t*)property.val));
      break;

    case BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP:
      ASSERT_EQ((int)sizeof(int), property.len);
      ASSERT_EQ(kRemoteDeviceTimestamp, *((int*)property.val));
      break;

    default:
      FAIL() << "Illegal property type:" << type;
      break;
  }
}

// Fill a property container with all possible property types
void fill_properties(std::vector<std::shared_ptr<BtProperty>>& properties) {
  fill_property(BT_PROPERTY_BDNAME, properties);
  fill_property(BT_PROPERTY_BDADDR, properties);
  fill_property(BT_PROPERTY_UUIDS, properties);
  fill_property(BT_PROPERTY_CLASS_OF_DEVICE, properties);
  fill_property(BT_PROPERTY_TYPE_OF_DEVICE, properties);
  fill_property(BT_PROPERTY_SERVICE_RECORD, properties);
  fill_property(BT_PROPERTY_ADAPTER_SCAN_MODE, properties);
  fill_property(BT_PROPERTY_ADAPTER_BONDED_DEVICES, properties);
  fill_property(BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT, properties);
  fill_property(BT_PROPERTY_REMOTE_FRIENDLY_NAME, properties);
  fill_property(BT_PROPERTY_REMOTE_RSSI, properties);
  fill_property(BT_PROPERTY_REMOTE_VERSION_INFO, properties);
  fill_property(BT_PROPERTY_LOCAL_LE_FEATURES, properties);
  fill_property(BT_PROPERTY_LOCAL_IO_CAPS, properties);
  fill_property(BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER, properties);
  fill_property(BT_PROPERTY_APPEARANCE, properties);
  fill_property(BT_PROPERTY_VENDOR_PRODUCT_INFO, properties);
  fill_property(BT_PROPERTY_REMOTE_ASHA_CAPABILITY, properties);
  fill_property(BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID, properties);
  fill_property(BT_PROPERTY_REMOTE_MODEL_NUM, properties);
  fill_property(BT_PROPERTY_REMOTE_ADDR_TYPE, properties);
  fill_property(BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP, properties);
  ASSERT_EQ(kNumberTestedProperties, properties.size());
}

}  // namespace
   //
class BtPropertyTest : public testing::Test {
 protected:
  void SetUp() override {}
  void TearDown() override {}
};

TEST_F(BtPropertyTest, bt_property_text_test) {
  {
    bt_property_t prop = {
        .type = BT_PROPERTY_BDNAME,
        .len = (int)sizeof(kBdName),
        .val = (void*)kBdName,
    };
    ASSERT_STREQ("type:BT_PROPERTY_BDNAME name:kBdName", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_BDADDR,
        .len = (int)sizeof(kRawAddress),
        .val = (void*)&kRawAddress,
    };
    ASSERT_STREQ("type:BT_PROPERTY_BDADDR addr:11:22:33:44:55:66", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_UUIDS,
        .len = (int)(sizeof(bluetooth::Uuid) * kUuids.size()),
        .val = (void*)&kUuids[0],
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_UUIDS uuids:00000001-1001-1000-8000-00805f9b34fb "
        "00000001-1002-1000-8000-00805f9b34fb 00000001-1003-1000-8000-00805f9b34fb",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_CLASS_OF_DEVICE,
        .len = (int)sizeof(kClassOfDevice),
        .val = (void*)&kClassOfDevice,
    };
    ASSERT_STREQ("type:BT_PROPERTY_CLASS_OF_DEVICE cod:0x99663300", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_TYPE_OF_DEVICE,
        .len = (int)sizeof(kTypeOfDevice),
        .val = (void*)&kTypeOfDevice,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_TYPE_OF_DEVICE type_of_device:1", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_SERVICE_RECORD,
        .len = (int)sizeof(kServiceRecord),
        .val = (void*)&kServiceRecord,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_SERVICE_RECORD uuid:00000001-1001-1000-8000-00805f9b34fb channel:4660 "
        "name:\"kServiceRecord.name\"",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_ADAPTER_SCAN_MODE,
        .len = (int)sizeof(kAdapterScanMode),
        .val = (void*)&kAdapterScanMode,
    };
    ASSERT_STREQ("type:BT_PROPERTY_ADAPTER_SCAN_MODE scan_mode:2", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_ADAPTER_BONDED_DEVICES,
        .len = (int)(sizeof(kAdapterBondedDevices)),
        .val = (void*)kAdapterBondedDevices,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_ADAPTER_BONDED_DEVICES addrs:11:22:33:44:55:00 12:22:33:44:55:00 "
        "13:22:33:44:55:00 14:22:33:44:55:00 15:22:33:44:55:00",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT,
        .len = (int)sizeof(kAdapterDiscoverableTimeout),
        .val = (void*)&kAdapterDiscoverableTimeout,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT discoverable_timeout:1149815808",
        bt_property_text(prop).c_str());
  }

  {
    bt_bdname_t bd_name;
    bd_name_copy(bd_name.name, kRemoteFriendlyName);
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_FRIENDLY_NAME,
        .len = (int)sizeof(bd_name.name),
        .val = (void*)&bd_name.name,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_FRIENDLY_NAME remote_friendly_name:kRemoteFriendlyName",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_RSSI,
        .len = (int)sizeof(kRemoteRssi),
        .val = (void*)&kRemoteRssi,
    };
    ASSERT_STREQ("type:BT_PROPERTY_REMOTE_RSSI rssi:16", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_VERSION_INFO,
        .len = (int)sizeof(kRemoteVersionInfo),
        .val = (void*)&kRemoteVersionInfo,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_VERSION_INFO version:1 sub:2 mfr:3",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_LOCAL_LE_FEATURES,
        .len = (int)sizeof(kLocalLeFeatures),
        .val = (void*)&kLocalLeFeatures,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_LOCAL_LE_FEATURES version_supported:4660 local_privacy_enabled:17 "
        "max_adv_instance:34 rpa_offload_supported:51 max_irk_list_size:68 "
        "max_adv_filter_supported:85 activity_energy_info_supported:102 "
        "scan_result_storage_size:22136 total_trackable_advertisers:39612 extended_scan_support:1 "
        "debug_logging_supported:1 le_2m_phy_supported:1 le_coded_phy_supported:1 "
        "le_extended_advertising_supported:1 le_periodic_advertising_supported:1 "
        "le_maximum_advertising_data_length:4951 dynamic_audio_buffer_supported:574908040 "
        "le_periodic_advertising_sync_transfer_sender_supported:1 "
        "le_connected_isochronous_stream_central_supported:1 le_isochronous_broadcast_supported:1 "
        "le_periodic_advertising_sync_transfer_recipient_supported:1 "
        "adv_filter_extended_features_mask:13158",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_LOCAL_IO_CAPS,
        .len = (int)sizeof(kLocalIoCaps),
        .val = (void*)&kLocalIoCaps,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_LOCAL_IO_CAPS local_io_caps:255", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER,
        .len = (int)sizeof(kRemoteIsCoordinatedSetMember),
        .val = (void*)&kRemoteIsCoordinatedSetMember,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER is_coordinated_set_member:true",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_APPEARANCE,
        .len = (int)sizeof(kAppearance),
        .val = (void*)&kAppearance,
    };
    ASSERT_STREQ("type:BT_PROPERTY_APPEARANCE appearance:0x44", bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_VENDOR_PRODUCT_INFO,
        .len = (int)sizeof(kVendorProductInfo),
        .val = (void*)&kVendorProductInfo,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_VENDOR_PRODUCT_INFO vendor_id_src:2 vendor_id:4661 product_id:22137 "
        "version:39613",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_ASHA_CAPABILITY,
        .len = (int)sizeof(kRemoteAshaCapability),
        .val = (void*)&kRemoteAshaCapability,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_ASHA_CAPABILITY remote_asha_capability:137",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID,
        .len = (int)sizeof(kRemoteAshaTruncatedHisyncId),
        .val = (void*)&kRemoteAshaTruncatedHisyncId,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_ASHA_TRUNCATED_HISYNCID remote_asha_truncated_hisyncid:574908040",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_MODEL_NUM,
        .len = (int)sizeof(kRemoteModelNum.name),
        .val = (void*)kRemoteModelNum.name,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_MODEL_NUM remote_model_num:kRemoteModelNum",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_ADDR_TYPE,
        .len = (int)sizeof(kRemoteAddrType),
        .val = (void*)&kRemoteAddrType,
    };
    ASSERT_STREQ(
        "type:BT_PROPERTY_REMOTE_ADDR_TYPE remote_asha_truncated_hisyncid:0x55",
        bt_property_text(prop).c_str());
  }

  {
    bt_property_t prop = {
        .type = BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP,
        .len = (int)sizeof(kRemoteDeviceTimestamp),
        .val = (void*)&kRemoteDeviceTimestamp,
    };
    ASSERT_STREQ("type:BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP", bt_property_text(prop).c_str());
  }
}

TEST_F(BtPropertyTest, verify_property_sizes) {
  std::vector<std::shared_ptr<BtProperty>> properties;
  fill_properties(properties);
}

TEST_F(BtPropertyTest, fill_and_serialize) {
  std::vector<std::shared_ptr<BtProperty>> properties;
  fill_properties(properties);

  BtPropertyLegacy legacy(properties);

  ASSERT_EQ(kNumberTestedProperties, legacy.NumProperties());
}

TEST_F(BtPropertyTest, serialize_and_verify) {
  std::vector<std::shared_ptr<BtProperty>> properties;
  fill_properties(properties);

  BtPropertyLegacy legacy(properties);

  for (const auto p : legacy.Properties()) {
    verify_property(p.type, p);
  }
}

TEST_F(BtPropertyTest, name_too_long) {
  std::vector<std::shared_ptr<BtProperty>> properties;
  BD_NAME bd_name;
  for (size_t i = 0; i < kBdPropNameLength; i++) {
    bd_name[i] = ((i + 1) % 10) ? 'a' : 'A';
  }

  properties.push_back(BdName::Create(bd_name));
  BtPropertyLegacy legacy(properties);
  ASSERT_EQ(1U, legacy.NumProperties());

  bt_property_t bt_properties[1];
  legacy.Export(bt_properties, 1U);

  ASSERT_STREQ((const char*)kReallyLongName, (const char*)bt_properties[0].val);
}

class BtPropertyArrayTest : public testing::Test {
 protected:
  void SetUp() override {
    fill_properties(properties);
  }
  void TearDown() override {}
  std::vector<std::shared_ptr<BtProperty>> properties;
  bt_property_t props[kNumberTestedProperties];
};

TEST_F(BtPropertyArrayTest, serialize_and_verify) {
  BtPropertyLegacy legacy(properties);

  for (const auto p : legacy.Properties()) {
    verify_property(p.type, p);
  }
}

TEST_F(BtPropertyArrayTest, async_data) {
  auto future = std::async(std::launch::async, []() {
    std::vector<std::shared_ptr<BtProperty>> properties;
    fill_properties(properties);
    return properties;
  });

  auto properties = future.get();

  BtPropertyLegacy legacy(properties);

  for (const auto p : legacy.Properties()) {
    verify_property(p.type, p);
  }
}

class BtPropertyDynamicArrayTest : public testing::Test {
 protected:
  void SetUp() override {
    fill_properties(properties);
    props = (bt_property_t*)malloc(sizeof(bt_property_t) * properties.size());
  }
  void TearDown() override {
    free(props);
  }

  bt_property_t* props{nullptr};
  std::vector<std::shared_ptr<BtProperty>> properties;
};

TEST_F(BtPropertyDynamicArrayTest, serialize_and_verify) {
  BtPropertyLegacy legacy(properties);
  // Legacy now has complete copy of properties
  properties.clear();

  legacy.Export(props, kNumberTestedProperties);

  bt_property_t* p = props;
  for (size_t i = 0; i < kNumberTestedProperties; i++) {
    verify_property(p->type, *p);
  }
}

class BtPropertyMultiAllocationTest : public testing::Test {
 protected:
  static constexpr size_t kNumProperties = 1;
  static constexpr size_t kNumThreads = 20;

  void SetUp() override {}
  void TearDown() override {}

  std::vector<std::future<std::vector<std::shared_ptr<BtProperty>>>> future_vector;
  bt_property_t bt_properties[kNumberTestedProperties][kNumThreads] = {};

  std::vector<std::shared_ptr<BtProperty>> properties;
};

TEST_F(BtPropertyMultiAllocationTest, async_data_multi) {
  for (size_t i = 0; i < kNumThreads; i++) {
    future_vector.push_back(std::async(std::launch::async, [i]() {
      std::vector<std::shared_ptr<BtProperty>> properties;
      properties.push_back(RemoteDeviceTimestamp::Create((uint32_t)i));
      return properties;
    }));
  }

  for (size_t i = 0; i < kNumThreads; i++) {
    std::vector<std::shared_ptr<BtProperty>> props = future_vector[i].get();
    BtPropertyLegacy legacy(props);
    memcpy(
        bt_properties[i], (const void*)legacy.Ptr(), (size_t)legacy.Len() * sizeof(bt_property_t));

    ASSERT_EQ(BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP, (int)bt_properties[i]->type);
    ASSERT_EQ((int)sizeof(uint32_t), bt_properties[i]->len);
    ASSERT_EQ((int)i, *(int*)bt_properties[i]->val);
  }
}
