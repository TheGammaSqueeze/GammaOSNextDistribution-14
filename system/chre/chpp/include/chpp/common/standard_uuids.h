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

#ifndef CHPP_STANDARD_UUIDS_H_
#define CHPP_STANDARD_UUIDS_H_

#ifdef __cplusplus
extern "C" {
#endif

/************************************************
 * Client / Service UUID v4 Definitions
 ***********************************************/

/**
 * Standard WWAN UUID
 */
#define CHPP_UUID_WWAN_STANDARD                                             \
  {                                                                         \
    0x1f, 0x56, 0x3d, 0xf2, 0x5d, 0x07, 0x49, 0x87, 0xba, 0x2b, 0xb3, 0x0e, \
        0xf2, 0x3d, 0x11, 0x28                                              \
  }

/**
 * Standard WiFi UUID
 */
#define CHPP_UUID_WIFI_STANDARD                                             \
  {                                                                         \
    0x24, 0x2c, 0x25, 0xaa, 0xc8, 0x2b, 0x40, 0xf1, 0xb8, 0x42, 0x6b, 0xdc, \
        0x8c, 0x46, 0xa1, 0xb6                                              \
  }

/**
 * Standard GNSS UUID
 */
#define CHPP_UUID_GNSS_STANDARD                                             \
  {                                                                         \
    0x3a, 0x64, 0x11, 0xa2, 0x50, 0x5b, 0x44, 0x42, 0xaf, 0xdb, 0x41, 0x8e, \
        0x3a, 0x74, 0xfa, 0xf4                                              \
  }

#ifdef __cplusplus
}
#endif

#endif  // CHPP_STANDARD_UUIDS_H_
