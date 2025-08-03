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

#include <cstdint>

#include "hci/octets.h"

using Octet16 = bluetooth::hci::Octet16;
using LinkKey = bluetooth::hci::Octet16; /* Link Key */
static constexpr int OCTET16_LEN = bluetooth::hci::kOctet16Length;

/* Sample LTK from BT Spec 5.1 | Vol 6, Part C 1
 * 0x4C68384139F574D836BCF34E9DFB01BF */
constexpr Octet16 SAMPLE_LTK = {0xbf, 0x01, 0xfb, 0x9d, 0x4e, 0xf3, 0xbc, 0x36,
                                0xd8, 0x74, 0xf5, 0x39, 0x41, 0x38, 0x68, 0x4c};
inline bool is_sample_ltk(const Octet16& ltk) { return ltk == SAMPLE_LTK; }

#define BT_OCTET8_LEN 8
typedef uint8_t BT_OCTET8[BT_OCTET8_LEN]; /* octet array: size 16 */

#define BT_OCTET32_LEN 32
typedef uint8_t BT_OCTET32[BT_OCTET32_LEN]; /* octet array: size 32 */

#define PIN_CODE_LEN 16
typedef uint8_t PIN_CODE[PIN_CODE_LEN]; /* Pin Code (upto 128 bits) MSB is 0 */
