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

#pragma once

#ifdef ANDROID

#include <cutils/properties.h>

#else

#include <cstdio>
#include <cstdlib>
#include <cstring>

// NOLINTNEXTLINE(readability-identifier-naming)
constexpr int PROPERTY_VALUE_MAX = 92;

// NOLINTNEXTLINE(readability-identifier-naming)
auto inline property_get(const char *name, char *value,
                         const char *default_value) -> int {
  // NOLINTNEXTLINE (concurrency-mt-unsafe)
  char *prop = std::getenv(name);
  snprintf(value, PROPERTY_VALUE_MAX, "%s",
           (prop == nullptr) ? default_value : prop);
  return static_cast<int>(strlen(value));
}

#endif
