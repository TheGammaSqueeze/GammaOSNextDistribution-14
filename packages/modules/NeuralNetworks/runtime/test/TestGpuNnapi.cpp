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

#include <android-base/logging.h>
#include <android-base/unique_fd.h>
#include <android/hardware_buffer.h>
#include <gtest/gtest.h>
#include <vulkan/vulkan.h>
#include <vulkan/vulkan_android.h>

#include <algorithm>
#include <cmath>
#include <cstring>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include "TestNeuralNetworksWrapper.h"

#ifndef NNTEST_ONLY_PUBLIC_API
#include "Manager.h"
#endif

namespace android::nn {
namespace {

using Type = test_wrapper::Type;
using OperandType = test_wrapper::OperandType;
using Result = test_wrapper::Result;

constexpr uint32_t kOperandSizeX = 256;
constexpr uint32_t kOperandSizeY = 256;
constexpr uint32_t kOperandLength = kOperandSizeX * kOperandSizeY;
constexpr uint32_t kNumberOfIterationsToTest = 100;
constexpr uint32_t kMaxNumberOfPrintedErrors = 10;

// This file implements a test suite that exercises a GPU -> NNAPI pipeline using AHardwareBuffer
// and sync fence. One pass of the pipeline involves the following three stages:
//
//   - GPU: Invoke the compute shader to clear the all elements in the output buffer to value "1"
//          of the corresponding element type. Because GPU may not be able to natively support
//          float16/int8/uint8 data types, we pack each data type into a 4-byte chunk as uint32_t
//          and pass to the shader. E.g., float16 will be packed as 0x3c003c00 -- float16 value
//          of "1" (0x3c00) repeated twice. The compute shader will use this 4-byte chunk to clear
//          the data in the output buffer (see CLEAR_DATA in the compute shader code).
//
//          The GPU workload will output directly to an AHardwareBuffer and export an Android sync
//          fence.
//
//   - NNAPI: Execute a broadcast ADD operation
//
//                output = ADD(input, const, act)
//
//            where "input" and "output" are of size [kOperandSizeY, kOperandSizeX], "const" and
//            "act" are model constant operands, "const" is of size [1] and value "1" of the
//            corresponding element type, "act" = 0. The ADD operation will increment each element
//            in the input tensor by 1.
//
//            The NNAPI executor takes the GPU output AHardwareBuffer as its input memory,
//            and directly outputs to another AHardwareBuffer. We use startComputeWithDependencies
//            to wait on the sync fence from the GPU workload. If supported, the NNAPI executor will
//            emit a sync fence; Otherwise, it will wait until the workload is finished.
//
//   - Check: Verify that each element in the resulting tensor is 1 + 1 = 2.
//
// We use introspection API to run the pipeline with each individual driver. Because this test is
// added in NNAPI feature level 5, we will exclude devices with a lower feature level. We expect
// that if the driver successfully prepares the model, it should finish execution without an error.
//
// The pipeline is tested with four data types: float32, float16, quant8_asymm, and
// quant8_asymm_signed. These data types are chosen to make sure that a driver is likely to
// support at least one of the data types.
//
// For each configuration, we run the pipeline for kNumberOfIterationsToTest iterations.

const std::vector<uint32_t> kComputeShader =
#include "shaders/TestGpuNnapi.comp.spv.inl"
        ;

// The expected element value in the final NNAPI output AHardwareBuffer.
constexpr uint32_t kExpectedResultInInt = 2;

// Helper templates for information related to a primary tensor data type. Only four specializations
// exists for this template: Type::TENSOR_FLOAT32, Type::TENSOR_FLOAT16, Type::TENSOR_QUANT8_ASYMM,
// and Type::TENSOR_QUANT8_ASYMM_SIGNED. Each specialization corresponds to a primary data type for
// the testing pipeline.
//
// Each template specialization defines the following fields:
//   - ElementType: The corresponding C++ type. Use sizeof(ElementType) to get the element size.
//   - kIsQuantized: Whether the data type is a quantized type or not.
//   - kClearData: The CLEAR_DATA used in the compute shader.
//   - kTolerance: The absolute tolerance used to check the computation result.
template <Type dataType>
struct TestTypeHelper;
template <>
struct TestTypeHelper<Type::TENSOR_FLOAT32> {
    using ElementType = float;
    static constexpr bool kIsQuantized = false;
    // One float32 of value (1.0) packed into uint32_t
    static constexpr uint32_t kClearData = 0x3f800000;
    static constexpr double kTolerance = 1e-6;
};
template <>
struct TestTypeHelper<Type::TENSOR_FLOAT16> {
    using ElementType = _Float16;
    static constexpr bool kIsQuantized = false;
    // Two float16 of value (1.0) packed into uint32_t
    static constexpr uint32_t kClearData = 0x3c003c00;
    static constexpr double kTolerance = 1e-3;
};
template <>
struct TestTypeHelper<Type::TENSOR_QUANT8_ASYMM> {
    using ElementType = uint8_t;
    static constexpr bool kIsQuantized = true;
    // Four uint8_t of value (1) packed into uint32_t
    static constexpr uint32_t kClearData = 0x01010101;
    static constexpr double kTolerance = 0;
};
template <>
struct TestTypeHelper<Type::TENSOR_QUANT8_ASYMM_SIGNED> {
    using ElementType = int8_t;
    static constexpr bool kIsQuantized = true;
    // Four int8_t of value (1) packed into uint32_t
    static constexpr uint32_t kClearData = 0x01010101;
    static constexpr double kTolerance = 0;
};

bool isExtensionSupported(const std::vector<VkExtensionProperties>& supportedExtensions,
                          const char* requestedExtension) {
    return std::any_of(supportedExtensions.begin(), supportedExtensions.end(),
                       [requestedExtension](const auto& extension) {
                           return strcmp(extension.extensionName, requestedExtension) == 0;
                       });
}

// Records the workgroup size and the group counts of dispatching the compute shader.
struct DispatchSize {
    uint32_t workgroupSize;
    uint32_t groupCountX;
    uint32_t groupCountY;
};

// Choose an appropriate dispatch size. We are using a square workgroup size.
template <Type dataType>
DispatchSize chooseDispatchSize(const VkPhysicalDeviceLimits& limits) {
    // Compute the number of invocations along each dimension.
    const uint32_t elementSize = sizeof(typename TestTypeHelper<dataType>::ElementType);
    const uint32_t numberOfElementsPerInvocation = sizeof(uint32_t) / elementSize;
    const uint32_t workgroupInvocationsX = kOperandSizeX / numberOfElementsPerInvocation;
    const uint32_t workgroupInvocationsY = kOperandSizeY;

    // Make sure the workgroup size does not exceed the number of invocations along the X and Y
    // dimensions.
    uint32_t workgroupSize = std::min(workgroupInvocationsX, workgroupInvocationsY);

    // Make sure the workgroup size does not exceed the device limit along the X and Y dimensions.
    workgroupSize = std::min<uint32_t>(workgroupSize, limits.maxComputeWorkGroupSize[0]);
    workgroupSize = std::min<uint32_t>(workgroupSize, limits.maxComputeWorkGroupSize[1]);

    // Make sure the total number of invocations does not exceed the device limit.
    uint32_t maxSquareWorkGroupSize =
            static_cast<uint32_t>(std::sqrt(limits.maxComputeWorkGroupInvocations));
    workgroupSize = std::min(workgroupSize, maxSquareWorkGroupSize);

    // Round down to a power of 2. This is to make sure workgroupInvocationsX and
    // workgroupInvocationsY are divisible by the workgroup size so that we don't need to apply
    // bound check in the shader.
    uint32_t power = static_cast<uint32_t>(std::log2(static_cast<float>(workgroupSize)));
    workgroupSize = 1u << power;
    CHECK(workgroupInvocationsX % workgroupSize == 0);
    CHECK(workgroupInvocationsY % workgroupSize == 0);

    return {
            .workgroupSize = workgroupSize,
            .groupCountX = workgroupInvocationsX / workgroupSize,
            .groupCountY = workgroupInvocationsY / workgroupSize,
    };
}

// Find the first memory index that satisfies the requirements
// See VkAndroidHardwareBufferPropertiesANDROID::memoryTypeBits for the semantics of
// "memoryTypeBitsRequirement"
std::optional<uint32_t> findMemoryType(const VkPhysicalDeviceMemoryProperties& properties,
                                       uint32_t memoryTypeBitsRequirement,
                                       VkDeviceSize sizeRequirement) {
    for (uint32_t memoryIndex = 0; memoryIndex < VK_MAX_MEMORY_TYPES; ++memoryIndex) {
        const uint32_t memoryTypeBits = (1 << memoryIndex);
        const bool isRequiredMemoryType = memoryTypeBitsRequirement & memoryTypeBits;
        const uint32_t heapIndex = properties.memoryTypes[memoryIndex].heapIndex;
        const bool isLargeEnough = properties.memoryHeaps[heapIndex].size >= sizeRequirement;
        if (isRequiredMemoryType && isLargeEnough) return memoryIndex;
    }

    // failed to find memory type.
    return std::nullopt;
}

void addBufferTransitionBarrier(VkCommandBuffer commandBuffer, VkBuffer buffer,
                                VkPipelineStageFlags srcStageMask,
                                VkPipelineStageFlags dstStageMask, VkAccessFlags srcAccessMask,
                                VkAccessFlags dstAccessMask, uint32_t srcQueue, uint32_t dstQueue) {
    const VkBufferMemoryBarrier bufferBarrier = {
            .sType = VK_STRUCTURE_TYPE_BUFFER_MEMORY_BARRIER,
            .pNext = nullptr,
            .srcAccessMask = srcAccessMask,
            .dstAccessMask = dstAccessMask,
            .srcQueueFamilyIndex = srcQueue,
            .dstQueueFamilyIndex = dstQueue,
            .buffer = buffer,
            .offset = 0,
            .size = VK_WHOLE_SIZE,
    };
    vkCmdPipelineBarrier(commandBuffer, srcStageMask, dstStageMask, 0, 0, nullptr, 1,
                         &bufferBarrier, 0, nullptr);
}

void allocateBlobAhwb(uint32_t size, uint64_t usage, AHardwareBuffer** outAhwb) {
    AHardwareBuffer_Desc desc = {
            .width = size,
            .height = 1u,
            .layers = 1u,
            .format = AHARDWAREBUFFER_FORMAT_BLOB,
            .usage = usage,
    };
    if (AHardwareBuffer_allocate(&desc, outAhwb) != 0) {
        GTEST_SKIP() << "Device failed to allocate Android hardware buffer";
    }
}

using NameAndDevice = std::pair<const char*, const ANeuralNetworksDevice*>;

void getNnapiDevices(std::vector<NameAndDevice>* outDevices) {
    // Get the number of available NNAPI devices
    uint32_t numDevices = 0;
    ASSERT_EQ(ANeuralNetworks_getDeviceCount(&numDevices), ANEURALNETWORKS_NO_ERROR);

    std::vector<NameAndDevice> devices;
    for (uint32_t i = 0; i < numDevices; i++) {
        // Get device
        ANeuralNetworksDevice* device;
        ASSERT_EQ(ANeuralNetworks_getDevice(/*devIndex=*/i, &device), ANEURALNETWORKS_NO_ERROR);

        // Get device name
        const char* deviceName = nullptr;
        ASSERT_EQ(ANeuralNetworksDevice_getName(device, &deviceName), ANEURALNETWORKS_NO_ERROR);

        // Check device feature level. This test is added in NNAPI feature level 5, so skip if the
        // device is of a lower feature level.
        int64_t featureLevel;
        ASSERT_EQ(ANeuralNetworksDevice_getFeatureLevel(device, &featureLevel),
                  ANEURALNETWORKS_NO_ERROR);
        if (featureLevel < ANEURALNETWORKS_FEATURE_LEVEL_5) {
            continue;
        }

        devices.emplace_back(deviceName, device);
    }
    *outDevices = std::move(devices);
}

std::vector<NameAndDevice> getNnapiDevices() {
    std::vector<NameAndDevice> devices;
    getNnapiDevices(&devices);
    return devices;
}

std::string printGpuNnapiTest(const testing::TestParamInfo<NameAndDevice>& info) {
    std::string name = info.param.first;
    // gtest test names must only contain alphanumeric characters
    std::replace_if(
            name.begin(), name.end(), [](char c) { return !std::isalnum(c); }, '_');
    return name;
}

template <Type dataType>
class VulkanComputePipeline {
   public:
    // Returns the created object on success, or nullptr on failure.
    static std::unique_ptr<VulkanComputePipeline> create(AHardwareBuffer* output) {
        auto pipeline = std::make_unique<VulkanComputePipeline>();
        pipeline->initialize(output);
        return pipeline->mIsValid ? std::move(pipeline) : nullptr;
    }

