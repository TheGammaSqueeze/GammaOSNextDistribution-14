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

#include <limits>

#include <gmock/gmock.h>
#include <gtest/gtest.h>

#include "common/lru_cache.h"

namespace testing {

using bluetooth::common::LruCache;

TEST(LruCacheTest, empty_test) {
  LruCache<int, int> cache(3);  // capacity = 3;
  EXPECT_EQ(cache.size(), 0ul);
  EXPECT_EQ(cache.find(42), cache.end());
  cache.clear();  // should not crash
  EXPECT_EQ(cache.find(42), cache.end());
  EXPECT_FALSE(cache.contains(42));
  EXPECT_FALSE(cache.extract(42));
}

TEST(LruCacheTest, comparison_test) {
  LruCache<int, int> cache_1(2);
  cache_1.insert_or_assign(1, 10);
  cache_1.insert_or_assign(2, 20);
  LruCache<int, int> cache_2(2);
  cache_2.insert_or_assign(1, 10);
  cache_2.insert_or_assign(2, 20);
  EXPECT_EQ(cache_1, cache_2);
  // Cache with different order should not be equal
  cache_2.find(1);
  EXPECT_NE(cache_1, cache_2);
  cache_1.find(1);
  EXPECT_EQ(cache_1, cache_2);
  // Cache with different value should be different
  cache_2.insert_or_assign(1, 11);
  EXPECT_NE(cache_1, cache_2);
  // Cache with different capacity should not be equal
  LruCache<int, int> cache_3(3);
  cache_3.insert_or_assign(1, 10);
  cache_3.insert_or_assign(2, 20);
  EXPECT_NE(cache_1, cache_3);
  // Empty cache should not be equal to non-empty ones
  LruCache<int, int> cache_4(2);
  EXPECT_NE(cache_1, cache_4);
  // Empty caches should be equal
  LruCache<int, int> cache_5(2);
  EXPECT_EQ(cache_4, cache_5);
  // Empty caches with different capacity should not be equal
  LruCache<int, int> cache_6(3);
  EXPECT_NE(cache_4, cache_6);
}

TEST(LruCacheTest, try_emplace_test) {
  LruCache<int, int> cache(2);
  cache.insert_or_assign(1, 10);
  cache.insert_or_assign(2, 20);
  auto result = cache.try_emplace(42, 420);
  // 1, 10 evicted
  EXPECT_EQ(std::get<2>(result), std::make_pair(1, 10));
  auto iter = cache.find(42);
  EXPECT_EQ(iter->second, 420);
  EXPECT_EQ(iter, std::get<0>(result));
  ASSERT_THAT(cache, ElementsAre(Pair(42, 420), Pair(2, 20)));
}

TEST(LruCacheTest, copy_test) {
  LruCache<int, std::shared_ptr<int>> cache(2);
  cache.insert_or_assign(1, std::make_shared<int>(100));
  auto iter = cache.find(1);
  EXPECT_EQ(*iter->second, 100);
  LruCache<int, std::shared_ptr<int>> new_cache = cache;
  iter = new_cache.find(1);
  EXPECT_EQ(*iter->second, 100);
  *iter->second = 300;
  iter = new_cache.find(1);
  EXPECT_EQ(*iter->second, 300);
  // Since copy is used, shared_ptr should increase count
  EXPECT_EQ(iter->second.use_count(), 2);
}

TEST(LruCacheTest, move_test) {
  LruCache<int, std::shared_ptr<int>> cache(2);
  cache.insert_or_assign(1, std::make_shared<int>(100));
  auto iter = cache.find(1);
  EXPECT_EQ(*iter->second, 100);
  LruCache<int, std::shared_ptr<int>> new_cache = std::move(cache);
  iter = new_cache.find(1);
  EXPECT_EQ(*iter->second, 100);
  *iter->second = 300;
  iter = new_cache.find(1);
  EXPECT_EQ(*iter->second, 300);
  // Since move is used, shared_ptr should not increase count
  EXPECT_EQ(iter->second.use_count(), 1);
}

TEST(LruCacheTest, move_insert_unique_ptr_test) {
  LruCache<int, std::unique_ptr<int>> cache(2);
  cache.insert_or_assign(1, std::make_unique<int>(100));
  auto iter = cache.find(1);
  EXPECT_EQ(*iter->second, 100);
  cache.insert_or_assign(1, std::make_unique<int>(400));
  iter = cache.find(1);
  EXPECT_EQ(*iter->second, 400);
}

TEST(LruCacheTest, move_insert_cache_test) {
  LruCache<int, LruCache<int, int>> cache(2);
  LruCache<int, int> m1(2);
  m1.insert_or_assign(1, 100);
  cache.insert_or_assign(1, std::move(m1));
  auto iter = cache.find(1);
  EXPECT_THAT(iter->second, ElementsAre(Pair(1, 100)));
  LruCache<int, int> m2(2);
  m2.insert_or_assign(2, 200);
  cache.insert_or_assign(1, std::move(m2));
  iter = cache.find(1);
  EXPECT_THAT(iter->second, ElementsAre(Pair(2, 200)));
}

TEST(LruCacheTest, erase_one_item_test) {
  LruCache<int, int> cache(3);
  cache.insert_or_assign(1, 10);
  cache.insert_or_assign(2, 20);
  cache.insert_or_assign(3, 30);
  auto iter = cache.find(2);
  // 2, 3, 1
  cache.find(3);
  // 3, 2, 1
  iter = cache.erase(iter);
  EXPECT_EQ(iter->first, 1);
  EXPECT_EQ(iter->second, 10);
  EXPECT_THAT(cache, ElementsAre(Pair(3, 30), Pair(1, 10)));
}

TEST(LruCacheTest, erase_in_for_loop_test) {
  LruCache<int, int> cache(3);
  cache.insert_or_assign(1, 10);
  cache.insert_or_assign(2, 20);
  cache.insert_or_assign(3, 30);
  for (auto iter = cache.begin(); iter != cache.end();) {
    if (iter->first == 2) {
      iter = cache.erase(iter);
    } else {
      ++iter;
    }
  }
  EXPECT_THAT(cache, ElementsAre(Pair(3, 30), Pair(1, 10)));
}

TEST(LruCacheTest, get_and_contains_key_test) {
  LruCache<int, int> cache(3);  // capacity = 3;
  EXPECT_EQ(cache.size(), 0ul);
  EXPECT_EQ(cache.find(42), cache.end());
  EXPECT_FALSE(cache.contains(42));
  EXPECT_FALSE(cache.insert_or_assign(56, 200));
  EXPECT_EQ(cache.find(42), cache.end());
  EXPECT_FALSE(cache.contains(42));
  EXPECT_NE(cache.find(56), cache.end());
  EXPECT_TRUE(cache.contains(56));
  auto iter = cache.find(56);
  EXPECT_NE(iter, cache.end());
  EXPECT_EQ(iter->second, 200);
  EXPECT_TRUE(cache.extract(56));
  EXPECT_FALSE(cache.contains(56));
}

TEST(LruCacheTest, put_and_get_sequence_1) {
  // Section 1: Ordered put and ordered get
  LruCache<int, int> cache(3);  // capacity = 3;
  EXPECT_FALSE(cache.insert_or_assign(1, 10));
  EXPECT_EQ(cache.size(), 1ul);
  EXPECT_FALSE(cache.insert_or_assign(2, 20));
  EXPECT_EQ(cache.size(), 2ul);
  EXPECT_FALSE(cache.insert_or_assign(3, 30));
  EXPECT_EQ(cache.size(), 3ul);
  // 3, 2, 1 after above operations

  auto evicted = cache.insert_or_assign(4, 40);
  // 4, 3, 2 after above operations, 1 is evicted
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(1, 10));
  EXPECT_EQ(cache.find(1), cache.end());
  LruCache<int, int>::const_iterator iter;
  EXPECT_NE(iter = cache.find(4), cache.end());
  EXPECT_EQ(iter->second, 40);
  EXPECT_NE(iter = cache.find(2), cache.end());
  EXPECT_EQ(iter->second, 20);
  EXPECT_NE(iter = cache.find(3), cache.end());
  EXPECT_EQ(iter->second, 30);
  // 3, 2, 4 after above operations

