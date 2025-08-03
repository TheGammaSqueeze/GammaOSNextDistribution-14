/*
 * Copyright (C) 2021 The Android Open Source Project
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

package com.android.safetycenter;

import static android.Manifest.permission.MANAGE_SAFETY_CENTER;
import static android.Manifest.permission.READ_SAFETY_CENTER_STATUS;
import static android.Manifest.permission.SEND_SAFETY_CENTER_UPDATE;
import static android.Manifest.permission.START_TASKS_FROM_RECENTS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;
import static android.os.Build.VERSION_CODES.UPSIDE_DOWN_CAKE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_DEVICE_LOCALE_CHANGE;
import static android.safetycenter.SafetyCenterManager.REFRESH_REASON_OTHER;
import static android.safetycenter.SafetyCenterManager.RefreshReason;
import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED;
import static android.safetycenter.SafetyEvent.SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED;

import static com.android.permission.PermissionStatsLog.SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT;
import static com.android.permission.PermissionStatsLog.SAFETY_STATE;
import static com.android.safetycenter.SafetyCenterFlags.PROPERTY_SAFETY_CENTER_ENABLED;
import static com.android.safetycenter.internaldata.SafetyCenterIds.toUserFriendlyString;

import static java.util.Objects.requireNonNull;

import android.annotation.UserIdInt;
import android.app.ActivityManager;
import android.app.PendingIntent;
import android.app.StatsManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.pm.PackageManager.PackageInfoFlags;
import android.content.res.Resources;
import android.os.Binder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.provider.DeviceConfig.OnPropertiesChangedListener;
import android.safetycenter.IOnSafetyCenterDataChangedListener;
import android.safetycenter.ISafetyCenterManager;
import android.safetycenter.SafetyCenterData;
import android.safetycenter.SafetyCenterErrorDetails;
import android.safetycenter.SafetyCenterManager;
import android.safetycenter.SafetyEvent;
import android.safetycenter.SafetySourceData;
import android.safetycenter.SafetySourceErrorDetails;
import android.safetycenter.SafetySourceIssue;
import android.safetycenter.config.SafetyCenterConfig;
import android.text.TextUtils;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.Keep;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;
import com.android.permission.flags.Flags;
import com.android.permission.util.ForegroundThread;
import com.android.permission.util.UserUtils;
import com.android.safetycenter.data.SafetyCenterDataManager;
import com.android.safetycenter.data.SafetyEventFix;
import com.android.safetycenter.data.SafetySourceDataFix;
import com.android.safetycenter.internaldata.SafetyCenterIds;
import com.android.safetycenter.internaldata.SafetyCenterIssueActionId;
import com.android.safetycenter.internaldata.SafetyCenterIssueId;
import com.android.safetycenter.internaldata.SafetyCenterIssueKey;
import com.android.safetycenter.logging.SafetyCenterPullAtomCallback;
import com.android.safetycenter.notifications.SafetyCenterNotificationChannels;
import com.android.safetycenter.notifications.SafetyCenterNotificationReceiver;
import com.android.safetycenter.notifications.SafetyCenterNotificationSender;
import com.android.safetycenter.pendingintents.PendingIntentSender;
import com.android.safetycenter.resources.SafetyCenterResourcesApk;
import com.android.server.SystemService;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;

/**
 * Service for the safety center.
 *
 * @hide
 */
@Keep
public final class SafetyCenterService extends SystemService {

    private static final String TAG = "SafetyCenterService";

    private final ApiLock mApiLock = new ApiLock();

    @GuardedBy("mApiLock")
    private final SafetyCenterTimeouts mSafetyCenterTimeouts = new SafetyCenterTimeouts();

    @GuardedBy("mApiLock")
    private final SafetyCenterResourcesApk mSafetyCenterResourcesApk;

    @GuardedBy("mApiLock")
    private final SafetyCenterConfigReader mSafetyCenterConfigReader;

    @GuardedBy("mApiLock")
    private final SafetyCenterRefreshTracker mSafetyCenterRefreshTracker;

    private final SafetySourceDataFix mSafetySourceDataFix;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataManager mSafetyCenterDataManager;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataFactory mSafetyCenterDataFactory;

    @GuardedBy("mApiLock")
    private final SafetyCenterListeners mSafetyCenterListeners;

    @GuardedBy("mApiLock")
    private final SafetyCenterNotificationChannels mNotificationChannels;

    @GuardedBy("mApiLock")
    private final SafetyCenterNotificationSender mNotificationSender;

    @GuardedBy("mApiLock")
    private final SafetyCenterBroadcastDispatcher mSafetyCenterBroadcastDispatcher;

    @GuardedBy("mApiLock")
    private final SafetyCenterDataChangeNotifier mSafetyCenterDataChangeNotifier;

    private final boolean mDeviceSupportsSafetyCenter;

    /** Whether the {@link SafetyCenterConfig} was successfully loaded. */
    private volatile boolean mConfigAvailable = false;

    public SafetyCenterService(Context context) {
        super(context);
        mSafetyCenterResourcesApk = new SafetyCenterResourcesApk(context);
        mSafetyCenterConfigReader = new SafetyCenterConfigReader(mSafetyCenterResourcesApk);
        mSafetyCenterRefreshTracker = new SafetyCenterRefreshTracker(context);
        PendingIntentFactory pendingIntentFactory =
                new PendingIntentFactory(context, mSafetyCenterResourcesApk);
        mSafetySourceDataFix =
                new SafetySourceDataFix(context, pendingIntentFactory, mSafetyCenterConfigReader);
        mSafetyCenterDataManager =
                new SafetyCenterDataManager(
                        context, mSafetyCenterConfigReader, mSafetyCenterRefreshTracker, mApiLock);
        mSafetyCenterDataFactory =
                new SafetyCenterDataFactory(
                        context,
                        mSafetyCenterResourcesApk,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker,
                        pendingIntentFactory,
                        mSafetyCenterDataManager);
        mSafetyCenterListeners = new SafetyCenterListeners(mSafetyCenterDataFactory);
        mNotificationChannels = new SafetyCenterNotificationChannels(mSafetyCenterResourcesApk);
        mNotificationSender =
                SafetyCenterNotificationSender.newInstance(
                        context,
                        mSafetyCenterResourcesApk,
                        mNotificationChannels,
                        mSafetyCenterDataManager);
        mSafetyCenterBroadcastDispatcher =
                new SafetyCenterBroadcastDispatcher(
                        context,
                        mSafetyCenterConfigReader,
                        mSafetyCenterRefreshTracker,
                        mSafetyCenterDataManager);
        mSafetyCenterDataChangeNotifier =
                new SafetyCenterDataChangeNotifier(mNotificationSender, mSafetyCenterListeners);
        mDeviceSupportsSafetyCenter =
                context.getResources()
                        .getBoolean(
                                Resources.getSystem()
                                        .getIdentifier(
                                                "config_enableSafetyCenter", "bool", "android"));
    }

