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

#include <cstddef>
#include <map>
#include <mutex>
#include <unordered_map>
#include <vector>

template <typename T>
class SyncMapCount {
 public:
  struct Item {
    T item;
    size_t count;
  };

 private:
  std::map<const T, std::size_t> map_;
  size_t max_size_{SIZE_MAX};
  mutable std::mutex mutex_;

  std::vector<Item> Vectorize() const {
    std::vector<Item> vec;
    for (auto& it : this->Get()) {
      vec.push_back(Item{it.first, it.second});
    }
    return vec;
  }

  std::vector<Item> GetSorted(std::function<bool(const Item& a, const Item& b)> sort_func) const {
    std::vector<Item> vec = Vectorize();
    sort(vec.begin(), vec.end(), [=](const Item& a, const Item& b) -> bool { return sort_func(a, b); });
    return vec;
  }

 public:
  SyncMapCount() : max_size_(SIZE_MAX) {}
  explicit SyncMapCount(size_t max_size) : max_size_(max_size) {}
  ~SyncMapCount() = default;

  void Put(const T item) {
    std::unique_lock<std::mutex> lock(mutex_);
    if (map_.size() == max_size_) return;
    (map_.count(item) > 0) ? map_[item] += 1 : map_[item] = 1;
  }

  std::map<const T, std::size_t> Get() const {
    std::unique_lock<std::mutex> lock(mutex_);
    return map_;
  }

  std::size_t Size() const {
    std::unique_lock<std::mutex> lock(mutex_);
    return map_.size();
  }

  void Clear() {
    std::unique_lock<std::mutex> lock(mutex_);
    map_.clear();
  }

  std::vector<Item> GetSortedHighToLow() const {
    return GetSorted([](const Item& a, const Item& b) -> bool { return a.count > b.count; });
  }

  std::vector<Item> GetSortedLowToHigh() const {
    return GetSorted([](const Item& a, const Item& b) -> bool { return a.count < b.count; });
  }
};
