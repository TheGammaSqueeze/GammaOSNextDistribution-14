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
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.HINGE_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createWildcardSplitPairRule;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertFinishing;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.waitAndAssertResumedAndFillsTask;
import static android.server.wm.jetpack.utils.TestActivityLauncher.KEY_ACTIVITY_ID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.content.Intent;
import android.server.wm.jetpack.utils.TestActivityWithId;
import android.server.wm.jetpack.utils.TestConfigChangeHandlingActivity;
import android.util.ArraySet;

import androidx.annotation.NonNull;
import androidx.window.extensions.embedding.ActivityStack;
import androidx.window.extensions.embedding.EmbeddingRule;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

// TODO(b/270492365): Verify finish expanded ActivityStack
/**
 * Test class to verify {@link androidx.window.extensions.embedding.ActivityStack} APIs
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:ActivityStackApisTests
 */
public class ActivityStackApisTests extends ActivityEmbeddingTestBase {
    @ApiTest(apis = {"androidx.window.extensions.embedding.ActivityEmbeddingComponent"
            + "#finishActivityStacks"})
    @Test
    public void testFinishActivityStacks_finishPrimary() throws InterruptedException {
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        final Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule, "secondaryActivity", mSplitInfoConsumer);

        final SplitInfo splitInfo = getSplitInfo(primaryActivity, secondaryActivity);

        mActivityEmbeddingComponent.finishActivityStacksWithTokens(Collections.singleton(
                splitInfo.getPrimaryActivityStack().getActivityStackToken()));

        waitAndAssertFinishing(primaryActivity);
        waitAndAssertResumedAndFillsTask(secondaryActivity);

        List<SplitInfo> splitInfoList = mSplitInfoConsumer.waitAndGet();
        assertTrue(splitInfoList.isEmpty());
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.ActivityEmbeddingComponent"
            + "#finishActivityStacks"})
    @Test
    public void testFinishActivityStacks_finishSecondary() throws InterruptedException {
        SplitPairRule splitPairRule = createWildcardSplitPairRule();
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        final Activity primaryActivity = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        final Activity secondaryActivity = startActivityAndVerifySplitAttributes(primaryActivity,
                TestActivityWithId.class, splitPairRule, "secondaryActivity", mSplitInfoConsumer);

        final SplitInfo splitInfo = getSplitInfo(primaryActivity, secondaryActivity);

        mActivityEmbeddingComponent.finishActivityStacksWithTokens(Collections.singleton(
                splitInfo.getSecondaryActivityStack().getActivityStackToken()));

        waitAndAssertFinishing(secondaryActivity);
        waitAndAssertResumedAndFillsTask(primaryActivity);

        List<SplitInfo> splitInfoList = mSplitInfoConsumer.waitAndGet();
        assertTrue(splitInfoList.isEmpty());
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.ActivityEmbeddingComponent"
            + "#finishActivityStacks"})
    @Test
    public void testFinishActivityStacks_finishAllSecondaryStacks_primaryStackExpand()
            throws InterruptedException {
        SplitPairRule splitPairRuleAB = createSplitPairRuleBuilder(
                activityActivityPair -> {
                    final Activity activity = activityActivityPair.second;
                    return activity instanceof TestActivityWithId
                            && "activityB".equals(((TestActivityWithId) activity).getId());
                },
                activityIntentPair -> {
                    final Intent intent = activityIntentPair.second;
                    return intent.getComponent().getClassName().equals(
                            TestActivityWithId.class.getName())
                            && "activityB".equals(intent.getStringExtra(KEY_ACTIVITY_ID));
                },
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(DEFAULT_SPLIT_ATTRS)
                .build();
        // Create another split pair rule to split activity A with activity C with different
        // split attributes so that Activity embedding library creates another ActivityStack
        // for Activity C
        SplitPairRule splitPairRuleAC = createSplitPairRuleBuilder(
                activityActivityPair -> {
                    final Activity activity = activityActivityPair.second;
                    return activity instanceof TestActivityWithId
                            && "activityC".equals(((TestActivityWithId) activity).getId());
                },
                activityIntentPair -> {
                    final Intent intent = activityIntentPair.second;
                    return intent.getComponent().getClassName().equals(
                            TestActivityWithId.class.getName())
                            && "activityC".equals(intent.getStringExtra(KEY_ACTIVITY_ID));
                },
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(HINGE_SPLIT_ATTRS)
                .build();

        final Set<EmbeddingRule> rules = new ArraySet<>();
        rules.add(splitPairRuleAB);
        rules.add(splitPairRuleAC);
        mActivityEmbeddingComponent.setEmbeddingRules(rules);

        final Activity activityA = startFullScreenActivityNewTask(
                TestConfigChangeHandlingActivity.class);
        final Activity activityB = startActivityAndVerifySplitAttributes(activityA,
                TestActivityWithId.class, splitPairRuleAB, "activityB",
                mSplitInfoConsumer);
        final Activity activityC = startActivityAndVerifySplitAttributes(activityA,
                TestActivityWithId.class, splitPairRuleAC, "activityC",
                mSplitInfoConsumer);

        final Set<ActivityStack.Token> secondaryActivityStacks = mSplitInfoConsumer
                .getLastReportedValue()
                .stream()
                .filter(splitInfo ->
                        splitInfo.getPrimaryActivityStack().getActivities().contains(activityA))
                .map(splitInfo -> splitInfo.getSecondaryActivityStack().getActivityStackToken())
                .collect(Collectors.toSet());
        assertEquals(2, secondaryActivityStacks.size());

        mActivityEmbeddingComponent.finishActivityStacksWithTokens(secondaryActivityStacks);

        waitAndAssertFinishing(activityB);
        waitAndAssertFinishing(activityC);
        waitAndAssertResumedAndFillsTask(activityA);

        List<SplitInfo> splitInfoList = mSplitInfoConsumer.waitAndGet();
        assertTrue(splitInfoList.isEmpty());
    }

    private SplitInfo getSplitInfo(@NonNull Activity primaryActivity,
            @NonNull Activity secondaryActivity) {
        List<SplitInfo> splitInfoList = mSplitInfoConsumer.getLastReportedValue();

        for (SplitInfo splitInfo : splitInfoList) {
            if (splitInfo.getPrimaryActivityStack().getActivities().contains(primaryActivity)
                    && splitInfo.getSecondaryActivityStack().getActivities()
                            .contains(secondaryActivity)) {
                return splitInfo;
            }
        }
        return null;
    }
}
