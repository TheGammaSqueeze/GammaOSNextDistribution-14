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

RutabagaVirtGpuDevice::RutabagaVirtGpuDevice(std::shared_ptr<EmulatedVirtioGpu> emulation,
                                             VirtGpuCapset capset)
    : VirtGpuDevice(capset), mEmulation(emulation), mCapset(capset) {}

RutabagaVirtGpuDevice::~RutabagaVirtGpuDevice() { mEmulation->DestroyContext(mContextId); }

bool RutabagaVirtGpuDevice::Init() {
    uint32_t capsetId = 0;
    uint32_t capsetSize = 0;
    uint32_t contextInit = 0;
    uint8_t* capsetPtr = nullptr;

    mCaps = {
        .params =
            {
                [kParam3D] = 1,
                [kParamCapsetFix] = 1,
                [kParamResourceBlob] = 1,
                [kParamHostVisible] = 1,
                [kParamCrossDevice] = 0,
                [kParamContextInit] = 1,
                [kParamSupportedCapsetIds] = 0,
                [kParamExplicitDebugName] = 0,
                [kParamCreateGuestHandle] = 0,
            },
    };

    capsetId = static_cast<uint32_t>(mCapset);
    switch (mCapset) {
        case kCapsetGfxStreamVulkan:
            capsetSize = sizeof(struct vulkanCapset);
            capsetPtr = reinterpret_cast<uint8_t*>(&mCaps.vulkanCapset);
            break;
        case kCapsetGfxStreamMagma:
            capsetSize = sizeof(struct magmaCapset);
            capsetPtr = reinterpret_cast<uint8_t*>(&mCaps.magmaCapset);
            break;
        case kCapsetGfxStreamGles:
            capsetSize = sizeof(struct vulkanCapset);
            capsetPtr = reinterpret_cast<uint8_t*>(&mCaps.glesCapset);
            break;
        case kCapsetGfxStreamComposer:
            capsetSize = sizeof(struct vulkanCapset);
            capsetPtr = reinterpret_cast<uint8_t*>(&mCaps.composerCapset);
            break;
        default:
            capsetSize = 0;
    }

    if (capsetId != 0) {
        bool success = mEmulation->GetCaps(capsetId, capsetSize, capsetPtr);
        if (!success) {
            ALOGE("Failed to capability set");
            return false;
        }
    }

    const auto contextIdOp = mEmulation->CreateContext(capsetId);
    if (!contextIdOp) {
        ALOGE("Failed to create RutabagaVirtGpuDevice: failed to create context.");
        return false;
    }

    mContextId = *contextIdOp;
    return true;
}

int64_t RutabagaVirtGpuDevice::getDeviceHandle() { return -1; }

VirtGpuCaps RutabagaVirtGpuDevice::getCaps() { return mCaps; }

VirtGpuBlobPtr RutabagaVirtGpuDevice::createBlob(const struct VirtGpuCreateBlob& blobCreate) {
    const auto resourceIdOpt = mEmulation->CreateBlob(
        mContextId, static_cast<uint32_t>(blobCreate.blobMem),
        static_cast<uint32_t>(blobCreate.flags), blobCreate.blobId, blobCreate.size);
    if (!resourceIdOpt) {
        return nullptr;
    }

    return VirtGpuBlobPtr(new RutabagaVirtGpuResource(
        mEmulation, *resourceIdOpt, RutabagaVirtGpuResource::ResourceType::kBlob, mContextId));
}

VirtGpuBlobPtr RutabagaVirtGpuDevice::createVirglBlob(uint32_t width, uint32_t height,
                                                      uint32_t virglFormat) {
    uint32_t target = 0;
    uint32_t bind = 0;
    uint32_t bpp = 0;
    uint32_t size;

    switch (virglFormat) {
        case VIRGL_FORMAT_R8G8B8A8_UNORM:
        case VIRGL_FORMAT_B8G8R8A8_UNORM:
            target = PIPE_TEXTURE_2D;
            bind = VIRGL_BIND_RENDER_TARGET;
            bpp = 4;
            break;
        case VIRGL_FORMAT_B5G6R5_UNORM:
            target = PIPE_TEXTURE_2D;
            bind = VIRGL_BIND_RENDER_TARGET;
            bpp = 2;
            break;
        case VIRGL_FORMAT_R8G8B8_UNORM:
            target = PIPE_TEXTURE_2D;
            bind = VIRGL_BIND_RENDER_TARGET;
            bpp = 3;
            break;
        case VIRGL_FORMAT_R8_UNORM:
            target = PIPE_BUFFER;
            bind = VIRGL_BIND_CUSTOM;
            bpp = 1;
            break;
        default:
            ALOGE("Unknown virgl format %u", virglFormat);
            return {};
    }

    size = width * height * bpp;

    const auto resourceIdOpt =
        mEmulation->CreateVirglBlob(mContextId, width, height, virglFormat, target, bind, size);
    if (!resourceIdOpt) {
        return nullptr;
    }

    return VirtGpuBlobPtr(new RutabagaVirtGpuResource(
        mEmulation, *resourceIdOpt, RutabagaVirtGpuResource::ResourceType::kPipe, mContextId));
}

int RutabagaVirtGpuDevice::execBuffer(struct VirtGpuExecBuffer& execbuffer,
                                      const VirtGpuBlob* blob) {
    std::optional<uint32_t> blobResourceId;
    uint32_t fenceId = 0;
    VirtioGpuFenceFlags fenceFlags = kFlagNone;

    if (blob) {
        blobResourceId = blob->getResourceHandle();
    }

    if (execbuffer.flags & kFenceOut) {
        fenceFlags = kFlagFence;
    }

    int ret = mEmulation->SubmitCmd(mContextId, execbuffer.command_size, execbuffer.command,
                                    execbuffer.ring_idx, fenceFlags, fenceId, blobResourceId);

    if (execbuffer.flags & kFenceOut) {
        execbuffer.handle.osHandle = fenceId;
        execbuffer.handle.type = kFenceHandleSyncFd;
    }

    return ret;
}

VirtGpuBlobPtr RutabagaVirtGpuDevice::importBlob(const struct VirtGpuExternalHandle&) {
    ALOGE("Unimplemented %s", __FUNCTION__);
    return nullptr;
}

}  // namespace gfxstream

VirtGpuDevice* createPlatformVirtGpuDevice(enum VirtGpuCapset capset, int) {
    std::shared_ptr<gfxstream::EmulatedVirtioGpu> emulation = gfxstream::EmulatedVirtioGpu::Get();
    if (!emulation) {
        ALOGE("Failed to create RutabagaVirtGpuDevice: failed to get emulation layer.");
        return nullptr;
    }

    auto device = new gfxstream::RutabagaVirtGpuDevice(emulation, capset);
    bool success = device->Init();
    if (!success) {
        ALOGE("Failed to create RutabagaVirtGpuDevice: Init failed.");
        delete device;
        return nullptr;
    }

    return device;
}
