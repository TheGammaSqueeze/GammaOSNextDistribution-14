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

package android.photopicker.cts;

import static android.photopicker.cts.PhotoPickerCloudUtils.disableDeviceConfigSync;
import static android.photopicker.cts.util.PhotoPickerComponentUtils.PICKER_SETTINGS_ACTIVITY_COMPONENT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.REGEX_PACKAGE_NAME;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.isPhotoPickerVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActionBarIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsActivityIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsCloudProviderOptionIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsDescriptionIsVisible;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsFragmentContainerExists;
import static android.photopicker.cts.util.PhotoPickerUiUtils.verifySettingsTitleIsVisible;

import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.UserHandle;
import android.photopicker.cts.util.PhotoPickerComponentUtils;
import android.photopicker.cts.util.PhotoPickerUiUtils;
import android.provider.MediaStore;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.UiObject;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.Assume;
import org.junit.Before;
import org.junit.ClassRule;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Photo Picker tests for settings activity launched from PhotoPickerActivity or intent.
 */
@RunWith(BedsteadJUnit4.class)
public class PhotoPickerSettingsTest extends PhotoPickerBaseTest {
    private static final String TAG = PhotoPickerSettingsTest.class.getSimpleName();
    private static final String EXTRA_TAB_USER_ID = "user_id";
    private static final String TAB_CONTAINER_RESOURCE_ID =
            REGEX_PACKAGE_NAME + ":id/tab_container";
    private static final String TAB_LAYOUT_RESOURCE_ID = REGEX_PACKAGE_NAME + ":id/tabs";
    private static final String PERSONAL_TAB_TITLE_ENGLISH = "Personal";
    private static final String WORK_TAB_TITLE_ENGLISH = "Work";
    private static final String DEFAULT_APP_LABEL = "Photo Picker Device Tests";
    private static int sPhotoPickerSettingsActivityState;
    private Intent mSettingsIntent;
    @Nullable
    private static DeviceStatePreserver sDeviceStatePreserver;

    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sPhotoPickerSettingsActivityState = PhotoPickerComponentUtils
                .enableAndGetOldState(PICKER_SETTINGS_ACTIVITY_COMPONENT);

        mSettingsIntent = new Intent(MediaStore.ACTION_PICK_IMAGES_SETTINGS);

