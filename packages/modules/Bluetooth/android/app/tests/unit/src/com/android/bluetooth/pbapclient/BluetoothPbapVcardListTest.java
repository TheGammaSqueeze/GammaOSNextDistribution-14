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

package com.android.bluetooth.pbapclient;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;

import android.accounts.Account;
import android.content.Context;
import android.content.res.Resources;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.R;
import com.android.bluetooth.TestUtils;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapVcardListTest {

    private static final Account ACCOUNT = mock(Account.class);
    private Context mTargetContext = InstrumentationRegistry.getTargetContext();
    private Resources mTestResources = TestUtils.getTestApplicationResources(mTargetContext);

    @Test
    public void constructor_withInputStreamThatThrowsIoeWhenRead_throwsIOException() {

        final InputStream is = new InputStream() {
            @Override
            public int read() throws IOException {
                throw new IOException();
            }

            @Override
            public int read(byte[] b) throws IOException {
                throw new IOException();
            }

            @Override
            public int read(byte[] b, int off, int len) throws IOException {
                throw new IOException();
            }
        };

        assertThrows(IOException.class, () ->
                new BluetoothPbapVcardList(ACCOUNT, is, PbapClientConnectionHandler.VCARD_TYPE_30));
        assertThrows(IOException.class, () ->
                new BluetoothPbapVcardList(ACCOUNT, is, PbapClientConnectionHandler.VCARD_TYPE_21));
    }

    @Test
    public void constructor_withInvalidVcardType_throwsIllegalArgumentException() {
        assertThrows(IllegalArgumentException.class, () ->
                new BluetoothPbapVcardList(ACCOUNT,
                new ByteArrayInputStream("Hello world".getBytes()), (byte) -1));
    }

    @Test
    public void test30ParserWith21Vcard_parsingSucceeds() throws IOException {
        InputStream fileStream = mTestResources.openRawResource(
                com.android.bluetooth.tests.R.raw.v21_simple);
        BluetoothPbapVcardList result = new BluetoothPbapVcardList(ACCOUNT, fileStream,
                PbapClientConnectionHandler.VCARD_TYPE_30);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    public void test21ParserWith30Vcard_parsingSucceeds() throws IOException {
        InputStream fileStream = mTestResources.openRawResource(
                com.android.bluetooth.tests.R.raw.v30_simple);
        BluetoothPbapVcardList result = new BluetoothPbapVcardList(ACCOUNT, fileStream,
                PbapClientConnectionHandler.VCARD_TYPE_21);
        assertThat(result.getCount()).isEqualTo(1);
    }

    @Test
    public void test30ParserWithUnsupportedVcardVersion_parsingFails() throws IOException {
        InputStream fileStream = mTestResources.openRawResource(
                com.android.bluetooth.tests.R.raw.unsupported_version);
        BluetoothPbapVcardList result = new BluetoothPbapVcardList(ACCOUNT, fileStream,
                PbapClientConnectionHandler.VCARD_TYPE_30);
        assertThat(result.getCount()).isEqualTo(0);
    }
}
