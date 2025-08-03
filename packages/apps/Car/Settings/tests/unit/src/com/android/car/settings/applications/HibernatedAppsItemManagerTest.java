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

import static android.provider.DeviceConfig.NAMESPACE_APP_HIBERNATION;

import static com.android.car.settings.applications.ApplicationsUtils.PROPERTY_APP_HIBERNATION_ENABLED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.content.pm.PackageManager;
import android.permission.PermissionControllerManager;
import android.provider.DeviceConfig;

import androidx.test.core.app.ApplicationProvider;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

@RunWith(AndroidJUnit4.class)
public class HibernatedAppsItemManagerTest {
    private static final int CALLBACK_TIMEOUT_MS = 100;

    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private final TestListener mHibernatedAppsCountListener = new TestListener();
    @Mock
    private PackageManager mPackageManager;
    @Mock
    private PermissionControllerManager mPermissionControllerManager;
    @Captor
    private ArgumentCaptor<IntConsumer> mIntConsumerCaptor;
    private Context mContext;
    private HibernatedAppsItemManager mManager;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        DeviceConfig.setProperty(NAMESPACE_APP_HIBERNATION, PROPERTY_APP_HIBERNATION_ENABLED,
                "true", false);
        mContext = spy(ApplicationProvider.getApplicationContext());
        when(mContext.getPackageManager()).thenReturn(mPackageManager);
        when(mContext.getSystemService(PermissionControllerManager.class))
                .thenReturn(mPermissionControllerManager);
        mManager = new HibernatedAppsItemManager(mContext);
        mManager.setListener(mHibernatedAppsCountListener);
    }


    @Test
    public void getSummary_getsRightCountForHibernatedPackage() throws Exception {
        mManager.startLoading();
        mCountDownLatch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        verify(mPermissionControllerManager).getUnusedAppCount(any(), mIntConsumerCaptor.capture());
        mIntConsumerCaptor.getValue().accept(1);
        assertThat(mHibernatedAppsCountListener.mResult).isEqualTo(1);
    }

    private class TestListener implements HibernatedAppsItemManager.HibernatedAppsCountListener {
        int mResult;

        @Override
        public void onHibernatedAppsCountLoaded(int hibernatedAppsCount) {
            mResult = hibernatedAppsCount;
            mCountDownLatch.countDown();
        }
    };
}
