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
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndGetTaskBounds;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributes.LayoutDirection;
import androidx.window.extensions.embedding.SplitAttributes.SplitType;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.Set;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * split bounds.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingBoundsTests
 */
@ApiTest(apis = {"androidx.window.extensions.embedding.SplitPairRule#getDefaultSplitAttributes"})
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingBoundsTests extends ActivityEmbeddingTestBase {
    public static SplitType UNEVEN_CONTAINERS_DEFAULT_SPLIT_TYPE =
            new SplitType.RatioSplitType(0.7f);

    /**
     * Tests that when two activities are in a split and the parent bounds shrink such that
     * they can no longer support split activities, then the activities become stacked.
     */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitRule#checkParentMetrics"})
    @Test
    public void testParentWindowMetricsPredicate() {
        // Launch primary activity
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Set split pair rule such that if the parent bounds is any smaller than it is now, then
        // the parent cannot support a split.
        final Rect taskBounds = waitAndGetTaskBounds(primaryActivity,
                true /* shouldWaitForResume */);
        final int originalTaskWidth = taskBounds.width();
        final int originalTaskHeight = taskBounds.height();
        final SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> parentWindowMetrics.getBounds().width() >= originalTaskWidth
                        && parentWindowMetrics.getBounds().height() >= originalTaskHeight)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the secondary activity
        final String secondaryActivityId = "secondaryActivityId";
        final TestActivity secondaryActivity = (TestActivity) startActivityAndVerifySplitAttributes(
                primaryActivity, TestActivityWithId.class, splitPairRule, secondaryActivityId,
                mSplitInfoConsumer);

        // Resize the display multiple times to verify that the activities are correctly split or
        // stacked depending on the parent bounds. Resizing multiple times simulates a foldable
        // display is that folded and unfolded multiple times while running the same app.
        final int numTimesToResize = 2;
        final Size originalDisplaySize = mReportedDisplayMetrics.getSize();
        for (int i = 0; i < numTimesToResize; i++) {
            // Shrink the display by 10% to make the activities stacked
            mReportedDisplayMetrics.setSize(new Size((int) (originalDisplaySize.getWidth() * 0.9),
                    (int) (originalDisplaySize.getHeight() * 0.9)));
            waitAndAssertResumedAndFillsTask(secondaryActivity);
            waitAndAssertNotVisible(primaryActivity);

            // Return the display to its original size and verify that the activities are split
            mReportedDisplayMetrics.setSize(originalDisplaySize);
            mInstrumentation.waitForIdleSync();
            assertValidSplit(primaryActivity, secondaryActivity, splitPairRule);
        }
    }

    /**
     * Tests that the activity bounds for activities in a split match the LTR layout direction
     * provided in the {@link SplitPairRule}.
     */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".LayoutDirection#LEFT_TO_RIGHT"})
    @Test
    public void testLayoutDirection_LeftToRight() {
        // Create a split pair rule with layout direction LEFT_TO_RIGHT and a split ratio that
        // results in uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(
                LayoutDirection.LEFT_TO_RIGHT);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is LEFT_TO_RIGHT,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that the activity bounds for activities in a split match the RTL layout direction
     * provided in the {@link SplitPairRule}.
     */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".LayoutDirection#RIGHT_TO_LEFT"})
    @Test
    public void testLayoutDirection_RightToLeft() {
        // Create a split pair rule with layout direction RIGHT_TO_LEFT and a split ratio that
        // results in uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(
                LayoutDirection.RIGHT_TO_LEFT);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is RIGHT_TO_LEFT,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that the activity bounds for activities in a split match the Locale layout direction
     * provided in the {@link SplitPairRule}.
     */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".LayoutDirection#LOCALE"})
    @Test
    public void testLayoutDirection_Locale() {
        // Create a split pair rule with layout direction LOCALE and a split ratio that results in
        // uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(LayoutDirection.LOCALE);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is the device locale,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".LayoutDirection#TOP_TO_BOTTOM"})
    @Test
    public void testLayoutDirection_TopToBottom() {
        // Create a split pair rule with layout direction TOP_TO_BOTTOM and a split ratio that
        // results in uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(
                LayoutDirection.TOP_TO_BOTTOM);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is TOP_TO_BOTTOM,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".LayoutDirection#BOTTOM_TO_TOP"})
    @Test
    public void testLayoutDirection_BottomToTop() {
        // Create a split pair rule with layout direction BOTTOM_TO_TOP and a split ratio that
        // results in uneven bounds between the primary and secondary containers.
        final SplitPairRule splitPairRule = createUnevenWidthSplitPairRule(
                LayoutDirection.TOP_TO_BOTTOM);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the layout direction is BOTTOM_TO_TOP,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    /**
     * Tests that when two activities enter a split, then their split ratio matches what is in their
     * {@link SplitPairRule}, and is not assumed to be 0.5 or match the split ratio of the previous
     * top-most activity split.
     */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".SplitType.RatioSplitType#getRatio"})
    @Test
    public void testSplitRatio() {
        final String activityAId = "activityA";
        final String activityBId = "activityB";
        final String activityCId = "activityC";
        final SplitType activityABSplitRatio = new SplitType.RatioSplitType(0.37f);
        final SplitType activityBCSplitRatio = new SplitType.RatioSplitType(0.85f);

        // Create a split rule for activity A and activity B where the split ratio is 0.37.
        final SplitPairRule splitPairRuleAB = createSplitPairRuleBuilder(
                activityActivityPair -> false /* activityPairPredicate */,
                activityIntentPair -> matchesActivityIntentPair(activityIntentPair, activityAId,
                        activityBId) /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(new SplitAttributes.Builder().setSplitType(
                        activityABSplitRatio).build())
                .build();

        // Create a split rule for activity B and activity C where the split ratio is 0.65.
        final SplitPairRule splitPairRuleBC = createSplitPairRuleBuilder(
                activityActivityPair -> false /* activityPairPredicate */,
                activityIntentPair -> matchesActivityIntentPair(activityIntentPair, activityBId,
                        activityCId) /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(new SplitAttributes.Builder().setSplitType(
                        activityBCSplitRatio).build())
                .build();

        // Register the two split pair rules
        mActivityEmbeddingComponent.setEmbeddingRules(Set.of(splitPairRuleAB, splitPairRuleBC));

        // Launch the activity A and B split and verify that the split ratio is 0.37 in
        // {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class, activityAId);
        Activity activityB = startActivityAndVerifySplitAttributes(activityA,
                TestActivityWithId.class, splitPairRuleAB, activityBId, mSplitInfoConsumer);

        // Launch the activity B and C split and verify that the split ratio is 0.65 in
        // {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity activityC = startActivityAndVerifySplitAttributes(activityB,
                TestActivityWithId.class, splitPairRuleBC, activityCId, mSplitInfoConsumer);

        // Finish activity C so that activity A and B are in a split again. Verify that the split
        // ratio returns to 0.37 in {@link ActivityEmbeddingUtil#assertValidSplit}.
        activityC.finish();
        assertValidSplit(activityA, activityB, splitPairRuleAB);
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes.HingeSplitType"
            + "#HingeSplitType"})
    @Test
    public void testHingeSplitType() {
        TestConfigChangeHandlingActivity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true,
                activityIntentPair -> true,
                windowMetrics -> true)
                .setDefaultSplitAttributes(HINGE_SPLIT_ATTRS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start another activity to split with the primary activity and verify that the split type
        // is hinge.
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    /** Verifies {@link SplitAttributes.SplitType.ExpandContainersSplitType} behavior. */
    @ApiTest(apis = {"androidx.window.extensions.embedding.SplitAttributes"
            + ".ExpandContainersSplitType#ExpandContainersSplitType"})
    @Test
    public void testExpandSplitType() {
        SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true,
                activityIntentPair -> true,
                windowMetrics -> true
        )
                .setDefaultSplitAttributes(EXPAND_SPLIT_ATTRS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Start activities in a split and verify that the split type is expand,
        // which is checked in {@link ActivityEmbeddingUtil#startActivityAndVerifySplit}.
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivityId", mSplitInfoConsumer);
    }

    private SplitPairRule createUnevenWidthSplitPairRule(int layoutDir) {
        return createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(new SplitAttributes.Builder()
                        .setSplitType(UNEVEN_CONTAINERS_DEFAULT_SPLIT_TYPE)
                        .setLayoutDirection(layoutDir)
                        .build())
                .build();
    }

    static boolean matchesActivityIntentPair(@NonNull Pair<Activity, Intent> activityIntentPair,
            @NonNull String primaryActivityId, @NonNull String secondaryActivityId) {
        if (!(activityIntentPair.first instanceof TestActivityWithId)) {
            return false;
        }
        return primaryActivityId.equals(((TestActivityWithId) activityIntentPair.first).getId())
                && secondaryActivityId.equals(activityIntentPair.second.getStringExtra(
                KEY_ACTIVITY_ID));
    }
}
