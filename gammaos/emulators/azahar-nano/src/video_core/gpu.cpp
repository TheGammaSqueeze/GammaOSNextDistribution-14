// Copyright 2023-2026 Citra Emulator Project / Azahar Emulator Project
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#include <dlfcn.h>
#include <sched.h>
#include <sys/resource.h>
#include <sys/system_properties.h>
#include <unistd.h>
#include <algorithm>
#include <cerrno>
#include <cstring>
#include <atomic>
#include <chrono>
#include <string>
#include <condition_variable>
#include <deque>
#include <mutex>
#include <thread>
#include <vector>
#include "common/archives.h"
#include "common/gammaos_tweak.h"
#include "common/hacks/hack_manager.h"
#include "common/microprofile.h"
#include "core/core.h"
#include "core/core_timing.h"
#include "core/hle/service/gsp/gsp_gpu.h"
#include "core/hle/service/plgldr/plgldr.h"
#include "core/loader/loader.h"
#include "video_core/debug_utils/debug_utils.h"
#include "video_core/gpu.h"
#include "video_core/gpu_debugger.h"
#include "video_core/gpu_impl.h"
#include "video_core/pica/pica_core.h"
#include "video_core/pica/regs_lcd.h"
#include "video_core/renderer_base.h"
#include "video_core/renderer_vulkan/vk_gammaos_game_prop.h"
#include "video_core/renderer_software/sw_blitter.h"
#include "video_core/right_eye_disabler.h"
#include "video_core/video_core.h"

namespace VideoCore {

constexpr VAddr VADDR_LCD = 0x1ED02000;
constexpr VAddr VADDR_GPU = 0x1EF00000;

class DelayGenerator {
private:
    DelayGenerator() = default;

    // Average transfer speed based on measurements taken from real
    // hardware. 4 different modes have been taken into consideration:
    // RAM -> RAM, RAM -> VRAM, VRAM -> RAM and VRAM -> VRAM.
    // Furthermore, measurements are split into DMA transfers and tex
    // copies. For simplicity, we will assume fills are as fast as
    // texture copies.

    static constexpr double mibps_to_ns_per_byte(double mib_per_sec) {
        return 1'000'000'000.0 / (mib_per_sec * 1024.0 * 1024.0);
    }

    static constexpr std::array<std::array<double, 4>, 2> speed_mibps = {
        {{
             190.0, // DMA RAMTORAM
             310.0, // DMA RAMTOVRAM
             380.0, // DMA VRAMTORAM
             380.0, // DMA VRAMTOVRAM
         },
         {
             450.0,  // TEX RAMTORAM
             3100.0, // TEX RAMTOVRAM
             5400.0, // TEX VRAMTORAM
             5400.0, // TEX VRAMTOVRAM
         }}};

public:
    enum class CopyMode {
        RAMTORAM,
        RAMTOVRAM,
        VRAMTORAM,
        VRAMTOVRAM,
    };

    static CopyMode GetCopyMode(bool input_vram, bool output_vram) {
        if (!input_vram && !output_vram) {
            return CopyMode::RAMTORAM;
        } else if (!input_vram && output_vram) {
            return CopyMode::RAMTOVRAM;
        } else if (input_vram && !output_vram) {
            return CopyMode::VRAMTORAM;
        } else {
            return CopyMode::VRAMTOVRAM;
        }
    }

    static u64 CalculateDelayNanoseconds(CopyMode mode, bool is_textre, size_t size) {
        double base_ns_per_byte =
            mibps_to_ns_per_byte(speed_mibps[is_textre][static_cast<u32>(mode)]);

        return static_cast<u64>(size * base_ns_per_byte);
    }
};

namespace GammaSnapshot {
thread_local const Snapshot* t_active = nullptr;
thread_local std::shared_ptr<const void> t_owner;
}

// Intervals a list's draws read, collected while the list is walked at submit.
struct GPU::DrawRanges {
    std::vector<std::pair<PAddr, u32>> intervals;
    u64 total = 0;
};

namespace {
// True on the GPU worker thread (it never waits for itself).
thread_local bool t_gpu_worker = false;
// Diagnostics: P3D interrupts the worker raised (credited or not), per thread.
thread_local u32 t_worker_p3d = 0;

constexpr std::array<u32, 16> kMaskBytes = {
    0x00000000, 0x000000FF, 0x0000FF00, 0x0000FFFF, 0x00FF0000, 0x00FF00FF, 0x00FFFF00, 0x00FFFFFF,
    0xFF000000, 0xFF0000FF, 0xFF00FF00, 0xFF00FFFF, 0xFFFF0000, 0xFFFF00FF, 0xFFFFFF00, 0xFFFFFFFF,
};
constexpr u64 kSnapshotCap = 16u * 1024 * 1024; // per list, bytes

// Diagnostics: sys.gammaos.azahar_nano.gx_log=1 (polled every 256 calls) logs the GSP command
// stream as the emulation thread issues it, and the worker's uncredited interrupts.
bool GxLogOn() {
    // async_gpu_nolog=1: the switch is never polled (cost measurement).
    static const bool never = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_nolog", v);
        return v[0] == '1';
    }();
    if (never) {
        return false;
    }
    static std::atomic<bool> on{false};
    static std::atomic<u32> polls{0};
    if ((polls.fetch_add(1) & 0xF) == 0) {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("sys.gammaos.azahar_nano.gx_log", v);
        on.store(v[0] == '1');
    }
    return on.load();
}
// The GPU whose worker AsyncGpuDrain waits for (one emulated system per process).
std::atomic<GPU*> s_async_gpu{nullptr};
} // Anonymous namespace

// GammaOS async GPU worker: a FIFO of GPU jobs run on one thread, and the interrupts those
// jobs raise, held for the emulation thread. Mode 1 (persist.gammaos.azahar_nano.async_gpu=1)
// queues command lists only and every other GPU access waits for the worker first; mode 2
// (async_gpu=2) queues every GSP register write and GPU command as well, in order, so a game
// that writes the next list's registers while the previous list runs (Kirby: Planet Robobot,
// 8 lists a frame submitted through WriteHWRegs) no longer waits for that list at each write.
// Only reads of GPU state, CPU accesses to GPU memory and the vblank wait for the worker.
struct GPU::Async {
    std::thread thread;
    std::mutex mutex;
    std::condition_variable work_cv;
    std::condition_variable idle_cv;
    std::deque<std::function<void()>> jobs;
    bool busy = false;
    bool stop = false;
    int mode = 1;
    // Mode 3: the interrupts of a queued list, fill or transfer are raised when the guest
    // submits it (the guest goes on while the worker draws); each one is a credit that drops
    // the same interrupt when the worker raises it. The lists are copied at submit time, and
    // shadow holds the GSP register writes as the emulation thread issued them (the worker
    // applies them later), for the addresses and sizes those decisions need.
    bool eager = false;
    std::array<std::atomic<int>, static_cast<std::size_t>(Service::GSP::InterruptId::COUNT)>
        credit{};
    std::unique_ptr<Pica::PicaCore::Regs> shadow;
    // The PICA internal registers as the submitted lists leave them (replayed at submit),
    // seeded from the real ones whenever the queue is empty.
    std::unique_ptr<Pica::RegsInternal> shadow_internal;
    std::atomic<int> pending{0};
    std::atomic<u64> done{0}; // jobs finished, for the progress wait

    std::mutex interrupt_mutex;
    std::vector<std::pair<Service::GSP::InterruptId, u64>> interrupts;
    std::atomic<int> interrupt_count{0};  // lets the per-slice poll skip the lock
    Service::GSP::InterruptHandler deliver;

    void Run() {
        t_gpu_worker = true;
        pthread_setname_np(pthread_self(), "AzaharGpu");
        // Same priority as the emulation thread: the two run side by side.
        setpriority(PRIO_PROCESS, static_cast<id_t>(gettid()), -10);
#ifdef ANDROID
        // With a real-time emulation thread (emu_rt) a normal worker it wakes often queues on
        // its own core; one priority below it, the worker goes to a free core at once (as the
        // VulkanWorker does).
        {
            const int emu_prio = Common::GammaOS::EmuRtPriority();
            if (emu_prio > 1) {
                sched_param sp{};
                sp.sched_priority = emu_prio - 1;
                if (sched_setscheduler(0, SCHED_FIFO, &sp) != 0) {
                    LOG_WARNING(HW_GPU, "async GPU worker: SCHED_FIFO refused ({})", errno);
                }
            }
        }
#endif
        std::unique_lock lock{mutex};
        while (true) {
            work_cv.wait(lock, [this] { return stop || !jobs.empty(); });
            if (jobs.empty()) {
                break;
            }
            auto job = std::move(jobs.front());
            jobs.pop_front();
            busy = true;
            lock.unlock();
            job();
            lock.lock();
            busy = false;
            pending.fetch_sub(1, std::memory_order_release);
            done.fetch_add(1, std::memory_order_release);
            // Every finished job wakes a progress wait; an empty queue wakes a drain.
            idle_cv.notify_all();
        }
    }

