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

/*
 * Implementation Notes
 * Platforms must only supply chpp/platform/platform_work_monitor.h if they
 * set the CHPP_ENABLE_WORK_MONITOR macro in their builds.
 */

#ifndef CHPP_WORK_MONITOR_H_
#define CHPP_WORK_MONITOR_H_

#ifdef CHPP_ENABLE_WORK_MONITOR

#ifdef __cplusplus
extern "C" {
#endif

/*
 * Platform-specific work monitor struct, defined in the platform's
 * platform_work_monitor.h file.
 */
struct ChppWorkMonitor;

/*
 * Initializes a specified platform-specific work monitor.
 *
 * @param workMonitor points to the ChppWorkMonitor mutex struct.
 */
static void chppWorkMonitorInit(struct ChppWorkMonitor *workMonitor);

/*
 * Deinitializes a specified platform-specific work monitor.
 *
 * @param workMonitor points to the ChppWorkMonitor mutex struct.
 */
static void chppWorkMonitorDeinit(struct ChppWorkMonitor *workMonitor);

/*
 * Called before CHPP starts doing work on the transport thread. This allows
 * platforms to start something like a watchdog to ensure the CHPP thread
 * is caught if it gets stuck.
 *
 * @param workMonitor points to the ChppWorkMonitor mutex struct.
 */
static void chppWorkMonitorPreProcess(struct ChppWorkMonitor *workMonitor);

/*
 * Called after CHPP finished doing work on the transport thread. This allows
 * platforms to stop any watchdog tracking the thread.
 *
 * @param workMonitor points to the ChppWorkMonitor mutex struct.
 */
static void chppWorkMonitorPostProcess(struct ChppWorkMonitor *workMonitor);

#ifdef __cplusplus
}
#endif

#include "chpp/platform/platform_work_monitor.h"

#endif  // CHPP_ENABLE_WORK_MONITOR
#endif  // CHPP_WORK_MONITOR_H_
