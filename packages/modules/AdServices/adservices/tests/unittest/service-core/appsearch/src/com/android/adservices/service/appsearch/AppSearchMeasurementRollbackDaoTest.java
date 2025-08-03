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

import static com.android.adservices.service.appsearch.AppSearchMeasurementRollbackDao.getRowId;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.any;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.eq;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;

import static org.junit.Assert.assertThrows;

import android.app.adservices.AdServicesManager;

import androidx.appsearch.app.AppSearchSession;

import com.android.adservices.common.AdServicesExtendedMockitoTestCase;
import com.android.adservices.concurrency.AdServicesExecutors;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import com.google.common.util.concurrent.ListenableFuture;

import org.junit.Test;
import org.mockito.Mock;
import org.mockito.Mockito;

import java.util.concurrent.Executor;

@MockStatic(AppSearchDao.class)
public final class AppSearchMeasurementRollbackDaoTest extends AdServicesExtendedMockitoTestCase {
    private static final String ID = "ID";
    private static final String USER_ID = "USER_ID";
    private static final String NAMESPACE = "test_namespace";
    private static final long APEX_VERSION = 100L;

    private final Executor mExecutor = AdServicesExecutors.getBackgroundExecutor();
    private final String mAdServicePackageName =
            AppSearchConsentWorker.getAdServicesPackageName(mContext);
    @Mock private ListenableFuture<AppSearchSession> mAppSearchSession;

    @Test
    public void testGetProperties() {
        AppSearchMeasurementRollbackDao dao =
                new AppSearchMeasurementRollbackDao(ID, NAMESPACE, USER_ID, APEX_VERSION);
        expect.that(dao.getId()).isEqualTo(ID);
        expect.that(dao.getNamespace()).isEqualTo(NAMESPACE);
        expect.that(dao.getUserId()).isEqualTo(USER_ID);
        expect.that(dao.getApexVersion()).isEqualTo(APEX_VERSION);
    }

    @Test
    public void testEqualsAndHashcode() {
        String id = ID;
        String namespace = NAMESPACE;
        String userId = USER_ID;
        long apexVersion = APEX_VERSION;

        AppSearchMeasurementRollbackDao equals1 =
                new AppSearchMeasurementRollbackDao(id, namespace, userId, apexVersion);
        AppSearchMeasurementRollbackDao equals2 =
                new AppSearchMeasurementRollbackDao(id, namespace, userId, apexVersion);
        AppSearchMeasurementRollbackDao different1 =
                new AppSearchMeasurementRollbackDao(id + "42", namespace, userId, apexVersion);
        AppSearchMeasurementRollbackDao different2 =
                new AppSearchMeasurementRollbackDao(id, namespace + "42", userId, apexVersion);
        AppSearchMeasurementRollbackDao different3 =
                new AppSearchMeasurementRollbackDao(id, namespace, userId + "42", apexVersion);
        AppSearchMeasurementRollbackDao different4 =
                new AppSearchMeasurementRollbackDao(id, namespace, userId, apexVersion + 42);

        expectObjectsAreEqual(equals1, equals1);
        expectObjectsAreEqual(equals1, equals2);

        expectObjectsAreNotEqual(equals1, null);
        expectObjectsAreNotEqual(equals1, "DAO, Y U NO STRING?");
        expectObjectsAreNotEqual(equals1, different1);
        expectObjectsAreNotEqual(equals1, different2);
        expectObjectsAreNotEqual(equals1, different3);
        expectObjectsAreNotEqual(equals1, different4);
    }

    @Test
    public void testToString() {
        AppSearchMeasurementRollbackDao dao1 =
                new AppSearchMeasurementRollbackDao(ID, NAMESPACE, USER_ID, APEX_VERSION);
        String expected =
                String.format(
                        "id=%s; userId=%s; namespace=%s; apexVersion=%d",
                        ID, USER_ID, NAMESPACE, APEX_VERSION);
        expect.that(dao1.toString()).isEqualTo(expected);
    }

    @Test
    public void testGetRowId() {
        String expected = String.format("Measurement_Rollback_%s_0", ID);
        expect.that(getRowId(ID, AdServicesManager.MEASUREMENT_DELETION)).isEqualTo(expected);

        assertThrows(
                NullPointerException.class,
                () -> getRowId(null, AdServicesManager.MEASUREMENT_DELETION));
    }

    @Test
    public void testGetQuery() {
        String expected = "userId:" + ID;
        expect.that(AppSearchMeasurementRollbackDao.getQuery(ID)).isEqualTo(expected);
    }

    @Test
    public void testReadDocument_invalidInputs() {
        assertThrows(
                NullPointerException.class,
                () ->
                        AppSearchMeasurementRollbackDao.readDocument(
                                null, mExecutor, ID, mAdServicePackageName));
        assertThrows(
                NullPointerException.class,
                () ->
                        AppSearchMeasurementRollbackDao.readDocument(
                                mAppSearchSession, null, ID, mAdServicePackageName));
            assertThrows(
                    NullPointerException.class,
                    () ->
                            AppSearchMeasurementRollbackDao.readDocument(
                                    mAppSearchSession, mExecutor, null, mAdServicePackageName));
        expect.that(
                        AppSearchMeasurementRollbackDao.readDocument(
                                mAppSearchSession, mExecutor, "", mAdServicePackageName))
                .isNull();
    }

    @Test
    public void testReadDocument() {
            AppSearchMeasurementRollbackDao mockDao =
                    Mockito.mock(AppSearchMeasurementRollbackDao.class);
            doReturn(mockDao)
                    .when(
                            () ->
                                    AppSearchDao.readAppSearchSessionData(
                                            any(), any(), any(), any(), any(), any()));

        AppSearchMeasurementRollbackDao returned =
                AppSearchMeasurementRollbackDao.readDocument(
                        mAppSearchSession, mExecutor, ID, mAdServicePackageName);
        expect.that(returned).isEqualTo(mockDao);
        verify(
                () ->
                        AppSearchDao.readAppSearchSessionData(
                                eq(AppSearchMeasurementRollbackDao.class),
                                eq(mAppSearchSession),
                                eq(mExecutor),
                                eq(AppSearchMeasurementRollbackDao.NAMESPACE),
                                eq(AppSearchMeasurementRollbackDao.getQuery(ID)),
                                eq(mAdServicePackageName)));
    }
}
