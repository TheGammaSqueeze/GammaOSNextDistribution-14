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
package com.android.cts.host.blob;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.AfterClassWithInfo;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;
import com.android.tradefed.util.Pair;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Map;

@RunWith(DeviceJUnit4ClassRunner.class)
public class BlobStoreMultiUserTest extends BaseBlobStoreHostTest {
    private static final String TEST_CLASS = TARGET_PKG + ".DataCleanupTest";

    private static final String PERM_ACCESS_BLOBS_ACROSS_USERS =
            "android.permission.ACCESS_BLOBS_ACROSS_USERS";

    private static int mPrimaryUserId;
    private static int mSecondaryUserId;

    @BeforeClassWithInfo
    public static void setUpClass(TestInformation testInfo) throws Exception {
        assumeTrue("Multi-user is not supported on this device",
                isMultiUserSupported(testInfo.getDevice()));

        mPrimaryUserId = testInfo.getDevice().getPrimaryUserId();
        mSecondaryUserId = testInfo.getDevice().createUser("Test_User");
        assertThat(testInfo.getDevice().startUser(mSecondaryUserId)).isTrue();
    }

    @Before
    public void setUp() throws Exception {
        for (String apk : new String[] {TARGET_APK, TARGET_APK_DEV}) {
            installPackageAsUser(apk, true /* grantPermissions */, mPrimaryUserId, "-t");
            installPackageAsUser(apk, true /* grantPermissions */, mSecondaryUserId, "-t");
        }
        // We would verify access with and without the app holding the assist role, so we don't
        // want the ACCESS_BLOBS_ACROSS_USERS permission to be granted by default.
        installPackageAsUser(TARGET_APK_ASSIST, false /* grantPermissions */, mPrimaryUserId);
        installPackageAsUser(TARGET_APK_ASSIST, false /* grantPermissions */, mSecondaryUserId);
        // Explicitly revoke the permission, in order to deal with
        // http://b/233710271 which was causing the permission to be pre-granted.
        revokePermission(TARGET_PKG_ASSIST, PERM_ACCESS_BLOBS_ACROSS_USERS, mPrimaryUserId);
        revokePermission(TARGET_PKG_ASSIST, PERM_ACCESS_BLOBS_ACROSS_USERS, mSecondaryUserId);
    }

    @AfterClassWithInfo
    public static void tearDownClass(TestInformation testInfo) throws Exception {
        if (mSecondaryUserId > 0) {
            testInfo.getDevice().removeUser(mSecondaryUserId);
        }
    }

