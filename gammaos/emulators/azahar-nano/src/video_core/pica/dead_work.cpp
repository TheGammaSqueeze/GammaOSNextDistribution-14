// Copyright 2026 GammaOS
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <algorithm>
#include <string>
#include <fmt/format.h>
#include <xxhash.h>
#include "common/gammaos_tweak.h"
#include "common/logging/log.h"
#include "common/settings.h"
#include "video_core/pica/dead_work.h"
#include "video_core/pica/regs_external.h"
#include "video_core/pica/regs_internal.h"
#ifdef ANDROID
#include <sys/system_properties.h>
#endif

namespace Pica {

namespace {

// Signature tokens for framebuffer addresses, which rotate from frame to frame.
constexpr u64 TOKEN_TOP_LEFT = 1ull << 40;
constexpr u64 TOKEN_TOP_RIGHT = 2ull << 40;
constexpr u64 TOKEN_BOTTOM = 3ull << 40;

constexpr u64 SIG_DRAW = 1, SIG_IMMEDIATE = 2, SIG_TRANSFER = 3, SIG_COPY = 4, SIG_FILL = 5,
              SIG_CPU_READ = 6;

constexpr u32 MAX_VIOLATIONS = 3;
// Frames of learning without a learned frame before resting, and the rest's length.
constexpr u32 LEARN_FRAMES = 300;
constexpr u32 DORMANT_FRAMES = 3600;

bool PropEnabled() {
#ifdef ANDROID
    static const bool on = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.dead_work", v);
        return v[0] != '0';
    }();
    return on;
#else
    return true;
#endif
}

template <typename T>
u64 Hash(u64 seed, const T& value) {
    return XXH3_64bits_withSeed(&value, sizeof(value), seed);
}

boost::icl::right_open_interval<u64, std::less> MakeRange(u64 addr, u64 size) {
    return boost::icl::right_open_interval<u64, std::less>(addr, addr + std::max<u64>(size, 1));
}

} // namespace

bool DeadWork::Enabled() const {
    return PropEnabled() && mode != Mode::Off && mode != Mode::Dormant &&
           Settings::values.disable_right_eye_render.GetValue() &&
           Settings::values.render_3d.GetValue() == Settings::StereoRenderOption::Off &&
           Settings::values.mono_render_option.GetValue() == Settings::MonoRenderOption::LeftEye;
}

u64 DeadWork::Normalize(PAddr addr) const {
    for (std::size_t i = 0; i < fb_addrs.size(); i += 2) {
        if (fb_addrs[i] == addr) {
            return fb_addrs[i + 1];
        }
    }
    return addr;
}

void DeadWork::AddTargets(const RegsInternal& regs, Op& op) {
    // A draw reads what it blends with and depth tests against. A buffer counts only when the
    // draw can touch it: games leave the scene's depth buffer bound for flat passes.
    const auto& fb = regs.framebuffer.framebuffer;
    const auto& om = regs.framebuffer.output_merger;
    const u64 area = static_cast<u64>(fb.GetWidth()) * fb.GetHeight();
    const bool shadow = regs.framebuffer.IsShadowRendering();
    const PAddr color = fb.GetColorBufferPhysicalAddress();
    const bool color_used = shadow || om.red_enable || om.green_enable || om.blue_enable ||
                            om.alpha_enable;
    if (color != 0 && color_used) {
        const auto r = MakeRange(color, area * FramebufferRegs::BytesPerColorPixel(fb.color_format));
        op.writes.push_back(r);
        op.reads.push_back(r);
    }
    const PAddr depth = fb.GetDepthBufferPhysicalAddress();
    const bool depth_used = !shadow && (om.depth_test_enable || om.depth_write_enable ||
                                        (regs.framebuffer.HasStencil() && om.stencil_test.enable));
    if (depth != 0 && depth_used) {
        const auto r = MakeRange(depth, area * FramebufferRegs::BytesPerDepthPixel(fb.depth_format));
        op.writes.push_back(r);
        op.reads.push_back(r);
    }
}

