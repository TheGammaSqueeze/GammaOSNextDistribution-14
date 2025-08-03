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

package com.android.adservices.service.measurement.rollback;

import static com.android.adservices.service.measurement.rollback.MeasurementRollbackCompatManager.APEX_VERSION_WHEN_NOT_FOUND;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doReturn;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.doThrow;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.mock;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.spy;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verify;
import static com.android.dx.mockito.inline.extended.ExtendedMockito.verifyNoMoreInteractions;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;

import android.app.adservices.AdServicesManager;
import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.UserHandle;
import android.util.Pair;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.filters.SmallTest;

import com.android.adservices.mockito.AdServicesExtendedMockitoRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.mockito.Mock;
import org.mockito.quality.Strictness;

import java.util.ArrayList;
import java.util.List;

@SmallTest
public class MeasurementRollbackCompatManagerTest {
    private static final int UID = 100;
    private static final long CURRENT_APEX_VERSION = 1000;
    private static final String EXTSERVICES_PACKAGE_NAME = "com.google.android.extservices";

    private final Context mContext = spy(ApplicationProvider.getApplicationContext());

    @Mock private MeasurementRollbackWorker<String> mWorker;

    private MeasurementRollbackCompatManager mManager;

    @Rule
    public final AdServicesExtendedMockitoRule mockitoRule =
            new AdServicesExtendedMockitoRule.Builder(this)
                    .mockStatic(UserHandle.class)
                    .setStrictness(Strictness.WARN)
                    .build();

    @Before
    public void setup() {
        createManager(CURRENT_APEX_VERSION);
    }

    @Test
    public void testRecordAdServicesDeletionOccurred() {
        mManager.recordAdServicesDeletionOccurred();
        verify(mWorker)
                .recordAdServicesDeletionOccurred(
                        AdServicesManager.MEASUREMENT_DELETION, CURRENT_APEX_VERSION);
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testRecordAdServicesDeletionOccurred_noCurrentApex() {
        createManager(APEX_VERSION_WHEN_NOT_FOUND);

        mManager.recordAdServicesDeletionOccurred();
        verify(mWorker, never()).recordAdServicesDeletionOccurred(anyInt(), anyLong());
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testRecordAdServicesDeletionOccurred_exception() {
        doThrow(RuntimeException.class)
                .when(mWorker)
                .recordAdServicesDeletionOccurred(anyInt(), anyLong());

        // Manager should not crash when the worker throws an exception.
        mManager.recordAdServicesDeletionOccurred();
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_StoredDataNull() {
        doReturn(null).when(mWorker).getAdServicesDeletionRollbackMetadata(anyInt());
        assertThat(mManager.needsToHandleRollbackReconciliation()).isFalse();
        verify(mWorker).getAdServicesDeletionRollbackMetadata(anyInt());
        verify(mWorker, never()).clearAdServicesDeletionOccurred(anyString());
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_StoredVersionLower() {
        doReturn(Pair.create(CURRENT_APEX_VERSION - 1, "test"))
                .when(mWorker)
                .getAdServicesDeletionRollbackMetadata(anyInt());

        assertThat(mManager.needsToHandleRollbackReconciliation()).isFalse();
        verify(mWorker).getAdServicesDeletionRollbackMetadata(anyInt());
        verify(mWorker, never()).clearAdServicesDeletionOccurred(anyString());
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_StoredVersionEqual() {
        doReturn(Pair.create(CURRENT_APEX_VERSION, "test"))
                .when(mWorker)
                .getAdServicesDeletionRollbackMetadata(anyInt());

        assertThat(mManager.needsToHandleRollbackReconciliation()).isFalse();
        verify(mWorker).getAdServicesDeletionRollbackMetadata(anyInt());
        verify(mWorker, never()).clearAdServicesDeletionOccurred(anyString());
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_StoredVersionHigher() {
        String mockRowId = "mock_row_id";
        doReturn(Pair.create(CURRENT_APEX_VERSION + 1, mockRowId))
                .when(mWorker)
                .getAdServicesDeletionRollbackMetadata(anyInt());

        assertThat(mManager.needsToHandleRollbackReconciliation()).isTrue();
        verify(mWorker).getAdServicesDeletionRollbackMetadata(anyInt());
        verify(mWorker).clearAdServicesDeletionOccurred(eq(mockRowId));
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_noCurrentApex() {
        createManager(APEX_VERSION_WHEN_NOT_FOUND);

        assertThat(mManager.needsToHandleRollbackReconciliation()).isFalse();
        verify(mWorker, never()).clearAdServicesDeletionOccurred(any());
        verify(mWorker, never()).getAdServicesDeletionRollbackMetadata(anyInt());
        verifyNoMoreInteractions(mWorker);
    }

    @Test
    public void testNeedsToHandleRollbackReconciliation_workerException() {
        doThrow(RuntimeException.class)
                .when(mWorker)
                .getAdServicesDeletionRollbackMetadata(anyInt());
        // Manager should not crash if the worker throws an exception
        assertThat(mManager.needsToHandleRollbackReconciliation()).isFalse();
    }

    @Test
    public void testComputeApexVersion_noMatch() {
        mockApexVersion("test");
        long apex = MeasurementRollbackCompatManager.computeApexVersion(mContext);
        assertThat(apex).isEqualTo(APEX_VERSION_WHEN_NOT_FOUND);
    }

    @Test
    public void testComputeApexVersion_someMatch() {
        mockApexVersion("test", EXTSERVICES_PACKAGE_NAME);
        long apex = MeasurementRollbackCompatManager.computeApexVersion(mContext);
        assertThat(apex).isEqualTo(CURRENT_APEX_VERSION + 1);
    }

    @Test
    public void testGetUserId() {
        // Mock the user handle to return the fake user id
        UserHandle mockUserHandle = mock(UserHandle.class);
        doReturn(mockUserHandle).when(() -> UserHandle.getUserHandleForUid(anyInt()));
        doReturn(UID).when(mockUserHandle).getIdentifier();

        assertThat(MeasurementRollbackCompatManager.getUserId()).isEqualTo(Integer.toString(UID));
    }

    private void createManager(long currentApexVersion) {
        mManager =
                new MeasurementRollbackCompatManager(
                        currentApexVersion, AdServicesManager.MEASUREMENT_DELETION, mWorker);
    }

    private void mockApexVersion(String... packageNames) {
        List<PackageInfo> list = new ArrayList<>();
        for (int i = 0; i < packageNames.length; i++) {
            PackageInfo info = new PackageInfo();
            info.packageName = packageNames[i];
            info.setLongVersionCode(CURRENT_APEX_VERSION + i);
            info.isApex = true;
            list.add(info);
        }

        PackageManager packageManager = mock(PackageManager.class);
        doReturn(packageManager).when(mContext).getPackageManager();
        doReturn(list).when(packageManager).getInstalledPackages(PackageManager.MATCH_APEX);
    }
}
