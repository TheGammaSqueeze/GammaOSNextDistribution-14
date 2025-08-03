/*
 * Copyright 2019 The Android Open Source Project
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
#include <memory>

#define PACKET_TESTING  // Instantiate the tests in the packet files
#include "hci/hci_packets.h"

#include "os/log.h"
#include "packet/bit_inserter.h"
#include "packet/raw_builder.h"

using bluetooth::packet::BitInserter;
using std::vector;

namespace bluetooth {
namespace hci {

std::vector<uint8_t> pixel_3_xl_write_extended_inquiry_response{
    0x52, 0x0c, 0xf1, 0x01, 0x0b, 0x09, 0x50, 0x69, 0x78, 0x65, 0x6c, 0x20, 0x33, 0x20, 0x58, 0x4c, 0x19, 0x03, 0x05,
    0x11, 0x0a, 0x11, 0x0c, 0x11, 0x0e, 0x11, 0x12, 0x11, 0x15, 0x11, 0x16, 0x11, 0x1f, 0x11, 0x2d, 0x11, 0x2f, 0x11,
    0x00, 0x12, 0x32, 0x11, 0x01, 0x05, 0x81, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

std::vector<uint8_t> pixel_3_xl_write_extended_inquiry_response_no_uuids{
    0x52, 0x0c, 0xf1, 0x01, 0x0b, 0x09, 0x50, 0x69, 0x78, 0x65, 0x6c, 0x20, 0x33, 0x20, 0x58, 0x4c, 0x01, 0x03, 0x01,
    0x05, 0x81, 0x07, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00};

std::vector<uint8_t> pixel_3_xl_write_extended_inquiry_response_no_uuids_just_eir{
    pixel_3_xl_write_extended_inquiry_response_no_uuids.begin() + 4,  // skip command, size, and fec_required
    pixel_3_xl_write_extended_inquiry_response_no_uuids.end()};

TEST(HciPacketsTest, testWriteExtendedInquiryResponse) {
  std::shared_ptr<std::vector<uint8_t>> view_bytes =
      std::make_shared<std::vector<uint8_t>>(pixel_3_xl_write_extended_inquiry_response);

  PacketView<kLittleEndian> packet_bytes_view(view_bytes);
  auto view = WriteExtendedInquiryResponseView::Create(CommandView::Create(packet_bytes_view));
  ASSERT_TRUE(view.IsValid());
  auto gap_data = view.GetExtendedInquiryResponse();
  ASSERT_GE(gap_data.size(), 4ul);
  ASSERT_EQ(gap_data[0].data_type_, GapDataType::COMPLETE_LOCAL_NAME);
  ASSERT_EQ(gap_data[0].data_.size(), 10ul);
  ASSERT_EQ(gap_data[1].data_type_, GapDataType::COMPLETE_LIST_16_BIT_UUIDS);
  ASSERT_EQ(gap_data[1].data_.size(), 24ul);
  ASSERT_EQ(gap_data[2].data_type_, GapDataType::COMPLETE_LIST_32_BIT_UUIDS);
  ASSERT_EQ(gap_data[2].data_.size(), 0ul);
  ASSERT_EQ(gap_data[3].data_type_, GapDataType::COMPLETE_LIST_128_BIT_UUIDS);
  ASSERT_EQ(gap_data[3].data_.size(), 128ul);

  std::vector<GapData> no_padding{gap_data.begin(), gap_data.begin() + 4};
  auto builder = WriteExtendedInquiryResponseBuilder::Create(view.GetFecRequired(), no_padding);

  std::shared_ptr<std::vector<uint8_t>> packet_bytes = std::make_shared<std::vector<uint8_t>>();
  BitInserter it(*packet_bytes);
  builder->Serialize(it);

  EXPECT_EQ(packet_bytes->size(), view_bytes->size());
  for (size_t i = 0; i < view_bytes->size(); i++) {
    ASSERT_EQ(packet_bytes->at(i), view_bytes->at(i));
  }
}

//  TODO: Revisit reflection tests for EIR
// DEFINE_AND_INSTANTIATE_WriteExtendedInquiryResponseReflectionTest(pixel_3_xl_write_extended_inquiry_response,
// pixel_3_xl_write_extended_inquiry_response_no_uuids);

std::vector<uint8_t> le_set_scan_parameters{
    0x0b, 0x20, 0x07, 0x01, 0x12, 0x00, 0x12, 0x00, 0x01, 0x00,
};
TEST(HciPacketsTest, testLeSetScanParameters) {
  PacketView<kLittleEndian> packet_bytes_view(std::make_shared<std::vector<uint8_t>>(le_set_scan_parameters));
  auto view = LeSetScanParametersView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(LeScanType::ACTIVE, view.GetLeScanType());
  ASSERT_EQ(0x12, view.GetLeScanInterval());
  ASSERT_EQ(0x12, view.GetLeScanWindow());
  ASSERT_EQ(OwnAddressType::RANDOM_DEVICE_ADDRESS, view.GetOwnAddressType());
  ASSERT_EQ(LeScanningFilterPolicy::ACCEPT_ALL, view.GetScanningFilterPolicy());
}

std::vector<uint8_t> le_set_scan_enable{
    0x0c, 0x20, 0x02, 0x01, 0x00,
};
TEST(HciPacketsTest, testLeSetScanEnable) {
  PacketView<kLittleEndian> packet_bytes_view(std::make_shared<std::vector<uint8_t>>(le_set_scan_enable));
  auto view = LeSetScanEnableView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(Enable::ENABLED, view.GetLeScanEnable());
  ASSERT_EQ(Enable::DISABLED, view.GetFilterDuplicates());
}

std::vector<uint8_t> le_get_vendor_capabilities{
    0x53,
    0xfd,
    0x00,
};
TEST(HciPacketsTest, testLeGetVendorCapabilities) {
  PacketView<kLittleEndian> packet_bytes_view(std::make_shared<std::vector<uint8_t>>(le_get_vendor_capabilities));
  auto view = LeGetVendorCapabilitiesView::Create(VendorCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
}

std::vector<uint8_t> le_get_vendor_capabilities_complete{
    0x0e, 0x0c, 0x01, 0x53, 0xfd, 0x00, 0x05, 0x01, 0x00, 0x04, 0x80, 0x01, 0x10, 0x01,
};
TEST(HciPacketsTest, testLeGetVendorCapabilitiesComplete) {
  PacketView<kLittleEndian> packet_bytes_view(
      std::make_shared<std::vector<uint8_t>>(le_get_vendor_capabilities_complete));
  auto view =
      LeGetVendorCapabilitiesCompleteView::Create(CommandCompleteView::Create(EventView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  auto base_capabilities = view.GetBaseVendorCapabilities();
  ASSERT_EQ(5, base_capabilities.max_advt_instances_);
  ASSERT_EQ(1, base_capabilities.offloaded_resolution_of_private_address_);
  ASSERT_EQ(1024, base_capabilities.total_scan_results_storage_);
  ASSERT_EQ(128, base_capabilities.max_irk_list_sz_);
  ASSERT_EQ(1, base_capabilities.filtering_support_);
  ASSERT_EQ(16, base_capabilities.max_filter_);
  ASSERT_EQ(1, base_capabilities.activity_energy_info_support_);
}

std::vector<uint8_t> le_set_extended_scan_parameters{
    0x41, 0x20, 0x08, 0x01, 0x00, 0x01, 0x01, 0x12, 0x00, 0x12, 0x00,
};

TEST(HciPacketsTest, testLeSetExtendedScanParameters) {
  PacketView<kLittleEndian> packet_bytes_view(std::make_shared<std::vector<uint8_t>>(le_set_extended_scan_parameters));
  auto view =
      LeSetExtendedScanParametersView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(1, view.GetScanningPhys());
  auto params = view.GetParameters();
  ASSERT_EQ(1ul, params.size());
  ASSERT_EQ(LeScanType::ACTIVE, params[0].le_scan_type_);
  ASSERT_EQ(18, params[0].le_scan_interval_);
  ASSERT_EQ(18, params[0].le_scan_window_);
}

std::vector<uint8_t> le_set_extended_scan_parameters_6553{
    0x41, 0x20, 0x08, 0x01, 0x00, 0x01, 0x01, 0x99, 0x19, 0x99, 0x19,
};

TEST(HciPacketsTest, testLeSetExtendedScanParameters_6553) {
  PacketView<kLittleEndian> packet_bytes_view(
      std::make_shared<std::vector<uint8_t>>(le_set_extended_scan_parameters_6553));
  auto view =
      LeSetExtendedScanParametersView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(1, view.GetScanningPhys());
  auto params = view.GetParameters();
  ASSERT_EQ(1ul, params.size());
  ASSERT_EQ(LeScanType::ACTIVE, params[0].le_scan_type_);
  ASSERT_EQ(6553, params[0].le_scan_interval_);
  ASSERT_EQ(6553, params[0].le_scan_window_);
}

std::vector<uint8_t> le_set_extended_scan_enable{
    0x42, 0x20, 0x06, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
};

TEST(HciPacketsTest, testLeSetExtendedScanEnable) {
  PacketView<kLittleEndian> packet_bytes_view(std::make_shared<std::vector<uint8_t>>(le_set_extended_scan_enable));
  auto view =
      LeSetExtendedScanEnableView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(FilterDuplicates::DISABLED, view.GetFilterDuplicates());
  ASSERT_EQ(Enable::ENABLED, view.GetEnable());
  ASSERT_EQ(0, view.GetDuration());
  ASSERT_EQ(0, view.GetPeriod());
}

std::vector<uint8_t> le_set_extended_scan_enable_disable{
    0x42, 0x20, 0x06, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00,
};

TEST(HciPacketsTest, testLeSetExtendedScanEnableDisable) {
  PacketView<kLittleEndian> packet_bytes_view(
      std::make_shared<std::vector<uint8_t>>(le_set_extended_scan_enable_disable));
  auto view =
      LeSetExtendedScanEnableView::Create(LeScanningCommandView::Create(CommandView::Create(packet_bytes_view)));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(FilterDuplicates::ENABLED, view.GetFilterDuplicates());
  ASSERT_EQ(Enable::DISABLED, view.GetEnable());
  ASSERT_EQ(0, view.GetDuration());
  ASSERT_EQ(0, view.GetPeriod());
}

std::vector<uint8_t> le_extended_create_connection = {
    0x43, 0x20, 0x2a, 0x01, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x03, 0x00, 0x08,
    0x30, 0x00, 0x18, 0x00, 0x28, 0x00, 0x00, 0x00, 0xf4, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00,
    0x08, 0x30, 0x00, 0x18, 0x00, 0x28, 0x00, 0x00, 0x00, 0xf4, 0x01, 0x00, 0x00, 0x00, 0x00};

TEST(HciPacketsTest, testLeExtendedCreateConnection) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_extended_create_connection);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeExtendedCreateConnectionView::Create(
      LeConnectionManagementCommandView::Create(AclCommandView::Create(CommandView::Create(packet_bytes_view))));
  ASSERT_TRUE(view.IsValid());
}

std::vector<uint8_t> le_set_extended_advertising_random_address = {
    0x35, 0x20, 0x07, 0x00, 0x77, 0x58, 0xeb, 0xd3, 0x1c, 0x6e,
};

TEST(HciPacketsTest, testLeSetAdvertisingSetRandomAddress) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_random_address);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetAdvertisingSetRandomAddressView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  uint8_t random_address_bytes[] = {0x77, 0x58, 0xeb, 0xd3, 0x1c, 0x6e};
  ASSERT_EQ(0, view.GetAdvertisingHandle());
  ASSERT_EQ(Address(random_address_bytes), view.GetRandomAddress());
}

std::vector<uint8_t> le_set_extended_advertising_data{
    0x37, 0x20, 0x12, 0x00, 0x03, 0x01, 0x0e, 0x02, 0x01, 0x02, 0x0a,
    0x09, 0x50, 0x69, 0x78, 0x65, 0x6c, 0x20, 0x33, 0x20, 0x58,
};
TEST(HciPacketsTest, testLeSetExtendedAdvertisingData) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_data);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetExtendedAdvertisingDataRawView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(0, view.GetAdvertisingHandle());
  ASSERT_EQ(Operation::COMPLETE_ADVERTISEMENT, view.GetOperation());
  ASSERT_EQ(FragmentPreference::CONTROLLER_SHOULD_NOT, view.GetFragmentPreference());
  std::vector<uint8_t> advertising_data{
      0x02, 0x01, 0x02, 0x0a, 0x09, 0x50, 0x69, 0x78, 0x65, 0x6c, 0x20, 0x33, 0x20, 0x58,
  };
  auto payload = view.GetPayload();
  std::vector<uint8_t> payload_data(payload.begin(), payload.end());
  ASSERT_EQ(advertising_data, payload_data);
}

std::vector<uint8_t> le_set_extended_advertising_parameters_set_0{
    0x36, 0x20, 0x19, 0x00, 0x13, 0x00, 0x90, 0x01, 0x00, 0xc2, 0x01, 0x00, 0x07, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xf9, 0x01, 0x00, 0x01, 0x01, 0x00,
};
TEST(HciPacketsTest, testLeSetExtendedAdvertisingParametersLegacySet0) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_parameters_set_0);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetExtendedAdvertisingParametersLegacyView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(0, view.GetAdvertisingHandle());
  ASSERT_EQ(400ul, view.GetPrimaryAdvertisingIntervalMin());
  ASSERT_EQ(450ul, view.GetPrimaryAdvertisingIntervalMax());
  ASSERT_EQ(0x7, view.GetPrimaryAdvertisingChannelMap());
  ASSERT_EQ(OwnAddressType::RANDOM_DEVICE_ADDRESS, view.GetOwnAddressType());
  ASSERT_EQ(PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS, view.GetPeerAddressType());
  ASSERT_EQ(Address::kEmpty, view.GetPeerAddress());
  ASSERT_EQ(AdvertisingFilterPolicy::ALL_DEVICES, view.GetAdvertisingFilterPolicy());
  ASSERT_EQ(1, view.GetAdvertisingSid());
  ASSERT_EQ(Enable::DISABLED, view.GetScanRequestNotificationEnable());
}

std::vector<uint8_t> le_set_extended_advertising_parameters_set_1{
    0x36, 0x20, 0x19, 0x01, 0x13, 0x00, 0x90, 0x01, 0x00, 0xc2, 0x01, 0x00, 0x07, 0x01,
    0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0xf9, 0x01, 0x00, 0x01, 0x01, 0x00,
};
TEST(HciPacketsTest, testLeSetExtendedAdvertisingParametersSet1) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_parameters_set_1);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetExtendedAdvertisingParametersLegacyView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(1, view.GetAdvertisingHandle());
  ASSERT_EQ(400ul, view.GetPrimaryAdvertisingIntervalMin());
  ASSERT_EQ(450ul, view.GetPrimaryAdvertisingIntervalMax());
  ASSERT_EQ(0x7, view.GetPrimaryAdvertisingChannelMap());
  ASSERT_EQ(OwnAddressType::RANDOM_DEVICE_ADDRESS, view.GetOwnAddressType());
  ASSERT_EQ(PeerAddressType::PUBLIC_DEVICE_OR_IDENTITY_ADDRESS, view.GetPeerAddressType());
  ASSERT_EQ(Address::kEmpty, view.GetPeerAddress());
  ASSERT_EQ(AdvertisingFilterPolicy::ALL_DEVICES, view.GetAdvertisingFilterPolicy());
  ASSERT_EQ(1, view.GetAdvertisingSid());
  ASSERT_EQ(Enable::DISABLED, view.GetScanRequestNotificationEnable());
}

std::vector<uint8_t> le_set_extended_advertising_parameters_complete{0x0e, 0x05, 0x01, 0x36, 0x20, 0x00, 0xf5};
TEST(HciPacketsTest, testLeSetExtendedAdvertisingParametersComplete) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_parameters_complete);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetExtendedAdvertisingParametersCompleteView::Create(
      CommandCompleteView::Create(EventView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(static_cast<uint8_t>(-11), view.GetSelectedTxPower());
}

std::vector<uint8_t> le_remove_advertising_set_1{
    0x3c,
    0x20,
    0x01,
    0x01,
};
TEST(HciPacketsTest, testLeRemoveAdvertisingSet1) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_remove_advertising_set_1);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view =
      LeRemoveAdvertisingSetView::Create(LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(1, view.GetAdvertisingHandle());
}

std::vector<uint8_t> le_set_extended_advertising_disable_1{
    0x39, 0x20, 0x06, 0x00, 0x01, 0x01, 0x00, 0x00, 0x00,
};
TEST(HciPacketsTest, testLeSetExtendedAdvertisingDisable1) {
  std::shared_ptr<std::vector<uint8_t>> packet_bytes =
      std::make_shared<std::vector<uint8_t>>(le_set_extended_advertising_disable_1);
  PacketView<kLittleEndian> packet_bytes_view(packet_bytes);
  auto view = LeSetExtendedAdvertisingDisableView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(packet_bytes_view)));
  ASSERT_TRUE(view.IsValid());
  auto disabled_set = view.GetDisabledSets();
  ASSERT_EQ(1ul, disabled_set.size());
  ASSERT_EQ(1, disabled_set[0].advertising_handle_);
}

TEST(HciPacketsTest, testLeSetAdvertisingDataBuilderLength) {
  GapData gap_data;
  gap_data.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  gap_data.data_ = std::vector<uint8_t>({'A', ' ', 'g', 'o', 'o', 'd', ' ', 'n', 'a', 'm', 'e'});
  auto builder = LeSetAdvertisingDataBuilder::Create({gap_data});
  ASSERT_EQ(2ul /*opcode*/ + 1ul /* parameter size */ + 1ul /* data_length */ + 31ul /* data */, builder->size());

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);
  auto command_view = LeAdvertisingCommandView::Create(CommandView::Create(PacketView<kLittleEndian>(packet_bytes)));
  ASSERT_TRUE(command_view.IsValid());
  ASSERT_EQ(1ul /* data_length */ + 31ul /* data */, command_view.GetPayload().size());
  auto view = LeSetAdvertisingDataView::Create(command_view);
  ASSERT_TRUE(view.IsValid());
}

