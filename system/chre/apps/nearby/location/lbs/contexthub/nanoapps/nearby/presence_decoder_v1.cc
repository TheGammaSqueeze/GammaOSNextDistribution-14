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

#include "location/lbs/contexthub/nanoapps/nearby/presence_decoder_v1.h"

#include <cinttypes>

#include "location/lbs/contexthub/nanoapps/nearby/crypto.h"
#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][PRESENCE_DECODER_V1]"

namespace nearby {
// The Presence v1 advertisement is defined in the format below:
// Header (1 byte) | salt (1+2 bytes) | Identity + filter (2+16 bytes)
// | repeated Data Element fields (various bytes)
// The header contains:
// version (3 bits) | 5 bit reserved for future use (RFU)
bool PresenceDecoderV1::Decode(const ByteArray &encoded_data,
                               const Crypto &crypto,
                               const Crypto &identity_crypto,
                               const ByteArray &key,
                               const ByteArray &metadata_encryption_key_tag) {
  // 1 + 1 + 2 + 2 + 16
  constexpr size_t kMinAdvertisementLength = 22;
  constexpr size_t kHeaderIndex = 0;
  constexpr size_t kSaltIndex = 1;
  constexpr size_t kIdentityIndex = 4;
  constexpr size_t kDataElementIndex = 22;
  constexpr size_t kIdentityHeaderLength = 2;
  constexpr size_t kDataElementSignatureLength = 16;

  constexpr uint8_t kVersionMask = 0b11100000;
  constexpr uint8_t kVersion = 1;

  chre::Optional<DataElementHeaderV1> de_header;
  uint8_t *const data = encoded_data.data;
  const size_t data_size = encoded_data.length;

  if (data_size < kMinAdvertisementLength) {
    LOGE(
        "Encoded advertisement does not have sufficient bytes to include "
        "de_header, salt, and identity");
    return false;
  }
  if ((data[kHeaderIndex] & kVersionMask) >> 5 != kVersion) {
    LOGE("Advertisement version is not v1");
    return false;
  }

  // Decodes salt.
  de_header =
      DataElementHeaderV1::Decode(&data[kSaltIndex], data_size - kSaltIndex);
  if (de_header.has_value() &&
      de_header->type == DataElementHeaderV1::kSaltType &&
      de_header->length == DataElementHeaderV1::kSaltLength) {
    salt[0] = data[kSaltIndex + 1];
    salt[1] = data[kSaltIndex + 2];
  } else {
    LOGE("Advertisement has no valid salt.");
    return false;
  }

  de_header = DataElementHeaderV1::Decode(&data[kIdentityIndex],
                                          data_size - kIdentityIndex);
#ifdef LOG_INCLUDE_SENSITIVE_INFO
  size_t identity_data_index = kIdentityIndex + kIdentityHeaderLength;
  LOGD_SENSITIVE_INFO("encrypted identity:");
  for (size_t i = identity_data_index;
       i < (identity_data_index + DataElementHeaderV1::kIdentityLength); i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, data[i]);
  }
  LOGD_SENSITIVE_INFO("metadata encryption key tag:");
  for (size_t i = 0; i < metadata_encryption_key_tag.length; i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, metadata_encryption_key_tag.data[i]);
  }
  LOGD_SENSITIVE_INFO("SALT [ %" PRIi8 ", %" PRIi8 "]", salt[0], salt[1]);
  LOGD_SENSITIVE_INFO("authenticity key:");
  for (size_t i = 0; i < key.length; i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, key.data[i]);
  }