    // Waits until the worker finished at least one more job (or ran dry), then returns: the
    // emulation thread idles with every emulated thread waiting on a GPU interrupt, and the
    // interrupt of the job that just finished may be what one of them waits for.
    void WaitProgress() {
        const u64 seen = done.load(std::memory_order_acquire);
        std::unique_lock lock{mutex};
        idle_cv.wait(lock, [&] {
            return (jobs.empty() && !busy) || done.load(std::memory_order_acquire) != seen ||
                   interrupt_count.load(std::memory_order_acquire) != 0;
        });
    }

    void Enqueue(std::function<void()> job) {
        {
            std::scoped_lock lock{mutex};
            jobs.push_back(std::move(job));
            pending.fetch_add(1, std::memory_order_release);
        }
        work_cv.notify_one();
    }

    void WaitIdle() {
        std::unique_lock lock{mutex};
        idle_cv.wait(lock, [this] { return jobs.empty() && !busy; });
    }

    // Waits until at most n jobs are queued or running.
    void WaitPendingAtMost(int n) {
        std::unique_lock lock{mutex};
        idle_cv.wait(lock, [this, n] { return pending.load(std::memory_order_acquire) <= n; });
    }

    // Returns how many interrupts it delivered.
    std::size_t Deliver() {
        if (interrupt_count.load(std::memory_order_acquire) == 0) {
            return 0;
        }
        std::vector<std::pair<Service::GSP::InterruptId, u64>> ready;
        {
            std::scoped_lock lock{interrupt_mutex};
            ready.swap(interrupts);
            interrupt_count.store(0, std::memory_order_release);
        }
        for (const auto& [id, delay] : ready) {
            if (deliver) {
                deliver(id, delay);
            }
            if (GxLogOn()) {
                LOG_INFO(HW_GPU, "gx delivered worker interrupt {}", static_cast<int>(id));
            }
        }
        return ready.size();
    }
};

void AsyncGpuDrain() {
    if (GPU* gpu = s_async_gpu.load(std::memory_order_acquire)) {
        gpu->AsyncDrain(__builtin_return_address(0));
    }
}

// Diagnostics: which callers had to wait for the worker, how often, how long (logged
// every two seconds with persist.gammaos.azahar_nano.async_gpu_log=1).
namespace {
struct WaitSite {
    void* site;
    u32 count;
    double ms;
};
std::vector<WaitSite> s_wait_sites;
std::chrono::steady_clock::time_point s_wait_log_last{};
} // Anonymous namespace

void GPU::AsyncDrain(void* caller) {
    if (!async || t_gpu_worker) {
        return;
    }
    if (async->pending.load(std::memory_order_acquire) != 0) {
        static const bool log = [] {
            char v[PROP_VALUE_MAX] = {};
            __system_property_get("persist.gammaos.azahar_nano.async_gpu_log", v);
            return v[0] == '1';
        }();
        if (log) {
            const auto t0 = std::chrono::steady_clock::now();
            async->WaitIdle();
            const auto t1 = std::chrono::steady_clock::now();
            void* site = caller ? caller : __builtin_return_address(0);
            const double ms = std::chrono::duration<double, std::milli>(t1 - t0).count();
            auto it = std::find_if(s_wait_sites.begin(), s_wait_sites.end(),
                                   [site](const WaitSite& w) { return w.site == site; });
            if (it == s_wait_sites.end()) {
                s_wait_sites.push_back({site, 1, ms});
            } else {
                it->count++;
                it->ms += ms;
            }
            if (t1 - s_wait_log_last > std::chrono::seconds(2)) {
                s_wait_log_last = t1;
                std::string line;
                for (const auto& w : s_wait_sites) {
                    Dl_info info{};
                    if (dladdr(w.site, &info) && info.dli_fbase) {
                        line += fmt::format(
                            " {}+{:#x}:{}x/{:.1f}ms", info.dli_sname ? info.dli_sname : "lib",
                            reinterpret_cast<uintptr_t>(w.site) -
                                reinterpret_cast<uintptr_t>(info.dli_sname ? info.dli_saddr
                                                                           : info.dli_fbase),
                            w.count, w.ms);
                    } else {
                        line += fmt::format(" {}:{}x/{:.1f}ms", w.site, w.count, w.ms);
                    }
                }
                LOG_INFO(HW_GPU, "async GPU waits:{}", line);
                s_wait_sites.clear();
            }
        } else {
            async->WaitIdle();
        }
    }
    async->Deliver();
}

void GPU::AsyncPoll() {
    if (!async || t_gpu_worker) {
        return;
    }
    async->Deliver();
}

bool GPU::AsyncWaitProgress() {
    if (!async || t_gpu_worker) {
        return false;
    }
    // Mode 3: the guest already has the interrupts of everything queued, so an idle run loop
    // is a real idle (the clock may skip ahead); waiting for the worker's current job here
    // cost Kirby 7 ms a frame (an idle after every SleepThread storm, each up to a list long).
    if (async->eager) {
        return async->Deliver() != 0;
    }
    if (async->pending.load(std::memory_order_acquire) != 0) {
        if (async->mode >= 2) {
            async->WaitProgress();
        } else {
            async->WaitIdle();
        }
    }
    return async->Deliver() != 0;
}

bool GPU::AsyncQueued() const {
    return async && async->mode >= 2 && !t_gpu_worker;
}

// Mode 3: raise an interrupt now for work just queued; the worker's own one is dropped.
void GPU::EagerSignal(Service::GSP::InterruptId id, u32 count) {
    // Raised from inside the guest's GSP call: on hardware no interrupt can land before that
    // call returns, and Kirby: Planet Robobot crawled at 1 fps when one did (a wait of a second
    // per frame). A short emulated delay puts it after the return.
    // persist.gammaos.azahar_nano.async_gpu_irq_us: that delay in microseconds (default 50).
    static const u64 delay_ns = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_irq_us", v);
        return (v[0] ? static_cast<u64>(atoi(v)) : 50u) * 1000u;
    }();
    // The GSP drops interrupt delays unless 3DS GPU timings are simulated, so the delay is a
    // core timing event of the GPU's own.
    for (u32 i = 0; i < count; i++) {
        async->credit[static_cast<std::size_t>(id)].fetch_add(1, std::memory_order_acq_rel);
        if (delay_ns == 0 || !impl->eager_event) {
            impl->signal_interrupt(id, 0);
        } else {
            impl->Timing().ScheduleEvent(nsToCycles(static_cast<s64>(delay_ns)), impl->eager_event,
                                         static_cast<uintptr_t>(id));
        }
    }
}

