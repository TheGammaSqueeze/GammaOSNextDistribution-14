/*
 * Copyright 2016 The Android Open Source Project
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

#pragma once

#include <hardware/keymaster_defs.h>

#include <keymaster/authorization_set.h>
#include <keymaster/km_version.h>

#include <cppbor.h>
#include <openssl/asn1t.h>

#include <vector>

#define remove_type_mask(tag) ((tag)&0x0FFFFFFF)

namespace keymaster {

class AttestationContext;

constexpr KmVersion kCurrentKmVersion = KmVersion::KEYMASTER_4_1;

// Size (in bytes) of generated UNIQUE_ID values.
constexpr int UNIQUE_ID_SIZE = 16;

constexpr int EAT_CLAIM_PRIVATE_BASE = -80000;
constexpr int EAT_CLAIM_PRIVATE_NON_KM_BASE = EAT_CLAIM_PRIVATE_BASE - 2000;

constexpr int64_t convert_to_eat_claim(keymaster_tag_t tag) {
    return EAT_CLAIM_PRIVATE_BASE - remove_type_mask(tag);
}

struct stack_st_ASN1_TYPE_Delete {
    void operator()(stack_st_ASN1_TYPE* p) { sk_ASN1_TYPE_free(p); }
};

struct ASN1_STRING_Delete {
    void operator()(ASN1_STRING* p) { ASN1_STRING_free(p); }
};

struct ASN1_TYPE_Delete {
    void operator()(ASN1_TYPE* p) { ASN1_TYPE_free(p); }
};

#define ASN1_INTEGER_SET STACK_OF(ASN1_INTEGER)

typedef struct km_root_of_trust {
    ASN1_OCTET_STRING* verified_boot_key;
    ASN1_BOOLEAN device_locked;
    ASN1_ENUMERATED* verified_boot_state;
    ASN1_OCTET_STRING* verified_boot_hash;
} KM_ROOT_OF_TRUST;

ASN1_SEQUENCE(KM_ROOT_OF_TRUST) = {
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, verified_boot_key, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, device_locked, ASN1_BOOLEAN),
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, verified_boot_state, ASN1_ENUMERATED),
    ASN1_SIMPLE(KM_ROOT_OF_TRUST, verified_boot_hash, ASN1_OCTET_STRING),
} ASN1_SEQUENCE_END(KM_ROOT_OF_TRUST);
DECLARE_ASN1_FUNCTIONS(KM_ROOT_OF_TRUST);

// Fields ordered in tag order.
typedef struct km_auth_list {
    ASN1_INTEGER_SET* purpose;
    ASN1_INTEGER* algorithm;
    ASN1_INTEGER* key_size;
    ASN1_INTEGER_SET* block_mode;
    ASN1_INTEGER_SET* digest;
    ASN1_INTEGER_SET* padding;
    ASN1_NULL* caller_nonce;
    ASN1_INTEGER* min_mac_length;
    ASN1_INTEGER_SET* kdf;
    ASN1_INTEGER* ec_curve;
    ASN1_INTEGER* rsa_public_exponent;
    ASN1_INTEGER_SET* mgf_digest;
    ASN1_NULL* rollback_resistance;
    ASN1_NULL* early_boot_only;
    ASN1_INTEGER* active_date_time;
    ASN1_INTEGER* origination_expire_date_time;
    ASN1_INTEGER* usage_expire_date_time;
    ASN1_INTEGER* usage_count_limit;
    ASN1_NULL* no_auth_required;
    ASN1_INTEGER* user_auth_type;
    ASN1_INTEGER* auth_timeout;
    ASN1_NULL* allow_while_on_body;
    ASN1_NULL* trusted_user_presence_required;
    ASN1_NULL* trusted_confirmation_required;
    ASN1_NULL* unlocked_device_required;
    ASN1_NULL* all_applications;
    ASN1_OCTET_STRING* application_id;
    ASN1_INTEGER* creation_date_time;
    ASN1_INTEGER* origin;
    ASN1_NULL* rollback_resistant;
    KM_ROOT_OF_TRUST* root_of_trust;
    ASN1_INTEGER* os_version;
    ASN1_INTEGER* os_patchlevel;
    ASN1_OCTET_STRING* attestation_application_id;
    ASN1_OCTET_STRING* attestation_id_brand;
    ASN1_OCTET_STRING* attestation_id_device;
    ASN1_OCTET_STRING* attestation_id_product;
    ASN1_OCTET_STRING* attestation_id_serial;
    ASN1_OCTET_STRING* attestation_id_imei;
    ASN1_OCTET_STRING* attestation_id_meid;
    ASN1_OCTET_STRING* attestation_id_manufacturer;
    ASN1_OCTET_STRING* attestation_id_model;
    ASN1_INTEGER* vendor_patchlevel;
    ASN1_INTEGER* boot_patch_level;
    ASN1_NULL* device_unique_attestation;
    ASN1_NULL* identity_credential_key;
    ASN1_OCTET_STRING* attestation_id_second_imei;
} KM_AUTH_LIST;

ASN1_SEQUENCE(KM_AUTH_LIST) = {
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, purpose, ASN1_INTEGER, TAG_PURPOSE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, algorithm, ASN1_INTEGER, TAG_ALGORITHM.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, key_size, ASN1_INTEGER, TAG_KEY_SIZE.masked_tag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, block_mode, ASN1_INTEGER, TAG_BLOCK_MODE.masked_tag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, digest, ASN1_INTEGER, TAG_DIGEST.masked_tag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, padding, ASN1_INTEGER, TAG_PADDING.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, caller_nonce, ASN1_NULL, TAG_CALLER_NONCE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, min_mac_length, ASN1_INTEGER, TAG_MIN_MAC_LENGTH.masked_tag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, kdf, ASN1_INTEGER, TAG_KDF.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, ec_curve, ASN1_INTEGER, TAG_EC_CURVE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, rsa_public_exponent, ASN1_INTEGER,
                 TAG_RSA_PUBLIC_EXPONENT.masked_tag()),
    ASN1_EXP_SET_OF_OPT(KM_AUTH_LIST, mgf_digest, ASN1_INTEGER,
                        TAG_RSA_OAEP_MGF_DIGEST.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, rollback_resistance, ASN1_NULL,
                 TAG_ROLLBACK_RESISTANCE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, early_boot_only, ASN1_NULL, TAG_EARLY_BOOT_ONLY.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, active_date_time, ASN1_INTEGER, TAG_ACTIVE_DATETIME.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, origination_expire_date_time, ASN1_INTEGER,
                 TAG_ORIGINATION_EXPIRE_DATETIME.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, usage_expire_date_time, ASN1_INTEGER,
                 TAG_USAGE_EXPIRE_DATETIME.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, usage_count_limit, ASN1_INTEGER, TAG_USAGE_COUNT_LIMIT.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, no_auth_required, ASN1_NULL, TAG_NO_AUTH_REQUIRED.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, user_auth_type, ASN1_INTEGER, TAG_USER_AUTH_TYPE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, auth_timeout, ASN1_INTEGER, TAG_AUTH_TIMEOUT.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, allow_while_on_body, ASN1_NULL,
                 TAG_ALLOW_WHILE_ON_BODY.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, trusted_user_presence_required, ASN1_NULL,
                 TAG_TRUSTED_USER_PRESENCE_REQUIRED.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, trusted_confirmation_required, ASN1_NULL,
                 TAG_TRUSTED_CONFIRMATION_REQUIRED.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, unlocked_device_required, ASN1_NULL,
                 TAG_UNLOCKED_DEVICE_REQUIRED.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, all_applications, ASN1_NULL, TAG_ALL_APPLICATIONS.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, application_id, ASN1_OCTET_STRING, TAG_APPLICATION_ID.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, creation_date_time, ASN1_INTEGER,
                 TAG_CREATION_DATETIME.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, origin, ASN1_INTEGER, TAG_ORIGIN.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, rollback_resistant, ASN1_NULL, TAG_ROLLBACK_RESISTANT.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, root_of_trust, KM_ROOT_OF_TRUST, TAG_ROOT_OF_TRUST.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, os_version, ASN1_INTEGER, TAG_OS_VERSION.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, os_patchlevel, ASN1_INTEGER, TAG_OS_PATCHLEVEL.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_application_id, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_APPLICATION_ID.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_brand, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_BRAND.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_device, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_DEVICE.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_product, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_PRODUCT.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_serial, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_SERIAL.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_imei, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_IMEI.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_meid, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_MEID.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_manufacturer, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_MANUFACTURER.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_model, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_MODEL.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, vendor_patchlevel, ASN1_INTEGER, TAG_VENDOR_PATCHLEVEL.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, boot_patch_level, ASN1_INTEGER, TAG_BOOT_PATCHLEVEL.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, device_unique_attestation, ASN1_NULL,
                 TAG_DEVICE_UNIQUE_ATTESTATION.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, identity_credential_key, ASN1_NULL,
                 TAG_IDENTITY_CREDENTIAL_KEY.masked_tag()),
    ASN1_EXP_OPT(KM_AUTH_LIST, attestation_id_second_imei, ASN1_OCTET_STRING,
                 TAG_ATTESTATION_ID_SECOND_IMEI.masked_tag()),
} ASN1_SEQUENCE_END(KM_AUTH_LIST);
DECLARE_ASN1_FUNCTIONS(KM_AUTH_LIST);

typedef struct km_key_description {
    ASN1_INTEGER* attestation_version;
    ASN1_ENUMERATED* attestation_security_level;
    ASN1_INTEGER* keymaster_version;
    ASN1_ENUMERATED* keymaster_security_level;
    ASN1_OCTET_STRING* attestation_challenge;
    KM_AUTH_LIST* software_enforced;
    KM_AUTH_LIST* tee_enforced;
    ASN1_OCTET_STRING* unique_id;
} KM_KEY_DESCRIPTION;

ASN1_SEQUENCE(KM_KEY_DESCRIPTION) = {
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_version, ASN1_INTEGER),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_security_level, ASN1_ENUMERATED),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, keymaster_version, ASN1_INTEGER),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, keymaster_security_level, ASN1_ENUMERATED),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, attestation_challenge, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, unique_id, ASN1_OCTET_STRING),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, software_enforced, KM_AUTH_LIST),
    ASN1_SIMPLE(KM_KEY_DESCRIPTION, tee_enforced, KM_AUTH_LIST),
} ASN1_SEQUENCE_END(KM_KEY_DESCRIPTION);
DECLARE_ASN1_FUNCTIONS(KM_KEY_DESCRIPTION);

enum class EatClaim {

    // Official CWT claims, as defined in https://www.iana.org/assignments/cwt/cwt.xhtml

    IAT = 6,
    CTI = 7,

    // Claims defined in
    // https://github.com/laurencelundblade/ctoken/blob/master/inc/ctoken_eat_labels.h, by the
    // author of the EAT standard, and in the ARM PSA. They should be considered stable at least
    // until the standard is officially published.

    UEID = -75009,
    NONCE = -75008,
    SECURITY_LEVEL = -76002,
    BOOT_STATE = -76003,
    SUBMODS = -76000,

    // Claims specific to Android, and not part of the official EAT standard.

    PURPOSE = convert_to_eat_claim(KM_TAG_PURPOSE),
    ALGORITHM = convert_to_eat_claim(KM_TAG_ALGORITHM),
    KEY_SIZE = convert_to_eat_claim(KM_TAG_KEY_SIZE),
    BLOCK_MODE = convert_to_eat_claim(KM_TAG_BLOCK_MODE),
    DIGEST = convert_to_eat_claim(KM_TAG_DIGEST),
    PADDING = convert_to_eat_claim(KM_TAG_PADDING),
    // TODO: Check if CALLER_NONCE is needed (see go/keymint-eat)
    CALLER_NONCE = convert_to_eat_claim(KM_TAG_CALLER_NONCE),
    MIN_MAC_LENGTH = convert_to_eat_claim(KM_TAG_MIN_MAC_LENGTH),
    EC_CURVE = convert_to_eat_claim(KM_TAG_EC_CURVE),
    RSA_PUBLIC_EXPONENT = convert_to_eat_claim(KM_TAG_RSA_PUBLIC_EXPONENT),
    EARLY_BOOT_ONLY = convert_to_eat_claim(KM_TAG_EARLY_BOOT_ONLY),
    ACTIVE_DATETIME = convert_to_eat_claim(KM_TAG_ACTIVE_DATETIME),
    ORIGINATION_EXPIRE_DATETIME = convert_to_eat_claim(KM_TAG_ORIGINATION_EXPIRE_DATETIME),
    USAGE_EXPIRE_DATETIME = convert_to_eat_claim(KM_TAG_USAGE_EXPIRE_DATETIME),
    NO_AUTH_REQUIRED = convert_to_eat_claim(KM_TAG_NO_AUTH_REQUIRED),
    USER_AUTH_TYPE = convert_to_eat_claim(KM_TAG_USER_AUTH_TYPE),
    AUTH_TIMEOUT = convert_to_eat_claim(KM_TAG_AUTH_TIMEOUT),
    ALLOW_WHILE_ON_BODY = convert_to_eat_claim(KM_TAG_ALLOW_WHILE_ON_BODY),
    TRUSTED_USER_PRESENCE_REQUIRED = convert_to_eat_claim(KM_TAG_TRUSTED_USER_PRESENCE_REQUIRED),
    TRUSTED_CONFIRMATION_REQUIRED = convert_to_eat_claim(KM_TAG_TRUSTED_CONFIRMATION_REQUIRED),
    UNLOCKED_DEVICE_REQUIRED = convert_to_eat_claim(KM_TAG_UNLOCKED_DEVICE_REQUIRED),
    ALL_APPLICATIONS = convert_to_eat_claim(KM_TAG_ALL_APPLICATIONS),
    APPLICATION_ID = convert_to_eat_claim(KM_TAG_APPLICATION_ID),
    ORIGIN = convert_to_eat_claim(KM_TAG_ORIGIN),
    ROLLBACK_RESISTANT = convert_to_eat_claim(KM_TAG_ROLLBACK_RESISTANT),
    OS_VERSION = convert_to_eat_claim(KM_TAG_OS_VERSION),
    OS_PATCHLEVEL = convert_to_eat_claim(KM_TAG_OS_PATCHLEVEL),
    ATTESTATION_APPLICATION_ID = convert_to_eat_claim(KM_TAG_ATTESTATION_APPLICATION_ID),
    ATTESTATION_ID_BRAND = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_BRAND),
    ATTESTATION_ID_DEVICE = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_DEVICE),
    ATTESTATION_ID_PRODUCT = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_PRODUCT),
    ATTESTATION_ID_SERIAL = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_SERIAL),
    ATTESTATION_ID_MEID = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_MEID),
    ATTESTATION_ID_MANUFACTURER = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_MANUFACTURER),
    VENDOR_PATCHLEVEL = convert_to_eat_claim(KM_TAG_VENDOR_PATCHLEVEL),
    BOOT_PATCHLEVEL = convert_to_eat_claim(KM_TAG_BOOT_PATCHLEVEL),
    ATTESTATION_ID_MODEL = convert_to_eat_claim(KM_TAG_ATTESTATION_ID_MODEL),
    DEVICE_UNIQUE_ATTESTATION = convert_to_eat_claim(KM_TAG_DEVICE_UNIQUE_ATTESTATION),
    IDENTITY_CREDENTIAL_KEY = convert_to_eat_claim(KM_TAG_IDENTITY_CREDENTIAL_KEY),
    STORAGE_KEY = convert_to_eat_claim(KM_TAG_STORAGE_KEY),
    CONFIRMATION_TOKEN = convert_to_eat_claim(KM_TAG_CONFIRMATION_TOKEN),

    // Claims specific to Android that do not exist as Keymint tags.

    VERIFIED_BOOT_KEY = EAT_CLAIM_PRIVATE_NON_KM_BASE - 1,
    DEVICE_LOCKED = EAT_CLAIM_PRIVATE_NON_KM_BASE - 2,
    VERIFIED_BOOT_HASH = EAT_CLAIM_PRIVATE_NON_KM_BASE - 3,
    ATTESTATION_VERSION = EAT_CLAIM_PRIVATE_NON_KM_BASE - 4,
    KEYMASTER_VERSION = EAT_CLAIM_PRIVATE_NON_KM_BASE - 5,
    OFFICIAL_BUILD = EAT_CLAIM_PRIVATE_NON_KM_BASE - 6,
};

enum class EatSecurityLevel {
    UNRESTRICTED = 1,
    RESTRICTED = 2,
    SECURE_RESTRICTED = 3,
    HARDWARE = 4,
};

enum class EatEcCurve {
    P_224 = KM_EC_CURVE_P_224,
    P_256 = KM_EC_CURVE_P_256,
    P_384 = KM_EC_CURVE_P_384,
    P_521 = KM_EC_CURVE_P_521,
};

static const char kEatSubmodNameSoftware[] = "software";
static const char kEatSubmodNameTee[] = "tee";

constexpr size_t kImeiBlobLength = 15;
constexpr size_t kUeidLength = 15;
constexpr uint8_t kImeiTypeByte = 0x03;

/**
 * The OID for Android attestation records.  For the curious, it breaks down as follows:
 *
 * 1 = ISO
 * 3 = org
 * 6 = DoD (Huh? OIDs are weird.)
 * 1 = IANA
 * 4 = Private
 * 1 = Enterprises
 * 11129 = Google
 * 2 = Google security
 * 1 = certificate extension
 * 17 / 25 = ASN.1 attestation extension / EAT attestation extension
 */
