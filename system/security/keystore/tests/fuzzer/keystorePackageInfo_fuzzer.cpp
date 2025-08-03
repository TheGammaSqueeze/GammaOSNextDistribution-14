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

class KeystorePackageInfoFuzzer {
  public:
    void process(const uint8_t* data, size_t size);
    ~KeystorePackageInfoFuzzer() {}

  private:
    void invokePackageInfo();
    std::unique_ptr<FuzzedDataProvider> mFdp;
};

void KeystorePackageInfoFuzzer::invokePackageInfo() {
    auto packageInfoData = initPackageInfoData(mFdp.get());
    auto packageInfo = KeyAttestationPackageInfo();
    packageInfo.packageName = String16((packageInfoData.packageName).c_str());
    packageInfo.versionCode = packageInfoData.versionCode;
    std::move(packageInfoData.sharedSignaturesVector->begin(),
              packageInfoData.sharedSignaturesVector->end(),
              std::back_inserter(packageInfo.signatures));
    invokeReadWriteParcel(&packageInfo);
}

void KeystorePackageInfoFuzzer::process(const uint8_t* data, size_t size) {
    mFdp = std::make_unique<FuzzedDataProvider>(data, size);
    invokePackageInfo();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    KeystorePackageInfoFuzzer keystorePackageInfoFuzzer;
    keystorePackageInfoFuzzer.process(data, size);
    return 0;
}
