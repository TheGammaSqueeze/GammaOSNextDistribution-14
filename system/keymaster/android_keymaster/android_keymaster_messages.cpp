/*
 * Copyright 2014 The Android Open Source Project
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

#include <keymaster/android_keymaster_messages.h>
#include <keymaster/android_keymaster_utils.h>

namespace keymaster {

// Maximum supported number of entries in an HmacSharingParamsArray.
const size_t kMaxSharingParams = 10;

namespace {

/*
 * Helper functions for working with key blobs.
 */

void set_key_blob(keymaster_key_blob_t* key_blob, const void* key_material, size_t length) {
    delete[] key_blob->key_material;
    key_blob->key_material = dup_buffer(key_material, length);
    key_blob->key_material_size = length;
}

size_t key_blob_size(const keymaster_key_blob_t& key_blob) {
    return sizeof(uint32_t) /* key size */ + key_blob.key_material_size;
}

uint8_t* serialize_key_blob(const keymaster_key_blob_t& key_blob, uint8_t* buf,
                            const uint8_t* end) {
    return append_size_and_data_to_buf(buf, end, key_blob.key_material, key_blob.key_material_size);
}

bool deserialize_key_blob(keymaster_key_blob_t* key_blob, const uint8_t** buf_ptr,
                          const uint8_t* end) {
    delete[] key_blob->key_material;
    key_blob->key_material = nullptr;
    UniquePtr<uint8_t[]> deserialized_key_material;
    if (!copy_size_and_data_from_buf(buf_ptr, end, &key_blob->key_material_size,
                                     &deserialized_key_material))
        return false;
    key_blob->key_material = deserialized_key_material.release();
    return true;
}

void set_blob(keymaster_blob_t* blob, const void* data, size_t length) {
    assert(blob);
    assert(data);
    delete[] blob->data;
    blob->data = dup_buffer(data, length);
    blob->data_length = length;
}

size_t blob_size(const keymaster_blob_t& blob) {
    return sizeof(uint32_t) /* data size */ + blob.data_length;
}

uint8_t* serialize_blob(const keymaster_blob_t& blob, uint8_t* buf, const uint8_t* end) {
    return append_size_and_data_to_buf(buf, end, blob.data, blob.data_length);
}

bool deserialize_blob(keymaster_blob_t* blob, const uint8_t** buf_ptr, const uint8_t* end) {
    delete[] blob->data;
    *blob = {};
    UniquePtr<uint8_t[]> deserialized_blob;
    if (!copy_size_and_data_from_buf(buf_ptr, end, &blob->data_length, &deserialized_blob))
        return false;
    blob->data = deserialized_blob.release();
    return true;
}

/*
 * Helper functions for working with certificate chains.
 */
const size_t kMaxChainEntryCount = 10;

size_t chain_size(const keymaster_cert_chain_t& certificate_chain) {
    size_t result = sizeof(uint32_t); /* certificate_chain.entry_count */
    for (size_t i = 0; i < certificate_chain.entry_count; ++i) {
        result += sizeof(uint32_t); /* certificate_chain.entries[i].data_length */
        result += certificate_chain.entries[i].data_length;
    }
    return result;
}

uint8_t* serialize_chain(const keymaster_cert_chain_t& certificate_chain, uint8_t* buf,
                         const uint8_t* end) {
    buf = append_uint32_to_buf(buf, end, certificate_chain.entry_count);
    for (size_t i = 0; i < certificate_chain.entry_count; ++i) {
        buf = append_size_and_data_to_buf(buf, end, certificate_chain.entries[i].data,
                                          certificate_chain.entries[i].data_length);
    }
    return buf;
}

CertificateChain deserialize_chain(const uint8_t** buf_ptr, const uint8_t* end) {
    size_t entry_count;
    if (!copy_uint32_from_buf(buf_ptr, end, &entry_count) || entry_count > kMaxChainEntryCount) {
        return {};
    }

    CertificateChain certificate_chain(entry_count);
    if (!certificate_chain.entries) return {};

    for (size_t i = 0; i < certificate_chain.entry_count; ++i) {
        UniquePtr<uint8_t[]> data;
        size_t data_length;
        if (!copy_size_and_data_from_buf(buf_ptr, end, &data_length, &data)) return {};
        certificate_chain.entries[i].data = data.release();
        certificate_chain.entries[i].data_length = data_length;
    }

    return certificate_chain;
}

}  // namespace

