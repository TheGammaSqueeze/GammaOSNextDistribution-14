/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.systemui.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL;
import static android.Manifest.permission.REVOKE_RUNTIME_PERMISSIONS;
import static android.server.wm.BarTestUtils.assumeHasColoredNavigationBar;
import static android.server.wm.BarTestUtils.assumeHasColoredStatusBar;

import static androidx.test.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertTrue;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Insets;
import android.os.Process;
import android.os.SystemClock;
import android.permission.PermissionManager;
import android.permission.cts.PermissionUtils;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.PlatinumTest;
import android.server.wm.IgnoreOrientationRequestSession;
import android.view.Gravity;
import android.view.InputDevice;
import android.view.MotionEvent;
import android.view.WindowInsets.Type;
import android.view.WindowManager;
import android.view.WindowMetrics;

import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.ThrowingRunnable;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;
import org.junit.runner.RunWith;

/**
 * Test for light status bar.
 *
 * atest CtsSystemUiTestCases:LightBarTests
 */
@RunWith(AndroidJUnit4.class)
public class LightBarTests extends LightBarTestBase {

    public static final String TAG = "LightStatusBarTests";

    /**
     * Color may be slightly off-spec when resources are resized for lower densities. Use this error
     * margin to accommodate for that when comparing colors.
     */
    private static final int COLOR_COMPONENT_ERROR_MARGIN = 20;

    /**
     * It's possible for the device to have color sampling enabled in the nav bar -- in that
     * case we need to pick a background color that would result in the same dark icon tint
     * that matches the default visibility flags used when color sampling is not enabled.
     */
    private static final int LIGHT_BG_COLOR = Color.rgb(255, 128, 128);

    private final String NOTIFICATION_TAG = "TEST_TAG";
    private final String NOTIFICATION_CHANNEL_ID = "test_channel";
    private final String NOTIFICATION_GROUP_KEY = "test_group";
    private NotificationManager mNm;
    private IgnoreOrientationRequestSession mOrientationRequestSession;

    @Rule
    public ActivityTestRule<LightBarActivity> mActivityRule = new ActivityTestRule<>(
            LightBarActivity.class);
    @Rule
    public TestName mTestName = new TestName();



    @Before
    public void setUp() {
        // We need to prevent letterboxing because when an activity is letterboxed, then the status
        // bar icons are outside the activity space so our verification will fail. See b/246515090.
        //
        // When ignore_orientation_request is set to true and the device is in landscape but the
        // activity is in portrait, then the device remains in landscape but letterboxes the
        // activity (so the activity is *not* full screen). Setting ignore_orientation_request to
        // false will cause the device to instead rotate to portrait to match the activity, thus
        // preventing letterboxing.
        mOrientationRequestSession = new IgnoreOrientationRequestSession(false /* enable */);
    }

    @After
    public void tearDown() {
        if (mOrientationRequestSession != null) {
            mOrientationRequestSession.close();
        }
    }

    @Test
    @AppModeFull // Instant apps cannot create notifications
    @PlatinumTest(focusArea = "sysui")
    public void testLightStatusBarIcons() throws Throwable {
        assumeHasColoredStatusBar(mActivityRule);

        runInNotificationSession(() -> {
            requestLightBars(LIGHT_BG_COLOR);
            Thread.sleep(WAIT_TIME);

            Bitmap bitmap = takeStatusBarScreenshot(mActivityRule.getActivity());
            Stats s = evaluateLightBarBitmap(bitmap, LIGHT_BG_COLOR, 0);
            assertStats(bitmap, s, true /* light */);
        });
    }

    @Test
    @AppModeFull // Instant apps cannot create notifications
    @PlatinumTest(focusArea = "sysui")
    public void testAppearanceCanOverwriteLegacyFlags() throws Throwable {
        assumeHasColoredStatusBar(mActivityRule);

        runInNotificationSession(() -> {
            final LightBarActivity activity = mActivityRule.getActivity();
            activity.runOnUiThread(() -> {
                activity.getWindow().setStatusBarColor(LIGHT_BG_COLOR);
                activity.getWindow().setNavigationBarColor(LIGHT_BG_COLOR);

                activity.setLightStatusBarLegacy(true);
                activity.setLightNavigationBarLegacy(true);

                // The new appearance APIs can overwrite the appearance specified by the legacy
                // flags.
                activity.setLightStatusBarAppearance(false);
                activity.setLightNavigationBarAppearance(false);
            });
            Thread.sleep(WAIT_TIME);

            Bitmap bitmap = takeStatusBarScreenshot(mActivityRule.getActivity());
            Stats s = evaluateDarkBarBitmap(bitmap, LIGHT_BG_COLOR, 0);
            assertStats(bitmap, s, false /* light */);
        });
    }

