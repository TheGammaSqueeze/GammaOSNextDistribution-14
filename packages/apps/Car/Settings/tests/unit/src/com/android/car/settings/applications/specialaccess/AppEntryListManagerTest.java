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

package com.android.car.settings.applications.specialaccess;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.os.Looper;

import androidx.test.annotation.UiThreadTest;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.settingslib.applications.ApplicationsState;

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
public class AppEntryListManagerTest {

    private Context mContext = ApplicationProvider.getApplicationContext();
    private AppEntryListManager mAppEntryListManager;

    @Mock
    private ApplicationsState mMockApplicationsState;
    @Mock
    private ApplicationsState.Session mMockSession;
    @Mock
    private AppEntryListManager.ExtraInfoBridge mMockExtraInfoBridge;
    @Mock
    private AppEntryListManager.AppFilterProvider mMockFilterProvider;
    @Mock
    private AppEntryListManager.Callback mMockCallback;
    @Captor
    private ArgumentCaptor<ApplicationsState.Callbacks> mSessionCallbacksCaptor;
    @Captor
    private ArgumentCaptor<List<ApplicationsState.AppEntry>> mEntriesCaptor;

    @Before
    @UiThreadTest
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        when(mMockApplicationsState.newSession(mSessionCallbacksCaptor.capture()))
                .thenReturn(mMockSession);
        when(mMockApplicationsState.getBackgroundLooper()).thenReturn(Looper.getMainLooper());

        mAppEntryListManager = new AppEntryListManager(mContext, mMockApplicationsState);
        mAppEntryListManager.init(mMockExtraInfoBridge, mMockFilterProvider, mMockCallback);
    }

    @Test
    public void start_resumesSession() {
        mAppEntryListManager.start();

        verify(mMockSession).onResume();
    }

    @Test
    public void onPackageListChanged_loadsExtraInfo() {
        mSessionCallbacksCaptor.getValue().onPackageListChanged();

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockExtraInfoBridge).loadExtraInfo(any());
    }

    @Test
    public void onLoadEntriesComplete_loadsExtraInfo() {
        mSessionCallbacksCaptor.getValue().onLoadEntriesCompleted();

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockExtraInfoBridge).loadExtraInfo(any());
    }

    @Test
    public void stop_pausesSession() {
        mAppEntryListManager.stop();

        verify(mMockSession).onPause();
    }

    @Test
    public void destroy_destroysSession() {
        mAppEntryListManager.destroy();

        verify(mMockSession).onDestroy();
    }

    @Test
    public void forceUpdate_loadsExtraInfo() {
        ArrayList<ApplicationsState.AppEntry> entries = new ArrayList<>();
        entries.add(mock(ApplicationsState.AppEntry.class));
        when(mMockSession.getAllApps()).thenReturn(entries);

        mAppEntryListManager.forceUpdate();

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockExtraInfoBridge).loadExtraInfo(entries);
    }

    @Test
    public void forceUpdate_forEntry_loadsExtraInfo() {
        ApplicationsState.AppEntry entry = mock(ApplicationsState.AppEntry.class);

        mAppEntryListManager.forceUpdate(entry);

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockExtraInfoBridge).loadExtraInfo(mEntriesCaptor.capture());
        assertThat(mEntriesCaptor.getValue()).containsExactly(entry);
    }

    @Test
    public void loadingFinished_rebuildsSession() {
        ApplicationsState.AppFilter appFilter = mock(ApplicationsState.AppFilter.class);
        when(mMockFilterProvider.getAppFilter()).thenReturn(appFilter);

        mSessionCallbacksCaptor.getValue().onLoadEntriesCompleted();

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        verify(mMockSession).rebuild(eq(appFilter),
                eq(ApplicationsState.ALPHA_COMPARATOR), /* foreground= */ eq(false));
    }

    @Test
    public void onRebuildComplete_callsCallback() {
        ArrayList<ApplicationsState.AppEntry> entries = new ArrayList<>();
        entries.add(mock(ApplicationsState.AppEntry.class));

        mSessionCallbacksCaptor.getValue().onRebuildComplete(entries);

        verify(mMockCallback).onAppEntryListChanged(entries);
    }
}
