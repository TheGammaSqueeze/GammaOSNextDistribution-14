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

package com.android.server.appsearch.contactsindexer;

import static android.os.Process.INVALID_UID;

import android.annotation.BinderThread;
import android.annotation.NonNull;
import android.annotation.UserIdInt;
import android.app.appsearch.AppSearchEnvironmentFactory;
import android.app.appsearch.util.LogUtil;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.os.CancellationSignal;
import android.os.PatternMatcher;
import android.os.UserHandle;
import android.provider.ContactsContract;
import android.util.Log;
import android.util.Slog;
import android.util.SparseArray;

import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import android.app.appsearch.AppSearchEnvironment;

import java.io.File;
import java.io.PrintWriter;
import java.util.List;
import java.util.Objects;

/**
 * Manages the per device-user ContactsIndexer instance to index CP2 contacts into AppSearch.
 *
 * <p>This class is thread-safe.
 *
 * @hide
 */
public final class ContactsIndexerManagerService extends SystemService {
    static final String TAG = "ContactsIndexerManagerS";

    private static final String DEFAULT_CONTACTS_PROVIDER_PACKAGE_NAME =
            "com.android.providers.contacts";

    private final Context mContext;
    private final ContactsIndexerConfig mContactsIndexerConfig;
    private final LocalService mLocalService;
    // Sparse array of ContactsIndexerUserInstance indexed by the device-user ID.
    private final SparseArray<ContactsIndexerUserInstance> mContactsIndexersLocked =
            new SparseArray<>();

    private String mContactsProviderPackageName;

    /** Constructs a {@link ContactsIndexerManagerService}. */
    public ContactsIndexerManagerService(@NonNull Context context,
            @NonNull ContactsIndexerConfig contactsIndexerConfig) {
        super(context);
        mContext = Objects.requireNonNull(context);
        mContactsIndexerConfig = Objects.requireNonNull(contactsIndexerConfig);
        mLocalService = new LocalService();
    }

    @Override
    public void onStart() {
        mContactsProviderPackageName = getContactsProviderPackageName();
        registerReceivers();
        LocalManagerRegistry.addManager(LocalService.class, mLocalService);
    }

    @Override
    public void onUserUnlocking(@NonNull TargetUser user) {
        try {
            Objects.requireNonNull(user);
            UserHandle userHandle = user.getUserHandle();
            synchronized (mContactsIndexersLocked) {
                int userId = userHandle.getIdentifier();
                ContactsIndexerUserInstance instance = mContactsIndexersLocked.get(userId);
                if (instance == null) {
                    AppSearchEnvironment appSearchEnvironment = AppSearchEnvironmentFactory
                            .getEnvironmentInstance();
                    Context userContext = appSearchEnvironment
                            .createContextAsUser(mContext, userHandle);
                    File appSearchDir = appSearchEnvironment
                            .getAppSearchDir(userContext, userHandle);
                    File contactsDir = new File(appSearchDir, "contacts");
                    instance = ContactsIndexerUserInstance.createInstance(userContext,
                            contactsDir, mContactsIndexerConfig);
                    if (LogUtil.DEBUG) {
                        Log.d(TAG, "Created Contacts Indexer instance for user " + userHandle);
                    }
                    mContactsIndexersLocked.put(userId, instance);
                }
                instance.startAsync();
            }
        } catch (RuntimeException e) {
            Slog.wtf(TAG, "ContactsIndexerManagerService.onUserUnlocking() failed ", e);
        }
    }

    @Override
    public void onUserStopping(@NonNull TargetUser user) {
        try {
            Objects.requireNonNull(user);
            UserHandle userHandle = user.getUserHandle();
            synchronized (mContactsIndexersLocked) {
                int userId = userHandle.getIdentifier();
                ContactsIndexerUserInstance instance = mContactsIndexersLocked.get(userId);
                if (instance != null) {
                    mContactsIndexersLocked.delete(userId);
                    try {
                        instance.shutdown();
                    } catch (InterruptedException e) {
                        Log.w(TAG, "Failed to shutdown contacts indexer for " + userHandle, e);
                    }
                }
            }
        } catch (RuntimeException e) {
            Slog.wtf(TAG, "ContactsIndexerManagerService.onUserStopping() failed ", e);
        }
    }

    /** Dumps ContactsIndexer internal state for the user. */
    @BinderThread
    public void dumpContactsIndexerForUser(
            @NonNull UserHandle userHandle, @NonNull PrintWriter pw, boolean verbose) {
        try{
            Objects.requireNonNull(userHandle);
            Objects.requireNonNull(pw);
            int userId = userHandle.getIdentifier();
            synchronized (mContactsIndexersLocked) {
                ContactsIndexerUserInstance instance = mContactsIndexersLocked.get(userId);
                if (instance != null) {
                    instance.dump(pw, verbose);
                } else {
                    pw.println("ContactsIndexerUserInstance is not created for " + userHandle);
                }
            }
        } catch (RuntimeException e) {
            Slog.wtf(TAG, "ContactsIndexerManagerService.dumpContactsIndexerForUser() failed ", e);
        }
    }

