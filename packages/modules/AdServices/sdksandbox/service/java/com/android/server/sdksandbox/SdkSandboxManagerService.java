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

import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_FOREGROUND;
import static android.app.ActivityManager.RunningAppProcessInfo.IMPORTANCE_VISIBLE;
import static android.app.adservices.AdServicesManager.AD_SERVICES_SYSTEM_SERVICE;
import static android.app.sdksandbox.SdkSandboxManager.ACTION_START_SANDBOXED_ACTIVITY;
import static android.app.sdksandbox.SdkSandboxManager.EXTRA_SANDBOXED_ACTIVITY_HANDLER;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR;
import static android.app.sdksandbox.SdkSandboxManager.LOAD_SDK_SDK_SANDBOX_DISABLED;
import static android.app.sdksandbox.SdkSandboxManager.REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_PROCESS_NOT_AVAILABLE;
import static android.app.sdksandbox.SdkSandboxManager.SDK_SANDBOX_SERVICE;

import static com.android.sdksandbox.flags.Flags.sandboxActivitySdkBasedContext;
import static com.android.sdksandbox.service.stats.SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION;
import static com.android.server.sdksandbox.SdkSandboxStorageManager.StorageDirInfo;
import static com.android.server.wm.ActivityInterceptorCallback.MAINLINE_SDK_SANDBOX_ORDER_ID;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.RequiresPermission;
import android.annotation.WorkerThread;
import android.app.ActivityManager;
import android.app.sdksandbox.AppOwnedSdkSandboxInterface;
import android.app.sdksandbox.ILoadSdkCallback;
import android.app.sdksandbox.IRequestSurfacePackageCallback;
import android.app.sdksandbox.ISdkSandboxManager;
import android.app.sdksandbox.ISdkSandboxProcessDeathCallback;
import android.app.sdksandbox.ISdkToServiceCallback;
import android.app.sdksandbox.ISharedPreferencesSyncCallback;
import android.app.sdksandbox.IUnloadSdkCallback;
import android.app.sdksandbox.LoadSdkException;
import android.app.sdksandbox.LogUtil;
import android.app.sdksandbox.SandboxLatencyInfo;
import android.app.sdksandbox.SandboxedSdk;
import android.app.sdksandbox.SdkSandboxManager;
import android.app.sdksandbox.SharedPreferencesUpdate;
import android.app.sdksandbox.sandboxactivity.SdkSandboxActivityAuthority;
import android.app.sdksandbox.sdkprovider.SdkSandboxController;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.ActivityInfo;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.content.pm.ResolveInfo;
import android.content.pm.ServiceInfo;
import android.os.Binder;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.ParcelFileDescriptor;
import android.os.Process;
import android.os.RemoteCallbackList;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.provider.Settings;
import android.util.ArrayMap;
import android.util.ArraySet;
import android.util.Log;
import android.webkit.WebViewUpdateService;

import androidx.annotation.RequiresApi;

import com.android.adservices.AdServicesCommon;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;
import com.android.modules.utils.BackgroundThread;
import com.android.modules.utils.build.SdkLevel;
import com.android.sdksandbox.IComputeSdkStorageCallback;
import com.android.sdksandbox.IRequestSurfacePackageFromSdkCallback;
import com.android.sdksandbox.ISdkSandboxService;
import com.android.sdksandbox.service.stats.SdkSandboxStatsLog;
import com.android.server.LocalManagerRegistry;
import com.android.server.SystemService;
import com.android.server.am.ActivityManagerLocal;
import com.android.server.pm.PackageManagerLocal;
import com.android.server.sdksandbox.helpers.StringHelper;
import com.android.server.sdksandbox.proto.Services.AllowedService;
import com.android.server.sdksandbox.proto.Services.AllowedServices;
import com.android.server.wm.ActivityInterceptorCallback;
import com.android.server.wm.ActivityInterceptorCallback.ActivityInterceptorInfo;
import com.android.server.wm.ActivityInterceptorCallbackRegistry;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Implementation of {@link SdkSandboxManager}.
 *
 * @hide
 */
public class SdkSandboxManagerService extends ISdkSandboxManager.Stub {

    private static final String TAG = "SdkSandboxManager";

    private static final String STOP_SDK_SANDBOX_PERMISSION =
            "com.android.app.sdksandbox.permission.STOP_SDK_SANDBOX";

    private static final String SANDBOX_NOT_AVAILABLE_MSG = "Sandbox is unavailable";
    private static final String SANDBOX_DISABLED_MSG = "SDK sandbox is disabled";

    private static final String DUMP_ARG_AD_SERVICES = "--AdServices";

    private final Context mContext;

    private final ActivityManager mActivityManager;
    private final ActivityManagerLocal mActivityManagerLocal;
    private final Handler mHandler;
    private final SdkSandboxStorageManager mSdkSandboxStorageManager;
    private final SdkSandboxServiceProvider mServiceProvider;
    private final SdkSandboxStatsdLogger mSdkSandboxStatsdLogger;
    private final SdkSandboxRestrictionManager mSdkSandboxRestrictionManager;

    @GuardedBy("mLock")
    private IBinder mAdServicesManager;

    // TODO(b/282239822): temporary guard to define if dump() should handle the --AdServices otpion
    @GuardedBy("mLock")
    private boolean mAdServicesManagerPublished;

    private final Object mLock = new Object();

    private static final String PACKAGE_MIME_TYPE = "application/vnd.android.package-archive";

    /**
     * For each app, keep a mapping from SDK name to it's corresponding LoadSdkSession. This can
     * contain all SDKs that are pending load, have been loaded, unloaded etc. Therefore, it is
     * important to filter out by the type needed.
     */
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayMap<String, LoadSdkSession>> mLoadSdkSessions =
            new ArrayMap<>();

    /**
     * For each app, keep a mapping from {@link AppOwnedSdkSandboxInterface} name to its
     * corresponding {@link AppOwnedSdkSandboxInterface}. This contains all
     * AppOwnedSdkSandboxInterfaces that are registered.
     */
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayMap<String, AppOwnedSdkSandboxInterface>>
            mHeldInterfaces = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, IBinder> mCallingInfosWithDeathRecipients =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final Set<CallingInfo> mRunningInstrumentations = new ArraySet<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, RemoteCallbackList<ISdkSandboxProcessDeathCallback>>
            mSandboxLifecycleCallbacks = new ArrayMap<>();

    // Callbacks that need to be invoked when the sandbox binding has occurred (either successfully
    // or unsuccessfully).
    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ArrayList<SandboxBindingCallback>>
            mSandboxBindingCallbacks = new ArrayMap<>();

    @GuardedBy("mLock")
    private final ArrayMap<CallingInfo, ISharedPreferencesSyncCallback> mSyncDataCallbacks =
            new ArrayMap<>();

    @GuardedBy("mLock")
    private final UidImportanceListener mUidImportanceListener = new UidImportanceListener();

    private Injector mInjector;

    private final SdkSandboxPulledAtoms mSdkSandboxPulledAtoms;

    private SdkSandboxSettingsListener mSdkSandboxSettingsListener;

    private static final boolean DEFAULT_VALUE_DISABLE_SDK_SANDBOX = true;
    private static final boolean DEFAULT_VALUE_CUSTOMIZED_SDK_CONTEXT_ENABLED = false;

    /**
     * Property to enforce restrictions for SDK sandbox processes. If the value of this property is
     * {@code true}, the restrictions will be enforced.
     */
    static final String PROPERTY_ENFORCE_RESTRICTIONS = "sdksandbox_enforce_restrictions";

    static final boolean DEFAULT_VALUE_ENFORCE_RESTRICTIONS = true;

    private static final String WEBVIEW_DEVELOPER_MODE_CONTENT_PROVIDER =
            "DeveloperModeContentProvider";

    private static final String WEBVIEW_SAFE_MODE_CONTENT_PROVIDER = "SafeModeContentProvider";

    // If AdServices register itself as binder service, dump() will ignore the --AdServices option
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static final String DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF =
            "Don't need to dump AdServices as it's available as " + AD_SERVICES_SYSTEM_SERVICE;

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    static final ArraySet<String> DEFAULT_ACTIVITY_ALLOWED_ACTIONS =
            new ArraySet<>(
                    Arrays.asList(
                            Intent.ACTION_VIEW,
                            Intent.ACTION_DIAL,
                            Intent.ACTION_EDIT,
                            Intent.ACTION_INSERT));

    static final ArraySet<String> DEFAULT_CONTENTPROVIDER_ALLOWED_AUTHORITIES =
            new ArraySet<>(
                    Arrays.asList(
                            Settings.AUTHORITY, "com.android.textclassifier.icons", "downloads"));

    static class Injector {
        private final Context mContext;
        private SdkSandboxManagerLocal mLocalManager;
        private final SdkSandboxServiceProvider mServiceProvider;
        private final @Nullable String mAdServicesPackageName;
        private final SdkSandboxStatsdLogger mSdkSandboxStatsdLogger;

        Injector(Context context) {
            mContext = context;
            mServiceProvider = new SdkSandboxServiceProviderImpl(mContext);
            mAdServicesPackageName = resolveAdServicesPackage(mContext);
            mSdkSandboxStatsdLogger = new SdkSandboxStatsdLogger();
        }

        private static final boolean IS_EMULATOR =
                SystemProperties.getBoolean("ro.boot.qemu", false);

        private static String resolveAdServicesPackage(Context context) {
            PackageManager pm = context.getPackageManager();
            Intent serviceIntent = new Intent(AdServicesCommon.ACTION_TOPICS_SERVICE);
            List<ResolveInfo> resolveInfos =
                    pm.queryIntentServices(
                            serviceIntent,
                            PackageManager.GET_SERVICES
                                    | PackageManager.MATCH_SYSTEM_ONLY
                                    | PackageManager.MATCH_DIRECT_BOOT_AWARE
                                    | PackageManager.MATCH_DIRECT_BOOT_UNAWARE);
            ServiceInfo serviceInfo =
                    AdServicesCommon.resolveAdServicesService(
                            resolveInfos, serviceIntent.getAction());
            return serviceInfo != null ? serviceInfo.packageName : null;
        }

        long elapsedRealtime() {
            return SystemClock.elapsedRealtime();
        }

        SdkSandboxShellCommand createShellCommand(
                SdkSandboxManagerService service,
                Context context,
                boolean supportsAdServicesShellCmd) {
            return new SdkSandboxShellCommand(service, context, supportsAdServicesShellCmd);
        }

        boolean isEmulator() {
            return IS_EMULATOR;
        }

        SdkSandboxServiceProvider getSdkSandboxServiceProvider() {
            return mServiceProvider;
        }

        SdkSandboxPulledAtoms getSdkSandboxPulledAtoms() {
            return new SdkSandboxPulledAtoms();
        }

        PackageManagerLocal getPackageManagerLocal() {
            return LocalManagerRegistry.getManager(PackageManagerLocal.class);
        }

        SdkSandboxStorageManager getSdkSandboxStorageManager() {
            return new SdkSandboxStorageManager(mContext, mLocalManager, getPackageManagerLocal());
        }

        void setLocalManager(SdkSandboxManagerLocal localManager) {
            mLocalManager = localManager;
        }

        SdkSandboxManagerLocal getLocalManager() {
            return mLocalManager;
        }

        String getAdServicesPackageName() {
            return mAdServicesPackageName;
        }

        boolean isAdServiceApkPresent() {
            return mAdServicesPackageName != null;
        }

        SdkSandboxStatsdLogger getSdkSandboxStatsdLogger() {
            return mSdkSandboxStatsdLogger;
        }
    }

