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

#include "discovery/device/data_parser.h"

#include <algorithm>

#include "gtest/gtest.h"
#include "hci/hci_packets.h"

using namespace bluetooth::hci;
using bluetooth::discovery::device::DataParser;

namespace {
constexpr uint8_t kOneFlag32Data[] = {
    0x5, static_cast<uint8_t>(GapDataType::FLAGS), 0xde, 0xad, 0xbe, 0xef};
constexpr uint8_t kTwoFlag32Data[] = {
    0x5,
    static_cast<uint8_t>(GapDataType::FLAGS),
    0xde,
    0xad,
    0xbe,
    0xef,
    0x5,
    static_cast<uint8_t>(GapDataType::FLAGS),
    0x11,
    0x22,
    0x33,
    0x44};
constexpr uint8_t kNoUuid16Data[] = {
    0x2, static_cast<uint8_t>(GapDataType::COMPLETE_LIST_16_BIT_UUIDS)};
constexpr uint8_t kPartialUuid16Data[] = {
    0x2, static_cast<uint8_t>(GapDataType::COMPLETE_LIST_16_BIT_UUIDS), 0x12};
constexpr uint8_t kOneUuid16Data[] = {
    0x3, static_cast<uint8_t>(GapDataType::COMPLETE_LIST_16_BIT_UUIDS), 0x12, 0x34};

uint32_t toLeInt(const std::vector<uint8_t>& v) {
  return v[3] | (v[2] << 8) | (v[1] << 16) | (v[0] << 24);
}

}  // namespace

TEST(DataParserTest, no_data) {
  auto data = std::make_shared<std::vector<uint8_t>>();

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
}

TEST(DataParserTest, one_element_data) {
  auto data = std::make_shared<std::vector<uint8_t>>(1);
  data->push_back(0xff);

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
}

TEST(DataParserTest, two_element_data) {
  auto data = std::make_shared<std::vector<uint8_t>>(2);
  data->push_back(0xff);
  data->push_back(0xff);

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
}

TEST(DataParserTest, all_ones_data) {
  auto data = std::make_shared<std::vector<uint8_t>>(256);
  std::fill(data->begin(), data->end(), 0xff);

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
}

TEST(DataParserTest, simple_flag) {
  auto data = std::make_shared<std::vector<uint8_t>>(
      kOneFlag32Data, kOneFlag32Data + sizeof(kOneFlag32Data));

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
  ASSERT_EQ(gap_data.data_type_, GapDataType::FLAGS);
  ASSERT_EQ(0xdeadbeef, toLeInt(gap_data.data_));
}

