#include <cerrno>
#include <cstdio>
#include <sched.h>
#include <unistd.h>
#include <sys/system_properties.h>
// Copyright 2014-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <stdexcept>
#include <utility>
#include <boost/serialization/array.hpp>
#include "audio_core/dsp_interface.h"
#include "audio_core/hle/hle.h"
#include "audio_core/lle/lle.h"
#include "common/arch.h"
#include "common/gammaos_tweak.h"
#include "common/logging/log.h"
#include "common/scope_exit.h"
#include "common/settings.h"
#include "core/arm/arm_interface.h"
#include "core/arm/exclusive_monitor.h"
#include "core/hle/service/cam/cam.h"
#include "core/hle/service/hid/hid.h"
#include "core/hle/service/ir/ir_user.h"
#if CITRA_ARCH(x86_64) || CITRA_ARCH(arm64)
#include "core/arm/dynarmic/arm_dynarmic.h"
#endif
#include "core/arm/dyncom/arm_dyncom.h"
#include "core/cheats/cheats.h"
#include "core/core.h"
#include "core/arm/dynarmic/arm_dynarmic.h"
#include <algorithm>
#include <array>
#include <chrono>
#include "core/core_timing.h"
#include "core/dumping/backend.h"
#include "core/file_sys/ncch_container.h"
#include "core/frontend/image_interface.h"
#ifdef ENABLE_GDBSTUB
#include "core/gdbstub/gdbstub.h"
#endif
#include "core/global.h"
#include "core/hle/kernel/ipc_debugger/recorder.h"
#include "core/hle/kernel/kernel.h"
#include "core/hle/kernel/process.h"
#include "core/hle/kernel/thread.h"
#include "core/hle/service/apt/applet_manager.h"
#include "core/hle/service/apt/apt.h"
#include "core/hle/service/cam/cam.h"
#include "core/hle/service/fs/archive.h"
#include "core/hle/service/gsp/gsp.h"
#include "core/hle/service/gsp/gsp_gpu.h"
#include "core/hle/service/ir/ir_rst.h"
#include "core/hle/service/mic/mic_u.h"
#include "core/hle/service/plgldr/plgldr.h"
#include "core/hle/service/service.h"
#include "core/hle/service/sm/sm.h"
#include "core/hw/aes/key.h"
#include "core/loader/loader.h"
#include "core/movie.h"
#include "core/savestate.h"
#ifdef ENABLE_SCRIPTING
#include "core/rpc/server.h"
#endif
#include "network/network.h"
#include "video_core/custom_textures/custom_tex_manager.h"
#include "video_core/gpu.h"
#include "video_core/renderer_base.h"