    @Test
    @AppModeFull // Instant apps cannot create notifications
    @PlatinumTest(focusArea = "sysui")
    public void testLegacyFlagsCannotOverwriteAppearance() throws Throwable {
        assumeHasColoredStatusBar(mActivityRule);

        runInNotificationSession(() -> {
            final LightBarActivity activity = mActivityRule.getActivity();
            activity.runOnUiThread(() -> {
                activity.getWindow().setStatusBarColor(LIGHT_BG_COLOR);
                activity.getWindow().setNavigationBarColor(LIGHT_BG_COLOR);

                activity.setLightStatusBarAppearance(false);
                activity.setLightNavigationBarAppearance(false);

                // Once the client starts using the new appearance APIs, the legacy flags won't
                // change the appearance anymore.
                activity.setLightStatusBarLegacy(true);
                activity.setLightNavigationBarLegacy(true);
            });
            Thread.sleep(WAIT_TIME);

            Bitmap bitmap = takeStatusBarScreenshot(mActivityRule.getActivity());
            Stats s = evaluateDarkBarBitmap(bitmap, LIGHT_BG_COLOR, 0);
            assertStats(bitmap, s, false /* light */);
        });
    }

    @Test
    public void testLightNavigationBar() throws Throwable {
        assumeHasColoredNavigationBar(mActivityRule);

        requestLightBars(LIGHT_BG_COLOR);
        Thread.sleep(WAIT_TIME);

        // Inject a cancelled interaction with the nav bar to ensure it is at full opacity.
        int x = mActivityRule.getActivity().getWidth() / 2;
        int y = mActivityRule.getActivity().getBottom() + 10;
        injectCanceledTap(x, y);
        Thread.sleep(WAIT_TIME);

        LightBarActivity activity = mActivityRule.getActivity();
        Bitmap bitmap = takeNavigationBarScreenshot(activity);
        Stats s = evaluateLightBarBitmap(bitmap, LIGHT_BG_COLOR, activity.getBottom());
        assertStats(bitmap, s, true /* light */);
    }

    @Test
    public void testNavigationBarDivider() throws Throwable {
        assumeHasColoredNavigationBar(mActivityRule);

        mActivityRule.runOnUiThread(() -> {
            mActivityRule.getActivity().getWindow().setNavigationBarColor(Color.RED);
            mActivityRule.getActivity().getWindow().setNavigationBarDividerColor(Color.WHITE);
        });
        Thread.sleep(WAIT_TIME);

        checkNavigationBarDivider(mActivityRule.getActivity(), Color.WHITE, Color.RED,
                mTestName.getMethodName());
    }

    @Test
    @AppModeFull // Instant apps cannot create notifications
    public void testLightBarIsNotAllowed_fitStatusBar() throws Throwable {
        assumeHasColoredStatusBar(mActivityRule);

        runInNotificationSession(() -> {
            final LightBarActivity activity = mActivityRule.getActivity();
            activity.runOnUiThread(() -> {
                final WindowMetrics metrics = activity.getWindowManager().getCurrentWindowMetrics();
                final Insets insets = metrics.getWindowInsets().getInsets(Type.statusBars());
                final WindowManager.LayoutParams attrs = activity.getWindow().getAttributes();
                attrs.gravity = Gravity.LEFT | Gravity.TOP;
                attrs.x = insets.left;
                attrs.y = insets.top;
                attrs.width = metrics.getBounds().width() - insets.left - insets.right;
                attrs.height = metrics.getBounds().height() - insets.top - insets.bottom;
                activity.getWindow().setAttributes(attrs);
                activity.getWindow().setStatusBarColor(Color.BLACK);
                activity.getWindow().setNavigationBarColor(Color.BLACK);
                activity.setLightStatusBarAppearance(true);
                activity.setLightNavigationBarAppearance(true);
            });
            Thread.sleep(WAIT_TIME);

            Bitmap bitmap = takeStatusBarScreenshot(activity);
            Stats s = evaluateDarkBarBitmap(bitmap, Color.TRANSPARENT, 0);
            assertStats(bitmap, s, false /* light */);
        });
    }

