/*
 * Copyright 2019 The Android Open Source Project
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

#pragma once

#include <cstdint>
#include <functional>

#define LOG_DUMPSYS(fd, fmt, args...)                 \
  do {                                                \
    dprintf(fd, "%s " fmt "\n", DUMPSYS_TAG, ##args); \
  } while (false)

#define LOG_DUMPSYS_TITLE(fd, title)         \
  do {                                       \
    dprintf(fd, " ----- %s -----\n", title); \
  } while (false)

inline double ticks_to_seconds(uint16_t ticks) {
  return (static_cast<double>(ticks) * 0.625 * 0.001);
}

inline double ticks_to_milliseconds(uint16_t ticks) {
  return (static_cast<double>(ticks) * 0.625);
}

inline double supervision_timeout_to_seconds(uint16_t timeout) {
  return (static_cast<double>(timeout) * 0.01);
}

namespace bluetooth {
namespace shim {

using DumpsysFunction = std::function<void(int fd)>;

/**
 * Entrypoint from legacy stack to provide dumpsys functionality
 * for both the legacy shim and the Gabeldorsche stack.
 */
void Dump(int fd, const char** args);

/**
 * Dumpsys access for legacy shim modules.
 */
void RegisterDumpsysFunction(const void* token, DumpsysFunction func);
void UnregisterDumpsysFunction(const void* token);

}  // namespace shim
}  // namespace bluetooth
