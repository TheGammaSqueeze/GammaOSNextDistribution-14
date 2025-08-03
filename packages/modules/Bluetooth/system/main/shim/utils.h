/*
 * Copyright 2023 The Android Open Source Project
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
#include <vector>

#include "hci/hci_packets.h"

namespace bluetooth {
namespace shim {
/**
 * @brief Parsing gap data from raw bytes
 *
 * @param raw_data input, raw bytes
 * @param output vector of GapData
 */
void parse_gap_data(const std::vector<uint8_t> &raw_data,
                    std::vector<hci::GapData> &output);
}  // namespace shim
}  // namespace bluetooth
