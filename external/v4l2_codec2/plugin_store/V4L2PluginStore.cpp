// Copyright 2020 The Chromium Authors. All rights reserved.
// Use of this source code is governed by a BSD-style license that can be
// found in the LICENSE file.

//#define LOG_NDEBUG 0
#define LOG_TAG "V4L2PluginStore"

#include <inttypes.h>

#include <map>
#include <memory>
#include <mutex>

#include <C2AllocatorGralloc.h>
#include <C2BqBufferPriv.h>
#include <C2BufferPriv.h>
#include <log/log.h>

#include <v4l2_codec2/plugin_store/V4L2AllocatorId.h>
#include <v4l2_codec2/plugin_store/VendorAllocatorLoader.h>

namespace android {

static std::mutex sAllocatorLoaderMutex;

// Using optional because in the case of library being no availiable, we do not want to retry its search.
static std::optional<std::unique_ptr<VendorAllocatorLoader>> sAllocatorLoader = std::nullopt;

const std::unique_ptr<VendorAllocatorLoader>& getAllocatorLoader() {
    {
        std::lock_guard<std::mutex> lock(sAllocatorLoaderMutex);

        if (sAllocatorLoader == std::nullopt) {
            sAllocatorLoader = VendorAllocatorLoader::Create();
        }
    }

    return *sAllocatorLoader;
}

C2Allocator* createAllocator(C2Allocator::id_t allocatorId) {
    ALOGV("%s(allocatorId=%d)", __func__, allocatorId);

    auto& allocatorLoader = getAllocatorLoader();
    if (allocatorLoader != nullptr) {
        ALOGD("%s(): Create C2Allocator (id=%u) from VendorAllocatorLoader", __func__, allocatorId);
        return allocatorLoader->createAllocator(allocatorId);
    }

    ALOGI("%s(): Fallback to create C2AllocatorGralloc(id=%u)", __func__, allocatorId);
    return new C2AllocatorGralloc(allocatorId, true);
}

std::shared_ptr<C2Allocator> fetchAllocator(C2Allocator::id_t allocatorId) {
    ALOGV("%s(allocatorId=%d)", __func__, allocatorId);
    static std::mutex sMutex;
    static std::map<C2Allocator::id_t, std::weak_ptr<C2Allocator>> sCacheAllocators;

    std::lock_guard<std::mutex> lock(sMutex);

    std::shared_ptr<C2Allocator> allocator;
    auto iter = sCacheAllocators.find(allocatorId);
    if (iter != sCacheAllocators.end()) {
        allocator = iter->second.lock();
        if (allocator != nullptr) {
            return allocator;
        }
    }

    allocator.reset(createAllocator(allocatorId));
    sCacheAllocators[allocatorId] = allocator;
    return allocator;
}

C2BlockPool* createBlockPool(C2Allocator::id_t allocatorId, C2BlockPool::local_id_t poolId) {
    ALOGV("%s(allocatorId=%d, poolId=%" PRIu64 ")", __func__, allocatorId, poolId);

    auto& allocatorLoader = getAllocatorLoader();
    if (allocatorLoader != nullptr) {
        ALOGD("%s(): Create C2BlockPool (id=%u) from VendorAllocatorLoader", __func__, allocatorId);
        C2BlockPool* pool = allocatorLoader->createBlockPool(allocatorId, poolId);
        ;
        if (pool != nullptr) {
            return pool;
        }
    }

    std::shared_ptr<C2Allocator> allocator = fetchAllocator(allocatorId);
    if (allocator == nullptr) {
        ALOGE("%s(): Failed to create allocator id=%u", __func__, allocatorId);
        return nullptr;
    }

    switch (allocatorId) {
    case V4L2AllocatorId::SECURE_LINEAR:
        return new C2PooledBlockPool(allocator, poolId);

    case V4L2AllocatorId::SECURE_GRAPHIC:
        return new C2BufferQueueBlockPool(allocator, poolId);

    default:
        ALOGE("%s(): Unknown allocator id=%u", __func__, allocatorId);
        return nullptr;
    }
}

}  // namespace android

extern "C" ::C2BlockPool* CreateBlockPool(::C2Allocator::id_t allocatorId,
                                          ::C2BlockPool::local_id_t poolId) {
    ALOGV("%s(allocatorId=%d, poolId=%" PRIu64 ")", __func__, allocatorId, poolId);
    return ::android::createBlockPool(allocatorId, poolId);
}

extern "C" ::C2Allocator* CreateAllocator(::C2Allocator::id_t allocatorId, ::c2_status_t* status) {
    ALOGV("%s(allocatorId=%d)", __func__, allocatorId);

    ::C2Allocator* res = ::android::createAllocator(allocatorId);
    *status = (res != nullptr) ? C2_OK : C2_BAD_INDEX;
    return res;
}
