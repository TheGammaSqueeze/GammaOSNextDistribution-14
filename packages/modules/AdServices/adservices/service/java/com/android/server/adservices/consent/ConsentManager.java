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
package com.android.server.adservices.consent;

import android.annotation.NonNull;
import android.app.adservices.consent.ConsentParcel;

import com.android.adservices.shared.storage.BooleanFileDatastore;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.LogUtil;
import com.android.server.adservices.feature.PrivacySandboxEnrollmentChannelCollection;
import com.android.server.adservices.feature.PrivacySandboxFeatureType;
import com.android.server.adservices.feature.PrivacySandboxUxCollection;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.Objects;
import java.util.concurrent.locks.ReadWriteLock;
import java.util.concurrent.locks.ReentrantReadWriteLock;
import java.util.stream.Stream;

/**
 * Manager to handle user's consent. We will have one ConsentManager instance per user.
 *
 * @hide
 */
public final class ConsentManager {
    public static final String ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT =
            "getConsent method failed. Revoked consent is returned as fallback.";

    public static final String VERSION_KEY = "android.app.adservices.consent.VERSION";

    @VisibleForTesting
    static final String NOTIFICATION_DISPLAYED_ONCE = "NOTIFICATION-DISPLAYED-ONCE";

    static final String GA_UX_NOTIFICATION_DISPLAYED_ONCE = "GA-UX-NOTIFICATION-DISPLAYED-ONCE";

    static final String PAS_NOTIFICATION_DISPLAYED_ONCE = "PAS_NOTIFICATION_DISPLAYED_ONCE";

    static final String TOPICS_CONSENT_PAGE_DISPLAYED = "TOPICS-CONSENT-PAGE-DISPLAYED";

    static final String FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED =
            "FLDEGE-AND-MSMT-CONDENT-PAGE-DISPLAYED";

    private static final String CONSENT_API_TYPE_PREFIX = "CONSENT_API_TYPE_";

    // Deprecate this since we store each version in its own folder.
    static final int STORAGE_VERSION = 1;
    static final String STORAGE_XML_IDENTIFIER = "ConsentManagerStorageIdentifier.xml";

    private final BooleanFileDatastore mDatastore;

    @VisibleForTesting static final String DEFAULT_CONSENT = "DEFAULT_CONSENT";

    @VisibleForTesting static final String TOPICS_DEFAULT_CONSENT = "TOPICS_DEFAULT_CONSENT";

    @VisibleForTesting static final String FLEDGE_DEFAULT_CONSENT = "FLEDGE_DEFAULT_CONSENT";

    @VisibleForTesting
    static final String MEASUREMENT_DEFAULT_CONSENT = "MEASUREMENT_DEFAULT_CONSENT";

    @VisibleForTesting static final String DEFAULT_AD_ID_STATE = "DEFAULT_AD_ID_STATE";

    @VisibleForTesting
    static final String MANUAL_INTERACTION_WITH_CONSENT_RECORDED =
            "MANUAL_INTERACTION_WITH_CONSENT_RECORDED";

    private final ReadWriteLock mReadWriteLock = new ReentrantReadWriteLock();

    private ConsentManager(@NonNull BooleanFileDatastore datastore) {
        Objects.requireNonNull(datastore);

        mDatastore = datastore;
    }

    /** Create a ConsentManager with base directory and for userIdentifier */
    @NonNull
    public static ConsentManager createConsentManager(@NonNull String baseDir, int userIdentifier)
            throws IOException {
        Objects.requireNonNull(baseDir, "Base dir must be provided.");

        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        // Create the consent directory if needed.
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        baseDir, userIdentifier);

        BooleanFileDatastore datastore = createAndInitBooleanFileDatastore(consentDataStoreDir);

