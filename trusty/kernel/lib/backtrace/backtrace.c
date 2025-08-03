/*
 * Copyright (c) 2020 Google Inc. All rights reserved
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

#include <arch/arch_ops.h>
#include <assert.h>
#include <inttypes.h>
#include <lib/backtrace/backtrace.h>
#include <lib/backtrace/symbolize.h>
#include <lib/trusty/trusty_app.h>
#include <trusty/uuid.h>

/*
 * Traces on release builds look like this for:
 *  Backtrace for thread: trusty_app_12_7ee4dddc-177a-420
 *  (app: crasher)
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *  kSP+0x0nn0: 0x00000..0nnnnn crasher_data_func+0x0/0xnn
 *  uSP+0x0nn0: 0x00000..0nnnnn chan_event_handler_proc...
 *  uSP+0x0nn0: 0x00000..0nnnnn tipc_handle_event+0xnn/0xnnn
 *  uSP+0x0nn0: 0x00000..0nnnnn main+0xnn/0xnn
 *  uSP+0x0nn0: 0x00000..0nnnnn libc_start_main_stage2+0xnn/0xnn
 *  uSP+0x0nn0: 0x00000..000000
 *
 * Debug builds show more information:
 *  Backtrace for thread: trusty_app_30_7ee4dddc-177a-420
 *  (app: crasher)
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0x0000n..n/0x00000..0nnnnn crasher_data_func+0x0/0xnn
 *  0x0000n..n0: 0x0000n..n/0x00000..0nnnnn chan_event_handler_proc...
 *  0x0000n..n0: 0x0000n..n/0x00000..0nnnnn tipc_handle_event+0xnn/0xnnn
 *  0x0000n..n0: 0x0000n..n/0x00000..0nnnnn main+0xnn/0xnn
 *  0x0000n..n0: 0x0000n..n/0x00000..0nnnnn libc_start_main_stage2+0xnn/0xnn
 *  0x0000n..n0: 0x00000..0/0x00000..000000
 *
 * Kernel panics in release builds:
 *  Backtrace for thread: app manager
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *  kSP+0x0nn0: 0xffff0..0nnnnn
 *
 * Kernel panics in debug builds:
 *  Backtrace for thread: app manager
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 *  0xffffn..n0: 0xffffn..n/0xffff0..0nnnnn
 */

#if IS_64BIT
#define PRI0xPTR "016" PRIxPTR
#else
#define PRI0xPTR "08" PRIxPTR
#endif

/* Format for canonical stack offsets */
#define PRI0xSTKOFF "04" PRIxPTR

extern char _start;

static bool is_on_user_stack(struct thread* _thread, uintptr_t addr);
static bool is_on_kernel_stack(struct thread* thread, uintptr_t addr);

static void print_stack_address(struct thread* thread, uintptr_t addr) {
#if TEST_BUILD
    /*
     * For security, never print absolute addresses in release builds.
     */
    printf("0x%" PRI0xPTR " ", addr);
#endif

    if (is_on_user_stack(thread, addr)) {
        struct trusty_thread* trusty_thread = trusty_thread_get(thread);
        uintptr_t stack_low_addr =
                trusty_thread->stack_start - trusty_thread->stack_size;
        printf("uSP+0x%" PRI0xSTKOFF "x", addr - stack_low_addr);
        return;
    }

    if (is_on_kernel_stack(thread, addr)) {
        printf("kSP+0x%" PRI0xSTKOFF "x", addr - (uintptr_t)thread->stack);
        return;
    }

    /*
     * We should never get here for frame->frame_addr,
     * but we print something just in case
     */
    if (addr) {
        printf("<non-null>");
    } else {
        printf("    <null>");
    }
}

static void print_function_info(struct thread* thread,
                                struct stack_frame* frame,
                                uintptr_t load_bias,
                                struct pc_symbol_info* info) {
    uintptr_t pc_offset;
    uintptr_t pc = arch_extract_return_addr(frame->ret_addr);
    __builtin_sub_overflow(pc, load_bias, &pc_offset);

    print_stack_address(thread, frame->frame_addr);
    printf(": ");

#if TEST_BUILD
    /*
     * For security reasons, never print absolute addresses in
     * release builds
     */
    printf("0x%" PRI0xPTR "/", pc);
#endif
    printf("0x%" PRI0xPTR "x", pc_offset);

    if (info) {
        printf(" %s+0x%" PRIxPTR "x/0x%" PRIxPTR "x\n", info->symbol,
               info->offset, info->size);
    } else {
        printf("\n");
    }
}

static void dump_user_function(struct thread* thread,
                               struct trusty_app* app,
                               struct stack_frame* frame) {
    uintptr_t ret_addr = arch_extract_return_addr(frame->ret_addr);
    uintptr_t load_bias = app ? app->load_bias : 0;
    struct pc_symbol_info info;
    int rc = trusty_app_symbolize(app, ret_addr, &info);
    if (rc == NO_ERROR) {
        print_function_info(thread, frame, load_bias, &info);
    } else {
        print_function_info(thread, frame, load_bias, NULL);
    }
}

static void dump_kernel_function(struct thread* thread,
                                 struct stack_frame* frame) {
    uintptr_t load_bias;
    __builtin_sub_overflow((uintptr_t)&_start, KERNEL_BASE + KERNEL_LOAD_OFFSET,
                           &load_bias);

    /* TODO(b/164524596): kernel instruction address symbolization */
    print_function_info(thread, frame, load_bias, NULL);
}

