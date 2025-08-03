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

#define ATRACE_TAG (ATRACE_TAG_POWER | ATRACE_TAG_HAL)
#define LOG_TAG "libperfmgr"

#include "perfmgr/AdpfConfig.h"

#include <android-base/file.h>
#include <android-base/logging.h>

#include <sstream>

namespace android {
namespace perfmgr {

int64_t AdpfConfig::getPidIInitDivI() {
    return (mPidI == 0) ? 0 : static_cast<int64_t>(mPidIInit / mPidI);
}
int64_t AdpfConfig::getPidIHighDivI() {
    return (mPidI == 0) ? 0 : static_cast<int64_t>(mPidIHigh / mPidI);
}
int64_t AdpfConfig::getPidILowDivI() {
    return (mPidI == 0) ? 0 : static_cast<int64_t>(mPidILow / mPidI);
}

void AdpfConfig::dumpToFd(int fd) {
    std::ostringstream dump_buf;
    dump_buf << "Name: " << mName << "\n";
    dump_buf << "PID_On: " << mPidOn << "\n";
    dump_buf << "PID_Po: " << mPidPo << "\n";
    dump_buf << "PID_Pu: " << mPidPu << "\n";
    dump_buf << "PID_I: " << mPidI << "\n";
    dump_buf << "PID_I_Init: " << mPidIInit << "\n";
    dump_buf << "PID_I_High: " << mPidIHigh << "\n";
    dump_buf << "PID_I_Low: " << mPidILow << "\n";
    dump_buf << "PID_Do: " << mPidDo << "\n";
    dump_buf << "PID_Du: " << mPidDu << "\n";
    dump_buf << "SamplingWindow_P: " << mSamplingWindowP << "\n";
    dump_buf << "SamplingWindow_I: " << mSamplingWindowI << "\n";
    dump_buf << "SamplingWindow_D: " << mSamplingWindowD << "\n";
    dump_buf << "UclampMin_On: " << mUclampMinOn << "\n";
    dump_buf << "UclampMin_High: " << mUclampMinHigh << "\n";
    dump_buf << "UclampMin_Low: " << mUclampMinLow << "\n";
    dump_buf << "ReportingRateLimitNs: " << mReportingRateLimitNs << "\n";
    dump_buf << "TargetTimeFactor: " << mTargetTimeFactor << "\n";
    dump_buf << "StaleTimeFactor: " << mStaleTimeFactor << "\n";
    if (!android::base::WriteStringToFd(dump_buf.str(), fd)) {
        LOG(ERROR) << "Failed to dump ADPF profile to fd: " << fd;
    }
}

}  // namespace perfmgr
}  // namespace android
