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

package com.android.car.settings.qc;

import static com.google.common.truth.Truth.assertThat;

import static org.testng.Assert.assertThrows;

import android.content.Context;
import android.net.Uri;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.dx.mockito.inline.extended.ExtendedMockito;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Set;

@RunWith(AndroidJUnit4.class)
public class SettingsQCProviderTest {
    private Context mContext = ApplicationProvider.getApplicationContext();
    private SettingsQCProvider mProvider;

    @Before
    public void setUp() {
        SettingsQCRegistry.sUriToQC.clear();
        SettingsQCRegistry.sUriToQC.put(QCTestUtils.URI, TestSettingsQCItem.class);
        mProvider = new SettingsQCProvider();
        ExtendedMockito.spyOn(mProvider);
        ExtendedMockito.doReturn(mContext).when(mProvider).getContext();
        ExtendedMockito.doReturn(mContext.getPackageName()).when(mProvider).getCallingPackage();
    }

    @Test
    public void onCreate_allowlistSet() {
        mProvider.onCreate();
        Set<String> allowlist = mProvider.getAllowlistedPackages();
        assertThat(allowlist.contains(QCTestUtils.AUTHORITY)).isTrue();
    }

    @Test
    public void onBind_invalidUri_throwsException() {
        mProvider.onCreate();
        assertThrows(IllegalArgumentException.class,
                () -> mProvider.onBind(Uri.EMPTY));
    }

    @Test
    public void onBind_validUri_returnsQCItem() {
        mProvider.onCreate();
        assertThat(mProvider.onBind(QCTestUtils.URI)).isNotNull();
    }

    @Test
    public void onSubscribe_startsBackgroundWorker() {
        mProvider.onCreate();
        mProvider.onSubscribed(QCTestUtils.URI);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SettingsQCBackgroundWorker worker = mProvider.mSubscribedWorkers.get(QCTestUtils.URI);
        assertThat(worker).isNotNull();
        assertThat(worker instanceof TestSettingsQCBackgroundWorker).isTrue();
        TestSettingsQCBackgroundWorker testWorker = (TestSettingsQCBackgroundWorker) worker;
        assertThat(testWorker.isSubscribed()).isTrue();
    }

    @Test
    public void onSubscribe_stopsBackgroundWorker() {
        mProvider.onCreate();
        mProvider.onSubscribed(QCTestUtils.URI);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        SettingsQCBackgroundWorker worker = mProvider.mSubscribedWorkers.get(QCTestUtils.URI);
        assertThat(worker).isNotNull();
        assertThat(worker instanceof TestSettingsQCBackgroundWorker).isTrue();
        TestSettingsQCBackgroundWorker testWorker = (TestSettingsQCBackgroundWorker) worker;
        mProvider.onUnsubscribed(QCTestUtils.URI);
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        assertThat(testWorker.isSubscribed()).isFalse();
        assertThat(mProvider.mSubscribedWorkers.containsKey(QCTestUtils.URI)).isFalse();
    }
}
