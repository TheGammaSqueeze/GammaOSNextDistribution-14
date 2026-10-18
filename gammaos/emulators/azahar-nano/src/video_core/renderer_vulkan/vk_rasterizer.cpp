#include <fstream>
#include <set>
#include <chrono>
#include "video_core/shader/generator/uber_fs_shader_gen.h"
#include <sys/system_properties.h>
// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include "common/alignment.h"
#include "common/gammaos_tweak.h"
#include "common/literals.h"
#include "common/logging/log.h"
#include "common/math_util.h"
#include "common/microprofile.h"
#include "common/settings.h"
#include "core/core.h"
#include "core/core_timing.h"
#include "core/loader/loader.h"
#include "core/memory.h"
#include "video_core/pica/pica_core.h"
#include "video_core/renderer_vulkan/renderer_vulkan.h"
#include "video_core/renderer_vulkan/vk_instance.h"
#include "common/scope_exit.h"
#include <cstring>
#include "video_core/gpu.h"
#include "video_core/gammaos_snapshot.h"
#include "video_core/renderer_vulkan/vk_rasterizer.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"
#include "video_core/renderer_vulkan/vk_gammaos_gpu_timer.h"
#include "video_core/renderer_vulkan/vk_gammaos_game_prop.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"
#include "video_core/texture/texture_decode.h"

namespace Vulkan {

namespace {

MICROPROFILE_DEFINE(Vulkan_VS, "Vulkan", "Vertex Shader Setup", MP_RGB(192, 128, 128));
MICROPROFILE_DEFINE(Vulkan_GS, "Vulkan", "Geometry Shader Setup", MP_RGB(128, 192, 128));
MICROPROFILE_DEFINE(Vulkan_Drawing, "Vulkan", "Drawing", MP_RGB(128, 128, 192));

using TriangleTopology = Pica::PipelineRegs::TriangleTopology;
using VideoCore::SurfaceType;

using namespace Common::Literals;
using namespace Pica::Shader::Generator;

constexpr u64 STREAM_BUFFER_SIZE = 64_MiB;
constexpr u64 UNIFORM_BUFFER_SIZE = 8_MiB;
constexpr u64 TEXTURE_BUFFER_SIZE = 2_MiB;

constexpr vk::BufferUsageFlags BUFFER_USAGE =
    vk::BufferUsageFlagBits::eVertexBuffer | vk::BufferUsageFlagBits::eIndexBuffer;

struct DrawParams {
    u32 vertex_count;
    s32 vertex_offset;
    u32 binding_count;
    std::array<u32, 16> bindings;
    bool is_indexed;
};

// GammaOS: the vertex and index stream in CPU cached memory on Mali. A Bifrost driver finds the
// vertex range of every indexed draw by reading its indices on the CPU, and from the default
// write-combined memory every load goes to DRAM (the scan was 38 percent of the VulkanWorker
// with malisx2 on the RG DS). The commits flush the written lines instead.
// persist.gammaos.azahar_nano.stream_cached: 1 on, 0 off, unset on for Mali only.
[[nodiscard]] BufferType StreamBufferType() {
    static const bool cached = Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.stream_cached");
    return cached ? BufferType::StreamCached : BufferType::Stream;
}

[[nodiscard]] u64 TextureBufferSize(const Instance& instance) {
    // Use the smallest texel size from the texel views
    // which corresponds to eR32G32Sfloat
    const u64 max_size = instance.MaxTexelBufferElements() * 8;
    return std::min(max_size, TEXTURE_BUFFER_SIZE);
}

} // Anonymous namespace

namespace {
// GammaOS: logs any single draw-path step that takes longer than 10 ms, only with
// persist.gammaos.azahar_nano.slow_step_log=1: clock reads are syscalls on this kernel (the
// arm64 erratum 858921 workaround disables the vDSO counter), ~12% of the emulation thread in
// Mario Kart 7 when always on.
struct SlowStep {
    static bool Enabled() {
        static const bool on = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.slow_step_log", v);
            return v[0] == '1';
        }();
        return on;
    }
    const char* name;
    std::chrono::steady_clock::time_point start;
    explicit SlowStep(const char* n)
        : name{n}, start{Enabled() ? std::chrono::steady_clock::now()
                                   : std::chrono::steady_clock::time_point{}} {}
    ~SlowStep() {
        if (!Enabled()) {
            return;
        }
        const auto ms = std::chrono::duration_cast<std::chrono::microseconds>(
                            std::chrono::steady_clock::now() - start)
                            .count() /
                        1000.0;
        if (ms > 10.0) {
            LOG_WARNING(Render_Vulkan, "slow step {} {:.1f} ms", name, ms);
        }
    }
};
// GammaOS: persist.gammaos.azahar_nano.hybrid: empty or 1 = hybrid (skip until the fast
// pipeline is built), 0 = off (wait for every compile), uber = experimental ubershader.
void ApplyHybridProp() {
    char v[PROP_VALUE_MAX] = {};
    __system_property_get("persist.gammaos.azahar_nano.hybrid", v);
    const std::string_view mode{v};
    if (mode == "0") {
        Settings::values.gammaos_hybrid_shaders = false;
    } else if (mode == "uber") {
        Settings::values.gammaos_hybrid_shaders = true;
        Settings::values.gammaos_hybrid_uber = true;
    }
}
int HybridDebugBits() {
    // Read once: this runs for every draw.
    static const int value = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.uber_strip", v);
        return atoi(v);
    }();
    return value;
}
bool HybridDebugSwSpecialized() {
    return (HybridDebugBits() & 64) != 0;
}
// Debug 256: every hardware draw falls back; 512: the software path always uses the uber.
bool HybridDebugForceFallback() {
    return (HybridDebugBits() & 256) != 0;
}
bool HybridDebugForceUber() {
    return (HybridDebugBits() & 512) != 0;
}

} // namespace

RasterizerVulkan::RasterizerVulkan(Memory::MemorySystem& memory, Pica::PicaCore& pica,
                                   VideoCore::CustomTexManager& custom_tex_manager,
                                   VideoCore::RendererBase& renderer,
                                   Frontend::EmuWindow& emu_window, const Instance& instance,
                                   Scheduler& scheduler, RenderManager& renderpass_cache,
                                   DescriptorUpdateQueue& update_queue_, u32 image_count)
    : RasterizerAccelerated{memory, pica}, instance{instance}, scheduler{scheduler},
      renderpass_cache{renderpass_cache}, update_queue{update_queue_},
      pipeline_cache{instance, scheduler, renderpass_cache, update_queue},
      runtime{instance, scheduler, renderpass_cache, update_queue, image_count},
      res_cache{memory, custom_tex_manager, runtime, regs, renderer},
      stream_buffer{instance, scheduler, BUFFER_USAGE, STREAM_BUFFER_SIZE, StreamBufferType()},
      uniform_buffer{instance, scheduler, vk::BufferUsageFlagBits::eUniformBuffer,
                     UNIFORM_BUFFER_SIZE},
      texture_buffer{instance, scheduler, vk::BufferUsageFlagBits::eUniformTexelBuffer,
                     TextureBufferSize(instance)},
      texture_lf_buffer{instance, scheduler, vk::BufferUsageFlagBits::eUniformTexelBuffer,
                        TextureBufferSize(instance)},
      async_shaders{Settings::values.async_shader_compilation.GetValue()} {
    ApplyHybridProp();
    (void)HybridDebugBits(); // latch the debug switches at startup
    vertex_depth_enabled = instance.UseVertexDepth();
    // GammaOS: entries 96 to 127 of the PICA uniform table read as 1.0 (shader_uniforms.h);
    // the cache below is updated by dirty range, so they are set once here.
    for (std::size_t j = 96; j < vs_pica_cache.f.size(); j++) {
        vs_pica_cache.f[j] = Common::MakeVec<f32>(1.f, 1.f, 1.f, 1.f);
    }

    vertex_buffers.fill(stream_buffer.Handle());

    // Query uniform buffer alignment.
    uniform_buffer_alignment = instance.UniformMinAlignment();
    uniform_size_aligned_vs_pica =
        Common::AlignUp<u32>(sizeof(VSPicaUniformData), uniform_buffer_alignment);
    uniform_size_aligned_vs = Common::AlignUp<u32>(sizeof(VSUniformData), uniform_buffer_alignment);
    uniform_size_aligned_fs = Common::AlignUp<u32>(sizeof(FSUniformData), uniform_buffer_alignment);

    // Define vertex layout for software shaders
    MakeSoftwareVertexLayout();
    pipeline_cache.SetSoftwareLayout(software_layout);
    pipeline_info.state.vertex_layout = software_layout;

    const vk::Device device = instance.GetDevice();
    texture_lf_view = device.createBufferViewUnique({
        .buffer = texture_lf_buffer.Handle(),
        .format = vk::Format::eR32G32Sfloat,
        .offset = 0,
        .range = VK_WHOLE_SIZE,
    });
    texture_rg_view = device.createBufferViewUnique({
        .buffer = texture_buffer.Handle(),
        .format = vk::Format::eR32G32Sfloat,
        .offset = 0,
        .range = VK_WHOLE_SIZE,
    });
    texture_rgba_view = device.createBufferViewUnique({
        .buffer = texture_buffer.Handle(),
        .format = vk::Format::eR32G32B32A32Sfloat,
        .offset = 0,
        .range = VK_WHOLE_SIZE,
    });

    scheduler.RegisterOnSubmit([this, &renderpass_cache] {
        renderpass_cache.EndRendering();
        // GammaOS: the cached stream's writes reach memory before the GPU can read them.
        stream_buffer.FlushPending();
    });

    // Prepare the static buffer descriptor set.
    const auto buffer_set = pipeline_cache.Acquire(DescriptorHeapType::Buffer);
    update_queue.AddBuffer(buffer_set, 0, uniform_buffer.Handle(), 0, sizeof(VSPicaUniformData));
    update_queue.AddBuffer(buffer_set, 1, uniform_buffer.Handle(), 0, sizeof(VSUniformData));
    update_queue.AddBuffer(buffer_set, 2, uniform_buffer.Handle(), 0, sizeof(FSUniformData));
    update_queue.AddTexelBuffer(buffer_set, 3, *texture_lf_view);
    update_queue.AddTexelBuffer(buffer_set, 4, *texture_rg_view);
    update_queue.AddTexelBuffer(buffer_set, 5, *texture_rgba_view);

    const auto texture_set = pipeline_cache.Acquire(DescriptorHeapType::Texture);
    // The null surface stands in for shadow maps too, as a storage image.
    res_cache.EnsureStorage(VideoCore::NULL_SURFACE_ID);
    Surface& null_surface = res_cache.GetSurface(VideoCore::NULL_SURFACE_ID);
    Sampler& null_sampler = res_cache.GetSampler(VideoCore::NULL_SAMPLER_ID);

    // Prepare texture and utility descriptor sets.
    for (u32 i = 0; i < 3; i++) {
        update_queue.AddImageSampler(texture_set, i, 0, null_surface.ImageView(),
                                     null_sampler.Handle());
    }

    const auto utility_set = pipeline_cache.Acquire(DescriptorHeapType::Utility);
    update_queue.AddStorageImage(utility_set, 0, null_surface.StorageView());
    update_queue.AddImageSampler(utility_set, 1, 0, null_surface.ImageView(),
                                 null_sampler.Handle());
    update_queue.Flush();
}

RasterizerVulkan::~RasterizerVulkan() = default;

void RasterizerVulkan::TickFrame() {
    fallback_vertices_this_frame = 0;
    ++frame_index;
    if (budget_held_since.size() > 4096) {
        budget_held_since.clear();
    }
    // GammaOS: draining the worker at every frame kept the emulation thread from building the
    // next frame while the worker recorded this one (Mario Kart 7 and SM3DL levels ran 5-8%
    // faster without it), but never waiting let the emulation thread run far ahead, which was
    // 2.5-4% slower in GPU-heavy scenes (SM3DL title). Wait for the previous frame's work only:
    // one frame of overlap. persist.gammaos.azahar_nano.tick_wait: 1 = full drain every frame,
    // 2 = never wait (the cache tick still drains when it has to).
    static const int wait_mode = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.tick_wait", v);
        return v[0] == '1' ? 1 : v[0] == '2' ? 2 : 0;
    }();
    if (wait_mode == 1 || res_cache.TickFrameNeedsIdleWorker()) {
        scheduler.WaitWorker();
    } else if (wait_mode == 0) {
        scheduler.DispatchWork();
        const u64 mark = scheduler.DispatchedChunks();
        scheduler.WaitWorkerUpTo(previous_frame_mark);
        previous_frame_mark = mark;
    }
    res_cache.TickFrame();
    pipeline_cache.TickFrame();
}

