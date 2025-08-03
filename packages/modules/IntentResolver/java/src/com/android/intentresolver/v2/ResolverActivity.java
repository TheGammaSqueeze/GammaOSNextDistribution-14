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

package com.android.intentresolver.v2;

import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CROSS_PROFILE_BLOCKED_TITLE;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.intentresolver.v2.ext.CreationExtrasExtKt.addDefaultArgs;
import static com.android.intentresolver.v2.ui.viewmodel.ResolverRequestReaderKt.readResolverRequest;
import static com.android.internal.annotations.VisibleForTesting.Visibility.PROTECTED;

import static java.util.Objects.requireNonNull;

import android.app.ActivityThread;
import android.app.VoiceInteractor.PickOptionRequest;
import android.app.VoiceInteractor.PickOptionRequest.Option;
import android.app.VoiceInteractor.Prompt;
import android.app.admin.DevicePolicyEventLogger;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.graphics.Insets;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PatternMatcher;
import android.os.RemoteException;
import android.os.StrictMode;
import android.os.Trace;
import android.os.UserHandle;
import android.os.UserManager;
import android.provider.Settings;
import android.stats.devicepolicy.DevicePolicyEnums;
import android.text.TextUtils;
import android.util.Log;
import android.util.Slog;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewGroup.LayoutParams;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.AbsListView;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.Space;
import android.widget.TabHost;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.FragmentActivity;
import androidx.lifecycle.viewmodel.CreationExtras;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.AnnotatedUserHandles;
import com.android.intentresolver.R;
import com.android.intentresolver.ResolverListAdapter;
import com.android.intentresolver.ResolverListController;
import com.android.intentresolver.WorkProfileAvailabilityManager;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.emptystate.CompositeEmptyStateProvider;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.icons.DefaultTargetDataLoader;
import com.android.intentresolver.icons.TargetDataLoader;
import com.android.intentresolver.model.ResolverRankerServiceResolverComparator;
import com.android.intentresolver.v2.data.repository.DevicePolicyResources;
import com.android.intentresolver.v2.emptystate.NoAppsAvailableEmptyStateProvider;
import com.android.intentresolver.v2.emptystate.NoCrossProfileEmptyStateProvider;
import com.android.intentresolver.v2.emptystate.NoCrossProfileEmptyStateProvider.DevicePolicyBlockerEmptyState;
import com.android.intentresolver.v2.emptystate.ResolverWorkProfilePausedEmptyStateProvider;
import com.android.intentresolver.v2.profiles.MultiProfilePagerAdapter;
import com.android.intentresolver.v2.profiles.MultiProfilePagerAdapter.ProfileType;
import com.android.intentresolver.v2.profiles.OnProfileSelectedListener;
import com.android.intentresolver.v2.profiles.OnSwitchOnWorkSelectedListener;
import com.android.intentresolver.v2.profiles.ResolverMultiProfilePagerAdapter;
import com.android.intentresolver.v2.profiles.TabConfig;
import com.android.intentresolver.v2.shared.model.Profile;
import com.android.intentresolver.v2.ui.ActionTitle;
import com.android.intentresolver.v2.ui.model.ActivityModel;
import com.android.intentresolver.v2.ui.model.ResolverRequest;
import com.android.intentresolver.v2.validation.Finding;
import com.android.intentresolver.v2.validation.FindingsKt;
import com.android.intentresolver.v2.validation.Invalid;
import com.android.intentresolver.v2.validation.Valid;
import com.android.intentresolver.v2.validation.ValidationResult;
import com.android.intentresolver.widget.ResolverDrawerLayout;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;

import com.google.common.collect.ImmutableList;

import dagger.hilt.android.AndroidEntryPoint;

import kotlin.Pair;
import kotlin.Unit;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Consumer;

import javax.inject.Inject;

/**
 * This is a copy of ResolverActivity to support IntentResolver's ChooserActivity. This code is
 * *not* the resolver that is actually triggered by the system right now (you want
 * frameworks/base/core/java/com/android/internal/app/ResolverActivity.java for that), the full
 * migration is not complete.
 */
