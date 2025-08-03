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

#include "local_reference_table-inl.h"

#include "android-base/stringprintf.h"

#include "class_root-inl.h"
#include "common_runtime_test.h"
#include "mirror/class-alloc-inl.h"
#include "mirror/object-inl.h"
#include "scoped_thread_state_change-inl.h"

namespace art HIDDEN {
namespace jni {

using android::base::StringPrintf;

class LocalReferenceTableTest : public CommonRuntimeTest {
 protected:
  LocalReferenceTableTest() {
    use_boot_image_ = true;  // Make the Runtime creation cheaper.
  }

  static void CheckDump(LocalReferenceTable* lrt, size_t num_objects, size_t num_unique)
      REQUIRES_SHARED(Locks::mutator_lock_);

  void BasicTest(bool check_jni, size_t max_count);
  void BasicHolesTest(bool check_jni, size_t max_count);
  void BasicResizeTest(bool check_jni, size_t max_count);
  void TestAddRemove(bool check_jni, size_t max_count, size_t fill_count = 0u);
  void TestAddRemoveMixed(bool start_check_jni);
};

void LocalReferenceTableTest::CheckDump(
    LocalReferenceTable* lrt, size_t num_objects, size_t num_unique) {
  std::ostringstream oss;
  lrt->Dump(oss);
  if (num_objects == 0) {
    EXPECT_EQ(oss.str().find("java.lang.Object"), std::string::npos) << oss.str();
  } else if (num_objects == 1) {
    EXPECT_NE(oss.str().find("1 of java.lang.Object"), std::string::npos) << oss.str();
  } else {
    EXPECT_NE(oss.str().find(StringPrintf("%zd of java.lang.Object (%zd unique instances)",
                                          num_objects, num_unique)),
              std::string::npos)
                  << "\n Expected number of objects: " << num_objects
                  << "\n Expected unique objects: " << num_unique << "\n"
                  << oss.str();
  }
}

void LocalReferenceTableTest::BasicTest(bool check_jni, size_t max_count) {
  // This will lead to error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);

  ScopedObjectAccess soa(Thread::Current());
  StackHandleScope<5> hs(soa.Self());
  Handle<mirror::Class> c = hs.NewHandle(GetClassRoot<mirror::Object>());
  ASSERT_TRUE(c != nullptr);
  Handle<mirror::Object> obj0 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj0 != nullptr);
  Handle<mirror::Object> obj1 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj1 != nullptr);
  Handle<mirror::Object> obj2 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj2 != nullptr);
  Handle<mirror::Object> obj3 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj3 != nullptr);

  std::string error_msg;
  LocalReferenceTable lrt(check_jni);
  bool success = lrt.Initialize(max_count, &error_msg);
  ASSERT_TRUE(success) << error_msg;

  CheckDump(&lrt, 0, 0);

  if (check_jni) {
    IndirectRef bad_iref = (IndirectRef) 0x11110;
    EXPECT_FALSE(lrt.Remove(bad_iref)) << "unexpectedly successful removal";
  }

  // Add three, check, remove in the order in which they were added.
  IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  CheckDump(&lrt, 1, 1);
  IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  CheckDump(&lrt, 2, 2);
  IndirectRef iref2 = lrt.Add(obj2.Get(), &error_msg);
  EXPECT_TRUE(iref2 != nullptr);
  CheckDump(&lrt, 3, 3);

  EXPECT_OBJ_PTR_EQ(obj0.Get(), lrt.Get(iref0));
  EXPECT_OBJ_PTR_EQ(obj1.Get(), lrt.Get(iref1));
  EXPECT_OBJ_PTR_EQ(obj2.Get(), lrt.Get(iref2));

  EXPECT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 2, 2);
  EXPECT_TRUE(lrt.Remove(iref1));
  CheckDump(&lrt, 1, 1);
  EXPECT_TRUE(lrt.Remove(iref2));
  CheckDump(&lrt, 0, 0);

  // Table should be empty now.
  EXPECT_EQ(0U, lrt.Capacity());

  // Check that the entry off the end of the list is not valid.
  // (CheckJNI shall abort for such entries.)
  EXPECT_FALSE(lrt.IsValidReference(iref0, &error_msg));

  // Add three, remove in the opposite order.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  iref2 = lrt.Add(obj2.Get(), &error_msg);
  EXPECT_TRUE(iref2 != nullptr);
  CheckDump(&lrt, 3, 3);

  ASSERT_TRUE(lrt.Remove(iref2));
  CheckDump(&lrt, 2, 2);
  ASSERT_TRUE(lrt.Remove(iref1));
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 0, 0);

  // Table should be empty now.
  ASSERT_EQ(0U, lrt.Capacity());

  // Add three, remove middle / middle / bottom / top.  (Second attempt
  // to remove middle should fail.)
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  iref2 = lrt.Add(obj2.Get(), &error_msg);
  EXPECT_TRUE(iref2 != nullptr);
  CheckDump(&lrt, 3, 3);

  ASSERT_EQ(3U, lrt.Capacity());

  ASSERT_TRUE(lrt.Remove(iref1));
  CheckDump(&lrt, 2, 2);
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(iref1));
    CheckDump(&lrt, 2, 2);
  }

  // Check that the reference to the hole is not valid.
  EXPECT_FALSE(lrt.IsValidReference(iref1, &error_msg));

  ASSERT_TRUE(lrt.Remove(iref2));
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 0, 0);

  // Table should be empty now.
  ASSERT_EQ(0U, lrt.Capacity());

  // Add four entries.  Remove #1, add new entry, verify that table size
  // is still 4 (i.e. holes are getting filled).  Remove #1 and #3, verify
  // that we delete one and don't hole-compact the other.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  iref2 = lrt.Add(obj2.Get(), &error_msg);
  EXPECT_TRUE(iref2 != nullptr);
  IndirectRef iref3 = lrt.Add(obj3.Get(), &error_msg);
  EXPECT_TRUE(iref3 != nullptr);
  CheckDump(&lrt, 4, 4);

  ASSERT_TRUE(lrt.Remove(iref1));
  CheckDump(&lrt, 3, 3);

  iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);

  ASSERT_EQ(4U, lrt.Capacity()) << "hole not filled";
  CheckDump(&lrt, 4, 4);

  ASSERT_TRUE(lrt.Remove(iref1));
  CheckDump(&lrt, 3, 3);
  ASSERT_TRUE(lrt.Remove(iref3));
  CheckDump(&lrt, 2, 2);

  ASSERT_EQ(3U, lrt.Capacity()) << "should be 3 after two deletions";

  ASSERT_TRUE(lrt.Remove(iref2));
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 0, 0);

  ASSERT_EQ(0U, lrt.Capacity()) << "not empty after split remove";

  // Add an entry, remove it, add a new entry, and try to use the original
  // iref.  They have the same slot number but are for different objects.
  // With the extended checks in place, this should fail.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 0, 0);
  iref1 = lrt.Add(obj1.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  CheckDump(&lrt, 1, 1);
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(iref0)) << "mismatched del succeeded";
    CheckDump(&lrt, 1, 1);
  }
  ASSERT_TRUE(lrt.Remove(iref1)) << "switched del failed";
  ASSERT_EQ(0U, lrt.Capacity()) << "switching del not empty";
  CheckDump(&lrt, 0, 0);

  // Same as above, but with the same object.  A more rigorous checker
  // (e.g. with slot serialization) will catch this.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  CheckDump(&lrt, 0, 0);
  iref1 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref1 != nullptr);
  CheckDump(&lrt, 1, 1);
  if (iref0 != iref1) {
    // Try 0, should not work.
    ASSERT_FALSE(lrt.Remove(iref0)) << "temporal del succeeded";
  }
  ASSERT_TRUE(lrt.Remove(iref1)) << "temporal cleanup failed";
  ASSERT_EQ(0U, lrt.Capacity()) << "temporal del not empty";
  CheckDump(&lrt, 0, 0);

  // Stale reference is not valid.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  EXPECT_TRUE(iref0 != nullptr);
  CheckDump(&lrt, 1, 1);
  ASSERT_TRUE(lrt.Remove(iref0));
  EXPECT_FALSE(lrt.IsValidReference(iref0, &error_msg)) << "stale lookup succeeded";
  CheckDump(&lrt, 0, 0);

  // Test table resizing.
  // These ones fit...
  static const size_t kTableInitial = max_count / 2;
  IndirectRef manyRefs[kTableInitial];
  for (size_t i = 0; i < kTableInitial; i++) {
    manyRefs[i] = lrt.Add(obj0.Get(), &error_msg);
    ASSERT_TRUE(manyRefs[i] != nullptr) << "Failed adding " << i;
    CheckDump(&lrt, i + 1, 1);
  }
  // ...this one causes overflow.
  iref0 = lrt.Add(obj0.Get(), &error_msg);
  ASSERT_TRUE(iref0 != nullptr);
  ASSERT_EQ(kTableInitial + 1, lrt.Capacity());
  CheckDump(&lrt, kTableInitial + 1, 1);

  for (size_t i = 0; i < kTableInitial; i++) {
    ASSERT_TRUE(lrt.Remove(manyRefs[i])) << "failed removing " << i;
    CheckDump(&lrt, kTableInitial - i, 1);
  }
  // Because of removal order, should have 11 entries, 10 of them holes.
  ASSERT_EQ(kTableInitial + 1, lrt.Capacity());

  ASSERT_TRUE(lrt.Remove(iref0)) << "multi-remove final failed";

  ASSERT_EQ(0U, lrt.Capacity()) << "multi-del not empty";
  CheckDump(&lrt, 0, 0);
}

