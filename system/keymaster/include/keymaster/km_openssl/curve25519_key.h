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

#pragma once

#include <utility>

#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/openssl_utils.h>

namespace keymaster {

// OpenSSL uses 64-byte private keys for the APIs in curve25519.h, and the
// first 32 bytes hold the seed (as per RFC 8032).  The EVP_PKEY_* functions
// also only expect to deal with the seed.
constexpr int ED25519_SEED_LEN = 32;

// Determine whether the key characteristics indicate the presence of an Ed25519 key.
bool IsEd25519Key(const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced);

// Determine whether the key characteristics indicate the presence of an X25519 key.
bool IsX25519Key(const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced);

class Curve25519Key : public AsymmetricKey {
  public:
    Curve25519Key(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced,
                  const KeyFactory* factory)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory) {}
    Curve25519Key(AuthorizationSet hw_enforced, AuthorizationSet sw_enforced,
                  const KeyFactory* factory, const KeymasterKeyBlob& key_material)
        : AsymmetricKey(std::move(hw_enforced), std::move(sw_enforced), factory) {
        key_material_ = key_material;
    }

    EVP_PKEY_Ptr InternalToEvp() const override;
    bool EvpToInternal(const EVP_PKEY* pkey) override;
};

class Ed25519Key : public Curve25519Key {
  public:
    using Curve25519Key::Curve25519Key;
    int evp_key_type() const override { return EVP_PKEY_ED25519; }
};

class X25519Key : public Curve25519Key {
  public:
    using Curve25519Key::Curve25519Key;
    int evp_key_type() const override { return EVP_PKEY_X25519; }
};

}  // namespace keymaster