int32_t NegotiateMessageVersion(const GetVersionResponse& response, keymaster_error_t* error) {
    switch (response.major_ver) {
    case 1:  // KM1
        switch (response.minor_ver) {
        case 0:
            return MessageVersion(KmVersion::KEYMASTER_1, 0 /* km_date */);
        case 1:
            return MessageVersion(KmVersion::KEYMASTER_1_1, 0 /* km_date */);
        }
        break;
    case 2:
        return MessageVersion(KmVersion::KEYMASTER_2, 0 /* km_date */);
    case 3:
        return MessageVersion(KmVersion::KEYMASTER_3, 0 /* km_date */);
    case 4:
        switch (response.minor_ver) {
        case 0:
            return MessageVersion(KmVersion::KEYMASTER_4, 0 /* km_date */);
        case 1:
            return MessageVersion(KmVersion::KEYMASTER_4_1, 0 /* km_date */);
        }
        break;
    }

    *error = KM_ERROR_UNKNOWN_ERROR;
    return -1;
}

int32_t NegotiateMessageVersion(const GetVersion2Request& request,
                                const GetVersion2Response& response) {
    return request.max_message_version < response.max_message_version
               ? request.max_message_version
               : response.max_message_version;
}

size_t KeymasterResponse::SerializedSize() const {
    if (error != KM_ERROR_OK)
        return sizeof(int32_t);
    else
        return sizeof(int32_t) + NonErrorSerializedSize();
}

uint8_t* KeymasterResponse::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint32_to_buf(buf, end, static_cast<uint32_t>(error));
    if (error == KM_ERROR_OK) buf = NonErrorSerialize(buf, end);
    return buf;
}

bool KeymasterResponse::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!copy_uint32_from_buf(buf_ptr, end, &error)) return false;
    if (error != KM_ERROR_OK) return true;
    return NonErrorDeserialize(buf_ptr, end);
}

size_t GenerateKeyRequest::SerializedSize() const {
    size_t size = key_description.SerializedSize();
    if (message_version < 4) return size;
    return size                                           //
           + key_blob_size(attestation_signing_key_blob)  //
           + attest_key_params.SerializedSize()           //
           + blob_size(issuer_subject);
}

uint8_t* GenerateKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = key_description.Serialize(buf, end);
    if (message_version < 4) return buf;
    buf = serialize_key_blob(attestation_signing_key_blob, buf, end);
    buf = attest_key_params.Serialize(buf, end);
    return serialize_blob(issuer_subject, buf, end);
}

bool GenerateKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!key_description.Deserialize(buf_ptr, end)) return false;
    if (message_version < 4) return true;
    return deserialize_key_blob(&attestation_signing_key_blob, buf_ptr, end)  //
           && attest_key_params.Deserialize(buf_ptr, end)                     //
           && deserialize_blob(&issuer_subject, buf_ptr, end);
}

size_t GenerateKeyResponse::NonErrorSerializedSize() const {
    size_t result =
        key_blob_size(key_blob) + enforced.SerializedSize() + unenforced.SerializedSize();
    if (message_version < 4) return result;
    return result + chain_size(certificate_chain);
}

uint8_t* GenerateKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    buf = enforced.Serialize(buf, end);
    buf = unenforced.Serialize(buf, end);
    if (message_version < 4) return buf;
    return serialize_chain(certificate_chain, buf, end);
}

bool GenerateKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!deserialize_key_blob(&key_blob, buf_ptr, end) ||  //
        !enforced.Deserialize(buf_ptr, end) ||             //
        !unenforced.Deserialize(buf_ptr, end)) {
        return false;
    }
    if (message_version < 4) return true;
    certificate_chain = deserialize_chain(buf_ptr, end);
    return !!certificate_chain.entries;
}

size_t GenerateRkpKeyResponse::NonErrorSerializedSize() const {
    return key_blob_size(key_blob) + blob_size(maced_public_key);
}

