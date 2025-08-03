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

import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
import static com.android.intentresolver.ChooserActivity.TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER;

import android.app.ActivityManager;
import android.app.prediction.AppTarget;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.pm.LabeledIntent;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.DeviceConfig;
import android.service.chooser.ChooserTarget;
import android.text.Layout;
import android.text.TextUtils;
import android.util.Log;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.MainThread;
import androidx.annotation.Nullable;
import androidx.annotation.WorkerThread;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;
import com.android.intentresolver.chooser.NotSelectableTargetInfo;
import com.android.intentresolver.chooser.SelectableTargetInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.icons.TargetDataLoader;
import com.android.intentresolver.logging.EventLog;
import com.android.intentresolver.widget.BadgeTextView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.config.sysui.SystemUiDeviceConfigFlags;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.Executor;
import java.util.stream.Collectors;

public class ChooserListAdapter extends ResolverListAdapter {

    /**
     * Delegate interface for injecting a chooser-specific operation to be performed before handling
     * a package-change event. This allows the "driver" invoking the package-change to be generic,
     * with no knowledge specific to the chooser implementation.
     */
    public interface PackageChangeCallback {
        /** Perform any steps necessary before processing the package-change event. */
        void beforeHandlingPackagesChanged();
    }

    private static final String TAG = "ChooserListAdapter";
    private static final boolean DEBUG = false;

    public static final int NO_POSITION = -1;
    public static final int TARGET_BAD = -1;
    public static final int TARGET_CALLER = 0;
    public static final int TARGET_SERVICE = 1;
    public static final int TARGET_STANDARD = 2;
    public static final int TARGET_STANDARD_AZ = 3;

    private static final int MAX_SUGGESTED_APP_TARGETS = 4;

    /** {@link #getBaseScore} */
    public static final float CALLER_TARGET_SCORE_BOOST = 900.f;
    /** {@link #getBaseScore} */
    public static final float SHORTCUT_TARGET_SCORE_BOOST = 90.f;

    private final Intent mReferrerFillInIntent;

    private final int mMaxRankedTargets;

    private final EventLog mEventLog;

    private final Set<TargetInfo> mRequestedIcons = new HashSet<>();

    @Nullable
    private final PackageChangeCallback mPackageChangeCallback;

    // Reserve spots for incoming direct share targets by adding placeholders
    private final TargetInfo mPlaceHolderTargetInfo;
    private final TargetDataLoader mTargetDataLoader;
    private final boolean mUseBadgeTextViewForLabels;
    private final List<TargetInfo> mServiceTargets = new ArrayList<>();
    private final List<DisplayResolveInfo> mCallerTargets = new ArrayList<>();

    private final ShortcutSelectionLogic mShortcutSelectionLogic;

    // Sorted list of DisplayResolveInfos for the alphabetical app section.
    private final List<DisplayResolveInfo> mSortedList = new ArrayList<>();

    private final ItemRevealAnimationTracker mAnimationTracker = new ItemRevealAnimationTracker();

    // For pinned direct share labels, if the text spans multiple lines, the TextView will consume
    // the full width, even if the characters actually take up less than that. Measure the actual
    // line widths and constrain the View's width based upon that so that the pin doesn't end up
    // very far from the text.
    private final View.OnLayoutChangeListener mPinTextSpacingListener =
            new View.OnLayoutChangeListener() {
                @Override
                public void onLayoutChange(View v, int left, int top, int right, int bottom,
                        int oldLeft, int oldTop, int oldRight, int oldBottom) {
                    TextView textView = (TextView) v;
                    Layout layout = textView.getLayout();
                    if (layout != null) {
                        int textWidth = 0;
                        for (int line = 0; line < layout.getLineCount(); line++) {
                            textWidth = Math.max((int) Math.ceil(layout.getLineMax(line)),
                                    textWidth);
                        }
                        int desiredWidth = textWidth + textView.getPaddingLeft()
                                + textView.getPaddingRight();
                        if (textView.getWidth() > desiredWidth) {
                            ViewGroup.LayoutParams params = textView.getLayoutParams();
                            params.width = desiredWidth;
                            textView.setLayoutParams(params);
                            // Need to wait until layout pass is over before requesting layout.
                            textView.post(() -> textView.requestLayout());
                        }
                        textView.removeOnLayoutChangeListener(this);
                    }
                }
            };

