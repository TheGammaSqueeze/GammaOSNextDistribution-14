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
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.consent.ConsentManager;
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
public class AppSearchInteractionsDaoTest {
    private static final String ID = "1";
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
        String apiType = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;
        int value = PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT_FF.ordinal();
        AppSearchInteractionsDao dao =
                new AppSearchInteractionsDao(ID, ID, NAMESPACE, apiType, value);
        assertThat(dao.toString())
                .isEqualTo(
                        "id="
                                + ID
                                + "; userId="
                                + ID
                                + "; namespace="
                                + NAMESPACE
                                + "; apiType="
                                + apiType
                                + "; value="
                                + value);
    }

    @Test
    public void testEquals() {
        String apiType1 = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;
        String apiType2 = AppSearchInteractionsDao.API_TYPE_INTERACTIONS;
        int val1 = PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT_FF.ordinal();
        int val2 = ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED;
        AppSearchInteractionsDao dao1 =
                new AppSearchInteractionsDao(ID, ID, NAMESPACE, apiType1, val1);
        AppSearchInteractionsDao dao2 =
                new AppSearchInteractionsDao(ID, ID, NAMESPACE, apiType1, val1);
        AppSearchInteractionsDao dao3 =
                new AppSearchInteractionsDao(ID, "foo", NAMESPACE, apiType2, val2);
        assertThat(dao1.equals(dao2)).isTrue();
        assertThat(dao1.equals(dao3)).isFalse();
        assertThat(dao2.equals(dao3)).isFalse();
    }

    @Test
    public void testGetQuery() {
        String apiType = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;
        String expected = "userId:" + ID + " " + "apiType:" + apiType;
        assertThat(AppSearchInteractionsDao.getQuery(ID, apiType)).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        String apiType = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;
        assertThat(AppSearchInteractionsDao.getRowId(ID, apiType)).isEqualTo(ID + "_" + apiType);
    }

    @Test
    public void testGetPrivacySandboxFeatureType_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        PrivacySandboxFeatureType result =
                AppSearchInteractionsDao.getPrivacySandboxFeatureType(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED);
    }

    @Test
    public void testGetPrivacySandboxFeatureType() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        String apiType = AppSearchInteractionsDao.API_TYPE_PRIVACY_SANDBOX_FEATURE;

        String query = "userId:" + ID + " " + "apiType:" + apiType;
        AppSearchInteractionsDao dao = Mockito.mock(AppSearchInteractionsDao.class);
        Mockito.when(dao.getValue())
                .thenReturn(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT.ordinal());
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        PrivacySandboxFeatureType result =
                AppSearchInteractionsDao.getPrivacySandboxFeatureType(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isEqualTo(PrivacySandboxFeatureType.PRIVACY_SANDBOX_FIRST_CONSENT);
    }

    @Test
    public void testGetManualInteractions_null() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        ExtendedMockito.doReturn(null)
                .when(() -> AppSearchDao.readConsentData(any(), any(), any(), any(), any(), any()));
        int result =
                AppSearchInteractionsDao.getManualInteractions(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isEqualTo(ConsentManager.NO_MANUAL_INTERACTIONS_RECORDED);
    }

    @Test
    public void testGetManualInteractions() {
        ListenableFuture mockSearchSession = Mockito.mock(ListenableFuture.class);
        Executor mockExecutor = Mockito.mock(Executor.class);
        String apiType = AppSearchInteractionsDao.API_TYPE_INTERACTIONS;

        String query = "userId:" + ID + " " + "apiType:" + apiType;
        AppSearchInteractionsDao dao = Mockito.mock(AppSearchInteractionsDao.class);
        Mockito.when(dao.getValue()).thenReturn(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
        ExtendedMockito.doReturn(dao)
                .when(
                        () ->
                                AppSearchDao.readConsentData(
                                        any(), any(), any(), any(), eq(query), any()));
        int result =
                AppSearchInteractionsDao.getManualInteractions(
                        mockSearchSession, mockExecutor, ID, mAdServicesPackageName);
        assertThat(result).isEqualTo(ConsentManager.MANUAL_INTERACTIONS_RECORDED);
    }
}
