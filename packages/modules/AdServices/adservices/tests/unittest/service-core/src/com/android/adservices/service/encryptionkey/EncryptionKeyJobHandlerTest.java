/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.encryptionkey;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;

import com.android.adservices.data.encryptionkey.EncryptionKeyDao;
import com.android.adservices.data.enrollment.EnrollmentDao;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.enrollment.EnrollmentData;
import com.android.dx.mockito.inline.extended.ExtendedMockito;
import com.android.dx.mockito.inline.extended.StaticMockitoSession;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.junit.MockitoJUnitRunner;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Unit test for {@link EncryptionKeyJobHandler} */
@RunWith(MockitoJUnitRunner.class)
public class EncryptionKeyJobHandlerTest {

    protected static final Context sContext = ApplicationProvider.getApplicationContext();

    @Mock private EnrollmentDao mEnrollmentDao;
    @Mock private EncryptionKeyDao mEncryptionKeyDao;
    @Mock private EncryptionKeyFetcher mEncryptionKeyFetcher;
    @Mock Flags mMockFlags;
    private StaticMockitoSession mMockitoSession;
    EncryptionKeyJobHandler mEncryptionKeyJobHandler;
    EncryptionKeyJobHandler mSpyEncryptionKeyJobHandler;

    private static final EnrollmentData ENROLLMENT_DATA =
            new EnrollmentData.Builder()
                    .setEnrollmentId("100")
                    .setEnrolledAPIs("PRIVACY_SANDBOX_API_TOPICS")
                    .setSdkNames("1sdk")
                    .setAttributionSourceRegistrationUrl(List.of("https://test1.com/source"))
                    .setAttributionTriggerRegistrationUrl(List.of("https://test.com/trigger"))
                    .setAttributionReportingUrl(List.of("https://test1.com"))
                    .setRemarketingResponseBasedRegistrationUrl(List.of("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com")
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY =
            new EncryptionKey.Builder()
                    .setId("1")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("ATFVFAVB")
                    .setExpiration(10001L)
                    .build();

    private static final EncryptionKey SIGNING_KEY =
            new EncryptionKey.Builder()
                    .setId("2")
                    .setKeyType(EncryptionKey.KeyType.SIGNING)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.ECDSA)
                    .setKeyCommitmentId(12)
                    .setBody("BTFVFAVB")
                    .setExpiration(10002L)
                    .build();

    private static final EncryptionKey ENCRYPTION_KEY_NO_ENROLLMENT_WITH_IT =
            new EncryptionKey.Builder()
                    .setId("3")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("200")
                    .setReportingOrigin(Uri.parse("https://test2.com"))
                    .setEncryptionKeyUrl("https://test2.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(11)
                    .setBody("CTFVFAVB")
                    .setExpiration(10003L)
                    .build();

    private static final EncryptionKey NEW_ENCRYPTION_KEY =
            new EncryptionKey.Builder()
                    .setId("4")
                    .setKeyType(EncryptionKey.KeyType.ENCRYPTION)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.HPKE)
                    .setKeyCommitmentId(14)
                    .setBody("DTFVFAVB")
                    .setExpiration(10004L)
                    .build();

    private static final EncryptionKey NEW_SIGNING_KEY =
            new EncryptionKey.Builder()
                    .setId("5")
                    .setKeyType(EncryptionKey.KeyType.SIGNING)
                    .setEnrollmentId("100")
                    .setReportingOrigin(Uri.parse("https://test1.com"))
                    .setEncryptionKeyUrl("https://test1.com/.well-known/encryption-keys")
                    .setProtocolType(EncryptionKey.ProtocolType.ECDSA)
                    .setKeyCommitmentId(15)
                    .setBody("ETFVFAVB")
                    .setExpiration(10005L)
                    .build();

    @Before
    public void setUp() {
        mMockitoSession =
                ExtendedMockito.mockitoSession()
                        .spyStatic(FlagsFactory.class)
                        .strictness(Strictness.LENIENT)
                        .startMocking();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_CONNECT_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkConnectTimeoutMs();
        doReturn(Flags.ENCRYPTION_KEY_NETWORK_READ_TIMEOUT_MS)
                .when(mMockFlags)
                .getEncryptionKeyNetworkReadTimeoutMs();
        mEncryptionKeyJobHandler =
                new EncryptionKeyJobHandler(
                        mEncryptionKeyDao, mEnrollmentDao, mEncryptionKeyFetcher);
        mSpyEncryptionKeyJobHandler = spy(mEncryptionKeyJobHandler);
    }

    @After
    public void after() {
        mMockitoSession.finishMocking();
    }

