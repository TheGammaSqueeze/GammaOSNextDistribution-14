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

package com.android.server.uwb.secure.provisioning;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.os.test.TestLooper;

import com.android.server.uwb.secure.SecureElementChannel;
import com.android.server.uwb.secure.csml.FiRaCommand;
import com.android.server.uwb.secure.iso7816.ResponseApdu;
import com.android.server.uwb.secure.iso7816.StatusWord;
import com.android.server.uwb.util.ObjectIdentifier;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;
import java.util.UUID;

public class ProvisioningManagerTest {
    @Mock
    private SecureElementChannel mSecureElementChannel;
    @Mock
    private ProvisioningManager.ProvisioningCallback mProvisioningCallback;
    @Mock
    private ProvisioningManager.DeleteAdfCallback mDeleteAdfCallback;

    private final TestLooper mTestLooper = new TestLooper();

    private ProvisioningManager mUnderTest;

    private static final UUID SERVICE_INSTANCE_ID = UUID.fromString("1-2-3-4-5");
    private static final ObjectIdentifier ADF_OID =
            ObjectIdentifier.fromBytes(new byte[] {(byte) 1});

    @Before
    public void setup() {
        MockitoAnnotations.initMocks(this);
        mUnderTest = new ProvisioningManager(mSecureElementChannel, mTestLooper.getLooper());
    }

    @Test
    public void provisioningException() {
        mUnderTest.provisioningAdf(SERVICE_INSTANCE_ID, new byte[0], mProvisioningCallback);

        mTestLooper.dispatchAll();

        verify(mProvisioningCallback).onFail(eq(SERVICE_INSTANCE_ID));
    }

    @Test
    public void deleteAdfSuccess() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(FiRaCommand.class)))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_NO_ERROR));

        mUnderTest.deleteAdf(SERVICE_INSTANCE_ID, ADF_OID, mDeleteAdfCallback);
        mTestLooper.dispatchAll();

        verify(mDeleteAdfCallback).onSuccess(eq(SERVICE_INSTANCE_ID), eq(ADF_OID));
    }

    @Test
    public void deleteAdfFail() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(FiRaCommand.class)))
                .thenReturn(ResponseApdu.fromStatusWord(StatusWord.SW_COMMAND_NOT_ALLOWED));

        mUnderTest.deleteAdf(SERVICE_INSTANCE_ID, ADF_OID, mDeleteAdfCallback);
        mTestLooper.dispatchAll();

        verify(mDeleteAdfCallback).onFail(eq(SERVICE_INSTANCE_ID), eq(ADF_OID));
    }

    @Test
    public void deleteAdfWithException() throws IOException {
        when(mSecureElementChannel.openChannel()).thenReturn(true);
        when(mSecureElementChannel.transmit(any(FiRaCommand.class)))
                .thenThrow(new IOException("error"));
        mUnderTest.deleteAdf(SERVICE_INSTANCE_ID, ADF_OID, mDeleteAdfCallback);
        mTestLooper.dispatchAll();

        verify(mDeleteAdfCallback).onFail(eq(SERVICE_INSTANCE_ID), eq(ADF_OID));
    }
}
