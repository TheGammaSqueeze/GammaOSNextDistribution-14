/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHPP_CRC_H_
#define CHPP_CRC_H_

#include <stddef.h>
#include <stdint.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * Calculates IEEE CRC-32, initialized to (~crc), on an input data buffer.
 *
 * It is expected that the implementation be optimized based on individual
 * platforms, e.g. by reusing HW/SW optimized CRC functionality.
 *
 * @param crc CRC initialization value. This may be used to calculate a CRC on
 * daisy-chained input data. Otherwise, it can be set to zero to initialize to
 * 0xFFFFFFFF.
 * @param buf Input data.
 * @param len Input data length in bytes.
 *
 * @return Calculated CRC-32.
 */
uint32_t chppCrc32(uint32_t crc, const uint8_t *buf, size_t len);

#ifdef __cplusplus
}
#endif

#endif  // CHPP_CRC_H_