TEST(HciPacketsTest, testLeSetScanResponseDataBuilderLength) {
  GapData gap_data;
  gap_data.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  gap_data.data_ = std::vector<uint8_t>({'A', ' ', 'g', 'o', 'o', 'd', ' ', 'n', 'a', 'm', 'e'});
  auto builder = LeSetScanResponseDataBuilder::Create({gap_data});
  ASSERT_EQ(2ul /*opcode*/ + 1ul /* parameter size */ + 1ul /*data_length */ + 31ul /* data */, builder->size());

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);
  auto command_view = LeAdvertisingCommandView::Create(CommandView::Create(PacketView<kLittleEndian>(packet_bytes)));
  ASSERT_TRUE(command_view.IsValid());
  ASSERT_EQ(1ul /* data_length */ + 31ul /* data */, command_view.GetPayload().size());
  auto view = LeSetScanResponseDataView::Create(command_view);
  ASSERT_TRUE(view.IsValid());
}

TEST(HciPacketsTest, testLeMultiAdvSetAdvertisingDataBuilderLength) {
  GapData gap_data;
  gap_data.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  gap_data.data_ = std::vector<uint8_t>({'A', ' ', 'g', 'o', 'o', 'd', ' ', 'n', 'a', 'm', 'e'});
  uint8_t set = 3;
  auto builder = LeMultiAdvtSetDataBuilder::Create({gap_data}, set);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);
  auto command_view = LeMultiAdvtSetDataView::Create(LeMultiAdvtView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(PacketView<kLittleEndian>(packet_bytes)))));
  ASSERT_TRUE(command_view.IsValid());
  auto view = LeMultiAdvtSetDataView::Create(command_view);
  ASSERT_TRUE(view.IsValid());
  ASSERT_TRUE(view.GetAdvertisingData().size() > 0ul);
  ASSERT_EQ(view.GetAdvertisingData()[0].data_, gap_data.data_);
  ASSERT_EQ(view.GetAdvertisingInstance(), 3);
}