bool DeadWork::OnDraw(const RegsInternal& regs, u32 num_vertices, bool is_indexed) {
    if (!Enabled()) {
        return false;
    }
    const auto& fb = regs.framebuffer.framebuffer;
    const u64 area = static_cast<u64>(fb.GetWidth()) * fb.GetHeight();
    const PAddr color = fb.GetColorBufferPhysicalAddress();
    const PAddr depth = fb.GetDepthBufferPhysicalAddress();
    Op op{};
    u64 sig = Hash(SIG_DRAW, num_vertices | (is_indexed ? 1ull << 32 : 0));
    sig = Hash(sig, Normalize(color));
    sig = Hash(sig, Normalize(depth));
    sig = Hash(sig, area);
    AddTargets(regs, op);
    // The vertex arrays (and the index array) are read from memory. An indexed draw may reach
    // any vertex an index can name; every loader's range is taken up to that bound.
    {
        const auto& va = regs.pipeline.vertex_attributes;
        const PAddr base = va.GetPhysicalBaseAddress();
        const u64 count = is_indexed ? 65536 : static_cast<u64>(num_vertices);
        for (u32 i = 0; i < va.GetNumTotalAttributes() && i < 12; i++) {
            const auto& loader = va.attribute_loaders[i];
            if (loader.component_count == 0 || loader.byte_count == 0) {
                continue;
            }
            op.reads.push_back(MakeRange(base + loader.data_offset,
                                         static_cast<u64>(loader.byte_count) * count));
        }
        if (is_indexed) {
            op.reads.push_back(MakeRange(base + regs.pipeline.index_array.offset,
                                         static_cast<u64>(num_vertices) * 2));
        }
    }
    const auto& tex = regs.texturing;
    for (const auto& t : tex.GetTextures()) {
        if (!t.enabled) {
            continue;
        }
        // Over-estimated: 8 bytes a texel covers every format and its mipmaps.
        const u64 size = static_cast<u64>(t.config.width) * t.config.height * 8;
        const PAddr addr = t.config.GetPhysicalAddress();
        op.reads.push_back(MakeRange(addr, size));
        sig = Hash(sig, addr);
    }
    if (tex.main_config.texture0_enable &&
        (tex.texture0.type == TexturingRegs::TextureConfig::TextureCube ||
         tex.texture0.type == TexturingRegs::TextureConfig::ShadowCube)) {
        const u64 size = static_cast<u64>(tex.texture0.width) * tex.texture0.height * 8;
        for (u32 face = 1; face < 6; face++) {
            const PAddr addr =
                tex.GetCubePhysicalAddress(static_cast<TexturingRegs::CubeFace>(face));
            op.reads.push_back(MakeRange(addr, size));
            sig = Hash(sig, addr);
        }
    }
    op.signature = sig;
    op.kind = 'D';
    return Process(std::move(op));
}

void DeadWork::OnImmediateDraw(const RegsInternal& regs) {
    if (!Enabled()) {
        return;
    }
    // Recorded like a draw that is never skipped, once per run of immediate triangles.
    const auto& fb = regs.framebuffer.framebuffer;
    const PAddr color = fb.GetColorBufferPhysicalAddress();
    const u64 sig = Hash(Hash(SIG_IMMEDIATE, Normalize(color)),
                         Normalize(fb.GetDepthBufferPhysicalAddress()));
    if (!current_sigs.empty() && current_sigs.back() == sig) {
        return;
    }
    Op op{};
    AddTargets(regs, op);
    for (const auto& t : regs.texturing.GetTextures()) {
        if (t.enabled) {
            op.reads.push_back(MakeRange(t.config.GetPhysicalAddress(),
                                         static_cast<u64>(t.config.width) * t.config.height * 8));
        }
    }
    op.signature = sig;
    op.kind = 'I';
    op.always_live = true;
    Process(std::move(op));
}

