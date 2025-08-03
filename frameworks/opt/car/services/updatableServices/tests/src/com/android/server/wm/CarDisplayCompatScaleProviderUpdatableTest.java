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

package com.android.server.wm;

import static android.view.Display.DEFAULT_DISPLAY;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.content.pm.FeatureInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

@RunWith(AndroidJUnit4.class)
public class CarDisplayCompatScaleProviderUpdatableTest {
    private CarDisplayCompatScaleProviderUpdatableImpl mImpl;
    private MockitoSession mMockingSession;

    @Mock
    private Context mContext;
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PackageInfo mPackageInfo;

    private final CarDisplayCompatScaleProviderInterface mInterface =
            new CarDisplayCompatScaleProviderInterface() {
                @Override
                public int getMainDisplayAssignedToUser(int userId) {
                    return DEFAULT_DISPLAY;
                }

                @Override
                public boolean isPrivileged(ApplicationInfo applicationInfo) {
                    return false;
                }
            };

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .strictness(Strictness.LENIENT)
                .startMocking();
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mPackageManager.hasSystemFeature(eq("android.car.displaycompatibility")))
                .thenReturn(true);
        mImpl = new CarDisplayCompatScaleProviderUpdatableImpl(mContext, mInterface);
    }

    @After
    public void tearDown() {
        // If the exception is thrown during the MockingSession setUp, mMockingSession can be null.
        if (mMockingSession != null) {
            mMockingSession.finishMocking();
        }
    }

    @Test
    public void requiresDisplayCompat_returnsTrue() throws NameNotFoundException {
        FeatureInfo[] features = new FeatureInfo[1];
        features[0] = new FeatureInfo();
        features[0].name = "android.car.displaycompatibility";
        mPackageInfo.reqFeatures = features;
        when(mPackageManager.getPackageInfo(eq("package1"), any(PackageInfoFlags.class)))
                .thenReturn(mPackageInfo);

        assertThat(mImpl.requiresDisplayCompat("package1", 10)).isTrue();
    }

    @Test
    public void requiresDisplayCompat_returnsFalse() throws NameNotFoundException {
        when(mPackageManager.getPackageInfo(eq("package1"), any(PackageInfoFlags.class)))
                .thenReturn(mPackageInfo);
        assertThat(mImpl.requiresDisplayCompat("package1", 10)).isFalse();
    }

    @Test
    public void requiresDisplayCompat_packageStateIsCached() throws NameNotFoundException {
        FeatureInfo[] features = new FeatureInfo[1];
        features[0] = new FeatureInfo();
        features[0].name = "android.car.displaycompatibility";
        mPackageInfo.reqFeatures = features;
        when(mPackageManager.getPackageInfo(eq("package1"), any(PackageInfoFlags.class)))
                .thenReturn(mPackageInfo);
        mImpl.requiresDisplayCompat("package1", 10);

        assertThat(mImpl.requiresDisplayCompat("package1", 10)).isTrue();
        // Verify the number of calls to PackageManager#getPackageInfo did not increase.
        verify(mPackageManager, times(1)).getPackageInfo(eq("package1"),
                any(PackageInfoFlags.class));
    }
}