TEST(HciPacketsTest, testLeMultiAdvSetScanResponseDataBuilderLength) {
  GapData gap_data;
  gap_data.data_type_ = GapDataType::COMPLETE_LOCAL_NAME;
  gap_data.data_ = std::vector<uint8_t>({'A', ' ', 'g', 'o', 'o', 'd', ' ', 'n', 'a', 'm', 'e'});
  uint8_t set = 3;
  auto builder = LeMultiAdvtSetScanRespBuilder::Create({gap_data}, set);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);
  auto command_view = LeMultiAdvtSetScanRespView::Create(LeMultiAdvtView::Create(
      LeAdvertisingCommandView::Create(CommandView::Create(PacketView<kLittleEndian>(packet_bytes)))));
  ASSERT_TRUE(command_view.IsValid());
  auto view = LeMultiAdvtSetScanRespView::Create(command_view);
  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(view.GetAdvertisingData()[0].data_, gap_data.data_);
  ASSERT_EQ(view.GetAdvertisingInstance(), 3);
}

TEST(HciPacketsTest, testMsftReadSupportedFeatures) {
  // MSFT opcode is not defined in PDL.
  auto msft_opcode = static_cast<OpCode>(0xfc01);

  auto builder = MsftReadSupportedFeaturesBuilder::Create(msft_opcode);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);

  std::vector<uint8_t> expected_bytes{
      0x01,  // Vendor command opcode and MSFT base code.
      0xfc,
      0x01,  // Packet length
      0x00,  // Subcommand Opcode for Read Supported Features
  };
  ASSERT_EQ(expected_bytes, *packet_bytes);
}

