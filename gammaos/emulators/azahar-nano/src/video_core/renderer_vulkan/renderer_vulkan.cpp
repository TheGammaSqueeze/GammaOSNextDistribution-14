// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include "common/assert.h"
#include "common/logging/log.h"
#include "common/memory_detect.h"
#include "common/microprofile.h"
#include "common/settings.h"
#include "core/core.h"
#include "core/frontend/emu_window.h"
#include "video_core/gpu.h"
#include "video_core/pica/pica_core.h"
#include "video_core/renderer_vulkan/renderer_vulkan.h"
#include "video_core/renderer_vulkan/gammaos_lcd_shaders.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"
#include "video_core/renderer_vulkan/vk_gammaos_gpu_timer.h"
#include "video_core/renderer_vulkan/vk_memory_util.h"
#include "video_core/renderer_vulkan/vk_shader_util.h"

#include "video_core/host_shaders/vulkan_present_anaglyph_frag.h"
#include "video_core/host_shaders/vulkan_present_frag.h"
#include "video_core/host_shaders/vulkan_present_interlaced_frag.h"
#include "video_core/host_shaders/vulkan_present_vert.h"

#include "video_core/host_shaders/vulkan_cursor_frag.h"
#include "video_core/host_shaders/vulkan_cursor_vert.h"

#include <vk_mem_alloc.h>
#if defined(__APPLE__) && !defined(HAVE_LIBRETRO)
#include "common/apple_utils.h"
#endif

#ifdef ENABLE_SDL2
#include <SDL.h>
#endif

MICROPROFILE_DEFINE(Vulkan_RenderFrame, "Vulkan", "Render Frame", MP_RGB(128, 128, 64));

namespace Vulkan {

struct ScreenRectVertex {
    ScreenRectVertex() = default;
    ScreenRectVertex(float x, float y, float u, float v)
        : position{Common::MakeVec(x, y)}, tex_coord{Common::MakeVec(u, v)} {}