/**
 * dump_function() - dump symbol info about function containing pc
 * @thread: thread containing the instruction
 * @frame: instruction address of the function being dumped and next frame ptr
 */
static void dump_function(thread_t* thread, struct stack_frame* frame) {
    uintptr_t ret_addr = arch_extract_return_addr(frame->ret_addr);

    if (is_user_address(ret_addr)) {
        struct trusty_thread* trusty_thread = trusty_thread_get(thread);
        dump_user_function(thread, trusty_thread ? trusty_thread->app : NULL,
                           frame);
    } else if (is_kernel_address(ret_addr)) {
        dump_kernel_function(thread, frame);
    } else {
        print_function_info(thread, frame, 0, NULL);
    }
}

static bool is_on_user_stack(struct thread* _thread, uintptr_t addr) {
    uintptr_t stack_end;
    uintptr_t stack_bottom;
    struct trusty_thread* thread = trusty_thread_get(_thread);

    if (!thread) {
        return false;
    }

    stack_end = thread->stack_start;
    if (__builtin_sub_overflow(stack_end, thread->stack_size, &stack_bottom)) {
        return false;
    }

    return stack_bottom <= addr && addr < stack_end;
}

static bool is_on_kernel_stack(struct thread* thread, uintptr_t addr) {
    uintptr_t stack_bottom;
    uintptr_t stack_end;

    stack_bottom = (uintptr_t)thread->stack;
    if (__builtin_add_overflow(stack_bottom, thread->stack_size, &stack_end)) {
        return false;
    }

    return stack_bottom <= addr && addr < stack_end;
}

/**
 * is_on_stack() - check if address is on the stack
 * @thread: thread that owns the stack
 * @addr: address being checked
 * @user: true if we need to check against user stack, false if kernel stack
 *
 * Return: true if @addr is on the stack, false otherwise
 */
static bool is_on_stack(struct thread* thread, uintptr_t addr, bool user) {
    if (user) {
        return is_on_user_stack(thread, addr);
    } else {
        return is_on_kernel_stack(thread, addr);
    }
}

static inline bool is_trace_monotonic(uintptr_t prev_fp, uintptr_t next_fp) {
    return stack_direction ? next_fp < prev_fp : next_fp > prev_fp;
}

/**
 * dump_monotonic_backtrace() - dump backtrace while only moving up the stack
 * @thread: thread being backtraced
 * @frame: starting frame, used to iterate through frames in-place
 * @user: true if we're traversing a user stack, false if kernel stack
 *
 * Return: state of @frame
 */
static int dump_monotonic_backtrace(struct thread* thread,
                                    struct stack_frame* frame,
                                    bool user) {
    int frame_state = FRAME_OK;
    while (frame_state == FRAME_OK) {
        frame_state = step_frame(frame, user);
        dump_function(thread, frame);

        if (is_on_stack(thread, frame->fp, !user)) {
            /* Transistion to a different stack */
            return FRAME_OK;
        }
        if (is_zero_frame(frame)) {
            return FRAME_ZERO;
        }
        /* Validate that FP actually points to the stack */
        if (!is_on_stack(thread, frame->fp, user)) {
            return FRAME_CORRUPT;
        }
        /* Stack should only move in one direction */
        if (frame->frame_addr &&
            !is_trace_monotonic(frame->frame_addr, frame->fp)) {
            return FRAME_NON_MONOTONIC;
        }
    }
    return frame_state;
}

static void dump_backtrace_etc(struct thread* thread,
                               struct stack_frame* frame) {
    /*
     * dump_backtrace_*() functions can only be called from kernel space.
     * Expect the first frame to be in kernel address space
     */
    if (!is_kernel_address(frame->fp)) {
        printf("Corrupt stack frame pointer! fp: 0x%" PRIxPTR "\n", frame->fp);
        return;
    }
    int frame_state = dump_monotonic_backtrace(thread, frame, false);
    if (frame_state == FRAME_NON_MONOTONIC) {
        printf("Stack frame moved in wrong direction! Stack overflow likely\n");
        /*
         * Try dumping the stack before the stack overflow. This will be corrupt
         * when it reaches the part of the stack that has been reused by the
         * current exception, but it might have useful information before it
         * gets to that point.
         */
        frame_state = dump_monotonic_backtrace(thread, frame, false);
    }

    if (frame_state == FRAME_OK && is_user_address(frame->fp)) {
        frame_state = dump_monotonic_backtrace(thread, frame, true);
    }

    switch (frame_state) {
    case FRAME_ZERO:
        /* Backtrace is expected to terminate with a zero frame */
        break;
    case FRAME_NON_MONOTONIC:
        printf("Stack frame moved in wrong direction! ");
        dump_function(thread, frame);
        break;
    default:
        printf("Corrupt stack frame! ");
        dump_function(thread, frame);
    }
}

void dump_thread_backtrace(struct thread* thread) {
    if (!thread) {
        printf("Not executing in any thread, backtrace not available!\n");
        return;
    }

    /*
     * TODO(b/149918767): Support backtracing for non-current threads. We need
     * operations on trusty_thread and trusty_app to be thread-safe first.
     */
    assert(thread == get_current_thread());

    struct stack_frame frame = {0};
    get_current_frame(&frame);

    printf("\nBacktrace for thread: %s\n", thread->name);
    struct trusty_app *app = current_trusty_app();
    if (app) {
        char uuid_str[UUID_STR_SIZE];
        uuid_to_str(&app->props.uuid, uuid_str);
        printf("(app: %s uuid: %s)\n", app->props.app_name, uuid_str);
    }

    dump_backtrace_etc(thread, &frame);
}