TEST_F(LocalReferenceTableTest, BasicTest) {
  BasicTest(/*check_jni=*/ false, /*max_count=*/ 20u);
  BasicTest(/*check_jni=*/ false, /*max_count=*/ kSmallLrtEntries);
  BasicTest(/*check_jni=*/ false, /*max_count=*/ 2u * kSmallLrtEntries);
}

TEST_F(LocalReferenceTableTest, BasicTestCheckJNI) {
  BasicTest(/*check_jni=*/ true, /*max_count=*/ 20u);
  BasicTest(/*check_jni=*/ true, /*max_count=*/ kSmallLrtEntries);
  BasicTest(/*check_jni=*/ true, /*max_count=*/ 2u * kSmallLrtEntries);
}

void LocalReferenceTableTest::BasicHolesTest(bool check_jni, size_t max_count) {
  // Test the explicitly named cases from the LRT implementation:
  //
  // 1) Segment with holes (current_num_holes_ > 0), push new segment, add/remove reference
  // 2) Segment with holes (current_num_holes_ > 0), pop segment, add/remove reference
  // 3) Segment with holes (current_num_holes_ > 0), push new segment, pop segment, add/remove
  //    reference
  // 4) Empty segment, push new segment, create a hole, pop a segment, add/remove a reference
  // 5) Base segment, push new segment, create a hole, pop a segment, push new segment, add/remove
  //    reference

  ScopedObjectAccess soa(Thread::Current());
  StackHandleScope<6> hs(soa.Self());
  Handle<mirror::Class> c = hs.NewHandle(GetClassRoot<mirror::Object>());
  ASSERT_TRUE(c != nullptr);
  Handle<mirror::Object> obj0 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj0 != nullptr);
  Handle<mirror::Object> obj1 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj1 != nullptr);
  Handle<mirror::Object> obj2 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj2 != nullptr);
  Handle<mirror::Object> obj3 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj3 != nullptr);
  Handle<mirror::Object> obj4 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj4 != nullptr);

  std::string error_msg;

  // 1) Segment with holes (current_num_holes_ > 0), push new segment, add/remove reference.
  {
    LocalReferenceTable lrt(check_jni);
    bool success = lrt.Initialize(max_count, &error_msg);
    ASSERT_TRUE(success) << error_msg;

    CheckDump(&lrt, 0, 0);

    IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);
    IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref2 = lrt.Add(obj2.Get(), &error_msg);

    EXPECT_TRUE(lrt.Remove(iref1));
    EXPECT_EQ(lrt.Capacity(), 3u);

    // New segment.
    const LRTSegmentState cookie = lrt.PushFrame();

    IndirectRef iref3 = lrt.Add(obj3.Get(), &error_msg);

    // Must not have filled the previous hole.
    EXPECT_EQ(lrt.Capacity(), 4u);
    EXPECT_FALSE(lrt.IsValidReference(iref1, &error_msg));
    CheckDump(&lrt, 3, 3);

    lrt.PopFrame(cookie);
    EXPECT_EQ(lrt.Capacity(), 3u);

    UNUSED(iref0, iref1, iref2, iref3);
  }

  // 2) Segment with holes (current_num_holes_ > 0), pop segment, add/remove reference
  {
    LocalReferenceTable lrt(check_jni);
    bool success = lrt.Initialize(max_count, &error_msg);
    ASSERT_TRUE(success) << error_msg;

    CheckDump(&lrt, 0, 0);

    IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);

    // New segment.
    const LRTSegmentState cookie = lrt.PushFrame();

    IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref2 = lrt.Add(obj2.Get(), &error_msg);
    IndirectRef iref3 = lrt.Add(obj3.Get(), &error_msg);

    EXPECT_TRUE(lrt.Remove(iref2));

    // Pop segment.
    lrt.PopFrame(cookie);

    IndirectRef iref4 = lrt.Add(obj4.Get(), &error_msg);

    EXPECT_EQ(lrt.Capacity(), 2u);
    EXPECT_FALSE(lrt.IsValidReference(iref2, &error_msg));
    CheckDump(&lrt, 2, 2);

    UNUSED(iref0, iref1, iref2, iref3, iref4);
  }

  // 3) Segment with holes (current_num_holes_ > 0), push new segment, pop segment, add/remove
  //    reference.
  {
    LocalReferenceTable lrt(check_jni);
    bool success = lrt.Initialize(max_count, &error_msg);
    ASSERT_TRUE(success) << error_msg;

    CheckDump(&lrt, 0, 0);

    IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);

    // New segment.
    const LRTSegmentState cookie0 = lrt.PushFrame();

    IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref2 = lrt.Add(obj2.Get(), &error_msg);

    EXPECT_TRUE(lrt.Remove(iref1));

    // New segment.
    const LRTSegmentState cookie1 = lrt.PushFrame();

    IndirectRef iref3 = lrt.Add(obj3.Get(), &error_msg);

    // Pop segment.
    lrt.PopFrame(cookie1);

    IndirectRef iref4 = lrt.Add(obj4.Get(), &error_msg);

    EXPECT_EQ(lrt.Capacity(), 3u);
    if (check_jni) {
      EXPECT_FALSE(lrt.IsValidReference(iref1, &error_msg));
    }
    CheckDump(&lrt, 3, 3);

    lrt.PopFrame(cookie0);
    CheckDump(&lrt, 1, 1);

    UNUSED(iref0, iref1, iref2, iref3, iref4);
  }

  // 4) Empty segment, push new segment, create a hole, pop a segment, add/remove a reference.
  {
    LocalReferenceTable lrt(check_jni);
    bool success = lrt.Initialize(max_count, &error_msg);
    ASSERT_TRUE(success) << error_msg;

    CheckDump(&lrt, 0, 0);

    IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);

    // New segment.
    const LRTSegmentState cookie0 = lrt.PushFrame();

    IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
    EXPECT_TRUE(lrt.Remove(iref1));

    // Emptied segment, push new one.
    const LRTSegmentState cookie1 = lrt.PushFrame();

    IndirectRef iref2 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref3 = lrt.Add(obj2.Get(), &error_msg);
    IndirectRef iref4 = lrt.Add(obj3.Get(), &error_msg);

    EXPECT_TRUE(lrt.Remove(iref3));

    // Pop segment.
    lrt.PopFrame(cookie1);

    IndirectRef iref5 = lrt.Add(obj4.Get(), &error_msg);

    EXPECT_EQ(lrt.Capacity(), 2u);
    EXPECT_FALSE(lrt.IsValidReference(iref3, &error_msg));
    CheckDump(&lrt, 2, 2);

    // Pop segment.
    lrt.PopFrame(cookie0);
    CheckDump(&lrt, 1, 1);

    UNUSED(iref0, iref1, iref2, iref3, iref4, iref5);
  }

  // 5) Base segment, push new segment, create a hole, pop a segment, push new segment, add/remove
  //    reference
  {
    LocalReferenceTable lrt(check_jni);
    bool success = lrt.Initialize(max_count, &error_msg);
    ASSERT_TRUE(success) << error_msg;

    CheckDump(&lrt, 0, 0);

    IndirectRef iref0 = lrt.Add(obj0.Get(), &error_msg);

    // New segment.
    const LRTSegmentState cookie0 = lrt.PushFrame();

    IndirectRef iref1 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref2 = lrt.Add(obj1.Get(), &error_msg);
    IndirectRef iref3 = lrt.Add(obj2.Get(), &error_msg);

    EXPECT_TRUE(lrt.Remove(iref2));

    // Pop segment.
    lrt.PopFrame(cookie0);

    // Push segment.
    const LRTSegmentState cookie0_second = lrt.PushFrame();
    EXPECT_EQ(cookie0.top_index, cookie0_second.top_index);

    IndirectRef iref4 = lrt.Add(obj3.Get(), &error_msg);

    EXPECT_EQ(lrt.Capacity(), 2u);
    EXPECT_FALSE(lrt.IsValidReference(iref3, &error_msg));
    CheckDump(&lrt, 2, 2);

    UNUSED(iref0, iref1, iref2, iref3, iref4);
  }
}

