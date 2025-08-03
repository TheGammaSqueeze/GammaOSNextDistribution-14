/*
 * Copyright (C) 2023, The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

#pragma once

#include <chrono>
#include <string>

#define LOGI(...)                     \
    do {                              \
        fprintf(stdout, "[INFO] ");   \
        fprintf(stdout, __VA_ARGS__); \
    } while (0)

#ifdef DEBUG
#define LOGD(...)                               \
    do {                                        \
        if (DEBUG) fprintf(stdout, "[DEBUG] "); \
        fprintf(stdout, __VA_ARGS__);           \
    } while (0)
#else
#define LOGD(...)
#endif

#define LOGE(...)                     \
    do {                              \
        fprintf(stderr, "[ERROR] ");  \
        fprintf(stderr, __VA_ARGS__); \
    } while (0)

namespace android {
namespace express {

#ifdef DEBUG

class ExecutionMeasure final {
public:
    ExecutionMeasure(std::string name)
        : mName(std::move(name)), mStart(std::chrono::high_resolution_clock::now()) {
    }

    ~ExecutionMeasure() {
        const auto stop = std::chrono::high_resolution_clock::now();
        const auto duration = std::chrono::duration_cast<std::chrono::microseconds>(stop - mStart);
        LOGI("%s took to complete %lld microseconds\n", mName.c_str(), duration.count());
    }

private:
    const std::string mName;
    const std::chrono::time_point<std::chrono::high_resolution_clock> mStart;
};

#define MEASURE_FUNC() ExecutionMeasure func_execution_measure(__func__);
#else
#define MEASURE_FUNC()
#endif
}  // namespace express
}  // namespace android
