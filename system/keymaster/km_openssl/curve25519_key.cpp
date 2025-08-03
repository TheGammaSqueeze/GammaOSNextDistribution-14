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

#include <keymaster/km_openssl/curve25519_key.h>
#include <openssl/curve25519.h>
#include <openssl/evp.h>

namespace keymaster {

bool IsEd25519Key(const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced) {
    AuthProxy proxy(hw_enforced, sw_enforced);
    return (proxy.Contains(TAG_ALGORITHM, KM_ALGORITHM_EC) &&
            proxy.Contains(TAG_EC_CURVE, KM_EC_CURVE_CURVE_25519) &&
            (proxy.Contains(TAG_PURPOSE, KM_PURPOSE_SIGN) ||
             proxy.Contains(TAG_PURPOSE, KM_PURPOSE_ATTEST_KEY)));
}

bool IsX25519Key(const AuthorizationSet& hw_enforced, const AuthorizationSet& sw_enforced) {
    AuthProxy proxy(hw_enforced, sw_enforced);
    return (proxy.Contains(TAG_ALGORITHM, KM_ALGORITHM_EC) &&
            proxy.Contains(TAG_EC_CURVE, KM_EC_CURVE_CURVE_25519) &&
            proxy.Contains(TAG_PURPOSE, KM_PURPOSE_AGREE_KEY));
}

bool Curve25519Key::EvpToInternal(const EVP_PKEY* pkey) {
    return (EvpKeyToKeyMaterial(pkey, &key_material_) == KM_ERROR_OK);
}

EVP_PKEY_Ptr Curve25519Key::InternalToEvp() const {
    const uint8_t* tmp = key_material().key_material;
    return EVP_PKEY_Ptr(
        d2i_PrivateKey(evp_key_type(), nullptr /* pkey */, &tmp, key_material().key_material_size));
}

}  // namespace keymaster