// Mode 3: copies a command list the guest just submitted and counts the interrupts it will
// raise (writes of the irq_request register: one P3D each, as HandleSpecialReg raises them).
GPU::CmdListCopy GPU::CopyCmdList(PAddr addr, u32 size, u32& irqs, u32 depth,
                                  DrawRanges* ranges, u32* cb_parent,
                                  std::vector<PAddr>* visited) {
    irqs = 0;
    CmdListCopy copy{addr, size, {}, {}, {}};
    // Kirby's frame is a linked list of passes (launcher -> list in VRAM -> launcher -> ...,
    // two levels per pass, the irq_request at the very end): the chain is followed as far as
    // the worker follows it, with a loop guard instead of a depth of 4.
    std::vector<PAddr> visited_local;
    if (!visited) {
        visited = &visited_local;
    }
    if (std::find(visited->begin(), visited->end(), addr) != visited->end()) {
        return copy; // a cycle: the worker would spin too, nothing sane to copy
    }
    visited->push_back(addr);
    auto& words = copy.words;
    // Top level: the register shadow starts from the real registers when nothing is queued
    // (the worker is idle, nothing writes them), the draws' ranges are collected and copied.
    DrawRanges local;
    const bool top = ranges == nullptr;
    if (top) {
        ranges = &local;
        if (async && async->eager && async->pending.load(std::memory_order_acquire) == 0) {
            std::memcpy(async->shadow_internal.get(), &impl->pica.regs.internal,
                        sizeof(Pica::RegsInternal));
        }
    }
    Pica::RegsInternal& sh = *async->shadow_internal;
    // The declared size is the buffer, not the list: it may run past the end of the physical
    // region (clamped here). A list can hold several irq_requests without autostop (Kirby:
    // Planet Robobot froze when the copy ended at the first one), so the whole buffer is
    // copied and every irq_request counted.
    const auto [src, avail] = impl->memory.GetPhysicalSpan(addr);
    if (!src || size == 0 || avail < sizeof(u32)) {
        return copy;
    }
    const u32 n = static_cast<u32>(std::min<std::size_t>(size, avail) / sizeof(u32));
    const u32* in = reinterpret_cast<const u32*>(src);
    constexpr u32 irq_request_id = 0x10;
    // Command buffer registers a list may write to start another list (size0 size1 addr0
    // addr1 trigger0 trigger1), seeded from the GSP writes the shadow saw.
    // The GSP shadow is the GPU register file (the internal block sits past 0x400 in it); the
    // command ids inside a list are internal register indices (trigger[0] = 0x23C). Comparing
    // list ids against the GPU-file index never matched: chained lists were never copied nor
    // credited, so the worker read them from guest memory the game had rewritten (garbage
    // lists) and raised their interrupts without credit (the game's count drifted, and it
    // stalled once the CacheFlush drain no longer hid it).
    constexpr u32 cb_first = GPU_REG_INDEX(internal.pipeline.command_buffer.size[0]);
    constexpr u32 cb_first_int = PICA_REG_INDEX(pipeline.command_buffer.size[0]);
    constexpr u32 cb_trigger0_int = PICA_REG_INDEX(pipeline.command_buffer.trigger[0]);
    // The command buffer registers are one state the chained lists share: a launcher writes
    // addr[1]/size[1], the list it starts fires trigger[1] (Kirby: launcher -> list in VRAM ->
    // 0x20-byte trailer that holds the irq_request). Re-seeding them per depth lost the
    // trailer (address 0, never copied, its interrupt never credited).
    u32 cb_local[6] = {};
    u32* cb = cb_parent ? cb_parent : cb_local;
    if (!cb_parent && async && async->shadow) {
        for (u32 k = 0; k < 6; k++) {
            cb[k] = async->shadow->reg_array[cb_first + k];
        }
    }
    constexpr u32 draw_id = PICA_REG_INDEX(pipeline.trigger_draw);
    constexpr u32 draw_indexed_id = PICA_REG_INDEX(pipeline.trigger_draw_indexed);
    const auto on_draw = [&](bool indexed) {
        if (ranges->total >= kSnapshotCap) {
            return;
        }
        const auto& va = sh.pipeline.vertex_attributes;
        const PAddr base = va.GetPhysicalBaseAddress();
        const u32 count = sh.pipeline.num_vertices;
        if (base == 0 || count == 0) {
            return;
        }
        u32 vmin, vmax;
        if (indexed) {
            const u32 isize = sh.pipeline.index_array.format != 0 ? 2 : 1;
            const PAddr iaddr = base + sh.pipeline.index_array.offset;
            const auto [ip, avail] = impl->memory.GetPhysicalSpan(iaddr);
            if (!ip || avail < static_cast<std::size_t>(count) * isize) {
                return;
            }
            ranges->intervals.emplace_back(iaddr, count * isize);
            ranges->total += count * isize;
            vmin = 0xFFFF;
            vmax = 0;
            if (isize == 2) {
                const u16* p16 = reinterpret_cast<const u16*>(ip);
                for (u32 i = 0; i < count; i++) {
                    vmin = std::min<u32>(vmin, p16[i]);
                    vmax = std::max<u32>(vmax, p16[i]);
                }
            } else {
                for (u32 i = 0; i < count; i++) {
                    vmin = std::min<u32>(vmin, ip[i]);
                    vmax = std::max<u32>(vmax, ip[i]);
                }
            }
        } else {
            vmin = sh.pipeline.vertex_offset;
            vmax = sh.pipeline.vertex_offset + count - 1;
        }
        const u32 vnum = vmax - vmin + 1;
        for (const auto& loader : va.attribute_loaders) {
            if (loader.component_count == 0 || loader.byte_count == 0) {
                continue;
            }
            const PAddr daddr = base + loader.data_offset + vmin * loader.byte_count;
            const u32 dsize = loader.byte_count * vnum;
            ranges->intervals.emplace_back(daddr, dsize);
            ranges->total += dsize;
        }
    };
    // GammaOS: the credit must equal what the worker will raise for this list, as
    // HandleSpecialReg does: a P3D only when a byte of the written irq_request equals one of
    // irq_compare, and the list stops there when irq_autostop is set (words past that point are
    // never run, so they neither count nor contribute draws to the snapshot). Counting every
    // irq_request word in the buffer (the old behaviour, async_gpu_exact_irq=0) credited
    // interrupts the worker never raised and missed none it did, which let the credits drift
    // against the game's own count once the CacheFlush drain no longer resynchronised them.
    static const bool exact_irq = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_exact_irq", v);
        return v[0] != '0';
    }();
    // async_gpu_chain_copy=1: chained lists are copied and counted at submit. Default off:
    // Kirby's frame is a GPU command ring the game extends while the worker consumes it (900
    // hops per launcher on average, addresses reused within one run), which no submit-time
    // copy can capture; the worker reads chains live and the CacheFlush drain bounds its lag.
    // Copying what exists at submit only costs the emulation thread (build 62: 47.7 fps
    // against 51.5).
    static const bool chain_copy = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_chain_copy", v);
        return v[0] == '1';
    }();
    bool stop = false;
    const auto any_byte_match = [](u32 a, u32 b) {
        return ((a & 0xFF) == (b & 0xFF)) || (((a >> 8) & 0xFF) == ((b >> 8) & 0xFF)) ||
               (((a >> 16) & 0xFF) == ((b >> 16) & 0xFF)) ||
               (((a >> 24) & 0xFF) == ((b >> 24) & 0xFF));
    };
    const auto on_write = [&](u32 id, u32 value, u32 mask) {
        if (stop) {
            return;
        }
        if (id < Pica::RegsInternal::NUM_REGS) {
            const u32 m = kMaskBytes[mask & 0xF];
            sh.reg_array[id] = (sh.reg_array[id] & ~m) | (value & m);
        }
        if (id == irq_request_id) {
            if (!exact_irq) {
                irqs++;
            } else if (any_byte_match(sh.reg_array[id], sh.irq_compare)) {
                irqs++;
                if (sh.irq_autostop) {
                    stop = true;
                }
            } else if (GxLogOn()) {
                LOG_INFO(HW_GPU, "gx irq_request {:08X} (mask {:X}) no match with compare {:08X}",
                         sh.reg_array[id], mask, sh.irq_compare);
            }
        } else if (id == draw_id || id == draw_indexed_id) {
            on_draw(id == draw_indexed_id);
        } else if (id >= cb_first_int && id < cb_first_int + 6) {
            cb[id - cb_first_int] = value;
            // PicaCore::HandleSpecialReg moves its cursor to the named list on ANY write of a
            // trigger register (no value check): an empty or null target ends processing there.
            // The replay mirrors that: the parent's remaining words never run, and a target
            // with no size is the end of the chain.
            if ((id == cb_trigger0_int || id == cb_trigger0_int + 1) && exact_irq) {
                const u32 slot = id - cb_trigger0_int;
                const PAddr caddr = cb[2 + slot] << 3;
                const u32 csize = cb[slot] << 3;
                if (GxLogOn()) {
                    LOG_INFO(HW_GPU, "gx chain depth {} slot {} value {} -> {:08X}/{:X}", depth,
                             slot, value, caddr, csize);
                }
                stop = true;
                if (caddr != 0 && csize != 0 && depth < 64 && chain_copy) {
                    u32 cirqs = 0;
                    copy.chained.push_back(CopyCmdList(caddr, csize, cirqs, depth + 1, ranges, cb, visited));
                    irqs += cirqs;
                }
                return;
            }
            if ((id == cb_trigger0_int || id == cb_trigger0_int + 1) && value != 0 && depth < 64) {
                const u32 slot = id - cb_trigger0_int;
                const PAddr caddr = cb[2 + slot] << 3;
                const u32 csize = cb[slot] << 3;
                if (GxLogOn()) {
                    const auto [cp, cavail] = impl->memory.GetPhysicalSpan(caddr);
                    LOG_INFO(HW_GPU, "gx chain depth {} slot {} {:08X}/{:X} span {} avail {:X}",
                             depth, slot, caddr, csize, cp != nullptr, cavail);
                }
                if (caddr != 0 && csize != 0 && chain_copy) {
                    // async_gpu_chain_credit=0: a chained list is copied but its interrupt
                    // waits for the worker (build 14 behaviour, 53 fps on Kirby).
                    static const bool chain_credit = [] {
                        char v[PROP_VALUE_MAX] = {};
                        __system_property_get("persist.gammaos.azahar_nano.async_gpu_chain_credit", v);
                        return v[0] != '0';
                    }();
                    u32 cirqs = 0;
                    copy.chained.push_back(CopyCmdList(caddr, csize, cirqs, depth + 1, ranges, cb, visited));
                    if (GxLogOn()) {
                        const auto& cw = copy.chained.back().words;
                        std::string w;
                        for (std::size_t k = 0; k < cw.size() && k < 16; k++) {
                            w += fmt::format(" {:08X}", cw[k]);
                        }
                        LOG_INFO(HW_GPU, "gx chain copied {:08X}: {} words, {} irqs, compare {:08X}:{}",
                                 caddr, cw.size(), cirqs, sh.irq_compare, w);
                    }
                    if (chain_credit) {
                        irqs += cirqs;
                    }
                }
                // The trigger replaces the running list (PicaCore resets its cursor to the
                // chained one): the words after it never run.
                if (exact_irq) {
                    stop = true;
                }
            }
        }
    };
    u32 i = 0;
    while (i + 1 < n && !stop) {
        if (i % 2 != 0) {
            i++;
            continue;
        }
        const u32 value = in[i];
        const u32 header = in[i + 1];
        const u32 id = header & 0xFFFF;
        const u32 mask = (header >> 16) & 0xF;
        const u32 extra = (header >> 20) & 0xFF;
        const bool group = (header >> 31) != 0;
        on_write(id, value, mask);
        for (u32 k = 0; k < extra && i + 2 + k < n; k++) {
            on_write(group ? id + 1 + k : id, in[i + 2 + k], mask);
        }
        i += 2 + extra;
    }
    words.resize(n);
    std::memcpy(words.data(), in, n * sizeof(u32));
    if (top) {
        TakeSnapshot(copy, local);
    }
    return copy;
}