TEST(HciPacketsTest, testMsftLeMonitorAdvUuid) {
  // MSFT opcode is not defined in PDL.
  auto msft_opcode = static_cast<OpCode>(0xfc01);

  auto builder = MsftLeMonitorAdvConditionUuid2Builder::Create(
      msft_opcode,
      0x10 /* RSSI threshold high */,
      0x11 /* RSSI threshold low */,
      0x12 /* RSSI threshold low timeout */,
      0x13 /* RSSI sampling period */,
      std::array<uint8_t, 2>{0x71, 0x72} /* 16-bit UUID */);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);

  std::vector<uint8_t> expected_bytes{
      0x01,  // Vendor command opcode and MSFT base code.
      0xfc,
      0x09,  // Packet length
      0x03,  // Subcommand Opcode for LE Monitor Adv
      0x10,  // RSSI threshold high
      0x11,  // RSSI threshold low
      0x12,  // RSSI threshold low timeout
      0x13,  // RSSI sampling period
      0x02,  // Condition type = UUID
      0x01,  // UUID type = 16-bit UUID
      0x71,  // UUID content
      0x72,
  };
  ASSERT_EQ(expected_bytes, *packet_bytes);
}

TEST(HciPacketsTest, testMsftLeMonitorAdvPatternsEmpty) {
  // MSFT opcode is not defined in PDL.
  auto msft_opcode = static_cast<OpCode>(0xfc01);

  std::vector<MsftLeMonitorAdvConditionPattern> patterns;

  auto builder = MsftLeMonitorAdvConditionPatternsBuilder::Create(
      msft_opcode,
      0x10 /* RSSI threshold high */,
      0x11 /* RSSI threshold low */,
      0x12 /* RSSI threshold low timeout */,
      0x13 /* RSSI sampling period */,
      patterns);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);

  std::vector<uint8_t> expected_bytes{
      0x01,  // Vendor command opcode and MSFT base code.
      0xfc,
      0x07,  // Packet length
      0x03,  // Subcommand Opcode for LE Monitor Adv
      0x10,  // RSSI threshold high
      0x11,  // RSSI threshold low
      0x12,  // RSSI threshold low timeout
      0x13,  // RSSI sampling period
      0x01,  // Condition type = Patterns
      0x00,  // Number of patterns
  };
  ASSERT_EQ(expected_bytes, *packet_bytes);
}

