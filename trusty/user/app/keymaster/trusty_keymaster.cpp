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

#include "trusty_keymaster.h"
#include "secure_storage_manager.h"

#include <lib/keybox/client/keybox.h>
#include <uapi/err.h>

#include <memory>

namespace keymaster {

GetVersion2Response TrustyKeymaster::GetVersion2(
        const GetVersion2Request& req) {
    switch (req.max_message_version) {
    case 3:
        context_->SetKmVersion(KmVersion::KEYMASTER_4);
        break;

    case 4:
        context_->SetKmVersion(KmVersion::KEYMINT_3);
        break;

    default:
#ifdef FUZZING_BUILD_MODE_UNSAFE_FOR_PRODUCTION
        // In a fuzzing build, if the fuzzer sends invalid messages we should
        // log an error and continue, to allow the fuzzer to explore more of the
        // code.
        LOG_E("HAL sent invalid message version %d, struggling on as fuzzing build",
              req.max_message_version);
        context_->SetKmVersion((req.max_message_version & 0x01)
                                       ? KmVersion::KEYMINT_3
                                       : KmVersion::KEYMASTER_4);
#else
        // By default, if the HAL service is sending invalid messages then the
        // safest thing to do is to terminate.
        LOG_E("HAL sent invalid message version %d, crashing",
              req.max_message_version);
        abort();
#endif
    }

    return AndroidKeymaster::GetVersion2(req);
}

long TrustyKeymaster::GetAuthTokenKey(keymaster_key_blob_t* key) {
    keymaster_error_t error = context_->GetAuthTokenKey(key);
    if (error != KM_ERROR_OK)
        return ERR_GENERIC;
    return NO_ERROR;
}

std::unique_ptr<cppbor::Map> TrustyKeymaster::GetDeviceInfo() {
    return context_->GetDeviceIds();
}

void TrustyKeymaster::SetBootParams(const SetBootParamsRequest& request,
                                    SetBootParamsResponse* response) {
    if (response == nullptr)
        return;

    response->error = context_->SetBootParams(
            request.os_version, request.os_patchlevel,
            request.verified_boot_key, request.verified_boot_state,
            request.device_locked, request.verified_boot_hash);
}

AttestationKeySlot keymaster_algorithm_to_key_slot(
        keymaster_algorithm_t algorithm) {
    switch (algorithm) {
    case KM_ALGORITHM_RSA:
        return AttestationKeySlot::kRsa;
    case KM_ALGORITHM_EC:
        return AttestationKeySlot::kEcdsa;
    default:
        return AttestationKeySlot::kInvalid;
    }
}

void TrustyKeymaster::SetAttestationKey(const SetAttestationKeyRequest& request,
                                        SetAttestationKeyResponse* response) {
    if (response == nullptr)
        return;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    size_t key_size = request.key_data.buffer_size();
    const uint8_t* key = request.key_data.begin();
    AttestationKeySlot key_slot;

    key_slot = keymaster_algorithm_to_key_slot(request.algorithm);
    if (key_slot == AttestationKeySlot::kInvalid) {
        response->error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        return;
    }
    if (key_size == 0) {
        response->error = KM_ERROR_INVALID_INPUT_LENGTH;
        return;
    }
    response->error = ss_manager->WriteKeyToStorage(key_slot, key, key_size);
}

void TrustyKeymaster::DestroyAttestationIds(
        const DestroyAttestationIdsRequest& request,
        DestroyAttestationIdsResponse* response) {
    if (response == nullptr) {
        return;
    }
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }
    response->error = ss_manager->ClearAttestationIds();
}

void TrustyKeymaster::SetAttestationIds(const SetAttestationIdsRequest& request,
                                        EmptyKeymasterResponse* response) {
    if (response == nullptr) {
        return;
    }
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }
    response->error = ss_manager->SetAttestationIds(request);
}

void TrustyKeymaster::SetAttestationIdsKM3(
        const SetAttestationIdsKM3Request& request,
        EmptyKeymasterResponse* response) {
    if (response == nullptr) {
        return;
    }
    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }
    response->error = ss_manager->SetAttestationIdsKM3(request);
}

void TrustyKeymaster::SetWrappedAttestationKey(
        const SetAttestationKeyRequest& request,
        SetAttestationKeyResponse* response) {
    if (response == nullptr) {
        return;
    }
    AttestationKeySlot key_slot =
            keymaster_algorithm_to_key_slot(request.algorithm);
    if (key_slot == AttestationKeySlot::kInvalid) {
        response->error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        return;
    }
    /*
     * This assumes unwrapping decreases size.
     * If it doesn't, the unwrap call will fail.
     */
    size_t unwrapped_buf_size = request.key_data.buffer_size();
    size_t unwrapped_key_size;
    std::unique_ptr<uint8_t[]> unwrapped_key(
            new (std::nothrow) uint8_t[unwrapped_buf_size]);
    if (!unwrapped_key) {
        response->error = KM_ERROR_MEMORY_ALLOCATION_FAILED;
        return;
    }
    int rc = keybox_unwrap(request.key_data.begin(),
                           request.key_data.buffer_size(), unwrapped_key.get(),
                           unwrapped_buf_size, &unwrapped_key_size);
    if (rc != NO_ERROR) {
        response->error = KM_ERROR_VERIFICATION_FAILED;
        return;
    }

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    response->error = ss_manager->WriteKeyToStorage(
            key_slot, unwrapped_key.get(), unwrapped_key_size);
}

