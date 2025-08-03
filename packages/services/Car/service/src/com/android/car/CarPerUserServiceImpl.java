/*
 * Copyright (C) 2017 The Android Open Source Project
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
package com.android.car;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.ICarBluetoothUserService;
import android.car.ICarPerUserService;
import android.car.ILocationManagerProxy;
import android.car.builtin.util.Slogf;
import android.content.Context;
import android.content.Intent;
import android.os.IBinder;

import com.android.car.bluetooth.CarBluetoothUserService;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.ProxiedService;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.Keep;

import java.io.FileDescriptor;
import java.io.PrintWriter;

/**
 * {@link CarServiceImpl} process runs as the System User. When logged in as a different user, some
 * services do not provide an API to register or bind as a User, hence CarService doesn't receive
 * the events from services/processes running as a non-system user.
 *
 * This Service is run as the Current User on every User Switch and components of CarService can
 * use this service to communicate with services/processes running as the current (non-system) user.
 */
@Keep
public class CarPerUserServiceImpl extends ProxiedService {
    private static final boolean DBG = true;
    private static final String TAG = CarLog.tagFor(CarPerUserServiceImpl.class);

    private CarBluetoothUserService mCarBluetoothUserService;
    private LocationManagerProxy mLocationManagerProxy;

    private CarPerUserServiceBinder mCarPerUserServiceBinder;

    @Override
    public IBinder onBind(Intent intent) {
        if (DBG) Slogf.d(TAG, "onBind()");

        if (mCarPerUserServiceBinder == null) {
            Slogf.e(TAG, "CarPerUserServiceBinder null");
        }
        return mCarPerUserServiceBinder;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (DBG) Slogf.d(TAG, "onStart()");

        return START_STICKY;
    }

    @Override
    public void onCreate() {
        Context context = getApplicationContext();
        Slogf.i(TAG, "created for user %s", context.getUser());

        mCarPerUserServiceBinder = new CarPerUserServiceBinder();
        mCarBluetoothUserService = new CarBluetoothUserService(this);
        mLocationManagerProxy = new LocationManagerProxy(this);
        super.onCreate();
    }

    @Override
    public void onDestroy() {
        Slogf.i(TAG, "destroyed for user %s", getApplicationContext().getUser());

        mCarPerUserServiceBinder = null;
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    protected void dump(FileDescriptor fd, PrintWriter writer, String[] args) {
        try (IndentingPrintWriter pw = new IndentingPrintWriter(writer)) {
            pw.println("CarBluetoothUserService");
            pw.increaseIndent();
            mCarBluetoothUserService.dump(pw);
            pw.decreaseIndent();
            pw.println();
            pw.println("LocationManagerProxy");
            pw.increaseIndent();
            mLocationManagerProxy.dump(pw);
            pw.decreaseIndent();
            pw.println();
        }
    }

    /**
     * Other Services in CarService can create their own Binder interface and receive that interface
     * through this CarPerUserService binder.
     */
    private final class CarPerUserServiceBinder extends ICarPerUserService.Stub {
        @Override
        public ICarBluetoothUserService getBluetoothUserService() {
            return mCarBluetoothUserService;
        }

        @Override
        public ILocationManagerProxy getLocationManagerProxy() {
            return mLocationManagerProxy;
        }
    }
}
