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

#pragma once

#include <assert.h>
#include <stdint.h>
#include <stdlib.h>
#include <string.h>

#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <keymaster/android_keymaster_utils.h>
#include <keymaster/authorization_set.h>
#include <keymaster/km_version.h>

namespace keymaster {

// Commands
enum AndroidKeymasterCommand : uint32_t {
    GENERATE_KEY = 0,
    BEGIN_OPERATION = 1,
    UPDATE_OPERATION = 2,
    FINISH_OPERATION = 3,
    ABORT_OPERATION = 4,
    IMPORT_KEY = 5,
    EXPORT_KEY = 6,
    GET_VERSION = 7,
    ADD_RNG_ENTROPY = 8,
    GET_SUPPORTED_ALGORITHMS = 9,
    GET_SUPPORTED_BLOCK_MODES = 10,
    GET_SUPPORTED_PADDING_MODES = 11,
    GET_SUPPORTED_DIGESTS = 12,
    GET_SUPPORTED_IMPORT_FORMATS = 13,
    GET_SUPPORTED_EXPORT_FORMATS = 14,
    GET_KEY_CHARACTERISTICS = 15,
    ATTEST_KEY = 16,
    UPGRADE_KEY = 17,
    CONFIGURE = 18,
    GET_HMAC_SHARING_PARAMETERS = 19,
    COMPUTE_SHARED_HMAC = 20,
    VERIFY_AUTHORIZATION = 21,
    DELETE_KEY = 22,
    DELETE_ALL_KEYS = 23,
    DESTROY_ATTESTATION_IDS = 24,
    IMPORT_WRAPPED_KEY = 25,
    EARLY_BOOT_ENDED = 26,
    DEVICE_LOCKED = 27,
    GET_VERSION_2 = 28,
    GENERATE_RKP_KEY = 29,
    GENERATE_CSR = 30,
    GENERATE_TIMESTAMP_TOKEN = 31,
    CONFIGURE_VENDOR_PATCHLEVEL = 32,
    CONFIGURE_BOOT_PATCHLEVEL = 33,
    CONFIGURE_VERIFIED_BOOT_INFO = 34,
    GET_ROOT_OF_TRUST = 35,
    GET_HW_INFO = 36,
    GENERATE_CSR_V2 = 37,
    SET_ATTESTATION_IDS = 38,
    SET_ATTESTATION_IDS_KM3 = 39,
};

/**
 * Keymaster message versions are tied to keymaster versions.  We map the keymaster version to a
 * sequential "message version".  The actual message formatting differences are implemented in the
 * message classes. Note that it is not necessary to increase the message version when new messages
 * are added, only when the serialized format of one or more messages changes.  A message version
 * argument is provided to the message constructor and when the serialization/deserialization
 * methods are called the implementations of those methods should examine the message version and
 * generate/parse the byte stream accordingly.
 *
 * The original design of message versioning uses the GetVersion message, sent from client (e.g. HAL
 * service) to server (e.g. trusted app), and then relies on the client to identify what messages to
 * send.  This architecture assumes that the client is never older than the server.  This assumption
 * turned out not to be true in general.
 *
 * The current approach performs a mutual exchange of message version info between client and
 * server, using the GetVersion2 message.  In addition, it defers the specification of the message
 * ID to the message classes, so a message class can use a different ID when necessary.  ID changes
 * should be rare, in fact the only time they should be required is during the switch from
 * GetVersion to GetVersion2.
 *
 * Assuming both client and server support GetVersion2, the approach is as follows:
 *
 * 1.  Client sends GetVersion2Request, containing its maximum message version, c_max.
 * 2.  Server replies with GetVersion2Response, containing its maximum message version, s_max.
 * 3.  Both sides proceed to create all messages with version min(c_max, s_max).
 *
 * To enable this, the client must always send GetVersion2 as its first message.  If the server
 * doesn't support GetVersion2, it will reply with an error of some sort (the details are likely
 * environment-specific).  If the client gets this error, it must respond by sending GetVersion, and
 * then must configure its message version according to the response.  Note that an acceptable
 * response to a too-old server version is to return an error to the caller of the client, informing
 * it of the problem.
 *
 * On the server side, a server that supports GetVersion2 must also support GetVersion.  If it
 * received GetVersion2 it should proceed as outline above, and expect that the client will not send
 * GetVersion.  If it received GetVersion, it must assume that the client does not support
 * GetVersion2 and reply that it is version 2.0.0 and use the corresponding message version (3).
 */
constexpr int32_t kInvalidMessageVersion = -1;
constexpr int32_t kMaxMessageVersion = 4;
constexpr int32_t kDefaultMessageVersion = 3;

/**
 * MessageVersion returns the message version for a specified KM version and, possibly, KM release
 * date in YYYYMMDD format (it's not recommended to change message formats within a KM version, but
 * it could happen).
 */
inline int32_t MessageVersion(KmVersion version, uint32_t /* km_date */ = 0) {
    switch (version) {
    case KmVersion::KEYMASTER_1:
        return 1;
    case KmVersion::KEYMASTER_1_1:
        return 2;
    case KmVersion::KEYMASTER_2:
    case KmVersion::KEYMASTER_3:
    case KmVersion::KEYMASTER_4:
    case KmVersion::KEYMASTER_4_1:
        return 3;
    case KmVersion::KEYMINT_1:
    case KmVersion::KEYMINT_2:
    case KmVersion::KEYMINT_3:
        return 4;
    }
    return kInvalidMessageVersion;
}

/**
 * NegotiateMessageVersion implements the client side of the GetVersion protocol, determining the
 * appropriate message version from the values returned by the server.
 */
struct GetVersionResponse;
int32_t NegotiateMessageVersion(const GetVersionResponse& response, keymaster_error_t* error);

/**
 * This MessageVersion overload determines the message version to use given the provided client and
 * server messages.  If the client gets an error when it sends GetVersion2Request, it should send
 * GetVersionRequest and use the above overload.  If the server receives GetVersionRequest, it
 * should assume it should use message version 3 and return GetVersionResponse(2, 0, 0).
 */
struct GetVersion2Request;
struct GetVersion2Response;
int32_t NegotiateMessageVersion(const GetVersion2Request& request,
                                const GetVersion2Response& response);

struct KeymasterMessage : public Serializable {
    explicit KeymasterMessage(int32_t ver) : message_version(ver) { assert(ver >= 0); }

