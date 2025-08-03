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

import com.android.adservices.shared.storage.BooleanFileDatastore;
import com.android.internal.annotations.VisibleForTesting;
import com.android.internal.util.Preconditions;

import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Manager to handle user's consent for a certain App. We will have one AppConsentManager instance
 * per user.
 *
 * @hide
 */
public class AppConsentManager {
    @VisibleForTesting public static final int DATASTORE_VERSION = 1;
    @VisibleForTesting public static final String DATASTORE_NAME = "adservices.appconsent.xml";

    @VisibleForTesting static final String DATASTORE_KEY_SEPARATOR = "  ";

    @VisibleForTesting static final String VERSION_KEY = "android.app.adservices.consent.VERSION";

    @VisibleForTesting
    static final String BASE_DIR_MUST_BE_PROVIDED_ERROR_MESSAGE = "Base dir must be provided.";

    @VisibleForTesting static final String DUMP_PREFIX = "  ";

    /**
     * The {@link BooleanFileDatastore} will store {@code true} if an app has had its consent
     * revoked and {@code false} if the app is allowed (has not had its consent revoked). Keys in
     * the datastore consist of a combination of package name and UID.
     */
    private final BooleanFileDatastore mDatastore;

    /** Constructs the {@link AppConsentManager}. */
    @VisibleForTesting
    public AppConsentManager(@NonNull BooleanFileDatastore datastore) {
        Objects.requireNonNull(datastore);

        mDatastore = datastore;
    }

    /** @return the singleton instance of the {@link AppConsentManager} */
    public static AppConsentManager createAppConsentManager(String baseDir, int userIdentifier)
            throws IOException {
        Objects.requireNonNull(baseDir, BASE_DIR_MUST_BE_PROVIDED_ERROR_MESSAGE);

        // The Data store is in folder with the following format.
        // /data/system/adservices/user_id/consent/data_schema_version/
        // Create the consent directory if needed.
        String consentDataStoreDir =
                ConsentDatastoreLocationHelper.getConsentDataStoreDirAndCreateDir(
                        baseDir, userIdentifier);

        BooleanFileDatastore datastore =
                new BooleanFileDatastore(
                        consentDataStoreDir, DATASTORE_NAME, DATASTORE_VERSION, VERSION_KEY);
        datastore.initialize();

        return new AppConsentManager(datastore);
    }

    /** @return a set of all known apps in the database that have not had user consent revoked */
    @NonNull
    public List<String> getKnownAppsWithConsent(@NonNull List<String> installedPackages) {
        Objects.requireNonNull(installedPackages);

        Set<String> apps = new HashSet<>();
        Set<String> appWithConsentInDatastore = mDatastore.keySetFalse();
        for (String appDatastoreKey : appWithConsentInDatastore) {
            String packageName = datastoreKeyToPackageName(appDatastoreKey);
            if (installedPackages.contains(packageName)) {
                apps.add(packageName);
            }
        }

        return new ArrayList<>(apps);
    }

    /**
     * @return a set of all known apps in the database that have had user consent revoked
     * @throws IOException if the operation fails
     */
    @NonNull
    public List<String> getAppsWithRevokedConsent(@NonNull List<String> installedPackages)
            throws IOException {
        Objects.requireNonNull(installedPackages);

        Set<String> apps = new HashSet<>();
        Set<String> appWithoutConsentInDatastore = mDatastore.keySetTrue();
        for (String appDatastoreKey : appWithoutConsentInDatastore) {
            String packageName = datastoreKeyToPackageName(appDatastoreKey);
            if (installedPackages.contains(packageName)) {
                apps.add(packageName);
            }
        }

        return new ArrayList<>(apps);
    }

