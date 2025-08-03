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

#include <fuzzer/FuzzedDataProvider.h>

size_t convertPrintable(char*, const char*, size_t);

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t length) {
  FuzzedDataProvider dataProvider(data, length);
  std::string contents = dataProvider.ConsumeRemainingBytesAsString();

  size_t size = convertPrintable(nullptr, contents.data(), contents.size());

  char buf[size + 1];
  convertPrintable(buf, contents.data(), contents.size());

  return 0;
}
