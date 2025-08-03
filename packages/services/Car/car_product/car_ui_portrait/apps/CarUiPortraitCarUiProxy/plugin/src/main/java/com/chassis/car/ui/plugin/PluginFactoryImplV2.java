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

package com.chassis.car.ui.plugin;

import android.content.Context;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.car.ui.appstyledview.AppStyledViewControllerImpl;
import com.android.car.ui.plugin.PluginUiContextFactory;
import com.android.car.ui.plugin.oemapis.FocusAreaOEMV1;
import com.android.car.ui.plugin.oemapis.FocusParkingViewOEMV1;
import com.android.car.ui.plugin.oemapis.InsetsOEMV1;
import com.android.car.ui.plugin.oemapis.PluginFactoryOEMV2;
import com.android.car.ui.plugin.oemapis.appstyledview.AppStyledViewControllerOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.AdapterOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ListItemOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewAttributesOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.RecyclerViewOEMV1;
import com.android.car.ui.plugin.oemapis.recyclerview.ViewHolderOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV1;
import com.android.car.ui.recyclerview.CarUiListItem;
import com.android.car.ui.recyclerview.CarUiListItemAdapter;
import com.android.car.ui.recyclerview.CarUiRecyclerViewImpl;
import com.android.car.ui.utils.CarUiUtils;


import com.chassis.car.ui.plugin.appstyledview.AppStyledViewControllerAdapterProxyV1;
import com.chassis.car.ui.plugin.recyclerview.CarListItemAdapterAdapterProxy;
import com.chassis.car.ui.plugin.recyclerview.ListItemUtils;
import com.chassis.car.ui.plugin.recyclerview.RecyclerViewAdapterProxyV1;
import com.chassis.car.ui.plugin.toolbar.BaseLayoutInstaller;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * See {@code PluginFactoryImplV7}. This class is for backwards compatibility with apps that use
 * an older version of car-ui-lib.
 */
public class PluginFactoryImplV2 implements PluginFactoryOEMV2 {
    private final PluginUiContextFactory mPluginUiContextFactory;
    @Nullable
    private Function<Context, FocusParkingViewOEMV1> mFocusParkingViewFactory;
    @Nullable
    private Function<Context, FocusAreaOEMV1> mFocusAreaFactory;

    public PluginFactoryImplV2(Context pluginContext) {
        mPluginUiContextFactory = new PluginUiContextFactory(pluginContext);
    }

    @Override
    public void setRotaryFactories(
            Function<Context, FocusParkingViewOEMV1> focusParkingViewFactory,
            Function<Context, FocusAreaOEMV1> focusAreaFactory) {
        mFocusParkingViewFactory = focusParkingViewFactory;
        mFocusAreaFactory = focusAreaFactory;
    }

    @Nullable
    @Override
    public ToolbarControllerOEMV1 installBaseLayoutAround(
            @NonNull Context sourceContext,
            @NonNull View contentView,
            @Nullable Consumer<InsetsOEMV1> insetsChangedListener,
            boolean toolbarEnabled,
            boolean fullscreen) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        return BaseLayoutInstaller.installBaseLayoutAroundV1(
                pluginContext,
                contentView,
                insetsChangedListener,
                toolbarEnabled,
                fullscreen,
                mFocusParkingViewFactory,
                mFocusAreaFactory);
    }

    @Override
    public boolean customizesBaseLayout() {
        return true;
    }

    @Nullable
    @Override
    public AppStyledViewControllerOEMV1 createAppStyledView(@NonNull Context sourceContext) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        // build the app styled controller that will be delegated to
        AppStyledViewControllerImpl appStyledViewController = new AppStyledViewControllerImpl(
                pluginContext);
        return new AppStyledViewControllerAdapterProxyV1(appStyledViewController);
    }

    @Nullable
    @Override
    public RecyclerViewOEMV1 createRecyclerView(@NonNull Context sourceContext,
            @Nullable RecyclerViewAttributesOEMV1 recyclerViewAttributesOEMV1) {
        Context pluginContext = mPluginUiContextFactory.getPluginUiContext(sourceContext);
        CarUiRecyclerViewImpl recyclerView =
                new CarUiRecyclerViewImpl(pluginContext, recyclerViewAttributesOEMV1);
        return new RecyclerViewAdapterProxyV1(pluginContext, recyclerView,
                recyclerViewAttributesOEMV1);
    }

    @Override
    public AdapterOEMV1<? extends ViewHolderOEMV1> createListItemAdapter(
            List<ListItemOEMV1> items) {
        List<? extends CarUiListItem> staticItems = CarUiUtils.convertList(items,
                ListItemUtils::toStaticListItem);
        // Build the CarUiListItemAdapter that will be delegated to
        CarUiListItemAdapter carUiListItemAdapter = new CarUiListItemAdapter(staticItems);
        return new CarListItemAdapterAdapterProxy(
                carUiListItemAdapter, mPluginUiContextFactory.getRecentPluginUiContext());
    }
}