// Merges the draws' intervals and copies them from guest memory (valid now: the guest has not
// had the list's interrupt yet).
void GPU::TakeSnapshot(CmdListCopy& copy, DrawRanges& ranges) {
    auto& iv = ranges.intervals;
    if (iv.empty()) {
        return;
    }
    std::sort(iv.begin(), iv.end());
    std::vector<std::pair<PAddr, u32>> merged;
    for (const auto& [a, s] : iv) {
        if (!merged.empty() &&
            static_cast<u64>(a) <= static_cast<u64>(merged.back().first) + merged.back().second) {
            const u64 end = std::max<u64>(static_cast<u64>(merged.back().first) + merged.back().second,
                                          static_cast<u64>(a) + s);
            merged.back().second = static_cast<u32>(end - merged.back().first);
        } else {
            merged.emplace_back(a, s);
        }
    }
    copy.data.ranges.reserve(merged.size());
    for (const auto& [a, s] : merged) {
        const auto [src, avail] = impl->memory.GetPhysicalSpan(a);
        if (!src || avail == 0) {
            continue;
        }
        const u32 n = static_cast<u32>(std::min<std::size_t>(s, avail));
        GammaSnapshot::Range r{a, n, {}};
        r.bytes.resize(n);
        std::memcpy(r.bytes.data(), src, n);
        copy.data.ranges.push_back(std::move(r));
    }
}

// The chained copy lookup the PICA core calls from a list's command buffer trigger (worker).
const u32* GPU::ChainedCopyFor(PAddr addr, u32& size) const {
    const CmdListCopy* cur = impl->cmd_copy;
    if (!cur) {
        return nullptr;
    }
    // Mode 3: a chained list the submit-time replay did not copy is never read from guest
    // memory. The game already has the list's interrupt and reuses its buffers (Kirby DMAs
    // the next pass's lists into the same VRAM ring right after submitting): the worker then
    // ran overwritten lists, looped through a ring of 0x60-byte lists tens of thousands of
    // times and raised interrupts the game never asked for. An empty list ends the chain
    // where the replay ended it instead.
    static const bool live_chains = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_live_chains", v);
        return v[0] != '0'; // default: a chain without a copy is read from guest memory
    }();
    static constexpr u32 kEmpty[2] = {0, 0};
    // Depth-first over the copies of this submit (no allocation: this runs at every chain
    // trigger): the chained list the worker reaches is the one whose address matches.
    struct Walk {
        static const u32* Find(const CmdListCopy& c, PAddr addr, u32& size) {
            for (const auto& ch : c.chained) {
                if (ch.addr == addr && ch.size == size && !ch.words.empty()) {
                    size = static_cast<u32>(ch.words.size() * sizeof(u32));
                    return ch.words.data();
                }
                if (const u32* w = Find(ch, addr, size)) {
                    return w;
                }
            }
            return nullptr;
        }
    };
    if (const u32* w = Walk::Find(*cur, addr, size)) {
        return w;
    }
    if (live_chains || !async || !async->eager) {
        return nullptr;
    }
    if (GxLogOn()) {
        LOG_INFO(HW_GPU, "gx worker chain {:08X}/{:X} has no copy: chain ends here", addr, size);
    }
    size = 0;
    return kEmpty;
}

// The worker runs a list from its copy only when the registers still name that list (a list
// or a GSP command can set the command buffer registers behind the shadow's back); otherwise
// it reads guest memory as the synchronous path does.
const u32* GPU::CopyFor(PAddr addr, u32& size) const {
    if (!impl->cmd_copy || impl->cmd_copy->words.empty()) {
        return nullptr;
    }
    if (impl->cmd_copy->addr != addr || impl->cmd_copy->size != size) {
        static std::atomic<u32> mismatches{0};
        if (mismatches.fetch_add(1) < 8) {
            LOG_WARNING(HW_GPU, "async GPU: list copy {:08X}/{} does not match registers {:08X}/{}",
                        impl->cmd_copy->addr, impl->cmd_copy->size, addr, size);
        }
        return nullptr;
    }
    size = static_cast<u32>(impl->cmd_copy->words.size() * sizeof(u32));
    return impl->cmd_copy->words.data();
}

void GPU::SyncShadow() {
    if (async && async->eager) {
        *async->shadow = impl->pica.regs;
    }
}

bool GPU::AsyncPending() const {
    return async && async->pending.load(std::memory_order_acquire) != 0;
}

bool GPU::AsyncActive() const {
    return async != nullptr;
}

MICROPROFILE_DEFINE(GPU_DisplayTransfer, "GPU", "DisplayTransfer", MP_RGB(100, 100, 255));
MICROPROFILE_DEFINE(GPU_CmdlistProcessing, "GPU", "Cmdlist Processing", MP_RGB(100, 255, 100));

GPU::GPU(Core::System& system, Frontend::EmuWindow& emu_window,
         Frontend::EmuWindow* secondary_window)
    : right_eye_disabler{std::make_unique<RightEyeDisabler>(*this)},
      impl{std::make_unique<Impl>(system, emu_window, secondary_window)} {
    RegisterVBlank();

    // Bind the rasterizer to the PICA GPU
    impl->pica.BindRasterizer(impl->rasterizer);

    // persist.gammaos.azahar_nano.async_gpu: 0 off (default), 1 lists only, 2 every GSP write and
    // command queued, 3 = 2 with the interrupts raised at submit. Mode 3 gave Kirby: Planet Robobot
    // 50 to 53.4 fps on the RG DS, but an interrupt before the list ran lets the game reuse the
    // vertex and index buffers the worker still reads (wild fetch addresses: 3D Land crashes at
    // launch), so it stays an experiment until those reads come from a snapshot taken at submit.
    // Per game: the frontend publishes the override value as sys.gammaos.azahar_nano.async_gpu
    // (GammaGameProp), the persist prop is the fallback.
    char v[PROP_VALUE_MAX] = {};
    GammaGameProp::Get("async_gpu", v);
    int mode = 0;
    if (v[0] >= '1' && v[0] <= '3') {
        mode = v[0] - '0';
    }
    if (mode != 0) {
        async = std::make_unique<Async>();
        async->mode = mode;
        async->eager = async->mode >= 3;
        async->shadow = std::make_unique<Pica::PicaCore::Regs>();
        std::memset(async->shadow.get(), 0, sizeof(Pica::PicaCore::Regs));
        async->shadow_internal = std::make_unique<Pica::RegsInternal>();
        std::memset(async->shadow_internal.get(), 0, sizeof(Pica::RegsInternal));
        async->thread = std::thread([a = async.get()] { a->Run(); });
        s_async_gpu.store(this, std::memory_order_release);
        impl->pica.SetChainedCopyLookup(
            [this](PAddr a, u32& sz) { return ChainedCopyFor(a, sz); });
        LOG_INFO(HW_GPU, "async GPU mode {}: {} on a worker thread{}", async->mode,
                 async->mode >= 2 ? "GPU register writes and commands" : "command lists",
                 async->eager ? ", interrupts raised at submit" : "");
    }
}

GPU::~GPU() {
    if (async) {
        GPU* expected = this;
        s_async_gpu.compare_exchange_strong(expected, nullptr);
        {
            std::scoped_lock lock{async->mutex};
            async->stop = true;
        }
        async->work_cv.notify_all();
        async->thread.join();
    }
}

PAddr GPU::VirtualToPhysicalAddress(VAddr addr) {
    if (addr == 0) {
        return 0;
    }

    if (addr >= Memory::VRAM_VADDR && addr <= Memory::VRAM_VADDR_END) {
        return addr - Memory::VRAM_VADDR + Memory::VRAM_PADDR;
    }
    if (addr >= Memory::LINEAR_HEAP_VADDR && addr <= Memory::LINEAR_HEAP_VADDR_END) {
        return addr - Memory::LINEAR_HEAP_VADDR + Memory::FCRAM_PADDR;
    }
    if (addr >= Memory::NEW_LINEAR_HEAP_VADDR && addr <= Memory::NEW_LINEAR_HEAP_VADDR_END) {
        return addr - Memory::NEW_LINEAR_HEAP_VADDR + Memory::FCRAM_PADDR;
    }
    PAddr plg_fb_addr;
    if (addr >= Memory::PLUGIN_3GX_FB_VADDR && addr <= Memory::PLUGIN_3GX_FB_VADDR_END &&
        (plg_fb_addr = impl->system.Memory().Plugin3GXFramebufferAddress())) {
        return addr - Memory::PLUGIN_3GX_FB_VADDR + plg_fb_addr;
    }

    LOG_ERROR(HW_Memory, "Unknown virtual address @ 0x{:08X}", addr);
    return addr;
}

