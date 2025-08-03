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

#pragma once

#include <keymaster/android_keymaster_messages.h>
#include <keymaster/serializable.h>

namespace keymaster {
enum class SerializableType : uint32_t {
    SUPPORTED_IMPORT_FORMATS_REQUEST,
    SUPPORTED_EXPORT_FORMATS_REQUEST,
    SUPPORTED_BLOCK_MODES_REQUEST,
    SUPPORTED_PADDING_MODES_REQUEST,
    SUPPORTED_DIGESTS_REQUEST,
    SUPPORTED_ALGORITHMS_RESPONSE,
    SUPPORTED_BLOCK_MODES_RESPONSE,
    SUPPORTED_PADDING_MODES_RESPONSE,
    SUPPORTED_DIGESTS_RESPONSE,
    SUPPORTED_IMPORT_FORMATS_RESPONSE,
    SUPPORTED_EXPORT_FORMATS_RESPONSE,
    GENERATE_KEY_REQUEST,
    GENERATE_KEY_RESPONSE,
    GET_KEY_CHARACTERISTICS_REQUEST,
    GET_KEY_CHARACTERISTICS_RESPONSE,
    BEGIN_OPERATION_REQUEST,
    BEGIN_OPERATION_RESPONSE,
    UPDATE_OPERATION_REQUEST,
    UPDATE_OPERATION_RESPONSE,
    FINISH_OPERATION_REQUEST,
    FINISH_OPERATION_RESPONSE,
    ABORT_OPERATION_REQUEST,
    ABORT_OPERATION_RESPONSE,
    ADD_ENTROPY_REQUEST,
    ADD_ENTROPY_RESPONSE,
    IMPORT_KEY_REQUEST,
    IMPORT_KEY_RESPONSE,
    EXPORT_KEY_REQUEST,
    EXPORT_KEY_RESPONSE,
    DELETE_KEY_REQUEST,
    DELETE_KEY_RESPONSE,
    DELETE_ALL_KEYS_REQUEST,
    DELETE_ALL_KEYS_RESPONSE,
    GET_VERSION_REQUEST,
    GET_VERSION_RESPONSE,
    GET_VERSION2_REQUEST,
    GET_VERSION2_RESPONSE,
    ATTEST_KEY_REQUEST,
    ATTEST_KEY_RESPONSE,
    UPGRADE_KEY_REQUEST,
    UPGRADE_KEY_RESPONSE,
    CONFIGURE_REQUEST,
    CONFIGURE_RESPONSE,
    HMAC_SHARING_PARAMETERS,
    HMAC_SHARING_PARAMETERS_ARRAY,
    GET_HMAC_SHARING_PARAMETERS_RESPONSE,
    COMPUTE_SHARED_HMAC_REQUEST,
    COMPUTE_SHARED_HMAC_RESPONSE,
    IMPORT_WRAPPED_KEY_REQUEST,
    IMPORT_WRAPPED_KEY_RESPONSE,
    HARDWARE_AUTH_TOKEN,
    VERIFICATION_TOKEN,
    VERIFY_AUTHORIZATION_REQUEST,
    VERIFY_AUTHORIZATION_RESPONSE,
    DEVICE_LOCKED_REQUEST,
    BUFFER,
    // Libfuzzer needs this to always be the last value
    kMaxValue = BUFFER
};

std::unique_ptr<Serializable> getSerializable(SerializableType serType) {
    switch (serType) {
    case SerializableType::SUPPORTED_IMPORT_FORMATS_REQUEST:
        return std::make_unique<SupportedImportFormatsRequest>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_EXPORT_FORMATS_REQUEST:
        return std::make_unique<SupportedExportFormatsRequest>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_BLOCK_MODES_REQUEST:
        return std::make_unique<SupportedBlockModesRequest>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_PADDING_MODES_REQUEST:
        return std::make_unique<SupportedPaddingModesRequest>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_DIGESTS_REQUEST:
        return std::make_unique<SupportedDigestsRequest>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_ALGORITHMS_RESPONSE:
        return std::make_unique<SupportedAlgorithmsResponse>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_BLOCK_MODES_RESPONSE:
        return std::make_unique<SupportedBlockModesResponse>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_PADDING_MODES_RESPONSE:
        return std::make_unique<SupportedPaddingModesResponse>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_DIGESTS_RESPONSE:
        return std::make_unique<SupportedDigestsResponse>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_IMPORT_FORMATS_RESPONSE:
        return std::make_unique<SupportedImportFormatsResponse>(kMaxMessageVersion);
    case SerializableType::SUPPORTED_EXPORT_FORMATS_RESPONSE:
        return std::make_unique<SupportedExportFormatsResponse>(kMaxMessageVersion);
    case SerializableType::GENERATE_KEY_REQUEST:
        return std::make_unique<GenerateKeyRequest>(kMaxMessageVersion);
    case SerializableType::GENERATE_KEY_RESPONSE:
        return std::make_unique<GenerateKeyResponse>(kMaxMessageVersion);
    case SerializableType::GET_KEY_CHARACTERISTICS_REQUEST:
        return std::make_unique<GetKeyCharacteristicsRequest>(kMaxMessageVersion);
    case SerializableType::GET_KEY_CHARACTERISTICS_RESPONSE:
        return std::make_unique<GetKeyCharacteristicsResponse>(kMaxMessageVersion);
    case SerializableType::BEGIN_OPERATION_REQUEST:
        return std::make_unique<BeginOperationRequest>(kMaxMessageVersion);
    case SerializableType::BEGIN_OPERATION_RESPONSE:
        return std::make_unique<BeginOperationResponse>(kMaxMessageVersion);
    case SerializableType::UPDATE_OPERATION_REQUEST:
        return std::make_unique<UpdateOperationRequest>(kMaxMessageVersion);
    case SerializableType::UPDATE_OPERATION_RESPONSE:
        return std::make_unique<UpdateOperationResponse>(kMaxMessageVersion);
    case SerializableType::FINISH_OPERATION_REQUEST:
        return std::make_unique<FinishOperationRequest>(kMaxMessageVersion);
    case SerializableType::FINISH_OPERATION_RESPONSE:
        return std::make_unique<FinishOperationResponse>(kMaxMessageVersion);
    case SerializableType::ABORT_OPERATION_REQUEST:
        return std::make_unique<AbortOperationRequest>(kMaxMessageVersion);
    case SerializableType::ABORT_OPERATION_RESPONSE:
        return std::make_unique<AbortOperationResponse>(kMaxMessageVersion);
    case SerializableType::ADD_ENTROPY_REQUEST:
        return std::make_unique<AddEntropyRequest>(kMaxMessageVersion);
    case SerializableType::ADD_ENTROPY_RESPONSE:
        return std::make_unique<AddEntropyResponse>(kMaxMessageVersion);
    case SerializableType::IMPORT_KEY_REQUEST:
        return std::make_unique<ImportKeyRequest>(kMaxMessageVersion);
    case SerializableType::IMPORT_KEY_RESPONSE:
        return std::make_unique<ImportKeyResponse>(kMaxMessageVersion);
    case SerializableType::EXPORT_KEY_REQUEST:
        return std::make_unique<ExportKeyRequest>(kMaxMessageVersion);
    case SerializableType::EXPORT_KEY_RESPONSE:
        return std::make_unique<ExportKeyResponse>(kMaxMessageVersion);
    case SerializableType::DELETE_KEY_REQUEST:
        return std::make_unique<DeleteKeyRequest>(kMaxMessageVersion);
    case SerializableType::DELETE_KEY_RESPONSE:
        return std::make_unique<DeleteKeyResponse>(kMaxMessageVersion);
    case SerializableType::DELETE_ALL_KEYS_REQUEST:
        return std::make_unique<DeleteAllKeysRequest>(kMaxMessageVersion);
    case SerializableType::DELETE_ALL_KEYS_RESPONSE:
        return std::make_unique<DeleteAllKeysResponse>(kMaxMessageVersion);
    case SerializableType::GET_VERSION_REQUEST:
        // Not versionable
        return std::make_unique<GetVersionRequest>();
    case SerializableType::GET_VERSION_RESPONSE:
        // Not versionable
        return std::make_unique<GetVersionResponse>();
    case SerializableType::GET_VERSION2_REQUEST:
        // Not versionable
        return std::make_unique<GetVersion2Request>();
    case SerializableType::GET_VERSION2_RESPONSE:
        // Not versionable
        return std::make_unique<GetVersion2Response>();
    case SerializableType::ATTEST_KEY_REQUEST:
        return std::make_unique<AttestKeyRequest>(kMaxMessageVersion);
    case SerializableType::ATTEST_KEY_RESPONSE:
        return std::make_unique<AttestKeyResponse>(kMaxMessageVersion);
    case SerializableType::UPGRADE_KEY_REQUEST:
        return std::make_unique<UpgradeKeyRequest>(kMaxMessageVersion);
    case SerializableType::UPGRADE_KEY_RESPONSE:
        return std::make_unique<UpgradeKeyResponse>(kMaxMessageVersion);
    case SerializableType::CONFIGURE_REQUEST:
        return std::make_unique<ConfigureRequest>(kMaxMessageVersion);
    case SerializableType::CONFIGURE_RESPONSE:
        return std::make_unique<ConfigureResponse>(kMaxMessageVersion);
    case SerializableType::DEVICE_LOCKED_REQUEST:
        return std::make_unique<DeviceLockedRequest>(kMaxMessageVersion);
    case SerializableType::GET_HMAC_SHARING_PARAMETERS_RESPONSE:
        return std::make_unique<GetHmacSharingParametersResponse>(kMaxMessageVersion);
    case SerializableType::COMPUTE_SHARED_HMAC_REQUEST:
        return std::make_unique<ComputeSharedHmacRequest>(kMaxMessageVersion);
    case SerializableType::COMPUTE_SHARED_HMAC_RESPONSE:
        return std::make_unique<ComputeSharedHmacResponse>(kMaxMessageVersion);
    case SerializableType::IMPORT_WRAPPED_KEY_REQUEST:
        return std::make_unique<ImportWrappedKeyRequest>(kMaxMessageVersion);
    case SerializableType::IMPORT_WRAPPED_KEY_RESPONSE:
        return std::make_unique<ImportWrappedKeyResponse>(kMaxMessageVersion);
    case SerializableType::VERIFY_AUTHORIZATION_REQUEST:
        return std::make_unique<VerifyAuthorizationRequest>(kMaxMessageVersion);
    case SerializableType::VERIFY_AUTHORIZATION_RESPONSE:
        return std::make_unique<VerifyAuthorizationResponse>(kMaxMessageVersion);

    // These are not messages, and expect an empty constructor.
    case SerializableType::HMAC_SHARING_PARAMETERS:
        return std::make_unique<HmacSharingParameters>();
    case SerializableType::HMAC_SHARING_PARAMETERS_ARRAY:
        return std::make_unique<HmacSharingParametersArray>();
    case SerializableType::HARDWARE_AUTH_TOKEN:
        return std::make_unique<HardwareAuthToken>();
    case SerializableType::VERIFICATION_TOKEN:
        return std::make_unique<VerificationToken>();
    case SerializableType::BUFFER:
        return std::make_unique<Buffer>();
    }
}
}  // namespace keymaster
