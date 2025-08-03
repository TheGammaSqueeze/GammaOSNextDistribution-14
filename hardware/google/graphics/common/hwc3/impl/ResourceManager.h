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

#include <composer-resources/2.2/ComposerResources.h>

#include "include/IResourceManager.h"

using android::hardware::graphics::composer::V2_2::hal::ComposerResources;

namespace aidl::android::hardware::graphics::composer3::impl {

// Wrapper of ComposerResources before there is an aidl version
class BufferReleaser : public IBufferReleaser {
  public:
    BufferReleaser(bool isBuffer) : mReplacedHandle(isBuffer) {}
    virtual ~BufferReleaser() = default;

    ComposerResources::ReplacedHandle* getReplacedHandle() { return &mReplacedHandle; }

  private:
    // ReplacedHandle releases buffer at its destruction.
    ComposerResources::ReplacedHandle mReplacedHandle;
};

class ResourceManager : public IResourceManager {
  public:
    virtual ~ResourceManager() = default;

    std::unique_ptr<IBufferReleaser> createReleaser(bool isBuffer) override;
    void clear(RemoveDisplay removeDisplay) override;
    bool hasDisplay(int64_t display) override;
    int32_t addPhysicalDisplay(int64_t display) override;
    int32_t addVirtualDisplay(int64_t display, uint32_t outputBufferCacheSize) override;
    int32_t removeDisplay(int64_t display) override;
    int32_t setDisplayClientTargetCacheSize(int64_t display,
                                            uint32_t clientTargetCacheSize) override;
    int32_t getDisplayClientTargetCacheSize(int64_t display, size_t* outCacheSize) override;
    int32_t getDisplayOutputBufferCacheSize(int64_t display, size_t* outCacheSize) override;
    int32_t addLayer(int64_t display, int64_t layer, uint32_t bufferCacheSize) override;
    int32_t removeLayer(int64_t display, int64_t layer) override;
    void setDisplayMustValidateState(int64_t display, bool mustValidate) override;
    bool mustValidateDisplay(int64_t display) override;
    int32_t getDisplayReadbackBuffer(int64_t display, const buffer_handle_t handle,
                                     buffer_handle_t& outHandle,
                                     IBufferReleaser* bufReleaser) override;
    int32_t getDisplayClientTarget(int64_t display, uint32_t slot, bool fromCache,
                                   const buffer_handle_t handle, buffer_handle_t& outHandle,
                                   IBufferReleaser* bufReleaser) override;
    int32_t getDisplayOutputBuffer(int64_t display, uint32_t slot, bool fromCache,
                                   const buffer_handle_t handle, buffer_handle_t& outHandle,
                                   IBufferReleaser* bufReleaser) override;
    int32_t getLayerBuffer(int64_t display, int64_t layer, uint32_t slot, bool fromCache,
                           const buffer_handle_t rawHandle,
                           buffer_handle_t& outBufferHandle,
                           IBufferReleaser* bufReleaser) override;
    int32_t getLayerSidebandStream(int64_t display, int64_t layer,
                                   const buffer_handle_t rawHandle,
                                   buffer_handle_t& outStreamHandle,
                                   IBufferReleaser* bufReleaser) override;
  private:
    std::unique_ptr<ComposerResources> mResources = ComposerResources::create();
};

} // namespace aidl::android::hardware::graphics::composer3::impl
