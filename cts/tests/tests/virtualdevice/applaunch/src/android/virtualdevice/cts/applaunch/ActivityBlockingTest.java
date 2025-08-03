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

import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_CUSTOM;
import static android.companion.virtual.VirtualDeviceParams.DEVICE_POLICY_DEFAULT;
import static android.companion.virtual.VirtualDeviceParams.POLICY_TYPE_ACTIVITY;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager.ActivityListener;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.companion.virtual.VirtualDeviceParams;
import android.companion.virtual.flags.Flags;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
import android.virtualdevice.cts.common.StreamedAppConstants;
import android.virtualdevice.cts.common.VirtualDeviceRule;

import androidx.test.ext.junit.runners.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Tests for blocking of activities that should not be shown on the virtual device.
 */
@RunWith(AndroidJUnit4.class)
@AppModeFull(reason = "VirtualDeviceManager cannot be accessed by instant apps")
public class ActivityBlockingTest {

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getTargetContext();
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);

    private final Intent mEmptyActivityIntent = new Intent(mContext, EmptyActivity.class)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    // Monitor an activity in a different APK to test cross-task navigations.
    private final Intent mMonitoredIntent = new Intent(Intent.ACTION_MAIN)
            .setComponent(StreamedAppConstants.CUSTOM_HOME_ACTIVITY)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

    private VirtualDevice mVirtualDevice;
    private VirtualDisplay mVirtualDisplay;

    @Mock
    private ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void nonTrustedDisplay_startNonEmbeddableActivity_shouldThrowSecurityException() {
        createVirtualDeviceAndNonTrustedDisplay();
        mRule.assumeActivityLaunchSupported(mVirtualDisplay.getDisplay().getDisplayId());
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), mMonitoredIntent))
                .isFalse();
        assertThrows(SecurityException.class,
                () -> mRule.sendIntentToDisplay(mMonitoredIntent, mVirtualDisplay));
    }

    @Test
    public void cannotDisplayOnRemoteActivity_shouldBeBlockedFromLaunching() {
        createVirtualDeviceAndTrustedDisplay();
        Intent blockedIntent = new Intent(mContext, CannotDisplayOnRemoteActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        assertActivityLaunchBlocked(blockedIntent);
    }

    @Test
    public void trustedDisplay_startNonEmbeddableActivity_shouldSucceed() {
        createVirtualDeviceAndTrustedDisplay();
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setAllowedActivities_shouldBlockNonAllowedActivities() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void setAllowedActivities_shouldAllowActivitiesInAllowlist() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @Test
    public void setBlockedActivities_shouldBlockActivityFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @Test
    public void setBlockedActivities_shouldAllowOtherActivitiesToLaunch() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setBlockedActivities_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void setAllowedActivities_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedActivities(Set.of(mMonitoredIntent.getComponent()))
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_addExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_addExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_removeExemption_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_removeExemption_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.removeActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void customActivityPolicy_changeToDefault_shouldAllowLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM)
                .build());
        assertActivityLaunchBlocked(mMonitoredIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_changeToCustom_shouldBlockFromLaunching() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        assertActivityLaunchAllowed(mMonitoredIntent);

        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(Flags.FLAG_DYNAMIC_POLICY)
    @Test
    public void defaultActivityPolicy_changePolicy_clearsExemptions() {
        // Initially, allow launches by default except for the monitored component.
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT)
                .build());
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to block by default still blocks it as it is not exempt anymore.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_CUSTOM);
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Making it exempt allows for launching it.
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Changing the policy to allow by default allows it as the exemptions were cleared.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchAllowed(mMonitoredIntent);

        // Adding an exemption blocks it from launching.
        mVirtualDevice.addActivityPolicyExemption(mMonitoredIntent.getComponent());
        assertActivityLaunchBlocked(mMonitoredIntent);

        // Changing the policy to its current value does not affect the exemptions.
        mVirtualDevice.setDevicePolicy(POLICY_TYPE_ACTIVITY, DEVICE_POLICY_DEFAULT);
        assertActivityLaunchBlocked(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(
            {Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void autoMirrorDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(
            {Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void publicDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @RequiresFlagsEnabled(
            {Flags.FLAG_INTERACTIVE_SCREEN_MIRROR, Flags.FLAG_CONSISTENT_DISPLAY_FLAGS})
    @Test
    public void publicAutoMirrorDisplay_shouldNotLaunchActivity() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build(),
                DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR);
        assertNoActivityLaunched(mMonitoredIntent);
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertBlockedAppStreamingActivityLaunched();
    }

    @Test
    public void setAllowedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setAllowedCrossTaskNavigations(Set.of(mMonitoredIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertActivityLaunched(mMonitoredIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldAllowNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(mEmptyActivityIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertActivityLaunched(mMonitoredIntent.getComponent());
    }

    @Test
    public void setBlockedCrossTaskNavigations_shouldBlockNonAllowedNavigations() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder()
                .setBlockedCrossTaskNavigations(Set.of(mMonitoredIntent.getComponent()))
                .build());
        EmptyActivity emptyActivity =
                mRule.startActivityOnDisplaySync(mVirtualDisplay, EmptyActivity.class);
        emptyActivity.startActivity(mMonitoredIntent);
        assertBlockedAppStreamingActivityLaunched();
    }

    private void createVirtualDeviceAndNonTrustedDisplay() {
        createVirtualDeviceAndDisplay(
                new VirtualDeviceParams.Builder().build(), /* virtualDisplayFlags= */0);
    }

    private void createVirtualDeviceAndTrustedDisplay() {
        createVirtualDeviceAndTrustedDisplay(new VirtualDeviceParams.Builder().build());
    }

    private void createVirtualDeviceAndTrustedDisplay(VirtualDeviceParams virtualDeviceParams) {
        createVirtualDeviceAndDisplay(
                virtualDeviceParams, DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    private void createVirtualDeviceAndTrustedDisplay(
            VirtualDeviceParams virtualDeviceParams, int virtualDisplayFlags) {
        createVirtualDeviceAndDisplay(
                virtualDeviceParams,
                virtualDisplayFlags | DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED);
    }

    private void createVirtualDeviceAndDisplay(
            VirtualDeviceParams virtualDeviceParams, int virtualDisplayFlags) {
        mVirtualDevice = mRule.createManagedVirtualDevice(virtualDeviceParams);
        mVirtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        mVirtualDisplay = mRule.createManagedVirtualDisplay(mVirtualDevice,
                VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder()
                        .setFlags(virtualDisplayFlags)
                        .build());
    }

    /**
     * Assert that starting an activity with the given intent actually starts
     * BlockedAppStreamingActivity.
     */
    private void assertActivityLaunchBlocked(Intent intent) {
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        mRule.sendIntentToDisplay(intent, mVirtualDisplay);
        assertBlockedAppStreamingActivityLaunched();
    }

    /**
     * Assert that no activity is launched with the given intent.
     */
    private void assertNoActivityLaunched(Intent intent) {
        mRule.sendIntentToDisplay(intent, mVirtualDisplay);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isFalse();
        verify(mActivityListener, never()).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()), any(), anyInt());
        reset(mActivityListener);
    }

    /**
     * Assert that launching an activity is successful with the given intent.
     */
    private void assertActivityLaunchAllowed(Intent intent) {
        mRule.sendIntentToDisplay(intent, mVirtualDisplay);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(
                mContext, mVirtualDisplay.getDisplay().getDisplayId(), intent)).isTrue();
        assertActivityLaunched(intent.getComponent());
    }

    private void assertBlockedAppStreamingActivityLaunched() {
        assertActivityLaunched(VirtualDeviceRule.BLOCKED_ACTIVITY_COMPONENT);
    }

    private void assertActivityLaunched(ComponentName componentName) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(mVirtualDisplay.getDisplay().getDisplayId()), eq(componentName), anyInt());
        reset(mActivityListener);
    }

    /** An empty activity that cannot be displayed on remote devices. */
    public static class CannotDisplayOnRemoteActivity extends EmptyActivity {}
}

