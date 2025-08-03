/*
 * Copyright (C) 2021 The Android Open Source Project
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

#include <aidlcommonsupport/NativeHandle.h>

using AidlNativeHandle = aidl::android::hardware::common::NativeHandle;

namespace aidl::android::hardware::graphics::composer3::impl {

/// Some IResourceManager functions return a replaced buffer and that buffer should be
// released later (at the time of IBufferReleaser object destruction)
class IBufferReleaser {
 public:
    virtual ~IBufferReleaser() = default;
};

class IResourceManager {
public:
    static std::unique_ptr<IResourceManager> create();
    using RemoveDisplay = std::function<void(int64_t display, bool isVirtual,
                                             const std::vector<int64_t>& layers)>;
    virtual ~IResourceManager() = default;
    virtual std::unique_ptr<IBufferReleaser> createReleaser(bool isBuffer) = 0;

    virtual void clear(RemoveDisplay removeDisplay) = 0;
    virtual bool hasDisplay(int64_t display) = 0;
    virtual int32_t addPhysicalDisplay(int64_t display) = 0;
    virtual int32_t addVirtualDisplay(int64_t display, uint32_t outputBufferCacheSize) = 0;
    virtual int32_t removeDisplay(int64_t display) = 0;
    virtual int32_t setDisplayClientTargetCacheSize(int64_t display,
                                                    uint32_t clientTargetCacheSize) = 0;
    virtual int32_t getDisplayClientTargetCacheSize(int64_t display, size_t* outCacheSize) = 0;
    virtual int32_t getDisplayOutputBufferCacheSize(int64_t display, size_t* outCacheSize) = 0;
    virtual int32_t addLayer(int64_t display, int64_t layer, uint32_t bufferCacheSize) = 0;
    virtual int32_t removeLayer(int64_t display, int64_t layer) = 0;
    virtual void setDisplayMustValidateState(int64_t display, bool mustValidate) = 0;
    virtual bool mustValidateDisplay(int64_t display) = 0;
    virtual int32_t getDisplayReadbackBuffer(int64_t display, const buffer_handle_t handle,
                                             buffer_handle_t& outHandle,
                                             IBufferReleaser* bufReleaser) = 0;
    virtual int32_t getDisplayClientTarget(int64_t display, uint32_t slot, bool fromCache,
                                           const buffer_handle_t handle,
                                           buffer_handle_t& outHandle,
                                           IBufferReleaser* bufReleaser) = 0;
    virtual int32_t getDisplayOutputBuffer(int64_t display, uint32_t slot, bool fromCache,
                                           const buffer_handle_t handle,
                                           buffer_handle_t& outHandle,
                                           IBufferReleaser* bufReleaser) = 0;
    virtual int32_t getLayerBuffer(int64_t display, int64_t layer, uint32_t slot,
                                   bool fromCache,
                                   const buffer_handle_t rawHandle,
                                   buffer_handle_t& outBufferHandle,
                                   IBufferReleaser* bufReleaser) = 0;
    virtual int32_t getLayerSidebandStream(int64_t display, int64_t layer,
                                           const buffer_handle_t rawHandle,
                                           buffer_handle_t& outStreamHandle,
                                           IBufferReleaser* bufReleaser) = 0;
};

} // namespace aidl::android::hardware::graphics::composer3::impl