namespace Core {

/*static*/ System System::s_instance;

template <>
Core::System& Global() {
    return System::GetInstance();
}

template <>
Kernel::KernelSystem& Global() {
    return System::GetInstance().Kernel();
}

template <>
Core::Timing& Global() {
    return System::GetInstance().CoreTiming();
}

System::System() : movie{*this}, cheat_engine{*this} {}

System::~System() {
    // GammaOS: never destroy a running save state writer (std::terminate).
    WaitSaveWriter();
}

#ifdef ANDROID
namespace {
// GammaOS: the emulation thread's real-time state (RunLoop below).
bool gamma_emu_rt_active = false;
int gamma_emu_rt_prio = 0;

// A save state load or save runs for up to seconds without coming back to the run loop, too long
// for a real-time thread with kernel throttling off: run it as a normal thread.
struct GammaRtPause {
    GammaRtPause() {
        if (gamma_emu_rt_active) {
            sched_param sp{};
            sched_setscheduler(0, SCHED_OTHER, &sp);
        }
    }
    ~GammaRtPause() {
        if (gamma_emu_rt_active) {
            sched_param sp{};
            sp.sched_priority = gamma_emu_rt_prio;
            sched_setscheduler(0, SCHED_FIFO, &sp);
        }
    }
};
} // namespace
#endif

System::ResultStatus System::RunLoop(bool tight_loop) {
    status = ResultStatus::Success;

    if (!IsPoweredOn()) {
        return ResultStatus::ErrorNotInitialized;
    }

    // GammaOS async GPU: hand over the interrupts of command lists the worker finished.
    if (gpu) {
        gpu->AsyncPoll();
    }

#ifdef ANDROID
    // GammaOS: the emulation thread as a real-time thread (SCHED_FIFO), so other runnable
    // threads wait for a free core instead of preempting it. A waking real-time thread only
    // looks for another core when the running one has at least its priority, so the default is
    // 2, the priority of the DRM presenter threads (on the RG DS they and the core's present
    // threads took 2 to 7 percent of the emulation thread's time, in bursts that made long
    // frames). persist.gammaos.azahar_nano.emu_rt: 0 off, 1 on at priority 2, 2 to 9 that
    // priority; default on (priority 2) on the Mali devices only.
    {
        static bool rt_checked = false; // the emulation thread is the only caller
        bool& rt_active = gamma_emu_rt_active;
        // Kernel real-time throttling is off on these kernels (sched_rt_runtime_us -1), so a
        // real-time thread that never blocks would starve the per-core kernel threads of its
        // core (workqueues, ksoftirqd) for as long as it runs. Give that core a 100 us sleep
        // whenever 50 ms passed without one (at most 0.2 percent of the thread).
        if (rt_active) {
            static u32 passes = 0;
            static auto last_breath = std::chrono::steady_clock::now();
            if ((++passes & 63) == 0) {
                const auto now = std::chrono::steady_clock::now();
                if (now - last_breath >= std::chrono::milliseconds(50)) {
                    usleep(100);
                    last_breath = std::chrono::steady_clock::now();
                }
            }
        }
        if (!rt_checked) {
            rt_checked = true;
            const int prio = Common::GammaOS::EmuRtPriority();
            if (prio > 0) {
                sched_param sp{};
                sp.sched_priority = prio;
                if (sched_setscheduler(0, SCHED_FIFO, &sp) == 0) {
                    rt_active = true;
                    gamma_emu_rt_prio = prio;
                    LOG_INFO(Core, "emulation thread: SCHED_FIFO priority {}", prio);
                } else {
                    LOG_WARNING(Core, "emulation thread: SCHED_FIFO refused ({})", errno);
                }
            }
        }
    }
#endif

#ifdef ENABLE_GDBSTUB
    if (GDBStub::IsServerEnabled()) {
        // The break flag is only set if GDB is connected,
        // we can do clearing here safely. If it is ever
        // used outside, move the clearing outside the if.
        for (auto& cpu_core : cpu_cores) {
            cpu_core->ClearBreakFlag();
        }
        GDBStub::HandlePacket(*this);
    }
#endif

    // GammaOS slice_fast (persist.gammaos.azahar_nano.slice_fast, 1 on / 0 off, default off; needs
    // idle_cores): the run loop pass for a game that runs threads on the first core only. The
    // signal mutex is taken only when SendSignal flagged a signal, and the timers of the cores
    // with no threads and nothing scheduled are parked (Timing::ParkTimers) instead of being
    // advanced, sliced and idled every pass: each pass visits the first core alone and stays on
    // it (no switch to the last core at the end). A thread created on a parked core, an event
    // scheduled on one, svc_stats sampling, the async GPU worker or a save state operation
    // unpark them first, at the point of the pass where the full visits would have continued.
    static const bool slice_fast =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.slice_fast", false);

    Signal signal{Signal::None};
    u32 param{};
    if (!slice_fast || signal_pending.load(std::memory_order_acquire)) {
        std::scoped_lock lock{signal_mutex};
        if (current_signal != Signal::None) {
            signal = current_signal;
            param = signal_param;
            current_signal = Signal::None;
        }
        signal_pending.store(false, std::memory_order_relaxed);
    }
    // Save states read and write every timer: write the parked ones back first.
    if (timing && timing->AnyParked() &&
        (signal != Signal::None || save_state_request_status != SaveStateStatus::NONE)) {
        timing->SnapshotParkLead();
        timing->UnparkTimers(false);
    }
    switch (signal) {
    case Signal::Reset: {
        if (app_loader && app_loader->DoingInitialSetup()) {
            // Treat reset as shutdown if we are doing the initial setup
            return ResultStatus::ShutdownRequested;
        }
        Reset();
        return ResultStatus::Success;
    }
    case Signal::Shutdown:
        return ResultStatus::ShutdownRequested;
    case Signal::Load: {
        if (save_state_request_status != SaveStateStatus::NONE) {
            LOG_ERROR(Core, "A pending save state operation has not finished yet");
            status_details = "A pending save state operation has not finished yet";
            return ResultStatus::ErrorSavestate;
        }
        u64 title_id{};
        if (app_loader) {
            app_loader->ReadProgramId(title_id);
        }
        auto info = GetSaveStateInfo(title_id, movie.GetCurrentMovieID(), param);
        if (info.slot == std::numeric_limits<u32>::max()) {
            // Should not happen
            LOG_ERROR(Core, "Load of slot {} refused: no valid state for title {:016X} movie {:016X}",
                      param, title_id, movie.GetCurrentMovieID());
            status_details = "Failed to load savestate";
            return ResultStatus::ErrorSavestate;
        }
        if (info.status == Core::SaveStateInfo::ValidationStatus::BuildMismatch) {
            LOG_ERROR(Core, "Load of slot {} refused: made by build {} {}", param, info.build_name,
                      info.build_version);
            status_details = info.build_name;
            return ResultStatus::ErrorSavestateBuildMismatch;
        }
        LOG_INFO(Core, "Load of slot {} requested", param);
        save_state_slot = param;
        save_state_request_time = std::chrono::steady_clock::now();
        save_state_request_status = SaveStateStatus::LOADING;
        break;
    }
    case Signal::Save: {
        if (save_state_request_status != SaveStateStatus::NONE) {
            LOG_ERROR(Core, "A pending save state operation has not finished yet");
            status_details = "A pending save state operation has not finished yet";
            return ResultStatus::ErrorSavestate;
        }
        save_state_slot = param;
        save_state_request_time = std::chrono::steady_clock::now();
        save_state_request_status = SaveStateStatus::SAVING;
        break;
    }
    default:
        break;
    }

    if (save_state_request_status == SaveStateStatus::LOADING && kernel.get() &&
        !kernel->AreAsyncOperationsPending()) {
        const u32 slot = save_state_slot;
        save_state_request_status = SaveStateStatus::NONE;
        LOG_INFO(Core, "Begin load of slot {}", slot);
        try {
#ifdef ANDROID
            GammaRtPause rt_pause;
#endif
            System::LoadState(slot);
            LOG_INFO(Core, "Load completed");
            // GammaOS: the loaded system starts with no frame history (the realtime audio
            // clock would read a scale of 0 and stretch the audio tick a hundredfold).
            ResetFrameTiming();
            GammaJitWarm(true);
        } catch (const std::exception& e) {
            LOG_ERROR(Core, "Error loading: {}", e.what());
            status_details = e.what();
            return ResultStatus::ErrorSavestate;
        }
        frame_limiter.WaitOnce();
        return ResultStatus::Success;
    } else if (save_state_request_status == SaveStateStatus::SAVING && kernel.get() &&
               !kernel->AreAsyncOperationsPending()) {
        save_state_request_status = SaveStateStatus::NONE;
        const u32 slot = save_state_slot;
        LOG_INFO(Core, "Begin save to slot {}", slot);
        try {
#ifdef ANDROID
            GammaRtPause rt_pause;
#endif
            System::SaveState(slot);
            LOG_INFO(Core, "Save completed");
        } catch (const std::exception& e) {
            LOG_ERROR(Core, "Error saving: {}", e.what());
            status_details = e.what();
            return ResultStatus::ErrorSavestate;
        }
        frame_limiter.WaitOnce();
        return ResultStatus::Success;
    } else if (save_state_request_status != SaveStateStatus::NONE &&
               (std::chrono::steady_clock::now() - save_state_request_time) >
                   std::chrono::seconds(5)) {
        save_state_request_status = SaveStateStatus::NONE;
        LOG_ERROR(Core, "Cannot perform save state operation due to pending async operations");
        status_details = "Cannot perform save state operation due to pending async operations";
        return ResultStatus::ErrorSavestate;
    }

    // GammaOS: a core no thread was ever created on and with nothing scheduled on its timer
    // (the two extra New 3DS cores, usually the system core too) only needs its timer kept
    // in step: switching the kernel to it, rescheduling its empty thread list and idling it
    // cost about half of every pass, and Kirby: Planet Robobot makes 18000 passes a second.
    // The last core's state is restored at the end of the pass, as if it had been visited.
    // persist.gammaos.azahar_nano.idle_cores=0 visits every core; default Mali only.
    static const bool skip_idle_cores =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.idle_cores");
    const auto core_is_idle = [this](const std::shared_ptr<ARM_Interface>& core) {
        return skip_idle_cores && !(gpu && gpu->AsyncPending()) &&
               kernel->GetThreadManager(core->GetID()).GetThreadList().empty() &&
               core->GetTimer().HasNoEvents();
    };

    // The two loops of a pass with all cores at the same time, one core at a time: advance (fire
    // the due events, pick the thread, find the slice every core can run) and run.
    s64 max_slice = Timing::MAX_SLICE_LENGTH;
    bool last_core_skipped = false;
    const auto visit_advance = [&](const std::shared_ptr<ARM_Interface>& cpu_core) {
        if (core_is_idle(cpu_core)) {
            cpu_core->GetTimer().Advance();
            max_slice = std::min(max_slice, cpu_core->GetTimer().GetMaxSliceLength());
            return;
        }
        running_core = cpu_core.get();
        kernel->SetRunningCPU(running_core);
        cpu_core->GetTimer().Advance();
        cpu_core->PrepareReschedule();
        kernel->GetThreadManager(cpu_core->GetID()).Reschedule();
        max_slice = std::min(max_slice, cpu_core->GetTimer().GetMaxSliceLength());
    };
    const auto visit_run = [&](const std::shared_ptr<ARM_Interface>& cpu_core) {
        if (core_is_idle(cpu_core)) {
            // What the full visit below does for a core with no current thread.
            cpu_core->GetTimer().SetNextSlice(max_slice);
            const auto start_ticks = cpu_core->GetTimer().GetTicks();
            cpu_core->GetTimer().Idle();
            max_slice = cpu_core->GetTimer().GetTicks() - start_ticks;
            last_core_skipped = true;
            return;
        }
        last_core_skipped = false;
        cpu_core->GetTimer().SetNextSlice(max_slice);
        auto start_ticks = cpu_core->GetTimer().GetTicks();
        LOG_TRACE(Core_ARM11, "Core {} running for {} ticks", cpu_core->GetID(),
                  cpu_core->GetTimer().GetDowncount());
        running_core = cpu_core.get();
        kernel->SetRunningCPU(running_core);
        // If we don't have a currently active thread then don't execute instructions,
        // instead advance to the next event and try to yield to the next thread
        if (kernel->GetCurrentThreadManager().GetCurrentThread() == nullptr) {
            LOG_TRACE(Core_ARM11, "Core {} idling", cpu_core->GetID());
            if (gpu && gpu->AsyncPending()) {
                gpu->AsyncDrain();
            }
            cpu_core->GetTimer().Idle();
            PrepareReschedule();
        } else {
            // In the rare case the break flag is set (due to exception thrown)
            // there is probably no need to adjust the timer accordingly.
            // GammaOS debug timing only while svc_stats sampling is on (clock reads cost
            // about 2% of the emulation thread in busy games).
            const auto run_start = gamma_pc_sampling ? std::chrono::steady_clock::now()
                                                     : std::chrono::steady_clock::time_point{};
            const bool parked = timing->AnyParked();
            if (parked) {
                timing->SetParkLeadRunning(true);
            }
            if (tight_loop) {
                cpu_core->Run();
            } else {
                cpu_core->Step();
            }
            if (parked) {
                timing->SetParkLeadRunning(false);
            }
            if (gamma_pc_sampling) {
                const u64 key = (u64{cpu_core->GetPC()} << 32) | cpu_core->GetReg(14);
                gamma_pc_samples[key]++;
            }
            if (gamma_pc_sampling) {
                gamma_core_runs[cpu_core->GetID() & 3]++;
                gamma_core_host_ns[cpu_core->GetID() & 3] +=
                    std::chrono::duration_cast<std::chrono::nanoseconds>(
                        std::chrono::steady_clock::now() - run_start)
                        .count();
            }
        }
        max_slice = cpu_core->GetTimer().GetTicks() - start_ticks;
        Reschedule();
    };
    const auto switch_to_last_core = [&] {
        running_core = cpu_cores.back().get();
        kernel->SetRunningCPU(running_core);
    };

    // GammaOS slice_fast: the pass with the other cores parked.
    const auto park_allowed = [&] {
        return slice_fast && skip_idle_cores && !gamma_pc_sampling &&
               !(gpu && gpu->AsyncActive()) &&
               save_state_request_status == SaveStateStatus::NONE;
    };
    const auto park_dirty = [&] {
        return timing->ParkDirty() || kernel->thread_list_epoch != gamma_park_thread_epoch;
    };
    bool fast_pass = false;
    if (timing->AnyParked()) {
        timing->SnapshotParkLead();
        const auto& lead = cpu_cores[0];
        if (!park_allowed() || park_dirty() || core_is_idle(lead)) {
            timing->UnparkTimers(false);
        } else {
            fast_pass = true;
            // Nothing to bring up to the global time (the first loop below): the parked cores
            // are at the lead's time, which is the global time.
            visit_advance(lead);
            if (park_dirty()) {
                // The lead's events gave a parked core work: the rest of the pass as usual.
                timing->UnparkTimers(false);
                for (std::size_t i = 1; i < cpu_cores.size(); i++) {
                    visit_advance(cpu_cores[i]);
                }
                for (const auto& cpu_core : cpu_cores) {
                    visit_run(cpu_core);
                }
                if (last_core_skipped) {
                    switch_to_last_core();
                }
            } else {
                visit_run(lead);
                if (park_dirty()) {
                    // Work for a parked core while the lead ran: visit the others now.
                    timing->UnparkTimers(true);
                    for (std::size_t i = 1; i < cpu_cores.size(); i++) {
                        visit_run(cpu_cores[i]);
                    }
                    if (last_core_skipped) {
                        switch_to_last_core();
                    }
                }
            }
        }
    }

    if (!fast_pass) {
    // All cores should have executed the same amount of ticks. If this is not the case an event was
    // scheduled with a cycles_into_future smaller then the current downcount.
    // So we have to get those cores to the same global time first
    u64 global_ticks = timing->GetGlobalTicks();
    s64 max_delay = 0;
    ARM_Interface* current_core_to_execute = nullptr;
    for (auto& cpu_core : cpu_cores) {
        if (cpu_core->GetTimer().GetTicks() < global_ticks) {
            s64 delay = global_ticks - cpu_core->GetTimer().GetTicks();
            running_core = cpu_core.get();
            kernel->SetRunningCPU(running_core);
            cpu_core->GetTimer().Advance();
            cpu_core->PrepareReschedule();
            kernel->GetThreadManager(cpu_core->GetID()).Reschedule();
            cpu_core->GetTimer().SetNextSlice(delay);
            if (max_delay < delay) {
                max_delay = delay;
                current_core_to_execute = cpu_core.get();
            }
        }
    }

    // jit sometimes overshoot by a few ticks which might lead to a minimal desync in the cores.
    // This small difference shouldn't make it necessary to sync the cores and would only cost
    // performance. Thus we don't sync delays below min_delay
    static constexpr s64 min_delay = 100;
    if (max_delay > min_delay) {
        LOG_TRACE(Core_ARM11, "Core {} running (delayed) for {} ticks",
                  current_core_to_execute->GetID(),
                  current_core_to_execute->GetTimer().GetDowncount());
        if (running_core != current_core_to_execute) {
            running_core = current_core_to_execute;
            kernel->SetRunningCPU(running_core);
        }
        if (kernel->GetCurrentThreadManager().GetCurrentThread() == nullptr) {
            LOG_TRACE(Core_ARM11, "Core {} idling", current_core_to_execute->GetID());
            // GammaOS async GPU: nothing to run is usually a thread waiting for the GPU; let
            // the worker finish (and its interrupts arrive) instead of skipping time ahead.
            if (gpu && gpu->AsyncPending()) {
                gpu->AsyncDrain();
            }
            current_core_to_execute->GetTimer().Idle();
            PrepareReschedule();
        } else {
            if (tight_loop) {
                current_core_to_execute->Run();
            } else {
                current_core_to_execute->Step();
            }
        }
        Reschedule();
    } else {
        // Now all cores are at the same global time. So we will run them one after the other
        // with a max slice that is the minimum of all max slices of all cores
        // TODO: Make special check for idle since we can easily revert the time of idle cores
        for (const auto& cpu_core : cpu_cores) {
            visit_advance(cpu_core);
        }
        for (const auto& cpu_core : cpu_cores) {
            visit_run(cpu_core);
        }
        // GammaOS slice_fast: park the idle cores when only the first one has work (it stays
        // the running core: no switch to the last one).
        bool parked_now = false;
        if (park_allowed() && cpu_cores.size() > 1 && running_core == cpu_cores[0].get() &&
            !core_is_idle(cpu_cores[0])) {
            u32 mask = 0;
            bool all_idle = true;
            for (std::size_t i = 1; i < cpu_cores.size() && all_idle; i++) {
                all_idle = core_is_idle(cpu_cores[i]);
                mask |= 1u << i;
            }
            if (all_idle && timing->ParkTimers(0, mask)) {
                gamma_park_thread_epoch = kernel->thread_list_epoch;
                parked_now = true;
                // Each park follows an unpark: a count that keeps growing means the fast pass
                // rarely holds.
                static u32 parks = 0;
                if ((++parks & (parks - 1)) == 0) {
                    LOG_INFO(Core, "slice_fast: idle cores parked (mask {:x}), {} times", mask,
                             parks);
                }
            }
        }
        if (last_core_skipped && !parked_now) {
            switch_to_last_core();
        }
    }
    }

    // GammaOS debug (sys.gammaos.azahar_nano.svc_stats=1): per core, emulated ticks executed by
    // guest code vs skipped idle, every 2 s of host time.
    {
        static bool enabled = false;
        static u32 polls = 0;
        static auto last = std::chrono::steady_clock::now();
        static std::array<u64, 4> last_ticks{}, last_idle{};
        if ((polls++ & 0x3FF) == 0) {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("sys.gammaos.azahar_nano.svc_stats", v);
            enabled = v[0] == '1';
            gamma_pc_sampling = enabled;
        }
        const auto now = enabled ? std::chrono::steady_clock::now() : last;
        if (enabled && now - last > std::chrono::seconds(2)) {
            last = now;
            std::string line;
            for (std::size_t i = 0; i < cpu_cores.size() && i < 4; i++) {
                const u64 ticks = cpu_cores[i]->GetTimer().GetTicks();
                const u64 idle = cpu_cores[i]->GetTimer().GetIdleTicks();
                const u64 dt = ticks - last_ticks[i], di = idle - last_idle[i];
                line += fmt::format(" core{} ticks {}M idle {}M host {} ms runs {}", i, dt / 1000000,
                                    di / 1000000, gamma_core_host_ns[i] / 1000000, gamma_core_runs[i]);
                gamma_core_runs[i] = 0;
                gamma_core_host_ns[i] = 0;
                last_ticks[i] = ticks;
                last_idle[i] = idle;
            }
            LOG_INFO(Core, "emulated time:{}", line);
            // Guest PC / LR at the end of each core slice: the 25 most frequent pairs.
            std::vector<std::pair<u64, u64>> top(gamma_pc_samples.begin(), gamma_pc_samples.end());
            std::sort(top.begin(), top.end(), [](auto& a, auto& b) { return a.second > b.second; });
            u64 total = 0;
            for (auto& t : top) total += t.second;
            std::string pcs;
            for (std::size_t k = 0; k < top.size() && k < 25; k++) {
                pcs += fmt::format(" {:08X}<{:08X}:{}", u32(top[k].first >> 32), u32(top[k].first),
                                   top[k].second);
            }
            LOG_INFO(Core, "guest pc<lr samples ({} slices):{}", total, pcs);
            gamma_pc_samples.clear();
        }
    }

    return status;
}

bool System::SendSignal(System::Signal signal, u32 param) {
    std::scoped_lock lock{signal_mutex};
    if (current_signal != signal && current_signal != Signal::None) {
        LOG_ERROR(Core, "Unable to {} as {} is ongoing", signal, current_signal);
        return false;
    }
    current_signal = signal;
    signal_param = param;
    signal_pending.store(true, std::memory_order_release);
    return true;
}

System::ResultStatus System::SingleStep() {
    return RunLoop(false);
}

System::ResultStatus System::Load(Frontend::EmuWindow& emu_window, const std::string& filepath,
                                  Frontend::EmuWindow* secondary_window) {
    Settings::ResetTemporaryFrameLimit();
    FileUtil::SetCurrentRomPath(filepath);
    if (early_app_loader) {
        app_loader = std::move(early_app_loader);
    } else {
        app_loader = Loader::GetLoader(filepath);
    }
    if (!app_loader) {
        LOG_CRITICAL(Core, "Failed to obtain loader for {}", filepath);
        return ResultStatus::ErrorGetLoader;
    }

    u64_le program_id = 0;
    app_loader->ReadProgramId(program_id);
    if (restore_plugin_context.has_value() && restore_plugin_context->is_enabled &&
        restore_plugin_context->use_user_load_parameters) {
        if (restore_plugin_context->user_load_parameters.low_title_Id ==
                static_cast<u32_le>(program_id) &&
            restore_plugin_context->user_load_parameters.plugin_memory_strategy ==
                Service::PLGLDR::PLG_LDR::PluginMemoryStrategy::PLG_STRATEGY_MODE3) {
            app_loader->SetKernelMemoryModeOverride(Kernel::MemoryMode::Dev2);
        }
    }

    Kernel::MemoryMode app_mem_mode;
    Kernel::MemoryMode system_mem_mode;
    bool used_default_mem_mode = false;
    Kernel::New3dsHwCapabilities app_n3ds_hw_capabilities;

    if (m_mem_mode) {
        // Use memory mode set by the FIRM launch parameters
        system_mem_mode = static_cast<Kernel::MemoryMode>(m_mem_mode.value());
        m_mem_mode = {};
    } else {
        // Use default memory mode based on the n3ds setting
        system_mem_mode = Settings::values.is_new_3ds.GetValue() ? Kernel::MemoryMode::NewProd
                                                                 : Kernel::MemoryMode::Prod;
        used_default_mem_mode = true;
    }

    {
        auto memory_mode = app_loader->LoadKernelMemoryMode();
        if (memory_mode.second != Loader::ResultStatus::Success) {
            LOG_CRITICAL(Core, "Failed to determine system mode (Error {})!",
                         static_cast<int>(memory_mode.second));

            switch (memory_mode.second) {
            case Loader::ResultStatus::ErrorEncrypted:
                return ResultStatus::ErrorLoader_ErrorEncrypted;
            case Loader::ResultStatus::ErrorInvalidFormat:
                return ResultStatus::ErrorLoader_ErrorInvalidFormat;
            case Loader::ResultStatus::ErrorGbaTitle:
                return ResultStatus::ErrorLoader_ErrorGbaTitle;
            case Loader::ResultStatus::ErrorArtic:
                return ResultStatus::ErrorArticDisconnected;
            default:
                return ResultStatus::ErrorSystemMode;
            }
        }

        ASSERT(memory_mode.first);
        app_mem_mode = memory_mode.first.value();
    }

    auto n3ds_hw_caps = app_loader->LoadNew3dsHwCapabilities();
    ASSERT(n3ds_hw_caps.first);
    app_n3ds_hw_capabilities = n3ds_hw_caps.first.value();

    if (!Settings::values.is_new_3ds.GetValue() && app_loader->IsN3DSExclusive()) {
        return ResultStatus::ErrorN3DSApplication;
    }

    // If the default mem mode has been used, we do not come from a FIRM launch. On real HW
    // however, the home menu is in charge or setting the proper memory mode when launching
    // applications by doing a FIRM launch. Since we launch the application without going
    // through the home menu, we need to emulate the FIRM launch having happened and set the
    // proper memory mode.
    if (used_default_mem_mode) {

        // If we are on the Old 3DS prod mode and the application memory mode does not match, we
        // need to adjust it. We do not need adjustment if we are on the New 3DS prod mode, as that
        // one overrides all the Old 3DS memory modes.
        if (system_mem_mode == Kernel::MemoryMode::Prod && app_mem_mode != system_mem_mode) {
            system_mem_mode = app_mem_mode;
        }

        // If we are on the New 3DS prod mode, and the application needs the New 3DS extended
        // memory mode (only CTRAging is known to do this), adjust the memory mode.
        else if (system_mem_mode == Kernel::MemoryMode::NewProd &&
                 app_n3ds_hw_capabilities.memory_mode == Kernel::New3dsMemoryMode::NewDev1) {
            system_mem_mode = Kernel::MemoryMode::NewDev1;
        }
    }

    u32 num_cores = 2;
    if (Settings::values.is_new_3ds) {
        num_cores = 4;
    }
    ResultStatus init_result{Init(emu_window, secondary_window, system_mem_mode, num_cores)};
    if (init_result != ResultStatus::Success) {
        LOG_CRITICAL(Core, "Failed to initialize system (Error {})!",
                     static_cast<u32>(init_result));
        System::Shutdown();
        return init_result;
    }

    kernel->UpdateCPUAndMemoryState(program_id, app_mem_mode, app_n3ds_hw_capabilities);

    // Restore any parameters that should be carried through a reset.
    if (auto apt = Service::APT::GetModule(*this)) {
        if (restore_deliver_arg.has_value()) {
            apt->GetAppletManager()->SetDeliverArg(restore_deliver_arg);
            restore_deliver_arg.reset();
        }
        if (restore_sys_menu_arg.has_value()) {
            apt->GetAppletManager()->SetSysMenuArg(restore_sys_menu_arg.value());
            restore_sys_menu_arg.reset();
        }
        apt->SetWirelessRebootInfoBuffer(restore_wireless_reboot_info);
    }

    if (restore_plugin_context.has_value()) {
        if (auto plg_ldr = Service::PLGLDR::GetService(*this)) {
            plg_ldr->SetPluginLoaderContext(restore_plugin_context.value());
        }
        restore_plugin_context.reset();
    }

    if (restore_ipc_recorder) {
        kernel->RestoreIPCRecorder(std::move(restore_ipc_recorder));
    }

    std::shared_ptr<Kernel::Process> process;
    const Loader::ResultStatus load_result{app_loader->Load(process)};
    if (Loader::ResultStatus::Success != load_result) {
        LOG_CRITICAL(Core, "Failed to load ROM (Error {})!", load_result);
        System::Shutdown();

        switch (load_result) {
        case Loader::ResultStatus::ErrorEncrypted:
            return ResultStatus::ErrorLoader_ErrorEncrypted;
        case Loader::ResultStatus::ErrorInvalidFormat:
            return ResultStatus::ErrorLoader_ErrorInvalidFormat;
        case Loader::ResultStatus::ErrorGbaTitle:
            return ResultStatus::ErrorLoader_ErrorGbaTitle;
        case Loader::ResultStatus::ErrorPatches:
            return ResultStatus::ErrorLoader_ErrorPatches;
        case Loader::ResultStatus::ErrorPatchesInvalidTitle:
            return ResultStatus::ErrorLoader_ErrorPatchesInvalidTitle;
        case Loader::ResultStatus::ErrorArtic:
            return ResultStatus::ErrorArticDisconnected;
        default:
            return ResultStatus::ErrorLoader;
        }
    }
    kernel->SetCurrentProcess(process);
    title_id = 0;
    if (app_loader->ReadProgramId(title_id) != Loader::ResultStatus::Success) {
        LOG_ERROR(Core, "Failed to find title id for ROM (Error {})",
                  static_cast<u32>(load_result));
    }

    cheat_engine.LoadCheatFile(title_id);
    cheat_engine.Connect(process->process_id);

    perf_stats = std::make_unique<PerfStats>(title_id);

    if (Settings::values.dump_textures) {
        custom_tex_manager->PrepareDumping(title_id);
    }
    if (Settings::values.custom_textures) {
        custom_tex_manager->FindCustomTextures();
    }

    status = ResultStatus::Success;
    m_emu_window = &emu_window;
    m_secondary_window = secondary_window;
    m_filepath = filepath;

    // Reset counters and set time origin to current frame
    [[maybe_unused]] const PerfStats::Results result = GetAndResetPerfStats();
    perf_stats->BeginSystemFrame();
    return status;
}

void System::PrepareReschedule() {
    running_core->PrepareReschedule();
    curr_core_reschedule_pending = true;
}

PerfStats::Results System::GetAndResetPerfStats() {
    return (perf_stats && timing) ? perf_stats->GetAndResetStats(timing->GetGlobalTimeUs())
                                  : PerfStats::Results{};
}

PerfStats::Results System::GetLastPerfStats() {
    return perf_stats ? perf_stats->GetLastStats() : PerfStats::Results{};
}

double System::GetStableFrameTimeScale() {
    return perf_stats->GetStableFrameTimeScale();
}

void System::ResetFrameTiming() {
    if (perf_stats) {
        perf_stats->ResetFrameTiming();
    }
}

void System::Reschedule() {
    if (!curr_core_reschedule_pending) {
        return;
    }

    curr_core_reschedule_pending = false;
    kernel->GetThreadManager(running_core->GetID()).Reschedule();
}

System::ResultStatus System::Init(Frontend::EmuWindow& emu_window,
                                  Frontend::EmuWindow* secondary_window,
                                  Kernel::MemoryMode memory_mode, u32 num_cores) {
    // Notification for system initialization (either boot or savestate).
    if (on_init_callback) {
        on_init_callback(true);
    }
    SCOPE_EXIT({
        if (on_init_callback) {
            on_init_callback(false);
        }
    });

    LOG_DEBUG(HW_Memory, "initialized OK");

    if (!memory) {
        memory = std::make_unique<Memory::MemorySystem>(*this);
    }

    timing = std::make_unique<Timing>(num_cores, Settings::values.cpu_clock_percentage.GetValue(),
                                      movie.GetOverrideBaseTicks());

    kernel = std::make_unique<Kernel::KernelSystem>(
        *memory, *timing, [this] { PrepareReschedule(); }, memory_mode, num_cores,
        movie.GetOverrideInitTime());

    exclusive_monitor = MakeExclusiveMonitor(*memory, num_cores);
    cpu_cores.reserve(num_cores);
    if (Settings::values.use_cpu_jit) {
#if CITRA_ARCH(x86_64) || CITRA_ARCH(arm64)
        for (u32 i = 0; i < num_cores; ++i) {
            cpu_cores.push_back(std::make_shared<ARM_Dynarmic>(
                *this, *memory, i, timing->GetTimer(i), *exclusive_monitor));
        }
#else
        for (u32 i = 0; i < num_cores; ++i) {
            cpu_cores.push_back(
                std::make_shared<ARM_DynCom>(*this, *memory, USER32MODE, i, timing->GetTimer(i)));
        }
        LOG_WARNING(Core, "CPU JIT requested, but Dynarmic not available");
#endif
    } else {
        for (u32 i = 0; i < num_cores; ++i) {
            cpu_cores.push_back(
                std::make_shared<ARM_DynCom>(*this, *memory, USER32MODE, i, timing->GetTimer(i)));
        }
    }
    running_core = cpu_cores[0].get();

    kernel->SetCPUs(cpu_cores);
    kernel->SetRunningCPU(cpu_cores[0].get());

    const auto audio_emulation = Settings::values.audio_emulation.GetValue();
    if (audio_emulation == Settings::AudioEmulation::HLE) {
        dsp_core = std::make_unique<AudioCore::DspHle>(*this);
    } else {
        const bool multithread = audio_emulation == Settings::AudioEmulation::LLEMultithreaded;
        dsp_core = std::make_unique<AudioCore::DspLle>(*this, multithread);
    }

    dsp_core->SetSink(Settings::values.output_type.GetValue(),
                      Settings::values.output_device.GetValue());
    dsp_core->EnableStretching(Settings::values.enable_audio_stretching.GetValue());

#ifdef ENABLE_SCRIPTING
    if (Settings::values.enable_rpc_server.GetValue()) {
        rpc_server = std::make_unique<RPC::Server>(*this);
    }
#endif

    service_manager = std::make_unique<Service::SM::ServiceManager>(*this);
    archive_manager = std::make_unique<Service::FS::ArchiveManager>(*this);

    u64 loading_title_id = 0;
    app_loader->ReadProgramId(loading_title_id);
    HW::AES::InitKeys();
    Service::Init(*this, loading_title_id, lle_modules, !app_loader->DoingInitialSetup());
#ifdef ENABLE_GDBSTUB
    GDBStub::DeferStart();
#endif

    if (!registered_image_interface) {
        registered_image_interface = std::make_shared<Frontend::ImageInterface>();
    }

    if (!custom_tex_manager) {
        custom_tex_manager = std::make_unique<VideoCore::CustomTexManager>(*this);
    }

    auto gsp = service_manager->GetService<Service::GSP::GSP_GPU>("gsp::Gpu");
    if (gpu) {
        gpu->ResetForLoad();   // kept across a save state load (see Shutdown)
    } else {
        gpu = std::make_unique<VideoCore::GPU>(*this, emu_window, secondary_window);
    }
    gpu->SetInterruptHandler([gsp](Service::GSP::InterruptId interrupt_id, u64 wait_delay_ns) {
        gsp->SignalInterrupt(interrupt_id, wait_delay_ns);
    });

    auto plg_ldr = Service::PLGLDR::GetService(*this);
    if (plg_ldr) {
        plg_ldr->SetEnabled(Settings::values.plugin_loader_enabled.GetValue());
        plg_ldr->SetAllowGameChangeState(Settings::values.allow_plugin_loader.GetValue());
    }

    SetInfoLEDColor({});

    LOG_DEBUG(Core, "Initialized OK");

    is_powered_on = true;

    return ResultStatus::Success;
}

VideoCore::GPU& System::GPU() {
    return *gpu;
}

Service::SM::ServiceManager& System::ServiceManager() {
    return *service_manager;
}

const Service::SM::ServiceManager& System::ServiceManager() const {
    return *service_manager;
}

Service::FS::ArchiveManager& System::ArchiveManager() {
    return *archive_manager;
}

const Service::FS::ArchiveManager& System::ArchiveManager() const {
    return *archive_manager;
}

Kernel::KernelSystem& System::Kernel() {
    return *kernel;
}

const Kernel::KernelSystem& System::Kernel() const {
    return *kernel;
}

bool System::KernelRunning() {
    return kernel != nullptr;
}

Timing& System::CoreTiming() {
    return *timing;
}

const Timing& System::CoreTiming() const {
    return *timing;
}

Memory::MemorySystem& System::Memory() {
    return *memory;
}

const Memory::MemorySystem& System::Memory() const {
    return *memory;
}

Cheats::CheatEngine& System::CheatEngine() {
    return cheat_engine;
}

const Cheats::CheatEngine& System::CheatEngine() const {
    return cheat_engine;
}

void System::RegisterVideoDumper(std::shared_ptr<VideoDumper::Backend> dumper) {
    video_dumper = std::move(dumper);
}

VideoCore::CustomTexManager& System::CustomTexManager() {
    return *custom_tex_manager;
}

const VideoCore::CustomTexManager& System::CustomTexManager() const {
    return *custom_tex_manager;
}

Core::Movie& System::Movie() {
    return movie;
}

const Core::Movie& System::Movie() const {
    return movie;
}

void System::RegisterMiiSelector(std::shared_ptr<Frontend::MiiSelector> mii_selector) {
    registered_mii_selector = std::move(mii_selector);
}

void System::RegisterSoftwareKeyboard(std::shared_ptr<Frontend::SoftwareKeyboard> swkbd) {
    registered_swkbd = std::move(swkbd);
}

void System::RegisterImageInterface(std::shared_ptr<Frontend::ImageInterface> image_interface) {
    registered_image_interface = std::move(image_interface);
}

void System::Shutdown(bool is_deserializing) {
    WaitSaveWriter();

    // Shutdown emulation session
    is_powered_on = false;

    // GammaOS: a save state load keeps the GPU (renderer, shader and pipeline caches), the
    // guest memory it reads and the custom texture manager it holds; the load restores the
    // memory's contents and GPU::ResetForLoad drops the surface cache. Tearing the renderer
    // down cost up to 7 s (freeing Kirby: Planet Robobot's pipelines) and every shader had to
    // be compiled again after the load.
    if (!is_deserializing) {
        if (exiting_process && gpu) {
            // The process ends right after this (azahar-nano leaves with _exit): write the disk
            // caches the renderer's teardown would write, then leave the renderer to the process
            // exit. Freeing it (the driver releasing every pipeline) took about 6 s of every exit.
            gpu->Renderer().Rasterizer()->PersistDiskResources();
            (void)gpu.release();
        } else {
            gpu.reset();
        }
    }
    if (!is_deserializing) {
        lle_modules.clear();
#ifdef ENABLE_GDBSTUB
        GDBStub::Shutdown();
#endif
        perf_stats.reset();
        app_loader.reset();
    }
    if (!is_deserializing) {
        custom_tex_manager.reset();
    }
#ifdef ENABLE_SCRIPTING
    rpc_server.reset();
#endif
    archive_manager.reset();
    service_manager.reset();
    dsp_core.reset();
    kernel.reset();
    cpu_cores.clear();
    exclusive_monitor.reset();
    timing.reset();

    if (video_dumper && video_dumper->IsDumping()) {
        video_dumper->StopDumping();
    }

    if (auto room_member = Network::GetRoomMember().lock()) {
        Network::GameInfo game_info{};
        room_member->SendGameInfo(game_info);
    }

    if (!is_deserializing) {
        memory.reset();
    }

    SetInfoLEDColor({});

    LOG_DEBUG(Core, "Shutdown OK");
}

void System::Reset() {
    // This is NOT a proper reset, but a temporary workaround by shutting down the system and
    // reloading.
    // TODO: Properly implement the reset

    // Save the APT deliver arg and plugin loader context across resets.
    // This is needed as we don't currently support proper app jumping.
    if (auto apt = Service::APT::GetModule(*this)) {
        restore_deliver_arg = apt->GetAppletManager()->ReceiveDeliverArg();
        restore_sys_menu_arg = apt->GetAppletManager()->GetSysMenuArg();
        restore_wireless_reboot_info = apt->GetWirelessRebootInfoBuffer();
    }
    if (auto plg_ldr = Service::PLGLDR::GetService(*this)) {
        restore_plugin_context = plg_ldr->GetPluginLoaderContext();
    }

    restore_ipc_recorder = std::move(kernel->BackupIPCRecorder());

    Shutdown();

    if (!m_chainloadpath.empty()) {
        m_filepath = m_chainloadpath;
        m_chainloadpath.clear();
    }

    // Reload the system with the same setting
    [[maybe_unused]] const System::ResultStatus result =
        Load(*m_emu_window, m_filepath, m_secondary_window);
}

void System::ApplySettings() {
#ifdef ENABLE_GDBSTUB
    if (override_gdb_port != -1) {
        GDBStub::SetServerPort(override_gdb_port);
        GDBStub::ToggleServer(true);
    } else {
        GDBStub::SetServerPort(Settings::values.gdbstub_port.GetValue());
        GDBStub::ToggleServer(Settings::values.use_gdbstub.GetValue());
    }
#endif

    if (gpu) {
#ifndef ANDROID
        gpu->Renderer().UpdateCurrentFramebufferLayout();
#endif
        auto& settings = gpu->Renderer().Settings();
        settings.bg_color_update_requested = true;
        settings.shader_update_requested = true;
    }

    if (IsPoweredOn()) {
        CoreTiming().UpdateClockSpeed(Settings::values.cpu_clock_percentage.GetValue());
        dsp_core->SetSink(Settings::values.output_type.GetValue(),
                          Settings::values.output_device.GetValue());
        dsp_core->EnableStretching(Settings::values.enable_audio_stretching.GetValue());

        auto hid = Service::HID::GetModule(*this);
        if (hid) {
            hid->ReloadInputDevices();
        }

        auto apt = Service::APT::GetModule(*this);
        if (apt) {
            apt->GetAppletManager()->ReloadInputDevices();
        }

        auto ir_user = service_manager->GetService<Service::IR::IR_USER>("ir:USER");
        if (ir_user)
            ir_user->ReloadInputDevices();
        auto ir_rst = service_manager->GetService<Service::IR::IR_RST>("ir:rst");
        if (ir_rst)
            ir_rst->ReloadInputDevices();

        auto cam = Service::CAM::GetModule(*this);
        if (cam) {
            cam->ReloadCameraDevices();
        }

        Service::MIC::ReloadMic(*this);
    }

    auto plg_ldr = Service::PLGLDR::GetService(*this);
    if (plg_ldr) {
        plg_ldr->SetEnabled(Settings::values.plugin_loader_enabled.GetValue());
        plg_ldr->SetAllowGameChangeState(Settings::values.allow_plugin_loader.GetValue());
    }
}

void System::RegisterAppLoaderEarly(std::unique_ptr<Loader::AppLoader>& loader) {
    early_app_loader = std::move(loader);
}

void System::InsertCartridge(const std::string& path) {
    FileSys::NCCHContainer cartridge_container(path);
    if (cartridge_container.LoadHeader() == Loader::ResultStatus::Success &&
        cartridge_container.IsNCSD()) {
        inserted_cartridge = path;
    }
}

void System::EjectCartridge() {
    inserted_cartridge.clear();
}

bool System::IsInitialSetup() {
    return app_loader && app_loader->DoingInitialSetup();
}

void System::DebugUnscheduleAllThreadsFromFrontend(bool unschedule) {
    if (!is_powered_on)
        return;

    for (auto proc : kernel->GetProcessList()) {
        if (unschedule) {
            proc->SetUnscheduleMode(Kernel::UnscheduleMode::FRONTEND);
        } else {
            proc->ClearUnscheduleMode(Kernel::UnscheduleMode::FRONTEND);
        }
    }
}

template <class Archive>
void System::serialize(Archive& ar, const unsigned int file_version) {

    if (Archive::is_loading::value) {
        save_state_status = SaveStateStatus::LOADING;
    } else {
        save_state_status = SaveStateStatus::SAVING;
    }

    u32 num_cores;
    if (Archive::is_saving::value) {
        num_cores = this->GetNumCores();
    }
    ar & num_cores;

    // TODO(PabloMK7): Figure out why this is the case
    if (!lle_modules.empty()) {
        throw std::runtime_error("Savestates are not supported with LLE modules enabled");
    }

    ar & lle_modules;
    Kernel::MemoryMode mem_mode{};
    if (!Archive::is_loading::value) {
        mem_mode = kernel->GetMemoryMode();
    }
    ar & mem_mode;

    if (Archive::is_loading::value) {
        // When loading, we want to make sure any lingering state gets cleared out before we begin.
        // Shutdown, but persist a few things between loads...
        Shutdown(true);

        [[maybe_unused]] const System::ResultStatus result =
            Init(*m_emu_window, m_secondary_window, mem_mode, num_cores);
    }

    // Flush on save, don't flush on load
    const bool should_flush = !Archive::is_loading::value;
    gpu->ClearAll(should_flush);
    ar&* timing.get();
    for (u32 i = 0; i < num_cores; i++) {
        ar&* cpu_cores[i].get();
    }
    ar&* service_manager.get();
    ar&* archive_manager.get();

    // NOTE: DSP doesn't like being destroyed and recreated. So instead we do an inline
    // serialization; this means that the DSP Settings need to match for loading to work.
    auto dsp_hle = dynamic_cast<AudioCore::DspHle*>(dsp_core.get());
    if (dsp_hle) {
        ar&* dsp_hle;
    } else {
        throw std::runtime_error("LLE audio not supported for save states");
    }

    ar&* memory.get();
    ar&* kernel.get();
    ar&* gpu.get();
    ar & movie;

    // This needs to be set from somewhere - might as well be here!
    if (Archive::is_loading::value) {
        u32 cheats_pid;
        ar & cheats_pid;
        timing->UnlockEventQueue();
        cheat_engine.Connect(cheats_pid);

        if (Settings::values.custom_textures) {
            custom_tex_manager->FindCustomTextures();
        }

        // Re-register gpu callback, because gsp service changed after service_manager got
        // serialized
        auto gsp = service_manager->GetService<Service::GSP::GSP_GPU>("gsp::Gpu");
        gpu->SetInterruptHandler([gsp](Service::GSP::InterruptId interrupt_id, u64 wait_delay_ns) {
            gsp->SignalInterrupt(interrupt_id, wait_delay_ns);
        });

        // Apply per program settings and switch the shader cache to the title running when the
        // savestate was created.
        // TODO(PabloMK7): Find better way to obtain the program ID.
        const u32 thread_id = gsp->GetActiveClientThreadId();
        if (thread_id != std::numeric_limits<u32>::max()) {
            const auto thread = kernel->GetThreadByID(thread_id);
            if (thread) {
                const std::shared_ptr<Kernel::Process> process = thread->owner_process.lock();
                if (process) {
                    gpu->ApplyPerProgramSettings(process->codeset->program_id);
                    gpu->Renderer().Rasterizer()->SwitchDiskResources(process->codeset->program_id);
                }
            }
        }
    } else {
        u32 cheats_pid = cheat_engine.GetConnectedPID();
        ar & cheats_pid;
    }

    save_state_status = SaveStateStatus::NONE;
}

SERIALIZE_IMPL(System)

} // namespace Core