void GPU::SetInterruptHandler(Service::GSP::InterruptHandler handler) {
    if (async) {
        // Interrupts raised on the worker wait for the emulation thread (kernel objects and
        // core timing live there).
        async->deliver = handler;
        Async* a = async.get();
        impl->signal_interrupt = [a, handler](Service::GSP::InterruptId id, u64 delay) {
            if (t_gpu_worker) {
                if (id == Service::GSP::InterruptId::P3D) {
                    t_worker_p3d++;
                }
                if (a->eager) {
                    auto& c = a->credit[static_cast<std::size_t>(id)];
                    int have = c.load(std::memory_order_acquire);
                    while (have > 0) {
                        if (c.compare_exchange_weak(have, have - 1, std::memory_order_acq_rel)) {
                            return; // raised already, when the guest submitted the work
                        }
                    }
                }
                if (a->eager && GxLogOn()) {
                    LOG_INFO(HW_GPU, "gx worker interrupt {} without credit (pending {})",
                             static_cast<int>(id), a->pending.load());
                }
                std::scoped_lock lock{a->interrupt_mutex};
                a->interrupts.emplace_back(id, delay);
                a->interrupt_count.fetch_add(1, std::memory_order_release);
            } else {
                handler(id, delay);
            }
        };
    } else {
        impl->signal_interrupt = handler;
    }
    impl->pica.SetInterruptHandler(impl->signal_interrupt);
}

void GPU::CpuAccess(PAddr addr, u32 size, bool read, bool write) {
    // The dead work tracker is also fed by the worker's fills, transfers and draws.
    AsyncDrain();
    if (read) {
        impl->pica.dead_work.OnCpuRead(addr, size);
    }
    if (write) {
        impl->pica.dead_work.OnCpuWrite(addr, size);
    }
}

void GPU::FlushRegion(PAddr addr, u32 size) {
    AsyncDrain();
    impl->rasterizer->FlushRegion(addr, size);
}

void GPU::InvalidateRegion(PAddr addr, u32 size) {
    AsyncDrain();
    impl->rasterizer->InvalidateRegion(addr, size);
}

void GPU::RegisterVBlank() {
    impl->vblank_event = impl->Timing().RegisterEvent(
        "GPU::VBlankCallback",
        [this](uintptr_t user_data, s64 cycles_late) { VBlankCallback(user_data, cycles_late); });
    impl->Timing().ScheduleEvent(FRAME_TICKS, impl->vblank_event);
    // Async GPU mode 3: the interrupt of queued work, raised a little after the guest's call.
    impl->eager_event = impl->Timing().RegisterEvent(
        "GPU::EagerInterrupt", [this](uintptr_t user_data, s64) {
            impl->signal_interrupt(static_cast<Service::GSP::InterruptId>(user_data), 0);
        });
}

void GPU::ResetForLoad() {
    AsyncDrain();
    if (async) {
        for (auto& c : async->credit) {
            c.store(0, std::memory_order_relaxed);
        }
    }
    SyncShadow();
    // The emulated system around the GPU was rebuilt: its vblank event goes on the new timing
    // (the loaded state then replaces the event queue), every cached surface is dropped
    // without a flush (the loaded memory replaces what it holds), and the state the PICA core
    // keeps between command lists starts over. The shader and pipeline caches stay.
    RegisterVBlank();
    impl->rasterizer->ResetForLoad();
    impl->pica.ResetForLoad();
}

void GPU::ClearAll(bool flush) {
    AsyncDrain();
    impl->rasterizer->ClearAll(flush);
}

void GPU::Execute(const Service::GSP::Command& command) {
    using Service::GSP::CommandId;

    // Async GPU: a command list goes to the worker (its registers are written there, in
    // order); in mode 2 the fills and transfers go the same way, so the queue keeps the GSP
    // order. Every other command touches GPU state here and waits for the worker first.
    if (async && command.id == CommandId::SubmitCmdList) {
        const auto& params = command.submit_gpu_cmdlist;
        const u32 paddr = VirtualToPhysicalAddress(params.address);
        const u32 size = params.size;
        CmdListCopy copy{};
        if (async->eager) {
            auto& sh = async->shadow->internal.pipeline.command_buffer;
            sh.addr[0].Assign(paddr >> 3);
            sh.size[0].Assign(size >> 3);
            u32 irqs = 0;
            copy = CopyCmdList(paddr, size, irqs);
            EagerSignal(Service::GSP::InterruptId::P3D, irqs);
            if (GxLogOn()) {
                if (copy.words.size() <= 16) {
                    std::string w;
                    for (u32 x : copy.words) {
                        w += fmt::format(" {:08X}", x);
                    }
                    LOG_INFO(HW_GPU, "gx list words:{}", w);
                }
                LOG_INFO(HW_GPU, "gx list {:08X} size {:X} irqs {} credits P3D {} PPF {} PSC {}/{} DMA {} queued {}",
                         paddr, size, irqs,
                         async->credit[static_cast<std::size_t>(Service::GSP::InterruptId::P3D)].load(),
                         async->credit[static_cast<std::size_t>(Service::GSP::InterruptId::PPF)].load(),
                         async->credit[static_cast<std::size_t>(Service::GSP::InterruptId::PSC0)].load(),
                         async->credit[static_cast<std::size_t>(Service::GSP::InterruptId::PSC1)].load(),
                         async->credit[static_cast<std::size_t>(Service::GSP::InterruptId::DMA)].load(),
                         async->pending.load());
            }
        }
        auto held = std::make_shared<CmdListCopy>(std::move(copy));
        async->Enqueue([this, paddr, size, held] {
            auto& cmdbuffer = impl->pica.regs.internal.pipeline.command_buffer;
            cmdbuffer.addr[0].Assign(paddr >> 3);
            cmdbuffer.size[0].Assign(size >> 3);
            cmdbuffer.trigger[0] = 1;
            impl->cmd_copy = held.get();
            GammaSnapshot::t_active = &held->data;
            GammaSnapshot::t_owner = held;
            SubmitCmdList(0);
            GammaSnapshot::t_active = nullptr;
            GammaSnapshot::t_owner.reset();
            impl->cmd_copy = nullptr;
        });
        return;
    }
    // Diagnostics: persist.gammaos.azahar_nano.async_gpu_flush=0 makes CacheFlush wait for the
    // worker again, async_gpu_dma=0 runs RequestDma on the emulation thread after a wait.
    // The drain at CacheFlush is load bearing with interrupts raised at submit (Kirby crawls at
    // 1 fps or the worker runs garbage lists without it): async_gpu_flush=1 removes it, for tests.
    static const bool flush_free = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_flush", v);
        return v[0] == '1';
    }();
    // A queued DMA (async_gpu_dma=1) breaks the submit-time snapshot: a list submitted after
    // the DMA has its vertex data copied before the DMA ran (3D Land: wild index buffers). The
    // DMA runs on the emulation thread after a drain unless that switch is set.
    static const bool dma_queued = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_dma", v);
        return v[0] == '1';
    }();
    // Diagnostics (async_gpu_log=1): what the guest flushes and what the queued GPU work
    // writes, to find what the CPU reads back after a CacheFlush.
    if (GxLogOn() && AsyncQueued()) {
        {
            switch (command.id) {
            case CommandId::CacheFlush: {
                const auto& r = command.cache_flush.regions;
                LOG_INFO(HW_GPU, "gx flush {:08X}/{:X} {:08X}/{:X} {:08X}/{:X}",
                         VirtualToPhysicalAddress(r[0].address), r[0].size,
                         VirtualToPhysicalAddress(r[1].address), r[1].size,
                         VirtualToPhysicalAddress(r[2].address), r[2].size);
                break;
            }
            case CommandId::MemoryFill:
                LOG_INFO(HW_GPU, "gx fill {:08X}-{:08X} {:08X}-{:08X}",
                         VirtualToPhysicalAddress(command.memory_fill.start1),
                         VirtualToPhysicalAddress(command.memory_fill.end1),
                         VirtualToPhysicalAddress(command.memory_fill.start2),
                         VirtualToPhysicalAddress(command.memory_fill.end2));
                break;
            case CommandId::DisplayTransfer:
                LOG_INFO(HW_GPU, "gx transfer {:08X} -> {:08X} out {:X}",
                         VirtualToPhysicalAddress(command.display_transfer.in_buffer_address),
                         VirtualToPhysicalAddress(command.display_transfer.out_buffer_address),
                         command.display_transfer.out_buffer_size);
                break;
            case CommandId::TextureCopy:
                LOG_INFO(HW_GPU, "gx texcopy {:08X} -> {:08X} size {:X}",
                         VirtualToPhysicalAddress(command.texture_copy.in_buffer_address),
                         VirtualToPhysicalAddress(command.texture_copy.out_buffer_address),
                         command.texture_copy.size);
                break;
            case CommandId::RequestDma:
                LOG_INFO(HW_GPU, "gx dma {:08X} -> {:08X} size {:X}",
                         VirtualToPhysicalAddress(command.dma_request.source_address),
                         VirtualToPhysicalAddress(command.dma_request.dest_address),
                         command.dma_request.size);
                break;
            case CommandId::SubmitCmdList:
                LOG_INFO(HW_GPU, "gx list {:08X} size {:X}",
                         VirtualToPhysicalAddress(command.submit_gpu_cmdlist.address),
                         command.submit_gpu_cmdlist.size);
                break;
            default:
                break;
            }
        }
    }
    if (AsyncQueued() && command.id == CommandId::CacheFlush && flush_free) {
        return; // nothing to do, and nothing to wait for
    }
    // persist.gammaos.azahar_nano.async_gpu_flush_lag=N: CacheFlush lets N jobs stay in flight
    // instead of waiting for the whole queue (0 = wait for everything, the safe default).
    static const int flush_lag = [] {
        char v[PROP_VALUE_MAX] = {};
        __system_property_get("persist.gammaos.azahar_nano.async_gpu_flush_lag", v);
        return v[0] ? atoi(v) : 0;
    }();
    if (AsyncQueued() && command.id == CommandId::CacheFlush && flush_lag > 0) {
        if (async->pending.load(std::memory_order_acquire) > flush_lag) {
            async->WaitPendingAtMost(flush_lag);
        }
        async->Deliver();
        return;
    }
    if (AsyncQueued() &&
        (command.id == CommandId::MemoryFill || command.id == CommandId::DisplayTransfer ||
         command.id == CommandId::TextureCopy ||
         (command.id == CommandId::RequestDma && async->eager && dma_queued))) {
        if (async->eager) {
            if (command.id == CommandId::MemoryFill) {
                const auto& params = command.memory_fill;
                const bool both = params.start1 != 0 && params.start2 != 0;
                if (params.start1 != 0 && !both) {
                    EagerSignal(Service::GSP::InterruptId::PSC0);
                }
                if (params.start2 != 0) {
                    EagerSignal(both ? Service::GSP::InterruptId::PSC0
                                     : Service::GSP::InterruptId::PSC1);
                }
            } else if (command.id == CommandId::RequestDma) {
                // The copy runs in queue order on the worker (its destination is GPU memory
                // a queued list may read or write); the guest gets the interrupt now.
                EagerSignal(Service::GSP::InterruptId::DMA);
            } else {
                EagerSignal(Service::GSP::InterruptId::PPF);
            }
        }
        // The DMA copies through the requesting process's address space: taken here, the
        // kernel's current process is the emulation thread's to read.
        std::shared_ptr<Kernel::Process> process;
        if (command.id == CommandId::RequestDma) {
            process = impl->system.Kernel().GetCurrentProcess();
        }
        async->Enqueue([this, command, process] { ExecuteCommand(command, process); });
        return;
    }
    AsyncDrain();
    ExecuteCommand(command, nullptr);
}