    @Test
    public void testCreateAndOpenSession() throws Exception {
        // Create a session.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testCreateSession",
                mPrimaryUserId);
        final Map<String, String> args = createArgsFromLastTestRun();
        // Verify that previously created session can be accessed.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenSession", args,
                mPrimaryUserId);
        // verify that previously created session cannot be accessed from another user.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenSession_shouldThrow", args,
                mSecondaryUserId);
    }

    @Test
    public void testCommitAndOpenBlob() throws Exception {
        Map<String, String> args = createArgs(Pair.create(KEY_ALLOW_PUBLIC, String.valueOf(1)));
        // Commit a blob.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testCommitBlob", args,
                mPrimaryUserId);
        args = createArgsFromLastTestRun();
        // Verify that previously committed blob can be accessed.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob", args,
                mPrimaryUserId);
        // Verify that previously committed blob cannot be access from another user.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob_shouldThrow", args,
                mSecondaryUserId);
    }

    @Test
    public void testBlobAccessAcrossUsers_withTestOnlyApp() throws Exception {
        Map<String, String> args = createArgs(Pair.create(KEY_ALLOW_PUBLIC, String.valueOf(1)));
        // Commit a blob.
        runDeviceTestAsUser(TARGET_PKG_DEV, TEST_CLASS, "testCommitBlob", args,
                mPrimaryUserId);
        Map<String, String> argsFromLastTestRun = createArgsFromLastTestRun();
        // Verify that previously committed blob can be accessed.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob", argsFromLastTestRun,
                mPrimaryUserId);
        // Verify that previously committed blob cannot be access from another user.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob_shouldThrow", argsFromLastTestRun,
                mSecondaryUserId);

        // Verify that previously committed blob can be accessed from another user holding
        // a priv permission.
        runDeviceTestAsUser(TARGET_PKG_DEV, TEST_CLASS, "testOpenBlob", argsFromLastTestRun,
                mSecondaryUserId);
    }

    @Test
    public void testBlobAccessAcrossUsers_withAssistRole() throws Exception {
        // TODO: make it same signature
        Map<String, String> args = createArgs(Pair.create(KEY_ALLOW_SAME_SIGNATURE,
                String.valueOf(1)));
        try {
            addAssistRoleHolder(TARGET_PKG_ASSIST, mPrimaryUserId);
            // Commit a blob.
            runDeviceTestAsUser(TARGET_PKG_ASSIST, TEST_CLASS, "testCommitBlob", args,
                    mPrimaryUserId);
            Map<String, String> argsFromLastTestRun = createArgsFromLastTestRun();
            // Verify that previously committed blob cannot be access from another user.
            runDeviceTestAsUser(TARGET_PKG_ASSIST, TEST_CLASS, "testOpenBlob_shouldThrow",
                    argsFromLastTestRun, mSecondaryUserId);

            // Verify that previously committed blob can be accessed from another user holding
            // a priv permission.
            try {
                addAssistRoleHolder(TARGET_PKG_ASSIST, mSecondaryUserId);
                runDeviceTestAsUser(TARGET_PKG_ASSIST, TEST_CLASS, "testOpenBlob",
                        argsFromLastTestRun, mSecondaryUserId);
                runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob_shouldThrow",
                        argsFromLastTestRun, mSecondaryUserId);
            } finally {
                removeAssistRoleHolder(TARGET_PKG_ASSIST, mSecondaryUserId);
            }
        } finally {
            removeAssistRoleHolder(TARGET_PKG_ASSIST, mPrimaryUserId);
        }
    }

    @Test
    public void testBlobAccessAcrossUsers_recommit() throws Exception {
        Map<String, String> args = createArgs(Pair.create(KEY_ALLOW_PUBLIC, String.valueOf(1)));
        // Commit a blob.
        runDeviceTestAsUser(TARGET_PKG_DEV, TEST_CLASS, "testCommitBlob", args,
                mPrimaryUserId);
        Map<String, String> argsFromLastTestRun = createArgsFromLastTestRun();
        // Verify that previously committed blob cannot be access from another user.
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob_shouldThrow", argsFromLastTestRun,
                mSecondaryUserId);

        // Recommit the blob on another user
        argsFromLastTestRun.putAll(args);
        runDeviceTestAsUser(TARGET_PKG_DEV, TEST_CLASS, "testRecommitBlob", argsFromLastTestRun,
                mSecondaryUserId);
        // Any package on another user should be able to access the blob
        runDeviceTestAsUser(TARGET_PKG, TEST_CLASS, "testOpenBlob", argsFromLastTestRun,
                mSecondaryUserId);
    }

    @Test
    public void testBlobAccessAcrossUsers_withDifferentApps() throws Exception {
        Map<String, String> args = createArgs(Pair.create(KEY_ALLOW_PUBLIC,
                String.valueOf(1)));
        try {
            addAssistRoleHolder(TARGET_PKG_ASSIST, mPrimaryUserId);
            // Commit a blob.
            runDeviceTestAsUser(TARGET_PKG_ASSIST, TEST_CLASS, "testCommitBlob", args,
                    mPrimaryUserId);
            Map<String, String> argsFromLastTestRun = createArgsFromLastTestRun();
            // Verify that previously committed blob cannot be access from another user.
            runDeviceTestAsUser(TARGET_PKG_ASSIST, TEST_CLASS, "testOpenBlob_shouldThrow",
                    argsFromLastTestRun, mSecondaryUserId);

            // Verify that previously committed blob can be accessed from another user holding
            // a priv permission.
            runDeviceTestAsUser(TARGET_PKG_DEV, TEST_CLASS, "testOpenBlob", argsFromLastTestRun,
                    mSecondaryUserId);
        } finally {
            removeAssistRoleHolder(TARGET_PKG_ASSIST, mPrimaryUserId);
        }
    }
}