    // The message version that should be used for this message.  This indicates how the data is
    // serialized/deserialized. Commonly, higher message versions serialize/deserialize additional
    // arguments, though there is no specific rule limiting later version to adding parameters.
    const int32_t message_version;
};

/**
 * All responses include an error value, and if the error is not KM_ERROR_OK, return no additional
 * data.
 */
struct KeymasterResponse : public KeymasterMessage {
    explicit KeymasterResponse(int32_t ver)
        : KeymasterMessage(ver), error(KM_ERROR_UNKNOWN_ERROR) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    virtual size_t NonErrorSerializedSize() const = 0;
    virtual uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const = 0;
    virtual bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) = 0;

    keymaster_error_t error;
};

// Abstract base for empty requests.
struct EmptyKeymasterRequest : public KeymasterMessage {
    explicit EmptyKeymasterRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return 0; }
    uint8_t* Serialize(uint8_t* buf, const uint8_t*) const override { return buf; }
    bool Deserialize(const uint8_t**, const uint8_t*) override { return true; };
};

// Empty response.
struct EmptyKeymasterResponse : public KeymasterResponse {
    explicit EmptyKeymasterResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override { return 0; }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t*) const override { return buf; }
    bool NonErrorDeserialize(const uint8_t**, const uint8_t*) override { return true; }
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedAlgorithmsRequest : public KeymasterMessage {
    explicit SupportedAlgorithmsRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return 0; };
    uint8_t* Serialize(uint8_t* buf, const uint8_t* /* end */) const override { return buf; }
    bool Deserialize(const uint8_t** /* buf_ptr */, const uint8_t* /* end */) override {
        return true;
    }
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedByAlgorithmRequest : public KeymasterMessage {
    explicit SupportedByAlgorithmRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint32_t); };
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, algorithm);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &algorithm);
    }

    keymaster_algorithm_t algorithm;
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedImportFormatsRequest : public SupportedByAlgorithmRequest {
    explicit SupportedImportFormatsRequest(int32_t ver) : SupportedByAlgorithmRequest(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedExportFormatsRequest : public SupportedByAlgorithmRequest {
    explicit SupportedExportFormatsRequest(int32_t ver) : SupportedByAlgorithmRequest(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedByAlgorithmAndPurposeRequest : public KeymasterMessage {
    explicit SupportedByAlgorithmAndPurposeRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint32_t) * 2; };
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, algorithm);
        return append_uint32_to_buf(buf, end, purpose);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &algorithm) &&
               copy_uint32_from_buf(buf_ptr, end, &purpose);
    }

    keymaster_algorithm_t algorithm;
    keymaster_purpose_t purpose;
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedBlockModesRequest : public SupportedByAlgorithmAndPurposeRequest {
    explicit SupportedBlockModesRequest(int32_t ver) : SupportedByAlgorithmAndPurposeRequest(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedPaddingModesRequest : public SupportedByAlgorithmAndPurposeRequest {
    explicit SupportedPaddingModesRequest(int32_t ver)
        : SupportedByAlgorithmAndPurposeRequest(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedDigestsRequest : public SupportedByAlgorithmAndPurposeRequest {
    explicit SupportedDigestsRequest(int32_t ver) : SupportedByAlgorithmAndPurposeRequest(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
template <typename T> struct SupportedResponse : public KeymasterResponse {
    explicit SupportedResponse(int32_t ver)
        : KeymasterResponse(ver), results(nullptr), results_length(0) {}
    ~SupportedResponse() { delete[] results; }

    template <size_t N> void SetResults(const T (&arr)[N]) { SetResults(arr, N); }

    void SetResults(const T* arr, size_t n) {
        delete[] results;
        results_length = 0;
        results = dup_array(arr, n);
        if (results == nullptr) {
            error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        } else {
            results_length = n;
            error = KM_ERROR_OK;
        }
    }

    size_t NonErrorSerializedSize() const override {
        return sizeof(uint32_t) + results_length * sizeof(uint32_t);
    }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_array_to_buf(buf, end, results, results_length);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        delete[] results;
        results = nullptr;
        UniquePtr<T[]> tmp;
        if (!copy_uint32_array_from_buf(buf_ptr, end, &tmp, &results_length)) return false;
        results = tmp.release();
        return true;
    }

    T* results;
    size_t results_length;
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedAlgorithmsResponse : public SupportedResponse<keymaster_algorithm_t> {
    explicit SupportedAlgorithmsResponse(int32_t ver)
        : SupportedResponse<keymaster_algorithm_t>(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedBlockModesResponse : public SupportedResponse<keymaster_block_mode_t> {
    explicit SupportedBlockModesResponse(int32_t ver)
        : SupportedResponse<keymaster_block_mode_t>(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedPaddingModesResponse : public SupportedResponse<keymaster_padding_t> {
    explicit SupportedPaddingModesResponse(int32_t ver)
        : SupportedResponse<keymaster_padding_t>(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedDigestsResponse : public SupportedResponse<keymaster_digest_t> {
    explicit SupportedDigestsResponse(int32_t ver) : SupportedResponse<keymaster_digest_t>(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedImportFormatsResponse : public SupportedResponse<keymaster_key_format_t> {
    explicit SupportedImportFormatsResponse(int32_t ver)
        : SupportedResponse<keymaster_key_format_t>(ver) {}
};

// TODO(swillden): Remove when Keymaster1 is deleted
struct SupportedExportFormatsResponse : public SupportedResponse<keymaster_key_format_t> {
    explicit SupportedExportFormatsResponse(int32_t ver)
        : SupportedResponse<keymaster_key_format_t>(ver) {}
};

struct GenerateKeyRequest : public KeymasterMessage {
    explicit GenerateKeyRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    AuthorizationSet key_description;
    KeymasterKeyBlob attestation_signing_key_blob;
    AuthorizationSet attest_key_params;
    KeymasterBlob issuer_subject;
};

struct GenerateKeyResponse : public KeymasterResponse {
    explicit GenerateKeyResponse(int32_t ver)
        : KeymasterResponse(ver), key_blob{}, certificate_chain{} {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterKeyBlob key_blob;
    AuthorizationSet enforced;
    AuthorizationSet unenforced;
    CertificateChain certificate_chain;
};

struct GenerateRkpKeyRequest : KeymasterMessage {
    explicit GenerateRkpKeyRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint8_t); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_to_buf(buf, end, &test_mode, sizeof(uint8_t));
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_from_buf(buf_ptr, end, &test_mode, sizeof(uint8_t));
    }

    bool test_mode = false;
};

struct GenerateRkpKeyResponse : public KeymasterResponse {
    explicit GenerateRkpKeyResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterKeyBlob key_blob;
    KeymasterBlob maced_public_key;
};

struct GenerateCsrRequest : public KeymasterMessage {
    explicit GenerateCsrRequest(int32_t ver) : KeymasterMessage(ver) {}

    ~GenerateCsrRequest() override { delete[] keys_to_sign_array; }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;
    void SetKeyToSign(uint32_t index, const void* data, size_t length);
    void SetEndpointEncCertChain(const void* data, size_t length);
    void SetChallenge(const void* data, size_t length);

    bool test_mode = false;
    size_t num_keys = 0;
    KeymasterBlob* keys_to_sign_array = nullptr;
    KeymasterBlob endpoint_enc_cert_chain;
    KeymasterBlob challenge;
};

struct GenerateCsrResponse : public KeymasterResponse {
    explicit GenerateCsrResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterBlob keys_to_sign_mac;
    KeymasterBlob device_info_blob;
    KeymasterBlob protected_data_blob;
};

struct GenerateCsrV2Request : public KeymasterMessage {
    explicit GenerateCsrV2Request(int32_t ver) : KeymasterMessage(ver) {}

    ~GenerateCsrV2Request() override { delete[] keys_to_sign_array; }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;
    bool InitKeysToSign(uint32_t count);
    void SetKeyToSign(uint32_t index, const void* data, size_t length);
    void SetChallenge(const void* data, size_t length);

    uint32_t num_keys = 0;
    KeymasterBlob* keys_to_sign_array = nullptr;
    KeymasterBlob challenge;
};

struct GenerateCsrV2Response : public KeymasterResponse {
    explicit GenerateCsrV2Response(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterBlob csr;
};

struct GetKeyCharacteristicsRequest : public KeymasterMessage {
    explicit GetKeyCharacteristicsRequest(int32_t ver) : KeymasterMessage(ver) {
        key_blob.key_material = nullptr;
        key_blob.key_material_size = 0;
    }
    ~GetKeyCharacteristicsRequest();

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_key_blob_t key_blob;
    AuthorizationSet additional_params;
};

struct GetKeyCharacteristicsResponse : public KeymasterResponse {
    explicit GetKeyCharacteristicsResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    AuthorizationSet enforced;
    AuthorizationSet unenforced;
};

struct BeginOperationRequest : public KeymasterMessage {
    explicit BeginOperationRequest(int32_t ver) : KeymasterMessage(ver) {
        key_blob.key_material = nullptr;
        key_blob.key_material_size = 0;
    }
    ~BeginOperationRequest() { delete[] key_blob.key_material; }

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_purpose_t purpose;
    keymaster_key_blob_t key_blob;
    AuthorizationSet additional_params;
};

struct BeginOperationResponse : public KeymasterResponse {
    explicit BeginOperationResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_operation_handle_t op_handle;
    AuthorizationSet output_params;
};

struct UpdateOperationRequest : public KeymasterMessage {
    explicit UpdateOperationRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_operation_handle_t op_handle;
    Buffer input;
    AuthorizationSet additional_params;
};

struct UpdateOperationResponse : public KeymasterResponse {
    explicit UpdateOperationResponse(int32_t ver) : KeymasterResponse(ver), input_consumed(0) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    Buffer output;
    size_t input_consumed;
    AuthorizationSet output_params;
};

struct FinishOperationRequest : public KeymasterMessage {
    explicit FinishOperationRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_operation_handle_t op_handle;
    Buffer input;
    Buffer signature;
    AuthorizationSet additional_params;
};

struct FinishOperationResponse : public KeymasterResponse {
    explicit FinishOperationResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    Buffer output;
    AuthorizationSet output_params;
};

struct AbortOperationRequest : public KeymasterMessage {
    explicit AbortOperationRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(uint64_t); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint64_to_buf(buf, end, op_handle);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint64_from_buf(buf_ptr, end, &op_handle);
    }

    keymaster_operation_handle_t op_handle;
};

using AbortOperationResponse = EmptyKeymasterResponse;

struct AddEntropyRequest : public KeymasterMessage {
    explicit AddEntropyRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    Buffer random_data;
};

using AddEntropyResponse = EmptyKeymasterResponse;

struct ImportKeyRequest : public KeymasterMessage {
    explicit ImportKeyRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;
    void SetKeyMaterial(const void* key_material, size_t length);

    AuthorizationSet key_description;
    keymaster_key_format_t key_format;
    KeymasterKeyBlob key_data;
    KeymasterKeyBlob attestation_signing_key_blob;
    AuthorizationSet attest_key_params;
    KeymasterBlob issuer_subject;
};

struct ImportKeyResponse : public KeymasterResponse {
    explicit ImportKeyResponse(int32_t ver)
        : KeymasterResponse(ver), key_blob{}, certificate_chain{} {}
    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterKeyBlob key_blob;
    AuthorizationSet enforced;
    AuthorizationSet unenforced;
    CertificateChain certificate_chain;
};

struct ExportKeyRequest : public KeymasterMessage {
    explicit ExportKeyRequest(int32_t ver) : KeymasterMessage(ver) {
        key_blob.key_material = nullptr;
        key_blob.key_material_size = 0;
    }
    ~ExportKeyRequest() { delete[] key_blob.key_material; }

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    AuthorizationSet additional_params;
    keymaster_key_format_t key_format;
    keymaster_key_blob_t key_blob;
};

struct ExportKeyResponse : public KeymasterResponse {
    explicit ExportKeyResponse(int32_t ver) : KeymasterResponse(ver), key_data(nullptr) {}
    ~ExportKeyResponse() { delete[] key_data; }

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    uint8_t* key_data;
    size_t key_data_length;
};

struct DeleteKeyRequest : public KeymasterMessage {
    explicit DeleteKeyRequest(int32_t ver) : KeymasterMessage(ver) {
        key_blob.key_material = nullptr;
        key_blob.key_material_size = 0;
    }
    ~DeleteKeyRequest() { delete[] key_blob.key_material; }

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_key_blob_t key_blob;
};

using DeleteKeyResponse = EmptyKeymasterResponse;

struct DeleteAllKeysRequest : public EmptyKeymasterRequest {
    explicit DeleteAllKeysRequest(int32_t ver) : EmptyKeymasterRequest(ver) {}
};

using DeleteAllKeysResponse = EmptyKeymasterResponse;

struct DestroyAttestationIdsRequest : public EmptyKeymasterRequest {
    explicit DestroyAttestationIdsRequest(int32_t ver) : EmptyKeymasterRequest(ver) {}
};

using DestroyAttestationIdsResponse = EmptyKeymasterResponse;

struct GetVersionRequest : public EmptyKeymasterRequest {
    // GetVersionRequest ctor takes a version arg so it has the same signature as others, but the
    // value is ignored because it is not not versionable.
    explicit GetVersionRequest(uint32_t /* ver */ = 0)
        : EmptyKeymasterRequest(0 /* not versionable */) {}
};

struct GetVersionResponse : public KeymasterResponse {
    // GetVersionResponse ctor takes a version arg so it has the same signature as others, but the
    // value is ignored because it is not not versionable.
    explicit GetVersionResponse(uint32_t /* ver */ = 0)
        : KeymasterResponse(0 /* not versionable */), major_ver(0), minor_ver(0), subminor_ver(0) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    uint8_t major_ver;
    uint8_t minor_ver;
    uint8_t subminor_ver;
};

struct AttestKeyRequest : public KeymasterMessage {
    explicit AttestKeyRequest(int32_t ver) : KeymasterMessage(ver) {
        key_blob.key_material = nullptr;
        key_blob.key_material_size = 0;
    }
    ~AttestKeyRequest();

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_key_blob_t key_blob;
    AuthorizationSet attest_params;
};

struct AttestKeyResponse : public KeymasterResponse {
    explicit AttestKeyResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    CertificateChain certificate_chain;
};

struct UpgradeKeyRequest : public KeymasterMessage {
    explicit UpgradeKeyRequest(int32_t ver) : KeymasterMessage(ver) { key_blob = {nullptr, 0}; }
    ~UpgradeKeyRequest();

    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_key_blob_t key_blob;
    AuthorizationSet upgrade_params;
};

struct UpgradeKeyResponse : public KeymasterResponse {
    explicit UpgradeKeyResponse(int32_t ver) : KeymasterResponse(ver) {
        upgraded_key = {nullptr, 0};
    }
    ~UpgradeKeyResponse();

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    keymaster_key_blob_t upgraded_key;
};

struct ConfigureRequest : public KeymasterMessage {
    explicit ConfigureRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(os_version) + sizeof(os_patchlevel); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, os_version);
        return append_uint32_to_buf(buf, end, os_patchlevel);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &os_version) &&
               copy_uint32_from_buf(buf_ptr, end, &os_patchlevel);
    }

    uint32_t os_version;
    uint32_t os_patchlevel;  // YYYYMM
};

using ConfigureResponse = EmptyKeymasterResponse;

struct HmacSharingParameters : public Serializable {
    HmacSharingParameters() : seed({}) { memset(nonce, 0, sizeof(nonce)); }
    HmacSharingParameters(HmacSharingParameters&& other) {
        seed = std::move(other.seed);
        memcpy(nonce, other.nonce, sizeof(nonce));
    }

    void SetSeed(KeymasterBlob&& value) { seed = std::move(value); }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterBlob seed{};
    uint8_t nonce[32];
};

struct HmacSharingParametersArray : public Serializable {
    HmacSharingParametersArray() : params_array(nullptr), num_params(0) {}
    HmacSharingParametersArray(HmacSharingParametersArray&& other) {
        delete[] params_array;
        params_array = other.params_array;
        num_params = other.num_params;
        other.params_array = nullptr;
        other.num_params = 0;
    }
    ~HmacSharingParametersArray() override { delete[] params_array; }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    HmacSharingParameters* params_array;
    size_t num_params;
};

struct GetHmacSharingParametersRequest : public EmptyKeymasterRequest {
    explicit GetHmacSharingParametersRequest(int32_t ver) : EmptyKeymasterRequest(ver) {}
};

struct GetHmacSharingParametersResponse : public KeymasterResponse {
    explicit GetHmacSharingParametersResponse(int32_t ver) : KeymasterResponse(ver) {}
    GetHmacSharingParametersResponse(GetHmacSharingParametersResponse&& other)
        : KeymasterResponse(other.message_version), params(std::move(other.params)) {}

    void SetSeed(KeymasterBlob&& seed_data) { params.SetSeed(std::move(seed_data)); }

    size_t NonErrorSerializedSize() const override { return params.SerializedSize(); }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        return params.Serialize(buf, end);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return params.Deserialize(buf_ptr, end);
    }

    HmacSharingParameters params;
};

struct ComputeSharedHmacRequest : public KeymasterMessage {
    explicit ComputeSharedHmacRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return params_array.SerializedSize(); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return params_array.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return params_array.Deserialize(buf_ptr, end);
    }

    HmacSharingParametersArray params_array;
};

struct ComputeSharedHmacResponse : public KeymasterResponse {
    explicit ComputeSharedHmacResponse(int32_t ver) : KeymasterResponse(ver) {}
    ComputeSharedHmacResponse(ComputeSharedHmacResponse&& other)
        : KeymasterResponse(other.message_version) {
        sharing_check = std::move(other.sharing_check);
    }

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterBlob sharing_check;
};

struct ImportWrappedKeyRequest : public KeymasterMessage {
    explicit ImportWrappedKeyRequest(int32_t ver) : KeymasterMessage(ver) {}

    void SetWrappedMaterial(const void* key_material, size_t length);
    void SetWrappingMaterial(const void* key_material, size_t length);
    void SetMaskingKeyMaterial(const void* key_material, size_t length);

    void SetKeyMaterial(const keymaster_key_blob_t& wrapped, const keymaster_key_blob_t& wrapping) {
        SetWrappedMaterial(wrapped.key_material, wrapped.key_material_size);
        SetWrappingMaterial(wrapping.key_material, wrapping.key_material_size);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterKeyBlob wrapped_key;
    KeymasterKeyBlob wrapping_key;
    KeymasterKeyBlob masking_key;
    AuthorizationSet additional_params;
    uint64_t password_sid;
    uint64_t biometric_sid;
};

struct ImportWrappedKeyResponse : public KeymasterResponse {
    explicit ImportWrappedKeyResponse(int32_t ver = kDefaultMessageVersion)
        : KeymasterResponse(ver), key_blob{}, certificate_chain{} {}
    void SetKeyMaterial(const void* key_material, size_t length);
    void SetKeyMaterial(const keymaster_key_blob_t& blob) {
        SetKeyMaterial(blob.key_material, blob.key_material_size);
    }

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    KeymasterKeyBlob key_blob;
    AuthorizationSet enforced;
    AuthorizationSet unenforced;
    CertificateChain certificate_chain;
};

struct HardwareAuthToken : public Serializable {
    HardwareAuthToken() = default;
    HardwareAuthToken(HardwareAuthToken&& other) {
        challenge = other.challenge;
        user_id = other.user_id;
        authenticator_id = other.authenticator_id;
        authenticator_type = other.authenticator_type;
        timestamp = other.timestamp;
        mac = std::move(other.mac);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    uint64_t challenge{};
    uint64_t user_id{};
    uint64_t authenticator_id{};
    hw_authenticator_type_t authenticator_type{};
    uint64_t timestamp{};
    KeymasterBlob mac;
};

struct VerificationToken : public Serializable {
    VerificationToken() = default;
    VerificationToken(VerificationToken&& other) {
        challenge = other.challenge;
        timestamp = other.timestamp;
        parameters_verified = std::move(other.parameters_verified);
        security_level = other.security_level;
        mac = std::move(other.mac);
    }

    size_t SerializedSize() const override;
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override;
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    uint64_t challenge{};
    uint64_t timestamp{};
    AuthorizationSet parameters_verified{};
    keymaster_security_level_t security_level{};
    KeymasterBlob mac{};
};

struct VerifyAuthorizationRequest : public KeymasterMessage {
    explicit VerifyAuthorizationRequest(int32_t ver) : KeymasterMessage(ver) {}
    VerifyAuthorizationRequest(VerifyAuthorizationRequest&& other) = default;

    size_t SerializedSize() const override {
        return sizeof(challenge) + parameters_to_verify.SerializedSize() +
               auth_token.SerializedSize();
    }

    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint64_to_buf(buf, end, challenge);
        buf = parameters_to_verify.Serialize(buf, end);
        return auth_token.Serialize(buf, end);
    }

    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return (copy_uint64_from_buf(buf_ptr, end, &challenge) &&
                parameters_to_verify.Deserialize(buf_ptr, end) &&
                auth_token.Deserialize(buf_ptr, end));
    }

    uint64_t challenge{};
    AuthorizationSet parameters_to_verify;
    HardwareAuthToken auth_token;
};

struct VerifyAuthorizationResponse : public KeymasterResponse {
    explicit VerifyAuthorizationResponse(int32_t ver) : KeymasterResponse(ver) {}
    VerifyAuthorizationResponse(VerifyAuthorizationResponse&& other) = default;

    size_t NonErrorSerializedSize() const override {
        return sizeof(error) + token.SerializedSize();
    }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, error);
        return token.Serialize(buf, end);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &error) && token.Deserialize(buf_ptr, end);
    }

    VerificationToken token;
};

struct EarlyBootEndedRequest : public EmptyKeymasterRequest {
    explicit EarlyBootEndedRequest(int32_t ver) : EmptyKeymasterRequest(ver) {}
};

struct EarlyBootEndedResponse : public KeymasterResponse {
    explicit EarlyBootEndedResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override { return 0; }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t*) const override { return buf; }
    bool NonErrorDeserialize(const uint8_t**, const uint8_t*) override { return true; }
};

struct DeviceLockedRequest : public KeymasterMessage {
    explicit DeviceLockedRequest(int32_t ver) : KeymasterMessage(ver) {}
    explicit DeviceLockedRequest(int32_t ver, bool passwordOnly_, VerificationToken&& token_)
        : KeymasterMessage(ver), passwordOnly(passwordOnly_), token(std::move(token_)) {}

    size_t SerializedSize() const override { return 1; }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        if (buf < end) *buf++ = passwordOnly ? 1 : 0;
        return token.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        if (*buf_ptr >= end) return false;
        passwordOnly = !!*(*buf_ptr)++;
        return token.Deserialize(buf_ptr, end);
    }

    bool passwordOnly;
    VerificationToken token;
};

struct DeviceLockedResponse : public KeymasterResponse {
    explicit DeviceLockedResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override { return 0; }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t*) const override { return buf; }
    bool NonErrorDeserialize(const uint8_t**, const uint8_t*) override { return true; }
};

struct GetVersion2Request : public KeymasterMessage {
    // GetVersion2Request ctor takes a version arg so it has the same signature as others, but the
    // value is ignored because it's not versionable.
    explicit GetVersion2Request(uint32_t /* ver */ = 0)
        : KeymasterMessage(0 /* not versionable */) {}

    size_t SerializedSize() const override { return sizeof(uint32_t); /* max message version */ }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, max_message_version);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &max_message_version);
    }

    uint32_t max_message_version = kDefaultMessageVersion;
};

