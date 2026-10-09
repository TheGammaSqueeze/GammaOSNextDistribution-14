// GammaOS: per-frame renderer work counters for azahar-nano profiling. Plain
// relaxed atomics, summed between two swap trace lines (every 120 frames).
#pragma once

#include <atomic>
#include <cstdint>

namespace Vulkan::GammaStats {

enum Counter : int {
    Uploads, UploadPixels, UploadScaledPixels,
    Downloads, DownloadPixels,
    Copies, CopyPixels,
    Blits, BlitPixels,
    Clears, ClearPixels,
    Mipmaps,
    Draws, RenderPasses,
    Reinterprets,
    PassBreaksAreaOnly,
    DrawsZBuffer, DrawsWBuffer, DrawsShadow, DrawsAlphaTest, DrawsBlend,
    CmdLists, CmdListsTop, CmdListsBlocked, Transfers, TransfersBlocked,
    PipesFast, PipesQueued, DrawsSkippedPending, ShadersCompiled,
    ImagesCreated, SurfacesRecycled, HybridFallbackDraws, HybridUberDraws, HybridUberCompiles, PipeHwBuilds, PipeHwUs, PipeSwBuilds, PipeSwUs, PipeUberBuilds, PipeUberUs, FastAttempts, FastAttemptUs, FastAttemptMaxUs, PipeBuilds, PipeBuildUs, PipeFirstBuilds, PipeFirstUs, PipeRepeatBuilds, PipeRepeatUs,
    Count
};

inline std::atomic<std::uint64_t> g_counters[Count];

inline void Add(Counter c, std::uint64_t v = 1) {
    g_counters[c].fetch_add(v, std::memory_order_relaxed);
}

inline std::uint64_t Peek(Counter c) {
    return g_counters[c].load(std::memory_order_relaxed);
}

inline void Set(Counter c, std::uint64_t v) {
    g_counters[c].store(v, std::memory_order_relaxed);
}

inline std::uint64_t Take(Counter c) {
    return g_counters[c].exchange(0, std::memory_order_relaxed);
}

} // namespace Vulkan::GammaStats
