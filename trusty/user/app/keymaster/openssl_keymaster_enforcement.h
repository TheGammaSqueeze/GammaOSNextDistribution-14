/*
**
** Copyright 2018, The Android Open Source Project
**
** Licensed under the Apache License, Version 2.0 (the "License");
** you may not use this file except in compliance with the License.
** You may obtain a copy of the License at
**
**     http://www.apache.org/licenses/LICENSE-2.0
**
** Unless required by applicable law or agreed to in writing, software
** distributed under the License is distributed on an "AS IS" BASIS,
** WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
** See the License for the specific language governing permissions and
** limitations under the License.
*/

#ifndef INCLUDE_KEYMASTER_OPENSSL_KEYMASTER_ENFORCEMENT_H_
#define INCLUDE_KEYMASTER_OPENSSL_KEYMASTER_ENFORCEMENT_H_

#include <keymaster/android_keymaster_messages.h>
#include <keymaster/keymaster_enforcement.h>

/*
 * Controls size of KAK used for Strongbox agreement.
 * This size must match the size of the com.android.trusty.keymint.kak keyslot
 * Defaults to 32 to allow devices which do not have a Strongbox to use the
 * keyslot in our sample code without configuration.
 */
#ifndef TRUSTY_KM_KAK_SIZE
#define TRUSTY_KM_KAK_SIZE 32
#endif

namespace keymaster {

class OpenSSLKeymasterEnforcement : public KeymasterEnforcement {
public:
    OpenSSLKeymasterEnforcement(uint32_t max_access_time_map_size,
                                uint32_t max_access_count_map_size)
            : KeymasterEnforcement(max_access_time_map_size,
                                   max_access_count_map_size) {}
    virtual ~OpenSSLKeymasterEnforcement() {}

    bool CreateKeyId(const keymaster_key_blob_t& key_blob,
                     km_id_t* keyid) const override;
    keymaster_error_t GetHmacSharingParameters(
            HmacSharingParameters* params) override;
    keymaster_error_t ComputeSharedHmac(
            const HmacSharingParametersArray& params_array,
            KeymasterBlob* sharingCheck) override;
    VerifyAuthorizationResponse VerifyAuthorization(
            const VerifyAuthorizationRequest& request) override;
    KmErrorOr<std::array<uint8_t, 32>> ComputeHmac(
            const std::vector<uint8_t>& data_to_mac) const override;
    keymaster_error_t GetHmacKey(keymaster_key_blob_t* key) const;
    keymaster_error_t GetUniqueIdKey(KeymasterKeyBlob* key) const;

private:
    static const size_t kKeyAgreementKeySize = TRUSTY_KM_KAK_SIZE;
    keymaster_error_t GetKeyAgreementKey(KeymasterKeyBlob* kak) const;
    bool have_saved_params_ = false;
    HmacSharingParameters saved_params_;
    KeymasterKeyBlob hmac_key_;
};

}  // namespace keymaster

#endif  // INCLUDE_KEYMASTER_OPENSSL_KEYMASTER_ENFORCEMENT_H_
