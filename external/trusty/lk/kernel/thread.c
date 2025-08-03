/*
 * Copyright (c) 2008-2015 Travis Geiselbrecht
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

/**
 * @file
 * @brief  Kernel threading
 *
 * This file is the core kernel threading interface.
 *
 * @defgroup thread Threads
 * @{
 */
#include <debug.h>
#include <assert.h>
#include <list.h>
#include <malloc.h>
#include <string.h>
#include <printf.h>
#include <err.h>
#include <lib/dpc.h>
#include <kernel/thread.h>
#include <kernel/timer.h>
#include <kernel/debug.h>
#include <kernel/mp.h>
#include <platform.h>
#include <target.h>
#include <lib/heap.h>
#include <lib/rand/rand.h>
#include <inttypes.h>
#if WITH_KERNEL_VM
#include <kernel/vm.h>
#endif
#if LK_LIBC_IMPLEMENTATION_IS_MUSL
#include <trusty/libc_state.h>
#endif

#if THREAD_STATS
struct thread_stats thread_stats[SMP_MAX_CPUS];
#endif

#define STACK_DEBUG_BYTE (0x99)
#define STACK_DEBUG_WORD (0x99999999)

#define DEBUG_THREAD_CONTEXT_SWITCH (0)
#define DEBUG_THREAD_CPU_WAKE (0)
#define DEBUG_THREAD_CPU_PIN (0)

/* global thread list */
static struct list_node thread_list;

/* master thread spinlock */
spin_lock_t thread_lock = SPIN_LOCK_INITIAL_VALUE;

atomic_uint thread_lock_owner = SMP_MAX_CPUS;

/* the run queue */
static struct list_node run_queue[NUM_PRIORITIES];
static uint32_t run_queue_bitmap;

/* make sure the bitmap is large enough to cover our number of priorities */
STATIC_ASSERT(NUM_PRIORITIES <= sizeof(run_queue_bitmap) * 8);

/* Priority of current thread running on cpu, or last signalled */
static int cpu_priority[SMP_MAX_CPUS];

/* the idle thread(s) (statically allocated) */
#if WITH_SMP
static thread_t _idle_threads[SMP_MAX_CPUS];
#define idle_thread(cpu) (&_idle_threads[cpu])
#else
static thread_t _idle_thread;
#define idle_thread(cpu) (&_idle_thread)
#endif

/* list of dead detached thread and wait queue to signal reaper */
static struct list_node dead_threads;
static struct wait_queue reaper_wait_queue;

/* local routines */
static const char *thread_state_to_str(enum thread_state state);
static void thread_resched(void);
static void idle_thread_routine(void) __NO_RETURN;
static enum handler_return thread_timer_callback(struct timer *t,
                                                 lk_time_ns_t now, void *arg);

/* preemption timer */
static timer_t preempt_timer[SMP_MAX_CPUS];

#define US2NS(us) ((us) * 1000ULL)
#define MS2NS(ms) (US2NS(ms) * 1000ULL)

__WEAK void platform_cpu_priority_set(uint32_t cpu, uint32_t priority) {
    (void)cpu;
    (void)priority;
}

/* run queue manipulation */
static void insert_in_run_queue_head(thread_t *t)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_READY);
    DEBUG_ASSERT(!list_in_list(&t->queue_node));
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    list_add_head(&run_queue[t->priority], &t->queue_node);
    run_queue_bitmap |= (1U<<t->priority);
}

static void insert_in_run_queue_tail(thread_t *t)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_READY);
    DEBUG_ASSERT(!list_in_list(&t->queue_node));
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    list_add_tail(&run_queue[t->priority], &t->queue_node);
    run_queue_bitmap |= (1U<<t->priority);
}

static void delete_from_run_queue(thread_t *t) {
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_READY);
    DEBUG_ASSERT(list_in_list(&t->queue_node));
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    list_delete(&t->queue_node);

    if (list_is_empty(&run_queue[t->priority]))
        run_queue_bitmap &= ~(1U<<t->priority);
}

/**
 * thread_get_expected_cookie() - get expected cookie for a given thread
 * @t: address of thread associated with the expected cookie
 *
 * Threads are expected to have the same cookie value modulo the effects of
 * xor'ing the cookie with the address of the enclosing thread struct.
 *
 * Returns: expected cookie for thread t
 */
static inline uint64_t thread_get_expected_cookie(const thread_t *t) {
    /* undo xor with bootstrap thread address then xor with address of t */
    return idle_thread(0)->cookie ^ (uint64_t)idle_thread(0) ^ (uint64_t)t;
}

static void init_thread_struct(thread_t *t, const char *name)
{
    memset(t, 0, sizeof(thread_t));
    t->magic = THREAD_MAGIC;
    /*
     * The bootstrap thread holds expected cookie. If t is the bootstrap
     * thread, the cookie has already been initialized so the following
     * assignment is essentially a no-op.
     */
    t->cookie = thread_get_expected_cookie(t);
    thread_set_pinned_cpu(t, -1);
    strlcpy(t->name, name, sizeof(t->name));
}

/**
 * adjust_shadow_stack_base() - make shadow stack hit guard page if too small
 * @base: pointer to the shadow stack allocation
 * @size: size of the shadow stack. Can be less than memory allocated.
 *
 * Shadow stacks grow up and are followed by guard pages. Adjust the base
 * so we'll hit the guard page if a thread needs more than the number of
 * bytes requested. Call revert_shadow_stack_base to undo the adjustment.
 *
 * Return: pointer into shadow stack allocation iff size % PAGE_SIZE != 0
 */
static void* adjust_shadow_stack_base(uint8_t *base, size_t size) {
   size_t adjustment = round_up(size, PAGE_SIZE) - size;
   return base + adjustment;
}

/**
 * revert_shadow_stack_base() - inverse of adjust_shadow_stack_base
 * @base: pointer returned by adjust_shadow_stack_base
 * @size: size passed to adjust_shadow_stack_base
 *
 * Return: original pointer returned by vmm_alloc
 */
static void* revert_shadow_stack_base(uint8_t *base, size_t size) {
   size_t adjustment = round_up(size, PAGE_SIZE) - size;
   return base - adjustment;
}

/**
 * @brief  Create a new thread
 *
 * This function creates a new thread.  The thread is initially suspended, so you
 * need to call thread_resume() to execute it.
 *
 * @param  t           Allocate thread if NULL; reuse existing thread t otherwise
 * @param  name        Name of thread
 * @param  entry       Entry point of thread
 * @param  arg         Arbitrary argument passed to entry()
 * @param  priority    Execution priority for the thread
 * @param  stack_size  Stack size for the thread
 * @param  shadow_stack_size  Shadow stack size for the thread, if enabled
 *
 * Thread priority is an integer from 0 (lowest) to 31 (highest).  Some standard
 * priorities are defined in <kernel/thread.h>:
 *
 *  HIGHEST_PRIORITY
 *  DPC_PRIORITY
 *  HIGH_PRIORITY
 *  DEFAULT_PRIORITY
 *  LOW_PRIORITY
 *  IDLE_PRIORITY
 *  LOWEST_PRIORITY
 *
 * Stack size is typically set to DEFAULT_STACK_SIZE
 *
 * @return  Pointer to thread object, or NULL on failure.
 */
