/*
 * Copyright (C) 2020 The Android Open Source Project
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

#include <keymaster/cppcose/cppcose.h>

#include <iostream>
#include <stdio.h>

#include <cppbor.h>
#include <cppbor_parse.h>
#include <openssl/ecdsa.h>

#include <openssl/err.h>

namespace cppcose {
constexpr int kP256AffinePointSize = 32;
constexpr int kP384AffinePointSize = 48;

using EVP_PKEY_Ptr = bssl::UniquePtr<EVP_PKEY>;
using EVP_PKEY_CTX_Ptr = bssl::UniquePtr<EVP_PKEY_CTX>;
using ECDSA_SIG_Ptr = bssl::UniquePtr<ECDSA_SIG>;
using EC_KEY_Ptr = bssl::UniquePtr<EC_KEY>;

namespace {

ErrMsgOr<bssl::UniquePtr<EVP_CIPHER_CTX>> aesGcmInitAndProcessAad(const bytevec& key,
                                                                  const bytevec& nonce,
                                                                  const bytevec& aad,
                                                                  bool encrypt) {
    if (key.size() != kAesGcmKeySize) return "Invalid key size";

    bssl::UniquePtr<EVP_CIPHER_CTX> ctx(EVP_CIPHER_CTX_new());
    if (!ctx) return "Failed to allocate cipher context";

    if (!EVP_CipherInit_ex(ctx.get(), EVP_aes_256_gcm(), nullptr /* engine */, key.data(),
                           nonce.data(), encrypt ? 1 : 0)) {
        return "Failed to initialize cipher";
    }

    int outlen;
    if (!aad.empty() && !EVP_CipherUpdate(ctx.get(), nullptr /* out; null means AAD */, &outlen,
                                          aad.data(), aad.size())) {
        return "Failed to process AAD";
    }

    return std::move(ctx);
}

ErrMsgOr<bytevec> signP256Digest(const bytevec& key, const bytevec& data) {
    auto bn = BIGNUM_Ptr(BN_bin2bn(key.data(), key.size(), nullptr));
    if (bn.get() == nullptr) {
        return "Error creating BIGNUM";
    }

    auto ec_key = EC_KEY_Ptr(EC_KEY_new_by_curve_name(NID_X9_62_prime256v1));
    if (EC_KEY_set_private_key(ec_key.get(), bn.get()) != 1) {
        return "Error setting private key from BIGNUM";
    }

    auto sig = ECDSA_SIG_Ptr(ECDSA_do_sign(data.data(), data.size(), ec_key.get()));
    if (sig == nullptr) {
        return "Error signing digest";
    }
    size_t len = i2d_ECDSA_SIG(sig.get(), nullptr);
    bytevec signature(len);
    unsigned char* p = (unsigned char*)signature.data();
    i2d_ECDSA_SIG(sig.get(), &p);
    return signature;
}

ErrMsgOr<bytevec> ecdh(const bytevec& publicKey, const bytevec& privateKey) {
    auto group = EC_GROUP_Ptr(EC_GROUP_new_by_curve_name(NID_X9_62_prime256v1));
    auto point = EC_POINT_Ptr(EC_POINT_new(group.get()));
    if (EC_POINT_oct2point(group.get(), point.get(), publicKey.data(), publicKey.size(), nullptr) !=
        1) {
        return "Error decoding publicKey";
    }
    auto ecKey = EC_KEY_Ptr(EC_KEY_new());
    auto pkey = EVP_PKEY_Ptr(EVP_PKEY_new());
    if (ecKey.get() == nullptr || pkey.get() == nullptr) {
        return "Memory allocation failed";
    }
    if (EC_KEY_set_group(ecKey.get(), group.get()) != 1) {
        return "Error setting group";
    }
    if (EC_KEY_set_public_key(ecKey.get(), point.get()) != 1) {
        return "Error setting point";
    }
    if (EVP_PKEY_set1_EC_KEY(pkey.get(), ecKey.get()) != 1) {
        return "Error setting key";
    }

    auto bn = BIGNUM_Ptr(BN_bin2bn(privateKey.data(), privateKey.size(), nullptr));
    if (bn.get() == nullptr) {
        return "Error creating BIGNUM for private key";
    }
    auto privEcKey = EC_KEY_Ptr(EC_KEY_new_by_curve_name(NID_X9_62_prime256v1));
    if (EC_KEY_set_private_key(privEcKey.get(), bn.get()) != 1) {
        return "Error setting private key from BIGNUM";
    }
    auto privPkey = EVP_PKEY_Ptr(EVP_PKEY_new());
    if (EVP_PKEY_set1_EC_KEY(privPkey.get(), privEcKey.get()) != 1) {
        return "Error setting private key";
    }

    auto ctx = EVP_PKEY_CTX_Ptr(EVP_PKEY_CTX_new(privPkey.get(), NULL));
    if (ctx.get() == nullptr) {
        return "Error creating context";
    }

    if (EVP_PKEY_derive_init(ctx.get()) != 1) {
        return "Error initializing context";
    }

    if (EVP_PKEY_derive_set_peer(ctx.get(), pkey.get()) != 1) {
        return "Error setting peer";
    }

    /* Determine buffer length for shared secret */
    size_t secretLen = 0;
    if (EVP_PKEY_derive(ctx.get(), NULL, &secretLen) != 1) {
        return "Error determing length of shared secret";
    }
    bytevec sharedSecret(secretLen);

    if (EVP_PKEY_derive(ctx.get(), sharedSecret.data(), &secretLen) != 1) {
        return "Error deriving shared secret";
    }
    return sharedSecret;
}

