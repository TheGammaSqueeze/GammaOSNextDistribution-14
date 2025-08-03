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

#include <AndroidKeymaster4Device.h>
#include <android/hardware/keymaster/4.0/IKeymasterDevice.h>
#include <fuzzer/FuzzedDataProvider.h>
#include <keymasterV4_0/authorization_set.h>

namespace keymaster::V4_0::ng::fuzzer {

using ::android::hardware::hidl_string;
using ::android::hardware::keymaster::V4_0::AuthorizationSet;
using ::android::hardware::keymaster::V4_0::AuthorizationSetBuilder;
using ::android::hardware::keymaster::V4_0::Digest;
using ::android::hardware::keymaster::V4_0::KeyFormat;
using ::android::hardware::keymaster::V4_0::KeyPurpose;
using ::android::hardware::keymaster::V4_0::PaddingMode;

constexpr SecurityLevel kSecurityLevel[] = {
    SecurityLevel::SOFTWARE,
    SecurityLevel::TRUSTED_ENVIRONMENT,
    SecurityLevel::STRONGBOX,
};

constexpr PaddingMode kPaddingMode[] = {
    PaddingMode::NONE,
    PaddingMode::RSA_OAEP,
    PaddingMode::RSA_PSS,
    PaddingMode::RSA_PKCS1_1_5_ENCRYPT,
    PaddingMode::RSA_PKCS1_1_5_SIGN,
    PaddingMode::PKCS7,
};

constexpr Digest kDigest[] = {
    Digest::NONE,      Digest::MD5,       Digest::SHA1,      Digest::SHA_2_224,
    Digest::SHA_2_256, Digest::SHA_2_384, Digest::SHA_2_512,
};

constexpr KeyFormat kKeyFormat[] = {
    KeyFormat::X509,
    KeyFormat::PKCS8,
    KeyFormat::RAW,
};

constexpr KeyPurpose kKeyPurpose[] = {
    KeyPurpose::ENCRYPT, KeyPurpose::DECRYPT,  KeyPurpose::SIGN,
    KeyPurpose::VERIFY,  KeyPurpose::WRAP_KEY,
};

constexpr uint32_t kRSAKeySize[] = {1024, 2048, 3072, 4096};
constexpr uint32_t kECCKeySize[] = {224, 256, 384, 521};
constexpr size_t kMinBytes = 0;
constexpr size_t kMaxBytes = 100;

class KeyMaster4DeviceFuzzer {
  public:
    bool init(const uint8_t* data, size_t size);
    void process();

