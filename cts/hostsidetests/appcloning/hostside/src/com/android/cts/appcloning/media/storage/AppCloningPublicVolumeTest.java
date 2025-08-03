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

import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.LargeTest;

import com.android.cts.appcloning.AppCloningBaseHostTest;
import com.android.tradefed.invoker.TestInformation;
import com.android.tradefed.testtype.DeviceJUnit4ClassRunner;
import com.android.tradefed.testtype.junit4.BeforeClassWithInfo;

import org.junit.AfterClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.HashMap;
import java.util.Map;

/**
 * Test Public SdCard Volume access for Clone Profile
 */
@RunWith(DeviceJUnit4ClassRunner.class)
@AppModeFull
@LargeTest
public class AppCloningPublicVolumeTest extends AppCloningBaseHostTest{

    private static final String IMAGE_NAME_TO_BE_CREATED_KEY = "imageNameToBeCreated";
    private static final String IMAGE_NAME_TO_BE_DISPLAYED_KEY = "imageNameToBeDisplayed";
    private static final String PUBLIC_SD_CARD_VOLUME_KEY = "publicSdCardVol";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY =
            "imageNameToBeVerifiedInOwnerProfile";
    private static final String IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY =
            "imageNameToBeVerifiedInCloneProfile";
    private static final String CLONE_USER_ID = "cloneUserId";

    @BeforeClassWithInfo
    public static void beforeClassWithDevice(TestInformation testInfo) throws Exception {
        assertThat(testInfo.getDevice()).isNotNull();
        AppCloningBaseHostTest.baseHostSetup(testInfo.getDevice());
        createSDCardVirtualDisk();
    }

    @AfterClass
    public static void afterClass() throws Exception {
        AppCloningBaseHostTest.baseHostTeardown();
        removeVirtualDisk();
    }

    @Test
    public void testCrossUserMediaAccessInPublicSdCard() throws Exception {
        assumeTrue(isAtLeastU(sDevice));

        // Install the app in both the user spaces
        installPackage(APP_A, "--user all");

        int currentUserId = getCurrentUserId();

        // Run save image test in owner user space
        Map<String, String> ownerArgs = new HashMap<>();
        ownerArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdOwnerProfileImage");
        ownerArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "owner_profile_image");
        ownerArgs.put(PUBLIC_SD_CARD_VOLUME_KEY, sPublicSdCardVol);

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToPublicSdCard",
                currentUserId, ownerArgs);

        // Run save image test in clone user space
        Map<String, String> cloneArgs = new HashMap<>();
        cloneArgs.put(IMAGE_NAME_TO_BE_DISPLAYED_KEY, "WeirdCloneProfileImage");
        cloneArgs.put(IMAGE_NAME_TO_BE_CREATED_KEY, "clone_profile_image");
        cloneArgs.put(PUBLIC_SD_CARD_VOLUME_KEY, sPublicSdCardVol);

        runDeviceTestAsUserInPkgA("testMediaStoreManager_writeImageToPublicSdCard",
                Integer.valueOf(sCloneUserId), cloneArgs);

        // Run cross user access test
        Map<String, String> args = new HashMap<>();
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_OWNER_PROFILE_KEY, "WeirdOwnerProfileImage");
        args.put(IMAGE_NAME_TO_BE_VERIFIED_IN_CLONE_PROFILE_KEY, "WeirdCloneProfileImage");
        args.put(CLONE_USER_ID, sCloneUserId);
        args.put(PUBLIC_SD_CARD_VOLUME_KEY, sPublicSdCardVol);

        // From owner user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInPublicSdCard", currentUserId, args);

        // From clone user space
        runDeviceTestAsUserInPkgA(
                "testMediaStoreManager_verifyCrossUserImagesInPublicSdCard",
                Integer.valueOf(sCloneUserId), args);
    }
}