uint8_t* GenerateRkpKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    return serialize_blob(maced_public_key, buf, end);
}

bool GenerateRkpKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&key_blob, buf_ptr, end) &&
           deserialize_blob(&maced_public_key, buf_ptr, end);
}

size_t GenerateCsrRequest::SerializedSize() const {
    size_t size = sizeof(uint8_t); /* test_mode */
    size += sizeof(uint32_t);      /* num_keys */
    for (size_t i = 0; i < num_keys; i++) {
        size += blob_size(keys_to_sign_array[i]);
    }
    size += blob_size(endpoint_enc_cert_chain);
    size += blob_size(challenge);
    return size;
}

uint8_t* GenerateCsrRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_to_buf(buf, end, &test_mode, sizeof(uint8_t));
    buf = append_uint32_to_buf(buf, end, num_keys);
    for (size_t i = 0; i < num_keys; i++) {
        buf = serialize_blob(keys_to_sign_array[i], buf, end);
    }
    buf = serialize_blob(endpoint_enc_cert_chain, buf, end);
    return serialize_blob(challenge, buf, end);
}

bool GenerateCsrRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!copy_from_buf(buf_ptr, end, &test_mode, sizeof(uint8_t)) ||
        !copy_from_buf(buf_ptr, end, &num_keys, sizeof(uint32_t))) {
        return false;
    }
    keys_to_sign_array = new (std::nothrow) KeymasterBlob[num_keys];
    if (!keys_to_sign_array) return false;
    for (size_t i = 0; i < num_keys; i++) {
        if (!deserialize_blob(&keys_to_sign_array[i], buf_ptr, end)) return false;
    }
    return deserialize_blob(&endpoint_enc_cert_chain, buf_ptr, end) &&
           deserialize_blob(&challenge, buf_ptr, end);
}

void GenerateCsrRequest::SetKeyToSign(uint32_t index, const void* data, size_t length) {
    if (index >= num_keys) {
        return;
    }
    set_blob(&keys_to_sign_array[index], data, length);
}

void GenerateCsrRequest::SetEndpointEncCertChain(const void* data, size_t length) {
    set_blob(&endpoint_enc_cert_chain, data, length);
}

void GenerateCsrRequest::SetChallenge(const void* data, size_t length) {
    set_blob(&challenge, data, length);
}

size_t GenerateCsrResponse::NonErrorSerializedSize() const {
    return blob_size(keys_to_sign_mac) + blob_size(device_info_blob) +
           blob_size(protected_data_blob);
}

uint8_t* GenerateCsrResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_blob(keys_to_sign_mac, buf, end);
    buf = serialize_blob(device_info_blob, buf, end);
    return serialize_blob(protected_data_blob, buf, end);
}

bool GenerateCsrResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_blob(&keys_to_sign_mac, buf_ptr, end) &&
           deserialize_blob(&device_info_blob, buf_ptr, end) &&
           deserialize_blob(&protected_data_blob, buf_ptr, end);
}

size_t GenerateCsrV2Request::SerializedSize() const {
    size_t size = sizeof(uint32_t); /* num_keys */
    for (size_t i = 0; i < num_keys; i++) {
        size += blob_size(keys_to_sign_array[i]);
    }
    size += blob_size(challenge);
    return size;
}

uint8_t* GenerateCsrV2Request::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint32_to_buf(buf, end, num_keys);
    for (size_t i = 0; i < num_keys; i++) {
        buf = serialize_blob(keys_to_sign_array[i], buf, end);
    }
    return serialize_blob(challenge, buf, end);
}

bool GenerateCsrV2Request::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!copy_from_buf(buf_ptr, end, &num_keys, sizeof(uint32_t))) return false;

    keys_to_sign_array = new (std::nothrow) KeymasterBlob[num_keys];
    if (!keys_to_sign_array) return false;
    for (size_t i = 0; i < num_keys; i++) {
        if (!deserialize_blob(&keys_to_sign_array[i], buf_ptr, end)) return false;
    }
    return deserialize_blob(&challenge, buf_ptr, end);
}

