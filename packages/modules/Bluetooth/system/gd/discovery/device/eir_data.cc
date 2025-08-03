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

#include "discovery/device/eir_data.h"

#include <algorithm>
#include <array>
#include <iterator>
#include <vector>

#include "hci/hci_packets.h"
#include "hci/uuid.h"

using namespace bluetooth;

using namespace bluetooth::hci;
using namespace bluetooth::packet;

namespace bluetooth::discovery::device {

EirData::EirData(const std::vector<uint8_t>& data) : DataParser(data) {}

bool EirData::GetCompleteNames(std::vector<std::array<uint8_t, 240>>& names) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::COMPLETE_LOCAL_NAME) {
      std::array<uint8_t, 240> array;
      std::copy(gap_data.data_.begin(), gap_data.data_.end(), array.begin());
      names.push_back(array);
    }
  }
  return !names.empty();
}

bool EirData::GetShortenedNames(std::vector<std::array<uint8_t, 240>>& names) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::SHORTENED_LOCAL_NAME) {
      std::array<uint8_t, 240> array;
      std::copy(gap_data.data_.begin(), gap_data.data_.end(), array.begin());
      names.push_back(array);
    }
  }
  return !names.empty();
}

bool EirData::GetUuids16(std::vector<uint16_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::COMPLETE_LIST_16_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (signed)Uuid::kNumBytes16) {
        uuids.push_back(*it | *(it + 1) << 8);
        it += Uuid::kNumBytes16;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetUuidsIncomplete16(std::vector<uint16_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::INCOMPLETE_LIST_16_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (signed)Uuid::kNumBytes16) {
        uuids.push_back(*it | *(it + 1) << 8);
        it += Uuid::kNumBytes16;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetUuids32(std::vector<uint32_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::COMPLETE_LIST_32_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (signed)Uuid::kNumBytes32) {
        uuids.push_back(*it | *(it + 1) << 8 | *(it + 2) << 16 | *(it + 3) << 24);
        it += Uuid::kNumBytes32;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetUuidsIncomplete32(std::vector<uint32_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::INCOMPLETE_LIST_32_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (signed)Uuid::kNumBytes32) {
        uuids.push_back(*it | *(it + 1) << 8 | *(it + 2) << 16 | *(it + 3) << 24);
        it += Uuid::kNumBytes32;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetUuids128(std::vector<hci::Uuid>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::COMPLETE_LIST_128_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (long)Uuid::kNumBytes128) {
        auto uuid = bluetooth::hci::Uuid::From128BitLE(&it[0]);
        uuids.push_back(uuid);
        it += Uuid::kNumBytes128;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetUuidsIncomplete128(std::vector<hci::Uuid>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::INCOMPLETE_LIST_128_BIT_UUIDS) {
      auto it = gap_data.data_.begin();
      while (std::distance(it, gap_data.data_.end()) >= (long)Uuid::kNumBytes128) {
        auto uuid = bluetooth::hci::Uuid::From128BitLE(&it[0]);
        uuids.push_back(uuid);
        it += Uuid::kNumBytes128;
      }
    }
  }
  return !uuids.empty();
}

bool EirData::GetDeviceId(std::vector<std::vector<uint8_t>>& device_ids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::DEVICE_ID) {
      device_ids.push_back(gap_data.data_);
    }
  }
  return !device_ids.empty();
}

bool EirData::GetManufacturerSpecificData(std::vector<std::vector<uint8_t>>& data) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::MANUFACTURER_SPECIFIC_DATA) {
      data.push_back(gap_data.data_);
    }
  }
  return !data.empty();
}

bool EirData::GetSecurityManagerOobFlags(std::vector<std::vector<uint8_t>>& flags) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::SECURITY_MANAGER_OOB_FLAGS) {
      flags.push_back(gap_data.data_);
    }
  }
  return !flags.empty();
}

bool EirData::GetServiceUuuids16(std::vector<service_uuid16_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::SERVICE_DATA_16_BIT_UUIDS) {
      if (gap_data.data_.size() < Uuid::kNumBytes16) continue;
      auto it = gap_data.data_.begin();
      uuids.push_back({
          .uuid = (uint16_t)(*it | *(it + 1) << 8),
          .data = std::vector<uint8_t>(it + Uuid::kNumBytes16, gap_data.data_.end()),
      });
    }
  }
  return !uuids.empty();
}

bool EirData::GetServiceUuuids32(std::vector<service_uuid32_t>& uuids) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::SERVICE_DATA_32_BIT_UUIDS) {
      if (gap_data.data_.size() < Uuid::kNumBytes32) continue;
      auto it = gap_data.data_.begin();
      uuids.push_back({
          .uuid = (uint32_t)(*it | *(it + 1) << 8 | *(it + 2) << 16 | *(it + 3) << 24),
          .data = std::vector<uint8_t>(it + Uuid::kNumBytes32, gap_data.data_.end()),
      });
    }
  }
  return !uuids.empty();
}

bool EirData::GetTxPowerLevel(std::vector<int8_t>& tx_power_level) const {
  for (const auto& gap_data : gap_data_) {
    if (gap_data.data_type_ == hci::GapDataType::TX_POWER_LEVEL) {
      if (gap_data.data_.size() == 1U)
        tx_power_level.push_back(static_cast<int8_t>(gap_data.data_[0]));
    }
  }
  return !tx_power_level.empty();
}

}  // namespace bluetooth::discovery::device
