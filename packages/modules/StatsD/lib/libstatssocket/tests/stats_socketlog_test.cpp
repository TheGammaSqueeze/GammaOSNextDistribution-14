/*
 * Copyright (C) 2023 The Android Open Source Project
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

#include <gtest/gtest.h>

#include "stats_statsdsocketlog.h"
#include "utils.h"

TEST(StatsStatsdSocketLog, TestToSocketLossError) {
    using namespace android::os::statsdsocket;

    EXPECT_EQ(-EAGAIN, toSocketLossError(EAGAIN));
    EXPECT_EQ(STATS_SOCKET_LOSS_REPORTED__ERRORS__SOCKET_LOSS_ERROR_UNKNOWN,
              toSocketLossError(ERANGE));
}