static const char kAsn1TokenOid[] = "1.3.6.1.4.1.11129.2.1.17";
static const char kEatTokenOid[] = "1.3.6.1.4.1.11129.2.1.25";

// This build_attestation_record sets the keymaster version to the default
// value, and chooses the correct attestation extension (ASN.1 or EAT) based
// on that value.
keymaster_error_t build_attestation_record(const AuthorizationSet& attestation_params,
                                           AuthorizationSet software_enforced,
                                           AuthorizationSet tee_enforced,
                                           const AttestationContext& context,
                                           UniquePtr<uint8_t[]>* asn1_key_desc,
                                           size_t* asn1_key_desc_len);

// Builds EAT record, with the same values as the attestation record above,
// but encoded as a CBOR (EAT) structure rather than an ASN.1 structure.
keymaster_error_t build_eat_record(const AuthorizationSet& attestation_params,
                                   AuthorizationSet software_enforced,
                                   AuthorizationSet tee_enforced,      //
                                   const AttestationContext& context,  //
                                   std::vector<uint8_t>* eat_token);

// Builds the input to HMAC-SHA256 for unique ID generation.
keymaster_error_t build_unique_id_input(uint64_t creation_date_time,
                                        const keymaster_blob_t& application_id,
                                        bool reset_since_rotation, Buffer* input_data);

