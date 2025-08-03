/*
 * Copyright (C) 2021 The Android Open Source Project
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

import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUris;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAddButton;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findItemList;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findProfileButton;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertPickerUriFormat;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertRedactedReadOnlyAccess;
import static android.provider.MediaStore.ACTION_PICK_IMAGES;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.provider.MediaStore;

import androidx.test.filters.LargeTest;
import androidx.test.filters.SdkSuppress;
import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiSelector;

import com.android.bedstead.harrier.BedsteadJUnit4;
import com.android.bedstead.harrier.DeviceState;
import com.android.bedstead.harrier.annotations.EnsureHasWorkProfile;
import com.android.bedstead.harrier.annotations.RequireRunOnWorkProfile;
import com.android.modules.utils.build.SdkLevel;

import org.junit.After;
import org.junit.ClassRule;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker Device only tests for cross profile interaction flows.
 */
@RunWith(BedsteadJUnit4.class)
@LargeTest
public class PhotoPickerCrossProfileTest extends PhotoPickerBaseTest {
    @ClassRule @Rule
    public static final DeviceState sDeviceState = new DeviceState();

    private List<Uri> mUriList = new ArrayList<>();

    @After
    public void tearDown() throws Exception {
        for (Uri uri : mUriList) {
            deleteMedia(uri, mContext);
        }
        mUriList.clear();
        if (mActivity != null) {
            mActivity.finish();
        }
    }

    /**
     * ACTION_PICK_IMAGES is allowlisted by default from work to personal. This got allowlisted
     * in a platform code change and is available Android T onwards.
     */
    @Test
    @RequireRunOnWorkProfile
    @SdkSuppress(minSdkVersion = 32, codeName = "T")
    public void testWorkApp_canAccessPersonalProfileContents() throws Exception {
        final int primaryUserId;
        if (SdkLevel.isAtLeastU()) {
            primaryUserId = sDeviceState.initialUser().id();
        } else {
            primaryUserId = sDeviceState.primaryUser().id();
        }

        final int imageCount = 2;
        mUriList.addAll(createImagesAndGetUris(imageCount, primaryUserId));

        Intent intent = new Intent(ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, imageCount);
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Click the profile button to change to personal profile
        final UiObject profileButton = findProfileButton();
        profileButton.click();
        sDevice.waitForIdle();

        final List<UiObject> itemList = findItemList(imageCount);
        final int itemCount = itemList.size();
        assertThat(itemCount).isEqualTo(imageCount);
        for (int i = 0; i < itemCount; i++) {
            final UiObject item = itemList.get(i);
            item.click();
            sDevice.waitForIdle();
        }

        final UiObject addButton = findAddButton();
        addButton.click();
        sDevice.waitForIdle();

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(imageCount);
        for (int i = 0; i < count; i++) {
            Uri uri = clipData.getItemAt(i).getUri();
            assertPickerUriFormat(ACTION_PICK_IMAGES, uri, primaryUserId);
            assertRedactedReadOnlyAccess(uri);
        }
    }

    /**
     * ACTION_PICK_IMAGES is allowlisted by default from work to personal. This got allowlisted
     * in a platform code change and is available Android T onwards. Before that it needs to be
     * explicitly allowlisted by the device admin.
     */
    @Test
    @RequireRunOnWorkProfile
    @SdkSuppress(maxSdkVersion = 31, codeName = "S")
    public void testWorkApp_cannotAccessPersonalProfile_beforeT() throws Exception {
        assertBlockedByAdmin(/* isInvokedFromWorkProfile */ true);
    }

    /**
     * ACTION_PICK_IMAGES is allowlisted by default from work to personal only (not vice-a-versa)
     */
    @Test
    @EnsureHasWorkProfile
    public void testPersonalApp_cannotAccessWorkProfile_default() throws Exception {
        assertBlockedByAdmin(/* isInvokedFromWorkProfile */ false);
    }

    private void assertBlockedByAdmin(boolean isInvokedFromWorkProfile) throws Exception {
        Intent intent = new Intent(ACTION_PICK_IMAGES);
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_MAX, MediaStore.getPickImagesMaxLimit());
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        // Click the profile button to change to work profile
        final UiObject profileButton = findProfileButton();
        profileButton.click();
        sDevice.waitForIdle();

        assertBlockedByAdminDialog(isInvokedFromWorkProfile);
    }

    private void assertBlockedByAdminDialog(boolean isInvokedFromWorkProfile) {
        final String dialogTitle = "Blocked by your admin";
        assertWithMessage("Timed out while waiting for blocked by admin dialog to appear")
                .that(new UiObject(new UiSelector().textContains(dialogTitle))
                        .waitForExists(SHORT_TIMEOUT))
                .isTrue();

        final String dialogDescription;
        if (isInvokedFromWorkProfile) {
            dialogDescription = "Accessing personal data from a work app is not permitted";
        } else {
            dialogDescription = "Accessing work data from a personal app is not permitted";
        }
        assertWithMessage("Blocked by admin description is not as expected")
                .that(new UiObject(new UiSelector().textContains(dialogDescription)).exists())
                .isTrue();
    }
}
