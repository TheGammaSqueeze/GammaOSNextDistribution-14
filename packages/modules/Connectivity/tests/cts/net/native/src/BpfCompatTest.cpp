/*
 * Copyright (C) 2021 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless requied by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */

#define LOG_TAG "BpfCompatTest"

#include <fstream>

#include <gtest/gtest.h>

#include "android-modules-utils/sdk_level.h"

#include "libbpf_android.h"

using namespace android::bpf;

void doBpfStructSizeTest(const char *elfPath) {
  std::ifstream elfFile(elfPath, std::ios::in | std::ios::binary);
  ASSERT_TRUE(elfFile.is_open());

  if (android::modules::sdklevel::IsAtLeastU()) {
    EXPECT_EQ(120, readSectionUint("size_of_bpf_map_def", elfFile, 0));
    EXPECT_EQ(92, readSectionUint("size_of_bpf_prog_def", elfFile, 0));
  } else if (android::modules::sdklevel::IsAtLeastT()) {
    EXPECT_EQ(116, readSectionUint("size_of_bpf_map_def", elfFile, 0));
    EXPECT_EQ(92, readSectionUint("size_of_bpf_prog_def", elfFile, 0));
  } else {
    EXPECT_EQ(48, readSectionUint("size_of_bpf_map_def", elfFile, 0));
    EXPECT_EQ(28, readSectionUint("size_of_bpf_prog_def", elfFile, 0));
  }
}

TEST(BpfTest, bpfStructSizeTestPreT) {
  if (android::modules::sdklevel::IsAtLeastT()) GTEST_SKIP() << "T+ device.";
  doBpfStructSizeTest("/system/etc/bpf/netd.o");
  doBpfStructSizeTest("/system/etc/bpf/clatd.o");
}

TEST(BpfTest, bpfStructSizeTest) {
  if (android::modules::sdklevel::IsAtLeastU()) {
      doBpfStructSizeTest("/system/etc/bpf/gpuMem.o");
      doBpfStructSizeTest("/system/etc/bpf/timeInState.o");
  } else {
      doBpfStructSizeTest("/system/etc/bpf/gpu_mem.o");
      doBpfStructSizeTest("/system/etc/bpf/time_in_state.o");
  }
}

int main(int argc, char **argv) {
  testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