ErrMsgOr<bytevec> ecdsaCoseSignatureToDer(int point_size, const bytevec& ecdsaCoseSignature) {
    if (ecdsaCoseSignature.size() != (size_t)(point_size * 2)) {
        return "COSE signature wrong length";
    }

    auto rBn = BIGNUM_Ptr(BN_bin2bn(ecdsaCoseSignature.data(), point_size, nullptr));
    if (rBn.get() == nullptr) {
        return "Error creating BIGNUM for r";
    }

    auto sBn = BIGNUM_Ptr(BN_bin2bn(ecdsaCoseSignature.data() + point_size, point_size, nullptr));
    if (sBn.get() == nullptr) {
        return "Error creating BIGNUM for s";
    }

    ECDSA_SIG sig;
    sig.r = rBn.get();
    sig.s = sBn.get();

    size_t len = i2d_ECDSA_SIG(&sig, nullptr);
    bytevec derSignature(len);
    unsigned char* p = (unsigned char*)derSignature.data();
    i2d_ECDSA_SIG(&sig, &p);
    return derSignature;
}

ErrMsgOr<bytevec> ecdsaDerSignatureToCose(int point_size, const bytevec& ecdsaSignature) {
    const unsigned char* p = ecdsaSignature.data();
    auto sig = ECDSA_SIG_Ptr(d2i_ECDSA_SIG(nullptr, &p, ecdsaSignature.size()));
    if (sig == nullptr) {
        return "Error decoding DER signature";
    }

    bytevec ecdsaCoseSignature(point_size * 2, 0);
    if (BN_bn2binpad(ECDSA_SIG_get0_r(sig.get()), ecdsaCoseSignature.data(), point_size) !=
        point_size) {
        return "Error encoding r";
    }
    if (BN_bn2binpad(ECDSA_SIG_get0_s(sig.get()), ecdsaCoseSignature.data() + point_size,
                     point_size) != point_size) {
        return "Error encoding s";
    }
    return ecdsaCoseSignature;
}

bool verifyEcdsaDigest(int curve_nid, const bytevec& key, const bytevec& digest,
                       const bytevec& signature) {
    const unsigned char* p = (unsigned char*)signature.data();
    auto sig = ECDSA_SIG_Ptr(d2i_ECDSA_SIG(nullptr, &p, signature.size()));
    if (sig.get() == nullptr) {
        return false;
    }

    auto group = EC_GROUP_Ptr(EC_GROUP_new_by_curve_name(curve_nid));
    auto point = EC_POINT_Ptr(EC_POINT_new(group.get()));
    if (EC_POINT_oct2point(group.get(), point.get(), key.data(), key.size(), nullptr) != 1) {
        return false;
    }
    auto ecKey = EC_KEY_Ptr(EC_KEY_new());
    if (ecKey.get() == nullptr) {
        return false;
    }
    if (EC_KEY_set_group(ecKey.get(), group.get()) != 1) {
        return false;
    }
    if (EC_KEY_set_public_key(ecKey.get(), point.get()) != 1) {
        return false;
    }

    int rc = ECDSA_do_verify(digest.data(), digest.size(), sig.get(), ecKey.get());
    if (rc != 1) {
        return false;
    }
    return true;
}

}  // namespace

