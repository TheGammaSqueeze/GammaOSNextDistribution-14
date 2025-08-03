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

#include <cstdint>
#include <ctime>

int64_t TimespecToNanos(const timespec& t);
double TimespecToDoubleNanos(const timespec& t);
std::vector<int64_t> TimespecToNanos(const std::vector<timespec>& tv);
std::vector<double> TimespecToDoubleNanos(const std::vector<timespec>& tv);
timespec NanosToTimespec(int64_t time_ns);
timespec MicrosToTimespec(int64_t time_us);

bool operator==(const timespec& t1, const timespec& t2);
bool operator!=(const timespec& t1, const timespec& t2);
bool operator<(const timespec& t1, const timespec& t2);
bool operator<=(const timespec& t1, const timespec& t2);
bool operator>(const timespec& t1, const timespec& t2);
bool operator>=(const timespec& t1, const timespec& t2);
timespec operator-(const timespec& t1, const timespec& t2);
timespec operator+(const timespec& t1, const timespec& t2);
timespec operator/(const timespec& t1, uint64_t n);
timespec operator/(const timespec& t1, const timespec& t2);
