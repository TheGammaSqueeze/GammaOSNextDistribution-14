// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "common/common_types.h"
#include "common/math_util.h"
#include "video_core/renderer_base.h"
#ifdef HAVE_LIBRETRO
#include "citra_libretro/libretro_vk.h"
#else
#include "video_core/renderer_vulkan/vk_instance.h"
#include "video_core/renderer_vulkan/vk_present_window.h"
#endif
#include "video_core/renderer_vulkan/vk_rasterizer.h"
#include "video_core/renderer_vulkan/vk_render_manager.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"

namespace Core {
class System;
}

namespace Memory {
class MemorySystem;
}

namespace Pica {
class PicaCore;
}

namespace Layout {
struct FramebufferLayout;
}

namespace VideoCore {
class GPU;
}

namespace Vulkan {

struct TextureInfo {
    u32 width;
    u32 height;
    Pica::PixelFormat format;
    vk::Image image;
    vk::ImageView image_view;
    VmaAllocation allocation;
};

struct ScreenInfo {
    TextureInfo texture;
    Common::Rectangle<f32> texcoords;
    vk::ImageView image_view;
    /// GammaOS: size of the image behind image_view (the Screen AA prepass renders at it).
    u32 view_width = 0;
    u32 view_height = 0;
};

struct PresentUniformData {
    std::array<f32, 4 * 4> modelview;
    Common::Vec4f i_resolution;
    Common::Vec4f o_resolution;
    int screen_id_l = 0;
    int screen_id_r = 0;
    int layer = 0;
    int reverse_interlaced = 0;
    // GammaOS LCD shaders: 3DS screen pixel = (tex.y * x + y, tex.x * z + w).
    Common::Vec4f lcd_map;
};
// 128 bytes: the push constant size every Vulkan device supports.
static_assert(sizeof(PresentUniformData) == 128,
              "PresentUniformData does not structure in shader!");

class RendererVulkan : public VideoCore::RendererBase {
    static constexpr std::size_t PRESENT_PIPELINES = 3;

public:
    explicit RendererVulkan(Core::System& system, Pica::PicaCore& pica, Frontend::EmuWindow& window,
                            Frontend::EmuWindow* secondary_window,
                            Pica::PicaCore* draw_pica = nullptr);
    ~RendererVulkan() override;

    [[nodiscard]] VideoCore::RasterizerInterface* Rasterizer() override {
        return &rasterizer;
    }

    void NotifySurfaceChanged(bool second) override;
    void NotifySurfaceResized(bool second) override;

    void SwapBuffers() override;
    void TryPresent(int timeout_ms, bool is_secondary) override {}

private:
    void ReloadPipeline(Settings::StereoRenderOption render_3d);
    void CompileShaders();
    void BuildLayouts();
    void BuildPipelines();
    vk::Pipeline BuildPresentPipeline(vk::ShaderModule fragment_shader);

    /// GammaOS Screen AA prepass: FXAA once per emulated pixel into a small target per
    /// screen, which the present pass then samples (instead of FXAA per panel pixel).
    bool ScreenAaWanted() const;
    void RunScreenAa();
    bool EnsureAaTarget(u32 index, u32 width, u32 height);
    vk::Pipeline BuildAaPipeline();
    /// The present pipeline for a screen draw: its LCD shader when one is selected.
    /// The present pipeline for a screen (stock, or a GammaOS screen shader); user_sampler is
    /// set when it samples through the Linear Filtering sampler rather than the linear one.
    vk::Pipeline ScreenPipeline(u32 screen_id, bool& user_sampler);
    void ConfigureFramebufferTexture(TextureInfo& texture,
                                     const Pica::FramebufferConfig& framebuffer);
    void ConfigureRenderPipeline();
    void PrepareRendertarget();
    void RenderScreenshot();
    void RenderScreenshotWithStagingCopy();
    bool TryRenderScreenshotWithHostMemory();
    void PrepareDraw(Frame* frame, const Layout::FramebufferLayout& layout);
    void RenderToWindow(PresentWindow& window, const Layout::FramebufferLayout& layout,
                        bool flipped);

