// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <functional>
#include <list>
#include <optional>
#include <span>
#include <unordered_map>
#include <vector>
#include <boost/icl/interval_map.hpp>
#include <tsl/robin_map.h>

#include "video_core/rasterizer_cache/framebuffer_base.h"
#include "video_core/rasterizer_cache/sampler_params.h"
#include "video_core/rasterizer_cache/surface_params.h"
#include "video_core/rasterizer_cache/texture_cube.h"

namespace Memory {
class MemorySystem;
}

namespace Pica {
struct RegsInternal;
struct DisplayTransferConfig;
struct MemoryFillConfig;
} // namespace Pica

namespace Pica::Texture {
struct TextureInfo;
}

namespace Settings {
enum class TextureFilter : u32;
}

namespace VideoCore {

enum class ScaleMatch {
    Exact,   ///< Only accept same res scale
    Upscale, ///< Only allow higher scale than params
    Ignore   ///< Accept every scaled res
};

enum class MatchFlags {
    Exact = 1 << 0,       ///< Surface perfectly matches params
    SubRect = 1 << 1,     ///< Surface encompasses params
    Copy = 1 << 2,        ///< Surface that can be used as a copy source
    TexCopy = 1 << 3,     ///< Surface that will match a display transfer "texture copy" parameters
    Reinterpret = 1 << 4, ///< Surface might have different pixel format.
};

DECLARE_ENUM_FLAG_OPERATORS(MatchFlags);

class CustomTexManager;
class RendererBase;
enum class SurfaceFlagBits : u32;

template <class T>
class RasterizerCache {
    /// Address shift for caching surfaces into a hash table
    static constexpr u64 CITRA_PAGEBITS = 18;

    using Runtime = typename T::Runtime;
    using Sampler = typename T::Sampler;
    using Surface = typename T::Surface;
    using Framebuffer = typename T::Framebuffer;
    using DebugScope = typename T::DebugScope;

    using SurfaceMap = boost::icl::interval_map<PAddr, SurfaceId, boost::icl::partial_absorber,
                                                std::less, boost::icl::inplace_plus,
                                                boost::icl::inter_section, SurfaceInterval>;

    using SurfaceRect_Tuple = std::pair<SurfaceId, Common::Rectangle<u32>>;
    using PageMap = boost::icl::interval_map<u32, int>;

public:
    explicit RasterizerCache(Memory::MemorySystem& memory, CustomTexManager& custom_tex_manager,
                             Runtime& runtime, Pica::RegsInternal& regs, RendererBase& renderer);
    ~RasterizerCache();

    /// Notify the cache that a new frame has been queued
    void TickFrame();

    /// GammaOS: counts the downloads that wrote guest memory (surface flushes and fills), so a
    /// caller can tell that guest memory did not change in between.
    u64 GuestWrites() const noexcept {
        return guest_writes;
    }

    /// GammaOS: changes whenever a lookup could return something different (surfaces validated,
    /// uploaded, invalidated, registered, unregistered...). Callers memoize lookups on it.
    u64 Epoch() const noexcept {
        return cache_epoch;
    }

    /// GammaOS: true when the next TickFrame may drop every surface or upload custom textures,
    /// which needs the worker thread idle. Plain garbage collection does not: it only deletes
    /// surfaces whose last use the GPU has already completed.
    bool TickFrameNeedsIdleWorker() const;

    /// Perform hardware accelerated texture copy according to the provided configuration
    bool AccelerateTextureCopy(const Pica::DisplayTransferConfig& config);

    /// GammaOS: GPU path for a gapless texture copy whose source lies in GPU-modified tiled
    /// surface memory but does not start on a tile row (see the definition).
    bool AccelerateTiledByteCopy(PAddr src_addr, PAddr dst_addr, u32 size);

    /// Perform hardware accelerated display transfer according to the provided configuration
    bool AccelerateDisplayTransfer(const Pica::DisplayTransferConfig& config);

    /// Perform hardware accelerated memory fill according to the provided configuration
    bool AccelerateFill(const Pica::MemoryFillConfig& config);

    /// Returns a reference to the surface object assigned to surface_id
    Surface& GetSurface(SurfaceId surface_id);

    /// Returns a reference to the sampler object matching the provided configuration
    Sampler& GetSampler(const Pica::TexturingRegs::TextureConfig& config);

    /// GammaOS stereo twins. Two eye buffers (colour + depth each) that are known to hold the
    /// same content can take a pair of identical draws as one draw into the canonical pair.
    /// TwinTargets returns the canonical colour/depth addresses to draw into when the draw
    /// from (cur_color, cur_depth) and its twin to (next_color, next_depth) qualify: the pair
    /// is already mirrored, or all four buffers were freshly filled with matching values.
    bool TwinTargets(PAddr cur_color, PAddr cur_depth, PAddr next_color, PAddr next_depth,
                     u32 color_size, u32 depth_size, std::span<const PAddr> textures,
                     PAddr& canon_color, PAddr& canon_depth);
    /// While set, draws into the mirrored buffers do not end the mirror.
    void SetTwinDraw(bool active) {
        twin_draw = active;
    }
    /// Any access to the mirror buffers, or a write to the canonical ones, ends the mirror:
    /// the canonical content is copied over first. Called by every cache entry point.
    void MirrorAccess(PAddr addr, u32 size, bool write) {
        // Called on every flush/invalidate (CPU memory access checks): keep the common case,
        // no mirror, to one test.
        if (mirror.active) [[unlikely]] {
            MirrorAccessSlow(addr, size, write);
        }
    }
    void MirrorAccessSlow(PAddr addr, u32 size, bool write);
    /// Count of twin draws drawn once and of mirror copies (stats).
    u32 TakeTwinStats(u32& copies);
    Sampler& GetSampler(SamplerId sampler_id);

