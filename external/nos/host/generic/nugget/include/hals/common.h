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

#ifdef __cplusplus
extern "C" {
#endif

#include <stdint.h>

#ifndef __packed
#define __packed __attribute__((packed))
#endif

/****************************************************************************/
/**
 * This should be the start of EVERY request and response struct.
 *
 * We don't really need a struct just to hold one integer, but if we need to add
 * to it later, we'll be glad we did.
 */
struct nos2_cmd_hal {
  uint32_t version;
} __packed;
/**
 * IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT
 *
 *   Do *NOT* increment the version number with each new dessert release!
 *
 *   We'll use a (major << 16) | (minor) value for the version. The major
 *   versionn indicates when the command was first supported, and the minor
 *   indicates variations to it since then.
 *
 *   We're currently working on Android 14 (UDC), so start with that. Bump minor
 *   values ONLY if the behavior changes.
 *
 *   By including the version struct in every request and response, we can
 *   support multiple minor HAL changes independently. Add a new version
 *   constant below IF AND ONLY IF a command's struct changes or its behavior is
 *   different. THEN use that version internally to
 *
 *   1. Reject the command if the version is one you don't know about, AND
 *
 *   2. Verify that the incoming struct matches expectations for the versions
 *      you do know about, AND
 *
 *   3. Support as many versions as possible, in case Android is downgraded and
 *      Nugget OS is not (or vice-versa), SO
 *
 *   4) Make sure to indicate the version in the output structs too, in case the
 *      command has no input args but the output later changes.
 *
 * IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT IMPORTANT
 */
#define NOS2_HAL_VERSION_UDC (14U << 16)
/* STOP! Don't just randomly add new values here! Read the comment above! */

/****************************************************************************/
/* Common types */

/* TODO(b/257251378): We'll need some <tag,len,bytes[]> stuff here. */

/****************************************************************************/
#ifdef __cplusplus
}
#endif
