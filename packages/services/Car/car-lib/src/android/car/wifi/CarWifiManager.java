/*
 * Copyright (C) 2024 The Android Open Source Project
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

package android.car.wifi;

import android.annotation.FlaggedApi;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.car.feature.Flags;
import android.os.IBinder;
import android.os.RemoteException;

/**
 * CarWifiManager provides API to allow for applications to perform Wi-Fi specific operations.
 *
 * @hide
 */
@SystemApi
@FlaggedApi(Flags.FLAG_PERSIST_AP_SETTINGS)
public final class CarWifiManager extends CarManagerBase {
    private final ICarWifi mService;

    /** @hide */
    public CarWifiManager(Car car, IBinder service) {
        super(car);
        mService = ICarWifi.Stub.asInterface(service);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {}

    /**
     * Returns {@code true} if the persist tethering settings are able to be changed.
     *
     * @hide
     */
    @SystemApi
    @FlaggedApi(Flags.FLAG_PERSIST_AP_SETTINGS)
    @RequiresPermission(Car.PERMISSION_READ_PERSIST_TETHERING_SETTINGS)
    public boolean canControlPersistTetheringSettings() {
        try {
            return mService.canControlPersistTetheringSettings();
        } catch (RemoteException e) {
            return handleRemoteExceptionFromCarService(e, false);
        }
    }
}
