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

#ifndef CHRE_UTIL_SYSTEM_NAPP_PERMISSONS
#define CHRE_UTIL_SYSTEM_NAPP_PERMISSONS

#include <stdint.h>

namespace chre {

/**
 * Enum declaring the various CHRE permissions that can be declared. Nanoapps
 * built against CHRE API v1.5+ must contain the respective permission for the
 * set of APIs they attempt to call. For example, CHRE_NANOAPP_USES_WIFI must
 * be declared by the nanoapp in order for it to make use of any WiFi APIs.
 *
 * The 8 most-significant bits (MSBs) are reserved for vendor use and must be
 * used if a vendor API allows access to privacy sensitive information that is
 * guarded by a permission on the Android side (e.g. location).
 */
enum NanoappPermissions : uint32_t {
  CHRE_PERMS_NONE = 0,
  CHRE_PERMS_AUDIO = 1,
  CHRE_PERMS_GNSS = 1 << 1,
  CHRE_PERMS_WIFI = 1 << 2,
  CHRE_PERMS_WWAN = 1 << 3,
  CHRE_PERMS_BLE = 1 << 4,
  CHRE_PERMS_ALL = 0xffffffff,
};

}  // namespace chre

#endif  // CHRE_UTIL_SYSTEM_NAPP_PERMISSONS