thread_t *thread_create_etc(thread_t *t, const char *name, thread_start_routine entry, void *arg, int priority, void *stack, size_t stack_size, size_t shadow_stack_size)
{
    int ret;
    unsigned int flags = 0;

    ASSERT(priority > IDLE_PRIORITY);
    ASSERT(priority <= HIGHEST_PRIORITY);

    if (!t) {
        t = malloc(sizeof(thread_t));
        if (!t)
            return NULL;
        flags |= THREAD_FLAG_FREE_STRUCT;
    }

    init_thread_struct(t, name);

    t->entry = entry;
    t->arg = arg;
    t->priority = priority;
    t->state = THREAD_SUSPENDED;
    t->blocking_wait_queue = NULL;
    t->wait_queue_block_ret = NO_ERROR;
    thread_set_curr_cpu(t, -1);

    t->retcode = 0;
    wait_queue_init(&t->retcode_wait_queue);

#if WITH_KERNEL_VM
    t->aspace = NULL;
#endif

    /* create the stack */
    if (!stack) {
        ret = vmm_alloc(vmm_get_kernel_aspace(), "kernel-stack", stack_size,
                        &t->stack, 0, 0, ARCH_MMU_FLAG_PERM_NO_EXECUTE);
        if (ret) {
            if (flags & THREAD_FLAG_FREE_STRUCT)
                free(t);
            return NULL;
        }
        flags |= THREAD_FLAG_FREE_STACK;
    } else {
        t->stack = stack;
    }
#if THREAD_STACK_HIGHWATER
    memset(t->stack, STACK_DEBUG_BYTE, stack_size);
#endif

    t->stack_high = t->stack + stack_size;
    t->stack_size = stack_size;

#if KERNEL_SCS_ENABLED
    /* shadow stacks can only store an integral number of return addresses */
    t->shadow_stack_size = round_up(shadow_stack_size, sizeof(vaddr_t));
    ret = vmm_alloc(vmm_get_kernel_aspace(), "kernel-shadow-stack",
                    t->shadow_stack_size, &t->shadow_stack, PAGE_SIZE_SHIFT,
                    0, ARCH_MMU_FLAG_PERM_NO_EXECUTE);
    if (ret) {
        if (flags & THREAD_FLAG_FREE_STACK)
            free(t->stack);
        if (flags & THREAD_FLAG_FREE_STRUCT)
            free(t);
        return NULL;
    }
    flags |= THREAD_FLAG_FREE_SHADOW_STACK;

    t->shadow_stack = adjust_shadow_stack_base(t->shadow_stack,
                                               t->shadow_stack_size);
#endif

    /* save whether or not we need to free the thread struct and/or stack */
    t->flags = flags;

    /* inheirit thread local storage from the parent */
    thread_t *current_thread = get_current_thread();
    int i;
    for (i=0; i < MAX_TLS_ENTRY; i++)
        t->tls[i] = current_thread->tls[i];

#if LK_LIBC_IMPLEMENTATION_IS_MUSL
    /* set up thread-local libc info */
    ret = libc_state_thread_init(t);
    if (ret != NO_ERROR) {
#if KERNEL_SCS_ENABLED
        if (flags & THREAD_FLAG_FREE_SHADOW_STACK) {
            free(t->shadow_stack);
        }
#endif
        if (flags & THREAD_FLAG_FREE_STACK) {
            free(t->stack);
        }
        if (flags & THREAD_FLAG_FREE_STRUCT) {
            free(t);
        }
        return NULL;
    }
    flags |= THREAD_FLAG_FREE_LIBC_STATE;
#endif

    /* set up the initial stack frame */
    arch_thread_initialize(t);

    /* add it to the global thread list */
    THREAD_LOCK(state);
    list_add_head(&thread_list, &t->thread_list_node);
    THREAD_UNLOCK(state);

    return t;
}

thread_t *thread_create(const char *name, thread_start_routine entry, void *arg, int priority, size_t stack_size)
{
    return thread_create_etc(NULL, name, entry, arg, priority, NULL,
                             stack_size, DEFAULT_SHADOW_STACK_SIZE);
}

/**
 * @brief Flag a thread as real time
 *
 * @param t Thread to flag
 *
 * @return NO_ERROR on success
 */
status_t thread_set_real_time(thread_t *t)
{
    if (!t)
        return ERR_INVALID_ARGS;

    DEBUG_ASSERT(t->magic == THREAD_MAGIC);

    THREAD_LOCK(state);
    if (t == get_current_thread()) {
        /* if we're currently running, cancel the preemption timer. */
        timer_cancel(&preempt_timer[arch_curr_cpu_num()]);
    }
    t->flags |= THREAD_FLAG_REAL_TIME;
    THREAD_UNLOCK(state);

    return NO_ERROR;
}

static bool thread_is_realtime(thread_t *t)
{
    return (t->flags & THREAD_FLAG_REAL_TIME) && t->priority > DEFAULT_PRIORITY;
}

static bool thread_is_idle(thread_t *t)
{
    return !!(t->flags & THREAD_FLAG_IDLE);
}

static bool thread_is_real_time_or_idle(thread_t *t)
{
    return !!(t->flags & (THREAD_FLAG_REAL_TIME | THREAD_FLAG_IDLE));
}

static mp_cpu_mask_t thread_get_mp_reschedule_target(thread_t *current_thread, thread_t *t)
{
#if WITH_SMP
    uint cpu = arch_curr_cpu_num();
    uint target_cpu;

    if (t->pinned_cpu != -1 && current_thread->pinned_cpu == t->pinned_cpu)
        return 0;

    if (t->pinned_cpu == -1 || (uint)t->pinned_cpu == cpu)
        return 0;

    target_cpu = (uint)t->pinned_cpu;

    ASSERT(target_cpu < SMP_MAX_CPUS);

    if (t->priority < cpu_priority[target_cpu]) {
        /*
         * The thread is pinned to a cpu that is already running, or has already
         * been signalled to run, a higher priority thread. No ipi is needed.
         */
#if DEBUG_THREAD_CPU_WAKE
        dprintf(ALWAYS, "%s: cpu %d, don't wake cpu %d, priority %d for priority %d thread (current priority %d)\n",
            __func__, cpu, target_cpu, cpu_priority[target_cpu], t->priority, current_thread->priority);
#endif
        return 0;
    }

#if DEBUG_THREAD_CPU_WAKE
    dprintf(ALWAYS, "%s: cpu %d, wake cpu %d, priority %d for priority %d thread (current priority %d)\n",
        __func__, cpu, target_cpu, cpu_priority[target_cpu], t->priority, current_thread->priority);
#endif
    /*
     * Pretend the target CPU is already running the thread so we don't send it
     * another ipi for a lower priority thread. This is most important if that
     * thread can run on another CPU instead.
     */
    cpu_priority[target_cpu] = t->priority;
    platform_cpu_priority_set(target_cpu, t->priority);

    return 1UL << target_cpu;
#else
    return 0;
#endif
}

static void thread_mp_reschedule(thread_t *current_thread, thread_t *t)
{
    mp_reschedule(thread_get_mp_reschedule_target(current_thread, t), 0);
}

static void thread_init_cookie(thread_t *t) {
    int rc = rand_get_bytes((uint8_t *)&t->cookie, sizeof(t->cookie));
    /* tie the expected cookie to the address of the enclosing thread */
    t->cookie ^= (uint64_t)t;
    ASSERT(!rc && "Failed to initialize thread cookie.");
}

static void thread_check_cookie(const thread_t *t) {
    const uint64_t expected_cookie = thread_get_expected_cookie(t);

    if (unlikely(t->cookie != expected_cookie)) {
        /*
         * It is not safe to call panic with interrupts enabled because
         * backtracing calls printf which may block the current thread and put
         * it on the runqueues with an invalid cookie - causing a double panic.
         */
        arch_disable_ints();
        panic("Corrupt or invalid thread cookie detected for thread: %s.\n"
              "Expected cookie: %" PRIu64 ", actual cookie: %" PRIu64 "\n",
              t->name,
              expected_cookie,
              t->cookie
        );
    }
}

/**
 * @brief  Make a suspended thread executable.
 *
 * This function is typically called to start a thread which has just been
 * created with thread_create()
 *
 * @param t  Thread to resume
 *
 * @return NO_ERROR on success, ERR_NOT_SUSPENDED if thread was not suspended.
 */
status_t thread_resume(thread_t *t)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state != THREAD_DEATH);

    thread_check_cookie(t);

    bool resched = false;
    bool ints_disabled = arch_ints_disabled();
    THREAD_LOCK(state);
    if (t->state == THREAD_SUSPENDED) {
        t->state = THREAD_READY;
        insert_in_run_queue_head(t);
        if (!ints_disabled) /* HACK, don't resced into bootstrap thread before idle thread is set up */
            resched = true;
    }

    thread_mp_reschedule(get_current_thread(), t);

    THREAD_UNLOCK(state);

    if (resched)
        thread_yield();

    return NO_ERROR;
}

