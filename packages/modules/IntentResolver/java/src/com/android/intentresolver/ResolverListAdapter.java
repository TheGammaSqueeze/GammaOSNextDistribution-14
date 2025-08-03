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

import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.RemoteException;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AbsListView;
import android.widget.BaseAdapter;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.icons.LabelInfo;
import com.android.intentresolver.icons.TargetDataLoader;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class ResolverListAdapter extends BaseAdapter {
    private static final String TAG = "ResolverListAdapter";

    @Nullable  // TODO: other model for lazy computation? Or just precompute?
    private static ColorMatrixColorFilter sSuspendedMatrixColorFilter;

    protected final Context mContext;
    protected final LayoutInflater mInflater;
    protected final ResolverListCommunicator mResolverListCommunicator;
    public final ResolverListController mResolverListController;

    private final List<Intent> mIntents;
    private final Intent[] mInitialIntents;
    private final List<ResolveInfo> mBaseResolveList;
    private final PackageManager mPm;
    private final TargetDataLoader mTargetDataLoader;
    private final UserHandle mUserHandle;
    private final Intent mTargetIntent;

    private final Set<DisplayResolveInfo> mRequestedIcons = new HashSet<>();
    private final Set<DisplayResolveInfo> mRequestedLabels = new HashSet<>();
    private final Executor mBgExecutor;
    private final Executor mCallbackExecutor;
    private final AtomicBoolean mDestroyed = new AtomicBoolean();

    private ResolveInfo mLastChosen;
    private DisplayResolveInfo mOtherProfile;
    private int mPlaceholderCount;

    // This one is the list that the Adapter will actually present.
    private final List<DisplayResolveInfo> mDisplayList;
    private List<ResolvedComponentInfo> mUnfilteredResolveList;

    private int mLastChosenPosition = -1;
    private final boolean mFilterLastUsed;
    private boolean mIsTabLoaded;
    // Represents the UserSpace in which the Initial Intents should be resolved.
    private final UserHandle mInitialIntentsUserSpace;

    public ResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            ResolverListCommunicator resolverListCommunicator,
            UserHandle initialIntentsUserSpace,
            TargetDataLoader targetDataLoader) {
        this(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                initialIntentsUserSpace,
                targetDataLoader,
                AsyncTask.SERIAL_EXECUTOR,
                runnable -> context.getMainThreadHandler().post(runnable));
    }

    @VisibleForTesting
    public ResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            ResolverListCommunicator resolverListCommunicator,
            UserHandle initialIntentsUserSpace,
            TargetDataLoader targetDataLoader,
            Executor bgExecutor,
            Executor callbackExecutor) {
        mContext = context;
        mIntents = payloadIntents;
        mInitialIntents = initialIntents;
        mBaseResolveList = rList;
        mInflater = LayoutInflater.from(context);
        mPm = context.getPackageManager();
        mTargetDataLoader = targetDataLoader;
        mDisplayList = new ArrayList<>();
        mFilterLastUsed = filterLastUsed;
        mResolverListController = resolverListController;
        mUserHandle = userHandle;
        mTargetIntent = targetIntent;
        mResolverListCommunicator = resolverListCommunicator;
        mInitialIntentsUserSpace = initialIntentsUserSpace;
        mBgExecutor = bgExecutor;
        mCallbackExecutor = callbackExecutor;
    }

    protected Intent getTargetIntent() {
        return mTargetIntent;
    }

    public final DisplayResolveInfo getFirstDisplayResolveInfo() {
        return mDisplayList.get(0);
    }

    public final ImmutableList<DisplayResolveInfo> getTargetsInCurrentDisplayList() {
        return ImmutableList.copyOf(mDisplayList);
    }

    public void handlePackagesChanged() {
        mResolverListCommunicator.onHandlePackagesChanged(this);
    }

    public void setPlaceholderCount(int count) {
        mPlaceholderCount = count;
    }

    public int getPlaceholderCount() {
        return mPlaceholderCount;
    }

    @Nullable
    public DisplayResolveInfo getFilteredItem() {
        if (mFilterLastUsed && mLastChosenPosition >= 0) {
            // Not using getItem since it offsets to dodge this position for the list
            return mDisplayList.get(mLastChosenPosition);
        }
        return null;
    }

    public DisplayResolveInfo getOtherProfile() {
        return mOtherProfile;
    }

    public int getFilteredPosition() {
        if (mFilterLastUsed && mLastChosenPosition >= 0) {
            return mLastChosenPosition;
        }
        return AbsListView.INVALID_POSITION;
    }

    public boolean hasFilteredItem() {
        return mFilterLastUsed && mLastChosen != null;
    }

    public float getScore(DisplayResolveInfo target) {
        return mResolverListController.getScore(target);
    }

    /**
     * Returns the app share score of the given {@code targetInfo}.
     */
    public float getScore(TargetInfo targetInfo) {
        return mResolverListController.getScore(targetInfo);
    }

    /**
     * Updates the model about the chosen {@code targetInfo}.
     */
    public void updateModel(TargetInfo targetInfo) {
        mResolverListController.updateModel(targetInfo);
    }

    /**
     * Updates the model about Chooser Activity selection.
     */
    public void updateChooserCounts(String packageName, String action, UserHandle userHandle) {
        mResolverListController.updateChooserCounts(
                packageName, userHandle, action);
    }

    public List<ResolvedComponentInfo> getUnfilteredResolveList() {
        return mUnfilteredResolveList;
    }

    /**
     * Rebuild the list of resolvers. When rebuilding is complete, queue the {@code onPostListReady}
     * callback on the callback executor with {@code rebuildCompleted} true.
     *
     * In some cases some parts will need some asynchronous work to complete. Then this will first
     * immediately queue {@code onPostListReady} (on the callback executor) with
     * {@code rebuildCompleted} false; only when the asynchronous work completes will this then go
     * on to queue another {@code onPostListReady} callback with {@code rebuildCompleted} true.
     *
     * The {@code doPostProcessing} parameter is used to specify whether to update the UI and
     * load additional targets (e.g. direct share) after the list has been rebuilt. We may choose
     * to skip that step if we're only loading the inactive profile's resolved apps to know the
     * number of targets.
     *
     * @return Whether the list building was completed synchronously. If not, we'll queue the
     * {@code onPostListReady} callback first with {@code rebuildCompleted} false, and then again
     * with {@code rebuildCompleted} true at the end of some newly-launched asynchronous work.
     * Otherwise the callback is only queued once, with {@code rebuildCompleted} true.
     */
    public boolean rebuildList(boolean doPostProcessing) {
        Trace.beginSection("ResolverListAdapter#rebuildList");
        mDisplayList.clear();
        mIsTabLoaded = false;
        mLastChosenPosition = -1;

        List<ResolvedComponentInfo> currentResolveList = getInitialRebuiltResolveList();

        /* TODO: this seems like unnecessary extra complexity; why do we need to do this "primary"
         * (i.e. "eligibility") filtering before evaluating the "other profile" special-treatment,
         * but the "secondary" (i.e. "priority") filtering after? Are there in fact cases where the
         * eligibility conditions will filter out a result that would've otherwise gotten the "other
         * profile" treatment? Or, are there cases where the priority conditions *would* filter out
         * a result, but we *want* that result to get the "other profile" treatment, so we only
         * filter *after* evaluating the special-treatment conditions? If the answer to either is
         * "no," then the filtering steps can be consolidated. (And that also makes the "unfiltered
         * list" bookkeeping a little cleaner.)
         */
        mUnfilteredResolveList = performPrimaryResolveListFiltering(currentResolveList);

        // So far we only support a single other profile at a time.
        // The first one we see gets special treatment.
        ResolvedComponentInfo otherProfileInfo =
                getFirstNonCurrentUserResolvedComponentInfo(currentResolveList);
        updateOtherProfileTreatment(otherProfileInfo);
        if (otherProfileInfo != null) {
            currentResolveList.remove(otherProfileInfo);
            /* TODO: the previous line removed the "other profile info" item from
             * mUnfilteredResolveList *ONLY IF* that variable is an alias for the same List instance
             * as currentResolveList (i.e., if no items were filtered out as the result of the
             * earlier "primary" filtering). It seems wrong for our behavior to depend on that.
             * Should we:
             *  A. replicate the above removal to mUnfilteredResolveList (which is idempotent, so we
             *     don't even have to check whether they're aliases); or
             *  B. break the alias relationship by copying currentResolveList to a new
             *  mUnfilteredResolveList instance if necessary before removing otherProfileInfo?
             * In other words: do we *want* otherProfileInfo in the "unfiltered" results? Either
             * way, we'll need one of the changes suggested above.
             */
        }

        // If no results have yet been filtered, mUnfilteredResolveList is an alias for the same
        // List instance as currentResolveList. Then we need to make a copy to store as the
        // mUnfilteredResolveList if we go on to filter any more items. Otherwise we've already
        // copied the original unfiltered items to a separate List instance and can now filter
        // the remainder in-place without any further bookkeeping.
        boolean needsCopyOfUnfiltered = (mUnfilteredResolveList == currentResolveList);
        List<ResolvedComponentInfo> originalList = performSecondaryResolveListFiltering(
                currentResolveList, needsCopyOfUnfiltered);
        if (originalList != null) {
            // Only need the originalList value if there was a modification (otherwise it's null
            // and shouldn't overwrite mUnfilteredResolveList).
            mUnfilteredResolveList = originalList;
        }

        boolean result =
                finishRebuildingListWithFilteredResults(currentResolveList, doPostProcessing);
        Trace.endSection();
        return result;
    }

    /**
     * Get the full (unfiltered) set of {@code ResolvedComponentInfo} records for all resolvers
     * to be considered in a newly-rebuilt list. This list will be filtered and ranked before the
     * rebuild is complete.
     */
    List<ResolvedComponentInfo> getInitialRebuiltResolveList() {
        if (mBaseResolveList != null) {
            List<ResolvedComponentInfo> currentResolveList = new ArrayList<>();
            mResolverListController.addResolveListDedupe(currentResolveList,
                    mTargetIntent,
                    mBaseResolveList);
            return currentResolveList;
        } else {
            return getResolversForUser(mUserHandle);
        }
    }

    /**
     * Remove ineligible activities from {@code currentResolveList} (if non-null), in-place. More
     * broadly, filtering logic should apply in the "primary" stage if it should preclude items from
     * receiving the "other profile" special-treatment described in {@code rebuildList()}.
     *
     * @return A copy of the original {@code currentResolveList}, if any items were removed, or a
     * (possibly null) reference to the original list otherwise. (That is, this always returns a
     * list of all the unfiltered items, but if no items were filtered, it's just an alias for the
     * same list that was passed in).
     */
    @Nullable
    List<ResolvedComponentInfo> performPrimaryResolveListFiltering(
            @Nullable List<ResolvedComponentInfo> currentResolveList) {
        /* TODO: mBaseResolveList appears to be(?) some kind of configured mode. Why is it not
         * subject to filterIneligibleActivities, even though all the other logic still applies
         * (including "secondary" filtering)? (This also relates to the earlier question; do we
         * believe there's an item that would be eligible for "other profile" special treatment,
         * except we want to filter it out as ineligible... but only if we're not in
         * "mBaseResolveList mode"? */
        if ((mBaseResolveList != null) || (currentResolveList == null)) {
            return currentResolveList;
        }

        List<ResolvedComponentInfo> originalList =
                mResolverListController.filterIneligibleActivities(currentResolveList, true);
        return (originalList == null) ? currentResolveList : originalList;
    }

    /**
     * Remove low-priority activities from {@code currentResolveList} (if non-null), in place. More
     * broadly, filtering logic should apply in the "secondary" stage to prevent items from
     * appearing in the rebuilt-list results, while still considering those items for the "other
     * profile" special-treatment described in {@code rebuildList()}.
     *
     * @return the same (possibly null) List reference as {@code currentResolveList} if the list is
     * unmodified as a result of filtering; or, if some item(s) were removed, then either a copy of
     * the original {@code currentResolveList} (if {@code returnCopyOfOriginalListIfModified} is
     * true), or null (otherwise).
     */
    @Nullable
    List<ResolvedComponentInfo> performSecondaryResolveListFiltering(
            @Nullable List<ResolvedComponentInfo> currentResolveList,
            boolean returnCopyOfOriginalListIfModified) {
        if ((currentResolveList == null) || currentResolveList.isEmpty()) {
            return currentResolveList;
        }
        return mResolverListController.filterLowPriority(
                currentResolveList, returnCopyOfOriginalListIfModified);
    }

    /**
     * Update the special "other profile" UI treatment based on the components resolved for a
     * newly-built list.
     *
     * @param otherProfileInfo the first {@code ResolvedComponentInfo} specifying a
     * {@code targetUserId} other than {@code USER_CURRENT}, or null if no such component info was
     * found in the process of rebuilding the list (or if any such candidates were already removed
     * due to "primary filtering").
     */
    void updateOtherProfileTreatment(@Nullable ResolvedComponentInfo otherProfileInfo) {
        mLastChosen = null;

        if (otherProfileInfo != null) {
            mOtherProfile = makeOtherProfileDisplayResolveInfo(
                    otherProfileInfo,
                    mPm,
                    mTargetIntent,
                    mResolverListCommunicator
            );
        } else {
            mOtherProfile = null;
            try {
                mLastChosen = mResolverListController.getLastChosen();
                // TODO: does this also somehow need to update mLastChosenPosition? If so, maybe
                // the current method should also take responsibility for re-initializing
                // mLastChosenPosition, where it's currently done at the start of rebuildList()?
                // (Why is this related to the presence of mOtherProfile in fhe first place?)
            } catch (RemoteException re) {
                Log.d(TAG, "Error calling getLastChosenActivity\n" + re);
            }
        }
    }

    /**
     * Prepare the appropriate placeholders to eventually display the final set of resolved
     * components in a newly-rebuilt list, and spawn an asynchronous sorting task if necessary.
     * This eventually results in a {@code onPostListReady} callback with {@code rebuildCompleted}
     * true; if any asynchronous work is required, that will first be preceded by a separate
     * occurrence of the callback with {@code rebuildCompleted} false (once there are placeholders
     * set up to represent the pending asynchronous results).
     * @return Whether we were able to do all the work to prepare the list for display
     * synchronously; if false, there will eventually be two separate {@code onPostListReady}
     * callbacks, first with placeholders to represent pending asynchronous results, then later when
     * the results are ready for presentation.
     */
    boolean finishRebuildingListWithFilteredResults(
            @Nullable List<ResolvedComponentInfo> filteredResolveList, boolean doPostProcessing) {
        if (filteredResolveList == null || filteredResolveList.size() < 2) {
            // No asynchronous work to do.
            setPlaceholderCount(0);
            processSortedList(filteredResolveList, doPostProcessing);
            return true;
        }

        int placeholderCount = filteredResolveList.size();
        if (mResolverListCommunicator.useLayoutWithDefault()) {
            --placeholderCount;
        }
        setPlaceholderCount(placeholderCount);

        // Send an "incomplete" list-ready while the async task is running.
        postListReadyRunnable(doPostProcessing, /* rebuildCompleted */ false);
        mBgExecutor.execute(() -> {
            List<ResolvedComponentInfo> sortedComponents = null;
            //TODO: the try-catch logic here is to formally match the AsyncTask's behavior.
            // Empirically, we don't need it as in the case on an exception, the app will crash and
            // `onComponentsSorted` won't be invoked.
            try {
                sortComponents(filteredResolveList);
                sortedComponents = filteredResolveList;
            } catch (Throwable t) {
                Log.e(TAG, "Failed to sort components", t);
                throw t;
            } finally {
                final List<ResolvedComponentInfo> result = sortedComponents;
                mCallbackExecutor.execute(() -> onComponentsSorted(result, doPostProcessing));
            }
        });
        return false;
    }

    @WorkerThread
    protected void sortComponents(List<ResolvedComponentInfo> components) {
        mResolverListController.sort(components);
    }

    @MainThread
    protected void onComponentsSorted(
            @Nullable List<ResolvedComponentInfo> sortedComponents, boolean doPostProcessing) {
        processSortedList(sortedComponents, doPostProcessing);
        notifyDataSetChanged();
    }

    protected void processSortedList(
            @Nullable List<ResolvedComponentInfo> sortedComponents, boolean doPostProcessing) {
        final int n = sortedComponents != null ? sortedComponents.size() : 0;
        Trace.beginSection("ResolverListAdapter#processSortedList:" + n);
        if (n != 0) {
            // First put the initial items at the top.
            if (mInitialIntents != null) {
                for (int i = 0; i < mInitialIntents.length; i++) {
                    Intent ii = mInitialIntents[i];
                    if (ii == null) {
                        continue;
                    }
                    // Because of AIDL bug, resolveActivityInfo can't accept subclasses of Intent.
                    final Intent rii = (ii.getClass() == Intent.class) ? ii : new Intent(ii);
                    ActivityInfo ai = rii.resolveActivityInfo(mPm, 0);
                    if (ai == null) {
                        Log.w(TAG, "No activity found for " + ii);
                        continue;
                    }
                    ResolveInfo ri = new ResolveInfo();
                    ri.activityInfo = ai;
                    UserManager userManager =
                            (UserManager) mContext.getSystemService(Context.USER_SERVICE);
                    if (ii instanceof LabeledIntent) {
                        LabeledIntent li = (LabeledIntent) ii;
                        ri.resolvePackageName = li.getSourcePackage();
                        ri.labelRes = li.getLabelResource();
                        ri.nonLocalizedLabel = li.getNonLocalizedLabel();
                        ri.icon = li.getIconResource();
                        ri.iconResourceId = ri.icon;
                    }
                    if (userManager.isManagedProfile()) {
                        ri.noResourceId = true;
                        ri.icon = 0;
                    }

                    ri.userHandle = mInitialIntentsUserSpace;
                    addResolveInfo(DisplayResolveInfo.newDisplayResolveInfo(
                            ii,
                            ri,
                            ri.loadLabel(mPm),
                            null,
                            ii));
                }
            }


            for (ResolvedComponentInfo rci : sortedComponents) {
                final ResolveInfo ri = rci.getResolveInfoAt(0);
                if (ri != null) {
                    addResolveInfoWithAlternates(rci);
                }
            }
        }

        mResolverListCommunicator.sendVoiceChoicesIfNeeded();
        postListReadyRunnable(doPostProcessing, /* rebuildCompleted */ true);
        mIsTabLoaded = true;
        Trace.endSection();
    }

    /**
     * Some necessary methods for creating the list are initiated in onCreate and will also
     * determine the layout known. We therefore can't update the UI inline and post to the
     * callback executor to update after the current task is finished.
     * @param doPostProcessing Whether to update the UI and load additional direct share targets
     *                         after the list has been rebuilt
     * @param rebuildCompleted Whether the list has been completely rebuilt
     */
    public void postListReadyRunnable(boolean doPostProcessing, boolean rebuildCompleted) {
        Runnable listReadyRunnable = new Runnable() {
            @Override
            public void run() {
                if (mDestroyed.get()) {
                    return;
                }
                mResolverListCommunicator.onPostListReady(ResolverListAdapter.this,
                        doPostProcessing, rebuildCompleted);
            }
        };
        mCallbackExecutor.execute(listReadyRunnable);
    }

    private void addResolveInfoWithAlternates(ResolvedComponentInfo rci) {
        final int count = rci.getCount();
        final Intent intent = rci.getIntentAt(0);
        final ResolveInfo add = rci.getResolveInfoAt(0);
        final Intent replaceIntent =
                mResolverListCommunicator.getReplacementIntent(add.activityInfo, intent);
        final Intent defaultIntent = mResolverListCommunicator.getReplacementIntent(
                add.activityInfo, mTargetIntent);
        final DisplayResolveInfo dri = DisplayResolveInfo.newDisplayResolveInfo(
                intent,
                add,
                (replaceIntent != null) ? replaceIntent : defaultIntent);
        dri.setPinned(rci.isPinned());
        if (rci.isPinned()) {
            Log.i(TAG, "Pinned item: " + rci.name);
        }
        addResolveInfo(dri);
        if (replaceIntent == intent) {
            // Only add alternates if we didn't get a specific replacement from
            // the caller. If we have one it trumps potential alternates.
            for (int i = 1, n = count; i < n; i++) {
                final Intent altIntent = rci.getIntentAt(i);
                dri.addAlternateSourceIntent(altIntent);
            }
        }
        updateLastChosenPosition(add);
    }

    private void updateLastChosenPosition(ResolveInfo info) {
        // If another profile is present, ignore the last chosen entry.
        if (mOtherProfile != null) {
            mLastChosenPosition = -1;
            return;
        }
        if (mLastChosen != null
                && mLastChosen.activityInfo.packageName.equals(info.activityInfo.packageName)
                && mLastChosen.activityInfo.name.equals(info.activityInfo.name)) {
            mLastChosenPosition = mDisplayList.size() - 1;
        }
    }

    // We assume that at this point we've already filtered out the only intent for a different
    // targetUserId which we're going to use.
    private void addResolveInfo(DisplayResolveInfo dri) {
        if (dri != null && dri.getResolveInfo() != null
                && dri.getResolveInfo().targetUserId == UserHandle.USER_CURRENT) {
            if (shouldAddResolveInfo(dri)) {
                mDisplayList.add(dri);
                Log.i(TAG, "Add DisplayResolveInfo component: " + dri.getResolvedComponentName()
                        + ", intent component: " + dri.getResolvedIntent().getComponent());
            }
        }
    }

    // Check whether {@code dri} should be added into mDisplayList.
    protected boolean shouldAddResolveInfo(DisplayResolveInfo dri) {
        // Checks if this info is already listed in display.
        for (DisplayResolveInfo existingInfo : mDisplayList) {
            if (ResolveInfoHelpers
                    .resolveInfoMatch(dri.getResolveInfo(), existingInfo.getResolveInfo())) {
                return false;
            }
        }
        return true;
    }

    @Nullable
    public ResolveInfo resolveInfoForPosition(int position, boolean filtered) {
        TargetInfo target = targetInfoForPosition(position, filtered);
        if (target != null) {
            return target.getResolveInfo();
        }
        return null;
    }

    @Nullable
    public TargetInfo targetInfoForPosition(int position, boolean filtered) {
        if (filtered) {
            return getItem(position);
        }
        if (mDisplayList.size() > position) {
            return mDisplayList.get(position);
        }
        return null;
    }

    @Override
    public int getCount() {
        int totalSize = mDisplayList == null || mDisplayList.isEmpty() ? mPlaceholderCount :
                mDisplayList.size();
        if (mFilterLastUsed && mLastChosenPosition >= 0) {
            totalSize--;
        }
        return totalSize;
    }

    public int getUnfilteredCount() {
        return mDisplayList.size();
    }

    @Override
    @Nullable
    public TargetInfo getItem(int position) {
        if (mFilterLastUsed && mLastChosenPosition >= 0 && position >= mLastChosenPosition) {
            position++;
        }
        if (mDisplayList.size() > position) {
            return mDisplayList.get(position);
        } else {
            return null;
        }
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    public final int getDisplayResolveInfoCount() {
        return mDisplayList.size();
    }

    public final boolean allResolveInfosHandleAllWebDataUri() {
        return mDisplayList.stream().allMatch(t -> t.getResolveInfo().handleAllWebDataURI);
    }

    public final DisplayResolveInfo getDisplayResolveInfo(int index) {
        // Used to query services. We only query services for primary targets, not alternates.
        return mDisplayList.get(index);
    }

    @Override
    public final View getView(int position, View convertView, ViewGroup parent) {
        View view = convertView;
        if (view == null) {
            view = createView(parent);
        }
        onBindView(view, getItem(position), position);
        return view;
    }

    public final View createView(ViewGroup parent) {
        final View view = onCreateView(parent);
        final ViewHolder holder = new ViewHolder(view);
        view.setTag(holder);
        return view;
    }

    View onCreateView(ViewGroup parent) {
        return mInflater.inflate(
                R.layout.resolve_list_item, parent, false);
    }

    public final void bindView(int position, View view) {
        onBindView(view, getItem(position), position);
    }

    protected void onBindView(View view, TargetInfo info, int position) {
        final ViewHolder holder = (ViewHolder) view.getTag();
        if (info == null) {
            holder.icon.setImageDrawable(loadIconPlaceholder());
            holder.bindLabel("", "");
            return;
        }

        if (info.isDisplayResolveInfo()) {
            DisplayResolveInfo dri = (DisplayResolveInfo) info;
            if (dri.hasDisplayLabel()) {
                holder.bindLabel(
                        dri.getDisplayLabel(),
                        dri.getExtendedInfo());
            } else {
                holder.bindLabel("", "");
                loadLabel(dri);
            }
            holder.bindIcon(info);
            if (!dri.hasDisplayIcon()) {
                loadIcon(dri);
            }
        }
    }

    protected final void loadIcon(DisplayResolveInfo info) {
        if (mRequestedIcons.add(info)) {
            mTargetDataLoader.loadAppTargetIcon(
                    info,
                    getUserHandle(),
                    (drawable) -> onIconLoaded(info, drawable));
        }
    }

    private void onIconLoaded(DisplayResolveInfo displayResolveInfo, Drawable drawable) {
        if (!displayResolveInfo.hasDisplayIcon()) {
            displayResolveInfo.getDisplayIconHolder().setDisplayIcon(drawable);
            notifyDataSetChanged();
        }
    }

    protected final void loadLabel(DisplayResolveInfo info) {
        if (mRequestedLabels.add(info)) {
            mTargetDataLoader.loadLabel(info, (result) -> onLabelLoaded(info, result));
        }
    }

    protected final void onLabelLoaded(
            DisplayResolveInfo displayResolveInfo, LabelInfo result) {
        if (displayResolveInfo.hasDisplayLabel()) {
            return;
        }
        displayResolveInfo.setDisplayLabel(result.getLabel());
        displayResolveInfo.setExtendedInfo(result.getSubLabel());
        notifyDataSetChanged();
    }

    public void onDestroy() {
        mDestroyed.set(true);

        if (mResolverListController != null) {
            mResolverListController.destroy();
        }
        mRequestedIcons.clear();
        mRequestedLabels.clear();
    }

    private static ColorMatrixColorFilter getSuspendedColorMatrix() {
        if (sSuspendedMatrixColorFilter == null) {

            int grayValue = 127;
            float scale = 0.5f; // half bright

            ColorMatrix tempBrightnessMatrix = new ColorMatrix();
            float[] mat = tempBrightnessMatrix.getArray();
            mat[0] = scale;
            mat[6] = scale;
            mat[12] = scale;
            mat[4] = grayValue;
            mat[9] = grayValue;
            mat[14] = grayValue;

            ColorMatrix matrix = new ColorMatrix();
            matrix.setSaturation(0.0f);
            matrix.preConcat(tempBrightnessMatrix);
            sSuspendedMatrixColorFilter = new ColorMatrixColorFilter(matrix);
        }
        return sSuspendedMatrixColorFilter;
    }

    protected final Drawable loadIconPlaceholder() {
        return mContext.getDrawable(R.drawable.resolver_icon_placeholder);
    }

    public void loadFilteredItemIconTaskAsync(@NonNull ImageView iconView) {
        final DisplayResolveInfo iconInfo = getFilteredItem();
        if (iconInfo != null) {
            mTargetDataLoader.loadAppTargetIcon(
                    iconInfo, getUserHandle(), iconView::setImageDrawable);
        }
    }

    public UserHandle getUserHandle() {
        return mUserHandle;
    }

    public final List<ResolvedComponentInfo> getResolversForUser(UserHandle userHandle) {
        return mResolverListController.getResolversForIntentAsUser(
                /* shouldGetResolvedFilter= */ true,
                mResolverListCommunicator.shouldGetActivityMetadata(),
                mResolverListCommunicator.shouldGetOnlyDefaultActivities(),
                mIntents,
                userHandle);
    }

    public final List<Intent> getIntents() {
        // TODO: immutable copy?
        return mIntents;
    }

    public boolean isTabLoaded() {
        return mIsTabLoaded;
    }

    public void markTabLoaded() {
        mIsTabLoaded = true;
    }

    /**
     * Find the first element in a list of {@code ResolvedComponentInfo} objects whose
     * {@code ResolveInfo} specifies a {@code targetUserId} other than the current user.
     * @return the first ResolvedComponentInfo targeting a non-current user, or null if there are
     * none (or if the list itself is null).
     */
    private static ResolvedComponentInfo getFirstNonCurrentUserResolvedComponentInfo(
            @Nullable List<ResolvedComponentInfo> resolveList) {
        if (resolveList == null) {
            return null;
        }

        for (ResolvedComponentInfo info : resolveList) {
            ResolveInfo resolveInfo = info.getResolveInfoAt(0);
            if (resolveInfo.targetUserId != UserHandle.USER_CURRENT) {
                return info;
            }
        }
        return null;
    }

    /**
     * Set up a {@code DisplayResolveInfo} to provide "special treatment" for the first "other"
     * profile in the resolve list (i.e., the first non-current profile to appear as the target user
     * of an element in the resolve list).
     */
    private static DisplayResolveInfo makeOtherProfileDisplayResolveInfo(
            ResolvedComponentInfo resolvedComponentInfo,
            PackageManager pm,
            Intent targetIntent,
            ResolverListCommunicator resolverListCommunicator) {
        ResolveInfo resolveInfo = resolvedComponentInfo.getResolveInfoAt(0);

        Intent pOrigIntent = resolverListCommunicator.getReplacementIntent(
                resolveInfo.activityInfo,
                resolvedComponentInfo.getIntentAt(0));
        Intent replacementIntent = resolverListCommunicator.getReplacementIntent(
                resolveInfo.activityInfo, targetIntent);

        return DisplayResolveInfo.newDisplayResolveInfo(
                resolvedComponentInfo.getIntentAt(0),
                resolveInfo,
                resolveInfo.loadLabel(pm),
                resolveInfo.loadLabel(pm),
                pOrigIntent != null ? pOrigIntent : replacementIntent);
    }

    /**
     * Necessary methods to communicate between {@link ResolverListAdapter}
     * and {@link ResolverActivity}.
     */
    public interface ResolverListCommunicator {

        Intent getReplacementIntent(ActivityInfo activityInfo, Intent defIntent);

        // ResolverListCommunicator
        default void updateProfileViewButton() {
        }

        void onPostListReady(ResolverListAdapter listAdapter, boolean updateUi,
                boolean rebuildCompleted);

        void sendVoiceChoicesIfNeeded();

        default boolean useLayoutWithDefault() {
            return false;
        }

        boolean shouldGetActivityMetadata();

        /**
         * @return true to filter only apps that can handle
         *     {@link android.content.Intent#CATEGORY_DEFAULT} intents
         */
        default boolean shouldGetOnlyDefaultActivities() {
            return true;
        }

        void onHandlePackagesChanged(ResolverListAdapter listAdapter);
    }

    /**
     * A view holder keeps a reference to a list view and provides functionality for managing its
     * state.
     */
    @VisibleForTesting
    public static class ViewHolder {
        public View itemView;
        public final Drawable defaultItemViewBackground;

        public TextView text;
        public TextView text2;
        public ImageView icon;

        public final void reset() {
            text.setText("");
            text.setMaxLines(2);
            text.setMaxWidth(Integer.MAX_VALUE);

            text2.setVisibility(View.GONE);
            text2.setText("");

            itemView.setContentDescription(null);
            itemView.setBackground(defaultItemViewBackground);

            icon.setImageDrawable(null);
            icon.setColorFilter(null);
            icon.clearAnimation();
        }

        @VisibleForTesting
        public ViewHolder(View view) {
            itemView = view;
            defaultItemViewBackground = view.getBackground();
            text = (TextView) view.findViewById(com.android.internal.R.id.text1);
            text2 = (TextView) view.findViewById(com.android.internal.R.id.text2);
            icon = (ImageView) view.findViewById(com.android.internal.R.id.icon);
        }

        public void bindLabel(CharSequence label, CharSequence subLabel) {
            text.setText(label);

            if (TextUtils.equals(label, subLabel)) {
                subLabel = null;
            }

            if (!TextUtils.isEmpty(subLabel)) {
                text.setMaxLines(1);
                text2.setText(subLabel);
                text2.setVisibility(View.VISIBLE);
            } else {
                text.setMaxLines(2);
                text2.setVisibility(View.GONE);
            }

            itemView.setContentDescription(null);
        }

        /**
         * Bind view holder to a TargetInfo.
         */
        public void bindIcon(TargetInfo info) {
            Drawable displayIcon = info.getDisplayIconHolder().getDisplayIcon();
            icon.setImageDrawable(displayIcon);
            if (info.isSuspended()) {
                icon.setColorFilter(getSuspendedColorMatrix());
            } else {
                icon.setColorFilter(null);
            }
        }
    }
}
