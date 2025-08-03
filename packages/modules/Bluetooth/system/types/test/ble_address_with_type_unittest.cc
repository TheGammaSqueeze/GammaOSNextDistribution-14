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

#include "types/ble_address_with_type.h"

#include <gtest/gtest.h>
static constexpr uint8_t RAW_ADDRESS_TEST1[6] = {0x01, 0x02, 0x03,
                                                 0x04, 0x05, 0x06};

TEST(BleAddressWithTypeTest, to_ble_addr_type) {
  for (unsigned i = 0; i < 0xff + 1; i++) {
    switch (to_ble_addr_type((uint8_t)i)) {
      case BLE_ADDR_PUBLIC:
        ASSERT_TRUE(i == 0);
        break;
      case BLE_ADDR_RANDOM:
        ASSERT_TRUE(i == 1);
        break;
      case BLE_ADDR_PUBLIC_ID:
        ASSERT_TRUE(i == 2);
        break;
      case BLE_ADDR_RANDOM_ID:
        ASSERT_TRUE(i == 3);
        break;
      case BLE_ADDR_ANONYMOUS:
        ASSERT_TRUE(i == 0xff);
        break;
      default:
        ASSERT_TRUE(i > 3 && i != 0xff);
        break;
    }
  }
}

TEST(BleAddressWithTypeTest, from_ble_addr_type) {
  struct type_table_t {
    tBLE_ADDR_TYPE type;
    uint8_t value;
  } type_table[] = {
      {BLE_ADDR_PUBLIC, 0},       {BLE_ADDR_RANDOM, 1},
      {BLE_ADDR_PUBLIC_ID, 2},    {BLE_ADDR_RANDOM_ID, 3},
      {BLE_ADDR_ANONYMOUS, 0xff},
  };

  for (unsigned i = 0; i < sizeof(type_table) / sizeof(type_table[0]); i++) {
    ASSERT_TRUE(from_ble_addr_type(type_table[i].type) == type_table[i].value);
  }
}

TEST(BleAddressWithTypeTest, STREAM_TO_BLE_ADDR_TYPE) {
  uint8_t buf[256] = {0x00, 0x01, 0x02, 0x03};
  buf[10] = 0x01;
  buf[20] = 0x02;
  buf[30] = 0x03;
  buf[127] = 0xff;
  buf[255] = 0xff;

  uint8_t* p = buf;
  for (unsigned i = 0; i < sizeof(buf); i++) {
    tBLE_ADDR_TYPE type;
    STREAM_TO_BLE_ADDR_TYPE(type, p);
    switch (i) {
      case 0:
        ASSERT_TRUE(type == BLE_ADDR_PUBLIC);
        break;
      case 1:
      case 10:
        ASSERT_TRUE(type == BLE_ADDR_RANDOM);
        break;
      case 2:
      case 20:
        ASSERT_TRUE(type == BLE_ADDR_PUBLIC_ID);
        break;
      case 3:
      case 30:
        ASSERT_TRUE(type == BLE_ADDR_RANDOM_ID);
        break;
      case 127:
      case 255:
        ASSERT_TRUE(type == BLE_ADDR_ANONYMOUS);
        break;
      default:
        ASSERT_TRUE(type == BLE_ADDR_PUBLIC);
        break;
    }
  }
}

TEST(BleAddressWithTypeTest, TYPED_ADDRESS_TRANSPORT) {
  tAclLinkSpec linkSpecA = {{BLE_ADDR_PUBLIC, RAW_ADDRESS_TEST1},
                            BT_TRANSPORT_AUTO};
  tAclLinkSpec linkSpecB = {{BLE_ADDR_PUBLIC, RAW_ADDRESS_TEST1},
                            BT_TRANSPORT_BR_EDR};
  tAclLinkSpec linkSpecC = {{BLE_ADDR_PUBLIC, RAW_ADDRESS_TEST1},
                            BT_TRANSPORT_LE};

  ASSERT_EQ(linkSpecA, linkSpecB);
  ASSERT_EQ(linkSpecA, linkSpecC);
  ASSERT_NE(linkSpecB, linkSpecC);

  ASSERT_FALSE(linkSpecA.StrictlyEquals(linkSpecB));
  ASSERT_FALSE(linkSpecA.StrictlyEquals(linkSpecC));
  ASSERT_FALSE(linkSpecB.StrictlyEquals(linkSpecC));
}

TEST(BleAddressWithTypeTest, BLE_ADDR_TYPE_TO_STREAM) {
  uint8_t buf[256] = {0};
  uint8_t* p = buf;

  BLE_ADDR_TYPE_TO_STREAM(p, BLE_ADDR_PUBLIC);
  BLE_ADDR_TYPE_TO_STREAM(p, BLE_ADDR_RANDOM);
  BLE_ADDR_TYPE_TO_STREAM(p, BLE_ADDR_PUBLIC_ID);
  BLE_ADDR_TYPE_TO_STREAM(p, BLE_ADDR_RANDOM_ID);
  BLE_ADDR_TYPE_TO_STREAM(p, BLE_ADDR_ANONYMOUS);

  const uint8_t exp[] = {0x0, 0x1, 0x2, 0x3, 0xff};
  ASSERT_EQ(*exp, *buf);
  ASSERT_EQ(5, p - buf);
}
