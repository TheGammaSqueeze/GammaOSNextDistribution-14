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
#pragma once

#include <android/hidl/memory/1.0/IMemory.h>
#include <hidl/Status.h>
#include <inttypes.h>
#include <log/log.h>
#include <sys/mman.h>

namespace android {
namespace hardware {
namespace impl {

using ::android::sp;
using ::android::hardware::hidl_memory;
using ::android::hardware::Return;
using ::android::hardware::Void;
using ::android::hidl::memory::V1_0::IMemory;

struct AshmemMemory : public IMemory {
    AshmemMemory(const hidl_memory& memory, void* data) : mMemory(memory), mData(data) {}

    ~AshmemMemory() { munmap(mData, mMemory.size()); }

    // Methods from ::android::hidl::memory::V1_0::IMemory follow.
    Return<void*> getPointer() override { return mData; }
    Return<uint64_t> getSize() override { return mMemory.size(); }
    // NOOPs (since non0remoted memory)
    Return<void> update() override { return Void(); }
    Return<void> updateRange(uint64_t /*start*/, uint64_t /*length*/) override { return Void(); }
    Return<void> read() override { return Void(); }
    Return<void> readRange(uint64_t /*start*/, uint64_t /*length*/) override { return Void(); }
    Return<void> commit() override { return Void(); }

  private:
    // Holding onto hidl_memory reference because it contains
    // handle and size, and handle will also be required for
    // the remoted case.
    hidl_memory mMemory;

    // Mapped memory in process.
    void* mData;
};

}  // namespace impl
}  // namespace hardware
}  // namespace android