@AndroidEntryPoint(FragmentActivity.class)
public class ResolverActivity extends Hilt_ResolverActivity implements
        ResolverListAdapter.ResolverListCommunicator {

    @Inject public PackageManager mPackageManager;
    @Inject public DevicePolicyResources mDevicePolicyResources;
    @Inject public IntentForwarding mIntentForwarding;
    private ResolverRequest mResolverRequest;
    private ActivityModel mActivityModel;
    protected ActivityLogic mLogic;
    protected TargetDataLoader mTargetDataLoader;
    private boolean mResolvingHome;

    private Button mAlwaysButton;
    private Button mOnceButton;
    protected View mProfileView;
    private int mLastSelected = AbsListView.INVALID_POSITION;
    private int mLayoutId;
    private PickTargetOptionRequest mPickOptionRequest;
    // Expected to be true if this object is ResolverActivity or is ResolverWrapperActivity.
    protected ResolverDrawerLayout mResolverDrawerLayout;

    private static final String TAG = "ResolverActivity";
    private static final boolean DEBUG = false;
    private static final String LAST_SHOWN_TAB_KEY = "last_shown_tab_key";

    private boolean mRegistered;

    protected Insets mSystemWindowInsets = null;
    private Space mFooterSpacer = null;

    protected static final String METRICS_CATEGORY_RESOLVER = "intent_resolver";
    protected static final String METRICS_CATEGORY_CHOOSER = "intent_chooser";

    /** Tracks if we should ignore future broadcasts telling us the work profile is enabled */
    private final boolean mWorkProfileHasBeenEnabled = false;

    protected static final String TAB_TAG_PERSONAL = "personal";
    protected static final String TAB_TAG_WORK = "work";

    private PackageMonitor mPersonalPackageMonitor;
    private PackageMonitor mWorkPackageMonitor;

    protected ResolverMultiProfilePagerAdapter mMultiProfilePagerAdapter;

    public static final int PROFILE_PERSONAL = MultiProfilePagerAdapter.PROFILE_PERSONAL;
    public static final int PROFILE_WORK = MultiProfilePagerAdapter.PROFILE_WORK;

    private UserHandle mHeaderCreatorUser;

    @Nullable
    private OnSwitchOnWorkSelectedListener mOnSwitchOnWorkSelectedListener;

    protected PackageMonitor createPackageMonitor(ResolverListAdapter listAdapter) {
        return new PackageMonitor() {
            @Override
            public void onSomePackagesChanged() {
                listAdapter.handlePackagesChanged();
            }

            @Override
            public boolean onPackageChanged(String packageName, int uid, String[] components) {
                // We care about all package changes, not just the whole package itself which is
                // default behavior.
                return true;
            }
        };
    }
    protected ActivityModel createActivityModel() {
        return ActivityModel.createFrom(this);
    }

    @VisibleForTesting
    protected ActivityLogic createActivityLogic() {
        return  new ResolverActivityLogic(
                TAG,
                /* activity = */ this,
                this::onWorkProfileStatusUpdated);
    }

    @NonNull
    @Override
    public CreationExtras getDefaultViewModelCreationExtras() {
        return addDefaultArgs(
                super.getDefaultViewModelCreationExtras(),
                new Pair<>(ActivityModel.ACTIVITY_MODEL_KEY, ActivityModel.createFrom(this)));
    }

    @Override
    protected final void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setTheme(R.style.Theme_DeviceDefault_Resolver);
        mActivityModel = createActivityModel();

        Log.i(TAG, "onCreate");
        Log.i(TAG, "activityModel=" + mActivityModel.toString());
        int callerUid = mActivityModel.getLaunchedFromUid();
        if (callerUid < 0 || UserHandle.isIsolated(callerUid)) {
            Log.e(TAG, "Can't start a resolver from uid " + callerUid);
            finish();
        }

        ValidationResult<ResolverRequest> result = readResolverRequest(mActivityModel);
        if (result instanceof Invalid) {
            ((Invalid) result).getErrors().forEach(new Consumer<Finding>() {
                @Override
                public void accept(Finding finding) {
                    FindingsKt.log(finding, TAG);
                }
            });
            finish();
        }
        mResolverRequest = ((Valid<ResolverRequest>) result).getValue();
        mLogic = createActivityLogic();
        mResolvingHome = mResolverRequest.isResolvingHome();
        mTargetDataLoader = new DefaultTargetDataLoader(
                this,
                getLifecycle(),
                mResolverRequest.isAudioCaptureDevice());
        init();
        restore(savedInstanceState);
    }

    private void init() {
        Intent intent = mResolverRequest.getIntent();

        // The last argument of createResolverListAdapter is whether to do special handling
        // of the last used choice to highlight it in the list.  We need to always
        // turn this off when running under voice interaction, since it results in
        // a more complicated UI that the current voice interaction flow is not able
        // to handle. We also turn it off when multiple tabs are shown to simplify the UX.
        // We also turn it off when clonedProfile is present on the device, because we might have
        // different "last chosen" activities in the different profiles, and PackageManager doesn't
        // provide any more information to help us select between them.
        boolean filterLastUsed = !isVoiceInteraction()
                && !hasWorkProfile() && !hasCloneProfile();
        mMultiProfilePagerAdapter = createMultiProfilePagerAdapter(
                new Intent[0],
                /* resolutionList = */ mResolverRequest.getResolutionList(),
                filterLastUsed
        );
        if (configureContentView(mTargetDataLoader)) {
            return;
        }

        mPersonalPackageMonitor = createPackageMonitor(
                mMultiProfilePagerAdapter.getPersonalListAdapter());
        mPersonalPackageMonitor.register(
                this,
                getMainLooper(),
                requireAnnotatedUserHandles().personalProfileUserHandle,
                false
        );
        if (hasWorkProfile()) {
            mWorkPackageMonitor = createPackageMonitor(
                    mMultiProfilePagerAdapter.getWorkListAdapter());
            mWorkPackageMonitor.register(
                    this,
                    getMainLooper(),
                    requireAnnotatedUserHandles().workProfileUserHandle,
                    false
            );
        }

        mRegistered = true;

        final ResolverDrawerLayout rdl = findViewById(com.android.internal.R.id.contentPanel);
        if (rdl != null) {
            rdl.setOnDismissedListener(new ResolverDrawerLayout.OnDismissedListener() {
                @Override
                public void onDismissed() {
                    finish();
                }
            });

            boolean hasTouchScreen = mPackageManager
                    .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

            if (isVoiceInteraction() || !hasTouchScreen) {
                rdl.setCollapsed(false);
            }

            rdl.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            rdl.setOnApplyWindowInsetsListener(this::onApplyWindowInsets);

            mResolverDrawerLayout = rdl;
        }

        final Set<String> categories = intent.getCategories();
        MetricsLogger.action(this, mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem()
                ? MetricsProto.MetricsEvent.ACTION_SHOW_APP_DISAMBIG_APP_FEATURED
                : MetricsProto.MetricsEvent.ACTION_SHOW_APP_DISAMBIG_NONE_FEATURED,
                intent.getAction() + ":" + intent.getType() + ":"
                        + (categories != null ? Arrays.toString(categories.toArray()) : ""));
    }

    private void restore(@Nullable Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            // onRestoreInstanceState
            resetButtonBar();
            ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
            if (viewPager != null) {
                viewPager.setCurrentItem(savedInstanceState.getInt(LAST_SHOWN_TAB_KEY));
            }
        }

        mMultiProfilePagerAdapter.clearInactiveProfileCache();
    }

    protected ResolverMultiProfilePagerAdapter createMultiProfilePagerAdapter(
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed) {
        ResolverMultiProfilePagerAdapter resolverMultiProfilePagerAdapter = null;
        if (hasWorkProfile()) {
            resolverMultiProfilePagerAdapter =
                    createResolverMultiProfilePagerAdapterForTwoProfiles(
                            initialIntents, resolutionList, filterLastUsed);
        } else {
            resolverMultiProfilePagerAdapter = createResolverMultiProfilePagerAdapterForOneProfile(
                    initialIntents, resolutionList, filterLastUsed);
        }
        return resolverMultiProfilePagerAdapter;
    }

    protected EmptyStateProvider createBlockerEmptyStateProvider() {
        final boolean shouldShowNoCrossProfileIntentsEmptyState = getUser().equals(getIntentUser());

        if (!shouldShowNoCrossProfileIntentsEmptyState) {
            // Implementation that doesn't show any blockers
            return new EmptyStateProvider() {};
        }

        final EmptyState noWorkToPersonalEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */ RESOLVER_CANT_ACCESS_PERSONAL,
                        /* defaultSubtitleResource= */
                        R.string.resolver_cant_access_personal_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL,
                        /* devicePolicyEventCategory= */
                                ResolverActivity.METRICS_CATEGORY_RESOLVER);

        final EmptyState noPersonalToWorkEmptyState =
                new DevicePolicyBlockerEmptyState(
                        /* context= */ this,
                        /* devicePolicyStringTitleId= */ RESOLVER_CROSS_PROFILE_BLOCKED_TITLE,
                        /* defaultTitleResource= */ R.string.resolver_cross_profile_blocked,
                        /* devicePolicyStringSubtitleId= */ RESOLVER_CANT_ACCESS_WORK,
                        /* defaultSubtitleResource= */
                        R.string.resolver_cant_access_work_apps_explanation,
                        /* devicePolicyEventId= */ RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK,
                        /* devicePolicyEventCategory= */
                                ResolverActivity.METRICS_CATEGORY_RESOLVER);

        return new NoCrossProfileEmptyStateProvider(
                requireAnnotatedUserHandles().personalProfileUserHandle,
                noWorkToPersonalEmptyState,
                noPersonalToWorkEmptyState,
                createCrossProfileIntentsChecker(),
                requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch);
    }

    /**
     * Numerous layouts are supported, each with optional ViewGroups.
     * Make sure the inset gets added to the correct View, using
     * a footer for Lists so it can properly scroll under the navbar.
     */
    protected boolean shouldAddFooterView() {
        if (useLayoutWithDefault()) return true;

        View buttonBar = findViewById(com.android.internal.R.id.button_bar);
        return buttonBar == null || buttonBar.getVisibility() == View.GONE;
    }

    protected void applyFooterView(int height) {
        if (mFooterSpacer == null) {
            mFooterSpacer = new Space(getApplicationContext());
        } else {
            ((ResolverMultiProfilePagerAdapter) mMultiProfilePagerAdapter)
                .getActiveAdapterView().removeFooterView(mFooterSpacer);
        }
        mFooterSpacer.setLayoutParams(new AbsListView.LayoutParams(LayoutParams.MATCH_PARENT,
                                                                   mSystemWindowInsets.bottom));
        ((ResolverMultiProfilePagerAdapter) mMultiProfilePagerAdapter)
            .getActiveAdapterView().addFooterView(mFooterSpacer);
    }

    protected WindowInsets onApplyWindowInsets(View v, WindowInsets insets) {
        mSystemWindowInsets = insets.getSystemWindowInsets();

        mResolverDrawerLayout.setPadding(mSystemWindowInsets.left, mSystemWindowInsets.top,
                mSystemWindowInsets.right, 0);

        resetButtonBar();

        if (shouldUseMiniResolver()) {
            View buttonContainer = findViewById(com.android.internal.R.id.button_bar_container);
            buttonContainer.setPadding(0, 0, 0, mSystemWindowInsets.bottom
                    + getResources().getDimensionPixelOffset(R.dimen.resolver_button_bar_spacing));
        }

        // Need extra padding so the list can fully scroll up
        if (shouldAddFooterView()) {
            applyFooterView(mSystemWindowInsets.bottom);
        }

        return insets.consumeSystemWindowInsets();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
        if (hasWorkProfile() && !useLayoutWithDefault()
                && !shouldUseMiniResolver()) {
            updateIntentPickerPaddings();
        }

        if (mSystemWindowInsets != null) {
            mResolverDrawerLayout.setPadding(mSystemWindowInsets.left, mSystemWindowInsets.top,
                    mSystemWindowInsets.right, 0);
        }
    }

    public int getLayoutResource() {
        return R.layout.resolver_list;
    }

    @Override
    protected void onStop() {
        super.onStop();

        final Window window = this.getWindow();
        final WindowManager.LayoutParams attrs = window.getAttributes();
        attrs.privateFlags &= ~SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;
        window.setAttributes(attrs);

        if (mRegistered) {
            mPersonalPackageMonitor.unregister();
            if (mWorkPackageMonitor != null) {
                mWorkPackageMonitor.unregister();
            }
            mRegistered = false;
        }
        final Intent intent = getIntent();
        if ((intent.getFlags() & FLAG_ACTIVITY_NEW_TASK) != 0 && !isVoiceInteraction()
                && !mResolvingHome) {
            // This resolver is in the unusual situation where it has been
            // launched at the top of a new task.  We don't let it be added
            // to the recent tasks shown to the user, and we need to make sure
            // that each time we are launched we get the correct launching
            // uid (not re-using the same resolver from an old launching uid),
            // so we will now finish ourself since being no longer visible,
            // the user probably can't get back to us.
            if (!isChangingConfigurations()) {
                finish();
            }
        }
        // TODO: should we clean up the work-profile manager before we potentially finish() above?
        mLogic.getWorkProfileAvailabilityManager().unregisterWorkProfileStateReceiver(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (!isChangingConfigurations() && mPickOptionRequest != null) {
            mPickOptionRequest.cancel();
        }
        if (mMultiProfilePagerAdapter != null
                && mMultiProfilePagerAdapter.getActiveListAdapter() != null) {
            mMultiProfilePagerAdapter.getActiveListAdapter().onDestroy();
        }
    }

    // referenced by layout XML: android:onClick="onButtonClick"
    public void onButtonClick(View v) {
        final int id = v.getId();
        ListView listView = (ListView) mMultiProfilePagerAdapter.getActiveAdapterView();
        ResolverListAdapter currentListAdapter = mMultiProfilePagerAdapter.getActiveListAdapter();
        int which = currentListAdapter.hasFilteredItem()
                ? currentListAdapter.getFilteredPosition()
                : listView.getCheckedItemPosition();
        boolean hasIndexBeenFiltered = !currentListAdapter.hasFilteredItem();
        startSelected(which, id == com.android.internal.R.id.button_always, hasIndexBeenFiltered);
    }

    public void startSelected(int which, boolean always, boolean hasIndexBeenFiltered) {
        if (isFinishing()) {
            return;
        }
        ResolveInfo ri = mMultiProfilePagerAdapter.getActiveListAdapter()
                .resolveInfoForPosition(which, hasIndexBeenFiltered);
        if (mResolvingHome && hasManagedProfile() && !supportsManagedProfiles(ri)) {
            String launcherName = ri.activityInfo.loadLabel(mPackageManager).toString();
            Toast.makeText(this,
                    mDevicePolicyResources.getWorkProfileNotSupportedMessage(launcherName),
                    Toast.LENGTH_LONG).show();
            return;
        }

        TargetInfo target = mMultiProfilePagerAdapter.getActiveListAdapter()
                .targetInfoForPosition(which, hasIndexBeenFiltered);
        if (target == null) {
            return;
        }
        if (onTargetSelected(target, always)) {
            if (always) {
                MetricsLogger.action(
                        this, MetricsProto.MetricsEvent.ACTION_APP_DISAMBIG_ALWAYS);
            } else {
                MetricsLogger.action(
                        this, MetricsProto.MetricsEvent.ACTION_APP_DISAMBIG_JUST_ONCE);
            }
            MetricsLogger.action(this,
                    mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem()
                            ? MetricsProto.MetricsEvent.ACTION_HIDE_APP_DISAMBIG_APP_FEATURED
                            : MetricsProto.MetricsEvent.ACTION_HIDE_APP_DISAMBIG_NONE_FEATURED);
            finish();
        }
    }

    @Override // ResolverListCommunicator
    public Intent getReplacementIntent(ActivityInfo aInfo, Intent defIntent) {
        return defIntent;
    }

    protected void onListRebuilt(ResolverListAdapter listAdapter, boolean rebuildCompleted) {
        final ItemClickListener listener = new ItemClickListener();
        setupAdapterListView((ListView) mMultiProfilePagerAdapter.getActiveAdapterView(), listener);
        if (hasWorkProfile()) {
            final ResolverDrawerLayout rdl = findViewById(com.android.internal.R.id.contentPanel);
            if (rdl != null) {
                rdl.setMaxCollapsedHeight(getResources()
                        .getDimensionPixelSize(useLayoutWithDefault()
                                ? R.dimen.resolver_max_collapsed_height_with_default_with_tabs
                                : R.dimen.resolver_max_collapsed_height_with_tabs));
            }
        }
    }

    protected boolean onTargetSelected(TargetInfo target, boolean always) {
        final ResolveInfo ri = target.getResolveInfo();
        final Intent intent = target != null ? target.getResolvedIntent() : null;

        if (intent != null /*&& mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem()*/
                && mMultiProfilePagerAdapter.getActiveListAdapter().getUnfilteredResolveList() != null) {
            // Build a reasonable intent filter, based on what matched.
            IntentFilter filter = new IntentFilter();
            Intent filterIntent;

            if (intent.getSelector() != null) {
                filterIntent = intent.getSelector();
            } else {
                filterIntent = intent;
            }

            String action = filterIntent.getAction();
            if (action != null) {
                filter.addAction(action);
            }
            Set<String> categories = filterIntent.getCategories();
            if (categories != null) {
                for (String cat : categories) {
                    filter.addCategory(cat);
                }
            }
            filter.addCategory(Intent.CATEGORY_DEFAULT);

            int cat = ri.match & IntentFilter.MATCH_CATEGORY_MASK;
            Uri data = filterIntent.getData();
            if (cat == IntentFilter.MATCH_CATEGORY_TYPE) {
                String mimeType = filterIntent.resolveType(this);
                if (mimeType != null) {
                    try {
                        filter.addDataType(mimeType);
                    } catch (IntentFilter.MalformedMimeTypeException e) {
                        Log.w("ResolverActivity", e);
                        filter = null;
                    }
                }
            }
            if (data != null && data.getScheme() != null) {
                // We need the data specification if there was no type,
                // OR if the scheme is not one of our magical "file:"
                // or "content:" schemes (see IntentFilter for the reason).
                if (cat != IntentFilter.MATCH_CATEGORY_TYPE
                        || (!"file".equals(data.getScheme())
                        && !"content".equals(data.getScheme()))) {
                    filter.addDataScheme(data.getScheme());

                    // Look through the resolved filter to determine which part
                    // of it matched the original Intent.
                    Iterator<PatternMatcher> pIt = ri.filter.schemeSpecificPartsIterator();
                    if (pIt != null) {
                        String ssp = data.getSchemeSpecificPart();
                        while (ssp != null && pIt.hasNext()) {
                            PatternMatcher p = pIt.next();
                            if (p.match(ssp)) {
                                filter.addDataSchemeSpecificPart(p.getPath(), p.getType());
                                break;
                            }
                        }
                    }
                    Iterator<IntentFilter.AuthorityEntry> aIt = ri.filter.authoritiesIterator();
                    if (aIt != null) {
                        while (aIt.hasNext()) {
                            IntentFilter.AuthorityEntry a = aIt.next();
                            if (a.match(data) >= 0) {
                                int port = a.getPort();
                                filter.addDataAuthority(a.getHost(),
                                        port >= 0 ? Integer.toString(port) : null);
                                break;
                            }
                        }
                    }
                    pIt = ri.filter.pathsIterator();
                    if (pIt != null) {
                        String path = data.getPath();
                        while (path != null && pIt.hasNext()) {
                            PatternMatcher p = pIt.next();
                            if (p.match(path)) {
                                filter.addDataPath(p.getPath(), p.getType());
                                break;
                            }
                        }
                    }
                }
            }

            if (filter != null) {
                final int N = mMultiProfilePagerAdapter.getActiveListAdapter()
                        .getUnfilteredResolveList().size();
                ComponentName[] set;
                // If we don't add back in the component for forwarding the intent to a managed
                // profile, the preferred activity may not be updated correctly (as the set of
                // components we tell it we knew about will have changed).
                final boolean needToAddBackProfileForwardingComponent =
                        mMultiProfilePagerAdapter.getActiveListAdapter().getOtherProfile() != null;
                if (!needToAddBackProfileForwardingComponent) {
                    set = new ComponentName[N];
                } else {
                    set = new ComponentName[N + 1];
                }

                int bestMatch = 0;
                for (int i = 0; i < N; i++) {
                    ResolveInfo r = mMultiProfilePagerAdapter.getActiveListAdapter()
                            .getUnfilteredResolveList().get(i).getResolveInfoAt(0);
                    set[i] = new ComponentName(r.activityInfo.packageName,
                            r.activityInfo.name);
                    if (r.match > bestMatch) bestMatch = r.match;
                }

                if (needToAddBackProfileForwardingComponent) {
                    set[N] = mMultiProfilePagerAdapter.getActiveListAdapter()
                            .getOtherProfile().getResolvedComponentName();
                    final int otherProfileMatch = mMultiProfilePagerAdapter.getActiveListAdapter()
                            .getOtherProfile().getResolveInfo().match;
                    if (otherProfileMatch > bestMatch) bestMatch = otherProfileMatch;
                }

                if (always) {
                    final int userId = getUserId();
                    final PackageManager pm = mPackageManager;

                    // Set the preferred Activity
                    pm.addUniquePreferredActivity(filter, bestMatch, set, intent.getComponent());

                    if (ri.handleAllWebDataURI) {
                        // Set default Browser if needed
                        final String packageName = pm.getDefaultBrowserPackageNameAsUser(userId);
                        if (TextUtils.isEmpty(packageName)) {
                            pm.setDefaultBrowserPackageNameAsUser(ri.activityInfo.packageName,
                                    userId);
                        }
                    }
                } else {
                    try {
                        mMultiProfilePagerAdapter.getActiveListAdapter()
                                .mResolverListController.setLastChosen(intent, filter, bestMatch);
                    } catch (RemoteException re) {
                        Log.d(TAG, "Error calling setLastChosenActivity\n" + re);
                    }
                }
            }
        }

        safelyStartActivity(target);

        // Rely on the ActivityManager to pop up a dialog regarding app suspension
        // and return false
        return !target.isSuspended();
    }

    @Override // ResolverListCommunicator
    public boolean shouldGetActivityMetadata() {
        return false;
    }

    public boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        return !target.isSuspended();
    }

    @VisibleForTesting
    protected ResolverListController createListController(UserHandle userHandle) {
        ResolverRankerServiceResolverComparator resolverComparator =
                new ResolverRankerServiceResolverComparator(
                        this,
                        mResolverRequest.getIntent(),
                        mActivityModel.getReferrerPackage(),
                        null,
                        null,
                        getResolverRankerServiceUserHandleList(userHandle),
                        null);
        return new ResolverListController(
                this,
                mPackageManager,
                mActivityModel.getIntent(),
                mActivityModel.getReferrerPackage(),
                mActivityModel.getLaunchedFromUid(),
                resolverComparator,
                getQueryIntentsUser(userHandle));
    }

    /**
     * Finishing procedures to be performed after the list has been rebuilt.
     * </p>Subclasses must call postRebuildListInternal at the end of postRebuildList.
     * @param rebuildCompleted
     * @return <code>true</code> if the activity is finishing and creation should halt.
     */
    protected boolean postRebuildList(boolean rebuildCompleted) {
        return postRebuildListInternal(rebuildCompleted);
    }

    /**
     * Callback called when user changes the profile tab.
     */
    /* TODO: consider merging with the customized considerations of our implemented
     * {@link MultiProfilePagerAdapter.OnProfileSelectedListener}. The only apparent distinctions
     * between the respective listener callbacks would occur in the triggering patterns during init
     * (when the `OnProfileSelectedListener` is registered after a possible tab-change), or possibly
     * if there's some way to trigger an update in one model but not the other.  If there's an
     * initialization dependency, we can probably reason about it with confidence. If there's a
     * discrepancy between the `TabHost` and pager-adapter data models, that inconsistency is
     * likely to be a bug that would benefit from consolidation.
     */
    protected void onProfileTabSelected(int currentPage) {
        setupViewVisibilities();
        maybeLogProfileChange();
        if (hasWorkProfile()) {
            // The device policy logger is only concerned with sessions that include a work profile.
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.RESOLVER_SWITCH_TABS)
                    .setInt(currentPage)
                    .setStrings(getMetricsCategory())
                    .write();
        }
    }

    /**
     * Add a label to signify that the user can pick a different app.
     * @param adapter The adapter used to provide data to item views.
     */
    public void addUseDifferentAppLabelIfNecessary(ResolverListAdapter adapter) {
        final boolean useHeader = adapter.hasFilteredItem();
        if (useHeader) {
            FrameLayout stub = findViewById(com.android.internal.R.id.stub);
            stub.setVisibility(View.VISIBLE);
            TextView textView = (TextView) LayoutInflater.from(this).inflate(
                    R.layout.resolver_different_item_header, null, false);
            if (hasWorkProfile()) {
                textView.setGravity(Gravity.CENTER);
            }
            stub.addView(textView);
        }
    }

    protected void resetButtonBar() {
        final ViewGroup buttonLayout = findViewById(com.android.internal.R.id.button_bar);
        if (buttonLayout == null) {
            Log.e(TAG, "Layout unexpectedly does not have a button bar");
            return;
        }
        ResolverListAdapter activeListAdapter =
                mMultiProfilePagerAdapter.getActiveListAdapter();
        View buttonBarDivider = findViewById(com.android.internal.R.id.resolver_button_bar_divider);
        if (!useLayoutWithDefault()) {
            int inset = mSystemWindowInsets != null ? mSystemWindowInsets.bottom : 0;
            buttonLayout.setPadding(buttonLayout.getPaddingLeft(), buttonLayout.getPaddingTop(),
                    buttonLayout.getPaddingRight(), getResources().getDimensionPixelSize(
                            R.dimen.resolver_button_bar_spacing) + inset);
        }
        if (activeListAdapter.isTabLoaded()
                && mMultiProfilePagerAdapter.shouldShowEmptyStateScreen(activeListAdapter)
                && !useLayoutWithDefault()) {
            buttonLayout.setVisibility(View.INVISIBLE);
            if (buttonBarDivider != null) {
                buttonBarDivider.setVisibility(View.INVISIBLE);
            }
            setButtonBarIgnoreOffset(/* ignoreOffset */ false);
            return;
        }
        if (buttonBarDivider != null) {
            buttonBarDivider.setVisibility(View.VISIBLE);
        }
        buttonLayout.setVisibility(View.VISIBLE);
        setButtonBarIgnoreOffset(/* ignoreOffset */ true);

        mOnceButton = (Button) buttonLayout.findViewById(com.android.internal.R.id.button_once);
        mAlwaysButton = (Button) buttonLayout.findViewById(com.android.internal.R.id.button_always);

        resetAlwaysOrOnceButtonBar();
    }

    protected String getMetricsCategory() {
        return METRICS_CATEGORY_RESOLVER;
    }

    @Override // ResolverListCommunicator
    public final void onHandlePackagesChanged(ResolverListAdapter listAdapter) {
        if (!mMultiProfilePagerAdapter.onHandlePackagesChanged(
                listAdapter,
                mLogic.getWorkProfileAvailabilityManager().isWaitingToEnableWorkProfile())) {
            // We no longer have any items... just finish the activity.
            finish();
        }
    }

    protected void maybeLogProfileChange() {}

    @VisibleForTesting
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        return new CrossProfileIntentsChecker(getContentResolver());
    }

    protected Unit onWorkProfileStatusUpdated() {
        if (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_WORK) {
            mMultiProfilePagerAdapter.rebuildActiveTab(true);
        } else {
            mMultiProfilePagerAdapter.clearInactiveProfileCache();
        }
        return Unit.INSTANCE;
    }

    // @NonFinalForTesting
    @VisibleForTesting
    protected ResolverListAdapter createResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed,
            UserHandle userHandle) {
        UserHandle initialIntentsUserSpace = isLaunchedAsCloneProfile()
                && userHandle.equals(requireAnnotatedUserHandles().personalProfileUserHandle)
                ? requireAnnotatedUserHandles().cloneProfileUserHandle : userHandle;
        return new ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                resolutionList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                mResolverRequest.getIntent(),
                this,
                initialIntentsUserSpace,
                mTargetDataLoader);
    }

    protected final EmptyStateProvider createEmptyStateProvider(
            @Nullable UserHandle workProfileUserHandle) {
        final EmptyStateProvider blockerEmptyStateProvider = createBlockerEmptyStateProvider();

        final EmptyStateProvider workProfileOffEmptyStateProvider =
                new ResolverWorkProfilePausedEmptyStateProvider(this, workProfileUserHandle,
                        mLogic.getWorkProfileAvailabilityManager(),
                        /* onSwitchOnWorkSelectedListener= */
                        () -> {
                            if (mOnSwitchOnWorkSelectedListener != null) {
                                mOnSwitchOnWorkSelectedListener.onSwitchOnWorkSelected();
                            }
                        },
                        getMetricsCategory());

        final EmptyStateProvider noAppsEmptyStateProvider = new NoAppsAvailableEmptyStateProvider(
                this,
                workProfileUserHandle,
                requireAnnotatedUserHandles().personalProfileUserHandle,
                getMetricsCategory(),
                requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch
        );

        // Return composite provider, the order matters (the higher, the more priority)
        return new CompositeEmptyStateProvider(
                blockerEmptyStateProvider,
                workProfileOffEmptyStateProvider,
                noAppsEmptyStateProvider
        );
    }

    private ResolverMultiProfilePagerAdapter
            createResolverMultiProfilePagerAdapterForOneProfile(
                    Intent[] initialIntents,
                    List<ResolveInfo> resolutionList,
                    boolean filterLastUsed) {
        ResolverListAdapter personalAdapter = createResolverListAdapter(
                /* context */ this,
                mResolverRequest.getPayloadIntents(),
                initialIntents,
                resolutionList,
                filterLastUsed,
                /* userHandle */ requireAnnotatedUserHandles().personalProfileUserHandle
        );
        return new ResolverMultiProfilePagerAdapter(
                /* context */ this,
                ImmutableList.of(
                        new TabConfig<>(
                                PROFILE_PERSONAL,
                                mDevicePolicyResources.getPersonalTabLabel(),
                                mDevicePolicyResources.getPersonalTabAccessibilityLabel(),
                                TAB_TAG_PERSONAL,
                                personalAdapter)),
                createEmptyStateProvider(/* workProfileUserHandle= */ null),
                /* workProfileQuietModeChecker= */ () -> false,
                /* defaultProfile= */ PROFILE_PERSONAL,
                /* workProfileUserHandle= */ null,
                requireAnnotatedUserHandles().cloneProfileUserHandle);
    }

    private UserHandle getIntentUser() {
        return Objects.requireNonNullElse(mResolverRequest.getCallingUser(),
                requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch);
    }

    private ResolverMultiProfilePagerAdapter createResolverMultiProfilePagerAdapterForTwoProfiles(
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed) {
        // In the edge case when we have 0 apps in the current profile and >1 apps in the other,
        // the intent resolver is started in the other profile. Since this is the only case when
        // this happens, we check for it here and set the current profile's tab.
        int selectedProfile = getCurrentProfile();
        UserHandle intentUser = getIntentUser();
        if (!requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch.equals(intentUser)) {
            if (requireAnnotatedUserHandles().personalProfileUserHandle.equals(intentUser)) {
                selectedProfile = PROFILE_PERSONAL;
            } else if (requireAnnotatedUserHandles().workProfileUserHandle.equals(intentUser)) {
                selectedProfile = PROFILE_WORK;
            }
        } else {
            int selectedProfileExtra = getSelectedProfileExtra();
            if (selectedProfileExtra != -1) {
                selectedProfile = selectedProfileExtra;
            }
        }
        // We only show the default app for the profile of the current user. The filterLastUsed
        // flag determines whether to show a default app and that app is not shown in the
        // resolver list. So filterLastUsed should be false for the other profile.
        ResolverListAdapter personalAdapter = createResolverListAdapter(
                /* context */ this,
                mResolverRequest.getPayloadIntents(),
                selectedProfile == PROFILE_PERSONAL ? initialIntents : null,
                resolutionList,
                (filterLastUsed && UserHandle.myUserId()
                        == requireAnnotatedUserHandles().personalProfileUserHandle.getIdentifier()),
                /* userHandle */ requireAnnotatedUserHandles().personalProfileUserHandle
        );
        UserHandle workProfileUserHandle = requireAnnotatedUserHandles().workProfileUserHandle;
        ResolverListAdapter workAdapter = createResolverListAdapter(
                /* context */ this,
                mResolverRequest.getPayloadIntents(),
                selectedProfile == PROFILE_WORK ? initialIntents : null,
                resolutionList,
                (filterLastUsed && UserHandle.myUserId()
                        == workProfileUserHandle.getIdentifier()),
                /* userHandle */ workProfileUserHandle
        );
        return new ResolverMultiProfilePagerAdapter(
                /* context */ this,
                ImmutableList.of(
                        new TabConfig<>(
                                PROFILE_PERSONAL,
                                mDevicePolicyResources.getPersonalTabLabel(),
                                mDevicePolicyResources.getPersonalTabAccessibilityLabel(),
                                TAB_TAG_PERSONAL,
                                personalAdapter),
                        new TabConfig<>(
                                PROFILE_WORK,
                                mDevicePolicyResources.getWorkTabLabel(),
                                mDevicePolicyResources.getWorkTabAccessibilityLabel(),
                                TAB_TAG_WORK,
                                workAdapter)),
                createEmptyStateProvider(workProfileUserHandle),
                () -> mLogic.getWorkProfileAvailabilityManager().isQuietModeEnabled(),
                selectedProfile,
                workProfileUserHandle,
                requireAnnotatedUserHandles().cloneProfileUserHandle);
    }

    /**
     * Returns {@link #PROFILE_PERSONAL} or {@link #PROFILE_WORK} if the {@link
     * #EXTRA_SELECTED_PROFILE} extra was supplied, or {@code -1} if no extra was supplied.
     */
    final int getSelectedProfileExtra() {
        Profile.Type selected = mResolverRequest.getSelectedProfile();
        if (selected == null) {
            return -1;
        }
        switch (selected) {
            case PERSONAL: return PROFILE_PERSONAL;
            case WORK: return PROFILE_WORK;
            default: return -1;
        }
    }

    protected final @ProfileType int getCurrentProfile() {
        UserHandle launchUser = requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch;
        UserHandle personalUser = requireAnnotatedUserHandles().personalProfileUserHandle;
        return launchUser.equals(personalUser) ? PROFILE_PERSONAL : PROFILE_WORK;
    }

    private AnnotatedUserHandles requireAnnotatedUserHandles() {
        return requireNonNull(mLogic.getAnnotatedUserHandles());
    }

    private boolean hasWorkProfile() {
        return requireAnnotatedUserHandles().workProfileUserHandle != null;
    }

    private boolean hasCloneProfile() {
        return requireAnnotatedUserHandles().cloneProfileUserHandle != null;
    }

    protected final boolean isLaunchedAsCloneProfile() {
        UserHandle launchUser = requireAnnotatedUserHandles().userHandleSharesheetLaunchedAs;
        UserHandle cloneUser = requireAnnotatedUserHandles().cloneProfileUserHandle;
        return hasCloneProfile() && launchUser.equals(cloneUser);
    }

    private void updateIntentPickerPaddings() {
        View titleCont = findViewById(com.android.internal.R.id.title_container);
        titleCont.setPadding(
                titleCont.getPaddingLeft(),
                titleCont.getPaddingTop(),
                titleCont.getPaddingRight(),
                getResources().getDimensionPixelSize(R.dimen.resolver_title_padding_bottom));
        View buttonBar = findViewById(com.android.internal.R.id.button_bar);
        buttonBar.setPadding(
                buttonBar.getPaddingLeft(),
                getResources().getDimensionPixelSize(R.dimen.resolver_button_bar_spacing),
                buttonBar.getPaddingRight(),
                getResources().getDimensionPixelSize(R.dimen.resolver_button_bar_spacing));
    }

    private void maybeLogCrossProfileTargetLaunch(TargetInfo cti, UserHandle currentUserHandle) {
        if (!hasWorkProfile() || currentUserHandle.equals(getUser())) {
            return;
        }
        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RESOLVER_CROSS_PROFILE_TARGET_OPENED)
                .setBoolean(
                        currentUserHandle.equals(
                                requireAnnotatedUserHandles().personalProfileUserHandle))
                .setStrings(getMetricsCategory(),
                        cti.isInDirectShareMetricsCategory() ? "direct_share" : "other_target")
                .write();
    }

    @Override // ResolverListCommunicator
    public final void sendVoiceChoicesIfNeeded() {
        if (!isVoiceInteraction()) {
            // Clearly not needed.
            return;
        }

        int count = mMultiProfilePagerAdapter.getActiveListAdapter().getCount();
        final Option[] options = new Option[count];
        for (int i = 0; i < options.length; i++) {
            TargetInfo target = mMultiProfilePagerAdapter.getActiveListAdapter().getItem(i);
            if (target == null) {
                // If this occurs, a new set of targets is being loaded. Let that complete,
                // and have the next call to send voice choices proceed instead.
                return;
            }
            options[i] = optionForChooserTarget(target, i);
        }

        mPickOptionRequest = new PickTargetOptionRequest(
                new Prompt(getTitle()), options, null);
        getVoiceInteractor().submitRequest(mPickOptionRequest);
    }

    final Option optionForChooserTarget(TargetInfo target, int index) {
        return new Option(getOrLoadDisplayLabel(target), index);
    }

    protected final CharSequence getTitleForAction(Intent intent, int defaultTitleRes) {
        final ActionTitle title = mResolvingHome
                ? ActionTitle.HOME
                : ActionTitle.forAction(intent.getAction());

        // While there may already be a filtered item, we can only use it in the title if the list
        // is already sorted and all information relevant to it is already in the list.
        final boolean named =
                mMultiProfilePagerAdapter.getActiveListAdapter().getFilteredPosition() >= 0;
        if (title == ActionTitle.DEFAULT && defaultTitleRes != 0) {
            return getString(defaultTitleRes);
        } else {
            return named
                    ? getString(
                            title.namedTitleRes,
                            getOrLoadDisplayLabel(
                                    mMultiProfilePagerAdapter
                                        .getActiveListAdapter().getFilteredItem()))
                    : getString(title.titleRes);
        }
    }

    @Override
    protected final void onRestart() {
        super.onRestart();
        if (!mRegistered) {
            mPersonalPackageMonitor.register(
                    this,
                    getMainLooper(),
                    requireAnnotatedUserHandles().personalProfileUserHandle,
                    false);
            if (hasWorkProfile()) {
                if (mWorkPackageMonitor == null) {
                    mWorkPackageMonitor = createPackageMonitor(
                            mMultiProfilePagerAdapter.getWorkListAdapter());
                }
                mWorkPackageMonitor.register(
                        this,
                        getMainLooper(),
                        requireAnnotatedUserHandles().workProfileUserHandle,
                        false);
            }
            mRegistered = true;
        }
        WorkProfileAvailabilityManager workProfileAvailabilityManager =
                mLogic.getWorkProfileAvailabilityManager();
        if (hasWorkProfile() && workProfileAvailabilityManager.isWaitingToEnableWorkProfile()) {
            if (workProfileAvailabilityManager.isQuietModeEnabled()) {
                workProfileAvailabilityManager.markWorkProfileEnabledBroadcastReceived();
            }
        }
        mMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
    }

    @Override
    protected final void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        if (viewPager != null) {
            outState.putInt(LAST_SHOWN_TAB_KEY, viewPager.getCurrentItem());
        }
    }

    @Override
    protected final void onStart() {
        super.onStart();
        this.getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        if (hasWorkProfile()) {
            mLogic.getWorkProfileAvailabilityManager().registerWorkProfileStateReceiver(this);
        }
    }

    private boolean hasManagedProfile() {
        UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
        if (userManager == null) {
            return false;
        }

        try {
            List<UserInfo> profiles = userManager.getProfiles(getUserId());
            for (UserInfo userInfo : profiles) {
                if (userInfo != null && userInfo.isManagedProfile()) {
                    return true;
                }
            }
        } catch (SecurityException e) {
            return false;
        }
        return false;
    }

    private boolean supportsManagedProfiles(ResolveInfo resolveInfo) {
        try {
            ApplicationInfo appInfo = mPackageManager.getApplicationInfo(
                    resolveInfo.activityInfo.packageName, 0 /* default flags */);
            return appInfo.targetSdkVersion >= Build.VERSION_CODES.LOLLIPOP;
        } catch (NameNotFoundException e) {
            return false;
        }
    }

    private void setAlwaysButtonEnabled(boolean hasValidSelection, int checkedPos,
            boolean filtered) {
        if (!mMultiProfilePagerAdapter.getCurrentUserHandle().equals(getUser())) {
            // Never allow the inactive profile to always open an app.
            mAlwaysButton.setEnabled(false);
            return;
        }
        // In case of clonedProfile being active, we do not allow the 'Always' option in the
        // disambiguation dialog of Personal Profile as the package manager cannot distinguish
        // between cross-profile preferred activities.
        if (hasCloneProfile()
                && (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)) {
            mAlwaysButton.setEnabled(false);
            return;
        }
        boolean enabled = false;
        ResolveInfo ri = null;
        if (hasValidSelection) {
            ri = mMultiProfilePagerAdapter.getActiveListAdapter()
                    .resolveInfoForPosition(checkedPos, filtered);
            if (ri == null) {
                Log.e(TAG, "Invalid position supplied to setAlwaysButtonEnabled");
                return;
            } else if (ri.targetUserId != UserHandle.USER_CURRENT) {
                Log.e(TAG, "Attempted to set selection to resolve info for another user");
                return;
            } else {
                enabled = true;
            }

            mAlwaysButton.setText(getResources()
                    .getString(R.string.activity_resolver_use_always));
        }

        if (ri != null) {
            ActivityInfo activityInfo = ri.activityInfo;

            boolean hasRecordPermission = mPackageManager
                    .checkPermission(android.Manifest.permission.RECORD_AUDIO,
                            activityInfo.packageName)
                            == PackageManager.PERMISSION_GRANTED;

            if (!hasRecordPermission) {
                // OK, we know the record permission, is this a capture device
                boolean hasAudioCapture = mResolverRequest.isAudioCaptureDevice();
                enabled = !hasAudioCapture;
            }
        }
        mAlwaysButton.setEnabled(enabled);
    }

    @Override // ResolverListCommunicator
    public final void onPostListReady(ResolverListAdapter listAdapter, boolean doPostProcessing,
            boolean rebuildCompleted) {
        if (isAutolaunching()) {
            return;
        }
        mMultiProfilePagerAdapter.setUseLayoutWithDefault(useLayoutWithDefault());

        if (mMultiProfilePagerAdapter.shouldShowEmptyStateScreen(listAdapter)) {
            mMultiProfilePagerAdapter.showEmptyResolverListEmptyState(listAdapter);
        } else {
            mMultiProfilePagerAdapter.showListView(listAdapter);
        }
        // showEmptyResolverListEmptyState can mark the tab as loaded,
        // which is a precondition for auto launching
        if (rebuildCompleted && maybeAutolaunchActivity()) {
            return;
        }
        if (doPostProcessing) {
            maybeCreateHeader(listAdapter);
            resetButtonBar();
            onListRebuilt(listAdapter, rebuildCompleted);
        }
    }

    /** Start the activity specified by the {@link TargetInfo}.*/
    public final void safelyStartActivity(TargetInfo cti) {
        // In case cloned apps are present, we would want to start those apps in cloned user
        // space, which will not be same as the adapter's userHandle. resolveInfo.userHandle
        // identifies the correct user space in such cases.
        UserHandle activityUserHandle = cti.getResolveInfo().userHandle;
        safelyStartActivityAsUser(cti, activityUserHandle, null);
    }

    /**
     * Start activity as a fixed user handle.
     * @param cti TargetInfo to be launched.
     * @param user User to launch this activity as.
     */
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PROTECTED)
    public final void safelyStartActivityAsUser(TargetInfo cti, UserHandle user) {
        safelyStartActivityAsUser(cti, user, null);
    }

    protected final void safelyStartActivityAsUser(
            TargetInfo cti, UserHandle user, @Nullable Bundle options) {
        // We're dispatching intents that might be coming from legacy apps, so
        // don't kill ourselves.
        StrictMode.disableDeathOnFileUriExposure();
        try {
            safelyStartActivityInternal(cti, user, options);
        } finally {
            StrictMode.enableDeathOnFileUriExposure();
        }
    }

    final void showTargetDetails(ResolveInfo ri) {
        Intent in = new Intent().setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
                .setData(Uri.fromParts("package", ri.activityInfo.packageName, null))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_DOCUMENT);
        startActivityAsUser(in, mMultiProfilePagerAdapter.getCurrentUserHandle());
    }

    /**
     * Sets up the content view.
     * @return <code>true</code> if the activity is finishing and creation should halt.
     */
    private boolean configureContentView(TargetDataLoader targetDataLoader) {
        if (mMultiProfilePagerAdapter.getActiveListAdapter() == null) {
            throw new IllegalStateException("mMultiProfilePagerAdapter.getCurrentListAdapter() "
                    + "cannot be null.");
        }
        Trace.beginSection("configureContentView");
        // We partially rebuild the inactive adapter to determine if we should auto launch
        // isTabLoaded will be true here if the empty state screen is shown instead of the list.
        // To date, we really only care about "partially rebuilding" tabs for work and/or personal.
        boolean rebuildCompleted = mMultiProfilePagerAdapter.rebuildTabs(hasWorkProfile());

        if (shouldUseMiniResolver()) {
            configureMiniResolverContent(targetDataLoader);
            Trace.endSection();
            return false;
        }

        if (useLayoutWithDefault()) {
            mLayoutId = R.layout.resolver_list_with_default;
        } else {
            mLayoutId = getLayoutResource();
        }
        setContentView(mLayoutId);
        mMultiProfilePagerAdapter.setupViewPager(findViewById(com.android.internal.R.id.profile_pager));
        boolean result = postRebuildList(rebuildCompleted);
        Trace.endSection();
        return result;
    }

    /**
     * Mini resolver is shown when the user is choosing between browser[s] in this profile and a
     * single app in the other profile (see shouldUseMiniResolver()). It shows the single app icon
     * and asks the user if they'd like to open that cross-profile app or use the in-profile
     * browser.
     */
    private void configureMiniResolverContent(TargetDataLoader targetDataLoader) {
        mLayoutId = R.layout.miniresolver;
        setContentView(mLayoutId);

        boolean inWorkProfile = getCurrentProfile() == PROFILE_WORK;

        ResolverListAdapter sameProfileAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getPersonalListAdapter()
                : mMultiProfilePagerAdapter.getWorkListAdapter();

        ResolverListAdapter inactiveAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getWorkListAdapter()
                : mMultiProfilePagerAdapter.getPersonalListAdapter();

        DisplayResolveInfo sameProfileResolveInfo = sameProfileAdapter.getFirstDisplayResolveInfo();

        final DisplayResolveInfo otherProfileResolveInfo =
                inactiveAdapter.getFirstDisplayResolveInfo();

        // Load the icon asynchronously
        ImageView icon = findViewById(com.android.internal.R.id.icon);
        targetDataLoader.loadAppTargetIcon(
                otherProfileResolveInfo,
                inactiveAdapter.getUserHandle(),
                (drawable) -> {
                    if (!isDestroyed()) {
                        otherProfileResolveInfo.getDisplayIconHolder().setDisplayIcon(drawable);
                        new ResolverListAdapter.ViewHolder(icon).bindIcon(otherProfileResolveInfo);
                    }
                });

        ((TextView) findViewById(com.android.internal.R.id.open_cross_profile)).setText(
                getResources().getString(
                        inWorkProfile
                                ? R.string.miniresolver_open_in_personal
                                : R.string.miniresolver_open_in_work,
                        getOrLoadDisplayLabel(otherProfileResolveInfo)));
        ((Button) findViewById(com.android.internal.R.id.use_same_profile_browser)).setText(
                inWorkProfile ? R.string.miniresolver_use_work_browser
                        : R.string.miniresolver_use_personal_browser);

        findViewById(com.android.internal.R.id.use_same_profile_browser).setOnClickListener(
                v -> {
                    safelyStartActivity(sameProfileResolveInfo);
                    finish();
                });

        findViewById(com.android.internal.R.id.button_open).setOnClickListener(v -> {
            Intent intent = otherProfileResolveInfo.getResolvedIntent();
            safelyStartActivityAsUser(otherProfileResolveInfo, inactiveAdapter.getUserHandle());
            finish();
        });
    }

    private boolean isTwoPagePersonalAndWorkConfiguration() {
        return (mMultiProfilePagerAdapter.getCount() == 2)
                && mMultiProfilePagerAdapter.hasPageForProfile(PROFILE_PERSONAL)
                && mMultiProfilePagerAdapter.hasPageForProfile(PROFILE_WORK);
    }

    @VisibleForTesting
    protected void safelyStartActivityInternal(
            TargetInfo cti, UserHandle user, @Nullable Bundle options) {
        // If the target is suspended, the activity will not be successfully launched.
        // Do not unregister from package manager updates in this case
        if (!cti.isSuspended() && mRegistered) {
            if (mPersonalPackageMonitor != null) {
                mPersonalPackageMonitor.unregister();
            }
            if (mWorkPackageMonitor != null) {
                mWorkPackageMonitor.unregister();
            }
            mRegistered = false;
        }
        // If needed, show that intent is forwarded
        // from managed profile to owner or other way around.
        String profileSwitchMessage =
                mIntentForwarding.forwardMessageFor(mResolverRequest.getIntent());
        if (profileSwitchMessage != null) {
            Toast.makeText(this, profileSwitchMessage, Toast.LENGTH_LONG).show();
        }
        try {
            if (cti.startAsCaller(this, options, user.getIdentifier())) {
                maybeLogCrossProfileTargetLaunch(cti, user);
            }
        } catch (RuntimeException e) {
            Slog.wtf(TAG,
                    "Unable to launch as uid " + mActivityModel.getLaunchedFromUid()
                    + " package " + getLaunchedFromPackage() + ", while running in "
                    + ActivityThread.currentProcessName(), e);
        }
    }

    /**
     * Finishing procedures to be performed after the list has been rebuilt.
     * @param rebuildCompleted
     * @return <code>true</code> if the activity is finishing and creation should halt.
     */
    final boolean postRebuildListInternal(boolean rebuildCompleted) {
        int count = mMultiProfilePagerAdapter.getActiveListAdapter().getUnfilteredCount();

        // We only rebuild asynchronously when we have multiple elements to sort. In the case where
        // we're already done, we can check if we should auto-launch immediately.
        if (rebuildCompleted && maybeAutolaunchActivity()) {
            return true;
        }

        setupViewVisibilities();

        if (hasWorkProfile()) {
            setupProfileTabs();
        }

        return false;
    }

    /**
     * Mini resolver should be used when all of the following are true:
     * 1. This is the intent picker (ResolverActivity).
     * 2. This profile only has web browser matches.
     * 3. The other profile has a single non-browser match.
     */
    private boolean shouldUseMiniResolver() {
        if (!isTwoPagePersonalAndWorkConfiguration()) {
            return false;
        }

        ResolverListAdapter sameProfileAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getPersonalListAdapter()
                : mMultiProfilePagerAdapter.getWorkListAdapter();

        ResolverListAdapter otherProfileAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getWorkListAdapter()
                : mMultiProfilePagerAdapter.getPersonalListAdapter();

        if (sameProfileAdapter.getDisplayResolveInfoCount() == 0) {
            Log.d(TAG, "No targets in the current profile");
            return false;
        }

        if (otherProfileAdapter.getDisplayResolveInfoCount() != 1) {
            Log.d(TAG, "Other-profile count: " + otherProfileAdapter.getDisplayResolveInfoCount());
            return false;
        }

        if (otherProfileAdapter.allResolveInfosHandleAllWebDataUri()) {
            Log.d(TAG, "Other profile is a web browser");
            return false;
        }

        if (!sameProfileAdapter.allResolveInfosHandleAllWebDataUri()) {
            Log.d(TAG, "Non-browser found in this profile");
            return false;
        }

        return true;
    }

    private boolean maybeAutolaunchIfSingleTarget() {
        int count = mMultiProfilePagerAdapter.getActiveListAdapter().getUnfilteredCount();
        if (count != 1) {
            return false;
        }

        if (mMultiProfilePagerAdapter.getActiveListAdapter().getOtherProfile() != null) {
            return false;
        }

        // Only one target, so we're a candidate to auto-launch!
        final TargetInfo target = mMultiProfilePagerAdapter.getActiveListAdapter()
                .targetInfoForPosition(0, false);
        if (shouldAutoLaunchSingleChoice(target)) {
            safelyStartActivity(target);
            finish();
            return true;
        }
        return false;
    }

    /**
     * When we have just a personal and a work profile, we auto launch in the following scenario:
     * - There is 1 resolved target on each profile
     * - That target is the same app on both profiles
     * - The target app has permission to communicate cross profiles
     * - The target app has declared it supports cross-profile communication via manifest metadata
     */
    private boolean maybeAutolaunchIfCrossProfileSupported() {
        if (!isTwoPagePersonalAndWorkConfiguration()) {
            return false;
        }

        ResolverListAdapter activeListAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getPersonalListAdapter()
                : mMultiProfilePagerAdapter.getWorkListAdapter();

        ResolverListAdapter inactiveListAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                ? mMultiProfilePagerAdapter.getWorkListAdapter()
                : mMultiProfilePagerAdapter.getPersonalListAdapter();

        if (!activeListAdapter.isTabLoaded() || !inactiveListAdapter.isTabLoaded()) {
            return false;
        }

        if ((activeListAdapter.getUnfilteredCount() != 1)
                || (inactiveListAdapter.getUnfilteredCount() != 1)) {
            return false;
        }

        TargetInfo activeProfileTarget = activeListAdapter.targetInfoForPosition(0, false);
        TargetInfo inactiveProfileTarget = inactiveListAdapter.targetInfoForPosition(0, false);
        if (!Objects.equals(
                activeProfileTarget.getResolvedComponentName(),
                inactiveProfileTarget.getResolvedComponentName())) {
            return false;
        }

        if (!shouldAutoLaunchSingleChoice(activeProfileTarget)) {
            return false;
        }

        String packageName = activeProfileTarget.getResolvedComponentName().getPackageName();
        if (!mIntentForwarding.canAppInteractAcrossProfiles(this, packageName)) {
            return false;
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RESOLVER_AUTOLAUNCH_CROSS_PROFILE_TARGET)
                .setBoolean(activeListAdapter.getUserHandle()
                        .equals(requireAnnotatedUserHandles().personalProfileUserHandle))
                .setStrings(getMetricsCategory())
                .write();
        safelyStartActivity(activeProfileTarget);
        finish();
        return true;
    }

    private boolean isAutolaunching() {
        return !mRegistered && isFinishing();
    }

    /**
     * @return {@code true} if a resolved target is autolaunched, otherwise {@code false}
     */
    private boolean maybeAutolaunchActivity() {
        if (!isTwoPagePersonalAndWorkConfiguration()) {
            return false;
        }

        ResolverListAdapter activeListAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                        ? mMultiProfilePagerAdapter.getPersonalListAdapter()
                        : mMultiProfilePagerAdapter.getWorkListAdapter();

        ResolverListAdapter inactiveListAdapter =
                (mMultiProfilePagerAdapter.getActiveProfile() == PROFILE_PERSONAL)
                        ? mMultiProfilePagerAdapter.getWorkListAdapter()
                        : mMultiProfilePagerAdapter.getPersonalListAdapter();

        if (!activeListAdapter.isTabLoaded() || !inactiveListAdapter.isTabLoaded()) {
            return false;
        }

        if ((activeListAdapter.getUnfilteredCount() != 1)
                || (inactiveListAdapter.getUnfilteredCount() != 1)) {
            return false;
        }

        TargetInfo activeProfileTarget = activeListAdapter.targetInfoForPosition(0, false);
        TargetInfo inactiveProfileTarget = inactiveListAdapter.targetInfoForPosition(0, false);
        if (!Objects.equals(
                activeProfileTarget.getResolvedComponentName(),
                inactiveProfileTarget.getResolvedComponentName())) {
            return false;
        }

        if (!shouldAutoLaunchSingleChoice(activeProfileTarget)) {
            return false;
        }

        String packageName = activeProfileTarget.getResolvedComponentName().getPackageName();
        if (!mIntentForwarding.canAppInteractAcrossProfiles(this, packageName)) {
            return false;
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RESOLVER_AUTOLAUNCH_CROSS_PROFILE_TARGET)
                .setBoolean(activeListAdapter.getUserHandle()
                        .equals(requireAnnotatedUserHandles().personalProfileUserHandle))
                .setStrings(getMetricsCategory())
                .write();
        safelyStartActivity(activeProfileTarget);
        finish();
        return true;
    }

    private void maybeHideDivider() {
        final View divider = findViewById(com.android.internal.R.id.divider);
        if (divider == null) {
            return;
        }
        divider.setVisibility(View.GONE);
    }

    private void resetCheckedItem() {
        mLastSelected = ListView.INVALID_POSITION;
        ((ResolverMultiProfilePagerAdapter) mMultiProfilePagerAdapter)
                .clearCheckedItemsInInactiveProfiles();
    }

    private void setupViewVisibilities() {
        ResolverListAdapter activeListAdapter = mMultiProfilePagerAdapter.getActiveListAdapter();
        if (!mMultiProfilePagerAdapter.shouldShowEmptyStateScreen(activeListAdapter)) {
            addUseDifferentAppLabelIfNecessary(activeListAdapter);
        }
    }

    /**
     * Updates the button bar container {@code ignoreOffset} layout param.
     * <p>Setting this to {@code true} means that the button bar will be glued to the bottom of
     * the screen.
     */
    private void setButtonBarIgnoreOffset(boolean ignoreOffset) {
        View buttonBarContainer = findViewById(com.android.internal.R.id.button_bar_container);
        if (buttonBarContainer != null) {
            ResolverDrawerLayout.LayoutParams layoutParams =
                    (ResolverDrawerLayout.LayoutParams) buttonBarContainer.getLayoutParams();
            layoutParams.ignoreOffset = ignoreOffset;
            buttonBarContainer.setLayoutParams(layoutParams);
        }
    }

    private void setupAdapterListView(ListView listView, ItemClickListener listener) {
        listView.setOnItemClickListener(listener);
        listView.setOnItemLongClickListener(listener);
        listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
    }

    /**
     * Configure the area above the app selection list (title, content preview, etc).
     */
    private void maybeCreateHeader(ResolverListAdapter listAdapter) {
        if (mHeaderCreatorUser != null
                && !listAdapter.getUserHandle().equals(mHeaderCreatorUser)) {
            return;
        }
        if (!hasWorkProfile()
                && listAdapter.getCount() == 0 && listAdapter.getPlaceholderCount() == 0) {
            final TextView titleView = findViewById(com.android.internal.R.id.title);
            if (titleView != null) {
                titleView.setVisibility(View.GONE);
            }
        }

        CharSequence title = mResolverRequest.getTitle() != null
                ? mResolverRequest.getTitle()
                : getTitleForAction(mResolverRequest.getIntent(), 0);

        if (!TextUtils.isEmpty(title)) {
            final TextView titleView = findViewById(com.android.internal.R.id.title);
            if (titleView != null) {
                titleView.setText(title);
            }
            setTitle(title);
        }

        final ImageView iconView = findViewById(com.android.internal.R.id.icon);
        if (iconView != null) {
            listAdapter.loadFilteredItemIconTaskAsync(iconView);
        }
        mHeaderCreatorUser = listAdapter.getUserHandle();
    }

    private void resetAlwaysOrOnceButtonBar() {
        // Disable both buttons initially
        setAlwaysButtonEnabled(false, ListView.INVALID_POSITION, false);
        mOnceButton.setEnabled(false);

        int filteredPosition = mMultiProfilePagerAdapter.getActiveListAdapter()
                .getFilteredPosition();
        if (useLayoutWithDefault() && filteredPosition != ListView.INVALID_POSITION) {
            setAlwaysButtonEnabled(true, filteredPosition, false);
            mOnceButton.setEnabled(true);
            // Focus the button if we already have the default option
            mOnceButton.requestFocus();
            return;
        }

        // When the items load in, if an item was already selected, enable the buttons
        ListView currentAdapterView = (ListView) mMultiProfilePagerAdapter.getActiveAdapterView();
        if (currentAdapterView != null
                && currentAdapterView.getCheckedItemPosition() != ListView.INVALID_POSITION) {
            setAlwaysButtonEnabled(true, currentAdapterView.getCheckedItemPosition(), true);
            mOnceButton.setEnabled(true);
        }
    }

    @Override // ResolverListCommunicator
    public final boolean useLayoutWithDefault() {
        // We only use the default app layout when the profile of the active user has a
        // filtered item. We always show the same default app even in the inactive user profile.
        return mMultiProfilePagerAdapter.getListAdapterForUserHandle(
                requireAnnotatedUserHandles().tabOwnerUserHandleForLaunch
        ).hasFilteredItem();
    }

    final class ItemClickListener implements AdapterView.OnItemClickListener,
            AdapterView.OnItemLongClickListener {
        @Override
        public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
            final ListView listView = parent instanceof ListView ? (ListView) parent : null;
            if (listView != null) {
                position -= listView.getHeaderViewsCount();
            }
            if (position < 0) {
                // Header views don't count.
                return;
            }
            // If we're still loading, we can't yet enable the buttons.
            if (mMultiProfilePagerAdapter.getActiveListAdapter()
                    .resolveInfoForPosition(position, true) == null) {
                return;
            }
            ListView currentAdapterView =
                    (ListView) mMultiProfilePagerAdapter.getActiveAdapterView();
            final int checkedPos = currentAdapterView.getCheckedItemPosition();
            final boolean hasValidSelection = checkedPos != ListView.INVALID_POSITION;
            if (!useLayoutWithDefault()
                    && (!hasValidSelection || mLastSelected != checkedPos)
                    && mAlwaysButton != null) {
                setAlwaysButtonEnabled(hasValidSelection, checkedPos, true);
                mOnceButton.setEnabled(hasValidSelection);
                if (hasValidSelection) {
                    currentAdapterView.smoothScrollToPosition(checkedPos);
                    mOnceButton.requestFocus();
                }
                mLastSelected = checkedPos;
            } else {
                startSelected(position, false, true);
            }
        }

        @Override
        public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
            final ListView listView = parent instanceof ListView ? (ListView) parent : null;
            if (listView != null) {
                position -= listView.getHeaderViewsCount();
            }
            if (position < 0) {
                // Header views don't count.
                return false;
            }
            ResolveInfo ri = mMultiProfilePagerAdapter.getActiveListAdapter()
                    .resolveInfoForPosition(position, true);
            showTargetDetails(ri);
            return true;
        }

    }

    private void setupProfileTabs() {
        maybeHideDivider();

        TabHost tabHost = findViewById(com.android.internal.R.id.profile_tabhost);
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);

        mMultiProfilePagerAdapter.setupProfileTabs(
                getLayoutInflater(),
                tabHost,
                viewPager,
                R.layout.resolver_profile_tab_button,
                com.android.internal.R.id.profile_pager,
                () -> onProfileTabSelected(viewPager.getCurrentItem()),
                new OnProfileSelectedListener() {
                    @Override
                    public void onProfilePageSelected(@ProfileType int profileId, int pageNumber) {
                        resetButtonBar();
                        resetCheckedItem();
                    }

                    @Override
                    public void onProfilePageStateChanged(int state) {}
                });
        mOnSwitchOnWorkSelectedListener = () -> {
            final View workTab =
                    tabHost.getTabWidget().getChildAt(
                            mMultiProfilePagerAdapter.getPageNumberForProfile(PROFILE_WORK));
            workTab.setFocusable(true);
            workTab.setFocusableInTouchMode(true);
            workTab.requestFocus();
        };
    }

    static final class PickTargetOptionRequest extends PickOptionRequest {
        public PickTargetOptionRequest(@Nullable Prompt prompt, Option[] options,
                @Nullable Bundle extras) {
            super(prompt, options, extras);
        }

        @Override
        public void onCancel() {
            super.onCancel();
            final ResolverActivity ra = (ResolverActivity) getActivity();
            if (ra != null) {
                ra.mPickOptionRequest = null;
                ra.finish();
            }
        }

        @Override
        public void onPickOptionResult(boolean finished, Option[] selections, Bundle result) {
            super.onPickOptionResult(finished, selections, result);
            if (selections.length != 1) {
                // TODO In a better world we would filter the UI presented here and let the
                // user refine. Maybe later.
                return;
            }

            final ResolverActivity ra = (ResolverActivity) getActivity();
            if (ra != null) {
                final TargetInfo ti = ra.mMultiProfilePagerAdapter.getActiveListAdapter()
                        .getItem(selections[0].getIndex());
                if (ra.onTargetSelected(ti, false)) {
                    ra.mPickOptionRequest = null;
                    ra.finish();
                }
            }
        }
    }
    /**
     * Returns the {@link UserHandle} to use when querying resolutions for intents in a
     * {@link ResolverListController} configured for the provided {@code userHandle}.
     */
    protected final UserHandle getQueryIntentsUser(UserHandle userHandle) {
        return requireAnnotatedUserHandles().getQueryIntentsUser(userHandle);
    }

    /**
     * Returns the {@link List} of {@link UserHandle} to pass on to the
     * {@link ResolverRankerServiceResolverComparator} as per the provided {@code userHandle}.
     */
    @VisibleForTesting(visibility = PROTECTED)
    public final List<UserHandle> getResolverRankerServiceUserHandleList(UserHandle userHandle) {
        return getResolverRankerServiceUserHandleListInternal(userHandle);
    }

    @VisibleForTesting
    protected List<UserHandle> getResolverRankerServiceUserHandleListInternal(
            UserHandle userHandle) {
        List<UserHandle> userList = new ArrayList<>();
        userList.add(userHandle);
        // Add clonedProfileUserHandle to the list only if we are:
        // a. Building the Personal Tab.
        // b. CloneProfile exists on the device.
        if (userHandle.equals(requireAnnotatedUserHandles().personalProfileUserHandle)
                && hasCloneProfile()) {
            userList.add(requireAnnotatedUserHandles().cloneProfileUserHandle);
        }
        return userList;
    }

    private CharSequence getOrLoadDisplayLabel(TargetInfo info) {
        if (info.isDisplayResolveInfo()) {
            mTargetDataLoader.getOrLoadLabel((DisplayResolveInfo) info);
        }
        CharSequence displayLabel = info.getDisplayLabel();
        return displayLabel == null ? "" : displayLabel;
    }
}