void RasterizerVulkan::LoadDefaultDiskResources(
    const std::atomic_bool& stop_loading, const VideoCore::DiskResourceLoadCallback& callback) {

    u64 program_id;
    if (Core::System::GetInstance().GetAppLoader().ReadProgramId(program_id) !=
        Loader::ResultStatus::Success) {
        program_id = 0;
    }

    if (callback) {
        callback(VideoCore::LoadCallbackStage::Prepare, 0, 0, "");
    }

    pipeline_cache.SetProgramID(program_id);
    pipeline_cache.SetAccurateMul(accurate_mul);
    pipeline_cache.LoadCache(stop_loading, callback);

    if (callback) {
        callback(VideoCore::LoadCallbackStage::Complete, 0, 0, "");
    }
}

void RasterizerVulkan::SyncDrawState() {
    SyncDrawUniforms();

    // SyncCullMode();
    pipeline_info.state.rasterization.cull_mode.Assign(regs.rasterizer.cull_mode);
    // If the framebuffer is flipped, request to also flip vulkan viewport
    const bool is_flipped = regs.framebuffer.framebuffer.IsFlipped();
    pipeline_info.state.rasterization.flip_viewport.Assign(is_flipped);
    // SyncBlendEnabled();
    pipeline_info.state.blending.blend_enable = regs.framebuffer.output_merger.alphablend_enable;
    // SyncBlendFuncs();
    pipeline_info.state.blending.color_blend_eq.Assign(
        regs.framebuffer.output_merger.alpha_blending.blend_equation_rgb);
    pipeline_info.state.blending.alpha_blend_eq.Assign(
        regs.framebuffer.output_merger.alpha_blending.blend_equation_a);
    pipeline_info.state.blending.src_color_blend_factor.Assign(
        regs.framebuffer.output_merger.alpha_blending.factor_source_rgb);
    pipeline_info.state.blending.dst_color_blend_factor.Assign(
        regs.framebuffer.output_merger.alpha_blending.factor_dest_rgb);
    pipeline_info.state.blending.src_alpha_blend_factor.Assign(
        regs.framebuffer.output_merger.alpha_blending.factor_source_a);
    pipeline_info.state.blending.dst_alpha_blend_factor.Assign(
        regs.framebuffer.output_merger.alpha_blending.factor_dest_a);
    // SyncBlendColor();
    pipeline_info.dynamic_info.blend_color = regs.framebuffer.output_merger.blend_const.raw;
    // SyncLogicOp();
    // SyncColorWriteMask();
    pipeline_info.state.blending.logic_op = regs.framebuffer.output_merger.logic_op;

    const u32 color_mask = regs.framebuffer.framebuffer.allow_color_write != 0
                               ? (regs.framebuffer.output_merger.depth_color_mask >> 8) & 0xF
                               : 0;
    pipeline_info.state.blending.color_write_mask = color_mask;

    // SyncStencilTest();
    const auto& stencil_test = regs.framebuffer.output_merger.stencil_test;
    const bool test_enable = stencil_test.enable && regs.framebuffer.framebuffer.depth_format ==
                                                        Pica::FramebufferRegs::DepthFormat::D24S8;

    pipeline_info.state.depth_stencil.stencil_test_enable.Assign(test_enable);
    pipeline_info.state.depth_stencil.stencil_fail_op.Assign(stencil_test.action_stencil_fail);
    pipeline_info.state.depth_stencil.stencil_pass_op.Assign(stencil_test.action_depth_pass);
    pipeline_info.state.depth_stencil.stencil_depth_fail_op.Assign(stencil_test.action_depth_fail);
    pipeline_info.state.depth_stencil.stencil_compare_op.Assign(stencil_test.func);
    pipeline_info.dynamic_info.stencil_reference = stencil_test.reference_value;
    pipeline_info.dynamic_info.stencil_compare_mask = stencil_test.input_mask;
    // SyncStencilWriteMask();
    pipeline_info.dynamic_info.stencil_write_mask =
        (regs.framebuffer.framebuffer.allow_depth_stencil_write != 0)
            ? static_cast<u32>(regs.framebuffer.output_merger.stencil_test.write_mask)
            : 0;
    // SyncDepthTest();
    const bool test_enabled = regs.framebuffer.output_merger.depth_test_enable == 1 ||
                              regs.framebuffer.output_merger.depth_write_enable == 1;
    const auto compare_op = regs.framebuffer.output_merger.depth_test_enable == 1
                                ? regs.framebuffer.output_merger.depth_test_func.Value()
                                : Pica::FramebufferRegs::CompareFunc::Always;

    pipeline_info.state.depth_stencil.depth_test_enable.Assign(test_enabled);
    pipeline_info.state.depth_stencil.depth_compare_op.Assign(compare_op);
    // SyncDepthWriteMask();
    const bool write_enable = (regs.framebuffer.framebuffer.allow_depth_stencil_write != 0 &&
                               regs.framebuffer.output_merger.depth_write_enable);
    pipeline_info.state.depth_stencil.depth_write_enable.Assign(write_enable);
}

bool RasterizerVulkan::SetupVertexArray() {
    const auto [vs_input_index_min, vs_input_index_max, vs_input_size] = vertex_info;
    auto [array_ptr, array_offset, invalidate] = stream_buffer.Map(vs_input_size, 16);

    /**
     * The Nintendo 3DS has 12 attribute loaders which are used to tell the GPU
     * how to interpret vertex data. The program firsts sets GPUREG_ATTR_BUF_BASE to the base
     * address containing the vertex array data. The data for each attribute loader (i) can be found
     * by adding GPUREG_ATTR_BUFi_OFFSET to the base address. Attribute loaders can be thought
     * as something analogous to Vulkan bindings. The user can store attributes in separate loaders
     * or interleave them in the same loader.
     **/
    const auto& vertex_attributes = regs.pipeline.vertex_attributes;
    const PAddr base_address = vertex_attributes.GetPhysicalBaseAddress(); // GPUREG_ATTR_BUF_BASE
    const u32 stride_alignment = instance.GetMinVertexStrideAlignment();

    VertexLayout& layout = pipeline_info.state.vertex_layout;
    layout.binding_count = 0;
    layout.attribute_count = 16;
    enable_attributes.fill(false);

    u32 buffer_offset = 0;
    for (const auto& loader : vertex_attributes.attribute_loaders) {
        if (loader.component_count == 0 || loader.byte_count == 0) {
            continue;
        }

        // Analyze the attribute loader by checking which attributes it provides
        u32 offset = 0;
        for (u32 comp = 0; comp < loader.component_count && comp < 12; comp++) {
            const u32 attribute_index = loader.GetComponent(comp);
            if (attribute_index >= 12) {
                // Attribute ids 12, to 15 signify 4, 8, 12 and 16-byte paddings respectively.
                offset = Common::AlignUp(offset, 4);
                offset += (attribute_index - 11) * 4;
                continue;
            }

            const u32 size = vertex_attributes.GetNumElements(attribute_index);
            if (size == 0) {
                continue;
            }

            offset =
                Common::AlignUp(offset, vertex_attributes.GetElementSizeInBytes(attribute_index));

            const u32 input_reg = regs.vs.GetRegisterForAttribute(attribute_index);
            const auto format = vertex_attributes.GetFormat(attribute_index);

            VertexAttribute& attribute = layout.attributes[input_reg];
            attribute.binding.Assign(layout.binding_count);
            attribute.location.Assign(input_reg);
            attribute.offset.Assign(offset);
            attribute.type.Assign(format);
            attribute.size.Assign(size);

            enable_attributes[input_reg] = true;
            offset += vertex_attributes.GetStride(attribute_index);
        }

        const PAddr data_addr =
            base_address + loader.data_offset + (vs_input_index_min * loader.byte_count);
        const u32 vertex_num = vs_input_index_max - vs_input_index_min + 1;
        u32 data_size = loader.byte_count * vertex_num;
        res_cache.FlushRegion(data_addr, data_size);

        // GammaOS: the raw span (same pointer and size as a MemoryRef, without its reference
        // count traffic for every loader of every draw); async GPU mode 3 reads the bytes
        // snapshotted when the list was submitted.
        auto [src_raw, src_size] = memory.GetPhysicalSpan(data_addr);
        const u8* src_span_raw = src_raw;
        if (const u8* snap = VideoCore::GammaSnapshot::Find(data_addr, data_size)) {
            src_raw = const_cast<u8*>(snap);
            src_size = data_size;
        }
        if (src_size < data_size) {
            LOG_ERROR(Render_Vulkan,
                      "Vertex buffer size {} exceeds available space {} at address {:#016X}",
                      data_size, src_size, data_addr);
            return false; // GammaOS: the draw is skipped instead of reading past the region
        }

        const u8* src_ptr = src_raw;
        u8* dst_ptr = array_ptr + buffer_offset;

        // Align stride up if required by Vulkan implementation.
        const u32 aligned_stride =
            Common::AlignUp(static_cast<u32>(loader.byte_count), stride_alignment);

        // GammaOS vertex cache: when this loader's data is byte-identical to what an earlier
        // draw uploaded in the current lap of the stream buffer, bind that copy instead of
        // copying again (static meshes drawn every frame, like skinned characters whose
        // bones change only the uniforms).
        // persist.gammaos.azahar_nano.vtx_cache=0 turns it off.
        static const bool vtx_cache = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.vtx_cache", v);
            return v[0] != '0';
        }();
        constexpr u32 MinCachedBytes = 1024;
        u64 data_hash = 0;
        u64 cache_key = 0;
        if (vtx_cache && data_size >= MinCachedBytes && src_size >= data_size) {
            if (vertex_cache_lap != stream_buffer.Lap() || vertex_cache.size() > 8192) {
                vertex_cache.clear();
                vertex_cache_lap = stream_buffer.Lap();
            }
            cache_key = (static_cast<u64>(data_addr) << 32) ^ (static_cast<u64>(data_size) << 8) ^
                        aligned_stride;
            const auto it = vertex_cache.find(cache_key);
            // GammaOS hash memo: a range hashed earlier in the same command list, with no
            // rasterizer download writing guest memory since, still has that hash (no guest code
            // runs during a command list). 3D Land hashes a third of its vertex bytes twice.
            // persist.gammaos.azahar_nano.vtx_memo=0 hashes every time; default Mali only, and
            // off with the asynchronous GPU worker (the guest runs during its command lists).
            static const bool async_worker = [] {
                char v[PROP_VALUE_MAX] = {};
                GammaGameProp::Get("async_gpu", v);
                return v[0] >= '1' && v[0] <= '3';
            }();
            static const bool vtx_memo =
                Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.vtx_memo");
            // With the worker the guest runs during a list, so the memo only holds for bytes
            // that came from the submit-time snapshot (mode 3), which cannot change.
            const bool memo_ok = vtx_memo && (!async_worker || src_raw != src_span_raw);
            const u64 list_serial = pica.CmdListSerial();
            const u64 guest_writes = res_cache.GuestWrites();
            if (memo_ok && it != vertex_cache.end() && it->second.cmd_list == list_serial &&
                it->second.guest_writes == guest_writes && it->second.addr == data_addr &&
                it->second.size == data_size && it->second.stride == aligned_stride) {
                data_hash = it->second.hash;
                GammaStats::Add(GammaStats::VertexBytesRehashed, data_size);
            } else {
                data_hash = Common::ComputeHash64(src_ptr, data_size);
                GammaStats::Add(GammaStats::VertexBytesHashed, data_size);
            }
            if (it != vertex_cache.end() && it->second.hash == data_hash &&
                it->second.addr == data_addr && it->second.size == data_size &&
                it->second.stride == aligned_stride) {
                it->second.cmd_list = list_serial;
                it->second.guest_writes = guest_writes;
            }
            if (it != vertex_cache.end() && it->second.hash == data_hash) {
                // Debug: persist.gammaos.azahar_nano.vtx_cache_verify=1 compares the reused
                // copy with the game's data byte for byte and counts any difference.
                static const bool verify = [] {
                    char v[PROP_VALUE_MAX] = {};
                    __system_property_get("persist.gammaos.azahar_nano.vtx_cache_verify", v);
                    return v[0] == '1';
                }();
                if (verify) {
                    static u64 checked = 0, bad = 0;
                    const u8* copy = stream_buffer.MappedBase() + it->second.offset;
                    bool same = true;
                    if (aligned_stride == loader.byte_count) {
                        same = std::memcmp(copy, src_ptr, data_size) == 0;
                    } else {
                        for (u32 v = 0; v < vertex_num && same; v++) {
                            same = std::memcmp(copy + v * aligned_stride,
                                               src_ptr + v * loader.byte_count,
                                               loader.byte_count) == 0;
                        }
                    }
                    ++checked;
                    if (!same) {
                        ++bad;
                        LOG_ERROR(Render_Vulkan, "vertex cache: stale copy at {:X} for {:08X}+{}",
                                  it->second.offset, data_addr, data_size);
                    }
                    if ((checked & 4095) == 0) {
                        LOG_INFO(Render_Vulkan, "vertex cache verify: {} reuses checked, {} stale",
                                 checked, bad);
                    }
                }
                VertexBinding& binding = layout.bindings[layout.binding_count];
                binding.binding.Assign(layout.binding_count);
                binding.fixed.Assign(0);
                binding.byte_count.Assign(loader.byte_count);
                binding_offsets[layout.binding_count++] = it->second.offset;
                // The ring must not overwrite that copy before this draw has run.
                stream_buffer.Touch(it->second.offset);
                GammaStats::Add(GammaStats::VertexBytesReused, data_size);
                continue;
            }
        }
        GammaStats::Add(GammaStats::VertexBytesCopied, data_size);
        if (aligned_stride == loader.byte_count) {
            std::memcpy(dst_ptr, src_ptr, data_size);
        } else {
            for (std::size_t vertex = 0; vertex < vertex_num; vertex++) {
                std::memcpy(dst_ptr + vertex * aligned_stride, src_ptr + vertex * loader.byte_count,
                            loader.byte_count);
            }
        }

        // Create the binding associated with this loader
        VertexBinding& binding = layout.bindings[layout.binding_count];
        binding.binding.Assign(layout.binding_count);
        binding.fixed.Assign(0);
        // Will be adjusted on pipeline build, to keep the info transferable.
        binding.byte_count.Assign(loader.byte_count);

        // Keep track of the binding offsets so we can bind the vertex buffer later
        binding_offsets[layout.binding_count++] = static_cast<u32>(array_offset + buffer_offset);
        if (cache_key != 0) {
            vertex_cache[cache_key] = CachedVertices{
                data_hash,           static_cast<u32>(array_offset + buffer_offset),
                data_addr,           data_size,
                aligned_stride,      pica.CmdListSerial(),
                res_cache.GuestWrites()};
        }
        buffer_offset += Common::AlignUp(aligned_stride * vertex_num, 4);
    }

    stream_buffer.Commit(buffer_offset);

    // Assign the rest of the attributes to the last binding
    SetupFixedAttribs();
    return true;
}

