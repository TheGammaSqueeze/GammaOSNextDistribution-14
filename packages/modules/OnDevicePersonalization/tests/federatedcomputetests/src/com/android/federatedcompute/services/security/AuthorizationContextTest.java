/*
 * Copyright (C) 2024 The Android Open Source Project
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

package com.android.federatedcompute.services.security;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.doAnswer;
import static com.android.federatedcompute.services.common.Flags.ODP_AUTHORIZATION_TOKEN_TTL;
import static com.android.federatedcompute.services.http.HttpClientUtil.ODP_AUTHORIZATION_KEY;

import static com.google.common.truth.Truth.assertThat;

import static junit.framework.Assert.assertFalse;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertNull;
import static junit.framework.Assert.assertTrue;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;

import com.android.federatedcompute.services.common.Clock;
import com.android.federatedcompute.services.common.MonotonicClock;
import com.android.federatedcompute.services.common.TrainingEventLogger;
import com.android.federatedcompute.services.data.FederatedComputeDbHelper;
import com.android.federatedcompute.services.data.ODPAuthorizationToken;
import com.android.federatedcompute.services.data.ODPAuthorizationTokenDao;

import com.google.internal.federatedcompute.v1.AuthenticationMetadata;
import com.google.internal.federatedcompute.v1.KeyAttestationAuthMetadata;
import com.google.protobuf.ByteString;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;

public class AuthorizationContextTest {
    private static final String OWNER_ID = "com.android.pckg.name/com.android.class.name";
    private static final String OWNER_ID_CERT_DIGEST = "owner_cert";
    private static final String TOKEN = "token";
    private static final byte[] CHALLENGE = "CHALLENGE".getBytes();
    private static final AuthenticationMetadata AUTH_METADATA =
            AuthenticationMetadata.newBuilder()
                    .setKeyAttestationMetadata(
                            KeyAttestationAuthMetadata.newBuilder()
                                    .setChallenge(ByteString.copyFrom(CHALLENGE)))
                    .build();
    private static final List<String> KA_RECORD =
            List.of("aasldkgjlaskdjgalskj", "aldkjglasdkjlasjg");

    private Context mContext;
    @Mock private KeyAttestation mMocKeyAttestation;

    @Mock private TrainingEventLogger mMockTrainingEventLogger;
    private ODPAuthorizationTokenDao mAuthTokenDao;
    private Clock mClock;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mContext = ApplicationProvider.getApplicationContext();
        doReturn(KA_RECORD).when(mMocKeyAttestation).generateAttestationRecord(any(), anyString());
        mAuthTokenDao = spy(ODPAuthorizationTokenDao.getInstanceForTest(mContext));
        mClock = MonotonicClock.getInstance();
        doNothing().when(mMockTrainingEventLogger).logKeyAttestationLatencyEvent(anyLong());
    }

    @After
    public void tearDown() throws Exception {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);
        dbHelper.getWritableDatabase().close();
        dbHelper.getReadableDatabase().close();
        dbHelper.close();
    }

    @Test
    public void updateAuthState_success() {
        AuthorizationContext authContext =
                new AuthorizationContext(
                        OWNER_ID,
                        OWNER_ID_CERT_DIGEST,
                        mAuthTokenDao,
                        mMocKeyAttestation,
                        MonotonicClock.getInstance());

        authContext.updateAuthState(AUTH_METADATA, mMockTrainingEventLogger);

        assertFalse(authContext.isFirstAuthTry());
        assertNotNull(authContext.getAttestationRecord());
        verify(mMockTrainingEventLogger, timeout(1)).logKeyAttestationLatencyEvent(anyLong());
    }

    @Test
    public void generateAuthHeader_emptyToken() {
        AuthorizationContext authContext =
                new AuthorizationContext(
                        OWNER_ID,
                        OWNER_ID_CERT_DIGEST,
                        mAuthTokenDao,
                        mMocKeyAttestation,
                        MonotonicClock.getInstance());

        Map<String, String> headers = authContext.generateAuthHeaders();
        assertTrue(headers.isEmpty());
        assertNull(mAuthTokenDao.getUnexpiredAuthorizationToken(OWNER_ID));
    }

    @Test
    public void generateAuthHeader_token() {
        insertAuthToken();
        AuthorizationContext authContext =
                new AuthorizationContext(
                        OWNER_ID,
                        OWNER_ID_CERT_DIGEST,
                        mAuthTokenDao,
                        mMocKeyAttestation,
                        MonotonicClock.getInstance());

        Map<String, String> headers = authContext.generateAuthHeaders();
        assertThat(headers.get(ODP_AUTHORIZATION_KEY)).isEqualTo(TOKEN);
    }

    @Test
    public void generateAuthHeader_attestationRecord() throws InterruptedException {
        AuthorizationContext authContext =
                new AuthorizationContext(
                        OWNER_ID,
                        OWNER_ID_CERT_DIGEST,
                        mAuthTokenDao,
                        mMocKeyAttestation,
                        MonotonicClock.getInstance());

        CountDownLatch latch = new CountDownLatch(1);
        doAnswer(
                        invocation -> {
                            invocation.callRealMethod();
                            latch.countDown();
                            return true;
                        })
                .when(mAuthTokenDao)
                .insertAuthorizationToken(any(ODPAuthorizationToken.class));
        authContext.updateAuthState(AUTH_METADATA, mMockTrainingEventLogger);
        assertNull(mAuthTokenDao.getUnexpiredAuthorizationToken(OWNER_ID));
        verify(mMockTrainingEventLogger, times(1)).logKeyAttestationLatencyEvent(anyLong());

        Map<String, String> headerMap = authContext.generateAuthHeaders();
        latch.await();

        assertNotNull(headerMap.get(ODP_AUTHORIZATION_KEY));
        assertNotNull(mAuthTokenDao.getUnexpiredAuthorizationToken(OWNER_ID));
        verify(mMocKeyAttestation).generateAttestationRecord(eq(CHALLENGE), anyString());
    }

    private void insertAuthToken() {
        // insert authorization token
        ODPAuthorizationToken authToken =
                new ODPAuthorizationToken.Builder(
                                OWNER_ID,
                                TOKEN,
                                mClock.currentTimeMillis(),
                                mClock.currentTimeMillis() + ODP_AUTHORIZATION_TOKEN_TTL)
                        .build();
        mAuthTokenDao.insertAuthorizationToken(authToken);
        assertThat(mAuthTokenDao.getUnexpiredAuthorizationToken(OWNER_ID)).isEqualTo(authToken);
    }
}
