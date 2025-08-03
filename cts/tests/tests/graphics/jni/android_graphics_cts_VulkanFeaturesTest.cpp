/*
 * Copyright 2016 The Android Open Source Project
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

#define LOG_TAG "VulkanFeaturesTest"

#ifndef VK_USE_PLATFORM_ANDROID_KHR
#define VK_USE_PLATFORM_ANDROID_KHR
#endif

#include <android/log.h>
#include <jni.h>
#include <vkjson.h>

#define ALOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define ALOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

#include "VulkanProfiles.h"

namespace {

jstring android_graphics_cts_VulkanFeaturesTest_nativeGetVkJSON(JNIEnv* env,
    jclass /*clazz*/)
{
    std::string vkjson(VkJsonInstanceToJson(VkJsonGetInstance()));
    return env->NewStringUTF(vkjson.c_str());
}

jstring android_graphics_cts_VulkanFeaturesTest_nativeGetABPSupport(JNIEnv* env,
    jclass /*clazz*/)
{
    uint32_t count;
    VkResult result = VK_SUCCESS;
    VkBool32 supported = VK_FALSE;
    VpProfileProperties profile {
        VP_ANDROID_BASELINE_2021_NAME,
        VP_ANDROID_BASELINE_2021_SPEC_VERSION
    };

    result = vpGetInstanceProfileSupport(nullptr, &profile, &supported);
    if (result != VK_SUCCESS) {
        std::string error ("There was a failure from vpGetinstanceProfileSupport:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }
    if (supported != VK_TRUE) {
        std::string error ("There was a failure from vpGetinstanceProfileSupport:\n"
            "    supported = " + std::to_string(supported));
        return env->NewStringUTF(error.c_str());
    }

    const VkApplicationInfo appInfo = {
        VK_STRUCTURE_TYPE_APPLICATION_INFO,
        nullptr,
        "vulkan_features_test",
        0,
        "",
        0,
        VP_ANDROID_BASELINE_2021_MIN_API_VERSION,
    };
    VkInstanceCreateInfo instanceCreateInfo = {
        VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        nullptr,
        0,
        &appInfo,
        0,
        nullptr,
        0,
        nullptr,
    };

    VpInstanceCreateInfo vpInstanceCreateInfo{};
    vpInstanceCreateInfo.pCreateInfo = &instanceCreateInfo;
    vpInstanceCreateInfo.pProfile = &profile;

    VkInstance instance = VK_NULL_HANDLE;
    result = vpCreateInstance(&vpInstanceCreateInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        std::string error("There was a failure from vpCreateInstance:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        std::string error("There was a failure from vkEnumeratePhysicalDevices:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    std::vector<VkPhysicalDevice> devices(count, VK_NULL_HANDLE);
    result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        std::string error("There was a failure from vkEnumeratePhysicalDevices (2):\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    bool onePhysicalDeviceSupports = false;
    for (size_t i = 0; i < count; i++) {
        result = vpGetPhysicalDeviceProfileSupport(instance, devices[i], &profile, &supported);
        if (result != VK_SUCCESS) {
            ALOGD("vpGetPhysicalDeviceProfileSupport fail, result = %d", result);
            continue;
        }
        else if (supported != VK_TRUE) {
            ALOGD("vpGetPhysicalDeviceProfileSupport fail, supported = %d", supported);
            continue;
        }

        onePhysicalDeviceSupports = true;
    }

    if(onePhysicalDeviceSupports == false) {
        std::string error("There was a failure from vpGetPhysicalDeviceProfileSupport:\n"
            "    No VkPhysicalDevice supports the profile");
        return env->NewStringUTF(error.c_str());
    }

    return env->NewStringUTF("");
}

jstring android_graphics_cts_VulkanFeaturesTest_nativeGetABPCpuOnlySupport(JNIEnv* env,
    jclass /*clazz*/)
{
    uint32_t count;
    VkResult result = VK_SUCCESS;
    VkBool32 supported = VK_FALSE;
    VpProfileProperties profile {
        VP_ANDROID_BASELINE_CPU_ONLY_2021_NAME,
        VP_ANDROID_BASELINE_CPU_ONLY_2021_SPEC_VERSION
    };

    result = vpGetInstanceProfileSupport(nullptr, &profile, &supported);
    if (result != VK_SUCCESS) {
        std::string error ("There was a failure from vpGetinstanceProfileSupport:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }
    if (supported != VK_TRUE) {
        std::string error ("There was a failure from vpGetinstanceProfileSupport:\n"
            "    supported = " + std::to_string(supported));
        return env->NewStringUTF(error.c_str());
    }

    const VkApplicationInfo appInfo = {
        VK_STRUCTURE_TYPE_APPLICATION_INFO,
        nullptr,
        "vulkan_features_test",
        0,
        "",
        0,
        VP_ANDROID_BASELINE_CPU_ONLY_2021_MIN_API_VERSION,
    };
    VkInstanceCreateInfo instanceCreateInfo = {
        VK_STRUCTURE_TYPE_INSTANCE_CREATE_INFO,
        nullptr,
        0,
        &appInfo,
        0,
        nullptr,
        0,
        nullptr,
    };

    VpInstanceCreateInfo vpInstanceCreateInfo{};
    vpInstanceCreateInfo.pCreateInfo = &instanceCreateInfo;
    vpInstanceCreateInfo.pProfile = &profile;

    VkInstance instance = VK_NULL_HANDLE;
    result = vpCreateInstance(&vpInstanceCreateInfo, nullptr, &instance);
    if (result != VK_SUCCESS) {
        std::string error("There was a failure from vpCreateInstance:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    count = 0;
    result = vkEnumeratePhysicalDevices(instance, &count, nullptr);
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        std::string error("There was a failure from vkEnumeratePhysicalDevices:\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    std::vector<VkPhysicalDevice> devices(count, VK_NULL_HANDLE);
    result = vkEnumeratePhysicalDevices(instance, &count, devices.data());
    if (result != VK_SUCCESS) {
        vkDestroyInstance(instance, nullptr);
        std::string error("There was a failure from vkEnumeratePhysicalDevices (2):\n"
            "    result = " + std::to_string(result));
        return env->NewStringUTF(error.c_str());
    }

    bool onePhysicalDeviceSupports = false;
    for (size_t i = 0; i < count; i++) {
        result = vpGetPhysicalDeviceProfileSupport(instance, devices[i], &profile, &supported);
        if (result != VK_SUCCESS) {
            ALOGD("vpGetPhysicalDeviceProfileSupport fail, result = %d", result);
            continue;
        }
        else if (supported != VK_TRUE) {
            ALOGD("vpGetPhysicalDeviceProfileSupport fail, supported = %d", supported);
            continue;
        }

        onePhysicalDeviceSupports = true;
    }

    if(onePhysicalDeviceSupports == false) {
        std::string error("There was a failure from vpGetPhysicalDeviceProfileSupport:\n"
            "    No VkPhysicalDevice supports the profile");
        return env->NewStringUTF(error.c_str());
    }

    return env->NewStringUTF("");
}

static JNINativeMethod gMethods[] = {
    {   "nativeGetVkJSON", "()Ljava/lang/String;",
        (void*) android_graphics_cts_VulkanFeaturesTest_nativeGetVkJSON },
    {   "nativeGetABPSupport", "()Ljava/lang/String;",
        (void*) android_graphics_cts_VulkanFeaturesTest_nativeGetABPSupport },
    {   "nativeGetABPCpuOnlySupport", "()Ljava/lang/String;",
        (void*) android_graphics_cts_VulkanFeaturesTest_nativeGetABPCpuOnlySupport },
};

} // anonymous namespace

int register_android_graphics_cts_VulkanFeaturesTest(JNIEnv* env) {
    jclass clazz = env->FindClass("android/graphics/cts/VulkanFeaturesTest");
    return env->RegisterNatives(clazz, gMethods,
            sizeof(gMethods) / sizeof(JNINativeMethod));
}