bool GenerateCsrV2Request::InitKeysToSign(uint32_t count) {
    num_keys = count;
    keys_to_sign_array = new (std::nothrow) KeymasterBlob[count];
    if (!keys_to_sign_array) {
        return false;
    }
    return true;
}

void GenerateCsrV2Request::SetKeyToSign(uint32_t index, const void* data, size_t length) {
    if (index >= num_keys) {
        return;
    }
    set_blob(&keys_to_sign_array[index], data, length);
}

void GenerateCsrV2Request::SetChallenge(const void* data, size_t length) {
    set_blob(&challenge, data, length);
}

size_t GenerateCsrV2Response::NonErrorSerializedSize() const {
    return blob_size(csr);
}

uint8_t* GenerateCsrV2Response::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    return serialize_blob(csr, buf, end);
}

bool GenerateCsrV2Response::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_blob(&csr, buf_ptr, end);
}

GetKeyCharacteristicsRequest::~GetKeyCharacteristicsRequest() {
    delete[] key_blob.key_material;
}

void GetKeyCharacteristicsRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t GetKeyCharacteristicsRequest::SerializedSize() const {
    return key_blob_size(key_blob) + additional_params.SerializedSize();
}

uint8_t* GetKeyCharacteristicsRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    return additional_params.Serialize(buf, end);
}

bool GetKeyCharacteristicsRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&key_blob, buf_ptr, end) &&
           additional_params.Deserialize(buf_ptr, end);
}

size_t GetKeyCharacteristicsResponse::NonErrorSerializedSize() const {
    return enforced.SerializedSize() + unenforced.SerializedSize();
}

uint8_t* GetKeyCharacteristicsResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = enforced.Serialize(buf, end);
    return unenforced.Serialize(buf, end);
}

bool GetKeyCharacteristicsResponse::NonErrorDeserialize(const uint8_t** buf_ptr,
                                                        const uint8_t* end) {
    return enforced.Deserialize(buf_ptr, end) && unenforced.Deserialize(buf_ptr, end);
}

void BeginOperationRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t BeginOperationRequest::SerializedSize() const {
    return sizeof(uint32_t) /* purpose */ + key_blob_size(key_blob) +
           additional_params.SerializedSize();
}

uint8_t* BeginOperationRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint32_to_buf(buf, end, purpose);
    buf = serialize_key_blob(key_blob, buf, end);
    return additional_params.Serialize(buf, end);
}

bool BeginOperationRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return copy_uint32_from_buf(buf_ptr, end, &purpose) &&
           deserialize_key_blob(&key_blob, buf_ptr, end) &&
           additional_params.Deserialize(buf_ptr, end);
}

size_t BeginOperationResponse::NonErrorSerializedSize() const {
    if (message_version == 0)
        return sizeof(op_handle);
    else
        return sizeof(op_handle) + output_params.SerializedSize();
}

uint8_t* BeginOperationResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint64_to_buf(buf, end, op_handle);
    if (message_version > 0) buf = output_params.Serialize(buf, end);
    return buf;
}

bool BeginOperationResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    bool retval = copy_uint64_from_buf(buf_ptr, end, &op_handle);
    if (retval && message_version > 0) retval = output_params.Deserialize(buf_ptr, end);
    return retval;
}

size_t UpdateOperationRequest::SerializedSize() const {
    if (message_version == 0)
        return sizeof(op_handle) + input.SerializedSize();
    else
        return sizeof(op_handle) + input.SerializedSize() + additional_params.SerializedSize();
}

uint8_t* UpdateOperationRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint64_to_buf(buf, end, op_handle);
    buf = input.Serialize(buf, end);
    if (message_version > 0) buf = additional_params.Serialize(buf, end);
    return buf;
}

bool UpdateOperationRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    bool retval = copy_uint64_from_buf(buf_ptr, end, &op_handle) && input.Deserialize(buf_ptr, end);
    if (retval && message_version > 0) retval = additional_params.Deserialize(buf_ptr, end);
    return retval;
}

size_t UpdateOperationResponse::NonErrorSerializedSize() const {
    size_t size = 0;
    switch (message_version) {
    case 4:
    case 3:
    case 2:
        size += output_params.SerializedSize();
        FALLTHROUGH;
    case 1:
        size += sizeof(uint32_t);
        FALLTHROUGH;
    case 0:
        size += output.SerializedSize();
        break;

    default:
        assert(false);
    }

    return size;
}

