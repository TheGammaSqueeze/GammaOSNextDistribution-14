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

#include <keymaster/contexts/pure_soft_remote_provisioning_context.h>

#include <algorithm>
#include <android-base/logging.h>
#include <assert.h>

#include <keymaster/cppcose/cppcose.h>
#include <keymaster/logger.h>

#include <openssl/bn.h>
#include <openssl/ec.h>
#include <openssl/hkdf.h>
#include <openssl/rand.h>

namespace keymaster {
namespace {

using cppcose::constructCoseSign1;
using cppcose::CoseKey;
using cppcose::ED25519;
using cppcose::EDDSA;
using cppcose::ErrMsgOr;
using cppcose::OCTET_KEY_PAIR;
using cppcose::VERIFY;

std::array<uint8_t, 32> GetRandomBytes() {
    std::array<uint8_t, 32> bytes;
    // This is used in code paths that cannot fail, so CHECK. If it turns
    // out that we can actually run out of entropy during thes code paths,
    // we'll need to refactor the interfaces to allow errors to propagate.
    CHECK_EQ(RAND_bytes(bytes.data(), bytes.size()), 1) << "Unable to get random bytes";
    return bytes;
}

}  // namespace

PureSoftRemoteProvisioningContext::PureSoftRemoteProvisioningContext(
    keymaster_security_level_t security_level)
    : security_level_(security_level) {}

std::vector<uint8_t>
PureSoftRemoteProvisioningContext::DeriveBytesFromHbk(const std::string& context,
                                                      size_t num_bytes) const {
    static const std::array<uint8_t, 32> fakeHbk = GetRandomBytes();
    std::vector<uint8_t> result(num_bytes);

    // TODO: Figure out if HKDF can fail.  It doesn't seem like it should be able to,
    // but the function does return an error code.
    HKDF(result.data(), num_bytes,              //
         EVP_sha256(),                          //
         fakeHbk.data(), fakeHbk.size(),        //
         nullptr /* salt */, 0 /* salt len */,  //
         reinterpret_cast<const uint8_t*>(context.data()), context.size());

    return result;
}

std::unique_ptr<cppbor::Map>
PureSoftRemoteProvisioningContext::CreateDeviceInfo(uint32_t csrVersion) const {
    auto result = std::make_unique<cppbor::Map>(cppbor::Map());

    // The following placeholders show how the DeviceInfo map would be populated.
    result->add(cppbor::Tstr("brand"), cppbor::Tstr("Google"));
    result->add(cppbor::Tstr("manufacturer"), cppbor::Tstr("Google"));
    result->add(cppbor::Tstr("product"), cppbor::Tstr("Fake Product"));
    result->add(cppbor::Tstr("model"), cppbor::Tstr("Fake Model"));
    result->add(cppbor::Tstr("device"), cppbor::Tstr("Fake Device"));
    if (bootloader_state_) {
        result->add(cppbor::Tstr("bootloader_state"), cppbor::Tstr(*bootloader_state_));
    }
    if (verified_boot_state_) {
        result->add(cppbor::Tstr("vb_state"), cppbor::Tstr(*verified_boot_state_));
    }
    if (vbmeta_digest_) {
        result->add(cppbor::Tstr("vbmeta_digest"), cppbor::Bstr(*vbmeta_digest_));
    }
    if (os_version_) {
        result->add(cppbor::Tstr("os_version"), cppbor::Tstr(std::to_string(*os_version_)));
    }
    if (os_patchlevel_) {
        result->add(cppbor::Tstr("system_patch_level"), cppbor::Uint(*os_patchlevel_));
    }
    if (boot_patchlevel_) {
        result->add(cppbor::Tstr("boot_patch_level"), cppbor::Uint(*boot_patchlevel_));
    }
    if (vendor_patchlevel_) {
        result->add(cppbor::Tstr("vendor_patch_level"), cppbor::Uint(*vendor_patchlevel_));
    }
    // "version" field was removed from DeviceInfo in CSR v3.
    if (csrVersion < 3) {
        result->add(cppbor::Tstr("version"), cppbor::Uint(csrVersion));
    }
    result->add(cppbor::Tstr("fused"), cppbor::Uint(0));

    // "software" security level is not supported, so lie and say we're a TEE
    // even if we're software.
    const char* security_level =
        security_level_ == KM_SECURITY_LEVEL_STRONGBOX ? "strongbox" : "tee";
    result->add(cppbor::Tstr("security_level"), cppbor::Tstr(security_level));

    result->canonicalize();
    return result;
}

void PureSoftRemoteProvisioningContext::LazyInitProdBcc() const {
    std::call_once(bccInitFlag_,
                   [this]() { std::tie(devicePrivKey_, bcc_) = GenerateBcc(/*testMode=*/false); });
}

std::pair<std::vector<uint8_t> /* privKey */, cppbor::Array /* BCC */>
PureSoftRemoteProvisioningContext::GenerateBcc(bool testMode) const {
    std::vector<uint8_t> privKey(ED25519_PRIVATE_KEY_LEN);
    std::vector<uint8_t> pubKey(ED25519_PUBLIC_KEY_LEN);

    std::array<uint8_t, 32> seed;  // Length is hard-coded in the BoringCrypto API
    if (testMode) {
        seed = GetRandomBytes();
    } else {
        auto seed_vector = DeriveBytesFromHbk("Device Key Seed", sizeof(seed));
        std::copy(seed_vector.begin(), seed_vector.end(), seed.begin());
    }
    ED25519_keypair_from_seed(pubKey.data(), privKey.data(), seed.data());

    auto coseKey = cppbor::Map()
                       .add(CoseKey::KEY_TYPE, OCTET_KEY_PAIR)
                       .add(CoseKey::ALGORITHM, EDDSA)
                       .add(CoseKey::CURVE, ED25519)
                       .add(CoseKey::PUBKEY_X, pubKey)
                       .canonicalize();
    auto sign1Payload = cppbor::Map()
                            .add(1 /* Issuer */, "Issuer")
                            .add(2 /* Subject */, "Subject")
                            .add(-4670552 /* Subject Pub Key */, coseKey.encode())
                            .add(-4670553 /* Key Usage (little-endian order) */,
                                 std::vector<uint8_t>{0x20} /* keyCertSign = 1<<5 */)
                            .canonicalize()
                            .encode();
    auto coseSign1 = constructCoseSign1(privKey,       /* signing key */
                                        cppbor::Map(), /* extra protected */
                                        sign1Payload, {} /* AAD */);
    assert(coseSign1);

    return {privKey, cppbor::Array().add(std::move(coseKey)).add(coseSign1.moveValue())};
}

ErrMsgOr<std::vector<uint8_t>> PureSoftRemoteProvisioningContext::BuildProtectedDataPayload(
    bool isTestMode,                     //
    const std::vector<uint8_t>& macKey,  //
    const std::vector<uint8_t>& aad) const {
    std::vector<uint8_t> devicePrivKey;
    cppbor::Array bcc;
    if (isTestMode) {
        std::tie(devicePrivKey, bcc) = GenerateBcc(/*testMode=*/true);
    } else {
        LazyInitProdBcc();
        devicePrivKey = devicePrivKey_;
        auto clone = bcc_.clone();
        if (!clone->asArray()) {
            return "The BCC is not an array";
        }
        bcc = std::move(*clone->asArray());
    }
    auto sign1 = constructCoseSign1(devicePrivKey, macKey, aad);
    if (!sign1) {
        return sign1.moveMessage();
    }
    return cppbor::Array().add(sign1.moveValue()).add(std::move(bcc)).encode();
}

std::optional<cppcose::HmacSha256>
PureSoftRemoteProvisioningContext::GenerateHmacSha256(const cppcose::bytevec& input) const {
    // Fix the key for now, else HMACs will fail to verify after reboot.
    static const uint8_t kHmacKey[] = "Key to MAC public keys";
    std::vector<uint8_t> key(std::begin(kHmacKey), std::end(kHmacKey));
    auto result = cppcose::generateHmacSha256(key, input);
    if (!result) {
        LOG_E("Error signing MAC: %s", result.message().c_str());
        return std::nullopt;
    }
    return *result;
}

void PureSoftRemoteProvisioningContext::GetHwInfo(GetHwInfoResponse* hwInfo) const {
    hwInfo->version = 3;
    hwInfo->rpcAuthorName = "Google";
    hwInfo->supportedEekCurve = 2 /* CURVE_25519 */;
    hwInfo->uniqueId = "default keymint";
    hwInfo->supportedNumKeysInCsr = 20;
}

cppcose::ErrMsgOr<cppbor::Array>
PureSoftRemoteProvisioningContext::BuildCsr(const std::vector<uint8_t>& challenge,
                                            cppbor::Array keysToSign) const {
    LazyInitProdBcc();
    uint32_t csrVersion = 3;
    auto deviceInfo = std::move(*CreateDeviceInfo(csrVersion));
    auto csrPayload = cppbor::Array()
                          .add(csrVersion)
                          .add("keymint" /* CertificateType */)
                          .add(std::move(deviceInfo))
                          .add(std::move(keysToSign));
    auto signedDataPayload = cppbor::Array().add(challenge).add(cppbor::Bstr(csrPayload.encode()));
    auto signedData = constructCoseSign1(devicePrivKey_, signedDataPayload.encode(), {} /* aad */);

    return cppbor::Array()
        .add(1 /* version */)
        .add(cppbor::Map() /* UdsCerts */)
        .add(std::move(*bcc_.clone()->asArray()) /* DiceCertChain */)
        .add(std::move(*signedData) /* SignedData */);
}

void PureSoftRemoteProvisioningContext::SetSystemVersion(uint32_t os_version,
                                                         uint32_t os_patchlevel) {
    os_version_ = os_version;
    os_patchlevel_ = os_patchlevel;
}

void PureSoftRemoteProvisioningContext::SetVendorPatchlevel(uint32_t vendor_patchlevel) {
    vendor_patchlevel_ = vendor_patchlevel;
}

void PureSoftRemoteProvisioningContext::SetBootPatchlevel(uint32_t boot_patchlevel) {
    boot_patchlevel_ = boot_patchlevel;
}

void PureSoftRemoteProvisioningContext::SetVerifiedBootInfo(
    std::string_view boot_state, std::string_view bootloader_state,
    const std::vector<uint8_t>& vbmeta_digest) {
    verified_boot_state_ = boot_state;
    bootloader_state_ = bootloader_state;
    vbmeta_digest_ = vbmeta_digest;
}

}  // namespace keymaster