  // Section 2: Over capacity put and ordered get
  evicted = cache.insert_or_assign(5, 50);
  // 5, 3, 2 after above operations, 4 is evicted
  EXPECT_EQ(cache.size(), 3ul);
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(4, 40));

  EXPECT_TRUE(cache.extract(3));
  // 5, 2 should be in cache, 3 is removed
  EXPECT_FALSE(cache.insert_or_assign(6, 60));
  // 6, 5, 2 should be in cache

  // Section 3: Out of order get
  EXPECT_EQ(cache.find(3), cache.end());
  EXPECT_EQ(cache.find(4), cache.end());
  EXPECT_NE(iter = cache.find(2), cache.end());
  // 2, 6, 5 should be in cache
  EXPECT_EQ(iter->second, 20);
  EXPECT_NE(iter = cache.find(6), cache.end());
  // 6, 2, 5 should be in cache
  EXPECT_EQ(iter->second, 60);
  EXPECT_NE(iter = cache.find(5), cache.end());
  // 5, 6, 2 should be in cache
  EXPECT_EQ(iter->second, 50);
  evicted = cache.insert_or_assign(7, 70);
  // 7, 5, 6 should be in cache, 2 is evicted
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(2, 20));
}

TEST(LruCacheTest, put_and_get_sequence_2) {
  // Section 1: Replace item in cache
  LruCache<int, int> cache(2);  // size = 2;
  EXPECT_FALSE(cache.insert_or_assign(1, 10));
  EXPECT_FALSE(cache.insert_or_assign(2, 20));
  // 2, 1 in cache
  auto evicted = cache.insert_or_assign(3, 30);
  // 3, 2 in cache, 1 is evicted
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(1, 10));
  EXPECT_FALSE(cache.insert_or_assign(2, 200));
  // 2, 3 in cache, nothing is evicted
  EXPECT_EQ(cache.size(), 2ul);

  EXPECT_FALSE(cache.contains(1));
  LruCache<int, int>::const_iterator iter;
  EXPECT_NE(iter = cache.find(2), cache.end());
  EXPECT_EQ(iter->second, 200);
  EXPECT_NE(iter = cache.find(3), cache.end());
  // 3, 2 in cache
  EXPECT_EQ(iter->second, 30);

  evicted = cache.insert_or_assign(4, 40);
  // 4, 3 in cache, 2 is evicted
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(2, 200));

  EXPECT_FALSE(cache.contains(2));
  EXPECT_NE(iter = cache.find(3), cache.end());
  EXPECT_EQ(iter->second, 30);
  EXPECT_NE(iter = cache.find(4), cache.end());
  EXPECT_EQ(iter->second, 40);
  // 4, 3 in cache

  EXPECT_TRUE(cache.extract(4));
  EXPECT_FALSE(cache.contains(4));
  // 3 in cache
  EXPECT_EQ(cache.size(), 1ul);
  EXPECT_FALSE(cache.insert_or_assign(2, 2000));
  // 2, 3 in cache

  EXPECT_FALSE(cache.contains(4));
  EXPECT_NE(iter = cache.find(3), cache.end());
  EXPECT_EQ(iter->second, 30);
  EXPECT_NE(iter = cache.find(2), cache.end());
  EXPECT_EQ(iter->second, 2000);

  EXPECT_TRUE(cache.extract(2));
  EXPECT_TRUE(cache.extract(3));
  EXPECT_FALSE(cache.insert_or_assign(5, 50));
  EXPECT_FALSE(cache.insert_or_assign(1, 100));
  EXPECT_FALSE(cache.insert_or_assign(5, 1000));
  EXPECT_EQ(cache.size(), 2ul);
  // 5, 1 in cache

  evicted = cache.insert_or_assign(6, 2000);
  // 6, 5 in cache
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(1, 100));

  EXPECT_FALSE(cache.contains(2));
  EXPECT_FALSE(cache.contains(3));
  EXPECT_NE(iter = cache.find(6), cache.end());
  EXPECT_EQ(iter->second, 2000);
  EXPECT_NE(iter = cache.find(5), cache.end());
  EXPECT_EQ(iter->second, 1000);
}