struct GetVersion2Response : public KeymasterResponse {
    // GetVersion2Request ctor takes a version arg so it has the same signature as others, but the
    // value is ignored because it's not versionable.
    explicit GetVersion2Response(uint32_t /* ver */ = 0)
        : KeymasterResponse(0 /* not versionable */) {}

    size_t NonErrorSerializedSize() const override;
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t*) const override;
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override;

    uint32_t max_message_version;
    KmVersion km_version;
    uint32_t km_date;
};

struct TimestampToken : public Serializable {
    explicit TimestampToken() = default;
    TimestampToken(TimestampToken&& other) {
        challenge = other.challenge;
        timestamp = other.timestamp;
        security_level = other.security_level;
        mac = std::move(other.mac);
    }
    size_t SerializedSize() const override {
        return sizeof(challenge) + sizeof(timestamp) + sizeof(security_level) +
               mac.SerializedSize();
    }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint64_to_buf(buf, end, challenge);
        buf = append_uint64_to_buf(buf, end, timestamp);
        buf = append_uint32_to_buf(buf, end, security_level);
        return mac.Serialize(buf, end);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint64_from_buf(buf_ptr, end, &challenge) &&
               copy_uint64_from_buf(buf_ptr, end, &timestamp) &&
               copy_uint32_from_buf(buf_ptr, end, &security_level) && mac.Deserialize(buf_ptr, end);
    }
    uint64_t challenge{};
    uint64_t timestamp{};
    keymaster_security_level_t security_level{};
    KeymasterBlob mac{};
};

