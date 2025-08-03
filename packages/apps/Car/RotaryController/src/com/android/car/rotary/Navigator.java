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

import static android.app.ActivityTaskManager.INVALID_TASK_ID;
import static android.view.View.FOCUS_DOWN;
import static android.view.View.FOCUS_LEFT;
import static android.view.View.FOCUS_RIGHT;
import static android.view.View.FOCUS_UP;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD;
import static android.view.accessibility.AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD;
import static android.view.accessibility.AccessibilityNodeInfo.FOCUS_INPUT;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION;
import static android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD;

import android.content.pm.PackageManager;
import android.graphics.Rect;
import android.view.Display;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
import android.view.accessibility.AccessibilityWindowInfo;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.VisibleForTesting;

import com.android.car.ui.FocusArea;
import com.android.car.ui.FocusParkingView;
import com.android.internal.util.dump.DualDumpOutputStream;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

/**
 * A helper class used for finding the next focusable node when the rotary controller is rotated or
 * nudged.
 */
class Navigator {

    @NonNull
    private NodeCopier mNodeCopier = new NodeCopier();

    @NonNull
    private final TreeTraverser mTreeTraverser = new TreeTraverser();

    @NonNull
    @VisibleForTesting
    final SurfaceViewHelper mSurfaceViewHelper = new SurfaceViewHelper();

    private final int mHunLeft;
    private final int mHunRight;

    @View.FocusRealDirection
    private int mHunNudgeDirection;

    @NonNull
    private final Rect mAppWindowBounds;

    private int mAppWindowTaskId = INVALID_TASK_ID;

    Navigator(int displayWidth, int displayHeight, int hunLeft, int hunRight,
            boolean showHunOnBottom) {
        mHunLeft = hunLeft;
        mHunRight = hunRight;
        mHunNudgeDirection = showHunOnBottom ? FOCUS_DOWN : FOCUS_UP;
        mAppWindowBounds = new Rect(0, 0, displayWidth, displayHeight);
    }

    @VisibleForTesting
    Navigator() {
        this(0, 0, 0, 0, false);
    }

    /**
     * Updates {@link #mAppWindowTaskId} if {@code window} is a full-screen app window on the
     * default display.
     */
    void updateAppWindowTaskId(@NonNull AccessibilityWindowInfo window) {
        if (window.getType() == TYPE_APPLICATION
                && window.getDisplayId() == Display.DEFAULT_DISPLAY) {
            Rect windowBounds = new Rect();
            window.getBoundsInScreen(windowBounds);
            if (mAppWindowBounds.equals(windowBounds)) {
                mAppWindowTaskId = window.getTaskId();
                L.d("Task ID of app window: " + mAppWindowTaskId);
            }
        }
    }

    /** Initializes the package name of the host app. */
    void initHostApp(@NonNull PackageManager packageManager) {
        mSurfaceViewHelper.initHostApp(packageManager);
    }

    /** Clears the package name of the host app if the given {@code packageName} matches. */
    void clearHostApp(@NonNull String packageName) {
        mSurfaceViewHelper.clearHostApp(packageName);
    }

    /** Returns whether it supports AAOS template apps. */
    boolean supportTemplateApp() {
        return mSurfaceViewHelper.supportTemplateApp();
    }

    /** Adds the package name of the client app. */
    void addClientApp(@NonNull CharSequence clientAppPackageName) {
        mSurfaceViewHelper.addClientApp(clientAppPackageName);
    }

    /** Returns whether the given {@code node} represents a view of the host app. */
    boolean isHostNode(@NonNull AccessibilityNodeInfo node) {
        return mSurfaceViewHelper.isHostNode(node);
    }

    /** Returns whether the given {@code node} represents a view of the client app. */
    boolean isClientNode(@NonNull AccessibilityNodeInfo node) {
        return mSurfaceViewHelper.isClientNode(node);
    }

    @Nullable
    AccessibilityWindowInfo findHunWindow(@NonNull List<AccessibilityWindowInfo> windows) {
        for (AccessibilityWindowInfo window : windows) {
            if (isHunWindow(window)) {
                return window;
            }
        }
        return null;
    }

