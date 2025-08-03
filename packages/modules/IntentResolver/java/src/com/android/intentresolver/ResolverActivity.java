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

import static android.Manifest.permission.INTERACT_ACROSS_PROFILES;
import static android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.FORWARD_INTENT_TO_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_PERSONAL;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CANT_ACCESS_WORK;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_CROSS_PROFILE_BLOCKED_TITLE;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_PERSONAL_TAB_ACCESSIBILITY;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_PROFILE_NOT_SUPPORTED;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB;
import static android.app.admin.DevicePolicyResources.Strings.Core.RESOLVER_WORK_TAB_ACCESSIBILITY;
import static android.content.Intent.FLAG_ACTIVITY_LAUNCH_ADJACENT;
import static android.content.Intent.FLAG_ACTIVITY_NEW_TASK;
import static android.content.PermissionChecker.PID_UNKNOWN;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_PERSONAL;
import static android.stats.devicepolicy.nano.DevicePolicyEnums.RESOLVER_EMPTY_STATE_NO_SHARING_TO_WORK;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.internal.annotations.VisibleForTesting.Visibility.PROTECTED;

import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityThread;
import android.app.VoiceInteractor.PickOptionRequest;
import android.app.VoiceInteractor.PickOptionRequest.Option;
import android.app.VoiceInteractor.Prompt;
import android.app.admin.DevicePolicyEventLogger;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.PermissionChecker;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.ResolveInfo;
import android.content.pm.UserInfo;
import android.content.res.Configuration;
import android.content.res.TypedArray;
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
import android.provider.MediaStore;
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
import android.widget.TabWidget;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.annotation.UiThread;
import androidx.fragment.app.FragmentActivity;
import androidx.viewpager.widget.ViewPager;

import com.android.intentresolver.MultiProfilePagerAdapter.MyUserIdProvider;
import com.android.intentresolver.MultiProfilePagerAdapter.OnSwitchOnWorkSelectedListener;
import com.android.intentresolver.MultiProfilePagerAdapter.Profile;
import com.android.intentresolver.chooser.DisplayResolveInfo;
import com.android.intentresolver.chooser.TargetInfo;
import com.android.intentresolver.emptystate.CompositeEmptyStateProvider;
import com.android.intentresolver.emptystate.CrossProfileIntentsChecker;
import com.android.intentresolver.emptystate.EmptyState;
import com.android.intentresolver.emptystate.EmptyStateProvider;
import com.android.intentresolver.emptystate.NoAppsAvailableEmptyStateProvider;
import com.android.intentresolver.emptystate.NoCrossProfileEmptyStateProvider;
import com.android.intentresolver.emptystate.NoCrossProfileEmptyStateProvider.DevicePolicyBlockerEmptyState;
import com.android.intentresolver.emptystate.WorkProfilePausedEmptyStateProvider;
import com.android.intentresolver.icons.DefaultTargetDataLoader;
import com.android.intentresolver.icons.TargetDataLoader;
import com.android.intentresolver.model.ResolverRankerServiceResolverComparator;
import com.android.intentresolver.widget.ResolverDrawerLayout;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.content.PackageMonitor;
import com.android.internal.logging.MetricsLogger;
import com.android.internal.logging.nano.MetricsProto;
import com.android.internal.util.LatencyTracker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.function.Supplier;

/**
 * This is a copy of ResolverActivity to support IntentResolver's ChooserActivity. This code is
 * *not* the resolver that is actually triggered by the system right now (you want
 * frameworks/base/core/java/com/android/internal/app/ResolverActivity.java for that), the full
 * migration is not complete.
 */
