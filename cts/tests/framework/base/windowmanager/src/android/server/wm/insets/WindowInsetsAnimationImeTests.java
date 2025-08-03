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

package android.server.wm.insets;

import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.graphics.Insets.NONE;
import static android.view.WindowInsets.Type.ime;
import static android.view.WindowInsets.Type.statusBars;

import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;

import static org.junit.Assert.assertEquals;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.withSettings;

import android.content.pm.PackageManager;
import android.platform.test.annotations.Presubmit;
import android.server.wm.ActivityManagerTestBase;
import android.server.wm.MockImeHelper;
import android.server.wm.WindowManagerState;
import android.server.wm.WindowManagerTestBase;
import android.view.WindowInsets;

import androidx.test.filters.FlakyTest;

import com.android.cts.mockime.MockIme;

import org.junit.Before;
import org.junit.Test;
import org.mockito.InOrder;

/**
 * Same as {@link WindowInsetsAnimationTests} but IME specific.
 *
 * <p>Build/Install/Run: atest CtsWindowManagerDeviceInsets:WindowInsetsAnimationImeTests
 */
@Presubmit
@android.server.wm.annotation.Group2
public class WindowInsetsAnimationImeTests extends WindowInsetsAnimationTestBase {

    private static final int KEYBOARD_HEIGHT = 600;

    @Before
    public void setup() throws Exception {
        super.setUp();
        assumeFalse(
                "Automotive is to skip this test until showing and hiding certain insets "
                        + "simultaneously in a single request is supported",
                mInstrumentation
                        .getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE));
        assumeTrue(
                "MockIme cannot be used for devices that do not support installable IMEs",
                mInstrumentation
                        .getContext()
                        .getPackageManager()
                        .hasSystemFeature(PackageManager.FEATURE_INPUT_METHODS));
    }

    private void initActivity(boolean useFloating) {
        MockImeHelper.createManagedMockImeSession(this, KEYBOARD_HEIGHT, useFloating);

        // The existing IME will be replaced by MockIME. Wait for the new IME window.
        mWmState.waitFor(
                "MockIme must be ready.",
                wms -> {
                    final WindowManagerState.WindowState ime = wms.getInputMethodWindowState();
                    return ime != null
                            && ime.getPackageName().equals(MockIme.class.getPackageName());
                });

        mActivity =
                WindowManagerTestBase.startActivityInWindowingMode(
                        TestActivity.class, WINDOWING_MODE_FULLSCREEN);
        mRootView = mActivity.getWindow().getDecorView();
    }

    @FlakyTest(bugId = 293267558)
    @Test
    public void testImeAnimationCallbacksShowAndHide() {
        initActivity(false /* useFloating */);
        testShowAndHide();
    }

    @Test
    public void testAnimationCallbacks_overlapping_opposite() {
        initActivity(false /* useFloating */);
        assumeTrue(hasWindowInsets(mRootView, statusBars()));

        WindowInsets before = mActivity.mLastWindowInsets;

        MultiAnimCallback callbackInner = new MultiAnimCallback();
        MultiAnimCallback callback =
                mock(
                        MultiAnimCallback.class,
                        withSettings()
                                .spiedInstance(callbackInner)
                                .defaultAnswer(CALLS_REAL_METHODS)
                                .verboseLogging());
        mActivity.mView.setWindowInsetsAnimationCallback(callback);

        getInstrumentation()
                .runOnMainSync(() -> mRootView.getWindowInsetsController().hide(statusBars()));
        getInstrumentation().runOnMainSync(() -> mRootView.getWindowInsetsController().show(ime()));

        ActivityManagerTestBase.waitForOrFail(
                "Waiting until IME animation starts", () -> callback.imeAnimStarted);
        ActivityManagerTestBase.waitForOrFail(
                "Waiting until animation done", () -> callback.runningAnims.isEmpty());

        WindowInsets after = mActivity.mLastWindowInsets;

        // When system bar and IME are animated together, order of events cannot be predicted
        // relative to one another: especially the end since animation durations are different.
        // Use individual inOrder for each.
        InOrder inOrderBar = inOrder(callback, mActivity.mListener);
        InOrder inOrderIme = inOrder(callback, mActivity.mListener);

        inOrderBar.verify(callback).onPrepare(eq(callback.statusBarAnim));

        inOrderIme
                .verify(mActivity.mListener)
                .onApplyWindowInsets(
                        any(),
                        argThat(
                                argument ->
                                        NONE.equals(argument.getInsets(statusBars()))
                                                && NONE.equals(argument.getInsets(ime()))));

        inOrderBar
                .verify(callback)
                .onStart(
                        eq(callback.statusBarAnim),
                        argThat(
                                argument ->
                                        argument.getLowerBound().equals(NONE)
                                                && argument.getUpperBound()
                                                        .equals(before.getInsets(statusBars()))));

        inOrderIme.verify(callback).onPrepare(eq(callback.imeAnim));
        inOrderIme
                .verify(mActivity.mListener)
                .onApplyWindowInsets(any(), eq(mActivity.mLastWindowInsets));

        inOrderIme
                .verify(callback)
                .onStart(
                        eq(callback.imeAnim),
                        argThat(
                                argument ->
                                        argument.getLowerBound().equals(NONE)
                                                && !argument.getUpperBound().equals(NONE)));

        inOrderBar.verify(callback).onEnd(eq(callback.statusBarAnim));
        inOrderIme.verify(callback).onEnd(eq(callback.imeAnim));

        assertAnimationSteps(callback.statusAnimSteps, false /* showAnimation */);
        assertAnimationSteps(callback.imeAnimSteps, true /* showAnimation */, ime());

        assertEquals(
                before.getInsets(statusBars()),
                callback.statusAnimSteps.get(0).insets.getInsets(statusBars()));
        assertEquals(
                after.getInsets(statusBars()),
                callback.statusAnimSteps
                        .get(callback.statusAnimSteps.size() - 1)
                        .insets
                        .getInsets(statusBars()));

        assertEquals(before.getInsets(ime()), callback.imeAnimSteps.get(0).insets.getInsets(ime()));
        assertEquals(
                after.getInsets(ime()),
                callback.imeAnimSteps
                        .get(callback.imeAnimSteps.size() - 1)
                        .insets
                        .getInsets(ime()));
    }

    @FlakyTest(bugId = 297381114)
    @Test
    public void testZeroInsetsImeAnimates() {
        initActivity(true /* useFloating */);
        testShowAndHide();
    }

    private void testShowAndHide() {
        WindowInsets before = mActivity.mLastWindowInsets;
        getInstrumentation().runOnMainSync(() -> mRootView.getWindowInsetsController().show(ime()));

        ActivityManagerTestBase.waitForOrFail(
                "Waiting until animation done", () -> mActivity.mCallback.animationDone);
        commonAnimationAssertions(mActivity, before, true /* show */, ime());

        mActivity.resetAnimationDone();

        before = mActivity.mLastWindowInsets;

        getInstrumentation().runOnMainSync(() -> mRootView.getWindowInsetsController().hide(ime()));

        ActivityManagerTestBase.waitForOrFail(
                "Waiting until animation done", () -> mActivity.mCallback.animationDone);

        commonAnimationAssertions(mActivity, before, false /* show */, ime());
    }
}
