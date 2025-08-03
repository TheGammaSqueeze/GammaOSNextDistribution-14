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

#include <string>

#include "GfxstreamEnd2EndTests.h"
#include "gfxstream/RutabagaLayerTestUtils.h"

namespace gfxstream {
namespace tests {
namespace {

using testing::Eq;
using testing::Ge;
using testing::IsEmpty;
using testing::IsNull;
using testing::Not;
using testing::NotNull;

class GfxstreamEnd2EndVkSnapshotImageTest : public GfxstreamEnd2EndTest {};

TEST_P(GfxstreamEnd2EndVkSnapshotImageTest, PreserveImageHandle) {
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    const uint32_t width = 32;
    const uint32_t height = 32;

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
        .usage = vkhpp::ImageUsageFlagBits::eSampled | vkhpp::ImageUsageFlagBits::eTransferDst |
                 vkhpp::ImageUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
        .samples = vkhpp::SampleCountFlagBits::e1,
    };
    auto image = device->createImageUnique(imageCreateInfo).value;

    vkhpp::MemoryRequirements imageMemoryRequirements{};
    device->getImageMemoryRequirements(*image, &imageMemoryRequirements);

    const uint32_t imageMemoryIndex = GetMemoryType(physicalDevice, imageMemoryRequirements,
                                                    vkhpp::MemoryPropertyFlagBits::eDeviceLocal);
    ASSERT_THAT(imageMemoryIndex, Not(Eq(-1)));

    const vkhpp::MemoryAllocateInfo imageMemoryAllocateInfo = {
        .allocationSize = imageMemoryRequirements.size,
        .memoryTypeIndex = imageMemoryIndex,
    };

    auto imageMemory = device->allocateMemoryUnique(imageMemoryAllocateInfo).value;
    ASSERT_THAT(imageMemory, IsValidHandle());

    SnapshotSaveAndLoad();