ErrMsgOr<HmacSha256> generateHmacSha256(const bytevec& key, const bytevec& data) {
    HmacSha256 digest;
    unsigned int outLen;
    uint8_t* out = HMAC(EVP_sha256(),              //
                        key.data(), key.size(),    //
                        data.data(), data.size(),  //
                        digest.data(), &outLen);

    if (out == nullptr || outLen != digest.size()) {
        return "Error generating HMAC";
    }
    return digest;
}

ErrMsgOr<HmacSha256> generateCoseMac0Mac(HmacSha256Function macFunction, const bytevec& externalAad,
                                         const bytevec& payload) {
    auto macStructure = cppbor::Array()
                            .add("MAC0")
                            .add(cppbor::Map().add(ALGORITHM, HMAC_256).canonicalize().encode())
                            .add(externalAad)
                            .add(payload)
                            .encode();

    auto macTag = macFunction(macStructure);
    if (!macTag) {
        return "Error computing public key MAC";
    }

    return *macTag;
}

ErrMsgOr<cppbor::Array> constructCoseMac0(HmacSha256Function macFunction,
                                          const bytevec& externalAad, const bytevec& payload) {
    auto tag = generateCoseMac0Mac(macFunction, externalAad, payload);
    if (!tag) return tag.moveMessage();

    return cppbor::Array()
        .add(cppbor::Map().add(ALGORITHM, HMAC_256).canonicalize().encode())
        .add(cppbor::Map() /* unprotected */)
        .add(payload)
        .add(std::pair(tag->begin(), tag->end()));
}

ErrMsgOr<bytevec /* payload */> verifyAndParseCoseMac0(const cppbor::Item* macItem,
                                                       const bytevec& macKey) {
    auto mac = macItem ? macItem->asArray() : nullptr;
    if (!mac || mac->size() != kCoseMac0EntryCount) {
        return "Invalid COSE_Mac0";
    }

    auto protectedParms = mac->get(kCoseMac0ProtectedParams)->asBstr();
    auto unprotectedParms = mac->get(kCoseMac0UnprotectedParams)->asMap();
    auto payload = mac->get(kCoseMac0Payload)->asBstr();
    auto tag = mac->get(kCoseMac0Tag)->asBstr();
    if (!protectedParms || !unprotectedParms || !payload || !tag) {
        return "Invalid COSE_Mac0 contents";
    }

    auto [protectedMap, _, errMsg] = cppbor::parse(protectedParms);
    if (!protectedMap || !protectedMap->asMap()) {
        return "Invalid Mac0 protected: " + errMsg;
    }
    auto& algo = protectedMap->asMap()->get(ALGORITHM);
    if (!algo || !algo->asInt() || algo->asInt()->value() != HMAC_256) {
        return "Unsupported Mac0 algorithm";
    }

    auto macFunction = [&macKey](const bytevec& input) {
        return generateHmacSha256(macKey, input);
    };
    auto macTag = generateCoseMac0Mac(macFunction, {} /* external_aad */, payload->value());
    if (!macTag) return macTag.moveMessage();

    if (macTag->size() != tag->value().size() ||
        CRYPTO_memcmp(macTag->data(), tag->value().data(), macTag->size()) != 0) {
        return "MAC tag mismatch";
    }

    return payload->value();
}

ErrMsgOr<bytevec> createECDSACoseSign1Signature(const bytevec& key, const bytevec& protectedParams,
                                                const bytevec& payload, const bytevec& aad) {
    bytevec signatureInput = cppbor::Array()
                                 .add("Signature1")  //
                                 .add(protectedParams)
                                 .add(aad)
                                 .add(payload)
                                 .encode();
    auto ecdsaSignature = signP256Digest(key, sha256(signatureInput));
    if (!ecdsaSignature) return ecdsaSignature.moveMessage();

    return ecdsaDerSignatureToCose(kP256AffinePointSize, *ecdsaSignature);
}

