/*
 * Copyright (C) 2020 The Android Open Source Project
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
package com.android.car.rotary;

import static android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_SYSTEM;

import static com.android.car.ui.utils.RotaryConstants.ROTARY_VERTICALLY_SCROLLABLE;

import static com.google.common.truth.Truth.assertThat;

import android.accessibilityservice.AccessibilityServiceInfo;
import android.app.Activity;
import android.app.UiAutomation;
import android.content.Intent;
import android.graphics.Rect;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;
import android.widget.Button;

import androidx.annotation.LayoutRes;
import androidx.recyclerview.widget.RecyclerView;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;

import com.android.car.rotary.Navigator.FindRotateTargetResult;
import com.android.car.rotary.ui.TestRecyclerViewAdapter;
import com.android.car.ui.recyclerview.CarUiRecyclerView;

import org.junit.After;
import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

@RunWith(AndroidJUnit4.class)
public class NavigatorTest {

    private static final String HOST_APP_PACKAGE_NAME = "host.app.package.name";
    private static final String CLIENT_APP_PACKAGE_NAME = "client.app.package.name";

    private static UiAutomation sUiAutomoation;
    private static int sOriginalFlags;

    private final List<AccessibilityNodeInfo> mNodes = new ArrayList<>();

    private ActivityTestRule<NavigatorTestActivity> mActivityRule;
    private Intent mIntent;
    private Rect mDisplayBounds;
    private Rect mHunWindowBounds;
    private Navigator mNavigator;
    private AccessibilityNodeInfo mWindowRoot;
    private NodeBuilder mNodeBuilder;

    @BeforeClass
    public static void oneTimeSetup() {
        sUiAutomoation = InstrumentationRegistry.getInstrumentation().getUiAutomation();

        // FLAG_RETRIEVE_INTERACTIVE_WINDOWS is necessary to reliably access the root window.
        AccessibilityServiceInfo serviceInfo = sUiAutomoation.getServiceInfo();
        sOriginalFlags = serviceInfo.flags;
        serviceInfo.flags |= AccessibilityServiceInfo.FLAG_RETRIEVE_INTERACTIVE_WINDOWS;
        sUiAutomoation.setServiceInfo(serviceInfo);
    }

    @AfterClass
    public static void oneTimeTearDown() {
        AccessibilityServiceInfo serviceInfo = sUiAutomoation.getServiceInfo();
        serviceInfo.flags = sOriginalFlags;
        sUiAutomoation.setServiceInfo(serviceInfo);
    }

    @Before
    public void setUp() {
        mActivityRule = new ActivityTestRule<>(NavigatorTestActivity.class);
        mIntent = new Intent();
        mIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK | Intent.FLAG_ACTIVITY_NEW_TASK);
        mDisplayBounds = new Rect(0, 0, 1080, 920);
        mHunWindowBounds = new Rect(50, 10, 950, 200);
        // The values of displayWidth and displayHeight affects testFindNudgeTargetFocusArea5
        mNavigator = new Navigator(/* displayWidth= */ mDisplayBounds.right,
                /* displayHeight= */ mDisplayBounds.bottom,
                /* hun left */ mHunWindowBounds.left,
                /* hun right */ mHunWindowBounds.right,
                /* showHunOnBottom= */ false);
        mNavigator.setNodeCopier(MockNodeCopierProvider.get());
        mNodeBuilder = new NodeBuilder(new ArrayList<>());
    }

    @After
    public void tearDown() {
        mActivityRule.finishActivity();
        Utils.recycleNode(mWindowRoot);
        Utils.recycleNodes(mNodes);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *              root
     *               |
     *           focusArea
     *          /    |    \
     *        /      |     \
     *    button1 button2 button3
     * </pre>
     */
    @Test
    public void testFindRotateTarget() {
        initActivity(R.layout.navigator_find_rotate_target_test_activity);

        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo button3 = createNode("button3");

        int direction = View.FOCUS_FORWARD;

        // Rotate once, the focus should move from button1 to button2.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button1, direction, 1);
        assertThat(target.node).isEqualTo(button2);
        assertThat(target.advancedCount).isEqualTo(1);

        Utils.recycleNode(target.node);

        // Rotate twice, the focus should move from button1 to button3.
        target = mNavigator.findRotateTarget(button1, direction, 2);
        assertThat(target.node).isEqualTo(button3);
        assertThat(target.advancedCount).isEqualTo(2);

        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                     root
     *                    /    \
     *                   /      \
     *      focusParkingView   focusArea
     *                           /    \
     *                          /      \
     *                       button1  button2
     * </pre>
     */
    @Test
    public void testFindRotateTargetNoWrapAround() {
        initActivity(R.layout.navigator_find_rotate_target_no_wrap_test_1_activity);

        AccessibilityNodeInfo button2 = createNode("button2");

        int direction = View.FOCUS_FORWARD;

        // Rotate at the end of focus area, no wrap-around should happen.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button2, direction, 1);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                     root
     *                    /    \
     *                   /      \
     *           focusArea  genericFocusParkingView
     *            /    \
     *           /      \
     *       button1  button2
     * </pre>
     */
    @Test
    public void testFindRotateTargetNoWrapAroundWithGenericFpv() {
        initActivity(R.layout.navigator_find_rotate_target_no_wrap_test_1_generic_fpv_activity);

        AccessibilityNodeInfo button2 = createNode("button2");

        int direction = View.FOCUS_FORWARD;

        // Rotate at the end of focus area, no wrap-around should happen.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button2, direction, 1);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                          root
     *                         /  |  \
     *                       /    |    \
     *                     /      |      \
     *      focusParkingView   button1   button2
     * </pre>
     */
    @Test
    public void testFindRotateTargetNoWrapAround2() {
        initActivity(R.layout.navigator_find_rotate_target_no_wrap_test_2_activity);

        AccessibilityNodeInfo button2 = createNode("button2");

        int direction = View.FOCUS_FORWARD;

        // Rotate at the end of focus area, no wrap-around should happen.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button2, direction, 1);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                          root
     *                         /  |  \
     *                       /    |    \
     *                     /      |      \
     *              button1   button2  genericFocusParkingView
     * </pre>
     */
    @Test
    public void testFindRotateTargetNoWrapAround2WithGenericFpv() {
        initActivity(R.layout.navigator_find_rotate_target_no_wrap_test_2_generic_fpv_activity);

        AccessibilityNodeInfo button2 = createNode("button2");

        int direction = View.FOCUS_FORWARD;

        // Rotate at the end of focus area, no wrap-around should happen.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button2, direction, 1);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *      ============ focus area ============
     *      =                                  =
     *      =  *****     recycler view    **** =
     *      =  *                             * =
     *      =  *  ........ text 1   ........ * =
     *      =  *  .        visible         . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  *  ........ text 2   ........ * =
     *      =  *  .        visible         . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  *  ........ text 3   ........ * =
     *      =  *  .        offscreen ....... * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  ******************************* =
     *      =                                  =
     *      ============ focus area ============
     * </pre>
     */
    @Test
    public void testFindRotateTargetDoesNotSkipOffscreenNode() {
        initActivity(
                R.layout.navigator_find_rotate_target_does_not_skip_offscreen_node_test_activity);

        Activity activity = mActivityRule.getActivity();
        CarUiRecyclerView carUiRecyclerView = activity.findViewById(R.id.scrollable);
        carUiRecyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 3);
            adapter.setItemsFocusable(true);
            carUiRecyclerView.setAdapter(adapter);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo text1 = createNodeByText("Test Item 1");
        AccessibilityNodeInfo text2 = createNodeByText("Test Item 2");

        int direction = View.FOCUS_FORWARD;

        FindRotateTargetResult target = mNavigator.findRotateTarget(text1, direction, 1);
        assertThat(target.node).isEqualTo(text2);
        Utils.recycleNode(target.node);

        AccessibilityNodeInfo text3 = createNodeByText("Test Item 3");
        assertThat(text3).isNull();

        target = mNavigator.findRotateTarget(text2, direction, 1);
        // Need to query for text3 after the rotation, so that it is visible on the screen for the
        // instrumentation to pick it up.
        text3 = createNodeByText("Test Item 3");
        assertThat(target.node).isEqualTo(text3);
        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *              root
     *               |
     *           focusArea
     *          /    |    \
     *        /      |     \
     *    button1 button2 button3
     * </pre>
     * where {@code button2} is not focusable.
     */
    @Test
    public void testFindRotateTargetSkipNodeThatCannotPerformFocus() {
        initActivity(R.layout.navigator_find_rotate_target_test_activity);

        Activity activity = mActivityRule.getActivity();
        View rootView = activity.findViewById(R.id.root);
        rootView.post(() -> {
            Button button2 = activity.findViewById(R.id.button2);
            button2.setFocusable(false);
        });

        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button3 = createNode("button3");

        int direction = View.FOCUS_FORWARD;

        // Rotate from button1, it should skip the empty view.
        FindRotateTargetResult target = mNavigator.findRotateTarget(button1, direction, 1);
        assertThat(target.node).isEqualTo(button3);

        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                          root
     *                         /  |  \
     *                       /    |    \
     *                     /      |      \
     *              button1   scrollable  button2
     *                       recyclerView
     *                            |
     *                      non-focusable
     * </pre>
     */
    @Test
    public void testFindRotateTargetReturnScrollableContainer() {
        initActivity(R.layout.navigator_find_rotate_target_scrollable_container_test_activity);

        Activity activity = mActivityRule.getActivity();
        RecyclerView recyclerView = activity.findViewById(R.id.scrollable);
        recyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 1);
            recyclerView.setAdapter(adapter);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo windowRoot = sUiAutomoation.getRootInActiveWindow();
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo scrollable = createNode("scrollable");

        int direction = View.FOCUS_FORWARD;

        FindRotateTargetResult target = mNavigator.findRotateTarget(button1, direction, 1);
        assertThat(target.node).isEqualTo(scrollable);

        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                          root
     *                         /  |  \
     *                       /    |    \
     *                     /      |      \
     *                   /        |        \
     *             button1  non-scrollable  button2
     *                       recyclerView
     * </pre>
     */
    @Test
    public void testFindRotateTargetSkipScrollableContainer() {
        initActivity(
                R.layout.navigator_find_rotate_target_skip_scrollable_container_test_1_activity);

        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");

        int direction = View.FOCUS_FORWARD;

        FindRotateTargetResult target = mNavigator.findRotateTarget(button1, direction, 1);
        assertThat(target.node).isEqualTo(button2);

        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *                     root
     *                    /    \
     *                  /       \
     *    focusParkingView    scrollable
     *                        container
     *                           /    \
     *                          /      \
     *                  focusable1    focusable2
     * </pre>
     */
    @Test
    public void testFindRotateTargetSkipScrollableContainer2() {
        initActivity(
                R.layout.navigator_find_rotate_target_skip_scrollable_container_test_2_activity);

        Activity activity = mActivityRule.getActivity();
        RecyclerView recyclerView = activity.findViewById(R.id.scrollable);
        recyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 2);
            adapter.setItemsFocusable(true);
            recyclerView.setAdapter(adapter);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo focusable1 = createNodeByText("Test Item 1");
        AccessibilityNodeInfo focusable2 = createNodeByText("Test Item 2");

        int direction = View.FOCUS_BACKWARD;

        FindRotateTargetResult target = mNavigator.findRotateTarget(focusable2, direction, 2);
        assertThat(target.node).isEqualTo(focusable1);
        assertThat(target.advancedCount).isEqualTo(1);

        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following node tree:
     * <pre>
     *             node
     * </pre>
     */
    @Test
    public void testFindRotateTargetWithOneNode() {
        initActivity(
                R.layout.navigator_find_rotate_target_one_node_test_activity);

        AccessibilityNodeInfo node = createNode("node");

        int direction = View.FOCUS_BACKWARD;

        FindRotateTargetResult target = mNavigator.findRotateTarget(node, direction, 1);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following layout:
     * <pre>
     *      ============ focus area 1 ==========
     *      =                                  =
     *      =  ***** scrollable container **** =
     *      =  *                             * =
     *      =  *  ........ text 1   ........ * =
     *      =  *  .                        . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  *  ........ text 2   ........ * =
     *      =  *  .                        . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  ******************************* =
     *      =                                  =
     *      ============ focus area 1 ==========
     *      ============ focus area 2 ==========
     *      =     ........ text 3   ........   =
     *      =     .                        .   =
     *      =     ..........................   =
     *      ============ focus area 2 ==========
     * </pre>
     */
    @Test
    public void testFindRotateTargetInScrollableContainer1() {
        initActivity(
                R.layout.navigator_find_rotate_target_in_scrollable_container_test_1_activity);

        Activity activity = mActivityRule.getActivity();
        RecyclerView recyclerView = activity.findViewById(R.id.scrollable);
        recyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 2);
            adapter.setItemsFocusable(true);
            recyclerView.setAdapter(adapter);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo text1 = createNodeByText("Test Item 1");
        AccessibilityNodeInfo text2 = createNodeByText("Test Item 2");

        int direction = View.FOCUS_FORWARD;

        // Rotate once, the focus should move from text1 to text2.
        FindRotateTargetResult target = mNavigator.findRotateTarget(text1, direction, 1);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);

        // Rotate twice, the focus should move from text1 to text2 since text3 is not a
        // descendant of the scrollable container.
        target = mNavigator.findRotateTarget(text1, direction, 2);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);

        // Rotate three times should do the same.
        target = mNavigator.findRotateTarget(text1, direction, 3);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findRotateTarget} in the following layout:
     * <pre>
     *      ============ focus area ============
     *      =                                  =
     *      =  ***** scrollable container **** =
     *      =  *                             * =
     *      =  *  ........ text 1   ........ * =
     *      =  *  .        visible         . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  *  ........ text 2   ........ * =
     *      =  *  .        visible         . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  *  ........ text 3   ........ * =
     *      =  *  .        not visible     . * =
     *      =  *  .......................... * =
     *      =  *                             * =
     *      =  ******************************* =
     *      =                                  =
     *      ============ focus area ============
     * </pre>
     * where {@code text 3} is off the screen.
     */
    @Test
    public void testFindRotateTargetInScrollableContainer2() {
        initActivity(
                R.layout.navigator_find_rotate_target_in_scrollable_container_test_2_activity);

        Activity activity = mActivityRule.getActivity();
        RecyclerView recyclerView = activity.findViewById(R.id.scrollable);
        recyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 3);
            adapter.setItemsFocusable(true);
            recyclerView.setAdapter(adapter);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();

        AccessibilityNodeInfo text1 = createNodeByText("Test Item 1");
        AccessibilityNodeInfo text2 = createNodeByText("Test Item 2");

        int direction = View.FOCUS_FORWARD;

        // Rotate once, the focus should move from text1 to text2.
        FindRotateTargetResult target = mNavigator.findRotateTarget(text1, direction, 1);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);

        // Rotate twice, the focus should move from text1 to text2 since text3 is off the
        // screen.
        target = mNavigator.findRotateTarget(text1, direction, 2);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);

        // Rotate three times should do the same.
        target = mNavigator.findRotateTarget(text1, direction, 3);
        assertThat(target.node).isEqualTo(text2);
        assertThat(target.advancedCount).isEqualTo(1);
        Utils.recycleNode(target.node);
    }

    /**
     * Tests {@link Navigator#findScrollableContainer} in the following node tree:
     * <pre>
     *                root
     *                 |
     *                 |
     *             focusArea
     *              /     \
     *            /         \
     *        scrolling    button2
     *        container
     *           |
     *           |
     *       container
     *           |
     *           |
     *        button1
     * </pre>
     */
    @Test
    public void testFindScrollableContainer() {
        initActivity(R.layout.navigator_find_scrollable_container_test_activity);

        AccessibilityNodeInfo scrollableContainer = createNode("scrollableContainer");
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");

        AccessibilityNodeInfo target = mNavigator.findScrollableContainer(button1);
        assertThat(target).isEqualTo(scrollableContainer);
        Utils.recycleNodes(target);

        target = mNavigator.findScrollableContainer(button2);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findFocusableDescendantInDirection} going
     * {@link View#FOCUS_BACKWARD} in the following node tree:
     * <pre>
     *                     root
     *                   /      \
     *                 /          \
     *         container1        container2
     *           /   \             /   \
     *         /       \         /       \
     *     button1   button2  button3  button4
     * </pre>
     */
    @Test
    public void testFindFocusableVisibleDescendantInDirectionBackward() {
        initActivity(R.layout.navigator_find_focusable_descendant_test_activity);

        AccessibilityNodeInfo container1 = createNode("container1");
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo container2 = createNode("container2");
        AccessibilityNodeInfo button3 = createNode("button3");
        AccessibilityNodeInfo button4 = createNode("button4");

        int direction = View.FOCUS_BACKWARD;

        AccessibilityNodeInfo target = mNavigator.findFocusableDescendantInDirection(container2,
                button4, direction);
        assertThat(target).isEqualTo(button3);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container2, button3, direction);
        assertThat(target).isNull();
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container1, button2, direction);
        assertThat(target).isEqualTo(button1);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container1, button1, direction);
        assertThat(target).isNull();
        Utils.recycleNode(target);
    }

    /**
     * Tests {@link Navigator#findFocusableDescendantInDirection} going
     * {@link View#FOCUS_FORWARD} in the following node tree:
     * <pre>
     *                     root
     *                   /      \
     *                 /          \
     *         container1        container2
     *           /   \             /   \
     *         /       \         /       \
     *     button1   button2  button3  button4
     * </pre>
     */
    @Test
    public void testFindFocusableVisibleDescendantInDirectionForward() {
        initActivity(R.layout.navigator_find_focusable_descendant_test_activity);

        AccessibilityNodeInfo container1 = createNode("container1");
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo container2 = createNode("container2");
        AccessibilityNodeInfo button3 = createNode("button3");
        AccessibilityNodeInfo button4 = createNode("button4");

        int direction = View.FOCUS_FORWARD;

        AccessibilityNodeInfo target = mNavigator.findFocusableDescendantInDirection(container1,
                button1, direction);
        assertThat(target).isEqualTo(button2);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container1, button2, direction);
        assertThat(target).isNull();

        target = mNavigator.findFocusableDescendantInDirection(container2, button3, direction);
        assertThat(target).isEqualTo(button4);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container2, button4, direction);
        assertThat(target).isNull();
    }

    /**
     * Tests {@link Navigator#findNextFocusableDescendant} in the following node tree:
     * <pre>
     *                     root
     *                      |
     *                      |
     *                  container
     *               /    /   \   \
     *            /      /     \      \
     *     button1  button2  button3  button4
     * </pre>
     * where {@code button3} and {@code button4} have empty bounds.
     */
    @Test
    public void testFindNextFocusableDescendantWithEmptyBounds() {
        initActivity(R.layout.navigator_find_focusable_descendant_empty_bounds_test_activity);

        AccessibilityNodeInfo container = createNode("container");
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo button3 = createNode("button3");
        AccessibilityNodeInfo button4 = createNode("button4");

        int direction = View.FOCUS_FORWARD;

        AccessibilityNodeInfo target =
                mNavigator.findFocusableDescendantInDirection(container, button1, direction);
        assertThat(target).isEqualTo(button2);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container, button2, direction);
        assertThat(target).isEqualTo(button1);
        Utils.recycleNode(target);

        target = mNavigator.findFocusableDescendantInDirection(container, button3, direction);
        assertThat(target).isEqualTo(button1);
        Utils.recycleNode(target);

        // Wrap around since there is no Focus Parking View present.
        target = mNavigator.findFocusableDescendantInDirection(container, button4, direction);
        assertThat(target).isEqualTo(button1);
        Utils.recycleNode(target);
    }

    /**
     * Tests {@link Navigator#findFirstFocusableDescendant} in the following node tree:
     * <pre>
     *                     root
     *                   /      \
     *                 /          \
     *         container1        container2
     *           /   \             /   \
     *         /       \         /       \
     *     button1   button2  button3  button4
     * </pre>
     * where {@code button1} and {@code button2} are disabled.
     */
    @Test
    public void testFindFirstFocusableDescendant() {
        initActivity(R.layout.navigator_find_focusable_descendant_test_activity);

        Activity activity = mActivityRule.getActivity();
        View rootView = activity.findViewById(R.id.root);
        rootView.post(() -> {
            Button button1View = activity.findViewById(R.id.button1);
            button1View.setEnabled(false);
            Button button2View = activity.findViewById(R.id.button2);
            button2View.setEnabled(false);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // When searching for the target node, even though the states of the views are correct
        // (i.e., button1View and button2View have been disabled), the states of the nodes might
        // not be up to date (i.e., button1 and button2 haven't been disabled yet) because they're
        // fetched from the node pool. So new nodes are created here to ensure the nodes are up to
        // date with the most recent state changes of the views they represent.
        AccessibilityNodeInfo button1 = createNode("button1");
        assertThat(button1.isEnabled()).isFalse();
        AccessibilityNodeInfo button2 = createNode("button2");
        assertThat(button2.isEnabled()).isFalse();

        AccessibilityNodeInfo root = createNode("root");
        AccessibilityNodeInfo button3 = createNode("button3");

        AccessibilityNodeInfo target = mNavigator.findFirstFocusableDescendant(root);
        assertThat(target).isEqualTo(button3);
        Utils.recycleNode(target);
    }

    /**
     * Tests {@link Navigator#findLastFocusableDescendant} in the following node tree:
     * <pre>
     *                     root
     *                   /      \
     *                 /          \
     *         container1        container2
     *           /   \             /   \
     *         /       \         /       \
     *     button1   button2  button3  button4
     * </pre>
     * where {@code button3} and {@code button4} are disabled.
     */
    @Test
    public void testFindLastFocusableDescendant() {
        initActivity(R.layout.navigator_find_focusable_descendant_test_activity);

        Activity activity = mActivityRule.getActivity();
        View rootView = activity.findViewById(R.id.root);
        rootView.post(() -> {
            Button button3View = activity.findViewById(R.id.button3);
            button3View.setEnabled(false);
            Button button4View = activity.findViewById(R.id.button4);
            button4View.setEnabled(false);
        });

        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        // When searching for the target node, even though the states of the views are correct
        // (i.e., button3View and button4View have been disabled), the states of the nodes might
        // not be up to date (i.e., button3 and button4 haven't been disabled yet) because they're
        // fetched from the node pool. So new nodes are created here to ensure the nodes are up to
        // date with the most recent state changes of the views they represent.
        AccessibilityNodeInfo button3 = createNode("button3");
        assertThat(button3.isEnabled()).isFalse();
        AccessibilityNodeInfo button4 = createNode("button4");
        assertThat(button4.isEnabled()).isFalse();

        AccessibilityNodeInfo root = createNode("root");
        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo target = mNavigator.findLastFocusableDescendant(root);
        assertThat(target).isEqualTo(button2);
        Utils.recycleNode(target);
    }


    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     * <pre>
     *
     *            =====focusArea1==============
     *            =  =========focusArea2====  =
     *            =  =       *view*        =  =
     *            =  =======================  =
     *            =                           =
     *            =                           =
     *            =   *scrollableContainer*   =
     *            =                           =
     *            =============================
     * </pre>
     * Where scrollableContainer has the same size as focusArea1. The top offset of focusArea1
     * equals the height of focusArea2.
     */
    @Test
    public void test_findNudgeTargetFocusArea_fromScrollableContainer() {
        initActivity(R.layout.navigator_find_nudge_target_focus_area_1_test_activity);
        Activity activity = mActivityRule.getActivity();
        CarUiRecyclerView carUiRecyclerView = activity.findViewById(R.id.scrollable_container);
        carUiRecyclerView.post(() -> {
            TestRecyclerViewAdapter adapter = new TestRecyclerViewAdapter(activity, 20);
            carUiRecyclerView.setAdapter(adapter);
            carUiRecyclerView.getView().requestFocus();
        });
        InstrumentationRegistry.getInstrumentation().waitForIdleSync();
        View focusedView = carUiRecyclerView.getView().findFocus();
        assertThat(focusedView.getContentDescription()).isEqualTo(ROTARY_VERTICALLY_SCROLLABLE);

        AccessibilityNodeInfo currentFocusArea = createNode("focus_area1");

        // Only an AccessibilityService with the permission to retrieve the active window content
        // can create an AccessibilityWindowInfo. So the AccessibilityWindowInfo and the associated
        // AccessibilityNodeInfos have to be mocked.
        AccessibilityWindowInfo window = new WindowBuilder()
                .setRoot(mWindowRoot)
                .setBoundsInScreen(mWindowRoot.getBoundsInScreen())
                .build();
        AccessibilityNodeInfo sourceNode = mNodeBuilder
                .setWindow(window)
                .setBoundsInScreen(mWindowRoot.getBoundsInScreen())
                .setParent(currentFocusArea)
                .setRotaryContainer()
                .build();
        // Though there are 20 children in the layout, only one child is mocked. This is fine as
        // long as the bounds are correct so that it can occupy the entire container.
        AccessibilityNodeInfo childNode = mNodeBuilder
                .setBoundsInScreen(mWindowRoot.getBoundsInScreen())
                .setParent(sourceNode)
                .build();

        List<AccessibilityWindowInfo> windows = Collections.singletonList(window);

        int direction = View.FOCUS_UP;
        AccessibilityNodeInfo targetFocusArea = createNode("focus_area2");
        AccessibilityNodeInfo result = mNavigator.findNudgeTargetFocusArea(
                windows, sourceNode, currentFocusArea, direction);
        assertThat(result).isEqualTo(targetFocusArea);

        // Note: only real nodes (and windows) need to be recycled.
        Utils.recycleNode(result);
    }

    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     * <pre>
     *    ========topLeft focus area========    ========topRight focus area========
     *    =                                =    =                                 =
     *    =  .............  .............  =    =  .............                  =
     *    =  .           .  .           .  =    =  .           .                  =
     *    =  . topLeft1  .  .  topLeft2 .  =    =  . topRight1 .                  =
     *    =  .           .  .           .  =    =  .           .                  =
     *    =  .............  .............  =    =  .............                  =
     *    =                                =    =                                 =
     *    ==================================    ===================================
     *
     *    =======middleLeft focus area======
     *    =                                =
     *    =  .............  .............  =
     *    =  .           .  .           .  =
     *    =  .middleLeft1.  .middleLeft2.  =
     *    =  . disabled  .  . disabled  .  =
     *    =  .............  .............  =
     *    =                                =
     *    ==================================
     *
     *    =======bottomLeft focus area======
     *    =                                =
     *    =  .............  .............  =
     *    =  .           .  .           .  =
     *    =  .bottomLeft1.  .bottomLeft2.  =
     *    =  .           .  .           .  =
     *    =  .............  .............  =
     *    =                                =
     *    ==================================
     * </pre>
     */
    @Test
    public void testFindNudgeTargetFocusArea2() {
        initActivity(R.layout.navigator_find_nudge_target_focus_area_2_test_activity);

        // The only way to create a AccessibilityWindowInfo in the test is via mock.
        AccessibilityWindowInfo window = new WindowBuilder()
                .setRoot(mWindowRoot)
                .setBoundsInScreen(mWindowRoot.getBoundsInScreen())
                .build();
        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(window);

        // Nudge down from topLeft1.
        AccessibilityNodeInfo topLeftFocusArea = createNode("top_left");
        AccessibilityNodeInfo bottomLeftFocusArea = createNode("bottom_left");
        AccessibilityNodeInfo topLeft1 = createNode("top_left1");
        AccessibilityNodeInfo mockTopLeft1 = mNodeBuilder
                .setWindow(window)
                .setBoundsInScreen(topLeft1.getBoundsInScreen())
                .setParent(topLeftFocusArea)
                .build();
        AccessibilityNodeInfo target1 = mNavigator.findNudgeTargetFocusArea(
                windows, mockTopLeft1, topLeftFocusArea, View.FOCUS_DOWN);
        assertThat(target1).isEqualTo(bottomLeftFocusArea);

        // Reach to the boundary.
        AccessibilityNodeInfo bottomLeft1 = createNode("bottom_left1");
        AccessibilityNodeInfo mockBottomLeft1 = mNodeBuilder
                .setWindow(window)
                .setBoundsInScreen(bottomLeft1.getBoundsInScreen())
                .setParent(bottomLeftFocusArea)
                .build();
        AccessibilityNodeInfo target2 = mNavigator.findNudgeTargetFocusArea(
                windows, mockBottomLeft1, bottomLeftFocusArea, View.FOCUS_DOWN);
        assertThat(target2).isNull();

        // Nudge to the right.
        AccessibilityNodeInfo topRightFocusArea = createNode("top_right");
        AccessibilityNodeInfo target3 = mNavigator.findNudgeTargetFocusArea(
                windows, mockBottomLeft1, bottomLeftFocusArea, View.FOCUS_RIGHT);
        assertThat(target3).isEqualTo(topRightFocusArea);

        Utils.recycleNodes(target1, target2, target3);
    }

    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     *
     * App window:
     * <pre>
     *
     *    ============focus area1===========
     *    =                                =
     *    =  .............                 =
     *    =  .   view1   .                 =
     *    =  .............                 =
     *    =                                =
     *    ==================================
     *
     *</pre>
     *
     * IME window:
     *<pre>
     *
     *    ============focus area2===========
     *    =                                =
     *    =  .............                 =
     *    =  .           .                 =
     *    =  .   key1    .                 =
     *    =  .           .                 =
     *    =  .............                 =
     *    =                                =
     *    ==================================
     *    ============focus area3===========
     *    =                                =
     *    =  .............                 =
     *    =  .           .                 =
     *    =  .   key2    .                 =
     *    =  . (focused) .                 =
     *    =  .............                 =
     *    =                                =
     *    ==================================
     *
     * </pre>
     * where App window and IME window overlap at the bottom.
     */
    @Test
    public void testFindNudgeTargetFocusArea3() {
        // App window.
        Rect appWindowBounds = new Rect(0,0, 1080, 600);
        AccessibilityNodeInfo appRoot = mNodeBuilder
                .setBoundsInScreen(appWindowBounds)
                .build();
        AccessibilityWindowInfo appWindow = new WindowBuilder()
                .setRoot(appRoot)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(appWindowBounds)
                .build();
        AccessibilityNodeInfo focusArea1 = mNodeBuilder
                .setBoundsInScreen(appWindowBounds)
                .setFocusArea()
                .setParent(appRoot)
                .build();
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(focusArea1)
                .build();

        // IME window.
        Rect imeWindowBounds = new Rect(0,528, 1080, 600);
        AccessibilityNodeInfo imeRoot = mNodeBuilder
                .setBoundsInScreen(imeWindowBounds)
                .build();
        AccessibilityWindowInfo imeWindow = new WindowBuilder()
                .setRoot(imeRoot)
                .setType(TYPE_INPUT_METHOD)
                .setBoundsInScreen(imeWindowBounds)
                .build();
        AccessibilityNodeInfo focusArea2 = mNodeBuilder
                .setBoundsInScreen(new Rect(0, 528, 1080, 564))
                .setFocusArea()
                .setParent(imeRoot)
                .build();
        AccessibilityNodeInfo key1 = mNodeBuilder
                .setWindow(imeWindow)
                .setParent(focusArea2)
                .build();
        AccessibilityNodeInfo focusArea3 = mNodeBuilder
                .setBoundsInScreen(new Rect(0, 564, 1080, 600))
                .setFocusArea()
                .setParent(imeRoot)
                .build();
        AccessibilityNodeInfo key2 = mNodeBuilder
                .setWindow(imeWindow)
                .setBoundsInScreen(new Rect(96, 566, 139, 598))
                .setParent(focusArea3)
                .build();

        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(appWindow);
        windows.add(imeWindow);

        // Nudge up from the second row of IME, it should focus on the first row of IME.
        AccessibilityNodeInfo targetFocusArea =
                mNavigator.findNudgeTargetFocusArea(windows, key2, focusArea3, View.FOCUS_UP);
        assertThat(targetFocusArea).isEqualTo(focusArea2);
    }

    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     *
     * <pre>
     *
     *    ======System window(focus area1)===========
     *    =                                         =
     *    =           .............                 =
     *    =           .   view1   .                 =
     *    =           .............                 =
     *    =                                         =
     *    ===========================================
     *
     *    ===============App window==================
     *    =                                         =
     *    =   ===TaskView window(focus area2)====   =
     *    =   =     ........................    =   =
     *    =   =     .         view2        .    =   =
     *    =   =     ........................    =   =
     *    =   ===================================   =
     *    =                                         =
     *    ===========================================
     *</pre>
     *
     */
    @Test
    public void testFindNudgeTargetFocusArea4() {
        // System window.
        Rect systemWindowBounds = new Rect(0, 0, 1080, 200);
        AccessibilityNodeInfo systemRoot = mNodeBuilder
                .setBoundsInScreen(systemWindowBounds)
                .build();
        AccessibilityWindowInfo systemWindow = new WindowBuilder()
                .setRoot(systemRoot)
                .setType(TYPE_SYSTEM)
                .setBoundsInScreen(systemWindowBounds)
                .build();
        AccessibilityNodeInfo focusArea1 = mNodeBuilder
                .setBoundsInScreen(systemWindowBounds)
                .setFocusArea()
                .setParent(systemRoot)
                .build();
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(focusArea1)
                .setBoundsInScreen(new Rect(500, 0, 600, 200))
                .build();

        // App window.
        final int appTaskId = 10;
        AccessibilityNodeInfo appRoot = mNodeBuilder
                .setBoundsInScreen(mDisplayBounds)
                .build();
        AccessibilityWindowInfo appWindow = new WindowBuilder()
                .setRoot(appRoot)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(mDisplayBounds)
                .setTaskId(appTaskId)
                .build();

        // TaskView window.
        final int taskViewTaskId = 11;
        Rect taskViewWindowBounds = new Rect(400, 0, 1080, 600);
        AccessibilityNodeInfo taskViewRoot = mNodeBuilder
                .setBoundsInScreen(taskViewWindowBounds)
                .build();
        AccessibilityWindowInfo taskViewWindow = new WindowBuilder()
                .setRoot(taskViewRoot)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(taskViewWindowBounds)
                .setTaskId(taskViewTaskId)
                .build();
        AccessibilityNodeInfo focusArea2 = mNodeBuilder
                .setBoundsInScreen(taskViewWindowBounds)
                .setFocusArea()
                .setParent(taskViewRoot)
                .build();
        AccessibilityNodeInfo view2 = mNodeBuilder
                .setWindow(taskViewWindow)
                .setParent(focusArea2)
                .setBoundsInScreen(new Rect(500, 400, 600, 500))
                .build();

        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(systemWindow);
        windows.add(appWindow);
        windows.add(taskViewWindow);

        mNavigator.updateAppWindowTaskId(systemWindow);
        mNavigator.updateAppWindowTaskId(appWindow);
        mNavigator.updateAppWindowTaskId(taskViewWindow);

        // Nudge up from view2 in TaskView window, it should focus in focusArea1 in system window.
        AccessibilityNodeInfo targetFocusArea =
                mNavigator.findNudgeTargetFocusArea(windows, view2, focusArea2, View.FOCUS_UP);
        assertThat(targetFocusArea).isEqualTo(focusArea1);
    }

    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     *
     * <pre>
     *
     *    ======System window(focus area1)===========
     *    =                                         =
     *    =           .............                 =
     *    =           .   view1   .                 =
     *    =           .............                 =
     *    =                                         =
     *    ===========================================
     *
     *    ===============App window==================
     *    =                                         =
     *    =   ====Dialog window(focus area2)=====   =
     *    =   =     ........................    =   =
     *    =   =     .         view2        .    =   =
     *    =   =     ........................    =   =
     *    =   ===================================   =
     *    =                                         =
     *    ===========================================
     *</pre>
     *
     */
    @Test
    public void testFindNudgeTargetFocusArea5() {
        // System window.
        Rect systemWindowBounds = new Rect(0, 0, 1080, 200);
        AccessibilityNodeInfo systemRoot = mNodeBuilder
                .setBoundsInScreen(systemWindowBounds)
                .build();
        AccessibilityWindowInfo systemWindow = new WindowBuilder()
                .setRoot(systemRoot)
                .setType(TYPE_SYSTEM)
                .setBoundsInScreen(systemWindowBounds)
                .build();
        AccessibilityNodeInfo focusArea1 = mNodeBuilder
                .setBoundsInScreen(systemWindowBounds)
                .setFocusArea()
                .setParent(systemRoot)
                .build();
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(focusArea1)
                .setBoundsInScreen(new Rect(500, 0, 600, 200))
                .build();

        // App window.
        final int appTaskId = 10;
        AccessibilityNodeInfo appRoot = mNodeBuilder
                .setBoundsInScreen(mDisplayBounds)
                .build();
        AccessibilityWindowInfo appWindow = new WindowBuilder()
                .setRoot(appRoot)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(mDisplayBounds)
                .setTaskId(appTaskId)
                .build();

        // Dialog window.
        Rect dialogWindowBounds = new Rect(400, 0, 1080, 600);
        AccessibilityNodeInfo dialogRoot = mNodeBuilder
                .setBoundsInScreen(dialogWindowBounds)
                .build();
        AccessibilityWindowInfo dialogWindow = new WindowBuilder()
                .setRoot(dialogRoot)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(dialogWindowBounds)
                .setTaskId(appTaskId)
                .build();
        AccessibilityNodeInfo focusArea2 = mNodeBuilder
                .setBoundsInScreen(dialogWindowBounds)
                .setFocusArea()
                .setParent(dialogRoot)
                .build();
        AccessibilityNodeInfo view2 = mNodeBuilder
                .setWindow(dialogWindow)
                .setParent(focusArea2)
                .setBoundsInScreen(new Rect(500, 400, 600, 500))
                .build();

        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(systemWindow);
        windows.add(appWindow);
        windows.add(dialogWindow);

        mNavigator.updateAppWindowTaskId(systemWindow);
        mNavigator.updateAppWindowTaskId(appWindow);
        mNavigator.updateAppWindowTaskId(dialogWindow);

        // Nudge up from view2 in Dialog window, the focus shouldn't move.
        AccessibilityNodeInfo targetFocusArea =
                mNavigator.findNudgeTargetFocusArea(windows, view2, focusArea2, View.FOCUS_UP);
        assertThat(targetFocusArea).isNull();
    }

    /**
     * Tests {@link Navigator#findNudgeTargetFocusArea} in the following layout:
     *
     * App window:
     *<pre>
     *
     *    ============focus area===========
     *    =                                =
     *    =  .............                 =    ............
     *    =  .           .                 =    .  view2   .
     *    =  .   view1    .                =    ............
     *    =  .           .                 =                  ...........
     *    =  .............                 =                  .  view3  .
     *    =                                =                  ...........
     *    ==================================
     *
     * </pre>
     */
    @Test
    public void testFindNudgeTargetFocusArea6() {
        Rect windowBounds = new Rect(0, 0, 1080, 600);
        AccessibilityNodeInfo root = mNodeBuilder
                .setBoundsInScreen(windowBounds)
                .setFocusable(false)
                .build();
        AccessibilityWindowInfo window = new WindowBuilder()
                .setRoot(root)
                .setType(TYPE_APPLICATION)
                .setBoundsInScreen(windowBounds)
                .build();
        AccessibilityNodeInfo focusArea = mNodeBuilder
                .setBoundsInScreen(new Rect(0, 0, 500, 600))
                .setFocusArea()
                .setParent(root)
                .build();
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(focusArea)
                .setBoundsInScreen(new Rect(0, 200, 100, 300))
                .setWindow(window)
                .build();

        // Orphan views.
        AccessibilityNodeInfo view2 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(new Rect(700, 200, 800, 300))
                .build();
        AccessibilityNodeInfo view3 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(new Rect(900, 300, 1000, 400))
                .build();


        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(window);

        // Nudge left from view1; the target focus area should be the root.
        AccessibilityNodeInfo targetFocusArea =
                mNavigator.findNudgeTargetFocusArea(windows, view1, focusArea, View.FOCUS_RIGHT);
        assertThat(targetFocusArea).isEqualTo(root);
    }

    /**
     * Tests {@link Navigator#findFirstOrphan} in the following layout:
     *
     * App window:
     *<pre>
     *
     *    ============focus area===========
     *    =                                =
     *    =  .............                 =    ............
     *    =  .           .                 =    .  view2   .
     *    =  .   view1    .                =    ............
     *    =  .           .                 =                  ...........
     *    =  .............                 =                  .  view3  .
     *    =                                =                  ...........
     *    ==================================
     *
     * </pre>
     */
    @Test
    public void testFindFirstOrphan() {
        Rect windowBounds = new Rect(0, 0, 1080, 600);
        AccessibilityNodeInfo root = mNodeBuilder
                .setBoundsInScreen(windowBounds)
                .setFocusable(false)
                .build();
        AccessibilityNodeInfo focusArea = mNodeBuilder
                .setBoundsInScreen(new Rect(0, 0, 500, 600))
                .setFocusArea()
                .setParent(root)
                .build();
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(focusArea)
                .setBoundsInScreen(new Rect(0, 200, 100, 300))
                .build();

        // Orphan views.
        AccessibilityNodeInfo view2 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(new Rect(700, 200, 800, 300))
                .build();
        AccessibilityNodeInfo view3 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(new Rect(900, 300, 1000, 400))
                .build();

        AccessibilityNodeInfo firstOrphan = mNavigator.findFirstOrphan(root);
        assertThat(firstOrphan).isEqualTo(view2);
    }

    /**
     * Tests {@link Navigator#findFocusParkingView} in the following node tree:
     * <pre>
     *                      root
     *                     /    \
     *                    /      \
     *              parent       button
     *               |
     *               |
     *        focusParkingView
     * </pre>
     */
    @Test
    public void testFindFocusParkingView() {
        initActivity(R.layout.navigator_find_focus_parking_view_test_activity);

        // The only way to create a AccessibilityWindowInfo in the test is via mock.
        AccessibilityWindowInfo window = new WindowBuilder().setRoot(mWindowRoot).build();
        AccessibilityNodeInfo button = mNodeBuilder.setWindow(window).build();

        AccessibilityNodeInfo fpv = createNode("focusParkingView");
        AccessibilityNodeInfo result = mNavigator.findFocusParkingView(button);
        assertThat(result).isEqualTo(fpv);
        Utils.recycleNode(result);
    }

    @Test
    public void testfindHunWindow() {
        // The only way to create a AccessibilityWindowInfo in the test is via mock.
        AccessibilityWindowInfo hunWindow = new WindowBuilder()
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM)
                .setBoundsInScreen(mHunWindowBounds)
                .build();
        AccessibilityWindowInfo window2 = new WindowBuilder()
                .setType(AccessibilityWindowInfo.TYPE_APPLICATION)
                .setBoundsInScreen(mHunWindowBounds)
                .build();
        AccessibilityWindowInfo window3 = new WindowBuilder()
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM)
                .build();
        List<AccessibilityWindowInfo> windows = new ArrayList<>();
        windows.add(window2);
        windows.add(window3);
        windows.add(hunWindow);

        AccessibilityWindowInfo result = mNavigator.findHunWindow(windows);
        assertThat(result).isEqualTo(hunWindow);
    }

    @Test
    public void testIsHunWindow() {
        // The only way to create an AccessibilityWindowInfo in the test is via mock.
        AccessibilityWindowInfo window = new WindowBuilder()
                .setType(AccessibilityWindowInfo.TYPE_SYSTEM)
                .setBoundsInScreen(mHunWindowBounds)
                .build();
        boolean isHunWindow = mNavigator.isHunWindow(window);
        assertThat(isHunWindow).isTrue();
    }

    /**
     * Tests {@link Navigator#getAncestorFocusArea} in the following node tree:
     * <pre>
     *                      root
     *                     /    \
     *                    /      \
     *            focusArea    button3
     *             /    \
     *            /      \
     *        parent  button2
     *          |
     *          |
     *       button1
     * </pre>
     */
    @Test
    public void testGetAncestorFocusArea() {
        initActivity(R.layout.navigator_get_ancestor_focus_area_test_activity);

        AccessibilityNodeInfo focusArea = createNode("focusArea");
        AccessibilityNodeInfo button1 = createNode("button1");
        AccessibilityNodeInfo result1 = mNavigator.getAncestorFocusArea(button1);
        assertThat(result1).isEqualTo(focusArea);

        AccessibilityNodeInfo button2 = createNode("button2");
        AccessibilityNodeInfo result2 = mNavigator.getAncestorFocusArea(button2);
        assertThat(result2).isEqualTo(focusArea);

        AccessibilityNodeInfo button3 = createNode("button3");
        AccessibilityNodeInfo result3 = mNavigator.getAncestorFocusArea(button3);
        assertThat(result3).isEqualTo(mWindowRoot);

        Utils.recycleNodes(result1, result2, result3);
    }

    /**
     * Tests {@link Navigator#getRoot} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testGetRoot_returnHostAppRoot() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        assertThat(mNavigator.getRoot(button2)).isEqualTo(hostAppRoot);
    }

    /**
     * Tests {@link Navigator#getRoot} in the following node tree:
     * <pre>
     *                     root
     *                     /    \
     *                    /      \
     *              button1    viewGroup1
     *                             |
     *                        viewGroup2
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testGetRoot_returnNodeRoot() {
        AccessibilityNodeInfo root = mNodeBuilder.build();
        AccessibilityNodeInfo button1 = mNodeBuilder.setParent(root).build();
        AccessibilityNodeInfo viewGroup1 = mNodeBuilder.setParent(root).build();
        AccessibilityNodeInfo viewGroup2 = mNodeBuilder.setParent(viewGroup1).build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(viewGroup2)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder.setParent(viewGroup2).build();

        assertThat(mNavigator.getRoot(button2)).isEqualTo(root);
    }

    /**
     * Tests {@link Navigator#getRoot} in the window:
     * <pre>
     *                     windowRoot
     *                        ...
     *                      button
     * </pre>
     * where {@code windowRoot} is the root node of the window containing {@code button}.
     */
    @Test
    public void testGetRoot_returnWindowRoot() {
        AccessibilityNodeInfo root = mNodeBuilder.build();
        AccessibilityWindowInfo window = new WindowBuilder().setRoot(root).build();
        AccessibilityNodeInfo button = mNodeBuilder.setWindow(window).build();
        assertThat(mNavigator.getRoot(button)).isEqualTo(root);
    }

    /**
     * Tests {@link Navigator#findFocusParkingViewInRoot} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testFindFocusParkingViewInRoot() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        assertThat(mNavigator.findFocusParkingViewInRoot(clientAppRoot))
                .isEqualTo(focusParkingView);
    }

    /**
     * Tests {@link Navigator#findSurfaceViewInRoot} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testFindSurfaceViewInRoot() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        assertThat(mNavigator.findSurfaceViewInRoot(clientAppRoot)).isEqualTo(surfaceView);
    }

    /**
     * Tests {@link Navigator#getDescendantHostRoot} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testGetDescendantHostRoot() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        assertThat(mNavigator.getDescendantHostRoot(clientAppRoot)).isEqualTo(hostAppRoot);
    }

   /**
     * Tests {@link Navigator#findNonEmptyFocusAreas} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testFindNonEmptyFocusAreas_inHostApp() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        AccessibilityWindowInfo window = new WindowBuilder().setRoot(clientAppRoot).build();

        // The client app shouldn't contain any explicit focus areas.
        List<AccessibilityNodeInfo> focusAreas = mNavigator.findNonEmptyFocusAreas(window);
        assertThat(focusAreas.size()).isEqualTo(0);
    }

    /**
     * Tests {@link Navigator#maybeAddImplicitFocusArea} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2
     * </pre>
     */
    @Test
    public void testMaybeAddImplicitFocusArea_inHostApp() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        AccessibilityWindowInfo window = new WindowBuilder().setRoot(clientAppRoot).build();
        // The root node of host app should be added as an implicit focus area.
        List<AccessibilityNodeInfo> focusAreas = new ArrayList<>();
        List<Rect> focusAreasBounds = new ArrayList<>();
        mNavigator.maybeAddImplicitFocusArea(window, focusAreas, focusAreasBounds);
        assertThat(focusAreas.size()).isEqualTo(1);
        assertThat(focusAreas.get(0)).isEqualTo(hostAppRoot);
    }

    /**
     * Tests {@link Navigator#findFocusedNodeInRoot} in the following node tree:
     * <pre>
     *                  clientAppRoot
     *                     /    \
     *                    /      \
     *              button1  surfaceView(focused)
     *                             |
     *                        hostAppRoot
     *                           /    \
     *                         /       \
     *            focusParkingView     button2(focused)
     * </pre>
     */
    @Test
    public void testFindFocusedNodeInRoot_inHostApp() {
        mNavigator.addClientApp(CLIENT_APP_PACKAGE_NAME);
        mNavigator.mSurfaceViewHelper.mHostApp = HOST_APP_PACKAGE_NAME;

        AccessibilityNodeInfo clientAppRoot = mNodeBuilder
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo button1 = mNodeBuilder
                .setParent(clientAppRoot)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo surfaceView = mNodeBuilder
                .setParent(clientAppRoot)
                .setFocused(true)
                .setPackageName(CLIENT_APP_PACKAGE_NAME)
                .setClassName(Utils.SURFACE_VIEW_CLASS_NAME)
                .build();

        AccessibilityNodeInfo hostAppRoot = mNodeBuilder
                .setParent(surfaceView)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();
        AccessibilityNodeInfo focusParkingView = mNodeBuilder
                .setParent(hostAppRoot)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .setFpv()
                .build();
        AccessibilityNodeInfo button2 = mNodeBuilder
                .setParent(hostAppRoot)
                .setFocused(true)
                .setPackageName(HOST_APP_PACKAGE_NAME)
                .build();

        assertThat(mNavigator.findFocusedNodeInRoot(clientAppRoot)).isEqualTo(button2);
    }

    @Test
    public void testComputeMinimumBoundsForOrphanDescendants() {
        Rect rootBounds = new Rect(0, 0, 1080, 600);
        AccessibilityNodeInfo root = mNodeBuilder
                .setBoundsInScreen(rootBounds)
                .setFocusable(false)
                .build();

        Rect bounds1 = new Rect(700, 200, 800, 300);
        AccessibilityNodeInfo view1 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(bounds1)
                .build();
        Rect bounds2 = new Rect(900, 300, 1000, 400);
        AccessibilityNodeInfo view2 = mNodeBuilder
                .setParent(root)
                .setBoundsInScreen(bounds2)
                .build();

        Rect expectedBounds = new Rect(bounds1);
        expectedBounds.union(bounds2);
        assertThat(mNavigator.computeMinimumBoundsForOrphanDescendants(root))
                .isEqualTo(expectedBounds);
    }

    /**
     * Starts the test activity with the given layout and initializes the root
     * {@link AccessibilityNodeInfo}.
     */
    private void initActivity(@LayoutRes int layoutResId) {
        mIntent.putExtra(NavigatorTestActivity.KEY_LAYOUT_ID, layoutResId);
        mActivityRule.launchActivity(mIntent);

        mWindowRoot = sUiAutomoation.getRootInActiveWindow();
    }

    /**
     * Returns the {@link AccessibilityNodeInfo} related to the provided viewId. Returns null if no
     * such node exists. Callers should ensure {@link #initActivity} has already been called. Caller
     * shouldn't recycle the result because it will be recycled in {@link #tearDown}.
     */
    private AccessibilityNodeInfo createNode(String viewId) {
        String fullViewId = "com.android.car.rotary.tests.unit:id/" + viewId;
        List<AccessibilityNodeInfo> nodes =
                mWindowRoot.findAccessibilityNodeInfosByViewId(fullViewId);
        if (nodes.isEmpty()) {
            L.e("Failed to create node by View ID " + viewId);
            return null;
        }
        mNodes.addAll(nodes);
        return nodes.get(0);
    }

    /**
     * Returns the {@link AccessibilityNodeInfo} of the view containing the provided text. Returns
     * null if no such node exists. Callers should ensure {@link #initActivity} has already
     * been called and also recycle the result.
     */
    private AccessibilityNodeInfo createNodeByText(String text) {
        List<AccessibilityNodeInfo> nodes = mWindowRoot.findAccessibilityNodeInfosByText(text);
        if (nodes.isEmpty()) {
            L.e("Failed to create node by text '" + text + "'");
            return null;
        }
        return nodes.get(0);
    }
}
