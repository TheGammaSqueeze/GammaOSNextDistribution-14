/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.uwb;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.BugreportManager;
import android.platform.test.annotations.Presubmit;

import androidx.test.filters.SmallTest;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.List;

/**
 * Unit tests for {@link com.android.server.uwb.UwbDiagnostics}.
 */
@RunWith(AndroidJUnit4.class)
@SmallTest
@Presubmit
public class UwbDiagnosticsTest {
    @Mock SystemBuildProperties mBuildProperties;
    @Mock Context mContext;
    @Mock UwbInjector mUwbInjector;
    @Mock DeviceConfigFacade mDeviceConfigFacade;
    @Mock BugreportManager mBugreportManager;
    @Mock PackageManager mPackageManager;
    @Mock List<ResolveInfo>  mResolveInfoList;
    UwbDiagnostics mUwbDiagnostics;

    private static final int BUG_REPORT_MIN_INTERVAL_MS = 3600_000;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mUwbInjector.getDeviceConfigFacade()).thenReturn(mDeviceConfigFacade);
        when(mDeviceConfigFacade.getBugReportMinIntervalMs())
                .thenReturn(BUG_REPORT_MIN_INTERVAL_MS);
        when(mBuildProperties.isUserdebugBuild()).thenReturn(true);
        when(mContext.getSystemService(BugreportManager.class)).thenReturn(mBugreportManager);
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.queryIntentActivities(any(), anyInt())).thenReturn(mResolveInfoList);
        mUwbDiagnostics = new UwbDiagnostics(mContext, mUwbInjector, mBuildProperties);
    }

    @Test
    public void takeBugReportDoesNothingOnUserBuild() throws Exception {
        when(mBuildProperties.isUserBuild()).thenReturn(true);
        mUwbDiagnostics.takeBugReport("");
        assertThat(mUwbDiagnostics.getLastBugReportTimeMs()).isEqualTo(0);
    }

    @Test
    public void takeBugReportTwiceWithInsufficientTimeGapSkipSecondRequest() throws Exception {
        // 1st attempt should succeed
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(10L);
        mUwbDiagnostics.takeBugReport("");
        assertThat(mUwbDiagnostics.getLastBugReportTimeMs()).isEqualTo(10L);
        // 2nd attempt should fail
        when(mUwbInjector.getElapsedSinceBootMillis()).thenReturn(BUG_REPORT_MIN_INTERVAL_MS - 20L);
        mUwbDiagnostics.takeBugReport("");
        assertThat(mUwbDiagnostics.getLastBugReportTimeMs()).isEqualTo(10L);
    }
}