    /**
     * Returns the package name where the Contacts Provider is hosted.
     */
    private String getContactsProviderPackageName() {
        PackageManager pm = mContext.getPackageManager();
        List<ProviderInfo> providers = pm.queryContentProviders(/*processName=*/ null, /*uid=*/ 0,
                PackageManager.ComponentInfoFlags.of(0));
        for (int i = 0; i < providers.size(); i++) {
            ProviderInfo providerInfo = providers.get(i);
            if (ContactsContract.AUTHORITY.equals(providerInfo.authority)) {
                return  providerInfo.packageName;
            }
        }
        return DEFAULT_CONTACTS_PROVIDER_PACKAGE_NAME;
    }

    /**
     * Registers a broadcast receiver to get package changed (disabled/enabled) and package data
     * cleared events for CP2.
     */
    private void registerReceivers() {
        IntentFilter contactsProviderChangedFilter = new IntentFilter();
        contactsProviderChangedFilter.addAction(Intent.ACTION_PACKAGE_CHANGED);
        contactsProviderChangedFilter.addAction(Intent.ACTION_PACKAGE_DATA_CLEARED);
        contactsProviderChangedFilter.addDataScheme("package");
        contactsProviderChangedFilter.addDataSchemeSpecificPart(mContactsProviderPackageName,
                PatternMatcher.PATTERN_LITERAL);
        mContext.registerReceiverForAllUsers(
                new ContactsProviderChangedReceiver(),
                contactsProviderChangedFilter,
                /*broadcastPermission=*/ null,
                /*scheduler=*/ null);
        if (LogUtil.DEBUG) {
            Log.v(TAG, "Registered receiver for CP2 (package: " + mContactsProviderPackageName + ")"
                    + " data cleared events");
        }
    }

    /**
     * Broadcast receiver to handle CP2 changed (disabled/enabled) and package data cleared events.
     *
     * <p>Contacts indexer syncs on-device contacts from ContactsProvider (CP2) denoted by {@link
     * android.provider.ContactsContract.Contacts#AUTHORITY} into the AppSearch "builtin:Person"
     * corpus under the "android" package name. The default package which hosts CP2 is
     * "com.android.providers.contacts" but it could be different on OEM devices. Since the Android
     * package that hosts CP2 is different from the package name that "owns" the builtin:Person
     * corpus in AppSearch, clearing the CP2 package data doesn't automatically clear the
     * builtin:Person corpus in AppSearch.
     *
     * <p>This broadcast receiver allows contacts indexer to listen to events which indicate that
     * CP2 data was cleared and force a full sync of CP2 contacts into AppSearch.
     */
    private class ContactsProviderChangedReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(@NonNull Context context, @NonNull Intent intent) {
            try {
                Objects.requireNonNull(context);
                Objects.requireNonNull(intent);

                switch (intent.getAction()) {
                    case Intent.ACTION_PACKAGE_CHANGED:
                    case Intent.ACTION_PACKAGE_DATA_CLEARED:
                        String packageName = intent.getData().getSchemeSpecificPart();
                        if (LogUtil.DEBUG) {
                            Log.v(TAG, "Received package data cleared event for " + packageName);
                        }
                        if (!mContactsProviderPackageName.equals(packageName)) {
                            return;
                        }
                        int uid = intent.getIntExtra(Intent.EXTRA_UID, INVALID_UID);
                        if (uid == INVALID_UID) {
                            Log.w(TAG, "uid is missing in the intent: " + intent);
                            return;
                        }
                        int userId = UserHandle.getUserHandleForUid(uid).getIdentifier();
                        mLocalService.doFullUpdateForUser(userId,  new CancellationSignal());
                        break;
                    default:
                        Log.w(TAG, "Received unknown intent: " + intent);
                }
            } catch (RuntimeException e) {
                Slog.wtf(TAG, "ContactsProviderChangedReceiver.onReceive() failed ", e);
            }
        }
    }

    class LocalService {
        void doFullUpdateForUser(@UserIdInt int userId, @NonNull CancellationSignal signal) {
            Objects.requireNonNull(signal);
            synchronized (mContactsIndexersLocked) {
                ContactsIndexerUserInstance instance = mContactsIndexersLocked.get(userId);
                if (instance != null) {
                    instance.doFullUpdateAsync(signal);
                }
            }
        }
    }
}
