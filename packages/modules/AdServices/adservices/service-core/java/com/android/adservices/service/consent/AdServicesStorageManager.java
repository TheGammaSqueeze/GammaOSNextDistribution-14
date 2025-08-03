/*
 * Copyright (C) 2023 The Android Open Source Project
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

package com.android.adservices.service.consent;

import static android.adservices.common.AdServicesPermissions.ACCESS_ADSERVICES_MANAGER;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.adservices.AdServicesManager;
import android.app.adservices.consent.ConsentParcel;
import android.app.adservices.topics.TopicParcel;
import android.content.pm.PackageManager;
import android.os.Build;

import androidx.annotation.RequiresApi;

import com.android.adservices.LogUtil;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.adservices.service.common.feature.PrivacySandboxFeatureType;
import com.android.adservices.service.ui.enrollment.collection.PrivacySandboxEnrollmentChannelCollection;
import com.android.adservices.service.ui.ux.collection.PrivacySandboxUxCollection;
import com.android.adservices.shared.common.ApplicationContextSingleton;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.collect.ImmutableList;

import java.io.IOException;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * AdServicesStoreManager to handle the internal communication between PPAPI process and AdServices
 * System Service. It is a wrapper of AdServicesManager.
 */
@RequiresApi(Build.VERSION_CODES.S)
public final class AdServicesStorageManager implements IConsentStorage {
    private static final Object SINGLETON_LOCK = new Object();

    @GuardedBy("SINGLETON_LOCK")
    private static volatile AdServicesStorageManager sSingleton;

    private final AdServicesManager mAdServicesManager;
    private final PackageManager mPackageManager;

    /** Constructor of {@link AdServicesStorageManager} */
    @VisibleForTesting
    public AdServicesStorageManager(
            AdServicesManager adServicesManager, PackageManager packageManager) {
        Objects.requireNonNull(adServicesManager);
        Objects.requireNonNull(packageManager);
        mAdServicesManager = adServicesManager;
        mPackageManager = packageManager;
    }

    /**
     * Gets an instance of {@link AdServicesStorageManager} to be used.
     *
     * <p>If no instance has been initialized yet, a new one will be created. Otherwise, the
     * existing instance will be returned.
     */
    @Nullable
    public static AdServicesStorageManager getInstance(
            @NonNull AdServicesManager adServicesManager) {
        synchronized (SINGLETON_LOCK) {
            if (sSingleton == null) {
                sSingleton =
                        new AdServicesStorageManager(
                                adServicesManager,
                                ApplicationContextSingleton.get().getPackageManager());
            }
            return sSingleton;
        }
    }

    /** Reset all apps consent. */
    @Override
    public void clearAllAppConsentData() {
        mAdServicesManager.clearAllAppConsentData();
    }

    @Override
    public void clearConsentForUninstalledApp(String packageName) throws IOException {
        int packageUid = getUidForInstalledPackageName(packageName);
        mAdServicesManager.clearConsentForUninstalledApp(packageName, packageUid);
    }

    /** Clear the app consent entry for uninstalled app. */
    @Override
    public void clearConsentForUninstalledApp(String packageName, int packageUid) {
        mAdServicesManager.clearConsentForUninstalledApp(packageName, packageUid);
    }

    /** Reset all apps and blocked apps. */
    @Override
    public void clearKnownAppsWithConsent() {
        mAdServicesManager.clearKnownAppsWithConsent();
    }

    /** Returns the list of apps with revoked consent. */
    @Override
    public ImmutableList<String> getAppsWithRevokedConsent() {
        return ImmutableList.copyOf(
                mAdServicesManager.getAppsWithRevokedConsent(getInstalledPackages()));
    }

    /** Return the User Consent */
    @Override
    public AdServicesApiConsent getConsent(AdServicesApiType apiType) {
        int consentApiType = apiType.toConsentApiType();
        ConsentParcel consentParcel = mAdServicesManager.getConsent(consentApiType);
        if (consentParcel == null) {
            return AdServicesApiConsent.REVOKED;
        }
        return AdServicesApiConsent.getConsent(consentParcel.isIsGiven());
    }