void RasterizerVulkan::SetupFixedAttribs() {
    const auto& vertex_attributes = regs.pipeline.vertex_attributes;
    VertexLayout& layout = pipeline_info.state.vertex_layout;

    auto [fixed_ptr, fixed_offset, _] = stream_buffer.Map(16 * sizeof(Common::Vec4f), 0);
    binding_offsets[layout.binding_count] = static_cast<u32>(fixed_offset);

    // Reserve the last binding for fixed and default attributes
    // Place the default attrib at offset zero for easy access
    static const Common::Vec4f default_attrib{0.f, 0.f, 0.f, 1.f};
    std::memcpy(fixed_ptr, default_attrib.AsArray(), sizeof(Common::Vec4f));

    // Find all fixed attributes and assign them to the last binding
    u32 offset = sizeof(Common::Vec4f);
    for (std::size_t i = 0; i < 16; i++) {
        if (vertex_attributes.IsDefaultAttribute(i)) {
            const u32 reg = regs.vs.GetRegisterForAttribute(i);
            if (!enable_attributes[reg]) {
                const auto& attr = pica.input_default_attributes[i];
                const std::array data = {attr.x.ToFloat32(), attr.y.ToFloat32(), attr.z.ToFloat32(),
                                         attr.w.ToFloat32()};

                const u32 data_size = sizeof(float) * static_cast<u32>(data.size());
                std::memcpy(fixed_ptr + offset, data.data(), data_size);

                VertexAttribute& attribute = layout.attributes[reg];
                attribute.binding.Assign(layout.binding_count);
                attribute.location.Assign(reg);
                attribute.offset.Assign(offset);
                attribute.type.Assign(Pica::PipelineRegs::VertexAttributeFormat::FLOAT);
                attribute.size.Assign(4);

                offset += data_size;
                enable_attributes[reg] = true;
            }
        }
    }

    // Loop one more time to find unused attributes and assign them to the default one
    // If the attribute is just disabled, shove the default attribute to avoid
    // errors if the shader ever decides to use it.
    for (u32 i = 0; i < 16; i++) {
        if (!enable_attributes[i]) {
            VertexAttribute& attribute = layout.attributes[i];
            attribute.binding.Assign(layout.binding_count);
            attribute.location.Assign(i);
            attribute.offset.Assign(0);
            attribute.type.Assign(Pica::PipelineRegs::VertexAttributeFormat::FLOAT);
            attribute.size.Assign(4);
        }
    }

    // Define the fixed+default binding
    VertexBinding& binding = layout.bindings[layout.binding_count];
    binding.binding.Assign(layout.binding_count++);
    binding.fixed.Assign(1);
    binding.byte_count.Assign(offset);

    stream_buffer.Commit(offset);
}

bool RasterizerVulkan::SetupVertexShader() {
    MICROPROFILE_SCOPE(Vulkan_VS);
    return pipeline_cache.UseProgrammableVertexShader(regs, pica.vs_setup,
                                                      pipeline_info.state.vertex_layout);
}

bool RasterizerVulkan::SetupGeometryShader() {
    MICROPROFILE_SCOPE(Vulkan_GS);

    if (regs.pipeline.use_gs != Pica::PipelineRegs::UseGS::No) {
        LOG_ERROR(Render_Vulkan, "Accelerate draw doesn't support geometry shader");
        return false;
    }

    // Enable the quaternion fix-up geometry-shader only if we are actually doing per-fragment
    // lighting and care about proper quaternions. Otherwise just use standard vertex+fragment
    // shaders. We also don't need a geometry shader if the barycentric extension is supported,
    // but that will be decided later as the GS config needs to be cached anyways.
    if (regs.lighting.disable) {
        pipeline_cache.UseTrivialGeometryShader();
        return true;
    }

    return pipeline_cache.UseFixedGeometryShader(regs);
}

bool RasterizerVulkan::AccelerateDrawBatch(bool is_indexed) {
    // GammaOS: on the GPU a draw costs the vertex shader's whole run time however few vertices
    // it has (5-7 us for Kirby's long programs on the Adreno 610). Draws of at most this many
    // vertices run their vertex shader on the CPU instead (its JIT takes well under a
    // microsecond for a quad) and reach the GPU with the trivial vertex shader.
    // persist.gammaos.azahar_nano.sw_small_verts = the limit (default 0 = off; 6 = quads).
    static const u32 sw_small_verts = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.sw_small_verts", v);
        return static_cast<u32>(atoi(v));
    }();
    if (regs.pipeline.num_vertices <= sw_small_verts &&
        regs.pipeline.use_gs == Pica::PipelineRegs::UseGS::No) {
        return false;
    }
    if (regs.pipeline.use_gs != Pica::PipelineRegs::UseGS::No) {
        if (regs.pipeline.gs_config.mode != Pica::PipelineRegs::GSMode::Point) {
            return false;
        }
        if (regs.pipeline.triangle_topology != Pica::PipelineRegs::TriangleTopology::Shader) {
            return false;
        }
    }

    pipeline_info.state.rasterization.topology.Assign(regs.pipeline.triangle_topology);
    if (regs.pipeline.triangle_topology == TriangleTopology::Fan &&
        !instance.IsTriangleFanSupported()) {
        LOG_DEBUG(Render_Vulkan,
                  "Skipping accelerated draw with unsupported triangle fan topology");
        return false;
    }

    // Vertex data setup might involve scheduler flushes so perform it
    // early to avoid invalidating our state in the middle of the draw.
    vertex_info = AnalyzeVertexArray(is_indexed, instance.GetMinVertexStrideAlignment());
    if (vertex_info.Invalid()) {
        // Do not draw anything if the vertex array is invalid.
        return true;
    }
    // GammaOS: every stream buffer allocation of this draw (vertices, fixed attributes and the
    // index array, which is allocated after the render pass begins) must fit before the ring
    // wraps: a wrap inside the pass can submit and end it (see StreamBuffer::Reserve).
    stream_buffer.Reserve(vertex_info.vs_input_size + 16 + 17 * sizeof(Common::Vec4f) +
                          (is_indexed ? regs.pipeline.num_vertices * 2 + 2 : 0) + 64);
    {
        SlowStep t{"SetupVertexArray"};
        if (!SetupVertexArray()) {
            return true; // skipped: a buffer outside memory
        }
    }

    bool vs_ok;
    {
        SlowStep t{"SetupVertexShader"};
        vs_ok = SetupVertexShader();
    }
    if (!vs_ok) {
        return false;
    }
    if (!SetupGeometryShader()) {
        return false;
    }

    return Draw(true, is_indexed);
}