@UiThread
public class ResolverActivity extends FragmentActivity implements
        ResolverListAdapter.ResolverListCommunicator {

    public ResolverActivity() {
        mIsIntentPicker = getClass().equals(ResolverActivity.class);
    }

    protected ResolverActivity(boolean isIntentPicker) {
        mIsIntentPicker = isIntentPicker;
    }

    /**
     * Whether to enable a launch mode that is safe to use when forwarding intents received from
     * applications and running in system processes.  This mode uses Activity.startActivityAsCaller
     * instead of the normal Activity.startActivity for launching the activity selected
     * by the user.
     */
    private boolean mSafeForwardingMode;

    private Button mAlwaysButton;
    private Button mOnceButton;
    protected View mProfileView;
    private int mLastSelected = AbsListView.INVALID_POSITION;
    private boolean mResolvingHome = false;
    private String mProfileSwitchMessage;
    private int mLayoutId;
    @VisibleForTesting
    protected final ArrayList<Intent> mIntents = new ArrayList<>();
    private PickTargetOptionRequest mPickOptionRequest;
    private String mReferrerPackage;
    private CharSequence mTitle;
    private int mDefaultTitleResId;
    // Expected to be true if this object is ResolverActivity or is ResolverWrapperActivity.
    private final boolean mIsIntentPicker;

    // Whether or not this activity supports choosing a default handler for the intent.
    @VisibleForTesting
    protected boolean mSupportsAlwaysUseOption;
    protected ResolverDrawerLayout mResolverDrawerLayout;
    protected PackageManager mPm;

    private static final String TAG = "ResolverActivity";
    private static final boolean DEBUG = false;
    private static final String LAST_SHOWN_TAB_KEY = "last_shown_tab_key";

    private boolean mRegistered;

    protected Insets mSystemWindowInsets = null;
    private Space mFooterSpacer = null;

    /** See {@link #setRetainInOnStop}. */
    private boolean mRetainInOnStop;

    protected static final String METRICS_CATEGORY_RESOLVER = "intent_resolver";
    protected static final String METRICS_CATEGORY_CHOOSER = "intent_chooser";

    /** Tracks if we should ignore future broadcasts telling us the work profile is enabled */
    private boolean mWorkProfileHasBeenEnabled = false;

    private static final String TAB_TAG_PERSONAL = "personal";
    private static final String TAB_TAG_WORK = "work";

    private PackageMonitor mPersonalPackageMonitor;
    private PackageMonitor mWorkPackageMonitor;

    private TargetDataLoader mTargetDataLoader;

    @VisibleForTesting
    protected MultiProfilePagerAdapter mMultiProfilePagerAdapter;

    protected WorkProfileAvailabilityManager mWorkProfileAvailability;

    // Intent extra for connected audio devices
    public static final String EXTRA_IS_AUDIO_CAPTURE_DEVICE = "is_audio_capture_device";

    /**
     * Integer extra to indicate which profile should be automatically selected.
     * <p>Can only be used if there is a work profile.
     * <p>Possible values can be either {@link #PROFILE_PERSONAL} or {@link #PROFILE_WORK}.
     */
    protected static final String EXTRA_SELECTED_PROFILE =
            "com.android.internal.app.ResolverActivity.EXTRA_SELECTED_PROFILE";

    /**
     * {@link UserHandle} extra to indicate the user of the user that the starting intent
     * originated from.
     * <p>This is not necessarily the same as {@link #getUserId()} or {@link UserHandle#myUserId()},
     * as there are edge cases when the intent resolver is launched in the other profile.
     * For example, when we have 0 resolved apps in current profile and multiple resolved
     * apps in the other profile, opening a link from the current profile launches the intent
     * resolver in the other one. b/148536209 for more info.
     */
    static final String EXTRA_CALLING_USER =
            "com.android.internal.app.ResolverActivity.EXTRA_CALLING_USER";

    protected static final int PROFILE_PERSONAL = MultiProfilePagerAdapter.PROFILE_PERSONAL;
    protected static final int PROFILE_WORK = MultiProfilePagerAdapter.PROFILE_WORK;

    private UserHandle mHeaderCreatorUser;

    // User handle annotations are lazy-initialized to ensure that they're computed exactly once
    // (even though they can't be computed prior to activity creation).
    // TODO: use a less ad-hoc pattern for lazy initialization (by switching to Dagger or
    // introducing a common `LazySingletonSupplier` API, etc), and/or migrate all dependents to a
    // new component whose lifecycle is limited to the "created" Activity (so that we can just hold
    // the annotations as a `final` ivar, which is a better way to show immutability).
    private Supplier<AnnotatedUserHandles> mLazyAnnotatedUserHandles = () -> {
        final AnnotatedUserHandles result = computeAnnotatedUserHandles();
        mLazyAnnotatedUserHandles = () -> result;
        return result;
    };

    // This method is called exactly once during creation to compute the immutable annotations
    // accessible through the lazy supplier {@link mLazyAnnotatedUserHandles}.
    // TODO: this is only defined so that tests can provide an override that injects fake
    // annotations. Dagger could provide a cleaner model for our testing/injection requirements.
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    protected AnnotatedUserHandles computeAnnotatedUserHandles() {
        return AnnotatedUserHandles.forShareActivity(this);
    }

    @Nullable
    private OnSwitchOnWorkSelectedListener mOnSwitchOnWorkSelectedListener;

    protected final LatencyTracker mLatencyTracker = getLatencyTracker();

    private enum ActionTitle {
        VIEW(Intent.ACTION_VIEW,
                R.string.whichViewApplication,
                R.string.whichViewApplicationNamed,
                R.string.whichViewApplicationLabel),
        EDIT(Intent.ACTION_EDIT,
                R.string.whichEditApplication,
                R.string.whichEditApplicationNamed,
                R.string.whichEditApplicationLabel),
        SEND(Intent.ACTION_SEND,
                R.string.whichSendApplication,
                R.string.whichSendApplicationNamed,
                R.string.whichSendApplicationLabel),
        SENDTO(Intent.ACTION_SENDTO,
                R.string.whichSendToApplication,
                R.string.whichSendToApplicationNamed,
                R.string.whichSendToApplicationLabel),
        SEND_MULTIPLE(Intent.ACTION_SEND_MULTIPLE,
                R.string.whichSendApplication,
                R.string.whichSendApplicationNamed,
                R.string.whichSendApplicationLabel),
        CAPTURE_IMAGE(MediaStore.ACTION_IMAGE_CAPTURE,
                R.string.whichImageCaptureApplication,
                R.string.whichImageCaptureApplicationNamed,
                R.string.whichImageCaptureApplicationLabel),
        DEFAULT(null,
                R.string.whichApplication,
                R.string.whichApplicationNamed,
                R.string.whichApplicationLabel),
        HOME(Intent.ACTION_MAIN,
                R.string.whichHomeApplication,
                R.string.whichHomeApplicationNamed,
                R.string.whichHomeApplicationLabel);

        // titles for layout that deals with http(s) intents
        public static final int BROWSABLE_TITLE_RES = R.string.whichOpenLinksWith;
        public static final int BROWSABLE_HOST_TITLE_RES = R.string.whichOpenHostLinksWith;
        public static final int BROWSABLE_HOST_APP_TITLE_RES = R.string.whichOpenHostLinksWithApp;
        public static final int BROWSABLE_APP_TITLE_RES = R.string.whichOpenLinksWithApp;

        public final String action;
        public final int titleRes;
        public final int namedTitleRes;
        public final @StringRes int labelRes;

        ActionTitle(String action, int titleRes, int namedTitleRes, @StringRes int labelRes) {
            this.action = action;
            this.titleRes = titleRes;
            this.namedTitleRes = namedTitleRes;
            this.labelRes = labelRes;
        }

        public static ActionTitle forAction(String action) {
            for (ActionTitle title : values()) {
                if (title != HOME && action != null && action.equals(title.action)) {
                    return title;
                }
            }
            return DEFAULT;
        }
    }

    protected PackageMonitor createPackageMonitor(ResolverListAdapter listAdapter) {
        return new PackageMonitor() {
            @Override
            public void onSomePackagesChanged() {
                listAdapter.handlePackagesChanged();
                updateProfileViewButton();
            }

            @Override
            public boolean onPackageChanged(String packageName, int uid, String[] components) {
                // We care about all package changes, not just the whole package itself which is
                // default behavior.
                return true;
            }
        };
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Use a specialized prompt when we're handling the 'Home' app startActivity()
        final Intent intent = makeMyIntent();
        final Set<String> categories = intent.getCategories();
        if (Intent.ACTION_MAIN.equals(intent.getAction())
                && categories != null
                && categories.size() == 1
                && categories.contains(Intent.CATEGORY_HOME)) {
            // Note: this field is not set to true in the compatibility version.
            mResolvingHome = true;
        }

        onCreate(
                savedInstanceState,
                intent,
                /* additionalTargets= */ null,
                /* title= */ null,
                /* defaultTitleRes= */ 0,
                /* initialIntents= */ null,
                /* resolutionList= */ null,
                /* supportsAlwaysUseOption= */ true,
                createIconLoader(),
                /* safeForwardingMode= */ true);
    }

    /**
     * Compatibility version for other bundled services that use this overload without
     * a default title resource
     */
    protected void onCreate(
            Bundle savedInstanceState,
            Intent intent,
            CharSequence title,
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean supportsAlwaysUseOption,
            boolean safeForwardingMode) {
        onCreate(
                savedInstanceState,
                intent,
                null,
                title,
                0,
                initialIntents,
                resolutionList,
                supportsAlwaysUseOption,
                createIconLoader(),
                safeForwardingMode);
    }

    protected void onCreate(
            Bundle savedInstanceState,
            Intent intent,
            Intent[] additionalTargets,
            CharSequence title,
            int defaultTitleRes,
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean supportsAlwaysUseOption,
            TargetDataLoader targetDataLoader,
            boolean safeForwardingMode) {
        setTheme(appliedThemeResId());
        super.onCreate(savedInstanceState);

        // Determine whether we should show that intent is forwarded
        // from managed profile to owner or other way around.
        setProfileSwitchMessage(intent.getContentUserHint());

        // Force computation of user handle annotations in order to validate the caller ID. (See the
        // associated TODO comment to explain why this is structured as a lazy computation.)
        AnnotatedUserHandles unusedReferenceToHandles = mLazyAnnotatedUserHandles.get();

        mWorkProfileAvailability = createWorkProfileAvailabilityManager();

        mPm = getPackageManager();

        mReferrerPackage = getReferrerPackageName();

        // The initial intent must come before any other targets that are to be added.
        mIntents.add(0, new Intent(intent));
        if (additionalTargets != null) {
            Collections.addAll(mIntents, additionalTargets);
        }

        mTitle = title;
        mDefaultTitleResId = defaultTitleRes;

        mSupportsAlwaysUseOption = supportsAlwaysUseOption;
        mSafeForwardingMode = safeForwardingMode;
        mTargetDataLoader = targetDataLoader;

        // The last argument of createResolverListAdapter is whether to do special handling
        // of the last used choice to highlight it in the list.  We need to always
        // turn this off when running under voice interaction, since it results in
        // a more complicated UI that the current voice interaction flow is not able
        // to handle. We also turn it off when the work tab is shown to simplify the UX.
        // We also turn it off when clonedProfile is present on the device, because we might have
        // different "last chosen" activities in the different profiles, and PackageManager doesn't
        // provide any more information to help us select between them.
        boolean filterLastUsed = mSupportsAlwaysUseOption && !isVoiceInteraction()
                && !shouldShowTabs() && !hasCloneProfile();
        mMultiProfilePagerAdapter = createMultiProfilePagerAdapter(
                initialIntents, resolutionList, filterLastUsed, targetDataLoader);
        if (configureContentView(targetDataLoader)) {
            return;
        }

        mPersonalPackageMonitor = createPackageMonitor(
                mMultiProfilePagerAdapter.getPersonalListAdapter());
        mPersonalPackageMonitor.register(
                this, getMainLooper(), getAnnotatedUserHandles().personalProfileUserHandle, false);
        if (shouldShowTabs()) {
            mWorkPackageMonitor = createPackageMonitor(
                    mMultiProfilePagerAdapter.getWorkListAdapter());
            mWorkPackageMonitor.register(
                    this, getMainLooper(), getAnnotatedUserHandles().workProfileUserHandle, false);
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

            boolean hasTouchScreen = getPackageManager()
                    .hasSystemFeature(PackageManager.FEATURE_TOUCHSCREEN);

            if (isVoiceInteraction() || !hasTouchScreen) {
                rdl.setCollapsed(false);
            }

            rdl.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_STABLE);
            rdl.setOnApplyWindowInsetsListener(this::onApplyWindowInsets);

            mResolverDrawerLayout = rdl;
        }

        mProfileView = findViewById(com.android.internal.R.id.profile_button);
        if (mProfileView != null) {
            mProfileView.setOnClickListener(this::onProfileClick);
            updateProfileViewButton();
        }

        final Set<String> categories = intent.getCategories();
        MetricsLogger.action(this, mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem()
                ? MetricsProto.MetricsEvent.ACTION_SHOW_APP_DISAMBIG_APP_FEATURED
                : MetricsProto.MetricsEvent.ACTION_SHOW_APP_DISAMBIG_NONE_FEATURED,
                intent.getAction() + ":" + intent.getType() + ":"
                        + (categories != null ? Arrays.toString(categories.toArray()) : ""));
    }

    protected MultiProfilePagerAdapter createMultiProfilePagerAdapter(
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed,
            TargetDataLoader targetDataLoader) {
        MultiProfilePagerAdapter resolverMultiProfilePagerAdapter = null;
        if (shouldShowTabs()) {
            resolverMultiProfilePagerAdapter =
                    createResolverMultiProfilePagerAdapterForTwoProfiles(
                            initialIntents, resolutionList, filterLastUsed, targetDataLoader);
        } else {
            resolverMultiProfilePagerAdapter = createResolverMultiProfilePagerAdapterForOneProfile(
                    initialIntents, resolutionList, filterLastUsed, targetDataLoader);
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
                getAnnotatedUserHandles().personalProfileUserHandle,
                noWorkToPersonalEmptyState,
                noPersonalToWorkEmptyState,
                createCrossProfileIntentsChecker(),
                getAnnotatedUserHandles().tabOwnerUserHandleForLaunch);
    }

    protected int appliedThemeResId() {
        return R.style.Theme_DeviceDefault_Resolver;
    }

    /**
     * Numerous layouts are supported, each with optional ViewGroups.
     * Make sure the inset gets added to the correct View, using
     * a footer for Lists so it can properly scroll under the navbar.
     */
    protected boolean shouldAddFooterView() {
        if (useLayoutWithDefault()) return true;

        View buttonBar = findViewById(com.android.internal.R.id.button_bar);
        if (buttonBar == null || buttonBar.getVisibility() == View.GONE) return true;

        return false;
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
    public void onConfigurationChanged(@NonNull Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        mMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
        if (mIsIntentPicker && shouldShowTabs() && !useLayoutWithDefault()
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
                && !mResolvingHome && !mRetainInOnStop) {
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
        mWorkProfileAvailability.unregisterWorkProfileStateReceiver(this);
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
            Toast.makeText(this,
                    getWorkProfileNotSupportedMsg(
                            ri.activityInfo.loadLabel(getPackageManager()).toString()),
                    Toast.LENGTH_LONG).show();
            return;
        }

        TargetInfo target = mMultiProfilePagerAdapter.getActiveListAdapter()
                .targetInfoForPosition(which, hasIndexBeenFiltered);
        if (target == null) {
            return;
        }
        if (onTargetSelected(target, always)) {
            if (always && mSupportsAlwaysUseOption) {
                MetricsLogger.action(
                        this, MetricsProto.MetricsEvent.ACTION_APP_DISAMBIG_ALWAYS);
            } else if (mSupportsAlwaysUseOption) {
                MetricsLogger.action(
                        this, MetricsProto.MetricsEvent.ACTION_APP_DISAMBIG_JUST_ONCE);
            } else {
                MetricsLogger.action(
                        this, MetricsProto.MetricsEvent.ACTION_APP_DISAMBIG_TAP);
            }
            MetricsLogger.action(this,
                    mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem()
                            ? MetricsProto.MetricsEvent.ACTION_HIDE_APP_DISAMBIG_APP_FEATURED
                            : MetricsProto.MetricsEvent.ACTION_HIDE_APP_DISAMBIG_NONE_FEATURED);
            finish();
        }
    }

    /**
     * Replace me in subclasses!
     */
    @Override // ResolverListCommunicator
    public Intent getReplacementIntent(ActivityInfo aInfo, Intent defIntent) {
        return defIntent;
    }

    protected void onListRebuilt(ResolverListAdapter listAdapter, boolean rebuildCompleted) {
        final ItemClickListener listener = new ItemClickListener();
        setupAdapterListView((ListView) mMultiProfilePagerAdapter.getActiveAdapterView(), listener);
        if (shouldShowTabs() && mIsIntentPicker) {
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

        if (intent != null && (mSupportsAlwaysUseOption
                || mMultiProfilePagerAdapter.getActiveListAdapter().hasFilteredItem())
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
                for (int i=0; i<N; i++) {
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
                    final PackageManager pm = getPackageManager();

                    // Set the preferred Activity
                    pm.addUniquePreferredActivity(filter, bestMatch, set, intent.getComponent());

                    if (ri.handleAllWebDataURI) {
                        // Set default Browser if needed
                        final String packageName = pm.getDefaultBrowserPackageNameAsUser(userId);
                        if (TextUtils.isEmpty(packageName)) {
                            pm.setDefaultBrowserPackageNameAsUser(ri.activityInfo.packageName, userId);
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

        if (target != null) {
            safelyStartActivity(target);

            // Rely on the ActivityManager to pop up a dialog regarding app suspension
            // and return false
            if (target.isSuspended()) {
                return false;
            }
        }

        return true;
    }

    public void onActivityStarted(TargetInfo cti) {
        // Do nothing
    }

    @Override // ResolverListCommunicator
    public boolean shouldGetActivityMetadata() {
        return false;
    }

    public boolean shouldAutoLaunchSingleChoice(TargetInfo target) {
        return !target.isSuspended();
    }

    // TODO: this method takes an unused `UserHandle` because the override in `ChooserActivity` uses
    // that data to set up other components as dependencies of the controller. In reality, these
    // methods don't require polymorphism, because they're only invoked from within their respective
    // concrete class; `ResolverActivity` will never call this method expecting to get a
    // `ChooserListController` (subclass) result, because `ResolverActivity` only invokes this
    // method as part of handling `createMultiProfilePagerAdapter()`, which is itself overridden in
    // `ChooserActivity`. A future refactoring could better express the coupling between the adapter
    // and controller types; in the meantime, structuring as an override (with matching signatures)
    // shows that these methods are *structurally* related, and helps to prevent any regressions in
    // the future if resolver *were* to make any (non-overridden) calls to a version that used a
    // different signature (and thus didn't return the subclass type).
    @VisibleForTesting
    protected ResolverListController createListController(UserHandle userHandle) {
        ResolverRankerServiceResolverComparator resolverComparator =
                new ResolverRankerServiceResolverComparator(
                        this,
                        getTargetIntent(),
                        getReferrerPackageName(),
                        null,
                        null,
                        getResolverRankerServiceUserHandleList(userHandle),
                        null);
        return new ResolverListController(
                this,
                mPm,
                getTargetIntent(),
                getReferrerPackageName(),
                getAnnotatedUserHandles().userIdOfCallingApp,
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

    void onHorizontalSwipeStateChanged(int state) {}

    /**
     * Callback called when user changes the profile tab.
     * <p>This method is intended to be overridden by subclasses.
     */
    protected void onProfileTabSelected() { }

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
            if (shouldShowTabs()) {
                textView.setGravity(Gravity.CENTER);
            }
            stub.addView(textView);
        }
    }

    protected void resetButtonBar() {
        if (!mSupportsAlwaysUseOption) {
            return;
        }
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
    public void onHandlePackagesChanged(ResolverListAdapter listAdapter) {
        if (listAdapter == mMultiProfilePagerAdapter.getActiveListAdapter()) {
            if (listAdapter.getUserHandle().equals(getAnnotatedUserHandles().workProfileUserHandle)
                    && mWorkProfileAvailability.isWaitingToEnableWorkProfile()) {
                // We have just turned on the work profile and entered the pass code to start it,
                // now we are waiting to receive the ACTION_USER_UNLOCKED broadcast. There is no
                // point in reloading the list now, since the work profile user is still
                // turning on.
                return;
            }
            boolean listRebuilt = mMultiProfilePagerAdapter.rebuildActiveTab(true);
            if (listRebuilt) {
                ResolverListAdapter activeListAdapter =
                        mMultiProfilePagerAdapter.getActiveListAdapter();
                activeListAdapter.notifyDataSetChanged();
                if (activeListAdapter.getCount() == 0 && !inactiveListAdapterHasItems()) {
                    // We no longer have any items...  just finish the activity.
                    finish();
                }
            }
        } else {
            mMultiProfilePagerAdapter.clearInactiveProfileCache();
        }
    }

    protected void maybeLogProfileChange() {}

    // @NonFinalForTesting
    @VisibleForTesting
    protected MyUserIdProvider createMyUserIdProvider() {
        return new MyUserIdProvider();
    }

    // @NonFinalForTesting
    @VisibleForTesting
    protected CrossProfileIntentsChecker createCrossProfileIntentsChecker() {
        return new CrossProfileIntentsChecker(getContentResolver());
    }

    protected WorkProfileAvailabilityManager createWorkProfileAvailabilityManager() {
        return new WorkProfileAvailabilityManager(
                getSystemService(UserManager.class),
                getAnnotatedUserHandles().workProfileUserHandle,
                this::onWorkProfileStatusUpdated);
    }

    protected void onWorkProfileStatusUpdated() {
        if (mMultiProfilePagerAdapter.getCurrentUserHandle().equals(
                getAnnotatedUserHandles().workProfileUserHandle)) {
            mMultiProfilePagerAdapter.rebuildActiveTab(true);
        } else {
            mMultiProfilePagerAdapter.clearInactiveProfileCache();
        }
    }

    // @NonFinalForTesting
    @VisibleForTesting
    protected ResolverListAdapter createResolverListAdapter(
            Context context,
            List<Intent> payloadIntents,
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed,
            UserHandle userHandle,
            TargetDataLoader targetDataLoader) {
        UserHandle initialIntentsUserSpace = isLaunchedAsCloneProfile()
                && userHandle.equals(getAnnotatedUserHandles().personalProfileUserHandle)
                ? getAnnotatedUserHandles().cloneProfileUserHandle : userHandle;
        return new ResolverListAdapter(
                context,
                payloadIntents,
                initialIntents,
                resolutionList,
                filterLastUsed,
                createListController(userHandle),
                userHandle,
                getTargetIntent(),
                this,
                initialIntentsUserSpace,
                targetDataLoader);
    }

    private TargetDataLoader createIconLoader() {
        Intent startIntent = getIntent();
        boolean isAudioCaptureDevice =
                startIntent.getBooleanExtra(EXTRA_IS_AUDIO_CAPTURE_DEVICE, false);
        return new DefaultTargetDataLoader(this, getLifecycle(), isAudioCaptureDevice);
    }

    private LatencyTracker getLatencyTracker() {
        return LatencyTracker.getInstance(this);
    }

    /**
     * Get the string resource to be used as a label for the link to the resolver activity for an
     * action.
     *
     * @param action The action to resolve
     *
     * @return The string resource to be used as a label
     */
    public static @StringRes int getLabelRes(String action) {
        return ActionTitle.forAction(action).labelRes;
    }

    protected final EmptyStateProvider createEmptyStateProvider(
            @Nullable UserHandle workProfileUserHandle) {
        final EmptyStateProvider blockerEmptyStateProvider = createBlockerEmptyStateProvider();

        final EmptyStateProvider workProfileOffEmptyStateProvider =
                new WorkProfilePausedEmptyStateProvider(this, workProfileUserHandle,
                        mWorkProfileAvailability,
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
                getAnnotatedUserHandles().personalProfileUserHandle,
                getMetricsCategory(),
                getAnnotatedUserHandles().tabOwnerUserHandleForLaunch
        );

        // Return composite provider, the order matters (the higher, the more priority)
        return new CompositeEmptyStateProvider(
                blockerEmptyStateProvider,
                workProfileOffEmptyStateProvider,
                noAppsEmptyStateProvider
        );
    }

    private Intent makeMyIntent() {
        Intent intent = new Intent(getIntent());
        intent.setComponent(null);
        // The resolver activity is set to be hidden from recent tasks.
        // we don't want this attribute to be propagated to the next activity
        // being launched.  Note that if the original Intent also had this
        // flag set, we are now losing it.  That should be a very rare case
        // and we can live with this.
        intent.setFlags(intent.getFlags() & ~Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS);

        // If FLAG_ACTIVITY_LAUNCH_ADJACENT was set, ResolverActivity was opened in the alternate
        // side, which means we want to open the target app on the same side as ResolverActivity.
        if ((intent.getFlags() & FLAG_ACTIVITY_LAUNCH_ADJACENT) != 0) {
            intent.setFlags(intent.getFlags() & ~FLAG_ACTIVITY_LAUNCH_ADJACENT);
        }
        return intent;
    }

    /**
     * Call {@link Activity#onCreate} without initializing anything further. This should
     * only be used when the activity is about to be immediately finished to avoid wasting
     * initializing steps and leaking resources.
     */
    protected final void super_onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    private ResolverMultiProfilePagerAdapter
            createResolverMultiProfilePagerAdapterForOneProfile(
                    Intent[] initialIntents,
                    List<ResolveInfo> resolutionList,
                    boolean filterLastUsed,
                    TargetDataLoader targetDataLoader) {
        ResolverListAdapter adapter = createResolverListAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                initialIntents,
                resolutionList,
                filterLastUsed,
                /* userHandle */ getAnnotatedUserHandles().personalProfileUserHandle,
                targetDataLoader);
        return new ResolverMultiProfilePagerAdapter(
                /* context */ this,
                adapter,
                createEmptyStateProvider(/* workProfileUserHandle= */ null),
                /* workProfileQuietModeChecker= */ () -> false,
                /* workProfileUserHandle= */ null,
                getAnnotatedUserHandles().cloneProfileUserHandle);
    }

    private UserHandle getIntentUser() {
        return getIntent().hasExtra(EXTRA_CALLING_USER)
                ? getIntent().getParcelableExtra(EXTRA_CALLING_USER)
                : getAnnotatedUserHandles().tabOwnerUserHandleForLaunch;
    }

    private ResolverMultiProfilePagerAdapter createResolverMultiProfilePagerAdapterForTwoProfiles(
            Intent[] initialIntents,
            List<ResolveInfo> resolutionList,
            boolean filterLastUsed,
            TargetDataLoader targetDataLoader) {
        // In the edge case when we have 0 apps in the current profile and >1 apps in the other,
        // the intent resolver is started in the other profile. Since this is the only case when
        // this happens, we check for it here and set the current profile's tab.
        int selectedProfile = getCurrentProfile();
        UserHandle intentUser = getIntentUser();
        if (!getAnnotatedUserHandles().tabOwnerUserHandleForLaunch.equals(intentUser)) {
            if (getAnnotatedUserHandles().personalProfileUserHandle.equals(intentUser)) {
                selectedProfile = PROFILE_PERSONAL;
            } else if (getAnnotatedUserHandles().workProfileUserHandle.equals(intentUser)) {
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
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_PERSONAL ? initialIntents : null,
                resolutionList,
                (filterLastUsed && UserHandle.myUserId()
                        == getAnnotatedUserHandles().personalProfileUserHandle.getIdentifier()),
                /* userHandle */ getAnnotatedUserHandles().personalProfileUserHandle,
                targetDataLoader);
        UserHandle workProfileUserHandle = getAnnotatedUserHandles().workProfileUserHandle;
        ResolverListAdapter workAdapter = createResolverListAdapter(
                /* context */ this,
                /* payloadIntents */ mIntents,
                selectedProfile == PROFILE_WORK ? initialIntents : null,
                resolutionList,
                (filterLastUsed && UserHandle.myUserId()
                        == workProfileUserHandle.getIdentifier()),
                /* userHandle */ workProfileUserHandle,
                targetDataLoader);
        return new ResolverMultiProfilePagerAdapter(
                /* context */ this,
                personalAdapter,
                workAdapter,
                createEmptyStateProvider(workProfileUserHandle),
                () -> mWorkProfileAvailability.isQuietModeEnabled(),
                selectedProfile,
                workProfileUserHandle,
                getAnnotatedUserHandles().cloneProfileUserHandle);
    }

    /**
     * Returns {@link #PROFILE_PERSONAL} or {@link #PROFILE_WORK} if the {@link
     * #EXTRA_SELECTED_PROFILE} extra was supplied, or {@code -1} if no extra was supplied.
     * @throws IllegalArgumentException if the value passed to the {@link #EXTRA_SELECTED_PROFILE}
     * extra is not {@link #PROFILE_PERSONAL} or {@link #PROFILE_WORK}
     */
    final int getSelectedProfileExtra() {
        int selectedProfile = -1;
        if (getIntent().hasExtra(EXTRA_SELECTED_PROFILE)) {
            selectedProfile = getIntent().getIntExtra(EXTRA_SELECTED_PROFILE, /* defValue = */ -1);
            if (selectedProfile != PROFILE_PERSONAL && selectedProfile != PROFILE_WORK) {
                throw new IllegalArgumentException(EXTRA_SELECTED_PROFILE + " has invalid value "
                        + selectedProfile + ". Must be either ResolverActivity.PROFILE_PERSONAL or "
                        + "ResolverActivity.PROFILE_WORK.");
            }
        }
        return selectedProfile;
    }

    protected final @Profile int getCurrentProfile() {
        UserHandle launchUser = getAnnotatedUserHandles().tabOwnerUserHandleForLaunch;
        UserHandle personalUser = getAnnotatedUserHandles().personalProfileUserHandle;
        return launchUser.equals(personalUser) ? PROFILE_PERSONAL : PROFILE_WORK;
    }

    protected final AnnotatedUserHandles getAnnotatedUserHandles() {
        return mLazyAnnotatedUserHandles.get();
    }

    private boolean hasWorkProfile() {
        return getAnnotatedUserHandles().workProfileUserHandle != null;
    }

    private boolean hasCloneProfile() {
        return getAnnotatedUserHandles().cloneProfileUserHandle != null;
    }

    protected final boolean isLaunchedAsCloneProfile() {
        UserHandle launchUser = getAnnotatedUserHandles().userHandleSharesheetLaunchedAs;
        UserHandle cloneUser = getAnnotatedUserHandles().cloneProfileUserHandle;
        return hasCloneProfile() && launchUser.equals(cloneUser);
    }

    protected final boolean shouldShowTabs() {
        return hasWorkProfile();
    }

    protected final void onProfileClick(View v) {
        final DisplayResolveInfo dri =
                mMultiProfilePagerAdapter.getActiveListAdapter().getOtherProfile();
        if (dri == null) {
            return;
        }

        // Do not show the profile switch message anymore.
        mProfileSwitchMessage = null;

        onTargetSelected(dri, false);
        finish();
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
                                getAnnotatedUserHandles().personalProfileUserHandle))
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

    public final Intent getTargetIntent() {
        return mIntents.isEmpty() ? null : mIntents.get(0);
    }

    protected final String getReferrerPackageName() {
        final Uri referrer = getReferrer();
        if (referrer != null && "android-app".equals(referrer.getScheme())) {
            return referrer.getHost();
        }
        return null;
    }

    @Override // ResolverListCommunicator
    public final void updateProfileViewButton() {
        if (mProfileView == null) {
            return;
        }

        final DisplayResolveInfo dri =
                mMultiProfilePagerAdapter.getActiveListAdapter().getOtherProfile();
        if (dri != null && !shouldShowTabs()) {
            mProfileView.setVisibility(View.VISIBLE);
            View text = mProfileView.findViewById(com.android.internal.R.id.profile_button);
            if (!(text instanceof TextView)) {
                text = mProfileView.findViewById(com.android.internal.R.id.text1);
            }
            ((TextView) text).setText(dri.getDisplayLabel());
        } else {
            mProfileView.setVisibility(View.GONE);
        }
    }

    private void setProfileSwitchMessage(int contentUserHint) {
        if ((contentUserHint != UserHandle.USER_CURRENT)
                && (contentUserHint != UserHandle.myUserId())) {
            UserManager userManager = (UserManager) getSystemService(Context.USER_SERVICE);
            UserInfo originUserInfo = userManager.getUserInfo(contentUserHint);
            boolean originIsManaged = originUserInfo != null ? originUserInfo.isManagedProfile()
                    : false;
            boolean targetIsManaged = userManager.isManagedProfile();
            if (originIsManaged && !targetIsManaged) {
                mProfileSwitchMessage = getForwardToPersonalMsg();
            } else if (!originIsManaged && targetIsManaged) {
                mProfileSwitchMessage = getForwardToWorkMsg();
            }
        }
    }

    private String getForwardToPersonalMsg() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                FORWARD_INTENT_TO_PERSONAL,
                () -> getString(R.string.forward_intent_to_owner));
    }

    private String getForwardToWorkMsg() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                FORWARD_INTENT_TO_WORK,
                () -> getString(R.string.forward_intent_to_work));
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

    final void dismiss() {
        if (!isFinishing()) {
            finish();
        }
    }

    @Override
    protected final void onRestart() {
        super.onRestart();
        if (!mRegistered) {
            mPersonalPackageMonitor.register(
                    this,
                    getMainLooper(),
                    getAnnotatedUserHandles().personalProfileUserHandle,
                    false);
            if (shouldShowTabs()) {
                if (mWorkPackageMonitor == null) {
                    mWorkPackageMonitor = createPackageMonitor(
                            mMultiProfilePagerAdapter.getWorkListAdapter());
                }
                mWorkPackageMonitor.register(
                        this,
                        getMainLooper(),
                        getAnnotatedUserHandles().workProfileUserHandle,
                        false);
            }
            mRegistered = true;
        }
        if (shouldShowTabs() && mWorkProfileAvailability.isWaitingToEnableWorkProfile()) {
            if (mWorkProfileAvailability.isQuietModeEnabled()) {
                mWorkProfileAvailability.markWorkProfileEnabledBroadcastReceived();
            }
        }
        mMultiProfilePagerAdapter.getActiveListAdapter().handlePackagesChanged();
        updateProfileViewButton();
    }

    @Override
    protected final void onStart() {
        super.onStart();

        this.getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        if (shouldShowTabs()) {
            mWorkProfileAvailability.registerWorkProfileStateReceiver(this);
        }
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
    protected final void onRestoreInstanceState(@NonNull Bundle savedInstanceState) {
        super.onRestoreInstanceState(savedInstanceState);
        resetButtonBar();
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        if (viewPager != null) {
            viewPager.setCurrentItem(savedInstanceState.getInt(LAST_SHOWN_TAB_KEY));
        }
        mMultiProfilePagerAdapter.clearInactiveProfileCache();
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
            ApplicationInfo appInfo = getPackageManager().getApplicationInfo(
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
        if (hasCloneProfile() && (mMultiProfilePagerAdapter.getCurrentPage() == PROFILE_PERSONAL)) {
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

            boolean hasRecordPermission =
                    mPm.checkPermission(android.Manifest.permission.RECORD_AUDIO,
                            activityInfo.packageName)
                            == android.content.pm.PackageManager.PERMISSION_GRANTED;

            if (!hasRecordPermission) {
                // OK, we know the record permission, is this a capture device
                boolean hasAudioCapture =
                        getIntent().getBooleanExtra(
                                ResolverActivity.EXTRA_IS_AUDIO_CAPTURE_DEVICE, false);
                enabled = !hasAudioCapture;
            }
        }
        mAlwaysButton.setEnabled(enabled);
    }

    private String getWorkProfileNotSupportedMsg(String launcherName) {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                RESOLVER_WORK_PROFILE_NOT_SUPPORTED,
                () -> getString(
                        R.string.activity_resolver_work_profiles_support,
                        launcherName),
                launcherName);
    }

    @Override // ResolverListCommunicator
    public final void onPostListReady(ResolverListAdapter listAdapter, boolean doPostProcessing,
            boolean rebuildCompleted) {
        if (isAutolaunching()) {
            return;
        }
        if (mIsIntentPicker) {
            ((ResolverMultiProfilePagerAdapter) mMultiProfilePagerAdapter)
                    .setUseLayoutWithDefault(useLayoutWithDefault());
        }
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
        if (mProfileSwitchMessage != null) {
            Toast.makeText(this, mProfileSwitchMessage, Toast.LENGTH_LONG).show();
        }
        if (!mSafeForwardingMode) {
            if (cti.startAsUser(this, options, user)) {
                onActivityStarted(cti);
                maybeLogCrossProfileTargetLaunch(cti, user);
            }
            return;
        }
        try {
            if (cti.startAsCaller(this, options, user.getIdentifier())) {
                onActivityStarted(cti);
                maybeLogCrossProfileTargetLaunch(cti, user);
            }
        } catch (RuntimeException e) {
            Slog.wtf(TAG,
                    "Unable to launch as uid " + getAnnotatedUserHandles().userIdOfCallingApp
                    + " package " + getLaunchedFromPackage() + ", while running in "
                    + ActivityThread.currentProcessName(), e);
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
        boolean rebuildCompleted = mMultiProfilePagerAdapter.rebuildActiveTab(true)
                || mMultiProfilePagerAdapter.getActiveListAdapter().isTabLoaded();
        if (shouldShowTabs()) {
            boolean rebuildInactiveCompleted = mMultiProfilePagerAdapter.rebuildInactiveTab(false)
                    || mMultiProfilePagerAdapter.getInactiveListAdapter().isTabLoaded();
            rebuildCompleted = rebuildCompleted && rebuildInactiveCompleted;
        }

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

        DisplayResolveInfo sameProfileResolveInfo =
                mMultiProfilePagerAdapter.getActiveListAdapter().getFirstDisplayResolveInfo();
        boolean inWorkProfile = getCurrentProfile() == PROFILE_WORK;

        final ResolverListAdapter inactiveAdapter =
                mMultiProfilePagerAdapter.getInactiveListAdapter();
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

    /**
     * Mini resolver should be used when all of the following are true:
     * 1. This is the intent picker (ResolverActivity).
     * 2. This profile only has web browser matches.
     * 3. The other profile has a single non-browser match.
     */
    private boolean shouldUseMiniResolver() {
        if (!mIsIntentPicker) {
            return false;
        }
        if (mMultiProfilePagerAdapter.getActiveListAdapter() == null
                || mMultiProfilePagerAdapter.getInactiveListAdapter() == null) {
            return false;
        }
        ResolverListAdapter sameProfileAdapter =
                mMultiProfilePagerAdapter.getActiveListAdapter();
        ResolverListAdapter otherProfileAdapter =
                mMultiProfilePagerAdapter.getInactiveListAdapter();

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

        if (shouldShowTabs()) {
            setupProfileTabs();
        }

        return false;
    }

    private int isPermissionGranted(String permission, int uid) {
        return ActivityManager.checkComponentPermission(permission, uid,
                /* owningUid= */-1, /* exported= */ true);
    }

    /**
     * @return {@code true} if a resolved target is autolaunched, otherwise {@code false}
     */
    private boolean maybeAutolaunchActivity() {
        int numberOfProfiles = mMultiProfilePagerAdapter.getItemCount();
        if (numberOfProfiles == 1 && maybeAutolaunchIfSingleTarget()) {
            return true;
        } else if (numberOfProfiles == 2
                && mMultiProfilePagerAdapter.getActiveListAdapter().isTabLoaded()
                && mMultiProfilePagerAdapter.getInactiveListAdapter().isTabLoaded()
                && maybeAutolaunchIfCrossProfileSupported()) {
            // TODO(b/280988288): If the ChooserActivity is shown we should consider showing the
            //  correct intent-picker UIs (e.g., mini-resolver) if it was launched without
            //  ACTION_SEND.
            return true;
        }
        return false;
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
     * When we have a personal and a work profile, we auto launch in the following scenario:
     * - There is 1 resolved target on each profile
     * - That target is the same app on both profiles
     * - The target app has permission to communicate cross profiles
     * - The target app has declared it supports cross-profile communication via manifest metadata
     */
    private boolean maybeAutolaunchIfCrossProfileSupported() {
        ResolverListAdapter activeListAdapter = mMultiProfilePagerAdapter.getActiveListAdapter();
        int count = activeListAdapter.getUnfilteredCount();
        if (count != 1) {
            return false;
        }
        ResolverListAdapter inactiveListAdapter =
                mMultiProfilePagerAdapter.getInactiveListAdapter();
        if (inactiveListAdapter.getUnfilteredCount() != 1) {
            return false;
        }
        TargetInfo activeProfileTarget = activeListAdapter
                .targetInfoForPosition(0, false);
        TargetInfo inactiveProfileTarget = inactiveListAdapter.targetInfoForPosition(0, false);
        if (!Objects.equals(activeProfileTarget.getResolvedComponentName(),
                inactiveProfileTarget.getResolvedComponentName())) {
            return false;
        }
        if (!shouldAutoLaunchSingleChoice(activeProfileTarget)) {
            return false;
        }
        String packageName = activeProfileTarget.getResolvedComponentName().getPackageName();
        if (!canAppInteractCrossProfiles(packageName)) {
            return false;
        }

        DevicePolicyEventLogger
                .createEvent(DevicePolicyEnums.RESOLVER_AUTOLAUNCH_CROSS_PROFILE_TARGET)
                .setBoolean(activeListAdapter.getUserHandle()
                        .equals(getAnnotatedUserHandles().personalProfileUserHandle))
                .setStrings(getMetricsCategory())
                .write();
        safelyStartActivity(activeProfileTarget);
        finish();
        return true;
    }

    /**
     * Returns whether the package has the necessary permissions to interact across profiles on
     * behalf of a given user.
     *
     * <p>This means meeting the following condition:
     * <ul>
     *     <li>The app's {@link ApplicationInfo#crossProfile} flag must be true, and at least
     *     one of the following conditions must be fulfilled</li>
     *     <li>{@code Manifest.permission.INTERACT_ACROSS_USERS_FULL} granted.</li>
     *     <li>{@code Manifest.permission.INTERACT_ACROSS_USERS} granted.</li>
     *     <li>{@code Manifest.permission.INTERACT_ACROSS_PROFILES} granted, or the corresponding
     *     AppOps {@code android:interact_across_profiles} is set to "allow".</li>
     * </ul>
     *
     */
    private boolean canAppInteractCrossProfiles(String packageName) {
        ApplicationInfo applicationInfo;
        try {
            applicationInfo = getPackageManager().getApplicationInfo(packageName, 0);
        } catch (NameNotFoundException e) {
            Log.e(TAG, "Package " + packageName + " does not exist on current user.");
            return false;
        }
        if (!applicationInfo.crossProfile) {
            return false;
        }

        int packageUid = applicationInfo.uid;

        if (isPermissionGranted(android.Manifest.permission.INTERACT_ACROSS_USERS_FULL,
                packageUid) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (isPermissionGranted(android.Manifest.permission.INTERACT_ACROSS_USERS, packageUid)
                == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        if (PermissionChecker.checkPermissionForPreflight(this, INTERACT_ACROSS_PROFILES,
                PID_UNKNOWN, packageUid, packageName) == PackageManager.PERMISSION_GRANTED) {
            return true;
        }
        return false;
    }

    private boolean isAutolaunching() {
        return !mRegistered && isFinishing();
    }

    private void setupProfileTabs() {
        maybeHideDivider();
        TabHost tabHost = findViewById(com.android.internal.R.id.profile_tabhost);
        tabHost.setup();
        ViewPager viewPager = findViewById(com.android.internal.R.id.profile_pager);
        viewPager.setSaveEnabled(false);

        Button personalButton = (Button) getLayoutInflater().inflate(
                R.layout.resolver_profile_tab_button, tabHost.getTabWidget(), false);
        personalButton.setText(getPersonalTabLabel());
        personalButton.setContentDescription(getPersonalTabAccessibilityLabel());

        TabHost.TabSpec tabSpec = tabHost.newTabSpec(TAB_TAG_PERSONAL)
                .setContent(com.android.internal.R.id.profile_pager)
                .setIndicator(personalButton);
        tabHost.addTab(tabSpec);

        Button workButton = (Button) getLayoutInflater().inflate(
                R.layout.resolver_profile_tab_button, tabHost.getTabWidget(), false);
        workButton.setText(getWorkTabLabel());
        workButton.setContentDescription(getWorkTabAccessibilityLabel());

        tabSpec = tabHost.newTabSpec(TAB_TAG_WORK)
                .setContent(com.android.internal.R.id.profile_pager)
                .setIndicator(workButton);
        tabHost.addTab(tabSpec);

        TabWidget tabWidget = tabHost.getTabWidget();
        tabWidget.setVisibility(View.VISIBLE);
        updateActiveTabStyle(tabHost);

        tabHost.setOnTabChangedListener(tabId -> {
            updateActiveTabStyle(tabHost);
            if (TAB_TAG_PERSONAL.equals(tabId)) {
                viewPager.setCurrentItem(0);
            } else {
                viewPager.setCurrentItem(1);
            }
            setupViewVisibilities();
            maybeLogProfileChange();
            onProfileTabSelected();
            DevicePolicyEventLogger
                    .createEvent(DevicePolicyEnums.RESOLVER_SWITCH_TABS)
                    .setInt(viewPager.getCurrentItem())
                    .setStrings(getMetricsCategory())
                    .write();
        });

        viewPager.setVisibility(View.VISIBLE);
        tabHost.setCurrentTab(mMultiProfilePagerAdapter.getCurrentPage());
        mMultiProfilePagerAdapter.setOnProfileSelectedListener(
                new MultiProfilePagerAdapter.OnProfileSelectedListener() {
                    @Override
                    public void onProfileSelected(int index) {
                        tabHost.setCurrentTab(index);
                        resetButtonBar();
                        resetCheckedItem();
                    }

                    @Override
                    public void onProfilePageStateChanged(int state) {
                        onHorizontalSwipeStateChanged(state);
                    }
                });
        mOnSwitchOnWorkSelectedListener = () -> {
            final View workTab = tabHost.getTabWidget().getChildAt(1);
            workTab.setFocusable(true);
            workTab.setFocusableInTouchMode(true);
            workTab.requestFocus();
        };
    }

    private String getPersonalTabLabel() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                RESOLVER_PERSONAL_TAB, () -> getString(R.string.resolver_personal_tab));
    }

    private String getWorkTabLabel() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                RESOLVER_WORK_TAB, () -> getString(R.string.resolver_work_tab));
    }

    private void maybeHideDivider() {
        if (!mIsIntentPicker) {
            return;
        }
        final View divider = findViewById(com.android.internal.R.id.divider);
        if (divider == null) {
            return;
        }
        divider.setVisibility(View.GONE);
    }

    private void resetCheckedItem() {
        if (!mIsIntentPicker) {
            return;
        }
        mLastSelected = ListView.INVALID_POSITION;
        ListView inactiveListView = (ListView) mMultiProfilePagerAdapter.getInactiveAdapterView();
        if (inactiveListView.getCheckedItemCount() > 0) {
            inactiveListView.setItemChecked(inactiveListView.getCheckedItemPosition(), false);
        }
    }

    private String getPersonalTabAccessibilityLabel() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                RESOLVER_PERSONAL_TAB_ACCESSIBILITY,
                () -> getString(R.string.resolver_personal_tab_accessibility));
    }

    private String getWorkTabAccessibilityLabel() {
        return getSystemService(DevicePolicyManager.class).getResources().getString(
                RESOLVER_WORK_TAB_ACCESSIBILITY,
                () -> getString(R.string.resolver_work_tab_accessibility));
    }

    private static int getAttrColor(Context context, int attr) {
        TypedArray ta = context.obtainStyledAttributes(new int[]{attr});
        int colorAccent = ta.getColor(0, 0);
        ta.recycle();
        return colorAccent;
    }

    private void updateActiveTabStyle(TabHost tabHost) {
        int currentTab = tabHost.getCurrentTab();
        TextView selected = (TextView) tabHost.getTabWidget().getChildAt(currentTab);
        TextView unselected = (TextView) tabHost.getTabWidget().getChildAt(1 - currentTab);
        selected.setSelected(true);
        unselected.setSelected(false);
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

        if (mSupportsAlwaysUseOption) {
            listView.setChoiceMode(AbsListView.CHOICE_MODE_SINGLE);
        }
    }

    /**
     * Configure the area above the app selection list (title, content preview, etc).
     */
    private void maybeCreateHeader(ResolverListAdapter listAdapter) {
        if (mHeaderCreatorUser != null
                && !listAdapter.getUserHandle().equals(mHeaderCreatorUser)) {
            return;
        }
        if (!shouldShowTabs()
                && listAdapter.getCount() == 0 && listAdapter.getPlaceholderCount() == 0) {
            final TextView titleView = findViewById(com.android.internal.R.id.title);
            if (titleView != null) {
                titleView.setVisibility(View.GONE);
            }
        }

        CharSequence title = mTitle != null
                ? mTitle
                : getTitleForAction(getTargetIntent(), mDefaultTitleResId);

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
        boolean adapterForCurrentUserHasFilteredItem =
                mMultiProfilePagerAdapter.getListAdapterForUserHandle(
                        getAnnotatedUserHandles().tabOwnerUserHandleForLaunch).hasFilteredItem();
        return mSupportsAlwaysUseOption && adapterForCurrentUserHasFilteredItem;
    }

    /**
     * If {@code retainInOnStop} is set to true, we will not finish ourselves when onStop gets
     * called and we are launched in a new task.
     */
    protected final void setRetainInOnStop(boolean retainInOnStop) {
        mRetainInOnStop = retainInOnStop;
    }

    private boolean inactiveListAdapterHasItems() {
        if (!shouldShowTabs()) {
            return false;
        }
        return mMultiProfilePagerAdapter.getInactiveListAdapter().getCount() > 0;
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

    /** Determine whether a given match result is considered "specific" in our application. */
    public static final boolean isSpecificUriMatch(int match) {
        match = (match & IntentFilter.MATCH_CATEGORY_MASK);
        return match >= IntentFilter.MATCH_CATEGORY_HOST
                && match <= IntentFilter.MATCH_CATEGORY_PATH;
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
        return getAnnotatedUserHandles().getQueryIntentsUser(userHandle);
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
        if (userHandle.equals(getAnnotatedUserHandles().personalProfileUserHandle)
                && hasCloneProfile()) {
            userList.add(getAnnotatedUserHandles().cloneProfileUserHandle);
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
