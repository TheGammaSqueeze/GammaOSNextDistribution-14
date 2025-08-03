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
#include <type_traits>
#include <unordered_map>

namespace bluetooth {
namespace common {

// A map that maintains order of its element as a list. An element that is put earlier will appear before an element
// that is put later when iterating through this map's entries. Keys must be unique.
//
// Performance:
//   - Key look-up and modification is O(1)
//   - Value operated by replacement, no in-place modification
//   - Memory consumption is:
//     O(2*capacity*sizeof(K) + capacity*(sizeof(nullptr)+sizeof(V)))
//   - NOT THREAD SAFE
//
// Template:
//   - Key key
//   - T value
template <typename Key, typename T>
class ListMap {
 public:
  using value_type = std::pair<const Key, T>;
  // different from c++17 node_type on purpose as we want node to be copyable
  using node_type = std::pair<Key, T>;
  using iterator = typename std::list<value_type>::iterator;
  using const_iterator = typename std::list<value_type>::const_iterator;

  // Constructor of the list map
  ListMap() = default;

  // for move
  ListMap(ListMap&& other) noexcept = default;
  ListMap& operator=(ListMap&& other) noexcept = default;

  // copy-constructor
  // iterators in key_map_ cannot be copied directly
  ListMap(const ListMap& other) : node_list_(other.node_list_) {
    for (auto iter = node_list_.begin(); iter != node_list_.end(); iter++) {
      key_map_.emplace(iter->first, iter);
    }
  }

  // copy-assignment
  // iterators in key_map_ cannot be copied directly
  ListMap& operator=(const ListMap& other) {
    if (&other == this) {
      return *this;
    }
    node_list_ = other.node_list_;
    key_map_.clear();
    for (auto iter = node_list_.begin(); iter != node_list_.end(); iter++) {
      key_map_.emplace(iter->first, iter);
    }
    return *this;
  }

  // comparison operators
  bool operator==(const ListMap& rhs) const {
    return node_list_ == rhs.node_list_;
  }
  bool operator!=(const ListMap& rhs) const {
    return !(*this == rhs);
  }

  ~ListMap() {
    clear();
  }

  // Clear the list map
  void clear() {
    key_map_.clear();
    node_list_.clear();
  }

  // const version of find()
  const_iterator find(const Key& key) const {
    return const_cast<ListMap*>(this)->find(key);
  }

  // Get the value of a key. Return iterator to the item if found, end() if not found
  iterator find(const Key& key) {
    auto map_iterator = key_map_.find(key);
    if (map_iterator == key_map_.end()) {
      return end();
    }
    return map_iterator->second;
  }

  // Check if key exist in the map. Return true if key exist in map, false if not.
  bool contains(const Key& key) const {
    return find(key) != end();
  }

  // Try emplace an element before a specific position |pos| of the list map. If the |key| already exists, does nothing.
  // Moved arguments won't be moved when key already exists. Return <iterator, true> when key does not exist, <iterator,
  // false> when key exist and iterator is the position where it was placed.
  template <class... Args>
  std::pair<iterator, bool> try_emplace(const_iterator pos, const Key& key, Args&&... args) {
    auto map_iterator = key_map_.find(key);
    if (map_iterator != key_map_.end()) {
      return std::make_pair(end(), false);
    }
    auto list_iterator = node_list_.emplace(pos, key, std::forward<Args>(args)...);
    key_map_.emplace(key, list_iterator);
    return std::make_pair(list_iterator, true);
  }

  // Try emplace an element before the end of the list map. If the key already exists, does nothing. Moved arguments
  // won't be moved when key already exists return <iterator, true> when key does not exist, <iterator, false> when key
  // exist and iterator is the position where it was placed
  template <class... Args>
  std::pair<iterator, bool> try_emplace_back(const Key& key, Args&&... args) {
    return try_emplace(end(), key, std::forward<Args>(args)...);
  }

  // Put a key-value pair to the map before position. If key already exist, |pos| will be ignored and existing value
  // will be replaced
  void insert_or_assign(const_iterator pos, const Key& key, T value) {
    auto map_iterator = key_map_.find(key);
    if (map_iterator != key_map_.end()) {
      map_iterator->second->second = std::move(value);
      return;
    }
    auto list_iterator = node_list_.emplace(pos, key, std::move(value));
    key_map_.emplace(key, list_iterator);
  }

  // Put a key-value pair to the tail of the map or replace the current value without moving the key if key exists
  void insert_or_assign(const Key& key, T value) {
    insert_or_assign(end(), key, std::move(value));
  }

  // STL splice, same as std::list::splice
  // - pos: element before which the content will be inserted
  // - other: another container to transfer the content from
  // - it: the element to transfer from other to *this
  void splice(const_iterator pos, ListMap<Key, T>& other, const_iterator it) {
    if (&other != this) {
      auto map_node = other.key_map_.extract(it->first);
      key_map_.insert(std::move(map_node));
    }
    node_list_.splice(pos, other.node_list_, it);
  }

  // Remove a key from the list map and return removed value if key exits, std::nullopt if not. The return value will be
  // evaluated to true in a boolean context if a value is contained by std::optional, false otherwise.
  std::optional<node_type> extract(const Key& key) {
    auto map_iterator = key_map_.find(key);
    if (map_iterator == key_map_.end()) {
      return std::nullopt;
    }
    std::optional<node_type> removed_node(std::move(*map_iterator->second));
    node_list_.erase(map_iterator->second);
    key_map_.erase(map_iterator);
    return removed_node;
  }

  // Remove an iterator pointed item from the list map and return the iterator immediately after the erased item
  iterator erase(const_iterator iter) {
    key_map_.erase(iter->first);
    return node_list_.erase(iter);
  }

  // Return size of the list map
  inline size_t size() const {
    return node_list_.size();
  }

  // Return iterator interface for begin
  inline iterator begin() {
    return node_list_.begin();
  }

  // Iterator interface for begin, const
  inline const_iterator begin() const {
    return node_list_.begin();
  }

  // Iterator interface for end
  inline iterator end() {
    return node_list_.end();
  }

  // Iterator interface for end, const
  inline const_iterator end() const {
    return node_list_.end();
  }

 private:
  std::list<value_type> node_list_;
  std::unordered_map<Key, iterator> key_map_;
};

}  // namespace common
}  // namespace bluetooth