    ~VulkanComputePipeline() {
        if (mDevice != VK_NULL_HANDLE) {
            vkDestroyFence(mDevice, mFence, nullptr);
            vkDestroyPipeline(mDevice, mPipeline, nullptr);
            vkDestroyDescriptorSetLayout(mDevice, mDescriptorSetLayout, nullptr);
            vkDestroyPipelineLayout(mDevice, mPipelineLayout, nullptr);
            vkFreeMemory(mDevice, mOutputBufferMemory, nullptr);
            vkDestroyBuffer(mDevice, mOutputBuffer, nullptr);
            vkDestroyShaderModule(mDevice, mShaderModule, nullptr);
            vkDestroyCommandPool(mDevice, mCommandPool, nullptr);
            vkDestroyDescriptorPool(mDevice, mDescriptorPool, nullptr);
        }
        vkDestroyDevice(mDevice, nullptr);
        vkDestroyInstance(mInstance, nullptr);
    }

    // Returns {success, sync_fd}
    std::pair<bool, base::unique_fd> run() {
        bool success = false;
        base::unique_fd outSyncFd;
        runInternal(&success, &outSyncFd);
        return {success, std::move(outSyncFd)};
    }

   private:
    void initialize(AHardwareBuffer* output) {
        // Create instance
        const VkApplicationInfo applicationDesc = {
                .sType = VK_STRUCTURE_TYPE_APPLICATION_INFO,
                .pApplicationName = "TestGpuNnapi",
                .applicationVersion = VK_MAKE_VERSION(1, 0, 0),
                .apiVersion = VK_API_VERSION_1_1,
        };
        const VkInstanceCreateInfo instanceDesc = {
                .sType = VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
                .pApplicationInfo = &applicationDesc,
                .enabledLayerCount = 0,
                .ppEnabledLayerNames = nullptr,
                .enabledExtensionCount = 0,
                .ppEnabledExtensionNames = nullptr,
        };
        ASSERT_EQ(vkCreateInstance(&instanceDesc, nullptr, &mInstance), VK_SUCCESS);

        // Enumerate physical devices
        uint32_t numberOfDevices = 0;
        ASSERT_EQ(vkEnumeratePhysicalDevices(mInstance, &numberOfDevices, nullptr), VK_SUCCESS);
        std::vector<VkPhysicalDevice> physicalDevices(numberOfDevices);
        ASSERT_EQ(vkEnumeratePhysicalDevices(mInstance, &numberOfDevices, physicalDevices.data()),
                  VK_SUCCESS);

        // Pick the first device with a compute queue
        for (const auto& physicalDevice : physicalDevices) {
            uint32_t numberOfQueueFamilies = 0;
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &numberOfQueueFamilies,
                                                     nullptr);
            std::vector<VkQueueFamilyProperties> queueFamilies(numberOfQueueFamilies);
            vkGetPhysicalDeviceQueueFamilyProperties(physicalDevice, &numberOfQueueFamilies,
                                                     queueFamilies.data());

            uint32_t pickedQueueFamilyIndex = 0;
            bool hasComputeQueue = false;
            for (uint32_t i = 0; i < queueFamilies.size(); i++) {
                if (queueFamilies[i].queueFlags & VK_QUEUE_COMPUTE_BIT) {
                    pickedQueueFamilyIndex = i;
                    hasComputeQueue = true;
                    break;
                }
            }
            if (!hasComputeQueue) continue;
            mPhysicalDevice = physicalDevice;
            mQueueFamilyIndex = pickedQueueFamilyIndex;
            break;
        }
        if (mPhysicalDevice == VK_NULL_HANDLE) {
            GTEST_SKIP() << "No device can handle a compute queue";
        }

