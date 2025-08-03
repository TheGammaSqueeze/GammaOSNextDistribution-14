// Copyright 2024 The Android Open Source Project
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

#ifdef _WIN32
#include <malloc.h>
#endif

#include <stdlib.h>
#include <set>
#include <string>

#include "Handle.h"
#include "aemu/base/files/Stream.h"
#include "aemu/base/memory/SharedMemory.h"
#include "aemu/base/synchronization/ConditionVariable.h"
#include "aemu/base/synchronization/Lock.h"
#include "common/goldfish_vk_deepcopy.h"
#include "vulkan/VkAndroidNativeBuffer.h"
#include "vulkan/VkFormatUtils.h"
#include "vulkan/emulated_textures/CompressedImageInfo.h"

namespace gfxstream {
namespace vk {
template <class TDispatch>
class ExternalFencePool {
   public:
    ExternalFencePool(TDispatch* dispatch, VkDevice device)
        : m_vk(dispatch), mDevice(device), mMaxSize(5) {}

    ~ExternalFencePool() {
        if (!mPool.empty()) {
            GFXSTREAM_ABORT(emugl::FatalError(emugl::ABORT_REASON_OTHER))
                << "External fence pool for device " << static_cast<void*>(mDevice)
                << " destroyed but " << mPool.size() << " fences still not destroyed.";
        }
    }

    void add(VkFence fence) {
        android::base::AutoLock lock(mLock);
        mPool.push_back(fence);
        if (mPool.size() > mMaxSize) {
            INFO("External fence pool for %p has increased to size %d", mDevice, mPool.size());
            mMaxSize = mPool.size();
        }
    }

    VkFence pop(const VkFenceCreateInfo* pCreateInfo) {
        VkFence fence = VK_NULL_HANDLE;
        {
            android::base::AutoLock lock(mLock);
            auto it = std::find_if(mPool.begin(), mPool.end(), [this](const VkFence& fence) {
                VkResult status = m_vk->vkGetFenceStatus(mDevice, fence);
                if (status != VK_SUCCESS) {
                    if (status != VK_NOT_READY) {
                        VK_CHECK(status);
                    }

                    // Status is valid, but fence is not yet signaled
                    return false;
                }
                return true;
            });
            if (it == mPool.end()) {
                return VK_NULL_HANDLE;
            }

            fence = *it;
            mPool.erase(it);
        }

        if (!(pCreateInfo->flags & VK_FENCE_CREATE_SIGNALED_BIT)) {
            VK_CHECK(m_vk->vkResetFences(mDevice, 1, &fence));
        }

        return fence;
    }

    std::vector<VkFence> popAll() {
        android::base::AutoLock lock(mLock);
        std::vector<VkFence> popped = mPool;
        mPool.clear();
        return popped;
    }

   private:
    TDispatch* m_vk;
    VkDevice mDevice;
    android::base::Lock mLock;
    std::vector<VkFence> mPool;
    int mMaxSize;
};

class PrivateMemory {
public:
    PrivateMemory(size_t alignment, size_t size) {
#ifdef _WIN32
        mAddr = _aligned_malloc(size, alignment);
#else
        mAddr = aligned_alloc(alignment, size);
#endif
    }
    ~PrivateMemory() {
        if (mAddr) {
#ifdef _WIN32
            _aligned_free(mAddr);
#else
            free(mAddr);
#endif
            mAddr = nullptr;
        }
    }
    void* getAddr() {
        return mAddr;
    }
private:
    void* mAddr{nullptr};
};

// We always map the whole size on host.
// This makes it much easier to implement
// the memory map API.
struct MemoryInfo {
    // This indicates whether the VkDecoderGlobalState needs to clean up
    // and unmap the mapped memory; only the owner of the mapped memory
    // should call unmap.
    bool needUnmap = false;
    // When ptr is null, it means the VkDeviceMemory object
    // was not allocated with the HOST_VISIBLE property.
    void* ptr = nullptr;
    VkDeviceSize size;
    // GLDirectMem info
    bool directMapped = false;
    bool virtioGpuMapped = false;
    uint32_t caching = 0;
    uint64_t guestPhysAddr = 0;
    void* pageAlignedHva = nullptr;
    uint64_t sizeToPage = 0;
    uint64_t hostmemId = 0;
    VkDevice device = VK_NULL_HANDLE;
    MTLTextureRef mtlTexture = nullptr;
    uint32_t memoryIndex = 0;
    // Set if the memory is backed by shared memory.
    std::optional<android::base::SharedMemory> sharedMemory;

    std::shared_ptr<PrivateMemory> privateMemory;
    // virtio-gpu blobs
    uint64_t blobId = 0;

    // Buffer, provided via vkAllocateMemory().
    std::optional<HandleType> boundBuffer;
    // ColorBuffer, provided via vkAllocateMemory().
    std::optional<HandleType> boundColorBuffer;
};

struct InstanceInfo {
    std::vector<std::string> enabledExtensionNames;
    uint32_t apiVersion = VK_MAKE_VERSION(1, 0, 0);
    VkInstance boxed = nullptr;
    bool isAngle = false;
};

struct PhysicalDeviceInfo {
    VkPhysicalDeviceProperties props;
    VkPhysicalDeviceMemoryProperties memoryProperties;
    std::vector<VkQueueFamilyProperties> queueFamilyProperties;
    VkPhysicalDevice boxed = nullptr;
};

struct DeviceInfo {
    std::unordered_map<uint32_t, std::vector<VkQueue>> queues;
    std::vector<std::string> enabledExtensionNames;
    bool emulateTextureEtc2 = false;
    bool emulateTextureAstc = false;
    bool useAstcCpuDecompression = false;
    VkPhysicalDevice physicalDevice;
    VkDevice boxed = nullptr;
    DebugUtilsHelper debugUtilsHelper = DebugUtilsHelper::withUtilsDisabled();
    std::unique_ptr<ExternalFencePool<VulkanDispatch>> externalFencePool = nullptr;
    std::set<VkFormat> imageFormats = {};  // image formats used on this device
    std::unique_ptr<GpuDecompressionPipelineManager> decompPipelines = nullptr;

