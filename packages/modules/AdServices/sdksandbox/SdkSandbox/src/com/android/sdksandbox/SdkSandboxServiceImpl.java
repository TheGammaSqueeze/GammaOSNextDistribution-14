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

package com.android.sdksandbox;

import android.annotation.NonNull;
import android.app.Service;
import android.app.sdksandbox.FileUtil;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LogUtil;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdkContext;
import android.app.sdksandbox.SdkSandboxLocalSingleton;
import android.app.sdksandbox.SharedPreferencesKey;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.sdkprovider.SdkSandboxActivityRegistry;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.SystemClock;
import android.text.TextUtils;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;

import androidx.annotation.RequiresApi;

import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.build.SdkLevel;

import dalvik.system.PathClassLoader;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Implementation of Sdk Sandbox Service. */
public class SdkSandboxServiceImpl extends Service {

    private static final String TAG = "SdkSandbox";

    private final Object mLock = new Object();
    // Mapping from sdk name to its holder
    @GuardedBy("mLock")
    private final Map<String, SandboxedSdkHolder> mHeldSdk = new ArrayMap<>();

    private volatile boolean mInitialized;
    private boolean mCustomizedSdkContextEnabled;
    private Injector mInjector;
    private ISdkSandboxService.Stub mBinder;

    static class Injector {

        private final Context mContext;

        Injector(Context context) {
            mContext = context;
        }

        int getCallingUid() {
            return Binder.getCallingUidOrThrow();
        }

        Context getContext() {
            return mContext;
        }

        long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        SdkSandboxActivityRegistry getSdkSandboxActivityRegistry() {
            return SdkSandboxActivityRegistry.getInstance();
        }
    }

    public SdkSandboxServiceImpl() {
    }

    @VisibleForTesting
    SdkSandboxServiceImpl(Injector injector) {
        mInjector = injector;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        // This prevents the sandbox from restarting. This should be kept in sync with the status
        // maintained in SdkSandboxServiceProviderImpl.SdkSandboxConnection.
        return START_NOT_STICKY;
    }

    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onCreate() {
        mBinder = new SdkSandboxServiceDelegate();
        mInjector = new Injector(getApplicationContext());
    }

    /**
     * Initializes the Sandbox.
     *
     * <p>Sandbox should be initialized before any SDKs are loaded. This method is idempotent.
     *
     * @param sdkToServiceCallback for initialization of {@link SdkSandboxLocalSingleton}
     */
    public void initialize(
            ISdkToServiceCallback sdkToServiceCallback, boolean isCustomizedSdkContextEnabled) {
        enforceCallerIsSystemServer();

        if (mInitialized) {
            Log.e(TAG, "Sandbox is already initialized");
            return;
        }

        mCustomizedSdkContextEnabled = isCustomizedSdkContextEnabled;

        SdkSandboxLocalSingleton.initInstance(sdkToServiceCallback.asBinder());

        cleanUpSyncedSharedPreferencesData();

        mInitialized = true;
        LogUtil.d(
                TAG,
                "Sandbox initialized. isCustomizedSdkContextEnabled: "
                        + isCustomizedSdkContextEnabled);
    }

    /** Computes the storage of the shared and SDK storage for an app */
    public void computeSdkStorage(
            List<String> sharedPaths, List<String> sdkPaths, IComputeSdkStorageCallback callback) {
        int sharedStorageKb = FileUtil.getStorageInKbForPaths(sharedPaths);
        int sdkStorageKb = FileUtil.getStorageInKbForPaths(sdkPaths);

        try {
            callback.onStorageInfoComputed(sharedStorageKb, sdkStorageKb);
        } catch (RemoteException e) {
            LogUtil.d(TAG, "Error while calling computeSdkStorage in sandbox: " + e.getMessage());
        }
    }

    /** Loads SDK. */
    public void loadSdk(
            String callingPackageName,
            ApplicationInfo applicationInfo,
            String sdkName,
            String sdkProviderClassName,
            ApplicationInfo customizedApplicationInfo,
            Bundle params,
            ILoadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        enforceCallerIsSystemServer();

        if (!mInitialized) {
            sendLoadError(
                    callback,
                    ILoadSdkInSandboxCallback.LOAD_SDK_INSTANTIATION_ERROR,
                    "Sandbox was not properly initialized",
                    sandboxLatencyInfo);
            return;
        }
        loadSdkInternal(
                callingPackageName,
                applicationInfo,
                sdkName,
                sdkProviderClassName,
                customizedApplicationInfo,
                params,
                callback,
                sandboxLatencyInfo);
    }

