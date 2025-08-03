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

#include <android/uprobestats_client.h>

#include <android-base/file.h>
#include <android-base/properties.h>
#include <private/android_filesystem_config.h>


void AUprobestatsClient_startUprobestats(const uint8_t* config, int64_t size) {
    const char* filename = "/data/misc/uprobestats-configs/config";
    android::base::WriteStringToFile(
            std::string(reinterpret_cast<const char*>(config), size), filename);
    chmod(filename, S_IRUSR | S_IWUSR | S_IRGRP | S_IROTH);
    android::base::SetProperty("uprobestats.start_with_config", "config");
}