TEST(HciPacketsTest, testMsftLeMonitorAdvPatterns) {
  // MSFT opcode is not defined in PDL.
  auto msft_opcode = static_cast<OpCode>(0xfc01);

  MsftLeMonitorAdvConditionPattern pattern1;
  pattern1.ad_type_ = 0x03;
  pattern1.start_of_pattern_ = 0x00;
  pattern1.pattern_ = {1, 2, 3};

  MsftLeMonitorAdvConditionPattern pattern2;
  pattern2.ad_type_ = 0x0f;
  pattern2.start_of_pattern_ = 0x10;
  pattern2.pattern_ = {0xa1, 0xa2};

  std::vector<MsftLeMonitorAdvConditionPattern> patterns{pattern1, pattern2};

  auto builder = MsftLeMonitorAdvConditionPatternsBuilder::Create(
      msft_opcode,
      0x10 /* RSSI threshold high */,
      0x11 /* RSSI threshold low */,
      0x12 /* RSSI threshold low timeout */,
      0x13 /* RSSI sampling period */,
      patterns);

  auto packet_bytes = std::make_shared<std::vector<uint8_t>>();
  packet_bytes->reserve(builder->size());
  BitInserter bit_inserter(*packet_bytes);
  builder->Serialize(bit_inserter);

  std::vector<uint8_t> expected_bytes{
      0x01,  // Vendor command opcode and MSFT base code.
      0xfc,
      0x12,  // Packet length
      0x03,  // Subcommand Opcode for LE Monitor Adv
      0x10,  // RSSI threshold high
      0x11,  // RSSI threshold low
      0x12,  // RSSI threshold low timeout
      0x13,  // RSSI sampling period
      0x01,  // Condition type = Patterns
      0x02,  // Number of patterns
      // Pattern 1
      0x05,  // Length
      0x03,  // AD Type
      0x00,  // Start of pattern
      0x01,  // Pattern
      0x02,
      0x03,
      // Pattern 2
      0x04,  // Length
      0x0f,  // AD Type
      0x10,  // Start of pattern
      0xa1,  // Pattern
      0xa2,
  };
  ASSERT_EQ(expected_bytes, *packet_bytes);
}

