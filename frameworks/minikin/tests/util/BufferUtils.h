/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef MINIKIN_TEST_BUFFER_UTILS_H
#define MINIKIN_TEST_BUFFER_UTILS_H

#include <minikin/Buffer.h>
#include <vector>

namespace minikin {

template <class T>
std::vector<uint8_t> allocateBuffer(const T& t) {
    BufferWriter writer(nullptr);
    t.writeTo(&writer);
    // Fill with 0xFF for debugging.
    return std::vector<uint8_t>(writer.size(), 0xFFu);
}

template <class T, auto arg>
std::vector<uint8_t> allocateBuffer(const T& t) {
    BufferWriter writer(nullptr);
    t.template writeTo<arg>(&writer);
    // Fill with 0xFF for debugging.
    return std::vector<uint8_t>(writer.size(), 0xFFu);
}

template <class T>
std::vector<uint8_t> writeToBuffer(const T& t) {
    std::vector<uint8_t> buffer = allocateBuffer(t);
    BufferWriter writer(buffer.data());
    t.writeTo(&writer);
    return buffer;
}

}  // namespace minikin

#endif  // MINIKIN_TEST_BUFFER_UTILS_H
