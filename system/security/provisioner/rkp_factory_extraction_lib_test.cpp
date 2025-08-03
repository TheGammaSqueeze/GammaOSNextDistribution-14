/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "rkp_factory_extraction_lib.h"

#include "gmock/gmock-matchers.h"
#include "gmock/gmock-more-matchers.h"
#include <aidl/android/hardware/security/keymint/DeviceInfo.h>
#include <aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <aidl/android/hardware/security/keymint/MacedPublicKey.h>
#include <aidl/android/hardware/security/keymint/RpcHardwareInfo.h>
#include <android-base/properties.h>
#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstdint>
#include <memory>
#include <ostream>
#include <set>
#include <vector>

#include "aidl/android/hardware/security/keymint/ProtectedData.h"
#include "android/binder_auto_utils.h"
#include "android/binder_interface_utils.h"
#include "cppbor.h"

using ::ndk::ScopedAStatus;
using ::ndk::SharedRefBase;

using namespace ::aidl::android::hardware::security::keymint;
using namespace ::cppbor;
using namespace ::testing;

namespace cppbor {

std::ostream& operator<<(std::ostream& os, const Item& item) {
    return os << prettyPrint(&item);
}

std::ostream& operator<<(std::ostream& os, const std::unique_ptr<Item>& item) {
    return os << *item;
}

std::ostream& operator<<(std::ostream& os, const Item* item) {
    return os << *item;
}

}  // namespace cppbor

