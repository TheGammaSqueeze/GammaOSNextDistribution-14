// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2023 Google LLC
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
// Author: Siddharth Nayyar

#ifndef STG_READER_OPTIONS_H_
#define STG_READER_OPTIONS_H_

#include <type_traits>

namespace stg {

struct ReadOptions {
  enum Value {
    TYPE_ROOTS = 1 << 0,
  };

  using Bitset = std::underlying_type_t<Value>;

  ReadOptions() = default;
  template <typename... Values>
  explicit ReadOptions(Values... values) {
    for (auto value : {values...}) {
      Set(value);
    }
  }

  void Set(Value other) {
    bitset |= static_cast<Bitset>(other);
  }

  bool Test(Value other) const {
    return static_cast<bool>(bitset & static_cast<Bitset>(other));
  }

  Bitset bitset = 0;
};

}  // namespace stg

#endif  // STG_READER_OPTIONS_H_