        return new ConsentManager(datastore);
    }

    @NonNull
    @VisibleForTesting
    static BooleanFileDatastore createAndInitBooleanFileDatastore(String consentDataStoreDir)
            throws IOException {
        // Create the DataStore and initialize it.
        BooleanFileDatastore datastore =
                new BooleanFileDatastore(
                        consentDataStoreDir, STORAGE_XML_IDENTIFIER, STORAGE_VERSION, VERSION_KEY);
        datastore.initialize();
        // TODO(b/259607624): implement a method in the datastore which would support
        // this exact scenario - if the value is null, return default value provided
        // in the parameter (similar to SP apply etc.)
        if (datastore.get(NOTIFICATION_DISPLAYED_ONCE) == null) {
            datastore.put(NOTIFICATION_DISPLAYED_ONCE, false);
        }
        if (datastore.get(GA_UX_NOTIFICATION_DISPLAYED_ONCE) == null) {
            datastore.put(GA_UX_NOTIFICATION_DISPLAYED_ONCE, false);
        }
        if (datastore.get(TOPICS_CONSENT_PAGE_DISPLAYED) == null) {
            datastore.put(TOPICS_CONSENT_PAGE_DISPLAYED, false);
        }
        if (datastore.get(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED) == null) {
            datastore.put(FLEDGE_AND_MSMT_CONSENT_PAGE_DISPLAYED, false);
        }
        return datastore;
    }

    /** Retrieves the consent for all PP API services. */
    public ConsentParcel getConsent(@ConsentParcel.ConsentApiType int consentApiType) {
        LogUtil.d("ConsentManager.getConsent() is invoked for consentApiType = " + consentApiType);

        mReadWriteLock.readLock().lock();
        try {
            return new ConsentParcel.Builder()
                    .setConsentApiType(consentApiType)
                    .setIsGiven(mDatastore.get(getConsentApiTypeKey(consentApiType)))
                    .build();
        } catch (NullPointerException | IllegalArgumentException e) {
            LogUtil.e(e, ERROR_MESSAGE_DATASTORE_EXCEPTION_WHILE_GET_CONTENT);
            return ConsentParcel.createRevokedConsent(consentApiType);
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Set Consent */
    public void setConsent(ConsentParcel consentParcel) throws IOException {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastore.put(
                    getConsentApiTypeKey(consentParcel.getConsentApiType()),
                    consentParcel.isIsGiven());
            if (consentParcel.getConsentApiType() == ConsentParcel.ALL_API) {
                // Convert from 1 to 3 consents.
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.TOPICS), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.FLEDGE), consentParcel.isIsGiven());
                mDatastore.put(
                        getConsentApiTypeKey(ConsentParcel.MEASUREMENT), consentParcel.isIsGiven());
            } else {
                // Convert from 3 consents to 1 consent.
                if (mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.TOPICS), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.FLEDGE), /* defaultValue */
                                false)
                        && mDatastore.get(
                                getConsentApiTypeKey(ConsentParcel.MEASUREMENT), /* defaultValue */
                                false)) {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), true);
                } else {
                    mDatastore.put(getConsentApiTypeKey(ConsentParcel.ALL_API), false);
                }
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /**
     * Saves information to the storage that notification was displayed for the first time to the
     * user.
     */
    public void recordNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordNotificationDisplayed");
    }

    /**
     * Returns information whether Consent Notification was displayed or not.
     *
     * @return true if Consent Notification was displayed, otherwise false.
     */
    public boolean wasNotificationDisplayed() {
        return getValueWithLock(NOTIFICATION_DISPLAYED_ONCE);
    }

    /**
     * Saves information to the storage that GA UX notification was displayed for the first time to
     * the user.
     */
    public void recordGaUxNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                GA_UX_NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordGaUxNotificationDisplayed");
    }

    /**
     * Returns information whether GA Ux Consent Notification was displayed or not.
     *
     * @return true if GA UX Consent Notification was displayed, otherwise false.
     */
    public boolean wasGaUxNotificationDisplayed() {
        return getValueWithLock(GA_UX_NOTIFICATION_DISPLAYED_ONCE);
    }

    /**
     * Saves information to the storage that PAS notification was displayed for the first time to
     * the user.
     */
    public void recordPasNotificationDisplayed(boolean wasNotificationDisplayed) {
        setValueWithLock(
                PAS_NOTIFICATION_DISPLAYED_ONCE,
                wasNotificationDisplayed,
                "recordPasNotificationDisplayed");
    }

    /**
     * Returns information whether PAS Consent Notification was displayed or not.
     *
     * @return true if PAS Consent Notification was displayed, otherwise false.
     */
    public boolean wasPasNotificationDisplayed() {
        return getValueWithLock(PAS_NOTIFICATION_DISPLAYED_ONCE);
    }

    /** Saves the default consent of a user. */
    public void recordDefaultConsent(boolean defaultConsent) {
        setValueWithLock(DEFAULT_CONSENT, defaultConsent, /* callerName */ "recordDefaultConsent");
    }

    /** Saves the default topics consent of a user. */
    public void recordTopicsDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                TOPICS_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordTopicsDefaultConsent");
    }

    /** Saves the default FLEDGE consent of a user. */
    public void recordFledgeDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                FLEDGE_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordFledgeDefaultConsent");
    }

    /** Saves the default measurement consent of a user. */
    public void recordMeasurementDefaultConsent(boolean defaultConsent) {
        setValueWithLock(
                MEASUREMENT_DEFAULT_CONSENT,
                defaultConsent, /* callerName */
                "recordMeasurementDefaultConsent");
    }

    /** Saves the default AdId state of a user. */
    public void recordDefaultAdIdState(boolean defaultAdIdState) {
        setValueWithLock(
                DEFAULT_AD_ID_STATE, defaultAdIdState, /* callerName */ "recordDefaultAdIdState");
    }

    /** Saves the information whether the user interated manually with the consent. */
    public void recordUserManualInteractionWithConsent(int interaction) {
        mReadWriteLock.writeLock().lock();
        try {
            switch (interaction) {
                case -1:
                    mDatastore.put(MANUAL_INTERACTION_WITH_CONSENT_RECORDED, false);
                    break;
                case 0:
                    mDatastore.remove(MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
                    break;
                case 1:
                    mDatastore.put(MANUAL_INTERACTION_WITH_CONSENT_RECORDED, true);
                    break;
                default:
                    throw new IllegalArgumentException(
                            String.format("InteractionId < %d > can not be handled.", interaction));
            }
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    "Record manual interaction with consent failed due to IOException thrown"
                            + " by Datastore: "
                            + e.getMessage());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns information whether user interacted with consent manually. */
    public int getUserManualInteractionWithConsent() {
        mReadWriteLock.readLock().lock();
        try {
            Boolean userManualInteractionWithConsent =
                    mDatastore.get(MANUAL_INTERACTION_WITH_CONSENT_RECORDED);
            if (userManualInteractionWithConsent == null) {
                return 0;
            } else if (Boolean.TRUE.equals(userManualInteractionWithConsent)) {
                return 1;
            } else {
                return -1;
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Returns the default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getDefaultConsent() {
        return getValueWithLock(DEFAULT_CONSENT);
    }

    /**
     * Returns the topics default consent state.
     *
     * @return true if topics default consent is given, otherwise false.
     */
    public boolean getTopicsDefaultConsent() {
        return getValueWithLock(TOPICS_DEFAULT_CONSENT);
    }


    /**
     * Returns the FLEDGE default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getFledgeDefaultConsent() {
        return getValueWithLock(FLEDGE_DEFAULT_CONSENT);
    }

    /**
     * Returns the measurement default consent state.
     *
     * @return true if default consent is given, otherwise false.
     */
    public boolean getMeasurementDefaultConsent() {
        return getValueWithLock(MEASUREMENT_DEFAULT_CONSENT);
    }

    /**
     * Returns the default AdId state when consent notification was sent.
     *
     * @return true if AdId is enabled by default, otherwise false.
     */
    public boolean getDefaultAdIdState() {
        return getValueWithLock(DEFAULT_AD_ID_STATE);
    }

    /** Set the current enabled privacy sandbox feature. */
    public void setCurrentPrivacySandboxFeature(String currentFeatureType) {
        mReadWriteLock.writeLock().lock();
        try {
            for (PrivacySandboxFeatureType featureType : PrivacySandboxFeatureType.values()) {
                try {
                    mDatastore.put(
                            featureType.name(), currentFeatureType.equals(featureType.name()));
                } catch (IOException e) {
                    LogUtil.e(
                            "IOException caught while saving privacy sandbox feature."
                                    + e.getMessage());
                }
            }
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns whether a privacy sandbox feature is enabled. */
    public boolean isPrivacySandboxFeatureEnabled(PrivacySandboxFeatureType featureType) {
        return getValueWithLock(featureType.name());
    }

    /**
     * Deletes the user directory which contains consent information present at
     * /data/system/adservices/user_id
     */
    public boolean deleteUserDirectory(File dir) {
        mReadWriteLock.writeLock().lock();
        try {
            boolean success = true;
            File[] files = dir.listFiles();
            // files will be null if dir is not a directory
            if (files != null) {
                for (File file : files) {
                    if (!deleteUserDirectory(file)) {
                        LogUtil.d("Failed to delete " + file);
                        success = false;
                    }
                }
            }
            return success && dir.delete();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    @VisibleForTesting
    String getConsentApiTypeKey(@ConsentParcel.ConsentApiType int consentApiType) {
        return CONSENT_API_TYPE_PREFIX + consentApiType;
    }

    /** tearDown method used for Testing only. */
    @VisibleForTesting
    public void tearDownForTesting() {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastore.tearDownForTesting();
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    @VisibleForTesting static final String IS_AD_ID_ENABLED = "IS_AD_ID_ENABLED";

    /** Returns whether the isAdIdEnabled bit is true. */
    public boolean isAdIdEnabled() {
        return getValueWithLock(IS_AD_ID_ENABLED);
    }

    /** Set the AdIdEnabled bit in system server. */
    public void setAdIdEnabled(boolean isAdIdEnabled) {
        setValueWithLock(IS_AD_ID_ENABLED, isAdIdEnabled, /* callerName */ "setAdIdEnabled");
    }

    @VisibleForTesting static final String IS_U18_ACCOUNT = "IS_U18_ACCOUNT";

    /** Returns whether the isU18Account bit is true. */
    public boolean isU18Account() {
        return getValueWithLock(IS_U18_ACCOUNT);
    }

    /** Set the U18Account bit in system server. */
    public void setU18Account(boolean isU18Account) {
        setValueWithLock(IS_U18_ACCOUNT, isU18Account, /* callerName */ "setU18Account");
    }

    @VisibleForTesting static final String IS_ENTRY_POINT_ENABLED = "IS_ENTRY_POINT_ENABLED";

    /** Returns whether the isEntryPointEnabled bit is true. */
    public boolean isEntryPointEnabled() {
        return getValueWithLock(IS_ENTRY_POINT_ENABLED);
    }

    /** Set the EntryPointEnabled bit in system server. */
    public void setEntryPointEnabled(boolean isEntryPointEnabled) {
        setValueWithLock(
                IS_ENTRY_POINT_ENABLED,
                isEntryPointEnabled, /* callerName */
                "setEntryPointEnabled");
    }

    @VisibleForTesting static final String IS_ADULT_ACCOUNT = "IS_ADULT_ACCOUNT";

    /** Returns whether the isAdultAccount bit is true. */
    public boolean isAdultAccount() {
        return getValueWithLock(IS_ADULT_ACCOUNT);
    }

    /** Set the AdultAccount bit in system server. */
    public void setAdultAccount(boolean isAdultAccount) {
        setValueWithLock(IS_ADULT_ACCOUNT, isAdultAccount, /* callerName */ "setAdultAccount");
    }

    @VisibleForTesting
    static final String WAS_U18_NOTIFICATION_DISPLAYED = "WAS_U18_NOTIFICATION_DISPLAYED";

    /** Returns whether the wasU18NotificationDisplayed bit is true. */
    public boolean wasU18NotificationDisplayed() {
        return getValueWithLock(WAS_U18_NOTIFICATION_DISPLAYED);
    }

    /** Set the U18NotificationDisplayed bit in system server. */
    public void setU18NotificationDisplayed(boolean wasU18NotificationDisplayed)
            throws IOException {
        setValueWithLock(
                WAS_U18_NOTIFICATION_DISPLAYED,
                wasU18NotificationDisplayed,
                /* callerName */ "setU18NotificationDisplayed");
    }

    /** Set the current enabled privacy sux. */
    public void setUx(String eligibleUx) {
        mReadWriteLock.writeLock().lock();
        try {
            Stream.of(PrivacySandboxUxCollection.values())
                    .forEach(
                            ux -> {
                                try {
                                    mDatastore.put(ux.toString(), ux.toString().equals(eligibleUx));
                                } catch (IOException e) {
                                    LogUtil.e(
                                            "IOException caught while setting the current UX."
                                                    + e.getMessage());
                                }
                            });
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns the current UX. */
    public String getUx() {
        mReadWriteLock.readLock().lock();
        try {
            return Stream.of(PrivacySandboxUxCollection.values())
                    .filter(ux -> Boolean.TRUE.equals(mDatastore.get(ux.toString())))
                    .findFirst()
                    .orElse(PrivacySandboxUxCollection.UNSUPPORTED_UX)
                    .toString();
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /** Set the current enrollment channel. */
    public void setEnrollmentChannel(String enrollmentChannel) {
        mReadWriteLock.writeLock().lock();
        try {
            Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                    .forEach(
                            channel -> {
                                try {
                                    mDatastore.put(
                                            channel.toString(),
                                            channel.toString().equals(enrollmentChannel));
                                } catch (IOException e) {
                                    LogUtil.e(
                                            "IOException caught while setting the current "
                                                    + "enrollment channel."
                                                    + e.getMessage());
                                }
                            });
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Returns the current enrollment channel. */
    public String getEnrollmentChannel() {
        mReadWriteLock.readLock().lock();
        try {
            PrivacySandboxEnrollmentChannelCollection enrollmentChannel =
                    Stream.of(PrivacySandboxEnrollmentChannelCollection.values())
                            .filter(
                                    channel ->
                                            Boolean.TRUE.equals(mDatastore.get(channel.toString())))
                            .findFirst()
                            .orElse(null);
            if (enrollmentChannel != null) {
                return enrollmentChannel.toString();
            }
        } finally {
            mReadWriteLock.readLock().unlock();
        }
        return null;
    }

    private boolean getValueWithLock(String key) {
        mReadWriteLock.readLock().lock();
        try {
            Boolean value = mDatastore.get(key);
            return value != null ? value : false;
        } finally {
            mReadWriteLock.readLock().unlock();
        }
    }

    /**
     * Sets the boolean value to the Datastore, using read write to make sure it is thread safe.
     *
     * @param key for the datastore
     * @param value boolean value to set
     * @param callerName the function name who call this setValueWithLock
     */
    private void setValueWithLock(String key, Boolean value, String callerName) {
        mReadWriteLock.writeLock().lock();
        try {
            mDatastore.put(key, value);
        } catch (IOException e) {
            LogUtil.e(
                    e,
                    callerName
                            + " operation failed due to IOException thrown by Datastore: "
                            + e.getMessage());
        } finally {
            mReadWriteLock.writeLock().unlock();
        }
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%sConsentManager:\n", prefix);
        String prefix2 = prefix + "  ";

        mDatastore.dump(writer, prefix2);
    }
}
