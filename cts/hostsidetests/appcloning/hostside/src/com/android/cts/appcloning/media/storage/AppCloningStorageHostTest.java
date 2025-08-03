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

package com.android.cts.appcloning.media.storage;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assume.assumeTrue;

import android.platform.test.annotations.LargeTest;

import com.android.cts.appcloning.AppCloningBaseHostTest;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;

@RunWith(DeviceJUnit4ClassRunner.class)
@LargeTest
public class AppCloningStorageHostTest extends AppCloningBaseHostTest {

    private static final String PKG_STATS = "com.android.cts.storagestatsapp";
    private static final String APK_STATS = "CtsAppCloningStorageStatsApp.apk";
    private static final String CLASS_STATS = "com.android.cts.storagestatsapp.StorageStatsTest";
    private static final String EXTERNAL_STORAGE_PATH = "/storage/emulated/%d/";
    private static final int CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS = 30000;
    private static final String CLONE_DIRECTORY_CREATION_FAILURE =
            "Failed to setup and user clone directories";

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AppCloningBaseHostTest.baseHostTeardown();
    }

    @Test
    public void testVerifyStatsExternalForClonedUser() throws Exception {
        assumeTrue(isAtLeastU(sDevice));
        installPackage(APK_STATS, "--user " + sCloneUserId);

        int mCloneUserIdInt = Integer.parseInt(sCloneUserId);
        // Check that the clone user directories have been created
        eventually(() -> sDevice.doesFileExist(
                String.format(EXTERNAL_STORAGE_PATH, mCloneUserIdInt), mCloneUserIdInt),
                CLONE_PROFILE_DIRECTORY_CREATION_TIMEOUT_MS,
                CLONE_DIRECTORY_CREATION_FAILURE);

        runDeviceTestAsUser(PKG_STATS, CLASS_STATS, "testVerifyStatsExternal",
                Integer.parseInt(sCloneUserId), new HashMap<>());
    }
}
