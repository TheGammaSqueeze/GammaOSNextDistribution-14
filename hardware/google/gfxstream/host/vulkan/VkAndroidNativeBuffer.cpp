// Copyright 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expresso or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#include "VkAndroidNativeBuffer.h"

#include <string.h>

#include <future>

#include "GrallocDefs.h"
#include "SyncThread.h"
#include "VkCommonOperations.h"
#include "VulkanDispatch.h"
#include "cereal/common/goldfish_vk_deepcopy.h"
#include "cereal/common/goldfish_vk_extension_structs.h"

#include "goldfish_vk_private_defs.h"
#include "host-common/GfxstreamFatalError.h"
#include "host/FrameBuffer.h"
#include "vulkan/vk_enum_string_helper.h"

namespace gfxstream {
namespace vk {

#define VK_ANB_ERR(fmt, ...) fprintf(stderr, "%s:%d " fmt "\n", __func__, __LINE__, ##__VA_ARGS__);

#define ENABLE_VK_ANB_DEBUG 0

#if ENABLE_VK_ANB_DEBUG
#define VK_ANB_DEBUG(fmt, ...) \
    fprintf(stderr, "vk-anb-debug: %s:%d " fmt "\n", __func__, __LINE__, ##__VA_ARGS__);
#define VK_ANB_DEBUG_OBJ(obj, fmt, ...) \
    fprintf(stderr, "vk-anb-debug: %s:%d:%p " fmt "\n", __func__, __LINE__, obj, ##__VA_ARGS__);
#else
#define VK_ANB_DEBUG(fmt, ...)
#define VK_ANB_DEBUG_OBJ(obj, fmt, ...)
#endif

using android::base::AutoLock;
using android::base::Lock;
using emugl::ABORT_REASON_OTHER;
using emugl::FatalError;

AndroidNativeBufferInfo::QsriWaitFencePool::QsriWaitFencePool(VulkanDispatch* vk, VkDevice device)
    : mVk(vk), mDevice(device) {}

VkFence AndroidNativeBufferInfo::QsriWaitFencePool::getFenceFromPool() {
    VK_ANB_DEBUG("enter");
    AutoLock lock(mLock);
    VkFence fence = VK_NULL_HANDLE;
    if (mAvailableFences.empty()) {
        VkFenceCreateInfo fenceCreateInfo = {
            VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
            0,
            0,
        };
        mVk->vkCreateFence(mDevice, &fenceCreateInfo, nullptr, &fence);
        VK_ANB_DEBUG("no fences in pool, created %p", fence);
    } else {
        fence = mAvailableFences.back();
        mAvailableFences.pop_back();
        VkResult res = mVk->vkResetFences(mDevice, 1, &fence);
        if (res != VK_SUCCESS) {
            GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
                << "Fail to reset Qsri VkFence: " << res << "(" << string_VkResult(res) << ").";
        }
        VK_ANB_DEBUG("existing fence in pool: %p. also reset the fence", fence);
    }
    mUsedFences.emplace(fence);
    VK_ANB_DEBUG("exit");
    return fence;
}

AndroidNativeBufferInfo::QsriWaitFencePool::~QsriWaitFencePool() {
    VK_ANB_DEBUG("enter");
    // Nothing in the fence pool is unsignaled
    if (!mUsedFences.empty()) {
        VK_ANB_ERR("%zu VkFences are still being used when destroying the Qsri fence pool.",
                   mUsedFences.size());
    }
    for (auto fence : mAvailableFences) {
        VK_ANB_DEBUG("destroy fence %p", fence);
        mVk->vkDestroyFence(mDevice, fence, nullptr);
    }
    VK_ANB_DEBUG("exit");
}

void AndroidNativeBufferInfo::QsriWaitFencePool::returnFence(VkFence fence) {
    AutoLock lock(mLock);
    if (!mUsedFences.erase(fence)) {
        GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
            << "Return an unmanaged Qsri VkFence back to the pool.";
        return;
    }
    mAvailableFences.push_back(fence);
}

bool parseAndroidNativeBufferInfo(const VkImageCreateInfo* pCreateInfo,
                                  AndroidNativeBufferInfo* info_out) {
    // Look through the extension chain.
    const void* curr_pNext = pCreateInfo->pNext;
    if (!curr_pNext) return false;

    uint32_t structType = goldfish_vk_struct_type(curr_pNext);

    return structType == VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID;
}

VkResult prepareAndroidNativeBufferImage(VulkanDispatch* vk, VkDevice device,
                                         android::base::BumpPool& allocator,
                                         const VkImageCreateInfo* pCreateInfo,
                                         const VkNativeBufferANDROID* nativeBufferANDROID,
                                         const VkAllocationCallbacks* pAllocator,
                                         const VkPhysicalDeviceMemoryProperties* memProps,
                                         AndroidNativeBufferInfo* out) {
    out->vk = vk;
    out->device = device;
    out->vkFormat = pCreateInfo->format;
    out->extent = pCreateInfo->extent;
    out->usage = pCreateInfo->usage;

    for (uint32_t i = 0; i < pCreateInfo->queueFamilyIndexCount; ++i) {
        out->queueFamilyIndices.push_back(pCreateInfo->pQueueFamilyIndices[i]);
    }

    out->format = nativeBufferANDROID->format;
    out->stride = nativeBufferANDROID->stride;
    out->colorBufferHandle = *static_cast<const uint32_t*>(nativeBufferANDROID->handle);

    bool externalMemoryCompatible = false;

    auto emu = getGlobalVkEmulation();

    if (emu && emu->live) {
        externalMemoryCompatible = emu->deviceInfo.supportsExternalMemoryImport &&
                                   emu->deviceInfo.supportsExternalMemoryExport;
    }

    bool colorBufferExportedToGl = false;
    if (!isColorBufferExportedToGl(out->colorBufferHandle, &colorBufferExportedToGl)) {
        VK_ANB_ERR("Failed to query if ColorBuffer:%d exported to GL.", out->colorBufferHandle);
        return VK_ERROR_INITIALIZATION_FAILED;
    }

    if (externalMemoryCompatible) {
        releaseColorBufferForGuestUse(out->colorBufferHandle);
        out->externallyBacked = true;
    }

    out->useVulkanNativeImage =
        (emu && emu->live && emu->guestUsesAngle) || colorBufferExportedToGl;

    VkDeviceSize bindOffset = 0;
    if (out->externallyBacked) {
        VkImageCreateInfo createImageCi;
        deepcopy_VkImageCreateInfo(&allocator, VK_STRUCTURE_TYPE_MAX_ENUM, pCreateInfo,
                                   &createImageCi);
        auto* nativeBufferAndroid = vk_find_struct<VkNativeBufferANDROID>(&createImageCi);
        if (!nativeBufferAndroid) {
            GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
                << "VkNativeBufferANDROID is required to be included in the pNext chain of the "
                   "VkImageCreateInfo when importing a gralloc buffer.";
        }
        vk_struct_chain_remove(nativeBufferAndroid, &createImageCi);

        if (vk_find_struct<VkExternalMemoryImageCreateInfo>(&createImageCi)) {
            GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER))
                << "Unhandled VkExternalMemoryImageCreateInfo in the pNext chain.";
        }
        // Create the image with extension structure about external backing.
        VkExternalMemoryImageCreateInfo extImageCi = {
            VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_IMAGE_CREATE_INFO,
            0,
            VK_EXT_MEMORY_HANDLE_TYPE_BIT,
        };

        vk_insert_struct(createImageCi, extImageCi);

        VkResult createResult = vk->vkCreateImage(device, &createImageCi, pAllocator, &out->image);

        if (createResult != VK_SUCCESS) return createResult;

        // Now import the backing memory.
        const auto& cbInfo = getColorBufferInfo(out->colorBufferHandle);
        const auto& memInfo = cbInfo.memory;

        vk->vkGetImageMemoryRequirements(device, out->image, &out->memReqs);

        if (out->memReqs.size < memInfo.size) {
            out->memReqs.size = memInfo.size;
        }

        if (memInfo.dedicatedAllocation) {
            if (!importExternalMemoryDedicatedImage(vk, device, &memInfo, out->image,
                                                    &out->imageMemory)) {
                VK_ANB_ERR(
                    "VK_ANDROID_native_buffer: Failed to import external memory (dedicated)");
                return VK_ERROR_INITIALIZATION_FAILED;
            }
        } else {
            if (!importExternalMemory(vk, device, &memInfo, &out->imageMemory)) {
                VK_ANB_ERR("VK_ANDROID_native_buffer: Failed to import external memory");
                return VK_ERROR_INITIALIZATION_FAILED;
            }
        }

        bindOffset = memInfo.bindOffset;
    } else {
        // delete the info struct and pass to vkCreateImage, and also add
        // transfer src capability to allow us to copy to CPU.
        VkImageCreateInfo infoNoNative = *pCreateInfo;
        infoNoNative.pNext = nullptr;
        infoNoNative.usage |= VK_IMAGE_USAGE_TRANSFER_SRC_BIT;
        VkResult createResult = vk->vkCreateImage(device, &infoNoNative, pAllocator, &out->image);

        if (createResult != VK_SUCCESS) return createResult;

        vk->vkGetImageMemoryRequirements(device, out->image, &out->memReqs);

        uint32_t imageMemoryTypeIndex = 0;
        bool imageMemoryTypeIndexFound = false;

        for (uint32_t i = 0; i < VK_MAX_MEMORY_TYPES; ++i) {
            bool supported = out->memReqs.memoryTypeBits & (1 << i);
            if (supported) {
                imageMemoryTypeIndex = i;
                imageMemoryTypeIndexFound = true;
                break;
            }
        }

        if (!imageMemoryTypeIndexFound) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not obtain "
                "image memory type index");
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_DEVICE_MEMORY;
        }

