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

package com.android.mms.service.metrics;

import static com.android.mms.MmsStatsLog.INCOMING_MMS;
import static com.android.mms.MmsStatsLog.OUTGOING_MMS;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.StatsManager;
import android.content.Context;
import android.util.StatsEvent;

import com.android.mms.IncomingMms;
import com.android.mms.OutgoingMms;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class MmsMetricsCollectorTest {
    private static final long MIN_COOLDOWN_MILLIS = 23L * 3600L * 1000L;
    Context mContext;
    private PersistMmsAtomsStorage mPersistMmsAtomsStorage;
    private MmsMetricsCollector mMmsMetricsCollector;

    @Before
    public void setUp() {
        mContext = mock(Context.class);
        mPersistMmsAtomsStorage = mock(PersistMmsAtomsStorage.class);
        mMmsMetricsCollector = new MmsMetricsCollector(mContext, mPersistMmsAtomsStorage);
    }

    @After
    public void tearDown() {
        mContext = null;
        mPersistMmsAtomsStorage = null;
        mMmsMetricsCollector = null;
    }

    @Test
    public void onPullAtom_incomingMms_empty() {
        doReturn(new ArrayList<>()).when(mPersistMmsAtomsStorage).getIncomingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(INCOMING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_incomingMms_tooFrequent() {
        doReturn(null).when(mPersistMmsAtomsStorage).getIncomingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(INCOMING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistMmsAtomsStorage, times(1))
                .getIncomingMms(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistMmsAtomsStorage);
    }

    @Test
    public void onPullAtom_incomingMms_multipleMms() {
        IncomingMms incomingMms = IncomingMms.newBuilder().build();
        doReturn(Arrays.asList(incomingMms, incomingMms, incomingMms, incomingMms))
                .when(mPersistMmsAtomsStorage).getIncomingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(INCOMING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_outgoingMms_empty() {
        doReturn(new ArrayList<>()).when(mPersistMmsAtomsStorage).getOutgoingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(OUTGOING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }

    @Test
    public void onPullAtom_outgoingMms_tooFrequent() {
        doReturn(null).when(mPersistMmsAtomsStorage).getOutgoingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(OUTGOING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(0);
        assertThat(result).isEqualTo(StatsManager.PULL_SKIP);
        verify(mPersistMmsAtomsStorage, times(1))
                .getOutgoingMms(eq(MIN_COOLDOWN_MILLIS));
        verifyNoMoreInteractions(mPersistMmsAtomsStorage);
    }

    @Test
    public void onPullAtom_outgoingMms_multipleMms() {
        OutgoingMms outgoingMms = OutgoingMms.newBuilder().build();
        doReturn(Arrays.asList(outgoingMms, outgoingMms, outgoingMms, outgoingMms))
                .when(mPersistMmsAtomsStorage).getOutgoingMms(anyLong());
        List<StatsEvent> actualAtoms = new ArrayList<>();

        int result = mMmsMetricsCollector.onPullAtom(OUTGOING_MMS, actualAtoms);

        assertThat(actualAtoms).hasSize(4);
        assertThat(result).isEqualTo(StatsManager.PULL_SUCCESS);
    }
}