/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.car.settings.datausage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.app.usage.NetworkStats;
import android.app.usage.NetworkStatsManager;
import android.content.Context;
import android.net.NetworkPolicyManager;
import android.os.Bundle;

import androidx.loader.app.LoaderManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

@RunWith(AndroidJUnit4.class)
public class AppsNetworkStatsManagerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private AppsNetworkStatsManager mAppsNetworkStatsManager;

    @Captor
    private ArgumentCaptor<LoaderManager.LoaderCallbacks<NetworkStats>> mCallbacksArgumentCaptor;
    @Mock
    private AppsNetworkStatsManager.Callback mCallback1;
    @Mock
    private AppsNetworkStatsManager.Callback mCallback2;
    @Mock
    private LoaderManager mLoaderManager;
    @Mock
    private NetworkStatsManager mNetworkStatsManager;
    @Mock
    private NetworkPolicyManager mNetworkPolicyManager;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        when(mNetworkPolicyManager.getUidsWithPolicy(anyInt())).thenReturn(new int[0]);

        mAppsNetworkStatsManager = new AppsNetworkStatsManager(
                mContext, mNetworkPolicyManager, mNetworkStatsManager);
        mAppsNetworkStatsManager.startLoading(mLoaderManager, Bundle.EMPTY);

        verify(mLoaderManager).restartLoader(eq(1), eq(Bundle.EMPTY),
                mCallbacksArgumentCaptor.capture());
    }

    @Test
    public void callback_onLoadFinished_listenerOnDataLoadedCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);

        NetworkStats networkStats = mock(NetworkStats.class);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, networkStats);

        verify(mCallback1).onDataLoaded(eq(networkStats), any());
        verify(mCallback2).onDataLoaded(eq(networkStats), any());
    }

    @Test
    public void callback_unregisterListener_onlyOneListenerOnDataLoadedCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);
        mAppsNetworkStatsManager.unregisterListener(mCallback2);

        NetworkStats networkStats = mock(NetworkStats.class);

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, networkStats);

        verify(mCallback1).onDataLoaded(eq(networkStats), any());
        verify(mCallback2, never()).onDataLoaded(eq(networkStats), any());
    }

    @Test
    public void callback_notLoaded_listenerOnDataLoadedNotCalled() throws Exception {
        mAppsNetworkStatsManager.registerListener(mCallback1);
        mAppsNetworkStatsManager.registerListener(mCallback2);
        mAppsNetworkStatsManager.unregisterListener(mCallback2);

        verify(mCallback1, never()).onDataLoaded(any(), any());
        verify(mCallback2, never()).onDataLoaded(any(), any());
    }
}
