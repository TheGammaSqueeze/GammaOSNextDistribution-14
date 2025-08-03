/*
 * Copyright (C) 2015 The Android Open Source Project
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

package com.android.permissioncontroller.permission.ui;

import static android.Manifest.permission.ACCESS_COARSE_LOCATION;
import static android.Manifest.permission.ACCESS_FINE_LOCATION;
import static android.Manifest.permission_group.LOCATION;
import static android.Manifest.permission_group.READ_MEDIA_VISUAL;
import static android.content.Intent.getIntent;
import static android.view.WindowManager.LayoutParams.FLAG_ALT_FOCUSABLE_IM;
import static android.view.WindowManager.LayoutParams.SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS;

import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.CANCELED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_DO_NOT_ASK_AGAIN;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.DENIED_MORE;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ALWAYS;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_FOREGROUND_ONLY;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_ONE_TIME;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.GRANTED_USER_SELECTED;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.LINKED_TO_PERMISSION_RATIONALE;
import static com.android.permissioncontroller.permission.ui.GrantPermissionsViewHandler.LINKED_TO_SETTINGS;
import static com.android.permissioncontroller.permission.ui.model.GrantPermissionsViewModel.APP_PERMISSION_REQUEST_CODE;
import static com.android.permissioncontroller.permission.ui.model.GrantPermissionsViewModel.ECM_REQUEST_CODE;
import static com.android.permissioncontroller.permission.ui.model.GrantPermissionsViewModel.PHOTO_PICKER_REQUEST_CODE;
import static com.android.permissioncontroller.permission.utils.Utils.getRequestMessage;

import android.Manifest;
import android.app.Activity;
import android.app.KeyguardManager;
import android.app.ecm.EnhancedConfirmationManager;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageItemInfo;
import android.content.pm.PackageManager;
import android.content.res.Resources;
import android.graphics.drawable.Icon;
import android.os.Build;
import android.os.Bundle;
import android.os.Process;
import android.permission.flags.Flags;
import android.text.Annotation;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.style.ClickableSpan;
import android.util.ArraySet;
import android.util.Log;
import android.util.Pair;
import android.view.KeyEvent;
import android.view.View;
import android.view.View.OnAttachStateChangeListener;
import android.view.Window;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;

import androidx.annotation.GuardedBy;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.core.util.Preconditions;

import com.android.modules.utils.build.SdkLevel;
import com.android.permissioncontroller.DeviceUtils;
import com.android.permissioncontroller.R;
import com.android.permissioncontroller.permission.ui.auto.GrantPermissionsAutoViewHandler;
import com.android.permissioncontroller.permission.ui.model.DenyButton;
import com.android.permissioncontroller.permission.ui.model.GrantPermissionsViewModel;
import com.android.permissioncontroller.permission.ui.model.GrantPermissionsViewModel.RequestInfo;
import com.android.permissioncontroller.permission.ui.model.NewGrantPermissionsViewModelFactory;
import com.android.permissioncontroller.permission.ui.model.Prompt;
import com.android.permissioncontroller.permission.ui.wear.GrantPermissionsWearViewHandler;
import com.android.permissioncontroller.permission.utils.ContextCompat;
import com.android.permissioncontroller.permission.utils.KotlinUtils;
import com.android.permissioncontroller.permission.utils.MultiDeviceUtils;
import com.android.permissioncontroller.permission.utils.PermissionMapping;
import com.android.permissioncontroller.permission.utils.Utils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;

/**
 * An activity which displays runtime permission prompts on behalf of an app.
 */
