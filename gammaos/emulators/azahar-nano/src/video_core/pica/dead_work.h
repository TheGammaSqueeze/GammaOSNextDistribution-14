// Copyright 2026 GammaOS
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <array>
#include <utility>
#include <vector>
#include <boost/container/small_vector.hpp>
#include <boost/icl/interval_set.hpp>
#include "common/common_types.h"

namespace Pica {

struct RegsInternal;
struct DisplayTransferConfig;
struct MemoryFillConfig;
struct FramebufferConfig;

/**
 * GammaOS: leaves out GPU work whose result is never seen.
 *
 * With 3D off the right eye image is never displayed, yet games that render both eyes draw
 * by draw (Metroid: Samus Returns) still run whole post-processing chains whose only consumer
 * is the right eye framebuffer. Which work that is cannot be told from one operation, so this
 * learns the frame: it records every draw, transfer and fill between two vblanks as the
 * memory ranges it reads and writes. Once the same sequence repeats, a backward dataflow pass
 * over it (two frames, so that work of one frame overwritten early in the next counts) marks
 * the operations whose output is overwritten or only reaches the right eye framebuffer before
 * anything reads it. While the following frames repeat the sequence those operations are
 * skipped. The displayed framebuffers are always kept, reads are over-estimated and only
 * complete overwrites by transfers and fills end a range's life, so the analysis errs towards
 * keeping work. Any divergence stops skipping for the rest of the frame, and a read of memory
 * a skipped operation would have written (a sequence change, or a CPU read) relearns, and
 * turns the feature off for the session after three of them.
 *
 * Only active while the right eye is not displayed (Disable Right Eye Rendering on, 3D off,
 * mono left eye). persist.gammaos.azahar_nano.dead_work=0 turns it off.
 */
class DeadWork {
public:
    /// Called for a DrawArrays draw. Returns true when the draw is to be skipped.
    bool OnDraw(const RegsInternal& regs, u32 num_vertices, bool is_indexed);
    /// Called for an immediate mode triangle (always performed).
    void OnImmediateDraw(const RegsInternal& regs);
    /// Called for a display transfer or texture copy. Returns true when it is to be skipped.
    bool OnTransfer(const DisplayTransferConfig& config);
    /// Called for a memory fill. Returns true when it is to be skipped.
    bool OnFill(const MemoryFillConfig& config);
    /// Called when the CPU reads GPU written memory.
    void OnCpuRead(PAddr addr, u32 size);
    /// Called when the CPU writes memory the GPU may hold.
    void OnCpuWrite(PAddr addr, u32 size);
    /// Called at every vblank.
    void OnVBlank(const FramebufferConfig& top, const FramebufferConfig& bottom);
    /// Whether the frame about to be presented shows the result of skipped work (the
    /// sequence changed after the skip). The renderer keeps the previous frame on the panels.
    bool TakePresentTainted() {
        return std::exchange(present_tainted, false);
    }

private:
    using Range = boost::icl::right_open_interval<u64, std::less>;
    bool WrittenRecently(const Range& r) const;
    using RangeSet = boost::icl::interval_set<u64, std::less, Range>;

    struct Op {
        u64 signature;
        char kind;                   ///< D draw, I immediate, T transfer, C copy, F fill
        boost::container::small_vector<Range, 6> reads;
        boost::container::small_vector<Range, 2> writes; ///< every byte it may write
        boost::container::small_vector<Range, 2> kills;  ///< bytes it certainly overwrites
        bool always_live;    ///< never skipped (immediate mode draws)
    };

    bool Enabled() const;
    void AddTargets(const RegsInternal& regs, Op& op);
    bool Process(Op&& op);
    void Violation(const char* what, const Range& range);
    void Analyze();
    u64 Normalize(PAddr addr) const;

    /// Dormant: nothing learned for a while, the tracker rests (no cost) before trying again.
    enum class Mode { Learn, Skip, Dormant, Off };
    Mode mode = Mode::Learn;

    std::vector<Op> current;          ///< this frame's operations
    std::vector<Op> previous;         ///< the previous frame's operations
    u32 learn_frames = 0;             ///< frames spent learning since the last success
    u32 dormant_frames = 0;
    std::vector<u64> current_sigs;    ///< this frame's signatures
    std::vector<u64> candidate_sigs;  ///< the previous frame's signatures
    u32 repeats = 0;                  ///< frames in a row equal to the candidate
    u32 repeats_needed = 3;           ///< doubled after every violation

    std::vector<u64> learned_sigs;    ///< the learned frame (Skip mode)
    std::vector<bool> learned_dead;
    std::size_t cursor = 0;           ///< position in the learned frame
    bool diverged = false;            ///< this frame left the learned sequence
    bool frame_has_ops = false;

    RangeSet stale;                   ///< memory that skipped work would have written
    bool frame_tainted = false;       ///< a displayed framebuffer took skipped work this frame
    bool present_tainted = false;
    RangeSet roots;                   ///< displayed framebuffers (always live)
    RangeSet right;                   ///< right eye framebuffers (never displayed)
    /// Framebuffer ranges of the last vblanks (games rotate through more buffers than the
    /// two the configuration names at a time).
    std::array<RangeSet, 8> recent_roots;
    std::array<RangeSet, 8> recent_right;
    std::size_t recent_index = 0;
    std::vector<PAddr> fb_addrs;      ///< every framebuffer address seen, for signatures

    u32 violations = 0;
    u64 skipped_ops = 0;
    u64 frames = 0;
};

} // namespace Pica
