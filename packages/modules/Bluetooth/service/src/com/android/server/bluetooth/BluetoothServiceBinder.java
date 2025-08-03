/*
 * Copyright 2023 The Android Open Source Project
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

package com.android.server.bluetooth;

import static android.Manifest.permission.BLUETOOTH_CONNECT;
import static android.Manifest.permission.BLUETOOTH_PRIVILEGED;
import static android.Manifest.permission.DUMP;
import static android.Manifest.permission.LOCAL_MAC_ADDRESS;
import static android.content.pm.PackageManager.PERMISSION_GRANTED;

import static com.android.server.bluetooth.BtPermissionUtils.checkConnectPermissionForDataDelivery;
import static com.android.server.bluetooth.BtPermissionUtils.getCallingAppId;
import static com.android.server.bluetooth.BtPermissionUtils.isCallerSystem;

import static java.util.Objects.requireNonNull;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.app.AppOpsManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.IBluetooth;
import android.bluetooth.IBluetoothManager;
import android.bluetooth.IBluetoothManagerCallback;
import android.content.AttributionSource;
import android.content.Context;
import android.os.IBinder;
import android.os.Looper;
import android.os.ParcelFileDescriptor;
import android.os.UserManager;
import android.permission.PermissionManager;

import java.io.FileDescriptor;
import java.io.PrintWriter;

class BluetoothServiceBinder extends IBluetoothManager.Stub {
    private static final String TAG = BluetoothServiceBinder.class.getSimpleName();

    private final BluetoothManagerService mBluetoothManagerService;
    private final Context mContext;
    private final UserManager mUserManager;
    private final AppOpsManager mAppOpsManager;
    private final PermissionManager mPermissionManager;
    private final BtPermissionUtils mPermissionUtils;
    private final Looper mLooper;

    BluetoothServiceBinder(
            BluetoothManagerService bms, Looper looper, Context ctx, UserManager userManager) {
        mBluetoothManagerService = bms;
        mLooper = looper;
        mContext = ctx;
        mUserManager = userManager;
        mAppOpsManager =
                requireNonNull(
                        ctx.getSystemService(AppOpsManager.class),
                        "AppOpsManager system service cannot be null");
        mPermissionManager =
                requireNonNull(
                        ctx.getSystemService(PermissionManager.class),
                        "PermissionManager system service cannot be null");
        mPermissionUtils = new BtPermissionUtils(ctx);
    }

    @Override
    @Nullable
    public IBluetooth registerAdapter(@NonNull IBluetoothManagerCallback callback) {
        requireNonNull(callback, "Callback cannot be null in registerAdapter");
        return mBluetoothManagerService.registerAdapter(callback);
    }

    @Override
    public void unregisterAdapter(@NonNull IBluetoothManagerCallback callback) {
        requireNonNull(callback, "Callback cannot be null in unregisterAdapter");
        mBluetoothManagerService.unregisterAdapter(callback);
    }

    @Override
    public boolean enable(@NonNull AttributionSource source) {
        requireNonNull(source, "AttributionSource cannot be null in enable");

        final String errorMsg =
                mPermissionUtils.callerCanToggle(
                        mContext,
                        source,
                        mUserManager,
                        mAppOpsManager,
                        mPermissionManager,
                        "enable",
                        true);
        if (!errorMsg.isEmpty()) {
            Log.d(TAG, "enable(): FAILED: " + errorMsg);
            return false;
        }

        return mBluetoothManagerService.enable(source.getPackageName());
    }

    @Override
    public boolean enableNoAutoConnect(AttributionSource source) {
        requireNonNull(source, "AttributionSource cannot be null in enableNoAutoConnect");

        final String errorMsg =
                mPermissionUtils.callerCanToggle(
                        mContext,
                        source,
                        mUserManager,
                        mAppOpsManager,
                        mPermissionManager,
                        "enableNoAutoConnect",
                        false);
        if (!errorMsg.isEmpty()) {
            Log.d(TAG, "enableNoAutoConnect(): FAILED: " + errorMsg);
            return false;
        }

        if (!mPermissionUtils.isCallerNfc(getCallingAppId())) {
            throw new SecurityException("No permission to enable Bluetooth quietly");
        }

        return mBluetoothManagerService.enableNoAutoConnect(source.getPackageName());
    }

    @Override
    public boolean disable(AttributionSource source, boolean persist) {
        requireNonNull(source, "AttributionSource cannot be null in disable");

        if (!persist) {
            mPermissionUtils.enforcePrivileged(mContext);
        }

        final String errorMsg =
                mPermissionUtils.callerCanToggle(
                        mContext,
                        source,
                        mUserManager,
                        mAppOpsManager,
                        mPermissionManager,
                        "disable",
                        true);
        if (!errorMsg.isEmpty()) {
            Log.d(TAG, "disable(): FAILED: " + errorMsg);
            return false;
        }

        return mBluetoothManagerService.disable(source.getPackageName(), persist);
    }

    @Override
    public int getState() {
        if (!isCallerSystem(getCallingAppId())
                && !mPermissionUtils.checkIfCallerIsForegroundUser(mUserManager)) {
            Log.w(TAG, "getState(): UNAUTHORIZED. Report OFF for non-active and non system user");
            return BluetoothAdapter.STATE_OFF;
        }

        return mBluetoothManagerService.getState();
    }

    @Override
    @RequiresPermission(allOf = {BLUETOOTH_CONNECT, LOCAL_MAC_ADDRESS})
    public String getAddress(AttributionSource source) {
        requireNonNull(source, "AttributionSource cannot be null in getAddress");

        if (!checkConnectPermissionForDataDelivery(
                mContext, mPermissionManager, source, "getAddress")) {
            return null;
        }

        if (!isCallerSystem(getCallingAppId())
                && !mPermissionUtils.checkIfCallerIsForegroundUser(mUserManager)) {
            Log.w(TAG, "getAddress(): Not allowed for non-active and non system user");
            return null;
        }

        if (mContext.checkCallingOrSelfPermission(LOCAL_MAC_ADDRESS) != PERMISSION_GRANTED) {
            // TODO(b/280890575): Throws a SecurityException instead
            Log.w(TAG, "getAddress(): Client does not have LOCAL_MAC_ADDRESS permission");
            return BluetoothAdapter.DEFAULT_MAC_ADDRESS;
        }

        return mBluetoothManagerService.getAddress(source);
    }

    @Override
    @RequiresPermission(BLUETOOTH_CONNECT)
    public String getName(AttributionSource source) {
        requireNonNull(source, "AttributionSource cannot be null in getName");

        if (!checkConnectPermissionForDataDelivery(
                mContext, mPermissionManager, source, "getName")) {
            return null;
        }

        if (!isCallerSystem(getCallingAppId())
                && !mPermissionUtils.checkIfCallerIsForegroundUser(mUserManager)) {
            Log.w(TAG, "getName(): not allowed for non-active and non system user");
            return null;
        }

        return mBluetoothManagerService.getName(source);
    }

    @Override
    @RequiresPermission(allOf = {BLUETOOTH_CONNECT, BLUETOOTH_PRIVILEGED})
    public boolean onFactoryReset(AttributionSource source) {
        requireNonNull(source, "AttributionSource cannot be null in onFactoryReset");

        mPermissionUtils.enforcePrivileged(mContext);

        if (!checkConnectPermissionForDataDelivery(
                mContext, mPermissionManager, source, "onFactoryReset")) {
            return false;
        }

        return mBluetoothManagerService.onFactoryReset(source);
    }

    @Override
    public boolean isBleScanAlwaysAvailable() {
        return mBluetoothManagerService.isBleScanAlwaysAvailable();
    }

    @Override
    @RequiresPermission(BLUETOOTH_CONNECT)
    public boolean enableBle(AttributionSource source, IBinder token) {
        requireNonNull(source, "AttributionSource cannot be null in enableBle");
        requireNonNull(token, "IBinder cannot be null in enableBle");

        final String errorMsg =
                mPermissionUtils.callerCanToggle(
                        mContext,
                        source,
                        mUserManager,
                        mAppOpsManager,
                        mPermissionManager,
                        "enableBle",
                        false);
        if (!errorMsg.isEmpty()) {
            Log.d(TAG, "enableBle(): FAILED: " + errorMsg);
            return false;
        }

        return mBluetoothManagerService.enableBle(source.getPackageName(), token);
    }

    @Override
    @RequiresPermission(BLUETOOTH_CONNECT)
    public boolean disableBle(AttributionSource source, IBinder token) {
        requireNonNull(source, "AttributionSource cannot be null in disableBle");
        requireNonNull(token, "IBinder cannot be null in disableBle");

        final String errorMsg =
                mPermissionUtils.callerCanToggle(
                        mContext,
                        source,
                        mUserManager,
                        mAppOpsManager,
                        mPermissionManager,
                        "disableBle",
                        false);
        if (!errorMsg.isEmpty()) {
            Log.d(TAG, "disableBle(): FAILED: " + errorMsg);
            return false;
        }

        return mBluetoothManagerService.disableBle(source, source.getPackageName(), token);
    }

    @Override
    public boolean isBleAppPresent() {
        return mBluetoothManagerService.isBleAppPresent();
    }

    @Override
    public boolean isHearingAidProfileSupported() {
        return mBluetoothManagerService.isHearingAidProfileSupported();
    }

    @Override
    @RequiresPermission(BLUETOOTH_PRIVILEGED)
    public int setBtHciSnoopLogMode(int mode) {
        mPermissionUtils.enforcePrivileged(mContext);

        return mBluetoothManagerService.setBtHciSnoopLogMode(mode);
    }

    @Override
    @RequiresPermission(BLUETOOTH_PRIVILEGED)
    public int getBtHciSnoopLogMode() {
        mPermissionUtils.enforcePrivileged(mContext);

        return mBluetoothManagerService.getBtHciSnoopLogMode();
    }

    @Override
    public int handleShellCommand(
            @NonNull ParcelFileDescriptor in,
            @NonNull ParcelFileDescriptor out,
            @NonNull ParcelFileDescriptor err,
            @NonNull String[] args) {
        return new BluetoothShellCommand(mBluetoothManagerService, mContext)
                .exec(
                        this,
                        in.getFileDescriptor(),
                        out.getFileDescriptor(),
                        err.getFileDescriptor(),
                        args);
    }

    @Override
    @RequiresPermission(BLUETOOTH_PRIVILEGED)
    public boolean isAutoOnSupported() {
        BtPermissionUtils.enforcePrivileged(mContext);
        return mBluetoothManagerService.isAutoOnSupported();
    }

    @Override
    @RequiresPermission(BLUETOOTH_PRIVILEGED)
    public boolean isAutoOnEnabled() {
        BtPermissionUtils.enforcePrivileged(mContext);
        return mBluetoothManagerService.isAutoOnEnabled();
    }

    @Override
    @RequiresPermission(BLUETOOTH_PRIVILEGED)
    public void setAutoOnEnabled(boolean status) {
        BtPermissionUtils.enforcePrivileged(mContext);
        mBluetoothManagerService.setAutoOnEnabled(status);
    }

    @Override
    @RequiresPermission(DUMP)
    public void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        if (mContext.checkCallingOrSelfPermission(DUMP) != PERMISSION_GRANTED) {
            // TODO(b/280890575): Throws SecurityException instead
            Log.w(TAG, "dump(): Client does not have DUMP permission");
            return;
        }

        mBluetoothManagerService.dump(fd, writer, args);
    }
}
