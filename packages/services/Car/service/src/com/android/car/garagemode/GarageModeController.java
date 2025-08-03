/*
 * Copyright (C) 2018 The Android Open Source Project
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

package com.android.car.garagemode;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.annotation.Nullable;
import android.car.builtin.util.Slogf;
import android.car.hardware.power.CarPowerManager;
import android.car.hardware.power.ICarPowerStateListener;
import android.content.Context;
import android.content.Intent;
import android.os.Handler;
import android.os.Looper;
import android.os.UserHandle;
import android.util.Log;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.power.CarPowerManagementService;
import com.android.car.systeminterface.SystemInterface;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Main controller for GarageMode. It controls all the flows of GarageMode and defines the logic.
 */
public class GarageModeController extends ICarPowerStateListener.Stub {

    private static final String TAG = CarLog.tagFor(GarageMode.class) + "_"
            + GarageModeController.class.getSimpleName();
    private static final boolean DBG = Slogf.isLoggable(TAG, Log.DEBUG);

    private final GarageMode mGarageMode;
    private final Handler mHandler;
    private CarPowerManagementService mCarPowerService;

    public GarageModeController(Context context, Looper looper) {
        this(context, looper, /* handler= */ null, /* garageMode= */ null);
    }

    public GarageModeController(Context context, Looper looper, Handler handler,
            GarageMode garageMode) {
        mHandler = (handler == null) ? new Handler(looper) : handler;
        mGarageMode = (garageMode == null) ? new GarageMode(context, this) : garageMode;
    }

    /** init */
    public void init() {
        mCarPowerService = CarLocalServices.getService(CarPowerManagementService.class);
        mCarPowerService.registerInternalListener(GarageModeController.this);
        mGarageMode.init();
        Slogf.e("GarageMode", "init");
    }

    /** release */
    public void release() {
        mCarPowerService.unregisterInternalListener(GarageModeController.this);
        mGarageMode.release();
    }

    // This function runs in the CarPowerManagementService handler thread, which is a different
    // thread than mHandler is using.
    @Override
    public void onStateChanged(int state, long expirationTimeMs) {
        if (DBG) {
            Slogf.d(TAG, "CPM state changed to %s",
                    CarPowerManagementService.powerStateToString(state));
        }
        switch (state) {
            case CarPowerManager.STATE_SHUTDOWN_CANCELLED:
                resetGarageMode(null);
                break;
            case CarPowerManager.STATE_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_SUSPEND_ENTER:
            case CarPowerManager.STATE_HIBERNATION_ENTER:
                resetGarageMode(() -> {
                    mCarPowerService.completeHandlingPowerStateChange(state,
                            GarageModeController.this);
                });
                break;
            case CarPowerManager.STATE_PRE_SHUTDOWN_PREPARE:
            case CarPowerManager.STATE_POST_SHUTDOWN_ENTER:
            case CarPowerManager.STATE_POST_SUSPEND_ENTER:
            case CarPowerManager.STATE_POST_HIBERNATION_ENTER:
                mCarPowerService.completeHandlingPowerStateChange(state, GarageModeController.this);
                break;
            case CarPowerManager.STATE_SHUTDOWN_PREPARE:
                initiateGarageMode(
                        () -> mCarPowerService.completeHandlingPowerStateChange(state,
                                GarageModeController.this));
                break;
            default:
                break;
        }
    }

    /**
     * @return boolean whether any jobs are currently in running that GarageMode cares about
     */
    boolean isGarageModeActive() {
        return mGarageMode.isGarageModeActive();
    }

    /**
     * Prints Garage Mode's status, including what jobs it is waiting for
     */
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    void dump(IndentingPrintWriter writer) {
        mGarageMode.dump(writer);
    }

    /**
     * Wrapper method to send a broadcast
     *
     * @param i intent that contains broadcast data
     */
    void sendBroadcast(Intent i) {
        SystemInterface systemInterface = CarLocalServices.getService(SystemInterface.class);
        if (DBG) {
            Slogf.d(TAG, "Sending broadcast with action: %s", i.getAction());
        }
        systemInterface.sendBroadcastAsUser(i, UserHandle.ALL);
    }

    /**
     * @return Handler instance used by controller
     */
    Handler getHandler() {
        return mHandler;
    }

    /**
     * Initiates GarageMode flow which will set the system idleness to true and will start
     * monitoring jobs which has idleness constraint enabled.
     */
    void initiateGarageMode(Runnable completor) {
        mHandler.post(() -> mGarageMode.enterGarageMode(completor));
    }

    /**
     * Resets GarageMode.
     */
    void resetGarageMode(@Nullable Runnable completor) {
        mHandler.post(() -> mGarageMode.cancel(completor));
    }

    @VisibleForTesting
    void finishGarageMode() {
        mHandler.post(() -> mGarageMode.finish());
    }
}
