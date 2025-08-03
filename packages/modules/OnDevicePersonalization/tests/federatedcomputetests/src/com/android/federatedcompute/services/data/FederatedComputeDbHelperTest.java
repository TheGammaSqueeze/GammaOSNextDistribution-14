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

package com.android.federatedcompute.services.data;

import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION;
import static com.android.adservices.service.stats.AdServicesStatsLog.AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.federatedcompute.services.data.FederatedComputeEncryptionKeyContract.ENCRYPTION_KEY_TABLE;
import static com.android.federatedcompute.services.data.FederatedTraningTaskContract.FEDERATED_TRAINING_TASKS_TABLE;
import static com.android.federatedcompute.services.data.ODPAuthorizationTokenContract.ODP_AUTHORIZATION_TOKEN_TABLE;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.database.Cursor;
import android.database.DatabaseUtils;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteException;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.federatedcompute.services.common.Flags;
import com.android.federatedcompute.services.common.FlagsFactory;
import com.android.federatedcompute.services.statsd.ClientErrorLogger;
import com.android.modules.utils.testing.ExtendedMockitoRule;
import com.android.modules.utils.testing.ExtendedMockitoRule.MockStatic;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.ArrayList;

@RunWith(AndroidJUnit4.class)
@MockStatic(FlagsFactory.class)
@MockStatic(ClientErrorLogger.class)
public final class FederatedComputeDbHelperTest {
    private Context mContext;

    @Rule
    public final ExtendedMockitoRule extendedMockitoRule =
            new ExtendedMockitoRule.Builder(this).setStrictness(Strictness.LENIENT).build();

    @Mock private Flags mMockFlags;
    @Mock private ClientErrorLogger mMockClientErrorLogger;

    @Before
    public void setUp() {
        mContext = ApplicationProvider.getApplicationContext();
        when(FlagsFactory.getFlags()).thenReturn(mMockFlags);
        when(ClientErrorLogger.getInstance()).thenReturn(mMockClientErrorLogger);
    }

    @After
    public void cleanUp() throws Exception {
        FederatedComputeDbHelper.resetInstance();
    }

    @Test
    public void onCreate() {
        FederatedComputeDbHelper dbHelper = FederatedComputeDbHelper.getInstanceForTest(mContext);

        SQLiteDatabase db = dbHelper.getReadableDatabase();

        assertThat(db).isNotNull();
        assertThat(DatabaseUtils.queryNumEntries(db, FEDERATED_TRAINING_TASKS_TABLE)).isEqualTo(0);
        assertThat(DatabaseUtils.queryNumEntries(db, ENCRYPTION_KEY_TABLE)).isEqualTo(0);
        assertThat(DatabaseUtils.queryNumEntries(db, ODP_AUTHORIZATION_TOKEN_TABLE)).isEqualTo(0);

        // query number of tables
        ArrayList<String> tableNames = new ArrayList<String>();
        Cursor cursor = db.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null);
        if (cursor.moveToFirst()) {
            while (!cursor.isAfterLast()) {
                tableNames.add(cursor.getString(cursor.getColumnIndex("name")));
                cursor.moveToNext();
            }
        }
        String[] expectedTables = {
            FEDERATED_TRAINING_TASKS_TABLE, ENCRYPTION_KEY_TABLE, ODP_AUTHORIZATION_TOKEN_TABLE
        };
        // android metadata table also exists in the database
        assertThat(tableNames).containsAtLeastElementsIn(expectedTables);
    }

    @Test
    public void testSafeGetReadableDatabase_exceptionOccurs_validatesErrorLogging() {
        FederatedComputeDbHelper dbHelper =
                spy(FederatedComputeDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getReadableDatabase();

        SQLiteDatabase db = dbHelper.safeGetReadableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_READ_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }

    @Test
    public void testSafeGetWriteDatabase_exceptionOccurs_validatesErrorLogging() {
        FederatedComputeDbHelper dbHelper =
                spy(FederatedComputeDbHelper.getInstanceForTest(mContext));
        Throwable tr = new SQLiteException();
        doThrow(tr).when(dbHelper).getWritableDatabase();

        SQLiteDatabase db = dbHelper.safeGetWritableDatabase();

        assertThat(db).isNull();
        verify(mMockClientErrorLogger)
                .logErrorWithExceptionInfo(
                        any(),
                        eq(AD_SERVICES_ERROR_REPORTED__ERROR_CODE__DATABASE_WRITE_EXCEPTION),
                        eq(AD_SERVICES_ERROR_REPORTED__PPAPI_NAME__FEDERATED_COMPUTE));
    }
}
