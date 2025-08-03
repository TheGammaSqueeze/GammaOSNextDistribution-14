/*
 * Copyright (C) 2022 The Android Open Source Project
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

package com.android.server.art;

import static com.android.server.art.OutputArtifacts.PermissionSettings;
import static com.android.server.art.OutputArtifacts.PermissionSettings.SeContext;
import static com.android.server.art.PrimaryDexUtils.DetailedPrimaryDexInfo;
import static com.android.server.art.Utils.Abi;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.Context;
import android.os.Build;
import android.os.CancellationSignal;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.pm.PackageStateModulesUtils;
import com.android.server.art.model.ArtFlags;
import com.android.server.art.model.Config;
import com.android.server.art.model.DexoptParams;
import com.android.server.art.model.DexoptResult;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.pm.pkg.AndroidPackage;
import com.android.server.pm.pkg.PackageState;

import dalvik.system.DexFile;

import com.google.auto.value.AutoValue;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** @hide */
@RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
public class PrimaryDexopter extends Dexopter<DetailedPrimaryDexInfo> {
    private static final String TAG = ArtManagerLocal.TAG;

    private final int mSharedGid;

    public PrimaryDexopter(@NonNull Context context, @NonNull Config config,
            @NonNull PackageState pkgState, @NonNull AndroidPackage pkg,
            @NonNull DexoptParams params, @NonNull CancellationSignal cancellationSignal) {
        this(new Injector(context, config), pkgState, pkg, params, cancellationSignal);
    }

    @VisibleForTesting
    public PrimaryDexopter(@NonNull Injector injector, @NonNull PackageState pkgState,
            @NonNull AndroidPackage pkg, @NonNull DexoptParams params,
            @NonNull CancellationSignal cancellationSignal) {
        super(injector, pkgState, pkg, params, cancellationSignal);

        mSharedGid = UserHandle.getSharedAppGid(pkgState.getAppId());
        if (mSharedGid < 0) {
            throw new IllegalStateException(
                    String.format("Unable to get shared gid for package '%s' (app ID: %d)",
                            pkgState.getPackageName(), pkgState.getAppId()));
        }
    }

    @Override
    protected boolean isInDalvikCache() throws RemoteException {
        return Utils.isInDalvikCache(mPkgState, mInjector.getArtd());
    }

    @Override
    @NonNull
    protected List<DetailedPrimaryDexInfo> getDexInfoList() {
        return PrimaryDexUtils.getDetailedDexInfo(mPkgState, mPkg);
    }

    @Override
    protected boolean isDexoptable(@NonNull DetailedPrimaryDexInfo dexInfo) {
        if (!dexInfo.hasCode()) {
            return false;
        }
        if ((mParams.getFlags() & ArtFlags.FLAG_FOR_SINGLE_SPLIT) != 0) {
            return Objects.equals(mParams.getSplitName(), dexInfo.splitName());
        }
        return true;
    }

    @Override
    protected boolean needsToBeShared(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return isSharedLibrary()
                || mInjector.getDexUseManager().isPrimaryDexUsedByOtherApps(
                        mPkgState.getPackageName(), dexInfo.dexPath());
    }

    @Override
    protected boolean isDexFilePublic(@NonNull DetailedPrimaryDexInfo dexInfo) {
        // The filesystem permission of a primary dex file always has the S_IROTH bit. In practice,
        // the accessibility is enforced by Application Sandbox, not filesystem permission.
        return true;
    }

    @Override
    @NonNull
    protected List<ProfilePath> getExternalProfiles(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return PrimaryDexUtils.getExternalProfiles(dexInfo);
    }

    @Override
    @NonNull
    protected PermissionSettings getPermissionSettings(
            @NonNull DetailedPrimaryDexInfo dexInfo, boolean canBePublic) {
        // The files and directories should belong to the system so that Package Manager can manage
        // them (e.g., move them around).
        // We don't need the "read" bit for "others" on the directories because others only need to
        // access the files in the directories, but they don't need to "ls" the directories.
        FsPermission dirFsPermission = AidlUtils.buildFsPermission(Process.SYSTEM_UID /* uid */,
                Process.SYSTEM_UID /* gid */, false /* isOtherReadable */,
                true /* isOtherExecutable */);
        FsPermission fileFsPermission = AidlUtils.buildFsPermission(
                Process.SYSTEM_UID /* uid */, mSharedGid /* gid */, canBePublic);
        // For primary dex, we can use the default SELinux context.
        SeContext seContext = null;
        return AidlUtils.buildPermissionSettings(dirFsPermission, fileFsPermission, seContext);
    }

    @Override
    @NonNull
    protected List<Abi> getAllAbis(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return Utils.getAllAbis(mPkgState);
    }

    @Override
    @NonNull
    protected ProfilePath buildRefProfilePath(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return PrimaryDexUtils.buildRefProfilePath(mPkgState, dexInfo);
    }

    @Override
    @NonNull
    protected OutputProfile buildOutputProfile(
            @NonNull DetailedPrimaryDexInfo dexInfo, boolean isPublic) {
        return PrimaryDexUtils.buildOutputProfile(
                mPkgState, dexInfo, Process.SYSTEM_UID, mSharedGid, isPublic);
    }

    @Override
    @NonNull
    protected List<ProfilePath> getCurProfiles(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return PrimaryDexUtils.getCurProfiles(mInjector.getUserManager(), mPkgState, dexInfo);
    }

    @Override
    @Nullable
    protected DexMetadataPath buildDmPath(@NonNull DetailedPrimaryDexInfo dexInfo) {
        return AidlUtils.buildDexMetadataPath(dexInfo.dexPath());
    }

    private boolean isSharedLibrary() {
        return PackageStateModulesUtils.isLoadableInOtherProcesses(mPkgState, true /* codeOnly */);
    }
}
