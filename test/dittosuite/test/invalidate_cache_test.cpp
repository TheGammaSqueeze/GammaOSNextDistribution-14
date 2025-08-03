// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include "include/instruction_test.h"

#include <ditto/invalidate_cache.h>
#include <ditto/syscall.h>

using dittosuite::Instruction;
using ::testing::_;

class InvalidateCacheTest : public InstructionTest {};

TEST_F(InvalidateCacheTest, InvalidatedCache) {
  EXPECT_CALL(syscall_, Sync());
  EXPECT_CALL(syscall_, Open("/proc/sys/vm/drop_caches", O_WRONLY, 0));
  EXPECT_CALL(syscall_, Write(_, _, sizeof(char), 0));
  EXPECT_CALL(syscall_, Close(_));

  dittosuite::InvalidateCache instruction((Instruction::Params){syscall_, 1});
  instruction.Run();
}