status_t thread_detach_and_resume(thread_t *t)
{
    status_t err;
    err = thread_detach(t);
    if (err < 0)
        return err;
    return thread_resume(t);
}

static void thread_free(thread_t *t)
{
#if LK_LIBC_IMPLEMENTATION_IS_MUSL
    int ret;

    /* free thread-local libc info */
    if (t->flags & THREAD_FLAG_FREE_LIBC_STATE) {
        ret = libc_state_thread_free(t);
        DEBUG_ASSERT(ret == NO_ERROR);
    }
#endif

    /* free its stack and the thread structure itself */
    if (t->flags & THREAD_FLAG_FREE_STACK && t->stack)
        vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)t->stack);

#if KERNEL_SCS_ENABLED
    if (t->flags & THREAD_FLAG_FREE_SHADOW_STACK) {
        /* each thread has a shadow stack when the mitigation is enabled */
        DEBUG_ASSERT(t->shadow_stack);
        /* get back the pointer returned by vmm_alloc by undoing adjustment */
        t->shadow_stack = revert_shadow_stack_base(t->shadow_stack,
                                                   t->shadow_stack_size);
        vmm_free_region(vmm_get_kernel_aspace(), (vaddr_t)t->shadow_stack);
    }
#endif

    if (t->flags & THREAD_FLAG_FREE_STRUCT) {
        free(t);
    }
}

status_t thread_join(thread_t *t, int *retcode, lk_time_t timeout)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);

    THREAD_LOCK(state);

    if (t->flags & THREAD_FLAG_DETACHED) {
        /* the thread is detached, go ahead and exit */
        THREAD_UNLOCK(state);
        return ERR_THREAD_DETACHED;
    }

    /* wait for the thread to die */
    if (t->state != THREAD_DEATH) {
        status_t err = wait_queue_block(&t->retcode_wait_queue, timeout);
        if (err < 0) {
            THREAD_UNLOCK(state);
            return err;
        }
    }

    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_DEATH);
    DEBUG_ASSERT(t->blocking_wait_queue == NULL);
    DEBUG_ASSERT(!list_in_list(&t->queue_node));

    /* save the return code */
    if (retcode)
        *retcode = t->retcode;

    /* remove it from the master thread list */
    list_delete(&t->thread_list_node);

    /* clear the structure's magic */
    t->magic = 0;

    THREAD_UNLOCK(state);

    thread_free(t);

    return NO_ERROR;
}

status_t thread_detach(thread_t *t)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);

    THREAD_LOCK(state);

    /* if another thread is blocked inside thread_join() on this thread,
     * wake them up with a specific return code */
    wait_queue_wake_all(&t->retcode_wait_queue, false, ERR_THREAD_DETACHED);

    /* if it's already dead, then just do what join would have and exit */
    if (t->state == THREAD_DEATH) {
        t->flags &= ~THREAD_FLAG_DETACHED; /* makes sure thread_join continues */
        THREAD_UNLOCK(state);
        return thread_join(t, NULL, 0);
    } else {
        t->flags |= THREAD_FLAG_DETACHED;
        THREAD_UNLOCK(state);
        return NO_ERROR;
    }
}

static int reaper_thread_routine(void *arg)
{
    THREAD_LOCK(state);
    while (true) {
        wait_queue_block(&reaper_wait_queue, INFINITE_TIME);

        while(true) {
            thread_t *t = list_remove_head_type(&dead_threads,
                                                thread_t, thread_list_node);
            if (!t) {
                break;
            }
            /* clear the structure's magic */
            t->magic = 0;
            THREAD_UNLOCK(state);
            thread_free(t);
            THREAD_LOCK(state);
        }
    }
}

/**
 * @brief  Terminate the current thread
 *
 * Current thread exits with the specified return code.
 *
 * This function does not return.
 */
void thread_exit(int retcode)
{
    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->magic == THREAD_MAGIC);
    DEBUG_ASSERT(!thread_is_idle(current_thread));
    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING);

    thread_check_cookie(current_thread);

//  dprintf("thread_exit: current %p\n", current_thread);

    THREAD_LOCK(state);

    /* enter the dead state */
    current_thread->state = THREAD_DEATH;
    current_thread->retcode = retcode;

    /* if we're detached, then do our teardown here */
    if (current_thread->flags & THREAD_FLAG_DETACHED) {
        /* remove it from the master thread list */
        list_delete(&current_thread->thread_list_node);

        /* add it to list of threads to free and wake up reaper */
        list_add_tail(&dead_threads, &current_thread->thread_list_node);
        wait_queue_wake_all(&reaper_wait_queue, false, 0);
    } else {
        /* signal if anyone is waiting */
        wait_queue_wake_all(&current_thread->retcode_wait_queue, false, 0);
    }

    /* reschedule */
    thread_resched();

    panic("somehow fell through thread_exit()\n");
}

/**
 * @brief  Terminate the current thread due to a panic
 *
 * Threads should not call this method directly. Instead, test threads can set
 * the exit-on-panic flag to exit via this function rather than halting the
 * system. This function should not be used outside of test builds.
 *
 * This function is similar to thread_exit insofar that it does not return.
 */
void thread_exit_from_panic(void) {
    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->flags & THREAD_FLAG_EXIT_ON_PANIC);

    /* thread lock, if held, was released in _panic */
    THREAD_LOCK(state);

    /* prevent potential infinite recursion in case we panic again */
    current_thread->flags &= ~THREAD_FLAG_EXIT_ON_PANIC;

    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING ||
                 current_thread->state == THREAD_READY);

#if TEST_BUILD
    /* ensure thread cookie is valid so we don't panic in thread_resched */
    current_thread->cookie = thread_get_expected_cookie(current_thread);
#endif
    /*
     * Threads are typically in the THREAD_RUNNING state when they exit.
     * However, if a thread with the THREAD_FLAG_EXIT_ON_PANIC flag set fails
     * a cookie check in thread_resched, this function may run while the
     * thread is still on the run queue with its state set to THREAD_READY.
     *
     * In test builds: Remove it from the run queue so it does not get
     * rescheduled and to ensure that a thread_join operation on the
     * panic'ed thread completes successfully. Set its state to THREAD_READY.
     *
     * Non-test builds shouldn't exercise this code path so we panic.
     */
    if (current_thread->state == THREAD_READY) {
#if TEST_BUILD
        delete_from_run_queue(current_thread);
        /* lie about the thread state to avoid tripping assert in thread_exit */
        current_thread->state = THREAD_RUNNING;
#else
        /* thread lock will be released in _panic */
        panic("%s: tried to exit runnable kernel test thread", __func__);
#endif
    }

    THREAD_UNLOCK(state);

    thread_exit(ERR_FAULT);
}

__WEAK void platform_idle(void)
{
    arch_idle();
}

static void idle_thread_routine(void)
{
    for (;;)
        platform_idle();
}

static thread_t *get_top_thread(int cpu, bool unlink)
{
    thread_t *newthread;
    uint32_t local_run_queue_bitmap = run_queue_bitmap;

    while (local_run_queue_bitmap) {
        /* find the first (remaining) queue with a thread in it */
        uint next_queue = sizeof(run_queue_bitmap) * 8 - 1 - __builtin_clz(local_run_queue_bitmap);

        list_for_every_entry(&run_queue[next_queue], newthread, thread_t, queue_node) {
#if WITH_SMP
            if (newthread->pinned_cpu < 0 || newthread->pinned_cpu == cpu)
#endif
            {
                if (unlink) {
                    delete_from_run_queue(newthread);
                }

                return newthread;
            }
        }

        local_run_queue_bitmap &= ~(1U<<next_queue);
    }

    /* No threads to run */
    if (cpu < 0) {
        /* no CPU has been selected, so we don't have an idle thread */
        return NULL;
    } else {
        /* select the idle thread for this cpu */
        return idle_thread(cpu);
    }
}

