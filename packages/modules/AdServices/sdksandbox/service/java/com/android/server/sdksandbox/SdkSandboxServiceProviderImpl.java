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

package com.android.server.sdksandbox;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.RemoteException;
import android.os.UserHandle;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.server.LocalManagerRegistry;
import com.android.server.am.ActivityManagerLocal;

import java.io.PrintWriter;
import java.util.Objects;

import javax.annotation.concurrent.ThreadSafe;

/**
 * Implementation of {@link SdkSandboxServiceProvider}.
 *
 * @hide
 */
@ThreadSafe
class SdkSandboxServiceProviderImpl implements SdkSandboxServiceProvider {

    private static final String TAG = "SdkSandboxManager";

    private final Object mLock = new Object();

    private final Context mContext;
    private final ActivityManagerLocal mActivityManagerLocal;

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, SdkSandboxConnection> mAppSdkSandboxConnections =
            new ArrayMap<>();

    SdkSandboxServiceProviderImpl(Context context) {
        mContext = context;
        mActivityManagerLocal = LocalManagerRegistry.getManager(ActivityManagerLocal.class);
    }

    @Override
    @Nullable
    public void bindService(CallingInfo callingInfo, ServiceConnection serviceConnection) {
        synchronized (mLock) {
            SdkSandboxConnection sdkSandboxConnection = getSdkSandboxConnectionLocked(callingInfo);
            if (sdkSandboxConnection != null && sdkSandboxConnection.getStatus() != NON_EXISTENT) {
                // The sandbox is either already created or is in the process of being
                // created/restarted. Do not bind again. Note that later restarts can take a while,
                // since retries are done exponentially.
                Log.i(TAG, "SDK sandbox for " + callingInfo + " is already created");
                return;
            }

            Log.i(TAG, "Binding sdk sandbox for " + callingInfo);

            ComponentName componentName = getServiceComponentName();
            if (componentName == null) {
                Log.e(TAG, "Failed to find sdk sandbox service");
                notifyFailedBinding(serviceConnection);
                return;
            }
            Intent intent = new Intent().setComponent(componentName);

            sdkSandboxConnection = new SdkSandboxConnection(serviceConnection);

            String callingPackageName = callingInfo.getPackageName();
            String sandboxProcessName = null;
            try {
                sandboxProcessName = toSandboxProcessName(callingInfo);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(TAG, "bindService failed for: " + callingInfo.toString(), e);
                notifyFailedBinding(serviceConnection);
                return;
            }
            try {
                boolean bound;
                // For U+, we start the sandbox and then bind to it to prevent restarts. For T,
                // the sandbox service is directly bound to using BIND_AUTO_CREATE flag which brings
                // up the sandbox but also restarts it if the sandbox dies when bound.
                if (SdkLevel.isAtLeastU()) {
                    ComponentName name =
                            mActivityManagerLocal.startSdkSandboxService(
                                    intent,
                                    callingInfo.getUid(),
                                    callingPackageName,
                                    sandboxProcessName);
                    if (name == null) {
                        notifyFailedBinding(serviceConnection);
                        return;
                    }
                    bound =
                            mActivityManagerLocal.bindSdkSandboxService(
                                    intent,
                                    serviceConnection,
                                    callingInfo.getUid(),
                                    callingInfo.getAppProcessToken(),
                                    callingPackageName,
                                    sandboxProcessName,
                                    0);
                } else {
                    // Using BIND_AUTO_CREATE will create the sandbox process.
                    bound =
                            mActivityManagerLocal.bindSdkSandboxService(
                                    intent,
                                    serviceConnection,
                                    callingInfo.getUid(),
                                    callingPackageName,
                                    sandboxProcessName,
                                    Context.BIND_AUTO_CREATE);
                }
                if (!bound) {
                    mContext.unbindService(serviceConnection);
                    notifyFailedBinding(serviceConnection);
                    return;
                }
            } catch (RemoteException e) {
                notifyFailedBinding(serviceConnection);
                return;
            }
            mAppSdkSandboxConnections.put(callingInfo, sdkSandboxConnection);
            Log.i(TAG, "Sdk sandbox has been bound");
        }
    }

