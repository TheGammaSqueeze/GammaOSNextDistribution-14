/******************************************************************************
 **
 ** The original Work has been changed by NXP.
 **
 ** Licensed under the Apache License, Version 2.0 (the "License");
 ** you may not use this file except in compliance with the License.
 ** You may obtain a copy of the License at
 **
 ** http://www.apache.org/licenses/LICENSE-2.0
 **
 ** Unless required by applicable law or agreed to in writing, software
 ** distributed under the License is distributed on an "AS IS" BASIS,
 ** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 ** See the License for the specific language governing permissions and
 ** limitations under the License.
 **
 ** Copyright 2021-2022 NXP
 **
 *********************************************************************************/
#define LOG_TAG "javacard.strongbox.keymint.operation-impl"
#include "JavacardSharedSecret.h"

#include <android-base/logging.h>

#include <KeyMintUtils.h>
#include <memunreachable/memunreachable.h>

/* 1 sec delay till OMAPI service initialized (~ 30 to 40 secs)
 * 20 retry as per transport layer retry logic.
 * Each retry logic takes 11~12 secs*/
#define MAX_SHARED_SECRET_RETRY_COUNT 60

namespace aidl::android::hardware::security::sharedsecret {
using ::keymint::javacard::Instruction;

static uint8_t getSharedSecretRetryCount = 0x00;

ScopedAStatus JavacardSharedSecret::getSharedSecretParameters(SharedSecretParameters* params) {
    auto error = card_->initializeJavacard();
    if (error != KM_ERROR_OK) {
        LOG(ERROR) << "Error in initializing javacard.";
    }
    auto [item, err] = card_->sendRequest(Instruction::INS_GET_SHARED_SECRET_PARAM_CMD);
#ifdef NXP_EXTNS
    if (err != KM_ERROR_OK && (getSharedSecretRetryCount < MAX_SHARED_SECRET_RETRY_COUNT)) {
        getSharedSecretRetryCount++;
    } else if (err != KM_ERROR_OK) {
        std::vector<uint8_t> refNonceSeed = {
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00,
          0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00 };
        params->seed.assign(refNonceSeed.begin(), refNonceSeed.end());
        params->nonce.assign(refNonceSeed.begin(), refNonceSeed.end());
        err = KM_ERROR_OK;
        return ScopedAStatus::ok();
    }
#endif
    if (err != KM_ERROR_OK) {
        LOG(ERROR) << "Error in sending in getSharedSecretParameters.";
        return keymint::km_utils::kmError2ScopedAStatus(err);
    }
    auto optSSParams = cbor_.getSharedSecretParameters(item, 1);
    if (!optSSParams) {
        LOG(ERROR) << "Error in sending in getSharedSecretParameters.";
        return keymint::km_utils::kmError2ScopedAStatus(KM_ERROR_UNKNOWN_ERROR);
    }
    *params = std::move(optSSParams.value());
    return ScopedAStatus::ok();
}

ScopedAStatus
JavacardSharedSecret::computeSharedSecret(const std::vector<SharedSecretParameters>& params,
                                          std::vector<uint8_t>* secret) {
    card_->sendPendingEvents();
    auto error = card_->initializeJavacard();
    if (error != KM_ERROR_OK) {
        LOG(ERROR) << "Error in initializing javacard.";
    }
    cppbor::Array request;
    cbor_.addSharedSecretParameters(request, params);
    auto [item, err] = card_->sendRequest(Instruction::INS_COMPUTE_SHARED_SECRET_CMD, request);
    if (err != KM_ERROR_OK) {
        LOG(ERROR) << "Error in sending in computeSharedSecret.";
        return keymint::km_utils::kmError2ScopedAStatus(err);
    }
    auto optSecret = cbor_.getByteArrayVec(item, 1);
    if (!optSecret) {
        LOG(ERROR) << "Error in decoding the response in computeSharedSecret.";
        return keymint::km_utils::kmError2ScopedAStatus(KM_ERROR_UNKNOWN_ERROR);
    }
    *secret = std::move(optSecret.value());
    return ScopedAStatus::ok();
}
binder_status_t JavacardSharedSecret::dump(int /* fd */, const char** /* p */, uint32_t /* q */) {
    LOG(INFO) << "\n KeyMint-JavacardSharedSecret HAL MemoryLeak Info = \n"
              << ::android::GetUnreachableMemoryString(true, 10000).c_str();
    return STATUS_OK;
}
}  // namespace aidl::android::hardware::security::sharedsecret
