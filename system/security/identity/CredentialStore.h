/*
 * Copyright (c) 2019, The Android Open Source Project
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

#ifndef SYSTEM_SECURITY_CREDENTIAL_STORE_H_
#define SYSTEM_SECURITY_CREDENTIAL_STORE_H_

#include <string>
#include <vector>

#include <android/hardware/identity/IIdentityCredentialStore.h>
#include <android/security/identity/BnCredentialStore.h>

namespace android {
namespace security {
namespace identity {

using ::android::sp;
using ::android::binder::Status;
using ::std::optional;
using ::std::string;
using ::std::unique_ptr;
using ::std::vector;

using ::android::hardware::identity::HardwareInformation;
using ::android::hardware::identity::IIdentityCredentialStore;
using ::android::hardware::identity::IPresentationSession;
using ::android::hardware::identity::IWritableIdentityCredential;
using ::android::hardware::security::keymint::IRemotelyProvisionedComponent;

class CredentialStore : public BnCredentialStore {
  public:
    CredentialStore(const string& dataPath, sp<IIdentityCredentialStore> hal);
    ~CredentialStore();

    bool init();

    // Used by both getCredentialByName() and Session::getCredential()
    //
    Status getCredentialCommon(const string& credentialName, int32_t cipherSuite,
                               sp<IPresentationSession> halSessionBinder,
                               sp<ICredential>* _aidl_return);

    // ICredentialStore overrides
    Status getSecurityHardwareInfo(SecurityHardwareInfoParcel* _aidl_return) override;

    Status createCredential(const string& credentialName, const string& docType,
                            sp<IWritableCredential>* _aidl_return) override;

    Status getCredentialByName(const string& credentialName, int32_t cipherSuite,
                               sp<ICredential>* _aidl_return) override;

    Status createPresentationSession(int32_t cipherSuite, sp<ISession>* _aidl_return) override;

  private:
    Status setRemotelyProvisionedAttestationKey(IWritableIdentityCredential* halWritableCredential);

    string dataPath_;

    sp<IIdentityCredentialStore> hal_;
    int halApiVersion_;

    HardwareInformation hwInfo_;

    sp<IRemotelyProvisionedComponent> rpc_;
};

}  // namespace identity
}  // namespace security
}  // namespace android

#endif  // SYSTEM_SECURITY_CREDENTIAL_STORE_H_
