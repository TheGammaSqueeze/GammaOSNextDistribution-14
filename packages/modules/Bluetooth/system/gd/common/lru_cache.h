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

#pragma once

#include <functional>
#include <iterator>
#include <list>
#include <mutex>
#include <optional>
#include <thread>
#include <unordered_map>

#include "common/list_map.h"
#include "os/log.h"

namespace bluetooth {
namespace common {

// An LRU map-cache the evict the oldest item when reaching capacity
//
// Usage:
//   - keys are sorted from warmest to coldest
//   - iterating through the cache won't warm up keys
//   - operations on iterators won't warm up keys
//   - find(), contains(), insert_or_assign() will warm up the key
//   - insert_or_assign() will evict coldest key when cache reaches capacity
//   - NOT THREAD SAFE
//
// Performance:
//   - Key look-up and modification is O(1)
//   - Memory consumption is:
//     O(2*capacity*sizeof(K) + capacity*(sizeof(nullptr)+sizeof(V)))
//
// Template:
//   - Key key type
//   - T value type
// */
template <typename Key, typename T>
class LruCache {
 public:
  using value_type = typename ListMap<Key, T>::value_type;
  // different from c++17 node_type on purpose as we want node to be copyable
  using node_type = typename ListMap<Key, T>::node_type;
  using iterator = typename ListMap<Key, T>::iterator;
  using const_iterator = typename ListMap<Key, T>::const_iterator;

  // Constructor a LRU cache with |capacity|
  explicit LruCache(size_t capacity) : capacity_(capacity) {
    ASSERT_LOG(capacity_ != 0, "Unable to have 0 LRU Cache capacity");
  }

  // for move
  LruCache(LruCache&& other) noexcept = default;
  LruCache& operator=(LruCache&& other) noexcept = default;

  // copy-constructor
  // iterators in key_map_ cannot be copied directly
  LruCache(const LruCache& other) : capacity_(other.capacity_), list_map_(other.list_map_) {}

  // copy-assignment
  // iterators in key_map_ cannot be copied directly
  LruCache& operator=(const LruCache& other) {
    if (&other == this) {
      return *this;
    }
    capacity_ = other.capacity_;
    list_map_ = other.list_map_;
    return *this;
  }

  // comparison operators
  bool operator==(const LruCache& rhs) const {
    return capacity_ == rhs.capacity_ && list_map_ == rhs.list_map_;
  }
  bool operator!=(const LruCache& rhs) const {
    return !(*this == rhs);
  }

  ~LruCache() {
    clear();
  }

  // Clear the cache
  void clear() {
    list_map_.clear();
  }

  // Find the value of a key, and move the key to the head of cache, if there is one. Return iterator to value if key
  // exists, end() if not. Iterator might be invalidated when removed or evicted. Const version.
  //
  // LRU: Will warm up key
  // LRU: Access to returned iterator won't move key in LRU
  const_iterator find(const Key& key) const {
    return const_cast<LruCache*>(this)->find(key);
  }

  // Find the value of a key, and move the key to the head of cache, if there is one. Return iterator to value if key
  // exists, end() if not. Iterator might be invalidated when removed or evicted
  //
  // LRU: Will warm up key
  // LRU: Access to returned iterator won't move key in LRU
  iterator find(const Key& key) {
    auto iter = list_map_.find(key);
    if (iter == list_map_.end()) {
      return end();
    }
    // move to front
    list_map_.splice(list_map_.begin(), list_map_, iter);
    return iter;
  }

  // Check if key exist in the cache. Return true if key exist in cache, false, if not
  //
  // LRU: Will warm up key
  bool contains(const Key& key) const {
    return find(key) != list_map_.end();
  }

  // Put a key-value pair to the head of cache, evict the oldest key if cache is at capacity. Eviction is based on key
  // ONLY. Hence, updating a key will not evict the oldest key. Return evicted value if old value was evicted,
  // std::nullopt if not. The return value will be evaluated to true in a boolean context if a value is contained by
  // std::optional, false otherwise.
  //
  // LRU: Will warm up key
  std::optional<node_type> insert_or_assign(const Key& key, T value) {
    if (contains(key)) {
      // contains() calls find() that moved the node to the head
      list_map_.begin()->second = std::move(value);
      return std::nullopt;
    }
    // remove tail if at capacity
    std::optional<node_type> evicted_node = std::nullopt;
    if (list_map_.size() == capacity_) {
      evicted_node = list_map_.extract(std::prev(list_map_.end())->first);
    }
    // insert new one to front of list
    list_map_.insert_or_assign(list_map_.begin(), key, std::move(value));
    return evicted_node;
  }

  // Put a key-value pair to the head of cache, evict the oldest key if cache is at capacity. Eviction is based on key
  // ONLY. Hence, updating a key will not evict the oldest key. This method tries to construct the value in-place. If
  // the key already exist, this method only update the value. Return inserted iterator, whether insertion happens, and
  // evicted value if old value was evicted or std::nullopt
  //
  // LRU: Will warm up key
  template <class... Args>
  std::tuple<iterator, bool, std::optional<node_type>> try_emplace(const Key& key, Args&&... args) {
    if (contains(key)) {
      // contains() calls find() that moved the node to the head
      return std::make_tuple(end(), false, std::nullopt);
    }
    // remove tail if at capacity
    std::optional<node_type> evicted_node = std::nullopt;
    if (list_map_.size() == capacity_) {
      evicted_node = list_map_.extract(std::prev(list_map_.end())->first);
    }
    // insert new one to front of list
    auto pair = list_map_.try_emplace(list_map_.begin(), key, std::forward<Args>(args)...);
    return std::make_tuple(pair.first, pair.second, std::move(evicted_node));
  }

  // Delete a key from cache, return removed value if old value was evicted, std::nullopt if not. The return value will
  // be evaluated to true in a boolean context if a value is contained by std::optional, false otherwise.
  inline std::optional<node_type> extract(const Key& key) {
    return list_map_.extract(key);
  }

  /// Remove an iterator pointed item from the lru cache and return the iterator immediately after the erased item
  iterator erase(const_iterator iter) {
    return list_map_.erase(iter);
  }

  // Return size of the cache
  inline size_t size() const {
    return list_map_.size();
  }

  // Iterator interface for begin
  inline iterator begin() {
    return list_map_.begin();
  }

  // Return iterator interface for begin, const
  inline const_iterator begin() const {
    return list_map_.begin();
  }

  // Return iterator interface for end
  inline iterator end() {
    return list_map_.end();
  }

  // Iterator interface for end, const
  inline const_iterator end() const {
    return list_map_.end();
  }

 private:
  size_t capacity_;
  ListMap<Key, T> list_map_;
};

}  // namespace common
}  // namespace bluetooth
