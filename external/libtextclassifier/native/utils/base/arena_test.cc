/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include "utils/base/arena.h"

#include "utils/base/logging.h"
#include "utils/base/macros.h"
#include "gtest/gtest.h"

namespace libtextclassifier3 {

//------------------------------------------------------------------------
// Write random data to allocated memory
static void TestMemory(void* mem, int size) {
  // Do some memory allocation to check that the arena doesn't mess up
  // the internal memory allocator
  char* tmp[100];
  for (int i = 0; i < TC3_ARRAYSIZE(tmp); i++) {
    tmp[i] = new char[i * i + 1];
  }

  memset(mem, 0xcc, size);

  // Free up the allocated memory;
  for (char* s : tmp) {
    delete[] s;
  }
}

//------------------------------------------------------------------------
// Check memory ptr
static void CheckMemory(void* mem, int size) {
  TC3_CHECK(mem != nullptr);
  TestMemory(mem, size);
}

//------------------------------------------------------------------------
// Check memory ptr and alignment
static void CheckAlignment(void* mem, int size, int alignment) {
  TC3_CHECK(mem != nullptr);
  ASSERT_EQ(0, (reinterpret_cast<uintptr_t>(mem) & (alignment - 1)))
      << "mem=" << mem << " alignment=" << alignment;
  TestMemory(mem, size);
}

//------------------------------------------------------------------------
template <class A>
void TestArena(const char* name, A* a, int blksize) {
  TC3_VLOG(INFO) << "Testing arena '" << name << "': blksize = " << blksize
                 << ": actual blksize = " << a->block_size();

  int s;
  blksize = a->block_size();

  // Allocate zero bytes
  TC3_CHECK(a->is_empty());
  a->Alloc(0);
  TC3_CHECK(a->is_empty());

  // Allocate same as blksize
  CheckMemory(a->Alloc(blksize), blksize);
  TC3_CHECK(!a->is_empty());

  // Allocate some chunks adding up to blksize
  s = blksize / 4;
  CheckMemory(a->Alloc(s), s);
  CheckMemory(a->Alloc(s), s);
  CheckMemory(a->Alloc(s), s);

  int s2 = blksize - (s * 3);
  CheckMemory(a->Alloc(s2), s2);

  // Allocate large chunk
  CheckMemory(a->Alloc(blksize * 2), blksize * 2);
  CheckMemory(a->Alloc(blksize * 2 + 1), blksize * 2 + 1);
  CheckMemory(a->Alloc(blksize * 2 + 2), blksize * 2 + 2);
  CheckMemory(a->Alloc(blksize * 2 + 3), blksize * 2 + 3);

  // Allocate aligned
  s = blksize / 2;
  CheckAlignment(a->AllocAligned(s, 1), s, 1);
  CheckAlignment(a->AllocAligned(s + 1, 2), s + 1, 2);
  CheckAlignment(a->AllocAligned(s + 2, 2), s + 2, 2);
  CheckAlignment(a->AllocAligned(s + 3, 4), s + 3, 4);
  CheckAlignment(a->AllocAligned(s + 4, 4), s + 4, 4);
  CheckAlignment(a->AllocAligned(s + 5, 4), s + 5, 4);
  CheckAlignment(a->AllocAligned(s + 6, 4), s + 6, 4);

  // Free
  for (int i = 0; i < 100; i++) {
    int i2 = i * i;
    a->Free(a->Alloc(i2), i2);
  }

  // Memdup
  char mem[500];
  for (int i = 0; i < 500; i++) mem[i] = i & 255;
  char* mem2 = a->Memdup(mem, sizeof(mem));
  TC3_CHECK_EQ(0, memcmp(mem, mem2, sizeof(mem)));

  // MemdupPlusNUL
  const char* msg_mpn = "won't use all this length";
  char* msg2_mpn = a->MemdupPlusNUL(msg_mpn, 10);
  TC3_CHECK_EQ(0, strcmp(msg2_mpn, "won't use "));
  a->Free(msg2_mpn, 11);

  // Strdup
  const char* msg = "arena unit test is cool...";
  char* msg2 = a->Strdup(msg);
  TC3_CHECK_EQ(0, strcmp(msg, msg2));
  a->Free(msg2, strlen(msg) + 1);

  // Strndup
  char* msg3 = a->Strndup(msg, 10);
  TC3_CHECK_EQ(0, strncmp(msg3, msg, 10));
  a->Free(msg3, 10);
  TC3_CHECK(!a->is_empty());

  // Reset
  a->Reset();
  TC3_CHECK(a->is_empty());

  // Realloc
  char* m1 = a->Alloc(blksize / 2);
  CheckMemory(m1, blksize / 2);
  TC3_CHECK(!a->is_empty());
  CheckMemory(a->Alloc(blksize / 2), blksize / 2);  // Allocate another block
  m1 = a->Realloc(m1, blksize / 2, blksize);
  CheckMemory(m1, blksize);
  m1 = a->Realloc(m1, blksize, 23456);
  CheckMemory(m1, 23456);

  // Shrink
  m1 = a->Shrink(m1, 200);
  CheckMemory(m1, 200);
  m1 = a->Shrink(m1, 100);
  CheckMemory(m1, 100);
  m1 = a->Shrink(m1, 1);
  CheckMemory(m1, 1);
  a->Free(m1, 1);
  TC3_CHECK(!a->is_empty());

  // Calloc
  char* m2 = a->Calloc(2000);
  for (int i = 0; i < 2000; ++i) {
    TC3_CHECK_EQ(0, m2[i]);
  }

  // bytes_until_next_allocation
  a->Reset();
  TC3_CHECK(a->is_empty());
  int alignment = blksize - a->bytes_until_next_allocation();
  TC3_VLOG(INFO) << "Alignment overhead in initial block = " << alignment;

  s = a->bytes_until_next_allocation() - 1;
  CheckMemory(a->Alloc(s), s);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), 1);
  CheckMemory(a->Alloc(1), 1);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), 0);

  CheckMemory(a->Alloc(2 * blksize), 2 * blksize);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), 0);

  CheckMemory(a->Alloc(1), 1);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), blksize - 1);

  s = blksize / 2;
  char* m0 = a->Alloc(s);
  CheckMemory(m0, s);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), blksize - s - 1);
  m0 = a->Shrink(m0, 1);
  CheckMemory(m0, 1);
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), blksize - 2);

  a->Reset();
  TC3_CHECK(a->is_empty());
  TC3_CHECK_EQ(a->bytes_until_next_allocation(), blksize - alignment);
}

