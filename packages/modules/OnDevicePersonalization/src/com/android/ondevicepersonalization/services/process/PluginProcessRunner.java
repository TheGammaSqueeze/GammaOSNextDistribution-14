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

package com.android.ondevicepersonalization.services.process;

import android.adservices.ondevicepersonalization.Constants;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.content.ComponentName;
import android.content.Context;
import android.os.Bundle;


import androidx.concurrent.futures.CallbackToFutureAdapter;

import com.android.ondevicepersonalization.internal.util.LoggerFactory;
import com.android.ondevicepersonalization.libraries.plugin.FailureType;
import com.android.ondevicepersonalization.libraries.plugin.PluginCallback;
import com.android.ondevicepersonalization.libraries.plugin.PluginController;
import com.android.ondevicepersonalization.libraries.plugin.PluginInfo;
import com.android.ondevicepersonalization.libraries.plugin.PluginManager;
import com.android.ondevicepersonalization.libraries.plugin.impl.PluginManagerImpl;
import com.android.ondevicepersonalization.services.OdpServiceException;
import com.android.ondevicepersonalization.services.OnDevicePersonalizationApplication;
import com.android.ondevicepersonalization.services.util.Clock;
import com.android.ondevicepersonalization.services.util.MonotonicClock;

import com.google.common.collect.ImmutableList;
import com.google.common.util.concurrent.Futures;
import com.google.common.util.concurrent.ListenableFuture;

import java.util.Objects;

/** Utilities to support loading and executing plugins. */
public class PluginProcessRunner implements ProcessRunner {
    private static final LoggerFactory.Logger sLogger = LoggerFactory.getLogger();
    private static final String TAG = "ProcessUtils";
    private static final String ENTRY_POINT_CLASS =
            "com.android.ondevicepersonalization.services.process.OnDevicePersonalizationPlugin";

    public static final String PARAM_CLASS_NAME_KEY = "param.classname";
    public static final String PARAM_OPERATION_KEY = "param.operation";
    public static final String PARAM_SERVICE_INPUT = "param.service_input";

    @NonNull private final Context mApplicationContext;

    private static volatile PluginManager sPluginManager;

    static class Injector {
        Clock getClock() {
            return MonotonicClock.getInstance();
        }
    }

    private final Injector mInjector;

    /** Creates a ProcessRunner. */
    PluginProcessRunner(
            @NonNull Context applicationContext,
            @NonNull Injector injector) {
        mApplicationContext = Objects.requireNonNull(applicationContext);
        mInjector = Objects.requireNonNull(injector);
    }


    private static class PluginProcessRunnerLazyInstanceHolder {
        static final PluginProcessRunner LAZY_INSTANCE =
                new PluginProcessRunner(
                        OnDevicePersonalizationApplication.getAppContext(),
                        new Injector());
    }

    /** Returns the global ProcessRunner */
    @NonNull public static PluginProcessRunner getInstance() {
        return PluginProcessRunnerLazyInstanceHolder.LAZY_INSTANCE;
    }

    /** Loads a service in an isolated process */
    @Override @NonNull public ListenableFuture<IsolatedServiceInfo> loadIsolatedService(
            @NonNull String taskName, @NonNull ComponentName componentName) {
        try {
            String packageName = Objects.requireNonNull(componentName.getPackageName());
            sLogger.d(TAG + ": loadIsolatedService: " + packageName);
            return loadPlugin(
                    mInjector.getClock().elapsedRealtime(),
                    componentName,
                    createPluginController(
                        createPluginId(packageName, taskName),
                        getPluginManager(mApplicationContext),
                        packageName));
        } catch (Exception e) {
            return Futures.immediateFailedFuture(e);
        }
    }

    /** Executes a service loaded in an isolated process */
    @Override @NonNull public ListenableFuture<Bundle> runIsolatedService(
            @NonNull IsolatedServiceInfo isolatedProcessInfo,
            int operationCode,
            @NonNull Bundle serviceParams) {
        sLogger.d(TAG + ": runIsolatedService: " + isolatedProcessInfo.getComponentName()
                + " op: " + operationCode);
        Bundle pluginParams = new Bundle();
        pluginParams.putString(
                PARAM_CLASS_NAME_KEY,
                isolatedProcessInfo.getComponentName().getClassName());
        pluginParams.putInt(PARAM_OPERATION_KEY, operationCode);
        pluginParams.putParcelable(PARAM_SERVICE_INPUT, serviceParams);
        return executePlugin(isolatedProcessInfo.getPluginController(), pluginParams);
    }