/**
 * thread_pinned_cond_mp_reschedule() - handles a new pinned cpu
 * when a thread is running or becomes ready.
 * @current_thread:    Thread currently running on the cpu
 * @thread:            Thread to be scheduled on a potentially
 *                     updated pinned cpu
 *
 * When the pinned cpu of a thread is changed, the thread needs
 * to be rescheduled on that new cpu.
 *
 * To achieve this, thread_resched() shall be invoked on the currently
 * running cpu. Within thread_resched the pinned cpu can be checked against
 * the current cpu and if different, an IPI shall be triggered on the
 * new cpu. thread_pinned_cond_mp_reschedule() is the helper function
 * invoked by thread_resched, checking above condition and conditionally
 * invoking thread_mp_reschedule() to trigger the IPI.
 *
 * Notes:
 * - If the thread is updating its own pinned cpu state,
 * thread_set_pinned_cpu() invokes thread_preempt(), which invokes
 * thread_resched() with current_thread set to the running thread.
 * - If the thread is suspended/blocked/ready when its pinned cpu is updated,
 * as soon as it transitions to ready,thread_resched() is invoked with
 * current_thread set to the ready thread.
 * - If the thread is sleeping when its pinned cpu is updated,
 * thread_sleep_handler() is invoked on the cpu the thread went to sleep on.
 * thread_sleep_handler() needs to invoke thread_pinned_cond_mp_reschedule()
 * to trigger the IPI on the new pinned cpu.
 */
static void thread_pinned_cond_mp_reschedule(thread_t* current_thread,
                                             thread_t* thread,
                                             uint cpu) {
#if WITH_SMP
    if (unlikely((thread->pinned_cpu > -1) && (thread->pinned_cpu != (int)cpu))) {
        DEBUG_ASSERT(thread->curr_cpu == (int)cpu || thread->curr_cpu == -1);
#if DEBUG_THREAD_CPU_PIN
        dprintf(ALWAYS,
                "%s: arch_curr_cpu %d, thread %s: pinned_cpu %d, curr_cpu %d, state [%s]\n",
                __func__, arch_curr_cpu_num(), thread->name, thread->pinned_cpu,
                thread->curr_cpu, thread_state_to_str(thread->state));
#endif
        thread_mp_reschedule(current_thread, thread);
    }
#endif
}

static void thread_cond_mp_reschedule(thread_t *current_thread, const char *caller)
{
#if WITH_SMP
    int i;
    uint best_cpu = ~0U;
    int best_cpu_priority = INT_MAX;
    thread_t *t = get_top_thread(-1, false);

    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    for (i = 0; i < SMP_MAX_CPUS; i++) {
        if (!mp_is_cpu_active(i))
            continue;

        if (cpu_priority[i] < best_cpu_priority) {
            best_cpu = i;
            best_cpu_priority = cpu_priority[i];
        }
    }

    if (!t || (t->priority <= best_cpu_priority))
        return;

#if DEBUG_THREAD_CPU_WAKE
    dprintf(ALWAYS, "%s from %s: cpu %d, wake cpu %d, priority %d for priority %d thread (%s), current %d (%s)\n",
            __func__, caller, arch_curr_cpu_num(), best_cpu, best_cpu_priority,
            t->priority, t->name,
            current_thread->priority, current_thread->name);
#endif
    cpu_priority[best_cpu] = t->priority;
    platform_cpu_priority_set(best_cpu, t->priority);
    mp_reschedule(1UL << best_cpu, 0);
#endif
}

/**
 * @brief  Cause another thread to be executed.
 *
 * Internal reschedule routine. The current thread needs to already be in whatever
 * state and queues it needs to be in. This routine simply picks the next thread and
 * switches to it.
 *
 * This is probably not the function you're looking for. See
 * thread_yield() instead.
 */
static void thread_resched(void) {
    thread_t *oldthread;
    thread_t *newthread;

    thread_t *current_thread = get_current_thread();
    uint cpu = arch_curr_cpu_num();

    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());
    DEBUG_ASSERT(current_thread->state != THREAD_RUNNING);

    THREAD_STATS_INC(reschedules);

    /*
     * These threads are already on the runqueues so these checks should pass
     * unless an existing thread struct was corrupted. If that is the case, the
     * thread cookies do not help much as an adversary could corrupt the
     * register file for that thread instead.
     */
    thread_check_cookie(current_thread);

    newthread = get_top_thread(cpu, true);

    /*
     * The current_thread is switched out from a given cpu,
     * however its pinned cpu may have changed and if so,
     * this current_thread should be scheduled on that new cpu.
     */
    thread_pinned_cond_mp_reschedule(newthread, current_thread, cpu);

    DEBUG_ASSERT(newthread);

    newthread->state = THREAD_RUNNING;

    oldthread = current_thread;

    if (newthread == oldthread) {
        if (cpu_priority[cpu] != oldthread->priority) {
            /*
             * When we try to wake up a CPU to run a specific thread, we record
             * the priority of that thread so we don't request the same CPU
             * again for a lower priority thread. If another CPU picks up that
             * thread before the CPU we sent the wake-up IPI gets to the
             * scheduler it will may to the early return path here. Reset this
             * priority value before returning.
             */
#if DEBUG_THREAD_CPU_WAKE
            dprintf(ALWAYS, "%s: cpu %d, reset cpu priority %d -> %d\n",
                __func__, cpu, cpu_priority[cpu], newthread->priority);
#endif
            cpu_priority[cpu] = newthread->priority;
            platform_cpu_priority_set(cpu, newthread->priority);
        }
        return;
    }

    /* set up quantum for the new thread if it was consumed */
    if (newthread->remaining_quantum <= 0) {
        newthread->remaining_quantum = 5; // XXX make this smarter
    }

    /* mark the cpu ownership of the threads */
    thread_set_curr_cpu(oldthread, -1);
    thread_set_curr_cpu(newthread, cpu);

#if WITH_SMP
    if (thread_is_idle(newthread)) {
        mp_set_cpu_idle(cpu);
    } else {
        mp_set_cpu_busy(cpu);
    }

    if (thread_is_realtime(newthread)) {
        mp_set_cpu_realtime(cpu);
    } else {
        mp_set_cpu_non_realtime(cpu);
    }
#endif

#if THREAD_STATS
    THREAD_STATS_INC(context_switches);

    if (thread_is_idle(oldthread)) {
        lk_time_ns_t now = current_time_ns();
        thread_stats[cpu].idle_time += now - thread_stats[cpu].last_idle_timestamp;
    }
    if (thread_is_idle(newthread)) {
        thread_stats[cpu].last_idle_timestamp = current_time_ns();
    }
#endif

    KEVLOG_THREAD_SWITCH(oldthread, newthread);

    if (thread_is_real_time_or_idle(newthread)) {
        thread_cond_mp_reschedule(newthread, __func__);
        if (!thread_is_real_time_or_idle(oldthread)) {
            /* if we're switching from a non real time to a real time, cancel
             * the preemption timer. */
#if DEBUG_THREAD_CONTEXT_SWITCH
            dprintf(ALWAYS, "arch_context_switch: stop preempt, cpu %d, old %p (%s), new %p (%s)\n",
                    cpu, oldthread, oldthread->name, newthread, newthread->name);
#endif
            timer_cancel(&preempt_timer[cpu]);
        }
    } else if (thread_is_real_time_or_idle(oldthread)) {
        /* if we're switching from a real time (or idle thread) to a regular one,
         * set up a periodic timer to run our preemption tick. */
#if DEBUG_THREAD_CONTEXT_SWITCH
        dprintf(ALWAYS, "arch_context_switch: start preempt, cpu %d, old %p (%s), new %p (%s)\n",
                cpu, oldthread, oldthread->name, newthread, newthread->name);
#endif
        timer_set_periodic_ns(&preempt_timer[cpu], MS2NS(10),
                              thread_timer_callback, NULL);
    }

    /* set some optional target debug leds */
    target_set_debug_led(0, !thread_is_idle(newthread));

    /* do the switch */
    cpu_priority[cpu] = newthread->priority;
    platform_cpu_priority_set(cpu, newthread->priority);
    set_current_thread(newthread);

    thread_check_cookie(newthread);

