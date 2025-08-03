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

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

import android.accessibility.cts.common.AccessibilityDumpOnFailureRule;
import android.accessibility.cts.common.InstrumentedAccessibilityService;
import android.accessibility.cts.common.InstrumentedAccessibilityServiceTestRule;
import android.accessibilityservice.AccessibilityServiceInfo;
import android.accessibilityservice.cts.activities.AccessibilityCacheActivity;
import android.app.Instrumentation;
import android.app.UiAutomation;
import android.platform.test.annotations.AppModeFull;
import android.platform.test.annotations.Presubmit;
import android.text.TextUtils;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.test.InstrumentationRegistry;
import androidx.test.rule.ActivityTestRule;
import androidx.test.runner.AndroidJUnit4;

import com.android.compatibility.common.util.CddTest;

import org.junit.AfterClass;
import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.RuleChain;
import org.junit.runner.RunWith;

import java.util.ArrayList;
import java.util.List;

@AppModeFull
@RunWith(AndroidJUnit4.class)
@CddTest(requirements = {"3.10/C-1-1,C-1-2"})
@Presubmit
public class AccessibilityCacheTest {
    private static Instrumentation sInstrumentation;
    private static UiAutomation sUiAutomation;

    private InstrumentedAccessibilityService mService;
    private AccessibilityCacheActivity mActivity;

    private AccessibilityDumpOnFailureRule mDumpOnFailureRule =
            new AccessibilityDumpOnFailureRule();

    private final ActivityTestRule<AccessibilityCacheActivity> mActivityRule =
            new ActivityTestRule<>(AccessibilityCacheActivity.class, false, false);

    private InstrumentedAccessibilityServiceTestRule<InstrumentedAccessibilityService>
            mInstrumentedAccessibilityServiceRule = new InstrumentedAccessibilityServiceTestRule<>(
            InstrumentedAccessibilityService.class, false);

    private static final String SUBTREE_ROOT_ID = "android.accessibilityservice.cts:id/subtreeRoot";

