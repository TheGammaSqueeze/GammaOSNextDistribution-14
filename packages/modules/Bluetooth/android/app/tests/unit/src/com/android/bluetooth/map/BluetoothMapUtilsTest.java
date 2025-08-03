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

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.mapapi.BluetoothMapContract;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.nio.charset.StandardCharsets;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothMapUtilsTest {

    private static final String TEXT = "코드";
    private static final String QUOTED_PRINTABLE_ENCODED_TEXT = "=EC=BD=94=EB=93=9C";
    private static final String BASE64_ENCODED_TEXT = "7L2U65Oc";

    @Test
    public void encodeQuotedPrintable_withNullInput_returnsNull() {
        assertThat(BluetoothMapUtils.encodeQuotedPrintable(null)).isNull();
    }

    @Test
    public void encodeQuotedPrintable() {
        assertThat(BluetoothMapUtils.encodeQuotedPrintable(TEXT.getBytes(StandardCharsets.UTF_8)))
                .isEqualTo(QUOTED_PRINTABLE_ENCODED_TEXT);
    }

    @Test
    public void quotedPrintableToUtf8() {
        assertThat(BluetoothMapUtils.quotedPrintableToUtf8(QUOTED_PRINTABLE_ENCODED_TEXT, null))
                .isEqualTo(TEXT.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    public void printCursor_doesNotCrash() {
        MatrixCursor cursor = new MatrixCursor(new String[] {
                BluetoothMapContract.PresenceColumns.LAST_ONLINE, "Name"});
        cursor.addRow(new Object[] {345345226L, "test_name"});

        BluetoothMapUtils.printCursor(cursor);
    }

    @Test
    public void stripEncoding_quotedPrintable() {
        assertThat(BluetoothMapUtils.stripEncoding("=?UTF-8?Q?" + QUOTED_PRINTABLE_ENCODED_TEXT
                + "?=")).isEqualTo(TEXT);
    }

    @Test
    public void stripEncoding_base64() {
        assertThat(BluetoothMapUtils.stripEncoding("=?UTF-8?B?" + BASE64_ENCODED_TEXT + "?="))
                .isEqualTo(TEXT);
    }
}
