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

package com.android.adservices.data.common;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.content.SharedPreferences;

import com.android.adservices.shared.util.Clock;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/** {@link UserProfileIdDao} implementation with {@link SharedPreferences}. */
public class UserProfileIdDaoSharedPreferencesImpl implements UserProfileIdDao {
    private static final Object SINGLETON_LOCK = new Object();

    private static final String STORAGE_NAME = "user_profile_id";
    @VisibleForTesting static final String USER_PROFILE_ID_KEY = "user_profile_id_key";

    @VisibleForTesting
    static final String USER_PROFILE_ID_CREATION_TIMESTAMP_KEY =
            "user_profile_id_creation_timestamp_key";

    private final SharedPreferences mSharedPreferences;
    private final Clock mClock;

    @GuardedBy("SINGLETON_LOCK")
    private static volatile UserProfileIdDao sUserProfileIdDao;

    @VisibleForTesting
    UserProfileIdDaoSharedPreferencesImpl(
            @NonNull final SharedPreferences sharedPreferences, @NonNull final Clock clock) {
        Objects.requireNonNull(sharedPreferences);

        mSharedPreferences = sharedPreferences;
        mClock = clock;
    }

    /** Returns the singleton instance of the {@link UserProfileIdDaoSharedPreferencesImpl} */
    @NonNull
    public static UserProfileIdDao getInstance(@NonNull final Context context) {
        Objects.requireNonNull(context, "Context must be provided.");

        synchronized (SINGLETON_LOCK) {
            if (sUserProfileIdDao == null) {
                sUserProfileIdDao =
                        new UserProfileIdDaoSharedPreferencesImpl(
                                context.getSharedPreferences(STORAGE_NAME, Context.MODE_PRIVATE),
                                Clock.getInstance());
            }
            return sUserProfileIdDao;
        }
    }

    @Override
    @Nullable
    public UUID getUserProfileId() {
        return Optional.ofNullable(mSharedPreferences.getString(USER_PROFILE_ID_KEY, null))
                .map(UUID::fromString)
                .orElse(null);
    }

    @Override
    public long getTimestamp() {
        return mSharedPreferences.getLong(USER_PROFILE_ID_CREATION_TIMESTAMP_KEY, 0);
    }

    @Override
    public void setUserProfileId(@NonNull final UUID userProfileId) {
        Objects.requireNonNull(userProfileId);

        mSharedPreferences.edit().putString(USER_PROFILE_ID_KEY, userProfileId.toString()).commit();
        mSharedPreferences
                .edit()
                .putLong(USER_PROFILE_ID_CREATION_TIMESTAMP_KEY, mClock.currentTimeMillis())
                .commit();
    }

    @Override
    public void deleteStorage() {
        mSharedPreferences.edit().clear().commit();
    }
}