        out->imageMemoryTypeIndex = imageMemoryTypeIndex;

        VkMemoryAllocateInfo allocInfo = {
            VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            0,
            out->memReqs.size,
            out->imageMemoryTypeIndex,
        };

        if (VK_SUCCESS != vk->vkAllocateMemory(device, &allocInfo, nullptr, &out->imageMemory)) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not allocate "
                "image memory. requested size: %zu",
                (size_t)(out->memReqs.size));
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_DEVICE_MEMORY;
        }
    }

    if (VK_SUCCESS != vk->vkBindImageMemory(device, out->image, out->imageMemory, bindOffset)) {
        VK_ANB_ERR(
            "VK_ANDROID_native_buffer: could not bind "
            "image memory.");
        teardownAndroidNativeBufferImage(vk, out);
        return VK_ERROR_OUT_OF_DEVICE_MEMORY;
    }

    // Allocate a staging memory and set up the staging buffer.
    // TODO: Make this shared as well if we can get that to
    // work on Windows with NVIDIA.
    {
        bool stagingIndexRes =
            getStagingMemoryTypeIndex(vk, device, memProps, &out->stagingMemoryTypeIndex);

        if (!stagingIndexRes) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not obtain "
                "staging memory type index");
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_HOST_MEMORY;
        }

        VkMemoryAllocateInfo allocInfo = {
            VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
            0,
            out->memReqs.size,
            out->stagingMemoryTypeIndex,
        };

        VkResult res = vk->vkAllocateMemory(device, &allocInfo, nullptr, &out->stagingMemory);
        if (VK_SUCCESS != res) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not allocate staging memory. "
                "res = %d. requested size: %zu",
                (int)res, (size_t)(out->memReqs.size));
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_HOST_MEMORY;
        }

        VkBufferCreateInfo stagingBufferCreateInfo = {
            VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
            0,
            0,
            out->memReqs.size,
            VK_BUFFER_USAGE_TRANSFER_DST_BIT | VK_BUFFER_USAGE_TRANSFER_SRC_BIT,
            VK_SHARING_MODE_EXCLUSIVE,
            0,
            nullptr,
        };
        if (out->queueFamilyIndices.size() > 1) {
            stagingBufferCreateInfo.sharingMode = VK_SHARING_MODE_CONCURRENT;
            stagingBufferCreateInfo.queueFamilyIndexCount =
                static_cast<uint32_t>(out->queueFamilyIndices.size());
            stagingBufferCreateInfo.pQueueFamilyIndices = out->queueFamilyIndices.data();
        }

        if (VK_SUCCESS !=
            vk->vkCreateBuffer(device, &stagingBufferCreateInfo, nullptr, &out->stagingBuffer)) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not create "
                "staging buffer.");
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_HOST_MEMORY;
        }

        if (VK_SUCCESS !=
            vk->vkBindBufferMemory(device, out->stagingBuffer, out->stagingMemory, 0)) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not bind "
                "staging buffer to staging memory.");
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_HOST_MEMORY;
        }

        if (VK_SUCCESS != vk->vkMapMemory(device, out->stagingMemory, 0, out->memReqs.size, 0,
                                          (void**)&out->mappedStagingPtr)) {
            VK_ANB_ERR(
                "VK_ANDROID_native_buffer: could not map "
                "staging buffer.");
            teardownAndroidNativeBufferImage(vk, out);
            return VK_ERROR_OUT_OF_HOST_MEMORY;
        }
    }

    out->qsriWaitFencePool =
        std::make_unique<AndroidNativeBufferInfo::QsriWaitFencePool>(out->vk, out->device);
    out->qsriTimeline = std::make_unique<VkQsriTimeline>();
    return VK_SUCCESS;
}