    SdkSandboxManagerService(Context context) {
        this(context, new Injector(context));
    }

    @VisibleForTesting
    SdkSandboxManagerService(Context context, Injector injector) {
        mContext = context;
        mInjector = injector;
        mInjector.setLocalManager(new LocalImpl());
        mServiceProvider = mInjector.getSdkSandboxServiceProvider();
        mActivityManager = mContext.getSystemService(ActivityManager.class);
        mActivityManagerLocal = LocalManagerRegistry.getManager(ActivityManagerLocal.class);
        mSdkSandboxPulledAtoms = mInjector.getSdkSandboxPulledAtoms();
        mSdkSandboxStorageManager = mInjector.getSdkSandboxStorageManager();
        mSdkSandboxStatsdLogger = mInjector.getSdkSandboxStatsdLogger();
        mSdkSandboxRestrictionManager = new SdkSandboxRestrictionManager(mContext);

        // Start the handler thread.
        HandlerThread handlerThread = new HandlerThread("SdkSandboxManagerServiceHandler");
        handlerThread.start();
        mHandler = new Handler(handlerThread.getLooper());

        registerBroadcastReceivers();

        mSdkSandboxSettingsListener = new SdkSandboxSettingsListener(mContext, this);
        mSdkSandboxPulledAtoms.initialize(mContext);

        if (SdkLevel.isAtLeastU()) {
            registerSandboxActivityInterceptor();
        }
    }

    private void registerBroadcastReceivers() {
        registerPackageUpdateBroadcastReceiver();
        registerVerifierBroadcastReceiver();
    }

