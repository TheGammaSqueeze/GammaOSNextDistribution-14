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

package android.server.wm.other;

import static android.server.wm.app.Components.UI_SCALING_TEST_ACTIVITY;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_ADD_SUBVIEW;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_CLEAR_DEFAULT_VIEW;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_GET_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_GET_SUBVIEW_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.COMMAND_UPDATE_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_COMMAND_SUCCESS;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_RESOURCES_CONFIG;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_SUBVIEW_ID;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_TEXT_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.KEY_VIEW_SIZE;
import static android.server.wm.app.Components.UiScalingTestActivity.SUBVIEW_ID1;
import static android.server.wm.app.Components.UiScalingTestActivity.SUBVIEW_ID2;

import static org.hamcrest.Matchers.closeTo;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.not;

import android.content.ComponentName;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.os.LocaleList;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CommandSession;
import android.server.wm.WindowManagerState;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ErrorCollector;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Arrays;

/**
 * The test is focused on compatibility scaling, and tests the feature form two sides. 1. It checks
 * that the applications "sees" the metrics in PXs, but the DP metrics remain the same. 2. It checks
 * the WindowManagerServer state, and makes sure that the scaling is correctly reflected in the
 * WindowState.
 *
 * <p>This is achieved by launching a {@link android.server.wm.app.UiScalingTestActivity} and having
 * it reporting the metrics it receives. The Activity also draws 3 UI elements: a text, a red square
 * with a 100dp side and a blue square with a 100px side. The text and the red square should have
 * the same when rendered on the screen (by HWC) both when the compat downscaling is enabled and
 * disabled. TODO(b/180098454): Add tests to make sure that the UI elements, which have their sizes
 * declared in DPs (the text and the red square) have the same sizes on the screen (after
 * composition).
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceOther:CompatScaleTests
 */
@RunWith(Parameterized.class)
public class CompatScaleTests extends ActivityManagerTestBase {
    /**
     * If application size is 1280, then Upscaling by 0.3 will make the surface 1280/0.3 = 4267.
     * Some devices do not support this high resolution, so limiting Upscaling test case for scaling
     * >= 0.5.
     */
    public static float MAX_UPSCALING_TESTED = 0.5f;

    @Parameterized.Parameters(name = "{0}")
    public static Iterable<Object[]> data() {
        return Arrays.asList(
                new Object[][] {
                    {"DOWNSCALE_30", 0.3f},
                    {"DOWNSCALE_35", 0.35f},
                    {"DOWNSCALE_40", 0.4f},
                    {"DOWNSCALE_45", 0.45f},
                    {"DOWNSCALE_50", 0.5f},
                    {"DOWNSCALE_55", 0.55f},
                    {"DOWNSCALE_60", 0.6f},
                    {"DOWNSCALE_65", 0.65f},
                    {"DOWNSCALE_70", 0.7f},
                    {"DOWNSCALE_75", 0.75f},
                    {"DOWNSCALE_80", 0.8f},
                    {"DOWNSCALE_85", 0.85f},
                    {"DOWNSCALE_90", 0.9f},
                });
    }

    @Rule public ErrorCollector collector = new ErrorCollector();

    private static final ComponentName ACTIVITY_UNDER_TEST = UI_SCALING_TEST_ACTIVITY;
    private static final String PACKAGE_UNDER_TEST = ACTIVITY_UNDER_TEST.getPackageName();
    private static final float EPSILON_GLOBAL_SCALE = 0.01f;
    private final String mCompatChangeName;
    private final float mCompatScale;
    private final float mInvCompatScale;
    private CommandSession.SizeInfo mAppSizesNormal;
    private CommandSession.SizeInfo mAppSizesDownscaled;
    private CommandSession.SizeInfo mAppSizesUpscaled;
    private WindowManagerState.WindowState mWindowStateNormal;
    private WindowManagerState.WindowState mWindowStateDownscaled;
    private WindowManagerState.WindowState mWindowStateUpscaled;

    public CompatScaleTests(String compatChangeName, float compatScale) {
        mCompatChangeName = compatChangeName;
        mCompatScale = compatScale;
        mInvCompatScale = 1 / mCompatScale;
    }

