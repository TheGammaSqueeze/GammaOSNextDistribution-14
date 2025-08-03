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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_SERVICE_DATA_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_SERVICE_DATA_H_

#include <stdint.h>

#include "third_party/contexthub/chre/util/include/chre/util/non_copyable.h"
#include "third_party/contexthub/chre/util/include/chre/util/optional.h"

namespace nearby {
static constexpr uint8_t kFpAccountKeyLength = 16;
static constexpr uint8_t kFpAccountKeySaltLength = 2;
static constexpr uint8_t kFpAccountKeyFilterLength = 9;
static constexpr uint8_t kFpAccountKeyDataLength =
    kFpAccountKeySaltLength + kFpAccountKeyFilterLength;
static constexpr uint8_t kFpModelIdLength = 3;
static constexpr uint8_t kFpBatteryStatusLength = 3;

// Represents a Nearby service data in BLE advertisement.
struct PresenceServiceData {
 public:
  static constexpr uint16_t kUuid = 0xFCF1;

  // Returns Presence service data by parsing data, which is an encoded byte
  // following the spec (go/nearby-presence-spec).
  // Returns no value if parse fails. Callee keeps the ownership of data.
  static chre::Optional<PresenceServiceData> Parse(const uint8_t data[],
                                                   const uint16_t size);
  chre::Optional<uint8_t> first_intent;
  chre::Optional<uint8_t> second_intent;
  bool has_fp_model_id = false;
  uint8_t fp_model_id[kFpModelIdLength];
  bool has_fp_account_key_data = false;
  uint8_t fp_account_key_salt[kFpAccountKeySaltLength];
  uint8_t fp_account_key_filter[kFpAccountKeyFilterLength];
  bool has_battery_status = false;
  uint8_t fp_battery_status[kFpBatteryStatusLength];
};

// Represents a field header inside a Nearby service data.
struct PresenceFieldHeader {
 public:
  // Constructs a Field Header for Presence service data from a header and an
  // optional extension byte.
  PresenceFieldHeader(const uint8_t header, const uint8_t extension) {
    type = header & 0x0F;
    if (type == kExtensionType) {
      type = extension & 0x0F;
      length = ((header & 0xF0) >> 2) + ((extension & 0xC0) >> 6);
    } else {
      length = (header & 0xF0) >> 4;
    }
  }

  // Constants defining the Presence data element type, sorted by their value.
  static constexpr uint8_t kIntentType = 0b0101;
  // Fast Pair model ID.
  static constexpr uint8_t kFpModelIdType = 0b0111;
  // Fast Pair Account Key data includes both SALT and Bloom filter.
  static constexpr uint8_t kFpAccountKeyDataType = 0b1001;
  static constexpr uint8_t kBatteryStatusType = 0b1011;
  static constexpr uint8_t kExtensionType = 0b1111;

  // length of data element value.
  uint8_t length;
  // type of data element.
  uint8_t type;
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_SERVICE_DATA_H_