#if DEBUG_THREAD_CONTEXT_SWITCH
    dprintf(ALWAYS, "arch_context_switch: cpu %d, old %p (%s, pri %d, flags 0x%x), new %p (%s, pri %d, flags 0x%x)\n",
            cpu, oldthread, oldthread->name, oldthread->priority,
            oldthread->flags, newthread, newthread->name,
            newthread->priority, newthread->flags);
#endif

#if WITH_KERNEL_VM
    /* see if we need to swap mmu context */
    if (newthread->aspace != oldthread->aspace) {
        vmm_context_switch(oldthread->aspace, newthread->aspace);
    }
#endif

    /* do the low level context switch */
    arch_context_switch(oldthread, newthread);
}

/**
 * @brief Yield the cpu to another thread
 *
 * This function places the current thread at the end of the run queue
 * and yields the cpu to another waiting thread (if any.)
 *
 * This function will return at some later time. Possibly immediately if
 * no other threads are waiting to execute.
 */
void thread_yield(void)
{
    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->magic == THREAD_MAGIC);
    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING);

    THREAD_LOCK(state);

    THREAD_STATS_INC(yields);

    /* we are yielding the cpu, so stick ourselves into the tail of the run queue and reschedule */
    current_thread->state = THREAD_READY;
    current_thread->remaining_quantum = 0;
    if (likely(!thread_is_idle(current_thread))) { /* idle thread doesn't go in the run queue */
        insert_in_run_queue_tail(current_thread);
    }
    thread_resched();

    THREAD_UNLOCK(state);
}

/**
 * @brief  Briefly yield cpu to another thread
 *
 * This function is similar to thread_yield(), except that it will
 * restart more quickly.
 *
 * This function places the current thread at the head of the run
 * queue and then yields the cpu to another thread.
 *
 * Exception:  If the time slice for this thread has expired, then
 * the thread goes to the end of the run queue.
 *
 * This function will return at some later time. Possibly immediately if
 * no other threads are waiting to execute.
 */
static void thread_preempt_inner(bool lock_held)
{
    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->magic == THREAD_MAGIC);
    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING);

#if THREAD_STATS
    if (!thread_is_idle(current_thread))
        THREAD_STATS_INC(preempts); /* only track when a meaningful preempt happens */
#endif

    KEVLOG_THREAD_PREEMPT(current_thread);

    spin_lock_saved_state_t state;
    if (!lock_held) {
        /* thread lock */
        spin_lock_irqsave(&thread_lock, state);
        thread_lock_complete();
    }

    /* we are being preempted, so we get to go back into the front of the run queue if we have quantum left */
    current_thread->state = THREAD_READY;
    if (likely(!thread_is_idle(current_thread))) { /* idle thread doesn't go in the run queue */
        if (current_thread->remaining_quantum > 0)
            insert_in_run_queue_head(current_thread);
        else
            insert_in_run_queue_tail(current_thread); /* if we're out of quantum, go to the tail of the queue */
    }
    thread_resched();

    if (!lock_held) {
        THREAD_UNLOCK(state);
    }
}

void thread_preempt(void)
{
    /*
     * We refrain from asserting that the thread lock is not held due to
     * performance concerns as this legacy function is called frequently and
     * its usage context is not updated.
     * DEBUG_ASSERT(!thread_lock_held());
     */
    thread_preempt_inner(false);
}

void thread_preempt_lock_held(void)
{
    DEBUG_ASSERT(thread_lock_held());
    thread_preempt_inner(true);
}

/**
 * @brief  Suspend thread until woken.
 *
 * This function schedules another thread to execute.  This function does not
 * return until the thread is made runable again by some other module.
 *
 * You probably don't want to call this function directly; it's meant to be called
 * from other modules, such as mutex, which will presumably set the thread's
 * state to blocked and add it to some queue or another.
 */
void thread_block(void)
{
    __UNUSED thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->magic == THREAD_MAGIC);
    DEBUG_ASSERT(current_thread->state == THREAD_BLOCKED);
    DEBUG_ASSERT(thread_lock_held());
    DEBUG_ASSERT(!thread_is_idle(current_thread));

    /* we are blocking on something. the blocking code should have already stuck us on a queue */
    thread_resched();
}

void thread_unblock(thread_t *t, bool resched)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_BLOCKED);
    DEBUG_ASSERT(thread_lock_held());
    DEBUG_ASSERT(!thread_is_idle(t));

    thread_check_cookie(t);

    t->state = THREAD_READY;
    insert_in_run_queue_head(t);
    thread_mp_reschedule(get_current_thread(), t);
    if (resched)
        thread_resched();
}

static enum handler_return thread_timer_callback(struct timer *t, lk_time_ns_t now,
                                              void *arg)
{
    thread_t *current_thread = get_current_thread();

    if (thread_is_idle(current_thread))
        return INT_NO_RESCHEDULE;

    THREAD_LOCK(state);
    thread_cond_mp_reschedule(current_thread, __func__);
    THREAD_UNLOCK(state);

    if (thread_is_real_time_or_idle(current_thread))
        return INT_NO_RESCHEDULE;

    current_thread->remaining_quantum--;
    if (current_thread->remaining_quantum <= 0) {
        return INT_RESCHEDULE;
    } else {
        return INT_NO_RESCHEDULE;
    }
}

/* timer callback to wake up a sleeping thread */
static enum handler_return thread_sleep_handler(timer_t *timer,
                                                lk_time_ns_t now, void *arg)
{
    thread_t *t = (thread_t *)arg;

    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(t->state == THREAD_SLEEPING);

    thread_check_cookie(t);

    THREAD_LOCK(state);

    t->state = THREAD_READY;
    insert_in_run_queue_head(t);
    /*
     * The awakened thread's thread_sleep_handler() is invoked
     * on the cpu the thread went to sleep on.
     * However the thread's pinned cpu may have changed
     * while the thread was asleep and if so,
     * this thread should be scheduled on the new pinned cpu.
     */
    thread_pinned_cond_mp_reschedule(
        get_current_thread(), t, arch_curr_cpu_num());
    THREAD_UNLOCK(state);

    return INT_RESCHEDULE;
}

/**
 * @brief  Put thread to sleep; delay specified in ms
 *
 * This function puts the current thread to sleep until the specified
 * delay in ns has expired.
 *
 * Note that this function could sleep for longer than the specified delay if
 * other threads are running.  When the timer expires, this thread will
 * be placed at the head of the run queue.
 */
void thread_sleep_ns(lk_time_ns_t delay_ns)
{
    timer_t timer;

    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(current_thread->magic == THREAD_MAGIC);
    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING);
    DEBUG_ASSERT(!thread_is_idle(current_thread));

    timer_initialize(&timer);

    THREAD_LOCK(state);
    timer_set_oneshot_ns(&timer, delay_ns, thread_sleep_handler,
                         (void *)current_thread);
    current_thread->state = THREAD_SLEEPING;
    thread_resched();
    THREAD_UNLOCK(state);

    /*
     * Make sure callback is not still running before timer goes out of scope as
     * it would corrupt the stack.
     */
    timer_cancel_sync(&timer);
}

/**
 * thread_sleep_until_ns - Put thread to sleep until specified time
 * @target_time_ns:  Time to sleep until.
 *
 * Sleep until current_time_ns() returns a value greater or equal than
 * @target_time_ns. If current_time_ns() is already greater or equal than
 * @target_time_ns return immediately.
 */
void thread_sleep_until_ns(lk_time_ns_t target_time_ns)
{
    lk_time_ns_t now_ns = current_time_ns();
    if (now_ns < target_time_ns) {
        /* TODO: Support absolute time in timer api and improve accuracy. */
        thread_sleep_ns(target_time_ns - now_ns);
    }
}

/**
 * @brief  Initialize threading system on primary CPU
 *
 * This function is called once, from kmain().
 * Disable PAC protection in case the arch enables PAC.  Suggest inlining to
 * avoid creating a non-PAC function if possible (inlining requires LTO).
 */
