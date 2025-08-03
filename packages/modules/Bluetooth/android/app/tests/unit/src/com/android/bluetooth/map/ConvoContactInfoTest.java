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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.mapapi.BluetoothMapContract;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class ConvoContactInfoTest {

    @Test
    public void setConvoColumns() {
        BluetoothMapContentObserver.ConvoContactInfo info =
                new BluetoothMapContentObserver.ConvoContactInfo();
        MatrixCursor cursor = new MatrixCursor(
                new String[]{BluetoothMapContract.ConvoContactColumns.CONVO_ID,
                        BluetoothMapContract.ConvoContactColumns.NAME,
                        BluetoothMapContract.ConvoContactColumns.NICKNAME,
                        BluetoothMapContract.ConvoContactColumns.X_BT_UID,
                        BluetoothMapContract.ConvoContactColumns.CHAT_STATE,
                        BluetoothMapContract.ConvoContactColumns.UCI,
                        BluetoothMapContract.ConvoContactColumns.LAST_ACTIVE,
                        BluetoothMapContract.ConvoContactColumns.PRESENCE_STATE,
                        BluetoothMapContract.ConvoContactColumns.STATUS_TEXT,
                        BluetoothMapContract.ConvoContactColumns.PRIORITY,
                        BluetoothMapContract.ConvoContactColumns.LAST_ONLINE});

        info.setConvoColunms(cursor);

        assertThat(info.mContactColConvoId).isEqualTo(0);
        assertThat(info.mContactColName).isEqualTo(1);
        assertThat(info.mContactColNickname).isEqualTo(2);
        assertThat(info.mContactColBtUid).isEqualTo(3);
        assertThat(info.mContactColChatState).isEqualTo(4);
        assertThat(info.mContactColUci).isEqualTo(5);
        assertThat(info.mContactColNickname).isEqualTo(2);
        assertThat(info.mContactColLastActive).isEqualTo(6);
        assertThat(info.mContactColName).isEqualTo(1);
        assertThat(info.mContactColPresenceState).isEqualTo(7);
        assertThat(info.mContactColPresenceText).isEqualTo(8);
        assertThat(info.mContactColPriority).isEqualTo(9);
        assertThat(info.mContactColLastOnline).isEqualTo(10);
    }
}
