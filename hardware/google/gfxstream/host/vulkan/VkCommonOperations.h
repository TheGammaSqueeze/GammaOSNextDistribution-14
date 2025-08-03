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

#include <GLES2/gl2.h>
#include <vulkan/vulkan.h>

#include <atomic>
#include <functional>
#include <memory>
#include <unordered_map>
#include <unordered_set>
#include <vector>

#include "BorrowedImageVk.h"
#include "CompositorVk.h"
#include "DebugUtilsHelper.h"
#include "DisplayVk.h"
#include "FrameworkFormats.h"
#include "aemu/base/ManagedDescriptor.hpp"
#include "aemu/base/Optional.h"
#include "aemu/base/synchronization/Lock.h"
#include "goldfish_vk_private_defs.h"
#include "utils/GfxApiLogger.h"
#include "utils/RenderDoc.h"

namespace gfxstream {
namespace vk {

struct VulkanDispatch;

// Returns a consistent answer for which memory type index is best for staging
// memory. This is not the simplest thing in the world because even if a memory
// type index is host visible, that doesn't mean a VkBuffer is allowed to be
// associated with it.
bool getStagingMemoryTypeIndex(VulkanDispatch* vk, VkDevice device,
                               const VkPhysicalDeviceMemoryProperties* memProps,
                               uint32_t* typeIndex);

#ifdef _WIN32
typedef void* HANDLE;
#endif

#if defined(_WIN32)
// External memory objects are HANDLE on Windows
typedef HANDLE VK_EXT_MEMORY_HANDLE;
// corresponds to INVALID_HANDLE_VALUE
#define VK_EXT_MEMORY_HANDLE_INVALID (VK_EXT_MEMORY_HANDLE)(uintptr_t)(-1)
#define VK_EXT_MEMORY_HANDLE_TYPE_BIT VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_WIN32_BIT
#elif defined(__QNX__)
#include <screen/screen.h>
// External memory objects are screen_buffer_t handles on QNX
typedef screen_buffer_t VK_EXT_MEMORY_HANDLE;
#define VK_EXT_MEMORY_HANDLE_INVALID (VK_EXT_MEMORY_HANDLE) nullptr
#define VK_EXT_MEMORY_HANDLE_TYPE_BIT VK_EXTERNAL_MEMORY_HANDLE_TYPE_SCREEN_BUFFER_BIT_QNX
#else
// External memory objects are fd's on other POSIX systems
typedef int VK_EXT_MEMORY_HANDLE;
#define VK_EXT_MEMORY_HANDLE_INVALID (-1)
#define VK_EXT_MEMORY_HANDLE_TYPE_BIT VK_EXTERNAL_MEMORY_HANDLE_TYPE_OPAQUE_FD_BIT
#endif

VK_EXT_MEMORY_HANDLE dupExternalMemory(VK_EXT_MEMORY_HANDLE);

enum class AstcEmulationMode {
    Disabled,  // No ASTC emulation (ie: ASTC not supported unless the GPU supports it natively)
    Cpu,       // Decompress ASTC textures on the CPU
    Gpu,       // Decompress ASTC textures on the GPU
};

// Global state that holds a global Vulkan instance along with globally
// exported memory allocations + images. This is in order to service things
// like AndroidHardwareBuffer/FuchsiaImagePipeHandle. Each such allocation is
// associated with a ColorBuffer handle, and depending on host-side support for
// GL_EXT_memory_object, also be able to zero-copy render into and readback
// with the traditional GL pipeline.
struct VkEmulation {
    // Whether initialization succeeded.
    bool live = false;

    // Whether to use deferred command submission.
    bool useDeferredCommands = false;

    // Whether to fuse memory requirements getting with resource creation.
    bool useCreateResourcesWithRequirements = false;

    // RenderDoc integration for guest VkInstances.
    std::unique_ptr<emugl::RenderDocWithMultipleVkInstances> guestRenderDoc = nullptr;

    // Whether to use ASTC emulation. Our current ASTC decoder implementation may lead to device
    // lost on certain device on Windows.
    AstcEmulationMode astcLdrEmulationMode = AstcEmulationMode::Disabled;

    // Whether to use ETC2 emulation.
    bool enableEtc2Emulation = false;

    // Whether to use Ycbcr emulation. If this feature is turned on, Ycbcr request will always use
    // the emulation path regardless of whether the host Vulkan driver actually supports Ycbcr
    // conversion or not.
    bool enableYcbcrEmulation = false;

    bool guestUsesAngle = false;

    bool useDedicatedAllocations = false;

