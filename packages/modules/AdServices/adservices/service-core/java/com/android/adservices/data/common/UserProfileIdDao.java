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

import java.util.UUID;

/** Interface of user profile id storage model. */
public interface UserProfileIdDao {
    /** Returns the user profile id if exists in the storage or returns null otherwise. */
    @Nullable
    UUID getUserProfileId();

    /**
     * Returns the timestamp (milliseconds since epoch) when the user profile id was persisted.
     *
     * <p>Returns 0 if not found
     */
    long getTimestamp();

    /** Sets the user profile id. */
    void setUserProfileId(@NonNull UUID userProfileId);

    /** Delete the user profile id from the storage. */
    void deleteStorage();
}
