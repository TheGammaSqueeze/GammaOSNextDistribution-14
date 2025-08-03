/*
 * Copyright (C) 2019 The Android Open Source Project
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

#include <android-base/logging.h>
#include <stdbool.h>

#include <map>
#include <mutex>

#include "palette/palette.h"
#include "palette_system.h"

// Cached thread priority for testing. No thread priorities are ever affected.
static std::mutex g_tid_priority_map_mutex;
static std::map<int32_t, int32_t> g_tid_priority_map;

// Unless explicitly mentioned otherwise, the following methods have been
// introduced in version 1 API, corresponding to SDK level 31.

palette_status_t PaletteSchedSetPriority(int32_t tid, int32_t priority) {
  if (priority < art::palette::kMinManagedThreadPriority ||
      priority > art::palette::kMaxManagedThreadPriority) {
    return PALETTE_STATUS_INVALID_ARGUMENT;
  }
  std::lock_guard guard(g_tid_priority_map_mutex);
  g_tid_priority_map[tid] = priority;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteSchedGetPriority(int32_t tid,
                                         /*out*/int32_t* priority) {
  std::lock_guard guard(g_tid_priority_map_mutex);
  if (g_tid_priority_map.find(tid) == g_tid_priority_map.end()) {
    g_tid_priority_map[tid] = art::palette::kNormalManagedThreadPriority;
  }
  *priority = g_tid_priority_map[tid];
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteWriteCrashThreadStacks(/*in*/ const char* stacks, size_t stacks_len) {
  LOG(INFO) << std::string_view(stacks, stacks_len);
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceEnabled(/*out*/bool* enabled) {
  *enabled = false;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceBegin([[maybe_unused]] const char* name) { return PALETTE_STATUS_OK; }

palette_status_t PaletteTraceEnd() {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteTraceIntegerValue([[maybe_unused]] const char* name,
                                          [[maybe_unused]] int32_t value) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteAshmemCreateRegion([[maybe_unused]] const char* name,
                                           [[maybe_unused]] size_t size,
                                           int* fd) {
  *fd = -1;
  return PALETTE_STATUS_NOT_SUPPORTED;
}

palette_status_t PaletteAshmemSetProtRegion([[maybe_unused]] int fd, [[maybe_unused]] int prot) {
  return PALETTE_STATUS_NOT_SUPPORTED;
}

palette_status_t PaletteCreateOdrefreshStagingDirectory(const char** staging_dir) {
  *staging_dir = nullptr;
  return PALETTE_STATUS_NOT_SUPPORTED;
}

palette_status_t PaletteShouldReportDex2oatCompilation(bool* value) {
  *value = false;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyStartDex2oatCompilation([[maybe_unused]] int source_fd,
                                                      [[maybe_unused]] int art_fd,
                                                      [[maybe_unused]] int oat_fd,
                                                      [[maybe_unused]] int vdex_fd) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyEndDex2oatCompilation([[maybe_unused]] int source_fd,
                                                    [[maybe_unused]] int art_fd,
                                                    [[maybe_unused]] int oat_fd,
                                                    [[maybe_unused]] int vdex_fd) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyDexFileLoaded([[maybe_unused]] const char* path) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyOatFileLoaded([[maybe_unused]] const char* path) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteShouldReportJniInvocations(bool* value) {
  *value = false;
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyBeginJniInvocation([[maybe_unused]] JNIEnv* env) {
  return PALETTE_STATUS_OK;
}

palette_status_t PaletteNotifyEndJniInvocation([[maybe_unused]] JNIEnv* env) {
  return PALETTE_STATUS_OK;
}

// Introduced in version 2 API, corresponding to SDK level 33.
palette_status_t PaletteReportLockContention([[maybe_unused]] JNIEnv* env,
                                             [[maybe_unused]] int32_t wait_ms,
                                             [[maybe_unused]] const char* filename,
                                             [[maybe_unused]] int32_t line_number,
                                             [[maybe_unused]] const char* method_name,
                                             [[maybe_unused]] const char* owner_filename,
                                             [[maybe_unused]] int32_t owner_line_number,
                                             [[maybe_unused]] const char* owner_method_name,
                                             [[maybe_unused]] const char* proc_name,
                                             [[maybe_unused]] const char* thread_name) {
  return PALETTE_STATUS_OK;
}

// Introduced in version 3 API, corresponding to SDK level 34.
palette_status_t PaletteSetTaskProfiles([[maybe_unused]] int32_t tid,
                                        [[maybe_unused]] const char* const profiles[],
                                        [[maybe_unused]] size_t profiles_len) {
  return PALETTE_STATUS_OK;
}
