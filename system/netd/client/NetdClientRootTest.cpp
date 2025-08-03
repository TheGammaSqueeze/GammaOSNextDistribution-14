/*
 * Copyright (C) 2022 The Android Open Source Project
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

#include <android-base/unique_fd.h>
#include <gtest/gtest.h>

#include "NetdClient.h"
#include "android/net/INetd.h"
#include "netid_client.h"
#include "test_utils.h"

constexpr int TEST_UID1 = 99999;

TEST(NetdClientTest, setSocketToInvalidNetwork) {
    const android::base::unique_fd s(socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0));
    ASSERT_LE(0, s);

    unsigned netId = NETID_UNSET;
    const ScopedUidChange scopedUidChange(TEST_UID1);
    EXPECT_EQ(-EACCES, setNetworkForSocket(android::net::INetd::LOCAL_NET_ID, s));
    EXPECT_EQ(0, getNetworkForSocket(&netId, s));
    EXPECT_EQ(static_cast<unsigned>(NETID_UNSET), netId);
}
