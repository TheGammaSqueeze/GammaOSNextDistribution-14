/*
 * Copyright (C) 2020 The Android Open Source Project
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

import static android.server.wm.WindowManagerTestBase.startActivity;
import static android.view.Display.DEFAULT_DISPLAY;
import static android.view.Display.INVALID_DISPLAY;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION;
import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import android.app.WindowConfiguration;
import android.content.ComponentCallbacks;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Binder;
import android.os.IBinder;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.server.wm.DisplayMetricsSession;
import android.server.wm.TestLogService;
import android.server.wm.WindowContextTestActivity;
import android.server.wm.WindowManagerState;
import android.server.wm.display.WindowContextTests.TestWindowService.TestToken;
import android.view.View;
import android.view.WindowManager;
import android.window.WindowProviderService;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.test.core.app.ApplicationProvider;
import androidx.test.rule.ServiceTestRule;

import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

/**
 * Tests that verify the behavior of window context
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerDeviceDisplay:WindowContextTests
 */
@Presubmit
public class WindowContextTests extends WindowContextTestBase {
    @Test
    @AppModeFull
    public void testWindowContextConfigChanges() {
        createAllowSystemAlertWindowAppOpSession();
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        mInstrumentation.runOnMainSync(() -> {
            final View view = new View(windowContext);
            WindowManager wm = windowContext.getSystemService(WindowManager.class);
            wm.addView(view, new WindowManager.LayoutParams(TYPE_APPLICATION_OVERLAY));
        });
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(display.mId);

        mWmState.computeState();

        Rect bounds = windowContext.getSystemService(WindowManager.class).getCurrentWindowMetrics()
                .getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);

        displayMetricsSession.changeDisplayMetrics(1.2 /* sizeRatio */, 1.1 /* densityRatio */);

        mWmState.computeState();

