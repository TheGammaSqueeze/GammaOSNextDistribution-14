/*
 * Copyright (C) 2017 The Android Open Source Project
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
 * limitations under the License
 */

package android.server.wm.window;

import static android.app.UiModeManager.MODE_NIGHT_AUTO;
import static android.app.UiModeManager.MODE_NIGHT_CUSTOM;
import static android.app.UiModeManager.MODE_NIGHT_NO;
import static android.app.UiModeManager.MODE_NIGHT_YES;
import static android.app.WindowConfiguration.WINDOWING_MODE_FREEFORM;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.content.Intent.ACTION_MAIN;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.server.wm.CliIntentExtra.extraBool;
import static android.server.wm.CliIntentExtra.extraString;
import static android.server.wm.WindowManagerState.STATE_RESUMED;
import static android.server.wm.app.Components.HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY;
import static android.server.wm.app.Components.SPLASHSCREEN_ACTIVITY;
import static android.server.wm.app.Components.SPLASH_SCREEN_REPLACE_ICON_ACTIVITY;
import static android.server.wm.app.Components.SPLASH_SCREEN_REPLACE_THEME_ACTIVITY;
import static android.server.wm.app.Components.SPLASH_SCREEN_STYLE_THEME_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.COMMAND_START_ACTIVITIES;
import static android.server.wm.app.Components.TestActivity.COMMAND_START_ACTIVITY;
import static android.server.wm.app.Components.TestActivity.EXTRA_INTENT;
import static android.server.wm.app.Components.TestActivity.EXTRA_INTENTS;
import static android.server.wm.app.Components.TestActivity.EXTRA_OPTION;
import static android.server.wm.app.Components.TestStartingWindowKeys.CANCEL_HANDLE_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.CENTER_VIEW_IS_SURFACE_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_BRANDING_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.CONTAINS_CENTER_VIEW;
import static android.server.wm.app.Components.TestStartingWindowKeys.DELAY_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.GET_NIGHT_MODE_ACTIVITY_CHANGED;
import static android.server.wm.app.Components.TestStartingWindowKeys.HANDLE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_DURATION;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_ANIMATION_START;
import static android.server.wm.app.Components.TestStartingWindowKeys.ICON_BACKGROUND_COLOR;
import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_COLOR;
import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_COMPONENT;
import static android.server.wm.app.Components.TestStartingWindowKeys.OVERRIDE_THEME_ENABLED;
import static android.server.wm.app.Components.TestStartingWindowKeys.RECEIVE_SPLASH_SCREEN_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REPLACE_ICON_EXIT;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_CREATE;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_HANDLE_EXIT_ON_RESUME;
import static android.server.wm.app.Components.TestStartingWindowKeys.REQUEST_SET_NIGHT_MODE_ON_CREATE;
import static android.server.wm.app.Components.TestStartingWindowKeys.STYLE_THEME_COMPONENT;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.WindowInsets.Type.captionBar;
import static android.view.WindowInsets.Type.systemBars;

import static org.hamcrest.MatcherAssert.assertThat;
import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.lessThanOrEqualTo;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;

import android.app.ActivityOptions;
import android.app.UiModeManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.LauncherApps;
import android.content.pm.ShortcutInfo;
import android.content.pm.ShortcutManager;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.CommandSession;
import android.server.wm.Condition;
import android.server.wm.DumpOnFailure;
import android.server.wm.TestJournalProvider;
import android.server.wm.WindowManagerState;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.view.WindowMetrics;
import android.window.SplashScreen;

import androidx.core.graphics.ColorUtils;

import com.android.compatibility.common.util.TestUtils;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.Collections;
import java.util.function.Consumer;

/**
 * Build/Install/Run:
 * atest CtsWindowManagerDeviceWindow:SplashscreenTests
 */
@Presubmit
@android.server.wm.annotation.Group1
public class SplashscreenTests extends ActivityManagerTestBase {

    private static final int CENTER_ICON_SIZE = 192;
    private static final int BRANDING_HEIGHT = 80;
    private static final int BRANDING_DEFAULT_MARGIN = 60;

    @Rule
    public final DumpOnFailure dumpOnFailure = new DumpOnFailure();

    @Before
    public void setUp() throws Exception {
        super.setUp();
        mWmState.setSanityCheckWithFocusedWindow(false);
        mWmState.waitForDisplayUnfrozen();
    }