bool RasterizerVulkan::AccelerateDrawBatchInternal(bool is_indexed) {
    if (is_indexed && !SetupIndexArray()) {
        return true; // skipped: an index buffer outside memory
    }

    // GammaOS: persist.gammaos.azahar_nano.async_small=1 also skips small (6 vertices or
    // fewer) draws while their pipeline compiles. Off by default: it removes the cold cache
    // freeze (4.7-5.4 s to 0.17 s at first boot) but parts of the scene go missing for a
    // few seconds while new pipelines compile.
    static const bool async_small = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_small", v);
        return v[0] == '1';
    }();
    if (Settings::values.gammaos_hybrid_shaders &&
        (!Settings::values.gammaos_hybrid_uber || pipeline_cache.UberAvailable())) {
        // GammaOS hybrid shaders: never wait for a compile. The build is queued in the
        // background and this draw falls back to CPU vertex processing with the
        // fragment ubershader (see the software branch of Draw).
        bool hw_ready;
        {
            SlowStep t{"BindPipeline hw"};
            hw_ready = pipeline_cache.BindPipeline(pipeline_info, false);
        }
        if (HybridDebugForceFallback() || !hw_ready) {
            if (!Settings::values.gammaos_hybrid_uber) {
                // Skip mode: check the software path pipeline before running the vertices
                // on the CPU. If it is not built either (this also queues its build), the
                // draw is left out now instead of after the CPU vertex work.
                PipelineInfo sw_info = pipeline_info;
                sw_info.state.vertex_layout = software_layout;
                sw_info.state.rasterization.topology.Assign(
                    Pica::PipelineRegs::TriangleTopology::List);
                pipeline_cache.UseTrivialVertexShader();
                pipeline_cache.UseTrivialGeometryShader();
                // Debug 16384: never take the CPU vertex path (skip until the hw pipeline).
                // Per frame budget for the CPU vertex work of fallback draws (about 6 us per
                // vertex here): beyond it the draw is skipped and shows up a frame or two later
                // instead of stretching this frame (a cold cache burst reached 17k vertices).
                static const u32 budget = [] {
                    char v[PROP_VALUE_MAX] = {};
                    __system_property_get("persist.gammaos.azahar_nano.fallback_budget", v);
                    return v[0] ? static_cast<u32>(atoi(v)) : 1500u;
                }();
                const u32 verts = regs.pipeline.num_vertices;
                bool sw_ready = pipeline_cache.BindPipeline(sw_info, false);
                hybrid_baked = false;
                if (!sw_ready) {
                    // A precompiled baked shader (combiner compiled in, the rest dynamic)
                    // can draw this now; its pipeline for a new state builds quickly.
                    u32 variant_key = 0;
                    std::array<u32, 16> pack{};
                    PrepareUberDraw(variant_key, pack, false);
                    std::array<u32, 12> baked{};
                    std::copy_n(pack.begin(), 12, baked.begin());
                    if (pipeline_cache.HasBakedShader(variant_key, baked)) {
                        hybrid_baked = true;
                        sw_ready = true;
                    } else if (HybridDebugBits() & 65536) {
                        // Debug: record misses (variant + masked words) once each.
                        static std::set<std::array<u32, 13>> seen_miss;
                        std::array<u32, 13> rec{};
                        rec[0] = variant_key;
                        const auto masked = PipelineCache::MaskBakedWords(baked);
                        std::copy_n(masked.begin(), 12, rec.begin() + 1);
                        if (seen_miss.insert(rec).second) {
                            std::ofstream f("/data/system/azahar-nano/baked/misses.bin",
                                            std::ios::binary | std::ios::app);
                            f.write(reinterpret_cast<const char*>(rec.data()), sizeof(rec));
                        }
                    }
                }
                bool skip = !sw_ready || (HybridDebugBits() & 16384);
                if (!skip && fallback_vertices_this_frame + verts > budget) {
                    // Over budget: hold this pipeline back. The budget is strict by default:
                    // forcing held draws through after a few frames (fallback_hold=N) let a
                    // cold cache burst stretch frames to 40 fps for ~20 s, while a strict
                    // budget keeps 57-60 with a little more pop-in.
                    static const u32 max_hold = [] {
                        char v[PROP_VALUE_MAX] = {};
                        __system_property_get("persist.gammaos.azahar_nano.fallback_hold", v);
                        const u32 n = v[0] ? static_cast<u32>(atoi(v)) : 0u;
                        return n == 0 ? 0xFFFFFFFFu : n;
                    }();
                    u32& since = budget_held_since[sw_info.Hash()];
                    if (since == 0) {
                        since = frame_index;
                    }
                    skip = frame_index - since < max_hold;
                }
                if (skip) {
                    GammaStats::Add(GammaStats::HybridSkippedDraws);
                    ++skipped_draws_since_present;
                    return true;
                }
                fallback_vertices_this_frame += verts;
            }
            GammaStats::Add(GammaStats::HybridFallbackDraws);
            hybrid_fallback = true;
            return false;
        }
    } else {
        const bool wait_built =
            !async_shaders || (!async_small && regs.pipeline.num_vertices <= 6);
        if (!pipeline_cache.BindPipeline(pipeline_info, wait_built)) {
            return true;
        }
    }

    const DrawParams params = {
        .vertex_count = regs.pipeline.num_vertices,
        .vertex_offset = -static_cast<s32>(vertex_info.vs_input_index_min),
        .binding_count = pipeline_info.state.vertex_layout.binding_count,
        .bindings = binding_offsets,
        .is_indexed = is_indexed,
    };

    scheduler.Record([this, params, q = gamma_dump_query, qpool = GammaDump::d.pool,
                      qprecise = GammaDump::d.precise](vk::CommandBuffer cmdbuf) {
        std::array<vk::DeviceSize, 16> offsets;
        std::transform(params.bindings.begin(), params.bindings.end(), offsets.begin(),
                       [](u32 offset) { return static_cast<vk::DeviceSize>(offset); });
        cmdbuf.bindVertexBuffers(0, params.binding_count, vertex_buffers.data(), offsets.data());
        GammaDump::QueryBegin(cmdbuf, qpool, q, qprecise);
        if (params.is_indexed) {
            cmdbuf.drawIndexed(params.vertex_count, 1, 0, params.vertex_offset, 0);
        } else {
            cmdbuf.draw(params.vertex_count, 1, 0, 0);
        }
        GammaDump::QueryEnd(cmdbuf, qpool, q);
    });

    return true;
}

bool RasterizerVulkan::SetupIndexArray() {
    const bool index_u8 = regs.pipeline.index_array.format == 0;
    const bool native_u8 = index_u8 && instance.IsIndexTypeUint8Supported();
    const u32 index_buffer_size = regs.pipeline.num_vertices * (native_u8 ? 1 : 2);
    const vk::IndexType index_type = native_u8 ? vk::IndexType::eUint8EXT : vk::IndexType::eUint16;

    const PAddr index_addr = regs.pipeline.vertex_attributes.GetPhysicalBaseAddress() +
                             regs.pipeline.index_array.offset;
    const u32 index_bytes = regs.pipeline.num_vertices * (index_u8 ? 1 : 2);
    const u8* index_data = VideoCore::GammaSnapshot::Find(index_addr, index_bytes);
    if (!index_data) {
        // GammaOS: bounded (a game, or a list run after its buffer was reused, can name an
        // index buffer outside mapped memory; the sync path crashed on it too).
        const auto [p, avail] = memory.GetPhysicalSpan(index_addr);
        if (!p || avail < index_bytes) {
            LOG_WARNING(Render_Vulkan, "index buffer {:08X}+{:X} outside memory, draw skipped",
                        index_addr, index_bytes);
            return false;
        }
        index_data = p;
    }

    auto [index_ptr, index_offset, _] = stream_buffer.Map(index_buffer_size, 2);

    if (index_u8 && !native_u8) {
        u16* index_ptr_u16 = reinterpret_cast<u16*>(index_ptr);
        for (u32 i = 0; i < regs.pipeline.num_vertices; i++) {
            index_ptr_u16[i] = index_data[i];
        }
    } else {
        std::memcpy(index_ptr, index_data, index_buffer_size);
    }

    stream_buffer.Commit(index_buffer_size);

    scheduler.Record(
        [this, index_offset = index_offset, index_type = index_type](vk::CommandBuffer cmdbuf) {
            cmdbuf.bindIndexBuffer(stream_buffer.Handle(), index_offset, index_type);
        });
    return true;
}

void RasterizerVulkan::DrawTriangles() {
    if (vertex_batch.empty()) {
        return;
    }

    pipeline_info.state.rasterization.topology.Assign(Pica::PipelineRegs::TriangleTopology::List);
    pipeline_info.state.vertex_layout = software_layout;

    pipeline_cache.UseTrivialVertexShader();
    pipeline_cache.UseTrivialGeometryShader();

    if (!hybrid_fallback) {
        GammaStats::Add(GammaStats::NaturalSwDraws);
        GammaStats::Add(GammaStats::NaturalSwVertices, vertex_batch.size());
    } else {
        GammaStats::Add(GammaStats::FallbackSwVertices, vertex_batch.size());
    }
    Draw(false, false);
}

// GammaOS: fills the fragment ubershader inputs for the current registers (variant key,
// packed combiner stages) and, with upload, its config words in the uniform block.
void RasterizerVulkan::PrepareUberDraw(u32& variant_key, std::array<u32, 16>& tev_pack,
                                       bool upload) {
    Pica::Shader::FSConfig fs_config{regs};
    fs_config.ApplyProfile(pipeline_cache.GetProfile());
    using namespace Pica::Shader::Generator;
    Uber::Variant variant{};
    switch (fs_config.texture.texture0_type.Value()) {
    case Pica::TexturingRegs::TextureConfig::TextureCube:
        variant.tex0 = Uber::Tex0Kind::Cube;
        break;
    case Pica::TexturingRegs::TextureConfig::Shadow2D:
        variant.tex0 = Uber::Tex0Kind::Shadow2D;
        break;
    case Pica::TexturingRegs::TextureConfig::ShadowCube:
        variant.tex0 = Uber::Tex0Kind::ShadowCube;
        break;
    default:
        variant.tex0 = Uber::Tex0Kind::Flat;
        break;
    }
    variant.shadow_rendering = fs_config.framebuffer.shadow_rendering.Value() != 0;
    variant.write_depth = fs_config.framebuffer.vertex_depth.Value() == 0;
    variant.lighting = fs_config.lighting.enable.Value() != 0;
    variant.proctex = fs_config.proctex.enable.Value() != 0;
    static_assert(sizeof(fs_config) == sizeof(fs_data.uber_config));
    std::array<u32, 68> words;
    std::memcpy(words.data(), &fs_config, sizeof(fs_config));
    // Word 36 (wrap modes, unused by the ubershader): stages to evaluate, up to
    // the last one that is not a pass-through (later stages only move buffers
    // that nothing reads any more).
    using TevStage = Pica::TexturingRegs::TevStageConfig;
    u32 stage_count = 0;
    // TevPack: two words per stage for the ubershader's push constants.
    // A: color sources 0-11, alpha sources 12-23, color op 24-27, alpha op 28-31.
    // B: color modifiers 0-11, alpha modifiers 12-20, color scale 21-22, alpha scale
    //    23-24, buffer rgb 25, buffer alpha 26, active 27.
    for (u32 st = 0; st < 6; ++st) {
        const TevStage stage = fs_config.texture.tev_stages[st];
        const bool pass_through =
            stage.color_op == TevStage::Operation::Replace &&
            stage.alpha_op == TevStage::Operation::Replace &&
            stage.color_source1 == TevStage::Source::Previous &&
            stage.alpha_source1 == TevStage::Source::Previous &&
            stage.color_modifier1 == TevStage::ColorModifier::SourceColor &&
            stage.alpha_modifier1 == TevStage::AlphaModifier::SourceAlpha &&
            stage.GetColorMultiplier() == 1 && stage.GetAlphaMultiplier() == 1;
        if (!pass_through) {
            stage_count = st + 1;
        }
        // Stage 0 reads source 3 where a source is "previous" (GetSource force_source3).
        const auto eff = [&](TevStage::Source source, TevStage::Source source3) {
            return static_cast<u32>(
                st == 0 && source == TevStage::Source::Previous ? source3 : source);
        };
        const u32 wa =
            eff(stage.color_source1, stage.color_source3) |
            (eff(stage.color_source2, stage.color_source3) << 4) |
            (eff(stage.color_source3, stage.color_source3) << 8) |
            (eff(stage.alpha_source1, stage.alpha_source3) << 12) |
            (eff(stage.alpha_source2, stage.alpha_source3) << 16) |
            (eff(stage.alpha_source3, stage.alpha_source3) << 20) |
            (static_cast<u32>(stage.color_op.Value()) << 24) |
            (static_cast<u32>(stage.alpha_op.Value()) << 28);
        const u32 wb =
            static_cast<u32>(stage.color_modifier1.Value()) |
            (static_cast<u32>(stage.color_modifier2.Value()) << 4) |
            (static_cast<u32>(stage.color_modifier3.Value()) << 8) |
            (static_cast<u32>(stage.alpha_modifier1.Value()) << 12) |
            (static_cast<u32>(stage.alpha_modifier2.Value()) << 15) |
            (static_cast<u32>(stage.alpha_modifier3.Value()) << 18) |
            (static_cast<u32>(stage.color_scale.Value()) << 21) |
            (static_cast<u32>(stage.alpha_scale.Value()) << 23) |
            ((fs_config.TevStageUpdatesCombinerBufferColor(st) ? 1u : 0u) << 25) |
            ((fs_config.TevStageUpdatesCombinerBufferAlpha(st) ? 1u : 0u) << 26) |
            ((pass_through ? 0u : 1u) << 27);
        tev_pack[2 * st] = wa;
        tev_pack[2 * st + 1] = wb;
    }
    words[36] = stage_count;
    variant_key = variant.Key();
    if (!upload) {
        return;
    }
    if (std::memcmp(&fs_data.uber_config, words.data(), sizeof(words)) != 0) {
        std::memcpy(&fs_data.uber_config, words.data(), sizeof(words));
        fs_data_dirty = true;
    }
    if (upload && fs_data_dirty) {
        UploadUniforms(false);
    }
}

