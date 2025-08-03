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

import android.car.drivingstate.CarUxRestrictions;
import android.content.Context;
import android.graphics.drawable.Drawable;

import androidx.annotation.NonNull;

import com.android.car.ui.plugin.oemapis.Consumer;
import com.android.car.ui.plugin.oemapis.toolbar.ImeSearchInterfaceOEMV2;
import com.android.car.ui.plugin.oemapis.toolbar.MenuItemOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ProgressBarControllerOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.TabOEMV1;
import com.android.car.ui.plugin.oemapis.toolbar.ToolbarControllerOEMV2;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.SearchMode;
import com.android.car.ui.toolbar.Tab;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Wrapper class that passes the data to car-ui via ToolbarControllerOEMV2 interface
 */
public final class ToolbarAdapterProxyV2 implements ToolbarControllerOEMV2 {

    private final Context mPluginContext;
    private final ToolbarControllerImpl mToolbarController;

    public ToolbarAdapterProxyV2(
            @NonNull Context pluginContext, @NonNull ToolbarControllerImpl toolbarController) {
        mPluginContext = pluginContext;
        mToolbarController = toolbarController;
        // Set to true so that this toolbar will show menu items when in search mode if appropriate.
        // For example, the plugin adapter will call setMenuItems with null when its corresponding
        // toolbar's showMenuItemsWhileSearching is false, and call setMenuItems with menu items
        // (not null) when showMenuItemsWhileSearching is true.
        mToolbarController.setShowMenuItemsWhileSearching(true);
    }

    @Override
    public void setTitle(String s) {
        mToolbarController.setTitle(s);
    }

    @Override
    public void setSubtitle(String s) {
        mToolbarController.setSubtitle(s);
    }

    @Override
    public void setTabs(@NonNull List<TabOEMV1> list, int i) {
        List<Tab> tabList = list.stream()
                .filter(Objects::nonNull)
                .map(this::createTab).collect(Collectors.toList());
        mToolbarController.setTabs(tabList, i);
    }

    private Tab createTab(@NonNull TabOEMV1 tabOEMV1) {
        return Tab.builder()
                .setIcon(tabOEMV1.getIcon())
                .setText(tabOEMV1.getTitle())
                .setTinted(tabOEMV1.isTinted())
                .setSelectedListener(tab -> {
                    if (tabOEMV1.getOnSelectedListener() != null) {
                        tabOEMV1.getOnSelectedListener().run();
                    }
                })
                .build();
    }

    @Override
    public void selectTab(int i, boolean b) {
        mToolbarController.selectTab(i);
    }

    @Override
    public void setLogo(Drawable drawable) {
        mToolbarController.setLogo(drawable);
    }

    @Override
    public void setSearchHint(String s) {
        mToolbarController.setSearchHint(s);
    }

    @Override
    public void setSearchIcon(Drawable drawable) {
        mToolbarController.setSearchIcon(drawable);
    }

    @Override
    public void setSearchQuery(String s) {
        mToolbarController.setSearchQuery(s);
    }

    @Override
    public void setSearchMode(int i) {
        SearchMode mode;
        switch (i) {
            case SEARCH_MODE_DISABLED:
                mode = SearchMode.DISABLED;
                break;
            case SEARCH_MODE_SEARCH:
                mode = SearchMode.SEARCH;
                break;
            case SEARCH_MODE_EDIT:
                mode = SearchMode.EDIT;
                break;
            default:
                throw new IllegalArgumentException("Search mode not defined.");
        }
        mToolbarController.setSearchMode(mode);
    }

    @Override
    public ImeSearchInterfaceOEMV2 getImeSearchInterface() {
        return mToolbarController.getImeSearchInterface();
    }