class MockIRemotelyProvisionedComponent : public IRemotelyProvisionedComponentDefault {
  public:
    MOCK_METHOD(ScopedAStatus, getHardwareInfo, (RpcHardwareInfo * _aidl_return), (override));
    MOCK_METHOD(ScopedAStatus, generateEcdsaP256KeyPair,
                (bool in_testMode, MacedPublicKey* out_macedPublicKey,
                 std::vector<uint8_t>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, generateCertificateRequest,
                (bool in_testMode, const std::vector<MacedPublicKey>& in_keysToSign,
                 const std::vector<uint8_t>& in_endpointEncryptionCertChain,
                 const std::vector<uint8_t>& in_challenge, DeviceInfo* out_deviceInfo,
                 ProtectedData* out_protectedData, std::vector<uint8_t>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, generateCertificateRequestV2,
                (const std::vector<MacedPublicKey>& in_keysToSign,
                 const std::vector<uint8_t>& in_challenge, std::vector<uint8_t>* _aidl_return),
                (override));
    MOCK_METHOD(ScopedAStatus, getInterfaceVersion, (int32_t * _aidl_return), (override));
    MOCK_METHOD(ScopedAStatus, getInterfaceHash, (std::string * _aidl_return), (override));
};

TEST(LibRkpFactoryExtractionTests, ToBase64) {
    std::vector<uint8_t> input(UINT8_MAX + 1);
    for (int i = 0; i < input.size(); ++i) {
        input[i] = i;
    }

    // Test three lengths so we get all the different paddding options
    EXPECT_EQ("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4"
              "vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV"
              "5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMj"
              "Y6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8"
              "vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uv"
              "s7e7v8PHy8/T19vf4+fr7/P3+/w==",
              toBase64(input));

    input.push_back(42);
    EXPECT_EQ("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4"
              "vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV"
              "5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMj"
              "Y6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8"
              "vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uv"
              "s7e7v8PHy8/T19vf4+fr7/P3+/yo=",
              toBase64(input));

    input.push_back(42);
    EXPECT_EQ("AAECAwQFBgcICQoLDA0ODxAREhMUFRYXGBkaGxwdHh8gISIjJCUmJygpKissLS4"
              "vMDEyMzQ1Njc4OTo7PD0+P0BBQkNERUZHSElKS0xNTk9QUVJTVFVWV1hZWltcXV"
              "5fYGFiY2RlZmdoaWprbG1ub3BxcnN0dXZ3eHl6e3x9fn+AgYKDhIWGh4iJiouMj"
              "Y6PkJGSk5SVlpeYmZqbnJ2en6ChoqOkpaanqKmqq6ytrq+wsbKztLW2t7i5uru8"
              "vb6/wMHCw8TFxsfIycrLzM3Oz9DR0tPU1dbX2Nna29zd3t/g4eLj5OXm5+jp6uv"
              "s7e7v8PHy8/T19vf4+fr7/P3+/yoq",
              toBase64(input));
}

TEST(LibRkpFactoryExtractionTests, UniqueChallengeSmokeTest) {
    // This will at least catch VERY broken implementations.
    constexpr size_t NUM_CHALLENGES = 32;
    std::set<std::vector<uint8_t>> challenges;
    for (size_t i = 0; i < NUM_CHALLENGES; ++i) {
        const std::vector<uint8_t> challenge = generateChallenge();
        const auto [_, wasInserted] = challenges.insert(generateChallenge());
        EXPECT_TRUE(wasInserted) << "Duplicate challenge: " << toBase64(challenge);
    }
}

TEST(LibRkpFactoryExtractionTests, GetCsrWithV2Hal) {
    ASSERT_TRUE(true);

    const std::vector<uint8_t> kFakeMac = {1, 2, 3, 4};

    Map cborDeviceInfo;
    cborDeviceInfo.add("product", "gShoe");
    cborDeviceInfo.add("version", 2);
    cborDeviceInfo.add("brand", "Fake Brand");
    cborDeviceInfo.add("manufacturer", "Fake Mfr");
    cborDeviceInfo.add("model", "Fake Model");
    cborDeviceInfo.add("device", "Fake Device");
    cborDeviceInfo.add("vb_state", "orange");
    cborDeviceInfo.add("bootloader_state", "unlocked");
    cborDeviceInfo.add("vbmeta_digest", std::vector<uint8_t>{1, 2, 3, 4});
    cborDeviceInfo.add("system_patch_level", 42);
    cborDeviceInfo.add("boot_patch_level", 31415);
    cborDeviceInfo.add("vendor_patch_level", 0);
    cborDeviceInfo.add("fused", 0);
    cborDeviceInfo.add("security_level", "tee");
    cborDeviceInfo.add("os_version", "the best version");
    const DeviceInfo kVerifiedDeviceInfo = {cborDeviceInfo.canonicalize().encode()};

    Array cborProtectedData;
    cborProtectedData.add(Bstr());   // protected
    cborProtectedData.add(Map());    // unprotected
    cborProtectedData.add(Bstr());   // ciphertext
    cborProtectedData.add(Array());  // recipients
    const ProtectedData kProtectedData = {cborProtectedData.encode()};

    std::vector<uint8_t> eekChain;
    std::vector<uint8_t> challenge;

    // Set up mock, then call getSCsr
    auto mockRpc = SharedRefBase::make<MockIRemotelyProvisionedComponent>();
    EXPECT_CALL(*mockRpc, getHardwareInfo(NotNull())).WillRepeatedly([](RpcHardwareInfo* hwInfo) {
        hwInfo->versionNumber = 2;
        return ScopedAStatus::ok();
    });
    EXPECT_CALL(*mockRpc,
                generateCertificateRequest(false,               // testMode
                                           IsEmpty(),           // keysToSign
                                           _,                   // endpointEncryptionCertChain
                                           _,                   // challenge
                                           NotNull(),           // deviceInfo
                                           NotNull(),           // protectedData
                                           NotNull()))          // _aidl_return
        .WillOnce(DoAll(SaveArg<2>(&eekChain),                  //
                        SaveArg<3>(&challenge),                 //
                        SetArgPointee<4>(kVerifiedDeviceInfo),  //
                        SetArgPointee<5>(kProtectedData),       //
                        SetArgPointee<6>(kFakeMac),             //
                        Return(ByMove(ScopedAStatus::ok()))));  //

    auto [csr, csrErrMsg] = getCsr("mock component name", mockRpc.get(),
                                   /*selfTest=*/false);
    ASSERT_THAT(csr, NotNull()) << csrErrMsg;
    ASSERT_THAT(csr->asArray(), Pointee(Property(&Array::size, Eq(4))));

    // Verify the input parameters that we received
    auto [parsedEek, ignore1, eekParseError] = parse(eekChain);
    ASSERT_THAT(parsedEek, NotNull()) << eekParseError;
    EXPECT_THAT(parsedEek->asArray(), Pointee(Property(&Array::size, Gt(1))));
    EXPECT_THAT(challenge, Property(&std::vector<uint8_t>::size, Eq(kChallengeSize)));

    // Device info consists of (verified info, unverified info)
    const Array* deviceInfoArray = csr->get(0)->asArray();
    EXPECT_THAT(deviceInfoArray, Pointee(Property(&Array::size, 2)));

    // Verified device info must match our mock value
    const Map* actualVerifiedDeviceInfo = deviceInfoArray->get(0)->asMap();
    EXPECT_THAT(actualVerifiedDeviceInfo, Pointee(Property(&Map::size, Eq(cborDeviceInfo.size()))));
    EXPECT_THAT(actualVerifiedDeviceInfo->get("product"), Pointee(Eq(Tstr("gShoe"))));
    EXPECT_THAT(actualVerifiedDeviceInfo->get("version"), Pointee(Eq(Uint(2))));

    // Empty unverified device info
    const Map* actualUnverifiedDeviceInfo = deviceInfoArray->get(1)->asMap();
    EXPECT_THAT(actualUnverifiedDeviceInfo, Pointee(Property(&Map::size, Eq(0))));

    // Challenge must match the call to generateCertificateRequest
    const Bstr* actualChallenge = csr->get(1)->asBstr();
    EXPECT_THAT(actualChallenge, Pointee(Property(&Bstr::value, Eq(challenge))));

    // Protected data must match the mock value
    const Array* actualProtectedData = csr->get(2)->asArray();
    EXPECT_THAT(actualProtectedData, Pointee(Eq(ByRef(cborProtectedData))));

    // Ensure the maced public key matches the expected COSE_mac0
    const Array* actualMacedKeys = csr->get(3)->asArray();
    ASSERT_THAT(actualMacedKeys, Pointee(Property(&Array::size, Eq(4))));
    ASSERT_THAT(actualMacedKeys->get(0)->asBstr(), NotNull());
    auto [macProtectedParams, ignore2, macParamParseError] =
        parse(actualMacedKeys->get(0)->asBstr());
    ASSERT_THAT(macProtectedParams, NotNull()) << macParamParseError;
    Map expectedMacProtectedParams;
    expectedMacProtectedParams.add(1, 5);
    EXPECT_THAT(macProtectedParams, Pointee(Eq(ByRef(expectedMacProtectedParams))));
    EXPECT_THAT(actualMacedKeys->get(1)->asMap(), Pointee(Property(&Map::size, Eq(0))));
    EXPECT_THAT(actualMacedKeys->get(2)->asNull(), NotNull());
    EXPECT_THAT(actualMacedKeys->get(3)->asBstr(), Pointee(Eq(Bstr(kFakeMac))));
}

TEST(LibRkpFactoryExtractionTests, GetCsrWithV3Hal) {
    const std::vector<uint8_t> kCsr = Array()
                                          .add(3 /* version */)
                                          .add(Map() /* UdsCerts */)
                                          .add(Array() /* DiceCertChain */)
                                          .add(Array() /* SignedData */)
                                          .encode();
    std::vector<uint8_t> challenge;

    // Set up mock, then call getCsr
    auto mockRpc = SharedRefBase::make<MockIRemotelyProvisionedComponent>();
    EXPECT_CALL(*mockRpc, getHardwareInfo(NotNull())).WillRepeatedly([](RpcHardwareInfo* hwInfo) {
        hwInfo->versionNumber = 3;
        return ScopedAStatus::ok();
    });
    EXPECT_CALL(*mockRpc,
                generateCertificateRequestV2(IsEmpty(),   // keysToSign
                                             _,           // challenge
                                             NotNull()))  // _aidl_return
        .WillOnce(DoAll(SaveArg<1>(&challenge), SetArgPointee<2>(kCsr),
                        Return(ByMove(ScopedAStatus::ok()))));

    auto [csr, csrErrMsg] = getCsr("mock component name", mockRpc.get(),
                                   /*selfTest=*/false);
    ASSERT_THAT(csr, NotNull()) << csrErrMsg;
    ASSERT_THAT(csr, Pointee(Property(&Array::size, Eq(5))));

    EXPECT_THAT(csr->get(0 /* version */), Pointee(Eq(Uint(3))));
    EXPECT_THAT(csr->get(1)->asMap(), NotNull());
    EXPECT_THAT(csr->get(2)->asArray(), NotNull());
    EXPECT_THAT(csr->get(3)->asArray(), NotNull());

    const Map* unverifedDeviceInfo = csr->get(4)->asMap();
    ASSERT_THAT(unverifedDeviceInfo, NotNull());
    EXPECT_THAT(unverifedDeviceInfo->get("fingerprint"), NotNull());
    const Tstr fingerprint(android::base::GetProperty("ro.build.fingerprint", ""));
    EXPECT_THAT(*unverifedDeviceInfo->get("fingerprint")->asTstr(), Eq(fingerprint));
}
