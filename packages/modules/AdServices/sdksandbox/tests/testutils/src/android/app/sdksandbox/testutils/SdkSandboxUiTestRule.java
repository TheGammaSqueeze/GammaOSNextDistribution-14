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

package android.app.sdksandbox.testutils;

import static android.app.sdksandbox.SdkSandboxManager.EXTRA_DISPLAY_ID;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HEIGHT_IN_PIXELS;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_HOST_TOKEN;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_WIDTH_IN_PIXELS;

import static com.google.common.truth.Truth.assertThat;

import android.app.Activity;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.content.Context;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.view.SurfaceView;
import android.view.View;

import androidx.lifecycle.Lifecycle;
import androidx.test.core.app.ActivityScenario;
import androidx.test.runner.screenshot.Screenshot;

import org.junit.rules.ExternalResource;
import org.junit.rules.RuleChain;
import org.junit.rules.TestRule;
import org.junit.runner.Description;
import org.junit.runners.model.Statement;

import platform.test.screenshot.DeviceEmulationRule;
import platform.test.screenshot.DeviceEmulationSpec;
import platform.test.screenshot.DisplaySpec;
import platform.test.screenshot.GoldenImagePathManager;
import platform.test.screenshot.GoldenImagePathManagerKt;
import platform.test.screenshot.PathConfig;
import platform.test.screenshot.ScreenshotAsserter;
import platform.test.screenshot.ScreenshotRuleAsserter;
import platform.test.screenshot.ScreenshotTestRule;
import platform.test.screenshot.matchers.AlmostPerfectMatcher;
import platform.test.screenshot.matchers.BitmapMatcher;

/**
 * A {@link TestRule} used for handling SDK sandbox UI tests.
 *
 * <p>This class handles setting up a {@link DeviceEmulationRule} to handle device emulation, as
 * well as the screenshot testing infrastructure. The given {@link Activity} will be started, and an
 * SDK will be loaded as part of this test rule. The SDK will be unloaded on completion of each test
 * case.
 *
 * <p>Individual test cases may interact with the test activity using {@link
 * #getActivityScenario()}, and may render a view inside a {@link SurfaceView} with {@link
 * #renderInView(int)}. A test may assert that a view has rendered correctly using {@link
 * #assertMatches(int, int, int, int, String)}.
 */
public class SdkSandboxUiTestRule implements TestRule {

    private static final String ASSETS_DIR = "assets";
    // TODO(b/268204038): Support different emulation configurations.
    private static final int EMULATION_WIDTH = 1080;
    private static final int EMULATION_HEIGHT = 2340;
    private static final int EMULATION_DPI = 432;

    private final ScreenshotTestRule mScreenshotTestRule;
    private final LoadSdkInSandboxRule mLoadSdkInSandboxRule;
    private final RuleChain mDelegateRule;

    public SdkSandboxUiTestRule(Context context, Class activityClass, String sdkName) {
        GoldenImagePathManager pathManager =
                new GoldenImagePathManager(
                        context,
                        ASSETS_DIR,
                        GoldenImagePathManagerKt.getDeviceOutputDirectory(context),
                        new PathConfig());
        mScreenshotTestRule = new ScreenshotTestRule(pathManager);
        mLoadSdkInSandboxRule = new LoadSdkInSandboxRule(context, activityClass, sdkName);
        DeviceEmulationRule deviceEmulationRule =
                new DeviceEmulationRule(
                        new DeviceEmulationSpec(
                                new DisplaySpec(
                                        "default",
                                        EMULATION_WIDTH,
                                        EMULATION_HEIGHT,
                                        EMULATION_DPI),
                                false,
                                false));
        mDelegateRule =
                RuleChain.outerRule(deviceEmulationRule)
                        .around(mScreenshotTestRule)
                        .around(mLoadSdkInSandboxRule);
    }

    @Override
    public Statement apply(Statement base, Description description) {
        return mDelegateRule.apply(base, description);
    }

