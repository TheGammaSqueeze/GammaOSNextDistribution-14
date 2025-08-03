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
#ifndef __KERNEL_THREAD_H
#define __KERNEL_THREAD_H

#include <assert.h>
#include <sys/types.h>
#include <list.h>
#include <compiler.h>
#include <arch/defines.h>
#include <arch/ops.h>
#include <arch/thread.h>
#include <kernel/wait.h>
#include <kernel/spinlock.h>
#include <stdatomic.h>
#include <debug.h>

#if WITH_KERNEL_VM
/* forward declaration */
typedef struct vmm_aspace vmm_aspace_t;
#endif

__BEGIN_CDECLS;

/* debug-enable runtime checks */
#if LK_DEBUGLEVEL > 1
#define THREAD_STATS 1
#define THREAD_STACK_HIGHWATER 1
#endif

enum thread_state {
    THREAD_SUSPENDED = 0,
    THREAD_READY,
    THREAD_RUNNING,
    THREAD_BLOCKED,
    THREAD_SLEEPING,
    THREAD_DEATH,
};

typedef int (*thread_start_routine)(void *arg);

/* thread local storage */
enum thread_tls_list {
#ifdef WITH_LIB_TRUSTY
    TLS_ENTRY_TRUSTY,
#endif
#ifdef WITH_LIB_LKUSER
    TLS_ENTRY_LKUSER,
#endif
#if defined(UBSAN_ENABLED) || defined(CFI_ENABLED)
    TLS_ENTRY_UBSAN,
#endif
#if LK_LIBC_IMPLEMENTATION_IS_MUSL
    TLS_ENTRY_LIBC,
#endif
    MAX_TLS_ENTRY
};

#define THREAD_FLAG_DETACHED                  (1U<<0)
#define THREAD_FLAG_FREE_STACK                (1U<<1)
#define THREAD_FLAG_FREE_STRUCT               (1U<<2)
#define THREAD_FLAG_REAL_TIME                 (1U<<3)
#define THREAD_FLAG_IDLE                      (1U<<4)
#define THREAD_FLAG_DEBUG_STACK_BOUNDS_CHECK  (1U<<5)
#define THREAD_FLAG_EXIT_ON_PANIC             (1U<<6)
#define THREAD_FLAG_FREE_SHADOW_STACK         (1U<<7)
#define THREAD_FLAG_FREE_LIBC_STATE           (1U<<8)

#define THREAD_MAGIC (0x74687264) // 'thrd'

typedef struct thread {
    /* stack stuff, don't move, used by assembly code to validate stack */
    void *stack;
    void *stack_high;
    size_t stack_size;
#if KERNEL_SCS_ENABLED
    void *shadow_stack; /* accessed from assembly code */
    size_t shadow_stack_size;
#endif

    /* helps detect benign corruption */
    int magic;
    /* helps detect malicious corruption */
    uint64_t cookie;
    struct list_node thread_list_node;

    /* active bits */
    struct list_node queue_node;
    int priority;
    enum thread_state state;
    int remaining_quantum;
    unsigned int flags;
#if WITH_SMP
    int curr_cpu;
    int pinned_cpu; /* only run on pinned_cpu if >= 0 */
#endif
#if WITH_KERNEL_VM
    vmm_aspace_t *aspace;
#endif

    /* if blocked, a pointer to the wait queue */
    struct wait_queue *blocking_wait_queue;
    status_t wait_queue_block_ret;

    /* architecture stuff */
    struct arch_thread arch;

    /* entry point */
    thread_start_routine entry;
    void *arg;

    /* return code */
    int retcode;
    struct wait_queue retcode_wait_queue;

    /* thread local storage */
    atomic_uintptr_t tls[MAX_TLS_ENTRY];

    char name[32];
} thread_t;

#if WITH_SMP
#define thread_curr_cpu(t) ((t)->curr_cpu)
#define thread_pinned_cpu(t) ((t)->pinned_cpu)
#define thread_set_curr_cpu(t,c) ((t)->curr_cpu = (c))
#else
#define thread_curr_cpu(t) (0)
#define thread_pinned_cpu(t) (-1)
#define thread_set_curr_cpu(t,c) do {} while(0)
#endif

