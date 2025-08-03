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
import android.app.role.RoleControllerService;
import android.app.role.RoleManager;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.RemoteCallback;
import android.os.UserHandle;

import androidx.annotation.NonNull;

import com.android.role.controller.service.RoleControllerServiceImpl;

import java.util.List;
import java.util.concurrent.Executor;
import java.util.function.Consumer;

public class LocalRoleController implements RoleController {

    @NonNull
    private final RoleControllerServiceImpl mService;
    @NonNull
    private final HandlerThread mWorkerThread;
    @NonNull
    private final Handler mWorkerHandler;

    public LocalRoleController(@NonNull UserHandle user, @NonNull Context context) {
        mService = new RoleControllerServiceImpl(user, context);
        mWorkerThread = new HandlerThread(RoleControllerService.class.getSimpleName());
        mWorkerThread.start();
        mWorkerHandler = new Handler(mWorkerThread.getLooper());
    }

    @Override
    public void grantDefaultRoles(@NonNull Executor executor, @NonNull Consumer<Boolean> callback) {
        mWorkerHandler.post(() -> {
            boolean successful = mService.onGrantDefaultRoles();
            executor.execute(() -> callback.accept(successful));
        });
    }

    @Override
    public void onAddRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mWorkerHandler.post(() -> {
            boolean successful = mService.onAddRoleHolder(roleName, packageName, flags);
            callback.sendResult(successful ? Bundle.EMPTY : null);
        });
    }

    @Override
    public void onRemoveRoleHolder(@NonNull String roleName, @NonNull String packageName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mWorkerHandler.post(() -> {
            boolean successful = mService.onRemoveRoleHolder(roleName, packageName, flags);
            callback.sendResult(successful ? Bundle.EMPTY : null);
        });
    }

    @Override
    public void onClearRoleHolders(@NonNull String roleName,
            @RoleManager.ManageHoldersFlags int flags, @NonNull RemoteCallback callback) {
        mWorkerHandler.post(() -> {
            boolean successful = mService.onClearRoleHolders(roleName, flags);
            callback.sendResult(successful ? Bundle.EMPTY : null);
        });
    }

    @Override
    public boolean isRoleVisible(@NonNull String roleName) {
        return mService.onIsRoleVisible(roleName);
    }

    @Override
    public boolean isApplicationVisibleForRole(@NonNull String roleName,
            @NonNull String packageName) {
        return mService.onIsApplicationVisibleForRole(roleName, packageName);
    }

    @Override
    public void getLegacyFallbackDisabledRoles(@NonNull @CallbackExecutor Executor executor,
            @NonNull Consumer<List<String>> callback) {
        throw new UnsupportedOperationException();
    }
}