namespace Core {

namespace {
std::string GammaJitPath(u64 title_id, u32 core) {
    // The list holds block start locations, which depend on the translation options that move
    // block boundaries (jit_cond_branch, jit_cond_mov, read the same way as MakeJit does): a list
    // made with other options would precompile blocks the game no longer enters, so each option
    // set keeps its own list (no suffix with both off, the lists made before the options).
    static const std::string tag = [] {
        const bool cond_branch =
            Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.jit_cond_branch");
        const bool cond_mov =
            Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.jit_cond_mov");
        return (cond_branch || cond_mov)
                   ? fmt::format("_t{}{}", cond_branch ? 1 : 0, cond_mov ? 1 : 0)
                   : std::string{};
    }();
    return fmt::format("{}jit/{:016X}_core{}{}.bin",
                       FileUtil::GetUserPath(FileUtil::UserPath::UserDir), title_id, core, tag);
}

std::vector<u64> GammaJitRead(const std::string& path) {
    std::vector<u64> list;
    FileUtil::IOFile f(path, "rb");
    if (!f.IsOpen()) {
        return list;
    }
    const u64 size = f.GetSize();
    list.resize(size / sizeof(u64));
    if (!list.empty() && f.ReadBytes(list.data(), list.size() * sizeof(u64)) != list.size() * sizeof(u64)) {
        list.clear();
    }
    return list;
}
} // namespace

void System::GammaJitWarm(bool after_load) {
    gamma_jit_pending.clear();
    gamma_jit_pending_pos.clear();
    if (!Settings::values.gammaos_jit_warmup || !app_loader) {
        return;
    }
    u64 title_id{};
    app_loader->ReadProgramId(title_id);
    gamma_jit_pending.resize(cpu_cores.size());
    gamma_jit_pending_pos.assign(cpu_cores.size(), 0);
    // Compile the earliest part (first-compile order) now, the rest in frame idle time.
    // persist.gammaos.azahar_nano.jit_warm_sync_ms: how long to compile before the game runs
    // at boot (behind the loading screen); jit_warm_load_ms: after a save state load, where
    // the game is frozen meanwhile (the full boot budget held loads for 2.4 s).
    static const auto boot_budget = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.jit_warm_sync_ms", v);
        return std::chrono::milliseconds(v[0] ? atoi(v) : 2000);
    }();
    static const auto load_budget = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.jit_warm_load_ms", v);
        return std::chrono::milliseconds(v[0] ? atoi(v) : 150);
    }();
    const auto sync_budget = after_load ? load_budget : boot_budget;
    for (u32 i = 0; i < static_cast<u32>(cpu_cores.size()); i++) {
        auto* dyn = dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get());
        if (!dyn) {
            continue;
        }
        gamma_jit_pending[i] = GammaJitRead(GammaJitPath(title_id, i));
        // Only blocks in the title's main code segment: that code is the same in every
        // session. Code loaded at run time (CRO modules) can sit at other addresses next time,
        // and compiling whatever is there now trips Dynarmic's emitter (Pokemon Sun aborted
        // with the saved blocks of its MovieLib module).
        if (const auto process = kernel->GetCurrentProcess(); process && process->codeset) {
            const auto& code = process->codeset->CodeSegment();
            const u64 begin = code.addr, end = u64{code.addr} + code.size;
            auto& l = gamma_jit_pending[i];
            const std::size_t before = l.size();
            std::erase_if(l, [&](u64 location) {
                const u64 pc = static_cast<u32>(location);
                return pc < begin || pc >= end;
            });
            if (l.size() != before) {
                LOG_INFO(Core, "JIT warm-up core {}: {} saved blocks outside the code segment "
                               "{:08X}-{:08X} skipped",
                         i, before - l.size(), begin, end);
            }
        } else {
            gamma_jit_pending[i].clear();
        }
        const auto& list = gamma_jit_pending[i];
        if (list.empty()) {
            continue;
        }
        const auto t0 = std::chrono::steady_clock::now();
        std::size_t pos = 0, compiled = 0;
        constexpr std::size_t chunk = 256;
        while (pos < list.size() && std::chrono::steady_clock::now() - t0 < sync_budget) {
            const std::size_t end = std::min(list.size(), pos + chunk);
            compiled += dyn->PrecompileBlocks(std::vector<u64>(list.begin() + pos, list.begin() + end));
            pos = end;
        }
        gamma_jit_pending_pos[i] = pos;
        const auto ms = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - t0).count();
        LOG_INFO(Core, "JIT warm-up core {}: {} blocks compiled in {:.0f} ms before running, {} left "
                       "for idle time ({} saved)",
                 i, compiled, ms, list.size() - pos, list.size());
    }
}

