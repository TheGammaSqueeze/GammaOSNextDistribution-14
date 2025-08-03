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

package android.view.surfacecontrol.cts;

import static android.server.wm.BuildUtils.HW_TIMEOUT_MULTIPLIER;
import static android.view.cts.surfacevalidator.BitmapPixelChecker.getInsets;
import static android.view.cts.surfacevalidator.BitmapPixelChecker.validateScreenshot;

import static org.junit.Assert.assertTrue;

import android.app.Instrumentation;
import android.graphics.Color;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.server.wm.CtsWindowInfoUtils;
import android.view.View;
import android.view.WindowManager;
import android.view.cts.surfacevalidator.BitmapPixelChecker;
import android.widget.PopupWindow;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import com.android.window.flags.Flags;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TestName;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

public class FlagSecureTest {
    private static final long WAIT_TIME_S = 5L * HW_TIMEOUT_MULTIPLIER;

    @Rule
    public TestName mName = new TestName();

    @Rule
    public ActivityScenarioRule<TestActivity> mActivityRule =
            new ActivityScenarioRule<>(TestActivity.class);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule =
            DeviceFlagsValueProvider.createCheckFlagsRule();

    private TestActivity mActivity;

    private Instrumentation mInstrumentation;

    @Before
    public void setUp() throws InterruptedException {
        mActivityRule.getScenario().onActivity(a -> mActivity = a);
        mInstrumentation = InstrumentationRegistry.getInstrumentation();
    }

    @RequiresFlagsEnabled(Flags.FLAG_SECURE_WINDOW_STATE)
    @Test
    public void testChildWindowInheritsFlagSecure() throws InterruptedException {
        CountDownLatch popupWindowCreatedLatch = new CountDownLatch(1);
        View popupView = new View(mActivity);
        popupView.setBackgroundColor(Color.RED);
        PopupWindow popupWindow = new PopupWindow(popupView, 100, 100);

        mActivity.runOnUiThread(() -> {
            mActivity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
            View rootView = mActivity.getWindow().getDecorView();
            if (rootView.isAttachedToWindow()) {
                popupWindow.showAsDropDown(rootView);
                popupWindowCreatedLatch.countDown();
            } else {
                rootView.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                    @Override
                    public void onViewAttachedToWindow(@NonNull View v) {
                        popupWindow.showAsDropDown(rootView);
                        popupWindowCreatedLatch.countDown();
                    }

                    @Override
                    public void onViewDetachedFromWindow(@NonNull View v) {
                    }
                });
            }
        });

        mInstrumentation.waitForIdleSync();
        assertTrue("Failed to add popup window",
                popupWindowCreatedLatch.await(WAIT_TIME_S, TimeUnit.SECONDS));
        assertTrue("Failed to wait for popup window to be visible",
                CtsWindowInfoUtils.waitForWindowVisible(popupView));

        // Take screenshot of the parent window since both child and parent should be black since
        // both should be marked as secure
        BitmapPixelChecker pixelChecker = new BitmapPixelChecker(Color.BLACK);
        validateScreenshot(mName, mActivity, pixelChecker, -1 /* expectedMatchingPixels */,
                getInsets(mActivity));
    }
}