    @Override
    public void onStart() {
        publishBinderService(Context.SAFETY_CENTER_SERVICE, new Stub());
        if (!mDeviceSupportsSafetyCenter) {
            Log.i(TAG, "Device does not support Safety Center, it will be disabled");
            return;
        }

        synchronized (mApiLock) {
            boolean safetyCenterResourcesInitialized = mSafetyCenterResourcesApk.init();
            if (!safetyCenterResourcesInitialized) {
                Log.e(TAG, "Cannot init Safety Center resources, Safety Center will be disabled");
                return;
            }

            SafetyCenterFlags.init(mSafetyCenterResourcesApk);

            if (!mSafetyCenterConfigReader.loadConfig()) {
                Log.e(TAG, "Cannot init Safety Center config, Safety Center will be disabled");
                return;
            }

            mConfigAvailable = true;
            mSafetyCenterDataManager.loadPersistableDataStateFromFile();
            new UserBroadcastReceiver().register(getContext());
            new SafetyCenterNotificationReceiver(
                            /* service= */ this,
                            mSafetyCenterDataManager,
                            mSafetyCenterDataChangeNotifier,
                            mApiLock)
                    .register(getContext());
            new LocaleBroadcastReceiver().register(getContext());
        }
    }

    @Override
    public void onBootPhase(int phase) {
        if (phase != SystemService.PHASE_BOOT_COMPLETED || !canUseSafetyCenter()) {
            return;
        }

        SafetyCenterPullAtomCallback pullAtomCallback;
        synchronized (mApiLock) {
            registerSafetyCenterEnabledListenerLocked();
            pullAtomCallback = newSafetyCenterPullAtomCallbackLocked();
        }
        registerSafetyCenterPullAtomCallback(pullAtomCallback);
    }