    Common::Vec2f position;
    Common::Vec2f tex_coord;
};

constexpr u32 VERTEX_BUFFER_SIZE = sizeof(ScreenRectVertex) * 8192;

constexpr std::array<f32, 4 * 4> MakeOrthographicMatrix(u32 width, u32 height) {
    // clang-format off
    return { 2.f / width, 0.f,         0.f, -1.f,
            0.f,         2.f / height, 0.f, -1.f,
            0.f,         0.f,          1.f,  0.f,
            0.f,         0.f,          0.f,  1.f};
    // clang-format on
}

constexpr static std::array<vk::DescriptorSetLayoutBinding, 1> PRESENT_BINDINGS = {{
    {0, vk::DescriptorType::eCombinedImageSampler, 3, vk::ShaderStageFlagBits::eFragment},
}};

namespace {
static bool IsLowRefreshRate() {
#if (defined(__APPLE__) || defined(ENABLE_SDL2)) && !defined(HAVE_LIBRETRO)
    if (!Settings::values.use_display_refresh_rate_detection) {
        LOG_INFO(Render_Vulkan, "Refresh rate detection is currently disabled via settings");
        return false;
    }
#ifdef __APPLE__
    // Apple's low power mode sometimes limits applications to 30fps without changing the refresh
    // rate, meaning the above code doesn't catch it.
    if (AppleUtils::IsLowPowerModeEnabled()) {
        LOG_WARNING(Render_Vulkan, "Apple's low power mode is enabled, assuming low application "
                                   "framerate. FIFO will be disabled");
        return true;
    }

    const auto cur_refresh_rate = AppleUtils::GetRefreshRate();
#elif defined(ENABLE_SDL2)
    if (SDL_WasInit(SDL_INIT_VIDEO) == 0) {
        LOG_ERROR(Render_Vulkan, "Attempted to check refresh rate via SDL, but failed because "
                                 "SDL_INIT_VIDEO wasn't initialized");
        return false;
    }

    SDL_DisplayMode cur_display_mode;
    SDL_GetCurrentDisplayMode(0, &cur_display_mode); // TODO: Multimonitor handling. -OS

    const auto cur_refresh_rate = cur_display_mode.refresh_rate;
#endif // ENABLE_SDL2

    if (cur_refresh_rate < SCREEN_REFRESH_RATE) {
        LOG_WARNING(Render_Vulkan,
                    "Detected refresh rate lower than the emulated 3DS screen: {}hz. FIFO will "
                    "be disabled",
                    cur_refresh_rate);
        return true;
    } else {
        LOG_INFO(Render_Vulkan, "Refresh rate is above emulated 3DS screen: {}hz. Good.",
                 cur_refresh_rate);
    }
#endif // (defined(__APPLE__) || defined(ENABLE_SDL2)) && !defined(HAVE_LIBRETRO)

    // We have no available method of checking refresh rate. Just assume that everything is fine :)
    return false;
}
} // Anonymous namespace

RendererVulkan::RendererVulkan(Core::System& system, Pica::PicaCore& pica_,
                               Frontend::EmuWindow& window, Frontend::EmuWindow* secondary_window)
    : RendererBase{system, window, secondary_window}, memory{system.Memory()}, pica{pica_},
      instance{window, Settings::values.physical_device.GetValue()}, scheduler{instance},
      renderpass_cache{instance, scheduler},
      main_present_window{window, instance, scheduler, IsLowRefreshRate()},
      vertex_buffer{instance, scheduler, vk::BufferUsageFlagBits::eVertexBuffer,
                    VERTEX_BUFFER_SIZE},
      update_queue{instance}, rasterizer{memory,
                                         pica,
                                         system.CustomTexManager(),
                                         *this,
                                         render_window,
                                         instance,
                                         scheduler,
                                         renderpass_cache,
                                         update_queue,
                                         main_present_window.ImageCount()},
      present_heap{instance, scheduler.GetMasterSemaphore(), PRESENT_BINDINGS, 32} {
    CompileShaders();
    BuildLayouts();
    BuildPipelines();
    if (secondary_window) {
        secondary_present_window_ptr = std::make_unique<PresentWindow>(
            *secondary_window, instance, scheduler, IsLowRefreshRate());
    }
}

RendererVulkan::~RendererVulkan() {
    vk::Device device = instance.GetDevice();
    scheduler.Finish();
    main_present_window.WaitPresent();
    device.waitIdle();
    GammaGpu::Shutdown();

    for (auto& t : aa_targets) {
        if (t.framebuffer) device.destroyFramebuffer(t.framebuffer);
        if (t.view) device.destroyImageView(t.view);
        if (t.image) vmaDestroyImage(instance.GetAllocator(), t.image, t.allocation);
    }
    if (aa_pipeline) device.destroyPipeline(aa_pipeline);
    if (aa_vertex_shader) device.destroyShaderModule(aa_vertex_shader);
    if (aa_fragment_shader) device.destroyShaderModule(aa_fragment_shader);
    device.destroyShaderModule(present_vertex_shader);
    for (u32 i = 0; i < PRESENT_PIPELINES; i++) {
        device.destroyPipeline(present_pipelines[i]);
        device.destroyShaderModule(present_shaders[i]);
    }
    for (u32 i = 0; i < lcd_pipelines.size(); i++) {
        if (lcd_pipelines[i]) device.destroyPipeline(lcd_pipelines[i]);
        if (lcd_shaders[i]) device.destroyShaderModule(lcd_shaders[i]);
    }

    for (auto& sampler : present_samplers) {
        device.destroySampler(sampler);
    }

    for (auto& info : screen_infos) {
        device.destroyImageView(info.texture.image_view);
        vmaDestroyImage(instance.GetAllocator(), info.texture.image, info.texture.allocation);
    }

    device.destroyPipeline(cursor_pipeline);
    device.destroyShaderModule(cursor_vertex_shader);
    device.destroyShaderModule(cursor_fragment_shader);
}

void RendererVulkan::PrepareRendertarget() {
    const auto& framebuffer_config = pica.regs.framebuffer_config;
    const auto& regs_lcd = pica.regs_lcd;
    for (u32 i = 0; i < 3; i++) {
        const u32 fb_id = i == 2 ? 1 : 0;
        const auto& framebuffer = framebuffer_config[fb_id];
        auto& texture = screen_infos[i].texture;

        const auto color_fill = fb_id == 0 ? regs_lcd.color_fill_top : regs_lcd.color_fill_bottom;
        if (color_fill.is_enabled) {
            screen_infos[i].image_view = texture.image_view;
            screen_infos[i].view_width = texture.width;
            screen_infos[i].view_height = texture.height;
            FillScreen(color_fill.AsVector(), texture);
            continue;
        }

        if (texture.width != framebuffer.width || texture.height != framebuffer.height ||
            texture.format != framebuffer.color_format) {
            ConfigureFramebufferTexture(texture, framebuffer);
        }

        LoadFBToScreenInfo(framebuffer, screen_infos[i], i == 1);
    }
}

void RendererVulkan::PrepareDraw(Frame* frame, const Layout::FramebufferLayout& layout) {
    const auto sampler = present_samplers[!Settings::values.filter_mode.GetValue()];
    const auto present_set = present_heap.Commit();
    for (u32 index = 0; index < screen_infos.size(); index++) {
        update_queue.AddImageSampler(present_set, 0, index, screen_infos[index].image_view,
                                     sampler);
    }
    // GammaOS LCD and sharp bilinear shaders sample through the linear sampler whatever the
    // Linear Filtering setting (the LCD ones snap to texel centres themselves).
    present_set_user = present_set;
    present_set_linear = present_set;
    if (Settings::values.gammaos_lcd_shader[0] > 0 || Settings::values.gammaos_lcd_shader[1] > 0) {
        if (sampler != present_samplers[0]) {
            present_set_linear = present_heap.Commit();
            for (u32 index = 0; index < screen_infos.size(); index++) {
                update_queue.AddImageSampler(present_set_linear, 0, index,
                                             screen_infos[index].image_view, present_samplers[0]);
            }
        }
    }

    renderpass_cache.EndRendering();
    renderpass_cache.FlushPendingClears();
    // GammaOS: the first screen is drawn at full opacity (blend constant alpha 1), so when its
    // rectangle covers the whole frame the clear is overwritten everywhere: skip it (the second
    // display's 1280x960 frame is fully covered by the bottom screen, ~5 MB of writes per frame).
    // A screen drawn first at full opacity: the first in draw order, or the second when the first
    // is disabled and the second's opacity is 1.
    const bool swap = Settings::values.swap_screen.GetValue();
    const auto covers = [&](const Common::Rectangle<u32>& r) {
        return r.left == 0 && r.top == 0 && r.right >= layout.width && r.bottom >= layout.height;
    };
    // Draw order (DrawScreens): without swap, top (opacity 1) then bottom (bottom_opacity);
    // with swap, bottom (opacity 1) then top (top_opacity).
    bool covered = false;
    if (!swap) {
        if (layout.top_screen_enabled) {
            covered = covers(layout.top_screen);
        } else if (layout.bottom_screen_enabled) {
            covered = layout.bottom_opacity >= 1.0f && covers(layout.bottom_screen);
        }
    } else {
        if (layout.bottom_screen_enabled) {
            covered = covers(layout.bottom_screen);
        } else if (layout.top_screen_enabled) {
            covered = layout.top_opacity >= 1.0f && covers(layout.top_screen);
        }
    }
    scheduler.Record([this, layout, frame, present_set,
                      renderpass = covered ? main_present_window.RenderpassNoClear()
                                           : main_present_window.Renderpass(),
                      index = current_pipeline](vk::CommandBuffer cmdbuf) {
        const vk::Viewport viewport = {
            .x = 0.0f,
            .y = 0.0f,
            .width = static_cast<float>(layout.width),
            .height = static_cast<float>(layout.height),
            .minDepth = 0.0f,
            .maxDepth = 1.0f,
        };

        const vk::Rect2D scissor = {
            .offset = {0, 0},
            .extent = {layout.width, layout.height},
        };

        cmdbuf.setViewport(0, viewport);
        cmdbuf.setScissor(0, scissor);

        const vk::ClearValue clear{.color = clear_color};
        const vk::PipelineLayout layout{*present_pipeline_layout};
        const vk::RenderPassBeginInfo renderpass_begin_info = {
            .renderPass = renderpass,
            .framebuffer = frame->framebuffer,
            .renderArea =
                vk::Rect2D{
                    .offset = {0, 0},
                    .extent = {frame->width, frame->height},
                },
            .clearValueCount = 1,
            .pClearValues = &clear,
        };

        cmdbuf.beginRenderPass(renderpass_begin_info, vk::SubpassContents::eInline);
        cmdbuf.bindPipeline(vk::PipelineBindPoint::eGraphics, present_pipelines[index]);
        cmdbuf.bindDescriptorSets(vk::PipelineBindPoint::eGraphics, layout, 0, present_set, {});
    });
}

void RendererVulkan::RenderToWindow(PresentWindow& window, const Layout::FramebufferLayout& layout,
                                    bool flipped) {
    // GammaOS fast forward: present at most about once per display period (the panel shows
    // no more anyway); the frames in between are emulated and not presented. Each window
    // keeps its own clock: the bottom screen presents right after the top in the same swap.
    if (Settings::values.gammaos_fast_forward) {
        static std::chrono::steady_clock::time_point last_present[2]{};
        auto& last = last_present[&window == &main_present_window ? 0 : 1];
        const auto now = std::chrono::steady_clock::now();
        if (now - last < std::chrono::microseconds(15500)) {
            return;
        }
        last = now;
    }
    if (hold_present) {
        return;
    }
    if (!Settings::values.use_skip_duplicate_frames.GetValue() ||
        Core::PerfStats::game_frames_updated) {
        Frame* frame = window.GetRenderFrame();

        if (layout.width != frame->width || layout.height != frame->height) {
            window.WaitPresent();
            scheduler.Finish();
            window.RecreateFrame(frame, layout.width, layout.height);
        }

        clear_color.float32[0] = Settings::values.bg_red.GetValue();
        clear_color.float32[1] = Settings::values.bg_green.GetValue();
        clear_color.float32[2] = Settings::values.bg_blue.GetValue();
        clear_color.float32[3] = 1.0f;

        GammaGpu::g.presenting = true;
        renderpass_cache.EndRendering();
    renderpass_cache.FlushPendingClears();
        const int present_q = GammaGpu::Begin(scheduler, GammaGpu::Present);
        DrawScreens(frame, layout, flipped);
        renderpass_cache.EndRendering();
    renderpass_cache.FlushPendingClears();
        GammaGpu::End(scheduler, present_q);
        GammaGpu::g.presenting = false;
        scheduler.Flush(frame->render_ready);
        window.Present(frame);
        if ((secondaryWindowEnabled && isSecondaryWindow) || (!secondaryWindowEnabled)) {
            Core::PerfStats::game_frames_updated = false;
            screenRendered = true;
        }
    }
}

void RendererVulkan::LoadFBToScreenInfo(const Pica::FramebufferConfig& framebuffer,
                                        ScreenInfo& screen_info, bool right_eye) {

    if (framebuffer.address_right1 == 0 || framebuffer.address_right2 == 0) {
        right_eye = false;
    }

    const PAddr framebuffer_addr =
        framebuffer.active_fb == 0
            ? (right_eye ? framebuffer.address_right1 : framebuffer.address_left1)
            : (right_eye ? framebuffer.address_right2 : framebuffer.address_left2);

    LOG_TRACE(Render_Vulkan, "0x{:08x} bytes from 0x{:08x}({}x{}), fmt {:x}",
              framebuffer.stride * framebuffer.height, framebuffer_addr, framebuffer.width.Value(),
              framebuffer.height.Value(), framebuffer.format);

    const u32 bpp = Pica::BytesPerPixel(framebuffer.color_format);
    const std::size_t pixel_stride = framebuffer.stride / bpp;

    ASSERT(pixel_stride * bpp == framebuffer.stride);
    ASSERT(pixel_stride % 4 == 0);

    if (!rasterizer.AccelerateDisplay(framebuffer, framebuffer_addr, static_cast<u32>(pixel_stride),
                                      screen_info)) {
        // Reset the screen info's display texture to its own permanent texture
        screen_info.image_view = screen_info.texture.image_view;
        screen_info.texcoords = {0.f, 0.f, 1.f, 1.f};

        ASSERT(false);
    }
}

void RendererVulkan::CompileShaders() {
    const vk::Device device = instance.GetDevice();
    const std::string_view preamble =
        instance.IsImageArrayDynamicIndexSupported() ? "#define ARRAY_DYNAMIC_INDEX" : "";
    present_vertex_shader =
        Compile(HostShaders::VULKAN_PRESENT_VERT, vk::ShaderStageFlagBits::eVertex, device);
    present_shaders[0] = Compile(HostShaders::VULKAN_PRESENT_FRAG,
                                 vk::ShaderStageFlagBits::eFragment, device, preamble);
    present_shaders[1] = Compile(HostShaders::VULKAN_PRESENT_ANAGLYPH_FRAG,
                                 vk::ShaderStageFlagBits::eFragment, device, preamble);
    present_shaders[2] = Compile(HostShaders::VULKAN_PRESENT_INTERLACED_FRAG,
                                 vk::ShaderStageFlagBits::eFragment, device, preamble);

    cursor_vertex_shader =
        Compile(HostShaders::VULKAN_CURSOR_VERT, vk::ShaderStageFlagBits::eVertex, device);
    cursor_fragment_shader =
        Compile(HostShaders::VULKAN_CURSOR_FRAG, vk::ShaderStageFlagBits::eFragment, device);

    for (std::size_t i = 0; i < present_samplers.size(); i++) {
        const bool linear = i == 0;
        const vk::Filter filter_mode = linear ? vk::Filter::eLinear : vk::Filter::eNearest;
        const vk::SamplerMipmapMode mipmap_mode =
            linear ? vk::SamplerMipmapMode::eLinear : vk::SamplerMipmapMode::eNearest;
        const vk::SamplerCreateInfo sampler_info = {
            .magFilter = filter_mode,
            .minFilter = filter_mode,
            .mipmapMode = mipmap_mode,
            .addressModeU = vk::SamplerAddressMode::eClampToEdge,
            .addressModeV = vk::SamplerAddressMode::eClampToEdge,
            .anisotropyEnable = VK_FALSE,
            .maxAnisotropy = 1.0f,
            .compareEnable = false,
            .compareOp = vk::CompareOp::eAlways,
            .minLod = 0.0f,
            .maxLod = 0.0f,
            .borderColor = vk::BorderColor::eIntOpaqueBlack,
            .unnormalizedCoordinates = false,
        };

        present_samplers[i] = device.createSampler(sampler_info);
    }
}

void RendererVulkan::BuildLayouts() {
    const vk::PushConstantRange push_range = {
        .stageFlags = vk::ShaderStageFlagBits::eVertex | vk::ShaderStageFlagBits::eFragment,
        .offset = 0,
        .size = sizeof(PresentUniformData),
    };

    const auto descriptor_set_layout = present_heap.Layout();
    const vk::PipelineLayoutCreateInfo layout_info = {
        .setLayoutCount = 1,
        .pSetLayouts = &descriptor_set_layout,
        .pushConstantRangeCount = 1,
        .pPushConstantRanges = &push_range,
    };
    present_pipeline_layout = instance.GetDevice().createPipelineLayoutUnique(layout_info);

    const vk::PipelineLayoutCreateInfo cursor_layout_info = {};
    cursor_pipeline_layout = instance.GetDevice().createPipelineLayoutUnique(cursor_layout_info);
}

vk::Pipeline RendererVulkan::BuildPresentPipeline(vk::ShaderModule fragment_shader) {
    const vk::VertexInputBindingDescription binding = {
        .binding = 0,
        .stride = sizeof(ScreenRectVertex),
        .inputRate = vk::VertexInputRate::eVertex,
    };

    const std::array attributes = {
        vk::VertexInputAttributeDescription{
            .location = 0,
            .binding = 0,
            .format = vk::Format::eR32G32Sfloat,
            .offset = offsetof(ScreenRectVertex, position),
        },
        vk::VertexInputAttributeDescription{
            .location = 1,
            .binding = 0,
            .format = vk::Format::eR32G32Sfloat,
            .offset = offsetof(ScreenRectVertex, tex_coord),
        },
    };

    const vk::PipelineVertexInputStateCreateInfo vertex_input_info = {
        .vertexBindingDescriptionCount = 1,
        .pVertexBindingDescriptions = &binding,
        .vertexAttributeDescriptionCount = static_cast<u32>(attributes.size()),
        .pVertexAttributeDescriptions = attributes.data(),
    };

    const vk::PipelineInputAssemblyStateCreateInfo input_assembly = {
        .topology = vk::PrimitiveTopology::eTriangleStrip,
        .primitiveRestartEnable = false,
    };

    const vk::PipelineRasterizationStateCreateInfo raster_state = {
        .depthClampEnable = false,
        .rasterizerDiscardEnable = false,
        .cullMode = vk::CullModeFlagBits::eNone,
        .frontFace = vk::FrontFace::eClockwise,
        .depthBiasEnable = false,
        .lineWidth = 1.0f,
    };

    const vk::PipelineMultisampleStateCreateInfo multisampling = {
        .rasterizationSamples = vk::SampleCountFlagBits::e1,
        .sampleShadingEnable = false,
    };

    const vk::PipelineColorBlendAttachmentState colorblend_attachment = {
        .blendEnable = true,
        .srcColorBlendFactor = vk::BlendFactor::eConstantAlpha,
        .dstColorBlendFactor = vk::BlendFactor::eOneMinusConstantAlpha,
        .colorBlendOp = vk::BlendOp::eAdd,
        .srcAlphaBlendFactor = vk::BlendFactor::eConstantAlpha,
        .dstAlphaBlendFactor = vk::BlendFactor::eOneMinusConstantAlpha,
        .alphaBlendOp = vk::BlendOp::eAdd,
        .colorWriteMask = vk::ColorComponentFlagBits::eR | vk::ColorComponentFlagBits::eG |
                          vk::ColorComponentFlagBits::eB | vk::ColorComponentFlagBits::eA,
    };

    const vk::PipelineColorBlendStateCreateInfo color_blending = {
        .logicOpEnable = false,
        .attachmentCount = 1,
        .pAttachments = &colorblend_attachment,
    };

    const vk::Viewport placeholder_viewport = vk::Viewport{0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
    const vk::Rect2D placeholder_scissor = vk::Rect2D{{0, 0}, {1, 1}};
    const vk::PipelineViewportStateCreateInfo viewport_info = {
        .viewportCount = 1,
        .pViewports = &placeholder_viewport,
        .scissorCount = 1,
        .pScissors = &placeholder_scissor,
    };

    const std::array dynamic_states = {
        vk::DynamicState::eBlendConstants,
        vk::DynamicState::eViewport,
        vk::DynamicState::eScissor,
    };

    const vk::PipelineDynamicStateCreateInfo dynamic_info = {
        .dynamicStateCount = static_cast<u32>(dynamic_states.size()),
        .pDynamicStates = dynamic_states.data(),
    };

    const vk::PipelineDepthStencilStateCreateInfo depth_info = {
        .depthTestEnable = false,
        .depthWriteEnable = false,
        .depthCompareOp = vk::CompareOp::eAlways,
        .depthBoundsTestEnable = false,
        .stencilTestEnable = false,
    };

    {
        const std::array shader_stages = {
            vk::PipelineShaderStageCreateInfo{
                .stage = vk::ShaderStageFlagBits::eVertex,
                .module = present_vertex_shader,
                .pName = "main",
            },
            vk::PipelineShaderStageCreateInfo{
                .stage = vk::ShaderStageFlagBits::eFragment,
                .module = fragment_shader,
                .pName = "main",
            },
        };

        const vk::GraphicsPipelineCreateInfo pipeline_info = {
            .stageCount = static_cast<u32>(shader_stages.size()),
            .pStages = shader_stages.data(),
            .pVertexInputState = &vertex_input_info,
            .pInputAssemblyState = &input_assembly,
            .pViewportState = &viewport_info,
            .pRasterizationState = &raster_state,
            .pMultisampleState = &multisampling,
            .pDepthStencilState = &depth_info,
            .pColorBlendState = &color_blending,
            .pDynamicState = &dynamic_info,
            .layout = *present_pipeline_layout,
            .renderPass = main_present_window.Renderpass(),
        };

        const auto [result, pipeline] =
            instance.GetDevice().createGraphicsPipeline({}, pipeline_info);
        ASSERT_MSG(result == vk::Result::eSuccess, "Unable to build present pipelines");
        return pipeline;
    }
}

bool RendererVulkan::ScreenAaWanted() const {
    if (Settings::values.resolution_factor.GetValue() != 1) {
        return false;
    }
    return Settings::values.gammaos_screen_aa ||
           Settings::values.gammaos_lcd_shader[0] == GammaLcd::kLegacySharpAa ||
           Settings::values.gammaos_lcd_shader[1] == GammaLcd::kLegacySharpAa;
}

bool RendererVulkan::EnsureAaTarget(u32 index, u32 width, u32 height) {
    AaTarget& t = aa_targets[index];
    if (t.image && t.width == width && t.height == height) {
        return true;
    }
    const vk::Device device = instance.GetDevice();
    if (t.image) {
        // The previous target may still be read by frames in flight.
        scheduler.Finish();
        device.destroyFramebuffer(t.framebuffer);
        device.destroyImageView(t.view);
        vmaDestroyImage(instance.GetAllocator(), t.image, t.allocation);
        t = AaTarget{};
    }
    const vk::Format format = instance.GetTraits(VideoCore::PixelFormat::RGBA8).native;
    const vk::ImageCreateInfo image_info = {
        .imageType = vk::ImageType::e2D,
        .format = format,
        .extent = {width, height, 1},
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = vk::SampleCountFlagBits::e1,
        .usage = vk::ImageUsageFlagBits::eSampled | vk::ImageUsageFlagBits::eColorAttachment,
    };
    const VmaAllocationCreateInfo alloc_info = {
        .flags = VMA_ALLOCATION_CREATE_WITHIN_BUDGET_BIT,
        .usage = VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
    };
    VkImage image{};
    const VkImageCreateInfo raw_info = static_cast<VkImageCreateInfo>(image_info);
    if (vmaCreateImage(instance.GetAllocator(), &raw_info, &alloc_info, &image, &t.allocation,
                       nullptr) != VK_SUCCESS) {
        LOG_ERROR(Render_Vulkan, "Screen AA: target {}x{} allocation failed", width, height);
        return false;
    }
    t.image = vk::Image{image};
    t.view = device.createImageView({
        .image = t.image,
        .viewType = vk::ImageViewType::e2D,
        .format = format,
        .subresourceRange{
            .aspectMask = vk::ImageAspectFlagBits::eColor,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    });
    const vk::RenderPass render_pass =
        renderpass_cache.GetRenderpass(VideoCore::PixelFormat::RGBA8,
                                       VideoCore::PixelFormat::Invalid, false);
    t.framebuffer = device.createFramebuffer({
        .renderPass = render_pass,
        .attachmentCount = 1,
        .pAttachments = &t.view,
        .width = width,
        .height = height,
        .layers = 1,
    });
    t.width = width;
    t.height = height;
    return true;
}

vk::Pipeline RendererVulkan::BuildAaPipeline() {
    const vk::Device device = instance.GetDevice();
    // A full-screen triangle without vertex input; the fragment shader works out the texel
    // from gl_FragCoord.
    static constexpr std::string_view kVert = R"(
#version 450 core
layout (location = 0) out vec2 frag_tex_coord;
void main() {
    const float x = float((gl_VertexIndex & 1) << 2);
    const float y = float((gl_VertexIndex & 2) << 1);
    gl_Position = vec4(x - 1.0, y - 1.0, 0.0, 1.0);
    frag_tex_coord = vec2(x, y) * 0.5;
}
)";
    static constexpr std::string_view kFrag = R"(
void main() {
    const vec2 size = vec2(ScreenSize());
    color = vec4(Fxaa((floor(gl_FragCoord.xy) + 0.5) / size), 1.0);
}
)";
    std::string preamble;
    if (instance.IsImageArrayDynamicIndexSupported()) preamble += "#define ARRAY_DYNAMIC_INDEX\n";
    preamble += "#define SCREEN_AA\n";
    aa_vertex_shader = Compile(kVert, vk::ShaderStageFlagBits::eVertex, device);
    std::string frag{GammaLcd::kCommon};
    frag += kFrag;
    aa_fragment_shader = Compile(frag, vk::ShaderStageFlagBits::eFragment, device, preamble);
    if (!aa_vertex_shader || !aa_fragment_shader) {
        return {};
    }
    const vk::PipelineVertexInputStateCreateInfo vertex_input{};
    const vk::PipelineInputAssemblyStateCreateInfo input_assembly = {
        .topology = vk::PrimitiveTopology::eTriangleList,
    };
    const vk::PipelineRasterizationStateCreateInfo raster = {
        .cullMode = vk::CullModeFlagBits::eNone,
        .frontFace = vk::FrontFace::eClockwise,
        .lineWidth = 1.0f,
    };
    const vk::PipelineMultisampleStateCreateInfo multisample = {
        .rasterizationSamples = vk::SampleCountFlagBits::e1,
    };
    const vk::PipelineColorBlendAttachmentState blend_attachment = {
        .blendEnable = false,
        .colorWriteMask = vk::ColorComponentFlagBits::eR | vk::ColorComponentFlagBits::eG |
                          vk::ColorComponentFlagBits::eB | vk::ColorComponentFlagBits::eA,
    };
    const vk::PipelineColorBlendStateCreateInfo blend = {
        .attachmentCount = 1,
        .pAttachments = &blend_attachment,
    };
    const vk::Viewport viewport{0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
    const vk::Rect2D scissor{{0, 0}, {1, 1}};
    const vk::PipelineViewportStateCreateInfo viewport_info = {
        .viewportCount = 1,
        .pViewports = &viewport,
        .scissorCount = 1,
        .pScissors = &scissor,
    };
    const std::array dynamic_states = {vk::DynamicState::eViewport, vk::DynamicState::eScissor};
    const vk::PipelineDynamicStateCreateInfo dynamic_info = {
        .dynamicStateCount = static_cast<u32>(dynamic_states.size()),
        .pDynamicStates = dynamic_states.data(),
    };
    const vk::PipelineDepthStencilStateCreateInfo depth = {
        .depthTestEnable = false,
        .depthWriteEnable = false,
        .depthCompareOp = vk::CompareOp::eAlways,
    };
    const std::array stages = {
        vk::PipelineShaderStageCreateInfo{
            .stage = vk::ShaderStageFlagBits::eVertex,
            .module = aa_vertex_shader,
            .pName = "main",
        },
        vk::PipelineShaderStageCreateInfo{
            .stage = vk::ShaderStageFlagBits::eFragment,
            .module = aa_fragment_shader,
            .pName = "main",
        },
    };
    const vk::GraphicsPipelineCreateInfo info = {
        .stageCount = static_cast<u32>(stages.size()),
        .pStages = stages.data(),
        .pVertexInputState = &vertex_input,
        .pInputAssemblyState = &input_assembly,
        .pViewportState = &viewport_info,
        .pRasterizationState = &raster,
        .pMultisampleState = &multisample,
        .pDepthStencilState = &depth,
        .pColorBlendState = &blend,
        .pDynamicState = &dynamic_info,
        .layout = *present_pipeline_layout,
        .renderPass = renderpass_cache.GetRenderpass(VideoCore::PixelFormat::RGBA8,
                                                     VideoCore::PixelFormat::Invalid, false),
    };
    const auto [result, pipeline] = device.createGraphicsPipeline({}, info);
    return result == vk::Result::eSuccess ? pipeline : vk::Pipeline{};
}

void RendererVulkan::RunScreenAa() {
    aa_prepass_done = false;
    if (aa_failed || !ScreenAaWanted()) {
        return;
    }
    if (!aa_pipeline) {
        aa_pipeline = BuildAaPipeline();
        if (!aa_pipeline) {
            LOG_ERROR(Render_Vulkan, "Screen AA: prepass pipeline failed, using the per pixel AA");
            aa_failed = true;
            return;
        }
        LOG_INFO(Render_Vulkan, "Screen AA: prepass ready");
    }
    std::array<vk::ImageView, 3> sources{};
    for (u32 i = 0; i < screen_infos.size(); i++) {
        sources[i] = screen_infos[i].image_view;
        if (!sources[i] || !screen_infos[i].view_width || !screen_infos[i].view_height ||
            !EnsureAaTarget(i, screen_infos[i].view_width, screen_infos[i].view_height)) {
            return;
        }
    }
    const auto set = present_heap.Commit();
    for (u32 i = 0; i < screen_infos.size(); i++) {
        update_queue.AddImageSampler(set, 0, i, sources[i], present_samplers[0]);
    }
    renderpass_cache.EndRendering();
    const vk::RenderPass render_pass = renderpass_cache.GetRenderpass(
        VideoCore::PixelFormat::RGBA8, VideoCore::PixelFormat::Invalid, false);
    for (u32 i = 0; i < screen_infos.size(); i++) {
        // The right eye usually shows the left eye's image: anti-alias it once.
        if (i == 1 && sources[1] == sources[0]) {
            screen_infos[1].image_view = aa_targets[0].view;
            continue;
        }
        const AaTarget& t = aa_targets[i];
        renderpass_cache.BeginRendering(RenderPass{
            .framebuffer = t.framebuffer,
            .render_pass = render_pass,
            .render_area = {.offset = {0, 0}, .extent = {t.width, t.height}},
        });
        PresentUniformData push{};
        push.screen_id_l = static_cast<int>(i);
        scheduler.Record([this, set, push, w = t.width, h = t.height](vk::CommandBuffer cmdbuf) {
            cmdbuf.bindPipeline(vk::PipelineBindPoint::eGraphics, aa_pipeline);
            cmdbuf.bindDescriptorSets(vk::PipelineBindPoint::eGraphics, *present_pipeline_layout,
                                      0, set, {});
            cmdbuf.pushConstants(*present_pipeline_layout,
                                 vk::ShaderStageFlagBits::eFragment |
                                     vk::ShaderStageFlagBits::eVertex,
                                 0, sizeof(push), &push);
            const vk::Viewport viewport{0.0f, 0.0f, static_cast<float>(w),
                                        static_cast<float>(h), 0.0f, 1.0f};
            cmdbuf.setViewport(0, viewport);
            cmdbuf.setScissor(0, vk::Rect2D{{0, 0}, {w, h}});
            cmdbuf.draw(3, 1, 0, 0);
        });
        renderpass_cache.EndRendering();
        scheduler.Record([image = t.image](vk::CommandBuffer cmdbuf) {
            const vk::ImageMemoryBarrier barrier = {
                .srcAccessMask = vk::AccessFlagBits::eColorAttachmentWrite,
                .dstAccessMask = vk::AccessFlagBits::eShaderRead,
                .oldLayout = vk::ImageLayout::eGeneral,
                .newLayout = vk::ImageLayout::eGeneral,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .image = image,
                .subresourceRange{
                    .aspectMask = vk::ImageAspectFlagBits::eColor,
                    .baseMipLevel = 0,
                    .levelCount = 1,
                    .baseArrayLayer = 0,
                    .layerCount = 1,
                },
            };
            cmdbuf.pipelineBarrier(vk::PipelineStageFlagBits::eColorAttachmentOutput,
                                   vk::PipelineStageFlagBits::eFragmentShader,
                                   vk::DependencyFlagBits::eByRegion, {}, {}, barrier);
        });
        screen_infos[i].image_view = t.view;
    }
    scheduler.MakeDirty(StateFlags::Pipeline);
    aa_prepass_done = true;
}

vk::Pipeline RendererVulkan::ScreenPipeline(u32 screen_id, bool& user_sampler) {
    user_sampler = true;
    int& setting = Settings::values.gammaos_lcd_shader[screen_id == 2 ? 1 : 0];
    int lcd = setting;
    // Screen AA works on the 3DS pixels, which only show as staircases at native 1x: above
    // that the image is already supersampled and the blend would only soften it. So it
    // applies at 1x only (set there by the user or by a Dynamic Resolution drop).
    const bool native = Settings::values.resolution_factor.GetValue() == 1;
    bool aa = Settings::values.gammaos_screen_aa && native;
    if (lcd == GammaLcd::kLegacySharpAa) {
        lcd = static_cast<int>(GammaLcd::kFirstSharp) + 1;
        aa = native;
    }
    // The prepass already anti-aliased the screen textures: the present shader stays plain.
    if (aa_prepass_done) {
        aa = false;
    }
    const int count = static_cast<int>(GammaLcd::kShaders.size());
    if (current_pipeline != 0 || lcd < 0 || lcd > count || (lcd == 0 && !aa)) {
        return present_pipelines[current_pipeline];
    }
    // Slot 0 is the plain FXAA shader (no screen shader, Screen AA on).
    const std::size_t i = static_cast<std::size_t>(lcd) + (aa ? count + 1 : 0);
    if (!lcd_pipelines[i]) {
        std::string preamble;
        if (instance.IsImageArrayDynamicIndexSupported()) preamble += "#define ARRAY_DYNAMIC_INDEX\n";
        if (aa) preamble += "#define SCREEN_AA\n";
        std::string code{GammaLcd::kCommon};
        if (lcd > 0 && static_cast<std::size_t>(lcd - 1) >= GammaLcd::kFirstSharp) {
            code += GammaLcd::kSharpCommon;
        }
        code += lcd == 0 ? GammaLcd::kPlainAa : GammaLcd::kShaders[lcd - 1];
        lcd_shaders[i] = Compile(code, vk::ShaderStageFlagBits::eFragment, instance.GetDevice(),
                                 preamble);
        if (!lcd_shaders[i]) {
            LOG_ERROR(Render_Vulkan, "screen shader {} (AA {}) failed to compile", lcd, aa);
            setting = 0;
            Settings::values.gammaos_screen_aa = false;
            return present_pipelines[current_pipeline];
        }
        lcd_pipelines[i] = BuildPresentPipeline(lcd_shaders[i]);
        LOG_INFO(Render_Vulkan, "screen shader {} (AA {}) ready", lcd, aa);
    }
    // The LCD and sharp shaders work on the linear sampler (they snap texel centres themselves);
    // the plain FXAA one keeps the Linear Filtering choice.
    user_sampler = lcd == 0;
    return lcd_pipelines[i];
}

void RendererVulkan::BuildPipelines() {
    for (u32 i = 0; i < PRESENT_PIPELINES; i++) {
        present_pipelines[i] = BuildPresentPipeline(present_shaders[i]);
    }

    // Build cursor pipeline (simple position-only, inverted color blending)
    {
        const vk::VertexInputBindingDescription cursor_binding = {
            .binding = 0,
            .stride = sizeof(float) * 2,
            .inputRate = vk::VertexInputRate::eVertex,
        };

        const vk::VertexInputAttributeDescription cursor_attribute = {
            .location = 0,
            .binding = 0,
            .format = vk::Format::eR32G32Sfloat,
            .offset = 0,
        };

        const vk::PipelineVertexInputStateCreateInfo cursor_vertex_input = {
            .vertexBindingDescriptionCount = 1,
            .pVertexBindingDescriptions = &cursor_binding,
            .vertexAttributeDescriptionCount = 1,
            .pVertexAttributeDescriptions = &cursor_attribute,
        };

        const vk::PipelineInputAssemblyStateCreateInfo cursor_input_assembly = {
            .topology = vk::PrimitiveTopology::eTriangleList,
            .primitiveRestartEnable = false,
        };

        const vk::PipelineRasterizationStateCreateInfo cursor_raster = {
            .depthClampEnable = false,
            .rasterizerDiscardEnable = false,
            .cullMode = vk::CullModeFlagBits::eNone,
            .frontFace = vk::FrontFace::eClockwise,
            .depthBiasEnable = false,
            .lineWidth = 1.0f,
        };

        const vk::PipelineMultisampleStateCreateInfo cursor_multisample = {
            .rasterizationSamples = vk::SampleCountFlagBits::e1,
            .sampleShadingEnable = false,
        };

        const vk::PipelineColorBlendAttachmentState cursor_blend_attachment = {
            .blendEnable = true,
            .srcColorBlendFactor = vk::BlendFactor::eOneMinusDstColor,
            .dstColorBlendFactor = vk::BlendFactor::eOneMinusSrcColor,
            .colorBlendOp = vk::BlendOp::eAdd,
            .srcAlphaBlendFactor = vk::BlendFactor::eOne,
            .dstAlphaBlendFactor = vk::BlendFactor::eZero,
            .alphaBlendOp = vk::BlendOp::eAdd,
            .colorWriteMask = vk::ColorComponentFlagBits::eR | vk::ColorComponentFlagBits::eG |
                              vk::ColorComponentFlagBits::eB | vk::ColorComponentFlagBits::eA,
        };

        const vk::PipelineColorBlendStateCreateInfo cursor_color_blending = {
            .logicOpEnable = false,
            .attachmentCount = 1,
            .pAttachments = &cursor_blend_attachment,
        };

        const vk::Viewport placeholder_vp = {0.0f, 0.0f, 1.0f, 1.0f, 0.0f, 1.0f};
        const vk::Rect2D placeholder_sc = {{0, 0}, {1, 1}};
        const vk::PipelineViewportStateCreateInfo cursor_viewport = {
            .viewportCount = 1,
            .pViewports = &placeholder_vp,
            .scissorCount = 1,
            .pScissors = &placeholder_sc,
        };

        const std::array cursor_dynamic_states = {
            vk::DynamicState::eViewport,
            vk::DynamicState::eScissor,
        };

        const vk::PipelineDynamicStateCreateInfo cursor_dynamic = {
            .dynamicStateCount = static_cast<u32>(cursor_dynamic_states.size()),
            .pDynamicStates = cursor_dynamic_states.data(),
        };

        const vk::PipelineDepthStencilStateCreateInfo cursor_depth = {
            .depthTestEnable = false,
            .depthWriteEnable = false,
            .depthCompareOp = vk::CompareOp::eAlways,
            .depthBoundsTestEnable = false,
            .stencilTestEnable = false,
        };

        const std::array cursor_shader_stages = {
            vk::PipelineShaderStageCreateInfo{
                .stage = vk::ShaderStageFlagBits::eVertex,
                .module = cursor_vertex_shader,
                .pName = "main",
            },
            vk::PipelineShaderStageCreateInfo{
                .stage = vk::ShaderStageFlagBits::eFragment,
                .module = cursor_fragment_shader,
                .pName = "main",
            },
        };

        const vk::GraphicsPipelineCreateInfo cursor_pipeline_info = {
            .stageCount = static_cast<u32>(cursor_shader_stages.size()),
            .pStages = cursor_shader_stages.data(),
            .pVertexInputState = &cursor_vertex_input,
            .pInputAssemblyState = &cursor_input_assembly,
            .pViewportState = &cursor_viewport,
            .pRasterizationState = &cursor_raster,
            .pMultisampleState = &cursor_multisample,
            .pDepthStencilState = &cursor_depth,
            .pColorBlendState = &cursor_color_blending,
            .pDynamicState = &cursor_dynamic,
            .layout = *cursor_pipeline_layout,
            .renderPass = main_present_window.Renderpass(),
        };

        const auto [result, pipeline] =
            instance.GetDevice().createGraphicsPipeline({}, cursor_pipeline_info);
        ASSERT_MSG(result == vk::Result::eSuccess, "Unable to build cursor pipeline");
        cursor_pipeline = pipeline;
    }
}

void RendererVulkan::ConfigureFramebufferTexture(TextureInfo& texture,
                                                 const Pica::FramebufferConfig& framebuffer) {
    vk::Device device = instance.GetDevice();
    if (texture.image_view) {
        device.destroyImageView(texture.image_view);
    }
    if (texture.image) {
        vmaDestroyImage(instance.GetAllocator(), texture.image, texture.allocation);
    }

    const VideoCore::PixelFormat pixel_format =
        VideoCore::PixelFormatFromGPUPixelFormat(framebuffer.color_format);
    const vk::Format format = instance.GetTraits(pixel_format).native;
    const vk::ImageCreateInfo image_info = {
        .imageType = vk::ImageType::e2D,
        .format = format,
        .extent = {framebuffer.width, framebuffer.height, 1},
        .mipLevels = 1,
        .arrayLayers = 1,
        .samples = vk::SampleCountFlagBits::e1,
        .usage = vk::ImageUsageFlagBits::eSampled,
    };

    const VmaAllocationCreateInfo alloc_info = {
        .flags = VMA_ALLOCATION_CREATE_WITHIN_BUDGET_BIT,
        .usage = VMA_MEMORY_USAGE_AUTO_PREFER_DEVICE,
        .requiredFlags = 0,
        .preferredFlags = 0,
        .pool = VK_NULL_HANDLE,
        .pUserData = nullptr,
    };

    VkImage unsafe_image{};
    VkImageCreateInfo unsafe_image_info = static_cast<VkImageCreateInfo>(image_info);

    VkResult result = vmaCreateImage(instance.GetAllocator(), &unsafe_image_info, &alloc_info,
                                     &unsafe_image, &texture.allocation, nullptr);
    if (result != VK_SUCCESS) [[unlikely]] {
        LOG_CRITICAL(Render_Vulkan, "Failed allocating texture with error {}", result);
        UNREACHABLE();
    }
    texture.image = vk::Image{unsafe_image};

    const vk::ImageViewCreateInfo view_info = {
        .image = texture.image,
        .viewType = vk::ImageViewType::e2D,
        .format = format,
        .subresourceRange{
            .aspectMask = vk::ImageAspectFlagBits::eColor,
            .baseMipLevel = 0,
            .levelCount = 1,
            .baseArrayLayer = 0,
            .layerCount = 1,
        },
    };
    texture.image_view = device.createImageView(view_info);

    texture.width = framebuffer.width;
    texture.height = framebuffer.height;
    texture.format = framebuffer.color_format;
}

void RendererVulkan::FillScreen(Common::Vec3<u8> color, const TextureInfo& texture) {
    // When loading some 3GX extensions, FillScreen may be called before texture image is available
    if (!texture.image) {
        return;
    }

    const vk::ClearColorValue clear_color = {
        .float32 =
            std::array{
                color.r() / 255.0f,
                color.g() / 255.0f,
                color.b() / 255.0f,
                1.0f,
            },
    };

    renderpass_cache.EndRendering();
    renderpass_cache.FlushPendingClears();
    scheduler.Record([image = texture.image, clear_color](vk::CommandBuffer cmdbuf) {
        const vk::ImageSubresourceRange range = {
            .aspectMask = vk::ImageAspectFlagBits::eColor,
            .baseMipLevel = 0,
            .levelCount = VK_REMAINING_MIP_LEVELS,
            .baseArrayLayer = 0,
            .layerCount = VK_REMAINING_ARRAY_LAYERS,
        };

        const vk::ImageMemoryBarrier pre_barrier = {
            .srcAccessMask = vk::AccessFlagBits::eShaderRead | vk::AccessFlagBits::eTransferRead,
            .dstAccessMask = vk::AccessFlagBits::eTransferWrite,
            .oldLayout = vk::ImageLayout::eGeneral,
            .newLayout = vk::ImageLayout::eTransferDstOptimal,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = image,
            .subresourceRange = range,
        };

        const vk::ImageMemoryBarrier post_barrier = {
            .srcAccessMask = vk::AccessFlagBits::eTransferWrite,
            .dstAccessMask = vk::AccessFlagBits::eShaderRead | vk::AccessFlagBits::eTransferRead,
            .oldLayout = vk::ImageLayout::eTransferDstOptimal,
            .newLayout = vk::ImageLayout::eGeneral,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = image,
            .subresourceRange = range,
        };

        cmdbuf.pipelineBarrier(vk::PipelineStageFlagBits::eFragmentShader,
                               vk::PipelineStageFlagBits::eTransfer,
                               vk::DependencyFlagBits::eByRegion, {}, {}, pre_barrier);

        cmdbuf.clearColorImage(image, vk::ImageLayout::eTransferDstOptimal, clear_color, range);

        cmdbuf.pipelineBarrier(vk::PipelineStageFlagBits::eTransfer,
                               vk::PipelineStageFlagBits::eFragmentShader,
                               vk::DependencyFlagBits::eByRegion, {}, {}, post_barrier);
    });
}

void RendererVulkan::ReloadPipeline(Settings::StereoRenderOption render_3d) {
    switch (render_3d) {
    case Settings::StereoRenderOption::Anaglyph:
        current_pipeline = 1;
        break;
    case Settings::StereoRenderOption::Interlaced:
    case Settings::StereoRenderOption::ReverseInterlaced:
        current_pipeline = 2;
        draw_info.reverse_interlaced = render_3d == Settings::StereoRenderOption::ReverseInterlaced;
        break;
    default:
        current_pipeline = 0;
        break;
    }
}

void RendererVulkan::DrawSingleScreen(u32 screen_id, float x, float y, float w, float h,
                                      Layout::DisplayOrientation orientation) {
    const ScreenInfo& screen_info = screen_infos[screen_id];
    const auto& texcoords = screen_info.texcoords;

    std::array<ScreenRectVertex, 4> vertices;
    switch (orientation) {
    case Layout::DisplayOrientation::Landscape:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x + w, y, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x, y + h, texcoords.top, texcoords.left),
            ScreenRectVertex(x + w, y + h, texcoords.top, texcoords.right),
        }};
        break;
    case Layout::DisplayOrientation::Portrait:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x + w, y, texcoords.top, texcoords.right),
            ScreenRectVertex(x, y + h, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x + w, y + h, texcoords.top, texcoords.left),
        }};
        std::swap(h, w);
        break;
    case Layout::DisplayOrientation::LandscapeFlipped:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.top, texcoords.right),
            ScreenRectVertex(x + w, y, texcoords.top, texcoords.left),
            ScreenRectVertex(x, y + h, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x + w, y + h, texcoords.bottom, texcoords.left),
        }};
        break;
    case Layout::DisplayOrientation::PortraitFlipped:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.top, texcoords.left),
            ScreenRectVertex(x + w, y, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x, y + h, texcoords.top, texcoords.right),
            ScreenRectVertex(x + w, y + h, texcoords.bottom, texcoords.right),
        }};
        std::swap(h, w);
        break;
    default:
        LOG_ERROR(Render_Vulkan, "Unknown DisplayOrientation: {}", orientation);
        break;
    }

    const u64 size = sizeof(ScreenRectVertex) * vertices.size();
    auto [data, offset, invalidate] = vertex_buffer.Map(size, 16);
    std::memcpy(data, vertices.data(), size);
    vertex_buffer.Commit(size);

    const u32 scale_factor = GetResolutionScaleFactor();
    draw_info.i_resolution =
        Common::MakeVec(static_cast<f32>(screen_info.texture.width * scale_factor),
                        static_cast<f32>(screen_info.texture.height * scale_factor),
                        1.0f / static_cast<f32>(screen_info.texture.width * scale_factor),
                        1.0f / static_cast<f32>(screen_info.texture.height * scale_factor));
    draw_info.o_resolution = Common::MakeVec(h, w, 1.0f / h, 1.0f / w);
    draw_info.screen_id_l = screen_id;
    {
        // The texture's v axis runs along the 3DS screen's width, u along its height (the
        // framebuffer is stored rotated), so this holds for every display orientation.
        const f32 sw = static_cast<f32>(std::max(screen_info.texture.width,
                                                 screen_info.texture.height));
        const f32 sh = static_cast<f32>(std::min(screen_info.texture.width,
                                                 screen_info.texture.height));
        const f32 du = texcoords.top - texcoords.bottom;
        const f32 dv = texcoords.right - texcoords.left;
        const f32 x = dv != 0.0f ? sw / dv : 0.0f;
        const f32 z = du != 0.0f ? sh / du : 0.0f;
        draw_info.lcd_map = Common::MakeVec(x, -texcoords.left * x, z, -texcoords.bottom * z);
        // GammaOS screen shaders read their per-draw constants from i_resolution (the stock
        // present shaders do not use it): the size of the image actually sampled, and how many
        // output pixels one 3DS pixel covers, as the LCD effect strength (none at 1.5 output
        // pixels per 3DS pixel or less, full from 3) and the output pixel size in 3DS pixels.
        // Worked out here once instead of with textureSize and fwidth for every output pixel.
        const f32 out_per_3ds = std::max(
            0.001f, std::min(std::max(w, h) / sw, std::min(w, h) / sh));
        const f32 strength = std::clamp((out_per_3ds - 1.5f) / 1.5f, 0.0f, 1.0f);
        const f32 view_w = static_cast<f32>(screen_info.view_width ? screen_info.view_width
                                                                   : screen_info.texture.width);
        const f32 view_h = static_cast<f32>(screen_info.view_height ? screen_info.view_height
                                                                    : screen_info.texture.height);
        draw_info.i_resolution = Common::MakeVec(view_w, view_h, strength, 1.0f / out_per_3ds);
    }
    bool user_sampler = true;
    const vk::Pipeline pipeline = ScreenPipeline(screen_id, user_sampler);
    const vk::DescriptorSet set = user_sampler ? present_set_user : present_set_linear;

    scheduler.Record([this, offset = offset, info = draw_info, pipeline,
                      set](vk::CommandBuffer cmdbuf) {
        const u32 first_vertex = static_cast<u32>(offset) / sizeof(ScreenRectVertex);
        cmdbuf.bindPipeline(vk::PipelineBindPoint::eGraphics, pipeline);
        cmdbuf.bindDescriptorSets(vk::PipelineBindPoint::eGraphics, *present_pipeline_layout, 0,
                                  set, {});
        cmdbuf.pushConstants(*present_pipeline_layout,
                             vk::ShaderStageFlagBits::eFragment | vk::ShaderStageFlagBits::eVertex,
                             0, sizeof(info), &info);

        cmdbuf.bindVertexBuffers(0, vertex_buffer.Handle(), {0});
        cmdbuf.draw(4, 1, first_vertex, 0);
    });
}

