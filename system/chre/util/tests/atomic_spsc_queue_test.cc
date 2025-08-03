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

#include "chre/util/system/atomic_spsc_queue.h"
#include "chre/util/array_queue.h"
#include "gtest/gtest.h"

#include <condition_variable>
#include <mutex>
#include <thread>

using chre::ArrayQueue;
using chre::AtomicSpscQueue;
using chre::FixedSizeVector;

namespace {

constexpr int kMaxTestCapacity = 10;
int destructor_count[kMaxTestCapacity];
int constructor_count;
int total_destructor_count;

class FakeElement {
 public:
  FakeElement() {
    constructor_count++;
  };
  FakeElement(int i) {
    val_ = i;
    constructor_count++;
  };
  ~FakeElement() {
    total_destructor_count++;
    if (val_ >= 0 && val_ < kMaxTestCapacity) {
      destructor_count[val_]++;
    }
  };
  void setValue(int i) {
    val_ = i;
  }

 private:
  int val_ = kMaxTestCapacity - 1;
};

}  // namespace

TEST(AtomicSpscQueueTest, IsEmptyInitially) {
  AtomicSpscQueue<int, 4> q;
  EXPECT_EQ(4, q.capacity());
  EXPECT_TRUE(q.consumer().empty());
  EXPECT_EQ(0, q.consumer().size());
  EXPECT_EQ(0, q.producer().size());
  EXPECT_EQ(0, q.size());
}

TEST(AtomicSpscQueueTest, SimplePushPop) {
  AtomicSpscQueue<int, 3> q;
  q.producer().push(1);
  q.producer().push(2);
  EXPECT_EQ(q.consumer().front(), 1);
  EXPECT_FALSE(q.producer().full());
  q.consumer().pop();
  q.producer().push(3);
  EXPECT_EQ(q.consumer().front(), 2);
  q.consumer().pop();
  EXPECT_EQ(q.consumer().front(), 3);
}

TEST(AtomicSpscQueueTest, TestSize) {
  AtomicSpscQueue<int, 2> q;
  EXPECT_EQ(0, q.size());
  q.producer().push(1);
  EXPECT_EQ(1, q.size());
  q.producer().push(2);
  EXPECT_EQ(2, q.size());
  q.consumer().pop();
  EXPECT_EQ(1, q.size());
  q.consumer().pop();
  EXPECT_EQ(0, q.size());
}

TEST(AtomicSpscQueueTest, TestFront) {
  AtomicSpscQueue<int, 3> q;
  q.producer().emplace(1);
  EXPECT_EQ(1, q.consumer().front());
  q.consumer().pop();
  q.producer().emplace(2);
  EXPECT_EQ(2, q.consumer().front());
  q.producer().emplace(3);
  EXPECT_EQ(2, q.consumer().front());
}

TEST(AtomicSpscQueueTest, DestructorCalledOnPop) {
  for (size_t i = 0; i < kMaxTestCapacity; ++i) {
    destructor_count[i] = 0;
  }

  AtomicSpscQueue<FakeElement, 3> q;
  FakeElement e;
  q.producer().push(e);
  q.producer().push(e);

  q.consumer().front().setValue(0);
  q.consumer().pop();
  EXPECT_EQ(1, destructor_count[0]);

  q.consumer().front().setValue(1);
  q.consumer().pop();
  EXPECT_EQ(1, destructor_count[1]);
}

TEST(AtomicSpscQueueTest, ElementsDestructedWhenQueueDestructed) {
  for (size_t i = 0; i < kMaxTestCapacity; ++i) {
    destructor_count[i] = 0;
  }

  {
    AtomicSpscQueue<FakeElement, 4> q;

    for (size_t i = 0; i < 3; ++i) {
      q.producer().emplace(i);
    }
  }

  for (size_t i = 0; i < 3; ++i) {
    EXPECT_EQ(1, destructor_count[i]);
  }

  EXPECT_EQ(0, destructor_count[3]);
}

