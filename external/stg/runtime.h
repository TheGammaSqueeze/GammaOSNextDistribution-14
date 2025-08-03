// SPDX-License-Identifier: Apache-2.0 WITH LLVM-exception
// -*- mode: C++ -*-
//
// Copyright 2021-2023 Google LLC
//
// Licensed under the Apache License v2.0 with LLVM Exceptions (the
// "License"); you may not use this file except in compliance with the
// License.  You may obtain a copy of the License at
//
//     https://llvm.org/LICENSE.txt
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
//
// Author: Giuliano Procida

#ifndef STG_RUNTIME_H_
#define STG_RUNTIME_H_

#include <cstddef>
#include <cstdint>
#include <ctime>
#include <map>
#include <ostream>

namespace stg {

struct Nanoseconds {
  explicit Nanoseconds(uint64_t ns) : ns(ns) {}
  uint64_t ns;
};

struct Frequencies {
  std::map<size_t, size_t> counts;
};

struct Runtime {
  Runtime(std::ostream& output, bool print_metrics)
      : output(output), print_metrics(print_metrics) {}
  template <typename V>
  void PrintMetric(const char* name, const V& value) {
    if (print_metrics) {
      output << name << ": " << value << '\n';
    }
  }
  std::ostream& output;
  bool print_metrics;
};

// These objects only record values on destruction, so scope them!

class Time {
 public:
  Time(Runtime& runtime, const char* name);
  Time(Time&& other) = delete;
  Time& operator=(Time&& other) = delete;
  ~Time();

 private:
  Runtime& runtime_;
  const char* name_;
  struct timespec start_;
};

class Counter {
 public:
  Counter(Runtime& runtime, const char* name);
  Counter(Counter&& other) = delete;
  Counter& operator=(Counter&& other) = delete;
  ~Counter();

  Counter& operator=(size_t x) {
    value_ = x;
    return *this;
  }

  Counter& operator+=(size_t x) {
    value_ += x;
    return *this;
  }

  Counter& operator++() {
    ++value_;
    return *this;
  }

 private:
  Runtime& runtime_;
  const char* name_;
  size_t value_;
};

class Histogram {
 public:
  Histogram(Runtime& runtime, const char* name);
  Histogram(Histogram&& other) = delete;
  Histogram& operator=(Histogram&& other) = delete;
  ~Histogram();

  void Add(size_t item) {
    ++frequencies_.counts[item];
  }

 private:
  Runtime& runtime_;
  const char* name_;
  Frequencies frequencies_;
};

}  // namespace stg

#endif  // STG_RUNTIME_H_
