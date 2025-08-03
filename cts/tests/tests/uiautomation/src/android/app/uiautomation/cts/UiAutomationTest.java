/*
 * Copyright (C) 2014 The Android Open Source Project
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

package android.app.uiautomation.cts;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeFalse;

import android.Manifest;
import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.utils.ActivityLaunchUtils;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.content.ContentResolver;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.os.Process;
import android.os.SystemClock;
import android.platform.test.annotations.AppModeFull;
import android.provider.Settings;
import android.view.FrameStats;
import android.view.KeyEvent;
import android.view.WindowAnimationFrameStats;
import android.view.WindowContentFrameStats;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityManager;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.ListView;

import static com.android.compatibility.common.util.TestUtils.waitOn;


import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.UserHelper;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.TimeoutException;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Tests for the UiAutomation APIs.
 */
@RunWith(AndroidJUnit4.class)
public final class UiAutomationTest {

    private static final long IDLE_QUIET_TIME_MS = 1000;
    private static final long IDLE_WAIT_TIME_MS = 10 * 1000;
    private static final long TIMEOUT_FOR_SERVICE_ENABLE_MS = 10 * 1000;
    private static final long TIMEOUT_FOR_LISTENER_NOTIFIED_MS = 5000;


    // Used to enable/disable accessibility services
    private static final String COMPONENT_NAME_SEPARATOR = ":";

    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();
    private final ActivityTestRule<UiAutomationTestActivity> mActivityRule =
            new ActivityTestRule<>(UiAutomationTestActivity.class, false, false);

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    private final UserHelper mUserHelper = new UserHelper();

    private UiAutomation mUiAutomation;
    private Activity mActivity;


    @Before
    public void setUp() {
        // TODO(b/272604566): remove check below once a11y supports concurrent users
        // NOTE: cannot use Harrier / DeviceState because they call Instrumentation in a way that
        // would make the tests pass. Besides, there are a @RequireNotVisibleBackgroundUsers and a
        // @RequireRunNotOnSecondaryUser, but not a @RequireRunNotOnVisibleBackgroundSecondaryUser
        assumeFalse("not supported when running on visible background user",
                mUserHelper.isVisibleBackgroundUser());

        InstrumentedAccessibilityService.disableAllServices();
    }

    @After
    public void tearDown() {
        if (mUiAutomation != null) {
            mUiAutomation.destroy();
        }
    }

    @AfterClass
    public static void postTestTearDown() {
        InstrumentedAccessibilityService.disableAllServices();
    }

    @AppModeFull
    @Test
    public void testAdoptAllShellPermissions() {
        final Context context = getInstrumentation().getContext();
        final ActivityManager activityManager = context.getSystemService(ActivityManager.class);
        final PackageManager packageManager = context.getPackageManager();
        mUiAutomation = getInstrumentation().getUiAutomation();

        // Try to access APIs guarded by a platform defined signature permissions
        assertThrows(SecurityException.class,
                () -> activityManager.getPackageImportance("foo.bar.baz"),
                "Should not be able to access APIs protected by a permission apps cannot get");
        assertThrows(SecurityException.class,
                () -> packageManager.grantRuntimePermission(context.getPackageName(),
                        Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle()),
                "Should not be able to access APIs protected by a permission apps cannot get");

        // Access APIs guarded by a platform defined signature permissions
        try {
            mUiAutomation.adoptShellPermissionIdentity();
            // Access APIs guarded by a platform defined signature permission
            activityManager.getPackageImportance("foo.bar.baz");

            // Grant ourselves a runtime permission (was granted at install)
            packageManager.grantRuntimePermission(context.getPackageName(),
                    Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle());
        } catch (SecurityException e) {
            fail("Should be able to access APIs protected by a permission apps cannot get");
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }

        // Try to access APIs guarded by a platform defined signature permissions
        assertThrows(SecurityException.class,
                () -> activityManager.getPackageImportance("foo.bar.baz"),
                "Should not be able to access APIs protected by a permission apps cannot get");
        assertThrows(SecurityException.class,
                () -> packageManager.revokeRuntimePermission(context.getPackageName(),
                        Manifest.permission.ANSWER_PHONE_CALLS, Process.myUserHandle()),
                "Should not be able to access APIs protected by a permission apps cannot get");
    }