    ASSERT_THAT(device->bindImageMemory(*image, *imageMemory, 0), IsVkSuccess());
}

TEST_P(GfxstreamEnd2EndVkSnapshotImageTest, ImageContent) {
    static constexpr int kWidth = 256;
    static constexpr int kHeight = 256;
    static constexpr vkhpp::DeviceSize kSize = 4 * kWidth * kHeight;

    std::vector<uint8_t> srcBufferContent(kSize);
    for (size_t i = 0; i < kSize; i++) {
        srcBufferContent[i] = static_cast<uint8_t>(i & 0xff);
    }
    auto [instance, physicalDevice, device, queue, queueFamilyIndex] =
        VK_ASSERT(SetUpTypicalVkTestEnvironment());

    // Staging buffer
    const vkhpp::BufferCreateInfo bufferCreateInfo = {
        .size = static_cast<VkDeviceSize>(kSize),
        .usage = vkhpp::BufferUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
    };
    auto stagingBuffer = device->createBufferUnique(bufferCreateInfo).value;
    ASSERT_THAT(stagingBuffer, IsValidHandle());

    vkhpp::MemoryRequirements stagingBufferMemoryRequirements{};
    device->getBufferMemoryRequirements(*stagingBuffer, &stagingBufferMemoryRequirements);

    const auto stagingBufferMemoryType = GetMemoryType(
        physicalDevice, stagingBufferMemoryRequirements,
        vkhpp::MemoryPropertyFlagBits::eHostVisible | vkhpp::MemoryPropertyFlagBits::eHostCoherent);

    // Staging memory
    const vkhpp::MemoryAllocateInfo stagingBufferMemoryAllocateInfo = {
        .allocationSize = stagingBufferMemoryRequirements.size,
        .memoryTypeIndex = stagingBufferMemoryType,
    };
    auto stagingBufferMemory = device->allocateMemoryUnique(stagingBufferMemoryAllocateInfo).value;
    ASSERT_THAT(stagingBufferMemory, IsValidHandle());
    ASSERT_THAT(device->bindBufferMemory(*stagingBuffer, *stagingBufferMemory, 0), IsVkSuccess());

    // Fill memory content
    void* mapped = nullptr;
    auto mapResult =
        device->mapMemory(*stagingBufferMemory, 0, VK_WHOLE_SIZE, vkhpp::MemoryMapFlags{}, &mapped);
    ASSERT_THAT(mapResult, IsVkSuccess());
    ASSERT_THAT(mapped, NotNull());

    auto* bytes = reinterpret_cast<uint8_t*>(mapped);
    std::memcpy(bytes, srcBufferContent.data(), kSize);

    const vkhpp::MappedMemoryRange range = {
        .memory = *stagingBufferMemory,
        .offset = 0,
        .size = kSize,
    };
    device->unmapMemory(*stagingBufferMemory);

    // Image
    const vkhpp::ImageCreateInfo imageCreateInfo = {
        .pNext = nullptr,
        .imageType = vkhpp::ImageType::e2D,
        .extent.width = kWidth,
        .extent.height = kHeight,
        .extent.depth = 1,
        .mipLevels = 1,
        .arrayLayers = 1,
        .format = vkhpp::Format::eR8G8B8A8Unorm,
        .tiling = vkhpp::ImageTiling::eOptimal,
        .initialLayout = vkhpp::ImageLayout::eUndefined,
        .usage = vkhpp::ImageUsageFlagBits::eTransferDst | vkhpp::ImageUsageFlagBits::eTransferSrc,
        .sharingMode = vkhpp::SharingMode::eExclusive,
        .samples = vkhpp::SampleCountFlagBits::e1,
    };
    auto image = device->createImageUnique(imageCreateInfo).value;

    vkhpp::MemoryRequirements imageMemoryRequirements{};
    device->getImageMemoryRequirements(*image, &imageMemoryRequirements);

    const uint32_t imageMemoryIndex = GetMemoryType(physicalDevice, imageMemoryRequirements,
                                                    vkhpp::MemoryPropertyFlagBits::eDeviceLocal);
    ASSERT_THAT(imageMemoryIndex, Not(Eq(-1)));

    const vkhpp::MemoryAllocateInfo imageMemoryAllocateInfo = {
        .allocationSize = imageMemoryRequirements.size,
        .memoryTypeIndex = imageMemoryIndex,
    };

    auto imageMemory = device->allocateMemoryUnique(imageMemoryAllocateInfo).value;
    ASSERT_THAT(imageMemory, IsValidHandle());

    ASSERT_THAT(device->bindImageMemory(*image, *imageMemory, 0), IsVkSuccess());

    // Command buffer
    const vkhpp::CommandPoolCreateInfo commandPoolCreateInfo = {
        .queueFamilyIndex = queueFamilyIndex,
    };

    auto commandPool = device->createCommandPoolUnique(commandPoolCreateInfo).value;
    ASSERT_THAT(commandPool, IsValidHandle());

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

    const vkhpp::ImageMemoryBarrier barrier{
        .oldLayout = vkhpp::ImageLayout::eUndefined,
        .newLayout = vkhpp::ImageLayout::eTransferDstOptimal,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = *image,
        .subresourceRange =
            {
                .aspectMask = vkhpp::ImageAspectFlagBits::eColor,
                .levelCount = 1,
                .layerCount = 1,
            },
    };

    commandBuffer->pipelineBarrier(vkhpp::PipelineStageFlagBits::eAllCommands,
                                   vkhpp::PipelineStageFlagBits::eAllCommands,
                                   vkhpp::DependencyFlags(), nullptr, nullptr, barrier);

    const vkhpp::BufferImageCopy bufferImageCopy = {
        .imageSubresource =
            {
                .aspectMask = vkhpp::ImageAspectFlagBits::eColor,
                .layerCount = 1,
            },
        .imageExtent =
            {
                .width = kWidth,
                .height = kHeight,
                .depth = 1,
            },
    };
    commandBuffer->copyBufferToImage(*stagingBuffer, *image,
                                     vkhpp::ImageLayout::eTransferDstOptimal, 1, &bufferImageCopy);

    commandBuffer->end();

    auto transferFence = device->createFenceUnique(vkhpp::FenceCreateInfo()).value;
    ASSERT_THAT(transferFence, IsValidHandle());

    // Execute the command to copy image
    const vkhpp::SubmitInfo submitInfo = {
        .commandBufferCount = 1,
        .pCommandBuffers = &commandBuffer.get(),
    };
    queue.submit(submitInfo, *transferFence);

    auto waitResult = device->waitForFences(*transferFence, VK_TRUE, 3000000000L);
    ASSERT_THAT(waitResult, IsVkSuccess());

    // Snapshot
    SnapshotSaveAndLoad();

    // Read-back buffer
    const vkhpp::BufferCreateInfo readbackBufferCreateInfo = {
        .size = static_cast<VkDeviceSize>(kSize),
        .usage = vkhpp::BufferUsageFlagBits::eTransferDst,
        .sharingMode = vkhpp::SharingMode::eExclusive,
    };
    auto readbackBuffer = device->createBufferUnique(readbackBufferCreateInfo).value;
    ASSERT_THAT(readbackBuffer, IsValidHandle());

    vkhpp::MemoryRequirements readbackBufferMemoryRequirements{};
    device->getBufferMemoryRequirements(*readbackBuffer, &readbackBufferMemoryRequirements);

    const auto readbackBufferMemoryType = GetMemoryType(
        physicalDevice, readbackBufferMemoryRequirements,
        vkhpp::MemoryPropertyFlagBits::eHostVisible | vkhpp::MemoryPropertyFlagBits::eHostCoherent);

    // Read-back memory
    const vkhpp::MemoryAllocateInfo readbackBufferMemoryAllocateInfo = {
        .allocationSize = readbackBufferMemoryRequirements.size,
        .memoryTypeIndex = readbackBufferMemoryType,
    };
    auto readbackBufferMemory =
        device->allocateMemoryUnique(readbackBufferMemoryAllocateInfo).value;
    ASSERT_THAT(readbackBufferMemory, IsValidHandle());
    ASSERT_THAT(device->bindBufferMemory(*readbackBuffer, *readbackBufferMemory, 0), IsVkSuccess());

    auto readbackCommandBuffers =
        device->allocateCommandBuffersUnique(commandBufferAllocateInfo).value;
    ASSERT_THAT(readbackCommandBuffers, Not(IsEmpty()));
    auto readbackCommandBuffer = std::move(readbackCommandBuffers[0]);
    ASSERT_THAT(readbackCommandBuffer, IsValidHandle());

    readbackCommandBuffer->begin(commandBufferBeginInfo);
    const vkhpp::ImageMemoryBarrier readbackBarrier{
        .oldLayout = vkhpp::ImageLayout::eTransferDstOptimal,
        .newLayout = vkhpp::ImageLayout::eTransferSrcOptimal,
        .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
        .image = *image,
        .subresourceRange =
            {
                .aspectMask = vkhpp::ImageAspectFlagBits::eColor,
                .levelCount = 1,
                .layerCount = 1,
            },
    };

    readbackCommandBuffer->pipelineBarrier(
        vkhpp::PipelineStageFlagBits::eAllCommands, vkhpp::PipelineStageFlagBits::eAllCommands,
        vkhpp::DependencyFlags(), nullptr, nullptr, readbackBarrier);

    readbackCommandBuffer->copyImageToBuffer(*image, vkhpp::ImageLayout::eTransferSrcOptimal,
                                             *readbackBuffer, 1, &bufferImageCopy);
    readbackCommandBuffer->end();

    auto readbackFence = device->createFenceUnique(vkhpp::FenceCreateInfo()).value;
    ASSERT_THAT(readbackCommandBuffer, IsValidHandle());

    // Execute the command to copy image back to buffer
    const vkhpp::SubmitInfo readbackSubmitInfo = {
        .commandBufferCount = 1,
        .pCommandBuffers = &readbackCommandBuffer.get(),
    };
    queue.submit(readbackSubmitInfo, *readbackFence);

    auto readbackWaitResult = device->waitForFences(*readbackFence, VK_TRUE, 3000000000L);
    ASSERT_THAT(readbackWaitResult, IsVkSuccess());

    // Verify content
    mapResult = device->mapMemory(*readbackBufferMemory, 0, VK_WHOLE_SIZE, vkhpp::MemoryMapFlags{},
                                  &mapped);
    ASSERT_THAT(mapResult, IsVkSuccess());
    ASSERT_THAT(mapped, NotNull());
    bytes = reinterpret_cast<uint8_t*>(mapped);

    for (uint32_t i = 0; i < kSize; ++i) {
        ASSERT_THAT(bytes[i], Eq(srcBufferContent[i]));
    }
    device->unmapMemory(*readbackBufferMemory);
}

INSTANTIATE_TEST_CASE_P(GfxstreamEnd2EndTests, GfxstreamEnd2EndVkSnapshotImageTest,
                        ::testing::ValuesIn({
                            TestParams{
                                .with_gl = false,
                                .with_vk = true,
                                .with_vk_snapshot = true,
                            },
                        }),
                        &GetTestName);

}  // namespace
}  // namespace tests
}  // namespace gfxstream