#if defined(GAMMAOS_PGO_GENERATE)
extern "C" int __llvm_profile_write_file(void);
extern "C" void __llvm_profile_reset_counters(void);
#endif

void System::GammaJitMapPoll() {
#if defined(GAMMAOS_PGO_GENERATE)
    // PGO profiling build: sys.gammaos.azahar_nano.pgo_dump=1 adds the counters so far to the
    // profile in /data/local/tmp/azpgo and starts counting again (the process is usually
    // killed, so the exit-time write never happens).
    {
        static u32 pgo_polls = 0;
        if ((pgo_polls++ % 30) == 0) {
            char v[PROP_VALUE_MAX] = {};
            if (__system_property_get("sys.gammaos.azahar_nano.pgo_dump", v) > 0 && v[0] == '1') {
                __system_property_set("sys.gammaos.azahar_nano.pgo_dump", "");
                const int r = __llvm_profile_write_file();
                __llvm_profile_reset_counters();
                LOG_INFO(Core, "PGO profile written ({})", r);
            }
        }
    }
#endif

    // Debug: sys.gammaos.azahar_nano.jitmap=1 writes the JIT block map of each core (host code
    // address -> guest PC) to the log folder, to attribute host profiler samples to guest code.
    {
        static u32 polls = 0;
        if ((polls++ % 30) == 0) {
            char v[PROP_VALUE_MAX] = {};
            if (__system_property_get("sys.gammaos.azahar_nano.jitmap", v) > 0 && v[0] == '1') {
                __system_property_set("sys.gammaos.azahar_nano.jitmap", "");
                for (u32 i = 0; i < static_cast<u32>(cpu_cores.size()); i++) {
                    if (auto* dyn = dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get())) {
                        dyn->DumpBlockMap(fmt::format("/data/system/azahar-nano/log/jitmap_core{}.txt", i));
                    }
                }
                // The main code region too (0x100000-0x400000), to disassemble hot guest code.
                if (FILE* f = std::fopen("/data/system/azahar-nano/log/guestmem_100000.bin", "wb")) {
                    static const std::array<u8, 0x1000> zero{};
                    for (VAddr a = 0x100000; a < 0x400000; a += 0x1000) {
                        const u8* page = memory->GetPointer(a);
                        std::fwrite(page ? page : zero.data(), 1, 0x1000, f);
                    }
                    std::fclose(f);
                }
                // Also the guest memory where Pokemon Sun loads MovieLib (relocations applied),
                // for finding its vtables and functions.
                if (FILE* f = std::fopen("/data/system/azahar-nano/log/guestmem_6ac000.bin", "wb")) {
                    for (VAddr a = 0x6AC000; a < 0x6BD000; a += 0x1000) {
                        const u8* page = memory->GetPointer(a);
                        static const std::array<u8, 0x1000> zero{};
                        std::fwrite(page ? page : zero.data(), 1, 0x1000, f);
                    }
                    std::fclose(f);
                }
                LOG_INFO(Core, "JIT block maps written");
            }
        }
    }
}

