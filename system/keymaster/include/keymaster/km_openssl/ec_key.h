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

#include <utility>

#include <openssl/ec.h>

#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/openssl_utils.h>

namespace keymaster {

class EcdsaOperationFactory;

class EcKey : public AsymmetricKey {
  public:
    EcKey(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced, const KeyFactory* factory)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory) {}
    EcKey(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced, const KeyFactory* factory,
          EC_KEY_Ptr ec_key)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory),
          ec_key_(std::move(ec_key)) {}

    int evp_key_type() const override { return EVP_PKEY_EC; }

    EVP_PKEY_Ptr InternalToEvp() const override;
    bool EvpToInternal(const EVP_PKEY* pkey) override;

    EC_KEY* key() const { return ec_key_.get(); }

  protected:
    EcKey(EC_KEY* ec_key, AuthorizationSet hw_enforced, AuthorizationSet sw_enforced,
          const KeyFactory* key_factory)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), key_factory),
          ec_key_(ec_key) {}

  private:
    EC_KEY_Ptr ec_key_;
};

}  // namespace keymaster