bool RasterizerVulkan::Draw(bool accelerate, bool is_indexed) {
    if (!accelerate && !vertex_batch.empty()) {
        // The software vertices are allocated inside the render pass: wrap before it.
        stream_buffer.Reserve(static_cast<u32>(vertex_batch.size() * sizeof(HardwareVertex)) +
                              sizeof(HardwareVertex));
    }
    // Address AND size: games reuse the right eye target's memory as scratch for small
    // passes (Metroid: 64x128 bloom passes that feed the left eye too).
    const auto& sfb = regs.framebuffer.framebuffer;
    // The height register holds the height minus one.
    const u32 fb_w = sfb.width.Value(), fb_h = sfb.height.Value() + 1;
    if (stereo_right_rt != 0 && sfb.GetColorBufferPhysicalAddress() == stereo_right_rt &&
        ((fb_w == stereo_right_w && fb_h == stereo_right_h) ||
         (fb_w == stereo_right_h && fb_h == stereo_right_w))) {
        GammaStats::Add(GammaStats::CmdListsBlocked); // counted as skipped right eye work
        vertex_batch.clear();
        return true;
    }
    if (eye_phase != EyePhase::Idle) {
        const PAddr rt = regs.framebuffer.framebuffer.GetColorBufferPhysicalAddress();
        if (eye_phase == EyePhase::AfterLeft && rt == eye_rt) {
            eye_draws_after_left = true;
            if (eye_learned && RightEyeSkipAllowed()) {
                eye_phase = EyePhase::Skipping;
            }
        }
        if (eye_phase == EyePhase::Skipping) {
            if (rt == eye_rt) {
                GammaStats::Add(GammaStats::CmdListsBlocked);   // counted as skipped work
                return true;
            }
            // A draw into another target while skipping: not a plain right-eye
            // batch. Stop skipping (the transfer check relearns).
            eye_phase = EyePhase::Idle;
            eye_learned = false;
            LOG_WARNING(Render_Vulkan, "right-eye skip: draw into {:08X} while skipping", rt);
        }
    }
    GammaStats::Add(GammaStats::Draws);
    {
        const auto& r = regs;
        if (r.framebuffer.output_merger.fragment_operation_mode ==
            Pica::FramebufferRegs::FragmentOperationMode::Shadow) {
            GammaStats::Add(GammaStats::DrawsShadow);
        } else if (r.rasterizer.depthmap_enable ==
                   Pica::RasterizerRegs::DepthBuffering::WBuffering) {
            GammaStats::Add(GammaStats::DrawsWBuffer);
        } else {
            GammaStats::Add(GammaStats::DrawsZBuffer);
        }
        if (r.framebuffer.output_merger.alpha_test.enable) GammaStats::Add(GammaStats::DrawsAlphaTest);
        if (r.framebuffer.output_merger.alphablend_enable) GammaStats::Add(GammaStats::DrawsBlend);
    }
    MICROPROFILE_SCOPE(Vulkan_Drawing);
    SyncDrawState();

    const bool shadow_rendering = regs.framebuffer.IsShadowRendering();
    // GammaOS approximation (no_shadow_map=1, per game): the shadow map passes are not drawn
    // at all; the fragment shaders read the map as unshadowed (Profile::no_shadow). Latched
    // at startup like the other generator switches.
    static const bool no_shadow_map = [] {
        char v[PROP_VALUE_MAX] = {};
        GammaGameProp::Get("no_shadow_map", v);
        return v[0] == '1';
    }();
    if (shadow_rendering && no_shadow_map) {
        return true;
    }
    const bool has_stencil = regs.framebuffer.HasStencil();

    // A colour write mask of 0 that comes from the logic op NoOp emulation (drivers without
    // logicOp, malisx2 on the Mali-G52) keeps the colour attachment bound: the pipeline writes
    // nothing to it, and the draw stays in the open pass instead of forcing a switch to the
    // depth-only framebuffer and back (3D Land: 7 pass breaks per frame against 3). Drivers
    // with logicOp return the raw mask here, so nothing changes for them.
    const bool write_color_fb = shadow_rendering || pipeline_info.GetFinalColorWriteMask(instance) ||
                                pipeline_info.state.blending.color_write_mask != 0;
    const bool write_depth_fb = pipeline_info.IsDepthWriteEnabled();
    const bool using_color_fb =
        regs.framebuffer.framebuffer.GetColorBufferPhysicalAddress() != 0 && write_color_fb;
    const bool using_depth_fb =
        !shadow_rendering && regs.framebuffer.framebuffer.GetDepthBufferPhysicalAddress() != 0 &&
        (write_depth_fb || regs.framebuffer.output_merger.depth_test_enable != 0 ||
         (has_stencil && pipeline_info.state.depth_stencil.stencil_test_enable));

    SlowStep t_fb{"DrawAfterFramebuffer"};
    const auto fb_helper = res_cache.GetFramebufferSurfaces(using_color_fb, using_depth_fb);
    const Framebuffer* framebuffer = fb_helper.Framebuffer();
    if (!framebuffer->Handle()) {
        return true;
    }

    pipeline_info.state.attachments.color = framebuffer->Format(SurfaceType::Color);
    pipeline_info.state.attachments.depth = framebuffer->Format(SurfaceType::Depth);

    // Update scissor uniforms
    const auto [scissor_x1, scissor_y2, scissor_x2, scissor_y1] = fb_helper.Scissor();
    if (fs_data.scissor_x1 != scissor_x1 || fs_data.scissor_x2 != scissor_x2 ||
        fs_data.scissor_y1 != scissor_y1 || fs_data.scissor_y2 != scissor_y2) {

        fs_data.scissor_x1 = scissor_x1;
        fs_data.scissor_x2 = scissor_x2;
        fs_data.scissor_y1 = scissor_y1;
        fs_data.scissor_y2 = scissor_y2;
        fs_data_dirty = true;
    }

    // Sync and bind the texture surfaces
    {
        SlowStep t{"SyncTextures"};
        SyncTextureUnits(framebuffer);
        SyncUtilityTextures(framebuffer);
    }

    // Sync and bind the shader
    {
        SlowStep t{"UseFragmentShader"};
        pipeline_cache.UseFragmentShader(regs, user_config);
    }

    // Sync the LUTs within the texture buffer
    SyncAndUploadLUTs();
    SyncAndUploadLUTsLF();
    UploadUniforms(accelerate);

    // Begin rendering
    const auto draw_rect = fb_helper.DrawRect();
    renderpass_cache.BeginRendering(framebuffer, draw_rect);
    gamma_dump_query = -1;
    if (GammaDump::d.active) {
        const auto tex = regs.texturing.main_config;
        gamma_dump_query = GammaDump::BeginDraw(
            scheduler,
            GammaDump::DrawInfo{
                .rect_w = draw_rect.GetWidth(),
                .rect_h = draw_rect.GetHeight(),
                .vertices = regs.pipeline.num_vertices,
                .tex_mask = (tex.texture0_enable ? 1u : 0u) | (tex.texture1_enable ? 2u : 0u) |
                            (tex.texture2_enable ? 4u : 0u),
                .fog = static_cast<u32>(regs.texturing.fog_mode.Value()),
                .lighting = regs.lighting.disable ? 0u : 1u,
                .blend = regs.framebuffer.output_merger.alphablend_enable ? 1u : 0u,
                .alpha_test = regs.framebuffer.output_merger.alpha_test.enable ? 1u : 0u,
                .depth_test = regs.framebuffer.output_merger.depth_test_enable ? 1u : 0u,
                .tex0_fmt = static_cast<u32>(regs.texturing.texture0_format.Value()),
                .tex0_w = regs.texturing.texture0.width,
                .tex0_h = regs.texturing.texture0.height,
                .tex0_mag = static_cast<u32>(regs.texturing.texture0.mag_filter.Value()),
                .tex0_min = static_cast<u32>(regs.texturing.texture0.min_filter.Value()),
                .tex0_addr = regs.texturing.texture0.GetPhysicalAddress(),
                .color_addr = regs.framebuffer.framebuffer.GetColorBufferPhysicalAddress(),
                .lights = regs.lighting.disable ? 0u : regs.lighting.max_light_index + 1u,
                // bits: d0 d1 fr rr rg rb (1 = table in use)
                .luts = (regs.lighting.config1.disable_lut_d0 ? 0u : 1u) |
                        (regs.lighting.config1.disable_lut_d1 ? 0u : 2u) |
                        (regs.lighting.config1.disable_lut_fr ? 0u : 4u) |
                        (regs.lighting.config1.disable_lut_rr ? 0u : 8u) |
                        (regs.lighting.config1.disable_lut_rg ? 0u : 16u) |
                        (regs.lighting.config1.disable_lut_rb ? 0u : 32u),
                .dist_atten_mask = 0xFFu & ~static_cast<u32>(regs.lighting.config1.disable_dist_atten),
                .spot_atten_mask = 0xFFu & ~static_cast<u32>(regs.lighting.config1.disable_spot_atten),
                .indexed = is_indexed ? 1u : 0u,
                .topology = static_cast<u32>(regs.pipeline.triangle_topology.Value()),
                .index_range = is_indexed ? vertex_info.vs_input_index_max - vertex_info.vs_input_index_min + 1 : 0u,
                .fs_hash = GammaDump::d.last_fs_hash,
            });
    }
    SCOPE_EXIT({ gamma_dump_query = -1; });

    // Configure viewport and scissor
    const auto viewport = fb_helper.Viewport();
    pipeline_info.dynamic_info.viewport = Common::Rectangle<s32>{
        viewport.x,
        viewport.y,
        viewport.x + viewport.width,
        viewport.y + viewport.height,
    };
    pipeline_info.dynamic_info.scissor = draw_rect;
    // GammaOS measurement (persist.gammaos.azahar_nano.scissor_test=1, picture WRONG): clip
    // every draw to one pixel, leaving the vertex and per-draw GPU cost of the frame.
    static const bool scissor_test = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.scissor_test", v);
        return v[0] == '1';
    }();
    if (scissor_test) {
        pipeline_info.dynamic_info.scissor.right = pipeline_info.dynamic_info.scissor.left + 1;
        pipeline_info.dynamic_info.scissor.top = pipeline_info.dynamic_info.scissor.bottom + 1;
    }
    {
        // Debug: persist.gammaos.azahar_nano.fb_log=1 logs render target geometry changes.
        static const bool fblog = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.fb_log", v);
            return v[0] == '1';
        }();
        static std::string fblast;
        static u32 fblogged = 0;
        if (fblog && fblogged < 300) {
            const auto& fbc = regs.framebuffer.framebuffer;
            const auto vr = regs.rasterizer.GetViewportRect();
            const auto sc = fb_helper.Scissor();
            const std::string line = fmt::format(
                "fb {:08X} {}x{} flip {} vp l{} t{} r{} b{} -> view x{} y{} w{} h{} draw l{} t{} "
                "r{} b{} sc l{} t{} r{} b{} scmode {}",
                fbc.GetColorBufferPhysicalAddress(), fbc.GetWidth(), fbc.GetHeight(),
                fbc.IsFlipped(), vr.left, vr.top, vr.right, vr.bottom, viewport.x, viewport.y,
                viewport.width, viewport.height, draw_rect.left, draw_rect.top, draw_rect.right,
                draw_rect.bottom, sc.left, sc.top, sc.right, sc.bottom,
                static_cast<u32>(regs.rasterizer.scissor_test.mode.Value()));
            if (line != fblast) {
                fblast = line;
                ++fblogged;
                LOG_INFO(Render_Vulkan, "{}", line);
            }
        }
    }
    // GammaOS hardware scissor: the fragment shader no longer discards for a
    // "keep inside" scissor, so the same rectangle goes to the hardware scissor
    // (shader rule: pixels with x1 <= x < x2 and y1 <= y < y2 are kept).
    if (Settings::values.gammaos_hw_scissor &&
        regs.rasterizer.scissor_test.mode == Pica::RasterizerRegs::ScissorMode::Include) {
        const auto sc = fb_helper.Scissor();   // left = x1, top = y2, right = x2, bottom = y1
        auto& r = pipeline_info.dynamic_info.scissor;
        const u32 l = std::max<s32>(sc.left, 0), b = std::max<s32>(sc.bottom, 0);
        const u32 rr = std::max<s32>(sc.right, 0), t = std::max<s32>(sc.top, 0);
        r.left = std::max(r.left, l);
        r.bottom = std::max(r.bottom, b);
        r.right = std::max(r.left, std::min(r.right, rr));
        r.top = std::max(r.bottom, std::min(r.top, t));
    }

    // Draw the vertex batch
    bool succeeded = true;
    if (accelerate) {
        succeeded = AccelerateDrawBatchInternal(is_indexed);
    } else {
        if (!Settings::values.gammaos_hybrid_shaders) {
            pipeline_cache.BindPipeline(pipeline_info, true);
        } else if (!Settings::values.gammaos_hybrid_uber) {
            // Skip mode: draw once the fast software path pipeline is built (it builds in
            // the background, about 55 ms); until then this draw is left out.
            if (!pipeline_cache.BindPipeline(pipeline_info, false)) {
                if (hybrid_baked) {
                    u32 variant_key = 0;
                    std::array<u32, 16> pack{};
                    PrepareUberDraw(variant_key, pack, true);
                    std::array<u32, 12> baked{};
                    std::copy_n(pack.begin(), 12, baked.begin());
                    pipeline_cache.UseBakedFragmentShader(variant_key, baked);
                    pipeline_cache.PushUberTev(pack);
                    pipeline_cache.BindPipeline(pipeline_info, true);
                    GammaStats::Add(GammaStats::HybridUberDraws);
                } else {
                    GammaStats::Add(GammaStats::HybridSkippedDraws);
                    ++skipped_draws_since_present;
                    hybrid_fallback = false;
                    vertex_batch.clear();
                    return true;
                }
            }
        } else if (!pipeline_cache.UberAvailable()) {
            pipeline_cache.BindPipeline(pipeline_info, true);
        } else if (HybridDebugSwSpecialized()) {
            // Debug: software vertex path with the generated fragment shader (no ubershader).
            pipeline_cache.BindPipeline(pipeline_info, true);
        } else if (HybridDebugForceUber() || !pipeline_cache.BindPipeline(pipeline_info, false)) {
            // Fragment ubershader: its pipelines depend only on fixed-function state.
            u32 variant_key = 0;
            std::array<u32, 16> tev_pack{};
            PrepareUberDraw(variant_key, tev_pack, true);
            using namespace Pica::Shader::Generator;
            Uber::Variant variant = Uber::Variant::FromKey(variant_key);
            if (HybridDebugBits() & 32768) {
                // Experimental: combiner stages compiled into the shader.
                std::array<u32, 12> baked{};
                std::copy_n(tev_pack.begin(), 12, baked.begin());
                pipeline_cache.UseBakedFragmentShader(variant.Key(), baked);
            } else {
                pipeline_cache.UseUberFragmentShader(variant.Key());
            }
            pipeline_cache.PushUberTev(tev_pack);
            const bool ready = pipeline_cache.BindPipeline(pipeline_info, false);
            if (!ready) {
                // A fixed-function state no precompiled ubershader pipeline covers yet.
                GammaStats::Add(GammaStats::HybridUberCompiles);
                pipeline_cache.BindPipeline(pipeline_info, true);
            }
            GammaStats::Add(GammaStats::HybridUberDraws);
        }
        hybrid_fallback = false;
        hybrid_baked = false;

        const u32 vertex_count = static_cast<u32>(vertex_batch.size());
        const u32 vertex_size = vertex_count * sizeof(HardwareVertex);
        const auto [buffer, offset, _] = stream_buffer.Map(vertex_size, sizeof(HardwareVertex));

        std::memcpy(buffer, vertex_batch.data(), vertex_size);
        stream_buffer.Commit(vertex_size);

        scheduler.Record([this, offset = offset, vertex_count, q = gamma_dump_query,
                          qpool = GammaDump::d.pool,
                          qprecise = GammaDump::d.precise](vk::CommandBuffer cmdbuf) {
            cmdbuf.bindVertexBuffers(0, stream_buffer.Handle(), offset);
            GammaDump::QueryBegin(cmdbuf, qpool, q, qprecise);
            cmdbuf.draw(vertex_count, 1, 0, 0);
            GammaDump::QueryEnd(cmdbuf, qpool, q);
        });
        gamma_dump_query = -1;   // one query per draw call, the first batch
    }

    vertex_batch.clear();
    return succeeded;
}