    /**
     * Sets consent for a given installed application, identified by package name.
     *
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public void setConsentForApp(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked)
            throws IllegalArgumentException, IOException {
        mDatastore.put(toDatastoreKey(packageName, packageUid), isConsentRevoked);
    }

    /**
     * Tries to set consent for a given installed application, identified by package name, if it
     * does not already exist in the datastore, and returns the current consent setting after
     * checking.
     *
     * @return the current consent for the given {@code packageName} after trying to set the {@code
     *     value}
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public boolean setConsentForAppIfNew(
            @NonNull String packageName, int packageUid, boolean isConsentRevoked)
            throws IllegalArgumentException, IOException {
        return mDatastore.putIfNew(toDatastoreKey(packageName, packageUid), isConsentRevoked);
    }

    /**
     * Returns whether a given application (identified by package name) has had user consent
     * revoked.
     *
     * <p>If the given application is installed but is not found in the datastore, the application
     * is treated as having user consent, and this method returns {@code false}.
     *
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public boolean isConsentRevokedForApp(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException, IOException {
        return Boolean.TRUE.equals(mDatastore.get(toDatastoreKey(packageName, packageUid)));
    }

    /**
     * Clears the consent datastore of all settings.
     *
     * @throws IOException if the operation fails
     */
    public void clearAllAppConsentData() throws IOException {
        mDatastore.clear();
    }

    /**
     * Clears the consent datastore of all known apps with consent. Apps with revoked consent are
     * not removed.
     *
     * @throws IOException if the operation fails
     */
    public void clearKnownAppsWithConsent() throws IOException {
        mDatastore.clearAllFalse();
    }

    /**
     * Removes the consent setting for an application (if it exists in the datastore).
     *
     * @throws IllegalArgumentException if the package name or package UID is invalid
     * @throws IOException if the operation fails
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException, IOException {
        // Do not check whether the application has been uninstalled; in an edge case where the app
        // may have been reinstalled, data that should have been cleared might then be persisted
        mDatastore.remove(toDatastoreKey(packageName, packageUid));
    }

    /**
     * Returns the key that corresponds to the given package name and UID.
     *
     * <p>The given package name and UID are not checked for installation status.
     *
     * @throws IllegalArgumentException if the package UID is not valid
     */
    @VisibleForTesting
    @NonNull
    String toDatastoreKey(@NonNull String packageName, int packageUid)
            throws IllegalArgumentException {
        Objects.requireNonNull(packageName, "Package name must be provided");
        Preconditions.checkArgument(!packageName.isEmpty(), "Invalid package name");
        Preconditions.checkArgument(packageUid > 0, "Invalid package UID");
        return packageName.concat(DATASTORE_KEY_SEPARATOR).concat(Integer.toString(packageUid));
    }

    /**
     * Returns the package name extracted from the given datastore key.
     *
     * <p>The package name returned is not guaranteed to correspond to a currently installed
     * package.
     *
     * @throws IllegalArgumentException if the given key does not match the expected schema
     */
    @VisibleForTesting
    @NonNull
    String datastoreKeyToPackageName(@NonNull String datastoreKey) throws IllegalArgumentException {
        Objects.requireNonNull(datastoreKey);
        Preconditions.checkArgument(!datastoreKey.isEmpty(), "Empty input datastore key");
        int separatorIndex = datastoreKey.lastIndexOf(DATASTORE_KEY_SEPARATOR);
        Preconditions.checkArgument(separatorIndex > 0, "Invalid datastore key");
        return datastoreKey.substring(0, separatorIndex);
    }

    /** Dumps its internal state. */
    public void dump(PrintWriter writer, String prefix) {
        writer.printf("%sAppConsentManager:\n", prefix);
        String prefix2 = prefix + DUMP_PREFIX;
        String prefix3 = prefix2 + DUMP_PREFIX;

        writer.printf("%sDatastore:\n", prefix2);
        mDatastore.dump(writer, prefix3);
    }

    /** tearDown method used for Testing only. */
    @VisibleForTesting
    public void tearDownForTesting() {
        synchronized (this) {
            mDatastore.tearDownForTesting();
        }
    }
}
