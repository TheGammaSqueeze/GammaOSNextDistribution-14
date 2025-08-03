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

package com.android.adservices.service.appsearch;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;

import android.content.Context;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.dx.mockito.inline.extended.ExtendedMockito;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.Executor;

@SmallTest
public class AppSearchUxStatesDaoTest {
    private static final String ID1 = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "uxstates";
    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mAdServicesPackageName =
            AppSearchConsentWorker.getAdServicesPackageName(mContext);
    private MockitoSession mStaticMockSession;

    @Rule
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Before
    public void setup() {
        mStaticMockSession =
                ExtendedMockito.mockitoSession()
                        .mockStatic(AppSearchDao.class)
                        .strictness(Strictness.WARN)
                        .initMocks(this)
                        .startMocking();
    }

    @After
    public void teardown() {
        if (mStaticMockSession != null) {
            mStaticMockSession.finishMocking();
        }
    }

    @Test
    public void testToString() {
        AppSearchUxStatesDao dao =
                new AppSearchUxStatesDao(
                        ID1,
                        ID2,
                        NAMESPACE,
                        false,
                        false,
                        false,
                        false,
                        false,
                        "testUx",
                        "testEnrollmentChannel");
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID1
                                + "; userId="
                                + ID2
                                + "; namespace="
                                + NAMESPACE
                                + "; isEntryPointEnabled=false"
                                + "; isU18Account=false"
                                + "; isAdultAccount=false"
                                + "; isAdIdEnabled=false"
                                + "; wasU18NotificationDisplayed=false"
                                + "; ux=testUx"
                                + "; enrollmentChannel=testEnrollmentChannel");
    }

    @Test
    public void testEquals() {
        AppSearchUxStatesDao dao1 =
                new AppSearchUxStatesDao(
                        ID1, ID2, NAMESPACE, true, false, false, false, false, null, null);
        AppSearchUxStatesDao dao2 =
                new AppSearchUxStatesDao(
                        ID1, ID2, NAMESPACE, true, false, false, false, false, null, null);
        AppSearchUxStatesDao dao3 =
                new AppSearchUxStatesDao(
                        ID1, "foo", NAMESPACE, true, false, false, false, false, null, null);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID1;
        assertThat(AppSearchUxStatesDao.getQuery(ID1)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        assertThat(AppSearchUxStatesDao.getRowId(ID1)).isEqualTo(ID1);
    }

    @Test
    public void isEntryPointEnabledTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchUxStatesDao.readIsEntryPointEnabled(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void isEntryPointEnabledTest_trueBit() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao.isEntryPointEnabled()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        boolean result =
                AppSearchUxStatesDao.readIsEntryPointEnabled(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchUxStatesDao dao2 = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao2.isEntryPointEnabled()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchUxStatesDao.readIsEntryPointEnabled(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void isAdultAccountTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchUxStatesDao.readIsAdultAccount(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void isAdultAccountTest_trueBit() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao.isAdultAccount()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        boolean result =
                AppSearchUxStatesDao.readIsAdultAccount(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchUxStatesDao dao2 = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao2.isAdultAccount()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchUxStatesDao.readIsAdultAccount(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void isU18AccountTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchUxStatesDao.readIsU18Account(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void isU18AccountTest_trueBit() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao.isU18Account()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        boolean result =
                AppSearchUxStatesDao.readIsU18Account(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchUxStatesDao dao2 = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao2.isU18Account()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchUxStatesDao.readIsU18Account(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void isAdIdEnabledTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchUxStatesDao.readIsAdIdEnabled(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void isAdIdEnabledTest_trueBit() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao.isAdIdEnabled()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        boolean result =
                AppSearchUxStatesDao.readIsAdIdEnabled(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchUxStatesDao dao2 = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao2.isAdIdEnabled()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchUxStatesDao.readIsAdIdEnabled(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void wasU18NotificationDisplayedTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchUxStatesDao.readIsU18NotificationDisplayed(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void wasU18NotificationDisplayedTest_trueBit() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao.wasU18NotificationDisplayed()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));

        boolean result =
                AppSearchUxStatesDao.readIsU18NotificationDisplayed(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchUxStatesDao dao2 = Mockito.mock(AppSearchUxStatesDao.class);
        Mockito.when(dao2.wasU18NotificationDisplayed()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchUxStatesDao.readIsU18NotificationDisplayed(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void getUxTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        PrivacySandboxUxCollection result =
                AppSearchUxStatesDao.readUx(
                        mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
        assertThat(result).isEqualTo(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }

    @Test
    public void getUxTest_allUxs() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            Mockito.when(dao.getUx()).thenReturn(ux.toString());
            ExtendedMockito.doReturn(dao)
                    .when(
                            () ->
                                    AppSearchDao.readConsentData(
                                            any(), any(), any(), any(), eq(query), any()));

            PrivacySandboxUxCollection result =
                    AppSearchUxStatesDao.readUx(
                            mockSearchSession, mockExecutor, ID1, mAdServicesPackageName);
            assertThat(result).isEqualTo(ux);
        }
    }

    @Test
    public void getEnrollmentChannelTest_nullDao() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        PrivacySandboxEnrollmentChannelCollection result =
                AppSearchUxStatesDao.readEnrollmentChannel(
                        mockSearchSession,
                        mockExecutor,
                        ID1,
                        PrivacySandboxUxCollection.UNSUPPORTED_UX,
                        mAdServicesPackageName);
        assertThat(result).isEqualTo(null);
    }

    @Test
    public void getEnrollmentChannelTest_allUxsAllChannels() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID1;
        AppSearchUxStatesDao dao = Mockito.mock(AppSearchUxStatesDao.class);

        for (PrivacySandboxUxCollection ux : PrivacySandboxUxCollection.values()) {
            for (PrivacySandboxEnrollmentChannelCollection channel :
                    ux.getEnrollmentChannelCollection()) {
                Mockito.when(dao.getEnrollmentChannel()).thenReturn(channel.toString());
                ExtendedMockito.doReturn(dao)
                        .when(
                                () ->
                                        AppSearchDao.readConsentData(
                                                any(), any(), any(), any(), eq(query), any()));

                PrivacySandboxEnrollmentChannelCollection result =
                        AppSearchUxStatesDao.readEnrollmentChannel(
                                mockSearchSession, mockExecutor, ID1, ux, mAdServicesPackageName);
                assertThat(result).isEqualTo(channel);
            }
        }
    }
}
