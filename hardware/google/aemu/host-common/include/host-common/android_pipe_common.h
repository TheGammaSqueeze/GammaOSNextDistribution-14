// Copyright 2020 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
#pragma once

#include <stddef.h>
#include <stdint.h>

// Shared type and constant declarations between android_pipe.h and
// android_pipe_host.h

// Buffer descriptor for android_pipe_guest_send() and
// android_pipe_guest_recv().
typedef struct AndroidPipeBuffer {
    uint8_t* data;
    size_t size;
} AndroidPipeBuffer;

/* List of bitflags returned in status of CMD_POLL command */
enum PipePollFlags {
    PIPE_POLL_IN  = 1 << 0,
    PIPE_POLL_OUT = 1 << 1,
    PIPE_POLL_HUP = 1 << 2
};

/* Possible status values used to signal errors - see goldfish_pipe_error_convert */
enum PipeErrors {
    PIPE_ERROR_INVAL  = -1,
    PIPE_ERROR_AGAIN  = -2,
    PIPE_ERROR_NOMEM  = -3,
    PIPE_ERROR_IO     = -4
};

/* Bit-flags used to signal events from the emulator */
enum PipeWakeFlags {
    PIPE_WAKE_CLOSED = 1 << 0,  /* emulator closed pipe */
    PIPE_WAKE_READ   = 1 << 1,  /* pipe can now be read from */
    PIPE_WAKE_WRITE  = 1 << 2,  /* pipe can now be written to */
    PIPE_WAKE_UNLOCK_DMA  = 1 << 3,  /* unlock this pipe's DMA buffer */
    PIPE_WAKE_UNLOCK_DMA_SHARED  = 1 << 4,  /* unlock DMA buffer of the pipe shared to this pipe */
};

/* Possible pipe closing reasons */
typedef enum PipeCloseReason {
    PIPE_CLOSE_GRACEFUL = 0,      /* guest sent a close command */
    PIPE_CLOSE_REBOOT   = 1,      /* guest rebooted, we're closing the pipes */
    PIPE_CLOSE_LOAD_SNAPSHOT = 2, /* close old pipes on snapshot load */
    PIPE_CLOSE_ERROR    = 3,      /* some unrecoverable error on the pipe */
} PipeCloseReason;

/* Pipe flags for special transports and properties */
enum AndroidPipeFlags {
    /* first 4 bits are about whether it's using the normal goldfish pipe
     * or using virtio-gpu / address space */
    ANDROID_PIPE_DEFAULT = 0,
    ANDROID_PIPE_VIRTIO_GPU_BIT = (1 << 0),
    ANDROID_PIPE_ADDRESS_SPACE_BIT = (1 << 1),
    ANDROID_PIPE_RESERVED0_BIT = (1 << 2),
    ANDROID_PIPE_RESERVED1_BIT = (1 << 3),
    ANDROID_PIPE_VIRTIO_VSOCK_BIT = (1 << 4),
};
