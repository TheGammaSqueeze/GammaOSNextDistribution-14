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

package com.android.sdksandbox.cts.provider.dataisolationtest;

import android.content.Context;
import android.os.Bundle;
import android.os.Process;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;

public class DataIsolationTestSdkApiImpl extends IDataIsolationTestSdkApi.Stub {
    private final Context mContext;

    private static final String TAG = "SdkSandboxDataIsolationTestProvider";

    private static final String JAVA_FILE_PERMISSION_DENIED_MSG =
            "open failed: EACCES (Permission denied)";
    private static final String JAVA_FILE_NOT_FOUND_MSG =
            "open failed: ENOENT (No such file or directory)";
    private static final String JAVA_IS_A_DIRECTORY_ERROR_MSG =
            "open failed: EISDIR (Is a directory)";

    private static final String APP_PKG = "com.android.sdksandbox.cts.app";
    private static final String APP_2_PKG = "com.android.sdksandbox.cts.app2";

    private static final String CURRENT_USER_ID =
            String.valueOf(Process.myUserHandle().getUserId(Process.myUid()));

    public DataIsolationTestSdkApiImpl(Context sdkContext) {
        mContext = sdkContext;
    }

    @Override
    public void testSdkSandboxDataIsolation_SandboxCanAccessItsDirectory() {
        verifyDirectoryAccess(mContext.getDataDir().toString(), true);
    }

    @Override
    public void testSdkSandboxDataIsolation_CannotVerifyAppExistence() {
        // Check if the sandbox can check existence of any app through their data directories,
        // profiles or associated sandbox data directories.
        verifyDirectoryAccess("/data/user/" + CURRENT_USER_ID + "/" + APP_PKG, false);
        verifyDirectoryAccess("/data/user/" + CURRENT_USER_ID + "/" + APP_2_PKG, false);
        verifyDirectoryAccess("/data/user/" + CURRENT_USER_ID + "/does.not.exist", false);

        verifyDirectoryAccess("/data/misc/profiles/cur/" + CURRENT_USER_ID + "/" + APP_PKG, false);
        verifyDirectoryAccess(
                "/data/misc/profiles/cur/" + CURRENT_USER_ID + "/" + APP_2_PKG, false);
        verifyDirectoryAccess(
                "/data/misc/profiles/cur/" + CURRENT_USER_ID + "/does.not.exist", false);

        verifyDirectoryAccess(
                "/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(
                "/data/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist", false);
        verifyDirectoryAccess(
                "/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(
                "/data/misc_de/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist", false);
    }

    @Override
    public void testSdkSandboxDataIsolation_CannotVerifyOtherUserAppExistence(Bundle params) {
        final String otherUserId = params.getString("sandbox_isolation_user_id");

        String sandboxPackageDir1 = "/data/misc_ce/" + otherUserId + "/sdksandbox/" + APP_PKG;
        String sandboxPackageDir2 = "/data/misc_ce/" + otherUserId + "/sdksandbox/" + APP_2_PKG;

        // Check error message obtained when trying to access each of these packages.
        verifyDirectoryAccess(sandboxPackageDir1, false);
        verifyDirectoryAccess(sandboxPackageDir2, false);
    }

    @Override
    public void testSdkSandboxDataIsolation_CannotVerifyAcrossVolumes(Bundle params) {
        verifyDirectoryAccess(mContext.getApplicationContext().getDataDir().toString(), true);

        String uuid = params.getString("sandbox_isolation_uuid");
        String volumePath = "/mnt/expand/" + uuid;

        verifyDirectoryAccess(volumePath + "/user/" + CURRENT_USER_ID + "/" + APP_2_PKG, false);
        verifyDirectoryAccess(volumePath + "/user/" + CURRENT_USER_ID + "/does.not.exist", false);
        verifyDirectoryAccess(
                volumePath + "/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(
                volumePath + "/misc_ce/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist", false);
        verifyDirectoryAccess(
                volumePath + "/misc_de/" + CURRENT_USER_ID + "/sdksandbox/" + APP_2_PKG, false);
        verifyDirectoryAccess(
                volumePath + "/misc_de/" + CURRENT_USER_ID + "/sdksandbox/does.not.exist", false);
    }

    private void verifyDirectoryAccess(String path, boolean shouldBeAccessible) {
        File file = new File(path);
        try {
            new FileInputStream(file);
        } catch (FileNotFoundException exception) {
            String exceptionMsg = exception.getMessage();
            if (shouldBeAccessible) {
                if (!exceptionMsg.contains(JAVA_IS_A_DIRECTORY_ERROR_MSG)) {
                    throw new IllegalStateException(
                            path + " should be accessible, but received error: " + exceptionMsg);
                }
            } else if (!exceptionMsg.contains(JAVA_FILE_NOT_FOUND_MSG)
                    || exceptionMsg.contains(JAVA_FILE_PERMISSION_DENIED_MSG)
                    || exceptionMsg.contains(JAVA_IS_A_DIRECTORY_ERROR_MSG)) {
                throw new IllegalStateException(
                        "Accessing "
                                + path
                                + " should have shown ENOENT error, but received error: "
                                + exceptionMsg);
            }
        }
    }
}
