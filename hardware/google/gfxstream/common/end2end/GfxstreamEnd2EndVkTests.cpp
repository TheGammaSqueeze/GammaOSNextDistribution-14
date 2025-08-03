// Copyright (C) 2023 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#include <log/log.h>

#include "GfxstreamEnd2EndTests.h"
#include "drm_fourcc.h"

namespace gfxstream {
namespace tests {
namespace {

using namespace std::chrono_literals;
using testing::Eq;
using testing::Ge;
using testing::IsEmpty;
using testing::IsNull;
using testing::Not;
using testing::NotNull;

template <typename DurationType>
constexpr uint64_t AsVkTimeout(DurationType duration) {
    return static_cast<uint64_t>(std::chrono::duration_cast<std::chrono::nanoseconds>(duration).count());
}

class GfxstreamEnd2EndVkTest : public GfxstreamEnd2EndTest {};

TEST_P(GfxstreamEnd2EndVkTest, Basic) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());
}

TEST_P(GfxstreamEnd2EndVkTest, ImportAHB) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    const uint32_t width = 32;
    const uint32_t height = 32;
    AHardwareBuffer* ahb = nullptr;
    ASSERT_THAT(mGralloc->allocate(width, height, DRM_FORMAT_ABGR8888, -1, &ahb), Eq(0));

    const VkNativeBufferANDROID imageNativeBufferInfo = {
        .sType = VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID,
        .handle = mGralloc->getNativeHandle(ahb),
    };

    auto vkQueueSignalReleaseImageANDROID = PFN_vkQueueSignalReleaseImageANDROID(
        device->getProcAddr("vkQueueSignalReleaseImageANDROID"));
    ASSERT_THAT(vkQueueSignalReleaseImageANDROID, NotNull());

    const vkhpp::ImageCreateInfo imageCreateInfo = {
        .pNext = &imageNativeBufferInfo,
        .imageType = vkhpp::ImageType::e2D,
        .extent.width = width,
        .extent.height = height,
        .extent.depth = 1,
        .mipLevels = 1,
        .arrayLayers = 1,
        .format = vkhpp::Format::eR8G8B8A8Unorm,
        .tiling = vkhpp::ImageTiling::eOptimal,
        .initialLayout = vkhpp::ImageLayout::eUndefined,
        .usage = vkhpp::ImageUsageFlagBits::eSampled |
                 vkhpp::ImageUsageFlagBits::eTransferDst |
                 vkhpp::ImageUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
        .samples = vkhpp::SampleCountFlagBits::e1,
    };
    auto image = device->createImageUnique(imageCreateInfo).value;

    vkhpp::MemoryRequirements imageMemoryRequirements{};
    device->getImageMemoryRequirements(*image, &imageMemoryRequirements);

    const uint32_t imageMemoryIndex =
        GetMemoryType(physicalDevice, imageMemoryRequirements, vkhpp::MemoryPropertyFlagBits::eDeviceLocal);
    ASSERT_THAT(imageMemoryIndex, Not(Eq(-1)));

    const vkhpp::MemoryAllocateInfo imageMemoryAllocateInfo = {
        .allocationSize = imageMemoryRequirements.size,
        .memoryTypeIndex = imageMemoryIndex,
    };

    auto imageMemory = device->allocateMemoryUnique(imageMemoryAllocateInfo).value;
    ASSERT_THAT(imageMemory, IsValidHandle());
    ASSERT_THAT(device->bindImageMemory(*image, *imageMemory, 0), IsVkSuccess());

    const vkhpp::BufferCreateInfo bufferCreateInfo = {
        .size = static_cast<VkDeviceSize>(12 * 1024 * 1024),
        .usage = vkhpp::BufferUsageFlagBits::eTransferDst |
                 vkhpp::BufferUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
    };
    auto stagingBuffer = device->createBufferUnique(bufferCreateInfo).value;
    ASSERT_THAT(stagingBuffer, IsValidHandle());

    vkhpp::MemoryRequirements stagingBufferMemoryRequirements{};
    device->getBufferMemoryRequirements(*stagingBuffer, &stagingBufferMemoryRequirements);

