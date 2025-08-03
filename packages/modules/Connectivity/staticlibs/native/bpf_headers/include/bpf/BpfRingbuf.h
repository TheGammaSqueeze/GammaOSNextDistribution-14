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

#include <android-base/result.h>
#include <android-base/unique_fd.h>
#include <linux/bpf.h>
#include <poll.h>
#include <sys/mman.h>
#include <utils/Log.h>

#include "bpf/BpfUtils.h"

#include <atomic>

namespace android {
namespace bpf {

// BpfRingbufBase contains the non-templated functionality of BPF ring buffers.
class BpfRingbufBase {
 public:
  ~BpfRingbufBase() {
    if (mConsumerPos) munmap(mConsumerPos, mConsumerSize);
    if (mProducerPos) munmap(mProducerPos, mProducerSize);
    mConsumerPos = nullptr;
    mProducerPos = nullptr;
  }

  bool isEmpty(void);

  // returns !isEmpty() for convenience
  bool wait(int timeout_ms = -1);

 protected:
  // Non-initializing constructor, used by Create.
  BpfRingbufBase(size_t value_size) : mValueSize(value_size) {}

  // Full construction that aborts on error (use Create/Init to handle errors).
  BpfRingbufBase(const char* path, size_t value_size) : mValueSize(value_size) {
    if (auto status = Init(path); !status.ok()) {
      ALOGE("BpfRingbuf init failed: %s", status.error().message().c_str());
      abort();
    }
  }

  // Delete copy constructor (class owns raw pointers).
  BpfRingbufBase(const BpfRingbufBase&) = delete;

  // Initialize the base ringbuffer components. Must be called exactly once.
  base::Result<void> Init(const char* path);

  // Consumes all messages from the ring buffer, passing them to the callback.
  base::Result<int> ConsumeAll(
      const std::function<void(const void*)>& callback);

  // Replicates c-style void* "byte-wise" pointer addition.
  template <typename Ptr>
  static Ptr pointerAddBytes(void* base, ssize_t offset_bytes) {
    return reinterpret_cast<Ptr>(reinterpret_cast<char*>(base) + offset_bytes);
  }

  // Rounds len by clearing bitmask, adding header, and aligning to 8 bytes.
  static uint32_t roundLength(uint32_t len) {
    len &= ~(BPF_RINGBUF_BUSY_BIT | BPF_RINGBUF_DISCARD_BIT);
    len += BPF_RINGBUF_HDR_SZ;
    return (len + 7) & ~7;
  }

  const size_t mValueSize;

  size_t mConsumerSize;
  size_t mProducerSize;
  unsigned long mPosMask;
  android::base::unique_fd mRingFd;

  void* mDataPos = nullptr;
  // The kernel uses an "unsigned long" type for both consumer and producer position.
  // Unsigned long is a 4 byte value on a 32-bit kernel, and an 8 byte value on a 64-bit kernel.
  // To support 32-bit kernels, producer pos is capped at 4 bytes (despite it being 8 bytes on
  // 64-bit kernels) and all comparisons of consumer and producer pos only compare the low-order 4
  // bytes (an inequality comparison is performed to support overflow).
  // This solution is bitness agnostic. The consumer only increments the 8 byte consumer pos, which,
  // in a little-endian architecture, is safe since the entire page is mapped into memory and a
  // 32-bit kernel will just ignore the high-order bits.
  std::atomic_uint64_t* mConsumerPos = nullptr;
  std::atomic_uint32_t* mProducerPos = nullptr;

  // In order to guarantee atomic access in a 32 bit userspace environment, atomic_uint64_t is used
  // in addition to std::atomic<T>::is_always_lock_free that guarantees that read / write operations
  // are indeed atomic.
  // Since std::atomic does not support wrapping preallocated memory, an additional static assert on
  // the size of the atomic and the underlying type is added to ensure a reinterpret_cast from type
  // to its atomic version is safe (is_always_lock_free being true should provide additional
  // confidence).
  static_assert(std::atomic_uint64_t::is_always_lock_free);
  static_assert(std::atomic_uint32_t::is_always_lock_free);
  static_assert(sizeof(std::atomic_uint64_t) == sizeof(uint64_t));
  static_assert(sizeof(std::atomic_uint32_t) == sizeof(uint32_t));
};

// This is a class wrapper for eBPF ring buffers. An eBPF ring buffer is a
// special type of eBPF map used for sending messages from eBPF to userspace.
// The implementation relies on fast shared memory and atomics for the producer
// and consumer management. Ring buffers are a faster alternative to eBPF perf
// buffers.
//
// This class is thread compatible, but not thread safe.
//
// Note: A kernel eBPF ring buffer may be accessed by both kernel and userspace
// processes at the same time. However, the userspace consumers of a given ring
// buffer all share a single read pointer. There is no guarantee which readers
// will read which messages.
template <typename Value>
class BpfRingbuf : public BpfRingbufBase {
 public:
  using MessageCallback = std::function<void(const Value&)>;

  // Creates a ringbuffer wrapper from a pinned path. This initialization will
  // abort on error. To handle errors, initialize with Create instead.
  BpfRingbuf(const char* path) : BpfRingbufBase(path, sizeof(Value)) {}

  // Creates a ringbuffer wrapper from a pinned path. There are no guarantees
  // that the ringbuf outputs messaged of type `Value`, only that they are the
  // same size. Size is only checked in ConsumeAll.
  static base::Result<std::unique_ptr<BpfRingbuf<Value>>> Create(
      const char* path);

