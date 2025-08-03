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

#include <ditto/logger.h>
#include <ditto/sampler.h>

int64_t TimespecToNanos(const timespec& t) {
  return t.tv_sec * 1e9 + t.tv_nsec;
}

double TimespecToDoubleNanos(const timespec& t) {
  return static_cast<double>(TimespecToNanos(t));
}

std::vector<int64_t> TimespecToNanos(const std::vector<timespec>& tv) {
  std::vector<int64_t> nsv;
  nsv.reserve(tv.size());
  for (const auto& it : tv) nsv.push_back(TimespecToNanos(it));
  return nsv;
}

std::vector<double> TimespecToDoubleNanos(const std::vector<timespec>& tv) {
  std::vector<double> nsv;
  nsv.reserve(tv.size());
  for (const auto& it : tv) nsv.push_back(TimespecToDoubleNanos(it));
  return nsv;
}

timespec NanosToTimespec(const int64_t t_ns) {
  timespec result;
  result.tv_sec = t_ns / 1e9;
  result.tv_nsec = t_ns % static_cast<int64_t>(1e9);
  return result;
}

timespec MicrosToTimespec(const int64_t t_us) {
  timespec result;
  result.tv_sec = t_us / 1e6;
  result.tv_nsec = (t_us % static_cast<int64_t>(1e6)) * static_cast<int64_t>(1e3);
  return result;
}

bool operator==(const timespec& t1, const timespec& t2) {
  return t1.tv_sec == t2.tv_sec && t1.tv_nsec == t2.tv_nsec;
}

bool operator!=(const timespec& t1, const timespec& t2) {
  return !(t1 == t2);
}

bool operator<(const timespec& t1, const timespec& t2) {
  return ((t1.tv_sec < t2.tv_sec) || (t1.tv_sec == t2.tv_sec && t1.tv_nsec < t2.tv_nsec));
}

bool operator<=(const timespec& t1, const timespec& t2) {
  return t1 == t2 || t1 < t2;
}

bool operator>(const timespec& t1, const timespec& t2) {
  return !(t1 <= t2);
}

bool operator>=(const timespec& t1, const timespec& t2) {
  return t1 == t2 || t1 > t2;
}

// Return the value of t1 - t2, if t1 >= t2 or fail.
timespec operator-(const timespec& t1, const timespec& t2) {
  timespec result = {0, 0};
  if (t1 < t2) {
    LOGF("Subtraction cannot return negative timespec values");
  } else {
    result.tv_sec = t1.tv_sec - t2.tv_sec;
    if (t1.tv_nsec < t2.tv_nsec) {
      result.tv_sec--;
      result.tv_nsec = 1e9 - t2.tv_nsec + t1.tv_nsec;
    } else {
      result.tv_nsec = t1.tv_nsec - t2.tv_nsec;
    }
  }
  return result;
}

timespec operator+(const timespec& t1, const timespec& t2) {
  timespec result = {0, 0};
  result.tv_sec = t1.tv_sec + t2.tv_sec;
  if (t1.tv_nsec + t2.tv_nsec >= 1e9) {
    result.tv_sec++;
    result.tv_nsec = t1.tv_nsec + t2.tv_nsec - 1e9;
  } else {
    result.tv_nsec = t1.tv_nsec + t2.tv_nsec;
  }
  return result;
}

timespec operator/(const timespec& t1, uint64_t t2_ns) {
  if (t2_ns == 0) LOGF("Division by 0 for timespec");

  auto t1_ns = TimespecToNanos(t1);

  return NanosToTimespec(t1_ns / t2_ns);
}

timespec operator/(const timespec& t1, const timespec& t2) {
  return t1 / TimespecToNanos(t2);
}