    // Instance and device for creating the system-wide shareable objects.
    VkInstance instance = VK_NULL_HANDLE;
    VkPhysicalDevice physdev = VK_NULL_HANDLE;
    VkDevice device = VK_NULL_HANDLE;

    // Physical device index
    uint32_t physicalDeviceIndex = 0;

    // Global, instance and device dispatch tables.
    VulkanDispatch* gvk = nullptr;
    VulkanDispatch* ivk = nullptr;
    VulkanDispatch* dvk = nullptr;

    bool instanceSupportsExternalMemoryCapabilities = false;
    bool instanceSupportsExternalSemaphoreCapabilities = false;
    bool instanceSupportsSurface = false;
    PFN_vkGetPhysicalDeviceImageFormatProperties2KHR getImageFormatProperties2Func = nullptr;
    PFN_vkGetPhysicalDeviceProperties2KHR getPhysicalDeviceProperties2Func = nullptr;
    PFN_vkGetPhysicalDeviceFeatures2 getPhysicalDeviceFeatures2Func = nullptr;

#if defined(__APPLE__) && defined(VK_MVK_moltenvk)
    bool instanceSupportsMoltenVK = false;
    PFN_vkSetMTLTextureMVK setMTLTextureFunc = nullptr;
    PFN_vkGetMTLTextureMVK getMTLTextureFunc = nullptr;
#endif

    bool debugUtilsAvailableAndRequested = false;
    DebugUtilsHelper debugUtilsHelper = DebugUtilsHelper::withUtilsDisabled();

    // Queue, command pool, and command buffer
    // for running commands to sync stuff system-wide.
    // TODO(b/197362803): Encapsulate host side VkQueue and the lock.
    VkQueue queue = VK_NULL_HANDLE;
    std::shared_ptr<android::base::Lock> queueLock = nullptr;
    uint32_t queueFamilyIndex = 0;
    VkCommandPool commandPool = VK_NULL_HANDLE;
    VkCommandBuffer commandBuffer = VK_NULL_HANDLE;
    VkFence commandBufferFence = VK_NULL_HANDLE;

    struct ImageSupportInfo {
        // Input parameters
        VkFormat format;
        VkImageType type;
        VkImageTiling tiling;
        VkImageUsageFlags usageFlags;
        VkImageCreateFlags createFlags;

        // Output parameters
        bool supported = false;
        bool supportsExternalMemory = false;
        bool requiresDedicatedAllocation = false;

        // Keep the raw output around.
        VkFormatProperties2 formatProps2;
        VkImageFormatProperties2 imageFormatProps2;
        VkExternalImageFormatProperties extFormatProps;

        // Populated later when device is available.
        uint32_t memoryTypeBits = 0;
        bool memoryTypeBitsKnown = false;
    };

    std::vector<ImageSupportInfo> imageSupportInfo;

    struct DeviceSupportInfo {
        bool hasGraphicsQueueFamily = false;
        bool hasComputeQueueFamily = false;
        bool supportsExternalMemoryImport = false;
        bool supportsExternalMemoryExport = false;
        bool supportsIdProperties = false;
        bool supportsDriverProperties = false;
        bool hasSamplerYcbcrConversionExtension = false;
        bool supportsSamplerYcbcrConversion = false;
        bool glInteropSupported = false;

        std::vector<VkExtensionProperties> extensions;

        std::vector<uint32_t> graphicsQueueFamilyIndices;
        std::vector<uint32_t> computeQueueFamilyIndices;

        VkPhysicalDeviceProperties physdevProps;
        VkPhysicalDeviceMemoryProperties memProps;
        VkPhysicalDeviceIDPropertiesKHR idProps;

        std::string driverVendor;
        std::string driverVersion;

        PFN_vkGetImageMemoryRequirements2KHR getImageMemoryRequirements2Func = nullptr;
        PFN_vkGetBufferMemoryRequirements2KHR getBufferMemoryRequirements2Func = nullptr;

#ifdef _WIN32
        PFN_vkGetMemoryWin32HandleKHR getMemoryHandleFunc = nullptr;
#else
        PFN_vkGetMemoryFdKHR getMemoryHandleFunc = nullptr;
#endif
    };

    struct ExternalMemoryInfo {
        // Input fields
        VkDeviceSize size;
        uint32_t typeIndex;

        // Output fields
        uint32_t id = 0;
        VkDeviceMemory memory = VK_NULL_HANDLE;

