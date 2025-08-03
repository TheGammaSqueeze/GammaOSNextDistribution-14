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

package android.telephony.cts.externalsatellitegatewayservice;

import android.annotation.NonNull;
import android.annotation.Nullable;
import android.app.Service;
import android.content.Intent;
import android.os.IBinder;
import android.os.RemoteException;
import android.util.Log;

import java.util.concurrent.atomic.AtomicBoolean;

/**
 * A mock SatelliteGatewayService that is used for testing service crash scenarios.
 */
public class ExternalMockSatelliteGatewayService extends Service {
    private static final String TAG = "ExternalMockSatelliteGatewayService";
    private static final String SERVICE_INTERFACE =
            "android.telephony.satellite.SatelliteGatewayService";

    private final IBinder mTelephonyBinder = new ISatelliteGateway.Stub() {};
    private final AtomicBoolean mIsBound = new AtomicBoolean(false);
    private final CtsConnection mCtsBinder = new CtsConnection();
    @Nullable private IExternalSatelliteGatewayListener mExternalListener;

    // For CTS to access this Service.
    public class CtsConnection extends IExternalMockSatelliteGatewayService.Stub {
        public void setExternalSatelliteGatewayListener(
                @NonNull IExternalSatelliteGatewayListener listener) {
            logd("setExternalSatelliteGatewayListener: listener=" + listener);
            mExternalListener = listener;
            if (mIsBound.get()) {
                notifyTelephonyServiceBound();
            }
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        if (SERVICE_INTERFACE.equals(intent.getAction())) {
            logd("Telephony service bound");
            mIsBound.set(true);
            notifyTelephonyServiceBound();
            return mTelephonyBinder;
        }
        logd("CTS service bound");
        return mCtsBinder;
    }

    private void notifyTelephonyServiceBound() {
        logd("notifyTelephonyServiceBound");
        if (mExternalListener != null) {
            try {
                mExternalListener.onRemoteServiceConnected();
            } catch (RemoteException e) {
                loge("notifyTelephonyServiceBound: e=" + e);
            }
        } else {
            logd("notifyTelephonyServiceBound: mExternalListener is null. Waiting for CTS to bind");
        }
    }

    /**
     * Log the message to the radio buffer with {@code DEBUG} priority.
     *
     * @param log The message to log.
     */
    private static void logd(@NonNull String log) {
        Log.d(TAG, log);
    }

    /**
     * Log with error attribute
     *
     * @param s is string log
     */
    private static void loge(@NonNull String s) {
        Log.e(TAG, s);
    }
}
