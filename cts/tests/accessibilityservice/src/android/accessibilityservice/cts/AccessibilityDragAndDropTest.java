/*
 * Copyright (C) 2021 The Android Open Source Project
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

package android.accessibilityservice.cts;

import static android.accessibilityservice.cts.utils.ActivityLaunchUtils.launchActivityAndWaitForItToBeOnscreen;
import static android.accessibilityservice.cts.utils.AsyncUtils.DEFAULT_TIMEOUT_MS;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibilityservice.cts.activities.AccessibilityDragAndDropActivity;
import android.accessibilityservice.cts.utils.AccessibilityEventFilterUtils;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.Presubmit;
import android.view.View;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;
import android.widget.TextView;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.compatibility.common.util.CddTest;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;

@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityDragAndDropTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private AccessibilityDragAndDropActivity mActivity;
    private TextView mSourceView;
    private UiAutomation.AccessibilityEventFilter mDragStartedFilter =
            AccessibilityEventFilterUtils.filterWindowContentChangedWithChangeTypes(
                    AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_STARTED);

    private ActivityTestRule<AccessibilityDragAndDropActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityDragAndDropActivity.class, false, false);

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mDumpOnFailureRule);

    @BeforeClass
    public static void oneTimeSetup() throws Exception {
        sInstrumentation = InstrumentationRegistry.getInstrumentation();
        sUiAutomation = sInstrumentation.getUiAutomation();
    }

    @AfterClass
    public static void postTestTearDown() {
        sUiAutomation.destroy();
    }

    @Before
    public void setUp() throws Exception {
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
        mSourceView = mActivity.findViewById(R.id.source);
    }

    @After
    public void tearDown() {
        // Reset system drag state
        if (mSourceView != null) {
            mSourceView.cancelDragAndDrop();
        }
    }

    @Test
    public void testStartDrag_eventSentAndActionsUpdated() throws Throwable {
        AccessibilityEvent startEvent = performActionAndWaitForEvent(mSourceView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START, mDragStartedFilter);
        assertNotNull("Did not receive CONTENT_CHANGE_TYPE_DRAG_STARTED", startEvent);

        final AccessibilityNodeInfo sourceNode = getSourceNode();
        assertNodeAction(sourceNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL);

        final AccessibilityNodeInfo targetNode = getTargetNode();
        assertNodeAction(targetNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP);
    }

    @Test
    public void testCancelDrag_eventSentAndActionsUpdated() throws Throwable {
        performActionAndWaitForEvent(mSourceView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START,
                mDragStartedFilter);

        AccessibilityEvent cancelEvent = performActionAndWaitForEvent(mSourceView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL,
                AccessibilityEventFilterUtils.filterWindowContentChangedWithChangeTypes(
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_CANCELLED));

        assertNotNull("Did not receive CONTENT_CHANGE_TYPE_DRAG_CANCELLED",
                cancelEvent);

        final AccessibilityNodeInfo sourceNode = getSourceNode();
        assertNoNodeAction(sourceNode,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL);
        assertNodeAction(sourceNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START);

        final AccessibilityNodeInfo targetNode = getTargetNode();
        assertNoNodeAction(targetNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP);
    }

    @Test
    public void testDrop_eventSentAndActionsUpdated() throws Throwable {
        performActionAndWaitForEvent(mSourceView,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START, mDragStartedFilter);
        final TextView target = mActivity.findViewById(R.id.target);
        AccessibilityEvent dropEvent = performActionAndWaitForEvent(target,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP,
                AccessibilityEventFilterUtils.filterWindowContentChangedWithChangeTypes(
                        AccessibilityEvent.CONTENT_CHANGE_TYPE_DRAG_DROPPED));

        assertNotNull("Did not receive CONTENT_CHANGE_TYPE_DRAG_DROPPED",
                dropEvent);

        final AccessibilityNodeInfo targetNode = getTargetNode();
        assertEquals("Target text was: " + targetNode.getText(), mSourceView.getText(),
                targetNode.getText());
        assertNoNodeAction(targetNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_DROP);

        final AccessibilityNodeInfo sourceNode = getSourceNode();
        assertNoNodeAction(sourceNode,
                AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_CANCEL);
        assertNodeAction(sourceNode, AccessibilityNodeInfo.AccessibilityAction.ACTION_DRAG_START);
    }

    private AccessibilityEvent performActionAndWaitForEvent(View view,
            AccessibilityNodeInfo.AccessibilityAction action,
            UiAutomation.AccessibilityEventFilter filter) throws Throwable {
        AccessibilityEvent awaitedEvent =
                sUiAutomation.executeAndWaitForEvent(
                        () -> {
                            mActivity.runOnUiThread(new Runnable() {
                                @Override
                                public void run() {
                                    view.performAccessibilityAction(action.getId(), null);
                                }
                            });
                        },
                        filter,
                        DEFAULT_TIMEOUT_MS);
        return awaitedEvent;
    }

    private AccessibilityNodeInfo getSourceNode() {
        return sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/source").get(0);
    }

    private AccessibilityNodeInfo getTargetNode() {
        return sUiAutomation.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(
                        "android.accessibilityservice.cts:id/target").get(0);
    }

    private void assertNoNodeAction(
            AccessibilityNodeInfo info, AccessibilityNodeInfo.AccessibilityAction action) {
        assertFalse("Node has action: " + action.toString(),
                info.getActionList().contains(action));
    }

    private void assertNodeAction(
            AccessibilityNodeInfo info, AccessibilityNodeInfo.AccessibilityAction action) {
        assertTrue("Node does not have action: " + action.toString(),
                info.getActionList().contains(action));
    }
}