    /// Copy one surface's region to another
    void CopySurface(Surface& src_surface, Surface& dst_surface, SurfaceInterval copy_interval);

    /// Load a texture from 3DS memory to OpenGL and cache it (if not already cached)
    SurfaceId GetSurface(const SurfaceParams& params, ScaleMatch match_res_scale,
                         bool load_if_create, const SurfaceFlagBits& create_initial_flags = {});

    /// Attempt to find a subrect (resolution scaled) of a surface, otherwise loads a texture from
    /// 3DS memory to OpenGL and caches it (if not already cached)
    SurfaceRect_Tuple GetSurfaceSubRect(const SurfaceParams& params, ScaleMatch match_res_scale,
                                        bool load_if_create,
                                        const SurfaceFlagBits& create_initial_flags = {});

    /// Get a surface based on the texture configuration
    Surface& GetTextureSurface(const Pica::TexturingRegs::FullTextureConfig& config);

    /// GammaOS: as GetTextureSurface, for a texture read through its storage view (shadow maps).
    Surface& GetStorageTextureSurface(const Pica::TexturingRegs::FullTextureConfig& config);

    /// GammaOS: gives the surface the storage view it lacks (Surface::EnsureStorage) and drops
    /// what referenced its old images.
    void EnsureStorage(SurfaceId surface_id);
    SurfaceId GetTextureSurface(const Pica::Texture::TextureInfo& info, u32 max_level = 0);

    /// Get a texture cube based on the texture configuration
    Surface& GetTextureCube(const TextureCubeConfig& config);

    /// Get the color and depth surfaces based on the framebuffer configuration
    FramebufferHelper<T> GetFramebufferSurfaces(bool using_color_fb, bool using_depth_fb);

    /// Get a surface that matches a "texture copy" display transfer config
    SurfaceRect_Tuple GetTexCopySurface(const SurfaceParams& params);

    /// Write any cached resources overlapping the region back to memory (if dirty)
    void FlushRegion(PAddr addr, u32 size, SurfaceId flush_surface = {});

    /// Mark region as being invalidated by region_owner (nullptr if 3DS memory)
    void InvalidateRegion(PAddr addr, u32 size, SurfaceId region_owner = {});

    /// Flush all cached resources tracked by this cache manager
    void FlushAll();

    /// Clear all cached resources tracked by this cache manager
    void ClearAll(bool flush);

private:
    /// Iterate over all page indices in a range
    template <typename Func>
    void ForEachPage(PAddr addr, std::size_t size, Func&& func) {
        static constexpr bool RETURNS_BOOL = std::is_same_v<std::invoke_result<Func, u64>, bool>;
        const u64 page_end = (addr + size - 1) >> CITRA_PAGEBITS;
        for (u64 page = addr >> CITRA_PAGEBITS; page <= page_end; ++page) {
            if constexpr (RETURNS_BOOL) {
                if (func(page)) {
                    break;
                }
            } else {
                func(page);
            }
        }
    }

    /// Iterates over all the surfaces in a region calling func
    template <typename Func>
    void ForEachSurfaceInRegion(PAddr addr, std::size_t size, Func&& func);

    /// Get the best surface match (and its match type) for the given flags
    template <MatchFlags find_flags>
    SurfaceId FindMatch(const SurfaceParams& params, ScaleMatch match_scale_type,
                        std::optional<SurfaceInterval> validate_interval = std::nullopt);

    /// Unregisters sentenced surfaces that have surpassed the destruction threshold.
    void RunGarbageCollector();

    /// Removes any framebuffers that reference the provided surface_id.
    void RemoveFramebuffers(SurfaceId surface_id);

public:
    /// GammaOS: unregisters every surface without flushing it (save state load).
    void ResetForLoad();

private:

    /// Removes any references of the provided surface id from cached texture cubes.
    void RemoveTextureCubeFace(SurfaceId surface_id);

    /// Computes the hash of the provided texture data.
    u64 ComputeHash(const SurfaceParams& load_info, std::span<u8> upload_data);

    /// Update surface's texture for given region when necessary
    void ValidateSurface(SurfaceId surface, PAddr addr, u32 size);

    /// Copies pixel data in interval from the guest VRAM to the host GPU surface
    void UploadSurface(Surface& surface, SurfaceInterval interval);

    /// Uploads a custom texture identified with hash to the target surface
    bool UploadCustomSurface(SurfaceId surface_id, SurfaceInterval interval);

