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

package com.android.car.os;

import static com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport.DUMP_INFO;

import android.car.Car;
import android.car.builtin.util.Slogf;
import android.car.os.ICarPerformanceService;
import android.car.os.ThreadPolicyWithPriority;
import android.content.Context;
import android.os.Binder;
import android.os.RemoteException;
import android.util.Log;
import android.util.proto.ProtoOutputStream;

import com.android.car.CarLocalServices;
import com.android.car.CarLog;
import com.android.car.CarServiceBase;
import com.android.car.CarServiceUtils;
import com.android.car.internal.ExcludeFromCodeCoverageGeneratedReport;
import com.android.car.internal.util.IndentingPrintWriter;
import com.android.car.watchdog.CarWatchdogService;

/**
 * Service to implement CarPerformanceManager API.
 */
public final class CarPerformanceService extends ICarPerformanceService.Stub
        implements CarServiceBase {
    static final String TAG = CarLog.tagFor(CarPerformanceService.class);

    private static final boolean DEBUG = Slogf.isLoggable(TAG, Log.DEBUG);

    private CarWatchdogService mCarWatchdogService;
    private final Context mContext;

    public CarPerformanceService(Context context) {
        mContext = context;
    }

    @Override
    public void init() {
        mCarWatchdogService = CarLocalServices.getService(CarWatchdogService.class);
        if (DEBUG) {
            Slogf.d(TAG, "CarPerformanceService is initialized");
        }
    }

    @Override
    public void release() {}

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dump(IndentingPrintWriter writer) {
        writer.printf("*%s*\n", getClass().getSimpleName());
        writer.increaseIndent();
        writer.printf("DEBUG=%s", DEBUG);
        writer.decreaseIndent();
    }

    @Override
    @ExcludeFromCodeCoverageGeneratedReport(reason = DUMP_INFO)
    public void dumpProto(ProtoOutputStream proto) {}

    /**
     * Sets the thread priority for a specific thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalArgumentException If the given policy/priority is not valid.
     * @throws IllegalStateException If the provided tid does not belong to the calling process.
     * @throws RemoteException If binder error happens.
     * @throws SecurityException If permission check failed.
     * @throws ServiceSpecificException If the operation failed.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    @Override
    public void setThreadPriority(int tid, ThreadPolicyWithPriority threadPolicyWithPriority)
            throws RemoteException {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        mCarWatchdogService.setThreadPriority(pid, tid, uid, threadPolicyWithPriority.getPolicy(),
                threadPolicyWithPriority.getPriority());
    }

    /**
     * Gets the thread scheduling policy and priority for the specified thread.
     *
     * The thread must belong to the calling process.
     *
     * @throws IllegalStateException If the operation failed or the provided tid does not belong to
     *         the calling process.
     * @throws RemoteException If binder error happens.
     * @throws SecurityException If permission check failed.
     * @throws UnsupportedOperationException If the current android release doesn't support the API.
     */
    @Override
    public ThreadPolicyWithPriority getThreadPriority(int tid) throws RemoteException {
        CarServiceUtils.assertPermission(mContext, Car.PERMISSION_MANAGE_THREAD_PRIORITY);

        int pid = Binder.getCallingPid();
        int uid = Binder.getCallingUid();
        try {
            int[] result = mCarWatchdogService.getThreadPriority(pid, tid, uid);
            return new ThreadPolicyWithPriority(result[0], result[1]);
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "current scheduling policy doesn't support getting priority, cause: "
                    + e.getCause(), e);
        }
    }
}