  private:
    AuthorizationSet getAuthorizationSet();
    sp<IKeymasterDevice> mKeymaster = nullptr;
    std::unique_ptr<FuzzedDataProvider> mFdp = nullptr;
};

AuthorizationSet KeyMaster4DeviceFuzzer::getAuthorizationSet() {
    auto keyMasterFunction = mFdp->PickValueInArray<
        const std::function<android::hardware::keymaster::V4_0::AuthorizationSet()>>({
        [&]() {
            return AuthorizationSetBuilder()
                .RsaSigningKey(mFdp->PickValueInArray(kRSAKeySize),
                               mFdp->ConsumeIntegral<uint32_t>())
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .EcdsaKey(mFdp->PickValueInArray(kECCKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .AesKey(mFdp->PickValueInArray(kECCKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .TripleDesKey(mFdp->PickValueInArray(kRSAKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .HmacKey(mFdp->PickValueInArray(kRSAKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .RsaEncryptionKey(mFdp->PickValueInArray(kRSAKeySize),
                                  mFdp->ConsumeIntegral<uint64_t>())
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .EcdsaSigningKey(mFdp->PickValueInArray(kRSAKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .AesEncryptionKey(mFdp->PickValueInArray(kECCKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .TripleDesEncryptionKey(mFdp->PickValueInArray(kRSAKeySize))
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
        [&]() {
            return AuthorizationSetBuilder()
                .RsaKey(mFdp->PickValueInArray(kRSAKeySize), mFdp->ConsumeIntegral<uint64_t>())
                .Digest(mFdp->PickValueInArray(kDigest))
                .Padding(mFdp->PickValueInArray(kPaddingMode));
        },
    });
    return keyMasterFunction();
}

bool KeyMaster4DeviceFuzzer::init(const uint8_t* data, size_t size) {
    mFdp = std::make_unique<FuzzedDataProvider>(data, size);
    mKeymaster = CreateKeymasterDevice(mFdp->PickValueInArray(kSecurityLevel));
    if (!mKeymaster) {
        return false;
    }
    return true;
}

void KeyMaster4DeviceFuzzer::process() {
    std::vector<uint8_t> dataVec =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    mKeymaster->addRngEntropy(dataVec);

    hidl_vec<uint8_t> keyBlob = {};
    mKeymaster->generateKey(getAuthorizationSet().hidl_data(),
                            [&]([[maybe_unused]] ErrorCode hidlError,
                                const hidl_vec<uint8_t>& hidlKeyBlob,
                                [[maybe_unused]] const KeyCharacteristics& hidlKeyCharacteristics) {
                                keyBlob = hidlKeyBlob;
                            });

    mKeymaster->attestKey(
        keyBlob, getAuthorizationSet().hidl_data(),
        [&]([[maybe_unused]] ErrorCode hidlError,
            [[maybe_unused]] const hidl_vec<hidl_vec<uint8_t>>& hidlCertificateChain) {});

    mKeymaster->upgradeKey(keyBlob, hidl_vec<KeyParameter>(),
                           [&]([[maybe_unused]] ErrorCode error,
                               [[maybe_unused]] const hidl_vec<uint8_t>& upgraded_blob) {});

    std::vector<uint8_t> clientId =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    std::vector<uint8_t> appData =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    mKeymaster->getKeyCharacteristics(
        keyBlob, clientId, appData,
        [&]([[maybe_unused]] ErrorCode hidlError,
            [[maybe_unused]] const KeyCharacteristics& hidlKeyCharacteristics) {});

    KeyFormat keyFormat = mFdp->PickValueInArray(kKeyFormat);
    std::vector<uint8_t> keyData;
    keyData =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    ErrorCode importKeyError;
    if (mKeymaster
            ->importKey(getAuthorizationSet().hidl_data(), keyFormat, keyData,
                        [&]([[maybe_unused]] ErrorCode hidlError,
                            [[maybe_unused]] const hidl_vec<uint8_t>& hidlKeyBlob,
                            [[maybe_unused]] const KeyCharacteristics& hidlKeyCharacteristics) {
                            importKeyError = hidlError;
                        })
            .isOk()) {
        if (importKeyError == ErrorCode::OK) {
            abort();
        }
    }

    std::vector<uint8_t> wrappedKey, wrappingKey, maskingKey;
    wrappedKey =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    wrappingKey =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    maskingKey =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    uint64_t passwordSid = mFdp->ConsumeIntegral<uint64_t>();
    uint64_t biometricSid = mFdp->ConsumeIntegral<uint64_t>();
    mKeymaster->importWrappedKey(
        wrappedKey, wrappingKey, maskingKey, getAuthorizationSet().hidl_data(), passwordSid,
        biometricSid,
        [&]([[maybe_unused]] ErrorCode hidlError,
            [[maybe_unused]] const hidl_vec<uint8_t>& hidlKeyBlob,
            [[maybe_unused]] const KeyCharacteristics& hidlKeyCharacteristics) {});

    std::vector<uint8_t> keyBlobExportKey =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    mKeymaster->exportKey(keyFormat, keyBlobExportKey, clientId, appData,
                          [&]([[maybe_unused]] ErrorCode hidlErrorCode,
                              [[maybe_unused]] const hidl_vec<uint8_t>& hidlKeyMaterial) {});

    KeyPurpose keyPurpose = mFdp->PickValueInArray(kKeyPurpose);
    mKeymaster->begin(keyPurpose, keyBlob, getAuthorizationSet().hidl_data(), HardwareAuthToken(),
                      [&]([[maybe_unused]] ErrorCode hidlError,
                          [[maybe_unused]] const hidl_vec<KeyParameter>& hidlOutParams,
                          [[maybe_unused]] uint64_t hidlOpHandle) {});

    uint64_t operationHandle = mFdp->ConsumeIntegral<uint64_t>();
    std::vector<uint8_t> input =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    mKeymaster->update(operationHandle, getAuthorizationSet().hidl_data(), input,
                       HardwareAuthToken(), VerificationToken(),
                       [&]([[maybe_unused]] ErrorCode hidlError,
                           [[maybe_unused]] uint32_t hidlInputConsumed,
                           [[maybe_unused]] const hidl_vec<KeyParameter>& hidlOutParams,
                           [[maybe_unused]] const hidl_vec<uint8_t>& hidlOutput) {});

    std::vector<uint8_t> signature =
        mFdp->ConsumeBytes<uint8_t>(mFdp->ConsumeIntegralInRange<size_t>(kMinBytes, kMaxBytes));
    mKeymaster->finish(operationHandle, getAuthorizationSet().hidl_data(), input, signature,
                       HardwareAuthToken(), VerificationToken(),
                       [&]([[maybe_unused]] ErrorCode hidlError,
                           [[maybe_unused]] const hidl_vec<KeyParameter>& hidlOutParams,
                           [[maybe_unused]] const hidl_vec<uint8_t>& hidlOutput) {});

    mKeymaster->deleteKey(keyBlob);
    mKeymaster->deleteAllKeys();
    mKeymaster->abort(mFdp->ConsumeIntegral<uint64_t>());
}

extern "C" int LLVMFuzzerTestOneInput(const uint8_t* data, size_t size) {
    KeyMaster4DeviceFuzzer km4DeviceFuzzer;
    if (km4DeviceFuzzer.init(data, size)) {
        km4DeviceFuzzer.process();
    }
    return 0;
}
}  // namespace keymaster::V4_0::ng::fuzzer