ErrMsgOr<bytevec> createCoseSign1Signature(const bytevec& key, const bytevec& protectedParams,
                                           const bytevec& payload, const bytevec& aad) {
    bytevec signatureInput = cppbor::Array()
                                 .add("Signature1")  //
                                 .add(protectedParams)
                                 .add(aad)
                                 .add(payload)
                                 .encode();

    if (key.size() != ED25519_PRIVATE_KEY_LEN) return "Invalid signing key";
    bytevec signature(ED25519_SIGNATURE_LEN);
    if (!ED25519_sign(signature.data(), signatureInput.data(), signatureInput.size(), key.data())) {
        return "Signing failed";
    }

    return signature;
}

ErrMsgOr<cppbor::Array> constructECDSACoseSign1(const bytevec& key, cppbor::Map protectedParams,
                                                const bytevec& payload, const bytevec& aad) {
    bytevec protParms = protectedParams.add(ALGORITHM, ES256).canonicalize().encode();
    auto signature = createECDSACoseSign1Signature(key, protParms, payload, aad);
    if (!signature) return signature.moveMessage();

    return cppbor::Array()
        .add(std::move(protParms))
        .add(cppbor::Map() /* unprotected parameters */)
        .add(std::move(payload))
        .add(std::move(*signature));
}

ErrMsgOr<cppbor::Array> constructCoseSign1(const bytevec& key, cppbor::Map protectedParams,
                                           const bytevec& payload, const bytevec& aad) {
    bytevec protParms = protectedParams.add(ALGORITHM, EDDSA).canonicalize().encode();
    auto signature = createCoseSign1Signature(key, protParms, payload, aad);
    if (!signature) return signature.moveMessage();

    return cppbor::Array()
        .add(std::move(protParms))
        .add(cppbor::Map() /* unprotected parameters */)
        .add(std::move(payload))
        .add(std::move(*signature));
}

ErrMsgOr<cppbor::Array> constructCoseSign1(const bytevec& key, const bytevec& payload,
                                           const bytevec& aad) {
    return constructCoseSign1(key, {} /* protectedParams */, payload, aad);
}