void TrustyKeymaster::ClearAttestationCertChain(
        const ClearAttestationCertChainRequest& request,
        ClearAttestationCertChainResponse* response) {
    if (response == nullptr)
        return;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    AttestationKeySlot key_slot;

    key_slot = keymaster_algorithm_to_key_slot(request.algorithm);
    if (key_slot == AttestationKeySlot::kInvalid) {
        response->error = KM_ERROR_UNSUPPORTED_ALGORITHM;
        return;
    }

    keymaster_error_t err = ss_manager->DeleteCertChainFromStorage(key_slot);
    if (err != KM_ERROR_OK) {
        LOG_E("Failed to delete cert chain.\n");
        response->error = err;
        return;
    }

    uint32_t cert_chain_length = 0;
    err = ss_manager->ReadCertChainLength(key_slot, &cert_chain_length);
    if (err != KM_ERROR_OK) {
        LOG_E("Failed to read cert chain length.\n");
        response->error = err;
        return;
    }
    if (cert_chain_length != 0) {
        LOG_E("Cert chain could not be deleted.\n");
        response->error = err;
        return;
    }

    response->error = KM_ERROR_OK;
}

void TrustyKeymaster::AppendAttestationCertChain(
        const AppendAttestationCertChainRequest& request,
        AppendAttestationCertChainResponse* response) {
    if (response == nullptr)
        return;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    size_t cert_size = request.cert_data.buffer_size();
    const uint8_t* cert = request.cert_data.begin();
    AttestationKeySlot key_slot;

    response->error = KM_ERROR_UNSUPPORTED_ALGORITHM;
    switch (request.algorithm) {
    case KM_ALGORITHM_RSA:
        key_slot = AttestationKeySlot::kRsa;
        break;
    case KM_ALGORITHM_EC:
        key_slot = AttestationKeySlot::kEcdsa;
        break;
    default:
        return;
    }
    response->error = KM_ERROR_INVALID_INPUT_LENGTH;
    if (cert_size == 0) {
        return;
    }
    uint32_t cert_chain_length = 0;
    if (ss_manager->ReadCertChainLength(key_slot, &cert_chain_length) !=
        KM_ERROR_OK) {
        LOG_E("Failed to read cert chain length, initialize to 0.\n");
        cert_chain_length = 0;
    }
    response->error = ss_manager->WriteCertToStorage(key_slot, cert, cert_size,
                                                     cert_chain_length);
}

void TrustyKeymaster::AtapGetCaRequest(const AtapGetCaRequestRequest& request,
                                       AtapGetCaRequestResponse* response) {
    if (response == nullptr)
        return;

    // Not implemented.
    response->error = KM_ERROR_UNKNOWN_ERROR;
}

void TrustyKeymaster::AtapSetCaResponseBegin(
        const AtapSetCaResponseBeginRequest& request,
        AtapSetCaResponseBeginResponse* response) {
    if (response == nullptr)
        return;

    // Not implemented.
    response->error = KM_ERROR_UNKNOWN_ERROR;
}

void TrustyKeymaster::AtapSetCaResponseUpdate(
        const AtapSetCaResponseUpdateRequest& request,
        AtapSetCaResponseUpdateResponse* response) {
    if (response == nullptr)
        return;

    // Not implemented.
    response->error = KM_ERROR_UNKNOWN_ERROR;
}

void TrustyKeymaster::AtapSetCaResponseFinish(
        const AtapSetCaResponseFinishRequest& request,
        AtapSetCaResponseFinishResponse* response) {
    if (response == nullptr)
        return;

    // Not implemented.
    response->error = KM_ERROR_UNKNOWN_ERROR;
}

void TrustyKeymaster::AtapReadUuid(const AtapReadUuidRequest& request,
                                   AtapReadUuidResponse* response) {
    if (response == nullptr)
        return;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    uint8_t uuid[kAttestationUuidSize]{};
    response->error = ss_manager->ReadAttestationUuid(uuid);

    if (response->error == KM_ERROR_OK) {
        response->data.reserve(kAttestationUuidSize);
        response->data.write(uuid, kAttestationUuidSize);
    }
}

void TrustyKeymaster::AtapSetProductId(const AtapSetProductIdRequest& request,
                                       AtapSetProductIdResponse* response) {
    if (response == nullptr)
        return;

    SecureStorageManager* ss_manager = SecureStorageManager::get_instance();
    if (ss_manager == nullptr) {
        response->error = KM_ERROR_SECURE_HW_COMMUNICATION_FAILED;
        return;
    }

    // Not implemented.
    response->error = KM_ERROR_UNKNOWN_ERROR;
}
}  // namespace keymaster
