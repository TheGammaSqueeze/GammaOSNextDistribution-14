/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.role.controller.model;

import android.content.Context;
import android.content.pm.ApplicationInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.Collections;
import java.util.List;

/**
 * Interface for behavior of a role.
 */
public interface RoleBehavior {

    /**
     * @see Role#onRoleAddedAsUser(UserHandle, Context)
     */
    default void onRoleAddedAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {}

    /**
     * @see Role#isAvailableAsUser(UserHandle, Context)
     */
    default boolean isAvailableAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }

    /**
     * @see Role#getDefaultHolders(Context)
     */
    @Nullable
    default List<String> getDefaultHoldersAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#getFallbackHolder(Context)
     */
    @Nullable
    default String getFallbackHolderAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#shouldAllowBypassingQualification(Context)
     */
    @Nullable
    default Boolean shouldAllowBypassingQualification(@NonNull Role role,
                                                      @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#isPackageQualified(String, Context)
     */
    @Nullable
    default Boolean isPackageQualifiedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#getQualifyingPackagesAsUser(UserHandle, Context)
     */
    @Nullable
    default List<String> getQualifyingPackagesAsUser(@NonNull Role role, @NonNull UserHandle user,
                                                     @NonNull Context context) {
        return null;
    }

    /**
     * @see Role#grantAsUser(String, boolean, boolean, UserHandle, Context)
     */
    default void grantAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {}

    /**
     * @see Role#revokeAsUser(String, boolean, boolean, UserHandle, Context)
     */
    default void revokeAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {}

    /**
     * @see Role#onHolderSelectedAsUser(String, UserHandle, Context)
     */
    default void onHolderSelectedAsUser(@NonNull Role role, @NonNull String packageName,
            @NonNull UserHandle user, @NonNull Context context) {}

    /**
     * @see Role#onHolderChangedAsUser(String, UserHandle, Context)
     */
    default void onHolderChangedAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {}

    /**
     * Check whether this role should be visible to user.
     *
     * @param role the role to check for
     * @param user the user to check for
     * @param context the `Context` to retrieve system services
     *
     * @return whether this role should be visible to user
     */
    default boolean isVisibleAsUser(@NonNull Role role, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }

    /**
     * Check whether a qualifying application should be visible to user.
     *
     * @param role the role to check for
     * @param applicationInfo the {@link ApplicationInfo} for the application
     * @param user the user for the application
     * @param context the {@code Context} to retrieve system services
     *
     * @return whether the qualifying application should be visible to user
     */
    default boolean isApplicationVisibleAsUser(@NonNull Role role,
            @NonNull ApplicationInfo applicationInfo, @NonNull UserHandle user,
            @NonNull Context context) {
        return true;
    }
}
