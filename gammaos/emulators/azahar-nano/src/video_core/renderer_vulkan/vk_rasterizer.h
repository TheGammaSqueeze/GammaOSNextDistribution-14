// Copyright 2022-2025 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <unordered_map>

#include "video_core/rasterizer_accelerated.h"
#include "video_core/renderer_vulkan/vk_descriptor_update_queue.h"
#include "video_core/renderer_vulkan/vk_pipeline_cache.h"
#include "video_core/renderer_vulkan/vk_render_manager.h"
#include "video_core/renderer_vulkan/vk_stream_buffer.h"
#include "video_core/renderer_vulkan/vk_texture_runtime.h"

namespace Frontend {
class EmuWindow;
}

namespace VideoCore {
class CustomTexManager;
class RendererBase;
} // namespace VideoCore

namespace Pica {
struct DisplayTransferConfig;
struct MemoryFillConfig;
struct FramebufferConfig;
} // namespace Pica

namespace Vulkan {

struct ScreenInfo;

class Instance;
class Scheduler;
class RenderManager;

class RasterizerVulkan : public VideoCore::RasterizerAccelerated {
public:
    explicit RasterizerVulkan(Memory::MemorySystem& memory, Pica::PicaCore& pica,
                              VideoCore::CustomTexManager& custom_tex_manager,
                              VideoCore::RendererBase& renderer, Frontend::EmuWindow& emu_window,
                              const Instance& instance, Scheduler& scheduler,
                              RenderManager& renderpass_cache, DescriptorUpdateQueue& update_queue,
                              u32 image_count);
    ~RasterizerVulkan() override;

    void TickFrame();
    void LoadDefaultDiskResources(const std::atomic_bool& stop_loading,
                                  const VideoCore::DiskResourceLoadCallback& callback) override;

    void DrawTriangles() override;
    void FlushAll() override;
    void FlushRegion(PAddr addr, u32 size) override;
    void InvalidateRegion(PAddr addr, u32 size) override;
    void FlushAndInvalidateRegion(PAddr addr, u32 size) override;
    bool TwinTargets(PAddr cur_color, PAddr cur_depth, PAddr next_color, PAddr next_depth,
                     u32 color_size, u32 depth_size, std::span<const PAddr> textures,
                     PAddr& canon_color, PAddr& canon_depth) override;
    void SetTwinDraw(bool active) override;
    u32 TakeTwinStats(u32& copies) {
        return res_cache.TakeTwinStats(copies);
    }
    void ClearAll(bool flush) override;
    bool AccelerateDisplayTransfer(const Pica::DisplayTransferConfig& config) override;
    bool AccelerateTextureCopy(const Pica::DisplayTransferConfig& config) override;
    bool AccelerateFill(const Pica::MemoryFillConfig& config) override;
    bool AccelerateDisplay(const Pica::FramebufferConfig& config, PAddr framebuffer_addr,
                           u32 pixel_stride, ScreenInfo& screen_info);
    bool AccelerateDrawBatch(bool is_indexed) override;

    /// Switches the disk resources to the specified title
    void SwitchDiskResources(u64 title_id) override;

private:
    u64 previous_frame_mark = 0;

    /// Syncs pipeline state from PICA registers
    void SyncDrawState();

    /// Syncs and uploads the lighting, fog and proctex LUTs
    void SyncAndUploadLUTs();
    void SyncAndUploadLUTsLF();

    /// Syncs all enabled PICA texture units
    void SyncTextureUnits(const Framebuffer* framebuffer);

    /// Syncs all utility textures in the fragment shader.
    void SyncUtilityTextures(const Framebuffer* framebuffer);

    /// Binds the PICA shadow cube required for shadow mapping
    void BindShadowCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                        vk::DescriptorSet texture_set);

