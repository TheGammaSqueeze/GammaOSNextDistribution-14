/*
 * Copyright (C) 2022 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#include "bpf_helpers.h"

// This can't be easily changed since the program is loaded on boot and may be
// run against tests at a slightly different version.
#define TEST_RINGBUF_MAGIC_NUM 12345

// This ring buffer is for testing purposes only.
DEFINE_BPF_RINGBUF_EXT(test_ringbuf, __u64, 4096, AID_ROOT, AID_ROOT, 0660, "", "", PRIVATE,
                       BPFLOADER_MIN_VER, BPFLOADER_MAX_VER,
                       LOAD_ON_ENG, LOAD_ON_USER, LOAD_ON_USERDEBUG);

// This program is for test purposes only - it should never be attached to a
// socket, only executed manually with BPF_PROG_RUN.
DEFINE_BPF_PROG_KVER("skfilter/ringbuf_test", AID_ROOT, AID_ROOT, test_ringbuf_prog, KVER(5, 8, 0))
(void* unused_ctx) {
    __u64* output = bpf_test_ringbuf_reserve();
    if (output == NULL) return 1;

    (*output) = TEST_RINGBUF_MAGIC_NUM;
    bpf_test_ringbuf_submit(output);

    return 0;
}

LICENSE("Apache 2.0");
CRITICAL("BPF Ringbuf test");
