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

import static android.view.View.GONE;
import static android.view.View.VISIBLE;

import static com.android.car.ui.utils.CarUiUtils.findViewByRefId;
import static com.android.car.ui.utils.CarUiUtils.requireViewByRefId;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.DrawableRes;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.XmlRes;
import androidx.core.content.ContextCompat;

import com.android.car.ui.AlertDialogBuilder;
import com.android.car.ui.CarUiText;
import com.android.car.ui.imewidescreen.CarUiImeSearchListItem;
import com.android.car.ui.plugin.oemapis.toolbar.ImeSearchInterfaceOEMV2;
import com.android.car.ui.recyclerview.CarUiContentListItem;
import com.android.car.ui.recyclerview.CarUiListItem;
import com.android.car.ui.recyclerview.CarUiListItemAdapter;
import com.android.car.ui.toolbar.DeprecatedTabWrapper;
import com.android.car.ui.toolbar.MenuItem;
import com.android.car.ui.toolbar.MenuItemXmlParserUtil;
import com.android.car.ui.toolbar.NavButtonMode;
import com.android.car.ui.toolbar.ProgressBarController;
import com.android.car.ui.toolbar.SearchCapabilities;
import com.android.car.ui.toolbar.SearchConfig;
import com.android.car.ui.toolbar.SearchMode;
import com.android.car.ui.toolbar.SearchView;
import com.android.car.ui.toolbar.SearchWidescreenController;
import com.android.car.ui.toolbar.Tab;
import com.android.car.ui.toolbar.Toolbar;
import com.android.car.ui.toolbar.ToolbarController;
import com.android.car.ui.utils.CarUiUtils;
import com.android.car.ui.widget.CarUiTextView;

import com.chassis.car.ui.plugin.R;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/**
 * The implementation of {@link ToolbarController}. This class takes a ViewGroup, and looks in the
 * ViewGroup to find all the toolbar-related views to control.
 */
public class ToolbarControllerImpl implements ToolbarController {
    private static final String TAG = "CarUiPortraitToolbarController";

    @Nullable
    private View mBackground;
    private ImageView mNavIcon;
    private ViewGroup mNavIconContainer;
    private ViewGroup mTitleLogoContainer;
    private ViewGroup mTitleContainer;
    private Runnable mOnLogoClickListener;
    private CarUiTextView mTitle;
    @NonNull
    private CarUiText mTitleText = new CarUiText.Builder("").build();
    private CarUiTextView mSubtitle;
    @NonNull
    private CarUiText mSubtitleText = new CarUiText.Builder("").build();
    private ImageView mLogo;
    private TabLayout mTabLayout;
    private ViewGroup mMenuItemsContainer;
    private SearchConfig.SearchConfigBuilder mSearchConfigBuilder;
    private final FrameLayout mSearchViewContainer;
    private SearchView mSearchView;

    // Cached values that we will send to views when they are inflated
    private CharSequence mSearchHint;
    private Drawable mSearchIcon;
    private String mSearchQuery;
    private final Context mContext;
    private final Context mPluginContext;
    private final Set<Consumer<String>> mOnSearchListeners = new HashSet<>();
    private final Set<Runnable> mOnSearchCompletedListeners = new HashSet<>();
    private final Set<Toolbar.OnSearchListener> mDeprecatedSearchListeners = new HashSet<>();
    private final Set<Toolbar.OnSearchCompletedListener> mDeprecatedSearchCompletedListeners =
            new HashSet<>();

    private final Set<Toolbar.OnBackListener> mDeprecatedBackListeners = new HashSet<>();
    private final Set<Supplier<Boolean>> mBackListeners = new HashSet<>();
    private final Set<Toolbar.OnTabSelectedListener> mOnTabSelectedListeners = new HashSet<>();

    private final MenuItem mOverflowButton;
    private boolean mShowTabsInSubpage = false;
    private boolean mHasLogo = false;
    private boolean mShowMenuItemsWhileSearching;
    private Toolbar.State mState = Toolbar.State.HOME;
    private boolean mStateSet = false;
    private NavButtonMode mNavButtonMode = NavButtonMode.DISABLED;
    private SearchMode mSearchMode = SearchMode.DISABLED;
    @NonNull
    private List<MenuItem> mMenuItems = Collections.emptyList();
    private List<MenuItem> mOverflowItems = new ArrayList<>();
    private final List<CarUiListItem> mUiOverflowItems = new ArrayList<>();
    private final CarUiListItemAdapter mOverflowAdapter;
    private final List<MenuItemRenderer> mMenuItemRenderers = new ArrayList<>();
    private final List<DeprecatedTabWrapper> mDeprecatedTabs = new ArrayList<>();
    private View[] mMenuItemViews;
    private int mMenuItemsXmlId = 0;
    private AlertDialog mOverflowDialog;
    private final boolean mShowLogo;
    private SearchConfig mSearchConfigForWidescreen;
    private final ProgressBarController mProgressBar;
    private final View mNavIconSpacer;
    private final View mLogoSpacer;
    private final View mTitleSpacer;
    private final MenuItem.Listener mOverflowItemListener = item -> {
        updateOverflowDialog(item);
        update();
    };
    private Consumer<TextView> mSearchTextViewConsumer = null;
    private BiConsumer<String, Bundle> mOnPrivateImeCommandListener = null;


