// Copyright 2023 Citra Emulator Project
// Copyright 2024 Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include "common/archives.h"
#include "common/microprofile.h"
#include "core/core.h"
#include "core/core_timing.h"
#include "core/hle/service/gsp/gsp_gpu.h"
#include "core/hle/service/plgldr/plgldr.h"
#include "video_core/debug_utils/debug_utils.h"
#include "video_core/gpu.h"
#include "video_core/gpu_debugger.h"
#include "video_core/gpu_impl.h"
#include "video_core/pica/pica_core.h"
#include "video_core/pica/regs_lcd.h"
#include "video_core/renderer_base.h"
#include "video_core/renderer_software/sw_blitter.h"
#include "video_core/right_eye_disabler.h"
#include "video_core/video_core.h"

namespace VideoCore {
struct GPU::Impl {
    Core::System& system;
    Memory::MemorySystem& memory;
    std::shared_ptr<Pica::DebugContext> debug_context;
    Pica::PicaCore pica;
    /// GammaOS GPU worker split (persist.gammaos.azahar_nano.gpu_split): the core the draw
    /// stage draws from; the walker mirrors pica's state into it at every draw.
    std::unique_ptr<Pica::PicaCore> pica_draw;
    GraphicsDebugger gpu_debugger;
    std::unique_ptr<RendererBase> renderer;
    RasterizerInterface* rasterizer;
    std::unique_ptr<SwRenderer::SwBlitter> sw_blitter;
    Core::TimingEventType* vblank_event;
    Core::TimingEventType* eager_event = nullptr;
    Service::GSP::InterruptHandler signal_interrupt;
    /// GammaOS async GPU mode 3: the copy of the command list the worker's current job runs.
    const GPU::CmdListCopy* cmd_copy = nullptr;

    explicit Impl(Core::System& system, Frontend::EmuWindow& emu_window,
                  Frontend::EmuWindow* secondary_window)
        : system{system}, memory{system.Memory()},
          debug_context{Pica::g_debug_context}, pica{memory, debug_context},
          pica_draw{GPU::SplitWanted() ? std::make_unique<Pica::PicaCore>(memory, debug_context)
                                       : nullptr},
          renderer{VideoCore::CreateRenderer(emu_window, secondary_window, pica, system,
                                             pica_draw.get())},
          rasterizer{renderer->Rasterizer()},
          sw_blitter{std::make_unique<SwRenderer::SwBlitter>(memory, rasterizer)} {}
    ~Impl() = default;

    /// GammaOS: looked up each time; a save state load rebuilds the timing but keeps the GPU.
    Core::Timing& Timing() {
        return system.CoreTiming();
    }
};
} // namespace VideoCore