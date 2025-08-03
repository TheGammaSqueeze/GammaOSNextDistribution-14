/*
 * Copyright 2024 The Android Open Source Project
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

package android.view.accessibility.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.view.accessibility.AccessibilityEvent.CONTENT_CHANGE_TYPE_TEXT;
import static android.view.accessibility.AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED;

import static org.junit.Assert.assertEquals;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.app.Activity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.os.Bundle;
import android.platform.test.annotations.RequiresFlagsEnabled;
import android.platform.test.flag.junit.CheckFlagsRule;
import android.platform.test.flag.junit.DeviceFlagsValueProvider;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

/** Class for testing {@link AccessibilityEvent} and caching. */
@RunWith(AndroidJUnit4.class)
public class AccessibilityEventAndCacheTest {
    private static final long DEFAULT_TIMEOUT_MS = 2000;

    // In case throttling is refreshed between children, this test uses 3 child views.
    private static final int NUM_CHILD_VIEWS = 3;
    private final List<TextView> mChildViews = new ArrayList<>();

    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;
    private final ActivityTestRule<TestActivity> mActivityRule =
            new ActivityTestRule<>(TestActivity.class, false, false);
    private final AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();
    private final InstrumentedAccessibilityServiceTestRule<SpeakingAccessibilityService>
            mInstrumentedAccessibilityServiceRule =
            new InstrumentedAccessibilityServiceTestRule<>(
                    SpeakingAccessibilityService.class, false);

    @Rule
    public final RuleChain mRuleChain =
            RuleChain.outerRule(mActivityRule)
                    .around(mInstrumentedAccessibilityServiceRule)
                    .around(mDumpOnFailureRule);

    @Rule
    public final CheckFlagsRule mCheckFlagsRule = DeviceFlagsValueProvider.createCheckFlagsRule();

    @Before
    public void setUp() throws Throwable {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
        final Activity activity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        mInstrumentedAccessibilityServiceRule.enableService();

        mActivityRule.runOnUiThread(() -> {
            final LinearLayout list = activity.findViewById(R.id.buttonsParent);
            assertEquals(NUM_CHILD_VIEWS, list.getChildCount());
            for (int i = 0; i < NUM_CHILD_VIEWS; i++) {
                mChildViews.add((TextView) list.getChildAt(i));
            }
        });
    }

    @Test
    @RequiresFlagsEnabled(android.view.accessibility.Flags.FLAG_FIX_MERGED_CONTENT_CHANGE_EVENT)
    public void testSimultaneousChangesUpdatesAllChildNodes() throws Exception {
        // Makes sure that all nodes are fetched in client side.
        final List<AccessibilityNodeInfo> nodes = sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByText("Text");
        final AccessibilityNodeInfo listNode = nodes.get(0).getParent();

        // Update text and fetch it from client.
        sUiAutomation.executeAndWaitForEvent(
                () -> sInstrumentation.runOnMainSync(() -> {
                    for (int i = 0; i < NUM_CHILD_VIEWS; i++) {
                        mChildViews.get(i).setText("new " + i);
                    }
                }),
                event ->
                        event.getEventType() == TYPE_WINDOW_CONTENT_CHANGED
                                && (event.getContentChangeTypes() & CONTENT_CHANGE_TYPE_TEXT) != 0
                                && TextUtils.equals("new 0", listNode.getChild(0).getText())
                                && TextUtils.equals("new 1", listNode.getChild(1).getText())
                                && TextUtils.equals("new 2", listNode.getChild(2).getText()),
                DEFAULT_TIMEOUT_MS);
    }

    /** Activity for this test containing three text views. */
    public static class TestActivity extends AccessibilityTestActivity {
        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.event_and_cache_test);
        }
    }
}
