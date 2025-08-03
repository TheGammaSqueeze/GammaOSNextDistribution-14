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
package android.accessibility.microbenchmark;

import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.microbenchmark.Microbenchmark;
import android.platform.test.rule.DropCachesRule;
import android.platform.test.rule.PressHomeRule;
import android.view.ViewGroup;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.ext.junit.rules.ActivityScenarioRule;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

@RunWith(Microbenchmark.class)
public class AccessibilityNodeMicrobenchmark {
    private static final String TAG = "AccessibilityMicrobenchmark";
    private static final String PACKAGE_NAME = AccessibilityTestActivity.class.getPackageName();
    private static final String POPUP_BUTTON_ID = PACKAGE_NAME + ":id/openDialogBtn";
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    // Accessibility node prefetching doesn't exceed 50 nodes, so any more is redundant.
    private static final int NODE_ITERATIONS = AccessibilityNodeInfo.MAX_NUMBER_OF_PREFETCHED_NODES;

    ActivityScenarioRule<AccessibilityTestActivity> mActivityScenarioRule =
            new ActivityScenarioRule(AccessibilityTestActivity.class);
    Activity mActivity;

    @Rule
    public RuleChain rules =
            RuleChain.outerRule(new DropCachesRule())
                    .around(new PressHomeRule())
                    .around(mActivityScenarioRule);

    @Before
    public void setup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        mActivityScenarioRule.getScenario().onActivity(activity -> mActivity = activity);

        sInstrumentation.runOnMainSync(
                () -> {
                    ViewGroup parent = mActivity.findViewById(R.id.linearLayout);
                    for (int i = 0; i < NODE_ITERATIONS; i++) {
                        TextView text = new TextView(mActivity);
                        text.setText("Breadth " + i);
                        parent.addView(text);
                    }
                });
        sUiAutomation.clearCache();
    }

    @Test
    public void testPrefetching() throws Exception {
        sUiAutomation.getRootInActiveWindow();
        Thread.sleep(1000);
        // Sleep to allow time for prefetching (time spent in test should not impact perf output).
    }

    @Test
    public void testOpenPopupMenu() {
        sUiAutomation
                .getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(POPUP_BUTTON_ID)
                .get(0)
                .performAction(AccessibilityNodeInfo.ACTION_CLICK);
    }
}