    @After
    public void tearDown() {
        mWmState.setSanityCheckWithFocusedWindow(true);
    }

    /**
     * @return The starter activity session to start the test activity
     */
    private CommandSession.ActivitySession prepareTestStarter() {
        return createManagedActivityClientSession()
                .startActivity(getLaunchActivityBuilder().setUseInstrumentation());
    }

    private void startActivitiesFromStarter(CommandSession.ActivitySession starter,
            Intent[] intents, ActivityOptions options) {

        final Bundle data = new Bundle();
        data.putParcelableArray(EXTRA_INTENTS, intents);
        if (options != null) {
            data.putParcelable(EXTRA_OPTION, options.toBundle());
        }
        starter.sendCommand(COMMAND_START_ACTIVITIES, data);
    }

    private void startActivityFromStarter(CommandSession.ActivitySession starter,
            ComponentName componentName, Consumer<Intent> fillExtra, ActivityOptions options) {

        final Bundle data = new Bundle();
        final Intent startIntent = new Intent();
        startIntent.setComponent(componentName);
        startIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        fillExtra.accept(startIntent);
        data.putParcelable(EXTRA_INTENT, startIntent);
        if (options != null) {
            data.putParcelable(EXTRA_OPTION, options.toBundle());
        }
        starter.sendCommand(COMMAND_START_ACTIVITY, data);
    }

    @Test
    public void testSplashscreenContent() {
        // TODO(b/192431448): Allow Automotive to skip this test until Splash Screen is properly
        // applied insets by system bars in AAOS.
        assumeFalse(isCar());
        assumeFalse(isLeanBack());

        final CommandSession.ActivitySession starter = prepareTestStarter();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        noIconOptions.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // launch from app with no-icon options
        startActivityFromStarter(starter, SPLASHSCREEN_ACTIVITY,
                intent -> {}, noIconOptions);
        // The windowSplashScreenContent attribute is set to RED. We check that it is ignored.
        testSplashScreenColor(SPLASHSCREEN_ACTIVITY, Color.BLUE, Color.WHITE);
    }

