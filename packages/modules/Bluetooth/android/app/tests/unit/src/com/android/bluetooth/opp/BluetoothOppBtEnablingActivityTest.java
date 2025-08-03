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

package com.android.bluetooth.opp;

import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static androidx.lifecycle.Lifecycle.State.DESTROYED;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;

import android.bluetooth.BluetoothAdapter;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.view.KeyEvent;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;
import com.android.bluetooth.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mockito;
import org.mockito.MockitoAnnotations;
import org.mockito.Spy;

import java.util.concurrent.atomic.AtomicBoolean;

@RunWith(AndroidJUnit4.class)
public class BluetoothOppBtEnablingActivityTest {
    @Spy
    BluetoothMethodProxy mBluetoothMethodProxy;

    Intent mIntent;
    Context mTargetContext;

    int mRealTimeoutValue;

    // Activity tests can sometimes flaky because of external factors like system dialog, etc.
    // making the expected Espresso's root not focused or the activity doesn't show up.
    // Add retry rule to resolve this problem.
    @Rule public TestUtils.RetryTestRule mRetryTestRule = new TestUtils.RetryTestRule();

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        mBluetoothMethodProxy = Mockito.spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mBluetoothMethodProxy);

        mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();

        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothOppBtEnablingActivity.class);

        mRealTimeoutValue = BluetoothOppBtEnablingActivity.sBtEnablingTimeoutMs;
        TestUtils.setUpUiTest();
    }

    @After
    public void tearDown() throws Exception {
        TestUtils.tearDownUiTest();
        BluetoothMethodProxy.setInstanceForTesting(null);
        BluetoothOppBtEnablingActivity.sBtEnablingTimeoutMs = mRealTimeoutValue;
    }

    @Ignore("b/277594572")
    @Test
    public void onCreate_bluetoothEnableTimeout_finishAfterTimeout() throws Exception {
        int spedUpTimeoutValue = 1000;
        // To speed up the test
        BluetoothOppBtEnablingActivity.sBtEnablingTimeoutMs = spedUpTimeoutValue;
        doReturn(false).when(mBluetoothMethodProxy).bluetoothAdapterIsEnabled(any());

        ActivityScenario<BluetoothOppBtEnablingActivity> activityScenario = ActivityScenario.launch(
                mIntent);
        final BluetoothOppManager[] mOppManager = new BluetoothOppManager[1];
        activityScenario.onActivity(activity -> {
            // Should be cancelled after timeout
            mOppManager[0] = BluetoothOppManager.getInstance(activity);
        });
        Thread.sleep(spedUpTimeoutValue);
        assertThat(mOppManager[0].mSendingFlag).isEqualTo(false);
        assertActivityState(activityScenario, DESTROYED);
    }

    @Test
    public void onKeyDown_cancelProgress() throws Exception {
        doReturn(false).when(mBluetoothMethodProxy).bluetoothAdapterIsEnabled(any());
        ActivityScenario<BluetoothOppBtEnablingActivity> activityScenario = ActivityScenario.launch(
                mIntent);

        AtomicBoolean finishCalled = new AtomicBoolean(false);

        activityScenario.onActivity(
                activity -> {
                    activity.onKeyDown(
                            KeyEvent.KEYCODE_BACK,
                            new KeyEvent(KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_BACK));
                    // Should be cancelled immediately
                    BluetoothOppManager mOppManager = BluetoothOppManager.getInstance(activity);
                    assertThat(mOppManager.mSendingFlag).isEqualTo(false);

                    finishCalled.set(activity.isFinishing());
                });
        assertThat(finishCalled.get()).isTrue();
    }

    @Test
    public void onCreate_bluetoothAlreadyEnabled_finishImmediately() throws Exception {
        doReturn(true).when(mBluetoothMethodProxy).bluetoothAdapterIsEnabled(any());
        ActivityScenario<BluetoothOppBtEnablingActivity> activityScenario = ActivityScenario.launch(
                mIntent);
        assertActivityState(activityScenario, DESTROYED);
    }

    @Test
    public void broadcastReceiver_onReceive_finishImmediately() throws Exception {
        doReturn(false).when(mBluetoothMethodProxy).bluetoothAdapterIsEnabled(any());
        ActivityScenario<BluetoothOppBtEnablingActivity> activityScenario = ActivityScenario.launch(
                mIntent);

        AtomicBoolean finishCalled = new AtomicBoolean(false);
        activityScenario.onActivity(
                activity -> {
                    Intent intent = new Intent(BluetoothAdapter.ACTION_STATE_CHANGED);
                    intent.putExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.STATE_ON);
                    activity.mBluetoothReceiver.onReceive(mTargetContext, intent);

                    finishCalled.set(activity.isFinishing());
                });
        assertThat(finishCalled.get()).isTrue();
    }

    private void assertActivityState(ActivityScenario activityScenario, Lifecycle.State state)
      throws Exception {
        // TODO: Change this into an event driven systems
        Thread.sleep(3_000);
        assertThat(activityScenario.getState()).isEqualTo(state);
    }

    private void enableActivity(boolean enable) {
        int enabledState = enable ? COMPONENT_ENABLED_STATE_ENABLED
                : COMPONENT_ENABLED_STATE_DEFAULT;

        mTargetContext.getPackageManager().setApplicationEnabledSetting(
                mTargetContext.getPackageName(), enabledState, DONT_KILL_APP);

        ComponentName activityName = new ComponentName(mTargetContext,
                BluetoothOppTransferActivity.class);
        mTargetContext.getPackageManager().setComponentEnabledSetting(
                activityName, enabledState, DONT_KILL_APP);
    }
}