    void DrawScreens(Frame* frame, const Layout::FramebufferLayout& layout, bool flipped);
    void DrawBottomScreen(const Layout::FramebufferLayout& layout,
                          const Common::Rectangle<u32>& bottom_screen);

    void DrawTopScreen(const Layout::FramebufferLayout& layout,
                       const Common::Rectangle<u32>& top_screen);
    void DrawSingleScreen(u32 screen_id, float x, float y, float w, float h,
                          Layout::DisplayOrientation orientation);
    void DrawSingleScreenStereo(u32 screen_id_l, u32 screen_id_r, float x, float y, float w,
                                float h, Layout::DisplayOrientation orientation);

    void ApplySecondLayerOpacity(float alpha);

    void DrawCursor(const Layout::FramebufferLayout& layout);

    void LoadFBToScreenInfo(const Pica::FramebufferConfig& framebuffer, ScreenInfo& screen_info,
                            bool right_eye);
    void FillScreen(Common::Vec3<u8> color, const TextureInfo& texture);

private:
    Memory::MemorySystem& memory;
    Pica::PicaCore& pica;
    /// GammaOS GPU worker split: the core the rasterizer draws from (pica when not split).
    Pica::PicaCore& draw_pica;

#ifdef HAVE_LIBRETRO
    LibRetroVKInstance instance;
#else
    Instance instance;
#endif
    Scheduler scheduler;
    RenderManager renderpass_cache;
    PresentWindow main_present_window;
    StreamBuffer vertex_buffer;
    DescriptorUpdateQueue update_queue;
    RasterizerVulkan rasterizer;
    /// GammaOS: this swap's frame misses draws whose pipeline is still compiling: keep the last
    /// complete frame on the panels instead (a held repeat, not a flicker).
    bool hold_present = false;
    u32 held_incomplete = 0;
    std::unique_ptr<PresentWindow> secondary_present_window_ptr;
    DescriptorHeap present_heap;
    vk::UniquePipelineLayout present_pipeline_layout;
    struct AaTarget {
        vk::Image image{};
        VmaAllocation allocation{};
        vk::ImageView view{};
        vk::Framebuffer framebuffer{};
        u32 width = 0;
        u32 height = 0;
    };
    std::array<AaTarget, 3> aa_targets{};
    vk::ShaderModule aa_vertex_shader{};
    vk::ShaderModule aa_fragment_shader{};
    vk::Pipeline aa_pipeline{};
    bool aa_failed = false;
    bool aa_prepass_done = false; ///< this swap's screens were anti-aliased by the prepass
    std::array<vk::Pipeline, PRESENT_PIPELINES> present_pipelines;
    std::array<vk::ShaderModule, PRESENT_PIPELINES> present_shaders;
    // GammaOS LCD shader pipelines, built the first time each is selected.
    // GammaOS screen shaders: slot = shader index (1..6, 0 = the plain FXAA one), plus 7 when
    // Screen AA is on.
    std::array<vk::Pipeline, 14> lcd_pipelines{};
    std::array<vk::ShaderModule, 14> lcd_shaders{};
    // This frame's present descriptor sets: the user's sampler, and linear for the shaders.
    vk::DescriptorSet present_set_user{}, present_set_linear{};
    std::array<vk::Sampler, 2> present_samplers;
    vk::ShaderModule present_vertex_shader;
    u32 current_pipeline = 0;

    std::array<ScreenInfo, 3> screen_infos{};
    PresentUniformData draw_info{};
    vk::ClearColorValue clear_color{};

    vk::ShaderModule cursor_vertex_shader{};
    vk::ShaderModule cursor_fragment_shader{};
    vk::Pipeline cursor_pipeline{};
    vk::UniquePipelineLayout cursor_pipeline_layout{};
    bool isSecondaryWindow;
    bool secondaryWindowEnabled;
    bool screenRendered;
    // GammaOS: the pass being drawn is turned by 180 degrees (DrawScreens' flipped flag):
    // every screen rectangle is mirrored in the layout and its texture read the other way.
    bool turn_180 = false;
    float turn_w = 0.0f, turn_h = 0.0f;
};

} // namespace Vulkan
