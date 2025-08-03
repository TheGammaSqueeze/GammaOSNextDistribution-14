/*
 * Copyright (c) 2013-2016 Google Inc. All rights reserved
 *
 * Permission is hereby granted, free of charge, to any person obtaining
 * a copy of this software and associated documentation files
 * (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge,
 * publish, distribute, sublicense, and/or sell copies of the Software,
 * and to permit persons to whom the Software is furnished to do so,
 * subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be
 * included in all copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND,
 * EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF
 * MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 * IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY
 * CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT,
 * TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE
 * SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

#include <err.h>
#include <kernel/event.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <kernel/vm.h>
#include <lib/heap.h>
#include <lib/sm.h>
#include <lib/sm/sm_err.h>
#include <lib/sm/smcall.h>
#include <lk/init.h>
#include <platform.h>
#include <stdatomic.h>
#include <string.h>
#include <sys/types.h>
#include <trace.h>
#include <version.h>

#define LOCAL_TRACE 0

struct sm_std_call_state {
    spin_lock_t lock;
    event_t event;
    struct smc32_args args;
    long ret;
    bool done;
    int active_cpu;  /* cpu that expects stdcall result */
    int initial_cpu; /* Debug info: cpu that started stdcall */
    int last_cpu;    /* Debug info: most recent cpu expecting stdcall result */
    int restart_count;
};

extern unsigned long monitor_vector_table;
extern ulong lk_boot_args[4];

static void* boot_args;
static int boot_args_refcnt;
static mutex_t boot_args_lock = MUTEX_INITIAL_VALUE(boot_args_lock);
static atomic_uint_fast32_t sm_api_version;
static atomic_uint_fast32_t sm_api_version_min;
static atomic_uint_fast32_t sm_api_version_max = TRUSTY_API_VERSION_CURRENT;
static spin_lock_t sm_api_version_lock;
static atomic_bool platform_halted;

static event_t nsirqevent[SMP_MAX_CPUS];
static thread_t* nsirqthreads[SMP_MAX_CPUS];
static thread_t* nsidlethreads[SMP_MAX_CPUS];
static thread_t* stdcallthread;
static bool irq_thread_ready[SMP_MAX_CPUS];
struct sm_std_call_state stdcallstate = {
        .event = EVENT_INITIAL_VALUE(stdcallstate.event, 0, 0),
        .active_cpu = -1,
        .initial_cpu = -1,
        .last_cpu = -1,
};

extern smc32_handler_t sm_stdcall_table[];
extern smc32_handler_t sm_nopcall_table[];
extern smc32_handler_t sm_fastcall_table[];

long smc_sm_api_version(struct smc32_args* args) {
    uint32_t api_version = args->params[0];

    spin_lock(&sm_api_version_lock);
    LTRACEF("request api version %d\n", api_version);
    if (api_version > sm_api_version_max) {
        api_version = sm_api_version_max;
    }

    if (api_version < sm_api_version_min) {
        TRACEF("ERROR: Tried to select incompatible api version %d < %d, current version %d\n",
               api_version, sm_api_version_min, sm_api_version);
        api_version = sm_api_version;
    } else {
        sm_api_version = api_version;
    }
    spin_unlock(&sm_api_version_lock);

    LTRACEF("return api version %d\n", api_version);
    return api_version;
}

long smc_get_smp_max_cpus(struct smc32_args* args) {
    return SMP_MAX_CPUS;
}

uint32_t sm_get_api_version(void) {
    return sm_api_version;
}

bool sm_check_and_lock_api_version(uint32_t version_wanted) {
    spin_lock_saved_state_t state;

    DEBUG_ASSERT(version_wanted > 0);

    if (sm_api_version_min >= version_wanted) {
        return true;
    }
    if (sm_api_version_max < version_wanted) {
        return false;
    }

    spin_lock_save(&sm_api_version_lock, &state, SPIN_LOCK_FLAG_IRQ_FIQ);
    if (sm_api_version < version_wanted) {
        sm_api_version_max = MIN(sm_api_version_max, version_wanted - 1);
        TRACEF("max api version set: %d\n", sm_api_version_max);
    } else {
        sm_api_version_min = MAX(sm_api_version_min, version_wanted);
        TRACEF("min api version set: %d\n", sm_api_version_min);
    }
    DEBUG_ASSERT(sm_api_version_min <= sm_api_version_max);
    DEBUG_ASSERT(sm_api_version >= sm_api_version_min);
    DEBUG_ASSERT(sm_api_version <= sm_api_version_max);

    spin_unlock_restore(&sm_api_version_lock, state, SPIN_LOCK_FLAG_IRQ_FIQ);

    return sm_api_version_min >= version_wanted;
}