void GPU::ExecuteCommand(const Service::GSP::Command& command,
                         std::shared_ptr<Kernel::Process> dma_process) {
    using Service::GSP::CommandId;
    auto& regs = impl->pica.regs;

    switch (command.id) {
    case CommandId::RequestDma: {
        impl->system.Memory().RasterizerFlushVirtualRegion(
            command.dma_request.source_address, command.dma_request.size, Memory::FlushMode::Flush);
        impl->system.Memory().RasterizerFlushVirtualRegion(command.dma_request.dest_address,
                                                           command.dma_request.size,
                                                           Memory::FlushMode::Invalidate);

        // TODO(Subv): These memory accesses should not go through the application's memory mapping.
        // They should go through the GSP module's memory mapping.
        const auto process =
            dma_process ? dma_process : impl->system.Kernel().GetCurrentProcess();
        impl->memory.CopyBlock(*process, command.dma_request.dest_address,
                               command.dma_request.source_address, command.dma_request.size);

        auto is_vram = [&](u32 addr) {
            return addr >= Memory::VRAM_VADDR && addr <= Memory::VRAM_VADDR_END;
        };

        u64 delay = DelayGenerator::CalculateDelayNanoseconds(
            DelayGenerator::GetCopyMode(is_vram(command.dma_request.source_address),
                                        is_vram(command.dma_request.dest_address)),
            false, command.dma_request.size);

        impl->signal_interrupt(Service::GSP::InterruptId::DMA, delay);
        break;
    }
    case CommandId::SubmitCmdList: {
        auto& params = command.submit_gpu_cmdlist;
        auto& cmdbuffer = regs.internal.pipeline.command_buffer;

        // Write to the command buffer GPU registers
        cmdbuffer.addr[0].Assign(VirtualToPhysicalAddress(params.address) >> 3);
        cmdbuffer.size[0].Assign(params.size >> 3);
        cmdbuffer.trigger[0] = 1;

        // Trigger processing of the command list
        SubmitCmdList(0);
        break;
    }
    case CommandId::MemoryFill: {
        auto& params = command.memory_fill;
        auto& memfill = regs.memory_fill_config;

        // Write to the memory fill GPU registers.
        // If both buffers are set GSP dispatches PSC0 only.
        const bool has_both_bufs = params.start1 != 0 && params.start2 != 0;
        if (params.start1 != 0) {
            memfill[0].address_start = VirtualToPhysicalAddress(params.start1) >> 3;
            memfill[0].address_end = VirtualToPhysicalAddress(params.end1) >> 3;
            memfill[0].value_32bit = params.value1;
            memfill[0].control = params.control1;
            MemoryFill(0, has_both_bufs ? std::numeric_limits<u32>::max() : 0);
        }
        if (params.start2 != 0) {
            memfill[1].address_start = VirtualToPhysicalAddress(params.start2) >> 3;
            memfill[1].address_end = VirtualToPhysicalAddress(params.end2) >> 3;
            memfill[1].value_32bit = params.value2;
            memfill[1].control = params.control2;
            MemoryFill(1, has_both_bufs ? 0 : 1);
        }
        break;
    }
    case CommandId::DisplayTransfer: {
        auto& params = command.display_transfer;
        auto& display_transfer = regs.display_transfer_config;

        // Write to the transfer engine GPU registers.
        display_transfer.input_address = VirtualToPhysicalAddress(params.in_buffer_address) >> 3;
        display_transfer.output_address = VirtualToPhysicalAddress(params.out_buffer_address) >> 3;
        display_transfer.input_size = params.in_buffer_size;
        display_transfer.output_size = params.out_buffer_size;
        display_transfer.flags = params.flags;
        display_transfer.trigger.Assign(1);

        // Trigger the display transfer.
        MemoryTransfer();
        break;
    }
    case CommandId::TextureCopy: {
        auto& params = command.texture_copy;
        auto& texture_copy = regs.display_transfer_config;

        // Write to the transfer engine GPU registers.
        texture_copy.input_address = VirtualToPhysicalAddress(params.in_buffer_address) >> 3;
        texture_copy.output_address = VirtualToPhysicalAddress(params.out_buffer_address) >> 3;
        texture_copy.texture_copy.size = params.size;
        texture_copy.texture_copy.input_size = params.in_width_gap;
        texture_copy.texture_copy.output_size = params.out_width_gap;
        texture_copy.flags = params.flags;
        texture_copy.trigger.Assign(1);

        // Trigger the texture copy.
        MemoryTransfer();
        break;
    }
    case CommandId::CacheFlush: {
        // Rasterizer flushing handled elsewhere in CPU read/write and other GPU handlers
        // Use command.cache_flush.regions to implement this handler
        break;
    }
    default:
        LOG_ERROR(HW_GPU, "Unknown command {:#08X}", command.id.Value());
    }

    // Notify debugger that a GSP command was processed.
    if (impl->debug_context) {
        impl->debug_context->OnEvent(Pica::DebugContext::Event::GSPCommandProcessed, &command);
    }
}

void GPU::SetBufferSwap(u32 screen_id, const Service::GSP::FrameBufferInfo& info) {
    if (AsyncQueued()) {
        // The framebuffer registers change in queue order with the transfers that fill them;
        // the frame counter stays on the emulation thread.
        if (screen_id == 0) {
            MicroProfileFlip();
            impl->system.perf_stats->EndGameFrame();
        }
        async->Enqueue([this, screen_id, info] { SetBufferSwapNow(screen_id, info, false); });
        return;
    }
    AsyncDrain();
    SetBufferSwapNow(screen_id, info, true);
}

