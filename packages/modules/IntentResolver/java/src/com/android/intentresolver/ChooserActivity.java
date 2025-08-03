/*
 * Copyright (C) 2008 The Android Open Source Project
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

import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_SHARE_WITH_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CROSS_PROFILE_BLOCKED_TITLE;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK;

import static androidx.lifecycle.LifecycleKt.getCoroutineScope;

import static com.android.internal.util.LatencyTracker.ACTION_LOAD_SHARE_SHEET;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityOptions;
import android.app.prediction.AppPredictor;
import android.app.prediction.AppTarget;
import android.app.prediction.AppTargetEvent;
import android.app.prediction.AppTargetId;
import android.content.ComponentName;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.content.SharedPreferences;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ShortcutInfo;
import android.content.res.Configuration;
import android.database.Cursor;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.service.chooser.ChooserTarget;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.ViewTreeObserver;
import android.view.WindowInsets;
import android.widget.TextView;

import androidx.annotation.IntDef;
import androidx.annotation.MainThread;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.MultiDisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.contentpreview.BasePreviewViewModel;
import com.android.intentresolver.contentpreview.ChooserContentPreviewUi;
import com.android.intentresolver.contentpreview.HeadlineGeneratorImpl;
import com.android.intentresolver.contentpreview.PreviewViewModel;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.emptystate.NoCrossProfileEmptyStateProvider;
import com.android.intentresolver.emptystate.NoCrossProfileEmptyStateProvider.DevicePolicyBlockerEmptyState;
import com.android.intentresolver.grid.ChooserGridAdapter;
import com.android.intentresolver.icons.DefaultTargetDataLoader;
import com.android.intentresolver.icons.TargetDataLoader;
import com.android.intentresolver.logging.EventLog;
import com.android.intentresolver.measurements.Tracer;
import com.android.intentresolver.model.AbstractResolverComparator;
import com.android.intentresolver.model.AppPredictionServiceResolverComparator;
import com.android.intentresolver.model.ResolverRankerServiceResolverComparator;
import com.android.intentresolver.shortcuts.AppPredictorFactory;
import com.android.intentresolver.shortcuts.ShortcutLoader;
import com.android.intentresolver.widget.ImagePreviewView;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.logging.nano.MetricsProto.MetricsEvent;

import dagger.hilt.android.AndroidEntryPoint;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.text.Collator;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * The Chooser Activity handles intent resolution specifically for sharing intents -
 * for example, as generated by {@see android.content.Intent#createChooser(Intent, CharSequence)}.
 *
 */