void teardownAndroidNativeBufferImage(VulkanDispatch* vk, AndroidNativeBufferInfo* anbInfo) {
    auto device = anbInfo->device;

    auto image = anbInfo->image;
    auto imageMemory = anbInfo->imageMemory;

    auto stagingBuffer = anbInfo->stagingBuffer;
    auto mappedPtr = anbInfo->mappedStagingPtr;
    auto stagingMemory = anbInfo->stagingMemory;

    if (image) vk->vkDestroyImage(device, image, nullptr);
    if (imageMemory) vk->vkFreeMemory(device, imageMemory, nullptr);
    if (stagingBuffer) vk->vkDestroyBuffer(device, stagingBuffer, nullptr);
    if (mappedPtr) vk->vkUnmapMemory(device, stagingMemory);
    if (stagingMemory) vk->vkFreeMemory(device, stagingMemory, nullptr);

    for (auto queueState : anbInfo->queueStates) {
        queueState.teardown(vk, device);
    }

    anbInfo->queueStates.clear();

    anbInfo->acquireQueueState.teardown(vk, device);

    anbInfo->vk = nullptr;
    anbInfo->device = VK_NULL_HANDLE;
    anbInfo->image = VK_NULL_HANDLE;
    anbInfo->imageMemory = VK_NULL_HANDLE;
    anbInfo->stagingBuffer = VK_NULL_HANDLE;
    anbInfo->mappedStagingPtr = nullptr;
    anbInfo->stagingMemory = VK_NULL_HANDLE;

    anbInfo->qsriWaitFencePool = nullptr;
}

