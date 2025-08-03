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

package com.android.car.carlauncher.apporder;

import android.content.ComponentName;
import android.content.Intent;

import androidx.annotation.VisibleForTesting;
import androidx.lifecycle.MutableLiveData;

import com.android.car.carlauncher.AppItem;
import com.android.car.carlauncher.AppLauncherUtils;
import com.android.car.carlauncher.AppMetaData;
import com.android.car.carlauncher.LauncherItem;
import com.android.car.carlauncher.LauncherItemMessageHelper;
import com.android.car.carlauncher.LauncherItemProto.LauncherItemListMessage;
import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage;
import com.android.car.carlauncher.datastore.DataSourceController;
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Controller that manages the ordering of the app items in app grid.
 */
public class AppOrderController implements DataSourceController {
    // file name holding the user customized app order
    public static final String ORDER_FILE_NAME = "order.data";
    private final LauncherItemMessageHelper mItemHelper = new LauncherItemMessageHelper();
    // The app order of launcher items displayed to users
    private final MutableLiveData<List<LauncherItem>> mCurrentAppList;
    private final Map<ComponentName, LauncherItem> mLauncherItemMap = new HashMap<>();
    private final List<ComponentName> mProtoComponentNames = new ArrayList<>();
    private final List<LauncherItem> mDefaultOrder;
    private final List<LauncherItem> mCustomizedOrder;
    private final LauncherItemListSource mDataSource;
    private boolean mPlatformAppListLoaded;
    private boolean mCustomAppOrderFetched;
    private boolean mIsUserCustomized;

    public AppOrderController(File dataFileDirectory) {
        this(/* dataSource */ new LauncherItemListSource(dataFileDirectory, ORDER_FILE_NAME),
                /* appList */ new MutableLiveData<>(new ArrayList<>()),
                /* defaultOrder */ new ArrayList<>(),
                /* customizedOrder*/ new ArrayList<>());
    }

    public AppOrderController(LauncherItemListSource dataSource,
            MutableLiveData<List<LauncherItem>> appList, List<LauncherItem> defaultOrder,
            List<LauncherItem> customizedOrder) {
        mDataSource = dataSource;
        mCurrentAppList = appList;
        mDefaultOrder = defaultOrder;
        mCustomizedOrder = customizedOrder;
    }

    @Override
    public boolean checkDataSourceExists() {
        return mDataSource.exists();
    }

    public MutableLiveData<List<LauncherItem>> getAppOrderObservable() {
        return mCurrentAppList;
    }

    /**
     * Loads the full app list to be displayed in the app grid.
     */
    public void loadAppListFromPlatform(Map<ComponentName, LauncherItem> launcherItemsMap,
            List<LauncherItem> defaultItemOrder) {
        mDefaultOrder.clear();
        mDefaultOrder.addAll(defaultItemOrder);
        mLauncherItemMap.clear();
        mLauncherItemMap.putAll(launcherItemsMap);
        mPlatformAppListLoaded = true;
        maybePublishAppList();
    }

    /**
     * Loads any preexisting app order from the proto datastore on disk.
     */
    public void loadAppOrderFromFile() {
        // handle the app order reset case, where the proto file is removed from file system
        maybeHandleAppOrderReset();
        mProtoComponentNames.clear();
        List<LauncherItemMessage> protoItemMessage = mItemHelper.getSortedList(
                mDataSource.readFromFile());
        if (!protoItemMessage.isEmpty()) {
            mIsUserCustomized = true;
            for (LauncherItemMessage itemMessage : protoItemMessage) {
                ComponentName itemComponent = new ComponentName(
                        itemMessage.getPackageName(), itemMessage.getClassName());
                mProtoComponentNames.add(itemComponent);
            }
        }
        mCustomAppOrderFetched = true;
        maybePublishAppList();
    }

    @VisibleForTesting
    void maybeHandleAppOrderReset() {
        if (!checkDataSourceExists()) {
            mIsUserCustomized = false;
            mCustomizedOrder.clear();
        }
    }

