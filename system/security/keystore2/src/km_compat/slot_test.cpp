/*
 * Copyright 2020, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include <gtest/gtest.h>

#include "km_compat.h"
#include <keymint_support/keymint_tags.h>

#include <aidl/android/hardware/security/keymint/ErrorCode.h>
#include <aidl/android/hardware/security/keymint/IKeyMintOperation.h>

using ::aidl::android::hardware::security::keymint::Algorithm;
using ::aidl::android::hardware::security::keymint::BlockMode;
using ::aidl::android::hardware::security::keymint::Certificate;
using ::aidl::android::hardware::security::keymint::Digest;
using ::aidl::android::hardware::security::keymint::EcCurve;
using ::aidl::android::hardware::security::keymint::ErrorCode;
using ::aidl::android::hardware::security::keymint::IKeyMintOperation;
using ::aidl::android::hardware::security::keymint::KeyCharacteristics;
using ::aidl::android::hardware::security::keymint::KeyPurpose;
using ::aidl::android::hardware::security::keymint::PaddingMode;
using ::aidl::android::hardware::security::keymint::SecurityLevel;

namespace KMV1 = ::aidl::android::hardware::security::keymint;

static std::vector<uint8_t> generateAESKey(std::shared_ptr<KeyMintDevice> device) {
    auto keyParams = std::vector<KeyParameter>({
        KMV1::makeKeyParameter(KMV1::TAG_ALGORITHM, Algorithm::AES),
        KMV1::makeKeyParameter(KMV1::TAG_KEY_SIZE, 128),
        KMV1::makeKeyParameter(KMV1::TAG_BLOCK_MODE, BlockMode::CBC),
        KMV1::makeKeyParameter(KMV1::TAG_PADDING, PaddingMode::NONE),
        KMV1::makeKeyParameter(KMV1::TAG_NO_AUTH_REQUIRED, true),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::ENCRYPT),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::DECRYPT),
    });
    KeyCreationResult creationResult;
    auto status = device->generateKey(keyParams, std::nullopt /* attest_key */, &creationResult);
    if (!status.isOk()) {
        return {};
    }
    return creationResult.keyBlob;
}

static bool generateECSingingKey(std::shared_ptr<KeyMintDevice> device) {
    uint64_t now_ms = (uint64_t)time(nullptr) * 1000;

    auto keyParams = std::vector<KeyParameter>({
        KMV1::makeKeyParameter(KMV1::TAG_ALGORITHM, Algorithm::EC),
        KMV1::makeKeyParameter(KMV1::TAG_EC_CURVE, EcCurve::P_256),
        KMV1::makeKeyParameter(KMV1::TAG_NO_AUTH_REQUIRED, true),
        KMV1::makeKeyParameter(KMV1::TAG_DIGEST, Digest::SHA_2_256),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::SIGN),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::VERIFY),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_BEFORE, now_ms - 60 * 60 * 1000),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_AFTER, now_ms + 60 * 60 * 1000),
    });
    KeyCreationResult creationResult;
    auto status = device->generateKey(keyParams, std::nullopt /* attest_key */, &creationResult);
    EXPECT_TRUE(status.isOk()) << status.getDescription();
    return status.isOk();
}

static std::variant<BeginResult, ScopedAStatus> begin(std::shared_ptr<KeyMintDevice> device,
                                                      bool valid) {
    auto blob = generateAESKey(device);
    std::vector<KeyParameter> kps;
    if (valid) {
        kps.push_back(KMV1::makeKeyParameter(KMV1::TAG_BLOCK_MODE, BlockMode::CBC));
        kps.push_back(KMV1::makeKeyParameter(KMV1::TAG_PADDING, PaddingMode::NONE));
    }
    BeginResult beginResult;
    auto status = device->begin(KeyPurpose::ENCRYPT, blob, kps, HardwareAuthToken(), &beginResult);
    if (!status.isOk()) {
        return status;
    }
    return beginResult;
}

static std::shared_ptr<KMV1::IKeyMintOperation>
generateAndBeginECDHKeyOperation(std::shared_ptr<KeyMintDevice> device) {
    uint64_t now_ms = (uint64_t)time(nullptr) * 1000;

    auto keyParams = std::vector<KeyParameter>({
        KMV1::makeKeyParameter(KMV1::TAG_ALGORITHM, Algorithm::EC),
        KMV1::makeKeyParameter(KMV1::TAG_EC_CURVE, EcCurve::P_256),
        KMV1::makeKeyParameter(KMV1::TAG_NO_AUTH_REQUIRED, true),
        KMV1::makeKeyParameter(KMV1::TAG_DIGEST, Digest::NONE),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::AGREE_KEY),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_BEFORE, now_ms - 60 * 60 * 1000),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_AFTER, now_ms + 60 * 60 * 1000),
    });
    KeyCreationResult creationResult;
    auto status = device->generateKey(keyParams, std::nullopt /* attest_key */, &creationResult);
    EXPECT_TRUE(status.isOk()) << status.getDescription();
    if (!status.isOk()) {
        return {};
    }
    std::vector<KeyParameter> kps;
    BeginResult beginResult;
    auto bstatus = device->begin(KeyPurpose::AGREE_KEY, creationResult.keyBlob, kps,
                                 HardwareAuthToken(), &beginResult);
    EXPECT_TRUE(status.isOk()) << status.getDescription();
    if (status.isOk()) {
        return beginResult.operation;
    }
    return {};
}

