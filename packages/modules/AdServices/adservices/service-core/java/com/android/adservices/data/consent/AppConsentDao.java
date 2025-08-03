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

package com.android.adservices.data.consent;

import android.content.Context;
import android.content.pm.PackageManager;

import androidx.annotation.NonNull;

import com.android.adservices.LogUtil;
import com.android.adservices.data.common.BooleanFileDatastore;
import com.android.adservices.service.common.compat.FileCompatUtils;
import com.android.adservices.service.common.compat.PackageManagerCompatUtils;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import com.google.common.base.Preconditions;

import java.io.IOException;
import java.util.HashSet;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Data access object for the App Consent datastore serving the Privacy Sandbox Consent Manager and
 * the FLEDGE Custom Audience and Ad Selection APIs.
 *
 * <p>This class is not thread safe. It's methods are not synchronized.
 */
public class AppConsentDao {
    @VisibleForTesting public static final int DATASTORE_VERSION = 1;

    @VisibleForTesting
    public static final String DATASTORE_NAME =
            FileCompatUtils.getAdservicesFilename("adservices.appconsent.xml");

    private static final Object SINGLETON_LOCK = new Object();

    @VisibleForTesting static final String DATASTORE_KEY_SEPARATOR = "  ";

    @GuardedBy("SINGLETON_LOCK")
    private static volatile AppConsentDao sAppConsentDao;

    private volatile boolean mInitialized = false;

    /**
     * The {@link BooleanFileDatastore} will store {@code true} if an app has had its consent
     * revoked and {@code false} if the app is allowed (has not had its consent revoked). Keys in
     * the datastore consist of a combination of package name and UID.
     */
    private final BooleanFileDatastore mDatastore;

    private final PackageManager mPackageManager;

    /** Constructs the {@link AppConsentDao}. */
    @VisibleForTesting
    public AppConsentDao(
            @NonNull BooleanFileDatastore datastore, @NonNull PackageManager packageManager) {
        Objects.requireNonNull(datastore);
        Objects.requireNonNull(packageManager);

        mDatastore = datastore;
        mPackageManager = packageManager;
    }

    /** @return the singleton instance of the {@link AppConsentDao} */
    public static AppConsentDao getInstance(@NonNull Context context) {
        Objects.requireNonNull(context, "Context must be provided.");

        if (sAppConsentDao == null) {
            synchronized (SINGLETON_LOCK) {
                if (sAppConsentDao == null) {
                    BooleanFileDatastore datastore =
                            new BooleanFileDatastore(context, DATASTORE_NAME, DATASTORE_VERSION);
                    PackageManager packageManager = context.getPackageManager();
                    sAppConsentDao = new AppConsentDao(datastore, packageManager);
                }
            }
        }

        return sAppConsentDao;
    }

    /**
     * Lazily initializes the datastore by reading from the written file.
     *
     * <p>Guarantees only one initialization call per singleton object.
     *
     * @throws IOException if datastore initialization fails
     */
    @VisibleForTesting
    void initializeDatastoreIfNeeded() throws IOException {
        if (!mInitialized) {
            synchronized (SINGLETON_LOCK) {
                if (!mInitialized) {
                    mDatastore.initialize();
                    mInitialized = true;
                }
            }
        }
    }

    /**
     * @return a set of all known apps in the database that have not had user consent revoked
     * @throws IOException if the operation fails
     */
    public Set<String> getKnownAppsWithConsent() throws IOException {
        initializeDatastoreIfNeeded();
        Set<String> apps = new HashSet<>();
        Set<String> datastoreKeys = mDatastore.keySetFalse();
        Set<String> installedPackages = getInstalledPackages();
        for (String key : datastoreKeys) {
            String packageName = datastoreKeyToPackageName(key);
            if (installedPackages.contains(packageName)) {
                apps.add(packageName);
            }
        }

        return apps;
    }

    /**
     * @return a set of all known apps in the database that have had user consent revoked
     * @throws IOException if the operation fails
     */
    public Set<String> getAppsWithRevokedConsent() throws IOException {
        initializeDatastoreIfNeeded();
        Set<String> apps = new HashSet<>();
        Set<String> datastoreKeys = mDatastore.keySetTrue();
        Set<String> installedPackages = getInstalledPackages();
        for (String key : datastoreKeys) {
            String packageName = datastoreKeyToPackageName(key);
            if (installedPackages.contains(packageName)) {
                apps.add(packageName);
            }
        }

        return apps;
    }

