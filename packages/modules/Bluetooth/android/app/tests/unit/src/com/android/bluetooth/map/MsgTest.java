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

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MsgTest {
    private static final long TEST_ID = 1;
    private static final long TEST_FOLDER_ID = 1;
    private static final int TEST_READ_FLAG = 1;

    @Test
    public void constructor() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);

        assertThat(msg.id).isEqualTo(TEST_ID);
        assertThat(msg.folderId).isEqualTo(TEST_FOLDER_ID);
        assertThat(msg.flagRead).isEqualTo(TEST_READ_FLAG);
    }

    @Test
    public void hashCode_returnsExpectedResult() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);

        int expected = 31 + (int) (TEST_ID ^ (TEST_ID >>> 32));
        assertThat(msg.hashCode()).isEqualTo(expected);
    }

    @Test
    public void equals_withSameInstance() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);

        assertThat(msg.equals(msg)).isTrue();
    }

    @Test
    public void equals_withNull() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);

        assertThat(msg).isNotNull();
    }

    @Test
    public void equals_withDifferentClass() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);
        String msgOfDifferentClass = "msg_of_different_class";

        assertThat(msg).isNotEqualTo(msgOfDifferentClass);
    }

    @Test
    public void equals_withDifferentId() {
        long idOne = 1;
        long idTwo = 2;
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(idOne,
                TEST_FOLDER_ID, TEST_READ_FLAG);
        BluetoothMapContentObserver.Msg msgWithDifferentId = new BluetoothMapContentObserver.Msg(
                idTwo, TEST_FOLDER_ID, TEST_READ_FLAG);

        assertThat(msg).isNotEqualTo(msgWithDifferentId);
    }

    @Test
    public void equals_withEqualInstance() {
        BluetoothMapContentObserver.Msg msg = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);
        BluetoothMapContentObserver.Msg msgWithSameId = new BluetoothMapContentObserver.Msg(TEST_ID,
                TEST_FOLDER_ID, TEST_READ_FLAG);

        assertThat(msg).isEqualTo(msgWithSameId);
    }
}
