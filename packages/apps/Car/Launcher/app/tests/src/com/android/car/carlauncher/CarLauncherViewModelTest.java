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

package com.android.car.carlauncher;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.Instrumentation;
import android.car.app.RemoteCarTaskView;
import android.car.test.mocks.AbstractExtendedMockitoTestCase;
import android.content.Context;
import android.content.Intent;

import androidx.core.app.ActivityCompat;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@RunWith(AndroidJUnit4.class)
public final class CarLauncherViewModelTest extends AbstractExtendedMockitoTestCase {
    private final Instrumentation mInstrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context mContext = mInstrumentation.getContext();

    @Rule
    public final ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Mock
    private Intent mIntent;

    private RemoteCarTaskView mRemoteCarTaskView;
    private TestActivity mActivity;


    @Before
    public void setUp() {
        mActivityRule.getScenario().onActivity(activity -> mActivity = activity);
    }

    @After
    public void tearDown() throws InterruptedException {
        mActivityRule.getScenario().close();
        mActivity.finishCompletely();
        mRemoteCarTaskView = null;
    }

    @Test
    public void testOnConfigChange_sameRemoteCarTaskView() {
        // Arrange
        createCarLauncherViewModel();
        RemoteCarTaskView oldRemoteCarTaskView = mRemoteCarTaskView;

        // Act
        triggerActivityRecreation();

        // Assert
        assertThat(oldRemoteCarTaskView).isSameInstanceAs(mRemoteCarTaskView);
    }

    @Test
    public void testViewModelOnCleared_clearsRemoteCarTaskView() {
        // Arrange
        CarLauncherViewModel carLauncherViewModel = createCarLauncherViewModel();

        // Act
        runOnMain(carLauncherViewModel::onCleared);
        mInstrumentation.waitForIdleSync();

        // Assert
        assertThat(mRemoteCarTaskView).isNull();
    }

    private CarLauncherViewModel createCarLauncherViewModel() {
        CarLauncherViewModel carLauncherViewModel = new CarLauncherViewModel(mActivity, mIntent);
        runOnMain(() -> carLauncherViewModel.getRemoteCarTaskView().observeForever(
                remoteCarTaskView -> mRemoteCarTaskView = remoteCarTaskView));
        mInstrumentation.waitForIdleSync();
        return carLauncherViewModel;
    }

    private void triggerActivityRecreation() {
        // Causes activity recreation with a new instance resulting in the same flow as
        // activity being recreated due to a configuration change.
        runOnMain(() -> ActivityCompat.recreate(mActivity));
        mInstrumentation.waitForIdleSync();
    }

    private void runOnMain(Runnable runnable) {
        mContext.getMainExecutor().execute(runnable);
    }


    public static class TestActivity extends Activity {
        private static final int FINISH_TIMEOUT_MS = 1000;
        private final CountDownLatch mDestroyed = new CountDownLatch(1);

        @Override
        protected void onDestroy() {
            super.onDestroy();
            mDestroyed.countDown();
        }

        void finishCompletely() throws InterruptedException {
            finish();
            mDestroyed.await(FINISH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
        }
    }
}