void RendererVulkan::DrawSingleScreenStereo(u32 screen_id_l, u32 screen_id_r, float x, float y,
                                            float w, float h,
                                            Layout::DisplayOrientation orientation) {
    const ScreenInfo& screen_info_l = screen_infos[screen_id_l];
    const auto& texcoords = screen_info_l.texcoords;

    std::array<ScreenRectVertex, 4> vertices;
    switch (orientation) {
    case Layout::DisplayOrientation::Landscape:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x + w, y, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x, y + h, texcoords.top, texcoords.left),
            ScreenRectVertex(x + w, y + h, texcoords.top, texcoords.right),
        }};
        break;
    case Layout::DisplayOrientation::Portrait:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x + w, y, texcoords.top, texcoords.right),
            ScreenRectVertex(x, y + h, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x + w, y + h, texcoords.top, texcoords.left),
        }};
        std::swap(h, w);
        break;
    case Layout::DisplayOrientation::LandscapeFlipped:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.top, texcoords.right),
            ScreenRectVertex(x + w, y, texcoords.top, texcoords.left),
            ScreenRectVertex(x, y + h, texcoords.bottom, texcoords.right),
            ScreenRectVertex(x + w, y + h, texcoords.bottom, texcoords.left),
        }};
        break;
    case Layout::DisplayOrientation::PortraitFlipped:
        vertices = {{
            ScreenRectVertex(x, y, texcoords.top, texcoords.left),
            ScreenRectVertex(x + w, y, texcoords.bottom, texcoords.left),
            ScreenRectVertex(x, y + h, texcoords.top, texcoords.right),
            ScreenRectVertex(x + w, y + h, texcoords.bottom, texcoords.right),
        }};
        std::swap(h, w);
        break;
    default:
        LOG_ERROR(Render_Vulkan, "Unknown DisplayOrientation: {}", orientation);
        break;
    }

    const u64 size = sizeof(ScreenRectVertex) * vertices.size();
    auto [data, offset, invalidate] = vertex_buffer.Map(size, 16);
    std::memcpy(data, vertices.data(), size);
    vertex_buffer.Commit(size);

    const u32 scale_factor = GetResolutionScaleFactor();
    draw_info.i_resolution =
        Common::MakeVec(static_cast<f32>(screen_info_l.texture.width * scale_factor),
                        static_cast<f32>(screen_info_l.texture.height * scale_factor),
                        1.0f / static_cast<f32>(screen_info_l.texture.width * scale_factor),
                        1.0f / static_cast<f32>(screen_info_l.texture.height * scale_factor));
    draw_info.o_resolution = Common::MakeVec(h, w, 1.0f / h, 1.0f / w);
    draw_info.screen_id_l = screen_id_l;
    draw_info.screen_id_r = screen_id_r;

    // A mono screen drawn before may have bound its LCD pipeline.
    scheduler.Record([this, offset = offset, info = draw_info,
                      pipeline = present_pipelines[current_pipeline],
                      set = present_set_user](vk::CommandBuffer cmdbuf) {
        const u32 first_vertex = static_cast<u32>(offset) / sizeof(ScreenRectVertex);
        cmdbuf.bindPipeline(vk::PipelineBindPoint::eGraphics, pipeline);
        cmdbuf.bindDescriptorSets(vk::PipelineBindPoint::eGraphics, *present_pipeline_layout, 0,
                                  set, {});
        cmdbuf.pushConstants(*present_pipeline_layout,
                             vk::ShaderStageFlagBits::eFragment | vk::ShaderStageFlagBits::eVertex,
                             0, sizeof(info), &info);

        cmdbuf.bindVertexBuffers(0, vertex_buffer.Handle(), {0});
        cmdbuf.draw(4, 1, first_vertex, 0);
    });
}

