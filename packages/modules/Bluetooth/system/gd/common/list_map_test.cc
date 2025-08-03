/*
 * Copyright 2020 The Android Open Source Project
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

#include <memory>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "common/list_map.h"

namespace testing {

using bluetooth::common::ListMap;

TEST(ListMapTest, empty_test) {
  ListMap<int, int> list_map;
  EXPECT_EQ(list_map.size(), 0ul);
  EXPECT_EQ(list_map.find(42), list_map.end());
  list_map.clear();  // should not crash
  EXPECT_EQ(list_map.find(42), list_map.end());
  EXPECT_FALSE(list_map.contains(42));
  EXPECT_FALSE(list_map.extract(42));
}

TEST(ListMapTest, comparison_test) {
  ListMap<int, int> list_map_1;
  list_map_1.insert_or_assign(1, 10);
  list_map_1.insert_or_assign(2, 20);
  ListMap<int, int> list_map_2;
  list_map_2.insert_or_assign(1, 10);
  list_map_2.insert_or_assign(2, 20);
  EXPECT_EQ(list_map_1, list_map_2);
  // List map with different value should be different
  list_map_2.insert_or_assign(1, 11);
  EXPECT_NE(list_map_1, list_map_2);
  // List maps with different order should not be equal
  ListMap<int, int> list_map_3;
  list_map_3.insert_or_assign(2, 20);
  list_map_3.insert_or_assign(1, 10);
  EXPECT_NE(list_map_1, list_map_3);
  // Empty list map should not be equal to non-empty ones
  ListMap<int, int> list_map_4;
  EXPECT_NE(list_map_1, list_map_4);
  // Empty list maps should be equal
  ListMap<int, int> list_map_5;
  EXPECT_EQ(list_map_4, list_map_5);
}

TEST(ListMapTest, copy_test) {
  ListMap<int, std::shared_ptr<int>> list_map;
  list_map.insert_or_assign(1, std::make_shared<int>(100));
  auto iter = list_map.find(1);
  EXPECT_EQ(*iter->second, 100);
  ListMap<int, std::shared_ptr<int>> new_list_map = list_map;
  iter = new_list_map.find(1);
  EXPECT_EQ(*iter->second, 100);
  *iter->second = 300;
  iter = new_list_map.find(1);
  EXPECT_EQ(*iter->second, 300);
  // Since copy is used, shared_ptr should increase count
  EXPECT_EQ(iter->second.use_count(), 2);
}

TEST(ListMapTest, move_test) {
  ListMap<int, std::shared_ptr<int>> list_map;
  list_map.insert_or_assign(1, std::make_shared<int>(100));
  auto iter = list_map.find(1);
  EXPECT_EQ(*iter->second, 100);
  ListMap<int, std::shared_ptr<int>> new_list_map = std::move(list_map);
  iter = new_list_map.find(1);
  EXPECT_EQ(*iter->second, 100);
  *iter->second = 300;
  iter = new_list_map.find(1);
  EXPECT_EQ(*iter->second, 300);
  // Since move is used, shared_ptr should not increase count
  EXPECT_EQ(iter->second.use_count(), 1);
}

TEST(ListMapTest, move_insert_unique_ptr_test) {
  ListMap<int, std::unique_ptr<int>> list_map;
  list_map.insert_or_assign(1, std::make_unique<int>(100));
  auto iter = list_map.find(1);
  EXPECT_EQ(*iter->second, 100);
  list_map.insert_or_assign(1, std::make_unique<int>(400));
  iter = list_map.find(1);
  EXPECT_EQ(*iter->second, 400);
}

TEST(ListMapTest, move_insert_list_map_test) {
  ListMap<int, ListMap<int, int>> list_map;
  ListMap<int, int> m1;
  m1.insert_or_assign(1, 100);
  list_map.insert_or_assign(1, std::move(m1));
  auto iter = list_map.find(1);
  EXPECT_THAT(iter->second, ElementsAre(Pair(1, 100)));
  ListMap<int, int> m2;
  m2.insert_or_assign(2, 200);
  list_map.insert_or_assign(1, std::move(m2));
  iter = list_map.find(1);
  EXPECT_THAT(iter->second, ElementsAre(Pair(2, 200)));
}

TEST(ListMapTest, erase_one_item_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  list_map.insert_or_assign(3, 30);
  auto iter = list_map.find(2);
  iter = list_map.erase(iter);
  EXPECT_EQ(iter->first, 3);
  EXPECT_EQ(iter->second, 30);
}

TEST(ListMapTest, erase_in_for_loop_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  list_map.insert_or_assign(3, 30);
  for (auto iter = list_map.begin(); iter != list_map.end();) {
    if (iter->first == 2) {
      iter = list_map.erase(iter);
    } else {
      ++iter;
    }
  }
  EXPECT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(3, 30)));
}

TEST(ListMapTest, splice_different_list_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  list_map.insert_or_assign(3, 30);
  ListMap<int, int> list_map_2;
  list_map_2.insert_or_assign(4, 40);
  list_map_2.insert_or_assign(5, 50);
  list_map.splice(list_map.find(2), list_map_2, list_map_2.find(4));
  EXPECT_EQ(list_map_2.find(4), list_map_2.end());
  auto iter = list_map.find(4);
  EXPECT_NE(iter, list_map.end());
  EXPECT_EQ(iter->second, 40);
  EXPECT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(4, 40), Pair(2, 20), Pair(3, 30)));
}

TEST(ListMapTest, splice_same_list_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  list_map.insert_or_assign(3, 30);
  list_map.splice(list_map.find(2), list_map, list_map.find(3));
  EXPECT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(3, 30), Pair(2, 20)));
  list_map.extract(2);
  list_map.insert_or_assign(list_map.begin(), 4, 40);
  EXPECT_THAT(list_map, ElementsAre(Pair(4, 40), Pair(1, 10), Pair(3, 30)));
  auto iter = list_map.find(4);
  EXPECT_EQ(iter->second, 40);
  list_map.splice(list_map.begin(), list_map, list_map.find(4));
  list_map.splice(list_map.begin(), list_map, list_map.find(3));
  list_map.splice(list_map.begin(), list_map, list_map.find(1));
  EXPECT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(3, 30), Pair(4, 40)));
  iter = list_map.find(4);
  EXPECT_EQ(iter->second, 40);
  iter = list_map.find(3);
  EXPECT_EQ(iter->second, 30);
}

TEST(ListMapTest, put_get_and_contains_key_test) {
  ListMap<int, int> list_map;
  EXPECT_EQ(list_map.size(), 0ul);
  EXPECT_EQ(list_map.find(42), list_map.end());
  EXPECT_FALSE(list_map.contains(42));
  list_map.insert_or_assign(56, 200);
  EXPECT_EQ(list_map.find(42), list_map.end());
  EXPECT_FALSE(list_map.contains(42));
  auto iter = list_map.find(56);
  EXPECT_NE(iter, list_map.end());
  EXPECT_TRUE(list_map.contains(56));
  EXPECT_EQ(iter->second, 200);
  EXPECT_TRUE(list_map.extract(56));
  EXPECT_FALSE(list_map.contains(56));
}

TEST(ListMapTest, try_emplace_at_position_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  auto iter = list_map.find(2);
  EXPECT_EQ(iter->second, 20);
  auto result = list_map.try_emplace(iter, 42, 420);
  EXPECT_TRUE(result.second);
  iter = list_map.find(42);
  EXPECT_EQ(iter->second, 420);
  EXPECT_EQ(iter, result.first);
  ASSERT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(42, 420), Pair(2, 20)));
  EXPECT_FALSE(list_map.try_emplace(result.first, 42, 420).second);
}

TEST(ListMapTest, try_emplace_back_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  auto result = list_map.try_emplace_back(42, 420);
  EXPECT_TRUE(result.second);
  auto iter = list_map.find(42);
  EXPECT_EQ(iter->second, 420);
  EXPECT_EQ(iter, result.first);
  ASSERT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(2, 20), Pair(42, 420)));
  EXPECT_FALSE(list_map.try_emplace_back(42, 420).second);
}

TEST(ListMapTest, insert_at_position_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  auto iter = list_map.find(2);
  EXPECT_EQ(iter->second, 20);
  list_map.insert_or_assign(iter, 42, 420);
  iter = list_map.find(42);
  EXPECT_EQ(iter->second, 420);
  ASSERT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(42, 420), Pair(2, 20)));
}

TEST(ListMapTest, in_place_modification_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  auto iter = list_map.find(2);
  iter->second = 200;
  ASSERT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(2, 200)));
}

TEST(ListMapTest, get_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  auto iter = list_map.find(1);
  EXPECT_NE(iter, list_map.end());
  EXPECT_EQ(iter->second, 10);
}

TEST(ListMapTest, remove_test) {
  ListMap<int, int> list_map;
  for (int key = 0; key <= 30; key++) {
    list_map.insert_or_assign(key, key * 100);
  }
  for (int key = 0; key <= 30; key++) {
    EXPECT_TRUE(list_map.contains(key));
  }
  for (int key = 0; key <= 30; key++) {
    auto removed = list_map.extract(key);
    EXPECT_TRUE(removed);
    EXPECT_EQ(*removed, std::make_pair(key, key * 100));
  }
  for (int key = 0; key <= 30; key++) {
    EXPECT_FALSE(list_map.contains(key));
  }
}

TEST(ListMapTest, clear_test) {
  ListMap<int, int> list_map;
  for (int key = 0; key < 10; key++) {
    list_map.insert_or_assign(key, key * 100);
  }
  for (int key = 0; key < 10; key++) {
    EXPECT_TRUE(list_map.contains(key));
  }
  list_map.clear();
  for (int key = 0; key < 10; key++) {
    EXPECT_FALSE(list_map.contains(key));
  }

  for (int key = 0; key < 10; key++) {
    list_map.insert_or_assign(key, key * 1000);
  }
  for (int key = 0; key < 10; key++) {
    EXPECT_TRUE(list_map.contains(key));
  }
}

TEST(ListMapTest, container_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  ASSERT_THAT(list_map, ElementsAre(Pair(1, 10), Pair(2, 20)));
}

TEST(ListMapTest, iterator_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  std::list<std::pair<int, int>> list(list_map.begin(), list_map.end());
  ASSERT_THAT(list, ElementsAre(Pair(1, 10), Pair(2, 20)));
}

TEST(ListMapTest, for_loop_test) {
  ListMap<int, int> list_map;
  list_map.insert_or_assign(1, 10);
  list_map.insert_or_assign(2, 20);
  std::list<std::pair<int, int>> list;
  for (const auto& node : list_map) {
    list.emplace_back(node);
  }
  ASSERT_THAT(list, ElementsAre(Pair(1, 10), Pair(2, 20)));
  list.clear();
  for (auto& node : list_map) {
    list.emplace_back(node);
    node.second = node.second * 2;
  }
  ASSERT_THAT(list, ElementsAre(Pair(1, 10), Pair(2, 20)));
  list.clear();
  for (const auto& node : list_map) {
    list.emplace_back(node);
  }
  ASSERT_THAT(list, ElementsAre(Pair(1, 20), Pair(2, 40)));
}

TEST(ListMapTest, pressure_test) {
  int num_entries = 0xFFFF;  // 2^16 = 65535
  ListMap<int, int> list_map;

  // fill the list_map
  for (int key = 0; key < num_entries; key++) {
    list_map.insert_or_assign(key, key);
  }

  // make sure the list_map is full
  for (int key = 0; key < num_entries; key++) {
    EXPECT_TRUE(list_map.contains(key));
  }

  // clear the entire list_map
  for (int key = 0; key < num_entries; key++) {
    auto iter = list_map.find(key);
    EXPECT_NE(iter, list_map.end());
    EXPECT_EQ(iter->second, key);
    EXPECT_TRUE(list_map.extract(key));
  }
  EXPECT_EQ(list_map.size(), 0ul);
}

}  // namespace testing