@AndroidEntryPoint(ResolverActivity.class)
public class ChooserActivity extends Hilt_ChooserActivity implements
        ResolverListAdapter.ResolverListCommunicator, PackagesChangedListener, StartsSelectedItem {
    private static final String TAG = "ChooserActivity";

    /**
     * Boolean extra to change the following behavior: Normally, ChooserActivity finishes itself
     * in onStop when launched in a new task. If this extra is set to true, we do not finish
     * ourselves when onStop gets called.
     */
    public static final String EXTRA_PRIVATE_RETAIN_IN_ON_STOP
            = "com.android.internal.app.ChooserActivity.EXTRA_PRIVATE_RETAIN_IN_ON_STOP";

    /**
     * Transition name for the first image preview.
     * To be used for shared element transition into this activity.
     * @hide
     */
    public static final String FIRST_IMAGE_PREVIEW_TRANSITION_NAME = "screenshot_preview_image";

    private static final boolean DEBUG = true;

    public static final String LAUNCH_LOCATION_DIRECT_SHARE = "direct_share";
    private static final String SHORTCUT_TARGET = "shortcut_target";

    // TODO: these data structures are for one-time use in shuttling data from where they're
    // populated in `ShortcutToChooserTargetConverter` to where they're consumed in
    // `ShortcutSelectionLogic` which packs the appropriate elements into the final `TargetInfo`.
    // That flow should be refactored so that `ChooserActivity` isn't responsible for holding their
    // intermediate data, and then these members can be removed.
    private final Map<ChooserTarget, AppTarget> mDirectShareAppTargetCache = new HashMap<>();
    private final Map<ChooserTarget, ShortcutInfo> mDirectShareShortcutInfoCache = new HashMap<>();

    public static final int TARGET_TYPE_DEFAULT = 0;
    public static final int TARGET_TYPE_CHOOSER_TARGET = 1;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER = 2;
    public static final int TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE = 3;

    private static final int SCROLL_STATUS_IDLE = 0;
    private static final int SCROLL_STATUS_SCROLLING_VERTICAL = 1;
    private static final int SCROLL_STATUS_SCROLLING_HORIZONTAL = 2;

    @IntDef({
            TARGET_TYPE_DEFAULT,
            TARGET_TYPE_CHOOSER_TARGET,
            TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
            TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
    })
    @Retention(RetentionPolicy.SOURCE)
    public @interface ShareTargetType {}

    @Inject public FeatureFlags mFeatureFlags;
    @Inject public EventLog mEventLog;

    private ChooserIntegratedDeviceComponents mIntegratedDeviceComponents;

    /* TODO: this is `nullable` because we have to defer the assignment til onCreate(). We make the
     * only assignment there, and expect it to be ready by the time we ever use it --
     * someday if we move all the usage to a component with a narrower lifecycle (something that
     * matches our Activity's create/destroy lifecycle, not its Java object lifecycle) then we
     * should be able to make this assignment as "final."
     */
    @Nullable
    private ChooserRequestParameters mChooserRequest;

    private ChooserRefinementManager mRefinementManager;

    private ChooserContentPreviewUi mChooserContentPreviewUi;

    private boolean mShouldDisplayLandscape;
    private long mChooserShownTime;
    protected boolean mIsSuccessfullySelected;

    private int mCurrAvailableWidth = 0;
    private Insets mLastAppliedInsets = null;
    private int mLastNumberOfChildren = -1;
    private int mMaxTargetsPerRow = 1;

    private static final int MAX_LOG_RANK_POSITION = 12;

    // TODO: are these used anywhere? They should probably be migrated to ChooserRequestParameters.
    private static final int MAX_EXTRA_INITIAL_INTENTS = 2;
    private static final int MAX_EXTRA_CHOOSER_TARGETS = 2;

    private SharedPreferences mPinnedSharedPrefs;
    private static final String PINNED_SHARED_PREFS_NAME = "chooser_pin_settings";

    private final ExecutorService mBackgroundThreadPoolExecutor = Executors.newFixedThreadPool(5);

    private int mScrollStatus = SCROLL_STATUS_IDLE;

    @VisibleForTesting
    protected ChooserMultiProfilePagerAdapter mChooserMultiProfilePagerAdapter;
    private final EnterTransitionAnimationDelegate mEnterTransitionAnimationDelegate =
            new EnterTransitionAnimationDelegate(this, () -> mResolverDrawerLayout);

    private View mContentView = null;

    private final SparseArray<ProfileRecord> mProfileRecords = new SparseArray<>();

    private boolean mExcludeSharedText = false;
    /**
     * When we intend to finish the activity with a shared element transition, we can't immediately
     * finish() when the transition is invoked, as the receiving end may not be able to start the
     * animation and the UI breaks if this takes too long. Instead we defer finishing until onStop
     * in order to wait for the transition to begin.
     */
    private boolean mFinishWhenStopped = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (Settings.Global.getInt(getContentResolver(), Settings.Global.SECURE_FRP_MODE, 0) == 1) {
            Log.e(TAG, "Sharing disabled due to active FRP lock.");
            super.onCreate(savedInstanceState);
            finish();
            return;
        }
        Tracer.INSTANCE.markLaunched();
        final long intentReceivedTime = System.currentTimeMillis();
        mLatencyTracker.onActionStart(ACTION_LOAD_SHARE_SHEET);

        try {
            mChooserRequest = new ChooserRequestParameters(
                    getIntent(),
                    getReferrerPackageName(),
                    getReferrer());
        } catch (IllegalArgumentException e) {
            Log.e(TAG, "Caller provided invalid Chooser request parameters", e);
            finish();
            super_onCreate(null);
            return;
        }
        mPinnedSharedPrefs = getPinnedSharedPrefs(this);
        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mShouldDisplayLandscape =
                shouldDisplayLandscape(getResources().getConfiguration().orientation);
        setRetainInOnStop(mChooserRequest.shouldRetainInOnStop());

        createProfileRecords(
                new AppPredictorFactory(
                        this,
                        mChooserRequest.getSharedText(),
                        mChooserRequest.getTargetIntentFilter(),
                        getPackageManager().getAppPredictionServicePackageName() != null),
                mChooserRequest.getTargetIntentFilter());


        super.onCreate(
                savedInstanceState,
                mChooserRequest.getTargetIntent(),
                mChooserRequest.getAdditionalTargets(),
                mChooserRequest.getTitle(),
                mChooserRequest.getDefaultTitleResource(),
                mChooserRequest.getInitialIntents(),
                /* resolutionList= */ null,
                /* supportsAlwaysUseOption= */ false,
                new DefaultTargetDataLoader(this, getLifecycle(), false),
                /* safeForwardingMode= */ true);

        getEventLog().logSharesheetTriggered();

        mIntegratedDeviceComponents = getIntegratedDeviceComponents();

        mRefinementManager = new ViewModelProvider(this).get(ChooserRefinementManager.class);

        mRefinementManager.getRefinementCompletion().observe(this, completion -> {
            if (completion.consume()) {
                TargetInfo targetInfo = completion.getTargetInfo();
                // targetInfo is non-null if the refinement process was successful.
                if (targetInfo != null) {
                    maybeRemoveSharedText(targetInfo);

                    // We already block suspended targets from going to refinement, and we probably
                    // can't recover a Chooser session if that's the reason the refined target fails
                    // to launch now. Fire-and-forget the refined launch; ignore the return value
                    // and just make sure the Sharesheet session gets cleaned up regardless.
                    ChooserActivity.super.onTargetSelected(targetInfo, false);
                }

                finish();
            }
        });

        BasePreviewViewModel previewViewModel =
                new ViewModelProvider(this, createPreviewViewModelFactory())
                        .get(BasePreviewViewModel.class);
        previewViewModel.init(
                mChooserRequest.getTargetIntent(),
                getIntent(),
                /*additionalContentUri = */ null,
                /*focusedItemIdx = */ 0,
                /*isPayloadTogglingEnabled = */ false);
        mChooserContentPreviewUi = new ChooserContentPreviewUi(
                getCoroutineScope(getLifecycle()),
                previewViewModel.getPreviewDataProvider(),
                mChooserRequest.getTargetIntent(),
                previewViewModel.getImageLoader(),
                createChooserActionFactory(),
                mEnterTransitionAnimationDelegate,
                new HeadlineGeneratorImpl(this),
                ContentTypeHint.NONE,
                mChooserRequest.getMetadataText(),
                /*isPayloadTogglingEnabled =*/ false
        );

        updateStickyContentPreview();
        if (shouldShowStickyContentPreview()
                || mChooserMultiProfilePagerAdapter
                .getCurrentRootAdapter().getSystemRowCount() != 0) {
            getEventLog().logActionShareWithPreview(
                    mChooserContentPreviewUi.getPreferredContentPreview());
        }

        mChooserShownTime = System.currentTimeMillis();
        final long systemCost = mChooserShownTime - intentReceivedTime;
        getEventLog().logChooserActivityShown(
                isWorkProfile(), mChooserRequest.getTargetType(), systemCost);

        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.addOnLayoutChangeListener(this::handleLayoutChange);

            mResolverDrawerLayout.setOnCollapsedChangedListener(
                    isCollapsed -> {
                        mChooserMultiProfilePagerAdapter.setIsCollapsed(isCollapsed);
                        getEventLog().logSharesheetExpansionChanged(isCollapsed);
                    });
        }

        if (DEBUG) {
            Log.d(TAG, "System Time Cost is " + systemCost);
        }

        getEventLog().logShareStarted(
                getReferrerPackageName(),
                mChooserRequest.getTargetType(),
                mChooserRequest.getCallerChooserTargets().size(),
                (mChooserRequest.getInitialIntents() == null)
                        ? 0 : mChooserRequest.getInitialIntents().length,
                isWorkProfile(),
                mChooserContentPreviewUi.getPreferredContentPreview(),
                mChooserRequest.getTargetAction(),
                mChooserRequest.getChooserActions().size(),
                mChooserRequest.getModifyShareAction() != null
        );

        mEnterTransitionAnimationDelegate.postponeTransition();
    }

    @VisibleForTesting
    protected ChooserIntegratedDeviceComponents getIntegratedDeviceComponents() {
        return ChooserIntegratedDeviceComponents.get(this, new SecureSettings());
    }

    @Override
    protected int appliedThemeResId() {
        return R.style.Theme_DeviceDefault_Chooser;
    }

    private void createProfileRecords(
            AppPredictorFactory factory, IntentFilter targetIntentFilter) {
        UserHandle mainUserHandle = getAnnotatedUserHandles().personalProfileUserHandle;
        ProfileRecord record = createProfileRecord(mainUserHandle, targetIntentFilter, factory);
        if (record.shortcutLoader == null) {
            Tracer.INSTANCE.endLaunchToShortcutTrace();
        }

        UserHandle workUserHandle = getAnnotatedUserHandles().workProfileUserHandle;
        if (workUserHandle != null) {
            createProfileRecord(workUserHandle, targetIntentFilter, factory);
        }
    }

    private ProfileRecord createProfileRecord(
            UserHandle userHandle, IntentFilter targetIntentFilter, AppPredictorFactory factory) {
        AppPredictor appPredictor = factory.create(userHandle);
        ShortcutLoader shortcutLoader = ActivityManager.isLowRamDeviceStatic()
                    ? null
                    : createShortcutLoader(
                            this,
                            appPredictor,
                            userHandle,
                            targetIntentFilter,
                            shortcutsResult -> onShortcutsLoaded(userHandle, shortcutsResult));
        ProfileRecord record = new ProfileRecord(appPredictor, shortcutLoader);
        mProfileRecords.put(userHandle.getIdentifier(), record);
        return record;
    }

    @Nullable
    private ProfileRecord getProfileRecord(UserHandle userHandle) {
        return mProfileRecords.get(userHandle.getIdentifier(), null);
    }

    @VisibleForTesting
    protected ShortcutLoader createShortcutLoader(
            Context context,
            AppPredictor appPredictor,
            UserHandle userHandle,
            IntentFilter targetIntentFilter,
            Consumer<ShortcutLoader.Result> callback) {
        return new ShortcutLoader(
                context,
                getCoroutineScope(getLifecycle()),
                appPredictor,
                userHandle,
                targetIntentFilter,
                callback);
    }

    static SharedPreferences getPinnedSharedPrefs(Context context) {
        return context.getSharedPreferences(PINNED_SHARED_PREFS_NAME, MODE_PRIVATE);
    }

    @Override
    protected ChooserMultiProfilePagerAdapter createMultiProfilePagerAdapter(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            TargetDataLoader targetDataLoader) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForTwoProfiles(
                    initialIntents, rList, filterLastUsed, targetDataLoader);
        } else {
            mChooserMultiProfilePagerAdapter = createChooserMultiProfilePagerAdapterForOneProfile(
                    initialIntents, rList, filterLastUsed, targetDataLoader);
        }
        return mChooserMultiProfilePagerAdapter;
    }

    @Override
    protected EmptyStateProvider createBlockerEmptyStateProvider() {
        final boolean isSendAction = mChooserRequest.isSendActionTarget();

        final EmptyState noWorkToPersonalEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */
                        isSendAction ? RESOLVER_CANT_SHARE_WITH_PERSONAL : RESOLVER_CANT_ACCESS_PERSONAL,
                        /* defaultSubtitleResource= */
                        isSendAction ? R.string.resolver_cant_share_with_personal_apps_explanation
                                : R.string.resolver_cant_access_personal_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL,
                        /* devicePolicyEventCategory= */ ResolverActivity.METRICS_CATEGORY_CHOOSER);

        final EmptyState noPersonalToWorkEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */
                        isSendAction ? RESOLVER_CANT_SHARE_WITH_WORK : RESOLVER_CANT_ACCESS_WORK,
                        /* defaultSubtitleResource= */
                        isSendAction ? R.string.resolver_cant_share_with_work_apps_explanation
                                : R.string.resolver_cant_access_work_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK,
                        /* devicePolicyEventCategory= */ ResolverActivity.METRICS_CATEGORY_CHOOSER);

        return new NoCrossProfileEmptyStateProvider(
                getAnnotatedUserHandles().personalProfileUserHandle,
                noWorkToPersonalEmptyState,
                noPersonalToWorkEmptyState,
                createCrossProfileIntentsChecker(),
                getAnnotatedUserHandles().tabOwnerUserHandleForLaunch);
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForOneProfile(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            TargetDataLoader targetDataLoader) {
        ChooserGridAdapter adapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                initialIntents,
                rList,
                filterLastUsed,
                /* userHandle */ getAnnotatedUserHandles().personalProfileUserHandle,
                targetDataLoader);
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                adapter,
                createEmptyStateProvider(/* workProfileUserHandle= */ null),
                /* workProfileQuietModeChecker= */ () -> false,
                /* workProfileUserHandle= */ null,
                getAnnotatedUserHandles().cloneProfileUserHandle,
                mMaxTargetsPerRow,
                mFeatureFlags);
    }

    private ChooserMultiProfilePagerAdapter createChooserMultiProfilePagerAdapterForTwoProfiles(
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            TargetDataLoader targetDataLoader) {
        int selectedProfile = findSelectedProfile();
        ChooserGridAdapter personalAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_PERSONAL ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getAnnotatedUserHandles().personalProfileUserHandle,
                targetDataLoader);
        ChooserGridAdapter workAdapter = createChooserGridAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_WORK ? initialIntents : null,
                rList,
                filterLastUsed,
                /* userHandle */ getAnnotatedUserHandles().workProfileUserHandle,
                targetDataLoader);
        return new ChooserMultiProfilePagerAdapter(
                /* context */ this,
                personalAdapter,
                workAdapter,
                createEmptyStateProvider(getAnnotatedUserHandles().workProfileUserHandle),
                () -> mWorkProfileAvailability.isQuietModeEnabled(),
                selectedProfile,
                getAnnotatedUserHandles().workProfileUserHandle,
                getAnnotatedUserHandles().cloneProfileUserHandle,
                mMaxTargetsPerRow,
                mFeatureFlags);
    }

    private int findSelectedProfile() {
        int selectedProfile = getSelectedProfileExtra();
        if (selectedProfile == -1) {
            selectedProfile = getProfileForUser(
                    getAnnotatedUserHandles().tabOwnerUserHandleForLaunch);
        }
        return selectedProfile;
    }

    /**
     * Check if the profile currently used is a work profile.
     * @return true if it is work profile, false if it is parent profile (or no work profile is
     * set up)
     */
    protected boolean isWorkProfile() {
        return getSystemService(UserManager.class)
                .getUserInfo(UserHandle.myUserId()).isManagedProfile();
    }

    @Override
    protected PackageMonitor createPackageMonitor(ResolverListAdapter listAdapter) {
        return new PackageMonitor() {
            @Override
            public void onSomePackagesChanged() {
                handlePackagesChanged(listAdapter);
            }
        };
    }

    /**
     * Update UI to reflect changes in data.
     */
    @Override
    public void handlePackagesChanged() {
        handlePackagesChanged(/* listAdapter */ null);
    }

    /**
     * Update UI to reflect changes in data.
     * <p>If {@code listAdapter} is {@code null}, both profile list adapters are updated if
     * available.
     */
    private void handlePackagesChanged(@Nullable ResolverListAdapter listAdapter) {
        // Refresh pinned items
        mPinnedSharedPrefs = getPinnedSharedPrefs(this);
        if (listAdapter == null) {
            handlePackageChangePerProfile(mChooserMultiProfilePagerAdapter.getActiveListAdapter());
            if (mChooserMultiProfilePagerAdapter.getCount() > 1) {
                handlePackageChangePerProfile(
                        mChooserMultiProfilePagerAdapter.getInactiveListAdapter());
            }
        } else {
            handlePackageChangePerProfile(listAdapter);
        }
        updateProfileViewButton();
    }

    private void handlePackageChangePerProfile(ResolverListAdapter adapter) {
        ProfileRecord record = getProfileRecord(adapter.getUserHandle());
        if (record != null && record.shortcutLoader != null) {
            record.shortcutLoader.reset();
        }
        adapter.handlePackagesChanged();
    }

    @Override
    protected void onResume() {
        super.onResume();
        Log.d(TAG, "onResume: " + getComponentName().flattenToShortString());
        mFinishWhenStopped = false;
        mRefinementManager.onActivityResume();
    }

    @Override
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        if (viewPager.isLayoutRtl()) {
            mMultiProfilePagerAdapter.setupViewPager(viewPager);
        }

        mShouldDisplayLandscape = shouldDisplayLandscape(newConfig.orientation);
        mMaxTargetsPerRow = getResources().getInteger(R.integer.config_chooser_max_targets_per_row);
        mChooserMultiProfilePagerAdapter.setMaxTargetsPerRow(mMaxTargetsPerRow);
        adjustPreviewWidth(newConfig.orientation, null);
        updateStickyContentPreview();
        updateTabPadding();
    }

    private boolean shouldDisplayLandscape(int orientation) {
        // Sharesheet fixes the # of items per row and therefore can not correctly lay out
        // when in the restricted size of multi-window mode. In the future, would be nice
        // to use minimum dp size requirements instead
        return orientation == Configuration.ORIENTATION_LANDSCAPE && !isInMultiWindowMode();
    }

    private void adjustPreviewWidth(int orientation, View parent) {
        int width = -1;
        if (mShouldDisplayLandscape) {
            width = getResources().getDimensionPixelSize(R.dimen.chooser_preview_width);
        }

        parent = parent == null ? getWindow().getDecorView() : parent;

        updateLayoutWidth(com.android.internal.R.id.content_preview_file_layout, width, parent);
    }

    private void updateTabPadding() {
        if (shouldShowTabs()) {
            View tabs = findViewById(com.android.internal.R.id.tabs);
            float iconSize = getResources().getDimension(R.dimen.chooser_icon_size);
            // The entire width consists of icons or padding. Divide the item padding in half to get
            // paddingHorizontal.
            float padding = (tabs.getWidth() - mMaxTargetsPerRow * iconSize)
                    / mMaxTargetsPerRow / 2;
            // Subtract the margin the buttons already have.
            padding -= getResources().getDimension(R.dimen.resolver_profile_tab_margin);
            tabs.setPadding((int) padding, 0, (int) padding, 0);
        }
    }

    private void updateLayoutWidth(int layoutResourceId, int width, View parent) {
        View view = parent.findViewById(layoutResourceId);
        if (view != null && view.getLayoutParams() != null) {
            LayoutParams params = view.getLayoutParams();
            params.width = width;
            view.setLayoutParams(params);
        }
    }

    /**
     * Create a view that will be shown in the content preview area
     * @param parent reference to the parent container where the view should be attached to
     * @return content preview view
     */
    protected ViewGroup createContentPreviewView(ViewGroup parent) {
        ViewGroup layout = mChooserContentPreviewUi.displayContentPreview(
                getResources(),
                getLayoutInflater(),
                parent,
                mFeatureFlags.scrollablePreview()
                        ? findViewById(R.id.chooser_headline_row_container)
                        : null);

        if (layout != null) {
            adjustPreviewWidth(getResources().getConfiguration().orientation, layout);
        }

        return layout;
    }

    @Nullable
    private View getFirstVisibleImgPreviewView() {
        View imagePreview = findViewById(R.id.scrollable_image_preview);
        return imagePreview instanceof ImagePreviewView
                ? ((ImagePreviewView) imagePreview).getTransitionView()
                : null;
    }

    /**
     * Wrapping the ContentResolver call to expose for easier mocking,
     * and to avoid mocking Android core classes.
     */
    @VisibleForTesting
    public Cursor queryResolver(ContentResolver resolver, Uri uri) {
        return resolver.query(uri, null, null, null, null);
    }

    @Override
    protected void onStop() {
        super.onStop();
        mRefinementManager.onActivityStop(isChangingConfigurations());

        if (mFinishWhenStopped) {
            mFinishWhenStopped = false;
            finish();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();

        if (isFinishing()) {
            mLatencyTracker.onActionCancel(ACTION_LOAD_SHARE_SHEET);
        }

        mBackgroundThreadPoolExecutor.shutdownNow();

        destroyProfileRecords();
    }

    private void destroyProfileRecords() {
        for (int i = 0; i < mProfileRecords.size(); ++i) {
            mProfileRecords.valueAt(i).destroy();
        }
        mProfileRecords.clear();
    }

    @Override // ResolverListCommunicator
    public Intent getReplacementIntent(ActivityInfo aInfo, Intent defIntent) {
        if (mChooserRequest == null) {
            return defIntent;
        }

        Intent result = defIntent;
        if (mChooserRequest.getReplacementExtras() != null) {
            final Bundle replExtras =
                    mChooserRequest.getReplacementExtras().getBundle(aInfo.packageName);
            if (replExtras != null) {
                result = new Intent(defIntent);
                result.putExtras(replExtras);
            }
        }
        if (aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_PARENT)
                || aInfo.name.equals(IntentForwarderActivity.FORWARD_INTENT_TO_MANAGED_PROFILE)) {
            result = Intent.createChooser(result,
                    getIntent().getCharSequenceExtra(Intent.EXTRA_TITLE));

            // Don't auto-launch single intents if the intent is being forwarded. This is done
            // because automatically launching a resolving application as a response to the user
            // action of switching accounts is pretty unexpected.
            result.putExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, false);
        }
        return result;
    }

    @Override
    public void onActivityStarted(TargetInfo cti) {
        if (mChooserRequest.getChosenComponentSender() != null) {
            final ComponentName target = cti.getResolvedComponentName();
            if (target != null) {
                final Intent fillIn = new Intent().putExtra(Intent.EXTRA_CHOSEN_COMPONENT, target);
                try {
                    mChooserRequest.getChosenComponentSender().sendIntent(
                            this, Activity.RESULT_OK, fillIn, null, null);
                } catch (IntentSender.SendIntentException e) {
                    Slog.e(TAG, "Unable to launch supplied IntentSender to report "
                            + "the chosen component: " + e);
                }
            }
        }
    }

    private void addCallerChooserTargets() {
        if (!mChooserRequest.getCallerChooserTargets().isEmpty()) {
            // Send the caller's chooser targets only to the default profile.
            UserHandle defaultUser = (findSelectedProfile() == PROFILE_WORK)
                    ? getAnnotatedUserHandles().workProfileUserHandle
                    : getAnnotatedUserHandles().personalProfileUserHandle;
            if (mChooserMultiProfilePagerAdapter.getCurrentUserHandle() == defaultUser) {
                mChooserMultiProfilePagerAdapter.getActiveListAdapter().addServiceResults(
                        /* origTarget */ null,
                        new ArrayList<>(mChooserRequest.getCallerChooserTargets()),
                        TARGET_TYPE_DEFAULT,
                        /* directShareShortcutInfoCache */ Collections.emptyMap(),
                        /* directShareAppTargetCache */ Collections.emptyMap());
            }
        }
    }

    @Override
    public int getLayoutResource() {
        return mFeatureFlags.scrollablePreview()
                ? R.layout.chooser_grid_scrollable_preview
                : R.layout.chooser_grid;
    }

    @Override // ResolverListCommunicator
    public boolean shouldGetActivityMetadata() {
        return true;
    }

    @Override
    public boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        // Note that this is only safe because the Intent handled by the ChooserActivity is
        // guaranteed to contain no extras unknown to the local ClassLoader. That is why this
        // method can not be replaced in the ResolverActivity whole hog.
        if (!super.shouldAutoLaunchSingleChoice(target)) {
            return false;
        }

        return getIntent().getBooleanExtra(Intent.EXTRA_AUTO_LAUNCH_SINGLE_CHOICE, true);
    }

    private void showTargetDetails(TargetInfo targetInfo) {
        if (targetInfo == null) return;

        List<DisplayResolveInfo> targetList = targetInfo.getAllDisplayTargets();
        if (targetList.isEmpty()) {
            Log.e(TAG, "No displayable data to show target details");
            return;
        }

        // TODO: implement these type-conditioned behaviors polymorphically, and consider moving
        // the logic into `ChooserTargetActionsDialogFragment.show()`.
        boolean isShortcutPinned = targetInfo.isSelectableTargetInfo() && targetInfo.isPinned();
        IntentFilter intentFilter = targetInfo.isSelectableTargetInfo()
                ? mChooserRequest.getTargetIntentFilter() : null;
        String shortcutTitle = targetInfo.isSelectableTargetInfo()
                ? targetInfo.getDisplayLabel().toString() : null;
        String shortcutIdKey = targetInfo.getDirectShareShortcutId();

        ChooserTargetActionsDialogFragment.show(
                getSupportFragmentManager(),
                targetList,
                // Adding userHandle from ResolveInfo allows the app icon in Dialog Box to be
                // resolved correctly within the same tab.
                targetInfo.getResolveInfo().userHandle,
                shortcutIdKey,
                shortcutTitle,
                isShortcutPinned,
                intentFilter);
    }

    @Override
    protected boolean onTargetSelected(TargetInfo target, boolean alwaysCheck) {
        if (mRefinementManager.maybeHandleSelection(
                target,
                mChooserRequest.getRefinementIntentSender(),
                getApplication(),
                getMainThreadHandler())) {
            return false;
        }
        updateModelAndChooserCounts(target);
        maybeRemoveSharedText(target);
        return super.onTargetSelected(target, alwaysCheck);
    }

    @Override
    public void startSelected(int which, boolean always, boolean filtered) {
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        TargetInfo targetInfo = currentListAdapter
                .targetInfoForPosition(which, filtered);
        if (targetInfo != null && targetInfo.isNotSelectableTargetInfo()) {
            return;
        }

        final long selectionCost = System.currentTimeMillis() - mChooserShownTime;

        if ((targetInfo != null) && targetInfo.isMultiDisplayResolveInfo()) {
            MultiDisplayResolveInfo mti = (MultiDisplayResolveInfo) targetInfo;
            if (!mti.hasSelected()) {
                // Add userHandle based badge to the stackedAppDialogBox.
                ChooserStackedAppDialogFragment.show(
                        getSupportFragmentManager(),
                        mti,
                        which,
                        targetInfo.getResolveInfo().userHandle);
                return;
            }
        }

        super.startSelected(which, always, filtered);

        // TODO: both of the conditions around this switch logic *should* be redundant, and
        // can be removed if certain invariants can be guaranteed. In particular, it seems
        // like targetInfo (from `ChooserListAdapter.targetInfoForPosition()`) is *probably*
        // expected to be null only at out-of-bounds indexes where `getPositionTargetType()`
        // returns TARGET_BAD; then the switch falls through to a default no-op, and we don't
        // need to null-check targetInfo. We only need the null check if it's possible that
        // the ChooserListAdapter contains null elements "in the middle" of its list data,
        // such that they're classified as belonging to one of the real target types. That
        // should probably never happen. But why would this method ever be invoked with a
        // null target at all? Even an out-of-bounds index should never be "selected"...
        if ((currentListAdapter.getCount() > 0) && (targetInfo != null)) {
            switch (currentListAdapter.getPositionTargetType(which)) {
                case ChooserListAdapter.TARGET_SERVICE:
                    getEventLog().logShareTargetSelected(
                            EventLog.SELECTION_TYPE_SERVICE,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            which,
                            /* directTargetAlsoRanked= */ getRankedPosition(targetInfo),
                            mChooserRequest.getCallerChooserTargets().size(),
                            targetInfo.getHashedTargetIdForMetrics(this),
                            targetInfo.isPinned(),
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
                case ChooserListAdapter.TARGET_CALLER:
                case ChooserListAdapter.TARGET_STANDARD:
                    getEventLog().logShareTargetSelected(
                            EventLog.SELECTION_TYPE_APP,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            (which - currentListAdapter.getSurfacedTargetInfo().size()),
                            /* directTargetAlsoRanked= */ -1,
                            currentListAdapter.getCallerTargetCount(),
                            /* directTargetHashed= */ null,
                            targetInfo.isPinned(),
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
                case ChooserListAdapter.TARGET_STANDARD_AZ:
                    // A-Z targets are unranked standard targets; we use a value of -1 to mark that
                    // they are from the alphabetical pool.
                    // TODO: why do we log a different selection type if the -1 value already
                    // designates the same condition?
                    getEventLog().logShareTargetSelected(
                            EventLog.SELECTION_TYPE_STANDARD,
                            targetInfo.getResolveInfo().activityInfo.processName,
                            /* value= */ -1,
                            /* directTargetAlsoRanked= */ -1,
                            /* numCallerProvided= */ 0,
                            /* directTargetHashed= */ null,
                            /* isPinned= */ false,
                            mIsSuccessfullySelected,
                            selectionCost
                    );
                    return;
            }
        }
    }

    private int getRankedPosition(TargetInfo targetInfo) {
        String targetPackageName =
                targetInfo.getChooserTargetComponentName().getPackageName();
        ChooserListAdapter currentListAdapter =
                mChooserMultiProfilePagerAdapter.getActiveListAdapter();
        int maxRankedResults = Math.min(
                currentListAdapter.getDisplayResolveInfoCount(), MAX_LOG_RANK_POSITION);

        for (int i = 0; i < maxRankedResults; i++) {
            if (currentListAdapter.getDisplayResolveInfo(i)
                    .getResolveInfo().activityInfo.packageName.equals(targetPackageName)) {
                return i;
            }
        }
        return -1;
    }

    @Override
    protected boolean shouldAddFooterView() {
        // To accommodate for window insets
        return true;
    }

    @Override
    protected void applyFooterView(int height) {
        int count = mChooserMultiProfilePagerAdapter.getItemCount();

        for (int i = 0; i < count; i++) {
            mChooserMultiProfilePagerAdapter.getAdapterForIndex(i).setFooterHeight(height);
        }
    }

    private void logDirectShareTargetReceived(UserHandle forUser) {
        ProfileRecord profileRecord = getProfileRecord(forUser);
        if (profileRecord == null) {
            return;
        }
        getEventLog().logDirectShareTargetReceived(
                MetricsEvent.ACTION_DIRECT_SHARE_TARGETS_LOADED_SHORTCUT_MANAGER,
                (int) (SystemClock.elapsedRealtime() - profileRecord.loadingStartTime));
    }

    void updateModelAndChooserCounts(TargetInfo info) {
        if (info != null && info.isMultiDisplayResolveInfo()) {
            info = ((MultiDisplayResolveInfo) info).getSelectedTarget();
        }
        if (info != null) {
            sendClickToAppPredictor(info);
            final ResolveInfo ri = info.getResolveInfo();
            Intent targetIntent = getTargetIntent();
            if (ri != null && ri.activityInfo != null && targetIntent != null) {
                ChooserListAdapter currentListAdapter =
                        mChooserMultiProfilePagerAdapter.getActiveListAdapter();
                if (currentListAdapter != null) {
                    sendImpressionToAppPredictor(info, currentListAdapter);
                    currentListAdapter.updateModel(info);
                    currentListAdapter.updateChooserCounts(
                            ri.activityInfo.packageName,
                            targetIntent.getAction(),
                            ri.userHandle);
                }
                if (DEBUG) {
                    Log.d(TAG, "ResolveInfo Package is " + ri.activityInfo.packageName);
                    Log.d(TAG, "Action to be updated is " + targetIntent.getAction());
                }
            } else if (DEBUG) {
                Log.d(TAG, "Can not log Chooser Counts of null ResolveInfo");
            }
        }
        mIsSuccessfullySelected = true;
    }

    private void maybeRemoveSharedText(@NonNull TargetInfo targetInfo) {
        Intent targetIntent = targetInfo.getTargetIntent();
        if (targetIntent == null) {
            return;
        }
        Intent originalTargetIntent = new Intent(mChooserRequest.getTargetIntent());
        // Our TargetInfo implementations add associated component to the intent, let's do the same
        // for the sake of the comparison below.
        if (targetIntent.getComponent() != null) {
            originalTargetIntent.setComponent(targetIntent.getComponent());
        }
        // Use filterEquals as a way to check that the primary intent is in use (and not an
        // alternative one). For example, an app is sharing an image and a link with mime type
        // "image/png" and provides an alternative intent to share only the link with mime type
        // "text/uri". Should there be a target that accepts only the latter, the alternative intent
        // will be used and we don't want to exclude the link from it.
        if (mExcludeSharedText && originalTargetIntent.filterEquals(targetIntent)) {
            targetIntent.removeExtra(Intent.EXTRA_TEXT);
        }
    }

    private void sendImpressionToAppPredictor(TargetInfo targetInfo, ChooserListAdapter adapter) {
        // Send DS target impression info to AppPredictor, only when user chooses app share.
        if (targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        List<TargetInfo> surfacedTargetInfo = adapter.getSurfacedTargetInfo();
        List<AppTargetId> targetIds = new ArrayList<>();
        for (TargetInfo chooserTargetInfo : surfacedTargetInfo) {
            ShortcutInfo shortcutInfo = chooserTargetInfo.getDirectShareShortcutInfo();
            if (shortcutInfo != null) {
                ComponentName componentName =
                        chooserTargetInfo.getChooserTargetComponentName();
                targetIds.add(new AppTargetId(
                        String.format(
                                "%s/%s/%s",
                                shortcutInfo.getId(),
                                componentName.flattenToString(),
                                SHORTCUT_TARGET)));
            }
        }
        directShareAppPredictor.notifyLaunchLocationShown(LAUNCH_LOCATION_DIRECT_SHARE, targetIds);
    }

    private void sendClickToAppPredictor(TargetInfo targetInfo) {
        if (!targetInfo.isChooserTargetInfo()) {
            return;
        }

        AppPredictor directShareAppPredictor = getAppPredictor(
                mChooserMultiProfilePagerAdapter.getCurrentUserHandle());
        if (directShareAppPredictor == null) {
            return;
        }
        AppTarget appTarget = targetInfo.getDirectShareAppTarget();
        if (appTarget != null) {
            // This is a direct share click that was provided by the APS
            directShareAppPredictor.notifyAppTargetEvent(
                    new AppTargetEvent.Builder(appTarget, AppTargetEvent.ACTION_LAUNCH)
                        .setLaunchLocation(LAUNCH_LOCATION_DIRECT_SHARE)
                        .build());
        }
    }

    @Nullable
    private AppPredictor getAppPredictor(UserHandle userHandle) {
        ProfileRecord record = getProfileRecord(userHandle);
        // We cannot use APS service when clone profile is present as APS service cannot sort
        // cross profile targets as of now.
        return ((record == null) || (getAnnotatedUserHandles().cloneProfileUserHandle != null))
                ? null : record.appPredictor;
    }

    /**
     * Sort intents alphabetically based on display label.
     */
    static class AzInfoComparator implements Comparator<DisplayResolveInfo> {
        Comparator<DisplayResolveInfo> mComparator;
        AzInfoComparator(Context context) {
            Collator collator = Collator
                        .getInstance(context.getResources().getConfiguration().locale);
            // Adding two stage comparator, first stage compares using displayLabel, next stage
            //  compares using resolveInfo.userHandle
            mComparator = Comparator.comparing(DisplayResolveInfo::getDisplayLabel, collator)
                    .thenComparingInt(target -> target.getResolveInfo().userHandle.getIdentifier());
        }

        @Override
        public int compare(
                DisplayResolveInfo lhsp, DisplayResolveInfo rhsp) {
            return mComparator.compare(lhsp, rhsp);
        }
    }

    protected EventLog getEventLog() {
        return mEventLog;
    }

    public class ChooserListController extends ResolverListController {
        public ChooserListController(
                Context context,
                PackageManager pm,
                Intent targetIntent,
                String referrerPackageName,
                int launchedFromUid,
                AbstractResolverComparator resolverComparator,
                UserHandle queryIntentsAsUser) {
            super(
                    context,
                    pm,
                    targetIntent,
                    referrerPackageName,
                    launchedFromUid,
                    resolverComparator,
                    queryIntentsAsUser);
        }

        @Override
        public boolean isComponentFiltered(ComponentName name) {
            return mChooserRequest.getFilteredComponentNames().contains(name);
        }

        @Override
        public boolean isComponentPinned(ComponentName name) {
            return mPinnedSharedPrefs.getBoolean(name.flattenToString(), false);
        }
    }

    @VisibleForTesting
    public ChooserGridAdapter createChooserGridAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            UserHandle userHandle,
            TargetDataLoader targetDataLoader) {
        ChooserListAdapter chooserListAdapter = createChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                getTargetIntent(),
                mChooserRequest.getReferrerFillInIntent(),
                mMaxTargetsPerRow,
                targetDataLoader);

        return new ChooserGridAdapter(
                context,
                new ChooserGridAdapter.ChooserActivityDelegate() {
                    @Override
                    public boolean shouldShowTabs() {
                        return ChooserActivity.this.shouldShowTabs();
                    }

                    @Override
                    public View buildContentPreview(ViewGroup parent) {
                        return createContentPreviewView(parent);
                    }

                    @Override
                    public void onTargetSelected(int itemIndex) {
                        startSelected(itemIndex, false, true);
                    }

                    @Override
                    public void onTargetLongPressed(int selectedPosition) {
                        final TargetInfo longPressedTargetInfo =
                                mChooserMultiProfilePagerAdapter
                                .getActiveListAdapter()
                                .targetInfoForPosition(
                                        selectedPosition, /* filtered= */ true);
                        // Only a direct share target or an app target is expected
                        if (longPressedTargetInfo.isDisplayResolveInfo()
                                || longPressedTargetInfo.isSelectableTargetInfo()) {
                            showTargetDetails(longPressedTargetInfo);
                        }
                    }
                },
                chooserListAdapter,
                shouldShowContentPreview(),
                mMaxTargetsPerRow,
                mFeatureFlags);
    }

    @VisibleForTesting
    public ChooserListAdapter createChooserListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> rList,
            boolean filterLastUsed,
            ResolverListController resolverListController,
            UserHandle userHandle,
            Intent targetIntent,
            Intent referrerFillInIntent,
            int maxTargetsPerRow,
            TargetDataLoader targetDataLoader) {
        UserHandle initialIntentsUserSpace = isLaunchedAsCloneProfile()
                && userHandle.equals(getAnnotatedUserHandles().personalProfileUserHandle)
                ? getAnnotatedUserHandles().cloneProfileUserHandle : userHandle;
        return new ChooserListAdapter(
                context,
                payloadIntents,
                initialIntents,
                rList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                targetIntent,
                referrerFillInIntent,
                this,
                context.getPackageManager(),
                getEventLog(),
                maxTargetsPerRow,
                initialIntentsUserSpace,
                targetDataLoader,
                null,
                mFeatureFlags);
    }

    @Override
    protected void onWorkProfileStatusUpdated() {
        UserHandle workUser = getAnnotatedUserHandles().workProfileUserHandle;
        ProfileRecord record = workUser == null ? null : getProfileRecord(workUser);
        if (record != null && record.shortcutLoader != null) {
            record.shortcutLoader.reset();
        }
        super.onWorkProfileStatusUpdated();
    }

    @Override
    @VisibleForTesting
    protected ChooserListController createListController(UserHandle userHandle) {
        AppPredictor appPredictor = getAppPredictor(userHandle);
        AbstractResolverComparator resolverComparator;
        if (appPredictor != null) {
            resolverComparator = new AppPredictionServiceResolverComparator(this, getTargetIntent(),
                    getReferrerPackageName(), appPredictor, userHandle, getEventLog(),
                    getIntegratedDeviceComponents().getNearbySharingComponent());
        } else {
            resolverComparator =
                    new ResolverRankerServiceResolverComparator(
                            this,
                            getTargetIntent(),
                            getReferrerPackageName(),
                            null,
                            getEventLog(),
                            getResolverRankerServiceUserHandleList(userHandle),
                            getIntegratedDeviceComponents().getNearbySharingComponent());
        }

        return new ChooserListController(
                this,
                mPm,
                getTargetIntent(),
                getReferrerPackageName(),
                getAnnotatedUserHandles().userIdOfCallingApp,
                resolverComparator,
                getQueryIntentsUser(userHandle));
    }

    @VisibleForTesting
    protected ViewModelProvider.Factory createPreviewViewModelFactory() {
        return PreviewViewModel.Companion.getFactory();
    }

    private ChooserActionFactory createChooserActionFactory() {
        return new ChooserActionFactory(
                this,
                mChooserRequest,
                mIntegratedDeviceComponents,
                getEventLog(),
                (isExcluded) -> mExcludeSharedText = isExcluded,
                this::getFirstVisibleImgPreviewView,
                new ChooserActionFactory.ActionActivityStarter() {
                    @Override
                    public void safelyStartActivityAsPersonalProfileUser(TargetInfo targetInfo) {
                        safelyStartActivityAsUser(
                                targetInfo, getAnnotatedUserHandles().personalProfileUserHandle);
                        finish();
                    }

                    @Override
                    public void safelyStartActivityAsPersonalProfileUserWithSharedElementTransition(
                            TargetInfo targetInfo, View sharedElement, String sharedElementName) {
                        ActivityOptions options = ActivityOptions.makeSceneTransitionAnimation(
                                ChooserActivity.this, sharedElement, sharedElementName);
                        safelyStartActivityAsUser(
                                targetInfo,
                                getAnnotatedUserHandles().personalProfileUserHandle,
                                options.toBundle());
                        // Can't finish right away because the shared element transition may not
                        // be ready to start.
                        mFinishWhenStopped = true;
                    }
                },
                (status) -> {
                    if (status != null) {
                        setResult(status);
                    }
                    finish();
                });
    }

    /*
     * Need to dynamically adjust how many icons can fit per row before we add them,
     * which also means setting the correct offset to initially show the content
     * preview area + 2 rows of targets
     */
    private void handleLayoutChange(View v, int left, int top, int right, int bottom, int oldLeft,
            int oldTop, int oldRight, int oldBottom) {
        if (mChooserMultiProfilePagerAdapter == null) {
            return;
        }
        RecyclerView recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        ChooserGridAdapter gridAdapter = mChooserMultiProfilePagerAdapter.getCurrentRootAdapter();
        // Skip height calculation if recycler view was scrolled to prevent it inaccurately
        // calculating the height, as the logic below does not account for the scrolled offset.
        if (gridAdapter == null || recyclerView == null
                || recyclerView.computeVerticalScrollOffset() != 0) {
            return;
        }

        final int availableWidth = right - left - v.getPaddingLeft() - v.getPaddingRight();
        boolean isLayoutUpdated =
                gridAdapter.calculateChooserTargetWidth(availableWidth)
                || recyclerView.getAdapter() == null
                || availableWidth != mCurrAvailableWidth;

        boolean insetsChanged = !Objects.equals(mLastAppliedInsets, mSystemWindowInsets);

        if (isLayoutUpdated
                || insetsChanged
                || mLastNumberOfChildren != recyclerView.getChildCount()) {
            mCurrAvailableWidth = availableWidth;
            if (isLayoutUpdated) {
                // It is very important we call setAdapter from here. Otherwise in some cases
                // the resolver list doesn't get populated, such as b/150922090, b/150918223
                // and b/150936654
                recyclerView.setAdapter(gridAdapter);
                ((GridLayoutManager) recyclerView.getLayoutManager()).setSpanCount(
                        mMaxTargetsPerRow);

                updateTabPadding();
            }

            UserHandle currentUserHandle = mChooserMultiProfilePagerAdapter.getCurrentUserHandle();
            int currentProfile = getProfileForUser(currentUserHandle);
            int initialProfile = findSelectedProfile();
            if (currentProfile != initialProfile) {
                return;
            }

            if (mLastNumberOfChildren == recyclerView.getChildCount() && !insetsChanged) {
                return;
            }

            getMainThreadHandler().post(() -> {
                if (mResolverDrawerLayout == null || gridAdapter == null) {
                    return;
                }
                int offset = calculateDrawerOffset(top, bottom, recyclerView, gridAdapter);
                mResolverDrawerLayout.setCollapsibleHeightReserved(offset);
                mEnterTransitionAnimationDelegate.markOffsetCalculated();
                mLastAppliedInsets = mSystemWindowInsets;
            });
        }
    }

    private int calculateDrawerOffset(
            int top, int bottom, RecyclerView recyclerView, ChooserGridAdapter gridAdapter) {

        int offset = mSystemWindowInsets != null ? mSystemWindowInsets.bottom : 0;
        int rowsToShow = gridAdapter.getSystemRowCount()
                + gridAdapter.getServiceTargetRowCount()
                + gridAdapter.getCallerAndRankedTargetRowCount();

        // then this is most likely not a SEND_* action, so check
        // the app target count
        if (rowsToShow == 0) {
            rowsToShow = gridAdapter.getRowCount();
        }

        // still zero? then use a default height and leave, which
        // can happen when there are no targets to show
        if (rowsToShow == 0 && !shouldShowStickyContentPreview()) {
            offset += getResources().getDimensionPixelSize(
                    R.dimen.chooser_max_collapsed_height);
            return offset;
        }

        View stickyContentPreview = findViewById(com.android.internal.R.id.content_preview_container);
        if (shouldShowStickyContentPreview() && isStickyContentPreviewShowing()) {
            offset += stickyContentPreview.getHeight();
        }

        if (shouldShowTabs()) {
            offset += findViewById(com.android.internal.R.id.tabs).getHeight();
        }

        if (recyclerView.getVisibility() == View.VISIBLE) {
            rowsToShow = Math.min(4, rowsToShow);
            boolean shouldShowExtraRow = shouldShowExtraRow(rowsToShow);
            mLastNumberOfChildren = recyclerView.getChildCount();
            for (int i = 0, childCount = recyclerView.getChildCount();
                    i < childCount && rowsToShow > 0; i++) {
                View child = recyclerView.getChildAt(i);
                if (((GridLayoutManager.LayoutParams)
                        child.getLayoutParams()).getSpanIndex() != 0) {
                    continue;
                }
                int height = child.getHeight();
                offset += height;
                if (shouldShowExtraRow) {
                    offset += height;
                }
                rowsToShow--;
            }
        } else {
            ViewGroup currentEmptyStateView = getActiveEmptyStateView();
            if (currentEmptyStateView.getVisibility() == View.VISIBLE) {
                offset += currentEmptyStateView.getHeight();
            }
        }

        return Math.min(offset, bottom - top);
    }

    /**
     * If we have a tabbed view and are showing 1 row in the current profile and an empty
     * state screen in the other profile, to prevent cropping of the empty state screen we show
     * a second row in the current profile.
     */
    private boolean shouldShowExtraRow(int rowsToShow) {
        return shouldShowTabs()
                && rowsToShow == 1
                && mChooserMultiProfilePagerAdapter.shouldShowEmptyStateScreen(
                        mChooserMultiProfilePagerAdapter.getInactiveListAdapter());
    }

    /**
     * Returns {@link #PROFILE_WORK}, if the given user handle matches work user handle.
     * Returns {@link #PROFILE_PERSONAL}, otherwise.
     **/
    private int getProfileForUser(UserHandle currentUserHandle) {
        if (currentUserHandle.equals(getAnnotatedUserHandles().workProfileUserHandle)) {
            return PROFILE_WORK;
        }
        // We return personal profile, as it is the default when there is no work profile, personal
        // profile represents rootUser, clonedUser & secondaryUser, covering all use cases.
        return PROFILE_PERSONAL;
    }

    private ViewGroup getActiveEmptyStateView() {
        int currentPage = mChooserMultiProfilePagerAdapter.getCurrentPage();
        return mChooserMultiProfilePagerAdapter.getEmptyStateView(currentPage);
    }

    @Override // ResolverListCommunicator
    public void onHandlePackagesChanged(ResolverListAdapter listAdapter) {
        mChooserMultiProfilePagerAdapter.getActiveListAdapter().notifyDataSetChanged();
        super.onHandlePackagesChanged(listAdapter);
    }

    @Override
    protected void onListRebuilt(ResolverListAdapter listAdapter, boolean rebuildComplete) {
        setupScrollListener();
        maybeSetupGlobalLayoutListener();

        ChooserListAdapter chooserListAdapter = (ChooserListAdapter) listAdapter;
        UserHandle listProfileUserHandle = chooserListAdapter.getUserHandle();
        if (listProfileUserHandle.equals(mChooserMultiProfilePagerAdapter.getCurrentUserHandle())) {
            mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                    .setAdapter(mChooserMultiProfilePagerAdapter.getCurrentRootAdapter());
            mChooserMultiProfilePagerAdapter
                    .setupListAdapter(mChooserMultiProfilePagerAdapter.getCurrentPage());
        }

        //TODO: move this block inside ChooserListAdapter (should be called when
        // ResolverListAdapter#mPostListReadyRunnable is executed.
        if (chooserListAdapter.getDisplayResolveInfoCount() == 0) {
            chooserListAdapter.notifyDataSetChanged();
        } else {
            chooserListAdapter.updateAlphabeticalList();
        }

        if (rebuildComplete) {
            long duration = Tracer.INSTANCE.endAppTargetLoadingSection(listProfileUserHandle);
            if (duration >= 0) {
                Log.d(TAG, "app target loading time " + duration + " ms");
            }
            addCallerChooserTargets();
            getEventLog().logSharesheetAppLoadComplete();
            maybeQueryAdditionalPostProcessingTargets(
                    listProfileUserHandle,
                    chooserListAdapter.getDisplayResolveInfos());
            mLatencyTracker.onActionEnd(ACTION_LOAD_SHARE_SHEET);
        }
    }

    private void maybeQueryAdditionalPostProcessingTargets(
            UserHandle userHandle,
            DisplayResolveInfo[] displayResolveInfos) {
        ProfileRecord record = getProfileRecord(userHandle);
        if (record == null || record.shortcutLoader == null) {
            return;
        }
        record.loadingStartTime = SystemClock.elapsedRealtime();
        record.shortcutLoader.updateAppTargets(displayResolveInfos);
    }

    @MainThread
    private void onShortcutsLoaded(UserHandle userHandle, ShortcutLoader.Result result) {
        if (DEBUG) {
            Log.d(TAG, "onShortcutsLoaded for user: " + userHandle);
        }
        mDirectShareShortcutInfoCache.putAll(result.getDirectShareShortcutInfoCache());
        mDirectShareAppTargetCache.putAll(result.getDirectShareAppTargetCache());
        ChooserListAdapter adapter =
                mChooserMultiProfilePagerAdapter.getListAdapterForUserHandle(userHandle);
        if (adapter != null) {
            for (ShortcutLoader.ShortcutResultInfo resultInfo : result.getShortcutsByApp()) {
                adapter.addServiceResults(
                        resultInfo.getAppTarget(),
                        resultInfo.getShortcuts(),
                        result.isFromAppPredictor()
                                ? TARGET_TYPE_SHORTCUTS_FROM_PREDICTION_SERVICE
                                : TARGET_TYPE_SHORTCUTS_FROM_SHORTCUT_MANAGER,
                        mDirectShareShortcutInfoCache,
                        mDirectShareAppTargetCache);
            }
            adapter.completeServiceTargetLoading();
        }

        if (mMultiProfilePagerAdapter.getActiveListAdapter() == adapter) {
            long duration = Tracer.INSTANCE.endLaunchToShortcutTrace();
            if (duration >= 0) {
                Log.d(TAG, "stat to first shortcut time: " + duration + " ms");
            }
        }
        logDirectShareTargetReceived(userHandle);
        sendVoiceChoicesIfNeeded();
        getEventLog().logSharesheetDirectLoadComplete();
    }

    private void setupScrollListener() {
        if (mResolverDrawerLayout == null) {
            return;
        }
        int elevatedViewResId = shouldShowTabs() ? com.android.internal.R.id.tabs : com.android.internal.R.id.chooser_header;
        final View elevatedView = mResolverDrawerLayout.findViewById(elevatedViewResId);
        final float defaultElevation = elevatedView.getElevation();
        final float chooserHeaderScrollElevation =
                getResources().getDimensionPixelSize(R.dimen.chooser_header_scroll_elevation);
        mChooserMultiProfilePagerAdapter.getActiveAdapterView().addOnScrollListener(
                new RecyclerView.OnScrollListener() {
                    @Override
                    public void onScrollStateChanged(@NonNull RecyclerView view, int scrollState) {
                        if (scrollState == RecyclerView.SCROLL_STATE_IDLE) {
                            if (mScrollStatus == SCROLL_STATUS_SCROLLING_VERTICAL) {
                                mScrollStatus = SCROLL_STATUS_IDLE;
                                setHorizontalScrollingEnabled(true);
                            }
                        } else if (scrollState == RecyclerView.SCROLL_STATE_DRAGGING) {
                            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                                mScrollStatus = SCROLL_STATUS_SCROLLING_VERTICAL;
                                setHorizontalScrollingEnabled(false);
                            }
                        }
                    }

                    @Override
                    public void onScrolled(@NonNull RecyclerView view, int dx, int dy) {
                        if (view.getChildCount() > 0) {
                            View child = view.getLayoutManager().findViewByPosition(0);
                            if (child == null || child.getTop() < 0) {
                                elevatedView.setElevation(chooserHeaderScrollElevation);
                                return;
                            }
                        }

                        elevatedView.setElevation(defaultElevation);
                    }
                });
    }

    private void maybeSetupGlobalLayoutListener() {
        if (shouldShowTabs()) {
            return;
        }
        final View recyclerView = mChooserMultiProfilePagerAdapter.getActiveAdapterView();
        recyclerView.getViewTreeObserver()
                .addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
                    @Override
                    public void onGlobalLayout() {
                        // Fixes an issue were the accessibility border disappears on list creation.
                        recyclerView.getViewTreeObserver().removeOnGlobalLayoutListener(this);
                        final TextView titleView = findViewById(com.android.internal.R.id.title);
                        if (titleView != null) {
                            titleView.setFocusable(true);
                            titleView.setFocusableInTouchMode(true);
                            titleView.requestFocus();
                            titleView.requestAccessibilityFocus();
                        }
                    }
                });
    }

    /**
     * The sticky content preview is shown only when we have a tabbed view. It's shown above
     * the tabs so it is not part of the scrollable list. If we are not in tabbed view,
     * we instead show the content preview as a regular list item.
     */
    private boolean shouldShowStickyContentPreview() {
        return shouldShowStickyContentPreviewNoOrientationCheck();
    }

    private boolean shouldShowStickyContentPreviewNoOrientationCheck() {
        if (!shouldShowContentPreview()) {
            return false;
        }
        ResolverListAdapter adapter = mMultiProfilePagerAdapter.getListAdapterForUserHandle(
                UserHandle.of(UserHandle.myUserId()));
        boolean isEmpty = adapter == null || adapter.getCount() == 0;
        return (mFeatureFlags.scrollablePreview() || shouldShowTabs())
                && (!isEmpty || shouldShowContentPreviewWhenEmpty());
    }

    /**
     * This method could be used to override the default behavior when we hide the preview area
     * when the current tab doesn't have any items.
     *
     * @return true if we want to show the content preview area even if the tab for the current
     *         user is empty
     */
    protected boolean shouldShowContentPreviewWhenEmpty() {
        return false;
    }

    /**
     * @return true if we want to show the content preview area
     */
    protected boolean shouldShowContentPreview() {
        return (mChooserRequest != null) && mChooserRequest.isSendActionTarget();
    }

    private void updateStickyContentPreview() {
        if (shouldShowStickyContentPreviewNoOrientationCheck()) {
            // The sticky content preview is only shown when we show the work and personal tabs.
            // We don't show it in landscape as otherwise there is no room for scrolling.
            // If the sticky content preview will be shown at some point with orientation change,
            // then always preload it to avoid subsequent resizing of the share sheet.
            ViewGroup contentPreviewContainer =
                    findViewById(com.android.internal.R.id.content_preview_container);
            if (contentPreviewContainer.getChildCount() == 0) {
                ViewGroup contentPreviewView = createContentPreviewView(contentPreviewContainer);
                contentPreviewContainer.addView(contentPreviewView);
            }
        }
        if (shouldShowStickyContentPreview()) {
            showStickyContentPreview();
        } else {
            hideStickyContentPreview();
        }
    }

    private void showStickyContentPreview() {
        if (isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.VISIBLE);
    }

    private boolean isStickyContentPreviewShowing() {
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        return contentPreviewContainer.getVisibility() == View.VISIBLE;
    }

    private void hideStickyContentPreview() {
        if (!isStickyContentPreviewShowing()) {
            return;
        }
        ViewGroup contentPreviewContainer = findViewById(com.android.internal.R.id.content_preview_container);
        contentPreviewContainer.setVisibility(View.GONE);
    }

    private View findRootView() {
        if (mContentView == null) {
            mContentView = findViewById(android.R.id.content);
        }
        return mContentView;
    }

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    public void onButtonClick(View v) {}

    /**
     * Intentionally override the {@link ResolverActivity} implementation as we only need that
     * implementation for the intent resolver case.
     */
    @Override
    protected void resetButtonBar() {}

    @Override
    protected String getMetricsCategory() {
        return METRICS_CATEGORY_CHOOSER;
    }

    @Override
    protected void onProfileTabSelected() {
        // This fixes an edge case where after performing a variety of gestures, vertical scrolling
        // ends up disabled. That's because at some point the old tab's vertical scrolling is
        // disabled and the new tab's is enabled. For context, see b/159997845
        setVerticalScrollEnabled(true);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.scrollNestedScrollableChildBackToTop();
        }
    }

    @Override
    protected WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        if (shouldShowTabs()) {
            mChooserMultiProfilePagerAdapter
                    .setEmptyStateBottomOffset(insets.getSystemWindowInsetBottom());
            mChooserMultiProfilePagerAdapter.setupContainerPadding(
                    getActiveEmptyStateView().findViewById(com.android.internal.R.id.resolver_empty_state_container));
        }

        WindowInsets result = super.onApplyWindowInsets(v, insets);
        if (mResolverDrawerLayout != null) {
            mResolverDrawerLayout.requestLayout();
        }
        return result;
    }

    private void setHorizontalScrollingEnabled(boolean enabled) {
        ResolverViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        viewPager.setSwipingEnabled(enabled);
    }

    private void setVerticalScrollEnabled(boolean enabled) {
        ChooserGridLayoutManager layoutManager =
                (ChooserGridLayoutManager) mChooserMultiProfilePagerAdapter.getActiveAdapterView()
                        .getLayoutManager();
        layoutManager.setVerticalScrollEnabled(enabled);
    }

    @Override
    void onHorizontalSwipeStateChanged(int state) {
        if (state == ViewPager.SCROLL_STATE_DRAGGING) {
            if (mScrollStatus == SCROLL_STATUS_IDLE) {
                mScrollStatus = SCROLL_STATUS_SCROLLING_HORIZONTAL;
                setVerticalScrollEnabled(false);
            }
        } else if (state == ViewPager.SCROLL_STATE_IDLE) {
            if (mScrollStatus == SCROLL_STATUS_SCROLLING_HORIZONTAL) {
                mScrollStatus = SCROLL_STATUS_IDLE;
                setVerticalScrollEnabled(true);
            }
        }
    }

    @Override
    protected void maybeLogProfileChange() {
        getEventLog().logSharesheetProfileChanged();
    }

    private static class ProfileRecord {
        /** The {@link AppPredictor} for this profile, if any. */
        @Nullable
        public final AppPredictor appPredictor;
        /**
         * null if we should not load shortcuts.
         */
        @Nullable
        public final ShortcutLoader shortcutLoader;
        public long loadingStartTime;

        private ProfileRecord(
                @Nullable AppPredictor appPredictor,
                @Nullable ShortcutLoader shortcutLoader) {
            this.appPredictor = appPredictor;
            this.shortcutLoader = shortcutLoader;
        }

        public void destroy() {
            if (appPredictor != null) {
                appPredictor.destroy();
            }
        }
    }
}
