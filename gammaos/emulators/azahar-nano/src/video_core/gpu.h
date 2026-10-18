// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <functional>
#include <memory>
#include <vector>
#include <boost/serialization/access.hpp>

#include "core/hle/service/gsp/gsp_interrupt.h"
#include "video_core/gammaos_snapshot.h"

namespace Service::GSP {
struct Command;
struct FrameBufferInfo;
} // namespace Service::GSP

namespace Core {
class System;
}

namespace Pica {
class DebugContext;
class PicaCore;
struct RegsLcd;
union ColorFill;
} // namespace Pica

namespace Frontend {
class EmuWindow;
}

namespace Kernel {
class Process;
}

namespace VideoCore {

/// GammaOS async GPU: waits for the GPU worker before a CPU-side access to GPU state (the
/// rasterizer's flush/invalidate entry points call it). No-op when async GPU is off or when
/// called on the worker itself.
void AsyncGpuDrain();

/// Measured on hardware to be 2240568 timer cycles or 4481136 ARM11 cycles
constexpr u64 FRAME_TICKS = 4481136ull;

class GraphicsDebugger;
class RendererBase;
class RightEyeDisabler;

/**
 * The GPU class is the high level interface to the video_core for core services.
 */
class GPU {
public:
    explicit GPU(Core::System& system, Frontend::EmuWindow& emu_window,
                 Frontend::EmuWindow* secondary_window);
    ~GPU();

    /// Sets the function to call for signalling GSP interrupts.
    void SetInterruptHandler(Service::GSP::InterruptHandler handler);

    /// Notify rasterizer that any caches of the specified region should be flushed to Switch memory
    /// GammaOS: the CPU is about to read and/or write GPU memory (see Pica::DeadWork).
    void CpuAccess(PAddr addr, u32 size, bool read, bool write);

    void FlushRegion(PAddr addr, u32 size);

    /// Notify rasterizer that any caches of the specified region should be invalidated
    void InvalidateRegion(PAddr addr, u32 size);

    /// Flushes and invalidates all memory in the rasterizer cache and removes any leftover state.
    void ClearAll(bool flush);

    /// GammaOS: after a save state load rebuilt the emulated system around this GPU (see
    /// Core::System::Shutdown), attaches it to the new timing and drops the surface cache.
    void ResetForLoad();

    /// Executes the provided GSP command.
    void Execute(const Service::GSP::Command& command);

    /// Updates GPU display framebuffer configuration using the specified parameters.
    void SetBufferSwap(u32 screen_id, const Service::GSP::FrameBufferInfo& info);

    /// Sets the LCD color fill configuration for the top and bottom screens.
    void SetColorFill(const Pica::ColorFill& fill);

    /// Reads a word from the GPU virtual address.
    u32 ReadReg(VAddr addr);

    /// Writes the provided value to the GPU virtual address.
    void WriteReg(VAddr addr, u32 data);

    /// GammaOS: writes the mask bits of value to the register (WriteHWRegsWithMask), as one
    /// read-modify-write in the GPU's own order.
    void WriteRegMasked(VAddr addr, u32 value, u32 mask);

    /// Returns a mutable reference to the renderer.
    [[nodiscard]] VideoCore::RendererBase& Renderer();

    /// Returns a mutable reference to the PICA GPU.
    [[nodiscard]] Pica::PicaCore& PicaCore();

    /// Returns an immutable reference to the PICA GPU.
    [[nodiscard]] const Pica::PicaCore& PicaCore() const;

    /// Returns a mutable reference to the GSP command debugger.
    [[nodiscard]] GraphicsDebugger& Debugger();

    RightEyeDisabler& GetRightEyeDisabler() {
        return *right_eye_disabler;
    }

    void ApplyPerProgramSettings(u64 program_ID);

    /// Recreates the renderer (for GL context reset in libretro)
    void RecreateRenderer(Frontend::EmuWindow& emu_window, Frontend::EmuWindow* secondary_window);

    /// Releases the renderer (for GL context destroy in libretro)
    void ReleaseRenderer();

    /// GammaOS async GPU (persist.gammaos.azahar_nano.async_gpu): command lists are processed
    /// on a worker thread while the emulated CPU runs on. Everything else that touches GPU
    /// state first waits for the worker (AsyncDrain), and the interrupts the worker raises are
    /// delivered on the emulation thread (AsyncPoll, AsyncDrain).
    /// Waits until the worker is idle, then delivers its interrupts. No-op on the worker.
    void AsyncDrain(void* site = nullptr);
    /// Delivers the interrupts of command lists the worker has finished, without waiting.
    void AsyncPoll();
    /// Waits until the worker finished one more job (mode 2) or ran dry, then delivers its
    /// interrupts: for the run loop with every emulated thread waiting. True when it delivered
    /// an interrupt (a thread may be ready now: the emulated clock should not skip ahead).
    bool AsyncWaitProgress();
    /// True while command lists are queued or being processed.
    bool AsyncPending() const;
    /// GammaOS: the async command list worker is in use.
    bool AsyncActive() const;
    /// Mode 2 and called from the emulation thread: register writes and commands are queued.
    bool AsyncQueued() const;

private:
    void EagerSignal(Service::GSP::InterruptId id, u32 count = 1);
    struct CmdListCopy {
        PAddr addr;
        u32 size;
        std::vector<u32> words;
        // Lists this one starts (command buffer trigger inside the list), copied as well.
        std::vector<CmdListCopy> chained;
        // The vertex and index data every draw of this list (and its chains) reads, as it was
        // at submit (top-level copy only).
        GammaSnapshot::Snapshot data;
    };
    struct DrawRanges;
    CmdListCopy CopyCmdList(PAddr addr, u32 size, u32& irqs, u32 depth = 0,
                            DrawRanges* ranges = nullptr, u32* cb_parent = nullptr,
                            std::vector<PAddr>* visited = nullptr);
    void TakeSnapshot(CmdListCopy& copy, DrawRanges& ranges);
    const u32* CopyFor(PAddr addr, u32& size) const;
    const u32* ChainedCopyFor(PAddr addr, u32& size) const;
    CmdListCopy EagerRegWrite(u32 index, u32 data);
    void SyncShadow();
    void ExecuteCommand(const Service::GSP::Command& command,
                        std::shared_ptr<Kernel::Process> dma_process);
    void SetBufferSwapNow(u32 screen_id, const Service::GSP::FrameBufferInfo& info,
                          bool count_frame);
    u32 ReadRegNow(VAddr addr);
    void WriteRegNow(VAddr addr, u32 data);

    void SubmitCmdList(u32 index);

    // Interrupt index must be 0 or 1 to signal the relative PSC interrupt.
    void MemoryFill(u32 index, u32 intr_index);

    void MemoryTransfer();

    void RegisterVBlank();

    void VBlankCallback(uintptr_t user_data, s64 cycles_late);

    friend class boost::serialization::access;
    template <class Archive>
    void serialize(Archive& ar, const u32 file_version);

    std::unique_ptr<RightEyeDisabler> right_eye_disabler;
    struct Async;
    std::unique_ptr<Async> async;

private:
    friend class RightEyeDisabler;
    struct Impl;
    std::unique_ptr<Impl> impl;

    PAddr VirtualToPhysicalAddress(VAddr addr);
};

} // namespace VideoCore
