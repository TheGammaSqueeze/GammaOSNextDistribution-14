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

#ifndef BERBERIS_LITE_TRANSLATOR_LITE_TRANSLATE_REGION_H_
#define BERBERIS_LITE_TRANSLATOR_LITE_TRANSLATE_REGION_H_

#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/base/config.h"
#include "berberis/guest_state/guest_addr.h"
#include "berberis/runtime_primitives/host_code.h"
#include "berberis/runtime_primitives/runtime_library.h"

namespace berberis {

struct LiteTranslateParams {
  bool allow_dispatch = true;
  bool enable_reg_mapping = true;
  bool enable_self_profiling = false;
  uint32_t* counter_location = nullptr;
  uint32_t counter_threshold = config::kGearSwitchThreshold;
  HostCode counter_threshold_callback =
      AsHostCode(berberis::berberis_entry_HandleLightCounterThresholdReached);
};

bool LiteTranslateRange(GuestAddr start_pc,
                        GuestAddr end_pc,
                        MachineCode* machine_code,
                        LiteTranslateParams params);
std::tuple<bool, GuestAddr> TryLiteTranslateRegion(GuestAddr start_pc,
                                                   MachineCode* machine_code,
                                                   LiteTranslateParams params);

}  // namespace berberis

#endif  // BERBERIS_LITE_TRANSLATOR_LITE_TRANSLATE_REGION_H_
