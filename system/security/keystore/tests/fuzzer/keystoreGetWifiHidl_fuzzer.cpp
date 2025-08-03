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
#include <inttypes.h>
#include <keystore/keystore_get.h>

using namespace std;

constexpr int32_t kMaxKeySize = 256;
const string kValidStrKeyPrefix[] = {"USRSKEY_",
                                     "PLATFORM_VPN_",
                                     "USRPKEY_",
                                     "CACERT_",
                                     "VPN_"
                                     "USRCERT_",
                                     "WIFI_"};
constexpr char kStrGrantKeyPrefix[] = "ks2_keystore-engine_grant_id:";
constexpr char kStrKeySuffix[] = "LOCKDOWN_VPN";
constexpr size_t kGrantIdSize = 20;

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    FuzzedDataProvider fdp = FuzzedDataProvider(data, size);
    size_t keyLength = fdp.ConsumeIntegralInRange<size_t>(0, kMaxKeySize);
    bool usePrefix = fdp.ConsumeBool();
    string strKeyPrefix;
    size_t strKeyPrefixLength = 0;
    size_t strKeySuffixLength = min(fdp.remaining_bytes(), keyLength);
    if (usePrefix) {
        strKeyPrefix = fdp.PickValueInArray(kValidStrKeyPrefix);
        strKeyPrefixLength = sizeof(strKeyPrefix);
        strKeySuffixLength =
            (strKeySuffixLength > strKeyPrefixLength) ? strKeySuffixLength - strKeyPrefixLength : 0;
    }
    string strKeySuffix =
        fdp.ConsumeBool() ? string(kStrKeySuffix) : fdp.ConsumeBytesAsString(strKeySuffixLength);
    string strKey;
    strKey = usePrefix ? strKeyPrefix + strKeySuffix : strKeySuffix;
    if (fdp.ConsumeBool()) {
        uint64_t grant = fdp.ConsumeIntegral<uint64_t>();
        char grantId[kGrantIdSize] = "";
        snprintf(grantId, kGrantIdSize, "%" PRIx64, grant);
        strKey = strKey + string(kStrGrantKeyPrefix) + grantId;
    }
    const char* key = strKey.c_str();
    uint8_t* value = nullptr;
    keystore_get(key, strlen(key), &value);
    free(value);
    return 0;
}