ErrMsgOr<bytevec> verifyAndParseCoseSign1(const cppbor::Array* coseSign1,
                                          const bytevec& signingCoseKey, const bytevec& aad) {
    if (!coseSign1 || coseSign1->size() != kCoseSign1EntryCount) {
        return "Invalid COSE_Sign1";
    }

    const cppbor::Bstr* protectedParams = coseSign1->get(kCoseSign1ProtectedParams)->asBstr();
    const cppbor::Map* unprotectedParams = coseSign1->get(kCoseSign1UnprotectedParams)->asMap();
    const cppbor::Bstr* payload = coseSign1->get(kCoseSign1Payload)->asBstr();

    if (!protectedParams || !unprotectedParams || !payload) {
        return "Missing input parameters";
    }

    auto [parsedProtParams, _, errMsg] = cppbor::parse(protectedParams);
    if (!parsedProtParams) {
        return errMsg + " when parsing protected params.";
    }
    if (!parsedProtParams->asMap()) {
        return "Protected params must be a map";
    }

    auto& algorithm = parsedProtParams->asMap()->get(ALGORITHM);
    if (!algorithm || !algorithm->asInt() ||
        !(algorithm->asInt()->value() == EDDSA || algorithm->asInt()->value() == ES256 ||
          algorithm->asInt()->value() == ES384)) {
        return "Unsupported signature algorithm";
    }

    const cppbor::Bstr* signature = coseSign1->get(kCoseSign1Signature)->asBstr();
    if (!signature || signature->value().empty()) {
        return "Missing signature input";
    }

    bool selfSigned = signingCoseKey.empty();
    bytevec signatureInput =
        cppbor::Array().add("Signature1").add(*protectedParams).add(aad).add(*payload).encode();
    if (algorithm->asInt()->value() == EDDSA) {
        auto key = CoseKey::parseEd25519(selfSigned ? payload->value() : signingCoseKey);
        if (!key || key->getBstrValue(CoseKey::PUBKEY_X)->empty()) {
            return "Bad signing key: " + key.moveMessage();
        }

        if (!ED25519_verify(signatureInput.data(), signatureInput.size(), signature->value().data(),
                            key->getBstrValue(CoseKey::PUBKEY_X)->data())) {
            return "Signature verification failed";
        }
    } else if (algorithm->asInt()->value() == ES256) {
        auto key = CoseKey::parseP256(selfSigned ? payload->value() : signingCoseKey);
        if (!key || key->getBstrValue(CoseKey::PUBKEY_X)->empty() ||
            key->getBstrValue(CoseKey::PUBKEY_Y)->empty()) {
            return "Bad signing key: " + key.moveMessage();
        }
        auto publicKey = key->getEcPublicKey();
        if (!publicKey) return publicKey.moveMessage();

        auto ecdsaDerSignature = ecdsaCoseSignatureToDer(kP256AffinePointSize, signature->value());
        if (!ecdsaDerSignature) return ecdsaDerSignature.moveMessage();

        // convert public key to uncompressed form by prepending 0x04 at begin.
        publicKey->insert(publicKey->begin(), 0x04);

        if (!verifyEcdsaDigest(NID_X9_62_prime256v1, publicKey.moveValue(), sha256(signatureInput),
                               *ecdsaDerSignature)) {
            return "Signature verification failed";
        }
    } else {  // ES384
        auto key = CoseKey::parseP384(selfSigned ? payload->value() : signingCoseKey);
        if (!key || key->getBstrValue(CoseKey::PUBKEY_X)->empty() ||
            key->getBstrValue(CoseKey::PUBKEY_Y)->empty()) {
            return "Bad signing key: " + key.moveMessage();
        }
        auto publicKey = key->getEcPublicKey();
        if (!publicKey) return publicKey.moveMessage();

        auto ecdsaDerSignature = ecdsaCoseSignatureToDer(kP384AffinePointSize, signature->value());
        if (!ecdsaDerSignature) return ecdsaDerSignature.moveMessage();

        // convert public key to uncompressed form by prepending 0x04 at begin.
        publicKey->insert(publicKey->begin(), 0x04);

        if (!verifyEcdsaDigest(NID_secp384r1, publicKey.moveValue(), sha384(signatureInput),
                               *ecdsaDerSignature)) {
            return "Signature verification failed";
        }
    }

    return payload->value();
}

ErrMsgOr<bytevec> createCoseEncryptCiphertext(const bytevec& key, const bytevec& nonce,
                                              const bytevec& protectedParams,
                                              const bytevec& plaintextPayload, const bytevec& aad) {
    auto ciphertext = aesGcmEncrypt(key, nonce,
                                    cppbor::Array()            // Enc strucure as AAD
                                        .add("Encrypt")        // Context
                                        .add(protectedParams)  // Protected
                                        .add(aad)              // External AAD
                                        .encode(),
                                    plaintextPayload);

    if (!ciphertext) return ciphertext.moveMessage();
    return ciphertext.moveValue();
}

ErrMsgOr<cppbor::Array> constructCoseEncrypt(const bytevec& key, const bytevec& nonce,
                                             const bytevec& plaintextPayload, const bytevec& aad,
                                             cppbor::Array recipients) {
    auto encryptProtectedHeader = cppbor::Map()  //
                                      .add(ALGORITHM, AES_GCM_256)
                                      .canonicalize()
                                      .encode();

    auto ciphertext =
        createCoseEncryptCiphertext(key, nonce, encryptProtectedHeader, plaintextPayload, aad);
    if (!ciphertext) return ciphertext.moveMessage();

    return cppbor::Array()
        .add(encryptProtectedHeader)                       // Protected
        .add(cppbor::Map().add(IV, nonce).canonicalize())  // Unprotected
        .add(*ciphertext)                                  // Payload
        .add(std::move(recipients));
}