#endif
  ByteArray decrypted_identity_array(identity,
                                     DataElementHeaderV1::kIdentityLength);
  if (de_header.has_value() &&
      de_header->type >= DataElementHeaderV1::kPrivateIdentityType &&
      de_header->type <= DataElementHeaderV1::kProvisionIdentityType &&
      de_header->length == DataElementHeaderV1::kIdentityLength) {
    if (!identity_crypto.decrypt(
            ByteArray(&data[kIdentityIndex + kIdentityHeaderLength],
                      DataElementHeaderV1::kIdentityLength),
            ByteArray(salt, DataElementHeaderV1::kSaltLength), key,
            decrypted_identity_array)) {
      LOGE("Fail to decrypt the identity.");
      return false;
    }
#ifdef LOG_INCLUDE_SENSITIVE_INFO
    LOGD_SENSITIVE_INFO("decrypted identity:");
    for (size_t i = 0; i < decrypted_identity_array.length; i++) {
      LOGD_SENSITIVE_INFO("%" PRIi8, decrypted_identity_array.data[i]);
    }
#endif
  } else {
    LOGE("Advertisement has no identity.");
    return false;
  }
  if (!identity_crypto.verify(decrypted_identity_array, ByteArray(),
                              metadata_encryption_key_tag)) {
    LOGD("Authenticity key not matched.");
    return false;
  }

  if (data_size == kMinAdvertisementLength) {
    LOGD("Presence advertisement has no data elements.");
    return true;
  }

  size_t signature_length = kDataElementIndex + kDataElementSignatureLength;
  if (data_size < signature_length) {
    LOGE(
        "Presence advertisement data elements signature has less than %zu "
        "bytes.",
        kDataElementSignatureLength);
    return false;
  }
  size_t de_length = data_size - signature_length;

  // Decodes Data Elements.
  ByteArray decrypted_byte_array(decryption_output_buffer, de_length);
#ifdef LOG_INCLUDE_SENSITIVE_INFO
  LOGD_SENSITIVE_INFO("Data Elements length %d and encrypted bytes:",
                      (int)de_length);
  for (size_t i = kDataElementIndex; i < (kDataElementIndex + de_length); i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, data[i]);
  }
  LOGD_SENSITIVE_INFO("Salt bytes: %" PRIi8 " %" PRIu8, salt[0], salt[1]);
  LOGD_SENSITIVE_INFO("authenticity key:");
  for (size_t i = 0; i < key.length; i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, key.data[i]);
  }
#endif
  if (!crypto.decrypt(ByteArray(&data[kDataElementIndex], de_length),
                      ByteArray(salt, DataElementHeaderV1::kSaltLength), key,
                      decrypted_byte_array)) {
    LOGE("Fail to decrypt data elements.");
    return false;
  }
#ifdef LOG_INCLUDE_SENSITIVE_INFO
  LOGD_SENSITIVE_INFO("decrypted data elements bytes:");
  for (size_t i = 0; i < decrypted_byte_array.length; i++) {
    LOGD_SENSITIVE_INFO("%" PRIi8, decrypted_byte_array.data[i]);
  }
#endif
  if (!crypto.verify(decrypted_byte_array, key,
                     ByteArray(&data[data_size - kDataElementSignatureLength],
                               kDataElementSignatureLength))) {
    LOGE("Fail to verify data elements with signature.");
    return false;
  }
  if (!DecodeDataElements(decrypted_byte_array.data,
                          decrypted_byte_array.length)) {
    LOGE("Advertisement has invalid data elements.");
    return false;
  }

  decoded = true;
  return true;
}