void getGralloc0Usage(VkFormat format, VkImageUsageFlags imageUsage, int* usage_out) {
    // Pick some default flexible values for gralloc usage for now.
    (void)format;
    (void)imageUsage;
    *usage_out = GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN |
                 GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE;
}

// Taken from Android GrallocUsageConversion.h
void getGralloc1Usage(VkFormat format, VkImageUsageFlags imageUsage,
                      VkSwapchainImageUsageFlagsANDROID swapchainImageUsage,
                      uint64_t* consumerUsage_out, uint64_t* producerUsage_out) {
    // Pick some default flexible values for gralloc usage for now.
    (void)format;
    (void)imageUsage;
    (void)swapchainImageUsage;

    constexpr int usage = GRALLOC_USAGE_SW_READ_OFTEN | GRALLOC_USAGE_SW_WRITE_OFTEN |
                          GRALLOC_USAGE_HW_RENDER | GRALLOC_USAGE_HW_TEXTURE;

    constexpr uint64_t PRODUCER_MASK =
        GRALLOC1_PRODUCER_USAGE_CPU_READ |
        /* GRALLOC1_PRODUCER_USAGE_CPU_READ_OFTEN | */
        GRALLOC1_PRODUCER_USAGE_CPU_WRITE |
        /* GRALLOC1_PRODUCER_USAGE_CPU_WRITE_OFTEN | */
        GRALLOC1_PRODUCER_USAGE_GPU_RENDER_TARGET | GRALLOC1_PRODUCER_USAGE_PROTECTED |
        GRALLOC1_PRODUCER_USAGE_CAMERA | GRALLOC1_PRODUCER_USAGE_VIDEO_DECODER |
        GRALLOC1_PRODUCER_USAGE_SENSOR_DIRECT_DATA;
    constexpr uint64_t CONSUMER_MASK =
        GRALLOC1_CONSUMER_USAGE_CPU_READ |
        /* GRALLOC1_CONSUMER_USAGE_CPU_READ_OFTEN | */
        GRALLOC1_CONSUMER_USAGE_GPU_TEXTURE | GRALLOC1_CONSUMER_USAGE_HWCOMPOSER |
        GRALLOC1_CONSUMER_USAGE_CLIENT_TARGET | GRALLOC1_CONSUMER_USAGE_CURSOR |
        GRALLOC1_CONSUMER_USAGE_VIDEO_ENCODER | GRALLOC1_CONSUMER_USAGE_CAMERA |
        GRALLOC1_CONSUMER_USAGE_RENDERSCRIPT | GRALLOC1_CONSUMER_USAGE_GPU_DATA_BUFFER;

    *producerUsage_out = static_cast<uint64_t>(usage) & PRODUCER_MASK;
    *consumerUsage_out = static_cast<uint64_t>(usage) & CONSUMER_MASK;

    if ((static_cast<uint32_t>(usage) & GRALLOC_USAGE_SW_READ_OFTEN) ==
        GRALLOC_USAGE_SW_READ_OFTEN) {
        *producerUsage_out |= GRALLOC1_PRODUCER_USAGE_CPU_READ_OFTEN;
        *consumerUsage_out |= GRALLOC1_CONSUMER_USAGE_CPU_READ_OFTEN;
    }

    if ((static_cast<uint32_t>(usage) & GRALLOC_USAGE_SW_WRITE_OFTEN) ==
        GRALLOC_USAGE_SW_WRITE_OFTEN) {
        *producerUsage_out |= GRALLOC1_PRODUCER_USAGE_CPU_WRITE_OFTEN;
    }
}