struct GenerateTimestampTokenRequest : public KeymasterMessage {
    explicit GenerateTimestampTokenRequest(int32_t ver) : KeymasterMessage(ver), challenge{} {}
    size_t SerializedSize() const override { return sizeof(challenge); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint64_to_buf(buf, end, challenge);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint64_from_buf(buf_ptr, end, &challenge);
    }
    uint64_t challenge;
};

struct GenerateTimestampTokenResponse : public KeymasterResponse {
    explicit GenerateTimestampTokenResponse(int32_t ver) : KeymasterResponse(ver), token{} {}
    size_t NonErrorSerializedSize() const override { return token.SerializedSize(); }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        return token.Serialize(buf, end);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return token.Deserialize(buf_ptr, end);
    }
    TimestampToken token;
};

struct SetAttestationIdsRequest : public KeymasterMessage {
    explicit SetAttestationIdsRequest(int32_t ver) : KeymasterMessage(ver) {}
    size_t SerializedSize() const override {
        return brand.SerializedSize()           //
               + device.SerializedSize()        //
               + product.SerializedSize()       //
               + serial.SerializedSize()        //
               + imei.SerializedSize()          //
               + meid.SerializedSize()          //
               + manufacturer.SerializedSize()  //
               + model.SerializedSize();
    }

    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = brand.Serialize(buf, end);
        buf = device.Serialize(buf, end);
        buf = product.Serialize(buf, end);
        buf = serial.Serialize(buf, end);
        buf = imei.Serialize(buf, end);
        buf = meid.Serialize(buf, end);
        buf = manufacturer.Serialize(buf, end);
        return model.Serialize(buf, end);
    }

    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return brand.Deserialize(buf_ptr, end)            //
               && device.Deserialize(buf_ptr, end)        //
               && product.Deserialize(buf_ptr, end)       //
               && serial.Deserialize(buf_ptr, end)        //
               && imei.Deserialize(buf_ptr, end)          //
               && meid.Deserialize(buf_ptr, end)          //
               && manufacturer.Deserialize(buf_ptr, end)  //
               && model.Deserialize(buf_ptr, end);        //
    }

    Buffer brand;
    Buffer device;
    Buffer product;
    Buffer serial;
    Buffer imei;
    Buffer meid;
    Buffer manufacturer;
    Buffer model;
};