bool PresenceDecoderV1::DecodeDataElements(uint8_t data[], size_t data_size) {
  chre::Optional<DataElementHeaderV1> de_header;
  num_actions = 0;
  for (size_t index = 0; index < data_size;) {
    de_header = DataElementHeaderV1::Decode(&data[index], data_size - index);
    if (de_header.has_value()) {
      switch (de_header->type) {
        case DataElementHeaderV1::kActionType:
          if (de_header->length != DataElementHeaderV1::kActionLength) {
            LOGE("Advertisement has incorrect action length");
            return false;
          }
          actions[num_actions] = data[index + de_header->header_length];
          num_actions++;
          break;
        case DataElementHeaderV1::kTxPowerType:
          if (de_header->length != DataElementHeaderV1::kTxPowerLength) {
            LOGE("Advertisement has incorrect TX power length");
            return false;
          }
          tx_power = ByteArray(&data[index + de_header->header_length],
                               de_header->length);
          break;
        case DataElementHeaderV1::kModelIdType:
          if (de_header->length != DataElementHeaderV1::kModelIdLength) {
            LOGE("Advertisement has incorrect model ID length");
            return false;
          }
          model_id = ByteArray(&data[index + de_header->header_length],
                               de_header->length);
          break;
        case DataElementHeaderV1::kConnectionStatusType:
          if (de_header->length !=
              DataElementHeaderV1::kConnectionStatusLength) {
            LOGE("Advertisement has incorrect connection status length");
            return false;
          }
          connection_status = ByteArray(&data[index + de_header->header_length],
                                        de_header->length);
          break;
        case DataElementHeaderV1::kBatteryStatusType:
          if (de_header->length != DataElementHeaderV1::kBatteryStatusLength) {
            LOGE("Advertisement has incorrect battery status length");
            return false;
          }
          battery_status = ByteArray(&data[index + de_header->header_length],
                                     de_header->length);
          break;
        default:
          if (IsValidExtDataElementsType(de_header->type)) {
            extended_des.push_back(DataElement(
                static_cast<nearby_DataElement_ElementType>(de_header->type),
                ByteArray(&data[index + de_header->header_length],
                          de_header->length)));
          } else {
            LOGD("Invalid DE type(%" PRIi64 ") is included", de_header->type);
          }
          break;
      }
    }
    index = index + de_header->header_length + de_header->length;
  }
  return true;
}

bool PresenceDecoderV1::IsValidExtDataElementsType(uint64_t type) {
#ifdef ENABLE_TEST_DE
  return (type >= nearby_DataElement_ElementType_DE_TEST_BEGIN &&
          type <= nearby_DataElement_ElementType_DE_TEST_END);
#endif
  return false;
}

chre::Optional<DataElementHeaderV1> DataElementHeaderV1::Decode(
    const uint8_t data[], const size_t data_size) {
  // The bytes to define Data Element type should be less than 8.
  constexpr size_t kTypeMaxByteLength = 8;
  constexpr uint8_t kExtendBitMask = 0b10000000;
  constexpr uint8_t kNoneExtendBitsMask = 0b01111111;
  constexpr uint8_t kLengthBitsMask = 0b01110000;
  constexpr uint8_t kTypeBitsMask = 0b00001111;

  DataElementHeaderV1 header;

  if (data_size == 0) {
    LOGE("Decode Data Element header from zero byte.");
    return chre::Optional<DataElementHeaderV1>();
  }

  // Single byte header
  if ((data[0] & kExtendBitMask) == 0) {
    header.length = (data[0] & kLengthBitsMask) >> 4;
    header.type = (data[0] & kTypeBitsMask);
    header.header_length = 1;
    LOGD("Return single byte header with length: %" PRIu8 " and type: %" PRIu64,
         header.length, header.type);
    return header;
  }

  // multi-byte header
  header.length = data[0] & kNoneExtendBitsMask;
  header.type = 0;
  size_t i = 1;
  while (true) {
    if (i > kTypeMaxByteLength) {
      LOGE("Type exceeds the maximum byte length: %zu", kTypeMaxByteLength);
      return chre::Optional<DataElementHeaderV1>();
    }
    if (i >= data_size) {
      LOGE("Extended byte exceeds the data size.");
      return chre::Optional<DataElementHeaderV1>();
    }
    header.type = (header.type << 7) | (data[i] & kNoneExtendBitsMask);
    if ((data[i] & kExtendBitMask) == 0) {
      break;
    } else {
      i++;
    }
  }
  header.header_length = static_cast<uint8_t>(i + 1);
  LOGD("Return multi byte header with length: %" PRIu8 " and type: %" PRIu64,
       header.length, header.type);
  return header;
}

}  // namespace nearby
