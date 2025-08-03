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

#pragma once

#include <linux/in.h>
#include <linux/in6.h>

#include <stdbool.h>
#include <stdint.h>

// This header file is shared by eBPF kernel programs (C) and netd (C++) and
// some of the maps are also accessed directly from Java mainline module code.
//
// Hence: explicitly pad all relevant structures and assert that their size
// is the sum of the sizes of their fields.
#define STRUCT_SIZE(name, size) _Static_assert(sizeof(name) == (size), "Incorrect struct size.")

typedef struct {
    uint32_t iif;            // The input interface index
    struct in6_addr pfx96;   // The source /96 nat64 prefix, bottom 32 bits must be 0
    struct in6_addr local6;  // The full 128-bits of the destination IPv6 address
} ClatIngress6Key;
STRUCT_SIZE(ClatIngress6Key, 4 + 2 * 16);  // 36

typedef struct {
    uint32_t oif;           // The output interface to redirect to (0 means don't redirect)
    struct in_addr local4;  // The destination IPv4 address
} ClatIngress6Value;
STRUCT_SIZE(ClatIngress6Value, 4 + 4);  // 8

typedef struct {
    uint32_t iif;           // The input interface index
    struct in_addr local4;  // The source IPv4 address
} ClatEgress4Key;
STRUCT_SIZE(ClatEgress4Key, 4 + 4);  // 8

typedef struct {
    uint32_t oif;            // The output interface to redirect to
    struct in6_addr local6;  // The full 128-bits of the source IPv6 address
    struct in6_addr pfx96;   // The destination /96 nat64 prefix, bottom 32 bits must be 0
    bool oifIsEthernet;      // Whether the output interface requires ethernet header
    uint8_t pad[3];
} ClatEgress4Value;
STRUCT_SIZE(ClatEgress4Value, 4 + 2 * 16 + 1 + 3);  // 40

#undef STRUCT_SIZE
