#include "common/settings.h"
#include <mutex>
#include <unordered_set>
#include <chrono>
// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <boost/container/static_vector.hpp>

#include "common/alignment.h"
#include "common/hash.h"
#include "common/microprofile.h"
#include "video_core/renderer_vulkan/pica_to_vk.h"
#include "video_core/renderer_vulkan/vk_graphics_pipeline.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"
#include "video_core/renderer_vulkan/vk_instance.h"
#include "video_core/renderer_vulkan/vk_render_manager.h"
#include "video_core/renderer_vulkan/vk_shader_util.h"

namespace Vulkan {

MICROPROFILE_DEFINE(Vulkan_Pipeline, "Vulkan", "Pipeline Building", MP_RGB(0, 192, 32));

vk::ShaderStageFlagBits MakeShaderStage(std::size_t index) {
    switch (index) {
    case 0:
        return vk::ShaderStageFlagBits::eVertex;
    case 1:
        return vk::ShaderStageFlagBits::eFragment;
    case 2:
        return vk::ShaderStageFlagBits::eGeometry;
    default:
        LOG_CRITICAL(Render_Vulkan, "Invalid shader stage index!");
        UNREACHABLE();
    }
    return vk::ShaderStageFlagBits::eVertex;
}

u64 StaticPipelineInfo::OptimizedHash(const Instance& instance) const {
    u64 info_hash = Common::HashCombine(
        shader_ids[0], shader_ids[1], shader_ids[2], Common::ComputeStructHash64(vertex_layout),
        Common::ComputeStructHash64(attachments), Common::ComputeStructHash64(blending));

    if (!instance.IsExtendedDynamicStateSupported()) {
        info_hash = Common::HashCombine(info_hash, Common::ComputeStructHash64(rasterization),
                                        Common::ComputeStructHash64(depth_stencil));
    }

    return info_hash;
}

u16 PipelineInfo::GetFinalColorWriteMask(const Instance& instance) {
    u16 color_write_mask = state.blending.color_write_mask;
    const bool is_logic_op_emulated =
        instance.NeedsLogicOpEmulation() && !state.blending.blend_enable;
    const bool is_logic_op_noop = state.blending.logic_op == Pica::FramebufferRegs::LogicOp::NoOp;
    if (is_logic_op_emulated && is_logic_op_noop) {
        // Color output is disabled by logic operation. We use color write mask to skip
        // color but allow depth write.
        color_write_mask = 0;
    }
    return color_write_mask;
}

Shader::Shader(const Instance& instance) : device{instance.GetDevice()} {}

Shader::Shader(const Instance& instance, vk::ShaderStageFlagBits stage, std::string code)
    : Shader{instance} {
    module = Compile(code, stage, instance.GetDevice());
    MarkDone();
}

Shader::~Shader() {
    if (device && module) {
        device.destroyShaderModule(module);
    }
}

GraphicsPipeline::GraphicsPipeline(const Instance& instance_, RenderManager& renderpass_cache_,
                                   const PipelineInfo& info_, vk::PipelineCache pipeline_cache_,
                                   vk::PipelineLayout layout_, std::array<Shader*, 3> stages_,
                                   Common::ThreadWorker* worker_)
    : instance{instance_}, renderpass_cache{renderpass_cache_}, worker{worker_},
      pipeline_layout{layout_}, pipeline_cache{pipeline_cache_}, info{info_}, stages{stages_} {}

GraphicsPipeline::~GraphicsPipeline() = default;

bool GraphicsPipeline::TryBuild(bool wait_built) {
    // The pipeline is currently being compiled. We can either wait for it
    // or skip the draw.
    if (is_pending) {
        if (!wait_built) GammaStats::Add(GammaStats::DrawsSkippedPending);
        return wait_built;
    }

    // If the shaders haven't been compiled yet, we cannot proceed.
    const bool shaders_pending = std::any_of(
        stages.begin(), stages.end(), [](Shader* shader) { return shader && !shader->IsDone(); });
    if (!wait_built && shaders_pending) {
        GammaStats::Add(GammaStats::DrawsSkippedPending);
        return false;
    }

    // Ask the driver if it can give us the pipeline quickly. GammaOS: not in hybrid mode
    // (wait_built false), where the draw never waits: the probe costs ~2 ms on the
    // emulation thread per new pipeline and a burst of them made 100+ ms frames. The
    // worker returns just as fast when the driver has the pipeline cached.
    if (!shaders_pending && (wait_built || !Settings::values.gammaos_hybrid_shaders) &&
        instance.IsPipelineCreationCacheControlSupported() && Build(true)) {
        GammaStats::Add(GammaStats::PipesFast);
        return true;
    }

    // Fallback to (a)synchronous compilation
    GammaStats::Add(GammaStats::PipesQueued);
    worker->QueueWork([this] { Build(); });
    is_pending = true;
    return wait_built;
}

namespace {
// GammaOS: pipeline library parts, kept for the life of the device (keyed by device + part key).
struct LibraryCache {
    std::mutex mutex;
    std::unordered_map<u64, vk::Pipeline> parts;
};
LibraryCache& Libraries() {
    static LibraryCache cache;
    return cache;
}
template <typename... Ts>
u64 KeyOf(u64 kind, const Ts&... values) {
    u64 h = kind;
    ((h = Common::HashCombine(h, Common::ComputeHash64(&values, sizeof(values)))), ...);
    return h;
}
} // namespace

vk::Pipeline GraphicsPipeline::LinkFromLibraries(
    const vk::GraphicsPipelineCreateInfo& full,
    const vk::PipelineVertexInputStateCreateInfo& vertex_input,
    const vk::PipelineInputAssemblyStateCreateInfo& input_assembly,
    const vk::PipelineViewportStateCreateInfo& viewport,
    const vk::PipelineRasterizationStateCreateInfo& raster,
    const vk::PipelineMultisampleStateCreateInfo& multisample,
    const vk::PipelineDepthStencilStateCreateInfo& depth,
    const vk::PipelineColorBlendStateCreateInfo& blend,
    const vk::PipelineDynamicStateCreateInfo& dynamic,
    std::span<const vk::PipelineShaderStageCreateInfo> stages_info, u32 stage_count) {
    const vk::Device device = instance.GetDevice();
    const u64 device_key = reinterpret_cast<u64>(static_cast<VkDevice>(device));
    const auto& st = info.state;
    // Shader stages by kind (VS + GS go to pre-rasterization, FS to the fragment part).
    std::array<vk::PipelineShaderStageCreateInfo, MAX_SHADER_STAGES> pre{};
    u32 pre_count = 0;
    vk::PipelineShaderStageCreateInfo frag{};
    bool has_frag = false;
    for (u32 i = 0; i < stage_count; i++) {
        if (stages_info[i].stage == vk::ShaderStageFlagBits::eFragment) {
            frag = stages_info[i];
            has_frag = true;
        } else {
            pre[pre_count++] = stages_info[i];
        }
    }
    const u64 pass_key = KeyOf(0, st.attachments);
    const u64 vi_key = KeyOf(1, st.vertex_layout, st.rasterization.topology.Value());
    const u64 pre_key = KeyOf(2, st.shader_ids[0], st.shader_ids[2], raster.cullMode,
                              raster.frontFace, raster.depthClampEnable, pass_key);
    const u64 fs_key = KeyOf(3, st.shader_ids[1], st.depth_stencil, has_frag, pass_key);
    const u64 out_key = KeyOf(4, st.blending, blend.pAttachments->colorWriteMask,
                              blend.logicOpEnable, pass_key);

    auto build_part = [&](u64 key, vk::GraphicsPipelineLibraryFlagsEXT part,
                          vk::GraphicsPipelineCreateInfo ci) -> vk::Pipeline {
        const u64 full_key = Common::HashCombine(device_key, key);
        auto& cache = Libraries();
        {
            std::scoped_lock lock{cache.mutex};
            if (auto it = cache.parts.find(full_key); it != cache.parts.end()) {
                return it->second;
            }
        }
        const vk::GraphicsPipelineLibraryCreateInfoEXT lib_info{.flags = part};
        ci.pNext = &lib_info;
        ci.flags |= vk::PipelineCreateFlagBits::eLibraryKHR;
        const auto res = device.createGraphicsPipeline(pipeline_cache, ci);
        if (res.result != vk::Result::eSuccess) {
            LOG_ERROR(Render_Vulkan, "Pipeline library part {:X} failed: {}",
                      static_cast<u32>(part), vk::to_string(res.result));
            return {};
        }
        std::scoped_lock lock{cache.mutex};
        auto [it, inserted] = cache.parts.try_emplace(full_key, res.value);
        if (!inserted) {
            // Another worker built the same part meanwhile: keep theirs.
            device.destroyPipeline(res.value);
        }
        return it->second;
    };

    const vk::Pipeline vi = build_part(
        vi_key, vk::GraphicsPipelineLibraryFlagBitsEXT::eVertexInputInterface,
        vk::GraphicsPipelineCreateInfo{.pVertexInputState = &vertex_input,
                                       .pInputAssemblyState = &input_assembly,
                                       .pDynamicState = &dynamic});
    const vk::Pipeline pr = build_part(
        pre_key, vk::GraphicsPipelineLibraryFlagBitsEXT::ePreRasterizationShaders,
        vk::GraphicsPipelineCreateInfo{.stageCount = pre_count,
                                       .pStages = pre.data(),
                                       .pViewportState = &viewport,
                                       .pRasterizationState = &raster,
                                       .pDynamicState = &dynamic,
                                       .layout = full.layout,
                                       .renderPass = full.renderPass});
    const vk::Pipeline fs = build_part(
        fs_key, vk::GraphicsPipelineLibraryFlagBitsEXT::eFragmentShader,
        vk::GraphicsPipelineCreateInfo{.stageCount = has_frag ? 1u : 0u,
                                       .pStages = &frag,
                                       .pMultisampleState = &multisample,
                                       .pDepthStencilState = &depth,
                                       .pDynamicState = &dynamic,
                                       .layout = full.layout,
                                       .renderPass = full.renderPass});
    const vk::Pipeline out = build_part(
        out_key, vk::GraphicsPipelineLibraryFlagBitsEXT::eFragmentOutputInterface,
        vk::GraphicsPipelineCreateInfo{.pMultisampleState = &multisample,
                                       .pColorBlendState = &blend,
                                       .pDynamicState = &dynamic,
                                       .renderPass = full.renderPass});
    if (!vi || !pr || !fs || !out) {
        return {};
    }
    const std::array libs{vi, pr, fs, out};
    const vk::PipelineLibraryCreateInfoKHR link_info{
        .libraryCount = static_cast<u32>(libs.size()),
        .pLibraries = libs.data(),
    };
    const vk::GraphicsPipelineCreateInfo link{
        .pNext = &link_info,
        .layout = full.layout,
    };
    const auto res = device.createGraphicsPipeline(pipeline_cache, link);
    if (res.result != vk::Result::eSuccess) {
        LOG_ERROR(Render_Vulkan, "Pipeline link failed: {}", vk::to_string(res.result));
        return {};
    }
    return res.value;
}

bool GraphicsPipeline::Build(bool fail_on_compile_required) {
    MICROPROFILE_SCOPE(Vulkan_Pipeline);

    const u32 stride_alignment = instance.GetMinVertexStrideAlignment();
    std::array<vk::VertexInputBindingDescription, MAX_VERTEX_BINDINGS> bindings;
    for (u32 i = 0; i < info.state.vertex_layout.binding_count; i++) {
        const auto& binding = info.state.vertex_layout.bindings[i];
        bindings[i] = vk::VertexInputBindingDescription{
            .binding = binding.binding,
            .stride = Common::AlignUp(binding.byte_count.Value(), stride_alignment),
            .inputRate = binding.fixed.Value() ? vk::VertexInputRate::eInstance
                                               : vk::VertexInputRate::eVertex,
        };
    }

    std::array<vk::VertexInputAttributeDescription, MAX_VERTEX_ATTRIBUTES> attributes;
    for (u32 i = 0; i < info.state.vertex_layout.attribute_count; i++) {
        const auto& attr = info.state.vertex_layout.attributes[i];
        const FormatTraits& traits = instance.GetTraits(attr.type, attr.size);
        attributes[i] = vk::VertexInputAttributeDescription{
            .location = attr.location,
            .binding = attr.binding,
            .format = traits.native,
            .offset = attr.offset,
        };

        // At the end there's always the fixed binding which takes up
        // at least 16 bytes so we should always be able to alias.
        if (traits.needs_emulation) {
            const FormatTraits& comp_four_traits = instance.GetTraits(attr.type, 4);
            attributes[i].format = comp_four_traits.native;
        }
    }

    const vk::PipelineVertexInputStateCreateInfo vertex_input_info = {
        .vertexBindingDescriptionCount = info.state.vertex_layout.binding_count,
        .pVertexBindingDescriptions = bindings.data(),
        .vertexAttributeDescriptionCount = info.state.vertex_layout.attribute_count,
        .pVertexAttributeDescriptions = attributes.data(),
    };

    const vk::PipelineInputAssemblyStateCreateInfo input_assembly = {
        .topology = PicaToVK::PrimitiveTopology(info.state.rasterization.topology),
        .primitiveRestartEnable = false,
    };

    const vk::PipelineRasterizationStateCreateInfo raster_state = {
        .depthClampEnable = instance.UseVertexDepth(),
        .rasterizerDiscardEnable = false,
        .cullMode = PicaToVK::CullMode(info.state.rasterization.cull_mode,
                                       info.state.rasterization.flip_viewport),
        .frontFace = PicaToVK::FrontFace(info.state.rasterization.cull_mode),
        .depthBiasEnable = false,
        .lineWidth = 1.0f,
    };

    const vk::PipelineMultisampleStateCreateInfo multisampling = {
        .rasterizationSamples = vk::SampleCountFlagBits::e1,
        .sampleShadingEnable = false,
    };

    // GammaOS: blending with Add, One and Zero for color and alpha writes the source unchanged,
    // and so does logic op Copy. Enabled, either one makes the GPU read the destination back for
    // every covered pixel (expensive at 2x and above on Adreno 6xx); turn them off.
    using Pica::FramebufferRegs;
    const auto& bl = info.state.blending;
    const bool blend_is_copy =
        bl.color_blend_eq.Value() == FramebufferRegs::BlendEquation::Add &&
        bl.alpha_blend_eq.Value() == FramebufferRegs::BlendEquation::Add &&
        bl.src_color_blend_factor.Value() == FramebufferRegs::BlendFactor::One &&
        bl.src_alpha_blend_factor.Value() == FramebufferRegs::BlendFactor::One &&
        bl.dst_color_blend_factor.Value() == FramebufferRegs::BlendFactor::Zero &&
        bl.dst_alpha_blend_factor.Value() == FramebufferRegs::BlendFactor::Zero;
    const bool blend_on = bl.blend_enable && !blend_is_copy;
    const bool logic_on = !bl.blend_enable && !instance.NeedsLogicOpEmulation() &&
                          bl.logic_op != FramebufferRegs::LogicOp::Copy;
    const vk::PipelineColorBlendAttachmentState colorblend_attachment = {
        .blendEnable = blend_on,
        .srcColorBlendFactor = PicaToVK::BlendFunc(info.state.blending.src_color_blend_factor),
        .dstColorBlendFactor = PicaToVK::BlendFunc(info.state.blending.dst_color_blend_factor),
        .colorBlendOp = PicaToVK::BlendEquation(info.state.blending.color_blend_eq),
        .srcAlphaBlendFactor = PicaToVK::BlendFunc(info.state.blending.src_alpha_blend_factor),
        .dstAlphaBlendFactor = PicaToVK::BlendFunc(info.state.blending.dst_alpha_blend_factor),
        .alphaBlendOp = PicaToVK::BlendEquation(info.state.blending.alpha_blend_eq),
        .colorWriteMask =
            static_cast<vk::ColorComponentFlags>(info.GetFinalColorWriteMask(instance)),
    };

    const vk::PipelineColorBlendStateCreateInfo color_blending = {
        .logicOpEnable = logic_on,
        .logicOp = PicaToVK::LogicOp(info.state.blending.logic_op),
        .attachmentCount = 1,
        .pAttachments = &colorblend_attachment,
        .blendConstants = std::array{1.0f, 1.0f, 1.0f, 1.0f},
    };

    const vk::Viewport viewport = {
        .x = 0.0f,
        .y = 0.0f,
        .width = 1.0f,
        .height = 1.0f,
        .minDepth = 0.0f,
        .maxDepth = 1.0f,
    };

    const vk::Rect2D scissor = {
        .offset = {0, 0},
        .extent = {1, 1},
    };

    const vk::PipelineViewportStateCreateInfo viewport_info = {
        .viewportCount = 1,
        .pViewports = &viewport,
        .scissorCount = 1,
        .pScissors = &scissor,
    };

    boost::container::static_vector<vk::DynamicState, 14> dynamic_states = {
        vk::DynamicState::eViewport,           vk::DynamicState::eScissor,
        vk::DynamicState::eStencilCompareMask, vk::DynamicState::eStencilWriteMask,
        vk::DynamicState::eStencilReference,   vk::DynamicState::eBlendConstants,
    };

    if (instance.IsExtendedDynamicStateSupported()) {
        constexpr std::array extended = {
            vk::DynamicState::eCullModeEXT,        vk::DynamicState::eDepthCompareOpEXT,
            vk::DynamicState::eDepthTestEnableEXT, vk::DynamicState::eDepthWriteEnableEXT,
            vk::DynamicState::eFrontFaceEXT,       vk::DynamicState::ePrimitiveTopologyEXT,
            vk::DynamicState::eStencilOpEXT,       vk::DynamicState::eStencilTestEnableEXT,
        };
        dynamic_states.insert(dynamic_states.end(), extended.begin(), extended.end());
    }

    const vk::PipelineDynamicStateCreateInfo dynamic_info = {
        .dynamicStateCount = static_cast<u32>(dynamic_states.size()),
        .pDynamicStates = dynamic_states.data(),
    };

    const vk::StencilOpState stencil_op_state = {
        .failOp = PicaToVK::StencilOp(info.state.depth_stencil.stencil_fail_op),
        .passOp = PicaToVK::StencilOp(info.state.depth_stencil.stencil_pass_op),
        .depthFailOp = PicaToVK::StencilOp(info.state.depth_stencil.stencil_depth_fail_op),
        .compareOp = PicaToVK::CompareFunc(info.state.depth_stencil.stencil_compare_op),
    };

    const vk::PipelineDepthStencilStateCreateInfo depth_info = {
        .depthTestEnable = static_cast<u32>(info.state.depth_stencil.depth_test_enable.Value()),
        .depthWriteEnable = static_cast<u32>(info.state.depth_stencil.depth_write_enable.Value()),
        .depthCompareOp = PicaToVK::CompareFunc(info.state.depth_stencil.depth_compare_op),
        .depthBoundsTestEnable = false,
        .stencilTestEnable = static_cast<u32>(info.state.depth_stencil.stencil_test_enable.Value()),
        .front = stencil_op_state,
        .back = stencil_op_state,
    };

    u32 shader_count = 0;
    std::array<vk::PipelineShaderStageCreateInfo, MAX_SHADER_STAGES> shader_stages;
    for (std::size_t i = 0; i < stages.size(); i++) {
        Shader* shader = stages[i];
        if (!shader) {
            continue;
        }

        shader->WaitDone();
        shader_stages[shader_count++] = vk::PipelineShaderStageCreateInfo{
            .stage = MakeShaderStage(i),
            .module = shader->Handle(),
            .pName = "main",
        };
    }

    vk::GraphicsPipelineCreateInfo pipeline_info = {
        .stageCount = shader_count,
        .pStages = shader_stages.data(),
        .pVertexInputState = &vertex_input_info,
        .pInputAssemblyState = &input_assembly,
        .pViewportState = &viewport_info,
        .pRasterizationState = &raster_state,
        .pMultisampleState = &multisampling,
        .pDepthStencilState = &depth_info,
        .pColorBlendState = &color_blending,
        .pDynamicState = &dynamic_info,
        .layout = pipeline_layout,
        .renderPass = renderpass_cache.GetRenderpass(info.state.attachments.color,
                                                     info.state.attachments.depth, false),
    };

    if (fail_on_compile_required) {
        pipeline_info.flags |= vk::PipelineCreateFlagBits::eFailOnPipelineCompileRequiredEXT;
    }
    const auto build_start = std::chrono::steady_clock::now();
    // GammaOS: with graphics pipeline libraries (persist.gammaos.azahar_nano.gpl, on by
    // default where the driver links fast) each part is built once and a new pipeline only
    // links them: a new shader pairing no longer recompiles both shaders.
    static const bool gpl_wanted = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.gpl", v);
        return v[0] != '0';
    }();
    if (gpl_wanted && !fail_on_compile_required && instance.IsGraphicsPipelineLibrarySupported()) {
        const vk::Pipeline linked = LinkFromLibraries(
            pipeline_info, vertex_input_info, input_assembly, viewport_info, raster_state,
            multisampling, depth_info, color_blending, dynamic_info, shader_stages, shader_count);
        if (linked) {
            pipeline = vk::UniquePipeline{linked, instance.GetDevice()};
            MarkDone();
            return true;
        }
    }
    auto result = instance.GetDevice().createGraphicsPipelineUnique(pipeline_cache, pipeline_info);
    if (fail_on_compile_required) {
        // GammaOS: time of the "only if cached" attempts (run on the emulation thread).
        const u64 us = static_cast<u64>(std::chrono::duration_cast<std::chrono::microseconds>(
                                            std::chrono::steady_clock::now() - build_start)
                                            .count());
        GammaStats::Add(GammaStats::FastAttempts);
        GammaStats::Add(GammaStats::FastAttemptUs, us);
        if (us > GammaStats::Peek(GammaStats::FastAttemptMaxUs)) {
            GammaStats::Set(GammaStats::FastAttemptMaxUs, us);
        }
    }
    if (!fail_on_compile_required) {
        // GammaOS: split build time by whether this shader trio was built before.
        static std::mutex seen_mutex;
        static std::unordered_set<u64> seen_trios;
        u64 trio = 0;
        for (const Shader* sh : stages) {
            trio = trio * 1099511628211ULL ^ reinterpret_cast<u64>(sh);
        }
        bool repeat;
        bool vs_seen = false, fs_seen = false;
        {
            static std::unordered_set<const void*> seen_vs, seen_fs;
            std::scoped_lock lk{seen_mutex};
            repeat = !seen_trios.insert(trio).second;
            vs_seen = !seen_vs.insert(stages[0]).second;
            fs_seen = !seen_fs.insert(stages[1]).second;
        }
        const u64 us = static_cast<u64>(std::chrono::duration_cast<std::chrono::microseconds>(
                                            std::chrono::steady_clock::now() - build_start)
                                            .count());
        // Kind of pipeline: trivial vertex shader (software vertex path) with a fragment
        // ubershader, trivial vertex shader with a generated fragment shader, or a PICA
        // program vertex shader (hardware path).
        const bool trivial_vs = stages[0] && stages[0]->program == "trivial";
        const bool uber_fs = stages[1] && stages[1]->program == "uber";
        GammaStats::Add(!trivial_vs ? GammaStats::PipeHwBuilds
                        : uber_fs   ? GammaStats::PipeUberBuilds
                                    : GammaStats::PipeSwBuilds);
        GammaStats::Add(!trivial_vs ? GammaStats::PipeHwUs
                        : uber_fs   ? GammaStats::PipeUberUs
                                    : GammaStats::PipeSwUs,
                        us);
        if (!repeat) {
            // New trio split by which stage the driver has compiled before.
            const auto k = vs_seen && fs_seen ? 0 : vs_seen ? 1 : fs_seen ? 2 : 3;
            GammaStats::Add(static_cast<GammaStats::Counter>(GammaStats::NewBothSeen + 2 * k));
            GammaStats::Add(static_cast<GammaStats::Counter>(GammaStats::NewBothSeenUs + 2 * k), us);
        }
        GammaStats::Add(repeat ? GammaStats::PipeRepeatBuilds : GammaStats::PipeFirstBuilds);
        GammaStats::Add(repeat ? GammaStats::PipeRepeatUs : GammaStats::PipeFirstUs, us);
        GammaStats::Add(GammaStats::PipeBuildUs,
                        static_cast<u64>(std::chrono::duration_cast<std::chrono::microseconds>(
                                             std::chrono::steady_clock::now() - build_start)
                                             .count()));
        GammaStats::Add(GammaStats::PipeBuilds);
    }
    if (result.result == vk::Result::eSuccess) {
        pipeline = std::move(result.value);
    } else if (result.result == vk::Result::eErrorPipelineCompileRequiredEXT) {
        return false;
    } else {
        UNREACHABLE_MSG("Graphics pipeline creation failed!");
    }

    MarkDone();
    return true;
}

} // namespace Vulkan
