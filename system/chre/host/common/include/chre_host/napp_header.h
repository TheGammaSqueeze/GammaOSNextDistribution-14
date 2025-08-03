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

#ifndef CHRE_HOST_NAPP_HEADER_H_
#define CHRE_HOST_NAPP_HEADER_H_

namespace android {
namespace chre {

// This struct comes from build/build_template.mk and must not be modified.
// Refer to that file for more details.
struct NanoAppBinaryHeader {
  uint32_t headerVersion;
  uint32_t magic;
  uint64_t appId;
  uint32_t appVersion;
  uint32_t flags;
  uint64_t hwHubType;
  uint8_t targetChreApiMajorVersion;
  uint8_t targetChreApiMinorVersion;
  uint8_t reserved[6];
} __attribute__((packed));

}  // namespace chre
}  // namespace android

#endif  // CHRE_HOST_NAPP_HEADER_H_
