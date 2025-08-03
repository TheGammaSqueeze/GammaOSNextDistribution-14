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
import android.annotation.UserIdInt;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.ApplicationInfo;
import android.content.pm.IPackageDataObserver;
import android.content.pm.IPackageMoveObserver;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.os.Bundle;
import android.os.Handler;
import android.os.RemoteException;
import android.os.UserHandle;

import java.util.List;

public class PackageManagerCompat {
    public static final int DELETE_SUCCEEDED = PackageManager.DELETE_SUCCEEDED;
    /**
     * Error code that is passed to the {@link IPackageMoveObserver} when the
     * package hasn't been successfully moved by the system because of
     * insufficient memory on specified media.
     */
    public static final int MOVE_FAILED_INSUFFICIENT_STORAGE =
            PackageManager.MOVE_FAILED_INSUFFICIENT_STORAGE;

    /**
     * Error code that is passed to the {@link IPackageMoveObserver} if the
     * specified package doesn't exist.
     */
    public static final int MOVE_FAILED_DOESNT_EXIST =
            PackageManager.MOVE_FAILED_DOESNT_EXIST;

    /**
     * Error code that is passed to the {@link IPackageMoveObserver} if the
     * specified package cannot be moved since it's a system package.
     */
    public static final int MOVE_FAILED_SYSTEM_PACKAGE =
            PackageManager.MOVE_FAILED_SYSTEM_PACKAGE;

    /**
     * Error code that is passed to the {@link IPackageMoveObserver} if the
     * specified package cannot be moved to the specified location.
     */
    public static final int MOVE_FAILED_INVALID_LOCATION =
            PackageManager.MOVE_FAILED_INVALID_LOCATION;

    /**
     * Error code that is passed to the {@link IPackageMoveObserver} if the
     * specified package cannot be moved to the specified location.
     */
    public static final int MOVE_FAILED_INTERNAL_ERROR =
            PackageManager.MOVE_FAILED_INTERNAL_ERROR;

    /**
     * Error code that is passed to the {@link IPackageMoveObserver} if the
     * specified package cannot be moved since it contains a device admin.
     */
    public static final int MOVE_FAILED_DEVICE_ADMIN =
            PackageManager.MOVE_FAILED_DEVICE_ADMIN;

    public static final int MOVE_SUCCEEDED =
            PackageManager.MOVE_SUCCEEDED;

    private final PackageManager mManager;

    public PackageManagerCompat(PackageManager manager) {
        this.mManager = manager;
    }

    public int movePackage(@NonNull String packageName, @NonNull VolumeInfoCompat vol) {
        return mManager.movePackage(packageName, vol.mVolumeInfo);
    }

    public int movePrimaryStorage(@NonNull VolumeInfoCompat vol) {
        return this.mManager.movePrimaryStorage(vol.mVolumeInfo);
    }

    public @NonNull
    List<VolumeInfoCompat> getPrimaryStorageCandidateVolumes() {
        return VolumeInfoCompat.convert(this.mManager.getPrimaryStorageCandidateVolumes());
    }

    public ApplicationInfo getApplicationInfoAsUser(@NonNull String packageName, int flags,
            @UserIdInt int userId) throws PackageManager.NameNotFoundException {
        return this.mManager.getApplicationInfoAsUser(packageName, flags, userId);
    }

    public int getPackageUidAsUser(@NonNull String packageName, @UserIdInt int userId)
            throws PackageManager.NameNotFoundException {
        return mManager.getPackageUidAsUser(packageName, userId);
    }

    public interface PackageDataObserver {
        void onRemoveCompleted(String s, boolean b);
    }

    public void deleteApplicationCacheFiles(
            @NonNull String packageName, @Nullable PackageDataObserver observer) {
        mManager.deleteApplicationCacheFiles(packageName, new IPackageDataObserver.Stub() {
            @Override
            public void onRemoveCompleted(String s, boolean b) throws RemoteException {
                observer.onRemoveCompleted(s, b);
            }
        });
    }