static void EnsureNoAddressInRangeIsPoisoned(void* buffer, size_t range_size) {
#ifdef ADDRESS_SANITIZER
  TC3_CHECK_EQ(nullptr, __asan_region_is_poisoned(buffer, range_size));
#endif
}

static void DoTest(const char* label, int blksize, char* buffer) {
  {
    UnsafeArena ua(buffer, blksize);
    TestArena((std::string("UnsafeArena") + label).c_str(), &ua, blksize);
  }
  EnsureNoAddressInRangeIsPoisoned(buffer, blksize);
}

//------------------------------------------------------------------------
class BasicTest : public ::testing::TestWithParam<int> {};

INSTANTIATE_TEST_SUITE_P(AllSizes, BasicTest,
                         ::testing::Values(BaseArena::kDefaultAlignment + 1, 10,
                                           100, 1024, 12345, 123450, 131072,
                                           1234500));

TEST_P(BasicTest, DoTest) {
  const int blksize = GetParam();

  // Allocate some memory from heap first
  char* tmp[100];
  for (int i = 0; i < TC3_ARRAYSIZE(tmp); i++) {
    tmp[i] = new char[i * i];
  }

  // Initial buffer for testing pre-allocated arenas
  char* buffer = new char[blksize + BaseArena::kDefaultAlignment];

  DoTest("", blksize, nullptr);
  DoTest("(p0)", blksize, buffer + 0);
  DoTest("(p1)", blksize, buffer + 1);
  DoTest("(p2)", blksize, buffer + 2);
  DoTest("(p3)", blksize, buffer + 3);
  DoTest("(p4)", blksize, buffer + 4);
  DoTest("(p5)", blksize, buffer + 5);

  // Free up the allocated heap memory
  for (char* s : tmp) {
    delete[] s;
  }

  delete[] buffer;
}

//------------------------------------------------------------------------
// NOTE: these stats will only be accurate in non-debug mode (otherwise
// they'll all be 0).  So: if you want accurate timing, run in "normal"
// or "opt" mode.  If you want accurate stats, run in "debug" mode.
void ShowStatus(const char* const header, const BaseArena::Status& status) {
  printf("\n--- status: %s\n", header);
  printf("  %zu bytes allocated\n", status.bytes_allocated());
}