    // a way to notify manager that binding never happened
    private void notifyFailedBinding(ServiceConnection serviceConnection) {
        serviceConnection.onNullBinding(null);
    }

    @Override
    public void dump(PrintWriter writer) {
        synchronized (mLock) {
            if (mAppSdkSandboxConnections.size() == 0) {
                writer.println("mAppSdkSandboxConnections is empty");
            } else {
                writer.print("mAppSdkSandboxConnections size: ");
                writer.println(mAppSdkSandboxConnections.size());
                for (int i = 0; i < mAppSdkSandboxConnections.size(); i++) {
                    CallingInfo callingInfo = mAppSdkSandboxConnections.keyAt(i);
                    SdkSandboxConnection sdkSandboxConnection =
                            mAppSdkSandboxConnections.get(callingInfo);
                    writer.printf(
                            "Sdk sandbox for UID: %s, app package: %s, isConnected: %s Status: %d",
                            callingInfo.getUid(),
                            callingInfo.getPackageName(),
                            Objects.requireNonNull(sdkSandboxConnection).isConnected(),
                            sdkSandboxConnection.getStatus());
                    writer.println();
                }
            }
        }
    }

    @Override
    public void unbindService(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection sandbox = getSdkSandboxConnectionLocked(callingInfo);

            if (sandbox == null) {
                return;
            }

            if (sandbox.isBound) {
                try {
                    mContext.unbindService(sandbox.getServiceConnection());
                } catch (Exception e) {
                    // Sandbox has already unbound previously.
                }
                sandbox.onUnbind();
                Log.i(TAG, "Sdk sandbox for " + callingInfo + " has been unbound");
            }
        }
    }

    @Override
    public void stopSandboxService(CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        synchronized (mLock) {
            SdkSandboxConnection sandbox = getSdkSandboxConnectionLocked(callingInfo);

            if (!SdkLevel.isAtLeastU() || sandbox == null || sandbox.getStatus() == NON_EXISTENT) {
                return;
            }

            ComponentName componentName = getServiceComponentName();
            if (componentName == null) {
                Log.e(TAG, "Failed to find sdk sandbox service");
                return;
            }
            Intent intent = new Intent().setComponent(componentName);
            String callingPackageName = callingInfo.getPackageName();
            String sandboxProcessName = toSandboxProcessName(callingInfo);

            mActivityManagerLocal.stopSdkSandboxService(
                    intent, callingInfo.getUid(), callingPackageName, sandboxProcessName);
        }
    }

    @Override
    @Nullable
    public ISdkSandboxService getSdkSandboxServiceForApp(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection != null && connection.getStatus() == CREATED) {
                return connection.getSdkSandboxService();
            }
        }
        return null;
    }

    @Override
    public void onServiceConnected(CallingInfo callingInfo, @NonNull ISdkSandboxService service) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection != null) {
                connection.onServiceConnected(service);
            }
        }
    }

    @Override
    public void onServiceDisconnected(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection != null) {
                connection.onServiceDisconnected();
            }
        }
    }

    @Override
    public void onAppDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            mAppSdkSandboxConnections.remove(callingInfo);
        }
    }

    @Override
    public void onSandboxDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection != null) {
                connection.onSdkSandboxDeath();
            }
        }
    }

    @Override
    public boolean isSandboxBoundForApp(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection != null) {
                synchronized (connection.mLock) {
                    return connection.isBound;
                }
            }
            return false;
        }
    }

    @Override
    public int getSandboxStatusForApp(CallingInfo callingInfo) {
        synchronized (mLock) {
            SdkSandboxConnection connection = getSdkSandboxConnectionLocked(callingInfo);
            if (connection == null) {
                return NON_EXISTENT;
            } else {
                return connection.getStatus();
            }
        }
    }

    @Override
    @NonNull
    public String toSandboxProcessName(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        return getProcessName(callingInfo) + SANDBOX_PROCESS_NAME_SUFFIX;
    }

    @Override
    @NonNull
    public String toSandboxProcessNameForInstrumentation(@NonNull CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        return getProcessName(callingInfo) + SANDBOX_INSTR_PROCESS_NAME_SUFFIX;
    }

    @Nullable
    private ComponentName getServiceComponentName() {
        final Intent intent = new Intent(SdkSandboxManagerLocal.SERVICE_INTERFACE);
        intent.setPackage(mContext.getPackageManager().getSdkSandboxPackageName());

        final ResolveInfo resolveInfo = mContext.getPackageManager().resolveService(intent,
                PackageManager.GET_SERVICES | PackageManager.GET_META_DATA);
        if (resolveInfo == null) {
            Log.e(TAG, "Failed to find resolveInfo for sdk sandbox service");
            return null;
        }

        final ServiceInfo serviceInfo = resolveInfo.serviceInfo;
        if (serviceInfo == null) {
            Log.e(TAG, "Failed to find serviceInfo for sdk sandbox service");
            return null;
        }

        return new ComponentName(serviceInfo.packageName, serviceInfo.name);
    }

    @GuardedBy("mLock")
    @Nullable
    private SdkSandboxConnection getSdkSandboxConnectionLocked(CallingInfo callingInfo) {
        return mAppSdkSandboxConnections.get(callingInfo);
    }

    private String getProcessName(CallingInfo callingInfo)
            throws PackageManager.NameNotFoundException {
        UserHandle userHandle = UserHandle.getUserHandleForUid(callingInfo.getUid());
        return mContext.getPackageManager()
                .getApplicationInfoAsUser(callingInfo.getPackageName(), /*flags=*/ 0, userHandle)
                .processName;
    }

    // Represents the connection to an SDK sandbox service.
    static class SdkSandboxConnection {

        private final Object mLock = new Object();

        @GuardedBy("mLock")
        @SandboxStatus
        private int mStatus = CREATE_PENDING;

        // The connection used to bind and unbind from the SDK sandbox service.
        private final ServiceConnection mServiceConnection;

        // The binder returned by the SDK sandbox service on connection.
        @GuardedBy("mLock")
        @Nullable
        private ISdkSandboxService mSdkSandboxService = null;

        // Set to true when requested to bind to the SDK sandbox service. It is reset back to false
        // when unbinding the sandbox service.
        @GuardedBy("mLock")
        public boolean isBound = true;

        SdkSandboxConnection(ServiceConnection serviceConnection) {
            mServiceConnection = serviceConnection;
        }

        @SandboxStatus
        public int getStatus() {
            synchronized (mLock) {
                return mStatus;
            }
        }

        public void onUnbind() {
            synchronized (mLock) {
                isBound = false;
            }
        }

        public void onServiceConnected(ISdkSandboxService service) {
            synchronized (mLock) {
                mStatus = CREATED;
                mSdkSandboxService = service;
            }
        }

        public void onServiceDisconnected() {
            synchronized (mLock) {
                mSdkSandboxService = null;
            }
        }

        public void onSdkSandboxDeath() {
            synchronized (mLock) {
                // For U+, the sandbox does not restart after dying.
                if (SdkLevel.isAtLeastU()) {
                    mStatus = NON_EXISTENT;
                    return;
                }

                if (isBound) {
                    // If the sandbox was bound at the time of death, the system will automatically
                    // restart it.
                    mStatus = CREATE_PENDING;
                } else {
                    // If the sandbox was not bound at the time of death, the sandbox is dead for
                    // good.
                    mStatus = NON_EXISTENT;
                }
            }
        }

        @Nullable
        public ISdkSandboxService getSdkSandboxService() {
            synchronized (mLock) {
                return mSdkSandboxService;
            }
        }

        public ServiceConnection getServiceConnection() {
            return mServiceConnection;
        }

        boolean isConnected() {
            synchronized (mLock) {
                return mSdkSandboxService != null;
            }
        }
    }
}
