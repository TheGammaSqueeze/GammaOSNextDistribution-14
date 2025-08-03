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

#ifndef BERBERIS_HEAVY_OPTIMIZER_RISCV64_HEAVY_OPTIMIZE_REGION_H_
#define BERBERIS_HEAVY_OPTIMIZER_RISCV64_HEAVY_OPTIMIZE_REGION_H_

#include <cstdint>
#include <tuple>

#include "berberis/assembler/machine_code.h"
#include "berberis/guest_state/guest_addr.h"

namespace berberis {

struct HeavyOptimizeParams {
  // Generally we don't expect too long regions, since we break at unconditional branches, including
  // function calls and returns. But some applications end up having more than 1000 insns in region
  // (b/197703128), which results in huge memory consumption by translator's data structures
  // (specifically by LivenessAnalyzer). Regions longer than 200 are quite rare and there is a lot
  // of room for optimzations within this range. Thus this limitation has very little to no impact
  // on the generated code quality.
  size_t max_number_of_instructions = 200;
};

std::tuple<GuestAddr, bool, size_t> HeavyOptimizeRegion(
    GuestAddr pc,
    MachineCode* machine_code,
    const HeavyOptimizeParams& params = HeavyOptimizeParams());

}  // namespace berberis

#endif /* BERBERIS_HEAVY_OPTIMIZER_RISCV64_HEAVY_OPTIMIZE_REGION_H_ */