    public ToolbarControllerImpl(View view, Context pluginContext) {
        mContext = view.getContext();
        mPluginContext = pluginContext;
        mOverflowButton = MenuItem.builder(mPluginContext)
                .setIcon(R.drawable.car_ui_icon_overflow_menu)
                .setTitle(R.string.car_ui_toolbar_menu_item_overflow_title)
                .setOnClickListener(v -> {
                    if (mOverflowDialog == null) {
                        if (Log.isLoggable(TAG, Log.ERROR)) {
                            Log.e(TAG, "Overflow dialog was null when trying to show it!");
                        }
                    } else {
                        mOverflowDialog.show();
                    }
                })
                .build();

        mShowLogo = mPluginContext.getResources().getBoolean(
                R.bool.car_ui_toolbar_show_logo);
        mSearchHint = mPluginContext.getString(R.string.car_ui_toolbar_default_search_hint);

        mBackground = findViewByRefId(view, R.id.car_ui_portrait_toolbar_background);
        mLogoSpacer = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_logo_spacer);
        mLogo = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_logo);
        mOnLogoClickListener = null;
        mTitleLogoContainer = requireViewByRefId(view,
                R.id.car_ui_portrait_toolbar_title_logo_container);
        mTitleSpacer = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_title_spacer);
        mTitleContainer = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_title_container);
        mTitle = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_title);
        mSubtitle = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_subtitle);
        mNavIconSpacer = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_nav_icon_spacer);
        mNavIconContainer = requireViewByRefId(view,
                R.id.car_ui_portrait_toolbar_nav_icon_container);
        mNavIcon = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_nav_icon);
        mSearchViewContainer = requireViewByRefId(view, R.id.car_ui_toolbar_search_view_container);
        mMenuItemsContainer = requireViewByRefId(view, R.id.car_ui_toolbar_menu_items_container);
        mProgressBar = new ProgressBarControllerImpl(
                requireViewByRefId(view, R.id.car_ui_toolbar_progress_bar));
        mTabLayout = requireViewByRefId(view, R.id.car_ui_portrait_toolbar_tabs);
        mSearchConfigBuilder = SearchConfig.builder();

        setBackgroundShown(true);

        mOverflowAdapter = new CarUiListItemAdapter(mUiOverflowItems);
        update();
    }

    private Context getContext() {
        return mContext;
    }

    private Drawable getDrawable(@DrawableRes int resId) {
        if (resId == 0) {
            return null;
        } else {
            return ContextCompat.getDrawable(getContext(), resId);
        }
    }

    /**
     * Sets the title of the toolbar to a string resource.
     *
     * <p>The title may not always be shown, for example with one row layout with tabs.
     */
    @Override
    public void setTitle(@StringRes int title) {
        setTitle(title == 0 ? null : getContext().getString(title));
    }

    /**
     * Sets the title of the toolbar to a CharSequence.
     *
     * <p>The title may not always be shown, for example with one row layout with tabs.
     */
    @Override
    public void setTitle(CharSequence title) {
        mTitleText = title == null ? new CarUiText.Builder("").build() : new CarUiText.Builder(
                title).build();
        mTitle.setText(mTitleText);
        update();
    }

    @Override
    public void setTitle(CarUiText title) {
        mTitleText = title;
        mTitle.setText(mTitleText);
        update();
    }

    @Override
    public CharSequence getTitle() {
        return mTitleText.getPreferredText();
    }

    /**
     * Sets the subtitle of the toolbar to a string resource.
     *
     * <p>The title may not always be shown, for example with one row layout with tabs.
     */
    @Override
    public void setSubtitle(@StringRes int subTitle) {
        setSubtitle(subTitle == 0 ? null : getContext().getString(subTitle));
    }

    /**
     * Sets the subtitle of the toolbar to a CharSequence.
     *
     * <p>The title may not always be shown, for example with one row layout with tabs.
     */
    @Override
    public void setSubtitle(CharSequence subTitle) {
        mSubtitleText = subTitle == null ? new CarUiText.Builder("").build()
                : new CarUiText.Builder(subTitle).build();
        mSubtitle.setText(mSubtitleText);
        update();
    }

    @Override
    public void setSubtitle(CarUiText text) {
        mSubtitleText = text;
        mSubtitle.setText(mSubtitleText);
        update();
    }

    @Override
    public CharSequence getSubtitle() {
        return mSubtitleText.getPreferredText();
    }

    @Override
    public void setTabs(@Nullable List<Tab> tabs) {
        setTabs(tabs, 0);
    }

    @Override
    public void setTabs(@Nullable List<Tab> tabs, int selectedTab) {
        mDeprecatedTabs.clear();
        setTabsInternal(tabs, selectedTab);
    }

    @Override
    public List<Tab> getTabs() {
        return mTabLayout.getTabs();
    }

    private void setTabsInternal(@Nullable List<Tab> tabs, int selectedTab) {
        if (tabs == null || tabs.isEmpty()) {
            selectedTab = -1;
        } else if (selectedTab < 0 || selectedTab >= tabs.size()) {
            throw new IllegalArgumentException("Tab position is invalid: " + selectedTab);
        }
        mTabLayout.setTabs(tabs, selectedTab);
        update();
    }

    /**
     * Gets the number of tabs in the toolbar. The tabs can be retrieved using
     * {@link #getTab(int)}.
     */
    @Override
    public int getTabCount() {
        return mDeprecatedTabs.size();
    }

    @Override
    public int getTabPosition(com.android.car.ui.toolbar.TabLayout.Tab tab) {
        for (int i = 0; i < mDeprecatedTabs.size(); i++) {
            if (mDeprecatedTabs.get(i).getDeprecatedTab() == tab) {
                return i;
            }
        }
        return -1;
    }

    @Override
    public void addTab(com.android.car.ui.toolbar.TabLayout.Tab newTab) {
        mDeprecatedTabs.add(new DeprecatedTabWrapper(getContext(), newTab,
                this::updateModernTabsFromDeprecatedOnes, (tab) -> {
            for (Toolbar.OnTabSelectedListener listener : mOnTabSelectedListeners) {
                listener.onTabSelected(newTab);
            }
        }));
        updateModernTabsFromDeprecatedOnes();
    }

    private void updateModernTabsFromDeprecatedOnes() {
        List<Tab> modernTabs = new ArrayList<>();

        for (DeprecatedTabWrapper tab : mDeprecatedTabs) {
            modernTabs.add(tab.getModernTab());
        }

        setTabsInternal(modernTabs, 0);
    }

    @Override
    public void clearAllTabs() {
        mDeprecatedTabs.clear();
        setTabs(null);
    }

    @Override
    public com.android.car.ui.toolbar.TabLayout.Tab getTab(int position) {
        return mDeprecatedTabs.get(position).getDeprecatedTab();
    }

    /**
     * Selects a tab added to this toolbar. See {@link #addTab(TabLayout.Tab)}.
     */
    @Override
    public void selectTab(int position) {
        mTabLayout.selectTab(position);
    }

    @Override
    public int getSelectedTab() {
        return mTabLayout.getSelectedTab();
    }

    /**
     * Sets whether or not tabs should also be shown in the SUBPAGE {@link Toolbar.State}.
     */
    @Override
    public void setShowTabsInSubpage(boolean showTabs) {
        if (showTabs != mShowTabsInSubpage) {
            mShowTabsInSubpage = showTabs;
            update();
        }
    }

    /**
     * Gets whether or not tabs should also be shown in the SUBPAGE {@link Toolbar.State}.
     */
    @Override
    public boolean getShowTabsInSubpage() {
        return mShowTabsInSubpage;
    }

    /**
     * Sets the logo to display in this toolbar. If navigation icon is being displayed, this logo
     * will be displayed next to the title.
     */
    @Override
    public void setLogo(@DrawableRes int resId) {
        asyncSetLogo(resId, Runnable::run);
    }

    private void asyncSetLogo(int resId, Executor bgExecutor) {
        if (!mShowLogo) {
            // If no logo should be shown then we act as if we never received one.
            return;
        }
        if (resId != 0) {
            bgExecutor.execute(() -> {
                // load resource on background thread.
                Drawable drawable = getDrawable(resId);
                mLogo.post(() -> {
                    // UI thread.
                    mLogo.setImageDrawable(drawable);
                });
            });
            mHasLogo = true;
        } else {
            mHasLogo = false;
        }
        update();
    }

    /**
     * Sets the logo to display in this toolbar. If navigation icon is being displayed, this logo
     * will be displayed next to the title.
     */
    @Override
    public void setLogo(Drawable drawable) {
        if (!mShowLogo) {
            // If no logo should be shown then we act as if we never received one.
            return;
        }
        if (drawable != null) {
            mLogo.setImageDrawable(drawable);
            mHasLogo = true;
        } else {
            mHasLogo = false;
        }

        update();
    }

    @Override
    public void setOnLogoClickListener(@Nullable Runnable listener) {
        if (mOnLogoClickListener != listener) {
            mOnLogoClickListener = listener;
            update();
        }
    }

    /**
     * Sets the hint for the search bar.
     */
    @Override
    public void setSearchHint(@StringRes int resId) {
        setSearchHint(getContext().getString(resId));
    }

    /**
     * Sets the hint for the search bar.
     */
    public void setSearchHint(CharSequence hint) {
        mSearchHint = hint;
        if (mSearchView != null) {
            mSearchView.setHint(mSearchHint);
        }
    }

    /**
     * Gets the search hint
     */
    @Override
    public CharSequence getSearchHint() {
        return mSearchHint;
    }

    /**
     * Sets the icon to display in the search box.
     *
     * <p>The icon will be lost on configuration change, make sure to set it in onCreate() or
     * a similar place.
     */
    @Override
    public void setSearchIcon(@DrawableRes int resId) {
        setSearchIcon(getDrawable(resId));
    }

    /**
     * Sets the icon to display in the search box.
     *
     * <p>The icon will be lost on configuration change, make sure to set it in onCreate() or
     * a similar place.
     */
    @Override
    public void setSearchIcon(Drawable d) {
        if (!Objects.equals(d, mSearchIcon)) {
            mSearchIcon = d;
            if (mSearchView != null) {
                mSearchView.setIcon(mSearchIcon);
            }
        }
    }


    /**
     * Sets the {@link Toolbar.NavButtonMode}
     */
    @Override
    public void setNavButtonMode(Toolbar.NavButtonMode mode) {
        NavButtonMode modernMode;
        switch (mode) {
            case BACK:
                modernMode = NavButtonMode.BACK;
                break;
            case DOWN:
                modernMode = NavButtonMode.DOWN;
                break;
            case CLOSE:
                modernMode = NavButtonMode.CLOSE;
                break;
            case DISABLED:
            default:
                modernMode = NavButtonMode.DISABLED;
                break;
        }
        setNavButtonMode(modernMode);
    }

    @Override
    public void setNavButtonMode(NavButtonMode mode) {
        if (mode != mNavButtonMode) {
            mNavButtonMode = mode;
            update();
        }
    }

    /**
     * Gets the {@link Toolbar.NavButtonMode}
     */
    @Override
    public NavButtonMode getNavButtonMode() {
        return mNavButtonMode;
    }

    /**
     * Show/hide the background. When hidden, the toolbar is completely transparent.
     */
    @Override
    public void setBackgroundShown(boolean shown) {
        if (mBackground == null) {
            return;
        }

        if (shown) {
            mBackground.setBackground(getDrawable(R.drawable.car_ui_portrait_toolbar_background));
        } else {
            mBackground.setBackground(null);
        }
    }

    /**
     * Returns true is the toolbar background is shown
     */
    @Override
    public boolean getBackgroundShown() {
        if (mBackground == null) {
            return true;
        }

        return mBackground.getBackground() != null;
    }

    private void setMenuItemsInternal(@Nullable List<MenuItem> items) {
        if (items == null) {
            items = Collections.emptyList();
        }

        List<MenuItem> visibleMenuItems = new ArrayList<>();
        List<MenuItem> overflowItems = new ArrayList<>();
        AtomicInteger loadedMenuItems = new AtomicInteger(0);

        synchronized (this) {
            if (items.equals(mMenuItems)) {
                return;
            }

            for (MenuItem item : items) {
                if (item.getDisplayBehavior() == MenuItem.DisplayBehavior.NEVER) {
                    overflowItems.add(item);
                    item.setListener(mOverflowItemListener);
                } else {
                    visibleMenuItems.add(item);
                }
            }

            // Copy the list so that if the list is modified and setMenuItems is called again,
            // the equals() check will fail. Note that the MenuItems are not copied here.
            mMenuItems = new ArrayList<>(items);
            mOverflowItems = overflowItems;
            mMenuItemRenderers.clear();
            mMenuItemsContainer.removeAllViews();

            if (!overflowItems.isEmpty()) {
                visibleMenuItems.add(mOverflowButton);
                createOverflowDialog();
            }

            View[] menuItemViews = new View[visibleMenuItems.size()];
            mMenuItemViews = menuItemViews;

            for (int i = 0; i < visibleMenuItems.size(); ++i) {
                int index = i;
                MenuItem item = visibleMenuItems.get(i);
                MenuItemRenderer renderer = new MenuItemRenderer(item, mMenuItemsContainer);
                mMenuItemRenderers.add(renderer);
                renderer.createView(view -> {
                    synchronized (ToolbarControllerImpl.this) {
                        if (menuItemViews != mMenuItemViews) {
                            return;
                        }

                        menuItemViews[index] = view;
                        if (loadedMenuItems.addAndGet(1) == menuItemViews.length) {
                            for (View v : menuItemViews) {
                                mMenuItemsContainer.addView(v);
                            }
                        }
                    }
                });
            }
        }

        update();
    }

    /**
     * Sets the {@link MenuItem Menuitems} to display.
     */
    @Override
    public void setMenuItems(@Nullable List<MenuItem> items) {
        mMenuItemsXmlId = 0;
        setMenuItemsInternal(items);
    }

    /**
     * Sets the {@link MenuItem Menuitems} to display to a list defined in XML.
     *
     * <p>If this method is called twice with the same argument (and {@link #setMenuItems(List)}
     * wasn't called), nothing will happen the second time, even if the MenuItems were changed.
     *
     * <p>The XML file must have one <MenuItems> tag, with a variable number of <MenuItem>
     * child tags. See CarUiToolbarMenuItem in CarUi's attrs.xml for a list of available
     * attributes.
     * <p>
     * Example:
     * <pre>
     * <MenuItems>
     *     <MenuItem
     *         app:title="Foo"/>
     *     <MenuItem
     *         app:title="Bar"
     *         app:icon="@drawable/ic_tracklist"
     *         app:onClick="xmlMenuItemClicked"/>
     *     <MenuItem
     *         app:title="Bar"
     *         app:checkable="true"
     *         app:uxRestrictions="FULLY_RESTRICTED"
     *         app:onClick="xmlMenuItemClicked"/>
     * </MenuItems>
     * </pre>
     *
     * @return The MenuItems that were loaded from XML.
     * @see #setMenuItems(List)
     */
    @Override
    public List<MenuItem> setMenuItems(@XmlRes int resId) {
        if (mMenuItemsXmlId != 0 && mMenuItemsXmlId == resId) {
            return mMenuItems;
        }

        mMenuItemsXmlId = resId;
        List<MenuItem> menuItems = MenuItemXmlParserUtil.readMenuItemList(getContext(), resId);
        setMenuItemsInternal(menuItems);
        return menuItems;
    }

    /**
     * Gets the {@link MenuItem MenuItems} currently displayed
     */
    @Override
    @NonNull
    public List<MenuItem> getMenuItems() {
        return Collections.unmodifiableList(mMenuItems);
    }

    /**
     * Gets a {@link MenuItem} by id.
     */
    @Override
    @Nullable
    public MenuItem findMenuItemById(int id) {
        for (MenuItem item : mMenuItems) {
            if (item.getId() == id) {
                return item;
            }
        }
        return null;
    }

    /**
     * Gets a {@link MenuItem} by id. Will throw an IllegalArgumentException if not found.
     */
    @Override
    @NonNull
    public MenuItem requireMenuItemById(int id) {
        MenuItem result = findMenuItemById(id);

        if (result == null) {
            throw new IllegalArgumentException("ID does not reference a MenuItem on this Toolbar");
        }

        return result;
    }

    private int countVisibleOverflowItems() {
        int numVisibleItems = 0;
        for (MenuItem item : mOverflowItems) {
            if (item.isVisible()) {
                numVisibleItems++;
            }
        }
        return numVisibleItems;
    }

    private void createOverflowDialog() {
        // Need to check if overflow dialog is showing before the new AlertDialog is created
        // because it will return false when checked after
        boolean isShowing = mOverflowDialog == null ? false : mOverflowDialog.isShowing();

        mUiOverflowItems.clear();
        for (MenuItem menuItem : mOverflowItems) {
            if (menuItem.isVisible()) {
                mUiOverflowItems.add(toCarUiContentListItem(menuItem));
            }
        }
        mOverflowDialog = new AlertDialogBuilder(getContext())
                .setAdapter(mOverflowAdapter)
                .create();

        // When show() is called on a dialog, it is created from scratch. This means the underlying
        // list of the dialog is instantiated and the corresponding adapter is set on it. So, any
        // changes to the data of the dialog's list's adapter prior to the call to show() will be
        // be shown on screen. Previously, if the dialog was being shown and the data of the adapter
        // was changed (i.e., setMenuItems ->  setMenuItemsInternal -> createOverflowDialog), the
        // data of the adapter would change without show() being called, causing the updated data to
        // not be reflected on screen. So, call notifyDataSetChanged if the dialog is being shown.
        if (isShowing) {
            mOverflowAdapter.notifyDataSetChanged();
        }
    }

    private void updateOverflowDialog(MenuItem changedItem) {
        int itemIndex = mOverflowItems.indexOf(changedItem);
        if (itemIndex >= 0) {
            mUiOverflowItems.set(itemIndex, toCarUiContentListItem(changedItem));
            mOverflowAdapter.notifyItemChanged(itemIndex);
        } else {
            createOverflowDialog();
        }
    }

    private CarUiContentListItem toCarUiContentListItem(MenuItem menuItem) {
        CarUiContentListItem carUiItem;
        if (menuItem.isCheckable()) {
            carUiItem = new CarUiContentListItem(CarUiContentListItem.Action.SWITCH);
        } else {
            carUiItem = new CarUiContentListItem(CarUiContentListItem.Action.NONE);
        }
        carUiItem.setIcon(menuItem.getIcon());
        carUiItem.setActivated(menuItem.isActivated());
        carUiItem.setChecked(menuItem.isChecked());
        carUiItem.setEnabled(menuItem.isEnabled());
        carUiItem.setTitle(menuItem.getTitle());
        carUiItem.setOnItemClickedListener(item -> {
            menuItem.performClick();
            mOverflowDialog.hide();
        });
        return carUiItem;
    }

    /**
     * Set whether or not to show the {@link MenuItem MenuItems} while searching. Default false.
     * Even if this is set to true, the {@link MenuItem} created by
     * {@link MenuItem.Builder#setToSearch()} will still be hidden.
     */
    @Override
    public void setShowMenuItemsWhileSearching(boolean showMenuItems) {
        mShowMenuItemsWhileSearching = showMenuItems;
        update();
    }

    /**
     * Returns if {@link MenuItem MenuItems} are shown while searching
     */
    @Override
    public boolean getShowMenuItemsWhileSearching() {
        return mShowMenuItemsWhileSearching;
    }

    /**
     * Sets the search query.
     */
    @Override
    public void setSearchQuery(String query) {
        if (mSearchView != null) {
            mSearchView.setSearchQuery(query);
        } else {
            mSearchQuery = query;
            for (Toolbar.OnSearchListener listener : mDeprecatedSearchListeners) {
                listener.onSearch(query);
            }
            for (Consumer<String> listener : mOnSearchListeners) {
                listener.accept(query);
            }
        }
    }

    /**
     * Sets the state of the toolbar. This will show/hide the appropriate elements of the toolbar
     * for the desired state.
     */
    @Override
    public void setState(Toolbar.State state) {
        if (mState != state || !mStateSet) {
            mState = state;
            mStateSet = true;
            update();
        }
    }

    @Override
    public void setSearchMode(SearchMode mode) {
        if (mStateSet) {
            throw new IllegalStateException("Cannot set search mode when using setState()");
        }
        if (mSearchMode != mode) {
            mSearchMode = mode;
            update();
        }
    }

    @Override
    public SearchMode getSearchMode() {
        return mSearchMode;
    }

    private void update() {
        // Start by removing mState/mStateSet from the equation by incorporating them into other
        // variables.
        NavButtonMode navButtonMode = mNavButtonMode;
        if (mStateSet) {
            if (mState == Toolbar.State.HOME) {
                navButtonMode = NavButtonMode.DISABLED;
            } else if (navButtonMode == NavButtonMode.DISABLED) {
                navButtonMode = NavButtonMode.BACK;
            }
        }

        SearchMode searchMode = mSearchMode;
        if (mStateSet) {
            if (mState == Toolbar.State.SEARCH) {
                searchMode = SearchMode.SEARCH;
            } else if (mState == Toolbar.State.EDIT) {
                searchMode = SearchMode.EDIT;
            } else {
                searchMode = SearchMode.DISABLED;
            }
        }

        boolean hasLogo = mHasLogo;
        if (mStateSet && (mState == Toolbar.State.SEARCH || mState == Toolbar.State.EDIT)) {
            hasLogo = false;
        }

        boolean hasTabs = mTabLayout.hasTabs();
        if (mStateSet && mState != Toolbar.State.HOME
                && !(mState == Toolbar.State.SUBPAGE && mShowTabsInSubpage)) {
            hasTabs = false;
        }

        boolean isSearching = searchMode != SearchMode.DISABLED;
        if (mSearchView == null && isSearching) {
            inflateSearchView();
        }

        for (MenuItemRenderer renderer : mMenuItemRenderers) {
            renderer.setToolbarIsSearching(searchMode == SearchMode.SEARCH);
        }

        View.OnClickListener backClickListener = (v) -> {
            boolean absorbed = false;
            List<Toolbar.OnBackListener> deprecatedListenersCopy =
                    new ArrayList<>(mDeprecatedBackListeners);
            List<Supplier<Boolean>> listenersCopy = new ArrayList<>(mBackListeners);
            for (Toolbar.OnBackListener listener : deprecatedListenersCopy) {
                absorbed = absorbed || listener.onBack();
            }
            for (Supplier<Boolean> listener : listenersCopy) {
                absorbed = absorbed || listener.get();
            }

            if (!absorbed) {
                Activity activity = CarUiUtils.getActivity(getContext());
                if (activity != null) {
                    activity.onBackPressed();
                }
            }
        };

        switch (navButtonMode) {
            case CLOSE:
                mNavIcon.setImageResource(R.drawable.car_ui_icon_close);
                break;
            case DOWN:
                mNavIcon.setImageResource(R.drawable.car_ui_icon_down);
                break;
            default:
                mNavIcon.setImageResource(R.drawable.car_ui_icon_arrow_back);
                break;
        }

        mLogoSpacer.setVisibility(
                !hasTabs && navButtonMode != NavButtonMode.DISABLED ? VISIBLE : GONE);
        mLogo.setVisibility(hasLogo ? VISIBLE : GONE);

        mTitleSpacer.setVisibility(
                hasTabs && navButtonMode != NavButtonMode.DISABLED ? VISIBLE : GONE);
        mTitleLogoContainer.setOnClickListener(
                hasLogo && mOnLogoClickListener != null && navButtonMode != NavButtonMode.DISABLED
                        ? v -> mOnLogoClickListener.run() : null);
        mTitleLogoContainer.setVisibility(isSearching ? GONE : VISIBLE);

        // Show the nav icon container if we're not in the home space or the logo fills the nav icon
        // container. If car_ui_toolbar_nav_icon_reserve_space is true, hiding it will still reserve
        // its space
        mNavIconContainer.setVisibility(navButtonMode != NavButtonMode.DISABLED ? VISIBLE : GONE);
        mNavIconContainer.setOnClickListener(
                navButtonMode != NavButtonMode.DISABLED ? backClickListener : null);
        mNavIconContainer.setClickable(navButtonMode != NavButtonMode.DISABLED);
        mNavIconContainer.setContentDescription(navButtonMode != NavButtonMode.DISABLED
                ? getContext().getString(R.string.car_ui_toolbar_nav_icon_content_description)
                : null);
        mNavIconSpacer.setVisibility(hasTabs && !isSearching ? VISIBLE : GONE);

        // Show the title if we're in the subpage state, or in the home state with no tabs or tabs
        // on the second row
        mSubtitle.setVisibility(
                TextUtils.isEmpty(getSubtitle()) ? GONE : VISIBLE);

        mTabLayout.setVisibility(hasTabs && !isSearching ? VISIBLE : GONE);

        if (mSearchView != null) {
            if (isSearching) {
                mSearchView.setPlainText(searchMode == SearchMode.EDIT);
                mSearchView.setVisibility(VISIBLE);
            } else {
                mSearchView.setVisibility(GONE);
            }
        }

        boolean showButtons = !isSearching || mShowMenuItemsWhileSearching;
        mMenuItemsContainer.setVisibility(showButtons ? VISIBLE : GONE);
        mOverflowButton.setVisible(showButtons && countVisibleOverflowItems() > 0);
    }

    private void inflateSearchView() {
        SearchView searchView = new SearchView(mPluginContext);
        searchView.setHint(mSearchHint);
        searchView.setIcon(mSearchIcon);
        searchView.setSearchQuery(mSearchQuery);
        searchView.setSearchListeners(
                mDeprecatedSearchListeners, mOnSearchListeners);
        searchView.setSearchCompletedListeners(
                mDeprecatedSearchCompletedListeners, mOnSearchCompletedListeners);
        searchView.setVisibility(GONE);

        FrameLayout.LayoutParams layoutParams = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT);
        mSearchViewContainer.addView(searchView, layoutParams);

        searchView.setSearchConfig(mSearchConfigForWidescreen);

        mSearchView = searchView;

        // These consumers should only be set once search view has been inflated
        if (mSearchTextViewConsumer != null) {
            mSearchView.setSearchTextViewConsumer(
                    (TextView tv) -> mSearchTextViewConsumer.accept(tv));
        }
        if (mOnPrivateImeCommandListener != null) {
            mSearchView.setOnPrivateImeCommandListener(
                    (String s, Bundle b) -> mOnPrivateImeCommandListener.accept(s, b));
        }
    }

    /**
     * Add a view within a container that will animate with the wide screen IME to display search
     * results.
     *
     * <p>Note: Apps can only call this method if the package name is allowed via OEM to render
     * their view.  To check if the application have the permission to do so or not first call
     * {@link SearchCapabilities#canShowSearchResultsView()}. If the app is not allowed this method
     * will throw an {@link IllegalStateException}
     *
     * @param view to be added in the container.
     */
    @Override
    public void setSearchResultsView(View view) {
        if (!getSearchCapabilities().canShowSearchResultsView()) {
            throw new IllegalStateException(
                    "not allowed to add view to wide screen IME, package name: "
                            + getContext().getPackageName());
        }

        setSearchConfig(mSearchConfigBuilder.setSearchResultsView(view).build());
    }

    @Override
    public void setSearchResultsInputViewIcon(@NonNull Drawable drawable) {
        setSearchConfig(mSearchConfigBuilder.setSearchResultsInputViewIcon(drawable).build());
    }

    /**
     * Sets list of search item {@link CarUiListItem} to be displayed in the IMS template. This
     * method should be called when system is running in a wide screen mode. Apps can check that by
     * using {@link SearchCapabilities#canShowSearchResultItems()} Else, this method will throw an
     * {@link IllegalStateException}
     */
    @Override
    public void setSearchResultItems(List<? extends CarUiImeSearchListItem> searchItems) {
        if (!getSearchCapabilities().canShowSearchResultItems()) {
            throw new IllegalStateException(
                    "system not in wide screen mode, not allowed to set search result items ");
        }

        setSearchConfig(mSearchConfigBuilder.setSearchResultItems(searchItems).build());
    }

    @Override
    public void setSearchConfig(SearchConfig searchConfig) {
        mSearchConfigForWidescreen = searchConfig;
        if (mSearchView != null) {
            mSearchView.setSearchConfig(mSearchConfigForWidescreen);
        }
    }

    @Override
    public SearchCapabilities getSearchCapabilities() {
        return SearchWidescreenController.getSearchCapabilities(mContext);
    }

    @Override
    public boolean canShowSearchResultItems() {
        return getSearchCapabilities().canShowSearchResultItems();
    }

    @Override
    public boolean canShowSearchResultsView() {
        return getSearchCapabilities().canShowSearchResultsView();
    }

    /**
     * Gets the current {@link Toolbar.State} of the toolbar.
     */
    @Override
    public Toolbar.State getState() {
        return mState;
    }

    /**
     * Returns whether or not the state of the toolbar was previously set.
     */
    @Override
    public boolean isStateSet() {
        return mStateSet;
    }

    /**
     * Registers a new {@link Toolbar.OnTabSelectedListener} to the list of listeners.
     */
    @Override
    public void registerOnTabSelectedListener(Toolbar.OnTabSelectedListener listener) {
        mOnTabSelectedListeners.add(listener);
    }

    /**
     * Unregisters an existing {@link Toolbar.OnTabSelectedListener} from the list of listeners.
     */
    @Override
    public boolean unregisterOnTabSelectedListener(Toolbar.OnTabSelectedListener listener) {
        return mOnTabSelectedListeners.remove(listener);
    }

    /**
     * Registers a new {@link Toolbar.OnSearchListener} to the list of listeners.
     */
    @Override
    public void registerOnSearchListener(Toolbar.OnSearchListener listener) {
        mDeprecatedSearchListeners.add(listener);
    }

    /**
     * Unregisters an existing {@link Toolbar.OnSearchListener} from the list of listeners.
     */
    @Override
    public boolean unregisterOnSearchListener(Toolbar.OnSearchListener listener) {
        return mDeprecatedSearchListeners.remove(listener);
    }

    @Override
    public void registerSearchListener(Consumer<String> listener) {
        mOnSearchListeners.add(listener);
    }

    @Override
    public boolean unregisterSearchListener(Consumer<String> listener) {
        return mOnSearchListeners.remove(listener);
    }

    /**
     * Registers a new {@link Toolbar.OnSearchCompletedListener} to the list of listeners.
     */
    @Override
    public void registerOnSearchCompletedListener(Toolbar.OnSearchCompletedListener listener) {
        mDeprecatedSearchCompletedListeners.add(listener);
    }

    /**
     * Unregisters an existing {@link Toolbar.OnSearchCompletedListener} from the list of
     * listeners.
     */
    @Override
    public boolean unregisterOnSearchCompletedListener(Toolbar.OnSearchCompletedListener listener) {
        return mDeprecatedSearchCompletedListeners.remove(listener);
    }

    @Override
    public void registerSearchCompletedListener(Runnable listener) {
        mOnSearchCompletedListeners.add(listener);
    }

    @Override
    public boolean unregisterSearchCompletedListener(Runnable listener) {
        return mOnSearchCompletedListeners.remove(listener);
    }

    /**
     * Registers a new {@link Toolbar.OnBackListener} to the list of listeners.
     */
    @Override
    public void registerOnBackListener(Toolbar.OnBackListener listener) {
        mDeprecatedBackListeners.add(listener);
    }

    /**
     * Unregisters an existing {@link Toolbar.OnBackListener} from the list of listeners.
     */
    @Override
    public boolean unregisterOnBackListener(Toolbar.OnBackListener listener) {
        return mDeprecatedBackListeners.remove(listener);
    }

    @Override
    public void registerBackListener(Supplier<Boolean> listener) {
        mBackListeners.add(listener);
    }

    @Override
    public boolean unregisterBackListener(Supplier<Boolean> listener) {
        return mBackListeners.remove(listener);
    }

    /**
     * Returns the progress bar.
     */
    @Override
    public ProgressBarController getProgressBar() {
        return mProgressBar;
    }

    /**
     * Returns a ImeSearchInterfaceOEMV2 implementation.
     */
    public ImeSearchInterfaceOEMV2 getImeSearchInterface() {
        return new ImeSearchInterfaceOEMV2() {
            @Override
            public void setSearchTextViewConsumer(
                    @Nullable com.android.car.ui.plugin.oemapis.Consumer<TextView> consumer) {
                mSearchTextViewConsumer = (TextView tv) -> consumer.accept(tv);
            }

            @Override
            public void setOnPrivateImeCommandListener(@Nullable
                    com.android.car.ui.plugin.oemapis.BiConsumer<String, Bundle> biConsumer) {
                mOnPrivateImeCommandListener = (String s, Bundle b) -> biConsumer.accept(s, b);
            }
        };
    }
}
