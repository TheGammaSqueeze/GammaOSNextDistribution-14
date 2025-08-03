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

package com.android.rkpdapp.service;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.util.Log;

import com.android.rkpdapp.IGetRegistrationCallback;
import com.android.rkpdapp.IRegistration;
import com.android.rkpdapp.IRemoteProvisioning;
import com.android.rkpdapp.ThreadPool;
import com.android.rkpdapp.database.ProvisionedKeyDao;
import com.android.rkpdapp.database.RkpdDatabase;
import com.android.rkpdapp.interfaces.ServerInterface;
import com.android.rkpdapp.interfaces.ServiceManagerInterface;
import com.android.rkpdapp.interfaces.SystemInterface;
import com.android.rkpdapp.metrics.RkpdClientOperation;
import com.android.rkpdapp.provisioner.Provisioner;
import com.android.rkpdapp.utils.Settings;

/** Provides the implementation for IRemoteProvisioning.aidl */
public class RemoteProvisioningService extends Service {
    public static final String TAG = "com.android.rkpdapp";
    private static final boolean IS_ASYNC = false;
    private final IRemoteProvisioning.Stub mBinder = new RemoteProvisioningBinder();

    @Override
    public void onCreate() {
        super.onCreate();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    final class RemoteProvisioningBinder extends IRemoteProvisioning.Stub {
        @Override
        public void getRegistration(int callerUid, String irpcName,
                IGetRegistrationCallback callback) {
            final Context context = getApplicationContext();
            RkpdClientOperation metric = RkpdClientOperation.getRegistration(callerUid, irpcName);
            try (metric) {
                if (Settings.getUrl(context).isEmpty()) {
                    callback.onError("RKP is disabled. System configured with no default URL.");
                    metric.setResult(RkpdClientOperation.Result.RKP_UNSUPPORTED);
                    return;
                }

                // Check that only system process and self calls binding.
                if (Binder.getCallingUid() != Process.SYSTEM_UID
                        && Binder.getCallingUid() != Process.myUid()) {
                    callback.onError(
                            "Only system server and self are allowed to call RKP service.");
                    return;
                }

                SystemInterface systemInterface;
                try {
                    systemInterface = ServiceManagerInterface.getInstance(irpcName);
                } catch (IllegalArgumentException e) {
                    Log.e(TAG, "Error getting HAL '" + irpcName + "'", e);
                    callback.onError("Invalid HAL name: " + irpcName);
                    metric.setResult(RkpdClientOperation.Result.ERROR_INVALID_HAL);
                    return;
                }

                ProvisionedKeyDao dao = RkpdDatabase.getDatabase(context).provisionedKeyDao();
                Provisioner provisioner = new Provisioner(context, dao, IS_ASYNC);
                IRegistration.Stub registration = new RegistrationBinder(context, callerUid,
                        systemInterface, dao, new ServerInterface(context, IS_ASYNC), provisioner,
                        ThreadPool.EXECUTOR);
                metric.setResult(RkpdClientOperation.Result.SUCCESS);
                callback.onSuccess(registration);
            } catch (RemoteException e) {
                Log.e(TAG, "Error notifying callback binder", e);
                metric.setResult(RkpdClientOperation.Result.ERROR_INTERNAL);
                throw e.rethrowAsRuntimeException();
            }
        }

        @Override
        public void cancelGetRegistration(IGetRegistrationCallback callback) {
            // Not actually supported on this end of the transaction, because we always
            // complete, and there's no way to win the race.
            Log.i(TAG, "cancelGetRegistration");
        }
    }
}