    // True if this is a compressed image that needs to be decompressed on the GPU (with our
    // compute shader)
    bool needGpuDecompression(const CompressedImageInfo& cmpInfo) {
        return ((cmpInfo.isEtc2() && emulateTextureEtc2) ||
                (cmpInfo.isAstc() && emulateTextureAstc && !useAstcCpuDecompression));
    }
    bool needEmulatedDecompression(const CompressedImageInfo& cmpInfo) {
        return ((cmpInfo.isEtc2() && emulateTextureEtc2) ||
                (cmpInfo.isAstc() && emulateTextureAstc));
    }
    bool needEmulatedDecompression(VkFormat format) {
        return (gfxstream::vk::isEtc2(format) && emulateTextureEtc2) ||
               (gfxstream::vk::isAstc(format) && emulateTextureAstc);
    }
};

struct QueueInfo {
    android::base::Lock* lock = nullptr;
    VkDevice device;
    uint32_t queueFamilyIndex;
    VkQueue boxed = nullptr;
    uint32_t sequenceNumber = 0;
};

struct BufferInfo {
    VkDevice device;
    VkDeviceMemory memory = 0;
    VkDeviceSize memoryOffset = 0;
    VkDeviceSize size;
};

struct ImageInfo {
    VkDevice device;
    VkImageCreateInfo imageCreateInfoShallow;
    std::shared_ptr<AndroidNativeBufferInfo> anbInfo;
    CompressedImageInfo cmpInfo;
    // ColorBuffer, provided via vkAllocateMemory().
    std::optional<HandleType> boundColorBuffer;
    // TODO: might need to use an array of layouts to represent each sub resource
    VkImageLayout layout = VK_IMAGE_LAYOUT_UNDEFINED;
    VkDeviceMemory memory = VK_NULL_HANDLE;
};

struct ImageViewInfo {
    VkDevice device;
    bool needEmulatedAlpha = false;

    // Color buffer, provided via vkAllocateMemory().
    std::optional<HandleType> boundColorBuffer;
};

struct SamplerInfo {
    VkDevice device;
    bool needEmulatedAlpha = false;
    VkSamplerCreateInfo createInfo = {};
    VkSampler emulatedborderSampler = VK_NULL_HANDLE;
    android::base::BumpPool pool = android::base::BumpPool(256);
    SamplerInfo() = default;
    SamplerInfo& operator=(const SamplerInfo& other) {
        deepcopy_VkSamplerCreateInfo(&pool, VK_STRUCTURE_TYPE_SAMPLER_CREATE_INFO,
                                     &other.createInfo, &createInfo);
        device = other.device;
        needEmulatedAlpha = other.needEmulatedAlpha;
        emulatedborderSampler = other.emulatedborderSampler;
        return *this;
    }
    SamplerInfo(const SamplerInfo& other) { *this = other; }
    SamplerInfo(SamplerInfo&& other) = delete;
    SamplerInfo& operator=(SamplerInfo&& other) = delete;
};

struct FenceInfo {
    VkDevice device = VK_NULL_HANDLE;
    VkFence boxed = VK_NULL_HANDLE;
    VulkanDispatch* vk = nullptr;

    android::base::StaticLock lock;
    android::base::ConditionVariable cv;

    enum class State {
        kWaitable,
        kNotWaitable,
        kWaiting,
    };
    State state = State::kNotWaitable;

    bool external = false;
};

struct SemaphoreInfo {
    VkDevice device;
    int externalHandleId = 0;
    VK_EXT_MEMORY_HANDLE externalHandle = VK_EXT_MEMORY_HANDLE_INVALID;
};

struct DescriptorSetLayoutInfo {
    VkDevice device = 0;
    VkDescriptorSetLayout boxed = 0;
    VkDescriptorSetLayoutCreateInfo createInfo;
    std::vector<VkDescriptorSetLayoutBinding> bindings;
};

struct DescriptorPoolInfo {
    VkDevice device = 0;
    VkDescriptorPool boxed = 0;
    struct PoolState {
        VkDescriptorType type;
        uint32_t descriptorCount;
        uint32_t used;
    };

    VkDescriptorPoolCreateInfo createInfo;
    uint32_t maxSets;
    uint32_t usedSets;
    std::vector<PoolState> pools;

    std::unordered_map<VkDescriptorSet, VkDescriptorSet> allocedSetsToBoxed;
    std::vector<uint64_t> poolIds;
};

struct DescriptorSetInfo {
    VkDescriptorPool pool;
    std::vector<VkDescriptorSetLayoutBinding> bindings;
};

struct ShaderModuleInfo {
    VkDevice device;
};

struct PipelineCacheInfo {
    VkDevice device;
};

struct PipelineInfo {
    VkDevice device;
};

struct RenderPassInfo {
    VkDevice device;
};

struct FramebufferInfo {
    VkDevice device;
};
}  // namespace vk
}  // namespace gfxstream
