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

import static com.android.car.settings.datausage.AppSpecificDataUsageManager.NETWORK_CYCLE_LOADER_ID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import android.content.Context;
import android.net.NetworkTemplate;

import androidx.loader.app.LoaderManager;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.settingslib.net.NetworkCycleDataForUid;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class AppSpecificDataUsageManagerTest {

    private static final int TEST_UID = 1;

    private Context mContext = ApplicationProvider.getApplicationContext();
    private AppSpecificDataUsageManager mAppSpecificDataUsageManager;

    @Captor
    private ArgumentCaptor<LoaderManager.LoaderCallbacks<List<NetworkCycleDataForUid>>>
            mCallbacksArgumentCaptor;
    @Mock
    private AppSpecificDataUsageManager.AppSpecificDataLoaderCallback mCallback1;
    @Mock
    private AppSpecificDataUsageManager.AppSpecificDataLoaderCallback mCallback2;
    @Mock
    private LoaderManager mLoaderManager;
    @Mock
    private NetworkTemplate mNetworkTemplate;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);

        mAppSpecificDataUsageManager = new AppSpecificDataUsageManager(mContext, mNetworkTemplate,
                TEST_UID);
        mAppSpecificDataUsageManager.startLoading(mLoaderManager);

        verify(mLoaderManager).restartLoader(eq(NETWORK_CYCLE_LOADER_ID), eq(null),
                mCallbacksArgumentCaptor.capture());
    }

    @Test
    public void callback_onLoadFinished_listenerOnDataLoadedCalled() {
        mAppSpecificDataUsageManager.registerListener(mCallback1);
        mAppSpecificDataUsageManager.registerListener(mCallback2);

        List<NetworkCycleDataForUid> dataList = new ArrayList<>();

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, dataList);

        verify(mCallback1).onDataLoaded(dataList);
        verify(mCallback2).onDataLoaded(dataList);
    }

    @Test
    public void callback_unregisterListener_onlyOneListenerOnDataLoadedCalled() {
        mAppSpecificDataUsageManager.registerListener(mCallback1);
        mAppSpecificDataUsageManager.registerListener(mCallback2);
        mAppSpecificDataUsageManager.unregisterListener(mCallback2);

        List<NetworkCycleDataForUid> dataList = new ArrayList<>();

        mCallbacksArgumentCaptor.getValue().onLoadFinished(null, dataList);

        verify(mCallback1).onDataLoaded(dataList);
        verify(mCallback2, never()).onDataLoaded(dataList);
    }

    @Test
    public void callback_notLoaded_listenerOnDataLoadedNotCalled() {
        mAppSpecificDataUsageManager.registerListener(mCallback1);
        mAppSpecificDataUsageManager.registerListener(mCallback2);
        mAppSpecificDataUsageManager.unregisterListener(mCallback2);

        List<NetworkCycleDataForUid> dataList = new ArrayList<>();

        verify(mCallback1, never()).onDataLoaded(dataList);
        verify(mCallback2, never()).onDataLoaded(dataList);
    }
}
