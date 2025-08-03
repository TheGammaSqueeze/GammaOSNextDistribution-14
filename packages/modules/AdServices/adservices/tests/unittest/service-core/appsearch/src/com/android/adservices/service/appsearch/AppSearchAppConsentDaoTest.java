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

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.ListenableFuture;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mockito;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
public class AppSearchAppConsentDaoTest {
    private static final String ID = "1";
    private static final String ID2 = "2";
    private static final String NAMESPACE = "consent";
    private static final List<String> APPS =
            ImmutableList.of(ApplicationProvider.getApplicationContext().getPackageName());
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
        AppSearchAppConsentDao dao =
                new AppSearchAppConsentDao(
                        ID, ID, NAMESPACE, AppSearchAppConsentDao.APPS_WITH_CONSENT, APPS);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; consentType="
                                + AppSearchAppConsentDao.APPS_WITH_CONSENT
                                + "; namespace="
                                + NAMESPACE
                                + "; apps="
                                + Arrays.toString(APPS.toArray()));
    }

    @Test
    public void testEquals() {
        AppSearchAppConsentDao dao1 =
                new AppSearchAppConsentDao(
                        ID, ID, NAMESPACE, AppSearchAppConsentDao.APPS_WITH_CONSENT, APPS);
        AppSearchAppConsentDao dao2 =
                new AppSearchAppConsentDao(
                        ID, ID, NAMESPACE, AppSearchAppConsentDao.APPS_WITH_CONSENT, APPS);
        AppSearchAppConsentDao dao3 =
                new AppSearchAppConsentDao(
                        ID, ID, NAMESPACE, AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT, APPS);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String expected =
                "userId:" + ID + " " + "consentType:" + AppSearchAppConsentDao.APPS_WITH_CONSENT;
        assertThat(AppSearchAppConsentDao.getQuery(ID, AppSearchAppConsentDao.APPS_WITH_CONSENT))
                .isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        String expected = ID + "_" + consentType;
        assertThat(AppSearchAppConsentDao.getRowId(ID, consentType)).isEqualTo(expected);
    }

    @Test
    public void testReadConsentData_null() {
        String consentType = AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT;
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        AppSearchAppConsentDao result =
                AppSearchAppConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID, consentType, mAdServicesPackageName);
        assertThat(result).isNull();
    }

    @Test
    public void testReadConsentData() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);

        String consentType = AppSearchAppConsentDao.APPS_WITH_CONSENT;
        String query = "userId:" + ID + " " + "consentType:" + consentType;
        AppSearchAppConsentDao dao = Mockito.mock(AppSearchAppConsentDao.class);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        AppSearchAppConsentDao result =
                AppSearchAppConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID, consentType, mAdServicesPackageName);
        assertThat(result).isEqualTo(dao);

        // Confirm that the right value is returned even when it is true.
        String consentType2 = AppSearchAppConsentDao.APPS_WITH_REVOKED_CONSENT;
        String query2 = "userId:" + ID2 + " " + "consentType:" + consentType2;
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query2), any()));
        AppSearchAppConsentDao result2 =
                AppSearchAppConsentDao.readConsentData(
                        mockSearchSession, mockExecutor, ID2, consentType2, mAdServicesPackageName);
        assertThat(result2).isEqualTo(dao);
    }
}
