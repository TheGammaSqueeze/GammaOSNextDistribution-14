/*
 * Copyright 2021 The Android Open Source Project
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

#ifndef SYSTEM_KEYMASTER_ECDH_OPERATION_H_
#define SYSTEM_KEYMASTER_ECDH_OPERATION_H_

#include <utility>

#include <openssl/ec.h>
#include <openssl/evp.h>

#include <keymaster/UniquePtr.h>

#include <keymaster/key.h>
#include <keymaster/km_openssl/openssl_utils.h>
#include <keymaster/operation.h>

namespace keymaster {

class EcdhOperation : public Operation {
  public:
    EcdhOperation(AuthorizationSet&& hw_enforced, AuthorizationSet&& sw_enforced, EVP_PKEY* key)
        : Operation(KM_PURPOSE_AGREE_KEY, std::move(hw_enforced), std::move(sw_enforced)),
                    ecdh_key_(key) {}

    keymaster_error_t Abort() override { return KM_ERROR_OK; }

    keymaster_error_t Begin(const AuthorizationSet& input_params,
                            AuthorizationSet* output_params) override;
    keymaster_error_t Update(const AuthorizationSet& additional_params, const Buffer& input,
                             AuthorizationSet* output_params, Buffer* output,
                             size_t* input_consumed) override;
    keymaster_error_t Finish(const AuthorizationSet& additional_params, const Buffer& input,
                             const Buffer& signature, AuthorizationSet* output_params,
                             Buffer* output) override;

  protected:
    EVP_PKEY_Ptr ecdh_key_;
};

class X25519Operation : public EcdhOperation {
  public:
    X25519Operation(AuthorizationSet&& hw_enforced, AuthorizationSet&& sw_enforced, EVP_PKEY* key)
        : EcdhOperation(std::move(hw_enforced), std::move(sw_enforced), key) {}

    keymaster_error_t Finish(const AuthorizationSet& additional_params, const Buffer& input,
                             const Buffer& signature, AuthorizationSet* output_params,
                             Buffer* output) override;
};

class EcdhOperationFactory : public OperationFactory {
  private:
    KeyType registry_key() const override { return KeyType(KM_ALGORITHM_EC, KM_PURPOSE_AGREE_KEY); }
    OperationPtr CreateOperation(Key&& key, const AuthorizationSet& begin_params,
                                 keymaster_error_t* error) override;
};

}  // namespace keymaster

#endif  // SYSTEM_KEYMASTER_ECDH_OPERATION_H_