bool DeadWork::OnTransfer(const DisplayTransferConfig& config) {
    if (!Enabled()) {
        return false;
    }
    Op op{};
    const PAddr in = config.GetPhysicalInputAddress();
    const PAddr out = config.GetPhysicalOutputAddress();
    u64 sig;
    if (config.is_texture_copy) {
        const u64 size = config.texture_copy.size & ~0xFu;
        const u64 in_w = config.texture_copy.input_width * 16ull;
        const u64 in_gap = config.texture_copy.input_gap * 16ull;
        const u64 out_w = config.texture_copy.output_width * 16ull;
        const u64 out_gap = config.texture_copy.output_gap * 16ull;
        const bool in_packed = in_gap == 0 || in_w == 0 || in_w >= size;
        const bool out_packed = out_gap == 0 || out_w == 0 || out_w >= size;
        const u64 in_span = in_packed ? size : (size + in_w - 1) / in_w * (in_w + in_gap);
        const u64 out_span = out_packed ? size : (size + out_w - 1) / out_w * (out_w + out_gap);
        op.reads.push_back(MakeRange(in, in_span));
        if (out_packed) {
            op.kills.push_back(MakeRange(out, size));
        } else if (size / out_w <= 4096) {
            // With gaps only the lines are written; the bytes between them keep their content.
            for (u64 done = 0; done < size; done += out_w) {
                const u64 line = done / out_w;
                op.kills.push_back(
                    MakeRange(out + line * (out_w + out_gap), std::min(out_w, size - done)));
            }
        }
        // A copy writes exactly the bytes it overwrites (whole span if the lines are too many
        // to list, which only keeps more work).
        if (op.kills.empty()) {
            op.writes.push_back(MakeRange(out, out_span));
        } else {
            op.writes = op.kills;
        }
        sig = Hash(Hash(Hash(SIG_COPY, Normalize(in)), Normalize(out)),
                   (size << 32) ^ (config.texture_copy.input_size) ^
                       (static_cast<u64>(config.texture_copy.output_size) << 16));
    } else {
        const u64 in_size = static_cast<u64>(config.input_width.Value()) *
                            config.input_height.Value() * BytesPerPixel(config.input_format);
        const u64 out_size = static_cast<u64>(config.output_width.Value()) *
                             config.output_height.Value() * BytesPerPixel(config.output_format);
        op.reads.push_back(MakeRange(in, in_size));
        op.writes.push_back(MakeRange(out, out_size));
        op.kills.push_back(MakeRange(out, out_size));
        sig = Hash(Hash(Hash(SIG_TRANSFER, Normalize(in)), Normalize(out)),
                   (in_size << 32) ^ out_size ^ (static_cast<u64>(config.flags) << 20));
    }
    op.signature = sig;
    op.kind = config.is_texture_copy ? 'C' : 'T';
    return Process(std::move(op));
}

bool DeadWork::OnFill(const MemoryFillConfig& config) {
    if (!Enabled() || config.GetStartAddress() == 0) {
        return false;
    }
    Op op{};
    const PAddr start = config.GetStartAddress();
    const PAddr end = config.GetEndAddress();
    op.writes.push_back(MakeRange(start, end > start ? end - start : 1));
    op.kills.push_back(op.writes.back());
    op.signature = Hash(Hash(SIG_FILL, Normalize(start)), static_cast<u64>(end - start));
    op.kind = 'F';
    return Process(std::move(op));
}

bool DeadWork::WrittenRecently(const Range& r) const {
    for (const std::vector<Op>* ops : {&current, &previous}) {
        for (const Op& op : *ops) {
            for (const auto& w : op.writes) {
                if (boost::icl::intersects(w, r)) {
                    return true;
                }
            }
        }
    }
    return false;
}

void DeadWork::OnCpuRead(PAddr addr, u32 size) {
    if (!Enabled()) {
        return;
    }
    const auto r = MakeRange(addr, size);
    if (!boost::icl::intersects(stale, r) && !WrittenRecently(r)) {
        return;
    }
    // A CPU read of GPU written memory is part of the sequence: what it reads stays live, and
    // a read that is not part of the learned frame is a divergence.
    Op op{};
    op.kind = 'R';
    op.always_live = true;
    op.reads.push_back(r);
    op.signature = Hash(Hash(SIG_CPU_READ, static_cast<u64>(addr)), static_cast<u64>(size));
    if (!current_sigs.empty() && current_sigs.back() == op.signature) {
        return; // the same read again (block reads come in pieces)
    }
    if (boost::icl::intersects(stale, r)) {
        Violation("CPU read", r);
    }
    Process(std::move(op));
}

void DeadWork::OnCpuWrite(PAddr addr, u32 size) {
    if (!stale.empty()) {
        // What the CPU writes no longer holds the result of skipped work.
        stale -= MakeRange(addr, size);
    }
}

void DeadWork::Violation(const char* what, const Range& range) {
    ++violations;
    repeats_needed *= 2;
    LOG_WARNING(HW_GPU, "dead work: {} of {:08X}..{:08X} needs skipped work, relearning ({} of {}, "
                "after {} equal frames)", what, range.lower(), range.upper(), violations,
                MAX_VIOLATIONS, repeats_needed);
    // The stale memory stays known: what reads it later carries the taint on.
    mode = violations >= MAX_VIOLATIONS ? Mode::Off : Mode::Learn;
    repeats = 0;
    candidate_sigs.clear();
    diverged = true;
    if (mode == Mode::Off) {
        LOG_WARNING(HW_GPU, "dead work: off for this session");
    }
}

