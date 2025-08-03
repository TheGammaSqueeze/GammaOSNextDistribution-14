add_subdirectory(cereal)
add_subdirectory(emulated_textures)

add_library(gfxstream-vulkan-server
            BorrowedImageVk.cpp
            BufferVk.cpp
            ColorBufferVk.cpp
            CompositorVk.cpp
            DisplayVk.cpp
            DisplaySurfaceVk.cpp
            DebugUtilsHelper.cpp
            PostWorkerVk.cpp
            SwapChainStateVk.cpp
            RenderThreadInfoVk.cpp
            VkAndroidNativeBuffer.cpp
            VkCommonOperations.cpp
            VkDecoder.cpp
            VkDecoderGlobalState.cpp
            VkDecoderSnapshot.cpp
            VkDecoderSnapshotUtils.cpp
            VkFormatUtils.cpp
            VkReconstruction.cpp
            VulkanDispatch.cpp
            VulkanHandleMapping.cpp
            VulkanStream.cpp
            vk_util.cpp)
set_source_files_properties(VkDecoder.cpp PROPERTIES COMPILE_FLAGS -Wno-unused-variable)

target_link_libraries(gfxstream-vulkan-server PUBLIC
        OpenglRender_vulkan_cereal
        gfxstream-compressedTextures
        emulated_textures
        apigen-codec-common
        aemu-base.headers
        gfxstream-snapshot.headers
        gfxstream_utils
        gfxstream_vulkan_headers
        PRIVATE
        gfxstream_egl_headers)

if (WIN32)
target_compile_definitions(gfxstream-vulkan-server PRIVATE -DVK_USE_PLATFORM_WIN32_KHR)
elseif (APPLE)
target_compile_definitions(gfxstream-vulkan-server PRIVATE -DVK_USE_PLATFORM_METAL_EXT)
elseif (QNX)
target_compile_definitions(gfxstream-vulkan-server PRIVATE -DVK_USE_PLATFORM_SCREEN_QNX)
endif()

if (NOT MSVC)
    target_compile_options(gfxstream-vulkan-server PRIVATE -fvisibility=hidden)
endif()
target_compile_options(gfxstream-vulkan-server PRIVATE -Wno-unused-value -Wno-return-type -Wno-return-type-c-linkage)

target_include_directories(gfxstream-vulkan-server
                           PUBLIC
                           ${GFXSTREAM_REPO_ROOT}/vulkan
                           ${GFXSTREAM_REPO_ROOT}/vulkan/cereal
                           PRIVATE
                           ${GFXSTREAM_REPO_ROOT}
                           ${GFXSTREAM_REPO_ROOT}/include
                           ${GFXSTREAM_REPO_ROOT}/host
                           ${GFXSTREAM_REPO_ROOT}/host/vulkan
                           ${GFXSTREAM_REPO_ROOT}/host/vulkan/cereal/common
                           ${GFXSTREAM_REPO_ROOT}/host/apigen-codec-common
                           ${GFXSTREAM_REPO_ROOT}/third-party/glm/include)
