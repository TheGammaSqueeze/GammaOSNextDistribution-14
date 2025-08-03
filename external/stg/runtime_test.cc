// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2022-2023 Google LLC
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

#include "runtime.h"

#include <array>
#include <cstddef>
#include <sstream>
#include <string>

#include <catch2/catch.hpp>

namespace Test {

TEST_CASE("empty") {
  std::ostringstream os;
  {
    const stg::Runtime runtime(os, true);
  }
  CHECK(os.str().empty());
}

TEST_CASE("times") {
  const size_t count = 20;
  std::ostringstream os;
  {
    stg::Runtime runtime(os, true);
    const std::array<const stg::Time, count> timers = {
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
      stg::Time(runtime, "name"),
    };
  }
  std::istringstream is(os.str());
  const std::string name = "name:";
  const std::string ms = "ms";
  size_t index = 0;
  double last_time = 0.0;
  while (is && index < count) {
    std::string first;
    double time;
    std::string second;
    is >> first >> time >> second;
    CHECK(first == name);
    CHECK(time > last_time);
    CHECK(second == ms);
    last_time = time;
    ++index;
  }
  CHECK(index == count);
  std::string junk;
  is >> junk;
  CHECK(junk.empty());
  CHECK(is.eof());
}

TEST_CASE("counters") {
  std::ostringstream os;
  {
    stg::Runtime runtime(os, true);
    stg::Counter a(runtime, "a");
    stg::Counter b(runtime, "b");
    stg::Counter c(runtime, "c");
    const stg::Counter d(runtime, "d");
    stg::Counter e(runtime, "e");
    c = 17;
    ++b;
    ++b;
    e = 1;
    a = 3;
    c += 2;
  }
  const std::string expected = "e: 1\nd: 0\nc: 19\nb: 2\na: 3\n";
  CHECK(os.str() == expected);
}

TEST_CASE("histogram") {
  std::ostringstream os;
  {
    stg::Runtime runtime(os, true);
    stg::Histogram h(runtime, "h");
    h.Add(13);
    h.Add(14);
    h.Add(13);
    h.Add(12);
  }
  const std::string expected = "h: [12]=1 [13]=2 [14]=1\n";
  CHECK(os.str() == expected);
}

}  // namespace Test
