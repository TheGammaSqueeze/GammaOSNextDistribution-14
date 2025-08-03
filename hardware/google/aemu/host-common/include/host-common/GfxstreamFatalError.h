// Copyright 2021 The Android Open Source Project
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

#pragma once

#include <cstdint>
#include <functional>
#include <optional>
#include <sstream>

namespace emugl {

typedef int32_t VkResult;

enum GfxstreamAbortReason : int64_t {
    VK_RESULT = 0,
    ABORT_REASON_OTHER =
        4'300'000'000  // VkResult is 32-bit, so we pick this to be outside the 32-bit range.
};

struct FatalError {
    const GfxstreamAbortReason abort_reason;
    const VkResult vk_result;

    explicit FatalError(GfxstreamAbortReason ab_reason)
        : abort_reason(ab_reason), vk_result(0) {}
    explicit FatalError(VkResult vk_result) : abort_reason(VK_RESULT), vk_result(vk_result) {}

    inline int64_t getAbortCode() const {
        return abort_reason == VK_RESULT ? static_cast<int64_t>(vk_result) : abort_reason;
    }
};

class AbortMessage {
   public:
    AbortMessage(const char* file, const char* function, int line, FatalError reason);

    [[noreturn]] ~AbortMessage();

    std::ostream& stream() { return mOss; }

   private:
    const char* const mFile;
    const char* const mFunction;
    const int mLine;
    const FatalError mReason;
    std::ostringstream mOss;
};

// A function that terminates the process should be passed in. When calling the GFXSTREAM_ABORT
// macro, the set function will be used to terminate the process instead of std::abort.
void setDieFunction(std::optional<std::function<void()>> newDie);
}  // namespace emugl

#define GFXSTREAM_ABORT(reason) ::emugl::AbortMessage(__FILE__, __func__, __LINE__, reason).stream()

#define FATAL() GFXSTREAM_ABORT(::emugl::FatalError(ABORT_REASON_OTHER))
