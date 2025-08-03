/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <dice/dice.h>
#include <openssl/curve25519.h>

/*
    The implementation of validation functions is shared by Rust and C++
    applications, and so two different interfaces are provided -- one that
    uses primitive C types to interface with bindgen, and one that uses
    C++ standard library types.

    For Rust, `validate_bcc` and `validate_bcc_handover` are exposed.
    For C++, `validate_bcc_impl` and `validate_bcc_handover_impl` are
    exposed in addition to the aforementioned interfaces.
*/

#ifdef __cplusplus
#include <array>
#include <vector>

using PubKey = std::array<uint8_t, ED25519_PUBLIC_KEY_LEN>;
using CDI = std::array<uint8_t, DICE_CDI_SIZE>;

bool validate_bcc_impl(const uint8_t* bcc,
                       size_t bcc_size,
                       std::vector<PubKey>* keys);

bool validate_bcc_handover_impl(const uint8_t* bcc_handover,
                                size_t bcc_handover_size,
                                CDI* next_cdi_attest,
                                CDI* next_cdi_seal);
#endif

#ifdef __cplusplus
extern "C" {
#endif

bool validate_bcc(const uint8_t* bcc,
                  size_t bcc_size,
                  uint8_t dk_pub_key[ED25519_PUBLIC_KEY_LEN],
                  uint8_t km_pub_key[ED25519_PUBLIC_KEY_LEN]);

bool validate_bcc_handover(const uint8_t* bcc_handover,
                           size_t bcc_handover_size,
                           uint8_t next_cdi_attest[DICE_CDI_SIZE],
                           uint8_t next_cdi_seal[DICE_CDI_SIZE]);

#ifdef __cplusplus
}
#endif