void RasterizerVulkan::SyncTextureUnits(const Framebuffer* framebuffer) {
    using TextureType = Pica::TexturingRegs::TextureConfig::TextureType;

    // GammaOS: the texture unit registers, main_config through texture2_format.
    const u8* const tex_regs_lo = reinterpret_cast<const u8*>(&regs.texturing.main_config);
    const u8* const tex_regs_hi =
        reinterpret_cast<const u8*>(&regs.texturing.texture2_format) + sizeof(u32);
    const std::size_t tex_regs_size = static_cast<std::size_t>(tex_regs_hi - tex_regs_lo);
    ASSERT(tex_regs_size <= sizeof(tex_memo.regs));
    const vk::ImageView color_view = framebuffer->ImageView(SurfaceType::Color);
    const u32 sampling = static_cast<u32>(Settings::values.texture_sampling.GetValue());
    const u64 tick = scheduler.CurrentTick();
    // The set may only be bound again while the command buffer it was committed to records:
    // the pool hands it out again once that buffer has finished.
    if (BindMemoEnabled() && tex_memo.valid && tex_memo.tick == tick && tex_memo.epoch == res_cache.Epoch() &&
        tex_memo.color_view == color_view && tex_memo.sampling == sampling &&
        pipeline_cache.BoundSet(DescriptorHeapType::Texture) == tex_memo.set &&
        std::memcmp(tex_memo.regs.data(), tex_regs_lo, tex_regs_size) == 0) {
        GammaStats::Add(GammaStats::TextureBindsReused);
        return;
    }

    const auto pica_textures = regs.texturing.GetTextures();
    const bool use_cube_heap =
        pica_textures[0].enabled && pica_textures[0].config.type == TextureType::ShadowCube;
    const auto texture_set = pipeline_cache.Acquire(use_cube_heap ? DescriptorHeapType::Texture
                                                                  : DescriptorHeapType::Texture);
    // Only plain 2D bindings repeat exactly: shadow, cube and feedback bindings record copies or
    // flags per draw.
    bool memo_ok = true;
    SCOPE_EXIT({
        tex_memo.valid = memo_ok;
        if (memo_ok) {
            std::memcpy(tex_memo.regs.data(), tex_regs_lo, tex_regs_size);
            tex_memo.epoch = res_cache.Epoch();
            tex_memo.tick = tick;
            tex_memo.color_view = color_view;
            tex_memo.sampling = sampling;
            tex_memo.set = texture_set;
        }
    });

    for (u32 texture_index = 0; texture_index < pica_textures.size(); ++texture_index) {
        const auto& texture = pica_textures[texture_index];

        // If the texture unit is disabled bind a null surface to it
        if (!texture.enabled) {
            switch (texture.config.type.Value()) {
            case TextureType::TextureCube:
            case TextureType::ShadowCube: {
                Surface& null_surface = res_cache.GetSurface(VideoCore::NULL_SURFACE_CUBE_ID);
                const Sampler& null_sampler = res_cache.GetSampler(VideoCore::NULL_SURFACE_CUBE_ID);
                update_queue.AddImageSampler(texture_set, texture_index, 0,
                                             null_surface.ImageView(), null_sampler.Handle());
                break;
            }
            default: {
                Surface& null_surface = res_cache.GetSurface(VideoCore::NULL_SURFACE_ID);
                const Sampler& null_sampler = res_cache.GetSampler(VideoCore::NULL_SURFACE_ID);
                update_queue.AddImageSampler(texture_set, texture_index, 0,
                                             null_surface.ImageView(), null_sampler.Handle());
                break;
            }
            }
            continue;
        }

        // Handle special tex0 configurations
        if (texture_index == 0) {
            switch (texture.config.type.Value()) {
            case TextureType::Shadow2D: {
                memo_ok = false;
                Surface& surface = res_cache.GetStorageTextureSurface(texture);
                Sampler& sampler = res_cache.GetSampler(texture.config);
                surface.flags |= VideoCore::SurfaceFlagBits::ShadowSource;
                update_queue.AddImageSampler(texture_set, texture_index, 0, surface.StorageView(),
                                             sampler.Handle());
                continue;
            }
            case TextureType::ShadowCube: {
                memo_ok = false;
                BindShadowCube(texture, texture_set);
                continue;
            }
            case TextureType::TextureCube: {
                memo_ok = false;
                BindTextureCube(texture, texture_set);
                continue;
            }
            default:
                break;
            }
        }

        // Bind the texture provided by the rasterizer cache
        Surface& surface = res_cache.GetTextureSurface(texture);
        Sampler& sampler = res_cache.GetSampler(texture.config);
        const bool is_feedback_loop = color_view == surface.FramebufferView();
        memo_ok &= !is_feedback_loop;
        const vk::ImageView texture_view =
            is_feedback_loop ? surface.CopyImageView() : surface.ImageView();
        update_queue.AddImageSampler(texture_set, texture_index, 0, texture_view, sampler.Handle());
    }
}

void RasterizerVulkan::SyncUtilityTextures(const Framebuffer* framebuffer) {
    const bool shadow_writing = regs.framebuffer.IsShadowRendering();
    bool shadow_reading = regs.lighting.config0.enable_shadow;
    // Ensure the shadow-texture slot is actually enabled
    if (shadow_reading) {
        const u32 shadow_texture_unit = regs.lighting.config0.shadow_selector.Value();
        const auto shadow_texture = regs.texturing.GetTextures()[shadow_texture_unit];
        shadow_reading &= shadow_texture.enabled;
    }

    // GammaOS: reuse the previous draw's set when it binds the same image (see tex_memo).
    const vk::ImageView color_view = framebuffer->ImageView(SurfaceType::Color);
    const u32 mode = shadow_writing ? 1u
                     : shadow_reading
                         ? 2u + regs.lighting.config0.shadow_selector.Value()
                         : 0u;
    const u64 tick = scheduler.CurrentTick();
    if (BindMemoEnabled() && util_memo.tick == tick && util_memo.mode == mode &&
        pipeline_cache.BoundSet(DescriptorHeapType::Utility) == util_memo.set &&
        (mode == 0 || (mode == 1 && util_memo.color_view == color_view))) {
        return;
    }

    const auto utility_set = pipeline_cache.Acquire(DescriptorHeapType::Utility);
    // Shadow reads go through a lookup of the texture unit each draw; only the fixed bindings
    // are kept.
    util_memo = UtilityBindMemo{
        .epoch = res_cache.Epoch(),
        .tick = mode < 2 ? tick : ~0ull,
        .color_view = color_view,
        .set = utility_set,
        .mode = mode,
    };

    // Reading and writing are mutually exclusive
    assert(!(shadow_writing && shadow_reading));

    if (shadow_writing) {
        update_queue.AddStorageImage(utility_set, 0, framebuffer->ImageView(SurfaceType::Color));
    } else if (shadow_reading) {
        const u32 shadow_texture_unit = regs.lighting.config0.shadow_selector.Value();
        const auto shadow_texture = regs.texturing.GetTextures()[shadow_texture_unit];
        Surface& shadow_surface = res_cache.GetStorageTextureSurface(shadow_texture);
        update_queue.AddStorageImage(utility_set, 0, shadow_surface.StorageView());
    } else {
        Surface& null_surface = res_cache.GetSurface(VideoCore::NULL_SURFACE_ID);
        update_queue.AddStorageImage(utility_set, 0, null_surface.StorageView());
    }
}

void RasterizerVulkan::BindShadowCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                                      vk::DescriptorSet texture_set) {
    using CubeFace = Pica::TexturingRegs::CubeFace;
    auto info = Pica::Texture::TextureInfo::FromPicaRegister(texture.config, texture.format);
    constexpr std::array faces = {
        CubeFace::PositiveX, CubeFace::NegativeX, CubeFace::PositiveY,
        CubeFace::NegativeY, CubeFace::PositiveZ, CubeFace::NegativeZ,
    };

    Sampler& sampler = res_cache.GetSampler(texture.config);

    for (CubeFace face : faces) {
        const u32 binding = static_cast<u32>(face);
        info.physical_address = regs.texturing.GetCubePhysicalAddress(face);

        const VideoCore::SurfaceId surface_id = res_cache.GetTextureSurface(info);
        res_cache.EnsureStorage(surface_id);
        Surface& surface = res_cache.GetSurface(surface_id);
        surface.flags |= VideoCore::SurfaceFlagBits::ShadowSource;
        update_queue.AddImageSampler(texture_set, 0, binding, surface.StorageView(),
                                     sampler.Handle());
    }
}

