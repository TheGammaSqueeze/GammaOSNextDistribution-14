/*
 * Copyright (C) 2012 The Android Open Source Project
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
#include "ExynosHWCDebug.h"
#include "ExynosDisplay.h"
#include <sync/sync.h>
#include "exynos_sync.h"

int32_t saveErrorLog(const String8& errString, const ExynosDisplay* display) {
    if (display == nullptr) return -1;
    int32_t ret = NO_ERROR;

    auto &fileWriter = display->mErrLogFileWriter;

    if (!fileWriter.chooseOpenedFile()) {
        return -1;
    }

    String8 saveString;
    struct timeval tv;
    gettimeofday(&tv, NULL);

    saveString.appendFormat("%s errFrameNumber %" PRIu64 ": %s\n", getLocalTimeStr(tv).c_str(),
                            display->mErrorFrameCount, errString.c_str());

    fileWriter.write(saveString);
    fileWriter.flush();
    return ret;
}