void AndroidNativeBufferInfo::QueueState::setup(VulkanDispatch* vk, VkDevice device,
                                                VkQueue queueIn, uint32_t queueFamilyIndexIn,
                                                android::base::Lock* queueLockIn) {
    queue = queueIn;
    queueFamilyIndex = queueFamilyIndexIn;
    lock = queueLockIn;

    VkCommandPoolCreateInfo poolCreateInfo = {
        VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
        0,
        VK_COMMAND_POOL_CREATE_RESET_COMMAND_BUFFER_BIT,
        queueFamilyIndex,
    };

    vk->vkCreateCommandPool(device, &poolCreateInfo, nullptr, &pool);

    VkCommandBufferAllocateInfo cbAllocInfo = {
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO, 0, pool, VK_COMMAND_BUFFER_LEVEL_PRIMARY, 1,
    };

    vk->vkAllocateCommandBuffers(device, &cbAllocInfo, &cb);

    vk->vkAllocateCommandBuffers(device, &cbAllocInfo, &cb2);

    VkFenceCreateInfo fenceCreateInfo = {
        VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
        0,
        0,
    };

    vk->vkCreateFence(device, &fenceCreateInfo, nullptr, &fence);
}

void AndroidNativeBufferInfo::QueueState::teardown(VulkanDispatch* vk, VkDevice device) {
    if (queue) {
        AutoLock qlock(*lock);
        vk->vkQueueWaitIdle(queue);
    }
    if (cb) vk->vkFreeCommandBuffers(device, pool, 1, &cb);
    if (pool) vk->vkDestroyCommandPool(device, pool, nullptr);
    if (fence) vk->vkDestroyFence(device, fence, nullptr);

    lock = nullptr;
    queue = VK_NULL_HANDLE;
    pool = VK_NULL_HANDLE;
    cb = VK_NULL_HANDLE;
    fence = VK_NULL_HANDLE;
    queueFamilyIndex = 0;
}

