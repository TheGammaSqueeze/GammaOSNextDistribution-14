/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "gmock/gmock.h"
#include "gtest/gtest.h"

#include <string_view>

#include "berberis/runtime_primitives/code_pool.h"

namespace berberis {

class MockExecRegionFactory {
 public:
  static void SetImpl(MockExecRegionFactory* impl) { impl_ = impl; }

  static const uint32_t kExecRegionSize;

  // Gmock is not able to mock static methods so we call *Impl counterpart
  // and mock it instead.
  static ExecRegion Create(size_t size) { return impl_->CreateImpl(size); }

  MOCK_METHOD(ExecRegion, CreateImpl, (size_t));

 private:
  static MockExecRegionFactory* impl_;
};

const uint32_t MockExecRegionFactory::kExecRegionSize = sysconf(_SC_PAGESIZE);
MockExecRegionFactory* MockExecRegionFactory::impl_ = nullptr;

namespace {

uint8_t* AllocWritableRegion() {
  return reinterpret_cast<uint8_t*>(MmapImplOrDie({
      .size = MockExecRegionFactory::kExecRegionSize,
      .prot = PROT_READ | PROT_WRITE,
      .flags = MAP_PRIVATE | MAP_ANONYMOUS,
  }));
}

uint8_t* AllocExecutableRegion() {
  return reinterpret_cast<uint8_t*>(MmapImplOrDie({
      .size = MockExecRegionFactory::kExecRegionSize,
      .prot = PROT_NONE,
      .flags = MAP_PRIVATE | MAP_ANONYMOUS,
  }));
}

TEST(CodePool, Smoke) {
  MockExecRegionFactory exec_region_factory_mock;
  MockExecRegionFactory::SetImpl(&exec_region_factory_mock);
  auto* first_exec_region_memory_write = AllocWritableRegion();
  auto* first_exec_region_memory_exec = AllocExecutableRegion();
  auto* second_exec_region_memory_write = AllocWritableRegion();
  auto* second_exec_region_memory_exec = AllocExecutableRegion();

  EXPECT_CALL(exec_region_factory_mock, CreateImpl(MockExecRegionFactory::kExecRegionSize))
      .WillOnce([&](size_t) {
        return ExecRegion{first_exec_region_memory_exec,
                          first_exec_region_memory_write,
                          MockExecRegionFactory::kExecRegionSize};
      })
      .WillOnce([&](size_t) {
        return ExecRegion{second_exec_region_memory_exec,
                          second_exec_region_memory_write,
                          MockExecRegionFactory::kExecRegionSize};
      });

  CodePool<MockExecRegionFactory> code_pool;
  {
    MachineCode machine_code;
    constexpr std::string_view kCode = "test1";
    machine_code.AddSequence(kCode.data(), kCode.size());
    auto host_code = code_pool.Add(&machine_code);
    ASSERT_EQ(host_code, first_exec_region_memory_exec);
    EXPECT_EQ(std::string_view{reinterpret_cast<const char*>(first_exec_region_memory_write)},
              kCode);
  }

  code_pool.ResetExecRegion();

  {
    MachineCode machine_code;
    constexpr std::string_view kCode = "test2";
    machine_code.AddSequence(kCode.data(), kCode.size());
    auto host_code = code_pool.Add(&machine_code);
    ASSERT_EQ(host_code, second_exec_region_memory_exec);
    EXPECT_EQ(std::string_view{reinterpret_cast<const char*>(second_exec_region_memory_write)},
              kCode);
  }
}

TEST(DataPool, Smoke) {
  DataPool data_pool;
  static uint32_t kConst1 = 0x12345678;
  static uint32_t kConst2 = 0x87654321;
  uint32_t kVar = kConst2;
  uint32_t* ptr = data_pool.Add(kVar);
  EXPECT_EQ(kConst2, *ptr);
  *ptr = kConst1;
  EXPECT_EQ(kConst1, *ptr);
}

}  // namespace

}  // namespace berberis