    /**
     * Combine the proto order read from proto with any additional apps read from the platform, then
     * publish the new list to user interface.
     *
     * Prior to publishing the app list to the LiveData (and subsequently to the UI), both (1) the
     * default platform mapping and (2) user customized order must be read into memory. These
     * pre-fetch methods may be executed on different threads, so we should only publish the final
     * ordering when both steps have completed.
     */
    @VisibleForTesting
    void maybePublishAppList() {
        if (!appsDataLoadingCompleted()) {
            return;
        }
        // app names found in order proto file will be displayed first
        mCustomizedOrder.clear();
        List<LauncherItem> customOrder = new ArrayList<>();
        Set<ComponentName> namesFoundInProto = new HashSet<>();
        for (ComponentName name: mProtoComponentNames) {
            if (mLauncherItemMap.containsKey(name)) {
                customOrder.add(mLauncherItemMap.get(name));
                namesFoundInProto.add(name);
            }
        }
        mCustomizedOrder.addAll(customOrder);
        if (shouldUseCustomOrder()) {
            // new apps from platform not found in proto will be added to the end
            mCustomizedOrder.clear();
            List<ComponentName> newPlatformApps = mLauncherItemMap.keySet()
                    .stream()
                    .filter(element -> !namesFoundInProto.contains(element))
                    .collect(Collectors.toList());
            if (!newPlatformApps.isEmpty()) {
                Collections.sort(newPlatformApps);
                for (ComponentName newAppName: newPlatformApps) {
                    customOrder.add(mLauncherItemMap.get(newAppName));
                }
            }
            mCustomizedOrder.addAll(customOrder);
            mCurrentAppList.postValue(customOrder);
        } else {
            mCurrentAppList.postValue(mDefaultOrder);
            mCustomizedOrder.clear();
        }
        // reset apps data loading flags
        mPlatformAppListLoaded = mCustomAppOrderFetched = false;
    }

    @VisibleForTesting
    boolean appsDataLoadingCompleted() {
        return mPlatformAppListLoaded && mCustomAppOrderFetched;
    }

    @VisibleForTesting
    boolean shouldUseCustomOrder() {
        return mIsUserCustomized && mCustomizedOrder.size() != 0;
    }

    /**
     * Persistently writes the current in memory app order into disk.
     */
    public void handleAppListChange() {
        if (mIsUserCustomized) {
            List<LauncherItem> currentItems = mCurrentAppList.getValue();
            List<LauncherItemMessage> msgList = new ArrayList<LauncherItemMessage>();
            for (int i = 0; i < currentItems.size(); i++) {
                msgList.add(currentItems.get(i).convertToMessage(i, -1));
            }
            LauncherItemListMessage appOrderListMessage = mItemHelper.convertToMessage(msgList);
            mDataSource.writeToFile(appOrderListMessage);
        }
    }

    /**
     * Move an app to a specified index and post the value to LiveData.
     */
    public void setAppPosition(int position, AppMetaData app) {
        List<LauncherItem> current = mCurrentAppList.getValue();
        LauncherItem item = mLauncherItemMap.get(app.getComponentName());
        if (current != null && current.size() != 0 && position < current.size() && item != null) {
            mIsUserCustomized = true;
            current.remove(item);
            current.add(position, item);
            mCurrentAppList.postValue(current);
        }
    }

    /**
     * Handles the incoming mirroring intent from ViewModel.
     *
     * Update an AppItem's AppMetaData isMirroring state and its launch callback then post the
     * updated to LiveData.
     */
    public void updateMirroringItem(String packageName, Intent mirroringIntent) {
        List<LauncherItem> launcherList = mCurrentAppList.getValue();
        if (launcherList == null) {
            return;
        }
        List<LauncherItem> launcherListCopy = new ArrayList<>();
        for (LauncherItem item : launcherList) {
            if (item instanceof AppItem) {
                // TODO (b/272796126): move deep copying to inside DiffUtil
                AppMetaData metaData = ((AppItem) item).getAppMetaData();
                if (item.getPackageName().equals(packageName)) {
                    launcherListCopy.add(new AppItem(item.getPackageName(), item.getClassName(),
                            item.getDisplayName(), new AppMetaData(metaData.getDisplayName(),
                            metaData.getComponentName(), metaData.getIcon(),
                            metaData.getIsDistractionOptimized(), /* isMirroring= */ true,
                            metaData.getIsDisabledByTos(),
                                    contextArg ->
                                            AppLauncherUtils.launchApp(contextArg, mirroringIntent),
                            metaData.getAlternateLaunchCallback())));
                } else if (metaData.getIsMirroring()) {
                    Intent intent = new Intent(Intent.ACTION_MAIN)
                            .setComponent(metaData.getComponentName())
                            .addCategory(Intent.CATEGORY_LAUNCHER)
                            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    launcherListCopy.add(new AppItem(item.getPackageName(), item.getClassName(),
                            item.getDisplayName(), new AppMetaData(metaData.getDisplayName(),
                            metaData.getComponentName(), metaData.getIcon(),
                            metaData.getIsDistractionOptimized(), /* isMirroring= */ false,
                            metaData.getIsDisabledByTos(),
                                    contextArg ->
                                            AppLauncherUtils.launchApp(contextArg, intent),
                            metaData.getAlternateLaunchCallback())));
                } else {
                    launcherListCopy.add(item);
                }
            } else {
                launcherListCopy.add(item);
            }
        }
        mCurrentAppList.postValue(launcherListCopy);
    }
}
