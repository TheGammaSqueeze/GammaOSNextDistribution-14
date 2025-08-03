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

#pragma once

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

template <class T> struct Span {
    Span() : begin_(nullptr), end_(nullptr) {}
    template <size_t N> Span(T (&a)[N]) : begin_(&a[0]), end_(&a[N]) {}
    template <class I> Span(I begin, I end) : begin_(&*begin), end_(&*end) {}
    template <class I> Span(I begin, size_t size) : begin_(&*begin), end_(&begin[size]) {}

    T* data() { return begin_; }
    const T* data() const { return begin_; }
    T* begin() { return begin_; }
    T* end() { return end_; }
    const T* begin() const { return begin_; }
    const T* end() const { return end_; }
    size_t size() const { return end_ - begin_; }
    T& operator[](size_t i) { return begin_[i]; }
    const T& operator[](size_t i) const { return begin_[i]; }

private:
    T* begin_;
    T* end_;
};

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
