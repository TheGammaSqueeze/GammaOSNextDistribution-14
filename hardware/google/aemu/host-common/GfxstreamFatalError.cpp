// Copyright 2023 The Android Open Source Project
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

#include "host-common/GfxstreamFatalError.h"

#include <cstdlib>
#include <ostream>

#include "aemu/base/Metrics.h"
#include "host-common/logging.h"

namespace {

using android::base::CreateMetricsLogger;
using android::base::GfxstreamVkAbort;

std::optional<std::function<void()>> customDieFunction = std::nullopt;

[[noreturn]] void die() {
    if (customDieFunction) {
        (*customDieFunction)();
    }
    abort();
}

}  // namespace

namespace emugl {

AbortMessage::AbortMessage(const char *file, const char *function, int line, FatalError reason)
    : mFile(file), mFunction(function), mLine(line), mReason(reason) {
    mOss << "FATAL in " << function << ", err code: " << reason.getAbortCode() << ": ";
}

AbortMessage::~AbortMessage() {
    OutputLog(stderr, 'F', mFile, mLine, 0, mOss.str().c_str());
    fflush(stderr);
    CreateMetricsLogger()->logMetricEvent(GfxstreamVkAbort{.file = mFile,
                                                           .function = mFunction,
                                                           .msg = mOss.str().c_str(),
                                                           .line = mLine,
                                                           .abort_reason = mReason.getAbortCode()});

    die();
}

void setDieFunction(std::optional<std::function<void()>> newDie) { customDieFunction = newDie; }
}  // namespace emugl
