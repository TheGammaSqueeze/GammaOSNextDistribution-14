/*
 * Copyright (C) 2024 The Android Open Source Project
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

#include <inttypes.h>
#include <sys/cdefs.h>

__BEGIN_DECLS

// Starts uprobestats with the provided config.
//
// The behavior of uprobestats should mostly be controlled by a config from the
// server.  Therefore there is very little API exposed on the client device for
// another client component to control uprobestats.
//
// The only responsibilities of the user of this API are
// 1. Get the config from the server and pass it to uprobestats.
// 2. Decide when to start uprobestats.
//
// Both are accomplished by this API below.
void AUprobestatsClient_startUprobestats(const uint8_t* config, int64_t size)
        __INTRODUCED_IN(__ANDROID_API_V__);

__END_DECLS
