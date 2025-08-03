//
// Copyright (C) 2022 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//

#ifndef __BUFFER_VIEW_CPP_H
#define __BUFFER_VIEW_CPP_H

#include <stddef.h>
#include <assert.h>

// non-owning reference to a contiguous memory region. Similar to
// std::string_view, but allows you to modify underlying memory region.
template <typename T>
struct BufferView {
  constexpr BufferView() = default;
  constexpr BufferView(T* data, size_t size) : ptr_(data), size_(size) {}

  T* data() { return ptr_; }
  size_t size() const { return size_; }
  T* begin() { return ptr_; };
  T* end() { return ptr_ + size_; }
  bool empty() const { return size_ == 0; }

  T& operator[](const size_t idx) {
    assert(idx < size_);
    return ptr_[idx];
  }
  const T& operator[](const size_t idx) const {
    assert(idx < size_);
    return ptr_[idx];
  }

 private:
  T* ptr_{};
  size_t size_{};
};

#endif