void GPU::SetBufferSwapNow(u32 screen_id, const Service::GSP::FrameBufferInfo& info,
                           bool count_frame) {
    const PAddr phys_address_left = VirtualToPhysicalAddress(info.address_left);
    const PAddr phys_address_right = VirtualToPhysicalAddress(info.address_right);

    // Update framebuffer properties.
    auto& framebuffer = impl->pica.regs.framebuffer_config[screen_id];
    if (info.active_fb == 0) {
        framebuffer.address_left1 = phys_address_left;
        framebuffer.address_right1 = phys_address_right;
    } else {
        framebuffer.address_left2 = phys_address_left;
        framebuffer.address_right2 = phys_address_right;
    }

    framebuffer.stride = info.stride;
    framebuffer.format = info.format;
    framebuffer.active_fb = info.shown_fb;

    // Notify debugger about the buffer swap.
    if (impl->debug_context) {
        impl->debug_context->OnEvent(Pica::DebugContext::Event::BufferSwapped, nullptr);
    }

    if (screen_id == 0) {
        if (count_frame) {
            MicroProfileFlip();
            impl->system.perf_stats->EndGameFrame();
        }
        right_eye_disabler->ReportEndFrame();
    }
}

void GPU::SetColorFill(const Pica::ColorFill& fill) {
    AsyncDrain();
    impl->pica.regs_lcd.color_fill_top = fill;
    impl->pica.regs_lcd.color_fill_bottom = fill;
}

u32 GPU::ReadReg(VAddr addr) {
    AsyncDrain();
    const u32 v = ReadRegNow(addr);
    if (GxLogOn()) {
        LOG_INFO(HW_GPU, "gx readreg {:08X} = {:08X}", addr, v);
    }
    return v;
}

void GPU::WriteRegMasked(VAddr addr, u32 value, u32 mask) {
    if (AsyncQueued()) {
        CmdListCopy copy{};
        if (async->eager && (addr & 0xFFFFF000) != VADDR_LCD) {
            // The shadow only sees GSP writes (a list may have changed the register since), so
            // it decides the interrupt and the copy; the read-modify-write itself runs on the
            // worker, in order, against the real register.
            const u32 index = (addr - VADDR_GPU) / sizeof(u32);
            if (index < Pica::PicaCore::Regs::NUM_REGS) {
                const u32 eff = (async->shadow->reg_array[index] & ~mask) | (value & mask);
                copy = EagerRegWrite(index, eff);
            }
        }
        auto held = std::make_shared<CmdListCopy>(std::move(copy));
        async->Enqueue([this, addr, value, mask, held] {
            impl->cmd_copy = held.get();
            GammaSnapshot::t_active = &held->data;
            GammaSnapshot::t_owner = held;
            WriteRegNow(addr, (ReadRegNow(addr) & ~mask) | (value & mask));
            GammaSnapshot::t_active = nullptr;
            GammaSnapshot::t_owner.reset();
            impl->cmd_copy = nullptr;
        });
        return;
    }
    WriteReg(addr, (ReadReg(addr) & ~mask) | (value & mask));
}

// Mode 3, emulation thread: records a GSP register write in the shadow and, for a trigger
// register, raises the interrupt now and copies the list it starts.
GPU::CmdListCopy GPU::EagerRegWrite(u32 index, u32 data) {
    const bool gx_log_regs = GxLogOn();
    auto& sh = *async->shadow;
    sh.reg_array[index] = data;
    CmdListCopy copy{};
    switch (index) {
    case GPU_REG_INDEX(memory_fill_config[0].trigger):
    case GPU_REG_INDEX(memory_fill_config[1].trigger): {
        const u32 i = index == GPU_REG_INDEX(memory_fill_config[0].trigger) ? 0 : 1;
        if ((data & 1) && sh.memory_fill_config[i].GetStartAddress() != 0) {
            EagerSignal(i == 0 ? Service::GSP::InterruptId::PSC0 : Service::GSP::InterruptId::PSC1);
            static u32 logged = 0;
            if (gx_log_regs && logged++ < 100000) {
                LOG_INFO(HW_GPU, "gx reg fill{} {:08X}-{:08X}", i,
                         sh.memory_fill_config[i].GetStartAddress(),
                         sh.memory_fill_config[i].GetEndAddress());
            }
        }
        break;
    }
    case GPU_REG_INDEX(display_transfer_config.trigger):
        if (data & 1) {
            EagerSignal(Service::GSP::InterruptId::PPF);
            static u32 logged = 0;
            if (gx_log_regs && logged++ < 100000) {
                const auto& dt = sh.display_transfer_config;
                LOG_INFO(HW_GPU, "gx reg transfer {:08X} -> {:08X} {}x{} copy {} size {:X}",
                         dt.GetPhysicalInputAddress(), dt.GetPhysicalOutputAddress(),
                         dt.output_width.Value(), dt.output_height.Value(),
                         dt.is_texture_copy.Value(), dt.texture_copy.size);
            }
        }
        break;
    case GPU_REG_INDEX(internal.pipeline.command_buffer.trigger[0]):
    case GPU_REG_INDEX(internal.pipeline.command_buffer.trigger[1]): {
        const u32 i = index == GPU_REG_INDEX(internal.pipeline.command_buffer.trigger[0]) ? 0 : 1;
        if (data != 0) {
            const auto& cb = sh.internal.pipeline.command_buffer;
            u32 irqs = 0;
            copy = CopyCmdList(cb.GetPhysicalAddress(i), cb.GetSize(i), irqs);
            EagerSignal(Service::GSP::InterruptId::P3D, irqs);
            static u32 logged = 0;
            if (gx_log_regs && logged++ < 100000) {
                LOG_INFO(HW_GPU, "gx reg list {:08X} size {:X} irqs {}", cb.GetPhysicalAddress(i),
                         cb.GetSize(i), irqs);
            }
        }
        break;
    }
    default:
        break;
    }
    return copy;
}

u32 GPU::ReadRegNow(VAddr addr) {
    switch (addr & 0xFFFFF000) {
    case VADDR_LCD: {
        const u32 offset = addr - VADDR_LCD;
        const u32 index = offset / sizeof(u32);
        ASSERT(addr % sizeof(u32) == 0);
        ASSERT(index < Pica::RegsLcd::NumIds());
        return impl->pica.regs_lcd[index];
    }
    case VADDR_GPU:
    case VADDR_GPU + 0x1000: {
        const u32 offset = addr - VADDR_GPU;
        const u32 index = offset / sizeof(u32);
        ASSERT(addr % sizeof(u32) == 0);
        ASSERT(index < Pica::PicaCore::Regs::NUM_REGS);
        return impl->pica.regs.reg_array[index];
    }
    default:
        UNREACHABLE_MSG("Read from unknown GPU address {:#08X}", addr);
    }
}

void GPU::WriteReg(VAddr addr, u32 data) {
    const bool gx_log_regs = GxLogOn();
    if (gx_log_regs) {
        LOG_INFO(HW_GPU, "gx writereg {:08X} = {:08X}", addr, data);
    }
    if (AsyncQueued()) {
        if (async->eager && (addr & 0xFFFFF000) != VADDR_LCD) {
            const u32 index = (addr - VADDR_GPU) / sizeof(u32);
            if (index < Pica::PicaCore::Regs::NUM_REGS) {
                CmdListCopy copy = EagerRegWrite(index, data);
                auto held = std::make_shared<CmdListCopy>(std::move(copy));
                async->Enqueue([this, addr, data, held] {
                    impl->cmd_copy = held.get();
                    GammaSnapshot::t_active = &held->data;
                    GammaSnapshot::t_owner = held;
                    WriteRegNow(addr, data);
                    GammaSnapshot::t_active = nullptr;
                    GammaSnapshot::t_owner.reset();
                    impl->cmd_copy = nullptr;
                });
                return;
            }
        }
        async->Enqueue([this, addr, data] { WriteRegNow(addr, data); });
        return;
    }
    AsyncDrain();
    WriteRegNow(addr, data);
}

void GPU::WriteRegNow(VAddr addr, u32 data) {
    switch (addr & 0xFFFFF000) {
    case VADDR_LCD: {
        const u32 offset = addr - VADDR_LCD;
        const u32 index = offset / sizeof(u32);
        ASSERT(addr % sizeof(u32) == 0);
        ASSERT(index < Pica::RegsLcd::NumIds());
        impl->pica.regs_lcd[index] = data;
        break;
    }
    case VADDR_GPU:
    case VADDR_GPU + 0x1000: {
        const u32 offset = addr - VADDR_GPU;
        const u32 index = offset / sizeof(u32);

        ASSERT(addr % sizeof(u32) == 0);
        ASSERT(index < Pica::PicaCore::Regs::NUM_REGS);
        impl->pica.regs.reg_array[index] = data;

        // Handle registers that trigger GPU actions
        switch (index) {
        case GPU_REG_INDEX(memory_fill_config[0].trigger):
            MemoryFill(0, 0);
            break;
        case GPU_REG_INDEX(memory_fill_config[1].trigger):
            MemoryFill(1, 1);
            break;
        case GPU_REG_INDEX(display_transfer_config.trigger):
            MemoryTransfer();
            break;
        case GPU_REG_INDEX(internal.pipeline.command_buffer.trigger[0]):
            if (async && !t_gpu_worker) {
                async->Enqueue([this] { SubmitCmdList(0); });
            } else {
                SubmitCmdList(0);
            }
            break;
        case GPU_REG_INDEX(internal.pipeline.command_buffer.trigger[1]):
            if (async && !t_gpu_worker) {
                async->Enqueue([this] { SubmitCmdList(1); });
            } else {
                SubmitCmdList(1);
            }
            break;
        default:
            break;
        }
        break;
    }
    default:
        UNREACHABLE_MSG("Write to unknown GPU address {:#08X}", addr);
    }
}

