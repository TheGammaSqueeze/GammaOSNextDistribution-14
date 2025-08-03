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

#include "keystoreCommon.h"
#include <android/security/keystore/KeyAttestationApplicationId.h>

using ::android::security::keystore::KeyAttestationApplicationId;

constexpr size_t kPackageVectorSizeMin = 1;
constexpr size_t kPackageVectorSizeMax = 10;

class KeystoreApplicationId {
  public:
    void process(const uint8_t* data, size_t size);
    ~KeystoreApplicationId() {}

  private:
    void invokeApplicationId();
    std::unique_ptr<FuzzedDataProvider> mFdp;
};

void KeystoreApplicationId::invokeApplicationId() {
    KeyAttestationApplicationId applicationId;
    bool shouldUsePackageInfoVector = mFdp->ConsumeBool();
    if (shouldUsePackageInfoVector) {
        ::std::vector<KeyAttestationPackageInfo> packageInfoVector;
        int32_t packageVectorSize =
            mFdp->ConsumeIntegralInRange<int32_t>(kPackageVectorSizeMin, kPackageVectorSizeMax);
        for (int32_t packageSize = 0; packageSize < packageVectorSize; ++packageSize) {
            auto packageInfoData = initPackageInfoData(mFdp.get());
            auto pInfo = KeyAttestationPackageInfo();
            pInfo.packageName = String16((packageInfoData.packageName).c_str());
            pInfo.versionCode = packageInfoData.versionCode;
            std::move(packageInfoData.sharedSignaturesVector->begin(),
                      packageInfoData.sharedSignaturesVector->end(),
                      std::back_inserter(pInfo.signatures));

            packageInfoVector.push_back(std::move(pInfo));
        }

        std::move(packageInfoVector.begin(), packageInfoVector.end(),
                  std::back_inserter(applicationId.packageInfos));
    } else {
        auto packageInfoData = initPackageInfoData(mFdp.get());
        auto pInfo = KeyAttestationPackageInfo();
        pInfo.packageName = String16((packageInfoData.packageName).c_str());
        pInfo.versionCode = packageInfoData.versionCode;
        std::move(packageInfoData.sharedSignaturesVector->begin(),
                  packageInfoData.sharedSignaturesVector->end(),
                  std::back_inserter(pInfo.signatures));
        applicationId.packageInfos.push_back(std::move(pInfo));
    }
    invokeReadWriteParcel(&applicationId);
}

void KeystoreApplicationId::process(const uint8_t* data, size_t size) {
    mFdp = std::make_unique<FuzzedDataProvider>(data, size);
    invokeApplicationId();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    KeystoreApplicationId keystoreApplicationId;
    keystoreApplicationId.process(data, size);
    return 0;
}
