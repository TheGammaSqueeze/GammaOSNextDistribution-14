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
import android.app.role.RoleControllerManager;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.RemoteCallback;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.permission.util.ForegroundThread;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class RemoteRoleController implements RoleController {
    @NonNull
    private final RoleControllerManager mRoleControllerManager;

    public RemoteRoleController(@NonNull UserHandle user, @NonNull Context context) {
        Context userContext = context.createContextAsUser(user, 0);
        mRoleControllerManager =
                RoleControllerManager.createWithInitializedRemoteServiceComponentName(
                        ForegroundThread.getHandler(), userContext);
    }

    @Override
    public void grantDefaultRoles(@NonNull Executor executor, @NonNull Consumer<Boolean> callback) {
        mRoleControllerManager.grantDefaultRoles(executor, callback);
    }

    @Override
    public void onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mRoleControllerManager.onAddRoleHolder(roleName, packageName, flags, callback);
    }

    @Override
    public void onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mRoleControllerManager.onRemoveRoleHolder(roleName, packageName, flags, callback);
    }

    @Override
    public void onClearRoleHolders(@NonNull String roleName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mRoleControllerManager.onClearRoleHolders(roleName, flags, callback);
    }

    @Override
    public boolean isRoleVisible(@NonNull String roleName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public boolean isApplicationVisibleForRole(@NonNull String roleName,
            @NonNull String packageName) {
        throw new UnsupportedOperationException();
    }

    @Override
    public void getLegacyFallbackDisabledRoles(@NonNull @CallbackExecutor Executor executor,
            @NonNull Consumer<List<String>> callback) {
        mRoleControllerManager.getLegacyFallbackDisabledRoles(executor, callback);
    }
}
