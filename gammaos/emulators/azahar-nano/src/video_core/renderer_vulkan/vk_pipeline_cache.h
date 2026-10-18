// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <atomic>
#include <bitset>
#include <chrono>
#include <memory>
#include <mutex>
#include <unordered_map>

#include "video_core/rasterizer_interface.h"
#include "video_core/renderer_vulkan/vk_graphics_pipeline.h"
#include "video_core/renderer_vulkan/vk_resource_pool.h"
#include "video_core/renderer_vulkan/vk_shader_disk_cache.h"
#include "video_core/shader/generator/pica_fs_config.h"
#include "video_core/shader/generator/profile.h"
#include "video_core/shader/generator/shader_gen.h"

namespace Pica {
struct RegsInternal;
struct ShaderSetup;
} // namespace Pica

namespace Vulkan {

class Instance;
class Scheduler;
class RenderManager;
class DescriptorUpdateQueue;

enum class DescriptorHeapType : u32 {
    Buffer,
    Texture,
    Utility,
};

/**
 * Stores a collection of rasterizer pipelines used during rendering.
 */
class PipelineCache {
    static constexpr u32 NumRasterizerSets = 3;
    static constexpr u32 NumDescriptorHeaps = 3;
    static constexpr u32 NumDynamicOffsets = 3;

public:
    explicit PipelineCache(const Instance& instance, Scheduler& scheduler,
                           RenderManager& renderpass_cache, DescriptorUpdateQueue& update_queue);
    ~PipelineCache();

    /// GammaOS: finishes the queued compiles and writes the fixed states and the driver pipeline
    /// cache (when it grew), as the destructor does; the process exit path calls it instead.
    void PersistDiskCache();

    /// GammaOS: the set Acquire last bound for this heap.
    vk::DescriptorSet BoundSet(DescriptorHeapType type) const {
        return bound_descriptor_sets[static_cast<u32>(type)];
    }

    /// Acquires and binds a free descriptor set from the appropriate heap.
    vk::DescriptorSet Acquire(DescriptorHeapType type) {
        const u32 index = static_cast<u32>(type);
        const auto descriptor_set = descriptor_heaps[index].Commit();
        bound_descriptor_sets[index] = descriptor_set;
        return descriptor_set;
    }

    /// Sets the dynamic offset for the uniform buffer at binding
    void UpdateRange(u8 binding, u32 offset) {
        offsets[binding] = offset;
    }

    /// Loads the driver pipeline cache and the disk shader cache
    void LoadCache(const std::atomic_bool& stop_loading = std::atomic_bool{false},
                   const VideoCore::DiskResourceLoadCallback& callback = {});

    /// Switches the driver pipeline cache and the shader disk cache to the specified title
    void SwitchCache(u64 title_id, const std::atomic_bool& stop_loading = std::atomic_bool{false},
                     const VideoCore::DiskResourceLoadCallback& callback = {});

    /// Binds a pipeline using the provided information
    bool BindPipeline(PipelineInfo& info, bool wait_built = false);

    Pica::Shader::Generator::ExtraVSConfig CalcExtraConfig(
        const Pica::Shader::Generator::PicaVSConfig& config);

    /// Binds a PICA decompiled vertex shader
    bool UseProgrammableVertexShader(const Pica::RegsInternal& regs, Pica::ShaderSetup& setup,
                                     const VertexLayout& layout,
                                     bool may_reuse = false);

    /// Binds a passthrough vertex shader
    void UseTrivialVertexShader();

    /// Binds a PICA decompiled geometry shader
    bool UseFixedGeometryShader(const Pica::RegsInternal& regs);

    /// Binds a passthrough geometry shader
    void UseTrivialGeometryShader();

    /// Binds a fragment shader generated from PICA state
    /// GammaOS: may_reuse = no register the fragment shader config reads changed since the
    /// previous draw, so the previous shader stands and the config is not rebuilt or hashed.
    void UseFragmentShader(const Pica::RegsInternal& regs, const Pica::Shader::UserConfig& user,
                           bool may_reuse = false);

    /// GammaOS: shader id of a fragment ubershader variant (never a real config hash).
    static constexpr u64 UberIdBase = 0x5542455246530000ULL; // "UBERFS"
    static bool IsUberId(u64 id) {
        return (id & 0xFFFFFFFFFFFF0000ULL) == UberIdBase;
    }
    /// Returns the fragment ubershader for a variant key, compiling it once.
    Shader* GetUberShader(u32 variant_key);
    /// False once an ubershader failed to compile (the hybrid path then waits instead).
    bool UberAvailable() const {
        return !uber_failed.load();
    }
    /// GammaOS: fragment ubershader with the combiner stages compiled in (baked).
    static constexpr u64 BakedIdBase = 0x42414B4500000000ULL; // "BAKE"
    Shader* GetBakedShader(u32 variant_key, const std::array<u32, 12>& tev, u64* id_out);
    void UseBakedFragmentShader(u32 variant_key, const std::array<u32, 12>& tev);
    /// True when a baked shader for this combiner setup is already compiled (no compile).
    bool HasBakedShader(u32 variant_key, const std::array<u32, 12>& tev);
    static std::array<u32, 12> MaskBakedWords(const std::array<u32, 12>& tev);
    /// GammaOS: fixed-function states seen on this device (all titles), for precompiling.
    struct FixedState {
        BlendingState blending;
        AttachmentInfo attachments;
        RasterizationState rasterization;
        DepthStencilState depth_stencil;
    };
    void RecordFixedState(const StaticPipelineInfo& state);
    void LoadFixedStates();
    void SaveFixedStates();
    void SetSoftwareLayout(const VertexLayout& layout) {
        sw_layout = layout;
        has_sw_layout = true;
    }
    /// Records the ubershader's packed combiner stages (push constants).
    void PushUberTev(const std::array<u32, 16>& words);
    /// Binds a fragment ubershader variant instead of the generated fragment shader.
    void UseUberFragmentShader(u32 variant_key);
    const Pica::Shader::Profile& GetProfile() const {
        return profile;
    }

