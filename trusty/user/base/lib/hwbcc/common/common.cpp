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

#define TLOG_TAG "hwbcc-common"

#include <cppbor.h>
#include <cppbor_parse.h>
#include <lib/hwbcc/common/common.h>
#include <trusty_unittest.h>
#include <array>

#define CHECK(statement)                                        \
    do {                                                        \
        if (!(statement)) {                                     \
            TLOGE("(" STRINGIFY(statement) ") check failed\n"); \
            return false;                                       \
        }                                                       \
    } while (0);

#define CHECK_NOT_NULL(val) CHECK(val != NULL)
#define CHECK_EQ(val1, val2) CHECK(val1 == val2)

static bool validate_pub_key_ed25519(const cppbor::Map* cose_key,
                                     PubKey* out_key) {
    /* This is what we expect. Note that field 4 is different in COSE spec.
     *  PubKeyEd25519 = {        // COSE_Key
     *      1 : 1,               // Key type : octet key pair
     *      3 : AlgorithmEdDSA,  // Algorithm : EdDSA
     *      4 : 2,               // Ops: Verify
     *      -1 : 6,              // Curve : Ed25519
     *      -2 : bstr            // X coordinate, little-endian
     *  }
     */
    const cppbor::Int* key_type = cose_key->get(1)->asInt();
    const cppbor::Int* algorithm = cose_key->get(3)->asInt();
    const cppbor::Int* curve = cose_key->get(-1)->asInt();
    const cppbor::Bstr* key = cose_key->get(-2)->asBstr();

    /*
     * TODO(b/201344393): There are inconsistencies in "ops" field depending on
     * implementation.
     *
     * const cppbor::Int* ops = cose_key->get(4)->asInt();
     * CHECK_NOT_NULL(ops);
     * CHECK_EQ(ops->value(), 2);
     */

    CHECK(cose_key->size() == 5);

    CHECK_NOT_NULL(key_type);
    CHECK_NOT_NULL(algorithm);
    CHECK_NOT_NULL(curve);
    CHECK_NOT_NULL(key);

    CHECK_EQ(key_type->value(), 1);
    CHECK_EQ(algorithm->value(), -8);
    CHECK_EQ(curve->value(), 6);
    CHECK_EQ(key->value().size(), ED25519_PUBLIC_KEY_LEN);

    std::copy(key->value().begin(), key->value().end(), out_key->begin());
    return true;
}

static bool validate_protected_params(const cppbor::Bstr* protected_params) {
    auto [parsed_params, _, err_msg] = cppbor::parse(protected_params);
    CHECK_NOT_NULL(parsed_params);

    const cppbor::Map* params = parsed_params->asMap();
    CHECK_NOT_NULL(params);

    CHECK_EQ(params->size(), 1);

    const cppbor::Int* algorithm = params->get(1)->asInt();
    CHECK_EQ(algorithm->value(), -8);

    return true;
}

static bool validate_subject_key(const cppbor::Bstr* subject_key,
                                 PubKey* out_key) {
    auto [parsed_subject_key, _, err_msg] = cppbor::parse(subject_key);
    CHECK_NOT_NULL(parsed_subject_key);

    const cppbor::Map* cose_key = parsed_subject_key->asMap();
    CHECK_NOT_NULL(cose_key);

    CHECK(validate_pub_key_ed25519(cose_key, out_key));

    return true;
}

static bool validate_bcc_payload(const cppbor::Bstr* bcc_payload,
                                 PubKey* out_key) {
    /* This is what we expect:
     *  BccPayload = {                          // CWT
     *      1 : tstr,                           // Issuer
     *      2 : tstr,                           // Subject
     *      // See the Open Profile for DICE for details on these fields.
     *      ? -4670545 : bstr,                  // Code Hash
     *      ? -4670546 : bstr,                  // Code Descriptor
     *      ? -4670547 : bstr,                  // Configuration Hash
     *      ? -4670548 : bstr .cbor {           // Configuration Descriptor
     *          ? -70002 : tstr,                // Component name
     *          ? -70003 : int,                 // Firmware version
     *          ? -70004 : null,                // Resettable
     *      },
     *      ? -4670549 : bstr,                  // Authority Hash
     *      ? -4670550 : bstr,                  // Authority Descriptor
     *      ? -4670551 : bstr,                  // Mode
     *      -4670552 : bstr .cbor PubKeyEd25519 // Subject Public Key
     *      -4670553 : bstr                     // Key Usage
     *  }
     */
    auto [parsed_payload, _, err_msg] = cppbor::parse(bcc_payload);
    CHECK_NOT_NULL(parsed_payload);

    const cppbor::Map* payload = parsed_payload->asMap();
    CHECK_NOT_NULL(payload);

    CHECK(payload->size() >= 4);

    const cppbor::Tstr* issuer = payload->get(1)->asTstr();
    const cppbor::Tstr* subject = payload->get(2)->asTstr();
    const cppbor::Bstr* subject_key = payload->get(-4670552)->asBstr();
    const cppbor::Bstr* key_usage = payload->get(-4670553)->asBstr();

    CHECK_NOT_NULL(issuer);
    CHECK_NOT_NULL(subject);
    CHECK_NOT_NULL(subject_key);
    CHECK_NOT_NULL(key_usage);

    CHECK(validate_subject_key(subject_key, out_key));

    return true;
}

