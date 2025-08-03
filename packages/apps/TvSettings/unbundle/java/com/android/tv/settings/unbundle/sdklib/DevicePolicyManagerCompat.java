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

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteCallback;
import android.os.UserHandle;

import java.util.List;

public class DevicePolicyManagerCompat {
    public static final String EXTRA_BUGREPORT_NOTIFICATION_TYPE =
            DevicePolicyManager.EXTRA_BUGREPORT_NOTIFICATION_TYPE;

    public static final int NOTIFICATION_BUGREPORT_FINISHED_NOT_ACCEPTED =
            DevicePolicyManager.NOTIFICATION_BUGREPORT_FINISHED_NOT_ACCEPTED;

    public static final String ACTION_BUGREPORT_SHARING_ACCEPTED =
            DevicePolicyManager.ACTION_BUGREPORT_SHARING_ACCEPTED;

    /**
     * Action: Bugreport sharing with device owner has been declined by the user.
     */
    public static final String ACTION_BUGREPORT_SHARING_DECLINED =
            "com.android.server.action.REMOTE_BUGREPORT_SHARING_DECLINED";

    public static final String ACTION_DEVICE_POLICY_MANAGER_STATE_CHANGED =
            "android.app.action.DEVICE_POLICY_MANAGER_STATE_CHANGED";

    public static final int NOTIFICATION_BUGREPORT_STARTED =
            DevicePolicyManager.NOTIFICATION_BUGREPORT_STARTED;

    public static final int NOTIFICATION_BUGREPORT_ACCEPTED_NOT_FINISHED =
            DevicePolicyManager.NOTIFICATION_BUGREPORT_ACCEPTED_NOT_FINISHED;

    private final DevicePolicyManager mManager;

    public DevicePolicyManagerCompat(DevicePolicyManager manager) {
        this.mManager = manager;
    }

    public int getDeviceOwnerUserId() {
        return this.mManager.getDeviceOwnerUserId();
    }

    public static final String POLICY_SUSPEND_PACKAGES =
            DevicePolicyManager.POLICY_SUSPEND_PACKAGES;

    public boolean isCurrentInputMethodSetByOwner() {
        return this.mManager.isCurrentInputMethodSetByOwner();
    }

    public boolean hasUserSetupCompleted() {
        return this.mManager.hasUserSetupCompleted();
    }

    public List<String> getOwnerInstalledCaCerts(@NonNull UserHandle user) {
        return this.mManager.getOwnerInstalledCaCerts(user);
    }

    public @Nullable
    ComponentName getProfileOwnerOrDeviceOwnerSupervisionComponent(
            @NonNull UserHandle user) {
        return this.mManager.getProfileOwnerOrDeviceOwnerSupervisionComponent(user);
    }

    public @Nullable
    List<ComponentName> getActiveAdminsAsUser(int userId) {
        return this.mManager.getActiveAdminsAsUser(userId);
    }

    public int getMaximumFailedPasswordsForWipe(@Nullable ComponentName admin, int userHandle) {
        return this.mManager.getMaximumFailedPasswordsForWipe(admin, userHandle);
    }

    public ComponentName getDeviceOwnerComponentOnCallingUser() {
        return this.mManager.getDeviceOwnerComponentOnCallingUser();
    }

    public @Nullable
    ComponentName getProfileOwnerAsUser(final int userId) {
        return this.mManager.getProfileOwnerAsUser(userId);
    }

    public long getLastSecurityLogRetrievalTime() {
        return this.mManager.getLastSecurityLogRetrievalTime();
    }

    public long getLastBugReportRequestTime() {
        return this.mManager.getLastBugReportRequestTime();
    }

    public long getLastNetworkLogRetrievalTime() {
        return this.mManager.getLastNetworkLogRetrievalTime();
    }

    public @Nullable
    CharSequence getDeviceOwnerOrganizationName() {
        return this.mManager.getDeviceOwnerOrganizationName();
    }

    public @Nullable
    CharSequence getShortSupportMessageForUser(
            @NonNull ComponentName admin, int userHandle) {
        return this.mManager.getShortSupportMessageForUser(admin, userHandle);
    }

    public @NonNull
    DevicePolicyManager getParentProfileInstance(UserInfoCompat uInfo) {
        return this.mManager.getParentProfileInstance(uInfo.mUserInfo);
    }

    public boolean isRemovingAdmin(@NonNull ComponentName admin, int userId) {
        return this.mManager.isRemovingAdmin(admin, userId);
    }

    public void setActiveAdmin(@NonNull ComponentName policyReceiver, boolean refreshing) {
        this.mManager.setActiveAdmin(policyReceiver, refreshing);
    }

    public boolean setProfileOwner(@NonNull ComponentName admin, int userHandle)
            throws IllegalArgumentException {
        return this.mManager.setProfileOwner(admin, userHandle);
    }

    public void uninstallPackageWithActiveAdmins(String packageName) {
        this.mManager.uninstallPackageWithActiveAdmins(packageName);
    }

    public interface onResultListener {
        void onResult(Bundle result);
    }

    public void getRemoveWarning(@Nullable ComponentName admin, onResultListener listener,
            Handler handler) {
        this.mManager.getRemoveWarning(admin, new RemoteCallback(
                new RemoteCallback.OnResultListener() {
                    @Override
                    public void onResult(Bundle result) {
                        listener.onResult(result);
                    }
                }, handler));
    }

    public @Nullable
    CharSequence getLongSupportMessageForUser(
            @NonNull ComponentName admin, int userHandle) {
        return this.mManager.getLongSupportMessageForUser(admin, userHandle);
    }

    public boolean isAdminActiveAsUser(@NonNull ComponentName admin, int userId) {
        return this.mManager.isAdminActiveAsUser(admin, userId);
    }
}
