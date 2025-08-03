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

#pragma once

#include <base/logging.h>

#include <optional>
#include <string>

#include "hardware/bt_has.h"

namespace le_audio {
namespace has {
/* Represents preset instance. It stores properties such as preset name,
 * preset index and if it supports renaming. Also stores all the needed
 * GATT characteristics and descriptor informations.
 */
class HasPreset {
 private:
  mutable std::string name_;
  mutable uint8_t properties_;
  uint8_t index_;

 public:
  static constexpr size_t kCharValueMinSize = 1 /*index*/ + 1 /*properties*/;

  static constexpr uint8_t kPropertyWritable = 0x01;
  static constexpr uint8_t kPropertyAvailable = 0x02;

  static constexpr uint8_t kPresetNameLengthLimit = 40;

  HasPreset(uint8_t index, uint8_t props = 0,
            std::optional<std::string> name = std::nullopt)
      : properties_(props), index_(index) {
    name_ = name.value_or("");
  }
  HasPreset()
      : name_(""),
        properties_(0),
        index_(bluetooth::has::kHasPresetIndexInvalid) {}

  auto& GetName() const { return name_; }
  decltype(index_) GetIndex() const { return index_; }
  decltype(properties_) GetProperties() const { return properties_; }
  bool IsWritable() const { return properties_ & kPropertyWritable; }
  bool IsAvailable() const { return properties_ & kPropertyAvailable; }

  HasPreset& operator=(const HasPreset& other) {
    LOG_ASSERT(index_ == other.GetIndex())
        << "Assigning immutable preset index!";

    if ((this != &other) && (*this != other)) {
      index_ = other.GetIndex();
      name_ = other.GetName();
    }
    return *this;
  }

  bool operator==(const HasPreset& b) const {
    return (index_ == b.index_) && (properties_ == b.properties_) &&
           (name_ == b.name_);
  }
  bool operator!=(const HasPreset& b) const {
    return (index_ != b.index_) || (properties_ != b.properties_) ||
           (name_ != b.name_);
  }
  bool operator<(const HasPreset& b) const { return index_ < b.index_; }
  friend std::ostream& operator<<(std::ostream& os, const HasPreset& b);

  struct ComparatorDesc {
    using is_transparent = void;
    bool operator()(HasPreset const& a, int index) const {
      return a.index_ < index;
    }
    bool operator()(int index, HasPreset const& a) const {
      return index < a.index_;
    }
    bool operator()(HasPreset const& a, HasPreset const& b) const {
      return a.index_ < b.index_;
    }
  };

  static std::optional<HasPreset> FromCharacteristicValue(uint16_t& len,
                                                          const uint8_t* value);
  void ToCharacteristicValue(std::vector<uint8_t>& value) const;

  /* Calculates buffer space that the preset will use when serialized */
  uint8_t SerializedSize() const {
    return (sizeof(index_) + sizeof(properties_) + 1 /* name length */
            + name_.length());
  }
  /* Serializes into binary blob for the persistent storage */
  uint8_t* Serialize(uint8_t* p_out, size_t buffer_size) const;
  /* Deserializes binary blob read from the persistent storage */
  static const uint8_t* Deserialize(const uint8_t* p_in, size_t len,
                                    HasPreset& preset);
};

}  // namespace has
}  // namespace le_audio
