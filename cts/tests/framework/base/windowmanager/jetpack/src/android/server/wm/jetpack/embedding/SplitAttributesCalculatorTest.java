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

package android.server.wm.jetpack.embedding;

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.DEFAULT_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EXPAND_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.HINGE_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;

import static com.android.compatibility.common.util.PollingCheck.waitFor;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assume.assumeTrue;

import android.app.Activity;
import android.app.PictureInPictureParams;
import android.content.Intent;
import android.server.wm.NestedShellPermission;
import android.server.wm.RotationSession;
import android.server.wm.TestTaskOrganizer;
import android.server.wm.jetpack.utils.TestActivityWithId;

import androidx.annotation.NonNull;
import androidx.window.extensions.core.util.function.Function;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributesCalculatorParams;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.Collections;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

/**
 * Tests to verify the behaviors of
 * {@link ActivityEmbeddingComponent#setSplitAttributesCalculator(Function)} and
 * {@link ActivityEmbeddingComponent#clearSplitAttributesCalculator}
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:SplitAttributesCalculatorTest
 */
@ApiTest(apis = {
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent"
                + "#setSplitAttributesCalculator",
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent"
                + "#clearSplitAttributesCalculator"})
public class SplitAttributesCalculatorTest extends ActivityEmbeddingTestBase {
    private static final String ACTIVITY_A_ID = "activityA";
    private static final String ACTIVITY_B_ID = "activityB";

    /**
     * Verifies whether setting and clearing splitAttributes calculator function is expected.
     */
    @Test
    public void testSetAndClearSplitAttributesCalculator() {
        final String activityCId = "activityC";
        final String activityDId = "activityD";
        final String tag = "testSetAndClearSplitAttributesCalculator";

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(activityA, TestActivityWithId.class, splitPairRule,
                ACTIVITY_B_ID, mSplitInfoConsumer);


        // Choose a split attributes that different from the default one.
        final SplitAttributes customizedSplitAttributes = new SplitAttributes.Builder()
                .setSplitType(new SplitAttributes.SplitType.RatioSplitType(0.7f))
                .build();
        Function<SplitAttributesCalculatorParams, SplitAttributes> calculator = params -> {
            // Only make the customized split attributes apply to the split rule with test tag in
            // case other tests are affected.
            if (tag.equals(params.getSplitRuleTag())) {
                return customizedSplitAttributes;
            }
            // Otherwise, keep the default behavior.
            if (!params.areDefaultConstraintsSatisfied()) {
                return EXPAND_SPLIT_ATTRS;
            }
            return params.getDefaultSplitAttributes();
        };
        mActivityEmbeddingComponent.setSplitAttributesCalculator(calculator);

        // Start another Activity to trigger the calculator function, and verify if the split pair
        // matches the customized split attributes.
        startActivityAndVerifySplitAttributes(activityA, activityA, /* expectedPrimaryActivity */
                TestActivityWithId.class, customizedSplitAttributes, activityCId,
                1 /* expectedCallbackCount */, mSplitInfoConsumer);

        // Clear the calculator function, then the split pair should align the default split pair
        // rule behavior.
        mActivityEmbeddingComponent.clearSplitAttributesCalculator();

        // Launch Activity D to apply the change.
        startActivityAndVerifySplitAttributes(activityA,
                activityA, /* expectedPrimaryActivity */ TestActivityWithId.class,
                splitPairRule, activityDId, 1, /* expectedCallbackCount */
                mSplitInfoConsumer);
    }

    /**
     * Verifies the behavior to use splitAttributes calculator function to customize a split pair
     * to expand split type.
     */
    @ApiTest(apis = "androidx.window.extensions.embedding.SplitAttributes"
            + ".ExpandContainersSplitType")
    @Test
    public void testCalculatorSplitAttributesCustomization_expand() {
        testSplitAttributesCustomizationByCalculator(EXPAND_SPLIT_ATTRS);
    }

    /**
     * Verifies the behavior to use splitAttributes calculator function to customize a split pair
     * to hinge split type.
     */
    @ApiTest(apis = "androidx.window.extensions.embedding.SplitAttributes.HingeSplitType")
    @Test
    public void testCalculatorSplitAttributesCustomization_hinge() {
        testSplitAttributesCustomizationByCalculator(HINGE_SPLIT_ATTRS);
    }

    private void testSplitAttributesCustomizationByCalculator(
            @NonNull SplitAttributes customizedSplitAttributes) {
        final String tag = "testSplitAttributesCustomizationByCalculator"
                + customizedSplitAttributes;
        Function<SplitAttributesCalculatorParams, SplitAttributes> calculator = params -> {
            // Only make the customized split attributes apply to the split rule with test tag in
            // case other tests are affected.
            if (tag.equals(params.getSplitRuleTag())) {
                return customizedSplitAttributes;
            }
            // Otherwise, keep the default behavior.
            if (!params.areDefaultConstraintsSatisfied()) {
                return EXPAND_SPLIT_ATTRS;
            }
            return params.getDefaultSplitAttributes();
        };
        mActivityEmbeddingComponent.setSplitAttributesCalculator(calculator);

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // customizedSplitAttributes.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);

        startActivityAndVerifySplitAttributes(activityA, activityA, /* expectedPrimaryActivity */
                TestActivityWithId.class, customizedSplitAttributes, ACTIVITY_B_ID,
                1 /* expectedCallbackCount */, mSplitInfoConsumer);
    }