// Builds a unique ID of size UNIQUE_ID_SIZE from the given inputs.
keymaster_error_t generate_unique_id(const std::vector<uint8_t>& hbk, uint64_t creation_date_time,
                                     const keymaster_blob_t& application_id,
                                     bool reset_since_rotation, Buffer* unique_id);

/**
 * Helper functions for attestation record tests. Caller takes ownership of
 * |attestation_challenge->data| and |unique_id->data|, deallocate using delete[].
 */
keymaster_error_t parse_attestation_record(const uint8_t* asn1_key_desc, size_t asn1_key_desc_len,
                                           uint32_t* attestation_version,  //
                                           keymaster_security_level_t* attestation_security_level,
                                           uint32_t* keymaster_version,
                                           keymaster_security_level_t* keymaster_security_level,
                                           keymaster_blob_t* attestation_challenge,
                                           AuthorizationSet* software_enforced,
                                           AuthorizationSet* tee_enforced,
                                           keymaster_blob_t* unique_id);

/**
 * Caller takes ownership of |verified_boot_key->data|, deallocate using delete[].
 */
keymaster_error_t parse_root_of_trust(const uint8_t* asn1_key_desc, size_t asn1_key_desc_len,
                                      keymaster_blob_t* verified_boot_key,
                                      keymaster_verified_boot_t* verified_boot_state,
                                      bool* device_locked);

