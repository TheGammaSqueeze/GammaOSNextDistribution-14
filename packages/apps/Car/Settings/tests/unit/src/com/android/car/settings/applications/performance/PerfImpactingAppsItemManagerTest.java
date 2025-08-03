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

package com.android.car.settings.applications.performance;

import static android.car.settings.CarSettings.Secure.KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE;

import static com.android.dx.mockito.inline.extended.ExtendedMockito.mockitoSession;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

import android.content.Context;
import android.provider.Settings;

import androidx.test.core.app.ApplicationProvider;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.MockitoSession;
import org.mockito.quality.Strictness;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class PerfImpactingAppsItemManagerTest {
    private static final int CALLBACK_TIMEOUT_MS = 100;
    private static final String TEST_PKG_NAME = "test.package.name";
    private static final String TEST_PRIVILEGE_PKG_NAME = "test.privilege.package.name";
    private static final String TEST_DISABLED_PACKAGES_SETTING_STRING = TEST_PKG_NAME + ";"
            + TEST_PRIVILEGE_PKG_NAME;

    private final CountDownLatch mCountDownLatch = new CountDownLatch(1);

    private final TestListener mPerfImpactingAppsListener = new TestListener();

    private MockitoSession mMockingSession;

    private Context mContext;
    private PerfImpactingAppsItemManager mManager;

    @Before
    public void setUp() {
        mMockingSession = mockitoSession()
                .initMocks(this)
                .mockStatic(Settings.Secure.class)
                .strictness(Strictness.LENIENT)
                .startMocking();

        mContext = spy(ApplicationProvider.getApplicationContext());

        when(Settings.Secure.getString(any(), eq(KEY_PACKAGES_DISABLED_ON_RESOURCE_OVERUSE)))
                .thenReturn(TEST_DISABLED_PACKAGES_SETTING_STRING);

        mManager = new PerfImpactingAppsItemManager(mContext);
        mManager.addListener(mPerfImpactingAppsListener);
    }

    @After
    public void tearDown() {
        mMockingSession.finishMocking();
    }

    @Test
    public void startLoading_getDisabledPackagesCount() throws Exception {
        mManager.startLoading();
        mCountDownLatch.await(CALLBACK_TIMEOUT_MS, TimeUnit.MILLISECONDS);

        assertThat(mPerfImpactingAppsListener.mResult).isEqualTo(2);
    }

    private class TestListener implements PerfImpactingAppsItemManager.PerfImpactingAppsListener {
        int mResult;

        @Override
        public void onPerfImpactingAppsLoaded(int disabledPackagesCount) {
            mResult = disabledPackagesCount;
            mCountDownLatch.countDown();
        }
    }
}
