// Copyright 2026 GammaOS
// Licensed under GPLv2 or any later version
// Refer to the license.txt file included.

#pragma once

#include <algorithm>
#include <vector>
#include "common/common_types.h"

namespace VideoCore::GammaSnapshot {

/**
 * GammaOS async GPU mode 3: the vertex and index data a command list reads, copied when the
 * guest submitted the list. The guest gets the list's interrupt at submit and may reuse those
 * buffers before the worker runs the list; the worker's reads come from here instead.
 */
struct Range {
    PAddr addr;
    u32 size;
    std::vector<u8> bytes;
};

struct Snapshot {
    std::vector<Range> ranges; // sorted by addr, non-overlapping
};

/// The snapshot of the list the current thread is running (worker only), or nullptr.
extern thread_local const Snapshot* t_active;

/// The snapshot bytes for [addr, addr + size) when the active snapshot holds them all.
inline const u8* Find(PAddr addr, u32 size) {
    const Snapshot* s = t_active;
    if (!s || s->ranges.empty()) {
        return nullptr;
    }
    auto it = std::upper_bound(s->ranges.begin(), s->ranges.end(), addr,
                               [](PAddr a, const Range& r) { return a < r.addr; });
    if (it == s->ranges.begin()) {
        return nullptr;
    }
    --it;
    if (addr >= it->addr && static_cast<u64>(addr) + size <= static_cast<u64>(it->addr) + it->size) {
        return it->bytes.data() + (addr - it->addr);
    }
    return nullptr;
}

} // namespace VideoCore::GammaSnapshot