VideoCore::RendererBase& GPU::Renderer() {
    return *impl->renderer;
}

Pica::PicaCore& GPU::PicaCore() {
    return impl->pica;
}

const Pica::PicaCore& GPU::PicaCore() const {
    return impl->pica;
}

GraphicsDebugger& GPU::Debugger() {
    return impl->gpu_debugger;
}

void GPU::ApplyPerProgramSettings(u64 program_ID) {
    AsyncDrain();
    auto hack = Common::Hacks::hack_manager.GetHack(
        Common::Hacks::HackType::ACCURATE_MULTIPLICATION, program_ID);
    bool use_accurate_mul = Settings::values.shaders_accurate_mul.GetValue();
    if (hack) {
        switch (hack->mode) {
        case Common::Hacks::HackAllowMode::DISALLOW:
            use_accurate_mul = false;
            break;
        case Common::Hacks::HackAllowMode::FORCE:
            use_accurate_mul = true;
            break;
        case Common::Hacks::HackAllowMode::ALLOW:
        default:
            break;
        }
    }
    impl->rasterizer->SetAccurateMul(use_accurate_mul);
}

void GPU::SubmitCmdList(u32 index) {
    // Check if a command list was triggered.
    auto& config = impl->pica.regs.internal.pipeline.command_buffer;
    if (!config.trigger[index]) {
        return;
    }

    MICROPROFILE_SCOPE(GPU_CmdlistProcessing);

    // Forward command list processing to the PICA core.
    const PAddr addr = config.GetPhysicalAddress(index);
    u32 size = config.GetSize(index);
    const u32* words = CopyFor(addr, size); // the copy's length when one applies
    const bool allow = right_eye_disabler->ShouldAllowCmdQueueTrigger(addr, size, words);
    const u32 raised_before = t_worker_p3d;
    impl->pica.ProcessCmdList(addr, size, !allow, words);
    config.trigger[index] = 0;
    if (t_gpu_worker && GxLogOn()) {
        LOG_INFO(HW_GPU, "gx worker list {:08X} size {:X} raised {} copy {} blocked {}", addr, size,
                 t_worker_p3d - raised_before, words != nullptr, !allow);
    }
}

void GPU::MemoryFill(u32 index, u32 intr_index) {
    // Check if a memory fill was triggered.
    auto& config = impl->pica.regs.memory_fill_config[index];
    if (!config.trigger) {
        return;
    }

    // Perform memory fill (unless nothing ever reads it, see Pica::DeadWork).
    if (!impl->pica.dead_work.OnFill(config) && !impl->rasterizer->AccelerateFill(config)) {
        impl->sw_blitter->MemoryFill(config);
    }

    // Treat fill as texture transfer from VRAM
    u64 delay = DelayGenerator::CalculateDelayNanoseconds(
        DelayGenerator::GetCopyMode(true, config.IsVRAM()), true,
        config.GetEndAddress() - config.GetStartAddress());

    // It seems that it won't signal interrupt if "address_start" is zero.
    // TODO: hwtest this
    if (config.GetStartAddress() != 0) {
        if (intr_index == 0) {
            impl->signal_interrupt(Service::GSP::InterruptId::PSC0, delay);
        } else if (intr_index == 1) {
            impl->signal_interrupt(Service::GSP::InterruptId::PSC1, delay);
        }
    }

    // Reset "trigger" flag and set the "finish" flag
    // This was confirmed to happen on hardware even if "address_start" is zero.
    config.trigger.Assign(0);
    config.finished.Assign(1);
}

void GPU::MemoryTransfer() {
    // Check if a transfer was triggered.
    auto& config = impl->pica.regs.display_transfer_config;
    if (!config.trigger.Value()) {
        return;
    }

    MICROPROFILE_SCOPE(GPU_DisplayTransfer);

    // Notify debugger about the display transfer.
    if (impl->debug_context) {
        impl->debug_context->OnEvent(Pica::DebugContext::Event::IncomingDisplayTransfer, nullptr);
    }

    u64 delay{};
    // Perform memory transfer (unless nothing ever reads it, see Pica::DeadWork).
    const bool dead = impl->pica.dead_work.OnTransfer(config);
    if (dead) {
        delay = DelayGenerator::CalculateDelayNanoseconds(
            DelayGenerator::GetCopyMode(config.IsInputVRAM(), config.IsOutputVRAM()), true,
            config.is_texture_copy ? static_cast<u32>(config.texture_copy.size)
                                   : config.input_width * config.input_height *
                                         BytesPerPixel(config.input_format));
    } else if (config.is_texture_copy) {
        if (!impl->rasterizer->AccelerateTextureCopy(config)) {
            impl->sw_blitter->TextureCopy(config);
        }
        delay = DelayGenerator::CalculateDelayNanoseconds(
            DelayGenerator::GetCopyMode(config.IsInputVRAM(), config.IsOutputVRAM()), true,
            config.texture_copy.size);
    } else {
        if (right_eye_disabler->ShouldAllowDisplayTransfer(config.GetPhysicalInputAddress(),
                                                           config.input_height)) {
            if (!impl->rasterizer->AccelerateDisplayTransfer(config)) {
                impl->sw_blitter->DisplayTransfer(config);
            }
        }
        delay = DelayGenerator::CalculateDelayNanoseconds(
            DelayGenerator::GetCopyMode(config.IsInputVRAM(), config.IsOutputVRAM()), true,
            config.input_width * config.input_height * BytesPerPixel(config.input_format));
    }

    // Complete transfer.
    config.trigger.Assign(0);
    impl->signal_interrupt(Service::GSP::InterruptId::PPF, delay);
}

void GPU::VBlankCallback(std::uintptr_t user_data, s64 cycles_late) {
    // The frame's command lists must be done before it is presented.
    AsyncDrain();
    // Signal to GSP that GPU interrupt has occurred
    impl->signal_interrupt(Service::GSP::InterruptId::PDC0, 0);
    impl->signal_interrupt(Service::GSP::InterruptId::PDC1, 0);

    impl->pica.dead_work.OnVBlank(impl->pica.regs.framebuffer_config[0],
                                  impl->pica.regs.framebuffer_config[1]);
    // Present renderered frame.
    impl->renderer->SwapBuffers();

    // Reschedule recurrent event
    impl->Timing().ScheduleEvent(FRAME_TICKS - cycles_late, impl->vblank_event);
}

void GPU::RecreateRenderer(Frontend::EmuWindow& emu_window, Frontend::EmuWindow* secondary_window) {
    AsyncDrain();
    // Reset the renderer (this will destroy OpenGL resources)
    impl->renderer.reset();

    // Create a new renderer
    impl->renderer =
        VideoCore::CreateRenderer(emu_window, secondary_window, impl->pica, impl->system);
    impl->rasterizer = impl->renderer->Rasterizer();

    // Rebind the rasterizer to the PICA GPU
    impl->pica.BindRasterizer(impl->rasterizer);

    // Update the sw_blitter with the new rasterizer
    impl->sw_blitter = std::make_unique<SwRenderer::SwBlitter>(impl->memory, impl->rasterizer);

    // Re-apply per-game configuration and reload disk shader cache
    u64 program_id{};
    impl->system.GetAppLoader().ReadProgramId(program_id);
    ApplyPerProgramSettings(program_id);
    if (Settings::values.use_disk_shader_cache) {
        impl->renderer->Rasterizer()->LoadDefaultDiskResources(false, nullptr);
    }

    // Mark ALL GPU registers as dirty so current state gets uploaded to new renderer
    impl->pica.dirty_regs.SetAllDirty();

    // Also mark shader setups as dirty so uniforms get re-uploaded and
    // stale pointers to the old rasterizer's JIT cache are cleared.
    impl->pica.vs_setup.MarkAllUniformsDirty();
    impl->pica.vs_setup.cached_shader = nullptr;
    impl->pica.gs_setup.MarkAllUniformsDirty();
    impl->pica.gs_setup.cached_shader = nullptr;

    // Mark all cached LUT/table state in pica as dirty
    impl->pica.lighting.lut_dirty = impl->pica.lighting.LutAllDirty;
    impl->pica.fog.lut_dirty = true;
    impl->pica.proctex.table_dirty = impl->pica.proctex.TableAllDirty;
}

void GPU::ReleaseRenderer() {
    AsyncDrain();
    // Just reset the renderer to release OpenGL resources
    // Don't null out rasterizer pointer as it will become dangling
    impl->renderer.reset();
    impl->sw_blitter.reset();
    LOG_INFO(HW_GPU, "Renderer released for context destroy");
}

template <class Archive>
void GPU::serialize(Archive& ar, const u32 file_version) {
    AsyncDrain();
    ar & impl->pica;
    if (Archive::is_loading::value) {
        SyncShadow();
    }
}

SERIALIZE_IMPL(GPU)

} // namespace VideoCore
