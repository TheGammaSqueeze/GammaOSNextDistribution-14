/*
 * Copyright (C) 2013 The Android Open Source Project
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
#define LOG_TAG "memtrack"

#include <aidl/android/hardware/memtrack/DeviceInfo.h>
#include <aidl/android/hardware/memtrack/IMemtrack.h>
#include <aidl/android/hardware/memtrack/MemtrackRecord.h>
#include <aidl/android/hardware/memtrack/MemtrackType.h>

#include <android/binder_manager.h>
#include <memtrack/memtrack.h>

#include <errno.h>
#include <malloc.h>
#include <string.h>
#include <mutex>
#include <vector>

#include <log/log.h>

using aidl::android::hardware::memtrack::DeviceInfo;
using aidl::android::hardware::memtrack::IMemtrack;
using aidl::android::hardware::memtrack::MemtrackRecord;
using aidl::android::hardware::memtrack::MemtrackType;

const std::vector<MemtrackType> kMemtrackTypes{ndk::enum_range<MemtrackType>().begin(),
                                               ndk::enum_range<MemtrackType>().end()};

struct memtrack_proc_type {
    MemtrackType type;
    std::vector<MemtrackRecord> records;
};

struct memtrack_proc {
    pid_t pid;
    std::vector<memtrack_proc_type> types;

    memtrack_proc() {
        types.resize(kMemtrackTypes.size());
    }
};

static std::shared_ptr<IMemtrack> get_memtrack_proxy_service() {
    const char* kMemtrackProxyService = "memtrack.proxy";
    static std::shared_ptr<IMemtrack> memtrack_proxy_service = nullptr;
    if (!memtrack_proxy_service &&
        !(memtrack_proxy_service = IMemtrack::fromBinder(
                  ndk::SpAIBinder(AServiceManager_checkService(kMemtrackProxyService))))) {
        ALOGE("Unable to connect to %s\n", kMemtrackProxyService);
    }
    return memtrack_proxy_service;
}

bool memtrack_gpu_device_info(std::vector<DeviceInfo>* device_info) {
    std::shared_ptr<IMemtrack> memtrack_proxy_service = get_memtrack_proxy_service();
    if (!memtrack_proxy_service) {
        return false;
    }

    auto status = memtrack_proxy_service->getGpuDeviceInfo(device_info);
    return status.isOk();
}

memtrack_proc *memtrack_proc_new(void)
{
    return new memtrack_proc();
}

void memtrack_proc_destroy(memtrack_proc *p)
{
    delete(p);
}

static int memtrack_proc_get_type(memtrack_proc_type *t,
        pid_t pid, MemtrackType type)
{
    int err = 0;

    std::shared_ptr<IMemtrack> memtrack_proxy_service = get_memtrack_proxy_service();
    if (!memtrack_proxy_service) {
        return -1;
    }

    std::vector<MemtrackRecord> records;
    auto status = memtrack_proxy_service->getMemory(pid, type, &records);

    if (!status.isOk()) {
        return -1;
    }

    t->records.resize(records.size());
    for (size_t i = 0; i < records.size(); i++) {
        t->records[i].sizeInBytes = records[i].sizeInBytes;
        t->records[i].flags = records[i].flags;
    }

    return err;
}

/* TODO: sanity checks on return values from HALs:
 *   make sure no records have invalid flags set
 *    - unknown flags
 *    - too many flags of a single category
 *    - missing ACCOUNTED/UNACCOUNTED
 *   make sure there are not overlapping SHARED and SHARED_PSS records
 */
static int memtrack_proc_sanity_check(memtrack_proc* /*p*/)
{
    return 0;
}

int memtrack_proc_get(memtrack_proc *p, pid_t pid)
{
    if (!p) {
        return -EINVAL;
    }

    p->pid = pid;
    for (int i = 0; i < kMemtrackTypes.size(); i++) {
        int ret = memtrack_proc_get_type(&p->types[i], pid, kMemtrackTypes[i]);
        if (ret != 0)
           return ret;
    }

    return memtrack_proc_sanity_check(p);
}

static ssize_t memtrack_proc_sum(memtrack_proc *p,
        const std::vector<MemtrackType>& types, uint32_t flags)
{
    ssize_t sum = 0;

    for (size_t i = 0; i < types.size(); i++) {
        memtrack_proc_type type = p->types[static_cast<int>(types[i])];
        std::vector<MemtrackRecord> records = type.records;
        for (size_t j = 0; j < records.size(); j++) {
            if ((records[j].flags & flags) == flags) {
                sum += records[j].sizeInBytes;
            }
        }
    }

    return sum;
}

ssize_t memtrack_proc_graphics_total(memtrack_proc *p)
{
    std::vector<MemtrackType> types = {MemtrackType::GRAPHICS};
    return memtrack_proc_sum(p, types, 0);
}

ssize_t memtrack_proc_graphics_pss(memtrack_proc *p)
{
    std::vector<MemtrackType> types = { MemtrackType::GRAPHICS };
    return memtrack_proc_sum(p, types,
            (uint32_t)MemtrackRecord::FLAG_SMAPS_UNACCOUNTED);
}

ssize_t memtrack_proc_gl_total(memtrack_proc *p)
{
    std::vector<MemtrackType> types = { MemtrackType::GL };
    return memtrack_proc_sum(p, types, 0);
}

ssize_t memtrack_proc_gl_pss(memtrack_proc *p)
{
    std::vector<MemtrackType> types = { MemtrackType::GL };
    return memtrack_proc_sum(p, types,
            (uint32_t)MemtrackRecord::FLAG_SMAPS_UNACCOUNTED);
}

ssize_t memtrack_proc_other_total(memtrack_proc *p)
{
    std::vector<MemtrackType> types = { MemtrackType::MULTIMEDIA,
            MemtrackType::CAMERA, MemtrackType::OTHER };
    return memtrack_proc_sum(p, types, 0);
}

ssize_t memtrack_proc_other_pss(memtrack_proc *p)
{
    std::vector<MemtrackType> types = { MemtrackType::MULTIMEDIA,
            MemtrackType::CAMERA, MemtrackType::OTHER };
    return memtrack_proc_sum(p, types,
            (uint32_t)MemtrackRecord::FLAG_SMAPS_UNACCOUNTED);
}
