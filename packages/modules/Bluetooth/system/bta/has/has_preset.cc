/*
 * Copyright 2021 HIMSA II K/S - www.himsa.com.
 * Represented by EHIMA - www.ehima.com
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

#include "has_preset.h"

#include <bluetooth/log.h>

#include "stack/include/bt_types.h"

using namespace bluetooth;

namespace le_audio {
namespace has {

std::optional<HasPreset> HasPreset::FromCharacteristicValue(
    uint16_t& len, const uint8_t* value) {
  if ((len < kCharValueMinSize) ||
      (len > kCharValueMinSize + kPresetNameLengthLimit)) {
    log::error("Preset record to long: {}", len);
    return std::nullopt;
  }

  HasPreset preset;
  STREAM_TO_UINT8(preset.index_, value);
  --len;
  STREAM_TO_UINT8(preset.properties_, value);
  --len;
  preset.name_ = std::string(value, value + len);

  return preset;
}

void HasPreset::ToCharacteristicValue(std::vector<uint8_t>& value) const {
  auto initial_offset = value.size();

  value.resize(value.size() + kCharValueMinSize + name_.size());
  auto pp = value.data() + initial_offset;

  UINT8_TO_STREAM(pp, index_);
  UINT8_TO_STREAM(pp, properties_);
  ARRAY_TO_STREAM(pp, name_.c_str(), (int)name_.size());
}

uint8_t* HasPreset::Serialize(uint8_t* p_out, size_t buffer_size) const {
  if (buffer_size < SerializedSize()) {
    log::error("Invalid output buffer size!");
    return p_out;
  }

  uint8_t name_len = name_.length();
  if (name_len > kPresetNameLengthLimit) {
    log::error("Invalid preset name length. Cannot be serialized!");
    return p_out;
  }

  /* Serialized data length */
  UINT8_TO_STREAM(p_out, name_len + 2);

  UINT8_TO_STREAM(p_out, index_);
  UINT8_TO_STREAM(p_out, properties_);
  ARRAY_TO_STREAM(p_out, name_.c_str(), (int)name_.size());
  return p_out;
}

const uint8_t* HasPreset::Deserialize(const uint8_t* p_in, size_t len,
                                      HasPreset& preset) {
  const uint8_t nonamed_size = HasPreset(0, 0).SerializedSize();
  auto* p_curr = p_in;

  if (len < nonamed_size) {
    log::error("Invalid buffer size {}. Cannot deserialize.", len);
    return p_in;
  }

  uint8_t serialized_data_len;
  STREAM_TO_UINT8(serialized_data_len, p_curr);
  if (serialized_data_len < 2) {
    log::error("Invalid data size. Cannot be deserialized!");
    return p_in;
  }

  auto name_len = serialized_data_len - 2;
  if ((name_len > kPresetNameLengthLimit) ||
      ((size_t)nonamed_size + name_len > len)) {
    log::error("Invalid preset name length. Cannot be deserialized!");
    return p_in;
  }

  STREAM_TO_UINT8(preset.index_, p_curr);
  STREAM_TO_UINT8(preset.properties_, p_curr);
  if (name_len) preset.name_ = std::string((const char*)p_curr, name_len);

  return p_curr + name_len;
}

std::ostream& operator<<(std::ostream& os, const HasPreset& b) {
  os << "{\"index\": " << +b.GetIndex();
  os << ", \"name\": \"" << b.GetName() << "\"";
  os << ", \"is_available\": " << (b.IsAvailable() ? "\"True\"" : "\"False\"");
  os << ", \"is_writable\": " << (b.IsWritable() ? "\"True\"" : "\"False\"");
  os << "}";
  return os;
}

}  // namespace has
}  // namespace le_audio
