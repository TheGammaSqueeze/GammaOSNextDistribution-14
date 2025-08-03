/*
 * Copyright 2023 The Android Open Source Project
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

#include <log/log.h>

#include "RutabagaLayer.h"
#include "RutabagaVirtGpu.h"

namespace gfxstream {

RutabagaVirtGpuResource::RutabagaVirtGpuResource(std::shared_ptr<EmulatedVirtioGpu> emulation,
                                                 uint32_t resourceId, ResourceType resourceType,
                                                 uint32_t contextId)
    : mEmulation(emulation),
      mContextId(contextId),
      mResourceId(resourceId),
      mResourceType(resourceType) {}

RutabagaVirtGpuResource::~RutabagaVirtGpuResource() {
    mEmulation->DestroyResource(mContextId, mResourceId);
}

VirtGpuBlobMappingPtr RutabagaVirtGpuResource::createMapping(void) {
    uint8_t* mapped = mEmulation->Map(mResourceId);
    return std::make_shared<RutabagaVirtGpuBlobMapping>(mEmulation, shared_from_this(), mapped);
}

uint32_t RutabagaVirtGpuResource::getResourceHandle() const { return mResourceId; }

uint32_t RutabagaVirtGpuResource::getBlobHandle() const {
    if (mResourceType != ResourceType::kBlob) {
        ALOGE("Attempting to get blob handle for non-blob resource");
        return -1;
    }

    ALOGE("Unimplemented: %s", __FUNCTION__);
    return -1;
}

int RutabagaVirtGpuResource::exportBlob(VirtGpuExternalHandle&) {
    if (mResourceType != ResourceType::kBlob) {
        ALOGE("Attempting to export blob for non-blob resource");
        return -1;
    }

    ALOGE("Unimplemented: %s", __FUNCTION__);
    return -1;
}

int RutabagaVirtGpuResource::wait() { return mEmulation->Wait(mResourceId); }

int RutabagaVirtGpuResource::transferFromHost(uint32_t offset, uint32_t size) {
    if (mResourceType != ResourceType::kPipe) {
        ALOGE("Unexpected transferFromHost() called on non-pipe resource.");
        return -1;
    }

    return mEmulation->TransferFromHost(mContextId, mResourceId, offset, size);
}

int RutabagaVirtGpuResource::transferToHost(uint32_t offset, uint32_t size) {
    if (mResourceType != ResourceType::kPipe) {
        ALOGE("Unexpected transferToHost() called on non-pipe resource.");
        return -1;
    }

    return mEmulation->TransferToHost(mContextId, mResourceId, offset, size);
}

}  // namespace gfxstream