uint8_t* UpdateOperationResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = output.Serialize(buf, end);
    if (message_version > 0) buf = append_uint32_to_buf(buf, end, input_consumed);
    if (message_version > 1) buf = output_params.Serialize(buf, end);
    return buf;
}

bool UpdateOperationResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    bool retval = output.Deserialize(buf_ptr, end);
    if (retval && message_version > 0) retval = copy_uint32_from_buf(buf_ptr, end, &input_consumed);
    if (retval && message_version > 1) retval = output_params.Deserialize(buf_ptr, end);
    return retval;
}

size_t FinishOperationRequest::SerializedSize() const {
    size_t size = 0;
    switch (message_version) {
    case 4:
    case 3:
        size += input.SerializedSize();
        FALLTHROUGH;
    case 2:
    case 1:
        size += additional_params.SerializedSize();
        FALLTHROUGH;
    case 0:
        size += sizeof(op_handle) + signature.SerializedSize();
        break;

    default:
        assert(false);  // Should never get here.
    }

    return size;
}

uint8_t* FinishOperationRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint64_to_buf(buf, end, op_handle);
    buf = signature.Serialize(buf, end);
    if (message_version > 0) buf = additional_params.Serialize(buf, end);
    if (message_version > 2) buf = input.Serialize(buf, end);
    return buf;
}

bool FinishOperationRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    bool retval =
        copy_uint64_from_buf(buf_ptr, end, &op_handle) && signature.Deserialize(buf_ptr, end);
    if (retval && message_version > 0) retval = additional_params.Deserialize(buf_ptr, end);
    if (retval && message_version > 2) retval = input.Deserialize(buf_ptr, end);
    return retval;
}

size_t FinishOperationResponse::NonErrorSerializedSize() const {
    if (message_version < 2)
        return output.SerializedSize();
    else
        return output.SerializedSize() + output_params.SerializedSize();
}

uint8_t* FinishOperationResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = output.Serialize(buf, end);
    if (message_version > 1) buf = output_params.Serialize(buf, end);
    return buf;
}

bool FinishOperationResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    bool retval = output.Deserialize(buf_ptr, end);
    if (retval && message_version > 1) retval = output_params.Deserialize(buf_ptr, end);
    return retval;
}

size_t AddEntropyRequest::SerializedSize() const {
    return random_data.SerializedSize();
}

uint8_t* AddEntropyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    return random_data.Serialize(buf, end);
}

bool AddEntropyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return random_data.Deserialize(buf_ptr, end);
}

size_t ImportKeyRequest::SerializedSize() const {
    size_t size = key_description.SerializedSize()     //
                  + sizeof(uint32_t) /* key_format */  //
                  + key_blob_size(key_data);
    if (message_version < 4) return size;
    return size                                           //
           + key_blob_size(attestation_signing_key_blob)  //
           + attest_key_params.SerializedSize()           //
           + blob_size(issuer_subject);
}

uint8_t* ImportKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = key_description.Serialize(buf, end);
    buf = append_uint32_to_buf(buf, end, key_format);
    buf = serialize_key_blob(key_data, buf, end);
    if (message_version < 4) return buf;
    buf = serialize_key_blob(attestation_signing_key_blob, buf, end);
    buf = attest_key_params.Serialize(buf, end);
    return serialize_blob(issuer_subject, buf, end);
}

bool ImportKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!(key_description.Deserialize(buf_ptr, end) &&
          copy_uint32_from_buf(buf_ptr, end, &key_format) &&
          deserialize_key_blob(&key_data, buf_ptr, end))) {
        return false;
    }
    if (message_version < 4) return true;

    return deserialize_key_blob(&attestation_signing_key_blob, buf_ptr, end)  //
           && attest_key_params.Deserialize(buf_ptr, end)                     //
           && deserialize_blob(&issuer_subject, buf_ptr, end);
}

void ImportKeyRequest::SetKeyMaterial(const void* key_material, size_t length) {
    key_data = KeymasterKeyBlob(static_cast<const uint8_t*>(key_material), length);
}

