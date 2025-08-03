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

#include <string>
#include <android-base/result.h>

namespace android {
  namespace aconfigd {

    /// Aconfigd socket name
    static constexpr char kAconfigdSocket[] = "aconfigd";

    /// Socket message buffer size
    static constexpr size_t kBufferSize = 4096;

    /// Initialize platform RO partition flag storages
    base::Result<void> InitializePlatformStorage();

    /// Handle incoming messages to aconfigd socket
    void HandleSocketRequest(const std::string& msg);

  } // namespace aconfigd
} // namespace android
