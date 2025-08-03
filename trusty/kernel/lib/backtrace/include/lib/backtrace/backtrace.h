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

#pragma once

#ifdef LIB_BACKTRACE_ENABLE
#include <kernel/thread.h>

/**
 * stack_direction - direction of stack growth
 *
 * Stack direction is arch-specific. False if stack grows downwards (i.e.
 * towards 0), true otherwise.
 */
extern const bool stack_direction;

/**
 * struct stack_frame - stack frame of a function call
 * @frame_addr: address of the current frame on the stack
 * @fp: pointer to previous frame on the stack
 * @ret_addr: return address
 *
 * There is more stuff in the frame record. However, for the purpose of
 * backtracing we only need frame pointer and return address.
 */
struct stack_frame {
    uintptr_t frame_addr;
    uintptr_t fp;
    uintptr_t ret_addr;
};

static inline bool is_zero_frame(struct stack_frame* frame) {
    return !frame->fp;
}

enum frame_state {
    FRAME_OK,
    FRAME_ZERO,
    FRAME_CORRUPT,
    FRAME_NON_MONOTONIC,
};

/**
 * get_current_frame() - get current stack frame
 * @frame: current frame will be copied into @frame
 */
void get_current_frame(struct stack_frame* frame);

/**
 * step_frame() - get next stack frame
 * @frame: current frame, next frame will written in-place
 * @user: true if we're traversing a user stack, false if kernel stack
 *
 * Return: frame_state, state of the next frame
 */
int step_frame(struct stack_frame* frame, bool user);

/**
 * dump_thread_backtrace() - dump backtrace of a given thread
 * @thread: thread being backtraced
 */
void dump_thread_backtrace(struct thread* thread);

/**
 * dump_backtrace() - dump backtrace from current location
 */
static inline void dump_backtrace(void) {
    dump_thread_backtrace(get_current_thread());
}
#else
static void dump_backtrace(void) {}
#endif