TEST(AtomicSpscQueueTest, ExtractFull) {
  constexpr size_t kSize = 16;
  AtomicSpscQueue<int32_t, kSize> q;

  for (int32_t i = 0; i < kSize; i++) {
    q.producer().push(i);
  }

  int32_t dest[kSize + 1];
  memset(dest, 0, sizeof(dest));
  dest[kSize] = 0xdeadbeef;
  size_t extracted = q.consumer().extract(dest, kSize);
  EXPECT_EQ(extracted, kSize);
  for (int32_t i = 0; i < kSize; i++) {
    EXPECT_EQ(dest[i], i);
  }
  EXPECT_EQ(0xdeadbeef, dest[kSize]);
}

TEST(AtomicSpscQueueTest, ExtractPartial) {
  constexpr size_t kSize = 16;
  AtomicSpscQueue<int32_t, kSize> q;

  for (int32_t i = 0; i < kSize / 2; i++) {
    q.producer().push(i);
  }

  int32_t dest[kSize + 1];
  memset(dest, 0, sizeof(dest));
  size_t extracted = q.consumer().extract(dest, kSize / 4);
  EXPECT_EQ(extracted, kSize / 4);
  for (int32_t i = 0; i < kSize / 4; i++) {
    EXPECT_EQ(dest[i], i);
  }
  EXPECT_EQ(0, dest[kSize / 4]);
  EXPECT_EQ(kSize / 4, q.size());

  extracted = q.consumer().extract(&dest[kSize / 4], kSize / 4);
  EXPECT_EQ(extracted, kSize / 4);
  for (int32_t i = kSize / 4; i < kSize / 2; i++) {
    EXPECT_EQ(dest[i], i);
  }
  EXPECT_EQ(0, dest[kSize]);
  EXPECT_TRUE(q.consumer().empty());

  q.producer().push(0xd00d);
  EXPECT_EQ(0xd00d, q.consumer().front());
  q.consumer().pop();
  EXPECT_TRUE(q.consumer().empty());
}

TEST(AtomicSpscQueueTest, ExtractWraparound) {
  constexpr size_t kSize = 16;
  AtomicSpscQueue<int32_t, kSize> q;
  auto p = q.producer();
  auto c = q.consumer();

  for (int32_t i = 0; i < kSize; i++) {
    p.push(i);
  }

  for (int32_t i = kSize; i < kSize + kSize / 2; i++) {
    c.pop();
    p.push(i);
  }

  // Now two copies will be needed to extract the data
  int32_t dest[kSize + 1];
  memset(dest, 0, sizeof(dest));
  dest[kSize] = 0xdeadbeef;

  // Pull all except 1
  size_t extracted = c.extract(dest, kSize - 1);
  EXPECT_EQ(extracted, kSize - 1);

  // And now the last one (asking for more than we expect to get)
  EXPECT_EQ(1, q.size());
  extracted = c.extract(&dest[kSize - 1], 2);
  EXPECT_EQ(extracted, 1);

  for (int32_t i = 0; i < kSize; i++) {
    EXPECT_EQ(dest[i], i + kSize / 2);
  }
  EXPECT_EQ(0xdeadbeef, dest[kSize]);
}

TEST(AtomicSpscQueueTest, PopWraparound) {
  constexpr size_t kSize = 16;
  AtomicSpscQueue<int32_t, kSize> q;
  auto p = q.producer();
  auto c = q.consumer();

  for (int32_t i = 0; i < kSize; i++) {
    p.push(i);
  }

  for (int32_t i = kSize; i < kSize + kSize / 2; i++) {
    EXPECT_EQ(c.front(), i - kSize);
    c.pop();
    p.push(i);
  }

  for (int32_t i = kSize / 2; i < kSize + kSize / 2; i++) {
    EXPECT_EQ(c.front(), i);
    c.pop();
  }
}

