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

#include <keymaster/km_openssl/attestation_record.h>

#include <assert.h>
#include <math.h>

#include <unordered_map>

#include <cppbor_parse.h>
#include <openssl/asn1t.h>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/attestation_context.h>
#include <keymaster/km_openssl/hmac.h>
#include <keymaster/km_openssl/openssl_err.h>
#include <keymaster/km_openssl/openssl_utils.h>

#define ASSERT_OR_RETURN_ERROR(stmt, error)                                                        \
    do {                                                                                           \
        assert(stmt);                                                                              \
        if (!(stmt)) {                                                                             \
            return error;                                                                          \
        }                                                                                          \
    } while (0)

namespace keymaster {

constexpr size_t kMaximumAttestationChallengeLength = 128;

IMPLEMENT_ASN1_FUNCTIONS(KM_ROOT_OF_TRUST);
IMPLEMENT_ASN1_FUNCTIONS(KM_AUTH_LIST);
IMPLEMENT_ASN1_FUNCTIONS(KM_KEY_DESCRIPTION);

static const keymaster_tag_t kDeviceAttestationTags[] = {
    KM_TAG_ATTESTATION_ID_BRAND,        KM_TAG_ATTESTATION_ID_DEVICE,
    KM_TAG_ATTESTATION_ID_PRODUCT,      KM_TAG_ATTESTATION_ID_SERIAL,
    KM_TAG_ATTESTATION_ID_IMEI,         KM_TAG_ATTESTATION_ID_MEID,
    KM_TAG_ATTESTATION_ID_MANUFACTURER, KM_TAG_ATTESTATION_ID_MODEL,
    KM_TAG_ATTESTATION_ID_SECOND_IMEI};

struct KM_AUTH_LIST_Delete {
    void operator()(KM_AUTH_LIST* p) { KM_AUTH_LIST_free(p); }
};

struct KM_KEY_DESCRIPTION_Delete {
    void operator()(KM_KEY_DESCRIPTION* p) { KM_KEY_DESCRIPTION_free(p); }
};

struct KM_ROOT_OF_TRUST_Delete {
    void operator()(KM_ROOT_OF_TRUST* p) { KM_ROOT_OF_TRUST_free(p); }
};

static cppbor::Bstr blob_to_bstr(const keymaster_blob_t& blob) {
    return cppbor::Bstr(std::pair(blob.data, blob.data_length));
}

static keymaster_error_t bstr_to_blob(const cppbor::Bstr* bstr, keymaster_blob_t* blob) {
    ASSERT_OR_RETURN_ERROR(bstr, KM_ERROR_INVALID_TAG);
    const std::vector<uint8_t>& vec = bstr->value();
    uint8_t* data = (uint8_t*)calloc(vec.size(), sizeof(uint8_t));
    if (data == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    std::copy(vec.begin(), vec.end(), data);
    blob->data = data;
    blob->data_length = vec.size();

    return KM_ERROR_OK;
}

static uint32_t get_uint32_value(const keymaster_key_param_t& param) {
    switch (keymaster_tag_get_type(param.tag)) {
    case KM_ENUM:
    case KM_ENUM_REP:
        return param.enumerated;
    case KM_UINT:
    case KM_UINT_REP:
        return param.integer;
    default:
        ASSERT_OR_RETURN_ERROR(false, 0xFFFFFFFF);
    }
}

static int64_t get_uint32_value(EatSecurityLevel level) {
    return static_cast<int64_t>(level);
}

// Insert value in either the dest_integer or the dest_integer_set, whichever is provided.
static keymaster_error_t insert_integer(ASN1_INTEGER* value, ASN1_INTEGER** dest_integer,
                                        ASN1_INTEGER_SET** dest_integer_set) {
    ASSERT_OR_RETURN_ERROR((dest_integer == nullptr) ^ (dest_integer_set == nullptr),
                           KM_ERROR_UNEXPECTED_NULL_POINTER);
    ASSERT_OR_RETURN_ERROR(value, KM_ERROR_INVALID_ARGUMENT);

    if (dest_integer_set) {
        if (!*dest_integer_set) {
            *dest_integer_set = sk_ASN1_INTEGER_new_null();
        }
        if (!*dest_integer_set) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        if (!sk_ASN1_INTEGER_push(*dest_integer_set, value)) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        return KM_ERROR_OK;

    } else if (dest_integer) {
        if (*dest_integer) {
            ASN1_INTEGER_free(*dest_integer);
        }
        *dest_integer = value;
        return KM_ERROR_OK;
    }

    ASSERT_OR_RETURN_ERROR(false, KM_ERROR_UNKNOWN_ERROR);  // Should never get here.
}

// Add a repeating enum to a map going mapping its key to list of values.
static void add_repeating_enum(EatClaim key, uint32_t value,
                               std::unordered_map<EatClaim, cppbor::Array>* fields_map) {
    auto field = fields_map->find(key);
    if (field != fields_map->end()) {
        field->second.add(value);
    } else {
        fields_map->insert({key, cppbor::Array().add(value)});
    }
}

static keymaster_error_t
insert_unknown_tag(const keymaster_key_param_t& param, cppbor::Map* dest_map,
                   std::unordered_map<EatClaim, cppbor::Array>* fields_map) {
    EatClaim private_eat_tag = static_cast<EatClaim>(convert_to_eat_claim(param.tag));
    switch (keymaster_tag_get_type(param.tag)) {
    case KM_ENUM:
        dest_map->add(private_eat_tag, param.enumerated);
        break;
    case KM_ENUM_REP:
        add_repeating_enum(private_eat_tag, param.enumerated, fields_map);
        break;
    case KM_UINT:
        dest_map->add(private_eat_tag, param.integer);
        break;
    case KM_UINT_REP:
        add_repeating_enum(private_eat_tag, param.integer, fields_map);
        break;
    case KM_ULONG:
        dest_map->add(private_eat_tag, param.long_integer);
        break;
    case KM_ULONG_REP:
        add_repeating_enum(private_eat_tag, param.long_integer, fields_map);
        break;
    case KM_DATE:
        dest_map->add(private_eat_tag, param.date_time);
        break;
    case KM_BOOL:
        dest_map->add(private_eat_tag, true);
        break;
    case KM_BIGNUM:
    case KM_BYTES:
        dest_map->add(private_eat_tag, blob_to_bstr(param.blob));
        break;
    default:
        ASSERT_OR_RETURN_ERROR(false, KM_ERROR_INVALID_TAG);
    }
    return KM_ERROR_OK;
}

/**
 * Convert an IMEI encoded as a string of numbers into the UEID format defined in
 * https://tools.ietf.org/html/draft-ietf-rats-eat.
 * The resulting format is a bstr encoded as follows:
 * - Type byte: 0x03
 * - IMEI (without check digit), encoded as byte string of length 14 with each byte as the digit's
 *   value. The IMEI value encoded SHALL NOT include Luhn checksum or SVN information.
 */
keymaster_error_t imei_to_ueid(const keymaster_blob_t& imei_blob, cppbor::Bstr* out) {
    ASSERT_OR_RETURN_ERROR(imei_blob.data_length == kImeiBlobLength, KM_ERROR_INVALID_TAG);

    uint8_t ueid[kUeidLength];
    ueid[0] = kImeiTypeByte;
    // imei_blob corresponds to android.telephony.TelephonyManager#getDeviceId(), which is the
    // 15-digit IMEI (including the check digit), encoded as a string.
    for (size_t i = 1; i < kUeidLength; i++) {
        // Convert each character to its numeric value.
        ueid[i] = imei_blob.data[i - 1] - '0';  // Intentionally skip check digit at last position.
    }

    *out = cppbor::Bstr(std::pair(ueid, sizeof(ueid)));
    return KM_ERROR_OK;
}

keymaster_error_t ueid_to_imei_blob(const cppbor::Bstr* ueid, keymaster_blob_t* out) {
    ASSERT_OR_RETURN_ERROR(ueid, KM_ERROR_INVALID_TAG);
    const std::vector<uint8_t>& ueid_vec = ueid->value();
    ASSERT_OR_RETURN_ERROR(ueid_vec.size() == kUeidLength, KM_ERROR_INVALID_TAG);
    ASSERT_OR_RETURN_ERROR(ueid_vec[0] == kImeiTypeByte, KM_ERROR_INVALID_TAG);

    uint8_t* imei_string = (uint8_t*)calloc(kImeiBlobLength, sizeof(uint8_t));
    // Fill string from left to right, and calculate Luhn check digit.
    int luhn_digit_sum = 0;
    for (size_t i = 0; i < kImeiBlobLength - 1; i++) {
        uint8_t digit_i = ueid_vec[i + 1];
        // Convert digit to its string value.
        imei_string[i] = '0' + digit_i;
        luhn_digit_sum += i % 2 == 0 ? digit_i : digit_i * 2 / 10 + (digit_i * 2) % 10;
    }
    imei_string[kImeiBlobLength - 1] = '0' + (10 - luhn_digit_sum % 10) % 10;

    *out = {.data = imei_string, .data_length = kImeiBlobLength};
    return KM_ERROR_OK;
}

keymaster_error_t ec_key_size_to_eat_curve(uint32_t key_size_bits, int* curve) {
    switch (key_size_bits) {
    default:
        return KM_ERROR_UNSUPPORTED_KEY_SIZE;

    case 224:
        *curve = (int)EatEcCurve::P_224;
        break;

    case 256:
        *curve = (int)EatEcCurve::P_256;
        break;

    case 384:
        *curve = (int)EatEcCurve::P_384;
        break;

    case 521:
        *curve = (int)EatEcCurve::P_521;
        break;
    }

    return KM_ERROR_OK;
}

bool is_valid_attestation_challenge(const keymaster_blob_t& attestation_challenge) {
    // TODO(171864369): Limit apps targeting >= API 30 to attestations in the range of
    // [0, 128] bytes.
    return (attestation_challenge.data_length <= kMaximumAttestationChallengeLength);
}

// Put the contents of the keymaster AuthorizationSet auth_list into the EAT record structure.
keymaster_error_t build_eat_submod(const AuthorizationSet& auth_list,
                                   const EatSecurityLevel security_level, cppbor::Map* submod) {
    ASSERT_OR_RETURN_ERROR(submod, KM_ERROR_UNEXPECTED_NULL_POINTER);

    if (auth_list.empty()) return KM_ERROR_OK;

    submod->add(EatClaim::SECURITY_LEVEL, get_uint32_value(security_level));

    // Keep repeating fields in a separate map for easy lookup.
    // Add them to submod map in postprocessing.
    std::unordered_map<EatClaim, cppbor::Array> repeating_fields =
        std::unordered_map<EatClaim, cppbor::Array>();

    for (auto entry : auth_list) {

        switch (entry.tag) {

        default:
            // Unknown tags should only be included if they're software-enforced.
            if (security_level == EatSecurityLevel::UNRESTRICTED) {
                keymaster_error_t error = insert_unknown_tag(entry, submod, &repeating_fields);
                if (error != KM_ERROR_OK) {
                    return error;
                }
            }
            break;

        /* Tags ignored because they should never exist */
        case KM_TAG_INVALID:

        /* Tags ignored because they're not used. */
        case KM_TAG_ALL_USERS:
        case KM_TAG_EXPORTABLE:
        case KM_TAG_ECIES_SINGLE_HASH_MODE:
        case KM_TAG_KDF:

        /* Tags ignored because they're used only to provide information to operations */
        case KM_TAG_ASSOCIATED_DATA:
        case KM_TAG_NONCE:
        case KM_TAG_AUTH_TOKEN:
        case KM_TAG_MAC_LENGTH:
        case KM_TAG_ATTESTATION_CHALLENGE:
        case KM_TAG_RESET_SINCE_ID_ROTATION:

        /* Tags ignored because they have no meaning off-device */
        case KM_TAG_USER_ID:
        case KM_TAG_USER_SECURE_ID:
        case KM_TAG_BLOB_USAGE_REQUIREMENTS:

        /* Tags ignored because they're not usable by app keys */
        case KM_TAG_BOOTLOADER_ONLY:
        case KM_TAG_INCLUDE_UNIQUE_ID:
        case KM_TAG_MAX_USES_PER_BOOT:
        case KM_TAG_MIN_SECONDS_BETWEEN_OPS:
        case KM_TAG_UNIQUE_ID:

        /* Tags ignored because they contain data that should not be exported */
        case KM_TAG_APPLICATION_DATA:
        case KM_TAG_APPLICATION_ID:
        case KM_TAG_ROOT_OF_TRUST:
            continue;

        /* Non-repeating enumerations */
        case KM_TAG_ALGORITHM:
            submod->add(EatClaim::ALGORITHM, get_uint32_value(entry));
            break;
        case KM_TAG_EC_CURVE:
            submod->add(EatClaim::EC_CURVE, get_uint32_value(entry));
            break;
        case KM_TAG_USER_AUTH_TYPE:
            submod->add(EatClaim::USER_AUTH_TYPE, get_uint32_value(entry));
            break;
        case KM_TAG_ORIGIN:
            submod->add(EatClaim::ORIGIN, get_uint32_value(entry));
            break;

        /* Repeating enumerations */
        case KM_TAG_PURPOSE:
            add_repeating_enum(EatClaim::PURPOSE, get_uint32_value(entry), &repeating_fields);
            break;
        case KM_TAG_PADDING:
            add_repeating_enum(EatClaim::PADDING, get_uint32_value(entry), &repeating_fields);
            break;
        case KM_TAG_DIGEST:
            add_repeating_enum(EatClaim::DIGEST, get_uint32_value(entry), &repeating_fields);
            break;
        case KM_TAG_BLOCK_MODE:
            add_repeating_enum(EatClaim::BLOCK_MODE, get_uint32_value(entry), &repeating_fields);
            break;

        /* Non-repeating unsigned integers */
        case KM_TAG_KEY_SIZE:
            submod->add(EatClaim::KEY_SIZE, get_uint32_value(entry));
            break;
        case KM_TAG_AUTH_TIMEOUT:
            submod->add(EatClaim::AUTH_TIMEOUT, get_uint32_value(entry));
            break;
        case KM_TAG_OS_VERSION:
            submod->add(EatClaim::OS_VERSION, get_uint32_value(entry));
            break;
        case KM_TAG_OS_PATCHLEVEL:
            submod->add(EatClaim::OS_PATCHLEVEL, get_uint32_value(entry));
            break;
        case KM_TAG_VENDOR_PATCHLEVEL:
            submod->add(EatClaim::VENDOR_PATCHLEVEL, get_uint32_value(entry));
            break;
        case KM_TAG_BOOT_PATCHLEVEL:
            submod->add(EatClaim::BOOT_PATCHLEVEL, get_uint32_value(entry));
            break;
        case KM_TAG_MIN_MAC_LENGTH:
            submod->add(EatClaim::MIN_MAC_LENGTH, get_uint32_value(entry));
            break;

        /* Non-repeating long unsigned integers */
        case KM_TAG_RSA_PUBLIC_EXPONENT:
            submod->add(EatClaim::RSA_PUBLIC_EXPONENT, entry.long_integer);
            break;

        /* Dates */
        case KM_TAG_ACTIVE_DATETIME:
            submod->add(EatClaim::ACTIVE_DATETIME, entry.date_time);
            break;
        case KM_TAG_ORIGINATION_EXPIRE_DATETIME:
            submod->add(EatClaim::ORIGINATION_EXPIRE_DATETIME, entry.date_time);
            break;
        case KM_TAG_USAGE_EXPIRE_DATETIME:
            submod->add(EatClaim::USAGE_EXPIRE_DATETIME, entry.date_time);
            break;
        case KM_TAG_CREATION_DATETIME:
            submod->add(EatClaim::IAT, entry.date_time);
            break;

        /* Booleans */
        case KM_TAG_NO_AUTH_REQUIRED:
            submod->add(EatClaim::NO_AUTH_REQUIRED, true);
            break;
        case KM_TAG_ALL_APPLICATIONS:
            submod->add(EatClaim::ALL_APPLICATIONS, true);
            break;
        case KM_TAG_ROLLBACK_RESISTANT:
            submod->add(EatClaim::ROLLBACK_RESISTANT, true);
            break;
        case KM_TAG_ALLOW_WHILE_ON_BODY:
            submod->add(EatClaim::ALLOW_WHILE_ON_BODY, true);
            break;
        case KM_TAG_UNLOCKED_DEVICE_REQUIRED:
            submod->add(EatClaim::UNLOCKED_DEVICE_REQUIRED, true);
            break;
        case KM_TAG_CALLER_NONCE:
            submod->add(EatClaim::CALLER_NONCE, true);
            break;
        case KM_TAG_TRUSTED_CONFIRMATION_REQUIRED:
            submod->add(EatClaim::TRUSTED_CONFIRMATION_REQUIRED, true);
            break;
        case KM_TAG_EARLY_BOOT_ONLY:
            submod->add(EatClaim::EARLY_BOOT_ONLY, true);
            break;
        case KM_TAG_DEVICE_UNIQUE_ATTESTATION:
            submod->add(EatClaim::DEVICE_UNIQUE_ATTESTATION, true);
            break;
        case KM_TAG_IDENTITY_CREDENTIAL_KEY:
            submod->add(EatClaim::IDENTITY_CREDENTIAL_KEY, true);
            break;
        case KM_TAG_TRUSTED_USER_PRESENCE_REQUIRED:
            submod->add(EatClaim::TRUSTED_USER_PRESENCE_REQUIRED, true);
            break;
        case KM_TAG_STORAGE_KEY:
            submod->add(EatClaim::STORAGE_KEY, true);
            break;

        /* Byte arrays*/
        case KM_TAG_ATTESTATION_APPLICATION_ID:
            submod->add(EatClaim::ATTESTATION_APPLICATION_ID, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_BRAND:
            submod->add(EatClaim::ATTESTATION_ID_BRAND, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_DEVICE:
            submod->add(EatClaim::ATTESTATION_ID_DEVICE, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_PRODUCT:
            submod->add(EatClaim::ATTESTATION_ID_PRODUCT, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_SERIAL:
            submod->add(EatClaim::ATTESTATION_ID_SERIAL, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_IMEI: {
            cppbor::Bstr ueid("");
            keymaster_error_t error = imei_to_ueid(entry.blob, &ueid);
            if (error != KM_ERROR_OK) return error;
            submod->add(EatClaim::UEID, ueid);
            break;
        }
        case KM_TAG_ATTESTATION_ID_MEID:
            submod->add(EatClaim::ATTESTATION_ID_MEID, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_MANUFACTURER:
            submod->add(EatClaim::ATTESTATION_ID_MANUFACTURER, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_ATTESTATION_ID_MODEL:
            submod->add(EatClaim::ATTESTATION_ID_MODEL, blob_to_bstr(entry.blob));
            break;
        case KM_TAG_CONFIRMATION_TOKEN:
            submod->add(EatClaim::CONFIRMATION_TOKEN, blob_to_bstr(entry.blob));
            break;
        }
    }

    // Move values from repeating enums into the submod map.
    for (auto const& repeating_field : repeating_fields) {
        EatClaim key = static_cast<EatClaim>(repeating_field.first);
        submod->add(key, std::move(repeating_fields.at(key)));
    }

    int ec_curve;
    uint32_t key_size;
    if (auth_list.Contains(TAG_ALGORITHM, KM_ALGORITHM_EC) && !auth_list.Contains(TAG_EC_CURVE) &&
        auth_list.GetTagValue(TAG_KEY_SIZE, &key_size)) {
        // This must be a keymaster1 key. It's an EC key with no curve.  Insert the curve.

        keymaster_error_t error = ec_key_size_to_eat_curve(key_size, &ec_curve);
        if (error != KM_ERROR_OK) return error;

        submod->add(EatClaim::EC_CURVE, ec_curve);
    }

    return KM_ERROR_OK;
}

// Put the contents of the keymaster AuthorizationSet auth_list into the ASN.1 record structure,
// record.
keymaster_error_t build_auth_list(const AuthorizationSet& auth_list, KM_AUTH_LIST* record) {
    ASSERT_OR_RETURN_ERROR(record, KM_ERROR_UNEXPECTED_NULL_POINTER);

    if (auth_list.empty()) return KM_ERROR_OK;

    for (auto entry : auth_list) {

        ASN1_INTEGER_SET** integer_set = nullptr;
        ASN1_INTEGER** integer_ptr = nullptr;
        ASN1_OCTET_STRING** string_ptr = nullptr;
        ASN1_NULL** bool_ptr = nullptr;

        switch (entry.tag) {

        /* Tags ignored because they should never exist */
        case KM_TAG_INVALID:

        /* Tags ignored because they're not used. */
        case KM_TAG_ALL_USERS:
        case KM_TAG_EXPORTABLE:
        case KM_TAG_ECIES_SINGLE_HASH_MODE:

        /* Tags ignored because they're used only to provide information to operations */
        case KM_TAG_ASSOCIATED_DATA:
        case KM_TAG_NONCE:
        case KM_TAG_AUTH_TOKEN:
        case KM_TAG_MAC_LENGTH:
        case KM_TAG_ATTESTATION_CHALLENGE:
        case KM_TAG_KDF:

        /* Tags ignored because they're used only to provide for certificate generation */
        case KM_TAG_CERTIFICATE_SERIAL:
        case KM_TAG_CERTIFICATE_SUBJECT:
        case KM_TAG_CERTIFICATE_NOT_BEFORE:
        case KM_TAG_CERTIFICATE_NOT_AFTER:
        case KM_TAG_INCLUDE_UNIQUE_ID:
        case KM_TAG_RESET_SINCE_ID_ROTATION:

        /* Tags ignored because they have no meaning off-device */
        case KM_TAG_USER_ID:
        case KM_TAG_USER_SECURE_ID:
        case KM_TAG_BLOB_USAGE_REQUIREMENTS:

        /* Tags ignored because they're not usable by app keys */
        case KM_TAG_BOOTLOADER_ONLY:
        case KM_TAG_MAX_BOOT_LEVEL:
        case KM_TAG_MAX_USES_PER_BOOT:
        case KM_TAG_MIN_SECONDS_BETWEEN_OPS:
        case KM_TAG_STORAGE_KEY:
        case KM_TAG_UNIQUE_ID:

        /* Tags ignored because they contain data that should not be exported */
        case KM_TAG_APPLICATION_DATA:
        case KM_TAG_APPLICATION_ID:
        case KM_TAG_CONFIRMATION_TOKEN:
        case KM_TAG_ROOT_OF_TRUST:
            continue;

        /* Non-repeating enumerations */
        case KM_TAG_ALGORITHM:
            integer_ptr = &record->algorithm;
            break;
        case KM_TAG_EC_CURVE:
            integer_ptr = &record->ec_curve;
            break;
        case KM_TAG_USER_AUTH_TYPE:
            integer_ptr = &record->user_auth_type;
            break;
        case KM_TAG_ORIGIN:
            integer_ptr = &record->origin;
            break;

        /* Repeating enumerations */
        case KM_TAG_PURPOSE:
            integer_set = &record->purpose;
            break;
        case KM_TAG_PADDING:
            integer_set = &record->padding;
            break;
        case KM_TAG_DIGEST:
            integer_set = &record->digest;
            break;
        case KM_TAG_BLOCK_MODE:
            integer_set = &record->block_mode;
            break;
        case KM_TAG_RSA_OAEP_MGF_DIGEST:
            integer_set = &record->mgf_digest;
            break;

        /* Non-repeating unsigned integers */
        case KM_TAG_KEY_SIZE:
            integer_ptr = &record->key_size;
            break;
        case KM_TAG_AUTH_TIMEOUT:
            integer_ptr = &record->auth_timeout;
            break;
        case KM_TAG_OS_VERSION:
            integer_ptr = &record->os_version;
            break;
        case KM_TAG_OS_PATCHLEVEL:
            integer_ptr = &record->os_patchlevel;
            break;
        case KM_TAG_MIN_MAC_LENGTH:
            integer_ptr = &record->min_mac_length;
            break;
        case KM_TAG_BOOT_PATCHLEVEL:
            integer_ptr = &record->boot_patch_level;
            break;
        case KM_TAG_VENDOR_PATCHLEVEL:
            integer_ptr = &record->vendor_patchlevel;
            break;
        case KM_TAG_USAGE_COUNT_LIMIT:
            integer_ptr = &record->usage_count_limit;
            break;

        /* Non-repeating long unsigned integers */
        case KM_TAG_RSA_PUBLIC_EXPONENT:
            integer_ptr = &record->rsa_public_exponent;
            break;

        /* Dates */
        case KM_TAG_ACTIVE_DATETIME:
            integer_ptr = &record->active_date_time;
            break;
        case KM_TAG_ORIGINATION_EXPIRE_DATETIME:
            integer_ptr = &record->origination_expire_date_time;
            break;
        case KM_TAG_USAGE_EXPIRE_DATETIME:
            integer_ptr = &record->usage_expire_date_time;
            break;
        case KM_TAG_CREATION_DATETIME:
            integer_ptr = &record->creation_date_time;
            break;

        /* Booleans */
        case KM_TAG_NO_AUTH_REQUIRED:
            bool_ptr = &record->no_auth_required;
            break;
        case KM_TAG_ALL_APPLICATIONS:
            bool_ptr = &record->all_applications;
            break;
        case KM_TAG_ROLLBACK_RESISTANT:
            bool_ptr = &record->rollback_resistant;
            break;
        case KM_TAG_ROLLBACK_RESISTANCE:
            bool_ptr = &record->rollback_resistance;
            break;
        case KM_TAG_ALLOW_WHILE_ON_BODY:
            bool_ptr = &record->allow_while_on_body;
            break;
        case KM_TAG_UNLOCKED_DEVICE_REQUIRED:
            bool_ptr = &record->unlocked_device_required;
            break;
        case KM_TAG_CALLER_NONCE:
            bool_ptr = &record->caller_nonce;
            break;
        case KM_TAG_TRUSTED_CONFIRMATION_REQUIRED:
            bool_ptr = &record->trusted_confirmation_required;
            break;
        case KM_TAG_EARLY_BOOT_ONLY:
            bool_ptr = &record->early_boot_only;
            break;
        case KM_TAG_DEVICE_UNIQUE_ATTESTATION:
            bool_ptr = &record->device_unique_attestation;
            break;
        case KM_TAG_IDENTITY_CREDENTIAL_KEY:
            bool_ptr = &record->identity_credential_key;
            break;
        case KM_TAG_TRUSTED_USER_PRESENCE_REQUIRED:
            bool_ptr = &record->trusted_user_presence_required;
            break;

        /* Byte arrays*/
        case KM_TAG_ATTESTATION_APPLICATION_ID:
            string_ptr = &record->attestation_application_id;
            break;
        case KM_TAG_ATTESTATION_ID_BRAND:
            string_ptr = &record->attestation_id_brand;
            break;
        case KM_TAG_ATTESTATION_ID_DEVICE:
            string_ptr = &record->attestation_id_device;
            break;
        case KM_TAG_ATTESTATION_ID_PRODUCT:
            string_ptr = &record->attestation_id_product;
            break;
        case KM_TAG_ATTESTATION_ID_SERIAL:
            string_ptr = &record->attestation_id_serial;
            break;
        case KM_TAG_ATTESTATION_ID_IMEI:
            string_ptr = &record->attestation_id_imei;
            break;
        case KM_TAG_ATTESTATION_ID_SECOND_IMEI:
            string_ptr = &record->attestation_id_second_imei;
            break;
        case KM_TAG_ATTESTATION_ID_MEID:
            string_ptr = &record->attestation_id_meid;
            break;
        case KM_TAG_ATTESTATION_ID_MANUFACTURER:
            string_ptr = &record->attestation_id_manufacturer;
            break;
        case KM_TAG_ATTESTATION_ID_MODEL:
            string_ptr = &record->attestation_id_model;
            break;
        }

        keymaster_tag_type_t type = keymaster_tag_get_type(entry.tag);
        switch (type) {
        case KM_ENUM:
        case KM_ENUM_REP:
        case KM_UINT:
        case KM_UINT_REP: {
            ASSERT_OR_RETURN_ERROR((keymaster_tag_repeatable(entry.tag) && integer_set) ||
                                       (!keymaster_tag_repeatable(entry.tag) && integer_ptr),
                                   KM_ERROR_INVALID_TAG);

            UniquePtr<ASN1_INTEGER, ASN1_INTEGER_Delete> value(ASN1_INTEGER_new());
            if (!value.get()) {
                return KM_ERROR_MEMORY_ALLOCATION_FAILED;
            }
            if (!ASN1_INTEGER_set(value.get(), get_uint32_value(entry))) {
                return TranslateLastOpenSslError();
            }

            insert_integer(value.release(), integer_ptr, integer_set);
            break;
        }

        case KM_ULONG:
        case KM_ULONG_REP:
        case KM_DATE: {
            ASSERT_OR_RETURN_ERROR((keymaster_tag_repeatable(entry.tag) && integer_set) ||
                                       (!keymaster_tag_repeatable(entry.tag) && integer_ptr),
                                   KM_ERROR_INVALID_TAG);

            UniquePtr<BIGNUM, BIGNUM_Delete> bn_value(BN_new());
            if (!bn_value.get()) {
                return KM_ERROR_MEMORY_ALLOCATION_FAILED;
            }

            if (type == KM_DATE) {
                if (!BN_set_u64(bn_value.get(), entry.date_time)) {
                    return TranslateLastOpenSslError();
                }
            } else {
                if (!BN_set_u64(bn_value.get(), entry.long_integer)) {
                    return TranslateLastOpenSslError();
                }
            }

            UniquePtr<ASN1_INTEGER, ASN1_INTEGER_Delete> value(
                BN_to_ASN1_INTEGER(bn_value.get(), nullptr));
            if (!value.get()) {
                return KM_ERROR_MEMORY_ALLOCATION_FAILED;
            }

            insert_integer(value.release(), integer_ptr, integer_set);
            break;
        }

        case KM_BOOL:
            ASSERT_OR_RETURN_ERROR(bool_ptr, KM_ERROR_INVALID_TAG);
            if (!*bool_ptr) *bool_ptr = ASN1_NULL_new();
            if (!*bool_ptr) return KM_ERROR_MEMORY_ALLOCATION_FAILED;
            break;

        /* Byte arrays*/
        case KM_BYTES:
            ASSERT_OR_RETURN_ERROR(string_ptr, KM_ERROR_INVALID_TAG);
            if (!*string_ptr) {
                *string_ptr = ASN1_OCTET_STRING_new();
            }
            if (!*string_ptr) {
                return KM_ERROR_MEMORY_ALLOCATION_FAILED;
            }
            if (!ASN1_OCTET_STRING_set(*string_ptr, entry.blob.data, entry.blob.data_length)) {
                return TranslateLastOpenSslError();
            }
            break;

        default:
            return KM_ERROR_UNIMPLEMENTED;
        }
    }

    keymaster_ec_curve_t ec_curve;
    uint32_t key_size;
    if (auth_list.Contains(TAG_ALGORITHM, KM_ALGORITHM_EC) &&  //
        !auth_list.Contains(TAG_EC_CURVE) &&                   //
        auth_list.GetTagValue(TAG_KEY_SIZE, &key_size)) {
        // This must be a keymaster1 key. It's an EC key with no curve.  Insert the curve if we
        // can unambiguously figure it out.

        keymaster_error_t error = EcKeySizeToCurve(key_size, &ec_curve);
        if (error != KM_ERROR_OK) return error;

        UniquePtr<ASN1_INTEGER, ASN1_INTEGER_Delete> value(ASN1_INTEGER_new());
        if (!value.get()) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }

        if (!ASN1_INTEGER_set(value.get(), ec_curve)) {
            return TranslateLastOpenSslError();
        }

        insert_integer(value.release(), &record->ec_curve, nullptr);
    }

    return KM_ERROR_OK;
}

// Construct a CBOR-encoded attestation record containing the values from sw_enforced
// and tee_enforced.
keymaster_error_t build_eat_record(const AuthorizationSet& attestation_params,
                                   AuthorizationSet sw_enforced, AuthorizationSet tee_enforced,
                                   const AttestationContext& context,
                                   std::vector<uint8_t>* eat_token) {
    ASSERT_OR_RETURN_ERROR(eat_token, KM_ERROR_UNEXPECTED_NULL_POINTER);

    cppbor::Map eat_record;
    switch (context.GetSecurityLevel()) {
    case KM_SECURITY_LEVEL_SOFTWARE:
        eat_record.add(EatClaim::SECURITY_LEVEL, get_uint32_value(EatSecurityLevel::UNRESTRICTED));
        break;
    case KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT:
        eat_record.add(EatClaim::SECURITY_LEVEL,
                       get_uint32_value(EatSecurityLevel::SECURE_RESTRICTED));
        break;
    case KM_SECURITY_LEVEL_STRONGBOX:
        eat_record.add(EatClaim::SECURITY_LEVEL, get_uint32_value(EatSecurityLevel::HARDWARE));
        break;
    default:
        return KM_ERROR_UNKNOWN_ERROR;
    }

    keymaster_error_t error;
    const AttestationContext::VerifiedBootParams* vb_params = context.GetVerifiedBootParams(&error);
    if (error != KM_ERROR_OK) return error;

    if (vb_params->verified_boot_key.data_length) {
        eat_record.add(EatClaim::VERIFIED_BOOT_KEY, blob_to_bstr(vb_params->verified_boot_key));
    }
    if (vb_params->verified_boot_hash.data_length) {
        eat_record.add(EatClaim::VERIFIED_BOOT_HASH, blob_to_bstr(vb_params->verified_boot_hash));
    }
    if (vb_params->device_locked) {
        eat_record.add(EatClaim::DEVICE_LOCKED, vb_params->device_locked);
    }

    bool verified_or_self_signed = (vb_params->verified_boot_state == KM_VERIFIED_BOOT_VERIFIED ||
                                    vb_params->verified_boot_state == KM_VERIFIED_BOOT_SELF_SIGNED);
    auto eat_boot_state = cppbor::Array()
                              .add(verified_or_self_signed)  // secure-boot-enabled
                              .add(verified_or_self_signed)  // debug-disabled
                              .add(verified_or_self_signed)  // debug-disabled-since-boot
                              .add(verified_or_self_signed)  // debug-permanent-disable
                              .add(false);  // debug-full-permanent-disable (no way to verify)
    eat_record.add(EatClaim::BOOT_STATE, std::move(eat_boot_state));
    eat_record.add(EatClaim::OFFICIAL_BUILD,
                   vb_params->verified_boot_state == KM_VERIFIED_BOOT_VERIFIED);

    eat_record.add(EatClaim::ATTESTATION_VERSION,
                   version_to_attestation_version(context.GetKmVersion()));
    eat_record.add(EatClaim::KEYMASTER_VERSION,
                   version_to_attestation_km_version(context.GetKmVersion()));

    keymaster_blob_t attestation_challenge = {nullptr, 0};
    if (!attestation_params.GetTagValue(TAG_ATTESTATION_CHALLENGE, &attestation_challenge)) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    }

    if (!is_valid_attestation_challenge(attestation_challenge)) {
        return KM_ERROR_INVALID_INPUT_LENGTH;
    }

    eat_record.add(EatClaim::NONCE, blob_to_bstr(attestation_challenge));

    keymaster_blob_t attestation_app_id;
    if (!attestation_params.GetTagValue(TAG_ATTESTATION_APPLICATION_ID, &attestation_app_id)) {
        return KM_ERROR_ATTESTATION_APPLICATION_ID_MISSING;
    }
    // TODO: what should happen when sw_enforced already contains TAG_ATTESTATION_APPLICATION_ID?
    // (as is the case in android_keymaster_test.cpp). For now, we will ignore the provided one in
    // attestation_params if that's the case.
    keymaster_blob_t existing_app_id;
    if (!sw_enforced.GetTagValue(TAG_ATTESTATION_APPLICATION_ID, &existing_app_id)) {
        sw_enforced.push_back(TAG_ATTESTATION_APPLICATION_ID, attestation_app_id);
    }

    error = context.VerifyAndCopyDeviceIds(
        attestation_params,
        context.GetSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE ? &sw_enforced : &tee_enforced);
    if (error == KM_ERROR_UNIMPLEMENTED) {
        // The KeymasterContext implementation does not support device ID attestation. Bail out if
        // device ID attestation is being attempted.
        for (const auto& tag : kDeviceAttestationTags) {
            if (attestation_params.find(tag) != -1) {
                return KM_ERROR_CANNOT_ATTEST_IDS;
            }
        }
    } else if (error != KM_ERROR_OK) {
        return error;
    }

    if (attestation_params.Contains(TAG_DEVICE_UNIQUE_ATTESTATION) &&
        context.GetSecurityLevel() == KM_SECURITY_LEVEL_STRONGBOX) {
        eat_record.add(EatClaim::DEVICE_UNIQUE_ATTESTATION, true);
    }

    cppbor::Map software_submod;
    error = build_eat_submod(sw_enforced, EatSecurityLevel::UNRESTRICTED, &software_submod);
    if (error != KM_ERROR_OK) return error;

    cppbor::Map tee_submod;
    error = build_eat_submod(tee_enforced, EatSecurityLevel::SECURE_RESTRICTED, &tee_submod);
    if (error != KM_ERROR_OK) return error;

    if (software_submod.size() + tee_submod.size() > 0) {
        cppbor::Map submods;
        if (software_submod.size() > 0) {
            submods.add(kEatSubmodNameSoftware, std::move(software_submod));
        }
        if (tee_submod.size() > 0) {
            submods.add(kEatSubmodNameTee, std::move(tee_submod));
        }

        eat_record.add(EatClaim::SUBMODS, std::move(submods));
    }

    if (attestation_params.GetTagValue(TAG_INCLUDE_UNIQUE_ID)) {
        uint64_t creation_datetime;
        // Only check sw_enforced for TAG_CREATION_DATETIME, since it shouldn't be in tee_enforced,
        // since this implementation has no secure wall clock.
        if (!sw_enforced.GetTagValue(TAG_CREATION_DATETIME, &creation_datetime)) {
            LOG_E("Unique ID cannot be created without creation datetime");
            return KM_ERROR_INVALID_KEY_BLOB;
        }

        Buffer unique_id = context.GenerateUniqueId(
            creation_datetime, attestation_app_id,
            attestation_params.GetTagValue(TAG_RESET_SINCE_ID_ROTATION), &error);
        if (error != KM_ERROR_OK) return error;

        eat_record.add(EatClaim::CTI,
                       cppbor::Bstr(std::pair(unique_id.begin(), unique_id.available_read())));
    }

    *eat_token = eat_record.encode();

    return KM_ERROR_OK;
}

keymaster_error_t build_unique_id_input(uint64_t creation_date_time,
                                        const keymaster_blob_t& application_id,
                                        bool reset_since_rotation, Buffer* input_data) {
    if (input_data == nullptr) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }
    uint64_t rounded_date = creation_date_time / 2592000000LLU;
    uint8_t* serialized_date = reinterpret_cast<uint8_t*>(&rounded_date);
    uint8_t reset_byte = (reset_since_rotation ? 1 : 0);

    if (!input_data->Reinitialize(sizeof(rounded_date) + application_id.data_length + 1) ||
        !input_data->write(serialized_date, sizeof(rounded_date)) ||
        !input_data->write(application_id.data, application_id.data_length) ||
        !input_data->write(&reset_byte, 1)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    return KM_ERROR_OK;
}

keymaster_error_t generate_unique_id(const std::vector<uint8_t>& hbk, uint64_t creation_date_time,
                                     const keymaster_blob_t& application_id,
                                     bool reset_since_rotation, Buffer* unique_id) {
    if (unique_id == nullptr) {
        return KM_ERROR_UNEXPECTED_NULL_POINTER;
    }
    HmacSha256 hmac;
    hmac.Init(hbk.data(), hbk.size());

    Buffer input;
    keymaster_error_t error =
        build_unique_id_input(creation_date_time, application_id, reset_since_rotation, &input);
    if (error != KM_ERROR_OK) {
        return error;
    }
    if (!unique_id->Reinitialize(UNIQUE_ID_SIZE)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    hmac.Sign(input.peek_read(), input.available_read(), unique_id->peek_write(),
              unique_id->available_write());
    unique_id->advance_write(UNIQUE_ID_SIZE);
    return KM_ERROR_OK;
}

// Construct an ASN1.1 DER-encoded attestation record containing the values from sw_enforced and
// tee_enforced.
keymaster_error_t build_attestation_record(const AuthorizationSet& attestation_params,  //
                                           AuthorizationSet sw_enforced,
                                           AuthorizationSet tee_enforced,
                                           const AttestationContext& context,
                                           UniquePtr<uint8_t[]>* asn1_key_desc,
                                           size_t* asn1_key_desc_len) {
    ASSERT_OR_RETURN_ERROR(asn1_key_desc && asn1_key_desc_len, KM_ERROR_UNEXPECTED_NULL_POINTER);

    UniquePtr<KM_KEY_DESCRIPTION, KM_KEY_DESCRIPTION_Delete> key_desc(KM_KEY_DESCRIPTION_new());
    if (!key_desc.get()) return KM_ERROR_MEMORY_ALLOCATION_FAILED;

    KM_ROOT_OF_TRUST* root_of_trust = nullptr;
    if (context.GetSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE) {
        key_desc->software_enforced->root_of_trust = KM_ROOT_OF_TRUST_new();
        root_of_trust = key_desc->software_enforced->root_of_trust;
    } else {
        key_desc->tee_enforced->root_of_trust = KM_ROOT_OF_TRUST_new();
        root_of_trust = key_desc->tee_enforced->root_of_trust;
    }

    keymaster_error_t error;
    auto vb_params = context.GetVerifiedBootParams(&error);
    if (error != KM_ERROR_OK) return error;
    if (vb_params->verified_boot_key.data_length &&
        !ASN1_OCTET_STRING_set(root_of_trust->verified_boot_key, vb_params->verified_boot_key.data,
                               vb_params->verified_boot_key.data_length)) {
        return TranslateLastOpenSslError();
    }
    if (vb_params->verified_boot_hash.data_length &&
        !ASN1_OCTET_STRING_set(root_of_trust->verified_boot_hash,
                               vb_params->verified_boot_hash.data,
                               vb_params->verified_boot_hash.data_length)) {
        return TranslateLastOpenSslError();
    }

    root_of_trust->device_locked = vb_params->device_locked ? 0xFF : 0x00;
    if (!ASN1_ENUMERATED_set(root_of_trust->verified_boot_state, vb_params->verified_boot_state)) {
        return TranslateLastOpenSslError();
    }

    if (!ASN1_INTEGER_set(key_desc->attestation_version,
                          version_to_attestation_version(context.GetKmVersion())) ||
        !ASN1_ENUMERATED_set(key_desc->attestation_security_level, context.GetSecurityLevel()) ||
        !ASN1_INTEGER_set(key_desc->keymaster_version,
                          version_to_attestation_km_version(context.GetKmVersion())) ||
        !ASN1_ENUMERATED_set(key_desc->keymaster_security_level, context.GetSecurityLevel())) {
        return TranslateLastOpenSslError();
    }

    keymaster_blob_t attestation_challenge = {nullptr, 0};
    if (!attestation_params.GetTagValue(TAG_ATTESTATION_CHALLENGE, &attestation_challenge)) {
        return KM_ERROR_ATTESTATION_CHALLENGE_MISSING;
    }

    if (!is_valid_attestation_challenge(attestation_challenge)) {
        return KM_ERROR_INVALID_INPUT_LENGTH;
    }

    if (!ASN1_OCTET_STRING_set(key_desc->attestation_challenge, attestation_challenge.data,
                               attestation_challenge.data_length)) {
        return TranslateLastOpenSslError();
    }

    keymaster_blob_t attestation_app_id;
    if (!attestation_params.GetTagValue(TAG_ATTESTATION_APPLICATION_ID, &attestation_app_id)) {
        return KM_ERROR_ATTESTATION_APPLICATION_ID_MISSING;
    }
    sw_enforced.push_back(TAG_ATTESTATION_APPLICATION_ID, attestation_app_id);

    error = context.VerifyAndCopyDeviceIds(
        attestation_params,
        context.GetSecurityLevel() == KM_SECURITY_LEVEL_SOFTWARE ? &sw_enforced : &tee_enforced);
    if (error == KM_ERROR_UNIMPLEMENTED) {
        // The KeymasterContext implementation does not support device ID attestation. Bail out if
        // device ID attestation is being attempted.
        for (const auto& tag : kDeviceAttestationTags) {
            if (attestation_params.find(tag) != -1) {
                return KM_ERROR_CANNOT_ATTEST_IDS;
            }
        }
    } else if (error != KM_ERROR_OK) {
        return error;
    }

    if (attestation_params.Contains(TAG_DEVICE_UNIQUE_ATTESTATION) &&
        context.GetSecurityLevel() == KM_SECURITY_LEVEL_STRONGBOX) {
        tee_enforced.push_back(TAG_DEVICE_UNIQUE_ATTESTATION);
    };

    error = build_auth_list(sw_enforced, key_desc->software_enforced);
    if (error != KM_ERROR_OK) return error;

    error = build_auth_list(tee_enforced, key_desc->tee_enforced);
    if (error != KM_ERROR_OK) return error;

    if (attestation_params.GetTagValue(TAG_INCLUDE_UNIQUE_ID)) {
        uint64_t creation_datetime;
        // Only check sw_enforced for TAG_CREATION_DATETIME, since it shouldn't be in tee_enforced,
        // since this implementation has no secure wall clock.
        if (!sw_enforced.GetTagValue(TAG_CREATION_DATETIME, &creation_datetime)) {
            LOG_E("Unique ID cannot be created without creation datetime");
            return KM_ERROR_INVALID_KEY_BLOB;
        }

        Buffer unique_id = context.GenerateUniqueId(
            creation_datetime, attestation_app_id,
            attestation_params.GetTagValue(TAG_RESET_SINCE_ID_ROTATION), &error);
        if (error != KM_ERROR_OK) return error;

        if (!ASN1_OCTET_STRING_set(key_desc->unique_id, unique_id.peek_read(),
                                   unique_id.available_read()))
            return TranslateLastOpenSslError();
    }

    int len = i2d_KM_KEY_DESCRIPTION(key_desc.get(), nullptr);
    if (len < 0) return TranslateLastOpenSslError();
    *asn1_key_desc_len = len;
    asn1_key_desc->reset(new (std::nothrow) uint8_t[*asn1_key_desc_len]);
    if (!asn1_key_desc->get()) return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    uint8_t* p = asn1_key_desc->get();
    len = i2d_KM_KEY_DESCRIPTION(key_desc.get(), &p);
    if (len < 0) return TranslateLastOpenSslError();

    return KM_ERROR_OK;
}

// Copy all enumerated values with the specified tag from stack to auth_list.
static bool get_repeated_enums(const ASN1_INTEGER_SET* stack, keymaster_tag_t tag,
                               AuthorizationSet* auth_list) {
    ASSERT_OR_RETURN_ERROR(keymaster_tag_get_type(tag) == KM_ENUM_REP, KM_ERROR_INVALID_TAG);
    for (size_t i = 0; i < sk_ASN1_INTEGER_num(stack); ++i) {
        if (!auth_list->push_back(
                keymaster_param_enum(tag, ASN1_INTEGER_get(sk_ASN1_INTEGER_value(stack, i)))))
            return false;
    }
    return true;
}

// Add the specified integer tag/value pair to auth_list.
template <keymaster_tag_type_t Type, keymaster_tag_t Tag, typename KeymasterEnum>
static bool get_enum(const ASN1_INTEGER* asn1_int, TypedEnumTag<Type, Tag, KeymasterEnum> tag,
                     AuthorizationSet* auth_list) {
    if (!asn1_int) return true;
    return auth_list->push_back(tag, static_cast<KeymasterEnum>(ASN1_INTEGER_get(asn1_int)));
}

// Add the specified ulong tag/value pair to auth_list.
static bool get_ulong(const ASN1_INTEGER* asn1_int, keymaster_tag_t tag,
                      AuthorizationSet* auth_list) {
    if (!asn1_int) return true;
    UniquePtr<BIGNUM, BIGNUM_Delete> bn(ASN1_INTEGER_to_BN(asn1_int, nullptr));
    if (!bn.get()) return false;
    uint64_t ulong = 0;
    BN_get_u64(bn.get(), &ulong);
    return auth_list->push_back(keymaster_param_long(tag, ulong));
}

// Extract the values from the specified ASN.1 record and place them in auth_list.
keymaster_error_t extract_auth_list(const KM_AUTH_LIST* record, AuthorizationSet* auth_list) {
    if (!record) return KM_ERROR_OK;

    // Purpose
    if (!get_repeated_enums(record->purpose, TAG_PURPOSE, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Algorithm
    if (!get_enum(record->algorithm, TAG_ALGORITHM, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Key size
    if (record->key_size &&
        !auth_list->push_back(TAG_KEY_SIZE, ASN1_INTEGER_get(record->key_size))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Block mode
    if (!get_repeated_enums(record->block_mode, TAG_BLOCK_MODE, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Digest
    if (!get_repeated_enums(record->digest, TAG_DIGEST, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Padding
    if (!get_repeated_enums(record->padding, TAG_PADDING, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Caller nonce
    if (record->caller_nonce && !auth_list->push_back(TAG_CALLER_NONCE)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Min mac length
    if (!get_ulong(record->min_mac_length, TAG_MIN_MAC_LENGTH, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // EC curve
    if (!get_enum(record->ec_curve, TAG_EC_CURVE, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // RSA public exponent
    if (!get_ulong(record->rsa_public_exponent, TAG_RSA_PUBLIC_EXPONENT, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Rsa Oaep Mgf Digest
    if (!get_repeated_enums(record->mgf_digest, TAG_RSA_OAEP_MGF_DIGEST, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Rollback resistance
    if (record->rollback_resistance && !auth_list->push_back(TAG_ROLLBACK_RESISTANCE)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Early boot only
    if (record->early_boot_only && !auth_list->push_back(TAG_EARLY_BOOT_ONLY)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Active date time
    if (!get_ulong(record->active_date_time, TAG_ACTIVE_DATETIME, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Origination expire date time
    if (!get_ulong(record->origination_expire_date_time, TAG_ORIGINATION_EXPIRE_DATETIME,
                   auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Usage Expire date time
    if (!get_ulong(record->usage_expire_date_time, TAG_USAGE_EXPIRE_DATETIME, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Usage count limit
    if (record->usage_count_limit &&
        !auth_list->push_back(TAG_USAGE_COUNT_LIMIT, ASN1_INTEGER_get(record->usage_count_limit))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // No auth required
    if (record->no_auth_required && !auth_list->push_back(TAG_NO_AUTH_REQUIRED)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // User auth type
    if (!get_enum(record->user_auth_type, TAG_USER_AUTH_TYPE, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Auth timeout
    if (record->auth_timeout &&
        !auth_list->push_back(TAG_AUTH_TIMEOUT, ASN1_INTEGER_get(record->auth_timeout))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Allow while on body
    if (record->allow_while_on_body && !auth_list->push_back(TAG_ALLOW_WHILE_ON_BODY)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // trusted user presence required
    if (record->trusted_user_presence_required &&
        !auth_list->push_back(TAG_TRUSTED_USER_PRESENCE_REQUIRED)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // trusted confirmation required
    if (record->trusted_confirmation_required &&
        !auth_list->push_back(TAG_TRUSTED_CONFIRMATION_REQUIRED)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Unlocked device required
    if (record->unlocked_device_required && !auth_list->push_back(TAG_UNLOCKED_DEVICE_REQUIRED)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // All applications
    if (record->all_applications && !auth_list->push_back(TAG_ALL_APPLICATIONS)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Application ID
    if (record->application_id &&
        !auth_list->push_back(TAG_APPLICATION_ID, record->application_id->data,
                              record->application_id->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Creation date time
    if (!get_ulong(record->creation_date_time, TAG_CREATION_DATETIME, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Origin
    if (!get_enum(record->origin, TAG_ORIGIN, auth_list)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Rollback resistant
    if (record->rollback_resistant && !auth_list->push_back(TAG_ROLLBACK_RESISTANT)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Root of trust
    if (record->root_of_trust) {
        KM_ROOT_OF_TRUST* rot = record->root_of_trust;
        if (!rot->verified_boot_key) return KM_ERROR_INVALID_KEY_BLOB;

        // Other root of trust fields are not mapped to auth set entries.
    }

    // OS Version
    if (record->os_version &&
        !auth_list->push_back(TAG_OS_VERSION, ASN1_INTEGER_get(record->os_version))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // OS Patch level
    if (record->os_patchlevel &&
        !auth_list->push_back(TAG_OS_PATCHLEVEL, ASN1_INTEGER_get(record->os_patchlevel))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // attestation application Id
    if (record->attestation_application_id &&
        !auth_list->push_back(TAG_ATTESTATION_APPLICATION_ID,
                              record->attestation_application_id->data,
                              record->attestation_application_id->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Brand name
    if (record->attestation_id_brand &&
        !auth_list->push_back(TAG_ATTESTATION_ID_BRAND, record->attestation_id_brand->data,
                              record->attestation_id_brand->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Device name
    if (record->attestation_id_device &&
        !auth_list->push_back(TAG_ATTESTATION_ID_DEVICE, record->attestation_id_device->data,
                              record->attestation_id_device->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Product name
    if (record->attestation_id_product &&
        !auth_list->push_back(TAG_ATTESTATION_ID_PRODUCT, record->attestation_id_product->data,
                              record->attestation_id_product->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Serial number
    if (record->attestation_id_serial &&
        !auth_list->push_back(TAG_ATTESTATION_ID_SERIAL, record->attestation_id_serial->data,
                              record->attestation_id_serial->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // IMEI
    if (record->attestation_id_imei &&
        !auth_list->push_back(TAG_ATTESTATION_ID_IMEI, record->attestation_id_imei->data,
                              record->attestation_id_imei->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // MEID
    if (record->attestation_id_meid &&
        !auth_list->push_back(TAG_ATTESTATION_ID_MEID, record->attestation_id_meid->data,
                              record->attestation_id_meid->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Manufacturer name
    if (record->attestation_id_manufacturer &&
        !auth_list->push_back(TAG_ATTESTATION_ID_MANUFACTURER,
                              record->attestation_id_manufacturer->data,
                              record->attestation_id_manufacturer->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Model name
    if (record->attestation_id_model &&
        !auth_list->push_back(TAG_ATTESTATION_ID_MODEL, record->attestation_id_model->data,
                              record->attestation_id_model->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // vendor patch level
    if (record->vendor_patchlevel &&
        !auth_list->push_back(TAG_VENDOR_PATCHLEVEL, ASN1_INTEGER_get(record->vendor_patchlevel))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // boot patch level
    if (record->boot_patch_level &&
        !auth_list->push_back(TAG_BOOT_PATCHLEVEL, ASN1_INTEGER_get(record->boot_patch_level))) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // device unique attestation
    if (record->device_unique_attestation && !auth_list->push_back(TAG_DEVICE_UNIQUE_ATTESTATION)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // identity credential key
    if (record->identity_credential_key && !auth_list->push_back(TAG_IDENTITY_CREDENTIAL_KEY)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    // Second IMEI
    if (record->attestation_id_second_imei &&
        !auth_list->push_back(TAG_ATTESTATION_ID_SECOND_IMEI,
                              record->attestation_id_second_imei->data,
                              record->attestation_id_second_imei->length)) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    return KM_ERROR_OK;
}

// Parse the DER-encoded attestation record, placing the results in keymaster_version,
// attestation_challenge, software_enforced, tee_enforced and unique_id.
keymaster_error_t parse_attestation_record(const uint8_t* asn1_key_desc, size_t asn1_key_desc_len,
                                           uint32_t* attestation_version,  //
                                           keymaster_security_level_t* attestation_security_level,
                                           uint32_t* keymaster_version,
                                           keymaster_security_level_t* keymaster_security_level,
                                           keymaster_blob_t* attestation_challenge,
                                           AuthorizationSet* software_enforced,
                                           AuthorizationSet* tee_enforced,
                                           keymaster_blob_t* unique_id) {
    const uint8_t* p = asn1_key_desc;
    UniquePtr<KM_KEY_DESCRIPTION, KM_KEY_DESCRIPTION_Delete> record(
        d2i_KM_KEY_DESCRIPTION(nullptr, &p, asn1_key_desc_len));
    if (!record.get()) return TranslateLastOpenSslError();

    *attestation_version = ASN1_INTEGER_get(record->attestation_version);
    *attestation_security_level = static_cast<keymaster_security_level_t>(
        ASN1_ENUMERATED_get(record->attestation_security_level));
    *keymaster_version = ASN1_INTEGER_get(record->keymaster_version);
    *keymaster_security_level = static_cast<keymaster_security_level_t>(
        ASN1_ENUMERATED_get(record->keymaster_security_level));

    attestation_challenge->data =
        dup_buffer(record->attestation_challenge->data, record->attestation_challenge->length);
    attestation_challenge->data_length = record->attestation_challenge->length;

    unique_id->data = dup_buffer(record->unique_id->data, record->unique_id->length);
    unique_id->data_length = record->unique_id->length;

    keymaster_error_t error = extract_auth_list(record->software_enforced, software_enforced);
    if (error != KM_ERROR_OK) return error;

    return extract_auth_list(record->tee_enforced, tee_enforced);
}

keymaster_error_t parse_root_of_trust(const uint8_t* asn1_key_desc, size_t asn1_key_desc_len,
                                      keymaster_blob_t* verified_boot_key,
                                      keymaster_verified_boot_t* verified_boot_state,
                                      bool* device_locked) {
    const uint8_t* p = asn1_key_desc;
    UniquePtr<KM_KEY_DESCRIPTION, KM_KEY_DESCRIPTION_Delete> record(
        d2i_KM_KEY_DESCRIPTION(nullptr, &p, asn1_key_desc_len));
    if (!record.get()) {
        return TranslateLastOpenSslError();
    }
    if (!record->tee_enforced) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (!record->tee_enforced->root_of_trust) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (!record->tee_enforced->root_of_trust->verified_boot_key) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    KM_ROOT_OF_TRUST* root_of_trust = record->tee_enforced->root_of_trust;
    verified_boot_key->data = dup_buffer(root_of_trust->verified_boot_key->data,
                                         root_of_trust->verified_boot_key->length);
    verified_boot_key->data_length = root_of_trust->verified_boot_key->length;
    *verified_boot_state = static_cast<keymaster_verified_boot_t>(
        ASN1_ENUMERATED_get(root_of_trust->verified_boot_state));
    *device_locked = root_of_trust->device_locked;
    return KM_ERROR_OK;
}

// Parse the EAT-encoded attestation record, placing the results in keymaster_version,
// attestation_challenge, software_enforced, tee_enforced and unique_id.
keymaster_error_t parse_eat_record(
    const uint8_t* eat_key_desc, size_t eat_key_desc_len, uint32_t* attestation_version,
    keymaster_security_level_t* attestation_security_level, uint32_t* keymaster_version,
    keymaster_security_level_t* keymaster_security_level, keymaster_blob_t* attestation_challenge,
    AuthorizationSet* software_enforced, AuthorizationSet* tee_enforced,
    keymaster_blob_t* unique_id, keymaster_blob_t* verified_boot_key,
    keymaster_verified_boot_t* verified_boot_state, bool* device_locked,
    std::vector<int64_t>* unexpected_claims) {
    auto [top_level_item, next_pos, error] = cppbor::parse(eat_key_desc, eat_key_desc_len);
    ASSERT_OR_RETURN_ERROR(top_level_item, KM_ERROR_INVALID_TAG);
    const cppbor::Map* eat_map = top_level_item->asMap();
    ASSERT_OR_RETURN_ERROR(eat_map, KM_ERROR_INVALID_TAG);
    bool verified_or_self_signed = false;

    for (size_t i = 0; i < eat_map->size(); i++) {
        auto& [key_item, value_item] = (*eat_map)[i];
        const cppbor::Int* key = key_item->asInt();
        ASSERT_OR_RETURN_ERROR(key, (KM_ERROR_INVALID_TAG));

        // The following values will either hold the typed value, or be null (if not the right
        // type).
        const cppbor::Int* int_value = value_item->asInt();
        const cppbor::Bstr* bstr_value = value_item->asBstr();
        const cppbor::Simple* simple_value = value_item->asSimple();
        const cppbor::Array* array_value = value_item->asArray();
        const cppbor::Map* map_value = value_item->asMap();

        keymaster_error_t error;
        switch ((EatClaim)key->value()) {
        default:
            unexpected_claims->push_back(key->value());
            break;
        case EatClaim::ATTESTATION_VERSION:
            ASSERT_OR_RETURN_ERROR(int_value, KM_ERROR_INVALID_TAG);
            *attestation_version = int_value->value();
            break;
        case EatClaim::SECURITY_LEVEL:
            ASSERT_OR_RETURN_ERROR(int_value, KM_ERROR_INVALID_TAG);
            switch ((EatSecurityLevel)int_value->value()) {
            // TODO: Is my assumption correct that the security level of the attestation data should
            // always be equal to the security level of keymint, as the attestation data always
            // lives in the top-level module?
            case EatSecurityLevel::UNRESTRICTED:
                *keymaster_security_level = *attestation_security_level =
                    KM_SECURITY_LEVEL_SOFTWARE;
                break;
            case EatSecurityLevel::SECURE_RESTRICTED:
                *keymaster_security_level = *attestation_security_level =
                    KM_SECURITY_LEVEL_TRUSTED_ENVIRONMENT;
                break;
            case EatSecurityLevel::HARDWARE:
                *keymaster_security_level = *attestation_security_level =
                    KM_SECURITY_LEVEL_STRONGBOX;
                break;
            default:
                return KM_ERROR_INVALID_TAG;
            }
            break;
        case EatClaim::KEYMASTER_VERSION:
            ASSERT_OR_RETURN_ERROR(int_value, KM_ERROR_INVALID_TAG);
            *keymaster_version = int_value->value();
            break;
        case EatClaim::SUBMODS:
            ASSERT_OR_RETURN_ERROR(map_value, KM_ERROR_INVALID_TAG);
            for (size_t j = 0; j < map_value->size(); j++) {
                auto& [submod_key, submod_value] = (*map_value)[j];
                const cppbor::Map* submod_map = submod_value->asMap();
                ASSERT_OR_RETURN_ERROR(submod_map, KM_ERROR_INVALID_TAG);
                error = parse_eat_submod(submod_map, software_enforced, tee_enforced);
                if (error != KM_ERROR_OK) return error;
            }
            break;
        case EatClaim::CTI:
            error = bstr_to_blob(bstr_value, unique_id);
            if (error != KM_ERROR_OK) return error;
            break;
        case EatClaim::NONCE:
            error = bstr_to_blob(bstr_value, attestation_challenge);
            if (error != KM_ERROR_OK) return error;
            break;
        case EatClaim::VERIFIED_BOOT_KEY:
            error = bstr_to_blob(bstr_value, verified_boot_key);
            if (error != KM_ERROR_OK) return error;
            break;
        case EatClaim::VERIFIED_BOOT_HASH:
            // Not parsing this for now.
            break;
        case EatClaim::DEVICE_UNIQUE_ATTESTATION:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            // Not parsing this for now.
            break;
        case EatClaim::DEVICE_LOCKED:
            ASSERT_OR_RETURN_ERROR(simple_value->asBool(), KM_ERROR_INVALID_TAG);
            *device_locked = simple_value->asBool()->value();
            break;
        case EatClaim::BOOT_STATE:
            ASSERT_OR_RETURN_ERROR(array_value, KM_ERROR_INVALID_TAG);
            ASSERT_OR_RETURN_ERROR(array_value->size() == 5, KM_ERROR_INVALID_TAG);
            ASSERT_OR_RETURN_ERROR((*array_value)[4]->asSimple()->asBool()->value() == false,
                                   KM_ERROR_INVALID_TAG);
            verified_or_self_signed = (*array_value)[0]->asSimple()->asBool()->value();
            ASSERT_OR_RETURN_ERROR(verified_or_self_signed ==
                                       (*array_value)[1]->asSimple()->asBool()->value(),
                                   KM_ERROR_INVALID_TAG);
            ASSERT_OR_RETURN_ERROR(verified_or_self_signed ==
                                       (*array_value)[2]->asSimple()->asBool()->value(),
                                   KM_ERROR_INVALID_TAG);
            ASSERT_OR_RETURN_ERROR(verified_or_self_signed ==
                                       (*array_value)[3]->asSimple()->asBool()->value(),
                                   KM_ERROR_INVALID_TAG);
            break;
        case EatClaim::OFFICIAL_BUILD:
            *verified_boot_state = KM_VERIFIED_BOOT_VERIFIED;
            break;
        }
    }

    if (*verified_boot_state == KM_VERIFIED_BOOT_VERIFIED) {
        (void)(verified_boot_state);
        // TODO: re-enable this
        // ASSERT_OR_RETURN_ERROR(verified_or_self_signed, KM_ERROR_INVALID_TAG);
    } else {
        *verified_boot_state =
            verified_or_self_signed ? KM_VERIFIED_BOOT_SELF_SIGNED : KM_VERIFIED_BOOT_UNVERIFIED;
    }

    return KM_ERROR_OK;
}

keymaster_error_t parse_submod_values(AuthorizationSetBuilder* set_builder,
                                      int* auth_set_security_level, const cppbor::Map* submod_map) {
    ASSERT_OR_RETURN_ERROR(set_builder, KM_ERROR_UNEXPECTED_NULL_POINTER);
    for (size_t i = 0; i < submod_map->size(); i++) {
        auto& [key_item, value_item] = (*submod_map)[i];
        const cppbor::Int* key_int = key_item->asInt();
        ASSERT_OR_RETURN_ERROR(key_int, KM_ERROR_INVALID_TAG);
        int key = key_int->value();
        keymaster_error_t error;
        keymaster_blob_t blob;

        switch ((EatClaim)key) {
        default:
            return KM_ERROR_INVALID_TAG;
        case EatClaim::ALGORITHM:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(
                TAG_ALGORITHM, static_cast<keymaster_algorithm_t>(value_item->asInt()->value()));
            break;
        case EatClaim::EC_CURVE:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(
                TAG_EC_CURVE, static_cast<keymaster_ec_curve_t>(value_item->asInt()->value()));
            break;
        case EatClaim::USER_AUTH_TYPE:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_USER_AUTH_TYPE, static_cast<hw_authenticator_type_t>(
                                                               value_item->asInt()->value()));
            break;
        case EatClaim::ORIGIN:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(
                TAG_ORIGIN, static_cast<keymaster_key_origin_t>(value_item->asInt()->value()));
            break;
        case EatClaim::PURPOSE:
            for (size_t j = 0; j < value_item->asArray()->size(); j++) {
                set_builder->Authorization(TAG_PURPOSE,
                                           static_cast<keymaster_purpose_t>(
                                               (*value_item->asArray())[j]->asInt()->value()));
            }
            break;
        case EatClaim::PADDING:
            for (size_t j = 0; j < value_item->asArray()->size(); j++) {
                set_builder->Authorization(TAG_PADDING,
                                           static_cast<keymaster_padding_t>(
                                               (*value_item->asArray())[j]->asInt()->value()));
            }
            break;
        case EatClaim::DIGEST:
            for (size_t j = 0; j < value_item->asArray()->size(); j++) {
                set_builder->Authorization(
                    TAG_DIGEST,
                    static_cast<keymaster_digest_t>((*value_item->asArray())[j]->asInt()->value()));
            }
            break;
        case EatClaim::BLOCK_MODE:
            for (size_t j = 0; j < value_item->asArray()->size(); j++) {
                set_builder->Authorization(TAG_BLOCK_MODE,
                                           static_cast<keymaster_block_mode_t>(
                                               (*value_item->asArray())[j]->asInt()->value()));
            }
            break;
        case EatClaim::KEY_SIZE:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_KEY_SIZE, value_item->asInt()->value());
            break;
        case EatClaim::AUTH_TIMEOUT:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_AUTH_TIMEOUT, value_item->asInt()->value());
            break;
        case EatClaim::OS_VERSION:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_OS_VERSION, value_item->asInt()->value());
            break;
        case EatClaim::OS_PATCHLEVEL:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_OS_PATCHLEVEL, value_item->asInt()->value());
            break;
        case EatClaim::MIN_MAC_LENGTH:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_MIN_MAC_LENGTH, value_item->asInt()->value());
            break;
        case EatClaim::BOOT_PATCHLEVEL:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_BOOT_PATCHLEVEL, value_item->asInt()->value());
            break;
        case EatClaim::VENDOR_PATCHLEVEL:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_VENDOR_PATCHLEVEL, value_item->asInt()->value());
            break;
        case EatClaim::RSA_PUBLIC_EXPONENT:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_RSA_PUBLIC_EXPONENT, value_item->asInt()->value());
            break;
        case EatClaim::ACTIVE_DATETIME:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_ACTIVE_DATETIME, value_item->asInt()->value());
            break;
        case EatClaim::ORIGINATION_EXPIRE_DATETIME:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_ORIGINATION_EXPIRE_DATETIME,
                                       value_item->asInt()->value());
            break;
        case EatClaim::USAGE_EXPIRE_DATETIME:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_USAGE_EXPIRE_DATETIME, value_item->asInt()->value());
            break;
        case EatClaim::IAT:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            set_builder->Authorization(TAG_CREATION_DATETIME, value_item->asInt()->value());
            break;
        case EatClaim::NO_AUTH_REQUIRED:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_NO_AUTH_REQUIRED);
            break;
        case EatClaim::ALL_APPLICATIONS:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_ALL_APPLICATIONS);
            break;
        case EatClaim::ROLLBACK_RESISTANT:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_ROLLBACK_RESISTANT);
            break;
        case EatClaim::ALLOW_WHILE_ON_BODY:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_ALLOW_WHILE_ON_BODY);
            break;
        case EatClaim::UNLOCKED_DEVICE_REQUIRED:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_UNLOCKED_DEVICE_REQUIRED);
            break;
        case EatClaim::CALLER_NONCE:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_CALLER_NONCE);
            break;
        case EatClaim::TRUSTED_CONFIRMATION_REQUIRED:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_TRUSTED_CONFIRMATION_REQUIRED);
            break;
        case EatClaim::EARLY_BOOT_ONLY:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_EARLY_BOOT_ONLY);
            break;
        case EatClaim::IDENTITY_CREDENTIAL_KEY:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_IDENTITY_CREDENTIAL_KEY);
            break;
        case EatClaim::STORAGE_KEY:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_STORAGE_KEY);
            break;
        case EatClaim::TRUSTED_USER_PRESENCE_REQUIRED:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_TRUSTED_USER_PRESENCE_REQUIRED);
            break;
        case EatClaim::DEVICE_UNIQUE_ATTESTATION:
            if (value_item->asSimple() == nullptr || !value_item->asSimple()->asBool()->value()) {
                return KM_ERROR_INVALID_TAG;
            }
            set_builder->Authorization(TAG_DEVICE_UNIQUE_ATTESTATION);
            break;
        case EatClaim::APPLICATION_ID:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_APPLICATION_ID, blob);
            break;
        case EatClaim::ATTESTATION_APPLICATION_ID:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_APPLICATION_ID, blob);
            break;
        case EatClaim::ATTESTATION_ID_BRAND:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_BRAND, blob);
            break;
        case EatClaim::ATTESTATION_ID_DEVICE:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_DEVICE, blob);
            break;
        case EatClaim::ATTESTATION_ID_PRODUCT:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_PRODUCT, blob);
            break;
        case EatClaim::ATTESTATION_ID_SERIAL:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_SERIAL, blob);
            break;
        case EatClaim::UEID:
            error = ueid_to_imei_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_IMEI, blob);
            break;
        case EatClaim::ATTESTATION_ID_MEID:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_MEID, blob);
            break;
        case EatClaim::ATTESTATION_ID_MANUFACTURER:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_MANUFACTURER, blob);
            break;
        case EatClaim::ATTESTATION_ID_MODEL:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_ATTESTATION_ID_MODEL, blob);
            break;
        case EatClaim::CONFIRMATION_TOKEN:
            error = bstr_to_blob(value_item->asBstr(), &blob);
            if (error != KM_ERROR_OK) return error;
            set_builder->Authorization(TAG_CONFIRMATION_TOKEN, blob);
            break;
        case EatClaim::SECURITY_LEVEL:
            ASSERT_OR_RETURN_ERROR(value_item->asInt(), KM_ERROR_INVALID_TAG);
            *auth_set_security_level = value_item->asInt()->value();
        }
    }

    return KM_ERROR_OK;
}

keymaster_error_t parse_eat_submod(const cppbor::Map* submod_values,
                                   AuthorizationSet* software_enforced,
                                   AuthorizationSet* tee_enforced) {
    AuthorizationSetBuilder auth_set_builder;
    int auth_set_security_level = 0;
    keymaster_error_t error =
        parse_submod_values(&auth_set_builder, &auth_set_security_level, submod_values);
    if (error) return error;
    switch ((EatSecurityLevel)auth_set_security_level) {
    case EatSecurityLevel::HARDWARE:
        // Hardware attestation should never occur in a submod of another EAT.
        [[fallthrough]];
    default:
        return KM_ERROR_INVALID_TAG;
    case EatSecurityLevel::UNRESTRICTED:
        *software_enforced = AuthorizationSet(auth_set_builder);
        break;
    case EatSecurityLevel::SECURE_RESTRICTED:
        *tee_enforced = AuthorizationSet(auth_set_builder);
        break;
    }

    return KM_ERROR_OK;
}
}  // namespace keymaster
