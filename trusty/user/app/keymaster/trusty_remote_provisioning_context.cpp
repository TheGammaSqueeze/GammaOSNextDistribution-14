/*
 * Copyright 2021 The Android Open Source Project
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

#include "trusty_remote_provisioning_context.h"

#include <assert.h>
#include <keymaster/cppcose/cppcose.h>
#include <keymaster/logger.h>
#include <lib/hwbcc/client/hwbcc.h>
#include <lib/hwkey/hwkey.h>
#include <lib/system_state/system_state.h>
#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/hkdf.h>
#include <openssl/rand.h>
#include <algorithm>
#include <cstdint>

#include "keymaster_attributes.pb.h"
#include "secure_storage_manager.h"

namespace keymaster {

using cppcose::ALGORITHM;
using cppcose::constructCoseSign1;
using cppcose::CoseKey;
using cppcose::ED25519;
using cppcose::EDDSA;
using cppcose::ErrMsgOr;
using cppcose::OCTET_KEY_PAIR;
using cppcose::VERIFY;

constexpr uint32_t kMacKeyLength = 32;
constexpr uint32_t kRkpVersion = TRUSTY_KM_RKP_VERSION;

static const uint8_t kMasterKeyDerivationData[kMacKeyLength] =
        "RemoteKeyProvisioningMasterKey";

std::vector<uint8_t> TrustyRemoteProvisioningContext::DeriveBytesFromHbk(
        const std::string& context,
        size_t num_bytes) const {
    long rc = hwkey_open();
    if (rc < 0) {
        LOG_S("Couldn't open hwkey session: %ld", rc);
        return {};
    }

    hwkey_session_t session = static_cast<hwkey_session_t>(rc);
    std::array<uint8_t, kMacKeyLength> hw_backed_key;

    uint32_t kdf_version = HWKEY_KDF_VERSION_1;
    rc = hwkey_derive(session, &kdf_version, kMasterKeyDerivationData,
                      hw_backed_key.data(), kMacKeyLength);

    if (rc < 0) {
        LOG_S("Error deriving master key: %ld", rc);
        return {};
    }

    hwkey_close(session);

    std::vector<uint8_t> result(num_bytes);

    // TODO: Figure out if HKDF can fail.  It doesn't seem like it should be
    // able to, but the function does return an error code.
    HKDF(result.data(), num_bytes,                    //
         EVP_sha256(),                                //
         hw_backed_key.data(), hw_backed_key.size(),  //
         nullptr /* salt */, 0 /* salt len */,        //
         reinterpret_cast<const uint8_t*>(context.data()), context.size());

    return result;
}

keymaster_error_t ReadAttestationIds(AttestationIds* ids) {
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        LOG_E("Failed to open secure storage session.");
        return KM_ERROR_SECURE_HW_BUSY;
    }

    auto err = ss_manager->ReadAttestationIds(ids);
    if (err != KM_ERROR_OK) {
        LOG_E("Failed to read attestation IDs");
        return err;
    }
    return KM_ERROR_OK;
}

#define ADD_ID_FIELD(array, proto, field_name)                              \
    if ((proto).size > 0) {                                                 \
        (array)->add(                                                       \
                (field_name),                                               \
                cppbor::Tstr((proto).bytes, (proto).bytes + (proto).size)); \
    }

std::unique_ptr<cppbor::Map> TrustyRemoteProvisioningContext::CreateDeviceInfo(
        uint32_t csrVersion) const {
    auto result = std::make_unique<cppbor::Map>();
    AttestationIds ids;
    if (ReadAttestationIds(&ids) != KM_ERROR_OK) {
        return result;
    }
    ADD_ID_FIELD(result, ids.brand, "brand")
    ADD_ID_FIELD(result, ids.manufacturer, "manufacturer")
    ADD_ID_FIELD(result, ids.product, "product")
    ADD_ID_FIELD(result, ids.model, "model")
    ADD_ID_FIELD(result, ids.device, "device")
    if (bootParams_) {
        // KM validated device_locked and verified_boot_state combinations, so
        // there is no need to re-validate here.
        switch (bootParams_->verified_boot_state) {
        case KM_VERIFIED_BOOT_VERIFIED:
            result->add("vb_state", "green");
            break;
        case KM_VERIFIED_BOOT_SELF_SIGNED:
            result->add("vb_state", "yellow");
            break;
        case KM_VERIFIED_BOOT_UNVERIFIED:
            result->add("vb_state", "orange");
            break;
        default:
            break;
        }
        result->add("bootloader_state",
                    bootParams_->device_locked ? "locked" : "unlocked");
        result->add("vbmeta_digest",
                    cppbor::Bstr(bootParams_->verified_boot_hash.begin(),
                                 bootParams_->verified_boot_hash.end()));
        result->add("os_version", std::to_string(bootParams_->boot_os_version));
        result->add("system_patch_level",
                    cppbor::Uint(bootParams_->boot_os_patchlevel));
        result->add("boot_patch_level", cppbor::Uint(boot_patchlevel_));
        result->add("vendor_patch_level", cppbor::Uint(vendor_patchlevel_));
        result->add("fused", system_state_get_flag_default(
                                     SYSTEM_STATE_FLAG_APP_LOADING_UNLOCKED,
                                     0 /* default */)
                                     ? 0
                                     : 1);
        result->add("security_level", "tee");
        // "version" field was removed from DeviceInfo in CSR v3.
        if (csrVersion < 3) {
            result->add("version", csrVersion);
        }
    }

    result->canonicalize();
    return result;
}