        // Get physical device properties
        vkGetPhysicalDeviceProperties(mPhysicalDevice, &mPhysicalDeviceProperties);
        vkGetPhysicalDeviceMemoryProperties(mPhysicalDevice, &mPhysicalDeviceMemoryProperties);

        // Check physical device version
        if (mPhysicalDeviceProperties.apiVersion < VK_API_VERSION_1_1) {
            GTEST_SKIP() << "Device API version too low";
        }

        // Check if the physical device is able to handle the compute work
        const auto dispatchSize = chooseDispatchSize<dataType>(mPhysicalDeviceProperties.limits);
        if (mPhysicalDeviceProperties.limits.maxComputeWorkGroupCount[0] <
            dispatchSize.groupCountX) {
            GTEST_SKIP() << "Device cannot handle " << dispatchSize.groupCountX
                         << " workgroups for the X dimension";
        }
        if (mPhysicalDeviceProperties.limits.maxComputeWorkGroupCount[1] <
            dispatchSize.groupCountY) {
            GTEST_SKIP() << "Device cannot handle " << dispatchSize.groupCountY
                         << " workgroups for the Y dimension";
        }

        // Enumerate device extensions
        uint32_t numberOfExtensions = 0;
        ASSERT_EQ(vkEnumerateDeviceExtensionProperties(mPhysicalDevice, nullptr,
                                                       &numberOfExtensions, nullptr),
                  VK_SUCCESS);
        std::vector<VkExtensionProperties> extensions(numberOfExtensions);
        ASSERT_EQ(vkEnumerateDeviceExtensionProperties(mPhysicalDevice, nullptr,
                                                       &numberOfExtensions, extensions.data()),
                  VK_SUCCESS);

