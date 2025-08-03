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

#include "discovery/device/eir_data.h"

#include "discovery/device/eir_test_data_packets.h"
#include "gmock/gmock.h"
#include "gtest/gtest.h"
#include "hci/hci_packets.h"
#include "os/log.h"

using namespace bluetooth;
using bluetooth::discovery::device::EirData;

namespace {
constexpr uint8_t kPartialUuid16Data[] = {
    0x2, static_cast<uint8_t>(hci::GapDataType::COMPLETE_LIST_16_BIT_UUIDS), 0x34};
constexpr uint8_t kOneUuid16Data[] = {
    0x3, static_cast<uint8_t>(hci::GapDataType::COMPLETE_LIST_16_BIT_UUIDS), 0x34, 0x12};
constexpr char kAudiMmi9962[] = "Audi_MMI_9962";
constexpr char kChromeBoxForMeetings[] = "Chromebox for Meetings";

}  // namespace

namespace debug {
void LogUuids16(const std::vector<uint16_t>& uuids16) {
  for (const auto& uuid : uuids16) {
    LOG_INFO("  uuid:0x%x", uuid);
  }
}

void LogUuids128(const std::vector<hci::Uuid>& uuids128) {
  for (const auto& uuid : uuids128) {
    LOG_INFO("  uuid:%s", uuid.ToString().c_str());
  }
}
}  // namespace debug

TEST(EirDataTest, partial_uuid16) {
  const EirData eir_data(
      std::vector<uint8_t>(kPartialUuid16Data, kPartialUuid16Data + sizeof(kPartialUuid16Data)));

  std::vector<uint16_t> uuids;
  ASSERT_FALSE(eir_data.GetUuids16(uuids));
}

TEST(EirDataTest, one_uuid16) {
  const EirData eir_data(
      std::vector<uint8_t>(kOneUuid16Data, kOneUuid16Data + sizeof(kOneUuid16Data)));

  std::vector<uint16_t> uuids;
  ASSERT_TRUE(eir_data.GetUuids16(uuids));
  ASSERT_EQ(1U, uuids.size());
  ASSERT_EQ(0x1234, uuids[0]);
}

