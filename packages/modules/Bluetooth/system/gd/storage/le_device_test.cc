/*
 * Copyright 2020 The Android Open Source Project
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

#include "storage/device.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "storage/classic_device.h"
#include "storage/le_device.h"
#include "storage/mutation.h"

using bluetooth::hci::Address;
using bluetooth::hci::AddressType;
using bluetooth::hci::DeviceType;
using bluetooth::storage::ConfigCache;
using bluetooth::storage::Device;
using bluetooth::storage::LeDevice;
using bluetooth::storage::Mutation;
using ::testing::Eq;
using ::testing::Optional;

TEST(LeDeviceTest, create_new_le_device) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  bluetooth::hci::Address address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  LeDevice device(&config, &memory_only_config, address.ToString());
  ASSERT_FALSE(device.GetAddressType());
}

TEST(LeDeviceTest, set_property) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  Address address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  LeDevice device(&config, &memory_only_config, address.ToString());
  ASSERT_FALSE(device.GetAddressType());
  Mutation mutation(&config, &memory_only_config);
  mutation.Add(device.SetAddressType(AddressType::RANDOM_DEVICE_ADDRESS));
  mutation.Commit();
  ASSERT_THAT(device.GetAddressType(), Optional(Eq(AddressType::RANDOM_DEVICE_ADDRESS)));
}

TEST(LeDeviceTest, equality_test) {
  ConfigCache config(10, Device::kLinkKeyProperties);
  ConfigCache memory_only_config(10, {});
  bluetooth::hci::Address address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  LeDevice device1(&config, &memory_only_config, address.ToString());
  LeDevice device2(&config, &memory_only_config, address.ToString());
  ASSERT_EQ(device1, device2);
  bluetooth::hci::Address address3 = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x07}};
  LeDevice device3(&config, &memory_only_config, address3.ToString());
  ASSERT_NE(device1, device3);
}

TEST(LeDeviceTest, operator_less_than) {
  ConfigCache config1(10, Device::kLinkKeyProperties);
  ConfigCache config2(10, Device::kLinkKeyProperties);
  ASSERT_NE(&config1, &config2);
  ConfigCache* smaller_config_ptr = &config1;
  ConfigCache* larger_config_ptr = &config2;
  if (&config2 < &config1) {
    smaller_config_ptr = &config2;
    larger_config_ptr = &config1;
  }

  ConfigCache memory_only_config1(10, {});
  ConfigCache memory_only_config2(10, {});
  ASSERT_NE(&memory_only_config1, &memory_only_config2);
  ConfigCache* smaller_memory_only_config_ptr = &memory_only_config1;
  ConfigCache* larger_memory_only_config_ptr = &memory_only_config2;
  if (&memory_only_config2 < &memory_only_config1) {
    smaller_memory_only_config_ptr = &memory_only_config2;
    larger_memory_only_config_ptr = &memory_only_config1;
  }

  bluetooth::hci::Address smaller_address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x06}};
  bluetooth::hci::Address larger_address = {{0x01, 0x02, 0x03, 0x04, 0x05, 0x07}};

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(larger_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_FALSE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(larger_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    LeDevice device2(larger_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(larger_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_FALSE(device1 < device2);
  }

  {
    LeDevice device1(larger_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    LeDevice device2(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_FALSE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    LeDevice device2(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(larger_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    LeDevice device2(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_FALSE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(smaller_config_ptr, larger_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(smaller_config_ptr, smaller_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(larger_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }

  {
    LeDevice device1(smaller_config_ptr, smaller_memory_only_config_ptr, smaller_address.ToString());
    LeDevice device2(smaller_config_ptr, larger_memory_only_config_ptr, larger_address.ToString());
    ASSERT_TRUE(device1 < device2);
  }
}
