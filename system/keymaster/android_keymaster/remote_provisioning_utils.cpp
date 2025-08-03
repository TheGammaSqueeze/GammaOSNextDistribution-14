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

#include <keymaster/remote_provisioning_utils.h>

#include <algorithm>
#include <span>

#include <keymaster/cppcose/cppcose.h>
#include <keymaster/logger.h>

namespace keymaster {

using cppcose::ALGORITHM;
using cppcose::COSE_KEY;
using cppcose::CoseKey;
using cppcose::CoseKeyCurve;
using cppcose::EC2;
using cppcose::ECDH_ES_HKDF_256;
using cppcose::ES256;
using cppcose::generateCoseMac0Mac;
using cppcose::HMAC_256;
using cppcose::kCoseMac0EntryCount;
using cppcose::kCoseMac0Payload;
using cppcose::kCoseMac0ProtectedParams;
using cppcose::kCoseMac0Tag;
using cppcose::kCoseMac0UnprotectedParams;
using cppcose::KEY_ID;
using cppcose::OCTET_KEY_PAIR;
using cppcose::P256;
using cppcose::verifyAndParseCoseSign1;

using byte_view = std::span<const uint8_t>;

struct KeyInfo {
    CoseKeyCurve curve;
    byte_view pubkey;
    // Note: There's no need to include algorithm here, since it is assumed
    //       that all root keys are EDDSA.