static int __NO_RETURN sm_stdcall_loop(void* arg) {
    long ret;
    spin_lock_saved_state_t state;

    while (true) {
        LTRACEF("cpu %d, wait for stdcall\n", arch_curr_cpu_num());
        event_wait(&stdcallstate.event);

        /* Dispatch 'standard call' handler */
        LTRACEF("cpu %d, got stdcall: 0x%x, 0x%x, 0x%x, 0x%x\n",
                arch_curr_cpu_num(), stdcallstate.args.smc_nr,
                stdcallstate.args.params[0], stdcallstate.args.params[1],
                stdcallstate.args.params[2]);
        ret = sm_stdcall_table[SMC_ENTITY(stdcallstate.args.smc_nr)](
                &stdcallstate.args);
        LTRACEF("cpu %d, stdcall(0x%x, 0x%x, 0x%x, 0x%x) returned 0x%lx (%ld)\n",
                arch_curr_cpu_num(), stdcallstate.args.smc_nr,
                stdcallstate.args.params[0], stdcallstate.args.params[1],
                stdcallstate.args.params[2], ret, ret);
        spin_lock_save(&stdcallstate.lock, &state, SPIN_LOCK_FLAG_IRQ);
        stdcallstate.ret = ret;
        stdcallstate.done = true;
        event_unsignal(&stdcallstate.event);
        spin_unlock_restore(&stdcallstate.lock, state, SPIN_LOCK_FLAG_IRQ);
    }
}

/* must be called with irqs disabled */
static long sm_queue_stdcall(struct smc32_args* args) {
    long ret;
    uint cpu = arch_curr_cpu_num();

    spin_lock(&stdcallstate.lock);

    if (stdcallstate.event.signaled || stdcallstate.done) {
        if (args->smc_nr == SMC_SC_RESTART_LAST &&
            stdcallstate.active_cpu == -1) {
            stdcallstate.restart_count++;
            LTRACEF_LEVEL(3, "cpu %d, restart std call, restart_count %d\n",
                          cpu, stdcallstate.restart_count);
            goto restart_stdcall;
        }
        dprintf(CRITICAL, "%s: cpu %d, std call busy\n", __func__, cpu);
        ret = SM_ERR_BUSY;
        goto err;
    } else {
        if (args->smc_nr == SMC_SC_RESTART_LAST) {
            dprintf(CRITICAL,
                    "%s: cpu %d, unexpected restart, no std call active\n",
                    __func__, arch_curr_cpu_num());
            ret = SM_ERR_UNEXPECTED_RESTART;
            goto err;
        }
    }

    LTRACEF("cpu %d, queue std call 0x%x\n", cpu, args->smc_nr);
    stdcallstate.initial_cpu = cpu;
    stdcallstate.ret = SM_ERR_INTERNAL_FAILURE;
    stdcallstate.args = *args;
    stdcallstate.restart_count = 0;
    event_signal(&stdcallstate.event, false);

restart_stdcall:
    stdcallstate.active_cpu = cpu;
    ret = 0;

err:
    spin_unlock(&stdcallstate.lock);

    return ret;
}

static void sm_sched_nonsecure_fiq_loop(long ret, struct smc32_args* args) {
    while (true) {
        if (atomic_load(&platform_halted)) {
            ret = SM_ERR_PANIC;
        }
        sm_sched_nonsecure(ret, args);
        if (atomic_load(&platform_halted) && args->smc_nr != SMC_FC_FIQ_ENTER) {
            continue;
        }
        if (SMC_IS_SMC64(args->smc_nr)) {
            ret = SM_ERR_NOT_SUPPORTED;
            continue;
        }
        if (!SMC_IS_FASTCALL(args->smc_nr)) {
            break;
        }
        ret = sm_fastcall_table[SMC_ENTITY(args->smc_nr)](args);
    }
}