    public int getPackageUidAsUser(@NonNull String packageName,
            @NonNull PackageManager.PackageInfoFlags flags, @UserIdInt int userId)
            throws PackageManager.NameNotFoundException {
        return mManager.getPackageUidAsUser(packageName, flags, userId);
    }

    public @NonNull
    String getServicesSystemSharedLibraryPackageName() {
        return mManager.getServicesSystemSharedLibraryPackageName();
    }

    public String getPermissionControllerPackageName() {
        return mManager.getPermissionControllerPackageName();
    }

    public List<VolumeInfoCompat> getPackageCandidateVolumes(@NonNull ApplicationInfo app) {
        return VolumeInfoCompat.convert(mManager.getPackageCandidateVolumes(app));
    }

    public String getSharedSystemSharedLibraryPackageName() {
        return mManager.getSharedSystemSharedLibraryPackageName();
    }

    public List<ResolveInfo> queryIntentServicesAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId) {
        return mManager.queryIntentServicesAsUser(intent, flags, userId);
    }

    public List<ResolveInfo> queryBroadcastReceiversAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId) {
        return mManager.queryBroadcastReceiversAsUser(intent, flags, userId);
    }

    public List<ResolveInfo> queryIntentActivitiesAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId) {
        return mManager.queryIntentActivitiesAsUser(intent, flags, userId);
    }

    public @Nullable
    VolumeInfoCompat getPackageCurrentVolume(@NonNull ApplicationInfo app) {
        return new VolumeInfoCompat(mManager.getPackageCurrentVolume(app));
    }

    public ResolveInfo resolveActivityAsUser(@NonNull Intent intent,
            @NonNull PackageManager.ResolveInfoFlags flags, @UserIdInt int userId) {
        return this.mManager.resolveActivityAsUser(intent, flags, userId);
    }

    public ResolveInfo resolveActivityAsUser(
            @NonNull Intent intent, int flags, @UserIdInt int userId) {
        return this.mManager.resolveActivityAsUser(intent, flags, userId);
    }

    public ComponentName getHomeActivities(@NonNull List<ResolveInfo> outActivities) {
        return mManager.getHomeActivities(outActivities);
    }

    public VolumeInfoCompat getPrimaryStorageCurrentVolume() {
        return new VolumeInfoCompat(this.mManager.getPrimaryStorageCurrentVolume());
    }

    public int getInstallReason(@NonNull String packageName, @NonNull UserHandle user) {
        return this.mManager.getInstallReason(packageName, user);
    }

    public List<ApplicationInfo> getInstalledApplicationsAsUser(int flags, @UserIdInt int userId) {
        return this.mManager.getInstalledApplicationsAsUser(flags, userId);
    }


    public ProviderInfo resolveContentProviderAsUser(
            @NonNull String providerName, int flags, @UserIdInt int userId) {
        return mManager.resolveContentProviderAsUser(providerName, flags, userId);
    }

    public abstract static class MoveCallback {
        public void onCreated(int moveId, Bundle extras) {
        }

        public abstract void onStatusChanged(int moveId, int status, long estMillis);
    }

    private PackageManager.MoveCallback mMoveCallback;

    public void registerMoveCallback(
            @NonNull MoveCallback callback, @NonNull Handler handler) {
        mMoveCallback = new PackageManager.MoveCallback() {
            @Override
            public void onStatusChanged(int moveId, int status, long estMillis) {
                callback.onStatusChanged(moveId, status, estMillis);
            }
        };
        mManager.registerMoveCallback(mMoveCallback, handler);
    }

    public void unregisterMoveCallback(@NonNull MoveCallback callback) {
        if (mMoveCallback != null) {
            mManager.unregisterMoveCallback(mMoveCallback);
        }
    }

    public static boolean isMoveStatusFinished(int status) {
        return PackageManager.isMoveStatusFinished(status);
    }
}