__ARCH_NO_PAC __ALWAYS_INLINE void thread_init_early(void)
{
    int i;

    DEBUG_ASSERT(arch_curr_cpu_num() == 0);

    /* initialize the run queues */
    for (i=0; i < NUM_PRIORITIES; i++)
        list_initialize(&run_queue[i]);

    /* initialize the thread list */
    list_initialize(&thread_list);

    list_initialize(&dead_threads);
    wait_queue_init(&reaper_wait_queue);

    /* create a thread to cover the current running state */
    thread_t *t = idle_thread(0);
    init_thread_struct(t, "bootstrap");

    arch_init_thread_initialize(t, 0);

    /* half construct this thread, since we're already running */
    t->priority = HIGHEST_PRIORITY;
    t->state = THREAD_RUNNING;
    t->flags = THREAD_FLAG_DETACHED;
    thread_set_curr_cpu(t, 0);
    thread_set_pinned_cpu(t, 0);
    wait_queue_init(&t->retcode_wait_queue);
    list_add_head(&thread_list, &t->thread_list_node);
    cpu_priority[0] = t->priority;
    platform_cpu_priority_set(0, t->priority);
    set_current_thread(t);
    thread_init_cookie(t);
}

static void thread_reaper_init(void)
{
    thread_t *t = thread_create("reaper", reaper_thread_routine, NULL,
                                HIGH_PRIORITY, DEFAULT_STACK_SIZE);
    if (!t) {
        dprintf(CRITICAL, "Failed to start reaper thread\n");
        return;
    }
    thread_detach_and_resume(t);
}

/**
 * @brief Complete thread initialization
 *
 * This function is called once at boot time
 */
void thread_init(void)
{
    for (uint i = 0; i < SMP_MAX_CPUS; i++) {
        timer_initialize(&preempt_timer[i]);
    }
    thread_reaper_init();
}

/**
 * @brief Change name of current thread
 */
void thread_set_name(const char *name)
{
    thread_t *current_thread = get_current_thread();
    strlcpy(current_thread->name, name, sizeof(current_thread->name));
}

/**
 * @brief Change priority of current thread
 *
 * See thread_create() for a discussion of priority values.
 */
void thread_set_priority(int priority)
{
    DEBUG_ASSERT(!thread_lock_held());
    thread_t *current_thread = get_current_thread();

    THREAD_LOCK(state);

    if (priority <= IDLE_PRIORITY)
        priority = IDLE_PRIORITY + 1;
    if (priority > HIGHEST_PRIORITY)
        priority = HIGHEST_PRIORITY;
    current_thread->priority = priority;

    current_thread->state = THREAD_READY;
    insert_in_run_queue_head(current_thread);
    thread_resched();

    THREAD_UNLOCK(state);
}

/**
 * thread_set_pinned_cpu() - Pin thread to a given CPU.
 * @t:      Thread to pin
 * @cpu:    cpu id on which to pin the thread
 */
void thread_set_pinned_cpu(thread_t* t, int cpu) {
#if WITH_SMP
    DEBUG_ASSERT(t);
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(cpu >= -1 && cpu < SMP_MAX_CPUS);
    DEBUG_ASSERT(!thread_lock_held());

    THREAD_LOCK(state);
    if (t->pinned_cpu == cpu) {
        goto done;
    }

    t->pinned_cpu = cpu;
    if ((t->pinned_cpu > -1) && (t->pinned_cpu == t->curr_cpu)) {
        /*
         * No need to reschedule the thread on a new cpu.
         * This exit path is also used during the initial
         * boot phase when processors are being brought up:
         * see thread_init_early()
         * and thread_secondary_cpu_init_early()
         */
        goto done;
    }

    switch(t->state){
        case THREAD_SUSPENDED: {
            /*
             * Early init phase, thread not scheduled yet,
             * the cpu pinning will apply at a later stage
             * when thread is scheduled
             */
            goto done;
        }
        case THREAD_READY: {
            DEBUG_ASSERT(!thread_is_idle(t));
            DEBUG_ASSERT(t->curr_cpu == -1);
            thread_t *current_thread = get_current_thread();
            DEBUG_ASSERT(t != current_thread);
            /*
             * Thread `t` is ready and shall be rescheduled
             * according to a new cpu target (either the
             * pinned cpu if pinned cpu > -1, or any available
             * cpu if pinned cpu == -1).
             */
            int curr_cpu = arch_curr_cpu_num();
            if (t->pinned_cpu == -1 || t->pinned_cpu == curr_cpu) {
                if (current_thread->priority < t->priority) {
                    /*
                     * if the thread is to be rescheduled on the current
                     * cpu due to being higher priority, thread_preempt
                     * shall be invoked.
                     */
                    thread_preempt_lock_held();
                    goto done;
                }
                if (t->pinned_cpu == -1
                    && thread_is_realtime(current_thread)) {
                    /*
                     * if the thread is unpinned, it may be rescheduled on
                     * another cpu. There are two cases:
                     * if the current thread is a standard thread, its time
                     * quantum tick handler thread_timer_callback(),
                     * will select the next best cpu for the top unpinned thread.
                     * However if the current thread is a real-time thread,
                     * its quantum slicing is disabled (by design),
                     * thus the newly unpinned thread shall be rescheduled
                     * manually on its best cpu
                     */
                    thread_cond_mp_reschedule(t, __func__);
                }
            } else {
                /*
                 * if the thread is pinned on another cpu than current
                 * an ipi may be sent to the best cpu. This is achieved
                 * by invoking thread_mp_reschedule().
                 */
                thread_mp_reschedule(current_thread, t);
            }
            goto done;
        }
        case THREAD_RUNNING: {
            DEBUG_ASSERT(!thread_is_idle(t));
            int thread_curr_cpu = t->curr_cpu;
            DEBUG_ASSERT(thread_curr_cpu > -1);
            thread_t *current_thread = get_current_thread();
            if (t->pinned_cpu == -1){
                /*
                 * pinned cpu is reset,
                 * current cpu still is a valid option,
                 * nothing to do
                 */
                goto done;
            }
            /*
             * Thread `t` is running and its pinned cpu is
             * different from its current cpu, two cases to handle:
             * - Running on current cpu
             * - Running on another cpu than current
             */
            if (t == current_thread) {
                /*
                 * Thread `t` is the current thread running
                 * on current cpu:
                 * (thread_set_pinned_cpu called from within
                 * current thread), the thread needs to be
                 * rescheduled to the new pinned cpu,
                 * this is handled within the thread_preempt
                 * call
                 */
                DEBUG_ASSERT(thread_curr_cpu == (int)arch_curr_cpu_num());
                thread_preempt_lock_held();
                goto done;
            }
            /*
             * Thread `t` is running on another cpu than
             * the current one:
             * thread_preempt needs to be invoked on this
             * other cpu. We do this by invoking mp_reschedule
             * on the thread's current cpu, which in turns
             * invoke thread_resched to schedule out our thread
             * and finally send an IPI to the newly pinned cpu
             */
            DEBUG_ASSERT(thread_curr_cpu != (int)arch_curr_cpu_num());
            mp_reschedule(1UL << (uint)thread_curr_cpu, 0);
            goto done;
        }
        case THREAD_BLOCKED:
        case THREAD_SLEEPING: {
            /*
             * the new pinned cpu shall be taken into account
             * when the thread state change (to THREAD_READY)
             * happen - see thread_pinned_cond_mp_reschedule()
             */
            DEBUG_ASSERT(!thread_is_idle(t));
            DEBUG_ASSERT(t != get_current_thread());
            goto done;
        }
        case THREAD_DEATH: {
            /*
             * thread_set_pinned_cpu cannot be
             * invoked on such a dead/exited thread
             */
            DEBUG_ASSERT(false);
            goto done;
        }
        /*
         * Compiler option -Wswitch will catch missing
         * case statement if a new thread state
         * value is added and not handled.
         */
    }
done:
    THREAD_UNLOCK(state);
#if DEBUG_THREAD_CPU_PIN
    dprintf(ALWAYS,
            "%s(%d): thread %s, pinned_cpu %d, curr_cpu %d, state [%s]\n",
            __func__, cpu, t->name, t->pinned_cpu, t->curr_cpu,
            thread_state_to_str(t->state));
#endif
#endif
}

