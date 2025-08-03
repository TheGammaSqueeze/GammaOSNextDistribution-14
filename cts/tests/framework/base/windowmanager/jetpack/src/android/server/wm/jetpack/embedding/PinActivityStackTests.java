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
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_RESUME;
import static android.server.wm.activity.lifecycle.LifecycleConstants.ON_START;
import static android.server.wm.activity.lifecycle.TransitionVerifier.checkOrder;
import static android.server.wm.activity.lifecycle.TransitionVerifier.transition;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EXPAND_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRuleBuilderWithPrimaryActivityClass;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertNotVisible;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumed;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndGetTaskBounds;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.graphics.Rect;
import android.platform.test.annotations.Presubmit;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.Pair;
import android.util.Size;

import androidx.annotation.NonNull;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitPairRule;
import androidx.window.extensions.embedding.SplitPinRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.Collections;
import java.util.List;

/**
 * Tests for the {@link androidx.window.extensions} implementation provided on the device (and only
 * if one is available) for the Activity Embedding functionality. Specifically tests pinning the
 * top ActivityStack on a Task.
 * <p>
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:PinActivityStackTests
 */
@Presubmit
@RunWith(AndroidJUnit4.class)
@ApiTest(apis = {
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent#pinTopActivityStack",
        "androidx.window.extensions.embedding.ActivityEmbeddingComponent#unpinTopActivityStack"
})
public class PinActivityStackTests extends ActivityEmbeddingLifecycleTestBase {
    private Activity mPrimaryActivity;
    private Activity mPinnedActivity;
    private String mPinnedActivityId = "pinActivity";
    private SplitPairRule mWildcardSplitPairRule;
    private int mTaskId;

