// Copyright 2024-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include "common/assert.h"
#include "video_core/rasterizer_cache/pixel_format.h"
#include "video_core/renderer_vulkan/vk_instance.h"
#include "video_core/renderer_vulkan/vk_render_manager.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"
#include "video_core/renderer_vulkan/vk_gammaos_gpu_timer.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"
#include "video_core/renderer_vulkan/vk_texture_runtime.h"

namespace Vulkan {

constexpr u32 MinDrawsToFlush = 20;

using VideoCore::PixelFormat;
using VideoCore::SurfaceType;

RenderManager::RenderManager(const Instance& instance, Scheduler& scheduler)
    : instance{instance}, scheduler{scheduler} {}

RenderManager::~RenderManager() = default;

void RenderManager::BeginRendering(const Framebuffer* framebuffer,
                                   Common::Rectangle<u32> draw_rect) {
    const vk::Rect2D render_area = {
        .offset{
            .x = static_cast<s32>(draw_rect.left),
            .y = static_cast<s32>(draw_rect.bottom),
        },
        .extent{
            .width = draw_rect.GetWidth(),
            .height = draw_rect.GetHeight(),
        },
    };
    if (GammaGpu::g.enabled && GammaGpu::g.dump[GammaGpu::g.frame] &&
        framebuffer->Handle() != pass.framebuffer) {
        LOG_INFO(Render_Vulkan, "pass target: fb {:X} {}x{} color fmt {} depth fmt {} draw {}x{} at {},{}{}",
                 reinterpret_cast<u64>(static_cast<VkFramebuffer>(framebuffer->Handle())),
                 framebuffer->Width(), framebuffer->Height(),
                 static_cast<int>(framebuffer->Format(VideoCore::SurfaceType::Color)),
                 static_cast<int>(framebuffer->Format(VideoCore::SurfaceType::DepthStencil)),
                 draw_rect.GetWidth(), draw_rect.GetHeight(), draw_rect.left, draw_rect.bottom,
                 framebuffer->shadow_rendering ? " shadow" : "");
    }
    const RenderPass new_pass = {
        .framebuffer = framebuffer->Handle(),
        .render_pass = framebuffer->RenderPass(),
        .render_area = render_area,
        .clear = {},
        .do_clear = false,
    };
    images = framebuffer->Images();
    aspects = framebuffer->Aspects();
    shadow_rendering = framebuffer->shadow_rendering;

    RenderPass folded_pass = new_pass;
    if (!pending_clears.empty() && !shadow_rendering) {
        // Fold pending full-surface clears of this pass's attachments into its
        // load ops when the pass covers the whole framebuffer.
        const auto fb_images = framebuffer->Images();
        const u32 fb_w = framebuffer->Width();
        const u32 fb_h = framebuffer->Height();
        const bool full = draw_rect.left == 0 && draw_rect.bottom == 0 &&
                          draw_rect.GetWidth() == fb_w && draw_rect.GetHeight() == fb_h;
        u32 mask = 0;
        std::array<vk::ClearValue, 2> values{};
        if (full && !(pass == new_pass)) {
            for (u32 i = 0; i < 2; i++) {
                if (!fb_images[i]) continue;
                for (const auto& pc : pending_clears) {
                    if (pc.image == fb_images[i] && pc.width == fb_w && pc.height == fb_h) {
                        mask |= 1u << i;
                        values[i] = pc.value;
                    }
                }
            }
        }
        if (mask) {
            std::erase_if(pending_clears, [&](const PendingClear& pc) {
                return (pc.image == fb_images[0] && (mask & 1)) ||
                       (pc.image == fb_images[1] && (mask & 2));
            });
            folded_pass.render_pass = GetRenderpassMasked(framebuffer->Format(VideoCore::SurfaceType::Color),
                                                          framebuffer->Format(VideoCore::SurfaceType::DepthStencil),
                                                          mask);
            folded_pass.base_render_pass = new_pass.render_pass;
            u32 n = 0;
            if (fb_images[0]) folded_pass.folded_clears[n++] = values[0];
            if (fb_images[1]) folded_pass.folded_clears[n++] = values[1];
            folded_pass.folded_count = n;
        }
    }
    BeginRendering(folded_pass);
}

void RenderManager::DeferClear(PendingClear&& clear) {
    std::erase_if(pending_clears,
                  [&](const PendingClear& pc) { return pc.image == clear.image; });
    pending_clears.push_back(std::move(clear));
}

void RenderManager::FlushPendingClears() {
    if (pending_clears.empty()) return;
    auto clears = std::move(pending_clears);
    pending_clears.clear();
    for (auto& pc : clears) {
        pc.record();
    }
}

void RenderManager::DropPendingClear(vk::Image image) {
    std::erase_if(pending_clears, [&](const PendingClear& pc) { return pc.image == image; });
}

void RenderManager::BeginRendering(const RenderPass& new_pass) {
    if (pass == new_pass) [[likely]] {
        num_draws++;
        return;
    }
    // A folded pass is still open: plain draws to the same framebuffer and area
    // continue it (the clear already happened at its start).
    if (pass.render_pass && pass.folded_count && pass.framebuffer == new_pass.framebuffer &&
        pass.base_render_pass == new_pass.render_pass && pass.render_area == new_pass.render_area &&
        !new_pass.do_clear && !new_pass.folded_count) {
        num_draws++;
        return;
    }
    GammaStats::Add(GammaStats::RenderPasses);
    if (pass.render_pass && pass.framebuffer == new_pass.framebuffer &&
        pass.render_pass == new_pass.render_pass && !new_pass.do_clear) {
        GammaStats::Add(GammaStats::PassBreaksAreaOnly);
    }
    // With the one-shot gputime dump armed, say why this pass could not continue the
    // open one (profiling: pass counts per frame differ between drivers).
    if (GammaGpu::g.enabled && GammaGpu::g.dump[GammaGpu::g.frame]) {
        LOG_INFO(Render_Vulkan,
                 "pass break: open {} -> new {}{}{}{}{}{} | new area {}x{} at {},{} clear {} folded {} fb {:X}",
                 pass.render_pass ? "pass" : "none", new_pass.folded_count ? "folded" : "plain",
                 pass.framebuffer != new_pass.framebuffer ? " fb" : "",
                 pass.render_pass != new_pass.render_pass ? " rp" : "",
                 pass.render_area != new_pass.render_area ? " area" : "",
                 pass.do_clear != new_pass.do_clear ? " do_clear" : "",
                 std::memcmp(&pass.clear, &new_pass.clear, sizeof(vk::ClearValue)) != 0 ? " clearval" : "",
                 new_pass.render_area.extent.width, new_pass.render_area.extent.height,
                 new_pass.render_area.offset.x, new_pass.render_area.offset.y, new_pass.do_clear,
                 new_pass.folded_count, reinterpret_cast<u64>(static_cast<VkFramebuffer>(new_pass.framebuffer)));
    }

    EndRendering();
    // Clears not folded into this pass are recorded first, in order.
    if (!pending_clears.empty()) {
        if (new_pass.folded_count) {
            // keep pending ones that this pass does not use; they are separate images
        }
        FlushPendingClears();
    }
    // GammaOS: the label string is built only for a frame being dumped (it was a heap string
    // per pass, 0.3 percent of the emulation thread in Kirby: Planet Robobot).
    gpu_timer_query = GammaGpu::Begin(
        scheduler, GammaGpu::g.presenting ? GammaGpu::Present : GammaGpu::Pass,
        GammaGpu::WantLabel() ? fmt::format("area {}x{} at {},{} clear {} folded {} fb {:X}", new_pass.render_area.extent.width,
                    new_pass.render_area.extent.height, new_pass.render_area.offset.x,
                    new_pass.render_area.offset.y, new_pass.do_clear, new_pass.folded_count,
                    reinterpret_cast<u64>(static_cast<VkFramebuffer>(new_pass.framebuffer))) : std::string{});
    scheduler.Record([info = new_pass](vk::CommandBuffer cmdbuf) {
        const vk::RenderPassBeginInfo renderpass_begin_info = {
            .renderPass = info.render_pass,
            .framebuffer = info.framebuffer,
            .renderArea = info.render_area,
            .clearValueCount = info.folded_count ? info.folded_count : (info.do_clear ? 1u : 0u),
            .pClearValues = info.folded_count ? info.folded_clears.data() : &info.clear,
        };
        cmdbuf.beginRenderPass(renderpass_begin_info, vk::SubpassContents::eInline);
    });

    pass = new_pass;
}

void RenderManager::EndRendering() {
    if (!pass.render_pass) {
        return;
    }

    scheduler.Record([images = images, aspects = aspects,
                      shadow_rendering = shadow_rendering](vk::CommandBuffer cmdbuf) {
        u32 num_barriers = 0;
        vk::PipelineStageFlags pipeline_flags{};
        vk::AccessFlags src_access_flags{};
        std::array<vk::ImageMemoryBarrier, 2> barriers;
        for (u32 i = 0; i < images.size(); i++) {
            if (!images[i]) {
                continue;
            }
            const bool is_color = static_cast<bool>(aspects[i] & vk::ImageAspectFlagBits::eColor);
            if (is_color) {
                pipeline_flags |= shadow_rendering
                                      ? vk::PipelineStageFlagBits::eFragmentShader
                                      : vk::PipelineStageFlagBits::eColorAttachmentOutput;
                src_access_flags = shadow_rendering ? vk::AccessFlagBits::eShaderWrite
                                                    : vk::AccessFlagBits::eColorAttachmentWrite;
            } else {
                pipeline_flags |= vk::PipelineStageFlagBits::eEarlyFragmentTests |
                                  vk::PipelineStageFlagBits::eLateFragmentTests;
                src_access_flags = vk::AccessFlagBits::eDepthStencilAttachmentWrite;
            }
            barriers[num_barriers++] = vk::ImageMemoryBarrier{
                .srcAccessMask = src_access_flags,
                .dstAccessMask =
                    vk::AccessFlagBits::eShaderRead | vk::AccessFlagBits::eTransferRead,
                .oldLayout = vk::ImageLayout::eGeneral,
                .newLayout = vk::ImageLayout::eGeneral,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .image = images[i],
                .subresourceRange{
                    .aspectMask = aspects[i],
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = VK_REMAINING_ARRAY_LAYERS,
                },
            };
        }
        cmdbuf.endRenderPass();
        if (num_barriers == 0) {
            return;
        }
        cmdbuf.pipelineBarrier(pipeline_flags,
                               vk::PipelineStageFlagBits::eFragmentShader |
                                   vk::PipelineStageFlagBits::eTransfer,
                               vk::DependencyFlagBits::eByRegion, 0, nullptr, 0, nullptr,
                               num_barriers, barriers.data());
    });

    GammaGpu::End(scheduler, gpu_timer_query);
    gpu_timer_query = -1;

    // Reset state.
    pass.render_pass = VK_NULL_HANDLE;
    images = {};
    aspects = {};
    shadow_rendering = false;

    // The Mali guide recommends flushing at the end of each major renderpass
    // Testing has shown this has a significant effect on rendering performance
    if (num_draws > MinDrawsToFlush && instance.ShouldFlush()) {
        scheduler.Flush();
        num_draws = 0;
    }
}

vk::RenderPass RenderManager::GetRenderpass(VideoCore::PixelFormat color,
                                            VideoCore::PixelFormat depth, bool is_clear) {
    std::scoped_lock lock{cache_mutex};

    const u32 color_index =
        color == VideoCore::PixelFormat::Invalid ? NumColorFormats : static_cast<u32>(color);
    const u32 depth_index =
        depth == VideoCore::PixelFormat::Invalid
            ? NumDepthFormats
            : (static_cast<u32>(depth - VideoCore::PixelFormat::NumColorFormat));

    ASSERT_MSG(color_index <= NumColorFormats && depth_index <= NumDepthFormats,
               "Invalid color index {} and/or depth_index {}", color_index, depth_index);

    vk::UniqueRenderPass& renderpass = cached_renderpasses[color_index][depth_index][is_clear];
    if (!renderpass) {
        const vk::Format color_format = instance.GetTraits(color).native;
        const vk::Format depth_format = instance.GetTraits(depth).native;
        const vk::AttachmentLoadOp load_op =
            is_clear ? vk::AttachmentLoadOp::eClear : vk::AttachmentLoadOp::eLoad;
        renderpass = CreateRenderPass(color_format, depth_format, load_op, load_op);
    }

    return *renderpass;
}

vk::RenderPass RenderManager::GetRenderpassMasked(VideoCore::PixelFormat color,
                                                  VideoCore::PixelFormat depth, u32 clear_mask) {
    std::scoped_lock lock{cache_mutex};
    const u32 color_index =
        color == VideoCore::PixelFormat::Invalid ? NumColorFormats : static_cast<u32>(color);
    const u32 depth_index =
        depth == VideoCore::PixelFormat::Invalid
            ? NumDepthFormats
            : (static_cast<u32>(depth - VideoCore::PixelFormat::NumColorFormat));
    // Slots: 0 = load, 1 = clear both (the existing is_clear), 2 = clear color
    // only, 3 = clear depth only.
    const u32 slot = clear_mask == 3 ? 1 : (clear_mask == 1 ? 2 : (clear_mask == 2 ? 3 : 0));
    vk::UniqueRenderPass& renderpass = cached_renderpasses[color_index][depth_index][slot];
    if (!renderpass) {
        const vk::Format color_format = instance.GetTraits(color).native;
        const vk::Format depth_format = instance.GetTraits(depth).native;
        const auto op = [&](u32 bit) {
            return (clear_mask & bit) ? vk::AttachmentLoadOp::eClear : vk::AttachmentLoadOp::eLoad;
        };
        renderpass = CreateRenderPass(color_format, depth_format, op(1), op(2));
    }
    return *renderpass;
}

vk::UniqueRenderPass RenderManager::CreateRenderPass(vk::Format color, vk::Format depth,
                                                     vk::AttachmentLoadOp color_load,
                                                     vk::AttachmentLoadOp depth_load) const {
    u32 attachment_count = 0;
    std::array<vk::AttachmentDescription, 2> attachments;

    bool use_color = false;
    vk::AttachmentReference color_attachment_ref{};
    bool use_depth = false;
    vk::AttachmentReference depth_attachment_ref{};

    if (color != vk::Format::eUndefined) {
        attachments[attachment_count] = vk::AttachmentDescription{
            .format = color,
            .loadOp = color_load,
            .storeOp = vk::AttachmentStoreOp::eStore,
            .stencilLoadOp = vk::AttachmentLoadOp::eDontCare,
            .stencilStoreOp = vk::AttachmentStoreOp::eDontCare,
            .initialLayout = vk::ImageLayout::eGeneral,
            .finalLayout = vk::ImageLayout::eGeneral,
        };

        color_attachment_ref = vk::AttachmentReference{
            .attachment = attachment_count++,
            .layout = vk::ImageLayout::eGeneral,
        };

        use_color = true;
    }

    if (depth != vk::Format::eUndefined) {
        attachments[attachment_count] = vk::AttachmentDescription{
            .format = depth,
            .loadOp = depth_load,
            .storeOp = vk::AttachmentStoreOp::eStore,
            .stencilLoadOp = depth_load,
            .stencilStoreOp = vk::AttachmentStoreOp::eStore,
            .initialLayout = vk::ImageLayout::eGeneral,
            .finalLayout = vk::ImageLayout::eGeneral,
        };

        depth_attachment_ref = vk::AttachmentReference{
            .attachment = attachment_count++,
            .layout = vk::ImageLayout::eGeneral,
        };

        use_depth = true;
    }

    const vk::SubpassDescription subpass = {
        .pipelineBindPoint = vk::PipelineBindPoint::eGraphics,
        .inputAttachmentCount = 0,
        .pInputAttachments = nullptr,
        .colorAttachmentCount = use_color ? 1u : 0u,
        .pColorAttachments = &color_attachment_ref,
        .pResolveAttachments = 0,
        .pDepthStencilAttachment = use_depth ? &depth_attachment_ref : nullptr,
    };

    const vk::RenderPassCreateInfo renderpass_info = {
        .attachmentCount = attachment_count,
        .pAttachments = attachments.data(),
        .subpassCount = 1,
        .pSubpasses = &subpass,
        .dependencyCount = 0,
        .pDependencies = nullptr,
    };

    return instance.GetDevice().createRenderPassUnique(renderpass_info);
}

} // namespace Vulkan
