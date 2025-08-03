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

package com.android.tests.sdksandbox.ui;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.fail;

import android.app.UiAutomation;
import android.app.sdksandbox.testutils.SdkSandboxUiTestRule;
import android.os.RemoteException;
import android.os.SystemClock;
import android.view.MotionEvent;
import android.view.SurfaceView;
import android.view.View;
import android.widget.ScrollView;

import androidx.test.platform.app.InstrumentationRegistry;

import com.android.sdksandbox.uiprovider.IUiProviderApi;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.JUnit4;

@RunWith(JUnit4.class)
public class SdkSandboxScreenshotTest {

    // Slight delay used to ensure view has rendered before screenshot testing.
    // TODO(b/268204038): Figure out how to decrease this delay using graphics acceleration
    // in testing environments.
    private static final int RENDERING_DELAY_MS = 1000;

    // TODO(b/268204038): Have multiple golden images to render.
    private static final int WIDTH_PX = 500;
    private static final int HEIGHT_PX = 500;

    private static final String IMG_IDENTIFIER = "colors";
    private static final String SDK_NAME = "com.android.sdksandbox.uiprovider";
    private IUiProviderApi mUiProvider;
    private UiAutomation mUiAutomation;

    @Rule
    public SdkSandboxUiTestRule mUiTestRule =
            new SdkSandboxUiTestRule(
                    InstrumentationRegistry.getInstrumentation().getContext(),
                    UiTestActivity.class,
                    SDK_NAME);

    @Before
    public void setUp() {
        mUiProvider = IUiProviderApi.Stub.asInterface(mUiTestRule.getSandboxedSdk().getInterface());
        mUiAutomation = InstrumentationRegistry.getInstrumentation().getUiAutomation();
    }

    @Test
    public void testSimpleRemoteRender() throws Exception {
        renderAndVerifyView(R.id.rendered_view, WIDTH_PX, HEIGHT_PX);
    }

    @Test
    public void testAnotherSimpleRemoteRender() throws Exception {
        renderAndVerifyView(R.id.rendered_view2, WIDTH_PX, HEIGHT_PX);
    }

    @Test
    public void testScrolling() throws Exception {
        mUiTestRule.switchActivity(ScrollTestActivity.class);
        renderAndVerifyView(R.id.rendered_view_in_scrollview, WIDTH_PX, HEIGHT_PX);
        // Verify that rendered view is consistent as it scrolls.
        for (int i = 0; i < 5; i++) {
            final int scrollY = (i + 1) * 100;
            mUiTestRule
                    .getActivityScenario()
                    .onActivity(
                            activity -> {
                                ScrollView view = activity.findViewById(R.id.scroll_view);
                                view.scrollTo(0, scrollY);
                            });
            verifyView(R.id.rendered_view_in_scrollview, WIDTH_PX, HEIGHT_PX);
        }
    }

    @Test
    public void testClickHandling() throws Exception {
        mUiTestRule.switchActivity(OverlappingActivity.class);
        // Remote view has not been rendered, so overlapped view should capture clicks initially.
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            OverlappingActivity overlappingActivity =
                                    (OverlappingActivity) activity;
                            assertThat(overlappingActivity.getClickCount()).isEqualTo(0);
                            View view = activity.findViewById(R.id.overlapped_view);
                            int[] location = new int[2];
                            view.getLocationOnScreen(location);
                            injectClick(location[0] + 100, location[1] + 100);
                        });
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            OverlappingActivity overlappingActivity =
                                    (OverlappingActivity) activity;
                            assertThat(overlappingActivity.getClickCount()).isEqualTo(1);
                        });
        // Render remote view, and ensure it catches the click.
        renderAndVerifyView(R.id.overlapping_rendered_view, WIDTH_PX, HEIGHT_PX);
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            try {
                                SurfaceView surfaceView =
                                        activity.findViewById(R.id.overlapping_rendered_view);
                                int[] location = new int[2];
                                surfaceView.getLocationOnScreen(location);
                                assertThat(mUiProvider.wasViewClicked()).isFalse();
                                injectClick(location[0] + 100, location[1] + 100);
                                assertThat(mUiProvider.wasViewClicked()).isTrue();
                            } catch (RemoteException e) {
                                fail("Could not communicate with UIProvider: " + e);
                            }
                        });
        // Click has been caught by remote view, so the click count for the overlapped view should
        // not increment.
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            OverlappingActivity overlappingActivity =
                                    (OverlappingActivity) activity;
                            assertThat(overlappingActivity.getClickCount()).isEqualTo(1);
                        });
    }

    /** Injects a click into (x, y) screen coordinates. */
    private void injectClick(int x, int y) {
        MotionEvent downEvent =
                MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_DOWN,
                        x,
                        y,
                        0);
        MotionEvent upEvent =
                MotionEvent.obtain(
                        SystemClock.uptimeMillis(),
                        SystemClock.uptimeMillis(),
                        MotionEvent.ACTION_UP,
                        x,
                        y,
                        0);
        mUiAutomation.injectInputEvent(downEvent, false, false);
        mUiAutomation.injectInputEvent(upEvent, false, false);
    }

    /**
     * Renders a remote view of a given {@code width} and {@code height} in pixels into {@code
     * viewResourceId}, and asserts that the view is rendered correctly.
     */
    private void renderAndVerifyView(int viewResourceId, int width, int height) throws Exception {
        mUiTestRule.renderInView(viewResourceId, width, height);
        verifyView(viewResourceId, width, height);
    }

    private void verifyView(int viewResourceId, int width, int height) throws Exception {
        Thread.sleep(RENDERING_DELAY_MS);
        mUiTestRule
                .getActivityScenario()
                .onActivity(
                        activity -> {
                            SurfaceView view = activity.findViewById(viewResourceId);
                            int[] location = new int[2];
                            view.getLocationOnScreen(location);
                            mUiTestRule.assertMatches(
                                    location[0], location[1], width, height, IMG_IDENTIFIER);
                        });
    }
}
