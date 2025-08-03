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

package android.telephony.satellite.cts;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.Binder;
import android.os.IBinder;
import android.telephony.satellite.stub.ISatelliteGateway;
import android.telephony.satellite.stub.SatelliteGatewayService;
import android.util.Log;

import com.android.internal.util.FunctionalUtils;
import com.android.telephony.Rlog;

import java.util.concurrent.Executor;
import java.util.concurrent.atomic.AtomicBoolean;

public class MockSatelliteGatewayService extends Service {
    private static final String TAG = "MockSatelliteGatewayService";

    @Nullable private ILocalSatelliteGatewayListener mLocalListener;
    private final LocalBinder mLocalBinder = new LocalBinder();

    private final Executor mExecutor;

    private final IBinder mRemoteBinder = new ISatelliteGateway.Stub() {};
    private final AtomicBoolean mShouldNotifyRemoteServiceConnected = new AtomicBoolean(false);

    // For local access of this Service.
    class LocalBinder extends Binder {
        MockSatelliteGatewayService getService() {
            return MockSatelliteGatewayService.this;
        }
    }

    /**
     * Create MockSatelliteGatewayService using the Executor specified for methods being called from
     * the framework.
     *
     * @param executor The executor for the framework to use when executing satellite methods.
     */
    public MockSatelliteGatewayService(@NonNull Executor executor) {
        mExecutor = executor;
    }

    /**
     * Zero-argument constructor to prevent service binding exception.
     */
    public MockSatelliteGatewayService() {
        this(Runnable::run);
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (SatelliteGatewayService.SERVICE_INTERFACE.equals(intent.getAction())) {
            logd("Remote service bound");
            notifyRemoteServiceConnected();
            return mRemoteBinder;
        }
        logd("Local service bound");
        return mLocalBinder;
    }

    @Override
    public boolean onUnbind(Intent intent) {
        if (SatelliteGatewayService.SERVICE_INTERFACE.equals(intent.getAction())) {
            logd("Remote service unbound");
            notifyRemoteServiceDisconnected();
        }
        return false;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        logd("onCreate");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        logd("onDestroy");
    }

    public void setLocalSatelliteListener(@NonNull ILocalSatelliteGatewayListener listener) {
        logd("setLocalSatelliteListener: listener=" + listener);
        mLocalListener = listener;
        if (mShouldNotifyRemoteServiceConnected.get()) {
            notifyRemoteServiceConnected();
        }
    }

    private void notifyRemoteServiceConnected() {
        logd("notifyRemoteServiceConnected");
        if (mLocalListener != null) {
            mShouldNotifyRemoteServiceConnected.set(false);
            runWithExecutor(() -> mLocalListener.onRemoteServiceConnected());
        } else {
            mShouldNotifyRemoteServiceConnected.set(true);
            logd("notifyRemoteServiceConnected: mLocalListener is null");
        }
    }

    private void notifyRemoteServiceDisconnected() {
        logd("notifyRemoteServiceDisconnected");
        mShouldNotifyRemoteServiceConnected.set(false);
        if (mLocalListener != null) {
            runWithExecutor(() -> mLocalListener.onRemoteServiceDisconnected());
        } else {
            loge("notifyRemoteServiceDisconnected: mLocalListener is null");
        }
    }

    /**
     * Execute the given runnable using the executor that this service was created with.
     *
     * @param r A runnable that can throw an exception.
     */
    private void runWithExecutor(@NonNull FunctionalUtils.ThrowingRunnable r) {
        mExecutor.execute(() -> Binder.withCleanCallingIdentity(r));
    }

    /**
     * Log the message to the radio buffer with {@code DEBUG} priority.
     *
     * @param log The message to log.
     */
    private static void logd(@NonNull String log) {
        Rlog.d(TAG, log);
    }

    /**
     * Log with error attribute
     *
     * @param s is string log
     */
    protected void loge(@NonNull String s) {
        Log.e(TAG, s);
    }
}
