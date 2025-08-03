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

#include <cstddef>
#include <deque>
#include <iterator>
#include <memory>
#include <mutex>
#include <vector>

namespace bluetooth {
namespace common {

template <typename T>
class CircularBuffer {
 public:
  explicit CircularBuffer(size_t size);

  // Push one item to the circular buffer
  void Push(T item);
  // Take a snapshot of the circular buffer and return it as a vector
  std::vector<T> Pull() const;
  // Drain everything from the circular buffer and return them as a vector
  std::vector<T> Drain();

 private:
  const size_t size_;
  std::deque<T> queue_;
  mutable std::mutex mutex_;
};

class Timestamper {
 public:
  virtual long long GetTimestamp() const = 0;
  virtual ~Timestamper() {}
};

class TimestamperInMilliseconds : public Timestamper {
 public:
  long long GetTimestamp() const override {
    return std::chrono::duration_cast<std::chrono::milliseconds>(std::chrono::system_clock::now().time_since_epoch())
        .count();
  }
  virtual ~TimestamperInMilliseconds() {}
};

template <typename T>
struct TimestampedEntry {
  long long timestamp;
  T entry;
};

template <typename T>
class TimestampedCircularBuffer : public CircularBuffer<TimestampedEntry<T>> {
 public:
  explicit TimestampedCircularBuffer(
      size_t size, std::unique_ptr<Timestamper> timestamper = std::make_unique<TimestamperInMilliseconds>());

  void Push(T item);
  std::vector<TimestampedEntry<T>> Pull() const;
  std::vector<TimestampedEntry<T>> Drain();

 private:
  std::unique_ptr<Timestamper> timestamper_{std::make_unique<TimestamperInMilliseconds>()};
};

}  // namespace common
}  // namespace bluetooth

template <typename T>
bluetooth::common::CircularBuffer<T>::CircularBuffer(size_t size) : size_(size) {}

template <typename T>
void bluetooth::common::CircularBuffer<T>::Push(const T item) {
  std::unique_lock<std::mutex> lock(mutex_);
  queue_.push_back(item);
  while (queue_.size() > size_) {
    queue_.pop_front();
  }
}

template <typename T>
std::vector<T> bluetooth::common::CircularBuffer<T>::Pull() const {
  std::unique_lock<std::mutex> lock(mutex_);
  return std::vector<T>(queue_.cbegin(), queue_.cend());
}

template <typename T>
std::vector<T> bluetooth::common::CircularBuffer<T>::Drain() {
  std::unique_lock<std::mutex> lock(mutex_);
  std::vector<T> items(std::make_move_iterator(queue_.begin()), std::make_move_iterator(queue_.end()));
  queue_.clear();
  return items;
}

template <typename T>
bluetooth::common::TimestampedCircularBuffer<T>::TimestampedCircularBuffer(
    size_t size, std::unique_ptr<Timestamper> timestamper)
    : CircularBuffer<TimestampedEntry<T>>(size), timestamper_(std::move(timestamper)) {}

template <typename T>
void bluetooth::common::TimestampedCircularBuffer<T>::Push(const T item) {
  TimestampedEntry<T> timestamped_entry{timestamper_->GetTimestamp(), item};
  bluetooth::common::CircularBuffer<TimestampedEntry<T>>::Push(timestamped_entry);
}

template <typename T>
std::vector<struct bluetooth::common::TimestampedEntry<T>> bluetooth::common::TimestampedCircularBuffer<T>::Pull()
    const {
  return bluetooth::common::CircularBuffer<TimestampedEntry<T>>::Pull();
}

template <typename T>
std::vector<struct bluetooth::common::TimestampedEntry<T>> bluetooth::common::TimestampedCircularBuffer<T>::Drain() {
  return bluetooth::common::CircularBuffer<TimestampedEntry<T>>::Drain();
}
