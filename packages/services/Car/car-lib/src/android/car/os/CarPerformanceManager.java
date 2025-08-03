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

package android.car.os;

import android.annotation.CallbackExecutor;
import android.annotation.NonNull;
import android.annotation.RequiresPermission;
import android.annotation.SystemApi;
import android.car.Car;
import android.car.CarManagerBase;
import android.os.IBinder;
import android.os.Process;
import android.os.RemoteException;
import android.os.ServiceSpecificException;

import java.util.Objects;
import java.util.concurrent.Executor;

/**
 * CarPerformanceManager allows applications to tweak performance settings for their
 * processes/threads and listen for CPU available change notifications.
 *
 * @hide
 */
@SystemApi
public final class CarPerformanceManager extends CarManagerBase {

    private final ICarPerformanceService mService;

    /**
     * An exception type thrown when {@link setThreadPriority} failed.
     *
     * @hide
     */
    @SystemApi
    public static final class SetSchedulerFailedException extends Exception {
        SetSchedulerFailedException(Throwable cause) {
            super(cause);
        }
    }

    /** @hide */
    public CarPerformanceManager(Car car, IBinder service) {
        super(car);
        mService = ICarPerformanceService.Stub.asInterface(service);
    }

    /** @hide */
    @Override
    public void onCarDisconnected() {
        // nothing to do
    }

    /**
     * Listener to get CPU availability change notifications.
     *
     * <p>
     * Applications implement the listener method to perform one of the following actions:
     * <ul>
     * <li>Execute CPU intensive tasks when the CPU availability percent is above the specified
     * upper bound percent.
     * <li>Stop executing CPU intensive tasks when the CPU availability percent is below
     * the specified lower bound percent.
     * <li>Handle the CPU availability timeout.
     * </ul>
     * </p>
     *
     * @hide
     * @removed API not used since Android U. Must be removed in Android W (release 35).
     */
    public interface CpuAvailabilityChangeListener {
        /**
         * Called on one of the following events:
         * 1. When the CPU availability percent has reached or decreased below the lower bound
         *    percent specified at {@link CpuAvailabilityMonitoringConfig#getLowerBoundPercent()}.
         * 2. When the CPU availability percent has reached or increased above the upper bound
         *    percent specified at {@link CpuAvailabilityMonitoringConfig#getUpperBoundPercent()}.
         * 3. When the CPU availability monitoring has reached the timeout specified at
         *    {@link CpuAvailabilityMonitoringConfig#getTimeoutInSeconds()}.
         *
         * <p>The listener is called at the executor which is specified in
         * {@link CarPerformanceManager#addCpuAvailabilityChangeListener(Executor,
         * CpuAvailabilityMonitoringConfig, CpuAvailabilityChangeListener)}.
         *
         * @param info CPU availability information.
         */
        void onCpuAvailabilityChange(@NonNull CpuAvailabilityInfo info);
    }

    /**
     * Adds the {@link CpuAvailabilityChangeListener} for the calling package.
     *
     * @param config CPU availability monitoring config.
     * @param listener Listener implementing {@link CpuAvailabilityChangeListener}
     * interface.
     *
     * @throws IllegalStateException if {@code listener} is already added.
     *
     * @hide
     * @removed API not used since Android U. Must be removed in Android W (release 35).
     */
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_CPU_INFO)
    public void addCpuAvailabilityChangeListener(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull CpuAvailabilityMonitoringConfig config,
            @NonNull CpuAvailabilityChangeListener listener) {
        Objects.requireNonNull(executor, "Executor must be non-null");
        Objects.requireNonNull(config, "Config must be non-null");
        Objects.requireNonNull(listener, "Listener must be non-null");

        // TODO(b/217422127): Implement the API.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Removes the {@link CpuAvailabilityChangeListener} for the calling package.
     *
     * @param listener Listener implementing {@link CpuAvailabilityChangeListener}
     * interface.
     *
     * @hide
     * @removed API not used since Android U. Must be removed in Android W (release 35).
     */
    @RequiresPermission(Car.PERMISSION_COLLECT_CAR_CPU_INFO)
    public void removeCpuAvailabilityChangeListener(
            @NonNull @CallbackExecutor Executor executor,
            @NonNull CpuAvailabilityChangeListener listener) {
        Objects.requireNonNull(executor, "Executor must be non-null");
        Objects.requireNonNull(listener, "Listener must be non-null");

        // TODO(b/217422127): Implement the API.
        throw new UnsupportedOperationException("Not yet implemented");
    }

    /**
     * Sets the thread scheduling policy with priority for the current thread.
     *
     * For {@link ThreadPolicyWithPriority#SCHED_DEFAULT} scheduling algorithm, the standard
     * round-robin time-sharing algorithm will be used and the priority field will be ignored.
     * Please use {@link Process#setThreadPriority} to adjust the priority for the default
     * scheduling.
     *
     * @param policyWithPriority A thread scheduling policy with priority.
     * @throws IllegalArgumentException If the policy is not supported or the priority is not within
     *         {@link ThreadPolicyWithPriority#PRIORITY_MIN} and
     *         {@link ThreadPolicyWithPriority#PRIORITY_MAX}.
     * @throws SetSchedulerFailedException If failed to set the scheduling policy and priority.
     * @throws SecurityException If permission check failed.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_MANAGE_THREAD_PRIORITY)
    public void setThreadPriority(@NonNull ThreadPolicyWithPriority policyWithPriority)
            throws SetSchedulerFailedException {
        int tid = Process.myTid();
        try {
            mService.setThreadPriority(tid, policyWithPriority);
        } catch (ServiceSpecificException e) {
            throw new SetSchedulerFailedException(e);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
        }
    }

    /**
     * Gets the thread scheduling policy with priority for the current thread.
     *
     * For {@link ThreadPolicyWithPriority#SCHED_FIFO} or
     * {@link ThreadPolicyWithPriority#SCHED_RR}, this function returns the priority for the
     * scheduling algorithm. For {@link ThreadPolicyWithPriority#SCHED_DEFAULT} which is the
     * standard round-robin time-sharing algorithm, this function always return 0 for priority. The
     * priority for the default algorithm can be fetched by {@link Process#getThreadPriority}.
     *
     * @throws IllegalStateException If failed to get policy or priority.
     * @throws SecurityException If permission check failed.
     *
     * @hide
     */
    @SystemApi
    @RequiresPermission(Car.PERMISSION_MANAGE_THREAD_PRIORITY)
    public @NonNull ThreadPolicyWithPriority getThreadPriority() {
        int tid = Process.myTid();
        try {
            return mService.getThreadPriority(tid);
        } catch (RemoteException e) {
            handleRemoteExceptionFromCarService(e);
            // Car service has crashed, return a default value since we do not
            // want to crash the client.
            return new ThreadPolicyWithPriority(
                    ThreadPolicyWithPriority.SCHED_DEFAULT, /* priority= */ 0);
        }
    }
}
