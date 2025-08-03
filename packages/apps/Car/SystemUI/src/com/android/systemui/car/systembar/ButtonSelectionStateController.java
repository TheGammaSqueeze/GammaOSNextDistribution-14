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

import static android.app.WindowConfiguration.ACTIVITY_TYPE_UNDEFINED;
import static android.app.WindowConfiguration.WINDOWING_MODE_FULLSCREEN;
import static android.app.WindowConfiguration.WINDOWING_MODE_MULTI_WINDOW;
import static android.window.DisplayAreaOrganizer.FEATURE_DEFAULT_TASK_CONTAINER;

import android.app.ActivityTaskManager;
import android.app.ActivityTaskManager.RootTaskInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;

import com.android.systemui.dagger.SysUISingleton;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * CarSystemBarButtons can optionally have selection state that toggles certain visual indications
 * based on whether the active application on screen is associated with it. This is basically a
 * similar concept to a radio button group.
 *
 * This class controls the selection state of CarSystemBarButtons that have opted in to have such
 * selection state-dependent visual indications.
 */
@SysUISingleton
public class ButtonSelectionStateController {
    private static final String TAG = ButtonSelectionStateController.class.getSimpleName();

    private final Set<CarSystemBarButton> mRegisteredViews = new HashSet<>();

    protected final Context mContext;
    protected ButtonMap mButtonsByCategory = new ButtonMap();
    protected ButtonMap mButtonsByPackage = new ButtonMap();
    protected ButtonMap mButtonsByComponentName = new ButtonMap();
    protected HashSet<CarSystemBarButton> mSelectedButtons;

    public ButtonSelectionStateController(Context context) {
        mContext = context;
        mSelectedButtons = new HashSet<>();
    }

    /**
     * Iterate through a view looking for CarSystemBarButton and add it to the controller if it
     * opted in to be highlighted when the active application is associated with it.
     *
     * @param v the View that may contain CarFacetButtons
     */
    protected void addAllButtonsWithSelectionState(View v) {
        if (v instanceof CarSystemBarButton) {
            if (((CarSystemBarButton) v).hasSelectionState()) {
                addButtonWithSelectionState((CarSystemBarButton) v);
            }
        } else if (v instanceof ViewGroup) {
            ViewGroup viewGroup = (ViewGroup) v;
            for (int i = 0; i < viewGroup.getChildCount(); i++) {
                addAllButtonsWithSelectionState(viewGroup.getChildAt(i));
            }
        }
    }

    /** Removes all buttons from the button maps. */
    protected void removeAll() {
        mButtonsByCategory.clear();
        mButtonsByPackage.clear();
        mButtonsByComponentName.clear();
        mSelectedButtons.clear();
        mRegisteredViews.clear();
    }

    /**
     * This will unselect the currently selected CarSystemBarButton and determine which one should
     * be selected next. It does this by reading the properties on the CarSystemBarButton and
     * seeing if they are a match with the supplied StackInfo list.
     * The order of selection detection is ComponentName, PackageName then Category
     * They will then be compared with the supplied StackInfo list.
     * The StackInfo is expected to be supplied in order of recency and StackInfo will only be used
     * for consideration if it has the same displayId as the CarSystemBarButton.
     *
     * @param taskInfoList of the currently running application
     * @param validDisplay index of the valid display
     */

    protected void taskChanged(List<RootTaskInfo> taskInfoList, int validDisplay) {
        RootTaskInfo validTaskInfo = null;

        for (RootTaskInfo taskInfo : taskInfoList) {
            // Find the first stack info with a topActivity in the primary display.
            // TODO: We assume that CarFacetButton will launch an app only in the primary display.
            // We need to extend the functionality to handle the multiple display properly.
            if (taskInfo.topActivity != null && taskInfo.displayAreaFeatureId == validDisplay) {
                validTaskInfo = taskInfo;
                break;
            }
        }

        if (validTaskInfo == null) {
            // No stack was found that was on the same display as the buttons thus return
            return;
        }
        int displayId = validTaskInfo.displayId;

        // Clear all registered views
        clearAllSelectedButtons(displayId);

        HashSet<CarSystemBarButton> selectedButtons = findSelectedButtons(validTaskInfo);

        if (selectedButtons != null) {
            selectedButtons.forEach(carSystemBarButton -> {
                if (carSystemBarButton.getDisplayId() == displayId) {
                    carSystemBarButton.setSelected(true);
                    mSelectedButtons.add(carSystemBarButton);
                }
            });
        }
    }