    const auto stagingBufferMemoryType =
         GetMemoryType(physicalDevice,
                       stagingBufferMemoryRequirements,
                       vkhpp::MemoryPropertyFlagBits::eHostVisible |
                       vkhpp::MemoryPropertyFlagBits::eHostCoherent);

    const vkhpp::MemoryAllocateInfo stagingBufferMemoryAllocateInfo = {
        .allocationSize = stagingBufferMemoryRequirements.size,
        .memoryTypeIndex = stagingBufferMemoryType,
    };
    auto stagingBufferMemory = device->allocateMemoryUnique(stagingBufferMemoryAllocateInfo).value;
    ASSERT_THAT(stagingBufferMemory, IsValidHandle());
    ASSERT_THAT(device->bindBufferMemory(*stagingBuffer, *stagingBufferMemory, 0), IsVkSuccess());

    const vkhpp::CommandPoolCreateInfo commandPoolCreateInfo = {
        .queueFamilyIndex = queueFamilyIndex,
    };

    auto commandPool = device->createCommandPoolUnique(commandPoolCreateInfo).value;
    ASSERT_THAT(stagingBufferMemory, IsValidHandle());

    const vkhpp::CommandBufferAllocateInfo commandBufferAllocateInfo = {
        .level = vkhpp::CommandBufferLevel::ePrimary,
        .commandPool = *commandPool,
        .commandBufferCount = 1,
    };
    auto commandBuffers = device->allocateCommandBuffersUnique(commandBufferAllocateInfo).value;
    ASSERT_THAT(commandBuffers, Not(IsEmpty()));
    auto commandBuffer = std::move(commandBuffers[0]);
    ASSERT_THAT(commandBuffer, IsValidHandle());

    const vkhpp::CommandBufferBeginInfo commandBufferBeginInfo = {
        .flags = vkhpp::CommandBufferUsageFlagBits::eOneTimeSubmit,
    };
    commandBuffer->begin(commandBufferBeginInfo);
    commandBuffer->end();

    std::vector<vkhpp::CommandBuffer> commandBufferHandles;
    commandBufferHandles.push_back(*commandBuffer);

    auto transferFence = device->createFenceUnique(vkhpp::FenceCreateInfo()).value;
    ASSERT_THAT(commandBuffer, IsValidHandle());

    const vkhpp::SubmitInfo submitInfo = {
        .commandBufferCount = static_cast<uint32_t>(commandBufferHandles.size()),
        .pCommandBuffers = commandBufferHandles.data(),
    };
    queue.submit(submitInfo, *transferFence);

    auto waitResult = device->waitForFences(*transferFence, VK_TRUE, AsVkTimeout(3s));
    ASSERT_THAT(waitResult, IsVkSuccess());

    int fence;

    auto result = vkQueueSignalReleaseImageANDROID(queue, 0, nullptr, *image, &fence);
    ASSERT_THAT(result, Eq(VK_SUCCESS));
    ASSERT_THAT(fence, Not(Eq(-1)));

    ASSERT_THAT(mSync->wait(fence, 3000), Eq(0));

    mGralloc->release(ahb);
}

