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

#include "gtest/gtest.h"

#include "berberis/runtime/execute_guest.h"

#include <cstdint>

#include "berberis/guest_os_primitives/guest_map_shadow.h"
#include "berberis/guest_os_primitives/guest_thread_manager.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/guest_state/guest_state.h"
#include "berberis/runtime/berberis.h"
#include "berberis/runtime_primitives/translation_cache.h"

namespace berberis {

namespace {

TEST(ExecuteGuestRiscv64, Basic) {
  const uint32_t code[] = {
      0x003100b3,  // add x1, x2, x3
      0x004090b3,  // sll x1, x1, x4
      0x008002ef,  // jal x5, 8
  };

  InitBerberis();

  GuestMapShadow::GetInstance()->SetExecutable(ToGuestAddr(&code[0]), sizeof(code));

  GuestThread* thread = GetCurrentGuestThread();
  auto& cpu_state = thread->state()->cpu;
  cpu_state.insn_addr = ToGuestAddr(&code[0]);
  SetXReg<2>(cpu_state, 10);
  SetXReg<3>(cpu_state, 11);
  SetXReg<4>(cpu_state, 1);
  GuestAddr stop_pc = ToGuestAddr(&code[0]) + 16;
  auto cache = TranslationCache::GetInstance();
  cache->SetStop(stop_pc);
  ExecuteGuest(thread->state());
  cache->TestingClearStop(stop_pc);
  EXPECT_EQ(GetXReg<1>(cpu_state), 42u);

  GuestMapShadow::GetInstance()->ClearExecutable(ToGuestAddr(&code[0]), sizeof(code));
}

}  // namespace

}  // namespace berberis
