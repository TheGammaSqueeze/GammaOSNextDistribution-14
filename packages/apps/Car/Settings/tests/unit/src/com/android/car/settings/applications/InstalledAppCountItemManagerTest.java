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

package com.android.car.settings.applications;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class InstalledAppCountItemManagerTest {

    private Context mContext = spy(ApplicationProvider.getApplicationContext());
    private InstalledAppCountItemManager mInstalledAppCountItemManager;

    @Mock
    private ApplicationInfo mMockApplicationInfo;
    @Mock
    private PackageManager mMockPm;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);

        mInstalledAppCountItemManager = new InstalledAppCountItemManager(mContext);
    }

    @Test
    public void isUpdatedSystemApp_isCounted() {
        mMockApplicationInfo.flags = ApplicationInfo.FLAG_UPDATED_SYSTEM_APP;

        assertThat(mInstalledAppCountItemManager.shouldCountApp(mMockApplicationInfo)).isTrue();
    }

    @Test
    public void isSystemApp_userCanOpen_isCounted() {
        mMockApplicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        List<ResolveInfo> intents = new ArrayList<>();
        intents.add(new ResolveInfo());
        when(mContext.getPackageManager()).thenReturn(mMockPm);
        when(mMockPm.queryIntentActivitiesAsUser(any(), anyInt(), anyInt())).thenReturn(intents);

        assertThat(mInstalledAppCountItemManager.shouldCountApp(mMockApplicationInfo)).isTrue();
    }

    @Test
    public void isSystemApp_userCannotOpen_isNotCounted() {
        mMockApplicationInfo.flags = ApplicationInfo.FLAG_SYSTEM;
        List<ResolveInfo> intents = new ArrayList<>();
        when(mContext.getPackageManager()).thenReturn(mMockPm);
        when(mMockPm.queryIntentActivitiesAsUser(any(), anyInt(), anyInt())).thenReturn(intents);

        assertThat(mInstalledAppCountItemManager.shouldCountApp(mMockApplicationInfo)).isFalse();
    }
}