using SetAttestationIdsResponse = EmptyKeymasterResponse;

struct SetAttestationIdsKM3Request : public KeymasterMessage {
    explicit SetAttestationIdsKM3Request(int32_t ver) : KeymasterMessage(ver), base(ver) {}
    size_t SerializedSize() const override {
        return base.SerializedSize()  //
               + second_imei.SerializedSize();
    }

    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = base.Serialize(buf, end);
        return second_imei.Serialize(buf, end);
    }

    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return base.Deserialize(buf_ptr, end)             //
               && second_imei.Deserialize(buf_ptr, end);  //
    }

    SetAttestationIdsRequest base;
    Buffer second_imei;
};

using SetAttestationIdsKM3Response = EmptyKeymasterResponse;

struct ConfigureVendorPatchlevelRequest : public KeymasterMessage {
    explicit ConfigureVendorPatchlevelRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(vendor_patchlevel); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, vendor_patchlevel);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &vendor_patchlevel);
    }

    uint32_t vendor_patchlevel{};  // YYYYMMDD
};

using ConfigureVendorPatchlevelResponse = EmptyKeymasterResponse;

struct ConfigureBootPatchlevelRequest : public KeymasterMessage {
    explicit ConfigureBootPatchlevelRequest(int32_t ver) : KeymasterMessage(ver) {}

