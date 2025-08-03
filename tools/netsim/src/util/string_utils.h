/*
 * Copyright 2022 The Android Open Source Project
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
#include <iostream>
#include <string>
#include <string_view>
#include <vector>

namespace netsim {
namespace stringutils {

std::string ToHexString(uint8_t x, uint8_t y);
std::string ToHexString(uint8_t x);
std::string ToHexString(const uint8_t *, size_t);
std::string ToHexString(const std::vector<uint8_t> &data, int max_length);

std::string_view LTrim(const std::string_view);
std::string_view RTrim(const std::string_view);
std::string_view Trim(const std::string_view);
std::vector<std::string_view> Split(const std::string_view,
                                    const std::string_view &);
inline std::string AsString(std::string_view v) { return {v.data(), v.size()}; }

}  // namespace stringutils
}  // namespace netsim
