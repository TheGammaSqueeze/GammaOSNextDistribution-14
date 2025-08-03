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

package android.accessibilityservice.cts;

import static android.accessibilityservice.MagnificationConfig.MAGNIFICATION_MODE_FULLSCREEN;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.homeScreenOrBust;
import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;

import static com.google.common.truth.Truth.assertThat;

import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityService;
import android.accessibilityservice.AccessibilityService.MagnificationController;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.MagnificationConfig;
import android.accessibilityservice.cts.activities.AccessibilityWindowQueryActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.graphics.Rect;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;

import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;
import com.android.compatibility.common.util.TestUtils;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Class for testing {@See FullScreenMagnificationController}.
 */
@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class FullScreenMagnificationControllerTest {

    /** Maximum timeout while waiting for a config to be updated */
    private static final int TIMEOUT_CONFIG_SECONDS = 15;
    private static final int BOUNDS_TOLERANCE = 1;

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private StubMagnificationAccessibilityService mService;

    private final ActivityTestRule<AccessibilityWindowQueryActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityWindowQueryActivity.class, false, false);

    private InstrumentedAccessibilityServiceTestRule<StubMagnificationAccessibilityService>
            mMagnificationAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            StubMagnificationAccessibilityService.class, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mMagnificationAccessibilityServiceRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() {
        sInstrumentation = androidx.test.platform.app.InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        AccessibilityServiceInfo info = sUiAutomation.getServiceInfo();
        info.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomation.setServiceInfo(info);
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mService = mMagnificationAccessibilityServiceRule.enableService();
    }

    @Test
    public void testActivityTransitions_fullscreenMagnifierMagnifying_zoomOut() throws Exception {
        // wait for the activity to be on screen
        launchActivityAndWaitForItToBeOnscreen(sInstrumentation, sUiAutomation, mActivityRule);

        zoomIn(/* scale= */ 2.0f);
        // transition to home screen
        homeScreenOrBust(sInstrumentation.getContext(), sUiAutomation);

        // we cannot identify if the always on feature flag is enabled here, so we cannot ensure if
        // the magnifier should deactivate itself when transitions. At least we can still verify
        // the fullscreen magnifier zooming out here.
        assertThat(currentScale()).isEqualTo(1f);
    }

    private void zoomIn(float scale) throws Exception {
        final MagnificationController controller = mService.getMagnificationController();
        final Rect rect = controller.getMagnificationRegion().getBounds();
        final float x = rect.centerX();
        final float y = rect.centerY();
        final AtomicBoolean setConfig = new AtomicBoolean();

        final MagnificationConfig config = new MagnificationConfig.Builder()
                .setMode(MAGNIFICATION_MODE_FULLSCREEN)
                .setScale(scale)
                .setCenterX(x)
                .setCenterY(y).build();

        mService.runOnServiceSync(() -> {
            setConfig.set(controller.setMagnificationConfig(config, false));
        });
        waitUntilMagnificationConfigEquals(controller, config);

        assertTrue("Failed to set config", setConfig.get());
    }

    private float currentScale() {
        final MagnificationController controller = mService.getMagnificationController();
        final MagnificationConfig config = controller.getMagnificationConfig();

        assertThat(config).isNotNull();

        return config.getScale();
    }

    private void waitUntilMagnificationConfigEquals(
            AccessibilityService.MagnificationController controller,
            MagnificationConfig config) throws Exception {
        TestUtils.waitUntil(
                "Failed to apply the config. expected: " + config + " , actual: "
                        + controller.getMagnificationConfig(), TIMEOUT_CONFIG_SECONDS,
                () -> {
                    final MagnificationConfig actualConfig = controller.getMagnificationConfig();
                    // If expected config activated is false, we just need to verify the activated
                    // value is the same. Otherwise, we need to check all the actual values are
                    // equal to the expected values.
                    if (config.isActivated()) {
                        return actualConfig.getMode() == config.getMode()
                                && actualConfig.isActivated() == config.isActivated()
                                && Float.compare(actualConfig.getScale(), config.getScale()) == 0
                                && (Math.abs(actualConfig.getCenterX() - config.getCenterX())
                                <= BOUNDS_TOLERANCE)
                                && (Math.abs(actualConfig.getCenterY() - config.getCenterY())
                                <= BOUNDS_TOLERANCE);
                    } else {
                        return actualConfig.isActivated() == config.isActivated();
                    }
                });
    }
}
