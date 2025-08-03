/*
 * Copyright (C) 2008 The Android Open Source Project
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

package android.widget.cts;

import static android.Manifest.permission.POST_NOTIFICATIONS;
import static android.Manifest.permission.UNLIMITED_TOASTS;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import static java.util.stream.Collectors.toList;

import android.app.ActivityOptions;
import android.app.NotificationManager;
import android.app.UiAutomation;
import android.app.UiAutomation.AccessibilityEventFilter;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.graphics.drawable.Drawable;
import android.os.ConditionVariable;
import android.os.SystemClock;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.annotation.UiThreadTest;
import androidx.test.filters.LargeTest;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.ApiTest;
import com.android.compatibility.common.util.PollingCheck;
import com.android.compatibility.common.util.SystemUtil;
import com.android.compatibility.common.util.TestUtils;
import com.android.compatibility.common.util.UserHelper;

import junit.framework.Assert;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

@LargeTest
@RunWith(AndroidJUnit4.class)
public class ToastTest {
    private static final String TAG = ToastTest.class.getSimpleName();
    private static final String TEST_TOAST_TEXT = "test toast";
    private static final String TEST_CUSTOM_TOAST_TEXT = "test custom toast";
    private static final String SETTINGS_ACCESSIBILITY_UI_TIMEOUT =
            "accessibility_non_interactive_ui_timeout_ms";
    private static final int ACCESSIBILITY_STATE_WAIT_TIMEOUT_MS = 3000;
    private static final long TIME_FOR_UI_OPERATION  = 1000L;
    private static final long TIME_OUT = 5000L;
    private static final int MAX_PACKAGE_TOASTS_LIMIT = 5;
    private static final String ACTION_TRANSLUCENT_ACTIVITY_RESUMED =
            "android.widget.cts.app.TRANSLUCENT_ACTIVITY_RESUMED";
    private static final ComponentName COMPONENT_CTS_ACTIVITY =
            ComponentName.unflattenFromString("android.widget.cts/.CtsActivity");
    private static final ComponentName COMPONENT_TRANSLUCENT_ACTIVITY =
            ComponentName.unflattenFromString("android.widget.cts.app/.TranslucentActivity");
    private static final double TOAST_DURATION_ERROR_TOLERANCE_FRACTION = 0.25;

    // The following two fields work together to define rate limits for toasts, where each limit is
    // defined as TOAST_RATE_LIMITS[i] toasts are allowed in the window of length
    // TOAST_WINDOW_SIZES_MS[i].
    private static final int[] TOAST_RATE_LIMITS = {3, 5, 6};
    private static final long[] TOAST_WINDOW_SIZES_MS = {20_000, 42_000, 68_000};

    private Toast mToast;
    private Context mContext;
    private boolean mLayoutDone;
    private ViewTreeObserver.OnGlobalLayoutListener mLayoutListener;
    private ConditionVariable mToastShown;
    private ConditionVariable mToastHidden;
    private NotificationManager mNotificationManager;
    private UserHelper mUserHelper;

    @Rule
    public ActivityTestRule<CtsActivity> mActivityRule =
            new ActivityTestRule<>(CtsActivity.class);
    private UiAutomation mUiAutomation;

    @Before
    public void setup() {
        mContext = getInstrumentation().getContext();
        mUserHelper = new UserHelper(mContext);
        mUiAutomation = getInstrumentation().getUiAutomation();
        mLayoutListener = () -> mLayoutDone = true;
        mNotificationManager =
                mContext.getSystemService(NotificationManager.class);
        // disable rate limiting for tests
        SystemUtil.runWithShellPermissionIdentity(() -> mNotificationManager
                .setToastRateLimitingEnabled(false));

        // Wait until the activity is resumed. Otherwise, custom toasts from non-resumed activity
        // may be blocked depending on the timing.
        PollingCheck.waitFor(TIME_OUT, () -> null != mActivityRule.getActivity());
        try {
            assertTrue("Timeout while waiting for onResume()",
                    mActivityRule.getActivity().waitUntilResumed(TIME_OUT));
        } catch (InterruptedException e) {
            fail("Got InterruptedException while waiting for onResume()");
        }
    }

    @After
    public void teardown() {
        waitForToastToExpire();
        // re-enable rate limiting
        SystemUtil.runWithShellPermissionIdentity(() -> mNotificationManager
                .setToastRateLimitingEnabled(true));
    }

    @UiThreadTest
    @Test
    public void testConstructor() {
        new Toast(mContext);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testConstructorNullContext() {
        new Toast(null);
    }

    private static void assertCustomToastShown(final View view) {
        PollingCheck.waitFor(TIME_OUT, () -> null != view.getParent());
    }

    private static void assertCustomToastShown(CustomToastInfo customToastInfo) {
        PollingCheck.waitFor(TIME_OUT, customToastInfo::isShowing);
    }

    private static void assertCustomToastHidden(CustomToastInfo customToastInfo) {
        PollingCheck.waitFor(TIME_OUT, () -> !customToastInfo.isShowing());
    }

    private static void assertCustomToastShownAndHidden(final View view) {
        assertCustomToastShown(view);
        PollingCheck.waitFor(TIME_OUT, () -> null == view.getParent());
    }

    private static void assertCustomToastShownAndHidden(CustomToastInfo customToastInfo) {
        assertCustomToastShown(customToastInfo);
        assertCustomToastHidden(customToastInfo);
    }

    private void assertTextToastShownAndHidden() {
        assertTrue(mToastShown.block(TIME_OUT));
        assertTrue(mToastHidden.block(TIME_OUT));
    }

    private void assertTextToastShownAndHidden(TextToastInfo textToastInfo) {
        assertTrue(textToastInfo.blockOnToastShown(TIME_OUT));
        assertTrue(textToastInfo.blockOnToastHidden(TIME_OUT));
    }

    private static void assertCustomToastNotShown(final View view) {
        // sleep a while and then make sure do not show toast
        SystemClock.sleep(TIME_FOR_UI_OPERATION);
        assertNull(view.getParent());
    }

    private static void assertCustomToastNotShown(CustomToastInfo customToastInfo) {
        assertThat(customToastInfo.isShowing()).isFalse();

        // sleep a while and then make sure it's still not shown
        SystemClock.sleep(TIME_FOR_UI_OPERATION);
        assertThat(customToastInfo.isShowing()).isFalse();
    }

    private void assertTextToastNotShown(TextToastInfo textToastInfo) {
        assertFalse(textToastInfo.blockOnToastShown(TIME_FOR_UI_OPERATION));
    }

    private void registerLayoutListener(final View view) {
        mLayoutDone = false;
        view.getViewTreeObserver().addOnGlobalLayoutListener(mLayoutListener);
    }

    private void assertLayoutDone(final View view) {
        PollingCheck.waitFor(TIME_OUT, () -> mLayoutDone);
        view.getViewTreeObserver().removeOnGlobalLayoutListener(mLayoutListener);
    }

    private void makeTextToast() throws Throwable {
        makeTextToast(mContext);
    }

    private void makeTextToast(Context context) throws Throwable {
        Log.d(TAG, "creating Toast on context " + context + " (user=" + context.getUserId()
                + ", display=" + context.getDisplayId() + ")");
        mToastShown = new ConditionVariable(false);
        mToastHidden = new ConditionVariable(false);
        mActivityRule.runOnUiThread(
                () -> {
                    mToast = Toast.makeText(context, TEST_TOAST_TEXT, Toast.LENGTH_LONG);
                    mToast.addCallback(new ConditionCallback(mToastShown, mToastHidden));
                });
    }

    private void makeCustomToast() throws Throwable {
        mActivityRule.runOnUiThread(
                () -> {
                    mToast = new Toast(mContext);
                    mToast.setDuration(Toast.LENGTH_LONG);
                    TextView view = new TextView(mContext);
                    view.setText(TEST_CUSTOM_TOAST_TEXT);
                    mToast.setView(view);
                }
        );
    }

    private void waitForToastToExpire() {
        if (mToast == null) {
            return;
        }
        // text toast case
        if (mToastShown != null && mToastHidden != null) {
            boolean toastShown = mToastShown.block(/* return immediately */ 1);
            boolean toastHidden = mToastHidden.block(/* return immediately */ 1);

            if (toastShown && !toastHidden) {
                assertTrue(mToastHidden.block(TIME_OUT));
            }
            return;
        }

        // custom toast case
        View view = mToast.getView();
        if (view != null && view.getParent() != null) {
            PollingCheck.waitFor(TIME_OUT, () -> view.getParent() == null);
        }
    }

    @Test
    public void testShow_whenCustomToast() throws Throwable {
        makeCustomToast();

        final View view = mToast.getView();

        // view has not been attached to screen yet
        assertNull(view.getParent());
        assertEquals(View.VISIBLE, view.getVisibility());

        runOnMainAndDrawSync(view, () -> showToastWithNotificationPermission(mToast));

        // view will be attached to screen when show it
        assertEquals(View.VISIBLE, view.getVisibility());
        assertCustomToastShownAndHidden(view);
    }

    @Test
    @ApiTest(apis = {"android.widget.Toast#show"})
    public void testShow_whenTextToast() throws Throwable {
        makeTextToast();

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTextToastShownAndHidden();
    }

    @Test
    @ApiTest(apis = {"android.widget.Toast#show"})
    public void testShow_activityContext_whenTextToast() throws Throwable {
        makeTextToast(mActivityRule.getActivity());

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTextToastShownAndHidden();
    }

    @Test
    @ApiTest(apis = {"android.widget.Toast#show"})
    public void testShow_appContext_whenTextToast() throws Throwable {
        makeTextToast(mContext.getApplicationContext());

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTextToastShownAndHidden();
    }

    @Test
    public void testHideTextToastAfterExpirationOfFirstShowCall_despiteRepeatedShowCalls()
            throws Throwable {
        // Measure the length of a long toast.
        makeTextToast();
        long start1 = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());
        assertTextToastShownAndHidden();
        long longDurationMs = SystemClock.uptimeMillis() - start1;

        // Call show in the middle of the toast duration.
        makeTextToast();
        long start2 = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());
        SystemClock.sleep(longDurationMs / 2);
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertTextToastShownAndHidden();
        long repeatCallDurationMs = SystemClock.uptimeMillis() - start2;

        // Assert duration was roughly the same despite a repeat call.
        assertThat((double) repeatCallDurationMs)
                .isWithin(longDurationMs * TOAST_DURATION_ERROR_TOLERANCE_FRACTION)
                .of(longDurationMs);
    }

    @Test
    public void testHideCustomToastAfterExpirationOfFirstShowCall_despiteRepeatedShowCalls()
            throws Throwable {
        // Measure the length of a long toast.
        makeCustomToast();
        long start1 = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());
        assertCustomToastShownAndHidden(mToast.getView());
        long longDurationMs = SystemClock.uptimeMillis() - start1;

        // Call show in the middle of the toast duration.
        makeCustomToast();
        long start2 = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());
        SystemClock.sleep(longDurationMs / 2);
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertCustomToastShownAndHidden(mToast.getView());
        long repeatCallDurationMs = SystemClock.uptimeMillis() - start2;

        // Assert duration was roughly the same despite a repeat call.
        assertThat((double) repeatCallDurationMs)
                .isWithin(longDurationMs * TOAST_DURATION_ERROR_TOLERANCE_FRACTION)
                .of(longDurationMs);
    }

    @UiThreadTest
    @Test(expected=RuntimeException.class)
    public void testShowFailure() {
        Toast toast = new Toast(mContext);
        // do not have any views or text
        assertNull(toast.getView());
        showToastWithNotificationPermission(mToast);
    }

    @Test
    public void testCancel_whenCustomToast() throws Throwable {
        makeCustomToast();

        final View view = mToast.getView();

        // view has not been attached to screen yet
        assertNull(view.getParent());
        mActivityRule.runOnUiThread(() -> {
            showToastWithNotificationPermission(mToast);
            mToast.cancel();
        });

        assertCustomToastNotShown(view);
    }

    @Test
    public void testAccessView_whenCustomToast() throws Throwable {
        makeTextToast();
        assertFalse(mToast.getView() instanceof ImageView);

        final ImageView imageView = new ImageView(mContext);
        Drawable drawable = mContext.getResources().getDrawable(R.drawable.pass);
        imageView.setImageDrawable(drawable);

        runOnMainAndDrawSync(imageView, () -> {
            mToast.setView(imageView);
            showToastWithNotificationPermission(mToast);
        });
        assertSame(imageView, mToast.getView());
        assertCustomToastShownAndHidden(imageView);
    }

    @Test
    public void testAccessDuration_whenCustomToast() throws Throwable {
        long start = SystemClock.uptimeMillis();
        makeCustomToast();
        runOnMainAndDrawSync(mToast.getView(),
                () -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());

        View view = mToast.getView();
        assertCustomToastShownAndHidden(view);
        long longDuration = SystemClock.uptimeMillis() - start;

        start = SystemClock.uptimeMillis();
        runOnMainAndDrawSync(mToast.getView(), () -> {
            mToast.setDuration(Toast.LENGTH_SHORT);
            showToastWithNotificationPermission(mToast);
        });
        assertEquals(Toast.LENGTH_SHORT, mToast.getDuration());

        view = mToast.getView();
        assertCustomToastShownAndHidden(view);
        long shortDuration = SystemClock.uptimeMillis() - start;

        assertTrue(longDuration > shortDuration);
    }

    @Test
    public void testAccessDuration_whenTextToast() throws Throwable {
        long start = SystemClock.uptimeMillis();
        makeTextToast();
        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));
        assertEquals(Toast.LENGTH_LONG, mToast.getDuration());

        assertTextToastShownAndHidden();
        long longDuration = SystemClock.uptimeMillis() - start;

        start = SystemClock.uptimeMillis();
        makeTextToast();
        mActivityRule.runOnUiThread(() -> {
            mToast.setDuration(Toast.LENGTH_SHORT);
            showToastWithNotificationPermission(mToast);
        });
        assertEquals(Toast.LENGTH_SHORT, mToast.getDuration());

        assertTextToastShownAndHidden();
        long shortDuration = SystemClock.uptimeMillis() - start;

        assertTrue(longDuration > shortDuration);
    }

    // TODO(b/255426725): remove method, callers, and mUserHelper when A11Y supports it
    private void requireRunNotOnVisibleBackgroundNonProfileUser() {
        assumeFalse("Not supported on visible background user",
                mUserHelper.isVisibleBackgroundUser());
    }

    @Test
    public void testAccessDuration_whenCustomToastAndWithA11yTimeoutEnabled() throws Throwable {
        requireRunNotOnVisibleBackgroundNonProfileUser();
        makeCustomToast();
        final Runnable showToast = () -> {
            mToast.setDuration(Toast.LENGTH_SHORT);
            showToastWithNotificationPermission(mToast);
        };
        long start = SystemClock.uptimeMillis();
        runOnMainAndDrawSync(mToast.getView(), showToast);
        assertCustomToastShownAndHidden(mToast.getView());
        final long shortDuration = SystemClock.uptimeMillis() - start;

        final String originalSetting = Settings.Secure.getString(mContext.getContentResolver(),
                SETTINGS_ACCESSIBILITY_UI_TIMEOUT);
        try {
            final int a11ySettingDuration = (int) shortDuration + 1000;
            putSecureSetting(SETTINGS_ACCESSIBILITY_UI_TIMEOUT,
                    Integer.toString(a11ySettingDuration));
            waitForA11yRecommendedTimeoutChanged(mContext,
                    ACCESSIBILITY_STATE_WAIT_TIMEOUT_MS, a11ySettingDuration);
            start = SystemClock.uptimeMillis();
            runOnMainAndDrawSync(mToast.getView(), showToast);
            assertCustomToastShownAndHidden(mToast.getView());
            final long a11yDuration = SystemClock.uptimeMillis() - start;
            assertTrue("Toast duration " + a11yDuration + "ms < A11y setting " + a11ySettingDuration
                    + "ms", a11yDuration >= a11ySettingDuration);
        } finally {
            putSecureSetting(SETTINGS_ACCESSIBILITY_UI_TIMEOUT, originalSetting);
        }
    }

    @Test
    public void testAccessDuration_whenTextToastAndWithA11yTimeoutEnabled() throws Throwable {
        requireRunNotOnVisibleBackgroundNonProfileUser();
        makeTextToast();
        final Runnable showToast = () -> {
            mToast.setDuration(Toast.LENGTH_SHORT);
            showToastWithNotificationPermission(mToast);
        };
        long start = SystemClock.uptimeMillis();
        mActivityRule.runOnUiThread(showToast);
        assertTextToastShownAndHidden();
        final long shortDuration = SystemClock.uptimeMillis() - start;

        final String originalSetting = Settings.Secure.getString(mContext.getContentResolver(),
                SETTINGS_ACCESSIBILITY_UI_TIMEOUT);
        try {
            final int a11ySettingDuration = (int) shortDuration + 1000;
            putSecureSetting(SETTINGS_ACCESSIBILITY_UI_TIMEOUT,
                    Integer.toString(a11ySettingDuration));
            waitForA11yRecommendedTimeoutChanged(mContext,
                    ACCESSIBILITY_STATE_WAIT_TIMEOUT_MS, a11ySettingDuration);
            makeTextToast();
            start = SystemClock.uptimeMillis();
            mActivityRule.runOnUiThread(showToast);
            assertTextToastShownAndHidden();
            final long a11yDuration = SystemClock.uptimeMillis() - start;
            assertTrue("Toast duration " + a11yDuration + "ms < A11y setting " + a11ySettingDuration
                    + "ms", a11yDuration >= a11ySettingDuration);
        } finally {
            putSecureSetting(SETTINGS_ACCESSIBILITY_UI_TIMEOUT, originalSetting);
        }
    }

    /**
     * Wait for accessibility recommended timeout changed and equals to expected timeout.
     *
     * @param expectedTimeoutMs expected recommended timeout
     */
    private void waitForA11yRecommendedTimeoutChanged(Context context,
            long waitTimeoutMs, int expectedTimeoutMs) {
        final AccessibilityManager manager =
                (AccessibilityManager) context.getSystemService(Context.ACCESSIBILITY_SERVICE);
        final Object lock = new Object();
        AccessibilityManager.AccessibilityServicesStateChangeListener listener = (m) -> {
            synchronized (lock) {
                lock.notifyAll();
            }
        };
        manager.addAccessibilityServicesStateChangeListener(listener);
        try {
            TestUtils.waitOn(lock,
                    () -> manager.getRecommendedTimeoutMillis(0,
                            AccessibilityManager.FLAG_CONTENT_TEXT) == expectedTimeoutMs,
                    waitTimeoutMs,
                    "Wait for accessibility recommended timeout changed");
        } finally {
            manager.removeAccessibilityServicesStateChangeListener(listener);
        }
    }

    private void putSecureSetting(String name, String value) {
        final StringBuilder cmd = new StringBuilder("settings put secure ")
                .append(name).append(" ")
                .append(value);
        SystemUtil.runShellCommand(cmd.toString());
    }

    @Test
    public void testAccessMargin_whenCustomToast() throws Throwable {
        assumeFalse("Skipping test: Auto does not support toast with margin", isCar());

        makeCustomToast();
        View view = mToast.getView();
        assertFalse(view.getLayoutParams() instanceof WindowManager.LayoutParams);

        final float horizontal1 = 1.0f;
        final float vertical1 = 1.0f;
        runOnMainAndDrawSync(view, () -> {
            mToast.setMargin(horizontal1, vertical1);
            showToastWithNotificationPermission(mToast);
            registerLayoutListener(mToast.getView());
        });
        assertCustomToastShown(view);

        assertEquals(horizontal1, mToast.getHorizontalMargin(), 0.0f);
        assertEquals(vertical1, mToast.getVerticalMargin(), 0.0f);
        WindowManager.LayoutParams params1 = (WindowManager.LayoutParams) view.getLayoutParams();
        assertEquals(horizontal1, params1.horizontalMargin, 0.0f);
        assertEquals(vertical1, params1.verticalMargin, 0.0f);
        assertLayoutDone(view);

        int[] xy1 = new int[2];
        view.getLocationOnScreen(xy1);
        assertCustomToastShownAndHidden(view);

        final float horizontal2 = 0.1f;
        final float vertical2 = 0.1f;
        runOnMainAndDrawSync(view, () -> {
            mToast.setMargin(horizontal2, vertical2);
            showToastWithNotificationPermission(mToast);
            registerLayoutListener(mToast.getView());
        });
        assertCustomToastShown(view);

        assertEquals(horizontal2, mToast.getHorizontalMargin(), 0.0f);
        assertEquals(vertical2, mToast.getVerticalMargin(), 0.0f);
        WindowManager.LayoutParams params2 = (WindowManager.LayoutParams) view.getLayoutParams();
        assertEquals(horizontal2, params2.horizontalMargin, 0.0f);
        assertEquals(vertical2, params2.verticalMargin, 0.0f);

        assertLayoutDone(view);
        int[] xy2 = new int[2];
        view.getLocationOnScreen(xy2);
        assertCustomToastShownAndHidden(view);

        /** Check if the test is being run on a watch.
         *
         * Change I8180e5080e0a6860b40dbb2faa791f0ede926ca7 updated how toast are displayed on the
         * watch. Unlike the phone, which displays toast centered horizontally at the bottom of the
         * screen, the watch now displays toast in the center of the screen.
         */
        if (Gravity.CENTER == mToast.getGravity()) {
            assertTrue(xy1[0] > xy2[0]);
            assertTrue(xy1[1] > xy2[1]);
        } else {
            assertTrue(xy1[0] > xy2[0]);
            assertTrue(xy1[1] < xy2[1]);
        }
    }

    @Test
    public void testAccessGravity_whenCustomToast() throws Throwable {
        assumeFalse("Skipping test: Auto does not support toast with gravity", isCar());

        makeCustomToast();
        runOnMainAndDrawSync(mToast.getView(), () -> {
            mToast.setGravity(Gravity.CENTER, 0, 0);
            showToastWithNotificationPermission(mToast);
            registerLayoutListener(mToast.getView());
        });
        View view = mToast.getView();
        assertCustomToastShown(view);
        assertEquals(Gravity.CENTER, mToast.getGravity());
        assertEquals(0, mToast.getXOffset());
        assertEquals(0, mToast.getYOffset());
        assertLayoutDone(view);
        int[] centerXY = new int[2];
        view.getLocationOnScreen(centerXY);
        assertCustomToastShownAndHidden(view);

        runOnMainAndDrawSync(mToast.getView(), () -> {
            mToast.setGravity(Gravity.BOTTOM, 0, 0);
            showToastWithNotificationPermission(mToast);
            registerLayoutListener(mToast.getView());
        });
        view = mToast.getView();
        assertCustomToastShown(view);
        assertEquals(Gravity.BOTTOM, mToast.getGravity());
        assertEquals(0, mToast.getXOffset());
        assertEquals(0, mToast.getYOffset());
        assertLayoutDone(view);
        int[] bottomXY = new int[2];
        view.getLocationOnScreen(bottomXY);
        assertCustomToastShownAndHidden(view);

        // x coordinate is the same
        assertEquals(centerXY[0], bottomXY[0]);
        // bottom view is below of center view
        assertTrue(centerXY[1] < bottomXY[1]);

        final int xOffset = 20;
        final int yOffset = 10;
        runOnMainAndDrawSync(mToast.getView(), () -> {
            mToast.setGravity(Gravity.BOTTOM, xOffset, yOffset);
            mToast.show();
            registerLayoutListener(mToast.getView());
        });
        view = mToast.getView();
        assertCustomToastShown(view);
        assertEquals(Gravity.BOTTOM, mToast.getGravity());
        assertEquals(xOffset, mToast.getXOffset());
        assertEquals(yOffset, mToast.getYOffset());
        assertLayoutDone(view);
        int[] bottomOffsetXY = new int[2];
        view.getLocationOnScreen(bottomOffsetXY);
        assertCustomToastShownAndHidden(view);

        assertEquals(bottomXY[0] + xOffset, bottomOffsetXY[0]);
        assertEquals(bottomXY[1] - yOffset, bottomOffsetXY[1]);
    }

    @UiThreadTest
    @Test
    public void testMakeTextFromString() {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        Toast toast = Toast.makeText(mContext, "android", Toast.LENGTH_SHORT);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_SHORT, toast.getDuration());
        View view = toast.getView();
        assertNull(view);

        toast = Toast.makeText(mContext, "cts", Toast.LENGTH_LONG);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        view = toast.getView();
        assertNull(view);

        toast = Toast.makeText(mContext, null, Toast.LENGTH_LONG);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        view = toast.getView();
        assertNull(view);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testMakeTextFromStringNullContext() {
        Toast.makeText(null, "test", Toast.LENGTH_LONG);
    }

    @UiThreadTest
    @Test
    public void testMakeTextFromResource() {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        Toast toast = Toast.makeText(mContext, R.string.hello_world, Toast.LENGTH_LONG);

        assertNotNull(toast);
        assertEquals(Toast.LENGTH_LONG, toast.getDuration());
        View view = toast.getView();
        assertNull(view);

        toast = Toast.makeText(mContext, R.string.hello_android, Toast.LENGTH_SHORT);
        assertNotNull(toast);
        assertEquals(Toast.LENGTH_SHORT, toast.getDuration());
        view = toast.getView();
        assertNull(view);
    }

    @UiThreadTest
    @Test(expected=NullPointerException.class)
    public void testMakeTextFromResourceNullContext() {
        Toast.makeText(null, R.string.hello_android, Toast.LENGTH_SHORT);
    }

    @UiThreadTest
    @Test
    public void testSetTextFromResource() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);

        toast.setText(R.string.hello_world);
        // TODO: how to getText to assert?

        toast.setText(R.string.hello_android);
        // TODO: how to getText to assert?
    }

    @UiThreadTest
    @Test(expected=RuntimeException.class)
    public void testSetTextFromInvalidResource() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);
        toast.setText(-1);
    }

    @UiThreadTest
    @Test
    public void testSetTextFromString() {
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);

        toast.setText("cts");
        // TODO: how to getText to assert?

        toast.setText("android");
        // TODO: how to getText to assert?
    }

    @UiThreadTest
    @Test(expected = IllegalStateException.class)
    public void testSetTextFromStringNonNullView() {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        Toast toast = Toast.makeText(mContext, R.string.text, Toast.LENGTH_LONG);
        toast.setView(new TextView(mContext));
        toast.setText(null);
    }

    @Test
    public void testRemovedCallbackIsNotCalled() throws Throwable {
        CompletableFuture<Void> toastShown = new CompletableFuture<>();
        CompletableFuture<Void> toastHidden = new CompletableFuture<>();
        Toast.Callback testCallback = new Toast.Callback() {
            @Override
            public void onToastShown() {
                toastShown.complete(null);
            }
            @Override
            public void onToastHidden() {
                toastHidden.complete(null);
            }
        };
        mToastShown = new ConditionVariable(false);
        mToastHidden = new ConditionVariable(false);
        mActivityRule.runOnUiThread(
                () -> {
                    mToast = Toast.makeText(mContext, TEST_TOAST_TEXT, Toast.LENGTH_LONG);
                    mToast.addCallback(testCallback);
                    mToast.addCallback(new ConditionCallback(mToastShown, mToastHidden));
                    mToast.removeCallback(testCallback);
                });

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTextToastShownAndHidden();
        assertFalse(toastShown.isDone());
        assertFalse(toastHidden.isDone());
    }

    @Test(expected = NullPointerException.class)
    public void testAddCallback_whenNull_throws() throws Throwable {
        makeTextToast();
        mToast.addCallback(null);
    }

    @Test
    public void testCallback_whenTextToast_isCalled() throws Throwable {
        ConditionVariable toastShown = new ConditionVariable(false);
        ConditionVariable toastHidden = new ConditionVariable(false);
        mActivityRule.runOnUiThread(
                () -> {
                    mToast = Toast.makeText(mContext, TEST_TOAST_TEXT, Toast.LENGTH_LONG);
                    mToast.addCallback(new ConditionCallback(toastShown, toastHidden));
                });

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTrue(toastShown.block(TIME_OUT));
        assertTrue(toastHidden.block(TIME_OUT));
    }

    @Test
    public void testCallback_whenCustomToast_isCalled() throws Throwable {
        makeCustomToast();
        ConditionVariable toastShown = new ConditionVariable(false);
        ConditionVariable toastHidden = new ConditionVariable(false);
        mActivityRule.runOnUiThread(
                () -> mToast.addCallback(new ConditionCallback(toastShown, toastHidden)));

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertTrue(toastShown.block(TIME_OUT));
        assertTrue(toastHidden.block(TIME_OUT));
    }

    @Test
    public void testTextToastAllowed_whenInTheForeground()
            throws Throwable {
        makeTextToast();

        mActivityRule.runOnUiThread(
                () -> showToastWithoutNotificationPermission(mToast)
        );

        assertTextToastShownAndHidden();
    }

    @Test
    public void testCustomToastAllowed_whenInTheForeground() throws Throwable {
        makeCustomToast();
        View view = mToast.getView();
        // View has not been attached to screen yet
        assertNull(view.getParent());

        mActivityRule.runOnUiThread(
                () -> showToastWithoutNotificationPermission(mToast)
        );

        assertCustomToastShownAndHidden(view);
    }

    @Test
    public void testTextToastAllowed_whenInTheBackground_withNotificationPermission()
            throws Throwable {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        // Make it background
        mActivityRule.finishActivity();
        makeTextToast();

        mActivityRule.runOnUiThread(
                () -> showToastWithNotificationPermission(mToast)
        );
        assertTextToastShownAndHidden();
    }

    @Test
    public void testTextToastNotAllowed_whenInTheBackground_withoutNotificationPermission()
            throws Throwable {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());

        // Make it background
        mActivityRule.finishActivity();
        // may take time for the app process importance to get downgraded from foreground:
        SystemClock.sleep(TIME_FOR_UI_OPERATION);

        List<TextToastInfo> toastInfoList = createTextToasts(1, "Text", Toast.LENGTH_SHORT);

        mActivityRule.runOnUiThread(
                () -> showToastWithoutNotificationPermission(toastInfoList.get(0).getToast())
        );

        assertTextToastNotShown(toastInfoList.get(0));
    }

    @Test
    public void testCustomToastBlocked_whenInTheBackground() throws Throwable {
        // Make it background
        mActivityRule.finishActivity();
        makeCustomToast();
        View view = mToast.getView();
        // View has not been attached to screen yet
        assertNull(view.getParent());

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertCustomToastNotShown(view);
    }

    @Test
    public void testCustomToastBlocked_whenBehindTranslucentActivity() throws Throwable {
        ConditionVariable activityStarted = registerBlockingReceiver(
                ACTION_TRANSLUCENT_ACTIVITY_RESUMED);
        Intent intent = new Intent();
        intent.setComponent(COMPONENT_TRANSLUCENT_ACTIVITY);
        // Launch the translucent activity in fullscreen to ensure the test activity won't resume
        // even on the freeform-first multi-window device.
        final ActivityOptions options = ActivityOptions.makeBasic();
        options.setLaunchWindowingMode(WINDOWING_MODE_FULLSCREEN);
        mActivityRule.getActivity().startActivity(intent, options.toBundle());
        activityStarted.block();
        makeCustomToast();
        View view = mToast.getView();

        mActivityRule.runOnUiThread(() -> showToastWithNotificationPermission(mToast));

        assertCustomToastNotShown(view);

        // Start CtsActivity with CLEAR_TOP flag to finish the TranslucentActivity on top.
        intent = new Intent();
        intent.setComponent(COMPONENT_CTS_ACTIVITY);
        intent.setAction(Intent.ACTION_MAIN);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        mActivityRule.getActivity().startActivity(intent);
    }

    @UiThreadTest
    @Test
    public void testGetWindowParams_whenTextToast_returnsNull() {
          assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        Toast toast = Toast.makeText(mContext, "Text", Toast.LENGTH_LONG);
        assertNull(toast.getWindowParams());
    }

    @UiThreadTest
    @Test
    public void testGetWindowParams_whenCustomToast_doesNotReturnNull() {
        Toast toast = new Toast(mContext);
        toast.setView(new TextView(mContext));
        assertNotNull(toast.getWindowParams());
    }

    @Test
    public void testShow_whenTextToast_sendsAccessibilityEvent() throws Throwable {
        makeTextToast();
        AccessibilityEventFilter filter =
                event -> event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;

        AccessibilityEvent event = mUiAutomation.executeAndWaitForEvent(
                () -> uncheck(() -> mActivityRule.runOnUiThread(
                        () -> showToastWithNotificationPermission(mToast))),
                filter,
                TIME_OUT);
        assertThat(event.getEventType()).isEqualTo(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        assertThat(event.getClassName()).isEqualTo(Toast.class.getCanonicalName());
        assertThat(event.getPackageName()).isEqualTo(mContext.getPackageName());
        assertThat(event.getText()).contains(TEST_TOAST_TEXT);
    }

    @Test
    public void testShow_whenCustomToast_sendsAccessibilityEvent() throws Throwable {
        makeCustomToast();
        AccessibilityEventFilter filter =
                event -> event.getEventType() == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED;

        AccessibilityEvent event = mUiAutomation.executeAndWaitForEvent(
                () -> uncheck(() -> mActivityRule.runOnUiThread(
                        () -> showToastWithNotificationPermission(mToast))),
                filter,
                TIME_OUT);

        assertThat(event.getEventType()).isEqualTo(
                AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED);
        assertThat(event.getClassName()).isEqualTo(Toast.class.getCanonicalName());
        assertThat(event.getPackageName()).isEqualTo(mContext.getPackageName());
        assertThat(event.getText()).contains(TEST_CUSTOM_TOAST_TEXT);
    }

    @Test
    public void testPackageCantPostMoreThanMaxToastsQuickly() throws Throwable {
        List<TextToastInfo> toasts =
                createTextToasts(MAX_PACKAGE_TOASTS_LIMIT + 1, "Text", Toast.LENGTH_SHORT);
        showToasts(toasts);

        assertTextToastsShownAndHidden(toasts.subList(0, MAX_PACKAGE_TOASTS_LIMIT));
        assertTextToastNotShown(toasts.get(MAX_PACKAGE_TOASTS_LIMIT));
    }

    /**
     * Long running test (~50 seconds) - we need to test toast rate limiting limits, which requires
     * us to wait a given amount of time to test that the limit has been enforced/lifted.
     */
    @Test
    @ApiTest(apis = {"android.widget.Toast#show"})
    public void testRateLimitingToastsWhenInBackground() throws Throwable {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        // enable rate limiting to test it
        SystemUtil.runWithShellPermissionIdentity(() -> mNotificationManager
                .setToastRateLimitingEnabled(true));
        // move to background
        mActivityRule.finishActivity();

        long totalTimeSpentMs = 0;
        int shownToastsNum = 0;
        // We add additional 3 seconds just to be sure we get into the next window.
        long additionalWaitTime = 3_000L;

        for (int i = 0; i < TOAST_RATE_LIMITS.length; i++) {
            int currentToastNum = TOAST_RATE_LIMITS[i] - shownToastsNum;
            List<TextToastInfo> toasts =
                    createTextToasts(currentToastNum + 1, "Text", Toast.LENGTH_SHORT);
            long startTime = SystemClock.elapsedRealtime();
            showToasts(toasts);

            assertTextToastsShownAndHidden(toasts.subList(0, currentToastNum));
            assertTextToastNotShown(toasts.get(currentToastNum));
            long endTime = SystemClock.elapsedRealtime();

            // We won't check after the last limit, no need to sleep then.
            if (i != TOAST_RATE_LIMITS.length - 1) {
                totalTimeSpentMs += endTime - startTime;
                shownToastsNum += currentToastNum;
                long sleepTime = Math.max(
                        TOAST_WINDOW_SIZES_MS[i] - totalTimeSpentMs + additionalWaitTime, 0);
                SystemClock.sleep(sleepTime);
                totalTimeSpentMs += sleepTime;
            }
        }
    }

    @Test
    public void testDontRateLimitToastsWhenInForeground() throws Throwable {
        // enable rate limiting to test it
        SystemUtil.runWithShellPermissionIdentity(() -> mNotificationManager
                .setToastRateLimitingEnabled(true));

        List<TextToastInfo> toasts =
                createTextToasts(TOAST_RATE_LIMITS[0] + 1, "Text", Toast.LENGTH_SHORT);
        showToasts(toasts);
        assertTextToastsShownAndHidden(toasts);
    }

    @Test
    public void testCustomToastPostedWhileInForeground_notShownWhenAppGoesToBackground()
            throws Throwable {
        List<CustomToastInfo> toasts = createCustomToasts(2, "Custom", Toast.LENGTH_SHORT);
        showToasts(toasts);
        assertCustomToastShown(toasts.get(0));

        // move to background
        mActivityRule.finishActivity();

        assertCustomToastHidden(toasts.get(0));
        assertCustomToastNotShown(toasts.get(1));
    }

    @Test
    @ApiTest(apis = {"android.widget.Toast#show"})
    public void testAppWithUnlimitedToastsPermissionCanPostUnlimitedToasts() throws Throwable {
        assumeFalse("Skipping test: Watch does not support new Toast behavior yet", isWatch());
        // enable rate limiting to test it
        SystemUtil.runWithShellPermissionIdentity(() -> mNotificationManager
                .setToastRateLimitingEnabled(true));
        // move to background
        mActivityRule.finishActivity();

        int highestToastRateLimit = TOAST_RATE_LIMITS[TOAST_RATE_LIMITS.length - 1];
        List<TextToastInfo> toasts = createTextToasts(highestToastRateLimit + 1, "Text",
                Toast.LENGTH_SHORT);

        // We have to show one by one to avoid max number of toasts enqueued by a single package at
        // a time.
        for (TextToastInfo t : toasts) {
            // Run with both android.permission.UNLIMITED_TOASTS and POST_NOTIFICATIONS permissions.
            showToastWithUnlimitedToastAndPostNotificationsPermissions(t);
            assertTextToastShownAndHidden(t);
        }
    }

    /** Create given number of text toasts with the same given text and length. */
    private List<TextToastInfo> createTextToasts(int num, String text, int length)
            throws Throwable {
        List<TextToastInfo> toasts = new ArrayList<>();
        mActivityRule.runOnUiThread(() -> {
            toasts.addAll(Stream
                    .generate(() -> TextToastInfo.create(mContext, text, length))
                    .limit(num)
                    .collect(toList()));
        });
        return toasts;
    }

    /** Create given number of custom toasts with the same given text and length. */
    private List<CustomToastInfo> createCustomToasts(int num, String text, int length)
            throws Throwable {
        List<CustomToastInfo> toasts = new ArrayList<>();
        mActivityRule.runOnUiThread(() -> {
            toasts.addAll(Stream
                    .generate(() -> CustomToastInfo.create(mContext, text, length))
                    .limit(num)
                    .collect(toList()));
        });
        return toasts;
    }

    private void showToasts(List<? extends ToastInfo> toasts) throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            for (ToastInfo t : toasts) {
                showToastWithNotificationPermission(t.getToast());
            }
        });
    }

    private void showToastWithUnlimitedToastAndPostNotificationsPermissions(ToastInfo toast)
            throws Throwable {
        mActivityRule.runOnUiThread(() -> {
            showToastWithPermissions(toast.getToast(), POST_NOTIFICATIONS, UNLIMITED_TOASTS);
        });
    }

    private void assertTextToastsShownAndHidden(List<TextToastInfo> toasts) {
        for (int i = 0; i < toasts.size(); i++) {
            assertTextToastShownAndHidden(toasts.get(i));
        }
    }

    private ConditionVariable registerBlockingReceiver(String action) {
        ConditionVariable broadcastReceived = new ConditionVariable(false);
        IntentFilter filter = new IntentFilter(action);
        mContext.registerReceiver(new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                broadcastReceived.open();
            }
        }, filter, Context.RECEIVER_EXPORTED_UNAUDITED);
        return broadcastReceived;
    }

    private void runOnMainAndDrawSync(@NonNull final View toastView,
            @Nullable final Runnable runner) {
        final CountDownLatch latch = new CountDownLatch(1);

        try {
            mActivityRule.runOnUiThread(() -> {
                final ViewTreeObserver.OnDrawListener listener =
                        new ViewTreeObserver.OnDrawListener() {
                            @Override
                            public void onDraw() {
                                // posting so that the sync happens after the draw that's about
                                // to happen
                                toastView.post(() -> {
                                    toastView.getViewTreeObserver().removeOnDrawListener(this);
                                    latch.countDown();
                                });
                            }
                        };

                toastView.getViewTreeObserver().addOnDrawListener(listener);

                if (runner != null) {
                    runner.run();
                }
                toastView.invalidate();
            });

            Assert.assertTrue("Expected toast draw pass occurred within 5 seconds",
                    latch.await(5, TimeUnit.SECONDS));
        } catch (Throwable t) {
            throw new RuntimeException(t);
        }
    }

    private boolean isCar() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);
    }

    private boolean isWatch() {
        PackageManager pm = mContext.getPackageManager();
        return pm.hasSystemFeature(PackageManager.FEATURE_WATCH);
    }

    private static void uncheck(ThrowingRunnable runnable) {
        try {
            runnable.run();
        } catch (Throwable e) {
            throw new RuntimeException(e);
        }
    }

    private static void showToastWithNotificationPermission(Toast toast) {
        showToast(toast, true);
    }

    private static void showToastWithoutNotificationPermission(Toast toast) {
        showToast(toast, false);
    }

    private static void showToast(Toast toast, boolean runWithPostNotificationPermission) {
        if (runWithPostNotificationPermission) {
            SystemUtil.runWithShellPermissionIdentity(() -> toast.show(), POST_NOTIFICATIONS);
        } else {
            toast.show();
        }
    }

    private static void showToastWithPermissions(Toast toast,  String... permissions) {
        if (permissions.length > 0) {
            SystemUtil.runWithShellPermissionIdentity(() -> toast.show(), permissions);
        } else {
            toast.show();
        }
    }

    private interface ThrowingRunnable {
        void run() throws Throwable;
    }

    private static class ConditionCallback extends Toast.Callback {
        private final ConditionVariable mToastShown;
        private final ConditionVariable mToastHidden;

        ConditionCallback(ConditionVariable toastShown, ConditionVariable toastHidden) {
            mToastShown = toastShown;
            mToastHidden = toastHidden;
        }

        @Override
        public void onToastShown() {
            mToastShown.open();
        }

        @Override
        public void onToastHidden() {
            mToastHidden.open();
        }
    }

    private static class TextToastInfo implements ToastInfo {
        private final Toast mToast;
        private final ConditionVariable mToastShown;
        private final ConditionVariable mToastHidden;

        TextToastInfo(
                Toast toast,
                ConditionVariable toastShown,
                ConditionVariable toastHidden) {
            mToast = toast;
            mToastShown = toastShown;
            mToastHidden = toastHidden;
        }

        static TextToastInfo create(Context context, String text, int toastLength) {
            Toast t = Toast.makeText(context, text, toastLength);
            ConditionVariable toastShown = new ConditionVariable(false);
            ConditionVariable toastHidden = new ConditionVariable(false);
            t.addCallback(new ConditionCallback(toastShown, toastHidden));
            return new TextToastInfo(t, toastShown, toastHidden);
        }

        @Override
        public Toast getToast() {
            return mToast;
        }

        boolean blockOnToastShown(long timeout) {
            return mToastShown.block(timeout);
        }

        boolean blockOnToastHidden(long timeout) {
            return mToastHidden.block(timeout);
        }
    }

    private static class CustomToastInfo implements ToastInfo {
        private final Toast mToast;

        CustomToastInfo(Toast toast) {
            mToast = toast;
        }

        static CustomToastInfo create(Context context, String text, int toastLength) {
            Toast t = new Toast(context);
            t.setDuration(toastLength);
            TextView view = new TextView(context);
            view.setText(text);
            t.setView(view);
            return new CustomToastInfo(t);
        }

        @Override
        public Toast getToast() {
            return mToast;
        }

        boolean isShowing() {
            return mToast.getView().getParent() != null;
        }
    }

    interface ToastInfo {
        Toast getToast();
    }
}
