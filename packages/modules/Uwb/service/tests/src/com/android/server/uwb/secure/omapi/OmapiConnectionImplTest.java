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

package com.android.server.uwb.secure.omapi;

import static com.android.server.uwb.util.Constants.FIRA_APPLET_AID;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.se.omapi.Channel;
import android.se.omapi.Reader;
import android.se.omapi.SEService;
import android.se.omapi.Session;

import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;


import java.io.IOException;

public class OmapiConnectionImplTest {
    @Mock
    Context mMockContext;
    @Mock
    SEService mMockSeService;
    @Mock
    Reader mMockReader;
    @Mock
    Session mMockSeSession;
    @Mock
    Channel mMockChannel;
    @Rule
    public ExpectedException mThrown = ExpectedException.none();

    OmapiConnectionImpl mOmapiConnection;

    @Before
    public void setup() throws Exception {
        MockitoAnnotations.initMocks(this);

        mOmapiConnection = new OmapiConnectionImpl(mMockContext);
        mOmapiConnection.mSeService = mMockSeService;
        when(mMockSeService.getReaders()).thenReturn(new Reader[]{mMockReader});
        when(mMockSeService.isConnected()).thenReturn(true);
        when(mMockReader.getName()).thenReturn("eSE");
        when(mMockReader.openSession()).thenReturn(mMockSeSession);
        when(mMockSeSession.openLogicalChannel(eq(FIRA_APPLET_AID))).thenReturn(mMockChannel);
    }

    @Test
    public void openChannel() throws IOException {
        when(mMockChannel.getSelectResponse())
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR).toByteArray());
        ResponseApdu selectResponse = mOmapiConnection.openChannel();

        assertThat(selectResponse).isEqualTo(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR));
    }

    @Test
    public void openChannelWithNullResponse() throws IOException {
        mThrown.expect(IOException.class);

        when(mMockChannel.getSelectResponse()).thenReturn(null);

        mOmapiConnection.openChannel();
    }

    @Test
    public void openChannel2Times() throws IOException {
        when(mMockChannel.getSelectResponse())
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR).toByteArray());

        mOmapiConnection.openChannel();
        ResponseApdu responseApdu = mOmapiConnection.openChannel();
        assertThat(responseApdu.getStatusWord())
                .isEqualTo(StatusWord.SW_NO_SPECIFIC_DIAGNOSTIC.toInt());
    }
}