    @GuardedBy("mApiLock")
    private void registerSafetyCenterEnabledListenerLocked() {
        SafetyCenterEnabledListener safetyCenterEnabledListener = new SafetyCenterEnabledListener();
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_PRIVACY,
                ForegroundThread.getExecutor(),
                safetyCenterEnabledListener);
        // Set the initial state *after* registering the listener, in the unlikely event that the
        // flag changes between creating the listener and registering it (in which case we could
        // miss an update and end up with an inconsistent state).
        setInitialStateLocked(safetyCenterEnabledListener);
    }

    @GuardedBy("mApiLock")
    @SuppressWarnings("GuardedBy")
    // @GuardedBy is unable to infer that the `SafetyCenterService.this.mApiLock` in
    // `SafetyCenterService` is the same as the one in `SafetyCenterEnabledListener` here, so it
    // has to be suppressed.
    private void setInitialStateLocked(SafetyCenterEnabledListener safetyCenterEnabledListener) {
        safetyCenterEnabledListener.setInitialStateLocked();
    }

    @GuardedBy("mApiLock")
    private SafetyCenterPullAtomCallback newSafetyCenterPullAtomCallbackLocked() {
        return new SafetyCenterPullAtomCallback(
                getContext(),
                mApiLock,
                mSafetyCenterConfigReader,
                mSafetyCenterDataFactory,
                mSafetyCenterDataManager);
    }

    private void registerSafetyCenterPullAtomCallback(
            SafetyCenterPullAtomCallback pullAtomCallback) {
        StatsManager statsManager =
                requireNonNull(getContext().getSystemService(StatsManager.class));
        statsManager.setPullAtomCallback(
                SAFETY_STATE,
                /* metadata= */ null,
                BackgroundThread.getExecutor(),
                pullAtomCallback);
    }

    /** Service implementation of {@link ISafetyCenterManager.Stub}. */
    private final class Stub extends ISafetyCenterManager.Stub {
        @Override
        public boolean isSafetyCenterEnabled() {
            enforceAnyCallingOrSelfPermissions(
                    "isSafetyCenterEnabled", READ_SAFETY_CENTER_STATUS, SEND_SAFETY_CENTER_UPDATE);

            return isApiEnabled();
        }

        @Override
        public void setSafetySourceData(
                String safetySourceId,
                @Nullable SafetySourceData safetySourceData,
                SafetyEvent safetyEvent,
                String packageName,
                @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(safetyEvent);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "setSafetySourceData");
            if (!enforceCrossUserPermission("setSafetySourceData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("setSafetySourceData")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            synchronized (mApiLock) {
                safetySourceData =
                        mSafetySourceDataFix.maybeOverrideSafetySourceData(
                                safetySourceId, safetySourceData, packageName, userId);
                safetyEvent =
                        SafetyEventFix.maybeOverrideSafetyEvent(
                                mSafetyCenterDataManager,
                                safetySourceId,
                                safetySourceData,
                                safetyEvent,
                                userId);
                boolean hasUpdate =
                        mSafetyCenterDataManager.setSafetySourceData(
                                safetySourceData, safetySourceId, safetyEvent, packageName, userId);
                if (hasUpdate) {
                    // When an action is successfully resolved, call notifyActionSuccess before
                    // updateDataConsumers: Calling the former first will turn any notification for
                    // the resolved issue into a success notification, whereas calling the latter
                    // will simply clear any issue notification and no success message will show.
                    if (safetyEvent.getType() == SAFETY_EVENT_TYPE_RESOLVING_ACTION_SUCCEEDED) {
                        mNotificationSender.notifyActionSuccess(
                                safetySourceId, safetyEvent, userId);
                    }
                    mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
                }
            }
        }

        @Override
        @Nullable
        public SafetySourceData getSafetySourceData(
                String safetySourceId, String packageName, @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(packageName);
            enforceAnyCallingOrSelfPermissions(
                    "getSafetySourceData", SEND_SAFETY_CENTER_UPDATE, MANAGE_SAFETY_CENTER);
            if (!enforceCrossUserPermission("getSafetySourceData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("getSafetySourceData")) {
                return null;
            }

            synchronized (mApiLock) {
                return mSafetyCenterDataManager.getSafetySourceData(
                        safetySourceId, packageName, userId);
            }
        }

        @Override
        public void reportSafetySourceError(
                String safetySourceId,
                SafetySourceErrorDetails errorDetails,
                String packageName,
                @UserIdInt int userId) {
            requireNonNull(safetySourceId);
            requireNonNull(errorDetails);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            SEND_SAFETY_CENTER_UPDATE, "reportSafetySourceError");
            if (!enforceCrossUserPermission("reportSafetySourceError", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("reportSafetySourceError")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            synchronized (mApiLock) {
                boolean hasUpdate =
                        mSafetyCenterDataManager.reportSafetySourceError(
                                errorDetails, safetySourceId, packageName, userId);
                SafetyCenterErrorDetails safetyCenterErrorDetails = null;
                if (hasUpdate
                        && errorDetails.getSafetyEvent().getType()
                                == SAFETY_EVENT_TYPE_RESOLVING_ACTION_FAILED) {
                    safetyCenterErrorDetails =
                            new SafetyCenterErrorDetails(
                                    mSafetyCenterResourcesApk.getStringByName(
                                            "resolving_action_error"));
                }
                if (hasUpdate) {
                    mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
                }
                if (safetyCenterErrorDetails != null) {
                    mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                            userProfileGroup, safetyCenterErrorDetails);
                }
            }
        }

        @Override
        public void refreshSafetySources(@RefreshReason int refreshReason, @UserIdInt int userId) {
            RefreshReasons.validate(refreshReason);
            getContext().enforceCallingPermission(MANAGE_SAFETY_CENTER, "refreshSafetySources");
            if (!enforceCrossUserPermission("refreshSafetySources", userId)
                    || !checkApiEnabled("refreshSafetySources")) {
                return;
            }

            synchronized (mApiLock) {
                startRefreshingSafetySourcesLocked(refreshReason, userId);
            }
        }

        @Override
        @RequiresApi(UPSIDE_DOWN_CAKE)
        public void refreshSpecificSafetySources(
                @RefreshReason int refreshReason,
                @UserIdInt int userId,
                List<String> safetySourceIds) {
            requireNonNull(safetySourceIds, "safetySourceIds cannot be null");
            RefreshReasons.validate(refreshReason);
            getContext()
                    .enforceCallingPermission(MANAGE_SAFETY_CENTER, "refreshSpecificSafetySources");
            if (!enforceCrossUserPermission("refreshSpecificSafetySources", userId)
                    || !checkApiEnabled("refreshSpecificSafetySources")) {
                return;
            }

            synchronized (mApiLock) {
                startRefreshingSafetySourcesLocked(refreshReason, userId, safetySourceIds);
            }
        }

        @Override
        @Nullable
        public SafetyCenterConfig getSafetyCenterConfig() {
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterConfig");
            // We still return the SafetyCenterConfig object when the API is disabled, as Settings
            // search works by adding all the entries very rarely (and relies on filtering them out
            // instead).
            if (!canUseSafetyCenter()) {
                Log.i(TAG, "Called getSafetyCenterConfig, but Safety Center is not supported");
                return null;
            }

            synchronized (mApiLock) {
                return mSafetyCenterConfigReader.getSafetyCenterConfig();
            }
        }

        @Override
        public SafetyCenterData getSafetyCenterData(String packageName, @UserIdInt int userId) {
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(MANAGE_SAFETY_CENTER, "getSafetyCenterData");
            if (!enforceCrossUserPermission("getSafetyCenterData", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("getSafetyCenterData")) {
                return SafetyCenterDataFactory.getDefaultSafetyCenterData();
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            synchronized (mApiLock) {
                return mSafetyCenterDataFactory.assembleSafetyCenterData(
                        packageName, userProfileGroup);
            }
        }

        @Override
        public void addOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener,
                String packageName,
                @UserIdInt int userId) {
            requireNonNull(listener);
            requireNonNull(packageName);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "addOnSafetyCenterDataChangedListener");
            if (!enforceCrossUserPermission("addOnSafetyCenterDataChangedListener", userId)
                    || !enforcePackage(Binder.getCallingUid(), packageName, userId)
                    || !checkApiEnabled("addOnSafetyCenterDataChangedListener")) {
                return;
            }

            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            synchronized (mApiLock) {
                IOnSafetyCenterDataChangedListener registeredListener =
                        mSafetyCenterListeners.addListener(listener, packageName, userId);
                if (registeredListener == null) {
                    return;
                }
                SafetyCenterListeners.deliverDataForListener(
                        registeredListener,
                        mSafetyCenterDataFactory.assembleSafetyCenterData(
                                packageName, userProfileGroup));
            }
        }

        @Override
        public void removeOnSafetyCenterDataChangedListener(
                IOnSafetyCenterDataChangedListener listener, @UserIdInt int userId) {
            requireNonNull(listener);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "removeOnSafetyCenterDataChangedListener");
            if (!enforceCrossUserPermission("removeOnSafetyCenterDataChangedListener", userId)
                    || !checkApiEnabled("removeOnSafetyCenterDataChangedListener")) {
                return;
            }

            synchronized (mApiLock) {
                mSafetyCenterListeners.removeListener(listener, userId);
            }
        }

        @Override
        public void dismissSafetyCenterIssue(String issueId, @UserIdInt int userId) {
            requireNonNull(issueId);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "dismissSafetyCenterIssue");
            if (!enforceCrossUserPermission("dismissSafetyCenterIssue", userId)
                    || !checkApiEnabled("dismissSafetyCenterIssue")) {
                return;
            }

            SafetyCenterIssueId safetyCenterIssueId = SafetyCenterIds.issueIdFromString(issueId);
            SafetyCenterIssueKey safetyCenterIssueKey =
                    safetyCenterIssueId.getSafetyCenterIssueKey();
            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            enforceSameUserProfileGroup(
                    "dismissSafetyCenterIssue", userProfileGroup, safetyCenterIssueKey.getUserId());
            synchronized (mApiLock) {
                SafetySourceIssue safetySourceIssue =
                        mSafetyCenterDataManager.getSafetySourceIssue(safetyCenterIssueKey);
                if (safetySourceIssue == null) {
                    Log.w(TAG, "Attempt to dismiss an issue that is not provided by the source");
                    // Don't send the error to the UI here, since it could happen when clicking the
                    // button multiple times in a row (e.g. if the source is clearing the issue as a
                    // result of the onDismissPendingIntent).
                    return;
                }
                if (mSafetyCenterDataManager.isIssueDismissed(
                        safetyCenterIssueKey, safetySourceIssue.getSeverityLevel())) {
                    Log.w(TAG, "Attempt to dismiss an issue that is already dismissed");
                    // Don't send the error to the UI here, since it could happen when clicking the
                    // button multiple times in a row.
                    return;
                }
                mSafetyCenterDataManager.dismissSafetyCenterIssue(safetyCenterIssueKey);
                PendingIntent onDismissPendingIntent =
                        safetySourceIssue.getOnDismissPendingIntent();
                if (onDismissPendingIntent != null
                        && !dispatchPendingIntent(onDismissPendingIntent)) {
                    Log.w(
                            TAG,
                            "Error dispatching dismissal for issue: "
                                    + safetyCenterIssueKey.getSafetySourceIssueId()
                                    + ", of source: "
                                    + safetyCenterIssueKey.getSafetySourceId());
                    // We still consider the dismissal a success if there is an error dispatching
                    // the dismissal PendingIntent, since SafetyCenter won't surface this warning
                    // anymore.
                }
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
            }
        }

        @Override
        public void executeSafetyCenterIssueAction(
                String issueId, String issueActionId, @UserIdInt int userId) {
            requireNonNull(issueId);
            requireNonNull(issueActionId);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "executeSafetyCenterIssueAction");
            if (!enforceCrossUserPermission("executeSafetyCenterIssueAction", userId)
                    || !checkApiEnabled("executeSafetyCenterIssueAction")) {
                return;
            }

            SafetyCenterIssueId safetyCenterIssueId = SafetyCenterIds.issueIdFromString(issueId);
            SafetyCenterIssueKey safetyCenterIssueKey =
                    safetyCenterIssueId.getSafetyCenterIssueKey();
            SafetyCenterIssueActionId safetyCenterIssueActionId =
                    SafetyCenterIds.issueActionIdFromString(issueActionId);
            if (!safetyCenterIssueActionId.getSafetyCenterIssueKey().equals(safetyCenterIssueKey)) {
                throw new IllegalArgumentException(
                        toUserFriendlyString(safetyCenterIssueId)
                                + " and "
                                + toUserFriendlyString(safetyCenterIssueActionId)
                                + " do not match");
            }
            UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
            enforceSameUserProfileGroup(
                    "executeSafetyCenterIssueAction",
                    userProfileGroup,
                    safetyCenterIssueKey.getUserId());
            Integer taskId =
                    safetyCenterIssueId.hasTaskId() ? safetyCenterIssueId.getTaskId() : null;
            executeIssueActionInternal(safetyCenterIssueActionId, userProfileGroup, taskId);
        }

        @Override
        public void clearAllSafetySourceDataForTests() {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "clearAllSafetySourceDataForTests");
            if (!checkApiEnabled("clearAllSafetySourceDataForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        @Override
        public void setSafetyCenterConfigForTests(SafetyCenterConfig safetyCenterConfig) {
            requireNonNull(safetyCenterConfig);
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "setSafetyCenterConfigForTests");
            if (!checkApiEnabled("setSafetyCenterConfigForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                mSafetyCenterConfigReader.setConfigOverrideForTests(safetyCenterConfig);
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        @Override
        public void clearSafetyCenterConfigForTests() {
            getContext()
                    .enforceCallingOrSelfPermission(
                            MANAGE_SAFETY_CENTER, "clearSafetyCenterConfigForTests");
            if (!checkApiEnabled("clearSafetyCenterConfigForTests")) {
                return;
            }

            List<UserProfileGroup> userProfileGroups =
                    UserProfileGroup.getAllUserProfileGroups(getContext());
            synchronized (mApiLock) {
                mSafetyCenterConfigReader.clearConfigOverrideForTests();
                // TODO(b/236693607): Should tests leave real data untouched?
                clearDataLocked();
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroups);
            }
        }

        private boolean isApiEnabled() {
            return canUseSafetyCenter() && SafetyCenterFlags.getSafetyCenterEnabled();
        }

        private void enforceAnyCallingOrSelfPermissions(String message, String... permissions) {
            if (permissions.length == 0) {
                throw new IllegalArgumentException("Must check at least one permission");
            }
            for (int i = 0; i < permissions.length; i++) {
                if (getContext().checkCallingOrSelfPermission(permissions[i])
                        == PERMISSION_GRANTED) {
                    return;
                }
            }
            throw new SecurityException(
                    message
                            + " requires any of: "
                            + Arrays.toString(permissions)
                            + ", but none were granted");
        }

        /** Enforces cross user permission and returns whether the user is valid. */
        private boolean enforceCrossUserPermission(String message, @UserIdInt int userId) {
            UserUtils.enforceCrossUserPermission(
                    userId, /* allowAll= */ false, message, getContext());
            if (!UserUtils.isUserExistent(userId, getContext())) {
                Log.w(
                        TAG,
                        "Called "
                                + message
                                + " with user id: "
                                + userId
                                + ", which does not correspond to an existing user");
                return false;
            }
            if (!UserProfileGroup.isSupported(userId, getContext())) {
                Log.w(
                        TAG,
                        "Called "
                                + message
                                + " with user id: "
                                + userId
                                + ", which is an unsupported user");
                return false;
            }
            return true;
        }

        /**
         * Returns {@code true} if the {@code packageName} exists and it belongs to the {@code
         * callingUid}.
         *
         * <p>Throws a {@link SecurityException} if the {@code packageName} does not belong to the
         * {@code callingUid}.
         */
        private boolean enforcePackage(int callingUid, String packageName, @UserIdInt int userId) {
            if (TextUtils.isEmpty(packageName)) {
                throw new IllegalArgumentException("packageName may not be empty");
            }
            int actualUid;
            PackageManager packageManager = getContext().getPackageManager();
            try {
                actualUid =
                        packageManager.getPackageUidAsUser(
                                packageName, PackageInfoFlags.of(0), userId);
            } catch (NameNotFoundException e) {
                Log.w(TAG, "Package: " + packageName + ", not found for user id: " + userId, e);
                return false;
            }
            if (callingUid == Process.ROOT_UID || callingUid == Process.SYSTEM_UID) {
                return true;
            }
            if (UserHandle.getAppId(callingUid) != UserHandle.getAppId(actualUid)) {
                throw new SecurityException(
                        "Package: "
                                + packageName
                                + ", does not belong to calling uid: "
                                + callingUid);
            }
            return true;
        }

        private boolean checkApiEnabled(String message) {
            if (!isApiEnabled()) {
                Log.w(TAG, "Called " + message + ", but Safety Center is disabled");
                return false;
            }
            return true;
        }

        private void enforceSameUserProfileGroup(
                String message, UserProfileGroup userProfileGroup, @UserIdInt int userId) {
            if (!userProfileGroup.contains(userId)) {
                throw new SecurityException(
                        message
                                + " requires target user id "
                                + userId
                                + " to be within the same profile group of the caller: "
                                + userProfileGroup);
            }
        }

        @Override
        public int handleShellCommand(
                ParcelFileDescriptor in,
                ParcelFileDescriptor out,
                ParcelFileDescriptor err,
                String[] args) {
            return new SafetyCenterShellCommandHandler(
                            getContext(),
                            /* safetyCenterManager= */ this,
                            mDeviceSupportsSafetyCenter)
                    .exec(
                            /* target= */ this,
                            in.getFileDescriptor(),
                            out.getFileDescriptor(),
                            err.getFileDescriptor(),
                            args);
        }

        /** Dumps state for debugging purposes. */
        @Override
        protected void dump(FileDescriptor fd, PrintWriter fout, @Nullable String[] args) {
            if (!checkDumpPermission(fout)) {
                return;
            }
            List<String> subjects = Arrays.asList(args);
            boolean all = subjects.isEmpty();
            synchronized (mApiLock) {
                if (all || subjects.contains("service")) {
                    SafetyCenterService.this.dumpLocked(fout);
                }
                if (all || subjects.contains("flags")) {
                    SafetyCenterFlags.dump(fout);
                }
                if (all || subjects.contains("config")) {
                    mSafetyCenterConfigReader.dump(fout);
                }
                if (all || subjects.contains("data")) {
                    mSafetyCenterDataManager.dump(fd, fout);
                }
                if (all || subjects.contains("refresh")) {
                    mSafetyCenterRefreshTracker.dump(fout);
                }
                if (all || subjects.contains("timeouts")) {
                    mSafetyCenterTimeouts.dump(fout);
                }
                if (all || subjects.contains("listeners")) {
                    mSafetyCenterListeners.dump(fout);
                }
                if (all || subjects.contains("notifications")) {
                    mNotificationSender.dump(fout);
                }
            }
        }

        private boolean checkDumpPermission(PrintWriter writer) {
            if (getContext().checkCallingOrSelfPermission(android.Manifest.permission.DUMP)
                    != PERMISSION_GRANTED) {
                writer.println(
                        "Permission Denial: can't dump "
                                + "safety_center"
                                + " from from pid="
                                + Binder.getCallingPid()
                                + ", uid="
                                + Binder.getCallingUid()
                                + " due to missing "
                                + android.Manifest.permission.DUMP
                                + " permission");
                return false;
            } else {
                return true;
            }
        }
    }

    /**
     * An {@link OnPropertiesChangedListener} for {@link
     * SafetyCenterFlags#PROPERTY_SAFETY_CENTER_ENABLED} that sends broadcasts when the SafetyCenter
     * property is enabled or disabled.
     *
     * <p>This listener assumes that the {@link SafetyCenterFlags#PROPERTY_SAFETY_CENTER_ENABLED}
     * value maps to {@link SafetyCenterManager#isSafetyCenterEnabled()}. It should only be
     * registered if the device supports SafetyCenter and the {@link SafetyCenterConfig} was loaded
     * successfully.
     */
    private final class SafetyCenterEnabledListener implements OnPropertiesChangedListener {

        @GuardedBy("mApiLock")
        private boolean mSafetyCenterEnabled;

        @Override
        public void onPropertiesChanged(DeviceConfig.Properties properties) {
            if (!properties.getKeyset().contains(PROPERTY_SAFETY_CENTER_ENABLED)) {
                return;
            }
            boolean safetyCenterEnabled =
                    properties.getBoolean(PROPERTY_SAFETY_CENTER_ENABLED, SdkLevel.isAtLeastU());
            synchronized (mApiLock) {
                if (mSafetyCenterEnabled == safetyCenterEnabled) {
                    Log.i(
                            TAG,
                            "Safety Center is already "
                                    + (mSafetyCenterEnabled ? "enabled" : "disabled")
                                    + ", ignoring change");
                    return;
                }
                onSafetyCenterEnabledChangedLocked(safetyCenterEnabled);
            }
        }

        @GuardedBy("mApiLock")
        private void setInitialStateLocked() {
            mSafetyCenterEnabled = SafetyCenterFlags.getSafetyCenterEnabled();
            if (mSafetyCenterEnabled) {
                onApiInitEnabledLocked();
            }
            Log.i(TAG, "Safety Center is " + (mSafetyCenterEnabled ? "enabled" : "disabled"));
        }

        @GuardedBy("mApiLock")
        private void onSafetyCenterEnabledChangedLocked(boolean safetyCenterEnabled) {
            if (safetyCenterEnabled) {
                onApiEnabledLocked();
            } else {
                onApiDisabledLocked();
            }

            mSafetyCenterEnabled = safetyCenterEnabled;
            Log.i(TAG, "Safety Center is now " + (mSafetyCenterEnabled ? "enabled" : "disabled"));
        }

        @GuardedBy("mApiLock")
        private void onApiInitEnabledLocked() {
            mNotificationChannels.createAllChannelsForAllUsers(getContext());
        }

        @GuardedBy("mApiLock")
        private void onApiEnabledLocked() {
            mNotificationChannels.createAllChannelsForAllUsers(getContext());
            mSafetyCenterBroadcastDispatcher.sendEnabledChanged();
        }

        @GuardedBy("mApiLock")
        private void onApiDisabledLocked() {
            // We're not clearing the Safety Center notification channels here. The reason for this
            // is that the NotificationManager will post a runnable to cancel all associated
            // notifications when clearing the channels. Given this happens asynchronously, this can
            // leak between test cases and cause notifications that should be active to be cleared
            // inadvertently. We're ok with the inconsistency because the channels are hidden
            // somewhat deeply under Settings anyway, and we're unlikely to turn off Safety Center
            // in production.
            clearDataLocked();
            mSafetyCenterListeners.clear();
            mSafetyCenterBroadcastDispatcher.sendEnabledChanged();
        }
    }

    /** A {@link Runnable} that is called to signal a refresh timeout. */
    private final class RefreshTimeout implements Runnable {

        private final String mRefreshBroadcastId;
        @RefreshReason private final int mRefreshReason;
        private final UserProfileGroup mUserProfileGroup;

        RefreshTimeout(
                String refreshBroadcastId,
                @RefreshReason int refreshReason,
                UserProfileGroup userProfileGroup) {
            mRefreshBroadcastId = refreshBroadcastId;
            mRefreshReason = refreshReason;
            mUserProfileGroup = userProfileGroup;
        }

        @Override
        public void run() {
            synchronized (mApiLock) {
                mSafetyCenterTimeouts.remove(this);
                ArraySet<SafetySourceKey> stillInFlight =
                        mSafetyCenterRefreshTracker.timeoutRefresh(mRefreshBroadcastId);
                if (stillInFlight == null) {
                    return;
                }
                boolean setError = !RefreshReasons.isBackgroundRefresh(mRefreshReason);
                for (int i = 0; i < stillInFlight.size(); i++) {
                    mSafetyCenterDataManager.markSafetySourceRefreshTimedOut(
                            stillInFlight.valueAt(i), setError);
                }
                mSafetyCenterDataChangeNotifier.updateDataConsumers(mUserProfileGroup);
            }
        }

        @Override
        public String toString() {
            return "RefreshTimeout{"
                    + "mRefreshBroadcastId='"
                    + mRefreshBroadcastId
                    + '\''
                    + ", mUserProfileGroup="
                    + mUserProfileGroup
                    + '}';
        }
    }

    /** A {@link Runnable} that is called to signal a resolving action timeout. */
    private final class ResolvingActionTimeout implements Runnable {

        private final SafetyCenterIssueActionId mSafetyCenterIssueActionId;
        private final UserProfileGroup mUserProfileGroup;

        ResolvingActionTimeout(
                SafetyCenterIssueActionId safetyCenterIssueActionId,
                UserProfileGroup userProfileGroup) {
            mSafetyCenterIssueActionId = safetyCenterIssueActionId;
            mUserProfileGroup = userProfileGroup;
        }

        @Override
        public void run() {
            synchronized (mApiLock) {
                mSafetyCenterTimeouts.remove(this);
                SafetySourceIssue safetySourceIssue =
                        mSafetyCenterDataManager.getSafetySourceIssue(
                                mSafetyCenterIssueActionId.getSafetyCenterIssueKey());
                boolean safetyCenterDataHasChanged =
                        mSafetyCenterDataManager.unmarkSafetyCenterIssueActionInFlight(
                                mSafetyCenterIssueActionId,
                                safetySourceIssue,
                                SAFETY_CENTER_SYSTEM_EVENT_REPORTED__RESULT__TIMEOUT);
                if (!safetyCenterDataHasChanged) {
                    return;
                }
                mSafetyCenterDataChangeNotifier.updateDataConsumers(mUserProfileGroup);
                mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                        mUserProfileGroup,
                        new SafetyCenterErrorDetails(
                                mSafetyCenterResourcesApk.getStringByName(
                                        "resolving_action_error")));
                Log.w(
                        TAG,
                        "Resolving action timed out for: "
                                + toUserFriendlyString(mSafetyCenterIssueActionId));
            }
        }

        @Override
        public String toString() {
            return "ResolvingActionTimeout{"
                    + "mSafetyCenterIssueActionId="
                    + toUserFriendlyString(mSafetyCenterIssueActionId)
                    + ", mUserProfileGroup="
                    + mUserProfileGroup
                    + '}';
        }
    }

    private boolean canUseSafetyCenter() {
        return mDeviceSupportsSafetyCenter && mConfigAvailable;
    }

    /** {@link BroadcastReceiver} which handles Locale changes. */
    private final class LocaleBroadcastReceiver extends BroadcastReceiver {

        private static final String TAG = "SafetyCenterLocaleBroad";

        void register(Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_LOCALE_CHANGED);
            context.registerReceiverForAllUsers(
                    /* receiver= */ this,
                    filter,
                    /* broadcastPermission= */ null,
                    /* scheduler= */ null);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SafetyCenterFlags.getSafetyCenterEnabled()) {
                Log.i(TAG, "Safety Center is disabled, ignoring intent: " + intent);
                return;
            }

            String action = intent.getAction();
            if (!TextUtils.equals(action, Intent.ACTION_LOCALE_CHANGED)) {
                Log.w(TAG, "Received unexpected action: " + action);
                return;
            }

            Log.d(TAG, "Locale changed broadcast received");

            int userId = ActivityManager.getCurrentUser();
            synchronized (mApiLock) {
                startRefreshingSafetySourcesLocked(REFRESH_REASON_DEVICE_LOCALE_CHANGE, userId);
                mNotificationChannels.createAllChannelsForUser(getContext(), UserHandle.of(userId));
            }
        }
    }

    /**
     * {@link BroadcastReceiver} which handles user and work profile related broadcasts that Safety
     * Center is interested including quiet mode turning on/off and accounts being added/removed.
     */
    private final class UserBroadcastReceiver extends BroadcastReceiver {

        private static final String TAG = "SafetyCenterUserBroadca";

        void register(Context context) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(Intent.ACTION_USER_SWITCHED);
            filter.addAction(Intent.ACTION_USER_REMOVED);
            if (SdkLevel.isAtLeastV() && Flags.privateProfileSupported()) {
                // These intents are available on V+ only, and are called for managed and other
                // profile(s).
                filter.addAction(Intent.ACTION_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_PROFILE_REMOVED);
                filter.addAction(Intent.ACTION_PROFILE_AVAILABLE);
                filter.addAction(Intent.ACTION_PROFILE_UNAVAILABLE);
            } else {
                // Only these intents are available in T and U, but that's okay because only managed
                // profiles are supported by Safety Center on these SDK versions.
                filter.addAction(Intent.ACTION_MANAGED_PROFILE_ADDED);
                filter.addAction(Intent.ACTION_MANAGED_PROFILE_REMOVED);
                filter.addAction(Intent.ACTION_MANAGED_PROFILE_AVAILABLE);
                filter.addAction(Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE);
            }
            context.registerReceiverForAllUsers(
                    /* receiver= */ this,
                    filter,
                    /* broadcastPermission= */ null,
                    /* scheduler= */ null);
        }

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!SafetyCenterFlags.getSafetyCenterEnabled()) {
                Log.i(TAG, "Safety Center is disabled, ignoring intent: " + intent);
                return;
            }

            String action = intent.getAction();
            if (action == null) {
                Log.w(TAG, "Received broadcast with null action");
                return;
            }

            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
            if (userHandle == null) {
                Log.w(TAG, "Received action: " + action + ", but missing user extra");
                return;
            }

            int userId = userHandle.getIdentifier();
            Log.d(TAG, "Received action: " + action + ", for user id: " + userId);

            if (!isUserIdValidForAction(action, userId, context)) {
                return;
            }

            if (isUserOrProfileRemoved(action)) {
                removeUserAndData(userId);
                return;
            }

            if (isProfileUnavailable(action)) {
                removeUser(userId);
                return;
            }

            if (Intent.ACTION_USER_SWITCHED.equals(action) || isProfileAddedOrAvailable(action)) {
                synchronized (mApiLock) {
                    startRefreshingSafetySourcesLocked(REFRESH_REASON_OTHER, userId);
                    mNotificationChannels.createAllChannelsForUser(getContext(), userHandle);
                }
                return;
            }
            Log.w(TAG, "Received unexpected broadcast with action: " + action);
        }
    }

    private static boolean isUserIdValidForAction(
            String action, @UserIdInt int userId, Context context) {
        if (!UserProfileGroup.isSupported(userId, context)) {
            Log.i(
                    TAG,
                    "Received broadcast for user id: "
                            + userId
                            + ", which is an unsupported user");
            return false;
        }
        if (Intent.ACTION_USER_SWITCHED.equals(action)
                && userId != ActivityManager.getCurrentUser()) {
            Log.w(
                    TAG,
                    "Received broadcast for user id: "
                            + userId
                            + ", which is not the current user");
            return false;
        }
        if (isProfileAddedOrAvailable(action) && !UserUtils.isUserExistent(userId, context)) {
            Log.w(
                    TAG,
                    "Received broadcast for user id: "
                            + userId
                            + ", which does not exist");
            return false;
        }
        return true;
    }

    private static boolean isUserOrProfileRemoved(String action) {
        if (Intent.ACTION_USER_REMOVED.equals(action)) {
            return true;
        }
        if (SdkLevel.isAtLeastV() && Flags.privateProfileSupported()) {
            return Intent.ACTION_PROFILE_REMOVED.equals(action);
        }
        return Intent.ACTION_MANAGED_PROFILE_REMOVED.equals(action);
    }

    private static boolean isProfileUnavailable(String action) {
        if (SdkLevel.isAtLeastV() && Flags.privateProfileSupported()) {
            return Intent.ACTION_PROFILE_UNAVAILABLE.equals(action);
        }
        return Intent.ACTION_MANAGED_PROFILE_UNAVAILABLE.equals(action);
    }

    private static boolean isProfileAddedOrAvailable(String action) {
        if (SdkLevel.isAtLeastV() && Flags.privateProfileSupported()) {
            return Intent.ACTION_PROFILE_AVAILABLE.equals(action)
                    || Intent.ACTION_PROFILE_ADDED.equals(action);
        }
        return Intent.ACTION_MANAGED_PROFILE_ADDED.equals(action)
                || Intent.ACTION_MANAGED_PROFILE_AVAILABLE.equals(action);
    }

    private void removeUserAndData(@UserIdInt int userId) {
        removeUser(userId, /* clearDataPermanently= */ true);
    }

    private void removeUser(@UserIdInt int userId) {
        removeUser(userId, /* clearDataPermanently= */ false);
    }

    private void removeUser(@UserIdInt int userId, boolean clearDataPermanently) {
        UserProfileGroup userProfileGroup = UserProfileGroup.fromUser(getContext(), userId);
        synchronized (mApiLock) {
            mSafetyCenterListeners.clearForUser(userId);
            mSafetyCenterRefreshTracker.clearRefreshForUser(userId);

            if (clearDataPermanently) {
                mSafetyCenterDataManager.clearForUser(userId);
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup, userId);
            } else {
                mSafetyCenterListeners.deliverDataForUserProfileGroup(userProfileGroup);
            }
        }
    }

    @GuardedBy("mApiLock")
    private void startRefreshingSafetySourcesLocked(
            @RefreshReason int refreshReason, @UserIdInt int userId) {
        startRefreshingSafetySourcesLocked(
                refreshReason,
                UserProfileGroup.fromUser(getContext(), userId),
                /* selectedSafetySourceIds= */ null);
    }

    @GuardedBy("mApiLock")
    private void startRefreshingSafetySourcesLocked(
            @RefreshReason int refreshReason,
            @UserIdInt int userId,
            List<String> selectedSafetySourceIds) {
        startRefreshingSafetySourcesLocked(
                refreshReason,
                UserProfileGroup.fromUser(getContext(), userId),
                selectedSafetySourceIds);
    }

    @GuardedBy("mApiLock")
    private void startRefreshingSafetySourcesLocked(
            @RefreshReason int refreshReason,
            UserProfileGroup userProfileGroup,
            @Nullable List<String> selectedSafetySourceIds) {
        String refreshBroadcastId =
                mSafetyCenterBroadcastDispatcher.sendRefreshSafetySources(
                        refreshReason, userProfileGroup, selectedSafetySourceIds);
        if (refreshBroadcastId == null) {
            return;
        }

        RefreshTimeout refreshTimeout =
                new RefreshTimeout(refreshBroadcastId, refreshReason, userProfileGroup);
        mSafetyCenterTimeouts.add(
                refreshTimeout, SafetyCenterFlags.getRefreshSourcesTimeout(refreshReason));

        mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup);
    }

    /**
     * Executes the {@link SafetySourceIssue.Action} specified by the given {@link
     * SafetyCenterIssueActionId}.
     *
     * <p>No validation is performed on the contents of the given ID.
     */
    public void executeIssueActionInternal(SafetyCenterIssueActionId safetyCenterIssueActionId) {
        SafetyCenterIssueKey safetyCenterIssueKey =
                safetyCenterIssueActionId.getSafetyCenterIssueKey();
        UserProfileGroup userProfileGroup =
                UserProfileGroup.fromUser(getContext(), safetyCenterIssueKey.getUserId());
        executeIssueActionInternal(safetyCenterIssueActionId, userProfileGroup, /* taskId= */ null);
    }

    private void executeIssueActionInternal(
            SafetyCenterIssueActionId safetyCenterIssueActionId,
            UserProfileGroup userProfileGroup,
            @Nullable Integer taskId) {
        synchronized (mApiLock) {
            SafetySourceIssue.Action safetySourceIssueAction =
                    mSafetyCenterDataManager.getSafetySourceIssueAction(safetyCenterIssueActionId);

            if (safetySourceIssueAction == null) {
                Log.w(
                        TAG,
                        "Attempt to execute an issue action that is not provided by the source,"
                                + " that was dismissed, or is already in flight");
                // Don't send the error to the UI here, since it could happen when clicking the
                // button multiple times in a row.
                return;
            }
            PendingIntent issueActionPendingIntent = safetySourceIssueAction.getPendingIntent();
            if (!dispatchPendingIntent(issueActionPendingIntent, taskId)) {
                Log.w(
                        TAG,
                        "Error dispatching action: "
                                + toUserFriendlyString(safetyCenterIssueActionId));
                CharSequence errorMessage;
                if (safetySourceIssueAction.willResolve()) {
                    errorMessage =
                            mSafetyCenterResourcesApk.getStringByName("resolving_action_error");
                } else {
                    errorMessage = mSafetyCenterResourcesApk.getStringByName("redirecting_error");
                }
                mSafetyCenterListeners.deliverErrorForUserProfileGroup(
                        userProfileGroup, new SafetyCenterErrorDetails(errorMessage));
                return;
            }
            if (safetySourceIssueAction.willResolve()) {
                Log.d(
                        TAG,
                        "Starting resolving action for: "
                                + toUserFriendlyString(safetyCenterIssueActionId));
                mSafetyCenterDataManager.markSafetyCenterIssueActionInFlight(
                        safetyCenterIssueActionId);
                ResolvingActionTimeout resolvingActionTimeout =
                        new ResolvingActionTimeout(safetyCenterIssueActionId, userProfileGroup);
                mSafetyCenterTimeouts.add(
                        resolvingActionTimeout, SafetyCenterFlags.getResolvingActionTimeout());
                mSafetyCenterDataChangeNotifier.updateDataConsumers(userProfileGroup);
            }
        }
    }

    private boolean dispatchPendingIntent(PendingIntent pendingIntent) {
        return dispatchPendingIntent(pendingIntent, /* launchTaskId= */ null);
    }

    private boolean dispatchPendingIntent(
            PendingIntent pendingIntent, @Nullable Integer launchTaskId) {
        if (launchTaskId != null
                && getContext().checkCallingOrSelfPermission(START_TASKS_FROM_RECENTS)
                        != PERMISSION_GRANTED) {
            launchTaskId = null;
        }
        return PendingIntentSender.trySend(pendingIntent, launchTaskId);
    }

    @GuardedBy("mApiLock")
    private void clearDataLocked() {
        mSafetyCenterDataManager.clear();
        mSafetyCenterTimeouts.clear();
        mSafetyCenterRefreshTracker.clearRefresh();
        mNotificationSender.cancelAllNotifications();
    }

    /** Dumps state for debugging purposes. */
    @GuardedBy("mApiLock")
    private void dumpLocked(PrintWriter fout) {
        fout.println("SERVICE");
        fout.println(
                "\tSafetyCenterService{"
                        + "mDeviceSupportsSafetyCenter="
                        + mDeviceSupportsSafetyCenter
                        + ", mConfigAvailable="
                        + mConfigAvailable
                        + '}');
        fout.println();
    }
}