// This just tests the arena code proper, without use of allocators of
// gladiators or STL or anything like that
void TestArena2(UnsafeArena* const arena) {
  const char sshort[] = "This is a short string";
  char slong[3000];
  memset(slong, 'a', sizeof(slong));
  slong[sizeof(slong) - 1] = '\0';

  char* s1 = arena->Strdup(sshort);
  char* s2 = arena->Strdup(slong);
  char* s3 = arena->Strndup(sshort, 100);
  char* s4 = arena->Strndup(slong, 100);
  char* s5 = arena->Memdup(sshort, 10);
  char* s6 = arena->Realloc(s5, 10, 20);
  arena->Shrink(s5, 10);  // get s5 back to using 10 bytes again
  char* s7 = arena->Memdup(slong, 10);
  char* s8 = arena->Realloc(s7, 10, 5);
  char* s9 = arena->Strdup(s1);
  char* s10 = arena->Realloc(s4, 100, 10);
  char* s11 = arena->Realloc(s4, 10, 100);
  char* s12 = arena->Strdup(s9);
  char* s13 = arena->Realloc(s9, sizeof(sshort) - 1, 100000);  // won't fit :-)

  TC3_CHECK_EQ(0, strcmp(s1, sshort));
  TC3_CHECK_EQ(0, strcmp(s2, slong));
  TC3_CHECK_EQ(0, strcmp(s3, sshort));
  // s4 was realloced so it is not safe to read from
  TC3_CHECK_EQ(0, strncmp(s5, sshort, 10));
  TC3_CHECK_EQ(0, strncmp(s6, sshort, 10));
  TC3_CHECK_EQ(s5, s6);  // Should have room to grow here
  // only the first 5 bytes of s7 should match; the realloc should have
  // caused the next byte to actually go to s9
  TC3_CHECK_EQ(0, strncmp(s7, slong, 5));
  TC3_CHECK_EQ(s7, s8);  // Realloc-smaller should cause us to realloc in place
  // s9 was realloced so it is not safe to read from
  TC3_CHECK_EQ(s10, s4);  // Realloc-smaller should cause us to realloc in place
  // Even though we're back to prev size, we had to move the pointer.  Thus
  // only the first 10 bytes are known since we grew from 10 to 100
  TC3_CHECK_NE(s11, s4);
  TC3_CHECK_EQ(0, strncmp(s11, slong, 10));
  TC3_CHECK_EQ(0, strcmp(s12, s1));
  TC3_CHECK_NE(s12, s13);  // too big to grow-in-place, so we should move
}

//--------------------------------------------------------------------
// Test some fundamental STL containers

template <typename T>
struct test_hash {
  int operator()(const T&) const { return 0; }
  inline bool operator()(const T& s1, const T& s2) const { return s1 < s2; }
};
template <>
struct test_hash<const char*> {
  int operator()(const char*) const { return 0; }

  inline bool operator()(const char* s1, const char* s2) const {
    return (s1 != s2) &&
           (s2 == nullptr || (s1 != nullptr && strcmp(s1, s2) < 0));
  }
};

// temp definitions from strutil.h, until the compiler error
// generated by #including that file is fixed.
struct streq {
  bool operator()(const char* s1, const char* s2) const {
    return ((s1 == nullptr && s2 == nullptr) ||
            (s1 && s2 && *s1 == *s2 && strcmp(s1, s2) == 0));
  }
};
struct strlt {
  bool operator()(const char* s1, const char* s2) const {
    return (s1 != s2) &&
           (s2 == nullptr || (s1 != nullptr && strcmp(s1, s2) < 0));
  }
};

void DoPoisonTest(BaseArena* b, size_t size) {
#ifdef ADDRESS_SANITIZER
  TC3_LOG(INFO) << "DoPoisonTest(" << b << ", " << size << ")";
  char* c1 = b->SlowAlloc(size);
  char* c2 = b->SlowAlloc(size);
  TC3_CHECK_EQ(nullptr, __asan_region_is_poisoned(c1, size));
  TC3_CHECK_EQ(nullptr, __asan_region_is_poisoned(c2, size));
  char* c3 = b->SlowRealloc(c2, size, size / 2);
  TC3_CHECK_EQ(nullptr, __asan_region_is_poisoned(c3, size / 2));
  TC3_CHECK_NE(nullptr, __asan_region_is_poisoned(c2, size));
  b->Reset();
  TC3_CHECK_NE(nullptr, __asan_region_is_poisoned(c1, size));
  TC3_CHECK_NE(nullptr, __asan_region_is_poisoned(c2, size));
  TC3_CHECK_NE(nullptr, __asan_region_is_poisoned(c3, size / 2));
#endif
}

TEST(ArenaTest, TestPoison) {
  {
    UnsafeArena arena(512);
    DoPoisonTest(&arena, 128);
    DoPoisonTest(&arena, 256);
    DoPoisonTest(&arena, 512);
    DoPoisonTest(&arena, 1024);
  }

  char* buffer = new char[512];
  {
    UnsafeArena arena(buffer, 512);
    DoPoisonTest(&arena, 128);
    DoPoisonTest(&arena, 256);
    DoPoisonTest(&arena, 512);
    DoPoisonTest(&arena, 1024);
  }
  EnsureNoAddressInRangeIsPoisoned(buffer, 512);

  delete[] buffer;
}

//------------------------------------------------------------------------

template <class A>
void TestStrndupUnterminated() {
  const char kFoo[3] = {'f', 'o', 'o'};
  char* source = new char[3];
  memcpy(source, kFoo, sizeof(kFoo));
  A arena(4096);
  char* dup = arena.Strndup(source, sizeof(kFoo));
  TC3_CHECK_EQ(0, memcmp(dup, kFoo, sizeof(kFoo)));
  delete[] source;
}

TEST(ArenaTest, StrndupWithUnterminatedStringUnsafe) {
  TestStrndupUnterminated<UnsafeArena>();
}

}  // namespace libtextclassifier3
