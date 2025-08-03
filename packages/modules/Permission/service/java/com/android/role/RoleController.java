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

package com.android.role;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.app.role.RoleManager;
import android.os.RemoteCallback;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public interface RoleController {
    /**
     * @see android.app.role.RoleControllerManager#grantDefaultRoles
     */
    void grantDefaultRoles(@NonNull @CallbackExecutor Executor executor,
            @NonNull Consumer<Boolean> callback);

    /**
     * @see android.app.role.RoleControllerManager#onAddRoleHolder
     */
    void onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback);

    /**
     * @see android.app.role.RoleControllerManager#onRemoveRoleHolder
     */
    void onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback);

    /**
     * @see android.app.role.RoleControllerManager#onClearRoleHolders
     */
    void onClearRoleHolders(@NonNull String roleName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback);

    /**
     * @see android.app.role.RoleControllerManager#isRoleVisible
     */
    boolean isRoleVisible(@NonNull String roleName);

    /**
     * @see android.app.role.RoleControllerManager#isApplicationVisibleForRole
     */
    boolean isApplicationVisibleForRole(@NonNull String roleName, @NonNull String packageName);

    /**
     * @see android.app.role.RoleControllerManager#getLegacyFallbackDisabledRoles
     */
    void getLegacyFallbackDisabledRoles(@NonNull @CallbackExecutor Executor executor,
            @NonNull Consumer<List<String>> callback);
}
