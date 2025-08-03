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

package com.android.bluetooth.pbap;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.verify;

import androidx.test.filters.MediumTest;
import androidx.test.runner.AndroidJUnit4;

import com.android.obex.PasswordAuthentication;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@MediumTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapAuthenticatorTest {

    private BluetoothPbapAuthenticator mAuthenticator;

    @Mock
    PbapStateMachine mMockPbapStateMachine;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mAuthenticator = new BluetoothPbapAuthenticator(mMockPbapStateMachine);
    }

    @Test
    public void testConstructor() {
        assertThat(mAuthenticator.mChallenged).isFalse();
        assertThat(mAuthenticator.mAuthCancelled).isFalse();
        assertThat(mAuthenticator.mSessionKey).isNull();
        assertThat(mAuthenticator.mPbapStateMachine).isEqualTo(mMockPbapStateMachine);
    }

    @Test
    public void testSetChallenged() {
        mAuthenticator.setChallenged(true);
        assertThat(mAuthenticator.mChallenged).isTrue();

        mAuthenticator.setChallenged(false);
        assertThat(mAuthenticator.mChallenged).isFalse();
    }

    @Test
    public void testSetCancelled() {
        mAuthenticator.setCancelled(true);
        assertThat(mAuthenticator.mAuthCancelled).isTrue();

        mAuthenticator.setCancelled(false);
        assertThat(mAuthenticator.mAuthCancelled).isFalse();
    }

    @Test
    public void testSetSessionKey() {
        final String sessionKey = "test_session_key";

        mAuthenticator.setSessionKey(sessionKey);
        assertThat(mAuthenticator.mSessionKey).isEqualTo(sessionKey);

        mAuthenticator.setSessionKey(null);
        assertThat(mAuthenticator.mSessionKey).isNull();
    }

    @Test
    public void testOnAuthenticationChallenge() {
        final String sessionKey = "test_session_key";
        doAnswer(invocation -> {
            mAuthenticator.setSessionKey(sessionKey);
            mAuthenticator.setChallenged(true);
            return null;
        }).when(mMockPbapStateMachine).sendMessage(PbapStateMachine.CREATE_NOTIFICATION);

        // Note: onAuthenticationChallenge() does not use any arguments
        PasswordAuthentication passwordAuthentication = mAuthenticator.onAuthenticationChallenge(
                /*description=*/ null, /*isUserIdRequired=*/ false, /*isFullAccess=*/ false);

        verify(mMockPbapStateMachine).sendMessage(PbapStateMachine.CREATE_NOTIFICATION);
        verify(mMockPbapStateMachine).sendMessageDelayed(PbapStateMachine.REMOVE_NOTIFICATION,
                BluetoothPbapService.USER_CONFIRM_TIMEOUT_VALUE);
        assertThat(passwordAuthentication.getPassword()).isEqualTo(sessionKey.getBytes());
    }

    @Test
    public void testOnAuthenticationChallenge_returnsNullWhenSessionKeyIsEmpty() {
        final String emptySessionKey = "";
        doAnswer(invocation -> {
            mAuthenticator.setSessionKey(emptySessionKey);
            mAuthenticator.setChallenged(true);
            return null;
        }).when(mMockPbapStateMachine).sendMessage(PbapStateMachine.CREATE_NOTIFICATION);

        // Note: onAuthenticationChallenge() does not use any arguments
        PasswordAuthentication passwordAuthentication = mAuthenticator.onAuthenticationChallenge(
                /*description=*/ null, /*isUserIdRequired=*/ false, /*isFullAccess=*/ false);
        assertThat(passwordAuthentication).isNull();
    }

    @Test
    public void testOnAuthenticationResponse() {
        byte[] userName = "test_user_name".getBytes();

        // This assertion should be fixed when the implementation changes.
        assertThat(mAuthenticator.onAuthenticationResponse(userName)).isNull();
    }
}
