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

#include <keymaster/android_keymaster.h>
#include <keymaster/logger.h>

#include "trusty_keymaster_context.h"
#include "trusty_keymaster_messages.h"

namespace keymaster {

// TrustyKeymaster implements handlers for IPC operations. Most operations are
// implemented by AndroidKeymaster but some operations which are not part of the
// interface with Android are implemented here. These operations are expected to
// be called from a bootloader or another Trusty application.
class TrustyKeymaster : public AndroidKeymaster {
public:
    TrustyKeymaster(TrustyKeymasterContext* context,
                    size_t operation_table_size)
            : AndroidKeymaster(context, operation_table_size),
              context_(context) {
        LOG_D("Creating TrustyKeymaster");
    }

    // Replace GetVersion2 handler from AndroidKeymaster.  It's not virtual, but
    // that's okay because it's only called non-polymorphically.
    GetVersion2Response GetVersion2(const GetVersion2Request& req);

    // The GetAuthTokenKey IPC call is accepted only from Gatekeeper.
    long GetAuthTokenKey(keymaster_key_blob_t* key);

    // Retrieve the device information needed for remote key provisioning.
    std::unique_ptr<cppbor::Map> GetDeviceInfo();

    // SetBootParams can only be called once. If it is never called then
    // Keymaster will fail to configure. The intention is that it is called from
    // the bootloader.
    void SetBootParams(const SetBootParamsRequest& request,
                       SetBootParamsResponse* response);

    // SetAttestastionKey sets a single attestation key. There should be one
    // call for each supported algorithm.
    void SetAttestationKey(const SetAttestationKeyRequest& request,
                           SetAttestationKeyResponse* response);

    // SetWrappedAttestationKey sets a single attestation key. There should be
    // one call for each supported algorithm.
    void SetWrappedAttestationKey(const SetAttestationKeyRequest& request,
                                  SetAttestationKeyResponse* response);

    // DestroyAttestationIds clears the device IDs.
    void DestroyAttestationIds(const DestroyAttestationIdsRequest& request,
                               DestroyAttestationIdsResponse* response);

    // SetDeviceIds sets all device IDs in the KM spec under ATTESTATION_ID_*
    // This is a factory provisioning step that should not be callable after
    // provisioning.
    void SetAttestationIds(const SetAttestationIdsRequest& request,
                           EmptyKeymasterResponse* response);

    // SetAttestationIdsKM3 sets the device IDs added to the spec under KM3
    // This is a factory provisioning step that should not be callable after
    // provisioning.
    void SetAttestationIdsKM3(const SetAttestationIdsKM3Request& request,
                              EmptyKeymasterResponse* response);

    // ClearAttestationCertChain clears the attestation certificate chain for
    // the specified algorithm.
    void ClearAttestationCertChain(
            const ClearAttestationCertChainRequest& request,
            ClearAttestationCertChainResponse* response);

    // AppendAttestationCertChain sets a single certificate in an attestation
    // certificate chain. The bootloader should push certificates into Trusty,
    // one certificate per request, starting with the attestation certificate.
    // Multiple AppendAttestationCertChain requests are expected.
    void AppendAttestationCertChain(
            const AppendAttestationCertChainRequest& request,
            AppendAttestationCertChainResponse* response);

    // AtapGetCaRequest is the first of two calls that are part of the the
    // Android Things Attestation Provisioning (ATAP) protocol. This protocol is
    // used instead of SetAttestationKey and AppendAttestationCertChain.
    void AtapGetCaRequest(const AtapGetCaRequestRequest& request,
                          AtapGetCaRequestResponse* response);

    // AtapSetCaResponse is the second of two calls that are part of the the
    // Android Things Attestation Provisioning (ATAP) protocol. This protocol is
    // used instead of SetAttestationKey and AppendAttestationCertChain. The CA
    // Response message is larger than 4k, so the call is split into Begin,
    // Update, and Finish messages.
    void AtapSetCaResponseBegin(const AtapSetCaResponseBeginRequest& request,
                                AtapSetCaResponseBeginResponse* response);

    void AtapSetCaResponseUpdate(const AtapSetCaResponseUpdateRequest& request,
                                 AtapSetCaResponseUpdateResponse* response);
    void AtapSetCaResponseFinish(const AtapSetCaResponseFinishRequest& request,
                                 AtapSetCaResponseFinishResponse* response);

    // Reads the UUID from the certificate of the last provisioned attestation
    // credentials.
    void AtapReadUuid(const AtapReadUuidRequest& request,
                      AtapReadUuidResponse* response);

    // SetProductId is only called once to set the secure product id. Caller
    // should read the product id from permanent attributes structure and set
    // the product id while fusing the permanent attributes.
    void AtapSetProductId(const AtapSetProductIdRequest& request,
                          AtapSetProductIdResponse* response);

    bool ConfigureCalled() {
        return configure_error_ != KM_ERROR_KEYMASTER_NOT_CONFIGURED;
    }
    keymaster_error_t get_configure_error() { return configure_error_; }
    void set_configure_error(keymaster_error_t err) { configure_error_ = err; }

private:
    TrustyKeymasterContext* context_;
    keymaster_error_t configure_error_ = KM_ERROR_KEYMASTER_NOT_CONFIGURED;
    Buffer ca_response_;
};

}  // namespace keymaster