        bounds = windowContext.getSystemService(WindowManager.class).getCurrentWindowMetrics()
                .getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);
    }

    private void assertBoundsEquals(ReportedDisplayMetrics expectedMetrics,
            Rect bounds) {
        assertEquals(expectedMetrics.getSize().getWidth(), bounds.width());
        assertEquals(expectedMetrics.getSize().getHeight(), bounds.height());
    }

    @Test
    @AppModeFull
    public void testWindowContextBindService() {
        createAllowSystemAlertWindowAppOpSession();
        final Context windowContext = createWindowContext(DEFAULT_DISPLAY);
        final ServiceConnection serviceConnection = new ServiceConnection() {
            @Override
            public void onServiceConnected(ComponentName name, IBinder service) {}

            @Override
            public void onServiceDisconnected(ComponentName name) {}
        };
        try {
            assertTrue("WindowContext must bind service successfully.",
                    windowContext.bindService(new Intent(windowContext, TestLogService.class),
                            serviceConnection, Context.BIND_AUTO_CREATE));
        } finally {
            windowContext.unbindService(serviceConnection);
        }
    }

    /**
     * Verify if the {@link ComponentCallbacks#onConfigurationChanged(Configuration)} callback
     * is received when the window context configuration changes.
     */
    @Test
    public void testWindowContextRegisterComponentCallbacks() {
        final TestComponentCallbacks callbacks = new TestComponentCallbacks();
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setSimulateDisplay(true).createDisplay();
        final Context windowContext = createWindowContext(display.mId);
        final DisplayMetricsSession displayMetricsSession =
                createManagedDisplayMetricsSession(display.mId);

        windowContext.registerComponentCallbacks(callbacks);

        callbacks.mLatch = new CountDownLatch(1);

        displayMetricsSession.changeDisplayMetrics(1.2 /* sizeRatio */, 1.1 /* densityRatio */);

        // verify if there is a callback from the window context configuration change.
        try {
            assertTrue(callbacks.mLatch.await(4, TimeUnit.SECONDS));
        } catch(InterruptedException e) {
            throw new RuntimeException(e);
        }
        Rect bounds = callbacks.mConfiguration.windowConfiguration.getBounds();
        assertBoundsEquals(displayMetricsSession.getDisplayMetrics(), bounds);

        windowContext.unregisterComponentCallbacks(callbacks);
    }

    /**
     * Verifies if window context on the secondary display receives global configuration changes.
     */
    @Test
    public void testWindowContextGlobalConfigChanges() {
        final TestComponentCallbacks callbacks = new TestComponentCallbacks();
        final WindowManagerState.DisplayContent display = createManagedVirtualDisplaySession()
                .setPublicDisplay(true).createDisplay();
        final FontScaleSession fontScaleSession = createManagedFontScaleSession();
        final Context windowContext = createWindowContext(display.mId);
        windowContext.registerComponentCallbacks(callbacks);
        final float expectedFontScale = fontScaleSession.get() + 0.3f;
        fontScaleSession.set(expectedFontScale);

        // Wait for TestComponentCallbacks#mConfiguration to be assigned.
        callbacks.waitForConfigChanged();

        // We don't rely on latch to verify the result because we may receive two configuration
        // changes. One may from that WindowContext attaches to a DisplayArea although it is before
        // ComponentCallback registration), the other is from font the scale change, which is what
        // we want to verify.
        waitForOrFail("font scale to match " + expectedFontScale, () ->
                expectedFontScale == callbacks.mConfiguration.fontScale);

        windowContext.unregisterComponentCallbacks(callbacks);
    }

    /**
     * Verify the {@link WindowProviderService} lifecycle:
     * <ul>
     *     <li>In {@link WindowProviderService#onCreate()}, register to the DisplayArea with
     *     given value from {@link WindowProviderService#getWindowType()} and
     *     {@link WindowProviderService#getWindowContextOptions()}} and receive a
     *     {@link Configuration} update which matches DisplayArea's metrics.</li>
     *     <li>After {@link WindowProviderService#attachToWindowToken(IBinder)}, the
     *     {@link WindowProviderService} must be switched to register to the Window Token and
     *     receive a configuration update which matches Window Token's metrics.</li>
     * </ul>
     */
    @Test
    public void testWindowProviderServiceLifecycle() {
        assumeTrue(supportsSplitScreenMultiWindow());

        // Start an activity for WindowProviderService to attach
        final WindowContextTestActivity activity = startActivity(WindowContextTestActivity.class);
        final ComponentName activityName = activity.getComponentName();

        // If the device supports multi-window, make this Activity to multi-window mode.
        // In this way, we can verify if the WindowProviderService's metrics matches
        // the split-screen Activity's metrics, which is different from TaskDisplayArea's metrics.
        mWmState.computeState(activityName);

        putActivityInPrimarySplit(activityName);

        activity.waitAndAssertConfigurationChanged();

        // Obtain the TestWindowService instance.
        final TestWindowService service = createManagedWindowServiceSession().getService();

        // Compute state to obtain associated TaskActivityArea information.
        mWmState.computeState(activityName);
        final WindowManagerState.DisplayArea da = mWmState.getTaskDisplayArea(activityName);
        final Rect daBounds = da.getFullConfiguration().windowConfiguration.getBounds();
        final Rect maxDaBounds = da.getFullConfiguration().windowConfiguration.getMaxBounds();

        assertBoundsMatches(service, daBounds, maxDaBounds,
                "WindowProviderService bounds must match DisplayArea bounds.");

        // Obtain the Activity's token and attach it to TestWindowService.
        final IBinder windowToken = activity.getWindow().getAttributes().token;
        service.attachToWindowToken(windowToken);

        final WindowManager wm = activity.getWindowManager();
        final Rect currentBounds = wm.getCurrentWindowMetrics().getBounds();
        final Rect maxBounds = wm.getMaximumWindowMetrics().getBounds();

        service.waitAndAssertConfigurationChanged();
        // After TestWindowService attaches the Activity's token, which is also a WindowToken,
        // it is expected to receive a config update which matches the WindowMetrics of
        // the Activity.
        assertBoundsMatches(service, currentBounds, maxBounds,
                "WindowProviderService bounds must match WindowToken bounds.");
    }

    /**
     * Verifies if:
     * <ul>
     *     <li>{@link android.view.WindowMetrics} bounds matches provided bounds.</li>
     *     <li>Bounds from {@link WindowProviderService#onConfigurationChanged(Configuration)}
     *     callback matches provided bounds.</li>
     * </ul>
     */
    private void assertBoundsMatches(TestWindowService service, Rect currentBounds,
            Rect maxBounds, String message) {
        final WindowConfiguration winConfig = service.mConfiguration.windowConfiguration;
        assertWithMessage(message + " WindowConfiguration#getBounds not matched.")
                .that(winConfig.getBounds()).isEqualTo(currentBounds);
        assertWithMessage(message +  " WindowConfiguration#getMaxBounds not "
                + "matched.").that(winConfig.getMaxBounds()).isEqualTo(maxBounds);

        final WindowManager wm = service.getSystemService(WindowManager.class);
        final Rect currentWindowBounds = wm.getCurrentWindowMetrics().getBounds();
        final Rect maxWindowBounds = wm.getMaximumWindowMetrics().getBounds();
        assertWithMessage(message + " Current WindowMetrics bounds not matched.")
                .that(currentWindowBounds).isEqualTo(currentBounds);
        assertWithMessage(message  + " Maximum WindowMetrics bounds not matched.")
                .that(maxWindowBounds).isEqualTo(maxBounds);
    }

    @Test
    public void testWidowProviderServiceGlobalConfigChanges() {
        final TestWindowService service = createManagedWindowServiceSession().getService();

        // Obtain the original config
        final Configuration originalConfiguration =
                new Configuration(service.getResources().getConfiguration());

        final FontScaleSession fontScaleSession = createManagedFontScaleSession();
        final float expectedFontScale = fontScaleSession.get() + 0.3f;
        fontScaleSession.set(expectedFontScale);

        service.waitAndAssertConfigurationChanged();

        assertThat(service.mConfiguration.fontScale).isEqualTo(expectedFontScale);
        // Also check Configuration obtained from WindowProviderService's Resources
        assertWithMessage("Configuration update must contains font scale change.")
                .that(originalConfiguration.diff(service.mConfiguration)
                        & ActivityInfo.CONFIG_FONT_SCALE).isNotEqualTo(0);
        assertWithMessage("Font scale must be updated to WindowProviderService Resources.")
                .that(service.getResources().getConfiguration().fontScale)
                .isEqualTo(expectedFontScale);
    }

    @Test
    public void testWindowProviderServiceCallWmBeforeOnCreateNotCrash() {
        final TestWindowService service =
                createManagedWindowServiceSession(true /* verifyWmInOnCreate */,
                        false /* verifyAttachingInvalidDisplay */).getService();
        if (service.mThrowableFromOnCreate != null) {
            throw new AssertionError("Calling WindowManager APIs before"
                    + " WindowProviderService#onCreate must not throw Throwable, but did.",
                    service.mThrowableFromOnCreate);
        }
    }

    @Test
    public void testWindowProviderServiceAttachingInvalidDisplayNotCrash() {
        final TestWindowServiceSession session = createManagedWindowServiceSession(
                false /* verifyWmInOnCreate */, true /* verifyAttachingInvalidDisplay */);
        final TestWindowService service = session.getService();
        if (session.mThrowableAtInitialization != null) {
            fail("WindowProviderService attaches invalid display must fallback to the default "
                    + "display without throwing exception, but did."
                    + session.mThrowableAtInitialization);
        }
        assertNotNull(service);
    }

    private TestWindowServiceSession createManagedWindowServiceSession() {
        return mObjectTracker.manage(new TestWindowServiceSession(false /* verifyWmInOnCreate */,
                false /* verifyAttachingInvalidDisplay */));
    }

    private TestWindowServiceSession createManagedWindowServiceSession(boolean verifyWmInOnCreate,
            boolean verifyAttachingInvalidDisplay) {
        return mObjectTracker.manage(new TestWindowServiceSession(verifyWmInOnCreate,
                verifyAttachingInvalidDisplay));
    }

    private static class TestWindowServiceSession implements AutoCloseable {
        private final ServiceTestRule mServiceRule = new ServiceTestRule();
        private TestWindowService mService;
        private static BooleanSupplier sVerifyWmInOnCreate;

        private static BooleanSupplier sVerifyAttachingInvalidDisplay;

        private Throwable mThrowableAtInitialization;

        private TestWindowServiceSession(boolean verifyWmInOnCreate,
                                         boolean verifyAttachingInvalidDisplay) {
            final Context context = ApplicationProvider.getApplicationContext();
            final Intent intent = new Intent(context, TestWindowService.class);
            sVerifyWmInOnCreate = () -> verifyWmInOnCreate;
            sVerifyAttachingInvalidDisplay = () -> verifyAttachingInvalidDisplay;
            try {
                final TestToken token = (TestToken) mServiceRule.bindService(intent);
                mService = token.getService();
            } catch (Throwable e) {
                mThrowableAtInitialization = e;
                mService = null;
            }
        }

        private TestWindowService getService() {
            return mService;
        }

        @Override
        public void close() {
            mServiceRule.unbindService();
        }
    }

    public static class TestWindowService extends WindowProviderService {
        private final IBinder mToken = new TestToken();
        private CountDownLatch mLatch = new CountDownLatch(1);
        private Configuration mConfiguration;
        private Throwable mThrowableFromOnCreate;

        @Override
        public int getWindowType() {
            return TYPE_APPLICATION;
        }

        @Override
        public int getInitialDisplayId() {
            return TestWindowServiceSession.sVerifyAttachingInvalidDisplay.getAsBoolean()
                    ? INVALID_DISPLAY : DEFAULT_DISPLAY;
        }

        @Nullable
        @Override
        public IBinder onBind(Intent intent) {
            return mToken;
        }

        @Override
        public void onCreate() {
            // Verify if call WindowManager before WindowProviderService#onCreate throws Exception.
            if (TestWindowServiceSession.sVerifyWmInOnCreate.getAsBoolean()) {
                try {
                    getSystemService(WindowManager.class).getCurrentWindowMetrics();
                } catch (Throwable t) {
                    mThrowableFromOnCreate = t;
                }
            }
            super.onCreate();
            mConfiguration = getResources().getConfiguration();
        }

        @Override
        public void onConfigurationChanged(Configuration newConfig) {
            super.onConfigurationChanged(newConfig);
            mConfiguration = newConfig;
            mLatch.countDown();
        }

        private void resetLatch() {
            mLatch = new CountDownLatch(1);
        }

        private void waitAndAssertConfigurationChanged() {
            try {
                assertThat(mLatch.await(4, TimeUnit.SECONDS)).isTrue();
            } catch(InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        public class TestToken extends Binder {
            private TestWindowService getService() {
                return TestWindowService.this;
            }
        }
    }

    private static class TestComponentCallbacks implements ComponentCallbacks {
        private Configuration mConfiguration;
        private CountDownLatch mLatch = new CountDownLatch(1);

        private void waitForConfigChanged() {
            try {
                assertThat(mLatch.await(4, TimeUnit.SECONDS)).isTrue();
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public void onConfigurationChanged(@NonNull Configuration newConfig) {
            mConfiguration = newConfig;
            mLatch.countDown();
        }

        @Override
        public void onLowMemory() {}
    }
}
