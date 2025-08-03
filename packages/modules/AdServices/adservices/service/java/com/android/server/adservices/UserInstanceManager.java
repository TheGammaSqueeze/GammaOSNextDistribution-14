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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.util.ArrayMap;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.server.adservices.consent.AppConsentManager;
import com.android.server.adservices.consent.ConsentManager;
import com.android.server.adservices.data.topics.TopicsDao;
import com.android.server.adservices.rollback.RollbackHandlingManager;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * Manager to handle User Instance. This is to ensure that each user profile is isolated.
 *
 * @hide
 */
public class UserInstanceManager {

    private final Object mLock = new Object();

    // We have 1 ConsentManager per user/user profile. This is to isolate user's data.
    @GuardedBy("mLock")
    private final ArrayMap<Integer, ConsentManager> mConsentManagerMapLocked = new ArrayMap<>(0);

    @GuardedBy("mLock")
    private final ArrayMap<Integer, AppConsentManager> mAppConsentManagerMapLocked =
            new ArrayMap<>(0);

    @GuardedBy("UserInstanceManager.class")
    private final ArrayMap<Integer, BlockedTopicsManager> mBlockedTopicsManagerMapLocked =
            new ArrayMap<>(0);

    // We have 1 RollbackManager per user/user profile, to isolate each user's data.
    @GuardedBy("mLock")
    private final ArrayMap<Integer, RollbackHandlingManager> mRollbackHandlingManagerMapLocked =
            new ArrayMap<>(0);

    private final String mAdServicesBaseDir;

    private final TopicsDao mTopicsDao;

    UserInstanceManager(@NonNull TopicsDao topicsDao, @NonNull String adServicesBaseDir) {
        mTopicsDao = topicsDao;
        mAdServicesBaseDir = adServicesBaseDir;
    }