    /**
     * Sets consent for a given installed application, identified by package name.
     *
     * @throws IllegalArgumentException if the package name is invalid or not found as an installed
     *     application
     * @throws IOException if the operation fails
     */
    public void setConsentForApp(@NonNull String packageName, boolean isConsentRevoked)
            throws IllegalArgumentException, IOException {
        initializeDatastoreIfNeeded();
        mDatastore.put(toDatastoreKey(packageName), isConsentRevoked);
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
    public boolean setConsentForAppIfNew(@NonNull String packageName, boolean isConsentRevoked)
            throws IllegalArgumentException, IOException {
        initializeDatastoreIfNeeded();
        return mDatastore.putIfNew(toDatastoreKey(packageName), isConsentRevoked);
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
    public boolean isConsentRevokedForApp(@NonNull String packageName)
            throws IllegalArgumentException, IOException {
        initializeDatastoreIfNeeded();
        return Boolean.TRUE.equals(mDatastore.get(toDatastoreKey(packageName)));
    }

    /**
     * Clears the consent datastore of all settings.
     *
     * @throws IOException if the operation fails
     */
    public void clearAllConsentData() throws IOException {
        initializeDatastoreIfNeeded();
        mDatastore.clear();
    }

    /**
     * Clears the consent datastore of all known apps with consent. Apps with revoked consent are
     * not removed.
     *
     * @throws IOException if the operation fails
     */
    public void clearKnownAppsWithConsent() throws IOException {
        initializeDatastoreIfNeeded();
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
        initializeDatastoreIfNeeded();
        // Do not check whether the application has been uninstalled; in an edge case where the app
        // may have been reinstalled, data that should have been cleared might then be persisted
        mDatastore.remove(toDatastoreKey(packageName, packageUid));
    }

    /**
     * Removes the consent setting for an application (if it exists in the datastore). <strong>All
     * entries matching this package name will be removed.</strong>
     *
     * <p>This method is meant for backwards-compatibility to Android R & S, and should only be
     * invoked on Android versions prior to T, where the package UID is not available when the
     * package is uninstalled.
     *
     * @throws IllegalArgumentException if the package name is invalid
     * @throws IOException if the operation fails
     */
    public void clearConsentForUninstalledApp(@NonNull String packageName) throws IOException {
        Objects.requireNonNull(packageName, "Package name must be provided");
        Preconditions.checkArgument(!packageName.isEmpty(), "Invalid package name");

        initializeDatastoreIfNeeded();

        // It's not possible to use the toDatastoreKey method to look up the key because the
        // package has been uninstalled. Instead, ask the datastore to clear data for all entries
        // beginning with the package name + separator, since the datastore stores keys in the
        // form of package name + separator + package uid.
        mDatastore.removeByPrefix(packageName + DATASTORE_KEY_SEPARATOR);
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
     * Returns the key that corresponds to the given package name.
     *
     * <p>The given package name is checked for installation status.
     *
     * @throws IllegalArgumentException if the package name does not correspond to an installed
     *     application
     */
    @VisibleForTesting
    @NonNull
    String toDatastoreKey(@NonNull String packageName) throws IllegalArgumentException {
        Objects.requireNonNull(packageName);

        int packageUid = getUidForInstalledPackageName(packageName);

        return toDatastoreKey(packageName, packageUid);
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

    /**
     * Checks if a package name corresponds to a valid installed app for the user and returns its
     * UID if so.
     *
     * @return the UID for the installed application, if found
     */
    public int getUidForInstalledPackageName(@NonNull String packageName) {
        Objects.requireNonNull(packageName);

        try {
            return PackageManagerCompatUtils.getPackageUid(mPackageManager, packageName, 0);
        } catch (PackageManager.NameNotFoundException exception) {
            LogUtil.e(exception, "Package name not found");
            throw new IllegalArgumentException(exception);
        }
    }

    /** Returns the list of packages installed on the device of the user. */
    @NonNull
    public Set<String> getInstalledPackages() {
        return PackageManagerCompatUtils.getInstalledApplications(mPackageManager, 0).stream()
                .map(applicationInfo -> applicationInfo.packageName)
                .collect(Collectors.toSet());
    }
}