void ImportKeyResponse::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t ImportKeyResponse::NonErrorSerializedSize() const {
    size_t result =
        key_blob_size(key_blob) + enforced.SerializedSize() + unenforced.SerializedSize();
    if (message_version < 4) return result;
    return result + chain_size(certificate_chain);
}

uint8_t* ImportKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    buf = enforced.Serialize(buf, end);
    buf = unenforced.Serialize(buf, end);
    if (message_version < 4) return buf;
    return serialize_chain(certificate_chain, buf, end);
}

bool ImportKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!deserialize_key_blob(&key_blob, buf_ptr, end) ||  //
        !enforced.Deserialize(buf_ptr, end) ||             //
        !unenforced.Deserialize(buf_ptr, end)) {
        return false;
    }
    if (message_version < 4) return true;
    certificate_chain = deserialize_chain(buf_ptr, end);
    return !!certificate_chain.entries;
}

void ExportKeyRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t ExportKeyRequest::SerializedSize() const {
    return additional_params.SerializedSize() + sizeof(uint32_t) /* key_format */ +
           key_blob_size(key_blob);
}

uint8_t* ExportKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = additional_params.Serialize(buf, end);
    buf = append_uint32_to_buf(buf, end, key_format);
    return serialize_key_blob(key_blob, buf, end);
}

bool ExportKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return additional_params.Deserialize(buf_ptr, end) &&
           copy_uint32_from_buf(buf_ptr, end, &key_format) &&
           deserialize_key_blob(&key_blob, buf_ptr, end);
}

void ExportKeyResponse::SetKeyMaterial(const void* key_material, size_t length) {
    delete[] key_data;
    key_data = dup_buffer(key_material, length);
    key_data_length = length;
}

size_t ExportKeyResponse::NonErrorSerializedSize() const {
    return sizeof(uint32_t) /* key_data_length */ + key_data_length;
}

uint8_t* ExportKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    return append_size_and_data_to_buf(buf, end, key_data, key_data_length);
}

bool ExportKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    delete[] key_data;
    key_data = nullptr;
    UniquePtr<uint8_t[]> deserialized_key_material;
    if (!copy_size_and_data_from_buf(buf_ptr, end, &key_data_length, &deserialized_key_material))
        return false;
    key_data = deserialized_key_material.release();
    return true;
}

void DeleteKeyRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t DeleteKeyRequest::SerializedSize() const {
    return key_blob_size(key_blob);
}

uint8_t* DeleteKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    return serialize_key_blob(key_blob, buf, end);
}

bool DeleteKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&key_blob, buf_ptr, end);
}

size_t GetVersionResponse::NonErrorSerializedSize() const {
    return sizeof(major_ver) + sizeof(minor_ver) + sizeof(subminor_ver);
}

uint8_t* GetVersionResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    if (buf + NonErrorSerializedSize() <= end) {
        *buf++ = major_ver;
        *buf++ = minor_ver;
        *buf++ = subminor_ver;
    } else {
        buf += NonErrorSerializedSize();
    }
    return buf;
}

bool GetVersionResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (*buf_ptr + NonErrorSerializedSize() > end) return false;
    const uint8_t* tmp = *buf_ptr;
    major_ver = *tmp++;
    minor_ver = *tmp++;
    subminor_ver = *tmp++;
    *buf_ptr = tmp;
    return true;
}

AttestKeyRequest::~AttestKeyRequest() {
    delete[] key_blob.key_material;
}

void AttestKeyRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t AttestKeyRequest::SerializedSize() const {
    return key_blob_size(key_blob) + attest_params.SerializedSize();
}

uint8_t* AttestKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    return attest_params.Serialize(buf, end);
}

bool AttestKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&key_blob, buf_ptr, end) && attest_params.Deserialize(buf_ptr, end);
}

size_t AttestKeyResponse::NonErrorSerializedSize() const {
    return chain_size(certificate_chain);
}

uint8_t* AttestKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    return serialize_chain(certificate_chain, buf, end);
}

bool AttestKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    certificate_chain = deserialize_chain(buf_ptr, end);
    return !!certificate_chain.entries;
}

