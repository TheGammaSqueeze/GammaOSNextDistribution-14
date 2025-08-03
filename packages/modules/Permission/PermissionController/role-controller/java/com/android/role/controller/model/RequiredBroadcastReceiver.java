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
import android.content.Intent;
import android.content.pm.ComponentInfo;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.os.UserHandle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.android.role.controller.util.UserUtils;

import java.util.List;

/**
 * Specifies a required {@code BroadcastReceiver} for an application to qualify for a {@link Role}.
 */
public class RequiredBroadcastReceiver extends RequiredComponent {

    public RequiredBroadcastReceiver(@NonNull IntentFilterData intentFilterData,
            int minTargetSdkVersion, int flags, @Nullable String permission, int queryFlags,
            @NonNull List<RequiredMetaData> metaData) {
        super(intentFilterData, minTargetSdkVersion, flags, permission, queryFlags, metaData);
    }

    @NonNull
    @Override
    protected List<ResolveInfo> queryIntentComponentsAsUser(@NonNull Intent intent, int flags,
            @NonNull UserHandle user, @NonNull Context context) {
        Context userContext = UserUtils.getUserContext(context, user);
        PackageManager userPackageManager = userContext.getPackageManager();
        return userPackageManager.queryBroadcastReceivers(intent, flags);
    }

    @NonNull
    @Override
    protected ComponentInfo getComponentComponentInfo(@NonNull ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo;
    }

    @Override
    protected int getComponentFlags(@NonNull ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo.flags;
    }

    @Nullable
    @Override
    protected String getComponentPermission(@NonNull ResolveInfo resolveInfo) {
        return resolveInfo.activityInfo.permission;
    }
}