    /**
     * Returns the target focusable for a rotate. The caller is responsible for recycling the node
     * in the result.
     *
     * <p>Limits navigation to focusable views within a scrollable container's viewport, if any.
     *
     * @param sourceNode    the current focus
     * @param direction     rotate direction, must be {@link View#FOCUS_FORWARD} or {@link
     *                      View#FOCUS_BACKWARD}
     * @param rotationCount the number of "ticks" to rotate. Only count nodes that can take focus
     *                      (visible, focusable and enabled). If {@code skipNode} is encountered, it
     *                      isn't counted.
     * @return a FindRotateTargetResult containing a node and a count of the number of times the
     *         search advanced to another node. The node represents a focusable view in the given
     *         {@code direction} from the current focus within the same {@link FocusArea}. If the
     *         first or last view is reached before counting up to {@code rotationCount}, the first
     *         or last view is returned. However, if there are no views that can take focus in the
     *         given {@code direction}, {@code null} is returned.
     */
    @Nullable
    FindRotateTargetResult findRotateTarget(
            @NonNull AccessibilityNodeInfo sourceNode, int direction, int rotationCount) {
        int advancedCount = 0;
        AccessibilityNodeInfo currentFocusArea = getAncestorFocusArea(sourceNode);
        AccessibilityNodeInfo candidate = copyNode(sourceNode);
        AccessibilityNodeInfo target = null;
        while (advancedCount < rotationCount) {
            AccessibilityNodeInfo nextCandidate = null;
            // Virtual View hierarchies like WebViews and ComposeViews do not support focusSearch().
            AccessibilityNodeInfo virtualViewAncestor = findVirtualViewAncestor(candidate);
            if (virtualViewAncestor != null) {
                nextCandidate =
                    findNextFocusableInVirtualRoot(virtualViewAncestor, candidate, direction);
            }
            if (nextCandidate == null) {
                // If we aren't in a virtual node hierarchy, or there aren't any more focusable
                // nodes within the virtual node hierarchy, use focusSearch().
                nextCandidate = candidate.focusSearch(direction);
            }
            AccessibilityNodeInfo candidateFocusArea =
                    nextCandidate == null ? null : getAncestorFocusArea(nextCandidate);

            // Only advance to nextCandidate if:
            // 1. it's in the same focus area,
            // 2. and it isn't a FocusParkingView (this is to prevent wrap-around when there is only
            //    one focus area in the window, including when the root node is treated as a focus
            //    area),
            // 3. and nextCandidate is different from candidate (if sourceNode is the first
            //    focusable node in the window, searching backward will return sourceNode itself).
            if (nextCandidate != null && currentFocusArea != null
                    && currentFocusArea.equals(candidateFocusArea)
                    && !Utils.isFocusParkingView(nextCandidate)
                    && !nextCandidate.equals(candidate)) {
                // We need to skip nextTargetNode if:
                // 1. it can't perform focus action (focusSearch() may return a node with zero
                //    width and height),
                // 2. or it is a scrollable container but it shouldn't be scrolled (i.e., it is not
                //    scrollable, or its descendants can take focus).
                //    When we want to focus on its element directly, we'll skip the container. When
                //    we want to focus on container and scroll it, we won't skip the container.
                if (!Utils.canPerformFocus(nextCandidate)
                        || (Utils.isScrollableContainer(nextCandidate)
                            && !Utils.canScrollableContainerTakeFocus(nextCandidate))) {
                    Utils.recycleNode(candidate);
                    Utils.recycleNode(candidateFocusArea);
                    candidate = nextCandidate;
                    continue;
                }

                // If we're navigating in a scrollable container that can scroll in the specified
                // direction and the next candidate is off-screen or there are no more focusable
                // views within the scrollable container, stop navigating so that any remaining
                // detents are used for scrolling.
                AccessibilityNodeInfo scrollableContainer = findScrollableContainer(candidate);
                AccessibilityNodeInfo.AccessibilityAction scrollAction =
                        direction == View.FOCUS_FORWARD
                                ? ACTION_SCROLL_FORWARD
                                : ACTION_SCROLL_BACKWARD;
                if (scrollableContainer != null
                        && scrollableContainer.getActionList().contains(scrollAction)
                        && (!Utils.isDescendant(scrollableContainer, nextCandidate)
                                || Utils.getBoundsInScreen(nextCandidate).isEmpty())) {
                    Utils.recycleNode(nextCandidate);
                    Utils.recycleNode(candidateFocusArea);
                    break;
                }
                Utils.recycleNode(scrollableContainer);

                Utils.recycleNode(candidate);
                Utils.recycleNode(candidateFocusArea);
                candidate = nextCandidate;
                Utils.recycleNode(target);
                target = copyNode(candidate);
                advancedCount++;
            } else {
                Utils.recycleNode(nextCandidate);
                Utils.recycleNode(candidateFocusArea);
                break;
            }
        }
        candidate.recycle();
        if (sourceNode.equals(target)) {
            L.e("Wrap-around on the same node");
            target.recycle();
            return null;
        }
        return target == null ? null : new FindRotateTargetResult(target, advancedCount);
    }

    /** Sets a NodeCopier instance for testing. */
    @VisibleForTesting
    void setNodeCopier(@NonNull NodeCopier nodeCopier) {
        mNodeCopier = nodeCopier;
        mTreeTraverser.setNodeCopier(nodeCopier);
    }

    /**
     * Returns the root node in the tree containing {@code node}. The caller is responsible for
     * recycling the result.
     */
    @NonNull
    AccessibilityNodeInfo getRoot(@NonNull AccessibilityNodeInfo node) {
        // If the node represents a view in the embedded view hierarchy hosted by a SurfaceView,
        // return the root node of the hierarchy, which is the only child of the SurfaceView node.
        if (isHostNode(node)) {
            AccessibilityNodeInfo child = mNodeCopier.copy(node);
            AccessibilityNodeInfo parent = node.getParent();
            while (parent != null && !Utils.isSurfaceView(parent)) {
                child.recycle();
                child = parent;
                parent = child.getParent();
            }
            Utils.recycleNode(parent);
            return child;
        }

        // Get the root node directly via the window.
        AccessibilityWindowInfo window = node.getWindow();
        if (window != null) {
            AccessibilityNodeInfo root = window.getRoot();
            window.recycle();
            if (root != null) {
                return root;
            }
        }

        // If the root node can't be accessed via the window, navigate up the node tree.
        AccessibilityNodeInfo child = mNodeCopier.copy(node);
        AccessibilityNodeInfo parent = node.getParent();
        while (parent != null) {
            child.recycle();
            child = parent;
            parent = child.getParent();
        }
        return child;
    }

