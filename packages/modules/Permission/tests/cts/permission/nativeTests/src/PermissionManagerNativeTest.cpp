/*
 * Copyright (C) 2020 The Android Open Source Project
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

// #define LOG_NDEBUG 0
#define LOG_TAG "PermissionManagerNativeTest"

#include <android/permission_manager.h>
#include <android-base/logging.h>
#include <gtest/gtest.h>

//-----------------------------------------------------------------
class PermissionManagerNativeTest : public ::testing::Test {

protected:
    PermissionManagerNativeTest() { }

    virtual ~PermissionManagerNativeTest() { }

    /* Test setup*/
    virtual void SetUp() { }

    /* Test tear down */
    virtual void TearDown() { }
};

//-------------------------------------------------------------------------------------------------
TEST_F(PermissionManagerNativeTest, testCheckPermission) {
    pid_t selfPid = ::getpid();
    uid_t selfUid = ::getuid();

    LOG(INFO) << "testCheckPermission: uid " << selfUid << ", pid" << selfPid;

    // Test is set up to force unroot by RootTargetPreparer, so we should be running as SHELL.
    // Check some permissions SHELL should definitely have or not have.
    int32_t result;
    EXPECT_EQ(APermissionManager_checkPermission("android.permission.DUMP",
                                                 selfPid, selfUid, &result),
              PERMISSION_MANAGER_STATUS_OK);
    EXPECT_EQ(result, PERMISSION_MANAGER_PERMISSION_GRANTED);

    EXPECT_EQ(APermissionManager_checkPermission("android.permission.MANAGE_USERS",
                                                 selfPid, selfUid, &result),
              PERMISSION_MANAGER_STATUS_OK);
    EXPECT_EQ(result, PERMISSION_MANAGER_PERMISSION_DENIED);

    EXPECT_EQ(APermissionManager_checkPermission("android.permission.NETWORK_STACK",
                                                 selfPid, selfUid, &result),
              PERMISSION_MANAGER_STATUS_OK);
    EXPECT_EQ(result, PERMISSION_MANAGER_PERMISSION_DENIED);
}
