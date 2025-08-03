/*
 * Copyright 2017 The Android Open Source Project
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

#include "secure_storage_manager.h"
#include "keymaster_attributes.pb.h"

#include <errno.h>
#include <stdio.h>
#include <uapi/err.h>

#include <lib/storage/storage.h>

#include <keymaster/UniquePtr.h>
#include <keymaster/android_keymaster_utils.h>
#include "pb_decode.h"
#include "pb_encode.h"
#include "trusty_logger.h"

namespace keymaster {

// Name of the attestation key file is kAttestKeyCertPrefix.%algorithm. This key
// file stores key and certificate chain in a protobuf format.
const char* kAttestKeyCertPrefix = "AttestKeyCert";
// Name of the legacy attestation key file prefix.
const char* kLegacyAttestKeyPrefix = "AttestKey.";
// Name of the legacy certificate file prefix.
const char* kLegacyAttestCertPrefix = "AttestCert.";

// Name of the file to store keymaster attributes in a protobuf format.
const char* kAttributeFileName = "Attribute";
// Name of the legacy keymaster attribute files.
const char* kLegacyAttestUuidFileName = "AttestUuid";
const char* kLegacyProductIdFileName = "ProductId";

// Name of the file to store attestation IDs in a protobuf format.
const char* kAttestationIdsFileName = "AttestationIds";

// Maximum file name size.
static const int kStorageIdLengthMax = 64;

// Maximum length for an individual attestation ID field.
static const int kAttestationIdLengthMax = 64;

// These values should match keymaster_attributes.proto descriptions.
static const int kKeySizeMax = 2048;
static const int kCertSizeMax = 2048;

const char* GetKeySlotStr(AttestationKeySlot key_slot) {
    switch (key_slot) {
    case AttestationKeySlot::kRsa:
        return "rsa";
    case AttestationKeySlot::kEcdsa:
        return "ec";
    case AttestationKeySlot::kEddsa:
        return "ed";
    case AttestationKeySlot::kEpid:
        return "epid";
    case AttestationKeySlot::kClaimable0:
        return "c0";
    case AttestationKeySlot::kSomRsa:
        return "s_rsa";
    case AttestationKeySlot::kSomEcdsa:
        return "s_ec";
    case AttestationKeySlot::kSomEddsa:
        return "s_ed";
    case AttestationKeySlot::kSomEpid:
        return "s_epid";
    default:
        return "";
    }
}

class FileCloser {
public:
    file_handle_t get_file_handle() { return file_handle; }
    int open_file(storage_session_t session,
                  const char* name,
                  uint32_t flags,
                  uint32_t opflags) {
        return storage_open_file(session, &file_handle, name, flags, opflags);
    }
    ~FileCloser() {
        if (file_handle) {
            storage_close_file(file_handle);
        }
    }

private:
    file_handle_t file_handle = 0;
};

SecureStorageManager* SecureStorageManager::get_instance(
        bool translate_format) {
    static SecureStorageManager instance;
    if (instance.session_handle_ != STORAGE_INVALID_SESSION) {
        int rc = storage_end_transaction(instance.session_handle_, false);
        if (rc < 0) {
            LOG_E("Error: existing session is stale.");
            storage_close_session(instance.session_handle_);
            instance.session_handle_ = STORAGE_INVALID_SESSION;
        }
    }
    if (instance.session_handle_ == STORAGE_INVALID_SESSION) {
        storage_open_session(&instance.session_handle_, STORAGE_CLIENT_TP_PORT);
        if (instance.session_handle_ == STORAGE_INVALID_SESSION) {
            return nullptr;
        }
    }
#ifdef KEYMASTER_LEGACY_FORMAT
    if (translate_format && instance.legacy_format) {
        keymaster_error_t err = instance.TranslateLegacyFormat();
        if (err != KM_ERROR_OK) {
            LOG_E("Failed to translate legacy file format!");
            instance.CloseSession();
            return nullptr;
        } else {
            instance.legacy_format = false;
        }
    }
#endif  // #ifdef KEYMASTER_LEGACY_FORMAT
    return &instance;
}

keymaster_error_t SecureStorageManager::WriteKeyToStorage(
        AttestationKeySlot key_slot,
        const uint8_t* key,
        uint32_t key_size) {
    if (key_size > kKeySizeMax) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    attestation_key->has_key = true;
    memcpy(attestation_key->key.bytes, key, key_size);
    attestation_key->key.size = key_size;

    err = WriteAttestationKey(key_slot, attestation_key.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

KeymasterKeyBlob SecureStorageManager::ReadKeyFromStorage(
        AttestationKeySlot key_slot,
        keymaster_error_t* error) {
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        if (error) {
            *error = err;
        }
        return {};
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    if (!attestation_key->has_key) {
        if (error) {
            *error = KM_ERROR_INVALID_ARGUMENT;
        }
        return {};
    }
    KeymasterKeyBlob result(attestation_key->key.size);
    if (result.key_material == nullptr) {
        if (error) {
            *error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        return {};
    }
    memcpy(result.writable_data(), attestation_key->key.bytes,
           result.key_material_size);
    return result;
}

keymaster_error_t SecureStorageManager::AttestationKeyExists(
        AttestationKeySlot key_slot,
        bool* exists) {
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    *exists = attestation_key->has_key;
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::WriteCertToStorage(
        AttestationKeySlot key_slot,
        const uint8_t* cert,
        uint32_t cert_size,
        uint32_t index) {
    if (cert_size > kCertSizeMax || index >= kMaxCertChainLength) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    if (attestation_key->certs_count < index) {
        /* Skip a layer in cert chain. */
        return KM_ERROR_INVALID_ARGUMENT;
    } else if (attestation_key->certs_count == index) {
        attestation_key->certs_count++;
    }
    attestation_key->certs[index].content.size = cert_size;
    memcpy(attestation_key->certs[index].content.bytes, cert, cert_size);

    err = WriteAttestationKey(key_slot, attestation_key.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::ReadCertChainFromStorage(
        AttestationKeySlot key_slot,
        keymaster_cert_chain_t* cert_chain) {
    AttestationKey* attestation_key_p;
    // Clear entry count in case of early return.
    cert_chain->entry_count = 0;
    cert_chain->entries = nullptr;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    uint32_t cert_chain_length = attestation_key->certs_count;
    cert_chain->entry_count = cert_chain_length;
    if (cert_chain_length == 0) {
        return KM_ERROR_OK;
    }

    cert_chain->entries =
            new (std::nothrow) keymaster_blob_t[cert_chain_length];
    if (cert_chain->entries == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    memset(cert_chain->entries, 0,
           sizeof(keymaster_blob_t) * cert_chain_length);

    for (size_t i = 0; i < cert_chain_length; i++) {
        uint32_t content_size = attestation_key->certs[i].content.size;
        cert_chain->entries[i].data_length = content_size;
        uint8_t* buffer = new (std::nothrow) uint8_t[content_size];
        if (buffer == nullptr) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        memcpy(buffer, attestation_key->certs[i].content.bytes, content_size);
        cert_chain->entries[i].data = buffer;
    }

    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::DeleteCertChainFromStorage(
        AttestationKeySlot key_slot) {
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    attestation_key->certs_count = 0;

    err = WriteAttestationKey(key_slot, attestation_key.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::ReadCertChainLength(
        AttestationKeySlot key_slot,
        uint32_t* cert_chain_length) {
    AttestationKey* attestation_key_p;
    keymaster_error_t err = ReadAttestationKey(key_slot, &attestation_key_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<AttestationKey> attestation_key(attestation_key_p);
    *cert_chain_length = attestation_key->certs_count;
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::DeleteKey(AttestationKeySlot key_slot,
                                                  bool commit) {
    char key_file[kStorageIdLengthMax];
    snprintf(key_file, kStorageIdLengthMax, "%s.%s", kAttestKeyCertPrefix,
             GetKeySlotStr(key_slot));
    int rc = storage_delete_file(session_handle_, key_file,
                                 commit ? STORAGE_OP_COMPLETE : 0);
    if (rc < 0 && rc != ERR_NOT_FOUND) {
        LOG_E("Error: [%d] deleting storage object '%s'", rc, key_file);
        if (commit) {
            // If DeleteKey is part of a larger operations, then do not close
            // the session.
            CloseSession();
        }
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::ReadKeymasterAttributes(
        KeymasterAttributes** km_attributes_p) {
    UniquePtr<KeymasterAttributes> km_attributes(new (
            std::nothrow) KeymasterAttributes(KeymasterAttributes_init_zero));
    if (!km_attributes.get()) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    keymaster_error_t err =
            DecodeFromFile(KeymasterAttributes_fields, km_attributes.get(),
                           kAttributeFileName);
    if (err < 0) {
        LOG_E("Error: [%d] decoding from file '%s'", err, kAttributeFileName);
        return err;
    }
    *km_attributes_p = km_attributes.release();
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::WriteKeymasterAttributes(
        const KeymasterAttributes* km_attributes,
        bool commit) {
    return EncodeToFile(KeymasterAttributes_fields, km_attributes,
                        kAttributeFileName, commit);
}

keymaster_error_t SecureStorageManager::WriteAttestationIds(
        const AttestationIds* attestation_ids,
        bool commit) {
    return EncodeToFile(AttestationIds_fields, attestation_ids,
                        kAttestationIdsFileName, commit);
}

keymaster_error_t SecureStorageManager::ReadAttestationUuid(
        uint8_t attestation_uuid[kAttestationUuidSize]) {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
    if (!(km_attributes->has_uuid)) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (km_attributes->uuid.size != kAttestationUuidSize) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    memcpy(attestation_uuid, km_attributes->uuid.bytes, kAttestationUuidSize);
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::WriteAttestationUuid(
        const uint8_t attestation_uuid[kAttestationUuidSize]) {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
    km_attributes->has_uuid = true;
    km_attributes->uuid.size = kAttestationUuidSize;
    memcpy(km_attributes->uuid.bytes, attestation_uuid, kAttestationUuidSize);
    err = WriteKeymasterAttributes(km_attributes.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::DeleteAttestationUuid() {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
    km_attributes->has_uuid = false;
    err = WriteKeymasterAttributes(km_attributes.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::SetProductId(
        const uint8_t product_id[kProductIdSize]) {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
#ifndef KEYMASTER_DEBUG
    if (km_attributes->has_product_id) {
        LOG_E("Error: Product ID already set!\n");
        return KM_ERROR_INVALID_ARGUMENT;
    }
#endif /* KEYMASTER_DEBUG */
    km_attributes->has_product_id = true;
    km_attributes->product_id.size = kProductIdSize;
    memcpy(km_attributes->product_id.bytes, product_id, kProductIdSize);
    err = WriteKeymasterAttributes(km_attributes.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::ReadProductId(
        uint8_t product_id[kProductIdSize]) {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
    if (!km_attributes->has_product_id) {
        return KM_ERROR_INVALID_ARGUMENT;
    }
    if (km_attributes->product_id.size != kProductIdSize) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    memcpy(product_id, km_attributes->product_id.bytes, kProductIdSize);
    return KM_ERROR_OK;
}

struct AttestationIdResult {
    keymaster_error_t error;
    UniquePtr<AttestationIds> ids;
};

static struct AttestationIdResult ValidateAndSetBaseAttestationIds(
        const SetAttestationIdsRequest& request) {
    AttestationIds* attestation_ids_p =
            new (std::nothrow) AttestationIds(AttestationIds_init_zero);
    if (attestation_ids_p == nullptr) {
        return {KM_ERROR_MEMORY_ALLOCATION_FAILED, nullptr};
    }
    UniquePtr<AttestationIds> attestation_ids(attestation_ids_p);
    if (request.brand.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Brand ID too large: %zu", request.brand.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.brand.buffer_size() > 0) {
        attestation_ids->has_brand = true;
        attestation_ids->brand.size = request.brand.buffer_size();
        memcpy(attestation_ids->brand.bytes, request.brand.begin(),
               request.brand.buffer_size());
    }

    if (request.device.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Device ID too large: %zu", request.device.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.device.buffer_size() > 0) {
        attestation_ids->has_device = true;
        attestation_ids->device.size = request.device.buffer_size();
        memcpy(attestation_ids->device.bytes, request.device.begin(),
               request.device.buffer_size());
    }

    if (request.product.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Product ID too large: %zu",
              request.product.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.product.buffer_size() > 0) {
        attestation_ids->has_product = true;
        attestation_ids->product.size = request.product.buffer_size();
        memcpy(attestation_ids->product.bytes, request.product.begin(),
               request.product.buffer_size());
    }

    if (request.serial.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Serial number too large: %zu",
              request.serial.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.serial.buffer_size() > 0) {
        attestation_ids->has_serial = true;
        attestation_ids->serial.size = request.serial.buffer_size();
        memcpy(attestation_ids->serial.bytes, request.serial.begin(),
               request.serial.buffer_size());
    }

    if (request.imei.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: IMEI ID too large: %zu", request.imei.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.imei.buffer_size() > 0) {
        attestation_ids->has_imei = true;
        attestation_ids->imei.size = request.imei.buffer_size();
        memcpy(attestation_ids->imei.bytes, request.imei.begin(),
               request.imei.buffer_size());
    }

    if (request.meid.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: MEID ID too large: %zu", request.meid.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.meid.buffer_size() > 0) {
        attestation_ids->has_meid = true;
        attestation_ids->meid.size = request.meid.buffer_size();
        memcpy(attestation_ids->meid.bytes, request.meid.begin(),
               request.meid.buffer_size());
    }

    if (request.manufacturer.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Manufacturer ID too large: %zu",
              request.manufacturer.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.manufacturer.buffer_size() > 0) {
        attestation_ids->has_manufacturer = true;
        attestation_ids->manufacturer.size = request.manufacturer.buffer_size();
        memcpy(attestation_ids->manufacturer.bytes,
               request.manufacturer.begin(),
               request.manufacturer.buffer_size());
    }

    if (request.model.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Model ID too large: %zu", request.model.buffer_size());
        return {KM_ERROR_INVALID_ARGUMENT, nullptr};
    } else if (request.model.buffer_size() > 0) {
        attestation_ids->has_model = true;
        attestation_ids->model.size = request.model.buffer_size();
        memcpy(attestation_ids->model.bytes, request.model.begin(),
               request.model.buffer_size());
    }
    return {KM_ERROR_OK, std::move(attestation_ids)};
}

keymaster_error_t SecureStorageManager::SetAttestationIdsKM3(
        const SetAttestationIdsKM3Request& request) {
    auto result = ValidateAndSetBaseAttestationIds(request.base);
    if (result.error != KM_ERROR_OK) {
        return result.error;
    }
    if (request.second_imei.buffer_size() > kAttestationIdLengthMax) {
        LOG_E("Error: Second IMEI ID too large: %zu",
              request.second_imei.buffer_size());
        return KM_ERROR_INVALID_ARGUMENT;
    } else if (request.second_imei.buffer_size() > 0) {
        result.ids->has_second_imei = true;
        result.ids->second_imei.size = request.second_imei.buffer_size();
        memcpy(result.ids->second_imei.bytes, request.second_imei.begin(),
               request.second_imei.buffer_size());
    }

    keymaster_error_t err = WriteAttestationIds(result.ids.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::ClearAttestationIds() {
    int rc = storage_delete_file(session_handle_, kAttestationIdsFileName,
                                 STORAGE_OP_COMPLETE);
    if (rc < 0 && rc != ERR_NOT_FOUND) {
        LOG_E("Error: [%d] deleting attestation IDs file", rc);
        CloseSession();
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    return KM_ERROR_UNIMPLEMENTED;
}

keymaster_error_t SecureStorageManager::SetAttestationIds(
        const SetAttestationIdsRequest& request) {
    auto result = ValidateAndSetBaseAttestationIds(request);
    if (result.error != KM_ERROR_OK) {
        return result.error;
    }
    keymaster_error_t err = WriteAttestationIds(result.ids.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::ReadAttestationIds(
        AttestationIds* attestation_ids_p) {
    *attestation_ids_p = AttestationIds_init_zero;
    keymaster_error_t err = DecodeFromFile(
            AttestationIds_fields, attestation_ids_p, kAttestationIdsFileName);
    if (err < 0) {
        LOG_E("Error: [%d] decoding from file '%s'", err,
              kAttestationIdsFileName);
        CloseSession();
        return err;
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::DeleteProductId() {
    KeymasterAttributes* km_attributes_p;
    keymaster_error_t err = ReadKeymasterAttributes(&km_attributes_p);
    if (err != KM_ERROR_OK) {
        CloseSession();
        return err;
    }
    UniquePtr<KeymasterAttributes> km_attributes(km_attributes_p);
    km_attributes_p->has_product_id = false;
    err = WriteKeymasterAttributes(km_attributes.get(), true);
    if (err != KM_ERROR_OK) {
        CloseSession();
    }
    return err;
}

keymaster_error_t SecureStorageManager::DeleteAllAttestationData() {
    if (DeleteKey(AttestationKeySlot::kRsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kEcdsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kEddsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kEpid, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kClaimable0, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kSomRsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kSomEcdsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kSomEddsa, false) != KM_ERROR_OK ||
        DeleteKey(AttestationKeySlot::kSomEpid, false) != KM_ERROR_OK) {
        // Something wrong, abort the transaction.
        storage_end_transaction(session_handle_, false);
        CloseSession();
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    int rc = storage_end_transaction(session_handle_, true);
    if (rc < 0) {
        LOG_E("Error: failed to commit transaction while deleting keys.");
        CloseSession();
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::ReadAttestationKey(
        AttestationKeySlot key_slot,
        AttestationKey** attestation_key_p) {
    char key_file[kStorageIdLengthMax];
    snprintf(key_file, kStorageIdLengthMax, "%s.%s", kAttestKeyCertPrefix,
             GetKeySlotStr(key_slot));

    UniquePtr<AttestationKey> attestation_key(
            new (std::nothrow) AttestationKey(AttestationKey_init_zero));
    if (!attestation_key.get()) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    keymaster_error_t err = DecodeFromFile(AttestationKey_fields,
                                           attestation_key.get(), key_file);
    if (err < 0) {
        LOG_E("Error: [%d] decoding from file '%s'", err, key_file);
        return err;
    }
    *attestation_key_p = attestation_key.release();
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::WriteAttestationKey(
        AttestationKeySlot key_slot,
        const AttestationKey* attestation_key,
        bool commit) {
    char key_file[kStorageIdLengthMax];
    snprintf(key_file, kStorageIdLengthMax, "%s.%s", kAttestKeyCertPrefix,
             GetKeySlotStr(key_slot));

    return EncodeToFile(AttestationKey_fields, attestation_key, key_file,
                        commit);
}

void SecureStorageManager::CloseSession() {
    if (session_handle_ != STORAGE_INVALID_SESSION) {
        storage_close_session(session_handle_);
        session_handle_ = STORAGE_INVALID_SESSION;
    }
}

struct FileStatus {
    /* How many bytes handled in the file. */
    uint64_t bytes_handled;
    file_handle_t file_handle;
    FileStatus() : bytes_handled(0), file_handle(0) {}
};

bool write_to_file_callback(pb_ostream_t* stream,
                            const uint8_t* buf,
                            size_t count) {
    FileStatus* file_status = reinterpret_cast<FileStatus*>(stream->state);
    /* Do not commit the write. */
    int rc = storage_write(file_status->file_handle, file_status->bytes_handled,
                           buf, count, 0);
    if (rc < 0 || static_cast<size_t>(rc) < count) {
        LOG_E("Error: failed to write to file: %d\n", rc);
        return false;
    }
    file_status->bytes_handled += rc;
    return true;
}

bool read_from_file_callback(pb_istream_t* stream, uint8_t* buf, size_t count) {
    if (buf == NULL) {
        return false;
    }
    FileStatus* file_status = reinterpret_cast<FileStatus*>(stream->state);
    int rc = storage_read(file_status->file_handle, file_status->bytes_handled,
                          buf, count);
    if (rc < 0 || static_cast<size_t>(rc) < count) {
        LOG_E("Error: failed to read from file: %d\n", rc);
        return false;
    }
    file_status->bytes_handled += rc;
    return true;
}

keymaster_error_t SecureStorageManager::EncodeToFile(const pb_field_t fields[],
                                                     const void* dest_struct,
                                                     const char filename[],
                                                     bool commit) {
    FileCloser file;
    int rc = file.open_file(
            session_handle_, filename,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    if (rc < 0) {
        LOG_E("Error: failed to open file '%s': %d\n", filename, rc);
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    FileStatus new_file_status;
    new_file_status.file_handle = file.get_file_handle();
    pb_ostream_t stream = {&write_to_file_callback, &new_file_status, SIZE_MAX,
                           0, 0};
    if (!pb_encode(&stream, fields, dest_struct)) {
        LOG_E("Error: encoding fields to file '%s'", filename);
        /* Abort the transaction. */
        storage_end_transaction(session_handle_, false);
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    if (commit) {
        /* Commit the write. */
        rc = storage_end_transaction(session_handle_, true);
        if (rc < 0) {
            LOG_E("Error: failed to commit write transaction for file '%s': %d"
                  "\n",
                  filename, rc);
            return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        }
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::DecodeFromFile(
        const pb_field_t fields[],
        void* dest_struct,
        const char filename[]) {
    uint64_t file_size;
    FileCloser file;
    int rc = file.open_file(session_handle_, filename, 0, 0);
    if (rc == ERR_NOT_FOUND) {
        // File not exists
        return KM_ERROR_OK;
    }
    if (rc < 0) {
        LOG_E("Error: failed to open file '%s': %d\n", filename, rc);
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    rc = storage_get_file_size(file.get_file_handle(), &file_size);
    if (rc < 0) {
        LOG_E("Error: failed to get size of attributes file '%s': %d\n",
              filename, rc);
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    FileStatus new_file_status;
    new_file_status.file_handle = file.get_file_handle();
    pb_istream_t stream = {&read_from_file_callback, &new_file_status,
                           static_cast<size_t>(file_size), 0};
    if (!pb_decode(&stream, fields, dest_struct)) {
        LOG_E("Error: decoding fields from file '%s'", filename);
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }
    return KM_ERROR_OK;
}

#ifdef KEYMASTER_LEGACY_FORMAT

keymaster_error_t SecureStorageManager::TranslateLegacyFormat() {
    FileCloser file;
    int rc = file.open_file(session_handle_, kAttributeFileName, 0, 0);
    if (rc == NO_ERROR) {
        // New attribute file exists, nothing to do.
        return KM_ERROR_OK;
    }

    UniquePtr<KeymasterAttributes> km_attributes(new (
            std::nothrow) KeymasterAttributes(KeymasterAttributes_init_zero));
    if (km_attributes.get() == nullptr) {
        return KM_ERROR_MEMORY_ALLOCATION_FAILED;
    }

    AttestationKeySlot key_slots[] = {
            AttestationKeySlot::kRsa,        AttestationKeySlot::kEcdsa,
            AttestationKeySlot::kEddsa,      AttestationKeySlot::kEpid,
            AttestationKeySlot::kClaimable0, AttestationKeySlot::kSomRsa,
            AttestationKeySlot::kSomEcdsa,   AttestationKeySlot::kSomEddsa,
            AttestationKeySlot::kSomEpid};
    char key_file[kStorageIdLengthMax];
    char cert_file[kStorageIdLengthMax];
    uint32_t key_size;
    uint32_t cert_size;
    keymaster_error_t err;
    for (size_t i = 0; i < sizeof(key_slots) / sizeof(int); i++) {
        AttestationKeySlot key_slot = key_slots[i];
        UniquePtr<AttestationKey> attestation_key(
                new (std::nothrow) AttestationKey(AttestationKey_init_zero));
        if (attestation_key.get() == nullptr) {
            return KM_ERROR_MEMORY_ALLOCATION_FAILED;
        }
        snprintf(key_file, kStorageIdLengthMax, "%s.%s", kLegacyAttestKeyPrefix,
                 GetKeySlotStr(key_slot));
        err = LegacySecureStorageRead(key_file, attestation_key->key.bytes,
                                      &key_size, kKeySizeMax);
        if (err != KM_ERROR_OK) {
            return err;
        }
        if (key_size == 0) {
            // Legacy key file for this key slot is not found.
            continue;
        }
        attestation_key->key.size = key_size;
        attestation_key->has_key = true;
        // Do not commit the delete.
        rc = storage_delete_file(session_handle_, key_file, 0);
        if (rc < 0) {
            return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        }
        for (int index = 0; index < kMaxCertChainLength; index++) {
            snprintf(cert_file, kStorageIdLengthMax, "%s.%s.%d",
                     kLegacyAttestCertPrefix, GetKeySlotStr(key_slot), index);
            err = LegacySecureStorageRead(
                    cert_file, attestation_key->certs[index].content.bytes,
                    &cert_size, kCertSizeMax);
            if (err != KM_ERROR_OK) {
                return err;
            }
            if (cert_size == 0) {
                // One cert does not exist, no need to continue reading.
                break;
            }
            attestation_key->certs[index].content.size = cert_size;
            attestation_key->certs_count = index + 1;
            // Do not commit the delete.
            rc = storage_delete_file(session_handle_, cert_file, 0);
            if (rc < 0) {
                return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
            }
        }
        // Do not commit the write.
        keymaster_error_t err =
                WriteAttestationKey(key_slot, attestation_key.get(), false);
        if (err != KM_ERROR_OK) {
            LOG_E("Failed to write attestation key for slot: %d: %d\n",
                  key_slot, err);
            return err;
        }
    }

    uint32_t product_id_size;
    err = LegacySecureStorageRead(kLegacyProductIdFileName,
                                  km_attributes->product_id.bytes,
                                  &product_id_size, kProductIdSize);
    if (err != KM_ERROR_OK) {
        return err;
    }
    if (product_id_size != 0) {
        km_attributes->has_product_id = true;
        km_attributes->product_id.size = product_id_size;
        rc = storage_delete_file(session_handle_, kLegacyProductIdFileName, 0);
        if (rc < 0) {
            return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        }
    }
    uint32_t uuid_size;
    err = LegacySecureStorageRead(kLegacyAttestUuidFileName,
                                  km_attributes->uuid.bytes, &uuid_size,
                                  kAttestationUuidSize);
    if (err != KM_ERROR_OK) {
        return err;
    }
    if (uuid_size != 0) {
        km_attributes->has_uuid = true;
        km_attributes->uuid.size = uuid_size;
        rc = storage_delete_file(session_handle_, kLegacyAttestUuidFileName, 0);
        if (rc < 0) {
            return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        }
    }
    err = WriteKeymasterAttributes(km_attributes.get(), false);
    if (err != KM_ERROR_OK) {
        return err;
    }

    // Commit the pending transactions.
    rc = storage_end_transaction(session_handle_, STORAGE_OP_COMPLETE);
    if (rc < 0) {
        LOG_E("Error: failed to commit write transaction to translate file"
              " format.\n");
        return KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::LegacySecureStorageRead(
        const char* filename,
        void* data,
        uint32_t* size,
        uint32_t max_size) {
    FileCloser file;
    uint64_t file_size;
    int rc = file.open_file(session_handle_, filename, 0, 0);
    if (rc == ERR_NOT_FOUND) {
        *size = 0;
        return KM_ERROR_OK;
    }
    rc = storage_get_file_size(file.get_file_handle(), &file_size);
    if (rc < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (file_size > static_cast<uint64_t>(max_size)) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    *size = static_cast<uint32_t>(file_size);
    rc = storage_read(file.get_file_handle(), 0, data, file_size);
    if (rc < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (static_cast<uint32_t>(rc) < *size) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    return KM_ERROR_OK;
}

keymaster_error_t SecureStorageManager::LegacySecureStorageWrite(
        const char* filename,
        const uint8_t* data,
        uint32_t data_size) {
    FileCloser file;
    int rc = file.open_file(
            session_handle_, filename,
            STORAGE_FILE_OPEN_CREATE | STORAGE_FILE_OPEN_TRUNCATE, 0);
    if (rc < 0) {
        return KM_ERROR_UNKNOWN_ERROR;
    }
    rc = storage_write(file.get_file_handle(), 0, data, data_size,
                       STORAGE_OP_COMPLETE);
    if (rc < 0) {
        LOG_E("Error: [%d] writing storage object '%s'", rc, filename);
        return KM_ERROR_UNKNOWN_ERROR;
    }
    if (static_cast<uint32_t>(rc) < data_size) {
        LOG_E("Error: invalid object size [%d] from '%s'", rc, filename);
        return KM_ERROR_UNKNOWN_ERROR;
    }

    return KM_ERROR_OK;
}

// Deprecated implementation for writing key to storage, for backward
// compatibility tests only.
keymaster_error_t SecureStorageManager::LegacyWriteKeyToStorage(
        AttestationKeySlot key_slot,
        const uint8_t* key,
        uint32_t key_size) {
    char key_file[kStorageIdLengthMax];
    snprintf(key_file, kStorageIdLengthMax, "%s.%s", kLegacyAttestKeyPrefix,
             GetKeySlotStr(key_slot));
    return LegacySecureStorageWrite(key_file, key, key_size);
}

// Deprecated implementation for writing cert to storage, for backward
// compatibility tests only.
keymaster_error_t SecureStorageManager::LegacyWriteCertToStorage(
        AttestationKeySlot key_slot,
        const uint8_t* cert,
        uint32_t cert_size,
        uint32_t index) {
    char cert_file[kStorageIdLengthMax];
    snprintf(cert_file, kStorageIdLengthMax, "%s.%s.%d",
             kLegacyAttestCertPrefix, GetKeySlotStr(key_slot), index);
    return LegacySecureStorageWrite(cert_file, cert, cert_size);
}

// Deprecated, for unit tests only.
keymaster_error_t SecureStorageManager::LegacyWriteAttestationUuid(
        const uint8_t attestation_uuid[kAttestationUuidSize]) {
    return LegacySecureStorageWrite(kLegacyAttestUuidFileName, attestation_uuid,
                                    kAttestationUuidSize);
}

// Deprecated, for unit tests only.
keymaster_error_t SecureStorageManager::LegacySetProductId(
        const uint8_t product_id[kProductIdSize]) {
    return LegacySecureStorageWrite(kLegacyProductIdFileName, product_id,
                                    kProductIdSize);
}
#endif  // #ifdef KEYMASTER_LEGACY_FORMAT

SecureStorageManager::SecureStorageManager() {
    session_handle_ = STORAGE_INVALID_SESSION;
}

SecureStorageManager::~SecureStorageManager() {
    CloseSession();
}

}  // namespace keymaster
