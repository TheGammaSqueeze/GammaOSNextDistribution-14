/*
 * Copyright 2023 The Android Open Source Project
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

#include <cstdint>

namespace aidl {
namespace google {
namespace hardware {
namespace power {
namespace impl {
namespace pixel {

enum class AdpfErrorCode : int32_t { ERR_OK = 0, ERR_BAD_STATE = -1, ERR_BAD_ARG = -2 };

enum class AdpfHintType : int32_t {
    ADPF_VOTE_DEFAULT = 1,
    ADPF_CPU_LOAD_UP = 2,
    ADPF_CPU_LOAD_RESET = 3,
    ADPF_CPU_LOAD_RESUME = 4,
    ADPF_VOTE_POWER_EFFICIENCY = 5,
    ADPF_GPU_LOAD_UP = 6,
    ADPF_GPU_LOAD_DOWN = 7,
    ADPF_GPU_LOAD_RESET = 8,
    ADPF_GPU_CAPACITY = 9,
};

constexpr int kUclampMin{0};
constexpr int kUclampMax{1024};

}  // namespace pixel
}  // namespace impl
}  // namespace power
}  // namespace hardware
}  // namespace google
}  // namespace aidl