    @Override
    @Before
    public void setUp() {
        super.setUp();
        mPrimaryActivity = startFullScreenActivityNewTask(TestConfigChangeHandlingActivity.class);
        mTaskId = mPrimaryActivity.getTaskId();
        mWildcardSplitPairRule = createWildcardSplitPairRuleBuilderWithPrimaryActivityClass(
                TestConfigChangeHandlingActivity.class, false /* shouldClearTop */)
                .build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(mWildcardSplitPairRule));
    }

    /**
     * Verifies that the activity starts in a new container and the navigation are isolated after
     * the top ActivityStack is pinned.
     */
    @Test
    public void testPinTopActivityStack_createContainerForNewActivity() {
        pinTopActivityStackAndVerifyLifecycle(true /* newPrimaryContainer */);
    }

    /**
     * Verifies that the activity starts in the same container and the navigation are isolated
     * after the top ActivityStack is pinned.
     */
    @Test
    public void testPinTopActivityStack_reuseContainerForNewActivity() {
        pinTopActivityStackAndVerifyLifecycle(false /* newPrimaryContainer */);
    }

    private void pinTopActivityStackAndVerifyLifecycle(boolean newPrimaryContainer) {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());
        mEventLog.clear();

        if (!newPrimaryContainer) {
            mActivityEmbeddingComponent.setEmbeddingRules(Collections.emptySet());
        }

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the primary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPinnedActivityId);
        waitAndAssertNotVisible(mPrimaryActivity);
        final List<Pair<String, String>> expectedLifecycle = List.of(
                transition(TestConfigChangeHandlingActivity.class, ON_PAUSE),
                transition(TestActivityWithId.class, ON_CREATE),
                transition(TestActivityWithId.class, ON_START),
                transition(TestActivityWithId.class, ON_RESUME));
        assertTrue("Pause existing primary Activity before resuming another activity on top",
                mLifecycleTracker.waitForConditionWithTimeout(() ->
                        checkOrder(mEventLog, expectedLifecycle)));
        final Activity activity1 = getResumedActivityById(activityId1);

        // Start an Activity from the pinned ActivityStack
        final String activityId2 = "Activity2";
        startActivityFromActivity(mPinnedActivity, TestActivityWithId.class, activityId2);

        // Verifies the activity on the pinned ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId2);
        waitAndAssertResumed(activity1);
        waitAndAssertNotVisible(mPrimaryActivity);
        waitAndAssertNotVisible(mPinnedActivity);
        final Activity activity2 = getResumedActivityById(activityId2);

        // Finishes activities on the pinned ActivityStack
        activity2.finish();
        mPinnedActivity.finish();

        waitAndAssertResumed(activity1);
        if (newPrimaryContainer) {
            // Verifies primary activity is resumed due to the two activities split side-by-side.
            waitAndAssertResumed(mPrimaryActivity);
        }
    }

    /**
     * Verifies that the activity navigation are not isolated after the top ActivityStack is
     * unpinned.
     */
    @Test
    public void testUnpinTopActivityStack() {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin and unpin the top ActivityStack
        assertTrue(pinTopActivityStack());
        mActivityEmbeddingComponent.unpinTopActivityStack(mTaskId);

        // Verifies the activities still splits after unpin.
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertResumed(mPinnedActivity);

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the secondary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertNotVisible(mPinnedActivity);
    }

    /**
     * Verifies that the activity is expanded if no split rules after unpinned.
     */
    @Test
    public void testUnpinTopActivityStack_expands() {
        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());
        waitAndAssertResumed(mPrimaryActivity);
        waitAndAssertResumed(mPinnedActivity);

        // Start an Activity from the primary ActivityStack
        final String activityId1 = "Activity1";
        startActivityFromActivity(mPrimaryActivity, TestActivityWithId.class, activityId1);

        // Verifies the activity in the primary ActivityStack is occluded by the new Activity.
        waitAndAssertResumed(activityId1);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
        final Activity activity1 = getResumedActivityById(activityId1);

        // Verifies the activity is expanded and occludes other activities after unpin.
        mActivityEmbeddingComponent.unpinTopActivityStack(mTaskId);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
        waitAndAssertNotVisible(activity1);
    }

    /**
     * Verifies that top ActivityStack cannot be pinned whenever it is not allowed.
     */
    @Test
    public void testPinTopActivityStack_invalidPin() {
        // Cannot pin if there's no ActivityStack.
        assertFalse(pinTopActivityStack());

        // Launch a secondary activity to side
        mPinnedActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        // Cannot pin if no such task.
        assertFalse(pinTopActivityStack(mTaskId + 1));

        // Cannot pin if parent window metric not large enough.
        final SplitPinRule oversizeParentMetricsRule = new SplitPinRule.Builder(
                new SplitAttributes.Builder().build(),
                parentWindowMetrics -> false /* parentWindowMetricsPredicate */).build();
        assertFalse(pinTopActivityStack(mTaskId, oversizeParentMetricsRule));

        // Pin the top ActivityStack
        assertTrue(pinTopActivityStack());

        // Cannot pin once there's already a pinned ActivityStack.
        assertFalse(pinTopActivityStack());
    }

    /**
     * Verifies that the pinned rule is sticky and applies whenever possible.
     */
    @Test
    public void testPinTopActivityStack_resizeStickyPin() {
        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        pinExpandActivityAndResizeDisplay(secondaryActivity, true /* stickyPin */);

        // Verify the activities are still split
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
    }

    /**
     * Verifies that the pinned rule is non-sticky and removed after resizing.
     */
    @Test
    public void testPinTopActivityStack_resizeNonStickyPin() {
        // Launch a secondary activity to side
        Activity secondaryActivity = startActivityAndVerifySplitAttributes(mPrimaryActivity,
                TestActivityWithId.class, mWildcardSplitPairRule,
                mPinnedActivityId, mSplitInfoConsumer);

        pinExpandActivityAndResizeDisplay(secondaryActivity, false /* stickyPin */);

        // Verify the unpinned activity is expanded.
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(secondaryActivity);
        waitAndAssertNotVisible(mPrimaryActivity);
    }

    private void pinExpandActivityAndResizeDisplay(@NonNull Activity secondaryActivity,
            boolean stickyPin) {
        // Starts an Activity to always-expand
        final SplitPairRule expandRule = createSplitPairRuleBuilder(activityActivityPair -> true,
                activityIntentPair -> true, windowMetrics -> true).setDefaultSplitAttributes(
                EXPAND_SPLIT_ATTRS).build();
        mActivityEmbeddingComponent.setEmbeddingRules(
                Collections.singleton(expandRule));
        mPinnedActivity = startActivityAndVerifySplitAttributes(secondaryActivity,
                TestActivityWithId.class, expandRule, "expandActivityId", mSplitInfoConsumer);

        // Pin the top ActivityStack
        final Rect taskBounds = waitAndGetTaskBounds(mPinnedActivity,
                true /* shouldWaitForResume */);
        final int originalTaskWidth = taskBounds.width();
        final int originalTaskHeight = taskBounds.height();
        final SplitPinRule stickySplitPinRule = new SplitPinRule.Builder(
                new SplitAttributes.Builder().build(),
                parentWindowMetrics -> parentWindowMetrics.getBounds().width() >= originalTaskWidth
                        && parentWindowMetrics.getBounds().height() >= originalTaskHeight)
                .setSticky(stickyPin).build();

        // Verify the pinned activity is split with next-top activity
        assertTrue(pinTopActivityStack(mTaskId, stickySplitPinRule));
        waitAndAssertResumed(secondaryActivity);
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(mPrimaryActivity);

        // Shrink the display by 10% to make the activities stacked
        final Size originalDisplaySize = mReportedDisplayMetrics.getSize();
        mReportedDisplayMetrics.setSize(new Size((int) (originalDisplaySize.getWidth() * 0.9),
                (int) (originalDisplaySize.getHeight() * 0.9)));

        // Verify only the pinned activity is visible
        waitAndAssertResumed(mPinnedActivity);
        waitAndAssertNotVisible(secondaryActivity);
        waitAndAssertNotVisible(mPrimaryActivity);

        // Restore to the original display size
        mReportedDisplayMetrics.setSize(originalDisplaySize);
    }

    private boolean pinTopActivityStack() {
        return pinTopActivityStack(mTaskId);
    }

    private boolean pinTopActivityStack(int taskId) {
        SplitPinRule splitPinRule = new SplitPinRule.Builder(new SplitAttributes.Builder().build(),
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */).build();
        return pinTopActivityStack(taskId, splitPinRule);
    }

    private boolean pinTopActivityStack(int taskId, @NonNull SplitPinRule splitPinRule) {
        return mActivityEmbeddingComponent.pinTopActivityStack(taskId, splitPinRule);
    }
}