    /** Returns the current privacy sandbox feature. */
    @Override
    public PrivacySandboxFeatureType getCurrentPrivacySandboxFeature() throws IOException {
        if (mAdServicesManager == null
                || mAdServicesManager.getCurrentPrivacySandboxFeature() == null) {
            return PrivacySandboxFeatureType.PRIVACY_SANDBOX_UNSUPPORTED;
        }
        return PrivacySandboxFeatureType.valueOf(
                mAdServicesManager.getCurrentPrivacySandboxFeature());
    }

    /** Returns current enrollment channel. */
    @Override
    public PrivacySandboxEnrollmentChannelCollection getEnrollmentChannel(
            PrivacySandboxUxCollection ux) {
        String enrollmentChannel = mAdServicesManager.getEnrollmentChannel();
        return Stream.of(ux.getEnrollmentChannelCollection())
                .filter(channel -> enrollmentChannel.equals(channel.toString()))
                .findFirst()
                .orElse(null);
    }

    /** Returns the list of packages installed on the device of the user. */
    public List<String> getInstalledPackages() {
        return PackageManagerCompatUtils.getInstalledApplications(mPackageManager, 0).stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toList());
    }

    /** Returns the list of known apps with consent. */
    @Override
    public ImmutableList<String> getKnownAppsWithConsent() {
        return ImmutableList.copyOf(
                mAdServicesManager.getKnownAppsWithConsent(getInstalledPackages()));
    }

    /** Returns uid of installed package. */
    @VisibleForTesting
    public int getUidForInstalledPackageName(@androidx.annotation.NonNull String packageName) {
        Objects.requireNonNull(packageName);
        try {
            return PackageManagerCompatUtils.getPackageUid(mPackageManager, packageName, 0);
        } catch (PackageManager.NameNotFoundException exception) {
            LogUtil.e(exception, "Package name not found");
            throw new IllegalArgumentException(exception);
        }
    }

    /**
     * Returns information whether user interacted with consent manually.
     *
     * @return
     *     <ul>
     *       <li>-1 when no manual interaction was recorded
     *       <li>0 when no data about interaction (similar to null)
     *       <li>1 when manual interaction was recorded
     *     </ul>
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    @Override
    public int getUserManualInteractionWithConsent() {
        return mAdServicesManager.getUserManualInteractionWithConsent();
    }

    /** Returns the current UX. */
    @Override
    public PrivacySandboxUxCollection getUx() {
        return convertUxString(mAdServicesManager.getUx());
    }

    /** Returns whether the isAdIdEnabled bit is true. */
    @Override
    public boolean isAdIdEnabled() {
        return mAdServicesManager.isAdIdEnabled();
    }

    /** Returns whether the isAdultAccount bit is true. */
    @Override
    public boolean isAdultAccount() {
        return mAdServicesManager.isAdultAccount();
    }

    /**
     * Get if user consent is revoked for a given app.
     *
     * @return {@code true} if the user consent was revoked.
     */
    @RequiresPermission(ACCESS_ADSERVICES_MANAGER)
    @Override
    public boolean isConsentRevokedForApp(String packageName) {
        int packageUid = getUidForInstalledPackageName(packageName);
        return mAdServicesManager.isConsentRevokedForApp(packageName, packageUid);
    }

    /** Returns whether the isEntryPointEnabled bit is true. */
    @Override
    public boolean isEntryPointEnabled() {
        return mAdServicesManager.isEntryPointEnabled();
    }

    /** Returns whether the isU18Account bit is true. */
    @Override
    public boolean isU18Account() {
        return mAdServicesManager.isU18Account();
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @Override
    public void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        mAdServicesManager.recordGaUxNotificationDisplayed(wasNotificationDisplayed);
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    @Override
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        mAdServicesManager.recordNotificationDisplayed(wasNotificationDisplayed);
    }

    /** Saves information to the storage that user interacted with consent manually. */
    @Override
    public void recordUserManualInteractionWithConsent(int interaction) {
        mAdServicesManager.recordUserManualInteractionWithConsent(interaction);
    }

    /**
     * Record a blocked topic.
     *
     * @param blockedTopicParcels the blocked topic to record
     */
    public void recordBlockedTopic(@NonNull List<TopicParcel> blockedTopicParcels) {
        mAdServicesManager.recordBlockedTopic(blockedTopicParcels);
    }

    /** Saves the isAdIdEnabled bit. */
    @Override
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        mAdServicesManager.setAdIdEnabled(isAdIdEnabled);
    }

    /** Saves the isAdultAccount bit. */
    @Override
    public void setAdultAccount(boolean isAdultAccount) {
        mAdServicesManager.setAdultAccount(isAdultAccount);
    }

    /** Sets the consent to system server. */
    @Override
    public void setConsent(AdServicesApiType apiType, boolean isGiven) {
        ConsentParcel consentParcel =
                new ConsentParcel.Builder()
                        .setConsentApiType(apiType.toConsentApiType())
                        .setIsGiven(isGiven)
                        .build();
        mAdServicesManager.setConsent(consentParcel);
    }

    /** Set user consent for an app */
    @Override
    public void setConsentForApp(String packageName, boolean isConsentRevoked) {
        int packageUid = getUidForInstalledPackageName(packageName);
        mAdServicesManager.setConsentForApp(packageName, packageUid, isConsentRevoked);
    }

    /**
     * Set user consent if the app first time request access and/or return consent value for the
     * app.
     *
     * @return {@code true} if user consent was given.
     */
    @Override
    public boolean setConsentForAppIfNew(String packageName, boolean isConsentRevoked) {
        int packageUid = getUidForInstalledPackageName(packageName);
        return mAdServicesManager.setConsentForAppIfNew(packageName, packageUid, isConsentRevoked);
    }

    /** Set the current privacy sandbox feature. */
    @Override
    public void setCurrentPrivacySandboxFeature(PrivacySandboxFeatureType featureType) {
        mAdServicesManager.setCurrentPrivacySandboxFeature(featureType.name());
    }

    /** Set the current enrollment channel to storage. */
    @Override
    public void setEnrollmentChannel(
            PrivacySandboxUxCollection ux, PrivacySandboxEnrollmentChannelCollection channel) {
        mAdServicesManager.setEnrollmentChannel(channel.toString());
    }

    /** Saves the isEntryPointEnabled bit. */
    @Override
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        mAdServicesManager.setEntryPointEnabled(isEntryPointEnabled);
    }

    /** Saves the isU18Account bit. */
    @Override
    public void setU18Account(boolean isU18Account) {
        mAdServicesManager.setU18Account(isU18Account);
    }

    /** Saves the wasU18NotificationDisplayed bit. */
    @Override
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed) {
        mAdServicesManager.setU18NotificationDisplayed(wasU18NotificationDisplayed);
    }

    /** Set the current UX to storage. */
    @Override
    public void setUx(PrivacySandboxUxCollection ux) {
        mAdServicesManager.setUx(ux.toString());
    }

    /**
     * Returns information whether Consent GA UX Notification was displayed or not.
     *
     * @return true if Consent GA UX Notification was displayed, otherwise false.
     */
    @Override
    public boolean wasGaUxNotificationDisplayed() {
        return mAdServicesManager.wasGaUxNotificationDisplayed();
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    @Override
    public boolean wasNotificationDisplayed() {
        return mAdServicesManager.wasNotificationDisplayed();
    }

    /** Returns whether the wasU18NotificationDisplayed bit is true. */
    @Override
    public boolean wasU18NotificationDisplayed() {
        return mAdServicesManager.wasU18NotificationDisplayed();
    }

    private PrivacySandboxUxCollection convertUxString(@NonNull String uxString) {
        Objects.requireNonNull(uxString);
        return Stream.of(PrivacySandboxUxCollection.values())
                .filter(ux -> uxString.equals(ux.toString()))
                .findFirst()
                .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX);
    }
}
