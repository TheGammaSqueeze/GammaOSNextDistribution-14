/*
 * Copyright (C) 2015 The Android Open Source Project
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

package android.car;

import static android.car.CarLibLog.TAG_CAR;

import android.annotation.Nullable;
import android.app.Activity;
import android.content.Context;
import android.os.Handler;
import android.os.RemoteException;
import android.util.Dumpable;
import android.util.DumpableContainer;
import android.util.Log;

import com.android.car.internal.ICarBase;

import java.util.function.Supplier;

/**
 * Common base class for Car*Manager
 * @hide
 */
public abstract class CarManagerBase {

    private static final boolean DEBUG = Log.isLoggable(TAG_CAR, Log.DEBUG);

    protected final ICarBase mCar;

    public CarManagerBase(ICarBase car) {
        mCar = car;
    }

    protected Context getContext() {
        return mCar.getContext();
    }

    protected Handler getEventHandler() {
        return mCar.getEventHandler();
    }

    protected <T> T handleRemoteExceptionFromCarService(RemoteException e, T returnValue) {
        return mCar.handleRemoteExceptionFromCarService(e, returnValue);
    }

    protected void handleRemoteExceptionFromCarService(RemoteException e) {
        mCar.handleRemoteExceptionFromCarService(e);
    }

    /**
     * Handles runtime and remote exception from CarService.
     */
    protected <T> T handleExceptionFromCarService(Exception e, T returnValue) {
        if (e instanceof RemoteException) {
            return handleRemoteExceptionFromCarService((RemoteException) e, returnValue);
        }

        if (e instanceof RuntimeException) {
            Log.w(TAG_CAR, "Car service threw Runtime Exception.", e);
            return returnValue;
        }

        // exception should be either runtime or remote exception
        Log.wtf(TAG_CAR, "Car service threw Exception.", e);

        return returnValue;
    }

    /**
     * Handle disconnection of car service (=crash). As car service has crashed already, this call
     * should only clean up any listeners / callbacks passed from client. Clearing object passed
     * to car service is not necessary as car service has crashed. Note that Car*Manager will not
     * work any more as all binders are invalid. Client should re-create all Car*Managers when
     * car service is restarted.
     */
    protected abstract void onCarDisconnected();

    /**
     * Adds a {@link Dumpable} to a "compatible" container (i.e., an object that extends
     * {@link DumpableContainer}, {@code Activity}, etc...).
     *
     * @return supplied dumpable, or {@code null} if {@code container} is not compatible.
     */
    @Nullable
    protected <T extends Dumpable> T addDumpable(Object container, Supplier<T> dumpableSupplier) {
        if (container instanceof Activity) {
            T dumpable = dumpableSupplier.get();
            if (DEBUG) {
                Log.d(TAG_CAR, "Adding " + dumpable.getDumpableName() + " to actvity " + container);
            }
            ((Activity) container).addDumpable(dumpable);
            return dumpable;
        }
        if (container instanceof DumpableContainer) {
            T dumpable = dumpableSupplier.get();
            if (DEBUG) {
                Log.d(TAG_CAR, "Adding " + dumpable.getDumpableName() + " to DumpableContainer "
                        + container);
            }
            ((DumpableContainer) container).addDumpable(dumpable);
            return dumpable;
        }
        Log.v(TAG_CAR, "NOT adding dumpable to object (" + container
                + ") that doesn't implement addDumpable");
        return null;
    }
}
