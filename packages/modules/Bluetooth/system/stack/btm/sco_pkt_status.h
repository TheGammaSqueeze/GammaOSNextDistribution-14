/*
 * Copyright (C) 2023 The Android Open Source Project
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
#include <iomanip>
#include <iostream>
#include <sstream>
#include <string>

#include "common/time_util.h"

#define BTM_PKT_STATUS_LEN 64
#define BTM_PKT_STATUS_WBS_FRAME_US 7500

/* Object to log consecutive packets' status */
struct tBTM_SCO_PKT_STATUS {
  // Bytes to store packets' status.
  uint8_t data[BTM_PKT_STATUS_LEN];
  // Total number of bits in |data|.
  int size;
  // Position of the next bit to log packet status.
  int offset;
  // Whether the ring buffer is full to be wrapped.
  bool is_full;
  // The timestamp of the first bit of |data|'s last update.
  uint64_t ts;

 public:
  void init() {
    std::fill(std::begin(data), std::end(data), 0);
    size = BTM_PKT_STATUS_LEN * 8;
    offset = 0;
    is_full = false;
    ts = 0;
  }

  void update(bool is_lost) {
    if (is_lost) {
      data[offset / 8] |= 1UL << (offset % 8);
    } else {
      data[offset / 8] &= ~(1UL << (offset % 8));
    }
    if (offset == 0) {
      ts = bluetooth::common::time_gettimeofday_us();
    }
    offset++;
    if (offset == size) {
      offset = 0;
      is_full = true;
    }
  }

  /* Rewinds logger's time stamp to calculate the beginning.
   * If logger's ring buffer hasn't wrapped, simply return ts.
   * Otherwise begin_ts = ts - WBS_FRAME_US * (size - offset)
   */
  uint64_t begin_ts_raw_us() {
    return !is_full ? ts : ts - BTM_PKT_STATUS_WBS_FRAME_US * (size - offset);
  }

  /* Fast-forwards the logger's time stamp to calculate the end.
   * In other words, end_ts = logger_ts + WBS_FRAME_US * wp
   */
  uint64_t end_ts_raw_us() { return ts + BTM_PKT_STATUS_WBS_FRAME_US * offset; }

  std::string data_to_hex_string() {
    int i;
    int len = is_full ? size : offset;
    int head = is_full ? offset : 0;
    uint8_t byte = 0;
    std::stringstream oss;

    for (i = 0; i < len; ++i) {
      int j = (head + i) % size;
      byte |= (1U << (j % 8)) & data[j / 8];

      if ((i + 1) % 8 == 0) {
        // +(byte) to prevent an uint8_t to be interpreted as a char
        oss << std::hex << std::setw(2) << std::setfill('0') << +(byte);
        byte = 0;
      }
    }

    if (i % 8) oss << std::hex << std::setw(2) << std::setfill('0') << +(byte);

    return oss.str();
  }

  std::string data_to_binary_string() {
    int head = is_full ? offset : 0;
    int len = is_full ? size : offset;
    std::string s;

    for (int i = 0; i < len; ++i) {
      int j = (head + i) % size;
      s += std::to_string(((data[j / 8] >> (j % 8)) & 1U));
    }

    return s;
  }
};
