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

package android.car.oem;

import android.annotation.CallSuper;
import android.annotation.NonNull;
import android.annotation.Nullable;
import android.annotation.SystemApi;
import android.app.Service;
import android.car.CarVersion;
import android.car.builtin.util.Slogf;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.ArrayMap;
import android.util.Log;

import com.android.internal.annotations.GuardedBy;

import java.io.FileDescriptor;
import java.io.PrintWriter;
import java.util.Map;

/**
 * This code will be running as part of the OEM Service. This provides basic implementation for OEM
 * Service. OEMs should extend this class and override relevant methods.
 *
 * <p>
 * OEM service implementation should have {@code android.car.permission.BIND_OEM_CAR_SERVICE} as
 * required permission in manifest to connect to the OEM service.
 *
 * @hide
 */
@SystemApi
public abstract class OemCarService extends Service {

    private static final String TAG = OemCarService.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);
    private static final String PERMISSION_BIND_OEM_CAR_SERVICE =
            "android.car.permission.BIND_OEM_CAR_SERVICE";

    // OEM Service components
    // Note: Change the size as more components are added.
    @GuardedBy("mLock")
    private final ArrayMap<Class<?>, OemCarServiceComponent> mOemCarServiceComponents =
            new ArrayMap<Class<?>, OemCarServiceComponent>(1);

    private final Object mLock = new Object();

    private final IOemCarService mInterface = new IOemCarService.Stub() {
        // Component services
        @Override
        public IOemCarAudioFocusService getOemAudioFocusService() {
            assertPermission();
            synchronized (mLock) {
                return (IOemCarAudioFocusService) mOemCarServiceComponents
                        .getOrDefault(IOemCarAudioFocusService.class, null);
            }
        }

        @Override
        public IOemCarAudioVolumeService getOemAudioVolumeService() {
            assertPermission();
            synchronized (mLock) {
                return (IOemCarAudioVolumeService) mOemCarServiceComponents
                        .getOrDefault(IOemCarAudioVolumeService.class, null);
            }
        }

        @Override
        public IOemCarAudioDuckingService getOemAudioDuckingService() {
            assertPermission();
            synchronized (mLock) {
                return (IOemCarAudioDuckingService) mOemCarServiceComponents
                        .getOrDefault(IOemCarAudioDuckingService.class, null);
            }
        }

        @Override
        public void onCarServiceReady(IOemCarServiceCallback callback) throws RemoteException {
            assertPermission();
            OemCarService.this.onCarServiceReady();
            synchronized (mLock) {
                for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                    if (DBG) {
                        Slogf.d(TAG, "Calling onCarServiceReady for %s\n",
                                mOemCarServiceComponents.keyAt(i).getSimpleName());
                    }
                    mOemCarServiceComponents.valueAt(i).onCarServiceReady();
                }
            }
            callback.sendOemCarServiceReady();
        }

        @Override
        public CarVersion getSupportedCarVersion() {
            assertPermission();
            return OemCarService.this.getSupportedCarVersion();
        }

        @Override
        public String getAllStackTraces() {
            assertPermission();
            Map<Thread, StackTraceElement[]> tracesMap = Thread.getAllStackTraces();
            StringBuilder sb = new StringBuilder();
            sb.append("OemService stack trace:\n");
            int i = 0;
            for (Map.Entry<Thread, StackTraceElement[]> entry : tracesMap.entrySet()) {
                sb.append("Thread: ").append(i++).append('\n');
                StackTraceElement[] stack = entry.getValue();
                for (int j = 0; j < stack.length; j++) {
                    sb.append(stack[j].toString()).append('\n');
                }
            }

            return sb.toString();
        }

        private void assertPermission() {
            if (checkCallingPermission(
                    PERMISSION_BIND_OEM_CAR_SERVICE) != PackageManager.PERMISSION_GRANTED) {
                String errorMsg = "Caller with uid:" + Binder.getCallingUid()
                        + " doesn't have permission " + PERMISSION_BIND_OEM_CAR_SERVICE;
                Slogf.e(TAG, errorMsg);
                throw new SecurityException(errorMsg);
            }
        }
    };


    /**
     * {@inheritDoc}
     *
     * <p>
     * OEM should override this method and do the initialization. OEM should also call super after
     * initialization as this method would call {@link OemCarServiceComponent#init()} for each
     * component implemented by OEM.
     *
     * <p>
     * Car Service will not be available at the time of this initialization. If the OEM needs
     * anything from CarService, they should wait for the CarServiceReady() call. It is expected
     * that most of the initialization will finish in this call.
     */
    @Override
    @CallSuper
    public void onCreate() {
        if (DBG) {
            Slogf.d(TAG, "OnCreate");
        }

        // Create all components
        OemCarAudioFocusService oemCarAudioFocusService = getOemAudioFocusService();
        OemCarAudioVolumeService oemCarAudioVolumeService = getOemAudioVolumeService();
        OemCarAudioDuckingService oemCarAudioDuckingService = getOemAudioDuckingService();
        synchronized (mLock) {
            if (oemCarAudioFocusService != null) {
                mOemCarServiceComponents.put(IOemCarAudioFocusService.class,
                        new OemCarAudioFocusServiceImpl(oemCarAudioFocusService));
            }
            if (oemCarAudioVolumeService != null) {
                mOemCarServiceComponents.put(IOemCarAudioVolumeService.class,
                        new OemCarAudioVolumeServiceImpl(oemCarAudioVolumeService));
            }
            if (oemCarAudioDuckingService != null) {
                mOemCarServiceComponents.put(IOemCarAudioDuckingService.class,
                        new OemCarAudioDuckingServiceImpl(oemCarAudioDuckingService));
            }

            // Initialize them
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                if (DBG) {
                    Slogf.d(TAG, "Initializing %s\n",
                            mOemCarServiceComponents.keyAt(i).getSimpleName());
                }
                mOemCarServiceComponents.valueAt(i).init();
            }
        }
        super.onCreate();
    }

    /**
     * {@inheritDoc}
     *
     * <p>
     * OEM should override this method and do all the resources deallocation. OEM should also call
     * super after resource deallocation as this method would call
     * {@link OemCarServiceComponent#release()} for each component implemented by OEM.
     */
    @Override
    @CallSuper
    public void onDestroy() {
        if (DBG) {
            Slogf.d(TAG, "OnDestroy");
        }

        // Destroy all components and release the resources
        synchronized (mLock) {
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).release();
            }
        }

        super.onDestroy();
    }

    @Override
    public final int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        if (DBG) {
            Slogf.d(TAG, "onStartCommand");
        }
        return START_STICKY;
    }

    @NonNull
    @Override
    public final IBinder onBind(@Nullable Intent intent) {
        if (DBG) {
            Slogf.d(TAG, "onBind");
        }
        return mInterface.asBinder();
    }

    /**
     * Gets Audio Focus Service implemented by OEM Service.
     *
     * @return audio focus service if implemented by OEM service, else return {@code null}.
     */
    @Nullable
    @SuppressWarnings("[OnNameExpected]")
    public OemCarAudioFocusService getOemAudioFocusService() {
        if (DBG) {
            Slogf.d(TAG, "getOemUserService");
        }
        return null;
    }

    /**
     * Gets Audio Volume implemented by OEM Service.
     *
     * @return audio volume service if implemented by OEM service, else return {@code null}.
     */
    @Nullable
    @SuppressWarnings("OnNameExpected")
    public OemCarAudioVolumeService getOemAudioVolumeService() {
        if (DBG) {
            Slogf.d(TAG, "getOemAudioVolumeService");
        }
        return null;
    }

    /**
     * Gets Audio Ducking implemented by OEM Service.
     *
     * @return audio ducking service if implemented by OEM service, else return {@code null}.
     */
    @Nullable
    @SuppressWarnings("OnNameExpected")
    public OemCarAudioDuckingService getOemAudioDuckingService() {
        if (DBG) {
            Slogf.d(TAG, "getOemAudioDuckingService");
        }
        return null;
    }

    @CallSuper
    @Override
    public void dump(@Nullable FileDescriptor fd, @Nullable PrintWriter writer,
            @Nullable String[] args) {
        writer.println("**** Dump OemCarService ****");
        synchronized (mLock) {
            for (int i = 0; i < mOemCarServiceComponents.size(); i++) {
                mOemCarServiceComponents.valueAt(i).dump(writer, args);
            }
        }
    }

    /**
     * Checks the supported CarVersion by the OEM service.
     */
    @SuppressWarnings("[OnNameExpected]")
    @NonNull
    public abstract CarVersion getSupportedCarVersion();

    /**
     * Informs OEM service that CarService is now ready for communication.
     *
     * <p>
     * OEM should override this method if there is any initialization depending on CarService.
     */
    public abstract void onCarServiceReady();
}
