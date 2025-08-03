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
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.Mockito.mock;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.ClientSession;
import com.android.obex.HeaderSet;
import com.android.obex.ObexTransport;
import com.android.obex.ResponseCodes;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.InputStream;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapRequestTest {

    private BluetoothPbapRequest mRequest = new BluetoothPbapRequest() {};

    @Mock
    private ObexTransport mObexTransport;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mRequest = new BluetoothPbapRequest() {};
    }

    @Test
    public void isSuccess_true() {
        mRequest.mResponseCode = ResponseCodes.OBEX_HTTP_OK;

        assertThat(mRequest.isSuccess()).isTrue();
    }

    @Test
    public void isSuccess_false() {
        mRequest.mResponseCode = ResponseCodes.OBEX_HTTP_INTERNAL_ERROR;

        assertThat(mRequest.isSuccess()).isFalse();
    }

    @Test
    public void execute_afterAbort() throws Exception {
        mRequest.abort();
        ClientSession session = new ClientSession(mObexTransport);
        mRequest.execute(session);

        assertThat(mRequest.mResponseCode).isEqualTo(ResponseCodes.OBEX_HTTP_INTERNAL_ERROR);
    }

    // TODO: Add execute_success test case.

    @Test
    public void emptyMethods() {
        try {
            mRequest.readResponse(mock(InputStream.class));
            mRequest.readResponseHeaders(new HeaderSet());
            mRequest.checkResponseCode(ResponseCodes.OBEX_HTTP_OK);

        } catch (Exception e) {
            assertWithMessage("Exception should not happen.").fail();
        }
    }
}