    @Test
    public void testUpdateResourcesConfiguration() {
        // Launch activity with down/up scaling *disabled*
        try (var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
            runTestUpdateResourcesConfiguration(session.getActivitySession());
        }

        try (var scale = new CompatChangeCloseable(mCompatChangeName, PACKAGE_UNDER_TEST)) {
            // Now launch the same activity with downscaling *enabled*
            try (var down = new CompatChangeCloseable("DOWNSCALED", PACKAGE_UNDER_TEST);
                    var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
                runTestUpdateResourcesConfiguration(session.getActivitySession());
            }

            if (mCompatScale >= MAX_UPSCALING_TESTED) {
                // Now launch the same activity with upscaling *enabled*
                try (var up = new CompatChangeCloseable("DOWNSCALED_INVERSE", PACKAGE_UNDER_TEST);
                        var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
                    runTestUpdateResourcesConfiguration(session.getActivitySession());
                }
            }
        }
    }

    private void runTestUpdateResourcesConfiguration(CommandSession.ActivitySession activity) {
        activity.sendCommandAndWaitReply(COMMAND_CLEAR_DEFAULT_VIEW);
        addSubview(activity, SUBVIEW_ID1);
        Bundle subviewSize1 = getSubViewSize(activity, SUBVIEW_ID1);
        collector.checkThat(
                subviewSize1.getParcelable(KEY_TEXT_SIZE, Rect.class), not(equalTo(new Rect())));
        collector.checkThat(
                subviewSize1.getParcelable(KEY_VIEW_SIZE, Rect.class), not(equalTo(new Rect())));
        collector.checkThat(subviewSize1.getBoolean(KEY_COMMAND_SUCCESS), is(true));
        Configuration config =
                activity.sendCommandAndWaitReply(COMMAND_GET_RESOURCES_CONFIG)
                        .getParcelable(KEY_RESOURCES_CONFIG, Configuration.class);
        config.setLocales(LocaleList.forLanguageTags("en-US,en-XC"));
        Bundle data = new Bundle();
        data.putParcelable(KEY_RESOURCES_CONFIG, config);
        collector.checkThat(
                "Failed to update resources configuration",
                activity.sendCommandAndWaitReply(COMMAND_UPDATE_RESOURCES_CONFIG, data)
                        .getBoolean(KEY_COMMAND_SUCCESS),
                is(true));

        addSubview(activity, SUBVIEW_ID2);
        Bundle subviewSize2 = getSubViewSize(activity, SUBVIEW_ID2);
        collector.checkThat(subviewSize2.getBoolean(KEY_COMMAND_SUCCESS), is(true));
        collector.checkThat(
                subviewSize1.getParcelable(KEY_TEXT_SIZE, Rect.class),
                equalTo(subviewSize2.getParcelable(KEY_TEXT_SIZE, Rect.class)));
        collector.checkThat(
                subviewSize1.getParcelable(KEY_VIEW_SIZE, Rect.class),
                equalTo(subviewSize2.getParcelable(KEY_VIEW_SIZE, Rect.class)));
    }

    /**
     * Tests that the parameters that the application receives from the {@link
     * android.content.res.Configuration} are correctly scaled.
     */
    @Test
    public void test_scalesCorrectly() {
        // Launch activity with down/up scaling *disabled* and get the sizes it reports and its
        // Window state.
        try (var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
            mAppSizesNormal = session.getActivitySession().getConfigInfo().sizeInfo;
            mWindowStateNormal = getPackageWindowState();
        }

        try (var scale = new CompatChangeCloseable(mCompatChangeName, PACKAGE_UNDER_TEST)) {
            // Now launch the same activity with downscaling *enabled* and get the sizes it reports
            // and its Window state.
            try (var down = new CompatChangeCloseable("DOWNSCALED", PACKAGE_UNDER_TEST);
                    var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
                mAppSizesDownscaled = session.getActivitySession().getConfigInfo().sizeInfo;
                mWindowStateDownscaled = getPackageWindowState();
            }
            test_scalesCorrectly_inCompatDownscalingMode();
            test_windowState_inCompatDownscalingMode();

            if (mCompatScale >= MAX_UPSCALING_TESTED) {
                // Now launch the same activity with upscaling *enabled* and get the sizes it
                // reports and its Window state.
                try (var up = new CompatChangeCloseable("DOWNSCALED_INVERSE", PACKAGE_UNDER_TEST);
                        var session = new BaseActivitySessionCloseable(ACTIVITY_UNDER_TEST)) {
                    mAppSizesUpscaled = session.getActivitySession().getConfigInfo().sizeInfo;
                    mWindowStateUpscaled = getPackageWindowState();
                }
                test_scalesCorrectly_inCompatUpscalingMode();
                test_windowState_inCompatUpscalingMode();
            }
        }
    }