        // host-mapping fields
        // host virtual address (hva).
        void* mappedPtr = nullptr;
        // host virtual address, aligned to 4KB page.
        void* pageAlignedHva = nullptr;
        // the offset of |mappedPtr| off its memory page.
        uint32_t pageOffset = 0u;
        // the offset set in |vkBindImageMemory| or |vkBindBufferMemory|.
        uint32_t bindOffset = 0u;
        // the size of all the pages the mmeory uses.
        size_t sizeToPage = 0u;
        // guest physical address.
        uintptr_t gpa = 0u;

        VK_EXT_MEMORY_HANDLE externalHandle = VK_EXT_MEMORY_HANDLE_INVALID;

        bool dedicatedAllocation = false;
    };

    // 128 mb staging buffer (really, just a few 4K frames or one 4k HDR frame)
    // ought to be big enough for anybody!
    static constexpr VkDeviceSize kDefaultStagingBufferSize = 128ULL * 1048576ULL;

    struct StagingBufferInfo {
        // TODO: Don't actually use this as external memory until host visible
        // external is supported on all platforms
        ExternalMemoryInfo memory;
        VkBuffer buffer = VK_NULL_HANDLE;
        VkDeviceSize size = kDefaultStagingBufferSize;
    };

    enum class VulkanMode {
        // Default: ColorBuffers can still be used with the existing GL-based
        // API.  Synchronization with (if it exists) Vulkan images happens on
        // every one of the GL-based API calls:
        //
        // rcReadColorBuffer
        // rcUpdateColorBuffer
        // rcBindTexture
        // rcBindRenderbuffer
        // rcFlushWindowColorBuffer
        //
        // either through explicit CPU copies or implicit in the host driver
        // if OpenGL interop is supported.
        //
        // When images are posted (rcFBPost),
        // eglSwapBuffers is used, even if that requires a CPU readback.

        Default = 0,

        // VulkanOnly: It is assumed that the guest interacts entirely with
        // the underlying Vulkan image in the guest and does not use the
        // GL-based API.  This means we can assume those APIs are not called:
        //
        // rcReadColorBuffer
        // rcUpdateColorBuffer
        // rcBindTexture
        // rcBindRenderbuffer
        // rcFlushWindowColorBuffer
        //
        // and thus we skip a lot of GL/Vk synchronization.
        //
        // When images are posted, eglSwapBuffers is only used if OpenGL
        // interop is supported. If OpenGL interop is not supported, then we
        // use a host platform-specific Vulkan swapchain to display the
        // results.

        VulkanOnly = 1,
    };
    struct ColorBufferInfo {
        ExternalMemoryInfo memory;

        uint32_t handle;

        /* Set in create(), before initialize() */
        uint32_t width;
        uint32_t height;
        GLenum internalFormat;
        uint32_t memoryProperty;
        int frameworkFormat;
        int frameworkStride;
        bool initialized = false;

        VkImage image = VK_NULL_HANDLE;
        VkImageView imageView = VK_NULL_HANDLE;
        VkImageCreateInfo imageCreateInfoShallow = {};
        VkMemoryRequirements memReqs;

        VkImageLayout currentLayout = VK_IMAGE_LAYOUT_UNDEFINED;
        uint32_t currentQueueFamilyIndex = VK_QUEUE_FAMILY_EXTERNAL;

        bool glExported = false;

        VulkanMode vulkanMode = VulkanMode::Default;

        MTLTextureRef mtlTexture = nullptr;
    };

    struct BufferInfo {
        ExternalMemoryInfo memory;
        uint32_t handle;

        VkDeviceSize size;
        VkBufferCreateFlags createFlags;
        VkBufferUsageFlags usageFlags;
        VkSharingMode sharingMode;

        VkBuffer buffer = VK_NULL_HANDLE;
        VkMemoryRequirements memReqs;

        bool glExported = false;
        VulkanMode vulkanMode = VulkanMode::Default;
        MTLBufferRef mtlBuffer = nullptr;
    };

    // Track what is supported on whatever device was selected.
    DeviceSupportInfo deviceInfo;

    // Track additional vulkan diagnostics
    uint32_t vulkanInstanceVersion;
    std::vector<VkExtensionProperties> instanceExtensions;

    // A single staging buffer to perform most transfers to/from OpenGL on the
    // host. It is shareable across instances. The memory is shareable but the
    // buffer is not; other users need to create buffers that
    // bind to imported versions of the memory.
    StagingBufferInfo staging;

    // ColorBuffers are intended to back the guest's shareable images.
    // For example:
    // Android: gralloc
    // Fuchsia: ImagePipeHandle
    // Linux: dmabuf
    std::unordered_map<uint32_t, ColorBufferInfo> colorBuffers;

