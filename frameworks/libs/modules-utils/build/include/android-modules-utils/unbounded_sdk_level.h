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

#pragma once

#include <assert.h>
#include <ctype.h>
#include <limits.h>
#include <stdlib.h>
#include <string.h>

#include <android/api-level.h>
#include <log/log.h>
#include <sys/system_properties.h>

#include "sdk_level.h"

namespace android {
namespace modules {
namespace sdklevel {
namespace unbounded {

inline auto getVersionInt(const char *version) {
  LOG_ALWAYS_FATAL_IF(version[0] == '\0', "empty version");
  char *next_char = 0;
  const long versionInt = strtol(version, &next_char, 10);
  LOG_ALWAYS_FATAL_IF(*next_char != '\0', "no conversion from \"%s\" to long",
                      version);
  LOG_ALWAYS_FATAL_IF(versionInt <= 0, "negative version: %s", version);
  LOG_ALWAYS_FATAL_IF(versionInt > INT_MAX, "version too large: %s", version);
  return (int)versionInt;
}

inline bool isCodename(const char *version) {
  LOG_ALWAYS_FATAL_IF(version[0] == '\0', "empty version");
  return isupper(version[0]);
}

struct SdkLevelAndCodenames {
  int sdk_level;
  const char *codenames;
};

static constexpr SdkLevelAndCodenames PREVIOUS_CODENAMES[] = {
    {29, "Q"}, {30, "Q,R"}, {31, "Q,R,S"}, {32, "Q,R,S,Sv2"}};

static char *getPreviousCodenames(int sdk_level) {
  for (size_t i = 0;
       i < sizeof(PREVIOUS_CODENAMES) / sizeof(PREVIOUS_CODENAMES[0]); ++i) {
    if (sdk_level == PREVIOUS_CODENAMES[i].sdk_level) {
      return strdup(PREVIOUS_CODENAMES[i].codenames);
    }
  }
  return strdup("");
}

static char *getKnownCodenames() {
  const prop_info *pi =
      __system_property_find("ro.build.version.known_codenames");
  LOG_ALWAYS_FATAL_IF(pi == nullptr, "known_codenames property doesn't exist");
  char *codenames = nullptr;
  // The length of this property is not limited to PROP_VALUE_MAX; therefore it
  // cannot be requested via __system_property_get
  __system_property_read_callback(
      pi,
      [](void *cookie, const char *, const char *value, unsigned) {
        auto codenames_ptr = static_cast<const char **>(cookie);
        *codenames_ptr = strdup(value);
      },
      &codenames);
  return codenames;
}

// Checks if input version is same/previous codename of one running on device.
static bool isKnownCodename(const char *version) {
  LOG_ALWAYS_FATAL_IF(!isCodename(version), "input version is not a codename");
  char *const known_codenames =
      IsAtLeastT() ? getKnownCodenames()
                   : getPreviousCodenames(android_get_device_api_level());
  LOG_ALWAYS_FATAL_IF(known_codenames == nullptr, "null for known codenames");
  char *p, *saveptr = known_codenames;
  bool found = false;
  // The example of known_codenames is Q,R,S,Sv2 (versions split by ',')
  while (!found && (p = strtok_r(saveptr, ",", &saveptr))) {
    if (strcmp(version, p) == 0) {
      found = true;
    }
  }
  free(known_codenames);
  return found;
}

// Checks if the device is running a specific version or newer.
// Always use specific methods IsAtLeast*() available in sdk_level.h when the
// version is known at build time. This should only be used when a dynamic
// runtime check is needed.
inline bool IsAtLeast(const char *version) {
  char device_codename[PROP_VALUE_MAX];
  detail::GetCodename(device_codename);
  if (!strcmp("REL", device_codename)) {
    if (isCodename(version)) {
      LOG_ALWAYS_FATAL_IF(
          isKnownCodename(version),
          "Artifact with a known codename "
          "%s must be recompiled with a finalized integer version.",
          version);
      return false;
    }
    return android_get_device_api_level() >= getVersionInt(version);
  }
  if (isCodename(version)) {
    return isKnownCodename(version);
  }
  return android_get_device_api_level() >= getVersionInt(version);
}

// Checks if the device is running a specific version or older.
// Always use specific methods IsAtLeast*() available in sdk_level.h when the
// version is known at build time. This should only be used when a dynamic
// runtime check is needed.
inline bool IsAtMost(const char *version) {
  char device_codename[PROP_VALUE_MAX];
  detail::GetCodename(device_codename);
  if (!strcmp("REL", device_codename)) {
    if (isCodename(version)) {
      LOG_ALWAYS_FATAL_IF(
          isKnownCodename(version),
          "Artifact with a known codename "
          "%s must be recompiled with a finalized integer version.",
          version);
      return true;
    }
    return android_get_device_api_level() <= getVersionInt(version);
  }
  if (isCodename(version)) {
    return !isKnownCodename(version) || strcmp(version, device_codename) == 0;
  }
  return android_get_device_api_level() < getVersionInt(version);
}

} // namespace unbounded
} // namespace sdklevel
} // namespace modules
} // namespace android