    private void registerPackageUpdateBroadcastReceiver() {
        // Register for package addition and update
        final IntentFilter packageAddedIntentFilter = new IntentFilter();
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_ADDED);
        packageAddedIntentFilter.addAction(Intent.ACTION_PACKAGE_REPLACED);
        packageAddedIntentFilter.addDataScheme("package");
        BroadcastReceiver packageAddedIntentReceiver =
                new BroadcastReceiver() {
                    @Override
                    public void onReceive(Context context, Intent intent) {
                        final String packageName = intent.getData().getSchemeSpecificPart();
                        final int uid = intent.getIntExtra(Intent.EXTRA_UID, -1);
                        final CallingInfo callingInfo = new CallingInfo(uid, packageName);
                        mHandler.post(
                                () ->
                                        mSdkSandboxStorageManager.onPackageAddedOrUpdated(
                                                callingInfo));
                    }
                };
        mContext.registerReceiver(
                packageAddedIntentReceiver,
                packageAddedIntentFilter,
                /*broadcastPermission=*/ null,
                mHandler);
    }

    private void registerVerifierBroadcastReceiver() {
        final IntentFilter packageNeedsVerificationIntentFilter = new IntentFilter();
        try {
            packageNeedsVerificationIntentFilter.addDataType(PACKAGE_MIME_TYPE);
            packageNeedsVerificationIntentFilter.addAction(
                    Intent.ACTION_PACKAGE_NEEDS_VERIFICATION);
            mContext.registerReceiverForAllUsers(
                    new SdkSandboxVerifierReceiver(),
                    packageNeedsVerificationIntentFilter,
                    /*broadcastPermission=*/ null,
                    /*scheduler=*/ null,
                    Context.RECEIVER_EXPORTED);
        } catch (IntentFilter.MalformedMimeTypeException e) {
            Log.e(TAG, "Could not register verifier");
        }
    }

    @Override
    public List<SandboxedSdk> getSandboxedSdks(
            String callingPackageName, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);

        final List<SandboxedSdk> sandboxedSdks = new ArrayList<>();
        synchronized (mLock) {
            ArrayList<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
            for (int i = 0; i < loadedSdks.size(); i++) {
                LoadSdkSession sdk = loadedSdks.get(i);
                SandboxedSdk sandboxedSdk = sdk.getSandboxedSdk();
                if (sandboxedSdk != null) {
                    sandboxedSdks.add(sandboxedSdk);
                } else {
                    Log.w(
                            TAG,
                            "SandboxedSdk is null for SDK "
                                    + sdk.mSdkName
                                    + " despite being loaded");
                }
            }
        }
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        logSandboxApiLatency(sandboxLatencyInfo);
        return sandboxedSdks;
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private void registerSandboxActivityInterceptor() {
        final ActivityInterceptorCallback mActivityInterceptorCallback =
                new SdkSandboxInterceptorCallback();
        ActivityInterceptorCallbackRegistry registry =
                ActivityInterceptorCallbackRegistry.getInstance();
        registry.registerActivityInterceptorCallback(
                MAINLINE_SDK_SANDBOX_ORDER_ID, mActivityInterceptorCallback);
    }

    private ArrayList<AppOwnedSdkSandboxInterface> getRegisteredAppOwnedSdkSandboxInterfacesForApp(
            CallingInfo callingInfo) {
        synchronized (mLock) {
            if (!mHeldInterfaces.containsKey(callingInfo)) {
                return new ArrayList<>();
            }
            return new ArrayList<>(mHeldInterfaces.get(callingInfo).values());
        }
    }

    private ArrayList<LoadSdkSession> getLoadedSdksForApp(CallingInfo callingInfo) {
        ArrayList<LoadSdkSession> loadedSdks = new ArrayList<>();
        synchronized (mLock) {
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession sdk = loadSessions.get(i);
                    if (sdk.getStatus() == LoadSdkSession.LOADED) {
                        loadedSdks.add(sdk);
                    }
                }
            }
        }
        return loadedSdks;
    }

    @Override
    public void addSdkSandboxProcessDeathCallback(
            String callingPackageName,
            SandboxLatencyInfo sandboxLatencyInfo,
            ISdkSandboxProcessDeathCallback callback) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
        synchronized (mLock) {
            if (mSandboxLifecycleCallbacks.containsKey(callingInfo)) {
                mSandboxLifecycleCallbacks.get(callingInfo).register(callback);
            } else {
                RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks =
                        new RemoteCallbackList<>();
                sandboxLifecycleCallbacks.register(callback);
                mSandboxLifecycleCallbacks.put(callingInfo, sandboxLifecycleCallbacks);
            }
        }

        // addSdkSandboxProcessDeathCallback() can be called without calling loadSdk(). Register for
        // app death to make sure cleanup occurs.
        registerForAppDeath(callingInfo, callback.asBinder());
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        logSandboxApiLatency(sandboxLatencyInfo);
    }

    // Register a handler for app death using any binder object originating from the app. Returns
    // true if registering the handle succeeded and false if it failed (because the app died by
    // then).
    private boolean registerForAppDeath(CallingInfo callingInfo, IBinder appBinderObject) {
        // Register a death recipient to clean up app related state and unbind its service after
        // the app dies.
        try {
            synchronized (mLock) {
                if (!mCallingInfosWithDeathRecipients.containsKey(callingInfo)) {
                    Log.d(TAG, "Registering " + callingInfo + " for death notification");
                    appBinderObject.linkToDeath(() -> onAppDeath(callingInfo), 0);
                    mCallingInfosWithDeathRecipients.put(callingInfo, appBinderObject);
                }
            }
        } catch (RemoteException re) {
            // App has already died, cleanup sdk link, and unbind its service
            onAppDeath(callingInfo);
            return false;
        }

        return true;
    }

    @Override
    public void removeSdkSandboxProcessDeathCallback(
            String callingPackageName,
            SandboxLatencyInfo sandboxLatencyInfo,
            ISdkSandboxProcessDeathCallback callback) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
        synchronized (mLock) {
            RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks =
                    mSandboxLifecycleCallbacks.get(callingInfo);
            if (sandboxLifecycleCallbacks != null) {
                sandboxLifecycleCallbacks.unregister(callback);
            }
        }
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        logSandboxApiLatency(sandboxLatencyInfo);
    }

    @Override
    public List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces(
            String callingPackageName, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
        List<AppOwnedSdkSandboxInterface> appOwnedSdkSandboxInterfaces =
                getRegisteredAppOwnedSdkSandboxInterfacesForApp(callingInfo);
        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        logSandboxApiLatency(sandboxLatencyInfo);
        return appOwnedSdkSandboxInterfaces;
    }

    @Override
    public void registerAppOwnedSdkSandboxInterface(
            String callingPackageName,
            AppOwnedSdkSandboxInterface appOwnedSdkSandboxInterface,
            SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);

        synchronized (mLock) {
            if (mHeldInterfaces.containsKey(callingInfo)) {
                if (mHeldInterfaces
                        .get(callingInfo)
                        .containsKey(appOwnedSdkSandboxInterface.getName())) {
                    throw new IllegalStateException(
                            "Already registered interface of name "
                                    + appOwnedSdkSandboxInterface.getName());
                }
            }
            mHeldInterfaces.computeIfAbsent(callingInfo, k -> new ArrayMap<>());
            mHeldInterfaces
                    .get(callingInfo)
                    .put(appOwnedSdkSandboxInterface.getName(), appOwnedSdkSandboxInterface);
        }
        // registerAppOwnedSdkSandboxInterface() can be called without calling loadSdk(). Register
        // for app death to make sure cleanup occurs.
        boolean isRegistrationForAppDeathSuccessful =
                registerForAppDeath(callingInfo, appOwnedSdkSandboxInterface.getInterface());

        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        if (!isRegistrationForAppDeathSuccessful) {
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
        }
        logSandboxApiLatency(sandboxLatencyInfo);
    }

    @Override
    public void unregisterAppOwnedSdkSandboxInterface(
            String callingPackageName, String name, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);

        synchronized (mLock) {
            if (mHeldInterfaces.containsKey(callingInfo)) {
                mHeldInterfaces.get(callingInfo).remove(name);
            }
        }

        sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
        logSandboxApiLatency(sandboxLatencyInfo);
    }

    @Override
    public void loadSdk(
            String callingPackageName,
            IBinder callingAppProcessToken,
            String sdkName,
            SandboxLatencyInfo sandboxLatencyInfo,
            Bundle params,
            ILoadSdkCallback callback) {
        try {
            sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

            final int callingUid = Binder.getCallingUid();
            CallingInfo callingInfo;
            if (Process.isSdkSandboxUid(callingUid)) {
                callingInfo =
                        CallingInfo.fromExternal(
                                mContext,
                                Process.getAppUidForSdkSandboxUid(callingUid),
                                callingPackageName);
            } else {
                callingInfo =
                        CallingInfo.fromBinderWithApplicationThread(
                                mContext, callingPackageName, callingAppProcessToken);
            }
            enforceCallerHasNetworkAccess(callingPackageName);
            enforceCallerOrItsSandboxRunInForeground(callingInfo);
            synchronized (mLock) {
                if (mRunningInstrumentations.contains(callingInfo)) {
                    throw new SecurityException(
                            "Currently running instrumentation of this sdk sandbox process");
                }
            }

            if (isSdkSandboxDisabled()) {
                Log.i(TAG, "Not loading an SDK as the SDK sandbox is disabled");
                sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());
                callback.onLoadSdkFailure(
                        new LoadSdkException(LOAD_SDK_SDK_SANDBOX_DISABLED, SANDBOX_DISABLED_MSG),
                        sandboxLatencyInfo);
                return;
            }

            final long token = Binder.clearCallingIdentity();
            try {
                loadSdkWithClearIdentity(
                        callingInfo,
                        sdkName,
                        params,
                        callback,
                        sandboxLatencyInfo);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } catch (Throwable e) {
            try {
                Log.e(TAG, "Failed to load SDK " + sdkName, e);
                sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());
                callback.onLoadSdkFailure(
                        new LoadSdkException(LOAD_SDK_INTERNAL_ERROR, e.getMessage(), e),
                        sandboxLatencyInfo);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to send onLoadSdkFailure", e);
            }
        }
    }

    private void loadSdkWithClearIdentity(
            CallingInfo callingInfo,
            String sdkName,
            Bundle params,
            ILoadSdkCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {
        LoadSdkSession loadSdkSession;

        try {
            loadSdkSession =
                    new LoadSdkSession(
                            mContext, this, mInjector, sdkName, callingInfo, params, callback);
        } catch (PackageManager.NameNotFoundException e) {
            Log.w(TAG, e.getMessage(), e);
            sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);

            LoadSdkException loadSdkException =
                    new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_NOT_FOUND, e.getMessage() + " not found");
            sandboxLatencyInfo.setTimeSystemServerCalledApp(mInjector.elapsedRealtime());
            try {
                callback.onLoadSdkFailure(loadSdkException, sandboxLatencyInfo);
            } catch (RemoteException remoteException) {
                Log.w(TAG, "Failed to send onLoadSdkFailure", remoteException);
            }
            return;
        }

        // Ensure we are not already loading this sdk. That's determined by checking if we already
        // have a completed LoadSdkSession with the same SDK name for the calling info.
        synchronized (mLock) {
            LoadSdkSession prevLoadSession = null;
            // Get any previous load session for this SDK if exists.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                prevLoadSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }

            // If there was a previous load session and the status is loaded, this new load request
            // should fail.
            if (prevLoadSession != null && prevLoadSession.getStatus() == LoadSdkSession.LOADED) {
                sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
                sandboxLatencyInfo.setSandboxStatus(
                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
                loadSdkSession.handleLoadFailure(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " has been loaded already"),
                        sandboxLatencyInfo);
                return;
            }

            // If there was an ongoing load session for this SDK, this new load request should fail.
            if (prevLoadSession != null
                    && prevLoadSession.getStatus() == LoadSdkSession.LOAD_PENDING) {
                sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
                sandboxLatencyInfo.setSandboxStatus(
                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
                loadSdkSession.handleLoadFailure(
                        new LoadSdkException(
                                SdkSandboxManager.LOAD_SDK_ALREADY_LOADED,
                                sdkName + " is currently being loaded"),
                        sandboxLatencyInfo);
                return;
            }

            // If there was no previous load session (or there was one but its load status was
            // unloaded or failed), it should be replaced by the new load session.
            mLoadSdkSessions.computeIfAbsent(callingInfo, k -> new ArrayMap<>());
            mLoadSdkSessions.get(callingInfo).put(sdkName, loadSdkSession);
        }

        synchronized (mLock) {
            if (!callingInfo.isCallFromSdkSandbox()) {
                // The code is used to be able to detect app death and app foreground state.
                // Hence, it is of no use for the call from sandbox.
                mUidImportanceListener.startListening();
                if (!registerForAppDeath(callingInfo, callback.asBinder())) {
                    sandboxLatencyInfo.setTimeSystemServerCallFinished(mInjector.elapsedRealtime());
                    sandboxLatencyInfo.setSandboxStatus(
                            SandboxLatencyInfo
                                    .SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);
                    // App has already died and there is no point in loading the SDK.
                    return;
                }
            }
        }

        // Callback to be invoked once the sandbox has been created;
        SandboxBindingCallback sandboxBindingCallback = createSdkLoadCallback(loadSdkSession);
        startSdkSandboxIfNeeded(callingInfo, sandboxBindingCallback, sandboxLatencyInfo);
    }

    private SandboxBindingCallback createSdkLoadCallback(LoadSdkSession loadSdkSession) {
        return new SandboxBindingCallback() {
            @Override
            public void onBindingSuccessful(
                    ISdkSandboxService service, SandboxLatencyInfo sandboxLatencyInfo) {
                loadSdkForService(loadSdkSession, service, sandboxLatencyInfo);
            }

            @Override
            public void onBindingFailed(
                    LoadSdkException exception, SandboxLatencyInfo sandboxLatencyInfo) {
                sandboxLatencyInfo.setTimeSandboxLoaded(mInjector.elapsedRealtime());
                sandboxLatencyInfo.setSandboxStatus(
                        SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_LOAD_SANDBOX);
                loadSdkSession.handleLoadFailure(
                        exception,
                        sandboxLatencyInfo);
            }
        };
    }

    void startSdkSandboxIfNeeded(
            CallingInfo callingInfo,
            SandboxBindingCallback callback,
            SandboxLatencyInfo sandboxLatencyInfo) {

        boolean isSandboxStartRequired = false;
        synchronized (mLock) {
            @SdkSandboxServiceProvider.SandboxStatus
            int sandboxStatus = mServiceProvider.getSandboxStatusForApp(callingInfo);

            // Check if service is already created for the app.
            if (sandboxStatus == SdkSandboxServiceProvider.NON_EXISTENT) {
                // We do not want to start sandbox if the call is from sandbox
                // and sandbox is dead/non-existent since SDK loading the other
                // SDK itself will be unloaded if sandbox dies after the loadSdk call.
                if (callingInfo.isCallFromSdkSandbox()) {
                    return;
                }
                addSandboxBindingCallback(callingInfo, callback);
                isSandboxStartRequired = true;
            } else if (sandboxStatus == SdkSandboxServiceProvider.CREATE_PENDING) {
                addSandboxBindingCallback(callingInfo, callback);
                // The sandbox is in the process of being brought up. Nothing more to do here.
                return;
            }
        }

        if (!isSandboxStartRequired) {
            ISdkSandboxService service = mServiceProvider.getSdkSandboxServiceForApp(callingInfo);
            if (service == null) {
                LoadSdkException exception =
                        new LoadSdkException(
                                SDK_SANDBOX_PROCESS_NOT_AVAILABLE, SANDBOX_NOT_AVAILABLE_MSG);
                callback.onBindingFailed(exception, sandboxLatencyInfo);
            }
            callback.onBindingSuccessful(service, sandboxLatencyInfo);
            return;
        }

        // Prepare sdk data directories before starting the sandbox. If sdk data package directory
        // is missing, starting the sandbox process would crash as we will fail to mount data_mirror
        // for sdk-data isolation.
        mSdkSandboxStorageManager.prepareSdkDataOnLoad(callingInfo);
        sandboxLatencyInfo.setTimeLoadSandboxStarted(mInjector.elapsedRealtime());
        mServiceProvider.bindService(
                callingInfo,
                new SandboxServiceConnection(mServiceProvider, callingInfo, sandboxLatencyInfo));
    }

    private void addSandboxBindingCallback(
            CallingInfo callingInfo, SandboxBindingCallback callback) {
        synchronized (mLock) {
            mSandboxBindingCallbacks.computeIfAbsent(callingInfo, k -> new ArrayList<>());
            mSandboxBindingCallbacks.get(callingInfo).add(callback);
        }
    }

    @Override
    public void unloadSdk(
            String callingPackageName, String sdkName, SandboxLatencyInfo sandboxLatencyInfo) {
        sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

        final int callingUid = Binder.getCallingUid();
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
        enforceCallerOrItsSandboxRunInForeground(callingInfo);

        final long token = Binder.clearCallingIdentity();
        try {
            unloadSdkWithClearIdentity(callingInfo, sdkName, sandboxLatencyInfo);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void unloadSdkWithClearIdentity(
            CallingInfo callingInfo, String sdkName, SandboxLatencyInfo sandboxLatencyInfo) {
        LoadSdkSession prevLoadSession = null;
        synchronized (mLock) {
            // TODO(b/254657226): Add a callback or return value for unloadSdk() to indicate
            // success of unload.

            // Get any previous load session for this SDK if exists.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                prevLoadSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }
        }

        // If there was no previous load session or the SDK is not loaded, there is nothing to
        // unload.
        if (prevLoadSession == null) {
            // Unloading SDK that is not loaded is a no-op, return.
            Log.w(TAG, "SDK " + sdkName + " is not loaded for " + callingInfo);
            return;
        }

        IUnloadSdkCallback unloadSdkCallback =
                new IUnloadSdkCallback.Stub() {
                    @Override
                    public void onUnloadSdk(SandboxLatencyInfo sandboxLatencyInfo)
                            throws RemoteException {
                        sandboxLatencyInfo.setTimeSystemServerCalledApp(
                                mInjector.elapsedRealtime());
                        logSandboxApiLatency(sandboxLatencyInfo);
                    }
                };
        prevLoadSession.unload(sandboxLatencyInfo, unloadSdkCallback);

        ArrayList<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
        if (loadedSdks.isEmpty()) {
            stopSdkSandboxService(
                    callingInfo, "Caller " + callingInfo + " has no remaining SDKS loaded.");
        }
    }

    private void enforceCallingPackageBelongsToUid(CallingInfo callingInfo) {
        int callingUid = callingInfo.getUid();
        String callingPackage = callingInfo.getPackageName();
        int packageUid;
        PackageManager pm = mContext.createContextAsUser(
                UserHandle.getUserHandleForUid(callingUid), 0).getPackageManager();
        try {
            packageUid = pm.getPackageUid(callingPackage, 0);
        } catch (PackageManager.NameNotFoundException e) {
            throw new SecurityException(callingPackage + " not found");
        }
        if (packageUid != callingUid) {
            throw new SecurityException(callingPackage + " does not belong to uid " + callingUid);
        }
    }

    private void enforceCallerOrItsSandboxRunInForeground(CallingInfo callingInfo) {
        String callingPackage = callingInfo.getPackageName();
        final long token = Binder.clearCallingIdentity();
        try {
            int importance =
                    Math.min(
                            mActivityManager.getUidImportance(callingInfo.getUid()),
                            mActivityManager.getUidImportance(
                                    Process.toSdkSandboxUid(callingInfo.getUid())));
            if (importance > IMPORTANCE_FOREGROUND) {
                throw new SecurityException(callingPackage + " does not run in the foreground");
            }
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    private void enforceCallerHasNetworkAccess(String callingPackage) {
        mContext.enforceCallingPermission(android.Manifest.permission.INTERNET,
                callingPackage + " does not hold INTERNET permission");
        mContext.enforceCallingPermission(android.Manifest.permission.ACCESS_NETWORK_STATE,
                callingPackage + " does not hold ACCESS_NETWORK_STATE permission");
    }

    private void onAppDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            Log.d(TAG, "App " + callingInfo + " has died, cleaning up associated sandbox info");
            mSandboxLifecycleCallbacks.remove(callingInfo);
            mSandboxBindingCallbacks.remove(callingInfo);
            mCallingInfosWithDeathRecipients.remove(callingInfo);
            if (mCallingInfosWithDeathRecipients.size() == 0) {
                mUidImportanceListener.stopListening();
            }
            mSyncDataCallbacks.remove(callingInfo);
            mLoadSdkSessions.remove(callingInfo);
            mHeldInterfaces.remove(callingInfo);
            stopSdkSandboxService(callingInfo, "Caller " + callingInfo + " has died");
            mServiceProvider.onAppDeath(callingInfo);
        }
    }

    @Override
    public void requestSurfacePackage(
            String callingPackageName,
            String sdkName,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            SandboxLatencyInfo sandboxLatencyInfo,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        try {
            sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());

            LogUtil.d(
                    TAG,
                    "requestSurfacePackage call received. callingPackageName: "
                            + callingPackageName);

            final int callingUid = Binder.getCallingUid();
            final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
            enforceCallerOrItsSandboxRunInForeground(callingInfo);

            final long token = Binder.clearCallingIdentity();
            try {
                requestSurfacePackageWithClearIdentity(
                        callingInfo,
                        sdkName,
                        hostToken,
                        displayId,
                        width,
                        height,
                        sandboxLatencyInfo,
                        params,
                        callback);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } catch (Throwable e) {
            try {
                callback.onSurfacePackageError(
                        IRequestSurfacePackageFromSdkCallback.SURFACE_PACKAGE_INTERNAL_ERROR,
                        e.getMessage(),
                        sandboxLatencyInfo);
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to send onRequestSurfacePackageError", e);
            }
        }
    }

    private void requestSurfacePackageWithClearIdentity(
            CallingInfo callingInfo,
            String sdkName,
            IBinder hostToken,
            int displayId,
            int width,
            int height,
            SandboxLatencyInfo sandboxLatencyInfo,
            Bundle params,
            IRequestSurfacePackageCallback callback) {
        LoadSdkSession loadSdkSession = null;
        synchronized (mLock) {
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                loadSdkSession = mLoadSdkSessions.get(callingInfo).get(sdkName);
            }
        }
        if (loadSdkSession == null) {
            LogUtil.d(
                    TAG,
                    callingInfo + " requested surface package, but could not find SDK " + sdkName);

            final long timeSystemServerProcessedCall = mInjector.elapsedRealtime();
            sandboxLatencyInfo.setTimeSystemServerCallFinished(timeSystemServerProcessedCall);
            sandboxLatencyInfo.setTimeSystemServerCalledApp(timeSystemServerProcessedCall);
            sandboxLatencyInfo.setSandboxStatus(
                    SandboxLatencyInfo.SANDBOX_STATUS_FAILED_AT_SYSTEM_SERVER_APP_TO_SANDBOX);

            try {
                callback.onSurfacePackageError(
                        REQUEST_SURFACE_PACKAGE_SDK_NOT_LOADED,
                        "SDK " + sdkName + " is not loaded",
                        sandboxLatencyInfo);
            } catch (RemoteException e) {
                Log.w(TAG, "Failed to send onSurfacePackageError", e);
            }
            return;
        }

        loadSdkSession.requestSurfacePackage(
                hostToken, displayId, width, height, sandboxLatencyInfo, params, callback);
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void onUserUnlocking(int userId) {
        Log.i(TAG, "onUserUnlocking " + userId);
        // using postDelayed to wait for other volumes to mount
        mHandler.postDelayed(() -> mSdkSandboxStorageManager.onUserUnlocking(userId), 20000);
    }

    @Override
    @RequiresPermission(android.Manifest.permission.DUMP)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        mContext.enforceCallingPermission(android.Manifest.permission.DUMP,
                "Can't dump " + TAG);

        if (args != null && args.length > 0 && args[0].equals(DUMP_ARG_AD_SERVICES)) {
            dumpAdServices(fd, writer, args, /* quiet= */ false);
            return;
        }

        // TODO(b/211575098): Use IndentingPrintWriter for better formatting
        synchronized (mLock) {
            writer.println(
                    "Killswitch enabled: " + mSdkSandboxSettingsListener.isKillSwitchEnabled());
            writer.println(
                    "Customized Sdk Context enabled: "
                            + mSdkSandboxSettingsListener.isCustomizedSdkContextEnabled());
            writer.println("mLoadSdkSessions size: " + mLoadSdkSessions.size());
            for (CallingInfo callingInfo : mLoadSdkSessions.keySet()) {
                writer.printf("Caller: %s has following SDKs", callingInfo);
                writer.println();
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession sdk = loadSessions.get(i);
                    writer.printf("SDK: %s Status: %s", sdk.mSdkName, sdk.getStatus());
                    writer.println();
                }
            }
            writer.println();

            writer.println("AdServicesManager binder published: " + mAdServicesManagerPublished);
        }
        if (mInjector.isAdServiceApkPresent()) {
            writer.println("AdService package name: " + mInjector.getAdServicesPackageName());
        } else {
            writer.println("AdService apk not present.");
        }
        writer.println();

        writer.println("mServiceProvider:");
        mServiceProvider.dump(writer);
        writer.println();

        dumpAdServices(fd, writer, args, /* quiet= */ true);
    }

    private void dumpAdServices(
            @Nullable FileDescriptor fd, PrintWriter writer, String[] args, boolean quiet) {

        synchronized (mLock) {
            if (mAdServicesManagerPublished) {
                // AdServices registered itself as binder service
                if (quiet) {
                    Log.d(TAG, DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF);
                } else {
                    writer.println(DUMP_AD_SERVICES_MESSAGE_HANDLED_BY_AD_SERVICES_ITSELF);
                }
                return;
            }
        }

        writer.print("AdServices:");
        IBinder adServicesManager = getAdServicesManager();
        if (adServicesManager == null) {
            // Should not happen on "real life", but it could on unit tests.
            Log.e(TAG, "dumpAdServices(): mAdServicesManager not set");
            writer.println(" N/A");
            return;
        }
        writer.println();
        writer.println();
        writer.flush(); // must flush, other raw dump on fd below will be printed before it
        try {
            adServicesManager.dump(fd, args);
        } catch (RemoteException e) {
            Log.e(TAG, "Failed to dump AdServices", e);
            // Shouldn't happen, but it doesn't hurt to catch
            writer.printf("Failed to dump Adservices: %s\n", e);
        }
        writer.println();
    }

    @Override
    public void syncDataFromClient(
            String callingPackageName,
            SandboxLatencyInfo sandboxLatencyInfo,
            SharedPreferencesUpdate update,
            ISharedPreferencesSyncCallback callback) {
        try {
            sandboxLatencyInfo.setTimeSystemServerReceivedCallFromApp(mInjector.elapsedRealtime());
            logSandboxApiLatency(sandboxLatencyInfo);

            final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);
            enforceCallingPackageBelongsToUid(callingInfo);

            final long token = Binder.clearCallingIdentity();
            try {
                syncDataFromClientInternal(callingInfo, update, callback);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        } catch (Throwable e) {
            try {
                callback.onError(
                        ISharedPreferencesSyncCallback.PREFERENCES_SYNC_INTERNAL_ERROR,
                        e.getMessage());
            } catch (RemoteException ex) {
                Log.e(TAG, "Failed to send ISharedPreferencesSyncCallback.onError", e);
            }
        }
    }

    private void syncDataFromClientInternal(
            CallingInfo callingInfo,
            SharedPreferencesUpdate update,
            ISharedPreferencesSyncCallback callback) {
        // check first if service already bound
        ISdkSandboxService service = mServiceProvider.getSdkSandboxServiceForApp(callingInfo);
        if (service != null) {
            try {
                service.syncDataFromClient(update);
            } catch (RemoteException e) {
                syncDataOnError(callingInfo, callback, e.getMessage());
            }
        } else {
            syncDataOnError(callingInfo, callback, "Sandbox not available");
        }
    }

    private void syncDataOnError(
            CallingInfo callingInfo, ISharedPreferencesSyncCallback callback, String errorMsg) {
        // Store reference to the callback so that we can notify SdkSandboxManager when sandbox
        // starts
        synchronized (mLock) {
            mSyncDataCallbacks.put(callingInfo, callback);
        }
        try {
            callback.onError(ISharedPreferencesSyncCallback.SANDBOX_NOT_AVAILABLE, errorMsg);
        } catch (RemoteException ignore) {
            // App died. Sync will be re-established again by app later.
        }
    }

    @Override
    public void logSandboxApiLatency(SandboxLatencyInfo sandboxLatencyInfo) {
        mSdkSandboxStatsdLogger.logSandboxApiLatency(sandboxLatencyInfo);
    }

    @Override
    public void logSandboxActivityApiLatency(int method, int callResult, int latencyMillis) {
        int clientUid = Binder.getCallingUid();
        if (Process.isSdkSandboxUid(clientUid)) {
            clientUid = Process.getAppUidForSdkSandboxUid(clientUid);
        }
        logSandboxActivityApiLatency(method, callResult, latencyMillis, clientUid);
    }

    private void logSandboxActivityApiLatency(
            int method, int callResult, int latencyMillis, int clientUid) {
        // TODO(b/321974997): log SDK package name in addition to existing data.
        mSdkSandboxStatsdLogger.logSandboxActivityApiLatency(
                method, callResult, latencyMillis, clientUid);
    }

    interface SandboxBindingCallback {
        void onBindingSuccessful(
                ISdkSandboxService service,
                SandboxLatencyInfo sandboxLatencyInfo);

        void onBindingFailed(
                LoadSdkException exception,
                SandboxLatencyInfo sandboxLatencyInfo);
    }

    class SandboxServiceConnection implements ServiceConnection {

        private final SdkSandboxServiceProvider mServiceProvider;
        private final CallingInfo mCallingInfo;
        private boolean mHasConnectedBefore = false;
        private SandboxLatencyInfo mSandboxLatencyInfo;

        SandboxServiceConnection(
                SdkSandboxServiceProvider serviceProvider,
                CallingInfo callingInfo,
                SandboxLatencyInfo sandboxLatencyInfo) {
            mServiceProvider = serviceProvider;
            mCallingInfo = callingInfo;
            mSandboxLatencyInfo = sandboxLatencyInfo;
        }

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            final ISdkSandboxService mService = ISdkSandboxService.Stub.asInterface(service);

            // Perform actions needed after every sandbox restart.
            if (!onSandboxConnected(mService)) {
                // We don't need to call sandboxBindingCallback.onBindingFailed() in this case since
                // onSdkSandboxDeath() will take care of iterating through LoadSdkSessions and
                // informing SDKs about load failure.
                return;
            }

            // Set connected service for app once all initialization has finished. This needs to be
            // set after every sandbox restart as well.
            mServiceProvider.onServiceConnected(mCallingInfo, mService);

            // Once bound service has been set, sync manager is notified.
            notifySyncManagerSandboxStarted(mCallingInfo);

            BackgroundThread.getExecutor()
                    .execute(
                            () -> {
                                computeSdkStorage(mCallingInfo, mService);
                            });

            mSandboxLatencyInfo.setTimeSandboxLoaded(mInjector.elapsedRealtime());
            if (!mHasConnectedBefore) {
                mHasConnectedBefore = true;
            }

            ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp =
                    clearAndGetSandboxBindingCallbacks();
            for (int i = 0; i < sandboxBindingCallbacksForApp.size(); i++) {
                SandboxBindingCallback callback = sandboxBindingCallbacksForApp.get(i);
                callback.onBindingSuccessful(mService, mSandboxLatencyInfo);
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            // Sdk sandbox crashed or killed, system will start it again.
            Log.d(TAG, "Sandbox service for " + mCallingInfo + " has been disconnected");
            mServiceProvider.onServiceDisconnected(mCallingInfo);
        }

        @Override
        public void onBindingDied(ComponentName name) {
            Log.d(TAG, "Sandbox service for " + mCallingInfo + " : died on binding");
            // We call the lifecycle callback only after service is unbound to avoid a race
            // condition with a new binding, if the app immediately reloads the SDK.
            synchronized (mLock) {
                mServiceProvider.unbindService(mCallingInfo);
                handleSandboxLifecycleCallbacksLocked(mCallingInfo);
            }
        }

        @Override
        public void onNullBinding(ComponentName name) {
            Log.d(TAG, "Sandbox service failed to bind for " + mCallingInfo + " : service is null");
            LoadSdkException exception =
                    new LoadSdkException(
                            SdkSandboxManager.LOAD_SDK_INTERNAL_ERROR,
                            "Failed to bind the service");
            ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp =
                    clearAndGetSandboxBindingCallbacks();
            for (int i = 0; i < sandboxBindingCallbacksForApp.size(); i++) {
                SandboxBindingCallback callback = sandboxBindingCallbacksForApp.get(i);
                callback.onBindingFailed(exception, mSandboxLatencyInfo);
            }
        }

        /**
         * Actions to be performed every time the sandbox connects for a particular app, such as the
         * first time the sandbox is brought up and every time it restarts.
         *
         * @return true if all actions were performed successfully, false otherwise.
         */
        private boolean onSandboxConnected(ISdkSandboxService service) {
            Log.i(
                    TAG,
                    String.format(
                            "Sdk sandbox has been bound for app package %s with uid %d",
                            mCallingInfo.getPackageName(), mCallingInfo.getUid()));
            try {
                service.asBinder().linkToDeath(() -> onSdkSandboxDeath(mCallingInfo), 0);
            } catch (RemoteException e) {
                // Sandbox had already died, cleanup sdk links.
                onSdkSandboxDeath(mCallingInfo);
                return false;
            }

            try {
                service.initialize(
                        new SdkToServiceLink(),
                        mSdkSandboxSettingsListener.isCustomizedSdkContextEnabled());
            } catch (Throwable e) {
                handleFailedSandboxInitialization(mCallingInfo);
                return false;
            }

            return true;
        }

        private ArrayList<SandboxBindingCallback> clearAndGetSandboxBindingCallbacks() {
            ArrayList<SandboxBindingCallback> sandboxBindingCallbacksForApp;
            synchronized (mLock) {
                sandboxBindingCallbacksForApp = mSandboxBindingCallbacks.get(mCallingInfo);
                mSandboxBindingCallbacks.remove(mCallingInfo);
            }
            if (sandboxBindingCallbacksForApp == null) {
                sandboxBindingCallbacksForApp = new ArrayList<>();
            }
            return sandboxBindingCallbacksForApp;
        }
    }

    void handleFailedSandboxInitialization(CallingInfo callingInfo) {
        final String errorMsg = "Failed to initialize sandbox";
        Log.e(TAG, errorMsg + " for " + callingInfo);
        // Kill the sandbox if it failed to initialize as it might not be properly usable.
        stopSdkSandboxService(callingInfo, errorMsg);
    }

    private void onSdkSandboxDeath(CallingInfo callingInfo) {
        synchronized (mLock) {
            killAppOnSandboxDeathIfNeededLocked(callingInfo);
            mSandboxBindingCallbacks.remove(callingInfo);
            // If SDK sandbox is already unbound then we can invoke callbacks immediately,
            // otherwise we defer until onBindingDied is called.
            if (mServiceProvider.getSandboxStatusForApp(callingInfo)
                            != SdkSandboxServiceProvider.NON_EXISTENT
                    && !mServiceProvider.isSandboxBoundForApp(callingInfo)) {
                handleSandboxLifecycleCallbacksLocked(callingInfo);
            }

            mServiceProvider.onSandboxDeath(callingInfo);
            // All SDK state is lost on death.
            if (mLoadSdkSessions.containsKey(callingInfo)) {
                ArrayList<LoadSdkSession> loadSessions =
                        new ArrayList<>(mLoadSdkSessions.get(callingInfo).values());
                for (int i = 0; i < loadSessions.size(); i++) {
                    LoadSdkSession loadSdkSession = loadSessions.get(i);
                    loadSdkSession.onSandboxDeath();
                }
                mLoadSdkSessions.remove(callingInfo);
            }
        }
    }

    @GuardedBy("mLock")
    private void killAppOnSandboxDeathIfNeededLocked(CallingInfo callingInfo) {
        if (!SdkLevel.isAtLeastU()
                || !mCallingInfosWithDeathRecipients.containsKey(callingInfo)
                || mSandboxLifecycleCallbacks.containsKey(callingInfo)
                || getLoadedSdksForApp(callingInfo).size() == 0) {
            /* The app should not be killed in any one of the following cases:
               1) The SDK level is not U+ (as app kill API is not supported in that case).
               2) The app is already dead.
               3) The app has registered at least one callback to deal with sandbox death.
               4) The app has no SDKs loaded.
            */
            return;
        }

        // TODO(b/261442377): Only the processes that loaded some SDK should be killed. For now,
        // kill the process that loaded the first SDK.
        mActivityManagerLocal.killSdkSandboxClientAppProcess(callingInfo.getAppProcessToken());
    }

    @GuardedBy("mLock")
    private void handleSandboxLifecycleCallbacksLocked(CallingInfo callingInfo) {
        RemoteCallbackList<ISdkSandboxProcessDeathCallback> sandboxLifecycleCallbacks;
        sandboxLifecycleCallbacks = mSandboxLifecycleCallbacks.get(callingInfo);

        if (sandboxLifecycleCallbacks == null) {
            return;
        }

        int size = sandboxLifecycleCallbacks.beginBroadcast();
        for (int i = 0; i < size; ++i) {
            try {
                sandboxLifecycleCallbacks.getBroadcastItem(i).onSdkSandboxDied();
            } catch (RemoteException e) {
                Log.w(TAG, "Unable to send sdk sandbox death event to app", e);
            }
        }
        sandboxLifecycleCallbacks.finishBroadcast();
    }

    @Override
    public boolean isSdkSandboxServiceRunning(String callingPackageName) {
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);

        final long token = Binder.clearCallingIdentity();
        try {
            return isSdkSandboxServiceRunning(callingInfo);
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public void stopSdkSandbox(String callingPackageName) {
        final CallingInfo callingInfo = CallingInfo.fromBinder(mContext, callingPackageName);

        mContext.enforceCallingPermission(
                STOP_SDK_SANDBOX_PERMISSION,
                callingPackageName + " does not have permission to stop their sandbox");

        final long token = Binder.clearCallingIdentity();
        try {
            stopSdkSandboxService(callingInfo, "App requesting sandbox kill");
        } finally {
            Binder.restoreCallingIdentity(token);
        }
    }

    @Override
    public IBinder getAdServicesManager() {
        synchronized (mLock) {
            return mAdServicesManager;
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void registerAdServicesManagerService(IBinder iBinder, boolean published) {
        Log.d(TAG, "registerAdServicesManagerService(): published=" + published);
        synchronized (mLock) {
            mAdServicesManager = iBinder;
            mAdServicesManagerPublished = published;
        }
    }

    boolean isSdkSandboxDisabled() {
        synchronized (mLock) {
            if (!mInjector.isAdServiceApkPresent()) {
                return true;
            }

            // Ignore killswitch if the device is an emulator
            if (mInjector.isEmulator()) {
                return false;
            }

            return getSdkSandboxSettingsListener().isKillSwitchEnabled();
        }
    }

    /**
     * Clears the SDK sandbox state. This will result in the state being checked again the next time
     * an SDK is loaded.
     */
    void clearSdkSandboxState() {
        synchronized (mLock) {
            getSdkSandboxSettingsListener().setKillSwitchState(DEFAULT_VALUE_DISABLE_SDK_SANDBOX);
        }
    }

    /**
     * Enables the sandbox for testing purposes. Note that the sandbox can still be disabled by
     * setting the killswitch.
     */
    void forceEnableSandbox() {
        synchronized (mLock) {
            getSdkSandboxSettingsListener().setKillSwitchState(false);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxSettingsListener getSdkSandboxSettingsListener() {
        synchronized (mLock) {
            return mSdkSandboxSettingsListener;
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    void setSdkSandboxSettingsListener(SdkSandboxSettingsListener listener) {
        synchronized (mLock) {
            mSdkSandboxSettingsListener = listener;
        }
    }

    void stopAllSandboxes() {
        synchronized (mLock) {
            stopAllSandboxesLocked();
        }
    }

    /** Stops all running sandboxes in the case that the killswitch is triggered. */
    @GuardedBy("mLock")
    void stopAllSandboxesLocked() {
        for (int i = mLoadSdkSessions.size() - 1; i >= 0; --i) {
            stopSdkSandboxService(mLoadSdkSessions.keyAt(i), "SDK sandbox killswitch enabled");
        }
    }

    void stopSdkSandboxService(CallingInfo currentCallingInfo, String reason) {
        if (!isSdkSandboxServiceRunning(currentCallingInfo)) {
            Log.d(TAG, "Cannot kill sandbox for " + currentCallingInfo + ", already dead");
            return;
        }

        mServiceProvider.unbindService(currentCallingInfo);

        // For T, we kill the sandbox by uid. For U, we kill a specific sandbox process.
        if (SdkLevel.isAtLeastU()) {
            try {
                mServiceProvider.stopSandboxService(currentCallingInfo);
            } catch (PackageManager.NameNotFoundException e) {
                // Just log the exception for the CallingUid for which package is not found to
                // ensure other sandbox services are stopped
                Log.e(
                        TAG,
                        "Failed to stop sandbox service for: " + currentCallingInfo.toString(),
                        e);
            }
        } else {
            // For apps with shared uid, unbind the sandboxes for all the remaining apps since we
            // kill the sandbox by uid.
            synchronized (mLock) {
                for (int i = 0; i < mCallingInfosWithDeathRecipients.size(); i++) {
                    final CallingInfo callingInfo = mCallingInfosWithDeathRecipients.keyAt(i);
                    if (callingInfo.getUid() == currentCallingInfo.getUid()) {
                        mServiceProvider.unbindService(callingInfo);
                    }
                }
            }
            final int sdkSandboxUid = Process.toSdkSandboxUid(currentCallingInfo.getUid());
            Log.i(TAG, "Killing sdk sandbox/s with uid " + sdkSandboxUid);
            mActivityManager.killUid(sdkSandboxUid, reason);
        }
    }

    boolean isSdkSandboxServiceRunning(CallingInfo callingInfo) {
        int sandboxStatus = mServiceProvider.getSandboxStatusForApp(callingInfo);
        return sandboxStatus == SdkSandboxServiceProvider.CREATED
                || sandboxStatus == SdkSandboxServiceProvider.CREATE_PENDING;
    }

    @WorkerThread
    private void computeSdkStorage(CallingInfo callingInfo, ISdkSandboxService service) {
        final List<StorageDirInfo> sharedStorageDirsInfo =
                mSdkSandboxStorageManager.getInternalStorageDirInfo(callingInfo);
        final List<StorageDirInfo> sdkStorageDirsInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(callingInfo);

        try {
            service.computeSdkStorage(
                    getListOfStoragePaths(sharedStorageDirsInfo),
                    getListOfStoragePaths(sdkStorageDirsInfo),
                    new IComputeSdkStorageCallback.Stub() {
                        @Override
                        public void onStorageInfoComputed(int sharedStorageKb, int sdkStorageKb) {
                            mSdkSandboxPulledAtoms.logStorage(
                                    callingInfo.getUid(), sharedStorageKb, sdkStorageKb);
                        }
                    });
        } catch (RemoteException e) {
            Log.e(TAG, "Error while computing sdk storage for CallingInfo: " + callingInfo);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    List<String> getListOfStoragePaths(List<StorageDirInfo> storageDirInfos) {
        final List<String> paths = new ArrayList<>();

        for (int i = 0; i < storageDirInfos.size(); i++) {
            paths.add(storageDirInfos.get(i).getCeDataDir());
            paths.add(storageDirInfos.get(i).getDeDataDir());
        }
        return paths;
    }

    private void notifySyncManagerSandboxStarted(CallingInfo callingInfo) {
        ISharedPreferencesSyncCallback syncManagerCallback = null;
        synchronized (mLock) {
            syncManagerCallback = mSyncDataCallbacks.get(callingInfo);
            if (syncManagerCallback != null) {
                try {
                    syncManagerCallback.onSandboxStart();
                } catch (RemoteException ignore) {
                    // App died.
                }
            }
            mSyncDataCallbacks.remove(callingInfo);
        }
    }

    private void loadSdkForService(
            LoadSdkSession loadSdkSession,
            ISdkSandboxService service,
            SandboxLatencyInfo sandboxLatencyInfo) {
        CallingInfo callingInfo = loadSdkSession.mCallingInfo;
        // Gather sdk storage information
        final StorageDirInfo sdkDataInfo =
                mSdkSandboxStorageManager.getSdkStorageDirInfo(
                        callingInfo, loadSdkSession.mSdkProviderInfo.getSdkInfo().getName());

        ApplicationInfo customizedInfo =
                createCustomizedApplicationInfo(loadSdkSession.getApplicationInfo(), sdkDataInfo);

        loadSdkSession.load(service, customizedInfo, sandboxLatencyInfo);
    }

    /** The customized ApplicationInfo is used to create CustomizedSdkContext for sdks. */
    ApplicationInfo createCustomizedApplicationInfo(
            ApplicationInfo original, StorageDirInfo dirInfo) {
        ApplicationInfo custom = new ApplicationInfo(original);

        // Assign per-sdk storage path as data dir
        custom.dataDir = dirInfo.getCeDataDir();
        custom.credentialProtectedDataDir = dirInfo.getCeDataDir();
        custom.deviceProtectedDataDir = dirInfo.getDeDataDir();

        // Package name still needs to be that of the sandbox because permissions are defined
        // for the sandbox app.
        custom.packageName = mContext.getPackageManager().getSdkSandboxPackageName();

        return custom;
    }

    private void failStartOrBindService(Intent intent) {
        throw new SecurityException(
                "SDK sandbox uid may not bind to or start to this service: " + intent.toString());
    }

    private void enforceAllowedToStartOrBindService(Intent intent) {
        if (!Process.isSdkSandboxUid(Binder.getCallingUid())
                || !mSdkSandboxSettingsListener.areRestrictionsEnforced()) {
            return;
        }
        ComponentName component = intent.getComponent();

        if (component != null) {
            String componentPackageName = component.getPackageName();
            if ((componentPackageName != null)
                    && (componentPackageName.equals(
                                    WebViewUpdateService.getCurrentWebViewPackageName())
                            || componentPackageName.equals(mInjector.getAdServicesPackageName()))) {
                return;
            }
        }

        if (requestAllowedPerAllowlist(
                intent.getAction(),
                intent.getPackage(),
                /*componentClassName=*/ (component == null) ? null : component.getClassName(),
                /*componentPackageName=*/ (component == null)
                        ? null
                        : component.getPackageName())) {
            return;
        }

        // Default disallow.
        failStartOrBindService(intent);
    }

    @Override
    public int handleShellCommand(ParcelFileDescriptor in, ParcelFileDescriptor out,
            ParcelFileDescriptor err, String[] args) {
        boolean supportsAdServicesShellCmd = !mAdServicesManagerPublished;
        return mInjector
                .createShellCommand(this, mContext, supportsAdServicesShellCmd)
                .exec(
                        this,
                        in.getFileDescriptor(),
                        out.getFileDescriptor(),
                        err.getFileDescriptor(),
                        args);
    }

    private ApplicationInfo getSdkSandboxApplicationInfoForInstrumentation(
            ApplicationInfo clientAppInfo, boolean isSdkInSandbox)
            throws PackageManager.NameNotFoundException {
        int uid = clientAppInfo.uid;
        PackageManager pm = mContext.getPackageManager();
        ApplicationInfo sdkSandboxInfo =
                pm.getApplicationInfoAsUser(
                        pm.getSdkSandboxPackageName(),
                        /* flags= */ 0,
                        UserHandle.getUserHandleForUid(uid));
        ApplicationInfo sdkSandboxInfoForInstrumentation =
                (isSdkInSandbox)
                        ? createCustomizedApplicationInfo(
                                clientAppInfo,
                                new StorageDirInfo(
                                        sdkSandboxInfo.dataDir,
                                        sdkSandboxInfo.deviceProtectedDataDir))
                        : sdkSandboxInfo;

        // Required to allow adopt shell permissions in tests.
        sdkSandboxInfoForInstrumentation.uid = Process.toSdkSandboxUid(uid);
        // We want to use a predictable process name during testing.
        sdkSandboxInfoForInstrumentation.processName =
                getLocalManager().getSdkSandboxProcessNameForInstrumentation(clientAppInfo);

        return sdkSandboxInfoForInstrumentation;
    }

    /**
     * A callback object to establish a link between the sdk in sandbox calling into manager
     * service.
     *
     * <p>When a sandbox is initialized, a callback object of {@link SdkToServiceLink} is passed to
     * be used as a part of {@link SdkSandboxController}. The Controller can then can call APIs on
     * the link object to get data from the manager service.
     */
    // TODO(b/268043836): Move SdkToServiceLink out of SdkSandboxManagerService
    private class SdkToServiceLink extends ISdkToServiceCallback.Stub {

        /**
         * Fetches a list of {@link AppOwnedSdkSandboxInterface} registered for an app
         *
         * <p>This provides the information on the interfaces that are currently registered in the
         * app.
         *
         * @param clientPackageName of the client package
         * @return empty list if callingInfo not found in map otherwise a list of {@link
         *     AppOwnedSdkSandboxInterface}
         */
        @Override
        public List<AppOwnedSdkSandboxInterface> getAppOwnedSdkSandboxInterfaces(
                String clientPackageName) throws RemoteException {
            int uid = Binder.getCallingUid();
            if (Process.isSdkSandboxUid(uid)) {
                uid = Process.getAppUidForSdkSandboxUid(uid);
            }
            CallingInfo callingInfo = new CallingInfo(uid, clientPackageName);
            return getRegisteredAppOwnedSdkSandboxInterfacesForApp(callingInfo);
        }

        /**
         * Fetches {@link SandboxedSdk} for all SDKs that are loaded in the sandbox.
         *
         * <p>This provides the information on the library that is currently loaded in the sandbox
         * and also channels to communicate with loaded SDK.
         *
         * @param clientPackageName package name of the app for which the sdk was loaded in the
         *     sandbox
         * @return List of {@link SandboxedSdk} containing all currently loaded sdks
         */
        @Override
        public List<SandboxedSdk> getSandboxedSdks(String clientPackageName)
                throws RemoteException {
            // TODO(b/258195148): Write multiuser tests
            // TODO(b/242039497): Add authorisation checks to make sure only the sandbox calls this
            //  API.
            int uid = Binder.getCallingUid();
            if (Process.isSdkSandboxUid(uid)) {
                uid = Process.getAppUidForSdkSandboxUid(uid);
            }
            CallingInfo callingInfo = new CallingInfo(uid, clientPackageName);
            final List<SandboxedSdk> sandboxedSdks = new ArrayList<>();
            synchronized (mLock) {
                List<LoadSdkSession> loadedSdks = getLoadedSdksForApp(callingInfo);
                for (int i = 0; i < loadedSdks.size(); i++) {
                    LoadSdkSession sdk = loadedSdks.get(i);
                    SandboxedSdk sandboxedSdk = sdk.getSandboxedSdk();
                    if (sandboxedSdk != null) {
                        sandboxedSdks.add(sandboxedSdk);
                    } else {
                        Log.e(
                                TAG,
                                "SandboxedSdk is null for SDK "
                                        + sdk.mSdkName
                                        + " despite being loaded");
                    }
                }
            }
            return sandboxedSdks;
        }

        @Override
        public void loadSdk(
                String callingPackageName,
                String sdkName,
                SandboxLatencyInfo sandboxLatencyInfo,
                Bundle params,
                ILoadSdkCallback callback)
                throws RemoteException {
            // The process token is only used to kill the app process when the
            // sandbox dies (for U+, not available on T), so a sandbox process token
            // is not needed here. This is taken care of when the first SDK is loaded
            // by the app.
            SdkSandboxManagerService.this.loadSdk(
                    callingPackageName,
                    /*callingAppProcessToken=*/ null,
                    sdkName,
                    sandboxLatencyInfo,
                    params,
                    callback);
        }

        @Override
        public void logLatenciesFromSandbox(SandboxLatencyInfo sandboxLatencyInfo) {
            logSandboxApiLatency(sandboxLatencyInfo);
        }

        @Override
        public void logSandboxActivityApiLatencyFromSandbox(
                int method, int callResult, int latencyMillis) {
            SdkSandboxManagerService.this.logSandboxActivityApiLatency(
                    method, callResult, latencyMillis);
        }
    }

    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    SdkSandboxManagerLocal getLocalManager() {
        return mInjector.getLocalManager();
    }

    private void notifyInstrumentationStarted(CallingInfo callingInfo) {
        Log.d(
                TAG,
                "notifyInstrumentationStarted: clientApp = "
                        + callingInfo.getPackageName()
                        + " clientAppUid = "
                        + callingInfo.getUid());
        synchronized (mLock) {
            mServiceProvider.unbindService(callingInfo);
            int sdkSandboxUid = Process.toSdkSandboxUid(callingInfo.getUid());
            mActivityManager.killUid(sdkSandboxUid, "instrumentation started");
            mRunningInstrumentations.add(callingInfo);
        }
        // TODO(b/223386213): we need to check if there is reconcileSdkData task already enqueued
        //  because the instrumented client app was just installed.
        mSdkSandboxStorageManager.notifyInstrumentationStarted(callingInfo);
    }

    private void notifyInstrumentationFinished(CallingInfo callingInfo) {
        Log.d(TAG, "notifyInstrumentationFinished: clientApp = " + callingInfo.getPackageName()
                + " clientAppUid = " + callingInfo.getUid());
        synchronized (mLock) {
            mRunningInstrumentations.remove(callingInfo);
        }
    }

    private boolean isInstrumentationRunning(CallingInfo callingInfo) {
        synchronized (mLock) {
            return mRunningInstrumentations.contains(callingInfo);
        }
    }

    private boolean isSdkSandboxAllowedToStartActivities(int pid, int uid) {
        return mContext.checkPermission(
                        "android.permission.START_ACTIVITIES_FROM_SDK_SANDBOX", pid, uid)
                == PackageManager.PERMISSION_GRANTED;
    }

    // TODO(b/300059435): remove once the {@link
    // SdkSandboxActivityAuthority#isSdkSandboxActivityIntent} API is stable.
    @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
    boolean isSdkSandboxActivity(Intent intent) {
        if (intent == null) {
            return false;
        }
        if (intent.getAction() != null
                && intent.getAction().equals(ACTION_START_SANDBOXED_ACTIVITY)) {
            return true;
        }
        final String sandboxPackageName = mContext.getPackageManager().getSdkSandboxPackageName();
        if (intent.getPackage() != null && intent.getPackage().equals(sandboxPackageName)) {
            return true;
        }
        if (intent.getComponent() != null
                && intent.getComponent().getPackageName().equals(sandboxPackageName)) {
            return true;
        }
        return false;
    }

    /** @hide */
    public static class Lifecycle extends SystemService {
        @VisibleForTesting(visibility = VisibleForTesting.Visibility.PRIVATE)
        SdkSandboxManagerService mService;

        public Lifecycle(Context context) {
            super(context);
            mService = new SdkSandboxManagerService(getContext());
        }

        @Override
        public void onStart() {
            publishBinderService(SDK_SANDBOX_SERVICE, mService);

            LocalManagerRegistry.addManager(
                    SdkSandboxManagerLocal.class, mService.getLocalManager());
        }

        @Override
        public void onUserUnlocking(TargetUser user) {
            final int userId = user.getUserHandle().getIdentifier();
            mService.onUserUnlocking(userId);
        }
    }

    @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
    private class SdkSandboxInterceptorCallback implements ActivityInterceptorCallback {

        private enum InterceptCase {
            SANDBOXED_ACTIVITY,
            INSTRUMENTATION_ACTIVITY,
            NO_INTERCEPT
        }

        private InterceptCase shouldIntercept(ActivityInterceptorInfo info) {
            final Intent intent = info.getIntent();
            if (intent == null) {
                return InterceptCase.NO_INTERCEPT;
            }

            boolean isSdkSandboxActivity =
                    (sandboxActivitySdkBasedContext())
                            ? SdkSandboxActivityAuthority.isSdkSandboxActivityIntent(
                                    mContext, intent)
                            : isSdkSandboxActivity(intent);
            if (isSdkSandboxActivity) {
                final String sdkSandboxPackageName =
                        mContext.getPackageManager().getSdkSandboxPackageName();
                // Only intercept if action and package are both defined and refer to the
                // sandbox activity.
                if (intent.getPackage() == null
                        || !intent.getPackage().equals(sdkSandboxPackageName)
                        || intent.getAction() == null
                        || !intent.getAction().equals(ACTION_START_SANDBOXED_ACTIVITY)) {
                    return InterceptCase.NO_INTERCEPT;
                }

                // If component is set, it should refer to the sandbox package to intercept.
                if (intent.getComponent() != null) {
                    if (!intent.getComponent().getPackageName().equals(sdkSandboxPackageName)) {
                        return InterceptCase.NO_INTERCEPT;
                    }
                }
                return InterceptCase.SANDBOXED_ACTIVITY;
            }

            if (info.getActivityInfo() == null
                    || !Process.isSdkSandboxUid(info.getCallingUid())
                    || !SdkLevel.isAtLeastV()) {
                return InterceptCase.NO_INTERCEPT;
            }
            final ApplicationInfo applicationInfo = info.getActivityInfo().applicationInfo;
            synchronized (mLock) {
                if (applicationInfo.packageName != null
                        && mRunningInstrumentations.contains(
                                new CallingInfo(applicationInfo.uid, applicationInfo.packageName))
                        && isSdkSandboxAllowedToStartActivities(
                                info.getCallingPid(), info.getCallingUid())) {
                    return InterceptCase.INSTRUMENTATION_ACTIVITY;
                }
            }

            return InterceptCase.NO_INTERCEPT;
        }

        @Override
        public ActivityInterceptResult onInterceptActivityLaunch(
                @NonNull ActivityInterceptorInfo info) {
            final ActivityInfo activityInfo = info.getActivityInfo();
            ActivityInterceptorCallback.ActivityInterceptResult activityInterceptResult = null;
            long timeEventStarted = mInjector.elapsedRealtime();
            int callingUid = info.getCallingUid();

            // Do not add any lines before checking if interception should apply, this interception
            // happens for every single activity and adding logic might add significant performance
            // overhead.
            switch (shouldIntercept(info)) {
                case SANDBOXED_ACTIVITY:
                    // Update process name and uid to match sandbox process for the calling app.
                    activityInfo.applicationInfo.uid = Process.toSdkSandboxUid(callingUid);
                    CallingInfo callingInfo = new CallingInfo(callingUid, info.getCallingPackage());
                    try {
                        activityInfo.processName =
                                mInjector
                                        .getSdkSandboxServiceProvider()
                                        .toSandboxProcessName(callingInfo);
                    } catch (PackageManager.NameNotFoundException e) {
                        SdkSandboxManagerService.this.logSandboxActivityApiLatency(
                                SdkSandboxStatsLog
                                        .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY,
                                SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION,
                                (int) (mInjector.elapsedRealtime() - timeEventStarted),
                                callingUid);
                        Log.e(
                                TAG,
                                "onInterceptActivityLaunch failed for: " + callingInfo.toString(),
                                e);
                        throw new SecurityException(e.toString());
                    }
                    activityInterceptResult =
                            new ActivityInterceptorCallback.ActivityInterceptResult(
                                    info.getIntent(), info.getCheckedOptions(), true);
                    SdkSandboxManagerService.this.logSandboxActivityApiLatency(
                            SdkSandboxStatsLog
                                    .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY,
                            SdkSandboxStatsLog
                                    .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                            (int) (mInjector.elapsedRealtime() - timeEventStarted),
                            callingUid);
                    break;
                case INSTRUMENTATION_ACTIVITY:
                    // Tests instrumented to run in the Sandbox already use a sandbox Uid.
                    activityInfo.applicationInfo.uid = callingUid;
                    callingInfo =
                            new CallingInfo(callingUid, activityInfo.applicationInfo.packageName);
                    activityInterceptResult =
                            new ActivityInterceptorCallback.ActivityInterceptResult(
                                    info.getIntent(), info.getCheckedOptions(), true);
                    try {
                        activityInfo.processName =
                                mInjector
                                        .getSdkSandboxServiceProvider()
                                        .toSandboxProcessNameForInstrumentation(callingInfo);
                    } catch (PackageManager.NameNotFoundException e) {
                        Log.e(
                                TAG,
                                "onInterceptActivityLaunch failed for: " + callingInfo.toString(),
                                e);
                        throw new SecurityException(e.toString());
                    }
                    break;
                default: // NO_INTERCEPT
                    SdkSandboxManagerService.this.logSandboxActivityApiLatency(
                            SdkSandboxStatsLog
                                    .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__INTERCEPT_SANDBOX_ACTIVITY,
                            SdkSandboxStatsLog
                                    .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE,
                            (int) (mInjector.elapsedRealtime() - timeEventStarted),
                            callingUid);
                    return null;
            }

            return activityInterceptResult;
        }
    }

    private class UidImportanceListener implements ActivityManager.OnUidImportanceListener {

        private final int mImportanceCutpoint;

        public boolean isListening = false;

        UidImportanceListener() {
            if (SdkLevel.isAtLeastU()) {
                // On U+, we inform the SDK when the app has transitioned to and from foreground
                // importance.
                mImportanceCutpoint = IMPORTANCE_FOREGROUND;
            } else {
                // On T, we unbind the sandbox when the app stops being visible to the user in some
                // way.
                mImportanceCutpoint = IMPORTANCE_VISIBLE;
            }
        }

        public void startListening() {
            synchronized (mLock) {
                if (isListening) {
                    return;
                }
                mActivityManager.addOnUidImportanceListener(this, mImportanceCutpoint);
                isListening = true;
            }
        }

        public void stopListening() {
            synchronized (mLock) {
                if (!isListening) {
                    return;
                }
                mActivityManager.removeOnUidImportanceListener(this);
                isListening = false;
            }
        }

        @Override
        public void onUidImportance(int uid, int importance) {
            synchronized (mLock) {
                for (int i = 0; i < mCallingInfosWithDeathRecipients.size(); i++) {
                    final CallingInfo callingInfo = mCallingInfosWithDeathRecipients.keyAt(i);
                    if (callingInfo.getUid() == uid) {
                        if (SdkLevel.isAtLeastU()) {
                            informSdksAboutAppTransition(importance, callingInfo);
                        } else {
                            unbindSandbox(importance, callingInfo);
                        }
                    }
                }
            }
        }

        private void unbindSandbox(int importance, CallingInfo callingInfo) {
            if (importance <= mImportanceCutpoint) {
                // The lower the importance value, the more "important" the process is. We
                // are only interested when the process is no longer visible.
                return;
            }
            LogUtil.d(
                    TAG,
                    "App with uid "
                            + callingInfo.getUid()
                            + " is no longer visible, unbinding sandbox");
            // Unbind the sandbox when the app is no longer visible to lower its priority.
            mServiceProvider.unbindService(callingInfo);
        }

        private void informSdksAboutAppTransition(int importance, CallingInfo callingInfo) {
            ISdkSandboxService sandbox = mServiceProvider.getSdkSandboxServiceForApp(callingInfo);
            if (sandbox == null) {
                return;
            }

            try {
                // Inform the sandbox when the client app uid has changed from foreground to
                // background importance or vice versa.
                sandbox.notifySdkSandboxClientImportanceChange(importance <= mImportanceCutpoint);
            } catch (RemoteException e) {
                Log.e(
                        TAG,
                        "Could not inform sandbox about state change of "
                                + callingInfo
                                + " : "
                                + e.getMessage());
            }
        }
    }

    // For testing as SANDBOXED_ACTIVITY_HANDLER_KEY is hidden from
    // SdkSandboxManagerServiceUnitTests
    @NonNull
    public String getSandboxedActivityHandlerKey() {
        return EXTRA_SANDBOXED_ACTIVITY_HANDLER;
    }

    private ArraySet<String> getContentProviderAllowlist() {
        String curWebViewPackageName = WebViewUpdateService.getCurrentWebViewPackageName();
        ArraySet<String> contentProviderAuthoritiesAllowlist = new ArraySet<>();
        // TODO(b/279557220): Make curWebViewPackageName a static variable once fixed.
        for (String webViewAuthority :
                new String[] {
                    WEBVIEW_DEVELOPER_MODE_CONTENT_PROVIDER, WEBVIEW_SAFE_MODE_CONTENT_PROVIDER
                }) {
            contentProviderAuthoritiesAllowlist.add(curWebViewPackageName + '.' + webViewAuthority);
        }

        synchronized (mLock) {
            if (mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()
                    && mSdkSandboxSettingsListener.getNextContentProviderAllowlist() != null) {
                contentProviderAuthoritiesAllowlist.addAll(
                        mSdkSandboxSettingsListener.getNextContentProviderAllowlist());
                return contentProviderAuthoritiesAllowlist;
            }

            // TODO(b/271547387): Filter out the allowlist based on targetSdkVersion.
            ArraySet<String> contentProviderAllowlistForTargetSdkVersion =
                    mSdkSandboxSettingsListener
                            .getContentProviderAllowlistPerTargetSdkVersion()
                            .get(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
            if (contentProviderAllowlistForTargetSdkVersion != null) {
                contentProviderAuthoritiesAllowlist.addAll(
                        contentProviderAllowlistForTargetSdkVersion);
            } else {
                contentProviderAuthoritiesAllowlist.addAll(
                        DEFAULT_CONTENTPROVIDER_ALLOWED_AUTHORITIES);
            }
        }
        return contentProviderAuthoritiesAllowlist;
    }

    // Returns null if an allowlist was not set at all.
    @Nullable
    private ArraySet<String> getBroadcastReceiverAllowlist() {
        synchronized (mLock) {
            if (mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()) {
                return mSdkSandboxSettingsListener.getNextBroadcastReceiverAllowlist();
            }

            ArrayMap<Integer, ArraySet<String>> broadcastReceiverAllowlist =
                    mSdkSandboxSettingsListener.getBroadcastReceiverAllowlistPerTargetSdkVersion();

            if (broadcastReceiverAllowlist == null) {
                return null;
            }
            // TODO(b/271547387): Filter out the allowlist based on targetSdkVersion.
            return broadcastReceiverAllowlist.get(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
        }
    }

    @NonNull
    private ArraySet<String> getActivityAllowlist() {
        synchronized (mLock) {
            if (mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()
                    && mSdkSandboxSettingsListener.getNextActivityAllowlist() != null) {
                return mSdkSandboxSettingsListener.getNextActivityAllowlist();
            }
            return getActivityAllowlistForTargetSdk();
        }
    }

    @NonNull
    private ArraySet<String> getActivityAllowlistForTargetSdk() {
        synchronized (mLock) {
            if (mSdkSandboxSettingsListener.getActivityAllowlistPerTargetSdkVersion() == null) {
                return DEFAULT_ACTIVITY_ALLOWED_ACTIONS;
            }
            // TODO(b/271547387): Filter out the allowlist based on targetSdkVersion.
            ArraySet<String> activityAllowlistPerTargetSdkVersion =
                    mSdkSandboxSettingsListener
                            .getActivityAllowlistPerTargetSdkVersion()
                            .get(Build.VERSION_CODES.UPSIDE_DOWN_CAKE);
            if (activityAllowlistPerTargetSdkVersion == null) {
                return DEFAULT_ACTIVITY_ALLOWED_ACTIONS;
            }
            return activityAllowlistPerTargetSdkVersion;
        }
    }

    private boolean requestAllowedPerAllowlist(
            String action,
            String packageName,
            String componentClassName,
            String componentPackageName) {
        // TODO(b/288873117): Use effective targetSdkVersion of the sandbox for the client app.
        AllowedServices allowedServices =
                mSdkSandboxSettingsListener.applySdkSandboxRestrictionsNext()
                        ? mSdkSandboxSettingsListener.getNextServiceAllowlist()
                        : mSdkSandboxSettingsListener.getServiceAllowlistForTargetSdkVersion(
                                /*targetSdkVersion=*/ 34);

        if (Objects.isNull(allowedServices)) {
            return false;
        }

        for (int i = 0; i < allowedServices.getAllowedServicesCount(); i++) {
            AllowedService allowedService = allowedServices.getAllowedServices(i);
            if (StringHelper.doesInputMatchWildcardPattern(
                            allowedService.getAction(), action, /*matchOnNullInput=*/ true)
                    && StringHelper.doesInputMatchWildcardPattern(
                            allowedService.getPackageName(),
                            packageName,
                            /*matchOnNullInput=*/ true)
                    && StringHelper.doesInputMatchWildcardPattern(
                            allowedService.getComponentClassName(),
                            componentClassName,
                            /*matchOnNullInput=*/ true)
                    && StringHelper.doesInputMatchWildcardPattern(
                            allowedService.getComponentPackageName(),
                            componentPackageName,
                            /*matchOnNullInput=*/ true)) {
                return true;
            }
        }
        return false;
    }

    private int getEffectiveTargetSdkVersion(int sdkSandboxUid)
            throws PackageManager.NameNotFoundException {
        return mSdkSandboxRestrictionManager.getEffectiveTargetSdkVersion(
                Process.getAppUidForSdkSandboxUid(sdkSandboxUid));
    }

    private class LocalImpl implements SdkSandboxManagerLocal {
        @Override
        public void registerAdServicesManagerService(IBinder iBinder, boolean published) {
            SdkSandboxManagerService.this.registerAdServicesManagerService(iBinder, published);
        }

        @NonNull
        @Override
        public String getSdkSandboxProcessNameForInstrumentation(
                @NonNull ApplicationInfo clientAppInfo) {
            CallingInfo callingInfo = new CallingInfo(clientAppInfo.uid, clientAppInfo.packageName);
            try {
                return mServiceProvider.toSandboxProcessNameForInstrumentation(callingInfo);
            } catch (PackageManager.NameNotFoundException e) {
                Log.e(
                        TAG,
                        "getSdkSandboxProcessNameForInstrumentation failed for: "
                                + callingInfo.toString(),
                        e);
                throw new SecurityException(e.toString());
            }
        }

        @NonNull
        @Override
        public ApplicationInfo getSdkSandboxApplicationInfoForInstrumentation(
                @NonNull ApplicationInfo clientAppInfo, boolean isSdkInSandbox)
                throws PackageManager.NameNotFoundException {
            return SdkSandboxManagerService.this.getSdkSandboxApplicationInfoForInstrumentation(
                    clientAppInfo, isSdkInSandbox);
        }

        @Override
        public void notifyInstrumentationStarted(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationStarted(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public void notifyInstrumentationFinished(
                @NonNull String clientAppPackageName, int clientAppUid) {
            SdkSandboxManagerService.this.notifyInstrumentationFinished(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public boolean isInstrumentationRunning(
                @NonNull String clientAppPackageName, int clientAppUid) {
            return SdkSandboxManagerService.this.isInstrumentationRunning(
                    new CallingInfo(clientAppUid, clientAppPackageName));
        }

        @Override
        public void enforceAllowedToSendBroadcast(@NonNull Intent intent) {
            if (!canSendBroadcast(intent)) {
                throw new SecurityException(
                        "Intent "
                                + intent.getAction()
                                + " may not be broadcast from an SDK sandbox uid");
            }
        }

        @Override
        public boolean canSendBroadcast(@NonNull Intent intent) {
            return false;
        }

        @Override
        public void enforceAllowedToStartActivity(@NonNull Intent intent) {
            if (!Process.isSdkSandboxUid(Binder.getCallingUid())
                    || !mSdkSandboxSettingsListener.areRestrictionsEnforced()) {
                return;
            }

            if (intent.getAction() == null) {
                return;
            }

            if (StringHelper.doesInputMatchAnyWildcardPattern(
                    getActivityAllowlist(), intent.getAction())) {
                return;
            }
            // During CTS-in-sandbox testing, we store the package name of the instrumented test in
            // the intent identifier to match it against the running instrumentations.
            final String instrumentationPackageName = intent.getIdentifier();
            final int callingUid = Binder.getCallingUid();
            final int appUid = Process.getAppUidForSdkSandboxUid(callingUid);
            synchronized (mLock) {
                if (instrumentationPackageName != null
                        && Process.isSdkSandboxUid(callingUid)
                        && mRunningInstrumentations.contains(
                                new CallingInfo(appUid, instrumentationPackageName))
                        && isSdkSandboxAllowedToStartActivities(
                                Binder.getCallingPid(), callingUid)) {
                    // allow launching activities for sdk-in-sandbox instrumented tests.
                    return;
                }
            }
            throw new SecurityException(
                    "Intent "
                            + intent.getAction()
                            + " may not be started from an SDK sandbox uid.");
        }

        @Override
        public void enforceAllowedToStartOrBindService(@NonNull Intent intent) {
            SdkSandboxManagerService.this.enforceAllowedToStartOrBindService(intent);
        }

        @Override
        public boolean canAccessContentProviderFromSdkSandbox(@NonNull ProviderInfo providerInfo) {
            if (!Process.isSdkSandboxUid(Binder.getCallingUid())) {
                return true;
            }

            /**
             * By clearing the calling identity, system server identity is set which allows us to
             * call {@DeviceConfig.getBoolean}
             */
            long token = Binder.clearCallingIdentity();

            try {
                return !mSdkSandboxSettingsListener.areRestrictionsEnforced()
                        || StringHelper.doesInputMatchAnyWildcardPattern(
                                getContentProviderAllowlist(), providerInfo.authority);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public void enforceAllowedToHostSandboxedActivity(
                @NonNull Intent intent, int clientAppUid, @NonNull String clientAppPackageName) {
            long timeEventStarted = mInjector.elapsedRealtime();
            try {
                if (Process.isSdkSandboxUid(clientAppUid)) {
                    throw new SecurityException(
                            "Sandbox process is not allowed to start sandbox activities.");
                }
                if (intent == null) {
                    throw new SecurityException("Intent to start sandbox activity is null.");
                }
                if (intent.getAction() == null
                        || !intent.getAction().equals(ACTION_START_SANDBOXED_ACTIVITY)) {
                    throw new SecurityException(
                            "Sandbox activity intent must have an action ("
                                    + ACTION_START_SANDBOXED_ACTIVITY
                                    + ").");
                }
                String sandboxPackageName = mContext.getPackageManager().getSdkSandboxPackageName();
                if (intent.getPackage() == null
                        || !intent.getPackage().equals(sandboxPackageName)) {
                    throw new SecurityException(
                            "Sandbox activity intent's package must be set to the sandbox package");
                }
                if (intent.getComponent() != null) {
                    final String componentPackageName = intent.getComponent().getPackageName();
                    if (!componentPackageName.equals(sandboxPackageName)) {
                        throw new SecurityException(
                                "Sandbox activity intent's component must refer to the sandbox"
                                        + " package");
                    }
                }
            } catch (SecurityException e) {
                logEnforceAllowedToHostSandboxedActivityLatency(
                        SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION,
                        timeEventStarted);
                throw e;
            }

            final CallingInfo callingInfo = new CallingInfo(clientAppUid, clientAppPackageName);
            if (mServiceProvider.getSdkSandboxServiceForApp(callingInfo) == null) {
                logEnforceAllowedToHostSandboxedActivityLatency(
                        SdkSandboxStatsLog
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_SECURITY_EXCEPTION_NO_SANDBOX_PROCESS,
                        timeEventStarted);
                throw new SecurityException(
                        "There is no sandbox process running for the caller uid"
                                + ": "
                                + clientAppUid
                                + ".");
            }

            Bundle extras = intent.getExtras();
            if (extras == null || extras.getBinder(getSandboxedActivityHandlerKey()) == null) {
                logEnforceAllowedToHostSandboxedActivityLatency(
                        SdkSandboxStatsLog
                                .SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__FAILURE_ILLEGAL_ARGUMENT_EXCEPTION,
                        timeEventStarted);
                throw new IllegalArgumentException(
                        "Intent should contain an extra params with key = "
                                + getSandboxedActivityHandlerKey()
                                + " and value is an IBinder that identifies a registered "
                                + "SandboxedActivityHandler.");
            }

            logEnforceAllowedToHostSandboxedActivityLatency(
                    SdkSandboxStatsLog.SANDBOX_ACTIVITY_EVENT_OCCURRED__CALL_RESULT__SUCCESS,
                    timeEventStarted);
        }

        private void logEnforceAllowedToHostSandboxedActivityLatency(
                int callResult, long timeEventStarted) {
            SdkSandboxManagerService.this.logSandboxActivityApiLatency(
                    SdkSandboxStatsLog
                            .SANDBOX_ACTIVITY_EVENT_OCCURRED__METHOD__ENFORCE_ALLOWED_TO_HOST_SANDBOXED_ACTIVITY,
                    callResult,
                    (int) (mInjector.elapsedRealtime() - timeEventStarted));
        }

        @Override
        public boolean canRegisterBroadcastReceiver(
                @NonNull IntentFilter intentFilter, int flags, boolean onlyProtectedBroadcasts) {
            if (!Process.isSdkSandboxUid(Binder.getCallingUid())) {
                return true;
            }

            int actionsCount = intentFilter.countActions();
            if (actionsCount == 0) {
                return false;
            }

            /**
             * By clearing the calling identity, system server identity is set which allows us to
             * call {@DeviceConfig.getBoolean}
             */
            long token = Binder.clearCallingIdentity();

            try {
                if (!mSdkSandboxSettingsListener.areRestrictionsEnforced()) {
                    return true;
                }

                ArraySet<String> broadcastReceiverAllowlist = getBroadcastReceiverAllowlist();
                // If an allowlist was not set at all, only allow protected broadcasts. Note that
                // this is different from an empty allowlist (which blocks all BroadcastReceivers).
                if (broadcastReceiverAllowlist == null) {
                    return onlyProtectedBroadcasts;
                }

                ArraySet<String> actions = new ArraySet<>();
                for (int i = 0; i < actionsCount; ++i) {
                    actions.add(intentFilter.getAction(i));
                }

                return broadcastReceiverAllowlist.containsAll(actions);
            } finally {
                Binder.restoreCallingIdentity(token);
            }
        }

        @Override
        public int getEffectiveTargetSdkVersion(int sdkSandboxUid)
                throws PackageManager.NameNotFoundException {
            return SdkSandboxManagerService.this.getEffectiveTargetSdkVersion(sdkSandboxUid);
        }
    }
}
