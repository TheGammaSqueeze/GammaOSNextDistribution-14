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

import android.os.Handler;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.PasswordAuthentication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapObexAuthenticatorTest {

    private BluetoothPbapObexAuthenticator mAuthenticator;

    @Mock
    Handler mHandler;

    @Before
    public void setUp() throws Exception {
        mAuthenticator = new BluetoothPbapObexAuthenticator(mHandler);
    }

    @Test
    public void onAuthenticationChallenge() {
        // Note: onAuthenticationChallenge() does not use any arguments
        PasswordAuthentication passwordAuthentication = mAuthenticator.onAuthenticationChallenge(
                /*description=*/ null, /*isUserIdRequired=*/ false, /*isFullAccess=*/ false);

        assertThat(passwordAuthentication.getPassword())
                .isEqualTo(mAuthenticator.mSessionKey.getBytes());
    }

    @Test
    public void onAuthenticationResponse() {
        byte[] userName = new byte[] {};
        // Note: onAuthenticationResponse() does not use any arguments
        assertThat(mAuthenticator.onAuthenticationResponse(userName)).isNull();
    }
}