ErrMsgOr<std::pair<bytevec /* pubkey */, bytevec /* key ID */>>
getSenderPubKeyFromCoseEncrypt(const cppbor::Item* coseEncrypt) {
    if (!coseEncrypt || !coseEncrypt->asArray() ||
        coseEncrypt->asArray()->size() != kCoseEncryptEntryCount) {
        return "Invalid COSE_Encrypt";
    }

    auto& recipients = coseEncrypt->asArray()->get(kCoseEncryptRecipients);
    if (!recipients || !recipients->asArray() || recipients->asArray()->size() != 1) {
        return "Invalid recipients list";
    }

    auto& recipient = recipients->asArray()->get(0);
    if (!recipient || !recipient->asArray() || recipient->asArray()->size() != 3) {
        return "Invalid COSE_recipient";
    }

    auto& ciphertext = recipient->asArray()->get(2);
    if (!ciphertext->asSimple() || !ciphertext->asSimple()->asNull()) {
        return "Unexpected value in recipients ciphertext field " +
               cppbor::prettyPrint(ciphertext.get());
    }

    auto& protParms = recipient->asArray()->get(0);
    if (!protParms || !protParms->asBstr()) return "Invalid protected params";
    auto [parsedProtParms, _, errMsg] = cppbor::parse(protParms->asBstr());
    if (!parsedProtParms) return "Failed to parse protected params: " + errMsg;
    if (!parsedProtParms->asMap()) return "Invalid protected params";

    auto& algorithm = parsedProtParms->asMap()->get(ALGORITHM);
    if (!algorithm || !algorithm->asInt() || algorithm->asInt()->value() != ECDH_ES_HKDF_256) {
        return "Invalid algorithm";
    }

    auto& unprotParms = recipient->asArray()->get(1);
    if (!unprotParms || !unprotParms->asMap()) return "Invalid unprotected params";

    auto& senderCoseKey = unprotParms->asMap()->get(COSE_KEY);
    if (!senderCoseKey || !senderCoseKey->asMap()) return "Invalid sender COSE_Key";

    auto& keyType = senderCoseKey->asMap()->get(CoseKey::KEY_TYPE);
    if (!keyType || !keyType->asInt() ||
        (keyType->asInt()->value() != OCTET_KEY_PAIR && keyType->asInt()->value() != EC2)) {
        return "Invalid key type";
    }

    auto& curve = senderCoseKey->asMap()->get(CoseKey::CURVE);
    if (!curve || !curve->asInt() ||
        (keyType->asInt()->value() == OCTET_KEY_PAIR && curve->asInt()->value() != X25519) ||
        (keyType->asInt()->value() == EC2 && curve->asInt()->value() != P256)) {
        return "Unsupported curve";
    }

    bytevec publicKey;
    if (keyType->asInt()->value() == EC2) {
        auto& pubX = senderCoseKey->asMap()->get(CoseKey::PUBKEY_X);
        if (!pubX || !pubX->asBstr() || pubX->asBstr()->value().size() != kP256AffinePointSize) {
            return "Invalid EC public key";
        }
        auto& pubY = senderCoseKey->asMap()->get(CoseKey::PUBKEY_Y);
        if (!pubY || !pubY->asBstr() || pubY->asBstr()->value().size() != kP256AffinePointSize) {
            return "Invalid EC public key";
        }
        auto key = CoseKey::getEcPublicKey(pubX->asBstr()->value(), pubY->asBstr()->value());
        if (!key) return key.moveMessage();
        publicKey = key.moveValue();
    } else {
        auto& pubkey = senderCoseKey->asMap()->get(CoseKey::PUBKEY_X);
        if (!pubkey || !pubkey->asBstr() ||
            pubkey->asBstr()->value().size() != X25519_PUBLIC_VALUE_LEN) {
            return "Invalid X25519 public key";
        }
        publicKey = pubkey->asBstr()->value();
    }

    auto& key_id = unprotParms->asMap()->get(KEY_ID);
    if (key_id && key_id->asBstr()) {
        return std::make_pair(publicKey, key_id->asBstr()->value());
    }

    // If no key ID, just return an empty vector.
    return std::make_pair(publicKey, bytevec{});
}

