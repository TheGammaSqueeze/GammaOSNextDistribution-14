/*
 * Copyright (C) 2022 The Android Open Source Project
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
package com.android.server.adservices;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_MANAGER;
import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;

import android.adservices.common.AdServicesPermissions;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.app.adservices.AdServicesManager;
import android.app.adservices.IAdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.adservices.topics.TopicParcel;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.VersionedPackage;
import android.content.rollback.PackageRollbackInfo;
import android.content.rollback.RollbackInfo;
import android.content.rollback.RollbackManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.ParcelFileDescriptor;
import android.os.UserHandle;
import android.provider.DeviceConfig;
import android.util.ArrayMap;
import android.util.Dumpable;

import com.android.adservices.service.CommonFlagsConstants;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BackgroundThread;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;
import com.android.server.sdksandbox.SdkSandboxManagerLocal;

import java.io.FileDescriptor;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** @hide */
// TODO(b/267667963): Offload methods from binder thread to background thread.
public class AdServicesManagerService extends IAdServicesManager.Stub {
    // The base directory for AdServices System Service.
    private static final String SYSTEM_DATA = "/data/system/";
    public static String ADSERVICES_BASE_DIR = SYSTEM_DATA + "adservices";
    private static final String ERROR_MESSAGE_NOT_PERMITTED_TO_CALL_ADSERVICESMANAGER_API =
            "Unauthorized caller. Permission to call AdServicesManager API is not granted in System"
                    + " Server.";
    private final Object mRegisterReceiverLock = new Object();
    private final Object mRollbackCheckLock = new Object();
    private final Object mSetPackageVersionLock = new Object();

    /**
     * Broadcast send from the system service to the AdServices module when a package has been
     * installed/uninstalled. This intent must match the intent defined in the AdServices manifest.
     */
    private static final String PACKAGE_CHANGED_BROADCAST =
            "com.android.adservices.PACKAGE_CHANGED";

    /** Key for designating the specific action. */
    private static final String ACTION_KEY = "action";

    /** Value if the package change was an uninstallation. */
    private static final String PACKAGE_FULLY_REMOVED = "package_fully_removed";

    /** Value if the package change was an installation. */
    private static final String PACKAGE_ADDED = "package_added";

    /** Value if the package has its data cleared. */
    private static final String PACKAGE_DATA_CLEARED = "package_data_cleared";

    private final Context mContext;

    @GuardedBy("mRegisterReceiverLock")
    private BroadcastReceiver mSystemServicePackageChangedReceiver;

    @GuardedBy("mRegisterReceiverLock")
    private BroadcastReceiver mSystemServiceUserActionReceiver;

    @GuardedBy("mRegisterReceiverLock")
    private HandlerThread mHandlerThread;

    @GuardedBy("mRegisterReceiverLock")
    private Handler mHandler;

    @GuardedBy("mSetPackageVersionLock")
    private int mAdServicesModuleVersion;

    @GuardedBy("mSetPackageVersionLock")
    private String mAdServicesModuleName;

    @GuardedBy("mRollbackCheckLock")
    private final Map<Integer, VersionedPackage> mAdServicesPackagesRolledBackFrom =
            new ArrayMap<>();

    @GuardedBy("mRollbackCheckLock")
    private final Map<Integer, VersionedPackage> mAdServicesPackagesRolledBackTo = new ArrayMap<>();

    // This will be triggered when there is a flag change.
    private final DeviceConfig.OnPropertiesChangedListener mOnFlagsChangedListener =
            properties -> {
                if (!properties.getNamespace().equals(DeviceConfig.NAMESPACE_ADSERVICES)) {
                    return;
                }
                registerReceivers();
                setAdServicesApexVersion();
                setRollbackStatus();
            };

    private final UserInstanceManager mUserInstanceManager;

    @VisibleForTesting
    AdServicesManagerService(Context context, UserInstanceManager userInstanceManager) {
        mContext = context;
        mUserInstanceManager = userInstanceManager;

        // TODO(b/298635325): use AdServices shared background thread pool instead.
        DeviceConfig.addOnPropertiesChangedListener(
                DeviceConfig.NAMESPACE_ADSERVICES,
                BackgroundThread.getExecutor(),
                mOnFlagsChangedListener);

        registerReceivers();
        setAdServicesApexVersion();
        setRollbackStatus();
    }