/* thread priority */
#define NUM_PRIORITIES 32
#define LOWEST_PRIORITY 0
#define HIGHEST_PRIORITY (NUM_PRIORITIES - 1)
#define DPC_PRIORITY (NUM_PRIORITIES - 2)
#define IDLE_PRIORITY LOWEST_PRIORITY
#define LOW_PRIORITY (NUM_PRIORITIES / 4)
#define DEFAULT_PRIORITY (NUM_PRIORITIES / 2)
#define HIGH_PRIORITY ((NUM_PRIORITIES / 4) * 3)

/* stack size */
#ifdef CUSTOM_DEFAULT_STACK_SIZE
#define DEFAULT_STACK_SIZE CUSTOM_DEFAULT_STACK_SIZE
#else
#define DEFAULT_STACK_SIZE ARCH_DEFAULT_STACK_SIZE
#endif

/* shadow stack size */
#ifdef CUSTOM_DEFAULT_SHADOW_STACK_SIZE
#define DEFAULT_SHADOW_STACK_SIZE CUSTOM_DEFAULT_SHADOW_STACK_SIZE
#elif defined(ARCH_DEFAULT_SHADOW_STACK_SIZE)
#define DEFAULT_SHADOW_STACK_SIZE ARCH_DEFAULT_SHADOW_STACK_SIZE
#else
#define DEFAULT_SHADOW_STACK_SIZE PAGE_SIZE
#endif

/* functions */
void thread_init_early(void);
void thread_init(void);
void thread_become_idle(void) __NO_RETURN;
void thread_secondary_cpu_init_early(void);
void thread_secondary_cpu_entry(void) __NO_RETURN;
void thread_set_name(const char *name);

/**
 * thread_set_priority() - set priority of current thread
 * @priority:      Priority for the current thread,
 *                 between %LOWEST_PRIORITY
 *                 and %HIGHEST_PRIORITY
 *
 * Context:        This function shall be invoked without
 *                 holding the thread lock.
 */
void thread_set_priority(int priority);

/**
 * thread_set_pinned_cpu() - Pin thread to a given CPU.
 * @t:             Thread to pin
 * @cpu:           cpu id on which to pin the thread
 *
 * Context:        This function shall be invoked without
 *                 holding the thread lock.
 */
void thread_set_pinned_cpu(thread_t* t, int cpu);

thread_t *thread_create(const char *name, thread_start_routine entry, void *arg, int priority, size_t stack_size);
thread_t *thread_create_etc(thread_t *t, const char *name, thread_start_routine entry, void *arg, int priority, void *stack, size_t stack_size, size_t shadow_stack_size);
status_t thread_resume(thread_t *);
void thread_exit(int retcode) __NO_RETURN;
void thread_exit_from_panic(void) __NO_RETURN;
void thread_sleep_ns(lk_time_ns_t delay_ns);
void thread_sleep_until_ns(lk_time_ns_t target_time_ns);
static inline void thread_sleep(lk_time_t delay_ms) {
    thread_sleep_ns(delay_ms * 1000ULL * 1000);
}
status_t thread_detach(thread_t *t);
status_t thread_join(thread_t *t, int *retcode, lk_time_t timeout);
status_t thread_detach_and_resume(thread_t *t);
status_t thread_set_real_time(thread_t *t);

void dump_thread(thread_t *t);
void arch_dump_thread(thread_t *t);
void dump_all_threads(void);

/* scheduler routines */
void thread_yield(void); /* give up the cpu voluntarily */
void thread_preempt(void); /* get preempted (inserted into head of run queue) */
void thread_block(void); /* block on something and reschedule */
void thread_unblock(thread_t *t, bool resched); /* go back in the run queue */

/* the current thread */
thread_t *get_current_thread(void);
void set_current_thread(thread_t *);

/* scheduler lock */
extern spin_lock_t thread_lock;
extern atomic_uint thread_lock_owner;

static inline uint thread_lock_owner_get(void) {
    return atomic_load_explicit(&thread_lock_owner, memory_order_relaxed);
}

static inline void thread_lock_complete(void) {
    DEBUG_ASSERT(thread_lock_owner_get() == SMP_MAX_CPUS);
    atomic_store_explicit(&thread_lock_owner, arch_curr_cpu_num(),
                          memory_order_relaxed);
}