    // Buffers are intended to back the guest's shareable Vulkan buffers.
    std::unordered_map<uint32_t, BufferInfo> buffers;

    // In order to support VK_KHR_external_memory_(fd|win32) we need also to
    // support the concept of plain external memories that are just memory and
    // not necessarily images. These are then intended to pass through to the
    // guest in some way, with 1:1 mapping between guest and host external
    // memory handles.
    std::unordered_map<uint32_t, ExternalMemoryInfo> externalMemories;

    // The host keeps a set of occupied guest memory addresses to avoid a
    // host memory address mapped to guest twice.
    std::unordered_set<uint64_t> occupiedGpas;

    // We can also consider using a single external memory object to back all
    // host visible allocations in the guest. This would save memory, but we
    // would also need to automatically add
    // VkExternalMemory(Image|Buffer)CreateInfo, or if it is already there, OR
    // it with the handle types on the host.
    // A rough sketch: Some memories/images/buffers in the guest
    // are backed by host visible memory:
    // There is already a virtual memory type for those things in the current
    // implementation. The guest doesn't know whether the pointer or the
    // VkDeviceMemory object is backed by host external or non external.
    // TODO: are all possible buffer / image usages compatible with
    // external backing?
    // TODO: try switching to this
    ExternalMemoryInfo virtualHostVisibleHeap;

    // Every command buffer in the pool is associated with a VkFence which is
    // signaled only if the command buffer completes.
    std::vector<std::tuple<VkCommandBuffer, VkFence>> transferQueueCommandBufferPool;

    std::unique_ptr<CompositorVk> compositorVk;

    // The implementation for Vulkan native swapchain. Only initialized in initVkEmulationFeatures
    // if useVulkanNativeSwapchain is set.
    std::unique_ptr<DisplayVk> displayVk;

