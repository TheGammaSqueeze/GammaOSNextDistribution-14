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

package com.android.systemui.car.statusicon;

import static com.android.systemui.car.statusicon.StatusIconController.PANEL_CONTENT_LAYOUT_NONE;

import android.annotation.ArrayRes;
import android.annotation.LayoutRes;
import android.content.Context;
import android.content.res.Resources;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;

import androidx.annotation.Nullable;

import com.android.internal.annotations.VisibleForTesting;
import com.android.systemui.R;
import com.android.systemui.car.statusicon.ui.QuickControlsEntryPointContainer;
import com.android.systemui.dagger.qualifiers.Main;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import javax.inject.Provider;

/**
 * A base controller for a view that contains a group of StatusIcons. It creates a button view for
 * each icon to display, instantiates {@link StatusIconController} instances associated with those
 * icons, and then registers those icons to those controllers.
 */
public abstract class StatusIconGroupContainerController {
    private final Context mContext;
    private final Resources mResources;
    private final Map<Class<?>, Provider<StatusIconController>> mIconControllerCreators;
    @Nullable
    private final Provider<StatusIconPanelViewController.Builder> mPanelControllerBuilderProvider;
    private final Set<StatusIconController> mStatusIconControllers;
    private final Set<StatusIconPanelViewController> mStatusIconPanelViewControllers;
    private String mIconTag;
    private String[] mStatusIconControllerNames;
    private Map<String, View> mStatusIconViewClassMap;

    public StatusIconGroupContainerController(
            Context context,
            @Main Resources resources,
            Map<Class<?>, Provider<StatusIconController>> iconControllerCreators,
            Provider<StatusIconPanelViewController.Builder> panelControllerBuilderProvider) {
        mContext = context;
        mResources = resources;
        mPanelControllerBuilderProvider = panelControllerBuilderProvider;
        mIconControllerCreators = iconControllerCreators;

        initResources();
        mStatusIconViewClassMap = new HashMap<>();
        mStatusIconControllers = new HashSet<>();
        mStatusIconPanelViewControllers = new HashSet<>();
    }

    private void initResources() {
        mIconTag = mResources.getString(R.string.qc_icon_tag);
        mStatusIconControllerNames = mResources.getStringArray(
                getStatusIconControllersStringArray());
    }

    private static <T> T resolve(String className, Map<Class<?>, Provider<T>> creators) {
        try {
            Class<?> clazz = Class.forName(className);
            Provider<T> provider = creators.get(clazz);
            return provider == null ? null : provider.get();
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    /**
     * Returns the layout res id to use as the button view that contains the StatusIcon.
     */
    @LayoutRes
    public int getButtonViewLayout(boolean isVertical) {
        return isVertical ? R.layout.default_status_icon_vertical
                : R.layout.default_status_icon_horizontal;
    }

    /**
     * See {@link #addIconViews(ViewGroup, boolean)}
     */
    public void addIconViews(ViewGroup containerViewGroup) {
        addIconViews(containerViewGroup, /* shouldAttachPanel= */ true);
    }

    /**
     * Adds Quick Control entry points to the provided container ViewGroup. Also attaches
     * the Quick Control panel if it's specified and allowed.
     */
    public void addIconViews(ViewGroup containerViewGroup, boolean shouldAttachPanel) {
        LayoutInflater li = LayoutInflater.from(mContext);

        for (String clsName : mStatusIconControllerNames) {
            StatusIconController statusIconController = getStatusIconControllerByName(clsName);
            boolean isVertical = mContext.getResources().getInteger(
                    R.integer.config_statusIconLayoutOrientation) == 1;

            View entryPointView = li.inflate(getButtonViewLayout(isVertical),
                    containerViewGroup, /* attachToRoot= */ false);
            entryPointView.setId(statusIconController.getId());

            ImageView statusIconView = entryPointView.findViewWithTag(mIconTag);
            statusIconController.registerIconView(statusIconView);

            if (shouldAttachPanel
                    && statusIconController.getPanelContentLayout() != PANEL_CONTENT_LAYOUT_NONE
                    && mPanelControllerBuilderProvider != null) {
                StatusIconPanelViewController panelViewController;
                if (containerViewGroup instanceof QuickControlsEntryPointContainer) {
                    QuickControlsEntryPointContainer qcEntryPointContainer =
                            (QuickControlsEntryPointContainer) containerViewGroup;
                    int gravity = qcEntryPointContainer.getPanelGravity();
                    boolean showAsDropDown = qcEntryPointContainer.showAsDropDown();
                    int offset = mContext.getResources().getDimensionPixelSize(
                            R.dimen.car_quick_controls_panel_margin);
                    panelViewController = mPanelControllerBuilderProvider.get().setXOffset(offset)
                            .setYOffset(offset).setGravity(gravity)
                            .setShowAsDropDown(showAsDropDown).build(entryPointView,
                                    statusIconController.getPanelContentLayout(),
                                    statusIconController.getPanelWidth());
                } else {
                    panelViewController = mPanelControllerBuilderProvider.get()
                            .build(entryPointView, statusIconController.getPanelContentLayout(),
                            statusIconController.getPanelWidth());
                }
                panelViewController.init();

                mStatusIconPanelViewControllers.add(panelViewController);
            }
            containerViewGroup.addView(entryPointView);
            mStatusIconControllers.add(statusIconController);
            mStatusIconViewClassMap.put(clsName, entryPointView);
        }
    }

    /** Gets the class name of the selected View. */
    public String getClassNameOfSelectedView() {
        for (String clsName : mStatusIconViewClassMap.keySet()) {
            View statusIconView = mStatusIconViewClassMap.get(clsName);
            if (statusIconView.isSelected()) {
                return clsName;
            }
        }
        return null;
    }

    /** Gets the View corresponding to the given class name. */
    public View getViewFromClassName(String clsName) {
        return mStatusIconViewClassMap.getOrDefault(clsName, null);
    }

    /** Resets the cached Views. */
    public void resetCache() {
        for (StatusIconController controller : mStatusIconControllers) {
            controller.onDestroy();
        }
        mStatusIconControllers.clear();
        mStatusIconPanelViewControllers.clear();
        mStatusIconViewClassMap.clear();
        initResources();
    }

    @ArrayRes
    protected abstract int getStatusIconControllersStringArray();

    private StatusIconController getStatusIconControllerByName(String className) {
        try {
            StatusIconController statusIconController = resolveStatusIconController(className);
            if (statusIconController == null) {
                Constructor constructor = Class.forName(className).getConstructor(Context.class);
                statusIconController = (StatusIconController) constructor.newInstance(this);
            }

            return statusIconController;
        } catch (ClassNotFoundException
                 | NoSuchMethodException
                 | IllegalAccessException
                 | InstantiationException
                 | InvocationTargetException ex) {
            throw new RuntimeException(ex);
        }
    }

    private StatusIconController resolveStatusIconController(String className) {
        return resolve(className, mIconControllerCreators);
    }

    @VisibleForTesting
    public void setStatusIconViewClassMap(Map<String, View> statusIconViewClassMap) {
        mStatusIconViewClassMap = statusIconViewClassMap;
    }
}
