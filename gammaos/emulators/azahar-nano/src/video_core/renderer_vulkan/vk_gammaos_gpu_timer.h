// GammaOS: GPU time per category of work (render passes, clears, blits, the
// final present), measured with Vulkan timestamps for azahar-nano profiling.
// Every timestamp is written outside a render pass. Each frame uses one pool of
// a ring; it is reset lazily by the first mark of the frame and read back,
// without waiting, three frames later. Summed between two swap trace lines.
#pragma once

#include <array>
#include <atomic>
#include <cstdint>
#include <string>
#include <vector>
#include <sys/system_properties.h>

#include "common/logging/log.h"

#include "video_core/renderer_vulkan/vk_common.h"
#include "video_core/renderer_vulkan/vk_scheduler.h"

namespace Vulkan::GammaGpu {

enum Category : int { Pass, Clear, Blit, Present, Copy, Upload, NumCategories };

inline constexpr const char* kCategoryNames[NumCategories] = {"passes", "clears", "blits",
                                                              "present", "copies", "uploads"};
inline constexpr std::uint32_t kRing = 4;
inline constexpr std::uint32_t kQueries = 256;   // per pool (pairs)

struct State {
    vk::Device device{};
    std::array<vk::QueryPool, kRing> pools{};
    std::array<std::uint32_t, kRing> used{};
    std::array<std::vector<std::uint8_t>, kRing> cats{};   // category per pair
    std::array<std::vector<std::string>, kRing> labels{};  // what each pair measured
    std::array<bool, kRing> dump{};    // log this pool's pairs one by one when read back
    bool dump_next = false;
    std::array<bool, kRing> needs_reset{};
    // Queries to reset before the pool is reused: only the ones written since the last reset
    // (Turnip resets query by query, a full 512-query reset per frame cost ~10% of the worker).
    std::array<std::uint32_t, kRing> reset_count{};
    std::uint32_t frame = 0;   // index of the pool being recorded
    double period_ns = 1.0;
    bool enabled = false;
    bool presenting = false;
    // accumulated over the trace window, in ms
    std::array<double, NumCategories> sum_ms{};
    double span_ms = 0.0;
    double max_frame_pass_ms = 0.0;  // worst frame's render pass total
    double max_pass_ms = 0.0;        // worst single render pass
    std::uint32_t frames_read = 0;
};

inline State g;

inline void Init(vk::Device device, float timestamp_period_ns, bool supported) {
    if (!supported || g.enabled) return;
    g.device = device;
    g.period_ns = timestamp_period_ns;
    const vk::QueryPoolCreateInfo info = {
        .queryType = vk::QueryType::eTimestamp,
        .queryCount = kQueries * 2,
    };
    for (std::uint32_t i = 0; i < kRing; i++) {
        g.pools[i] = device.createQueryPool(info);
        g.needs_reset[i] = true;
        g.reset_count[i] = kQueries * 2;
        g.cats[i].reserve(kQueries);
        g.labels[i].reserve(kQueries);
    }
    g.enabled = true;
}

inline void Shutdown() {
    if (!g.enabled) return;
    for (auto& p : g.pools) g.device.destroyQueryPool(p);
    g.enabled = false;
}

// Writes the begin stamp of a pair; returns the pair index or -1.
inline int Begin(Scheduler& scheduler, Category cat, std::string label = {}) {
    if (!g.enabled) return -1;
    const std::uint32_t f = g.frame;
    if (g.used[f] >= kQueries) return -1;
    const std::uint32_t reset = g.needs_reset[f] ? g.reset_count[f] : 0;
    g.needs_reset[f] = false;
    const int idx = static_cast<int>(g.used[f]++);
    g.cats[f].push_back(static_cast<std::uint8_t>(cat));
    g.labels[f].push_back(std::move(label));
    scheduler.Record([pool = g.pools[f], idx, reset](vk::CommandBuffer cmdbuf) {
        if (reset) cmdbuf.resetQueryPool(pool, 0, reset);
        cmdbuf.writeTimestamp(vk::PipelineStageFlagBits::eTopOfPipe, pool, idx * 2);
    });
    return idx;
}

inline void End(Scheduler& scheduler, int idx) {
    if (!g.enabled || idx < 0) return;
    scheduler.Record([pool = g.pools[g.frame], idx](vk::CommandBuffer cmdbuf) {
        cmdbuf.writeTimestamp(vk::PipelineStageFlagBits::eBottomOfPipe, pool, idx * 2 + 1);
    });
}

// Once per presented frame (start of SwapBuffers): read the pool written three
// frames ago if it is complete, then move recording to the next pool.
inline void FrameBoundary() {
    if (!g.enabled) return;
    const std::uint32_t old = (g.frame + 1) % kRing;   // oldest in the ring
    const std::uint32_t n = g.used[old];
    if (n > 0) {
        std::vector<std::uint64_t> ts(n * 2);
        const vk::Result r = g.device.getQueryPoolResults(
            g.pools[old], 0, n * 2, ts.size() * sizeof(std::uint64_t), ts.data(),
            sizeof(std::uint64_t), vk::QueryResultFlagBits::e64);
        if (r == vk::Result::eSuccess) {
            std::uint64_t first = UINT64_MAX, last = 0;
            double frame_pass_ms = 0.0;
            if (g.dump[old]) {
                LOG_INFO(Render_Vulkan, "gpu time dump: {} entries", n);
            }
            for (std::uint32_t i = 0; i < n; i++) {
                const std::uint64_t b = ts[i * 2], e = ts[i * 2 + 1];
                if (g.dump[old]) {
                    LOG_INFO(Render_Vulkan, "gpu time {:3} {:8} {:7.3f} ms at {:7.3f} | {}", i,
                             kCategoryNames[g.cats[old][i]],
                             e > b ? (e - b) * g.period_ns / 1e6 : 0.0,
                             (b - ts[0]) * g.period_ns / 1e6, g.labels[old][i]);
                }
                if (e <= b) continue;
                const double ms = (e - b) * g.period_ns / 1e6;
                g.sum_ms[g.cats[old][i]] += ms;
                if (g.cats[old][i] == Pass) {
                    frame_pass_ms += ms;
                    g.max_pass_ms = std::max(g.max_pass_ms, ms);
                }
                first = std::min(first, b);
                last = std::max(last, e);
            }
            if (last > first) g.span_ms += (last - first) * g.period_ns / 1e6;
            g.max_frame_pass_ms = std::max(g.max_frame_pass_ms, frame_pass_ms);
            g.frames_read++;
        }
    }
    // A pool with nothing recorded keeps its pending reset (and count) for the next use.
    if (n > 0) {
        g.reset_count[old] = n * 2;
        g.needs_reset[old] = true;
    }
    g.used[old] = 0;
    g.cats[old].clear();
    g.labels[old].clear();
    g.dump[old] = false;
    g.frame = old;
    // Debug: sys.gammaos.azahar_nano.gputime_dump=1 logs every timed entry of the next frame.
    static std::uint32_t polls = 0;
    if ((polls++ & 31) == 0) {
        char v[PROP_VALUE_MAX] = {};
        if (__system_property_get("sys.gammaos.azahar_nano.gputime_dump", v) > 0 && v[0] == '1') {
            __system_property_set("sys.gammaos.azahar_nano.gputime_dump", "");
            g.dump[g.frame] = true;
        }
    }
}

} // namespace Vulkan::GammaGpu

