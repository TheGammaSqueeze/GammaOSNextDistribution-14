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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapFolderElementTest {
    private static final boolean TEST_HAS_SMS_MMS_CONTENT = true;
    private static final boolean TEST_HAS_IM_CONTENT = true;
    private static final boolean TEST_HAS_EMAIL_CONTENT = true;
    private static final boolean TEST_IGNORE = true;

    private static final String TEST_SMS_MMS_FOLDER_NAME = "smsmms";
    private static final String TEST_IM_FOLDER_NAME = "im";
    private static final String TEST_EMAIL_FOLDER_NAME = "email";
    private static final String TEST_TELECOM_FOLDER_NAME = "telecom";
    private static final String TEST_MSG_FOLDER_NAME = "msg";
    private static final String TEST_PLACEHOLDER_FOLDER_NAME = "placeholder";

    private static final long TEST_ROOT_FOLDER_ID = 1;
    private static final long TEST_PARENT_FOLDER_ID = 2;
    private static final long TEST_FOLDER_ID = 3;
    private static final long TEST_IM_FOLDER_ID = 4;
    private static final long TEST_EMAIL_FOLDER_ID = 5;
    private static final long TEST_PLACEHOLDER_ID = 6;

    private static final String TEST_FOLDER_NAME = "test";
    private static final String TEST_PARENT_FOLDER_NAME = "parent";
    private static final String TEST_ROOT_FOLDER_NAME = "root";

    private final BluetoothMapFolderElement mRootFolderElement =
            new BluetoothMapFolderElement(TEST_ROOT_FOLDER_NAME, null);

    private BluetoothMapFolderElement mParentFolderElement;
    private BluetoothMapFolderElement mTestFolderElement;


    @Before
    public void setUp() throws Exception {
        mRootFolderElement.setFolderId(TEST_ROOT_FOLDER_ID);
        mRootFolderElement.addFolder(TEST_PARENT_FOLDER_NAME);

        mParentFolderElement = mRootFolderElement.getSubFolder(TEST_PARENT_FOLDER_NAME);
        mParentFolderElement.setFolderId(TEST_PARENT_FOLDER_ID);
        mParentFolderElement.addFolder(TEST_FOLDER_NAME);

        mTestFolderElement = mParentFolderElement.getSubFolder(TEST_FOLDER_NAME);
        mTestFolderElement.setFolderId(TEST_FOLDER_ID);
        mTestFolderElement.setIgnore(TEST_IGNORE);
        mTestFolderElement.setHasSmsMmsContent(TEST_HAS_SMS_MMS_CONTENT);
        mTestFolderElement.setHasEmailContent(TEST_HAS_EMAIL_CONTENT);
        mTestFolderElement.setHasImContent(TEST_HAS_IM_CONTENT);
    }


    @Test
    public void getters() {
        assertThat(mTestFolderElement.shouldIgnore()).isEqualTo(TEST_IGNORE);
        assertThat(mTestFolderElement.getFolderId()).isEqualTo(TEST_FOLDER_ID);
        assertThat(mTestFolderElement.hasSmsMmsContent()).isEqualTo(TEST_HAS_SMS_MMS_CONTENT);
        assertThat(mTestFolderElement.hasEmailContent()).isEqualTo(TEST_HAS_EMAIL_CONTENT);
        assertThat(mTestFolderElement.hasImContent()).isEqualTo(TEST_HAS_IM_CONTENT);
    }

    @Test
    public void getFullPath() {
        assertThat(mTestFolderElement.getFullPath()).isEqualTo(
                String.format("%s/%s", TEST_PARENT_FOLDER_NAME, TEST_FOLDER_NAME));
    }

    @Test
    public void getRoot() {
        assertThat(mTestFolderElement.getRoot()).isEqualTo(mRootFolderElement);
    }

    @Test
    public void addFolders() {
        mTestFolderElement.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        mTestFolderElement.addImFolder(TEST_IM_FOLDER_NAME, TEST_IM_FOLDER_ID);
        mTestFolderElement.addEmailFolder(TEST_EMAIL_FOLDER_NAME, TEST_EMAIL_FOLDER_ID);

        assertThat(mTestFolderElement.getSubFolder(TEST_SMS_MMS_FOLDER_NAME).getName()).isEqualTo(
                TEST_SMS_MMS_FOLDER_NAME);
        assertThat(mTestFolderElement.getSubFolder(TEST_IM_FOLDER_NAME).getName()).isEqualTo(
                TEST_IM_FOLDER_NAME);
        assertThat(mTestFolderElement.getSubFolder(TEST_EMAIL_FOLDER_NAME).getName()).isEqualTo(
                TEST_EMAIL_FOLDER_NAME);

        mTestFolderElement.addFolder(TEST_SMS_MMS_FOLDER_NAME);
        assertThat(mTestFolderElement.getSubFolderCount()).isEqualTo(3);
    }

    @Test
    public void getFolderById() {
        assertThat(mTestFolderElement.getFolderById(TEST_FOLDER_ID)).isEqualTo(mTestFolderElement);
        assertThat(mRootFolderElement.getFolderById(TEST_ROOT_FOLDER_ID)).isEqualTo(
                mRootFolderElement);
        assertThat(BluetoothMapFolderElement.getFolderById(TEST_FOLDER_ID, null)).isNull();
        assertThat(BluetoothMapFolderElement.getFolderById(TEST_PLACEHOLDER_ID,
                mTestFolderElement)).isNull();
    }

    @Test
    public void getFolderByName() {
        mRootFolderElement.addFolder(TEST_TELECOM_FOLDER_NAME);
        mRootFolderElement.getSubFolder(TEST_TELECOM_FOLDER_NAME).addFolder(TEST_MSG_FOLDER_NAME);
        BluetoothMapFolderElement placeholderFolderElement = mRootFolderElement.getSubFolder(
                TEST_TELECOM_FOLDER_NAME).getSubFolder(TEST_MSG_FOLDER_NAME).addFolder(
                TEST_PLACEHOLDER_FOLDER_NAME);
        assertThat(mRootFolderElement.getFolderByName(TEST_PLACEHOLDER_FOLDER_NAME)).isNull();
        placeholderFolderElement.setFolderId(TEST_PLACEHOLDER_ID);
        assertThat(mRootFolderElement.getFolderByName(TEST_PLACEHOLDER_FOLDER_NAME)).isEqualTo(
                placeholderFolderElement);
    }

    @Test
    public void compareTo_withNull_returnsOne() {
        assertThat(mTestFolderElement.compareTo(null)).isEqualTo(1);
    }

    @Test
    public void compareTo_withDifferentName_returnsCharacterDifference() {
        assertThat(mTestFolderElement.compareTo(mParentFolderElement)).isEqualTo(4);
    }

    @Test
    public void compareTo_withSameSubFolders_returnsZero() {
        BluetoothMapFolderElement folderElementWithSameSubFolders =
                new BluetoothMapFolderElement(TEST_FOLDER_NAME, mParentFolderElement);

        mTestFolderElement.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        folderElementWithSameSubFolders.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        assertThat(mTestFolderElement.compareTo(folderElementWithSameSubFolders)).isEqualTo(0);
    }

    @Test
    public void compareTo_withDifferentSubFoldersSize_returnsSizeDifference() {
        BluetoothMapFolderElement folderElementWithDifferentSubFoldersSize =
                new BluetoothMapFolderElement(TEST_FOLDER_NAME, mParentFolderElement);

        mTestFolderElement.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        folderElementWithDifferentSubFoldersSize.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        folderElementWithDifferentSubFoldersSize.addImFolder(TEST_IM_FOLDER_NAME,
                TEST_IM_FOLDER_ID);
        assertThat(
                mTestFolderElement.compareTo(folderElementWithDifferentSubFoldersSize)).isEqualTo(
                -1);
    }

    @Test
    public void compareTo_withDifferentSubFolderTree_returnsCompareToRecursively() {
        BluetoothMapFolderElement folderElementWithDifferentSubFoldersTree =
                new BluetoothMapFolderElement(TEST_FOLDER_NAME, mParentFolderElement);

        mTestFolderElement.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        folderElementWithDifferentSubFoldersTree.addSmsMmsFolder(TEST_SMS_MMS_FOLDER_NAME);
        folderElementWithDifferentSubFoldersTree.getSubFolder(TEST_SMS_MMS_FOLDER_NAME).addFolder(
                TEST_PLACEHOLDER_FOLDER_NAME);
        assertThat(
                mTestFolderElement.compareTo(folderElementWithDifferentSubFoldersTree)).isEqualTo(
                -1);
    }
}