TEST_P(GfxstreamEnd2EndVkTest, DeferredImportAHB) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    const uint32_t width = 32;
    const uint32_t height = 32;
    AHardwareBuffer* ahb = nullptr;
    ASSERT_THAT(mGralloc->allocate(width, height, DRM_FORMAT_ABGR8888, -1, &ahb), Eq(0));

    auto vkQueueSignalReleaseImageANDROID = PFN_vkQueueSignalReleaseImageANDROID(
        device->getProcAddr("vkQueueSignalReleaseImageANDROID"));
    ASSERT_THAT(vkQueueSignalReleaseImageANDROID, NotNull());

    const vkhpp::ImageCreateInfo imageCreateInfo = {
        .pNext = nullptr,
        .imageType = vkhpp::ImageType::e2D,
        .extent.width = width,
        .extent.height = height,
        .extent.depth = 1,
        .mipLevels = 1,
        .arrayLayers = 1,
        .format = vkhpp::Format::eR8G8B8A8Unorm,
        .tiling = vkhpp::ImageTiling::eOptimal,
        .initialLayout = vkhpp::ImageLayout::eUndefined,
        .usage = vkhpp::ImageUsageFlagBits::eSampled |
                 vkhpp::ImageUsageFlagBits::eTransferDst |
                 vkhpp::ImageUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
        .samples = vkhpp::SampleCountFlagBits::e1,
    };
    auto image = device->createImageUnique(imageCreateInfo).value;

    // NOTE: Binding the VkImage to the AHB happens after the VkImage is created.
    const VkNativeBufferANDROID imageNativeBufferInfo = {
        .sType = VK_STRUCTURE_TYPE_NATIVE_BUFFER_ANDROID,
        .handle = mGralloc->getNativeHandle(ahb),
    };

    const vkhpp::BindImageMemoryInfo imageBindMemoryInfo = {
        .pNext = &imageNativeBufferInfo,
        .image = *image,
        .memory = VK_NULL_HANDLE,
        .memoryOffset = 0,
    };
    ASSERT_THAT(device->bindImageMemory2({imageBindMemoryInfo}), IsVkSuccess());

    std::vector<vkhpp::Semaphore> semaphores;
    int fence;

    auto result = vkQueueSignalReleaseImageANDROID(queue, 0, nullptr, *image, &fence);
    ASSERT_THAT(result, Eq(VK_SUCCESS));
    ASSERT_THAT(fence, Not(Eq(-1)));

    ASSERT_THAT(mSync->wait(fence, 3000), Eq(0));

    mGralloc->release(ahb);
}

TEST_P(GfxstreamEnd2EndVkTest, HostMemory) {
    static constexpr const vkhpp::DeviceSize kSize = 16 * 1024;

    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    uint32_t hostMemoryTypeIndex = -1;
    const auto memoryProperties = physicalDevice.getMemoryProperties();
    for (uint32_t i = 0; i < memoryProperties.memoryTypeCount; i++) {
        const vkhpp::MemoryType& memoryType = memoryProperties.memoryTypes[i];
        if (memoryType.propertyFlags & vkhpp::MemoryPropertyFlagBits::eHostVisible) {
            hostMemoryTypeIndex = i;
        }
    }
    if (hostMemoryTypeIndex == -1) {
        GTEST_SKIP() << "Skipping test due to no host visible memory type.";
        return;
    }

    const vkhpp::MemoryAllocateInfo memoryAllocateInfo = {
        .allocationSize = kSize,
        .memoryTypeIndex = hostMemoryTypeIndex,
    };
    auto memory = device->allocateMemoryUnique(memoryAllocateInfo).value;
    ASSERT_THAT(memory, IsValidHandle());

    void* mapped = nullptr;

    auto mapResult = device->mapMemory(*memory, 0, VK_WHOLE_SIZE, vkhpp::MemoryMapFlags{}, &mapped);
    ASSERT_THAT(mapResult, IsVkSuccess());
    ASSERT_THAT(mapped, NotNull());

    auto* bytes = reinterpret_cast<uint8_t*>(mapped);
    std::memset(bytes, 0xFF, kSize);

    const vkhpp::MappedMemoryRange range = {
        .memory = *memory,
        .offset = 0,
        .size = kSize,
    };
    device->flushMappedMemoryRanges({range});
    device->invalidateMappedMemoryRanges({range});

    for (uint32_t i = 0; i < kSize; ++i) {
        EXPECT_THAT(bytes[i], Eq(0xFF));
    }
}

TEST_P(GfxstreamEnd2EndVkTest, GetPhysicalDeviceProperties2) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    auto props1 = physicalDevice.getProperties();
    auto props2 = physicalDevice.getProperties2();

    EXPECT_THAT(props1.vendorID, Eq(props2.properties.vendorID));
    EXPECT_THAT(props1.deviceID, Eq(props2.properties.deviceID));
}

