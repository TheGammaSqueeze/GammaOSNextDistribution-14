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
public class AppSearchNotificationDaoTest {
    private static final String ID = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "notifications";
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
        AppSearchNotificationDao dao =
                new AppSearchNotificationDao(ID, ID, NAMESPACE, false, false);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; namespace="
                                + NAMESPACE
                                + "; wasNotificationDisplayed=false"
                                + "; wasGaUxNotificationDisplayed=false");
    }

    @Test
    public void testEquals() {
        AppSearchNotificationDao dao1 =
                new AppSearchNotificationDao(ID, ID, NAMESPACE, true, false);
        AppSearchNotificationDao dao2 =
                new AppSearchNotificationDao(ID, ID, NAMESPACE, true, false);
        AppSearchNotificationDao dao3 =
                new AppSearchNotificationDao(ID, "foo", NAMESPACE, true, false);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID;
        assertThat(AppSearchNotificationDao.getQuery(ID)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        assertThat(AppSearchNotificationDao.getRowId(ID)).isEqualTo(ID);
    }

    @Test
    public void testWasNotificationDisplayed_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchNotificationDao.wasNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void testWasNotificationDisplayed() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID;
        AppSearchNotificationDao dao = Mockito.mock(AppSearchNotificationDao.class);
        Mockito.when(dao.getWasNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        boolean result =
                AppSearchNotificationDao.wasNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchNotificationDao dao2 = Mockito.mock(AppSearchNotificationDao.class);
        Mockito.when(dao2.getWasNotificationDisplayed()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchNotificationDao.wasNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }

    @Test
    public void testGaUxWasNotificationDisplayed_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void testWasGaUxNotificationDisplayed() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID;
        AppSearchNotificationDao dao = Mockito.mock(AppSearchNotificationDao.class);
        Mockito.when(dao.getWasGaUxNotificationDisplayed()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        boolean result =
                AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2;
        AppSearchNotificationDao dao2 = Mockito.mock(AppSearchNotificationDao.class);
        Mockito.when(dao2.getWasGaUxNotificationDisplayed()).thenReturn(true);
        ExtendedMockito.doReturn(dao2)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchNotificationDao.wasGaUxNotificationDisplayed(
                        mockSearchSession, mockExecutor, ID2, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }
}
