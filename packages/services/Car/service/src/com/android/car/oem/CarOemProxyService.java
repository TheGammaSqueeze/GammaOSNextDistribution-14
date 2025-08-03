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

package com.android.car.oem;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.content.pm.PackageManagerHelper;
import android.car.builtin.os.BuildHelper;
import android.car.builtin.os.TraceHelper;
import android.car.builtin.util.Slogf;
import android.car.builtin.util.TimingsTraceLog;
import android.car.oem.IOemCarAudioDuckingService;
import android.car.oem.IOemCarAudioFocusService;
import android.car.oem.IOemCarAudioVolumeService;
import android.car.oem.IOemCarService;
import android.car.oem.IOemCarServiceCallback;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Resources;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.RemoteException;
import android.os.SystemClock;
import android.os.SystemProperties;
import android.os.UserHandle;
import android.text.TextUtils;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.R;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.GuardedBy;
import com.android.internal.annotations.VisibleForTesting;

import java.util.ArrayList;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Manages access to OemCarService.
 *
 * <p>All calls in this class are blocking on OEM service initialization, so should be called as
 *  late as possible.
 *
 * <b>NOTE</b>: All {@link CarOemProxyService} call should be after init of ICarImpl. If any
 * component calls {@link CarOemProxyService} before init of ICarImpl complete, it would throw
 * {@link IllegalStateException}.
 */
public final class CarOemProxyService implements CarServiceBase {

    private static final String TAG = CarOemProxyService.class.getSimpleName();
    private static final String CALL_TAG = CarOemProxyService.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    // mock component name for testing if system property is set.
    private static final String PROPERTY_EMULATED_OEM_CAR_SERVICE =
            "persist.com.android.car.internal.debug.oem_car_service";

    private final int mOemServiceConnectionTimeoutMs;
    private final int mOemServiceReadyTimeoutMs;
    private final Object mLock = new Object();
    private final boolean mIsFeatureEnabled;
    private final Context mContext;
    private final boolean mIsOemServiceBound;
    private final CarOemProxyServiceHelper mHelper;
    private final HandlerThread mHandlerThread;
    private final Handler mHandler;
    @GuardedBy("mLock")
    private final ArrayList<CarOemProxyServiceCallback> mCallbacks = new ArrayList<>();


    private String mComponentName;

    // True once OemService return true for {@code isOemServiceReady} call. It means that OEM
    // service has completed all the initialization and ready to serve requests.
    @GuardedBy("mLock")
    private boolean mIsOemServiceReady;
    // True once OEM service is connected. It means that OEM service has return binder for
    // communication. OEM service may still not be ready.
    @GuardedBy("mLock")
    private boolean mIsOemServiceConnected;

    @GuardedBy("mLock")
    private boolean mInitComplete;
    @GuardedBy("mLock")
    private IOemCarService mOemCarService;
    @GuardedBy("mLock")
    private CarOemAudioFocusProxyService mCarOemAudioFocusProxyService;
    @GuardedBy("mLock")
    private CarOemAudioVolumeProxyService mCarOemAudioVolumeProxyService;
    @GuardedBy("mLock")
    private CarOemAudioDuckingProxyService mCarOemAudioDuckingProxyService;
    private long mWaitForOemServiceConnectedDuration;
    private long mWaitForOemServiceReadyDuration;


