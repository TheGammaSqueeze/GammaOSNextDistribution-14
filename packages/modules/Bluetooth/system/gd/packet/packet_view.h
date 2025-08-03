/*
 * Copyright 2019 The Android Open Source Project
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
#include <forward_list>
#include <vector>

#include "packet/iterator.h"
#include "packet/view.h"

namespace bluetooth {
namespace packet {

static const bool kLittleEndian = true;

// Abstract base class that is subclassed to provide type-specifc accessors.
// Holds a shared pointer to the underlying data.
// The template parameter little_endian controls the generation of extract().
template <bool little_endian>
class PacketView {
 public:
  explicit PacketView(std::forward_list<View> fragments);
  explicit PacketView(std::shared_ptr<const std::vector<uint8_t>> packet);
  PacketView(const PacketView& PacketView) = default;
  PacketView<little_endian>() = delete;
  virtual ~PacketView() = default;

  Iterator<little_endian> begin() const;
  Iterator<little_endian> end() const;

  uint8_t operator[](size_t i) const;
  uint8_t at(size_t index) const;

  size_t size() const;

  PacketView<true> GetLittleEndianSubview(size_t begin, size_t end) const;
  PacketView<false> GetBigEndianSubview(size_t begin, size_t end) const;

 protected:
  void Append(PacketView to_add);

 private:
  std::forward_list<View> fragments_;
  size_t length_;

  std::forward_list<View> GetSubviewList(size_t begin, size_t end) const;
};

}  // namespace packet
}  // namespace bluetooth