        // Only enable cloud media in S+ because R cannot use Device Config APIs.
        if (SdkLevel.isAtLeastS()) {
            sDeviceStatePreserver = new DeviceStatePreserver(sDevice);
            sDeviceStatePreserver.saveCurrentCloudProviderState();
            disableDeviceConfigSync();

            // Enable Settings menu item in PhotoPickerActivity's overflow menu.
            PhotoPickerCloudUtils.enableCloudMediaAndSetAllowedCloudProviders(
                    /* allowedCloudProviders */ sTargetPackageName);
        }
    }

    @After
    public void tearDown() throws Exception {
        if (mActivity != null) {
            mActivity.finish();
        }

        PhotoPickerComponentUtils.setState(PICKER_SETTINGS_ACTIVITY_COMPONENT,
                sPhotoPickerSettingsActivityState);

        // Reset CloudMedia configs.
        if (SdkLevel.isAtLeastS() && sDeviceStatePreserver != null) {
            sDeviceStatePreserver.restoreCloudProviderState();
        }
    }

    @Test
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testSettingsLaunchFromOverflowMenu_WorkDisabled() throws Exception {
        String cmpAppLabel = getCmpAppLabel();

        // Launch PhotoPickerActivity.
        final Intent intent = new Intent(MediaStore.ACTION_PICK_IMAGES);
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        Assume.assumeTrue("Assume photo picker activity is visible", isPhotoPickerVisible());

        // Click on the Settings menu item in the overflow menu.
        final UiObject settingsMenuItem = PhotoPickerUiUtils.findSettingsOverflowMenuItem(sDevice);
        PhotoPickerUiUtils.clickAndWait(sDevice, settingsMenuItem);

        // Verify PhotoPickerSettingsActivity is launched and visible.
        verifySettingsActivityIsVisible();
        verifySettingsActionBarIsVisible();
        verifySettingsTitleIsVisible();
        verifySettingsDescriptionIsVisible();
        verifySettingsFragmentContainerExists();
        verifySettingsCloudProviderOptionIsVisible(cmpAppLabel);

        // Verify Tab container (to switch profiles) is not visible since Work profile is disabled.
        verifySettingsTabContainerIsNotVisible();
    }

    @Test
    @LargeTest
    @RequireRunOnWorkProfile
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testSettingsLaunchedInPersonalProfile_WorkEnabled() throws Exception {
        mSettingsIntent.putExtra(EXTRA_TAB_USER_ID, sDeviceState.initialUser().id());
        launchSettingsActivityWithRetry(/* retryCount */ 3, /* backoffSeedInMillis */ 500);
        verifySettingsActivityIsVisible();

        verifySettingsTabContainerIsVisible();
        assertWithMessage("Personal tab is not selected")
                .that(PhotoPickerUiUtils.isSelectedTabTitle(
                        PERSONAL_TAB_TITLE_ENGLISH, TAB_LAYOUT_RESOURCE_ID, sDevice))
                .isTrue();
    }

    @Test
    @LargeTest
    @RequireRunOnWorkProfile
    @SdkSuppress(minSdkVersion = Build.VERSION_CODES.S)
    public void testSettingsLaunchedInWorkProfile() throws Exception {
        mSettingsIntent.putExtra(EXTRA_TAB_USER_ID, UserHandle.myUserId());
        launchSettingsActivityWithRetry(/* retryCount */ 3, /* backoffSeedInMillis */ 500);
        verifySettingsActivityIsVisible();

        verifySettingsTabContainerIsVisible();
        assertWithMessage("Work tab is not selected")
                .that(PhotoPickerUiUtils.isSelectedTabTitle(
                        WORK_TAB_TITLE_ENGLISH, TAB_LAYOUT_RESOURCE_ID, sDevice)).isTrue();
    }

    private static void verifySettingsTabContainerIsVisible() {
        assertWithMessage("Timed out waiting for settings profile select tab container to appear")
                .that(PhotoPickerUiUtils.findObject(
                        TAB_CONTAINER_RESOURCE_ID, sDevice).waitForExists(SHORT_TIMEOUT))
                .isTrue();
    }

    private static void verifySettingsTabContainerIsNotVisible() {
        assertWithMessage("Found the settings profile select tab container")
                .that(PhotoPickerUiUtils.findObject(
                        TAB_CONTAINER_RESOURCE_ID, sDevice).waitForExists(SHORT_TIMEOUT))
                .isFalse();
    }
    @Test
    // This test is required for API coverage in Android R
    public void testSettingsLaunchFromIntent() throws InterruptedException {
        // Launch PhotoPickerSettingsActivity.
        launchSettingsActivityWithRetry(/* retryCount */ 3, /* backoffSeedInMillis */ 500);

        // Verify PhotoPickerSettingsActivity is launched and visible.
        verifySettingsActivityIsVisible();
        verifySettingsActionBarIsVisible();
        verifySettingsTitleIsVisible();
        verifySettingsDescriptionIsVisible();
        verifySettingsFragmentContainerExists();
    }


    private void launchSettingsActivityWithRetry(long maxRetries, long backoffSeedInMillis)
            throws InterruptedException {
        for (int attempt = 0; attempt <= maxRetries; attempt++) {
            if (attempt > 0) {
                // If the Settings Activity component has been recently enabled, it may take some
                // time for the resolver to resolve the intent to the right activity.
                long backoffTimeInMillis = backoffSeedInMillis * (2 ^ (attempt - 1));
                Log.e(TAG, "Retry launching activity for " + mSettingsIntent
                        + " after backoff " + backoffTimeInMillis);
                Thread.sleep(backoffTimeInMillis);
            }

            try {
                mActivity.startActivity(mSettingsIntent);
                sDevice.waitForIdle();
                return;
            } catch (ActivityNotFoundException e) {
                Log.e(TAG, "Activity not found for intent " + mSettingsIntent);
            }
        }

        Log.e(TAG, "Intent " + mSettingsIntent + " does not resolve to any component.");
        throw new AssertionError("Cannot find activity for intent " + mSettingsIntent);
    }

    @NonNull
    private String getCmpAppLabel() {
        PackageManager pm = mContext.getPackageManager();
        {
            try {
                ApplicationInfo applicationInfo = pm.getApplicationInfo(sTargetPackageName, 0);
                return (String) pm.getApplicationLabel(applicationInfo);
            } catch (PackageManager.NameNotFoundException e) {
                return DEFAULT_APP_LABEL;
            }
        }
    }
}
