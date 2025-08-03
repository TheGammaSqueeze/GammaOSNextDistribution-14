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
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertValidSplit;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertFinishing;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndGetTaskBounds;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import static androidx.window.extensions.embedding.SplitRule.FINISH_NEVER;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.WindowManagerStateHelper;
import android.server.wm.jetpack.utils.TestActivity;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.util.Pair;
import android.util.Size;
import android.view.WindowMetrics;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.ActivityEmbeddingComponent;
import androidx.window.extensions.embedding.SplitPlaceholderRule;

import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Arrays;
import java.util.Collections;
import java.util.Optional;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the placeholders functionality within Activity Embedding. An activity
 * can provide a {@link SplitPlaceholderRule} to the {@link ActivityEmbeddingComponent} which will
 * enable the activity to launch directly into a split with the placeholder activity it is
 * configured to launch with.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingPlaceholderTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingPlaceholderTests extends ActivityEmbeddingTestBase {

    private static final String PRIMARY_ACTIVITY_ID = "primaryActivity";
    private static final String PLACEHOLDER_ACTIVITY_ID = "placeholderActivity";

    /**
     * Tests that an activity with a matching {@link SplitPlaceholderRule} is successfully able to
     * launch into a split with its placeholder.
     */
    @Test
    public void testPlaceholderLaunchesWithPrimaryActivity() {
        // Set embedding rules
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair = launchActivityWithPlaceholderAndVerifySplit(
                PRIMARY_ACTIVITY_ID, PLACEHOLDER_ACTIVITY_ID, splitPlaceholderRule);
        final Activity primaryActivity = activityPair.first;
        final Activity placeholderActivity = activityPair.second;

        // Finishing the primary activity and verify that the placeholder activity is also finishing
        primaryActivity.finish();
        waitAndAssertFinishing(placeholderActivity);
    }

    /**
     * Tests that when the parent window metrics predicate in a {@link SplitPlaceholderRule} does
     * not allow for a split on the current parent window metrics, then when an activity with a
     * placeholder rule is launched, the placeholder is not launched.
     */
    @Test
    public void testPlaceholderDoesNotLaunchWhenParentMetricsDoNotAllow() {
        // Set embedding rules where the parent window metrics do not allow for a placeholder
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID)
                        .setParentWindowMetrics(parentWindowMetrics -> false).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPlaceholderRule));

        // Launch the primary activity and verify that the placeholder activity was not launched and
        // the primary activity fills the task.
        Activity primaryActivity = startFullScreenActivityNewTask(TestActivityWithId.class,
                PRIMARY_ACTIVITY_ID);
        waitAndAssertNotResumed(PLACEHOLDER_ACTIVITY_ID);
        waitAndAssertResumedAndFillsTask(primaryActivity);
    }

    /**
     * Tests that when the placeholder activity is finished, then the activity it launched with is
     * also finished because the default value for finishPrimaryWithSecondary is
     * {@link androidx.window.extensions.embedding.SplitRule#FINISH_ALWAYS}.
     */
    @Test
    public void testFinishingPlaceholderFinishesPrimaryActivity() {
        // Set embedding rules
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair = launchActivityWithPlaceholderAndVerifySplit(
                PRIMARY_ACTIVITY_ID, PLACEHOLDER_ACTIVITY_ID, splitPlaceholderRule);
        final Activity primaryActivity = activityPair.first;
        final Activity placeholderActivity = activityPair.second;

        // Finish the placeholder activity and verify that the primary activity is also finishing
        placeholderActivity.finish();
        waitAndAssertFinishing(primaryActivity);
    }

    /**
     * Tests that when a placeholder activity that is created from a rule that sets
     * finishPrimaryWithSecondary to
     * {@link androidx.window.extensions.embedding.SplitRule#FINISH_NEVER} is finished, then the
     * activity it launched with is not finished.
     */
    @Test
    @Ignore("b/222188067")
    public void testPlaceholderFinishPrimaryWithSecondary_FinishNever() {
        // Set embedding rules with finishPrimaryWithSecondary set to FINISH_NEVER
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID).setFinishPrimaryWithSecondary(FINISH_NEVER)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair = launchActivityWithPlaceholderAndVerifySplit(
                PRIMARY_ACTIVITY_ID, PLACEHOLDER_ACTIVITY_ID, splitPlaceholderRule);
        final TestActivity primaryActivity = (TestActivity) activityPair.first;
        final Activity placeholderActivity = activityPair.second;

        // Finish the placeholder activity and verify that the primary activity does not finish
        // and fills the task.
        placeholderActivity.finish();
        waitAndAssertResumedAndFillsTask(primaryActivity);
    }

    /**
     * Tests that when the task width is decreased below the width that can support split
     * activities, then the placeholder activity is finished.
     */
    @Test
    public void testPlaceholderFinishedWhenTaskWidthDecreased() {
        final Rect taskBounds = getTaskBounds();
        final int taskWidth = taskBounds.width();
        final int taskHeight = taskBounds.height();

        // Set embedding rules with the parent window metrics only allowing side-by-side
        // activities on a task bounds at least the current bounds.
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID)
                        .setParentWindowMetrics(windowMetrics ->
                                windowMetrics.getBounds().width() >= taskWidth
                                        && windowMetrics.getBounds().height() >= taskHeight)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair =
                launchActivityWithPlaceholderAndVerifySplit(
                        PRIMARY_ACTIVITY_ID, PLACEHOLDER_ACTIVITY_ID, splitPlaceholderRule);
        final TestActivity primaryActivity = (TestActivity) activityPair.first;
        final Activity placeholderActivity = activityPair.second;

        // Shrink display size by 10% so that the primary and placeholder activities are stacked
        final Size currentSize = mReportedDisplayMetrics.getSize();
        mReportedDisplayMetrics.setSize(new Size((int) (currentSize.getWidth() * 0.9),
                (int) (currentSize.getHeight() * 0.9)));

        // Verify that the placeholder activity was finished and that the primary activity now
        // fills the task.
        waitAndAssertFinishing(placeholderActivity);
        waitAndAssertResumedAndFillsTask(primaryActivity);
    }

    /**
     * Tests that when the task width is increased to a width large enough to support a placeholder,
     * then a placeholder activity is launched.
     */
    @Test
    public void testPlaceholderLaunchedWhenTaskWidthIncreased() {
        // Reduce display size by 50% so that display size won't exceed the maximum display
        // size during the test.
        final Size currentSize = mReportedDisplayMetrics.getSize();
        final Size displaySize = new Size((int) (currentSize.getWidth() * 0.5),
                (int) (currentSize.getHeight() * 0.5));
        mReportedDisplayMetrics.setSize(displaySize);

        final Rect taskBounds = getTaskBounds();
        final double splitTaskWidth = taskBounds.width() * 1.05;
        final double splitTaskHeight = taskBounds.height() * 1.05;

        // Set embedding rules with the parent window metrics only allowing side-by-side
        // activities on a task bounds 5% larger than the current task bounds.
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID)
                        .setParentWindowMetrics(windowMetrics ->
                                windowMetrics.getBounds().width() >= splitTaskWidth
                                        && windowMetrics.getBounds().height()
                                        >= splitTaskHeight)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(splitPlaceholderRule));

        // Launch activity and verify that it fills the task and that a placeholder activity is
        // not launched
        Activity primaryActivity = startFullScreenActivityNewTask(TestActivityWithId.class,
                PRIMARY_ACTIVITY_ID);
        waitAndAssertResumedAndFillsTask(primaryActivity);
        waitAndAssertNotResumed(PLACEHOLDER_ACTIVITY_ID);

        // Increase display size by 10% so that the primary and placeholder activities are
        // stacked
        mReportedDisplayMetrics.setSize(new Size((int) (displaySize.getWidth() * 1.1),
                (int) (displaySize.getHeight() * 1.1)));

        // Verify that the placeholder activity is launched into a split with the primary
        // activity
        waitAndAssertResumed(PLACEHOLDER_ACTIVITY_ID);
        Activity placeholderActivity = getResumedActivityById(PLACEHOLDER_ACTIVITY_ID);
        assertValidSplit(primaryActivity, placeholderActivity, splitPlaceholderRule);
    }

    /**
     * Tests that when an activity is launched with a sticky placeholder, then resizing the task
     * such that it can no longer support split activities does not cause the placeholder activity
     * to finish.
     */
    @Test
    public void testStickyPlaceholder() {
        final Rect taskBounds = getTaskBounds();
        final int taskWidth = taskBounds.width();
        final int taskHeight = taskBounds.height();

        // Set embedding rules with isSticky set to true and the parent window metrics only
        // allowing side-by-side activities on a task width at least the current width.
        final SplitPlaceholderRule splitPlaceholderRule =
                new SplitPlaceholderRuleBuilderWithDefaults(PRIMARY_ACTIVITY_ID,
                        PLACEHOLDER_ACTIVITY_ID).setIsSticky(true)
                        .setParentWindowMetrics(windowMetrics ->
                                windowMetrics.getBounds().width() >= taskWidth
                                        && windowMetrics.getBounds().height() >= taskHeight)
                        .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(splitPlaceholderRule));

        // Launch activity with placeholder
        final Pair<Activity, Activity> activityPair =
                launchActivityWithPlaceholderAndVerifySplit(
                        PRIMARY_ACTIVITY_ID, PLACEHOLDER_ACTIVITY_ID, splitPlaceholderRule);
        final TestActivity placeholderActivity = (TestActivity) activityPair.second;

        // Shrink display width by 10% so that the primary and placeholder activities are
        // stacked
        final Size currentSize = mReportedDisplayMetrics.getSize();
        mReportedDisplayMetrics.setSize(new Size((int) (currentSize.getWidth() * 0.9),
                (int) (currentSize.getHeight() * 0.9)));

        // Verify that the placeholder was not finished and fills the task
        waitAndAssertResumedAndFillsTask(placeholderActivity);
        waitAndAssertResumed(Arrays.asList(placeholderActivity));
    }

    /**
     * Convenience builder for a SplitPlaceholderRule with default values.
     */
    private class SplitPlaceholderRuleBuilderWithDefaults {
        private final String mPrimaryActivityId;
        private final String mPlaceholderActivityId;

        // By default, allow any parent window metrics to allow a placeholder to be launched
        private Predicate<WindowMetrics> mParentWindowMetricsPredicate = windowMetrics -> true;

        private Optional<Integer> mFinishPrimaryWithSecondary = Optional.empty();
        private Optional<Boolean> mIsSticky = Optional.empty();

        SplitPlaceholderRuleBuilderWithDefaults(@NonNull String primaryActivityId,
                @NonNull String placeholderActivityId) {
            mPrimaryActivityId = primaryActivityId;
            mPlaceholderActivityId = placeholderActivityId;
        }

        public SplitPlaceholderRuleBuilderWithDefaults setParentWindowMetrics(
                Predicate<WindowMetrics> parentWindowMetricsPredicate) {
            mParentWindowMetricsPredicate = parentWindowMetricsPredicate;
            return this;
        }

        public SplitPlaceholderRuleBuilderWithDefaults setFinishPrimaryWithSecondary(
                int finishPrimaryWithSecondary) {
            mFinishPrimaryWithSecondary = Optional.of(finishPrimaryWithSecondary);
            return this;
        }

        public SplitPlaceholderRuleBuilderWithDefaults setIsSticky(boolean isSticky) {
            mIsSticky = Optional.of(isSticky);
            return this;
        }

        public SplitPlaceholderRule build() {
            // Create placeholder activity intent
            Intent placeholderIntent = new Intent(mContext, TestActivityWithId.class);
            placeholderIntent.putExtra(KEY_ACTIVITY_ID, mPlaceholderActivityId);

            // Create {@link SplitPlaceholderRule} that launches the placeholder in a split with the
            // target primary activity.
            SplitPlaceholderRule.Builder splitPlaceholderRuleBuilder =
                    new SplitPlaceholderRule.Builder(placeholderIntent,
                            activity -> activity instanceof TestActivityWithId
                                    && mPrimaryActivityId.equals(((TestActivityWithId) activity)
                                    .getId()) /* activityPredicate */,
                            intent -> mPrimaryActivityId.equals(
                                    intent.getStringExtra(KEY_ACTIVITY_ID)
                            )/* intentPredicate */, mParentWindowMetricsPredicate)
                            .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS);

            // Only set finishPrimaryWithSecondary if an explicit value is present
            if (mFinishPrimaryWithSecondary.isPresent()) {
                splitPlaceholderRuleBuilder.setFinishPrimaryWithSecondary(
                        mFinishPrimaryWithSecondary.get());
            }

            // Only set isSticky if an explicit value is present
            if (mIsSticky.isPresent()) {
                splitPlaceholderRuleBuilder.setSticky(mIsSticky.get());
            }

            return splitPlaceholderRuleBuilder.build();
        }
    }

    /**
     * Launches an activity that has a placeholder and verifies that the placeholder launches to
     * the side of the activity.
     */
    @NonNull
    private Pair<Activity, Activity> launchActivityWithPlaceholderAndVerifySplit(
            @NonNull String primaryActivityId, @NonNull String placeholderActivityId,
            @NonNull SplitPlaceholderRule splitPlaceholderRule) {
        // Launch the primary activity
        startFullScreenActivityNewTask(TestActivityWithId.class, primaryActivityId);
        // Get primary activity
        waitAndAssertResumed(primaryActivityId);
        Activity primaryActivity = getResumedActivityById(primaryActivityId);
        // Get placeholder activity
        waitAndAssertResumed(placeholderActivityId);
        Activity placeholderActivity = getResumedActivityById(placeholderActivityId);
        // Verify they are correctly split
        assertValidSplit(primaryActivity, placeholderActivity, splitPlaceholderRule);
        return new Pair<>(primaryActivity, placeholderActivity);
    }

    @NonNull
    private Rect getTaskBounds() {
        final Activity activity = startFullScreenActivityNewTask(TestActivity.class);
        final Rect taskBounds = waitAndGetTaskBounds(activity, true /* shouldWaitForResume */);
        activity.finish();
        new WindowManagerStateHelper().waitAndAssertActivityRemoved(activity.getComponentName());

        return taskBounds;
    }
}
