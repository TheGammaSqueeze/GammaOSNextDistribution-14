/*
 * Copyright (C) 2020 The Android Open Source Project
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

#ifndef CHRE_PLATFORM_FREERTOS_INIT_H_
#define CHRE_PLATFORM_FREERTOS_INIT_H_

#include "FreeRTOS.h"

namespace chre {

/** @return the CHRE task priority being used. */
BaseType_t getChreTaskPriority();

namespace freertos {

/**
 * This init function spawns a (non-privileged) FreeRTOS task that
 * initializes the CHRE core, loads any static nanoapps, and starts
 * the CHRE event loop.
 * The task attribute constants are located in the corresponding init.cc
 * source file, in case they need to be altered. The defaults chosen are:
 * - Task Stack Depth: 8K Words
 * - Task Priority: 1 (idle_task + 1)
 *
 * @return pdPASS on success, a FreeRTOS error code otherwise.
 */
BaseType_t init();

/**
 * This function initializes the and starts the logger so it's ready to accept
 * logs. Spawns a (non-privileged) FreeRTOS task for sending logs to the host.
 *
 * @return pdPASS on success, a FreeRTOS error code otherwise.
 */
BaseType_t initLogger();

/**
 * Delete the task spawned in the init function
 */
void deinit();

const char *getChreTaskName();

}  // namespace freertos
}  // namespace chre

#endif  // CHRE_PLATFORM_FREERTOS_INIT_H_