    size_t SerializedSize() const override { return sizeof(boot_patchlevel); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_uint32_to_buf(buf, end, boot_patchlevel);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &boot_patchlevel);
    }

    uint32_t boot_patchlevel{};  // YYYYMMDD
};

using ConfigureBootPatchlevelResponse = EmptyKeymasterResponse;

struct ConfigureVerifiedBootInfoRequest : public KeymasterMessage {
    explicit ConfigureVerifiedBootInfoRequest(int32_t ver)
        : ConfigureVerifiedBootInfoRequest(ver, {}, {}, {}) {}

    ConfigureVerifiedBootInfoRequest(int32_t ver, std::string_view boot_state_param,
                                     std::string_view bootloader_state_param,
                                     std::vector<uint8_t> vbmeta_digest_param)
        : KeymasterMessage(ver), boot_state(boot_state_param),
          bootloader_state(bootloader_state_param), vbmeta_digest(std::move(vbmeta_digest_param)) {}

    size_t SerializedSize() const override {
        return sizeof(uint32_t) + sizeof(uint32_t) + sizeof(uint32_t)  // buffer sizes
               + boot_state.size() + bootloader_state.size() + vbmeta_digest.size();
    }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_collection_to_buf(buf, end, boot_state);
        buf = append_collection_to_buf(buf, end, bootloader_state);
        return append_collection_to_buf(buf, end, vbmeta_digest);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_collection_from_buf(buf_ptr, end, &boot_state) &&
               copy_collection_from_buf(buf_ptr, end, &bootloader_state) &&
               copy_collection_from_buf(buf_ptr, end, &vbmeta_digest);
    }

    std::string boot_state;
    std::string bootloader_state;
    std::vector<uint8_t> vbmeta_digest;
};