    /**
     * Searches {@code root} and its descendants, and returns the currently focused node if it's
     * not a {@link FocusParkingView}, or returns null in other cases. The caller is responsible
     * for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findFocusedNodeInRoot(@NonNull AccessibilityNodeInfo root) {
        AccessibilityNodeInfo focusedNode = findFocusedNodeInRootInternal(root);
        if (focusedNode != null && Utils.isFocusParkingView(focusedNode)) {
            focusedNode.recycle();
            return null;
        }
        return focusedNode;
    }

    /**
     * Searches {@code root} and its descendants, and returns the currently focused node, if any,
     * or returns null if not found. The caller is responsible for recycling the result.
     */
    @Nullable
    private AccessibilityNodeInfo findFocusedNodeInRootInternal(
            @NonNull AccessibilityNodeInfo root) {
        AccessibilityNodeInfo surfaceView = null;
        if (!isClientNode(root)) {
            AccessibilityNodeInfo focusedNode = Utils.findFocusWithRetry(root);
            if (focusedNode != null && Utils.isSurfaceView(focusedNode)) {
                // The focused node represents a SurfaceView. In this case the root node is actually
                // a client node but Navigator doesn't know that because SurfaceViewHelper doesn't
                // know the package name of the client app.
                // Although the package name of the client app will be stored in SurfaceViewHelper
                // when RotaryService handles TYPE_WINDOW_STATE_CHANGED event, RotaryService may not
                // receive the event. For example, RotaryService may have been killed and restarted.
                // In this case, Navigator should store the package name.
                surfaceView = focusedNode;
                addClientApp(surfaceView.getPackageName());
            } else {
                return focusedNode;
            }
        }

        // The root node is in client app, which contains a SurfaceView to display the embedded
        // view hierarchy. In this case only search inside the embedded view hierarchy.
        if (surfaceView == null) {
            surfaceView = findSurfaceViewInRoot(root);
        }
        if (surfaceView == null) {
            L.w("Failed to find SurfaceView in client app " + root);
            return null;
        }
        if (surfaceView.getChildCount() == 0) {
            L.d("Host app is not loaded yet");
            surfaceView.recycle();
            return null;
        }
        AccessibilityNodeInfo embeddedRoot = surfaceView.getChild(0);
        surfaceView.recycle();
        if (embeddedRoot == null) {
            L.w("Failed to get the root of host app");
            return null;
        }
        AccessibilityNodeInfo focusedNode = embeddedRoot.findFocus(FOCUS_INPUT);
        embeddedRoot.recycle();
        return focusedNode;
    }