public class GrantPermissionsActivity extends SettingsActivity
        implements GrantPermissionsViewHandler.ResultListener {

    private static final String LOG_TAG = "GrantPermissionsActivity";

    private static final String KEY_SESSION_ID = GrantPermissionsActivity.class.getName()
            + "_REQUEST_ID";
    public static final String KEY_RESTRICTED_REQUESTED_PERMISSIONS =
            GrantPermissionsActivity.class.getName() + "_RESTRICTED_REQUESTED_PERMISSIONS";
    public static final String KEY_UNRESTRICTED_REQUESTED_PERMISSIONS =
            GrantPermissionsActivity.class.getName() + "_UNRESTRICTED_REQUESTED_PERMISSIONS";
    public static final String KEY_ORIGINAL_REQUESTED_PERMISSIONS =
            GrantPermissionsActivity.class.getName() + "_ORIGINAL_REQUESTED_PERMISSIONS";

    public static final String ANNOTATION_ID = "link";

    public static final int NEXT_BUTTON = 15;
    public static final int ALLOW_BUTTON = 0;
    public static final int ALLOW_ALWAYS_BUTTON = 1; // Used in auto
    public static final int ALLOW_FOREGROUND_BUTTON = 2;
    public static final int DENY_BUTTON = 3;
    public static final int DENY_AND_DONT_ASK_AGAIN_BUTTON = 4;
    public static final int ALLOW_ONE_TIME_BUTTON = 5;
    public static final int NO_UPGRADE_BUTTON = 6;
    public static final int NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON = 7;
    public static final int NO_UPGRADE_OT_BUTTON = 8; // one-time
    public static final int NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON = 9; // one-time
    public static final int LINK_TO_SETTINGS = 10;
    public static final int ALLOW_ALL_BUTTON = 11; // button for options with a picker, allow all
    public static final int ALLOW_SELECTED_BUTTON = 12; // allow selected, with picker
    // button to cancel a request for more data with a picker
    public static final int DONT_ALLOW_MORE_SELECTED_BUTTON = 13;
    public static final int LINK_TO_PERMISSION_RATIONALE = 14;

    public static final int NEXT_LOCATION_DIALOG = 6;
    public static final int LOCATION_ACCURACY_LAYOUT = 0;
    public static final int FINE_RADIO_BUTTON = 1;
    public static final int COARSE_RADIO_BUTTON = 2;
    public static final int DIALOG_WITH_BOTH_LOCATIONS = 3;
    public static final int DIALOG_WITH_FINE_LOCATION_ONLY = 4;
    public static final int DIALOG_WITH_COARSE_LOCATION_ONLY = 5;

    // The maximum number of dialogs we will allow the same package, on the same task, to launch
    // simultaneously
    public static final int MAX_DIALOGS_PER_PKG_TASK = 10;

    public static final Map<String, Integer> PERMISSION_TO_BIT_SHIFT =
            Map.of(
                    ACCESS_COARSE_LOCATION, 0,
                    ACCESS_FINE_LOCATION, 1);

    public static final String INTENT_PHOTOS_SELECTED = "intent_extra_result";

    /**
     * A map of the currently shown GrantPermissionsActivity for this user, per package and task ID
     */
    @GuardedBy("sCurrentGrantRequests")
    public static final Map<Pair<String, Integer>, GrantPermissionsActivity> sCurrentGrantRequests =
            new HashMap<>();

    /** Unique Id of a request */
    private long mSessionId;

    /**
     * The permission group that was showing, before a new permission request came in on top of an
     * existing request
     */
    private String mPreMergeShownGroupName;

    /** The current list of permissions requested, across all current requests for this app */
    private List<String> mRequestedPermissions = new ArrayList<>();

    /**
     * If any requested permissions are considered restricted by ECM, they will be stored here.
     */
    private ArrayList<String> mRestrictedRequestedPermissionGroups = null;

    /**
     * If any requested permissions are considered restricted by ECM, the non-restricted
     * permissions will be stored here.
     */
    private List<String> mUnrestrictedRequestedPermissions = null;

    /** A list of permissions requested on an app's behalf by the system. Usually Implicitly
     * requested, although this isn't necessarily always the case.
     */
    private List<String> mSystemRequestedPermissions = new ArrayList<>();
    /** A copy of the list of permissions originally requested in the intent to this activity */
    private String[] mOriginalRequestedPermissions = new String[0];

    private boolean[] mButtonVisibilities;
    private int mRequestCounts = 0;
    private List<RequestInfo> mRequestInfos = new ArrayList<>();
    private GrantPermissionsViewHandler mViewHandler;
    private GrantPermissionsViewModel mViewModel;

    /**
     * A list of other GrantPermissionActivities for the same package which passed their list of
     * permissions to this one. They need to be informed when this activity finishes.
     */
    private List<GrantPermissionsActivity> mFollowerActivities = new ArrayList<>();

    /** Whether this activity has asked another GrantPermissionsActivity to show on its behalf */
    private boolean mDelegated;

    /** Whether this activity has been triggered by the system */
    private boolean mIsSystemTriggered = false;

    /** The set result code, or MAX_VALUE if it hasn't been set yet */
    private int mResultCode = Integer.MAX_VALUE;

    /** Package that shall have permissions granted */
    private String mTargetPackage;

    /** A key representing this activity, defined by the target package and task ID */
    private Pair<String, Integer> mKey;

    private float mOriginalDimAmount;
    private View mRootView;
    private int mStoragePermGroupIcon = R.drawable.ic_empty_icon;

    /** Which device the permission will affect. Default is the primary device. */
    private int mTargetDeviceId = ContextCompat.DEVICE_ID_DEFAULT;

    @Override
    public void onCreate(Bundle icicle) {
        if (DeviceUtils.isAuto(this)) {
            setTheme(R.style.GrantPermissions_Car_FilterTouches);
        }
        super.onCreate(icicle);

        if (icicle == null) {
            mSessionId = new Random().nextLong();
        } else {
            mSessionId = icicle.getLong(KEY_SESSION_ID);
        }

        getWindow().addSystemFlags(SYSTEM_FLAG_HIDE_NON_SYSTEM_OVERLAY_WINDOWS);
        if (DeviceUtils.isWear(this)) {
            // Do not grab input focus and hide keyboard.
            getWindow().addFlags(FLAG_ALT_FOCUSABLE_IM);
        }

        int permissionsSdkLevel;
        if (PackageManager.ACTION_REQUEST_PERMISSIONS_FOR_OTHER.equals(getIntent().getAction())) {
            mIsSystemTriggered = true;
            mTargetPackage = getIntent().getStringExtra(Intent.EXTRA_PACKAGE_NAME);
            if (mTargetPackage == null) {
                Log.e(LOG_TAG, "null EXTRA_PACKAGE_NAME. Must be set for "
                        + "REQUEST_PERMISSIONS_FOR_OTHER activity");
                finishAfterTransition();
                return;
            }
            // We don't want to do any filtering in this case.
            // These calls are coming from the system on behalf of the app.
            permissionsSdkLevel = Build.VERSION_CODES.CUR_DEVELOPMENT;
        } else {
            // Cache this as this can only read on onCreate, not later.
            mTargetPackage = getCallingPackage();
            if (mTargetPackage == null) {
                Log.e(LOG_TAG, "null callingPackageName. Please use \"RequestPermission\" to "
                        + "request permissions");
                finishAfterTransition();
                return;
            }
            try {
                PackageInfo packageInfo = getPackageManager().getPackageInfo(mTargetPackage, 0);
                permissionsSdkLevel = packageInfo.applicationInfo.targetSdkVersion;
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(LOG_TAG, "Unable to get package info for the calling package.", e);
                finishAfterTransition();
                return;
            }
        }

        String[] requestedPermissionsArray =
                getIntent().getStringArrayExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES);
        if (requestedPermissionsArray == null) {
            setResultAndFinish();
            return;
        }

        mRequestedPermissions = removeNullOrEmptyPermissions(requestedPermissionsArray);
        if (mIsSystemTriggered) {
            mSystemRequestedPermissions.addAll(mRequestedPermissions);
        }

        if (mRequestedPermissions.isEmpty()) {
            setResultAndFinish();
            return;
        }

        if (MultiDeviceUtils.isDeviceAwareGrantFlowEnabled()) {
            mTargetDeviceId =
                    getIntent()
                            .getIntExtra(
                                    PackageManager.EXTRA_REQUEST_PERMISSIONS_DEVICE_ID,
                                    ContextCompat.DEVICE_ID_DEFAULT);
        }

        // If the permissions requested are for a remote device, check if each permission is device
        // aware.
        if (mTargetDeviceId != ContextCompat.DEVICE_ID_DEFAULT) {
            if (!MultiDeviceUtils.isDeviceAwareGrantFlowEnabled()) {
                Log.e(LOG_TAG, "targetDeviceId should be the default device if device aware grant"
                        + " flow is not enabled");
                finishAfterTransition();
                return;
            }

            for (String permission : mRequestedPermissions) {
                if (!MultiDeviceUtils.isPermissionDeviceAware(permission)) {
                    Log.e(LOG_TAG, "When target device is external, permission " + permission
                            + " needs to be device aware.");
                    finishAfterTransition();
                    return;
                }
            }
        }

        mOriginalRequestedPermissions = mRequestedPermissions.toArray(new String[0]);

        if (SdkLevel.isAtLeastV() && Flags.enhancedConfirmationModeApisEnabled()) {
            EnhancedConfirmationManager ecm = getEnhancedConfirmationManager();

            // Retrieve ECM-related persisted permission lists
            if (icicle != null) {
                mOriginalRequestedPermissions = icicle.getStringArray(
                        KEY_ORIGINAL_REQUESTED_PERMISSIONS);
                mRestrictedRequestedPermissionGroups = icicle.getStringArrayList(
                        KEY_RESTRICTED_REQUESTED_PERMISSIONS);
                mUnrestrictedRequestedPermissions = icicle.getStringArrayList(
                        KEY_UNRESTRICTED_REQUESTED_PERMISSIONS);
            }
            // If these lists aren't persisted yet, it means we haven't yet divided
            // mRequestedPermissions into restricted-vs-unrestricted, so do so.
            if (mRestrictedRequestedPermissionGroups == null) {
                String packageName = getCallingPackage();
                ArraySet<String> restrictedPermGroups = new ArraySet<>();
                ArrayList<String> unrestrictedPermissions = new ArrayList<>();

                for (String requestedPermission : mRequestedPermissions) {
                    String requestedPermGroup =
                            PermissionMapping.getGroupOfPlatformPermission(requestedPermission);
                    if (restrictedPermGroups.contains(requestedPermGroup)
                            || isPermissionEcmRestricted(ecm, requestedPermission, packageName)) {
                        restrictedPermGroups.add(requestedPermGroup);
                    } else {
                        unrestrictedPermissions.add(requestedPermission);
                    }
                }
                mRestrictedRequestedPermissionGroups = new ArrayList<>(restrictedPermGroups);
                mUnrestrictedRequestedPermissions = unrestrictedPermissions;
            }
            // If there are remaining restricted permission groups to process, show the ECM dialog
            // for the next one, then recreate this activity.
            if (!mRestrictedRequestedPermissionGroups.isEmpty()) {
                String nextRestrictedPermissionGroup = mRestrictedRequestedPermissionGroups.remove(
                        0);
                try {
                    Intent intent = ecm.createRestrictedSettingDialogIntent(getPackageName(),
                            nextRestrictedPermissionGroup);
                    startActivityForResult(intent, ECM_REQUEST_CODE);
                    return;
                } catch (PackageManager.NameNotFoundException e) {
                    mRequestedPermissions = mUnrestrictedRequestedPermissions;
                }
            } else {
                mRequestedPermissions = mUnrestrictedRequestedPermissions;
            }
        }

        NewGrantPermissionsViewModelFactory factory =
                new NewGrantPermissionsViewModelFactory(
                        getApplication(),
                        mTargetPackage,
                        mTargetDeviceId,
                        mRequestedPermissions,
                        mSystemRequestedPermissions,
                        mSessionId,
                        icicle);
        mViewModel = factory.create(GrantPermissionsViewModel.class);

        synchronized (sCurrentGrantRequests) {
            mKey = new Pair<>(mTargetPackage, getTaskId());
            GrantPermissionsActivity current = sCurrentGrantRequests.get(mKey);
            if (current == null) {
                sCurrentGrantRequests.put(mKey, this);
                finishSystemStartedDialogsOnOtherTasksLocked();
            } else if (mIsSystemTriggered) {
                // The system triggered dialog doesn't require results. Delegate, and finish.
                current.onNewFollowerActivity(null, mRequestedPermissions, false);
                finishAfterTransition();
                return;
            } else if (current.mIsSystemTriggered) {
                // merge into the system triggered dialog, which has task overlay set
                mDelegated = true;
                current.onNewFollowerActivity(this, mRequestedPermissions, false);
            } else {
                // this + current + current.mFollowerActivities
                if ((current.mFollowerActivities.size() + 2) > MAX_DIALOGS_PER_PKG_TASK) {
                    // If there are too many dialogs for the same package, in the same task, cancel
                    finishAfterTransition();
                    return;
                }
                // Merge the old dialogs into the new
                onNewFollowerActivity(current, current.mRequestedPermissions, true);
                sCurrentGrantRequests.put(mKey, this);
            }
        }

        setFinishOnTouchOutside(false);

        setTitle(R.string.permission_request_title);

        if (DeviceUtils.isTelevision(this)) {
            mViewHandler = new com.android.permissioncontroller.permission.ui.television
                    .GrantPermissionsViewHandlerImpl(this,
                    mTargetPackage).setResultListener(this);
        } else if (DeviceUtils.isWear(this)) {
            mViewHandler = new GrantPermissionsWearViewHandler(this).setResultListener(this);
        } else if (DeviceUtils.isAuto(this)) {
            mViewHandler = new GrantPermissionsAutoViewHandler(this, mTargetPackage)
                    .setResultListener(this);
        } else {
            mViewHandler = new com.android.permissioncontroller.permission.ui.handheld
                    .GrantPermissionsViewHandlerImpl(this, this);
        }

        if (!mDelegated) {
            mViewModel.getRequestInfosLiveData().observe(this, this::onRequestInfoLoad);
        }

        mRootView = mViewHandler.createView();
        mRootView.setVisibility(View.GONE);
        setContentView(mRootView);
        Window window = getWindow();
        WindowManager.LayoutParams layoutParams = window.getAttributes();
        mOriginalDimAmount = layoutParams.dimAmount;
        mViewHandler.updateWindowAttributes(layoutParams);
        window.setAttributes(layoutParams);

        if (SdkLevel.isAtLeastS() && getResources().getBoolean(R.bool.config_useWindowBlur)) {
            java.util.function.Consumer<Boolean> blurEnabledListener = enabled -> {
                mViewHandler.onBlurEnabledChanged(window, enabled);
            };
            mRootView.addOnAttachStateChangeListener(new OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    window.getWindowManager().addCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {
                    window.getWindowManager().removeCrossWindowBlurEnabledListener(
                            blurEnabledListener);
                }
            });
        }
        // Restore UI state after lifecycle events. This has to be before we show the first request,
        // as the UI behaves differently for updates and initial creations.
        if (icicle != null) {
            mViewHandler.loadInstanceState(icicle);
        } else if (mRootView == null || mRootView.getVisibility() != View.VISIBLE) {
            // Do not show screen dim until data is loaded
            window.setDimAmount(0f);
        }

        PackageItemInfo storageGroupInfo =
                Utils.getGroupInfo(Manifest.permission_group.STORAGE, this.getApplicationContext());
        if (storageGroupInfo != null) {
            mStoragePermGroupIcon = storageGroupInfo.icon;
        }
    }

    private EnhancedConfirmationManager getEnhancedConfirmationManager() {
        Context userContext = Utils.getUserContext(this, Process.myUserHandle());
        return Utils.getSystemServiceSafe(userContext, EnhancedConfirmationManager.class);
    }

    private boolean isPermissionEcmRestricted(EnhancedConfirmationManager ecm,
            String requestedPermission, String packageName) {
        try {
            return ecm.isRestricted(packageName, requestedPermission);
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

    /**
     * A new GrantPermissionsActivity has opened for this same package. Merge its requested
     * permissions with the original ones set in the intent, and recalculate the grant states.
     * @param follower The activity requesting permissions, which needs to be informed upon this
     *                 activity finishing
     * @param newPermissions The new permissions requested in the activity
     */
    private void onNewFollowerActivity(@Nullable GrantPermissionsActivity follower,
            @NonNull List<String> newPermissions, boolean followerIsOlder) {
        if (follower != null) {
            // Ensure the list of follower activities is a stack
            mFollowerActivities.add(0, follower);
            follower.mViewModel = mViewModel;
            if (followerIsOlder) {
                follower.mDelegated = true;
            }
        }

        // If the follower is older, examine it to find the pre-merge group
        GrantPermissionsActivity olderActivity = follower != null && followerIsOlder
                ? follower : this;
        boolean isShowingGroup = olderActivity.mRootView != null
                && olderActivity.mRootView.getVisibility() == View.VISIBLE;
        List<RequestInfo> currentGroups =
                olderActivity.mViewModel.getRequestInfosLiveData().getValue();
        if (mPreMergeShownGroupName == null && isShowingGroup
                && currentGroups != null && !currentGroups.isEmpty()) {
            mPreMergeShownGroupName = currentGroups.get(0).getGroupName();
        }

        if (isShowingGroup && mPreMergeShownGroupName != null
                && followerIsOlder && currentGroups != null) {
            // Load a request from the old activity
            mRequestInfos = currentGroups;
            showNextRequest();
            olderActivity.mRootView.setVisibility(View.GONE);
        }
        if (follower != null && followerIsOlder) {
            follower.mFollowerActivities.forEach((oldFollower) ->
                    onNewFollowerActivity(oldFollower, new ArrayList<>(), true));
            follower.mFollowerActivities.clear();
        }

        if (mRequestedPermissions.containsAll(newPermissions)) {
            return;
        }

        ArrayList<String> currentPermissions = new ArrayList<>(mRequestedPermissions);
        for (String newPerm : newPermissions) {
            if (!currentPermissions.contains(newPerm)) {
                currentPermissions.add(newPerm);
            }
        }
        mRequestedPermissions = currentPermissions;

        Bundle oldState = new Bundle();
        mViewModel.getRequestInfosLiveData().removeObservers(this);
        mViewModel.saveInstanceState(oldState);
        NewGrantPermissionsViewModelFactory factory =
                new NewGrantPermissionsViewModelFactory(
                        getApplication(),
                        mTargetPackage,
                        mTargetDeviceId,
                        mRequestedPermissions,
                        mSystemRequestedPermissions,
                        mSessionId,
                        oldState);
        mViewModel = factory.create(GrantPermissionsViewModel.class);
        mViewModel.getRequestInfosLiveData().observe(this, this::onRequestInfoLoad);
        if (follower != null) {
            follower.mViewModel = mViewModel;
        }
    }

    /**
     * When the leader activity this activity delegated to finishes, finish this activity
     * @param resultCode the result of the leader
     */
    private void onLeaderActivityFinished(int resultCode) {
        setResultIfNeeded(resultCode);
        finishAfterTransition();
    }

    private void onRequestInfoLoad(List<RequestInfo> requests) {
        if (!mViewModel.getRequestInfosLiveData().isInitialized() || isResultSet() || mDelegated) {
            return;
        } else if (requests == null) {
            finishAfterTransition();
            return;
        } else if (requests.isEmpty()) {
            setResultAndFinish();
            return;
        }

        mRequestInfos = requests;

        // If we were already showing a group, and then another request came in with more groups,
        // keep the current group showing until the user makes a decision
        if (mPreMergeShownGroupName != null) {
            return;
        }

        showNextRequest();
    }

    private void showNextRequest() {
        if (mRequestInfos.isEmpty() || mDelegated) {
            return;
        }

        RequestInfo info = mRequestInfos.get(0);

        if (info.getPrompt() == Prompt.NO_UI_SETTINGS_REDIRECT) {
            mViewModel.sendDirectlyToSettings(this, info.getGroupName());
            return;
        } else if (info.getPrompt() == Prompt.NO_UI_PHOTO_PICKER_REDIRECT) {
            mViewModel.openPhotoPicker(this);
            return;
        } else if (info.getPrompt() == Prompt.NO_UI_FILTER_THIS_GROUP) {
            // Filtered permissions should be removed from the requested permissions list entirely,
            // and not have status returned to the app
            List<String> permissionsToFilter =
                    PermissionMapping.getPlatformPermissionNamesOfGroup(info.getGroupName());
            mRequestedPermissions.removeAll(permissionsToFilter);
            mRequestInfos.remove(info);
            onRequestInfoLoad(mRequestInfos);
            return;
        } else if (info.getPrompt() == Prompt.NO_UI_HEALTH_REDIRECT) {
            mViewModel.handleHealthConnectPermissions(this);
            return;
        }

        String appLabel =
                KotlinUtils.INSTANCE.getPackageLabel(
                        getApplication(), mTargetPackage, Process.myUserHandle());

        // Show device name in the dialog when the dialog is streamed to a remote device OR
        // target device is different from streamed device.
        int dialogDisplayDeviceId = ContextCompat.getDeviceId(this);
        boolean isMessageDeviceAware =
                dialogDisplayDeviceId != ContextCompat.DEVICE_ID_DEFAULT
                        || dialogDisplayDeviceId != mTargetDeviceId;

        int messageId = getMessageId(info.getGroupName(), info.getPrompt(), isMessageDeviceAware);
        CharSequence message =
                getRequestMessage(
                        appLabel,
                        mTargetPackage,
                        info.getGroupName(),
                        MultiDeviceUtils.getDeviceName(getApplicationContext(), info.getDeviceId()),
                        this,
                        isMessageDeviceAware,
                        messageId);

        int detailMessageId = getDetailMessageId(info.getGroupName(), info.getPrompt());
        Spanned detailMessage = null;
        if (detailMessageId != 0) {
            detailMessage = new SpannableString(getText(detailMessageId));
            Annotation[] annotations =
                    detailMessage.getSpans(0, detailMessage.length(), Annotation.class);
            int numAnnotations = annotations.length;
            for (int i = 0; i < numAnnotations; i++) {
                Annotation annotation = annotations[i];
                if (annotation.getValue().equals(ANNOTATION_ID)) {
                    int start = detailMessage.getSpanStart(annotation);
                    int end = detailMessage.getSpanEnd(annotation);
                    ClickableSpan clickableSpan = getLinkToAppPermissions(info);
                    SpannableString spannableString = new SpannableString(detailMessage);
                    spannableString.setSpan(clickableSpan, start, end, 0);
                    detailMessage = spannableString;
                    break;
                }
            }
        }

        Icon icon = null;
        try {
            if (info.getPrompt() == Prompt.STORAGE_SUPERGROUP_Q_TO_S
                    || info.getPrompt() == Prompt.STORAGE_SUPERGROUP_PRE_Q) {
                icon = Icon.createWithResource(getPackageName(), mStoragePermGroupIcon);
            } else {
                icon = Icon.createWithResource(
                        info.getGroupInfo().getPackageName(),
                        info.getGroupInfo().getIcon());
            }
        } catch (Resources.NotFoundException e) {
            Log.e(LOG_TAG, "Cannot load icon for group" + info.getGroupName(), e);
        }

        // Set the permission message as the title so it can be announced. Skip on Wear
        // because the dialog title is already announced, as is the default selection which
        // is a text view containing the title.
        if (!DeviceUtils.isWear(this)) {
            setTitle(message);
        }

        mButtonVisibilities = getButtonsForPrompt(info.getPrompt(), info.getDeny(),
                info.getShowRationale());

        CharSequence permissionRationaleMessage = null;
        if (isPermissionRationaleVisible()) {
            permissionRationaleMessage =
                getString(
                    getPermissionRationaleMessageResIdForPermissionGroup(
                        info.getGroupName()));
        }

        boolean[] locationVisibilities = getLocationButtonsForPrompt(info.getPrompt());

        if (mRequestCounts < mRequestInfos.size()) {
            mRequestCounts = mRequestInfos.size();
        }

        int pageIdx = mRequestCounts - mRequestInfos.size();
        mViewHandler.updateUi(info.getGroupName(), mRequestCounts, pageIdx, icon,
                message, detailMessage, permissionRationaleMessage, mButtonVisibilities,
                locationVisibilities);

        getWindow().setDimAmount(mOriginalDimAmount);
        if (mRootView.getVisibility() == View.GONE) {
            if (mIsSystemTriggered) {
                // We don't want the keyboard obscuring system-triggered dialogs
                InputMethodManager manager = getSystemService(InputMethodManager.class);
                manager.hideSoftInputFromWindow(mRootView.getWindowToken(), 0);
            }
            mRootView.setVisibility(View.VISIBLE);
        }
    }

    private int getMessageId(String permGroupName, Prompt prompt, Boolean isDeviceAware) {
        return switch (prompt) {
            case UPGRADE_SETTINGS_LINK, OT_UPGRADE_SETTINGS_LINK -> Utils.getUpgradeRequest(
                    permGroupName, isDeviceAware);
            case SETTINGS_LINK_FOR_BG, SETTINGS_LINK_WITH_OT -> Utils.getBackgroundRequest(
                    permGroupName, isDeviceAware);
            case LOCATION_FINE_UPGRADE -> Utils.getFineLocationRequest(isDeviceAware);
            case LOCATION_COARSE_ONLY -> Utils.getCoarseLocationRequest(isDeviceAware);
            case STORAGE_SUPERGROUP_PRE_Q -> R.string.permgrouprequest_storage_pre_q;
            case STORAGE_SUPERGROUP_Q_TO_S -> R.string.permgrouprequest_storage_q_to_s;
            case SELECT_MORE_PHOTOS -> Utils.getMorePhotosRequest(isDeviceAware);
            default -> Utils.getRequest(permGroupName, isDeviceAware);
        };
    }

    private int getDetailMessageId(String permGroupName, Prompt prompt) {
        return switch (prompt) {
            case UPGRADE_SETTINGS_LINK, OT_UPGRADE_SETTINGS_LINK ->
                    Utils.getUpgradeRequestDetail(permGroupName);
            case SETTINGS_LINK_FOR_BG, SETTINGS_LINK_WITH_OT ->
                    Utils.getBackgroundRequestDetail(permGroupName);
            default -> 0;
        };
    }

    private boolean[] getButtonsForPrompt(Prompt prompt, DenyButton denyButton,
                                          boolean shouldShowRationale) {
        ArraySet<Integer> buttons = new ArraySet<>();
        switch (prompt) {
            case BASIC, STORAGE_SUPERGROUP_PRE_Q, STORAGE_SUPERGROUP_Q_TO_S ->
                    buttons.add(ALLOW_BUTTON);
            case FG_ONLY, SETTINGS_LINK_FOR_BG ->  buttons.add(ALLOW_FOREGROUND_BUTTON);
            case ONE_TIME_FG, SETTINGS_LINK_WITH_OT, LOCATION_TWO_BUTTON_COARSE_HIGHLIGHT,
                    LOCATION_TWO_BUTTON_FINE_HIGHLIGHT, LOCATION_COARSE_ONLY,
                    LOCATION_FINE_UPGRADE ->
                buttons.addAll(Arrays.asList(ALLOW_FOREGROUND_BUTTON, ALLOW_ONE_TIME_BUTTON));
            case SELECT_PHOTOS, SELECT_MORE_PHOTOS ->
                buttons.addAll(Arrays.asList(ALLOW_ALL_BUTTON, ALLOW_SELECTED_BUTTON));
        }

        switch (denyButton) {
            case DENY -> buttons.add(DENY_BUTTON);
            case DENY_DONT_ASK_AGAIN -> buttons.add(DENY_AND_DONT_ASK_AGAIN_BUTTON);
            case DONT_SELECT_MORE -> buttons.add(DONT_ALLOW_MORE_SELECTED_BUTTON);
            case NO_UPGRADE -> buttons.add(NO_UPGRADE_BUTTON);
            case NO_UPGRADE_OT -> buttons.add(NO_UPGRADE_OT_BUTTON);
            case NO_UPGRADE_AND_DONT_ASK_AGAIN ->
                    buttons.add(NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON);
            case NO_UPGRADE_AND_DONT_ASK_AGAIN_OT ->
                    buttons.add(NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON);
        }

        if (shouldShowRationale) {
            buttons.add(LINK_TO_PERMISSION_RATIONALE);
        }
        return convertSetToBoolList(buttons, NEXT_BUTTON);
    }

    private boolean[] getLocationButtonsForPrompt(Prompt prompt) {
        ArraySet<Integer> locationButtons = new ArraySet<>();
        switch (prompt) {
            case LOCATION_TWO_BUTTON_COARSE_HIGHLIGHT ->
                locationButtons.addAll(Arrays.asList(LOCATION_ACCURACY_LAYOUT,
                        DIALOG_WITH_BOTH_LOCATIONS, COARSE_RADIO_BUTTON));
            case LOCATION_TWO_BUTTON_FINE_HIGHLIGHT ->
                locationButtons.addAll(Arrays.asList(LOCATION_ACCURACY_LAYOUT,
                        DIALOG_WITH_BOTH_LOCATIONS, FINE_RADIO_BUTTON));
            case LOCATION_COARSE_ONLY ->
                locationButtons.addAll(Arrays.asList(LOCATION_ACCURACY_LAYOUT,
                        DIALOG_WITH_COARSE_LOCATION_ONLY));
            case LOCATION_FINE_UPGRADE ->
                locationButtons.addAll(Arrays.asList(LOCATION_ACCURACY_LAYOUT,
                        DIALOG_WITH_FINE_LOCATION_ONLY));
        }
        return convertSetToBoolList(locationButtons, NEXT_LOCATION_DIALOG);
    }

    private boolean[] convertSetToBoolList(Set<Integer> buttonSet, int size) {
        boolean[] buttonArray = new boolean[size];
        for (int button: buttonSet) {
            buttonArray[button] = true;
        }
        return buttonArray;
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE
                && event.getRepeatCount() == 0
                && event.hasNoModifiers()) {
            event.startTracking();
            mViewHandler.onCancelled();
            finishAfterTransition();
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_ESCAPE
                && event.isTracking()
                && !event.isCanceled()) {
            // Mark it as handled since we did handle the down event
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    protected void onSaveInstanceState(@NonNull Bundle outState) {
        super.onSaveInstanceState(outState);

        if (SdkLevel.isAtLeastV() && Flags.enhancedConfirmationModeApisEnabled()) {
            outState.putStringArrayList(KEY_RESTRICTED_REQUESTED_PERMISSIONS, new ArrayList<>(
                    mRestrictedRequestedPermissionGroups));
            outState.putStringArrayList(KEY_UNRESTRICTED_REQUESTED_PERMISSIONS, new ArrayList<>(
                    mUnrestrictedRequestedPermissions));
            outState.putStringArray(KEY_ORIGINAL_REQUESTED_PERMISSIONS,
                    mOriginalRequestedPermissions);
        }

        if (mViewHandler == null || mViewModel == null) {
            return;
        }

        mViewHandler.saveInstanceState(outState);
        mViewModel.saveInstanceState(outState);

        outState.putLong(KEY_SESSION_ID, mSessionId);
    }

    private ClickableSpan getLinkToAppPermissions(RequestInfo info) {
        return new ClickableSpan() {
            @Override
            public void onClick(View widget) {
                logGrantPermissionActivityButtons(info.getGroupName(), null, LINKED_TO_SETTINGS);
                mViewModel.sendToSettingsFromLink(GrantPermissionsActivity.this,
                        info.getGroupName());
            }
        };
    }


    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (SdkLevel.isAtLeastV() && Flags.enhancedConfirmationModeApisEnabled()) {
            if (requestCode == ECM_REQUEST_CODE) {
                recreate();
                return;
            }
        }
        if (requestCode != APP_PERMISSION_REQUEST_CODE
                && requestCode != PHOTO_PICKER_REQUEST_CODE) {
            return;
        }
        if (requestCode == PHOTO_PICKER_REQUEST_CODE) {
            data = new Intent("").putExtra(INTENT_PHOTOS_SELECTED, resultCode == RESULT_OK);
        }
        mViewModel.handleCallback(data, requestCode);
    }

    @Override
    public void onPermissionGrantResult(String name,
            @GrantPermissionsViewHandler.Result int result) {
        onPermissionGrantResult(name, null, result);
    }

    @Override
    public void onPermissionGrantResult(String name, List<String> affectedForegroundPermissions,
            @GrantPermissionsViewHandler.Result int result) {
        if (checkKgm(name, affectedForegroundPermissions, result)) {
            return;
        }

        if (name == null || name.equals(mPreMergeShownGroupName)) {
            mPreMergeShownGroupName = null;
        }

        if (Objects.equals(READ_MEDIA_VISUAL, name) && result == GRANTED_USER_SELECTED) {
            // Only the top activity can receive activity results
            Activity top = mFollowerActivities.isEmpty() ? this : mFollowerActivities.get(0);
            mViewModel.openPhotoPicker(this);
            logGrantPermissionActivityButtons(name, affectedForegroundPermissions, result);
            return;
        }

        logGrantPermissionActivityButtons(name, affectedForegroundPermissions, result);
        mViewModel.onPermissionGrantResult(name, affectedForegroundPermissions, result);
        if (result == CANCELED) {
            setResultAndFinish();
        }
    }

    @Override
    public void onPermissionRationaleClicked(String groupName) {
        logGrantPermissionActivityButtons(groupName,
                /* affectedForegroundPermissions= */ null,
                LINKED_TO_PERMISSION_RATIONALE);
        mViewModel.showPermissionRationaleActivity(this, groupName);
    }

    @Override
    public void onBackPressed() {
        if (mViewHandler == null) {
            return;
        }
        mViewHandler.onBackPressed();
    }

    @Override
    public void finishAfterTransition() {
        if (!setResultIfNeeded(RESULT_CANCELED)) {
            return;
        }
        if (mViewModel != null) {
            mViewModel.autoGrantNotify();
        }
        super.finishAfterTransition();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (!isResultSet()) {
            removeActivityFromMap();
        }
    }

    /**
     * Remove this activity from the map of activities
     */
    private void removeActivityFromMap() {
        synchronized (sCurrentGrantRequests) {
            GrantPermissionsActivity leader = sCurrentGrantRequests.get(mKey);
            if (this.equals(leader)) {
                sCurrentGrantRequests.remove(mKey);
            } else if (leader != null) {
                leader.mFollowerActivities.remove(this);
            }
        }
        for (GrantPermissionsActivity activity: mFollowerActivities) {
            activity.onLeaderActivityFinished(mResultCode);
        }
        mFollowerActivities.clear();
    }

    private boolean checkKgm(String name, List<String> affectedForegroundPermissions,
            @GrantPermissionsViewHandler.Result int result) {
        if (result == GRANTED_ALWAYS || result == GRANTED_FOREGROUND_ONLY
                || result == DENIED_DO_NOT_ASK_AGAIN) {
            KeyguardManager kgm = getSystemService(KeyguardManager.class);

            if (kgm != null && kgm.isDeviceLocked()) {
                kgm.requestDismissKeyguard(this, new KeyguardManager.KeyguardDismissCallback() {
                    @Override
                    public void onDismissError() {
                        Log.e(LOG_TAG, "Cannot dismiss keyguard perm=" + name
                                + " result=" + result);
                    }

                    @Override
                    public void onDismissCancelled() {
                        // do nothing (i.e. stay at the current permission group)
                    }

                    @Override
                    public void onDismissSucceeded() {
                        // Now the keyguard is dismissed, hence the device is not locked
                        // anymore
                        onPermissionGrantResult(name, affectedForegroundPermissions, result);
                    }
                });
                return true;
            }
        }
        return false;
    }

    private boolean setResultIfNeeded(int resultCode) {
        if (!isResultSet()) {
            List<String> oldRequestedPermissions = mRequestedPermissions;
            mResultCode = resultCode;
            removeActivityFromMap();
            // If a new merge request came in before we managed to remove this activity from the
            // map, then cancel the result set for now.
            if (!Objects.equals(oldRequestedPermissions, mRequestedPermissions)) {
                // Reset the result code back to its starting value of MAX_VALUE;
                mResultCode = Integer.MAX_VALUE;
                return false;
            }

            if (mViewModel != null) {
                mViewModel.logRequestedPermissionGroups();
            }

            // Only include the originally requested permissions in the result
            Intent result = new Intent(PackageManager.ACTION_REQUEST_PERMISSIONS);
            String[] resultPermissions = mOriginalRequestedPermissions;
            int[] grantResults = new int[resultPermissions.length];

            if ((mDelegated || (mViewModel != null && mViewModel.shouldReturnPermissionState()))
                    && mTargetPackage != null) {
                PackageManager pm = getPackageManager();
                for (int i = 0; i < resultPermissions.length; i++) {
                    grantResults[i] = pm.checkPermission(resultPermissions[i], mTargetPackage);
                }
            } else {
                grantResults = new int[0];
                resultPermissions = new String[0];
            }

            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_NAMES, resultPermissions);
            result.putExtra(PackageManager.EXTRA_REQUEST_PERMISSIONS_RESULTS, grantResults);
            result.putExtra(Intent.EXTRA_PACKAGE_NAME, mTargetPackage);
            setResult(resultCode, result);
        }
        return true;
    }

    private void setResultAndFinish() {
        if (setResultIfNeeded(RESULT_OK)) {
            finishAfterTransition();
        }
    }

    private void logGrantPermissionActivityButtons(String permissionGroupName,
            List<String> affectedForegroundPermissions, int grantResult) {
        int clickedButton = 0;
        int presentedButtons = getButtonState();
        switch (grantResult) {
            case GRANTED_ALWAYS:
                if (mButtonVisibilities[ALLOW_BUTTON]) {
                    clickedButton = 1 << ALLOW_BUTTON;
                } else if (mButtonVisibilities[ALLOW_ALWAYS_BUTTON]) {
                    clickedButton = 1 << ALLOW_ALWAYS_BUTTON;
                } else if (mButtonVisibilities[ALLOW_ALL_BUTTON]) {
                    clickedButton = 1 << ALLOW_ALL_BUTTON;
                }
                break;
            case GRANTED_FOREGROUND_ONLY:
                clickedButton = 1 << ALLOW_FOREGROUND_BUTTON;
                break;
            case DENIED:
                if (mButtonVisibilities != null) {
                    if (mButtonVisibilities[NO_UPGRADE_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_BUTTON;
                    } else if (mButtonVisibilities[NO_UPGRADE_OT_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_OT_BUTTON;
                    } else if (mButtonVisibilities[DENY_BUTTON]) {
                        clickedButton = 1 << DENY_BUTTON;
                    }
                }
                break;
            case DENIED_DO_NOT_ASK_AGAIN:
                if (mButtonVisibilities != null) {
                    if (mButtonVisibilities[NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_AND_DONT_ASK_AGAIN_BUTTON;
                    } else if (mButtonVisibilities[NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << NO_UPGRADE_OT_AND_DONT_ASK_AGAIN_BUTTON;
                    } else if (mButtonVisibilities[DENY_AND_DONT_ASK_AGAIN_BUTTON]) {
                        clickedButton = 1 << DENY_AND_DONT_ASK_AGAIN_BUTTON;
                    }
                }
                break;
            case GRANTED_ONE_TIME:
                clickedButton = 1 << ALLOW_ONE_TIME_BUTTON;
                break;
            case LINKED_TO_SETTINGS:
                clickedButton = 1 << LINK_TO_SETTINGS;
                break;
            case GRANTED_USER_SELECTED:
                clickedButton = 1 << ALLOW_SELECTED_BUTTON;
                break;
            case DENIED_MORE:
                clickedButton = 1 << DONT_ALLOW_MORE_SELECTED_BUTTON;
                break;
            case LINKED_TO_PERMISSION_RATIONALE:
                clickedButton = 1 << LINK_TO_PERMISSION_RATIONALE;
                break;
            case CANCELED:
                // fall through
            default:
                break;
        }

        int selectedPrecision = 0;
        if (affectedForegroundPermissions != null) {
            for (Map.Entry<String, Integer> entry : PERMISSION_TO_BIT_SHIFT.entrySet()) {
                if (affectedForegroundPermissions.contains(entry.getKey())) {
                    selectedPrecision |= 1 << entry.getValue();
                }
            }
        }

        mViewModel.logClickedButtons(permissionGroupName, selectedPrecision, clickedButton,
                presentedButtons, isPermissionRationaleVisible());
    }

    private int getButtonState() {
        if (mButtonVisibilities == null) {
            return 0;
        }
        int buttonState = 0;
        for (int i = NEXT_BUTTON - 1; i >= 0; i--) {
            buttonState *= 2;
            if (mButtonVisibilities[i]) {
                buttonState++;
            }
        }
        return buttonState;
    }

    private boolean isPermissionRationaleVisible() {
        return mButtonVisibilities != null && mButtonVisibilities[LINK_TO_PERMISSION_RATIONALE];
    }

    private boolean isResultSet() {
        return mResultCode != Integer.MAX_VALUE;
    }

    // Remove null and empty permissions from an array, return a list
    private List<String> removeNullOrEmptyPermissions(String[] perms) {
        ArrayList<String> sanitized = new ArrayList<>();
        for (String perm : perms) {
            if (perm == null || perm.isEmpty()) {
                continue;
            }
            sanitized.add(perm);
        }
        return sanitized;
    }

    /**
     * If there is another system-shown dialog on another task, that is not being relied upon by an
     * app-defined dialogs, these other dialogs should be finished.
     */
    @GuardedBy("sCurrentGrantRequests")
    private void finishSystemStartedDialogsOnOtherTasksLocked() {
        for (Pair<String, Integer> key : sCurrentGrantRequests.keySet()) {
            if (key.first.equals(mTargetPackage) && key.second != getTaskId()) {
                GrantPermissionsActivity other = sCurrentGrantRequests.get(key);
                if (other.mIsSystemTriggered && other.mFollowerActivities.isEmpty()) {
                    other.finish();
                }
            }
        }
    }

    /**
     * Returns permission rationale message string resource id for the given permission group.
     *
     * <p> Supported permission groups: LOCATION
     *
     * @param permissionGroupName permission group for which to get a message string id
     * @throws IllegalArgumentException if passing unsupported permission group
     */
    @StringRes
    private int getPermissionRationaleMessageResIdForPermissionGroup(String permissionGroupName) {
        Preconditions.checkArgument(LOCATION.equals(permissionGroupName),
                "Permission Rationale does not support %s", permissionGroupName);

        return R.string.permission_rationale_message_location;
    }
}
