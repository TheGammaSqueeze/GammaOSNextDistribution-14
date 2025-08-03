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

import com.android.bluetooth.SignedLongLong;

import org.junit.Test;
import org.junit.runner.RunWith;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class MapContactTest {
    private static final long TEST_NON_ZERO_ID = 1;
    private static final long TEST_ZERO_ID = 0;
    private static final String TEST_NAME = "test_name";

    @Test
    public void constructor() {
        MapContact contact = MapContact.create(TEST_NON_ZERO_ID, TEST_NAME);

        assertThat(contact.getId()).isEqualTo(TEST_NON_ZERO_ID);
        assertThat(contact.getName()).isEqualTo(TEST_NAME);
    }

    @Test
    public void getXBtUidString_withZeroId() {
        MapContact contact = MapContact.create(TEST_ZERO_ID, TEST_NAME);

        assertThat(contact.getXBtUidString()).isNull();
    }

    @Test
    public void getXBtUidString_withNonZeroId() {
        MapContact contact = MapContact.create(TEST_NON_ZERO_ID, TEST_NAME);

        assertThat(contact.getXBtUidString()).isEqualTo(
                BluetoothMapUtils.getLongLongAsString(TEST_NON_ZERO_ID, 0));
    }

    @Test
    public void getXBtUid_withZeroId() {
        MapContact contact = MapContact.create(TEST_ZERO_ID, TEST_NAME);

        assertThat(contact.getXBtUid()).isNull();
    }

    @Test
    public void getXBtUid_withNonZeroId() {
        MapContact contact = MapContact.create(TEST_NON_ZERO_ID, TEST_NAME);

        assertThat(contact.getXBtUid()).isEqualTo(new SignedLongLong(TEST_NON_ZERO_ID, 0));
    }

    @Test
    public void toString_returnsName() {
        MapContact contact = MapContact.create(TEST_NON_ZERO_ID, TEST_NAME);

        assertThat(contact.toString()).isEqualTo(TEST_NAME);
    }
}
