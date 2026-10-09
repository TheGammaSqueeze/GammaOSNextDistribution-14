// Copyright 2024 Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include "common/settings.h"
#include "right_eye_disabler.h"
#include "video_core/gpu.h"
#include "video_core/gpu_impl.h"
#include "video_core/renderer_vulkan/vk_gammaos_stats.h"

namespace VideoCore {
bool RightEyeDisabler::ShouldAllowCmdQueueTrigger(PAddr addr, u32 size) {
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

    auto guess = gpu.impl->pica.GuessCmdRenderProperties(addr, size);
    if (guess.vp_height == top_screen_size) GS::Add(GS::CmdListsTop);
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
bool RightEyeDisabler::ShouldAllowDisplayTransfer(PAddr src_address, size_t size) {
    namespace GS = Vulkan::GammaStats;
    GS::Add(GS::Transfers);
    if (!enabled || !enable_for_frame)
        return true;

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
