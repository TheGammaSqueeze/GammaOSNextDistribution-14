/*
 * Copyright (c) 2021, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#define LOG_TAG "credstore"

#include <android-base/logging.h>
#include <android/binder_manager.h>
#include <android/hardware/identity/support/IdentityCredentialSupport.h>

#include <android/security/identity/ICredentialStore.h>
#include <android/security/identity/ISession.h>

#include "Session.h"
#include "Util.h"

namespace android {
namespace security {
namespace identity {

using std::optional;

using ::android::hardware::identity::IPresentationSession;
using ::android::hardware::identity::IWritableIdentityCredential;

using ::android::hardware::identity::support::ecKeyPairGetPkcs12;
using ::android::hardware::identity::support::ecKeyPairGetPrivateKey;
using ::android::hardware::identity::support::ecKeyPairGetPublicKey;
using ::android::hardware::identity::support::hexdump;
using ::android::hardware::identity::support::sha256;

Status Session::getEphemeralKeyPair(vector<uint8_t>* _aidl_return) {
    vector<uint8_t> keyPair;
    Status status = halBinder_->getEphemeralKeyPair(&keyPair);
    if (!status.isOk()) {
        return halStatusToGenericError(status);
    }
    time_t nowSeconds = std::chrono::system_clock::to_time_t(std::chrono::system_clock::now());
    time_t validityNotBefore = nowSeconds;
    time_t validityNotAfter = nowSeconds + 24 * 60 * 60;
    optional<vector<uint8_t>> pkcs12Bytes = ecKeyPairGetPkcs12(keyPair,
                                                               "ephemeralKey",  // Alias for key
                                                               "0",  // Serial, as a decimal number
                                                               "Credstore",      // Issuer
                                                               "Ephemeral Key",  // Subject
                                                               validityNotBefore, validityNotAfter);
    if (!pkcs12Bytes) {
        return Status::fromServiceSpecificError(ICredentialStore::ERROR_GENERIC,
                                                "Error creating PKCS#12 structure for key pair");
    }
    *_aidl_return = pkcs12Bytes.value();
    return Status::ok();
}

Status Session::setReaderEphemeralPublicKey(const vector<uint8_t>& publicKey) {
    Status status = halBinder_->setReaderEphemeralPublicKey(publicKey);
    if (!status.isOk()) {
        return halStatusToGenericError(status);
    }
    return Status::ok();
}

Status Session::setSessionTranscript(const vector<uint8_t>& sessionTranscript) {
    Status status = halBinder_->setSessionTranscript(sessionTranscript);
    if (!status.isOk()) {
        return halStatusToGenericError(status);
    }
    return Status::ok();
}

Status Session::getCredentialForPresentation(const string& credentialName,
                                             sp<ICredential>* _aidl_return) {
    return store_->getCredentialCommon(credentialName, cipherSuite_, halBinder_, _aidl_return);
}

Status Session::getAuthChallenge(int64_t* _aidl_return) {
    *_aidl_return = 0;
    int64_t authChallenge;
    Status status = halBinder_->getAuthChallenge(&authChallenge);
    if (!status.isOk()) {
        return halStatusToGenericError(status);
    }
    *_aidl_return = authChallenge;
    return Status::ok();
}

}  // namespace identity
}  // namespace security
}  // namespace android