TEST(DataParserTest, two_flags) {
  auto data = std::make_shared<std::vector<uint8_t>>(
      kTwoFlag32Data, kTwoFlag32Data + sizeof(kTwoFlag32Data));

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data[2];
  it = GapData::Parse(&gap_data[0], it);

  ASSERT_EQ(it.NumBytesRemaining(), 1U /* length */ + 1U /* type */ + 4U /* data */);
  ASSERT_EQ(gap_data[0].data_type_, GapDataType::FLAGS);
  ASSERT_EQ((unsigned)0xdeadbeef, toLeInt(gap_data[0].data_));

  it = GapData::Parse(&gap_data[1], it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
  ASSERT_EQ(gap_data[1].data_type_, GapDataType::FLAGS);
  ASSERT_EQ((unsigned)0x11223344, toLeInt(gap_data[1].data_));
}

TEST(DataParserTest, no_uuid16) {
  auto data =
      std::make_shared<std::vector<uint8_t>>(kNoUuid16Data, kNoUuid16Data + sizeof(kNoUuid16Data));

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
  ASSERT_EQ(gap_data.data_type_, GapDataType::COMPLETE_LIST_16_BIT_UUIDS);
  ASSERT_EQ(0U, gap_data.data_.size());
}

TEST(DataParserTest, partial_uuid16) {
  auto data = std::make_shared<std::vector<uint8_t>>(
      kPartialUuid16Data, kPartialUuid16Data + sizeof(kPartialUuid16Data));

  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
  ASSERT_EQ(gap_data.data_type_, GapDataType::COMPLETE_LIST_16_BIT_UUIDS);
  ASSERT_EQ(1U, gap_data.data_.size());
}

TEST(DataParserTest, one_uuid16) {
  auto data = std::make_shared<std::vector<uint8_t>>(
      kOneUuid16Data, kOneUuid16Data + sizeof(kOneUuid16Data));
  auto it = Iterator<kLittleEndian>(data);
  GapData gap_data;
  it = GapData::Parse(&gap_data, it);

  ASSERT_EQ(it.NumBytesRemaining(), 0U);
  ASSERT_EQ(gap_data.data_type_, GapDataType::COMPLETE_LIST_16_BIT_UUIDS);
  ASSERT_EQ(2U, gap_data.data_.size());
}

TEST(DataParserTest, simple_data_parser) {
  std::vector<uint8_t> v(kTwoFlag32Data, kTwoFlag32Data + sizeof(kTwoFlag32Data));
  DataParser data_parser(v);
  ASSERT_EQ(2U, data_parser.GetNumGapData());

  std::vector<bluetooth::hci::GapData> flags;
  std::vector<bluetooth::hci::GapData> gap_data = data_parser.GetData();
  for (const auto& data : gap_data) {
    ASSERT_EQ(bluetooth::hci::GapDataType::FLAGS, data.data_type_);
    flags.push_back(data);
  }

  ASSERT_EQ(2U, flags.size());
  uint32_t value[2] = {
      toLeInt(flags[0].data_),
      toLeInt(flags[1].data_),
  };
  ASSERT_EQ((unsigned)0xdeadbeef, value[0]);
  ASSERT_EQ((unsigned)0x11223344, value[1]);
}

TEST(DataParserTest, two_flags_backing_store_cleared) {
  std::vector<uint8_t>* v = new std::vector<uint8_t>(sizeof(kTwoFlag32Data));
  std::copy(kTwoFlag32Data, kTwoFlag32Data + sizeof(kTwoFlag32Data), v->begin());
  DataParser data_parser(*v);
  v->clear();
  ASSERT_EQ(2U, data_parser.GetNumGapData());

  std::vector<bluetooth::hci::GapData> flags;
  std::vector<bluetooth::hci::GapData> gap_data = data_parser.GetData();
  for (const auto& data : gap_data) {
    ASSERT_EQ(bluetooth::hci::GapDataType::FLAGS, data.data_type_);
    flags.push_back(data);
  }

  ASSERT_EQ(2U, flags.size());
  uint32_t value[2] = {
      toLeInt(flags[0].data_),
      toLeInt(flags[1].data_),
  };
  ASSERT_EQ((unsigned)0xdeadbeef, value[0]);
  ASSERT_EQ((unsigned)0x11223344, value[1]);

  delete v;
}

TEST(DataParserTest, backing_store_freed) {
  uint8_t* data = (uint8_t*)malloc(sizeof(kTwoFlag32Data));
  std::copy(kTwoFlag32Data, kTwoFlag32Data + sizeof(kTwoFlag32Data), data);
  DataParser data_parser(std::vector<uint8_t>(data, data + sizeof(kTwoFlag32Data)));
  free(data);
  ASSERT_EQ(2U, data_parser.GetNumGapData());

  std::vector<bluetooth::hci::GapData> flags;
  std::vector<bluetooth::hci::GapData> gap_data = data_parser.GetData();
  for (const auto& data : gap_data) {
    ASSERT_EQ(bluetooth::hci::GapDataType::FLAGS, data.data_type_);
    flags.push_back(data);
  }

  ASSERT_EQ(2U, flags.size());
  uint32_t value[2] = {
      toLeInt(flags[0].data_),
      toLeInt(flags[1].data_),
  };
  ASSERT_EQ((unsigned)0xdeadbeef, value[0]);
  ASSERT_EQ((unsigned)0x11223344, value[1]);
}

std::string GapDataToString(const GapData& data) {
  std::stringstream ss;
  ss << std::hex << std::showbase << "LengthAndData { ";
  ss << "data = "
     << "VECTOR[";
  for (size_t index = 0; index < data.data_.size(); index++) {
    ss << ((index == 0) ? "" : ", ") << static_cast<uint64_t>((data.data_[index]));
  }
  ss << "]";
  ss << " }";
  return ss.str();
}

TEST(DataParserTest, random) {
  constexpr int kMaxLoop = 1000;
  auto data = std::vector<uint8_t>(512);

  for (int i = 0; i < kMaxLoop; i++) {
    size_t size = rand() % 512;
    for (size_t i = 0; i < size; i++) {
      data[i] = rand() % 256;
    }
    DataParser data_parser(data);

    if (((i + 1) % 100) == 0) {
      LOG_INFO("loop %d", i);
    }
  }
}