using ConfigureVerifiedBootInfoResponse = EmptyKeymasterResponse;

struct GetRootOfTrustRequest : public KeymasterMessage {
    explicit GetRootOfTrustRequest(int32_t ver) : GetRootOfTrustRequest(ver, {}) {}
    GetRootOfTrustRequest(int32_t ver, std::vector<uint8_t> challenge_param)
        : KeymasterMessage(ver), challenge(std::move(challenge_param)){};

    size_t SerializedSize() const override { return sizeof(uint32_t) + challenge.size(); }
    uint8_t* Serialize(uint8_t* buf, const uint8_t* end) const override {
        return append_collection_to_buf(buf, end, challenge);
    }
    bool Deserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_collection_from_buf(buf_ptr, end, &challenge);
    }

    std::vector<uint8_t> challenge;
};

struct GetRootOfTrustResponse : public KeymasterResponse {
    explicit GetRootOfTrustResponse(uint32_t ver) : GetRootOfTrustResponse(ver, {}) {}
    GetRootOfTrustResponse(uint32_t ver, std::vector<uint8_t> rootOfTrust_param)
        : KeymasterResponse(ver), rootOfTrust(std::move(rootOfTrust_param)){};

    size_t NonErrorSerializedSize() const override { return sizeof(uint32_t) + rootOfTrust.size(); }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        return append_collection_to_buf(buf, end, rootOfTrust);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_collection_from_buf(buf_ptr, end, &rootOfTrust);
    }

    std::vector<uint8_t> rootOfTrust;
};