std::vector<uint8_t> msft_read_supported_features_complete{
    0x0e,  // command complete event code
    0x10,  // event size
    0x01,  // num_hci_command_packets
    0x1e,
    0xfc,  // vendor specific MSFT opcode assigned by Intel
    0x00,  // status
    0x00,  // MSFT subcommand opcode
    0x7f,
    0x00,
    0x00,
    0x00,
    0x00,
    0x00,
    0x00,
    0x00,  // supported features
    0x02,  // MSFT event prefix length
    0x87,
    0x80,  // prefix: MSFT event prefix provided by Intel
};
TEST(HciPacketsTest, testMsftReadSupportedFeaturesComplete) {
  PacketView<kLittleEndian> packet_bytes_view(
      std::make_shared<std::vector<uint8_t>>(msft_read_supported_features_complete));
  auto view = MsftReadSupportedFeaturesCommandCompleteView::Create(
      MsftCommandCompleteView::Create(CommandCompleteView::Create(EventView::Create(packet_bytes_view))));

  ASSERT_TRUE(view.IsValid());
  ASSERT_EQ(ErrorCode::SUCCESS, view.GetStatus());
  ASSERT_EQ((uint8_t)0x00, (uint8_t)view.GetSubcommandOpcode());
  ASSERT_EQ((uint64_t)0x000000000000007f, view.GetSupportedFeatures());
  ASSERT_EQ(2ul, view.GetPrefix().size());

  uint16_t prefix = 0;
  for (auto p : view.GetPrefix()) prefix = (prefix << 8) + p;
  ASSERT_EQ((uint16_t)0x8780, prefix);
}

}  // namespace hci
}  // namespace bluetooth
