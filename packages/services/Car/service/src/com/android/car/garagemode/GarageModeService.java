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

import static com.android.car.CarServiceUtils.getHandlerThread;
import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.content.Context;
import android.os.HandlerThread;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarServiceBase;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.internal.annotations.VisibleForTesting;

/**
 * Main service container for car Garage Mode.
 * Garage Mode enables idle time in cars.
 */
public class GarageModeService implements CarServiceBase {

    private static final String HANDLER_THREAD_NAME = "GarageModeService";

    private final GarageModeController mController;
    private final HandlerThread mHandlerThread = getHandlerThread(HANDLER_THREAD_NAME);

    public GarageModeService(Context context) {
        this(context, /* controller= */ null);
    }

    @VisibleForTesting
    protected GarageModeService(Context context, GarageModeController controller) {
        mController = (controller != null ? controller
                : new GarageModeController(context, mHandlerThread.getLooper()));
    }

    /**
     * Initializes GarageMode
     */
    @Override
    public void init() {
        mController.init();
    }

    /**
     * Cleans up GarageMode processes
     */
    @Override
    public void release() {
        mController.release();
        mHandlerThread.quitSafely();
    }

    /**
     * Dumps useful information about GarageMode
     * @param writer Where to dump the information
     */
    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        boolean isActive = mController.isGarageModeActive();
        writer.println("GarageModeInProgress " + isActive);
        mController.dump(writer);
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * @return whether GarageMode is in progress. Used by {@link com.android.car.ICarImpl}.
     */
    public boolean isGarageModeActive() {
        return mController.isGarageModeActive();
    }

    /**
     * Forces GarageMode to start. Used by {@link com.android.car.ICarImpl}.
     */
    public void forceStartGarageMode() {
        mController.initiateGarageMode(null);
    }

    /**
     * Stops and resets the GarageMode. Used by {@link com.android.car.ICarImpl}.
     */
    public void stopAndResetGarageMode() {
        mController.resetGarageMode(null);
    }
}
