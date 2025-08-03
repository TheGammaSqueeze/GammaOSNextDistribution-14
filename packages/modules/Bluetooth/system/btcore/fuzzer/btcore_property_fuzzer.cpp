/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#include <fuzzer/FuzzedDataProvider.h>

#include "btcore/include/property.h"
#include "types/bluetooth/uuid.h"
#include "types/raw_address.h"

using bluetooth::Uuid;

constexpr int32_t kRandomStringLength = 256;

class BTCorePropertyFuzzer {
 public:
  void process(const uint8_t* data, size_t size);

 private:
  std::unique_ptr<FuzzedDataProvider> mFdp = nullptr;
};

void BTCorePropertyFuzzer::process(const uint8_t* data, size_t size) {
  mFdp = std::make_unique<FuzzedDataProvider>(data, size);
  uint8_t addr[RawAddress::kLength];
  mFdp->ConsumeData(addr, sizeof(uint8_t) * RawAddress::kLength);
  RawAddress btAddress = {addr};
  bt_property_t* property = property_new_addr(&btAddress);
  property_as_addr(property);
  property_free(property);

  bt_device_class_t deviceClass = {{mFdp->ConsumeIntegral<uint8_t>(),
                                    mFdp->ConsumeIntegral<uint8_t>(),
                                    mFdp->ConsumeIntegral<uint8_t>()}};
  property = property_new_device_class(&deviceClass);

  const bt_device_class_t* pDeviceClass = property_as_device_class(property);
  (void)device_class_to_int(pDeviceClass);
  property_free(property);

  bt_device_type_t deviceType =
      (bt_device_type_t)(mFdp->ConsumeIntegral<uint32_t>());
  property = property_new_device_type(deviceType);
  (void)property_as_device_type(property);
  property_free(property);

  uint32_t timeout = mFdp->ConsumeIntegral<uint32_t>();
  property = property_new_discoverable_timeout(timeout);
  (void)property_as_discoverable_timeout(property);
  property_free(property);

  std::string name = mFdp->ConsumeRandomLengthString(kRandomStringLength);
  property = property_new_name(name.c_str());
  (void)property_as_name(property);
  property_free(property);

  int8_t rssi = mFdp->ConsumeIntegral<int8_t>();
  property = property_new_rssi(rssi);
  (void)property_as_rssi(property);
  property_free(property);

  bt_scan_mode_t mode = (bt_scan_mode_t)(mFdp->ConsumeIntegral<uint32_t>());
  property = property_new_scan_mode(mode);
  (void)property_as_scan_mode(property);
  property_free(property);

  size_t uuidSize = sizeof(uint8_t) * bluetooth::Uuid::kNumBytes128;
  uint8_t uuid[bluetooth::Uuid::kNumBytes128];
  mFdp->ConsumeData(uuid, uuidSize);
  Uuid uuidBE = Uuid::From128BitBE(uuid);
  property = property_new_uuids(&uuidBE, 1);
  size_t uuidCount;
  (void)property_as_uuids(property, &uuidCount);
  property_free(property);

  mFdp->ConsumeData(uuid, uuidSize);
  Uuid uuidLE = Uuid::From128BitLE(uuid);
  Uuid uuids[] = {uuidBE, uuidLE};
  bt_property_t* propertySrc = property_new_uuids(uuids, std::size(uuids));
  bt_property_t propertyDest;
  (void)property_copy(&propertyDest, propertySrc);
  property_free(propertySrc);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  BTCorePropertyFuzzer btCorePropertyFuzzer;
  btCorePropertyFuzzer.process(data, size);
  return 0;
}