TEST(AtomicSpscQueueTest, ExtractVector) {
  constexpr size_t kSize = 8;
  AtomicSpscQueue<int, kSize> q;

  auto p = q.producer();
  for (int i = 0; i < kSize; i++) {
    p.push(i);
  }

  auto c = q.consumer();
  constexpr size_t kExtraSpace = 2;
  static_assert(kSize > kExtraSpace + 2, "Test assumption broken");
  FixedSizeVector<int, kSize + kExtraSpace> v;

  // Output size dependent on elements available in queue
  size_t extracted = c.extract(&v);
  EXPECT_EQ(extracted, kSize);
  EXPECT_EQ(kSize, v.size());
  for (int i = 0; i < kSize; i++) {
    EXPECT_EQ(v[i], i);
  }

  for (int i = kSize; i < kSize + kExtraSpace; i++) {
    p.push(i);
  }
  p.push(1337);
  p.push(42);

  // Output size dependent on space available in vector
  extracted = c.extract(&v);
  EXPECT_EQ(extracted, kExtraSpace);
  EXPECT_EQ(v.capacity(), v.size());
  for (int i = 0; i < kSize + kExtraSpace; i++) {
    EXPECT_EQ(v[i], i);
  }
  EXPECT_EQ(2, q.size());

  // Output size 0 (no space left in vector)
  extracted = c.extract(&v);
  EXPECT_EQ(0, extracted);
  EXPECT_EQ(2, q.size());

  // Extract into reset vector
  v.resize(0);
  extracted = c.extract(&v);
  EXPECT_EQ(2, extracted);
  EXPECT_EQ(2, v.size());
  EXPECT_EQ(v[0], 1337);
  EXPECT_EQ(v[1], 42);

  // Output size 0 (no elements left in queue)
  EXPECT_TRUE(q.consumer().empty());
  extracted = c.extract(&v);
  EXPECT_EQ(0, extracted);
}

// If this test fails it's likely due to thread interleaving, so consider
// increasing kMaxCount (e.g. by a factor of 100 or more) and/or run the test in
// parallel on multiple processes to increase the likelihood of repro.
TEST(AtomicSpscQueueStressTest, ConcurrencyStress) {
  constexpr size_t kCapacity = 2048;
  constexpr int64_t kMaxCount = 100 * kCapacity;
  AtomicSpscQueue<int64_t, kCapacity> q;

  auto producer = q.producer();
  std::thread producerThread = std::thread(
      [](decltype(producer) p) {
        int64_t count = 0;
        while (count <= kMaxCount) {
          if (p.full()) {
            // Give the other thread a chance to be scheduled
            std::this_thread::yield();
            continue;
          }

          p.push(count++);
        }
      },
      producer);

  auto consumer = q.consumer();
  std::thread consumerThread = std::thread(
      [](decltype(consumer) c) {
        int64_t last = -1;
        do {
          if (c.empty()) {
            std::this_thread::yield();
            continue;
          }
          int64_t next = c.front();
          if (last != -1) {
            EXPECT_EQ(last + 1, next);
          }
          last = next;
          c.pop();
        } while (last < kMaxCount);
      },
      consumer);

  producerThread.join();
  consumerThread.join();

  EXPECT_EQ(0, q.size());
}

// Helpers for SynchronizedConcurrencyStress
enum class Op {
  kPush = 0,
  kPull = 1,
};
struct HistoryEntry {
  Op op;
  int numElements;
  int64_t last;

  HistoryEntry() = default;
  HistoryEntry(Op op_, int numElements_, int64_t last_)
      : op(op_), numElements(numElements_), last(last_) {}
};

constexpr size_t kHistorySize = 512;