ErrMsgOr<bytevec> decryptCoseEncrypt(const bytevec& key, const cppbor::Item* coseEncrypt,
                                     const bytevec& external_aad) {
    if (!coseEncrypt || !coseEncrypt->asArray() ||
        coseEncrypt->asArray()->size() != kCoseEncryptEntryCount) {
        return "Invalid COSE_Encrypt";
    }

    auto& protParms = coseEncrypt->asArray()->get(kCoseEncryptProtectedParams);
    auto& unprotParms = coseEncrypt->asArray()->get(kCoseEncryptUnprotectedParams);
    auto& ciphertext = coseEncrypt->asArray()->get(kCoseEncryptPayload);
    auto& recipients = coseEncrypt->asArray()->get(kCoseEncryptRecipients);

    if (!protParms || !protParms->asBstr() || !unprotParms || !ciphertext || !recipients) {
        return "Invalid COSE_Encrypt";
    }

    auto [parsedProtParams, _, errMsg] = cppbor::parse(protParms->asBstr()->value());
    if (!parsedProtParams) {
        return errMsg + " when parsing protected params.";
    }
    if (!parsedProtParams->asMap()) {
        return "Protected params must be a map";
    }

    auto& algorithm = parsedProtParams->asMap()->get(ALGORITHM);
    if (!algorithm || !algorithm->asInt() || algorithm->asInt()->value() != AES_GCM_256) {
        return "Unsupported encryption algorithm";
    }

    if (!unprotParms->asMap() || unprotParms->asMap()->size() != 1) {
        return "Invalid unprotected params";
    }

    auto& nonce = unprotParms->asMap()->get(IV);
    if (!nonce || !nonce->asBstr() || nonce->asBstr()->value().size() != kAesGcmNonceLength) {
        return "Invalid nonce";
    }

    if (!ciphertext->asBstr()) return "Invalid ciphertext";

    auto aad = cppbor::Array()                         // Enc strucure as AAD
                   .add("Encrypt")                     // Context
                   .add(protParms->asBstr()->value())  // Protected
                   .add(external_aad)                  // External AAD
                   .encode();

    return aesGcmDecrypt(key, nonce->asBstr()->value(), aad, ciphertext->asBstr()->value());
}

ErrMsgOr<bytevec> consructKdfContext(const bytevec& pubKeyA, const bytevec& privKeyA,
                                     const bytevec& pubKeyB, bool senderIsA) {
    if (privKeyA.empty() || pubKeyA.empty() || pubKeyB.empty()) {
        return "Missing input key parameters";
    }

    bytevec kdfContext = cppbor::Array()
                             .add(AES_GCM_256)
                             .add(cppbor::Array()  // Sender Info
                                      .add(cppbor::Bstr("client"))
                                      .add(bytevec{} /* nonce */)
                                      .add(senderIsA ? pubKeyA : pubKeyB))
                             .add(cppbor::Array()  // Recipient Info
                                      .add(cppbor::Bstr("server"))
                                      .add(bytevec{} /* nonce */)
                                      .add(senderIsA ? pubKeyB : pubKeyA))
                             .add(cppbor::Array()               // SuppPubInfo
                                      .add(kAesGcmKeySizeBits)  // output key length
                                      .add(bytevec{}))          // protected
                             .encode();
    return kdfContext;
}

ErrMsgOr<bytevec> ECDH_HKDF_DeriveKey(const bytevec& pubKeyA, const bytevec& privKeyA,
                                      const bytevec& pubKeyB, bool senderIsA) {
    if (privKeyA.empty() || pubKeyA.empty() || pubKeyB.empty()) {
        return "Missing input key parameters";
    }

    // convert public key to uncompressed form by prepending 0x04 at begin
    bytevec publicKey;
    publicKey.insert(publicKey.begin(), 0x04);
    publicKey.insert(publicKey.end(), pubKeyB.begin(), pubKeyB.end());
    auto rawSharedKey = ecdh(publicKey, privKeyA);
    if (!rawSharedKey) return rawSharedKey.moveMessage();

    auto kdfContext = consructKdfContext(pubKeyA, privKeyA, pubKeyB, senderIsA);
    if (!kdfContext) return kdfContext.moveMessage();

    bytevec retval(SHA256_DIGEST_LENGTH);
    bytevec salt{};
    if (!HKDF(retval.data(), retval.size(),                //
              EVP_sha256(),                                //
              rawSharedKey->data(), rawSharedKey->size(),  //
              salt.data(), salt.size(),                    //
              kdfContext->data(), kdfContext->size())) {
        return "ECDH HKDF failed";
    }

    return retval;
}

