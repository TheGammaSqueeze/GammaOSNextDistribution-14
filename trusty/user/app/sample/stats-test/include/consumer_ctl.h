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

#include <android/frameworks/stats/VendorAtom.h>
#include <lk/compiler.h>
#include <sys/types.h>
#include <atomic>

using namespace android::frameworks::stats;

#define PAGE_SIZE 0x1000
#define TIME_OUT_INTERVAL_MS 10
#define MS_TO_NS(x) ((x)*1000000ULL)

#define SHM_CONTENT_VENDOR_ATOM_VALUES 16
#define SHM_CONTENT_VENDOR_ATOM_STR_SIZE 128

struct ShmVendorAtomValue {
    union {
        int32_t i;
        int64_t l;
        float f;
        char s[SHM_CONTENT_VENDOR_ATOM_STR_SIZE];
    };
    VendorAtomValue::Tag tag;
    uint32_t reserved;
};
STATIC_ASSERT(sizeof(ShmVendorAtomValue) ==
              SHM_CONTENT_VENDOR_ATOM_STR_SIZE + 8);

struct ShmContent {
    ShmVendorAtomValue vendor_atom_values[SHM_CONTENT_VENDOR_ATOM_VALUES];
    char reverse_domain_name[SHM_CONTENT_VENDOR_ATOM_STR_SIZE];
    std::atomic<bool> full;
    int32_t atom_id;
    uint32_t ureserved;
    uint64_t reserved[PAGE_SIZE / 8 - 2 - SHM_CONTENT_VENDOR_ATOM_STR_SIZE / 8 -
                      17 * SHM_CONTENT_VENDOR_ATOM_VALUES];
};
STATIC_ASSERT(sizeof(ShmContent) == PAGE_SIZE);

enum ConsumerCtlCommand : uint32_t {
    CONSUMER_CTL_REQ_SHIFT = 1,

    CONSUMER_CTL_SHM_SHARE = (0 << CONSUMER_CTL_REQ_SHIFT),
    CONSUMER_CTL_SHM_RECLAIM = (1 << CONSUMER_CTL_REQ_SHIFT),
};

struct ConsumerCtlMsg {
    uint32_t cmd;
    uint32_t reserved;
};