TEST(LruCacheTest, in_place_modification_test) {
  LruCache<int, int> cache(2);
  cache.insert_or_assign(1, 10);
  cache.insert_or_assign(2, 20);
  auto iter = cache.find(2);
  ASSERT_THAT(cache, ElementsAre(Pair(2, 20), Pair(1, 10)));
  iter->second = 200;
  ASSERT_THAT(cache, ElementsAre(Pair(2, 200), Pair(1, 10)));
  cache.insert_or_assign(1, 100);
  // 1, 2 in cache
  ASSERT_THAT(cache, ElementsAre(Pair(1, 100), Pair(2, 200)));
  // modifying iterator does not warm up key
  iter->second = 400;
  ASSERT_THAT(cache, ElementsAre(Pair(1, 100), Pair(2, 400)));
}

TEST(LruCacheTest, get_test) {
  LruCache<int, int> cache(2);
  EXPECT_FALSE(cache.insert_or_assign(1, 10));
  EXPECT_FALSE(cache.insert_or_assign(2, 20));
  EXPECT_TRUE(cache.contains(1));
  // 1, 2 in cache
  auto evicted = cache.insert_or_assign(3, 30);
  // 3, 1 in cache
  EXPECT_TRUE(evicted);
  EXPECT_EQ(*evicted, std::make_pair(2, 20));
}

