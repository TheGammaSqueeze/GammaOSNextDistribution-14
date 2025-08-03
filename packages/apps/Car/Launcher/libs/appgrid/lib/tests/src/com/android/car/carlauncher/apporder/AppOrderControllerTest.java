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

import static com.google.common.truth.Truth.assertThat;

import static org.mockito.Mockito.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import android.content.ComponentName;

import androidx.lifecycle.MutableLiveData;
import androidx.test.ext.junit.runners.AndroidJUnit4;

import com.android.car.carlauncher.AppItem;
import com.android.car.carlauncher.AppMetaData;
import com.android.car.carlauncher.LauncherItem;
import com.android.car.carlauncher.LauncherItemMessageHelper;
import com.android.car.carlauncher.LauncherItemProto.LauncherItemMessage;
import com.android.car.carlauncher.datastore.launcheritem.LauncherItemListSource;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@RunWith(AndroidJUnit4.class)
public class AppOrderControllerTest {
    private AppOrderController mController;
    private Map<ComponentName, LauncherItem> mLauncherItemsMap;
    private List<LauncherItem> mDefaultOrder;
    private List<LauncherItem> mCustomizedOrder;
    @Mock
    private LauncherItemListSource mMockDataSource;
    private MutableLiveData<List<LauncherItem>> mCurrentAppList;

    @Before
    public void setUp() throws Exception {
        MockitoAnnotations.initMocks(this);
        when(mMockDataSource.exists()).thenReturn(true);

        mLauncherItemsMap = new HashMap<>();
        mDefaultOrder = spy(new ArrayList<>());
        mCustomizedOrder = spy(new ArrayList<>());
        mCurrentAppList = spy(new MutableLiveData<>());
        mCustomizedOrder.add(null);

        mController = spy(new AppOrderController(mMockDataSource, mCurrentAppList, mDefaultOrder,
                mCustomizedOrder));
    }

    @Test
    public void maybePublishAppList_loadAppListFromPlatform_noPublishing() {
        // tests that multiple platform connection does not publish app list
        mController.loadAppListFromPlatform(mLauncherItemsMap, mDefaultOrder);
        assertThat(mController.appsDataLoadingCompleted()).isFalse();

        mController.loadAppListFromPlatform(mLauncherItemsMap, mDefaultOrder);
        assertThat(mController.appsDataLoadingCompleted()).isFalse();

        verify(mController, times(2)).maybePublishAppList();
        verify(mCurrentAppList, never()).postValue(any());
    }

    @Test
    public void maybePublishAppList_loadAppListFromFile_noPublishing() {
        // tests that multiple file read does not publish app list
        mController.loadAppOrderFromFile();
        assertThat(mController.appsDataLoadingCompleted()).isFalse();

        mController.loadAppOrderFromFile();
        assertThat(mController.appsDataLoadingCompleted()).isFalse();

        verify(mController, times(2)).maybePublishAppList();
        verify(mCurrentAppList, never()).postValue(any());
    }

    @Test
    public void maybePublishAppList_publishing_defaultOrder() {
        when(mController.checkDataSourceExists()).thenReturn(false);

        mController.loadAppOrderFromFile();
        assertThat(mController.appsDataLoadingCompleted()).isFalse();
        assertThat(mController.shouldUseCustomOrder()).isFalse();

        mController.loadAppListFromPlatform(mLauncherItemsMap, mDefaultOrder);
        verify(mController, times(2)).maybePublishAppList();
        verify(mCurrentAppList, times(1)).postValue(any());
    }

    @Test
    public void maybePublishAppList_publishing_customOrder() {
        when(mController.checkDataSourceExists()).thenReturn(true);
        // if the data source exists and the list is non-empty, we expect to use custom oder
        List<LauncherItemMessage> nonEmptyMessageList = new ArrayList<>();
        LauncherItemMessage emptyAppItemMessage =
                (new AppItem("packageName", "className", "displayName", null))
                        .convertToMessage(1, 1);
        nonEmptyMessageList.add(emptyAppItemMessage);
        LauncherItemMessageHelper helper = new LauncherItemMessageHelper();
        when(mMockDataSource.readFromFile()).thenReturn(
                helper.convertToMessage(nonEmptyMessageList));

        mController.loadAppOrderFromFile();
        assertThat(mController.appsDataLoadingCompleted()).isFalse();
        assertThat(mController.shouldUseCustomOrder()).isTrue();

        mController.loadAppListFromPlatform(mLauncherItemsMap, mDefaultOrder);
        verify(mController, times(2)).maybePublishAppList();
        verify(mCurrentAppList, times(1)).postValue(any());
    }

    @Test
    public void setAppPosition_postValue() {
        // simulate platform app loading
        LauncherItem testItem1 = new AppItem("packageName1", "className1", "displayName1", null);
        LauncherItem testItem2 = new AppItem("packageName2", "className2", "displayName2", null);
        String packageName3 = "packageName3";
        LauncherItem testItem3 = new AppItem(packageName3, "className3", "displayName3", null);

        mLauncherItemsMap.put(new ComponentName("componentName1", "componentName1"), testItem1);
        mLauncherItemsMap.put(new ComponentName("componentName2", "componentName2"), testItem2);
        ComponentName componentName3 = new ComponentName("componentName3", "componentName3");
        mLauncherItemsMap.put(componentName3, testItem3);
        List<LauncherItem> newAppList = new ArrayList<>();
        newAppList.add(testItem1);
        newAppList.add(testItem2);
        newAppList.add(testItem3);
        when(mCurrentAppList.getValue()).thenReturn(newAppList);

        // simulate launcher cold start - no app list from file
        mController.loadAppOrderFromFile();
        assertThat(mController.shouldUseCustomOrder()).isFalse();
        mController.loadAppListFromPlatform(mLauncherItemsMap, newAppList);
        verify(mCurrentAppList, times(1)).postValue(any());

        AppMetaData mockApp3MetaData = mock(AppMetaData.class);
        when(mockApp3MetaData.getComponentName()).thenReturn(componentName3);

        // tests that setAppPosition posts update to the user interface
        mController.setAppPosition(0, mockApp3MetaData);
        verify(mCurrentAppList, times(2)).postValue(any());

        // tests that the setAppPosition correctly modifies app position
        assertThat(mCurrentAppList.getValue()).isNotNull();
        assertThat(mCurrentAppList.getValue().isEmpty()).isFalse();
        assertThat(mCurrentAppList.getValue().get(0).getPackageName()).isEqualTo(packageName3);
    }
}
