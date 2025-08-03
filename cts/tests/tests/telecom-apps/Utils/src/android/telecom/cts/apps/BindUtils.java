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

package android.telecom.cts.apps;

import static org.junit.Assert.fail;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.IBinder;
import android.util.Log;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

public class BindUtils {
    private static final String TAG = BindUtils.class.getSimpleName();
    private static final Map<TelecomTestApp, TelecomAppServiceConnection> sTelecomAppToService =
            new HashMap<>();

    private static class TelecomAppServiceConnection implements ServiceConnection {
        private static final String TAG = TelecomAppServiceConnection.class.getSimpleName();
        private final CompletableFuture<IAppControl> mFuture;

        TelecomAppServiceConnection(CompletableFuture<IAppControl> future) {
            mFuture = future;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            Log.i(TAG, String.format("onServiceConnected: ComponentName=[%s]", name));
            mFuture.complete(IAppControl.Stub.asInterface(service));
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            Log.i(TAG, String.format("onServiceDisconnected: ComponentName=[%s]", name));
            mFuture.complete(null);
        }
    }

    public AppControlWrapper bindToApplication(Context context, TelecomTestApp applicationName)
            throws Exception {
        final AppControlWrapper appControl;
        IAppControl binder = waitOnBindForApplication(context, applicationName);
        appControl = new AppControlWrapper(binder, applicationName);
        WaitUntil.waitUntilConditionIsTrueOrTimeout(new Condition() {
                    @Override
                    public Object expected() {
                        return true;
                    }

                    @Override
                    public Object actual() {
                        return appControl.isBound();
                    }
                },
                WaitUntil.DEFAULT_TIMEOUT_MS,
                "Timed out waiting for isBound to return <TRUE> for [" + applicationName + "]");
        return appControl;
    }


    public void unbindFromApplication(Context context, AppControlWrapper appControl) {
        TelecomTestApp name = appControl.getTelecomApps();
        if (!sTelecomAppToService.containsKey(name)) {
            fail(String.format("cannot find the service binder for application=[%s]", name));
        }
        TelecomAppServiceConnection serviceConnection = sTelecomAppToService.get(name);
        context.unbindService(serviceConnection);
        try {
            WaitUntil.waitUntilConditionIsTrueOrTimeout(new Condition() {
                    @Override
                    public Object expected() {
                        return false;
                    }

                    @Override
                    public Object actual() {
                        return appControl.isBound();
                    }
                    },
                    WaitUntil.DEFAULT_TIMEOUT_MS,
                    "Timed out waiting for isBound to return <FALSE>");
            sTelecomAppToService.remove(name);
        } catch (Exception e) {
            // Note: Avoid throwing the exception or else the test will fail
            Log.e(TAG, String.format("unbindFromApplication: app=[%s], e=[%s]", name, e));
        }
    }

    private Intent createBindIntentForApplication(TelecomTestApp application) throws Exception {
        Intent bindIntent = new Intent(getBindActionFromApplicationName(application));
        bindIntent.setPackage(getPackageNameFromApplicationName(application));
        return bindIntent;
    }

    private String getBindActionFromApplicationName(TelecomTestApp app) throws Exception {
        switch (app) {
            case TransactionalVoipAppMain, TransactionalVoipAppClone -> {
                return TelecomTestApp.T_CONTROL_INTERFACE_ACTION;
            }
            case ConnectionServiceVoipAppMain, ConnectionServiceVoipAppClone -> {
                return TelecomTestApp.VOIP_CS_CONTROL_INTERFACE_ACTION;
            }
            case ManagedConnectionServiceApp -> {
                return TelecomTestApp.CONTROL_INTERFACE_ACTION;
            }
        }
        throw new Exception(
                String.format("%s doesn't have a <CONTROL_INTERFACE> mapping." + app));
    }

    private String getPackageNameFromApplicationName(TelecomTestApp app) throws Exception {
        switch (app) {
            case TransactionalVoipAppMain -> {
                return TelecomTestApp.TRANSACTIONAL_PACKAGE_NAME;
            }
            case TransactionalVoipAppClone -> {
                return TelecomTestApp.TRANSACTIONAL_CLONE_PACKAGE_NAME;
            }
            case ConnectionServiceVoipAppMain -> {
                return TelecomTestApp.SELF_MANAGED_CS_MAIN_PACKAGE_NAME;
            }
            case ConnectionServiceVoipAppClone -> {
                return TelecomTestApp.SELF_MANAGED_CS_CLONE_PACKAGE_NAME;
            }
            case ManagedConnectionServiceApp -> {
                return TelecomTestApp.MANAGED_PACKAGE_NAME;
            }
        }
        throw new Exception(
                String.format("%s doesn't have a <PACKAGE_NAME> mapping.", app));
    }

    private IAppControl waitOnBindForApplication(Context context, TelecomTestApp application)
            throws Exception {
        CompletableFuture<IAppControl> future = new CompletableFuture<>();

        final TelecomAppServiceConnection serviceConnection = new TelecomAppServiceConnection(future);

        boolean success = context.bindService(createBindIntentForApplication(application),
                serviceConnection, Context.BIND_AUTO_CREATE);

        if (!success) {
            throw new Exception("Failed to get control interface -- bind error");
        }
        sTelecomAppToService.put(application, serviceConnection);
        return future.get(5000, TimeUnit.MILLISECONDS);
    }
}
