/*
 * Copyright 2020 The Android Open Source Project
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

#include <keymaster/contexts/soft_attestation_context.h>

#include <string>

#include <keymaster/km_openssl/asymmetric_key.h>
#include <keymaster/km_openssl/attestation_utils.h>

namespace keymaster {

const AttestationContext::VerifiedBootParams*
SoftAttestationContext::GetVerifiedBootParams(keymaster_error_t* error) const {
    static AttestationContext::VerifiedBootParams params;
    static std::string fake_vb_key(32, 0);

    params.verified_boot_key = {reinterpret_cast<uint8_t*>(fake_vb_key.data()), fake_vb_key.size()};
    params.verified_boot_hash = {reinterpret_cast<uint8_t*>(fake_vb_key.data()),
                                 fake_vb_key.size()};
    params.verified_boot_state = KM_VERIFIED_BOOT_UNVERIFIED;
    params.device_locked = false;
    *error = KM_ERROR_OK;
    return &params;
}

}  // namespace keymaster