VkResult setAndroidNativeImageSemaphoreSignaled(VulkanDispatch* vk, VkDevice device,
                                                VkQueue defaultQueue,
                                                uint32_t defaultQueueFamilyIndex,
                                                Lock* defaultQueueLock, VkSemaphore semaphore,
                                                VkFence fence, AndroidNativeBufferInfo* anbInfo) {
    auto fb = FrameBuffer::getFB();
    auto emu = getGlobalVkEmulation();

    bool firstTimeSetup = !anbInfo->everSynced && !anbInfo->everAcquired;

    anbInfo->everAcquired = true;

    if (firstTimeSetup) {
        VkSubmitInfo submitInfo = {
            VK_STRUCTURE_TYPE_SUBMIT_INFO,
            0,
            0,
            nullptr,
            nullptr,
            0,
            nullptr,
            (uint32_t)(semaphore == VK_NULL_HANDLE ? 0 : 1),
            semaphore == VK_NULL_HANDLE ? nullptr : &semaphore,
        };
        AutoLock qlock(*defaultQueueLock);
        VK_CHECK(vk->vkQueueSubmit(defaultQueue, 1, &submitInfo, fence));
    } else {
        // Setup queue state for this queue family index.
        auto queueFamilyIndex = anbInfo->lastUsedQueueFamilyIndex;
        if (queueFamilyIndex >= anbInfo->queueStates.size()) {
            anbInfo->queueStates.resize(queueFamilyIndex + 1);
        }
        AndroidNativeBufferInfo::QueueState& queueState =
            anbInfo->queueStates[queueFamilyIndex];
        if (!queueState.queue) {
            queueState.setup(vk, anbInfo->device, defaultQueue, queueFamilyIndex, defaultQueueLock);
        }

        // If we used the Vulkan image without copying it back
        // to the CPU, reset the layout to PRESENT.
        if (anbInfo->useVulkanNativeImage) {
            VkCommandBufferBeginInfo beginInfo = {
                VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                0,
                VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
                nullptr /* no inheritance info */,
            };

            vk->vkBeginCommandBuffer(queueState.cb2, &beginInfo);

            emu->debugUtilsHelper.cmdBeginDebugLabel(queueState.cb2,
                                                     "vkAcquireImageANDROID(ColorBuffer:%d)",
                                                     anbInfo->colorBufferHandle);

            VkImageMemoryBarrier queueTransferBarrier = {
                .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
                .srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
                .oldLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL,
                .dstQueueFamilyIndex = anbInfo->lastUsedQueueFamilyIndex,
                .image = anbInfo->image,
                .subresourceRange =
                    {
                        VK_IMAGE_ASPECT_COLOR_BIT,
                        0,
                        1,
                        0,
                        1,
                    },
            };
            vk->vkCmdPipelineBarrier(queueState.cb2, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                     VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr,
                                     1, &queueTransferBarrier);

            emu->debugUtilsHelper.cmdEndDebugLabel(queueState.cb2);

            vk->vkEndCommandBuffer(queueState.cb2);

            VkSubmitInfo submitInfo = {
                VK_STRUCTURE_TYPE_SUBMIT_INFO,
                0,
                0,
                nullptr,
                nullptr,
                1,
                &queueState.cb2,
                (uint32_t)(semaphore == VK_NULL_HANDLE ? 0 : 1),
                semaphore == VK_NULL_HANDLE ? nullptr : &semaphore,
            };

            AutoLock qlock(*queueState.lock);
            // TODO(kaiyili): initiate ownership transfer from DisplayVk here
            VK_CHECK(vk->vkQueueSubmit(queueState.queue, 1, &submitInfo, fence));
        } else {
            const AndroidNativeBufferInfo::QueueState& queueState =
                anbInfo->queueStates[anbInfo->lastUsedQueueFamilyIndex];
            VkSubmitInfo submitInfo = {
                VK_STRUCTURE_TYPE_SUBMIT_INFO,
                0,
                0,
                nullptr,
                nullptr,
                0,
                nullptr,
                (uint32_t)(semaphore == VK_NULL_HANDLE ? 0 : 1),
                semaphore == VK_NULL_HANDLE ? nullptr : &semaphore,
            };
            AutoLock qlock(*queueState.lock);
            VK_CHECK(vk->vkQueueSubmit(queueState.queue, 1, &submitInfo, fence));
        }
    }

    return VK_SUCCESS;
}

static constexpr uint64_t kTimeoutNs = 3ULL * 1000000000ULL;

