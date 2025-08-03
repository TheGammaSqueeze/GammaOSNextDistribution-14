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

import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.content.Context;
import android.os.Bundle;

import androidx.test.InstrumentationRegistry;
import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

@SmallTest
@RunWith(AndroidJUnit4.class)
public class AuthenticatorTest {

    private Context mTargetContext;
    private Authenticator mAuthenticator;

    @Mock
    AccountAuthenticatorResponse mResponse;

    @Mock
    Account mAccount;

    @Before
    public void setUp() throws Exception {
        mTargetContext = InstrumentationRegistry.getTargetContext();
        mAuthenticator = new Authenticator(mTargetContext);
    }

    @Test
    public void editProperties_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAuthenticator.editProperties(mResponse, null));
    }

    @Test
    public void addAccount_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAuthenticator.addAccount(mResponse, null, null, null, null));
    }

    @Test
    public void confirmCredentials_returnsNull() throws Exception {
        assertThat(mAuthenticator.confirmCredentials(mResponse, mAccount, null)).isNull();
    }

    @Test
    public void getAuthToken_throwsUnsupportedOperationException() {
        assertThrows(UnsupportedOperationException.class,
                () -> mAuthenticator.getAuthToken(mResponse, mAccount, null, null));
    }

    @Test
    public void getAuthTokenLabel_returnsNull() {
        assertThat(mAuthenticator.getAuthTokenLabel(null)).isNull();
    }

    @Test
    public void updateCredentials_returnsNull() throws Exception {
        assertThat(mAuthenticator.updateCredentials(mResponse, mAccount, null, null)).isNull();
    }

    @Test
    public void hasFeatures_notSupported() throws Exception {
        Bundle result = mAuthenticator.hasFeatures(mResponse, mAccount, null);
        assertThat(result.getBoolean(AccountManager.KEY_BOOLEAN_RESULT)).isFalse();
    }
}