TEST_F(LocalReferenceTableTest, BasicHolesTest) {
  BasicHolesTest(/*check_jni=*/ false, 20u);
  BasicHolesTest(/*check_jni=*/ false, /*max_count=*/ kSmallLrtEntries);
  BasicHolesTest(/*check_jni=*/ false, /*max_count=*/ 2u * kSmallLrtEntries);
}

TEST_F(LocalReferenceTableTest, BasicHolesTestCheckJNI) {
  BasicHolesTest(/*check_jni=*/ true, 20u);
  BasicHolesTest(/*check_jni=*/ true, /*max_count=*/ kSmallLrtEntries);
  BasicHolesTest(/*check_jni=*/ true, /*max_count=*/ 2u * kSmallLrtEntries);
}

void LocalReferenceTableTest::BasicResizeTest(bool check_jni, size_t max_count) {
  ScopedObjectAccess soa(Thread::Current());
  StackHandleScope<2> hs(soa.Self());
  Handle<mirror::Class> c = hs.NewHandle(GetClassRoot<mirror::Object>());
  ASSERT_TRUE(c != nullptr);
  Handle<mirror::Object> obj0 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj0 != nullptr);

  std::string error_msg;
  LocalReferenceTable lrt(check_jni);
  bool success = lrt.Initialize(max_count, &error_msg);
  ASSERT_TRUE(success) << error_msg;

  CheckDump(&lrt, 0, 0);

  for (size_t i = 0; i != max_count + 1; ++i) {
    lrt.Add(obj0.Get(), &error_msg);
  }

  EXPECT_EQ(lrt.Capacity(), max_count + 1);
}