ErrMsgOr<bytevec> x25519_HKDF_DeriveKey(const bytevec& pubKeyA, const bytevec& privKeyA,
                                        const bytevec& pubKeyB, bool senderIsA) {
    if (privKeyA.empty() || pubKeyA.empty() || pubKeyB.empty()) {
        return "Missing input key parameters";
    }

    bytevec rawSharedKey(X25519_SHARED_KEY_LEN);
    if (!::X25519(rawSharedKey.data(), privKeyA.data(), pubKeyB.data())) {
        return "ECDH operation failed";
    }

    auto kdfContext = consructKdfContext(pubKeyA, privKeyA, pubKeyB, senderIsA);
    if (!kdfContext) return kdfContext.moveMessage();

    bytevec retval(SHA256_DIGEST_LENGTH);
    bytevec salt{};
    if (!HKDF(retval.data(), retval.size(),              //
              EVP_sha256(),                              //
              rawSharedKey.data(), rawSharedKey.size(),  //
              salt.data(), salt.size(),                  //
              kdfContext->data(), kdfContext->size())) {
        return "ECDH HKDF failed";
    }

    return retval;
}

ErrMsgOr<bytevec> aesGcmEncrypt(const bytevec& key, const bytevec& nonce, const bytevec& aad,
                                const bytevec& plaintext) {
    auto ctx = aesGcmInitAndProcessAad(key, nonce, aad, true /* encrypt */);
    if (!ctx) return ctx.moveMessage();

    bytevec ciphertext(plaintext.size() + kAesGcmTagSize);
    int outlen;
    if (!EVP_CipherUpdate(ctx->get(), ciphertext.data(), &outlen, plaintext.data(),
                          plaintext.size())) {
        return "Failed to encrypt plaintext";
    }
    assert(plaintext.size() == static_cast<uint64_t>(outlen));

    if (!EVP_CipherFinal_ex(ctx->get(), ciphertext.data() + outlen, &outlen)) {
        return "Failed to finalize encryption";
    }
    assert(outlen == 0);

    if (!EVP_CIPHER_CTX_ctrl(ctx->get(), EVP_CTRL_GCM_GET_TAG, kAesGcmTagSize,
                             ciphertext.data() + plaintext.size())) {
        return "Failed to retrieve tag";
    }

    return ciphertext;
}

ErrMsgOr<bytevec> aesGcmDecrypt(const bytevec& key, const bytevec& nonce, const bytevec& aad,
                                const bytevec& ciphertextWithTag) {
    auto ctx = aesGcmInitAndProcessAad(key, nonce, aad, false /* encrypt */);
    if (!ctx) return ctx.moveMessage();

    if (ciphertextWithTag.size() < kAesGcmTagSize) return "Missing tag";

    bytevec plaintext(ciphertextWithTag.size() - kAesGcmTagSize);
    int outlen;
    if (!EVP_CipherUpdate(ctx->get(), plaintext.data(), &outlen, ciphertextWithTag.data(),
                          ciphertextWithTag.size() - kAesGcmTagSize)) {
        return "Failed to decrypt plaintext";
    }
    assert(plaintext.size() == static_cast<uint64_t>(outlen));

    bytevec tag(ciphertextWithTag.end() - kAesGcmTagSize, ciphertextWithTag.end());
    if (!EVP_CIPHER_CTX_ctrl(ctx->get(), EVP_CTRL_GCM_SET_TAG, kAesGcmTagSize, tag.data())) {
        return "Failed to set tag: " + std::to_string(ERR_peek_last_error());
    }

    if (!EVP_CipherFinal_ex(ctx->get(), nullptr, &outlen)) {
        return "Failed to finalize encryption";
    }
    assert(outlen == 0);

    return plaintext;
}

bytevec sha256(const bytevec& data) {
    bytevec ret(SHA256_DIGEST_LENGTH);
    SHA256_CTX ctx;
    SHA256_Init(&ctx);
    SHA256_Update(&ctx, data.data(), data.size());
    SHA256_Final((unsigned char*)ret.data(), &ctx);
    return ret;
}

bytevec sha384(const bytevec& data) {
    bytevec ret(SHA384_DIGEST_LENGTH);
    SHA512_CTX ctx;
    SHA384_Init(&ctx);
    SHA384_Update(&ctx, data.data(), data.size());
    SHA384_Final((unsigned char*)ret.data(), &ctx);
    return ret;
}

}  // namespace cppcose