    @Rule
    public final RuleChain mRuleChain = RuleChain
            .outerRule(mActivityRule)
            .around(mInstrumentedAccessibilityServiceRule)
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
        mService = mInstrumentedAccessibilityServiceRule.enableService();
        AccessibilityServiceInfo info = mService.getServiceInfo();
        info.flags &= ~AccessibilityServiceInfo.FLAG_INCLUDE_NOT_IMPORTANT_VIEWS;
        mService.setServiceInfo(info);
        mActivity = launchActivityAndWaitForItToBeOnscreen(
                sInstrumentation, sUiAutomation, mActivityRule);
    }

    @Test
    public void enable_cacheEnabled() {
        assertTrue(mService.setCacheEnabled(false));
        assertFalse("Failed to disable", mService.isCacheEnabled());

        assertTrue(mService.setCacheEnabled(true));
        assertTrue("Failed to enable", mService.isCacheEnabled());
    }

    @Test
    public void disable_cacheDisabled() {
        assertTrue(mService.setCacheEnabled(false));
        assertFalse("Failed to disable", mService.isCacheEnabled());
    }

    @Test
    public void queryNode_nodeIsInCache() {
        AccessibilityNodeInfo info = mService.getRootInActiveWindow();
        assertTrue("Node is not in cache", mService.isNodeInCache(info));
    }

    @Test
    public void invalidateNode_nodeInCacheInvalidated() {
        AccessibilityNodeInfo info = mService.getRootInActiveWindow();
        assertTrue(mService.clearCachedSubtree(info));
        assertFalse("Node is still in cache", mService.isNodeInCache(info));
    }

    @Test
    public void invalidateNode_subtreeInCacheInvalidated() {
        // Subtree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo root = mService.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(SUBTREE_ROOT_ID).get(0);
        assertThat(root.getChildCount(), is(2));
        AccessibilityNodeInfo child0 = root.getChild(0);
        AccessibilityNodeInfo child1 = root.getChild(1);
        AccessibilityNodeInfo grandChild = child1.getChild(0);

        assertTrue(mService.clearCachedSubtree(root));

        assertFalse("Root is in cache", mService.isNodeInCache(root));
        assertFalse("Child0 is in cache", mService.isNodeInCache(child0));
        assertFalse("Child1 is in cache", mService.isNodeInCache(child1));
        assertFalse("Grandchild is in cache", mService.isNodeInCache(grandChild));
    }

    @Test
    public void clear_cacheInvalidated() {
        AccessibilityNodeInfo root = mService.getRootInActiveWindow();

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        allNodes.add(root);
        getNodes(allNodes, root);

        assertTrue(mService.clearCache());

        for (AccessibilityNodeInfo node : allNodes) {
            assertFalse("Node " + node.getContentDescription() + " is in cache",
                    mService.isNodeInCache(node));
        }
    }

    @Test
    public void getChild_descendantNotPrefetched() {
        // Subtree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo frameRoot = mService.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(SUBTREE_ROOT_ID).get(0);
        assertThat(frameRoot.getChildCount(), is(2));
        AccessibilityNodeInfo textViewChild = frameRoot.getChild(0);
        AccessibilityNodeInfo linearLayoutChild = frameRoot.getChild(1);
        AccessibilityNodeInfo frameGrandChild = linearLayoutChild.getChild(0);

        // Clear cache.
        assertTrue(mService.clearCachedSubtree(frameRoot));
        frameRoot.getChild(1, AccessibilityNodeInfo.FLAG_PREFETCH_SIBLINGS
                | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);
        assertTrue("Root is not in cache", mService.isNodeInCache(frameRoot));
        assertTrue("TextView is not in cache", mService.isNodeInCache(textViewChild));
        assertTrue("LinearLayout is not in cache", mService.isNodeInCache(linearLayoutChild));
        // No descendant prefetching
        assertFalse("Root grandchild is in cache", mService.isNodeInCache(frameGrandChild));
    }

    @Test
    public void getChild_descendantPrefetched() {
        // Subtree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo frameRoot = mService.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(SUBTREE_ROOT_ID).get(0);
        assertThat(frameRoot.getChildCount(), is(2));
        AccessibilityNodeInfo textViewChild = frameRoot.getChild(0);
        AccessibilityNodeInfo linearLayoutChild = frameRoot.getChild(1);
        AccessibilityNodeInfo frameGrandChild = linearLayoutChild.getChild(0);

        // Clear cache.
        assertTrue(mService.clearCachedSubtree(frameRoot));

        frameRoot.getChild(1, AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_DEPTH_FIRST
                | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);

        assertFalse("Root is in cache", mService.isNodeInCache(frameRoot));
        assertFalse("TextView is in cache", mService.isNodeInCache(textViewChild));
        assertTrue("LinearLayout is not in cache", mService.isNodeInCache(linearLayoutChild));
        // Descendant prefetching
        assertTrue("Root grandchild is not in cache", mService.isNodeInCache(frameGrandChild));
    }

    @Test
    public void getParent_ancestorsPrefetched() {
        // Subtree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo frameRoot = mService.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(SUBTREE_ROOT_ID).get(0);
        assertThat(frameRoot.getChildCount(), is(2));
        AccessibilityNodeInfo textViewChild = frameRoot.getChild(0);
        AccessibilityNodeInfo linearLayoutChild = frameRoot.getChild(1);
        AccessibilityNodeInfo frameGrandChild = linearLayoutChild.getChild(0);

        // Clear cache.
        assertTrue(mService.clearCachedSubtree(frameRoot));

        frameGrandChild.getParent(AccessibilityNodeInfo.FLAG_PREFETCH_ANCESTORS
                | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);

        assertTrue("Root is not in cache", mService.isNodeInCache(frameRoot));
        assertFalse("TextView is in cache", mService.isNodeInCache(textViewChild));
        assertTrue("linearLayout is not in cache", mService.isNodeInCache(linearLayoutChild));
        // Grandchild itself isn't in the cache
        assertFalse("root grandchild is in cache", mService.isNodeInCache(frameGrandChild));
    }

    /**
     * Tests a request that prefetches descendants with multiple strategies. This throws an
     * exception.
     */
    @Test
    public void testRequest_withMultiplePrefetchingStrategies_throwsException() {
        // Subtree is FrameLayout with TextView and LinearLayout children.
        // The LinearLayout has a TextView child.
        AccessibilityNodeInfo root = mService.getRootInActiveWindow()
                .findAccessibilityNodeInfosByViewId(SUBTREE_ROOT_ID).get(0);
        assertThat(root.getChildCount(), is(2));

        // Clear cache.
        mService.clearCachedSubtree(root);

        assertThrows(IllegalArgumentException.class, () -> {
            root.getChild(0,
                    AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_BREADTH_FIRST
                            | AccessibilityNodeInfo.FLAG_PREFETCH_DESCENDANTS_HYBRID);
        });
    }

    @Test
    public void testRequest_prefetchWithA11yWindowInfo() {
        List<AccessibilityWindowInfo> windows = mService.getWindows();
        AccessibilityWindowInfo activityWindowInfo = null;
        for (AccessibilityWindowInfo window : windows) {
            if (window.getTitle() != null
                    && TextUtils.equals(window.getTitle(), mActivity.getTitle())) {
                activityWindowInfo = window;
            }
        }

        assertNotNull(activityWindowInfo);
        AccessibilityNodeInfo windowRoot = activityWindowInfo.getRoot();

        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        allNodes.add(windowRoot); // root should not be in the cache after clearing
        getNodes(allNodes, windowRoot);

        // Clear cache.
        assertTrue(mService.clearCachedSubtree(windowRoot));

        for (AccessibilityNodeInfo node : allNodes) {
            assertFalse("Node " + node.getContentDescription() + " is in cache",
                    mService.isNodeInCache(node));
        }
    }

    @Test
    public void testRequest_prefetchWithRootInActiveWindow() {
        AccessibilityNodeInfo windowRoot = mService.getRootInActiveWindow();

        List<AccessibilityNodeInfo> allNodesExceptRoot = new ArrayList<>();
        getNodes(allNodesExceptRoot, windowRoot);

        // Clear cache.
        assertTrue(mService.clearCachedSubtree(windowRoot));

        AccessibilityNodeInfo windowRoot2 = mService.getRootInActiveWindow(
                AccessibilityNodeInfo.FLAG_PREFETCH_SIBLINGS
                        | AccessibilityNodeInfo.FLAG_PREFETCH_UNINTERRUPTIBLE);

        assertTrue("Root is in cache", mService.isNodeInCache(windowRoot2));
        for (AccessibilityNodeInfo node : allNodesExceptRoot) {
            assertFalse("Node " + node.getContentDescription() + " is in cache",
                    mService.isNodeInCache(node));
        }
    }

    @Test
    public void testUiAutomation_clearCache_cacheInvalidated() {
        AccessibilityNodeInfo root = sUiAutomation.getRootInActiveWindow();
        List<AccessibilityNodeInfo> allNodes = new ArrayList<>();
        allNodes.add(root);
        getNodes(allNodes, root);

        assertTrue(sUiAutomation.clearCache());

        for (AccessibilityNodeInfo node : allNodes) {
            assertFalse("Node " + node.getContentDescription() + " is in cache",
                    sUiAutomation.isNodeInCache(node));
        }
    }

    private void getNodes(List<AccessibilityNodeInfo> nodesList, AccessibilityNodeInfo node) {
        // Explicitly not prefetching to avoid a race condition where the cache may be populated
        // after calling clearCachedSubtree
        final int noPrefetchingStrategy = 0;
        for (int i = 0; i < node.getChildCount(); i++) {
            nodesList.add(node.getChild(i, noPrefetchingStrategy));
            getNodes(nodesList, node.getChild(i));
        }
    }
}
