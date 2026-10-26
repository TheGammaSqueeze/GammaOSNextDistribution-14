// Copyright 2024-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "common/common_types.h"

namespace VideoCore {
class GPU;

class RightEyeDisabler {
public:
    RightEyeDisabler(GPU& gpu) : gpu{gpu} {}

    bool ShouldAllowCmdQueueTrigger(PAddr addr, u32 size, const u32* words = nullptr);
    bool ShouldAllowDisplayTransfer(PAddr src_address, PAddr dst_address, size_t size);

    void ReportEndFrame();

    void SetEnabled(bool enable) {
        enabled = enable;
    }

private:
    bool enabled = true;
    // Enabled by ReportEndFrame() once the user setting has been read.
    bool enable_for_frame = false;

    bool top_screen_drawn = false;
    bool top_screen_transfered = false;
    bool top_screen_blocked = false;
    bool cmd_trigger_blocked = false;
    PAddr top_screen_buf = 0;

    // GammaOS: the render targets the eyes are transferred from, learned from the transfers
    // into the left and right framebuffers. A title that renders each eye into its own
    // target (Castlevania: Mirror of Fate, both transferred at the end of its 50 fps frame)
    // has only the right one skipped; the timing rule below is for titles that render both
    // eyes through one target after the left transfer (Metroid: Samus Returns).
    PAddr left_rt = 0;
    PAddr right_rt = 0;
    u32 frame_no = 0;
    u32 right_seen_frame = 0;
    // The last frame in which the right target fed the LEFT eye: a title that double buffers
    // its targets (the cutscenes here) swaps which one is the right eye every frame, so its
    // lists cannot be told apart by target and are rendered; only its right transfers are cut.
    u32 right_as_left_frame = 0;
    bool DistinctRightTarget() const {
        return right_rt != 0 && left_rt != 0 && right_rt != left_rt &&
               frame_no - right_seen_frame <= 4 &&
               (right_as_left_frame == 0 || frame_no - right_as_left_frame > 8);
    }
    bool TargetsAlternate() const {
        return right_as_left_frame != 0 && frame_no - right_as_left_frame <= 8;
    }
    bool RightFramebuffer(PAddr dst) const;
    bool TitleHasRightEye() const;

    bool cmd_queue_trigger_happened = false;
    bool display_tranfer_happened = false;
    bool report_end_frame_pending = false;

    GPU& gpu;
};
} // namespace VideoCore