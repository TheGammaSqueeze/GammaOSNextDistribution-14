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

#pragma once

#include <memory>

#include "VirtGpu.h"

namespace gfxstream {

// Virtio GPU abstraction that directly runs a host render server.

class RutabagaVirtGpuDevice;

class RutabagaVirtGpuBlobMapping : public VirtGpuBlobMapping {
  public:
   RutabagaVirtGpuBlobMapping(std::shared_ptr<EmulatedVirtioGpu> emulation, VirtGpuBlobPtr blob,
                              uint8_t* mapped);
   ~RutabagaVirtGpuBlobMapping();

   uint8_t* asRawPtr(void) override;

  private:
   const std::shared_ptr<EmulatedVirtioGpu> mEmulation;
   const VirtGpuBlobPtr mBlob;
   uint8_t* mMapped = nullptr;
};

class RutabagaVirtGpuResource : public std::enable_shared_from_this<RutabagaVirtGpuResource>, public VirtGpuBlob {
  public:
    ~RutabagaVirtGpuResource();

    VirtGpuBlobMappingPtr createMapping(void) override;

    uint32_t getResourceHandle() const override;
    uint32_t getBlobHandle() const override;

    int exportBlob(VirtGpuExternalHandle& handle) override;
    int wait() override;

    int transferFromHost(uint32_t offset, uint32_t size) override;
    int transferToHost(uint32_t offset, uint32_t size) override;

  private:
    friend class RutabagaVirtGpuDevice;

    enum class ResourceType {
        kBlob,
        kPipe,
    };

    RutabagaVirtGpuResource(std::shared_ptr<EmulatedVirtioGpu> emulation, uint32_t resourceId,
                            ResourceType resourceType, uint32_t contextId);

    const std::shared_ptr<EmulatedVirtioGpu> mEmulation;
    const uint32_t mContextId;
    const uint32_t mResourceId;
    const ResourceType mResourceType;
};

class RutabagaVirtGpuDevice : public std::enable_shared_from_this<RutabagaVirtGpuDevice>, public VirtGpuDevice {
  public:
   RutabagaVirtGpuDevice(std::shared_ptr<EmulatedVirtioGpu> emulation, VirtGpuCapset capset);
   ~RutabagaVirtGpuDevice();

   bool Init();

   int64_t getDeviceHandle() override;

   VirtGpuCaps getCaps() override;

   VirtGpuBlobPtr createBlob(const struct VirtGpuCreateBlob& blobCreate) override;

   VirtGpuBlobPtr createVirglBlob(uint32_t width, uint32_t height, uint32_t virglFormat) override;

   VirtGpuBlobPtr importBlob(const struct VirtGpuExternalHandle& handle) override;

   int execBuffer(struct VirtGpuExecBuffer& execbuffer, const VirtGpuBlob* blob) override;

  private:
   const std::shared_ptr<EmulatedVirtioGpu> mEmulation;
   uint32_t mContextId;
   VirtGpuCapset mCapset;
   struct VirtGpuCaps mCaps;

   friend class RutabagaVirtGpuResource;
   uint32_t GetContextId() const { return mContextId; }
};

}  // namespace gfxstream
