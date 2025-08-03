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

#ifndef AUDIO_CONTROL_UTILS
#define AUDIO_CONTROL_UTILS

#include <stdbool.h>

#ifdef __cplusplus
extern "C" {
#endif
// Sets the device address specified to ducked state
// device_address is the device address to set ducked
// is_ducked is the new ducked state
//
// Note: The device address must be defined in
// audio_policy_configuration.xml
void set_device_address_is_ducked(const char *device_address, bool is_ducked);


// Sets the device address specified to muted state
// device_address is the device address to set muted
// is_muted is the new mute state
//
// Note: The device address must be defined in
// audio_policy_configuration.xml
void set_device_address_is_muted(const char *device_address, bool is_muted);

#ifdef __cplusplus
}
#endif // __cplusplus
#endif // AUDIO_CONTROL_UTILS