    /**
     * Searches the window containing {@code node}, and returns the node representing a {@link
     * FocusParkingView}, if any, or returns null if not found. The caller is responsible for
     * recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findFocusParkingView(@NonNull AccessibilityNodeInfo node) {
        AccessibilityNodeInfo root = getRoot(node);
        AccessibilityNodeInfo fpv = findFocusParkingViewInRoot(root);
        root.recycle();
        return fpv;
    }

    /**
     * Searches {@code root} and its descendants, and returns the node representing a {@link
     * FocusParkingView}, if any, or returns null if not found. The caller is responsible for
     * recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findFocusParkingViewInRoot(@NonNull AccessibilityNodeInfo root) {
        return mTreeTraverser.depthFirstSearch(
                root,
                /* skipPredicate= */ Utils::isFocusArea,
                /* targetPredicate= */ Utils::isFocusParkingView
        );
    }

    /**
     * Searches {@code root} and its descendants, and returns the node representing a {@link
     * android.view.SurfaceView}, if any, or returns null if not found. The caller is responsible
     * for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findSurfaceViewInRoot(@NonNull AccessibilityNodeInfo root) {
        return mTreeTraverser.depthFirstSearch(root, /* targetPredicate= */ Utils::isSurfaceView);
    }

    /**
     * Returns the best target focus area for a nudge in the given {@code direction}. The caller is
     * responsible for recycling the result.
     *
     * @param windows          a list of windows to search from
     * @param sourceNode       the current focus
     * @param currentFocusArea the current focus area
     * @param direction        nudge direction, must be {@link View#FOCUS_UP}, {@link
     *                         View#FOCUS_DOWN}, {@link View#FOCUS_LEFT}, or {@link
     *                         View#FOCUS_RIGHT}
     */
    AccessibilityNodeInfo findNudgeTargetFocusArea(
            @NonNull List<AccessibilityWindowInfo> windows,
            @NonNull AccessibilityNodeInfo sourceNode,
            @NonNull AccessibilityNodeInfo currentFocusArea,
            int direction) {
        AccessibilityWindowInfo currentWindow = sourceNode.getWindow();
        if (currentWindow == null) {
            L.e("Currently focused window is null");
            return null;
        }

        // Build a list of candidate focus areas, starting with all the other focus areas in the
        // same window as the current focus area.
        List<AccessibilityNodeInfo> candidateFocusAreas = findNonEmptyFocusAreas(currentWindow);
        for (AccessibilityNodeInfo focusArea : candidateFocusAreas) {
            if (focusArea.equals(currentFocusArea)) {
                candidateFocusAreas.remove(focusArea);
                focusArea.recycle();
                break;
            }
        }

        List<Rect> candidateFocusAreasBounds = new ArrayList<>();
        for (AccessibilityNodeInfo focusArea : candidateFocusAreas) {
            Rect bounds = Utils.getBoundsInScreen(focusArea);
            candidateFocusAreasBounds.add(bounds);
        }

        maybeAddImplicitFocusArea(currentWindow, candidateFocusAreas, candidateFocusAreasBounds);

        // If the current focus area is an explicit focus area, use its focus area bounds to find
        // nudge target as usual. Otherwise, use the tailored bounds, which was added as the last
        // element of the list in maybeAddImplicitFocusArea().
        Rect currentFocusAreaBounds;
        if (Utils.isFocusArea(currentFocusArea)) {
            currentFocusAreaBounds = Utils.getBoundsInScreen(currentFocusArea);
        } else if (candidateFocusAreasBounds.size() > 0) {
            currentFocusAreaBounds =
                    candidateFocusAreasBounds.get(candidateFocusAreasBounds.size() - 1);
        } else {
            // TODO(b/323112198): this should never happen, but let's try to recover from this.
            L.e("currentFocusArea is an implicit focus area but not added to"
                    + " currentFocusAreaBounds");
            L.d("sourceNode:" + sourceNode);
            L.d("currentFocusArea:" + currentFocusArea);
            AccessibilityNodeInfo root = getRoot(sourceNode);
            Utils.printDescendants(root, Utils.LOG_INDENT);
            Utils.recycleNode(root);

            currentFocusArea.recycle();
            currentFocusArea = getAncestorFocusArea(sourceNode);
            currentFocusAreaBounds = Utils.getBoundsInScreen(currentFocusArea);
            L.d("updated currentFocusArea:" + currentFocusArea);
        }

        if (currentWindow.getType() != TYPE_INPUT_METHOD
                || shouldNudgeOutOfIme(sourceNode, currentFocusArea, candidateFocusAreas,
                           direction)) {
            // Add candidate focus areas in other windows in the given direction.
            List<AccessibilityWindowInfo> candidateWindows = new ArrayList<>();
            boolean isSourceNodeEditable = sourceNode.isEditable();
            addWindowsInDirection(windows, currentWindow, candidateWindows, direction,
                    isSourceNodeEditable);
            currentWindow.recycle();
            for (AccessibilityWindowInfo window : candidateWindows) {
                List<AccessibilityNodeInfo> focusAreasInAnotherWindow =
                        findNonEmptyFocusAreas(window);
                candidateFocusAreas.addAll(focusAreasInAnotherWindow);

                for (AccessibilityNodeInfo focusArea : focusAreasInAnotherWindow) {
                    Rect bounds = Utils.getBoundsInScreen(focusArea);
                    candidateFocusAreasBounds.add(bounds);
                }

                maybeAddImplicitFocusArea(window, candidateFocusAreas, candidateFocusAreasBounds);
            }
        }

        Rect sourceBounds = Utils.getBoundsInScreen(sourceNode);
        // Choose the best candidate as our target focus area.
        AccessibilityNodeInfo targetFocusArea = chooseBestNudgeCandidate(sourceBounds,
                currentFocusAreaBounds, candidateFocusAreas, candidateFocusAreasBounds, direction);
        Utils.recycleNodes(candidateFocusAreas);
        return targetFocusArea;
    }

    /**
     * If there are orphan nodes in {@code window}, treats the root node of the window as an
     * implicit focus area, and add it to {@code candidateFocusAreas}. Besides, tailors its bounds
     * so that it just wraps its orphan descendants, and adds the tailored bounds to
     * {@code candidateFocusAreasBounds}.
     * Orphan nodes are focusable nodes not wrapped inside any explicitly declared focus areas.
     * It happens in two scenarios:
     * <ul>
     *     <li>The app developer wants to treat the entire window as a focus area but doesn't bother
     *         declaring a focus area to wrap around them. This is allowed.
     *     <li>The app developer intends to declare focus areas to wrap around focusable views, but
     *         misses some focusable views, causing them to be unreachable via rotary controller.
     *         This is not allowed, but RotaryService will try its best to make them reachable.
     * </ul>
     */
    @VisibleForTesting
    void maybeAddImplicitFocusArea(@NonNull AccessibilityWindowInfo window,
            @NonNull List<AccessibilityNodeInfo> candidateFocusAreas,
            @NonNull List<Rect> candidateFocusAreasBounds) {
        AccessibilityNodeInfo root = window.getRoot();
        if (root == null) {
            L.e("No root node for " + window);
            return;
        }
        // If the root node is in the client app and therefore contains a SurfaceView, skip the view
        // hierarchy of the client app, and scan the view hierarchy of the host app, which is
        // embedded in the SurfaceView.
        if (isClientNode(root)) {
            L.v("Root node is client node " + root);
            AccessibilityNodeInfo hostRoot = getDescendantHostRoot(root);
            root.recycle();
            if (hostRoot == null || !hasFocusableDescendants(hostRoot)) {
                L.w("No host node or host node has no focusable descendants " + hostRoot);
                Utils.recycleNode(hostRoot);
                return;
            }
            candidateFocusAreas.add(hostRoot);
            Rect bounds = new Rect();
            // To make things simple, just use the node's bounds. Don't tailor the bounds.
            hostRoot.getBoundsInScreen(bounds);
            candidateFocusAreasBounds.add(bounds);
            return;
        }

        Rect bounds = computeMinimumBoundsForOrphanDescendants(root);
        if (bounds.isEmpty()) {
            return;
        }
        L.w("The root node contains focusable nodes that are not inside any focus "
                + "areas: " + root);
        candidateFocusAreas.add(root);
        candidateFocusAreasBounds.add(bounds);
    }

    /**
     * Returns whether it should nudge out the IME window. If the current window is IME window and
     * there are candidate FocusAreas in it for the given direction, it shouldn't nudge out of the
     * IME window.
     */
    private boolean shouldNudgeOutOfIme(@NonNull AccessibilityNodeInfo sourceNode,
            @NonNull AccessibilityNodeInfo currentFocusArea,
            @NonNull List<AccessibilityNodeInfo> focusAreasInCurrentWindow,
            int direction) {
        if (!focusAreasInCurrentWindow.isEmpty()) {
            Rect sourceBounds = Utils.getBoundsInScreen(sourceNode);
            Rect sourceFocusAreaBounds = Utils.getBoundsInScreen(currentFocusArea);
            Rect candidateBounds = Utils.getBoundsInScreen(currentFocusArea);
            for (AccessibilityNodeInfo candidate : focusAreasInCurrentWindow) {
                if (isCandidate(sourceBounds, sourceFocusAreaBounds, candidate, candidateBounds,
                        direction)) {
                    return false;
                }
            }
        }
        return true;
    }

    private boolean containsWebViewWithFocusableDescendants(@NonNull AccessibilityNodeInfo node) {
        List<AccessibilityNodeInfo> webViews = new ArrayList<>();
        mTreeTraverser.depthFirstSelect(node, Utils::isWebView, webViews);
        if (webViews.isEmpty()) {
            return false;
        }
        boolean hasFocusableDescendant = false;
        for (AccessibilityNodeInfo webView : webViews) {
            if (webViewHasFocusableDescendants(webView)) {
                hasFocusableDescendant = true;
                break;
            }
        }
        Utils.recycleNodes(webViews);
        return hasFocusableDescendant;
    }

    private boolean webViewHasFocusableDescendants(@NonNull AccessibilityNodeInfo webView) {
        AccessibilityNodeInfo focusableDescendant = mTreeTraverser.depthFirstSearch(webView,
                Utils::canPerformFocus);
        if (focusableDescendant == null) {
            return false;
        }
        focusableDescendant.recycle();
        return true;
    }

    private boolean isWebViewWithFocusableDescendants(@NonNull AccessibilityNodeInfo node) {
        return Utils.isWebView(node) && webViewHasFocusableDescendants(node);
    }

    /**
     * Adds all the {@code windows} in the given {@code direction} of the given {@code source}
     * window to the given list if the {@code source} window is not an overlay. If it's an overlay
     * and the source node is editable, adds the IME window only. Otherwise does nothing.
     */
    private void addWindowsInDirection(@NonNull List<AccessibilityWindowInfo> windows,
            @NonNull AccessibilityWindowInfo source,
            @NonNull List<AccessibilityWindowInfo> results,
            int direction,
            boolean isSourceNodeEditable) {
        Rect sourceBounds = new Rect();
        source.getBoundsInScreen(sourceBounds);
        boolean isSourceWindowOverlayWindow = isOverlayWindow(source, sourceBounds);
        Rect destBounds = new Rect();
        for (AccessibilityWindowInfo window : windows) {
            if (window.equals(source)) {
               continue;
            }
            // Nudging out of the overlay window is not allowed unless the source node is editable
            // and the target window is an IME window. E.g., nudging from the EditText in the Dialog
            // to the IME is allowed, while nudging from the Button in the Dialog to the IME is not
            // allowed.
            if (isSourceWindowOverlayWindow
                    && (!isSourceNodeEditable || window.getType() != TYPE_INPUT_METHOD)) {
                continue;
            }

            window.getBoundsInScreen(destBounds);
            // Even if only part of destBounds is in the given direction of sourceBounds, we
            // still include it because that part may contain the target focus area.
            if (FocusFinder.isPartiallyInDirection(sourceBounds, destBounds, direction)) {
                results.add(window);
            }
        }
    }

    /**
     * Returns whether the given {@code window} with the given {@code bounds} is an overlay window.
     * <p>
     * If the source window is an application window on the default display and it's smaller than
       the display, then it's either a TaskView window or an overlay window (such as a Dialog
       window). The ID of a TaskView task is different from the full screen application, while
       the ID of an overlay task is the same with the full screen application, so task ID is used
       to decide whether it's an overlay window.
     */
    private boolean isOverlayWindow(@NonNull AccessibilityWindowInfo window, @NonNull Rect bounds) {
        return window.getType() == TYPE_APPLICATION
                && window.getDisplayId() == Display.DEFAULT_DISPLAY
                && !mAppWindowBounds.equals(bounds)
                && window.getTaskId() == mAppWindowTaskId;
    }

    /**
     * Returns whether nudging to the given {@code direction} can dismiss the given {@code window}
     * with the given {@code bounds}.
     */
    boolean isDismissible(@NonNull AccessibilityWindowInfo window,
            @NonNull Rect bounds,
            @View.FocusRealDirection int direction) {
        // Only overlay windows can be dismissed.
        if (!isOverlayWindow(window, bounds)) {
            return false;
        }
        // The window can be dismissed when part of the underlying window is not covered by it in
        // the given direction.
        switch (direction) {
            case FOCUS_UP:
                return mAppWindowBounds.top < bounds.top;
            case FOCUS_DOWN:
                return mAppWindowBounds.bottom > bounds.bottom;
            case FOCUS_LEFT:
                return mAppWindowBounds.left < bounds.left;
            case FOCUS_RIGHT:
                return mAppWindowBounds.right > bounds.right;
        }
        return false;
    }

    /**
     * Scans the view hierarchy of the given {@code window} looking for explicit focus areas with
     * focusable descendants and returns the focus areas. The caller is responsible for recycling
     * the result.
     */
    @NonNull
    @VisibleForTesting
    List<AccessibilityNodeInfo> findNonEmptyFocusAreas(@NonNull AccessibilityWindowInfo window) {
        List<AccessibilityNodeInfo> results = new ArrayList<>();
        AccessibilityNodeInfo rootNode = window.getRoot();
        if (rootNode == null) {
            L.e("No root node for " + window);
        } else if (!isClientNode(rootNode)) {
            addNonEmptyFocusAreas(rootNode, results);
        }
        // If the root node is in the client app, it won't contain any explicit focus areas, so
        // skip it.

        Utils.recycleNode(rootNode);
        return results;
    }

    /**
     * Searches from {@code clientNode}, and returns the root of the embedded view hierarchy if any,
     * or returns null if not found. The caller is responsible for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo getDescendantHostRoot(@NonNull AccessibilityNodeInfo clientNode) {
        return mTreeTraverser.depthFirstSearch(clientNode, this::isHostNode);
    }

    /**
     * Returns whether the given window is the Heads-up Notification (HUN) window. The HUN window
     * is identified by the left and right edges. The top and bottom vary depending on whether the
     * HUN appears at the top or bottom of the screen and on the height of the notification being
     * displayed so they aren't used.
     */
    boolean isHunWindow(@Nullable AccessibilityWindowInfo window) {
        if (window == null || window.getType() != AccessibilityWindowInfo.TYPE_SYSTEM) {
            return false;
        }
        Rect bounds = new Rect();
        window.getBoundsInScreen(bounds);
        return bounds.left == mHunLeft && bounds.right == mHunRight;
    }

    /**
     * Searches from the given node up through its ancestors to the containing focus area, looking
     * for a node that's marked as horizontally or vertically scrollable. Returns a copy of the
     * first such node or null if none is found. The caller is responsible for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findScrollableContainer(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.findNodeOrAncestor(node, /* stopPredicate= */ Utils::isFocusArea,
                /* targetPredicate= */ Utils::isScrollableContainer);
    }

    /**
     * Returns the previous node  before {@code referenceNode} in Tab order that can take focus or
     * the next node after {@code referenceNode} in Tab order that can take focus, depending on
     * {@code direction}. The search is limited to descendants of {@code containerNode}. Returns
     * null if there are no descendants that can take focus in the given direction. The caller is
     * responsible for recycling the result.
     *
     * @param containerNode the node with descendants
     * @param referenceNode a descendant of {@code containerNode} to start from
     * @param direction     {@link View#FOCUS_FORWARD} or {@link View#FOCUS_BACKWARD}
     * @return the node before or after {@code referenceNode} or null if none
     */
    @Nullable
    AccessibilityNodeInfo findFocusableDescendantInDirection(
            @NonNull AccessibilityNodeInfo containerNode,
            @NonNull AccessibilityNodeInfo referenceNode,
            int direction) {
        AccessibilityNodeInfo targetNode = copyNode(referenceNode);
        do {
            AccessibilityNodeInfo nextTargetNode = targetNode.focusSearch(direction);
            if (nextTargetNode == null
                    || nextTargetNode.equals(containerNode)
                    || !Utils.isDescendant(containerNode, nextTargetNode)) {
                Utils.recycleNode(nextTargetNode);
                Utils.recycleNode(targetNode);
                return null;
            }
            if (nextTargetNode.equals(referenceNode) || nextTargetNode.equals(targetNode)) {
                L.w((direction == View.FOCUS_FORWARD ? "Next" : "Previous")
                        + " node is the same node: " + referenceNode);
                Utils.recycleNode(nextTargetNode);
                Utils.recycleNode(targetNode);
                return null;
            }
            targetNode.recycle();
            targetNode = nextTargetNode;
        } while (!Utils.canTakeFocus(targetNode));
        return targetNode;
    }

    /**
     * Returns the first descendant of {@code node} which can take focus. The nodes are searched in
     * in depth-first order, not including {@code node} itself. If no descendant can take focus,
     * null is returned. The caller is responsible for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findFirstFocusableDescendant(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.depthFirstSearch(node,
                candidateNode -> candidateNode != node && Utils.canTakeFocus(candidateNode));
    }

    /**
     * Returns the first orphan descendant (focusable descendant not inside any focus areas) of
     * {@code node}. The nodes are searched in depth-first order, not including {@code node} itself.
     * If not found, null is returned. The caller is responsible for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findFirstOrphan(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.depthFirstSearch(node,
                /* skipPredicate= */ Utils::isFocusArea,
                /* targetPredicate= */ candidateNode -> candidateNode != node
                        && Utils.canTakeFocus(candidateNode));
    }

    /**
     * Returns the last descendant of {@code node} which can take focus. The nodes are searched in
     * reverse depth-first order, not including {@code node} itself. If no descendant can take
     * focus, null is returned. The caller is responsible for recycling the result.
     */
    @Nullable
    AccessibilityNodeInfo findLastFocusableDescendant(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.reverseDepthFirstSearch(node,
                candidateNode -> candidateNode != node && Utils.canTakeFocus(candidateNode));
    }

    /**
     * Scans descendants of the given {@code rootNode} looking for explicit focus areas with
     * focusable descendants and adds the focus areas to the given list. It doesn't scan inside
     * focus areas since nested focus areas aren't allowed. It ignores focus areas without
     * focusable descendants, because once we found the best candidate focus area, we don't dig
     * into other ones. If it has no descendants to take focus, the nudge will fail. The caller is
     * responsible for recycling added nodes.
     *
     * @param rootNode the root to start scanning from
     * @param results  a list of focus areas to add to
     */
    private void addNonEmptyFocusAreas(@NonNull AccessibilityNodeInfo rootNode,
            @NonNull List<AccessibilityNodeInfo> results) {
        mTreeTraverser.depthFirstSelect(rootNode,
                (focusArea) -> Utils.isFocusArea(focusArea) && hasFocusableDescendants(focusArea),
                results);
    }

    private boolean hasFocusableDescendants(@NonNull AccessibilityNodeInfo focusArea) {
        return Utils.canHaveFocus(focusArea) || containsWebViewWithFocusableDescendants(focusArea);
    }

    /**
     * Returns the minimum rectangle wrapping the given {@code node}'s orphan descendants. If
     * {@code node} has no orphan descendants, returns an empty {@link Rect}.
     */
    @NonNull
    @VisibleForTesting
    Rect computeMinimumBoundsForOrphanDescendants(
            @NonNull AccessibilityNodeInfo node) {
        Rect bounds = new Rect();
        if (Utils.isFocusArea(node) || Utils.isFocusParkingView(node)) {
            return bounds;
        }
        if (Utils.canTakeFocus(node) || isWebViewWithFocusableDescendants(node)) {
            return Utils.getBoundsInScreen(node);
        }
        for (int i = 0; i < node.getChildCount(); i++) {
            AccessibilityNodeInfo child = node.getChild(i);
            if (child == null) {
                continue;
            }
            Rect childBounds = computeMinimumBoundsForOrphanDescendants(child);
            child.recycle();
            if (childBounds != null) {
                bounds.union(childBounds);
            }
        }
        return bounds;
    }

    /**
     * Returns a copy of the best candidate from among the given {@code candidates} for a nudge
     * from {@code sourceNode} in the given {@code direction}. Returns null if none of the {@code
     * candidates} are in the given {@code direction}. The caller is responsible for recycling the
     * result.
     *
     * @param candidates could be a list of {@link FocusArea}s, or a list of focusable views
     */
    @Nullable
    private AccessibilityNodeInfo chooseBestNudgeCandidate(@NonNull Rect sourceBounds,
            @NonNull Rect sourceFocusAreaBounds,
            @NonNull List<AccessibilityNodeInfo> candidates,
            @NonNull List<Rect> candidatesBounds,
            int direction) {
        if (candidates.isEmpty()) {
            return null;
        }
        AccessibilityNodeInfo bestNode = null;
        Rect bestBounds = new Rect();
        for (int i = 0; i < candidates.size(); i++) {
            AccessibilityNodeInfo candidate = candidates.get(i);
            Rect candidateBounds = candidatesBounds.get(i);
            if (isCandidate(sourceBounds, sourceFocusAreaBounds, candidate, candidateBounds,
                    direction)) {
                if (bestNode == null || FocusFinder.isBetterCandidate(
                        direction, sourceBounds, candidateBounds, bestBounds)) {
                    bestNode = candidate;
                    bestBounds.set(candidateBounds);
                }
            }
        }
        return copyNode(bestNode);
    }

    /**
     * Returns whether the given {@code node} is a candidate from {@code sourceBounds} to the given
     * {@code direction}.
     * <p>
     * To be a candidate, the node
     * <ul>
     *     <li>must be considered a candidate by {@link FocusFinder#isCandidate} if it represents a
     *         focusable view within a focus area
     *     <li>must be in the {@code direction} of the {@code sourceFocusAreaBounds} and one of its
     *         focusable descendants must be a candidate if it represents a focus area
     * </ul>
     */
    private boolean isCandidate(@NonNull Rect sourceBounds,
            @NonNull Rect sourceFocusAreaBounds,
            @NonNull AccessibilityNodeInfo node,
            @NonNull Rect nodeBounds,
            int direction) {
        AccessibilityNodeInfo candidate = mTreeTraverser.depthFirstSearch(node,
                /* skipPredicate= */ candidateNode -> {
                    if (Utils.canTakeFocus(candidateNode)) {
                        return false;
                    }
                    // If a node can't take focus, it represents a focus area. If the focus area
                    // doesn't intersect with sourceFocusAreaBounds, and it's not in the given
                    // direction of sourceFocusAreaBounds, it's not a candidate, so we should return
                    // true to stop searching.
                    return !Rect.intersects(nodeBounds, sourceFocusAreaBounds)
                            && !FocusFinder.isInDirection(
                                sourceFocusAreaBounds, nodeBounds, direction);
                },
                /* targetPredicate= */ candidateNode -> {
                    // RotaryService can navigate to nodes in a WebView or a ComposeView even when
                    // off-screen, so we use canPerformFocus() to skip the bounds check.
                    if (isInVirtualNodeHierarchy(candidateNode)) {
                        return Utils.canPerformFocus(candidateNode);
                    }
                    // If a node isn't visible to the user, e.g. another window is obscuring it,
                    // skip it.
                    if (!candidateNode.isVisibleToUser()) {
                        return false;
                    }
                    // If a node can't take focus, it represents a focus area, so we return false to
                    // skip the node and let it search its descendants.
                    if (!Utils.canTakeFocus(candidateNode)) {
                        return false;
                    }
                    // The node represents a focusable view in a focus area, so check the geometry.
                    return FocusFinder.isCandidate(sourceBounds, nodeBounds, direction);
                });
        if (candidate == null) {
            return false;
        }
        candidate.recycle();
        return true;
    }

    private AccessibilityNodeInfo copyNode(@Nullable AccessibilityNodeInfo node) {
        return mNodeCopier.copy(node);
    }

    /**
     * Returns the closest ancestor focus area of the given {@code node}.
     * <ul>
     *     <li> If the given {@code node} is a {@link FocusArea} node or a descendant of a {@link
     *          FocusArea} node, returns the {@link FocusArea} node.
     *     <li> If there are no explicitly declared {@link FocusArea}s among the ancestors of this
     *          view, and this view is not in an embedded view hierarchy, returns the root node.
     *     <li> If there are no explicitly declared {@link FocusArea}s among the ancestors of this
     *          view, and this view is in an embedded view hierarchy, returns the root node of
     *          embedded view hierarchy.
     * </ul>
     * The caller is responsible for recycling the result.
     */
    @NonNull
    AccessibilityNodeInfo getAncestorFocusArea(@NonNull AccessibilityNodeInfo node) {
        Predicate<AccessibilityNodeInfo> isFocusAreaOrRoot = candidateNode -> {
            if (Utils.isFocusArea(candidateNode)) {
                // The candidateNode is a focus area.
                return true;
            }
            AccessibilityNodeInfo parent = candidateNode.getParent();
            if (parent == null) {
                // The candidateNode is the root node.
                return true;
            }
            if (Utils.isSurfaceView(parent)) {
                // Treat the root of embedded view hierarchy (i.e., the only child of the
                // SurfaceView) as an implicit focus area.
                return true;
            }
            parent.recycle();
            return false;
        };
        AccessibilityNodeInfo result = mTreeTraverser.findNodeOrAncestor(node, isFocusAreaOrRoot);
        if (result == null || !Utils.isFocusArea(result)) {
            L.w("Ancestor focus area for node " + node + " is not an explicit FocusArea: "
                    + result);
        }
        return result;
    }

    /**
     * Returns a copy of {@code node} or the nearest ancestor that represents a {@code WebView}.
     * Returns null if {@code node} isn't a {@code WebView} and isn't a descendant of a {@code
     * WebView}.
     */
    @Nullable
    private AccessibilityNodeInfo findWebViewAncestor(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.findNodeOrAncestor(node, Utils::isWebView);
    }

    /**
     * Returns a copy of {@code node} or the nearest ancestor that represents a {@code ComposeView}
     * or a {@code WebView}. Returns null if {@code node} isn't a {@code ComposeView} or a
     * {@code WebView} and is not a descendant of a {@code ComposeView} or a {@code WebView}.
     *
     * TODO(b/192274274): This method may not be necessary anymore if Compose supports focusSearch.
     */
    @Nullable
    private AccessibilityNodeInfo findVirtualViewAncestor(@NonNull AccessibilityNodeInfo node) {
        return mTreeTraverser.findNodeOrAncestor(node, /* targetPredicate= */ (nodeInfo) ->
            Utils.isComposeView(nodeInfo) || Utils.isWebView(nodeInfo));
    }

    /** Returns whether {@code node} is a {@code WebView} or is a descendant of one. */
    boolean isInWebView(@NonNull AccessibilityNodeInfo node) {
        AccessibilityNodeInfo webView = findWebViewAncestor(node);
        if (webView == null) {
            return false;
        }
        webView.recycle();
        return true;
    }

    /**
     * Returns whether {@code node} is a {@code ComposeView}, is a {@code WebView}, or is a
     * descendant of either.
     */
    boolean isInVirtualNodeHierarchy(@NonNull AccessibilityNodeInfo node) {
        AccessibilityNodeInfo virtualViewAncestor = findVirtualViewAncestor(node);
        if (virtualViewAncestor == null) {
            return false;
        }
        virtualViewAncestor.recycle();
        return true;
    }

    /**
     * Returns the next focusable node after {@code candidate} in {@code direction} in {@code
     * root} or null if none. This handles navigating into a WebView as well as within a WebView.
     * This also handles navigating into a ComposeView, as well as within a ComposeView.
     */
    @Nullable
    private AccessibilityNodeInfo findNextFocusableInVirtualRoot(
            @NonNull AccessibilityNodeInfo root,
            @NonNull AccessibilityNodeInfo candidate, int direction) {
        // focusSearch() doesn't work in WebViews or ComposeViews so use tree traversal instead.
        if (Utils.isWebView(candidate) || Utils.isComposeView(candidate)) {
            if (direction == View.FOCUS_FORWARD) {
                // When entering into the root of a virtual node hierarchy, find the first focusable
                // child node of the root if any.
                return findFirstFocusableDescendantInVirtualRoot(candidate);
            } else {
                // When backing into the root of a virtual node hierarchy, find the last focusable
                // child node of the root if any.
                return findLastFocusableDescendantInVirtualRoot(candidate);
            }
        } else {
            // When navigating within a virtual view hierarchy, find the next or previous focusable
            // node in depth-first order.
            if (direction == View.FOCUS_FORWARD) {
                return findFirstFocusDescendantInVirtualRootAfter(root, candidate);
            } else {
                return findFirstFocusDescendantInVirtualRootBefore(root, candidate);
            }
        }
    }

    /**
     * Returns the first descendant of {@code webView} which can perform focus. This includes off-
     * screen descendants. The nodes are searched in in depth-first order, not including
     * {@code root} itself. If no descendant can perform focus, null is returned. The caller is
     * responsible for recycling the result.
     */
    @Nullable
    private AccessibilityNodeInfo findFirstFocusableDescendantInVirtualRoot(
            @NonNull AccessibilityNodeInfo root) {
        return mTreeTraverser.depthFirstSearch(root,
                candidateNode -> candidateNode != root && Utils.canPerformFocus(candidateNode));
    }

    /**
     * Returns the last descendant of {@code root} which can perform focus. This includes off-
     * screen descendants. The nodes are searched in reverse depth-first order, not including
     * {@code root} itself. If no descendant can perform focus, null is returned. The caller is
     * responsible for recycling the result.
     */
    @Nullable
    private AccessibilityNodeInfo findLastFocusableDescendantInVirtualRoot(
            @NonNull AccessibilityNodeInfo root) {
        return mTreeTraverser.reverseDepthFirstSearch(root,
                candidateNode -> candidateNode != root && Utils.canPerformFocus(candidateNode));
    }

    @Nullable
    private AccessibilityNodeInfo findFirstFocusDescendantInVirtualRootBefore(
            @NonNull AccessibilityNodeInfo root, @NonNull AccessibilityNodeInfo beforeNode) {
        boolean[] foundBeforeNode = new boolean[1];
        return mTreeTraverser.reverseDepthFirstSearch(root,
                node -> {
                    if (foundBeforeNode[0] && Utils.canPerformFocus(node)) {
                        return true;
                    }
                    if (node.equals(beforeNode)) {
                        foundBeforeNode[0] = true;
                    }
                    return false;
                });
    }

    @Nullable
    private AccessibilityNodeInfo findFirstFocusDescendantInVirtualRootAfter(
            @NonNull AccessibilityNodeInfo root, @NonNull AccessibilityNodeInfo afterNode) {
        boolean[] foundAfterNode = new boolean[1];
        return mTreeTraverser.depthFirstSearch(root,
                node -> {
                    if (foundAfterNode[0] && Utils.canPerformFocus(node)) {
                        return true;
                    }
                    if (node.equals(afterNode)) {
                        foundAfterNode[0] = true;
                    }
                    return false;
                });
    }

    void dump(@NonNull DualDumpOutputStream dumpOutputStream, boolean dumpAsProto,
            @NonNull String fieldName, long fieldId) {
        long fieldToken = dumpOutputStream.start(fieldName, fieldId);
        dumpOutputStream.write("hunLeft", RotaryProtos.Navigator.HUN_LEFT, mHunLeft);
        dumpOutputStream.write("hunRight", RotaryProtos.Navigator.HUN_RIGHT, mHunRight);
        DumpUtils.writeFocusDirection(dumpOutputStream, dumpAsProto, "hunNudgeDirection",
                RotaryProtos.Navigator.HUN_NUDGE_DIRECTION, mHunNudgeDirection);
        DumpUtils.writeRect(dumpOutputStream, mAppWindowBounds, "appWindowBounds",
                RotaryProtos.Navigator.APP_WINDOW_BOUNDS);
        mSurfaceViewHelper.dump(dumpOutputStream, dumpAsProto, "surfaceViewHelper",
                RotaryProtos.Navigator.SURFACE_VIEW_HELPER);
        dumpOutputStream.end(fieldToken);
    }

    static String directionToString(@View.FocusRealDirection int direction) {
        switch (direction) {
            case FOCUS_UP:
                return "FOCUS_UP";
            case FOCUS_DOWN:
                return "FOCUS_DOWN";
            case FOCUS_LEFT:
                return "FOCUS_LEFT";
            case FOCUS_RIGHT:
                return "FOCUS_RIGHT";
            default:
                return "<unknown direction " + direction + ">";
        }
    }

    /** Result from {@link #findRotateTarget}. */
    static class FindRotateTargetResult {
        @NonNull final AccessibilityNodeInfo node;
        final int advancedCount;

        FindRotateTargetResult(@NonNull AccessibilityNodeInfo node, int advancedCount) {
            this.node = node;
            this.advancedCount = advancedCount;
        }
    }
}