static inline void thread_unlock_prepare(void) {
    DEBUG_ASSERT(arch_ints_disabled());
    DEBUG_ASSERT(thread_lock_owner_get() == arch_curr_cpu_num());
    atomic_store_explicit(&thread_lock_owner, (uint)SMP_MAX_CPUS,
                          memory_order_relaxed);
}

#define THREAD_LOCK(state) \
    spin_lock_saved_state_t state; \
    spin_lock_irqsave(&thread_lock, state); \
    thread_lock_complete()

#define THREAD_UNLOCK(state) \
    thread_unlock_prepare(); \
    spin_unlock_irqrestore(&thread_lock, state)

static inline void thread_lock_ints_disabled(void) {
    DEBUG_ASSERT(arch_ints_disabled());
    spin_lock(&thread_lock);
    thread_lock_complete();
}

static inline void thread_unlock_ints_disabled(void) {
    thread_unlock_prepare();
    spin_unlock(&thread_lock);
}

static inline bool thread_lock_held(void)
{
    bool ret;
    spin_lock_saved_state_t state;
    arch_interrupt_save(&state, SPIN_LOCK_FLAG_INTERRUPTS);
    ret = thread_lock_owner_get() == arch_curr_cpu_num();
    arch_interrupt_restore(state, SPIN_LOCK_FLAG_INTERRUPTS);
    return ret;
}

/* thread local storage */
static inline __ALWAYS_INLINE uintptr_t thread_tls_get(thread_t *t, uint entry)
{
    return atomic_load(&t->tls[entry]);
}

static inline __ALWAYS_INLINE uintptr_t tls_get(uint entry)
{
    return thread_tls_get(get_current_thread(), entry);
}

static inline __ALWAYS_INLINE uintptr_t __thread_tls_set(thread_t *t,
                                                         uint entry,
                                                         uintptr_t val)
{
    return atomic_exchange(&t->tls[entry], val);
}

#define thread_tls_set(t,e,v) \
    ({ \
        STATIC_ASSERT((e) < MAX_TLS_ENTRY); \
        __thread_tls_set(t, e, v); \
    })

#define tls_set(e,v) thread_tls_set(get_current_thread(), e, v)

static inline void thread_set_flag(thread_t *t, uint flag, bool enable)
{
    THREAD_LOCK(state);
    if (enable) {
        t->flags |= flag;
    } else {
        t->flags &= ~flag;
    }
    THREAD_UNLOCK(state);
}

static inline bool thread_get_flag(thread_t *t, uint flag)
{
    bool enabled;
    THREAD_LOCK(state);
    enabled = t->flags & flag;
    THREAD_UNLOCK(state);
    return enabled;
}

/**
 * thread_set_flag_exit_on_panic - Set flag to ignore panic in specific thread
 * @t:       Thread to set flag on
 * @enable:  If %true, exit thread instead of halting system if panic is called
 *           from @t. If %false, halt system if panic is called from @t
 *           (default behavior).
 *
 * Should only be used for kernel test threads as it is generally not safe to
 * proceed kernel execution after panic has been called.
 */
static inline void thread_set_flag_exit_on_panic(thread_t *t, bool enable)
{
    thread_set_flag(t, THREAD_FLAG_EXIT_ON_PANIC, enable);
}

static inline bool thread_get_flag_exit_on_panic(thread_t *t)
{
    return thread_get_flag(t, THREAD_FLAG_EXIT_ON_PANIC);
}

/* thread level statistics */
#if THREAD_STATS
struct thread_stats {
    lk_time_ns_t idle_time;
    lk_time_ns_t last_idle_timestamp;
    ulong reschedules;
    ulong context_switches;
    ulong preempts;
    ulong yields;
    ulong interrupts; /* platform code increment this */
    ulong timer_ints; /* timer code increment this */
    ulong timers; /* timer code increment this */

#if WITH_SMP
    ulong reschedule_ipis;
#endif
};

extern struct thread_stats thread_stats[SMP_MAX_CPUS];

#define THREAD_STATS_INC(name) do { thread_stats[arch_curr_cpu_num()].name++; } while(0)

#else

#define THREAD_STATS_INC(name) do { } while (0)

#endif

__END_CDECLS;

#endif
