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

#ifndef CHRE_PLATFORM_ZEPHYR_INIT_H_
#define CHRE_PLATFORM_ZEPHYR_INIT_H_

#include <zephyr/kernel.h>

namespace chre {

constexpr inline int getChreTaskPriority() {
  return CONFIG_CHRE_TASK_PRIORITY;
}

namespace zephyr {

int init();
void deinit();
k_tid_t getChreTaskId();

}  // namespace chre::zephyr

}  // namespace chre

#endif  // CHRE_PLATFORM_ZEPHYR_INIT_H_