        // Required device extensions
        std::vector<const char*> requiredDeviceExtensions = {
                // The following extensions are required to import an AHardwareBuffer to Vulkan
                VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
                VK_EXT_QUEUE_FAMILY_FOREIGN_EXTENSION_NAME,
                VK_KHR_GET_MEMORY_REQUIREMENTS_2_EXTENSION_NAME,
                VK_KHR_BIND_MEMORY_2_EXTENSION_NAME,
                VK_KHR_EXTERNAL_MEMORY_EXTENSION_NAME,
                // The following extensions are required to export a sync fence
                VK_KHR_EXTERNAL_FENCE_FD_EXTENSION_NAME,
                VK_KHR_MAINTENANCE1_EXTENSION_NAME,
        };
        for (const char* requiredDeviceExtension : requiredDeviceExtensions) {
            if (!isExtensionSupported(extensions, requiredDeviceExtension)) {
                GTEST_SKIP() << "Device extension " << requiredDeviceExtension
                             << " is not supported";
            }
        }

        // Check external memory properties
        const VkPhysicalDeviceExternalBufferInfo externalBufferInfo = {
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_BUFFER_INFO,
                .pNext = nullptr,
                .flags = 0u,
                .usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                .handleType = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID,
        };
        VkExternalBufferProperties externalBufferProperties = {
                .sType = VK_STRUCTURE_TYPE_EXTERNAL_BUFFER_PROPERTIES,
        };
        vkGetPhysicalDeviceExternalBufferProperties(mPhysicalDevice, &externalBufferInfo,
                                                    &externalBufferProperties);
        if (!(externalBufferProperties.externalMemoryProperties.externalMemoryFeatures &
              VK_EXTERNAL_MEMORY_FEATURE_IMPORTABLE_BIT)) {
            GTEST_SKIP() << "Device is not able to import Android hardware buffer";
        }
        ASSERT_FALSE(externalBufferProperties.externalMemoryProperties.externalMemoryFeatures &
                     VK_EXTERNAL_MEMORY_FEATURE_DEDICATED_ONLY_BIT);

        // Check external fence properties
        const VkPhysicalDeviceExternalFenceInfo externalFenceInfo = {
                .sType = VK_STRUCTURE_TYPE_PHYSICAL_DEVICE_EXTERNAL_FENCE_INFO,
                .pNext = nullptr,
                .handleType = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT,
        };
        VkExternalFenceProperties externalFenceProperties;
        vkGetPhysicalDeviceExternalFenceProperties(mPhysicalDevice, &externalFenceInfo,
                                                   &externalFenceProperties);
        if (!(externalFenceProperties.externalFenceFeatures &
              VK_EXTERNAL_FENCE_FEATURE_EXPORTABLE_BIT)) {
            GTEST_SKIP() << "Device is not able to export Android sync fence FD";
        }

        // Create logical device
        const float queuePriority = 1.0f;
        const VkDeviceQueueCreateInfo queueDesc = {
                .sType = VK_STRUCTURE_TYPE_DEVICE_QUEUE_CREATE_INFO,
                .queueFamilyIndex = mQueueFamilyIndex,
                .queueCount = 1,
                .pQueuePriorities = &queuePriority,
        };
        const VkDeviceCreateInfo deviceDesc = {
                .sType = VK_STRUCTURE_TYPE_DEVICE_CREATE_INFO,
                .queueCreateInfoCount = 1,
                .pQueueCreateInfos = &queueDesc,
                .enabledExtensionCount = static_cast<uint32_t>(requiredDeviceExtensions.size()),
                .ppEnabledExtensionNames = requiredDeviceExtensions.data(),
                .pEnabledFeatures = nullptr,
        };
        ASSERT_EQ(vkCreateDevice(mPhysicalDevice, &deviceDesc, nullptr, &mDevice), VK_SUCCESS);
        vkGetDeviceQueue(mDevice, mQueueFamilyIndex, 0, &mQueue);

        // Get extension function pointers
        mPfnVkGetFenceFdKHR = reinterpret_cast<PFN_vkGetFenceFdKHR>(
                vkGetDeviceProcAddr(mDevice, "vkGetFenceFdKHR"));
        ASSERT_NE(mPfnVkGetFenceFdKHR, nullptr);

        // Create descriptor pool
        const std::vector<VkDescriptorPoolSize> descriptorPoolSizes = {
                {
                        .type = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                },
        };
        const VkDescriptorPoolCreateInfo descriptorPoolCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
                .maxSets = 1,
                .poolSizeCount = static_cast<uint32_t>(descriptorPoolSizes.size()),
                .pPoolSizes = descriptorPoolSizes.data(),
        };
        ASSERT_EQ(vkCreateDescriptorPool(mDevice, &descriptorPoolCreateInfo, nullptr,
                                         &mDescriptorPool),
                  VK_SUCCESS);