TEST_P(GfxstreamEnd2EndVkTest, GetPhysicalDeviceFeatures2KHR) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    auto features1 = physicalDevice.getFeatures();
    auto features2 = physicalDevice.getFeatures2();
    EXPECT_THAT(features1.robustBufferAccess, Eq(features2.features.robustBufferAccess));
}

TEST_P(GfxstreamEnd2EndVkTest, GetPhysicalDeviceImageFormatProperties2KHR) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    const vkhpp::PhysicalDeviceImageFormatInfo2 imageFormatInfo = {
        .format = vkhpp::Format::eR8G8B8A8Unorm,
        .type = vkhpp::ImageType::e2D,
        .tiling = vkhpp::ImageTiling::eOptimal,
        .usage = vkhpp::ImageUsageFlagBits::eSampled,
    };
    const auto properties = VK_ASSERT_RV(physicalDevice.getImageFormatProperties2(imageFormatInfo));
    EXPECT_THAT(properties.imageFormatProperties.maxExtent.width, Ge(1));
    EXPECT_THAT(properties.imageFormatProperties.maxExtent.height, Ge(1));
    EXPECT_THAT(properties.imageFormatProperties.maxExtent.depth, Ge(1));
}

template <typename VkhppUniqueHandleType,
          typename VkhppHandleType = typename VkhppUniqueHandleType::element_type>
std::vector<VkhppHandleType> AsHandles(const std::vector<VkhppUniqueHandleType>& elements) {
    std::vector<VkhppHandleType> ret;
    ret.reserve(elements.size());
    for (const auto& e : elements) {
        ret.push_back(*e);
    }
    return ret;
}

struct DescriptorBundle {
    vkhpp::UniqueDescriptorPool descriptorPool;
    vkhpp::UniqueDescriptorSetLayout descriptorSetLayout;
    std::vector<vkhpp::UniqueDescriptorSet> descriptorSets;
};

vkhpp::Result ReallocateDescriptorBundleSets(vkhpp::Device device, uint32_t count, DescriptorBundle* bundle) {
    if (!bundle->descriptorSetLayout) {
        ALOGE("Invalid descriptor set layout.");
        return vkhpp::Result::eErrorUnknown;
    }

    const std::vector<vkhpp::DescriptorSetLayout> descriptorSetLayouts(count, *bundle->descriptorSetLayout);
    const vkhpp::DescriptorSetAllocateInfo descriptorSetAllocateInfo = {
        .descriptorPool = *bundle->descriptorPool,
        .descriptorSetCount = count,
        .pSetLayouts = descriptorSetLayouts.data(),
    };
    auto descriptorSets = VK_TRY_RV(device.allocateDescriptorSetsUnique(descriptorSetAllocateInfo));
    bundle->descriptorSets = std::move(descriptorSets);
    return vkhpp::Result::eSuccess;
}

VkExpected<DescriptorBundle> AllocateDescriptorBundle(vkhpp::Device device, uint32_t count) {
    const vkhpp::DescriptorPoolSize descriptorPoolSize = {
        .type = vkhpp::DescriptorType::eUniformBuffer,
        .descriptorCount = 1 * count,
    };
    const vkhpp::DescriptorPoolCreateInfo descriptorPoolCreateInfo = {
        .flags = vkhpp::DescriptorPoolCreateFlagBits::eFreeDescriptorSet,
        .maxSets = count,
        .poolSizeCount = 1,
        .pPoolSizes = &descriptorPoolSize,
    };
    auto descriptorPool = VK_EXPECT_RV(device.createDescriptorPoolUnique(descriptorPoolCreateInfo));

    const vkhpp::DescriptorSetLayoutBinding descriptorSetBinding = {
        .binding = 0,
        .descriptorType = vkhpp::DescriptorType::eUniformBuffer,
        .descriptorCount = 1,
        .stageFlags = vkhpp::ShaderStageFlagBits::eVertex,
    };
    const vkhpp::DescriptorSetLayoutCreateInfo descriptorSetLayoutInfo = {
        .bindingCount = 1,
        .pBindings = &descriptorSetBinding,
    };
    auto descriptorSetLayout = VK_EXPECT_RV(device.createDescriptorSetLayoutUnique(descriptorSetLayoutInfo));

    DescriptorBundle bundle = {
        .descriptorPool = std::move(descriptorPool),
        .descriptorSetLayout = std::move(descriptorSetLayout),
    };
    VK_EXPECT_RESULT(ReallocateDescriptorBundleSets(device, count, &bundle));
    return std::move(bundle);
}