    /** Verify the calculator function is called when the device is rotated.  */
    @Test
    public void testSplitAttributesCalculatorInvocation_screenRotation()
            throws InterruptedException {
        final String tag = "testSplitAttributesCalculatorInvocation_screenRotation";
        final InvocationVerifier verifier = new InvocationVerifier(tag);

        // Set the calculator function before the split pair launch.
        mActivityEmbeddingComponent.setSplitAttributesCalculator(verifier);

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(activityA, TestActivityWithId.class, splitPairRule,
                ACTIVITY_B_ID, mSplitInfoConsumer);

        verifier.waitAndAssertFunctionApplied("The calculator function must be called due to"
                + " split pair launch");

        if (!doesDeviceSupportRotation()) {
            // Stop the test here if the device doesn't support rotation.
            return;
        }

        try (RotationSession rotationSession = new RotationSession()) {
            final int initialRotation = activityA.getDisplay().getRotation();
            for (int i = 1; i <= 3; i++) {
                // Rotate the device by 90 degree clockwise.
                final int rotation = (initialRotation + i) % 4;
                rotationSession.set(rotation);

                verifier.waitAndAssertFunctionApplied("The calculator function must be called for"
                        + " rotation:" + rotation);
            }
        }
    }

    /** Verify the calculator function is called when the activity leaves PIP.  */
    @Test
    public void testSplitAttributesCalculatorInvocation_pip() throws InterruptedException {
        assumeTrue(supportsPip());

        final String tag = "testSplitAttributesCalculatorInvocation_screenRotation";
        final InvocationVerifier verifier = new InvocationVerifier(tag);

        // Set the calculator function before the split pair launch.
        mActivityEmbeddingComponent.setSplitAttributesCalculator(verifier);

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(activityA, TestActivityWithId.class, splitPairRule,
                ACTIVITY_B_ID, mSplitInfoConsumer);

        verifier.waitAndAssertFunctionApplied("The calculator function must be called due to"
                + " split pair launch.");

        activityA.enterPictureInPictureMode(new PictureInPictureParams.Builder().build());

        waitFor(activityA::isInPictureInPictureMode);
        // TODO(b/275526710): find a way to verify calculator is not called without waiting for
        //  2 secs.
        verifier.assertFunctionNotApplied("The calculator function must not be called because"
                + " activity A enter PIP.");

        // launch Activity A again to leave the activity from PIP.
        activityA.startActivity(new Intent(activityA.getIntent()));

        verifier.waitAndAssertFunctionApplied("The calculator function must be called because"
                + " activity A leaves PIP.");
    }

    /** Verify the calculator function is called when the host task enters/leaves split screen. */
    @Test
    public void testSplitAttributesCalculatorInvocation_splitScreen() throws InterruptedException {
        final String tag = "testSplitAttributesCalculatorInvocation_screenRotation";
        final InvocationVerifier verifier = new InvocationVerifier(tag);

        // Set the calculator function before the split pair launch.
        mActivityEmbeddingComponent.setSplitAttributesCalculator(verifier);

        // Create a split rule for activity A and activity B where the split ratio is 0.5.
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // defaultSplitAttributes.
        final Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        startActivityAndVerifySplitAttributes(activityA, TestActivityWithId.class, splitPairRule,
                ACTIVITY_B_ID, mSplitInfoConsumer);

        verifier.waitAndAssertFunctionApplied("The calculator function must be called due to"
                + " split pair launch.");

        // Make the host task go to split screen.
        final TestTaskOrganizer[] taskOrganizer = new TestTaskOrganizer[1];
        NestedShellPermission.run(() -> taskOrganizer[0] = new TestTaskOrganizer());
        taskOrganizer[0].putTaskInSplitPrimary(activityA.getTaskId());

        verifier.waitAndAssertFunctionApplied("The calculator function must be called because"
                + " the host task enters to split screen");

        taskOrganizer[0].dismissSplitScreen(true /* primaryOnTop */);

        verifier.waitAndAssertFunctionApplied("The calculator function must be called because"
                + " the host task leaves to split screen");
    }

    private static class InvocationVerifier
            implements Function<SplitAttributesCalculatorParams, SplitAttributes> {

        private static final int TIMEOUT_IN_SECONDS = 10;
        private static final int FUNCTION_NOT_CALLED_TIMEOUT_IN_SECONDS = 2;

        private final String mSplitRuleTagToVerify;
        private CountDownLatch mLatch = new CountDownLatch(1);

        InvocationVerifier(@NonNull String tag) {
            mSplitRuleTagToVerify = tag;
        }

        @Override
        public SplitAttributes apply(SplitAttributesCalculatorParams params) {
            // Keep the default split pair rule behavior, and restore the properties.
            final SplitAttributes splitAttributes = params.areDefaultConstraintsSatisfied()
                    ? params.getDefaultSplitAttributes()
                    : EXPAND_SPLIT_ATTRS;

            if (mSplitRuleTagToVerify.equals(params.getSplitRuleTag())) {
                mLatch.countDown();
            }
            return splitAttributes;
        }

        private void waitAndAssertFunctionApplied(String message) throws InterruptedException {
            assertTrue(message, mLatch.await(TIMEOUT_IN_SECONDS, TimeUnit.SECONDS));
            // reset count for the next verification.
            mLatch = new CountDownLatch(1);
        }

        private void assertFunctionNotApplied(String message) throws InterruptedException {
            assertFalse(message, mLatch.await(FUNCTION_NOT_CALLED_TIMEOUT_IN_SECONDS,
                    TimeUnit.SECONDS));
        }
    }
}