struct GetHwInfoRequest : public EmptyKeymasterRequest {
    explicit GetHwInfoRequest(int32_t ver) : EmptyKeymasterRequest(ver) {}
};

struct GetHwInfoResponse : public KeymasterResponse {
    explicit GetHwInfoResponse(int32_t ver) : KeymasterResponse(ver) {}

    size_t NonErrorSerializedSize() const override {
        return sizeof(version) + sizeof(uint32_t) + rpcAuthorName.size() +
               sizeof(supportedEekCurve) + sizeof(uint32_t) + uniqueId.size() +
               sizeof(supportedNumKeysInCsr);
    }
    uint8_t* NonErrorSerialize(uint8_t* buf, const uint8_t* end) const override {
        buf = append_uint32_to_buf(buf, end, version);
        buf = append_collection_to_buf(buf, end, rpcAuthorName);
        buf = append_uint32_to_buf(buf, end, supportedEekCurve);
        buf = append_collection_to_buf(buf, end, uniqueId);
        return append_uint32_to_buf(buf, end, supportedNumKeysInCsr);
    }
    bool NonErrorDeserialize(const uint8_t** buf_ptr, const uint8_t* end) override {
        return copy_uint32_from_buf(buf_ptr, end, &version) &&
               copy_collection_from_buf(buf_ptr, end, &rpcAuthorName) &&
               copy_uint32_from_buf(buf_ptr, end, &supportedEekCurve) &&
               copy_collection_from_buf(buf_ptr, end, &uniqueId) &&
               copy_uint32_from_buf(buf_ptr, end, &supportedNumKeysInCsr);
    }

    uint32_t version;
    std::string rpcAuthorName;
    uint32_t supportedEekCurve;
    std::string uniqueId;
    uint32_t supportedNumKeysInCsr;
};

}  // namespace keymaster