void RendererVulkan::ApplySecondLayerOpacity(float alpha) {
    scheduler.Record([alpha](vk::CommandBuffer cmdbuf) {
        const std::array<float, 4> blend_constants = {0.0f, 0.0f, 0.0f, alpha};
        cmdbuf.setBlendConstants(blend_constants.data());
    });
}

void RendererVulkan::DrawTopScreen(const Layout::FramebufferLayout& layout,
                                   const Common::Rectangle<u32>& top_screen) {
    if (!layout.top_screen_enabled) {
        return;
    }
    int leftside, rightside;
    leftside = Settings::values.swap_eyes_3d.GetValue() ? 1 : 0;
    rightside = Settings::values.swap_eyes_3d.GetValue() ? 0 : 1;
    const float top_screen_left = static_cast<float>(top_screen.left);
    const float top_screen_top = static_cast<float>(top_screen.top);
    const float top_screen_width = static_cast<float>(top_screen.GetWidth());
    const float top_screen_height = static_cast<float>(top_screen.GetHeight());

    const auto orientation = layout.is_rotated ? Layout::DisplayOrientation::Landscape
                                               : Layout::DisplayOrientation::Portrait;
    switch (layout.render_3d_mode) {
    case Settings::StereoRenderOption::Off: {
        const int eye = static_cast<int>(Settings::values.mono_render_option.GetValue());
        DrawSingleScreen(eye, top_screen_left, top_screen_top, top_screen_width, top_screen_height,
                         orientation);
        break;
    }
    case Settings::StereoRenderOption::SideBySide: {
        DrawSingleScreen(leftside, top_screen_left / 2, top_screen_top, top_screen_width / 2,
                         top_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(rightside, static_cast<float>((top_screen_left / 2) + (layout.width / 2)),
                         top_screen_top, top_screen_width / 2, top_screen_height, orientation);
        break;
    }
    case Settings::StereoRenderOption::SideBySideFull: {
        DrawSingleScreen(leftside, top_screen_left, top_screen_top, top_screen_width,
                         top_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(rightside, top_screen_left + layout.width / 2, top_screen_top,
                         top_screen_width, top_screen_height, orientation);
        break;
    }
    case Settings::StereoRenderOption::CardboardVR: {
        DrawSingleScreen(leftside, top_screen_left, top_screen_top, top_screen_width,
                         top_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(
            rightside,
            static_cast<float>(layout.cardboard.top_screen_right_eye + (layout.width / 2)),
            top_screen_top, top_screen_width, top_screen_height, orientation);
        break;
    }
    case Settings::StereoRenderOption::Anaglyph:
    case Settings::StereoRenderOption::Interlaced:
    case Settings::StereoRenderOption::ReverseInterlaced: {
        DrawSingleScreenStereo(leftside, rightside, top_screen_left, top_screen_top,
                               top_screen_width, top_screen_height, orientation);
        break;
    }
    }
}

void RendererVulkan::DrawBottomScreen(const Layout::FramebufferLayout& layout,
                                      const Common::Rectangle<u32>& bottom_screen) {
    if (!layout.bottom_screen_enabled) {
        return;
    }

    const float bottom_screen_left = static_cast<float>(bottom_screen.left);
    const float bottom_screen_top = static_cast<float>(bottom_screen.top);
    const float bottom_screen_width = static_cast<float>(bottom_screen.GetWidth());
    const float bottom_screen_height = static_cast<float>(bottom_screen.GetHeight());

    const auto orientation = layout.is_rotated ? Layout::DisplayOrientation::Landscape
                                               : Layout::DisplayOrientation::Portrait;

    switch (layout.render_3d_mode) {
    case Settings::StereoRenderOption::Off: {
        DrawSingleScreen(2, bottom_screen_left, bottom_screen_top, bottom_screen_width,
                         bottom_screen_height, orientation);

        break;
    }
    case Settings::StereoRenderOption::SideBySide: // Bottom screen is identical on both sides
    {
        DrawSingleScreen(2, bottom_screen_left / 2, bottom_screen_top, bottom_screen_width / 2,
                         bottom_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(2, static_cast<float>((bottom_screen_left / 2) + (layout.width / 2)),
                         bottom_screen_top, bottom_screen_width / 2, bottom_screen_height,
                         orientation);
        break;
    }
    case Settings::StereoRenderOption::SideBySideFull: {
        DrawSingleScreen(2, bottom_screen_left, bottom_screen_top, bottom_screen_width,
                         bottom_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(2, bottom_screen_left + layout.width / 2, bottom_screen_top,
                         bottom_screen_width, bottom_screen_height, orientation);
        break;
    }
    case Settings::StereoRenderOption::CardboardVR: {
        DrawSingleScreen(2, bottom_screen_left, bottom_screen_top, bottom_screen_width,
                         bottom_screen_height, orientation);
        draw_info.layer = 1;
        DrawSingleScreen(
            2, static_cast<float>(layout.cardboard.bottom_screen_right_eye + (layout.width / 2)),
            bottom_screen_top, bottom_screen_width, bottom_screen_height, orientation);
        break;
    }
    case Settings::StereoRenderOption::Anaglyph:
    case Settings::StereoRenderOption::Interlaced:
    case Settings::StereoRenderOption::ReverseInterlaced: {
        DrawSingleScreenStereo(2, 2, bottom_screen_left, bottom_screen_top, bottom_screen_width,
                               bottom_screen_height, orientation);
        break;
    }
    }
}

void RendererVulkan::DrawScreens(Frame* frame, const Layout::FramebufferLayout& layout,
                                 bool flipped) {
    if (settings.bg_color_update_requested.exchange(false)) {
        clear_color.float32[0] = Settings::values.bg_red.GetValue();
        clear_color.float32[1] = Settings::values.bg_green.GetValue();
        clear_color.float32[2] = Settings::values.bg_blue.GetValue();
    }
    if (settings.shader_update_requested.exchange(false)) {
        ReloadPipeline(layout.render_3d_mode);
    }

    PrepareDraw(frame, layout);

    const auto& top_screen = layout.top_screen;
    const auto& bottom_screen = layout.bottom_screen;
    draw_info.modelview = MakeOrthographicMatrix(layout.width, layout.height);

    draw_info.layer = 0;

    // Apply the initial default opacity value; Needed to avoid flickering
    ApplySecondLayerOpacity(1.0f);

    if (!Settings::values.swap_screen.GetValue()) {
        DrawTopScreen(layout, top_screen);
        draw_info.layer = 0;
        if (layout.bottom_opacity < 1) {
            ApplySecondLayerOpacity(layout.bottom_opacity);
        }
        DrawBottomScreen(layout, bottom_screen);
    } else {
        DrawBottomScreen(layout, bottom_screen);
        draw_info.layer = 0;
        if (layout.top_opacity < 1) {
            ApplySecondLayerOpacity(layout.top_opacity);
        }
        DrawTopScreen(layout, top_screen);
    }

    if (layout.additional_screen_enabled) {
        const auto& additional_screen = layout.additional_screen;
        if (!layout.additional_screen_is_bottom) {
            DrawTopScreen(layout, additional_screen);
        } else {
            DrawBottomScreen(layout, additional_screen);
        }
    }

    DrawCursor(layout);

    scheduler.Record([](vk::CommandBuffer cmdbuf) { cmdbuf.endRenderPass(); });
}

void RendererVulkan::DrawCursor(const Layout::FramebufferLayout& layout) {
    const auto cursor = render_window.GetCursorInfo();
    if (!cursor.visible) {
        return;
    }

    const float buf_w = static_cast<float>(layout.width);
    const float buf_h = static_cast<float>(layout.height);

    // Convert from bottom-screen-local to layout-absolute, then to NDC
    const float abs_x = layout.bottom_screen.left + cursor.projected_x;
    const float abs_y = layout.bottom_screen.top + cursor.projected_y;
    const float cx = (abs_x / buf_w) * 2.0f - 1.0f;
    const float cy = (abs_y / buf_h) * 2.0f - 1.0f;
    const float ratio = static_cast<float>(layout.bottom_screen.GetHeight()) / 30.0f;
    const float rw = ratio / buf_w;
    const float rh = ratio / buf_h;

    // Bottom screen bounds in NDC
    const float bl = (layout.bottom_screen.left / buf_w) * 2.0f - 1.0f;
    const float bt = (layout.bottom_screen.top / buf_h) * 2.0f - 1.0f;
    const float br = (layout.bottom_screen.right / buf_w) * 2.0f - 1.0f;
    const float bb = (layout.bottom_screen.bottom / buf_h) * 2.0f - 1.0f;

    // Crosshair geometry clamped to bottom screen bounds
    const float vl = std::fmax(cx - rw / 5.0f, bl);
    const float vr = std::fmin(cx + rw / 5.0f, br);
    const float vt = std::fmax(cy - rh, bt);
    const float vb = std::fmin(cy + rh, bb);

    const float hl = std::fmax(cx - rw, bl);
    const float hr = std::fmin(cx + rw, br);
    const float ht = std::fmax(cy - rh / 5.0f, bt);
    const float hb = std::fmin(cy + rh / 5.0f, bb);

    // 12 vertices = 4 triangles (2 for vertical bar, 2 for horizontal bar)
    // clang-format off
    const float vertices[] = {
        // Vertical bar
        vl, vt,  vr, vt,  vr, vb,
        vl, vt,  vr, vb,  vl, vb,
        // Horizontal bar
        hl, ht,  hr, ht,  hr, hb,
        hl, ht,  hr, hb,  hl, hb,
    };
    // clang-format on

    const u64 size = sizeof(vertices);
    auto [data, offset, invalidate] = vertex_buffer.Map(size, 16);
    std::memcpy(data, vertices, size);
    vertex_buffer.Commit(size);

    scheduler.Record([this, offset = offset, pipeline = cursor_pipeline](vk::CommandBuffer cmdbuf) {
        cmdbuf.bindPipeline(vk::PipelineBindPoint::eGraphics, pipeline);
        cmdbuf.bindVertexBuffers(0, vertex_buffer.Handle(), {0});
        const u32 first_vertex = static_cast<u32>(offset) / (sizeof(float) * 2);
        cmdbuf.draw(12, 1, first_vertex, 0);
    });
}

#ifdef ANDROID
namespace {
// GammaOS nano swap tracer: every 120 emulated frames log the wall-clock cadence of
// SwapBuffers and where the time between two swaps went (primary present, secondary
// present, limiter sleep in EndFrame). Cheap: a few clock reads per frame.
struct SwapTrace {
    std::chrono::steady_clock::time_point last{};
    double last_cpu_ms = 0;   // CLOCK_THREAD_CPUTIME_ID at the last swap
    // CPU time consumed by this thread between swaps, split by wall interval
    // (short < 14 ms, long > 20 ms): tells CPU-bound frames from waiting.
    double cpu_short = 0, cpu_long = 0;
    int n_short = 0, n_long = 0;
    int n = 0;
    double sum_int = 0, max_int = 0, sum_p = 0, max_p = 0, sum_s = 0, max_s = 0, sum_e = 0,
           max_e = 0, sum_pre = 0, max_pre = 0, sum_fin = 0, max_fin = 0, sum_tick = 0,
           max_tick = 0;
    int hist[5] = {};
    int skipped = 0;
};
SwapTrace g_swap_trace;
double ThreadCpuMs() {
    timespec ts{};
    clock_gettime(CLOCK_THREAD_CPUTIME_ID, &ts);
    return ts.tv_sec * 1000.0 + ts.tv_nsec / 1e6;
}
double MsSince(std::chrono::steady_clock::time_point t) {
    return std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t)
        .count();
}
} // namespace
#endif

void RendererVulkan::SwapBuffers() {
    if (Settings::values.deadline_frame_pacing) {
        system.frame_limiter.PaceBeforePresent(system.CoreTiming().GetGlobalTimeUs());
    }
#ifdef ANDROID
    const auto trace_t0 = std::chrono::steady_clock::now();
    const double trace_cpu = ThreadCpuMs();
    if (g_swap_trace.last.time_since_epoch().count() != 0) {
        const double dt =
            std::chrono::duration<double, std::milli>(trace_t0 - g_swap_trace.last).count();
        g_swap_trace.sum_int += dt;
        if (dt > g_swap_trace.max_int) g_swap_trace.max_int = dt;
        g_swap_trace.hist[dt < 10 ? 0 : dt < 14 ? 1 : dt < 20 ? 2 : dt < 28 ? 3 : 4]++;
        const double cpu = trace_cpu - g_swap_trace.last_cpu_ms;
        if (dt < 14) { g_swap_trace.cpu_short += cpu; g_swap_trace.n_short++; }
        else if (dt > 20) { g_swap_trace.cpu_long += cpu; g_swap_trace.n_long++; }
        if (dt > 40) {
            // GammaOS: a long frame. CPU time close to the wall time means the emulation
            // thread was busy; much less means it waited (GPU, presenter, a lock).
            LOG_WARNING(Render_Vulkan, "long frame {:.1f} ms, emulation thread cpu {:.1f} ms", dt,
                        cpu);
        }
    }
    g_swap_trace.last = trace_t0;
    g_swap_trace.last_cpu_ms = trace_cpu;
#endif
    if (!GammaGpu::g.enabled) {
        const auto props = instance.GetPhysicalDevice().getProperties();
        GammaGpu::Init(instance.GetDevice(), props.limits.timestampPeriod,
                       props.limits.timestampComputeAndGraphics);
    }
    GammaGpu::FrameBoundary();
    renderpass_cache.EndRendering();
    GammaDump::FrameBoundary(scheduler, instance.GetDevice(), instance.IsOcclusionPrecise());
    system.perf_stats->StartSwap();
    screenRendered = false;
#ifndef ANDROID
    if (Settings::values.layout_option.GetValue() == Settings::LayoutOption::SeparateWindows) {
        ASSERT(secondary_window);
        secondaryWindowEnabled = true;
    } else {
        secondaryWindowEnabled = false;
    }
#endif

#ifdef ANDROID
    if (secondary_window) {
        secondaryWindowEnabled = true;
    } else {
        secondaryWindowEnabled = false;
    }
#endif

    // GammaOS: a frame that left draws out while their pipelines compile is missing whole
    // layers (the background, the stage, an effect): showing it flickers. Keep the previous
    // complete frame on the panels instead, for at most a few frames in a row so a long
    // compile burst cannot freeze the picture. persist.gammaos.azahar_nano.hold_incomplete =
    // the most frames held in a row (default 6, about 100 ms; 0 shows incomplete frames).
    {
        static const u32 max_hold = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.hold_incomplete", v);
            return v[0] ? static_cast<u32>(atoi(v)) : 6u;
        }();
        const u32 skipped = rasterizer.TakeSkippedDraws();
        hold_present = skipped > 0 && held_incomplete < max_hold &&
                       !Settings::values.gammaos_fast_forward;
        held_incomplete = hold_present ? held_incomplete + 1 : 0;
        if (hold_present) GammaStats::Add(GammaStats::HeldIncompleteFrames);
    }
    const Layout::FramebufferLayout& layout = render_window.GetFramebufferLayout();
#ifdef ANDROID
    const auto trace_tpre = std::chrono::steady_clock::now();
#endif
    PrepareRendertarget();
    RunScreenAa();
    RenderScreenshot();
    isSecondaryWindow = false;
#ifdef ANDROID
    {
        const double d = MsSince(trace_tpre);
        g_swap_trace.sum_pre += d;
        if (d > g_swap_trace.max_pre) g_swap_trace.max_pre = d;
    }
    const auto trace_t1 = std::chrono::steady_clock::now();
#endif
    RenderToWindow(main_present_window, layout, false);
#ifdef ANDROID
    {
        const double d = MsSince(trace_t1);
        g_swap_trace.sum_p += d;
        if (d > g_swap_trace.max_p) g_swap_trace.max_p = d;
    }
    const auto trace_t2 = std::chrono::steady_clock::now();
#endif
#ifndef ANDROID
    if (Settings::values.layout_option.GetValue() == Settings::LayoutOption::SeparateWindows) {
        ASSERT(secondary_window);
        const auto& secondary_layout = secondary_window->GetFramebufferLayout();
        if (!secondary_present_window_ptr) {
            secondary_present_window_ptr = std::make_unique<PresentWindow>(
                *secondary_window, instance, scheduler, IsLowRefreshRate());
        }
        isSecondaryWindow = true;
        RenderToWindow(*secondary_present_window_ptr, secondary_layout, false);
        secondary_window->PollEvents();
    }
#endif

#ifdef ANDROID
    if (secondary_window) {
        const auto& secondary_layout = secondary_window->GetFramebufferLayout();
        if (!secondary_present_window_ptr) {
            secondary_present_window_ptr = std::make_unique<PresentWindow>(
                *secondary_window, instance, scheduler, IsLowRefreshRate());
        }
        isSecondaryWindow = true;
        RenderToWindow(*secondary_present_window_ptr, secondary_layout, false);
        secondary_window->PollEvents();
    }
#endif
#ifdef ANDROID
    {
        const double d = MsSince(trace_t2);
        g_swap_trace.sum_s += d;
        if (d > g_swap_trace.max_s) g_swap_trace.max_s = d;
    }
    const auto trace_tfin = std::chrono::steady_clock::now();
    if (!screenRendered) g_swap_trace.skipped++;
#endif
    if (!screenRendered) {
        // GammaOS: submit without waiting for the GPU. Waiting here (Finish) stalled the
        // emulation for the whole GPU frame on every repeated frame of a 30 fps game, so the
        // CPU and GPU never overlapped; resources still in use are protected by their fences.
        // persist.gammaos.azahar_nano.skip_finish=1 restores the wait.
        static const bool wait = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.skip_finish", v);
            return v[0] == '1';
        }();
        if (wait) {
            scheduler.Finish();
        } else {
            scheduler.Flush();
        }
    }
#ifdef ANDROID
    {
        const double d = MsSince(trace_tfin);
        g_swap_trace.sum_fin += d;
        if (d > g_swap_trace.max_fin) g_swap_trace.max_fin = d;
    }
#endif

    system.perf_stats->EndSwap();
#ifdef ANDROID
    const auto trace_ttick = std::chrono::steady_clock::now();
#endif
    rasterizer.TickFrame();
#ifdef ANDROID
    {
        const double d = MsSince(trace_ttick);
        g_swap_trace.sum_tick += d;
        if (d > g_swap_trace.max_tick) g_swap_trace.max_tick = d;
    }
    const auto trace_t3 = std::chrono::steady_clock::now();
#endif
    EndFrame();
#ifdef ANDROID
    {
        const double d = MsSince(trace_t3);
        g_swap_trace.sum_e += d;
        if (d > g_swap_trace.max_e) g_swap_trace.max_e = d;
    }
    if (++g_swap_trace.n >= 120) {
        auto& t = g_swap_trace;
        LOG_INFO(Render_Vulkan,
                 "swap trace: interval avg {:.2f} max {:.2f} ms hist <10:{} 10-14:{} 14-20:{} "
                 "20-28:{} >28:{} | primary avg {:.2f} max {:.2f} | secondary avg {:.2f} max "
                 "{:.2f} | endframe avg {:.2f} max {:.2f} | prepare avg {:.2f} max {:.2f} | "
                 "finish avg {:.2f} max {:.2f} skipped {} | tick avg {:.2f} max {:.2f} | cpu "
                 "per short frame {:.2f} ms ({}) per long frame {:.2f} ms ({})",
                 t.sum_int / t.n, t.max_int, t.hist[0], t.hist[1], t.hist[2], t.hist[3],
                 t.hist[4], t.sum_p / t.n, t.max_p, t.sum_s / t.n, t.max_s, t.sum_e / t.n,
                 t.max_e, t.sum_pre / t.n, t.max_pre, t.sum_fin / t.n, t.max_fin, t.skipped,
                 t.sum_tick / t.n, t.max_tick, t.n_short ? t.cpu_short / t.n_short : 0.0,
                 t.n_short, t.n_long ? t.cpu_long / t.n_long : 0.0, t.n_long);
        {
            namespace GS = GammaStats;
            const double n = t.n;
            LOG_INFO(Render_Vulkan,
                     "gpu work per frame: draws {:.1f} passes {:.1f} | uploads {:.1f} ({:.0f} px, "
                     "scaled {:.0f} px) downloads {:.2f} ({:.0f} px) | copies {:.1f} ({:.0f} px) "
                     "blits {:.1f} ({:.0f} px) clears {:.1f} ({:.0f} px) mips {:.2f} reinterp {:.2f} | "
                     "pass breaks with only the render area changed {:.1f} | draws z {:.1f} w {:.1f} "
                     "shadow {:.1f} alphatest {:.1f} blend {:.1f} | cmd lists {:.1f} top {:.1f} "
                     "blocked {:.2f} transfers {:.1f} blocked {:.2f} | enabled {} | pipelines fast {} "
                     "queued {} draws skipped {} shaders compiled {} images created {} surfaces recycled {} pipeline builds {} build ms {} new-shader builds {} ms {} same-shader builds {} ms {} | sw draws {} ({} verts) fallback verts {} | hybrid fallback draws {} skipped {} held frames {} uber draws {} uber compiles {} | builds hw {} {} ms sw {} {} ms uber {} {} ms | cached-only attempts {} {} ms max {} ms texture binds reused {:.1f} vertex kb reused {:.0f} (window totals)",
                     GS::Take(GS::Draws) / n, GS::Take(GS::RenderPasses) / n,
                     GS::Take(GS::Uploads) / n, GS::Take(GS::UploadPixels) / n,
                     GS::Take(GS::UploadScaledPixels) / n, GS::Take(GS::Downloads) / n,
                     GS::Take(GS::DownloadPixels) / n, GS::Take(GS::Copies) / n,
                     GS::Take(GS::CopyPixels) / n, GS::Take(GS::Blits) / n,
                     GS::Take(GS::BlitPixels) / n, GS::Take(GS::Clears) / n,
                     GS::Take(GS::ClearPixels) / n, GS::Take(GS::Mipmaps) / n,
                     GS::Take(GS::Reinterprets) / n, GS::Take(GS::PassBreaksAreaOnly) / n,
                     GS::Take(GS::DrawsZBuffer) / n, GS::Take(GS::DrawsWBuffer) / n,
                     GS::Take(GS::DrawsShadow) / n, GS::Take(GS::DrawsAlphaTest) / n,
                     GS::Take(GS::DrawsBlend) / n, GS::Take(GS::CmdLists) / n,
                     GS::Take(GS::CmdListsTop) / n, GS::Take(GS::CmdListsBlocked) / n,
                     GS::Take(GS::Transfers) / n, GS::Take(GS::TransfersBlocked) / n,
                     Settings::values.disable_right_eye_render.GetValue(), GS::Take(GS::PipesFast),
                     GS::Take(GS::PipesQueued), GS::Take(GS::DrawsSkippedPending),
                     GS::Take(GS::ShadersCompiled), GS::Take(GS::ImagesCreated),
                     GS::Take(GS::SurfacesRecycled), GS::Take(GS::PipeBuilds),
                     GS::Take(GS::PipeBuildUs) / 1000, GS::Take(GS::PipeFirstBuilds),
                     GS::Take(GS::PipeFirstUs) / 1000, GS::Take(GS::PipeRepeatBuilds),
                     GS::Take(GS::PipeRepeatUs) / 1000, GS::Take(GS::NaturalSwDraws), GS::Take(GS::NaturalSwVertices),
                     GS::Take(GS::FallbackSwVertices), GS::Take(GS::HybridFallbackDraws),
                     GS::Take(GS::HybridSkippedDraws), GS::Take(GS::HeldIncompleteFrames),
                     GS::Take(GS::HybridUberDraws), GS::Take(GS::HybridUberCompiles),
                     GS::Take(GS::PipeHwBuilds), GS::Take(GS::PipeHwUs) / 1000,
                     GS::Take(GS::PipeSwBuilds), GS::Take(GS::PipeSwUs) / 1000,
                     GS::Take(GS::PipeUberBuilds), GS::Take(GS::PipeUberUs) / 1000,
                     GS::Take(GS::FastAttempts), GS::Take(GS::FastAttemptUs) / 1000,
                     GS::Take(GS::FastAttemptMaxUs) / 1000, GS::Take(GS::TextureBindsReused) / n, GS::Take(GS::VertexBytesReused) / n / 1024.0);
        }
        {
            namespace GS = GammaStats;
            u32 twin_copies = 0;
            const u32 twin_pairs = rasterizer.TakeTwinStats(twin_copies);
            if (twin_pairs || twin_copies) {
                LOG_INFO(Render_Vulkan, "stereo twins: {} pairs drawn once, {} mirror copies",
                         twin_pairs, twin_copies);
            }
            const u64 vs = GS::Take(GS::GlslVs), fs = GS::Take(GS::GlslFs);
            if (vs || fs) {
                LOG_INFO(Render_Vulkan, "glsl->spirv: vs {} in {} ms, fs {} in {} ms, other {} ms",
                         vs, GS::Take(GS::GlslVsUs) / 1000, fs, GS::Take(GS::GlslFsUs) / 1000,
                         GS::Take(GS::GlslOtherUs) / 1000);
            }
            const u64 b = GS::Take(GS::NewBothSeen), v = GS::Take(GS::NewVsSeen),
                      f = GS::Take(GS::NewFsSeen), n = GS::Take(GS::NewNoneSeen);
            if (b || v || f || n) {
                LOG_INFO(Render_Vulkan,
                         "new shader pairs: both stages seen {} {} ms, vs seen {} {} ms, fs seen "
                         "{} {} ms, none seen {} {} ms",
                         b, GS::Take(GS::NewBothSeenUs) / 1000, v, GS::Take(GS::NewVsSeenUs) / 1000,
                         f, GS::Take(GS::NewFsSeenUs) / 1000, n, GS::Take(GS::NewNoneSeenUs) / 1000);
            }
        }
        {
            auto& gg = GammaGpu::g;
            if (gg.frames_read > 0) {
                const double fr = gg.frames_read;
                LOG_INFO(Render_Vulkan,
                         "gpu time per frame ({} frames): span {:.2f} ms | passes {:.2f} present "
                         "{:.2f} clears {:.2f} blits {:.2f} copies {:.2f} uploads {:.2f} ms | worst "
                         "frame passes {:.1f} ms, worst pass {:.1f} ms",
                         gg.frames_read, gg.span_ms / fr, gg.sum_ms[GammaGpu::Pass] / fr,
                         gg.sum_ms[GammaGpu::Present] / fr, gg.sum_ms[GammaGpu::Clear] / fr,
                         gg.sum_ms[GammaGpu::Blit] / fr, gg.sum_ms[GammaGpu::Copy] / fr,
                         gg.sum_ms[GammaGpu::Upload] / fr, gg.max_frame_pass_ms, gg.max_pass_ms);
            }
            gg.max_frame_pass_ms = 0.0;
            gg.max_pass_ms = 0.0;
            gg.sum_ms = {};
            gg.span_ms = 0.0;
            gg.frames_read = 0;
        }
        t = SwapTrace{};
        t.last = std::chrono::steady_clock::now();
        t.last_cpu_ms = ThreadCpuMs();
    }
#endif
}

void RendererVulkan::RenderScreenshot() {
    if (!settings.screenshot_requested.exchange(false)) {
        return;
    }

    if (!TryRenderScreenshotWithHostMemory()) {
        RenderScreenshotWithStagingCopy();
    }

    settings.screenshot_complete_callback(false);
}

void RendererVulkan::RenderScreenshotWithStagingCopy() {
    const vk::Device device = instance.GetDevice();

    const Layout::FramebufferLayout layout{settings.screenshot_framebuffer_layout};
    const u32 width = layout.width;
    const u32 height = layout.height;

    const vk::BufferCreateInfo staging_buffer_info = {
        .size = width * height * 4,
        .usage = vk::BufferUsageFlagBits::eTransferDst,
    };

    const VmaAllocationCreateInfo alloc_create_info = {
        .flags = VMA_ALLOCATION_CREATE_WITHIN_BUDGET_BIT | VMA_ALLOCATION_CREATE_MAPPED_BIT |
                 VMA_ALLOCATION_CREATE_HOST_ACCESS_RANDOM_BIT,
        .usage = VMA_MEMORY_USAGE_AUTO_PREFER_HOST,
        .requiredFlags = 0,
        .preferredFlags = 0,
        .pool = VK_NULL_HANDLE,
        .pUserData = nullptr,
    };

    VkBuffer unsafe_buffer{};
    VmaAllocation allocation{};
    VmaAllocationInfo alloc_info;
    VkBufferCreateInfo unsafe_buffer_info = static_cast<VkBufferCreateInfo>(staging_buffer_info);

    VkResult result = vmaCreateBuffer(instance.GetAllocator(), &unsafe_buffer_info,
                                      &alloc_create_info, &unsafe_buffer, &allocation, &alloc_info);
    if (result != VK_SUCCESS) [[unlikely]] {
        LOG_CRITICAL(Render_Vulkan, "Failed allocating texture with error {}", result);
        UNREACHABLE();
    }

    vk::Buffer staging_buffer{unsafe_buffer};

    Frame frame{};
    main_present_window.RecreateFrame(&frame, width, height);

    DrawScreens(&frame, layout, false);

    scheduler.Record(
        [width, height, source_image = frame.image, staging_buffer](vk::CommandBuffer cmdbuf) {
            const vk::ImageMemoryBarrier read_barrier = {
                .srcAccessMask = vk::AccessFlagBits::eMemoryWrite,
                .dstAccessMask = vk::AccessFlagBits::eTransferRead,
                .oldLayout = vk::ImageLayout::eTransferSrcOptimal,
                .newLayout = vk::ImageLayout::eTransferSrcOptimal,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .image = source_image,
                .subresourceRange{
                    .aspectMask = vk::ImageAspectFlagBits::eColor,
                    .baseMipLevel = 0,
                    .levelCount = VK_REMAINING_MIP_LEVELS,
                    .baseArrayLayer = 0,
                    .layerCount = VK_REMAINING_ARRAY_LAYERS,
                },
            };
            const vk::ImageMemoryBarrier write_barrier = {
                .srcAccessMask = vk::AccessFlagBits::eTransferRead,
                .dstAccessMask = vk::AccessFlagBits::eMemoryWrite,
                .oldLayout = vk::ImageLayout::eTransferSrcOptimal,
                .newLayout = vk::ImageLayout::eTransferSrcOptimal,
                .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
                .image = source_image,
                .subresourceRange{
                    .aspectMask = vk::ImageAspectFlagBits::eColor,
                    .baseMipLevel = 0,
                    .levelCount = VK_REMAINING_MIP_LEVELS,
                    .baseArrayLayer = 0,
                    .layerCount = VK_REMAINING_ARRAY_LAYERS,
                },
            };
            static constexpr vk::MemoryBarrier memory_write_barrier = {
                .srcAccessMask = vk::AccessFlagBits::eMemoryWrite,
                .dstAccessMask = vk::AccessFlagBits::eMemoryRead | vk::AccessFlagBits::eMemoryWrite,
            };

            const vk::BufferImageCopy image_copy = {
                .bufferOffset = 0,
                .bufferRowLength = 0,
                .bufferImageHeight = 0,
                .imageSubresource =
                    {
                        .aspectMask = vk::ImageAspectFlagBits::eColor,
                        .mipLevel = 0,
                        .baseArrayLayer = 0,
                        .layerCount = 1,
                    },
                .imageOffset = {0, 0, 0},
                .imageExtent = {width, height, 1},
            };

            cmdbuf.pipelineBarrier(vk::PipelineStageFlagBits::eAllCommands,
                                   vk::PipelineStageFlagBits::eTransfer,
                                   vk::DependencyFlagBits::eByRegion, {}, {}, read_barrier);
            cmdbuf.copyImageToBuffer(source_image, vk::ImageLayout::eTransferSrcOptimal,
                                     staging_buffer, image_copy);
            cmdbuf.pipelineBarrier(
                vk::PipelineStageFlagBits::eTransfer, vk::PipelineStageFlagBits::eAllCommands,
                vk::DependencyFlagBits::eByRegion, memory_write_barrier, {}, write_barrier);
        });

    // Ensure the copy is fully completed before saving the screenshot
    scheduler.Finish();

    // Copy backing image data to the QImage screenshot buffer
    std::memcpy(settings.screenshot_bits, alloc_info.pMappedData, staging_buffer_info.size);

    // QImage::Format_RGB32 expects BGRA byte order. If the swapchain format is RGBA,
    // swap R and B channels so the screenshot colors are correct.
    if (main_present_window.GetSurfaceFormat() == vk::Format::eR8G8B8A8Unorm) {
        u8* pixels = static_cast<u8*>(settings.screenshot_bits);
        for (u32 i = 0; i < width * height; i++) {
            std::swap(pixels[i * 4 + 0], pixels[i * 4 + 2]);
        }
    }

    // Destroy allocated resources
    vmaDestroyBuffer(instance.GetAllocator(), staging_buffer, allocation);
    vmaDestroyImage(instance.GetAllocator(), frame.image, frame.allocation);
    device.destroyFramebuffer(frame.framebuffer);
    device.destroyImageView(frame.image_view);
}

bool RendererVulkan::TryRenderScreenshotWithHostMemory() {
    // If the host-memory import alignment matches the allocation granularity of the platform, then
    // the entire span of memory can be trivially imported
    const bool trivial_import =
        instance.IsExternalMemoryHostSupported() &&
        instance.GetMinImportedHostPointerAlignment() == Common::GetPageSize();
    if (!trivial_import) {
        return false;
    }

    const vk::Device device = instance.GetDevice();

    const Layout::FramebufferLayout layout{settings.screenshot_framebuffer_layout};
    const u32 width = layout.width;
    const u32 height = layout.height;

    // For a span of memory [x, x + s], import [AlignDown(x, alignment), AlignUp(x + s, alignment)]
    // and maintain an offset to the start of the data
    const u64 import_alignment = instance.GetMinImportedHostPointerAlignment();
    const uintptr_t address = reinterpret_cast<uintptr_t>(settings.screenshot_bits);
    void* aligned_pointer = reinterpret_cast<void*>(Common::AlignDown(address, import_alignment));
    const u64 offset = address % import_alignment;
    const u64 aligned_size = Common::AlignUp(offset + width * height * 4ull, import_alignment);

    // Buffer<->Image mapping for the imported imported buffer
    const vk::BufferImageCopy buffer_image_copy = {
        .bufferOffset = offset,
        .bufferRowLength = 0,
        .bufferImageHeight = 0,
        .imageSubresource =
            {
                .aspectMask = vk::ImageAspectFlagBits::eColor,
                .mipLevel = 0,
                .baseArrayLayer = 0,
                .layerCount = 1,
            },
        .imageOffset = {0, 0, 0},
        .imageExtent = {width, height, 1},
    };

    const vk::MemoryHostPointerPropertiesEXT import_properties =
        device.getMemoryHostPointerPropertiesEXT(
            vk::ExternalMemoryHandleTypeFlagBits::eHostAllocationEXT, aligned_pointer);

    if (!import_properties.memoryTypeBits) {
        // Could not import memory
        return false;
    }

    const std::optional<u32> memory_type_index = FindMemoryType(
        instance.GetPhysicalDevice().getMemoryProperties(),
        vk::MemoryPropertyFlagBits::eHostVisible | vk::MemoryPropertyFlagBits::eHostCoherent,
        import_properties.memoryTypeBits);

    if (!memory_type_index.has_value()) {
        // Could not find memory type index
        return false;
    }

    const vk::StructureChain<vk::MemoryAllocateInfo, vk::ImportMemoryHostPointerInfoEXT>
        allocation_chain = {
            vk::MemoryAllocateInfo{
                .allocationSize = aligned_size,
                .memoryTypeIndex = memory_type_index.value(),
            },
            vk::ImportMemoryHostPointerInfoEXT{
                .handleType = vk::ExternalMemoryHandleTypeFlagBits::eHostAllocationEXT,
                .pHostPointer = aligned_pointer,
            },
        };

    // Import host memory
    const vk::UniqueDeviceMemory imported_memory =
        device.allocateMemoryUnique(allocation_chain.get());

    const vk::StructureChain<vk::BufferCreateInfo, vk::ExternalMemoryBufferCreateInfo> buffer_info =
        {
            vk::BufferCreateInfo{
                .size = aligned_size,
                .usage = vk::BufferUsageFlagBits::eTransferDst,
                .sharingMode = vk::SharingMode::eExclusive,
            },
            vk::ExternalMemoryBufferCreateInfo{
                .handleTypes = vk::ExternalMemoryHandleTypeFlagBits::eHostAllocationEXT,
            },
        };

    // Bind imported memory to buffer
    const vk::UniqueBuffer imported_buffer = device.createBufferUnique(buffer_info.get());
    device.bindBufferMemory(imported_buffer.get(), imported_memory.get(), 0);

    Frame frame{};
    main_present_window.RecreateFrame(&frame, width, height);

    DrawScreens(&frame, layout, false);

    scheduler.Record([buffer_image_copy, source_image = frame.image,
                      imported_buffer = imported_buffer.get()](vk::CommandBuffer cmdbuf) {
        const vk::ImageMemoryBarrier read_barrier = {
            .srcAccessMask = vk::AccessFlagBits::eMemoryWrite,
            .dstAccessMask = vk::AccessFlagBits::eTransferRead,
            .oldLayout = vk::ImageLayout::eTransferSrcOptimal,
            .newLayout = vk::ImageLayout::eTransferSrcOptimal,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = source_image,
            .subresourceRange{
                .aspectMask = vk::ImageAspectFlagBits::eColor,
                .baseMipLevel = 0,
                .levelCount = VK_REMAINING_MIP_LEVELS,
                .baseArrayLayer = 0,
                .layerCount = VK_REMAINING_ARRAY_LAYERS,
            },
        };
        const vk::ImageMemoryBarrier write_barrier = {
            .srcAccessMask = vk::AccessFlagBits::eTransferRead,
            .dstAccessMask = vk::AccessFlagBits::eMemoryWrite,
            .oldLayout = vk::ImageLayout::eTransferSrcOptimal,
            .newLayout = vk::ImageLayout::eTransferSrcOptimal,
            .srcQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .dstQueueFamilyIndex = VK_QUEUE_FAMILY_IGNORED,
            .image = source_image,
            .subresourceRange{
                .aspectMask = vk::ImageAspectFlagBits::eColor,
                .baseMipLevel = 0,
                .levelCount = VK_REMAINING_MIP_LEVELS,
                .baseArrayLayer = 0,
                .layerCount = VK_REMAINING_ARRAY_LAYERS,
            },
        };
        static constexpr vk::MemoryBarrier memory_write_barrier = {
            .srcAccessMask = vk::AccessFlagBits::eMemoryWrite,
            .dstAccessMask = vk::AccessFlagBits::eMemoryRead | vk::AccessFlagBits::eMemoryWrite,
        };

        cmdbuf.pipelineBarrier(vk::PipelineStageFlagBits::eAllCommands,
                               vk::PipelineStageFlagBits::eTransfer,
                               vk::DependencyFlagBits::eByRegion, {}, {}, read_barrier);
        cmdbuf.copyImageToBuffer(source_image, vk::ImageLayout::eTransferSrcOptimal,
                                 imported_buffer, buffer_image_copy);
        cmdbuf.pipelineBarrier(
            vk::PipelineStageFlagBits::eTransfer, vk::PipelineStageFlagBits::eAllCommands,
            vk::DependencyFlagBits::eByRegion, memory_write_barrier, {}, write_barrier);
    });

    // Ensure the copy is fully completed before saving the screenshot
    scheduler.Finish();

    // QImage::Format_RGB32 expects BGRA byte order. If the swapchain format is RGBA,
    // swap R and B channels so the screenshot colors are correct.
    if (main_present_window.GetSurfaceFormat() == vk::Format::eR8G8B8A8Unorm) {
        u8* pixels = static_cast<u8*>(settings.screenshot_bits);
        for (u32 i = 0; i < width * height; i++) {
            std::swap(pixels[i * 4 + 0], pixels[i * 4 + 2]);
        }
    }

    // Image data has been copied directly to host memory
    device.destroyFramebuffer(frame.framebuffer);
    device.destroyImageView(frame.image_view);

    return true;
}

void RendererVulkan::NotifySurfaceResized(bool is_second_window) {
    if (is_second_window) {
        if (secondary_present_window_ptr) {
            secondary_present_window_ptr->NotifySurfaceResized();
        }
    } else {
        main_present_window.NotifySurfaceResized();
    }
}

void RendererVulkan::NotifySurfaceChanged(bool is_second_window) {
    if (is_second_window) {
        if (secondary_present_window_ptr) {
            secondary_present_window_ptr->NotifySurfaceChanged();
        }
    } else {
        main_present_window.NotifySurfaceChanged();
    }
}

} // namespace Vulkan
