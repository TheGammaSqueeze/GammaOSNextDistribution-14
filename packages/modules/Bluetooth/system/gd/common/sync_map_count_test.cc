/*
 * Copyright 2021 The Android Open Source Project
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

#include "common/sync_map_count.h"

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include <cstddef>
#include <cstring>
#include <vector>

#include "os/log.h"

namespace testing {

const char* data[] = {
    "One",
    "Two",
    "Two",
    "Three",
    "Three",
    "Three",
    "AAA",
    "ZZZ",
    nullptr,
};

namespace {
void LoadStringMap(SyncMapCount<std::string>& map) {
  for (const char** p = data; *p != nullptr; p++) {
    map.Put(*p);
  }
}
}  // namespace

TEST(SyncMapCount, simple) {
  SyncMapCount<std::string> map;
  LoadStringMap(map);

  ASSERT_EQ(5ul, map.Size());

  auto m = map.Get();
  ASSERT_EQ(3ul, m["Three"]);
  ASSERT_EQ(2ul, m["Two"]);
  ASSERT_EQ(1ul, m["One"]);
}

TEST(SyncMapCount, sized) {
  SyncMapCount<std::string> map(2);
  LoadStringMap(map);

  ASSERT_EQ(2ul, map.Size());
}

TEST(SyncMapCount, sorted_string_value_low_to_high) {
  SyncMapCount<std::string> map;
  LoadStringMap(map);

  auto entries = map.GetSortedLowToHigh();
  ASSERT_EQ(3ul, entries[entries.size() - 1].count);
  ASSERT_EQ(2ul, entries[entries.size() - 2].count);
}

TEST(SyncMapCount, sorted_string_value_high_to_low) {
  SyncMapCount<std::string> map;
  LoadStringMap(map);

  auto entries = map.GetSortedHighToLow();
  ASSERT_EQ(3ul, entries[0].count);
  ASSERT_EQ(2ul, entries[1].count);
}

struct TestString {
  TestString(std::string string) : string_(string) {}
  std::string String() const {
    return string_;
  }

  bool operator<(const TestString& other) const {
    return (other.string_ > string_);
  }
  bool operator==(const TestString& other) const {
    return (other.string_ == string_);
  }

 private:
  std::string string_;
};

namespace {
void LoadTestStringMap(SyncMapCount<TestString>& map) {
  for (const char** p = data; *p != nullptr; p++) {
    map.Put(TestString(*p));
  }
}
}  // namespace

TEST(SyncMapCount, simple_struct) {
  SyncMapCount<TestString> map;
  LoadTestStringMap(map);

  ASSERT_EQ(5ul, map.Size());

  auto m = map.Get();
  ASSERT_EQ(3ul, m[TestString("Three")]);
  ASSERT_EQ(2ul, m[TestString("Two")]);
  ASSERT_EQ(1ul, m[TestString("One")]);
}

TEST(SyncMapCount, sorted_string_struct_value_low_to_high) {
  SyncMapCount<TestString> map;
  LoadTestStringMap(map);

  auto entries = map.GetSortedLowToHigh();
  ASSERT_EQ(3ul, entries[entries.size() - 1].count);
  ASSERT_EQ(2ul, entries[entries.size() - 2].count);
}

TEST(SyncMapCount, sorted_string_struct_value_high_to_low) {
  SyncMapCount<TestString> map;
  LoadTestStringMap(map);

  auto entries = map.GetSortedHighToLow();
  ASSERT_EQ(3ul, entries[0].count);
  ASSERT_EQ(2ul, entries[1].count);
}

TEST(SyncMapCount, locked_for_map_copy) {
  SyncMapCount<TestString> map;
  LoadTestStringMap(map);

  ASSERT_EQ(5ul, map.Size());
  std::vector<SyncMapCount<TestString>::Item> vec;
  for (auto& it : map.Get()) {
    map.Clear();
    vec.push_back(SyncMapCount<TestString>::Item{it.first, it.second});
  }
  ASSERT_EQ(0ul, map.Size());
  ASSERT_EQ(5ul, vec.size());
}

}  // namespace testing