        // Create descriptor set layout
        const std::vector<VkDescriptorSetLayoutBinding> descriptorsetLayoutBinding = {
                {
                        .binding = 0,  // output buffer
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .descriptorCount = 1,
                        .stageFlags = VK_SHADER_STAGE_COMPUTE_BIT,
                },

        };
        const VkDescriptorSetLayoutCreateInfo descriptorsetLayoutDesc = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
                .bindingCount = static_cast<uint32_t>(descriptorsetLayoutBinding.size()),
                .pBindings = descriptorsetLayoutBinding.data(),
        };
        ASSERT_EQ(vkCreateDescriptorSetLayout(mDevice, &descriptorsetLayoutDesc, nullptr,
                                              &mDescriptorSetLayout),
                  VK_SUCCESS);

        // Allocate descriptor set
        const VkDescriptorSetAllocateInfo descriptorSetAllocateInfo = {
                .sType = VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
                .descriptorPool = mDescriptorPool,
                .descriptorSetCount = 1,
                .pSetLayouts = &mDescriptorSetLayout,
        };
        ASSERT_EQ(vkAllocateDescriptorSets(mDevice, &descriptorSetAllocateInfo, &mDescriptorSet),
                  VK_SUCCESS);

        // Check the output AHardwareBuffer format and usage bits
        AHardwareBuffer_Desc desc;
        AHardwareBuffer_describe(output, &desc);
        ASSERT_EQ(desc.format, AHARDWAREBUFFER_FORMAT_BLOB);
        ASSERT_TRUE(desc.usage & AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER);

        // Get AHardwareBuffer properties
        VkAndroidHardwareBufferPropertiesANDROID properties = {
                .sType = VK_STRUCTURE_TYPE_ANDROID_HARDWARE_BUFFER_PROPERTIES_ANDROID,
                .pNext = nullptr,
        };
        ASSERT_EQ(vkGetAndroidHardwareBufferPropertiesANDROID(mDevice, output, &properties),
                  VK_SUCCESS);