    /** @hide */
    public static final class Lifecycle extends SystemService implements Dumpable {
        private final AdServicesManagerService mService;

        /** @hide */
        public Lifecycle(Context context) {
            this(
                    context,
                    new AdServicesManagerService(
                            context,
                            new UserInstanceManager(
                                    TopicsDao.getInstance(context), ADSERVICES_BASE_DIR)));
        }

        /** @hide */
        @VisibleForTesting
        public Lifecycle(Context context, AdServicesManagerService service) {
            super(context);
            mService = service;
            LogUtil.d("AdServicesManagerService constructed!");
        }

        /** @hide */
        @Override
        public void onStart() {
            LogUtil.d("AdServicesManagerService started!");

            boolean published = false;

            try {
                publishBinderService();
                published = true;
            } catch (RuntimeException e) {
                LogUtil.w(
                        e,
                        "Failed to publish %s service; will piggyback it into SdkSandbox anyways",
                        AD_SERVICES_SYSTEM_SERVICE);
            }

            // TODO(b/282239822): Remove this workaround (and try-catch above) on Android VIC

            // Register the AdServicesManagerService with the SdkSandboxManagerService.
            // This is a workaround for b/262282035.
            // This works since we start the SdkSandboxManagerService before the
            // AdServicesManagerService in the SystemServer.java
            SdkSandboxManagerLocal sdkSandboxManagerLocal =
                    LocalManagerRegistry.getManager(SdkSandboxManagerLocal.class);
            if (sdkSandboxManagerLocal != null) {
                sdkSandboxManagerLocal.registerAdServicesManagerService(mService, published);
            } else {
                throw new IllegalStateException(
                        "SdkSandboxManagerLocal not found when registering AdServicesManager!");
            }
        }

        // Need to encapsulate call to publishBinderService(...) because:
        // - Superclass method is protected final (hence it cannot be mocked or extended)
        // - Underlying method calls ServiceManager.addService(), which is hidden (and hence cannot
        //   be mocked by our tests)
        @VisibleForTesting
        void publishBinderService() {
            publishBinderService(AD_SERVICES_SYSTEM_SERVICE, mService);
        }

        @Override
        public String getDumpableName() {
            return "AdServices";
        }

        @Override
        public void dump(PrintWriter writer, String[] args) {
            // Dumps the service when it could not be published as a binder service.
            // Usage: adb shell dumpsys system_server_dumper --name AdServices
            mService.dump(/* fd= */ null, writer, args);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        return executeGetter(/* defaultReturn= */
                ConsentParcel.createRevokedConsent(consentApiType),
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getConsent(consentApiType));
    }

    // Return the User Identifier from the CallingUid.
    private int getUserIdentifierFromBinderCallingUid() {
        return UserHandle.getUserHandleForUid(Binder.getCallingUid()).getIdentifier();
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setConsent(ConsentParcel consentParcel) {
        enforceAdServicesManagerPermission();

        Objects.requireNonNull(consentParcel);

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setConsent(consentParcel);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to persist the consent.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordNotificationDisplayed(wasNotificationDisplayed);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to Record Notification Displayed.");
        }
    }

    /**
     * Record blocked topics.
     *
     * @param blockedTopicParcels the blocked topics to record
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void recordBlockedTopic(@NonNull List<TopicParcel> blockedTopicParcels) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordBlockedTopic() for User Identifier %d", userIdentifier);
        mUserInstanceManager
                .getOrCreateUserBlockedTopicsManagerInstance(userIdentifier)
                .recordBlockedTopic(blockedTopicParcels);
    }

    /**
     * Remove a blocked topic.
     *
     * @param blockedTopicParcel the blocked topic to remove
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void removeBlockedTopic(@NonNull TopicParcel blockedTopicParcel) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("removeBlockedTopic() for User Identifier %d", userIdentifier);
        mUserInstanceManager
                .getOrCreateUserBlockedTopicsManagerInstance(userIdentifier)
                .removeBlockedTopic(blockedTopicParcel);
    }

    /**
     * Get all blocked topics.
     *
     * @return a {@code List} of all blocked topics.
     */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public List<TopicParcel> retrieveAllBlockedTopics() {
        return executeGetter(/* defaultReturn= */ List.of(),
                (userId) -> mUserInstanceManager
                        .getOrCreateUserBlockedTopicsManagerInstance(userId)
                        .retrieveAllBlockedTopics());
    }