    /// Copies pixel data in interval from the host GPU surface to the guest VRAM
    void DownloadSurface(Surface& surface, SurfaceInterval interval);

    /// Downloads a fill surface to guest VRAM
    void DownloadFillSurface(Surface& surface, SurfaceInterval interval);

    /// Attempt to find a reinterpretable surface in the cache and use it to copy for validation
    bool ValidateByReinterpretation(SurfaceId surface_id, Surface& surface, SurfaceParams params,
                                    const SurfaceInterval& interval);

    /// Create a new surface
    SurfaceId CreateSurface(const SurfaceParams& params, const SurfaceFlagBits& initial_flags = {});

    /// Register surface into the cache
    void RegisterSurface(SurfaceId surface);

    /// Remove surface from the cache
    void UnregisterSurface(SurfaceId surface);

    /// Unregisters all surfaces from the cache
    void UnregisterAll();

    /// Increase/decrease the number of surface in pages touching the specified region
    void UpdatePagesCachedCount(PAddr addr, u32 size, int delta);

    /// GammaOS: re-evaluates, for every page touching [lo, hi), whether CPU reads may skip the
    /// flush (no dirty region overlaps the page) and tells the memory system when that changes.
    /// `known_dirty` skips the lookup when the caller just marked the whole range dirty.
    void UpdateReadablePages(PAddr lo, PAddr hi, bool known_dirty = false);

private:
    Memory::MemorySystem& memory;
    CustomTexManager& custom_tex_manager;
    Runtime& runtime;
    Pica::RegsInternal& regs;
    RendererBase& renderer;
    std::unordered_map<TextureCubeConfig, TextureCube> texture_cube_cache;
    tsl::robin_pg_map<u64, std::vector<SurfaceId>, Common::IdentityHash<u64>> page_table;
    std::unordered_map<FramebufferParams, FramebufferId> framebuffers;
    std::unordered_map<SamplerParams, SamplerId> samplers;
    // GammaOS: a sentenced surface also remembers the collector frame in which the
    // GPU was first seen done with it, so it can stay recyclable for a few frames.
    struct SentencedSurface {
        SentencedSurface(SurfaceId id, u64 tick) : first{id}, second{tick} {}
        SurfaceId first;
        u64 second;     // resource tick of its last use
        u64 idle_since = 0; // collector frame it became idle in (0 = still in use)
    };
    std::list<SentencedSurface> sentenced;
    u64 gc_frame = 1;
    Common::SlotVector<Surface> slot_surfaces;
    Common::SlotVector<Sampler> slot_samplers;
    Common::SlotVector<Framebuffer> slot_framebuffers;
    SurfaceMap dirty_regions;
    /// GammaOS: one bit per physical page, set while the memory system has the page marked as
    /// not directly readable by the JIT (see UpdateReadablePages).
    std::vector<u64> unreadable_pages = std::vector<u64>((u64{1} << 20) / 64);
    PageMap cached_pages;
    u32 resolution_scale_factor;
    FramebufferParams fb_params;
    // GammaOS stereo twins (see TwinTargets).
    struct FreshFill {
        PAddr start;
        PAddr end;
        u32 value;
        u32 mode;   // fill width: 16, 24 or 32 bit
    };
    std::vector<FreshFill> fresh_fills;
    /// GammaOS: render target invalidations already done (see InvalidateRegion).
    struct InvalidateMemo {
        PAddr lo = 0;
        PAddr hi = 0;
        SurfaceId owner{};
        u64 epoch = ~0ull;
    };
    /// GammaOS: the previous GetFramebufferSurfaces result, valid while its inputs and the
    /// cache epoch are unchanged (the lookups and validations would repeat exactly).
    struct FramebufferMemo {
        Pica::FramebufferRegs::FramebufferConfig config{};
        Common::Rectangle<u32> viewport{};
        u64 epoch = ~0ull;
        u32 res_scale = 0;
        bool using_color = false;
        bool using_depth = false;
        bool shadow = false;
        FramebufferId fb_id{};
        Common::Rectangle<u32> fb_rect{};
    };
    FramebufferMemo fb_memo{};
    std::array<InvalidateMemo, 4> invalidate_memo{};
    u32 invalidate_memo_next = 0;
    u64 cache_epoch = 0;
    u64 guest_writes = 0;
    struct Mirror {
        bool active = false;
        PAddr canon_color = 0, canon_depth = 0, mirror_color = 0, mirror_depth = 0;
        u32 color_size = 0, depth_size = 0;
        SurfaceParams color_params{};   // canonical colour surface (address set per copy)
        SurfaceParams depth_params{};
    } mirror;
    bool twin_draw = false;
    bool in_materialize = false;
    bool mirror_dirty = false;   // the canonical buffers took twin draws since the last copy
    u32 twin_pairs = 0, mirror_copies = 0;
    const FreshFill* FindFreshFill(PAddr start, u32 size) const;
    void MaterializeMirror();
    Settings::TextureFilter filter;
    bool dump_textures;
    bool use_custom_textures;
};

} // namespace VideoCore