        // Create the output buffer with AHardwareBuffer memory
        const VkExternalMemoryBufferCreateInfo externalMemoryBufferCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_EXTERNAL_MEMORY_BUFFER_CREATE_INFO,
                .pNext = nullptr,
                .handleTypes = VK_EXTERNAL_MEMORY_HANDLE_TYPE_ANDROID_HARDWARE_BUFFER_BIT_ANDROID,
        };
        const VkBufferCreateInfo bufferCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_BUFFER_CREATE_INFO,
                .pNext = &externalMemoryBufferCreateInfo,
                .flags = 0u,
                .size = desc.width,
                .usage = VK_BUFFER_USAGE_STORAGE_BUFFER_BIT,
                .sharingMode = VK_SHARING_MODE_EXCLUSIVE,
                .queueFamilyIndexCount = 0u,
                .pQueueFamilyIndices = nullptr,
        };
        ASSERT_EQ(vkCreateBuffer(mDevice, &bufferCreateInfo, nullptr, &mOutputBuffer), VK_SUCCESS);

        // Find a proper memory type
        const auto maybeMemoryTypeIndex =
                findMemoryType(mPhysicalDeviceMemoryProperties, properties.memoryTypeBits,
                               properties.allocationSize);
        if (!maybeMemoryTypeIndex.has_value()) {
            GTEST_SKIP() << "None of the memory type is suitable for allocation";
        }

        // Import the AHardwareBuffer memory
        const VkImportAndroidHardwareBufferInfoANDROID importMemoryAllocateInfo = {
                .sType = VK_STRUCTURE_TYPE_IMPORT_ANDROID_HARDWARE_BUFFER_INFO_ANDROID,
                .pNext = nullptr,
                .buffer = output,
        };
        const VkMemoryAllocateInfo memoryAllocInfo = {
                .sType = VK_STRUCTURE_TYPE_MEMORY_ALLOCATE_INFO,
                .pNext = &importMemoryAllocateInfo,
                .allocationSize = properties.allocationSize,
                .memoryTypeIndex = maybeMemoryTypeIndex.value(),
        };
        const auto allocationResult =
                vkAllocateMemory(mDevice, &memoryAllocInfo, nullptr, &mOutputBufferMemory);
        // Memory allocation may fail if the size exceeds the upper limit of a single allocation
        // that the platform supports
        if (allocationResult == VK_ERROR_OUT_OF_DEVICE_MEMORY) {
            GTEST_SKIP() << "Unable to allocate device memory of " << properties.allocationSize
                         << " bytes";
        }
        ASSERT_EQ(allocationResult, VK_SUCCESS);

        // Bind the memory with the buffer
        ASSERT_EQ(vkBindBufferMemory(mDevice, mOutputBuffer, mOutputBufferMemory, 0), VK_SUCCESS);

        // Update the descriptor sets
        const VkDescriptorBufferInfo outputBufferDesc = {
                .buffer = mOutputBuffer,
                .offset = 0,
                .range = VK_WHOLE_SIZE,
        };
        const std::vector<VkWriteDescriptorSet> writeDst = {
                {
                        .sType = VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
                        .pNext = nullptr,
                        .dstSet = mDescriptorSet,
                        .dstBinding = 0,  // output buffer
                        .dstArrayElement = 0,
                        .descriptorCount = 1,
                        .descriptorType = VK_DESCRIPTOR_TYPE_STORAGE_BUFFER,
                        .pImageInfo = nullptr,
                        .pBufferInfo = &outputBufferDesc,
                        .pTexelBufferView = nullptr,
                },
        };
        vkUpdateDescriptorSets(mDevice, writeDst.size(), writeDst.data(), 0, nullptr);

        // Create shader module
        const VkShaderModuleCreateInfo shaderDesc = {
                .sType = VK_STRUCTURE_TYPE_SHADER_MODULE_CREATE_INFO,
                .flags = 0,
                .codeSize = kComputeShader.size() * sizeof(uint32_t),
                .pCode = kComputeShader.data(),
        };
        ASSERT_EQ(vkCreateShaderModule(mDevice, &shaderDesc, nullptr, &mShaderModule), VK_SUCCESS);

        // Create pipeline layout
        const VkPipelineLayoutCreateInfo layoutDesc = {
                .sType = VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
                .setLayoutCount = 1,
                .pSetLayouts = &mDescriptorSetLayout,
                .pushConstantRangeCount = 0,
                .pPushConstantRanges = nullptr,
        };
        ASSERT_EQ(vkCreatePipelineLayout(mDevice, &layoutDesc, nullptr, &mPipelineLayout),
                  VK_SUCCESS);

        // Create compute pipeline
        const uint32_t specializationData[] = {
                dispatchSize.workgroupSize,            // local_size_x
                dispatchSize.workgroupSize,            // local_size_y
                TestTypeHelper<dataType>::kClearData,  // CLEAR_DATA
        };
        const std::vector<VkSpecializationMapEntry> specializationMap = {
                // {constantID, offset, size}
                {0, 0 * sizeof(uint32_t), sizeof(uint32_t)},
                {1, 1 * sizeof(uint32_t), sizeof(uint32_t)},
                {2, 2 * sizeof(uint32_t), sizeof(uint32_t)},
        };
        const VkSpecializationInfo specializationInfo = {
                .mapEntryCount = static_cast<uint32_t>(specializationMap.size()),
                .pMapEntries = specializationMap.data(),
                .dataSize = sizeof(specializationData),
                .pData = specializationData,
        };
        const VkComputePipelineCreateInfo pipelineDesc = {
                .sType = VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
                .stage =
                        {
                                .sType = VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
                                .stage = VK_SHADER_STAGE_COMPUTE_BIT,
                                .module = mShaderModule,
                                .pName = "main",
                                .pSpecializationInfo = &specializationInfo,
                        },
                .layout = mPipelineLayout,
        };
        ASSERT_EQ(vkCreateComputePipelines(mDevice, VK_NULL_HANDLE, 1, &pipelineDesc, nullptr,
                                           &mPipeline),
                  VK_SUCCESS);

        // Create command pool
        const VkCommandPoolCreateInfo cmdpoolDesc = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
                .flags = 0u,
                .queueFamilyIndex = mQueueFamilyIndex,
        };
        ASSERT_EQ(vkCreateCommandPool(mDevice, &cmdpoolDesc, nullptr, &mCommandPool), VK_SUCCESS);

        // Create a command buffer
        const VkCommandBufferAllocateInfo cmdBufferCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
                .pNext = nullptr,
                .commandPool = mCommandPool,
                .level = VK_COMMAND_BUFFER_LEVEL_PRIMARY,
                .commandBufferCount = 1,
        };
        ASSERT_EQ(vkAllocateCommandBuffers(mDevice, &cmdBufferCreateInfo, &mCommandBuffer),
                  VK_SUCCESS);

        // Record command buffer
        const VkCommandBufferBeginInfo commandBufferBeginInfo = {
                .sType = VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
                .pNext = nullptr,
                .flags = 0,
                .pInheritanceInfo = nullptr,
        };
        ASSERT_EQ(vkBeginCommandBuffer(mCommandBuffer, &commandBufferBeginInfo), VK_SUCCESS);

        // Buffer barrier to acquire the ownership of the output buffer
        addBufferTransitionBarrier(mCommandBuffer, mOutputBuffer, VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                                   VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT, 0,
                                   VK_ACCESS_SHADER_WRITE_BIT, VK_QUEUE_FAMILY_FOREIGN_EXT,
                                   mQueueFamilyIndex);

        // Setup resources
        vkCmdBindPipeline(mCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, mPipeline);
        vkCmdBindDescriptorSets(mCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, mPipelineLayout, 0,
                                1, &mDescriptorSet, 0, nullptr);

        // Dispatch compute
        vkCmdDispatch(mCommandBuffer, dispatchSize.groupCountX, dispatchSize.groupCountY, 1);

        // Buffer barrier to release the ownership of the output buffer
        addBufferTransitionBarrier(mCommandBuffer, mOutputBuffer,
                                   VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                                   VK_PIPELINE_STAGE_BOTTOM_OF_PIPE_BIT, VK_ACCESS_SHADER_WRITE_BIT,
                                   0, mQueueFamilyIndex, VK_QUEUE_FAMILY_FOREIGN_EXT);

        // Finish recording the command buffer
        ASSERT_EQ(vkEndCommandBuffer(mCommandBuffer), VK_SUCCESS);

        // Create fence
        const VkExportFenceCreateInfo exportFenceCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_EXPORT_FENCE_CREATE_INFO,
                .pNext = nullptr,
                .handleTypes = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT,
        };
        const VkFenceCreateInfo fenceCreateInfo = {
                .sType = VK_STRUCTURE_TYPE_FENCE_CREATE_INFO,
                .pNext = &exportFenceCreateInfo,
                .flags = 0,
        };
        ASSERT_EQ(vkCreateFence(mDevice, &fenceCreateInfo, nullptr, &mFence), VK_SUCCESS);

        mIsValid = true;
    }

    void runInternal(bool* outSuccess, base::unique_fd* outSyncFd) {
        *outSuccess = false;

        // Submit to queue
        const VkSubmitInfo submitInfo = {
                .sType = VK_STRUCTURE_TYPE_SUBMIT_INFO,
                .waitSemaphoreCount = 0,
                .pWaitSemaphores = nullptr,
                .pWaitDstStageMask = nullptr,
                .commandBufferCount = 1,
                .pCommandBuffers = &mCommandBuffer,
                .signalSemaphoreCount = 0,
                .pSignalSemaphores = nullptr,
        };
        ASSERT_EQ(vkResetFences(mDevice, 1, &mFence), VK_SUCCESS);
        ASSERT_EQ(vkQueueSubmit(mQueue, 1, &submitInfo, mFence), VK_SUCCESS);

        // Export a Android sync fence FD
        int syncFd = -1;
        const VkFenceGetFdInfoKHR fenceGetFdInfo = {
                .sType = VK_STRUCTURE_TYPE_FENCE_GET_FD_INFO_KHR,
                .pNext = nullptr,
                .fence = mFence,
                .handleType = VK_EXTERNAL_FENCE_HANDLE_TYPE_SYNC_FD_BIT,
        };
        ASSERT_EQ(mPfnVkGetFenceFdKHR(mDevice, &fenceGetFdInfo, &syncFd), VK_SUCCESS);
        *outSyncFd = base::unique_fd(syncFd);

        *outSuccess = true;
    }

    // Instance
    VkInstance mInstance = VK_NULL_HANDLE;

    // Physical device and queue family
    VkPhysicalDevice mPhysicalDevice = VK_NULL_HANDLE;
    VkPhysicalDeviceProperties mPhysicalDeviceProperties{};
    VkPhysicalDeviceMemoryProperties mPhysicalDeviceMemoryProperties{};
    uint32_t mQueueFamilyIndex = 0;

    // Logical device and queue
    VkDevice mDevice = VK_NULL_HANDLE;
    VkQueue mQueue = VK_NULL_HANDLE;

    // Extension functions
    PFN_vkGetFenceFdKHR mPfnVkGetFenceFdKHR = nullptr;

    // Resource descriptors
    VkDescriptorPool mDescriptorPool = VK_NULL_HANDLE;
    VkDescriptorSetLayout mDescriptorSetLayout = VK_NULL_HANDLE;
    VkDescriptorSet mDescriptorSet = VK_NULL_HANDLE;

    // Output buffer
    VkBuffer mOutputBuffer = VK_NULL_HANDLE;
    VkDeviceMemory mOutputBufferMemory = VK_NULL_HANDLE;

    // Compute pipeline
    VkShaderModule mShaderModule = VK_NULL_HANDLE;
    VkPipelineLayout mPipelineLayout = VK_NULL_HANDLE;
    VkPipeline mPipeline = VK_NULL_HANDLE;

    // Command buffer
    VkCommandPool mCommandPool = VK_NULL_HANDLE;
    VkCommandBuffer mCommandBuffer = VK_NULL_HANDLE;
    VkFence mFence = VK_NULL_HANDLE;

    bool mIsValid = false;
};

