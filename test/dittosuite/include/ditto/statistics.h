// Copyright (C) 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <ditto/logger.h>
#include <ditto/sampler.h>

#include <cmath>
#include <ctime>

#include <algorithm>
#include <memory>
#include <numeric>
#include <string>
#include <vector>

namespace dittosuite {

template <class T>
T StatisticsGetMin(const std::vector<T>& samples) {
  if (samples.empty()) LOGF("Cannot compute the min on an empty vector");

  return *std::min_element(samples.begin(), samples.end());
}

template <class T>
T StatisticsGetMax(const std::vector<T>& samples) {
  if (samples.empty()) LOGF("Cannot compute the max on an empty vector");

  return *std::max_element(samples.begin(), samples.end());
}

template <class T>
T StatisticsGetMean(const std::vector<T>& samples) {
  if (samples.empty()) LOGF("Cannot compute the mean on an empty vector");

  T result = std::accumulate(samples.begin(), samples.end(), T{});
  return result / samples.size();
}

template <class T>
T StatisticsGetMedian(const std::vector<T>& samples) {
  if (samples.empty()) LOGF("Cannot compute the median on an empty vector");

  auto my_vector_copy = samples;
  auto n = samples.size();

  if (n % 2) {
    // odd number of elements, the median is the element in the middle
    std::nth_element(my_vector_copy.begin(), my_vector_copy.begin() + n / 2, my_vector_copy.end());
    return my_vector_copy[n / 2];
  } else {
    // even number of elements, the median is the average between the two middle elements
    std::nth_element(my_vector_copy.begin(), my_vector_copy.begin() + n / 2, my_vector_copy.end());
    std::nth_element(my_vector_copy.begin(), my_vector_copy.begin() + (n - 1) / 2,
                     my_vector_copy.end());
    T result = my_vector_copy[n / 2] + my_vector_copy[(n - 1) / 2];
    return result / 2;
  }
}

// The standard deviation sd of a population of N samples, where x_i is the
// i-th sample and x is the average among all the samples is computed as:
//
// sd = sqrt( sum( (x_i - x)^2 ) / N )
template <class T>
double StatisticsGetSd(const std::vector<T>& samples) {
  if (samples.empty()) LOGF("Cannot compute the sd on an empty vector");

  T mean = StatisticsGetMean(samples);
  double variance;

  variance = 0.0;
  for (const auto& s : samples) {
    double deviation = s - mean;
    double deviation_square = std::pow(deviation, 2);
    variance += deviation_square;  // TODO(lucialup): add overflow error handling
  }
  variance /= samples.size();

  return std::sqrt(variance);
}

}  // namespace dittosuite