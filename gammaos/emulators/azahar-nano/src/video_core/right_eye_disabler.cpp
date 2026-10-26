// Copyright 2024 Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <sys/system_properties.h>
#include "common/settings.h"
#include "right_eye_disabler.h"
#include "video_core/gpu.h"
#include "video_core/gpu_impl.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"

namespace VideoCore {
bool RightEyeDisabler::ShouldAllowCmdQueueTrigger(PAddr addr, u32 size, const u32* words) {
    namespace GS = Vulkan::GammaStats;
    GS::Add(GS::CmdLists);
    if (!enabled || !enable_for_frame)
        return true;

    constexpr u32 top_screen_size = 0x00469000;

    if (report_end_frame_pending) {
        ReportEndFrame();
        report_end_frame_pending = false;
    }
    cmd_queue_trigger_happened = true;

    auto guess = gpu.impl->pica.GuessCmdRenderProperties(addr, size, words);
    if (guess.vp_height == top_screen_size) GS::Add(GS::CmdListsTop);
    // GammaOS: a title without right-eye framebuffers renders no right eye: nothing to skip,
    // and the timing rule would only ever cut the next frame's work.
    if (!TitleHasRightEye()) {
        cmd_trigger_blocked = false;
        return true;
    }
    // GammaOS: distinct eye targets (Castlevania: Mirror of Fate). The right transfers are cut
    // (never shown); the right lists are rendered: cutting them drops this title from 60 fps at
    // full speed to 37 fps at 63 percent (the game then runs 2.6 times the lists per shown
    // frame and doubles its texture copies). persist.gammaos.azahar_nano.eye_lists=1 cuts them
    // anyway, for experiments.
    if (DistinctRightTarget()) {
        static const bool cut_lists = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.eye_lists", v);
            return v[0] == '1';
        }();
        const bool right = cut_lists && guess.vp_height == top_screen_size &&
                           guess.paddr == right_rt &&
                           gpu.impl->pica.CmdListOnlyUsesViewportHeight(addr, size, top_screen_size);
        cmd_trigger_blocked = right;
        if (right) {
            GS::Add(GS::CmdListsBlocked);
            return false;
        }
        return true;
    }
    // GammaOS: targets that swap eyes between frames: no list can be told apart, and the timing
    // rule would cut a left eye now and then. Render everything; the right transfers are cut.
    if (TargetsAlternate()) {
        cmd_trigger_blocked = false;
        return true;
    }
    if (guess.vp_height == top_screen_size && !top_screen_blocked) {
        if (top_screen_buf == 0) {
            top_screen_buf = guess.paddr;
        }
        top_screen_drawn = true;
        // GammaOS: only block a list that renders nothing but top screen sized passes.
        if (top_screen_transfered &&
            gpu.impl->pica.CmdListOnlyUsesViewportHeight(addr, size, top_screen_size)) {
            cmd_trigger_blocked = true;
            GS::Add(GS::CmdListsBlocked);
            return false;
        }
    }

    cmd_trigger_blocked = false;
    return true;
}
bool RightEyeDisabler::RightFramebuffer(PAddr dst) const {
    const auto& fb = gpu.impl->pica.regs.framebuffer_config[0];
    return fb.address_right1 != 0 && fb.address_right1 != fb.address_left1 &&
           (dst == fb.address_right1 || dst == fb.address_right2);
}

bool RightEyeDisabler::TitleHasRightEye() const {
    const auto& fb = gpu.impl->pica.regs.framebuffer_config[0];
    return fb.address_right1 != 0 && fb.address_right1 != fb.address_left1;
}

bool RightEyeDisabler::ShouldAllowDisplayTransfer(PAddr src_address, PAddr dst_address,
                                                  size_t size) {
    namespace GS = Vulkan::GammaStats;
    GS::Add(GS::Transfers);
    if (!enabled || !enable_for_frame)
        return true;
    // GammaOS: learn which target feeds which eye, and whether the right one also feeds the
    // left eye (double buffered targets).
    if (size >= 400) {
        const auto& fb = gpu.impl->pica.regs.framebuffer_config[0];
        if (dst_address == fb.address_left1 || dst_address == fb.address_left2) {
            if (right_rt != 0 && src_address == right_rt)
                right_as_left_frame = frame_no;
            left_rt = src_address;
        } else if (RightFramebuffer(dst_address)) {
            right_rt = src_address;
            right_seen_frame = frame_no;
        }
    }
    // A transfer into the right framebuffer is never shown: cut it whatever the target pattern.
    // The lists behind it are only cut when the target is unambiguous (see the trigger rule).
    if (size >= 400 && RightFramebuffer(dst_address)) {
        if (cmd_queue_trigger_happened)
            display_tranfer_happened = true;
        GS::Add(GS::TransfersBlocked);
        return false;
    }
    if (DistinctRightTarget() || TargetsAlternate()) {
        if (cmd_queue_trigger_happened)
            display_tranfer_happened = true;
        return true;
    }
    if (size >= 400 && !top_screen_blocked) {
        if (top_screen_drawn && src_address == top_screen_buf) {
            top_screen_transfered = true;
        }

        if (src_address == top_screen_buf && cmd_trigger_blocked) {
            top_screen_blocked = true;
            GS::Add(GS::TransfersBlocked);
            return false;
        }
    }

    if (cmd_queue_trigger_happened)
        display_tranfer_happened = true;
    return true;
}
void RightEyeDisabler::ReportEndFrame() {
    if (!enabled)
        return;
    frame_no++;
    enable_for_frame = Settings::values.disable_right_eye_render.GetValue();

    if (display_tranfer_happened) {
        top_screen_drawn = false;
        top_screen_transfered = false;
        top_screen_blocked = false;
        cmd_queue_trigger_happened = false;
        cmd_trigger_blocked = false;
        display_tranfer_happened = false;
        top_screen_buf = 0;
    } else {
        report_end_frame_pending = true;
    }
}
} // namespace VideoCore