void System::GammaJitWarmStep(std::chrono::steady_clock::time_point until) {
    for (u32 i = 0; i < static_cast<u32>(gamma_jit_pending.size()); i++) {
        auto& list = gamma_jit_pending[i];
        auto& pos = gamma_jit_pending_pos[i];
        if (pos >= list.size()) {
            continue;
        }
        auto* dyn = i < cpu_cores.size() ? dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get()) : nullptr;
        if (!dyn) {
            continue;
        }
        // Small chunks: the step runs in the wait before a present and must not overrun it.
        constexpr std::size_t chunk = 2;
        while (pos < list.size() && std::chrono::steady_clock::now() < until) {
            const std::size_t end = std::min(list.size(), pos + chunk);
            dyn->PrecompileBlocks(std::vector<u64>(list.begin() + pos, list.begin() + end));
            pos = end;
        }
        if (pos >= list.size()) {
            LOG_INFO(Core, "JIT warm-up core {}: all {} saved blocks ready", i, list.size());
            list.clear();
            list.shrink_to_fit();
            pos = 0;
        }
    }
}

namespace {
// One writer of the warm-up files at a time (the periodic background save and the exit save).
std::mutex gamma_jit_save_mutex;
std::atomic<bool> gamma_jit_save_busy{false};
// Incremental collection for the periodic save: compile order position and cache generation
// reached per core (the emulation thread only).
std::array<std::size_t, 4> gamma_jit_cursor{};
std::array<u64, 4> gamma_jit_generation{};

// Keeps the saved order and appends the blocks the file does not have yet.
void GammaJitMerge(const std::string& path, u32 core, const std::vector<u64>& current) {
    if (current.empty()) {
        return;
    }
    auto list = GammaJitRead(path);
    const std::size_t saved_size = list.size();
    std::vector<u64> known(list);
    std::sort(known.begin(), known.end());
    for (const u64 v : current) {
        if (!std::binary_search(known.begin(), known.end(), v)) {
            list.push_back(v);
        }
    }
    if (list.size() == saved_size) {
        return; // nothing new
    }
    const std::string tmp = path + ".tmp";
    {
        FileUtil::IOFile f(tmp, "wb");
        if (!f.IsOpen() ||
            f.WriteBytes(list.data(), list.size() * sizeof(u64)) != list.size() * sizeof(u64)) {
            return;
        }
    }
    FileUtil::Rename(tmp, path);
    LOG_INFO(Core, "JIT warm-up core {}: saved {} blocks", core, list.size());
}
} // namespace

