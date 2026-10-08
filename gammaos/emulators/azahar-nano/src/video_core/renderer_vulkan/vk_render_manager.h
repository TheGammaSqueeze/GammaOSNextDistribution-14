// Copyright 2024-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <functional>
#include <mutex>
#include <vector>

#include "common/math_util.h"
#include "video_core/renderer_vulkan/vk_common.h"

namespace VideoCore {
enum class PixelFormat : u32;
}

namespace Vulkan {

class Instance;
class Scheduler;
class Framebuffer;

struct RenderPass {
    vk::Framebuffer framebuffer;
    vk::RenderPass render_pass;
    vk::Rect2D render_area;
    vk::ClearValue clear;
    u32 do_clear;
    // GammaOS folded clears: per-attachment clear values (attachment order) when
    // the pass was begun with deferred clears folded into its load ops.
    std::array<vk::ClearValue, 2> folded_clears{};
    u32 folded_count{};
    vk::RenderPass base_render_pass{};   // the plain (load) render pass it stands in for

    bool operator==(const RenderPass& other) const noexcept {
        return std::tie(framebuffer, render_pass, render_area, do_clear) ==
                   std::tie(other.framebuffer, other.render_pass, other.render_area,
                            other.do_clear) &&
               std::memcmp(&clear, &other.clear, sizeof(vk::ClearValue)) == 0;
    }
};

/// A full-surface clear waiting to be folded into the next render pass that uses
/// the image as an attachment (or recorded on its own before anything else
/// touches the image).
struct PendingClear {
    vk::Image image;
    vk::ClearValue value;
    u32 width;
    u32 height;
    std::function<void()> record;   // records the clear as its own pass
};

class RenderManager {
    static constexpr u32 NumColorFormats = static_cast<u32>(VideoCore::PixelFormat::NumColorFormat);
    static constexpr u32 NumDepthFormats = static_cast<u32>(VideoCore::PixelFormat::NumDepthFormat);

public:
    explicit RenderManager(const Instance& instance, Scheduler& scheduler);
    ~RenderManager();

    /// Begins a new renderpass with the provided framebuffer as render target.
    void BeginRendering(const Framebuffer* framebuffer, Common::Rectangle<u32> draw_rect);

    /// Begins a new renderpass with the provided render state.
    void BeginRendering(const RenderPass& new_pass);

    /// Exits from any currently active renderpass instance
    void EndRendering();

    /// Returns the renderpass associated with the color-depth format pair
    vk::RenderPass GetRenderpass(VideoCore::PixelFormat color, VideoCore::PixelFormat depth,
                                 bool is_clear);

    /// Same, with separate clear (1) / load (0) for color (bit 0) and depth (bit 1)
    vk::RenderPass GetRenderpassMasked(VideoCore::PixelFormat color, VideoCore::PixelFormat depth,
                                       u32 clear_mask);

    /// GammaOS: park a full-surface clear (replaces one already pending for the image)
    void DeferClear(PendingClear&& clear);
    /// Records every pending clear as its own pass (call with no pass open)
    void FlushPendingClears();
    /// The image is being destroyed: nothing can observe it, drop its clear
    void DropPendingClear(vk::Image image);

private:
    /// Creates a renderpass configured appropriately and stores it in cached_renderpasses
    vk::UniqueRenderPass CreateRenderPass(vk::Format color, vk::Format depth,
                                          vk::AttachmentLoadOp color_load,
                                          vk::AttachmentLoadOp depth_load) const;

private:
    const Instance& instance;
    Scheduler& scheduler;
    int gpu_timer_query = -1;   // GammaOS GPU timer pair of the open pass
    vk::UniqueRenderPass cached_renderpasses[NumColorFormats + 1][NumDepthFormats + 1][4];
    std::vector<PendingClear> pending_clears;
    std::mutex cache_mutex;
    std::array<vk::Image, 2> images;
    std::array<vk::ImageAspectFlags, 2> aspects;
    bool shadow_rendering{};
    RenderPass pass{};
    u32 num_draws{};
};

} // namespace Vulkan
