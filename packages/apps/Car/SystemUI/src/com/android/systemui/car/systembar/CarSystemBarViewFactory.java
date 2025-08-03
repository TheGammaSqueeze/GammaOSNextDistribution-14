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

package com.android.systemui.car.systembar;

import android.annotation.IdRes;
import android.content.Context;
import android.util.ArrayMap;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.LayoutRes;

import com.android.car.dockutil.Flags;
import com.android.car.ui.FocusParkingView;
import com.android.systemui.R;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointsController;
import com.android.systemui.car.systembar.element.CarSystemBarElementController;
import com.android.systemui.car.systembar.element.CarSystemBarElementInitializer;
import com.android.systemui.dagger.SysUISingleton;
import com.android.systemui.flags.FeatureFlags;
import com.android.systemui.settings.UserTracker;

import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

/** A factory that creates and caches views for navigation bars. */
@SysUISingleton
public class CarSystemBarViewFactory {

    private static final String TAG = CarSystemBarViewFactory.class.getSimpleName();
    private static final ArrayMap<Type, Integer> sLayoutMap = setupLayoutMapping();

    private static ArrayMap<Type, Integer> setupLayoutMapping() {
        ArrayMap<Type, Integer> map = new ArrayMap<>();
        map.put(Type.TOP, R.layout.car_top_system_bar);
        map.put(Type.TOP_WITH_DOCK, R.layout.car_top_system_bar_dock);
        map.put(Type.TOP_UNPROVISIONED, R.layout.car_top_system_bar_unprovisioned);
        map.put(Type.BOTTOM, R.layout.car_bottom_system_bar);
        map.put(Type.BOTTOM_WITH_DOCK, R.layout.car_bottom_system_bar_dock);
        map.put(Type.BOTTOM_UNPROVISIONED, R.layout.car_bottom_system_bar_unprovisioned);
        map.put(Type.LEFT, R.layout.car_left_system_bar);
        map.put(Type.LEFT_UNPROVISIONED, R.layout.car_left_system_bar_unprovisioned);
        map.put(Type.RIGHT, R.layout.car_right_system_bar);
        map.put(Type.RIGHT_UNPROVISIONED, R.layout.car_right_system_bar_unprovisioned);
        return map;
    }

    private final Context mContext;
    private final ArrayMap<Type, CarSystemBarView> mCachedViewMap = new ArrayMap<>(
            Type.values().length);
    private final ArrayMap<Type, ViewGroup> mCachedContainerMap = new ArrayMap<>();
    private final FeatureFlags mFeatureFlags;
    private final QuickControlsEntryPointsController mQuickControlsEntryPointsController;
    private final UserTracker mUserTracker;
    private final CarSystemBarElementInitializer mCarSystemBarElementInitializer;
    private final List<CarSystemBarElementController> mCarSystemBarElementControllers =
            new ArrayList<>();

    /** Type of navigation bar to be created. */
    private enum Type {
        TOP,
        TOP_WITH_DOCK,
        TOP_UNPROVISIONED,
        BOTTOM,
        BOTTOM_WITH_DOCK,
        BOTTOM_UNPROVISIONED,
        LEFT,
        LEFT_UNPROVISIONED,
        RIGHT,
        RIGHT_UNPROVISIONED
    }

    @Inject
    public CarSystemBarViewFactory(
            Context context,
            FeatureFlags featureFlags,
            QuickControlsEntryPointsController quickControlsEntryPointsController,
            UserTracker userTracker,
            CarSystemBarElementInitializer elementInitializer
    ) {
        mContext = context;
        mFeatureFlags = featureFlags;
        mQuickControlsEntryPointsController = quickControlsEntryPointsController;
        mUserTracker = userTracker;
        mCarSystemBarElementInitializer = elementInitializer;
    }

    /** Gets the top window. */
    public ViewGroup getTopWindow() {
        return getWindowCached(Type.TOP);
    }

    /** Gets the bottom window. */
    public ViewGroup getBottomWindow() {
        return getWindowCached(Type.BOTTOM);
    }

    /** Gets the left window. */
    public ViewGroup getLeftWindow() {
        return getWindowCached(Type.LEFT);
    }

    /** Gets the right window. */
    public ViewGroup getRightWindow() {
        return getWindowCached(Type.RIGHT);
    }