    /// Gets the current program ID
    u64 GetProgramID() const {
        return current_program_id;
    }

    void SetProgramID(u64 program_id) {
        current_program_id = program_id;
    }

    void SetAccurateMul(bool _accurate_mul) {
        profile.enable_accurate_mul = _accurate_mul;
    }

    /// Called once per presented frame: writes the driver pipeline cache in the
    /// background when it has grown, so a killed or crashed session keeps it.
    void TickFrame();

private:
    friend ShaderDiskCache;

    /// Loads the driver pipeline cache
    void LoadDriverPipelineDiskCache(const std::atomic_bool& stop_loading = std::atomic_bool{false},
                                     const VideoCore::DiskResourceLoadCallback& callback = {});

    /// Stores the generated pipeline cache
    void SaveDriverPipelineDiskCache();

    /// Loads the shader disk cache
    void LoadDiskCache(const std::atomic_bool& stop_loading = std::atomic_bool{false},
                       const VideoCore::DiskResourceLoadCallback& callback = {});

    /// Switches the disk cache at runtime to use a different title ID
    void SwitchDiskCache(u64 title_id, const std::atomic_bool& stop_loading,
                         const VideoCore::DiskResourceLoadCallback& callback);

    /// Builds the rasterizer pipeline layout
    void BuildLayout();

    /// Returns true when the disk data can be used by the current driver
    bool IsCacheValid(std::span<const u8> cache_data) const;

    /// Create pipeline cache directories. Returns true on success.
    bool EnsureDirectories() const;

    /// Returns the Vulkan shader directory
    std::string GetVulkanDir() const;

    /// Returns the pipeline cache storage dir
    std::string GetPipelineCacheDir() const;

    /// Driver pipeline cache file of the current title. The name carries the
    /// driver's pipelineCacheUUID and version: two drivers for the same GPU (the
    /// system driver and a custom Turnip build) report the same vendor and device
    /// IDs and would otherwise share one file and delete each other's cache.
    std::string GetDriverPipelineCachePath() const;

    /// Returns the transferable shader dir
    std::string GetTransferableDir() const;

private:
    const Instance& instance;
    Scheduler& scheduler;
    RenderManager& renderpass_cache;
    DescriptorUpdateQueue& update_queue;

    Pica::Shader::Profile profile{};
    vk::UniquePipelineCache driver_pipeline_cache;
    std::size_t saved_driver_cache_size{};
    std::chrono::steady_clock::time_point last_cache_check{};
    std::atomic_bool driver_cache_save_pending{false};
    std::mutex driver_cache_save_mutex;
    vk::UniquePipelineLayout pipeline_layout;
    std::size_t num_worker_threads;
    Common::ThreadWorker pipeline_workers;
    Common::ThreadWorker fast_pipeline_workers;
    Common::ThreadWorker shader_workers;
    PipelineInfo current_info{};
    GraphicsPipeline* current_pipeline{};
    std::array<DescriptorHeap, NumDescriptorHeaps> descriptor_heaps;
    std::array<vk::DescriptorSet, NumRasterizerSets> bound_descriptor_sets{};
    std::array<u32, NumDynamicOffsets> offsets{};

    std::array<u64, MAX_SHADER_STAGES> shader_hashes;
    std::array<Shader*, MAX_SHADER_STAGES> current_shaders;

    Shader trivial_vertex_shader;

    std::mutex uber_mutex;
    std::unordered_map<u32, std::unique_ptr<Shader>> uber_shaders;
    std::atomic_bool uber_failed{false};
    std::unordered_map<u64, std::unique_ptr<Shader>> baked_shaders;
    std::vector<FixedState> fixed_states;
    bool fixed_states_dirty = false;
    VertexLayout sw_layout{};
    bool has_sw_layout = false;

    u64 current_program_id{0};
    std::vector<std::shared_ptr<ShaderDiskCache>> disk_caches;
    std::shared_ptr<ShaderDiskCache> curr_disk_cache{};
    // GammaOS: the current VS / FS came from a lookup on this disk cache (reuse allowed).
    bool vs_valid = false;
    bool fs_valid = false;
};

} // namespace Vulkan
