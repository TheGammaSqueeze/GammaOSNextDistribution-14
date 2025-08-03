// Copyright 2020 The Android Open Source Project
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

#include <gmock/gmock.h>

#include "host-common/GfxstreamFatalError.h"

#include "aemu/base/testing/TestUtils.h"

namespace emugl {
namespace {

TEST(GFXSTREAM_ABORT, MessageIsWellFormatted) {
    EXPECT_DEATH(
        { GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER)) << "I'm dying!"; },
        MatchesStdRegex(R"re(F\d\d\d\d .*\] FATAL in \S+, err code: 4300000000: I'm dying!\n)re"));
}

TEST(GFXSTREAM_ABORT, WithVkResult) {
    VkResult VK_ERROR_FRAGMENTATION = -1000161000;
    EXPECT_DEATH({ GFXSTREAM_ABORT(FatalError(VK_ERROR_FRAGMENTATION)) << "so fragmented"; },
                 "err code: -1000161000: so fragmented");
}

TEST(GFXSTREAM_ABORT, WithCustomizedDeathFunction) {
    emugl::setDieFunction([] { exit(42); });
    EXPECT_EXIT(GFXSTREAM_ABORT(FatalError(ABORT_REASON_OTHER));, testing::ExitedWithCode(42), "");
    setDieFunction(std::nullopt);
}
}  // namespace
}  // namespace emugl
