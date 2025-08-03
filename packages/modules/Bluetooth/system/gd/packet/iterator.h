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
#include <memory>
#include <type_traits>

#include "packet/custom_field_fixed_size_interface.h"
#include "packet/view.h"

namespace bluetooth {
namespace packet {

// std::iterator is deprecated in C++17 onwards. Instead, you must declare all
// 5 aliases that the iterator needs for the std library.
#if __cplusplus >= 201703L
struct IteratorTraits {
  using iterator_category = std::random_access_iterator_tag;
  using value_type = uint8_t;
  using difference_type = std::ptrdiff_t;
  using pointer = uint8_t*;
  using reference = uint8_t&;
};
#else
struct IteratorTraits : public std::iterator<std::random_access_iterator_tag, uint8_t> {};
#endif

// Templated Iterator for endianness
template <bool little_endian>
class Iterator : public IteratorTraits {
 public:
  Iterator(const std::forward_list<View>& data, size_t offset);
  Iterator(std::shared_ptr<std::vector<uint8_t>> data);
  Iterator(const Iterator& itr) = default;
  virtual ~Iterator() = default;

  // All addition and subtraction operators are unbounded.
  Iterator operator+(int offset) const;
  Iterator& operator+=(int offset);
  Iterator& operator++();

  Iterator operator-(int offset) const;
  int operator-(const Iterator& itr) const;
  Iterator& operator-=(int offset);
  Iterator& operator--();

  Iterator& operator=(const Iterator& itr);

  bool operator!=(const Iterator& itr) const;
  bool operator==(const Iterator& itr) const;

  bool operator<(const Iterator& itr) const;
  bool operator>(const Iterator& itr) const;

  bool operator<=(const Iterator& itr) const;
  bool operator>=(const Iterator& itr) const;

  uint8_t operator*() const;

  size_t NumBytesRemaining() const;

  Iterator Subrange(size_t index, size_t length) const;

  // Get the next sizeof(T) bytes and return the filled type
  template <typename T, typename std::enable_if<std::is_trivial<T>::value, int>::type = 0>
  T extract() {
    static_assert(std::is_trivial<T>::value, "Iterator::extract requires a fixed-width type.");
    T extracted_value{};
    uint8_t* value_ptr = (uint8_t*)&extracted_value;

    for (size_t i = 0; i < sizeof(T); i++) {
      size_t index = (little_endian ? i : sizeof(T) - i - 1);
      value_ptr[index] = this->operator*();
      this->operator++();
    }
    return extracted_value;
  }

  template <typename T, typename std::enable_if<std::is_base_of_v<CustomFieldFixedSizeInterface<T>, T>, int>::type = 0>
  T extract() {
    T extracted_value{};
    for (size_t i = 0; i < CustomFieldFixedSizeInterface<T>::length(); i++) {
      size_t index = (little_endian ? i : CustomFieldFixedSizeInterface<T>::length() - i - 1);
      extracted_value.data()[index] = this->operator*();
      this->operator++();
    }
    return extracted_value;
  }

 private:
  std::forward_list<View> data_;
  size_t index_;
  size_t begin_;
  size_t end_;
};

}  // namespace packet
}  // namespace bluetooth