UpgradeKeyRequest::~UpgradeKeyRequest() {
    delete[] key_blob.key_material;
}

void UpgradeKeyRequest::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t UpgradeKeyRequest::SerializedSize() const {
    return key_blob_size(key_blob) + upgrade_params.SerializedSize();
}

uint8_t* UpgradeKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    return upgrade_params.Serialize(buf, end);
}

bool UpgradeKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&key_blob, buf_ptr, end) &&
           upgrade_params.Deserialize(buf_ptr, end);
}

UpgradeKeyResponse::~UpgradeKeyResponse() {
    delete[] upgraded_key.key_material;
}

size_t UpgradeKeyResponse::NonErrorSerializedSize() const {
    return key_blob_size(upgraded_key);
}

uint8_t* UpgradeKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    return serialize_key_blob(upgraded_key, buf, end);
}

bool UpgradeKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&upgraded_key, buf_ptr, end);
}

size_t HmacSharingParameters::SerializedSize() const {
    return blob_size(seed) + sizeof(nonce);
}

uint8_t* HmacSharingParameters::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_blob(seed, buf, end);
    return append_to_buf(buf, end, nonce, sizeof(nonce));
}

bool HmacSharingParameters::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_blob(&seed, buf_ptr, end) &&
           copy_from_buf(buf_ptr, end, nonce, sizeof(nonce));
}

size_t HmacSharingParametersArray::SerializedSize() const {
    size_t size = sizeof(uint32_t);  // num_params size
    for (size_t i = 0; i < num_params; ++i) {
        size += params_array[i].SerializedSize();
    }
    return size;
}

uint8_t* HmacSharingParametersArray::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint32_to_buf(buf, end, num_params);
    for (size_t i = 0; i < num_params; ++i) {
        buf = params_array[i].Serialize(buf, end);
    }
    return buf;
}

bool HmacSharingParametersArray::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!copy_uint32_from_buf(buf_ptr, end, &num_params)) return false;
    if (num_params > kMaxSharingParams) return false;
    params_array = new (std::nothrow) HmacSharingParameters[num_params];
    if (!params_array) return false;
    for (size_t i = 0; i < num_params; ++i) {
        if (!params_array[i].Deserialize(buf_ptr, end)) return false;
    }
    return true;
}

size_t ComputeSharedHmacResponse::NonErrorSerializedSize() const {
    return blob_size(sharing_check);
}

uint8_t* ComputeSharedHmacResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    return serialize_blob(sharing_check, buf, end);
}

bool ComputeSharedHmacResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_blob(&sharing_check, buf_ptr, end);
}

size_t ImportWrappedKeyRequest::SerializedSize() const {
    return key_blob_size(wrapped_key) + key_blob_size(wrapping_key) + key_blob_size(masking_key) +
           additional_params.SerializedSize() + sizeof(uint64_t)  // password_sid
           + sizeof(uint64_t);                                    // biometric_sid
}

uint8_t* ImportWrappedKeyRequest::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(wrapped_key, buf, end);
    buf = serialize_key_blob(wrapping_key, buf, end);
    buf = serialize_key_blob(masking_key, buf, end);
    buf = additional_params.Serialize(buf, end);
    buf = append_uint64_to_buf(buf, end, password_sid);
    return append_uint64_to_buf(buf, end, biometric_sid);
}

bool ImportWrappedKeyRequest::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return deserialize_key_blob(&wrapped_key, buf_ptr, end) &&
           deserialize_key_blob(&wrapping_key, buf_ptr, end) &&
           deserialize_key_blob(&masking_key, buf_ptr, end) &&
           additional_params.Deserialize(buf_ptr, end) &&
           copy_uint64_from_buf(buf_ptr, end, &password_sid) &&
           copy_uint64_from_buf(buf_ptr, end, &biometric_sid);
}

void ImportWrappedKeyRequest::SetWrappedMaterial(const void* key_material, size_t length) {
    set_key_blob(&wrapped_key, key_material, length);
}

void ImportWrappedKeyRequest::SetWrappingMaterial(const void* key_material, size_t length) {
    set_key_blob(&wrapping_key, key_material, length);
}

