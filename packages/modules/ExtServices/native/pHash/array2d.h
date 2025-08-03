/*
 * Copyright 2021 The Android Open Source Project
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

#include "phash_config.h"

namespace android {

// Represent a 2-D array of Length by kImageLength.
template <typename T>
class Array2D {
public:
    Array2D(T* buffer2d) : buffer(buffer2d) {}

    const T& operator()(const int y, const int x) const { return buffer[y * kImageLength + x]; }

    T& operator()(const int y, const int x) { return buffer[y * kImageLength + x]; }

    int width() const { return kImageLength; }
    int height() const { return kImageLength; }

private:
    T* const buffer;
};

} // namespace android
