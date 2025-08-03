// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "VendorAllocatorLoader"

#include <v4l2_codec2/plugin_store/VendorAllocatorLoader.h>

#include <dlfcn.h>
#include <cinttypes>

#include <log/log.h>

namespace android {
namespace {
const char* kLibPath = "libv4l2_codec2_vendor_allocator.so";
const char* kCreateAllocatorFuncName = "CreateAllocator";
const char* kCreateBlockPoolFuncName = "CreateBlockPool";
}  // namespace

// static
std::unique_ptr<VendorAllocatorLoader> VendorAllocatorLoader::Create() {
    ALOGV("%s()", __func__);

    void* libHandle = dlopen(kLibPath, RTLD_NOW | RTLD_NODELETE);
    if (!libHandle) {
        ALOGI("%s(): Failed to load library: %s", __func__, kLibPath);
        return nullptr;
    }

    auto createAllocatorFunc = (CreateAllocatorFunc)dlsym(libHandle, kCreateAllocatorFuncName);
    if (!createAllocatorFunc) {
        ALOGW("%s(): Failed to load functions: %s", __func__, kCreateAllocatorFuncName);
    }

    auto crateBlockPoolFunc = (CreateBlockPoolFunc)dlsym(libHandle, kCreateBlockPoolFuncName);
    if (!crateBlockPoolFunc) {
        ALOGW("%s(): Failed to load functions: %s", __func__, kCreateAllocatorFuncName);
    }

    return std::unique_ptr<VendorAllocatorLoader>(
            new VendorAllocatorLoader(libHandle, createAllocatorFunc, crateBlockPoolFunc));
}

VendorAllocatorLoader::VendorAllocatorLoader(void* libHandle,
                                             CreateAllocatorFunc createAllocatorFunc,
                                             CreateBlockPoolFunc createBlockPoolFunc)
      : mLibHandle(libHandle),
        mCreateAllocatorFunc(createAllocatorFunc),
        mCreateBlockPoolFunc(createBlockPoolFunc) {
    ALOGV("%s()", __func__);
}

VendorAllocatorLoader::~VendorAllocatorLoader() {
    ALOGV("%s()", __func__);

    dlclose(mLibHandle);
}

C2Allocator* VendorAllocatorLoader::createAllocator(C2Allocator::id_t allocatorId) {
    ALOGV("%s(%d)", __func__, allocatorId);

    if (!mCreateAllocatorFunc) {
        return nullptr;
    }

    return mCreateAllocatorFunc(allocatorId);
}

C2BlockPool* VendorAllocatorLoader::createBlockPool(C2Allocator::id_t allocatorId,
                                                    C2BlockPool::local_id_t poolId) {
    ALOGV("%s(allocatorId=%d, poolId=%" PRIu64 " )", __func__, allocatorId, poolId);

    if (!mCreateBlockPoolFunc) {
        return nullptr;
    }

    return mCreateBlockPoolFunc(allocatorId, poolId);
}

}  // namespace android
