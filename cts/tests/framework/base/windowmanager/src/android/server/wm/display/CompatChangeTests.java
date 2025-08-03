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

package android.server.wm.display;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.pm.ActivityInfo.FORCE_RESIZE_APP;
import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS;
import static android.content.pm.ActivityInfo.OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE;
import static android.content.pm.ActivityInfo.OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_NOSENSOR;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_REVERSE_LANDSCAPE;
import static android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
import static android.content.res.Configuration.ORIENTATION_LANDSCAPE;
import static android.content.res.Configuration.ORIENTATION_PORTRAIT;
import static android.provider.DeviceConfig.NAMESPACE_CONSTRAIN_DISPLAY_APIS;
import static android.server.wm.allowdisplayorientationoverride.Components.ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY;
import static android.server.wm.allowignoringorientationrequestwhenloopdetectedoptin.Components.ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_IN_ACTIVITY;
import static android.server.wm.allowignoringorientationrequestwhenloopdetectedoptout.Components.ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_OUT_ACTIVITY;
import static android.server.wm.allowignoringorientationrequestwhenloopdetectedunset.Components.ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_UNSET_ACTIVITY;
import static android.server.wm.allowminaspectratiooverrideoptin.Components.ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY;
import static android.server.wm.allowminaspectratiooverrideoptout.Components.ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY;
import static android.server.wm.alloworientationoverride.Components.ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY;
import static android.server.wm.alloworientationoverride.Components.ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY;
import static android.server.wm.allowresizeableactivityoverridesoptin.Components.ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_IN_ACTIVITY;
import static android.server.wm.allowresizeableactivityoverridesoptout.Components.ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_OUT_ACTIVITY;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED;
import static android.server.wm.allowsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED;
import static android.server.wm.allowsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY;
import static android.server.wm.allowsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS;
import static android.server.wm.allowuseraspectratiofullscreenoverrideoptin.Components.ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_IN_ACTIVITY;
import static android.server.wm.allowuseraspectratiofullscreenoverrideoptout.Components.ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_OUT_ACTIVITY;
import static android.server.wm.allowuseraspectratiooverrideoptin.Components.ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY;
import static android.server.wm.allowuseraspectratiooverrideoptout.Components.ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY;
import static android.server.wm.enablefakefocusoptin.Components.ENABLE_FAKE_FOCUS_OPT_IN_LEFT_ACTIVITY;
import static android.server.wm.enablefakefocusoptin.Components.ENABLE_FAKE_FOCUS_OPT_IN_RIGHT_ACTIVITY;
import static android.server.wm.enablefakefocusoptout.Components.ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY;
import static android.server.wm.enablefakefocusoptout.Components.ENABLE_FAKE_FOCUS_OPT_OUT_RIGHT_ACTIVITY;
import static android.server.wm.ignorerequestedorientationoverrideoptin.Components.OPT_IN_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY;
import static android.server.wm.ignorerequestedorientationoverrideoptout.Components.OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY;
import static android.server.wm.optoutsandboxingviewboundsapis.Components.TEST_VIEW_SANDBOX_OPT_OUT_TIMEOUT_MS;
import static android.server.wm.propertycameracompatallowforcerotation.Components.CAMERA_COMPAT_ALLOW_FORCE_ROTATION_ACTIVITY;
import static android.server.wm.propertycameracompatallowrefresh.Components.CAMERA_COMPAT_ALLOW_REFRESH_ACTIVITY;
import static android.server.wm.propertycameracompatenablerefreshviapauseoptin.Components.CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_IN_ACTIVITY;
import static android.server.wm.propertycameracompatenablerefreshviapauseoptout.Components.CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY;
import static android.view.Surface.ROTATION_0;
import static android.view.Surface.ROTATION_90;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.WindowConfiguration;
import android.compat.testing.PlatformCompatChangeRule;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.ConditionVariable;
import android.platform.test.annotations.Presubmit;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.provider.DeviceConfig;
import android.server.wm.HelperActivities;
import android.server.wm.MultiDisplayTestBase;
import android.server.wm.WindowManagerState;
import android.server.wm.app.AbstractLifecycleLogActivity;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.compatibility.common.util.ApiTest;
import com.android.window.flags.Flags;

import libcore.junit.util.compat.CoreCompatChangeRule.DisableCompatChanges;
import libcore.junit.util.compat.CoreCompatChangeRule.EnableCompatChanges;

