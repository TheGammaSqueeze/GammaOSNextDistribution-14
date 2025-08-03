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

import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.EXPAND_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.HINGE_SPLIT_ATTRS;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.assertSplitPairIsCorrect;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.createSplitPairRuleBuilder;
import static android.server.wm.jetpack.utils.ActivityEmbeddingUtil.startActivityAndVerifySplitAttributes;

import android.app.Activity;
import android.server.wm.jetpack.utils.TestActivityWithId;

import androidx.window.extensions.core.util.function.Function;
import androidx.window.extensions.embedding.SplitAttributes;
import androidx.window.extensions.embedding.SplitAttributesCalculatorParams;
import androidx.window.extensions.embedding.SplitInfo;
import androidx.window.extensions.embedding.SplitPairRule;

import com.android.compatibility.common.util.ApiTest;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.util.Collections;
import java.util.List;

/**
 * Test class to verify the behaviors of
 * {@link androidx.window.extensions.embedding.SplitAttributes} runtime APIs
 *
 * Build/Install/Run:
 *     atest CtsWindowManagerJetpackTestCases:SplitAttributesRuntimeApisTests
 */
@RunWith(Parameterized.class)
public class SplitAttributesRuntimeApisTests extends ActivityEmbeddingTestBase {

    private static final SplitAttributes RATIO_SPLIT_ATTRS = new SplitAttributes.Builder()
            .setSplitType(new SplitAttributes.SplitType.RatioSplitType(0.8f))
            .build();

    private static final SplitAttributes TOP_TO_BOTTOM_SPLIT_ATTRS = new SplitAttributes.Builder()
            .setSplitType(new SplitAttributes.SplitType.RatioSplitType(0.7f))
            .setLayoutDirection(SplitAttributes.LayoutDirection.TOP_TO_BOTTOM)
            .build();

    @Parameterized.Parameters(name = "{1}")
    public static Object[][] data() {
        return new Object[][]{
                {RATIO_SPLIT_ATTRS, "RATIO"},
                {EXPAND_SPLIT_ATTRS, "EXPAND"},
                {HINGE_SPLIT_ATTRS, "HINGE"},
        };
    }

    @Parameterized.Parameter(0)
    public SplitAttributes mCustomizedSplitAttributes;

    @Parameterized.Parameter(1)
    public String mAttrsName;

    private static final String ACTIVITY_A_ID = "activityA";
    private static final String ACTIVITY_B_ID = "activityB";

    @ApiTest(apis = {"androidx.window.extensions.embedding.ActivityEmbeddingComponent"
            + "#invalidateTopVisibleSplitAttributes",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent"
                    + "#setSplitAttributesCalculator",
            "androidx.window.extensions.embedding.ActivityEmbeddingComponent"
                    + "#clearSplitAttributesCalculator"})
    @Test
    public void testInvalidateTopVisibleSplitAttributes() {
        final String tag = "testInvalidateTopVisibleSplitAttributes" + mCustomizedSplitAttributes;

        // Create a split rule for activity A and activity B to split the screen horizontally.
        // Not use the default splitAttributes because the bounds of hinge split type may split
        // the screen equally.
        SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(TOP_TO_BOTTOM_SPLIT_ATTRS)
                .setTag(tag)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        Function<SplitAttributesCalculatorParams, SplitAttributes> calculator = params -> {
            // Only make the customized split attributes apply to the split rule with test tag in
            // case other tests are affected.
            if (tag.equals(params.getSplitRuleTag())) {
                return mCustomizedSplitAttributes;
            }
            // Otherwise, keep the default behavior.
            if (!params.areDefaultConstraintsSatisfied()) {
                return EXPAND_SPLIT_ATTRS;
            }
            return params.getDefaultSplitAttributes();
        };
        mActivityEmbeddingComponent.setSplitAttributesCalculator(calculator);

        // Launch the activity A and B split and verify that the split pair matches
        // customizedSplitAttributes.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        Activity activityB = startActivityAndVerifySplitAttributes(activityA,
                activityA /* expectedPrimaryActivity */, TestActivityWithId.class,
                mCustomizedSplitAttributes, ACTIVITY_B_ID, 1 /* expectedCallbackCount */,
                mSplitInfoConsumer);

        mActivityEmbeddingComponent.clearSplitAttributesCalculator();

        // Invalidate the current splitAttributes Apply the splitAttributes calculator change.
        mActivityEmbeddingComponent.invalidateTopVisibleSplitAttributes();

        // Checks the split pair
        assertSplitPairIsCorrect(activityA, activityB, TOP_TO_BOTTOM_SPLIT_ATTRS,
                mSplitInfoConsumer);
    }

    @ApiTest(apis = {"androidx.window.extensions.embedding.ActivityEmbeddingComponent"
            + "#invalidateTopVisibleSplitAttributes"})
    @Test
    public void testUpdateSplitAttributes() {
        // Create a split rule for activity A and activity B to split the screen horizontally.
        // Not use the default splitAttributes because the bounds of hinge split type may split
        // the screen equally.
        SplitPairRule splitPairRule = createSplitPairRuleBuilder(
                activityActivityPair -> true /* activityPairPredicate */,
                activityIntentPair -> true, /* activityIntentPairPredicate */
                parentWindowMetrics -> true /* parentWindowMetricsPredicate */)
                .setDefaultSplitAttributes(TOP_TO_BOTTOM_SPLIT_ATTRS)
                .build();

        // Register the split pair rule.
        mActivityEmbeddingComponent.setEmbeddingRules(Collections.singleton(splitPairRule));

        // Launch the activity A and B split and verify that the split pair matches
        // customizedSplitAttributes.
        Activity activityA = startFullScreenActivityNewTask(TestActivityWithId.class,
                ACTIVITY_A_ID);
        Activity activityB = startActivityAndVerifySplitAttributes(activityA,
                TestActivityWithId.class, splitPairRule, ACTIVITY_B_ID, mSplitInfoConsumer);

        List<SplitInfo> splitInfoList = mSplitInfoConsumer.getLastReportedValue();
        SplitInfo splitInfo = null;
        for (SplitInfo info : splitInfoList) {
            if (info.getPrimaryActivityStack().getActivities().contains(activityA)
                    && info.getSecondaryActivityStack().getActivities().contains(activityB)) {
                splitInfo = info;
                break;
            }
        }

        // Update split attributes that different from the default one.
        mActivityEmbeddingComponent.updateSplitAttributes(splitInfo.getToken(),
                mCustomizedSplitAttributes);

        // Checks the split pair
        assertSplitPairIsCorrect(activityA, activityB, mCustomizedSplitAttributes,
                mSplitInfoConsumer);
    }
}