    /** Unloads a service loaded in an isolated process */
    @Override @NonNull public ListenableFuture<Void> unloadIsolatedService(
            @NonNull IsolatedServiceInfo isolatedServiceInfo) {
        return unloadPlugin(isolatedServiceInfo.getPluginController());
    }

    @NonNull
    static PluginManager getPluginManager(@NonNull Context applicationContext) {
        if (sPluginManager == null) {
            synchronized (PluginProcessRunner.class) {
                if (sPluginManager == null) {
                    sPluginManager = new PluginManagerImpl(applicationContext);
                }
            }
        }
        return sPluginManager;
    }

    @NonNull static PluginController createPluginController(
            String taskName, @NonNull PluginManager pluginManager, @Nullable String apkName)
            throws Exception {
        PluginInfo info = PluginInfo.createJvmInfo(
                taskName, getArchiveList(apkName), ENTRY_POINT_CLASS);
        return Objects.requireNonNull(pluginManager.createPluginController(info));
    }

    @NonNull static ListenableFuture<IsolatedServiceInfo> loadPlugin(
            long startTimeMillis,
            @NonNull ComponentName componentName,
            @NonNull PluginController pluginController) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    sLogger.d(TAG + ": loadPlugin");
                    pluginController.load(new PluginCallback() {
                        @Override public void onSuccess(Bundle bundle) {
                            try {
                                completer.set(
                                        new IsolatedServiceInfo(
                                            startTimeMillis, componentName,
                                            pluginController, /* isolatedServiceBinder= */ null));
                            } catch (OdpServiceException e) {
                                completer.setException(e);
                            }

                        }
                        @Override public void onFailure(FailureType failure) {
                            completer.setException(new OdpServiceException(
                                    Constants.STATUS_INTERNAL_ERROR,
                                    String.format("loadPlugin failed. %s", failure.toString())));
                        }
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "loadPlugin";
            }
        );
    }

    @NonNull static ListenableFuture<Bundle> executePlugin(
            @NonNull PluginController pluginController, @NonNull Bundle pluginParams) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    sLogger.d(TAG + ": executePlugin");
                    pluginController.execute(pluginParams, new PluginCallback() {
                        @Override public void onSuccess(Bundle bundle) {
                            completer.set(bundle);
                        }
                        @Override public void onFailure(FailureType failure) {
                            completer.setException(new OdpServiceException(
                                    Constants.STATUS_SERVICE_FAILED,
                                    String.format("executePlugin failed: %s", failure.toString())));
                        }
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "executePlugin";
            }
        );
    }

    @NonNull static ListenableFuture<Void> unloadPlugin(
            @NonNull PluginController pluginController) {
        return CallbackToFutureAdapter.getFuture(
            completer -> {
                try {
                    sLogger.d(TAG + ": unloadPlugin");
                    pluginController.unload(new PluginCallback() {
                        @Override public void onSuccess(Bundle bundle) {
                            completer.set(null);
                        }
                        @Override public void onFailure(FailureType failure) {
                            completer.setException(new OdpServiceException(
                                    Constants.STATUS_INTERNAL_ERROR,
                                    String.format("executePlugin failed: %s", failure.toString())));
                        }
                    });
                } catch (Exception e) {
                    completer.setException(e);
                }
                return "executePlugin";
            }
        );
    }

    @NonNull static ImmutableList<PluginInfo.ArchiveInfo> getArchiveList(
            @Nullable String apkName) {
        if (apkName == null) {
            return ImmutableList.of();
        }
        ImmutableList.Builder<PluginInfo.ArchiveInfo> archiveInfoBuilder = ImmutableList.builder();
        archiveInfoBuilder.add(
                PluginInfo.ArchiveInfo.builder().setPackageName(apkName).build());
        return archiveInfoBuilder.build();
    }

    static String createPluginId(String vendorPackageName, String taskName) {
        // TODO(b/249345663) Perform any validation needed on the input.
        return vendorPackageName + "-" + taskName;
    }
}
