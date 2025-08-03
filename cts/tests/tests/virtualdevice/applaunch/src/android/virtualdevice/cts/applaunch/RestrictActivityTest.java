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

import static android.virtualdevice.cts.common.VirtualDeviceRule.createDefaultVirtualDisplayConfigBuilder;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import android.annotation.Nullable;
import android.app.Activity;
import android.app.ActivityManager;
import android.companion.virtual.VirtualDeviceManager;
import android.companion.virtual.VirtualDeviceManager.VirtualDevice;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.hardware.display.VirtualDisplayConfig;
import android.platform.test.annotations.AppModeFull;
import android.virtualdevice.cts.applaunch.AppComponents.EmptyActivity;
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
public class RestrictActivityTest {

    private static final String ACTIVITY_CATEGORY = "testActivityCategory";

    private static final String NON_MATCHING_ACTIVITY_CATEGORY = "someOtherCategory";

    private static final long TIMEOUT_MILLIS = TimeUnit.SECONDS.toMillis(3);

    @Rule
    public VirtualDeviceRule mRule = VirtualDeviceRule.createDefault();

    private final Context mContext = getInstrumentation().getContext();
    private final ActivityManager mActivityManager =
            mContext.getSystemService(ActivityManager.class);

    @Mock
    private VirtualDeviceManager.ActivityListener mActivityListener;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
    }

    @Test
    public void nonRestrictedActivityOnNonRestrictedDevice_shouldSucceed() {
        assertActivityLaunchSucceeds(EmptyActivity.class, /* displayCategories= */ null);
    }

    @Test
    public void restrictedActivityOnNonRestrictedDevice_shouldFail() {
        assertActivityLaunchBlocked(RestrictedActivity.class, /* displayCategories= */ null);
    }

    @Test
    public void nonRestrictedActivityOnRestrictedDevice_shouldFail() {
        assertActivityLaunchBlocked(
                EmptyActivity.class, Set.of(ACTIVITY_CATEGORY, NON_MATCHING_ACTIVITY_CATEGORY));
    }

    @Test
    public void restrictedActivity_differentCategory_shouldFail() {
        assertActivityLaunchBlocked(
                RestrictedActivity.class, Set.of(NON_MATCHING_ACTIVITY_CATEGORY));
    }

    @Test
    public void restrictedActivity_containCategories_shouldSucceed() {
        assertActivityLaunchSucceeds(RestrictedActivity.class, Set.of(ACTIVITY_CATEGORY));
    }

    @Test
    public void restrictedActivity_noGwpc_shouldFail() {
        final VirtualDisplay virtualDisplay = mRule.createManagedUnownedVirtualDisplayWithFlags(
                DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        final int displayId = virtualDisplay.getDisplay().getDisplayId();

        final Intent intent = new Intent(mContext, RestrictedActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);

        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(mContext, displayId, intent))
                .isFalse();
    }

    private <T extends Activity> void assertActivityLaunchSucceeds(
            Class<T> clazz, @Nullable Set<String> displayCategories) {
        final int displayId = createVirtualDeviceAndDisplayWithCategories(displayCategories);
        final Intent intent = new Intent(mContext, clazz)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        Activity activity = mRule.startActivityOnDisplaySync(displayId, clazz);
        assertActivityOnDisplay(activity.getComponentName(), displayId);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(mContext, displayId, intent))
                .isTrue();
    }

    private <T extends Activity> void assertActivityLaunchBlocked(
            Class<T> clazz, @Nullable Set<String> displayCategories) {
        final int displayId = createVirtualDeviceAndDisplayWithCategories(displayCategories);
        final Intent intent = new Intent(mContext, clazz)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        mRule.sendIntentToDisplay(intent, displayId);
        assertActivityOnDisplay(VirtualDeviceRule.BLOCKED_ACTIVITY_COMPONENT, displayId);
        assertThat(mActivityManager.isActivityStartAllowedOnDisplay(mContext, displayId, intent))
                .isFalse();
    }

    private int createVirtualDeviceAndDisplayWithCategories(
            @Nullable Set<String> displayCategories) {
        VirtualDevice virtualDevice = mRule.createManagedVirtualDevice();
        virtualDevice.addActivityListener(mContext.getMainExecutor(), mActivityListener);
        VirtualDisplayConfig.Builder builder = createDefaultVirtualDisplayConfigBuilder()
                .setFlags(DisplayManager.VIRTUAL_DISPLAY_FLAG_TRUSTED
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY
                        | DisplayManager.VIRTUAL_DISPLAY_FLAG_PUBLIC);
        if (displayCategories != null) {
            builder = builder.setDisplayCategories(displayCategories);
        }
        VirtualDisplay virtualDisplay = mRule.createManagedVirtualDisplay(
                virtualDevice, builder.build());
        return virtualDisplay.getDisplay().getDisplayId();
    }

    protected void assertActivityOnDisplay(ComponentName componentName, int displayId) {
        verify(mActivityListener, timeout(TIMEOUT_MILLIS)).onTopActivityChanged(
                eq(displayId), eq(componentName), eq(mContext.getUserId()));
    }

    /** An empty activity with a display category. */
    public static class RestrictedActivity extends EmptyActivity {}
}
