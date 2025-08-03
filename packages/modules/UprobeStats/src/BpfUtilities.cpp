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

#define LOG_TAG "uprobestats"

#include <BpfSyscallWrappers.h>
#include <android-base/file.h>
#include <android-base/logging.h>
#include <linux/perf_event.h>

#include <string>

#include "bpf/BpfRingbuf.h"

namespace android {
namespace uprobestats {

const char *PMU_TYPE_FILE = "/sys/bus/event_source/devices/uprobe/type";

int bpfPerfEventOpen(const char* filename, int offset, int pid, const char* bpfProgramPath) {
    android::base::unique_fd bpfProgramFd(android::bpf::retrieveProgram(bpfProgramPath));
    if (bpfProgramFd < 0) {
        LOG(ERROR) << "retrieveProgram failed";
        return -1;
    }

    std::string typeStr;
    if (!android::base::ReadFileToString(PMU_TYPE_FILE, &typeStr)) {
        LOG(ERROR) << "Failed to open pmu type file";
        return -1;
    }
    int pmu_type = (int) strtol(typeStr.c_str(), NULL, 10);

    struct perf_event_attr attr = {};
    attr.sample_period = 1;
    attr.wakeup_events = 1;
    attr.config2 = offset;
    attr.size = sizeof(attr);
    attr.type = pmu_type;
    attr.config1 = android::bpf::ptr_to_u64((void *)filename);
    attr.exclude_kernel = true;

    int perfEventFd = syscall(__NR_perf_event_open, &attr, pid, /*cpu=*/ -1, /* group_fd=*/ -1,
                              PERF_FLAG_FD_CLOEXEC);
    if (perfEventFd < 0) {
        LOG(ERROR) << "syscall(__NR_perf_event_open) failed. "
                   << "perfEventFd: " << perfEventFd << " "
                   << "error: " << strerror(errno);
        return -1;
    }
    if (ioctl(perfEventFd, PERF_EVENT_IOC_SET_BPF, int(bpfProgramFd)) < 0) {
        LOG(ERROR) << "PERF_EVENT_IOC_SET_BPF failed. " << strerror(errno);
        return -1;
    }
    if (ioctl(perfEventFd, PERF_EVENT_IOC_ENABLE, 0) < 0) {
        LOG(ERROR) << "PERF_EVENT_IOC_ENABLE failed. " << strerror(errno);
        return -1;
    }
    return 0;
}

void printRingBuf(const char* map_path) {
    auto result = android::bpf::BpfRingbuf<uint64_t>::Create(map_path);
    auto callback = [&](const uint64_t& value) {
        LOG(INFO) << "ringbuf result callback. value: " << value;
    };
    int num_consumed = result.value()->ConsumeAll(callback).value_or(-1);
    LOG(INFO) << "ring buffer size: " << num_consumed;
}

} // namespace uprobestats
} // namespace android
