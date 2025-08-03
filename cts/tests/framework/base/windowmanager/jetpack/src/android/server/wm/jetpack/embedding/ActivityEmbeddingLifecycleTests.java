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

import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_CREATE;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_PAUSE;
import static android.server.wm.activity.lifecycle.TransitionVerifier.checkOrder;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleBuilderWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import static androidx.window.extensions.embedding.ActivityEmbeddingOptionsProperties.KEY_ACTIVITY_STACK_TOKEN;
import static androidx.window.extensions.embedding.SplitRule.FINISH_ALWAYS;
import static androidx.window.extensions.embedding.SplitRule.FINISH_NEVER;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.app.ActivityOptions;
import android.content.Intent;
import android.os.Bundle;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestActivityWithId2;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.ActivityStack;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests the invocation
 * and order of lifecycle callbacks.
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityEmbeddingLifecycleTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
public class ActivityEmbeddingLifecycleTests extends ActivityEmbeddingLifecycleTestBase {
    /**
     * Tests launching activities to the side from the primary activity, each next one replacing the
     * previous one.
     */
    @Test
    public void testSecondaryActivityLaunch_replacing() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, true /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch a replacing secondary activity
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Replace secondary container activity",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertResumed(primaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        // Destroy may happen after the secondaryActivity2 becomes visible and IDLE.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
    }

    /**
     * Tests launching activities to the side from the primary activity, each next one launching on
     * top of the previous one.
     */
    @Test
    public void testSecondaryActivityLaunch_nonReplacing() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity1 = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch a secondary activity on top
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Launch second secondary activity",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertResumed(primaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(secondaryActivity1);
    }

    /**
     * Tests launching several layers of secondary activities.
     */
    @Test
    public void testSecondaryActivityLaunch_multiSplit() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1" /* secondActivityId */, mSplitInfoConsumer);
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Init split states", mLifecycleTracker.waitForConditionWithTimeout(() ->
                checkOrder(mEventLog, expected)));
        mEventLog.clear();

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        List<Pair<String, String>> expected2 = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE),
                transition(TestActivityWithId2.class, ON_CREATE),
                transition(TEST_OWNER, ON_SPLIT_STATES_UPDATED));
        assertTrue("Launch second secondary activity to side",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected2)));
        waitAndAssertNotVisible(primaryActivity);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
    }

    /**
     * Tests finishing activities in split - finishing secondary activity only.
     */
    @Test
    public void testSplitFinish_secondaryOnly() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish secondary activity
        secondaryActivity.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertResumed(primaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing secondary should trigger finishing of the
     * primary one.
     */
    @Test
    public void testSplitFinish_secondaryWithDependent() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish secondary activity, should trigger finishing of the primary as well
        secondaryActivity.finish();
        List<Pair<String, String>> expected = List.of(
                transition(TestActivityWithId.class, ON_PAUSE),
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE));
        assertTrue("Finish secondary activity with dependents",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing primary container only, the secondary should
     * remain.
     */
    @Test
    public void testSplitFinish_primaryOnly() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_NEVER)
                .setFinishSecondaryWithPrimary(FINISH_NEVER)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish primary activity
        primaryActivity.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertResumed(secondaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing primary container only, the secondary should
     * remain.
     */
    @Test
    public void testSplitFinish_primaryWithDependent() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        startActivityAndVerifySplitAttributes(primaryActivity, TestActivityWithId.class,
                splitPairRule, "secondaryActivity1", mSplitInfoConsumer);
        mEventLog.clear();

        // Finish primary activity should trigger finishing of the secondary as well.
        primaryActivity.finish();
        List<Pair<String, String>> expected = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE),
                transition(TestActivityWithId.class, ON_PAUSE));
        assertTrue("Finish primary activity with dependents",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expected)));
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing the last created container in multi-split.
     */
    @Test
    public void testSplitFinish_lastMultiSplit() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the last activity
        secondaryActivity2.finish();
        waitAndAssertSplitStatesUpdated();
        waitAndAssertActivityOnDestroy(TestActivityWithId2.class);
        waitAndAssertResumed(primaryActivity);
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     * There is no matching split rule for top and bottom containers, and they will overlap after
     * the one in the middle is finished.
     */
    @Test
    public void testSplitFinish_midMultiSplitOnly_noSplitRule() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(primaryActivity);

        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnStop(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     * Even though there is a matching split rule for top and bottom containers, and they will still
     * overlap after the one in the middle is finished - the split rules are only applied when new
     * activities are started.
     */
    @Test
    public void testSplitFinish_midMultiSplitOnly_withSplitRule() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        SplitPairRule splitPairRule = createWildcardSplitPairRule(false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(secondaryActivity2);
        waitAndAssertNotVisible(primaryActivity);

        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnStop(TestConfigChangeHandlingActivity.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests finishing activities in split - finishing a container in the middle of a multi-split.
     */
    @Test
    public void testSplitFinish_midMultiSplitWithDependents() {
        Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);

        // Launch a secondary activity to side
        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule,
                "secondaryActivity1", mSplitInfoConsumer);

        // Launch another secondary activity to side
        splitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestActivityWithId.class, false /* shouldClearTop */)
                .setFinishPrimaryWithSecondary(FINISH_ALWAYS)
                .setFinishSecondaryWithPrimary(FINISH_ALWAYS)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        Activity secondaryActivity2 = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId2.class, splitPairRule,
                "secondaryActivity2", mSplitInfoConsumer);
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(secondaryActivity2);
        mEventLog.clear();

        // Finish the middle activity
        secondaryActivity.finish();
        waitAndAssertResumed(primaryActivity);
        // There is no guarantee on the order, because the removal may be delayed until the next
        // resumed becomes visible.
        waitAndAssertActivityOnDestroy(TestActivityWithId.class);
        waitAndAssertActivityOnDestroy(TestActivityWithId2.class);
        waitAndAssertSplitStatesUpdated();
    }

    /**
     * Tests launching a new activity on an ActivityStack with wildcard split rule, new activity
     * shows on top.
     */
    @Test
    public void testLaunchActivityInActivityStack() {
        // Launch primary activity
        Activity primaryActivity = startActivityNewTask(TestConfigChangeHandlingActivity.class);

        // Register wildcard SplitRule and launch a secondary activity to side
        SplitPairRule splitPairRule = createWildcardSplitPairRuleWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */);
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));
        mSplitInfoConsumer.setCount(1);
        startActivityFromActivity(primaryActivity, TestActivityWithId.class,
                "secondaryActivity" /* secondActivityId */);

        // A split info callback should report after the activity launched
        List<SplitInfo> activeSplitStates = null;
        try {
            activeSplitStates = mSplitInfoConsumer.waitAndGet();
        } catch (InterruptedException e) {
            throw new AssertionError("testLaunchActivityInActivityStack()", e);
        }
        assertNotNull("Active Split States cannot be null.", activeSplitStates);

        // Get the primary ActivityStack token
        ActivityStack.Token primaryStackToken = null;
        for (SplitInfo splitInfo : activeSplitStates) {
            final ActivityStack primaryStack = splitInfo.getPrimaryActivityStack();
            final List<Activity> activities = primaryStack.getActivities();
            if (activities.contains(primaryActivity)) {
                primaryStackToken = primaryStack.getActivityStackToken();
                break;
            }
        }
        assertNotNull("Primary stack token cannot be null.", primaryStackToken);

        // Launch an activity to the primary ActivityStack
        Intent intent = new Intent(primaryActivity, TestActivityWithId2.class);
        intent.putExtra(KEY_ACTIVITY_ID, "primaryActivity2");
        final Bundle options = ActivityOptions.makeBasic().toBundle();
        options.putBundle(KEY_ACTIVITY_STACK_TOKEN, primaryStackToken.toBundle());
        primaryActivity.startActivity(intent, options);

        // The existing primary activity should be occluded by new Activity
        waitAndAssertResumed("primaryActivity2");
        waitAndAssertResumed("secondaryActivity");
        waitAndAssertNotVisible(primaryActivity);
    }
}