/* must be called with irqs disabled */
static void sm_return_and_wait_for_next_stdcall(long ret, int cpu) {
    struct smc32_args args = SMC32_ARGS_INITIAL_VALUE(args);

    do {
#if ARCH_HAS_FIQ
        arch_disable_fiqs();
#endif
        sm_sched_nonsecure_fiq_loop(ret, &args);
#if ARCH_HAS_FIQ
        arch_enable_fiqs();
#endif

        /* Allow concurrent SMC_SC_NOP calls on multiple cpus */
        if (args.smc_nr == SMC_SC_NOP) {
            LTRACEF_LEVEL(3, "cpu %d, got nop\n", cpu);
            ret = sm_nopcall_table[SMC_ENTITY(args.params[0])](&args);
        } else {
            ret = sm_queue_stdcall(&args);
        }
    } while (ret);
    sm_intc_enable_interrupts();
}

static void sm_irq_return_ns(void) {
    long ret;
    int cpu;

    cpu = arch_curr_cpu_num();

    spin_lock(&stdcallstate.lock); /* TODO: remove? */
    LTRACEF_LEVEL(2, "got irq on cpu %d, stdcallcpu %d\n", cpu,
                  stdcallstate.active_cpu);
    if (stdcallstate.active_cpu == cpu) {
        stdcallstate.last_cpu = stdcallstate.active_cpu;
        stdcallstate.active_cpu = -1;
        ret = SM_ERR_INTERRUPTED;
    } else {
        ret = SM_ERR_NOP_INTERRUPTED;
    }
    LTRACEF_LEVEL(2, "got irq on cpu %d, return %ld\n", cpu, ret);
    spin_unlock(&stdcallstate.lock);
    sm_return_and_wait_for_next_stdcall(ret, cpu);
}

static int __NO_RETURN sm_irq_loop(void* arg) {
    int cpu;
    /* cpu that requested this thread, the current cpu could be different */
    int eventcpu = (uintptr_t)arg;

    /*
     * Run this thread with interrupts masked, so we don't reenter the
     * interrupt handler. The interrupt handler for non-secure interrupts
     * returns to this thread with the interrupt still pending.
     */
    arch_disable_ints();
    irq_thread_ready[eventcpu] = true;

    cpu = arch_curr_cpu_num();
    LTRACEF("wait for irqs for cpu %d, on cpu %d\n", eventcpu, cpu);
    while (true) {
        event_wait(&nsirqevent[eventcpu]);
        sm_irq_return_ns();
    }
}

/* must be called with irqs disabled */
static long sm_get_stdcall_ret(void) {
    long ret;
    uint cpu = arch_curr_cpu_num();

    spin_lock(&stdcallstate.lock);

    if (stdcallstate.active_cpu != (int)cpu) {
        dprintf(CRITICAL, "%s: stdcallcpu, a%d != curr-cpu %d, l%d, i%d\n",
                __func__, stdcallstate.active_cpu, cpu, stdcallstate.last_cpu,
                stdcallstate.initial_cpu);
        ret = SM_ERR_INTERNAL_FAILURE;
        goto err;
    }
    stdcallstate.last_cpu = stdcallstate.active_cpu;
    stdcallstate.active_cpu = -1;

    if (stdcallstate.done) {
        stdcallstate.done = false;
        ret = stdcallstate.ret;
        LTRACEF("cpu %d, return stdcall result, %ld, initial cpu %d\n", cpu,
                stdcallstate.ret, stdcallstate.initial_cpu);
    } else {
        if (sm_check_and_lock_api_version(TRUSTY_API_VERSION_SMP))
            ret = SM_ERR_CPU_IDLE; /* ns using smp api */
        else if (stdcallstate.restart_count)
            ret = SM_ERR_BUSY;
        else
            ret = SM_ERR_INTERRUPTED;
        LTRACEF("cpu %d, initial cpu %d, restart_count %d, std call not finished, return %ld\n",
                cpu, stdcallstate.initial_cpu, stdcallstate.restart_count, ret);
    }
err:
    spin_unlock(&stdcallstate.lock);

    return ret;
}

