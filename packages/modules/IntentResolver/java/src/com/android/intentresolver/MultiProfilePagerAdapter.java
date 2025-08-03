/*
 * Copyright (C) 2019 The Android Open Source Project
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
package com.android.intentresolver;

import android.os.Trace;
import android.os.UserHandle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.viewpager.widget.PagerAdapter;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.emptystate.EmptyStateUiHelper;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.HashSet;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.function.Supplier;

/**
 * Skeletal {@link PagerAdapter} implementation for a UI with per-profile tabs (as in Sharesheet).
 *
 * TODO: attempt to further restrict visibility/improve encapsulation in the methods we expose.
 * TODO: deprecate and audit/fix usages of any methods that refer to the "active" or "inactive"
 * adapters; these were marked {@link VisibleForTesting} and their usage seems like an accident
 * waiting to happen since clients seem to make assumptions about which adapter will be "active" in
 * a particular context, and more explicit APIs would make sure those were valid.
 * TODO: consider renaming legacy methods (e.g. why do we know it's a "list", not just a "page"?)
 *
 * @param <PageViewT> the type of the widget that represents the contents of a page in this adapter
 * @param <SinglePageAdapterT> the type of a "root" adapter class to be instantiated and included in
 * the per-profile records.
 * @param <ListAdapterT> the concrete type of a {@link ResolverListAdapter} implementation to
 * control the contents of a given per-profile list. This is provided for convenience, since it must
 * be possible to get the list adapter from the page adapter via our {@link mListAdapterExtractor}.
 *
 * TODO: this is part of an in-progress refactor to merge with `GenericMultiProfilePagerAdapter`.
 * As originally noted there, we've reduced explicit references to the `ResolverListAdapter` base
 * type and may be able to drop the type constraint.
 */
