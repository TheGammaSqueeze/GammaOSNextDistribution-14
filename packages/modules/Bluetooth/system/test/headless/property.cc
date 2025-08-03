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

#define LOG_TAG "bt_property"

#include "test/headless/property.h"

#include <map>

#include "gd/os/log.h"
#include "include/hardware/bluetooth.h"
#include "test/headless/log.h"

using namespace bluetooth::test;

namespace {

// Map the bluetooth property names to the corresponding headless property
// structure factor
std::map<::bt_property_type_t, std::function<headless::bt_property_t*(
                                   const uint8_t* data, const size_t len)>>
    property_map = {
        {BT_PROPERTY_BDNAME,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::name_t(data, len);
         }},
        {BT_PROPERTY_BDADDR,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::bdaddr_t(data, len);
         }},
        {BT_PROPERTY_UUIDS,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::uuid_t(data, len);
         }},
        {BT_PROPERTY_CLASS_OF_DEVICE,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::class_of_device_t(data, len);
         }},
        {BT_PROPERTY_TYPE_OF_DEVICE,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::type_of_device_t(data, len);
         }},
        {BT_PROPERTY_SERVICE_RECORD,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(data, len,
                                                 BT_PROPERTY_SERVICE_RECORD);
         }},
        {BT_PROPERTY_ADAPTER_SCAN_MODE,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(data, len,
                                                 BT_PROPERTY_ADAPTER_SCAN_MODE);
         }},
        {BT_PROPERTY_ADAPTER_BONDED_DEVICES,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_ADAPTER_BONDED_DEVICES);
         }},
        {BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_ADAPTER_DISCOVERABLE_TIMEOUT);
         }},
        {BT_PROPERTY_REMOTE_FRIENDLY_NAME,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_REMOTE_FRIENDLY_NAME);
         }},
        {BT_PROPERTY_REMOTE_RSSI,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(data, len,
                                                 BT_PROPERTY_REMOTE_RSSI);
         }},
        {BT_PROPERTY_REMOTE_VERSION_INFO,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_REMOTE_VERSION_INFO);
         }},
        {BT_PROPERTY_LOCAL_LE_FEATURES,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(data, len,
                                                 BT_PROPERTY_LOCAL_LE_FEATURES);
         }},
        {BT_PROPERTY_LOCAL_IO_CAPS,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(data, len,
                                                 BT_PROPERTY_LOCAL_IO_CAPS);
         }},
        {BT_PROPERTY_DYNAMIC_AUDIO_BUFFER,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_DYNAMIC_AUDIO_BUFFER);
         }},
        {BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_REMOTE_IS_COORDINATED_SET_MEMBER);
         }},
        {BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP,
         [](const uint8_t* data, const size_t len) -> headless::bt_property_t* {
           return new headless::property::void_t(
               data, len, BT_PROPERTY_REMOTE_DEVICE_TIMESTAMP);
         }},
};

}  // namespace

// Caller owns the memory
headless::bt_property_t* bluetooth::test::headless::property_factory(
    const ::bt_property_t& bt_property) {
  const uint8_t* data = static_cast<uint8_t*>(bt_property.val);
  const size_t size = static_cast<size_t>(bt_property.len);

  if (size > 0) {
    ASSERT_LOG(data != nullptr, "Property value pointer is null");
  }

  const auto factory = property_map.find(bt_property.type);
  if (factory != property_map.end()) {
    return factory->second(data, size);
  }
  return new headless::property::void_t(data, size, bt_property.type);
}