static int sm_wait_for_smcall(void* arg) {
    int cpu;
    long ret = 0;

    LTRACEF("wait for stdcalls, on cpu %d\n", arch_curr_cpu_num());

    while (true) {
        /*
         * Disable interrupts so stdcallstate.active_cpu does not
         * change to or from this cpu after checking it below.
         */
        arch_disable_ints();

        /* Switch to sm-stdcall if sm_queue_stdcall woke it up */
        thread_yield();

        cpu = arch_curr_cpu_num();
        if (cpu == stdcallstate.active_cpu)
            ret = sm_get_stdcall_ret();
        else
            ret = SM_ERR_NOP_DONE;

        sm_return_and_wait_for_next_stdcall(ret, cpu);

        /* Re-enable interrupts (needed for SMC_SC_NOP) */
        arch_enable_ints();
    }
}

#if WITH_LIB_SM_MONITOR
/* per-cpu secure monitor initialization */
static void sm_mon_percpu_init(uint level) {
    /* let normal world enable SMP, lock TLB, access CP10/11 */
    __asm__ volatile(
            "mrc	p15, 0, r1, c1, c1, 2	\n"
            "orr	r1, r1, #0xC00		\n"
            "orr	r1, r1, #0x60000	\n"
            "mcr	p15, 0, r1, c1, c1, 2	@ NSACR	\n"
            :
            :
            : "r1");

    __asm__ volatile("mcr	p15, 0, %0, c12, c0, 1	\n"
                     :
                     : "r"(&monitor_vector_table));
}
LK_INIT_HOOK_FLAGS(libsm_mon_perrcpu,
                   sm_mon_percpu_init,
                   LK_INIT_LEVEL_PLATFORM - 3,
                   LK_INIT_FLAG_ALL_CPUS);
#endif

static void sm_init(uint level) {
    status_t err;
    char name[32];

    mutex_acquire(&boot_args_lock);

    /* Map the boot arguments if supplied by the bootloader */
    if (lk_boot_args[1] && lk_boot_args[2]) {
        ulong offset = lk_boot_args[1] & (PAGE_SIZE - 1);
        paddr_t paddr = round_down(lk_boot_args[1], PAGE_SIZE);
        size_t size = round_up(lk_boot_args[2] + offset, PAGE_SIZE);
        void* vptr;

        err = vmm_alloc_physical(vmm_get_kernel_aspace(), "sm", size, &vptr,
                                 PAGE_SIZE_SHIFT, paddr, 0,
                                 ARCH_MMU_FLAG_NS |
                                         ARCH_MMU_FLAG_PERM_NO_EXECUTE |
                                         ARCH_MMU_FLAG_CACHED);
        if (!err) {
            boot_args = (uint8_t*)vptr + offset;
            boot_args_refcnt++;
        } else {
            boot_args = NULL;
            TRACEF("Error mapping boot parameter block: %d\n", err);
        }
    }

    mutex_release(&boot_args_lock);

    for (int cpu = 0; cpu < SMP_MAX_CPUS; cpu++) {
        event_init(&nsirqevent[cpu], false, EVENT_FLAG_AUTOUNSIGNAL);

        snprintf(name, sizeof(name), "irq-ns-switch-%d", cpu);
        nsirqthreads[cpu] =
                thread_create(name, sm_irq_loop, (void*)(uintptr_t)cpu,
                              HIGHEST_PRIORITY, DEFAULT_STACK_SIZE);
        if (!nsirqthreads[cpu]) {
            panic("failed to create irq NS switcher thread for cpu %d!\n", cpu);
        }
        thread_set_pinned_cpu(nsirqthreads[cpu], cpu);
        thread_set_real_time(nsirqthreads[cpu]);

        snprintf(name, sizeof(name), "idle-ns-switch-%d", cpu);
        nsidlethreads[cpu] =
                thread_create(name, sm_wait_for_smcall, NULL,
                              LOWEST_PRIORITY + 1, DEFAULT_STACK_SIZE);
        if (!nsidlethreads[cpu]) {
            panic("failed to create idle NS switcher thread for cpu %d!\n",
                  cpu);
        }
        thread_set_pinned_cpu(nsidlethreads[cpu], cpu);
        thread_set_real_time(nsidlethreads[cpu]);
    }

    stdcallthread = thread_create("sm-stdcall", sm_stdcall_loop, NULL,
                                  LOWEST_PRIORITY + 2, DEFAULT_STACK_SIZE);
    if (!stdcallthread) {
        panic("failed to create sm-stdcall thread!\n");
    }
    thread_set_real_time(stdcallthread);
    thread_resume(stdcallthread);
}

