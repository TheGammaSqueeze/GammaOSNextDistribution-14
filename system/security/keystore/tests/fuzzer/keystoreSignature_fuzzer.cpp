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
#include <android/security/keystore/Signature.h>

using ::android::security::keystore::Signature;

class KeystoreSignatureFuzzer {
  public:
    void process(const uint8_t* data, size_t size);
    ~KeystoreSignatureFuzzer() {}

  private:
    void invokeSignature();
    std::unique_ptr<FuzzedDataProvider> mFdp;
};

void KeystoreSignatureFuzzer::invokeSignature() {
    Signature signature;
    bool shouldUseParameterizedConstructor = mFdp->ConsumeBool();
    if (shouldUseParameterizedConstructor) {
        std::vector<uint8_t> signatureData = initSignatureData(mFdp.get());
        signature.data = signatureData;
    } else {
        signature = Signature();
    }
    invokeReadWriteParcel(&signature);
}

void KeystoreSignatureFuzzer::process(const uint8_t* data, size_t size) {
    mFdp = std::make_unique<FuzzedDataProvider>(data, size);
    invokeSignature();
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    KeystoreSignatureFuzzer keystoreSignatureFuzzer;
    keystoreSignatureFuzzer.process(data, size);
    return 0;
}
