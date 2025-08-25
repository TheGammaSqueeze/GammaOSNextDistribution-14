/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.launcher3.taskbar.allapps;

import static com.android.launcher3.taskbar.TaskbarStashController.FLAG_STASHED_IN_TASKBAR_ALL_APPS;
import static com.android.launcher3.util.OnboardingPrefs.ALL_APPS_VISITED_COUNT;

import androidx.annotation.Nullable;

import com.android.internal.jank.Cuj;
import com.android.launcher3.AbstractFloatingView;
import com.android.launcher3.allapps.AllAppsTransitionListener;
import com.android.launcher3.anim.PendingAnimation;
import com.android.launcher3.appprediction.AppsDividerView;
import com.android.launcher3.taskbar.NavbarButtonsViewController;
import com.android.launcher3.taskbar.TaskbarControllers;
import com.android.launcher3.taskbar.TaskbarSharedState;
import com.android.launcher3.taskbar.TaskbarStashController;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayContext;
import com.android.launcher3.taskbar.overlay.TaskbarOverlayController;
import com.android.launcher3.util.DisplayController;
import com.android.systemui.shared.system.InteractionJankMonitorWrapper;

import java.util.Optional;

/**
 * Handles the {@link TaskbarAllAppsContainerView} behavior and synchronizes its transitions with
 * taskbar stashing.
 */
final class TaskbarAllAppsViewController {

    private final TaskbarOverlayContext mContext;
    private final TaskbarAllAppsSlideInView mSlideInView;
    private final TaskbarAllAppsContainerView mAppsView;
    private final TaskbarStashController mTaskbarStashController;
    private final NavbarButtonsViewController mNavbarButtonsViewController;
    private final TaskbarOverlayController mOverlayController;
    private final @Nullable TaskbarSharedState mTaskbarSharedState;
    private final boolean mShowKeyboard;

    TaskbarAllAppsViewController(
            TaskbarOverlayContext context,
            TaskbarAllAppsSlideInView slideInView,
            TaskbarControllers taskbarControllers,
            TaskbarSearchSessionController searchSessionController,
            boolean showKeyboard) {

        mContext = context;
        mSlideInView = slideInView;
        mAppsView = mSlideInView.getAppsView();
        // Prevent the first tap from bubbling to the scrim/root (which would dismiss the drawer).
        // By making the apps panel itself clickable/focusable with a no-op click listener,
        // taps within All Apps are consumed here; icon clicks will still fire on the child views.
        if (mAppsView != null) {
            mAppsView.setClickable(true);
            mAppsView.setFocusable(true);
            mAppsView.setFocusableInTouchMode(true);
            mAppsView.setOnClickListener(v -> { /* consume to avoid dismiss-on-tap */ });
        }
        mTaskbarStashController = taskbarControllers.taskbarStashController;
        mNavbarButtonsViewController = taskbarControllers.navbarButtonsViewController;
        mOverlayController = taskbarControllers.taskbarOverlayController;
        mTaskbarSharedState = taskbarControllers.getSharedState();
        mShowKeyboard = showKeyboard;

        mSlideInView.init(new TaskbarAllAppsCallbacks(searchSessionController));
        setUpAppDivider();
        setUpTaskbarStashing();
    }

    /** Starts the {@link TaskbarAllAppsSlideInView} enter transition. */
    void show(boolean animate) {
        // Avoid first-tap dismiss: disable scrim tap-to-dismiss until enter finishes.
        mSlideInView.setOnClickListener(null);
        mSlideInView.show(animate);
    }

    /** Closes the {@link TaskbarAllAppsSlideInView}. */
    void close(boolean animate) {
        mSlideInView.close(animate);
    }

    private void setUpAppDivider() {
        mAppsView.getFloatingHeaderView()
                .findFixedRowByType(AppsDividerView.class)
                .setShowAllAppsLabel(!ALL_APPS_VISITED_COUNT.hasReachedMax(mContext));
        ALL_APPS_VISITED_COUNT.increment(mContext);
    }

    private void setUpTaskbarStashing() {
        if (DisplayController.isTransientTaskbar(mContext)) {
            mTaskbarStashController.updateStateForFlag(FLAG_STASHED_IN_TASKBAR_ALL_APPS, true);
            mTaskbarStashController.applyState();
        }

        Optional.ofNullable(mTaskbarSharedState).ifPresent(s -> s.allAppsVisible = true);
        mNavbarButtonsViewController.setSlideInViewVisible(true);
        mSlideInView.setOnCloseBeginListener(() -> {
            Optional.ofNullable(mTaskbarSharedState).ifPresent(s -> s.allAppsVisible = false);
            mNavbarButtonsViewController.setSlideInViewVisible(false);
            AbstractFloatingView.closeOpenContainer(
                    mContext, AbstractFloatingView.TYPE_ACTION_POPUP);

            if (DisplayController.isTransientTaskbar(mContext)) {
                mTaskbarStashController.updateStateForFlag(FLAG_STASHED_IN_TASKBAR_ALL_APPS, false);
                mTaskbarStashController.applyState();
            }
        });
    }

    class TaskbarAllAppsCallbacks implements AllAppsTransitionListener {
        private final TaskbarSearchSessionController mSearchSessionController;

        private TaskbarAllAppsCallbacks(TaskbarSearchSessionController searchSessionController) {
            mSearchSessionController = searchSessionController;
        }

        int getOpenDuration() {
            return mOverlayController.getOpenDuration();
        }

        int getCloseDuration() {
            return mOverlayController.getCloseDuration();
        }

        @Override
        public void onAllAppsTransitionStart(boolean toAllApps) {
            mSearchSessionController.onAllAppsTransitionStart(toAllApps);
        }

        @Override
        public void onAllAppsTransitionEnd(boolean toAllApps) {
            mSearchSessionController.onAllAppsTransitionEnd(toAllApps);
            if (toAllApps
                    && mShowKeyboard
                    && mAppsView.getSearchUiManager().getEditText() != null) {
                mAppsView.getSearchUiManager().getEditText().requestFocus();
            }
            if (toAllApps) {
                InteractionJankMonitorWrapper.end(Cuj.CUJ_LAUNCHER_OPEN_ALL_APPS);
            }
            // Re-enable scrim tap-to-dismiss now that content is fully laid out.
            if (toAllApps) {
                mSlideInView.setOnClickListener(v -> mSlideInView.close(true));
            }
        }

        /** Invoked on back press, returning {@code true} if the search session handled it. */
        boolean handleSearchBackInvoked() {
            return mSearchSessionController.handleBackInvoked();
        }

        void onAllAppsAnimationPending(PendingAnimation animation, boolean toAllApps) {
            mSearchSessionController.onAllAppsAnimationPending(animation, toAllApps, mShowKeyboard);
        }
    }
}
