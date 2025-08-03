/******************************************************************************
 *
 *  Copyright 2019 The Android Open Source Project
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

#include "hci/address.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <string>
#include <unordered_map>

using bluetooth::hci::Address;

static const char* test_addr = "bc:9a:78:56:34:12";
static const char* test_addr2 = "21:43:65:87:a9:cb";

TEST(AddressUnittest, test_constructor_array) {
  Address bdaddr({0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc});

  ASSERT_EQ(0x12, bdaddr.address[0]);
  ASSERT_EQ(0x34, bdaddr.address[1]);
  ASSERT_EQ(0x56, bdaddr.address[2]);
  ASSERT_EQ(0x78, bdaddr.address[3]);
  ASSERT_EQ(0x9A, bdaddr.address[4]);
  ASSERT_EQ(0xBC, bdaddr.address[5]);

  std::string ret = bdaddr.ToString();

  ASSERT_STREQ(test_addr, ret.c_str());
}

TEST(AddressUnittest, test_is_empty) {
  Address empty;
  Address::FromString("00:00:00:00:00:00", empty);
  ASSERT_TRUE(empty.IsEmpty());

  Address not_empty;
  Address::FromString("00:00:00:00:00:01", not_empty);
  ASSERT_FALSE(not_empty.IsEmpty());
}

TEST(AddressUnittest, test_to_from_str) {
  Address bdaddr;
  Address::FromString(test_addr, bdaddr);

  ASSERT_EQ(0x12, bdaddr.address[0]);
  ASSERT_EQ(0x34, bdaddr.address[1]);
  ASSERT_EQ(0x56, bdaddr.address[2]);
  ASSERT_EQ(0x78, bdaddr.address[3]);
  ASSERT_EQ(0x9A, bdaddr.address[4]);
  ASSERT_EQ(0xBC, bdaddr.address[5]);

  std::string ret = bdaddr.ToString();

  ASSERT_STREQ(test_addr, ret.c_str());
}

TEST(AddressUnittest, test_from_octets) {
  static const uint8_t test_addr_array[] = {0x12, 0x34, 0x56, 0x78, 0x9a, 0xbc};

  Address bdaddr;
  size_t expected_result = Address::kLength;
  ASSERT_EQ(expected_result, bdaddr.FromOctets(test_addr_array));

  ASSERT_EQ(0x12, bdaddr.address[0]);
  ASSERT_EQ(0x34, bdaddr.address[1]);
  ASSERT_EQ(0x56, bdaddr.address[2]);
  ASSERT_EQ(0x78, bdaddr.address[3]);
  ASSERT_EQ(0x9A, bdaddr.address[4]);
  ASSERT_EQ(0xBC, bdaddr.address[5]);

  std::string ret = bdaddr.ToString();

  ASSERT_STREQ(test_addr, ret.c_str());
}

TEST(AddressTest, test_equals) {
  Address bdaddr1;
  Address bdaddr2;
  Address bdaddr3;
  Address::FromString(test_addr, bdaddr1);
  Address::FromString(test_addr, bdaddr2);
  ASSERT_TRUE(bdaddr1 == bdaddr2);
  ASSERT_FALSE(bdaddr1 != bdaddr2);
  ASSERT_TRUE(bdaddr1 == bdaddr1);
  ASSERT_FALSE(bdaddr1 != bdaddr1);

  Address::FromString(test_addr2, bdaddr3);
  ASSERT_FALSE(bdaddr2 == bdaddr3);
  ASSERT_TRUE(bdaddr2 != bdaddr3);
}

TEST(AddressTest, test_less_than) {
  Address bdaddr1;
  Address bdaddr2;
  Address bdaddr3;
  Address::FromString(test_addr, bdaddr1);
  Address::FromString(test_addr, bdaddr2);
  ASSERT_FALSE(bdaddr1 < bdaddr2);
  ASSERT_FALSE(bdaddr1 < bdaddr1);

  Address::FromString(test_addr2, bdaddr3);
  ASSERT_TRUE(bdaddr2 < bdaddr3);
  ASSERT_FALSE(bdaddr3 < bdaddr2);
}

TEST(AddressTest, test_more_than) {
  Address bdaddr1;
  Address bdaddr2;
  Address bdaddr3;
  Address::FromString(test_addr, bdaddr1);
  Address::FromString(test_addr, bdaddr2);
  ASSERT_FALSE(bdaddr1 > bdaddr2);
  ASSERT_FALSE(bdaddr1 > bdaddr1);

  Address::FromString(test_addr2, bdaddr3);
  ASSERT_FALSE(bdaddr2 > bdaddr3);
  ASSERT_TRUE(bdaddr3 > bdaddr2);
}

TEST(AddressTest, test_less_than_or_equal) {
  Address bdaddr1;
  Address bdaddr2;
  Address bdaddr3;
  Address::FromString(test_addr, bdaddr1);
  Address::FromString(test_addr, bdaddr2);
  ASSERT_TRUE(bdaddr1 <= bdaddr2);
  ASSERT_TRUE(bdaddr1 <= bdaddr1);

  Address::FromString(test_addr2, bdaddr3);
  ASSERT_TRUE(bdaddr2 <= bdaddr3);
  ASSERT_FALSE(bdaddr3 <= bdaddr2);
}

TEST(AddressTest, test_more_than_or_equal) {
  Address bdaddr1;
  Address bdaddr2;
  Address bdaddr3;
  Address::FromString(test_addr, bdaddr1);
  Address::FromString(test_addr, bdaddr2);
  ASSERT_TRUE(bdaddr1 >= bdaddr2);
  ASSERT_TRUE(bdaddr1 >= bdaddr1);

  Address::FromString(test_addr2, bdaddr3);
  ASSERT_FALSE(bdaddr2 >= bdaddr3);
  ASSERT_TRUE(bdaddr3 >= bdaddr2);
}

TEST(AddressTest, test_copy) {
  Address bdaddr1;
  Address bdaddr2;
  Address::FromString(test_addr, bdaddr1);
  bdaddr2 = bdaddr1;

  ASSERT_TRUE(bdaddr1 == bdaddr2);
}

TEST(AddressTest, IsValidAddress) {
  ASSERT_FALSE(Address::IsValidAddress(""));
  ASSERT_FALSE(Address::IsValidAddress("000000000000"));
  ASSERT_FALSE(Address::IsValidAddress("00:00:00:00:0000"));
  ASSERT_FALSE(Address::IsValidAddress("00:00:00:00:00:0"));
  ASSERT_FALSE(Address::IsValidAddress("00:00:00:00:00:0;"));
  ASSERT_TRUE(Address::IsValidAddress("00:00:00:00:00:00"));
  ASSERT_TRUE(Address::IsValidAddress("AB:cd:00:00:00:00"));
  ASSERT_FALSE(Address::IsValidAddress("aB:cD:eF:Gh:iJ:Kl"));
}

TEST(AddressTest, BdAddrFromString) {
  Address addr = {};

  ASSERT_TRUE(Address::FromString("00:00:00:00:00:00", addr));
  const Address result0 = {{0x00, 0x00, 0x00, 0x00, 0x00, 0x00}};
  ASSERT_EQ(0, memcmp(addr.data(), result0.data(), Address::kLength));

  ASSERT_TRUE(Address::FromString("ab:01:4C:d5:21:9f", addr));
  const Address result1 = {{0x9f, 0x21, 0xd5, 0x4c, 0x01, 0xab}};
  ASSERT_EQ("ab:01:4c:d5:21:9f", addr.ToString());
  ASSERT_EQ("ab:01:4c:d5:21:9f", result1.ToString());
  ASSERT_THAT(addr.address, testing::ElementsAre(0x9f, 0x21, 0xd5, 0x4c, 0x01, 0xab));
  ASSERT_EQ(0, memcmp(addr.data(), result1.data(), Address::kLength));
}

TEST(AddressTest, BdAddrFromStringToStringEquivalent) {
  std::string address = "c1:c2:c3:d1:d2:d3";
  Address addr;

  ASSERT_TRUE(Address::FromString(address, addr));
  ASSERT_EQ(addr.ToString(), address);
}

TEST(AddressTest, BdAddrSameValueSameOrder) {
  Address addr1{{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  Address addr2{{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  // Test if two addresses with same byte value have the same hash
  struct std::hash<bluetooth::hci::Address> hasher;
  ASSERT_EQ(hasher(addr1), hasher(addr2));
  // Test if two addresses with the same hash and the same value, they will
  // still map to the same value
  std::unordered_map<Address, int> data = {};
  data[addr1] = 5;
  data[addr2] = 8;
  ASSERT_EQ(data[addr1], data[addr2]);
}

TEST(AddressTest, BdAddrHashDifferentForDifferentAddressesZeroAddr) {
  Address addr1{{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  struct std::hash<Address> hasher;
  ASSERT_NE(hasher(addr1), hasher(Address::kEmpty));
}

TEST(AddressTest, BdAddrHashDifferentForDifferentAddressesFullAddr) {
  Address addr1{{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  struct std::hash<Address> hasher;
  ASSERT_NE(hasher(addr1), hasher(Address::kAny));
}

TEST(AddressTest, BdAddrHashDifferentForDifferentAddressesZeroAndFullAddr) {
  struct std::hash<Address> hasher;
  ASSERT_NE(hasher(Address::kEmpty), hasher(Address::kAny));
}

TEST(AddressTest, ToStringForLoggingTestOutputUnderDebuggablePropAndInitFlag) {
  Address addr{{0xab, 0x55, 0x44, 0x33, 0x22, 0x11}};
  const std::string redacted_loggable_str = "xx:xx:xx:xx:55:ab";
  const std::string loggable_str = "11:22:33:44:55:ab";

  std::string ret1 = addr.ToStringForLogging();
  ASSERT_STREQ(ret1.c_str(), loggable_str.c_str());
  std::string ret2 = addr.ToRedactedStringForLogging();
  ASSERT_STREQ(ret2.c_str(), redacted_loggable_str.c_str());
}
