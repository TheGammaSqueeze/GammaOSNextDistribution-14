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

#include "rkp_factory_extraction_lib.h"

#include <aidl/android/hardware/security/keymint/IRemotelyProvisionedComponent.h>
#include <android-base/properties.h>
#include <android/binder_manager.h>
#include <cppbor.h>
#include <cstddef>
#include <cstdint>
#include <cstring>
#include <iterator>
#include <keymaster/cppcose/cppcose.h>
#include <openssl/base64.h>
#include <remote_prov/remote_prov_utils.h>
#include <sys/random.h>

#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "cppbor_parse.h"

using aidl::android::hardware::security::keymint::DeviceInfo;
using aidl::android::hardware::security::keymint::IRemotelyProvisionedComponent;
using aidl::android::hardware::security::keymint::MacedPublicKey;
using aidl::android::hardware::security::keymint::ProtectedData;
using aidl::android::hardware::security::keymint::RpcHardwareInfo;
using aidl::android::hardware::security::keymint::remote_prov::EekChain;
using aidl::android::hardware::security::keymint::remote_prov::generateEekChain;
using aidl::android::hardware::security::keymint::remote_prov::getProdEekChain;
using aidl::android::hardware::security::keymint::remote_prov::jsonEncodeCsrWithBuild;
using aidl::android::hardware::security::keymint::remote_prov::parseAndValidateFactoryDeviceInfo;
using aidl::android::hardware::security::keymint::remote_prov::verifyFactoryCsr;
using aidl::android::hardware::security::keymint::remote_prov::verifyFactoryProtectedData;

using namespace cppbor;
using namespace cppcose;

constexpr size_t kVersionWithoutSuperencryption = 3;

std::string toBase64(const std::vector<uint8_t>& buffer) {
    size_t base64Length;
    int rc = EVP_EncodedLength(&base64Length, buffer.size());
    if (!rc) {
        std::cerr << "Error getting base64 length. Size overflow?" << std::endl;
        exit(-1);
    }

    std::string base64(base64Length, ' ');
    rc = EVP_EncodeBlock(reinterpret_cast<uint8_t*>(base64.data()), buffer.data(), buffer.size());
    ++rc;  // Account for NUL, which BoringSSL does not for some reason.
    if (rc != base64Length) {
        std::cerr << "Error writing base64. Expected " << base64Length
                  << " bytes to be written, but " << rc << " bytes were actually written."
                  << std::endl;
        exit(-1);
    }

    // BoringSSL automatically adds a NUL -- remove it from the string data
    base64.pop_back();

    return base64;
}

std::vector<uint8_t> generateChallenge() {
    std::vector<uint8_t> challenge(kChallengeSize);

    ssize_t bytesRemaining = static_cast<ssize_t>(challenge.size());
    uint8_t* writePtr = challenge.data();
    while (bytesRemaining > 0) {
        int bytesRead = getrandom(writePtr, bytesRemaining, /*flags=*/0);
        if (bytesRead < 0) {
            if (errno == EINTR) {
                continue;
            } else {
                std::cerr << errno << ": " << strerror(errno) << std::endl;
                exit(-1);
            }
        }
        bytesRemaining -= bytesRead;
        writePtr += bytesRead;
    }

    return challenge;
}

CborResult<Array> composeCertificateRequestV1(const ProtectedData& protectedData,
                                              const DeviceInfo& verifiedDeviceInfo,
                                              const std::vector<uint8_t>& challenge,
                                              const std::vector<uint8_t>& keysToSignMac,
                                              IRemotelyProvisionedComponent* provisionable) {
    Array macedKeysToSign = Array()
                                .add(Map().add(1, 5).encode())  // alg: hmac-sha256
                                .add(Map())                     // empty unprotected headers
                                .add(Null())                    // nil for the payload
                                .add(keysToSignMac);            // MAC as returned from the HAL

    ErrMsgOr<std::unique_ptr<Map>> parsedVerifiedDeviceInfo =
        parseAndValidateFactoryDeviceInfo(verifiedDeviceInfo.deviceInfo, provisionable);
    if (!parsedVerifiedDeviceInfo) {
        return {nullptr, parsedVerifiedDeviceInfo.moveMessage()};
    }

    auto [parsedProtectedData, ignore2, errMsg] = parse(protectedData.protectedData);
    if (!parsedProtectedData) {
        std::cerr << "Error parsing protected data: '" << errMsg << "'" << std::endl;
        return {nullptr, errMsg};
    }

    Array deviceInfo = Array().add(parsedVerifiedDeviceInfo.moveValue()).add(Map());

    auto certificateRequest = std::make_unique<Array>();
    (*certificateRequest)
        .add(std::move(deviceInfo))
        .add(challenge)
        .add(std::move(parsedProtectedData))
        .add(std::move(macedKeysToSign));
    return {std::move(certificateRequest), ""};
}

