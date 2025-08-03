/*
 * Copyright 2022 The Android Open Source Project
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

package com.android.bluetooth.pbap;

import static android.content.DialogInterface.BUTTON_POSITIVE;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DEFAULT;
import static android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
import static android.content.pm.PackageManager.DONT_KILL_APP;

import static androidx.lifecycle.Lifecycle.State;
import static androidx.lifecycle.Lifecycle.State.DESTROYED;

import static com.android.bluetooth.pbap.BluetoothPbapActivity.DISMISS_TIMEOUT_DIALOG;
import static com.android.bluetooth.pbap.BluetoothPbapActivity.DISMISS_TIMEOUT_DIALOG_DELAY_MS;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.text.Editable;
import android.text.SpannableStringBuilder;

import androidx.test.core.app.ActivityScenario;
import androidx.test.filters.LargeTest;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.runner.AndroidJUnit4;

import com.android.bluetooth.BluetoothMethodProxy;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class BluetoothPbapActivityTest {

    Context mTargetContext = InstrumentationRegistry.getInstrumentation().getTargetContext();
    Intent mIntent;

    ActivityScenario<BluetoothPbapActivity> mActivityScenario;

    BluetoothMethodProxy mMethodProxy;

    @Before
    public void setUp() {
        mMethodProxy = spy(BluetoothMethodProxy.getInstance());
        BluetoothMethodProxy.setInstanceForTesting(mMethodProxy);

        mIntent = new Intent();
        mIntent.setClass(mTargetContext, BluetoothPbapActivity.class);
        mIntent.setAction(BluetoothPbapService.AUTH_CHALL_ACTION);

        enableActivity(true);
        mActivityScenario = ActivityScenario.launch(mIntent);
    }

    @After
    public void tearDown() throws Exception {
        if (mActivityScenario != null) {
            // Workaround for b/159805732. Without this, test hangs for 45 seconds.
            Thread.sleep(1_000);
            mActivityScenario.close();
        }
        enableActivity(false);
        BluetoothMethodProxy.setInstanceForTesting(null);
    }

    @Test
    public void activityIsDestroyed_whenLaunchedWithoutIntentAction() throws Exception {
        mActivityScenario.close();

        mIntent.setAction(null);
        mActivityScenario = ActivityScenario.launch(mIntent);

        assertActivityState(DESTROYED);
    }

    @Test
    public void onPreferenceChange_returnsTrue() throws Exception {
        AtomicBoolean result = new AtomicBoolean(false);

        mActivityScenario.onActivity(activity -> result.set(
                activity.onPreferenceChange(/*preference=*/null, /*newValue=*/null)));

        assertThat(result.get()).isTrue();
    }

    @Test
    public void onPositive_finishesActivity() throws Exception {
        AtomicBoolean finishCalled = new AtomicBoolean(false);

        mActivityScenario.onActivity(
                activity -> {
                    activity.onPositive();
                    finishCalled.set(activity.isFinishing());
                });

        assertThat(finishCalled.get()).isTrue();
    }

    @Test
    public void onNegative_finishesActivity() throws Exception {
        AtomicBoolean finishCalled = new AtomicBoolean(false);

        mActivityScenario.onActivity(
                activity -> {
                    activity.onNegative();
                    finishCalled.set(activity.isFinishing());
                });

        assertThat(finishCalled.get()).isTrue();
    }

    @Test
    public void onReceiveTimeoutIntent_sendsDismissDialogMessage() throws Exception {
        Intent intent = new Intent(BluetoothPbapService.USER_CONFIRM_TIMEOUT_ACTION);

        mActivityScenario.onActivity(activity -> {
            activity.mReceiver.onReceive(activity, intent);
        });

        verify(mMethodProxy)
                .handlerSendMessageDelayed(
                        any(), eq(DISMISS_TIMEOUT_DIALOG), eq(DISMISS_TIMEOUT_DIALOG_DELAY_MS));
    }

    @Test
    public void afterTextChanged() throws Exception {
        Editable editable = new SpannableStringBuilder("An editable text");
        AtomicBoolean result = new AtomicBoolean(false);

        mActivityScenario.onActivity(activity -> {
            activity.afterTextChanged(editable);
            result.set(activity.getButton(BUTTON_POSITIVE).isEnabled());
        });

        assertThat(result.get()).isTrue();
    }

    // TODO: Test onSaveInstanceState and onRestoreInstanceState.
    // Note: Activity.recreate() fails. The Activity just finishes itself when recreated.
    //       Fix the bug and test those methods.

    @Test
    public void emptyMethods_doesNotThrowException() throws Exception {
        try {
            mActivityScenario.onActivity(activity -> {
                activity.beforeTextChanged(null, 0, 0, 0);
                activity.onTextChanged(null, 0, 0, 0);
            });
        } catch (Exception ex) {
            assertWithMessage("Exception should not happen!").fail();
        }
    }

    private void assertActivityState(State state) throws Exception {
        // TODO: Change this into an event driven systems
        Thread.sleep(3_000);
        assertThat(mActivityScenario.getState()).isEqualTo(state);
    }

    private void enableActivity(boolean enable) {
        int enabledState = enable ? COMPONENT_ENABLED_STATE_ENABLED
                : COMPONENT_ENABLED_STATE_DEFAULT;

        mTargetContext.getPackageManager().setApplicationEnabledSetting(
                mTargetContext.getPackageName(), enabledState, DONT_KILL_APP);

        ComponentName activityName = new ComponentName(mTargetContext, BluetoothPbapActivity.class);
        mTargetContext.getPackageManager().setComponentEnabledSetting(
                activityName, enabledState, DONT_KILL_APP);
    }
}