import org.junit.Before;
import org.junit.Ignore;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestRule;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * The test is focused on compatibility changes that have an effect on WM logic, and tests that
 * enabling these changes has the correct effect.
 *
 * This is achieved by launching a custom activity with certain properties (e.g., a resizeable
 * portrait activity) that behaves in a certain way (e.g., enter size compat mode after resizing the
 * display) and enabling a compatibility change (e.g., {@link ActivityInfo#FORCE_RESIZE_APP}) that
 * changes that behavior (e.g., not enter size compat mode).
 *
 * The behavior without enabling a compatibility change is also tested as a baseline.
 *
 * <p>Build/Install/Run:
 * atest CtsWindowManagerDeviceDisplay:CompatChangeTests
 */
@Presubmit
public final class CompatChangeTests extends MultiDisplayTestBase {
    private static final ComponentName RESIZEABLE_PORTRAIT_ACTIVITY =
            component(HelperActivities.ResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_PORTRAIT_ACTIVITY =
            component(HelperActivities.NonResizeablePortraitActivity.class);
    private static final ComponentName NON_RESIZEABLE_LANDSCAPE_ACTIVITY =
            component(HelperActivities.NonResizeableLandscapeActivity.class);
    private static final ComponentName NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY =
            component(HelperActivities.NonResizeableNonFixedOrientationActivity.class);
    private static final ComponentName NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY =
            component(HelperActivities.NonResizeableAspectRatioActivity.class);
    private static final ComponentName NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY =
            component(HelperActivities.NonResizeableLargeAspectRatioActivity.class);
    private static final ComponentName SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY =
            component(HelperActivities.SupportsSizeChangesPortraitActivity.class);
    private static final ComponentName RESIZEABLE_LEFT_ACTIVITY =
            component(HelperActivities.ResizeableLeftActivity.class);
    private static final ComponentName RESIZEABLE_RIGHT_ACTIVITY =
            component(HelperActivities.ResizeableRightActivity.class);
    private static final ComponentName RESPONSIVE_ACTIVITY =
            component(HelperActivities.ResponsiveActivity.class);
    private static final ComponentName NO_PROPERTY_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY =
            component(HelperActivities.NoPropertyChangeOrientationWhileRelaunchingActivity.class);

    // Fixed orientation min aspect ratio
    private static final float FIXED_ORIENTATION_MIN_ASPECT_RATIO = 1.03f;
    // The min aspect ratio of NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY (as defined in the manifest).
    private static final float ACTIVITY_MIN_ASPECT_RATIO = 1.6f;
    // The min aspect ratio of NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY (as defined in the
    // manifest). This needs to be higher than the aspect ratio of any device, which according to
    // CDD is at most 21:9.
    private static final float ACTIVITY_LARGE_MIN_ASPECT_RATIO = 4f;

    private static final float FLOAT_EQUALITY_DELTA = 0.01f;

    @Rule
    public TestRule compatChangeRule = new PlatformCompatChangeRule();
    @Rule
    public final CheckFlagsRule checkFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    @Override
    public void setUp() throws Exception {
        super.setUp();
        enableAndAssumeGestureNavigationMode();
        createManagedLetterboxAspectRatioSession(FIXED_ORIENTATION_MIN_ASPECT_RATIO);
        mObjectTracker.manage(setAlwaysConstrainDisplayApisFlag(null));
        mObjectTracker.manage(setNeverConstrainDisplayApisAllPackagesFlag(null));
        mObjectTracker.manage(setNeverConstrainDisplayApisFlag(null));
    }

    @Test
    public void testOverrideUndefinedOrientationToPortrait_propertyIsFalse_overrideNotApplied() {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT,
                ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY.getPackageName());
             var session = new ActivitySessionCloseable(
                     ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY)) {
            waitAssertEquals("expected unspecified orientation",
                    SCREEN_ORIENTATION_UNSPECIFIED,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_PORTRAIT})
    public void testOverrideUndefinedOrientationToPortrait() {
        try (var session = new ActivitySessionCloseable(RESPONSIVE_ACTIVITY)) {
            waitAssertEquals("expected portrait orientation", SCREEN_ORIENTATION_PORTRAIT,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    public void testOverrideUndefinedOrientationToNoSensor_propertyIsFalse_overrideNotApplied() {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR,
                ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY.getPackageName());
            var session = new ActivitySessionCloseable(
                    ALLOW_ORIENTATION_OVERRIDE_RESPONSIVE_ACTIVITY)) {
            waitAssertEquals("expected unspecified orientation",
                    SCREEN_ORIENTATION_UNSPECIFIED,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_UNDEFINED_ORIENTATION_TO_NOSENSOR})
    public void testOverrideUndefinedOrientationToNosensor() {
        try (var session = new ActivitySessionCloseable(RESPONSIVE_ACTIVITY)) {
            waitAssertEquals("expected no-sensor orientation",
                    SCREEN_ORIENTATION_NOSENSOR,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    public void
            testOverrideLandscapeOrientationToReverseLandscape_propertyIsFalse_overrideNotApply() {
        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE,
                ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY.getPackageName());
             var session = new ActivitySessionCloseable(
                     ALLOW_ORIENTATION_OVERRIDE_LANDSCAPE_ACTIVITY)) {
            waitAssertEquals("expected landscape orientation", SCREEN_ORIENTATION_LANDSCAPE,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_LANDSCAPE_ORIENTATION_TO_REVERSE_LANDSCAPE})
    public void testOverrideLandscapeOrientationToReverseLandscape() {
        try (var session = new ActivitySessionCloseable(NON_RESIZEABLE_LANDSCAPE_ACTIVITY)) {
            waitAssertEquals("expected reverse landscape orientation",
                    SCREEN_ORIENTATION_REVERSE_LANDSCAPE,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION})
    public void testOverrideUseDisplayLandscapeNaturalOrientation() {
        try (var displayMetricsSession = new DisplayMetricsWaitCloseable()) {
            // Run this test only when natural orientation is landscape
            Size displaySize = displayMetricsSession.getInitialDisplayMetrics().getSize();
            assumeTrue(displaySize.getHeight() < displaySize.getWidth());
        }

        try (var portrait = new DeviceOrientationCloseable(ORIENTATION_PORTRAIT);
             var session = new ActivitySessionCloseable(RESPONSIVE_ACTIVITY)) {
            // Verifying that orientation is overridden
            waitAssertEquals("expected rotation 0",
                    ROTATION_0, () -> mWmState.getRotation());
        }
    }

    @Test
    public void
            testOverrideUseDisplayLandscapeNaturalOrientation_propertyIsFalse_overrideNotApplied() {
        try (var displayMetricsSession = new DisplayMetricsWaitCloseable()) {
            // Run this test only when natural orientation is landscape
            Size displaySize = displayMetricsSession.getInitialDisplayMetrics().getSize();
            assumeTrue(displaySize.getHeight() < displaySize.getWidth());
        }

        final int originalRotation = mWmState.getRotation();

        try (var portrait = new DeviceOrientationCloseable(ORIENTATION_PORTRAIT);
             var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_USE_DISPLAY_LANDSCAPE_NATURAL_ORIENTATION,
                ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY.getPackageName());
             var session = new ActivitySessionCloseable(
                    ALLOW_DISPLAY_ORIENTATION_OVERRIDE_ACTIVITY)) {

            // Verifying that orientation not overridden
            if (portrait.isRotationApplied()) {
                // If the screen was rotated away from natural orientation (to portrait)
                waitAssertEquals("expected rotation 90",
                        ROTATION_90, () -> mWmState.getRotation());
            } else {
                // If the screen was already in portrait (rotated away from natural orientation)
                waitAssertEquals("expected originalRotation=" + originalRotation,
                        originalRotation, () -> mWmState.getRotation());
            }
        }
    }

    @Test
    public void testEnableFakeFocus_propertyIsFalse_overrideNotApplied() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS,
                ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY.getPackageName());
             var splitScreen = new SplitScreenActivitiesCloseable(
                     ENABLE_FAKE_FOCUS_OPT_OUT_LEFT_ACTIVITY,
                     ENABLE_FAKE_FOCUS_OPT_OUT_RIGHT_ACTIVITY)) {
            waitAssertEquals("expected should not send compat fake focus",
                    /* expected */ false,
                    () -> splitScreen.getPrimaryActivity()
                            .getActivityState().getShouldSendCompatFakeFocus());
        }
    }

    @Test
    public void testEnableFakeFocus_propertyIsTrue_returnsTrue() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        try (var splitScreen = new SplitScreenActivitiesCloseable(
                     ENABLE_FAKE_FOCUS_OPT_IN_LEFT_ACTIVITY,
                     ENABLE_FAKE_FOCUS_OPT_IN_RIGHT_ACTIVITY)) {
            waitAssertEquals("expected should send compat fake focus", /* expected */ true,
                    () -> splitScreen.getPrimaryActivity()
                            .getActivityState().getShouldSendCompatFakeFocus());
        }
    }

    @Test
    @EnableCompatChanges({OVERRIDE_ENABLE_COMPAT_FAKE_FOCUS})
    public void testEnableFakeFocus_overrideApplied_returnsTrue() {
        assumeTrue("Skipping test: no split multi-window support",
                supportsSplitScreenMultiWindow());
        assumeTrue("Skipping test: config_isCompatFakeFocusEnabled not enabled",
                getFakeFocusEnabledConfig());

        try (var splitScreen = new SplitScreenActivitiesCloseable(
                     RESIZEABLE_LEFT_ACTIVITY,
                     RESIZEABLE_RIGHT_ACTIVITY)) {
            waitAssertEquals("expected should send compat fake focus", /* expected */ true,
                    () -> splitScreen.getPrimaryActivity()
                            .getActivityState().getShouldSendCompatFakeFocus());
        }
    }

    boolean getFakeFocusEnabledConfig() {
        return mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier(
                        "config_isCompatFakeFocusEnabled",
                        "bool", "android"));
    }

    @Test
    @Ignore("b/295873734 flaky")
    public void testOverrideIgnoreRequestedOrientation_propertyIsFalse_overrideNotApplied() {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var compatChange = new CompatChangeCloseable(
                    ActivityInfo.OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION,
                    OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY.getPackageName());
             var session = new ActivitySessionCloseable(
                     OPT_OUT_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY)) {
            waitAssertEquals("expected landscape orientation",
                    SCREEN_ORIENTATION_LANDSCAPE,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    public void testOverrideIgnoreRequestedOrientation_isDisabled_propertyIsTrue_overrideApplied() {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var session = new ActivitySessionCloseable(
                     OPT_IN_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY)) {
            waitAssertEquals("expected portrait orientation",
                    SCREEN_ORIENTATION_PORTRAIT,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION})
    public void testOverrideIgnoreRequestedOrientation() {
        assumeTrue("Skipping test: "
                    + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled not enabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var session = new ActivitySessionCloseable(
                     NO_PROPERTY_CHANGE_ORIENTATION_WHILE_RELAUNCHING_ACTIVITY)) {
            waitAssertEquals("expected portrait orientation",
                    SCREEN_ORIENTATION_PORTRAIT,
                    () -> session.getActivityState().getOverrideOrientation());
        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is ignored when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is set to true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyTrue_overrideEnabled_overrideApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED,
                ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_IN_ACTIVITY
                        .getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_IN_ACTIVITY)) {
            // Activity with property set to true and override enabled. Orientation request should
            // be ignored if a loop is detected.
            assertTrue(session.getActivityState().getShouldIgnoreOrientationRequestLoop());
        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is respected when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * enabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is set to false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyFalse_overrideEnabled_overrideNotApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED,
                ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_OUT_ACTIVITY
                        .getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_OUT_ACTIVITY)) {
            // Activity with property set to false and override enabled. Orientation request should
            // be respected even if a loop is detected.
            assertFalse(session.getActivityState().getShouldIgnoreOrientationRequestLoop());


        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is respected when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * disabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is set to true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyTrue_overrideDisabled_overrideNotApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var session = new ActivitySessionCloseable(
                     ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_IN_ACTIVITY)) {
            // Activity with property set to true but with override disabled. Orientation request
            // should be respected even if a loop is detected.
            assertFalse(session.getActivityState().getShouldIgnoreOrientationRequestLoop());
        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is respected when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is set to false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyFalse_overrideDisabled_overrideNotApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var session = new ActivitySessionCloseable(
                     ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_OPT_OUT_ACTIVITY)) {
            // Activity with property set to false and with override disabled. Orientation request
            // should be respected even if a loop is detected.
            assertFalse(session.getActivityState().getShouldIgnoreOrientationRequestLoop());
        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is ignored when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyNotSet_overrideEnabled_overrideApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var compatChange = new CompatChangeCloseable(
                OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED,
                ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_UNSET_ACTIVITY
                        .getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_UNSET_ACTIVITY)) {
            // Activity without property set and override enabled. Property will default to true.
            // Orientation request should be ignored if a loop is detected.
            assertTrue(session.getActivityState().getShouldIgnoreOrientationRequestLoop());
        }
    }

    /**
     * Test that if an orientation loop is detected, the orientation request is respected when
     * {@link ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED} is
     * disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED}
     * is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_ENABLE_COMPAT_IGNORE_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideIgnoreOrientationRequestWhenLoopDetected_propertyNotSet_overrideDisabled_overrideNotApplied() {
        assumeTrue("Skipping test: "
                        + "config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled disabled",
                isPolicyForIgnoringRequestedOrientationEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_IGNORING_ORIENTATION_REQUEST_WHEN_LOOP_DETECTED_UNSET_ACTIVITY)) {
            // Activity without property set and with override disabled. Orientation request should
            // be respected even if a loop is detected.
            assertFalse(session.getActivityState().getShouldIgnoreOrientationRequestLoop());
        }
    }


    @Test
    public void testOptOutPropertyCameraCompatForceRotation_rotationDisabled() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            // Activity without property or override is eligible for force rotation.
            waitAssertEquals("expected to force rotate for camera compat",
                    /* expected */ true,
                    () -> session.getActivityState().getShouldForceRotateForCameraCompat());
        }

        try (var session = new ActivitySessionCloseable(
                     CAMERA_COMPAT_ALLOW_FORCE_ROTATION_ACTIVITY)) {
            waitAssertEquals("expected to not force rotate for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState().getShouldForceRotateForCameraCompat());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_FORCE_ROTATION})
    public void testOverrideForCameraCompatForceRotation_rotationDisabled() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            waitAssertEquals("expected to not force rotate for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState().getShouldForceRotateForCameraCompat());
        }
    }

    @Test
    public void testOptOutPropertyCameraCompatRefresh() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            // Activity without property or override is eligible for refresh.
            waitAssertEquals("expected to refresh activity for camera compat",
                    /* expected */ true,
                    () -> session.getActivityState().getShouldRefreshActivityForCameraCompat());
        }

        try (var session = new ActivitySessionCloseable(CAMERA_COMPAT_ALLOW_REFRESH_ACTIVITY)) {
            waitAssertEquals("expected to not refresh activity for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState().getShouldRefreshActivityForCameraCompat());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_DISABLE_REFRESH})
    public void testOverrideForCameraCompatRefresh() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            waitAssertEquals("expected to not refresh activity for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState().getShouldRefreshActivityForCameraCompat());
        }
    }

    @Test
    public void testOptInPropertyCameraCompatRefreshViaPause() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            // Activity without property or override doesn't refresh via
            // "resumed -> paused -> resumed".
            waitAssertEquals("expected to not refresh activity via pause for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState()
                            .getShouldRefreshActivityViaPauseForCameraCompat());
        }

        try (var session = new ActivitySessionCloseable(
                CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_IN_ACTIVITY)) {
            waitAssertEquals("expected to Refresh activity via pause for camera compat",
                    /* expected */ true,
                    () -> session.getActivityState()
                            .getShouldRefreshActivityViaPauseForCameraCompat());
        }
    }

    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE})
    public void testOverrideForCameraCompatRefreshViaPause() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var session = new ActivitySessionCloseable(RESIZEABLE_PORTRAIT_ACTIVITY)) {
            waitAssertEquals("expected to Refresh activity via pause for camera compat",
                    /* expected */ true,
                    () -> session.getActivityState()
                            .getShouldRefreshActivityViaPauseForCameraCompat());
        }
    }

    @Test
    public void testOptOutPropertyCameraCompatRefreshViaPause() {
        assumeTrue("Skipping test: config_isWindowManagerCameraCompatTreatmentEnabled not enabled",
                isCameraCompatForceRotationTreatmentConfigEnabled());

        try (var compatChange = new CompatChangeCloseable(
                ActivityInfo.OVERRIDE_CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE,
                CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY.getPackageName());
             var session = new ActivitySessionCloseable(
                     CAMERA_COMPAT_ENABLE_REFRESH_VIA_PAUSE_OPT_OUT_ACTIVITY)) {
            waitAssertEquals("expected to not refresh activity via pause for camera compat",
                    /* expected */ false,
                    () -> session.getActivityState()
                            .getShouldRefreshActivityViaPauseForCameraCompat());
        }
    }

    /**
     * Test that an activity is forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is set to
     * true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideForceResizeApp_propertyTrue_overrideEnabled_overrideApplied() {
        try (var compatChange = new CompatChangeCloseable(
                FORCE_RESIZE_APP,
                ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_IN_ACTIVITY.getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_IN_ACTIVITY)) {
            // Activity with property set to true and override enabled. Activity should be forced to
            // be resizeable.
            assertTrue(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that an activity is not forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is enabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is set to
     * false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideForceResizeApp_propertyFalse_overrideEnabled_overrideNotApplied() {
        try (var compatChange = new CompatChangeCloseable(
                FORCE_RESIZE_APP,
                ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_OUT_ACTIVITY.getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_OUT_ACTIVITY)) {
            // Activity with property set to false and override enabled. Activity should not be
            // forced to be resizeable.
            assertFalse(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that an activity is not forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is disabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is set to
     * true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideForceResizeApp_propertyTrue_overrideDisabled_overrideNotApplied() {
        try (var session = new ActivitySessionCloseable(
                     ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_IN_ACTIVITY)) {
            // Activity with property set to true and override disabled. Activity should not be
            // forced to be resizeable.
            assertFalse(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that an activity is not forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is set to
     * false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideForceResizeApp_propertyFalse_overrideDisabled_overrideApplied() {
        try (var session = new ActivitySessionCloseable(
                     ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES_OPT_OUT_ACTIVITY)) {
            // Activity with property set to false and override disabled. Activity should not be
            // forced to be resizeable.
            assertFalse(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that an activity is forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    @EnableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testOverrideForceResizeApp_propertyUnset_overrideEnabled_overrideApplied() {
        try (var session = new ActivitySessionCloseable(
                     NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            // Activity with property unset and override enabled. Property will default to true.
            // Activity should be forced to be resizeable.
            assertTrue(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that an activity is not forced to be resizeable when {@link
     * ActivityInfo#FORCE_RESIZE_APP} is disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES} is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#FORCE_RESIZE_APP",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_RESIZEABLE_ACTIVITY_OVERRIDES"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    @DisableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testOverrideForceResizeApp_propertyUnset_overrideDisabled_overrideNotApplied() {
        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            // Activity with property unset and override disabled. Activity should not be forced to
            // be resizeable.
            assertFalse(session.getActivityState().getShouldOverrideForceResizeApp());
        }
    }

    /**
     * Test that a non-resizeable portrait activity enters size compat mode after resizing the
     * display.
     */
    @Test
    public void testSizeCompatForNonResizeableActivity() {
        runSizeCompatTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize */ true);
    }

    /**
     * Test that a non-resizeable portrait activity doesn't enter size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_RESIZE_APP})
    public void testSizeCompatForNonResizeableActivityForceResizeEnabled() {
        runSizeCompatTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /*inSizeCompatModeAfterResize */ false);
    }

    /**
     * Test that a resizeable portrait activity doesn't enter size compat mode after resizing
     * the display.
     */
    @Test
    public void testSizeCompatForResizeableActivity() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize */ false);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes doesn't enter size
     * compat mode after resizing the display.
     */
    @Test
    public void testSizeCompatForSupportsSizeChangesActivity() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize */ false);
    }

    /**
     * Test that a resizeable portrait activity enters size compat mode after resizing
     * the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForResizeableActivityForceNonResizeEnabled() {
        runSizeCompatTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize */ true);
    }

    /**
     * Test that a non-resizeable portrait activity that supports size changes enters size compat
     * mode after resizing the display, when the {@link ActivityInfo#FORCE_NON_RESIZE_APP} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.FORCE_NON_RESIZE_APP})
    public void testSizeCompatForSupportsSizeChangesActivityForceNonResizeEnabled() {
        runSizeCompatTest(
                SUPPORTS_SIZE_CHANGES_PORTRAIT_ACTIVITY, /* inSizeCompatModeAfterResize */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode results in sandboxed
     * Display APIs.
     */
    @Test
    public void testSandboxForNonResizableAspectRatioActivity() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed */ true, /* inSizeCompatModeAfterResize */ true);
    }

     // =================
     // NEVER_SANDBOX test cases
     // =================
     // Validates that an activity forced into size compat mode never has sandboxing applied to the
     // max bounds. It is expected that an activity in size compat mode normally always has
     // sandboxing applied.

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the {@link ActivityInfo#NEVER_SANDBOX_DISPLAY_APIS} compat change is
     * enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.NEVER_SANDBOX_DISPLAY_APIS})
    public void testSandboxForNonResizableAspectRatioActivityNeverSandboxDisplayApisEnabled() {
        runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                /* isSandboxed */ false, /* inSizeCompatModeAfterResize */ true);
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'never_constrain_display_apis_all_packages' Device Config
     * flag is true.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigAllPackagesFlagTrue() {
        try (var neverForAll = setNeverConstrainDisplayApisAllPackagesFlag("true");
                var neverAnApp = setNeverConstrainDisplayApisFlag("com.android.other::")) {
            runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                    /* isSandboxed */ false, /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with an open ended range.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageUnboundedInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        try (var neverForApp = setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::")) {
            runSizeCompatModeSandboxTest(activity, /* isSandboxed */ false,
                    /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does not have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that matches the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageWithinRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        try (var neverForApp = setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version - 1) + ":" + String.valueOf(version + 1))) {
            runSizeCompatModeSandboxTest(activity, /* isSandboxed */ false,
                    /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains the test
     * package with a version range that doesn't match the installed version of the package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageOutsideRangeInNeverSandboxDeviceConfig() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        long version = getPackageVersion(activity);
        try (var neverForApp = setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":" + String.valueOf(
                        version + 1) + ":")) {
            runSizeCompatModeSandboxTest(activity, /* isSandboxed */ true,
                    /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag doesn't contain the
     * test package.
     */
    @Test
    public void testSandboxForNonResizableActivityPackageNotInNeverSandboxDeviceConfigFlag() {
        try (var neverForApp = setNeverConstrainDisplayApisFlag(
                "com.android.other::,com.android.other2::")) {
            runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                    /* isSandboxed */ true, /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxForNonResizableActivityNeverSandboxDeviceConfigFlagEmpty() {
        try (var empty = setNeverConstrainDisplayApisFlag("")) {
            runSizeCompatModeSandboxTest(NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY,
                    /* isSandboxed */ true, /* inSizeCompatModeAfterResize */ true);
        }
    }

    /**
     * Test that a min aspect ratio activity eligible for size compat mode does have the Display
     * APIs sandboxed when the 'never_constrain_display_apis' Device Config flag contains an invalid
     * entry for the test package.
     */
    @Test
    public void testSandboxForNonResizableActivityInvalidEntryInNeverSandboxDeviceConfigFlag() {
        ComponentName activity = NON_RESIZEABLE_LARGE_ASPECT_RATIO_ACTIVITY;
        try (var neverForApp = setNeverConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + ":::")) {
            runSizeCompatModeSandboxTest(activity, /* isSandboxed */ true,
                    /* inSizeCompatModeAfterResize */ true);
        }
    }

    /** =================
     * SANDBOX_VIEW_BOUNDS_APIS test cases
     * @see #testSandbox_viewApiForLetterboxedActivity
     * @see #testNoSandbox_viewApiForLetterboxedActivity
     * @see #testNoSandbox_viewApiForLetterboxedActivityOptOut
     * =================
     * Validates that an activity in letterbox mode has sandboxing applied to the
     * view bounds when OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS is set.
     * Without this flag or with
     * {@link android.view.WindowManager#PROPERTY_COMPAT_ALLOW_SANDBOXING_VIEW_BOUNDS_APIS}
     * value=false in AndroidManifest.xml
     * {@link android.view.View#getLocationOnScreen},
     * {@link android.view.View#getWindowDisplayFrame}
     * {@link android.view.View#getBoundsOnScreen}
     * and {@link android.view.View#getWindowVisibleDisplayFrame}
     * return location or display frame offset by the window location on the screen:
     * {@link WindowConfiguration#getBounds}
     */
    @Test
    public void testSandbox_viewApiForLetterboxedActivity() {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var aspectRatio = new DisplayAspectRatioCloseable(ORIENTATION_LANDSCAPE, 2.0f);
             var compatChange = new CompatChangeCloseable(
                OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS,
                TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY.getPackageName());
             var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED)) {

            try (var session = new ActivitySessionCloseable(TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY)) {
                // Wait for the broadcast action
                boolean testPassed = receiver
                        .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_ALLOWED_PASSED)
                        .block(TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS);

                assertThat(testPassed).isTrue();
            }
        }
    }

    @Test
    public void testNoSandbox_viewApiForLetterboxedActivity() {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var aspectRatio = new DisplayAspectRatioCloseable(ORIENTATION_LANDSCAPE, 2.0f);
             var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED)) {

            try (var session = new ActivitySessionCloseable(TEST_VIEW_SANDBOX_ALLOWED_ACTIVITY)) {
                // Wait for the broadcast action
                boolean testPassed = receiver
                        .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_NOT_ALLOWED_PASSED)
                        .block(TEST_VIEW_SANDBOX_ALLOWED_TIMEOUT_MS);

                assertThat(testPassed).isTrue();
            }
        }
    }

    @Test
    public void testNoSandbox_viewApiForLetterboxedActivityOptOut() {
        // Enable OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS changeId for the test application
        try (var aspectRatio = new DisplayAspectRatioCloseable(ORIENTATION_LANDSCAPE, 2.0f);
             var compatChange = new CompatChangeCloseable(
                OVERRIDE_SANDBOX_VIEW_BOUNDS_APIS,
                TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY.getPackageName());
             var receiver = new BroadcastReceiverCloseable(mContext,
                     ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED)) {

            try (var session = new ActivitySessionCloseable(TEST_VIEW_SANDBOX_OPT_OUT_ACTIVITY)) {
                // Wait for the broadcast action
                boolean testPassed = receiver
                        .getBroadcastReceivedVariable(ACTION_TEST_VIEW_SANDBOX_OPT_OUT_PASSED)
                        .block(TEST_VIEW_SANDBOX_OPT_OUT_TIMEOUT_MS);

                assertThat(testPassed).isTrue();
            }
        }
    }

    // =================
    // ALWAYS_SANDBOX test cases
    // =================
    // Validates that an activity simply in letterbox mode has sandboxing applied to the max
    // bounds when ALWAYS_SANDBOX is set. Without the flag, we would not expect a letterbox activity
    // to be sandboxed, unless it is also eligible for size compat mode.

    /**
     * Test that a portrait activity not eligible for size compat mode does have the
     * Display APIs sandboxed when the {@link ActivityInfo#ALWAYS_SANDBOX_DISPLAY_APIS} compat
     * change is enabled.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.ALWAYS_SANDBOX_DISPLAY_APIS})
    public void testSandboxForResizableActivityAlwaysSandboxDisplayApisEnabled() {
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed */ true);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does not have the
     * Display APIs sandboxed when the 'always_constrain_display_apis' Device Config flag is empty.
     */
    @Test
    public void testSandboxResizableActivityAlwaysSandboxDeviceConfigFlagEmpty() {
        runLetterboxSandboxTest(RESIZEABLE_PORTRAIT_ACTIVITY, /* isSandboxed */ false);
    }

    /**
     * Test that a portrait activity not eligible for size compat mode does have the Display
     * APIs sandboxed when the 'always_constrain_display_apis' Device Config flag contains the test
     * package.
     */
    @Test
    public void testSandboxResizableActivityPackageInAlwaysSandboxDeviceConfigFlag() {
        ComponentName activity = RESIZEABLE_PORTRAIT_ACTIVITY;
        try (var alwaysForApp = setAlwaysConstrainDisplayApisFlag(
                "com.android.other::," + activity.getPackageName() + "::")) {
            runLetterboxSandboxTest(activity, /* isSandboxed */ true);
        }
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} has no effect on its
     * own. The aspect ratio of the activity should be the same as that of the task, which should be
     * in line with that of the display.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO})
    public void testOverrideMinAspectRatioMissingSpecificOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected */ 0);
    }

    /**
     * Test that only applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * its own without the presence of {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioMissingGeneralOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY, /* expected */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForLandscapeActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation is fixed to landscape.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_PORTRAIT_ONLY})
    public void testOverrideMinAspectRatioForLandscapeActivityPortraitOnlyDisabled() {
        runMinAspectRatioTest(NON_RESIZEABLE_LANDSCAPE_ACTIVITY, /* expected */
                OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} has no effect on
     * activities whose orientation isn't fixed.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioForNonFixedOrientationActivity() {
        runMinAspectRatioTest(NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY, /* expected */ 0);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioLargeAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that applying {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM} sets the min aspect
     * ratio to {@link ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE}.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioMediumAspectRatio() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected */ OVERRIDE_MIN_ASPECT_RATIO_MEDIUM_VALUE);
    }

    /**
     * Test that applying multiple min aspect ratio overrides result in the largest one taking
     * effect.
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioBothAspectRatios() {
        runMinAspectRatioTest(NON_RESIZEABLE_PORTRAIT_ACTIVITY,
                /* expected */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is ignored if
     * there is an override for a larger min aspect ratio present (16:9 > 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_LARGE})
    public void testOverrideMinAspectRatioActivityMinAspectRatioSmallerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected */ OVERRIDE_MIN_ASPECT_RATIO_LARGE_VALUE);
    }

    /**
     * Test that the min aspect ratio of the activity as defined in the manifest is upheld if
     * there is an override for a smaller min aspect ratio present (3:2 < 1.6).
     */
    @Test
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO,
            ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO_MEDIUM})
    public void testOverrideMinAspectRatioActivityMinAspectRatioLargerThanOverride() {
        runMinAspectRatioTest(NON_RESIZEABLE_ASPECT_RATIO_ACTIVITY,
                /* expected */ ACTIVITY_MIN_ASPECT_RATIO);
    }

    /**
     * Test that an activities min aspect ratio is overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideMinAspectRatio_propertyTrue_overrideEnabled_overrideApplied() {
        try (var compatChange = new CompatChangeCloseable(OVERRIDE_MIN_ASPECT_RATIO,
                ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY.getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertTrue(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that an activities min aspect ratio is not overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is enabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideMinAspectRatio_propertyFalse_overrideEnabled_overrideNotApplied() {
        try (var compatChange = new CompatChangeCloseable(OVERRIDE_MIN_ASPECT_RATIO,
                ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY.getPackageName());
                var session = new ActivitySessionCloseable(
                        ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that an activities min aspect ratio is overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is enabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    @EnableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO})
    public void testOverrideMinAspectRatio_propertyNotSet_overrideEnabled_overrideApplied() {
        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertTrue(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that an activities min aspect ratio is not overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is unset.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    @DisableCompatChanges({ActivityInfo.OVERRIDE_MIN_ASPECT_RATIO})
    public void testOverrideMinAspectRatio_propertyNotSet_overrideDisabled_overrideNotApplied() {
        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that an activities min aspect ratio is not overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is disabled but {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is true.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideMinAspectRatio_propertyTrue_overrideUnset_overrideNotApplied() {
        try (var session = new ActivitySessionCloseable(
                ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that an activities min aspect ratio is not overridden when {@link
     * ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO} is disabled and {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE} is false.
     */
    @Test
    @ApiTest(apis = {"android.content.pm.ActivityInfo#OVERRIDE_MIN_ASPECT_RATIO",
            "android.view.WindowManager#PROPERTY_COMPAT_ALLOW_MIN_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testOverrideMinAspectRatio_propertyFalse_overrideUnset_overrideNotApplied() {
        try (var session = new ActivitySessionCloseable(
                ALLOW_MIN_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldOverrideMinAspectRatio());
        }
    }

    /**
     * Test that the user aspect ratio settings are enabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is set to true
     * and {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyTrue_configEnabled_overrideAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertTrue(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio settings are disabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is set to true
     * but {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyTrue_configDisabled_overrideNotAllowed() {
        assumeFalse("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled is enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio settings are disabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is set to false
     * but {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyFalse_configEnabled_overrideNotAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio settings are disabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is set to false
     * and {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyFalse_configDisabled_overrideNotAllowed() {
        assumeFalse("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled is enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio settings are enabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is unset and
     * {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyUnset_configEnabled_overrideAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertTrue(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio settings are disabled when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE} is unset but
     * {@link com.android.internal.R.bool.config_appCompatUserAppAspectRatioSettingsIsEnabled}
     * is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioSettings_propertyUnset_configDisabled_overrideNotAllowed() {
        assumeFalse("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled is enabled",
                isUserAppAspectRatioSettingsIsEnabled());

        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertFalse(session.getActivityState().getShouldEnableUserAspectRatioSettings());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * set to true and {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioFullscreen_propertyTrue_configEnabled_overrideAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeTrue(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled not enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertTrue(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is not applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * set to true but {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void
            testAllowUserAspectRatioFullscreen_propertyTrue_configDisabled_overrideNotAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeFalse(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled is enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_IN_ACTIVITY)) {
            assertFalse(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is not applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * set to false but {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void
            testAllowUserAspectRatioFullscreen_propertyFalse_configEnabled_overrideNotAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeTrue(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled not enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is not applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * set to false and {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void
            testAllowUserAspectRatioFullscreen_propertyFalse_configDisabled_overrideNotAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeFalse(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled is enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE_OPT_OUT_ACTIVITY)) {
            assertFalse(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * unset but {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is true.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void testAllowUserAspectRatioFullscreen_propertyUnset_configEnabled_overrideAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeTrue(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled not enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertTrue(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    /**
     * Test that the user aspect ratio fullscreen override is not applicable through the user aspect
     * ratio settings when {@link
     * android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_FULLSCREEN_OVERRIDE} is
     * set to unset but {@link
     * com.android.internal.R.bool.config_appCompatUserAppAspectRatioFullscreenIsEnabled} is false.
     */
    @Test
    @ApiTest(apis = {"android.view.WindowManager#PROPERTY_COMPAT_ALLOW_USER_ASPECT_RATIO_OVERRIDE"})
    @RequiresFlagsEnabled(Flags.FLAG_APP_COMPAT_PROPERTIES_API)
    public void
            testAllowUserAspectRatioFullscreen_propertyUnset_configDisabled_overrideNotAllowed() {
        assumeTrue("Skipping test: config_appCompatUserAppAspectRatioSettingsIsEnabled not enabled",
                isUserAppAspectRatioSettingsIsEnabled());
        assumeFalse(
                "Skipping test: config_appCompatUserAppAspectRatioFullscreenIsEnabled is enabled",
                isUserAppAspectRatioFullscreenIsEnabled());

        try (var session = new ActivitySessionCloseable(
                NON_RESIZEABLE_NON_FIXED_ORIENTATION_ACTIVITY)) {
            assertFalse(session.getActivityState().getIsUserFullscreenOverrideEnabled());
        }
    }

    private boolean isUserAppAspectRatioFullscreenIsEnabled() {
        return getBooleanConfig("config_appCompatUserAppAspectRatioFullscreenIsEnabled");
    }

    private boolean isUserAppAspectRatioSettingsIsEnabled() {
        return getBooleanConfig("config_appCompatUserAppAspectRatioSettingsIsEnabled");
    }

    private boolean isCameraCompatForceRotationTreatmentConfigEnabled() {
        return getBooleanConfig("config_isWindowManagerCameraCompatTreatmentEnabled");
    }

    private boolean isPolicyForIgnoringRequestedOrientationEnabled() {
        return getBooleanConfig("config_letterboxIsPolicyForIgnoringRequestedOrientationEnabled");
    }

    private boolean getBooleanConfig(String configName) {
        return mContext.getResources().getBoolean(
                Resources.getSystem().getIdentifier(configName, "bool", "android"));
    }

    /**
     * Launches the provided activity into size compat mode twice. The first time, the display
     * is resized to be half the size. The second time, the display is resized to be twice the
     * original size.
     *
     * @param activity                    the activity under test.
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTest(ComponentName activity, boolean inSizeCompatModeAfterResize) {
        try (var session = new ActivitySessionCloseable(activity)) {
            runSizeCompatTestForActivity(activity, /* resizeRatio */ 0.5,
                    inSizeCompatModeAfterResize);
        }

        try (var session = new ActivitySessionCloseable(activity)) {
            runSizeCompatTestForActivity(activity, /* resizeRatio */ 2,
                    inSizeCompatModeAfterResize);
        }
    }

    /**
     * Launches the provided activity on the default display, initially not in size compat mode.
     * After resizing the display, verifies if activity is in size compat mode or not
     *
     * @param activity                    the activity under test
     * @param resizeRatio                 the ratio to resize the display
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatTestForActivity(ComponentName activity, final double resizeRatio,
            final boolean inSizeCompatModeAfterResize) {

        waitAndAssertSizeCompatMode(activity, /* expectedInSizeCompatMode */ false);

        try (var resized = new DisplaySizeScaleCloseable(resizeRatio, activity)) {
            assertThat(resized.getInitialDisplayAspectRatio())
                    .isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);
            waitAndAssertSizeCompatMode(activity, inSizeCompatModeAfterResize);
        }
    }

    private void waitAndAssertSizeCompatMode(final ComponentName activity,
            final boolean expectedInSizeCompatMode) {
        waitAssertEquals("The window must be inSizeCompatMode==" + expectedInSizeCompatMode,
                expectedInSizeCompatMode,
                () -> getActivityWaitState(activity).inSizeCompatMode());
    }

    /**
     * Similar to {@link #runSizeCompatTest(ComponentName, boolean)}, but the activity is
     * expected to be in size compat mode after resizing the display.
     *
     * @param activity                    the activity under test
     * @param isSandboxed                 when {@code true},
     * {@link android.app.WindowConfiguration#getMaxBounds()}
     *                                    are sandboxed to the activity bounds. Otherwise, they
     *                                    inherit the
     *                                    DisplayArea bounds
     * @param inSizeCompatModeAfterResize if the activity should be in size compat mode after
     *                                    resizing the display
     */
    private void runSizeCompatModeSandboxTest(ComponentName activity, boolean isSandboxed,
            boolean inSizeCompatModeAfterResize) {
        try (var session = new ActivitySessionCloseable(activity, WINDOWING_MODE_FULLSCREEN)) {
            runSizeCompatTestForActivity(activity, /* resizeRatio */ 0.5,
                    inSizeCompatModeAfterResize);
            assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
            assertSandboxedByBounds(activity, isSandboxed);
        }

        try (var session = new ActivitySessionCloseable(activity, WINDOWING_MODE_FULLSCREEN)) {
            runSizeCompatTestForActivity(activity, /* resizeRatio */ 2,
                    inSizeCompatModeAfterResize);
            assertSandboxedByProvidesMaxBounds(activity, isSandboxed);
            assertSandboxedByBounds(activity, isSandboxed);
        }
    }

    /**
     * Similar to {@link #runSizeCompatModeSandboxTest(ComponentName, boolean, boolean)}, but the
     * activity is put into letterbox mode after resizing the display.
     *
     * @param activityName the activity under test
     * @param isSandboxed  when {@code true}, {@link android.app.WindowConfiguration#getMaxBounds()}
     *                     are sandboxed to the activity bounds. Otherwise, they inherit the
     *                     DisplayArea bounds
     */
    private void runLetterboxSandboxTest(ComponentName activityName, boolean isSandboxed) {
        try (var aspectRatio = new DisplayAspectRatioCloseable(ORIENTATION_LANDSCAPE, 2.0f)) {
            assertThat(aspectRatio.getInitialDisplayAspectRatio())
                    .isLessThan(ACTIVITY_LARGE_MIN_ASPECT_RATIO);

            try (var session = new ActivitySessionCloseable(activityName)) {
                assertSandboxedByProvidesMaxBounds(activityName, isSandboxed);
            }
        }
    }

    private void assertSandboxedByBounds(ComponentName activityName, boolean isSandboxed) {
        final WindowManagerState.Activity activity = getActivityWaitState(activityName);
        assertNotNull(activity);
        final Rect activityBounds = activity.getBounds();
        final Rect maxBounds = activity.getMaxBounds();
        WindowManagerState.DisplayArea tda = mWmState.getTaskDisplayArea(activityName);
        assertNotNull(tda);
        if (isSandboxed) {
            assertEquals(
                    "The window has max bounds sandboxed to the window bounds",
                    activityBounds, maxBounds);
        } else {
            assertEquals(
                    "The window is not sandboxed, with max bounds reflecting the DisplayArea",
                    tda.getBounds(), maxBounds);
        }
    }

    private void assertSandboxedByProvidesMaxBounds(ComponentName activityName,
            boolean isSandboxed) {
        final WindowManagerState.Activity activity = getActivityWaitState(activityName);
        assertNotNull(activity);
        if (isSandboxed) {
            assertTrue(
                    "The window should have max bounds sandboxed to the window bounds",
                    activity.providesMaxBounds());
        } else {
            assertFalse(
                    "The window should not be sandboxed; max bounds should reflect the DisplayArea",
                    activity.providesMaxBounds());
        }
    }

    private CloseableDeviceConfig setNeverConstrainDisplayApisFlag(@Nullable String value) {
        return setConstrainDisplayApisFlag("never_constrain_display_apis", value);
    }

    private CloseableDeviceConfig setNeverConstrainDisplayApisAllPackagesFlag(
            @Nullable String value) {
        return setConstrainDisplayApisFlag("never_constrain_display_apis_all_packages", value);
    }

    private CloseableDeviceConfig setAlwaysConstrainDisplayApisFlag(
            @Nullable String value) {
        return setConstrainDisplayApisFlag("always_constrain_display_apis", value);
    }

    private CloseableDeviceConfig setConstrainDisplayApisFlag(@NonNull String flagName,
            @Nullable String value) {
        return new CloseableDeviceConfig(NAMESPACE_CONSTRAIN_DISPLAY_APIS, flagName, value);
    }

    /**
     * Launches the provided activity and verifies that its min aspect ratio is equal to {@code
     * expected}.
     *
     * @param activity the activity under test.
     * @param expected the expected min aspect ratio in both portrait and landscape displays.
     */
    private void runMinAspectRatioTest(ComponentName activity, float expected) {
        try (var session = new ActivitySessionCloseable(activity)) {
            assertNotNull(session.getActivityState());
            assertEquals(expected,
                    session.getActivityState().getMinAspectRatio(),
                    FLOAT_EQUALITY_DELTA);
        }
    }

    private long getPackageVersion(ComponentName activity) {
        try {
            return mContext.getPackageManager().getPackageInfo(activity.getPackageName(),
                    /* flags */ 0).getLongVersionCode();
        } catch (PackageManager.NameNotFoundException e) {
            throw new RuntimeException(e);
        }
    }

    private static ComponentName component(Class<? extends Activity> activity) {
        return new ComponentName(getInstrumentation().getContext(), activity);
    }

    public static class ResizeablePortraitActivity extends AbstractLifecycleLogActivity {
    }

    public static class ResponsiveActivity extends AbstractLifecycleLogActivity {
    }

    public static class NonResizeablePortraitActivity extends AbstractLifecycleLogActivity {
    }

    public static class NonResizeableLandscapeActivity extends AbstractLifecycleLogActivity {
    }

    public static class NonResizeableNonFixedOrientationActivity extends
            AbstractLifecycleLogActivity {
    }

    public static class NonResizeableAspectRatioActivity extends AbstractLifecycleLogActivity {
    }

    public static class NonResizeableLargeAspectRatioActivity extends AbstractLifecycleLogActivity {
    }

    public static class SupportsSizeChangesPortraitActivity extends AbstractLifecycleLogActivity {
    }

    public static class ResizeableLeftActivity extends AbstractLifecycleLogActivity {
    }

    public static class ResizeableRightActivity extends AbstractLifecycleLogActivity {
    }

    public static class NoPropertyChangeOrientationWhileRelaunchingActivity extends
            AbstractLifecycleLogActivity {

        private static boolean sHasChangeOrientationInOnResume;

        @Override
        protected void onCreate(Bundle instance) {
            super.onCreate(instance);
            // When OVERRIDE_ENABLE_COMPAT_IGNORE_REQUESTED_ORIENTATION is enabled this request
            // should be ignored if sHasChangeOrientationInOnResume is true.
            setRequestedOrientation(SCREEN_ORIENTATION_LANDSCAPE);
        }

        @Override
        protected void onResume() {
            super.onResume();
            if (!sHasChangeOrientationInOnResume) {
                setRequestedOrientation(SCREEN_ORIENTATION_PORTRAIT);
                sHasChangeOrientationInOnResume = true;
            }
        }
    }

    /**
     * Registers broadcast receiver which receives result actions from Activities under test.
     */
    private static class BroadcastReceiverCloseable implements AutoCloseable {
        private final Context mContext;
        private final Map<String, ConditionVariable> mBroadcastsReceived;
        private final BroadcastReceiver mAppCommunicator = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                getBroadcastReceivedVariable(intent.getAction()).open();
            }
        };

        BroadcastReceiverCloseable(final Context context, final String action) {
            this.mContext = context;
            // Keep the received broadcast items in the map.
            mBroadcastsReceived = Collections.synchronizedMap(new HashMap<>());
            // Register for broadcast actions.
            IntentFilter filter = new IntentFilter();
            filter.addAction(action);
            mContext.registerReceiver(mAppCommunicator, filter, Context.RECEIVER_EXPORTED);
        }

        ConditionVariable getBroadcastReceivedVariable(String action) {
            return mBroadcastsReceived.computeIfAbsent(action, key -> new ConditionVariable());
        }

        @Override
        public void close() {
            mContext.unregisterReceiver(mAppCommunicator);
        }
    }

    /**
     * Resets device config to the original value after the try-with-resources block finishes
     * try (var dc = new CloseableDeviceConfig()) {...}
     */
    private class CloseableDeviceConfig implements AutoCloseable {
        private String mOriginalValue;
        private final String mNamespace;
        private final String mFlagName;

        CloseableDeviceConfig(@NonNull String namespace, @NonNull String flagName,
                @Nullable String value) {
            mNamespace = namespace;
            mFlagName = flagName;
            runWithShellPermission(() -> {
                mOriginalValue = DeviceConfig.getProperty(namespace, flagName);
                DeviceConfig.setProperty(namespace, flagName, value, /* makeDefault */
                        false);
            });
        }

        @Override
        public void close() {
            runWithShellPermission(() -> DeviceConfig.setProperty(mNamespace, mFlagName,
                    mOriginalValue, /* makeDefault */ false));
        }
    }
}