TEST_F(LocalReferenceTableTest, BasicResizeTest) {
  BasicResizeTest(/*check_jni=*/ false, 20u);
  BasicResizeTest(/*check_jni=*/ false, /*max_count=*/ kSmallLrtEntries);
  BasicResizeTest(/*check_jni=*/ false, /*max_count=*/ 2u * kSmallLrtEntries);
  BasicResizeTest(/*check_jni=*/ false, /*max_count=*/ gPageSize / sizeof(LrtEntry));
}

TEST_F(LocalReferenceTableTest, BasicResizeTestCheckJNI) {
  BasicResizeTest(/*check_jni=*/ true, 20u);
  BasicResizeTest(/*check_jni=*/ true, /*max_count=*/ kSmallLrtEntries);
  BasicResizeTest(/*check_jni=*/ true, /*max_count=*/ 2u * kSmallLrtEntries);
  BasicResizeTest(/*check_jni=*/ true, /*max_count=*/ gPageSize / sizeof(LrtEntry));
}

void LocalReferenceTableTest::TestAddRemove(bool check_jni, size_t max_count, size_t fill_count) {
  // This will lead to error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);

  ScopedObjectAccess soa(Thread::Current());
  StackHandleScope<9> hs(soa.Self());
  Handle<mirror::Class> c = hs.NewHandle(GetClassRoot<mirror::Object>());
  ASSERT_TRUE(c != nullptr);
  Handle<mirror::Object> obj0 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj0 != nullptr);
  Handle<mirror::Object> obj0x = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj0x != nullptr);
  Handle<mirror::Object> obj1 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj1 != nullptr);
  Handle<mirror::Object> obj1x = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj1x != nullptr);
  Handle<mirror::Object> obj2 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj2 != nullptr);
  Handle<mirror::Object> obj2x = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj2x != nullptr);
  Handle<mirror::Object> obj3 = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj3 != nullptr);
  Handle<mirror::Object> obj3x = hs.NewHandle(c->AllocObject(soa.Self()));
  ASSERT_TRUE(obj3x != nullptr);

  std::string error_msg;
  LocalReferenceTable lrt(check_jni);
  bool success = lrt.Initialize(max_count, &error_msg);
  ASSERT_TRUE(success) << error_msg;

  for (size_t i = 0; i != fill_count; ++i) {
    IndirectRef iref = lrt.Add(c.Get(), &error_msg);
    ASSERT_TRUE(iref != nullptr) << error_msg;
    ASSERT_EQ(i + 1u, lrt.Capacity());
    EXPECT_OBJ_PTR_EQ(c.Get(), lrt.Get(iref));
  }

  IndirectRef iref0, iref1, iref2, iref3;

