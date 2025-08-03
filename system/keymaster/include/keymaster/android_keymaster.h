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

#include "android_keymaster_messages.h"
#include <keymaster/android_keymaster_messages.h>
#include <keymaster/authorization_set.h>

namespace keymaster {

class Key;
class KeyFactory;
class KeymasterContext;
class OperationTable;

/**
 * This is the reference implementation of Keymaster.  In addition to acting as a reference for
 * other Keymaster implementers to check their assumptions against, it is used by Keystore as the
 * default implementation when no secure implementation is available, and may be installed and
 * executed in secure hardware as a secure implementation.
 *
 * Note that this class doesn't actually implement the Keymaster HAL interface, instead it
 * implements an alternative API which is similar to and based upon the HAL, but uses C++ "message"
 * classes which support serialization.
 *
 * For non-secure, pure software implementation there is a HAL translation layer that converts the
 * HAL's parameters to and from the message representations, which are then passed in to this
 * API.
 *
 * For secure implementation there is another HAL translation layer that serializes the messages to
 * the TEE. In the TEE implementation there's another component which deserializes the messages,
 * extracts the relevant parameters and calls this API.
 */
class AndroidKeymaster {
  public:
    AndroidKeymaster(KeymasterContext* context, size_t operation_table_size,
                     int32_t message_version = kDefaultMessageVersion);
    virtual ~AndroidKeymaster();
    AndroidKeymaster(AndroidKeymaster&&);

    void GetVersion(const GetVersionRequest& request, GetVersionResponse* response);
    void SupportedAlgorithms(const SupportedAlgorithmsRequest& request,
                             SupportedAlgorithmsResponse* response);
    void SupportedBlockModes(const SupportedBlockModesRequest& request,
                             SupportedBlockModesResponse* response);
    void SupportedPaddingModes(const SupportedPaddingModesRequest& request,
                               SupportedPaddingModesResponse* response);
    void SupportedDigests(const SupportedDigestsRequest& request,
                          SupportedDigestsResponse* response);
    void SupportedImportFormats(const SupportedImportFormatsRequest& request,
                                SupportedImportFormatsResponse* response);
    void SupportedExportFormats(const SupportedExportFormatsRequest& request,
                                SupportedExportFormatsResponse* response);

    GetHmacSharingParametersResponse GetHmacSharingParameters();
    ComputeSharedHmacResponse ComputeSharedHmac(const ComputeSharedHmacRequest& request);
    VerifyAuthorizationResponse VerifyAuthorization(const VerifyAuthorizationRequest& request);
    void GenerateTimestampToken(GenerateTimestampTokenRequest& request,
                                GenerateTimestampTokenResponse* response);
    void AddRngEntropy(const AddEntropyRequest& request, AddEntropyResponse* response);
    void Configure(const ConfigureRequest& request, ConfigureResponse* response);
    void GenerateKey(const GenerateKeyRequest& request, GenerateKeyResponse* response);
    void GenerateRkpKey(const GenerateRkpKeyRequest& request, GenerateRkpKeyResponse* response);
    void GenerateCsr(const GenerateCsrRequest& request, GenerateCsrResponse* response);
    void GenerateCsrV2(const GenerateCsrV2Request& request, GenerateCsrV2Response* response);
    void GetKeyCharacteristics(const GetKeyCharacteristicsRequest& request,
                               GetKeyCharacteristicsResponse* response);
    void ImportKey(const ImportKeyRequest& request, ImportKeyResponse* response);
    void ImportWrappedKey(const ImportWrappedKeyRequest& request,
                          ImportWrappedKeyResponse* response);
    void ExportKey(const ExportKeyRequest& request, ExportKeyResponse* response);
    void AttestKey(const AttestKeyRequest& request, AttestKeyResponse* response);
    void UpgradeKey(const UpgradeKeyRequest& request, UpgradeKeyResponse* response);
    void DeleteKey(const DeleteKeyRequest& request, DeleteKeyResponse* response);
    void DeleteAllKeys(const DeleteAllKeysRequest& request, DeleteAllKeysResponse* response);
    void BeginOperation(const BeginOperationRequest& request, BeginOperationResponse* response);
    void UpdateOperation(const UpdateOperationRequest& request, UpdateOperationResponse* response);
    void FinishOperation(const FinishOperationRequest& request, FinishOperationResponse* response);
    void AbortOperation(const AbortOperationRequest& request, AbortOperationResponse* response);

    EarlyBootEndedResponse EarlyBootEnded();
    DeviceLockedResponse DeviceLocked(const DeviceLockedRequest& request);
    GetVersion2Response GetVersion2(const GetVersion2Request& request);
    ConfigureVendorPatchlevelResponse
    ConfigureVendorPatchlevel(const ConfigureVendorPatchlevelRequest& request);
    ConfigureBootPatchlevelResponse
    ConfigureBootPatchlevel(const ConfigureBootPatchlevelRequest& request);
    ConfigureVerifiedBootInfoResponse
    ConfigureVerifiedBootInfo(const ConfigureVerifiedBootInfoRequest& request);
    GetRootOfTrustResponse GetRootOfTrust(const GetRootOfTrustRequest& request);
    GetHwInfoResponse GetHwInfo();
    SetAttestationIdsResponse SetAttestationIds(const SetAttestationIdsRequest& request);
    SetAttestationIdsKM3Response SetAttestationIdsKM3(const SetAttestationIdsKM3Request& request);

    bool has_operation(keymaster_operation_handle_t op_handle) const;

    // Returns the message version negotiated in GetVersion2.  All response messages should have
    // this passed to their constructors.  This is done automatically for the methods that return a
    // response by value.  The caller must do it for the methods that take a response pointer.
    int32_t message_version() const { return message_version_; }

  private:
    // Loads the KM key from `key_blob`, getting app ID and app data from `additional_params`, if
    // needed.  If loading the key fails for any reason (including failure of the version binding
    // check), the returned UniquePtr is null and `*error` is set (`error` must not be null).
    UniquePtr<Key> LoadKey(const keymaster_key_blob_t& key_blob,
                           const AuthorizationSet& additional_params, keymaster_error_t* error);

    UniquePtr<KeymasterContext> context_;
    UniquePtr<OperationTable> operation_table_;

    // If the caller doesn't bother to use GetVersion2 or GetVersion to configure the message
    // version, assume kDefaultVersion, i.e. assume the client and server always support the
    // latest default, which is the latest, except when experimental features are being added.
    uint32_t message_version_;
};

}  // namespace keymaster
