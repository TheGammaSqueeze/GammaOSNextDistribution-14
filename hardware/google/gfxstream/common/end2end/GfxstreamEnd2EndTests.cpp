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

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "GfxstreamEnd2EndTests.h"

#include <dlfcn.h>
#include <log/log.h>

#include <filesystem>

#include "ProcessPipe.h"
#include "RutabagaLayer.h"
#include "aemu/base/Path.h"
#include "gfxstream/RutabagaLayerTestUtils.h"

VULKAN_HPP_DEFAULT_DISPATCH_LOADER_DYNAMIC_STORAGE

namespace gfxstream {
namespace tests {

using testing::AnyOf;
using testing::Eq;
using testing::Gt;
using testing::IsFalse;
using testing::IsTrue;
using testing::Not;
using testing::NotNull;

std::string GfxstreamTransportToEnvVar(GfxstreamTransport transport) {
    switch (transport) {
        case GfxstreamTransport::kVirtioGpuAsg: {
            return "virtio-gpu-asg";
        }
        case GfxstreamTransport::kVirtioGpuPipe: {
            return "virtio-gpu-pipe";
        }
    }
}

std::string GfxstreamTransportToString(GfxstreamTransport transport) {
    switch (transport) {
        case GfxstreamTransport::kVirtioGpuAsg: {
            return "VirtioGpuAsg";
        }
        case GfxstreamTransport::kVirtioGpuPipe: {
            return "VirtioGpuPipe";
        }
    }
}

std::string TestParams::ToString() const {
    std::string ret;
    ret += (with_gl ? "With" : "Without");
    ret += "Gl";
    ret += (with_vk ? "With" : "Without");
    ret += "Vk";
    ret += (with_vk_snapshot ? "With" : "Without");
    ret += "Snapshot";
    ret += "Over";
    ret += GfxstreamTransportToString(with_transport);
    return ret;
}

std::ostream& operator<<(std::ostream& os, const TestParams& params) {
    return os << params.ToString();
}

std::string GetTestName(const ::testing::TestParamInfo<TestParams>& info) {
    return info.param.ToString();
}

std::unique_ptr<GfxstreamEnd2EndTest::GuestGlDispatchTable> GfxstreamEnd2EndTest::SetupGuestGl() {
    const std::filesystem::path testDirectory = gfxstream::guest::getProgramDirectory();
    const std::string eglLibPath = (testDirectory / "libEGL_emulation_with_host.so").string();
    const std::string gles2LibPath = (testDirectory / "libGLESv2_emulation_with_host.so").string();

    void* eglLib = dlopen(eglLibPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!eglLib) {
        ALOGE("Failed to load Gfxstream EGL library from %s.", eglLibPath.c_str());
        return nullptr;
    }

    void* gles2Lib = dlopen(gles2LibPath.c_str(), RTLD_NOW | RTLD_LOCAL);
    if (!gles2Lib) {
        ALOGE("Failed to load Gfxstream GLES2 library from %s.", gles2LibPath.c_str());
        return nullptr;
    }

    using GenericFnType = void*(void);
    using GetProcAddrType = GenericFnType*(const char*);

    auto eglGetAddr = reinterpret_cast<GetProcAddrType*>(dlsym(eglLib, "eglGetProcAddress"));
    if (!eglGetAddr) {
        ALOGE("Failed to load Gfxstream EGL library from %s.", eglLibPath.c_str());
        return nullptr;
    }

    auto gl = std::make_unique<GuestGlDispatchTable>();

    #define LOAD_EGL_FUNCTION(return_type, function_name, signature) \
        gl-> function_name = reinterpret_cast< return_type (*) signature >(eglGetAddr( #function_name ));

    LIST_RENDER_EGL_FUNCTIONS(LOAD_EGL_FUNCTION)
    LIST_RENDER_EGL_EXTENSIONS_FUNCTIONS(LOAD_EGL_FUNCTION)

    #define LOAD_GLES2_FUNCTION(return_type, function_name, signature, callargs)    \
        gl-> function_name = reinterpret_cast< return_type (*) signature >(eglGetAddr( #function_name )); \
        if (!gl-> function_name) { \
            gl-> function_name = reinterpret_cast< return_type (*) signature >(dlsym(gles2Lib, #function_name)); \
        }

    LIST_GLES_FUNCTIONS(LOAD_GLES2_FUNCTION, LOAD_GLES2_FUNCTION)

    return gl;
}

std::unique_ptr<vkhpp::DynamicLoader> GfxstreamEnd2EndTest::SetupGuestVk() {
    const std::filesystem::path testDirectory = gfxstream::guest::getProgramDirectory();
    const std::string vkLibPath = (testDirectory / "libgfxstream_guest_vulkan_with_host.so").string();

    auto dl = std::make_unique<vkhpp::DynamicLoader>(vkLibPath);
    if (!dl->success()) {
        ALOGE("Failed to load Vulkan from: %s", vkLibPath.c_str());
        return nullptr;
    }

    auto getInstanceProcAddr = dl->getProcAddress<PFN_vkGetInstanceProcAddr>("vk_icdGetInstanceProcAddr");
    if (!getInstanceProcAddr) {
        ALOGE("Failed to load Vulkan vkGetInstanceProcAddr. %s", dlerror());
        return nullptr;
    }

    VULKAN_HPP_DEFAULT_DISPATCHER.init(getInstanceProcAddr);

    return dl;
}

void GfxstreamEnd2EndTest::SetUp() {
    const TestParams params = GetParam();

    const std::string transportValue = GfxstreamTransportToEnvVar(params.with_transport);
    ASSERT_THAT(setenv("GFXSTREAM_TRANSPORT", transportValue.c_str(), /*overwrite=*/1), Eq(0));

    ASSERT_THAT(setenv("GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_GL",
                       params.with_gl ? "Y" : "N", /*overwrite=*/1), Eq(0));
    ASSERT_THAT(setenv("GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_VK", params.with_vk ? "Y" : "N",
                       /*overwrite=*/1),
                Eq(0));
    ASSERT_THAT(setenv("GFXSTREAM_EMULATED_VIRTIO_GPU_WITH_VK_SNAPSHOTS",
                       params.with_vk_snapshot ? "Y" : "N",
                       /*overwrite=*/1),
                Eq(0));

    if (params.with_gl) {
        mGl = SetupGuestGl();
        ASSERT_THAT(mGl, NotNull());
    }
    if (params.with_vk) {
        mVk = SetupGuestVk();
        ASSERT_THAT(mVk, NotNull());
    }

    mAnwHelper.reset(createPlatformANativeWindowHelper());
    mGralloc.reset(createPlatformGralloc());
    mSync.reset(createPlatformSyncHelper());
}

void GfxstreamEnd2EndTest::TearDownGuest() {
    if (mGl) {
        EGLDisplay display = mGl->eglGetCurrentDisplay();
        if (display != EGL_NO_DISPLAY) {
            mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT);
            mGl->eglTerminate(display);
        }
        mGl->eglReleaseThread();
        mGl.reset();
    }
    mVk.reset();

    mAnwHelper.reset();
    mGralloc.reset();
    mSync.reset();

    processPipeRestart();

    // Figure out more reliable way for guest shutdown to complete...
    std::this_thread::sleep_for(std::chrono::seconds(3));
}

void GfxstreamEnd2EndTest::TearDownHost() {
    const uint32_t users = GetNumActiveEmulatedVirtioGpuUsers();
    if (users != 0) {
        ALOGE("The EmulationVirtioGpu was found to still be active by %" PRIu32
              " after the "
              "end of the test. Please ensure you have fully destroyed all objects created "
              "during the test (Gralloc allocations, ANW allocations, etc).",
              users);
        abort();
    }
}

void GfxstreamEnd2EndTest::TearDown() {
    TearDownGuest();
    TearDownHost();
}

void GfxstreamEnd2EndTest::SetUpEglContextAndSurface(
        uint32_t contextVersion,
        uint32_t width,
        uint32_t height,
        EGLDisplay* outDisplay,
        EGLContext* outContext,
        EGLSurface* outSurface) {
    ASSERT_THAT(contextVersion, AnyOf(Eq(2), Eq(3)))
        << "Invalid context version requested.";

    EGLDisplay display = mGl->eglGetDisplay(EGL_DEFAULT_DISPLAY);
    ASSERT_THAT(display, Not(Eq(EGL_NO_DISPLAY)));

    int versionMajor = 0;
    int versionMinor = 0;
    ASSERT_THAT(mGl->eglInitialize(display, &versionMajor, &versionMinor), IsTrue());

    ASSERT_THAT(mGl->eglBindAPI(EGL_OPENGL_ES_API), IsTrue());

    // clang-format off
    static const EGLint configAttributes[] = {
        EGL_SURFACE_TYPE,    EGL_PBUFFER_BIT,
        EGL_RENDERABLE_TYPE, EGL_OPENGL_ES2_BIT,
        EGL_NONE,
    };
    // clang-format on

    int numConfigs = 0;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, nullptr, 1, &numConfigs), IsTrue());
    ASSERT_THAT(numConfigs, Gt(0));

    EGLConfig config = nullptr;
    ASSERT_THAT(mGl->eglChooseConfig(display, configAttributes, &config, 1, &numConfigs), IsTrue());
    ASSERT_THAT(config, Not(Eq(nullptr)));

    // clang-format off
    static const EGLint surfaceAttributes[] = {
        EGL_WIDTH,  static_cast<EGLint>(width),
        EGL_HEIGHT, static_cast<EGLint>(height),
        EGL_NONE,
    };
    // clang-format on

    EGLSurface surface = mGl->eglCreatePbufferSurface(display, config, surfaceAttributes);
    ASSERT_THAT(surface, Not(Eq(EGL_NO_SURFACE)));

    // clang-format off
    static const EGLint contextAttribs[] = {
        EGL_CONTEXT_CLIENT_VERSION, static_cast<EGLint>(contextVersion),
        EGL_NONE,
    };
    // clang-format on

    EGLContext context = mGl->eglCreateContext(display, config, EGL_NO_CONTEXT, contextAttribs);
    ASSERT_THAT(context, Not(Eq(EGL_NO_CONTEXT)));

    ASSERT_THAT(mGl->eglMakeCurrent(display, surface, surface, context), IsTrue());

    *outDisplay = display;
    *outContext = context;
    *outSurface = surface;
}

void GfxstreamEnd2EndTest::TearDownEglContextAndSurface(
        EGLDisplay display,
        EGLContext context,
        EGLSurface surface) {
    ASSERT_THAT(mGl->eglMakeCurrent(display, EGL_NO_SURFACE, EGL_NO_SURFACE, EGL_NO_CONTEXT), IsTrue());
    ASSERT_THAT(mGl->eglDestroyContext(display, context), IsTrue());
    ASSERT_THAT(mGl->eglDestroySurface(display, surface), IsTrue());
}

GlExpected<GLuint> GfxstreamEnd2EndTest::SetUpShader(GLenum type, const std::string& source) {
    if (!mGl) {
        return android::base::unexpected("Gl not enabled for this test.");
    }

    GLuint shader = mGl->glCreateShader(type);
    if (!shader) {
        return android::base::unexpected("Failed to create shader.");
    }

    const GLchar* sourceTyped = (const GLchar*)source.c_str();
    const GLint sourceLength = source.size();
    mGl->glShaderSource(shader, 1, &sourceTyped, &sourceLength);
    mGl->glCompileShader(shader);

    GLenum err = mGl->glGetError();
    if (err != GL_NO_ERROR) {
        return android::base::unexpected("Failed to compile shader.");
    }

    GLint compileStatus;
    mGl->glGetShaderiv(shader, GL_COMPILE_STATUS, &compileStatus);

    if (compileStatus == GL_TRUE) {
        return shader;
    } else {
        GLint errorLogLength = 0;
        mGl->glGetShaderiv(shader, GL_INFO_LOG_LENGTH, &errorLogLength);
        if (!errorLogLength) {
            errorLogLength = 512;
        }

        std::vector<GLchar> errorLog(errorLogLength);
        mGl->glGetShaderInfoLog(shader, errorLogLength, &errorLogLength, errorLog.data());

        const std::string errorString = errorLogLength == 0 ? "" : errorLog.data();
        ALOGE("Shader compilation failed with: \"%s\"", errorString.c_str());

        mGl->glDeleteShader(shader);
        return android::base::unexpected(errorString);
    }
}

GlExpected<GLuint> GfxstreamEnd2EndTest::SetUpProgram(
        const std::string& vertSource,
        const std::string& fragSource) {
    auto vertResult = SetUpShader(GL_VERTEX_SHADER, vertSource);
    if (!vertResult.ok()) {
        return vertResult;
    }
    auto vertShader = vertResult.value();

    auto fragResult = SetUpShader(GL_FRAGMENT_SHADER, fragSource);
    if (!fragResult.ok()) {
        return fragResult;
    }
    auto fragShader = fragResult.value();

    GLuint program = mGl->glCreateProgram();
    mGl->glAttachShader(program, vertShader);
    mGl->glAttachShader(program, fragShader);
    mGl->glLinkProgram(program);
    mGl->glDeleteShader(vertShader);
    mGl->glDeleteShader(fragShader);

    GLint linkStatus;
    mGl->glGetProgramiv(program, GL_LINK_STATUS, &linkStatus);
    if (linkStatus == GL_TRUE) {
        return program;
    } else {
        GLint errorLogLength = 0;
        mGl->glGetProgramiv(program, GL_INFO_LOG_LENGTH, &errorLogLength);
        if (!errorLogLength) {
            errorLogLength = 512;
        }

        std::vector<char> errorLog(errorLogLength, 0);
        mGl->glGetProgramInfoLog(program, errorLogLength, nullptr, errorLog.data());

        const std::string errorString = errorLogLength == 0 ? "" : errorLog.data();
        ALOGE("Program link failed with: \"%s\"", errorString.c_str());

        mGl->glDeleteProgram(program);
        return android::base::unexpected(errorString);
    }
}

VkExpected<GfxstreamEnd2EndTest::TypicalVkTestEnvironment>
GfxstreamEnd2EndTest::SetUpTypicalVkTestEnvironment(uint32_t apiVersion) {
    const auto availableInstanceLayers = vkhpp::enumerateInstanceLayerProperties().value;
    ALOGV("Available instance layers:");
    for (const vkhpp::LayerProperties& layer : availableInstanceLayers) {
        ALOGV(" - %s", layer.layerName.data());
    }

    constexpr const bool kEnableValidationLayers = true;

    std::vector<const char*> requestedInstanceExtensions;
    std::vector<const char*> requestedInstanceLayers;
    if (kEnableValidationLayers) {
        requestedInstanceExtensions.push_back(VK_EXT_DEBUG_UTILS_EXTENSION_NAME);
    }

    const vkhpp::ApplicationInfo applicationInfo{
        .pApplicationName = ::testing::UnitTest::GetInstance()->current_test_info()->name(),
        .applicationVersion = 1,
        .pEngineName = "Gfxstream Testing Engine",
        .engineVersion = 1,
        .apiVersion = apiVersion,
    };
    const vkhpp::InstanceCreateInfo instanceCreateInfo{
        .pApplicationInfo = &applicationInfo,
        .enabledLayerCount = static_cast<uint32_t>(requestedInstanceLayers.size()),
        .ppEnabledLayerNames = requestedInstanceLayers.data(),
        .enabledExtensionCount = static_cast<uint32_t>(requestedInstanceExtensions.size()),
        .ppEnabledExtensionNames = requestedInstanceExtensions.data(),
    };

    auto instance = VK_EXPECT_RV(vkhpp::createInstanceUnique(instanceCreateInfo));

    VULKAN_HPP_DEFAULT_DISPATCHER.init(*instance);

    auto physicalDevices = VK_EXPECT_RV(instance->enumeratePhysicalDevices());
    ALOGV("Available physical devices:");
    for (const auto& physicalDevice : physicalDevices) {
        const auto physicalDeviceProps = physicalDevice.getProperties();
        ALOGV(" - %s", physicalDeviceProps.deviceName.data());
    }

    if (physicalDevices.empty()) {
        ALOGE("No physical devices available?");
        return android::base::unexpected(vkhpp::Result::eErrorUnknown);
    }

    auto physicalDevice = std::move(physicalDevices[0]);
    {
        const auto physicalDeviceProps = physicalDevice.getProperties();
        ALOGV("Selected physical device: %s", physicalDeviceProps.deviceName.data());
    }
    {
        const auto exts = VK_EXPECT_RV(physicalDevice.enumerateDeviceExtensionProperties());
        ALOGV("Available physical device extensions:");
        for (const auto& ext : exts) {
            ALOGV(" - %s", ext.extensionName.data());
        }
    }

    uint32_t graphicsQueueFamilyIndex = -1;
    {
        const auto props = physicalDevice.getQueueFamilyProperties();
        for (uint32_t i = 0; i < props.size(); i++) {
            const auto& prop = props[i];
            if (prop.queueFlags & vkhpp::QueueFlagBits::eGraphics) {
                graphicsQueueFamilyIndex = i;
                break;
            }
        }
    }
    if (graphicsQueueFamilyIndex == -1) {
        ALOGE("Failed to find graphics queue.");
        return android::base::unexpected(vkhpp::Result::eErrorUnknown);
    }

    const float queuePriority = 1.0f;
    const vkhpp::DeviceQueueCreateInfo deviceQueueCreateInfo = {
        .queueFamilyIndex = graphicsQueueFamilyIndex,
        .queueCount = 1,
        .pQueuePriorities = &queuePriority,
    };
    const std::vector<const char*> deviceExtensions = {
        VK_ANDROID_NATIVE_BUFFER_EXTENSION_NAME,
        VK_ANDROID_EXTERNAL_MEMORY_ANDROID_HARDWARE_BUFFER_EXTENSION_NAME,
    };
    const vkhpp::DeviceCreateInfo deviceCreateInfo = {
        .pQueueCreateInfos = &deviceQueueCreateInfo,
        .queueCreateInfoCount = 1,
        .enabledLayerCount = 0,
        .ppEnabledLayerNames = nullptr,
        .enabledExtensionCount = static_cast<uint32_t>(deviceExtensions.size()),
        .ppEnabledExtensionNames = deviceExtensions.data(),
    };
    auto device = VK_EXPECT_RV(physicalDevice.createDeviceUnique(deviceCreateInfo));

    auto queue = device->getQueue(graphicsQueueFamilyIndex, 0);

    return TypicalVkTestEnvironment{
        .instance = std::move(instance),
        .physicalDevice = std::move(physicalDevice),
        .device = std::move(device),
        .queue = std::move(queue),
        .queueFamilyIndex = graphicsQueueFamilyIndex,
    };
}

uint32_t GfxstreamEnd2EndTest::GetMemoryType(const vkhpp::PhysicalDevice& physicalDevice,
                                             const vkhpp::MemoryRequirements& memoryRequirements,
                                             vkhpp::MemoryPropertyFlags memoryProperties) {
    const auto props = physicalDevice.getMemoryProperties();
    for (uint32_t i = 0; i < props.memoryTypeCount; i++) {
        if (!(memoryRequirements.memoryTypeBits & (1 << i))) {
            continue;
        }
        if ((props.memoryTypes[i].propertyFlags & memoryProperties) != memoryProperties) {
            continue;
        }
        return i;
    }
    return -1;
}

void GfxstreamEnd2EndTest::SnapshotSaveAndLoad() {
    auto directory = testing::TempDir();

    std::shared_ptr<gfxstream::EmulatedVirtioGpu> emulation = gfxstream::EmulatedVirtioGpu::Get();

    emulation->SnapshotSave(directory);
    emulation->SnapshotRestore(directory);
}

}  // namespace tests
}  // namespace gfxstream