#define ADD_REF(iref, obj, expected_capacity)                     \
  do {                                                            \
    (iref) = lrt.Add((obj).Get(), &error_msg);                    \
    ASSERT_TRUE((iref) != nullptr) << error_msg;                  \
    ASSERT_EQ(fill_count + (expected_capacity), lrt.Capacity());  \
    EXPECT_OBJ_PTR_EQ((obj).Get(), lrt.Get(iref));                \
  } while (false)
#define REMOVE_REF(iref, expected_capacity)                       \
  do {                                                            \
    ASSERT_TRUE(lrt.Remove(iref));                                \
    ASSERT_EQ(fill_count + (expected_capacity), lrt.Capacity());  \
  } while (false)
#define POP_SEGMENT(cookie, expected_capacity)                    \
  do {                                                            \
    lrt.PopFrame(cookie);                                         \
    ASSERT_EQ(fill_count + (expected_capacity), lrt.Capacity());  \
  } while (false)

  const LRTSegmentState cookie0 = lrt.PushFrame();
  ADD_REF(iref0, obj0, 1u);
  ADD_REF(iref1, obj1, 2u);
  REMOVE_REF(iref1, 1u);  // Remove top entry.
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(iref1));
  }
  ADD_REF(iref1, obj1x, 2u);
  REMOVE_REF(iref0, 2u);  // Create hole.
  IndirectRef obsolete_iref0 = iref0;
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(iref0));
  }
  ADD_REF(iref0, obj0x, 2u);  // Reuse hole
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(obsolete_iref0));
  }

  // Test addition to the second segment without a hole in the first segment.
  // Also test removal from the wrong segment here.
  LRTSegmentState cookie1 = lrt.PushFrame();  // Create second segment.
  ASSERT_FALSE(lrt.Remove(iref0));  // Cannot remove from inactive segment.
  ADD_REF(iref2, obj2, 3u);
  POP_SEGMENT(cookie1, 2u);  // Pop the second segment.
  if (check_jni) {
    ASSERT_FALSE(lrt.Remove(iref2));  // Cannot remove from popped segment.
  }

  // Test addition to the second segment with a hole in the first.
  // Use one more reference in the first segment to allow hitting the small table
  // overflow path either above or here, based on the provided `fill_count`.
  ADD_REF(iref2, obj2x, 3u);
  REMOVE_REF(iref1, 3u);  // Create hole.
  cookie1 = lrt.PushFrame();  // Create second segment.
  ADD_REF(iref3,  obj3, 4u);
  POP_SEGMENT(cookie1, 3u);  // Pop the second segment.
  REMOVE_REF(iref2, 1u);  // Remove top entry, prune previous entry.
  ADD_REF(iref1, obj1, 2u);

  cookie1 = lrt.PushFrame();  // Create second segment.
  ADD_REF(iref2, obj2, 3u);
  ADD_REF(iref3, obj3, 4u);
  REMOVE_REF(iref2, 4u);  // Create hole in second segment.
  POP_SEGMENT(cookie1, 2u);  // Pop the second segment with hole.
  ADD_REF(iref2, obj2x, 3u);  // Prune free list, use new entry.
  REMOVE_REF(iref2, 2u);

  REMOVE_REF(iref0, 2u);  // Create hole.
  cookie1 = lrt.PushFrame();  // Create second segment.
  ADD_REF(iref2, obj2, 3u);
  ADD_REF(iref3, obj3x, 4u);
  REMOVE_REF(iref2, 4u);  // Create hole in second segment.
  POP_SEGMENT(cookie1, 2u);  // Pop the second segment with hole.
  ADD_REF(iref0, obj0, 2u);  // Prune free list, use remaining entry from free list.

  REMOVE_REF(iref0, 2u);  // Create hole.
  cookie1 = lrt.PushFrame();  // Create second segment.
  ADD_REF(iref2, obj2x, 3u);
  ADD_REF(iref3, obj3, 4u);
  REMOVE_REF(iref2, 4u);  // Create hole in second segment.
  REMOVE_REF(iref3, 2u);  // Remove top entry, prune previous entry, keep hole above.
  POP_SEGMENT(cookie1, 2u);  // Pop the empty second segment.
  ADD_REF(iref0, obj0x, 2u);  // Reuse hole.

  POP_SEGMENT(cookie0, 0u);  // Pop the first segment.