  // Consumes all messages from the ring buffer, passing them to the callback.
  // Returns the number of messages consumed or a non-ok result on error. If the
  // ring buffer has no pending messages an OK result with count 0 is returned.
  base::Result<int> ConsumeAll(const MessageCallback& callback);

 private:
  // Empty ctor for use by Create.
  BpfRingbuf() : BpfRingbufBase(sizeof(Value)) {}
};


inline base::Result<void> BpfRingbufBase::Init(const char* path) {
  mRingFd.reset(mapRetrieveRW(path));
  if (!mRingFd.ok()) {
    return android::base::ErrnoError()
           << "failed to retrieve ringbuffer at " << path;
  }

  int map_type = android::bpf::bpfGetFdMapType(mRingFd);
  if (map_type != BPF_MAP_TYPE_RINGBUF) {
    errno = EINVAL;
    return android::base::ErrnoError()
           << "bpf map has wrong type: want BPF_MAP_TYPE_RINGBUF ("
           << BPF_MAP_TYPE_RINGBUF << ") got " << map_type;
  }

  int max_entries = android::bpf::bpfGetFdMaxEntries(mRingFd);
  if (max_entries < 0) {
    return android::base::ErrnoError()
           << "failed to read max_entries from ringbuf";
  }
  if (max_entries == 0) {
    errno = EINVAL;
    return android::base::ErrnoError() << "max_entries must be non-zero";
  }

  mPosMask = max_entries - 1;
  mConsumerSize = getpagesize();
  mProducerSize = getpagesize() + 2 * max_entries;

  {
    void* ptr = mmap(NULL, mConsumerSize, PROT_READ | PROT_WRITE, MAP_SHARED,
                     mRingFd, 0);
    if (ptr == MAP_FAILED) {
      return android::base::ErrnoError()
             << "failed to mmap ringbuf consumer pages";
    }
    mConsumerPos = reinterpret_cast<decltype(mConsumerPos)>(ptr);
  }

  {
    void* ptr = mmap(NULL, mProducerSize, PROT_READ, MAP_SHARED, mRingFd,
                     mConsumerSize);
    if (ptr == MAP_FAILED) {
      return android::base::ErrnoError()
             << "failed to mmap ringbuf producer page";
    }
    mProducerPos = reinterpret_cast<decltype(mProducerPos)>(ptr);
  }

  mDataPos = pointerAddBytes<void*>(mProducerPos, getpagesize());
  return {};
}

inline bool BpfRingbufBase::isEmpty(void) {
  uint32_t prod_pos = mProducerPos->load(std::memory_order_relaxed);
  uint64_t cons_pos = mConsumerPos->load(std::memory_order_relaxed);
  return (cons_pos & 0xFFFFFFFF) == prod_pos;
}

inline bool BpfRingbufBase::wait(int timeout_ms) {
  // possible optimization: if (!isEmpty()) return true;
  struct pollfd pfd = {  // 1-element array
    .fd = mRingFd.get(),
    .events = POLLIN,
  };
  (void)poll(&pfd, 1, timeout_ms);  // 'best effort' poll
  return !isEmpty();
}

inline base::Result<int> BpfRingbufBase::ConsumeAll(
    const std::function<void(const void*)>& callback) {
  int64_t count = 0;
  uint32_t prod_pos = mProducerPos->load(std::memory_order_acquire);
  // Only userspace writes to mConsumerPos, so no need to use std::memory_order_acquire
  uint64_t cons_pos = mConsumerPos->load(std::memory_order_relaxed);
  while ((cons_pos & 0xFFFFFFFF) != prod_pos) {
    // Find the start of the entry for this read (wrapping is done here).
    void* start_ptr = pointerAddBytes<void*>(mDataPos, cons_pos & mPosMask);

    // The entry has an 8 byte header containing the sample length.
    // struct bpf_ringbuf_hdr {
    //   u32 len;
    //   u32 pg_off;
    // };
    uint32_t length = *reinterpret_cast<volatile uint32_t*>(start_ptr);

    // If the sample isn't committed, we're caught up with the producer.
    if (length & BPF_RINGBUF_BUSY_BIT) return count;

    cons_pos += roundLength(length);

    if ((length & BPF_RINGBUF_DISCARD_BIT) == 0) {
      if (length != mValueSize) {
        mConsumerPos->store(cons_pos, std::memory_order_release);
        errno = EMSGSIZE;
        return android::base::ErrnoError()
               << "BPF ring buffer message has unexpected size (want "
               << mValueSize << " bytes, got " << length << " bytes)";
      }
      callback(pointerAddBytes<const void*>(start_ptr, BPF_RINGBUF_HDR_SZ));
      count++;
    }

    mConsumerPos->store(cons_pos, std::memory_order_release);
  }

  return count;
}

template <typename Value>
inline base::Result<std::unique_ptr<BpfRingbuf<Value>>>
BpfRingbuf<Value>::Create(const char* path) {
  auto rb = std::unique_ptr<BpfRingbuf>(new BpfRingbuf);
  if (auto status = rb->Init(path); !status.ok()) return status.error();
  return rb;
}

template <typename Value>
inline base::Result<int> BpfRingbuf<Value>::ConsumeAll(
    const MessageCallback& callback) {
  return BpfRingbufBase::ConsumeAll([&](const void* value) {
    callback(*reinterpret_cast<const Value*>(value));
  });
}

}  // namespace bpf
}  // namespace android