    private void test_scalesCorrectly_inCompatDownscalingMode() {
        checkScaled(
                "Density DPI should scale by " + mCompatScale,
                mAppSizesNormal.densityDpi,
                mCompatScale,
                mAppSizesDownscaled.densityDpi);
        collector.checkThat(
                "Width shouldn't change",
                mAppSizesNormal.widthDp,
                equalTo(mAppSizesDownscaled.widthDp));
        collector.checkThat(
                "Height shouldn't change",
                mAppSizesNormal.heightDp,
                equalTo(mAppSizesDownscaled.heightDp));
        collector.checkThat(
                "Smallest Width shouldn't change",
                mAppSizesNormal.smallestWidthDp,
                equalTo(mAppSizesDownscaled.smallestWidthDp));
        checkScaled(
                "Width should scale by " + mCompatScale,
                mAppSizesNormal.windowWidth,
                mCompatScale,
                mAppSizesDownscaled.windowWidth);
        checkScaled(
                "Height should scale by " + mCompatScale,
                mAppSizesNormal.windowHeight,
                mCompatScale,
                mAppSizesDownscaled.windowHeight);
        checkScaled(
                "App width should scale by " + mCompatScale,
                mAppSizesNormal.windowAppWidth,
                mCompatScale,
                mAppSizesDownscaled.windowAppWidth);
        checkScaled(
                "App height should scale by " + mCompatScale,
                mAppSizesNormal.windowAppHeight,
                mCompatScale,
                mAppSizesDownscaled.windowAppHeight);
        checkScaled(
                "Width should scale by " + mCompatScale,
                mAppSizesNormal.metricsWidth,
                mCompatScale,
                mAppSizesDownscaled.metricsWidth);
        checkScaled(
                "Height should scale by " + mCompatScale,
                mAppSizesNormal.metricsHeight,
                mCompatScale,
                mAppSizesDownscaled.metricsHeight);
        checkScaled(
                "Width should scale by " + mCompatScale,
                mAppSizesNormal.displayWidth,
                mCompatScale,
                mAppSizesDownscaled.displayWidth);
        checkScaled(
                "Height should scale by " + mCompatScale,
                mAppSizesNormal.displayHeight,
                mCompatScale,
                mAppSizesDownscaled.displayHeight);
    }

    private void test_scalesCorrectly_inCompatUpscalingMode() {
        checkScaled(
                "Density DPI should scale by " + mInvCompatScale,
                mAppSizesNormal.densityDpi,
                mInvCompatScale,
                mAppSizesUpscaled.densityDpi);
        collector.checkThat(
                "Width shouldn't change",
                mAppSizesNormal.widthDp,
                equalTo(mAppSizesUpscaled.widthDp));
        collector.checkThat(
                "Height shouldn't change",
                mAppSizesNormal.heightDp,
                equalTo(mAppSizesUpscaled.heightDp));
        collector.checkThat(
                "Smallest Width shouldn't change",
                mAppSizesNormal.smallestWidthDp,
                equalTo(mAppSizesUpscaled.smallestWidthDp));
        checkScaled(
                "Width should scale by " + mInvCompatScale,
                mAppSizesNormal.windowWidth,
                mInvCompatScale,
                mAppSizesUpscaled.windowWidth);
        checkScaled(
                "Height should scale by " + mInvCompatScale,
                mAppSizesNormal.windowHeight,
                mInvCompatScale,
                mAppSizesUpscaled.windowHeight);
        checkScaled(
                "App width should scale by " + mInvCompatScale,
                mAppSizesNormal.windowAppWidth,
                mInvCompatScale,
                mAppSizesUpscaled.windowAppWidth);
        checkScaled(
                "App height should scale by " + mInvCompatScale,
                mAppSizesNormal.windowAppHeight,
                mInvCompatScale,
                mAppSizesUpscaled.windowAppHeight);
        checkScaled(
                "Width should scale by " + mInvCompatScale,
                mAppSizesNormal.metricsWidth,
                mInvCompatScale,
                mAppSizesUpscaled.metricsWidth);
        checkScaled(
                "Height should scale by " + mInvCompatScale,
                mAppSizesNormal.metricsHeight,
                mInvCompatScale,
                mAppSizesUpscaled.metricsHeight);
        checkScaled(
                "Width should scale by " + mInvCompatScale,
                mAppSizesNormal.displayWidth,
                mInvCompatScale,
                mAppSizesUpscaled.displayWidth);
        checkScaled(
                "Height should scale by " + mInvCompatScale,
                mAppSizesNormal.displayHeight,
                mInvCompatScale,
                mAppSizesUpscaled.displayHeight);
    }