VkResult syncImageToColorBuffer(VulkanDispatch* vk, uint32_t queueFamilyIndex, VkQueue queue,
                                Lock* queueLock, uint32_t waitSemaphoreCount,
                                const VkSemaphore* pWaitSemaphores, int* pNativeFenceFd,
                                std::shared_ptr<AndroidNativeBufferInfo> anbInfo) {
    auto anbInfoPtr = anbInfo.get();
    auto fb = FrameBuffer::getFB();
    fb->lock();

    // Implicitly synchronized
    *pNativeFenceFd = -1;

    anbInfo->everSynced = true;
    anbInfo->lastUsedQueueFamilyIndex = queueFamilyIndex;

    // Setup queue state for this queue family index.
    if (queueFamilyIndex >= anbInfo->queueStates.size()) {
        anbInfo->queueStates.resize(queueFamilyIndex + 1);
    }

    auto& queueState = anbInfo->queueStates[queueFamilyIndex];

    if (!queueState.queue) {
        queueState.setup(vk, anbInfo->device, queue, queueFamilyIndex, queueLock);
    }

    auto emu = getGlobalVkEmulation();

    // Record our synchronization commands.
    VkCommandBufferBeginInfo beginInfo = {
        VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
        0,
        VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
        nullptr /* no inheritance info */,
    };

    vk->vkBeginCommandBuffer(queueState.cb, &beginInfo);

    emu->debugUtilsHelper.cmdBeginDebugLabel(queueState.cb,
                                             "vkQueueSignalReleaseImageANDROID(ColorBuffer:%d)",
                                             anbInfo->colorBufferHandle);

    // If using the Vulkan image directly (rather than copying it back to
    // the CPU), change its layout for that use.
    if (anbInfo->useVulkanNativeImage) {
        VkImageMemoryBarrier queueTransferBarrier = {
            .sType = VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            .srcAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .dstAccessMask = VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
            .oldLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            .newLayout = VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            .srcQueueFamilyIndex = queueFamilyIndex,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL,
            .image = anbInfo->image,
            .subresourceRange =
                {
                    VK_IMAGE_ASPECT_COLOR_BIT,
                    0,
                    1,
                    0,
                    1,
                },
        };
        vk->vkCmdPipelineBarrier(queueState.cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                 VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1,
                                 &queueTransferBarrier);

    } else {
        // Not a GL texture. Read it back and put it back in present layout.

        // From the spec: If an application does not need the contents of a resource
        // to remain valid when transferring from one queue family to another, then
        // the ownership transfer should be skipped.
        // We definitely need to transition the image to
        // VK_TRANSFER_SRC_OPTIMAL and back.
        VkImageMemoryBarrier presentToTransferSrc = {
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            0,
            0,
            VK_ACCESS_TRANSFER_READ_BIT,
            VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_QUEUE_FAMILY_IGNORED,
            VK_QUEUE_FAMILY_IGNORED,
            anbInfo->image,
            {
                VK_IMAGE_ASPECT_COLOR_BIT,
                0,
                1,
                0,
                1,
            },
        };

        vk->vkCmdPipelineBarrier(queueState.cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                 VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1,
                                 &presentToTransferSrc);

        VkBufferImageCopy region = {
            0 /* buffer offset */,
            anbInfo->extent.width,
            anbInfo->extent.height,
            {
                VK_IMAGE_ASPECT_COLOR_BIT,
                0,
                0,
                1,
            },
            {0, 0, 0},
            anbInfo->extent,
        };

        vk->vkCmdCopyImageToBuffer(queueState.cb, anbInfo->image,
                                   VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL, anbInfo->stagingBuffer, 1,
                                   &region);

        // Transfer back to present src.
        VkImageMemoryBarrier backToPresentSrc = {
            VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
            0,
            VK_ACCESS_TRANSFER_READ_BIT,
            0,
            VK_IMAGE_LAYOUT_TRANSFER_SRC_OPTIMAL,
            VK_IMAGE_LAYOUT_PRESENT_SRC_KHR,
            VK_QUEUE_FAMILY_IGNORED,
            VK_QUEUE_FAMILY_IGNORED,
            anbInfo->image,
            {
                VK_IMAGE_ASPECT_COLOR_BIT,
                0,
                1,
                0,
                1,
            },
        };

        vk->vkCmdPipelineBarrier(queueState.cb, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                                 VK_PIPELINE_STAGE_ALL_COMMANDS_BIT, 0, 0, nullptr, 0, nullptr, 1,
                                 &backToPresentSrc);
    }

    emu->debugUtilsHelper.cmdEndDebugLabel(queueState.cb);

    vk->vkEndCommandBuffer(queueState.cb);

    std::vector<VkPipelineStageFlags> pipelineStageFlags;
    pipelineStageFlags.resize(waitSemaphoreCount, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT);

    VkSubmitInfo submitInfo = {
        VK_STRUCTURE_TYPE_SUBMIT_INFO,
        0,
        waitSemaphoreCount,
        pWaitSemaphores,
        pipelineStageFlags.data(),
        1,
        &queueState.cb,
        0,
        nullptr,
    };

    // TODO(kaiyili): initiate ownership transfer to DisplayVk here.
    VkFence qsriFence = anbInfo->qsriWaitFencePool->getFenceFromPool();
    AutoLock qLock(*queueLock);
    VK_CHECK(vk->vkQueueSubmit(queueState.queue, 1, &submitInfo, qsriFence));
    auto waitForQsriFenceTask = [anbInfoPtr, anbInfo, vk, device = anbInfo->device, qsriFence] {
        (void)anbInfoPtr;
        VK_ANB_DEBUG_OBJ(anbInfoPtr, "wait callback: enter");
        VK_ANB_DEBUG_OBJ(anbInfoPtr, "wait callback: wait for fence %p...", qsriFence);
        VkResult res = vk->vkWaitForFences(device, 1, &qsriFence, VK_FALSE, kTimeoutNs);
        switch (res) {
            case VK_SUCCESS:
                break;
            case VK_TIMEOUT:
                VK_ANB_ERR("Timeout when waiting for the Qsri fence.");
                break;
            default:
                ERR("Failed to wait for QSRI fence: %s\n", string_VkResult(res));
                VK_CHECK(res);
        }
        VK_ANB_DEBUG_OBJ(anbInfoPtr, "wait callback: wait for fence %p...(done)", qsriFence);
        anbInfo->qsriWaitFencePool->returnFence(qsriFence);
    };
    fb->unlock();

    if (anbInfo->useVulkanNativeImage) {
        VK_ANB_DEBUG_OBJ(anbInfoPtr, "using native image, so use sync thread to wait");
        // Queue wait to sync thread with completion callback
        // Pass anbInfo by value to get a ref
        SyncThread::get()->triggerGeneral(
            [waitForQsriFenceTask = std::move(waitForQsriFenceTask), anbInfo]() mutable {
                waitForQsriFenceTask();
                anbInfo->qsriTimeline->signalNextPresentAndPoll();
            },
            "wait for the guest Qsri VkFence signaled");
    } else {
        VK_ANB_DEBUG_OBJ(anbInfoPtr, "not using native image, so wait right away");
        waitForQsriFenceTask();

        VkMappedMemoryRange toInvalidate = {
            VK_STRUCTURE_TYPE_MAPPED_MEMORY_RANGE, 0, anbInfo->stagingMemory, 0, VK_WHOLE_SIZE,
        };

        vk->vkInvalidateMappedMemoryRanges(anbInfo->device, 1, &toInvalidate);

        uint32_t colorBufferHandle = anbInfo->colorBufferHandle;

        // Copy to from staging buffer to color buffer
        uint32_t bpp = 4; /* format always rgba8...not */
        switch (anbInfo->vkFormat) {
            case VK_FORMAT_R5G6B5_UNORM_PACK16:
                bpp = 2;
                break;
            case VK_FORMAT_R8G8B8_UNORM:
                bpp = 3;
                break;
            default:
            case VK_FORMAT_R8G8B8A8_UNORM:
            case VK_FORMAT_B8G8R8A8_UNORM:
                bpp = 4;
                break;
        }

        FrameBuffer::getFB()->flushColorBufferFromVkBytes(
            colorBufferHandle, anbInfo->mappedStagingPtr,
            bpp * anbInfo->extent.width * anbInfo->extent.height);
        anbInfo->qsriTimeline->signalNextPresentAndPoll();
    }

    return VK_SUCCESS;
}

}  // namespace vk
}  // namespace gfxstream
