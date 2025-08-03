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

#ifndef LIBTEXTCLASSIFIER_UTILS_TESTING_TEST_DATA_GENERATOR_H_
#define LIBTEXTCLASSIFIER_UTILS_TESTING_TEST_DATA_GENERATOR_H_

#include <algorithm>
#include <iostream>
#include <limits>
#include <random>
#include <string>
#include <type_traits>

#include "utils/strings/stringpiece.h"

// Generates test data randomly.
class TestDataGenerator {
 public:
  explicit TestDataGenerator() : random_engine_(0) {}

  template <typename T,
            typename std::enable_if_t<std::is_integral<T>::value>* = nullptr>
  T generate() {
    typedef typename std::conditional<sizeof(T) >= sizeof(int16_t), T,
                                      std::int16_t>::type rand_type;
    std::uniform_int_distribution<rand_type> dist(
        std::numeric_limits<T>::min(), std::numeric_limits<T>::max());
    return static_cast<T>(dist(random_engine_));
  }

  template <>
  bool generate() {
    std::bernoulli_distribution dist(0.5);
    return dist(random_engine_);
  }

  template <>
  char generate() {
    std::uniform_int_distribution<int> dist(0, 25);
    return dist(random_engine_) + 'a';
  }

  template <typename T, typename std::enable_if_t<
                            std::is_floating_point<T>::value>* = nullptr>
  T generate() {
    std::uniform_real_distribution<T> dist;
    return dist(random_engine_);
  }

  template <typename T, typename std::enable_if_t<
                            std::is_same<std::string, T>::value>* = nullptr>
  T generate() {
    std::uniform_int_distribution<> dist(1, 10);
    return std::string(dist(random_engine_), '*');
  }

 private:
  std::default_random_engine random_engine_;
};

#endif  // LIBTEXTCLASSIFIER_UTILS_TESTING_TEST_DATA_GENERATOR_H_