    bool operator==(const KeyInfo& other) const {
        return curve == other.curve &&
               std::equal(pubkey.begin(), pubkey.end(), other.pubkey.begin(), other.pubkey.end());
    }
};

// The production root signing key for Google Endpoint Encryption Key cert chains.
inline constexpr uint8_t kGeekRoot[] = {
    0x99, 0xB9, 0xEE, 0xDD, 0x5E, 0xE4, 0x52, 0xF6, 0x85, 0xC6, 0x4C, 0x62, 0xDC, 0x3E, 0x61, 0xAB,
    0x57, 0x48, 0x7D, 0x75, 0x37, 0x29, 0xAD, 0x76, 0x80, 0x32, 0xD2, 0xB3, 0xCB, 0x63, 0x58, 0xD9};

// Hard-coded set of acceptable public COSE_Keys that can act as roots of EEK chains.
inline constexpr KeyInfo kAuthorizedEekRoots[] = {
    {CoseKeyCurve::ED25519, byte_view(kGeekRoot, sizeof(kGeekRoot))},
};

StatusOr<std::pair<std::vector<uint8_t> /* EEK pub */, std::vector<uint8_t> /* EEK ID */>>
validateAndExtractEekPubAndId(bool testMode, const KeymasterBlob& endpointEncryptionCertChain) {
    auto [item, newPos, errMsg] =
        cppbor::parse(endpointEncryptionCertChain.begin(), endpointEncryptionCertChain.end());

    if (!item || !item->asArray()) {
        LOG_E("Error parsing EEK chain: %s", errMsg.c_str());
        return kStatusFailed;
    }

    const cppbor::Array* certArr = item->asArray();
    std::vector<uint8_t> lastPubKey;
    for (size_t i = 0; i < certArr->size(); ++i) {
        auto cosePubKey =
            verifyAndParseCoseSign1(certArr->get(i)->asArray(), lastPubKey, {} /* AAD */);
        if (!cosePubKey) {
            LOG_E("Failed to validate EEK chain: %s", cosePubKey.moveMessage().c_str());
            return kStatusInvalidEek;
        }
        lastPubKey = *std::move(cosePubKey);

        // In prod mode the first pubkey should match a well-known Google public key.
        if (!testMode && i == 0) {
            auto parsedPubKey = CoseKey::parse(lastPubKey);
            if (!parsedPubKey) {
                LOG_E("%s", parsedPubKey.moveMessage().c_str());
                return kStatusFailed;
            }

            auto curve = parsedPubKey->getIntValue(CoseKey::CURVE);
            if (!curve) {
                LOG_E("Key is missing required label 'CURVE'");
                return kStatusInvalidEek;
            }

            auto rawPubKey = parsedPubKey->getBstrValue(CoseKey::PUBKEY_X);
            if (!rawPubKey) {
                LOG_E("Key is missing required label 'PUBKEY_X'");
                return kStatusInvalidEek;
            }

            KeyInfo matcher = {static_cast<CoseKeyCurve>(*curve),
                               byte_view(rawPubKey->data(), rawPubKey->size())};
            if (std::find(std::begin(kAuthorizedEekRoots), std::end(kAuthorizedEekRoots),
                          matcher) == std::end(kAuthorizedEekRoots)) {
                LOG_E("Unrecognized root of EEK chain");
                return kStatusInvalidEek;
            }
        }
    }

    auto eek = CoseKey::parseX25519(lastPubKey, true /* requireKid */);
    if (!eek) {
        LOG_E("Failed to get EEK: %s", eek.moveMessage().c_str());
        return kStatusInvalidEek;
    }

    return std::make_pair(eek->getBstrValue(CoseKey::PUBKEY_X).value(),
                          eek->getBstrValue(CoseKey::KEY_ID).value());
}

StatusOr<cppbor::Array /* pubkeys */>
validateAndExtractPubkeys(bool testMode, uint32_t numKeys, KeymasterBlob* keysToSign,
                          const cppcose::HmacSha256Function& macFunction) {
    auto pubKeysToMac = cppbor::Array();
    for (size_t i = 0; i < numKeys; i++) {
        auto [macedKeyItem, _, coseMacErrMsg] =
            cppbor::parse(keysToSign[i].begin(), keysToSign[i].end());
        if (!macedKeyItem || !macedKeyItem->asArray() ||
            macedKeyItem->asArray()->size() != kCoseMac0EntryCount) {
            LOG_E("Invalid COSE_Mac0 structure");
            return kStatusFailed;
        }

        auto protectedParms = macedKeyItem->asArray()->get(kCoseMac0ProtectedParams)->asBstr();
        auto unprotectedParms = macedKeyItem->asArray()->get(kCoseMac0UnprotectedParams)->asMap();
        auto payload = macedKeyItem->asArray()->get(kCoseMac0Payload)->asBstr();
        auto tag = macedKeyItem->asArray()->get(kCoseMac0Tag)->asBstr();
        if (!protectedParms || !unprotectedParms || !payload || !tag) {
            LOG_E("Invalid COSE_Mac0 contents");
            return kStatusFailed;
        }

        auto [protectedMap, __, errMsg] = cppbor::parse(protectedParms);
        if (!protectedMap || !protectedMap->asMap()) {
            LOG_E("Invalid Mac0 protected: %s", errMsg.c_str());
            return kStatusFailed;
        }
        auto& algo = protectedMap->asMap()->get(ALGORITHM);
        if (!algo || !algo->asInt() || algo->asInt()->value() != HMAC_256) {
            LOG_E("Unsupported Mac0 algorithm");
            return kStatusFailed;
        }

        auto pubKey = CoseKey::parse(payload->value(), EC2, ES256, P256);
        if (!pubKey) {
            LOG_E("%s", pubKey.moveMessage().c_str());
            return kStatusFailed;
        }

        bool testKey = static_cast<bool>(pubKey->getMap().get(CoseKey::TEST_KEY));
        if (testMode && !testKey) {
            LOG_E("Production key in test request");
            return kStatusProductionKeyInTestRequest;
        } else if (!testMode && testKey) {
            LOG_E("Test key in production request");
            return kStatusTestKeyInProductionRequest;
        }

        auto macTag = generateCoseMac0Mac(macFunction, {} /* external_aad */, payload->value());
        if (!macTag) {
            LOG_E("%s", macTag.moveMessage().c_str());
            return kStatusInvalidMac;
        }
        if (macTag->size() != tag->value().size() ||
            CRYPTO_memcmp(macTag->data(), tag->value().data(), macTag->size()) != 0) {
            LOG_E("MAC tag mismatch");
            return kStatusInvalidMac;
        }

        pubKeysToMac.add(pubKey->moveMap());
    }

    return pubKeysToMac;
}

cppbor::Array buildCertReqRecipients(const std::vector<uint8_t>& pubkey,
                                     const std::vector<uint8_t>& kid) {
    return cppbor::Array()           // Array of recipients
        .add(cppbor::Array()         // Recipient
                 .add(cppbor::Map()  // Protected
                          .add(ALGORITHM, ECDH_ES_HKDF_256)
                          .canonicalize()
                          .encode())
                 .add(cppbor::Map()  // Unprotected
                          .add(COSE_KEY, cppbor::Map()
                                             .add(CoseKey::KEY_TYPE, OCTET_KEY_PAIR)
                                             .add(CoseKey::CURVE, cppcose::X25519)
                                             .add(CoseKey::PUBKEY_X, pubkey)
                                             .canonicalize())
                          .add(KEY_ID, kid)
                          .canonicalize())
                 .add(cppbor::Null()));  // No ciphertext
}

}  // namespace keymaster
