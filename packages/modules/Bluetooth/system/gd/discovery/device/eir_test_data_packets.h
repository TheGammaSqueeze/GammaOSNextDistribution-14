/*
 * Copyright 2024 The Android Open Source Project
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

#include <string>
#include <unordered_map>
#include <vector>

struct header {
  uint8_t event;
  uint8_t event_code;
  uint8_t length;
  uint8_t num_rsp;
  uint8_t raw_address[6];
  uint8_t scan_mode;
  uint8_t reserved11;
  uint8_t cod[3];
  uint16_t clock_offset;
  uint8_t rssi;
  uint8_t eir_data[];
} __attribute__((packed));

constexpr size_t kEirOffset = sizeof(header);
constexpr size_t kEirSize = 240U;

extern std::unordered_map<std::string, const unsigned char*> selected_packets;
extern std::vector<const unsigned char*> data_packets;