bool DeadWork::Process(Op&& op) {
    frame_has_ops = true;
    bool dead = false;
    if (mode == Mode::Skip && !diverged) {
        if (cursor < learned_sigs.size() && learned_sigs[cursor] == op.signature) {
            dead = learned_dead[cursor] && !op.always_live;
            ++cursor;
            // The displayed framebuffers may have moved since the frame was learned.
            for (const auto& w : op.writes) {
                if (dead && boost::icl::intersects(roots, w)) {
                    dead = false;
                    diverged = true;
                }
            }
        } else {
            diverged = true;
        }
    }
    if (dead) {
        for (const auto& w : op.writes) {
            stale += w;
        }
        ++skipped_ops;
    } else if (!stale.empty()) {
        // Memory that skipped work would have written is "stale". An operation reading it
        // passes the staleness on to what it writes; that is harmless while it only reaches
        // the right eye or gets overwritten, and an error once it reaches a displayed
        // framebuffer (checked here) or the CPU (OnCpuRead).
        bool reads_stale = false;
        for (const auto& r : op.reads) {
            if (boost::icl::intersects(stale, r)) {
                reads_stale = true;
                break;
            }
        }
        for (const auto& k : op.kills) {
            stale -= k;
        }
        if (reads_stale) {
            bool shown = false;
            for (const auto& w : op.writes) {
                shown = shown || boost::icl::intersects(roots, w);
                stale += w;
            }
            if (shown && !frame_tainted) {
                // The frame is not shown (the renderer repeats the previous one).
                frame_tainted = true;
                LOG_WARNING(HW_GPU, "dead work: op {} ({}) shows skipped work, learned position "
                            "{}, {}; the frame is held back", current.size(), op.kind, cursor,
                            diverged ? "after a divergence" : "in sequence");
                Violation("display", op.writes.front());
            }
        }
    }
    current_sigs.push_back(op.signature);
    current.push_back(std::move(op));
    return dead;
}

void DeadWork::Analyze() {
    const std::size_t n = current.size();
    learned_dead.assign(n, false);
    RangeSet live;
    live += Range(0, 1ull << 33);
    live -= right;
    live += roots;
    std::size_t dead_count = 0;
    // Two copies of the frame: the later one only carries liveness back to the frame
    // boundary (work overwritten early in the next frame is not needed by it).
    for (int pass = 0; pass < 2; pass++) {
        for (std::size_t i = n; i-- > 0;) {
            const Op& op = current[i];
            bool needed = op.always_live;
            for (const auto& w : op.writes) {
                if (needed) {
                    break;
                }
                needed = boost::icl::intersects(live, w) || boost::icl::intersects(roots, w);
            }
            if (pass == 1 && !needed) {
                learned_dead[i] = true;
                ++dead_count;
            }
            if (!needed) {
                continue;
            }
            if (!op.kills.empty()) {
                for (const auto& k : op.kills) {
                    live -= k;
                }
                live += roots;
            }
            for (const auto& r : op.reads) {
                live += r;
            }
        }
    }
    learned_sigs = current_sigs;
    // GammaOS: following a learned frame costs about as much per operation as learning it
    // (Kirby: Planet Robobot relearns every few frames to skip 1 of 349 operations, 2.5 percent
    // of the emulation thread on the RG DS). Below one operation in 32 the skips do not pay for
    // the tracking: treat the frame as nothing learned, so the tracker rests after the learning
    // window. persist.gammaos.azahar_nano.dead_work_min=0 skips any amount; default Mali only.
    static const bool min_gain =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.dead_work_min");
    if (dead_count == 0 || (min_gain && dead_count * 32 < n)) {
        return;
    }
    mode = Mode::Skip;
    LOG_INFO(HW_GPU, "dead work: frame of {} operations learned, {} never seen are skipped", n,
             dead_count);
#ifdef ANDROID
    static const bool trace_log = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.dead_work_log", v);
        return v[0] == '1';
    }();
    static bool traced = false;
    if (trace_log && !traced && n > 300) {
        traced = true;
        const auto fmt_ranges = [](const auto& rs) {
            std::string out;
            for (std::size_t k = 0; k < rs.size() && k < 4; k++) {
                out += fmt::format(" {:08X}+{:X}", rs[k].lower(), rs[k].upper() - rs[k].lower());
            }
            if (rs.size() > 4) {
                out += fmt::format(" ..({})", rs.size());
            }
            return out;
        };
        for (std::size_t i = 0; i < n; i++) {
            const Op& op = current[i];
            LOG_INFO(HW_GPU, "dead work trace {:3} {} {} | w{} | k{} | r{}", i, op.kind,
                     learned_dead[i] ? "DEAD" : "live", fmt_ranges(op.writes),
                     fmt_ranges(op.kills), fmt_ranges(op.reads));
        }
    }
