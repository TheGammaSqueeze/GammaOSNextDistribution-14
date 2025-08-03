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

#ifndef SYSTEM_SECURITY_PRESENTATION_H_
#define SYSTEM_SECURITY_PRESENTATION_H_

#include <string>
#include <vector>

#include <android/security/identity/BnSession.h>

#include <android/hardware/identity/IPresentationSession.h>

#include <android/hardware/identity/IIdentityCredentialStore.h>

#include "CredentialStore.h"

namespace android {
namespace security {
namespace identity {

using ::android::sp;
using ::android::binder::Status;
using ::std::string;
using ::std::vector;

using ::android::hardware::identity::CipherSuite;
using ::android::hardware::identity::HardwareInformation;
using ::android::hardware::identity::IIdentityCredential;
using ::android::hardware::identity::IIdentityCredentialStore;
using ::android::hardware::identity::IPresentationSession;
using ::android::hardware::identity::RequestDataItem;
using ::android::hardware::identity::RequestNamespace;

class Session : public BnSession {
  public:
    Session(int32_t cipherSuite, sp<IPresentationSession> halBinder, sp<CredentialStore> store)
        : cipherSuite_(cipherSuite), halBinder_(halBinder), store_(store) {}

    bool initialize();

    // ISession overrides
    Status getEphemeralKeyPair(vector<uint8_t>* _aidl_return) override;

    Status setReaderEphemeralPublicKey(const vector<uint8_t>& publicKey) override;

    Status setSessionTranscript(const vector<uint8_t>& sessionTranscript) override;

    Status getAuthChallenge(int64_t* _aidl_return) override;

    Status getCredentialForPresentation(const string& credentialName,
                                        sp<ICredential>* _aidl_return) override;

  private:
    int32_t cipherSuite_;
    sp<IPresentationSession> halBinder_;
    sp<CredentialStore> store_;
};

}  // namespace identity
}  // namespace security
}  // namespace android

#endif  // SYSTEM_SECURITY_SESSION_H_
