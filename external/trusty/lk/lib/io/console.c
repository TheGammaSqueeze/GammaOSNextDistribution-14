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
#include <lib/io.h>

#include <err.h>
#include <ctype.h>
#include <debug.h>
#include <assert.h>
#include <list.h>
#include <string.h>
#include <lib/cbuf.h>
#include <arch/ops.h>
#include <platform.h>
#include <platform/debug.h>
#include <kernel/mutex.h>
#include <kernel/thread.h>
#include <lk/init.h>

/* routines for dealing with main console io */

#if WITH_LIB_SM
#define PRINT_LOCK_FLAGS SPIN_LOCK_FLAG_IRQ_FIQ
#else
#define PRINT_LOCK_FLAGS SPIN_LOCK_FLAG_INTERRUPTS
#endif


/*
 * The console is protected by two locks - print_mutex and print_spin_lock.
 * When printing from a context where interrupts are enabled, print_mutex is
 * acquired to guard the entire output, and then print_spin_lock is periodically
 * acquired and released.
 * When printing from a context where interrupts and disabled, print_spin_lock
 * is aquired to guard the entire output.
 * This setup means that, in general, writes to the console will complete
 * atomically. It is possible, however, for a context with disabled interrupts
 * to preempt a context with enabled interrupts. This reduces the chance a print
 * operation inside an interrupt handler will be blocked, at the cost of some
 * log corruption.
 */
static mutex_t print_mutex = MUTEX_INITIAL_VALUE(print_mutex);
static spin_lock_t print_spin_lock = 0;
static spin_lock_saved_state_t print_saved_state = 0;
static unsigned int lock_held_by = SMP_MAX_CPUS;
static struct list_node print_callbacks = LIST_INITIAL_VALUE(print_callbacks);

#if CONSOLE_HAS_INPUT_BUFFER
#ifndef CONSOLE_BUF_LEN
#define CONSOLE_BUF_LEN 256
#endif

/* global input circular buffer */
cbuf_t console_input_cbuf;
static uint8_t console_cbuf_buf[CONSOLE_BUF_LEN];
#endif // CONSOLE_HAS_INPUT_BUFFER

#if EARLY_LOG_BUFFER_SIZE
/*
 * Very early boot logs are captured in a buffer, and then dumped to the
 * first print callback that is registered
 */

static char early_log_buffer[EARLY_LOG_BUFFER_SIZE];
static char* early_log_writeptr = early_log_buffer;
static char* early_log_end = early_log_buffer + sizeof(early_log_buffer);

static void early_log_print(const char *str, size_t len)
{
    size_t remaining = early_log_end - early_log_writeptr;
    if (remaining == 0) {
        return;
    }
    if (len > remaining) {
        /*
         * Don't bother with partial lines, just mark the buffer as full
         * and drop all further logs
         */
        early_log_end = early_log_writeptr;
        return;
    }
    memcpy(early_log_writeptr, str, len);
    early_log_writeptr += len;
}
#endif

/* print lock must be held when invoking out, outs, outc */
static void out_count(const char *str, size_t len)
{
    print_callback_t *cb;
    size_t i;
    int need_lock = !arch_ints_disabled();
    spin_lock_saved_state_t state = 0;

    DEBUG_ASSERT(need_lock || lock_held_by == arch_curr_cpu_num());

    /* copy to the early log buffer if configured */
#if EARLY_LOG_BUFFER_SIZE
    if (need_lock) {
        spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);
    }
    early_log_print(str, len);
    if (need_lock) {
        spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
    }
#endif

    /* print to any registered loggers */
    if (!list_is_empty(&print_callbacks)) {
        if (need_lock) {
            spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);
        }
        list_for_every_entry(&print_callbacks, cb, print_callback_t, entry) {
            if (cb->print) {
                cb->print(cb, str, len);
            }
        }
        if (need_lock) {
            spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
        }
#if CONSOLE_CALLBACK_DISABLES_SERIAL
        return;
