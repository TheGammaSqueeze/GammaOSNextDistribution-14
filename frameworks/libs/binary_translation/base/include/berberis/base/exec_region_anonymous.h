/*
 * Copyright (C) 2021 The Android Open Source Project
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

#ifndef BERBERIS_BASE_EXEC_REGION_ANONYMOUS_H_
#define BERBERIS_BASE_EXEC_REGION_ANONYMOUS_H_

#include <cstddef>
#include <cstdint>

#include "exec_region.h"

namespace berberis {

class ExecRegionAnonymousFactory {
 public:
  // Size of anonymous executable code region.
  static constexpr uint32_t kExecRegionSize = 4 * 1024 * 1024;

  static ExecRegion Create(size_t size);
};

}  // namespace berberis

#endif  // BERBERIS_BASE_EXEC_REGION_ANONYMOUS_H_