    @Override
    public void setNavButtonMode(int i) {
        NavButtonMode mode;
        switch (i) {
            case NAV_BUTTON_MODE_DISABLED:
                mode = NavButtonMode.DISABLED;
                break;
            case NAV_BUTTON_MODE_BACK:
                mode = NavButtonMode.BACK;
                break;
            case NAV_BUTTON_MODE_CLOSE:
                mode = NavButtonMode.CLOSE;
                break;
            case NAV_BUTTON_MODE_DOWN:
                mode = NavButtonMode.DOWN;
                break;
            default:
                throw new IllegalArgumentException("Nav button mode not defined.");
        }
        mToolbarController.setNavButtonMode(mode);
    }

    @Override
    public void setMenuItems(@NonNull List<MenuItemOEMV1> list) {
        List<MenuItem> menuItemList =
                list.stream()
                        .filter(Objects::nonNull)
                        .map(this::createMenuItem).collect(Collectors.toList());
        mToolbarController.setMenuItems(menuItemList);
    }

    private MenuItem createMenuItem(@NonNull MenuItemOEMV1 menuItemOEMV1) {
        MenuItem.Builder menuItemBuilder = MenuItem.builder(mPluginContext);
        if (menuItemOEMV1.isActivatable()) menuItemBuilder.setActivatable();
        if (menuItemOEMV1.isCheckable()) menuItemBuilder.setCheckable();

        menuItemBuilder
                .setEnabled(menuItemOEMV1.isEnabled())
                .setVisible(menuItemOEMV1.isVisible())
                .setPrimary(menuItemOEMV1.isPrimary())
                .setTinted(menuItemOEMV1.isTinted())
                .setTitle(menuItemOEMV1.getTitle())
                .setIcon(menuItemOEMV1.getIcon())
                .setDisplayBehavior(menuItemOEMV1.getDisplayBehavior() == 0
                        ? MenuItem.DisplayBehavior.ALWAYS : MenuItem.DisplayBehavior.NEVER)
                .setShowIconAndTitle(menuItemOEMV1.isShowingIconAndTitle())
                .setOnClickListener(menuItem -> {
                    if (menuItemOEMV1.getOnClickListener() != null) {
                        menuItemOEMV1.getOnClickListener().run();
                    }
                })
                .setId(menuItemOEMV1.getKey());

        if (menuItemOEMV1.isActivatable()) {
            menuItemBuilder.setActivatable();
        }
        if (menuItemOEMV1.isCheckable()) {
            menuItemBuilder.setCheckable();
        }
        if (menuItemOEMV1.isActivated()) {
            menuItemBuilder.setActivated(menuItemOEMV1.isActivated());
        }
        if (menuItemOEMV1.isChecked()) {
            menuItemBuilder.setChecked(menuItemOEMV1.isChecked());
        }

        menuItemBuilder.setUxRestrictions(menuItemOEMV1.isRestricted()
                ? CarUxRestrictions.UX_RESTRICTIONS_FULLY_RESTRICTED
                : CarUxRestrictions.UX_RESTRICTIONS_BASELINE);

        return menuItemBuilder.build();
    }

    @Override
    public void setSearchListener(Consumer<String> consumer) {
        if (consumer != null) {
            mToolbarController.registerSearchListener(s -> consumer.accept(s));
        } else {
            mToolbarController.registerSearchListener(null);
        }

    }

    @Override
    public void setSearchCompletedListener(Runnable runnable) {
        if (runnable != null) {
            mToolbarController.registerSearchCompletedListener(() -> runnable.run());
        } else {
            mToolbarController.registerSearchCompletedListener(null);
        }
    }

    @Override
    public void setBackListener(Runnable runnable) {
        if (runnable != null) {
            mToolbarController.registerBackListener(() -> {
                runnable.run();
                return true;
            });
        } else {
            mToolbarController.registerBackListener(null);
        }
    }

    @Override
    public ProgressBarControllerOEMV1 getProgressBar() {
        return new ProgressBarAdapterProxy(mToolbarController.getProgressBar());
    }

    @Override
    public void setBackgroundShown(boolean b) {
        mToolbarController.setBackgroundShown(b);
    }
}
