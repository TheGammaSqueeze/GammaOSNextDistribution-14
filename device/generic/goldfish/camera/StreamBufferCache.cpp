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

#define FAILURE_DEBUG_PREFIX "StreamBufferCache"

#include "StreamBufferCache.h"
#include "debug.h"

namespace android {
namespace hardware {
namespace camera {
namespace provider {
namespace implementation {

CachedStreamBuffer*
StreamBufferCache::update(const StreamBuffer& sb) {
    const auto bi = mCache.find(sb.bufferId);
    if (bi == mCache.end()) {
        const auto r = mCache.insert({sb.bufferId, CachedStreamBuffer(sb)});
        LOG_ALWAYS_FATAL_IF(!r.second);
        return &(r.first->second);
    } else {
        CachedStreamBuffer* csb = &bi->second;
        csb->importAcquireFence(sb.acquireFence);
        return csb;
    }
}

void StreamBufferCache::remove(const int64_t bufferId) {
    mCache.erase(bufferId);
}

void StreamBufferCache::clearStreamInfo() {
    for (auto& kv : mCache) {
        kv.second.setStreamInfo(nullptr);
    }
}

}  // namespace implementation
}  // namespace provider
}  // namespace camera
}  // namespace hardware
}  // namespace android
