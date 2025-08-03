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

#include "FreeRTOS.h"

#include "chre/target_platform/platform_cache_management.h"
#include "chre/platform/shared/nanoapp_loader.h"

#include "dma_api.h"

namespace chre {

void wipeSystemCaches(uintptr_t address, uint32_t span) {
  if (span == 0) return;

  auto aligned_addr = NanoappLoader::roundDownToAlign(address, CACHE_LINE_SIZE);
  auto aligned_span = (span + CACHE_LINE_SIZE - 1) & ~(CACHE_LINE_SIZE - 1);
  LOGV("Invalidate cache at 0x%lx for %u", aligned_addr, aligned_span);

  // Flush D cache first for updating binary to heap memory
  mrv_dcache_flush_multi_addr(aligned_addr, aligned_span);
  // Invalid I cache for fetch instructions
  mrv_icache_invalid_multi_addr(aligned_addr, aligned_span);
}

}  // namespace chre