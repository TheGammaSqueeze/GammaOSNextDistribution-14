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
public class AppSearchConsentDaoTest {
    private static final String ID = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "consent";
    private static final String API_TYPE = "CONSENT-TOPICS";
    private static final String API_TYPE2 = "CONSENT-FLEDGE";
    private static final String CONSENT = "true";
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
        AppSearchConsentDao dao = new AppSearchConsentDao(ID, ID, NAMESPACE, API_TYPE, CONSENT);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; apiType="
                                + API_TYPE
                                + "; namespace="
                                + NAMESPACE
                                + "; consent="
                                + CONSENT);
    }

    @Test
    public void testEquals() {
        AppSearchConsentDao dao1 = new AppSearchConsentDao(ID, ID, NAMESPACE, API_TYPE, CONSENT);
        AppSearchConsentDao dao2 = new AppSearchConsentDao(ID, ID, NAMESPACE, API_TYPE, CONSENT);
        AppSearchConsentDao dao3 = new AppSearchConsentDao(ID, "foo", NAMESPACE, API_TYPE, CONSENT);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID + " " + "apiType:" + API_TYPE;
        assertThat(AppSearchConsentDao.getQuery(ID, API_TYPE)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        String expected = ID + "_" + API_TYPE;
        assertThat(AppSearchConsentDao.getRowId(ID, API_TYPE)).isEqualTo(expected);
    }

    @Test
    public void testReadConsentData_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        boolean result =
                AppSearchConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID, API_TYPE, mAdServicesPackageName);
        assertThat(result).isFalse();
    }

    @Test
    public void testReadConsentData() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String query = "userId:" + ID + " " + "apiType:" + API_TYPE;
        AppSearchConsentDao dao = Mockito.mock(AppSearchConsentDao.class);
        Mockito.when(dao.isConsented()).thenReturn(false);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        boolean result =
                AppSearchConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID, API_TYPE, mAdServicesPackageName);
        assertThat(result).isFalse();

        // Confirm that the right value is returned even when it is true.
        String query2 = "userId:" + ID2 + " " + "apiType:" + API_TYPE2;
        Mockito.when(dao.isConsented()).thenReturn(true);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        boolean result2 =
                AppSearchConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID, API_TYPE, mAdServicesPackageName);
        assertThat(result2).isTrue();
    }
}