    /** Gets the top bar. */
    public CarSystemBarView getTopBar(boolean isSetUp) {
        if (Flags.dockFeature()) {
            return getBar(isSetUp, Type.TOP_WITH_DOCK, Type.TOP_UNPROVISIONED);
        }
        return getBar(isSetUp, Type.TOP, Type.TOP_UNPROVISIONED);
    }

    /** Gets the bottom bar. */
    public CarSystemBarView getBottomBar(boolean isSetUp) {
        if (Flags.dockFeature()) {
            return getBar(isSetUp, Type.BOTTOM_WITH_DOCK, Type.BOTTOM_UNPROVISIONED);
        }
        return getBar(isSetUp, Type.BOTTOM, Type.BOTTOM_UNPROVISIONED);
    }

    /** Gets the left bar. */
    public CarSystemBarView getLeftBar(boolean isSetUp) {
        return getBar(isSetUp, Type.LEFT, Type.LEFT_UNPROVISIONED);
    }

    /** Gets the right bar. */
    public CarSystemBarView getRightBar(boolean isSetUp) {
        return getBar(isSetUp, Type.RIGHT, Type.RIGHT_UNPROVISIONED);
    }

    private ViewGroup getWindowCached(Type type) {
        if (mCachedContainerMap.containsKey(type)) {
            return mCachedContainerMap.get(type);
        }

        ViewGroup window = (ViewGroup) View.inflate(mContext,
                R.layout.navigation_bar_window, /* root= */ null);
        window.setId(getWindowId(type));
        mCachedContainerMap.put(type, window);
        return mCachedContainerMap.get(type);
    }

    @IdRes
    private int getWindowId(Type type) {
        return switch (type) {
            case TOP -> R.id.car_top_bar_window;
            case BOTTOM -> R.id.car_bottom_bar_window;
            case LEFT -> R.id.car_left_bar_window;
            case RIGHT -> R.id.car_right_bar_window;
            default -> throw new IllegalArgumentException("unknown system bar window type " + type);
        };
    }

    private CarSystemBarView getBar(boolean isSetUp, Type provisioned, Type unprovisioned) {
        CarSystemBarView view = getBarCached(isSetUp, provisioned, unprovisioned);

        if (view == null) {
            String name = isSetUp ? provisioned.name() : unprovisioned.name();
            Log.e(TAG, "CarStatusBar failed inflate for " + name);
            throw new RuntimeException(
                    "Unable to build " + name + " nav bar due to missing layout");
        }
        return view;
    }

    private CarSystemBarView getBarCached(boolean isSetUp, Type provisioned, Type unprovisioned) {
        Type type = isSetUp ? provisioned : unprovisioned;
        if (mCachedViewMap.containsKey(type)) {
            return mCachedViewMap.get(type);
        }

        Integer barLayoutInteger = sLayoutMap.get(type);
        if (barLayoutInteger == null) {
            return null;
        }
        @LayoutRes int barLayout = barLayoutInteger;
        CarSystemBarView view = (CarSystemBarView) View.inflate(mContext, barLayout,
                /* root= */ null);

        view.setupHvacButton();
        view.setupQuickControlsEntryPoints(mQuickControlsEntryPointsController, isSetUp);
        view.setupSystemBarButtons(mUserTracker);
        mCarSystemBarElementControllers.addAll(
                mCarSystemBarElementInitializer.initializeCarSystemBarElements(view));

        // Include a FocusParkingView at the beginning. The rotary controller "parks" the focus here
        // when the user navigates to another window. This is also used to prevent wrap-around.
        view.addView(new FocusParkingView(mContext), 0);

        mCachedViewMap.put(type, view);
        return mCachedViewMap.get(type);
    }

    /** Gets the selected Quick Controls class name. */
    protected String getSelectedQuickControlsClassName() {
        return mQuickControlsEntryPointsController.getClassNameOfSelectedView();
    }

    /** Calls onClick for the given Quick Controls class name. */
    protected void callQuickControlsOnClickFromClassName(String clsName) {
        View statusIconView = mQuickControlsEntryPointsController.getViewFromClassName(clsName);
        if (statusIconView != null) {
            statusIconView.callOnClick();
        }
    }

    /** Resets the cached system bar views. */
    protected void resetSystemBarViewCache() {
        mQuickControlsEntryPointsController.resetCache();
        mCachedViewMap.clear();
    }

    /** Resets the cached system bar windows and system bar views. */
    protected void resetSystemBarWindowCache() {
        resetSystemBarViewCache();
        mCachedContainerMap.clear();
    }
}