void ImportWrappedKeyRequest::SetMaskingKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&masking_key, key_material, length);
}

void ImportWrappedKeyResponse::SetKeyMaterial(const void* key_material, size_t length) {
    set_key_blob(&key_blob, key_material, length);
}

size_t ImportWrappedKeyResponse::NonErrorSerializedSize() const {
    size_t result =
        key_blob_size(key_blob) + enforced.SerializedSize() + unenforced.SerializedSize();
    if (message_version < 4) return result;
    return result + chain_size(certificate_chain);
}

uint8_t* ImportWrappedKeyResponse::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = serialize_key_blob(key_blob, buf, end);
    buf = enforced.Serialize(buf, end);
    buf = unenforced.Serialize(buf, end);
    if (message_version < 4) return buf;
    return serialize_chain(certificate_chain, buf, end);
}

bool ImportWrappedKeyResponse::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    if (!deserialize_key_blob(&key_blob, buf_ptr, end) ||  //
        !enforced.Deserialize(buf_ptr, end) ||             //
        !unenforced.Deserialize(buf_ptr, end)) {
        return false;
    }
    if (message_version < 4) return true;
    certificate_chain = deserialize_chain(buf_ptr, end);
    return !!certificate_chain.entries;
}

size_t HardwareAuthToken::SerializedSize() const {
    return sizeof(challenge) + sizeof(user_id) + sizeof(authenticator_id) +
           sizeof(authenticator_type) + sizeof(timestamp) + blob_size(mac);
}

uint8_t* HardwareAuthToken::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint64_to_buf(buf, end, challenge);
    buf = append_uint64_to_buf(buf, end, user_id);
    buf = append_uint64_to_buf(buf, end, authenticator_id);
    buf = append_uint32_to_buf(buf, end, authenticator_type);
    buf = append_uint64_to_buf(buf, end, timestamp);
    return serialize_blob(mac, buf, end);
}

bool HardwareAuthToken::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return copy_uint64_from_buf(buf_ptr, end, &challenge) &&
           copy_uint64_from_buf(buf_ptr, end, &user_id) &&
           copy_uint64_from_buf(buf_ptr, end, &authenticator_id) &&
           copy_uint32_from_buf(buf_ptr, end, &authenticator_type) &&
           copy_uint64_from_buf(buf_ptr, end, &timestamp) &&  //
           deserialize_blob(&mac, buf_ptr, end);
}

size_t VerificationToken::SerializedSize() const {
    return sizeof(challenge) + sizeof(timestamp) + parameters_verified.SerializedSize() +
           sizeof(security_level) + blob_size(mac);
}

uint8_t* VerificationToken::Serialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint64_to_buf(buf, end, challenge);
    buf = append_uint64_to_buf(buf, end, timestamp);
    buf = parameters_verified.Serialize(buf, end);
    buf = append_uint32_to_buf(buf, end, security_level);
    return serialize_blob(mac, buf, end);
}

bool VerificationToken::Deserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return copy_uint64_from_buf(buf_ptr, end, &challenge) &&
           copy_uint64_from_buf(buf_ptr, end, &timestamp) &&
           parameters_verified.Deserialize(buf_ptr, end) &&
           copy_uint32_from_buf(buf_ptr, end, &security_level) &&
           deserialize_blob(&mac, buf_ptr, end);
}

size_t GetVersion2Response::NonErrorSerializedSize() const {
    return sizeof(max_message_version) +  //
           sizeof(km_version) +           //
           sizeof(km_date);
}

uint8_t* GetVersion2Response::NonErrorSerialize(uint8_t* buf, const uint8_t* end) const {
    buf = append_uint32_to_buf(buf, end, max_message_version);
    buf = append_uint32_to_buf(buf, end, km_version);
    return append_uint32_to_buf(buf, end, km_date);
}

bool GetVersion2Response::NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) {
    return copy_uint32_from_buf(buf_ptr, end, &max_message_version) &&
           copy_uint32_from_buf(buf_ptr, end, &km_version) &&
           copy_uint32_from_buf(buf_ptr, end, &km_date);
}

}  // namespace keymaster