    @AppModeFull
    @Test
    public void testAdoptSomeShellPermissions() {
        final Context context = getInstrumentation().getContext();
        mUiAutomation = getInstrumentation().getUiAutomation();

        // Make sure we don't have any of the permissions
        assertThat(context.checkSelfPermission(Manifest.permission.BATTERY_STATS))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Adopt a permission
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.BATTERY_STATS);
        // Check one is granted and the other not
        assertThat(context.checkSelfPermission(Manifest.permission.BATTERY_STATS))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS))
                .isEqualTo(PackageManager.PERMISSION_DENIED);

        // Adopt all permissions
        mUiAutomation.adoptShellPermissionIdentity();
        // Check both permissions are granted
        assertThat(context.checkSelfPermission(Manifest.permission.BATTERY_STATS))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
        assertThat(context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);

        // Adopt a permission
        mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.PACKAGE_USAGE_STATS);
        // Check one is granted and the other not
        assertThat(context.checkSelfPermission(Manifest.permission.BATTERY_STATS))
                .isEqualTo(PackageManager.PERMISSION_DENIED);
        assertThat(context.checkSelfPermission(Manifest.permission.PACKAGE_USAGE_STATS))
                .isEqualTo(PackageManager.PERMISSION_GRANTED);
    }

    @Test
    public void testWindowContentFrameStats() throws Exception {
        mUiAutomation = getInstrumentation().getUiAutomation();
        final int windowId = startActivitySync();
        // Clear stats to start with a clean slate.
        assertWithMessage("clearWindowContentFrameStats(%s)", windowId)
                .that(mUiAutomation.clearWindowContentFrameStats(windowId)).isTrue();

        // Scroll around before grabbing the frame stats.
        final ListView listView = mActivity.findViewById(R.id.list_view);
        scrollListView(listView, listView.getAdapter().getCount() - 1);
        scrollListView(listView, 0);
        WindowContentFrameStats stats = mUiAutomation.getWindowContentFrameStats(windowId);

        assertThat(stats).isNotNull();
        assertThat(stats.getRefreshPeriodNano()).isGreaterThan(0);
        assertThat(stats.getFrameCount()).isGreaterThan(0);
        assertWindowContentTimestampsInAscendingOrder(stats);
        // The start and end times are based on first and last frame.
        assertThat(stats.getStartTimeNano()).isEqualTo(
                stats.getFramePresentedTimeNano(0));
        assertThat(stats.getEndTimeNano()).isEqualTo(
                stats.getFramePresentedTimeNano(stats.getFrameCount() - 1));
    }

    @Test
    public void testWindowContentFrameStats_NoAnimation() throws Exception {
        mUiAutomation = getInstrumentation().getUiAutomation();
        final int windowId = startActivitySync();
        // Clear stats to start with a clean slate.
        assertWithMessage("clearWindowContentFrameStats(%s)", windowId)
                .that(mUiAutomation.clearWindowContentFrameStats(windowId)).isTrue();

        WindowContentFrameStats stats = mUiAutomation.getWindowContentFrameStats(windowId);

        assertThat(stats).isNotNull();
        assertThat(stats.getRefreshPeriodNano()).isGreaterThan(0);
        // Without scrolling we should have at most one frame rendered. Having zero or one
        // frames rendered here depends on the render pipeline and is out of scope of this test.
        final int frameCount = stats.getFrameCount();
        assertThat(frameCount).isAtMost(1);
        if (frameCount == 0) {
            assertThat(stats.getStartTimeNano()).isEqualTo(FrameStats.UNDEFINED_TIME_NANO);
            assertThat(stats.getEndTimeNano()).isEqualTo(FrameStats.UNDEFINED_TIME_NANO);
        }
    }

    @Test
    public void testWindowAnimationFrameStatsDoesNotCrash() {
        mUiAutomation = getInstrumentation().getUiAutomation();

        // Get the frame stats. This just needs to not crash because these APIs are deprecated.
        mUiAutomation.clearWindowAnimationFrameStats();
        WindowAnimationFrameStats stats = mUiAutomation.getWindowAnimationFrameStats();

        assertThat(stats.getFrameCount()).isEqualTo(0);
    }

    @Test
    public void testUsingUiAutomationAfterDestroy_shouldThrowException() {
        mUiAutomation = getInstrumentation().getUiAutomation();

        mUiAutomation.destroy();

        assertThrows(RuntimeException.class, () -> mUiAutomation.getServiceInfo(),
                "Expected exception when using destroyed UiAutomation");
    }

    @AppModeFull
    @Test
    public void testDontSuppressAccessibility_canStartA11yService() {
        mUiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);

        enableAccessibilityService();
    }

    @AppModeFull
    @Test
    public void testUiAutomationWithNoFlags_shutsDownA11yService() {
        enableAccessibilityService();

        mUiAutomation = getInstrumentation().getUiAutomation();

        waitForAccessibilityServiceToUnbind();
    }

    @AppModeFull
    @Test
    public void testUiAutomationWithDontUseAccessibilityFlag_shutsDownA11yService() {
        enableAccessibilityService();

        mUiAutomation = getInstrumentation().getUiAutomation(
                UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);

        waitForAccessibilityServiceToUnbind();
    }

    @AppModeFull
    @Test
    public void testUiAutomationSuppressingA11yServices_a11yServiceStartsWhenDestroyed() {
        enableAccessibilityService();

        mUiAutomation = getInstrumentation().getUiAutomation();
        waitForAccessibilityServiceToUnbind();

        mUiAutomation.destroy();
        waitForAccessibilityServiceToStart();
    }

    @AppModeFull
    @Test
    public void testUiAutomationSuppressingA11yServices_a11yServiceStartsWhenFlagsChange() {
        enableAccessibilityService();

        mUiAutomation = getInstrumentation().getUiAutomation();
        waitForAccessibilityServiceToUnbind();

        mUiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_SUPPRESS_ACCESSIBILITY_SERVICES);
        waitForAccessibilityServiceToStart();
    }

    @AppModeFull
    @Test
    public void testCallingAccessibilityAPIsWithDontUseAccessibilityFlag_shouldThrowException() {
        mUiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
        final String failMsg =
                "Should not be able to access Accessibility APIs disabled by UiAutomation flag, "
                        + "FLAG_DONT_USE_ACCESSIBILITY";
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK),
                failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.getServiceInfo(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.setServiceInfo(new AccessibilityServiceInfo()), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.findFocus(AccessibilityNodeInfo.FOCUS_INPUT), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.getWindows(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.getWindowsOnAllDisplays(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.clearWindowContentFrameStats(-1), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.getWindowContentFrameStats(-1), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.getRootInActiveWindow(), failMsg);
        assertThrows(IllegalStateException.class,
                () -> mUiAutomation.setOnAccessibilityEventListener(null), failMsg);
    }

    @AppModeFull
    @Test
    public void testCallingPublicAPIsWithDontUseAccessibilityFlag_shouldNotThrowException() {
        mUiAutomation = getInstrumentation()
                .getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
        final KeyEvent event = new KeyEvent(0, 0, KeyEvent.ACTION_DOWN,
                KeyEvent.KEYCODE_BACK, 0);
        mUiAutomation.injectInputEvent(event, true);
        mUiAutomation.syncInputTransactions();
        mUiAutomation.setRotation(UiAutomation.ROTATION_FREEZE_0);
        mUiAutomation.takeScreenshot();
        mUiAutomation.clearWindowAnimationFrameStats();
        mUiAutomation.getWindowAnimationFrameStats();
        try {
            mUiAutomation.adoptShellPermissionIdentity(Manifest.permission.BATTERY_STATS);
        } finally {
            mUiAutomation.dropShellPermissionIdentity();
        }
    }

    @AppModeFull
    @Test
    public void testOnA11yStateChanged_dontUseAccessibilityFlag_notifiesAppA11yIsOff() {
        enableAccessibilityWithAutomationIfNeeded();
        final AccessibilityManager manager =
                (AccessibilityManager) getInstrumentation().getContext().getSystemService(
                        Context.ACCESSIBILITY_SERVICE);
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final Object waitLockForA11yOff = new Object();
        final AccessibilityManager.AccessibilityStateChangeListener listener =
                b -> {
                    synchronized (waitLockForA11yOff) {
                        enabled.set(b);
                        waitLockForA11yOff.notifyAll();
                    }
                };
        manager.addAccessibilityStateChangeListener(listener);
        try {
            getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
            waitOn(waitLockForA11yOff, () -> !enabled.get(), TIMEOUT_FOR_LISTENER_NOTIFIED_MS,
                    "Accessibility is turned off.");
        } finally {
            manager.removeAccessibilityStateChangeListener(listener);
        }
    }

    @AppModeFull
    @Test
    public void testOnA11yStateChanged_removeDontUseAccessibilityFlag_turnsA11yBackOn() {
        enableAccessibilityWithAutomationIfNeeded();
        final AccessibilityManager manager =
                (AccessibilityManager) getInstrumentation().getContext().getSystemService(
                        Context.ACCESSIBILITY_SERVICE);
        final AtomicBoolean enabled = new AtomicBoolean(true);
        final Object waitLockForA11yChange = new Object();
        final AccessibilityManager.AccessibilityStateChangeListener listener =
                b -> {
                    synchronized (waitLockForA11yChange) {
                        enabled.set(b);
                        waitLockForA11yChange.notifyAll();
                    }
                };
        manager.addAccessibilityStateChangeListener(listener);
        try {
            getInstrumentation().getUiAutomation(UiAutomation.FLAG_DONT_USE_ACCESSIBILITY);
            waitOn(waitLockForA11yChange, () -> !enabled.get(), TIMEOUT_FOR_LISTENER_NOTIFIED_MS,
                    "Accessibility is turned off.");
            getInstrumentation().getUiAutomation(0);
            waitOn(waitLockForA11yChange, () -> enabled.get(), TIMEOUT_FOR_LISTENER_NOTIFIED_MS,
                    "Accessibility is turned back on.");
        } finally {
            manager.removeAccessibilityStateChangeListener(listener);
        }
    }

    @Test
    public void testTakeScreenshot() throws Exception {
        mUiAutomation = getInstrumentation().getUiAutomation();

        // Test with null window
        Bitmap bitmap = mUiAutomation.takeScreenshot(null);
        assertThat(bitmap).isNull();

        startActivitySync();
        final Bitmap screenshot = mUiAutomation.takeScreenshot(mActivity.getWindow());
        assertThat(screenshot).isNotNull();
    }

    private void scrollListView(final ListView listView, final int position)
            throws TimeoutException {
        getInstrumentation().runOnMainSync(() -> listView.smoothScrollToPosition(position));
        UiAutomation.AccessibilityEventFilter scrollFilter =
                accessibilityEvent -> accessibilityEvent.getEventType()
                        == AccessibilityEvent.TYPE_VIEW_SCROLLED;
        mUiAutomation.executeAndWaitForEvent(() -> {}, scrollFilter, IDLE_WAIT_TIME_MS);
        mUiAutomation.waitForIdle(IDLE_QUIET_TIME_MS, IDLE_WAIT_TIME_MS);
    }

    private void enableAccessibilityService() {
        final Context context = getInstrumentation().getContext();
        final AccessibilityManager manager = context.getSystemService(AccessibilityManager.class);
        for (AccessibilityServiceInfo serviceInfo :
                manager.getInstalledAccessibilityServiceList()) {
            if (context.getString(R.string.uiautomation_a11y_service_description)
                    .equals(serviceInfo.getDescription())) {
                ContentResolver cr = context.getContentResolver();
                String enabledServices = Settings.Secure.getString(cr,
                        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES);
                Settings.Secure.putString(cr, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
                        enabledServices + COMPONENT_NAME_SEPARATOR + serviceInfo.getId());
                Settings.Secure.putInt(cr, Settings.Secure.ACCESSIBILITY_ENABLED, 1);
                waitForAccessibilityServiceToStart();
                return;
            }
        }
        throw new RuntimeException("Test accessibility service not found for user "
                + mUserHelper.getUserId());
    }

    private void waitForAccessibilityServiceToStart() {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE_MS;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind) {
                if (UiAutomationTestA11yService.sConnectedInstance != null
                        && UiAutomationTestA11yService.sConnectedInstance.isConnected()) {
                    return;
                }
                try {
                    UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind.wait(
                            timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Test accessibility service not starting");
    }

    private void waitForAccessibilityServiceToUnbind() {
        long timeoutTimeMillis = SystemClock.uptimeMillis() + TIMEOUT_FOR_SERVICE_ENABLE_MS;
        while (SystemClock.uptimeMillis() < timeoutTimeMillis) {
            synchronized (UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind) {
                if (UiAutomationTestA11yService.sConnectedInstance == null) {
                    return;
                }
                try {
                    UiAutomationTestA11yService.sWaitObjectForConnectOrUnbind.wait(
                            timeoutTimeMillis - SystemClock.uptimeMillis());
                } catch (InterruptedException e) {
                    // Ignored; loop again
                }
            }
        }
        throw new RuntimeException("Test accessibility service doesn't unbind");
    }

    private void assertWindowContentTimestampsInAscendingOrder(WindowContentFrameStats stats) {
        long lastDesiredPresentTimeNano = 0;
        long lastPreviousFramePresentTimeNano = 0;
        long lastFrameReadyTimeNano = 0;

        StringBuilder statsDebugDump = new StringBuilder(stats.toString());
        for (int i = 0; i < stats.getFrameCount(); i++) {
            statsDebugDump.append(" [").append(i).append(":").append(
                    stats.getFramePostedTimeNano(i)).append(" ").append(
                    stats.getFramePresentedTimeNano(i)).append(" ").append(
                    stats.getFrameReadyTimeNano(i)).append("] ");
        }

        final int frameCount = stats.getFrameCount();
        for (int i = 0; i < frameCount; i++) {
            final long desiredPresentTimeNano = stats.getFramePostedTimeNano(i);
            final long previousFramePresentTimeNano = stats.getFramePresentedTimeNano(i);
            final long frameReadyTimeNano = stats.getFrameReadyTimeNano(i);

            if (desiredPresentTimeNano == FrameStats.UNDEFINED_TIME_NANO
                    || previousFramePresentTimeNano == FrameStats.UNDEFINED_TIME_NANO
                    || frameReadyTimeNano == FrameStats.UNDEFINED_TIME_NANO) {
                continue;
            }

            if (i > 0) {
                // WindowContentFrameStats#getFramePresentedTimeNano() returns the previous frame
                // presented time, so verify the actual presented timestamp is ahead of the
                // last frame's desired present time and frame ready time.

                // NOTE: actual present time maybe an estimate. If this test continues to be flaky,
                // we may need to add a margin like the one below.
                // previousFramePresentTimeNano += stats.getRefreshPeriodNano() / 2;
                assertWithMessage("Failed frame:" + i + statsDebugDump).that(
                        previousFramePresentTimeNano).isGreaterThan(lastDesiredPresentTimeNano);
                assertWithMessage("Failed frame:" + i + statsDebugDump).that(
                        previousFramePresentTimeNano).isGreaterThan(lastFrameReadyTimeNano);
            }
            assertWithMessage("Failed frame:" + i + statsDebugDump).that(
                    previousFramePresentTimeNano).isGreaterThan(lastPreviousFramePresentTimeNano);

            lastDesiredPresentTimeNano = desiredPresentTimeNano;
            lastPreviousFramePresentTimeNano = previousFramePresentTimeNano;
            lastFrameReadyTimeNano = frameReadyTimeNano;
        }
    }

    // An actual version of assertThrows() was added in JUnit5
    private static <T extends Throwable> void assertThrows(Class<T> clazz, Runnable r,
            String message) {
        try {
            r.run();
        } catch (Exception expected) {
            assertThat(expected.getClass()).isAssignableTo(clazz);
            return;
        }
        fail(message);
    }

    private Instrumentation getInstrumentation() {
        return InstrumentationRegistry.getInstrumentation();
    }

    /** Start an activity and return its accessibility window id. */
    private int startActivitySync() throws Exception {
        mActivity = ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen(
                getInstrumentation(), mUiAutomation, mActivityRule);
        return ActivityLaunchUtils.findWindowByTitle(mUiAutomation,
                ActivityLaunchUtils.getActivityTitle(getInstrumentation(), mActivity)).getId();
    }

    /**
     * Ensure that AccessibilityManager has accessibility enabled. If it isn't, enable it by
     * creating a UiAutomation instance.
     */
    private void enableAccessibilityWithAutomationIfNeeded() {
        final AccessibilityManager manager =
                (AccessibilityManager) getInstrumentation().getContext().getSystemService(
                        Context.ACCESSIBILITY_SERVICE);
        if (manager.isEnabled()) {
            return;
        }
        final AtomicBoolean enabled = new AtomicBoolean(false);
        final Object waitLockForA11yOn = new Object();
        final AccessibilityManager.AccessibilityStateChangeListener listener =
                b -> {
                    synchronized (waitLockForA11yOn) {
                        enabled.set(b);
                        waitLockForA11yOn.notifyAll();
                    }
                };
        manager.addAccessibilityStateChangeListener(listener);
        try {
            getInstrumentation().getUiAutomation(0);
            waitOn(waitLockForA11yOn, () -> enabled.get(), TIMEOUT_FOR_LISTENER_NOTIFIED_MS,
                    "Accessibility is turned on since a UiAutomation instance exists.");
        } finally {
            manager.removeAccessibilityStateChangeListener(listener);
        }
    }
}