cppcose::ErrMsgOr<std::vector<uint8_t>>
TrustyRemoteProvisioningContext::BuildProtectedDataPayload(
        bool testMode,
        const std::vector<uint8_t>& macKey,
        const std::vector<uint8_t>& aad) const {
    std::vector<uint8_t> signedOutput(HWBCC_MAX_RESP_PAYLOAD_SIZE);
    std::vector<uint8_t> bcc(HWBCC_MAX_RESP_PAYLOAD_SIZE);
    size_t actualBccSize = 0;
    size_t actualSignedMacKeySize = 0;
    int rc = hwbcc_get_protected_data(
            testMode, EDDSA, macKey.data(), macKey.size(), aad.data(),
            aad.size(), signedOutput.data(), signedOutput.size(),
            &actualSignedMacKeySize, bcc.data(), bcc.size(), &actualBccSize);
    if (rc != 0) {
        LOG_E("Error: [%d] Failed to sign the MAC key on WHI", rc);
        return "Failed to sign the MAC key on WHI";
    }
    signedOutput.resize(actualSignedMacKeySize);
    bcc.resize(actualBccSize);
    return cppbor::Array()
            .add(cppbor::EncodedItem(std::move(signedOutput)))
            .add(cppbor::EncodedItem(std::move(bcc)))
            .encode();
}

std::optional<cppcose::HmacSha256>
TrustyRemoteProvisioningContext::GenerateHmacSha256(
        const cppcose::bytevec& input) const {
    auto key = DeriveBytesFromHbk("Key to MAC public keys", kMacKeyLength);
    auto result = cppcose::generateHmacSha256(key, input);
    if (!result) {
        LOG_E("Error signing MAC: %s", result.message().c_str());
        return std::nullopt;
    }
    return *result;
}

void TrustyRemoteProvisioningContext::GetHwInfo(
        GetHwInfoResponse* hwInfo) const {
    hwInfo->version = kRkpVersion;
    hwInfo->rpcAuthorName = "Google";
    hwInfo->supportedEekCurve =
            kRkpVersion >= 3 ? 0 /* CURVE_NONE */ : 2 /* CURVE_25519 */;
    hwInfo->uniqueId = "Google Trusty Implementation";
    hwInfo->supportedNumKeysInCsr = 20;
}

cppcose::ErrMsgOr<cppbor::Array> TrustyRemoteProvisioningContext::BuildCsr(
        const std::vector<uint8_t>& challenge,
        cppbor::Array keysToSign) const {
    uint32_t csrVersion = 3;
    auto deviceInfo = std::move(*CreateDeviceInfo(csrVersion));
    auto csrPayload = cppbor::Array()
                              .add(csrVersion)
                              .add("keymint" /* CertificateType */)
                              .add(std::move(deviceInfo))
                              .add(std::move(keysToSign))
                              .encode();
    auto signedDataPayload = cppbor::Array()
                                     .add(challenge)
                                     .add(cppbor::Bstr(csrPayload))
                                     .encode();

    std::vector<uint8_t> signedData(HWBCC_MAX_RESP_PAYLOAD_SIZE);
    std::vector<uint8_t> bcc(HWBCC_MAX_RESP_PAYLOAD_SIZE);
    size_t actualSignedDataSize = 0;
    size_t actualBccSize = 0;

    int rc = hwbcc_get_protected_data(
            false /* test_mode */, EDDSA, signedDataPayload.data(),
            signedDataPayload.size(), NULL, 0, signedData.data(),
            signedData.size(), &actualSignedDataSize, bcc.data(), bcc.size(),
            &actualBccSize);
    if (rc != 0) {
        LOG_E("Error: [%d] Failed hwbcc_get_protected_data()", rc);
        return "Failed hwbcc_get_protected_data";
    }
    signedData.resize(actualSignedDataSize);
    bcc.resize(actualBccSize);

    return cppbor::Array()
            .add(1 /* version */)
            .add(cppbor::Map() /* UdsCerts */)
            .add(cppbor::EncodedItem(std::move(bcc)))
            .add(cppbor::EncodedItem(std::move(signedData)));
}

void TrustyRemoteProvisioningContext::SetBootParams(
        const BootParams* bootParams) {
    if (bootParamsSet_) {
        LOG_E("Boot parameters are already set in the remote provisioning context");
    }
    bootParamsSet_ = true;
    bootParams_ = bootParams;
}

}  // namespace keymaster
