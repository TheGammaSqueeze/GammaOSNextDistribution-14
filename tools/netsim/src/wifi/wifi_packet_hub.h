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

#include <cstdint>
#include <memory>
#include <vector>

#include "rust/cxx.h"

namespace netsim::wifi {

/* Handle packet requests for the WiFi Facade which may come over
   different transports including gRPC. */

void HandleWifiRequest(uint32_t chip_id,
                       const std::shared_ptr<std::vector<uint8_t>> &packet);

void HandleWifiRequestCxx(uint32_t chip_id, const rust::Vec<uint8_t> &packet);

}  // namespace netsim::wifi