/**
 * @brief  Become an idle thread
 *
 * This function marks the current thread as the idle thread -- the one which
 * executes when there is nothing else to do.  This function does not return.
 * This function is called once at boot time.
 */
void thread_become_idle(void)
{
    DEBUG_ASSERT(arch_ints_disabled());

    thread_t *t = get_current_thread();

#if WITH_SMP
    char name[16];
    snprintf(name, sizeof(name), "idle %d", arch_curr_cpu_num());
    thread_set_name(name);
#else
    thread_set_name("idle");
#endif

    /* mark ourself as idle */
    t->priority = IDLE_PRIORITY;
    t->flags |= THREAD_FLAG_IDLE;
    thread_set_pinned_cpu(t, arch_curr_cpu_num());

    mp_set_curr_cpu_active(true);
    mp_set_cpu_idle(arch_curr_cpu_num());

    /* enable interrupts and start the scheduler */
    arch_enable_ints();
    thread_yield();

    idle_thread_routine();
}

/**
 * @brief  Initialize threading system on secondary CPUs
 *
 * This function is called once per CPU, from lk_secondary_cpu_entry().
 * Disable PAC protection in case the arch enables PAC.  Suggest inlining to
 * avoid creating a non-PAC function if possible (inlining requires LTO).
 */
__ARCH_NO_PAC __ALWAYS_INLINE void thread_secondary_cpu_init_early(void)
{
    DEBUG_ASSERT(arch_ints_disabled());

    /* construct an idle thread to cover our cpu */
    uint cpu = arch_curr_cpu_num();
    thread_t *t = idle_thread(cpu);

    char name[16];
    snprintf(name, sizeof(name), "idle %u", cpu);
    init_thread_struct(t, name);
    thread_set_pinned_cpu(t, cpu);

    /* half construct this thread, since we're already running */
    t->priority = HIGHEST_PRIORITY;
    t->state = THREAD_RUNNING;
    t->flags = THREAD_FLAG_DETACHED | THREAD_FLAG_IDLE;
    thread_set_curr_cpu(t, cpu);
    thread_set_pinned_cpu(t, cpu);
    wait_queue_init(&t->retcode_wait_queue);

    arch_init_thread_initialize(t, cpu);

    THREAD_LOCK(state);

    list_add_head(&thread_list, &t->thread_list_node);
    cpu_priority[cpu] = t->priority;
    platform_cpu_priority_set(cpu, t->priority);
    set_current_thread(t);

    THREAD_UNLOCK(state);
}

void thread_secondary_cpu_entry(void)
{
    uint cpu = arch_curr_cpu_num();
    thread_t *t = get_current_thread();
    t->priority = IDLE_PRIORITY;

    mp_set_curr_cpu_active(true);
    mp_set_cpu_idle(cpu);

    /* enable interrupts and start the scheduler on this cpu */
    arch_enable_ints();
    thread_yield();

    idle_thread_routine();
}

static const char *thread_state_to_str(enum thread_state state)
{
    switch (state) {
        case THREAD_SUSPENDED:
            return "susp";
        case THREAD_READY:
            return "rdy";
        case THREAD_RUNNING:
            return "run";
        case THREAD_BLOCKED:
            return "blok";
        case THREAD_SLEEPING:
            return "slep";
        case THREAD_DEATH:
            return "deth";
        default:
            return "unkn";
    }
}

static size_t thread_stack_used(thread_t *t) {
#ifdef THREAD_STACK_HIGHWATER
    uint8_t *stack_base;
    size_t stack_size;
    size_t i;

    stack_base = t->stack;
    stack_size = t->stack_size;

    for (i = 0; i < stack_size; i++) {
        if (stack_base[i] != STACK_DEBUG_BYTE)
            break;
    }
    return stack_size - i;
#else
    return 0;
#endif
}
/**
 * @brief  Dump debugging info about the specified thread.
 */
void dump_thread(thread_t *t)
{
    dprintf(INFO, "dump_thread: t %p (%s)\n", t, t->name);
#if WITH_SMP
    dprintf(INFO, "\tstate %s, curr_cpu %d, pinned_cpu %d, priority %d, remaining quantum %d\n",
            thread_state_to_str(t->state), t->curr_cpu, t->pinned_cpu, t->priority, t->remaining_quantum);
#else
    dprintf(INFO, "\tstate %s, priority %d, remaining quantum %d\n",
            thread_state_to_str(t->state), t->priority, t->remaining_quantum);
#endif
#ifdef THREAD_STACK_HIGHWATER
    dprintf(INFO, "\tstack %p, stack_size %zd, stack_used %zd\n",
            t->stack, t->stack_size, thread_stack_used(t));
#else
    dprintf(INFO, "\tstack %p, stack_size %zd\n", t->stack, t->stack_size);
#endif
#if KERNEL_SCS_ENABLED
    dprintf(INFO, "\tshadow stack %p, shadow stack_size %zd\n",
            t->shadow_stack, t->shadow_stack_size);

#endif
    dprintf(INFO, "\tentry %p, arg %p, flags 0x%x\n", t->entry, t->arg, t->flags);
    dprintf(INFO, "\twait queue %p, wait queue ret %d\n", t->blocking_wait_queue, t->wait_queue_block_ret);
#if WITH_KERNEL_VM
    dprintf(INFO, "\taspace %p\n", t->aspace);
#endif
#if (MAX_TLS_ENTRY > 0)
    dprintf(INFO, "\ttls:");
    int i;
    for (i=0; i < MAX_TLS_ENTRY; i++) {
        dprintf(INFO, " 0x%lx", t->tls[i]);
    }
    dprintf(INFO, "\n");
#endif
    arch_dump_thread(t);
}

/**
 * @brief  Dump debugging info about all threads
 */
void dump_all_threads(void)
{
    thread_t *t;

    THREAD_LOCK(state);
    list_for_every_entry(&thread_list, t, thread_t, thread_list_node) {
        if (t->magic != THREAD_MAGIC) {
            dprintf(INFO, "bad magic on thread struct %p, aborting.\n", t);
            hexdump(t, sizeof(thread_t));
            break;
        }
        dump_thread(t);
    }
    THREAD_UNLOCK(state);
}

/** @} */


/**
 * @defgroup  wait  Wait Queue
 * @{
 */
void wait_queue_init(wait_queue_t *wait)
{
    *wait = (wait_queue_t)WAIT_QUEUE_INITIAL_VALUE(*wait);
}

static enum handler_return wait_queue_timeout_handler(timer_t *timer,
                                                      lk_time_ns_t now,
                                                      void *arg)
{
    thread_t *thread = (thread_t *)arg;

    DEBUG_ASSERT(thread->magic == THREAD_MAGIC);

    thread_lock_ints_disabled();

    enum handler_return ret = INT_NO_RESCHEDULE;
    if (thread_unblock_from_wait_queue(thread, ERR_TIMED_OUT) >= NO_ERROR) {
        ret = INT_RESCHEDULE;
    }

    thread_unlock_ints_disabled();

    return ret;
}

/**
 * @brief  Block until a wait queue is notified.
 *
 * This function puts the current thread at the end of a wait
 * queue and then blocks until some other thread wakes the queue
 * up again.
 *
 * @param  wait     The wait queue to enter
 * @param  timeout  The maximum time, in ms, to wait
 *
 * If the timeout is zero, this function returns immediately with
 * ERR_TIMED_OUT.  If the timeout is INFINITE_TIME, this function
 * waits indefinitely.  Otherwise, this function returns with
 * ERR_TIMED_OUT at the end of the timeout period.
 *
 * @return ERR_TIMED_OUT on timeout, else returns the return
 * value specified when the queue was woken by wait_queue_wake_one().
 */
