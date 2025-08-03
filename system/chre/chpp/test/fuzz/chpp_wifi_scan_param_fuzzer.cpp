/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "fuzzer/FuzzedDataProvider.h"

#include "chpp/common/wifi_types.h"
#include "chpp/memory.h"

// Fuzzer for validating the conversion methods between CHPP/CHRE types for
// chreWifiScanParams.

extern "C" int LLVMFuzzerTestOneInput(const uint8_t *data, size_t size) {
  FuzzedDataProvider fdp(data, size);

  struct chreWifiScanParams params = {};
  params.scanType = fdp.ConsumeIntegral<uint8_t>();
  params.maxScanAgeMs = fdp.ConsumeIntegral<uint32_t>();
  // ConsumeBytes only supports uint8_t currently.
  std::vector<uint8_t> frequencyList = fdp.ConsumeBytes<uint8_t>(
      CHRE_WIFI_FREQUENCY_LIST_MAX_LEN * sizeof(uint32_t));
  params.frequencyList = reinterpret_cast<uint32_t *>(frequencyList.data());
  params.frequencyListLen = frequencyList.size() / sizeof(uint32_t);
  params.ssidListLen = fdp.ConsumeIntegral<uint8_t>();
  std::vector<chreWifiSsidListItem> ssidList(params.ssidListLen);
  for (uint8_t i = 0; i < params.ssidListLen; ++i) {
    struct chreWifiSsidListItem item = {};
    item.ssidLen = fdp.ConsumeIntegral<uint8_t>();
    fdp.ConsumeData(item.ssid, item.ssidLen);
  }
  params.ssidList = ssidList.data();
  params.radioChainPref = fdp.ConsumeIntegral<uint8_t>();
  params.channelSet = fdp.ConsumeIntegral<uint8_t>();

  ChppWifiScanParamsWithHeader *chppWithHeader = nullptr;
  size_t outputSize = 999;
  chppWifiScanParamsFromChre(&params, &chppWithHeader, &outputSize);
  ChppWifiScanParams *chppParams = &chppWithHeader->payload;
  outputSize -= sizeof(struct ChppAppHeader);
  chreWifiScanParams *backParams =
      chppWifiScanParamsToChre(chppParams, outputSize);

  chppFree(chppWithHeader);
  if (backParams != NULL) {
    chppFree(const_cast<uint32_t *>(backParams->frequencyList));
    chppFree(const_cast<chreWifiSsidListItem *>(backParams->ssidList));
  }
  chppFree(backParams);
  return 0;
}