TEST(LruCacheTest, remove_test) {
  LruCache<int, int> cache(10);
  for (int key = 0; key <= 30; key++) {
    cache.insert_or_assign(key, key * 100);
  }
  for (int key = 0; key <= 20; key++) {
    EXPECT_FALSE(cache.contains(key));
  }
  for (int key = 21; key <= 30; key++) {
    EXPECT_TRUE(cache.contains(key));
  }
  for (int key = 0; key <= 20; key++) {
    EXPECT_FALSE(cache.extract(key));
  }
  for (int key = 21; key <= 30; key++) {
    auto removed = cache.extract(key);
    EXPECT_TRUE(removed);
    EXPECT_EQ(*removed, std::make_pair(key, key * 100));
  }
  for (int key = 21; key <= 30; key++) {
    EXPECT_FALSE(cache.contains(key));
  }
}

TEST(LruCacheTest, clear_test) {
  LruCache<int, int> cache(10);
  for (int key = 0; key < 10; key++) {
    cache.insert_or_assign(key, key * 100);
  }
  for (int key = 0; key < 10; key++) {
    EXPECT_TRUE(cache.contains(key));
  }
  cache.clear();
  for (int key = 0; key < 10; key++) {
    EXPECT_FALSE(cache.contains(key));
  }

  for (int key = 0; key < 10; key++) {
    cache.insert_or_assign(key, key * 1000);
  }
  for (int key = 0; key < 10; key++) {
    EXPECT_TRUE(cache.contains(key));
  }
}

TEST(LruCacheTest, container_test) {
  LruCache<int, int> lru_cache(2);
  lru_cache.insert_or_assign(1, 10);
  lru_cache.insert_or_assign(2, 20);
  // Warm elements first
  ASSERT_THAT(lru_cache, ElementsAre(Pair(2, 20), Pair(1, 10)));
}

TEST(LruCacheTest, iterator_test) {
  LruCache<int, int> lru_cache(2);
  lru_cache.insert_or_assign(1, 10);
  lru_cache.insert_or_assign(2, 20);
  // Warm elements first
  std::list<std::pair<int, int>> list(lru_cache.begin(), lru_cache.end());
  ASSERT_THAT(list, ElementsAre(Pair(2, 20), Pair(1, 10)));
}

TEST(LruCacheTest, for_loop_test) {
  LruCache<int, int> lru_cache(2);
  lru_cache.insert_or_assign(1, 10);
  lru_cache.insert_or_assign(2, 20);
  // Warm elements first
  std::list<std::pair<int, int>> list;
  for (const auto& node : lru_cache) {
    list.emplace_back(node);
  }
  ASSERT_THAT(list, ElementsAre(Pair(2, 20), Pair(1, 10)));
  list.clear();
  for (auto& node : lru_cache) {
    list.emplace_back(node);
    node.second = node.second * 2;
  }
  ASSERT_THAT(list, ElementsAre(Pair(2, 20), Pair(1, 10)));
  list.clear();
  for (const auto& node : lru_cache) {
    list.emplace_back(node);
  }
  ASSERT_THAT(list, ElementsAre(Pair(2, 40), Pair(1, 20)));
}

TEST(LruCacheTest, pressure_test) {
  int capacity = 0xFFFF;  // 2^16 = 65535
  LruCache<int, int> cache(static_cast<size_t>(capacity));

  // fill the cache
  for (int key = 0; key < capacity; key++) {
    cache.insert_or_assign(key, key);
  }

  // make sure the cache is full
  for (int key = 0; key < capacity; key++) {
    EXPECT_TRUE(cache.contains(key));
  }

  // refresh the entire cache
  for (int key = 0; key < capacity; key++) {
    int new_key = key + capacity;
    cache.insert_or_assign(new_key, new_key);
    EXPECT_FALSE(cache.contains(key));
    EXPECT_TRUE(cache.contains(new_key));
  }

  // clear the entire cache
  LruCache<int, int>::const_iterator iter;
  for (int key = capacity; key < 2 * capacity; key++) {
    EXPECT_NE(iter = cache.find(key), cache.end());
    EXPECT_EQ(iter->second, key);
    EXPECT_TRUE(cache.extract(key));
  }
  EXPECT_EQ(cache.size(), 0ul);
}

}  // namespace testing