    private void test_windowState_inCompatDownscalingMode() {
        // Check the "normal" window's state for disabled compat mode and appropriate global scale.
        collector.checkThat(
                "The Window should not be in the size compat mode",
                mWindowStateNormal.hasCompatScale(),
                is(false));
        collector.checkThat(
                "The window should not be scaled",
                1d,
                closeTo(mWindowStateNormal.getGlobalScale(), EPSILON_GLOBAL_SCALE));

        // Check the "downscaled" window's state for enabled compat mode and appropriate global
        // scale.
        collector.checkThat(
                "The Window should be in the size compat mode",
                mWindowStateDownscaled.hasCompatScale(),
                is(true));
        collector.checkThat(
                "The window should have global scale of " + mInvCompatScale,
                (double) mInvCompatScale,
                closeTo(mWindowStateDownscaled.getGlobalScale(), EPSILON_GLOBAL_SCALE));

        // Make sure the frame sizes changed correctly.
        collector.checkThat(
                "Window frame on should not change",
                mWindowStateNormal.getFrame(),
                equalTo(mWindowStateDownscaled.getFrame()));
        checkScaled(
                "Requested width should scale by " + mCompatScale,
                mWindowStateNormal.getRequestedWidth(),
                mCompatScale,
                mWindowStateDownscaled.getRequestedWidth());
        checkScaled(
                "Requested height "
                        + mWindowStateNormal.getRequestedHeight()
                        + " should scale by "
                        + mCompatScale,
                mWindowStateNormal.getRequestedHeight(),
                mCompatScale,
                mWindowStateDownscaled.getRequestedHeight());
    }

    private void test_windowState_inCompatUpscalingMode() {
        // Check the "normal" window's state for disabled compat mode and appropriate global scale.
        collector.checkThat(
                "The Window should not be in the size compat mode",
                mWindowStateNormal.hasCompatScale(),
                is(false));
        collector.checkThat(
                "The window should not be scaled",
                1d,
                closeTo(mWindowStateNormal.getGlobalScale(), EPSILON_GLOBAL_SCALE));

        // Check the "upscaled" window's state for enabled compat mode and appropriate global
        // scale.
        collector.checkThat(
                "The Window should be in the size compat mode",
                mWindowStateUpscaled.hasCompatScale(),
                is(true));
        collector.checkThat(
                "The window should have global scale of " + mCompatScale,
                (double) mCompatScale,
                closeTo(mWindowStateUpscaled.getGlobalScale(), EPSILON_GLOBAL_SCALE));

        // Make sure the frame sizes changed correctly.
        collector.checkThat(
                "Window frame on should not change",
                mWindowStateNormal.getFrame(),
                equalTo(mWindowStateUpscaled.getFrame()));
        checkScaled(
                "Requested width should scale by " + mInvCompatScale,
                mWindowStateNormal.getRequestedWidth(),
                mInvCompatScale,
                mWindowStateUpscaled.getRequestedWidth());
        checkScaled(
                "Requested height should scale by " + mInvCompatScale,
                mWindowStateNormal.getRequestedHeight(),
                mInvCompatScale,
                mWindowStateUpscaled.getRequestedHeight());
    }

    private WindowManagerState.WindowState getPackageWindowState() {
        return getPackageWindowState(PACKAGE_UNDER_TEST);
    }

    private void addSubview(CommandSession.ActivitySession activity, String subviewId) {
        Bundle data = new Bundle();
        data.putString(KEY_SUBVIEW_ID, subviewId);
        Bundle res = activity.sendCommandAndWaitReply(COMMAND_ADD_SUBVIEW, data);
        collector.checkThat(
                "Failed to add subview " + subviewId,
                res.getBoolean(KEY_COMMAND_SUCCESS),
                is(true));
    }

    private Bundle getSubViewSize(CommandSession.ActivitySession activity, String subviewId) {
        Bundle data = new Bundle();
        data.putString(KEY_SUBVIEW_ID, subviewId);
        return activity.sendCommandAndWaitReply(COMMAND_GET_SUBVIEW_SIZE, data);
    }

    private void checkScaled(String message, int baseValue, double expectedScale, int actualValue) {
        // In order to account for possible rounding errors, let's calculate the actual scale and
        // compare it's against the expected scale (allowing a small delta).
        final double actualScale = ((double) actualValue) / baseValue;
        collector.checkThat(message, actualScale, closeTo(expectedScale, EPSILON_GLOBAL_SCALE));
    }
}