    protected void clearAllSelectedButtons(int displayId) {
        mRegisteredViews.forEach(carSystemBarButton -> {
            if (carSystemBarButton.getDisplayId() == displayId) {
                carSystemBarButton.setSelected(false);
            }
        });
        mSelectedButtons.clear();
    }

    /**
     * Defaults to Display.DEFAULT_DISPLAY when no parameter is provided for the validDisplay.
     *
     * @param taskInfoList of the currently running application
     */
    protected void taskChanged(List<RootTaskInfo> taskInfoList) {
        taskChanged(taskInfoList, FEATURE_DEFAULT_TASK_CONTAINER);
    }

    /**
     * Add navigation button to this controller if it uses selection state.
     */
    private void addButtonWithSelectionState(CarSystemBarButton carSystemBarButton) {
        if (mRegisteredViews.contains(carSystemBarButton)) {
            return;
        }
        String[] categories = carSystemBarButton.getCategories();
        for (int i = 0; i < categories.length; i++) {
            mButtonsByCategory.add(categories[i], carSystemBarButton);
        }

        String[] packages = carSystemBarButton.getPackages();
        for (int i = 0; i < packages.length; i++) {
            mButtonsByPackage.add(packages[i], carSystemBarButton);
        }
        String[] componentNames = carSystemBarButton.getComponentName();
        for (int i = 0; i < componentNames.length; i++) {
            mButtonsByComponentName.add(componentNames[i], carSystemBarButton);
        }

        mRegisteredViews.add(carSystemBarButton);
    }

    private HashSet<CarSystemBarButton> findSelectedButtons(RootTaskInfo validTaskInfo) {
        ComponentName topActivity = getTopActivity(validTaskInfo);
        if (topActivity == null) return null;

        String packageName = topActivity.getPackageName();

        HashSet<CarSystemBarButton> selectedButtons =
                findButtonsByComponentName(topActivity);
        if (selectedButtons == null) {
            selectedButtons = mButtonsByPackage.get(packageName);
        }
        if (selectedButtons == null) {
            String category = getPackageCategory(packageName);
            if (category != null) {
                selectedButtons = mButtonsByCategory.get(category);
            }
        }

        return selectedButtons;
    }

    protected ComponentName getTopActivity(RootTaskInfo validTaskInfo) {
        // Window mode being WINDOW_MODE_MULTI_WINDOW implies TaskView might be visible on the
        // display. In such cases, topActivity reported by validTaskInfo will be the one hosted in
        // TaskView and not necessarily the main activity visible on display. Thus we should get
        // rootTaskInfo instead.
        if (validTaskInfo.getWindowingMode() == WINDOWING_MODE_MULTI_WINDOW) {
            try {
                RootTaskInfo rootTaskInfo =
                        ActivityTaskManager.getService().getRootTaskInfoOnDisplay(
                                WINDOWING_MODE_FULLSCREEN, ACTIVITY_TYPE_UNDEFINED,
                                validTaskInfo.displayId);
                return rootTaskInfo.topActivity;
            } catch (RemoteException e) {
                Log.e(TAG, "findSelectedButtons: Failed getting root task info", e);
            }
        } else {
            return validTaskInfo.topActivity;
        }

        return null;
    }

    private HashSet<CarSystemBarButton> findButtonsByComponentName(
            ComponentName componentName) {
        HashSet<CarSystemBarButton> buttons =
                mButtonsByComponentName.get(componentName.flattenToShortString());
        return (buttons != null) ? buttons :
                mButtonsByComponentName.get(componentName.flattenToString());
    }

    private String getPackageCategory(String packageName) {
        PackageManager pm = mContext.getPackageManager();
        Set<String> supportedCategories = mButtonsByCategory.keySet();
        for (String category : supportedCategories) {
            Intent intent = new Intent();
            intent.setPackage(packageName);
            intent.setAction(Intent.ACTION_MAIN);
            intent.addCategory(category);
            List<ResolveInfo> list = pm.queryIntentActivities(intent, 0);
            if (list.size() > 0) {
                // Cache this package name into ButtonsByPackage map, so we won't have to query
                // all categories next time this package name shows up.
                mButtonsByPackage.put(packageName, mButtonsByCategory.get(category));
                return category;
            }
        }
        return null;
    }

    // simple multi-map
    private static class ButtonMap extends HashMap<String, HashSet<CarSystemBarButton>> {

        public boolean add(String key, CarSystemBarButton value) {
            if (containsKey(key)) {
                return get(key).add(value);
            }
            HashSet<CarSystemBarButton> set = new HashSet<>();
            set.add(value);
            put(key, set);
            return true;
        }
    }
}