void System::GammaJitSave(bool background) {
    if (!Settings::values.gammaos_jit_warmup || !app_loader) {
        return;
    }
    u64 title_id{};
    app_loader->ReadProgramId(title_id);

    // GammaOS: the periodic save (every 30 s) took 10 to 15 ms of the emulation thread (the
    // full block list, then the file read, sort, merge and write), one long frame each time.
    // In the background mode the emulation thread only collects the blocks compiled since the
    // last save; a worker thread merges and writes them.
    // persist.gammaos.azahar_nano.jit_save_bg=0 keeps the save on the emulation thread.
    static const bool bg_save =
        Common::GammaOS::TweakEnabled("persist.gammaos.azahar_nano.jit_save_bg");
    if (background && bg_save) {
        if (gamma_jit_save_busy.exchange(true)) {
            return; // the previous save is still writing: try again next time
        }
        std::vector<std::pair<u32, std::vector<u64>>> fresh;
        for (u32 i = 0; i < static_cast<u32>(cpu_cores.size()) && i < gamma_jit_cursor.size();
             i++) {
            auto* dyn = dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get());
            if (!dyn) {
                continue;
            }
            auto blocks = dyn->GetCompiledBlocksSince(gamma_jit_cursor[i], gamma_jit_generation[i]);
            if (!blocks.empty()) {
                fresh.emplace_back(i, std::move(blocks));
            }
        }
        if (fresh.empty()) {
            gamma_jit_save_busy = false;
            return;
        }
        std::thread([title_id, fresh = std::move(fresh)] {
            std::scoped_lock lock{gamma_jit_save_mutex};
            FileUtil::CreateFullPath(GammaJitPath(title_id, 0));
            for (const auto& [core, blocks] : fresh) {
                GammaJitMerge(GammaJitPath(title_id, core), core, blocks);
            }
            gamma_jit_save_busy = false;
        }).detach();
        return;
    }

    std::scoped_lock lock{gamma_jit_save_mutex};
    FileUtil::CreateFullPath(GammaJitPath(title_id, 0));
    for (u32 i = 0; i < static_cast<u32>(cpu_cores.size()); i++) {
        auto* dyn = dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get());
        if (!dyn) {
            continue;
        }
        GammaJitMerge(GammaJitPath(title_id, i), i, dyn->GetCompiledBlocks()); // first-compile order
    }
}

} // namespace Core
