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
 *
 * The implementation below follows Fast Pair Spec
 * https://developers.google.com/nearby/fast-pair/specifications/service/provider#provider_advertising_signal
 * and SASS spec
 * https://developers.devsite.corp.google.com/nearby/fast-pair/early-access/specifications/extensions/sass#SmartAudioSourceSwitching
 */

#include "location/lbs/contexthub/nanoapps/nearby/fast_pair_account_data.h"

#include "third_party/contexthub/chre/util/include/chre/util/nanoapp/log.h"

#define LOG_TAG "[NEARBY][FAST_PAIR_ACCOUNT_DATA]"

namespace nearby {

namespace {
constexpr uint8_t kAccountFilterUiType = 0b0000;
constexpr uint8_t kAccountFilterNoUiType = 0b0010;
constexpr uint8_t kSaltType = 0b0001;
constexpr uint8_t kBatteryUiType = 0b0011;
constexpr uint8_t kBatteryNoUiType = 0b0100;
constexpr uint8_t kRrdType = 0b0110;

struct Header {
  Header(uint8_t length, uint8_t type) : length(length), type(type) {}

  static Header Parse(uint8_t byte) {
    // The byte has 4 bits length and 4 bits type as 0bLLLLTTTT.
    return Header((byte & 0xF0) >> 4, byte & 0x0F);
  }

  const uint8_t length;
  const uint8_t type;
};

}  // namespace

FastPairAccountData FastPairAccountData::Parse(const ByteArray &service_data) {
  const FastPairAccountData invalid_data(false, 0, ByteArray(), ByteArray(),
                                         ByteArray(), ByteArray());
  if (service_data.length == 0) {
    return invalid_data;
  }
  uint8_t *data = service_data.data;
  // First byte is Version and flags(0bVVVVFFFF), which splits the byte the same
  // way as Header.
  const uint8_t version = Header::Parse(data[0]).length;
  ByteArray filter;
  ByteArray salt;
  ByteArray battery;
  ByteArray rrd;
  // Each element has one byte header plus variable-length value.
  for (size_t i = 1; i < service_data.length;) {
    const Header header = Header::Parse(data[i]);
    // Available buffer for field data.
    size_t available_buf_size = service_data.length - i - 1;
    if (header.length > available_buf_size) {
      LOGE(
          "Invalid Fast Pair service data. Field length %d exceeds service "
          "data buffer size %zu",
          header.length, available_buf_size);
      return invalid_data;
    }
    // Element one byte header + value, used by battery and RRD.
    ByteArray header_element(&data[i], header.length + 1);
    i++;  // Move data index to the byte next to header.
    ByteArray element(&data[i], header.length);
    switch (header.type) {
      case kAccountFilterNoUiType:
      case kAccountFilterUiType:
        filter = element;
        break;
      case kSaltType:
        salt = element;
        break;
      case kBatteryUiType:
      case kBatteryNoUiType:
        battery = header_element;
        break;
      case kRrdType:
        rrd = header_element;
        break;
    }
    i += header.length;
  }
  // filter and salt are required.
  if (filter.length == 0 || salt.length == 0) {
    LOGD(
        "Invalid Fast Pair service data with filter length %zu and salt length "
        "%zu.",
        filter.length, salt.length);
    return invalid_data;
  } else {
    return FastPairAccountData(true, version, filter, salt, battery, rrd);
  }
}

}  // namespace nearby
