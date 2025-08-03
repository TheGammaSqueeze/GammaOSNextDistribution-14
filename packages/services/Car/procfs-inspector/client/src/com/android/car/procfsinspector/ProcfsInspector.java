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
package com.android.car.procfsinspector;

import android.annotation.Nullable;
import android.car.builtin.os.ServiceManagerHelper;
import android.os.RemoteException;
import android.util.Log;

import java.util.Collections;
import java.util.List;

/**
 * @deprecated use {@link com.android.car.watchdog.CarWatchdogService} and its related classes
 * for I/O related tasks.
 */
@Deprecated
public final class ProcfsInspector {
    private static final String TAG = "car.procfsinspector";
    private static final String SERVICE_NAME = "com.android.car.procfsinspector";
    private final IProcfsInspector mService;

    private ProcfsInspector(IProcfsInspector service) {
        mService = service;
    }

    @Nullable
    private static IProcfsInspector tryGet() {
        return IProcfsInspector.Stub.asInterface(
            ServiceManagerHelper.checkService(SERVICE_NAME));
    }

    public static List<ProcessInfo> readProcessTable() {
        IProcfsInspector procfsInspector = tryGet();
        if (procfsInspector != null) {
            try {
                return procfsInspector.readProcessTable();
            } catch (RemoteException e) {
                Log.w(TAG, "caught RemoteException", e);
            }
        }

        return Collections.emptyList();
    }
}
