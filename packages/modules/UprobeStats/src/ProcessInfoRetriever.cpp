/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <android-base/file.h>
#include <android-base/parseint.h>

#include <string>
#include <filesystem>

#define UPROBETEST_LOG_TAG "uprobestats"

namespace android {
namespace uprobestats {

int getPid(const std::string& processName) {
    for (const auto& entry : std::filesystem::directory_iterator("/proc")) {
        std::string cmdline;
        android::base::ReadFileToString(entry.path() / "cmdline", &cmdline);
        if (android::base::ReadFileToString(entry.path() / "cmdline", &cmdline)
            && (cmdline == processName
                || (cmdline.rfind(processName + '\0', 0) == 0))) {
            std::string pidStr = entry.path().string().substr(entry.path().string().rfind("/") + 1);
            int pid;
            if (!android::base::ParseInt(pidStr, &pid)) {
                return -1;
            }
            return pid;
        }
    }
    return -1;
}

} // namespace uprobestats
} // namespace android
