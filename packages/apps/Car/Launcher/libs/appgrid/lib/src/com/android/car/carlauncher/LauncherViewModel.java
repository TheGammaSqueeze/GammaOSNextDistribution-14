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

package com.android.car.carlauncher;

import android.content.ComponentName;
import android.content.Intent;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModel;

import com.android.car.carlauncher.apporder.AppOrderController;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * A launcher model decides how the apps are displayed.
 */
public class LauncherViewModel extends ViewModel {
    private final AppOrderController mAppOrderController;

    public LauncherViewModel(File launcherFileDir) {
        mAppOrderController = new AppOrderController(launcherFileDir);
    }

    public static final Comparator<LauncherItem> ALPHABETICAL_COMPARATOR = Comparator.comparing(
            LauncherItem::getDisplayName, String::compareToIgnoreCase);

    public LiveData<List<LauncherItem>> getCurrentLauncher() {
        return mAppOrderController.getAppOrderObservable();
    }

    /**
     * Read in apps order from file if exists, then publish app order to UI if valid.
     */
    public void loadAppsOrderFromFile() {
        mAppOrderController.loadAppOrderFromFile();
    }

    /**
     * Populate the apps based on alphabetical order and create mapping from packageName to
     * LauncherItem. Each item in the current launcher is AppItem.
     */
    public void processAppsInfoFromPlatform(AppLauncherUtils.LauncherAppsInfo launcherAppsInfo) {
        Map<ComponentName, LauncherItem> launcherItemsMap = new HashMap<>();
        List<LauncherItem> launcherItems = new ArrayList<>();
        List<AppMetaData> appMetaDataList = launcherAppsInfo.getLaunchableComponentsList();
        for (AppMetaData appMetaData : appMetaDataList) {
            LauncherItem nextItem = new AppItem(appMetaData);
            launcherItems.add(nextItem);
            launcherItemsMap.put(appMetaData.getComponentName(), nextItem);
        }
        Collections.sort(launcherItems, LauncherViewModel.ALPHABETICAL_COMPARATOR);
        mAppOrderController.loadAppListFromPlatform(launcherItemsMap, launcherItems);
    }

    /**
     * Notifies the controller that a change in the data model has been observed by the user
     * interface (e.g. platform apps list has been updated, user has updated the app order.)
     *
     * The controller should ONLY handle writing to disk in this method. This will ensure that all
     * changes to the data model is consistent with the user interface.
     */
    public void handleAppListChange() {
        mAppOrderController.handleAppListChange();
    }

    /**
     * Notifies the controller to move the given AppItem to a new position in the data model.
     */
    public void setAppPosition(int position, AppMetaData app) {
        mAppOrderController.setAppPosition(position, app);
    }

    /**
     * Updates the launcher data model when app mirroring intent is received.
     */
    public void updateMirroringItem(String packageName, Intent mirroringIntent) {
        mAppOrderController.updateMirroringItem(packageName, mirroringIntent);
    }
}