    @Test
    public void testNoEncryptionKey_fetchEncryptionKeys() {
        List<EncryptionKey> encryptionKeyList = new ArrayList<>();
        when(mEncryptionKeyDao.getAllEncryptionKeys()).thenReturn(encryptionKeyList);
        when(mEnrollmentDao.getAllEnrollmentData()).thenReturn(List.of(ENROLLMENT_DATA));
        Optional<List<EncryptionKey>> newEncryptionKeys =
                Optional.of(List.of(ENCRYPTION_KEY, SIGNING_KEY));
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(any(), any(), anyBoolean()))
                .thenReturn(newEncryptionKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(1)).insert(ENCRYPTION_KEY);
        verify(mEncryptionKeyDao, times(1)).insert(SIGNING_KEY);
    }

    @Test
    public void testHasEncryptionKey_noEnrollmentForEncryptionKey_deleteEncryptionKey() {
        when(mEncryptionKeyDao.getAllEncryptionKeys())
                .thenReturn(List.of(ENCRYPTION_KEY_NO_ENROLLMENT_WITH_IT));
        when(mEnrollmentDao.getEnrollmentData(any())).thenReturn(null);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(1)).delete(any());
    }

    @Test
    public void testHasEncryptionKey_dontUpdateNewKey() {
        when(mEncryptionKeyDao.getAllEncryptionKeys()).thenReturn(List.of(ENCRYPTION_KEY));
        when(mEnrollmentDao.getEnrollmentData(any())).thenReturn(ENROLLMENT_DATA);

        Optional<List<EncryptionKey>> newEncryptionKeys = Optional.empty();
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(any(), any(), anyBoolean()))
                .thenReturn(newEncryptionKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, never()).delete(any());
        verify(mEncryptionKeyDao, never()).insert((EncryptionKey) any());
    }

    @Test
    public void testHasEncryptionKey_updateNewEncryptionKey() {
        when(mEncryptionKeyDao.getAllEncryptionKeys()).thenReturn(List.of(ENCRYPTION_KEY));
        when(mEnrollmentDao.getEnrollmentData(any())).thenReturn(ENROLLMENT_DATA);

        Optional<List<EncryptionKey>> newKeys =
                Optional.of(List.of(NEW_ENCRYPTION_KEY, SIGNING_KEY, NEW_SIGNING_KEY));
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(any(), any(), anyBoolean()))
                .thenReturn(newKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(1)).delete(ENCRYPTION_KEY.getId());
        verify(mEncryptionKeyDao, times(3)).insert((EncryptionKey) any());
    }

    @Test
    public void testHasEncryptionKey_updateNewSigningKey() {
        when(mEncryptionKeyDao.getAllEncryptionKeys()).thenReturn(List.of(SIGNING_KEY));
        when(mEnrollmentDao.getEnrollmentData(any())).thenReturn(ENROLLMENT_DATA);

        Optional<List<EncryptionKey>> newKeys =
                Optional.of(List.of(NEW_ENCRYPTION_KEY, SIGNING_KEY, NEW_SIGNING_KEY));
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(any(), any(), anyBoolean()))
                .thenReturn(newKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(1)).delete(SIGNING_KEY.getId());
        verify(mEncryptionKeyDao, times(3)).insert((EncryptionKey) any());
    }

    @Test
    public void testHasEncryptionKey_updateBothNewEncryptionAndSigningKey() {
        when(mEncryptionKeyDao.getAllEncryptionKeys())
                .thenReturn(List.of(ENCRYPTION_KEY, SIGNING_KEY));
        when(mEnrollmentDao.getEnrollmentData(any())).thenReturn(ENROLLMENT_DATA);

        Optional<List<EncryptionKey>> newKeys =
                Optional.of(List.of(NEW_ENCRYPTION_KEY, SIGNING_KEY, NEW_SIGNING_KEY));
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(any(), any(), anyBoolean()))
                .thenReturn(newKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(2)).delete(any());
        verify(mEncryptionKeyDao, times(6)).insert((EncryptionKey) any());
    }

    @Test
    public void testHasEncryptionKey_insertEncryptionKeyForPreviouslyNotFetchedEnrollment() {
        when(mEncryptionKeyDao.getAllEncryptionKeys())
                .thenReturn(List.of(ENCRYPTION_KEY_NO_ENROLLMENT_WITH_IT));
        when(mEnrollmentDao.getAllEnrollmentData()).thenReturn(List.of(ENROLLMENT_DATA));
        when(mEnrollmentDao.getEnrollmentData(ENROLLMENT_DATA.getEnrollmentId()))
                .thenReturn(ENROLLMENT_DATA);
        when(mEnrollmentDao.getEnrollmentData(
                        ENCRYPTION_KEY_NO_ENROLLMENT_WITH_IT.getEnrollmentId()))
                .thenReturn(null);
        when(mEncryptionKeyDao.getEncryptionKeyFromEnrollmentId(ENROLLMENT_DATA.getEnrollmentId()))
                .thenReturn(new ArrayList<>());

        Optional<List<EncryptionKey>> newKeys = Optional.of(List.of(ENCRYPTION_KEY, SIGNING_KEY));
        when(mEncryptionKeyFetcher.fetchEncryptionKeys(null, ENROLLMENT_DATA, true))
                .thenReturn(newKeys);

        mSpyEncryptionKeyJobHandler.fetchAndUpdateEncryptionKeys();

        verify(mEncryptionKeyDao, times(1)).delete(ENCRYPTION_KEY_NO_ENROLLMENT_WITH_IT.getId());
        verify(mEncryptionKeyDao, times(1)).insert(ENCRYPTION_KEY);
        verify(mEncryptionKeyDao, times(1)).insert(SIGNING_KEY);
    }
}
