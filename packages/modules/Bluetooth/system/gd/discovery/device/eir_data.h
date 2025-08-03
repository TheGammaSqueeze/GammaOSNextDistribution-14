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

#include <cstdint>
#include <vector>

#include "discovery/device/data_parser.h"
#include "hci/uuid.h"

namespace bluetooth {
namespace discovery {
namespace device {

struct service_uuid16_t {
  uint16_t uuid;
  std::vector<uint8_t> data;
};

struct service_uuid32_t {
  uint32_t uuid;
  std::vector<uint8_t> data;
};

class EirData : public DataParser {
 public:
  EirData(const std::vector<uint8_t>& data);

  bool GetCompleteNames(std::vector<std::array<uint8_t, 240>>&) const;
  bool GetShortenedNames(std::vector<std::array<uint8_t, 240>>&) const;

  bool GetUuids16(std::vector<uint16_t>&) const;
  bool GetUuidsIncomplete16(std::vector<uint16_t>&) const;
  bool GetUuids32(std::vector<uint32_t>&) const;
  bool GetUuidsIncomplete32(std::vector<uint32_t>&) const;
  bool GetUuids128(std::vector<hci::Uuid>&) const;
  bool GetUuidsIncomplete128(std::vector<hci::Uuid>&) const;

  bool GetDeviceId(std::vector<std::vector<uint8_t>>&) const;

  bool GetManufacturerSpecificData(std::vector<std::vector<uint8_t>>&) const;

  bool GetSecurityManagerOobFlags(std::vector<std::vector<uint8_t>>&) const;
  bool GetServiceUuuids16(std::vector<service_uuid16_t>&) const;
  bool GetServiceUuuids32(std::vector<service_uuid32_t>&) const;
  bool GetTxPowerLevel(std::vector<int8_t>&) const;
};

}  // namespace device
}  // namespace discovery
}  // namespace bluetooth