    // The host memory type index that will be used to create an emulated memory type specifically
    // for AHardwareBuffers/ColorBuffers so that the host can control which memory flags are
    // exposed to the guest (i.e. hide VK_MEMORY_PROPERTY_HOST_VISIBLE_BIT from the guest).
    std::optional<uint32_t> representativeColorBufferMemoryTypeIndex;
};

VkEmulation* createGlobalVkEmulation(VulkanDispatch* vk, bool useVulkanNativeSwapchain);

struct VkEmulationFeatures {
    bool glInteropSupported = false;
    bool deferredCommands = false;
    bool createResourceWithRequirements = false;
    bool useVulkanComposition = false;
    bool useVulkanNativeSwapchain = false;
    std::unique_ptr<emugl::RenderDocWithMultipleVkInstances> guestRenderDoc = nullptr;
    AstcEmulationMode astcLdrEmulationMode = AstcEmulationMode::Disabled;
    bool enableEtc2Emulation = false;
    bool enableYcbcrEmulation = false;
    bool guestUsesAngle = false;
    bool useDedicatedAllocations = false;
};
void initVkEmulationFeatures(std::unique_ptr<VkEmulationFeatures>);

VkEmulation* getGlobalVkEmulation();
void teardownGlobalVkEmulation();

std::unique_ptr<gfxstream::DisplaySurface> createDisplaySurface(FBNativeWindowType window,
                                                                uint32_t width, uint32_t height);

bool allocExternalMemory(
    VulkanDispatch* vk, VkEmulation::ExternalMemoryInfo* info, bool actuallyExternal = true,
    android::base::Optional<uint64_t> deviceAlignment = android::base::kNullopt,
    android::base::Optional<VkBuffer> bufferForDedicatedAllocation = android::base::kNullopt,
    android::base::Optional<VkImage> imageForDedicatedAllocation = android::base::kNullopt);
void freeExternalMemoryLocked(VulkanDispatch* vk, VkEmulation::ExternalMemoryInfo* info);

bool importExternalMemory(VulkanDispatch* vk, VkDevice targetDevice,
                          const VkEmulation::ExternalMemoryInfo* info, VkDeviceMemory* out);
bool importExternalMemoryDedicatedImage(VulkanDispatch* vk, VkDevice targetDevice,
                                        const VkEmulation::ExternalMemoryInfo* info, VkImage image,
                                        VkDeviceMemory* out);

// ColorBuffer operations

bool isColorBufferExportedToGl(uint32_t colorBufferHandle, bool* exported);

bool getColorBufferAllocationInfo(uint32_t colorBufferHandle, VkDeviceSize* outSize,
                                  uint32_t* outMemoryTypeIndex, bool* outMemoryIsDedicatedAlloc,
                                  void** outMappedPtr);

std::unique_ptr<VkImageCreateInfo> generateColorBufferVkImageCreateInfo(VkFormat format,
                                                                        uint32_t width,
                                                                        uint32_t height,
                                                                        VkImageTiling tiling);

bool createVkColorBuffer(uint32_t width, uint32_t height, GLenum format,
                         FrameworkFormat frameworkFormat, uint32_t colorBufferHandle,
                         bool vulkanOnly, uint32_t memoryProperty);

bool teardownVkColorBuffer(uint32_t colorBufferHandle);

bool importExtMemoryHandleToVkColorBuffer(uint32_t colorBufferHandle, uint32_t type,
                                          VK_EXT_MEMORY_HANDLE extMemHandle);

VkEmulation::ColorBufferInfo getColorBufferInfo(uint32_t colorBufferHandle);
VK_EXT_MEMORY_HANDLE getColorBufferExtMemoryHandle(uint32_t colorBufferHandle);

struct VkColorBufferMemoryExport {
    android::base::ManagedDescriptor descriptor;
    uint64_t size = 0;
    bool linearTiling = false;
    bool dedicatedAllocation = false;
};
std::optional<VkColorBufferMemoryExport> exportColorBufferMemory(uint32_t colorBufferHandle);

MTLTextureRef getColorBufferMTLTexture(uint32_t colorBufferHandle);
bool setColorBufferVulkanMode(uint32_t colorBufferHandle, uint32_t vulkanMode);
int32_t mapGpaToBufferHandle(uint32_t bufferHandle, uint64_t gpa, uint64_t size = 0);

bool colorBufferNeedsUpdateBetweenGlAndVk(uint32_t colorBufferHandle);

bool readColorBufferToBytes(uint32_t colorBufferHandle, std::vector<uint8_t>* bytes);
bool readColorBufferToBytes(uint32_t colorBufferHandle, uint32_t x, uint32_t y, uint32_t w,
                            uint32_t h, void* outPixels);
bool readColorBufferToBytesLocked(uint32_t colorBufferHandle, uint32_t x, uint32_t y, uint32_t w,
                                  uint32_t h, void* outPixels);

bool updateColorBufferFromBytes(uint32_t colorBufferHandle, const std::vector<uint8_t>& bytes);
bool updateColorBufferFromBytes(uint32_t colorBufferHandle, uint32_t x, uint32_t y, uint32_t w,
                                uint32_t h, const void* pixels);

// Data buffer operations
bool getBufferAllocationInfo(uint32_t bufferHandle, VkDeviceSize* outSize,
                             uint32_t* outMemoryTypeIndex, bool* outMemoryIsDedicatedAlloc);

bool setupVkBuffer(uint64_t size, uint32_t bufferHandle, bool vulkanOnly = false,
                   uint32_t memoryProperty = 0);
bool teardownVkBuffer(uint32_t bufferHandle);
VK_EXT_MEMORY_HANDLE getBufferExtMemoryHandle(uint32_t bufferHandle);

bool readBufferToBytes(uint32_t bufferHandle, uint64_t offset, uint64_t size, void* outBytes);
bool updateBufferFromBytes(uint32_t bufferHandle, uint64_t offset, uint64_t size,
                           const void* bytes);

VkExternalMemoryHandleTypeFlags transformExternalMemoryHandleTypeFlags_tohost(
    VkExternalMemoryHandleTypeFlags bits);

VkExternalMemoryHandleTypeFlags transformExternalMemoryHandleTypeFlags_fromhost(
    VkExternalMemoryHandleTypeFlags hostBits,
    VkExternalMemoryHandleTypeFlags wantedGuestHandleType);

VkExternalMemoryProperties transformExternalMemoryProperties_tohost(
    VkExternalMemoryProperties props);

VkExternalMemoryProperties transformExternalMemoryProperties_fromhost(
    VkExternalMemoryProperties props, VkExternalMemoryHandleTypeFlags wantedGuestHandleType);

void setColorBufferCurrentLayout(uint32_t colorBufferHandle, VkImageLayout);

void releaseColorBufferForGuestUse(uint32_t colorBufferHandle);

std::unique_ptr<BorrowedImageInfoVk> borrowColorBufferForComposition(uint32_t colorBufferHandle,
                                                                     bool colorBufferIsTarget);
std::unique_ptr<BorrowedImageInfoVk> borrowColorBufferForDisplay(uint32_t colorBufferHandle);

}  // namespace vk
}  // namespace gfxstream
