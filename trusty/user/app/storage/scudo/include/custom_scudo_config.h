/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include "chunk.h"
#include "common.h"
#include "string_utils.h"

namespace scudo {

/*
 * generated using the "compute_size_class_config_tool" based on observed
 * allocations from the storage app, then manually tweaked to remove one
 * size class that only had one allocation in it, and to lower the cache
 * settings.
 */
struct TrustyCustomSizeClassConfig {
    static const uptr NumBits = 6;
    static const uptr MinSizeLog = 5;
    static const uptr MidSizeLog = 5;
    static const uptr MaxSizeLog = 15;
    static const u16 MaxNumCachedHint = 12;
    static const uptr MaxBytesCachedLog = 10;

    static constexpr u32 Classes[] = {
            0x00040, 0x00080, 0x00090, /*0x000b0,*/ 0x00150, 0x00490, 0x01090,
    };
    static const uptr SizeDelta = 16;
};
typedef TableSizeClassMap<TrustyCustomSizeClassConfig> TrustyCustomSizeClassMap;

struct TrustyCustomConfig {
    static const bool MaySupportMemoryTagging = true;
    template <class A>
    using TSDRegistryT = TSDRegistrySharedT<A, 1U, 1U>;  // Shared, max 1 TSD.

    struct Primary {
        using SizeClassMap = TrustyCustomSizeClassMap;
        static const uptr RegionSizeLog = 28U;
        static const uptr GroupSizeLog = 20U;
        typedef u32 CompactPtrT;
        static const bool EnableRandomOffset = false;
        static const uptr MapSizeIncrement = 1UL << 12;
        static const uptr CompactPtrScale = SCUDO_MIN_ALIGNMENT_LOG;
        static const s32 MinReleaseToOsIntervalMs = INT32_MIN;
        static const s32 MaxReleaseToOsIntervalMs = INT32_MAX;
    };
    template <typename Config>
    using PrimaryT = SizeClassAllocator64<Config>;

    struct Secondary {
        template <typename Config>
        using CacheT = MapAllocatorNoCache<Config>;
    };

    template <typename Config>
    using SecondaryT = MapAllocator<Config>;
};

typedef TrustyCustomConfig Config;
typedef TrustyCustomConfig DefaultConfig;

}  // namespace scudo