void RasterizerVulkan::BindTextureCube(const Pica::TexturingRegs::FullTextureConfig& texture,
                                       vk::DescriptorSet texture_set) {
    using CubeFace = Pica::TexturingRegs::CubeFace;
    const VideoCore::TextureCubeConfig config = {
        .px = regs.texturing.GetCubePhysicalAddress(CubeFace::PositiveX),
        .nx = regs.texturing.GetCubePhysicalAddress(CubeFace::NegativeX),
        .py = regs.texturing.GetCubePhysicalAddress(CubeFace::PositiveY),
        .ny = regs.texturing.GetCubePhysicalAddress(CubeFace::NegativeY),
        .pz = regs.texturing.GetCubePhysicalAddress(CubeFace::PositiveZ),
        .nz = regs.texturing.GetCubePhysicalAddress(CubeFace::NegativeZ),
        .width = texture.config.width,
        .levels = texture.config.lod.max_level + 1,
        .format = texture.format,
    };

    Surface& surface = res_cache.GetTextureCube(config);
    Sampler& sampler = res_cache.GetSampler(texture.config);
    update_queue.AddImageSampler(texture_set, 0, 0, surface.ImageView(), sampler.Handle());
}

void RasterizerVulkan::FlushAll() {
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    res_cache.FlushAll();
}

void RasterizerVulkan::FlushRegion(PAddr addr, u32 size) {
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    res_cache.MirrorAccess(addr, size, false);
    res_cache.FlushRegion(addr, size);
}

void RasterizerVulkan::InvalidateRegion(PAddr addr, u32 size) {
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    res_cache.MirrorAccess(addr, size, true);
    res_cache.InvalidateRegion(addr, size);
}

void RasterizerVulkan::FlushAndInvalidateRegion(PAddr addr, u32 size) {
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    res_cache.MirrorAccess(addr, size, true);
    res_cache.FlushRegion(addr, size);
    res_cache.InvalidateRegion(addr, size);
}

bool RasterizerVulkan::TwinTargets(PAddr cur_color, PAddr cur_depth, PAddr next_color,
                                   PAddr next_depth, u32 color_size, u32 depth_size,
                                   std::span<const PAddr> textures, PAddr& canon_color,
                                   PAddr& canon_depth) {
    return res_cache.TwinTargets(cur_color, cur_depth, next_color, next_depth, color_size,
                                 depth_size, textures, canon_color, canon_depth);
}

void RasterizerVulkan::SetTwinDraw(bool active) {
    res_cache.SetTwinDraw(active);
}

void RasterizerVulkan::ResetForLoad() {
    VideoCore::AsyncGpuDrain();
    res_cache.ResetForLoad();
}

void RasterizerVulkan::ClearAll(bool flush) {
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    res_cache.ClearAll(flush);
}

bool RasterizerVulkan::RightEyeSkipAllowed() const {
    return Settings::values.disable_right_eye_render.GetValue() &&
           Settings::values.render_3d.GetValue() == Settings::StereoRenderOption::Off &&
           Settings::values.mono_render_option.GetValue() == Settings::MonoRenderOption::LeftEye;
}

bool RasterizerVulkan::OnDisplayTransferEye(const Pica::DisplayTransferConfig& config) {
    if (!RightEyeSkipAllowed()) {
        eye_phase = EyePhase::Idle;
        eye_learned = false;
        stereo_right_rt = 0;
        return true;
    }
    {
        // Debug: persist.gammaos.azahar_nano.transfer_log=1 logs every display transfer.
        static const bool tlog = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.transfer_log", v);
            return v[0] == '1';
        }();
        static u32 logged = 0;
        if (tlog && logged < 400) {
            ++logged;
            const auto& lfb = pica.regs.framebuffer_config[0];
            LOG_INFO(Render_Vulkan, "transfer {:08X} -> {:08X} ({}x{}) | top L1 {:08X} L2 {:08X} R1 {:08X} R2 {:08X} active {}",
                     config.GetPhysicalInputAddress(), config.GetPhysicalOutputAddress(),
                     config.output_width.Value(), config.output_height.Value(), lfb.address_left1,
                     lfb.address_left2, lfb.address_right1, lfb.address_right2, static_cast<u32>(lfb.active_fb));
        }
    }
    static const bool stereo_skip = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.stereo_skip", v);
        return v[0] == '1';
    }();
    if (stereo_skip) {
        // GammaOS: games that draw both eyes interleaved draw by draw (Metroid: Samus
        // Returns) render the right eye into a surface they later transfer into the right
        // eye framebuffer. Learn that surface and leave its draws out (see Draw), as the
        // right eye image is never shown with 3D off.
        const auto& tfb = pica.regs.framebuffer_config[0];
        const PAddr t_out = config.GetPhysicalOutputAddress();
        const PAddr t_in = config.GetPhysicalInputAddress();
        const bool to_left = t_out == tfb.address_left1 || t_out == tfb.address_left2;
        if (to_left) {
            // A surface that feeds a displayed (left) framebuffer is never a right eye
            // target: games with 3D off often copy the left image to both framebuffers.
            last_left_src = t_in;
            if (stereo_right_rt == t_in) {
                LOG_INFO(Render_Vulkan, "stereo right eye target {:08X} feeds the left eye, dropped",
                         stereo_right_rt);
                stereo_right_rt = 0;
            }
        }
        if (tfb.address_right1 != 0 && !to_left && t_in != last_left_src &&
            (t_out == tfb.address_right1 || t_out == tfb.address_right2)) {
            if (stereo_right_rt != t_in) {
                LOG_INFO(Render_Vulkan, "stereo right eye target learned: {:08X}", t_in);
            }
            stereo_right_rt = t_in;
            stereo_right_w = config.input_width.Value();
            stereo_right_h = config.input_height.Value();
            GammaStats::Add(GammaStats::TransfersBlocked);
            return false; // the right eye image is never displayed
        }
        if (stereo_right_rt != 0 && t_in == stereo_right_rt && !to_left) {
            // That surface feeds something else: it is not a right eye target after all.
            LOG_INFO(Render_Vulkan, "stereo right eye target {:08X} used elsewhere, dropped",
                     stereo_right_rt);
            stereo_right_rt = 0;
        }
    }
    // Games rotate through more framebuffers than the two pairs in the config
    // (Super Mario 3D Land transfers into a third one), but every right-eye
    // buffer sits at the same offset from its left-eye partner.
    const auto& fb = pica.regs.framebuffer_config[0];
    const s64 d1 = static_cast<s64>(fb.address_right1) - static_cast<s64>(fb.address_left1);
    const s64 d2 = static_cast<s64>(fb.address_right2) - static_cast<s64>(fb.address_left2);
    if (fb.address_right1 == 0 || d1 == 0 || d1 != d2) {
        eye_phase = EyePhase::Idle;
        return true;
    }
    const PAddr out = config.GetPhysicalOutputAddress();
    const PAddr in = config.GetPhysicalInputAddress();
    const bool is_top = config.output_width.Value() == 240 && config.output_height.Value() == 400;
    const bool pair_of_left = is_top && in == eye_rt &&
                              static_cast<s64>(out) == static_cast<s64>(eye_left_out) + d1;
    if (eye_phase == EyePhase::Skipping) {
        eye_phase = EyePhase::Idle;
        if (pair_of_left) {
            GammaStats::Add(GammaStats::TransfersBlocked);
            return false;   // the right-eye image is never shown
        }
        // The skipped draws were not the right eye after all: stop predicting.
        eye_learned = false;
        LOG_WARNING(Render_Vulkan, "right-eye skip: pattern broke (transfer to {:08X}), relearning",
                    out);
        return true;
    }
    if (eye_phase == EyePhase::AfterLeft && pair_of_left) {
        if (eye_draws_after_left && !eye_learned) {
            eye_learned = true;
            LOG_INFO(Render_Vulkan, "right-eye skip: pattern learned (rt {:08X}, eye offset {:X})",
                     eye_rt, d1);
        }
        eye_phase = EyePhase::Idle;
        return true;
    }
    if (is_top) {
        eye_phase = EyePhase::AfterLeft;
        eye_rt = in;
        eye_left_out = out;
        eye_draws_after_left = false;
    } else {
        eye_phase = EyePhase::Idle;
    }
    return true;
}

bool RasterizerVulkan::AccelerateDisplayTransfer(const Pica::DisplayTransferConfig& config) {
    if (!OnDisplayTransferEye(config)) {
        return true;
    }
    if (GammaDump::d.active) {
        const auto& tfb = pica.regs.framebuffer_config[0];
        GammaDump::Note(fmt::format("top fb config: active {} left1 {:08X} left2 {:08X} right1 {:08X} "
                                    "right2 {:08X}", tfb.active_fb, tfb.address_left1,
                                    tfb.address_left2, tfb.address_right1, tfb.address_right2));
        GammaDump::Note(fmt::format("display transfer {:08X} ({}x{}) -> {:08X} ({}x{})",
                                    config.GetPhysicalInputAddress(), config.input_width.Value(),
                                    config.input_height.Value(), config.GetPhysicalOutputAddress(),
                                    config.output_width.Value(), config.output_height.Value()));
    }
    return res_cache.AccelerateDisplayTransfer(config);
}

bool RasterizerVulkan::AccelerateTextureCopy(const Pica::DisplayTransferConfig& config) {
    if (GammaDump::d.active) {
        GammaDump::Note(fmt::format("texture copy {:08X} ({}x{}) -> {:08X} ({}x{})",
                                    config.GetPhysicalInputAddress(), config.input_width.Value(),
                                    config.input_height.Value(), config.GetPhysicalOutputAddress(),
                                    config.output_width.Value(), config.output_height.Value()));
    }
    return res_cache.AccelerateTextureCopy(config);
}

bool RasterizerVulkan::AccelerateFill(const Pica::MemoryFillConfig& config) {
    if (GammaDump::d.active) {
        GammaDump::Note(fmt::format("fill {:08X}..{:08X}", config.GetStartAddress(),
                                    config.GetEndAddress()));
    }
    return res_cache.AccelerateFill(config);
}

bool RasterizerVulkan::AccelerateDisplay(const Pica::FramebufferConfig& config,
                                         PAddr framebuffer_addr, u32 pixel_stride,
                                         ScreenInfo& screen_info) {
    if (framebuffer_addr == 0) [[unlikely]] {
        return false;
    }
    res_cache.MirrorAccess(framebuffer_addr, config.stride * config.height, false);

    VideoCore::SurfaceParams src_params;
    src_params.addr = framebuffer_addr;
    src_params.width = std::min(config.width.Value(), pixel_stride);
    src_params.height = config.height;
    src_params.stride = pixel_stride;
    src_params.is_tiled = false;
    src_params.pixel_format = VideoCore::PixelFormatFromGPUPixelFormat(config.color_format);
    src_params.UpdateParams();

    const auto [src_surface_id, src_rect] =
        res_cache.GetSurfaceSubRect(src_params, VideoCore::ScaleMatch::Ignore, true);

    if (!src_surface_id) {
        return false;
    }

    Surface& src_surface = res_cache.GetSurface(src_surface_id);
    const u32 scaled_width = src_surface.GetScaledWidth();
    const u32 scaled_height = src_surface.GetScaledHeight();

    screen_info.texcoords = Common::Rectangle<f32>(
        (float)src_rect.bottom / (float)scaled_height, (float)src_rect.left / (float)scaled_width,
        (float)src_rect.top / (float)scaled_height, (float)src_rect.right / (float)scaled_width);

    screen_info.image_view = src_surface.ImageView();
    screen_info.view_width = scaled_width;
    screen_info.view_height = scaled_height;

    // Debug: persist.gammaos.azahar_nano.display_log=1 logs what each screen samples.
    static const bool dlog = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.display_log", v);
        return v[0] == '1';
    }();
    static u64 dlast[2] = {};
    const u64 dkey = (u64(framebuffer_addr) << 32) ^ (u64(pixel_stride) << 16) ^
                     (u64(config.height.Value()) << 4) ^ u64(config.color_format.Value()) ^
                     (u64(src_surface.addr) << 8);
    const u32 dscreen = config.height.Value() == 320 ? 1 : 0;
    if (dlog && dlast[dscreen] != dkey) {
        dlast[dscreen] = dkey;
        LOG_INFO(Render_Vulkan,
                 "display {:08X} {}x{} stride {} fmt {} -> surface {:08X}-{:08X} {}x{} stride {} "
                 "tiled {} scale {} rect l{} t{} r{} b{}",
                 framebuffer_addr, config.width.Value(), config.height.Value(), pixel_stride,
                 static_cast<u32>(config.color_format.Value()), src_surface.addr, src_surface.end,
                 src_surface.width, src_surface.height, src_surface.stride, src_surface.is_tiled,
                 src_surface.res_scale, src_rect.left, src_rect.top, src_rect.right,
                 src_rect.bottom);
    }

    return true;
}