    private final ServiceConnection mCarOemServiceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName componentName, IBinder iBinder) {
            Slogf.i(TAG, "onServiceConnected: %s, %s", componentName, iBinder);
            synchronized (mLock) {
                if (mOemCarService == IOemCarService.Stub.asInterface(iBinder)) {
                    return; // already connected.
                }
                Slogf.i(TAG, "car oem service binder changed, was %s now: %s",
                        mOemCarService, iBinder);
                mOemCarService = IOemCarService.Stub.asInterface(iBinder);
                Slogf.i(TAG, "**CarOemService connected**");
                mIsOemServiceConnected = true;
                mLock.notifyAll();
            }
        }

        @Override
        public void onServiceDisconnected(ComponentName componentName) {
            Slogf.e(TAG, "OEM service crashed. Crashing the CarService. ComponentName:%s",
                    componentName);
            mHelper.crashCarService("Service Disconnected");
        }
    };

    private final CountDownLatch mOemServiceReadyLatch = new CountDownLatch(1);

    private final IOemCarServiceCallback mOemCarServiceCallback = new IOemCarServiceCallbackImpl();

    @VisibleForTesting
    public CarOemProxyService(Context context) {
        this(context, null);
    }

    @VisibleForTesting
    public CarOemProxyService(Context context, CarOemProxyServiceHelper helper) {
        this(context, helper, null);
    }

    public CarOemProxyService(Context context, CarOemProxyServiceHelper helper, Handler handler) {
        // Bind to the OemCarService
        mContext = context;
        Resources res = mContext.getResources();
        mOemServiceConnectionTimeoutMs = res
                .getInteger(R.integer.config_oemCarService_connection_timeout_ms);
        mOemServiceReadyTimeoutMs = res
                .getInteger(R.integer.config_oemCarService_serviceReady_timeout_ms);

        String componentName = res.getString(R.string.config_oemCarService);

        if (TextUtils.isEmpty(componentName)) {
            // mock component name for testing if system property is set.
            String emulatedOemCarService = SystemProperties.get(PROPERTY_EMULATED_OEM_CAR_SERVICE,
                    "");
            if (!BuildHelper.isUserBuild() && emulatedOemCarService != null
                    && !emulatedOemCarService.isEmpty()) {
                componentName = emulatedOemCarService;
                Slogf.i(TAG, "Using emulated componentname for testing. ComponentName: %s",
                        mComponentName);
            }
        }

        mComponentName = componentName;

        Slogf.i(TAG, "Oem Car Service Config. Connection timeout:%s, Service Ready timeout:%d, "
                + "component Name:%s", mOemServiceConnectionTimeoutMs, mOemServiceReadyTimeoutMs,
                mComponentName);

        if (isInvalidComponentName(context, mComponentName)) {
            // feature disabled
            mIsFeatureEnabled = false;
            mIsOemServiceBound = false;
            mHelper = null;
            mHandlerThread = null;
            mHandler = null;
            Slogf.i(TAG, "**CarOemService is disabled.**");
            return;
        }

        Intent intent = (new Intent())
                .setComponent(ComponentName.unflattenFromString(mComponentName));

        Slogf.i(TAG, "Binding to Oem Service with intent: %s", intent);
        mHandlerThread = CarServiceUtils.getHandlerThread("car_oem_service");
        mHandler = handler == null ? new Handler(mHandlerThread.getLooper()) : handler;

        mIsOemServiceBound = mContext.bindServiceAsUser(intent, mCarOemServiceConnection,
                Context.BIND_AUTO_CREATE | Context.BIND_IMPORTANT, UserHandle.SYSTEM);

        if (mIsOemServiceBound) {
            mIsFeatureEnabled = true;
            Slogf.i(TAG, "OemCarService bounded.");
        } else {
            mIsFeatureEnabled = false;
            Slogf.e(TAG,
                    "Couldn't bound to OemCarService. Oem service feature is marked disabled.");
        }
        mHelper = helper ==  null ? new CarOemProxyServiceHelper(mContext) : helper;
    }

    private boolean isInvalidComponentName(Context context, String componentName) {
        if (componentName == null || componentName.isEmpty()) {
            if (DBG) {
                Slogf.d(TAG, "ComponentName is null or empty.");
            }
            return true;
        }

        // Only pre-installed package can be used for OEM Service.
        String packageName = ComponentName.unflattenFromString(componentName).getPackageName();
        PackageInfo info;
        try {
            info = context.getPackageManager().getPackageInfo(packageName, /* flags= */ 0);
        } catch (NameNotFoundException e) {
            Slogf.e(TAG, "componentName %s not found.", componentName);
            return true;
        }

        if (info == null || info.applicationInfo == null
                || !(PackageManagerHelper.isSystemApp(info.applicationInfo)
                        || PackageManagerHelper.isUpdatedSystemApp(info.applicationInfo)
                        || PackageManagerHelper.isOemApp(info.applicationInfo)
                        || PackageManagerHelper.isOdmApp(info.applicationInfo)
                        || PackageManagerHelper.isVendorApp(info.applicationInfo)
                        || PackageManagerHelper.isProductApp(info.applicationInfo)
                        || PackageManagerHelper.isSystemExtApp(info.applicationInfo))) {
            if (DBG) {
                Slogf.d(TAG, "Invalid component name. Info: %s", info);
            }
            return true;
        }

        if (DBG) {
            Slogf.d(TAG, "Valid component name %s, ", componentName);
        }

        return false;
    }

    /**
     * Registers callback to be called once OEM service is ready.
     *
     * <p>Other CarService components cannot call OEM service. But they can register a callback
     * which would be called as soon as OEM Service is ready./
     */
    public void registerCallback(CarOemProxyServiceCallback callback) {
        synchronized (mLock) {
            mCallbacks.add(callback);
        }
    }

    /**
     * Informs if OEM service is enabled.
     */
    public boolean isOemServiceEnabled() {
        synchronized (mLock) {
            return mIsFeatureEnabled;
        }
    }

    /**
     * Informs if OEM service is ready.
     */
    public boolean isOemServiceReady() {
        synchronized (mLock) {
            return mIsOemServiceReady;
        }
    }

    @Override
    public void init() {
        // Nothing to be done as OemCarService was initialized in the constructor.
    }

    @Override
    public void release() {
        // Stop OEM Service;
        if (mIsOemServiceBound) {
            Slogf.i(TAG, "Unbinding Oem Service");
            mContext.unbindService(mCarOemServiceConnection);
        }
    }

    @Override
    public void dump(IndentingPrintWriter writer) {
        writer.println("***CarOemProxyService dump***");
        writer.increaseIndent();
        synchronized (mLock) {
            writer.printf("mIsFeatureEnabled: %s\n", mIsFeatureEnabled);
            writer.printf("mIsOemServiceBound: %s\n", mIsOemServiceBound);
            writer.printf("mIsOemServiceReady: %s\n", mIsOemServiceReady);
            writer.printf("mIsOemServiceConnected: %s\n", mIsOemServiceConnected);
            writer.printf("mInitComplete: %s\n", mInitComplete);
            writer.printf("OEM_CAR_SERVICE_CONNECTED_TIMEOUT_MS: %s\n",
                    mOemServiceConnectionTimeoutMs);
            writer.printf("OEM_CAR_SERVICE_READY_TIMEOUT_MS: %s\n", mOemServiceReadyTimeoutMs);
            writer.printf("mComponentName: %s\n", mComponentName);
            writer.printf("waitForOemServiceConnected completed in : %d ms\n",
                    mWaitForOemServiceConnectedDuration);
            writer.printf("waitForOemServiceReady completed in : %d ms\n",
                    mWaitForOemServiceReadyDuration);
            // Dump other service components.
            getCarOemAudioFocusService().dump(writer);
            getCarOemAudioVolumeService().dump(writer);
            // Dump OEM service stack
            if (mIsOemServiceReady) {
                writer.printf("OEM callstack\n");
                int timeoutMs = 2000;
                try {
                    IOemCarService oemCarService = getOemService();
                    writer.printf(mHelper.doBinderTimedCallWithTimeout(CALL_TAG,
                            () -> oemCarService.getAllStackTraces(), timeoutMs));
                } catch (TimeoutException e) {
                    writer.printf("Didn't received OEM stack within %d milliseconds.\n", timeoutMs);
                }
            }
            // Dump helper
            if (mHelper != null) {
                mHelper.dump(writer);
            }
        }
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    public String getOemServiceName() {
        return mComponentName;
    }

    /**
     * Gets OEM audio focus service.
     */
    @Nullable
    public CarOemAudioFocusProxyService getCarOemAudioFocusService() {
        if (!mIsFeatureEnabled) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service is disabled, returning null for"
                        + " getCarOemAudioFocusService");
            }
            return null;
        }

        synchronized (mLock) {
            if (mCarOemAudioFocusProxyService != null) {
                return mCarOemAudioFocusProxyService;
            }
        }

        waitForOemService();

        // Defaults to returning null service and try again next time the service is requested.
        IOemCarService oemCarService = getOemService();
        IOemCarAudioFocusService oemAudioFocusService = mHelper.doBinderTimedCallWithDefaultValue(
                CALL_TAG, () -> oemCarService.getOemAudioFocusService(),
                /* defaultValue= */ null);

        if (oemAudioFocusService == null) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service doesn't implement AudioFocusService, returning null"
                        + " for getCarOemAudioFocusService");
            }
            return null;
        }

        CarOemAudioFocusProxyService carOemAudioFocusProxyService =
                new CarOemAudioFocusProxyService(mHelper, oemAudioFocusService);
        synchronized (mLock) {
            if (mCarOemAudioFocusProxyService != null) {
                return mCarOemAudioFocusProxyService;
            }
            mCarOemAudioFocusProxyService = carOemAudioFocusProxyService;
            Slogf.i(TAG, "CarOemAudioFocusProxyService is ready.");
            return mCarOemAudioFocusProxyService;
        }
    }

    /**
     * Gets OEM audio volume service.
     */
    @Nullable
    public CarOemAudioVolumeProxyService getCarOemAudioVolumeService() {
        if (!mIsFeatureEnabled) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service is disabled, returning null for"
                        + " getCarOemAudioVolumeService");
            }
            return null;
        }

        synchronized (mLock) {
            if (mCarOemAudioVolumeProxyService != null) {
                return mCarOemAudioVolumeProxyService;
            }
        }

        waitForOemService();
        IOemCarService oemCarService = getOemService();
        IOemCarAudioVolumeService oemAudioVolumeService = mHelper.doBinderTimedCallWithDefaultValue(
                CALL_TAG, () -> oemCarService.getOemAudioVolumeService(),
                /* defaultValue= */ null);

        if (oemAudioVolumeService == null) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service doesn't implement AudioVolumeService,"
                        + "returning null for getCarOemAudioDuckingService");
            }
            return null;
        }

        CarOemAudioVolumeProxyService carOemAudioVolumeProxyService =
                new CarOemAudioVolumeProxyService(mHelper, oemAudioVolumeService);
        synchronized (mLock) {
            if (mCarOemAudioVolumeProxyService != null) {
                return mCarOemAudioVolumeProxyService;
            }
            mCarOemAudioVolumeProxyService = carOemAudioVolumeProxyService;
            Slogf.i(TAG, "CarOemAudioVolumeProxyService is ready.");
        }
        return carOemAudioVolumeProxyService;
    }

    /**
     * Gets OEM audio ducking service.
     */
    @Nullable
    public CarOemAudioDuckingProxyService getCarOemAudioDuckingService() {
        if (!mIsFeatureEnabled) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service is disabled, returning null for"
                        + " getCarOemAudioDuckingService");
            }
            return null;
        }

        synchronized (mLock) {
            if (mCarOemAudioDuckingProxyService != null) {
                return mCarOemAudioDuckingProxyService;
            }
        }

        waitForOemService();

        IOemCarService oemCarService = getOemService();
        IOemCarAudioDuckingService oemAudioDuckingService =
                mHelper.doBinderTimedCallWithDefaultValue(
                CALL_TAG, () -> oemCarService.getOemAudioDuckingService(),
                /* defaultValue= */ null);

        if (oemAudioDuckingService == null) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service doesn't implement AudioDuckingService,"
                        + "returning null for getCarOemAudioDuckingService");
            }
            return null;
        }

        CarOemAudioDuckingProxyService carOemAudioDuckingProxyService =
                new CarOemAudioDuckingProxyService(mHelper, oemAudioDuckingService);
        synchronized (mLock) {
            if (mCarOemAudioDuckingProxyService != null) {
                return mCarOemAudioDuckingProxyService;
            }
            mCarOemAudioDuckingProxyService = carOemAudioDuckingProxyService;
            Slogf.i(TAG, "CarOemAudioDuckingProxyService is ready.");
        }
        return carOemAudioDuckingProxyService;
    }

    /**
     * Should be called when CarService is ready for communication. It updates the OEM service that
     * CarService is ready.
     */
    public void onCarServiceReady() {
        TimingsTraceLog t = new TimingsTraceLog(TAG, TraceHelper.TRACE_TAG_CAR_SERVICE);
        long startTime = SystemClock.uptimeMillis();
        t.traceBegin("waitForOemServiceConnected");
        waitForOemServiceConnected();
        mWaitForOemServiceConnectedDuration = SystemClock.uptimeMillis() - startTime;
        t.traceEnd();

        IOemCarService oemCarService = getOemService();
        mHelper.doBinderOneWayCall(CALL_TAG, () -> {
            try {
                oemCarService.onCarServiceReady(mOemCarServiceCallback);
            } catch (RemoteException ex) {
                Slogf.e(TAG, "Binder call received RemoteException, calling to crash CarService",
                        ex);
            }
        });

        t.traceBegin("waitForOemServiceReady");
        startTime = SystemClock.uptimeMillis();
        waitForOemServiceReady();
        mWaitForOemServiceReadyDuration = SystemClock.uptimeMillis() - startTime;
        t.traceEnd();
    }

    private void waitForOemServiceConnected() {
        synchronized (mLock) {
            if (!mInitComplete) {
                // No CarOemService call should be made before or during init of ICarImpl.
                throw new IllegalStateException(
                        "CarOemService should not be call before CarService initialization");
            }

            if (mIsOemServiceConnected) {
                return;
            }
            waitForOemServiceConnectedLocked();
        }
    }

    @GuardedBy("mLock")
    private void waitForOemServiceConnectedLocked() {
        long startTime = SystemClock.elapsedRealtime();
        long remainingTime = mOemServiceConnectionTimeoutMs;

        while (!mIsOemServiceConnected && remainingTime > 0) {
            try {
                Slogf.i(TAG, "waiting to connect to OemService. wait time: %s", remainingTime);
                mLock.wait(mOemServiceConnectionTimeoutMs);
                remainingTime = mOemServiceConnectionTimeoutMs
                        - (SystemClock.elapsedRealtime() - startTime);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                Slogf.w(TAG, "InterruptedException received. Reset interrupted status.", e);
            }
        }

        if (!mIsOemServiceConnected) {
            Slogf.e(TAG, "OEM Service is not connected within: %dms, calling to crash CarService",
                    mOemServiceConnectionTimeoutMs);
            mHelper.crashCarService("OEM Service not connected");
        }
    }

    private void waitForOemService() {
        waitForOemServiceConnected();
        waitForOemServiceReady();
    }

    private void waitForOemServiceReady() {
        synchronized (mLock) {
            if (mIsOemServiceReady) {
                return;
            }
        }

        try {
            mOemServiceReadyLatch.await(mOemServiceReadyTimeoutMs, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            Slogf.i(TAG, "Exception while waiting for OEM Service to be ready.", e);
        }

        synchronized (mLock) {
            if (!mIsOemServiceReady) {
                Slogf.e(TAG, "OEM Service is not ready within: " + mOemServiceReadyTimeoutMs
                        + "ms, calling to crash CarService");
                mHelper.crashCarService("OEM Service not ready");
            }
        }
        Slogf.i(TAG, "OEM Service is ready.");
    }

    // Initialize all OEM related components.
    private void initOemServiceComponents() {
        // Initialize all Oem Service components
        getCarOemAudioFocusService();

        // Callback registered Car Service components for OEM service.
        callCarServiceComponents();
    }

    private void callCarServiceComponents() {
        synchronized (mLock) {
            for (int i = 0; i < mCallbacks.size(); i++) {
                mCallbacks.get(i).onOemServiceReady();
            }
        }
    }

    /**
     * Informs CarOemService that ICarImpl's init is complete.
     */
    // This would set mInitComplete, which is an additional check so that no car service component
    // calls CarOemService during or before ICarImpl's init.
    @Override
    public void onInitComplete() {
        if (!mIsFeatureEnabled) {
            if (DBG) {
                Slogf.d(TAG, "Oem Car Service is disabled, No-op for onInitComplete");
            }
            return;
        }

        synchronized (mLock) {
            mInitComplete = true;
        }
        // inform OEM Service that CarService is ready for communication.
        // It has to be posted on the different thread as this call is part of init process.
        mHandler.post(() -> onCarServiceReady());
    }

    /**
     * Gets OEM service latest binder. Don't pass the method to helper as it can cause deadlock.
     */
    private IOemCarService getOemService() {
        synchronized (mLock) {
            return mOemCarService;
        }
    }

    private class IOemCarServiceCallbackImpl extends IOemCarServiceCallback.Stub {
        @Override
        public void sendOemCarServiceReady() {
            synchronized (mLock) {
                mIsOemServiceReady = true;
            }
            mOemServiceReadyLatch.countDown();
            int pid = Binder.getCallingPid();
            Slogf.i(TAG, "OEM Car service is ready and running. Process ID of OEM Car Service is:"
                    + " %d", pid);
            mHelper.updateOemPid(pid);
            IOemCarService oemCarService = getOemService();
            mHelper.updateOemStackCall(() -> oemCarService.getAllStackTraces());
            // Initialize other components on handler thread so that main thread is not
            // blocked
            mHandler.post(() -> initOemServiceComponents());
        }
    }
}
