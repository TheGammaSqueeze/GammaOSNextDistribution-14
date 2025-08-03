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

#include "location/lbs/contexthub/nanoapps/nearby/presence_service_data.h"

#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/assert.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][SERVICE_DATA]"

namespace nearby {

chre::Optional<PresenceServiceData> PresenceServiceData::Parse(
    const uint8_t data[], const uint16_t size) {
  const uint8_t service_header = data[0];
  if (((service_header & 0b11100000) >> 5) != 0) {
    LOGD("Presence advertisement version is not 0.");
    return chre::Optional<PresenceServiceData>();
  }
  uint8_t num_fields = (service_header & 0b00011110) >> 1;
  LOGD("Decode a FILTERS message with size %d", num_fields);

  PresenceServiceData presence_service_data;
  // pointer to scan data, starting from 1.
  uint16_t p = 1;
  while (num_fields > 0) {
    PresenceFieldHeader header(data[p], data[p + 1]);
    switch (header.type) {
      case PresenceFieldHeader::kIntentType:
        presence_service_data.first_intent = data[p + 1];
        presence_service_data.second_intent = data[p + 2];
        break;
      case PresenceFieldHeader::kFpModelIdType:
        if (header.length == kFpModelIdLength) {
          presence_service_data.has_fp_model_id = true;
          memcpy(presence_service_data.fp_model_id, &data[p + 1],
                 kFpModelIdLength);
        } else {
          LOGE("Fast Pair model ID length %d not equal to %d", header.length,
               kFpModelIdLength);
        }
        break;
      case PresenceFieldHeader::kFpAccountKeyDataType:
        if (header.length == kFpAccountKeyDataLength) {
          presence_service_data.has_fp_account_key_data = true;
          presence_service_data.fp_account_key_salt[0] = data[p + 1];
          presence_service_data.fp_account_key_salt[1] = data[p + 2];
          memcpy(presence_service_data.fp_account_key_filter, &data[p + 3],
                 kFpAccountKeyFilterLength);
        } else {
          LOGE("Fast account key filter length %d not equal to %d",
               header.length, kFpAccountKeyDataLength);
        }
        break;
      case PresenceFieldHeader::kBatteryStatusType:
        if (header.length == kFpBatteryStatusLength) {
          presence_service_data.has_battery_status = true;
          memcpy(presence_service_data.fp_battery_status, &data[p + 1],
                 kFpBatteryStatusLength);
        } else {
          LOGE("Battery status length %d not equal to %d", header.length,
               kFpBatteryStatusLength);
        }
        break;
      case PresenceFieldHeader::kExtensionType:
      default:
        break;
    }
    num_fields--;
    // Moves p to the field value
    p = header.type == PresenceFieldHeader::kExtensionType ? p + 2 : p + 1;
    // Further moves p to the next field header
    p += header.length;
  }
  // p should be moved to size for a valid encode. Otherwise, returns an empty
  // value.
  return (p == size ? presence_service_data
                    : chre::Optional<PresenceServiceData>());
}

}  // namespace nearby
