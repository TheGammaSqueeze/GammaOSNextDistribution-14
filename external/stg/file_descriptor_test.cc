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
// Author: Matthias Maennich

#include "file_descriptor.h"

#include <fcntl.h>
#include <unistd.h>

#include <utility>

#include <catch2/catch.hpp>

namespace Test {

TEST_CASE("default construction") {
  stg::FileDescriptor fd;
  CHECK_THROWS(fd.Value());
}

TEST_CASE("successful open") {
  stg::FileDescriptor fd("/dev/null", O_RDONLY);
  CHECK(fd.Value());
}

TEST_CASE("failed open") {
  CHECK_THROWS(stg::FileDescriptor("/dev/unicorn_null", O_RDONLY));
}

TEST_CASE("double close") {
  CHECK_THROWS([]() {
    stg::FileDescriptor fd("/dev/null", O_RDONLY);
    close(fd.Value());
    CHECK_NOTHROW(fd.Value());  // value is still ok
  }());                         // throws on destruction
}

TEST_CASE("ownership transfer on move") {
  stg::FileDescriptor fd("/dev/null", O_RDONLY);
  CHECK_NOTHROW(fd.Value());  // value is still ok

  const auto fd_val = fd.Value();

  auto fd2(std::move(fd));
  CHECK_THROWS(fd.Value());
  CHECK(fd_val == fd2.Value());

  auto fd3(std::move(fd2));
  CHECK_THROWS(fd2.Value());
  CHECK(fd_val == fd3.Value());
}

}  // namespace Test
