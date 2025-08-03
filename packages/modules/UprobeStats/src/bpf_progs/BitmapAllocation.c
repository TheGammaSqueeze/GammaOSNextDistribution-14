/*
 * Copyright 2023 The Android Open Source Project
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

#include <linux/bpf.h>
#include <stdbool.h>
#include <stdint.h>
#include <bpf_helpers.h>

DEFINE_BPF_RINGBUF_EXT(output_buf, __u64, 4096, AID_UPROBESTATS, AID_UPROBESTATS, 0600, "", "",
                       PRIVATE, BPFLOADER_MIN_VER, BPFLOADER_MAX_VER, LOAD_ON_ENG, LOAD_ON_USER,
                       LOAD_ON_USERDEBUG);

DEFINE_BPF_PROG("uprobe/bitmap_constructor_heap", AID_UPROBESTATS, AID_UPROBESTATS, BPF_KPROBE2)
(void* this_ptr, void* buffer_address, __u32 size) {
    __u64* output = bpf_output_buf_reserve();
    if (output == NULL) return 1;
    (*output) = 123;
    bpf_output_buf_submit(output);
    return 0;
}

LICENSE("GPL");
