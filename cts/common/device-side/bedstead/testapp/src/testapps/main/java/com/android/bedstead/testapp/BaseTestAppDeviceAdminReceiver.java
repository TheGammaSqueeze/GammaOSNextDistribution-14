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

package com.android.bedstead.testapp;

import android.app.admin.DeviceAdminReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.PersistableBundle;
import android.os.UserHandle;

import com.android.eventlib.premade.EventLibDeviceAdminReceiver;

/**
 * Implementation of {@link DeviceAdminReceiver} which logs events in response to callbacks and
 * supports TestApp Features.
 */
public class BaseTestAppDeviceAdminReceiver extends EventLibDeviceAdminReceiver {

    @Override
    public void onEnabled(Context context, Intent intent) {
        super.onEnabled(context, intent);
    }

    @Override
    public CharSequence onDisableRequested(Context context, Intent intent) {
        return super.onDisableRequested(context, intent);
    }

    @Override
    public void onDisabled(Context context, Intent intent) {
        super.onDisabled(context, intent);
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent) {
        super.onPasswordChanged(context, intent);
    }

    @Override
    public void onPasswordChanged(Context context, Intent intent, UserHandle user) {
        super.onPasswordChanged(context, intent, user);
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent) {
        super.onPasswordFailed(context, intent);
    }

    @Override
    public void onPasswordFailed(Context context, Intent intent, UserHandle user) {
        super.onPasswordFailed(context, intent, user);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent) {
        super.onPasswordSucceeded(context, intent);
    }

    @Override
    public void onPasswordSucceeded(Context context, Intent intent, UserHandle user) {
        super.onPasswordSucceeded(context, intent, user);
    }

    @Override
    public void onPasswordExpiring(Context context, Intent intent) {
        super.onPasswordExpiring(context, intent);
    }

    @Override
    public void onPasswordExpiring(Context context, Intent intent, UserHandle user) {
        super.onPasswordExpiring(context, intent, user);
    }

    @Override
    public void onProfileProvisioningComplete(Context context, Intent intent) {
        super.onProfileProvisioningComplete(context, intent);
    }

    @Override
    public void onReadyForUserInitialization(Context context, Intent intent) {
        super.onReadyForUserInitialization(context, intent);
    }

    @Override
    public void onLockTaskModeEntering(Context context, Intent intent, String pkg) {
        super.onLockTaskModeEntering(context, intent, pkg);
    }

    @Override
    public void onLockTaskModeExiting(Context context, Intent intent) {
        super.onLockTaskModeExiting(context, intent);
    }

    @Override
    public String onChoosePrivateKeyAlias(Context context, Intent intent, int uid, Uri uri,
            String alias) {
        return super.onChoosePrivateKeyAlias(context, intent, uid, uri, alias);
    }

    @Override
    public void onSystemUpdatePending(Context context, Intent intent, long receivedTime) {
        super.onSystemUpdatePending(context, intent, receivedTime);
    }

    @Override
    public void onBugreportSharingDeclined(Context context, Intent intent) {
        super.onBugreportSharingDeclined(context, intent);
    }

    @Override
    public void onBugreportShared(Context context, Intent intent, String bugreportHash) {
        super.onBugreportShared(context, intent, bugreportHash);
    }

    @Override
    public void onBugreportFailed(Context context, Intent intent, int failureCode) {
        super.onBugreportFailed(context, intent, failureCode);
    }

    @Override
    public void onSecurityLogsAvailable(Context context, Intent intent) {
        super.onSecurityLogsAvailable(context, intent);
    }

    @Override
    public void onNetworkLogsAvailable(Context context, Intent intent, long batchToken,
            int networkLogsCount) {
        super.onNetworkLogsAvailable(context, intent, batchToken, networkLogsCount);
    }

    @Override
    public void onUserAdded(Context context, Intent intent, UserHandle addedUser) {
        super.onUserAdded(context, intent, addedUser);
    }

    @Override
    public void onUserRemoved(Context context, Intent intent, UserHandle removedUser) {
        super.onUserRemoved(context, intent, removedUser);
    }

    @Override
    public void onUserStarted(Context context, Intent intent, UserHandle startedUser) {
        super.onUserStarted(context, intent, startedUser);
    }

    @Override
    public void onUserStopped(Context context, Intent intent, UserHandle stoppedUser) {
        super.onUserStopped(context, intent, stoppedUser);
    }

    @Override
    public void onUserSwitched(Context context, Intent intent, UserHandle switchedUser) {
        super.onUserSwitched(context, intent, switchedUser);
    }

    @Override
    public void onTransferOwnershipComplete(Context context, PersistableBundle bundle) {
        super.onTransferOwnershipComplete(context, bundle);
    }

    @Override
    public void onTransferAffiliatedProfileOwnershipComplete(Context context, UserHandle user) {
        super.onTransferAffiliatedProfileOwnershipComplete(context, user);
    }

    @Override
    public void onOperationSafetyStateChanged(Context context, int reason, boolean isSafe) {
        super.onOperationSafetyStateChanged(context, reason, isSafe);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
    }
}
