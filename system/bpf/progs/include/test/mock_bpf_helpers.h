/*
 * Copyright (C) 2021 The Android Open Source Project
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

#pragma once

/* Mock BPF helpers to be used for testing of BPF programs loaded by Android */

#include <linux/bpf.h>
#include <stdbool.h>
#include <stdint.h>

#include <cutils/android_filesystem_config.h>

typedef void* mock_bpf_map_t;

/* type safe macro to declare a map and related accessor functions */
#define DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, usr, grp, md)     \
    mock_bpf_map_t mock_bpf_map_##the_map;                                                       \
                                                                                                 \
    mock_bpf_map_t get_mock_bpf_map_##the_map() {                                                \
        if (mock_bpf_map_##the_map == 0) {                                                       \
            mock_bpf_map_##the_map = mock_bpf_map_create(sizeof(TypeOfKey), sizeof(TypeOfValue), \
                                                         BPF_MAP_TYPE_##TYPE);                   \
        }                                                                                        \
        return mock_bpf_map_##the_map;                                                           \
    }                                                                                            \
    __unused TypeOfValue* bpf_##the_map##_lookup_elem(const TypeOfKey* k) {                      \
        return (TypeOfValue*)mock_bpf_lookup_elem(get_mock_bpf_map_##the_map(), (void*)k);       \
    };                                                                                           \
                                                                                                 \
    __unused int bpf_##the_map##_update_elem(const TypeOfKey* k, const TypeOfValue* v,           \
                                             uint64_t flags) {                                   \
        return mock_bpf_update_elem(get_mock_bpf_map_##the_map(), (void*)k, (void*)v, flags);    \
    };                                                                                           \
                                                                                                 \
    __unused int bpf_##the_map##_delete_elem(const TypeOfKey* k) {                               \
        return mock_bpf_delete_elem(get_mock_bpf_map_##the_map(), (void*)k);                     \
    };

#define DEFINE_BPF_MAP(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, AID_ROOT, AID_ROOT, 0600)

#define DEFINE_BPF_MAP_GWO(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, AID_ROOT, gid, 0620)

#define DEFINE_BPF_MAP_GRO(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, AID_ROOT, gid, 0640)

#define DEFINE_BPF_MAP_GRW(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, gid) \
    DEFINE_BPF_MAP_UGM(the_map, TYPE, TypeOfKey, TypeOfValue, num_entries, AID_ROOT, gid, 0660)

#define DEFINE_BPF_PROG(section, owner, group, name) int name

#ifdef __cplusplus
extern "C" {
#endif

mock_bpf_map_t mock_bpf_map_create(uint32_t key_size, uint32_t value_size, uint32_t type);
void* mock_bpf_lookup_elem(mock_bpf_map_t map, void* key);
int mock_bpf_update_elem(mock_bpf_map_t map, void* key, void* value, uint64_t flags);
int mock_bpf_delete_elem(mock_bpf_map_t map, void* key);

uint64_t bpf_ktime_get_ns();
uint64_t bpf_get_smp_processor_id();
uint64_t bpf_get_current_uid_gid();
uint64_t bpf_get_current_pid_tgid();

void mock_bpf_set_ktime_ns(uint64_t time_ns);
void mock_bpf_set_smp_processor_id(uint32_t cpu);
void mock_bpf_set_current_uid_gid(uint32_t uid);
void mock_bpf_set_current_pid_tgid(uint64_t pid_tgid);

#ifdef __cplusplus
}  // extern "C"
#endif

/* place things in different elf sections */
#define SECTION(NAME) __attribute__((section(NAME), used))

/* Example use: LICENSE("GPL"); or LICENSE("Apache 2.0"); */
#define LICENSE(NAME) char _license[] SECTION("license") = (NAME)
