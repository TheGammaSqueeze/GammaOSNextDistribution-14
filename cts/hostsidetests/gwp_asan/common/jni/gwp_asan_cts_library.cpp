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

#include <android/log.h>
#include <jni.h>
#include <malloc.h>
#include <stdio.h>
#include <stdlib.h>

#include <fstream>
#include <memory>
#include <shared_mutex>
#include <string>
#include <vector>

// We can get all the GWP-ASan ranges ahead of time. GWP-ASan doesn't do an mmap() for each
// allocation, it reserves the entire pool up front (with the name "[anon:GWP-ASan Guard Page]") and
// then mprotect()s and renames pages in that pool as necessary. At the point where we're observing
// the range, it's normal to have a couple of slots already in use. Technically, the metadata region
// also exists at startup ("[anon:GWP-ASan Metadata]"), but no malloc() will ever be allocated
// there, and it's not necessary to special case this single range.
const std::vector<std::pair<uintptr_t, uintptr_t>>& get_gwp_asan_ranges() {
    static std::mutex gwp_asan_ranges_mutex;
    static std::vector<std::pair<uintptr_t, uintptr_t>> gwp_asan_ranges;

    std::lock_guard<std::mutex> l(gwp_asan_ranges_mutex);
    if (gwp_asan_ranges.size() != 0) return gwp_asan_ranges;

    std::ifstream mappings("/proc/self/maps");
    if (!mappings.good()) {
        __android_log_print(ANDROID_LOG_FATAL, getprogname(), "Failed to open /proc/self/maps");
    }

    std::string line;
    while (std::getline(mappings, line)) {
        uintptr_t map_start, map_end;
        if (line.find("[anon:GWP-ASan") != std::string::npos &&
            sscanf(line.c_str(), "%zx-%zx", &map_start, &map_end) == 2) {
            gwp_asan_ranges.emplace_back(map_start, map_end);
            __android_log_print(ANDROID_LOG_INFO, getprogname(),
                                "Found 0x%zx-byte GWP-ASan mapping: \"%s\"", map_end - map_start,
                                line.c_str());
        }
    }
    return gwp_asan_ranges;
}

bool is_gwp_asan_pointer(void* ptr) {
    static const std::vector<std::pair<uintptr_t, uintptr_t>>& ranges = get_gwp_asan_ranges();
    uintptr_t untagged_ptr = reinterpret_cast<uintptr_t>(ptr);
#if defined(__aarch64__)
    // Untag the heap pointer: https://source.android.com/docs/security/test/tagged-pointers
    untagged_ptr &= ~(0xffull << 56);
#endif // defined(__aarch64__)
    return std::any_of(ranges.cbegin(), ranges.cend(), [&](const auto& range) {
        return untagged_ptr >= range.first && untagged_ptr < range.second;
    });
}

constexpr size_t kMallocsToGuaranteeAGwpAsanPointer = 0x10000;

std::unique_ptr<char[]> get_gwp_asan_pointer() {
    for (size_t i = 0; i < kMallocsToGuaranteeAGwpAsanPointer; ++i) {
        auto p = std::make_unique<char[]>(4096);
        if (is_gwp_asan_pointer(p.get())) {
            __android_log_print(ANDROID_LOG_INFO, getprogname(), "Found GWP-ASan pointer: %p",
                                p.get());
            return p;
        }
    }
    return std::unique_ptr<char[]>();
}

// Note: The '_1' in the function signature here is the JNI literalization of the underscore in the
// 'gwp_asan' part of the package name. See "Table 2-1 Unicode Character Translation" in
// https://docs.oracle.com/javase/7/docs/technotes/guides/jni/spec/design.html
extern "C" JNIEXPORT jboolean JNICALL Java_android_cts_gwp_1asan_Utils_isGwpAsanEnabled(JNIEnv*) {
    std::unique_ptr<char[]> gwp_asan_ptr = get_gwp_asan_pointer();
    return gwp_asan_ptr.get() == nullptr ? JNI_FALSE : JNI_TRUE;
}

extern "C" JNIEXPORT void JNICALL
Java_android_cts_gwp_1asan_Utils_instrumentedUseAfterFree(JNIEnv*) {
    char* volatile p = nullptr;
    {
        std::unique_ptr<char[]> gwp_asan_ptr = get_gwp_asan_pointer();
        p = gwp_asan_ptr.get();
    }
    if (!p) return;
    __attribute__((unused)) volatile char c = *p;
}