public class MultiProfilePagerAdapter<
        PageViewT extends ViewGroup,
        SinglePageAdapterT,
        ListAdapterT extends ResolverListAdapter> extends PagerAdapter {

    /**
     * Delegate to set up a given adapter and page view to be used together.
     * @param <PageViewT> (as in {@link MultiProfilePagerAdapter}).
     * @param <SinglePageAdapterT> (as in {@link MultiProfilePagerAdapter}).
     */
    public interface AdapterBinder<PageViewT, SinglePageAdapterT> {
        /**
         * The given {@code view} will be associated with the given {@code adapter}. Do any work
         * necessary to configure them compatibly, introduce them to each other, etc.
         */
        void bind(PageViewT view, SinglePageAdapterT adapter);
    }

    public static final int PROFILE_PERSONAL = 0;
    public static final int PROFILE_WORK = 1;

    @IntDef({PROFILE_PERSONAL, PROFILE_WORK})
    public @interface Profile {}

    private final Function<SinglePageAdapterT, ListAdapterT> mListAdapterExtractor;
    private final AdapterBinder<PageViewT, SinglePageAdapterT> mAdapterBinder;
    private final Supplier<ViewGroup> mPageViewInflater;
    private final Supplier<Optional<Integer>> mContainerBottomPaddingOverrideSupplier;

    private final ImmutableList<ProfileDescriptor<PageViewT, SinglePageAdapterT>> mItems;

    private final EmptyStateProvider mEmptyStateProvider;
    private final UserHandle mWorkProfileUserHandle;
    private final UserHandle mCloneProfileUserHandle;
    private final Supplier<Boolean> mWorkProfileQuietModeChecker;  // True when work is quiet.

    private Set<Integer> mLoadedPages;
    private int mCurrentPage;
    private OnProfileSelectedListener mOnProfileSelectedListener;

    protected MultiProfilePagerAdapter(
            Function<SinglePageAdapterT, ListAdapterT> listAdapterExtractor,
            AdapterBinder<PageViewT, SinglePageAdapterT> adapterBinder,
            ImmutableList<SinglePageAdapterT> adapters,
            EmptyStateProvider emptyStateProvider,
            Supplier<Boolean> workProfileQuietModeChecker,
            @Profile int defaultProfile,
            UserHandle workProfileUserHandle,
            UserHandle cloneProfileUserHandle,
            Supplier<ViewGroup> pageViewInflater,
            Supplier<Optional<Integer>> containerBottomPaddingOverrideSupplier) {
        mCurrentPage = defaultProfile;
        mLoadedPages = new HashSet<>();
        mWorkProfileUserHandle = workProfileUserHandle;
        mCloneProfileUserHandle = cloneProfileUserHandle;
        mEmptyStateProvider = emptyStateProvider;
        mWorkProfileQuietModeChecker = workProfileQuietModeChecker;

        mListAdapterExtractor = listAdapterExtractor;
        mAdapterBinder = adapterBinder;
        mPageViewInflater = pageViewInflater;
        mContainerBottomPaddingOverrideSupplier = containerBottomPaddingOverrideSupplier;

        ImmutableList.Builder<ProfileDescriptor<PageViewT, SinglePageAdapterT>> items =
                new ImmutableList.Builder<>();
        for (SinglePageAdapterT adapter : adapters) {
            items.add(createProfileDescriptor(adapter));
        }
        mItems = items.build();
    }

    private ProfileDescriptor<PageViewT, SinglePageAdapterT> createProfileDescriptor(
            SinglePageAdapterT adapter) {
        return new ProfileDescriptor<>(mPageViewInflater.get(), adapter);
    }

    public void setOnProfileSelectedListener(OnProfileSelectedListener listener) {
        mOnProfileSelectedListener = listener;
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
                    mOnProfileSelectedListener.onProfileSelected(position);
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
        if (mLoadedPages.size() == 1) {
            return;
        }
        mLoadedPages.remove(1 - mCurrentPage);
    }

    @NonNull
    @Override
    public final ViewGroup instantiateItem(ViewGroup container, int position) {
        setupListAdapter(position);
        final ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(position);
        container.addView(descriptor.mRootView);
        return descriptor.mRootView;
    }

    @Override
    public void destroyItem(ViewGroup container, int position, @NonNull Object view) {
        container.removeView((View) view);
    }

    @Override
    public int getCount() {
        return getItemCount();
    }

    public int getCurrentPage() {
        return mCurrentPage;
    }

    @VisibleForTesting
    public UserHandle getCurrentUserHandle() {
        return getActiveListAdapter().getUserHandle();
    }

    @Override
    public boolean isViewFromObject(@NonNull View view, @NonNull Object object) {
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
    private ProfileDescriptor<PageViewT, SinglePageAdapterT> getItem(int pageIndex) {
        return mItems.get(pageIndex);
    }

    public ViewGroup getEmptyStateView(int pageIndex) {
        return getItem(pageIndex).getEmptyStateView();
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
        return getItem(index).mView;
    }

    /**
     * Returns the adapter of the list view for the relevant page specified by
     * <code>pageIndex</code>.
     * <p>This method is meant to be implemented with an implementation-specific return type
     * depending on the adapter type.
     */
    @VisibleForTesting
    public final SinglePageAdapterT getAdapterForIndex(int index) {
        return getItem(index).mAdapter;
    }

    /**
     * Performs view-related initialization procedures for the adapter specified
     * by <code>pageIndex</code>.
     */
    public final void setupListAdapter(int pageIndex) {
        mAdapterBinder.bind(getListViewForIndex(pageIndex), getAdapterForIndex(pageIndex));
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
        if (getPersonalListAdapter().getUserHandle().equals(userHandle)
                || userHandle.equals(getCloneUserHandle())) {
            return getPersonalListAdapter();
        } else if ((getWorkListAdapter() != null)
                && getWorkListAdapter().getUserHandle().equals(userHandle)) {
            return getWorkListAdapter();
        }
        return null;
    }

    /**
     * Returns the {@link ListAdapterT} instance of the profile that is currently visible
     * to the user.
     * <p>For example, if the user is viewing the work tab in the share sheet, this method returns
     * the work profile {@link ListAdapterT}.
     * @see #getInactiveListAdapter()
     */
    @VisibleForTesting
    public final ListAdapterT getActiveListAdapter() {
        return mListAdapterExtractor.apply(getAdapterForIndex(getCurrentPage()));
    }

    /**
     * If this is a device with a work profile, returns the {@link ListAdapterT} instance
     * of the profile that is <b><i>not</i></b> currently visible to the user. Otherwise returns
     * {@code null}.
     * <p>For example, if the user is viewing the work tab in the share sheet, this method returns
     * the personal profile {@link ListAdapterT}.
     * @see #getActiveListAdapter()
     */
    @VisibleForTesting
    @Nullable
    public final ListAdapterT getInactiveListAdapter() {
        if (getCount() < 2) {
            return null;
        }
        return mListAdapterExtractor.apply(getAdapterForIndex(1 - getCurrentPage()));
    }

    public final ListAdapterT getPersonalListAdapter() {
        return mListAdapterExtractor.apply(getAdapterForIndex(PROFILE_PERSONAL));
    }

    @Nullable
    public final ListAdapterT getWorkListAdapter() {
        if (!hasAdapterForIndex(PROFILE_WORK)) {
            return null;
        }
        return mListAdapterExtractor.apply(getAdapterForIndex(PROFILE_WORK));
    }

    public final SinglePageAdapterT getCurrentRootAdapter() {
        return getAdapterForIndex(getCurrentPage());
    }

    public final PageViewT getActiveAdapterView() {
        return getListViewForIndex(getCurrentPage());
    }

    @Nullable
    public final PageViewT getInactiveAdapterView() {
        if (getCount() < 2) {
            return null;
        }
        return getListViewForIndex(1 - getCurrentPage());
    }

    /**
     * Rebuilds the tab that is currently visible to the user.
     * <p>Returns {@code true} if rebuild has completed.
     */
    public boolean rebuildActiveTab(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildActiveTab");
        boolean result = rebuildTab(getActiveListAdapter(), doPostProcessing);
        Trace.endSection();
        return result;
    }

    /**
     * Rebuilds the tab that is not currently visible to the user, if such one exists.
     * <p>Returns {@code true} if rebuild has completed.
     */
    public boolean rebuildInactiveTab(boolean doPostProcessing) {
        Trace.beginSection("MultiProfilePagerAdapter#rebuildInactiveTab");
        if (getItemCount() == 1) {
            Trace.endSection();
            return false;
        }
        boolean result = rebuildTab(getInactiveListAdapter(), doPostProcessing);
        Trace.endSection();
        return result;
    }

    private int userHandleToPageIndex(UserHandle userHandle) {
        if (userHandle.equals(getPersonalListAdapter().getUserHandle())) {
            return PROFILE_PERSONAL;
        } else {
            return PROFILE_WORK;
        }
    }

    private boolean rebuildTab(ListAdapterT activeListAdapter, boolean doPostProcessing) {
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

    private boolean hasAdapterForIndex(int pageIndex) {
        return (pageIndex < getCount());
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
                ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                        userHandleToPageIndex(listAdapter.getUserHandle()));
                descriptor.mEmptyStateUi.showSpinner();
            });
        }

        showEmptyState(listAdapter, emptyState, clickListener);
    }

    /**
     * Class to get user id of the current process
     */
    public static class MyUserIdProvider {
        /**
         * @return user id of the current process
         */
        public int getMyUserId() {
            return UserHandle.myUserId();
        }
    }

    protected void showEmptyState(
            ListAdapterT activeListAdapter,
            EmptyState emptyState,
            View.OnClickListener buttonOnClick) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                userHandleToPageIndex(activeListAdapter.getUserHandle()));
        descriptor.mRootView.findViewById(
                com.android.internal.R.id.resolver_list).setVisibility(View.GONE);
        descriptor.mEmptyStateUi.resetViewVisibilities();

        ViewGroup emptyStateView = descriptor.getEmptyStateView();

        View container = emptyStateView.findViewById(
                com.android.internal.R.id.resolver_empty_state_container);
        setupContainerPadding(container);

        TextView titleView = emptyStateView.findViewById(
                com.android.internal.R.id.resolver_empty_state_title);
        String title = emptyState.getTitle();
        if (title != null) {
            titleView.setVisibility(View.VISIBLE);
            titleView.setText(title);
        } else {
            titleView.setVisibility(View.GONE);
        }

        TextView subtitleView = emptyStateView.findViewById(
                com.android.internal.R.id.resolver_empty_state_subtitle);
        String subtitle = emptyState.getSubtitle();
        if (subtitle != null) {
            subtitleView.setVisibility(View.VISIBLE);
            subtitleView.setText(subtitle);
        } else {
            subtitleView.setVisibility(View.GONE);
        }

        View defaultEmptyText = emptyStateView.findViewById(com.android.internal.R.id.empty);
        defaultEmptyText.setVisibility(emptyState.useDefaultEmptyView() ? View.VISIBLE : View.GONE);

        Button button = emptyStateView.findViewById(
                com.android.internal.R.id.resolver_empty_state_button);
        button.setVisibility(buttonOnClick != null ? View.VISIBLE : View.GONE);
        button.setOnClickListener(buttonOnClick);

        activeListAdapter.markTabLoaded();
    }

    /**
     * Sets up the padding of the view containing the empty state screens.
     * <p>This method is meant to be overridden so that subclasses can customize the padding.
     */
    public void setupContainerPadding(View container) {
        Optional<Integer> bottomPaddingOverride = mContainerBottomPaddingOverrideSupplier.get();
        bottomPaddingOverride.ifPresent(paddingBottom ->
                container.setPadding(
                    container.getPaddingLeft(),
                    container.getPaddingTop(),
                    container.getPaddingRight(),
                    paddingBottom));
    }

    public void showListView(ListAdapterT activeListAdapter) {
        ProfileDescriptor<PageViewT, SinglePageAdapterT> descriptor = getItem(
                userHandleToPageIndex(activeListAdapter.getUserHandle()));
        descriptor.mRootView.findViewById(
                com.android.internal.R.id.resolver_list).setVisibility(View.VISIBLE);
        descriptor.mEmptyStateUi.hide();
    }

    public boolean shouldShowEmptyStateScreen(ListAdapterT listAdapter) {
        int count = listAdapter.getUnfilteredCount();
        return (count == 0 && listAdapter.getPlaceholderCount() == 0)
                || (listAdapter.getUserHandle().equals(mWorkProfileUserHandle)
                    && mWorkProfileQuietModeChecker.get());
    }

    // TODO: `ChooserActivity` also has a per-profile record type. Maybe the "multi-profile pager"
    // should be the owner of all per-profile data (especially now that the API is generic)?
    private static class ProfileDescriptor<PageViewT, SinglePageAdapterT> {
        final ViewGroup mRootView;
        final EmptyStateUiHelper mEmptyStateUi;

        // TODO: post-refactoring, we may not need to retain these ivars directly (since they may
        // be encapsulated within the `EmptyStateUiHelper`?).
        private final ViewGroup mEmptyStateView;

        private final SinglePageAdapterT mAdapter;
        private final PageViewT mView;

        ProfileDescriptor(ViewGroup rootView, SinglePageAdapterT adapter) {
            mRootView = rootView;
            mAdapter = adapter;
            mEmptyStateView = rootView.findViewById(com.android.internal.R.id.resolver_empty_state);
            mView = (PageViewT) rootView.findViewById(com.android.internal.R.id.resolver_list);
            mEmptyStateUi = new EmptyStateUiHelper(rootView);
        }

        protected ViewGroup getEmptyStateView() {
            return mEmptyStateView;
        }
    }

    /** Listener interface for changes between the per-profile UI tabs. */
    public interface OnProfileSelectedListener {
        /**
         * Callback for when the user changes the active tab from personal to work or vice versa.
         * <p>This callback is only called when the intent resolver or share sheet shows
         * the work and personal profiles.
         * @param profileIndex {@link #PROFILE_PERSONAL} if the personal profile was selected or
         * {@link #PROFILE_WORK} if the work profile was selected.
         */
        void onProfileSelected(int profileIndex);


        /**
         * Callback for when the scroll state changes. Useful for discovering when the user begins
         * dragging, when the pager is automatically settling to the current page, or when it is
         * fully stopped/idle.
         * @param state {@link ViewPager#SCROLL_STATE_IDLE}, {@link ViewPager#SCROLL_STATE_DRAGGING}
         *              or {@link ViewPager#SCROLL_STATE_SETTLING}
         * @see ViewPager.OnPageChangeListener#onPageScrollStateChanged
         */
        void onProfilePageStateChanged(int state);
    }

    /**
     * Listener for when the user switches on the work profile from the work tab.
     */
    public interface OnSwitchOnWorkSelectedListener {
        /**
         * Callback for when the user switches on the work profile from the work tab.
         */
        void onSwitchOnWorkSelected();
    }
}
