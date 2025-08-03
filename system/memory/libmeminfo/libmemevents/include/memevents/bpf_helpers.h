/*
 * Copyright (C) 2024 The Android Open Source Project
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

#ifndef MEM_EVENTS_BPF_HELPERS_H_
#define MEM_EVENTS_BPF_HELPERS_H_

#include <bpf_helpers.h>

static inline void read_str(char* base, uint32_t __data_loc_var, char* str, uint32_t size) {
    short offset = __data_loc_var & 0xFFFF;
    bpf_probe_read_str(str, size, base + offset);
    return;
}

#endif /* MEM_EVENTS_BPF_HELPERS_H_ */