template <Type dataType>
class NnapiExecutor {
   public:
    // Returns the created object on success, or nullptr on failure.
    static std::unique_ptr<NnapiExecutor> create(const ANeuralNetworksDevice* device,
                                                 AHardwareBuffer* input, AHardwareBuffer* output) {
        auto nnapi = std::make_unique<NnapiExecutor>(input, output);
        nnapi->initialize(device);
        return nnapi->mIsValid ? std::move(nnapi) : nullptr;
    }

    // Prefer NnapiExecutor::create
    NnapiExecutor(AHardwareBuffer* input, AHardwareBuffer* output)
        : mInputMemory(input), mOutputMemory(output) {}

    // Returns {success, sync_fd}
    std::pair<bool, base::unique_fd> run(const base::unique_fd& inSyncFd) {
        bool success = false;
        base::unique_fd outSyncFd;
        runInternal(inSyncFd, &success, &outSyncFd);
        return {success, std::move(outSyncFd)};
    }

   private:
    using ElementType = typename TestTypeHelper<dataType>::ElementType;

    void initialize(const ANeuralNetworksDevice* device) {
        ASSERT_TRUE(mInputMemory.isValid());
        ASSERT_TRUE(mOutputMemory.isValid());

        // Model input
        const float scale = TestTypeHelper<dataType>::kIsQuantized ? 1.0f : 0.0f;
        const OperandType tensorType(dataType, {kOperandSizeY, kOperandSizeX}, scale,
                                     /*zeroPoint=*/0);
        uint32_t inputTensor = mModel.addOperand(&tensorType);

        // Constant tensor
        const OperandType constTensorType(dataType, {1}, scale, /*zeroPoint=*/0);
        const ElementType constTensorData = static_cast<ElementType>(1);
        uint32_t constTensor =
                mModel.addConstantOperand<ElementType>(&constTensorType, constTensorData);

        // Activation (NONE)
        const OperandType activationType(Type::INT32, {});
        uint32_t activationScalar = mModel.addConstantOperand<int32_t>(&activationType, 0);

        // Model output
        uint32_t outputTensor = mModel.addOperand(&tensorType);

        // Model operation
        mModel.addOperation(ANEURALNETWORKS_ADD, {inputTensor, constTensor, activationScalar},
                            {outputTensor});

        // Finish model
        mModel.identifyInputsAndOutputs({inputTensor}, {outputTensor});
        mModel.relaxComputationFloat32toFloat16(/*isRelax=*/true);
        ASSERT_TRUE(mModel.isValid());
        ASSERT_EQ(mModel.finish(), Result::NO_ERROR);

        // Create compilation for the target device
        Result result;
        std::tie(result, mCompilation) =
                test_wrapper::Compilation::createForDevice(&mModel, device);
        ASSERT_EQ(result, Result::NO_ERROR);

        // Finish the compilation
        result = mCompilation.finish();
        if (result != Result::NO_ERROR) {
            GTEST_SKIP() << "Model is not supported by the device";
        }

        mIsValid = true;
    }