#undef REMOVE_REF
#undef ADD_REF
}

TEST_F(LocalReferenceTableTest, TestAddRemove) {
  TestAddRemove(/*check_jni=*/ false, /*max_count=*/ 20u);
  TestAddRemove(/*check_jni=*/ false, /*max_count=*/ kSmallLrtEntries);
  TestAddRemove(/*check_jni=*/ false, /*max_count=*/ 2u * kSmallLrtEntries);
  static_assert(kSmallLrtEntries >= 4u);
  for (size_t fill_count = kSmallLrtEntries - 4u; fill_count != kSmallLrtEntries; ++fill_count) {
    TestAddRemove(/*check_jni=*/ false, /*max_count=*/ kSmallLrtEntries, fill_count);
  }
}

TEST_F(LocalReferenceTableTest, TestAddRemoveCheckJNI) {
  TestAddRemove(/*check_jni=*/ true, /*max_count=*/ 20u);
  TestAddRemove(/*check_jni=*/ true, /*max_count=*/ kSmallLrtEntries);
  TestAddRemove(/*check_jni=*/ true, /*max_count=*/ 2u * kSmallLrtEntries);
  static_assert(kSmallLrtEntries >= 4u);
  for (size_t fill_count = kSmallLrtEntries - 4u; fill_count != kSmallLrtEntries; ++fill_count) {
    TestAddRemove(/*check_jni=*/ true, /*max_count=*/ kSmallLrtEntries, fill_count);
  }
}

void LocalReferenceTableTest::TestAddRemoveMixed(bool start_check_jni) {
  // This will lead to error messages in the log.
  ScopedLogSeverity sls(LogSeverity::FATAL);

  ScopedObjectAccess soa(Thread::Current());
  static constexpr size_t kMaxUniqueRefs = 16;
  StackHandleScope<kMaxUniqueRefs + 1u> hs(soa.Self());
  Handle<mirror::Class> c = hs.NewHandle(GetClassRoot<mirror::Object>());
  ASSERT_TRUE(c != nullptr);
  std::array<Handle<mirror::Object>, kMaxUniqueRefs> objs;
  for (size_t i = 0u; i != kMaxUniqueRefs; ++i) {
    objs[i] = hs.NewHandle(c->AllocObject(soa.Self()));
    ASSERT_TRUE(objs[i] != nullptr);
  }

  std::string error_msg;
  std::array<IndirectRef, kMaxUniqueRefs> irefs;

#define ADD_REF(iref, obj)                                        \
  do {                                                            \
    (iref) = lrt.Add((obj).Get(), &error_msg);                    \
    ASSERT_TRUE((iref) != nullptr) << error_msg;                  \
    EXPECT_OBJ_PTR_EQ((obj).Get(), lrt.Get(iref));                \
  } while (false)

  for (size_t split = 1u; split < kMaxUniqueRefs - 1u; ++split) {
    for (size_t total = split + 1u; total < kMaxUniqueRefs; ++total) {
      for (size_t deleted_at_start = 0u; deleted_at_start + 1u < split; ++deleted_at_start) {
        LocalReferenceTable lrt(/*check_jni=*/ start_check_jni);
        bool success = lrt.Initialize(kSmallLrtEntries, &error_msg);
        ASSERT_TRUE(success) << error_msg;
        for (size_t i = 0; i != split; ++i) {
          ADD_REF(irefs[i], objs[i]);
          ASSERT_EQ(i + 1u, lrt.Capacity());
        }
        for (size_t i = 0; i != deleted_at_start; ++i) {
          ASSERT_TRUE(lrt.Remove(irefs[i]));
          if (lrt.IsCheckJniEnabled()) {
            ASSERT_FALSE(lrt.Remove(irefs[i]));
          }
          ASSERT_EQ(split, lrt.Capacity());
        }
        lrt.SetCheckJniEnabled(!start_check_jni);
        // Check top index instead of `Capacity()` after changing the CheckJNI setting.
        auto get_segment_state = [&lrt]() {
          LRTSegmentState cookie0 = lrt.PushFrame();
          LRTSegmentState cookie1 = lrt.PushFrame();
          uint32_t result = cookie1.top_index;
          lrt.PopFrame(cookie1);
          lrt.PopFrame(cookie0);
          return result;
        };
        uint32_t split_top_index = get_segment_state();
        uint32_t last_top_index = split_top_index;
        for (size_t i = split; i != total; ++i) {
          ADD_REF(irefs[i], objs[i]);
          ASSERT_LT(last_top_index, get_segment_state());
          last_top_index = get_segment_state();
        }
        for (size_t i = split; i != total; ++i) {
          ASSERT_TRUE(lrt.Remove(irefs[i]));
          if (lrt.IsCheckJniEnabled()) {
            ASSERT_FALSE(lrt.Remove(irefs[i]));
          }
          if (i + 1u != total) {
            ASSERT_LE(last_top_index, get_segment_state());
          } else {
            ASSERT_GT(last_top_index, get_segment_state());
            ASSERT_LE(split_top_index, get_segment_state());
          }
        }
      }
    }
  }

#undef ADD_REF
}

