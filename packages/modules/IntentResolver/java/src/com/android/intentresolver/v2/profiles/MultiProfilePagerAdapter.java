/*
 * Copyright (C) 2024 The Android Open Source Project
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
package com.android.intentresolver.v2.profiles;

import android.annotation.IntDef;
import android.annotation.Nullable;
import android.os.Trace;
import android.os.UserHandle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TabHost;
import android.widget.TextView;

import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Skeletal {@link PagerAdapter} implementation for a UI with per-profile tabs (as in Sharesheet).
 *
 * @param <PageViewT> the type of the widget that represents the contents of a page in this adapter
 * @param <SinglePageAdapterT> the type of a "root" adapter class to be instantiated and included in
 * the per-profile records.
 * @param <ListAdapterT> the concrete type of a {@link ResolverListAdapter} implementation to
 * control the contents of a given per-profile list. This is provided for convenience, since it must
 * be possible to get the list adapter from the page adapter via our
 * <code>mListAdapterExtractor</code>.
 */
public class MultiProfilePagerAdapter<
        PageViewT extends ViewGroup,
        SinglePageAdapterT,
        ListAdapterT extends ResolverListAdapter> extends PagerAdapter {

    public static final int PROFILE_PERSONAL = 0;
    public static final int PROFILE_WORK = 1;

    @IntDef({PROFILE_PERSONAL, PROFILE_WORK})
    public @interface ProfileType {}

    private final Function<SinglePageAdapterT, ListAdapterT> mListAdapterExtractor;
    private final AdapterBinder<PageViewT, SinglePageAdapterT> mAdapterBinder;
    private final Supplier<ViewGroup> mPageViewInflater;

    private final ImmutableList<ProfileDescriptor<PageViewT, SinglePageAdapterT>> mItems;

    private final EmptyStateProvider mEmptyStateProvider;
    private final UserHandle mWorkProfileUserHandle;
    private final UserHandle mCloneProfileUserHandle;
    private final Supplier<Boolean> mWorkProfileQuietModeChecker;  // True when work is quiet.

    private final Set<Integer> mLoadedPages;
    private int mCurrentPage;
    private OnProfileSelectedListener mOnProfileSelectedListener;

    protected MultiProfilePagerAdapter(
            Function<SinglePageAdapterT, ListAdapterT> listAdapterExtractor,
            AdapterBinder<PageViewT, SinglePageAdapterT> adapterBinder,
            ImmutableList<TabConfig<SinglePageAdapterT>> tabs,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @ProfileType int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            Supplier<ViewGroup> pageViewInflater,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        mLoadedPages = new HashSet<>();
        mWorkProfileUserHandle = workProfileUserHandle;
        mCloneProfileUserHandle = cloneProfileUserHandle;
        mEmptyStateProvider = emptyStateProvider;
        mWorkProfileQuietModeChecker = workProfileQuietModeChecker;

        mListAdapterExtractor = listAdapterExtractor;
        mAdapterBinder = adapterBinder;
        mPageViewInflater = pageViewInflater;

        ImmutableList.Builder<ProfileDescriptor<PageViewT, SinglePageAdapterT>> items =
                new ImmutableList.Builder<>();
        for (TabConfig<SinglePageAdapterT> tab : tabs) {
            // TODO: consider representing tabConfig in a different data structure that can ensure
            // uniqueness of their profile assignments (while still respecting the client's
            // requested tab order).
            items.add(
                    createProfileDescriptor(
                            tab.mProfile,
                            tab.mTabLabel,
                            tab.mTabAccessibilityLabel,
                            tab.mTabTag,
                            tab.mPageAdapter,
                            containerBottomPaddingOverrideSupplier));
        }
        mItems = items.build();

        mCurrentPage =
                hasPageForProfile(defaultProfile) ? getPageNumberForProfile(defaultProfile) : 0;
    }

    private ProfileDescriptor<PageViewT, SinglePageAdapterT> createProfileDescriptor(
            @ProfileType int profile,
            String tabLabel,
            String tabAccessibilityLabel,
            String tabTag,
            SinglePageAdapterT adapter,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        return new ProfileDescriptor<>(
                profile,
                tabLabel,
                tabAccessibilityLabel,
                tabTag,
                mPageViewInflater.get(),
                adapter,
                containerBottomPaddingOverrideSupplier);
    }

    private boolean hasPageForIndex(int pageIndex) {
        return (pageIndex >= 0) && (pageIndex < getCount());
    }

    public final boolean hasPageForProfile(@ProfileType int profile) {
        return hasPageForIndex(getPageNumberForProfile(profile));
    }

    private @ProfileType int getProfileForPageNumber(int position) {
        if (hasPageForIndex(position)) {
            return mItems.get(position).mProfile;
        }
        return -1;
    }

    public int getPageNumberForProfile(@ProfileType int profile) {
        for (int i = 0; i < mItems.size(); ++i) {
            if (profile == mItems.get(i).mProfile) {
                return i;
            }
        }
        return -1;
    }

    private ListAdapterT getListAdapterForPageNumber(int pageNumber) {
        SinglePageAdapterT pageAdapter = getPageAdapterForIndex(pageNumber);
        if (pageAdapter == null) {
            return null;
        }
        return mListAdapterExtractor.apply(pageAdapter);
    }

    private @ProfileType int getProfileForUserHandle(UserHandle userHandle) {
        if (userHandle.equals(getCloneUserHandle())) {
            // TODO: can we push this special case elsewhere -- e.g., when we check against each
            // list adapter's user handle in the loop below, could we instead ask the list adapter
            // whether it "represents" the queried user handle, and have the personal list adapter
            // return true because it knows it's also associated with the clone profile? Or if we
            // don't want to make modifications to the list adapter, maybe we could at least specify
            // it in our per-page configuration data that we use to build our tabs/pages, and then
            // maintain the relevant bookkeeping in our own ProfileDescriptor?
            return PROFILE_PERSONAL;
        }
        for (int i = 0; i < mItems.size(); ++i) {
            ListAdapterT listAdapter = getListAdapterForPageNumber(i);
            if (listAdapter.getUserHandle().equals(userHandle)) {
                return mItems.get(i).mProfile;
            }
        }
        return -1;
    }

    private int getPageNumberForUserHandle(UserHandle userHandle) {
        return getPageNumberForProfile(getProfileForUserHandle(userHandle));
    }

    /**
     * Returns the {@link ListAdapterT} instance of the profile that represents
     * <code>userHandle</code>. If there is no such adapter for the specified
     * <code>userHandle</code>, returns {@code null}.
     * <p>For example, if there is a work profile on the device with user id 10, calling this method
     * with <code>UserHandle.of(10)</code> returns the work profile {@link ListAdapterT}.
     */
    @Nullable
    public final ListAdapterT getListAdapterForUserHandle(UserHandle userHandle) {
        return getListAdapterForPageNumber(getPageNumberForUserHandle(userHandle));
    }

    @Nullable
    private ProfileDescriptor<PageViewT, SinglePageAdapterT> getDescriptorForUserHandle(
            UserHandle userHandle) {
        return getItem(getPageNumberForUserHandle(userHandle));
    }

    private int getPageNumberForTabTag(String tag) {
        for (int i = 0; i < mItems.size(); ++i) {
            if (Objects.equals(mItems.get(i).mTabTag, tag)) {
                return i;
            }
        }
        return -1;
    }

    private void updateActiveTabStyle(TabHost tabHost) {
        int currentTab = tabHost.getCurrentTab();

        for (int pageNumber = 0; pageNumber < getItemCount(); ++pageNumber) {
            // TODO: can we avoid this downcast by pushing our knowledge of the intended view type
            // somewhere else?
            TextView tabText = (TextView) tabHost.getTabWidget().getChildAt(pageNumber);
            tabText.setSelected(currentTab == pageNumber);
        }
    }

    public void setupProfileTabs(
            LayoutInflater layoutInflater,
            TabHost tabHost,
            ViewPager viewPager,
            int tabButtonLayoutResId,
            int tabPageContentViewId,
            Runnable onTabChangeListener,
            OnProfileSelectedListener clientOnProfileSelectedListener) {
        tabHost.setup();
        viewPager.setSaveEnabled(false);

        for (int pageNumber = 0; pageNumber < getItemCount(); ++pageNumber) {
            ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = mItems.get(pageNumber);
            Button profileButton = (Button) layoutInflater.inflate(
                    tabButtonLayoutResId, tabHost.getTabWidget(), false);
            profileButton.setText(descriptor.mTabLabel);
            profileButton.setContentDescription(descriptor.mTabAccessibilityLabel);

            TabHost.TabSpec profileTabSpec = tabHost.newTabSpec(descriptor.mTabTag)
                    .setContent(tabPageContentViewId)
                    .setIndicator(profileButton);
            tabHost.addTab(profileTabSpec);
        }

        tabHost.getTabWidget().setVisibility(View.VISIBLE);

        updateActiveTabStyle(tabHost);

        tabHost.setOnTabChangedListener(tabTag -> {
            updateActiveTabStyle(tabHost);

            int pageNumber = getPageNumberForTabTag(tabTag);
            if (pageNumber >= 0) {
                viewPager.setCurrentItem(pageNumber);
            }
            onTabChangeListener.run();
        });

        viewPager.setVisibility(View.VISIBLE);
        tabHost.setCurrentTab(getCurrentPage());
        mOnProfileSelectedListener =
                new OnProfileSelectedListener() {
                    @Override
                    public void onProfilePageSelected(@ProfileType int profileId, int pageNumber) {
                        tabHost.setCurrentTab(pageNumber);
                        clientOnProfileSelectedListener.onProfilePageSelected(
                                profileId, pageNumber);
                    }

                    @Override
                    public void onProfilePageStateChanged(int state) {
                        clientOnProfileSelectedListener.onProfilePageStateChanged(state);
                    }
                };
    }

    /**
     * Sets this instance of this class as {@link ViewPager}'s {@link PagerAdapter} and sets
     * an {@link ViewPager.OnPageChangeListener} where it keeps track of the currently displayed
     * page and rebuilds the list.
     */
    public void setupViewPager(ViewPager viewPager) {
        viewPager.setOnPageChangeListener(new ViewPager.SimpleOnPageChangeListener() {
            @Override
            public void onPageSelected(int position) {
                mCurrentPage = position;
                if (!mLoadedPages.contains(position)) {
                    rebuildActiveTab(true);
                    mLoadedPages.add(position);
                }
                if (mOnProfileSelectedListener != null) {
                    mOnProfileSelectedListener.onProfilePageSelected(
                            getProfileForPageNumber(position), position);
                }
            }

            @Override
            public void onPageScrollStateChanged(int state) {
                if (mOnProfileSelectedListener != null) {
                    mOnProfileSelectedListener.onProfilePageStateChanged(state);
                }
            }
        });
        viewPager.setAdapter(this);
        viewPager.setCurrentItem(mCurrentPage);
        mLoadedPages.add(mCurrentPage);
    }

    public void clearInactiveProfileCache() {
        forEachInactivePage(pageNumber -> mLoadedPages.remove(pageNumber));
    }

    @Override
    public final ViewGroup instantiateItem(ViewGroup container, int position) {
        setupListAdapter(position);
        final ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(position);
        container.addView(descriptor.mRootView);
        return descriptor.mRootView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, Object view) {
        container.removeView((View) view);
    }

    @Override
    public int getCount() {
        return getItemCount();
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    public final @ProfileType int getActiveProfile() {
        return getProfileForPageNumber(getCurrentPage());
    }

    @VisibleForTesting
    public UserHandle getCurrentUserHandle() {
        return getActiveListAdapter().getUserHandle();
    }

    @Override
    public boolean isViewFromObject(View view, Object object) {
        return view == object;
    }

    @Override
    public CharSequence getPageTitle(int position) {
        return null;
    }

    public UserHandle getCloneUserHandle() {
        return mCloneProfileUserHandle;
    }

    /**
     * Returns the {@link ProfileDescriptor} relevant to the given <code>pageIndex</code>.
     * <ul>
     * <li>For a device with only one user, <code>pageIndex</code> value of
     * <code>0</code> would return the personal profile {@link ProfileDescriptor}.</li>
     * <li>For a device with a work profile, <code>pageIndex</code> value of <code>0</code> would
     * return the personal profile {@link ProfileDescriptor}, and <code>pageIndex</code> value of
     * <code>1</code> would return the work profile {@link ProfileDescriptor}.</li>
     * </ul>
     */
    @Nullable
    private ProfileDescriptor<PageViewT, SinglePageAdapterT> getItem(int pageIndex) {
        if (!hasPageForIndex(pageIndex)) {
            return null;
        }
        return mItems.get(pageIndex);
    }

    private ViewGroup getEmptyStateView(int pageIndex) {
        return getItem(pageIndex).getEmptyStateView();
    }

    public ViewGroup getActiveEmptyStateView() {
        return getEmptyStateView(getCurrentPage());
    }

    /**
     * Returns the number of {@link ProfileDescriptor} objects.
     * <p>For a normal consumer device with only one user returns <code>1</code>.
     * <p>For a device with a work profile returns <code>2</code>.
     */
    public final int getItemCount() {
        return mItems.size();
    }

    public final PageViewT getListViewForIndex(int index) {
        return getItem(index).getView();
    }

    /**
     * Returns the adapter of the list view for the relevant page specified by
     * <code>pageIndex</code>.
     * <p>This method is meant to be implemented with an implementation-specific return type
     * depending on the adapter type.
     */
    @VisibleForTesting
    public final SinglePageAdapterT getPageAdapterForIndex(int index) {
        if (!hasPageForIndex(index)) {
            return null;
        }
        return getItem(index).getAdapter();
    }

    /**
     * Performs view-related initialization procedures for the adapter specified
     * by <code>pageIndex</code>.
     */
    public final void setupListAdapter(int pageIndex) {
        mAdapterBinder.bind(getListViewForIndex(pageIndex), getPageAdapterForIndex(pageIndex));
    }

    /**
     * Returns the {@link ListAdapterT} instance of the profile that is currently visible
     * to the user.
     * <p>For example, if the user is viewing the work tab in the share sheet, this method returns
     * the work profile {@link ListAdapterT}.
     */
    @VisibleForTesting
    public final ListAdapterT getActiveListAdapter() {
        return getListAdapterForPageNumber(getCurrentPage());
    }

    public final ListAdapterT getPersonalListAdapter() {
        return getListAdapterForPageNumber(getPageNumberForProfile(PROFILE_PERSONAL));
    }

    @Nullable
    public final ListAdapterT getWorkListAdapter() {
        if (!hasPageForProfile(PROFILE_WORK)) {
            return null;
        }
        return getListAdapterForPageNumber(getPageNumberForProfile(PROFILE_WORK));
    }

    public final SinglePageAdapterT getCurrentRootAdapter() {
        return getPageAdapterForIndex(getCurrentPage());
    }

    public final PageViewT getActiveAdapterView() {
        return getListViewForIndex(getCurrentPage());
    }

    private boolean anyAdapterHasItems() {
        for (int i = 0; i < mItems.size(); ++i) {
            ListAdapterT listAdapter = getListAdapterForPageNumber(i);
            if (listAdapter.getCount() > 0) {
                return true;
            }
        }
        return false;
    }

    public void refreshPackagesInAllTabs() {
        // TODO: it's unclear if this legacy logic really requires the active tab to be rebuilt
        // first, or if we could just iterate over the tabs in arbitrary order.
        getActiveListAdapter().handlePackagesChanged();
        forEachInactivePage(page -> getListAdapterForPageNumber(page).handlePackagesChanged());
    }

    /**
     * Notify that there has been a package change which could potentially modify the set of targets
     * that should be shown in the specified {@code listAdapter}. This <em>may</em> result in
     * "rebuilding" the target list for that adapter.
     *
     * @param listAdapter an adapter that may need to be updated after the package-change event.
     * @param waitingToEnableWorkProfile whether we've turned on the work profile, but haven't yet
     * seen an {@code ACTION_USER_UNLOCKED} broadcast. In this case we skip the rebuild of any
     * work-profile adapter because we wouldn't expect meaningful results -- but another rebuild
     * will be prompted when we eventually get the broadcast.
     *
     * @return whether we're able to proceed with a Sharesheet session after processing this
     * package-change event. If false, we were able to rebuild the targets but determined that there
     * aren't any we could present in the UI without the app looking broken, so we should just quit.
     */
    public boolean onHandlePackagesChanged(
            ListAdapterT listAdapter, boolean waitingToEnableWorkProfile) {
        if (listAdapter == getActiveListAdapter()) {
            if (listAdapter.getUserHandle().equals(mWorkProfileUserHandle)
                    && waitingToEnableWorkProfile) {
                // We have just turned on the work profile and entered the passcode to start it,
                // now we are waiting to receive the ACTION_USER_UNLOCKED broadcast. There is no
                // point in reloading the list now, since the work profile user is still turning on.
                return true;
            }

            boolean listRebuilt = rebuildActiveTab(true);
            if (listRebuilt) {
                listAdapter.notifyDataSetChanged();
            }

            // TODO: shouldn't we check that the inactive tabs are built before declaring that we
            // have to quit for lack of items?
            return anyAdapterHasItems();
        } else {
            clearInactiveProfileCache();
            return true;
        }
    }

    /**
     * Fully-rebuild the active tab and, if specified, partially-rebuild any other inactive tabs.
     */
    public boolean rebuildTabs(boolean includePartialRebuildOfInactiveTabs) {
        // TODO: we may be able to determine `includePartialRebuildOfInactiveTabs` ourselves as
        // a function of our own instance state. OTOH the purpose of this "partial rebuild" is to
        // be able to evaluate the intermediate state of one particular profile tab (i.e. work
        // profile) that may not generalize well when we have other "inactive tabs." I.e., either we
        // rebuild *all* the inactive tabs just to evaluate some auto-launch conditions that only
        // depend on personal and/or work tabs, or we have to explicitly specify the ones we care
        // about. It's not the pager-adapter's business to know "which ones we care about," so maybe
        // they should be rebuilt lazily when-and-if it comes up (e.g. during the evaluation of
        // autolaunch conditions).
        boolean rebuildCompleted = rebuildActiveTab(true) || getActiveListAdapter().isTabLoaded();
        if (includePartialRebuildOfInactiveTabs) {
            // Per legacy logic, avoid short-circuiting (TODO: why? possibly so that we *start*
            // loading the inactive tabs even if we're still waiting on the active tab to finish?).
            boolean completedRebuildingInactiveTabs = rebuildInactiveTabs(false);
            rebuildCompleted = rebuildCompleted && completedRebuildingInactiveTabs;
        }
        return rebuildCompleted;
    }

    /**
     * Rebuilds the tab that is currently visible to the user.
     * <p>Returns {@code true} if rebuild has completed.
     */
    public final boolean rebuildActiveTab(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildActiveTab");
        boolean result = rebuildTab(getActiveListAdapter(), doPostProcessing);
        Trace.endSection();
        return result;
    }

    /**
     * Rebuilds any tabs that are not currently visible to the user.
     * <p>Returns {@code true} if rebuild has completed in all inactive tabs.
     */
    private boolean rebuildInactiveTabs(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildInactiveTab");
        AtomicBoolean allRebuildsComplete = new AtomicBoolean(true);
        forEachInactivePage(pageNumber -> {
            // Evaluate the rebuild for every inactive page, even if we've already seen some adapter
            // return an "incomplete" status (i.e., even if `allRebuildsComplete` is already false)
            // and so we already know we'll end up returning false for the batch.
            // TODO: any particular reason the per-page legacy logic was set up in this order, or
            // could we possibly short-circuit the rebuild if the tab is already "loaded"?
            ListAdapterT inactiveAdapter = getListAdapterForPageNumber(pageNumber);
            boolean rebuildInactivePageCompleted =
                    rebuildTab(inactiveAdapter, doPostProcessing) || inactiveAdapter.isTabLoaded();
            if (!rebuildInactivePageCompleted) {
                allRebuildsComplete.set(false);
            }
        });
        Trace.endSection();
        return allRebuildsComplete.get();
    }

    protected void forEachPage(Consumer<Integer> pageNumberHandler) {
        for (int pageNumber = 0; pageNumber < getItemCount(); ++pageNumber) {
            pageNumberHandler.accept(pageNumber);
        }
    }

    protected void forEachInactivePage(Consumer<Integer> inactivePageNumberHandler) {
        forEachPage(pageNumber -> {
            if (pageNumber != getCurrentPage()) {
                inactivePageNumberHandler.accept(pageNumber);
            }
        });
    }

    protected boolean rebuildTab(ListAdapterT activeListAdapter, boolean doPostProcessing) {
        if (shouldSkipRebuild(activeListAdapter)) {
            activeListAdapter.postListReadyRunnable(doPostProcessing, /* rebuildCompleted */ true);
            return false;
        }
        return activeListAdapter.rebuildList(doPostProcessing);
    }

    private boolean shouldSkipRebuild(ListAdapterT activeListAdapter) {
        EmptyState emptyState = mEmptyStateProvider.getEmptyState(activeListAdapter);
        return emptyState != null && emptyState.shouldSkipDataRebuild();
    }

    /**
     * The empty state screens are shown according to their priority:
     * <ol>
     * <li>(highest priority) cross-profile disabled by policy (handled in
     * {@link #rebuildTab(ListAdapterT, boolean)})</li>
     * <li>no apps available</li>
     * <li>(least priority) work is off</li>
     * </ol>
     *
     * The intention is to prevent the user from having to turn
     * the work profile on if there will not be any apps resolved
     * anyway.
     *
     * TODO: move this comment to the place where we configure our composite provider.
     */
    public void showEmptyResolverListEmptyState(ListAdapterT listAdapter) {
        final EmptyState emptyState = mEmptyStateProvider.getEmptyState(listAdapter);

        if (emptyState == null) {
            return;
        }

        emptyState.onEmptyStateShown();

        View.OnClickListener clickListener = null;

        if (emptyState.getButtonClickListener() != null) {
            clickListener = v -> emptyState.getButtonClickListener().onClick(() -> {
                ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor =
                        getDescriptorForUserHandle(listAdapter.getUserHandle());
                descriptor.mEmptyStateUi.showSpinner();
            });
        }

        showEmptyState(listAdapter, emptyState, clickListener);
    }

    private void showEmptyState(
            ListAdapterT activeListAdapter,
            EmptyState emptyState,
            View.OnClickListener buttonOnClick) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor =
                getDescriptorForUserHandle(activeListAdapter.getUserHandle());
        descriptor.mEmptyStateUi.showEmptyState(emptyState, buttonOnClick);
        activeListAdapter.markTabLoaded();
    }

    /**
     * Sets up the padding of the view containing the empty state screens for the current adapter
     * view.
     */
    protected final void setupContainerPadding() {
        getItem(getCurrentPage()).setupContainerPadding();
    }

    public void showListView(ListAdapterT activeListAdapter) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor =
                getDescriptorForUserHandle(activeListAdapter.getUserHandle());
        descriptor.mEmptyStateUi.hide();
    }

    /**
     * @return whether any "inactive" tab's adapter would show an empty-state screen in our current
     * application state.
     */
    public final boolean shouldShowEmptyStateScreenInAnyInactiveAdapter() {
        AtomicBoolean anyEmpty = new AtomicBoolean(false);
        // TODO: The "inactive" condition is legacy logic. Could we simplify and ask "any"?
        forEachInactivePage(pageNumber -> {
            if (shouldShowEmptyStateScreen(getListAdapterForPageNumber(pageNumber))) {
                anyEmpty.set(true);
            }
        });
        return anyEmpty.get();
    }

    public boolean shouldShowEmptyStateScreen(ListAdapterT listAdapter) {
        int count = listAdapter.getUnfilteredCount();
        return (count == 0 && listAdapter.getPlaceholderCount() == 0)
                || (listAdapter.getUserHandle().equals(mWorkProfileUserHandle)
                    && mWorkProfileQuietModeChecker.get());
    }

}
