/*
 * Copyright (C) 2015 The Android Open Source Project
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

#include "gtest/gtest.h"

#include <utility>

#include "berberis/base/exec_region_anonymous.h"

namespace berberis {

namespace {

TEST(ExecRegionAnonymous, Smoke) {
  const char buf[] = "deadbeef";

  ExecRegion exec = ExecRegionAnonymousFactory::Create(sizeof(buf));
  const uint8_t* code = exec.begin();
  ASSERT_NE(nullptr, code);

  exec.Write(code, buf, sizeof(buf));
  ASSERT_EQ('f', code[7]);

  exec.Detach();
  ASSERT_EQ('f', code[7]);

  exec.Free();
}

}  // namespace

}  // namespace berberis
