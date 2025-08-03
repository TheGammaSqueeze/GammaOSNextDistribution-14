// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Giuliano Procida
// Author: Siddharth Nayyar

#ifndef STG_HASHING_H_
#define STG_HASHING_H_

#include <cstddef>
#include <cstdint>
#include <functional>
#include <string>
#include <string_view>

namespace stg {

struct HashValue {
  constexpr explicit HashValue(uint32_t value) : value(value) {}
  // TODO: bool operator==(const HashValue&) const = default;
  bool operator==(const HashValue& other) const {
    return value == other.value;
  }
  bool operator!=(const HashValue& other) const {
    return value != other.value;
  }

  uint32_t value;
};

}  // namespace stg

namespace std {

template <>
struct hash<stg::HashValue> {
  size_t operator()(const stg::HashValue& hv) const {
    // do not overhash
    return hv.value;
  }
};

}  // namespace std

namespace stg {

struct Hash {
  constexpr HashValue operator()(HashValue hash_value) const {
    return hash_value;
  }

  // Hash boolean by converting to int.
  constexpr HashValue operator()(bool x) const {
    return x ? (*this)(1) : (*this)(0);
  }

  // Hash unsigned 64 bits by splitting, hashing and combining.
  constexpr HashValue operator()(uint64_t x) const {
    const uint32_t lo = x;
    const uint32_t hi = x >> 32;
    return (*this)(lo, hi);
  }

  // Hash signed 64 bits by casting to unsigned 64 bits.
  constexpr HashValue operator()(int64_t x) const {
    return (*this)(static_cast<uint64_t>(x));
  }

  // See https://github.com/skeeto/hash-prospector.
  constexpr HashValue operator()(uint32_t x) const {
    x ^= x >> 16;
    x *= 0x21f0aaad;
    x ^= x >> 15;
    x *= 0xd35a2d97;
    x ^= x >> 15;
    return HashValue(x);
  }

  // Hash signed 32 bits by casting to unsigned 32 bits.
  constexpr HashValue operator()(int32_t x) const {
    return (*this)(static_cast<uint32_t>(x));
  }

  // Hash 8 bits by zero extending to 32 bits.
  constexpr HashValue operator()(char x) const {
    return (*this)(static_cast<uint32_t>(static_cast<unsigned char>(x)));
  }

  // 32-bit FNV-1a. See https://wikipedia.org/wiki/Fowler-Noll-Vo_hash_function.
  constexpr HashValue operator()(const std::string_view x) const {
    uint32_t h = 0x811c9dc5;
    for (auto ch : x) {
      h ^= static_cast<unsigned char>(ch);
      h *= 0x01000193;
    }
    return HashValue(h);
  }

  // Hash std::string by constructing a std::string_view.
  HashValue operator()(const std::string& x) const {
    return (*this)(std::string_view(x));
  }

  // Hash C string by constructing a std::string_view.
  constexpr HashValue operator()(const char* x) const {
    return (*this)(std::string_view(x));
  }

  // Reverse order Boost hash_combine (must be used with good hashes).
  template <typename Arg, typename... Args>
  constexpr HashValue operator()(Arg arg, Args... args) const {
    const uint32_t seed = (*this)(args...).value;
    const uint32_t hash = (*this)(arg).value;
    return HashValue(seed ^ (hash + 0x9e3779b9 + (seed << 6) + (seed >> 2)));
  }
};

}  // namespace stg

#endif  // STG_HASHING_H_
