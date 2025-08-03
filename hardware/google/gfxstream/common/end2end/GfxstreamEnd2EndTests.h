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

#include <android-base/expected.h>
#include <inttypes.h>

#include <future>
#include <memory>
#include <string>
#include <thread>
#include <variant>

// clang-format off
#include <EGL/egl.h>
#include <EGL/eglext.h>
#include "OpenGLESDispatch/gldefs.h"
#include "OpenGLESDispatch/gles_functions.h"
#include "OpenGLESDispatch/RenderEGL_functions.h"
#include "OpenGLESDispatch/RenderEGL_extensions_functions.h"

#define VULKAN_HPP_NAMESPACE vkhpp
#define VULKAN_HPP_DISPATCH_LOADER_DYNAMIC 1
#define VULKAN_HPP_ENABLE_DYNAMIC_LOADER_TOOL 1
#define VULKAN_HPP_NO_CONSTRUCTORS
#define VULKAN_HPP_NO_EXCEPTIONS
#include <vulkan/vulkan.hpp>
#include <vulkan/vk_android_native_buffer.h>
// clang-format on

#include "gfxstream/guest/ANativeWindow.h"
#include "gfxstream/guest/Gralloc.h"
#include "Sync.h"

namespace gfxstream {
namespace tests {

MATCHER(IsOk, "an ok result") {
  auto& result = arg;
  if (!result.ok()) {
    *result_listener << "which is an error with message: \""
                     << result.error()
                     << "\"";
    return false;
  }
  return true;
}

MATCHER(IsError, "an error result") {
    auto& result = arg;
    if (result.ok()) {
        *result_listener << "which is an ok result";
        return false;
    }
    return true;
}

MATCHER(IsVkSuccess, "is VK_SUCCESS") {
    auto& result = arg;
    if (result != vkhpp::Result::eSuccess) {
        *result_listener << "which is " << vkhpp::to_string(result);
        return false;
    }
    return true;
}

MATCHER(IsValidHandle, "a non-null handle") {
    auto& result = arg;
    if (!result) {
        *result_listener << "which is a VK_NULL_HANDLE";
        return false;
    }
    return true;
}

template <typename GlType>
using GlExpected = android::base::expected<GlType, std::string>;

template <typename VkType>
using VkExpected = android::base::expected<VkType, vkhpp::Result>;

#define VK_ASSERT(x)                                                          \
  ({                                                                          \
    auto vk_expect_android_base_expected = (x);                               \
    if (!vk_expect_android_base_expected.ok()) {                              \
      ASSERT_THAT(vk_expect_android_base_expected.ok(), ::testing::IsTrue()); \
    };                                                                        \
    std::move(vk_expect_android_base_expected.value());                       \
  })

#define VK_ASSERT_RV(x)                                                       \
  ({                                                                          \
    auto vkhpp_result_value = (x);                                            \
    ASSERT_THAT(vkhpp_result_value.result, IsVkSuccess());                    \
    std::move(vkhpp_result_value.value);                                      \
  })

#define VK_EXPECT_RESULT(x)                                                   \
  ({                                                                          \
    auto vkhpp_result = (x);                                                  \
    if (vkhpp_result != vkhpp::Result::eSuccess) {                            \
        return android::base::unexpected(vkhpp_result);                       \
    }                                                                         \
  })

#define VK_EXPECT_RV(x)                                                       \
  ({                                                                          \
    auto vkhpp_result_value = (x);                                            \
    if (vkhpp_result_value.result != vkhpp::Result::eSuccess) {               \
        return android::base::unexpected(vkhpp_result_value.result);          \
    }                                                                         \
    std::move(vkhpp_result_value.value);                                      \
  })

#define VK_TRY(x)                                                             \
  ({                                                                          \
    auto vkhpp_result = (x);                                                  \
    if (vkhpp_result != vkhpp::Result::eSuccess) {                            \
        return vkhpp_result;                                                  \
    }                                                                         \
  })

#define VK_TRY_RV(x)                                                          \
  ({                                                                          \
    auto vkhpp_result_value = (x);                                            \
    if (vkhpp_result_value.result != vkhpp::Result::eSuccess) {               \
        return vkhpp_result_value.result;                                     \
    }                                                                         \
    std::move(vkhpp_result_value.value);                                      \
  })

enum class GfxstreamTransport {
  kVirtioGpuAsg,
  kVirtioGpuPipe,
};

struct TestParams {
    bool with_gl;
    bool with_vk;
    bool with_vk_snapshot = false;
    GfxstreamTransport with_transport = GfxstreamTransport::kVirtioGpuAsg;

    std::string ToString() const;
    friend std::ostream& operator<<(std::ostream& os, const TestParams& params);
};

std::string GetTestName(const ::testing::TestParamInfo<TestParams>& info);

class GfxstreamEnd2EndTest : public ::testing::TestWithParam<TestParams> {
  protected:
    struct GuestGlDispatchTable {
        #define DECLARE_EGL_FUNCTION(return_type, function_name, signature) \
            return_type (*function_name) signature = nullptr;

        #define DECLARE_GLES_FUNCTION(return_type, function_name, signature, args) \
            return_type (*function_name) signature = nullptr;

        LIST_RENDER_EGL_FUNCTIONS(DECLARE_EGL_FUNCTION)
        LIST_RENDER_EGL_EXTENSIONS_FUNCTIONS(DECLARE_EGL_FUNCTION)
        LIST_GLES_FUNCTIONS(DECLARE_GLES_FUNCTION, DECLARE_GLES_FUNCTION)
    };

    std::unique_ptr<GuestGlDispatchTable> SetupGuestGl();
    std::unique_ptr<vkhpp::DynamicLoader> SetupGuestVk();

    void SetUp() override;

    void TearDownGuest();
    void TearDownHost();
    void TearDown() override;

    void SetUpEglContextAndSurface(uint32_t contextVersion,
                                   uint32_t width,
                                   uint32_t height,
                                   EGLDisplay* outDisplay,
                                   EGLContext* outContext,
                                   EGLSurface* outSurface);

    void TearDownEglContextAndSurface(EGLDisplay display,
                                      EGLContext context,
                                      EGLSurface surface);

    GlExpected<GLuint> SetUpShader(GLenum type, const std::string& source);

    GlExpected<GLuint> SetUpProgram(const std::string& vertSource,
                                    const std::string& fragSource);

    struct TypicalVkTestEnvironment {
        vkhpp::UniqueInstance instance;
        vkhpp::PhysicalDevice physicalDevice;
        vkhpp::UniqueDevice device;
        vkhpp::Queue queue;
        uint32_t queueFamilyIndex;
    };
    VkExpected<TypicalVkTestEnvironment> SetUpTypicalVkTestEnvironment(
        uint32_t apiVersion = VK_API_VERSION_1_2);

    uint32_t GetMemoryType(const vkhpp::PhysicalDevice& physicalDevice,
                           const vkhpp::MemoryRequirements& memoryRequirements,
                           vkhpp::MemoryPropertyFlags memoryProperties);

    void SnapshotSaveAndLoad();

    std::unique_ptr<ANativeWindowHelper> mAnwHelper;
    std::unique_ptr<Gralloc> mGralloc;
    std::unique_ptr<SyncHelper> mSync;
    std::unique_ptr<GuestGlDispatchTable> mGl;
    std::unique_ptr<vkhpp::DynamicLoader> mVk;
};

}  // namespace tests
}  // namespace gfxstream
