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

package com.android.car.testdpc.remotedpm;

import android.app.admin.DevicePolicyManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.testdpc.DpcReceiver;

import com.google.errorprone.annotations.FormatMethod;

/**
 {@code DevicePolicyManagerInterface} implementation that executes the device policies in another
 user, using a custom binder service for IPC.

 <p>In a "real world" app, it would be used by the device owner user running on background to apply
 policies on other users, while in a "test concept" app it could be used by the UI (running in the
 current user) to apply policies in the device owner user or for managed profile owner users to send
 commands to the device owner user.

 */
public final class RemoteDevicePolicyManager implements DevicePolicyManagerInterface {
    private static final String TAG = RemoteDevicePolicyManager.class.getSimpleName();
    private static final int THREAD_SLEEP_TIME = 100;
    private static final int THREAD_SLEEP_MAX = 5000;

    private final ComponentName mAdmin;
    private final Context mContext;
    private final DevicePolicyManager mDpm;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;

    private UserHandle mTargetUserHandle;

    private IRemoteDevicePolicyManager mRemoteDpm;

    private ServiceConnection mServiceConnection =
            new ServiceConnection() {
                @Override
                public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
                    Log.i(TAG, "onServiceConnected(ComponentName: " + componentName.toShortString()
                            + ", Binder: " + iBinder + ")");
                    mRemoteDpm = IRemoteDevicePolicyManager.Stub.asInterface(iBinder);
                }

                @Override
                public void onServiceDisconnected(ComponentName componentName) {
                    Log.i(TAG, "onServiceDisconnected(ComponentName: "
                            + componentName.toShortString() + ")");
                    mRemoteDpm = null;
                }
            };

    public RemoteDevicePolicyManager(ComponentName admin, Context context, UserHandle target) {
        mAdmin = admin;
        mContext = context;
        mDpm = context.getSystemService(DevicePolicyManager.class);

        mTargetUserHandle = target;

        mHandlerThread = new HandlerThread(TAG + "Thread");
        mHandlerThread.start();

        mHandler = new Handler(mHandlerThread.getLooper());

        mHandler.post(() -> handleWaitForBinding());
    }

    private void handleWaitForBinding() {
        int totalTime = 0;
        // User must be running and affiliated to move forward (exit loop)
        while (!isBound()) {
            try {
                handleBindRemoteDpm();
                Thread.sleep(THREAD_SLEEP_TIME);
                totalTime += THREAD_SLEEP_TIME;
                if (totalTime > THREAD_SLEEP_MAX) {
                    Log.i(TAG, "Reached max sleep time, no longer attempting to bind.");
                    break;
                }
                Log.i(TAG, "sleeping for " + totalTime + " seconds.");
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Log.e(TAG, "Thread sleep was interrupted.", e);
            }
        }
    }

    private void handleBindRemoteDpm() {
        try {
            mDpm.bindDeviceAdminServiceAsUser(
                    DpcReceiver.getComponentName(mContext),
                    new Intent(mContext, RemoteDevicePolicyManagerService.class),
                    mServiceConnection,
                    Context.BIND_AUTO_CREATE,
                    mTargetUserHandle);
        } catch (SecurityException | IllegalArgumentException e) {
            Log.e(TAG, "Cannot bind to user mTargetUserHandle: " + mTargetUserHandle, e);
        }
    }

    @Override
    public UserHandle getUser() {
        return mTargetUserHandle;
    }

    /**
     * Reboots the device
     *
     * <p>Only works when bound with device owner and the users are affiliated </p>
     */
    @Override
    public void reboot() {
        run(remotedpm -> remotedpm.reboot(mAdmin), "reboot(%s)", mAdmin);
    }

    @Override
    public void addUserRestriction(String key) {
        run(remotedpm -> remotedpm.addUserRestriction(mAdmin, key),
                "addUserRestriction(%s, %s)", mAdmin, key);
    }

    @Override
    public void startUserInBackground(UserHandle target) {
        // Device owner should make this call as itself... Do nothing if
        // it's a remote call
        run(remotedpm -> mDpm.startUserInBackground(mAdmin, target),
                "startUserInBackground(%s, %s)", mAdmin, target);
    }

    // TODO(b/245927102): Make this return a value
    @FormatMethod
    private void run(RemoteRunnable cmd, String cmdFormat, Object... cmdArgs) {
        mHandler.post(() -> {
            String cmdString = String.format(cmdFormat, cmdArgs);
            Log.i(TAG, "running " + cmdString + " on user " + mTargetUserHandle);

            try {
                cmd.run(mRemoteDpm);
            } catch (RuntimeException | RemoteException e) {
                Log.e(TAG, "Failure running " + cmdString, e);
            }
        });
    }

    public boolean isBound() {
        return mRemoteDpm != null;
    }
}
