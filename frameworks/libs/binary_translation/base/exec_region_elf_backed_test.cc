/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include "berberis/base/exec_region_elf_backed.h"

#include <dlfcn.h>

namespace berberis {

namespace {

TEST(ExecRegionElfBacked, Smoke) {
  const char buf[] = "deadbeef";

  ExecRegion exec = ExecRegionElfBackedFactory::Create(sizeof(buf));
  const uint8_t* code = exec.begin();
  ASSERT_NE(nullptr, code);

  exec.Write(code, buf, sizeof(buf));
  ASSERT_EQ('f', code[7]);

  exec.Detach();
  ASSERT_EQ('f', code[7]);

  exec.Free();
}

TEST(ExecRegionElfBacked, PltIsExecutable_b_254823538) {
  // DlClose calls .plt section for __cxa_finalize
  // This test makes sure it is called without incidents
  // http://b/254823538
  void* handle = dlopen("libberberis_exec_region.so", RTLD_NOW);
  EXPECT_NE(handle, nullptr) << dlerror();
  dlclose(handle);
}

TEST(ExecRegionElfBacked, TwoRegionsHaveDifferentAddresses) {
  auto region1 = ExecRegionElfBackedFactory::Create(1);
  auto region2 = ExecRegionElfBackedFactory::Create(1);
  EXPECT_NE(region1.begin(), region2.begin());
  region1.Free();
  region2.Free();
}

TEST(ExecRegionElfBacked, RegionOfDifferentSizes) {
  auto region = ExecRegionElfBackedFactory::Create(ExecRegionElfBackedFactory::kExecRegionSize);
  region.Free();
  // Anything bigger should result it CHECK fail.
  EXPECT_DEATH(
      (void)ExecRegionElfBackedFactory::Create(ExecRegionElfBackedFactory::kExecRegionSize + 1),
      "");
}

}  // namespace

}  // namespace berberis