    @Test
    public void testSplashscreenContent_FreeformWindow() {
        // TODO(b/192431448): Allow Automotive to skip this test until Splash Screen is properly
        // applied insets by system bars in AAOS.
        assumeFalse(isCar());
        assumeTrue(supportsFreeform());

        final CommandSession.ActivitySession starter = prepareTestStarter();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        noIconOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);
        // launch from app with no-icon options
        startActivityFromStarter(starter, SPLASHSCREEN_ACTIVITY,
                intent -> {}, noIconOptions);
        // The windowSplashScreenContent attribute is set to RED. We check that it is ignored.
        testSplashScreenColor(SPLASHSCREEN_ACTIVITY, Color.BLUE, Color.WHITE);
    }

    private void testSplashScreenColor(ComponentName name, int primaryColor, int secondaryColor) {
        // Activity may not be launched yet even if app transition is in idle state.
        mWmState.waitForActivityState(name, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);

        final Bitmap image = takeScreenshot();
        final WindowMetrics windowMetrics = mWm.getMaximumWindowMetrics();
        final Rect stableBounds = new Rect(windowMetrics.getBounds());
        stableBounds.inset(windowMetrics.getWindowInsets().getInsetsIgnoringVisibility(
                systemBars() & ~captionBar()));
        WindowManagerState.WindowState startingWindow = mWmState.findFirstWindowWithType(
                WindowManager.LayoutParams.TYPE_APPLICATION_STARTING);

        Rect startingWindowBounds = startingWindow.getBounds();
        final Rect appBounds;
        if (startingWindowBounds != null) {
            appBounds = new Rect(startingWindowBounds);
        } else {
            appBounds = new Rect(startingWindow.getFrame());
        }

        insetGivenFrame(startingWindow,
                insetsSource -> (insetsSource.is(WindowInsets.Type.captionBar())), appBounds);

        assertFalse("Couldn't find splash screen bounds. Impossible to assert the colors",
                appBounds.isEmpty());

        // Use ratios to flexibly accommodate circular or not quite rectangular displays
        // Note: Color.BLACK is the pixel color outside of the display region

        int px = WindowManagerState.dpToPx(CENTER_ICON_SIZE,
                mContext.getResources().getConfiguration().densityDpi);
        Rect ignoreRect = new Rect(0, 0, px, px);
        ignoreRect.offsetTo(appBounds.centerX() - ignoreRect.width() / 2,
                appBounds.centerY() - ignoreRect.height() / 2);

        appBounds.intersect(stableBounds);
        assertColors(image, appBounds, primaryColor, 0.99f, secondaryColor, 0.02f, ignoreRect);
    }

    // For real devices, gamma correction might be applied on hardware driver, so the colors may
    // not exactly match.
    private static boolean isSimilarColor(int a, int b) {
        if (a == b) {
            return true;
        }
        return Math.abs(Color.alpha(a) - Color.alpha(b)) +
                Math.abs(Color.red(a) - Color.red(b)) +
                Math.abs(Color.green(a) - Color.green(b)) +
                Math.abs(Color.blue(a) - Color.blue(b)) < 10;
    }

    private void assertColors(Bitmap img, Rect bounds, int primaryColor, float expectedPrimaryRatio,
            int secondaryColor, float acceptableWrongRatio, Rect ignoreRect) {

        int primaryPixels = 0;
        int secondaryPixels = 0;
        int wrongPixels = 0;

        assertThat(bounds.top, greaterThanOrEqualTo(0));
        assertThat(bounds.left, greaterThanOrEqualTo(0));
        assertThat(bounds.right, lessThanOrEqualTo(img.getWidth()));
        assertThat(bounds.bottom, lessThanOrEqualTo(img.getHeight()));

        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                if (ignoreRect != null && ignoreRect.contains(x, y)) {
                    continue;
                }
                final int color = img.getPixel(x, y);
                if (isSimilarColor(primaryColor, color)) {
                    primaryPixels++;
                } else if (isSimilarColor(secondaryColor, color)) {
                    secondaryPixels++;
                } else {
                    wrongPixels++;
                }
            }
        }

        int totalPixels = bounds.width() * bounds.height();
        if (ignoreRect != null) {
            totalPixels -= ignoreRect.width() * ignoreRect.height();
        }

        final float primaryRatio = (float) primaryPixels / totalPixels;
        if (primaryRatio < expectedPrimaryRatio) {
            generateFailureImage(img, bounds, primaryColor, secondaryColor, ignoreRect);
            fail("Less than " + (expectedPrimaryRatio * 100.0f)
                    + "% of pixels have non-primary color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels=" + wrongPixels);
        }
        // Some pixels might be covered by screen shape decorations, like rounded corners.
        // On circular displays, there is an antialiased edge.
        final float wrongRatio = (float) wrongPixels / totalPixels;
        if (wrongRatio > acceptableWrongRatio) {
            generateFailureImage(img, bounds, primaryColor, secondaryColor, ignoreRect);
            fail("More than " + (acceptableWrongRatio * 100.0f)
                    + "% of pixels have wrong color primaryPixels=" + primaryPixels
                    + " secondaryPixels=" + secondaryPixels + " wrongPixels="
                    + wrongPixels);
        }
    }

    private void generateFailureImage(Bitmap img, Rect bounds, int primaryColor,
            int secondaryColor, Rect ignoreRect) {

        // Create a bitmap with on the left the original image and on the right the result of the
        // test. The pixel marked in green have the right color, the transparent black one are
        // ignored and the wrong pixels have the original color.
        final int ignoredDebugColor = 0xEE000000;
        final int validDebugColor = 0x6600FF00;
        Bitmap result = Bitmap.createBitmap(img.getWidth() * 2, img.getHeight(),
                Bitmap.Config.ARGB_8888);

        // Execute the exact same logic applied in assertColor() to avoid bugs between the assertion
        // method and the failure method
        for (int x = bounds.left; x < bounds.right; x++) {
            for (int y = bounds.top; y < bounds.bottom; y++) {
                final int pixel = img.getPixel(x, y);
                if (ignoreRect != null && ignoreRect.contains(x, y)) {
                    markDebugPixel(pixel, result, x, y, ignoredDebugColor, 0.95f);
                    continue;
                }
                if (isSimilarColor(primaryColor, pixel)) {
                    markDebugPixel(pixel, result, x, y, validDebugColor, 0.8f);
                } else if (isSimilarColor(secondaryColor, pixel)) {
                    markDebugPixel(pixel, result, x, y, validDebugColor, 0.8f);
                } else {
                    markDebugPixel(pixel, result, x, y, Color.TRANSPARENT, 0.0f);
                }
            }
        }

        // Mark the pixels outside the bounds as ignored
        for (int x = 0; x < img.getWidth(); x++) {
            for (int y = 0; y < img.getHeight(); y++) {
                if (bounds.contains(x, y)) {
                    continue;
                }
                markDebugPixel(img.getPixel(x, y), result, x, y, ignoredDebugColor, 0.95f);
            }
        }
        dumpOnFailure.dumpOnFailure("splashscreen-color-check", result);
    }

    private void markDebugPixel(int pixel, Bitmap result, int x, int y, int color, float ratio) {
        int debugPixel = ColorUtils.blendARGB(pixel, color, ratio);
        result.setPixel(x, y, pixel);
        int debugOffsetX = result.getWidth() / 2;
        result.setPixel(x + debugOffsetX, y, debugPixel);
    }

    // Roughly check whether the height of the window is high enough to display the brand image.
    private boolean canShowBranding() {
        final int iconHeight = WindowManagerState.dpToPx(CENTER_ICON_SIZE,
                mContext.getResources().getConfiguration().densityDpi);
        final int brandingHeight = WindowManagerState.dpToPx(BRANDING_HEIGHT,
                mContext.getResources().getConfiguration().densityDpi);
        final int brandingDefaultMargin = WindowManagerState.dpToPx(BRANDING_DEFAULT_MARGIN,
                mContext.getResources().getConfiguration().densityDpi);
        final WindowMetrics windowMetrics = mWm.getMaximumWindowMetrics();
        final Rect drawableBounds = new Rect(windowMetrics.getBounds());
        final int leftHeight = (drawableBounds.height() - iconHeight) / 2;
        return leftHeight > brandingHeight + brandingDefaultMargin;
    }
    @Test
    public void testHandleExitAnimationOnCreate() throws Exception {
        assumeFalse(isLeanBack());
        launchRuntimeHandleExitAnimationActivity(true, false, false, true);
    }

    @Test
    public void testHandleExitAnimationOnResume() throws Exception {
        assumeFalse(isLeanBack());
        launchRuntimeHandleExitAnimationActivity(false, true, false, true);
    }

    @Test
    public void testHandleExitAnimationCancel() throws Exception {
        assumeFalse(isLeanBack());
        launchRuntimeHandleExitAnimationActivity(true, false, true, false);
    }

    private void launchRuntimeHandleExitAnimationActivity(boolean extraOnCreate,
            boolean extraOnResume, boolean extraCancel, boolean expectResult) throws Exception {
        TestJournalProvider.TestJournalContainer.start();

        launchActivityNoWait(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, extraOnCreate),
                extraBool(REQUEST_HANDLE_EXIT_ON_RESUME, extraOnResume),
                extraBool(CANCEL_HANDLE_EXIT, extraCancel));

        mWmState.computeState(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY);
        mWmState.assertVisibility(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY, true);
        if (expectResult) {
            assertHandleExit(HANDLE_SPLASH_SCREEN_EXIT, true /* containsIcon */,
                    true /* containsBranding */, false /* iconAnimatable */);
        }
    }

    @Test
    public void testSetApplicationNightMode() throws Exception {
        final UiModeManager uiModeManager = mContext.getSystemService(UiModeManager.class);
        assumeTrue(uiModeManager != null);
        final int systemNightMode = uiModeManager.getNightMode();
        final int testNightMode = (systemNightMode == MODE_NIGHT_AUTO
                || systemNightMode == MODE_NIGHT_CUSTOM) ? MODE_NIGHT_YES
                : systemNightMode == MODE_NIGHT_YES ? MODE_NIGHT_NO : MODE_NIGHT_YES;
        final int testConfigNightMode = testNightMode == MODE_NIGHT_YES
                ? Configuration.UI_MODE_NIGHT_YES
                : Configuration.UI_MODE_NIGHT_NO;
        final String nightModeNo = String.valueOf(testNightMode);

        TestJournalProvider.TestJournalContainer.start();
        launchActivity(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY,
                extraString(REQUEST_SET_NIGHT_MODE_ON_CREATE, nightModeNo));
        mWmState.computeState(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY);
        mWmState.assertVisibility(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY, true);
        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(HANDLE_SPLASH_SCREEN_EXIT);
        TestUtils.waitUntil("Waiting for night mode changed", 5 /* timeoutSecond */, () ->
                testConfigNightMode == journal.extras.getInt(GET_NIGHT_MODE_ACTIVITY_CHANGED));
        assertEquals(testConfigNightMode,
                journal.extras.getInt(GET_NIGHT_MODE_ACTIVITY_CHANGED));
    }

    @Test
    public void testSetBackgroundColorActivity() {
        // TODO(b/192431448): Allow Automotive to skip this test until Splash Screen is properly
        // applied insets by system bars in AAOS.
        assumeFalse(isCar());
        assumeFalse(isLeanBack());

        final CommandSession.ActivitySession starter = prepareTestStarter();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        noIconOptions.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);

        // launch from app with no-icon options
        startActivityFromStarter(starter, SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                intent -> intent.putExtra(DELAY_RESUME, true), noIconOptions);

        testSplashScreenColor(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, Color.BLUE, Color.WHITE);
    }

    @Test
    public void testSetBackgroundColorActivity_FreeformWindow() {
        // TODO(b/192431448): Allow Automotive to skip this test until Splash Screen is properly
        // applied insets by system bars in AAOS.
        assumeFalse(isCar());
        assumeTrue(supportsFreeform());

        final CommandSession.ActivitySession starter = prepareTestStarter();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        noIconOptions.setLaunchWindowingMode(WINDOWING_MODE_FREEFORM);

        // launch from app with no-icon options
        startActivityFromStarter(starter, SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                intent -> intent.putExtra(DELAY_RESUME, true), noIconOptions);

        testSplashScreenColor(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, Color.BLUE, Color.WHITE);
    }

    @Test
    public void testHandleExitIconAnimatingActivity() throws Exception {
        assumeFalse(isLeanBack());

        TestJournalProvider.TestJournalContainer.start();
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, true));
        mWmState.computeState(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY);
        mWmState.assertVisibility(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, true);

        assertHandleExit(REPLACE_ICON_EXIT, true /* containsIcon */, false /* containsBranding */,
                true /* iconAnimatable */);
    }

    @Test
    public void testCancelHandleExitIconAnimatingActivity() {
        assumeFalse(isLeanBack());

        TestJournalProvider.TestJournalContainer.start();
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY,
                extraBool(REQUEST_HANDLE_EXIT_ON_CREATE, true),
                extraBool(CANCEL_HANDLE_EXIT, true));

        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);

        final TestJournalProvider.TestJournal journal =
                TestJournalProvider.TestJournalContainer.get(REPLACE_ICON_EXIT);
        assertFalse(journal.extras.getBoolean(RECEIVE_SPLASH_SCREEN_EXIT));
    }

    @Test
    public void testShortcutChangeTheme() {
        // TODO(b/192431448): Allow Automotive to skip this test until Splash Screen is properly
        // applied insets by system bars in AAOS.
        assumeFalse(isCar());
        assumeFalse(isLeanBack());

        final LauncherApps launcherApps = mContext.getSystemService(LauncherApps.class);
        final ShortcutManager shortcutManager = mContext.getSystemService(ShortcutManager.class);
        assumeTrue(launcherApps != null && shortcutManager != null);

        final String shortCutId = "shortcut1";
        final ShortcutInfo.Builder b = new ShortcutInfo.Builder(
                mContext, shortCutId);
        final Intent i = new Intent(ACTION_MAIN)
                .setComponent(SPLASHSCREEN_ACTIVITY);
        final ShortcutInfo shortcut = b.setShortLabel("label")
                .setLongLabel("long label")
                .setIntent(i)
                .setStartingTheme(android.R.style.Theme_Black_NoTitleBar_Fullscreen)
                .build();
        try {
            shortcutManager.addDynamicShortcuts(Collections.singletonList(shortcut));
            runWithShellPermission(() -> launcherApps.startShortcut(shortcut, null, null));
            testSplashScreenColor(SPLASHSCREEN_ACTIVITY, Color.BLACK, Color.WHITE);
        } finally {
            shortcutManager.removeDynamicShortcuts(Collections.singletonList(shortCutId));
        }
    }

    private void waitAndAssertOverrideThemeColor(int expectedColor) {
        waitAndAssertForSelfFinishActivity(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY,
                OVERRIDE_THEME_COMPONENT, OVERRIDE_THEME_COLOR, result -> {
                if (expectedColor > 0) {
                    assertEquals("Override theme color must match",
                            Integer.toHexString(expectedColor),
                            Integer.toHexString(result.getInt(OVERRIDE_THEME_COLOR)));
                }
            });
    }

    @Test
    public void testLaunchWithSolidColorOptions() throws Exception {
        assumeFalse(isLeanBack());
        final CommandSession.ActivitySession starter = prepareTestStarter();
        TestJournalProvider.TestJournalContainer.start();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);
        startActivityFromStarter(starter, SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, intent ->
                intent.putExtra(REQUEST_HANDLE_EXIT_ON_CREATE, true), noIconOptions);
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);

        assertHandleExit(REPLACE_ICON_EXIT, false /* containsIcon */, false /* containsBranding */,
                false /* iconAnimatable */);
    }

    @Test
    public void testLaunchAppWithIconOptions() throws Exception {
        assumeFalse(isLeanBack());
        final Bundle bundle = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON).toBundle();
        TestJournalProvider.TestJournalContainer.start();
        final Intent intent = new Intent(Intent.ACTION_VIEW)
                .setComponent(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY)
                .setFlags(FLAG_ACTIVITY_NEW_TASK);
        intent.putExtra(REQUEST_HANDLE_EXIT_ON_CREATE, true);
        mContext.startActivity(intent, bundle);

        mWmState.waitForActivityState(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);

        assertHandleExit(HANDLE_SPLASH_SCREEN_EXIT, true /* containsIcon */,
                true /* containsBranding */, false /* iconAnimatable */);
    }

    private void launchActivitiesFromStarterWithOptions(Intent[] intents,
            ActivityOptions options, ComponentName waitResumeComponent) {
        assumeFalse(isLeanBack());
        final CommandSession.ActivitySession starter = prepareTestStarter();
        TestJournalProvider.TestJournalContainer.start();

        startActivitiesFromStarter(starter, intents, options);

        mWmState.waitForActivityState(waitResumeComponent, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
    }

    @Test
    public void testLaunchActivitiesWithIconOptions() throws Exception {
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        final Intent[] intents = new Intent[] {
                new Intent().setComponent(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                new Intent().setComponent(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY)
                        .putExtra(REQUEST_HANDLE_EXIT_ON_CREATE, true)
        };
        launchActivitiesFromStarterWithOptions(intents, options,
                SPLASH_SCREEN_REPLACE_ICON_ACTIVITY);
        assertHandleExit(REPLACE_ICON_EXIT, true /* containsIcon */, false /* containsBranding */,
                true /* iconAnimatable */);
    }

    @Test
    public void testLaunchActivitiesWithSolidColorOptions() throws Exception {
        final ActivityOptions options = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);

        final Intent[] intents = new Intent[] {
                new Intent().setComponent(HANDLE_SPLASH_SCREEN_EXIT_ACTIVITY)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(REQUEST_HANDLE_EXIT_ON_CREATE, true),
                new Intent().setComponent(SPLASH_SCREEN_REPLACE_ICON_ACTIVITY)
                        .putExtra(REQUEST_HANDLE_EXIT_ON_CREATE, true)
        };
        launchActivitiesFromStarterWithOptions(intents, options,
                SPLASH_SCREEN_REPLACE_ICON_ACTIVITY);
        assertHandleExit(REPLACE_ICON_EXIT, false /* containsIcon */, false /* containsBranding */,
                false /* iconAnimatable */);
    }

    private void assertHandleExit(String journalOwner,
            boolean containsIcon, boolean containsBranding, boolean iconAnimatable)
            throws Exception {
        final TestJournalProvider.TestJournal journal = TestJournalProvider.TestJournalContainer
                .get(journalOwner);
        TestUtils.waitUntil("Waiting for runtime onSplashScreenExit", 5 /* timeoutSecond */,
                () -> journal.extras.getBoolean(RECEIVE_SPLASH_SCREEN_EXIT));
        assertTrue("No entry for CONTAINS_CENTER_VIEW",
                journal.extras.containsKey(CONTAINS_CENTER_VIEW));
        assertTrue("No entry for CONTAINS_BRANDING_VIEW",
                journal.extras.containsKey(CONTAINS_BRANDING_VIEW));

        final long iconAnimationStart = journal.extras.getLong(ICON_ANIMATION_START);
        final long iconAnimationDuration = journal.extras.getLong(ICON_ANIMATION_DURATION);
        assertEquals(containsIcon, journal.extras.getBoolean(CONTAINS_CENTER_VIEW));
        assertEquals(iconAnimatable, journal.extras.getBoolean(CENTER_VIEW_IS_SURFACE_VIEW));
        assertEquals(iconAnimatable, (iconAnimationStart != 0));
        assertEquals(iconAnimatable ? 500 : 0, iconAnimationDuration);
        if (containsBranding && canShowBranding()) {
            assertEquals(containsBranding, journal.extras.getBoolean(CONTAINS_BRANDING_VIEW));
        }
        if (containsIcon && !iconAnimatable) {
            assertEquals(Color.BLUE, journal.extras.getInt(ICON_BACKGROUND_COLOR, Color.YELLOW));
        } else {
            assertEquals(Color.TRANSPARENT,
                    journal.extras.getInt(ICON_BACKGROUND_COLOR, Color.TRANSPARENT));
        }
    }

    @Test
    public void testOverrideSplashscreenTheme() {
        assumeFalse(isLeanBack());
        // Pre-launch the activity to ensure status is cleared on the device
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY);
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertOverrideThemeColor(0 /* ignore */);

        // Launch the activity a first time, check that the splashscreen use the default theme,
        // and override the theme for the next launch
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY,
                extraBool(OVERRIDE_THEME_ENABLED, true));
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertOverrideThemeColor(Color.BLUE);

        // Launch the activity a second time, check that the theme has been overridden and reset
        // to the default theme
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY);
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertOverrideThemeColor(Color.RED);

        // Launch the activity a third time just to check that the theme has indeed been reset.
        launchActivityNoWait(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY);
        mWmState.waitForActivityState(SPLASH_SCREEN_REPLACE_THEME_ACTIVITY, STATE_RESUMED);
        mWmState.waitForAppTransitionIdleOnDisplay(DEFAULT_DISPLAY);
        waitAndAssertOverrideThemeColor(Color.BLUE);
    }

    private void waitAndAssertForSelfFinishActivity(ComponentName activity, String component,
            String validateKey, Consumer<Bundle> assertConsumer) {
        final Bundle resultExtras = Condition.waitForResult(
                new Condition<Bundle>("splash screen of " + activity)
                        .setResultSupplier(() -> TestJournalProvider.TestJournalContainer.get(
                                component).extras)
                        .setResultValidator(extras -> extras.containsKey(validateKey)));
        if (resultExtras == null) {
            fail("No reported validate key from " + activity);
        }
        assertConsumer.accept(resultExtras);
        mWmState.waitForActivityRemoved(activity);
        separateTestJournal();
    }

    private void waitAndAssertStyleThemeIcon(boolean expectContainIcon) {
        waitAndAssertForSelfFinishActivity(SPLASH_SCREEN_STYLE_THEME_ACTIVITY,
                STYLE_THEME_COMPONENT, CONTAINS_CENTER_VIEW,
                result -> assertEquals("Splash screen style must match",
                        expectContainIcon, result.getBoolean(CONTAINS_CENTER_VIEW)));
    }

    @Test
    public void testDefineSplashScreenStyleFromTheme() {
        assumeFalse(isLeanBack());
        final CommandSession.ActivitySession starter = prepareTestStarter();
        final ActivityOptions noIconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_SOLID_COLOR);

        // launch from app with sold color options
        startActivityFromStarter(starter, SPLASH_SCREEN_STYLE_THEME_ACTIVITY,
                intent -> {}, noIconOptions);
        waitAndAssertStyleThemeIcon(false);

        // launch from app with icon options
        final ActivityOptions iconOptions = ActivityOptions.makeBasic()
                .setSplashScreenStyle(SplashScreen.SPLASH_SCREEN_STYLE_ICON);
        startActivityFromStarter(starter, SPLASH_SCREEN_STYLE_THEME_ACTIVITY,
                intent -> {}, iconOptions);
        waitAndAssertStyleThemeIcon(true);

        // launch from app without activity options
        startActivityFromStarter(starter, SPLASH_SCREEN_STYLE_THEME_ACTIVITY,
                intent -> {}, null /* options */);
        waitAndAssertStyleThemeIcon(true);
    }
}
