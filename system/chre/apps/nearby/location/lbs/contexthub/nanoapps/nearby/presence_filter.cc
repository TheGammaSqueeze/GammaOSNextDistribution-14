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

#include "location/lbs/contexthub/nanoapps/nearby/presence_filter.h"

#include <inttypes.h>

#include <cstddef>

#include "location/lbs/contexthub/nanoapps/nearby/crypto_non_encryption.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_crypto_identity_v1.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_crypto_v1.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_decoder_v1.h"
#include "location/lbs/contexthub/nanoapps/nearby/presence_service_data.h"
#include "location/lbs/contexthub/nanoapps/nearby/proto/ble_filter.nanopb.h"
#include "third_party/contexthub/chre/util/include/chre/util/macros.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][PRESENCE_FILTER]"

namespace nearby {

constexpr int kAuthenticityKeyLength = 16;
constexpr int kMetaDataEncryptionTagLength = 8;

static bool addDataElementToResult(nearby_DataElement_ElementType de_type,
                                   const ByteArray &de_value,
                                   nearby_BleFilterResult *result) {
  size_t de_index = result->data_element_count;
  if (de_index >= ARRAY_SIZE(result->data_element)) {
    LOGE("Data Elements(%u) exceed the maximum count: %zu", de_type, de_index);
    return false;
  }
  if (de_value.length > ARRAY_SIZE(result->data_element[de_index].value)) {
    LOGE("Data Element(%u) exceeds the maximum length: %zu", de_type,
         de_value.length);
    return false;
  }
  result->data_element_count++;
  result->data_element[de_index].has_key = true;
  result->data_element[de_index].key = de_type;
  result->data_element[de_index].has_value = true;
  result->data_element[de_index].has_value_length = true;
  result->data_element[de_index].value_length =
      static_cast<uint32_t>(de_value.length);
  LOGD_SENSITIVE_INFO(
      "AddDataElementToResult de_index: %zu de_type: %d de_value.length: %zu",
      de_index, de_type, de_value.length);
  memcpy(result->data_element[de_index].value, de_value.data, de_value.length);
  return true;
}

bool MatchFastPairInitial(const nearby_BleFilter &filter,
                          const PresenceServiceData &service_data,
                          nearby_BleFilterResult *result) {
  if (!service_data.has_fp_model_id) {
    return false;
  }
  bool has_initial_pairing_filter = false;
  for (int i = 0; i < filter.data_element_count; i++) {
    if (filter.data_element[i].has_key &&
        filter.data_element[i].key ==
            nearby_DataElement_ElementType_DE_FAST_PAIR_ACCOUNT_KEY &&
        filter.data_element[i].has_value &&
        filter.data_element[i].has_value_length &&
        filter.data_element[i].value_length == kFpAccountKeyLength) {
      uint32_t value_byte_summary = 0;
      for (int j = 0; j < kFpAccountKeyLength; j++) {
        value_byte_summary += filter.data_element[i].value[j];
      }
      if (value_byte_summary == 0) {
        has_initial_pairing_filter = true;
        break;
      }
    }
  }
  if (has_initial_pairing_filter) {
    size_t de_index = result->data_element_count;
    result->data_element_count++;
    result->data_element[de_index].has_key = true;
    result->data_element[de_index].key =
        nearby_DataElement_ElementType_DE_FAST_PAIR_ACCOUNT_KEY;
    // value bytes have already been initialized to zero by default.
    result->data_element[de_index].has_value = true;
    result->data_element[de_index].has_value_length = true;
    result->data_element[de_index].value_length = kFpAccountKeyLength;
    result->has_result_type = true;
    result->result_type = nearby_BleFilterResult_ResultType_RESULT_FAST_PAIR;
    return true;
  }
  return false;
}

bool MatchPresenceV0(const nearby_BleFilter &filter,
                     const BleScanRecord &scan_record,
                     nearby_BleFilterResult *result) {
  chre::Optional<PresenceServiceData> presence_service_data;
  for (const auto &ble_service_data : scan_record.service_data) {
    if (ble_service_data.uuid == PresenceServiceData::kUuid) {
      presence_service_data = PresenceServiceData::Parse(
          ble_service_data.data, ble_service_data.length);
      if (ble_service_data.length <= sizeof(result->ble_service_data)) {
        result->has_ble_service_data = true;
        memcpy(result->ble_service_data, ble_service_data.data,
               ble_service_data.length);
      } else {
        LOGI("Received the BLE advertisement with length larger than %zu",
             sizeof(result->ble_service_data));
      }
      break;
    }
  }
  if (!presence_service_data.has_value()) {
    LOGI("[MatchPresenceV0] presence_service_data is empty.");
    return false;
  }

  if (MatchFastPairInitial(filter, presence_service_data.value(), result)) {
    LOGD("MatchFastPairInitial succeeded");
    return true;
  } else {
    LOGD("[MatchPresenceV0] filter Presence");
    if (filter.has_intent) {
      if (!presence_service_data.has_value()) {
        return false;
      }
      if (presence_service_data->first_intent.has_value() &&
          filter.intent == presence_service_data->first_intent.value()) {
        return true;
      } else if (presence_service_data->second_intent.has_value() &&
                 filter.intent ==
                     presence_service_data->second_intent.value()) {
        return true;
      } else {
        return false;
      }
    }
    return false;
  }
}

static bool MatchExtendedDE(
    const nearby_BleFilter &filter,
    const chre::DynamicVector<DataElement> &extended_des,
    nearby_BleFilterResult *result) {
  for (int i = 0; i < filter.data_element_count; i++) {
    // If filter is valid, at least one DE should match with this filter.
    // Otherwise, returns failure
    if (filter.data_element[i].has_key && filter.data_element[i].has_value &&
        filter.data_element[i].has_value_length) {
      bool is_matched = false;
      for (const auto &ext_de : extended_des) {
        if (ext_de.key == filter.data_element[i].key &&
            ext_de.value.length == filter.data_element[i].value_length &&
            memcmp(ext_de.value.data, filter.data_element[i].value,
                   filter.data_element[i].value_length) == 0) {
          is_matched = true;
          break;
        }
      }
      if (!is_matched) {
        LOGD("Match Presence V1 Data Element failed with %" PRIi32 " type.",
             filter.data_element[i].key);
        return false;
      }
    }
  }
  // Passed all filters. Adds all DEs into results.
  for (const auto &ext_de : extended_des) {
    if (!addDataElementToResult(ext_de.key, ext_de.value, result)) {
      return false;
    }
  }
  return true;
}

bool MatchPresenceV1(const nearby_BleFilter &filter,
                     const BleScanRecord &scan_record, const Crypto &crypto,
                     const Crypto &identity_crypto,
                     nearby_BleFilterResult *result) {
  LOGD_SENSITIVE_INFO("Filter Presence V1 with %" PRIu16 " certificates",
                      filter.certificate_count);
  PresenceDecoderV1 decoder;
  for (const auto &ble_service_data : scan_record.service_data) {
    if (ble_service_data.uuid == PresenceServiceData::kUuid) {
      for (int cert_index = 0; cert_index < filter.certificate_count;
           cert_index++) {
        ByteArray authenticity_key(
            const_cast<uint8_t *>(
                filter.certificate[cert_index].authenticity_key),
            kAuthenticityKeyLength);
        LOGD_SENSITIVE_INFO("certificate metadata encryption key tag:");
        for (size_t i = 0; i < kMetaDataEncryptionTagLength; i++) {
          LOGD_SENSITIVE_INFO(
              "%" PRIi8,
              filter.certificate[cert_index].metadata_encryption_key_tag[i]);
        }
        ByteArray metadata_encryption_key_tag(
            const_cast<uint8_t *>(
                filter.certificate[cert_index].metadata_encryption_key_tag),
            kMetaDataEncryptionTagLength);
        if (decoder.Decode(
                ByteArray(const_cast<uint8_t *>(ble_service_data.data),
                          ble_service_data.length),
                crypto, identity_crypto, authenticity_key,
                metadata_encryption_key_tag)) {
          result->has_public_credential = true;
          result->public_credential.has_encrypted_metadata_tag = true;
          for (size_t i = 0; i < kMetaDataEncryptionTagLength; i++) {
            result->public_credential.encrypted_metadata_tag[i] =
                metadata_encryption_key_tag.data[i];
          }
          result->public_credential.has_authenticity_key = true;
          for (size_t i = 0; i < kAuthenticityKeyLength; i++) {
            result->public_credential.authenticity_key[i] =
                authenticity_key.data[i];
          }
          // TODO(b/244786064): remove unused fields.
          result->public_credential.has_secret_id = true;
          result->public_credential.has_encrypted_metadata = true;
          result->public_credential.has_public_key = true;
          LOGD("Succeeded to decode Presence advertisement v1.");
          break;
        }
      }
    }
  }

  if (!decoder.decoded) {
    LOGD("Decode Presence V1 failed.");
    return false;
  }

  if (filter.has_intent) {
    bool action_matched = false;
    for (size_t i = 0; i < decoder.num_actions; i++) {
      LOGD("Match filter action %" PRIu32 " with advertisement action %" PRIu8,
           filter.intent, decoder.actions[i]);
      if (filter.intent == decoder.actions[i]) {
        result->has_intent = true;
        result->intent = filter.intent;
        action_matched = true;
        break;
      }
    }
    if (!action_matched) {
      return false;
    }
  }

  if (decoder.connection_status.data != nullptr) {
    if (!addDataElementToResult(
            nearby_DataElement_ElementType_DE_CONNECTION_STATUS,
            decoder.connection_status, result)) {
      return false;
    }
  }

  if (decoder.battery_status.data != nullptr) {
    if (!addDataElementToResult(
            nearby_DataElement_ElementType_DE_BATTERY_STATUS,
            decoder.battery_status, result)) {
      return false;
    }
  }

  if (!MatchExtendedDE(filter, decoder.extended_des, result)) {
    return false;
  }

  result->has_result_type = true;
  result->result_type = nearby_BleFilterResult_ResultType_RESULT_PRESENCE;
  return true;
}

}  // namespace nearby
