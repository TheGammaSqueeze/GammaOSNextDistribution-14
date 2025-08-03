/*
 * Copyright 2018 The Android Open Source Project
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
 *
 */

#define LOG_TAG "BasicVulkanGpuTest"

#include <map>
#include <string>
#include <cmath>

#include <android/hardware_buffer.h>
#include <android/log.h>
#include <jni.h>
#include <unistd.h>

#include "NativeTestHelpers.h"
#include "VulkanTestHelpers.h"

namespace {

static constexpr uint32_t kTestImageWidth = 64;
static constexpr uint32_t kTestImageHeight = 64;

} // namespace

// Container for Vulkan objects that need to be destroyed.
// We don't have nice RAII handles for Vulkan objects in this
// test infrastructure so this is used instead.
struct ComputePassResources {
  ComputePassResources(VkInit *init) : mInit(init) {}

  ~ComputePassResources() {
    if (mCommandBuffer) vkFreeCommandBuffers(mInit->device(), mCommandPool, 1, &mCommandBuffer);
    if (mCommandPool) vkDestroyCommandPool(mInit->device(), mCommandPool, nullptr);
    if (mDescriptorPool) vkDestroyDescriptorPool(mInit->device(), mDescriptorPool, nullptr);
    if (mPipeline) vkDestroyPipeline(mInit->device(), mPipeline, nullptr);
    if (mPipelineLayout) vkDestroyPipelineLayout(mInit->device(), mPipelineLayout, nullptr);
    if (mDescriptorSetLayout) vkDestroyDescriptorSetLayout(mInit->device(), mDescriptorSetLayout, nullptr);
  }

  VkInit *const mInit;

  VkDescriptorSetLayout mDescriptorSetLayout = VK_NULL_HANDLE;
  VkPipelineLayout mPipelineLayout = VK_NULL_HANDLE;
  VkPipeline mPipeline = VK_NULL_HANDLE;
  VkDescriptorPool mDescriptorPool = VK_NULL_HANDLE;
  VkCommandPool mCommandPool = VK_NULL_HANDLE;
  VkCommandBuffer mCommandBuffer = VK_NULL_HANDLE;
};