TEST_F(LocalReferenceTableTest, TestAddRemoveMixed) {
  TestAddRemoveMixed(/*start_check_jni=*/ false);
  TestAddRemoveMixed(/*start_check_jni=*/ true);
}

TEST_F(LocalReferenceTableTest, RegressionTestB276210372) {
  LocalReferenceTable lrt(/*check_jni=*/ false);
  std::string error_msg;
  bool success = lrt.Initialize(kSmallLrtEntries, &error_msg);
  ASSERT_TRUE(success) << error_msg;
  ScopedObjectAccess soa(Thread::Current());
  ObjPtr<mirror::Class> c = GetClassRoot<mirror::Object>();

  auto get_previous_state = [&lrt]() {
    LRTSegmentState previous_state = lrt.PushFrame();
    lrt.PopFrame(previous_state);
    return previous_state;
  };

  // Create the first segment with two references.
  IndirectRef ref0 = lrt.Add(c, &error_msg);
  ASSERT_TRUE(ref0 != nullptr);
  IndirectRef ref1 = lrt.Add(c, &error_msg);
  ASSERT_TRUE(ref1 != nullptr);

  // Create a second segment with a hole, then pop it.
  const LRTSegmentState cookie0A = lrt.PushFrame();
  const LRTSegmentState previous_state_A = get_previous_state();
  IndirectRef ref2a = lrt.Add(c, &error_msg);
  ASSERT_TRUE(ref2a != nullptr);
  IndirectRef ref3a = lrt.Add(c, &error_msg);
  ASSERT_TRUE(ref3a != nullptr);
  EXPECT_TRUE(lrt.Remove(ref2a));
  lrt.PopFrame(cookie0A);

  // Create a hole in the first segment.
  // There was previously a bug that `Remove()` would not prune the popped free entries,
  // so the new free entry would point to the hole in the popped segment. The code below
  // would then overwrite that hole with a new segment, pop that segment, reuse the good
  // free entry and then crash trying to prune the overwritten hole. b/276210372
  EXPECT_TRUE(lrt.Remove(ref0));

  // Create a second segment again and overwite the old hole, then pop the segment.
  const LRTSegmentState cookie0B = lrt.PushFrame();
  const LRTSegmentState previous_state_B = get_previous_state();
  ASSERT_EQ(cookie0B.top_index, cookie0A.top_index);
  ASSERT_EQ(previous_state_B.top_index, previous_state_A.top_index);
  IndirectRef ref2b = lrt.Add(c, &error_msg);
  ASSERT_TRUE(ref2b != nullptr);
  lrt.PopFrame(cookie0B);

  // Reuse the hole in first segment.
  IndirectRef reused0 = lrt.Add(c, &error_msg);
  ASSERT_TRUE(reused0 != nullptr);

  // Add a new reference.
  IndirectRef new_ref = lrt.Add(c, &error_msg);
  ASSERT_TRUE(new_ref != nullptr);
}

TEST_F(LocalReferenceTableTest, RegressionTestB276864369) {
  LocalReferenceTable lrt(/*check_jni=*/ false);
  std::string error_msg;
  bool success = lrt.Initialize(kSmallLrtEntries, &error_msg);
  ASSERT_TRUE(success) << error_msg;
  ScopedObjectAccess soa(Thread::Current());
  ObjPtr<mirror::Class> c = GetClassRoot<mirror::Object>();

  // Add refs to fill all small tables and one bigger table.
  const size_t refs_per_page = gPageSize / sizeof(LrtEntry);
  std::vector<IndirectRef> refs;
  for (size_t i = 0; i != 2 * refs_per_page; ++i) {
    refs.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs.back() != nullptr);
  }

  // We had a bug in `Trim()` where we would try to skip one more table than available
  // if the capacity was exactly at the end of table. If the next table was not allocated,
  // we would hit a `DCHECK()` in `dchecked_vector<>` in debug mode but in release
  // mode we would proceed to use memory outside the allocated chunk. b/276864369
  lrt.Trim();
}