    /// Binds a texture cube to texture unit 0
    void BindTextureCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                         vk::DescriptorSet texture_set);

    /// Upload the uniform blocks to the uniform buffer object
    void UploadUniforms(bool accelerate_draw);

    /// Generic draw function for DrawTriangles and AccelerateDrawBatch
    bool Draw(bool accelerate, bool is_indexed);

    /// Internal implementation for AccelerateDrawBatch
    bool AccelerateDrawBatchInternal(bool is_indexed);

    /// Setup index array for AccelerateDrawBatch
    void SetupIndexArray();

    /// Setup vertex array for AccelerateDrawBatch
    void SetupVertexArray();

    /// Setup the fixed attribute emulation in vulkan
    void SetupFixedAttribs();

    /// Setup vertex shader for AccelerateDrawBatch
    bool SetupVertexShader();

    /// Setup geometry shader for AccelerateDrawBatch
    bool SetupGeometryShader();

    /// Creates the vertex layout struct used for software shader pipelines
    void MakeSoftwareVertexLayout();

private:
    const Instance& instance;
    Scheduler& scheduler;
    RenderManager& renderpass_cache;
    DescriptorUpdateQueue& update_queue;
    PipelineCache pipeline_cache;
    int gamma_dump_query = -1;   // GammaOS frame dump: query of the draw being recorded

    // GammaOS right-eye skip. Some games (Super Mario 3D Land menus) submit both
    // eyes of the top screen in one command list, which the command-list level
    // RightEyeDisabler cannot split. Here the right-eye batch is recognised at
    // the draw level: after the display transfer to the left-eye framebuffer,
    // the draws into the same render target that end in a transfer to the
    // right-eye framebuffer. Once seen, the next frames skip those draws and
    // that transfer. Only with "disable right eye render" and 3D off, when the
    // right-eye framebuffer is never displayed.
    enum class EyePhase { Idle, AfterLeft, Skipping };
    EyePhase eye_phase = EyePhase::Idle;
    bool hybrid_fallback = false;
    bool hybrid_baked = false; ///< GammaOS: this fallback draw uses a precompiled baked shader
    void PrepareUberDraw(u32& variant_key, std::array<u32, 16>& tev_pack, bool upload);
    u32 fallback_vertices_this_frame = 0; ///< GammaOS: CPU vertex budget of fallback draws
    u32 frame_index = 1;
    std::unordered_map<u64, u32> budget_held_since; ///< pipeline hash -> first held frame ///< GammaOS: this software draw replaces a declined hw draw
    PAddr eye_rt = 0;               // render target of the left-eye transfer
    PAddr eye_left_out = 0;         // where the left-eye image went
    bool eye_learned = false;       // the pattern was seen in a previous frame
    PAddr stereo_right_rt = 0;      // GammaOS: right eye target of interleaved stereo games
    PAddr last_left_src = 0;
    u32 stereo_right_w = 0, stereo_right_h = 0; // size of the right eye target        // GammaOS: last surface transferred to a left framebuffer
    bool eye_draws_after_left = false;
    bool RightEyeSkipAllowed() const;
    bool OnDisplayTransferEye(const Pica::DisplayTransferConfig& config);   // false = skip it
    TextureRuntime runtime;
    RasterizerCache res_cache;

    VertexLayout software_layout;
    std::array<u32, 16> binding_offsets{};
    std::array<bool, 16> enable_attributes{};
    std::array<vk::Buffer, 16> vertex_buffers;
    VertexArrayInfo vertex_info;
    PipelineInfo pipeline_info{};

    StreamBuffer stream_buffer;     ///< Vertex+Index buffer
    StreamBuffer uniform_buffer;    ///< Uniform buffer
    StreamBuffer texture_buffer;    ///< Texture buffer
    StreamBuffer texture_lf_buffer; ///< Texture Light-Fog buffer
    vk::UniqueBufferView texture_lf_view;
    vk::UniqueBufferView texture_rg_view;
    vk::UniqueBufferView texture_rgba_view;
    vk::DeviceSize uniform_buffer_alignment;
    u32 uniform_size_aligned_vs_pica;
    u32 uniform_size_aligned_vs;
    u32 uniform_size_aligned_fs;
    bool async_shaders{false};
};

} // namespace Vulkan
