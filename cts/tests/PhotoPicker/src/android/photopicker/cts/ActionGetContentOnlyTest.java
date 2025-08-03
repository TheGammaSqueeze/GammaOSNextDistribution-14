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

import static android.photopicker.cts.util.PhotoPickerComponentUtils.GET_CONTENT_ACTIVITY_COMPONENT;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.createImagesAndGetUriAndPath;
import static android.photopicker.cts.util.PhotoPickerFilesUtils.deleteMedia;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.clearPackageData;
import static android.photopicker.cts.util.PhotoPickerPackageUtils.getDocumentsUiPackageName;
import static android.photopicker.cts.util.PhotoPickerUiUtils.SHORT_TIMEOUT;
import static android.photopicker.cts.util.PhotoPickerUiUtils.clickAndWait;
import static android.photopicker.cts.util.PhotoPickerUiUtils.findAndClickBrowse;
import static android.photopicker.cts.util.ResultsAssertionsUtils.assertReadOnlyAccess;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.content.ClipData;
import android.content.Intent;
import android.net.Uri;
import android.photopicker.cts.util.PhotoPickerComponentUtils;
import android.photopicker.cts.util.UiAssertionUtils;
import android.provider.MediaStore;
import android.util.Pair;

import androidx.test.uiautomator.UiObject;
import androidx.test.uiautomator.UiObjectNotFoundException;
import androidx.test.uiautomator.UiScrollable;
import androidx.test.uiautomator.UiSelector;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Photo Picker tests for PhotoPicker launched via {@link Intent#ACTION_GET_CONTENT} intent
 * exclusively.
 */
public class ActionGetContentOnlyTest extends PhotoPickerBaseTest {

    public static final String TAG = "ActionGetContentOnlyTest";

    private static String sDocumentsUiPackageName;
    private static int sGetContentTakeOverActivityAliasState;

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

        PhotoPickerComponentUtils.setState(GET_CONTENT_ACTIVITY_COMPONENT,
                sGetContentTakeOverActivityAliasState);
    }

    @Before
    public void setUp() throws Exception {
        super.setUp();

        sDocumentsUiPackageName = getDocumentsUiPackageName();
        sGetContentTakeOverActivityAliasState = PhotoPickerComponentUtils
                .enableAndGetOldState(GET_CONTENT_ACTIVITY_COMPONENT);
        clearPackageData(sDocumentsUiPackageName);
    }

    @Test
    public void testMimeTypeFilter() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("audio/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        // Should open documentsUi
        assertThatShowsDocumentsUiButtons();

        // We don't test the result of the picker here because the intention of the test is only to
        // test that DocumentsUi is opened.
    }

    @Test
    public void testExtraMimeTypeFilter() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        intent.setType("image/*");
        intent.putExtra(Intent.EXTRA_MIME_TYPES, new String[]{"video/*", "audio/*"});
        mActivity.startActivityForResult(intent, REQUEST_CODE);
        sDevice.waitForIdle();
        // Should open documentsUi
        assertThatShowsDocumentsUiButtons();

        // We don't test the result of the picker here because the intention of the test is only to
        // test that DocumentsUi is opened.
    }

    @Test
    public void testBrowse_singleSelect() throws Exception {
        final int itemCount = 1;
        List<Pair<Uri, String>> createdImagesData = createImagesAndGetUriAndPath(itemCount,
                mContext.getUserId(), /* isFavorite */ false);

        final List<String> fileNameList = new ArrayList<>();
        for (Pair<Uri, String> createdImageData: createdImagesData) {
            mUriList.add(createdImageData.first);
            fileNameList.add(createdImageData.second);
        }

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        findAndClickBrowse(sDevice);

        findAndClickFilesInDocumentsUi(fileNameList);

        final Uri uri = mActivity.getResult().data.getData();

        assertReadOnlyAccess(uri, mContext.getContentResolver());
    }

    @Test
    public void testBrowse_multiSelect() throws Exception {
        final int itemCount = 3;
        List<Pair<Uri, String>> createdImagesData = createImagesAndGetUriAndPath(itemCount,
                mContext.getUserId(), /* isFavorite */ false);

        final List<String> fileNameList = new ArrayList<>();
        for (Pair<Uri, String> createdImageData: createdImagesData) {
            mUriList.add(createdImageData.first);
            fileNameList.add(createdImageData.second);
        }

        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
        intent.setType("image/*");
        mActivity.startActivityForResult(intent, REQUEST_CODE);

        findAndClickBrowse(sDevice);

        findAndClickFilesInDocumentsUi(fileNameList);

        final ClipData clipData = mActivity.getResult().data.getClipData();
        final int count = clipData.getItemCount();
        assertThat(count).isEqualTo(itemCount);
        for (int i = 0; i < count; i++) {
            assertReadOnlyAccess(clipData.getItemAt(i).getUri(), mContext.getContentResolver());
        }
    }

    @Test
    public void testChooserIntent_mediaFilter() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);

        // Should open Picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    @Test
    public void testChooserIntent_nonMediaFilter() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);

        // Should open DocumentsUi
        assertThatShowsDocumentsUiButtons();
    }

    @Test
    public void testPickerSupportedFromDocumentsUi() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);

        findAndClickMediaIcon();

        // Should open Picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    @Test
    public void testPickerLaunchTabWithGetContent() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("*/*");
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_LAUNCH_TAB, MediaStore.PICK_IMAGES_TAB_ALBUMS);

        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);

        findAndClickMediaIcon();

        // Should open Picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    @Test
    public void testPickerAccentColorWithGetContent() throws Exception {
        final Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.putExtra(MediaStore.EXTRA_PICK_IMAGES_ACCENT_COLOR, 0xFFFF5A5F);

        mActivity.startActivityForResult(Intent.createChooser(intent, TAG), REQUEST_CODE);
        sDevice.waitForIdle();

        // Should open the picker
        UiAssertionUtils.assertThatShowsPickerUi(intent.getType());
    }

    private void findAndClickMediaIcon() throws Exception {
        final UiSelector appList = new UiSelector().resourceId(sDocumentsUiPackageName
                + ":id/apps_row");

        // Wait for the first app list item to appear
        assertWithMessage("Waiting for app list to appear in DocumentsUi").that(
                new UiObject(appList).waitForExists(SHORT_TIMEOUT)).isTrue();

        String photoPickerAppName = "Media";
        UiObject mediaButton = sDevice.findObject(new UiSelector().text(photoPickerAppName));
        if (!new UiScrollable(appList).setAsHorizontalList().scrollIntoView(mediaButton)) {
            // While solving an accessibility bug the app_label was modified from 'Media' to
            // 'Media picker' and after making the modification, since this test had the
            // hardcoded value for the name as 'Media' it started failing. After fixing this some
            // versions of the code became incompatible with this test and hence have modified
            // the code to work with both names.
            photoPickerAppName = "Media picker";
            mediaButton = sDevice.findObject(new UiSelector().text(photoPickerAppName));
        }
        assertWithMessage("Timed out waiting for " + photoPickerAppName
                + " app icon to appear")
                .that(new UiScrollable(appList).setAsHorizontalList().scrollIntoView(mediaButton))
                .isTrue();
        sDevice.waitForIdle();

        clickAndWait(sDevice, mediaButton);
    }

    private void assertThatShowsDocumentsUiButtons() {
        // Assert that "Recent files" header for DocumentsUi shows
        // Add a short timeout wait for DocumentsUi to show
        assertThat(new UiObject(new UiSelector().resourceId(sDocumentsUiPackageName
                + ":id/header_title")).waitForExists(SHORT_TIMEOUT)).isTrue();
    }

    private UiObject findSaveButton() {
        return new UiObject(new UiSelector().resourceId(
                        sDocumentsUiPackageName + ":id/container_save")
                .childSelector(new UiSelector().resourceId("android:id/button1")));
    }

    private void findAndClickFilesInDocumentsUi(List<String> fileNameList) throws Exception {
        final UiSelector docList = getDirectoryListSelector();
        for (String fileName : fileNameList) {
            findAndClickFileInDocumentsUi(docList, fileName);
        }
        findAndClickSelect();
    }

    private void findAndClickSelect() throws Exception {
        final UiObject selectButton = new UiObject(new UiSelector().resourceId(
                sDocumentsUiPackageName + ":id/action_menu_select"));
        clickAndWait(sDevice, selectButton);
    }

    private UiSelector getDirectoryListSelector() throws Exception {
        final UiSelector docList = new UiSelector().resourceId(sDocumentsUiPackageName
                + ":id/dir_list");

        // Wait for the first list item to appear
        assertWithMessage("First list item").that(
                new UiObject(docList.childSelector(new UiSelector()))
                        .waitForExists(SHORT_TIMEOUT)).isTrue();

        try {
            // Enforce to set the list mode
            // Because UiScrollable can't reach the real bottom (when WEB_LINKABLE_FILE item)
            // in grid mode when screen landscape mode
            clickAndWait(sDevice, new UiObject(new UiSelector().resourceId(sDocumentsUiPackageName
                    + ":id/sub_menu_list")));
        } catch (UiObjectNotFoundException ignored) {
            // Do nothing, already be in list mode.
        }
        return docList;
    }

    private void findAndClickFileInDocumentsUi(UiSelector docList, String fileName)
            throws Exception {

        // Repeat swipe gesture to find our item
        // (UiScrollable#scrollIntoView does not seem to work well with SwipeRefreshLayout)
        UiObject targetObject = new UiObject(docList.childSelector(new UiSelector()
                .textContains(fileName)));
        UiObject saveButton = findSaveButton();
        int stepLimit = 10;
        while (stepLimit-- > 0) {
            if (targetObject.exists()) {
                boolean targetObjectFullyVisible = !saveButton.exists()
                        || targetObject.getVisibleBounds().bottom
                        <= saveButton.getVisibleBounds().top;
                if (targetObjectFullyVisible) {
                    break;
                }
            }

            sDevice.swipe(/* startX= */ sDevice.getDisplayWidth() / 2,
                    /* startY= */ sDevice.getDisplayHeight() / 2,
                    /* endX= */ sDevice.getDisplayWidth() / 2,
                    /* endY= */ 0,
                    /* steps= */ 40);
        }

        targetObject.longClick();
    }
}
