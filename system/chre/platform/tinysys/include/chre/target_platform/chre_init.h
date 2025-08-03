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

#ifndef CHRE_PLATFORM_TINYSYS_CHRE_INIT_H_
#define CHRE_PLATFORM_TINYSYS_CHRE_INIT_H_

#include "chre/target_platform/init.h"

extern "C" {
/**
 * Initializes CHRE, loads any static nanoapps, and starts the CHRE event loop.
 *
 * The default task attributes are:
 * - Task Stack Depth: 8K, set by macro CHRE_FREERTOS_STACK_DEPTH_IN_WORDS
 * - Task Priority: idle_task + 2, set by macro CHRE_FREERTOS_TASK_PRIORITY
 *
 * @return pdPASS on success, a FreeRTOS error code otherwise.
 */
BaseType_t chreTinysysInit();

/**
 * Deletes the task spawned in chreTinysysInit().
 */
void chreTinysysDeinit();
}

#endif  // CHRE_PLATFORM_TINYSYS_CHRE_INIT_H_