    /** Clear all Blocked Topics */
    @Override
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    public void clearAllBlockedTopics() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("clearAllBlockedTopics() for User Identifier %d", userIdentifier);
        mUserInstanceManager
                .getOrCreateUserBlockedTopicsManagerInstance(userIdentifier)
                .clearAllBlockedTopics();
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasNotificationDisplayed() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .wasNotificationDisplayed());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordGaUxNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordGaUxNotificationDisplayed(wasNotificationDisplayed);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record GA UX Notification Displayed.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordDefaultConsent(boolean defaultConsent) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordDefaultConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordDefaultConsent(defaultConsent);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to record default consent: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordTopicsDefaultConsent(boolean defaultConsent) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordTopicsDefaultConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordTopicsDefaultConsent(defaultConsent);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to record topics default consent: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordFledgeDefaultConsent(boolean defaultConsent) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordFledgeDefaultConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordFledgeDefaultConsent(defaultConsent);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to record fledge default consent: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordMeasurementDefaultConsent(boolean defaultConsent) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordMeasurementDefaultConsent() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordMeasurementDefaultConsent(defaultConsent);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to record measurement default consent: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordDefaultAdIdState() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordDefaultAdIdState(defaultAdIdState);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to record default AdId state: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordUserManualInteractionWithConsent(int interaction) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "recordUserManualInteractionWithConsent() for User Identifier %d, interaction %d",
                userIdentifier, interaction);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordUserManualInteractionWithConsent(interaction);
        } catch (IOException e) {
            LogUtil.e(
                    e, "Fail to record default manual interaction with consent: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean getTopicsDefaultConsent() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getTopicsDefaultConsent());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean getFledgeDefaultConsent() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getFledgeDefaultConsent());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean getMeasurementDefaultConsent() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getMeasurementDefaultConsent());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean getDefaultAdIdState() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getDefaultAdIdState());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public int getUserManualInteractionWithConsent() {
        return executeGetter(/* defaultReturn= */ 0,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getUserManualInteractionWithConsent());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasGaUxNotificationDisplayed() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .wasGaUxNotificationDisplayed());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordPasNotificationDisplayed(boolean wasNotificationDisplayed) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("recordPasNotificationDisplayed() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .recordPasNotificationDisplayed(wasNotificationDisplayed);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to Record PAS Notification Displayed.");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasPasNotificationDisplayed() {
        return executeGetter(
                /* defaultReturn= */ true,
                (userId) ->
                        mUserInstanceManager
                                .getOrCreateUserConsentManagerInstance(userId)
                                .wasPasNotificationDisplayed());
    }

    /** retrieves the default consent of a user. */
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean getDefaultConsent() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userId)
                        .getDefaultConsent());
    }

    /** Get the currently running privacy sandbox feature on device. */
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public String getCurrentPrivacySandboxFeature() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("getCurrentPrivacySandboxFeature() for User Identifier %d", userIdentifier);
        try {
            for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
                if (mUserInstanceManager
                        .getOrCreateUserConsentManagerInstance(userIdentifier)
                        .isPrivacySandboxFeatureEnabled(featureType)) {
                    return featureType.name();
                }
            }
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get the privacy sandbox feature state: " + e.getMessage());
        }
        return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED.name();
    }

    /** Set the currently running privacy sandbox feature on device. */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setCurrentPrivacySandboxFeature(String featureType) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setCurrentPrivacySandboxFeature() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setCurrentPrivacySandboxFeature(featureType);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to set current privacy sandbox feature: " + e.getMessage());
        }
    }

    @Override
    @RequiresPermission
    public List<String> getKnownAppsWithConsent(@NonNull List<String> installedPackages) {
        return executeGetter(/* defaultReturn= */ List.of(),
                (userId) -> mUserInstanceManager
                        .getOrCreateUserAppConsentManagerInstance(userId)
                        .getKnownAppsWithConsent(installedPackages));
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public List<String> getAppsWithRevokedConsent(@NonNull List<String> installedPackages) {
        return executeGetter(/* defaultReturn= */ List.of(),
                (userId) -> mUserInstanceManager
                        .getOrCreateUserAppConsentManagerInstance(userId)
                        .getAppsWithRevokedConsent(installedPackages));
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setConsentForApp(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();

        LogUtil.v(
                "setConsentForApp() for User Identifier %d, package name %s, and package uid %d to"
                        + " %s.",
                userIdentifier, packageName, packageUid, isConsentRevoked);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .setConsentForApp(packageName, packageUid, isConsentRevoked);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to setConsentForApp() for User Identifier %d, package name %s, and"
                            + " package uid %d to %s.",
                    userIdentifier,
                    packageName,
                    packageUid,
                    isConsentRevoked);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearKnownAppsWithConsent() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("clearKnownAppsWithConsent() for user identifier %d.", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearKnownAppsWithConsent();
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to clearKnownAppsWithConsent() for user identifier %d",
                    userIdentifier);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearAllAppConsentData() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("clearAllAppConsentData() for user identifier %d.", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearAllAppConsentData();
        } catch (IOException e) {
            LogUtil.e(
                    e, "Failed to clearAllAppConsentData() for user identifier %d", userIdentifier);
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isConsentRevokedForApp(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "isConsentRevokedForApp() for user identifier %d, package name %s, and package uid"
                        + " %d.",
                userIdentifier, packageName, packageUid);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .isConsentRevokedForApp(packageName, packageUid);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to call isConsentRevokedForApp() for user identifier %d, package name"
                            + " %s, and package uid %d.",
                    userIdentifier,
                    packageName,
                    packageUid);
            return true;
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean setConsentForAppIfNew(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked)
            throws IllegalArgumentException {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "setConsentForAppIfNew() for user identifier %d, package name"
                        + " %s, and package uid %d to %s.",
                userIdentifier, packageName, packageUid, isConsentRevoked);
        try {
            return mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .setConsentForAppIfNew(packageName, packageUid, isConsentRevoked);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to setConsentForAppIfNew() for user identifier %d, package name"
                            + " %s, and package uid %d to %s.",
                    userIdentifier,
                    packageName,
                    packageUid,
                    isConsentRevoked);
            return true;
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void clearConsentForUninstalledApp(@NonNull String packageName, int packageUid) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v(
                "clearConsentForUninstalledApp() for user identifier %d, package name"
                        + " %s, and package uid %d.",
                userIdentifier, packageName, packageUid);
        try {
            mUserInstanceManager
                    .getOrCreateUserAppConsentManagerInstance(userIdentifier)
                    .clearConsentForUninstalledApp(packageName, packageUid);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Failed to clearConsentForUninstalledApp() for user identifier %d, package name"
                            + " %s, and package uid %d.",
                    userIdentifier,
                    packageName,
                    packageUid);
        }
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter pw, String[] args) {
        mContext.enforceCallingPermission(android.Manifest.permission.DUMP, /* message= */ null);
        synchronized (mSetPackageVersionLock) {
            pw.printf("mAdServicesModuleName: %s\n", mAdServicesModuleName);
            pw.printf("mAdServicesModuleVersion: %d\n", mAdServicesModuleVersion);
        }
        synchronized (mRegisterReceiverLock) {
            pw.printf("mHandlerThread: %s\n", mHandlerThread);
        }
        synchronized (mRollbackCheckLock) {
            pw.printf("mAdServicesPackagesRolledBackFrom: %s\n", mAdServicesPackagesRolledBackFrom);
            pw.printf("mAdServicesPackagesRolledBackTo: %s\n", mAdServicesPackagesRolledBackTo);
        }
        pw.printf("ShellCmd enabled: %b\n", isShellCmdEnabled());
        mUserInstanceManager.dump(pw, args);
    }

    private static boolean isShellCmdEnabled() {
        return FlagsFactory.getFlags().getAdServicesShellCommandEnabled();
    }

    @Override
    public int handleShellCommand(
            ParcelFileDescriptor in,
            ParcelFileDescriptor out,
            ParcelFileDescriptor err,
            String[] args) {

        if (!isShellCmdEnabled()) {
            LogUtil.d(
                    "handleShellCommand(%s): disabled by flag %s",
                    Arrays.toString(args),
                    CommonFlagsConstants.KEY_ADSERVICES_SHELL_COMMAND_ENABLED);
            return super.handleShellCommand(in, out, err, args);
        }

        LogUtil.v("Executing shell cmd: %s", Arrays.toString(args));
        return new AdServicesShellCommand(mContext)
                .exec(
                        this,
                        in.getFileDescriptor(),
                        out.getFileDescriptor(),
                        err.getFileDescriptor(),
                        args);
    }

    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void recordAdServicesDeletionOccurred(
            @AdServicesManager.DeletionApiType int deletionType) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        try {
            LogUtil.v(
                    "recordAdServicesDeletionOccurred() for user identifier %d, api type %d",
                    userIdentifier, deletionType);
            mUserInstanceManager
                    .getOrCreateUserRollbackHandlingManagerInstance(
                            userIdentifier, getAdServicesApexVersion())
                    .recordAdServicesDataDeletion(deletionType);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to persist the deletion status.");
        }
    }

    public boolean needsToHandleRollbackReconciliation(
            @AdServicesManager.DeletionApiType int deletionType) {
        // Check if there was at least one rollback of the AdServices module.
        if (getAdServicesPackagesRolledBackFrom().isEmpty()) {
            return false;
        }

        // Check if the deletion bit is set.
        if (!hasAdServicesDeletionOccurred(deletionType)) {
            return false;
        }

        // For each rollback, check if the rolled back from version matches the previously stored
        // version and the rolled back to version matches the current version.
        int previousStoredVersion = getPreviousStoredVersion(deletionType);
        for (Integer rollbackId : getAdServicesPackagesRolledBackFrom().keySet()) {
            if (getAdServicesPackagesRolledBackFrom().get(rollbackId).getLongVersionCode()
                            == previousStoredVersion
                    && getAdServicesPackagesRolledBackTo().get(rollbackId).getLongVersionCode()
                            == getAdServicesApexVersion()) {
                resetAdServicesDeletionOccurred(deletionType);
                return true;
            }
        }

        // None of the stored rollbacks match the versions.
        return false;
    }

    @VisibleForTesting
    boolean hasAdServicesDeletionOccurred(@AdServicesManager.DeletionApiType int deletionType) {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                                userId, getAdServicesApexVersion())
                        .wasAdServicesDataDeleted(deletionType));
    }

    @VisibleForTesting
    void resetAdServicesDeletionOccurred(@AdServicesManager.DeletionApiType int deletionType) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        try {
            LogUtil.v("resetMeasurementDeletionOccurred() for user identifier %d", userIdentifier);
            mUserInstanceManager
                    .getOrCreateUserRollbackHandlingManagerInstance(
                            userIdentifier, getAdServicesApexVersion())
                    .resetAdServicesDataDeletion(deletionType);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to remove the fmeasurement deletion status.");
        }
    }

    @VisibleForTesting
    int getPreviousStoredVersion(@AdServicesManager.DeletionApiType int deletionType) {
        return executeGetter(/* defaultReturn= */ 0,
                (userId) -> mUserInstanceManager.getOrCreateUserRollbackHandlingManagerInstance(
                                userId, getAdServicesApexVersion())
                        .getPreviousStoredVersion(deletionType));
    }

    @VisibleForTesting
    void registerReceivers() {
        // There could be race condition between registerReceivers call
        // in the AdServicesManagerService constructor and the mOnFlagsChangedListener.
        synchronized (mRegisterReceiverLock) {
            if (!FlagsFactory.getFlags().getAdServicesSystemServiceEnabled()) {
                LogUtil.d("AdServicesSystemServiceEnabled is FALSE.");
                // If there is a SystemServicePackageChangeReceiver, unregister it.
                if (mSystemServicePackageChangedReceiver != null) {
                    LogUtil.d("Unregistering the existing SystemServicePackageChangeReceiver");
                    mContext.unregisterReceiver(mSystemServicePackageChangedReceiver);
                    mSystemServicePackageChangedReceiver = null;
                }

                // If there is a SystemServiceUserActionReceiver, unregister it.
                if (mSystemServiceUserActionReceiver != null) {
                    LogUtil.d("Unregistering the existing SystemServiceUserActionReceiver");
                    mContext.unregisterReceiver(mSystemServiceUserActionReceiver);
                    mSystemServiceUserActionReceiver = null;
                }

                if (mHandler != null) {
                    mHandlerThread.quitSafely();
                    mHandler = null;
                }
                return;
            }

            // Start the handler thread.
            if (mHandler == null) {
                mHandlerThread = new HandlerThread("AdServicesManagerServiceHandler");
                mHandlerThread.start();
                mHandler = new Handler(mHandlerThread.getLooper());
            }
            registerPackagedChangedBroadcastReceiversLocked();
            registerUserActionBroadcastReceiverLocked();
        }
    }

    @VisibleForTesting
    /**
     * Stores the AdServices module version locally. Users other than the main user do not have the
     * permission to get the version through the PackageManager, so we have to get the version when
     * the AdServices system service starts.
     */
    void setAdServicesApexVersion() {
        synchronized (mSetPackageVersionLock) {
            if (!FlagsFactory.getFlags().getAdServicesSystemServiceEnabled()) {
                LogUtil.d("AdServicesSystemServiceEnabled is FALSE.");
                return;
            }

            PackageManager packageManager = mContext.getPackageManager();

            List<PackageInfo> installedPackages =
                    packageManager.getInstalledPackages(
                            PackageManager.PackageInfoFlags.of(PackageManager.MATCH_APEX));

            installedPackages.forEach(
                    packageInfo -> {
                        if (packageInfo.packageName.contains("adservices") && packageInfo.isApex) {
                            mAdServicesModuleName = packageInfo.packageName;
                            mAdServicesModuleVersion = (int) packageInfo.getLongVersionCode();
                        }
                    });
        }
    }

    @VisibleForTesting
    int getAdServicesApexVersion() {
        return mAdServicesModuleVersion;
    }

    @VisibleForTesting
    /** Checks the RollbackManager to see the rollback status of the AdServices module. */
    void setRollbackStatus() {
        synchronized (mRollbackCheckLock) {
            if (!FlagsFactory.getFlags().getAdServicesSystemServiceEnabled()) {
                LogUtil.d("AdServicesSystemServiceEnabled is FALSE.");
                resetRollbackArraysRCLocked();
                return;
            }

            RollbackManager rollbackManager = mContext.getSystemService(RollbackManager.class);
            if (rollbackManager == null) {
                LogUtil.d("Failed to get the RollbackManager service.");
                resetRollbackArraysRCLocked();
                return;
            }
            List<RollbackInfo> recentlyCommittedRollbacks =
                    rollbackManager.getRecentlyCommittedRollbacks();

            for (RollbackInfo rollbackInfo : recentlyCommittedRollbacks) {
                for (PackageRollbackInfo packageRollbackInfo : rollbackInfo.getPackages()) {
                    if (packageRollbackInfo.getPackageName().equals(mAdServicesModuleName)) {
                        mAdServicesPackagesRolledBackFrom.put(
                                rollbackInfo.getRollbackId(),
                                packageRollbackInfo.getVersionRolledBackFrom());
                        mAdServicesPackagesRolledBackTo.put(
                                rollbackInfo.getRollbackId(),
                                packageRollbackInfo.getVersionRolledBackTo());
                        LogUtil.d(
                                "Rollback of AdServices module occurred, "
                                        + "from version %d to version %d",
                                packageRollbackInfo.getVersionRolledBackFrom().getLongVersionCode(),
                                packageRollbackInfo.getVersionRolledBackTo().getLongVersionCode());
                    }
                }
            }
        }
    }

    @GuardedBy("mRollbackCheckLock")
    private void resetRollbackArraysRCLocked() {
        mAdServicesPackagesRolledBackFrom.clear();
        mAdServicesPackagesRolledBackTo.clear();
    }

    @VisibleForTesting
    Map<Integer, VersionedPackage> getAdServicesPackagesRolledBackFrom() {
        return mAdServicesPackagesRolledBackFrom;
    }

    @VisibleForTesting
    Map<Integer, VersionedPackage> getAdServicesPackagesRolledBackTo() {
        return mAdServicesPackagesRolledBackTo;
    }

    /**
     * Registers a receiver for any broadcasts related to user profile removal for all users on the
     * device at boot up. After receiving the broadcast, we delete consent manager instance and
     * remove the user related data.
     */
    private void registerUserActionBroadcastReceiverLocked() {
        if (mSystemServiceUserActionReceiver != null) {
            // We already register the receiver.
            LogUtil.d("SystemServiceUserActionReceiver is already registered.");
            return;
        }
        mSystemServiceUserActionReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        mHandler.post(() -> onUserRemoved(intent));
                    }
                };
        mContext.registerReceiverForAllUsers(
                mSystemServiceUserActionReceiver,
                new IntentFilter(Intent.ACTION_USER_REMOVED),
                /* broadcastPermission= */ null,
                mHandler);
        LogUtil.d("SystemServiceUserActionReceiver registered.");
    }

    /** Deletes the user instance and remove the user consent related data. */
    @VisibleForTesting
    void onUserRemoved(@NonNull Intent intent) {
        Objects.requireNonNull(intent);
        if (Intent.ACTION_USER_REMOVED.equals(intent.getAction())) {
            UserHandle userHandle = intent.getParcelableExtra(Intent.EXTRA_USER, UserHandle.class);
            if (userHandle == null) {
                LogUtil.e("Extra " + Intent.EXTRA_USER + " is missing in the intent: " + intent);
                return;
            }
            LogUtil.d("Deleting user instance with user id: " + userHandle.getIdentifier());
            try {
                mUserInstanceManager.deleteUserInstance(userHandle.getIdentifier());
            } catch (Exception e) {
                LogUtil.e(e, "Failed to delete the consent manager directory");
            }
        }
    }

    /**
     * Registers a receiver for any broadcasts regarding changes to any packages for all users on
     * the device at boot up. After receiving the broadcast, send an explicit broadcast to the
     * AdServices module as that user.
     */
    private void registerPackagedChangedBroadcastReceiversLocked() {
        if (mSystemServicePackageChangedReceiver != null) {
            // We already register the receiver.
            LogUtil.d("SystemServicePackageChangedReceiver is already registered.");
            return;
        }

        final IntentFilter packageChangedIntentFilter = new IntentFilter();
        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_FULLY_REMOVED);
        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        packageChangedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageChangedIntentFilter.addDataScheme("package");

        mSystemServicePackageChangedReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        UserHandle user = getSendingUser();
                        mHandler.post(() -> onPackageChange(intent, user));
                    }
                };
        mContext.registerReceiverForAllUsers(
                mSystemServicePackageChangedReceiver,
                packageChangedIntentFilter,
                /* broadcastPermission */ null,
                mHandler);
        LogUtil.d("Package changed broadcast receivers registered.");
    }

    /** Sends an explicit broadcast to the AdServices module when a package change occurs. */
    @VisibleForTesting
    public void onPackageChange(Intent intent, UserHandle user) {
        Intent explicitBroadcast = new Intent();
        explicitBroadcast.setAction(PACKAGE_CHANGED_BROADCAST);
        explicitBroadcast.setData(intent.getData());

        final Intent i = new Intent(PACKAGE_CHANGED_BROADCAST);
        final List<ResolveInfo> resolveInfo =
                mContext.getPackageManager()
                        .queryBroadcastReceiversAsUser(
                                i,
                                PackageManager.ResolveInfoFlags.of(PackageManager.GET_RECEIVERS),
                                user);
        if (resolveInfo != null && !resolveInfo.isEmpty()) {
            for (ResolveInfo info : resolveInfo) {
                explicitBroadcast.setClassName(
                        info.activityInfo.packageName, info.activityInfo.name);
                int uidChanged = intent.getIntExtra(Intent.EXTRA_UID, -1);
                LogUtil.v("Package changed with UID " + uidChanged);
                explicitBroadcast.putExtra(Intent.EXTRA_UID, uidChanged);
                switch (intent.getAction()) {
                    case Intent.ACTION_PACKAGE_DATA_CLEARED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_DATA_CLEARED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_FULLY_REMOVED:
                        // TODO (b/233373604): Propagate broadcast to users not currently running
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_FULLY_REMOVED);
                        mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        break;
                    case Intent.ACTION_PACKAGE_ADDED:
                        explicitBroadcast.putExtra(ACTION_KEY, PACKAGE_ADDED);
                        // For users where the app is merely being updated rather than added, we
                        // don't want to send the broadcast.
                        if (!intent.getExtras().getBoolean(Intent.EXTRA_REPLACING, false)) {
                            mContext.sendBroadcastAsUser(explicitBroadcast, user);
                        }
                        break;
                }
            }
        }
    }

    // Check if caller has permission to invoke AdServicesManager APIs.
    @VisibleForTesting
    void enforceAdServicesManagerPermission() {
        mContext.enforceCallingPermission(
                AdServicesPermissions.ACCESS_ADSERVICES_MANAGER,
                ERROR_MESSAGE_NOT_PERMITTED_TO_CALL_ADSERVICESMANAGER_API);
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isAdIdEnabled() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                        userId).isAdIdEnabled());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setAdIdEnabled() for User Identifier %d", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setAdIdEnabled(isAdIdEnabled);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to call setAdIdEnabled().");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isU18Account() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                        userId).isU18Account());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setU18Account(boolean isU18Account) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setU18Account() for User Identifier %d", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setU18Account(isU18Account);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to call setU18Account().");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isEntryPointEnabled() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                        userId).isEntryPointEnabled());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setEntryPointEnabled() for User Identifier %d", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setEntryPointEnabled(isEntryPointEnabled);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to call setEntryPointEnabled().");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean isAdultAccount() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                        userId).isAdultAccount());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setAdultAccount(boolean isAdultAccount) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setAdultAccount() for User Identifier %d", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setAdultAccount(isAdultAccount);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to call setAdultAccount().");
        }
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public boolean wasU18NotificationDisplayed() {
        return executeGetter(/* defaultReturn= */ false,
                (userId) -> mUserInstanceManager.getOrCreateUserConsentManagerInstance(
                        userId).wasU18NotificationDisplayed());
    }

    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setU18NotificationDisplayed() for User Identifier %d", userIdentifier);

        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setU18NotificationDisplayed(wasU18NotificationDisplayed);
        } catch (IOException e) {
            LogUtil.e(e, "Failed to call setU18NotificationDisplayed().");
        }
    }

    /** Get the current UX. */
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public String getUx() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("getUx() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .getUx();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get current UX: " + e.getMessage());
        }
        return PrivacySandboxUxCollection.UNSUPPORTED_UX.toString();
    }

    /** Set the currently running privacy sandbox feature on device. */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setUx(String ux) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setUx() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager.getOrCreateUserConsentManagerInstance(userIdentifier).setUx(ux);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to set current UX: " + e.getMessage());
        }
    }

    /** Get the current enrollment channel. */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public String getEnrollmentChannel() {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("getUx() for User Identifier %d", userIdentifier);
        try {
            return mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .getEnrollmentChannel();
        } catch (IOException e) {
            LogUtil.e(e, "Fail to get current enrollment channel: " + e.getMessage());
        }
        return PrivacySandboxUxCollection.UNSUPPORTED_UX.toString();
    }

    /** Set the current enrollment channel. */
    @Override
    @RequiresPermission(AdServicesPermissions.ACCESS_ADSERVICES_MANAGER)
    public void setEnrollmentChannel(String enrollmentChannel) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();
        LogUtil.v("setUx() for User Identifier %d", userIdentifier);
        try {
            mUserInstanceManager
                    .getOrCreateUserConsentManagerInstance(userIdentifier)
                    .setEnrollmentChannel(enrollmentChannel);
        } catch (IOException e) {
            LogUtil.e(e, "Fail to set current enrollment channel: " + e.getMessage());
        }
    }

    @FunctionalInterface
    interface ThrowableGetter<R> {
        R apply(int userId) throws IOException;
    }

    private <R> R executeGetter(R r, ThrowableGetter<R> function) {
        enforceAdServicesManagerPermission();

        final int userIdentifier = getUserIdentifierFromBinderCallingUid();

        String logPrefix = getClass().getSimpleName() + function.toString();
        LogUtil.v("%s called. User identifier: %s", logPrefix, userIdentifier);

        try {
            return function.apply(userIdentifier);
        } catch (IOException e) {
            LogUtil.e(e, logPrefix + " failed.");
            return r;
        }
    }
}
