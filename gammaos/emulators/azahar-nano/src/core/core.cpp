#include <cstdio>
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

System::ResultStatus System::RunLoop(bool tight_loop) {
    status = ResultStatus::Success;

    if (!IsPoweredOn()) {
        return ResultStatus::ErrorNotInitialized;
    }

    // GammaOS async GPU: hand over the interrupts of command lists the worker finished.
    if (gpu) {
        gpu->AsyncPoll();
    }

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

    Signal signal{Signal::None};
    u32 param{};
    {
        std::scoped_lock lock{signal_mutex};
        if (current_signal != Signal::None) {
            signal = current_signal;
            param = signal_param;
            current_signal = Signal::None;
        }
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
            System::LoadState(slot);
            LOG_INFO(Core, "Load completed");
            // GammaOS: the loaded system starts with no frame history (the realtime audio
            // clock would read a scale of 0 and stretch the audio tick a hundredfold).
            ResetFrameTiming();
            GammaJitWarm();
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
        s64 max_slice = Timing::MAX_SLICE_LENGTH;
        for (const auto& cpu_core : cpu_cores) {
            running_core = cpu_core.get();
            kernel->SetRunningCPU(running_core);
            cpu_core->GetTimer().Advance();
            cpu_core->PrepareReschedule();
            kernel->GetThreadManager(cpu_core->GetID()).Reschedule();
            max_slice = std::min(max_slice, cpu_core->GetTimer().GetMaxSliceLength());
        }
        for (auto& cpu_core : cpu_cores) {
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
                if (tight_loop) {
                    cpu_core->Run();
                } else {
                    cpu_core->Step();
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

    memory = std::make_unique<Memory::MemorySystem>(*this);

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

    custom_tex_manager = std::make_unique<VideoCore::CustomTexManager>(*this);

    auto gsp = service_manager->GetService<Service::GSP::GSP_GPU>("gsp::Gpu");
    gpu = std::make_unique<VideoCore::GPU>(*this, emu_window, secondary_window);
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

    gpu.reset();
    if (!is_deserializing) {
        lle_modules.clear();
#ifdef ENABLE_GDBSTUB
        GDBStub::Shutdown();
#endif
        perf_stats.reset();
        app_loader.reset();
    }
    custom_tex_manager.reset();
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

    memory.reset();

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
    return fmt::format("{}jit/{:016X}_core{}.bin",
                       FileUtil::GetUserPath(FileUtil::UserPath::UserDir), title_id, core);
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

void System::GammaJitWarm() {
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
    // persist.gammaos.azahar_nano.jit_warm_sync_ms: how long to compile before the game runs.
    static const auto sync_budget = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.jit_warm_sync_ms", v);
        return std::chrono::milliseconds(v[0] ? atoi(v) : 2000);
    }();
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

void System::GammaJitMapPoll() {
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

void System::GammaJitSave() {
    if (!Settings::values.gammaos_jit_warmup || !app_loader) {
        return;
    }
    u64 title_id{};
    app_loader->ReadProgramId(title_id);
    FileUtil::CreateFullPath(GammaJitPath(title_id, 0));
    for (u32 i = 0; i < static_cast<u32>(cpu_cores.size()); i++) {
        auto* dyn = dynamic_cast<ARM_Dynarmic*>(cpu_cores[i].get());
        if (!dyn) {
            continue;
        }
        const auto current = dyn->GetCompiledBlocks();   // first-compile order
        if (current.empty()) {
            continue;
        }
        const std::string path = GammaJitPath(title_id, i);
        auto list = GammaJitRead(path);   // keep the saved order, append what is new
        const std::size_t saved_size = list.size();
        std::vector<u64> known(list);
        std::sort(known.begin(), known.end());
        for (const u64 v : current) {
            if (!std::binary_search(known.begin(), known.end(), v)) {
                list.push_back(v);
            }
        }
        if (list.size() == saved_size) {
            continue;   // nothing new
        }
        const std::string tmp = path + ".tmp";
        {
            FileUtil::IOFile f(tmp, "wb");
            if (!f.IsOpen() ||
                f.WriteBytes(list.data(), list.size() * sizeof(u64)) != list.size() * sizeof(u64)) {
                continue;
            }
        }
        FileUtil::Rename(tmp, path);
        LOG_INFO(Core, "JIT warm-up core {}: saved {} blocks", i, list.size());
    }
}

} // namespace Core
