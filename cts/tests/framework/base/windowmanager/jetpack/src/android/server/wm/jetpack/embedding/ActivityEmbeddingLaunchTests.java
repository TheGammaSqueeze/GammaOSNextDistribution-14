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
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.getPrimaryStackTopActivity;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.getSecondaryStackTopActivity;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.ActivityEmbeddingUtil;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestActivityWithId2;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.ActivityRule;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.google.common.collect.Sets;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests activity
 * launch scenarios.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingLaunchTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingLaunchTests extends ActivityEmbeddingTestBase {

    /**
     * Tests splitting activities with the same primary activity.
     */
    @Test
    public void testSplitWithPrimaryActivity() throws InterruptedException {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Only the primary activity can be in a split with another activity
        final Predicate<Pair<Activity, Activity>> activityActivityPredicate =
                activityActivityPair -> primaryActivity.equals(activityActivityPair.first);

        SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityActivityPredicate, activityIntentPair -> true /* activityIntentPredicate */,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch multiple activities from the primary activity and verify that they all
        // successfully split with the primary activity.
        List<Activity> secondaryActivities = new ArrayList<>();
        List<List<SplitInfo>> splitInfosList = new ArrayList<>();
        final int numActivitiesToLaunch = 4;
        for (int activityLaunchIndex = 0; activityLaunchIndex < numActivitiesToLaunch;
                activityLaunchIndex++) {
            Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                    TestActivityWithId.class, splitPairRule,
                    Integer.toString(activityLaunchIndex) /* secondActivityId */,
                    mSplitInfoConsumer);

            // Verify that the secondary container has all the secondary activities
            secondaryActivities.add(secondaryActivity);
            final List<SplitInfo> lastReportedSplitInfoList =
                    mSplitInfoConsumer.getLastReportedValue();
            splitInfosList.add(lastReportedSplitInfoList);
            assertEquals(1, lastReportedSplitInfoList.size());
            final SplitInfo splitInfo = lastReportedSplitInfoList.get(0);
            assertEquals(primaryActivity, getPrimaryStackTopActivity(splitInfo));
            assertEquals(secondaryActivities, splitInfo.getSecondaryActivityStack()
                    .getActivities());
        }

        // Iteratively finish each secondary activity and verify that the primary activity is split
        // with the next highest secondary activity.
        for (int i = secondaryActivities.size() - 1; i >= 1; i--) {
            final Activity currentSecondaryActivity = secondaryActivities.get(i);
            currentSecondaryActivity.finish();
            // A split info callback will occur because the split states have changed
            List<SplitInfo> newSplitInfos = mSplitInfoConsumer.waitAndGet();
            // Verify the new split
            final Activity newSecondaryActivity = secondaryActivities.get(i - 1);
            assertValidSplit(primaryActivity, newSecondaryActivity, splitPairRule);
            assertEquals(splitInfosList.get(i - 1), newSplitInfos);
        }
    }

    /**
     * Tests launching activities to the side from the primary activity where the secondary stack
     * is cleared after each launch.
     */
    @Test
    public void testPrimaryActivityLaunchToSideClearTop() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRule(true /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "initialSecondaryActivity" /* secondActivityId */, mSplitInfoConsumer);

        // Launch multiple activities to the side from the primary activity and verify that they
        // all successfully split with the primary activity and that the previous secondary activity
        // is finishing.
        final int numActivitiesToLaunch = 4;
        Activity prevSecondaryActivity;
        for (int i = 0; i < numActivitiesToLaunch; i++) {
            prevSecondaryActivity = secondaryActivity;
            secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                    TestActivityWithId.class, splitPairRule,
                    Integer.toString(i) /* secondActivityId */, mSplitInfoConsumer);
            // The previous secondary activity should be finishing because shouldClearTop was set
            // to true, which clears the secondary container before launching the next secondary
            // activity.
            assertTrue(prevSecondaryActivity.isFinishing());
        }

        // Verify that the last reported split info only contains the final split
        final List<SplitInfo> lastReportedSplitInfo = mSplitInfoConsumer.getLastReportedValue();
        assertEquals(1, lastReportedSplitInfo.size());
        final SplitInfo splitInfo = lastReportedSplitInfo.get(0);
        assertEquals(1, splitInfo.getPrimaryActivityStack().getActivities().size());
        assertEquals(1, splitInfo.getSecondaryActivityStack().getActivities().size());
    }

    /**
     * Tests that launching activities with wildcard split rules results in the newly launched
     * activity being split with the activity that has the highest z-order, which is the top
     * activity in the secondary stack.
     */
    @Test
    public void testSplitWithTopmostActivity() throws InterruptedException {
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        Activity nextPrimaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "initialSecondaryActivity" /* secondActivityId */, mSplitInfoConsumer);

        Map<Activity, List<SplitInfo>> secondaryActivityToSplitInfoMap = new HashMap<>();
        secondaryActivityToSplitInfoMap.put(nextPrimaryActivity,
                mSplitInfoConsumer.getLastReportedValue());

        // Store the launched activities in order for later use in checking the split info
        List<Activity> launchedActivitiesInOrder = new ArrayList<>();
        launchedActivitiesInOrder.addAll(Arrays.asList(primaryActivity, nextPrimaryActivity));

        // Launch multiple activities to the side from the secondary activity and verify that the
        // secondary activity becomes the primary activity and that it is split with the activity
        // that was just launched.
        final int numActivitiesToLaunch = 4;
        for (int activityLaunchIndex = 0; activityLaunchIndex < numActivitiesToLaunch;
                activityLaunchIndex++) {
            nextPrimaryActivity = startActivityAndVerifySplitAttributes(nextPrimaryActivity,
                    TestActivityWithId.class, splitPairRule,
                    Integer.toString(activityLaunchIndex) /* secondActivityId */,
                    mSplitInfoConsumer);

            launchedActivitiesInOrder.add(nextPrimaryActivity);

            // Verify the split states match with the current and previous launches
            final List<SplitInfo> lastReportedSplitInfoList =
                    mSplitInfoConsumer.getLastReportedValue();
            secondaryActivityToSplitInfoMap.put(nextPrimaryActivity, lastReportedSplitInfoList);
            // The number of splits is number of launched activities - 1 because the first primary
            // was the only activity to not launch into a split.
            assertEquals(launchedActivitiesInOrder.size() - 1,
                    lastReportedSplitInfoList.size());
            for (int splitInfoIndex = 0; splitInfoIndex < lastReportedSplitInfoList.size();
                    splitInfoIndex++) {
                final SplitInfo splitInfo = lastReportedSplitInfoList.get(splitInfoIndex);
                assertEquals(launchedActivitiesInOrder.get(splitInfoIndex),
                        getPrimaryStackTopActivity(splitInfo));
                assertEquals(launchedActivitiesInOrder.get(splitInfoIndex + 1),
                        getSecondaryStackTopActivity(splitInfo));
            }
        }

        // Iteratively finish each secondary activity and verify that the primary activity becomes
        // the secondary activity and the activity below that becomes the primary activity.
        for (int i = launchedActivitiesInOrder.size() - 1; i >= 2; i--) {
            final Activity currentSecondaryActivity = launchedActivitiesInOrder.get(i);
            currentSecondaryActivity.finish();
            // A split info callback will occur because the split states have changed
            List<SplitInfo> newSplitInfos = mSplitInfoConsumer.waitAndGet();
            // Verify the new split
            final Activity newPrimaryActivity = launchedActivitiesInOrder.get(i - 2);
            final Activity newSecondaryActivity = launchedActivitiesInOrder.get(i - 1);
            assertValidSplit(newPrimaryActivity, newSecondaryActivity, splitPairRule);
            assertEquals(secondaryActivityToSplitInfoMap.get(newSecondaryActivity), newSplitInfos);
        }
    }

    /**
     * Tests launching an activity that is set to always expand when it is launched over an existing
     * split from the current primary activity.
     */
    @Test
    public void testAlwaysExpandOverSplit_launchFromPrimary() {
        // Create activity rule that sets the target activity to always expand
        final String alwaysExpandedActivityId = "AlwaysExpandedActivityId";
        Predicate<Activity> activityPredicate = activity ->
                activity instanceof TestActivityWithId
                        && alwaysExpandedActivityId.equals(((TestActivityWithId) activity).getId());
        Predicate<Intent> intentPredicate = intent -> intent.getComponent() != null
                && TestActivityWithId.class.getName().equals(intent.getComponent().getClassName());
        ActivityRule expandActivityRule = new ActivityRule.Builder(activityPredicate,
                intentPredicate).setShouldAlwaysExpand(true).build();

        // Register wildcard split pair rule and always-expanded activity rule
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule,
                expandActivityRule));

        // Launch two activities into a split
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity" /* secondActivityId */, mSplitInfoConsumer);

        // Launch always expanded activity from the primary activity
        startActivityFromActivity(primaryActivity, TestActivityWithId.class,
                alwaysExpandedActivityId);

        // Verify that the always expanded activity is resumed and fills its parent
        ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask(alwaysExpandedActivityId);

        // Finish the always expanded activity and verify that the split is resumed
        final Activity alwaysExpandedActivity = getResumedActivityById(alwaysExpandedActivityId);
        alwaysExpandedActivity.finish();
        waitAndAssertResumed(Arrays.asList(primaryActivity, secondaryActivity));
        assertValidSplit(primaryActivity, secondaryActivity, splitPairRule);
    }

    /**
     * Tests launching an activity that is set to always expand when it is launched over an existing
     * split from the current secondary activity.
     */
    @Test
    public void testAlwaysExpandOverSplit_launchFromSecondary() {
        // Create activity rule that sets the target activity to always expand
        final String alwaysExpandedActivityId = "AlwaysExpandedActivityId";
        Predicate<Activity> activityPredicate = activity ->
                activity instanceof TestActivityWithId
                        && alwaysExpandedActivityId.equals(((TestActivityWithId) activity).getId());
        Predicate<Intent> intentPredicate = intent -> intent.getComponent() != null
                && TestActivityWithId.class.getName().equals(intent.getComponent().getClassName());
        ActivityRule expandActivityRule = new ActivityRule.Builder(activityPredicate,
                intentPredicate).setShouldAlwaysExpand(true).build();

        // Register wildcard split pair rule and always-expanded activity rule
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Sets.newHashSet(splitPairRule,
                expandActivityRule));

        // Launch two activities into a split
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity" /* secondActivityId */, mSplitInfoConsumer);

        // Launch always expanded activity from the secondary activity
        startActivityFromActivity(secondaryActivity, TestActivityWithId.class,
                alwaysExpandedActivityId);

        // Verify that the always expanded activity is resumed and fills its parent
        ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask(alwaysExpandedActivityId);

        // Finish the always expanded activity and verify that the split is resumed
        final Activity alwaysExpandedActivity = getResumedActivityById(alwaysExpandedActivityId);
        alwaysExpandedActivity.finish();
        waitAndAssertResumed(Arrays.asList(primaryActivity, secondaryActivity));
        assertValidSplit(primaryActivity, secondaryActivity, splitPairRule);
    }

    /**
     * Tests that if an activity is launched from the secondary activity that only the primary
     * activity can be split with, then the newly launched activity launches above the current
     * secondary activity in the same container.
     */
    @Test
    public void testSecondaryActivityLaunchAbove() throws InterruptedException {
        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Build a rule that will only allow to split with the primary activity.
        final Predicate<Pair<Activity, Intent>> activityIntentPredicate =
                activityIntentPair -> primaryActivity.equals(activityIntentPair.first);
        // Build the split pair rule
        final SplitPairRule splitPairRule = new SplitPairRule.Builder(
                activityPair -> true /* activityPairPredicate */, activityIntentPredicate,
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "initialSecondaryActivity", mSplitInfoConsumer);

        List<Activity> secondaryActivities = new ArrayList<>();
        secondaryActivities.add(secondaryActivity);

        List<List<SplitInfo>> splitInfosList = new ArrayList<>();
        splitInfosList.add(mSplitInfoConsumer.getLastReportedValue());

        // Launch multiple activities from the secondary activity and verify that they all
        // successfully split with the primary activity.
        final int numActivitiesToLaunch = 4;
        for (int i = 0; i < numActivitiesToLaunch; i++) {
            secondaryActivity = ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes(
                    secondaryActivity /* activityLaunchingFrom */,
                    primaryActivity /* expectedPrimaryActivity */, TestActivityWithId.class,
                    splitPairRule, Integer.toString(i) /* secondActivityId */,
                    1 /* expectedCallbackCount */, mSplitInfoConsumer);

            // Verify the split states match with the current and previous launches
            secondaryActivities.add(secondaryActivity);
            final List<SplitInfo> lastReportedSplitInfoList =
                    mSplitInfoConsumer.getLastReportedValue();
            splitInfosList.add(lastReportedSplitInfoList);
            assertEquals(1, lastReportedSplitInfoList.size());
            final SplitInfo splitInfo = lastReportedSplitInfoList.get(0);
            assertEquals(primaryActivity, getPrimaryStackTopActivity(splitInfo));
            assertEquals(secondaryActivities, splitInfo.getSecondaryActivityStack()
                    .getActivities());
        }

        // Iteratively finish each secondary activity and verify that the primary activity is split
        // with the next highest secondary activity.
        for (int i = secondaryActivities.size() - 1; i >= 1; i--) {
            final Activity currentSecondaryActivity = secondaryActivities.get(i);
            currentSecondaryActivity.finish();
            // A split info callback will occur because the split states have changed
            List<SplitInfo> newSplitInfos = mSplitInfoConsumer.waitAndGet();
            final Activity newSecondaryActivity = secondaryActivities.get(i - 1);
            assertValidSplit(primaryActivity, newSecondaryActivity, splitPairRule);
            assertEquals(splitInfosList.get(i - 1), newSplitInfos);
        }
    }
}