void RasterizerVulkan::MakeSoftwareVertexLayout() {
    constexpr std::array sizes = {4, 4, 2, 2, 2, 1, 4, 3};

    software_layout = VertexLayout{
        .binding_count = 1,
        .attribute_count = 8,
    };

    for (u32 i = 0; i < software_layout.binding_count; i++) {
        VertexBinding& binding = software_layout.bindings[i];
        binding.binding.Assign(i);
        binding.fixed.Assign(0);
        binding.byte_count.Assign(sizeof(HardwareVertex));
    }

    u32 offset = 0;
    for (u32 i = 0; i < 8; i++) {
        VertexAttribute& attribute = software_layout.attributes[i];
        attribute.binding.Assign(0);
        attribute.location.Assign(i);
        attribute.offset.Assign(offset);
        attribute.type.Assign(Pica::PipelineRegs::VertexAttributeFormat::FLOAT);
        attribute.size.Assign(sizes[i]);
        offset += sizes[i] * sizeof(float);
    }
}

void RasterizerVulkan::SyncAndUploadLUTsLF() {
    constexpr std::size_t max_size =
        sizeof(Common::Vec2f) * 256 * Pica::LightingRegs::NumLightingSampler +
        sizeof(Common::Vec2f) * 128; // fog

    if (!pica.lighting.lut_dirty && !pica.fog.lut_dirty) {
        return;
    }

    std::size_t bytes_used = 0;
    auto [buffer, offset, invalidate] = texture_lf_buffer.Map(max_size, sizeof(Common::Vec4f));

    if (invalidate) {
        pica.lighting.lut_dirty = pica.lighting.LutAllDirty;
        pica.fog.lut_dirty = true;
    }

    // Sync the lighting luts
    while (pica.lighting.lut_dirty) {
        u32 index = std::countr_zero(pica.lighting.lut_dirty);
        pica.lighting.lut_dirty &= ~(1 << index);

        // GammaOS: build the table in ordinary memory and copy it into the stream buffer once:
        // the light skip scan below reads the table back, and the stream buffer can be
        // uncached write-combined memory where every read is a trip to RAM.
        // persist.gammaos.azahar_nano.lut_stage=0 writes and scans in place; default Mali only.
        static const bool stage_lut =
            Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.lut_stage");
        std::array<Common::Vec2f, 256> staged;
        Common::Vec2f* const mapped = reinterpret_cast<Common::Vec2f*>(buffer + bytes_used);
        Common::Vec2f* const new_data = stage_lut ? staged.data() : mapped;
        const auto& source_lut = pica.lighting.luts[index];
        static_assert(std::tuple_size_v<std::decay_t<decltype(source_lut)>> == staged.size());
        for (u32 i = 0; i < source_lut.size(); i++) {
            new_data[i] = {source_lut[i].ToFloat(), source_lut[i].DiffToFloat()};
        }
        if (stage_lut) {
            std::memcpy(mapped, staged.data(), sizeof(staged));
        }
        // GammaOS light skip: the zero head and tail of a distance attenuation table.
        if (index >= 16 && index < 24) {
            const auto zero = [&](u32 i) { return new_data[i].x == 0.f && new_data[i].y == 0.f; };
            int lo = 0;
            while (lo < 256 && zero(static_cast<u32>(lo))) lo++;
            int hi = 256;
            while (hi > lo && zero(static_cast<u32>(hi - 1))) hi--;
            fs_data.light_dist_skip[index - 16] = {lo, hi, 0, 0};
        }
        fs_data.lighting_lut_offset[index / 4][index % 4] =
            static_cast<int>((offset + bytes_used) / sizeof(Common::Vec2f));
        fs_data_dirty = true;
        bytes_used += source_lut.size() * sizeof(Common::Vec2f);
    }

    // Sync the fog lut
    if (pica.fog.lut_dirty) {
        Common::Vec2f* new_data = reinterpret_cast<Common::Vec2f*>(buffer + bytes_used);
        for (u32 i = 0; i < pica.fog.lut.size(); i++) {
            new_data[i] = {pica.fog.lut[i].ToFloat(), pica.fog.lut[i].DiffToFloat()};
        }
        fs_data.fog_lut_offset = static_cast<int>((offset + bytes_used) / sizeof(Common::Vec2f));
        fs_data_dirty = true;
        bytes_used += pica.fog.lut.size() * sizeof(Common::Vec2f);
        pica.fog.lut_dirty = false;
    }

    texture_lf_buffer.Commit(static_cast<u32>(bytes_used));
}

void RasterizerVulkan::SyncAndUploadLUTs() {
    const auto& proctex = pica.proctex;
    constexpr std::size_t max_size =
        sizeof(Common::Vec2f) * 128 * 3 + // proctex: noise + color + alpha
        sizeof(Common::Vec4f) * 256 +     // proctex
        sizeof(Common::Vec4f) * 256;      // proctex diff

    if (!pica.proctex.lut_dirty) {
        return;
    }

    std::size_t bytes_used = 0;
    auto [buffer, offset, invalidate] = texture_buffer.Map(max_size, sizeof(Common::Vec4f));

    if (invalidate) {
        pica.proctex.table_dirty = pica.proctex.TableAllDirty;
    }

    // helper function for SyncProcTexNoiseLUT/ColorMap/AlphaMap
    const auto sync_proctex_value_lut =
        [&](const std::array<Pica::PicaCore::ProcTex::ValueEntry, 128>& lut, int& lut_offset) {
            Common::Vec2f* new_data = reinterpret_cast<Common::Vec2f*>(buffer + bytes_used);
            for (u32 i = 0; i < lut.size(); i++) {
                new_data[i] = {lut[i].ToFloat(), lut[i].DiffToFloat()};
            }
            lut_offset = static_cast<int>((offset + bytes_used) / sizeof(Common::Vec2f));
            fs_data_dirty = true;
            bytes_used += lut.size() * sizeof(Common::Vec2f);
        };

    // Sync the proctex noise lut
    if (pica.proctex.noise_lut_dirty) {
        sync_proctex_value_lut(proctex.noise_table, fs_data.proctex_noise_lut_offset);
    }

    // Sync the proctex color map
    if (pica.proctex.color_map_dirty) {
        sync_proctex_value_lut(proctex.color_map_table, fs_data.proctex_color_map_offset);
    }

    // Sync the proctex alpha map
    if (pica.proctex.alpha_map_dirty) {
        sync_proctex_value_lut(proctex.alpha_map_table, fs_data.proctex_alpha_map_offset);
    }

    // Sync the proctex lut
    if (pica.proctex.lut_dirty) {
        Common::Vec4f* new_data = reinterpret_cast<Common::Vec4f*>(buffer + bytes_used);
        for (u32 i = 0; i < proctex.color_table.size(); i++) {
            new_data[i] = proctex.color_table[i].ToVector() / 255.0f;
        }
        fs_data.proctex_lut_offset =
            static_cast<int>((offset + bytes_used) / sizeof(Common::Vec4f));
        fs_data_dirty = true;
        bytes_used += proctex.color_table.size() * sizeof(Common::Vec4f);
    }

    // Sync the proctex difference lut
    if (pica.proctex.diff_lut_dirty) {
        Common::Vec4f* new_data = reinterpret_cast<Common::Vec4f*>(buffer + bytes_used);
        for (u32 i = 0; i < proctex.color_diff_table.size(); i++) {
            new_data[i] = proctex.color_diff_table[i].ToVector() / 255.0f;
        }
        fs_data.proctex_diff_lut_offset =
            static_cast<int>((offset + bytes_used) / sizeof(Common::Vec4f));
        fs_data_dirty = true;
        bytes_used += proctex.color_diff_table.size() * sizeof(Common::Vec4f);
    }

    pica.proctex.table_dirty = 0;

    texture_buffer.Commit(static_cast<u32>(bytes_used));
}

void RasterizerVulkan::UploadUniforms(bool accelerate_draw) {
    const bool sync_vs_pica = accelerate_draw && pica.vs_setup.uniforms_dirty;
    if (!sync_vs_pica && !vs_data_dirty && !fs_data_dirty) {
        return;
    }

    const u32 uniform_size =
        uniform_size_aligned_vs_pica + uniform_size_aligned_vs + uniform_size_aligned_fs;
    auto [uniforms, offset, invalidate] =
        uniform_buffer.Map(uniform_size, uniform_buffer_alignment);

    u32 used_bytes = 0;

    if (vs_data_dirty || invalidate) {
        std::memcpy(uniforms + used_bytes, &vs_data, sizeof(vs_data));
        pipeline_cache.UpdateRange(1, offset + used_bytes);
        vs_data_dirty = false;
        used_bytes += uniform_size_aligned_vs;
    }

    if (fs_data_dirty || invalidate) {
        std::memcpy(uniforms + used_bytes, &fs_data, sizeof(fs_data));
        pipeline_cache.UpdateRange(2, offset + used_bytes);
        fs_data_dirty = false;
        used_bytes += uniform_size_aligned_fs;
    }

    if (sync_vs_pica || invalidate) {
        // GammaOS: this runs for most draws (games change vertex shader uniforms per draw,
        // usually a few matrices). Convert only the registers that changed into a host copy,
        // then copy the 1.6 KB block to the mapped (uncached) buffer in one go.
        auto& setup = pica.vs_setup;
        if (setup.dirty_bi) {
            vs_pica_cache.b = 0;
            for (u32 j = 0; j < setup.uniforms.b.size(); j++) {
                vs_pica_cache.b |= setup.uniforms.b[j] << j;
            }
            for (u32 j = 0; j < setup.uniforms.i.size(); j++) {
                const auto& v = setup.uniforms.i[j];
                vs_pica_cache.i[j] = Common::MakeVec<u32>(v.x, v.y, v.z, v.w);
            }
        }
        const u32 hi = std::min<u32>(setup.dirty_f_hi, static_cast<u32>(setup.uniforms.f.size()));
        for (u32 j = setup.dirty_f_lo; j < hi; j++) {
            const auto& v = setup.uniforms.f[j];
            vs_pica_cache.f[j] = Common::MakeVec<f32>(v.x.ToFloat32(), v.y.ToFloat32(),
                                                      v.z.ToFloat32(), v.w.ToFloat32());
        }
        setup.dirty_f_lo = static_cast<u32>(setup.uniforms.f.size());
        setup.dirty_f_hi = 0;
        setup.dirty_bi = false;
        std::memcpy(uniforms + used_bytes, &vs_pica_cache, sizeof(vs_pica_cache));
        pipeline_cache.UpdateRange(0, offset + used_bytes);
        pica.vs_setup.uniforms_dirty = false;
        used_bytes += uniform_size_aligned_vs_pica;
    }

    uniform_buffer.Commit(used_bytes);
}

void RasterizerVulkan::SwitchDiskResources(u64 title_id) {
    Pica::Shader::Generator::SetVsBoolSpecializationTitle(title_id);
    VideoCore::AsyncGpuDrain(); // GammaOS async GPU: CPU-side access, wait for the worker
    std::atomic_bool stop_loading = false;

    if (switch_disk_resources_callback) {
        switch_disk_resources_callback(VideoCore::LoadCallbackStage::Prepare, 0, 0, "");
    }

    pipeline_cache.SetAccurateMul(accurate_mul);
    pipeline_cache.SwitchCache(title_id, stop_loading, switch_disk_resources_callback);

    if (switch_disk_resources_callback) {
        switch_disk_resources_callback(VideoCore::LoadCallbackStage::Complete, 0, 0, "");
    }
}

} // namespace Vulkan
