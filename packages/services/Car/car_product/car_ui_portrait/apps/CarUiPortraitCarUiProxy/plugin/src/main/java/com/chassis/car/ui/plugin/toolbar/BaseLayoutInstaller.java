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

package com.chassis.car.ui.plugin.toolbar;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import androidx.annotation.LayoutRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.plugin.oemapis.Consumer;
import com.android.car.ui.plugin.oemapis.FocusAreaOEMV1;
import com.android.car.ui.plugin.oemapis.FocusParkingViewOEMV1;
import com.android.car.ui.plugin.oemapis.Function;
import com.android.car.ui.plugin.oemapis.InsetsOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV2;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV3;

import com.chassis.car.ui.plugin.R;

/**
 * A helper class for implementing installBaseLayoutAround from {@code PluginFactory}
 */
public class BaseLayoutInstaller {

    private static int sBaseLayoutId = 0;

    /**
     * Installs the base layout around the contentView. Optionally installs a toolbar and returns
     * an implementation of {@code ToolbarControllerOEMV1} if the toolbar is enabled.
     */
    @Nullable
    public static ToolbarControllerOEMV1 installBaseLayoutAroundV1(
            @NonNull Context pluginContext,
            @NonNull View contentView,
            @Nullable java.util.function.Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen,
            @Nullable java.util.function.Function<Context, FocusParkingViewOEMV1>
                    focusParkingViewFactory,
            @Nullable java.util.function.Function<Context, FocusAreaOEMV1> focusAreaFactory) {
        ToolbarControllerImpl toolbarControllerImpl = installBaseLayoutAround(
                pluginContext, contentView, insetsChangedListener != null
                        ? (Consumer<InsetsOEMV1>) insets -> insetsChangedListener.accept(insets)
                        : null, toolbarEnabled, fullscreen,
                focusParkingViewFactory != null ? ((Function<Context, FocusParkingViewOEMV1>)
                context -> focusParkingViewFactory.apply(context)) : null,
                focusAreaFactory != null ? ((Function<Context, FocusAreaOEMV1>)
                context -> focusAreaFactory.apply(context)) : null);
        return !toolbarEnabled ? null : new ToolbarAdapterProxyV1(pluginContext,
                toolbarControllerImpl);
    }

    /**
     * Installs the base layout around the contentView. Optionally installs a toolbar and returns
     * an implementation of {@code ToolbarControllerOEMV2} if the toolbar is enabled.
     */
    @Nullable
    public static ToolbarControllerOEMV2 installBaseLayoutAroundV2(
            @NonNull Context pluginContext,
            @NonNull View contentView,
            @Nullable Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen,
            @Nullable Function<Context, FocusParkingViewOEMV1> focusParkingViewFactory,
            @Nullable Function<Context, FocusAreaOEMV1> focusAreaFactory) {
        ToolbarControllerImpl toolbarControllerImpl = installBaseLayoutAround(
                pluginContext, contentView, insetsChangedListener, toolbarEnabled, fullscreen,
                focusParkingViewFactory, focusAreaFactory);
        return !toolbarEnabled ? null : new ToolbarAdapterProxyV2(pluginContext,
                toolbarControllerImpl);
    }

    /**
     * Installs the base layout around the contentView. Optionally installs a toolbar and returns
     * an implementation of {@code ToolbarControllerOEMV3} if the toolbar is enabled.
     */
    @Nullable
    public static ToolbarControllerOEMV3 installBaseLayoutAroundV3(
            @NonNull Context pluginContext,
            @NonNull View contentView,
            @Nullable Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen,
            @Nullable Function<Context, FocusParkingViewOEMV1> focusParkingViewFactory,
            @Nullable Function<Context, FocusAreaOEMV1> focusAreaFactory) {
        ToolbarControllerImpl toolbarControllerImpl = installBaseLayoutAround(
                pluginContext, contentView, insetsChangedListener, toolbarEnabled, fullscreen,
                focusParkingViewFactory, focusAreaFactory);
        return !toolbarEnabled ? null : new ToolbarAdapterProxyV3(pluginContext,
                toolbarControllerImpl);
    }

