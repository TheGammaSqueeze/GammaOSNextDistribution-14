// Copyright 2018 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either expresso or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include <vulkan/vulkan.h>

#include <atomic>
#include <deque>
#include <memory>
#include <unordered_set>
#include <vector>

#include "VkCommonOperations.h"
#include "VkQsriTimeline.h"
#include "aemu/base/BumpPool.h"
#include "aemu/base/synchronization/ConditionVariable.h"
#include "aemu/base/synchronization/Lock.h"
#include "goldfish_vk_private_defs.h"

namespace gfxstream {
namespace vk {

struct AndroidNativeBufferInfo;
struct VulkanDispatch;

// This class provides methods to create and query information about Android
// native buffers in the context of creating Android swapchain images that have
// Android native buffer backing.

// This is to be refactored to move to external memory only once we get that
// working.

void teardownAndroidNativeBufferImage(VulkanDispatch* vk, AndroidNativeBufferInfo* anbInfo);

struct AndroidNativeBufferInfo {
    ~AndroidNativeBufferInfo() {
        if (vk) {
            teardownAndroidNativeBufferImage(vk, this);
        }
    }

    VulkanDispatch* vk = nullptr;
    VkDevice device = VK_NULL_HANDLE;
    VkFormat vkFormat;
    VkExtent3D extent;
    VkImageUsageFlags usage;
    std::vector<uint32_t> queueFamilyIndices;

    int format;
    int stride;
    uint32_t colorBufferHandle;
    bool externallyBacked = false;
    bool useVulkanNativeImage = false;

    // We will be using separate allocations for image versus staging memory,
    // because not all host Vulkan drivers will support directly rendering to
    // host visible memory in a layout that glTexSubImage2D can consume.

    // If we are using external memory, these memories are imported
    // to the current instance.
    VkDeviceMemory imageMemory = VK_NULL_HANDLE;
    VkDeviceMemory stagingMemory = VK_NULL_HANDLE;

    VkBuffer stagingBuffer = VK_NULL_HANDLE;

    uint32_t imageMemoryTypeIndex;
    uint32_t stagingMemoryTypeIndex;

    uint8_t* mappedStagingPtr = nullptr;

    // To be populated later as we go.
    VkImage image = VK_NULL_HANDLE;
    VkMemoryRequirements memReqs;

    // The queue over which we send the buffer/image copy commands depends on
    // the queue over which vkQueueSignalReleaseImageANDROID happens.
    // It is assumed that the VkImage object has been created by Android swapchain layer
    // with all the relevant queue family indices for sharing set properly.
    struct QueueState {
        VkQueue queue = VK_NULL_HANDLE;
        VkCommandPool pool = VK_NULL_HANDLE;
        VkCommandBuffer cb = VK_NULL_HANDLE;
        VkCommandBuffer cb2 = VK_NULL_HANDLE;
        VkFence fence = VK_NULL_HANDLE;
        android::base::Lock* lock = nullptr;
        uint32_t queueFamilyIndex = 0;
        void setup(VulkanDispatch* vk, VkDevice device, VkQueue queue, uint32_t queueFamilyIndex,
                   android::base::Lock* queueLock);
        void teardown(VulkanDispatch* vk, VkDevice device);
    };
    // We keep one QueueState for each queue family index used by the guest
    // in vkQueuePresentKHR.
    std::vector<QueueState> queueStates;

    // Did we ever sync the Vulkan image with a ColorBuffer?
    // If so, set everSynced along with the queue family index
    // used to do that.
    // If the swapchain image was created with exclusive sharing
    // mode (reflected in this struct's |sharingMode| field),
    // this part doesn't really matter.
    bool everSynced = false;
    uint32_t lastUsedQueueFamilyIndex;

    // On first acquire, we might use a different queue family
    // to initially set the semaphore/fence to be signaled.
    // Track that here.
    bool everAcquired = false;
    QueueState acquireQueueState;

    // State that is of interest when interacting with sync fds and SyncThread.
    // Protected by this lock and condition variable.
    class QsriWaitFencePool {
       public:
        QsriWaitFencePool(VulkanDispatch*, VkDevice);
        ~QsriWaitFencePool();
        VkFence getFenceFromPool();
        void returnFence(VkFence fence);

       private:
        android::base::Lock mLock;

        VulkanDispatch* mVk;
        VkDevice mDevice;

        // A pool of vkFences for waiting (optimization so we don't keep recreating them every
        // time).
        std::vector<VkFence> mAvailableFences;
        std::unordered_set<VkFence> mUsedFences;
    };

    std::unique_ptr<QsriWaitFencePool> qsriWaitFencePool = nullptr;
    std::unique_ptr<VkQsriTimeline> qsriTimeline = nullptr;
};

VkResult prepareAndroidNativeBufferImage(VulkanDispatch* vk, VkDevice device,
                                         android::base::BumpPool& allocator,
                                         const VkImageCreateInfo* pCreateInfo,
                                         const VkNativeBufferANDROID* nativeBufferANDROID,
                                         const VkAllocationCallbacks* pAllocator,
                                         const VkPhysicalDeviceMemoryProperties* memProps,
                                         AndroidNativeBufferInfo* out);

void getGralloc0Usage(VkFormat format, VkImageUsageFlags imageUsage, int* usage_out);
void getGralloc1Usage(VkFormat format, VkImageUsageFlags imageUsage,
                      VkSwapchainImageUsageFlagsANDROID swapchainImageUsage,
                      uint64_t* consumerUsage_out, uint64_t* producerUsage_out);

VkResult setAndroidNativeImageSemaphoreSignaled(VulkanDispatch* vk, VkDevice device,
                                                VkQueue defaultQueue,
                                                uint32_t defaultQueueFamilyIndex,
                                                android::base::Lock* defaultQueueLock,
                                                VkSemaphore semaphore, VkFence fence,
                                                AndroidNativeBufferInfo* anbInfo);

VkResult syncImageToColorBuffer(VulkanDispatch* vk, uint32_t queueFamilyIndex, VkQueue queue,
                                android::base::Lock* queueLock, uint32_t waitSemaphoreCount,
                                const VkSemaphore* pWaitSemaphores, int* pNativeFenceFd,
                                std::shared_ptr<AndroidNativeBufferInfo> anbInfo);

}  // namespace vk
}  // namespace gfxstream