status_t wait_queue_block(wait_queue_t *wait, lk_time_t timeout)
{
    timer_t timer;

    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(wait->magic == WAIT_QUEUE_MAGIC);
    DEBUG_ASSERT(current_thread->state == THREAD_RUNNING);
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    if (timeout == 0)
        return ERR_TIMED_OUT;

    list_add_tail(&wait->list, &current_thread->queue_node);
    wait->count++;
    current_thread->state = THREAD_BLOCKED;
    current_thread->blocking_wait_queue = wait;
    current_thread->wait_queue_block_ret = NO_ERROR;

    /* if the timeout is nonzero or noninfinite, set a callback to yank us out of the queue */
    if (timeout != INFINITE_TIME) {
        timer_initialize(&timer);
        timer_set_oneshot_ns(&timer, MS2NS(timeout),
                             wait_queue_timeout_handler,
                             (void *)current_thread);
    }

    thread_resched();

    /* we don't really know if the timer fired or not, so it's better safe to try to cancel it */
    if (timeout != INFINITE_TIME) {
        /*
         * The timer could be running on another CPU. Drop the thread-lock then
         * cancel and wait for the stack allocated timer.
         */
        thread_unlock_ints_disabled();
        arch_enable_ints();
        timer_cancel_sync(&timer);
        arch_disable_ints();
        thread_lock_ints_disabled();
    }

    return current_thread->wait_queue_block_ret;
}

/**
 * @brief  Wake up one thread sleeping on a wait queue
 *
 * This function removes one thread (if any) from the head of the wait queue and
 * makes it executable.  The new thread will be placed at the head of the
 * run queue.
 *
 * @param wait  The wait queue to wake
 * @param reschedule  If true, the newly-woken thread will run immediately.
 * @param wait_queue_error  The return value which the new thread will receive
 * from wait_queue_block().
 *
 * @return  The number of threads woken (zero or one)
 */
int wait_queue_wake_one(wait_queue_t *wait, bool reschedule, status_t wait_queue_error)
{
    thread_t *t;
    int ret = 0;

    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(wait->magic == WAIT_QUEUE_MAGIC);
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    t = list_remove_head_type(&wait->list, thread_t, queue_node);
    if (t) {
        wait->count--;
        DEBUG_ASSERT(t->magic == THREAD_MAGIC);
        DEBUG_ASSERT(t->state == THREAD_BLOCKED);
        thread_check_cookie(t);
        t->state = THREAD_READY;
        t->wait_queue_block_ret = wait_queue_error;
        t->blocking_wait_queue = NULL;

        /* if we're instructed to reschedule, stick the current thread on the head
         * of the run queue first, so that the newly awakened thread gets a chance to run
         * before the current one, but the current one doesn't get unnecessarilly punished.
         */
        if (reschedule) {
            current_thread->state = THREAD_READY;
            insert_in_run_queue_head(current_thread);
        }
        insert_in_run_queue_head(t);
        thread_mp_reschedule(current_thread, t);
        if (reschedule) {
            thread_resched();
        }
        ret = 1;

    }

    return ret;
}


/**
 * @brief  Wake all threads sleeping on a wait queue
 *
 * This function removes all threads (if any) from the wait queue and
 * makes them executable.  The new threads will be placed at the head of the
 * run queue.
 *
 * @param wait  The wait queue to wake
 * @param reschedule  If true, the newly-woken threads will run immediately.
 * @param wait_queue_error  The return value which the new thread will receive
 * from wait_queue_block().
 *
 * @return  The number of threads woken (zero or one)
 */
int wait_queue_wake_all(wait_queue_t *wait, bool reschedule, status_t wait_queue_error)
{
    thread_t *t;
    int ret = 0;
    mp_cpu_mask_t mp_reschedule_target = 0;

    thread_t *current_thread = get_current_thread();

    DEBUG_ASSERT(wait->magic == WAIT_QUEUE_MAGIC);
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    if (reschedule && wait->count > 0) {
        /* if we're instructed to reschedule, stick the current thread on the head
         * of the run queue first, so that the newly awakened threads get a chance to run
         * before the current one, but the current one doesn't get unnecessarilly punished.
         */
        current_thread->state = THREAD_READY;
        insert_in_run_queue_head(current_thread);
    }

    /* pop all the threads off the wait queue into the run queue */
    while ((t = list_remove_head_type(&wait->list, thread_t, queue_node))) {
        wait->count--;
        DEBUG_ASSERT(t->magic == THREAD_MAGIC);
        DEBUG_ASSERT(t->state == THREAD_BLOCKED);
        thread_check_cookie(t);
        t->state = THREAD_READY;
        t->wait_queue_block_ret = wait_queue_error;
        t->blocking_wait_queue = NULL;

        insert_in_run_queue_head(t);
        mp_reschedule_target |= thread_get_mp_reschedule_target(current_thread, t);
        ret++;
    }

    DEBUG_ASSERT(wait->count == 0);

    if (ret > 0) {
        mp_reschedule(mp_reschedule_target, 0);
        if (reschedule) {
            DEBUG_ASSERT(current_thread->state == THREAD_READY);
            thread_resched();
        } else {
            /*
             * Verify that thread_resched is not skipped when
             * thread state changes to THREAD_READY
             */
            DEBUG_ASSERT(current_thread->state != THREAD_READY);
        }
    } else {
        /*
         * Verify that thread_resched is not skipped when
         * thread state changes to THREAD_READY
         */
        DEBUG_ASSERT(current_thread->state != THREAD_READY);
    }

    return ret;
}

/**
 * @brief  Free all resources allocated in wait_queue_init()
 *
 * If any threads were waiting on this queue, they are all woken.
 */
void wait_queue_destroy(wait_queue_t *wait, bool reschedule)
{
    DEBUG_ASSERT(wait->magic == WAIT_QUEUE_MAGIC);
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    wait_queue_wake_all(wait, reschedule, ERR_OBJECT_DESTROYED);
    wait->magic = 0;
}

/**
 * @brief  Wake a specific thread in a wait queue
 *
 * This function extracts a specific thread from a wait queue, wakes it, and
 * puts it at the head of the run queue.
 *
 * @param t  The thread to wake
 * @param wait_queue_error  The return value which the new thread will receive
 *   from wait_queue_block().
 *
 * @return ERR_NOT_BLOCKED if thread was not in any wait queue.
 */
status_t thread_unblock_from_wait_queue(thread_t *t, status_t wait_queue_error)
{
    DEBUG_ASSERT(t->magic == THREAD_MAGIC);
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_held());

    thread_check_cookie(t);

    if (t->state != THREAD_BLOCKED)
        return ERR_NOT_BLOCKED;

    DEBUG_ASSERT(t->blocking_wait_queue != NULL);
    DEBUG_ASSERT(t->blocking_wait_queue->magic == WAIT_QUEUE_MAGIC);
    DEBUG_ASSERT(list_in_list(&t->queue_node));

    list_delete(&t->queue_node);
    t->blocking_wait_queue->count--;
    t->blocking_wait_queue = NULL;
    t->state = THREAD_READY;
    t->wait_queue_block_ret = wait_queue_error;
    insert_in_run_queue_head(t);
    thread_mp_reschedule(get_current_thread(), t);

    return NO_ERROR;
}

#if defined(WITH_DEBUGGER_INFO)
// This is, by necessity, arch-specific, and arm-m specific right now,
// but lives here due to thread_list being static.
//
// It contains sufficient information for a remote debugger to walk
// the thread list without needing the symbols and debug sections in
// the elf binary for lk or the ability to parse them.
const struct __debugger_info__ {
    u32 version; // flags:16 major:8 minor:8
    void *thread_list_ptr;
    void *current_thread_ptr;
    u8 off_list_node;
    u8 off_state;
    u8 off_saved_sp;
    u8 off_was_preempted;
    u8 off_name;
    u8 off_waitq;
} _debugger_info = {
    .version = 0x0100,
    .thread_list_ptr = &thread_list,
    .current_thread_ptr = &_current_thread,
    .off_list_node = __builtin_offsetof(thread_t, thread_list_node),
    .off_state = __builtin_offsetof(thread_t, state),
    .off_saved_sp = __builtin_offsetof(thread_t, arch.sp),
    .off_was_preempted = __builtin_offsetof(thread_t, arch.was_preempted),
    .off_name = __builtin_offsetof(thread_t, name),
    .off_waitq = __builtin_offsetof(thread_t, blocking_wait_queue),
};
#endif
