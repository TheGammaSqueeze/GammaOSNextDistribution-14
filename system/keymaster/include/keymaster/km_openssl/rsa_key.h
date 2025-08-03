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

#ifndef SYSTEM_KEYMASTER_RSA_KEY_H_
#define SYSTEM_KEYMASTER_RSA_KEY_H_

#include <utility>

#include <openssl/rsa.h>

#include <keymaster/km_openssl/openssl_utils.h>

#include "asymmetric_key.h"

namespace keymaster {

class RsaKey : public AsymmetricKey {
  public:
    RsaKey(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced, const KeyFactory* factory)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory) {}
    RsaKey(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced, const KeyFactory* factory,
           RSA_Ptr rsa_key)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory),
                        rsa_key_(std::move(rsa_key)) {}

    int evp_key_type() const override { return EVP_PKEY_RSA; }

    EVP_PKEY_Ptr InternalToEvp() const override;
    bool EvpToInternal(const EVP_PKEY* pkey) override;

    bool SupportedMode(keymaster_purpose_t purpose, keymaster_padding_t padding);
    bool SupportedMode(keymaster_purpose_t purpose, keymaster_digest_t digest);

    RSA* key() const { return rsa_key_.get(); }

  protected:
    RsaKey(RSA* rsa, AuthorizationSet hw_enforced, AuthorizationSet sw_enforced,
           const KeyFactory* key_factory)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), key_factory), rsa_key_(rsa)
          {}

  private:
    RSA_Ptr rsa_key_;
};

}  // namespace keymaster

#endif  // SYSTEM_KEYMASTER_RSA_KEY_H_
