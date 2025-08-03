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

#include "host-common/HostmemIdMapping.h"

#include <gtest/gtest.h>

using android::emulation::HostmemIdMapping;

// Tests creation and destruction.
TEST(HostmemIdMapping, Basic) {
    HostmemIdMapping m;
}

// Tests basic operations on an entry: add, remove, get entry info
TEST(HostmemIdMapping, BasicEntry) {
    HostmemIdMapping m;
    {
        MemEntry entry{
            .hva = (void *)(uintptr_t)0,
            .size = 1,
            .register_fixed = 0,
            .fixed_id = 0,
            .caching = MAP_CACHE_NONE,
        };
        auto id = m.add(&entry);
        EXPECT_EQ(HostmemIdMapping::kInvalidHostmemId, id);
    }
    {
        MemEntry entry{
            .hva = (void *)(uintptr_t) 1,
            .size = 0,
            .register_fixed = 0,
            .fixed_id = 0,
            .caching = MAP_CACHE_NONE,
        };
        auto id = m.add(&entry);
        EXPECT_EQ(HostmemIdMapping::kInvalidHostmemId, id);
    }
    {
        MemEntry inputEntry{
            .hva = (void *)(uintptr_t) 1,
            .size = 2,
            .register_fixed = 0,
            .fixed_id = 0,
            .caching = MAP_CACHE_NONE,
        };
        auto id = m.add(&inputEntry);
        EXPECT_NE(HostmemIdMapping::kInvalidHostmemId, id);

        auto entry = m.get(id);
        EXPECT_EQ(id, entry.id);
        EXPECT_EQ(1, (uint64_t)(uintptr_t)entry.hva);
        EXPECT_EQ(2, entry.size);

        m.remove(id);

        entry = m.get(id);

        EXPECT_EQ(HostmemIdMapping::kInvalidHostmemId, entry.id);
        EXPECT_EQ(0, (uint64_t)(uintptr_t)entry.hva);
        EXPECT_EQ(0, entry.size);
    }
}

// Tests the clear() method.
TEST(HostmemIdMapping, Clear) {
    HostmemIdMapping m;
    MemEntry entry1{
        .hva = (void *)(uintptr_t) 1,
        .size = 2,
        .register_fixed = 0,
        .fixed_id = 0,
        .caching = MAP_CACHE_NONE,
    };
    auto id1 = m.add(&entry1);
    MemEntry entry2{
        .hva = (void *)(uintptr_t) 3,
        .size = 4,
        .register_fixed = 0,
        .fixed_id = 0,
        .caching = MAP_CACHE_NONE,
    };
    auto id2 = m.add(&entry2);

    m.clear();

    auto entry = m.get(id1);
    EXPECT_EQ(HostmemIdMapping::kInvalidHostmemId, entry.id);
    EXPECT_EQ(0, (uint64_t)(uintptr_t)entry.hva);
    EXPECT_EQ(0, entry.size);

    entry = m.get(id2);
    EXPECT_EQ(HostmemIdMapping::kInvalidHostmemId, entry.id);
    EXPECT_EQ(0, (uint64_t)(uintptr_t)entry.hva);
    EXPECT_EQ(0, entry.size);
}