keymaster_error_t build_eat_submod(const AuthorizationSet& auth_list,
                                   EatSecurityLevel security_level, cppbor::Map* submod);

keymaster_error_t build_auth_list(const AuthorizationSet& auth_list, KM_AUTH_LIST* record);

keymaster_error_t parse_eat_record(
    const uint8_t* eat_key_desc, size_t eat_key_desc_len, uint32_t* attestation_version,
    keymaster_security_level_t* attestation_security_level, uint32_t* keymaster_version,
    keymaster_security_level_t* keymaster_security_level, keymaster_blob_t* attestation_challenge,
    AuthorizationSet* software_enforced, AuthorizationSet* tee_enforced,
    keymaster_blob_t* unique_id, keymaster_blob_t* verified_boot_key,
    keymaster_verified_boot_t* verified_boot_state, bool* device_locked,
    std::vector<int64_t>* unexpected_claims);

keymaster_error_t parse_eat_submod(const cppbor::Map* submod_values,
                                   AuthorizationSet* software_enforced,
                                   AuthorizationSet* tee_enforced);

keymaster_error_t extract_auth_list(const KM_AUTH_LIST* record, AuthorizationSet* auth_list);

/**
 * Convert a KeymasterContext::Version to the keymaster version number used in attestations.
 */