    /**
     * Asserts that a View with top-left coordinates (x,y) and a given {@code width} and {@code
     * height}, matches the content of an asset identified by {@code identifier}.
     */
    public void assertMatches(int x, int y, int width, int height, String identifier) {
        // Use an AlmostPerfectMatcher to perform a small degree of tolerance to allow for rendering
        // differences.
        BitmapMatcher matcher = new AlmostPerfectMatcher();
        ScreenshotAsserter asserter =
                new ScreenshotRuleAsserter.Builder(mScreenshotTestRule)
                        .withMatcher(matcher)
                        .setScreenshotProvider(
                                () ->
                                        Bitmap.createBitmap(
                                                Screenshot.capture().getBitmap(),
                                                x,
                                                y,
                                                width,
                                                height))
                        .build();
        asserter.assertGoldenImage(identifier);
    }

    public ActivityScenario getActivityScenario() {
        return mLoadSdkInSandboxRule.getActivityScenario();
    }

    /**
     * Renders a remote view with a given {@code width} and {@code height} into the view with a
     * resource ID {@code viewResId}.
     */
    public void renderInView(int viewResId, int width, int height) {
        mLoadSdkInSandboxRule.renderInView(viewResId, width, height);
    }

    public void switchActivity(Class activityClass) {
        mLoadSdkInSandboxRule.switchActivity(activityClass);
    }

    public SandboxedSdk getSandboxedSdk() {
        return mLoadSdkInSandboxRule.getSandboxedSdk();
    }

    private static class LoadSdkInSandboxRule extends ExternalResource {
        final Class mActivityClass;
        final SdkSandboxManager mSdkSandboxManager;
        final String mSdkName;
        ActivityScenario mActivityScenario;
        SandboxedSdk mSandboxedSdk;

        LoadSdkInSandboxRule(Context context, Class activityClass, String sdkName) {
            mActivityClass = activityClass;
            mSdkSandboxManager = context.getSystemService(SdkSandboxManager.class);
            mSdkName = sdkName;
        }

        @Override
        protected void before() {
            mActivityScenario = ActivityScenario.launch(mActivityClass);
            assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.RESUMED);
            mActivityScenario.onActivity(
                    activity -> {
                        FakeLoadSdkCallback callback = new FakeLoadSdkCallback();
                        mSdkSandboxManager.loadSdk(mSdkName, new Bundle(), Runnable::run, callback);
                        callback.assertLoadSdkIsSuccessful();
                        mSandboxedSdk = callback.getSandboxedSdk();
                    });
        }

        @Override
        protected void after() {
            mActivityScenario.onActivity(
                    activity -> {
                        mSdkSandboxManager.unloadSdk(mSdkName);
                    });
            if (mActivityScenario != null) {
                mActivityScenario.close();
            }
        }

        private ActivityScenario getActivityScenario() {
            return mActivityScenario;
        }

        private void switchActivity(Class activityClass) {
            mActivityScenario = ActivityScenario.launch(activityClass);
            assertThat(mActivityScenario.getState()).isEqualTo(Lifecycle.State.RESUMED);
        }

        private SandboxedSdk getSandboxedSdk() {
            return mSandboxedSdk;
        }

        private void renderInView(int viewResId, int width, int height) {
            mActivityScenario.onActivity(
                    activity -> {
                        SurfaceView view = activity.findViewById(viewResId);
                        FakeRequestSurfacePackageCallback rspCallback =
                                new FakeRequestSurfacePackageCallback();
                        mSdkSandboxManager.requestSurfacePackage(
                                mSdkName,
                                createRequestSurfacePackageBundle(
                                        view, activity.getDisplayId(), width, height),
                                Runnable::run,
                                rspCallback);
                        assertThat(rspCallback.isRequestSurfacePackageSuccessful()).isTrue();
                        view.setChildSurfacePackage(rspCallback.getSurfacePackage());
                        view.setVisibility(View.VISIBLE);
                        view.setZOrderOnTop(true);
                    });
        }

        private Bundle createRequestSurfacePackageBundle(
                SurfaceView renderedView, int displayId, int width, int height) {
            Bundle params = new Bundle();
            params.putInt(EXTRA_WIDTH_IN_PIXELS, width);
            params.putInt(EXTRA_HEIGHT_IN_PIXELS, height);
            params.putInt(EXTRA_DISPLAY_ID, displayId);
            params.putBinder(EXTRA_HOST_TOKEN, renderedView.getHostToken());
            return params;
        }
    }
}