namespace chre {  // (PrintTo needs to be in the same namespace as ArrayQueue)

void PrintTo(const ArrayQueue<HistoryEntry, kHistorySize> &history,
             std::ostream *os) {
  *os << "Dumping history from oldest to newest:" << std::endl;
  for (const HistoryEntry &entry : history) {
    *os << "  " << ((entry.op == Op::kPush) ? "push " : "pull ") << std::setw(3)
        << entry.numElements << " elements, last " << entry.last << std::endl;
  }
}

}  // namespace chre

// If this test fails it's likely due to thread interleaving, so consider
// increasing kMaxCount (e.g. by a factor of 100 or more) and/or run the test in
// parallel on multiple processes to increase the likelihood of repro.
TEST(AtomicSpscQueueStressTest, SynchronizedConcurrencyStress) {
  constexpr size_t kCapacity = 512;
  constexpr int64_t kMaxCount = 2000 * kCapacity;
  AtomicSpscQueue<int64_t, kCapacity> q;

  std::mutex m;
  std::condition_variable cv;

  // Guarded by mutex m
  ArrayQueue<HistoryEntry, kHistorySize> history;
  int64_t totalOps = 0;

  auto lfsr = []() {
    // 9-bit LFSR with feedback polynomial x^9 + x^5 + 1 gives us a
    // pseudo-random sequence over all 511 possible values
    static uint16_t lfsr = 1;
    uint16_t nextBit = ((lfsr << 8) ^ (lfsr << 4)) & 0x100;
    lfsr = nextBit | (lfsr >> 1);

    return lfsr;
  };
  bool pending = false;

  auto p = q.producer();
  std::thread producerThread = std::thread([&]() {
    int64_t count = 0;
    while (count <= kMaxCount) {
      // Push in a pseudo-random number of elements into the queue, then notify
      // the consumer; yield if we can't push it all at once
      uint16_t pushCount = lfsr();
      while (p.capacity() - p.size() < pushCount) {
        std::this_thread::yield();
      }

      for (int i = 0; i < pushCount; i++) {
        p.push(count++);
        if (count > kMaxCount) {
          break;
        }
      }

      m.lock();
      history.kick_push(HistoryEntry(Op::kPush, pushCount, count - 1));
      totalOps++;
      pending = true;
      m.unlock();
      cv.notify_one();
    }
  });

  auto c = q.consumer();
  std::thread consumerThread = std::thread([&]() {
    int64_t last = -1;
    size_t extracted = 0;
    FixedSizeVector<int64_t, kCapacity> myBuf;
    while (last < kMaxCount) {
      {
        std::unique_lock<std::mutex> lock(m);
        if (last != -1) {
          history.kick_push(HistoryEntry(Op::kPull, extracted, last));
          totalOps++;
        }
        while (c.empty() && !pending) {
          cv.wait(lock);
          if (pending) {
            pending = false;
            break;
          }
        }
      }

      extracted = c.extract(&myBuf);
      EXPECT_LE(extracted, kCapacity);
      for (int i = 0; i < extracted; i++) {
        int64_t next = myBuf[i];
        if (last != -1 && last + 1 != next) {
          std::lock_guard<std::mutex> lock(m);
          EXPECT_EQ(last + 1, next)
              << "After pulling " << extracted << " elements, value at offset "
              << i << " is incorrect: expected " << (last + 1) << " but got "
              << next << "." << std::endl
              << testing::PrintToString(history)
              // totalOps + 1 because this call to extract() isn't counted yet
              << " Total operations since start: " << (totalOps + 1)
              << std::endl
              << "Note: most recent push may not be included in the history, "
              << "most recent pull is definitely not included (but indicated "
              << "in the first sentence above)." << std::endl;
          // The history is unlikely to have the most recent push operation
          // because the consumer thread runs freely until it tries to acquire
          // the mutex to add to the history. In other words, it may have pushed
          // any time between after we unblock from wait() and reach here, but
          // hasn't added it to the history yet.
        }
        last = next;
      }
      myBuf.resize(0);
    }
  });

  producerThread.join();
  consumerThread.join();

  EXPECT_EQ(0, q.size());
}