    void runInternal(const base::unique_fd& inSyncFd, bool* outSuccess,
                     base::unique_fd* outSyncFd) {
        *outSuccess = false;

        // Setup execution
        mExecution = std::make_unique<test_wrapper::Execution>(&mCompilation);
        ASSERT_EQ(mExecution->setInputFromMemory(/*index=*/0, &mInputMemory, /*offset=*/0,
                                                 kOperandLength * sizeof(ElementType)),
                  Result::NO_ERROR);
        ASSERT_EQ(mExecution->setOutputFromMemory(/*index=*/0, &mOutputMemory, /*offset=*/0,
                                                  kOperandLength * sizeof(ElementType)),
                  Result::NO_ERROR);

        // Setup dependencies
        std::vector<const test_wrapper::Event*> dependencies;
        test_wrapper::Event start;
        // The sync fence from Vulkan may not be valid if GPU workload has already finished
        // prior to exporting the fence.
        if (inSyncFd.ok()) {
            start = test_wrapper::Event(inSyncFd.get());
            ASSERT_TRUE(start.isValid());
            dependencies = {&start};
        }

        // Fenced compute
        test_wrapper::Event finished;
        mExecution->startComputeWithDependencies(dependencies, /*infinite timeout*/ 0, &finished);

        // Get the output sync fence if supported; Otherwise, wait until the execution is finished
        int syncFd = -1;
        finished.getSyncFenceFd(&syncFd);
        if (syncFd == -1) {
            ASSERT_EQ(finished.wait(), Result::NO_ERROR);
        }
        *outSyncFd = base::unique_fd(syncFd);
        *outSuccess = true;
    }

    test_wrapper::Model mModel;
    test_wrapper::Compilation mCompilation;
    std::unique_ptr<test_wrapper::Execution> mExecution;
    test_wrapper::Memory mInputMemory, mOutputMemory;
    bool mIsValid = false;
};

class GpuNnapiTest : public testing::TestWithParam<NameAndDevice> {
   protected:
    void TearDown() override {
        if (mGpuOutput) {
            AHardwareBuffer_release(mGpuOutput);
        }
        if (mNnapiOutput) {
            AHardwareBuffer_release(mNnapiOutput);
        }
    }

    template <Type dataType>
    void runTest() {
#ifndef NNTEST_ONLY_PUBLIC_API
        if (DeviceManager::get()->getUseCpuOnly()) {
            GTEST_SKIP();
        }
#endif

        // Allocate hardware buffers for GPU and NNAPI outputs
        const size_t size = kOperandLength * sizeof(typename TestTypeHelper<dataType>::ElementType);
        allocateBlobAhwb(
                size, AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER | AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                &mGpuOutput);
        allocateBlobAhwb(
                size, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN | AHARDWAREBUFFER_USAGE_CPU_WRITE_OFTEN,
                &mNnapiOutput);
        if (mGpuOutput == nullptr || mNnapiOutput == nullptr) return;

        // Create Vulkan compute pipeline
        auto vulkan = VulkanComputePipeline<dataType>::create(mGpuOutput);
        if (vulkan == nullptr) return;

        // Create NNAPI executor
        auto nnapi = NnapiExecutor<dataType>::create(kDevice, mGpuOutput, mNnapiOutput);
        if (nnapi == nullptr) return;

        // Run the test repeatly for kNumberOfIterationsToTest iterations
        for (uint32_t i = 0; i < kNumberOfIterationsToTest; i++) {
            auto [gpuSuccess, gpuSyncFd] = vulkan->run();
            ASSERT_TRUE(gpuSuccess);

            auto [nnapiSuccess, nnapiSyncFd] = nnapi->run(gpuSyncFd);
            ASSERT_TRUE(nnapiSuccess);

            const double tolerance = TestTypeHelper<dataType>::kTolerance;
            checkResults<dataType>(std::move(nnapiSyncFd), tolerance);
        }
    }

    template <Type dataType>
    void checkResults(base::unique_fd syncFd, double tolerance) {
        using ElementType = typename TestTypeHelper<dataType>::ElementType;

        // Lock the buffer with the sync fence
        // AHardwareBuffer_lock will take the ownership and close the sync fence even on errors
        void* data;
        ASSERT_EQ(AHardwareBuffer_lock(mNnapiOutput, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN,
                                       syncFd.release(), /*rect=*/nullptr, &data),
                  0);

        // Compare the actual results with the expect value
        uint32_t numberOfErrors = 0;
        const ElementType expected = static_cast<ElementType>(kExpectedResultInInt);
        for (uint32_t i = 0; i < kOperandLength; i++) {
            const ElementType actual = reinterpret_cast<ElementType*>(data)[i];

            // We expect the absolute difference in double is within the tolerance.
            const double expected_f64 = static_cast<double>(expected);
            const double actual_f64 = static_cast<double>(actual);
            const double diff = std::abs(expected_f64 - actual_f64);
            if (diff > tolerance) {
                // Print at most kMaxNumberOfPrintedErrors errors by EXPECT_EQ
                if (numberOfErrors < kMaxNumberOfPrintedErrors) {
                    EXPECT_NEAR(actual_f64, expected_f64, tolerance)
                            << "When comparing element [" << kOperandLength / kOperandSizeX << ", "
                            << kOperandLength % kOperandSizeX << "]";
                }
                numberOfErrors++;
            }
        }
        EXPECT_EQ(numberOfErrors, 0u);
        ASSERT_EQ(AHardwareBuffer_unlock(mNnapiOutput, /*fence=*/nullptr), 0);
    }

    // The NNAPI device under test
    const ANeuralNetworksDevice* kDevice = GetParam().second;

    AHardwareBuffer* mGpuOutput = nullptr;
    AHardwareBuffer* mNnapiOutput = nullptr;
};

TEST_P(GpuNnapiTest, Float32) {
    runTest<Type::TENSOR_FLOAT32>();
}
TEST_P(GpuNnapiTest, Float16) {
    runTest<Type::TENSOR_FLOAT16>();
}
TEST_P(GpuNnapiTest, Quant8Asymm) {
    runTest<Type::TENSOR_QUANT8_ASYMM>();
}
TEST_P(GpuNnapiTest, Quant8AsymmSigned) {
    runTest<Type::TENSOR_QUANT8_ASYMM_SIGNED>();
}

INSTANTIATE_TEST_SUITE_P(TestGpuNnapi, GpuNnapiTest, testing::ValuesIn(getNnapiDevices()),
                         printGpuNnapiTest);

}  // namespace
}  // namespace android::nn
