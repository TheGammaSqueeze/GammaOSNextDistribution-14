/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef NOGROD_BUFFER_
#define NOGROD_BUFFER_

#include <cstddef>
#include <vector>

namespace nogrod {

// This class represents a buffer which might optionally
// be backed by std::vector<T> and therefore is move-only.
template <typename T>
class Buffer {
 public:
  Buffer() = default;
  explicit Buffer(std::vector<T> buffer)
      : buffer_{std::move(buffer)}, data_{buffer_.data()}, size_{buffer_.size()} {}
  explicit constexpr Buffer(const T* data, size_t size) : data_{data}, size_{size} {}

  constexpr const T* data() const { return data_; }
  constexpr size_t size() const { return size_; }

  // Move-only
  Buffer(const Buffer<T>&) = delete;
  Buffer<T>& operator=(const Buffer<T>&) = delete;

  // Since I haven't found a written guarantee that std::vector move
  // preserves the data pointer we cannot rely on default move here.
  Buffer(Buffer<T>&& that) { *this = std::move(that); }

  Buffer<T>& operator=(Buffer<T>&& that) {
    bool that_data_points_to_vector = (that.data_ == that.buffer_.data());
    this->buffer_ = std::move(that.buffer_);
    if (that_data_points_to_vector) {
      this->data_ = this->buffer_.data();
    } else {
      this->data_ = that.data_;
    }
    this->size_ = that.size_;

    return *this;
  }

 private:
  std::vector<T> buffer_;
  const T* data_;
  size_t size_;
};

}  // namespace nogrod

#endif  // NOGROD_BUFFER_