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

package android.virtualdevice.cts.applaunch;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.verifyZeroInteractions;

import android.companion.virtual.VirtualDeviceManager.IntentInterceptorCallback;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;


@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityInterceptionTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    private static final String ACTION_INTERCEPTED_RECEIVER =
            "android.virtualdevice.applaunch.INTERCEPTED_RECEIVER";

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();
    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;
    private Intent mInterceptedIntent;

    @Mock
    private IntentInterceptorCallback mInterceptor;

    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        mInterceptedIntent = new Intent(ACTION_INTERCEPTED_RECEIVER)
                .setClass(mContext, InterceptedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @Test
    public void noInterceptorRegistered_activityShouldLaunch() {
        mRule.sendIntentToDisplay(mInterceptedIntent, mVirtualDisplay);
        mRule.waitAndAssertActivityResumed(new ComponentName(mContext, InterceptedActivity.class));
        verifyZeroInteractions(mInterceptor);
    }

    @Test
    public void interceptorRegistered_intentIsIntercepted() {
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(
                intentFilter, mContext.getMainExecutor(), mInterceptor);

        mRule.sendIntentToDisplay(mInterceptedIntent, mVirtualDisplay);

        assertIntentIntercepted();

        // Unregister interceptor and verify intent is not intercepted
        mVirtualDevice.unregisterIntentInterceptor(mInterceptor);
        mRule.sendIntentToDisplay(mInterceptedIntent, mVirtualDisplay);
        mRule.waitAndAssertActivityResumed(new ComponentName(mContext, InterceptedActivity.class));
        verifyNoMoreInteractions(mInterceptor);
    }

    @Test
    public void noMatchingInterceptor_activityShouldLaunch() {
        IntentFilter intentFilter = new IntentFilter("ACTION_OTHER");
        mVirtualDevice.registerIntentInterceptor(
                intentFilter, mContext.getMainExecutor(), mInterceptor);

        mRule.sendIntentToDisplay(mInterceptedIntent, mVirtualDisplay);
        mRule.waitAndAssertActivityResumed(new ComponentName(mContext, InterceptedActivity.class));
        verifyZeroInteractions(mInterceptor);
    }


    @Test
    public void differentInterceptorsRegistered_oneIntercepted() {
        // setup expected intent interceptor
        IntentFilter intentFilter = new IntentFilter(ACTION_INTERCEPTED_RECEIVER);
        mVirtualDevice.registerIntentInterceptor(
                intentFilter, mContext.getMainExecutor(), mInterceptor);

        // setup other intent interceptor
        IntentInterceptorCallback interceptorOther = mock(IntentInterceptorCallback.class);
        IntentFilter intentFilterOther = new IntentFilter("ACTION_OTHER");
        mVirtualDevice.registerIntentInterceptor(
                intentFilterOther, mContext.getMainExecutor(), interceptorOther);

        mRule.sendIntentToDisplay(mInterceptedIntent, mVirtualDisplay);

        assertIntentIntercepted();
        verifyZeroInteractions(interceptorOther);
    }

    @Test
    public void registerIntentInterceptor_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(
                        null, mContext.getMainExecutor(), mInterceptor));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(
                        new IntentFilter(), null, mInterceptor));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.registerIntentInterceptor(
                        new IntentFilter(), mContext.getMainExecutor(), null));
    }

    @Test
    public void unregisterIntentInterceptor_nullArguments_shouldThrow() {
        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.unregisterIntentInterceptor(null));
    }

    private void assertIntentIntercepted() {
        ArgumentCaptor<Intent> captor = ArgumentCaptor.forClass(Intent.class);
        verify(mInterceptor, timeout(TIMEOUT_MILLIS).times(1))
                .onIntentIntercepted(captor.capture());
        Intent capturedIntent = captor.getValue();

        assertThat(capturedIntent).isNotNull();
        assertThat(capturedIntent.getAction()).isEqualTo(mInterceptedIntent.getAction());
        assertThat(capturedIntent.getData()).isEqualTo(mInterceptedIntent.getData());
        assertThat(capturedIntent.getExtras()).isNull();
    }

    /** An empty activity that can be intercepted. */
    public static class InterceptedActivity extends EmptyActivity {}
}
