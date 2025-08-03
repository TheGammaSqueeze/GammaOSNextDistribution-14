/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <array>
#include <cstdint>
#include <cstring>

#include "hci/octets.h"

namespace crypto_toolbox {

bluetooth::hci::Octet16 c1(
    const bluetooth::hci::Octet16& k,
    const bluetooth::hci::Octet16& r,
    const uint8_t* pres,
    const uint8_t* preq,
    const uint8_t iat,
    const uint8_t* ia,
    const uint8_t rat,
    const uint8_t* ra);
bluetooth::hci::Octet16 s1(
    const bluetooth::hci::Octet16& k,
    const bluetooth::hci::Octet16& r1,
    const bluetooth::hci::Octet16& r2);

bluetooth::hci::Octet16 aes_128(
    const bluetooth::hci::Octet16& key, const bluetooth::hci::Octet16& message);
bluetooth::hci::Octet16 aes_cmac(
    const bluetooth::hci::Octet16& key, const uint8_t* message, uint16_t length);
bluetooth::hci::Octet16 f4(
    const uint8_t* u, const uint8_t* v, const bluetooth::hci::Octet16& x, uint8_t z);
void f5(
    const uint8_t* w,
    const bluetooth::hci::Octet16& n1,
    const bluetooth::hci::Octet16& n2,
    uint8_t* a1,
    uint8_t* a2,
    bluetooth::hci::Octet16* mac_key,
    bluetooth::hci::Octet16* ltk);
bluetooth::hci::Octet16 f6(
    const bluetooth::hci::Octet16& w,
    const bluetooth::hci::Octet16& n1,
    const bluetooth::hci::Octet16& n2,
    const bluetooth::hci::Octet16& r,
    uint8_t* iocap,
    uint8_t* a1,
    uint8_t* a2);
bluetooth::hci::Octet16 h6(const bluetooth::hci::Octet16& w, std::array<uint8_t, 4> keyid);
bluetooth::hci::Octet16 h7(const bluetooth::hci::Octet16& salt, const bluetooth::hci::Octet16& w);
uint32_t g2(
    const uint8_t* u,
    const uint8_t* v,
    const bluetooth::hci::Octet16& x,
    const bluetooth::hci::Octet16& y);
bluetooth::hci::Octet16 ltk_to_link_key(const bluetooth::hci::Octet16& ltk, bool use_h7);
bluetooth::hci::Octet16 link_key_to_ltk(const bluetooth::hci::Octet16& link_key, bool use_h7);

// |tlen| - lenth of mac desired
// |p_signature| - data pointer to where signed data to be stored, tlen long.
inline void aes_cmac(
    const bluetooth::hci::Octet16& key,
    const uint8_t* message,
    uint16_t length,
    uint16_t tlen,
    uint8_t* p_signature) {
  bluetooth::hci::Octet16 signature = aes_cmac(key, message, length);

  uint8_t* p_mac = signature.data() + (bluetooth::hci::kOctet16Length - tlen);
  memcpy(p_signature, p_mac, tlen);
}

inline bluetooth::hci::Octet16 aes_cmac(
    const bluetooth::hci::Octet16& key, const bluetooth::hci::Octet16& message) {
  return aes_cmac(key, message.data(), message.size());
}

}  // namespace crypto_toolbox
