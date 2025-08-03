/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include "chpp/crc.h"

#include <stddef.h>
#include <stdint.h>

uint32_t chppCrc32(uint32_t crc, const uint8_t *buf, size_t len) {
  // This lookup table (LUT) consumes 16 * 4 = 64 bytes. Other implementations
  // exist with a larger LUT, with a LUT calculated on the fly, or without using
  // a LUT altogether.
  static const uint32_t crc32LookupTable[] = {
      0x00000000, 0x1DB71064, 0x3B6E20C8, 0x26D930AC, 0x76DC4190, 0x6B6B51F4,
      0x4DB26158, 0x5005713C, 0xEDB88320, 0xF00F9344, 0xD6D6A3E8, 0xCB61B38C,
      0x9B64C2B0, 0x86D3D2D4, 0xA00AE278, 0xBDBDF21C};

  crc = ~crc;
  for (size_t i = 0; i < len; i++) {
    crc = crc32LookupTable[(crc ^ buf[i]) & 0x0F] ^ (crc >> 4);
    crc = crc32LookupTable[(crc ^ (buf[i] >> 4)) & 0x0F] ^ (crc >> 4);
  }
  crc = ~crc;

  return crc;
}