inline static uint32_t version_to_attestation_km_version(KmVersion version) {
    switch (version) {
    default:
    case KmVersion::KEYMASTER_1:
    case KmVersion::KEYMASTER_1_1:
        return 0;  // Attestation not actually supported.
    case KmVersion::KEYMASTER_2:
        return 2;
    case KmVersion::KEYMASTER_3:
        return 3;
    case KmVersion::KEYMASTER_4:
        return 4;
    case KmVersion::KEYMASTER_4_1:
        return 41;
    case KmVersion::KEYMINT_1:
        return 100;
    case KmVersion::KEYMINT_2:
        return 200;
    case KmVersion::KEYMINT_3:
        return 300;
    }
}

/**
 * Convert a KeymasterContext::Version to the corresponding attestation format version number.
 */
inline static uint32_t version_to_attestation_version(KmVersion version) {
    switch (version) {
    default:
    case KmVersion::KEYMASTER_1:
        return 0;  // Attestation not actually supported.
    case KmVersion::KEYMASTER_2:
        return 1;
    case KmVersion::KEYMASTER_3:
        return 2;
    case KmVersion::KEYMASTER_4:
        return 3;
    case KmVersion::KEYMASTER_4_1:
        return 4;
    case KmVersion::KEYMINT_1:
        return 100;
    case KmVersion::KEYMINT_2:
        return 200;
    case KmVersion::KEYMINT_3:
        return 300;
    }
}

}  // namespace keymaster
