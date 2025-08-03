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

#include <assert.h>
#include <kernel/usercopy.h>
#include <lib/backtrace/backtrace.h>
#include <stdbool.h>
#include <string.h>
#include <uapi/err.h>

/* Stack grows down */
const bool stack_direction = false;

/**
 * struct user_stack_frame - user-space stack frame
 * @fp: frame pointer
 * @lr: link register
 *
 * If user-space is explicitly 32-bit (e.g. 64u32 configuration), use 32 bits
 * for register values. Otherwise, assume same bitness as the kernel.
 */
struct user_stack_frame {
#if USER_32BIT
    uint32_t fp;
    uint32_t lr;
#else
    uintptr_t fp;
    uintptr_t lr;
#endif
};

/**
 * struct kernel_stack_frame - kernel-space stack frame
 * @fp: frame pointer
 * @lr: link register
 */
struct kernel_stack_frame {
    uintptr_t fp;
    uintptr_t lr;
};

static int step_user_frame(struct stack_frame* frame) {
    struct user_stack_frame uframe;
    int rc = copy_from_user(&uframe, frame->fp, sizeof(uframe));
    if (rc != NO_ERROR) {
        return FRAME_CORRUPT;
    }

    frame->frame_addr = frame->fp;
    frame->fp = uframe.fp;
    frame->ret_addr = uframe.lr;
    if (is_zero_frame(frame)) {
        return FRAME_ZERO;
    }
    return FRAME_OK;
}

static int step_kernel_frame(struct stack_frame* frame, bool current_frame) {
    struct kernel_stack_frame kframe;
    void* frame_addr = current_frame ? __GET_FRAME() : (void*)(frame->fp);
    memcpy(&kframe, frame_addr, sizeof(kframe));

    frame->frame_addr = (uintptr_t)frame_addr;
    frame->fp = kframe.fp;
    frame->ret_addr = kframe.lr;
    if (is_zero_frame(frame)) {
        return FRAME_ZERO;
    }
    return FRAME_OK;
}

int step_frame(struct stack_frame* frame, bool user) {
    if (user) {
        return step_user_frame(frame);
    } else {
        return step_kernel_frame(frame, false);
    }
}

void get_current_frame(struct stack_frame* frame) {
    step_kernel_frame(frame, true);
}
