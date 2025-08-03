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

#ifndef CHRE_PLATFORM_EMBOS_INIT_H_
#define CHRE_PLATFORM_EMBOS_INIT_H_

#include <stddef.h>

#ifdef __cplusplus
extern "C" {
#endif

/**
 * The init function spawns an EmbOS task that initializes the CHRE core,
 * loads any static nanoapps, and starts the CHRE event loop.
 * Note that this function should be called before starting the EmbOS
 * scheduler via OS_START.
 */
void chreEmbosInit();

/**
 * Stops the CHRE event loop, and cleans up the CHRE EmbOS task.
 */
void chreEmbosDeinit();

const char *getChreTaskName();

size_t getChreTaskNameLen();

#ifdef __cplusplus
}  // extern "C"
#endif

#endif  // CHRE_PLATFORM_EMBOS_INIT_H_
