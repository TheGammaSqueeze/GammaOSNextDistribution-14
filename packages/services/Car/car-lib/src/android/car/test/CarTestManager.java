/*
 * Copyright (C) 2021 The Android Open Source Project
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
package android.car.test;

import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.TestApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.os.IBinder;
import android.os.RemoteException;

import java.util.List;

/**
 * API for testing only. Allows mocking vehicle hal.
 *
 * @hide
 */
@TestApi
public final class CarTestManager extends CarManagerBase {

    private final ICarTest mService;

    /**
     * Constructs a new {@link CarTestManager}
     *
     * @hide
     */
    @TestApi
    public CarTestManager(@NonNull Car car, @NonNull IBinder carServiceBinder) {
        super(car);
        mService = ICarTest.Stub.asInterface(carServiceBinder);
    }

    /**
     * @hide
     */
    @Override
    public void onCarDisconnected() {
        // test will fail. nothing to do.
    }

    /**
     * Releases all car services. This make sense for test purpose when it is necessary to reduce
     * interference between testing and real instances of Car Service. For example changing audio
     * focus in CarAudioService may affect framework's AudioManager listeners. AudioManager has a
     * lot of complex logic which is hard to mock.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public void stopCarService(@NonNull IBinder token) {
        try {
            mService.stopCarService(token);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Re-initializes previously released car service.
     *
     * @see {@link #stopCarService(IBinder)}
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public void startCarService(@NonNull IBinder token) {
        try {
            mService.startCarService(token);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Dumps VHAL information or debug VHAL.
     *
     * {@code waitTimeoutMs} specifies the longest time CarTestService will wait to receive all
     * dumped information from VHAL before timeout. A correctly implemented VHAL should finish
     * dumping all the info before returning. As a result, {@code waitTimeoutMs} is used to regulate
     * how long CarTestService would wait before it determines that VHAL is dead or stuck and
     * returns error.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public String dumpVhal(List<String> options, long waitTimeoutMs) {
        try {
            return mService.dumpVhal(options, waitTimeoutMs);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            return "";
        }
    }

    /**
     * Returns whether AIDL VHAL is used for VHAL backend.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public boolean hasAidlVhal() throws RemoteException {
        return mService.hasAidlVhal();
    }

    /**
     * Returns OEM service name.
     *
     * @hide
     */
    @TestApi
    @RequiresPermission(Car.PERMISSION_CAR_TEST_SERVICE)
    public String getOemServiceName() throws RemoteException {
        return mService.getOemServiceName();
    }
}
