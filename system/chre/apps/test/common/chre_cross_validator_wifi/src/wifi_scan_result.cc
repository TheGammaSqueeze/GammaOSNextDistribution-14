/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include "wifi_scan_result.h"

#include "chre/util/nanoapp/log.h"
#include "chre_api/chre.h"

#include <stdio.h>
#include <cstring>

WifiScanResult::WifiScanResult(pb_istream_t *apWifiScanResultStream) {
  memset(mSsid, 0, CHRE_WIFI_SSID_MAX_LEN);
  chre_cross_validation_wifi_WifiScanResult wifiScanResultProto =
      chre_cross_validation_wifi_WifiScanResult_init_default;
  wifiScanResultProto.ssid = {.funcs = {.decode = decodeString}, .arg = mSsid};
  wifiScanResultProto.bssid = {.funcs = {.decode = decodeString},
                               .arg = mBssid};
  if (!pb_decode(apWifiScanResultStream,
                 chre_cross_validation_wifi_WifiScanResult_fields,
                 &wifiScanResultProto)) {
    LOGE("AP wifi scan result proto message decode failed");
  }
  mTotalNumResults = wifiScanResultProto.totalNumResults;
  mResultIndex = wifiScanResultProto.resultIndex;
}

WifiScanResult::WifiScanResult(const chreWifiScanResult &chreScanResult) {
  memset(mSsid, 0, CHRE_WIFI_SSID_MAX_LEN);
  memcpy(mSsid, chreScanResult.ssid, chreScanResult.ssidLen);
  memcpy(mBssid, chreScanResult.bssid, CHRE_WIFI_BSSID_LEN);
}

bool WifiScanResult::areEqual(const WifiScanResult &result1,
                              const WifiScanResult &result2) {
  // TODO(b/184653034): Compare all fields that are shared between AP and CHRE
  // scan result.
  return strcmp(result1.mSsid, result2.mSsid) == 0 &&
         bssidsAreEqual(result1, result2);
}

bool WifiScanResult::bssidsAreEqual(const WifiScanResult &result1,
                                    const WifiScanResult &result2) {
  return memcmp(result1.mBssid, result2.mBssid, CHRE_WIFI_BSSID_LEN) == 0;
}

bool WifiScanResult::decodeString(pb_istream_t *stream,
                                  const pb_field_t * /*field*/, void **arg) {
  pb_byte_t *strPtr = reinterpret_cast<pb_byte_t *>(*arg);
  return pb_read(stream, strPtr, stream->bytes_left);
}