static bool validate_bcc_entry(const cppbor::Array* bcc_entry,
                               const PubKey& prev_key,
                               PubKey* out_key) {
    /* This is what we expect:
     *  BccEntry = [                 // COSE_Sign1 (untagged)
     *      protected : bstr .cbor {
     *          1 : AlgorithmEdDSA,  // Algorithm
     *      },
     *      unprotected: {},
     *      payload: bstr .cbor BccPayload,
     *      signature: bstr .cbor PureEd25519(SigningKey,
     *                                        bstr .cbor BccEntryInput)
     *  ]
     *
     *  BccEntryInput = [
     *      context: "Signature1",
     *      protected: bstr .cbor {
     *          1 : AlgorithmEdDSA,  // Algorithm
     *      },
     *      external_aad: bstr .size 0,
     *      payload: bstr .cbor BccPayload
     *  ]
     */
    const cppbor::Bstr* protected_params = bcc_entry->get(0)->asBstr();
    const cppbor::Map* unprotected_params = bcc_entry->get(1)->asMap();
    const cppbor::Bstr* payload = bcc_entry->get(2)->asBstr();
    const cppbor::Bstr* signature = bcc_entry->get(3)->asBstr();

    CHECK_EQ(bcc_entry->size(), 4);

    CHECK_NOT_NULL(protected_params);
    CHECK_NOT_NULL(unprotected_params);
    CHECK_NOT_NULL(payload);
    CHECK_NOT_NULL(signature);

    CHECK(validate_protected_params(protected_params));
    CHECK_EQ(unprotected_params->size(), 0);
    CHECK(validate_bcc_payload(payload, out_key));

    std::vector<uint8_t> signature_input = cppbor::Array()
                                                   .add("Signature1")
                                                   .add(*protected_params)
                                                   .add(cppbor::Bstr())
                                                   .add(*payload)
                                                   .encode();

    int rc = ED25519_verify(signature_input.data(), signature_input.size(),
                            signature->value().data(), prev_key.data());
    CHECK_EQ(rc, 1);

    return true;
}

bool validate_bcc(const uint8_t* bcc,
                  size_t bcc_size,
                  uint8_t dk_pub_key[ED25519_PUBLIC_KEY_LEN],
                  uint8_t km_pub_key[ED25519_PUBLIC_KEY_LEN]) {
    std::vector<PubKey> keys;

    if (!validate_bcc_impl(bcc, bcc_size, &keys)) {
        TLOGE("BCC validation failed");
        return false;
    }

    if (keys.size() != 2) {
        TLOGE("BCC validation key vector invalid");
        return false;
    }

    std::copy(keys[0].begin(), keys[0].end(), dk_pub_key);
    std::copy(keys[1].begin(), keys[1].end(), km_pub_key);

    return true;
}

/* TODO: Also validate non-degenerate BCC case */
bool validate_bcc_impl(const uint8_t* bcc,
                       size_t bcc_size,
                       std::vector<PubKey>* keys) {
    /* This is what we expect:
     *  Bcc = [
     *      PubKeyEd25519, // DK_pub
     *      + BccEntry,    // Root -> leaf (KM_pub)
     *  ]
     */
    auto [parsed_bcc, _, err_msg] = cppbor::parse(bcc, bcc_size);
    CHECK_NOT_NULL(parsed_bcc);

    const cppbor::Array* bcc_array = parsed_bcc->asArray();
    CHECK_NOT_NULL(bcc_array);

    CHECK_EQ(bcc_array->size(), 2);

    const cppbor::Map* dk_pub = bcc_array->get(0)->asMap();
    const cppbor::Array* bcc_entry = bcc_array->get(1)->asArray();

    CHECK_NOT_NULL(dk_pub);
    CHECK_NOT_NULL(bcc_entry);

    PubKey dk_pub_key;
    PubKey km_pub_key;

    CHECK(validate_pub_key_ed25519(dk_pub, &dk_pub_key));
    CHECK(validate_bcc_entry(bcc_entry, dk_pub_key, &km_pub_key));

    keys->push_back(dk_pub_key);
    keys->push_back(km_pub_key);

    return true;
}

bool validate_bcc_handover(const uint8_t* bcc_handover,
                           size_t bcc_handover_size,
                           uint8_t next_cdi_attest[DICE_CDI_SIZE],
                           uint8_t next_cdi_seal[DICE_CDI_SIZE]) {
    CDI tmp_cdi_attest;
    CDI tmp_cdi_seal;

    if (!validate_bcc_handover_impl(bcc_handover, bcc_handover_size,
                                    &tmp_cdi_attest, &tmp_cdi_seal)) {
        return false;
    }

    std::copy(tmp_cdi_attest.begin(), tmp_cdi_attest.end(),
              &next_cdi_attest[0]);
    std::copy(tmp_cdi_seal.begin(), tmp_cdi_seal.end(), &next_cdi_seal[0]);

    return true;
}

bool validate_bcc_handover_impl(const uint8_t* bcc_handover,
                                size_t bcc_handover_size,
                                CDI* next_cdi_attest,
                                CDI* next_cdi_seal) {
    /**
     * This is what we expect:
     * BccHandover = {
     *      1 : bstr .size 32,	// CDI_Attest
     *      2 : bstr .size 32,	// CDI_Seal
     *      ? 3 : Bcc,          // Cert_Chain
     * }
     */
    auto [parsed_bcc_handover, _, err_msg] =
            cppbor::parse(bcc_handover, bcc_handover_size);
    CHECK_NOT_NULL(parsed_bcc_handover);

    const cppbor::Map* handover_map = parsed_bcc_handover->asMap();
    std::vector<uint8_t> cdi_attest = handover_map->get(1)->asBstr()->value();
    std::vector<uint8_t> cdi_seal = handover_map->get(2)->asBstr()->value();

    std::copy(cdi_attest.begin(), cdi_attest.end(), next_cdi_attest->begin());
    std::copy(cdi_seal.begin(), cdi_seal.end(), next_cdi_seal->begin());

    return true;
}