#endif
    static u32 detail_logs = 0;
    if (detail_logs < 4) {
        ++detail_logs;
        for (std::size_t i = 0; i < n; i++) {
            if (learned_dead[i] && !current[i].writes.empty()) {
                LOG_INFO(HW_GPU, "dead work:   op {} writes {:08X}..{:08X}", i,
                         current[i].writes[0].lower(), current[i].writes[0].upper());
            }
        }
    }
}

void DeadWork::OnVBlank(const FramebufferConfig& top, const FramebufferConfig& bottom) {
    if (mode == Mode::Dormant) {
        present_tainted = false;
        if (++dormant_frames >= DORMANT_FRAMES) {
            mode = Mode::Learn;
            learn_frames = 0;
            repeats = 0;
            candidate_sigs.clear();
        }
        return;
    }
    if (!Enabled()) {
        if (mode == Mode::Skip) {
            mode = Mode::Learn;
        }
        stale.clear();
        current.clear();
        previous.clear();
        current_sigs.clear();
        frame_has_ops = false;
        present_tainted = frame_tainted;
        frame_tainted = false;
        return;
    }
    // Displayed framebuffers stay live; right eye framebuffers are never displayed.
    recent_index = (recent_index + 1) % recent_roots.size();
    recent_roots[recent_index].clear();
    recent_right[recent_index].clear();
    const auto note = [this](PAddr addr, u64 size, u64 token, RangeSet& set) {
        if (addr == 0) {
            return;
        }
        set += MakeRange(addr, size);
        if (fb_addrs.size() < 128 &&
            std::find(fb_addrs.begin(), fb_addrs.end(), addr) == fb_addrs.end()) {
            fb_addrs.push_back(addr);
            fb_addrs.push_back(token);
        }
    };
    const u64 top_size = static_cast<u64>(top.stride) * top.height;
    const u64 bottom_size = static_cast<u64>(bottom.stride) * bottom.height;
    note(top.address_left1, top_size, TOKEN_TOP_LEFT, recent_roots[recent_index]);
    note(top.address_left2, top_size, TOKEN_TOP_LEFT, recent_roots[recent_index]);
    note(bottom.address_left1, bottom_size, TOKEN_BOTTOM, recent_roots[recent_index]);
    note(bottom.address_left2, bottom_size, TOKEN_BOTTOM, recent_roots[recent_index]);
    note(top.address_right1, top_size, TOKEN_TOP_RIGHT, recent_right[recent_index]);
    note(top.address_right2, top_size, TOKEN_TOP_RIGHT, recent_right[recent_index]);
    roots.clear();
    right.clear();
    for (std::size_t i = 0; i < recent_roots.size(); i++) {
        roots += recent_roots[i];
        right += recent_right[i];
    }
    right -= roots;

    if (!frame_has_ops) {
        return;
    }
    frame_has_ops = false;
    if (++frames % 1800 == 0 && skipped_ops != 0) {
        LOG_INFO(HW_GPU, "dead work: {} operations skipped in the last {} frames", skipped_ops,
                 1800);
        skipped_ops = 0;
    }
    if (mode == Mode::Skip) {
        if (diverged || cursor != learned_sigs.size()) {
            LOG_INFO(HW_GPU, "dead work: the frame changed ({} of {} operations matched, {} in "
                     "this frame), relearning", cursor, learned_sigs.size(), current.size());
            mode = Mode::Learn;
            repeats = 0;
        }
    }
    if (mode == Mode::Learn) {
        if (current_sigs == candidate_sigs) {
            ++repeats;
        } else {
            repeats = 0;
            candidate_sigs = current_sigs;
        }
        if (repeats + 1 >= repeats_needed) {
            Analyze();
            repeats = 0;
            candidate_sigs.clear();
        }
        if (mode == Mode::Skip) {
            learn_frames = 0;
        } else if (++learn_frames >= LEARN_FRAMES) {
            // Nothing to skip in this scene: rest, the frames cost nothing meanwhile.
            mode = Mode::Dormant;
            dormant_frames = 0;
            stale.clear();
            previous.clear();
            LOG_INFO(HW_GPU, "dead work: nothing learned in {} frames, resting for {}",
                     LEARN_FRAMES, DORMANT_FRAMES);
        }
    }
    std::swap(previous, current);
    current.clear();
    current_sigs.clear();
    cursor = 0;
    diverged = false;
    present_tainted = frame_tainted;
    frame_tainted = false;
}

} // namespace Pica