// One-shot frame dump for profiling: with sys.gammaos.azahar_nano.drawdump=1
// the next frame wraps every draw in an occlusion query and logs, per draw, the
// samples it wrote plus its blend / alpha test / texture / fog / lighting state.
#include <string>
#include <sys/system_properties.h>
#include "common/logging/log.h"

namespace Vulkan::GammaDump {

struct DrawInfo {
    std::uint32_t rect_w, rect_h, vertices, tex_mask, fog, lighting, blend, alpha_test, depth_test;
    std::uint32_t tex0_fmt, tex0_w, tex0_h, tex0_mag, tex0_min, tex0_addr, color_addr;
    std::uint32_t lights, luts, dist_atten_mask, spot_atten_mask;
    std::uint64_t fs_hash;
};

struct Event {
    int draw;            // draw index, or -1 for a non-draw event
    std::string text;    // non-draw event description
};

struct State {
    std::vector<Event> events;
    vk::Device device{};
    vk::QueryPool pool{};
    vk::QueryPool tpool{};   // GPU timestamp after each draw (index 0 = before the first)
    bool precise = false;
    bool active = false;
    int empty_presents = 0;
    std::uint64_t last_fs_hash = 0;     // fragment config of the draw being set up
    std::vector<std::uint64_t> fs_written;   // GLSL sources already written this session
    std::vector<DrawInfo> draws;
    static constexpr std::uint32_t kMax = 1024;
};
inline State d;

inline bool Requested() {
    char v[PROP_VALUE_MAX] = {};
    if (__system_property_get("sys.gammaos.azahar_nano.drawdump", v) > 0 && v[0] == '1') {
        __system_property_set("sys.gammaos.azahar_nano.drawdump", "");
        return true;
    }
    return false;
}

// Called at the start of SwapBuffers with no render pass open.
inline void FrameBoundary(Scheduler& scheduler, vk::Device device, bool precise) {
    // A present with no draws in it (the core presents more often than the game
    // draws): keep collecting, up to a few presents.
    if (d.active && d.draws.empty() && d.empty_presents < 3) {
        d.empty_presents++;
        return;
    }
    if (d.active) {
        d.active = false;
        scheduler.Finish();
        const std::uint32_t n = static_cast<std::uint32_t>(d.draws.size());
        std::vector<std::uint64_t> samples(n);
        if (n > 0) {
            (void)device.getQueryPoolResults(d.pool, 0, n, samples.size() * 8, samples.data(), 8,
                                             vk::QueryResultFlagBits::e64 |
                                                 vk::QueryResultFlagBits::eWait);
        }
        std::vector<std::uint64_t> ts(n + 1);
        if (n > 0) {
            (void)device.getQueryPoolResults(d.tpool, 0, n + 1, ts.size() * 8, ts.data(), 8,
                                             vk::QueryResultFlagBits::e64 |
                                                 vk::QueryResultFlagBits::eWait);
        }
        // A draw's time runs from the previous draw's end (or the first draw's start).
        const auto draw_us = [&](std::uint32_t i) {
            const std::uint64_t b = ts[i], e = ts[i + 1];
            return e > b && b != 0 ? (e - b) * GammaGpu::g.period_ns / 1e3 : 0.0;
        };
        std::uint64_t total = 0;
        for (auto s : samples) total += s;
        LOG_INFO(Render_Vulkan, "draw dump: {} draws, {} samples written in total", n, total);
        for (const auto& e : d.events) {
            if (e.draw < 0) LOG_INFO(Render_Vulkan, "event: {}", e.text);
            else LOG_INFO(Render_Vulkan, "event: draw {} ({} samples)", e.draw,
                          static_cast<std::uint32_t>(e.draw) < n ? samples[e.draw] : 0);
        }
        for (std::uint32_t i = 0; i < n; i++) {
            const auto& x = d.draws[i];
            LOG_INFO(Render_Vulkan,
                     "draw {:3}: us {:7.1f} samples {:8} ({:5.1f}%) rect {}x{} verts {} tex {:03b} fog {} "
                     "light {} blend {} atest {} dtest {} | tex0 fmt {} {}x{} mag {} min {} "
                     "addr {:08X} | target {:08X} | lights {} luts {:06b} dist {:08b} spot {:08b} | fs {:016X}",
                     i, draw_us(i), samples[i], total ? 100.0 * samples[i] / total : 0.0, x.rect_w, x.rect_h,
                     x.vertices, x.tex_mask, x.fog, x.lighting, x.blend, x.alpha_test,
                     x.depth_test, x.tex0_fmt, x.tex0_w, x.tex0_h, x.tex0_mag, x.tex0_min,
                     x.tex0_addr, x.color_addr, x.lights, x.luts, x.dist_atten_mask,
                     x.spot_atten_mask, x.fs_hash);
        }
    }
    if (!Requested()) return;
    if (!d.pool) {
        d.device = device;
        d.precise = precise;
        d.pool = device.createQueryPool(vk::QueryPoolCreateInfo{
            .queryType = vk::QueryType::eOcclusion,
            .queryCount = State::kMax,
        });
        d.tpool = device.createQueryPool(vk::QueryPoolCreateInfo{
            .queryType = vk::QueryType::eTimestamp,
            .queryCount = State::kMax + 1,
        });
    }
    d.draws.clear();
    d.events.clear();
    d.empty_presents = 0;
    scheduler.Record([pool = d.pool, tpool = d.tpool](vk::CommandBuffer cmdbuf) {
        cmdbuf.resetQueryPool(pool, 0, State::kMax);
        cmdbuf.resetQueryPool(tpool, 0, State::kMax + 1);
    });
    d.active = true;
    LOG_INFO(Render_Vulkan, "draw dump armed for the next frame (precise {})", precise);
}

// Registers the draw; the query itself is opened and closed around the draw
// command (QueryAround), so a submit in between (a stream buffer wait) can never
// split it across command buffers. Returns the draw index or -1.
inline int BeginDraw(Scheduler&, const DrawInfo& info) {
    if (!d.active || d.draws.size() >= State::kMax) return -1;
    const int idx = static_cast<int>(d.draws.size());
    d.draws.push_back(info);
    d.events.push_back({idx, {}});
    return idx;
}

// Called by the draw command's own recorded lambda.
inline void QueryBegin(vk::CommandBuffer cmdbuf, vk::QueryPool pool, int idx, bool precise) {
    if (idx == 0) {
        cmdbuf.writeTimestamp(vk::PipelineStageFlagBits::eBottomOfPipe, d.tpool, 0);
    }
    if (idx >= 0) {
        cmdbuf.beginQuery(pool, idx,
                          precise ? vk::QueryControlFlagBits::ePrecise : vk::QueryControlFlags{});
    }
}
inline void QueryEnd(vk::CommandBuffer cmdbuf, vk::QueryPool pool, int idx) {
    if (idx >= 0) {
        cmdbuf.endQuery(pool, idx);
        cmdbuf.writeTimestamp(vk::PipelineStageFlagBits::eBottomOfPipe, d.tpool, idx + 1);
    }
}

inline void Note(std::string text) {
    if (d.active && d.events.size() < 4096) d.events.push_back({-1, std::move(text)});
}

inline void EndDraw(Scheduler&, int) {}

} // namespace Vulkan::GammaDump
