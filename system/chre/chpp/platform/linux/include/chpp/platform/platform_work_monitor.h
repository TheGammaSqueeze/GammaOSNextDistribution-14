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

#ifndef CHPP_PLATFORM_WORK_MONITOR_H_
#define CHPP_PLATFORM_WORK_MONITOR_H_

#ifdef __cplusplus
extern "C" {
#endif

struct ChppWorkMonitor {
  uint32_t numPreProcessCalls;
  uint32_t numPostProcessCalls;
};

static inline void chppWorkMonitorInit(struct ChppWorkMonitor *workMonitor) {
  workMonitor->numPreProcessCalls = 0;
  workMonitor->numPostProcessCalls = 0;
}

static inline void chppWorkMonitorDeinit(struct ChppWorkMonitor *workMonitor) {
  UNUSED_VAR(workMonitor);
}

static inline void chppWorkMonitorPreProcess(
    struct ChppWorkMonitor *workMonitor) {
  ++workMonitor->numPreProcessCalls;
}

static inline void chppWorkMonitorPostProcess(
    struct ChppWorkMonitor *workMonitor) {
  ++workMonitor->numPostProcessCalls;
}

#ifdef __cplusplus
}
#endif

#endif  // CHPP_PLATFORM_WORK_MONITOR_H_