LK_INIT_HOOK(libsm, sm_init, LK_INIT_LEVEL_PLATFORM - 1);

enum handler_return sm_handle_irq(void) {
    int cpu = arch_curr_cpu_num();
    if (irq_thread_ready[cpu]) {
        event_signal(&nsirqevent[cpu], false);
    } else {
        TRACEF("warning: got ns irq before irq thread is ready\n");
        sm_irq_return_ns();
    }

    return INT_RESCHEDULE;
}

void sm_handle_fiq(void) {
    uint32_t expected_return;
    struct smc32_args args = SMC32_ARGS_INITIAL_VALUE(args);
    if (sm_check_and_lock_api_version(TRUSTY_API_VERSION_RESTART_FIQ)) {
        sm_sched_nonsecure_fiq_loop(SM_ERR_FIQ_INTERRUPTED, &args);
        expected_return = SMC_SC_RESTART_FIQ;
    } else {
        sm_sched_nonsecure_fiq_loop(SM_ERR_INTERRUPTED, &args);
        expected_return = SMC_SC_RESTART_LAST;
    }
    if (args.smc_nr != expected_return) {
        TRACEF("got bad restart smc %x, expected %x\n", args.smc_nr,
               expected_return);
        while (args.smc_nr != expected_return)
            sm_sched_nonsecure_fiq_loop(SM_ERR_INTERLEAVED_SMC, &args);
    }
}

void platform_halt(platform_halt_action suggested_action,
                   platform_halt_reason reason) {
    bool already_halted;
    struct smc32_args args = SMC32_ARGS_INITIAL_VALUE(args);

    arch_disable_ints();
    already_halted = atomic_exchange(&platform_halted, true);
    if (!already_halted) {
        for (int cpu = 0; cpu < SMP_MAX_CPUS; cpu++) {
            if (nsirqthreads[cpu]) {
                event_signal(&nsirqevent[cpu], false);
            }
        }
        dprintf(ALWAYS, "%s\n", lk_version);
        dprintf(ALWAYS, "HALT: (reason = %d)\n", reason);
    }

#if ARCH_HAS_FIQ
    arch_disable_fiqs();
#endif
    while (true)
        sm_sched_nonsecure_fiq_loop(SM_ERR_PANIC, &args);
}

status_t sm_get_boot_args(void** boot_argsp, size_t* args_sizep) {
    status_t err = NO_ERROR;

    if (!boot_argsp || !args_sizep)
        return ERR_INVALID_ARGS;

    mutex_acquire(&boot_args_lock);

    if (!boot_args) {
        err = ERR_NOT_CONFIGURED;
        goto unlock;
    }

    boot_args_refcnt++;
    *boot_argsp = boot_args;
    *args_sizep = lk_boot_args[2];
unlock:
    mutex_release(&boot_args_lock);
    return err;
}

static void resume_nsthreads(void) {
    int i;

    for (i = 0; i < SMP_MAX_CPUS; i++) {
        DEBUG_ASSERT(nsirqthreads[i]);
        DEBUG_ASSERT(nsidlethreads[i]);

        thread_resume(nsirqthreads[i]);
        thread_resume(nsidlethreads[i]);
    }
}

void sm_put_boot_args(void) {
    mutex_acquire(&boot_args_lock);

    if (!boot_args) {
        TRACEF("WARNING: caller does not own "
               "a reference to boot parameters\n");
        goto unlock;
    }

    boot_args_refcnt--;
    if (boot_args_refcnt == 0) {
        vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)boot_args);
        boot_args = NULL;
        resume_nsthreads();
    }
unlock:
    mutex_release(&boot_args_lock);
}

static void sm_release_boot_args(uint level) {
    if (boot_args) {
        sm_put_boot_args();
    } else {
        /* we need to resume the ns-switcher here if
         * the boot loader didn't pass bootargs
         */
        resume_nsthreads();
    }

    if (boot_args)
        TRACEF("WARNING: outstanding reference to boot args"
               "at the end of initialzation!\n");
}

LK_INIT_HOOK(libsm_bootargs, sm_release_boot_args, LK_INIT_LEVEL_LAST);