    /** Unloads SDK. */
    public void unloadSdk(
            String sdkName,
            IUnloadSdkInSandboxCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        enforceCallerIsSystemServer();

        sandboxLatencyInfo.setTimeSandboxCalledSdk(mInjector.elapsedRealtime());
        unloadSdkInternal(sdkName);
        sandboxLatencyInfo.setTimeSdkCallCompleted(mInjector.elapsedRealtime());

        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.elapsedRealtime());
        try {
            callback.onUnloadSdk(sandboxLatencyInfo);
        } catch (RemoteException ignore) {
            Log.e(TAG, "Could not send onUnloadSdk");
        }
    }

    /** Invoked when the client app changes foreground importance */
    public void notifySdkSandboxClientImportanceChange(boolean isForeground) {
        enforceCallerIsSystemServer();

        final long token = Binder.clearCallingIdentity();
        try {
            SdkSandboxLocalSingleton.getExistingInstance()
                    .notifySdkSandboxClientImportanceChange(isForeground);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    /** Syncs data from client. */
    public void syncDataFromClient(SharedPreferencesUpdate update) {
        enforceCallerIsSystemServer();

        LogUtil.d(TAG, "Syncing data from client");

        SharedPreferences pref = getClientSharedPreferences();
        SharedPreferences.Editor editor = pref.edit();
        final Bundle data = update.getData();
        for (SharedPreferencesKey keyInUpdate : update.getKeysInUpdate()) {
            updateSharedPreferences(editor, data, keyInUpdate);
        }
        editor.apply();
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SharedPreferences getClientSharedPreferences() {
        // TODO(b/248214708): We should retrieve synced data from a separate internal storage
        // directory.
        return mInjector
                .getContext()
                .getSharedPreferences(
                        SdkSandboxController.CLIENT_SHARED_PREFERENCES_NAME, Context.MODE_PRIVATE);
    }

    private void cleanUpSyncedSharedPreferencesData() {
        getClientSharedPreferences().edit().clear().apply();
    }

    private void updateSharedPreferences(
            SharedPreferences.Editor editor, Bundle data, SharedPreferencesKey keyInUpdate) {
        final String key = keyInUpdate.getName();

        if (!data.containsKey(key)) {
            // key was specified but bundle didn't have the key; meaning it has been removed.
            editor.remove(key);
            return;
        }

        final int type = keyInUpdate.getType();
        try {
            switch (type) {
                case SharedPreferencesKey.KEY_TYPE_STRING:
                    editor.putString(key, data.getString(key, ""));
                    break;
                case SharedPreferencesKey.KEY_TYPE_BOOLEAN:
                    editor.putBoolean(key, data.getBoolean(key, false));
                    break;
                case SharedPreferencesKey.KEY_TYPE_INTEGER:
                    editor.putInt(key, data.getInt(key, 0));
                    break;
                case SharedPreferencesKey.KEY_TYPE_FLOAT:
                    editor.putFloat(key, data.getFloat(key, 0.0f));
                    break;
                case SharedPreferencesKey.KEY_TYPE_LONG:
                    editor.putLong(key, data.getLong(key, 0L));
                    break;
                case SharedPreferencesKey.KEY_TYPE_STRING_SET:
                    final ArraySet<String> castedValue =
                            new ArraySet<>(data.getStringArrayList(key));
                    editor.putStringSet(key, castedValue);
                    break;
                default:
                    Log.e(
                            TAG,
                            "Unknown type found in default SharedPreferences for Key: "
                                    + key
                                    + " Type: "
                                    + type);
            }
        } catch (ClassCastException ignore) {
            editor.remove(key);
            // TODO(b/239403323): Once error reporting is supported, we should return error to the
            // user instead.
            Log.e(
                    TAG,
                    "Wrong type found in default SharedPreferences for Key: "
                            + key
                            + " Type: "
                            + type);
        }
    }

    @Override
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        synchronized (mLock) {
            // TODO(b/211575098): Use IndentingPrintWriter for better formatting
            if (mHeldSdk.isEmpty()) {
                writer.println("mHeldSdk is empty");
            } else {
                writer.print("mHeldSdk size: ");
                writer.println(mHeldSdk.size());
                for (SandboxedSdkHolder sandboxedSdkHolder : mHeldSdk.values()) {
                    sandboxedSdkHolder.dump(writer);
                    writer.println();
                }
            }
        }
    }

    // TODO(b/249760546): Write test for enforceCallerIsSystemServer
    private void enforceCallerIsSystemServer() {
        if (mInjector.getCallingUid() != Process.SYSTEM_UID) {
            throw new SecurityException(
                    "Only system_server is allowed to call this API, actual calling uid is "
                            + mInjector.getCallingUid());
        }
    }

    private void loadSdkInternal(
            @NonNull String callingPackageName,
            @NonNull ApplicationInfo applicationInfo,
            @NonNull String sdkName,
            @NonNull String sdkProviderClassName,
            @NonNull ApplicationInfo customizedApplicationInfo,
            @NonNull Bundle params,
            @NonNull ILoadSdkInSandboxCallback callback,
            @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
        synchronized (mLock) {
            if (mHeldSdk.containsKey(sdkName)) {
                sendLoadError(
                        callback,
                        ILoadSdkInSandboxCallback.LOAD_SDK_ALREADY_LOADED,
                        "Already loaded sdk for package " + applicationInfo.packageName,
                        sandboxLatencyInfo);
                return;
            }
        }

        Context baseContext;
        try {
            baseContext = createBaseContext(customizedApplicationInfo);
        } catch (PackageManager.NameNotFoundException e) {
            sendLoadError(
                    callback,
                    ILoadSdkInSandboxCallback.LOAD_SDK_INTERNAL_ERROR,
                    "Package name not found " + sdkName + ". errorMsg: " + e.getMessage(),
                    sandboxLatencyInfo);
            return;
        }

        final ClassLoader loader =
                mCustomizedSdkContextEnabled
                        ? baseContext.getClassLoader()
                        : getClassLoader(applicationInfo);

        SandboxedSdkContext sandboxedSdkContext =
                new SandboxedSdkContext(
                        baseContext,
                        loader,
                        callingPackageName,
                        applicationInfo,
                        sdkName,
                        customizedApplicationInfo.credentialProtectedDataDir,
                        customizedApplicationInfo.deviceProtectedDataDir,
                        mCustomizedSdkContextEnabled);

        final SandboxedSdkHolder sandboxedSdkHolder = new SandboxedSdkHolder();
        SdkHolderToSdkSandboxServiceCallbackImpl sdkHolderToSdkSandboxServiceCallback =
                new SdkHolderToSdkSandboxServiceCallbackImpl(sdkName, sandboxedSdkHolder);
        sandboxedSdkHolder.init(
                params,
                callback,
                sdkProviderClassName,
                loader,
                sandboxedSdkContext,
                mInjector,
                sandboxLatencyInfo,
                sdkHolderToSdkSandboxServiceCallback);
    }

    /**
     * Create a new instance of ContextImpl to be used for SandboxedSdkContext.
     *
     * <p>We want to ensure that SandboxedSdkContext.getSystemService() will return different
     * instances for different SandboxedSdkContext contexts, so that different SDKs running in the
     * same sdk sandbox process don't share the same manager instance. Because SandboxedSdkContext
     * is a ContextWrapper, it delegates the getSystemService() call to its base context. If we use
     * an application context here as a base context when creating an instance of
     * SandboxedSdkContext it will mean that all instances of SandboxedSdkContext will return the
     * same manager instances.
     *
     * <p>This method ensures we return a new instance of ContextImpl.
     */
    private Context createBaseContext(ApplicationInfo customizedInfo)
            throws PackageManager.NameNotFoundException {

        // In order to create per-SandboxedSdkContext instances in getSystemService, each
        // SandboxedSdkContext needs to have its own instance of ContextImpl as a base context. The
        // instance should have sdk-specific information infused so that system services get the
        // correct information from the base context.
        // customizedInfo has already been infused with sdk-specific information on the server-side.
        if (SdkLevel.isAtLeastU() && mCustomizedSdkContextEnabled) {
            // Context.CONTEXT_INCLUDE_CODE ensures SDK code is loaded in sandbox process along with
            // its class loaders. There is a security check to ensure an app loads code that belongs
            // to it only, but the check can be bypassed using Context.Context_IGNORE_SECURITY.
            int flag = Context.CONTEXT_INCLUDE_CODE | Context.CONTEXT_IGNORE_SECURITY;

            return mInjector.getContext().createContextForSdkInSandbox(customizedInfo, flag);
        } else {
            return mInjector.getContext().createCredentialProtectedStorageContext();
        }
    }

    private void unloadSdkInternal(@NonNull String sdkName) {
        synchronized (mLock) {
            SandboxedSdkHolder sandboxedSdkHolder = mHeldSdk.get(sdkName);
            if (sandboxedSdkHolder != null) {
                sandboxedSdkHolder.unloadSdk();
                mHeldSdk.remove(sdkName);
                if (SdkLevel.isAtLeastU()) {
                    mInjector
                            .getSdkSandboxActivityRegistry()
                            .unregisterAllActivityHandlersForSdk(sdkName);
                }
            }
        }
    }

    private void sendLoadError(
            ILoadSdkInSandboxCallback callback,
            int errorCode,
            String message,
            SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSandboxCalledSystemServer(mInjector.elapsedRealtime());
        sandboxLatencyInfo.setSandboxStatus(SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SANDBOX);
        try {
            callback.onLoadSdkError(new LoadSdkException(errorCode, message), sandboxLatencyInfo);
        } catch (RemoteException e) {
            Log.e(TAG, "Could not send onLoadCodeError");
        }
    }

    private ClassLoader getClassLoader(ApplicationInfo appInfo) {
        final ClassLoader current = getClass().getClassLoader();
        final ClassLoader parent = current != null ? current.getParent() : null;
        return new PathClassLoader(appInfo.sourceDir, parent);
    }

    final class SdkSandboxServiceDelegate extends ISdkSandboxService.Stub {
        @Override
        public void initialize(
                @NonNull ISdkToServiceCallback sdkToServiceCallback,
                boolean isCustomizedSdkContextEnabled) {
            Objects.requireNonNull(sdkToServiceCallback, "sdkToServiceCallback should not be null");
            SdkSandboxServiceImpl.this.initialize(
                    sdkToServiceCallback, isCustomizedSdkContextEnabled);
        }

        @Override
        public void computeSdkStorage(
                @NonNull List<String> sharedPaths,
                @NonNull List<String> sdkPaths,
                @NonNull IComputeSdkStorageCallback callback) {
            Objects.requireNonNull(sharedPaths, "sharedPaths should not be null");
            Objects.requireNonNull(sdkPaths, "sdkPaths should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            SdkSandboxServiceImpl.this.computeSdkStorage(sharedPaths, sdkPaths, callback);
        }

        @Override
        public void loadSdk(
                @NonNull String callingPackageName,
                @NonNull ApplicationInfo applicationInfo,
                @NonNull String sdkName,
                @NonNull String sdkProviderClassName,
                @NonNull ApplicationInfo customizedApplicationInfo,
                @NonNull Bundle params,
                @NonNull ILoadSdkInSandboxCallback callback,
                @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
            sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                    mInjector.elapsedRealtime());

            Objects.requireNonNull(callingPackageName, "callingPackageName should not be null");
            Objects.requireNonNull(applicationInfo, "applicationInfo should not be null");
            Objects.requireNonNull(sdkName, "sdkName should not be null");
            Objects.requireNonNull(sdkProviderClassName, "sdkProviderClassName should not be null");
            Objects.requireNonNull(
                    customizedApplicationInfo, "customized applicationInfo should not be null");
            Objects.requireNonNull(params, "params should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            if (TextUtils.isEmpty(sdkProviderClassName)) {
                throw new IllegalArgumentException("sdkProviderClassName must not be empty");
            }

            SdkSandboxServiceImpl.this.loadSdk(
                    callingPackageName,
                    applicationInfo,
                    sdkName,
                    sdkProviderClassName,
                    customizedApplicationInfo,
                    params,
                    callback,
                    sandboxLatencyInfo);
        }

        @Override
        public void unloadSdk(
                @NonNull String sdkName,
                @NonNull IUnloadSdkInSandboxCallback callback,
                @NonNull SandboxLatencyInfo sandboxLatencyInfo) {
            Objects.requireNonNull(sandboxLatencyInfo, "sandboxLatencyInfo should not be null");
            sandboxLatencyInfo.setTimeSandboxReceivedCallFromSystemServer(
                    mInjector.elapsedRealtime());
            Objects.requireNonNull(sdkName, "sdkName should not be null");
            Objects.requireNonNull(callback, "callback should not be null");
            SdkSandboxServiceImpl.this.unloadSdk(sdkName, callback, sandboxLatencyInfo);
        }

        @Override
        public void syncDataFromClient(@NonNull SharedPreferencesUpdate update) {
            Objects.requireNonNull(update, "update should not be null");
            SdkSandboxServiceImpl.this.syncDataFromClient(update);
        }

        @Override
        public void notifySdkSandboxClientImportanceChange(boolean isForeground) {
            SdkSandboxServiceImpl.this.notifySdkSandboxClientImportanceChange(isForeground);
        }
    }

    /**
     * Interface for {@link SandboxedSdkHolder} to indicate that the SDK has loaded successfully.
     */
    interface SdkHolderToSdkSandboxServiceCallback {
        void onSuccess();
    }

    private class SdkHolderToSdkSandboxServiceCallbackImpl
            implements SdkHolderToSdkSandboxServiceCallback {
        private final SandboxedSdkHolder mHolder;
        private final String mSdkName;

        SdkHolderToSdkSandboxServiceCallbackImpl(String sdkName, SandboxedSdkHolder holder) {
            mSdkName = sdkName;
            mHolder = holder;
        }

        @Override
        public void onSuccess() {
            synchronized (SdkSandboxServiceImpl.this.mLock) {
                mHeldSdk.put(mSdkName, mHolder);
            }
        }
    }
}