TEST(EirDataTest, test_data_packets__data_type) {
  ASSERT_EQ(1U, selected_packets.count("pkt34639"));
  const auto& pkt = selected_packets["pkt34639"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<hci::GapDataType> gap_data_types = eir_data.GetDataTypes();
  ASSERT_EQ(6U, gap_data_types.size());
}

TEST(EirDataTest, test_data_packets__complete_name) {
  ASSERT_EQ(1U, selected_packets.count("pkt34639"));
  const auto& pkt = selected_packets["pkt34639"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<std::array<uint8_t, kEirSize>> names;
  ASSERT_TRUE(eir_data.GetCompleteNames(names));
  ASSERT_EQ(1U, names.size());
  std::string name(names[0].begin(), names[0].end());
  ASSERT_STREQ(kAudiMmi9962, name.c_str());
}

TEST(EirDataTest, test_data_packets__uuids16) {
  ASSERT_EQ(1U, selected_packets.count("pkt34639"));
  const auto& pkt = selected_packets["pkt34639"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<uint16_t> uuids16;
  ASSERT_TRUE(eir_data.GetUuids16(uuids16));
  ASSERT_EQ(14U, uuids16.size());
  ASSERT_EQ(0x112e, uuids16[0]);
  ASSERT_EQ(0x180a, uuids16[13]);
}

TEST(EirDataTest, test_data_packets__uuids16_incomplete) {
  ASSERT_EQ(1U, selected_packets.count("pkt19200"));
  const auto& pkt = selected_packets["pkt19200"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<uint16_t> uuids16;
  ASSERT_TRUE(eir_data.GetUuidsIncomplete16(uuids16));
  ASSERT_EQ(7U, uuids16.size());
  ASSERT_EQ(0x110d, uuids16[0]);
  ASSERT_EQ(0x1131, uuids16[6]);
}

TEST(EirDataTest, test_data_packets__device_id) {
  ASSERT_EQ(1U, selected_packets.count("pkt2062"));
  const auto& pkt = selected_packets["pkt2062"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<std::vector<uint8_t>> device_ids;
  ASSERT_TRUE(eir_data.GetDeviceId(device_ids));
  ASSERT_EQ(1U, device_ids.size());
  ASSERT_EQ(0x01, device_ids[0][0]);
}

TEST(EirDataTest, test_data_packets__manufacturer_data) {
  ASSERT_EQ(1U, selected_packets.count("pkt26171"));
  const auto& pkt = selected_packets["pkt26171"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<std::vector<uint8_t>> mfr_data;
  ASSERT_TRUE(eir_data.GetManufacturerSpecificData(mfr_data));
  ASSERT_EQ(1U, mfr_data.size());
  ASSERT_EQ(0, mfr_data[0][0]);
}

TEST(EirDataTest, test_data_packets__security_manager_oob_flags) {
  ASSERT_EQ(1U, selected_packets.count("pkt26171"));
  const auto& pkt = selected_packets["pkt26171"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<std::vector<uint8_t>> oob_flags;
  ASSERT_TRUE(eir_data.GetManufacturerSpecificData(oob_flags));
  ASSERT_EQ(1U, oob_flags.size());
  ASSERT_EQ(0, oob_flags[0][0]);
}

TEST(EirDataTest, test_data_packets__service_uuids16) {
  ASSERT_EQ(1U, selected_packets.count("pktAsha"));
  const auto& pkt = selected_packets["pktAsha"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<discovery::device::service_uuid16_t> service_uuids16;
  ASSERT_TRUE(eir_data.GetServiceUuuids16(service_uuids16));
  ASSERT_EQ(1U, service_uuids16.size());
  ASSERT_EQ(0xfdf0, service_uuids16[0].uuid);
}

TEST(EirDataTest, test_data_packets__service_uuids32) {
  for (const auto& pkt : data_packets) {
    const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));
    std::vector<discovery::device::service_uuid32_t> service_uuids32;
    ASSERT_FALSE(eir_data.GetServiceUuuids32(service_uuids32));
  }
}

TEST(EirDataTest, test_data_packets__tx_power_level) {
  ASSERT_EQ(1U, selected_packets.count("pkt34639"));
  const auto& pkt = selected_packets["pkt34639"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<int8_t> levels;
  ASSERT_TRUE(eir_data.GetTxPowerLevel(levels));
  ASSERT_EQ(1U, levels.size());
  ASSERT_EQ(4, levels[0]);
}

TEST(EirDataTest, test_select_packets__pktAsha) {
  ASSERT_EQ(1U, selected_packets.count("pktAsha"));
  const auto& pkt = selected_packets["pktAsha"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<std::array<uint8_t, kEirSize>> names;
  ASSERT_TRUE(eir_data.GetCompleteNames(names));
  std::string name(names[0].begin(), names[0].end());
  ASSERT_STREQ(kChromeBoxForMeetings, name.c_str());

  std::vector<int8_t> tx_power_level;
  ASSERT_TRUE(eir_data.GetTxPowerLevel(tx_power_level));
  ASSERT_EQ(10, tx_power_level[0]);

  const std::vector<uint8_t> v1 =
      std::vector<uint8_t>({0x01, 0x00, 0xe0, 0x00, 0x05, 0xc4, 0x6c, 0x00});
  std::vector<std::vector<uint8_t>> device_ids;
  ASSERT_TRUE(eir_data.GetDeviceId(device_ids));
  ASSERT_EQ(v1.size(), device_ids[0].size());
  ASSERT_THAT(v1, testing::ContainerEq(device_ids[0]));

  const std::vector<uint16_t> v2 =
      std::vector<uint16_t>({0x1800, 0x1801, 0x180a, 0x110e, 0x110c, 0x111f, 0x110a});
  std::vector<uint16_t> uuids16;
  ASSERT_TRUE(eir_data.GetUuids16(uuids16));
  ASSERT_EQ(v2.size(), uuids16.size());
  ASSERT_THAT(v2, testing::ContainerEq(uuids16));

  std::vector<discovery::device::service_uuid16_t> service_uuids16;
  ASSERT_TRUE(eir_data.GetServiceUuuids16(service_uuids16));
  ASSERT_EQ(1U, service_uuids16.size());
  ASSERT_EQ(0xfdf0, service_uuids16[0].uuid);
}

TEST(EirDataTest, test_select_packets__pkt34639) {
  ASSERT_EQ(1U, selected_packets.count("pkt34639"));
  const auto& pkt = selected_packets["pkt34639"];
  const EirData eir_data(std::vector<uint8_t>(&pkt[kEirOffset], &pkt[kEirOffset] + kEirSize));

  std::vector<uint16_t> uuids16;
  ASSERT_TRUE(eir_data.GetUuids16(uuids16));
  ASSERT_EQ(14U, uuids16.size());
  ASSERT_EQ(0x112e, uuids16[0]);
  ASSERT_EQ(0x180a, uuids16[13]);

  std::vector<uint32_t> uuids32;
  ASSERT_FALSE(eir_data.GetUuids32(uuids32));
  ASSERT_EQ(0U, uuids32.size());

  std::vector<hci::Uuid> uuids128;
  ASSERT_TRUE(eir_data.GetUuids128(uuids128));

  ASSERT_EQ(hci::Uuid::FromString("00000000-deca-fade-deca-deafdecacaff"), uuids128[0]);

  std::vector<int8_t> tx_power_level;
  ASSERT_TRUE(eir_data.GetTxPowerLevel(tx_power_level));
  ASSERT_EQ(4, tx_power_level[0]);

  std::vector<std::array<uint8_t, 240>> names;
  ASSERT_TRUE(eir_data.GetCompleteNames(names));
  ASSERT_STREQ("Audi_MMI_9962", std::string(names[0].begin(), names[0].end()).data());
}
