/*
 * Copyright 2022 The Android Open Source Project
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
#include <cstdarg>
#include <functional>
#include <string>
namespace netsim {

#define BtsLog(fmt, ...) __BtsLog(3, __FILE__, __LINE__, fmt, ##__VA_ARGS__)
#define BtsLogInfo(fmt, ...) __BtsLog(2, __FILE__, __LINE__, fmt, ##__VA_ARGS__)
#define BtsLogWarn(fmt, ...) __BtsLog(1, __FILE__, __LINE__, fmt, ##__VA_ARGS__)
#define BtsLogError(fmt, ...) \
  __BtsLog(0, __FILE__, __LINE__, fmt, ##__VA_ARGS__)

void __BtsLog(int priority, const char *file, int line, const char *fmt, ...);

using BtsLogFn = std::function<void(int, const char *, int, const char *)>;

void setBtsLogSink(BtsLogFn logFn);

}  // namespace netsim