CborResult<Array> getCsrV1(std::string_view componentName, IRemotelyProvisionedComponent* irpc) {
    std::vector<uint8_t> keysToSignMac;
    std::vector<MacedPublicKey> emptyKeys;
    DeviceInfo verifiedDeviceInfo;
    ProtectedData protectedData;
    RpcHardwareInfo hwInfo;
    ::ndk::ScopedAStatus status = irpc->getHardwareInfo(&hwInfo);
    if (!status.isOk()) {
        std::cerr << "Failed to get hardware info for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }

    const std::vector<uint8_t> eek = getProdEekChain(hwInfo.supportedEekCurve);
    const std::vector<uint8_t> challenge = generateChallenge();
    status = irpc->generateCertificateRequest(
        /*test_mode=*/false, emptyKeys, eek, challenge, &verifiedDeviceInfo, &protectedData,
        &keysToSignMac);
    if (!status.isOk()) {
        std::cerr << "Bundle extraction failed for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }
    return composeCertificateRequestV1(protectedData, verifiedDeviceInfo, challenge, keysToSignMac,
                                       irpc);
}

void selfTestGetCsrV1(std::string_view componentName, IRemotelyProvisionedComponent* irpc) {
    std::vector<uint8_t> keysToSignMac;
    std::vector<MacedPublicKey> emptyKeys;
    DeviceInfo verifiedDeviceInfo;
    ProtectedData protectedData;
    RpcHardwareInfo hwInfo;
    ::ndk::ScopedAStatus status = irpc->getHardwareInfo(&hwInfo);
    if (!status.isOk()) {
        std::cerr << "Failed to get hardware info for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }

    const std::vector<uint8_t> eekId = {0, 1, 2, 3, 4, 5, 6, 7};
    ErrMsgOr<EekChain> eekChain = generateEekChain(hwInfo.supportedEekCurve, /*length=*/3, eekId);
    if (!eekChain) {
        std::cerr << "Error generating test EEK certificate chain: " << eekChain.message();
        exit(-1);
    }
    const std::vector<uint8_t> challenge = generateChallenge();
    status = irpc->generateCertificateRequest(
        /*test_mode=*/true, emptyKeys, eekChain->chain, challenge, &verifiedDeviceInfo,
        &protectedData, &keysToSignMac);
    if (!status.isOk()) {
        std::cerr << "Error generating test cert chain for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }

    auto result = verifyFactoryProtectedData(verifiedDeviceInfo, /*keysToSign=*/{}, keysToSignMac,
                                             protectedData, *eekChain, eekId,
                                             hwInfo.supportedEekCurve, irpc, challenge);

    if (!result) {
        std::cerr << "Self test failed for IRemotelyProvisionedComponent '" << componentName
                  << "'. Error message: '" << result.message() << "'." << std::endl;
        exit(-1);
    }
}

CborResult<Array> composeCertificateRequestV3(const std::vector<uint8_t>& csr) {
    const std::string kFingerprintProp = "ro.build.fingerprint";

    auto [parsedCsr, _, csrErrMsg] = cppbor::parse(csr);
    if (!parsedCsr) {
        return {nullptr, csrErrMsg};
    }
    if (!parsedCsr->asArray()) {
        return {nullptr, "CSR is not a CBOR array."};
    }

    if (!::android::base::WaitForPropertyCreation(kFingerprintProp)) {
        return {nullptr, "Unable to read build fingerprint"};
    }

    Map unverifiedDeviceInfo =
        Map().add("fingerprint", ::android::base::GetProperty(kFingerprintProp, /*default=*/""));
    parsedCsr->asArray()->add(std::move(unverifiedDeviceInfo));
    return {std::unique_ptr<Array>(parsedCsr.release()->asArray()), ""};
}

CborResult<cppbor::Array> getCsrV3(std::string_view componentName,
                                   IRemotelyProvisionedComponent* irpc, bool selfTest) {
    std::vector<uint8_t> csr;
    std::vector<MacedPublicKey> emptyKeys;
    const std::vector<uint8_t> challenge = generateChallenge();

    auto status = irpc->generateCertificateRequestV2(emptyKeys, challenge, &csr);
    if (!status.isOk()) {
        std::cerr << "Bundle extraction failed for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }

    if (selfTest) {
        auto result = verifyFactoryCsr(/*keysToSign=*/cppbor::Array(), csr, irpc, challenge);
        if (!result) {
            std::cerr << "Self test failed for IRemotelyProvisionedComponent '" << componentName
                      << "'. Error message: '" << result.message() << "'." << std::endl;
            exit(-1);
        }
    }

    return composeCertificateRequestV3(csr);
}

CborResult<Array> getCsr(std::string_view componentName, IRemotelyProvisionedComponent* irpc,
                         bool selfTest) {
    RpcHardwareInfo hwInfo;
    auto status = irpc->getHardwareInfo(&hwInfo);
    if (!status.isOk()) {
        std::cerr << "Failed to get hardware info for '" << componentName
                  << "'. Description: " << status.getDescription() << "." << std::endl;
        exit(-1);
    }

    if (hwInfo.versionNumber < kVersionWithoutSuperencryption) {
        if (selfTest) {
            selfTestGetCsrV1(componentName, irpc);
        }
        return getCsrV1(componentName, irpc);
    } else {
        return getCsrV3(componentName, irpc, selfTest);
    }
}
