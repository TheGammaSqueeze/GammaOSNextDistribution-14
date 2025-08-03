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

#ifndef BERBERIS_RUNTIME_PRIMITIVES_TABLE_OF_TABLES_H_
#define BERBERIS_RUNTIME_PRIMITIVES_TABLE_OF_TABLES_H_

#include <sys/mman.h>

#include <atomic>
#include <cstdint>
#include <mutex>

#include "berberis/base/logging.h"
#include "berberis/base/memfd_backed_mmap.h"
#include "berberis/base/mmap.h"

namespace berberis {

template <typename Key, typename T>
class TableOfTables {
 public:
  explicit TableOfTables(T default_value) : default_value_(default_value) {
    static_assert(sizeof(T) == sizeof(uintptr_t));
    default_table_ = static_cast<decltype(default_table_)>(CreateMemfdBackedMapOrDie(
        GetOrAllocDefaultMemfdUnsafe(), kChildTableBytes, kMemfdRegionSize));

    int main_memfd =
        CreateAndFillMemfd("main", kMemfdRegionSize, reinterpret_cast<uintptr_t>(default_table_));
    main_table_ = static_cast<decltype(main_table_)>(
        CreateMemfdBackedMapOrDie(main_memfd, kTableSize * sizeof(T*), kMemfdRegionSize));
    close(main_memfd);

    // The default table is read-only.
    MprotectOrDie(default_table_, kChildTableBytes, PROT_READ);
  }

  ~TableOfTables() {
    for (size_t i = 0; i < kTableSize; ++i) {
      if (main_table_[i] != default_table_) {
        MunmapOrDie(main_table_[i], kChildTableBytes);
      }
    }

    MunmapOrDie(main_table_, kTableSize * sizeof(T*));
    MunmapOrDie(default_table_, kChildTableBytes);
    CloseDefaultMemfdUnsafe();
  }

  /*may_discard*/ std::atomic<T>* Put(Key key, T value) {
    SplitKey split_key(key);

    AllocateIfNecessary(split_key.high);

    main_table_[split_key.high][split_key.low] = value;
    return &main_table_[split_key.high][split_key.low];
  }

  [[nodiscard]] T Get(Key key) const {
    SplitKey split_key(key);
    return main_table_[split_key.high][split_key.low];
  }

  // This function returns a value address.
  //
  // Note that since this function has additional checks and may
  // result in memory allocation, it is considerably slower than Get().
  [[nodiscard]] std::atomic<T>* GetPointer(Key key) {
    SplitKey split_key(key);

    AllocateIfNecessary(split_key.high);

    return &main_table_[split_key.high][split_key.low];
  }

  [[nodiscard]] const std::atomic<std::atomic<T>*>* main_table() const { return main_table_; }

  void CloseDefaultMemfdUnsafe() {
    if (default_memfd_ == -1) {
      return;
    }
    close(default_memfd_);
    default_memfd_ = -1;
  }

 private:
  struct SplitKey {
    explicit SplitKey(Key key) : low(key & (kTableSize - 1)), high(key >> kTableBits) {
      CHECK_EQ(high & ~(kTableSize - 1), 0);
    }

    const uint32_t low;
    const uint32_t high;
    static_assert(sizeof(Key) <= sizeof(low) * 2);
  };

  int GetOrAllocDefaultMemfdUnsafe() {
    if (default_memfd_ == -1) {
      default_memfd_ = CreateAndFillMemfd(
          "child", kMemfdRegionSize, reinterpret_cast<uintptr_t>(default_value_));
    }
    return default_memfd_;
  }

  // TODO(b/191390557): Inlining this function breaks app execution. Need to figure out
  // the root cause and remove noinline.
  void __attribute__((noinline)) AllocateIfNecessary(uint32_t high_word) {
    // Fast fallback to avoid expensive mutex lock.
    if (main_table_[high_word] != default_table_) {
      return;
    }

    std::lock_guard<std::mutex> lock(mutex_);
    // Check again since the value could have been modified by other threads.
    if (main_table_[high_word] == default_table_) {
      auto tmp = static_cast<std::atomic<T>*>(CreateMemfdBackedMapOrDie(
          GetOrAllocDefaultMemfdUnsafe(), kChildTableBytes, kMemfdRegionSize));
      // Use fence to make sure the allocated table has been fully initialized
      // before main_table_ is updated to point to it.
      std::atomic_thread_fence(std::memory_order_release);
      main_table_[high_word] = tmp;
    }
  }

#if defined(__LP64__) && defined(BERBERIS_GUEST_LP64)
  // On 64-bit architectures the effective pointer bits are limited to 48
  // which makes it possible to split tables into 2^24 + 2^24.
  static constexpr size_t kTableBits = 24;
  // Use a 16Mb memfd region to fill the main/default table.
  // Linux has a limited number of maps (sysctl vm.max_map_count).
  // A larger region size allows us to stay within the limit.
  static constexpr size_t kMemfdRegionSize = 1 << 24;
  static_assert(sizeof(Key) == 8);
#elif !defined(BERBERIS_GUEST_LP64)
  static constexpr size_t kTableBits = 16;
  // Use a 64k memfd region to fill the main/default table.
  // Linux has a limited number of maps (sysctl vm.max_map_count).
  // A larger region size allows us to stay within the limit.
  static constexpr size_t kMemfdRegionSize = 1 << 16;
  static_assert(sizeof(Key) == 4);
#else
#error "Unsupported combination of a 32-bit host with a 64-bit guest"
#endif
  static constexpr size_t kTableSize = 1 << kTableBits;
  static constexpr size_t kChildTableBytes = kTableSize * sizeof(T);
  std::mutex mutex_;
  std::atomic<std::atomic<T>*>* main_table_;
  std::atomic<T>* default_table_;
  int default_memfd_{-1};
  T default_value_;
};

}  // namespace berberis

#endif  // BERBERIS_RUNTIME_PRIMITIVES_TABLE_OF_TABLES_H_
