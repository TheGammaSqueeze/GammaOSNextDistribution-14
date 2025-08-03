/*
 * Copyright (C) 2018 The Android Open Source Project
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

#include <string>

#include <fcntl.h>
#include <inttypes.h>
#include <limits.h>
#include <linux/inet_diag.h>
#include <linux/sock_diag.h>
#include <net/if.h>
#include <sys/socket.h>
#include <sys/types.h>
#include <unistd.h>

#include <gtest/gtest.h>

#include <cutils/qtaguid.h>
#include <processgroup/processgroup.h>

#include <android-base/stringprintf.h>
#include <android-base/strings.h>
#include <netdutils/NetNativeTestBase.h>

#include "bpf/BpfMap.h"
#include "bpf/BpfUtils.h"
#include "netd.h"

using android::base::Result;

namespace android {
namespace bpf {

// Use the upper limit of uid to avoid conflict with real app uids. We can't use UID_MAX because
// it's -1, which is INVALID_UID.
constexpr uid_t TEST_UID = UID_MAX - 1;
constexpr uint32_t TEST_TAG = 42;

class BpfBasicTest : public NetNativeTestBase {
  protected:
    BpfBasicTest() {}
};

TEST_F(BpfBasicTest, TestCgroupMounted) {
    std::string cg2_path;
    ASSERT_EQ(true, CgroupGetControllerPath(CGROUPV2_CONTROLLER_NAME, &cg2_path));
    ASSERT_EQ(0, access(cg2_path.c_str(), R_OK));
    ASSERT_EQ(0, access((cg2_path + "/cgroup.controllers").c_str(), R_OK));
}

TEST_F(BpfBasicTest, TestTagSocket) {
    BpfMap<uint64_t, UidTagValue> cookieTagMap(COOKIE_TAG_MAP_PATH);
    ASSERT_TRUE(cookieTagMap.isValid());
    int sock = socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0);
    ASSERT_LE(0, sock);
    uint64_t cookie = getSocketCookie(sock);
    ASSERT_NE(NONEXISTENT_COOKIE, cookie);
    ASSERT_EQ(0, qtaguid_tagSocket(sock, TEST_TAG, TEST_UID));
    Result<UidTagValue> tagResult = cookieTagMap.readValue(cookie);
    ASSERT_RESULT_OK(tagResult);
    ASSERT_EQ(TEST_UID, tagResult.value().uid);
    ASSERT_EQ(TEST_TAG, tagResult.value().tag);
    ASSERT_EQ(0, qtaguid_untagSocket(sock));
    tagResult = cookieTagMap.readValue(cookie);
    ASSERT_FALSE(tagResult.ok());
    ASSERT_EQ(ENOENT, tagResult.error().code());
}

TEST_F(BpfBasicTest, TestCloseSocketWithoutUntag) {
    BpfMap<uint64_t, UidTagValue> cookieTagMap(COOKIE_TAG_MAP_PATH);
    ASSERT_TRUE(cookieTagMap.isValid());
    int sock = socket(AF_INET6, SOCK_STREAM | SOCK_CLOEXEC, 0);
    ASSERT_LE(0, sock);
    uint64_t cookie = getSocketCookie(sock);
    ASSERT_NE(NONEXISTENT_COOKIE, cookie);
    ASSERT_EQ(0, qtaguid_tagSocket(sock, TEST_TAG, TEST_UID));
    Result<UidTagValue> tagResult = cookieTagMap.readValue(cookie);
    ASSERT_RESULT_OK(tagResult);
    ASSERT_EQ(TEST_UID, tagResult.value().uid);
    ASSERT_EQ(TEST_TAG, tagResult.value().tag);
    ASSERT_EQ(0, close(sock));
    // Check map periodically until sk destroy handler have done its job.
    for (int i = 0; i < 1000; i++) {
        usleep(5000);  // 5ms
        tagResult = cookieTagMap.readValue(cookie);
        if (!tagResult.ok()) {
            ASSERT_EQ(ENOENT, tagResult.error().code());
            return;
        }
    }
    FAIL() << "socket tag still exist after 5s";
}

}
}
