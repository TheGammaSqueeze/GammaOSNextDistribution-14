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

package com.android.bluetooth.map;

import static com.google.common.truth.Truth.assertThat;

import android.database.MatrixCursor;
import android.provider.BaseColumns;
import android.provider.Telephony.Mms;
import android.provider.Telephony.Sms;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.mapapi.BluetoothMapContract;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class FilterInfoTest {
    private BluetoothMapContent.FilterInfo mFilterInfo;

    @Before
    public void setUp() {
        mFilterInfo = new BluetoothMapContent.FilterInfo();
    }

    @Test
    public void setMessageColumns() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                BluetoothMapContract.MessageColumns._ID,
                BluetoothMapContract.MessageColumns.DATE,
                BluetoothMapContract.MessageColumns.SUBJECT,
                BluetoothMapContract.MessageColumns.FOLDER_ID,
                BluetoothMapContract.MessageColumns.FLAG_READ,
                BluetoothMapContract.MessageColumns.MESSAGE_SIZE,
                BluetoothMapContract.MessageColumns.FROM_LIST,
                BluetoothMapContract.MessageColumns.TO_LIST,
                BluetoothMapContract.MessageColumns.FLAG_ATTACHMENT,
                BluetoothMapContract.MessageColumns.ATTACHMENT_SIZE,
                BluetoothMapContract.MessageColumns.FLAG_HIGH_PRIORITY,
                BluetoothMapContract.MessageColumns.FLAG_PROTECTED,
                BluetoothMapContract.MessageColumns.RECEPTION_STATE,
                BluetoothMapContract.MessageColumns.DEVILERY_STATE,
                BluetoothMapContract.MessageColumns.THREAD_ID});

        mFilterInfo.setMessageColumns(cursor);

        assertThat(mFilterInfo.mMessageColId).isEqualTo(0);
        assertThat(mFilterInfo.mMessageColDate).isEqualTo(1);
        assertThat(mFilterInfo.mMessageColSubject).isEqualTo(2);
        assertThat(mFilterInfo.mMessageColFolder).isEqualTo(3);
        assertThat(mFilterInfo.mMessageColRead).isEqualTo(4);
        assertThat(mFilterInfo.mMessageColSize).isEqualTo(5);
        assertThat(mFilterInfo.mMessageColFromAddress).isEqualTo(6);
        assertThat(mFilterInfo.mMessageColToAddress).isEqualTo(7);
        assertThat(mFilterInfo.mMessageColAttachment).isEqualTo(8);
        assertThat(mFilterInfo.mMessageColAttachmentSize).isEqualTo(9);
        assertThat(mFilterInfo.mMessageColPriority).isEqualTo(10);
        assertThat(mFilterInfo.mMessageColProtected).isEqualTo(11);
        assertThat(mFilterInfo.mMessageColReception).isEqualTo(12);
        assertThat(mFilterInfo.mMessageColDelivery).isEqualTo(13);
        assertThat(mFilterInfo.mMessageColThreadId).isEqualTo(14);
    }

    @Test
    public void setEmailMessageColumns() {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {BluetoothMapContract.MessageColumns.CC_LIST,
                        BluetoothMapContract.MessageColumns.BCC_LIST,
                        BluetoothMapContract.MessageColumns.REPLY_TO_LIST});

        mFilterInfo.setEmailMessageColumns(cursor);

        assertThat(mFilterInfo.mMessageColCcAddress).isEqualTo(0);
        assertThat(mFilterInfo.mMessageColBccAddress).isEqualTo(1);
        assertThat(mFilterInfo.mMessageColReplyTo).isEqualTo(2);
    }

    @Test
    public void setImMessageColumns() {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {BluetoothMapContract.MessageColumns.THREAD_NAME,
                        BluetoothMapContract.MessageColumns.ATTACHMENT_MINE_TYPES,
                        BluetoothMapContract.MessageColumns.BODY});

        mFilterInfo.setImMessageColumns(cursor);

        assertThat(mFilterInfo.mMessageColThreadName).isEqualTo(0);
        assertThat(mFilterInfo.mMessageColAttachmentMime).isEqualTo(1);
        assertThat(mFilterInfo.mMessageColBody).isEqualTo(2);
    }

    @Test
    public void setEmailImConvoColumns() {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {BluetoothMapContract.ConversationColumns.THREAD_ID,
                        BluetoothMapContract.ConversationColumns.LAST_THREAD_ACTIVITY,
                        BluetoothMapContract.ConversationColumns.THREAD_NAME,
                        BluetoothMapContract.ConversationColumns.READ_STATUS,
                        BluetoothMapContract.ConversationColumns.VERSION_COUNTER,
                        BluetoothMapContract.ConversationColumns.SUMMARY});

        mFilterInfo.setEmailImConvoColumns(cursor);

        assertThat(mFilterInfo.mConvoColConvoId).isEqualTo(0);
        assertThat(mFilterInfo.mConvoColLastActivity).isEqualTo(1);
        assertThat(mFilterInfo.mConvoColName).isEqualTo(2);
        assertThat(mFilterInfo.mConvoColRead).isEqualTo(3);
        assertThat(mFilterInfo.mConvoColVersionCounter).isEqualTo(4);
        assertThat(mFilterInfo.mConvoColSummary).isEqualTo(5);
    }

    @Test
    public void setEmailImConvoContactColumns() {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {BluetoothMapContract.ConvoContactColumns.X_BT_UID,
                        BluetoothMapContract.ConvoContactColumns.CHAT_STATE,
                        BluetoothMapContract.ConvoContactColumns.UCI,
                        BluetoothMapContract.ConvoContactColumns.NICKNAME,
                        BluetoothMapContract.ConvoContactColumns.LAST_ACTIVE,
                        BluetoothMapContract.ConvoContactColumns.NAME,
                        BluetoothMapContract.ConvoContactColumns.PRESENCE_STATE,
                        BluetoothMapContract.ConvoContactColumns.STATUS_TEXT,
                        BluetoothMapContract.ConvoContactColumns.PRIORITY});

        mFilterInfo.setEmailImConvoContactColumns(cursor);

        assertThat(mFilterInfo.mContactColBtUid).isEqualTo(0);
        assertThat(mFilterInfo.mContactColChatState).isEqualTo(1);
        assertThat(mFilterInfo.mContactColContactUci).isEqualTo(2);
        assertThat(mFilterInfo.mContactColNickname).isEqualTo(3);
        assertThat(mFilterInfo.mContactColLastActive).isEqualTo(4);
        assertThat(mFilterInfo.mContactColName).isEqualTo(5);
        assertThat(mFilterInfo.mContactColPresenceState).isEqualTo(6);
        assertThat(mFilterInfo.mContactColPresenceText).isEqualTo(7);
        assertThat(mFilterInfo.mContactColPriority).isEqualTo(8);
    }

    @Test
    public void setSmsColumns() {
        MatrixCursor cursor = new MatrixCursor(new String[]{BaseColumns._ID, Sms.TYPE, Sms.READ,
                Sms.BODY, Sms.ADDRESS, Sms.DATE, Sms.THREAD_ID});

        mFilterInfo.setSmsColumns(cursor);

        assertThat(mFilterInfo.mSmsColId).isEqualTo(0);
        assertThat(mFilterInfo.mSmsColFolder).isEqualTo(1);
        assertThat(mFilterInfo.mSmsColRead).isEqualTo(2);
        assertThat(mFilterInfo.mSmsColSubject).isEqualTo(3);
        assertThat(mFilterInfo.mSmsColAddress).isEqualTo(4);
        assertThat(mFilterInfo.mSmsColDate).isEqualTo(5);
        assertThat(mFilterInfo.mSmsColType).isEqualTo(1);
        assertThat(mFilterInfo.mSmsColThreadId).isEqualTo(6);
    }

    @Test
    public void setMmsColumns() {
        MatrixCursor cursor = new MatrixCursor(
                new String[] {BaseColumns._ID, Mms.MESSAGE_BOX, Mms.READ, Mms.MESSAGE_SIZE,
                        Mms.TEXT_ONLY, Mms.DATE, Mms.SUBJECT, Mms.THREAD_ID});

        mFilterInfo.setMmsColumns(cursor);

        assertThat(mFilterInfo.mMmsColId).isEqualTo(0);
        assertThat(mFilterInfo.mMmsColFolder).isEqualTo(1);
        assertThat(mFilterInfo.mMmsColRead).isEqualTo(2);
        assertThat(mFilterInfo.mMmsColAttachmentSize).isEqualTo(3);
        assertThat(mFilterInfo.mMmsColTextOnly).isEqualTo(4);
        assertThat(mFilterInfo.mMmsColSize).isEqualTo(3);
        assertThat(mFilterInfo.mMmsColDate).isEqualTo(5);
        assertThat(mFilterInfo.mMmsColSubject).isEqualTo(6);
        assertThat(mFilterInfo.mMmsColThreadId).isEqualTo(7);
    }
}