TEST_F(LocalReferenceTableTest, Trim) {
  LocalReferenceTable lrt(/*check_jni=*/ false);
  std::string error_msg;
  bool success = lrt.Initialize(kSmallLrtEntries, &error_msg);
  ASSERT_TRUE(success) << error_msg;
  ScopedObjectAccess soa(Thread::Current());
  ObjPtr<mirror::Class> c = GetClassRoot<mirror::Object>();

  // Add refs to fill all small tables.
  LRTSegmentState cookie0 = lrt.PushFrame();
  const size_t refs_per_page = gPageSize / sizeof(LrtEntry);
  std::vector<IndirectRef> refs0;
  for (size_t i = 0; i != refs_per_page; ++i) {
    refs0.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs0.back() != nullptr);
  }

  // Nothing to trim.
  lrt.Trim();
  ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs0.back())->IsNull());

  // Add refs to fill the next, page-sized table.
  std::vector<IndirectRef> refs1;
  LRTSegmentState cookie1 = lrt.PushFrame();
  for (size_t i = 0; i != refs_per_page; ++i) {
    refs1.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs1.back() != nullptr);
  }

  // Nothing to trim.
  lrt.Trim();
  ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs1.back())->IsNull());

  // Pop one reference and try to trim, there is no page to trim.
  ASSERT_TRUE(lrt.Remove(refs1.back()));
  lrt.Trim();
  ASSERT_FALSE(
      IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs1[refs1.size() - 2u])->IsNull());

  // Pop the entire segment with the page-sized table and trim, clearing the page.
  lrt.PopFrame(cookie1);
  lrt.Trim();
  for (IndirectRef ref : refs1) {
    ASSERT_TRUE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  refs1.clear();

  // Add refs to fill the page-sized table and half of the next one.
  cookie1 = lrt.PushFrame();  // Push a new segment.
  for (size_t i = 0; i != 2 * refs_per_page; ++i) {
    refs1.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs1.back() != nullptr);
  }

  // Add refs to fill the other half of the table with two pages.
  std::vector<IndirectRef> refs2;
  const LRTSegmentState cookie2 = lrt.PushFrame();
  for (size_t i = 0; i != refs_per_page; ++i) {
    refs2.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs2.back() != nullptr);
  }

  // Nothing to trim.
  lrt.Trim();
  ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs1.back())->IsNull());

  // Pop the last segment with one page worth of references and trim that page.
  lrt.PopFrame(cookie2);
  lrt.Trim();
  for (IndirectRef ref : refs2) {
    ASSERT_TRUE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  refs2.clear();
  for (IndirectRef ref : refs1) {
    ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }

  // Pop the middle segment with two pages worth of references, and trim those pages.
  lrt.PopFrame(cookie1);
  lrt.Trim();
  for (IndirectRef ref : refs1) {
    ASSERT_TRUE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  refs1.clear();

  // Pop the first segment with small tables and try to trim. Small tables are never trimmed.
  lrt.PopFrame(cookie0);
  lrt.Trim();
  for (IndirectRef ref : refs0) {
    ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  refs0.clear();

  // Fill small tables and one more reference, then another segment up to 4 pages.
  LRTSegmentState cookie0_second = lrt.PushFrame();
  ASSERT_EQ(cookie0.top_index, cookie0_second.top_index);
  for (size_t i = 0; i != refs_per_page + 1u; ++i) {
    refs0.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs0.back() != nullptr);
  }
  cookie1 = lrt.PushFrame();  // Push a new segment.
  for (size_t i = 0; i != 3u * refs_per_page - 1u; ++i) {
    refs1.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs1.back() != nullptr);
  }

  // Nothing to trim.
  lrt.Trim();
  ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs1.back())->IsNull());

  // Pop the middle segment, trim two pages.
  lrt.PopFrame(cookie1);
  lrt.Trim();
  for (IndirectRef ref : refs0) {
    ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  ASSERT_EQ(refs0.size(), lrt.Capacity());
  for (IndirectRef ref : ArrayRef<IndirectRef>(refs1).SubArray(0u, refs_per_page - 1u)) {
    // Popped but not trimmed as these are at the same page as the last entry in `refs0`.
    ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  for (IndirectRef ref : ArrayRef<IndirectRef>(refs1).SubArray(refs_per_page - 1u)) {
    ASSERT_TRUE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
}

TEST_F(LocalReferenceTableTest, PruneBeforeTrim) {
  LocalReferenceTable lrt(/*check_jni=*/ false);
  std::string error_msg;
  bool success = lrt.Initialize(kSmallLrtEntries, &error_msg);
  ASSERT_TRUE(success) << error_msg;
  ScopedObjectAccess soa(Thread::Current());
  ObjPtr<mirror::Class> c = GetClassRoot<mirror::Object>();

  // Add refs to fill all small tables and one bigger table.
  const LRTSegmentState cookie0 = lrt.PushFrame();
  const size_t refs_per_page = gPageSize / sizeof(LrtEntry);
  std::vector<IndirectRef> refs;
  for (size_t i = 0; i != 2 * refs_per_page; ++i) {
    refs.push_back(lrt.Add(c, &error_msg));
    ASSERT_TRUE(refs.back() != nullptr);
  }

  // Nothing to trim.
  lrt.Trim();
  ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(refs.back())->IsNull());

  // Create a hole in the last page.
  IndirectRef removed = refs[refs.size() - 2u];
  ASSERT_TRUE(lrt.Remove(removed));

  // Pop the entire segment and trim. Small tables are not pruned.
  lrt.PopFrame(cookie0);
  lrt.Trim();
  for (IndirectRef ref : ArrayRef<IndirectRef>(refs).SubArray(0u, refs_per_page)) {
    ASSERT_FALSE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }
  for (IndirectRef ref : ArrayRef<IndirectRef>(refs).SubArray(refs_per_page)) {
    ASSERT_TRUE(IndirectReferenceTable::ClearIndirectRefKind<LrtEntry*>(ref)->IsNull());
  }

  // Add a new reference and check that it reused the first slot rather than the old hole.
  IndirectRef new_ref = lrt.Add(c, &error_msg);
  ASSERT_TRUE(new_ref != nullptr);
  ASSERT_NE(new_ref, removed);
  ASSERT_EQ(new_ref, refs[0]);
}

}  // namespace jni
}  // namespace art
