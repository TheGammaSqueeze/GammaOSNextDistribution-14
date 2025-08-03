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

package android.content.wm.cts;

import static android.view.WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth.assertWithMessage;

import android.app.Activity;
import android.app.WindowConfiguration;
import android.content.ComponentCallbacks;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.res.Configuration;
import android.graphics.Rect;
import android.os.Bundle;
import android.platform.test.annotations.AppModeSdkSandbox;
import android.platform.test.annotations.Presubmit;
import android.util.DisplayMetrics;
import android.view.Display;

import androidx.annotation.NonNull;
import androidx.test.filters.SmallTest;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Test for {@link Context#registerComponentCallbacks(ComponentCallbacks)}}.
 * <p>Test context type listed below:</p>
 * <ul>
 *     <li>{@link Activity} - The {@link ComponentCallbacks} should be added to Activity.</li>
 *     <li>Context derived from {@link Activity}
 *     - The The {@link ComponentCallbacks} should be added to
 *     {@link Context#getApplicationContext() Application Context}.</li>
 *     <li>{@link ContextWrapper} - get The {@link ComponentCallbacks} should be added to
 *     {@link ContextWrapper#getBaseContext() base Context}.</li>
 *     <li>Context via {@link Context#createWindowContext(int, Bundle)}
 *     - The {@link ComponentCallbacks} should be added to the Window Context.</li>
 * </ul>
 *
 * <p>Build/Install/Run:
 *     atest CtsContentTestCases:ContextRegisterComponentCallbacksTest
 */
@Presubmit
@SmallTest
@AppModeSdkSandbox(reason = "Allow test in the SDK sandbox (does not prevent other modes).")
public class ContextRegisterComponentCallbacksTest extends ContextTestBase {
    private Context mTestContext;
    private TestComponentCallbacks mTestCallbacks;

    @Before
    public void setUp() {
        super.setUp();
        mTestCallbacks = new TestComponentCallbacks();
    }

    @After
    public void tearDown() {
        mTestContext.unregisterComponentCallbacks(mTestCallbacks);
    }

    /**
     * Verifies if {@link ComponentCallbacks} is added to the {@link Activity}
     * via {@link Activity#registerComponentCallbacks(ComponentCallbacks)}.
     */
    @Test
    public void testRegisterComponentCallbacksOnActivity() throws Throwable {
        final Activity activity = getTestActivity();
        initializeTestContext(activity);

        final Configuration config = new Configuration();
        config.fontScale = 1.2f;
        config.windowConfiguration.setWindowingMode(
                WindowConfiguration.WINDOWING_MODE_FREEFORM);
        config.windowConfiguration.setBounds(new Rect(0, 0, 100, 100));

        mActivityRule.runOnUiThread(() -> activity.onConfigurationChanged(config));

        mTestCallbacks.waitForConfigChanged();

        assertWithMessage("The dispatched Configuration must be the same")
                .that(mTestCallbacks.mConfiguration).isEqualTo(config);

    }

    /**
     * Verifies if {@link ComponentCallbacks} is added to the
     * {@link ContextWrapper#getBaseContext() base Context of ContextWrapper}
     * via {@link ContextWrapper#registerComponentCallbacks(ComponentCallbacks)}.
     */
    @Test
    public void testRegisterComponentCallbacksOnContextWrapper() throws Throwable {
        final Activity activity = getTestActivity();
        final Context contextWrapper = new ContextWrapper(activity);
        initializeTestContext(contextWrapper);

        final Configuration config = new Configuration();
        config.fontScale = 1.2f;
        config.windowConfiguration.setWindowingMode(
                WindowConfiguration.WINDOWING_MODE_FREEFORM);
        config.windowConfiguration.setBounds(new Rect(0, 0, 100, 100));

        // Make the Activity dispatch #onConfigurationChanged and verify if the
        // ComponentCallbacks receives the config change.
        mActivityRule.runOnUiThread(() -> activity.onConfigurationChanged(config));

        mTestCallbacks.waitForConfigChanged();

        assertWithMessage("The dispatched Configuration must be the same")
                .that(mTestCallbacks.mConfiguration).isEqualTo(config);
    }

    /**
     * Verifies if {@link ComponentCallbacks} is added to {@link Context#getApplicationContext()}
     * for Context without overriding
     * {@link Context#registerComponentCallbacks(ComponentCallbacks)}.
     */
    @Test
    public void testRegisterComponentCallbacksOnContextWithoutOverriding() throws Throwable {
        final Activity activity = getTestActivity();
        final Context contextWithoutOverriding = activity.createAttributionContext("");
        initializeTestContext(contextWithoutOverriding);

        final Configuration config = new Configuration();
        config.fontScale = 1.2f;
        config.windowConfiguration.setWindowingMode(
                WindowConfiguration.WINDOWING_MODE_FREEFORM);
        config.windowConfiguration.setBounds(new Rect(0, 0, 100, 100));

        mActivityRule.runOnUiThread(() -> activity.onConfigurationChanged(config));

        assertThat(mTestCallbacks.mLatch.await(1, TimeUnit.SECONDS)).isFalse();

        assertWithMessage("#onConfigurationChanged must not dispatched.")
                .that(mTestCallbacks.mConfiguration).isNull();
    }

    /**
     * Verifies if {@link ComponentCallbacks} is added to the Window Context
     * via {@link Activity#registerComponentCallbacks(ComponentCallbacks)}.
     */
    @Test
    public void testRegisterComponentCallbacksOnWindowContext() throws Throwable {
        // Create a WindowContext on secondary display.
        final Display secondaryDisplay = getSecondaryDisplay();
        final Context windowContext = mApplicationContext.createWindowContext(secondaryDisplay,
                TYPE_APPLICATION_OVERLAY, null /* options */);
        initializeTestContext(windowContext);

        final DisplayMetrics displayMetrics = new DisplayMetrics();
        secondaryDisplay.getMetrics(displayMetrics);

        final int newWidth = displayMetrics.widthPixels + 10;
        final int newHeight = displayMetrics.heightPixels + 10;
        // Resize display to update WindowContext's configuration.
        resizeSecondaryDisplay(newWidth, newHeight, displayMetrics.densityDpi);

        mTestCallbacks.waitForConfigChanged();

        final Rect bounds = mTestCallbacks.mConfiguration.windowConfiguration.getBounds();
        assertWithMessage("WindowContext width must match resized display")
                .that(bounds.width()).isEqualTo(newWidth);
        assertWithMessage("WindowContext height must match resized display")
                .that(bounds.height()).isEqualTo(newHeight);
    }

    private void initializeTestContext(Context context) {
        mTestContext = context;
        mTestContext.registerComponentCallbacks(mTestCallbacks);
    }

    private static class TestComponentCallbacks implements ComponentCallbacks {
        private Configuration mConfiguration;
        private final CountDownLatch mLatch = new CountDownLatch(1);

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
