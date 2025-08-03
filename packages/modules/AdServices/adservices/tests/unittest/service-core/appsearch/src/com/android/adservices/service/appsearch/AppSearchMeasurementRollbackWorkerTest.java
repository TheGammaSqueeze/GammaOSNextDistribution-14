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

import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.util.Pair;

import androidx.appsearch.app.AppSearchBatchResult;
import androidx.appsearch.app.AppSearchSession;
import androidx.appsearch.platformstorage.PlatformStorage;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.common.AdServicesDeviceSupportedRule;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.adservices.mockito.AdServicesExtendedMockitoRule;
import com.android.adservices.service.Flags;
import com.android.adservices.service.FlagsFactory;
import com.android.adservices.service.common.compat.FileCompatUtils;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.List;
import java.util.concurrent.Executor;

@SmallTest
public class AppSearchMeasurementRollbackWorkerTest {
    private static final String EXPECTED_DATABASE_NAME =
            FileCompatUtils.getAdservicesFilename("measurement_rollback");
    private static final String USERID = "user1";
    private static final long APEX_VERSION = 100L;
    private static final int APPSEARCH_WRITE_TIMEOUT_MS = 1000;

    private final Context mContext = ApplicationProvider.getApplicationContext();
    private final String mAdServicesPackageName =
            AppSearchConsentWorker.getAdServicesPackageName(mContext);
    private final Executor mExecutor = AdServicesExecutors.getBackgroundExecutor();
    private AppSearchMeasurementRollbackWorker mWorker;
    @Mock private ListenableFuture<AppSearchSession> mAppSearchSession;
    @Mock private Flags mMockFlags;

    @Rule(order = 0)
    public final AdServicesDeviceSupportedRule adServicesDeviceSupportedRule =
            new AdServicesDeviceSupportedRule();

    @Rule(order = 1)
    public final AdServicesExtendedMockitoRule adServicesExtendedMockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(PlatformStorage.class)
                    .mockStatic(AppSearchDao.class)
                    .mockStatic(FlagsFactory.class)
                    .setStrictness(Strictness.LENIENT)
                    .build();

    @Before
    public void setup() {
        doReturn(mMockFlags).when(FlagsFactory::getFlags);
        doReturn(APPSEARCH_WRITE_TIMEOUT_MS).when(mMockFlags).getAppSearchWriteTimeout();

        ArgumentCaptor<PlatformStorage.SearchContext> cap =
                ArgumentCaptor.forClass(PlatformStorage.SearchContext.class);
        doReturn(mAppSearchSession)
                .when(() -> PlatformStorage.createSearchSessionAsync(cap.capture()));

        mWorker = AppSearchMeasurementRollbackWorker.getInstance(mContext, USERID);
        assertThat(cap.getValue().getDatabaseName()).isEqualTo(EXPECTED_DATABASE_NAME);
    }