    /**
     * Implementation of installBaseLayoutAround from {@code PluginFactory}
     */
    private static ToolbarControllerImpl installBaseLayoutAround(
            @NonNull Context pluginContext,
            @NonNull View contentView,
            @Nullable Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullScreen,
            @Nullable Function<Context, FocusParkingViewOEMV1> focusParkingViewFactory,
            @Nullable Function<Context, FocusAreaOEMV1> focusAreaFactory) {

        @LayoutRes int layout = toolbarEnabled
                ? R.layout.car_ui_portrait_base_layout_toolbar
                : R.layout.car_ui_base_layout;

        sBaseLayoutId = R.id.car_ui_base_layout_content_container;
        ViewGroup baseLayout = (ViewGroup) LayoutInflater.from(pluginContext).inflate(
                layout, null, false);

        // Replace the app's content view with a base layout
        ViewGroup contentViewParent = (ViewGroup) contentView.getParent();
        int contentIndex = contentViewParent.indexOfChild(contentView);
        contentViewParent.removeView(contentView);
        contentViewParent.addView(baseLayout, contentIndex, contentView.getLayoutParams());

        // Add the app's content view to the baseLayout's content view container
        FrameLayout contentViewContainer = baseLayout.requireViewById(
                        R.id.car_ui_base_layout_content_container);
        contentViewContainer.addView(contentView, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        ToolbarControllerImpl toolbarController = null;
        if (toolbarEnabled) {
            toolbarController = new ToolbarControllerImpl(baseLayout, pluginContext);
        }

        InsetsUpdater updater = new InsetsUpdater(baseLayout, contentView);
        updater.replaceInsetsChangedListenerWith(insetsChangedListener);
        updater.installListeners();

        return toolbarController;
    }

    /**
     * InsetsUpdater waits for layout changes, and when there is one, calculates the appropriate
     * insets into the content view.
     */
    private static final class InsetsUpdater implements ViewTreeObserver.OnGlobalLayoutListener {
        // These tags mark views that should overlay the content view in the base layout.
        // Apps will then be able to draw under these views, but will be encouraged to not put
        // any user-interactable content there.
        private static final String LEFT_INSET_TAG = "car_ui_portrait_left_inset";
        private static final String RIGHT_INSET_TAG = "car_ui_portrait_right_inset";
        private static final String TOP_INSET_TAG = "car_ui_portrait_top_inset";
        private static final String BOTTOM_INSET_TAG = "car_ui_portrait_bottom_inset";

        private final View mContentView;
        private final View mContentViewContainer; // Equivalent to mContentView except in Media
        private final View mLeftInsetView;
        private final View mRightInsetView;
        private final View mTopInsetView;
        private final View mBottomInsetView;
        private Consumer<InsetsOEMV1> mInsetsChangedListenerDelegate;

        private boolean mInsetsDirty = true;
        private InsetsOEMV1 mInsets = new InsetsOEMV1();

        /**
         * Constructs an InsetsUpdater that calculates and dispatches insets to the method provided
         * via {@link #replaceInsetsChangedListenerWith(Consumer)}.
         *
         * @param baseLayout  The root view of the base layout
         * @param contentView The android.R.id.content View
         */
        InsetsUpdater(
                View baseLayout,
                View contentView) {
            mContentView = contentView;
            mContentViewContainer = baseLayout.requireViewById(sBaseLayoutId);

            mLeftInsetView = baseLayout.findViewWithTag(LEFT_INSET_TAG);
            mRightInsetView = baseLayout.findViewWithTag(RIGHT_INSET_TAG);
            mTopInsetView = baseLayout.findViewWithTag(TOP_INSET_TAG);
            mBottomInsetView = baseLayout.findViewWithTag(BOTTOM_INSET_TAG);

            final View.OnLayoutChangeListener layoutChangeListener =
                    (View v, int left, int top, int right, int bottom,
                            int oldLeft, int oldTop, int oldRight, int oldBottom) -> {
                        if (left != oldLeft || top != oldTop
                                || right != oldRight || bottom != oldBottom) {
                            mInsetsDirty = true;
                        }
                    };

            if (mLeftInsetView != null) {
                mLeftInsetView.addOnLayoutChangeListener(layoutChangeListener);
            }
            if (mRightInsetView != null) {
                mRightInsetView.addOnLayoutChangeListener(layoutChangeListener);
            }
            if (mTopInsetView != null) {
                mTopInsetView.addOnLayoutChangeListener(layoutChangeListener);
            }
            if (mBottomInsetView != null) {
                mBottomInsetView.addOnLayoutChangeListener(layoutChangeListener);
            }
            contentView.addOnLayoutChangeListener(layoutChangeListener);
            mContentViewContainer.addOnLayoutChangeListener(layoutChangeListener);
        }

        /**
         * Install a global layout listener, during which the insets will be recalculated and
         * dispatched.
         */
        public void installListeners() {
            // The global layout listener will run after all the individual layout change listeners
            // so that we only updateInsets once per layout, even if multiple inset views changed
            mContentView.getRootView().getViewTreeObserver()
                    .addOnGlobalLayoutListener(this);
        }

        InsetsOEMV1 getInsets() {
            return mInsets;
        }

        // TODO remove this method / cleanup this class
        public void replaceInsetsChangedListenerWith(Consumer<InsetsOEMV1> listener) {
            mInsetsChangedListenerDelegate = listener;
        }

        /**
         * onGlobalLayout() should recalculate the amount of insets we need, and then dispatch them.
         */
        @Override
        public void onGlobalLayout() {
            if (!mInsetsDirty) {
                return;
            }

            // Calculate how much each inset view overlays the content view

            // These initial values are for Media Center's implementation of base layouts.
            // They should evaluate to 0 in all other apps, because the content view and content
            // view container have the same size and position there.
            int top = Math.max(0,
                    getTopOfView(mContentViewContainer) - getTopOfView(mContentView));
            int left = Math.max(0,
                    getLeftOfView(mContentViewContainer) - getLeftOfView(mContentView));
            int right = Math.max(0,
                    getRightOfView(mContentView) - getRightOfView(mContentViewContainer));
            int bottom = Math.max(0,
                    getBottomOfView(mContentView) - getBottomOfView(mContentViewContainer));
            if (mTopInsetView != null) {
                top += Math.max(0,
                        getBottomOfView(mTopInsetView) - getTopOfView(mContentViewContainer));
            }
            if (mBottomInsetView != null) {
                bottom += Math.max(0,
                        getBottomOfView(mContentViewContainer) - getTopOfView(mBottomInsetView));
            }
            if (mLeftInsetView != null) {
                left += Math.max(0,
                        getRightOfView(mLeftInsetView) - getLeftOfView(mContentViewContainer));
            }
            if (mRightInsetView != null) {
                right += Math.max(0,
                        getRightOfView(mContentViewContainer) - getLeftOfView(mRightInsetView));
            }
            InsetsOEMV1 insets = new InsetsOEMV1(left, top, right, bottom);

            mInsetsDirty = false;
            if (!insets.equals(mInsets)) {
                mInsets = insets;
                if (mInsetsChangedListenerDelegate != null) {
                    mInsetsChangedListenerDelegate.accept(insets);
                }
            }
        }

        private static int getLeftOfView(View v) {
            int[] position = new int[2];
            v.getLocationOnScreen(position);
            return position[0];
        }

        private static int getRightOfView(View v) {
            int[] position = new int[2];
            v.getLocationOnScreen(position);
            return position[0] + v.getWidth();
        }

        private static int getTopOfView(View v) {
            int[] position = new int[2];
            v.getLocationOnScreen(position);
            return position[1];
        }

        private static int getBottomOfView(View v) {
            int[] position = new int[2];
            v.getLocationOnScreen(position);
            return position[1] + v.getHeight();
        }
    }
}
