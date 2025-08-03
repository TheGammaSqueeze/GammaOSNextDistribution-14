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

#ifndef LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_DECODER_V1_H_
#define LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_DECODER_V1_H_

#include <cstddef>
#include <cstdint>

#include "location/lbs/contexthub/nanoapps/nearby/byte_array.h"
#include "location/lbs/contexthub/nanoapps/nearby/crypto.h"
#include "location/lbs/contexthub/nanoapps/nearby/proto/ble_filter.nanopb.h"
#include "third_party/contexthub/chre/util/include/chre/util/dynamic_vector.h"
#include "third_party/contexthub/chre/util/include/chre/util/optional.h"

namespace nearby {

// Represents a Data Element header.
struct DataElementHeaderV1 {
  // Decodes data and returns the first Data Element header.
  // Returns no value if decoding fails.
  static chre::Optional<DataElementHeaderV1> Decode(const uint8_t data[],
                                                    const size_t data_size);

  static constexpr uint64_t kSaltType = 0;
  static constexpr uint64_t kPrivateIdentityType = 1;
  static constexpr uint64_t kProvisionIdentityType = 4;
  static constexpr uint64_t kTxPowerType = 5;
  static constexpr uint64_t kActionType = 6;
  static constexpr uint64_t kModelIdType = 7;
  static constexpr uint64_t kConnectionStatusType = 10;
  static constexpr uint64_t kBatteryStatusType = 11;

  static constexpr size_t kSaltLength = 2;
  static constexpr size_t kIdentityLength = 16;
  static constexpr size_t kTxPowerLength = 1;
  static constexpr size_t kActionLength = 1;
  static constexpr size_t kModelIdLength = 3;
  static constexpr size_t kConnectionStatusLength = 3;
  static constexpr size_t kBatteryStatusLength = 3;

  // length of data element value.
  uint8_t length;
  // length of header itself.
  uint8_t header_length;
  // type of data element.
  uint64_t type;
};

struct DataElement {
  DataElement(nearby_DataElement_ElementType key, ByteArray value)
      : key(key), value(value) {}

  nearby_DataElement_ElementType key;
  ByteArray value;
};

// PresenceDecoderV1 contains data fields specified by Presence V1.
struct PresenceDecoderV1 {
  static constexpr size_t kMaxNumActions = 5;
  static constexpr size_t kDecryptionOutputBufSize = 16 * 20;

  PresenceDecoderV1() = default;
  // Decodes encoded_data which is a byte array encoded by following the
  // Presence V1 specification. Returns true when decoding succeeds.
  bool Decode(const ByteArray &encoded_data, const Crypto &crypto,
              const Crypto &identity_crypto, const ByteArray &key,
              const ByteArray &metadata_encryption_key_tag);
  // Helper function to decode Presence data elements from data.
  // Returns true if decoding succeeds.
  bool DecodeDataElements(uint8_t data[], size_t data_size);
  // Returns true if valid Extended DE type.
  bool IsValidExtDataElementsType(uint64_t type);

  // required fields.
  uint8_t salt[DataElementHeaderV1::kSaltLength];
  uint8_t identity[DataElementHeaderV1::kIdentityLength];

  // repeated fields.
  uint8_t actions[kMaxNumActions];
  size_t num_actions = 0;

  // optional fields. An empty field is defined as Zero length of ByteArray.
  ByteArray tx_power;
  ByteArray model_id;
  ByteArray connection_status;
  ByteArray battery_status;

  // Extended DE list.
  chre::DynamicVector<DataElement> extended_des;

  // Sets to true after successfully decoding.
  bool decoded = false;

 private:
  // Decrypted buffer provide the underlying storage for optional fields.
  uint8_t decryption_output_buffer[kDecryptionOutputBufSize];
};

}  // namespace nearby

#endif  // LOCATION_LBS_CONTEXTHUB_NANOAPPS_NEARBY_PRESENCE_DECODER_V1_H_