    private void runInNotificationSession(ThrowingRunnable task) throws Exception {
        Context context = getInstrumentation().getContext();
        String packageName = getInstrumentation().getTargetContext().getPackageName();
        try {
            PermissionUtils.grantPermission(packageName, POST_NOTIFICATIONS);
            mNm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
            NotificationChannel channel1 = new NotificationChannel(NOTIFICATION_CHANNEL_ID,
                    NOTIFICATION_CHANNEL_ID, NotificationManager.IMPORTANCE_LOW);
            mNm.createNotificationChannel(channel1);

            // post 10 notifications to ensure enough icons in the status bar
            for (int i = 0; i < 10; i++) {
                Notification.Builder noti1 =
                        new Notification.Builder(context, NOTIFICATION_CHANNEL_ID)
                                .setSmallIcon(R.drawable.ic_save)
                                .setChannelId(NOTIFICATION_CHANNEL_ID)
                                .setPriority(Notification.PRIORITY_LOW)
                                .setGroup(NOTIFICATION_GROUP_KEY);
                mNm.notify(NOTIFICATION_TAG, i, noti1.build());
            }

            task.run();
        } finally {
            mNm.cancelAll();
            mNm.deleteNotificationChannel(NOTIFICATION_CHANNEL_ID);

            // Use test API to prevent PermissionManager from killing the test process when revoking
            // permission.
            SystemUtil.runWithShellPermissionIdentity(
                    () -> context.getSystemService(PermissionManager.class)
                            .revokePostNotificationPermissionWithoutKillForTest(
                                    packageName,
                                    Process.myUserHandle().getIdentifier()),
                    REVOKE_POST_NOTIFICATIONS_WITHOUT_KILL,
                    REVOKE_RUNTIME_PERMISSIONS);
        }
    }

    private void injectCanceledTap(int x, int y) {
        long downTime = SystemClock.uptimeMillis();
        injectEvent(MotionEvent.ACTION_DOWN, x, y, downTime);
        injectEvent(MotionEvent.ACTION_CANCEL, x, y, downTime);
    }

    private void injectEvent(int action, int x, int y, long downTime) {
        final UiAutomation automation = getInstrumentation().getUiAutomation();
        final long eventTime = SystemClock.uptimeMillis();
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action, x, y, 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        assertTrue(automation.injectInputEvent(event, true));
        event.recycle();
    }

    private void assertStats(Bitmap bitmap, Stats s, boolean light) {
        boolean success = false;
        try {
            assumeNavigationBarChangesColor(s.backgroundPixels, s.totalPixels());

            final String spec = light ? "60% black and 24% black" : "100% white and 30% white";
            assertMoreThan("Not enough pixels colored as in the spec", 0.3f,
                    (float) s.iconPixels / (float) s.foregroundPixels(),
                    "Are the bar icons colored according to the spec (" + spec + ")?");

            final String unexpected = light ? "lighter" : "darker";
            final String expected = light ? "dark" : "light";
            final int sameHuePixels = light ? s.sameHueLightPixels : s.sameHueDarkPixels;
            assertLessThan("Too many pixels " + unexpected + " than the background", 0.05f,
                    (float) sameHuePixels / (float) s.foregroundPixels(),
                    "Are the bar icons " + expected + "?");

            assertLessThan("Too many pixels with a changed hue", 0.05f,
                    (float) s.unexpectedHuePixels / (float) s.foregroundPixels(),
                    "Are the bar icons color-free?");

            success = true;
        } finally {
            if (!success) {
                dumpBitmap(bitmap, mTestName.getMethodName());
            }
        }
    }

    private void requestLightBars(final int background) {
        final LightBarActivity activity = mActivityRule.getActivity();
        activity.runOnUiThread(() -> {
            activity.getWindow().setStatusBarColor(background);
            activity.getWindow().setNavigationBarColor(background);
            activity.setLightStatusBarLegacy(true);
            activity.setLightNavigationBarLegacy(true);
        });
    }

    private static class Stats {
        int backgroundPixels;
        int iconPixels;
        int sameHueDarkPixels;
        int sameHueLightPixels;
        int unexpectedHuePixels;

        int totalPixels() {
            return backgroundPixels + iconPixels + sameHueDarkPixels
                    + sameHueLightPixels + unexpectedHuePixels;
        }

        int foregroundPixels() {
            return iconPixels + sameHueDarkPixels
                    + sameHueLightPixels + unexpectedHuePixels;
        }

        @Override
        public String toString() {
            return String.format("{bg=%d, ic=%d, dark=%d, light=%d, bad=%d}",
                    backgroundPixels, iconPixels, sameHueDarkPixels, sameHueLightPixels,
                    unexpectedHuePixels);
        }
    }

