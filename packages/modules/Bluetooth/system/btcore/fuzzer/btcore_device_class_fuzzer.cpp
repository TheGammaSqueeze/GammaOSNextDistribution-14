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
#include "btcore/include/device_class.h"

class BTCoreDeviceClassFuzzer {
 public:
  void process(const uint8_t* data, size_t size);

 private:
  std::unique_ptr<FuzzedDataProvider> mFdp = nullptr;
};

void BTCoreDeviceClassFuzzer::process(const uint8_t* data, size_t size) {
  mFdp = std::make_unique<FuzzedDataProvider>(data, size);
  size_t dcStreamSize = sizeof(bt_device_class_t) * sizeof(uint8_t);

  std::vector<uint8_t> dcStreamSrc(dcStreamSize, 0x0);
  mFdp->ConsumeData(dcStreamSrc.data(), dcStreamSize);

  bt_device_class_t deviceClass;
  device_class_from_stream(&deviceClass, dcStreamSrc.data());

  std::vector<uint8_t> dcStreamDst(dcStreamSize, 0x0);
  (void)device_class_to_stream(&deviceClass, dcStreamDst.data(), dcStreamSize);

  device_class_set_limited(&deviceClass, mFdp->ConsumeBool());
  (void)device_class_get_limited(&deviceClass);

  int val = mFdp->ConsumeIntegral<int>();
  device_class_set_major_device(&deviceClass, val);
  (void)device_class_get_major_device(&deviceClass);

  val = mFdp->ConsumeIntegral<int>();
  device_class_set_minor_device(&deviceClass, val);
  (void)device_class_get_minor_device(&deviceClass);

  device_class_set_information(&deviceClass, mFdp->ConsumeBool());
  (void)device_class_get_information(&deviceClass);

  bt_device_class_t deviceClassCopy;
  (void)device_class_copy(&deviceClassCopy, &deviceClass);
  (void)device_class_equals(&deviceClass, &deviceClassCopy);

  val = mFdp->ConsumeIntegralInRange(1, INT_MAX);
  device_class_from_int(&deviceClass, val);
  (void)device_class_to_int(&deviceClass);
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
  BTCoreDeviceClassFuzzer btCoreDeviceClassFuzzer;
  btCoreDeviceClassFuzzer.process(data, size);
  return 0;
}