    public ChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            Intent referrerFillInIntent,
            ResolverListCommunicator resolverListCommunicator,
            PackageManager packageManager,
            EventLog eventLog,
            int maxRankedTargets,
            UserHandle initialIntentsUserSpace,
            TargetDataLoader targetDataLoader,
            @Nullable PackageChangeCallback packageChangeCallback,
            FeatureFlags featureFlags) {
        this(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                resolverListController,
                userHandle,
                targetIntent,
                referrerFillInIntent,
                resolverListCommunicator,
                packageManager,
                eventLog,
                maxRankedTargets,
                initialIntentsUserSpace,
                targetDataLoader,
                packageChangeCallback,
                AsyncTask.SERIAL_EXECUTOR,
                context.getMainExecutor(),
                featureFlags);
    }

    @VisibleForTesting
    public ChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            Intent referrerFillInIntent,
            ResolverListCommunicator resolverListCommunicator,
            PackageManager packageManager,
            EventLog eventLog,
            int maxRankedTargets,
            UserHandle initialIntentsUserSpace,
            TargetDataLoader targetDataLoader,
            @Nullable PackageChangeCallback packageChangeCallback,
            Executor bgExecutor,
            Executor mainExecutor,
            FeatureFlags featureFlags) {
        // Don't send the initial intents through the shared ResolverActivity path,
        // we want to separate them into a different section.
        super(
                context,
                payloadIntents,
                null,
                rList,
                filterLastUsed,
                resolverListController,
                userHandle,
                targetIntent,
                resolverListCommunicator,
                initialIntentsUserSpace,
                targetDataLoader,
                bgExecutor,
                mainExecutor);

        mMaxRankedTargets = maxRankedTargets;
        mReferrerFillInIntent = referrerFillInIntent;

        mPlaceHolderTargetInfo = NotSelectableTargetInfo.newPlaceHolderTargetInfo(context);
        mTargetDataLoader = targetDataLoader;
        mPackageChangeCallback = packageChangeCallback;
        mUseBadgeTextViewForLabels = featureFlags.bespokeLabelView();
        createPlaceHolders();
        mEventLog = eventLog;
        mShortcutSelectionLogic = new ShortcutSelectionLogic(
                context.getResources().getInteger(R.integer.config_maxShortcutTargetsPerApp),
                DeviceConfig.getBoolean(
                        DeviceConfig.NAMESPACE_SYSTEMUI,
                        SystemUiDeviceConfigFlags.APPLY_SHARING_APP_LIMITS_IN_SYSUI,
                        true)
        );

        if (initialIntents != null) {
            for (int i = 0; i < initialIntents.length; i++) {
                final Intent ii = initialIntents[i];
                if (ii == null) {
                    continue;
                }

                // We reimplement Intent#resolveActivityInfo here because if we have an
                // implicit intent, we want the ResolveInfo returned by PackageManager
                // instead of one we reconstruct ourselves. The ResolveInfo returned might
                // have extra metadata and resolvePackageName set and we want to respect that.
                ResolveInfo ri = null;
                ActivityInfo ai = null;
                final ComponentName cn = ii.getComponent();
                if (cn != null) {
                    try {
                        ai = packageManager.getActivityInfo(
                                ii.getComponent(),
                                PackageManager.ComponentInfoFlags.of(PackageManager.GET_META_DATA));
                        ri = new ResolveInfo();
                        ri.activityInfo = ai;
                    } catch (PackageManager.NameNotFoundException ignored) {
                        // ai will == null below
                    }
                }
                if (ai == null) {
                    // Because of AIDL bug, resolveActivity can't accept subclasses of Intent.
                    final Intent rii = (ii.getClass() == Intent.class) ? ii : new Intent(ii);
                    ri = packageManager.resolveActivity(
                            rii,
                            PackageManager.ResolveInfoFlags.of(PackageManager.MATCH_DEFAULT_ONLY));
                    ai = ri != null ? ri.activityInfo : null;
                }
                if (ai == null) {
                    Log.w(TAG, "No activity found for " + ii);
                    continue;
                }
                UserManager userManager =
                        (UserManager) context.getSystemService(Context.USER_SERVICE);
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
                ri.userHandle = initialIntentsUserSpace;
                DisplayResolveInfo displayResolveInfo =
                        DisplayResolveInfo.newDisplayResolveInfo(ii, ri, ii);
                mCallerTargets.add(displayResolveInfo);
                if (mCallerTargets.size() == MAX_SUGGESTED_APP_TARGETS) break;
            }
        }
    }

    @Override
    public void handlePackagesChanged() {
        if (mPackageChangeCallback != null) {
            mPackageChangeCallback.beforeHandlingPackagesChanged();
        }
        if (DEBUG) {
            Log.d(TAG, "clearing queryTargets on package change");
        }
        createPlaceHolders();
        mResolverListCommunicator.onHandlePackagesChanged(this);

    }

    @Override
    public boolean rebuildList(boolean doPostProcessing) {
        mAnimationTracker.reset();
        mSortedList.clear();
        boolean result = super.rebuildList(doPostProcessing);
        notifyDataSetChanged();
        return result;
    }

    private void createPlaceHolders() {
        mServiceTargets.clear();
        for (int i = 0; i < mMaxRankedTargets; ++i) {
            mServiceTargets.add(mPlaceHolderTargetInfo);
        }
    }

    @Override
    View onCreateView(ViewGroup parent) {
        return mInflater.inflate(
                mUseBadgeTextViewForLabels
                        ? R.layout.chooser_grid_item
                        : R.layout.resolve_grid_item,
                parent,
                false);
    }

    @VisibleForTesting
    @Override
    public void onBindView(View view, TargetInfo info, int position) {
        final ViewHolder holder = (ViewHolder) view.getTag();

        resetViewHolder(holder);
        // Always remove the spacing listener, attach as needed to direct share targets below.
        holder.text.removeOnLayoutChangeListener(mPinTextSpacingListener);

        if (info == null) {
            holder.icon.setImageDrawable(loadIconPlaceholder());
            return;
        }

        final CharSequence displayLabel = Objects.requireNonNullElse(info.getDisplayLabel(), "");
        final CharSequence extendedInfo = Objects.requireNonNullElse(info.getExtendedInfo(), "");
        holder.bindLabel(displayLabel, extendedInfo);
        if (!TextUtils.isEmpty(displayLabel)) {
            mAnimationTracker.animateLabel(holder.text, info);
        }
        if (!TextUtils.isEmpty(extendedInfo) && holder.text2.getVisibility() == View.VISIBLE) {
            mAnimationTracker.animateLabel(holder.text2, info);
        }

        holder.bindIcon(info);
        if (info.hasDisplayIcon()) {
            mAnimationTracker.animateIcon(holder.icon, info);
        }

        if (info.isSelectableTargetInfo()) {
            // direct share targets should append the application name for a better readout
            DisplayResolveInfo rInfo = info.getDisplayResolveInfo();
            CharSequence appName =
                    Objects.requireNonNullElse(rInfo == null ? null : rInfo.getDisplayLabel(), "");
            String contentDescription =
                    String.join(" ", info.getDisplayLabel(), extendedInfo, appName);
            if (info.isPinned()) {
                contentDescription = String.join(
                    ". ",
                    contentDescription,
                    mContext.getResources().getString(R.string.pinned));
            }
            updateContentDescription(holder, contentDescription);
            if (!info.hasDisplayIcon()) {
                loadDirectShareIcon((SelectableTargetInfo) info);
            }
        } else if (info.isDisplayResolveInfo()) {
            if (info.isPinned()) {
                updateContentDescription(
                        holder,
                        String.join(
                                ". ",
                                info.getDisplayLabel(),
                                mContext.getResources().getString(R.string.pinned)));
            }
            DisplayResolveInfo dri = (DisplayResolveInfo) info;
            if (!dri.hasDisplayIcon()) {
                loadIcon(dri);
            }
            if (!dri.hasDisplayLabel()) {
                loadLabel(dri);
            }
        }

        if (info.isPlaceHolderTargetInfo()) {
            bindPlaceholder(holder);
        }

        if (info.isMultiDisplayResolveInfo()) {
            // If the target is grouped show an indicator
            bindGroupIndicator(
                    holder,
                    mContext.getDrawable(R.drawable.chooser_group_background));
        } else if (info.isPinned() && (getPositionTargetType(position) == TARGET_STANDARD
                || getPositionTargetType(position) == TARGET_SERVICE)) {
            // If the appShare or directShare target is pinned and in the suggested row show a
            // pinned indicator
            bindPinnedIndicator(holder, mContext.getDrawable(R.drawable.chooser_pinned_background));
            holder.text.addOnLayoutChangeListener(mPinTextSpacingListener);
        }
    }

    private void resetViewHolder(ViewHolder holder) {
        holder.reset();
        holder.itemView.setBackground(holder.defaultItemViewBackground);

        if (mUseBadgeTextViewForLabels) {
            ((BadgeTextView) holder.text).setBadgeDrawable(null);
        }
        holder.text.setBackground(null);
        holder.text.setPaddingRelative(0, 0, 0, 0);
    }

    private void updateContentDescription(ViewHolder holder, String description) {
        holder.itemView.setContentDescription(description);
    }

    private void bindPlaceholder(ViewHolder holder) {
        holder.itemView.setBackground(null);
    }

    private void bindGroupIndicator(ViewHolder holder, Drawable indicator) {
        if (mUseBadgeTextViewForLabels) {
            ((BadgeTextView) holder.text).setBadgeDrawable(indicator);
        } else {
            holder.text.setPaddingRelative(0, 0, /*end = */indicator.getIntrinsicWidth(), 0);
            holder.text.setBackground(indicator);
        }
    }

    private void bindPinnedIndicator(ViewHolder holder, Drawable indicator) {
        holder.text.setPaddingRelative(/*start = */indicator.getIntrinsicWidth(), 0, 0, 0);
        holder.text.setBackground(indicator);
    }

    private void loadDirectShareIcon(SelectableTargetInfo info) {
        if (mRequestedIcons.add(info)) {
            mTargetDataLoader.loadDirectShareIcon(
                    info,
                    getUserHandle(),
                    (drawable) -> onDirectShareIconLoaded(info, drawable));
        }
    }

    private void onDirectShareIconLoaded(SelectableTargetInfo mTargetInfo, Drawable icon) {
        if (icon != null && !mTargetInfo.hasDisplayIcon()) {
            mTargetInfo.getDisplayIconHolder().setDisplayIcon(icon);
            notifyDataSetChanged();
        }
    }

    public void updateAlphabeticalList() {
        final ChooserActivity.AzInfoComparator comparator =
                new ChooserActivity.AzInfoComparator(mContext);
        final List<DisplayResolveInfo> allTargets = new ArrayList<>();
        allTargets.addAll(getTargetsInCurrentDisplayList());
        allTargets.addAll(mCallerTargets);

        new AsyncTask<Void, Void, List<DisplayResolveInfo>>() {
            @Override
            protected List<DisplayResolveInfo> doInBackground(Void... voids) {
                try {
                    Trace.beginSection("update-alphabetical-list");
                    return updateList();
                } finally {
                    Trace.endSection();
                }
            }

            private List<DisplayResolveInfo> updateList() {
                loadMissingLabels(allTargets);

                // Consolidate multiple targets from same app.
                return allTargets
                        .stream()
                        .collect(Collectors.groupingBy(target ->
                                target.getResolvedComponentName().getPackageName()
                                        + "#" + target.getDisplayLabel()
                                        + '#' + target.getResolveInfo().userHandle.getIdentifier()
                        ))
                        .values()
                        .stream()
                        .map(appTargets ->
                                (appTargets.size() == 1)
                                        ? appTargets.get(0)
                                        : MultiDisplayResolveInfo.newMultiDisplayResolveInfo(
                                            appTargets))
                        .sorted(comparator)
                        .collect(Collectors.toList());
            }

            @Override
            protected void onPostExecute(List<DisplayResolveInfo> newList) {
                mSortedList.clear();
                mSortedList.addAll(newList);
                notifyDataSetChanged();
            }

            private void loadMissingLabels(List<DisplayResolveInfo> targets) {
                for (DisplayResolveInfo target: targets) {
                    mTargetDataLoader.getOrLoadLabel(target);
                }
            }
        }.execute();
    }

    @Override
    public int getCount() {
        return getRankedTargetCount() + getAlphaTargetCount()
                + getSelectableServiceTargetCount() + getCallerTargetCount();
    }

    @Override
    public int getUnfilteredCount() {
        int appTargets = super.getUnfilteredCount();
        if (appTargets > mMaxRankedTargets) {
            // TODO: what does this condition mean?
            appTargets = appTargets + mMaxRankedTargets;
        }
        return appTargets + getSelectableServiceTargetCount() + getCallerTargetCount();
    }


    public int getCallerTargetCount() {
        return mCallerTargets.size();
    }

    /**
     * Filter out placeholders and non-selectable service targets
     */
    public int getSelectableServiceTargetCount() {
        int count = 0;
        for (TargetInfo info : mServiceTargets) {
            if (info.isSelectableTargetInfo()) {
                count++;
            }
        }
        return count;
    }

    private static boolean hasSendAction(Intent intent) {
        String action = intent.getAction();
        return Intent.ACTION_SEND.equals(action)
                || Intent.ACTION_SEND_MULTIPLE.equals(action);
    }

    public int getServiceTargetCount() {
        if (hasSendAction(getTargetIntent()) && !ActivityManager.isLowRamDeviceStatic()) {
            return Math.min(mServiceTargets.size(), mMaxRankedTargets);
        }

        return 0;
    }

    public int getAlphaTargetCount() {
        int groupedCount = mSortedList.size();
        int ungroupedCount = mCallerTargets.size() + getDisplayResolveInfoCount();
        return (ungroupedCount > mMaxRankedTargets) ? groupedCount : 0;
    }

    /**
     * Fetch ranked app target count
     */
    public int getRankedTargetCount() {
        int spacesAvailable = mMaxRankedTargets - getCallerTargetCount();
        return Math.min(spacesAvailable, super.getCount());
    }

    /** Get all the {@link DisplayResolveInfo} data for our targets. */
    public DisplayResolveInfo[] getDisplayResolveInfos() {
        int size = getDisplayResolveInfoCount();
        DisplayResolveInfo[] resolvedTargets = new DisplayResolveInfo[size];
        for (int i = 0; i < size; i++) {
            resolvedTargets[i] = getDisplayResolveInfo(i);
        }
        return resolvedTargets;
    }

    public int getPositionTargetType(int position) {
        int offset = 0;

        final int serviceTargetCount = getServiceTargetCount();
        if (position < serviceTargetCount) {
            return TARGET_SERVICE;
        }
        offset += serviceTargetCount;

        final int callerTargetCount = getCallerTargetCount();
        if (position - offset < callerTargetCount) {
            return TARGET_CALLER;
        }
        offset += callerTargetCount;

        final int rankedTargetCount = getRankedTargetCount();
        if (position - offset < rankedTargetCount) {
            return TARGET_STANDARD;
        }
        offset += rankedTargetCount;

        final int standardTargetCount = getAlphaTargetCount();
        if (position - offset < standardTargetCount) {
            return TARGET_STANDARD_AZ;
        }

        return TARGET_BAD;
    }

    @Override
    public TargetInfo getItem(int position) {
        return targetInfoForPosition(position, true);
    }

    /**
     * Find target info for a given position.
     * Since ChooserActivity displays several sections of content, determine which
     * section provides this item.
     */
    @Override
    public TargetInfo targetInfoForPosition(int position, boolean filtered) {
        if (position == NO_POSITION) {
            return null;
        }

        int offset = 0;

        // Direct share targets
        final int serviceTargetCount = filtered ? getServiceTargetCount() :
                getSelectableServiceTargetCount();
        if (position < serviceTargetCount) {
            return mServiceTargets.get(position);
        }
        offset += serviceTargetCount;

        // Targets provided by calling app
        final int callerTargetCount = getCallerTargetCount();
        if (position - offset < callerTargetCount) {
            return mCallerTargets.get(position - offset);
        }
        offset += callerTargetCount;

        // Ranked standard app targets
        final int rankedTargetCount = getRankedTargetCount();
        if (position - offset < rankedTargetCount) {
            return filtered ? super.getItem(position - offset)
                    : getDisplayResolveInfo(position - offset);
        }
        offset += rankedTargetCount;

        // Alphabetical complete app target list.
        if (position - offset < getAlphaTargetCount() && !mSortedList.isEmpty()) {
            return mSortedList.get(position - offset);
        }

        return null;
    }

    // Check whether {@code dri} should be added into mDisplayList.
    @Override
    protected boolean shouldAddResolveInfo(DisplayResolveInfo dri) {
        // Checks if this info is already listed in callerTargets.
        for (TargetInfo existingInfo : mCallerTargets) {
            if (ResolveInfoHelpers.resolveInfoMatch(
                    dri.getResolveInfo(), existingInfo.getResolveInfo())) {
                return false;
            }
        }
        return super.shouldAddResolveInfo(dri);
    }

    /**
     * Fetch surfaced direct share target info
     */
    public List<TargetInfo> getSurfacedTargetInfo() {
        return mServiceTargets.subList(0,
                Math.min(mMaxRankedTargets, getSelectableServiceTargetCount()));
    }


    /**
     * Evaluate targets for inclusion in the direct share area. May not be included
     * if score is too low.
     */
    public void addServiceResults(
            @Nullable DisplayResolveInfo origTarget,
            List<ChooserTarget> targets,
            @ChooserActivity.ShareTargetType int targetType,
            Map<ChooserTarget, ShortcutInfo> directShareToShortcutInfos,
            Map<ChooserTarget, AppTarget> directShareToAppTargets) {
        // Avoid inserting any potentially late results.
        if ((mServiceTargets.size() == 1) && mServiceTargets.get(0).isEmptyTargetInfo()) {
            return;
        }
        boolean isShortcutResult = targetType == TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER
                || targetType == TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE;
        boolean isUpdated = mShortcutSelectionLogic.addServiceResults(
                origTarget,
                getBaseScore(origTarget, targetType),
                targets,
                isShortcutResult,
                directShareToShortcutInfos,
                directShareToAppTargets,
                mContext.createContextAsUser(getUserHandle(), 0),
                getTargetIntent(),
                mReferrerFillInIntent,
                mMaxRankedTargets,
                mServiceTargets);
        if (isUpdated) {
            notifyDataSetChanged();
        }
    }

    /**
     * Use the scoring system along with artificial boosts to create up to 4 distinct buckets:
     * <ol>
     *   <li>App-supplied targets
     *   <li>Shortcuts ranked via App Prediction Manager
     *   <li>Shortcuts ranked via legacy heuristics
     *   <li>Legacy direct share targets
     * </ol>
     */
    public float getBaseScore(
            DisplayResolveInfo target,
            @ChooserActivity.ShareTargetType int targetType) {
        if (target == null) {
            return CALLER_TARGET_SCORE_BOOST;
        }
        float score = super.getScore(target);
        if (targetType == TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER
                || targetType == TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE) {
            return score * SHORTCUT_TARGET_SCORE_BOOST;
        }
        return score;
    }

    /**
     * Calling this marks service target loading complete, and will attempt to no longer
     * update the direct share area.
     */
    public void completeServiceTargetLoading() {
        mServiceTargets.removeIf(o -> o.isPlaceHolderTargetInfo());
        if (mServiceTargets.isEmpty()) {
            mServiceTargets.add(NotSelectableTargetInfo.newEmptyTargetInfo());
            mEventLog.logSharesheetEmptyDirectShareRow();
        }
        notifyDataSetChanged();
    }

    /**
     * Rather than fully sorting the input list, this sorting task will put the top k elements
     * in the head of input list and fill the tail with other elements in undetermined order.
     */
    @Override
    @WorkerThread
    protected void sortComponents(List<ResolvedComponentInfo> components) {
        Trace.beginSection("ChooserListAdapter#SortingTask");
        mResolverListController.topK(components, mMaxRankedTargets);
        Trace.endSection();
    }

    @Override
    @MainThread
    protected void onComponentsSorted(
            @Nullable List<ResolvedComponentInfo> sortedComponents, boolean doPostProcessing) {
        processSortedList(sortedComponents, doPostProcessing);
        if (doPostProcessing) {
            notifyDataSetChanged();
        }
    }
}