    @Test
    public void testWorkerCreation_invalidValues() {
        assertThrows(
                NullPointerException.class,
                () -> AppSearchMeasurementRollbackWorker.getInstance(null, USERID));
        assertThrows(
                NullPointerException.class,
                () -> AppSearchMeasurementRollbackWorker.getInstance(mContext, null));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testClearAdServicesDeletionOccurred() {
        AppSearchBatchResult<String, Void> mockResult =
                new AppSearchBatchResult.Builder<String, Void>().build();
        doReturn(mockResult).when(() -> AppSearchDao.deleteData(any(), any(), any(), any(), any()));

        String mockRowId = "mock_row_id";
        mWorker.clearAdServicesDeletionOccurred(mockRowId);

        verify(
                () ->
                        AppSearchDao.deleteData(
                                eq(AppSearchMeasurementRollbackDao.class),
                                eq(mAppSearchSession),
                                any(),
                                eq(mockRowId),
                                eq(AppSearchMeasurementRollbackDao.NAMESPACE)));
    }

    @Test
    public void testClearAdServicesDeletionOccurred_throwsUnchecked() {
        IllegalStateException exception = new IllegalStateException("test exception");
        doThrow(exception).when(() -> AppSearchDao.deleteData(any(), any(), any(), any(), any()));

        RuntimeException e =
                assertThrows(
                        IllegalStateException.class,
                        () -> mWorker.clearAdServicesDeletionOccurred("mock_row_id"));
        assertThat(e).isSameInstanceAs(exception);
    }

    @Test
    public void testClearAdServicesDeletionOccurred_nullInput() {
        assertThrows(
                NullPointerException.class, () -> mWorker.clearAdServicesDeletionOccurred(null));
    }

    @Test
    public void testGetAdServicesDeletionApexVersion_documentFound() {
        AppSearchMeasurementRollbackDao mockDao = mock(AppSearchMeasurementRollbackDao.class);
        doReturn(APEX_VERSION).when(mockDao).getApexVersion();
        doReturn(mockDao)
                .when(
                        () ->
                                AppSearchDao.readAppSearchSessionData(
                                        any(), any(), any(), any(), any(), any()));

        Pair<Long, String> dao =
                mWorker.getAdServicesDeletionRollbackMetadata(
                        AdServicesManager.MEASUREMENT_DELETION);
        assertThat(dao).isNotNull();
        assertThat(dao.first).isEqualTo(APEX_VERSION);
        verify(
                () ->
                        AppSearchDao.readAppSearchSessionData(
                                eq(AppSearchMeasurementRollbackDao.class),
                                eq(mAppSearchSession),
                                any(),
                                eq(AppSearchMeasurementRollbackDao.NAMESPACE),
                                eq(AppSearchMeasurementRollbackDao.getQuery(USERID)),
                                eq(mAdServicesPackageName)));
    }

    @Test
    public void testGetAdServicesDeletionApexVersion_documentNotFound() {
        doReturn(null)
                .when(
                        () ->
                                AppSearchDao.readAppSearchSessionData(
                                        any(), any(), any(), any(), any(), any()));

        Pair<Long, String> dao =
                mWorker.getAdServicesDeletionRollbackMetadata(
                        AdServicesManager.MEASUREMENT_DELETION);
        assertThat(dao).isNull();
        verify(
                () ->
                        AppSearchDao.readAppSearchSessionData(
                                eq(AppSearchMeasurementRollbackDao.class),
                                eq(mAppSearchSession),
                                any(),
                                eq(AppSearchMeasurementRollbackDao.NAMESPACE),
                                eq(AppSearchMeasurementRollbackDao.getQuery(USERID)),
                                eq(mAdServicesPackageName)));
    }

    @SuppressWarnings("FutureReturnValueIgnored")
    @Test
    public void testRecordAdServicesDeletionOccurred() {
        AppSearchBatchResult<String, Void> mockResult = mock(AppSearchBatchResult.class);
        AppSearchMeasurementRollbackDao dao = mock(AppSearchMeasurementRollbackDao.class);
        doReturn(mockResult).when(dao).writeData(any(), any(), any());

        AppSearchMeasurementRollbackWorker spyWorker = spy(mWorker);
        doReturn(dao)
                .when(spyWorker)
                .createAppSearchMeasurementRollbackDao(
                        AdServicesManager.MEASUREMENT_DELETION, APEX_VERSION);

        spyWorker.recordAdServicesDeletionOccurred(
                AdServicesManager.MEASUREMENT_DELETION, APEX_VERSION);
        verify(dao).writeData(eq(mAppSearchSession), eq(List.of()), any());
        verifyNoMoreInteractions(dao);
    }

    @Test
    public void testRecordAdServicesDeletionOccurred_throwsUnchecked() {
        AppSearchMeasurementRollbackDao dao = mock(AppSearchMeasurementRollbackDao.class);
        IllegalStateException exceptionToThrow = new IllegalStateException("test exception");
        doThrow(exceptionToThrow).when(dao).writeData(any(), any(), any());

        AppSearchMeasurementRollbackWorker spyWorker = spy(mWorker);
        doReturn(dao)
                .when(spyWorker)
                .createAppSearchMeasurementRollbackDao(
                        AdServicesManager.MEASUREMENT_DELETION, APEX_VERSION);

        IllegalStateException e =
                assertThrows(
                        IllegalStateException.class,
                        () ->
                                spyWorker.recordAdServicesDeletionOccurred(
                                        AdServicesManager.MEASUREMENT_DELETION, APEX_VERSION));
        assertThat(e).isSameInstanceAs(exceptionToThrow);
    }

    @Test
    public void testCreateAppSearchMeasurementRollbackDao() {
        AppSearchMeasurementRollbackDao dao =
                mWorker.createAppSearchMeasurementRollbackDao(
                        AdServicesManager.MEASUREMENT_DELETION, APEX_VERSION);
        assertThat(dao.getApexVersion()).isEqualTo(APEX_VERSION);
        assertThat(dao.getNamespace()).isEqualTo(AppSearchMeasurementRollbackDao.NAMESPACE);
        assertThat(dao.getUserId()).isEqualTo(USERID);
        assertThat(dao.getId())
                .isEqualTo(
                        AppSearchMeasurementRollbackDao.getRowId(
                                USERID, AdServicesManager.MEASUREMENT_DELETION));
    }
}