#endif
    }

    /* write out the serial port */
    for (i = 0; i < len; i++) {
        if (need_lock) {
            spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);
        }
        platform_dputc(str[i]);
        if (need_lock) {
            spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
        }
    }
}

/* Signal that the write operation is complete. */
static void out_commit(void)
{
    print_callback_t *cb;

    int need_lock = !arch_ints_disabled();
    spin_lock_saved_state_t state = 0;

    DEBUG_ASSERT(need_lock || lock_held_by == arch_curr_cpu_num());

    /* commit to any registered loggers */
    if (!list_is_empty(&print_callbacks)) {
        if (need_lock) {
            spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);
        }
        list_for_every_entry(&print_callbacks, cb, print_callback_t, entry) {
            if (cb->commit) {
                cb->commit(cb);
            }
        }
        if (need_lock) {
            spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
        }
    }
}

static void out_lock(void)
{
    if (arch_ints_disabled()) {
        /*
         * Even though interupts are disabled, FIQs may not be. So save and
         * restore the interrupt state like a normal spin lock. Due to how
         * lock/unlock gets called, we need to stash the state in a global.
         */
        spin_lock_saved_state_t state;
        spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);
        print_saved_state = state;
        lock_held_by = arch_curr_cpu_num();
    } else {
        mutex_acquire(&print_mutex);
    }
}

static void out_unlock(void)
{
    if (arch_ints_disabled()) {
        DEBUG_ASSERT(lock_held_by == arch_curr_cpu_num());
        lock_held_by = SMP_MAX_CPUS;
        spin_unlock_restore(&print_spin_lock, print_saved_state,
                            PRINT_LOCK_FLAGS);
    } else {
        mutex_release(&print_mutex);
    }
}

void register_print_callback(print_callback_t *cb)
{
    spin_lock_saved_state_t state;
    spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);

#if EARLY_LOG_BUFFER_SIZE
    size_t early_log_len = early_log_writeptr - early_log_buffer;
    if (early_log_len) {
        if (cb->print) {
            cb->print(cb, early_log_buffer, early_log_len);
        }
        if (cb->commit) {
            cb->commit(cb);
        }
    }
#endif
    list_add_head(&print_callbacks, &cb->entry);

    spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
}

void unregister_print_callback(print_callback_t *cb)
{
    spin_lock_saved_state_t state;
    spin_lock_save(&print_spin_lock, &state, PRINT_LOCK_FLAGS);

    list_delete(&cb->entry);

    spin_unlock_restore(&print_spin_lock, state, PRINT_LOCK_FLAGS);
}

static ssize_t __debug_stdio_write(io_handle_t *io, const char *s, size_t len)
{
    out_count(s, len);
    return len;
}

static void __debug_stdio_write_commit(io_handle_t *io)
{
    out_commit();
}

static void __debug_stdio_lock(io_handle_t *io)
{
    out_lock();
}

static void __debug_stdio_unlock(io_handle_t *io)
{
    out_unlock();
}

static ssize_t __debug_stdio_read(io_handle_t *io, char *s, size_t len)
{
    if (len == 0)
        return 0;

#if CONSOLE_HAS_INPUT_BUFFER
    ssize_t err = cbuf_read(&console_input_cbuf, s, len, true);
    return err;
#else
    int err = platform_dgetc(s, true);
    if (err < 0)
        return err;

    return 1;
#endif
}

#if CONSOLE_HAS_INPUT_BUFFER
void console_init_hook(uint level)
{
    cbuf_initialize_etc(&console_input_cbuf, sizeof(console_cbuf_buf), console_cbuf_buf);
}

LK_INIT_HOOK(console, console_init_hook, LK_INIT_LEVEL_PLATFORM_EARLY - 1);
#endif

/* global console io handle */
static const io_handle_hooks_t console_io_hooks = {
    .write         = __debug_stdio_write,
    .write_commit  = __debug_stdio_write_commit,
    .read          = __debug_stdio_read,
    .lock          = __debug_stdio_lock,
    .unlock        = __debug_stdio_unlock,
};

io_handle_t console_io = IO_HANDLE_INITIAL_VALUE(&console_io_hooks);