    @NonNull
    ConsentManager getOrCreateUserConsentManagerInstance(int userIdentifier) throws IOException {
        synchronized (mLock) {
            ConsentManager instance = getUserConsentManagerInstance(userIdentifier);
            if (instance == null) {
                instance = ConsentManager.createConsentManager(mAdServicesBaseDir, userIdentifier);
                mConsentManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @NonNull
    AppConsentManager getOrCreateUserAppConsentManagerInstance(int userIdentifier)
            throws IOException {
        synchronized (mLock) {
            AppConsentManager instance = mAppConsentManagerMapLocked.get(userIdentifier);
            if (instance == null) {
                instance =
                        AppConsentManager.createAppConsentManager(
                                mAdServicesBaseDir, userIdentifier);
                mAppConsentManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @NonNull
    BlockedTopicsManager getOrCreateUserBlockedTopicsManagerInstance(int userIdentifier) {
        synchronized (UserInstanceManager.class) {
            BlockedTopicsManager instance = mBlockedTopicsManagerMapLocked.get(userIdentifier);
            if (instance == null) {
                instance = new BlockedTopicsManager(mTopicsDao, userIdentifier);
                mBlockedTopicsManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @NonNull
    RollbackHandlingManager getOrCreateUserRollbackHandlingManagerInstance(
            int userIdentifier, int packageVersion) throws IOException {
        synchronized (mLock) {
            RollbackHandlingManager instance =
                    mRollbackHandlingManagerMapLocked.get(userIdentifier);
            if (instance == null) {
                instance =
                        RollbackHandlingManager.createRollbackHandlingManager(
                                mAdServicesBaseDir, userIdentifier, packageVersion);
                mRollbackHandlingManagerMapLocked.put(userIdentifier, instance);
            }
            return instance;
        }
    }

    @VisibleForTesting
    ConsentManager getUserConsentManagerInstance(int userIdentifier) {
        synchronized (mLock) {
            return mConsentManagerMapLocked.get(userIdentifier);
        }
    }

    /**
     * Deletes the user instance and remove the user consent related data. This will delete the
     * directory: /data/system/adservices/user_id
     */
    void deleteUserInstance(int userIdentifier) throws Exception {
        synchronized (mLock) {
            ConsentManager instance = mConsentManagerMapLocked.get(userIdentifier);
            if (instance != null) {
                String userDirectoryPath = mAdServicesBaseDir + "/" + userIdentifier;
                final Path packageDir = Paths.get(userDirectoryPath);
                if (Files.exists(packageDir)) {
                    if (!instance.deleteUserDirectory(new File(userDirectoryPath))) {
                        LogUtil.e("Failed to delete " + userDirectoryPath);
                    }
                }
                mConsentManagerMapLocked.remove(userIdentifier);
            }

            // Delete all data in the database that belongs to this user
            mTopicsDao.clearAllBlockedTopicsOfUser(userIdentifier);
        }
    }

    void dump(PrintWriter writer, String[] args) {
        writer.println("UserInstanceManager");
        String prefix = "  ";
        writer.printf("%smAdServicesBaseDir: %s\n", prefix, mAdServicesBaseDir);
        dumpPerUserManagers(writer, prefix);
        mTopicsDao.dump(writer, prefix, args);
    }

    private void dumpPerUserManagers(PrintWriter writer, String prefix) {
        ArrayMap<Integer, PerUserDumpHelper> perUserDumpHelpers;

        synchronized (mLock) {
            perUserDumpHelpers = new ArrayMap<>(mConsentManagerMapLocked.size());
            for (int i = 0; i < mConsentManagerMapLocked.size(); i++) {
                getPerUserDumpHelperForUser(perUserDumpHelpers, mConsentManagerMapLocked.keyAt(0))
                                .consentMgr =
                        mConsentManagerMapLocked.valueAt(i);
            }
            for (int i = 0; i < mAppConsentManagerMapLocked.size(); i++) {
                getPerUserDumpHelperForUser(
                                        perUserDumpHelpers, mAppConsentManagerMapLocked.keyAt(0))
                                .appConsentMgr =
                        mAppConsentManagerMapLocked.valueAt(i);
            }
            for (int i = 0; i < mRollbackHandlingManagerMapLocked.size(); i++) {
                getPerUserDumpHelperForUser(
                                        perUserDumpHelpers,
                                        mRollbackHandlingManagerMapLocked.keyAt(0))
                                .rollbackHandlingMgr =
                        mRollbackHandlingManagerMapLocked.valueAt(i);
            }
        }
        synchronized (UserInstanceManager.class) {
            for (int i = 0; i < mBlockedTopicsManagerMapLocked.size(); i++) {
                getPerUserDumpHelperForUser(
                                        perUserDumpHelpers, mBlockedTopicsManagerMapLocked.keyAt(0))
                                .blockedTopicsMgr =
                        mBlockedTopicsManagerMapLocked.valueAt(i);
            }
        }

        int numberUsers = perUserDumpHelpers.size();
        if (numberUsers == 0) {
            writer.printf("%sno per-user data yet\n", prefix);
        } else {
            writer.printf("%s%d users:\n", prefix, numberUsers);
            String prefix2 = prefix + "  ";
            for (int i = 0; i < numberUsers; i++) {
                perUserDumpHelpers.valueAt(i).dump(writer, prefix2, perUserDumpHelpers.keyAt(i));
            }
        }
    }

    @VisibleForTesting
    void tearDownForTesting() {
        synchronized (mLock) {
            for (ConsentManager consentManager : mConsentManagerMapLocked.values()) {
                consentManager.tearDownForTesting();
            }
            for (AppConsentManager appConsentManager : mAppConsentManagerMapLocked.values()) {
                appConsentManager.tearDownForTesting();
            }
            for (RollbackHandlingManager rollbackHandlingManager :
                    mRollbackHandlingManagerMapLocked.values()) {
                rollbackHandlingManager.tearDownForTesting();
            }
        }
    }

    private PerUserDumpHelper getPerUserDumpHelperForUser(
            ArrayMap<Integer, PerUserDumpHelper> map, Integer userId) {
        PerUserDumpHelper dumper = map.get(userId);
        if (dumper == null) {
            dumper = new PerUserDumpHelper();
            map.put(userId, dumper);
        }
        return dumper;
    }

    /**
     * Helper class used to group all managers per-user during dump(), as there is no guarantee that
     * each map of managers will have all managers for a given user.
     */
    private static final class PerUserDumpHelper {
        public @Nullable ConsentManager consentMgr;
        public @Nullable AppConsentManager appConsentMgr;
        public @Nullable BlockedTopicsManager blockedTopicsMgr;
        public @Nullable RollbackHandlingManager rollbackHandlingMgr;

        public void dump(PrintWriter writer, String prefix, Integer userId) {
            writer.printf("%sUser %d:\n", prefix, userId);
            String prefix2 = prefix + "  ";
            if (consentMgr == null) {
                writer.printf("%sno consent manager\n", prefix2);
            } else {
                consentMgr.dump(writer, prefix2);
            }
            if (appConsentMgr == null) {
                writer.printf("%sno app consent manager\n", prefix2);
            } else {
                appConsentMgr.dump(writer, prefix2);
            }
            if (blockedTopicsMgr == null) {
                writer.printf("%sno blocked topics manager\n", prefix2);
            } else {
                blockedTopicsMgr.dump(writer, prefix2);
            }
            if (rollbackHandlingMgr == null) {
                writer.printf("%sno rollback handling manager\n", prefix2);
            } else {
                rollbackHandlingMgr.dump(writer, prefix2);
            }
        }
    }
}
