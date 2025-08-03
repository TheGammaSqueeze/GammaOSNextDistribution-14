/*
 * Copyright (C) 2016 The Android Open Source Project
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
#define LOG_TAG "libhidlmemory"

#include <map>
#include <mutex>
#include <string>

#include <AshmemMemory.h>
#include <hidlmemory/mapping.h>

#include <android-base/logging.h>
#include <android/hidl/memory/1.0/IMapper.h>
#include <hidl/HidlSupport.h>
#include <log/log.h>

using android::sp;
using android::hidl::memory::V1_0::IMemory;
using android::hidl::memory::V1_0::IMapper;

namespace android {
namespace hardware {

static std::map<std::string, sp<IMapper>> gMappersByName;
static std::mutex gMutex;
static std::once_flag gOnceFlagLog;

static sp<IMemory> createAshmemMemory(const hidl_memory& mem) {
    if (mem.handle()->numFds == 0) {
        return nullptr;
    }

    // If ashmem service runs in 32-bit (size_t is uint32_t) and a 64-bit
    // client process requests a memory > 2^32 bytes, the size would be
    // converted to a 32-bit number in mmap. mmap could succeed but the
    // mapped memory's actual size would be smaller than the reported size.
    if (mem.size() > SIZE_MAX) {
        ALOGE("Cannot map %" PRIu64 " bytes of memory because it is too large.", mem.size());
        android_errorWriteLog(0x534e4554, "79376389");
        return nullptr;
    }

    int fd = mem.handle()->data[0];
    void* data = mmap(0, mem.size(), PROT_READ | PROT_WRITE, MAP_SHARED, fd, 0);
    if (data == MAP_FAILED) {
        // mmap never maps at address zero without MAP_FIXED, so we can avoid
        // exposing clients to MAP_FAILED.
        return nullptr;
    }

    return new impl::AshmemMemory(mem, data);
}

static inline sp<IMapper> getMapperService(const std::string& name) {
    std::unique_lock<std::mutex> _lock(gMutex);
    auto iter = gMappersByName.find(name);
    if (iter != gMappersByName.end()) {
        return iter->second;
    }

    sp<IMapper> mapper = IMapper::getService(name, true /* getStub */);
    if (mapper != nullptr) {
        gMappersByName[name] = mapper;
    }
    return mapper;
}

sp<IMemory> mapMemory(const hidl_memory& memory) {

    sp<IMapper> mapper = getMapperService(memory.name());

    if (mapper == nullptr) {
        if (memory.name() == "ashmem") {
            std::call_once(gOnceFlagLog,
                           [&]() { LOG(INFO) << "Using libhidlmemory mapper for ashmem."; });
            return createAshmemMemory(memory);
        } else {
            LOG(ERROR) << "Could not fetch mapper for " << memory.name() << " shared memory";
            return nullptr;
        }
    }

    if (mapper->isRemote()) {
        LOG(ERROR) << "IMapper must be a passthrough service.";
        return nullptr;
    }

    // hidl_memory's size is stored in uint64_t, but mapMemory's mmap will map
    // size in size_t. If size is over SIZE_MAX, mapMemory could succeed
    // but the mapped memory's actual size will be smaller than the reported size.
    if (memory.size() > SIZE_MAX) {
        LOG(ERROR) << "Cannot map " << memory.size() << " bytes of memory because it is too large.";
        android_errorWriteLog(0x534e4554, "79376389");
        return nullptr;
    }

    Return<sp<IMemory>> ret = mapper->mapMemory(memory);

    if (!ret.isOk()) {
        LOG(ERROR) << "hidl_memory map returned transport error.";
        return nullptr;
    }

    return ret;
}

}  // namespace hardware
}  // namespace android