    private Stats evaluateLightBarBitmap(Bitmap bitmap, int background, int shiftY) {
        return evaluateBarBitmap(bitmap, background, shiftY, 0x99000000, 0x3d000000);
    }

    private Stats evaluateDarkBarBitmap(Bitmap bitmap, int background, int shiftY) {
        return evaluateBarBitmap(bitmap, background, shiftY, 0xffffffff, 0x4dffffff);
    }

    private Stats evaluateBarBitmap(Bitmap bitmap, int background, int shiftY, int iconColor,
            int iconPartialColor) {

        int mixedIconColor = mixSrcOver(background, iconColor);
        int mixedIconPartialColor = mixSrcOver(background, iconPartialColor);
        float [] hsvMixedIconColor = new float[3];
        float [] hsvMixedPartialColor = new float[3];
        Color.RGBToHSV(Color.red(mixedIconColor), Color.green(mixedIconColor),
                Color.blue(mixedIconColor), hsvMixedIconColor);
        Color.RGBToHSV(Color.red(mixedIconPartialColor), Color.green(mixedIconPartialColor),
                Color.blue(mixedIconPartialColor), hsvMixedPartialColor);

        float maxHsvValue = Math.max(hsvMixedIconColor[2], hsvMixedPartialColor[2]);
        float minHsvValue = Math.min(hsvMixedIconColor[2], hsvMixedPartialColor[2]);

        int[] pixels = new int[bitmap.getHeight() * bitmap.getWidth()];
        bitmap.getPixels(pixels, 0, bitmap.getWidth(), 0, 0, bitmap.getWidth(), bitmap.getHeight());

        Stats s = new Stats();
        float eps = 0.005f;

        loadCutout(mActivityRule.getActivity());
        float [] hsvPixel = new float[3];
        int i = 0;
        for (int c : pixels) {
            int x = i % bitmap.getWidth();
            int y = i / bitmap.getWidth();
            i++;
            if (isInsideCutout(x, shiftY + y)) {
                continue;
            }

            if (isColorSame(c, background)) {
                s.backgroundPixels++;
                continue;
            }

            // What we expect the icons to be colored according to the spec.
            Color.RGBToHSV(Color.red(c), Color.green(c), Color.blue(c), hsvPixel);
            if (isColorSame(c, mixedIconColor) || isColorSame(c, mixedIconPartialColor)
                    || (hsvPixel[2] >= minHsvValue && hsvPixel[2] <= maxHsvValue)) {
                s.iconPixels++;
                continue;
            }

            // Due to anti-aliasing, there will be deviations from the ideal icon color, but it
            // should still be mostly the same hue.
            float hueDiff = Math.abs(ColorUtils.hue(background) - ColorUtils.hue(c));
            if (hueDiff < eps || hueDiff > 1 - eps) {
                // .. it shouldn't be lighter than the original background though.
                if (ColorUtils.brightness(c) > ColorUtils.brightness(background)) {
                    s.sameHueLightPixels++;
                } else {
                    s.sameHueDarkPixels++;
                }
                continue;
            }

            s.unexpectedHuePixels++;
        }

        return s;
    }

    private int mixSrcOver(int background, int foreground) {
        int bgAlpha = Color.alpha(background);
        int bgRed = Color.red(background);
        int bgGreen = Color.green(background);
        int bgBlue = Color.blue(background);

        int fgAlpha = Color.alpha(foreground);
        int fgRed = Color.red(foreground);
        int fgGreen = Color.green(foreground);
        int fgBlue = Color.blue(foreground);

        return Color.argb(fgAlpha + (255 - fgAlpha) * bgAlpha / 255,
                    fgRed + (255 - fgAlpha) * bgRed / 255,
                    fgGreen + (255 - fgAlpha) * bgGreen / 255,
                    fgBlue + (255 - fgAlpha) * bgBlue / 255);
    }

    /**
     * Check if two colors' diff is in the error margin as defined in
     * {@link #COLOR_COMPONENT_ERROR_MARGIN}.
     */
    private boolean isColorSame(int c1, int c2){
        return Math.abs(Color.alpha(c1) - Color.alpha(c2)) < COLOR_COMPONENT_ERROR_MARGIN
                && Math.abs(Color.red(c1) - Color.red(c2)) < COLOR_COMPONENT_ERROR_MARGIN
                && Math.abs(Color.green(c1) - Color.green(c2)) < COLOR_COMPONENT_ERROR_MARGIN
                && Math.abs(Color.blue(c1) - Color.blue(c2)) < COLOR_COMPONENT_ERROR_MARGIN;
    }
}