// A Vulkan AHardwareBuffer import test which does the following:
// 1) Allocates an AHardwareBuffer that is both CPU-readable and
//    usable by the GPU as a storage image.
// 2) Writes a well-defined pattern into the AHB from a compute shader
// 3) Locks the AHB for CPU access
// 4) validates that the values are as expected.
static void verifyComputeShaderWrite(JNIEnv *env, jclass, jobject assetMgr) {
  // Set up Vulkan.
  VkInit init;
  if (!init.init()) {
    // Could not initialize Vulkan due to lack of device support, skip test.
    return;
  }

  // Create AHB usable as both storage image and cpu accessible
  AHardwareBuffer_Desc hwbDesc{
      .width = kTestImageWidth,
      .height = kTestImageHeight,
      .layers = 1,
      .format = AHARDWAREBUFFER_FORMAT_R8G8B8A8_UNORM,
      .usage = AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN |
               AHARDWAREBUFFER_USAGE_GPU_DATA_BUFFER |
               AHARDWAREBUFFER_USAGE_GPU_SAMPLED_IMAGE,
  };
  AHardwareBuffer *buffer;
  if (0 != AHardwareBuffer_allocate(&hwbDesc, &buffer)) {
    // We don't require that this is actually supported; only that if it is
    // claimed to be supported, that it works.
    return;
  }

  ShaderModule shaderModule;
  ASSERT(shaderModule.init(&init, env, assetMgr, "shaders/compute_write.spv"),
         "Could not load shader module");

  ComputePassResources res(&init);

  // Descriptor set layout
  VkDescriptorSetLayoutBinding dslb = {
    0, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1, VK_SHADER_STAGE_COMPUTE_BIT, nullptr
  };
  VkDescriptorSetLayoutCreateInfo dslci = {
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_LAYOUT_CREATE_INFO,
    nullptr, 0,
    1, &dslb,
  };
  vkCreateDescriptorSetLayout(
      init.device(), &dslci, nullptr, &res.mDescriptorSetLayout);

  // Pipeline layout
  VkPipelineLayoutCreateInfo plci = {
    VK_STRUCTURE_TYPE_PIPELINE_LAYOUT_CREATE_INFO,
    nullptr,
    0, 1, &res.mDescriptorSetLayout, 0, nullptr
  };
  vkCreatePipelineLayout(
      init.device(), &plci, nullptr, &res.mPipelineLayout);

  // Pipeline
  VkComputePipelineCreateInfo cpci = {
    VK_STRUCTURE_TYPE_COMPUTE_PIPELINE_CREATE_INFO,
    nullptr,
    0,
    {
      VK_STRUCTURE_TYPE_PIPELINE_SHADER_STAGE_CREATE_INFO,
      nullptr,
      0,
      VK_SHADER_STAGE_COMPUTE_BIT,
      shaderModule.module(),
      "main",
      nullptr
    },
    res.mPipelineLayout,
    VK_NULL_HANDLE, -1
  };
  ASSERT(VK_SUCCESS == vkCreateComputePipelines(
      init.device(), VK_NULL_HANDLE, 1, &cpci,
      nullptr, &res.mPipeline),
         "Could not create pipeline.");

  // Import the AHardwareBuffer into Vulkan.
  VkAHardwareBufferImage vkImage(&init);
  ASSERT(vkImage.init(buffer, false, -1, VK_IMAGE_USAGE_STORAGE_BIT | VK_IMAGE_USAGE_SAMPLED_BIT),
         "Could not initialize VkAHardwareBufferImage.");

  // Descriptor set
  VkDescriptorPoolSize poolSize = { VK_DESCRIPTOR_TYPE_STORAGE_IMAGE, 1 };
  VkDescriptorPoolCreateInfo dpci = {
    VK_STRUCTURE_TYPE_DESCRIPTOR_POOL_CREATE_INFO,
    nullptr,
    0, 1, 1, &poolSize
  };
  vkCreateDescriptorPool(init.device(), &dpci, nullptr, &res.mDescriptorPool);

  VkDescriptorSetAllocateInfo dsai = {
    VK_STRUCTURE_TYPE_DESCRIPTOR_SET_ALLOCATE_INFO,
    nullptr,
    res.mDescriptorPool,
    1, &res.mDescriptorSetLayout,
  };
  VkDescriptorSet ds; // lifetime owned by pool
  vkAllocateDescriptorSets(init.device(), &dsai, &ds);

  VkDescriptorImageInfo imageInfo = {
    VK_NULL_HANDLE,
    vkImage.view(),
    VK_IMAGE_LAYOUT_GENERAL
  };
  VkWriteDescriptorSet dsw = {
    VK_STRUCTURE_TYPE_WRITE_DESCRIPTOR_SET,
    nullptr,
    ds, 0, 0, 1, VK_DESCRIPTOR_TYPE_STORAGE_IMAGE,
    &imageInfo,
    nullptr,
    nullptr
  };
  vkUpdateDescriptorSets(
      init.device(), 1, &dsw, 0, nullptr);

  // Command pool
  VkCommandPoolCreateInfo pci = {
    VK_STRUCTURE_TYPE_COMMAND_POOL_CREATE_INFO,
    nullptr,
    0,
    init.queueFamilyIndex()
  };
  vkCreateCommandPool(init.device(), &pci, nullptr, &res.mCommandPool);

  // Command buffer
  VkCommandBufferAllocateInfo cbai = {
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_ALLOCATE_INFO,
    nullptr,
    res.mCommandPool,
    VK_COMMAND_BUFFER_LEVEL_PRIMARY,
    1
  };
  vkAllocateCommandBuffers(init.device(), &cbai, &res.mCommandBuffer);

  VkCommandBufferBeginInfo cbbi = {
    VK_STRUCTURE_TYPE_COMMAND_BUFFER_BEGIN_INFO,
    nullptr,
    VK_COMMAND_BUFFER_USAGE_ONE_TIME_SUBMIT_BIT,
    nullptr
  };
  vkBeginCommandBuffer(res.mCommandBuffer, &cbbi);

  // transfer ownership from the foreign queue
  VkImageMemoryBarrier imb = {
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
    nullptr,
    VK_ACCESS_MEMORY_WRITE_BIT,
    VK_ACCESS_SHADER_WRITE_BIT,
    VK_IMAGE_LAYOUT_GENERAL,
    VK_IMAGE_LAYOUT_GENERAL,
    VK_QUEUE_FAMILY_FOREIGN_EXT,
    init.queueFamilyIndex(),
    vkImage.image(),
    { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 },
  };
  vkCmdPipelineBarrier(res.mCommandBuffer, VK_PIPELINE_STAGE_ALL_COMMANDS_BIT,
                       VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                       0, 0, nullptr, 0, nullptr, 1, &imb);

  vkCmdBindPipeline(res.mCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, res.mPipeline);
  vkCmdBindDescriptorSets(res.mCommandBuffer, VK_PIPELINE_BIND_POINT_COMPUTE, res.mPipelineLayout,
                          0, 1, &ds, 0, nullptr);
  // local size in shader is 8x8 invocations. 8x8 groups then covers the whole
  // 64x64 test image.
  vkCmdDispatch(res.mCommandBuffer, 8, 8, 1);

  // transfer ownership to the foreign queue
  VkImageMemoryBarrier imb2 = {
    VK_STRUCTURE_TYPE_IMAGE_MEMORY_BARRIER,
    nullptr,
    VK_ACCESS_SHADER_WRITE_BIT,
    VK_ACCESS_MEMORY_READ_BIT | VK_ACCESS_MEMORY_WRITE_BIT,
    VK_IMAGE_LAYOUT_GENERAL,
    VK_IMAGE_LAYOUT_GENERAL,
    init.queueFamilyIndex(),
    VK_QUEUE_FAMILY_FOREIGN_EXT,
    vkImage.image(),
    { VK_IMAGE_ASPECT_COLOR_BIT, 0, 1, 0, 1 },
  };
  vkCmdPipelineBarrier(res.mCommandBuffer, VK_PIPELINE_STAGE_COMPUTE_SHADER_BIT,
                       VK_PIPELINE_STAGE_TOP_OF_PIPE_BIT,
                       0, 0, nullptr, 0, nullptr, 1, &imb2);

  ASSERT(VK_SUCCESS == vkEndCommandBuffer(res.mCommandBuffer),
         "Could not record command buffer.");

  // Submit the work
  VkSubmitInfo si = {
    VK_STRUCTURE_TYPE_SUBMIT_INFO,
    nullptr,
    0, nullptr, nullptr,
    1, &res.mCommandBuffer, 0, nullptr
  };
  vkQueueSubmit(init.queue(), 1, &si, VK_NULL_HANDLE);
  vkDeviceWaitIdle(init.device());

  // Lock the AHB and read back the contents
  AHardwareBuffer_describe(buffer, &hwbDesc);
  uint8_t *bufferAddr;
  ASSERT(0 == AHardwareBuffer_lock(
      buffer, AHARDWAREBUFFER_USAGE_CPU_READ_OFTEN, -1, nullptr,
      reinterpret_cast<void **>(&bufferAddr)),
         "Unable to lock hardware buffer.");

  uint8_t *dst = bufferAddr;
  for (size_t y = 0; y < kTestImageHeight; ++y) {
    for (size_t x = 0; x < kTestImageWidth; ++x) {
      uint8_t *target = dst + ((y * hwbDesc.stride * 4) +
                               x * 4);
      ASSERT(fabsf(target[0] / 255.f - (x&7) / 8.f) <= 1/255.f,
             "Invalid pixel red channel at %zu,%zu.", x, y);
      ASSERT(fabsf(target[1] / 255.f - (y&7) / 8.f) <= 1/255.f,
             "Invalid pixel green channel at %zu,%zu.", x, y);
      ASSERT(fabsf(target[2] / 255.f - (x) / 64.f) <= 1/255.f,
             "Invalid pixel blue channel at %zu,%zu.", x, y);
      ASSERT(fabsf(target[3] / 255.f - (y) / 64.f) <= 1/255.f,
             "Invalid pixel alpha channel at %zu,%zu.", x, y);
    }
  }

  AHardwareBuffer_unlock(buffer, nullptr);
}

static JNINativeMethod gMethods[] = {
    {"verifyComputeShaderWrite", "(Landroid/content/res/AssetManager;)V",
     (void *)verifyComputeShaderWrite},
};

int register_android_graphics_cts_ComputeAhbTest(JNIEnv *env) {
  jclass clazz = env->FindClass("android/graphics/cts/ComputeAhbTest");
  return env->RegisterNatives(clazz, gMethods,
                              sizeof(gMethods) / sizeof(JNINativeMethod));
}
