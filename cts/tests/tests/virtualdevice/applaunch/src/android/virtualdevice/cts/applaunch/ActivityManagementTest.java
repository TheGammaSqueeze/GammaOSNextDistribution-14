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

package android.virtualdevice.cts.applaunch;

import static android.content.Intent.FLAG_ACTIVITY_CLEAR_TASK;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;

import android.app.PendingIntent;
import android.app.Service;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.applaunch.AppComponents.SecondActivity;
import android.virtualdevice.cts.applaunch.AppComponents.TestService;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.concurrent.TimeUnit;
import java.util.function.IntConsumer;

/**
 * Tests for activity management, like launching and listening to activity change events, in the
 * virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityManagementTest {

    // VirtualDeviceImpl#PENDING_TRAMPOLINE_TIMEOUT_MS is 5000ms wait a bit longer than that.
    private static final long PENDING_INTENT_TIMEOUT_MILLIS = 5100;
    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();

    private final ComponentName mEmptyActivityComponent =
            new ComponentName(mContext, EmptyActivity.class);
    private final ComponentName mSecondActivityComponent =
            new ComponentName(mContext, SecondActivity.class);

    private VirtualDevice mVirtualDevice;
    private int mVirtualDisplayId;

    @Mock
    private IntConsumer mLaunchCompleteListener;
    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;


    @Before
    public void setUp() {
        MockitoAnnotations.initMocks(this);
        mVirtualDevice = mRule.createManagedVirtualDevice();
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplayWithFlags(mVirtualDevice,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY);
        mVirtualDisplayId = virtualDisplay.getDisplay().getDisplayId();
    }

    @Test
    public void removeActivityListener_shouldStopCallbacks() {
        EmptyActivity activity =
                mRule.startActivityOnDisplaySync(mVirtualDisplayId, EmptyActivity.class);

        verify(mActivityListener, timeout(TIMEOUT_MILLIS).times(1)).onTopActivityChanged(
                eq(mVirtualDisplayId), eq(mEmptyActivityComponent));
        assertActivityOnVirtualDisplay(mEmptyActivityComponent);

        mVirtualDevice.removeActivityListener(mActivityListener);
        activity.finish();
        mRule.waitAndAssertActivityRemoved(mEmptyActivityComponent);

        verifyNoMoreInteractions(mActivityListener);
    }

    @Test
    public void activityListener_shouldCallOnTopActivityChange() {
        EmptyActivity activity =
                mRule.startActivityOnDisplaySync(mVirtualDisplayId, EmptyActivity.class);

        verify(mActivityListener, timeout(TIMEOUT_MILLIS).times(1)).onTopActivityChanged(
                eq(mVirtualDisplayId), eq(mEmptyActivityComponent));
        assertActivityOnVirtualDisplay(mEmptyActivityComponent);

        activity.finish();
        mRule.waitAndAssertActivityRemoved(mEmptyActivityComponent);

        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onDisplayEmpty(mVirtualDisplayId);
    }

    @Test
    @ApiTest(apis = {"android.companion.virtual.VirtualDeviceManager#launchPendingIntent"})
    public void launchPendingIntent_multipleActivities_shouldLaunchOnSameDisplay() {
        mRule.assumeActivityLaunchSupported(mVirtualDisplayId);
        PendingIntent pendingIntent = PendingIntent.getActivities(
                mContext,
                /* requestCode= */ 1,
                new Intent[] {
                        new Intent().setComponent(mEmptyActivityComponent)
                                .addFlags(FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TASK),
                        new Intent().setComponent(mSecondActivityComponent)
                },
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);

        mVirtualDevice.launchPendingIntent(
                mVirtualDisplayId, pendingIntent, Runnable::run, mLaunchCompleteListener);

        assertActivityOnVirtualDisplay(mEmptyActivityComponent);
        assertActivityOnVirtualDisplay(mSecondActivityComponent);
        verify(mLaunchCompleteListener, timeout(PENDING_INTENT_TIMEOUT_MILLIS)).accept(
                eq(VirtualDeviceManager.LAUNCH_SUCCESS));
    }

    @Test
    public void launchPendingIntent_activityIntent_shouldLaunchActivity() throws Exception {
        mRule.assumeActivityLaunchSupported(mVirtualDisplayId);
        Service service = TestService.startService(mContext);
        Intent intent = new Intent(Intent.ACTION_MAIN).setClass(service, EmptyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                service, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        mVirtualDevice.launchPendingIntent(
                mVirtualDisplayId, pendingIntent, Runnable::run, mLaunchCompleteListener);

        verify(mLaunchCompleteListener, timeout(PENDING_INTENT_TIMEOUT_MILLIS)).accept(
                eq(VirtualDeviceManager.LAUNCH_SUCCESS));
        assertActivityOnVirtualDisplay(mEmptyActivityComponent);
    }

    @Test
    public void launchPendingIntent_serviceIntentTrampolineActivity_shouldLaunchActivity()
            throws Exception {
        mRule.assumeActivityLaunchSupported(mVirtualDisplayId);
        TestService service = TestService.startService(mContext);
        Intent intent = new Intent(TestService.ACTION_START_TRAMPOLINE_ACTIVITY)
                .setClass(service, EmptyActivity.class);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                service, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        mVirtualDevice.launchPendingIntent(
                mVirtualDisplayId, pendingIntent, Runnable::run, mLaunchCompleteListener);

        verify(mLaunchCompleteListener, timeout(PENDING_INTENT_TIMEOUT_MILLIS)).accept(
                eq(VirtualDeviceManager.LAUNCH_SUCCESS));
        assertActivityOnVirtualDisplay(mEmptyActivityComponent);
    }

    @Test
    public void launchPendingIntent_serviceIntentNoTrampoline_shouldBeNoOp() throws Exception {
        mRule.assumeActivityLaunchSupported(mVirtualDisplayId);
        Service service = TestService.startService(mContext);
        Intent intent = new Intent(Intent.ACTION_MAIN).setClass(service, TestService.class);
        PendingIntent pendingIntent = PendingIntent.getService(
                service, 1, intent, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        mVirtualDevice.launchPendingIntent(
                mVirtualDisplayId, pendingIntent, Runnable::run, mLaunchCompleteListener);

        verify(mLaunchCompleteListener, timeout(PENDING_INTENT_TIMEOUT_MILLIS)).accept(
                eq(VirtualDeviceManager.LAUNCH_FAILURE_NO_ACTIVITY));
    }

    @Test
    public void launchPendingIntent_nullArguments_shouldThrow() {
        PendingIntent pendingIntent = PendingIntent.getActivity(
                mContext, 1, new Intent(mContext, EmptyActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_ONE_SHOT);

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.launchPendingIntent(mVirtualDisplayId,
                        /* pendingIntent= */ null, Runnable::run, mLaunchCompleteListener));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.launchPendingIntent(mVirtualDisplayId, pendingIntent,
                        /* executor= */ null, mLaunchCompleteListener));

        assertThrows(NullPointerException.class,
                () -> mVirtualDevice.launchPendingIntent(mVirtualDisplayId, pendingIntent,
                        Runnable::run, /* listener= */ null));
    }

    private void assertActivityOnVirtualDisplay(ComponentName componentName) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(mVirtualDisplayId), eq(componentName), eq(mContext.getUserId()));
    }
}