static const int NUM_SLOTS = 2;

TEST(SlotTest, TestSlots) {
    static std::shared_ptr<KeyMintDevice> device =
        KeyMintDevice::getWrappedKeymasterDevice(SecurityLevel::TRUSTED_ENVIRONMENT);
    ASSERT_NE(device.get(), nullptr);

    device->setNumFreeSlots(NUM_SLOTS);

    // A begin() that returns a failure should not use a slot.
    auto result = begin(device, false);
    ASSERT_TRUE(std::holds_alternative<ScopedAStatus>(result));

    // Software emulated operations must not leak virtual slots.
    ASSERT_TRUE(!!generateAndBeginECDHKeyOperation(device));

    // Software emulated operations must not impact virtual slots accounting.
    // As opposed to the previous call, the software operation is kept alive.
    auto software_op = generateAndBeginECDHKeyOperation(device);
    ASSERT_TRUE(!!software_op);

    // Fill up all the slots.
    std::vector<std::shared_ptr<IKeyMintOperation>> operations;
    for (int i = 0; i < NUM_SLOTS; i++) {
        auto result = begin(device, true);
        ASSERT_TRUE(std::holds_alternative<BeginResult>(result));
        operations.push_back(std::get<BeginResult>(result).operation);
    }

    // We should not be able to create a new operation.
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<ScopedAStatus>(result));
    ASSERT_EQ(std::get<ScopedAStatus>(result).getServiceSpecificError(),
              static_cast<int32_t>(ErrorCode::TOO_MANY_OPERATIONS));

    // At this point all slots are in use. We should still be able to generate keys which
    // require an operation slot during generation.
    ASSERT_TRUE(generateECSingingKey(device));

    // Software emulated operations should work despite having all virtual operation slots
    // depleted.
    ASSERT_TRUE(generateAndBeginECDHKeyOperation(device));

    // TODO: I'm not sure how to generate a failing update call to test that.

    // Calling finish should free up a slot.
    auto last = operations.back();
    operations.pop_back();
    std::vector<uint8_t> byteVec;
    auto status = last->finish(std::nullopt /* input */, std::nullopt /* signature */,
                               std::nullopt /* authToken */, std::nullopt /* timestampToken */,
                               std::nullopt /* confirmationToken */, &byteVec);
    ASSERT_TRUE(status.isOk());
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<BeginResult>(result));
    operations.push_back(std::get<BeginResult>(result).operation);

    // Calling finish and abort on an already-finished operation should not free up another slot.
    status = last->finish(std::nullopt /* input */, std::nullopt /* signature */,
                          std::nullopt /* authToken */, std::nullopt /* timestampToken */,
                          std::nullopt /* confirmationToken */, &byteVec);
    ASSERT_TRUE(!status.isOk());
    status = last->abort();
    ASSERT_TRUE(!status.isOk());
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<ScopedAStatus>(result));
    ASSERT_EQ(std::get<ScopedAStatus>(result).getServiceSpecificError(),
              static_cast<int32_t>(ErrorCode::TOO_MANY_OPERATIONS));

    // Calling abort should free up a slot.
    last = operations.back();
    operations.pop_back();
    status = last->abort();
    ASSERT_TRUE(status.isOk());
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<BeginResult>(result));
    operations.push_back(std::get<BeginResult>(result).operation);

    // Calling finish and abort on an already-aborted operation should not free up another slot.
    status = last->finish(std::nullopt /* input */, std::nullopt /* signature */,
                          std::nullopt /* authToken */, std::nullopt /* timestampToken */,
                          std::nullopt /* confirmationToken */, &byteVec);
    ASSERT_TRUE(!status.isOk());
    status = last->abort();
    ASSERT_TRUE(!status.isOk());
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<ScopedAStatus>(result));
    ASSERT_EQ(std::get<ScopedAStatus>(result).getServiceSpecificError(),
              static_cast<int32_t>(ErrorCode::TOO_MANY_OPERATIONS));

    // Generating a certificate with signWith uses a slot but falls back to not using one.
    auto kps = std::vector<KeyParameter>({
        KMV1::makeKeyParameter(KMV1::TAG_ALGORITHM, Algorithm::RSA),
        KMV1::makeKeyParameter(KMV1::TAG_KEY_SIZE, 2048),
        KMV1::makeKeyParameter(KMV1::TAG_RSA_PUBLIC_EXPONENT, 65537),
        KMV1::makeKeyParameter(KMV1::TAG_DIGEST, Digest::SHA_2_256),
        KMV1::makeKeyParameter(KMV1::TAG_PURPOSE, KeyPurpose::SIGN),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_BEFORE, 0),
        KMV1::makeKeyParameter(KMV1::TAG_CERTIFICATE_NOT_AFTER, 253402300799000),
        KMV1::makeKeyParameter(KMV1::TAG_NO_AUTH_REQUIRED, true),
    });
    KeyCreationResult creationResult;
    status = device->generateKey(kps, std::nullopt /* attest_key */, &creationResult);
    ASSERT_TRUE(status.isOk());
    // But generating a certificate with signCert does not use a slot.
    kps.pop_back();
    status = device->generateKey(kps, std::nullopt /* attest_key */, &creationResult);
    ASSERT_TRUE(status.isOk());

    // Destructing operations should free up their slots.
    operations.clear();
    result = begin(device, true);
    ASSERT_TRUE(std::holds_alternative<BeginResult>(result));
}
