/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.mapapi;

import static com.google.common.truth.Truth.assertThat;

import android.net.Uri;

import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(AndroidJUnit4.class)
public class BluetoothMapContractTest {

    private static final String TEST_AUTHORITY = "com.test";
    private static final String ACCOUNT_ID = "test_account_id";
    private static final String MESSAGE_ID = "test_message_id";
    private static final String CONTACT_ID = "test_contact_id";

    @Test
    public void testBuildAccountUri() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + BluetoothMapContract.TABLE_ACCOUNT;

        Uri result = BluetoothMapContract.buildAccountUri(TEST_AUTHORITY);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildAccountUriWithId() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + BluetoothMapContract.TABLE_ACCOUNT + "/" + ACCOUNT_ID;

        Uri result = BluetoothMapContract.buildAccountUriwithId(TEST_AUTHORITY, ACCOUNT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildMessageUri() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + BluetoothMapContract.TABLE_MESSAGE;

        Uri result = BluetoothMapContract.buildMessageUri(TEST_AUTHORITY);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildMessageUri_withAccountId() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_MESSAGE;

        Uri result = BluetoothMapContract.buildMessageUri(TEST_AUTHORITY, ACCOUNT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildMessageUriWithId() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_MESSAGE + "/" + MESSAGE_ID;

        Uri result = BluetoothMapContract.buildMessageUriWithId(
                TEST_AUTHORITY, ACCOUNT_ID, MESSAGE_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildFolderUri() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_FOLDER;

        Uri result = BluetoothMapContract.buildFolderUri(TEST_AUTHORITY, ACCOUNT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildConversationUri() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_CONVERSATION;

        Uri result = BluetoothMapContract.buildConversationUri(TEST_AUTHORITY, ACCOUNT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildConvoContactsUri() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + BluetoothMapContract.TABLE_CONVOCONTACT;

        Uri result = BluetoothMapContract.buildConvoContactsUri(TEST_AUTHORITY);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildConvoContactsUri_withAccountId() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_CONVOCONTACT;

        Uri result = BluetoothMapContract.buildConvoContactsUri(TEST_AUTHORITY, ACCOUNT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }

    @Test
    public void testBuildConvoContactsUriWithId() {
        final String expectedUriString = "content://" + TEST_AUTHORITY + "/"
                + ACCOUNT_ID + "/" + BluetoothMapContract.TABLE_CONVOCONTACT + "/" + CONTACT_ID;

        Uri result = BluetoothMapContract.buildConvoContactsUriWithId(
                TEST_AUTHORITY, ACCOUNT_ID, CONTACT_ID);
        assertThat(result.toString()).isEqualTo(expectedUriString);
    }
}