// Tests creating a bunch of descriptor sets and freeing them via vkFreeDescriptorSet.
// 1. Via vkFreeDescriptorSet directly
// 2. Via vkResetDescriptorPool
// 3. Via vkDestroyDescriptorPool
// 4. Via vkResetDescriptorPool and double frees in vkFreeDescriptorSet
// 5. Via vkResetDescriptorPool and double frees in vkFreeDescriptorSet
// 4. Via vkResetDescriptorPool, creating more, and freeing vai vkFreeDescriptorSet
// (because vkFree* APIs are expected to never fail)
// https://github.com/KhronosGroup/Vulkan-Docs/issues/1070
TEST_P(GfxstreamEnd2EndVkTest, DescriptorSetAllocFree) {
    constexpr const uint32_t kNumSets = 4;

    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    auto bundle = VK_ASSERT(AllocateDescriptorBundle(*device, kNumSets));

    auto descriptorSetHandles = AsHandles(bundle.descriptorSets);
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());

    // The double free should also work
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());

    // Alloc/free again should also work
    ASSERT_THAT(ReallocateDescriptorBundleSets(*device, kNumSets, &bundle), IsVkSuccess());

    descriptorSetHandles = AsHandles(bundle.descriptorSets);
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());
}

TEST_P(GfxstreamEnd2EndVkTest, DescriptorSetAllocFreeReset) {
    constexpr const uint32_t kNumSets = 4;

    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    auto bundle = VK_ASSERT(AllocateDescriptorBundle(*device, kNumSets));

    device->resetDescriptorPool(*bundle.descriptorPool);

    // The double free should also work
    auto descriptorSetHandles = AsHandles(bundle.descriptorSets);
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());

    // Alloc/reset/free again should also work
    ASSERT_THAT(ReallocateDescriptorBundleSets(*device, kNumSets, &bundle), IsVkSuccess());

    device->resetDescriptorPool(*bundle.descriptorPool);

    descriptorSetHandles = AsHandles(bundle.descriptorSets);
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());
}

TEST_P(GfxstreamEnd2EndVkTest, DISABLED_DescriptorSetAllocFreeDestroy) {
    constexpr const uint32_t kNumSets = 4;

    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    auto bundle = VK_ASSERT(AllocateDescriptorBundle(*device, kNumSets));

    device->destroyDescriptorPool(*bundle.descriptorPool);

    // The double free should also work
    auto descriptorSetHandles = AsHandles(bundle.descriptorSets);
    EXPECT_THAT(device->freeDescriptorSets(*bundle.descriptorPool, kNumSets, descriptorSetHandles.data()), IsVkSuccess());
}

INSTANTIATE_TEST_CASE_P(GfxstreamEnd2EndTests, GfxstreamEnd2EndVkTest,
                        ::testing::ValuesIn({
                            TestParams{
                                .with_gl = false,
                                .with_vk = true,
                                .with_transport = GfxstreamTransport::kVirtioGpuAsg,
                            },
                            TestParams{
                                .with_gl = true,
                                .with_vk = true,
                                .with_transport = GfxstreamTransport::kVirtioGpuAsg,
                            },
                            TestParams{
                                .with_gl = false,
                                .with_vk = true,
                                .with_transport = GfxstreamTransport::kVirtioGpuPipe,
                            },
                            TestParams{
                                .with_gl = true,
                                .with_vk = true,
                                .with_transport = GfxstreamTransport::kVirtioGpuPipe,
                            },
                        }),
                        &GetTestName);

}  // namespace
}  // namespace tests
}  // namespace gfxstream
